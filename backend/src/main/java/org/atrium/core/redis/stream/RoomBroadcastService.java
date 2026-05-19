package org.atrium.core.redis.stream;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.connection.ReactiveSubscription;
import org.springframework.data.redis.core.ReactiveRedisTemplate;
import org.springframework.data.redis.listener.ChannelTopic;
import org.springframework.data.redis.listener.ReactiveRedisMessageListenerContainer;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import org.atrium.core.domain.constant.LobbyConstants;
import org.atrium.core.domain.event.RoomEvent;

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
public class RoomBroadcastService {

	private final ReactiveRedisTemplate<String, RoomEvent> eventTemplate;
	private final ReactiveRedisMessageListenerContainer listenerContainer;

	public Mono<Long> publish(RoomEvent event) {
		String channel = LobbyConstants.eventChannel(event.roomCode());
		log.debug("Publishing {} to {}", event.getClass().getSimpleName(), channel);
		return eventTemplate.convertAndSend(channel, event);
	}

	public Flux<RoomEvent> subscribe(String roomCode) {
		ChannelTopic topic = ChannelTopic.of(LobbyConstants.eventChannel(roomCode));
		return listenerContainer.receive(
				List.of(topic),
				eventTemplate.getSerializationContext().getKeySerializationPair(),
				eventTemplate.getSerializationContext().getValueSerializationPair())
			.map(ReactiveSubscription.Message::getMessage);
	}
}

