package org.atrium.core.api.dto;

import java.util.List;
import java.util.UUID;

/**
 * Response for {@code POST /api/atrium/status}. When the requesting client supplied no
 * cookies (or stale ones), the server allocates a fresh identity and returns it here —
 * the client must persist {@link #publicId} and {@link #secretId} as cookies.
 *
 * @param publicId      identity safe to share with other players
 * @param secretId      identity that proves "this is me" to the server
 * @param name          current display name (server may have normalised it)
 * @param avatar        current avatar
 * @param freshIdentity {@code true} when the server just minted these ids
 * @param activeRooms   compact views of the rooms the player is currently in; empty list means none
 */
public record StatusResponse(UUID publicId, UUID secretId, String name, String avatar, boolean freshIdentity, List<RoomView> activeRooms) {
}
