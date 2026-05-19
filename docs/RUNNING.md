# Running

## Quick start

```bash
# Terminal 1 — Redis
docker run --rm -p 6379:6379 redis:7-alpine

# Terminal 2 — backend
cd backend && ./gradlew bootRun

# Terminal 3 — frontend
cd frontend && npm start
```

Browse to <http://localhost:4200>.

## Environment variables

| Variable         | Default                | Used in                                |
|------------------|------------------------|----------------------------------------|
| `SERVER_PORT`    | `8080`                 | Spring HTTP port                       |
| `REDIS_HOST`     | `localhost`            | Spring Data Redis                      |
| `REDIS_PORT`     | `6379`                 | Spring Data Redis                      |
| `REDIS_PASSWORD` | *(empty)*              | Spring Data Redis                      |

## Configuration properties

Everything tunable about the lobby is under `atrium.core.*` in `application.yml`
or as `--atrium.core.…` command-line arguments. See
[`docs/ARCHITECTURE.md` §7](./ARCHITECTURE.md#7-inactivity-ttls) for the full list.

| Property                          | Default         | Meaning                                  |
|-----------------------------------|-----------------|------------------------------------------|
| `room-code-length`                | `6`             | Room-code character count.               |
| `default-max-players`             | `8`             | Default cap for new rooms.               |
| `absolute-max-players`            | `32`            | Hard ceiling regardless of host request. |
| `max-name-length`                 | `32`            | Player display-name cap.                 |
| `max-avatar-length`               | `256`           | Avatar string cap (URL / icon id).       |
| `disconnect-grace-period-seconds` | `60`            | WebSocket drop → official leave timeout. |
| `lobby-inactive-ttl-seconds`      | `7200`          | Lobby-state room TTL.                    |
| `in-game-inactive-ttl-seconds`    | `259200`        | In-game room TTL (3 days).               |
| `roomless-player-ttl-seconds`     | `7200`          | Roomless inactive player TTL.            |
| `cleanup-interval-seconds`        | `300`           | Cleanup sweep cadence.                   |
| `websocket-path`                  | `/api/atrium/ws` | WebSocket mount point.                  |
| `cors-allowed-origins`            | `["*"]`         | CORS whitelist for the lobby endpoints.  |

### Example — production overrides

```yaml
atrium:
  core:
    cors-allowed-origins:
      - "https://play.example.com"
    disconnect-grace-period-seconds: 30
    lobby-inactive-ttl-seconds: 3600
```

## CLI

`bootRun` accepts standard Spring Boot CLI arguments:

```bash
./gradlew bootRun --args='--server.port=9090 --atrium.core.disconnect-grace-period-seconds=10'
```

## Logging

Default level: `INFO` for `org.atrium`, framework defaults elsewhere. Crank lobby
debug logging with

```bash
--logging.level.org.atrium=DEBUG
```

## Running as a library inside another project

```kotlin
// build.gradle.kts (downstream project)
dependencies {
    implementation("org.atrium:atrium-core:1.0.0")
}
```

Then in `application.yml`:

```yaml
atrium:
  core:
    websocket-path: /api/atrium/ws      # or any path you want
spring:
  data:
    redis:
      host: redis.your-cluster.internal
```

Provide your own `GameSettings` subtype and a Jackson `Module` bean that registers
it — see [`docs/ARCHITECTURE.md` §8](./ARCHITECTURE.md#8-extension-points-for-downstream-projects).

