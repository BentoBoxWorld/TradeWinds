# TradeWinds — Development Plan
### Staged implementation for Claude Code

Each stage produces a testable, playable increment. Stages are sized so a stage is one or a few focused Claude Code sessions, with clear acceptance criteria Claude Code can verify (unit tests where deterministic, manual test checklist where not). Reference code lives in `~/git`: BentoBox core, AcidIsland, Poseidon, Boxed, Border, BlueBook.

**Before Stage 0:** write `TRADEWINDS_SPEC.md` in the repo root (same approach as GUSHER_SPEC.md) capturing the full design, plus a `CLAUDE.md` pointing at the reference repos, build commands, and coding conventions. Every stage below assumes Claude Code reads the spec first.

---

## Stage 0 — Addon scaffold
**Goal:** an installable gamemode addon with an empty ocean world.

- Maven project extending `GameModeAddon`; config, locale files, `/tw` player and admin commands.
- Register the overworld plus a dedicated Nether-environment world (interstice) — both using an AcidIsland-style generator (ocean over ocean floor, no acid), Nether variant styled accordingly.
- No End world. Config skeleton for all later knobs.

**Accept:** addon loads alongside other gamemodes; `/tw` teleports to spawn in open ocean; interstice world exists and is inaccessible to players.

## Stage 1 — Seeded galaxy: placement, biomes, names
**Goal:** deterministic island layout from one shareable seed.

- Placement engine: seed → random sparse island positions (rejection sampling against minimum separation via the spatial-hash island grid), each with type (agricultural, industrial, …), security band, biome, and range/protection radius.
- Elite-BBC-style procedural name generator (token-pair digraphs), seeded.
- Chunk generator derived from Poseidon: terrain noise × radial falloff mask at island centers; pure ocean elsewhere; per-column biome from island type. Guaranteed starter cluster density near spawn.
- On first generation of an island's center chunk: register an unowned BentoBox island with flags per security band; announce name on entry.

**Accept:** unit tests prove same seed → identical positions/types/names across runs; rowing out reveals distinct biomed islands with no visible pop-in; islands persist in the database.

## Stage 2 — Island content
**Goal:** islands worth visiting.

- Deterministic dock/market shelf terraformed at a fixed bearing from center; blueprint sets per type and biome pasted there.
- Jigsaw/structure-command placement for decorative structures (Boxed pattern).
- Villager population with professions per island type; resident iron golems.

**Accept:** every generated island has a dock, market, villagers, and golems appropriate to its type and biome.

## Stage 3 — Travel: warp, fuel, charting
**Goal:** the core movement loop.

- Border addon integration in passable mode; proximity trigger at the border while boated opens the warp dialog (Paper 1.21.6 dialog API): charted destinations, fuel cost each, unreachable grayed out.
- Route graph: seeded edge costs, default Euclidean × multiplier, per-edge override config.
- Fuel item value table (wood, coal, lava bucket, …); consumption from trading bundle / boat hold only.
- Warp execution: dismount, cross-position teleport, re-seat in boat (AcidIsland `/ai` pattern); arrival just inside the destination border on the origin bearing; nausea + blindness + configurable minor damage; portal particles and sound.
- Charting: islands enter a player's chart by rowing into their range or (later) buying chart data; dialog lists charted islands only. Starter cluster pre-charted.

**Accept:** full row-out/warp-back loop works including cross-distance re-seating; fuel deducts correctly; uncharted islands never appear in the dialog; costs match the seeded graph.

## Stage 4 — Economy and cargo
**Goal:** buying low and selling high — the game becomes a game here.

- BlueBook integration for base prices; per-island modifiers from type, biome, security band; per-island stock and price drift persisted via BentoBox database.
- Trade GUI at the dock transacting **only** against trading bundles and boat hold.
- Cargo progression: start kit (boat + 1 bundle); up to 3 bundles; chest boat; purchasable lore-renamed shulker "cargo expanders" up to a configurable cap, price doubling per unit. Boat owner tag in PDC.
- Vault economy hookup; starter balance config.

**Accept:** profitable routes exist and differ by island type; items outside bundles/hold cannot be sold; expansion pricing and caps enforced; market state survives restarts.

## Stage 5 — The interstice (warp failure)
**Goal:** risk on the fast route.

