package org.atrium.core.redis.stream;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

import org.atrium.core.domain.constant.AtriumConstants;
import org.atrium.core.domain.event.HomeEvent;

import org.springframework.data.redis.connection.ReactiveSubscription;
import org.springframework.data.redis.core.ReactiveRedisTemplate;
import org.springframework.data.redis.listener.ChannelTopic;
import org.springframework.data.redis.listener.ReactiveRedisMessageListenerContainer;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.util.List;

/**
 * Bridges {@link HomeEvent}s onto a single Redis pub/sub channel for the home page.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public final class HomeBroadcastService {

	private final ReactiveRedisTemplate<String, HomeEvent> homeEventTemplate;
	private final ReactiveRedisMessageListenerContainer listenerContainer;

	/**
	 * Publish a {@link HomeEvent} onto the home-screen pub/sub channel.
	 *
	 * @param event the home event to publish
	 * @return the number of subscribers that received the message
	 */
	public Mono<Long> publish(HomeEvent event) {
		return Mono.defer(() -> {
			log.debug("Publishing {} to {}", event.getClass().getSimpleName(), AtriumConstants.HOME_EVENT_CHANNEL);
			return homeEventTemplate.convertAndSend(AtriumConstants.HOME_EVENT_CHANNEL, event);
		});
	}

	/**
	 * Subscribe to all home-screen events. Returns a cold {@link Flux} that receives
	 * events from Redis pub/sub until the subscription is cancelled.
	 *
	 * @return a flux of home events
	 */
	public Flux<HomeEvent> subscribe() {
		final ChannelTopic topic = ChannelTopic.of(AtriumConstants.HOME_EVENT_CHANNEL);
		return listenerContainer.receive(List.of(topic), homeEventTemplate.getSerializationContext().getKeySerializationPair(), homeEventTemplate.getSerializationContext().getValueSerializationPair())
			.map(ReactiveSubscription.Message::getMessage);
	}
}
