# TradeWinds — Manual Test Plan

What only a live server can prove. Automated coverage lives in `src/test`
(248 tests); this is the rest.

**Ordered by value, not by stage.** Work top to bottom and stop when you run
out of time — everything above the line you reach is more likely to be broken,
or worse to be broken, than everything below it. Tier 1 is fifteen minutes and
catches what makes the game unplayable; Tier 6 is polish.

The pre-2026-08-02 stage-by-stage list lives in `docs/TESTING-archive.md`. It
covers a lot of mechanics that no longer exist (pouches, customs stamps,
carried expanders, the chest boat as a hold) — read it for history, not for
coverage.

## How to run

```bash
./scripts/deploy.sh          # builds, then installs - refuses while the server is up
```

The script will not overwrite a running server's jar: replacing it live
invalidates the plugin classloader and takes the server down inside chunk
generation. Stop the server first; the guard also waits for `latest.log` to go
quiet for a minute.

**Clean slate:** after any change to the hold, boat or galaxy model, delete the
`tradewinds_world*` folders **and** the `BoatHold`, `TWPlayerData`,
`TWIslandData` database folders. Stale records from a previous model are
indistinguishable from bugs.

Two accounts are needed for part of Tier 3. Everything else is single-player.

---

# Tier 1 — Smoke test (~15 min)

If any of these fail, stop and report: the game is unplayable and the rest is
noise.

## Boot

- [ ] Server starts with no TradeWinds errors or warnings. (Duplicate-key YAML
      warnings are a build bug the suite should have caught — mention any.)
- [ ] `bbox version` lists TradeWinds ENABLED alongside the other gamemodes.
- [ ] `tradewinds_world` and `tradewinds_world_nether` exist; no `_the_end`.
- [ ] `addons/TradeWinds/config.yml` has the current sections: `galaxy`,
      `boats`, `border`, `economy`, `travel`, `illegal-trade`.
- [ ] Other gamemodes still work (create or visit an AcidIsland island).

## The first ten minutes of a new player

- [ ] `/tw` puts you at the spawn port: plaza, stalls, villagers, dock. Not in
      the treetops, not in the seabed.
- [ ] You get an **Oak Boat**, and the message names it, its 3 cargo slots and
      the coal in its fuel tank.
- [ ] The boat item's lore reads `Cargo: 0/3 slots`, `Fuel: 64 units`,
      `Right-click to open the hold`.
- [ ] Right-click the boat item: the hold opens — TNT slot, 3 open cargo slots,
      the rest grey, 7 fuel slots holding the coal.
- [ ] Place the boat on water and board it: **no dialog**, no OLD BOAT, coal
      still aboard. (Historically the most-broken path in the game — go slowly.)

## The core loop

- [ ] Open the market at the plaza and buy cargo: it lands in the hold, money
      leaves, slots fill.
- [ ] Row to the island border: a **red** particle curtain, and the warp dialog
      fires as you touch it — not 30 blocks early.
- [ ] Warp: you and the boat arrive facing the **dock**; hold forward and you
      reach it.
- [ ] Sell the cargo at the new island at a different price than you paid.
- [ ] **Notable goods resurface elsewhere.** Sell an enchanted tool at a port,
      then check the *neighbouring* ports' Secondhand shelves: it appears at one
      of them, not at the port you sold it to. Ordinary cargo never appears.
- [ ] **The shelf is not a money printer.** A shelf item costs visibly more than
      the same port would pay you for it.
- [ ] **A full hold does not eat a listing.** With no free slots, try to buy a
      shelf item: refused, and the item is still on the shelf.
- [ ] Shelf items cannot be withdrawn from the hold afterwards (they are bought
      cargo), and listings disappear after `resale-ttl-hours`.
- [ ] **The counter says when to stop.** On the sell page, a good's tooltip states
      how many more the port will take at that price. Sell that many: the price
      drops and the depth reads near zero.
- [ ] **The logbook only knows where you have been.** `/tw prices` on a fresh
      player says the logbook is empty. Open a port's market, run it again: that
      port appears with an age. A port you have only *charted* never appears.
