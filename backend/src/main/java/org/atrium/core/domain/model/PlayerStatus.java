package org.atrium.core.domain.model;

/**
 * Connection status of a {@link Player} as observed by Atrium. Does not affect game
 * logic — it's only used to drive UI presence hints.
 */
public enum PlayerStatus {
	/**
	 * WebSocket is open (or the player has no room and is just browsing).
	 */
	ACTIVE,
	/**
	 * WebSocket dropped while the player remained in a room.
	 */
	DISCONNECTED
}
