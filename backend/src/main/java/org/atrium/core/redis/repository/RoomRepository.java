package org.atrium.core.redis.repository;

import lombok.RequiredArgsConstructor;
import org.atrium.core.domain.constant.LobbyConstants;
import org.atrium.core.domain.model.Room;
import org.springframework.data.domain.Range;
import org.springframework.data.redis.connection.Limit;
import org.springframework.data.redis.core.ReactiveRedisTemplate;
import org.springframework.data.redis.core.ReactiveStringRedisTemplate;
import org.springframework.stereotype.Repository;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.time.Instant;

/**
 * Reactive Redis repository for {@link Room}.
 *
 * <p>Rooms are stored under {@code lobby:room:{code}} and indexed in two string-valued
 * sorted sets — {@link LobbyConstants#ALL_ROOMS_INDEX} (every room) and
 * {@link LobbyConstants#PUBLIC_ROOMS_INDEX} (only public rooms) — both scored by
 * {@link Room#lastActivityAt()} epoch millis. Listing the most-active public rooms
 * and finding stale rooms for cleanup are both O(log n) range queries.
 */
@Repository
@RequiredArgsConstructor
public class RoomRepository {

	private final ReactiveRedisTemplate<String, Room> roomTemplate;
	private final ReactiveStringRedisTemplate stringTemplate;

	public Mono<Room> findByCode(String code) {
		return roomTemplate.opsForValue().get(LobbyConstants.roomKey(code));
	}

	public Mono<Boolean> existsByCode(String code) {
		return roomTemplate.hasKey(LobbyConstants.roomKey(code));
	}

	public Mono<Room> save(Room room) {
		String key = LobbyConstants.roomKey(room.code());
		double score = (double) room.lastActivityAt().toEpochMilli();
		Mono<Boolean> publicIndex = room.isPrivate()
			? stringTemplate.opsForZSet().remove(LobbyConstants.PUBLIC_ROOMS_INDEX, room.code()).map(removed -> true)
			: stringTemplate.opsForZSet().add(LobbyConstants.PUBLIC_ROOMS_INDEX, room.code(), score);
		return roomTemplate.opsForValue().set(key, room)
			.then(stringTemplate.opsForZSet().add(LobbyConstants.ALL_ROOMS_INDEX, room.code(), score))
			.then(publicIndex)
			.thenReturn(room);
	}

	public Mono<Void> delete(String code) {
		return stringTemplate.opsForZSet().remove(LobbyConstants.PUBLIC_ROOMS_INDEX, code)
			.then(stringTemplate.opsForZSet().remove(LobbyConstants.ALL_ROOMS_INDEX, code))
			.then(roomTemplate.opsForValue().delete(LobbyConstants.roomKey(code)))
			.then();
	}

	public Flux<Room> listPublic(int limit) {
		return stringTemplate.opsForZSet()
			.reverseRange(LobbyConstants.PUBLIC_ROOMS_INDEX, Range.closed(0L, (long) limit - 1))
			.flatMap(this::findByCode);
	}

	public Flux<String> findStaleCodes(Instant cutoff) {
		return stringTemplate.opsForZSet()
			.rangeByScore(
				LobbyConstants.ALL_ROOMS_INDEX,
				Range.closed(0d, (double) cutoff.toEpochMilli()),
				Limit.unlimited());
	}

	public Flux<Room> findAll() {
		return stringTemplate.opsForZSet()
			.range(LobbyConstants.ALL_ROOMS_INDEX, Range.unbounded())
			.flatMap(this::findByCode);
	}
}
