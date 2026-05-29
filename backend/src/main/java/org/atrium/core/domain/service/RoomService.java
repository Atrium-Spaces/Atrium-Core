package org.atrium.core.domain.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

import org.atrium.core.api.dto.RoomView;
import org.atrium.core.api.error.AtriumException;
import org.atrium.core.autoconfigure.AtriumProperties;
import org.atrium.core.domain.constant.AtriumConstants;
import org.atrium.core.domain.event.HomeEvent;
import org.atrium.core.domain.event.RoomEvent;
import org.atrium.core.domain.model.*;
import org.atrium.core.extension.listener.GameLifecycleListener;
import org.atrium.core.redis.repository.PlayerRepository;
import org.atrium.core.redis.repository.RoomRepository;
import org.atrium.core.redis.stream.HomeBroadcastService;
import org.atrium.core.redis.stream.RoomBroadcastService;

import org.jspecify.annotations.Nullable;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * Orchestrates all room state transitions: create, join, leave (delete-on-empty,
 * promote-on-host-leave), kick, delete, settings change, game start / stop, and
 * profile-change broadcast.
 *
 * <p>Every mutation:
 * <ol>
 *   <li>Re-fetches the room from Redis (the single source of truth) to avoid stale
 *       in-memory state racing with another instance / replica.</li>
 *   <li>Authorises the caller (host-only operations check {@code room.host()}).</li>
 *   <li>Persists the updated {@link Room} and any affected {@link Player} records.</li>
 *   <li>Publishes a {@link RoomEvent} on the room's pub/sub channel.</li>
 *   <li>Publishes a {@link HomeEvent} when public-room listing state changes.</li>
 * </ol>
 */
@Slf4j
@Service
@RequiredArgsConstructor
public final class RoomService {

	private final RoomRepository roomRepository;
	private final PlayerRepository playerRepository;
	private final PlayerService playerService;
	private final RoomCodeGenerator codeGenerator;
	private final HomeBroadcastService homeBroadcastService;
	private final RoomBroadcastService broadcastService;
	private final RoomPlayerLimitsResolver roomPlayerLimitsResolver;
	private final RoomViewAssembler viewAssembler;
	private final GameLifecycleListener gameLifecycleListener;
	private final AtriumProperties properties;

	/**
	 * Fetch a room by its code and return its expanded view with player profiles.
	 *
	 * @param code the 6-character room code
	 * @return the room view, or {@link AtriumException#roomNotFound} if missing
	 */
	public Mono<RoomView> view(String code) {
		return roomRepository.findByCode(code)
			.switchIfEmpty(Mono.error(AtriumException.roomNotFound(code)))
			.flatMap(viewAssembler::assemble);
	}

	/**
	 * List public rooms ordered by most-recently-active first.
	 *
	 * @param limit maximum number of rooms to return
	 * @return a flux of expanded room views
	 */
	public Flux<RoomView> listPublic(int limit) {
		return roomRepository.listPublic(limit).flatMap(viewAssembler::assemble);
	}

