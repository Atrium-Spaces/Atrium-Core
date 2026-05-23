package org.atrium.core.api.dto;

import jakarta.validation.constraints.NotNull;

import java.util.UUID;

/**
 * Authenticated no-body request — used by {@code status}, {@code join}, {@code leave},
 * {@code delete}, {@code start game} and {@code stop game} endpoints. Sent as a JSON
 * body rather than headers so the secret id never lands in access logs.
 */
public record AuthenticatedRequest(@NotNull UUID publicId, @NotNull UUID secretId) {
}
