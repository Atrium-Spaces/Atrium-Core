package org.atrium.core.domain.service;

import lombok.RequiredArgsConstructor;
import org.atrium.core.autoconfigure.AtriumProperties;
import org.atrium.core.domain.model.GameSettings;
import org.jspecify.annotations.Nullable;
import org.springframework.stereotype.Component;

/**
 * Centralises room player-count policy.
 *
 * <p>The global absolute bounds come from {@link AtriumProperties} and are validated once
 * at startup. A concrete {@link GameSettings} subtype may narrow or widen those bounds per
 * room through its override hooks, so those override values are resolved here and then used
 * consistently for room creation, room settings updates, and {@code RoomView} assembly.
 */
@Component
@RequiredArgsConstructor
public final class RoomPlayerLimitsResolver {

	private final AtriumProperties properties;

	/**
	 * Resolve the effective absolute player bounds for a room.
	 *
	 * @param gameSettings room-specific settings carrying optional absolute overrides
	 * @return the effective absolute bounds for this room
	 * @throws IllegalStateException when a game-specific override describes impossible bounds
	 */
	public AbsolutePlayerLimits absoluteLimits(GameSettings gameSettings) {
		final Integer absoluteMinPlayersOverride = gameSettings.absoluteMinPlayersOverride();
		final Integer absoluteMaxPlayersOverride = gameSettings.absoluteMaxPlayersOverride();
		final int absoluteMinPlayers = absoluteMinPlayersOverride != null ? absoluteMinPlayersOverride : properties.getAbsoluteMinPlayers();
		final int absoluteMaxPlayers = absoluteMaxPlayersOverride != null ? absoluteMaxPlayersOverride : properties.getAbsoluteMaxPlayers();
		validateAbsoluteBounds(absoluteMinPlayers, absoluteMaxPlayers);
		return new AbsolutePlayerLimits(absoluteMinPlayers, absoluteMaxPlayers);
	}

	/**
	 * Normalise requested player limits for room creation.
	 *
	 * <p>Null values fall back to the configured defaults. If the caller provides
	 * {@code maxPlayers < minPlayers}, the two values are swapped. The resulting pair is then
	 * clamped into the room's effective absolute bounds.
	 */
	public NormalizedPlayerLimits normalizeForCreate(@Nullable Integer requestedMinPlayers, @Nullable Integer requestedMaxPlayers, GameSettings gameSettings) {
		return normalize(requestedMinPlayers, requestedMaxPlayers, properties.getDefaultMinPlayers(), properties.getDefaultMaxPlayers(), gameSettings);
	}

	/**
	 * Normalise requested player limits for a room settings update.
	 *
	 * <p>Null values keep the current room values. If the caller provides
	 * {@code maxPlayers < minPlayers}, the two values are swapped. The resulting pair is then
	 * clamped into the room's effective absolute bounds.
	 */
	public NormalizedPlayerLimits normalizeForUpdate(@Nullable Integer requestedMinPlayers, @Nullable Integer requestedMaxPlayers, int currentMinPlayers, int currentMaxPlayers, GameSettings gameSettings) {
		return normalize(requestedMinPlayers, requestedMaxPlayers, currentMinPlayers, currentMaxPlayers, gameSettings);
	}

	private NormalizedPlayerLimits normalize(@Nullable Integer requestedMinPlayers, @Nullable Integer requestedMaxPlayers, int fallbackMinPlayers, int fallbackMaxPlayers, GameSettings gameSettings) {
		final AbsolutePlayerLimits absolutePlayerLimits = absoluteLimits(gameSettings);
		int minPlayers = requestedMinPlayers != null ? requestedMinPlayers : fallbackMinPlayers;
		int maxPlayers = requestedMaxPlayers != null ? requestedMaxPlayers : fallbackMaxPlayers;

		if (maxPlayers < minPlayers) {
			final int swappedMinPlayers = maxPlayers;
			maxPlayers = minPlayers;
			minPlayers = swappedMinPlayers;
		}

		minPlayers = clamp(minPlayers, absolutePlayerLimits.absoluteMinPlayers(), absolutePlayerLimits.absoluteMaxPlayers());
		maxPlayers = clamp(maxPlayers, absolutePlayerLimits.absoluteMinPlayers(), absolutePlayerLimits.absoluteMaxPlayers());
		return new NormalizedPlayerLimits(minPlayers, maxPlayers, absolutePlayerLimits.absoluteMinPlayers(), absolutePlayerLimits.absoluteMaxPlayers());
	}

	private static int clamp(int value, int minimum, int maximum) {
		return Math.clamp(value, minimum, maximum);
	}

	private static void validateAbsoluteBounds(int absoluteMinPlayers, int absoluteMaxPlayers) {
		if (absoluteMinPlayers < 1) {
			throw new IllegalStateException("GameSettings absoluteMinPlayersOverride must be at least 1");
		}
		if (absoluteMaxPlayers < absoluteMinPlayers) {
			throw new IllegalStateException("GameSettings absoluteMaxPlayersOverride must be at least absoluteMinPlayersOverride");
		}
	}

	/**
	 * Effective absolute bounds after applying room-specific overrides.
	 */
	public record AbsolutePlayerLimits(int absoluteMinPlayers, int absoluteMaxPlayers) {
	}

	/**
	 * Effective room player limits together with the absolute bounds they were derived from.
	 */
	public record NormalizedPlayerLimits(int minPlayers, int maxPlayers, int absoluteMinPlayers, int absoluteMaxPlayers) {
	}
}

