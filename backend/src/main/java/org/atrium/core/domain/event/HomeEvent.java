package org.atrium.core.domain.event;

import com.fasterxml.jackson.annotation.JsonSubTypes;
import com.fasterxml.jackson.annotation.JsonTypeInfo;
import com.fasterxml.jackson.annotation.JsonTypeName;
import org.atrium.core.api.dto.RoomView;
import org.atrium.core.domain.constant.AtriumConstants;

import java.time.Instant;
import java.util.List;

/**
 * Sealed event family for the home-screen room-list stream.
 *
 * <p>Published on {@code atrium:events:home} and forwarded by
 * {@code HomeWebSocketHandler}. The first frame for each socket is always
 * {@link Snapshot}.
 */
@JsonTypeInfo(use = JsonTypeInfo.Id.NAME, include = JsonTypeInfo.As.PROPERTY, property = "type")
@JsonSubTypes({
	@JsonSubTypes.Type(value = HomeEvent.Snapshot.class, name = AtriumConstants.HomeEventTypes.SNAPSHOT),
	@JsonSubTypes.Type(value = HomeEvent.RoomCreated.class, name = AtriumConstants.HomeEventTypes.ROOM_CREATED),
	@JsonSubTypes.Type(value = HomeEvent.RoomUpdated.class, name = AtriumConstants.HomeEventTypes.ROOM_UPDATED),
	@JsonSubTypes.Type(value = HomeEvent.RoomDeleted.class, name = AtriumConstants.HomeEventTypes.ROOM_DELETED),
})
public sealed interface HomeEvent {

	Instant emittedAt();

	@JsonTypeName(AtriumConstants.HomeEventTypes.SNAPSHOT)
	record Snapshot(Instant emittedAt, List<RoomView> rooms) implements HomeEvent {
	}

	@JsonTypeName(AtriumConstants.HomeEventTypes.ROOM_CREATED)
	record RoomCreated(Instant emittedAt, RoomView room) implements HomeEvent {
	}

	@JsonTypeName(AtriumConstants.HomeEventTypes.ROOM_UPDATED)
	record RoomUpdated(Instant emittedAt, RoomView room) implements HomeEvent {
	}

	@JsonTypeName(AtriumConstants.HomeEventTypes.ROOM_DELETED)
	record RoomDeleted(Instant emittedAt, String roomCode) implements HomeEvent {
	}
}
