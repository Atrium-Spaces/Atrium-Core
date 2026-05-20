# Lobby protocol

> The end-to-end flow of "open the site, get an identity, join a room, play, leave".
> Read [`ARCHITECTURE.md`](./ARCHITECTURE.md) first for the data model and
> [`API.md`](./API.md) for the per-endpoint contract.

## 1. Identity

The website stores four cookies:

| Cookie     | Purpose                                                 |
|------------|---------------------------------------------------------|
| `publicId` | UUID shown to other players in room rosters / events.   |
| `secretId` | UUID proving "this is me" to the server. Never exposed. |
| `name`     | Display name (cached locally to render before status).  |
| `avatar`   | Avatar string (URL / Iconify id / emoji).               |

On every page load the client `POST /api/atrium/status` with whatever it has. The
server either confirms the `(publicId, secretId)` pair and returns the existing
player record, or — if either id is missing or the pair doesn't match — mints a
**fresh** identity and returns `freshIdentity: true`. The client must overwrite its
cookies with whatever the server returned.

Identity is **decoupled** from the lobby: a brand-new player exists in Redis the
moment they hit `/status`. They're roomless (`roomCode = null`) until they
explicitly join one. Roomless players inactive beyond the cleanup threshold are
pruned by the scheduled sweep.

## 2. Home page

Two tabs:

- **Join** — text box for `name` + `avatar` (writes back to cookies and `POST
  /profile`), and a text box for a room code with a "Join" button (`POST
  /rooms/{code}/join`). A "Create room" button calls `POST /rooms`.
- **Browse** — `GET /rooms` for the public-room list. Each row shows current
  player count, game kind (`gameSettings.gameKind`), state. Buttons:
  - `LOBBY` rooms: "Join" (if you're not already in a room).
  - `IN_GAME` rooms: "Spectate" (open the room page; subscribe to WebSocket
    fan-out without calling `/join`).

## 3. Room page (`/room/{code}`)

On mount: `GET /rooms/{code}`. If 404 → redirect home (per spec).

Otherwise, open a WebSocket to `/api/atrium/ws/{code}?publicId=...&secretId=...`. The
first frame is a `snapshot` event giving the full `RoomView`; subsequent frames
are deltas.

### 3.1 Lobby-state UI

| Element                         | Visible to | Editable by           |
|---------------------------------|------------|-----------------------|
| Player list                     | everyone   | —                     |
| Kick button next to each player | host       | host                  |
| Max players                     | everyone   | host (PATCH settings) |
| Game settings                   | everyone   | host (PATCH settings) |
| Start game button               | host       | host (POST start)     |
| Leave game button               | members    | self                  |
| Delete game button              | host       | host                  |

Spectators (URL visitors who aren't members) see everything read-only.

### 3.2 In-game UI

The lobby UI yields to the **downstream project's** game UI. The library only
provides:

- A `stateChanged` event the game UI listens for to mount itself.
- The `Stop game` button (host-only) which calls `POST /rooms/{code}/stop` and
  returns to the lobby view with the same roster.
- The `Leave` button (members) — leaving an in-game room behaves the same as
  leaving a lobby (host re-elected if needed, room deleted if empty).

## 4. Host transitions

- **Host leaves voluntarily** → longest-joined remaining player becomes host
  (`hostChanged` event).
- **Host's WebSocket drops** → `playerDisconnected` event but host stays host;
  there is no delayed auto-leave in MVP.
- **Host explicitly deletes** → `roomDeleted` event; all members redirected home.

## 5. Disconnect handling

When a room member's WebSocket disconnects, the server marks them
`DISCONNECTED` and broadcasts `playerDisconnected`. Reconnecting marks them
`ACTIVE` and broadcasts `playerReconnected`.

No scheduled auto-leave runs after disconnect in MVP.

## 6. Polymorphic game settings

`GameSettings` is the extension hook. The library ships
`DefaultGameSettings` (`{"type": "default"}`) so it can run standalone. Downstream
projects subclass it:

```java
@JsonTypeName("chess")
public final class ChessSettings extends GameSettings {
    private final int initialClockSeconds;
    private final int incrementSeconds;

	@JsonCreator
    public ChessSettings(@JsonProperty("initialClockSeconds") int initialClockSeconds, @JsonProperty("incrementSeconds")   int incrementSeconds) {
        this.initialClockSeconds = initialClockSeconds;
        this.incrementSeconds = incrementSeconds;
    }

    @Override public String gameKind() { return "chess"; }
    // getters ...
}
```

...and register the subtype with a Jackson `Module` bean (see
[`ARCHITECTURE.md` §8](./ARCHITECTURE.md#8-extension-points-for-downstream-projects)).

The frontend round-trips the JSON opaquely — it doesn't need to know any concrete
type at the wire level.

## 7. PlayerView joinedAt limitation

The `joinedAt` field in `PlayerView` is populated with the room's `createdAt`
timestamp as a fallback. The domain model does not yet track per-player join times,
so all members of a room share the same visible join time in the current
implementation. Downstream consumers should treat `joinedAt` as an approximate
indicator only.

## 8. The active-index repair scan

Players carry a `roomCode` field as a fast lookup hint. If it ever disagrees with
the canonical room rosters (because Redis was partially restored, or a race in a
previous bug, or someone tampered with keys), `PlayerService.resolveRoom` scans
every room in the index and rewrites the player's `roomCode` to match the one
room that actually contains them — or clears it if none do. Triggered only on
suspicion (cached room doesn't exist, or exists without this player), so it's
zero-cost in the common case.

