package org.atrium.core.redis.stream;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.atrium.core.domain.constant.AtriumConstants;
import org.atrium.core.domain.event.RoomEvent;
import org.springframework.data.redis.connection.ReactiveSubscription;
import org.springframework.data.redis.core.ReactiveRedisTemplate;
import org.springframework.data.redis.listener.ChannelTopic;
import org.springframework.data.redis.listener.ReactiveRedisMessageListenerContainer;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.util.List;

/**
 * Bridges {@link RoomEvent}s onto Redis pub/sub channels (one channel per room).
 *
 * <p>Outbound: {@link #publish(RoomEvent)} {@code PUBLISH}es the event to
 * {@code lobby:events:{roomCode}}. Inbound: {@link #subscribe(String)} returns a
 * cold {@link Flux} of every event for the given room — the WebSocket handler
 * subscribes once per connection and forwards into the client session.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public final class RoomBroadcastService {

	private final ReactiveRedisTemplate<String, RoomEvent> eventTemplate;
	private final ReactiveRedisMessageListenerContainer listenerContainer;

	/**
	 * Publish a room event onto the room's Redis pub/sub channel.
	 * The event is delivered to all subscribed WebSocket sessions across all instances.
	 *
	 * @param event the room event to publish
	 * @return the number of subscribers that received the message
	 */
	public Mono<Long> publish(RoomEvent event) {
		return Mono.defer(() -> {
			final String channel = AtriumConstants.eventChannel(event.roomCode());
			log.debug("Publishing {} to {}", event.getClass().getSimpleName(), channel);
			return eventTemplate.convertAndSend(channel, event);
		});
	}

	/**
	 * Subscribe to all events for a given room. Returns a cold Flux that receives
	 * events from Redis pub/sub until the subscription is cancelled.
	 *
	 * @param roomCode the room code
	 * @return a flux of room events
	 */
	public Flux<RoomEvent> subscribe(String roomCode) {
		final ChannelTopic topic = ChannelTopic.of(AtriumConstants.eventChannel(roomCode));
		return listenerContainer.receive(List.of(topic), eventTemplate.getSerializationContext().getKeySerializationPair(), eventTemplate.getSerializationContext().getValueSerializationPair())
			.map(ReactiveSubscription.Message::getMessage);
	}
}
