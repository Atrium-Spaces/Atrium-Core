package org.atrium.core.websocket;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.atrium.core.autoconfigure.AtriumProperties;
import org.atrium.core.domain.event.RoomEvent;
import org.atrium.core.domain.model.PlayerStatus;
import org.atrium.core.domain.service.PlayerService;
import org.atrium.core.domain.service.RoomService;
import org.atrium.core.redis.config.RedisAtriumConfiguration;
import org.atrium.core.redis.stream.RoomBroadcastService;
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
import java.util.UUID;

/**
 * WebFlux WebSocket handler bridging Redis pub/sub events into a client session.
 *
 * <p>Mounted at {@code /api/atrium/ws/{code}} by
 * {@link RedisAtriumConfiguration#atriumWebSocketHandlerMapping(RoomWebSocketHandler, AtriumProperties)}.
 * The client must include {@code publicId} and {@code secretId} as query parameters
 * (e.g. {@code wss://host/api/atrium/ws/ABCDEF?publicId=...&secretId=...}). On open:
 *
 * <ol>
 *   <li>Authenticate the public / secret pair.</li>
 *   <li>Mark the player {@link PlayerStatus#ACTIVE} and emit a
 *       {@link RoomEvent.PlayerReconnected} if they had been disconnected.</li>
 *   <li>Send a {@link RoomEvent.Snapshot} as the first message so the client can
 *       hydrate its UI without a separate REST call.</li>
 *   <li>Subscribe to the room's Redis pub/sub channel and forward every event as a
 *       JSON text frame.</li>
 * </ol>
 *
 * <p>On close: members are marked {@link PlayerStatus#DISCONNECTED}.
 * Spectator connections (player not currently in {@code code}) do not toggle
 * connection status.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public final class RoomWebSocketHandler implements WebSocketHandler {

	private final PlayerService playerService;
	private final RoomService roomService;
	private final RoomBroadcastService broadcastService;
	private final ObjectMapper objectMapper;

	@Override
	public Mono<Void> handle(WebSocketSession session) {
		final String code = extractRoomCode(session);
		final Map<String, String> params = queryParams(session);
		log.debug("WebSocket connection attempt for room {}", code);
		final UUID publicId;
		final UUID secretId;

		try {
			publicId = UUID.fromString(params.getOrDefault("publicId", ""));
			secretId = UUID.fromString(params.getOrDefault("secretId", ""));
		} catch (IllegalArgumentException e) {
			log.debug("Rejecting WebSocket — missing/invalid publicId or secretId");
			return session.close();
		}

		return playerService.authenticate(publicId, secretId)
			.onErrorResume(error -> {
				log.debug("WebSocket auth failed for {} on room {}: {}", publicId, code, error.toString());
				return session.close().then(Mono.empty());
			})
			.flatMap(player -> {
				final boolean isRoomMember = code.equals(player.roomCode());
				log.debug("WebSocket authenticated for player {} on room {} (member={})", publicId, code, isRoomMember);
				if (!isRoomMember) {
					log.debug("Player {} not a member of room {} — spectator subscription", publicId, code);
				}

				if (isRoomMember) {
					return roomService.markReconnected(publicId).thenReturn(player);
				} else {
					return Mono.just(player);
				}
			})
			.flatMap(ignored -> {
				log.debug("WebSocket stream started for player {} on room {}", publicId, code);
				final Mono<WebSocketMessage> snapshot = roomService.view(code)
					.map(roomView -> new RoomEvent.Snapshot(code, Instant.now(), roomView))
					.map(event -> serialize(session, event));

				final Flux<WebSocketMessage> live = broadcastService.subscribe(code)
					.map(event -> serialize(session, event));

				final Flux<WebSocketMessage> outbound = Flux.concat(snapshot.flux(), live);

				final Mono<Void> inbound = session.receive().then();

				final Mono<Void> closure = session.closeStatus()
					.doOnNext(status -> log.debug("WebSocket closed for {} on {}: {}", publicId, code, status))
					.then(Mono.defer(() -> onDisconnect(publicId, code)));

				return Mono.when(session.send(outbound), inbound, closure);
			});
	}

	private Mono<Void> onDisconnect(UUID publicId, String code) {
		log.debug("Handling disconnect for player {} on room {}", publicId, code);
		return playerService.resolveRoom(publicId)
			.flatMap(room -> {
				if (code.equals(room.code())) {
					return roomService.markDisconnected(publicId).doOnSuccess(ignored -> log.debug("Player {} marked disconnected in room {}", publicId, code));
				} else {
					log.debug("Disconnect is for spectator session; no status change for player {} on room {}", publicId, code);
					return Mono.empty();
				}
			})
			.then();
	}

	private WebSocketMessage serialize(WebSocketSession session, RoomEvent event) {
		try {
			return session.textMessage(objectMapper.writeValueAsString(event));
		} catch (JsonProcessingException e) {
			throw new IllegalStateException("Failed to serialise RoomEvent " + event, e);
		}
	}

	private static String extractRoomCode(WebSocketSession session) {
		final String path = session.getHandshakeInfo().getUri().getPath();
		final int lastSlash = path.lastIndexOf('/');
		return lastSlash < 0 ? "" : path.substring(lastSlash + 1);
	}

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
