# TradeWinds — Salvage & Free Trading Plan

**Normative** for the salvage economy, the NBT-aware hold, and price discovery.
Where this document and `TRADEWINDS_SPEC.md` disagree on those subjects, this
one wins. It sits alongside `tradewinds-hold-plan.md`, which remains normative
for boat identity, ownership and capture.

> **Status: all seven phases delivered 2026-08-03.** See `docs/PROGRESS.md` for
> what each landed, the deviations, and the traps hit. Two things were
> deliberately not built: plaza rumours (Phase 6 - they duplicated the logbook)
> and the chart-hologram price display (the logbook surfaces via `/tw prices`).
>
> **Phase 6's logbook and harbour reports are PARKED behind
> `economy.price-logbook-enabled` (default false)** - ruled not required for MVP
> after playtest (2026-08-03). Before re-enabling, fix:
> - The unfiltered `/tw prices` "best" column compares **absolute** prices, so
>   the priciest good wins everywhere - every port read "Gems $850". Best must
>   be relative to book value (or the unfiltered view dropped for per-category
>   only).
> - The category argument has no tab-complete, and nothing tells the player what
>   the categories are.
> - Buying a report gives one chat line: no reach shown before paying, no list
>   of covered ports after. A $500 purchase should visibly land.
> - Consider folding the logbook into the chart holograms instead of a command.
> Depth-at-the-counter (the sell tooltip) is NOT parked - it works and stays.

## The problem

A player scavenging islets, farming, or taking prizes at sea accumulates goods
with nowhere to go. Today the hold will accept them, and `MarketService.sell()`
will sell anything the price engine can quote — there is no whitelist — but:

1. `PriceEngine` cannot price mob loot at all (no crafting recipe, not in the
   80-entry config table), and correctly refuses ingot↔block cycles. Unpriceable
   goods have no sale route, so destruction is the only exit.
2. NBT is discarded on the way in. The hold is `Map<String, Integer>`, so an
   enchanted sword becomes `DIAMOND_SWORD` and a nearly-broken pick sells for
   the price of a mint one. `MarketService.basePrice(Material)` narrows to a
   material before the engine — which already takes an `ItemStack` — sees it.
3. Cargo is one-way, so the hold cannot be used as a hold while mining an islet.
4. Stock drift counts **units**, so ten diamonds and ten wheat hit a port
   identically. A stack of diamonds should be several ports' worth of business.

## Shape of the answer

Ports buy almost anything, but **shallowly**: the price decays as you sell in and
recovers over time. That rate limit is what stops an infinite farm from
outcompeting the trade game — the answer to "I have 2,000 cobblestone" becomes
"visit five ports", which is travel, which burns fuel and meets customs.

The game is not live anywhere, so no migration or compatibility work is in
scope. Wipe worlds and databases freely.

---

## Phase 1 — The trader's purse (value-weighted drift)

Drift becomes coin-denominated: a port has spent *money* on a category, not
received *items*. This is the model players already describe out loud, and every
number tuned later depends on it, so it comes first.

- `IslandDataManager.adjustStock(spec, category, units)` → takes the transaction
  **value**. The two call sites are `MarketService.sell()` and `buy()`.
- `economy.drift-scale` (500 units) → coin-denominated and renamed;
  `economy.stock-decay-per-hour` (50) → coins per hour. Retune both: a
  full boatload of cheap bulk should approach the `drift-min` 0.7 floor, and
  recovery should be hours, not minutes.
- `decayed()` and `driftFactor()` are already pure and unit-tested — extend the
  tests rather than adding new machinery.

**Acceptance:** selling 10 diamonds visibly moves that port's prices; selling 10
wheat barely does. Same coin value moves them the same amount.

## Phase 2 — Price coverage, and a salvage bucket

- **Audit first.** A headless test enumerating every `Material` through
  `PriceEngine`, reporting priceable vs not. This produces the actual gap list
  instead of a guess; check the report into `docs/`.
- Add config base prices for what cannot be derived — mob drops (bone, string,
  gunpowder, leather, ender pearls, blaze rods…) and raw drops whose only
  "recipe" is a block cycle.
- **Salvage is anything that is not a recognised trade good in the game** — that
  is, a material appearing on no island type's produce or demand list. Salvage
  sales drift against a single per-island `SALVAGE` purse and never touch the
  trade categories, so dumping junk cannot crater the legitimate price gradient
  (a griefing vector on a small server). Existing trade goods keep their present
  categories and behaviour exactly.
- New knob `economy.salvage-discount` (~0.35–0.4 of book). Salvage must pay
  worse than proper cargo, or piracy and farming out-earn honest trading.

**Acceptance:** every material either has a price or is on a short, deliberate
refusal list. Selling 2,000 cobble at a mining port does not move the price of
its iron.

## Phase 3 — Tech gates what a port will handle

A TL1 fishing hamlet has no use for netherite. This gives loot a *destination*,
which is a route, which is gameplay.

Implement by value rather than a per-material tier table:
`economy.max-item-value-per-tech-level × techLevel` is the most a port will pay
for a single item; above that it declines. One knob, no table to maintain, and it
scales automatically as prices are retuned.

**Acceptance:** an enchanted netherite sword is refused at a TL1 port and bought
at a TL7 one, with a locale message that says why.

## Phase 4 — The NBT hold

The significant refactor. `PriceEngine` already applies durability and already
has `getEnchantmentValue()` (written, documented, and never called) — so most of
the valuation exists and is simply unreachable.

