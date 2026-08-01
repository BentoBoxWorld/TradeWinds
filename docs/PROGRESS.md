# TradeWinds — Progress Log

What is done, and pitfalls hit on the way. Newest stage first. Read
`TRADEWINDS_SPEC.md` for requirements; this file records reality.

## Stage 6a — the law: port flags, reputation, fines (2026-07-31)

**The flag audit, finally done properly.** Three separate playtest bugs (no boat
interaction, no chest boat, no workbench) had the same root cause: BentoBox
protection flags default to MEMBER rank, and **nobody is ever a member of an
unowned island**. Counting it out: of 96 protection flags only 6 default to
visitor rank, so 90 things were denied to everyone on every trading island, and
I had been allow-listing them one bug report at a time.

So `PortFlags` inverts the policy. A trading island is a **public market**:
everything is allowed at visitor rank except an explicit deny list, and that
list is now the design statement, grouped by why -
1. anti-grief (the plaza and dock are the only hand-built terrain in the world),
2. **the two economies** - an island's crops, stores, hive and livestock are
   denied because harvesting them would mint money outside trade margins
   (spec 5.0), which no amount of playtesting would have surfaced as a *bug*,
3. `TRADING`, so a right-click on a villager cannot bypass the market and the
   hold (principle 1) - the one I am most glad the audit caught,
4. livestock and residents, 5. portals.

It walks `Flags.values()` at runtime rather than naming an allow list, so a flag
added by a future BentoBox is open at a port on day one instead of silently
locking something. Admins get `bands.port-denied-flags` / `port-allowed-flags`
either way.

**Reputation core.** `ReputationScale` (pure, headless) owns the number line and
the bands; `Standing` owns what each band *means* to the world - hunted, lawful
target, barred from safe trade - so nothing downstream tests thresholds by hand.
`ReputationService` records crimes, fires `TWReputationChangeEvent`, and runs
decay. Decay is credited only while **online and in a TradeWinds world**, so a
week offline does not launder a reputation, and it walks toward zero from either
side without overshooting.

Three speeds of recovery, per spec section 7: slow decay, `/tw fine` at a port,
and positive acts later. A fine returns you to **Clean and no further** - money
buys you out of Wanted, not into virtue - and a Fugitive is refused at SAFE
ports, so buying your way back costs you a trip into danger. Principle 4 in
reverse.

`CrimeListener` carries the anti-bait guard: the innocent-kill penalty is
forfeit if the victim struck first within 30s, or standing near a wanted player
and dying at them would be a weapon.

**Pitfalls:**
- I audited the flag list against BentoBox **HEAD**, but the compile target is
  3.18.1, which has a different flag set (no `FISHING`). Runtime iteration over
  `Flags.values()` handles the drift; naming flags in code does not. Check the
  jar, not the source, before naming a constant.
- `getPlayerStanding` had to take a `User` - a standing is player-facing text
  and must be translated for the *viewer*, not the subject.
- 182 tests green. 6b (customs/contraband) and 6c (police/bounty payout,
  nameplates) still to come; the bounty ledger already clears on payout so it
  can only ever pay once.

## The sea itself: seabed shape, vanilla structures, denser islets (2026-07-31)

Ben, after covering 130 regions: "I have not found a single islet. Nothing...
the sea floor is really barren and repetitive." Both were real, and they had
different causes.

**Islets were generating — just too rarely to meet.** The math was sound (a
probe over the live seed found 357 in a 40,000-block square, the nearest 977
blocks from spawn) but at grid 1200 x chance 0.3 the mean distance to one was
well over a kilometre, and each was a fixed 70-block cone, so a whole voyage
could pass between two of them and see neither. Now grid 900 x chance 0.55,
and every islet rolls its own radius (0.55x-1.55x the mean) and its own
height, so the sea holds sandbars through to proper little islands. Measured
on the live seed: **mean 544 blocks to the nearest islet, worst case 1,376**
(was well over 2,000). `testIsletsAreFindable` now asserts < 1,200 from four
far-flung points; the old assertion allowed 2,500, which is why it never
caught this.

