package org.atrium.core.domain.service;

import lombok.RequiredArgsConstructor;
import org.atrium.core.api.dto.PlayerView;
import org.atrium.core.api.dto.RoomView;
import org.atrium.core.domain.model.Player;
import org.atrium.core.domain.model.PlayerStatus;
import org.atrium.core.domain.model.Room;
import org.atrium.core.redis.repository.PlayerRepository;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.time.Instant;
import java.util.UUID;

/**
 * Expands {@link Room} entities into {@link RoomView} DTOs by looking up each member
 * player's name / avatar / status. Resolves players in parallel.
 */
@Component
@RequiredArgsConstructor
public class RoomViewAssembler {

	private final PlayerRepository playerRepository;

	public Mono<RoomView> assemble(Room room) {
		Flux<PlayerView> playerViews = Flux.fromIterable(room.players())
			.concatMap(publicId -> playerRepository.findById(publicId)
				.map(player -> toPlayerView(player, room.createdAt()))
				.defaultIfEmpty(missingPlayerView(publicId, room.createdAt())));
		return playerViews.collectList().map(list -> new RoomView(
			room.code(),
			room.host(),
			list,
			room.maxPlayers(),
			room.gameSettings(),
			room.isPrivate(),
			room.state(),
			room.createdAt(),
			room.lastActivityAt()
		));
	}

	public PlayerView toPlayerView(Player player, Instant joinedAtFallback) {
		return new PlayerView(player.publicId(), player.name(), player.avatar(), player.status(), joinedAtFallback);
	}

	private PlayerView missingPlayerView(UUID publicId, Instant joinedAtFallback) {
		return new PlayerView(publicId, "?", "", PlayerStatus.DISCONNECTED, joinedAtFallback);
	}
}
