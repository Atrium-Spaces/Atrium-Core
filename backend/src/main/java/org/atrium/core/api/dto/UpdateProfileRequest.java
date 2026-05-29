package org.atrium.core.api.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

import java.util.UUID;

/**
 * Request body for {@code POST /api/atrium/profile}. May be sent at any time — in or
 * out of a room — and is broadcast to all room peers when the player is inside one.
 * {@link #avatar} is optional (may be blank). The endpoint responds with the resulting
 * {@link PlayerView} so callers can observe any server-side normalisation such as
 * truncation.
 */
public record UpdateProfileRequest(@NotNull UUID publicId, @NotNull UUID secretId, @NotBlank String name, String avatar) {
}
