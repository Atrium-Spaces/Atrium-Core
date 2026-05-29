package org.atrium.core.redis.repository;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import org.atrium.core.domain.constant.AtriumConstants;
import org.atrium.core.domain.model.Room;
import org.springframework.data.domain.Range;
import org.springframework.data.redis.connection.Limit;
import org.springframework.data.redis.core.ReactiveRedisTemplate;
import org.springframework.data.redis.core.ReactiveStringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.stereotype.Repository;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.time.Instant;
import java.util.List;

/**
 * Reactive Redis repository for {@link Room}.
 *
 * <p>Rooms are stored under {@code atrium:room:{code}} and indexed in two string-valued
 * sorted sets — {@link AtriumConstants#ALL_ROOMS_INDEX} (every room) and
 * {@link AtriumConstants#PUBLIC_ROOMS_INDEX} (only public rooms) — both scored by
 * {@link Room#lastActivityAt()} epoch millis. Listing the most-active public rooms
 * and finding stale rooms for cleanup are both O(log n) range queries.
 *
 * <p>Mutating writes use a Redis Lua CAS script over a dedicated
 * {@code atrium:room:version:{code}} key to prevent lost updates across concurrent
 * room mutations.
 */
@Repository
@RequiredArgsConstructor
public class RoomRepository {
	private static final String SAVE_ROOM_SCRIPT = """
		local roomKey = KEYS[1]
		local roomVersionKey = KEYS[2]
		local allRoomsIndexKey = KEYS[3]
		local publicRoomsIndexKey = KEYS[4]

		local expectedVersion = tonumber(ARGV[1])
		local roomPayload = ARGV[2]
		local roomScore = ARGV[3]
		local isPrivateFlag = ARGV[4]
		local roomCode = ARGV[5]

		local currentVersionRaw = redis.call('GET', roomVersionKey)

		if expectedVersion == -1 then
			if currentVersionRaw then
				return -1
			end
			redis.call('SET', roomKey, roomPayload)
			redis.call('SET', roomVersionKey, 0)
			redis.call('ZADD', allRoomsIndexKey, roomScore, roomCode)
			if isPrivateFlag == '1' then
				redis.call('ZREM', publicRoomsIndexKey, roomCode)
			else
				redis.call('ZADD', publicRoomsIndexKey, roomScore, roomCode)
			end
			return 0
		end

		if not currentVersionRaw then
			return -2
		end

		local currentVersion = tonumber(currentVersionRaw)
		if currentVersion ~= expectedVersion then
			return -3
		end

		local newVersion = currentVersion + 1
		redis.call('SET', roomKey, roomPayload)
		redis.call('SET', roomVersionKey, newVersion)
		redis.call('ZADD', allRoomsIndexKey, roomScore, roomCode)
		if isPrivateFlag == '1' then
			redis.call('ZREM', publicRoomsIndexKey, roomCode)
		else
			redis.call('ZADD', publicRoomsIndexKey, roomScore, roomCode)
		end
		return newVersion
		""";

	private static final DefaultRedisScript<Long> SAVE_ROOM_REDIS_SCRIPT = new DefaultRedisScript<>(SAVE_ROOM_SCRIPT, Long.class);

	private final ReactiveRedisTemplate<String, Room> roomTemplate;
	private final ReactiveStringRedisTemplate stringTemplate;
	private final ObjectMapper objectMapper;

	/**
	 * A room with its CAS version number, used for optimistic concurrency control.
	 *
	 * @param room    the room entity
	 * @param version the current version counter from Redis
	 */
	public record VersionedRoom(Room room, long version) {
	}

	/**
	 * Find a room by its code.
	 *
	 * @param code the 6-character room code
	 * @return the room, or empty if not found
	 */
	public Mono<Room> findByCode(String code) {
		return roomTemplate.opsForValue().get(AtriumConstants.roomKey(code));
	}

	/**
	 * Find a room by its code together with its CAS version number, for use with
	 * {@link #saveIfVersion}.
	 *
	 * @param code the 6-character room code
	 * @return the versioned room, or empty if not found
	 */
	public Mono<VersionedRoom> findVersionedByCode(String code) {
		return roomTemplate.opsForValue().get(AtriumConstants.roomKey(code))
			.flatMap(room -> stringTemplate.opsForValue().get(AtriumConstants.roomVersionKey(code))
				.defaultIfEmpty("0")
				.map(versionRaw -> new VersionedRoom(room, parseVersion(versionRaw))));
	}

