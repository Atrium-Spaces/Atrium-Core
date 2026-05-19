package org.atrium.core.domain.model;

import com.fasterxml.jackson.annotation.JsonTypeName;

/**
 * Fallback {@link GameSettings} implementation used when the host application has not
 * registered any concrete subtype. Carries no game-specific configuration so it's safe
 * to round-trip; primarily exists to let the lobby library run standalone for
 * integration testing.
 */
@JsonTypeName("default")
public final class DefaultGameSettings extends GameSettings {

	@Override
	public String gameKind() {
		return "default";
	}
}
