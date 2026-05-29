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

	/**
	 * Namespace root for all Atrium Redis keys.
	 */
	public static final String KEY_PREFIX = "atrium:";

	/**
	 * Key prefix for room JSON values ({@code atrium:room:{code}}).
	 */
	public static final String ROOM_KEY_PREFIX = KEY_PREFIX + "room:";

	/**
	 * Key prefix for the per-room CAS version counter ({@code atrium:room:version:{code}}).
	 */
	public static final String ROOM_VERSION_KEY_PREFIX = KEY_PREFIX + "room:version:";

	/**
	 * Key prefix for player JSON values ({@code atrium:player:{publicId}}).
	 */
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

	/**
	 * Prefix for all Redis pub/sub event channels ({@code atrium:events:}).
	 */
	public static final String EVENT_CHANNEL_PREFIX = KEY_PREFIX + "events:";

	/**
	 * Redis pub/sub channel for home-screen events (see {@link org.atrium.core.domain.event.HomeEvent}).
	 */
	public static final String HOME_EVENT_CHANNEL = EVENT_CHANNEL_PREFIX + "home";

	/**
	 * Type discriminators for {@link org.atrium.core.domain.event.RoomEvent} subtypes.
	 * Matched against the {@code type} property in the JSON serialised form.
	 */
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

	/**
	 * Type discriminators for {@link org.atrium.core.domain.event.HomeEvent} subtypes.
	 * Matched against the {@code type} property in the JSON serialised form.
	 */
	public static final class HomeEventTypes {
		public static final String SNAPSHOT = "snapshot";
		public static final String ROOM_CREATED = "roomCreated";
		public static final String ROOM_UPDATED = "roomUpdated";
		public static final String ROOM_DELETED = "roomDeleted";

		private HomeEventTypes() {
		}
	}

	/**
	 * String identifiers for {@link org.atrium.core.extension.listener.GameLifecycleListener}
	 * hook names, used in warning log messages when a hook fails.
	 */
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

	/**
	 * String reason tokens carried in {@link org.atrium.core.domain.event.RoomEvent.PlayerLeft}
	 * and passed to {@link org.atrium.core.extension.listener.GameLifecycleListener#onPlayerLeft}.
	 */
	public static final class LeaveReasons {
		public static final String LEFT = "left";
		public static final String KICKED = "kicked";

		private LeaveReasons() {
		}
	}

	/**
	 * Build the Redis key for a room's JSON value.
	 *
	 * @param code the 6-character room code
	 * @return the full Redis key (e.g. {@code atrium:room:ABCDEF})
	 */
	public static String roomKey(String code) {
		return ROOM_KEY_PREFIX + code;
	}

	/**
	 * Build the Redis key for a player's JSON value.
	 *
	 * @param publicId the player's public id
	 * @return the full Redis key (e.g. {@code atrium:player:{uuid}})
	 */
	public static String playerKey(UUID publicId) {
		return PLAYER_KEY_PREFIX + publicId;
	}

	/**
	 * Build the Redis key for a room's CAS version counter.
	 *
	 * @param code the 6-character room code
	 * @return the full Redis key (e.g. {@code atrium:room:version:ABCDEF})
	 */
	public static String roomVersionKey(String code) {
		return ROOM_VERSION_KEY_PREFIX + code;
	}

	/**
	 * Build the Redis pub/sub channel name for a given room.
	 *
	 * @param roomCode the 6-character room code
	 * @return the full channel name (e.g. {@code atrium:events:ABCDEF})
	 */
	public static String eventChannel(String roomCode) {
		return EVENT_CHANNEL_PREFIX + roomCode;
	}
}
