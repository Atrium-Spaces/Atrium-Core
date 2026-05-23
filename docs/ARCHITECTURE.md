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
│           ├── extension/                    Consumer extension points
│           └── websocket/                    WebFlux WebSocket bridge to Redis pub/sub
└── frontend/                                 Angular standalone app (lobby UI lives here)
```

The split is deliberate: `org.atrium.core.*` is the **library**, `org.atrium.Application`
is just the demo host so it can run standalone. Downstream projects depending on this
module get the core infrastructure auto-wired without needing the host class.

## 2. Tech stack

| Layer            | Choice                                                        | Why                                                                               |
|------------------|---------------------------------------------------------------|-----------------------------------------------------------------------------------|
| Build (backend)  | Gradle 9, Kotlin DSL                                          | Idiomatic for modern Spring Boot; reproducible archives.                          |
| Runtime          | Java 21                                                       | Records, sealed interfaces, pattern matching.                                     |
| Framework        | Spring Boot 3.x, WebFlux                                      | Reactive REST + reactive WebSocket out of the box.                                |
| Persistence      | Redis (reactive driver via Spring Data Redis Reactive)        | Single store for both state cache and pub/sub fan-out.                            |
| Messaging        | Redis Pub/Sub (`ReactiveRedisMessageListenerContainer`)       | Per-room channel plus home-list channel; no extra broker; multi-instance fan-out. |
| Serialisation    | Jackson with `@JsonTypeInfo` polymorphism                     | Lets downstream projects bring their own `GameSettings` subtype.                  |
| Null discipline  | JSpecify (`@NullMarked` on every package, `@Nullable` opt-in) | Documented contracts, IDE-checked.                                                |
| Code generation  | Lombok                                                        | `@RequiredArgsConstructor`, `@Slf4j` on every service.                            |
| Build (frontend) | Angular CLI 21 + ESLint                                       | Standalone components, signals.                                                   |
| UI               | PrimeNG 21, Iconify, Transloco                                | Component library, icons, i18n with British / American split.                     |

## 3. Event model

Two sealed event families handle all state-change notification:

- **`org.atrium.core.domain.event.RoomEvent`** — published per room to
  `atrium:events:{code}`. Subtypes cover player join/leave/kick/update,
  disconnect/reconnect, host change, settings change, state change (start/stop),
  room deletion, and an initial snapshot for new WebSocket subscribers.
- **`org.atrium.core.domain.event.HomeEvent`** — published to the shared
  `atrium:events:home` channel. Subtypes are `snapshot`, `roomCreated`,
  `roomUpdated`, and `roomDeleted`. The home-screen WebSocket handler
  (`org.atrium.core.websocket.HomeWebSocketHandler`) fans these out to every
  connected home-page client.

See the type discriminator tables in [`docs/API.md`](./API.md#event-shapes) for every variant.

## 4. Lobby data model

Two top-level entities:

### 4.1 `Room`

```java
record Room(
	String code,                  // 6-char [A-Z0-9]
	@Nullable String name,        // optional display name; null = unnamed
	UUID host,                    // public id of the host player
	List<UUID> players,           // public ids in join order; index 0 is longest-joined
	int minPlayers,
	int maxPlayers,
	GameSettings gameSettings,    // polymorphic (@JsonTypeInfo)
	boolean isPrivate,
	RoomState state,              // LOBBY | IN_GAME
	Instant createdAt,
	Instant lastActivityAt
) {
}
```

Stored at `atrium:room:{code}` as JSON. Indexed in two sorted sets:

- `atrium:rooms:all`     — every room, scored by `lastActivityAt` (cleanup sweep).
- `atrium:rooms:public`  — only `!isPrivate` rooms (home-page listing).

### 4.2 `Player`

```java
record Player(
	UUID publicId,
	UUID secretId,                // never exposed to other clients
	String name,
	String avatar,
	@Nullable String roomCode,    // active index — repairable
	PlayerStatus status,          // ACTIVE | DISCONNECTED
	Instant lastActiveAt
) {
}
```

Stored at `atrium:player:{publicId}`. Indexed in `atrium:players:all` scored by
`lastActiveAt`.

### 4.3 The active-index repair scan

`Player.roomCode` is **derived state** — a convenience pointer. The
`Room.players` list is the source of truth. If a lookup detects that

- the room at `Player.roomCode` doesn't exist, **or**
- the room exists but doesn't contain this player,

then `PlayerService.resolveRoom(...)` performs an emergency scan of every room in the
`all` index. If exactly one room contains the player, the index is rewritten to match;
otherwise the index is cleared.

## 5. Request lifecycle

```
client ──REST──▶ AtriumController ──▶ RoomService / PlayerService ──┐
                                                                   │
                                       ┌───── Redis (state) ◀──────┤
                                       │                           │
                                       └───── Redis (pub/sub) ◀────┘
                                                  │
                       all instances subscribed ──┬──▶ RoomWebSocketHandler (/ws/{code})
                                                  └──▶ HomeWebSocketHandler (/ws/home)
                                                              │
                                                       client (push frame)
