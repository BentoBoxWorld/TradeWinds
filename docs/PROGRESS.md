# TradeWinds — Progress Log

What is done, and pitfalls hit on the way. Newest stage first. Read
`TRADEWINDS_SPEC.md` for requirements; this file records reality.

## Playtest fixes — config drift and telling goods apart (2026-08-03)

**An oak boat carried 30 slots.** The shipped `config.yml` had every value in
`boats.ranks` multiplied by ten: cargo SLOT counts caught up in the whole-coin
money migration. Slots are not money. Because BentoBox **replaces** map settings
from config rather than merging them, the wrong values beat the correct code
defaults - and every unit test reads the code default, so the build stayed green.

That is the **second** time config/code drift shipped a broken game this way (the
first was `economy.base-prices` at 27 of 112 entries). So the fix is not just the
data: **`ConfigAgreementTest` now walks every `@ConfigEntry` map in `Settings` by
reflection and asserts `config.yml` agrees with the code default**, key for key
and value for value, comparing numbers by value so `20` and `20.0` are the same
setting. Verified by deliberately corrupting two entries and watching it name both.
A second test asserts the ranks ladder is 20 rungs of 2-21, which is the shape
that a money migration would break again.

**ClassCastException at the trader.** Integer cannot be cast to Double, from
`base-prices` written as `20` rather than `20.0` - see the commit; fixed both by
writing decimals and by coercing every numeric map in `Settings.asDoubles()`.

**Three identical "Iron Sword" rows on the sell page.** With NBT preserved, the
enchanted sword and the two plain ones were three rows of identical text at
different prices, and nothing said which was which. The sell page (and the
secondhand shelf) now render **one `ItemDialogBody` per row, in the same order as
the buttons, with `showTooltip(true)`** - so hovering gives the item's real
tooltip, enchantments and all. Buttons are far too narrow for an enchantment list,
so they get a short marker instead (`✦` for enchanted, `(worn)` for damaged, or
the item's given name if it has one).

Paper's dialog API does support item icons - `DialogBody.item(ItemStack)` with a
description and a real tooltip - but only in the dialog **body**, never in a
button label. Hence icons above, markers on the buttons.

## Stage 7.5 Phase 7 — the secondhand shelf (2026-08-03)

Notable goods sold to a trader go back out for sale instead of vanishing, so the
world feels inhabited: *someone dumped a Silk Touch pick at Baker's Reach*.

- `TWIslandData.shelf` is a bounded `List<ShelfItem>` (item + listed-at), swept by
  `economy.resale-ttl-hours` (72) on access, `economy.resale-slots` (2) deep, with
  the oldest listing making way for an arrival.
- **Only notable goods resurface**: enchanted, renamed, or worth at least
  `economy.resale-notable-value` (500). Ordinary cargo is not interesting to find
  and would bury the things that are.
- **They surface at a DIFFERENT port** than the one they were sold at, within
  `economy.resale-ship-radius` (8000) - the trader shipped it on. This is the
  load-bearing part: an unpredictable destination is what stops shelves becoming
  an alt-account laundering channel. The destination is *seeded* from the item and
  the selling port rather than random, so it cannot be re-rolled by retrying.
- `economy.resale-markup` (1.6) sits above book price, and a test asserts the
  shelf price exceeds what the same port would PAY for the item - otherwise buy
  from the shelf, sell at the counter, repeat.
- Shelf purchases arrive **marked as trader-bought**, so they are subject to the
  one-way rule like anything else a market sells.

**Ordering detail worth keeping:** `buyFromShelf` adds to the hold *before*
removing the listing, so a full hold cannot destroy a one-of-a-kind item.

## Stage 7.5 Phase 6 — price discovery (2026-08-03)

Two different questions, and only one of them is hard.

**At the counter you know everything.** The sell tooltip now states the *depth*:
"this port will take about N more at that price", from Phase 1's
`absorbableValue` divided by the unit price. That is the number a seller actually
needs, because it says when to stop selling and sail on.

**Across the map you should not know.** A **logbook**, not an oracle:
`TWPlayerData.priceLog` (island -> category -> price seen) plus `priceLogSeen`
(when), written whenever a player opens a port's market. `/tw prices` lists ports
you have *called at* with their age; `/tw prices metals` sorts by best price for
one kind of goods. Prices for ports never visited are simply not knowable, and
stale readings show their age - which is what makes a well-travelled trader more
capable than a new one rather than merely richer.

Category-level rather than per-material, because that is the granularity prices
vary at: type, tech, band and drift all move a whole category together.

**Harbour reports** (`economy.market-report-price-per-island` 250,
`market-report-radius-per-tech-level` 1500) let a broker fill in your logbook for
ports within the selling island's tech-scaled reach. This gives a developed port a
role beyond its shelves and a reason to call at a hub you are not trading with.
No boat needed - it is information, not cargo.

**Deviations from the plan, both deliberate:**
- The logbook surfaces through `/tw prices` rather than on the chart holograms.
  The gameplay value ("where do I take this?") is identical, the risk is far
  lower, and the hologram renderer can adopt the same data later.
- **Plaza rumours were not built.** They were the cheap flavour version of
  exactly what the logbook and harbour reports now do properly, so they would
  add a third route to the same information. Worth revisiting only as flavour.

## Stage 7.5 Phase 5 — the hold works both ways (2026-08-03)

A scavenger has to be able to use their hold as a hold. **This narrows a
load-bearing rule**: "cargo leaves the hold only by sale or destruction" now
applies to **trader-bought cargo only**.

- `travel/CargoMark` marks bought cargo in the item's own persistent data
  (`tradewinds:traded`). Because the mark lives in the meta it survives the
  hold's serialisation, travels through a boat capture, stays visible if a bug
  ever leaks one into an inventory - and makes marked and unmarked stacks fail
  `isSimilar`, so one bought diamond can never lock up twenty mined ones.
- `HoldService.withdraw` returns **-1** for trader-bought cargo rather than 0, so
  the GUI can say *why* instead of just refusing. Overflow drops at the player's
  feet rather than vanishing.
- Hold GUI gestures: **left-click takes cargo out**, shift-click sends burnables
  to the fuel row (the old plain-click behaviour), right-click selects for the
  TNT. The border pane's lore states all three.
- `MarketService.buy` marks what it sells you. Selling and destroying are
  untouched - `remove()` is not gated, only `withdraw()`.

**Bug found while doing it:** the existing `deposit` path added
`hold.add(player, material, amount)`, so loading an enchanted sword into the hold
stored a *plain* one. Harmless before Phase 4 (nothing had NBT to lose) and
silently destructive after it.

**Pitfall:** `CargoMark` first built its key with `new NamespacedKey(plugin, ...)`
cached in a static - which needs a mocked plugin and outlives a reload.
`BoatService` already had the right pattern: `NamespacedKey.fromString`, no
Plugin instance at all.

## Stage 7.5 Phase 4 — the hold carries NBT (2026-08-03)

The hold stores **one ItemStack per slot** instead of material→amount, so a worn
bow, a mint bow and a Silk Touch pick are three different goods.

- `BoatHold.contents` -> `cargo` as `List<ItemStack>`; `expanders` as
  `List<List<ItemStack>>`. **Fuel stays material-keyed** - a lump of coal is a
  lump of coal. BentoBox's `ItemStackTypeAdapter` (registered in
  `BentoboxTypeAdapterFactory`) persists these through Bukkit's own YAML
  serializer, so meta survives a restart; its 99-amount clamp cannot be hit
  because a slot never holds more than a stack.
- New `travel/CargoStore` holds all the slot arithmetic - capacity, consolidation,
  drain, compaction - as statics, which is what makes it testable.
- `HoldService` keeps its Material-based API as thin overloads over the
  ItemStack ones, so the buy path, fuel and starter kit were untouched.
- `MarketService.basePrice/playerBuysAt/playerSellsAt/sell/handlesValue` now take
  ItemStacks. The sell page groups by *distinct good*, so two differently
  enchanted swords are two rows at two prices.
- `PriceEngine` applies durability (already written), **enchantments** (
  `getEnchantmentValue` existed and had never been called - there was never an
  enchanted item left to price) and **potions** via `PotionMeta`. New knobs
  `economy.enchantment-price-factor` 0.05 and `economy.potion-effect-price` 150.
