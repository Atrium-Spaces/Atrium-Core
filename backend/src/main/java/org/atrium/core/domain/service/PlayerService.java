package org.atrium.core.domain.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

import org.atrium.core.api.error.AtriumException;
import org.atrium.core.autoconfigure.AtriumProperties;
import org.atrium.core.domain.model.Player;
import org.atrium.core.domain.model.PlayerStatus;
import org.atrium.core.domain.model.Room;
import org.atrium.core.redis.repository.PlayerRepository;
import org.atrium.core.redis.repository.RoomRepository;

import org.jspecify.annotations.Nullable;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

/**
 * Player-identity and profile operations.
 *
 * <p>Identity is a ({@link Player#publicId()}, {@link Player#secretId()}) pair stored
 * client-side as cookies. The two-id design lets other players know who you are
 * (public id appears in room rosters and events) without exposing the credential
 * needed to act as you.
 *
 * <p>{@link #resolvePlayerRooms(UUID)} implements the "active index" fail-safe described in
 * the architecture doc: if the cached {@code roomCodes} on the player point to
 * rooms that no longer exist or no longer contain them, an emergency scan of all
 * active rooms repairs the index.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class PlayerService {

	private final PlayerRepository playerRepository;
	private final RoomRepository roomRepository;
	private final AtriumProperties properties;

	/**
	 * Authenticate or freshly mint a player identity from a status request.
	 *
	 * <p>If both {@code publicId} and {@code secretId} are supplied and match a stored
	 * player, that player is returned. Otherwise a brand-new identity is allocated with
	 * an auto-generated name and no avatar; the result's {@link Player#publicId()} /
	 * {@link Player#secretId()} are what the caller must persist as cookies.
	 */
	public Mono<IdentityResult> ensureIdentity(@Nullable UUID publicId, @Nullable UUID secretId) {
		if (publicId == null || secretId == null) {
			log.debug("No identity provided; minting fresh player identity");
			return Mono.just(mintFresh());
		} else {
			log.debug("Ensuring identity for player {}", publicId);
			return playerRepository.findById(publicId)
				.flatMap(storedPlayer -> {
					if (storedPlayer.secretId().equals(secretId)) {
						return Mono.just(new IdentityResult(storedPlayer, false));
					} else {
						log.debug("Secret id mismatch for public id {} — allocating fresh identity", publicId);
						return Mono.just(mintFresh());
					}
				})
				.switchIfEmpty(Mono.fromSupplier(() -> {
					log.debug("No player found for public id {}; minting fresh identity", publicId);
					return mintFresh();
				}))
				.flatMap(result -> result.freshIdentity() ? playerRepository.save(result.player()).map(saved -> new IdentityResult(saved, true)) : Mono.just(result));
		}
	}

	private IdentityResult mintFresh() {
		final UUID publicId = UUID.randomUUID();
		final UUID secretId = UUID.randomUUID();
		final String name = cleanName("Player " + publicId.toString().replaceAll("\\W", "").substring(0, 6).toUpperCase());
		final String avatar = cleanAvatar(null);
		return new IdentityResult(new Player(publicId, secretId, name, avatar, List.of(), PlayerStatus.ACTIVE, Instant.now()), true);
	}

	/**
	 * Authenticate a player by their public/secret id pair. Every write endpoint calls
	 * this before mutating state.
	 *
	 * @param publicId the player's public id
	 * @param secretId the player's secret id
	 * @return the authenticated player
	 * @throws AtriumException if the pair doesn't match or the player doesn't exist
	 */
	public Mono<Player> authenticate(UUID publicId, UUID secretId) {
		log.debug("Authenticating player {}", publicId);
		return playerRepository.findById(publicId)
			.switchIfEmpty(Mono.error(AtriumException.playerNotFound()))
			.flatMap(player -> player.secretId().equals(secretId) ? Mono.just(player) : Mono.error(AtriumException.badCredentials()))
			.doOnSuccess(player -> log.debug("Authentication succeeded for player {}", player.publicId()));
	}

	/**
	 * Update a player's display name and avatar. The profile change is broadcast to
	 * the player's current room peers via {@link RoomService#broadcastProfileUpdate}
	 * and a {@link org.atrium.core.domain.event.HomeEvent.RoomUpdated} is published
	 * if the player's room is public.
	 *
	 * @param publicId the player's public id
	 * @param secretId the player's secret id
	 * @param name     the new display name
	 * @param avatar   the new avatar string (may be blank)
	 * @return the updated player
	 */
	public Mono<Player> updateProfile(UUID publicId, UUID secretId, String name, String avatar) {
		log.debug("Updating profile for player {}", publicId);
		return authenticate(publicId, secretId)
			.map(player -> player.withProfile(cleanName(name), cleanAvatar(avatar)))
			.flatMap(playerRepository::save)
			.doOnSuccess(player -> log.debug("Profile updated for player {}", player.publicId()));
	}

	/**
	 * Return all rooms the given player is currently in, repairing the cached index if
	 * it points to stale or wrong rooms.
	 *
	 * @return a flux of rooms the player is a member of; completes without emitting if none
	 */
	public Flux<Room> resolvePlayerRooms(UUID publicId) {
		log.debug("Resolving rooms for player {}", publicId);
		return playerRepository.findById(publicId)
			.flatMapMany(player -> {
				final List<String> cachedCodes = player.roomCodes();

				if (cachedCodes.isEmpty()) {
					return Flux.empty();
				}

				return Flux.fromIterable(cachedCodes)
					.flatMap(code -> roomRepository.findByCode(code).filter(room -> room.contains(publicId)))
					.collectList()
					.flatMapMany(validRooms -> {
						if (validRooms.size() == cachedCodes.size()) {
							return Flux.fromIterable(validRooms);
						} else {
							return repairPlayerRooms(player);
						}
					});
			});
	}

	private Flux<Room> repairPlayerRooms(Player player) {
		log.warn("Repairing room indices for player {}", player.publicId());
		return roomRepository.findAll()
			.filter(room -> room.contains(player.publicId()))
			.collectList()
			.flatMapMany(foundRooms -> {
				final List<String> foundCodes = foundRooms.stream().map(Room::code).toList();
				return playerRepository.save(player.withRoomCodes(foundCodes)).thenMany(Flux.fromIterable(foundRooms));
			});
	}

	private String cleanName(@Nullable String name) {
		final String trimmed = name == null ? "" : name.strip();

		if (trimmed.isEmpty()) {
			throw AtriumException.badRequest("Name must not be empty");
		}

		final int maxLength = properties.getMaxNameLength();
		return trimmed.length() > maxLength ? trimmed.substring(0, maxLength) : trimmed;
	}

	private String cleanAvatar(@Nullable String avatar) {
		final String trimmedAvatar = avatar == null ? "" : avatar.strip();
		final int maxLength = properties.getMaxAvatarLength();
		return trimmedAvatar.length() > maxLength ? trimmedAvatar.substring(0, maxLength) : trimmedAvatar;
	}

	/**
	 * Result envelope for {@link #ensureIdentity}.
	 */
	public record IdentityResult(Player player, boolean freshIdentity) {
	}
}
