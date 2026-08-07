# TradeWinds — Interstice Resources Plan

*Drafted 2026-08-05 from design discussion with Ben. Normative for nether-item
sourcing and portal behaviour. Status: **BUILT** (same day) — N1, N2 and the
booty half of N3 shipped; deferred: magma cubes, secondhand-shelf seeding,
price spot-checks after play. See PROGRESS.md for implementation notes.*

## Goal

Nether-exclusive items must exist so beacons, potions and the rest of
late-game crafting are reachable — without an End world (never), without
breaking "entry to the interstice is by accident", and without new economy
plumbing (every target item already has a base price; the salvage loop
absorbs everything).

The interstice stays what it is — a hostile dark sea you are dropped into —
but a dropped sailor now has a reason to look around before taking the free
re-engage home. **Warp failure changes from punishment to opportunity.**

## Entry model (decided)

**Consolation-prize only.** Resources are reachable only when a failed warp
puts you there. The owed re-engage never expires, so you may linger, gather
and fight as long as you dare, then take the free jump. No deliberate-entry
mechanic ships with this plan; see Portals below for the gated future option.

Knock-on to check at implementation: `/twadmin warpfail` becomes the admin
door for testing all of this, and players will discover that Anarchic-band
warps (highest failure chance) are the "route" to nether goods — that is
intended: the most lawless water feeds the most dangerous market.

## Item coverage

| Item | Needed for | Source (this plan) |
|---|---|---|
| Nether wart | every real potion | wart shoals (1) |
| Blaze rod / powder | brewing stand, fuel, strength, magma cream | blaze pickets (2) |
| Ghast tear | regeneration | already drops — interstice ghasts are not police |
| Glowstone dust | amplifier II, lighting | ceiling clusters (3) |
| Quartz | comparators, observers, building | spire seams (4) |
| Magma cream | fire resistance | slime + blaze powder craft; magma cubes (6) |
| Soul sand / soul soil | wither, soul fire | already the interstice floor |
| Wither skeleton skull | nether star → beacon | wither watchtowers (5) |
| Netherrack / nether bricks | building, watchtower salvage | interstice crust (already); towers (5) |
| Crimson/warped wood, shroomlight | building | groves on grand shoals (1) |
| Netherite scrap / templates | netherite gear | LOOT ONLY: tower chests, encounter booty, secondhand shelves — never mineable |
| Dragon's breath, ender pearls, shulker shells, echo shards | lingering potions, endgame | TRADE ONLY: "goods from beyond the horizon" — rare secondhand/booty items, never gatherable. No End, ever. |

Already covered in the overworld and unchanged: redstone, gunpowder, spider
eye, sugar, rabbit's foot, pufferfish, golden carrot, phantom membrane
(insomnia phantoms), glass, obsidian.

## Sources

All seeded, deterministic, pure functions of (seed, position) in the
`galaxy`-package style — no Bukkit imports in the geometry, unit-tested
headlessly. Numbered for reference; suggested phases at the end.

1. **Wart shoals.** Small soul-sand banks breaking the interstice surface —
   the interstice's wild islets, drab and low. Nether wart grows on top;
   harvest and replant like any crop. A rare **grand shoal** (config chance)
   carries a crimson or warped grove: stems, wart blocks, shroomlights.
   - Config: `interstice.shoal-chance`, `shoal-grid`, `shoal-radius`,
     `grand-shoal-chance`.
2. **Blaze pickets.** A config fraction of the existing braziers grow a
   nether-brick turret holding a **blaze spawner**. Renewable rods, fought
   from a boat, contained by water on every side.
   - Config: `interstice.blaze-picket-chance` (fraction of braziers).
3. **Glowstone ceiling clusters.** Seeded glowstone growths hanging from the
   interstice's netherrack lid — visible for hundreds of blocks, so light
   becomes navigation and destination at once. Mining them is boat-ladder
   engineering, which is the right price.
   - Config: `interstice.glowstone-cluster-chance` (per ceiling chunk),
     `glowstone-cluster-size`.