**The barren floor was one flat Perlin field**, and worse, it was the wrong
tool: the floor is not scenery, its depth is what picks the ocean biome, and
the biome is what tells vanilla where monuments, ruins and coral belong. So
the seabed moved into the pure galaxy package (`Seabed`, `SeabedConfig`) where
terrain and biome read the same field:
- **Basins** (2,200-block lattice) take the floor from sunlit shelf to abyssal
  plain. Depth over the deep-water threshold switches the biome to the
  `deep_*` variants — which is the whole unlock for ocean monuments.
- **Relief** (220) rolls dunes and hills over it.
- **Rifts**: ridged noise (value noise folded about its midpoint) cuts narrow
  wandering canyons up to 26 blocks below the surrounding floor.
- **Seamounts** rise off the deeper plains only, and are capped so they never
  break the surface — the galaxy's islands stay the world's only land.
- **Island shelf blend**: near any island or islet the natural floor eases to
  a standard 18-block shelf, so an island that lands over an abyss still sits
  in shallow water and still clears the waves by the same amount. A bonus:
  the shoreline radius is now exact rather than cut about by floor noise.

Measured over 53,000 open-sea samples on the live seed: depths spread 8-63,
**39.5% deep water**, and all nine ocean biomes present (frozen through warm,
shallow and deep).

**Vanilla now furnishes the sea.** `make-caves` and `make-structures` default
true — carvers cut caves under the floor, and structure placement supplies
shipwrecks, ocean ruins (warm and cold, by biome), monuments (deep basins),
buried treasure and trial chambers (in the rock under everything). The hybrid
is: we own the shape of the floor, vanilla furnishes it. Structures are
suppressed per-chunk over trading islands via the per-chunk
`shouldGenerateStructures(WorldInfo, Random, int, int)` overload, so nothing
drops through a market plaza; wild islets are fair game.

Islets also gained a sandy beach ring at the exact waterline (which is what
lets vanilla beach shipwrecks and bury treasure) and a 6% chance of being
**mushroom fields** — mycelium, mooshrooms, no hostile spawns.

**Breaking the circle (same day, from a playtest screenshot).** Ben: "almost
comically circular". Two causes, both mine:
1. The land mask is a cosine of *true* distance from the center, which is a
   perfect disc by definition. Fix: warp the distance with a seeded noise field
   before the mask ever sees it (`shapedDistance`) - the same mask then draws
   bays and headlands, for one noise lookup. The warp scales with the island's
   radius, so a sandbar and a trading island are equally ragged for their size.
2. Worse, the island shelf blend I had just added levelled *everything* near
   land to a flat shelf, which is what made the pale shallow ring a perfect
   circle too. Now only the **basin** is levelled toward the shelf - that is
   all the "islands always break the surface" guarantee needs. The rolling
   relief carries on across the shelf at half strength; rifts and seamounts
   fade out (a canyon through an anchorage helps nobody).

Land also got `hilliness`: a noise multiplier on the lift, so an island is
hills and hollows rather than a smooth dome. Multiplied into the lift so it
fades at the shore instead of calving fragments off into the sea.

Everything that reads the island footprint had to move onto the same warped
distance or the biome would have drawn a circle over the ragged terrain:
`islandAt`, `biomeKeyAt`, the icy approach ring, `shelfBlendAt`, `isletNear`.
Neighbour searches widened by `searchMargin()` to match, or a column in a
headland is missed.

Beaches were rewritten while I was there. They were a geometric ring at an
analytically exact shore radius - which only worked while the shelf was dead
flat. Now `isShoreAt` just asks the finished terrain whether it is 1-4 blocks
above sea level, so the beach follows the real waterline however ragged the
coast, and needs no geometry at all.

Verified by rendering islands as ASCII height maps before deploying: outlines
are lobed, the spawn island's quay still runs out to open water (pier end at
y=59, sea level 70). `galaxy.coast-roughness` / `galaxy.island-hilliness`
expose both, and `ShapeConfig.ROUND` restores the old coins.

