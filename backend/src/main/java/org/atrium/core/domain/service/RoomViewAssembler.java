package org.atrium.core.domain.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

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
 * player's name / avatar / status. Resolves players sequentially to avoid overwhelming
 * Redis with concurrent lookups.
 *
 * <p>Note: the {@link PlayerView#joinedAt()} field is populated with the room's
 * {@link Room#createdAt()} as a fallback because the domain model does not currently
 * track per-player join timestamps. This means all players in a room share the same
 * visible join time (the room's creation time). Downstream consumers should treat
 * {@code joinedAt} as an approximate indicator only.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public final class RoomViewAssembler {

	private final PlayerRepository playerRepository;
	private final RoomPlayerLimitsResolver roomPlayerLimitsResolver;

	/**
	 * Assemble a {@link RoomView} from a {@link Room}, resolving each member's profile
	 * from the player repository.
	 *
	 * @param room the room entity
	 * @return the expanded room view
	 */
	public Mono<RoomView> assemble(Room room) {
		final RoomPlayerLimitsResolver.AbsolutePlayerLimits absolutePlayerLimits = roomPlayerLimitsResolver.absoluteLimits(room.gameSettings());
		return Flux.fromIterable(room.players())
			.concatMap(publicId -> playerRepository.findById(publicId).map(player -> toPlayerView(player, room.createdAt())).defaultIfEmpty(missingPlayerView(publicId, room.createdAt())))
			.collectList().map(playerViews -> new RoomView(
				room.code(),
				room.name(),
				room.host(),
				playerViews,
				room.minPlayers(),
				room.maxPlayers(),
				absolutePlayerLimits.absoluteMinPlayers(),
				absolutePlayerLimits.absoluteMaxPlayers(),
				room.gameSettings(),
				room.isPrivate(),
				room.state(),
				room.createdAt(),
				room.lastActivityAt()
			));
	}

	/**
	 * Convert a {@link Player} to a public {@link PlayerView}, using the given fallback
	 * for the join timestamp.
	 */
	public PlayerView toPlayerView(Player player, Instant joinedAtFallback) {
		return new PlayerView(player.publicId(), player.name(), player.avatar(), player.status(), joinedAtFallback);
	}

	/**
	 * Convert a {@link Player} to a public {@link PlayerView} outside the context of a room.
	 * The player's {@link Player#lastActiveAt()} is reused as the best available timestamp.
	 */
	public PlayerView toPlayerView(Player player) {
		return toPlayerView(player, player.lastActiveAt());
	}

	/**
	 * Construct a placeholder view for a player who exists in a room's member list
	 * but whose record has been deleted from Redis.
	 */
	private PlayerView missingPlayerView(UUID publicId, Instant joinedAtFallback) {
		return new PlayerView(publicId, "?", "", PlayerStatus.DISCONNECTED, joinedAtFallback);
	}
}
