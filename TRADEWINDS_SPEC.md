# TradeWinds — BentoBox Game Mode Specification

**Name:** TradeWinds (decided — house style, one word, like BSkyBlock/AcidIsland; repo `TradeWinds` under BentoBoxWorld)
**Addon ID:** `TradeWinds` · **Package:** `world.bentobox.tradewinds` · **Commands:** `/tw` (admin: `/twadmin`)
**Target:** Paper 26.2 (Java 25 runtime, release-21 compile), BentoBox 3.18.1+
**Author context:** Written for implementation via Claude Code by the BentoBox author. Assume full familiarity with GameModeAddon, WorldSettings, Blueprints, Flags, the BentoBox Database, and the AcidIsland/Poseidon codebases (closest architectural relatives). Companion docs: `tradewinds-overview.md` (pitch), `tradewinds-design-decisions.md` (rationale + open questions), `tradewinds-dev-plan.md` (stage plan), **`tradewinds-hold-plan.md` (normative detail for the virtual hold, One Boat rule, boat ranks, dropped-boat lifecycle and expanders — revised 2026-08-01)**. Where this spec and those docs disagree, this spec wins — except that the hold plan is authoritative on hold/boat mechanics.

---

## 1. Concept

A sea-trading game mode: an endless procedurally generated ocean dotted with **NPC trading islands**. Players start with a boat — the boat IS the cargo hold — and get rich buying low and selling high — honestly, or by smuggling, bounty hunting, and piracy. Inspired by TradeWars 2002 and Elite, rebuilt in Minecraft terms.

| Elite/TW2002 ingredient | TradeWinds equivalent |
|---|---|
| Sectors / systems | Seeded sparse trading islands in open ocean |
| Hyperspace jump + fuel | Boat **warp** between island borders, paid in hold-carried fuel |
| Misjump / interdiction | **Interstice**: warp failure drops you in a hostile Nether sea |
| Cargo hold expansion | 20 boat ranks (Bamboo Raft → Pale Oak Chest Boat) → installed cargo expanders |
| Commodity market | BlueBook base prices × island type/biome/security modifiers |
| Police / legal status | Reputation bands, customs scans, police mobs, bounties |
| Player bases | Purchasable player islands in the open ocean |

### Design principles (load-bearing — do not break)

1. **The fuel/cargo tradeoff is the central mechanic.** Trading transacts **exclusively** against the player's **virtual hold** (server-authoritative, database-backed, sized by their one boat — see §4 and `tradewinds-hold-plan.md`) — never player inventory, ender chests, real containers, or anything else. Any feature letting sellable goods bypass the hold breaks the game's spine. Cargo leaves the hold only by being sold or destroyed.
2. **Risk symmetry between travel modes.** Warp: fuel cost, instant, interstice risk. Rowing: free, slow, lawless-ocean risk. Neither may become strictly dominant.
3. **Scans, not prices, balance contraband.** Sugar has infinite trivial supply; the customs-scan risk is the cost. Smuggling is a skill, not a tax.
4. **Crime pays you into danger.** Contraband sells only at less-safe islands; Fugitives are barred from safe-island markets. The richest criminals are structurally pushed into PvP space where bounty hunters lawfully operate.
5. **The seed is the world.** Island positions, existence, types, security bands, biomes, names, and route-graph edge costs are ALL pure deterministic functions of (seed, position) — unit-testable with no Bukkit dependency. Runtime randomness here breaks seed shareability.
6. **Generator-driven terrain, not pasted terrain.** Island landmass comes from the chunk generator (Poseidon-style noise × radial mask). Lazy generation is just chunk generation: no pop-in, no paste pacing. Blueprints/structures decorate; they never create the land.
7. **Scarcity by purchase-only endgame.** Cargo expanders are shop-only (top-tech ports), price-doubling per unit installed — the endgame money sink; the wallet is the cap. No End world, ever (nothing in the game needs it, and dimension exits break the ocean).

## 2. World Model

### 2.1 Worlds

