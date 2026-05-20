package org.atrium.core.api.dto;

import org.atrium.core.domain.model.Player;
import org.atrium.core.domain.model.PlayerStatus;

import java.time.Instant;
import java.util.UUID;

/**
 * Public projection of a {@link Player}. Never exposes
 * {@code secretId} or {@code lastActiveAt} (server-internal).
 */
public record PlayerView(UUID publicId, String name, String avatar, PlayerStatus status, Instant joinedAt) {
}
