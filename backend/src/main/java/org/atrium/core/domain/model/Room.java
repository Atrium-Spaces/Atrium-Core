package org.atrium.core.domain.model;

import org.jspecify.annotations.Nullable;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

/**
 * An Atrium room. The room entity is the single source of truth for membership — the
 * {@link Player#roomCodes()} field is a derived index that can be rebuilt from a scan
 * of all rooms if it drifts.
 *
 * @param code           6-character upper-case alphanumeric room code (unique)
 * @param name           optional display name for room lists; {@code null} means unnamed
 * @param host           public id of the host player
 * @param players        ordered list of player public ids; index 0 is the longest-joined
 *                       player. {@link #host} is always present in this list.
 * @param minPlayers     floor required to start a game; mutable in {@link RoomState#LOBBY}
 * @param maxPlayers     cap on {@link #players}'s size; mutable in {@link RoomState#LOBBY}
 * @param gameSettings   polymorphic game-specific settings; mutable in {@link RoomState#LOBBY}
 * @param isPrivate      when {@code true}, the room is hidden from the public listing
 * @param state          {@link RoomState#LOBBY} or {@link RoomState#IN_GAME}
 * @param createdAt      wall-clock creation time
 * @param lastActivityAt last time anything in this room changed; drives the room TTL
 */
public record Room(String code, @Nullable String name, UUID host, List<UUID> players, int minPlayers, int maxPlayers, GameSettings gameSettings, boolean isPrivate, RoomState state, Instant createdAt, Instant lastActivityAt) {

	public Room withPlayers(List<UUID> newPlayers) {
		return new Room(code, name, host, List.copyOf(newPlayers), minPlayers, maxPlayers, gameSettings, isPrivate, state, createdAt, Instant.now());
	}

	public Room withHost(UUID newHost) {
		return new Room(code, name, newHost, players, minPlayers, maxPlayers, gameSettings, isPrivate, state, createdAt, Instant.now());
	}

	public Room withState(RoomState newState) {
		return new Room(code, name, host, players, minPlayers, maxPlayers, gameSettings, isPrivate, newState, createdAt, Instant.now());
	}

	public Room withSettings(@Nullable String newName, int newMinPlayers, int newMaxPlayers, GameSettings newSettings, boolean newIsPrivate) {
		return new Room(code, newName, host, players, newMinPlayers, newMaxPlayers, newSettings, newIsPrivate, state, createdAt, Instant.now());
	}

	public Room touched() {
		return new Room(code, name, host, players, minPlayers, maxPlayers, gameSettings, isPrivate, state, createdAt, Instant.now());
	}

	public boolean contains(UUID publicId) {
		return players.contains(publicId);
	}

	public boolean isFull() {
		return players.size() >= maxPlayers;
	}
}