- `BoatHold.contents`: `Map<String, Integer>` → `List<ItemStack>`, **one entry
  per slot**, exactly like a real inventory. `expanders`:
  `List<Map<String, Integer>>` → `List<List<ItemStack>>`. **Fuel stays
  material-keyed** — fuel value is per material and genuinely fungible.
- Persistence is free: BentoBox's `ItemStackTypeAdapter` is registered in
  `BentoboxTypeAdapterFactory` and round-trips full meta through Bukkit's YAML
  serializer. Note its `MAX_AMOUNT` 99 clamp — one stack per slot never trips it.
- Slot accounting gets *simpler and exact*: list size is slots used, and unique
  items each occupy a slot, which is what players expect.
- `HoldService`: the (Material, amount) API becomes ItemStack-based. Keep a
  Material convenience overload for the buy path and fuel.
- `MarketService.basePrice` / `playerSellsAt` / `playerBuysAt` / `sell` take
  ItemStacks. Wire `getEnchantmentValue()` in behind a config factor.
- Add `PotionMeta` handling to `PriceEngine`: a water bottle and a Potion of
  Strength II are both `POTION` today.
- `HoldGui` gets simpler — it already renders from a `stacks` list.

**Testing risk, and the mitigation:** under this MockBukkit setup `ItemStack`
meta does not work (the item factory is a bare mock, `getItemMeta()` returns
null), so NBT behaviour needs mocked ItemStacks with mocked `ItemMeta`, and the
YAML round-trip cannot be tested headlessly at all. Therefore **keep the
slot-packing arithmetic in a pure helper** that operates on counts and stack
limits with no Bukkit types, unit-test that, and cover serialisation in
`TESTING.md` as a manual check.

**Acceptance:** a worn bow sells for less than a mint one; a Silk Touch pick
sells above a plain one; a Potion of Strength II is not priced as a water
bottle; a hold full of distinct enchanted gear survives a server restart.

## Phase 5 — The hold becomes two-way

This **narrows a load-bearing rule**: "cargo leaves the hold only by sale or
destruction" now applies to **trader-bought cargo only**. That is what preserves
cargo commitment (speculate and you must find a buyer) and keeps the hold from
being an off-market player-to-player transfer channel. Player-loaded salvage may
be withdrawn.

- Mark bought cargo with a PDC key (`tradewinds:cargo`) on the stack itself. It
  survives serialisation, travels with the goods, and stays visible if a bug ever
  leaks one into a player inventory.
- **Never merge stacks across provenance**, or one bought diamond locks up the
  twenty you mined. Separate list entries; the list-of-stacks model makes this
  natural.
- `HoldGui` becomes interactive: click and shift-click to move goods between
  player inventory and hold, refusing marked stacks outbound, and refusing
  containers/vessels inbound as `HoldService.refuses()` already does.

**Acceptance:** mined cobble goes in and comes back out; bought cargo cannot be
withdrawn and says so; capacity is never exceeded; nothing duplicates across a
restart or a boat swap.

## Phase 6 — Price discovery

Two distinct questions, and only one is hard.

- **At the counter you know everything.** Show the live unit price *and the
  depth*: "this port will take about 40 more before the price falls", derived
  from purse headroom. This is the number the player actually needs — it tells
  them when to stop selling and leave.
- **Across the map you should not know.** A **logbook**: extend
  `TWPlayerData.chartedIslands` (already a `Set<String>`) to remember prices you
  have personally seen, per island, with a timestamp, and render them on the
  chart with their age. Elite and TradeWars precedent. Stale data being visibly
  stale is a feature; it makes a veteran trader *skilled* rather than merely
  rich.
- **Market reports** purchasable at high-tech ports, radius and freshness scaling
  with tech level — gives TL a role beyond what is on the shelf, and a reason to
  visit an industrial hub you are not trading with.
- **Plaza rumours** as the cheap flavour version: "they're paying well for metals
  two jumps north."

**Acceptance:** no price is visible for a port never visited; remembered prices
show their age; the sell page states the depth remaining.

## Phase 7 — Resale shelves

Goods sold to a port can resurface for other players to buy — the charm is
"someone dumped a Fortune III pick at Baker's Reach".

- **Notable items only**: enchanted, renamed, or above a value threshold. A slot
  or two per island, with a TTL.
- The item surfaces at a **different island** than the one it was sold at — the
  trader shipped it on. Unpredictable destination is what keeps port shelves from
  becoming an alt-account laundering channel or a global auction house.
- `TWIslandData` grows a `List<ItemStack>` with timestamps; bounded and swept.

**Acceptance:** a distinctive item sold at one port is findable at another and
not at the one it was sold to; shelves do not grow without bound.

---

## Out of scope, sequenced after

**Missions as NPC requisition boards** — see `tradewinds-design-decisions.md` and
Stage 8 of `tradewinds-dev-plan.md`. Phase 6's price discovery is the *pull*
side's natural companion, so missions come after it.

## Working order

Phases 1–3 are small, independent of the refactor, and playtestable immediately —
they are also where the *feel* of a shallow market gets tuned, which is worth
settling before committing to the hold rework. Phases 4 and 5 land together (a
two-way hold built on material counts would only need redoing). Then 6, then 7.

Every gameplay number goes in config from the phase that introduces it, all
player-facing text in the locale, and `docs/PROGRESS.md` and `TESTING.md` get
updated as each phase lands.