- `HoldGui.Selection` carries the ItemStack, not the Material - otherwise
  selecting an enchanted sword and hitting the TNT destroyed a plain one.

**MockBukkit findings that shaped the design** (probed rather than assumed - the
CLAUDE.md note that "ItemStack meta does not work" was too coarse):
- `isSimilar`, `getMaxStackSize`, `clone` and `equals` all work.
- `new ItemStack(...)` attaches a phantom **UNSPECIFIC** meta and `clone()` drops
  it, so a cloned plain item stops being `isSimilar` to an identical one. Hence
  `CargoStore.copyOf` rebuilds plain items from their type and only clones items
  with real meta - which is better production code anyway, and gives the same
  answer in both worlds. `isPlain` compares against a pristine stack rather than
  trusting `hasItemMeta()`, which lies here.
- `new ItemStack(Material.AIR)` throws `AbstractMethodError`: never manufacture
  an air stack, only detect one.
- ItemStack **serialisation is dead** headlessly (`craftDelegate` is null), so
  persistence of enchanted cargo is a manual check in `TESTING.md`. Potion
  pricing is likewise manual-only - `PotionType`'s effect registry is not
  available in tests.

**Pitfall:** Maven does not recompile unchanged test sources, so four tests
failed at *runtime* with `NoSuchMethodError` against the new signatures rather
than failing to compile. If a rename looks like it broke tests mysteriously,
that is what happened.

## Stage 7.5 Phase 3 — tech gates what a port will handle (2026-08-03)

`economy.salvage-value-per-tech-level` (1500) caps the value of a single salvage
item a port will deal in: a TL1 hamlet takes mob loot but not diamond-grade gear,
a TL7 hub takes anything. Loot therefore has a *destination*, and a destination
is a voyage. Refused at the counter with `tradewinds.trade.too-advanced`, and
hidden from the sell page the same way contraband already is.

**Design correction made during implementation.** The plan said to gate on value
by tech, full stop. Applied to everything that would have had a low-tech LUXURY
island refusing the very diamonds it demands - gems are demanded by LUXURY but
are not on its produce shelf, so a flat cap catches them. The gate therefore
exempts recognised trade goods entirely: a port always deals in what its shelves
stock, whatever its tech. That keeps the existing trade economy untouched (again)
and leaves the gate doing only the job it was added for, which is turning exotic
loot into a reason to sail somewhere.

## Stage 7.5 Phase 2 — salvage has a market (2026-08-03)

Scavenged loot is now sellable, at a discount, into a stock pool of its own.

- **`TradeCategory.SALVAGE`**, which `of()` never returns: salvage is decided by
  whether a material is on any island's shelves (`TypeEconomy.tradeGoods()` -
  the union of every type catalog and outfitter shelf), not by its name. That
  leaves the existing trade economy completely untouched; an iron ingot is cargo
  exactly where it always was. No type produces or demands SALVAGE, so it is
  affinity- and tech-neutral, and `economy.salvage-discount` (0.375) is applied
  on top.
- A **separate stock pool** is the load-bearing part: without it, dumping a
  boatload of junk would crater the same category's honest cargo prices for every
  trader at that port, which is a griefing vector on a small server.
- **~50 natural drops priced** (mob loot, foraged food, dug blocks). None of
  these has any recipe, so the engine could never derive them however deep it
  recursed - they had to be stated or a scavenger's whole haul was unsellable.
- **`/twadmin priceaudit`** writes a full coverage report (unpriceable / salvage /
  trade goods) to `price-audit.txt`.

**The real bug behind the complaint.** BentoBox **replaces** `Map` settings from
`config.yml` rather than merging them (`YamlDatabaseHandler.deserializeMap`), and
the shipped `config.yml` carried **27 of 112** base prices. So ~50 goods were
unsellable on every real server while the unit tests - which read the code
default - passed happily. The config block is now complete and generated from the
code table, and `SettingsTest` fails the build if the two ever drift again. A
second new test asserts every price key resolves to a real `Material`, which also
catches Minecraft renames like `SCUTE` -> `TURTLE_SCUTE`.

**Why the audit is a command and not a unit test.** The plan called for a
headless coverage test, but most prices are *derived* from crafting recipes and
MockBukkit ships no vanilla recipe set - headless, nearly everything reports
unpriceable, which is a confident wrong answer. The live registry is the only
place the truth lives. The headless guards test what they can actually see: name
validity and config/code agreement.

## Stage 7.5 Phase 1 — the trader's purse (2026-08-03)

Planned in `tradewinds-salvage-plan.md`. Stock drift was counted in **items**,
so ten diamonds and ten wheat hit a port identically and a stack of diamonds
was one payday rather than several ports' business. Drift is now denominated in
**coins of inventory position** - a port's capacity is its capacity to *spend*.

- `TWIslandData.stock` -> `stockValue`; `IslandDataManager.adjustStock` ->
  `adjustStockValue(spec, category, coinDelta)`, fed the transaction total from
  `MarketService.sell`/`buy` (its only two call sites).
- `economy.drift-scale` 500 -> `economy.drift-value-scale` **30000**;
  `economy.stock-decay-per-hour` 50 -> `economy.stock-decay-value-per-hour`
  **3000**. Renamed rather than retuned in place so nobody has to guess the unit.
- New `IslandDataManager.absorbableValue()` - headroom before the price floor.
  This is Phase 6's "depth at the counter" number, built now because the drift
  clamp is what defines it: only the first `(1 - driftMin) x scale` coins move a
  price at all, so saturation is 9000 coins and recovery about 3 hours.

**Tuning check** (demanded good, SAFE band, no drift): 7 diamonds, 12 emeralds,
84 iron ingots, 391 wheat or 3000 cobblestone saturate one port. That puts a
stack of diamonds at roughly nine ports' worth of business and a boatload of
cobble at about half a port's - which is the intended shape: value-dense cargo
is what forces the tour, and bulk junk was never going to pay well anyway.

**Pitfall avoided:** the saturation/headroom math is in pure package-visible
statics (`saturationValue`, `absorbable`) beside the existing `decayed`, because
`IslandDataManager`'s constructor builds a real `Database` and is awkward to
mock. Same reason `decayed` was already shaped that way.

## Whole coins, and the economy's own formatter (2026-08-03)

"Oak Log x1 - $0.97" made every market chart ragged. Two independent faults:

**We never asked the economy how to write money.** Vault exposes
`fractionalDigits()` and `format(double)`, and BentoBox surfaces the latter as
`VaultHook.format()` - an admin running a whole-coin economy has stated a
preference. TradeWinds hand-formatted `%.2f` in 19 places and overrode them
everywhere. All of it now goes through `economy.Money.format(addon, amount)`,
which delegates to Vault and falls back to whole units when there is no
economy. Two local copies of the same helper (TWFineCommand, CustomsService)
were folded in.

**Prices are now whole coins.** Naive rounding would have broken the cheap end
- sand/stone/kelp sat at 0.2-0.35 after the produce factor and would have
rounded to FREE - so every money value is scaled x10 (base prices, starting
balance 2500, boats `250 x slots²`, expanders 50000, fines, bounties) and the
unit price is rounded **directionally**: buying `ceil`, selling `floor`, buy
never below 1. The direction is load-bearing: rounding both to nearest lets a
same-island round trip break even on some prices, which is free money.
Verified by brute force over 2000 base values x every type factor - zero
violations - and pinned by `testEveryPriceIsAWholeCoin`.

