# HTTP & WebSocket API

> Generated from `org.atrium.core.api.controller.AtriumController`,
> `org.atrium.core.websocket.RoomWebSocketHandler`, and
> `org.atrium.core.websocket.HomeWebSocketHandler`. When the code changes, update
> this document in the same PR.

Base path: **`/api/atrium`**. All write endpoints expect a JSON body containing the
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

### POST `/api/atrium/status`

Identity bootstrap. Uses the caller's `AuthenticatedRequest` shape — the
server returns the existing identity or mints a fresh one and returns
`StatusResponse`. The client must persist the returned `publicId` and
`secretId` as cookies.

**Request** — `AuthenticatedRequest`

```json
{
	"publicId": "uuid-or-null",
	"secretId": "uuid-or-null"
}
```

**Response 200** — `StatusResponse`

```json
{
	"publicId": "...",
	"secretId": "...",
	"name": "Alice",
	"avatar": "mdi:cat",
	"freshIdentity": false,
	"activeRooms": [
		/* RoomView, ... */
	]
}
```

### POST `/api/atrium/profile`

Update the caller's display name and avatar. Allowed in or out of a room. If the
player is in a room, the new profile is broadcast as a `playerUpdated` event.

**Request**

```json
{
	"publicId": "...",
	"secretId": "...",
	"name": "Bob",
	"avatar": "mdi:dog"
}
```

**Response 200** — no body.

### GET `/api/atrium/rooms?limit=50`

List public rooms (most-recently-active first). The `limit` parameter is clamped to `[1, 200]`.

**Response 200**

```json
[
	{
		/* RoomView */
	},
	...
]
```

### GET `/api/atrium/rooms/{code}`

Get a single room's `RoomView`. Returns 404 if the room doesn't exist (the
frontend should redirect to the home page).

### POST `/api/atrium/rooms`

Create a new room. The caller is automatically the host.

**Request** — `CreateRoomRequest`

```json
{
	"publicId": "...",
	"secretId": "...",
	"name": "My Room",
	// optional display name
	"minPlayers": 2,
	// optional
	"maxPlayers": 8,
	// optional
	"gameSettings": {
		"type": "default"
	},
	// optional
	"isPrivate": false
}
```

`name`, `minPlayers`, `maxPlayers`, and `gameSettings` are all
optional (defaults from `AtriumProperties` and/or `GameSettings` absolute
bounds). The server validates that the values fall within the game's absolute bounds;
requests outside the allowed range receive a `400 Bad Request`.

**Response 201** — `RoomView`.

**Errors**

- `400` minPlayers or maxPlayers outside the absolute bounds defined by the game settings.
- `409` room code collision during creation.

### POST `/api/atrium/rooms/{code}/join`

Join an existing room.

**Request** — `AuthenticatedRequest`

```json
{
	"publicId": "...",
	"secretId": "..."
}
```

**Response 200** — `RoomView`.

**Errors**

- `404` room doesn't exist.
- `403` room is in `IN_GAME` (spectate via WebSocket instead) or is full.

### POST `/api/atrium/rooms/{code}/leave`

Leave a room voluntarily.

**Request** — `AuthenticatedRequest`

```json
{
	"publicId": "...",
	"secretId": "..."
}
```

**Response 200** — no body. If the leaver was the host, the longest-joined
remaining player is promoted (`hostChanged` event). If the room becomes empty,
it's deleted (`roomDeleted` event).

### POST `/api/atrium/rooms/{code}/kick`

Host-only.

**Request** — `KickPlayerRequest`

```json
{
	"publicId": "host-...",
	"secretId": "host-...",
	"targetPublicId": "victim-..."
}
```

**Response 200** — no body.

### DELETE `/api/atrium/rooms/{code}`

Host-only. Deletes the room outright (broadcasts `roomDeleted` to every member,
removes the room code from their indexes).

**Request body** (yes, DELETE-with-body — secrets stay out of access logs) — `AuthenticatedRequest`

```json
{
	"publicId": "host-...",
	"secretId": "host-..."
}
```

### PATCH `/api/atrium/rooms/{code}/settings`

Host-only. Only allowed in `LOBBY` state. Any field left out is unchanged.

**Request**

```json
{
	"publicId": "host-...",
	"secretId": "host-...",
	"minPlayers": 2,
	"maxPlayers": 12,
	"gameSettings": {
		"type": "myGame",
		"...": "..."
	},
	"isPrivate": true
}
```

**Response 200** — updated `RoomView`. A `settingsChanged` event is broadcast.

### POST `/api/atrium/rooms/{code}/start`