- **Overworld** `tradewinds_world`: endless ocean, AcidIsland/Poseidon-style generator (sea over noised ocean floor, **no acid**), islands from the placement engine (§2.2). Default sea height 70, sea floor ~25 (Poseidon numbers; config).
- **Interstice** `tradewinds_world_nether`: NETHER-environment world, styled as a hostile dark sea (lava-tinged sky, ocean of water — Nether environment gives the ambience; water block configurable). Same generator class, no islands. Registered as the gamemode's nether world with `netherIslands: false`; **no portals** — entry only via warp failure (Stage 5). Until then it must be inaccessible to players.
- **No End world, ever** (principle 7). `endGenerate: false` hardcoded default.

### 2.2 Placement engine (pure, seeded)

`GalaxyEngine` — plain Java, zero Bukkit imports, fully unit-tested:

- **Input:** `galaxySeed` (long, config; shareable). All outputs are pure functions of (seed, cell/position).
- **Positions:** the ocean is divided into a coarse **spatial-hash grid** (cell size = `island-range × 2`, config). Each cell deterministically rolls (seeded by `hash(seed, cellX, cellZ)`) whether it hosts an island and where within the cell (jitter), then **rejection-samples against minimum separation** from neighbor cells' islands so protection zones never overlap and islands are never within sight of one another. Result: random-looking sparse scatter, deterministic, O(1) lookup of "which islands are near (x,z)".
- **Density**: base occupancy probability per cell, with a **density floor near spawn** (guaranteed starter cluster regardless of seed luck) and optional falloff outward. Proposed (adopt unless playtest says otherwise): density correlates with civilization — clusters are civilized, isolated islands anarchic; the map itself communicates risk.
- **Per-island attributes** (all from `hash(seed, islandPos, attributeSalt)`):
  - **Type:** agricultural, fishing, industrial, mining, luxury, … (config-extensible list; drives market modifiers and blueprint set).
  - **Security band:** EVE model — `SAFE`, `POLICED`, `FRONTIER`, `LAWLESS`, `ANARCHIC` (working names; config thresholds). Correlated with local density per above.
  - **Biome:** whole-island, from a per-type weighted table. Frozen-ocean approaches are a feature (ice = fast boat lanes).
  - **Name:** Elite-BBC-style procedural token-pair digraph generator, seeded — pronounceable, distinct.
  - **Tech level (adopted 2026-08-01):** 1–7, seeded, correlated with type (industrial/luxury skew high, agricultural/fishing low, ±2 variance — "Advanced Agricultural" exists). Starter cluster guarantees at least one ≥ TL3 island. Shown in entry announcement and chart data: *"Esedaxe — Industrial (Tech 6), Safe."* Tech gates **shops only, never docking, riding or crafting**: boat ranks sold ≤ TL × 3 (TL7 = all twenty), expanders sold only at TL7. Tech also modulates prices (±`tech-price-step`, default 3%, per TL step from 4): high-tech sells **finished** goods (metals, food) cheap and buys **raw** (ores, crops, wood, fish, stone) dear; low-tech the inverse — best routes are tech *differentials*, read off the chart as type × tech × security. Higher tech also furnishes the plaza: every port has the galley (workbench + campfire); TL3+ adds furnace + stonecutter, TL4+ smithing table + grindstone, TL5+ brewing stand + cauldron, TL6+ anvil, TL7 an enchanting table ringed with bookshelves.
  - **Range / protection radius:** AcidIsland framing — range ~1000, protection ~400 (config).
- **Reserved space:** cells that rolled empty are candidate sites for purchased player islands (Stage 7).

### 2.3 Chunk generator

`ChunkGeneratorWorld` owns the *shape* of the world; vanilla furnishes it. Vanilla noise and surface are off, so no vanilla continent can ever appear — but its carvers, decorators, mobs and structure placement all stay on.

