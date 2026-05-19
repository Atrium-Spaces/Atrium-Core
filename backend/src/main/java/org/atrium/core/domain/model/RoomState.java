package org.atrium.core.domain.model;

/**
 * High-level lifecycle state of a {@link Room}.
 */
public enum RoomState {
	/**
	 * Room is open for joining; settings and player roster can still change.
	 */
	LOBBY,
	/**
	 * Game is currently being played; only the host can stop it (back to {@link #LOBBY}).
	 */
	IN_GAME
}
