package org.atrium.core.domain.event;

import com.fasterxml.jackson.annotation.JsonSubTypes;
import com.fasterxml.jackson.annotation.JsonTypeInfo;
import com.fasterxml.jackson.annotation.JsonTypeName;
import org.atrium.core.api.dto.PlayerView;
import org.atrium.core.api.dto.RoomView;
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
@JsonTypeInfo(
	use = JsonTypeInfo.Id.NAME,
	include = JsonTypeInfo.As.PROPERTY,
	property = "type")
@JsonSubTypes({
	@JsonSubTypes.Type(value = RoomEvent.PlayerJoined.class, name = "playerJoined"),
	@JsonSubTypes.Type(value = RoomEvent.PlayerLeft.class, name = "playerLeft"),
	@JsonSubTypes.Type(value = RoomEvent.PlayerKicked.class, name = "playerKicked"),
	@JsonSubTypes.Type(value = RoomEvent.PlayerUpdated.class, name = "playerUpdated"),
	@JsonSubTypes.Type(value = RoomEvent.PlayerDisconnected.class, name = "playerDisconnected"),
	@JsonSubTypes.Type(value = RoomEvent.PlayerReconnected.class, name = "playerReconnected"),
	@JsonSubTypes.Type(value = RoomEvent.HostChanged.class, name = "hostChanged"),
	@JsonSubTypes.Type(value = RoomEvent.SettingsChanged.class, name = "settingsChanged"),
	@JsonSubTypes.Type(value = RoomEvent.StateChanged.class, name = "stateChanged"),
	@JsonSubTypes.Type(value = RoomEvent.RoomDeleted.class, name = "roomDeleted"),
	@JsonSubTypes.Type(value = RoomEvent.Snapshot.class, name = "snapshot"),
})
public sealed interface RoomEvent {

	String roomCode();

	Instant emittedAt();

	@JsonTypeName("playerJoined")
	record PlayerJoined(String roomCode, Instant emittedAt, PlayerView player) implements RoomEvent {
	}

	@JsonTypeName("playerLeft")
	record PlayerLeft(String roomCode, Instant emittedAt, UUID publicId, @Nullable String reason) implements RoomEvent {
	}

	@JsonTypeName("playerKicked")
	record PlayerKicked(String roomCode, Instant emittedAt, UUID publicId) implements RoomEvent {
	}

	@JsonTypeName("playerUpdated")
	record PlayerUpdated(String roomCode, Instant emittedAt, PlayerView player) implements RoomEvent {
	}

	@JsonTypeName("playerDisconnected")
	record PlayerDisconnected(String roomCode, Instant emittedAt, UUID publicId) implements RoomEvent {
	}

	@JsonTypeName("playerReconnected")
	record PlayerReconnected(String roomCode, Instant emittedAt, UUID publicId) implements RoomEvent {
	}

	@JsonTypeName("hostChanged")
	record HostChanged(String roomCode, Instant emittedAt, UUID newHost) implements RoomEvent {
	}

	@JsonTypeName("settingsChanged")
	record SettingsChanged(String roomCode, Instant emittedAt, RoomView room) implements RoomEvent {
	}

	@JsonTypeName("stateChanged")
	record StateChanged(String roomCode, Instant emittedAt, RoomState newState) implements RoomEvent {
	}

	@JsonTypeName("roomDeleted")
	record RoomDeleted(String roomCode, Instant emittedAt) implements RoomEvent {
	}

	/**
	 * Sent only over the WebSocket on subscribe (never broadcast through Redis) — gives
	 * a new subscriber a complete picture of the room without a separate REST call.
	 */
	@JsonTypeName("snapshot")
	record Snapshot(String roomCode, Instant emittedAt, RoomView room) implements RoomEvent {
	}
}

