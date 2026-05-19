package org.atrium.core.domain.service;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Mono;

import org.atrium.core.api.error.LobbyException;
import org.atrium.core.autoconfigure.LobbyProperties;
import org.atrium.core.domain.constant.LobbyConstants;
import org.atrium.core.redis.repository.RoomRepository;

import java.security.SecureRandom;

/**
 * Generates room codes from {@link LobbyConstants#ROOM_CODE_ALPHABET}, retrying on
 * collision up to {@link LobbyConstants#ROOM_CODE_GENERATION_MAX_ATTEMPTS} times.
 */
@Component
@RequiredArgsConstructor
public class RoomCodeGenerator {

	private final RoomRepository roomRepository;
	private final LobbyProperties properties;
	private final SecureRandom random = new SecureRandom();

	public Mono<String> next() {
		return attempt(0);
	}

	private Mono<String> attempt(int attemptNumber) {
		if (attemptNumber >= LobbyConstants.ROOM_CODE_GENERATION_MAX_ATTEMPTS) {
			return Mono.error(LobbyException.conflict("Could not allocate a unique room code"));
		}
		String candidate = randomCode();
		return roomRepository.existsByCode(candidate)
			.flatMap(exists -> Boolean.TRUE.equals(exists)
				? attempt(attemptNumber + 1)
				: Mono.just(candidate));
	}

	private String randomCode() {
		int length = properties.getRoomCodeLength();
		char[] buffer = new char[length];
		String alphabet = LobbyConstants.ROOM_CODE_ALPHABET;
		for (int i = 0; i < length; i++) {
			buffer[i] = alphabet.charAt(random.nextInt(alphabet.length()));
		}
		return new String(buffer);
	}
}

