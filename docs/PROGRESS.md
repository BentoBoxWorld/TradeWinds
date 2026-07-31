# TradeWinds — Progress Log

What is done, and pitfalls hit on the way. Newest stage first. Read
`TRADEWINDS_SPEC.md` for requirements; this file records reality.

## The fuel guarantee (2026-07-30)

Ben's rule: only the skint AND fuel-less row. `MarketService.saleCatalog`
wraps the type catalog and appends CHARCOAL when it carries no fuel-valued
material - so LUXURY (sells nothing) and AGRICULTURAL/FISHING/FROZEN all
offer charcoal, while FOREST (logs) and MINING (coal) already qualify.
Charcoal reclassified ORES -> WOOD (charred timber; forest islands sell it
cheap, differentiates from mined coal). Priced through the normal model.
Tested per type. 121 tests green.

## Rower navigation: hologram compass + Star Chart (2026-07-30)

Ben: /tw chart's text list was useless for navigation; rowers (no fuel) had
no idea which way to go. Two aids, both his design:
- **Hologram compass** (`ChartHolograms`): boated `/tw chart` spawns
  TextDisplay holograms on a 10-block ring in each charted island's true
  bearing (name/type+band/distance), zooming out from the player via display
  teleport interpolation (setTeleportDuration). Shared-bearing islands stack
  nearest-lowest (20-degree sectors). Caller-only (hideEntity for others),
  non-persistent, auto-fade after chart.hologram-duration-seconds. Marker
  geometry is pure and tested. `/tw chart list` keeps the text list.
- **Star Chart** (`StarChartRenderer` + `StarChartService` + `TWWorldData`):
  /tw starchart gives a FILLED_MAP bound to one shared contextual MapView;
  the renderer draws per holder - ocean background, holder centered as a
  rotating cursor, charted islands as band-colored dots (sized by real
  terrain radius) with MinecraftFont names, out-of-range islands pinned to
  the map edge as heading hints. Redraw throttled to 1/s or a pixel of
  movement. Map id persists in TWWorldData; MapInitializeEvent re-attaches
  the renderer after restarts so old items keep working. Click-to-zoom is
  not possible with map renderers - scale is config
  (chart.starchart-blocks-per-pixel).
119 tests green.

## Trade quantities + empty-hold UX (2026-07-30)

Ben wanted partial sells ("left-click all, right-click one"). Dialog buttons
carry no click-type, so instead each cargo row is THREE grid buttons
(columns(3)): x1 | x16 | All-with-total; buy rows are x1 | x16 | x64.
MarketService.sell gained an amount cap (MAX_VALUE = all). The Sell button
hides when the hold has nothing the island pays for; sell rows cap at 8
(dialog fits, note shown when truncated). 114 tests green.

## Playtest fix: spawn islet + safe respawn (2026-07-30)

Ben died and vanilla respawn hunted for "solid ground" near world spawn -
which in an ocean world is the seabed at y42, entombing him in a death loop.
Fix in three parts:
- **Spawn islet**: GalaxyConfig.spawnIsletRadius (config
  galaxy.spawn-islet-radius, 48) - the engine's landLiftAt raises a small
  plains islet at the origin (generator-made, principle 6; deterministic and
  independent of galaxy islands, which stay bit-identical). needsReset: only
  fresh chunks get it - delete the four origin region files on existing
  worlds.
- **World spawn pinned** to the islet surface (getHighestBlockYAt at enable).
- **SpawnRespawnListener**: deaths in either TradeWinds world without a
  bed/anchor respawn on the islet; bed and anchor spawns honored; other
  gamemodes untouched. StarterKit's land branch now applies at first spawn
  (boat item rather than water launch).
No BentoBox island object at spawn (a range-1000 island at the origin can
overlap a jittered starter island's range and break grid registration - see
the round-1 fix notes); protection of the islet itself is deferred.
114 tests green.

## Accessibility tune: warp dialog capped at 8 (2026-07-30)

The dialog scrolls beyond ~8 buttons, but the scroll affordance is easy to
miss (kids, accessibility). `travel.warp.max-destinations` default 20 -> 8 so
the list always fits on screen; live server config updated too (stored
values beat new defaults). Charting more islands is still fine - the eight
NEAREST are listed.