- Configurable warp failure chance; on failure, place player + boat on interstice water partway along the route; spawn 1–3 Ghasts nearby.
- Re-engaging warp from the interstice to the original destination is always free (fuel was already spent).
- Interstice mob/PvP rules; cleanup of stale interstice entities.

**Accept:** failure rate matches config; stranding is impossible (re-engage always available); ghast fight is survivable in a boat.

## Stage 6 — Crime: reputation, customs, police
**Goal:** the law layer. Largest stage — split into three sessions.

**6a — Reputation core:** global score with named bands and configurable thresholds; listeners for villager harm, golem kills, player kills (wanted vs innocent), contraband sales; slow clean-play decay; fine payment at civilized islands; villager gossip hook for price penalties.

**6b — Customs and contraband:** contraband list (default: sugar; villager "passengers" flagged at less-safe islands only) behind a master config gate for family servers. Scan roll on any entry to island space (row or warp), chance per security band; detection message; caught = police reach proximity/land a hit while contraband aboard → confiscation, fine, rep loss; jettisoned cargo clears you; fleeing a scan flags you at that island for a cooldown.

**6c — Police and bounties:** police spawner per security band — Golems (land), Guardians (sea perimeter), Phantoms (pursuit, combustion cancelled); PDC-tagged, zero drops, ticking `setTarget` task; pursue within protection zone, break off at the border, despawn beyond island range or on island unload. Wanted players: PvP damage override anywhere, bounty ledger growing per crime, paid to the killer with no rep penalty; Fugitive band barred from safe-island trading. Bounty **nameplate display** (config-gated): bounty shown with the player's name via per-player scoreboard team suffix, absent at zero bounty; expose a PlaceholderAPI placeholder so servers running TAB/nametag plugins can integrate instead of conflicting.

**Accept:** each rep transition triggers the right consequences; smuggler chase (scan → flee/fight/jettison) plays out correctly; police never drop loot and never leak entities; bounty pays exactly once.

## Stage 7 — Player islands
**Goal:** ownership. Largely existing BentoBox machinery.

- Purchase command: sufficient balance → choose island blueprint → placement at a valid random position respecting minimum separation from all islands; standard island/team management, protection, and home warps thereafter.

**Accept:** purchased islands appear in open ocean, never overlapping any range; AcidIsland-equivalent island life works.

## Stage 7.5 — Salvage economy & free trading
Scavengers, farmers and pirates need a market; the hold needs to carry NBT and
work in both directions. Planned in full in **`tradewinds-salvage-plan.md`**
(normative): trader's purse (value-weighted drift) → price coverage + salvage
bucket → tech gating → NBT hold → two-way hold → price discovery (depth at the
counter, chart logbook, market reports) → resale shelves.

## Stage 8 — Post-MVP backlog (seeded, unscheduled)
- **Re-enable the price logbook + harbour reports** (`economy.price-logbook-enabled`,
  built but parked 2026-08-03): fix the absolute-price "best" column, add category
  tab-complete, make the report purchase visibly land. See the status note in
  `tradewinds-salvage-plan.md`.
- Player market stalls rentable on NPC islands (goods enter that island's trade pool; rent as money sink).
- Chart data as a purchasable good; wanted-player "last seen" intel for bounty hunters.
- Boat/expander insurance (cargo never insurable); boat destruction and death-drop rules tuning.
- **Missions, as NPC requisition boards** — "Baker's Reach wants 200 wheat at
  40/unit, 3 days", posted at the plaza, seeded from island type/tech/stock, paid
  above market plus reputation. This is the intended pattern for *all* mission
  types (delivery, bounty, passenger, salvage): the island is the counterparty,
  so no escrow or player order book is needed. See the design-decisions entry.
- Multiple galaxies as additional worlds; inter-galaxy warp as endgame.
- Elder Guardian boss response for the most-wanted tier.

---

### Working notes for Claude Code sessions
- Keep everything downstream of the seed **pure and unit-testable**: placement, names, biomes, route costs should be plain functions of (seed, position) with no Bukkit dependencies, tested headlessly.
- Isolate Bukkit-facing code behind small interfaces so listeners and tasks stay thin.
- Every config value in `config.yml` from the stage it's introduced — no hardcoded gameplay numbers.
- Manual test checklist per stage lives in `TESTING.md`; Claude Code updates it as features land.
