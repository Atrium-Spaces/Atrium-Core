package org.atrium.core.redis.repository;

import lombok.RequiredArgsConstructor;
import org.atrium.core.domain.constant.LobbyConstants;
import org.atrium.core.domain.model.Player;
import org.springframework.data.domain.Range;
import org.springframework.data.redis.connection.Limit;
import org.springframework.data.redis.core.ReactiveRedisTemplate;
import org.springframework.data.redis.core.ReactiveStringRedisTemplate;
import org.springframework.stereotype.Repository;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.time.Instant;
import java.util.UUID;

/**
 * Reactive Redis repository for {@link Player}.
 *
 * <p>Players are stored under {@code lobby:player:{publicId}} with a JSON-serialised
 * value. Each player also appears in {@link LobbyConstants#ALL_PLAYERS_INDEX} — a
 * sorted set scored by {@link Player#lastActiveAt()} epoch millis — so the cleanup
 * job can range-query stale entries in O(log n).
 *
 * <p>The string-valued sorted-set index requires a separate
 * {@link ReactiveStringRedisTemplate} because {@code opsForZSet()} on the
 * typed {@link ReactiveRedisTemplate} would bind the member type to {@link Player}.
 */
@Repository
@RequiredArgsConstructor
public class PlayerRepository {

	private final ReactiveRedisTemplate<String, Player> playerTemplate;
	private final ReactiveStringRedisTemplate stringTemplate;

	public Mono<Player> findById(UUID publicId) {
		return playerTemplate.opsForValue().get(LobbyConstants.playerKey(publicId));
	}

	public Mono<Player> save(Player player) {
		String key = LobbyConstants.playerKey(player.publicId());
		double score = (double) player.lastActiveAt().toEpochMilli();
		return playerTemplate.opsForValue().set(key, player)
			.then(stringTemplate.opsForZSet().add(
				LobbyConstants.ALL_PLAYERS_INDEX, player.publicId().toString(), score))
			.thenReturn(player);
	}

	public Mono<Void> delete(UUID publicId) {
		return stringTemplate.opsForZSet()
			.remove(LobbyConstants.ALL_PLAYERS_INDEX, publicId.toString())
			.then(playerTemplate.opsForValue().delete(LobbyConstants.playerKey(publicId)))
			.then();
	}

	public Flux<UUID> findStaleIds(Instant cutoff) {
		return stringTemplate.opsForZSet()
			.rangeByScore(
				LobbyConstants.ALL_PLAYERS_INDEX,
				Range.closed(0d, (double) cutoff.toEpochMilli()),
				Limit.unlimited())
			.map(UUID::fromString);
	}
}
