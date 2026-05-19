package org.atrium.core.domain.model;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

/**
 * A lobby room. The room entity is the single source of truth for membership — the
 * {@link Player#roomCode()} field is a derived index that can be rebuilt from a scan
 * of all rooms if it drifts.
 *
 * @param code           6-character upper-case alphanumeric room code (unique)
 * @param host           public id of the host player
 * @param players        ordered list of player public ids; index 0 is the longest-joined
 *                       player. {@link #host} is always present in this list.
 * @param maxPlayers     cap on {@link #players}'s size; mutable in {@link RoomState#LOBBY}
 * @param gameSettings   polymorphic game-specific settings; mutable in {@link RoomState#LOBBY}
 * @param isPrivate      when {@code true}, the room is hidden from the public listing
 * @param state          {@link RoomState#LOBBY} or {@link RoomState#IN_GAME}
 * @param createdAt      wall-clock creation time
 * @param lastActivityAt last time anything in this room changed; drives the room TTL
 */
public record Room(
	String code,
	UUID host,
	List<UUID> players,
	int maxPlayers,
	GameSettings gameSettings,
	boolean isPrivate,
	RoomState state,
	Instant createdAt,
	Instant lastActivityAt) {

	public Room withPlayers(List<UUID> newPlayers) {
		return new Room(code, host, List.copyOf(newPlayers), maxPlayers, gameSettings, isPrivate, state, createdAt, Instant.now());
	}

	public Room withHost(UUID newHost) {
		return new Room(code, newHost, players, maxPlayers, gameSettings, isPrivate, state, createdAt, Instant.now());
	}

	public Room withState(RoomState newState) {
		return new Room(code, host, players, maxPlayers, gameSettings, isPrivate, newState, createdAt, Instant.now());
	}

	public Room withSettings(int newMaxPlayers, GameSettings newSettings, boolean newIsPrivate) {
		return new Room(code, host, players, newMaxPlayers, newSettings, newIsPrivate, state, createdAt, Instant.now());
	}

	public Room touched() {
		return new Room(code, host, players, maxPlayers, gameSettings, isPrivate, state, createdAt, Instant.now());
	}

	public boolean contains(UUID publicId) {
		return players.contains(publicId);
	}

	public boolean isFull() {
		return players.size() >= maxPlayers;
	}
}