	/**
	 * Create a new room with the authenticated player as host.
	 *
	 * @param publicId            the host's public id
	 * @param secretId            the host's secret id (authenticated against the player store)
	 * @param requestedName       optional display name for the room (null/blank = unnamed)
	 * @param requestedMinPlayers optional requested minimum players; uses default or game-setting bounds
	 * @param requestedMaxPlayers optional requested maximum players; uses default or game-setting bounds
	 * @param requestedSettings   optional game settings; falls back to {@link DefaultGameSettings}
	 * @param isPrivate           when true, the room is hidden from the public listing
	 * @return the expanded room view of the newly created room
	 */
	public Mono<RoomView> createRoom(UUID publicId, UUID secretId, @Nullable String requestedName, @Nullable Integer requestedMinPlayers, @Nullable Integer requestedMaxPlayers, @Nullable GameSettings requestedSettings, boolean isPrivate) {
		log.debug("Creating room requested by player={} (name={}, min={}, max={}, private={})", publicId, requestedName, requestedMinPlayers, requestedMaxPlayers, isPrivate);
		return playerService.authenticate(publicId, secretId)
			.flatMap(player -> {
				final String name = cleanRoomName(requestedName);
				final GameSettings settings = requestedSettings != null ? requestedSettings : new DefaultGameSettings();
				final RoomPlayerLimitsResolver.NormalizedPlayerLimits normalizedPlayerLimits = roomPlayerLimitsResolver.normalizeForCreate(requestedMinPlayers, requestedMaxPlayers, settings);

				return codeGenerator.next().flatMap(code -> {
					final Instant now = Instant.now();
					final Room room = new Room(code, name, publicId, List.of(publicId), normalizedPlayerLimits.minPlayers(), normalizedPlayerLimits.maxPlayers(), settings, isPrivate, RoomState.LOBBY, now, now);
					return roomRepository.saveNew(room)
						.flatMap(saved -> saved ? Mono.empty() : Mono.error(AtriumException.conflict("Room code collision during create; please retry")))
						.then(playerRepository.save(player.withRoomAdded(code)))
						.then(notifyLifecycle(gameLifecycleListener.onRoomCreated(room), AtriumConstants.LifecycleHookNames.ROOM_CREATED, code))
						.then(viewAssembler.assemble(room))
						.flatMap(roomView -> publishHomeRoomCreated(roomView).thenReturn(roomView));
				});
			})
			.doOnSuccess(roomView -> log.debug("Room created: code={} host={} players={} state={}", roomView.code(), publicId, roomView.players().size(), roomView.state()));
	}

	/**
	 * Join an existing room. Atomic CAS save prevents races on full-room or concurrent
	 * join scenarios.
	 *
	 * @param code     the room code
	 * @param publicId the joining player's public id
	 * @param secretId the joining player's secret id
	 * @return the updated room view
	 */
	public Mono<RoomView> joinRoom(String code, UUID publicId, UUID secretId) {
		log.debug("Join room requested: room={} player={}", code, publicId);
		return playerService.authenticate(publicId, secretId)
			.flatMap(player -> roomRepository.findVersionedByCode(code)
				.switchIfEmpty(Mono.error(AtriumException.roomNotFound(code)))
				.flatMap(versionedRoom -> {
					final Room room = versionedRoom.room();

					if (room.contains(publicId)) {
						log.debug("Player {} is already in room {}; refreshing player room index", publicId, code);
						return playerRepository.save(player.withRoomAdded(code)).then(viewAssembler.assemble(room));
					}

					if (room.state() != RoomState.LOBBY) {
						return Mono.error(AtriumException.forbidden("Room is no longer accepting players"));
					}

					if (room.isFull()) {
						return Mono.error(AtriumException.forbidden("Room is full"));
					}

					final List<UUID> newPlayers = new ArrayList<>(room.players());
					newPlayers.add(publicId);
					final Room updatedRoom = room.withPlayers(newPlayers);
					final Player updatedPlayer = player.withRoomAdded(code);
					return roomRepository.saveIfVersion(updatedRoom, versionedRoom.version())
						.flatMap(saved -> saved ? Mono.empty() : Mono.error(AtriumException.conflict("Room was updated concurrently; retry join")))
						.then(playerRepository.save(updatedPlayer))
						.then(notifyLifecycle(gameLifecycleListener.onPlayerJoined(updatedRoom, publicId), AtriumConstants.LifecycleHookNames.PLAYER_JOINED, code))
						.then(viewAssembler.assemble(updatedRoom))
						.flatMap(roomView -> broadcastService.publish(new RoomEvent.PlayerJoined(code, Instant.now(), viewAssembler.toPlayerView(updatedPlayer, Instant.now())))
							.then(publishHomeRoomUpdated(roomView))
							.thenReturn(roomView));
				}))
			.doOnSuccess(roomView -> log.debug("Player {} joined room {} (members={})", publicId, code, roomView.players().size()));
	}

