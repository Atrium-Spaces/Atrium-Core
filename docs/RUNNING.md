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

| Variable                        | Default     | Used in           |
|---------------------------------|-------------|-------------------|
| `SERVER_PORT`                   | `8080`      | Spring HTTP port  |
| `SPRING_DATA_REDIS_HOST`        | `localhost` | Spring Data Redis |
| `SPRING_DATA_REDIS_PORT`        | `6379`      | Spring Data Redis |
| `SPRING_DATA_REDIS_PASSWORD`    | *(empty)*   | Spring Data Redis |
| `SPRING_DATA_REDIS_SSL_ENABLED` | `false`     | Spring Data Redis |

## Configuration properties

Everything tunable about the lobby is under `atrium.core.*` in `application.yml`
or as `--atrium.core....` command-line arguments. See
[`docs/ARCHITECTURE.md` §8](./ARCHITECTURE.md#8-inactivity-cleanup) for cleanup behaviour.

| Property                   | Default          | Meaning                                                     |
|----------------------------|------------------|-------------------------------------------------------------|
| `room-code-length`         | `6`              | Room-code character count.                                  |
| `default-min-players`      | `2`              | Default floor for new rooms.                                |
| `absolute-min-players`     | `1`              | Hard minimum regardless of host request.                    |
| `default-max-players`      | `8`              | Default cap for new rooms.                                  |
| `absolute-max-players`     | `32`             | Hard ceiling regardless of host request.                    |
| `max-name-length`          | `32`             | Player display-name cap.                                    |
| `max-avatar-length`        | `256`            | Avatar string cap (URL / icon id).                          |
| `max-room-name-length`     | `64`             | Hard cap on room display name, in characters.               |
| `cleanup-inactive-seconds` | `259200`         | Inactivity threshold (seconds) for stale room/player sweep. |
| `websocket-path`           | `/api/atrium/ws` | WebSocket mount point.                                      |
| `cors-allowed-origins`     | `["*"]`          | CORS whitelist for the lobby endpoints.                     |

The backend fails fast during startup if `absolute-min-players` is below `1` or if
`absolute-max-players` is below `absolute-min-players`.

### Example — production overrides

```yaml
atrium:
  core:
    cors-allowed-origins:
      - "https://play.example.com"
```

## CLI

`bootRun` accepts standard Spring Boot CLI arguments:

```bash
./gradlew bootRun --args='--server.port=9090 --logging.level.org.atrium=DEBUG'
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
it — see [`docs/ARCHITECTURE.md` §9](./ARCHITECTURE.md#9-extension-points-for-downstream-projects).

