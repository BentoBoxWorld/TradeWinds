# TradeWinds — Design Decisions & Open Questions
*Companion to `tradewinds-overview.md` (admin pitch) and `tradewinds-dev-plan.md` (stages). This captures what was decided, **why**, and what remains open. Intended as the seed for `TRADEWINDS_SPEC.md`.*

---

## Design principles (load-bearing insights)

These are the reasons the design works. If a future change breaks one of these, stop and reconsider.

1. **The fuel/cargo tradeoff is the central mechanic.** Everything competes for hold space. This only binds because **trading transacts exclusively against bundles and boat hold** — never player inventory, ender chests, or carried shulkers. Any feature that lets sellable goods bypass the hold breaks the game's spine.
2. **Risk symmetry between travel modes.** Warping costs fuel but is instant; its risk is interstice interdiction. Rowing is free but slow; its risk is the lawless open ocean (no protection, no police, PvP live outside island ranges). Neither mode should become strictly dominant.
3. **Scans, not prices, balance contraband.** Sugar has infinite trivial supply (sugarcane farms), so its price cannot be the control. The customs-scan risk is the cost; that is why sugar can stay lucrative and smuggling can be a skill-expressing playstyle rather than a tax.
4. **Crime pays you into danger.** Contraband sells only at less-safe islands; Fugitives are barred from safe-island markets entirely. The richest criminals are structurally pushed into PvP space where bounty hunters can lawfully take them. This one rule closes the entire crime/bounty loop.
5. **The seed is the world.** Island positions, existence, types, biomes, names, and route-graph edge costs must ALL derive deterministically from the single shareable seed — pure functions of (seed, position), unit-testable with no Bukkit dependency. If any of these drifts to runtime randomness, seeds stop being shareable.
6. **Generator-driven terrain, not pasted terrain.** Island landmass comes from the chunk generator (Poseidon-style noise × radial mask), so lazy generation is just chunk generation: no pop-in, no paste pacing. Blueprints/structures decorate; they do not create the land.
7. **Scarcity by dimension removal.** No End world → shulker shells are unobtainable → cargo expanders are a purchasable, cap-controlled money sink. Do not add End access.

## Decided — by system

### World & generation
- Islands at fully **random ocean positions** (not grid-spaced), sparse by design, seeded; rejection-sampled against a minimum separation so protection zones never overlap (spatial-hash grid lookup at placement time). Blank ocean between islands; some space reserved for purchased player islands.
- **Density floor near spawn** guarantees a starter cluster regardless of seed luck.
- Whole-island biomes set per column at generation; vanilla decoration provides theming. Frozen-ocean ice is a **feature**: boats run fast on ice, so icy approaches are high-speed lanes (loved by rowers and pirates alike).
- Base generators: AcidIsland-style ocean/ocean-floor (no acid) for both the overworld and the dedicated Nether-environment interstice world. No End.
- Unowned BentoBox islands with protection flags per **security band** (EVE model: safe → less safe → anarchic); no block place/break normally; island name (Elite-style procedural) announced on entry.
- Reference dimensions from AcidIsland framing: ~1000 range, ~400 protection radius, border shown at protection edge. Islands never within sight of each other.
- **Tech level** per island (default scale 1–7), seeded, correlated with island type (industrial skews high, agricultural low, with variance — "Advanced Agricultural" exists). Shown in the entry announcement and chart data: *"Esedaxe — Advanced Industrial (Tech 6), Safe."* Tech gates **shops only, never docking or use**: boat ranks sold up to TL × 3 (TL7 = all twenty), expanders sold only at top-tech islands. Tech also modulates prices — high-tech sells finished goods cheap and buys raw dear, low-tech the inverse — so the best routes are tech *differentials* and route planning reads type × tech × security off the chart. Starter cluster guarantees at least one ~TL3 island. Crafting deliberately bypasses shop tech gates: shop-with-tech is the money route to a big boat, finding rare wood is the exploration route.