	/**
	 * Leave a room voluntarily. If the leaving player is the host, the longest-joined
	 * remaining player is promoted. If the room becomes empty, it is deleted.
	 *
	 * @param code     the room code
	 * @param publicId the leaving player's public id
	 * @param secretId the leaving player's secret id
	 * @return an empty mono on successful leave
	 */
	public Mono<Void> leaveRoom(String code, UUID publicId, UUID secretId) {
		log.debug("Leave room requested: room={} player={}", code, publicId);
		return playerService.authenticate(publicId, secretId).flatMap(ignored -> performLeave(code, publicId, AtriumConstants.LeaveReasons.LEFT));
	}

	/**
	 * Internal leave path used by both the explicit endpoint and the cleanup sweep.
	 * Skips secret-id authentication because the caller is the lobby itself.
	 * If the room no longer exists, the player's cached roomCodes list is still updated.
	 *
	 * @param code     the room code
	 * @param publicId the leaving player's public id
	 * @param reason   optional reason (e.g. {@code "left"}, {@code "kicked"})
	 * @return an empty mono on successful leave or no-op if already gone
	 */
	public Mono<Void> performLeave(String code, UUID publicId, @Nullable String reason) {
		log.debug("Processing leave: room={} player={} reason={}", code, publicId, reason);
		return roomRepository.findVersionedByCode(code)
			.switchIfEmpty(clearPlayerRoomIfMatching(publicId, code)
				.doOnSuccess(ignored -> log.debug("Leave ignored because room {} no longer exists for player {}", code, publicId))
				.then(Mono.empty()))
			.flatMap(versionedRoom -> {
				final Room room = versionedRoom.room();
				if (!room.contains(publicId)) {
					log.debug("Leave ignored because player {} is not in room {}", publicId, code);
					return clearPlayerRoomIfMatching(publicId, code);
				}

				final List<UUID> remaining = new ArrayList<>(room.players());
				remaining.remove(publicId);

				if (remaining.isEmpty()) {
					log.debug("Last player {} left room {}; deleting room", publicId, code);
					return deleteRoomInternal(room);
				}

				final UUID newHost = room.host().equals(publicId) ? remaining.getFirst() : room.host();
				Room updatedRoom = room.withPlayers(remaining);

				if (!newHost.equals(updatedRoom.host())) {
					updatedRoom = updatedRoom.withHost(newHost);
				}

				final Mono<Void> publishLeave = broadcastService.publish(new RoomEvent.PlayerLeft(code, Instant.now(), publicId, reason)).then();
				final Mono<Void> notifyPlayerLeft = notifyLifecycle(gameLifecycleListener.onPlayerLeft(room, publicId, reason), AtriumConstants.LifecycleHookNames.PLAYER_LEFT, code);
				final Mono<Void> publishHostChanged = newHost.equals(room.host())
					? Mono.empty()
					: broadcastService.publish(new RoomEvent.HostChanged(code, Instant.now(), newHost))
					.doOnSuccess(ignored -> log.debug("Host changed in room {} from {} to {}", code, room.host(), newHost))
					.then();
				return roomRepository.saveIfVersion(updatedRoom, versionedRoom.version())
					.flatMap(saved -> saved ? Mono.empty() : Mono.error(AtriumException.conflict("Room was updated concurrently; retry leave")))
					.then(clearPlayerRoomIfMatching(publicId, code))
					.then(notifyPlayerLeft)
					.then(publishLeave)
					.then(publishHostChanged)
					.then(publishHomeRoomUpdated(updatedRoom))
					.doOnSuccess(ignored -> log.debug("Player {} left room {} (remainingMembers={})", publicId, code, remaining.size()));
			});
	}

	private Mono<Void> clearPlayerRoomIfMatching(UUID publicId, String roomCode) {
		return playerRepository.findById(publicId)
			.flatMap(player -> player.roomCodes().contains(roomCode) ? playerRepository.save(player.withRoomRemoved(roomCode)).then() : Mono.empty());
	}