## Stage 4b — Trade UX + embedded BlueBook pricing (2026-07-30)

Feedback round on the market screens, plus a direction change from Ben:
- Every dialog page shows balance + approximate hold space
  (`HoldService.freeSpace`: empty slots x 64 + stack headroom + bundle
  weight); sub-pages get a Back exit button, the main menu a Close (the
  dialog API's exitAction slot).
- **BlueBook's pricing logic is now embedded** (`economy.PriceEngine`,
  ported from ~/git/bluebook PriceEngine) instead of referenced as a plugin:
  config base table (renamed `economy.base-prices`) + recursive recipe
  derivation (depth 6, cycle-safe, cooking adds COAL/8 fuel share,
  stonecutting passthrough, output-count division), durability scaling and
  enchantment weights included for later gear-selling. Reflection bridge and
  BlueBook softdepend removed; cache invalidated on reload. Recipe source is
  injectable - derivation is fully unit-tested (109 total green).

## Stage 4 — Economy and cargo (2026-07-29) — CODE COMPLETE, awaiting in-game test

The game becomes a game: buy low, sell high.

- **Prices**: `PriceModel` (pure record, fully tested invariants: produce->
  demand routes profitable, same-island round trips always lose, margins
  scale with band, stock drift clamps). Base prices from **BlueBook via
  reflection** (soft dependency through AddonsManager - no compile-time
  coupling; not in ~/.m2) with a ~50-entry fallback table in config.
  `TradeCategory` classifies materials by name heuristics; `TypeEconomy`
  holds the produce/demand tables and sale catalogs (code content for now,
  config later). LUXURY produces nothing - pure demand sink in dangerous
  space.
- **Stock drift**: `TWIslandData` (keyed by cell) persists per-category
  stock; selling floods depress prices, buyouts raise them; lazy decay
  toward equilibrium per hour on access.
- **The hold** (`HoldService`): chest-boat inventory + expander shulker
  contents + up to `max-bundles` (3) bundles. contents/count/remove/add with
  space handling. Pocket items are invisible to the market - tested.
- **Trade dialogs**: main (balance, sell/buy/shipwright) -> sell page (one
  button per hold material with island prices) -> buy page (catalog in
  16-batches, limited by balance then by hold space - pay only for what
  fit). Entry: right-click a resident trader (vanilla trade screen
  suppressed) or `/tw trade` in protection range. `TWTradeEvent`
  (cancellable) fires before every trade - Stage 6's contraband hook.
- **Cargo expanders**: PDC-marked gold-named shulker boxes, purchase-only
  (no End -> no shells), price doubling per owned (`expander-base-price`,
  `expander-cap`), stowed directly into the hold.
- **Vault**: BentoBox VaultHook; starter kit now deposits
  `economy.starting-balance`. Needed the VaultAPI provided dependency in the
  pom (VaultHook signatures reference EconomyResponse).
- Item drop/pickup explicitly set to visitor rank on trading islands (Ben's
  request; also a Stage 6 jettison prerequisite).
- 100 tests green.

## Playtest fix: creeper-proof markets (2026-07-29)

Creepers could be lured into the plaza to blow up stalls/landmarks. Fixed with
core's own flags, asserted each enable for both worlds:
`CREEPER_DAMAGE=false` (explosions break no blocks, still hurt entities) +
`CREEPER_GRIEFING=true` - counterintuitive but required: GRIEFING=false makes
core cancel the ENTIRE explosion when the creeper targets a non-member, and
everyone is a non-member on unowned trading islands, so creepers could not
even hurt players. Residents were already creeper-proof (non-player damage
cancelled). setDefaultSetting persists into world.flags, overriding any stale
lazily-saved true from earlier runs.

## Playtest tune: warp arrival distance (2026-07-29)

350-from-center arrivals were a boring paddle and easy to get lost from even
with the HUD. Config reworked from margin-inside-border to a direct
`travel.warp.arrival-distance` (blocks from destination center), default 130 -
inside the default 10-chunk view distance, so you materialize looking at your
destination. Note 130 is just inside the terrain radius (160): a high-noise
flank can occasionally mean shallow shelf water on arrival; boats cope. Add a
water-nudge only if playtests show beached arrivals.

## Playtest fix: warp offer at the visible border (2026-07-29)

