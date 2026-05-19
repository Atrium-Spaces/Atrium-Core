package org.atrium.core.domain.model;

import com.fasterxml.jackson.annotation.JsonTypeInfo;

/**
 * Polymorphic base type for game-specific room settings.
 *
 * <p>Downstream projects extend this class with their own concrete subtype (for example
 * {@code ChessSettings} carrying clock and variant information) and register the subtype
 * either:
 * <ul>
 *   <li>via {@link com.fasterxml.jackson.annotation.JsonSubTypes} on this class (when
 *       the subtype lives in the same module — see {@link DefaultGameSettings}), or</li>
 *   <li>by contributing a {@link com.fasterxml.jackson.databind.Module} bean that calls
 *       {@code registerSubtypes(...)} on the application {@code ObjectMapper}.</li>
 * </ul>
 *
 * <p>The {@link JsonTypeInfo} annotation embeds a {@code type} discriminator into the
 * JSON payload so the Atrium system can round-trip arbitrary subclasses through Redis
 * and the wire without knowing them ahead of time.
 */
@JsonTypeInfo(use = JsonTypeInfo.Id.NAME, include = JsonTypeInfo.As.PROPERTY, property = "type", defaultImpl = DefaultGameSettings.class)
public abstract class GameSettings {

	/**
	 * @return a short human-readable label for the game type (e.g. {@code "chess"});
	 * used by the home page when listing public rooms.
	 */
	public abstract String gameKind();
}
