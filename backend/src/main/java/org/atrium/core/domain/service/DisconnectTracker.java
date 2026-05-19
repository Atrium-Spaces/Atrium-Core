package org.atrium.core.domain.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.atrium.core.autoconfigure.AtriumProperties;
import org.springframework.stereotype.Component;
import reactor.core.Disposable;
import reactor.core.publisher.Mono;
import reactor.core.scheduler.Schedulers;

import java.time.Duration;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

/**
 * Tracks per-player disconnect grace timers. A {@link reactor.core.Disposable} per
 * {@link UUID} is held in memory — a host restart resets these (the player will
 * simply finish leaving on the next inactivity sweep instead).
 *
 * <p>Wired in by {@link org.atrium.core.websocket.RoomWebSocketHandler} (the WebSocket bridge): on
 * disconnect, {@link #scheduleLeave} schedules the official leave logic to run after
 * {@link AtriumProperties#getDisconnectGracePeriodSeconds()} seconds. On reconnect,
 * {@link #cancel(UUID)} cancels the pending timer.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class DisconnectTracker {

	private final ConcurrentMap<UUID, Disposable> pendingLeaves = new ConcurrentHashMap<>();
	private final AtriumProperties properties;

	public void scheduleLeave(UUID publicId, Mono<Void> leaveLogic) {
		cancel(publicId);
		final Duration gracePeriod = Duration.ofSeconds(properties.getDisconnectGracePeriodSeconds());
		log.debug("Scheduling leave for {} in {}s", publicId, gracePeriod.toSeconds());
		pendingLeaves.put(publicId, Mono.delay(gracePeriod, Schedulers.parallel())
			.flatMap(ignored -> {
				pendingLeaves.remove(publicId);
				return leaveLogic.doOnError(e -> log.warn("Disconnect leave failed for {}: {}", publicId, e.toString()))
					.onErrorResume(e -> Mono.empty());
			})
			.subscribe());
	}

	public boolean cancel(UUID publicId) {
		final Disposable existing = pendingLeaves.remove(publicId);
		if (existing != null && !existing.isDisposed()) {
			existing.dispose();
			log.debug("Cancelled pending leave for {}", publicId);
			return true;
		} else {
			return false;
		}
	}
}
