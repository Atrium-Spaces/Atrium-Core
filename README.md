# Atrium Core

**Atrium Core** is a reusable lobby / room-management system for online board games.
It's split into a Spring Boot backend library (this repo's `backend/`) and an
Angular frontend with a matching lobby UI library (this repo's `frontend/`).

Downstream projects depending on Atrium Core get:

- a complete reactive REST + WebSocket API for room lifecycle (create, join, kick,
  leave, delete, start / stop game) and player identity (cookies-driven, two-UUID
  scheme);
- Redis-backed state and pub/sub fan-out across instances;
- a polymorphic `GameSettings` extension point so the host project can plug its
  own game-specific configuration in via Jackson `@JsonTypeInfo`;
- SPI hooks (`GameLifecycleListener`) and optional `@EnableLobbySystem` annotation
  for explicit starter-style integration.

The library never touches game logic itself. A downstream project adds the lobby
as a dependency, registers its `GameSettings` subtype, and listens for the
`stateChanged → IN_GAME` event to take over.

## Tech stack

| Layer     | Backend                                                   | Frontend                                  |
|-----------|-----------------------------------------------------------|-------------------------------------------|
| Build     | Gradle 9 (Kotlin DSL)                                     | Angular CLI 21                            |
| Language  | Java 21                                                   | TypeScript / SCSS / HTML                  |
| Runtime   | Spring Boot 3.x, WebFlux                                  | Angular 21 standalone components, signals |
| State     | Redis (Spring Data Redis Reactive)                        | Cookies + Angular service state           |
| Messaging | Redis Pub/Sub via `ReactiveRedisMessageListenerContainer` | WebSocket client                          |
| Helpers   | Lombok, fastutil, JSpecify                                | PrimeNG, Transloco (i18n), Iconify        |
| Lint      | `javac -Xlint:all`                                        | ESLint flat config (angular-eslint)       |

## Documentation

- Build and development: [`docs/BUILD.md`](docs/BUILD.md)
- Running and configuration: [`docs/RUNNING.md`](docs/RUNNING.md)
- HTTP & WebSocket API: [`docs/API.md`](docs/API.md)
- Architecture and internals: [`docs/ARCHITECTURE.md`](docs/ARCHITECTURE.md)
- Lobby protocol end-to-end: [`docs/LOBBY.md`](docs/LOBBY.md)
- Code conventions: [`docs/CODE_STYLES.md`](docs/CODE_STYLES.md)

## Project layout

```
backend/   Spring Boot + Reactive Redis lobby library (+ reference host app)
frontend/  Angular app + lobby UI library (work in progress)
docs/      Architecture, API, protocol, conventions
```

See [`docs/ARCHITECTURE.md`](docs/ARCHITECTURE.md) for the full directory map.