	/**
	 * Kick a player from the room. Only the host can kick, and the host cannot kick
	 * themselves. The kicked player's cached roomCodes list is updated.
	 *
	 * @param code           the room code
	 * @param publicId       the host's public id
	 * @param secretId       the host's secret id
	 * @param targetPublicId the public id of the player to kick
	 * @return an empty mono on successful kick
	 */
	public Mono<Void> kickPlayer(String code, UUID publicId, UUID secretId, UUID targetPublicId) {
		log.debug("Kick requested: room={} host={} target={}", code, publicId, targetPublicId);
		return playerService.authenticate(publicId, secretId)
			.flatMap(ignored -> roomRepository.findVersionedByCode(code)
				.switchIfEmpty(Mono.error(AtriumException.roomNotFound(code)))
				.flatMap(versionedRoom -> {
					final Room room = versionedRoom.room();
					if (!room.host().equals(publicId)) {
						return Mono.error(AtriumException.forbidden("Only the host can kick players"));
					}

					if (publicId.equals(targetPublicId)) {
						return Mono.error(AtriumException.badRequest("Host cannot kick themselves"));
					}

					if (!room.contains(targetPublicId)) {
						return Mono.error(AtriumException.badRequest("Target is not in this room"));
					}

					final List<UUID> remaining = new ArrayList<>(room.players());
					remaining.remove(targetPublicId);
					final Room updatedRoom = room.withPlayers(remaining);
					return roomRepository.saveIfVersion(updatedRoom, versionedRoom.version())
						.flatMap(saved -> saved ? Mono.empty() : Mono.error(AtriumException.conflict("Room was updated concurrently; retry kick")))
						.then(clearPlayerRoomIfMatching(targetPublicId, code))
						.then(notifyLifecycle(gameLifecycleListener.onPlayerLeft(room, targetPublicId, AtriumConstants.LeaveReasons.KICKED), AtriumConstants.LifecycleHookNames.PLAYER_LEFT + "(" + AtriumConstants.LeaveReasons.KICKED + ")", code))
						.then(broadcastService.publish(new RoomEvent.PlayerKicked(code, Instant.now(), targetPublicId)))
						.then(publishHomeRoomUpdated(updatedRoom))
						.then();
				}))
			.doOnSuccess(ignored -> log.debug("Player {} was kicked from room {} by host {}", targetPublicId, code, publicId));
	}

	/**
	 * Delete a room entirely. Only the host can delete. Removes all members and
	 * broadcasts {@link RoomEvent.RoomDeleted}.
	 *
	 * @param code     the room code
	 * @param publicId the host's public id
	 * @param secretId the host's secret id
	 * @return an empty mono on successful deletion
	 */
	public Mono<Void> deleteRoom(String code, UUID publicId, UUID secretId) {
		log.debug("Delete room requested: room={} by player={}", code, publicId);
		return playerService.authenticate(publicId, secretId)
			.flatMap(ignored -> roomRepository.findByCode(code)
				.switchIfEmpty(Mono.error(AtriumException.roomNotFound(code)))
				.flatMap(room -> {
					if (!room.host().equals(publicId)) {
						return Mono.error(AtriumException.forbidden("Only the host can delete the room"));
					} else {
						return deleteRoomInternal(room);
					}
				}));
	}

	/**
	 * Repository delete + clear every member's room index + broadcast {@link RoomEvent.RoomDeleted}.
	 */
	Mono<Void> deleteRoomInternal(Room room) {
		log.info("Deleting room {} ({} player(s))", room.code(), room.players().size());
		return Flux.fromIterable(room.players())
			.flatMap(publicId -> clearPlayerRoomIfMatching(publicId, room.code()))
			.then()
			.then(roomRepository.delete(room.code()))
			.then(notifyLifecycle(gameLifecycleListener.onRoomDeleted(room), AtriumConstants.LifecycleHookNames.ROOM_DELETED, room.code()))
			.then(broadcastService.publish(new RoomEvent.RoomDeleted(room.code(), Instant.now())))
			.then(publishHomeRoomDeleted(room));
	}

	/**
	 * Transition a room from {@link RoomState#LOBBY} to {@link RoomState#IN_GAME}.
	 * Validates minimum player count and host permission.
	 *
	 * @param code     the room code
	 * @param publicId the host's public id
	 * @param secretId the host's secret id
	 * @return the updated room view
	 */
	public Mono<RoomView> startGame(String code, UUID publicId, UUID secretId) {
		log.debug("Start game requested: room={} host={}", code, publicId);
		return transitionState(code, publicId, secretId, RoomState.LOBBY, RoomState.IN_GAME, "start");
	}