- [ ] `/tw prices metals` sorts ports by what they paid for metals, best first.
- [ ] **Harbour reports fill the logbook.** Buy one at a high-tech port: several
      nearby ports appear in `/tw prices` at once, money leaves, and a low-tech
      port offers a smaller report (or none).
- [ ] **The hold works both ways.** Mine cobblestone on an islet, load it into
      the hold, then left-click it in the hold window: it comes back to your
      inventory. With a full inventory the overflow drops at your feet, never
      vanishes.
- [ ] **Bought cargo cannot be withdrawn.** Buy cargo at a market, then try to
      left-click it out of the hold: refused, with a message saying it leaves by
      sale or by the TNT. Selling it still works.
- [ ] **Bought and mined goods do not merge.** Buy 5 diamonds and mine 20: they
      occupy separate slots, and only the mined 20 can be withdrawn.
- [ ] Hold gestures: left-click withdraws, shift-click sends coal to the fuel
      row, right-click selects for the TNT.
- [ ] **Boat capacity matches the ladder.** An oak boat's hold says **3 slots**,
      a bamboo raft 2, a pale oak chest boat 21. If any reads a multiple of ten,
      `config.yml` has drifted from the code defaults - `ConfigAgreementTest`
      should have caught it.
- [ ] **The sell page tells identical goods apart.** With one enchanted and two
      plain iron swords aboard, the sell list shows two rows: "Iron Sword ✦ x1"
      and "Iron Sword x2", the enchanted one priced higher. No row shows a raw
      locale key like `tradewinds.ui.market.item-enchanted`.
- [ ] **Picking a good shows it and prices it live.** Click a row: its icon
      appears (hover for the real tooltip - enchantments, damage), with the unit
      price and how much more the port will take. Sell 1: the page stays open and
      the price and depth **move**. Sell the last one and you drop back to the
      list.
- [ ] **NBT survives the hold, and a restart.** Load an enchanted, a renamed and
      a half-broken tool into the hold. Each takes its own slot and keeps its
      name, enchantments and damage bar. Restart the server: all three are still
      exactly what they were. (This cannot be unit-tested - ItemStack
      serialisation does not work headlessly.)
- [ ] **Condition and enchantments move the price.** A worn tool sells for
      visibly less than a mint one; a Silk Touch pick for more than a plain one;
      a Potion of Strength II for far more than a water bottle. Two differently
      enchanted swords appear as two separate rows on the sell page.
- [ ] **The TNT destroys what you picked.** With both a plain and an enchanted
      sword aboard, select the enchanted one and destroy it: the plain one is
      still there.
- [ ] **Scavenged loot sells.** Kill a few mobs on an islet, load bones, string,
      rotten flesh and gunpowder into the hold, and sell at any port: every one
      of them has a price, visibly lower than a trade good of similar book value.
- [ ] `/twadmin priceaudit` writes `price-audit.txt` to the addon folder. Read
      the UNPRICEABLE list: anything a player could plausibly acquire and carry
      wants a base price adding.
- [ ] **A backwater refuses treasure.** Carry something rich and exotic (totem,
      nether star) into a low-tech port: it is absent from the sell page, and
      selling it by any other route says the port has no use for it. The same
      item sells at a high-tech island. Check a *low-tech luxury* island still
      buys diamonds - trade goods are exempt from this gate.
- [ ] **Dumping junk does not move honest cargo.** Note a port's iron price, sell
      it a boatload of dirt and rotten flesh, and check the iron price is
      unchanged - salvage drifts in its own pool.
- [ ] **Selling in saturates a port by value, not volume.** Sell ~7 diamonds (or
      12 emeralds) at one island: the unit price should fall to the floor and
      stop falling. Selling a few hundred wheat should move it about as much as
      those 7 diamonds did, and a boatload of cobblestone barely at all.
      Prices recover after ~3 hours of real time.
- [ ] `/tw chart` raises holograms: island names and a DOCK marker, and no BOAT
      marker while you are sitting in the boat.

---

# Tier 2 — The hold (~20 min)

The hold is virtual and one-way. Any route that gets cargo out unintentionally
is a duplication bug, which is the worst kind.

- [ ] Click items in your pack to deposit: fuel to the fuel row, everything
      else to cargo.
