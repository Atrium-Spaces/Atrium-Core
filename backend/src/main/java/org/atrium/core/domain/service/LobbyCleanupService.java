package org.atrium.core.domain.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.atrium.core.autoconfigure.AtriumProperties;
import org.atrium.core.redis.repository.PlayerRepository;
import org.atrium.core.redis.repository.RoomRepository;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.Instant;

/**
 * Periodic Redis sweep that enforces the lobby TTLs:
 *
 * <ul>
 *   <li>Lobby-state rooms idle for &gt; {@code lobbyInactiveTtlSeconds} are deleted.</li>
 *   <li>In-game rooms idle for &gt; {@code inGameInactiveTtlSeconds} are deleted.</li>
 *   <li>Players with no {@code roomCode} idle for &gt; {@code roomlessPlayerTtlSeconds} are deleted.</li>
 * </ul>
 *
 * <p>The {@link Scheduled#fixedRateString} interval is taken from {@code LobbyProperties}.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class LobbyCleanupService {

	private final RoomRepository roomRepository;
	private final PlayerRepository playerRepository;
	private final RoomService roomService;
	private final AtriumProperties properties;

	@Scheduled(fixedDelayString = "${atrium.core.cleanup-interval-seconds:300}000", initialDelayString = "${atrium.core.cleanup-interval-seconds:300}000")
	public void sweep() {
		final Instant now = Instant.now();
		final Instant lobbyCutoff = now.minusSeconds(properties.getLobbyInactiveTtlSeconds());
		final Instant inGameCutoff = now.minusSeconds(properties.getInGameInactiveTtlSeconds());
		final Instant playerCutoff = now.minusSeconds(properties.getRoomlessPlayerTtlSeconds());

		// Use the looser cutoff for the initial scan and filter per-room by state.
		final Instant scanCutoff = inGameCutoff.isAfter(lobbyCutoff) ? inGameCutoff : lobbyCutoff;

		roomRepository.findStaleCodes(scanCutoff)
			.flatMap(roomRepository::findByCode)
			.filter(room -> room.lastActivityAt().isBefore(switch (room.state()) {
				case LOBBY -> lobbyCutoff;
				case IN_GAME -> inGameCutoff;
			}))
			.flatMap(room -> {
				log.info("Cleanup: deleting stale {} room {} (last activity {})", room.state(), room.code(), room.lastActivityAt());
				return roomService.deleteRoomInternal(room);
			})
			.subscribe();

		playerRepository.findStaleIds(playerCutoff)
			.flatMap(playerRepository::findById)
			.filter(player -> player.roomCode() == null && player.lastActiveAt().isBefore(playerCutoff))
			.flatMap(player -> {
				log.info("Cleanup: deleting inactive roomless player {} (last active {})", player.publicId(), player.lastActiveAt());
				return playerRepository.delete(player.publicId());
			})
			.subscribe();
	}
}
