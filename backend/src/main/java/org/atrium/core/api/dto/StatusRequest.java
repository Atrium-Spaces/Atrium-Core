package org.atrium.core.api.dto;

import org.jspecify.annotations.Nullable;

import java.util.UUID;

/**
 * Request body for {@code POST /api/atrium/status}. Both fields may be {@code null} on
 * a first-time visitor — the server will then allocate fresh ids and return them.
 */
public record StatusRequest(@Nullable UUID publicId, @Nullable UUID secretId, @Nullable String name, @Nullable String avatar) {
}