Ben rowed out of Tiabi, got "Now leaving", and no warp offer ever came. Root
cause: TWO borders. BentoBox's enter/leave messages and the Border addon's
wall are at the PROTECTION range (400); the warp trigger and arrival were at
the island-space range (~1000) - 550 blocks of dead ocean past the perceived
border. Fixed: the offer ring now straddles the protection edge (fires the
moment "Now leaving" appears; ring is trigger-distance wide each side so a
crossing cannot skip it), and warp arrival lands protection - margin (~350)
from the destination center - island in sight, short row to the dock.
Not-a-bug notes from the same session: the HUD keeps showing the island out
to the full range (island waters), the bar fill is closeness-to-dock
(1 - dist/range), and fuel never hides the warp dialog (greyed entries).

## Stage 3b — Resident protection, band flags, nav bar, starter kit (2026-07-29)

Playtest-driven package (traders died at night and scattered; no dock
navigation after warp; bare-handed spawn):

- **Band flag policy** (`bands.*` config): per-band MONSTER_NATURAL_SPAWN
  (SAFE/POLICED off - note: setting flags only govern the protection range,
  so approaches stay dangerous), PVP, and HURT_VILLAGERS rank (SAFE=500
  prevents outright; elsewhere allowed - crime handled by Stage 6, not
  invincibility). Applied at registration; `/twadmin reflag` re-applies to
  existing islands after config changes.
- **Residents survive**: mobs never target them (EntityTargetLivingEntityEvent
  cancel on the resident PDC tag); only player-caused damage lands;
  `ResidentAuditTask` tethers strays home (HOME_KEY PDC, plain teleport) and
  respawns killed residents after `residents.respawn-delay-minutes` using the
  engine-deterministic staffing count (`GalaxyEngine.villagerCount`).
  Rejected: wandering-trader night despawn (blocks night trading, more state).
- `/tw settings` restored - core targets the island AT the player's location,
  so visitors get a read-only view for free (no rank = no toggling).
- **Navigation boss bar** (1s task): island name | standing | dock distance;
  fills as you close on the pier; color per band. `TradeWinds.getPlayerStanding` is a
  "Clean" stub until Stage 6. Config gate `hud.navigation-bossbar`.
- **Starter kit** (spec §4): first spawn arrival gives a named Trading Bundle
  + oak boat - seated in it on water, item on land; `starterKitGiven` in
  TWPlayerData. Boat owner UUID in PDC from day one. Repeat spawns auto-launch
  a carried boat item.
- MockBukkit patch extended AGAIN for a 26.2 registry gap:
  `keyed/damage_type.json` lacked `minecraft:sulfur_cube_hot` and
  `org.bukkit.damage.DamageType` clinit died - added `damage_type` to the
  KEYED diff; script synced back to GushBlock. (Third instance of this
  pattern: any `No value for minecraft:X` in tests means another registry
  file needs the diff treatment.)
- 84 tests green.

## Stage 3 — Travel: warp, fuel, charting (2026-07-29) — CODE COMPLETE, awaiting in-game test

**Verify-first resolved:** Paper 26.2 dialog API confirmed and used:
`Dialog.create(factory -> factory.empty().base(...).type(...))`,
`DialogType.multiAction(buttons)`, `ActionButton.create(label, tooltip, width,
DialogAction.customClick(callback, options))`, shown via Adventure's
`Player#showDialog`. No command round-trips - button callbacks call the warp
directly.

**Done:**
- `RouteGraph` (pure): cost = Euclidean x fuel-per-block, ceil, min 1;
  direction-independent `edgeKey("cx,cz>cx,cz")` overrides from config
  (`travel.warp.edge-overrides`) make cheap lanes/expensive frontiers.
  `arrivalPoint`: just inside the destination border on the origin bearing.
- `TWPlayerData` + `PlayerDataManager` (first Database use; AOneBlock
  cache-in-front pattern): chart as Set of cell keys; new players pre-chart
  the starter cluster; save on quit/disable.
- `ChartingListener`: chunk-crossing-gated moves chart islands whose range
  (1000) the player enters - action bar + `IslandChartedEvent`.
