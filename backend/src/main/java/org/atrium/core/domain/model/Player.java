package org.atrium.core.domain.model;

import org.atrium.core.domain.service.PlayerService;

import org.jspecify.annotations.Nullable;

import java.time.Instant;
import java.util.UUID;

/**
 * A player known to Atrium.
 *
 * <p>The pair ({@link #publicId}, {@link #secretId}) is the player's identity. The
 * public id is shared with everyone else in the room; the secret id is known only to
 * the player (stored client-side as a cookie) and is required to perform authenticated
 * operations like kicking, leaving, or updating one's profile.
 *
 * <p>{@link #roomCode} is the player's <em>active index</em> into the room they're
 * currently in — convenience only. The {@link Room#players()} list is the source of
 * truth; if these two disagree, the
 * {@link PlayerService#resolveRoom(UUID)} repair
 * scan corrects the index.
 *
 * @param publicId     identity shared with other clients
 * @param secretId     identity proving "this is me" to the server
 * @param name         display name (1–{@code maxNameLength} characters)
 * @param avatar       free-form avatar string (URL, emoji, iconify id — opaque to Atrium)
 * @param roomCode     code of the room this player has currently joined, or {@code null}
 * @param status       {@link PlayerStatus#ACTIVE} or {@link PlayerStatus#DISCONNECTED}
 * @param lastActiveAt last time the player did anything; drives the inactive-player TTL
 */
public record Player(UUID publicId, UUID secretId, String name, String avatar, @Nullable String roomCode, PlayerStatus status, Instant lastActiveAt) {

	public Player withRoomCode(@Nullable String newRoomCode) {
		return new Player(publicId, secretId, name, avatar, newRoomCode, status, Instant.now());
	}

	public Player withProfile(String newName, String newAvatar) {
		return new Player(publicId, secretId, newName, newAvatar, roomCode, status, Instant.now());
	}

	public Player withStatus(PlayerStatus newStatus) {
		return new Player(publicId, secretId, name, avatar, roomCode, newStatus, Instant.now());
	}
}
