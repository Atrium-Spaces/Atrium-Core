package org.atrium.core.autoconfigure;

import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;

import java.util.List;

/**
 * Operator-tunable lobby settings. Bind via {@code atrium.core.*} in
 * {@code application.yml}. {@link AtriumAutoConfiguration} registers this with
 * {@code @EnableConfigurationProperties}, so Spring can inject it anywhere.
 */
@Getter
@Setter
@ConfigurationProperties(prefix = "atrium.core")
public class AtriumProperties {

	/**
	 * Length of each generated room code, in characters.
	 */
	private int roomCodeLength = 6;

	/**
	 * Default cap on players for newly-created rooms.
	 */
	private int defaultMaxPlayers = 8;

	/**
	 * Hard ceiling on {@code maxPlayers} regardless of host preference.
	 */
	private int absoluteMaxPlayers = 32;

	/**
	 * Hard cap on player {@code name} length, in characters.
	 */
	private int maxNameLength = 32;

	/**
	 * Hard cap on player {@code avatar} string length, in characters.
	 */
	private int maxAvatarLength = 256;

	/**
	 * Grace window after a WebSocket drop before the player is officially removed.
	 */
	private long disconnectGracePeriodSeconds = 60L;

	/**
	 * A lobby-state room is deleted after this long with no activity.
	 */
	private long lobbyInactiveTtlSeconds = 7_200L; // 2 hours

	/**
	 * An in-game room is deleted after this long with no activity.
	 */
	private long inGameInactiveTtlSeconds = 259_200L; // 3 days

	/**
	 * Players not in any room are removed after this long.
	 */
	private long roomlessPlayerTtlSeconds = 7_200L; // 2 hours

	/**
	 * How often {@link org.atrium.core.domain.service.LobbyCleanupService} sweeps.
	 */
	private long cleanupIntervalSeconds = 300L; // 5 minutes

	/**
	 * WebSocket mount point (clients connect to {@code wss://host{path}/{code}}).
	 */
	private String websocketPath = "/api/atrium/ws";

	/**
	 * CORS origins for the REST surface; {@code "*"} for permissive development.
	 */
	private List<String> corsAllowedOrigins = List.of("*");
}
