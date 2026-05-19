package org.atrium.core.redis.repository;

import lombok.RequiredArgsConstructor;
import org.atrium.core.domain.constant.AtriumConstants;
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
 * sorted sets — {@link AtriumConstants#ALL_ROOMS_INDEX} (every room) and
 * {@link AtriumConstants#PUBLIC_ROOMS_INDEX} (only public rooms) — both scored by
 * {@link Room#lastActivityAt()} epoch millis. Listing the most-active public rooms
 * and finding stale rooms for cleanup are both O(log n) range queries.
 */
@Repository
@RequiredArgsConstructor
public class RoomRepository {

	private final ReactiveRedisTemplate<String, Room> roomTemplate;
	private final ReactiveStringRedisTemplate stringTemplate;

	public Mono<Room> findByCode(String code) {
		return roomTemplate.opsForValue().get(AtriumConstants.roomKey(code));
	}

	public Mono<Boolean> existsByCode(String code) {
		return roomTemplate.hasKey(AtriumConstants.roomKey(code));
	}

	public Mono<Room> save(Room room) {
		final String key = AtriumConstants.roomKey(room.code());
		final double score = (double) room.lastActivityAt().toEpochMilli();
		final Mono<Boolean> publicIndex = room.isPrivate() ? stringTemplate.opsForZSet().remove(AtriumConstants.PUBLIC_ROOMS_INDEX, room.code()).map(removed -> true) : stringTemplate.opsForZSet().add(AtriumConstants.PUBLIC_ROOMS_INDEX, room.code(), score);
		return roomTemplate.opsForValue().set(key, room)
			.then(stringTemplate.opsForZSet().add(AtriumConstants.ALL_ROOMS_INDEX, room.code(), score))
			.then(publicIndex)
			.thenReturn(room);
	}

	public Mono<Void> delete(String code) {
		return stringTemplate.opsForZSet().remove(AtriumConstants.PUBLIC_ROOMS_INDEX, code)
			.then(stringTemplate.opsForZSet().remove(AtriumConstants.ALL_ROOMS_INDEX, code))
			.then(roomTemplate.opsForValue().delete(AtriumConstants.roomKey(code)))
			.then();
	}

	public Flux<Room> listPublic(int limit) {
		return stringTemplate.opsForZSet().reverseRange(AtriumConstants.PUBLIC_ROOMS_INDEX, Range.closed(0L, (long) limit - 1)).flatMap(this::findByCode);
	}

	public Flux<String> findStaleCodes(Instant cutoff) {
		return stringTemplate.opsForZSet().rangeByScore(AtriumConstants.ALL_ROOMS_INDEX, Range.closed(0d, (double) cutoff.toEpochMilli()), Limit.unlimited());
	}

	public Flux<Room> findAll() {
		return stringTemplate.opsForZSet().range(AtriumConstants.ALL_ROOMS_INDEX, Range.unbounded()).flatMap(this::findByCode);
	}
}