Host-only. Transitions `LOBBY → IN_GAME`. Validates that the current member count
meets at least `minPlayers`. A `stateChanged` event is broadcast —
game-specific code listens for this to spin up the actual game state.

**Request** — `AuthenticatedRequest`

```json
{
	"publicId": "host-...",
	"secretId": "host-..."
}
```

### POST `/api/atrium/rooms/{code}/stop`

Host-only. Transitions `IN_GAME → LOBBY`, keeping the player roster intact.

**Request** — `AuthenticatedRequest`

```json
{
	"publicId": "host-...",
	"secretId": "host-..."
}
```

## WebSocket

### Home stream

Mount: **`/api/atrium/ws/home?limit=50`**

Public stream for home-screen room listings. No auth query params required.

On connect, the server sends one `snapshot` frame containing up to `limit` public
rooms (`limit` is clamped to `[1, 200]`, default `50`), then forwards every
subsequent `HomeEvent` from `atrium:events:home`.

| `type`        | Extra fields        | When emitted                                        |
|---------------|---------------------|-----------------------------------------------------|
| `snapshot`    | `rooms: RoomView[]` | First frame after subscribing.                      |
| `roomCreated` | `room: RoomView`    | A new public room is created (or private → public). |
| `roomUpdated` | `room: RoomView`    | Public room metadata/member/status/state changed.   |
| `roomDeleted` | `roomCode`          | Public room deleted (or public → private).          |

See [`HomeWebSocketHandler`](../backend/src/main/java/org/atrium/core/websocket/HomeWebSocketHandler.java) and
[`HomeEvent`](../backend/src/main/java/org/atrium/core/domain/event/HomeEvent.java) for the implementation.

Clients should apply deltas optimistically and still do occasional REST
reconciliation (`GET /api/atrium/rooms`) after reconnects.

### Room stream

Mount: **`/api/atrium/ws/{code}?publicId=...&secretId=...`**

Mass-broadcast channel for room events. Connect after `POST /api/atrium/status`
when entering a room page. The handler:

1. Authenticates the `(publicId, secretId)` pair.
2. Marks the player `ACTIVE` (broadcasts `playerReconnected` if they had been
   `DISCONNECTED`).
3. Sends one `snapshot` frame containing the full `RoomView`.
4. Forwards every subsequent `RoomEvent` from `atrium:events:{code}` as a JSON
   text frame.

On disconnect: members are marked `DISCONNECTED` and `playerDisconnected` is
broadcast. No delayed auto-leave is scheduled.

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
| `playerLeft`         | `publicId`, `reason?: string`    | Voluntary leave, explicit kick/delete flow side effects.  |
| `playerKicked`       | `publicId`                       | Host kicked a member.                                     |
| `playerUpdated`      | `player: PlayerView`             | Name/avatar change inside the room.                       |
| `playerDisconnected` | `publicId`                       | Room member's WebSocket dropped.                          |
| `playerReconnected`  | `publicId`                       | Previously disconnected member reconnected.               |
| `hostChanged`        | `newHost`                        | Host left and the longest-joined was promoted.            |
| `settingsChanged`    | `room: RoomView`                 | Host changed `maxPlayers` / `gameSettings` / `isPrivate`. |
| `stateChanged`       | `newState: "LOBBY" \| "IN_GAME"` | Host started or stopped the game.                         |
| `roomDeleted`        | *(none)*                         | Host deleted, or room emptied, or TTL'd out.              |

**Member vs. spectator**: If the WebSocket client is a current member of the room,
their status is set to `ACTIVE` on connect and `DISCONNECTED` on close. Spectators
(clients whose `publicId` is not in the room's player list) receive the same event
stream but do not affect connection status.

No delayed auto-leave is scheduled after disconnect — players remain in the room
until an explicit leave/kick/delete action or the inactivity cleanup removes them.

### Reference DTOs

```ts
interface PlayerView {
	publicId: string;
	name: string;
	avatar: string;
	status: "ACTIVE" | "DISCONNECTED";
	joinedAt: string;          // ISO-8601
}

interface RoomView {
	code: string;    // 6-char [A-Z0-9]
	name: string | null;   // optional display name
	host: string;    // PlayerView.publicId
	players: PlayerView[];
	minPlayers: number;
	maxPlayers: number;
	gameSettings: { type: string; /* ...game-specific fields... */ };
	isPrivate: boolean;
	state: "LOBBY" | "IN_GAME";
	createdAt: string;    // ISO-8601
	lastActivityAt: string;    // ISO-8601
}
```
