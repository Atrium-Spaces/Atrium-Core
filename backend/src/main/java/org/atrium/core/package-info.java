/**
 * Reusable lobby / room-management library for turn-based or real-time multiplayer
 * board games.
 *
 * <p>The package exposes:
 * <ul>
 *   <li>A reactive REST surface ({@link org.atrium.core.api.controller.LobbyController})
 *       covering status, create / join / leave / delete / kick / update-profile and
 *       start / stop game operations.</li>
 *   <li>A WebFlux WebSocket handler ({@link org.atrium.core.websocket.RoomWebSocketHandler})
 *       that bridges per-room Redis pub/sub channels to connected clients.</li>
 *   <li>A polymorphic {@link org.atrium.core.domain.model.GameSettings} base type that
 *       downstream projects extend with their own game-specific configuration.</li>
 *   <li>Spring Boot auto-configuration
 *       ({@link org.atrium.core.autoconfigure.LobbyAutoConfiguration}) so the library can be
 *       dropped in as a dependency.</li>
 * </ul>
 *
 * <p>Redis is the single source of truth for room and player state; the in-memory
 * services hold only transient scheduling data (e.g. disconnect timers).
 */
@NullMarked
package org.atrium.core;

import org.jspecify.annotations.NullMarked;
