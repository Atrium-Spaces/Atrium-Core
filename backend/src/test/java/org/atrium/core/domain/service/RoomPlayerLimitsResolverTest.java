package org.atrium.core.domain.service;

import org.atrium.core.autoconfigure.AtriumProperties;
import org.atrium.core.domain.model.GameSettings;
import org.jspecify.annotations.NonNull;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class RoomPlayerLimitsResolverTest {

	private final RoomPlayerLimitsResolver roomPlayerLimitsResolver;

	RoomPlayerLimitsResolverTest() {
		final AtriumProperties properties = new AtriumProperties();
		properties.setDefaultMinPlayers(2);
		properties.setDefaultMaxPlayers(8);
		properties.setAbsoluteMinPlayers(1);
		properties.setAbsoluteMaxPlayers(10);
		roomPlayerLimitsResolver = new RoomPlayerLimitsResolver(properties);
	}

	@Test
	void normalizeForCreateSwapsAndClampsValues() {
		RoomPlayerLimitsResolver.NormalizedPlayerLimits normalizedPlayerLimits = roomPlayerLimitsResolver.normalizeForCreate(12, -3, new TestGameSettings(null, null));

		assertEquals(1, normalizedPlayerLimits.minPlayers());
		assertEquals(10, normalizedPlayerLimits.maxPlayers());
		assertEquals(1, normalizedPlayerLimits.absoluteMinPlayers());
		assertEquals(10, normalizedPlayerLimits.absoluteMaxPlayers());
	}

	@Test
	void normalizeForCreateUsesGameSpecificAbsoluteBounds() {
		RoomPlayerLimitsResolver.NormalizedPlayerLimits normalizedPlayerLimits = roomPlayerLimitsResolver.normalizeForCreate(1, 10, new TestGameSettings(3, 6));

		assertEquals(3, normalizedPlayerLimits.minPlayers());
		assertEquals(6, normalizedPlayerLimits.maxPlayers());
		assertEquals(3, normalizedPlayerLimits.absoluteMinPlayers());
		assertEquals(6, normalizedPlayerLimits.absoluteMaxPlayers());
	}

	@Test
	void normalizeForUpdateKeepsCurrentValuesWhenUnset() {
		RoomPlayerLimitsResolver.NormalizedPlayerLimits normalizedPlayerLimits = roomPlayerLimitsResolver.normalizeForUpdate(null, null, 4, 7, new TestGameSettings(null, null));

		assertEquals(4, normalizedPlayerLimits.minPlayers());
		assertEquals(7, normalizedPlayerLimits.maxPlayers());
	}

	@Test
	void absoluteLimitsRejectImpossibleGameSpecificOverrides() {
		assertThrows(IllegalStateException.class, () -> roomPlayerLimitsResolver.absoluteLimits(new TestGameSettings(0, 5)));
		assertThrows(IllegalStateException.class, () -> roomPlayerLimitsResolver.absoluteLimits(new TestGameSettings(6, 5)));
	}

	private static final class TestGameSettings extends GameSettings {

		private final Integer absoluteMinPlayersOverride;
		private final Integer absoluteMaxPlayersOverride;

		private TestGameSettings(Integer absoluteMinPlayersOverride, Integer absoluteMaxPlayersOverride) {
			this.absoluteMinPlayersOverride = absoluteMinPlayersOverride;
			this.absoluteMaxPlayersOverride = absoluteMaxPlayersOverride;
		}

		@Override
		public @NonNull String gameKind() {
			return "test";
		}

		@Override
		public Integer absoluteMinPlayersOverride() {
			return absoluteMinPlayersOverride;
		}

		@Override
		public Integer absoluteMaxPlayersOverride() {
			return absoluteMaxPlayersOverride;
		}
	}
}