- `FuelService`: hold = chest-boat inventory + bundle contents ONLY (spec
  principle 1 - pocket fuel never counts). Cheapest-first consumption,
  overshoot burned, lava bucket leaves its empty bucket. Values config
  `travel.fuel-values`.
- `WarpService`: destination selection (charted-only, origin excluded,
  nearest-first, capped, affordability-flagged - pure and tested separately
  from dialog rendering); warp = TWWarpEvent (cancellable) -> consume ->
  dismount -> teleportAsync player + boat -> re-seat next tick (AcidIsland
  pattern) -> nausea/blindness/damage + portal effects ->
  TWWarpCompletedEvent. Failure roll deliberately absent until Stage 5.
- `BorderPromptListener`: boat within trigger-distance (30) inside an island
  border auto-offers the dialog, per-island cooldown. `/tw warp` (boated, in
  island waters) and `/tw chart` commands.
- Tests: 75 green (route math incl. overrides + arrival geometry, chart data,
  fuel hold-only/cheapest-first/lava rule, destination selection, border ring
  geometry).

**Notes:**
- Real `ItemStack`s work fine in tests under the mocked ItemFactory as long
  as no ItemMeta operations are exercised (bundle internals are in-game-only
  territory).

## Stage 2 — Island content (2026-07-29) — CODE COMPLETE, awaiting in-game test

Stage 0/1 checklists fully passed in-game before starting this.