- [ ] **Refused:** bundles, shulker boxes, chests, barrels, and any boat.
- [ ] **Nothing extracts cargo.** In the hold GUI try shift-click, number keys,
      double-click-gather, drag-split, cursor swap, drop key. All must fail.
- [ ] Fuel *does* come back out on click, and fuel-valued **cargo** (coal
      bought at market) moves to the fuel row when clicked.
- [ ] Select a cargo stack, click the TNT: destroyed, not dropped.
- [ ] Cargo consolidates: 40 + 30 wheat is 2 slots, not 2 spread stacks.
- [ ] Fill the hold, then buy more: refused with a message and no money taken.
- [ ] Deposit fuel, close the GUI, hover the boat item: the lore has updated.
- [ ] In a **chest** boat, press the inventory key while riding: the hold
      opens. (A plain boat cannot do this — Minecraft never tells the server.)

---

# Tier 3 — Boats: ownership, capture, loss (~30 min, two players)

The newest and most bug-prone system. Every check here has had a real bug
behind it.

## One boat

- [ ] The shipwright lists only boats **bigger** than yours, and only up to the
      island's tech level.
- [ ] With **no** boat, buy a Bamboo Raft: item in your pack, money gone once,
      hold opens with 2 slots.
- [ ] With a boat **at the port**, buy a bigger hull: same hold, same cargo,
      more slots — and the hull you ride or carry visibly becomes the new type.
- [ ] With your boat **elsewhere**, the yard refuses the refit and says to
      bring the ship in.
- [ ] Craft a bigger boat **by clicking the result** (not shift-clicking): the
      item has lore and right-click opens the hold. Repeat with shift-click.
      Crafting a smaller one is refused.

## Finding a hull

- [ ] Walk over an unowned boat item while you own a boat: one dialog (not one
      per tick) offering **Take the boat**, **Take only the cargo**, **Leave it**.
- [ ] "Take only the cargo" appears only when your own boat is within 200 blocks
      and the hull carries something; with your boat on another island the
      option is gone and the dialog says why.
- [ ] **Nothing teleports:** with your boat far away, no route moves cargo to it.
- [ ] Take a hull while carrying your own: you end with **one** boat item, all
      the cargo in it, and the old hull on the ground. Never two in the pack.
- [ ] Immediately after that swap, standing on the shed hull does NOT re-open
      the dialog; after ~5 seconds it does. Dropping and re-picking your OWN
      boat works at once, even inside that window.
- [ ] Right-clicking a boat item that is not your ship does nothing.
- [ ] Boarding an unattended hull offers the same three answers.

## Capture and loss (two players)

- [ ] Player 2 online when player 1 takes their boat: told immediately.
- [ ] Player 2 then has no boat: no cargo slots at market, **no** low-fuel
      warnings, **no** OLD BOAT on the chart (it was taken, not abandoned).
- [ ] Player 2 can still use the **outfitter and shipwright** — buying a hull is
      the only way off the island. Only Sell/Buy cargo are withheld.
- [ ] Restart the server: no repeat "taken while you were away", and player 1
      still owns the boat.
- [ ] Player 2 offline when it happens: told once, at next login.

## Protection and abandonment

- [ ] Boat plates read the owner's name, or UNOWNED; hidden while ridden.
- [ ] Another player's unattended boat in protected island space cannot be
      boarded or broken — mobs included. On an ANARCHIC island it can.
- [ ] Unowned hulls are takeable anywhere, even at a safe dock.
- [ ] Chart markers point only at boats you must travel to: never one you carry,
      never one under you, never one within ~32 blocks.
- [ ] `/tw chart list` names both boats with coordinates and distance, or says
      plainly that you have none.

## Death and recovery

- [ ] Die: the boat stays floating where it was and is still yours.
- [ ] Respawn with the ship far away: you are lent a Bamboo Raft. Board it →
      confirmation → your real boat becomes OLD BOAT.
- [ ] Row back and board it: yours again, and the marker clears.
- [ ] Lava is the only thing that destroys a boat and its cargo.
- [ ] Log out in open water: another player can take the boat. Log out inside
      protected space: safe.

---

# Tier 4 — Economy depth (~20 min)

