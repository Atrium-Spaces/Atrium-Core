package org.atrium.core.redis.config;

import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import org.atrium.core.autoconfigure.LobbyAutoConfiguration;
import org.atrium.core.autoconfigure.LobbyProperties;
import org.atrium.core.domain.constant.LobbyConstants;
import org.atrium.core.domain.event.RoomEvent;
import org.atrium.core.domain.model.Player;
import org.atrium.core.domain.model.Room;
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
 * {@code ObjectMapper} with extra {@link org.atrium.core.domain.model.GameSettings} subtypes
 * registered) without losing the rest of the wiring.
 */
@Configuration(proxyBeanMethods = false)
@RequiredArgsConstructor
public class RedisLobbyConfiguration {

	@Bean
	@ConditionalOnMissingBean(name = "lobbyRoomRedisTemplate")
	public ReactiveRedisTemplate<String, Room> lobbyRoomRedisTemplate(
		ReactiveRedisConnectionFactory connectionFactory,
		ObjectMapper objectMapper) {
		return buildTemplate(connectionFactory, objectMapper, Room.class);
	}

	@Bean
	@ConditionalOnMissingBean(name = "lobbyPlayerRedisTemplate")
	public ReactiveRedisTemplate<String, Player> lobbyPlayerRedisTemplate(
		ReactiveRedisConnectionFactory connectionFactory,
		ObjectMapper objectMapper) {
		return buildTemplate(connectionFactory, objectMapper, Player.class);
	}

	@Bean
	@ConditionalOnMissingBean(name = "lobbyEventRedisTemplate")
	public ReactiveRedisTemplate<String, RoomEvent> lobbyEventRedisTemplate(
		ReactiveRedisConnectionFactory connectionFactory,
		ObjectMapper objectMapper) {
		return buildTemplate(connectionFactory, objectMapper, RoomEvent.class);
	}

	@Bean
	@ConditionalOnMissingBean
	public ReactiveRedisMessageListenerContainer reactiveRedisMessageListenerContainer(
		ReactiveRedisConnectionFactory connectionFactory) {
		return new ReactiveRedisMessageListenerContainer(connectionFactory);
	}

	/**
	 * Mount the WebSocket handler at {@code {websocketPath}/{code}}.
	 *
	 * <p>Order is bumped above {@code WebFluxConfigurationSupport}'s default {@code 0}
	 * so the framework's annotation-based handler mapping doesn't grab the path first.
	 */
	@Bean
	@ConditionalOnMissingBean(name = "lobbyWebSocketHandlerMapping")
	public HandlerMapping lobbyWebSocketHandlerMapping(
		RoomWebSocketHandler roomWebSocketHandler,
		LobbyProperties properties) {
		Map<String, WebSocketHandler> handlers = Map.of(
			properties.getWebsocketPath() + "/{code}", roomWebSocketHandler);
		SimpleUrlHandlerMapping mapping = new SimpleUrlHandlerMapping(handlers);
		mapping.setOrder(-1);
		mapping.setCorsConfigurations(LobbyAutoConfiguration.corsConfigurations(properties));
		return mapping;
	}

	@Bean
	@ConditionalOnMissingBean
	public WebSocketHandlerAdapter webSocketHandlerAdapter() {
		return new WebSocketHandlerAdapter();
	}

	// ---- helpers -----------------------------------------------------------------------------

	private static <T> ReactiveRedisTemplate<String, T> buildTemplate(
		ReactiveRedisConnectionFactory connectionFactory,
		ObjectMapper objectMapper,
		Class<T> valueType) {
		Jackson2JsonRedisSerializer<T> valueSerializer = new Jackson2JsonRedisSerializer<>(objectMapper, valueType);
		RedisSerializationContext<String, T> context = RedisSerializationContext
			.<String, T>newSerializationContext(new StringRedisSerializer())
			.value(valueSerializer)
			.hashKey(new StringRedisSerializer())
			.hashValue(valueSerializer)
			.build();
		return new ReactiveRedisTemplate<>(connectionFactory, context);
	}

	@SuppressWarnings("unused") // referenced from LobbyAutoConfiguration via constant
	static String channelFor(String roomCode) {
		return LobbyConstants.eventChannel(roomCode);
	}
}