### Travel
- Border is **passable**; rowing between/past islands is free. Reaching the border in a boat offers the warp dialog (Paper 1.21.6 dialog API); rowing through does nothing special.
- **Route graph** with seeded per-edge costs; default = Euclidean distance × multiplier; per-edge overrides allow cheap lanes and expensive frontiers without regenerating the world ("warp lanes" is the in-fiction excuse for cost/distance mismatch).
- Fuel value table (wood < coal < lava bucket); lava's non-stackability is deliberate tension (high energy, full slot). Fuel consumed from hold only.
- Warp: dismount → teleport → re-seat (proven AcidIsland `/ai` pattern); arrival just inside the destination border **on the bearing of the origin island**; nausea + blindness + configurable minor damage (lore: warping hurts; gates the under-equipped); portal particles + sound (no real portal blocks needed).
- **Charting:** warp dialog lists charted islands only. Chart by physically entering an island's range (free, slow) or buying chart data (fast, money sink — post-MVP good). Starter cluster pre-charted. Charting doubles as the discovery layer over lazy generation.

### Interstice (warp failure)
- Configurable failure chance; on failure the player + boat land on interstice water **partway along the route**, with 1–3 Ghasts spawned.
- **Re-engaging to the original destination is always free** — the fuel was spent at engagement; failure is a detour, never a fuel loss. This rule is what makes stranding impossible; do not remove it.
- Ghast-vs-boat combat is intentionally survivable (fireballs can be batted back).

