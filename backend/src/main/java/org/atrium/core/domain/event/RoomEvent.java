package org.atrium.core.domain.event;

import com.fasterxml.jackson.annotation.JsonSubTypes;
import com.fasterxml.jackson.annotation.JsonTypeInfo;
import com.fasterxml.jackson.annotation.JsonTypeName;
import org.atrium.core.api.dto.PlayerView;
import org.atrium.core.api.dto.RoomView;
import org.atrium.core.domain.constant.AtriumConstants;
import org.atrium.core.domain.model.RoomState;
import org.jspecify.annotations.Nullable;

import java.time.Instant;
import java.util.UUID;

/**
 * Sealed, polymorphic event emitted onto a room's Redis pub/sub channel and forwarded
 * to every connected WebSocket subscriber.
 *
 * <p>Every variant carries the {@link #roomCode()} and the server wall-clock
 * {@link #emittedAt()} so clients can dedupe / reconcile out-of-order delivery without
 * the lobby needing per-channel sequence numbers.
 */
@JsonTypeInfo(use = JsonTypeInfo.Id.NAME, include = JsonTypeInfo.As.PROPERTY, property = "type")
@JsonSubTypes({
	@JsonSubTypes.Type(value = RoomEvent.PlayerJoined.class, name = AtriumConstants.RoomEventTypes.PLAYER_JOINED),
	@JsonSubTypes.Type(value = RoomEvent.PlayerLeft.class, name = AtriumConstants.RoomEventTypes.PLAYER_LEFT),
	@JsonSubTypes.Type(value = RoomEvent.PlayerKicked.class, name = AtriumConstants.RoomEventTypes.PLAYER_KICKED),
	@JsonSubTypes.Type(value = RoomEvent.PlayerUpdated.class, name = AtriumConstants.RoomEventTypes.PLAYER_UPDATED),
	@JsonSubTypes.Type(value = RoomEvent.PlayerDisconnected.class, name = AtriumConstants.RoomEventTypes.PLAYER_DISCONNECTED),
	@JsonSubTypes.Type(value = RoomEvent.PlayerReconnected.class, name = AtriumConstants.RoomEventTypes.PLAYER_RECONNECTED),
	@JsonSubTypes.Type(value = RoomEvent.HostChanged.class, name = AtriumConstants.RoomEventTypes.HOST_CHANGED),
	@JsonSubTypes.Type(value = RoomEvent.SettingsChanged.class, name = AtriumConstants.RoomEventTypes.SETTINGS_CHANGED),
	@JsonSubTypes.Type(value = RoomEvent.StateChanged.class, name = AtriumConstants.RoomEventTypes.STATE_CHANGED),
	@JsonSubTypes.Type(value = RoomEvent.RoomDeleted.class, name = AtriumConstants.RoomEventTypes.ROOM_DELETED),
	@JsonSubTypes.Type(value = RoomEvent.Snapshot.class, name = AtriumConstants.RoomEventTypes.SNAPSHOT),
})
public sealed interface RoomEvent {

	String roomCode();

	Instant emittedAt();

	@JsonTypeName(AtriumConstants.RoomEventTypes.PLAYER_JOINED)
	record PlayerJoined(String roomCode, Instant emittedAt, PlayerView player) implements RoomEvent {
	}

	@JsonTypeName(AtriumConstants.RoomEventTypes.PLAYER_LEFT)
	record PlayerLeft(String roomCode, Instant emittedAt, UUID publicId, @Nullable String reason) implements RoomEvent {
	}

	@JsonTypeName(AtriumConstants.RoomEventTypes.PLAYER_KICKED)
	record PlayerKicked(String roomCode, Instant emittedAt, UUID publicId) implements RoomEvent {
	}

	@JsonTypeName(AtriumConstants.RoomEventTypes.PLAYER_UPDATED)
	record PlayerUpdated(String roomCode, Instant emittedAt, PlayerView player) implements RoomEvent {
	}

	@JsonTypeName(AtriumConstants.RoomEventTypes.PLAYER_DISCONNECTED)
	record PlayerDisconnected(String roomCode, Instant emittedAt, UUID publicId) implements RoomEvent {
	}

	@JsonTypeName(AtriumConstants.RoomEventTypes.PLAYER_RECONNECTED)
	record PlayerReconnected(String roomCode, Instant emittedAt, UUID publicId) implements RoomEvent {
	}

	@JsonTypeName(AtriumConstants.RoomEventTypes.HOST_CHANGED)
	record HostChanged(String roomCode, Instant emittedAt, UUID newHost) implements RoomEvent {
	}

	@JsonTypeName(AtriumConstants.RoomEventTypes.SETTINGS_CHANGED)
	record SettingsChanged(String roomCode, Instant emittedAt, RoomView room) implements RoomEvent {
	}

	@JsonTypeName(AtriumConstants.RoomEventTypes.STATE_CHANGED)
	record StateChanged(String roomCode, Instant emittedAt, RoomState newState) implements RoomEvent {
	}

	@JsonTypeName(AtriumConstants.RoomEventTypes.ROOM_DELETED)
	record RoomDeleted(String roomCode, Instant emittedAt) implements RoomEvent {
	}

	/**
	 * Sent only over the WebSocket on subscribe (never broadcast through Redis) — gives
	 * a new subscriber a complete picture of the room without a separate REST call.
	 */
	@JsonTypeName(AtriumConstants.RoomEventTypes.SNAPSHOT)
	record Snapshot(String roomCode, Instant emittedAt, RoomView room) implements RoomEvent {
	}
}
