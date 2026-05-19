package org.atrium.core.domain.service;

import it.unimi.dsi.fastutil.objects.ObjectArrayList;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.jspecify.annotations.Nullable;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import org.atrium.core.api.dto.PlayerView;
import org.atrium.core.api.dto.RoomView;
import org.atrium.core.api.error.LobbyException;
import org.atrium.core.autoconfigure.LobbyProperties;
import org.atrium.core.domain.event.RoomEvent;
import org.atrium.core.domain.model.DefaultGameSettings;
import org.atrium.core.domain.model.GameSettings;
import org.atrium.core.domain.model.Player;
import org.atrium.core.domain.model.PlayerStatus;
import org.atrium.core.domain.model.Room;
import org.atrium.core.domain.model.RoomState;
import org.atrium.core.redis.repository.PlayerRepository;
import org.atrium.core.redis.repository.RoomRepository;
import org.atrium.core.redis.stream.RoomBroadcastService;
import org.atrium.core.spi.listener.GameLifecycleListener;

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
	private final LobbyProperties properties;

	// ---- queries -----------------------------------------------------------------------------

	public Mono<RoomView> view(String code) {
		return roomRepository.findByCode(code)
			.switchIfEmpty(Mono.error(LobbyException.roomNotFound(code)))
			.flatMap(viewAssembler::assemble);
	}

	public Flux<RoomView> listPublic(int limit) {
		return roomRepository.listPublic(limit).flatMap(viewAssembler::assemble);
	}

	public Mono<Room> raw(String code) {
		return roomRepository.findByCode(code)
			.switchIfEmpty(Mono.error(LobbyException.roomNotFound(code)));
	}

	// ---- create ------------------------------------------------------------------------------

	public Mono<RoomView> createRoom(
		UUID publicId,
		UUID secretId,
		@Nullable Integer requestedMaxPlayers,
		@Nullable GameSettings requestedSettings,
		boolean isPrivate) {
		return playerService.authenticate(publicId, secretId)
			.flatMap(player -> {
				if (player.roomCode() != null) {
					return Mono.error(LobbyException.conflict("Player is already in room " + player.roomCode()));
				}
				int maxPlayers = clampMaxPlayers(requestedMaxPlayers);
				GameSettings settings = requestedSettings != null ? requestedSettings : new DefaultGameSettings();
				return codeGenerator.next().flatMap(code -> {
					Instant now = Instant.now();
					List<UUID> players = new ObjectArrayList<>();
					players.add(publicId);
					Room room = new Room(code, publicId, List.copyOf(players), maxPlayers, settings, isPrivate, RoomState.LOBBY, now, now);
					Player updatedPlayer = player.withRoomCode(code);
					return roomRepository.save(room)
						.then(playerRepository.save(updatedPlayer))
						.then(notifyLifecycle(gameLifecycleListener.onRoomCreated(room), "onRoomCreated", code))
						.then(viewAssembler.assemble(room));
				});
			});
	}

	// ---- join --------------------------------------------------------------------------------

	public Mono<RoomView> joinRoom(String code, UUID publicId, UUID secretId) {
		return playerService.authenticate(publicId, secretId)
			.flatMap(player -> roomRepository.findByCode(code)
				.switchIfEmpty(Mono.error(LobbyException.roomNotFound(code)))
				.flatMap(room -> {
					if (player.roomCode() != null && !player.roomCode().equals(code)) {
						return Mono.error(LobbyException.conflict("Already in room " + player.roomCode()));
					}
					if (room.contains(publicId)) {
						// Already a member — idempotent rejoin (e.g. fresh tab).
						return playerRepository.save(player.withRoomCode(code))
							.then(viewAssembler.assemble(room));
					}
					if (room.state() != RoomState.LOBBY) {
						return Mono.error(LobbyException.forbidden("Room is no longer accepting players"));
					}
					if (room.isFull()) {
						return Mono.error(LobbyException.forbidden("Room is full"));
					}
					List<UUID> newPlayers = new ArrayList<>(room.players());
					newPlayers.add(publicId);
					Room updated = room.withPlayers(newPlayers);
					Player updatedPlayer = player.withRoomCode(code);
					return roomRepository.save(updated)
						.then(playerRepository.save(updatedPlayer))
						.then(notifyLifecycle(gameLifecycleListener.onPlayerJoined(updated, publicId), "onPlayerJoined", code))
						.then(viewAssembler.assemble(updated))
						.flatMap(view -> broadcastService.publish(
								new RoomEvent.PlayerJoined(
									code,
									Instant.now(),
									viewAssembler.toView(updatedPlayer, Instant.now())))
							.thenReturn(view));
				}));
	}

	// ---- leave -------------------------------------------------------------------------------

	public Mono<Void> leaveRoom(String code, UUID publicId, UUID secretId) {
		return playerService.authenticate(publicId, secretId)
			.flatMap(player -> performLeave(code, publicId, "left"));
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
				List<UUID> remaining = new ArrayList<>(room.players());
				remaining.remove(publicId);
				if (remaining.isEmpty()) {
					return deleteRoomInternal(room).then(clearPlayerRoom(publicId));
				}
				UUID newHost = room.host().equals(publicId) ? remaining.get(0) : room.host();
				Room updated = room.withPlayers(remaining);
				if (!newHost.equals(updated.host())) {
					updated = updated.withHost(newHost);
				}
				Room toPersist = updated;
				Mono<Void> publishLeave = broadcastService
					.publish(new RoomEvent.PlayerLeft(code, Instant.now(), publicId, reason))
					.then();
				Mono<Void> notifyPlayerLeft = notifyLifecycle(
					gameLifecycleListener.onPlayerLeft(room, publicId, reason),
					"onPlayerLeft",
					code);
				Mono<Void> publishHostChanged = newHost.equals(room.host())
					? Mono.empty()
					: broadcastService
					.publish(new RoomEvent.HostChanged(code, Instant.now(), newHost))
					.then();
				return roomRepository.save(toPersist)
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

	// ---- kick --------------------------------------------------------------------------------

	public Mono<Void> kickPlayer(String code, UUID publicId, UUID secretId, UUID targetPublicId) {
		return playerService.authenticate(publicId, secretId)
			.flatMap(host -> roomRepository.findByCode(code)
				.switchIfEmpty(Mono.error(LobbyException.roomNotFound(code)))
				.flatMap(room -> {
					if (!room.host().equals(publicId)) {
						return Mono.error(LobbyException.forbidden("Only the host can kick players"));
					}
					if (publicId.equals(targetPublicId)) {
						return Mono.error(LobbyException.badRequest("Host cannot kick themselves"));
					}
					if (!room.contains(targetPublicId)) {
						return Mono.error(LobbyException.badRequest("Target is not in this room"));
					}
					List<UUID> remaining = new ArrayList<>(room.players());
					remaining.remove(targetPublicId);
					Room updated = room.withPlayers(remaining);
					return roomRepository.save(updated)
						.then(clearPlayerRoom(targetPublicId))
						.then(notifyLifecycle(
							gameLifecycleListener.onPlayerLeft(room, targetPublicId, "kicked"),
							"onPlayerLeft(kicked)",
							code))
						.then(broadcastService.publish(
							new RoomEvent.PlayerKicked(code, Instant.now(), targetPublicId)))
						.then();
				}));
	}

	// ---- delete ------------------------------------------------------------------------------

	public Mono<Void> deleteRoom(String code, UUID publicId, UUID secretId) {
		return playerService.authenticate(publicId, secretId)
			.flatMap(host -> roomRepository.findByCode(code)
				.switchIfEmpty(Mono.error(LobbyException.roomNotFound(code)))
				.flatMap(room -> {
					if (!room.host().equals(publicId)) {
						return Mono.error(LobbyException.forbidden("Only the host can delete the room"));
					}
					return deleteRoomInternal(room);
				}));
	}

	/**
	 * Repository delete + clear every member's room index + broadcast {@link RoomEvent.RoomDeleted}.
	 */
	Mono<Void> deleteRoomInternal(Room room) {
		log.info("Deleting room {} ({} player(s))", room.code(), room.players().size());
		Mono<Void> clearMembers = Flux.fromIterable(room.players())
			.flatMap(this::clearPlayerRoom)
			.then();
		return clearMembers
			.then(roomRepository.delete(room.code()))
			.then(notifyLifecycle(gameLifecycleListener.onRoomDeleted(room), "onRoomDeleted", room.code()))
			.then(broadcastService.publish(new RoomEvent.RoomDeleted(room.code(), Instant.now())))
			.then();
	}

	// ---- start / stop game -------------------------------------------------------------------

	public Mono<RoomView> startGame(String code, UUID publicId, UUID secretId) {
		return transitionState(code, publicId, secretId, RoomState.LOBBY, RoomState.IN_GAME, "start");
	}

	public Mono<RoomView> stopGame(String code, UUID publicId, UUID secretId) {
		return transitionState(code, publicId, secretId, RoomState.IN_GAME, RoomState.LOBBY, "stop");
	}

	private Mono<RoomView> transitionState(
		String code, UUID publicId, UUID secretId, RoomState expected, RoomState next, String label) {
		return playerService.authenticate(publicId, secretId)
			.flatMap(host -> roomRepository.findByCode(code)
				.switchIfEmpty(Mono.error(LobbyException.roomNotFound(code)))
				.flatMap(room -> {
					if (!room.host().equals(publicId)) {
						return Mono.error(LobbyException.forbidden("Only the host can " + label + " the game"));
					}
					if (room.state() != expected) {
						return Mono.error(LobbyException.conflict("Room is not in state " + expected));
					}
					Room updated = room.withState(next);
					return roomRepository.save(updated)
						.then(next == RoomState.IN_GAME
							? notifyLifecycle(gameLifecycleListener.onGameStarted(updated), "onGameStarted", code)
							: notifyLifecycle(gameLifecycleListener.onGameStopped(updated), "onGameStopped", code))
						.then(viewAssembler.assemble(updated))
						.flatMap(view -> broadcastService
							.publish(new RoomEvent.StateChanged(code, Instant.now(), next))
							.thenReturn(view));
				}));
	}

	// ---- settings ----------------------------------------------------------------------------

	public Mono<RoomView> updateSettings(
		String code,
		UUID publicId,
		UUID secretId,
		@Nullable Integer maxPlayers,
		@Nullable GameSettings gameSettings,
		@Nullable Boolean isPrivate) {
		return playerService.authenticate(publicId, secretId)
			.flatMap(host -> roomRepository.findByCode(code)
				.switchIfEmpty(Mono.error(LobbyException.roomNotFound(code)))
				.flatMap(room -> {
					if (!room.host().equals(publicId)) {
						return Mono.error(LobbyException.forbidden("Only the host can change room settings"));
					}
					if (room.state() != RoomState.LOBBY) {
						return Mono.error(LobbyException.conflict("Settings can only change in the lobby"));
					}
					int newMax = maxPlayers != null ? clampMaxPlayers(maxPlayers) : room.maxPlayers();
					if (newMax < room.players().size()) {
						return Mono.error(LobbyException.badRequest(
							"maxPlayers cannot be below current member count (" + room.players().size() + ")"));
					}
					GameSettings newSettings = gameSettings != null ? gameSettings : room.gameSettings();
					boolean newPrivate = isPrivate != null ? isPrivate : room.isPrivate();
					Room updated = room.withSettings(newMax, newSettings, newPrivate);
					return roomRepository.save(updated)
						.then(viewAssembler.assemble(updated))
						.flatMap(view -> broadcastService
							.publish(new RoomEvent.SettingsChanged(code, Instant.now(), view))
							.thenReturn(view));
				}));
	}

	// ---- profile change while in a room ------------------------------------------------------

	/**
	 * Re-broadcast a player's new profile to their current room (if any). Called by
	 * the controller after {@link PlayerService#updateProfile} succeeds.
	 */
	public Mono<Void> broadcastProfileUpdate(Player player) {
		String roomCode = player.roomCode();
		if (roomCode == null) {
			return Mono.empty();
		}
		PlayerView view = viewAssembler.toView(player, Instant.now());
		return broadcastService.publish(new RoomEvent.PlayerUpdated(roomCode, Instant.now(), view)).then();
	}

	// ---- WebSocket lifecycle hooks -----------------------------------------------------------

	public Mono<Void> markDisconnected(UUID publicId) {
		return playerRepository.findById(publicId)
			.flatMap(player -> {
				String roomCode = player.roomCode();
				Mono<Void> save = playerRepository.save(player.withStatus(PlayerStatus.DISCONNECTED)).then();
				if (roomCode == null) {
					return save;
				}
				return save.then(broadcastService
					.publish(new RoomEvent.PlayerDisconnected(roomCode, Instant.now(), publicId)).then());
			});
	}

	public Mono<Void> markReconnected(UUID publicId) {
		return playerRepository.findById(publicId)
			.flatMap(player -> {
				String roomCode = player.roomCode();
				Mono<Void> save = playerRepository.save(player.withStatus(PlayerStatus.ACTIVE)).then();
				if (roomCode == null) {
					return save;
				}
				return save.then(broadcastService
					.publish(new RoomEvent.PlayerReconnected(roomCode, Instant.now(), publicId)).then());
			});
	}

	// ---- helpers -----------------------------------------------------------------------------

	private int clampMaxPlayers(@Nullable Integer requested) {
		int value = requested != null ? requested : properties.getDefaultMaxPlayers();
		if (value < 1) {
			throw LobbyException.badRequest("maxPlayers must be at least 1");
		}
		return Math.min(value, properties.getAbsoluteMaxPlayers());
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

