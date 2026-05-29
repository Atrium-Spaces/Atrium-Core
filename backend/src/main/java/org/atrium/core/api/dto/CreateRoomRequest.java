package org.atrium.core.api.dto;

import jakarta.validation.constraints.NotNull;
import org.atrium.core.domain.model.DefaultGameSettings;
import org.atrium.core.domain.model.GameSettings;
import org.jspecify.annotations.Nullable;

import java.util.UUID;

/**
 * Request body for {@code POST /api/atrium/rooms}. The host's identity is taken from
 * the {@code publicId}/{@code secretId} pair (matched against the player store), the
 * rest of the fields configure the new room. When {@link #gameSettings} is
 * {@code null} the server falls back to {@link DefaultGameSettings}. {@link #name}
 * is optional. {@link #minPlayers} and {@link #maxPlayers} are also optional; when
 * provided, the server swaps them if they are reversed and clamps them into the
 * room's effective absolute bounds instead of rejecting the request.
 */
public record CreateRoomRequest(@NotNull UUID publicId, @NotNull UUID secretId, @Nullable String name, @Nullable Integer minPlayers, @Nullable Integer maxPlayers, @Nullable GameSettings gameSettings, boolean isPrivate) {
}
