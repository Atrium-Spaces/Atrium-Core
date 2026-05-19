package org.atrium.core.api.dto;

import java.util.List;

/**
 * Response body for {@code GET /api/atrium/rooms}.
 */
public record RoomListResponse(List<RoomView> rooms) {
}
