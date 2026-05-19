package org.atrium.core.spi.model;

import org.atrium.core.domain.model.GameSettings;

/**
 * Backwards-friendly alias for host projects that prefer an SPI-facing name.
 *
 * <p>Functionally identical to {@link GameSettings}; this type exists to align
 * with starter-style integration docs where consuming projects extend an
 * "Abstract*" base class from the extension package.
 */
public abstract class AbstractGameSettings extends GameSettings {
}

