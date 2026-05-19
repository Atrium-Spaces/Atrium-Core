# Code Styles

> Conventions enforced across the Atrium Core codebase. New rules land here as they're agreed.

These rules apply to **both** the Spring Boot backend and the Angular / TypeScript /
SCSS / HTML frontend unless otherwise noted. Anything not listed here defers to the
project's linter / formatter (Angular ESLint flat config; Java compiler warnings).

## 1. Naming

### 1.1 Package / folder names are singular

- ✅ `core/service/`, `core/model/`, `core/utility/`, `core/constant/`, `app/component/<name>/`
- ❌ `services/`, `models/`, `utilities/`, `features/`

Backend Java packages follow the same rule (`core.domain.model`, `core.domain.service`,
`core.redis.repository`, `core.api.controller`, `core.websocket`, `core.autoconfigure`).

### 1.2 No abbreviations in identifiers

Package names, class / interface names, method names, variable names, parameter names, and
field names must spell out their meaning in full.

- ✅ `recipes.forEach((recipe) => { … })`
- ❌ `recipes.forEach((r) => { … })`
- ✅ `for (const ingredient of ingredients) { … }`
- ❌ `for (const ing of ingredients) { … }`

**Allowed exceptions:**

| Context           | Allowed                                 | Example                                       |
|-------------------|-----------------------------------------|-----------------------------------------------|
| Caught exceptions | `e`, `ex`                               | `} catch (Exception e) { … }`                 |
| Transloco let     | `t`                                     | `*transloco="let t"`                          |
| Loop indices      | `i`, `j`, `k`                           | `for (int i = 0; i < length; i++) { … }`      |
| Common idioms     | `id`, `lat`, `lon`, `min`, `max`, `dto` | These are de-facto whole words in our domain. |
| Library type echo | match the imported class                | `FileRef fileRef = new FileRef();`            |

The "library type echo" rule: when a local needs to hold a value of an imported type, prefer
the type's own name in lowerCamelCase as the variable name (`Path path`, `WebClient webClient`,
`GtfsReader gtfsReader`, `BoundingBox boundingBox`). It removes a class of bikeshedding and
makes refactor-rename across the file trivial.

### 1.3 Casing

- **TypeScript / Java classes / records**: `PascalCase`.
- **TypeScript / Java methods, fields, locals, parameters**: `camelCase`.
- **TypeScript constants exported from `*.constants.ts`**: `SCREAMING_SNAKE_CASE`.
- **Java constants (`static final`)**: `SCREAMING_SNAKE_CASE`.
- **Redis key prefixes** (string literals): `lower:colon:separated` (e.g. `atrium:room:ABCDEF`).
- **Room codes**: 6-character `UPPER_CASE_ALPHANUMERIC`.
- **CSS / SCSS class names**: `kebab-case`, prefixed `app-` for app-local utility classes.
- **Transloco i18n keys**: `camelCase`. Same no-abbreviation rule as identifiers
  (`lobby.player.kickButton`, **not** `lobby.player.kickBtn`).

### 1.4 British English in prose, American English in identifiers

- All Markdown docs (`README.md`, `docs/*.md`) and all source-code comments use **British
  English**: `colour`, `behaviour`, `centre`, `localise`, `organise`, `analyse`.
- All code identifiers — class names, method names, variable names, signal names, i18n keys,
  JSON content `id` fields, CSS class names — use **American English**: `color`, `behavior`,
  `center`, `localize`, `organize`, `analyze`.

The split keeps player-facing copy consistent with Hong Kong / UK readers, while
keeping API and code identifiers on the de-facto American convention every framework
and library already uses (`localStorage`, `Color`, `behavior` events, …). When in
doubt: prose-British, code-American.

## 2. Formatting

### 2.1 Indentation

- **Tabs**, not spaces. Both Java and TypeScript / HTML / SCSS / JSON.
- One tab per nesting level.
- **Exception — `application.yml`**: YAML forbids tabs at indentation. Use **2 spaces** per
  level there. This is the only file in the repo that uses spaces.

### 2.2 No mid-expression line wrapping

Lines stay on a single line. Don't pre-emptively wrap arguments or method bodies.

**Allowed exceptions** — chains and many-argument calls.

#### Chained streams / pipes

```ts
this.httpClient.get<Building[]>("/api/game/buildings")
	.pipe(
		tap((list) => this.buildings.set(list)),
		map((list) => list.length),
	)
	.subscribe();
```

