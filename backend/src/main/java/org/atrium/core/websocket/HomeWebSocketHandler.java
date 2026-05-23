package org.atrium.core.websocket;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.atrium.core.domain.event.HomeEvent;
import org.atrium.core.domain.service.RoomService;
import org.atrium.core.redis.stream.HomeBroadcastService;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.socket.WebSocketHandler;
import org.springframework.web.reactive.socket.WebSocketMessage;
import org.springframework.web.reactive.socket.WebSocketSession;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.HashMap;
import java.util.Map;

/**
 * Public home-screen WebSocket stream.
 *
 * <p>Mounted at {@code /api/atrium/ws/home}. On connect, this handler sends a
 * {@link HomeEvent.Snapshot} frame and then forwards all subsequent
 * {@link HomeEvent}s from Redis.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public final class HomeWebSocketHandler implements WebSocketHandler {

	private static final int DEFAULT_LIMIT = 50;
	private static final int MIN_LIMIT = 1;
	private static final int MAX_LIMIT = 200;

	private final RoomService roomService;
	private final HomeBroadcastService homeBroadcastService;
	private final ObjectMapper objectMapper;

	/**
	 * Handle the WebSocket session lifecycle.
	 *
	 * <ol>
	 *   <li>Resolve the {@code limit} query parameter (clamped to [1, 200], default 50).</li>
	 *   <li>Send a {@link HomeEvent.Snapshot} with up to {@code limit} public rooms.</li>
	 *   <li>Forward all subsequent {@link HomeEvent}s from Redis pub/sub.</li>
	 * </ol>
	 */
	@Override
	public Mono<Void> handle(WebSocketSession session) {
		final int limit = resolveLimit(session);
		log.debug("Home WebSocket connected (limit={})", limit);

		final Mono<WebSocketMessage> snapshot = roomService.listPublic(limit)
			.collectList()
			.map(rooms -> new HomeEvent.Snapshot(Instant.now(), rooms))
			.map(event -> serialize(session, event));

		final Flux<WebSocketMessage> live = homeBroadcastService.subscribe()
			.map(event -> serialize(session, event));

		final Flux<WebSocketMessage> outbound = Flux.concat(snapshot.flux(), live);
		final Mono<Void> inbound = session.receive().then();

		return Mono.when(session.send(outbound), inbound)
			.doFinally(signalType -> log.debug("Home WebSocket disconnected ({})", signalType));
	}

	/**
	 * @return the {@code limit} query parameter clamped to [{@value #MIN_LIMIT}, {@value #MAX_LIMIT}],
	 * or {@value #DEFAULT_LIMIT} if absent or unparseable
	 */
	private int resolveLimit(WebSocketSession session) {
		final Map<String, String> params = queryParams(session);
		final String raw = params.get("limit");
		if (raw == null || raw.isBlank()) {
			return DEFAULT_LIMIT;
		}

		try {
			return Math.clamp(Integer.parseInt(raw), MIN_LIMIT, MAX_LIMIT);
		} catch (NumberFormatException ignored) {
			return DEFAULT_LIMIT;
		}
	}

	private WebSocketMessage serialize(WebSocketSession session, HomeEvent event) {
		try {
			return session.textMessage(objectMapper.writeValueAsString(event));
		} catch (JsonProcessingException e) {
			throw new IllegalStateException("Failed to serialise HomeEvent " + event, e);
		}
	}

	/**
	 * Parse query parameters from the WebSocket handshake URI.
	 *
	 * @return a mutable map of decoded query params (never {@code null})
	 */
	private static Map<String, String> queryParams(WebSocketSession session) {
		final String query = session.getHandshakeInfo().getUri().getQuery();
		if (query == null || query.isEmpty()) {
			return Map.of();
		}

		final Map<String, String> result = new HashMap<>();
		for (String pair : query.split("&")) {
			int separatorIndex = pair.indexOf('=');
			if (separatorIndex > 0) {
				result.put(URLDecoder.decode(pair.substring(0, separatorIndex), StandardCharsets.UTF_8), URLDecoder.decode(pair.substring(separatorIndex + 1), StandardCharsets.UTF_8));
			}
		}
		return result;
	}
}
