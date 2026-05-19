package org.atrium.core.api.dto;

import jakarta.validation.constraints.NotNull;

import java.util.UUID;

/**
 * Request body for {@code POST /api/lobby/rooms/{code}/join}.
 */
public record JoinRoomRequest(
	@NotNull UUID publicId,
	@NotNull UUID secretId) {
}