4. **Quartz spire seams.** The basalt outcrops and rift walls carry quartz
   ore (and magma blocks). The flat seabed stays barren — "nothing worth
   mining" survives where it matters; the value stands up where you can see
   it.
   - Config: `interstice.spire-quartz-chance`.
5. **Wither watchtowers.** Rare nether-brick ruins standing out of the sea —
   the interstice's only structure. Wither skeletons spawn within the walls
   (spawner or structure-bounded natural spawns — decide at implementation;
   spawner is simpler and police-rule-proof). A loot chest may hold netherite
   scrap or a smithing template (config table). Three skulls plus floor soul
   sand = a wither summoned over open water, far from every build — the best
   possible arena for it.
   - Config: `interstice.watchtower-grid`, `watchtower-chance`,
     `watchtower-loot-table`.
6. **Magma cubes.** Natural spawns on the basalt around braziers (small
   sizes; the craft in (2) already covers fire resistance, so this is
   texture, not necessity).

**Explicit non-sources:** no ancient debris anywhere ("loot has a
destination, which is a route" — mineable debris collapses the netherite
trade); no piglin bartering (cut for scope; revisit only if a use appears);
no End portals, stronghold, or elytra, ever.

## Nether portals (decided 2026-08-05)

Reality check from play: ruined portals are common ocean structures (three
found in one session), so players WILL complete and light them, and a silent
non-answer reads as a bug.

- **Lighting a portal: refused, with lore.** The flint-and-steel moment gets
  a message — *"the sea between seas does not open to fire and stone"* — so
  the refusal is a world rule, not a missing feature. (Today the seal is
  silent: `IntersticePortalListener` + `make-nether-portals: false`.)
- **Ruined portals stay what they are: salvage.** Obsidian, crying obsidian,
  the gold loot — cargo for the hold, nothing more.
- **Rejected: portal as free/paid warp terminal to trading posts.** It
  bypasses the boat, the fuel economy and the border-crossing ritual — the
  three things that make travel the game. If a shore-side warp is ever
  wanted, it must price exactly like the border warp (boat at island, fuel
  from the hold), at which point it adds nothing the border does not.
- **Backlog (Stage 8, config-gated OFF): the portal as deliberate entry.**
  If deliberate interstice entry is ever wanted, the player-built portal is
  the natural door: expensive to build (obsidian is priced), one-way IN
  (arrive in the interstice near the scaled position, boat alongside like a
  warp arrival), exit only by the standard free re-engage. Gate:
  `interstice.portal-entry`, default false. This preserves
  entry-by-accident as the shipped default while giving the portal a real
  answer if a server wants it.

## Economy notes

- Everything gatherable above already has a base price
  (`economy.base-prices`) — wart, rods, tears, glowstone dust, quartz, magma
  cream, soul sand, netherrack. No new price plumbing.
- Watch two prices at implementation: NETHER_WART (30) and BLAZE_ROD (200)
  become *farmable* — the drift/absorption model already damps dumping, but
  spot-check a wart-farm dump against a TL5+ port.
- Nether goods are natural additions to the encounter booty table and the
  secondhand shelves at high-tech ports, which keeps the trade route open
  for players who never fail a warp.

## Suggested phases

- **N1 — gathering:** wart shoals + glowstone ceiling + quartz seams + the
  portal lighting message. (All generation + one listener message; no new
  mobs.) Clean-slate note applies: interstice world folder must be deleted.
- **N2 — fighting:** blaze pickets + magma cubes + wither watchtowers +
  loot tables. (Spawners, structure spawns, loot.)
- **N3 — polish:** grand-shoal groves, booty-table additions, secondhand
  seeding, price spot-checks.

## Testing notes (for TESTING.md when built)

- A rigged warp failure lands you within sight of at least one resource
  (shoal density should make this near-certain; if not, the consolation
  prize is a lie).
- Re-engage still free and offered after an hour of gathering.
- Blazes/wither skeletons never leak beyond their pickets/towers; police
  no-drop rule untouched (these are not police).
- Lighting a completed ruined portal prints the lore message and does NOT
  open a portal, in both worlds.
- Clean slate: regenerate `tradewinds_world_nether` after N1.
