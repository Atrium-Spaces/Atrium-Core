package org.atrium.core.domain.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.atrium.core.api.dto.RoomView;
import org.atrium.core.api.error.AtriumException;
import org.atrium.core.autoconfigure.AtriumProperties;
import org.atrium.core.domain.constant.AtriumConstants;
import org.atrium.core.domain.event.RoomEvent;
import org.atrium.core.domain.model.*;
import org.atrium.core.redis.repository.PlayerRepository;
import org.atrium.core.redis.repository.RoomRepository;
import org.atrium.core.redis.stream.RoomBroadcastService;
import org.atrium.core.spi.listener.GameLifecycleListener;
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
 * </ol>
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class RoomService {

	private final RoomRepository roomRepository;
	private final PlayerRepository playerRepository;
	private final PlayerService playerService;
	private final RoomCodeGenerator codeGenerator;
	private final RoomBroadcastService broadcastService;
	private final RoomViewAssembler viewAssembler;
	private final GameLifecycleListener gameLifecycleListener;
	private final AtriumProperties properties;

	public Mono<RoomView> view(String code) {
		return roomRepository.findByCode(code)
			.switchIfEmpty(Mono.error(AtriumException.roomNotFound(code)))
			.flatMap(viewAssembler::assemble);
	}

	public Flux<RoomView> listPublic(int limit) {
		return roomRepository.listPublic(limit).flatMap(viewAssembler::assemble);
	}


	public Mono<RoomView> createRoom(UUID publicId, UUID secretId, @Nullable Integer requestedMaxPlayers, @Nullable GameSettings requestedSettings, boolean isPrivate) {
		return playerService.authenticate(publicId, secretId)
			.flatMap(player -> {
				if (player.roomCode() != null) {
					return Mono.error(AtriumException.conflict("Player is already in room " + player.roomCode()));
				}

				final int maxPlayers = clampMaxPlayers(requestedMaxPlayers);
				final GameSettings settings = requestedSettings != null ? requestedSettings : new DefaultGameSettings();
				return codeGenerator.next().flatMap(code -> {
					final Instant now = Instant.now();
					final Room room = new Room(code, publicId, List.of(publicId), maxPlayers, settings, isPrivate, RoomState.LOBBY, now, now);
					return roomRepository.save(room)
						.then(playerRepository.save(player.withRoomCode(code)))
						.then(notifyLifecycle(gameLifecycleListener.onRoomCreated(room), AtriumConstants.LifecycleHookNames.ROOM_CREATED, code))
						.then(viewAssembler.assemble(room));
				});
			});
	}

	public Mono<RoomView> joinRoom(String code, UUID publicId, UUID secretId) {
		return playerService.authenticate(publicId, secretId)
			.flatMap(player -> roomRepository.findByCode(code)
				.switchIfEmpty(Mono.error(AtriumException.roomNotFound(code)))
				.flatMap(room -> {
					if (player.roomCode() != null && !player.roomCode().equals(code)) {
						return Mono.error(AtriumException.conflict("Already in room " + player.roomCode()));
					}

					if (room.contains(publicId)) {
						return playerRepository.save(player.withRoomCode(code)).then(viewAssembler.assemble(room));
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
					final Player updatedPlayer = player.withRoomCode(code);
					return roomRepository.save(updatedRoom)
						.then(playerRepository.save(updatedPlayer))
						.then(notifyLifecycle(gameLifecycleListener.onPlayerJoined(updatedRoom, publicId), AtriumConstants.LifecycleHookNames.PLAYER_JOINED, code))
						.then(viewAssembler.assemble(updatedRoom))
						.flatMap(roomView -> broadcastService.publish(new RoomEvent.PlayerJoined(code, Instant.now(), viewAssembler.toPlayerView(updatedPlayer, Instant.now()))).thenReturn(roomView));
				}));
	}

	public Mono<Void> leaveRoom(String code, UUID publicId, UUID secretId) {
		return playerService.authenticate(publicId, secretId).flatMap(ignored -> performLeave(code, publicId, AtriumConstants.LeaveReasons.LEFT));
	}

	/**
	 * Internal leave path used by both the explicit endpoint and the disconnect timer.
	 * Skips secret-id authentication because the caller is the lobby itself.
	 */
	public Mono<Void> performLeave(String code, UUID publicId, @Nullable String reason) {
		return roomRepository.findByCode(code)
			.switchIfEmpty(Mono.empty())
			.flatMap(room -> {
				if (!room.contains(publicId)) {
					return playerRepository.findById(publicId)
						.flatMap(player -> playerRepository.save(player.withRoomCode(null)))
						.then();
				}

				final List<UUID> remaining = new ArrayList<>(room.players());
				remaining.remove(publicId);

				if (remaining.isEmpty()) {
					return deleteRoomInternal(room).then(clearPlayerRoom(publicId));
				}

				final UUID newHost = room.host().equals(publicId) ? remaining.getFirst() : room.host();
				Room updatedRoom = room.withPlayers(remaining);

				if (!newHost.equals(updatedRoom.host())) {
					updatedRoom = updatedRoom.withHost(newHost);
				}

				final Mono<Void> publishLeave = broadcastService.publish(new RoomEvent.PlayerLeft(code, Instant.now(), publicId, reason)).then();
				final Mono<Void> notifyPlayerLeft = notifyLifecycle(gameLifecycleListener.onPlayerLeft(room, publicId, reason), AtriumConstants.LifecycleHookNames.PLAYER_LEFT, code);
				final Mono<Void> publishHostChanged = newHost.equals(room.host()) ? Mono.empty() : broadcastService.publish(new RoomEvent.HostChanged(code, Instant.now(), newHost)).then();
				return roomRepository.save(updatedRoom)
					.then(clearPlayerRoom(publicId))
					.then(notifyPlayerLeft)
					.then(publishLeave)
					.then(publishHostChanged);
			});
	}

	private Mono<Void> clearPlayerRoom(UUID publicId) {
		return playerRepository.findById(publicId)
			.flatMap(player -> playerRepository.save(player.withRoomCode(null)))
			.then();
	}

	public Mono<Void> kickPlayer(String code, UUID publicId, UUID secretId, UUID targetPublicId) {
		return playerService.authenticate(publicId, secretId)
			.flatMap(ignored -> roomRepository.findByCode(code)
				.switchIfEmpty(Mono.error(AtriumException.roomNotFound(code)))
				.flatMap(room -> {
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
					return roomRepository.save(updatedRoom)
						.then(clearPlayerRoom(targetPublicId))
						.then(notifyLifecycle(gameLifecycleListener.onPlayerLeft(room, targetPublicId, AtriumConstants.LeaveReasons.KICKED), AtriumConstants.LifecycleHookNames.PLAYER_LEFT + "(" + AtriumConstants.LeaveReasons.KICKED + ")", code))
						.then(broadcastService.publish(new RoomEvent.PlayerKicked(code, Instant.now(), targetPublicId)))
						.then();
				}));
	}

	public Mono<Void> deleteRoom(String code, UUID publicId, UUID secretId) {
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
			.flatMap(this::clearPlayerRoom)
			.then()
			.then(roomRepository.delete(room.code()))
			.then(notifyLifecycle(gameLifecycleListener.onRoomDeleted(room), AtriumConstants.LifecycleHookNames.ROOM_DELETED, room.code()))
			.then(broadcastService.publish(new RoomEvent.RoomDeleted(room.code(), Instant.now())))
			.then();
	}

	public Mono<RoomView> startGame(String code, UUID publicId, UUID secretId) {
		return transitionState(code, publicId, secretId, RoomState.LOBBY, RoomState.IN_GAME, "start");
	}

	public Mono<RoomView> stopGame(String code, UUID publicId, UUID secretId) {
		return transitionState(code, publicId, secretId, RoomState.IN_GAME, RoomState.LOBBY, "stop");
	}

	private Mono<RoomView> transitionState(String code, UUID publicId, UUID secretId, RoomState expected, RoomState next, String label) {
		return playerService.authenticate(publicId, secretId)
			.flatMap(ignored -> roomRepository.findByCode(code)
				.switchIfEmpty(Mono.error(AtriumException.roomNotFound(code)))
				.flatMap(room -> {
					if (!room.host().equals(publicId)) {
						return Mono.error(AtriumException.forbidden("Only the host can " + label + " the game"));
					}

					if (room.state() != expected) {
						return Mono.error(AtriumException.conflict("Room is not in state " + expected));
					}

					final Room updatedRoom = room.withState(next);
					return roomRepository.save(updatedRoom)
						.then(next == RoomState.IN_GAME ? notifyLifecycle(gameLifecycleListener.onGameStarted(updatedRoom), AtriumConstants.LifecycleHookNames.GAME_STARTED, code) : notifyLifecycle(gameLifecycleListener.onGameStopped(updatedRoom), AtriumConstants.LifecycleHookNames.GAME_STOPPED, code))
						.then(viewAssembler.assemble(updatedRoom))
						.flatMap(roomView -> broadcastService.publish(new RoomEvent.StateChanged(code, Instant.now(), next)).thenReturn(roomView));
				}));
	}

	public Mono<RoomView> updateSettings(String code, UUID publicId, UUID secretId, @Nullable Integer maxPlayers, @Nullable GameSettings gameSettings, @Nullable Boolean isPrivate) {
		return playerService.authenticate(publicId, secretId)
			.flatMap(ignored -> roomRepository.findByCode(code)
				.switchIfEmpty(Mono.error(AtriumException.roomNotFound(code)))
				.flatMap(room -> {
					if (!room.host().equals(publicId)) {
						return Mono.error(AtriumException.forbidden("Only the host can change room settings"));
					}

					if (room.state() != RoomState.LOBBY) {
						return Mono.error(AtriumException.conflict("Settings can only change in the lobby"));
					}

					final int newMaxPlayers = maxPlayers != null ? clampMaxPlayers(maxPlayers) : room.maxPlayers();
					if (newMaxPlayers < room.players().size()) {
						return Mono.error(AtriumException.badRequest("maxPlayers cannot be below current member count (" + room.players().size() + ")"));
					}

					final GameSettings newSettings = gameSettings != null ? gameSettings : room.gameSettings();
					final boolean newPrivate = isPrivate != null ? isPrivate : room.isPrivate();
					final Room updatedRoom = room.withSettings(newMaxPlayers, newSettings, newPrivate);
					return roomRepository.save(updatedRoom)
						.then(viewAssembler.assemble(updatedRoom))
						.flatMap(roomView -> broadcastService.publish(new RoomEvent.SettingsChanged(code, Instant.now(), roomView)).thenReturn(roomView));
				}));
	}

	/**
	 * Re-broadcast a player's new profile to their current room (if any). Called by
	 * the controller after {@link PlayerService#updateProfile} succeeds.
	 */
	public Mono<Void> broadcastProfileUpdate(Player player) {
		final String roomCode = player.roomCode();
		if (roomCode == null) {
			return Mono.empty();
		} else {
			return broadcastService.publish(new RoomEvent.PlayerUpdated(roomCode, Instant.now(), viewAssembler.toPlayerView(player, Instant.now()))).then();
		}
	}

	public Mono<Void> markDisconnected(UUID publicId) {
		return playerRepository.findById(publicId)
			.flatMap(player -> {
				final String roomCode = player.roomCode();
				final Mono<Void> save = playerRepository.save(player.withStatus(PlayerStatus.DISCONNECTED)).then();

				if (roomCode == null) {
					return save;
				} else {
					return save.then(broadcastService.publish(new RoomEvent.PlayerDisconnected(roomCode, Instant.now(), publicId)).then());
				}
			});
	}

	public Mono<Void> markReconnected(UUID publicId) {
		return playerRepository.findById(publicId)
			.flatMap(player -> {
				final String roomCode = player.roomCode();
				final Mono<Void> save = playerRepository.save(player.withStatus(PlayerStatus.ACTIVE)).then();

				if (roomCode == null) {
					return save;
				} else {
					return save.then(broadcastService.publish(new RoomEvent.PlayerReconnected(roomCode, Instant.now(), publicId)).then());
				}
			});
	}

	// ---- helpers -----------------------------------------------------------------------------

	private int clampMaxPlayers(@Nullable Integer requested) {
		int requestedMaxPlayers = requested != null ? requested : properties.getDefaultMaxPlayers();
		if (requestedMaxPlayers < 1) {
			throw AtriumException.badRequest("maxPlayers must be at least 1");
		}
		return Math.min(requestedMaxPlayers, properties.getAbsoluteMaxPlayers());
	}

	/**
	 * Lifecycle SPI hooks should not break the core lobby flow. A failing listener is
	 * logged and ignored so room mutations still complete.
	 */
	private Mono<Void> notifyLifecycle(Mono<Void> hook, String hookName, String roomCode) {
		return hook.onErrorResume(error -> {
			log.warn("Lifecycle hook {} failed for room {}: {}", hookName, roomCode, error.toString());
			return Mono.empty();
		});
	}
}
