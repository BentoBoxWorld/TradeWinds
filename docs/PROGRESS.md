# TradeWinds — Progress Log

What is done, and pitfalls hit on the way. Newest stage first. Read
`TRADEWINDS_SPEC.md` for requirements; this file records reality.

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