- **The sea floor** (`galaxy.Seabed`, pure and unit-tested — it is not scenery, its depth picks the ocean biome): a broad **basin** field taking the floor from sunlit shelf (~14 blocks down) to abyssal plain (~46), **relief** rolling dunes over it, ridged-noise **rifts** cutting narrow canyons up to 26 blocks deeper, and **seamounts** rising off the deeper plains only. Seamounts are capped below the surface: the galaxy's islands stay the world's only land (principle 6). Floor material follows depth in patches — sand banks, gravel beds, clay pans, bare stone and tuff in the deeps.
- **Island shelf:** near any island or islet the natural floor eases to a standard shelf depth, so an island that falls over an abyssal plain still stands in shallow water and clears the waves by the same amount. This also makes each island's shoreline radius exact rather than noise-dependent.
- Near an island center (from `GalaxyEngine` — O(1) neighbor-cell lookup per chunk): the shelf is lifted by a **radial falloff mask** `m(d)` (1 at center → 0 at the island's terrain radius). The distance `d` fed to that mask is **warped by seeded noise** (`galaxy.coast-roughness`) so the mask draws bays and headlands rather than a disc, and the lift is modulated by a second field (`galaxy.island-hilliness`) so the land is hills and hollows rather than a dome. Everything that tests the island footprint — biome, shelf, icy approach ring — must use the same warped distance, or it draws a circle over ragged terrain.
- **Shores** are read from the finished terrain (land within a few blocks of sea level), never from a radius, so a beach follows the real waterline whatever shape the coast is.
- **Biome:** `BiomeProvider` returns the island's biome within its terrain radius (per column); islets return their own biome with a sandy **beach ring** at the waterline; open sea returns an ocean biome chosen by seeded temperature *and depth* — deep water gets the `deep_*` variants. That depth-to-biome mapping is what tells vanilla where **ocean monuments** belong; temperature is what separates warm from cold **ocean ruins**.
- **Vanilla structures** (`world.make-structures`) supply shipwrecks, ocean ruins, monuments, buried treasure and trial chambers. They are suppressed per-chunk over trading islands (`world.keep-structures-off-islands`) so nothing drops through a hand-built plaza or dock; wild islets are fair game. **Vanilla caves** (`world.make-caves`) carve under the sea floor and inside the islands; `generateCaves` then seals a crust back over anything they cut through the floor, so the sea floor is never left open (carvers know nothing about the ocean above, and generated chunks get no block updates to flood the holes). The caves survive under it.
- Interstice variant: NETHER environment, its own shallow drab sea floor over basalt/soul sand, no galaxy, no structures.
- **Island registration:** on first generation of an island's center chunk (or first entry — listener on chunk load), register an **unowned BentoBox island** at the center with range/protection from config and flags per security band. Island name announced on entry (BentoBox island enter event → action bar/title).

## 3. Travel

### 3.1 Rowing

Free. The border (Border addon in passable/visual mode, or protection-range visuals) does not block movement. Open ocean between islands is lawless: no protection flags apply (PvP live), no police. Ice lanes are fast.

### 3.2 Warp

- **Trigger:** player in a boat reaches an island's border (proximity trigger + action-bar prompt; packet-level "click the wall" rejected as needless complexity). Opens the **warp dialog** (Paper dialog API): charted islands listed with per-destination fuel cost; unaffordable/unreachable grayed out.
- **Route graph:** seeded per-edge costs; default = Euclidean distance × `fuel-per-block` multiplier; per-edge overrides in config allow cheap "warp lanes" and expensive frontiers without world regen.
- **Fuel:** value table (config): wood < coal/charcoal < coal block < lava bucket (non-stackable = deliberate tension). Consumed **from the hold's fuel slots only** (principle 1; 7 dedicated slots, fuel never competes with cargo and moves freely both ways). Empty buckets returned.
- **Execution** (AcidIsland `/ai` proven pattern): dismount → teleport player + boat cross-world-safe → re-seat. Arrival just inside destination border **on the bearing of the origin island**. Effects: nausea + blindness (durations config) + configurable minor damage ("warping hurts" — gates the under-equipped), portal particles + sound.
- **Charting:** dialog lists **charted** islands only. Chart by physically entering an island's range (free, slow); starter cluster pre-charted for new players. Buying chart data = post-MVP. Charting is the discovery layer over lazy generation.

### 3.3 Interstice (warp failure)

- Config failure chance (base; wanted players may get a higher rate — adopted from proposals). On failure: player + boat placed on interstice water **partway along the route** (seeded fraction ± jitter), 1–3 Ghasts spawned inbound.
- **Re-engaging the warp to the original destination is always free** — fuel was spent at engagement; failure is a detour, never a loss. This makes stranding impossible. Do not remove.
- Ghast-vs-boat combat intentionally survivable (fireballs battable). Interstice is a **shared** world (interdiction/ambush meta — very Elite); PvP rules there are an open question (§10).
- Stale entity cleanup: interstice mobs despawn on player exit / world empty.

## 4. Cargo & Progression (canonical, revised 2026-08-01 — full detail in `tradewinds-hold-plan.md`)

- **One Boat Rule:** a player has exactly one boat, or none. The hold is the *player's* virtual cargo inventory; the boat they possess only sets its size. Upgrades never move items — the slot count grows, contents stay put.
- **Virtual hold:** server-authoritative, database-backed (`PlayerHold`); **no real container ever holds cargo**. Slot-stack model: up to 21 cargo slots (by boat rank) + 7 fuel slots, auto-consolidated. GUI via BentoBox Panel API: TNT destroy slot, locked-slot panes, fuel row. Opened by right-click while riding, sneak-right-click the boat entity, or right-click the boat item.
- **One-way cargo:** anything can go in (except container items — bundles, shulkers, pouches); cargo leaves only by being **sold** or **destroyed** (TNT slot). Fuel is exempt: freely added and removed, never destroyable.
- **Boat ranks:** 20, Bamboo Raft (2 slots) → Pale Oak Chest Boat (21 slots); config map `boat-material → slots`. Prices quadratic `25 × slots²`. Shops list only bigger boats, gated by island tech (rank ≤ TL × 3); **a purchase replaces and destroys the old boat** (a trade-in at the yard); **a bigger-boat pickup is a swap**: your old boat becomes the floating item, carrying whatever overflow did not fit - nothing is ever lost to the sea. Crafting bypasses tech gates; crafting smaller than current is blocked; adding a chest to the current boat is an in-place upgrade.
- **Start kit:** Oak Boat (rank 2, 3 slots) + starter coal in the fuel slots. Charity/destitution grants a Bamboo Raft. `/tw restart` resets to the start kit.
- **Dropped boats:** a boat item (thrown, death-dropped, or broken loose) carries its hold via a DB record keyed by a PDC UUID, under a DB-persisted TTL. Pickup: bigger = swap (you keep your cargo, salvage merges in, overflow floats on in your old hull); smaller/equal = most-valuable-first transfer into free space, remainder stays claimable; no boat = it becomes yours. Handing a loaded boat to another player this way is sanctioned trade, not an exploit. Boats are destroyed only by lava; death drops the boat with contents (keepInventory keeps it; DeathChest chests it).
- **Cargo expanders:** install only in a Pale Oak Chest Boat, occupy one cargo slot each, open a nested 21-slot panel (net +20; no fuel row, no containers, no nested expanders; TNT-destroyable only when empty). Price `5000 × 2^installed`, no cap — the wallet is the cap. Sold only at TL7 ports. A fully expanded ship approaches ~440 slots: the guard-it-with-your-life endgame.
- **Boat ownership:** owner UUID in boat entity PDC from day one (theft/persistence/bounty questions hang off it).

## 5. Economy

### 5.0 The two economies (adopted 2026-07-30; stamping removed 2026-08-01)

TradeWinds runs **two parallel economies**, bridged by the hold:

- **The trade economy (money).** Customs stamping is **removed entirely**.
  Traders buy anything the hold carries, at prices set by island type, tech,
  band and the per-island **stock/demand pools** — capacity (a hold slot is
  scarce) and pool drift (selling into an island crushes its price toward the
  drift floor; pools decay back slowly) are what carry the balance that the
  stamp used to. Money still overwhelmingly enters through trade margins,
  because hauling bought goods up a price gradient beats farming into a
  saturating pool.
- **The vanilla survival economy (stuff).** **Wild islets** (small unnamed
  islands on their own fine grid; `galaxy.wild-islet-*`) are free country:
  mine, farm, build, sleep. Each rolls its own size and biome, so they range
  from sandbars to proper little islands, with a sandy shore and rarely
  (`galaxy.mushroom-islet-chance`) mushroom fields. They must be **common
  enough to meet by chance while sailing** — an empty sea is the failure mode.
  The sea between them carries the vanilla furniture too: shipwrecks, ocean
  ruins, monuments in the deep basins, buried treasure on the beaches, trial
  chambers in the rock, and caves under the floor. All protection flags default
  to allowed outside named islands' protection ranges. Homemade goods are
  usable directly (eat, wear, build, burn as warp fuel) and, via the hold,
  sellable into the pools.
- **Contraband** remains its own config list (`illegal-trade.contraband-materials`,
  default sugar) with the ×premium black-market price at FRONTIER-or-rougher
  ports, balanced by scan risk per principle 3 — no longer coupled to any
  stamping concept.

Survival needs are met by the **outfitter** shelf every island guarantees
(bread always, charcoal when the trade catalog carries no fuel, gear by type:
smiths at INDUSTRIAL, beds at farms, rods at fisheries) and by wild-islet
living. Destitution has an exit: `/tw restart` (config-capped) resets balance
and kit, keeping the chart. Wild islets are the future claim targets for
Stage 7 player islands (teams + Bank addon).

- **BlueBook** (`~/git/bluebook`, addon name `BlueBook`) supplies base prices via `PriceEngine.getPrice(ItemStack, worldName)` (single price; TradeWinds derives buy/sell spread). Soft-depend; fail gracefully with a config fallback table if absent.
- **Per-island modifiers:** island type (farm sells food cheap, wants raw materials…), biome, security band (margins scale with danger), plus per-island **stock and price drift** persisted via the BentoBox Database (keyed by island). Everything in Minecraft is tradeable; players may sell found/crafted goods into the same market.
- **Trade GUI** at the dock (villager interaction or dock sign), transacting only against the hold (§4).
- **Vault** economy for balances; starter balance config.

## 6. Contraband & Customs

- **Master config gate** `illegal-trade.enabled` disables ALL illegal-goods mechanics (family servers). Default on.
- **Contraband:** sugar (config list; never called drugs anywhere — code, config, locale). **Villager "passengers"** in boats sellable at less-safe islands only (boating the villager is the player's problem — emergent). Gated separately (`illegal-trade.passenger-trade`).
- **Customs scan on every entry** into island space — rowed or warped. Chance per security band (SAFE scans often; ANARCHIC never). Per-island scan cooldown prevents re-entry scumming.
- **Detection = chase, not fine:** "customs patrol dispatched" — police launch from the island; ~400 blocks of water is the decision window: **flee** across the border, **fight**, or **jettison — destroy-only** (ruled 2026-08-01): dump the evidence into the hold GUI's TNT slot and it is gone, a last-ditch option when you cannot beat the police or escape. Nothing floats. **Caught** = police land a hit or close to proximity radius while contraband aboard → confiscation + fine + rep loss.
- **Fleeing a detection flags you** at that island for a cooldown: re-entry skips the roll, police launch immediately.

## 7. Reputation, Police, Bounties

- **Single global score**, named bands: `UPSTANDING / CLEAN / OFFENDER / WANTED / FUGITIVE` (working). Proposed numbers (adopt as defaults, all config): scale −1000..+1000; hit villager −5, kill villager −25, kill police −15, kill innocent −100, scan-caught −30 + fine, passenger sale −20; decay +1 per 15 min clean active play toward zero; thresholds +250 / 0 / −200 / −500.
- **Recovery, three speeds:** slow clean-play decay (crimes shadow you for sessions); fines (capped — money buys you out of Wanted, not into virtue); positive acts (contracts/donations — post-MVP) above zero. Positive rep must pay: better prices, lower scan odds — Upstanding is a pursued status.
- **Wanted:** PvP damage override anywhere; growing bounty; police response at civilized islands. **Fugitive:** shoot-on-sight, barred from safe-island trading (principle 4). Killing a wanted player pays the bounty, no rep penalty — bounty hunting is lawful work. Bounty pays exactly once.
- **Bounty nameplate** (config-gated, default on): bounty displayed with the player's name via per-player scoreboard team suffix, only when bounty > 0. PlaceholderAPI placeholder exposed (`%tradewinds_bounty%` etc.) so TAB-style plugins can render instead — avoids conflicts. (Boats prevent sneaking: traveling pirates are always visible. Feature.)
- **Police by mobility:** **Phantoms pursue** (combustion cancelled or day patrols burn at dawn), **Guardians hold the sea perimeter** (area denial, never chase boats), **Iron Golems** take anyone who lands. All PDC-tagged, ticking `setTarget` reassertion task, **zero drops** (else wanted players become a crime-powered iron farm). Pursue within protection zone; break off when target crosses the visible border; despawn beyond island range or on chunk unload.
- **Per-band knobs:** scan chance AND response size — a mid band may scan rarely but respond hard. Route planning with contraband is map-reading skill.
- **Anti-bait guard:** innocent-kill penalty on direct kills only; prior damage from the victim exempts. Log edge cases; don't over-engineer pre-MVP.
- **Villager gossip** API hooked for organic price penalties at the offended island.

## 8. Player Islands (Stage 7)

Purchase command: sufficient balance → choose blueprint → placement at a valid reserved position (empty grid cell) respecting minimum separation from **all** islands (NPC and player). Standard BentoBox island/team management, protection, homes thereafter. AcidIsland-equivalent island life.

## 9. Data Model (BentoBox Database objects)

- `TWPlayerData` (by player UUID): reputation score, bounty, chart set (known island IDs), flags (per-island flee-flags with expiry), scan cooldowns.
- `PlayerHold` (by player UUID): boat material (or none), cargo contents `[{material, amount}]`, fuel `[{material, amount}]`, installed expanders with nested contents.
- `DroppedBoat` (by hold UUID, mirrored in the boat item's PDC): boat material, contents, fuel, `expiresAt` TTL. Ownership transitions (drop → pickup → replace) are atomic moves between `PlayerHold` and `DroppedBoat`; duplicating a key item cannot duplicate cargo.
- `TWIslandData` (by island ID): galaxy cell coords, type, security band, biome, name, stock levels + price drift map, market state timestamps.
- `TWWorldData` (singleton): galaxy seed (authoritative copy; config seed used at first creation then persisted), route-edge overrides cache, bounty ledger totals.
- Boat ownership, police tags, loot tags: entity **PDC**, not database.
- Transient (in-memory): active chases, dialog sessions, pursuit tasks.

## 10. Open Questions (running list — resolve before/at the flagged stage)

1. **Poseidon mask internals** (Stage 1): mask multiplies noise amplitude vs. lifts a base height — prototype both, pick by coastline quality.
2. **Dock placement** (Stage 2): deterministic terraformed shelf at fixed bearing (lean) vs. jigsaw adaptive.
3. **Boat loss rules — RESOLVED 2026-08-01** (`tradewinds-hold-plan.md`): boats always drop as an item with hold contents attached (DB-keyed, TTL) — death, trident, collision alike; only lava truly destroys. Insurance remains post-MVP.
4. **Interstice PvP** (Stage 5): is interdicting non-wanted players lawful or itself a rep crime?
5. **Scan/flag tuning** (Stage 6b): cooldown lengths, caught-proximity radius.
6. **Galaxies architecture** (post-MVP): multi-instance addon vs. world manager. Keep world references abstracted from day one.
7. **Villager shoreline safety** (Stage 2): keep villagers inland behind docks so drive-by raids don't trivialize police.
8. **Reputation numbers** (Stage 6a): defaults above are proposals; playtest-tune.

## 11. BentoBox Integration Checklist

- `TradeWinds extends GameModeAddon`; `addon.yml` api-version 3.18.0, icon `OAK_CHEST_BOAT`, softdepend `BlueBook, Border, Level, Challenges, Visit, Warps`.
- `Settings implements WorldSettings`, `@StoreAt(filename="config.yml", path="addons/TradeWinds")`, every gameplay number a `@ConfigEntry` from the stage it appears. Master gates: `illegal-trade.enabled`, bounty nameplate, interstice.
- Commands: `/tw` (`DefaultPlayerCommand`: go/spawn, status, chart, sell/buy arrive with their stages), `/twadmin` (`DefaultAdminCommand`: seed info, island info, regen checks, rep set, force-scan…).
- Flags: security-band flag presets on NPC islands; new protection/world flags as systems land (e.g. `TW_MARKET`, `TW_CUSTOMS_EXEMPT`) — created per stage, not speculatively.
- Custom events for the ecosystem: `IslandChartedEvent`, `WarpEvent`/`WarpFailedEvent`, `CustomsScanEvent`, `SmugglingCaughtEvent`, `ReputationChangeEvent`, `BountyPaidEvent`, `TradeEvent`.
- Placeholders: reputation band/score, bounty, charted count, current island name/band, hold value.
- Locales: `locales/en-US.yml` reference-style; all player-facing text localized.
- Level addon: not meaningful for NPC islands; player islands (Stage 7) work unchanged.

## 12. Milestones (= dev-plan stages; each ends green-build, playable, TESTING.md updated)

- **Stage 0 — Scaffold:** GameModeAddon; overworld + interstice worlds (ocean generator, no acid); `/tw`/`/twadmin`; config skeleton; no End. *Accept:* loads alongside other gamemodes; `/tw` teleports to ocean spawn; interstice exists, players can't reach it.
- **Stage 1 — Seeded galaxy:** GalaxyEngine (positions/types/bands/biomes/names, pure + tested); generator radial mask; lazy island registration; name announce. *Accept:* same seed → identical galaxy across runs (unit-proved); rowing out reveals biomed islands, no pop-in; islands persist in DB.
- **Stage 2 — Island content:** deterministic dock shelf; blueprint sets per type/biome; jigsaw decoration (Boxed pattern); villagers + resident golems.
- **Stage 3 — Travel:** border proximity prompt + warp dialog; route graph; fuel table + hold consumption; warp execution with re-seat; charting.
- **Stage 4 — Economy & cargo:** BlueBook + modifiers + persisted drift; dock trade GUI (hold-only); bundle/chest-boat/expander progression; Vault.
- **Stage 5 — Interstice:** failure roll, mid-route placement, Ghasts, free re-engage, cleanup.
- **Stage 6 — Crime (3 sessions):** 6a reputation core; 6b customs & contraband; 6c police & bounties (+ nameplate, PlaceholderAPI).
- **Stage 7 — Player islands:** purchase + placement in reserved cells; standard island life.
- **Stage 8 — Post-MVP backlog:** stalls, chart data sales, intel, insurance, contracts, galaxies, Elder Guardian tier.

## 13. Environment & Verify-First

- **Toolchain (verified via Gusher — do not "upgrade" blindly):** Paper API `26.2.build.40-alpha` (Java 25 bytecode → build with JDK 25, compile release 21); BentoBox `3.18.1` provided (server runs 3.21.1-SNAPSHOT); MockBukkit has **no 26.2 release** — use locally patched `org.mockbukkit.mockbukkit:mockbukkit-v26.1.2:4.113.4-p262` (GushBlock's `scripts/build_patched_mockbukkit.py`) + the two Adventure shims + surefire `--add-opens` set. `org.bukkit.Sound` is an **interface** (no `valueOf`); config-supplied sounds resolve via `RegistryAccess`.
- **Verify at Stage 3:** Paper 26.2 dialog API surface (design docs reference the 1.21.6 API; confirm current package/builders).
- **Verify at Stage 0/1:** BentoBox unowned-island registration path (`IslandsManager.createIsland` without owner) and per-world portal suppression for the interstice.
- Test server: `/Users/ben/Minecraft/26.2` (jar → `plugins/BentoBox/addons/`). Manual checklists live in `TESTING.md`.
