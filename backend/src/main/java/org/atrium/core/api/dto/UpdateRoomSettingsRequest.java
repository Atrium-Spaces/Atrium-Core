package org.atrium.core.api.dto;

import jakarta.validation.constraints.NotNull;
import org.atrium.core.domain.model.GameSettings;
import org.jspecify.annotations.Nullable;

import java.util.UUID;

/**
 * Request body for {@code PATCH /api/lobby/rooms/{code}/settings}. Host-only and only
 * permitted while the room is in {@link org.atrium.core.domain.model.RoomState#LOBBY}. Any
 * field left {@code null} keeps its current value.
 */
public record UpdateRoomSettingsRequest(
	@NotNull UUID publicId,
	@NotNull UUID secretId,
	@Nullable Integer maxPlayers,
	@Nullable GameSettings gameSettings,
	@Nullable Boolean isPrivate) {
}

