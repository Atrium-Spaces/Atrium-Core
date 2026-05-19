package org.atrium.core.domain.constant;

/**
 * Static constants for the lobby system. All operator-tunable values live in
 * {@link org.atrium.core.autoconfigure.LobbyProperties} — only true compile-time invariants
 * (Redis key prefixes, channel name patterns, room-code alphabet) belong here.
 */
public final class LobbyConstants {

	private LobbyConstants() {
	}

	/**
	 * Alphabet used by {@link org.atrium.core.domain.service.RoomCodeGenerator}.
	 */
	public static final String ROOM_CODE_ALPHABET = "ABCDEFGHIJKLMNOPQRSTUVWXYZ0123456789";

	/**
	 * Maximum attempts to find a non-colliding room code before erroring.
	 */
	public static final int ROOM_CODE_GENERATION_MAX_ATTEMPTS = 16;

	// ---- Redis key namespaces ----------------------------------------------------------------

	public static final String KEY_PREFIX = "lobby:";
	public static final String ROOM_KEY_PREFIX = KEY_PREFIX + "room:";
	public static final String PLAYER_KEY_PREFIX = KEY_PREFIX + "player:";

	/**
	 * Sorted set of public room codes (score = last-activity epoch millis).
	 */
	public static final String PUBLIC_ROOMS_INDEX = KEY_PREFIX + "rooms:public";

	/**
	 * Sorted set of every room code (score = last-activity epoch millis).
	 */
	public static final String ALL_ROOMS_INDEX = KEY_PREFIX + "rooms:all";

	/**
	 * Sorted set of every player public id (score = last-active epoch millis).
	 */
	public static final String ALL_PLAYERS_INDEX = KEY_PREFIX + "players:all";

	public static final String EVENT_CHANNEL_PREFIX = KEY_PREFIX + "events:";

	public static String roomKey(String code) {
		return ROOM_KEY_PREFIX + code;
	}

	public static String playerKey(java.util.UUID publicId) {
		return PLAYER_KEY_PREFIX + publicId;
	}

	public static String eventChannel(String roomCode) {
		return EVENT_CHANNEL_PREFIX + roomCode;
	}
}

