package org.atrium.core.api.dto;

import org.atrium.core.domain.model.GameSettings;
import org.atrium.core.domain.model.Room;
import org.atrium.core.domain.model.RoomState;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

/**
 * Wire shape for a room, including expanded player profiles. Replaces
 * {@link Room} on the public API surface so callers don't have
 * to do a second round-trip to resolve names / avatars.
 */
public record RoomView(String code, UUID host, List<PlayerView> players, int minPlayers, int maxPlayers, GameSettings gameSettings, boolean isPrivate, RoomState state, Instant createdAt, Instant lastActivityAt) {
}
