package org.atrium.core.redis.config;

import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import org.atrium.core.autoconfigure.AtriumAutoConfiguration;
import org.atrium.core.autoconfigure.AtriumProperties;
import org.atrium.core.domain.constant.AtriumConstants;
import org.atrium.core.domain.event.HomeEvent;
import org.atrium.core.domain.event.RoomEvent;
import org.atrium.core.domain.model.GameSettings;
import org.atrium.core.domain.model.Player;
import org.atrium.core.domain.model.Room;
import org.atrium.core.websocket.HomeWebSocketHandler;
import org.atrium.core.websocket.RoomWebSocketHandler;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.redis.connection.ReactiveRedisConnectionFactory;
import org.springframework.data.redis.core.ReactiveRedisTemplate;
import org.springframework.data.redis.listener.ReactiveRedisMessageListenerContainer;
import org.springframework.data.redis.serializer.Jackson2JsonRedisSerializer;
import org.springframework.data.redis.serializer.RedisSerializationContext;
import org.springframework.data.redis.serializer.StringRedisSerializer;
import org.springframework.web.reactive.HandlerMapping;
import org.springframework.web.reactive.handler.SimpleUrlHandlerMapping;
import org.springframework.web.reactive.socket.WebSocketHandler;
import org.springframework.web.reactive.socket.server.support.WebSocketHandlerAdapter;

import java.util.Map;

/**
 * Wires up the reactive Redis templates, the pub/sub listener container, and the
 * WebSocket handler mapping for the lobby system.
 *
 * <p>Every bean is gated with {@link ConditionalOnMissingBean} so a downstream
 * application can override any single piece (e.g. providing a custom Jackson
 * {@code ObjectMapper} with extra {@link GameSettings} subtypes
 * registered) without losing the rest of the wiring.
 */
@Configuration(proxyBeanMethods = false)
@RequiredArgsConstructor
public final class RedisAtriumConfiguration {

	@Bean
	@ConditionalOnMissingBean(name = "atriumRoomRedisTemplate")
	public ReactiveRedisTemplate<String, Room> atriumRoomRedisTemplate(ReactiveRedisConnectionFactory connectionFactory, ObjectMapper objectMapper) {
		return buildTemplate(connectionFactory, objectMapper, Room.class);
	}

	@Bean
	@ConditionalOnMissingBean(name = "atriumPlayerRedisTemplate")
	public ReactiveRedisTemplate<String, Player> atriumPlayerRedisTemplate(ReactiveRedisConnectionFactory connectionFactory, ObjectMapper objectMapper) {
		return buildTemplate(connectionFactory, objectMapper, Player.class);
	}

	@Bean
	@ConditionalOnMissingBean(name = "atriumEventRedisTemplate")
	public ReactiveRedisTemplate<String, RoomEvent> atriumEventRedisTemplate(ReactiveRedisConnectionFactory connectionFactory, ObjectMapper objectMapper) {
		return buildTemplate(connectionFactory, objectMapper, RoomEvent.class);
	}

	/**
	 * Reactive Redis template for {@link HomeEvent} serialisation on the home-screen
	 * pub/sub channel ({@code atrium:events:home}).
	 */
	@Bean
	@ConditionalOnMissingBean(name = "atriumHomeEventRedisTemplate")
	public ReactiveRedisTemplate<String, HomeEvent> atriumHomeEventRedisTemplate(ReactiveRedisConnectionFactory connectionFactory, ObjectMapper objectMapper) {
		return buildTemplate(connectionFactory, objectMapper, HomeEvent.class);
	}

	@Bean
	@ConditionalOnMissingBean
	public ReactiveRedisMessageListenerContainer reactiveRedisMessageListenerContainer(ReactiveRedisConnectionFactory connectionFactory) {
		return new ReactiveRedisMessageListenerContainer(connectionFactory);
	}

	/**
	 * Mount WebSocket handlers at {@code {websocketPath}/home} and
	 * {@code {websocketPath}/{code}}.
	 *
	 * <p>Order is bumped above {@code WebFluxConfigurationSupport}'s default {@code 0}
	 * so the framework's annotation-based handler mapping doesn't grab the path first.
	 */
	@Bean
	@ConditionalOnMissingBean(name = "atriumWebSocketHandlerMapping")
	public HandlerMapping atriumWebSocketHandlerMapping(RoomWebSocketHandler roomWebSocketHandler, HomeWebSocketHandler homeWebSocketHandler, AtriumProperties properties) {
		final Map<String, WebSocketHandler> handlers = Map.of(
			properties.getWebsocketPath() + "/home", homeWebSocketHandler,
			properties.getWebsocketPath() + "/{code}", roomWebSocketHandler
		);
		final SimpleUrlHandlerMapping mapping = new SimpleUrlHandlerMapping(handlers);
		mapping.setOrder(-1);
		mapping.setCorsConfigurations(AtriumAutoConfiguration.corsConfigurations(properties));
		return mapping;
	}

	@Bean
	@ConditionalOnMissingBean
	public WebSocketHandlerAdapter webSocketHandlerAdapter() {
		return new WebSocketHandlerAdapter();
	}

	private static <T> ReactiveRedisTemplate<String, T> buildTemplate(ReactiveRedisConnectionFactory connectionFactory, ObjectMapper objectMapper, Class<T> valueType) {
		final Jackson2JsonRedisSerializer<T> valueSerializer = new Jackson2JsonRedisSerializer<>(objectMapper, valueType);
		return new ReactiveRedisTemplate<>(connectionFactory, RedisSerializationContext
			.<String, T>newSerializationContext(new StringRedisSerializer())
			.value(valueSerializer)
			.hashKey(new StringRedisSerializer())
			.hashValue(valueSerializer)
			.build());
	}

	/**
	 * Resolve the Redis pub/sub channel name for a given room code.
	 * Referenced from auto-configuration via constant reference.
	 *
	 * @param roomCode the 6-character room code
	 * @return the full channel name (e.g. {@code atrium:events:ABCDEF})
	 */
	@SuppressWarnings("unused") // referenced from LobbyAutoConfiguration via constant
	static String channelFor(String roomCode) {
		return AtriumConstants.eventChannel(roomCode);
	}
}
