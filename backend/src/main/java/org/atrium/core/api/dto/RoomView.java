package org.atrium.core.api.dto;

import org.atrium.core.domain.model.GameSettings;
import org.atrium.core.domain.model.Room;
import org.atrium.core.domain.model.RoomState;

import org.jspecify.annotations.Nullable;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

/**
 * Wire shape for a room, including expanded player profiles. Replaces
 * {@link Room} on the public API surface so callers don't have
 * to do a second round-trip to resolve names / avatars.
 *
 * @param code           unique room code (6-character upper-case alphanumeric)
 * @param name           optional display name for room lists; {@code null} means unnamed
 * @param host           public id of the host player
 * @param players        expanded player profiles in join order
 * @param minPlayers     floor required to start a game
 * @param maxPlayers     cap on player count
 * @param gameSettings   polymorphic game-specific settings
 * @param isPrivate      when {@code true}, hidden from the public listing
 * @param state          {@link RoomState#LOBBY} or {@link RoomState#IN_GAME}
 * @param createdAt      wall-clock creation time
 * @param lastActivityAt last time anything in this room changed
 */
public record RoomView(String code, @Nullable String name, UUID host, List<PlayerView> players, int minPlayers, int maxPlayers, GameSettings gameSettings, boolean isPrivate, RoomState state, Instant createdAt, Instant lastActivityAt) {
}