```

1. The browser sends a write through REST (`POST /api/atrium/rooms/{code}/join`).
2. `RoomService` authenticates the (publicId, secretId) pair, re-reads the room from
   Redis (source of truth), mutates, and writes back.
3. After the write commits, it `PUBLISH`es a `RoomEvent` on `atrium:events:{code}` and,
   when the change affects public-room listing state, a `HomeEvent` on
   `atrium:events:home`.
4. Every Spring instance — including ones the client isn't connected to — has the
   `ReactiveRedisMessageListenerContainer` subscribed; each instance forwards the
   event to every WebSocket session it holds for that room (or the home-page
   WebSocket in the case of `HomeEvent`).

## 6. Restart resilience

| State                      | Where it lives             | Survives Spring restart? |
|----------------------------|----------------------------|--------------------------|
| Rooms                      | Redis (`atrium:room:*`)    | Yes                      |
| Players                    | Redis (`atrium:player:*`)  | Yes                      |
| Room / player indexes      | Redis (sorted sets)        | Yes                      |
| WebSocket sessions         | In-memory on each instance | No — clients reconnect   |
| Disconnect grace timers    | *(none in MVP)*            | N/A                      |
| Public id ↔ Redis identity | Browser cookies + Redis    | Yes                      |

## 7. Multi-instance behaviour

The lobby is **horizontally scalable**: any instance can serve any request, because

- every read/write goes through Redis,
- every cross-client notification fans out through Redis pub/sub, not through
  in-memory channels.

The design avoids instance-local timer state for membership transitions. If a player's
WebSocket drops, they are marked disconnected but remain in the room until an explicit
leave/kick/delete action or inactive-room cleanup removes the room.

## 8. Inactivity cleanup

`LobbyCleanupService` runs on a fixed delay (5 minutes) and uses one shared threshold:
`atrium.core.cleanup-inactive-seconds` (default: 259200 = 3 days).

Each sweep performs:

1. Delete rooms whose `lastActivityAt` is older than the threshold.
2. For each room being deleted, delete players in that room whose `lastActiveAt` is
   also older than the threshold.
3. Delete roomless players whose `lastActiveAt` is older than the threshold.

No disconnect-grace auto-leave timer is performed.

A known limitation: the `PlayerView.joinedAt` field uses the room's creation time
as a fallback because per-player join timestamps are not tracked in the current
domain model (see [`LOBBY.md §7`](./LOBBY.md#7-playerview-joinedat-limitation)).

## 9. Extension points for downstream projects

To embed Atrium Core's lobby in another Spring Boot game project:

1. Add this module as a Gradle dependency.
2. Subclass `GameSettings` with your game-specific configuration and register the
   subtype via a Jackson `Module` bean:

   ```java
   @Bean
   public Module myGameSettingsModule() {
       SimpleModule module = new SimpleModule("MyGameSettingsModule");
       module.registerSubtypes(MyGameSettings.class);
       return module;
   }
   ```

3. Mount the game's own controllers / WebSocket handlers at non-`/api/atrium` paths.
   The lobby will broadcast `RoomEvent.StateChanged(IN_GAME)` when the host starts a
   game — that's the cue for game-specific code to take over.

Optional: annotate your host app with
`org.atrium.core.extension.EnableAtrium` when you want explicit, annotation-driven
import of lobby auto-configuration (functionally equivalent to Boot auto-discovery).

The lobby never touches game logic directly; the boundary is the `RoomState`
transition + the polymorphic `GameSettings`.