	/**
	 * Transition a room from {@link RoomState#IN_GAME} back to {@link RoomState#LOBBY}.
	 * Keeps the player roster intact.
	 *
	 * @param code     the room code
	 * @param publicId the host's public id
	 * @param secretId the host's secret id
	 * @return the updated room view
	 */
	public Mono<RoomView> stopGame(String code, UUID publicId, UUID secretId) {
		log.debug("Stop game requested: room={} host={}", code, publicId);
		return transitionState(code, publicId, secretId, RoomState.IN_GAME, RoomState.LOBBY, "stop");
	}

	private Mono<RoomView> transitionState(String code, UUID publicId, UUID secretId, RoomState expected, RoomState next, String label) {
		return playerService.authenticate(publicId, secretId)
			.flatMap(ignored -> roomRepository.findVersionedByCode(code)
				.switchIfEmpty(Mono.error(AtriumException.roomNotFound(code)))
				.flatMap(versionedRoom -> {
					final Room room = versionedRoom.room();
					if (!room.host().equals(publicId)) {
						return Mono.error(AtriumException.forbidden("Only the host can " + label + " the game"));
					}

					if (room.state() != expected) {
						return Mono.error(AtriumException.conflict("Room is not in state " + expected));
					}

					if (next == RoomState.IN_GAME && room.players().size() < room.minPlayers()) {
						return Mono.error(AtriumException.conflict("Need at least " + room.minPlayers() + " players to start the game"));
					}

					final Room updatedRoom = room.withState(next);
					return roomRepository.saveIfVersion(updatedRoom, versionedRoom.version())
						.flatMap(saved -> saved ? Mono.empty() : Mono.error(AtriumException.conflict("Room was updated concurrently; retry state transition")))
						.then(next == RoomState.IN_GAME ? notifyLifecycle(gameLifecycleListener.onGameStarted(updatedRoom), AtriumConstants.LifecycleHookNames.GAME_STARTED, code) : notifyLifecycle(gameLifecycleListener.onGameStopped(updatedRoom), AtriumConstants.LifecycleHookNames.GAME_STOPPED, code))
						.then(viewAssembler.assemble(updatedRoom))
						.flatMap(roomView -> broadcastService.publish(new RoomEvent.StateChanged(code, Instant.now(), next))
							.then(publishHomeRoomUpdated(roomView))
							.thenReturn(roomView));
				}))
			.doOnSuccess(roomView -> log.debug("Room {} state transition complete: {} -> {} by host {}", code, expected, next, publicId));
	}

