package org.atrium.core.api.dto;

import org.jspecify.annotations.Nullable;

import java.util.UUID;

/**
 * Response for {@code POST /api/lobby/status}. When the requesting client supplied no
 * cookies (or stale ones), the server allocates a fresh identity and returns it here —
 * the client must persist {@link #publicId} and {@link #secretId} as cookies.
 *
 * @param publicId      identity safe to share with other players
 * @param secretId      identity that proves "this is me" to the server
 * @param name          current display name (server may have normalised it)
 * @param avatar        current avatar
 * @param freshIdentity {@code true} when the server just minted these ids
 * @param activeRoom    compact view of the room the player is currently in, if any
 */
public record StatusResponse(
	UUID publicId,
	UUID secretId,
	String name,
	String avatar,
	boolean freshIdentity,
	@Nullable RoomView activeRoom) {
}

