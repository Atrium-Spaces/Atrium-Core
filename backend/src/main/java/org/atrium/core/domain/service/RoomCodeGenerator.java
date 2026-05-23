package org.atrium.core.domain.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.atrium.core.api.error.AtriumException;
import org.atrium.core.autoconfigure.AtriumProperties;
import org.atrium.core.domain.constant.AtriumConstants;
import org.atrium.core.redis.repository.RoomRepository;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Mono;

import java.security.SecureRandom;

/**
 * Generates room codes from {@link AtriumConstants#ROOM_CODE_ALPHABET}, retrying on
 * collision up to {@link AtriumConstants#ROOM_CODE_GENERATION_MAX_ATTEMPTS} times.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public final class RoomCodeGenerator {

	private final RoomRepository roomRepository;
	private final AtriumProperties properties;
	private final SecureRandom random = new SecureRandom();

	/**
	 * Generate a unique room code. Retries on collision up to
	 * {@link AtriumConstants#ROOM_CODE_GENERATION_MAX_ATTEMPTS} times.
	 *
	 * @return a unique room code
	 * @throws AtriumException if a collision-free code could not be generated within the retry limit
	 */
	public Mono<String> next() {
		return attempt(0);
	}

	private Mono<String> attempt(int attemptNumber) {
		if (attemptNumber >= AtriumConstants.ROOM_CODE_GENERATION_MAX_ATTEMPTS) {
			return Mono.error(AtriumException.conflict("Could not allocate a unique room code"));
		}

		final String candidate = randomCode();
		return roomRepository.existsByCode(candidate).flatMap(exists -> exists ? attempt(attemptNumber + 1) : Mono.just(candidate));
	}

	private String randomCode() {
		final int length = properties.getRoomCodeLength();
		final char[] buffer = new char[length];
		final String alphabet = AtriumConstants.ROOM_CODE_ALPHABET;

		for (int i = 0; i < length; i++) {
			buffer[i] = alphabet.charAt(random.nextInt(alphabet.length()));
		}

		return new String(buffer);
	}
}
