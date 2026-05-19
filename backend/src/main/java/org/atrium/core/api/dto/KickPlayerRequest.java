package org.atrium.core.api.dto;

import jakarta.validation.constraints.NotNull;

import java.util.UUID;

/**
 * Request body for {@code POST /api/lobby/rooms/{code}/kick}.
 */
public record KickPlayerRequest(
	@NotNull UUID publicId,
	@NotNull UUID secretId,
	@NotNull UUID targetPublicId) {
}

