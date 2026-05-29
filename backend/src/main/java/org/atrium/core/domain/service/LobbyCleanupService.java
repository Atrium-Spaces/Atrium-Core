package org.atrium.core.domain.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

import org.atrium.core.autoconfigure.AtriumProperties;
import org.atrium.core.domain.model.Room;
import org.atrium.core.redis.repository.PlayerRepository;
import org.atrium.core.redis.repository.RoomRepository;

import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.time.Instant;

/**
 * Periodic Redis sweep for stale entities using one shared inactivity threshold.
 *
 * <p>On each run:
 * <ol>
 *   <li>Delete rooms inactive beyond {@code atrium.core.cleanup-inactive-seconds}.</li>
 *   <li>From each deleted room, delete members who are also inactive beyond the same threshold.</li>
 *   <li>Delete roomless players inactive beyond the same threshold.</li>
 * </ol>
 */
@Slf4j
@Component
@RequiredArgsConstructor
public final class LobbyCleanupService {

	private final AtriumProperties properties;
	private final RoomRepository roomRepository;
	private final PlayerRepository playerRepository;
	private final RoomService roomService;

	@Scheduled(fixedDelay = 300_000, initialDelay = 300_000)
	public void sweep() {
		final Instant inactiveCutoff = Instant.now().minusSeconds(properties.getCleanupInactiveSeconds());

		cleanupInactiveRooms(inactiveCutoff)
			.then(cleanupInactiveRoomlessPlayers(inactiveCutoff))
			.doOnError(error -> log.error("Lobby cleanup sweep failed", error))
			.block();
	}

	private Mono<Void> cleanupInactiveRooms(Instant inactiveCutoff) {
		return roomRepository.findStaleCodes(inactiveCutoff)
			.flatMap(roomRepository::findByCode)
			.filter(room -> room.lastActivityAt().isBefore(inactiveCutoff))
			.flatMap(room -> cleanupInactivePlayersInRoom(room, inactiveCutoff)
				.then(Mono.defer(() -> {
					log.info("Cleanup: deleting stale room {} (state={}, last activity={})", room.code(), room.state(), room.lastActivityAt());
					return roomService.deleteRoomInternal(room);
				})))
			.then();
	}

	private Mono<Void> cleanupInactivePlayersInRoom(Room room, Instant inactiveCutoff) {
		return Flux.fromIterable(room.players())
			.flatMap(playerRepository::findById)
			.filter(player -> player.lastActiveAt().isBefore(inactiveCutoff))
			.flatMap(player -> {
				log.info("Cleanup: deleting inactive player {} from stale room {} (last active={})", player.publicId(), room.code(), player.lastActiveAt());
				return playerRepository.delete(player.publicId());
			})
			.then();
	}

	private Mono<Void> cleanupInactiveRoomlessPlayers(Instant inactiveCutoff) {
		return playerRepository.findStaleIds(inactiveCutoff)
			.flatMap(playerRepository::findById)
			.filter(player -> player.roomCodes().isEmpty() && player.lastActiveAt().isBefore(inactiveCutoff))
			.flatMap(player -> {
				log.info("Cleanup: deleting inactive roomless player {} (last active={})", player.publicId(), player.lastActiveAt());
				return playerRepository.delete(player.publicId());
			})
			.then();
	}
}
