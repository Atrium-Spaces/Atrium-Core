# Build

## Prerequisites

| Tool    | Version                 | Notes                                             |
|---------|-------------------------|---------------------------------------------------|
| JDK     | 21                      | Gradle's toolchain will auto-provision if absent. |
| Gradle  | bundled via `./gradlew` | Don't install separately.                         |
| Node.js | 22 LTS                  | For the Angular frontend.                         |
| npm     | bundled with Node.js    |                                                   |
| Redis   | 7.x (Docker is fine)    | Backend won't start without it.                   |

## Backend

From `backend/`:

```bash
# Compile only
./gradlew compileJava

# Full build (compile + test + jar)
./gradlew build

# Run the reference host application
./gradlew bootRun
```

The boot jar lands in `backend/build/libs/Atrium-Core-<version>.jar`. Run it with

```bash
java -jar build/libs/Atrium-Core-1.0.0.jar
```

### Spinning up Redis

Quickest path is Docker:

```bash
docker run --rm -p 6379:6379 --name atrium-redis redis:7-alpine
```

Defaults assume `localhost:6379` with no password; override with environment
variables (see [`RUNNING.md`](./RUNNING.md)).

## Frontend

From `frontend/`:

```bash
npm install         # first time only
npm start           # dev server on http://localhost:4200
npm run build       # production bundle into dist/
npm run lint        # ESLint flat-config
npm test            # Karma + Jasmine
```

## Both at once

Open two terminals — backend on 8080, frontend on 4200 with its `proxy.conf` (TBD)
forwarding `/api/*` and `/api/atrium/ws/*` to the backend.

## Reproducible archives

Tasks of type `AbstractArchiveTask` have `preserveFileTimestamps = false` and
`reproducibleFileOrder = true` (configured in `build.gradle.kts`) so the produced
jars are byte-stable across machines.