**Pitfall (twice now):** averaging fbm octaves pulls a field toward its middle.
The first coast warp used `Noise.fbm` and lost most of its range - the coast
came out nearly round anyway (reach 31-46 on a radius-73 islet). Two explicit
full-range `Noise.at` octaves instead. Same trap as the basin field earlier the
same day; worth remembering that fbm is for *shape*, not for amplitude.

**Sealing the carvers (same day, from a playtest screenshot).** Turning vanilla
caves on opened dry craters straight through the sea floor - carvers have no
idea there is an ocean overhead, and a generated chunk gets no block updates,
so nothing ever flows in to fill what they cut. Ben pointed at Poseidon, which
hit this and fixed it in `generateCaves`: the API guarantees vanilla's carvers
run *before* that hook and that the ChunkData handed to it already contains
their work, so it is the place to repair them.
Poseidon fills every carved space under the sea with rock. TradeWinds narrows
that, because caves under the seabed are wanted: it recomputes the true floor
top (pure function, so it is exact) and puts back only a 5-block **crust** -
air at or above the floor becomes water again, air just below becomes the same
sediment or rock the floor is made of, and anything deeper stays hollow. Caves
survive, with a sea floor over them; break in from below and they flood, which
is what a player expects. Cave mouths in an island's flank above the waterline
are left alone.

**Pitfalls:**
- The test helper reads the topmost *solid* block, which is `floorTop - 1` in
  generator terms (blocks are written for `y < floorTop`). Worth remembering
  when writing terrain assertions.
- Stacked fbm octaves pull toward the middle: the first cut gave only 16
  blocks of depth variation across a long transect — still barren. The basin
  field is now stretched about its midpoint and eased, restoring real shelves
  and real abyss.
- Bulk-filling base rock up to the chunk's *lowest* floor top left dead flat
  chunks (a market plaza, or level sea floor) as bare stone: every column's
  surface loop had zero iterations. The fill now stops 8 blocks short. Caught
  by `testPlazaAndDockTerraform` — worth having kept that test honest.
- The seabed hangs off the **galaxy** seed, not the world seed, so one number
  still decides the whole world (spec principle 5). `testDeterminism` had to
  stub a second engine to prove it.
- Stored config values beat changed code defaults *again* (third time):
  `make-structures`/`make-caves` and the islet knobs on the test server were
  still the old values. Live config updated by hand.
- 164 tests green.

## All player-facing text moved into the locale (2026-07-31)

Ben's correction: never do MiniMessage conversion by hand - BentoBox's User
API is locale-aware and does it (`getTranslationAsComponent`,
`sendMessage`). Auditing for that exposed a bigger problem of my own making:
dialog titles, buttons and tooltips, boss bar text, hologram labels, item
names and lore were all HARDCODED ENGLISH in code, so none of it could ever
be translated.
Everything now goes through the locale: ~50 new `tradewinds.ui.*`,
`tradewinds.hud.*`, `tradewinds.hologram.*` and `tradewinds.item.*` keys,
fetched with `user.getTranslationAsComponent(...)`. The charted action bar
uses the locale's `[actionbar]` marker so BentoBox delivers it, rather than
calling sendActionBar around a manually parsed string.
Audit is clean: no `Component.text("...")` literals and no MiniMessage
references remain in src/main. Gotcha: the no-variable
`getTranslationAsComponent(key)` call is ambiguous between the String... and
TagResolver... overloads - pass `new String[0]`.
Convention recorded in CLAUDE.md, including the [actionbar]/[title]/[sound]
markers, which also offer a tidier route for the trade sounds later.
154 tests green.

Ben: MiniMessage is the standard from now on. en-US.yml converted from legacy
ampersand codes to closed MiniMessage tags, matching core's own locale style
(`<red>text</red>`). Also fixed ChartingListener, which wrapped a translated
string in `Component.text` for the action bar - that would have printed the
tags literally; it now deserializes with MiniMessage. Convention recorded in
CLAUDE.md: locale strings are MiniMessage, and any Component-taking sink
(action bars, titles, holograms) must deserialize rather than wrap.

## The nautilus is a mount, and swimmers were spawning in the air (2026-07-31)