```java
return Mono.fromCallable(() -> registry.findRecipe(recipeId))
	.subscribeOn(Schedulers.boundedElastic())
	.map(ResponseEntity::ok)
	.defaultIfEmpty(ResponseEntity.notFound().build());
```

Each chained call gets its own line, indented one tab beyond the receiver.

#### Method calls with many parameters

```ts
this.gameService.placeBuilding(
	kind,
	latitude,
	longitude,
	recipeId,
);
```

```java
return new BuildingDto(
	building.id(),
	building.kind().name(),
	building.lat(),
	building.lon(),
	building.recipeId(),
	building.outputIngredientId(),
	operations
);
```

The opening paren is on the call line; arguments are indented one tab; the closing paren
sits at the original indent level on its own line.

### 2.3 Trailing newline

Every file ends with **exactly one** trailing newline (one blank line at EOF, no more).

### 2.4 String quotes

- **TypeScript**: double quotes `"…"` for ordinary strings; backticks for template literals
  only when interpolating.
- **Java**: standard `"…"` — text blocks (`"""…"""`) for multi-line SQL / JSON / GraphQL.
- **HTML attributes**: double quotes only.
- **JSON**: double quotes (RFC 8259 — there is no choice).

### 2.5 Imports

Group imports by **origin**, separated by a blank line. Within each group, sort
**alphabetically by module path**. The same rule applies to the `imports: [...]` array in
every Angular `@Component({ ... })` decorator — keep it alphabetised so additions land at
a predictable spot.

