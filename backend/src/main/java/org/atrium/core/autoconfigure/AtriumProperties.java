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
public final class AtriumProperties {

	/**
	 * Length of each generated room code, in characters.
	 */
	private int roomCodeLength = 6;

	/**
	 * Default floor on players for newly-created rooms.
	 */
	private int defaultMinPlayers = 2;

	/**
	 * Hard floor on {@code minPlayers} regardless of host preference.
	 */
	private int absoluteMinPlayers = 1;

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
	 * Generic inactivity threshold (in seconds) used by scheduled cleanup jobs.
	 *
	 * <p>Applied to stale room cleanup and stale player cleanup flows.
	 */
	private long cleanupInactiveSeconds = 259_200L; // 3 days

	/**
	 * WebSocket mount point (clients connect to {@code wss://host{path}/{code}}).
	 */
	private String websocketPath = "/api/atrium/ws";

	/**
	 * CORS origins for the REST surface; {@code "*"} for permissive development.
	 */
	private List<String> corsAllowedOrigins = List.of("*");
}
