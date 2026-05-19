package org.atrium.core.api.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

import java.util.UUID;

/**
 * Request body for {@code POST /api/atrium/profile}. May be sent at any time — in or
 * out of a room — and is broadcast to all room peers when the player is inside one.
 */
public record UpdateProfileRequest(@NotNull UUID publicId, @NotNull UUID secretId, @NotBlank String name, @NotBlank String avatar) {
}
