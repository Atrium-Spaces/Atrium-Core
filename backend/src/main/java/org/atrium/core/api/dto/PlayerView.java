package org.atrium.core.api.dto;

import org.atrium.core.domain.model.Player;
import org.atrium.core.domain.model.PlayerStatus;

import java.time.Instant;
import java.util.UUID;

/**
 * Public projection of a {@link Player}. Never exposes
 * {@code secretId} or {@code lastActiveAt} (server-internal).
 *
 * <p>{@link #joinedAt} is contextual: inside a {@link RoomView} it is currently an
 * approximate join timestamp derived from the room's creation time, while standalone
 * profile responses reuse the player's last-activity timestamp as the best available
 * server-known time anchor.
 */
public record PlayerView(UUID publicId, String name, String avatar, PlayerStatus status, Instant joinedAt) {
}
