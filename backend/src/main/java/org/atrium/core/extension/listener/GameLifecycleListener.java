package org.atrium.core.extension.listener;

import org.atrium.core.domain.model.Room;
import org.jspecify.annotations.Nullable;
import reactor.core.publisher.Mono;

import java.util.UUID;

/**
 * Extension hook for game projects embedding the lobby library.
 *
 * <p>Implement this interface (as a Spring bean) to react to lobby lifecycle
 * events and bridge them into game-specific orchestration.
 *
 * <p>Examples:
 * <ul>
 *   <li>Allocate match state when a room is created.</li>
 *   <li>Warm game caches when host presses start.</li>
 *   <li>Persist analytics when a room is deleted.</li>
 * </ul>
 *
 * <p>Default methods are no-op so consumers can override only the events they
 * care about.
 */
public interface GameLifecycleListener {

	default Mono<Void> onRoomCreated(Room room) {
		return Mono.empty();
	}

	default Mono<Void> onRoomDeleted(Room room) {
		return Mono.empty();
	}

	default Mono<Void> onPlayerJoined(Room room, UUID playerPublicId) {
		return Mono.empty();
	}

	default Mono<Void> onPlayerLeft(Room room, UUID playerPublicId, @Nullable String reason) {
		return Mono.empty();
	}

	default Mono<Void> onGameStarted(Room room) {
		return Mono.empty();
	}

	default Mono<Void> onGameStopped(Room room) {
		return Mono.empty();
	}
}
