package org.atrium.core.domain.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.jspecify.annotations.Nullable;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Mono;

import org.atrium.core.api.error.LobbyException;
import org.atrium.core.autoconfigure.LobbyProperties;
import org.atrium.core.domain.model.Player;
import org.atrium.core.domain.model.PlayerStatus;
import org.atrium.core.domain.model.Room;
import org.atrium.core.redis.repository.PlayerRepository;
import org.atrium.core.redis.repository.RoomRepository;

import java.util.UUID;

/**
 * Player-identity and profile operations.
 *
 * <p>Identity is a ({@link Player#publicId()}, {@link Player#secretId()}) pair stored
 * client-side as cookies. The two-id design lets other players know who you are
 * (public id appears in room rosters and events) without exposing the credential
 * needed to act as you.
 *
 * <p>{@link #resolveRoom(UUID)} implements the "active index" fail-safe described in
 * the architecture doc: if the cached {@code roomCode} on the player points to a
 * room that no longer exists or no longer contains them, an emergency scan of all
 * active rooms repairs the index.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class PlayerService {

	private final PlayerRepository playerRepository;
	private final RoomRepository roomRepository;
	private final LobbyProperties properties;

	// ---- identity ----------------------------------------------------------------------------

	/**
	 * Authenticate or freshly mint a player identity from a status request.
	 *
	 * <p>If both {@code publicId} and {@code secretId} are supplied and match a stored
	 * player, that player is returned (and {@code touched()}). Otherwise a brand-new
	 * identity is allocated; the result's {@link Player#publicId()} /
	 * {@link Player#secretId()} are what the caller must persist as cookies.
	 */
	public Mono<IdentityResult> ensureIdentity(
		@Nullable UUID publicId,
		@Nullable UUID secretId,
		@Nullable String requestedName,
		@Nullable String requestedAvatar) {
		if (publicId == null || secretId == null) {
			return Mono.just(new IdentityResult(mintFresh(requestedName, requestedAvatar), true));
		}
		return playerRepository.findById(publicId)
			.flatMap(existing -> {
				if (!existing.secretId().equals(secretId)) {
					log.debug("Secret id mismatch for public id {} — allocating fresh identity", publicId);
					return Mono.just(new IdentityResult(mintFresh(requestedName, requestedAvatar), true));
				}
				Player updated = existing.touched();
				if (requestedName != null || requestedAvatar != null) {
					String name = sanitiseName(requestedName != null ? requestedName : existing.name());
					String avatar = sanitiseAvatar(requestedAvatar != null ? requestedAvatar : existing.avatar());
					updated = updated.withProfile(name, avatar);
				}
				return playerRepository.save(updated).map(saved -> new IdentityResult(saved, false));
			})
			.switchIfEmpty(Mono.just(new IdentityResult(mintFresh(requestedName, requestedAvatar), true)))
			.flatMap(result -> result.freshIdentity()
				? playerRepository.save(result.player()).map(saved -> new IdentityResult(saved, true))
				: Mono.just(result));
	}

	private Player mintFresh(@Nullable String requestedName, @Nullable String requestedAvatar) {
		UUID publicId = UUID.randomUUID();
		UUID secretId = UUID.randomUUID();
		String name = sanitiseName(requestedName != null ? requestedName : "Player-" + publicId.toString().substring(0, 4));
		String avatar = sanitiseAvatar(requestedAvatar != null ? requestedAvatar : "");
		return new Player(publicId, secretId, name, avatar, null, PlayerStatus.ACTIVE, java.time.Instant.now());
	}

	// ---- authentication ----------------------------------------------------------------------

	public Mono<Player> authenticate(UUID publicId, UUID secretId) {
		return playerRepository.findById(publicId)
			.switchIfEmpty(Mono.error(LobbyException.playerNotFound()))
			.flatMap(player -> player.secretId().equals(secretId)
				? Mono.just(player)
				: Mono.error(LobbyException.badCredentials()));
	}

	// ---- profile -----------------------------------------------------------------------------

	public Mono<Player> updateProfile(UUID publicId, UUID secretId, String name, String avatar) {
		String sanitisedName = sanitiseName(name);
		String sanitisedAvatar = sanitiseAvatar(avatar);
		return authenticate(publicId, secretId)
			.map(player -> player.withProfile(sanitisedName, sanitisedAvatar))
			.flatMap(playerRepository::save);
	}

	public Mono<Player> save(Player player) {
		return playerRepository.save(player);
	}

	public Mono<Void> delete(UUID publicId) {
		return playerRepository.delete(publicId);
	}

	// ---- room index lookup (with self-repair) ------------------------------------------------

	/**
	 * Return the room the given player is currently in, repairing the cached index if
	 * it points to a stale or wrong room.
	 *
	 * @return the room when membership is confirmed, or empty {@link Mono} otherwise
	 */
	public Mono<Room> resolveRoom(UUID publicId) {
		return playerRepository.findById(publicId)
			.flatMap(player -> {
				String cachedCode = player.roomCode();
				if (cachedCode == null) {
					return Mono.empty();
				}
				return roomRepository.findByCode(cachedCode)
					.flatMap(room -> room.contains(publicId)
						? Mono.just(room)
						: repairAndResolve(player))
					.switchIfEmpty(repairAndResolve(player));
			});
	}

	private Mono<Room> repairAndResolve(Player player) {
		log.warn("Repairing room index for player {} (was pointing to {})",
			player.publicId(), player.roomCode());
		return roomRepository.findAll()
			.filter(room -> room.contains(player.publicId()))
			.next()
			.flatMap(found -> playerRepository.save(player.withRoomCode(found.code())).thenReturn(found))
			.switchIfEmpty(playerRepository.save(player.withRoomCode(null)).then(Mono.empty()));
	}

	// ---- helpers -----------------------------------------------------------------------------

	private String sanitiseName(String name) {
		String trimmed = name == null ? "" : name.strip();
		if (trimmed.isEmpty()) {
			throw LobbyException.badRequest("Name must not be empty");
		}
		int max = properties.getMaxNameLength();
		return trimmed.length() > max ? trimmed.substring(0, max) : trimmed;
	}

	private String sanitiseAvatar(String avatar) {
		String value = avatar == null ? "" : avatar.strip();
		int max = properties.getMaxAvatarLength();
		return value.length() > max ? value.substring(0, max) : value;
	}

	/**
	 * Result envelope for {@link #ensureIdentity}.
	 */
	public record IdentityResult(Player player, boolean freshIdentity) {
	}
}