Cost of the x10 scale: the cheapest goods carry up to ~10% rounding error
(sand's true 2.07 becomes 3). x20 or x100 would shrink it if play says it
matters. Live servers would need balances scaled to match; ours is wiped
anyway.

## The boat IS the hold: capture, abandonment, protection (2026-08-02)

Design session with Ben settled the abandonment rules, and they forced the
structural change the last refit left open: **the hold is now keyed to the
BOAT, not the player.** Capture gives the pirate "everything in it", an
abandoned boat keeps its cargo while you row back to it, and trading needs
the ship present - none of which works with a player-keyed hold. The two
records collapse into one `BoatHold` (material, cargo, fuel, expanders,
owner, last-seen position, item TTL) keyed by a UUID stamped in PDC on BOTH
the entity and its item form; `TWPlayerData` just points at `activeBoat` and
`oldBoat`. `PlayerHold` and `DroppedBoat` are gone.

**The rules as built:**
- **Trading gate:** no ship inside that island's PROTECTED space, no market.
  Carrying the boat as an item counts (it is with you); otherwise the record's
  last-seen position answers.
- **Capture by boarding or by pickup**, always behind a confirmation dialog -
  because the boat you leave behind keeps YOUR cargo and becomes unowned.
  Item pickups cancel first and prompt (15s suppression, since pickup fires
  every tick you stand near a hull). Passengers never capture. A player with
  no boat claims silently - nothing to lose.
- **Smaller hull:** take the goods, leave the hull to the sea and its TTL.
- **Protection:** an OWNED boat left unattended in protected island space
  cannot be boarded or broken by anyone else, mobs included - except at
  ANARCHIC ports. **Unowned boats are never protected, anywhere**, which is
  what makes an OLD BOAT a race to recover and dying with a loaded ship at a
  safe dock genuinely costly.
- **Exactly one OLD BOAT** is remembered; older abandonments are forgotten
  flotsam. Reclaiming it clears the marker.
- **Death leaves the boat where it is** - no item drop, still yours. Lava
  alone destroys boat and cargo together.
- **Name plates:** `Entity extends Nameable`, so the hull carries the owner's
  name or UNOWNED, hidden while ridden. (Vanilla name TAGS only work on mobs;
  the API has no such limit.)
- **Respawn/login:** a raft when your own ship is out of reach; a stolen-boat
  message and a raft for anyone left treading water at login.
- `/tw chart` now raises BOAT and OLD BOAT holograms anywhere in the world.

**Trap hit:** `yes:`/`no:` are YAML 1.1 BOOLEANS as keys - the capture dialog
locale keys parsed as `true`/`false` and blew up the locale marker test.
Renamed to confirm/cancel. Never use bare yes/no/on/off as locale keys.

**Warp arrivals face the dock (2026-08-02):** the boat (and sailor) now leave
a warp pointed at the destination's pier, computed from the same seeded dock
plan the quay is built from, so holding forward is the approach. **A hull
sits ACROSS its yaw, not along it** - the first cut used the plain look-at
yaw and arrived broadside ("turn 90 clockwise and I would be pointing at the
dock"), so `boatYawToward` leads the heading by a quarter turn and normalises
back into Minecraft's (-180, 180]. The plain `yawToward` keeps its own tests
as the honest compass maths.

**Offered to swap straight back (2026-08-03, playtest):** taking a boat from
the ground leaves the old hull at your feet, and walking over it immediately
asked whether you would like to swap back. The existing per-hull prompt
suppression could not help - the shed hull is a *different* boat, seen for the
first time - so a swap now opens a 5-second quiet window during which boat
items are ignored entirely (the pickup is cancelled, not allowed through, or
the spare would land in the pack). Re-pocketing your OWN boat still works
throughout: that branch runs before the window is checked.

**Jigsaw blocks left standing in islet structures (2026-08-02, playtest):** a
camp on an islet was held up by a row of glowing jigsaw blocks. The templates
were chosen as "single-piece NBT" precisely to avoid this, but that was wrong:
inspecting the shipped NBT shows the pillager camp features AND ruined portals
1/2/3/4/5 all carry jigsaw connectors. Vanilla's assembler swaps each for its
own `final_state` while building; a raw paste does not.

Rather than read `final_state` at runtime (Boxed does this via BentoBox's NMS
metadata helper, which needs a real Block and so does not fit a
BlockPopulator), the value is read from each template's NBT once and recorded
on the `Placement`: camps and portal_3 become AIR, portals 1/2/4/5 become
NETHERRACK. `IsletDecorator` then scrubs the pasted box - JIGSAW to that fill,
STRUCTURE blocks to air. A test asserts every placement names a real material
and that the netherrack-footed portals say so.

**Crafted boat came out blank (2026-08-02, playtest):** an acacia boat crafted
as an upgrade had no lore and would not open. Clicking a crafting result puts
the item on the **cursor**, not into a slot, and the stamping pass only walked
`getInventory().getContents()` - so the new hull never got its record id. It
now checks the cursor first (and retries a few ticks later if the item has not
landed anywhere yet). Because an unstamped hull can never be opened, the hold
GUI also self-heals: right-clicking a boat item with no id, of exactly your
boat's type, while carrying no other avatar of it, adopts it as the avatar.
That repairs any hull that reaches a player without its record, whatever the
route.

**Chart marking your own pocket (2026-08-02, playtest):** after a few boat
swaps the chart showed BOAT 8m and OLD BOAT 8m - both pointing at the
player's feet, one of them at the boat in their hand. Markers are now only
raised for a boat you actually have to GO to: never one you are carrying
(`BoatService.isCarrying`), never one already under you, and never one within
32 blocks. A hull deliberately shed during a merge (emptied, dropped at your
feet) also stops being charted as an OLD BOAT - you did not lose it, you put
it down.

**Two boats in one pack (2026-08-02, playtest):** recovering an old boat while
carrying the current one left BOTH hulls in the inventory - and since the
hold GUI matched the boat item by MATERIAL, either oak hull opened the active
hold. The spare could then be dropped, picked up and stripped for free fuel,
or thrown to show up as an OLD BOAT. Three fixes: taking a boat while your
old one is **with you** (carried, or moored within loading range) now pours
its cargo into the new hull and leaves the empty hull at your feet, unowned -
you end up with the best boat and all the cargo, per Ben's ruling; the GUI
gesture matches by boat IDENTITY, not type; and crafted hulls are stamped
with their record, so a freshly built boat is not an anonymous item that
merely looks like your ship. When the old boat is far away nothing changes -
it stays put with its cargo as the OLD BOAT you row back to.

**Paid $100 for a raft and got nothing (2026-08-02, playtest):** the yard's
purchase only knew how to REFIT - it changed the material on the record the
player already had. With no boat there was no record, so `ifPresent` did
nothing at all: money gone, no hull. Buying with no boat now creates the
record and hands over the item; buying WITH a boat is a genuine refit that
also swaps the avatar in the world (ridden entity replaced under the sailor,
carried item retyped, moored hull rebuilt where it floats) - the old code
changed only the record, so a refit would have left an oak hull claiming to
be a Cherry Chest Boat. A refit now also requires the ship to be AT the yard
(a trade-in needs the old hull present); buying your first boat never does,
since that is the escape hatch for a stranded sailor.

**Capture left the victim holding a ghost (2026-08-02, two-player playtest):**
one capture produced five bugs, all from a single omission - **taking a boat
never told the boat's owner.** Player 2's record still pointed at the hull
Player 1 had taken, so: no notification at the time; phantom "0/3 slots" at
the market; low-fuel nagging with no boat; and at login the stolen-boat path
called `setActiveBoat(null)`, which *demotes* - so it stripped the new
owner's name (handing the boat back!) and left a bogus OLD BOAT marker 16m
away. The database showed it plainly: both players' `oldBoat` pointed at the
same hull, and Player 1's real abandoned boat was orphaned.

Fixes: `setActiveBoat` now takes the boat off its previous owner (clearing
their pointer and telling them if online); **losing** a boat goes through a
new `clearActiveBoat` that does NOT demote (only abandonment leaves an OLD
BOAT); `oldBoat()` returns nothing - and forgets the pointer - once the hull
is owned again or gone; fuel warnings skip boatless players; and the market
gate now applies to CARGO only, so the outfitter and shipwright always serve
(a sailor who lost their boat could otherwise never buy another and was
stranded on the island for good).

**Tests now run the real thing:** these rules had been "verified" against a
hand-written stand-in for HoldManager, which is why none of it was caught.
`TestHolds` now drives the REAL manager over an in-memory database, so the
ownership regressions are pinned against production logic.

**No BOAT marker while aboard (2026-08-02):** pointing a sailor at the deck
under their feet is noise, so the green BOAT hologram is suppressed while
riding. OLD BOAT still shows - that is the one being rowed back to.

**Cargo teleported across the ocean (2026-08-02, playtest):** a hull thrown to
a player standing on ANOTHER island stripped itself into their boat thousands
of blocks away, and they could not take the hull at all ("I really wanted the
boat"). Both faults came from the automatic size-based salvage. Finding a hull
- by pickup or by boarding - now always opens one dialog with up to three
answers: take the boat (your own becomes an unowned OLD BOAT), take only the
cargo (**only** while your own boat is within `boats.cargo-transfer-range`,
default 200 - and the dialog says WHY the option is missing when it is not),
or leave it. A boatless finder still claims outright with no dialog. This
supersedes the earlier "smaller hull: goods only, leave the hull" ruling: the
size of the hull no longer decides anything, the player does.

**Warp facing, settled by console (2026-08-02):** the "hull sits across its
yaw" theory was WRONG and is reverted. The logging proved it in one warp:
arriving (-2919, 2187) with the dock flag at (-3237, 2497), the computed
look-at yaw was 45.8 and the sailor's own F3 read 46.3 when they turned to
face the dock - so `yawToward` had been right from the start. The real fault
is that **mounting drags the facing** (the boat ends up pointing wherever the
player is looking; the log showed player yaw 178.8 against a boat set to
135.8). Both rotations are now forced with `setRotation` two ticks AFTER the
re-seat instead of trusting the teleport, and a regression test pins the real
numbers from that console line. Confirmed working in play; the yaw
diagnostics were removed once they had done their job (the one-line arrival
log stays). Lesson: one empirical report ("turn 90
clockwise") is a symptom, not a diagnosis - the offset it suggested was a
coincidence of that arrival's geometry.

**Chart holograms vanishing (2026-08-02):** "/tw chart shows them, then they
quickly disappear." Every `show()` scheduled a fade for the configured
duration, but the timer cleared whatever was current rather than its OWN set
- so an earlier call's timer wiped a later call's holograms seconds after
they appeared. Each set now carries a generation number and a timer only
clears its own. Arrival also raises the chart a second time once the arrival
BLINDNESS wears off, which is why they were "sometimes" invisible on warp in.
Warp arrivals additionally log the dock-flag position, the look-at yaw, the
boat yaw set, and - a tick after the re-seat - what the yaw actually ended up
being, so the remaining facing question can be settled by console rather than
by eye.

**Playtest round 2 (2026-08-02):** `/tw chart` ashore printed the text list -
useless for the one job ashore actually has, finding your moored boat. The
hologram compass now raises anywhere in the world (text behind
`/tw chart list`). And the capture flow relabelled the abandoned boat BEFORE
`setActiveBoat` stripped its owner, so the plate kept the old owner's name -
relabel now happens after the switch.

**Playtest immediately after (2026-08-02):** "I dropped the boat in the water
and tried to enter it - it asked me to TAKE my own Oak Boat." Placing a boat
item spawns a plain vanilla entity carrying none of the item's PDC, so the
hull in the water was an unregistered stranger: boarding it registered a new
unowned record and offered a capture, which demoted the real starter boat
(coal and all) to OLD BOAT - hence the phantom marker 56m away and the
missing starter fuel. Fixed with an `EntityPlaceEvent` handler that carries
the record id from item to entity as it is placed. The same identity loss
existed on the teleport path (`BoatPickupListener` built a bare ItemStack),
now stamped; the chest boat's real inventory is no longer rescued there
because the hold is the record.

241 tests green.

## The great refit: virtual hold, One Boat, tech levels (2026-08-01)

The playtest rework, from `tradewinds-hold-plan.md` (normative) + session
rulings. Clean slate - **the test server's TradeWinds DB and world must be
wiped on next deploy** (pouches, stamped goods and real-container holds are
all meaningless now).

**Tech levels (galaxy).** `IslandSpec.techLevel()` 1-7, seeded, type-based
(+/-2 wobble; INDUSTRIAL/LUXURY base 5, farms/fisheries 2), starter cluster
guarantees one >= TL3 (deterministic boost of the best natural roll if seed
luck fails). Prices tilt by `economy.tech-price-step` (3%/step from TL4):
high tech sells finished (METALS, FOOD) cheap, buys raw (ORES, CROPS, WOOD,
FISH, STONE) dear - contraband exempt. Tech shows in the market subtitle,
warp tooltips, chart, holograms, nav bar. Plazas furnish by tech: galley
everywhere; furnace+stonecutter TL3, smithing+grindstone TL4, brewing+cauldron
TL5, anvil TL6, enchanting table with bookshelf arc TL7.

**The virtual hold.** `PlayerHold` (DB): boat, cargo material→amount, fuel,
expanders. No real container ever holds cargo; ItemStack meta does not
survive deposit (by design - cargo is a commodity). `HoldService` is the only
gate: slots = ceil(amount/stack) consolidated; one-way in (containers,
bundles, shulkers and BOATS refused); out = sell or TNT-destroy only; 7 fuel
slots, fuel free both ways. `HoldGui`: 54-slot render-and-command window -
every click cancelled and interpreted (deposit from pack, select stack → TNT,
fuel withdraw, expander panels). Chest-boat REAL inventories are now
unreachable by design (sneak-click and in-boat right-click open the hold GUI
instead) - no shadow storage.

**One Boat.** 20 ranks (config `boats.ranks`), Bamboo Raft 2 slots → Pale Oak
Chest Boat 21. Shop: quadratic `25 x slots²`, lists bigger-only, gated
rank <= TL x 3; purchase REPLACES and destroys the old boat, contents stay.
Crafting: smaller-or-equal refused, bigger is a replacement upgrade
(chest-add included); tech never gates crafting. Start kit: Oak Boat + coal
in the fuel slots. Charity: Bamboo Raft. `/tw restart` clears the whole hold.

**Stamping is GONE.** Contraband has its own list
(`illegal-trade.contraband-materials`); the market buys anything aboard.
Balance now rests on capacity + stock-pool drift - **watch the pools in
playtest** (farm-and-sell is legal now; drift-scale 500 / decay 50/hr may
need tightening).

**Dropped boats.** Boat items carry their hold via a PDC UUID onto a
`DroppedBoat` record; DB-persisted TTL (`boats.dropped-boat-ttl-minutes`),
sweep task sinks expired flotsam; vanilla despawn cancelled for tagged items.
Breakage always yields the item (destroy event cancelled, entity swapped for
a drop) - lava alone burns boat and hold. Death drops the tagged boat
(keepInventory keeps it; DeathChest chests it; fire/lava deaths burn it).
Pickup: no boat = claim whole; bigger = SWAP (ruled in session: your old boat
becomes the floating item, carrying whatever overflow did not fit - the old
hull can always take it, so nothing is ever lost to the sea); smaller/equal =
most-valuable-first partial transfer, remainder stays claimable. Handing a
loaded boat over IS the trade gesture. Shop purchases remain trade-ins (old
boat destroyed) - only salvage swaps.

**Expanders v2.** Install-only into a Pale Oak Chest Boat with a free slot
(each occupies one), TL7 shops only, `5000 x 2^installed`, no cap. Nested
21-slot panel per expander (own TNT, no fuel row, no nesting); TNT refuses a
loaded expander; in any smaller boat they ride inert, contents intact, still
counted aboard for trade and customs.

**Post-rulings (same session):** riding + inventory key opens the hold on
CHEST-variant boats (the client sends the vanilla open-vehicle-inventory
request and we redirect it - this also seals the chest boat's real inventory
completely). Plain boats CANNOT get this gesture: the client renders the
ordinary inventory screen without telling the server, so the right-click
gestures cover them. Also:  bigger-boat salvage is a SWAP (old boat
floats on with the overflow - nothing lost to the sea, conservation-tested);
fuel-valued cargo clicks MOVE to the fuel row instead of selecting for the
TNT (fuel is exempt from one-way, and the TNT never touches fuel, per plan).

**Respawn loaner (2026-08-02):** a boatless respawner is lent
`boats.respawn-boat` (default BAMBOO_RAFT, NONE disables) - death maroons you
ashore while your boat floats at the death site; the raft is the row back.
Never granted over an existing boat (keepInventory), and off-ladder config
values disable rather than misfire.

**Duplicate locale keys (2026-08-02):** the console warned "duplicate keys
found : description / nothing-to-sell". Both were self-inflicted by bulk
locale edits: the `commands.trade` subcommand had been left indented under
`starchart` (so its `description` collided and the trade command lost its
help line), and renaming `not-stamped` to `nothing-to-sell` collided with an
existing key of that name - two different messages, one for "this port wants
none of what you carry" (dialog) and one for "you have none of THAT"
(market), so the market's got its own key `nothing-of-that`. Bukkit only
WARNS and then silently keeps one, so `ResourceYamlTest` now fails the build
on any duplicate key in addon.yml, config.yml or any locale.

**Plain-boat pickups + no lingering hulls (2026-08-02, playtest):** "picking
up a given oak chest boat didn't upgrade." The ladder now applies to UNTAGGED
boat items too (command-given, spare hulls, other players' plain drops):
bigger replaces (no record = no overflow = old hull consumed outright),
boatless claims, smaller stays an ordinary item. And the salvage swap only
leaves the old boat afloat when it actually carries overflow - an empty old
hull no longer lingers.

**Visible boundaries (2026-08-02, playtest):** "there is no visible border -
it's invisible." A per-player dust-curtain task now paints the arc nearest
you of both rings: RED at the warp-offer ring (protection edge), BLUE at the
island-space edge. Colors/view distance/master switch in `border.*`; parsing
is forgiving (typo = default, never a stripped border). Paint, not a wall.

**First cut showed nothing** (warp dialog fired, no particles). Rewritten to
follow the Border addon's proven path exactly: `User.spawnParticle` (which
resolves DUST/REDSTONE across versions, validates the dust options, applies
the server view-distance check and passes extra=1 - a raw
`player.spawnParticle(..., extra 0, dust)` was the difference), and the
curtain now hangs around the PLAYER's own Y rather than an assumed sea level,
so a boat sitting above the waterline still sees it. Startup logs a line with
the ring radii and on/off state, so the console says whether it is alive.
Ruled out first: config default (BentoBox `config.contains` gating means a
missing path keeps the Java default `true`, so an old config cannot silently
disable it) and task registration (start() is in onEnable).

**Removed:** pouches, customs stamps, the carried-shulker expander,
`ExpanderListener`, `economy.unstamped-sellables`, `stamp-glint`,
`expander-cap`, `max-bundles`, `pouch-price`, `TWPlayerData.expandersPurchased`.

229 tests green.

## The galley: every plaza cooks (2026-08-01)

Every market plaza now has a **public workbench and a campfire hearth** beside
the quay entrance (bearing + 0.7 rad, plazaRadius − 4), so a sailor can craft
and cook their catch the moment they step ashore. No flag changes were needed:
CRAFTING is already visitor-rank at every port, campfire interaction is
governed by BentoBox's FURNACE flag (also visitor-rank), and BREAK_BLOCKS is
denied - usable by all, removable by none. The campfire stands on a
cobblestone hearth block so residents do not path across open flame. A
campfire rather than a furnace on purpose: it needs no fuel, so even a
destitute sailor can cook. No economy leak either way - anything crafted or
cooked is unstamped and therefore unsellable; the galley feeds players, not
wallets.

226 tests green.

## Ground truth, and ruins worth rowing to (2026-08-01)

**Per-biome surface materials.** With every biome now generatable, "everything
stands on grass" stopped being ignorable: a desert islet was a lawn with a
desert sky. `surfaceKindAt` is now purely biome-driven - it asks `biomeKeyAt`
(which already knows islet vs island vs beach fringe vs mushroom) and maps
through one table: sand under deserts and beaches, red sand over terracotta in
the badlands, podzol in old-growth taiga, mud in mangrove swamps, bare stone
on stony shores and the peaks, gravel on the gravelly hills, snow blocks on
groves/slopes/ice spikes, mycelium on mushroom fields. Trading islands follow
the same rule (a desert INDUSTRIAL port is a sand island now); plaza and dock
terraforming still override their own columns. `SurfaceKind` grew from 3 kinds
to 9; `IslandPalette` maps them (subsoil too: sandstone under sand, terracotta
under red sand). NOTE for the live world: the spawn island's mangrove biome
means its newly generated chunks are mud-surfaced - a seam against
already-generated grass chunks is expected on the test server.

**Islet structures.** Wild islets can now carry a small vanilla structure at
their heart, chance per islet in config (`galaxy.islet-structure-chance`,
default 0.25). Selection is pure and seeded (`galaxy.IsletStructures`):
igloos on the snowfields, half-buried fossils in the deserts/badlands/swamps,
ruined portals (loot chests intact) in jungles and woods, abandoned pillager
camps (tents, log piles, target ranges) on plains and savannas. Placement is
`generator.IsletDecorator`, a BlockPopulator that stamps the whole template
down when the islet's center chunk generates - single-chunk anchored, so no
cross-chunk consistency to maintain, and safe to toggle mid-game.
**Deliberately restricted to vanilla's single-piece NBT templates** (fossils,
igloo/top, ruined_portal/*, pillager_outpost/feature_*) - jigsaw-built
structures (villages, temples) would leave connector blocks behind if placed
raw; that is Stage-2 territory via the Boxed patterns. Mushroom islets and
pale gardens never decorate: there the biome itself is the find.

226 tests green.

## Every land biome now has somewhere to exist (2026-08-01)

"Is it possible to find a Cherry Grove islet or Pale Oak islet?" It was not:
wild islets drew uniformly from a list of 8 temperate biomes, and 21 of the
registry's 40 generatable overworld land biomes existed nowhere at all
(pale_garden, taiga, grove, bamboo_jungle, eroded_badlands, the peaks...).

Wild islets now draw from **temperature-banded lists** keyed to
`oceanTemperatureIndex` at the islet's center - the sea the galaxy already
varies. Frozen seas grow snowfields, groves and frozen peaks; cold seas taigas
and windswept hills; temperate seas the forests (cherry grove and pale garden
among them); lukewarm the savannas and sparse jungle; warm seas desert,
badlands, bamboo jungle and mangrove. Between the five bands and the trading
island types, every land biome is reachable (caves, rivers, oceans, Nether and
End deliberately excepted), and the land always suits the water it stands in.
`isletBiomes()` aggregates the bands, so the biome provider declares the new
keys with no further change. Trading island type biomes are untouched.

Caveat for the live world: islet biomes in not-yet-generated chunks re-roll
under the new scheme, so a chunk border through an already-half-generated
islet could show a biome seam. Trading islands are unaffected.

220 tests green.

## The expander that would not open (2026-08-01)

**"Right clicking a cargo expander does not open its inventory."** Two
findings, one report:

**The gesture players actually try was never implemented.** The listener only
knew the in-hand gesture (expander in the main hand, right-click the world);
the natural one - inventory screen open, right-click the expander where it
lies, like using a bundle - fell through to vanilla's pick-up-half. There is
now an `InventoryClickEvent` handler for it: own inventory only, empty cursor
only, opens the same 27-slot view next tick. The view write-back is tracked by
SLOT, and registration now happens only after `openInventory` confirms the
view really opened - registering first meant the close of a view being swapped
out could have its contents written into the box.

**The in-hand gesture was also broken, invisibly.** `PlayerInteractEvent` for
a click at AIR is *born cancelled* - `isCancelled()` is defined as "block use
denied", and with no block that half is permanently denied - so the
`ignoreCancelled = true` on `onRightClick` skipped every click at sea or at
the horizon. The handler now checks the half that means something for a held
item: `useItemInHand() == DENY`. **Pitfall for every future
PlayerInteractEvent listener: never pair `ignoreCancelled = true` with
RIGHT_CLICK_AIR handling.**

218 tests green.

## Arrivals reopened the warp dialog, and the dock sign sank (2026-08-01)

**"Warping in triggered the exit warp."** Moving arrivals to the border (below)
overshot: 400 blocks is exactly the protection range, which is exactly where
the warp-offer ring sits - so every arrival landed ON the ring and the dialog
the sailor had just come through reopened in their face. Two fixes, either of
which suffices: the arrival default is now 320 (inside the ring's inner edge at
protection − trigger = 370, with margin for the outward open-water correction),
and `BorderPromptListener` listens for `TWWarpCompletedEvent` and seeds its own
prompt cooldown at the destination - so even a config that lands arrivals on
the ring cannot reopen the dialog. Live configs need `arrival-distance: 320` by
hand; the cooldown seeding protects the ones that keep 400.

**"The DOCK sign is in the water."** Chart hologram heights are relative to the
player's feet, and a sailor in a boat has their feet at sea level - the dock
marker's 0.2 offset put it *in* the sea ten blocks out, below the horizon.
Dock marker raised to 1.5 and the island-name stack base to 2.5, preserving the
design (dock lowest, names stacked above, 0.8 per rank) with everything above
the waterline.

Also learned in the same playtest: the SAFE-cluster produce→demand triangle
(Belege → Teenla → Esedaxe on the test seed) earns as designed - "a bit of a
grind, but makes safe money."

211 tests green.

## Patrols that never ticked, and a false escape (2026-08-01)

The dispatch logging earned its place immediately - one paste of console output
answered three questions at once.

**"The mobs spawned but nothing happened."** Not BentoBox protection, which was
the natural suspect: they spawned fine, 200 blocks away at the pier, and
`simulation-distance=10` means anything past 160 blocks **never ticks**. They
did not swim, chase or do anything at all. Launching literally from the quay
was right in spirit and wrong in practice. The launch point is now pulled along
the line from pier to smuggler until it is close enough to be alive
(`patrol-distance`, now 80 - inside both the simulation distance and the
96-block entity tracking range). It reads as a patrol that has already rowed
most of the way out.

**"I was next to the dock and it said I'd reached open water."** The 120-second
chase timeout called the same `escaped()` as a genuine border crossing - same
message, same flee flag. So a player who never ran was told they had escaped
and was flagged at the port for it. Timeout is now its own outcome: the patrol
gives up and turns for home, and nothing is remembered against you.

**"I arrived too close - I'd expect to be near where the warp dialog pops up."**
Correct, and the number was indefensible: 130 blocks is *inside* the island's
own 160-block terrain radius, on its underwater shelf. Arrival is now 400, the
island's visible border - the same place the dialog offers itself on the way
out, so arriving mirrors leaving. (The 130 came from earlier feedback that the
paddle in was too long; the navigation bar and the dock hologram have since
solved being lost, which was the real complaint.)

211 tests green.

## Patrols launch from the dock, and leave bystanders alone (2026-08-01)

"Again instant ambush." Twice now I had answered that by moving the spawn
further from the player - 22 blocks, then 45 - and twice it was still an
ambush, because the distance was never the problem. **The origin was.** A
patrol that materialises near its quarry is an ambush at any radius.

Customs now launch from the **pier end** of the island's own quay, which is
where a harbour's boats put out from, and swim after the smuggler. You can see
them coming and outrun them, which is what the decision window in spec section
6 is for.

That change forced another: with the swimmers starting at the dock they will
never close on a boat, so a customs patrol now includes a **phantom**. Without
something that can genuinely pursue, "run" stops being a choice and becomes the
answer every time. (Ben also asked where the phantoms were - they only existed
in the *wanted* roster, never the customs one.)

**Police no longer hurt bystanders.** Targeting was re-asserted every couple of
seconds, but a mob can swing between ticks and a guardian's beam locks on
before any of that runs. The damage itself is now refused unless the victim is
the one the law actually wants - wanted, or being chased right now. A patrol
that hurts whoever is moored nearby is not policing, it is weather.

**Logging**, as asked: the warp arrival prints the player's coordinates and
distance from the island centre, and every dispatch prints the player position,
the pier position, the launch point, and each unit's type, coordinates and
distance from the player.

**Pitfall:** a scripted replace on `PoliceRoster` silently did not match, and
the script cheerfully printed "roster ok" anyway. The test caught it, but only
because it asserted on the roster contents. Never print success from a
replacement without checking it happened.

211 tests green.

## Contraband in pockets, and patrols that ambush (2026-08-01)

Two from the same playtest.

**"Will any sugar in any of my inventory slots be checked?"** It was not - only
the hold. That read as principled, since cargo means the hold everywhere else
in this game, but it handed smugglers a free pass: tip the sugar into your
pockets before the border, cross, stow it again on the far side. The whole
mechanic bypassed with two inventory clicks. Customs now search hold *and*
pockets, and confiscation takes from both. A customs officer searches the
sailor, not just the cargo manifest.

**"The guardians were instantly on me."** The patrol surfaced 22 blocks away
and a guardian's laser reaches 15, so it was firing before the warning had
finished printing. That is an ambush, not the decision window section 6 asks
for. Now 45 blocks (`illegal-trade.patrol-distance`), and the scatter around
that point can no longer pull units back inside it either.

**"I warped really close to the island too, which seems odd."** Measured: 37 of
864 approach bearings (4.3%) put the arrival on or beside land, because a warped
coastline can now reach the 130-block arrival ring. The fix from the
suffocation bug searched for water in *every* direction, which happily corrects
by moving the sailor inward into a bay - the exact symptom. Arrivals now step
**outward** along the approach bearing only, and require elbow room rather than
any single water block. Worst case push: 20 blocks.

211 tests green.

## The interstice: lit, lidded, and ghasts you can see (2026-08-01)

Ben: "very dark and dim... the ghast(s) I hear spawn when I failed - I never see
them, are they really there, or was it just the sound?"

**They were really there, and I had made them invisible.** Yesterday's fix for
players dying on arrival pushed the spawn distance out to 90 blocks so engaging
would be a choice. But the monster **entity-tracking-range** is 48 on a default
spigot.yml (96 on this server), so a ghast at 90-120 blocks is never sent to the
client at all - and since the same fix stopped giving them a target, they had no
reason to close the distance either. The result was a scream (which I play
myself on arrival) and nothing else, forever. Distance is now 44, comfortably
inside tracking range on any server, with the grace window and the
sometimes-nothing roll still doing the work of making arrival survivable.

A good reminder that "far enough away to be fair" has an upper bound set by what
the client is ever told about.

**Still too far (same day).** Two more faults behind that. The spawn code added
`random * 30` blocks on top of the configured distance, which quietly undid the
setting - a base of 44 was arriving as far out as 74. It is now a tight band
around the value (0.85-1.15x), and the default is 28. And nothing ever re-aimed
the ghasts: spawning them without a target stopped arrival being an ambush, but
they then drifted and the encounter simply never happened. Once the arrival
grace expires, anything nearby now acquires the castaway - the free way out is
always on offer, so staying is the choice that carries consequences.

The re-engage dialog also gained a **"Stay a while"** exit. It had exactly one
button, so the only way to put it down was to take the warp - and a player who
wants to look at the sea first should be able to.

**And the dark.** An open black sky over a black sea reads as unfinished rather
than hostile, so the interstice now has a **ceiling** (netherrack under bedrock,
48 blocks up) and **braziers**: netherrack outcrops rising out of the water with
fire burning on top, roughly one chunk in six. Fire on netherrack burns forever
and there is nothing out there for it to spread to. They are the only light in
the place, and they double as landmarks - somewhere featureless is disorienting
in a way that somewhere dangerous is not.

**Pitfall:** a careless string replace turned the live config's
`ghast-distance: 90.0` into a stray `.0` appended to the next key
(`brazier-chance: 0.18.0`). Parse the YAML after editing it, every time.

210 tests green.

## Server crash: hot-swapped jar, and a chunk-loading warp arrival (2026-08-01)

`NoClassDefFoundError: IslandPalette` inside chunk generation, which Paper
escalates to an unrecoverable chunk system failure and stops the server.

**Cause: my deploy.** The jar was written at 09:30:56 and the class failed to
load at 09:31:41, 45 seconds later, mid-session. The plugin classloader reads
classes lazily out of the jar file, so overwriting it under a running server
invalidates the handle and anything not already loaded is simply gone.
`IslandPalette` is only touched when a column turns out to be dock or plaza or
land, so it can go a long time unloaded - and then a warp arrival generated a
chunk near an island and it was needed. `scripts/deploy.sh` now refuses to
install while the server is up, and CLAUDE.md says why.

**And a real bug it exposed.** The crash landed in chunk generation *triggered
by* `SeaArrival.openSeaNear`, which read blocks - `world.getBlockAt(...)` -
spiralling out to 160 blocks. Reading a block in an ungenerated chunk forces a
synchronous load, so a single warp arrival could make the main thread generate
dozens of chunks in a row before the teleport even began. It also meant a
generation failure anywhere in that spiral took the server with it.

None of that was necessary: the sea floor and the island masks are pure
functions of (seed, position), so "is this open water?" is arithmetic. The
search now asks the galaxy - floor below sea level, and no dock or plaza
terraformed over the column - and touches no blocks at all. The interstice
passes a null engine: no islands, no docks, and a floor that cannot reach the
surface, so the intended point always serves.

210 tests green.

## The Star Chart is an instrument, not cargo (2026-08-01)

Ben: "When I did starchart I got given another one. I fear these could end up
littering."

Right on both counts, and the old `give()` was worse than described - it
dropped any inventory overflow on the ground, so a full pack meant a map
floating in the sea. Rather than making the command idempotent and leaving a
permanent item to manage, the chart is now **ephemeral**: it exists only while
you are looking at it.

It goes straight into a free hotbar slot which is then selected, so the
"put it away and it is gone" rule applies from the moment it arrives. Switching
slots reclaims it, dropping it destroys it, dying does not yield it, and
logging out takes it with you. Running the command twice reclaims the first, so
there is exactly one ever.

The slot-change rule only fires when the slot being *left* held a chart, so a
chart sitting elsewhere is never snatched before its owner has looked at it.

209 tests green.

## Dialogs close under attack, and no warping out of a fight (2026-08-01)

Ben: a dialog should exit if you are being attacked, with a bed-style action
bar - "cannot warp while enemies are close".

A dialog is a modal screen: reading the warp list while a patrol closes in
means not seeing the boat, the water, or the thing shooting. Any damage now
closes any open dialog - deliberately *any* damage, since drowning while
reading a shop menu deserves the same treatment.

The warp gate is the more interesting half, because it turns a UI nicety into a
rule: a warp is no longer a panic button out of a fight, which makes the
customs chase mean what section 6 says it means. Caught with contraband, the
choices are run, fight or jettison - warping away would have been a silent
fourth option that beat all three. Checked in three places, because the fuel is
spent at engagement: when the dialog opens, again when a destination is
clicked (a patrol can arrive while the menu is up), and during the stand-still
countdown, where it refunds.

**The interstice re-engage is exempt, and that exemption is load-bearing.** It
is the way *out* of a place designed to be dangerous, and gating it behind "no
enemies nearby" could strand a player permanently - the exact failure the free
re-engage exists to prevent. It goes through `deliver` rather than `warp`, so
it never meets the check; that is now written down in both classes so nobody
"tidies" the two paths together.

209 tests green.

## Star chart: fuel range ring, reachability, readable names (2026-08-01)

Three playtest asks, all about the chart telling a sailor what they can
actually do rather than only where things are.

**The fuel range ring.** A warp costs fuel per block of route, so the reachable
set genuinely is a circle - which is a thing a chart can draw and a number
cannot. Dashed, so it reads as an annotation rather than a wall, and drawn
before the islands so dots and names stay on top of it. It is skipped entirely
when the radius runs off the canvas: a ring clamped to the edge would be a lie
about your range, and worse than no ring at all.

**Reachability in `/tw chart list`.** A list of places you cannot afford to go
is a list of disappointments. Each entry now carries its fuel cost, green when
reachable and greyed with "(not enough fuel)" when not, under a "Fuel aboard"
line. Standing at a port the figure is the exact route price, lane overrides
included; adrift it is the same distance-based estimate measured from the
player, which is close enough to plan by.

**White names.** `MapCanvas` takes its text colour from a
section-sign/palette-index/semicolon prefix, and with no prefix the default was
a mid grey that all but vanished against the ocean blue.

209 tests green.

## /tw go: a door into the ocean, not a teleport (2026-08-01)

Ben spotted it: "Now that Spawn is a trading post, which is a good thing...
the /tw spawn command is a free way to warp back there!" Exactly right, and it
is the kind of exploit that only appears when two good decisions meet - spawn
became a real port so new players would trade there, and the free teleport that
was harmless when spawn was an empty islet turned into a free ride to a market
from anywhere in the galaxy.

First cut simply unregistered it. Ben caught the hole immediately: *"so if I'm
in another world and I run /tw, how do I start?"* Deleting the exploit had
deleted the front door - a new player, or anyone standing in another game
mode's world, had no way into the ocean at all.

So it is a door with two rules instead. It **refuses while you are already at
sea** - that is the exploit, and the whole of it. And coming in from outside
returns you to the water you **left**, not to spawn. That second rule matters
more than it looks: this server runs several game modes, so a spawn-anchored
door would have re-opened the same free ride through the side - `/acid`, then
`/tw`, and you are standing in a market. `SeaPositionTracker` records the spot
on quit and on any teleport out of a TradeWinds world, and only a sailor who
has never set out starts at the spawn port.

Recording uses `PlayerTeleportEvent`, not `PlayerChangedWorldEvent`: by the
time the latter fires the player has already moved, so their location is the
destination - the one position that is no use.

Labelled `go` (aliases `spawn`, `sail`), and both default actions point at it,
so bare `/tw` is the natural entry.

Death still respawns at the spawn plaza (you paid for that trip with your
cargo), and `/tw restart` still returns a destitute player there, capped.

205 tests green.

## Low fuel warning, and the band on the boss bar (2026-08-01)

Two playtest asks, both about the player not being told something they needed.

**Low fuel.** Running dry at a port is not a soft failure - the only way onward
is rowing - and it is only fixable while the sailor is still standing next to
the fuel. So the check happens at the port: fuel aboard against the *cheapest*
charted route out, since affording the far island is irrelevant if you cannot
afford the near one.

Ben's own caveat drove the design: "for kids, they won't read it if it isn't
obvious". So it is told three times, in three different lifetimes:
- the **action bar** repeats every 8s while ashore and short (a one-shot would
  be missed by exactly the players this is for),
- a **chat line** fires once per port and *stays* in the log, with how much more
  fuel is needed,
- the **market dialog** relabels whichever button actually sells fuel to
  "BUY FUEL HERE" - the outfitter normally, or the buy page at islands whose own
  catalog stocks fuel, since the outfitter only carries charcoal when the trade
  catalog does not.

No warning when nothing is charted: being unable to warp is not a fuel problem
then, and saying so would be a lie.

**The band on the boss bar.** "I arrived in an anarchy and I couldn't remember
what kind of island it was, and couldn't work out how to tell." The bar colour
already tracked the band, but a colour with no label is a puzzle. The bar now
reads *name | band | standing | dock*, with the band coloured in the locale
(blue Safe through bold dark-red ANARCHIC) - and the same coloured name now
appears on chart holograms and the market subtitle. Worth noting the bar's
"Clean" was the *player's standing* all along, which is easy to misread as
something about the island.

205 tests green.

## "Suffocated in a wall": warp arrivals landing inside the quay (2026-08-01)

Ben's death screen: *"BoxManager suffocated in a wall whilst fighting Ghast"*,
with the overworld navigation bar showing **Edatge | Dock 136m**. The ghast was
a red herring - it was the recent-damage attribution from the interstice, still
credited a few seconds after he re-engaged the warp and left.

Two hypotheses died before the real one. First: vanilla nether decoration
scattering blocks in the interstice's open air. **Checked it** by parsing the
interstice region files - 37,024 sections above y=72 and not one non-air block.
Second: ragged coastlines reaching past the arrival ring. Also wrong - a sweep
of every bearing around every island within 12,000 blocks found the arrival
point never lands on natural terrain.

The actual cause was in the nav bar all along. A warp arrives at a fixed 130
blocks from the island centre at **sea level + 1**, and the quay runs out to 136
blocks with its plank deck at **exactly that height**. Line the approach bearing
up with the dock bearing - 0.9% of bearings do - and the warp materialises the
sailor inside the decking. The arrival code never asked what was there.

`SeaArrival` now finds the nearest **open water** to the intended point
(searching outward, nearest first) and puts the arrival there, for both warp
arrivals and interstice strandings. Arrivals are by boat; open water is the only
sensible answer. The regression test asserts the quay collision exists *and*
that natural land does not, so the next person to read it does not go re-fixing
the coastline.

Also added `/twadmin warpfail <player>`: rigs a player's next warp to fail.
A 5% chance is miserable to reproduce on demand and the interstice needs testing
far more often than that. It toggles, and the flag is consumed by the next warp
either way, so it cannot sit forgotten on an account. Doubles as a live tool for
spicing up a session.

**Pitfall:** two plausible explanations, both wrong, and each would have led to a
real but pointless change. Parsing the world file took a few minutes and settled
it - measuring beat guessing again.

200 tests green.

## Interstice: a failed warp was killing new players (2026-08-01)

Playtest: "I was warping and got sent to the Nether. The ghasts immediately
started to attack... a dialog box popped up that I was too frightened to read
... I just died and lost everything after one warp."

The numbers were indefensible. Ghasts spawned at **20-35 blocks** - well inside
a ghast's 64-block detection range - and `setTarget(player)` was called on
arrival, so they were already hunting before the player had finished loading in.
There was no chance of an empty interstice: at least one always came. A 5% warp
failure meant a new player's first jump could cost them the boat, the cargo and
the kit, for nothing they did wrong.

The interstice is meant to be a **detour with a way out**, not a death sentence
(spec 3.3: the fuel is already spent, so re-engaging is free). Three changes:
- `interstice.ghast-chance` (0.6): sometimes nothing comes at all. Dark water
  and a long silence is unsettling on its own, and it means a failed warp is not
  automatically a fight.
- `interstice.ghast-distance` (90): ghasts appear **beyond their own detection
  range** and are no longer given a target. They are a thing you can see and
  decide about, which is the same rule the sea encounters already follow.
- `interstice.grace-seconds` (20): for a short window nothing may target or
  damage a new arrival, so the dialog can actually be read. It also covers a
  fireball already in flight - clicking "re-engage the warp" and dying to a shot
  fired before you read it is precisely the reported experience.

The grace covers the arrival only; stay and pick a fight and it is a fight.

197 tests green.

## Stage 6c — police, wanted response, bounties (2026-08-01)

Closes Stage 6. The law now answers for itself: while you are inside a policed
island's waters and wanted, patrols come.

`PoliceRoster` holds the decision and is pure, so the shape is testable without
a server. Units are picked by **mobility**, because each denies a different
escape (spec section 7): golems take anyone ashore, guardians hold the water,
and **phantoms are the only thing that can follow a boat**. A sea response
without a phantom is a response you simply row away from - there is a test
asserting every roster can pursue.

Three properties keep this from becoming a nuisance or a farm:
- **Break off at the border.** Police give up ~400 blocks past the protection
  range. Without it an escort would follow a player across the ocean and the
  security bands would flatten into one difficulty everywhere - lawless water
  has to be genuinely where the law is not. ANARCHIC sends nobody at all, which
  is the entire reason to run out there.
- **No leaks.** Units are recalled on break-off, on logout, on world change, on
  paying a fine, and on ceasing to be wanted; they are non-persistent, so an
  unloaded chunk takes them too.
- **No drops.** Already enforced for every tagged unit since 6b.

Police phantoms do not burn at dawn - a pursuit that ends because the sun came
up is not a pursuit - and an `EntityTargetEvent` guard keeps patrols off
innocent bystanders.

**PvP override**: BentoBox's own PvP listener cancels player damage at LOW
priority, so the override un-cancels it at NORMAL when the victim is a lawful
target. Without it a wanted player could moor in a SAFE band and be
untouchable, and bounty hunting would only work where it was least needed.

**Bounties are visible now**: a scoreboard team suffix beside the name while the
bounty is above zero. A bounty nobody can see is not a bounty. Because that
fights TAB-style nametag plugins, it is config-gated and PlaceholderAPI
placeholders are exposed alongside (`bounty`, `bounty_raw`, `standing`,
`reputation`, `wanted`) so a server can render it wherever it already owns the
name.

**Fugitive trade bar** was specified in 6a (`Standing.isBarredFromSafeTrade`)
but never actually enforced at a market - the method existed and nothing called
it. Now the dialog refuses to open at safe ports for a fugitive, which is
principle 4 arrived at from the other direction: smuggling pushes you outward,
and so does burning your name.

**Pitfall:** a `str.replace` on the locale for `"  customs:"` also matched the
six-space-indented admin `customs:` block and corrupted the YAML. Anchor
replacements on a newline plus the full indent, and re-parse the file
afterwards - which is how it was caught.

197 tests green.

## Stage 6b — customs and contraband (2026-07-31)

The scan on entering island space, and the chase after it. Detection is
deliberately **not** a fine (spec section 6): being caught at the border starts
a pursuit, and the water between you and the horizon is a decision - **run**,
**fight**, or **jettison**, which clears you at the cost of the cargo and
leaves it floating for anyone to take. That last one is the seed of piracy.

`CustomsService` owns the loop. Entry is detected by tracking which island's
protection range a player is inside and firing on the transition, so rowing in
and warping in are the same event - the spec asks for a scan on *every* entry,
and a teleport is an entry.

Two counters stop the obvious abuses, both from the spec:
- a **per-island scan cooldown**, so a smuggler cannot bounce across the border
  re-rolling the dice until they get a pass;
- a **flee flag**, so running is remembered: return to that port inside 20
  minutes and there is no roll at all, the patrol just launches.

The risk/reward shape has an invariant worth stating out loud, and there is now
a test for it: **the bands that buy contraband are the bands that do not scan
for it**. Safe ports search everyone and refuse to deal; anarchic ports do
neither. If that ever inverts, smuggling becomes either free money or
impossible. Standing tilts the odds both ways, which is where "positive rep
must pay" finally bites.

`PoliceDispatch` is the minimum force that makes a chase real - a sea patrol
surfacing *between* the smuggler and the port, so running for open water is a
live option and running for the harbour is not. Stage 6c builds the rest on it.
Police are PDC-tagged, non-persistent, and **drop nothing at all**: loot-bearing
police would turn a criminal record into an iron farm, which is exactly
backwards.

Contraband selling is band-gated (`safest-contraband-buyer`, default FRONTIER),
so a smuggling run is a voyage outward rather than a shortcut.

187 tests green. Open question 5 (scan/flag tuning) now has defaults to argue
with rather than blanks: 10 min scan cooldown, 20 min flee flag, 4-block arrest
radius, 400-block break-off, 120-second chase cap.

**First playtest: "not sure if it is working" - four real bugs.**
1. `<newline>` inside a `[title]` rendered as a literal line-feed glyph. BentoBox
   splits the string on the raw `[subtitle]` marker and hands each half to
   MiniMessage separately, so `<newline>` became an actual newline character
   inside a title component - and a title is one line by definition. The
   subtitle IS the second line. `CustomsLocaleMarkerTest` now walks every locale
   string and fails on a newline in a title half, a marker that is not at the
   start, or a `[subtitle]` with no `[title]`.
2. A title flashes past and is gone. Every customs alert now also sends a chat
   line, which persists.
3. **Warping away mid-chase silently disabled customs for the session.** The
   chase stayed live, and `onEntry` refuses to start a second one - so the
   destination never scanned, and neither did anywhere afterwards. Leaving an
   island's space now ends that island's chase (as an escape) *before* the new
   island is considered.
4. Logging in inside a port counted as an entry, and the patrol spawned on the
   plaza - guardians flopping on dry land. Login now records position without
   scanning, and a patrol that can find no water to launch from turns into a
   straight confiscation, which is what being caught ashore in a market should
   mean anyway.

**Second playtest: the reward half was missing, and the chase was unrunnable.**
- "Sugar trading should be high risk, high reward - but the trader only offered
  ~$1 for it." Correct, and a design hole rather than a tuning nit: contraband
  was priced from its crafting recipe like every other good, and sugar's recipe
  price is about one currency unit. The risk was built; the reward never was.
  Contraband now carries a **black-market premium**
  (`illegal-trade.contraband-price-multiplier`, default 8.0) and counts as
  *demanded* at any port that deals in it, so the existing band bonus applies
  and running further out pays more. Measured: $11.90 at FRONTIER rising to
  $14.28 at ANARCHIC, against an honest margin of $6.38 per item on a mid-value
  good - roughly double the return per hold slot, with no capital outlay, which
  is what "high risk, high reward" has to mean. A test pins the shape.
- Traders no longer **stock** contraband. If they did, a player could buy it
  over the counter at the honest price and sell it back at the premium with no
  farming and no risk - a money printer hiding behind the new premium.
- "`/twadmin customs` reports that the port does not buy it, but they did." The
  report was right and the dialog was wrong: the sell page quoted a price for
  contraband at ports that would refuse it at the counter. It now omits them.
- "When I arrived, I got instantly hurt and lost the sugar. No idea what
  happened." The water search for a patrol spawn fell back to the player's own
  position with no minimum distance, so patrols materialised alongside the boat,
  opened fire, and the chase tick registered an arrest within one tick. Patrols
  now keep a standoff of at least three times the arrest radius. A chase you
  cannot run from is not a chase.

Also added `/twadmin customs`: contraband aboard, standing, band, effective scan
chance, patrol size, chase state. Scans are probabilistic and cooldowns are
invisible, so "is it working?" was not answerable from behaviour alone - that
was the real complaint, and a diagnostic is the answer to it. 190 tests green.

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