Ben reported the "sea horror" encounter swimming harmlessly away - and he had
seen our encounter message, so it was ours. Two causes:
- **ZOMBIE_NAUTILUS is not a monster.** 26.2's `AbstractNautilus extends
  Tameable, InventoryHolder, Vehicle` - it is a rideable, tameable sea mount
  with an inventory, so it has no attack goals at all. Replaced with an
  ELDER_GUARDIAN as the FRONTIER+ deep-water encounter (a flee-not-fight
  threat), and a test now asserts every encounter mob implements
  `org.bukkit.entity.Enemy`, so scenery can never be rostered as a threat
  again.
- **Water mobs were spawning above the waterline** (seaHeight + 1.5), where a
  swimmer flops instead of hunting. EncounterType gained a Habitat: swimmers
  spawn 3 blocks under water, phantoms 14 above, boated crews on the surface.
Also added debug logging of encounter spawns (gated by `debug:`) so "was that
one of ours?" is answerable from the console.

NOTE for post-MVP: a tameable sea mount with an inventory is a gift for this
game mode - nautilus as an alternative to boats, or a late-game hold. Worth
its own feature.

## Varied ocean biomes (2026-07-31)

The open sea was a single flat OCEAN everywhere. It now varies through the
five ocean biomes from a seeded temperature field: a new pure `Noise` helper
(smoothstep value noise over a 3000-block lattice, seeded like everything
else) drives `GalaxyEngine.oceanBiomeKeyAt`.
Ben's blending worry has a structural answer rather than a blending pass:
because the field is CONTINUOUS and the biomes are mapped in TEMPERATURE
ORDER (frozen, cold, ocean, lukewarm, warm), neighbouring water can only
differ by one step - warm can never border frozen. A test walks 80,000 blocks
asserting the index never jumps by more than one, and that a voyage crosses
at least three different seas.
The provider declares all ocean biomes in getBiomes; `world.vary-ocean-biomes`
turns it off. FROZEN islands keep their frozen-ocean approach ring, which now
sits naturally inside cold water where the field allows.
152 tests green.

## Playtest fix: the harmless sea witch (2026-07-31)

The witch adrift did nothing and died easily. Two causes: a mob riding a boat
cannot run its attack goals at all (so the boated encounters - SEA_WITCH and
PIRATE_CREW - were ornaments), and the target was set once at spawn with
nothing to re-assert it, so any mob that lost interest stayed lost.
EncounterService now runs a 2s aggression pass over encounter-tagged mobs
near players: re-target when the target is gone, and ABANDON SHIP (leaveVehicle)
when the quarry is within 30 blocks - wider than the 28-block spawn distance,
so crews disembark as soon as they sight you. Ben then reported the witch
DOES throw potions but they sail over his head into the sea, which is the
same root cause: a passenger cannot reposition, so her arc is fixed from a
drifting platform. Swimming, she closes and aims normally. The abandoned
boat is left floating - salvage for the victor. This also stiffens the swimming
encounters (drowned, nautilus, guardians), which previously forgot their
quarry after a short chase.

## The port scan: charts spread from port to port (2026-07-31)

Discovery was sighting-only, so a player who warped everywhere never learned
anything new and the warp dialog stayed at the starter cluster. Ben's fix:
opening the chart AT a trading island copies that port's harbour charts -
the nearest `chart.port-scan` (8, matching the warp dialog cap) islands are
charted free. Implemented as `PlayerDataManager.portScan(player)`, called
from the chart command and from ChartHolograms.show (so boarding a boat at a
port scans too). Requires being within the protection range - at the port,
not merely in its waters - so discovery still needs landfall, and each port
teaches its own neighbourhood, which spreads the map outward naturally as
players trade.

## Recovery: the pouch soft-lock and the harbourmaster (2026-07-31)

Ben died, respawned with under $250, and could not afford a pouch. Worse than
a price problem: with NO pouch there is no hold, and with no hold a player can
neither buy nor sell - the market moves goods through the hold on both sides -
so a dead player could not earn the money to buy the thing that lets them
earn. A hard soft-lock, and my fault for pricing the entry ticket as an
upgrade.
Two fixes:
- **Pouches $50 flat** (was 250). First tried escalating prices (50/150/450)
  keyed off carried pouches; Ben immediately spotted the hole - drop a pouch,
  buy at the base price, pick it back up. Any per-pouch escalation is
  defeated the same way, so the price is flat and the max-bundles CAP does
  the limiting; that is also what keeps expanders necessary. A test asserts
  the starting balance always covers a pouch plus a boat, and another pins
  the price flat regardless of how many are carried.
- **The harbourmaster's charity**: at any market, a sailor with no cargo
  space, no boat and too little money to buy either can claim a free pouch
  (and a hull if needed), on a config cooldown (15 min). Unabusable by
  construction: charity goods are unstamped, so they cannot be sold for
  money, and the button only appears while genuinely destitute.
149 tests green.

## Playtest fixes: empty ocean, unopenable expanders (2026-07-31)

Ben teleported to 10000,10000 and found nothing at all. Measured: nearest
trading island 7329 blocks, and ZERO wild islets in a 6000x6000 area. Cause:
islets shared the trading-island grid (5000-block cells, and only in cells
with no island), averaging ~9000 blocks apart. They now have their own finer
grid (galaxy.wild-islet-grid, 1200) with a clearance test against trading
islands, giving an islet within ~500-1700 blocks of anywhere (tested).

Also, and worse: **Java Edition cannot open a shulker box from the
inventory**, so the "carried hold" I had just designed could not be filled or
emptied by hand at all - Ben found this trying to read the lore on stamped
goods. `ExpanderListener` now opens an expander on right-click (27-slot view,
written back into the item on close), refuses nesting, and never lets an
expander be placed as a block (it is cargo, not a chest to be robbed).

Charting range is now config (`chart.sighting-range`, 1200 - slightly beyond
island waters) so islands chart as they are sighted rather than only when
entering their waters. 145 tests green.

## Playtest fix: respawn in the treetops (2026-07-31)

Ben died and respawned on top of a tree at the island centre. SpawnRespawnListener
was still using `getHighestBlockYAt(0, 0)` from the bare-islet days - the island
CENTRE, which on a real trading island is wooded ground. It now uses
`IslandsManager.getSpawnPoint(world)` (the plaza, and wherever an admin later
moves it with /twadmin setspawnpoint), falling back to the world spawn.
The spawn point itself moved 3 blocks off the plaza centre so players do not
materialise inside the bell that stands there. 144 tests green.

## Pouches for sale, white expanders (2026-07-31)

Completing the carried-hold progression: the shipwright now sells Trading
Pouches (economy.pouch-price, 250) up to max-bundles, refusing beyond the cap
(HoldService.pouchCount counts past the cap so the shop can tell). At default
prices a pouch is ~$3.90/item against the expander's ~$2.89/item - the early
rung is deliberately the worse deal, so expanders stay the goal.
Expanders are now WHITE_SHULKER_BOX so they never read as a vanilla purple
shulker; isExpander accepts any *SHULKER_BOX carrying the PDC key, so purple
ones bought by earlier builds keep working. StarterKit reuses
MarketService.pouchItem() so the starting pouch and shop pouches are
identical. 143 tests green.

## The hold is carried, not moored (2026-07-31)

Ben bought a chest boat and found the hold still capped at his single pouch:
a chest boat is unfillable as an inventory item, and a moored one could not
be opened either - `Flags.CHEST` (MEMBER by default) guards chest boat
inventories, so visitors could not open their own boat at a port. The whole
"chest boat = bigger hold" step therefore did not work in practice.
Redesigned per Ben: the hold is what the sailor CARRIES -
  pouches (bundles, max 3) + cargo expanders wherever carried + the chest
  boat's inventory while riding one.
Expanders are now delivered to the pack instead of requiring a chest boat, so
they can always be opened and filled; HoldService gained `expanders(player)`
(pack + boat) and its add/remove/count/freeSpace all route through it, with
expanders preferred for storage. Ports also now grant CHEST and SHULKER_BOX
at visitor rank so sailors can open their own cargo at a dock.
Principle 1 is intact: loose pocket items are still invisible to the market.
Spec 4 updated. 142 tests green.

## Bug: no boat use at ANY trading island (2026-07-31)

Third instance of the same class of bug, and the worst: `applyBandFlags` -
which runs on every trading island - never set BOAT at all; only the spawn
island got that allowance. BOAT defaults to MEMBER rank, so on every island
in the galaxy a visitor could moor but not re-board their own boat after
shopping. Fatal for a boat game, and invisible while testing as op.
The harbor allowances now live in applyBandFlags, so every port grants
visitors BOAT, HURT_MONSTERS, CRAFTING, DOOR and GATE (plus the existing
ITEM_DROP/ITEM_PICKUP); the spawn bootstrap no longer repeats them and just
adds its no-hostiles/no-explosions settings. `/twadmin reflag` pushes them to
islands registered before this build.

## Bug: spawn island bootstrapped before islands loaded (2026-07-31)

Boats were STILL denied at spawn after the rank-flag fix. The server log gave
it away:

    17:19:19 [TradeWinds] Designated Spawn (FISHING) as the spawn island
    17:19:19 [BentoBox]   Loading islands from database...

`bootstrapSpawnIsland` ran in onEnable, but core loads islands from the
database AFTER addon enable (BentoBox.java: islandsManager.load() at ~line
234, addonsManager.allLoaded() at ~276). So the island cache was empty: every
startup created a brand-new island (the grid rejected the duplicates) and the
subsequent database load shadowed our in-memory changes - which is why the
island JSON showed name/protection/range from an earlier run but
`"spawn": false` and no BOAT/CRAFTING/HURT_MONSTERS.
Fix: run the bootstrap from `allLoaded()`, which core calls after the island
load.
Second trap found while fixing it: `Island.setSpawn(true)` calls
`setFlagsDefaults()`, wiping the flag map - so ALL flag work must happen
after designating spawn, not before (band flags were being erased). The
bootstrap now applies band flags and harbor allowances last, and is
idempotent.

## Bug: rank flags were being silently dropped (2026-07-31)

"No boat usage at spawn" - and the same cause behind every rank flag we have
ever set. BentoBox core:

    public void setFlag(Flag flag, int value, boolean doSubflags) {
        if (flags.containsKey(flag.getID()) && flags.get(flag.getID()) != value) {

`setFlag` is a NO-OP when the flag is not already a key in the island's map -
and islands created via `IslandsManager.createIsland` start with an empty map
(the live spawn island's JSON had `"flags": {}`). So BOAT, CRAFTING,
HURT_MONSTERS, HURT_VILLAGERS, ITEM_DROP and ITEM_PICKUP were all discarded,
leaving each flag at its default rank (MEMBER for BOAT) - i.e. visitors
locked out. `setSettingsFlag` uses put() directly, which is why PvP and
monster-spawn settings always worked and masked the problem.
Fix on our side: `GalaxyIslandRegistrar.setRanks(island, Map<Flag,Integer>)`
copies the flag map, writes the ranks and calls `setFlags` (which replaces the
map and marks the island changed). All rank setting now goes through it.
Also: `register()` used to return a pre-existing island untouched, so islands
from earlier builds never got names or flags - it now adopts them (name if
blank, band flags always), and spawn adoption additionally fixes protection
(400) and range (1000) left over from the old small-island bootstrap.
NOTE FOR CORE: `Island.setFlag` arguably should put unconditionally, or
`createIsland` should call `setFlagsDefaults()`. Worth a BentoBox fix.
141 tests green.

## Trade feedback sounds (2026-07-31)

Dialogs blur and cover the chat box, so refusal messages went unseen: every
market outcome now carries audio - BLOCK_NOTE_BLOCK_PLING (bright, pitch 1.6)
on a successful sell/buy/expander purchase, BLOCK_ANVIL_LAND (dull) on every
refusal path (cannot afford, no hold space, unstamped goods, expander cap,
missing chest boat, cancelled TWTradeEvent).

## Playtest fix: outfitter stores go to the pack (2026-07-31)

Buying a fishing rod failed with "no hold space" despite free space: gear does
not stack to 64, and `HoldService.addToBundle` only accepts 64-stackables, so
with no chest boat the hold could never take it. Ben's call (right on both
counts): outfitter goods are consumables/equipment - deliver them to the
player INVENTORY and do not customs-stamp them. Unstamped also means the
outfitter shelf cannot be arbitraged between islands, which keeps it a
service rather than a commodity market. `buyToInventory` generalized to an
amount (hulls reuse it), outfitter got its own dialog page offering x1 for
unstackables and x1/x16 for stackables, and the expander failure message now
names the real cause ("buy a chest boat first"). 140 tests green.

## Stage 5b — Spawn is a real trading island (2026-07-31)

Ben: make spawn a proper named island with dock, plaza and a config-picked
economy, designated as BentoBox's spawn - and preferably by reusing an
existing island rather than special-casing 0,0. Done by reserving the ORIGIN
CELL for it, which deleted more code than it added: the galaxy engine returns
a full SAFE trading island named "Spawn" centered exactly at 0,0 (economy from
galaxy.spawn-island-type, default FISHING, RANDOM allowed), so dock, plaza,
market, villagers, boss bar and border warp zone all come for free from the
existing systems. Other cells' islands are pushed radially clear of the origin
so min separation still holds (tested). The bare spawn islet and its
protection-range config are gone.
`bootstrapSpawnIsland` now: computes the plaza position from pure geometry (no
chunk load needed), sets it as the world spawn, registers the island via a
new reusable `GalaxyIslandRegistrar.register(spec, world)`, calls
`setSpawnPoint` + `IslandsManager.setSpawn` once (so admins can rename, move
the spawn point and change flags afterwards with normal BentoBox commands),
and re-asserts the harbor allowances every enable.
Players now spawn on a working market plaza with coal, money and a boat -
trade immediately, then walk to the dock and sail. No long empty row to start.
140 tests green (three tests had assumed 0,0 was open ocean).

## Stage 5 — Risk at sea (2026-07-30) — CODE COMPLETE, awaiting in-game test

Built as one package because it IS one idea: spec principle 2 (risk symmetry)
was half-implemented - warping was to risk the interstice, rowing to risk the
lawless ocean, but rowing was free AND safe, quietly dominant for the patient.

**Interstice (warp risk):** `IntersticeService` rolls
travel.warp.failure-chance at jump time; failure teleports player+boat to the
NETHER world partway along the route (35-65%), spawns
interstice.ghasts-min..max Ghasts targeting them, fires TWWarpFailedEvent.
The owed destination is remembered and the free re-engage dialog is offered
every interstice.prompt-seconds; taking it calls the new
`WarpService.deliver()` (the arrival half, split out of the jump) with no
fuel charge. Fallback if the pending destination is lost (relog): a free jump
to the nearest charted island - stranding must be impossible.

**Sea encounters (rowing risk):** pure `EncounterTable` (chance scales with
distance from the nearest island, clamped 0.25-1.0 of the band's base;
roster filtered by band and day/night) + `EncounterService` (45s rolls for
players actually at sea, one encounter at a time, spawned
encounters.distance ahead so fleeing is always possible, PDC-tagged,
setRemoveWhenFarAway). Roster: guardian picket (day), trident drowned
(night), ZOMBIE_NAUTILUS (26.2's new undead sea mob, FRONTIER+), phantoms,
pillager pirate crews and a sea witch in their own boats (LAWLESS+).
`EncounterListener` drops **customs-stamped** booty - so fighting is the
third income beside honest margins and smuggled sugar, and the stamp system
lets us aim the faucet precisely.

138 tests green.

## Teleport friction: stand-still before warping (2026-07-30)

Ben asked about BentoBox's stand-still-before-teleport. Finding: /tw spawn
already extends DelayedTeleportCommand and calls delayCommand, so it is
wired - the server just has commands.delay.time: 0, and ops bypass delays
anyway (which is why it never appeared in testing).
The warp teleports from a dialog callback, out of reach of the command-level
delay, so WarpService gained an equivalent: travel.warp.stand-still-seconds
(default 0 = instant, preserving the spec's fast warp until Ben tunes it).
Moving >2 blocks during the countdown aborts the jump and SALVAGES the fuel
as charcoal into the hold (never a silent loss); ops and
tradewinds.mod.bypassdelays bypass. 133 tests green.

## Spawn island visitor allowances (2026-07-30, playtest bug)

Full protection was too much: visitors could not launch boats, defend
themselves or use a workbench at spawn. bootstrapSpawnIsland now (re-)asserts
policy every enable (existing spawn islands pick up changes): BOAT /
HURT_MONSTERS / CRAFTING at visitor rank; MONSTER_NATURAL_SPAWN, TNT_DAMAGE
and BLOCK_EXPLODE_DAMAGE settings off. (SAFE/POLICED trading islands already
had monster spawning off via bands config.)

## Protected spawn island (2026-07-30)

The protection flip left the spawn islet explicitly free-build (non-ops
could grief it). Fixed with Ben's own Stranger Realms machinery: TradeWinds
overrides isFixIslandCenter=false (arbitrary island centers - we had been
relying on luck that core's grid realignment never moved our jittered
trading islands) and isEnforceEqualRanges=false (arbitrary ranges - also
what Stage 7 claims will need). Then the spawn islet is a REAL BentoBox
spawn island: created unowned at the origin, protection
galaxy.spawn-protection-range (100), range shrunk to 200 (provably can
never overlap a starter island: min starter center is ~1250/axis > 200 +
1000). setSpawn + named "Spawn". Known wrinkle: the grid indexes the
creation-time range (1000) until first restart - on pathological seeds a
starter island rowed to in the very first session could fail to register
until restart heals the grid. hasDistanceMismatch honors the override, so
loads are safe. 128 tests green.

## The Shipwright (2026-07-30)

Boat acquisition (Ben's design): every market's Shipwright page sells Oak
Boat ($20) and Oak Chest Boat ($60) - explicit base prices above raw plank
cost (shipwright labor) so crafting from wild-islet timber stays the frugal
path - plus the cargo-expander ladder (moved from the main menu; the whole
cargo progression boat -> chest boat -> expanders now lives in one shop).
Hulls deliver to the player INVENTORY (a shipless sailor has no hold), via
MarketService.buyToInventory, stamped like any purchase. No free boat on
death: buy, craft, or /tw restart. Pitfall: first put the boat prices into
defaultFuelValues by matching the wrong map's tail - boats briefly counted
as 20 fuel units. 127 tests green.

## The two economies: stamps, outfitters, wild islets, restart (2026-07-30)

Ben's survival-design discussion (players died gearless and hungry; farming
must not mint money). Adopted into the spec as §5.0:
- **Customs stamp**: MarketService.stamp() on every purchase (PDC
  tradewinds:stamp + lore, optional glint). sellableFilter() = stamped OR
  configured unstamped exceptions (SUGAR) while illegal-trade is enabled.
  HoldService went predicate-aware (count/remove/contents filter overloads
  threading through shulkers and bundles). Different lore = stamped and
  homemade stacks never merge.
- **Outfitter** (second buy page): bread always, charcoal when the trade
  catalog lacks fuel (fuel guarantee moved here), per-type gear
  (TypeEconomy.OUTFITTER_EXTRAS) - INDUSTRIAL arms you, farms sell beds,
  fisheries rods, MINING a pickaxe. Gear prices derive from recipes (the
  embedded engine pays off). Shelf capped at 8 (dialog fits, tested).
- **Wild islets**: empty galaxy cells roll small unnamed islands
  (galaxy.wild-islet-chance 0.3, radius 70; pure engine math, deterministic,
  never in a trading-island cell, tests). Vanilla wild biomes. All PROTECTION
  flags default-allowed outside island protection ranges (blanket
  setDefaultSetting loop) - open ocean and wild islets are free country;
  future Stage 7 claim targets.
- **/tw restart** (ConfirmableCommand): zero balance -> fresh kit at spawn
  (starter deposit restores starting balance), chart kept, restartsUsed
  capped by player.max-restarts (3).
- Starting money note: already existed ($250 with the kit); Ben's account
  predates Stage 4 so never saw it.
126 tests green.

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
