package org.atrium.core.api.dto;

import jakarta.validation.constraints.NotNull;
import org.atrium.core.domain.model.GameSettings;
import org.jspecify.annotations.Nullable;

import java.util.UUID;

/**
 * Request body for {@code POST /api/lobby/rooms}. The host's identity is taken from
 * the {@code publicId}/{@code secretId} pair (matched against the player store), the
 * rest of the fields configure the new room. When {@link #gameSettings} is
 * {@code null} the server falls back to {@link org.atrium.core.domain.model.DefaultGameSettings}.
 */
public record CreateRoomRequest(
	@NotNull UUID publicId,
	@NotNull UUID secretId,
	@Nullable Integer maxPlayers,
	@Nullable GameSettings gameSettings,
	boolean isPrivate) {
}