- [ ] Every price on every chart is a **whole coin** - no cents anywhere -
      and the market's figures match your balance line's formatting (both come
      from the server economy's own formatter now).
- [ ] Buying then selling the same good at the same island always LOSES money,
      including on the cheapest goods (sand, stone, kelp) where rounding is
      proportionally largest.
- [ ] Prices differ by island **type** (a farm sells food cheap) and by **tech**
      (high tech sells metals cheap, pays more for ore).
- [ ] Selling a lot of one category into one island visibly depresses its price,
      and it recovers over an hour.
- [ ] Tech shows everywhere an island is named: market, chart, warp tooltip,
      nav bar.
- [ ] Contraband (sugar) sells only at FRONTIER or rougher, at a premium.
- [ ] `/tw restart` when destitute: fresh kit, chart kept, hold emptied.
- [ ] Charity with no boat and no money: a Bamboo Raft.
- [ ] **Expanders:** sold only at Tech 7, install only into a Pale Oak Chest
      Boat, price doubles each time. The nested panel refuses containers, the
      TNT refuses a loaded expander, and in a smaller boat they ride inert but
      their contents still sell.

---

# Tier 5 — Crime (~25 min)

- [ ] Entering island space triggers a customs scan at a rate matching the band
      (SAFE often, ANARCHIC never). Clean scans announce themselves.
- [ ] Caught with contraband: the patrol launches from the pier and you can see
      it coming — flee, fight, or destroy the evidence via the TNT slot.
- [ ] Escaping across the border flags you at that island; a chase that times
      out does **not**.
- [ ] Reputation moves on crimes and decays with clean play; the band changes
      your scan odds.
- [ ] Wanted: police respond, the bounty shows over your head, and killing a
      wanted player pays out exactly once.
- [ ] Police drop nothing, ever, and never hurt bystanders.
- [ ] Fugitives are barred from safe-band markets.

---

# Tier 6 — World, atmosphere, persistence (~20 min)

- [ ] Islet biomes suit their sea: snowy in frozen water, desert and mangrove in
      warm. Cherry grove and pale garden exist somewhere.
- [ ] Surfaces match the biome: desert sand over sandstone, badlands red sand
      over terracotta, mangrove mud, old-growth taiga podzol, peaks bare rock.
- [ ] Roughly one islet in four carries a biome-appropriate structure (igloo,
      fossils, ruined portal, abandoned camp). Mushroom and pale garden islets
      never do.
- [ ] **No jigsaw or structure blocks are visible** in any of them. A ruined
      portal still stands on its netherrack footing; a camp tent has no
      glowing blocks holding it up. (Find several - the camps and portals
      1/2/4/5 are the ones that carry connectors.)
- [ ] Plaza amenities scale with tech: the galley everywhere, then furnace and
      stonecutter, smithing and grindstone, brewing, anvil, and at Tech 7 an
      enchanting table with bookshelves. All usable as a visitor, none
      breakable.
- [ ] Border curtains: blue at the edge of island space, red at the warp ring,
      both passable. Colours follow `border.*`; nonsense values fall back rather
      than vanishing.
- [ ] A failed warp: the interstice is lit by braziers, lidded, the ghasts are
      visible, and the free re-engage always works.
- [ ] Restart mid-voyage: boats, cargo, fuel, ownership, chart and island stock
      all survive.
- [ ] Warp arrival never lands you inside terrain or on the quay.

---

# Regression watchlist

One-line re-checks for bugs already fixed once. Cheap to run, and every one of
these reached a playtest before.

- [ ] Placing your own boat and boarding it does **not** offer to capture it.
- [ ] `/tw spawn` while riding: the boat comes with you and keeps its cargo.
- [ ] A warp arrival does not immediately re-open the warp dialog.
- [ ] The DOCK hologram floats above the waterline, never in the sea.
- [ ] Chart holograms do not vanish seconds after a second `/tw chart`.
- [ ] The expander opens by right-click **at sea** (aiming at nothing) as well
      as ashore.
- [ ] A wrecked or dropped boat item never despawns on vanilla's 5-minute clock;
      it runs its own TTL.
- [ ] Customs patrols actually move (anything past ~160 blocks never ticks).
- [ ] Trident hits and collisions yield the boat as an item, never destroy it.
- [ ] Villagers and golems do not walk into the plaza campfire.