The grouping order, top to bottom (skip groups that don't apply):

1. **Angular framework** — `@angular/*`.
2. **Third-party / external packages** — `@jsverse/transloco`, `leaflet`, `primeng/*`, `rxjs`, …
3. **Internal modules** — `../core/...`, `../component/...`, anything under `src/app/`.

```ts
import {ChangeDetectionStrategy, Component, inject} from "@angular/core";
import {FormsModule} from "@angular/forms";

import {TranslocoDirective} from "@jsverse/transloco";
import {ButtonModule} from "primeng/button";
import {DialogModule} from "primeng/dialog";

import {GAME_CONSTANTS} from "../../core/constant/game.constants";
import {RecipeTileComponent} from "../recipe-tile/recipe-tile.component";
```

No re-export barrels (`index.ts`) unless absolutely necessary; prefer direct imports so
refactor tools can move files cleanly.

### 2.6 No dead SCSS

Every selector in a component's `.scss` file must match a class actually used by the matching
`.html` file — or be referenced by runtime-injected DOM (Leaflet markers, PrimeNG drawer
overlays). When a rule targets runtime DOM, leave a comment naming the source so the next
reader doesn't assume it's dead and delete it; see `frontend/src/styles.scss` for the
`.building-marker` block as the canonical example.

When you remove markup, delete the now-orphaned SCSS in the same change. Run
`ng build --configuration=production` periodically — the production CSS extractor surfaces
unused rules through bundle-size growth.

## 3. Architecture

### 3.1 Atrium is a reusable library first, application second

Anything under `backend/src/main/java/org/atrium/core/` is part of the reusable
**library surface**. It must:

- not depend on game-specific code (no `import com.somegame.*`);
- expose extension points through Spring beans + Jackson `@JsonTypeInfo` polymorphism
  (see {@link org.atrium.core.domain.model.GameSettings}) rather than hard-coded enums;
- self-wire via `LobbyAutoConfiguration` so a downstream project gets every bean by
  declaring the Gradle dependency alone.

The reference host application (`org.atrium.Application`) lives outside `core/` and
exists only to make the library runnable on its own for local testing.

### 3.2 Constants live in their own files

- Backend: `org.atrium.core.domain.constant.AtriumConstants` for compile-time invariants
  (Redis key prefixes, room-code alphabet); `org.atrium.core.autoconfigure.AtriumProperties`
  for operator-tunable values bound from `application.yml` under `atrium.core.*`.
- Frontend: `frontend/src/app/core/constant/<area>.constants.ts`.

Magic numbers in component / service code are forbidden. Lift them to a constants file —
the rule of thumb: "if an operator or a designer might tweak this, it goes in a
constants file."

### 3.3 Reusable UI

Common UI shapes (icon + title cards, badges, etc.) live in their own component under
`frontend/src/app/component/<name>/`. Build features by composing these — don't copy markup.
Modularise aggressively, **even if a piece is only used once**: it keeps the parent component's
TypeScript focused on logic and lets the template stay declarative.

### 3.4 Angular component file layout

Always split an Angular component into **three sibling files**:

```
component/<name>/
├── <name>.component.ts
├── <name>.component.html
└── <name>.component.scss
```

No `template:` or `styles:` inline blocks in `@Component(...)` — even one-liners. Always use
`templateUrl` and `styleUrl`. This keeps diffs clean and makes IDE template tooling work.

### 3.5 PrimeNG severities for status colour

Use PrimeNG's `severity` palette (`info`, `success`, `warn`, `error`, `secondary`) and
design tokens (`var(--p-text-muted-color)`, `var(--p-content-border-color)`, …) for status /
chrome colour. Custom hex colours are reserved for **domain colour codes** (OSM zone categories,
ingredient category badges).

### 3.6 Tooltips on icon-only affordances

Every icon-only button (no visible label) MUST have a `pTooltip` describing its action. Same
for any icon plotted on the map without a textual label (building markers, shipment glyphs,
OSM placement zones). The tooltip text must come from the i18n bundle, not be hardcoded.

### 3.7 Lombok everywhere it cleans things up

Use Lombok aggressively on Spring-side Java:

- `@RequiredArgsConstructor` on `@Service` / `@RestController` / `@Configuration` /
  `@Repository` / `@Component` classes — never write a constructor by hand if Lombok
  can generate it.
- `@Slf4j` instead of hand-rolled loggers.
- `@Getter` / `@Setter` / `@Value` / `@Data` on POJOs and `@ConfigurationProperties`
  classes.
- `@Builder` for many-field DTOs.

Records are preferred over Lombok `@Data` for value types; reserve `@Getter` /
`@Setter` for mutable POJOs (configuration properties, JPA-style entities — neither
of which we use much here).

### 3.8 JSpecify nullness

- Each Java package has a `package-info.java` annotated `@NullMarked`. **Everything is
  non-null by default**; mark nullable values explicitly with `@Nullable`.
- Do not use `Optional` for fields or method parameters — `Optional` is for return values
  only (Java's official guidance). Use `@Nullable` instead.
- The frontend's TypeScript counterpart is `strictNullChecks: true` (already on by default
  in our `tsconfig`).

### 3.9 fastutil over JDK collections

For tight hot-path code (the lobby's room / player iteration loops, future game-engine
state stores), prefer fastutil's primitive-keyed and primitive-valued collections:

- `Object2ObjectOpenHashMap` instead of `HashMap`
- `Long2ObjectOpenHashMap`, `Object2IntOpenHashMap`, etc. when keys/values are primitives
- `ObjectArrayList`, `IntArrayList` for typed lists
- `ObjectLists.emptyList()` instead of `Collections.emptyList()`

Use JDK collections only when crossing a public API boundary that already commits to
JDK types (records returned to Jackson, Spring repositories, reactive `Flux` /
`Mono`, …).

### 3.10 Internationalised strings

- All player-visible text lives in `frontend/src/assets/i18n/<lang>.json`.
- Hardcoding English strings in templates / TypeScript is forbidden. **Single exception**:
  `LANGUAGE_LABELS` in `core/constant/game.constants.ts` — language names always render in
  their own native script regardless of the active locale, so they're code, not translatable
  text.
- **Translation-key parity is mandatory.** Every key present in `en.json` must also be present
  in `zh.json` (and any future locale). The resolver falls back to English at runtime, but a
  missing key in a locale file is a bug — review the diff before merging. A simple guard:
  `jq -r 'paths(scalars) | join(".")' en.json zh.json | sort -u | …` should produce identical
  key lists.

### 3.11 Locale-aware UI formatting

All numbers, dates, times, and currency-amount renderings must adapt to the **browser's
locale** (`navigator.language`), not the active translation language. Reach for the
helpers in `frontend/src/app/core/utility/format-locale.ts` (`formatMoney`,
`formatNumber`, `formatDate`, `formatTime`) — they wrap `Intl.NumberFormat` /
`Intl.DateTimeFormat` and accept an optional explicit `locale` for tests.

The currency **symbol** stays a literal `$` (lifted from `GAME_CONSTANTS.economy.currencySymbol`).
We don't pretend to convert exchange rates; only the *formatting* (decimal separator,
thousand separator, date order) follows the player's locale.

### 3.12 Style guides

- Angular: follow https://angular.dev/style-guide — standalone components, signals over
  RxJS where possible, `input()` / `output()` instead of `@Input` / `@Output` decorators,
  `inject()` over constructor parameters, `OnPush` change detection by default.