	/**
	 * Update room settings. Only the host can change settings, and only while the room
	 * is in {@link RoomState#LOBBY}. Any field left {@code null} keeps its current value.
	 *
	 * @param code         the room code
	 * @param publicId     the host's public id
	 * @param secretId     the host's secret id
	 * @param name         optional room display name (null keeps current value)
	 * @param minPlayers   optional new minimum player count
	 * @param maxPlayers   optional new maximum player count
	 * @param gameSettings optional new game settings
	 * @param isPrivate    optional new private flag
	 * @return the updated room view
	 */
	public Mono<RoomView> updateSettings(String code, UUID publicId, UUID secretId, @Nullable String name, @Nullable Integer minPlayers, @Nullable Integer maxPlayers, @Nullable GameSettings gameSettings, @Nullable Boolean isPrivate) {
		log.debug("Update settings requested: room={} host={} name={} min={} max={} private={}", code, publicId, name, minPlayers, maxPlayers, isPrivate);
		return playerService.authenticate(publicId, secretId)
			.flatMap(ignored -> roomRepository.findVersionedByCode(code)
				.switchIfEmpty(Mono.error(AtriumException.roomNotFound(code)))
				.flatMap(versionedRoom -> {
					final Room room = versionedRoom.room();
					if (!room.host().equals(publicId)) {
						return Mono.error(AtriumException.forbidden("Only the host can change room settings"));
					}

					if (room.state() != RoomState.LOBBY) {
						return Mono.error(AtriumException.conflict("Settings can only change in the lobby"));
					}

					final GameSettings newSettings = gameSettings != null ? gameSettings : room.gameSettings();
					final String newName = name != null ? cleanRoomName(name) : room.name();
					final RoomPlayerLimitsResolver.NormalizedPlayerLimits normalizedPlayerLimits = roomPlayerLimitsResolver.normalizeForUpdate(minPlayers, maxPlayers, room.minPlayers(), room.maxPlayers(), newSettings);

					if (normalizedPlayerLimits.maxPlayers() < room.players().size()) {
						return Mono.error(AtriumException.badRequest("maxPlayers cannot be below current member count (" + room.players().size() + ")"));
					}

					final boolean newPrivate = isPrivate != null ? isPrivate : room.isPrivate();
					final Room updatedRoom = room.withSettings(newName, normalizedPlayerLimits.minPlayers(), normalizedPlayerLimits.maxPlayers(), newSettings, newPrivate);
					return roomRepository.saveIfVersion(updatedRoom, versionedRoom.version())
						.flatMap(saved -> saved ? Mono.empty() : Mono.error(AtriumException.conflict("Room was updated concurrently; retry settings update")))
						.then(viewAssembler.assemble(updatedRoom))
						.flatMap(roomView -> broadcastService.publish(new RoomEvent.SettingsChanged(code, Instant.now(), roomView))
							.then(publishHomeSettingsChanged(room, roomView))
							.thenReturn(roomView));
				}))
			.doOnSuccess(roomView -> log.debug("Room {} settings updated by host {}", code, publicId));
	}

	/**
	 * Re-broadcast a player's new profile to all their current rooms. Called by
	 * the controller after {@link PlayerService#updateProfile} succeeds.
	 */
	public Mono<Void> broadcastProfileUpdate(Player player) {
		final List<String> roomCodes = player.roomCodes();

		if (roomCodes.isEmpty()) {
			return Mono.empty();
		}

		log.debug("Broadcasting profile update for player {} in rooms {}", player.publicId(), roomCodes);
		return Flux.fromIterable(roomCodes)
			.flatMap(roomCode -> broadcastService.publish(new RoomEvent.PlayerUpdated(roomCode, Instant.now(), viewAssembler.toPlayerView(player)))
				.then(roomRepository.findByCode(roomCode).flatMap(this::publishHomeRoomUpdated)))
			.then();
	}

	/**
	 * Mark a player as {@link PlayerStatus#DISCONNECTED} and broadcast
	 * {@link RoomEvent.PlayerDisconnected} to all rooms they are in.
	 * No-op when they are already disconnected.
	 *
	 * @param publicId the player's public id
	 * @return an empty mono on completion
	 */
	public Mono<Void> markDisconnected(UUID publicId) {
		return playerRepository.findById(publicId)
			.flatMap(player -> {
				if (player.status() == PlayerStatus.DISCONNECTED) {
					return Mono.empty();
				}

				final List<String> roomCodes = player.roomCodes();
				log.debug("Marking player {} as DISCONNECTED (rooms={})", publicId, roomCodes);

				return playerRepository.save(player.withStatus(PlayerStatus.DISCONNECTED))
					.thenMany(Flux.fromIterable(roomCodes)
						.flatMap(roomCode -> broadcastService.publish(new RoomEvent.PlayerDisconnected(roomCode, Instant.now(), publicId))
							.then(roomRepository.findByCode(roomCode).flatMap(this::publishHomeRoomUpdated))))
					.then();
			});
	}

