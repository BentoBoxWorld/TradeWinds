# TradeWinds — Progress Log

What is done, and pitfalls hit on the way. Newest stage first. Read
`TRADEWINDS_SPEC.md` for requirements; this file records reality.

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
