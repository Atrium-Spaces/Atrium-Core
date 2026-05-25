package org.atrium.core.domain.model;

import org.atrium.core.domain.service.PlayerService;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * A player known to Atrium.
 *
 * <p>The pair ({@link #publicId}, {@link #secretId}) is the player's identity. The
 * public id is shared with everyone else in the room; the secret id is known only to
 * the player (stored client-side as a cookie) and is required to perform authenticated
 * operations like kicking, leaving, or updating one's profile.
 *
 * <p>{@link #roomCodes} is the player's <em>active index</em> into the rooms they're
 * currently in — convenience only. The {@link Room#players()} list is the source of
 * truth; if these disagree, the
 * {@link PlayerService#resolvePlayerRooms(UUID)} repair
 * scan corrects the index.
 *
 * @param publicId     identity shared with other clients
 * @param secretId     identity proving "this is me" to the server
 * @param name         display name (1–{@code maxNameLength} characters)
 * @param avatar       free-form avatar string (URL, emoji, iconify id — opaque to Atrium)
 * @param roomCodes    codes of the rooms this player has currently joined; empty list means none
 * @param status       {@link PlayerStatus#ACTIVE} or {@link PlayerStatus#DISCONNECTED}
 * @param lastActiveAt last time the player did anything; drives the inactive-player TTL
 */
public record Player(UUID publicId, UUID secretId, String name, String avatar, List<String> roomCodes, PlayerStatus status, Instant lastActiveAt) {

	public Player {
		roomCodes = List.copyOf(roomCodes);
	}

	public Player withRoomAdded(String roomCode) {
		final List<String> updated = new ArrayList<>(roomCodes);
		if (!updated.contains(roomCode)) {
			updated.add(roomCode);
		}
		return new Player(publicId, secretId, name, avatar, List.copyOf(updated), status, Instant.now());
	}

	public Player withRoomRemoved(String roomCode) {
		final List<String> updated = new ArrayList<>(roomCodes);
		updated.remove(roomCode);
		return new Player(publicId, secretId, name, avatar, List.copyOf(updated), status, Instant.now());
	}

	public Player withRoomCodes(List<String> newRoomCodes) {
		return new Player(publicId, secretId, name, avatar, newRoomCodes, status, Instant.now());
	}

	public Player withProfile(String newName, String newAvatar) {
		return new Player(publicId, secretId, newName, newAvatar, roomCodes, status, Instant.now());
	}

	public Player withStatus(PlayerStatus newStatus) {
		return new Player(publicId, secretId, name, avatar, roomCodes, newStatus, Instant.now());
	}
}