### Cargo & progression
- Start kit: boat + 1 trading bundle (a bundle = one stack's worth of capacity). Up to 3 bundles → chest boat → lore-renamed shulker **cargo expanders**, purchased only, price ~doubling per unit, configurable cap. Long expansion runway is intentional: maxed boats carry fortunes and are worth defending or attacking.
- Boat owner recorded in entity PDC from day one (theft, persistence, chunk-unload questions all hang off this).

### Economy
- Everything in Minecraft is tradeable. **BlueBook** (`~/git/bluebook`) supplies base prices; modifiers from island type, biome, security band, and per-island stock/price drift persisted via the BentoBox database. Players may sell found/crafted items through the same market.
- Margins scale with danger: the best prices live in the risky bands.

### Salvage & free trading (decided 2026-08-03)
The scavenger/farmer/pirate has loot and nowhere to put it. Ports buy almost
anything, but **shallowly**: price decays as you sell in, recovers over time, so
a stack of diamonds is several ports' worth of business rather than one payday.
This is the existing stock-drift machinery (`IslandDataManager`, `TWIslandData`,
`driftScale`/`stockDecayPerHour`) applied to found goods — the rate limit is
what stops an infinite farm faucet outcompeting the trade game.
- **Drift must be value-weighted, not unit-weighted.** Ten diamonds should move
  a port's prices far more than ten wheat; `adjustStock` currently counts units.
  Model it as the trader's **purse** — coins spent on a category, replenishing —
  which is also how players naturally describe it.
- **Salvage gets its own stock bucket** (not `MISC`) and a steeper discount than
  proper trade goods. Otherwise dumping junk craters the legitimate price
  gradient in that category, which is a griefing vector on a small server, and
  piracy out-earns honest trading.
- **Tech level gates what a port recognises**: a TL1 hamlet has no use for
  netherite. Loot therefore has a *destination*, which is a route.
- **NBT is preserved and priced.** A worn bow, a Potion of Strength II and a
  Silk Touch pick are not their base materials. `PriceEngine` already applies
  durability and can weigh enchantments (`getEnchantmentValue`, currently
  unwired); potions need `PotionMeta` handling. The hold (`Map<String,Integer>`)
  and the `Material`-typed `MarketService` signatures are what throw NBT away —
  both must carry ItemStacks. Breaking data-model change: wipe on deploy.
- **The hold becomes two-way for player-loaded goods.** This narrows the
  load-bearing "cargo leaves only by sale or destruction" rule, which now
  applies to **trader-bought cargo only** — that is what preserves cargo
  commitment and keeps the hold from being an off-market transfer channel.
  Player-loaded salvage can be withdrawn. Track provenance per stack and never
  merge across it, or the flag becomes ambiguous.
- **Sold goods resurface for players to buy**, but only notable items (enchanted,
  named, above a value threshold), a slot or two per port, with a TTL — and they
  surface at a *different* island than the one they were sold at ("the trader
  shipped it on"). Unpredictable destination is what stops alt-account
  laundering turning port shelves into a global auction house.
- Price discovery is a **logbook**, not an oracle: prices cannot be shown for
  ports you have not visited, and the chart remembers only what you saw and how
  long ago (Elite/TradeWars precedent). At the counter, show the live price *and
  the depth* — "this port will take ~40 more before the price falls" — which is
  the number the player actually needs. Purchasable market reports at high-tech
  ports, radius and freshness scaling with tech level, give TL a role beyond
  goods; plaza rumours are the cheap flavour version.

### Contraband & customs
- Master **config gate disables all illegal-goods mechanics** for family servers; on by default.
- Contraband: **sugar** (never referred to as drugs, in code, config, or locale). Villager "passengers" in boats sellable at less-safe islands only (how the player boats a villager is their problem — emergent by design).
- **Scan on every entry** to island space — rowing or warping in alike. Chance scales with security band (high-sec scans often; anarchic never). Per-island scan cooldown prevents re-entry scumming.
- Detection starts a **chase, not an instant fine**: "customs patrol dispatched," police launch from the island, and the ~400 blocks of water are the smuggler's decision window — flee across the border, fight, or **jettison** the contraband (dumped items float, lootable by anyone: emergent piracy). Caught = police land a hit or close to proximity while contraband is aboard → confiscation + fine + rep loss.
- Fleeing a detection **flags you at that island** for a cooldown: re-entry skips the roll and launches police immediately.

### Reputation, police, bounties
- Single global score, named bands (working proposal: Upstanding / Clean / Offender / Wanted / Fugitive), all thresholds and penalties configurable. Villager gossip API hooked for organic price penalties on the offended island.
- Recovery, three speeds: slow decay toward zero during clean play (crimes shadow you for sessions, not minutes); paying fines (capped — money buys you out of *Wanted*, not into virtue); positive acts (contracts/donations, post-MVP) above zero. Positive rep must buy something (better prices, lower scan odds) so *Upstanding* is a pursued status.
- **Wanted**: PvP damage override anywhere, growing bounty, police response at civilized islands. **Fugitive**: shoot-on-sight and barred from safe-island trading (see principle 4). Killing a wanted player pays the bounty with no rep penalty — bounty hunting is lawful work.
- **Bounty nameplate** (config option): a wanted player's bounty is displayed with their name over their head — a target for hunters, a badge of infamy pirates will cultivate. Implementation: per-player scoreboard team suffix, only applied when bounty > 0 (clean players show nothing); PlaceholderAPI placeholder exposed so nametag plugins (TAB etc.) can render it themselves instead of conflicting. Note: sneaking hides nameplates on foot, but players in boats cannot sneak — traveling pirates are always visible.
- Police mobs by mobility, not just element: **Phantoms pursue** (fast; combustion cancelled or day patrols burn at dawn), **Guardians hold the sea perimeter** (slow swimmers who will never catch a boat — they are area denial around the island, not chasers), **Golems** handle anyone who lands. All PDC-tagged, ticking `setTarget` reassertion, **zero drops** (or wanted players become a crime-powered iron farm). Pursue within the protection zone, break off when the target crosses the visible border (escape you can see), despawn beyond island range or on unload.
- Per-band knobs: scan chance **and** response size — a mid-band island may scan rarely but respond hard. Route planning with contraband should be map-reading skill.
- Anti-bait guard for innocent-kill penalties: direct kills only; prior damage from the victim exempts. Log edge cases, tune later; do not over-engineer pre-MVP.

## Proposed but not yet adopted (needs Ben's sign-off)
- Concrete reputation numbers: −1000..+1000 scale; hit villager −5, kill villager −25, kill police −15, kill innocent −100, scan-caught −30 + fine, slave sale −20; decay ~+1 per 15 min clean active play; band thresholds +250 / 0 / −200 / −500.
- **Regional density = civilization**: occupied-position density higher near spawn, thinning outward, with security band correlated to local density (clusters civilized, isolated islands anarchic). Makes the map itself communicate risk.
- Wanted players suffer a higher warp-failure chance (crime feeds the interstice ambush meta).
- Interstice is a **shared** world (enabling warp-space interdiction/ambush — very Elite) rather than instanced pockets.
- Post-MVP: stall rental on NPC islands for the player market (goods join the island's trade pool; rent as sink; stall location matters via the route graph); insurance (hull/expanders insurable, cargo never); "last seen" wanted-player intel sold at trade posts; Elder Guardian as the top-tier wanted response (its fatigue aura hits innocents — `EntityPotionEffectEvent` cancellation is possible but fights the mob's design, hence demoted from standard police).

- **Missions as NPC requisitions** (Ben, 2026-08-03 — the intended shape for the
  whole missions system, not just delivery jobs): a port posts a standing order —
  *"Baker's Reach wants 200 wheat at 40/unit, expires in 3 days"* — seeded from
  the island's type, tech level and current stock, on a board at the plaza. The
  player fills it in whole or in part, at a price fixed above the drifting market
  rate, and the reward is the premium plus reputation.
  This is deliberately the **5% of an EVE order book that fits a 20-player
  server**: it gives the farmer a goal and the scavenger a destination, and it
  needs no escrow, no player-placed orders and no offline value transfer — the
  counterparty is the island. Player-placed buy orders were considered and
  rejected for now (an order book with three participants is a waiting room, not
  a market, and "collect your goods from the trader" implies a warehouse, which
  breaks the boat-is-the-hold rule). Revisit only if a server ever has the
  population to support it, and then at spawn only.
  Generalises to every mission type: bounty contracts, passenger runs, courier
  jobs and salvage recovery are all "a board posts a job, you sail, you're paid."

## Open questions
1. **Name — RESOLVED: TradeWinds** (one word, house style like BSkyBlock/AcidIsland; repo `TradeWinds` under BentoBoxWorld). "Trade Wars MC" rejected — the TradeWars 2002 mark is still live, and "Wars" mis-signals a trading game; Sandlot's Tradewinds series is defunct and the phrase is generic.
2. **Poseidon internals:** can a radial positional mask multiply into its existing density tweaks, or is it cleaner to lift its sea-level/decoration handling into a fresh generator with the mask native? (First real Stage 1 task.)
3. **Dock placement:** deterministic terraformed shelf at a fixed bearing (predictable, teachable) vs. jigsaw terrain-adaptive placement (organic) — current lean: deterministic dock, adaptive decorations.
4. **Border interaction:** "click the wall" needs packet-level handling of client-side blocks; proximity trigger + action-bar prompt is cheaper and reads the same. Pick one; Border must run in passable/visual mode regardless.
5. **Boat loss rules:** what drops on boat destruction / player death? Full expander drop makes trader-piracy the apex game (very EVE) but is brutal; insurance is the counterweight. Undecided.
6. **Interstice PvP rules** if shared: can hunters lawfully take non-wanted players there, or is interdiction itself a rep crime?
7. **Scan/flag tuning:** scan cooldown length, flee-flag duration, "caught" proximity radius.
8. **Galaxies architecture:** multiple configured addon instances (AcidIsland/BSkyBlock coexistence model) vs. a world manager inside one addon. Deferred, but keep the world reference abstracted from day one.
9. **Villager shoreline safety:** golems can't path onto water; keep villagers inland behind the docks so drive-by boat raids don't trivialize the police, or add a ranged deterrent.
10. **Reputation numeric tuning** (see proposal above) and whether reputation is global or per-galaxy once galaxies exist.