	/**
	 * Mark a player as {@link PlayerStatus#ACTIVE} and broadcast
	 * {@link RoomEvent.PlayerReconnected} to all rooms they are in.
	 * No-op when they are already active.
	 *
	 * @param publicId the player's public id
	 * @return an empty mono on completion
	 */
	public Mono<Void> markReconnected(UUID publicId) {
		return playerRepository.findById(publicId)
			.flatMap(player -> {
				if (player.status() == PlayerStatus.ACTIVE) {
					return Mono.empty();
				}

				final List<String> roomCodes = player.roomCodes();
				log.debug("Marking player {} as ACTIVE (rooms={})", publicId, roomCodes);

				return playerRepository.save(player.withStatus(PlayerStatus.ACTIVE))
					.thenMany(Flux.fromIterable(roomCodes)
						.flatMap(roomCode -> broadcastService.publish(new RoomEvent.PlayerReconnected(roomCode, Instant.now(), publicId))
							.then(roomRepository.findByCode(roomCode).flatMap(this::publishHomeRoomUpdated))))
					.then();
			});
	}

	/**
	 * Publish a {@link HomeEvent.RoomCreated} for public rooms only.
	 * Private rooms are silently skipped.
	 */
	private Mono<Void> publishHomeRoomCreated(RoomView roomView) {
		if (roomView.isPrivate()) {
			return Mono.empty();
		}
		return homeBroadcastService.publish(new HomeEvent.RoomCreated(Instant.now(), roomView)).then();
	}

	/**
	 * Convenience overload that assembles a view from the room entity first,
	 * then delegates to {@link #publishHomeRoomUpdated(RoomView)}.
	 * Private rooms are silently skipped.
	 */
	private Mono<Void> publishHomeRoomUpdated(Room room) {
		if (room.isPrivate()) {
			return Mono.empty();
		}
		return viewAssembler.assemble(room)
			.flatMap(this::publishHomeRoomUpdated);
	}

	/**
	 * Publish a {@link HomeEvent.RoomUpdated} for public rooms only.
	 * Private rooms are silently skipped.
	 */
	private Mono<Void> publishHomeRoomUpdated(RoomView roomView) {
		if (roomView.isPrivate()) {
			return Mono.empty();
		}
		return homeBroadcastService.publish(new HomeEvent.RoomUpdated(Instant.now(), roomView)).then();
	}

	/**
	 * Publish a {@link HomeEvent.RoomDeleted} for public rooms only.
	 * Private rooms are silently skipped.
	 */
	private Mono<Void> publishHomeRoomDeleted(Room room) {
		if (room.isPrivate()) {
			return Mono.empty();
		}
		return homeBroadcastService.publish(new HomeEvent.RoomDeleted(Instant.now(), room.code())).then();
	}

	/**
	 * Publish the appropriate {@link HomeEvent} when room visibility changes.
	 * Handles the private→public ({@code roomCreated}), public→private
	 * ({@code roomDeleted}), and public→public ({@code roomUpdated}) transitions.
	 * No-op when both before and after are private.
	 */
	private Mono<Void> publishHomeSettingsChanged(Room previousRoom, RoomView updatedRoomView) {
		if (previousRoom.isPrivate() && updatedRoomView.isPrivate()) {
			return Mono.empty();
		}
		if (previousRoom.isPrivate()) {
			return publishHomeRoomCreated(updatedRoomView);
		}
		if (updatedRoomView.isPrivate()) {
			return homeBroadcastService.publish(new HomeEvent.RoomDeleted(Instant.now(), previousRoom.code())).then();
		}
		return publishHomeRoomUpdated(updatedRoomView);
	}

	private @Nullable String cleanRoomName(@Nullable String roomName) {
		if (roomName == null) {
			return null;
		}
		final String trimmed = roomName.strip();
		if (trimmed.isEmpty()) {
			return null;
		}
		final int maxLength = properties.getMaxRoomNameLength();
		return trimmed.length() > maxLength ? trimmed.substring(0, maxLength) : trimmed;
	}


	/**
	 * Lifecycle extension hooks should not break the core lobby flow. A failing listener is
	 * logged and ignored so room mutations still complete.
	 */
	private Mono<Void> notifyLifecycle(Mono<Void> hook, String hookName, String roomCode) {
		return hook.onErrorResume(error -> {
			log.warn("Lifecycle hook {} failed for room {}: {}", hookName, roomCode, error.toString());
			return Mono.empty();
		});
	}
}
