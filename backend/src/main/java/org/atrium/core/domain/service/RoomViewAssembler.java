package org.atrium.core.domain.service;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import org.atrium.core.api.dto.PlayerView;
import org.atrium.core.api.dto.RoomView;
import org.atrium.core.domain.model.Player;
import org.atrium.core.domain.model.PlayerStatus;
import org.atrium.core.domain.model.Room;
import org.atrium.core.redis.repository.PlayerRepository;

import java.util.List;
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
		List<UUID> publicIds = room.players();
		Flux<PlayerView> playerViews = Flux.fromIterable(publicIds)
			.concatMap(publicId -> playerRepository.findById(publicId)
				.map(player -> toView(player, room.createdAt()))
				.defaultIfEmpty(missingPlayerView(publicId, room.createdAt())));
		return playerViews.collectList()
			.map(list -> new RoomView(
				room.code(),
				room.host(),
				list,
				room.maxPlayers(),
				room.gameSettings(),
				room.isPrivate(),
				room.state(),
				room.createdAt(),
				room.lastActivityAt()));
	}

	public PlayerView toView(Player player, java.time.Instant joinedAtFallback) {
		return new PlayerView(
			player.publicId(),
			player.name(),
			player.avatar(),
			player.status(),
			joinedAtFallback);
	}

	private PlayerView missingPlayerView(UUID publicId, java.time.Instant joinedAtFallback) {
		return new PlayerView(publicId, "?", "", PlayerStatus.DISCONNECTED, joinedAtFallback);
	}
}
