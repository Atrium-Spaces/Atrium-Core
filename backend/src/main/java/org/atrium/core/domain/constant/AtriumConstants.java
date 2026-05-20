package org.atrium.core.domain.constant;

import org.atrium.core.autoconfigure.AtriumProperties;
import org.atrium.core.domain.service.RoomCodeGenerator;

import java.util.UUID;

/**
 * Static constants for the Atrium system. All operator-tunable values live in
 * {@link AtriumProperties} — only true compile-time invariants
 * (Redis key prefixes, channel name patterns, room-code alphabet) belong here.
 */
public final class AtriumConstants {

	private AtriumConstants() {
	}

	/**
	 * Alphabet used by {@link RoomCodeGenerator}.
	 */
	public static final String ROOM_CODE_ALPHABET = "ABCDEFGHIJKLMNOPQRSTUVWXYZ0123456789";

	/**
	 * Maximum attempts to find a non-colliding room code before erroring.
	 */
	public static final int ROOM_CODE_GENERATION_MAX_ATTEMPTS = 16;

	public static final String KEY_PREFIX = "atrium:";
	public static final String ROOM_KEY_PREFIX = KEY_PREFIX + "room:";
	public static final String ROOM_VERSION_KEY_PREFIX = KEY_PREFIX + "room:version:";
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

	public static final class RoomEventTypes {
		public static final String PLAYER_JOINED = "playerJoined";
		public static final String PLAYER_LEFT = "playerLeft";
		public static final String PLAYER_KICKED = "playerKicked";
		public static final String PLAYER_UPDATED = "playerUpdated";
		public static final String PLAYER_DISCONNECTED = "playerDisconnected";
		public static final String PLAYER_RECONNECTED = "playerReconnected";
		public static final String HOST_CHANGED = "hostChanged";
		public static final String SETTINGS_CHANGED = "settingsChanged";
		public static final String STATE_CHANGED = "stateChanged";
		public static final String ROOM_DELETED = "roomDeleted";
		public static final String SNAPSHOT = "snapshot";

		private RoomEventTypes() {
		}
	}

	public static final class LifecycleHookNames {
		public static final String ROOM_CREATED = "onRoomCreated";
		public static final String ROOM_DELETED = "onRoomDeleted";
		public static final String PLAYER_JOINED = "onPlayerJoined";
		public static final String PLAYER_LEFT = "onPlayerLeft";
		public static final String GAME_STARTED = "onGameStarted";
		public static final String GAME_STOPPED = "onGameStopped";

		private LifecycleHookNames() {
		}
	}

	public static final class LeaveReasons {
		public static final String LEFT = "left";
		public static final String KICKED = "kicked";

		private LeaveReasons() {
		}
	}

	public static String roomKey(String code) {
		return ROOM_KEY_PREFIX + code;
	}

	public static String playerKey(UUID publicId) {
		return PLAYER_KEY_PREFIX + publicId;
	}

	public static String roomVersionKey(String code) {
		return ROOM_VERSION_KEY_PREFIX + code;
	}

	public static String eventChannel(String roomCode) {
		return EVENT_CHANNEL_PREFIX + roomCode;
	}
}