**Done:**
- **Deterministic dock** (open question resolved per the spec's lean): each
  island has a seeded bearing; the quay is terraformed by the chunk generator
  (principle 6 - generator makes land, nothing is pasted): stone-brick quay,
  type-specific plank deck at sea+1, running from the plaza (45% of terrain
  radius) out to open water (85%). Market **plaza**: flattened dirt-path disc
  at sea+2 with a 10-block blend ring so it meets terrain without cliffs.
  Geometry lives in the pure galaxy package (`DockPlan`, `ColumnPlan`,
  `GalaxyEngine.columnPlanAt`); `GalaxyConfig` gained `seaLevel`.
- **IslandDecorator** (BlockPopulator, overworld only): when an island's
  plaza-center chunk generates - bell landmark, four lantern posts, 2-4 stalls
  (fence + type-colored wool canopy + barrel), all island-deterministic
  (seeded from the island hash, not the chunk Random). Structures are
  code-built; per-type blueprint sets can replace the stall builder later.
- **Residents** via `LimitedRegion.createEntity` → configure → `addEntity`
  (Poseidon pattern): 3-5 villagers with professions per island economy
  (`IslandPalette`), skin type per biome, persistent + PDC-tagged
  `tradewinds:resident`; iron golems by band (SAFE 3 → ANARCHIC 0). Villagers
  spawn on the plaza, inland of the quay (shoreline-safety rule).
- Tests: 55 green (dock-plan geometry, column plans, terraform materials,
  decorator determinism/placement/residents, golem counts, villager types).

**Pitfalls:**
- In test code, fully-qualified `world.bentobox...` names inside methods are
  shadowed by CommonTestSetup's `protected World world` field - use imports.

**Stage 2b — island identity (same day, playtest feedback "islands all look
the same; INDUSTRIAL doesn't look industrial"):**
- Type **landmarks** built by IslandDecorator on the inland plaza edge:
  INDUSTRIAL brick chimney with campfire-on-hay signal smoke (visible from
  sea), MINING shaft head + rails + ore, AGRICULTURAL wheat plot + hay,
  FISHING smokehouse + moored OakBoat at the pier, FOREST log pile, LUXURY
  quartz fountain, FROZEN ice beacon.
- **Plaza surface per type** (polished blackstone / smooth quartz /
  cobblestone / podzol / planks / dirt path) via IslandPalette; type-colored
  **banner + lantern at the pier end**; **workstation blocks** beside each
  stall (safe now professions are XP-locked).
- **`galaxy.type-weights` config**: weights moved into GalaxyConfig (record
  gained a typeWeights param with a compact-constructor fallback to enum
  defaults on zero/empty totals; old 8-arg constructor kept for
  compatibility). Settings parses name→weight map, unknown names logged.
  Weight changes reshape the galaxy → needsReset.
- 58 tests green (landmark signatures, pier banner, weight overrides,
  default-weights galaxy unchanged by the new parameter).

**Playtest round 2 fixes (same day):**
- **Dock gap:** the plaza blend ring outranked the dock strip in
  `columnPlanAt`, and on the seaward side the ring blends toward submerged
  natural terrain - the quay started ~10 blocks offshore. Order is now plaza
  disc → dock strip → blend ring, so the deck runs unbroken from plaza edge
  to pier end. Regression test walks the full dock axis on 12 islands.
- **Villager professions collapsed to Fisherman/none:** vanilla villager
  brains reset a zero-XP, no-job-site villager to unemployed, and stall
  BARRELs are fisherman job sites, so the survivors all claimed those.
  `setVillagerExperience(1)` at spawn locks the assigned profession. (Known
  vanilla behavior - worth remembering for any future spawned-NPC work.)

## Playtest round 1 fixes (2026-07-29)

Ben's first in-game pass (see TESTING.md) found three failures; all fixed:

1. **`/tw` → "There is no spawn in this gamemode".** Core `IslandSpawnCommand`
   needs a spawn *island*, which nothing creates. Replaced with
   `TWSpawnCommand` (DelayedTeleportCommand straight to the world spawn on the
   sea surface; `World#setSpawnLocation(0, seaHeight+1, 0)` set in onEnable).
   Deliberately did NOT create a BentoBox spawn island at origin: a
   range-1000 island at 0,0 can overlap a starter island's range (worst-case
   jittered center is ~1768 from origin) and would fail grid registration.
2. **`/tw create` gave free bedrock islands.** The default player command set
   is wrong for TradeWinds - players buy islands at Stage 7. Player command
   setup now registers only `spawn`, `info`, `language`. Lifecycle test locks
   this in (create/reset must be absent).
3. **Nether portal reached the interstice.** `create-and-link-portals: false`
   only stops BentoBox's own linking; Multiverse handled the portal anyway.
   `IntersticePortalListener` now cancels PortalCreateEvent, PlayerPortalEvent
   and EntityPortalEvent touching either TradeWinds world at LOWEST priority.
4. **"No islands found" was not a bug but a discoverability gap** - islands
   register lazily on center-chunk load and nobody had traveled 3.5k blocks
   out. Added `/twadmin islands` (nearest 10 via pure galaxy query, works
   pre-generation) and `/twadmin tpisland <n>` (chunk-loading teleport).

46 tests green after fixes.

## Stage 1 — Seeded galaxy (2026-07-29) — CODE COMPLETE, awaiting in-game test

**Done:**
- `world.bentobox.tradewinds.galaxy` package — pure Java, zero Bukkit imports,
  fully headless-tested: `GalaxyEngine` (placement, types, security bands,
  biome keys, names), `GalaxyConfig`, `IslandSpec`, `IslandType`,
  `SecurityBand`, `NameGenerator` (classic Elite digraph table), `Hashing`
  (SplitMix64 over (seed, cell, salt) — order-independent, no shared Random).
- **Placement is constructive, not rejection-sampled:** cells of side
  2×min-separation, jitter capped at min-separation/2 → any two islands are
  ≥ min-separation apart by construction. Deterministic and O(1) per query.
- **Starter density floor:** the `starter-cluster-min-islands` cells nearest
  spawn are force-occupied and SAFE regardless of density rolls.
- **Bands by distance from spawn** (`galaxy.band-radius` per step) with ±1
  seeded wobble — the map communicates risk radially. FROZEN islands freeze
  their approach ring (terrain radius → 2×) into frozen_ocean ice lanes.
- Generator: `terrainLift` (additive, cosine mask, `galaxy.land-lift` at
  center) replaces the Stage 0 multiplicative hook; island columns that clear
  the sea get stone/dirt/grass soil so vanilla decoration plants them.
- `TradeWindsBiomeProvider` resolves galaxy biome keys via `Registry.BIOME`
  with cache; declares every possible biome in `getBiomes()`.
- `GalaxyIslandRegistrar` (ChunkLoadEvent): registers unowned BentoBox islands
  at center-chunk first load — `createIsland(loc, null)`, name set (BentoBox's
  ENTER_EXIT_MESSAGES announces it), PVP flag per band, type/band in island
  metadata. Guarded by an in-session cell set + `getIslandAt` DB check.
- `TradeWinds.getGalaxyEngine(worldSeed)`: lazy init; `galaxy.seed` 0 means
  "use the world seed" (logged so admins can share it).
- Tests: 41 total green (19 new: engine determinism/order-independence/
  separation/starter-floor/bands/lift/biome-ring, name generator, registrar,
  updated generator + biome provider tests).

**Pitfalls:**
- `putMetaData` on a fresh Island silently no-ops until `setMetaData(new
  HashMap<>())` initializes the map — the registrar does this explicitly.
- NameGenerator: the Elite digraph table's '.' skip tokens can roll a 1-2
  letter name; the fallback loops until ≥3 letters (a single fallback append
  could itself be the 1-letter "A." pair).

## Stage 0 — Addon scaffold (2026-07-29) — CODE COMPLETE, awaiting in-game test

**Done:**
- Maven project (`pom.xml` cloned from GushBlock's verified 26.2 toolchain:
  Paper API `26.2.build.40-alpha`, BentoBox 3.18.1, JDK 25 / release 21,
  patched MockBukkit `4.113.4-p262`, surefire `--add-opens` set, JaCoCo).
- `TradeWinds extends GameModeAddon` (AcidIsland lifecycle pattern):
  overworld `tradewinds_world` + interstice `tradewinds_world_nether`
  (NETHER environment), **no End world ever** (spec principle 7).
  `/tw` + `/twadmin` via Default{Player,Admin}Command; both default player
  actions are `spawn` since TradeWinds players own no island at first.
- `ChunkGeneratorWorld`: ocean everywhere — bedrock, solid base, Perlin ocean
  floor (8 octaves, scale 1/30, ±25 amplitude from `sea-floor` 25), water to
  `sea-height` 70. Interstice identical shape with netherrack/basalt/soul-sand
  palette. Vanilla noise/surface OFF so no vanilla continents can appear.
  `terrainScale(worldInfo, x, z)` hook is where Stage 1's radial island mask
  multiplies in.
- `TradeWindsBiomeProvider`: sea/air biome split (Poseidon pattern), interstice
  biome for NETHER. Defaults OCEAN / OCEAN / NETHER_WASTES.
- `Settings` adapted from Gusher's (full WorldSettings), plus Stage 0 skeleton
  knobs: `galaxy.*` (seed, min-separation, starter cluster), `travel.warp.*`
  (fuel-per-block, failure-chance), `illegal-trade.enabled` master gate.
- Tests: 22, all green — Settings defaults, addon lifecycle incl. world
  creation via ServerMock, generator shape/determinism/palette/terrainScale
  hook, biome provider.

**Pitfalls:**
- `org.bukkit.block.Biome` static init died in tests: 26.2 added
  `minecraft:sulfur_caves` and the patched MockBukkit's `keyed/worldgen/biome.json`
  lacked it. Fixed by adding `worldgen/biome` to the KEYED diff map in
  `scripts/build_patched_mockbukkit.py` (note the registry file is under
  `keyed/worldgen/`, not `keyed/`). Script re-run, artifact reinstalled, and the
  updated script synced back to `~/git/GushBlock/scripts/` so a rerun there
  cannot clobber the shared `~/.m2` artifact without the biome entries.
- Use `getServer().getWorld(...)` (not `Bukkit.getWorld`) in `createWorlds()` —
  ServerMock backs it in tests, and `WorldCreator.createWorld()` resolves
  through the mocked server, so world creation is fully unit-testable.

**Interstice inaccessibility (Stage 0 accept):** `world.nether.create-and-link-portals`
is false so BentoBox never links portals into the interstice; there is no
command that goes there. Verify on the live server per `TESTING.md` — if some
other plugin (Multiverse) offers a route in, revisit with a listener at Stage 5.

## Pre-Stage 0 (2026-07-29)

- `TRADEWINDS_SPEC.md` written (consolidates overview/design-decisions/dev-plan;
  spec wins on conflict). `CLAUDE.md` per GushBlock pattern.
- Reference intel gathered: AcidIsland (GameModeAddon anatomy, boat handling),
  Poseidon (ocean noise), GushBlock (26.2 toolchain + MockBukkit patching),
  BlueBook (`PriceEngine.getPrice(ItemStack, worldName)`), Border (visual
  border, `BorderShower` API, `barrier-offset`/passable behavior).