	/**
	 * Check whether a room with the given code exists.
	 *
	 * @param code the 6-character room code
	 * @return true if the room exists
	 */
	public Mono<Boolean> existsByCode(String code) {
		return roomTemplate.hasKey(AtriumConstants.roomKey(code));
	}

	/**
	 * Atomically create a new room (expected version = -1). Uses the Lua CAS script
	 * to prevent overwriting an existing room.
	 *
	 * @param room the room to create
	 * @return true if the room was created, false if a room with that code already exists
	 */
	public Mono<Boolean> saveNew(Room room) {
		return saveWithExpectedVersion(room, -1L).map(scriptResult -> scriptResult >= 0L);
	}

	/**
	 * Atomically update a room if its version matches the expected value. Uses the
	 * Lua CAS script to prevent lost concurrent updates.
	 *
	 * @param room            the room with updated fields
	 * @param expectedVersion the version that must match in Redis for the write to proceed
	 * @return true if the update was applied, false if the version didn't match
	 */
	public Mono<Boolean> saveIfVersion(Room room, long expectedVersion) {
		return saveWithExpectedVersion(room, expectedVersion).map(scriptResult -> scriptResult >= 0L);
	}

	/**
	 * Delete a room and all its indexes (public, all, version key).
	 *
	 * @param code the 6-character room code
	 * @return an empty mono on completion
	 */
	public Mono<Void> delete(String code) {
		return stringTemplate.opsForZSet().remove(AtriumConstants.PUBLIC_ROOMS_INDEX, code)
			.then(stringTemplate.opsForZSet().remove(AtriumConstants.ALL_ROOMS_INDEX, code))
			.then(stringTemplate.delete(AtriumConstants.roomVersionKey(code)))
			.then(roomTemplate.opsForValue().delete(AtriumConstants.roomKey(code)))
			.then();
	}

	/**
	 * List public rooms ordered by most-recently-active first. The result is capped
	 * at the given limit.
	 *
	 * @param limit maximum number of rooms to return
	 * @return a flux of rooms
	 */
	public Flux<Room> listPublic(int limit) {
		return stringTemplate.opsForZSet().reverseRange(AtriumConstants.PUBLIC_ROOMS_INDEX, Range.closed(0L, (long) limit - 1)).concatMap(this::findByCode);
	}

	/**
	 * Find room codes whose last-activity score is at or below the cutoff (inactive).
	 * Used by the cleanup sweep.
	 *
	 * @param cutoff the instant before which rooms are considered stale
	 * @return a flux of stale room codes
	 */
	public Flux<String> findStaleCodes(Instant cutoff) {
		return stringTemplate.opsForZSet().rangeByScore(AtriumConstants.ALL_ROOMS_INDEX, Range.closed(0d, (double) cutoff.toEpochMilli()), Limit.unlimited());
	}

	/**
	 * Return every room in the system. Used by the active-index repair scan.
	 *
	 * @return a flux of all rooms
	 */
	public Flux<Room> findAll() {
		return stringTemplate.opsForZSet().range(AtriumConstants.ALL_ROOMS_INDEX, Range.unbounded()).concatMap(this::findByCode);
	}

	private Mono<Long> saveWithExpectedVersion(Room room, long expectedVersion) {
		final String roomPayload;
		try {
			roomPayload = objectMapper.writeValueAsString(room);
		} catch (JsonProcessingException error) {
			return Mono.error(new IllegalStateException("Failed to serialise room " + room.code(), error));
		}

		final String roomScore = String.valueOf(room.lastActivityAt().toEpochMilli());
		final String isPrivateFlag = room.isPrivate() ? "1" : "0";
		return stringTemplate.execute(
				SAVE_ROOM_REDIS_SCRIPT,
				List.of(AtriumConstants.roomKey(room.code()), AtriumConstants.roomVersionKey(room.code()), AtriumConstants.ALL_ROOMS_INDEX, AtriumConstants.PUBLIC_ROOMS_INDEX),
				String.valueOf(expectedVersion),
				roomPayload,
				roomScore,
				isPrivateFlag,
				room.code()
			)
			.next()
			.switchIfEmpty(Mono.error(new IllegalStateException("Room save script returned no result for room " + room.code())));
	}

	private static long parseVersion(String versionRaw) {
		try {
			return Long.parseLong(versionRaw);
		} catch (NumberFormatException ignored) {
			return 0;
		}
	}
}
