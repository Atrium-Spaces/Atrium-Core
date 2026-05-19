# HTTP & WebSocket API

> Generated from `org.atrium.core.api.controller.LobbyController` and
> `org.atrium.core.websocket.RoomWebSocketHandler`. When the code changes, update
> this document in the same PR.

Base path: **`/api/lobby`**. All write endpoints expect a JSON body containing the
caller's `publicId` and `secretId`; the secret id never appears in URLs or headers.

Errors are returned as

```json
{
  "timestamp": "2026-05-18T12:34:56.789Z",
  "status": 404,
  "error": "Not Found",
  "message": "Room not found: ABCDEF"
}
```

## REST

### POST `/api/lobby/status`

Identity bootstrap. Returns existing or freshly minted identity, and the room (if
any) the player is currently in.

**Request**

```json
{
  "publicId":  "uuid-or-null",
  "secretId":  "uuid-or-null",
  "name":      "Alice (optional)",
  "avatar":    "mdi:cat (optional)"
}
```

**Response 200**

```json
{
  "publicId":      "…",
  "secretId":      "…",
  "name":          "Alice",
  "avatar":        "mdi:cat",
  "freshIdentity": false,
  "activeRoom":    {/* RoomView, or null */}
}
```

When `freshIdentity` is `true`, the client **must** persist `publicId` and
`secretId` to cookies — these are the new identity.

### POST `/api/lobby/profile`

Update the caller's display name and avatar. Allowed in or out of a room. If the
player is in a room, the new profile is broadcast as a `playerUpdated` event.

**Request**

```json
{ "publicId": "…", "secretId": "…", "name": "Bob", "avatar": "mdi:dog" }
```

**Response 200** — no body.

### GET `/api/lobby/rooms?limit=50`

List public rooms (most-recently-active first).

**Response 200**

```json
{ "rooms": [ { /* RoomView */ }, … ] }
```

### GET `/api/lobby/rooms/{code}`

Get a single room's `RoomView`. Returns 404 if the room doesn't exist (the
frontend should redirect to the home page).

### POST `/api/lobby/rooms`

Create a new room. The caller is automatically the host.

**Request**

```json
{
  "publicId":     "…",
  "secretId":     "…",
  "maxPlayers":   8,
  "gameSettings": { "type": "default" },
  "isPrivate":    false
}
```

`maxPlayers` and `gameSettings` are optional (defaults from `LobbyProperties`).

**Response 201** — `RoomView`.

### POST `/api/lobby/rooms/{code}/join`

Join an existing room.

**Request**

```json
{ "publicId": "…", "secretId": "…" }
```

**Response 200** — `RoomView`.

**Errors**

- `404` room doesn't exist.
- `403` room is in `IN_GAME` (spectate via WebSocket instead) or is full.
- `409` player is already in a different room.

### POST `/api/lobby/rooms/{code}/leave`

Leave a room voluntarily.

**Request**

```json
{ "publicId": "…", "secretId": "…" }
```

**Response 200** — no body. If the leaver was the host, the longest-joined
remaining player is promoted (`hostChanged` event). If the room becomes empty,
it's deleted (`roomDeleted` event).

### POST `/api/lobby/rooms/{code}/kick`

Host-only.

**Request**

```json
{ "publicId": "host-…", "secretId": "host-…", "targetPublicId": "victim-…" }
```

**Response 200** — no body.

### DELETE `/api/lobby/rooms/{code}`

Host-only. Deletes the room outright (broadcasts `roomDeleted` to every member,
clears their `roomCode` indexes).

**Request body** *(yes, DELETE-with-body — secrets stay out of access logs)*

```json
{ "publicId": "host-…", "secretId": "host-…" }
```

### PATCH `/api/lobby/rooms/{code}/settings`

Host-only. Only allowed in `LOBBY` state. Any field left out is unchanged.

**Request**

```json
{
  "publicId":     "host-…",
  "secretId":     "host-…",
  "maxPlayers":   12,
  "gameSettings": { "type": "myGame", "…": "…" },
  "isPrivate":    true
}
```

**Response 200** — updated `RoomView`. A `settingsChanged` event is broadcast.

### POST `/api/lobby/rooms/{code}/start`

Host-only. Transitions `LOBBY → IN_GAME`. A `stateChanged` event is broadcast —
game-specific code listens for this to spin up the actual game state.

### POST `/api/lobby/rooms/{code}/stop`

Host-only. Transitions `IN_GAME → LOBBY`, keeping the player roster intact.

## WebSocket

Mount: **`/api/lobby/ws/{code}?publicId=…&secretId=…`**

Mass-broadcast channel for room events. Connect after `POST /api/lobby/status`
when entering a room page. The handler:

1. Authenticates the `(publicId, secretId)` pair.
2. Cancels any pending disconnect grace timer for this player.
3. Marks the player `ACTIVE` (broadcasts `playerReconnected` if they had been
   `DISCONNECTED`).
4. Sends one `snapshot` frame containing the full `RoomView`.
5. Forwards every subsequent `RoomEvent` from `lobby:events:{code}` as a JSON
   text frame.

On disconnect: marks the player `DISCONNECTED`, broadcasts `playerDisconnected`,
and schedules `performLeave` to run after the grace period. A reconnect inside
the window cancels the leave.

You **don't have to be a member** of the room to subscribe — non-members
receive the same fan-out (spectator mode). The frontend uses this for the
public-room browser preview and for spectators in `IN_GAME` rooms.

### Event shapes

All events are JSON objects with a `type` discriminator and a common
`roomCode` + `emittedAt` envelope:

| `type`               | Extra fields                     | When emitted                                              |
|----------------------|----------------------------------|-----------------------------------------------------------|
| `snapshot`           | `room: RoomView`                 | First frame after subscribing.                            |
| `playerJoined`       | `player: PlayerView`             | A new player joined.                                      |
| `playerLeft`         | `publicId`, `reason?: string`    | Voluntary leave or disconnect grace expired.              |
| `playerKicked`       | `publicId`                       | Host kicked a member.                                     |
| `playerUpdated`      | `player: PlayerView`             | Name/avatar change inside the room.                       |
| `playerDisconnected` | `publicId`                       | WebSocket dropped; grace timer started.                   |
| `playerReconnected`  | `publicId`                       | WebSocket re-established inside grace.                    |
| `hostChanged`        | `newHost`                        | Host left and the longest-joined was promoted.            |
| `settingsChanged`    | `room: RoomView`                 | Host changed `maxPlayers` / `gameSettings` / `isPrivate`. |
| `stateChanged`       | `newState: "LOBBY" \| "IN_GAME"` | Host started or stopped the game.                         |
| `roomDeleted`        | *(none)*                         | Host deleted, or room emptied, or TTL'd out.              |

### Reference DTOs

```ts
interface PlayerView {
  publicId: string;
  name:     string;
  avatar:   string;
  status:   "ACTIVE" | "DISCONNECTED";
  joinedAt: string;          // ISO-8601
}

interface RoomView {
  code:           string;    // 6-char [A-Z0-9]
  host:           string;    // PlayerView.publicId
  players:        PlayerView[];
  maxPlayers:     number;
  gameSettings:   { type: string; /* …game-specific fields… */ };
  isPrivate:      boolean;
  state:          "LOBBY" | "IN_GAME";
  createdAt:      string;
  lastActivityAt: string;
}
```