- Java: idiomatic Java 21 — records for value types, sealed interfaces for ADTs, pattern
  matching in `switch`, virtual threads via `Thread.ofVirtual()` for I/O-bound work.
- TypeScript: idiomatic ES2024+ — `const` by default, `readonly` on every interface field,
  template literal types where they buy clarity, `satisfies` over type assertion.

### 3.13 Resolve all IDE warnings

Treat compiler / IntelliJ / TS-Server / ESLint warnings as errors. If a warning is genuinely
not applicable, suppress it locally with the narrowest possible annotation
(`@SuppressWarnings("…")`, `// eslint-disable-next-line …`) and a one-line comment explaining
why. Don't broad-suppress at the file or module level.

### 3.14 Networking — reactive, event-driven, Redis-backed

Full architecture in [`docs/ARCHITECTURE.md`](./ARCHITECTURE.md) and the lobby
specifics in [`docs/LOBBY.md`](./LOBBY.md). The short list every contributor must obey
when adding a new endpoint, event, or state mutation:

1. **REST stays reactive.** Every controller method returns `Mono<…>` or `Flux<…>`.
   Don't block on `Mono.block()` inside the request thread — push the work back to the
   reactor pipeline. Long-running work goes on
   `Schedulers.boundedElastic()`.
2. **Redis is the source of truth for room and player state.** In-memory caches inside
   services are for transient scheduling only (e.g. {@link
   org.atrium.core.domain.service.DisconnectTracker}'s timer map). On every mutation, re-read
   the entity from Redis before mutating — the lobby is multi-instance-safe by design.
3. **State changes broadcast through Redis pub/sub, never directly to WebSocket
   sessions.** A controller mutating a room must `publish(new RoomEvent.…)` afterwards;
   the WebSocket handler subscribes to the channel and forwards. Direct
   session.send(…) bypasses the multi-instance fan-out.
4. **Events are polymorphic and self-describing.** Every event variant under
   {@link org.atrium.core.domain.event.RoomEvent} carries `roomCode` + `emittedAt` plus its
   own payload. No sequence numbers, no implicit ordering — clients dedupe by
   `(type, roomCode, emittedAt, payloadKey)` if they care.
5. **Authenticate every write.** Public id alone is not enough — every write endpoint
   requires the `(publicId, secretId)` pair to match what Redis has. The secret id
   never appears in URLs or access logs (it's body-only).
6. **WebSocket is read-mostly.** Clients perform mutations through REST and observe
   the result via the WebSocket fan-out. The handler today accepts incoming frames
   only to keep the socket alive — game-specific messages should mount their own
   handler at a different path.
7. **Disconnect is a soft event.** A dropped WebSocket flips the player to
   {@code DISCONNECTED} and schedules a leave after the grace period; a reconnect
   inside the window cancels it. Don't bypass {@link
   org.atrium.core.domain.service.DisconnectTracker} — restart-resilience is its job.

## 4. Testing

- **Backend**: JUnit 5 (`@Test` from `org.junit.jupiter.api`). One test class per production
  class, named `<Class>Test`, located at the mirror path under `backend/src/test/java/`.
  Use AssertJ (`assertThat(...)`) for fluency. Reactor publishers go through `StepVerifier`.
- **Frontend**: Jasmine + Karma (Angular's defaults). One spec per component / service,
  named `<thing>.<kind>.spec.ts` next to the production file. Use `TestBed` + Angular's
  `provideHttpClientTesting()` for HTTP service tests.
- Update tests whenever you change the production code — a green test that no longer asserts
  anything meaningful is worse than no test.
- Aim for tests on:
	- All branches of pure utility functions (`RoomCodeGenerator`, name / avatar
	  sanitisation, …).
	- Every state-mutating method on {@link org.atrium.core.domain.service.RoomService}
	  (create / join / leave / kick / delete / start / stop / settings update).
	- Each REST endpoint's happy path + at least one failure path.
	- The {@link org.atrium.core.domain.service.PlayerService#resolveRoom} repair scan when
	  the cached index disagrees with the room's roster.

## 5. Documentation

- Every behavioural change updates the relevant doc — `README.md` (intro, structure),
  `docs/ARCHITECTURE.md`, `docs/API.md`, `docs/LOBBY.md`.
- Public Java types use Javadoc; public TS types use JSDoc / TSDoc.
- The auto-generated REST contract lives in `docs/API.md`; keep it in sync with the
  shape of `LobbyController` and the DTOs under `org.atrium.core.api.dto`.
