# Architecture

> How Atrium Core is put together. For per-endpoint detail see [`API.md`](./API.md); for
> the lobby protocol specifics see [`LOBBY.md`](./LOBBY.md).

## 1. Project layout

```
Atrium-Core/
├── backend/                                  Spring Boot + WebFlux + Redis
│   └── src/main/java/org/atrium/
│       ├── Application.java                  Reference host (single @SpringBootApplication)
│       └── core/                             Reusable core library
│           ├── autoconfigure/                Spring Boot starter wiring + properties
│           ├── api/                          REST controllers, DTOs, API errors
│           ├── domain/                       Domain model + core orchestration
│           ├── redis/                        Redis config, repositories, pub/sub bridge
│           ├── spi/                          Consumer extension points
│           └── websocket/                    WebFlux WebSocket bridge to Redis pub/sub
└── frontend/                                 Angular standalone app (lobby UI lives here)
```

The split is deliberate: `org.atrium.core.*` is the **library**, `org.atrium.Application`
is just the demo host so it can run standalone. Downstream projects depending on this
module get the core infrastructure auto-wired without needing the host class.

## 2. Tech stack

| Layer            | Choice                                                        | Why                                                                             |
|------------------|---------------------------------------------------------------|---------------------------------------------------------------------------------|
| Build (backend)  | Gradle 9, Kotlin DSL                                          | Idiomatic for modern Spring Boot; reproducible archives.                        |
| Runtime          | Java 21                                                       | Records, sealed interfaces, pattern matching.                                   |
| Framework        | Spring Boot 3.x, WebFlux                                      | Reactive REST + reactive WebSocket out of the box.                              |
| Persistence      | Redis (reactive driver via Spring Data Redis Reactive)        | Single store for both state cache and pub/sub fan-out.                          |
| Messaging        | Redis Pub/Sub (`ReactiveRedisMessageListenerContainer`)       | Channels per room; no extra broker; multi-instance fan-out.                     |
| Serialisation    | Jackson with `@JsonTypeInfo` polymorphism                     | Lets downstream projects bring their own `GameSettings` subtype.                |
| Collections      | fastutil for hot paths                                        | See [`CODE_STYLES.md` §3.9](./CODE_STYLES.md#39-fastutil-over-jdk-collections). |
| Null discipline  | JSpecify (`@NullMarked` on every package, `@Nullable` opt-in) | Documented contracts, IDE-checked.                                              |
| Code generation  | Lombok                                                        | `@RequiredArgsConstructor`, `@Slf4j` on every service.                          |
| Build (frontend) | Angular CLI 21 + ESLint                                       | Standalone components, signals.                                                 |
| UI               | PrimeNG 21, Iconify, Transloco                                | Component library, icons, i18n with British / American split.                   |

## 3. Lobby data model

Two top-level entities:

### 3.1 `Room`

```java
record Room(
        String code,                  // 6-char [A-Z0-9]
        UUID host,                    // public id of the host player
        List<UUID> players,           // public ids in join order; index 0 is longest-joined
        int maxPlayers,
        GameSettings gameSettings,    // polymorphic (@JsonTypeInfo)
        boolean isPrivate,
        RoomState state,              // LOBBY | IN_GAME
        Instant createdAt,
        Instant lastActivityAt)
```

Stored at `lobby:room:{code}` as JSON. Indexed in two sorted sets:

- `lobby:rooms:all`     — every room, scored by `lastActivityAt` (cleanup sweep).
- `lobby:rooms:public`  — only `!isPrivate` rooms (home-page listing).

### 3.2 `Player`

```java
record Player(
        UUID publicId,
        UUID secretId,                // never exposed to other clients
        String name,
        String avatar,
        @Nullable String roomCode,    // active index — repairable
        PlayerStatus status,          // ACTIVE | DISCONNECTED
        Instant lastActiveAt)
```

Stored at `lobby:player:{publicId}`. Indexed in `lobby:players:all` scored by
`lastActiveAt` for the inactive-player sweep.

### 3.3 The active-index repair scan

`Player.roomCode` is **derived state** — a convenience pointer. The
`Room.players` list is the source of truth. If a lookup detects that

- the room at `Player.roomCode` doesn't exist, **or**
- the room exists but doesn't contain this player,

then {@link org.atrium.core.domain.service.PlayerService#resolveRoom} performs an emergency
scan of every room in the `all` index. If exactly one room contains the player, the
index is rewritten to match; otherwise the index is cleared.

## 4. Request lifecycle

```
client ──REST──▶ LobbyController ──▶ RoomService / PlayerService ──┐
                                                                   │
                                       ┌───── Redis (state) ◀──────┤
                                       │                           │
                                       └───── Redis (pub/sub) ◀────┘
                                                  │
                       all instances subscribed ──┴──▶ RoomWebSocketHandler
                                                              │
                                                       client (push frame)
```

1. The browser sends a write through REST (`POST /api/lobby/rooms/{code}/join`).
2. `RoomService` authenticates the (publicId, secretId) pair, re-reads the room from
   Redis (source of truth), mutates, and writes back.
3. After the write commits, it `PUBLISH`es a `RoomEvent` on `lobby:events:{code}`.
4. Every Spring instance — including ones the client isn't connected to — has the
   `ReactiveRedisMessageListenerContainer` subscribed; each instance forwards the
   event to every WebSocket session it holds for that room.

## 5. Restart resilience

| State                      | Where it lives               | Survives Spring restart?              |
|----------------------------|------------------------------|---------------------------------------|
| Rooms                      | Redis (`lobby:room:*`)       | Yes                                   |
| Players                    | Redis (`lobby:player:*`)     | Yes                                   |
| Room / player indexes      | Redis (sorted sets)          | Yes                                   |
| WebSocket sessions         | In-memory on each instance   | No — clients reconnect                |
| Disconnect grace timers    | `DisconnectTracker` (in-mem) | No — cleanup sweep eventually catches |
| Public id ↔ Redis identity | Browser cookies + Redis      | Yes                                   |

## 6. Multi-instance behaviour

The lobby is **horizontally scalable**: any instance can serve any request, because

- every read/write goes through Redis,
- every cross-client notification fans out through Redis pub/sub, not through
  in-memory channels.

The only instance-local piece is the disconnect grace timer. If a player's WebSocket
was on instance A and instance A crashes during the 60-second window, the player is
not immediately leave-room'd — but the next scheduled cleanup sweep will reap the
room if it remains idle past its TTL, so the system is eventually consistent.

## 7. Inactivity TTLs

| Entity                     | Configurable                                       | Default  |
|----------------------------|----------------------------------------------------|----------|
| Lobby-state room           | `atrium.lobby.lobby-inactive-ttl-seconds`          | 2 hours  |
| In-game room               | `atrium.lobby.in-game-inactive-ttl-seconds`        | 3 days   |
| Player with no `roomCode`  | `atrium.lobby.roomless-player-ttl-seconds`         | 2 hours  |
| Disconnect grace window    | `atrium.lobby.disconnect-grace-period-seconds`     | 60s      |
| Cleanup sweep interval     | `atrium.lobby.cleanup-interval-seconds`            | 5 min    |

`LobbyCleanupService` runs the sweep on a `@Scheduled` fixed delay. Rooms exceeding
their state-specific TTL are deleted (broadcasting `RoomEvent.RoomDeleted` first so
any still-connected clients can react); roomless inactive players are removed.

## 8. Extension points for downstream projects

To embed Atrium Core's lobby in another Spring Boot game project:

1. Add this module as a Gradle dependency.
2. Subclass `GameSettings` (or the SPI-facing alias
   `org.atrium.core.spi.model.AbstractGameSettings`) with your game-specific configuration and register the
   subtype via a Jackson `Module` bean:

   ```java
   @Bean
   public Module myGameSettingsModule() {
       SimpleModule module = new SimpleModule("MyGameSettingsModule");
       module.registerSubtypes(MyGameSettings.class);
       return module;
   }
   ```

3. Mount the game's own controllers / WebSocket handlers at non-`/api/lobby` paths.
   The lobby will broadcast `RoomEvent.StateChanged(IN_GAME)` when the host starts a
   game — that's the cue for game-specific code to take over.

Optional: annotate your host app with
`org.atrium.core.spi.EnableLobbySystem` when you want explicit, annotation-driven
import of lobby auto-configuration (functionally equivalent to Boot auto-discovery).

The lobby never touches game logic directly; the boundary is the `RoomState`
transition + the polymorphic `GameSettings`.

