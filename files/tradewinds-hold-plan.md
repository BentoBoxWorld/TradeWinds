# TradeWinds — Virtual Hold & Boat Mechanics: Implementation Plan
*For Claude Code. Merged from Ben's "# Boat mechanics.md" (authoritative on conflicts) and the earlier virtual-hold plan. Server-authoritative holds in the database; no real containers ever hold cargo. Clean-slate build, no migration.*

## Core model

**One Boat Rule: a player has exactly one boat, or none.** The hold is conceptually the *player's* cargo inventory; the boat they possess only sets its size. Upgrading never moves items — the slot count grows and existing contents stay put.

Storage is database-backed (BentoBox Database API); the boat item/entity is a key. While a player owns a boat, the hold is keyed to the player; when the boat becomes a dropped item, the hold contents travel with the item (item-keyed record with the hold UUID in the ItemStack PDC) until picked up or expired.

## Boat ranks (defaults; `config.yml` map `boat-material → slots`)

| Rank | Boat | Slots | | Rank | Boat | Slots |
|---|---|---|---|---|---|---|
| 1 | Bamboo Raft | 2 | | 11 | Dark Oak Boat | 12 |
| 2 | Oak Boat | 3 | | 12 | Jungle Chest Boat | 13 |
| 3 | Spruce Boat | 4 | | 13 | Mangrove Boat | 14 |
| 4 | Bamboo Chest Raft | 5 | | 14 | Acacia Chest Boat | 15 |
| 5 | Birch Boat | 6 | | 15 | Cherry Boat | 16 |
| 6 | Oak Chest Boat | 7 | | 16 | Dark Oak Chest Boat | 17 |
| 7 | Jungle Boat | 8 | | 17 | Pale Oak Boat | 18 |
| 8 | Spruce Chest Boat | 9 | | 18 | Mangrove Chest Boat | 19 |
| 9 | Acacia Boat | 10 | | 19 | Cherry Chest Boat | 20 |
| 10 | Birch Chest Boat | 11 | | 20 | Pale Oak Chest Boat | 21 |

## Expanders

- Install **only in a Pale Oak Chest Boat** (the top rank). An installed expander **occupies one C slot**; clicking it opens a nested inventory panel — same style, own TNT destroy slot, **no fuel row**.
- Proposed defaults *(veto welcome)*: nested inventory is **21 slots** (net +20 per expander); expanders cannot contain other expanders or any container item; an expander can be dragged to TNT **only when empty**; nested inventories are openable only while the expander sits in a Pale Oak Chest Boat's hold (an expander that ends up elsewhere via drop/pickup rides along as inert cargo, contents preserved, until installed again or destroyed); sold at shops only, and only at **top-tech (TL7) islands** — the endgame sink has a home port.
- No installed-count cap needed: the 21 C slots are the natural ceiling, and doubling prices (below) make the tail aspirational. A fully expanded ship approaches ~440 slots and a nine-figure spend — the "guard it with your life" endgame.

## Pricing (defaults; formulas in config)

- **Boats — quadratic:** `price = 25 × slots²`. Anchors: Bamboo Raft 100, Birch 900, Jungle 1,600, Mangrove 4,900, Pale Oak 8,100, Cherry Chest 10,000, Pale Oak Chest 11,025. Early upgrades are pocket change; the top rungs cost real trading profit but stay reachable — correct, since boats are also craftable and the shop is a convenience for players who haven't found the rarer groves.
- **Expanders — doubling:** `price = 5,000 × 2^(installed)`. First 5,000; fifth 80,000; eighth 640,000; tenth 2.56M. The curve *is* the endgame money sink — no cap required, the wallet is the cap.

## Hold GUI (BentoBox Panel API)

Six rows:

```
X T X X X X X X X
X C C C C C C C X
X C C C C C C C X
X C C C C C C C X
X X X X X X X X X
X F F F F F F F X
```

- **T — TNT destroy slot.** Lore: "Drag cargo here to destroy them." Dragging cargo onto it removes the items from the game (destroyed, NOT dropped into the world). Fuel dragged here is never destroyed.
- **C — cargo slots**, up to 21, unlocked left-to-right/top-down to the boat's capacity; locked slots render as border panes (X).
- **F — fuel slots**, 7, shown as pale-red glass panes with lore "Fuel Slot" when empty. Fuel dropped anywhere in the GUI routes to a fuel slot and stacks where possible. Fuel is freely added, moved, and **removed** — fuel does not compete with cargo and is not subject to one-way rules. Lava buckets occupy a fuel slot each; on consumption the lava is burned and the empty bucket returned.
- **X — light-blue glass border**, no lore.

**Contents auto-consolidate:** items are always stored in optimal stacks, never spread — slot usage is always minimal.

### Removal rules
Cargo can leave the hold only by being **sold** or **destroyed** (TNT slot). Transfers out to player inventory, drops, hotkeys, shift-clicks, and hopper-adjacent tricks all must fail (adversarial test list required).

### Insertion rules
**Any item may be added to the hold, one-way**, except pouches, bundles, shulker boxes, or any other container item (they would expand effective hold size). Sole exception: official Cargo Expander items, in Pale Oak Chest Boats only. If an island won't buy what you inserted, it simply wastes space until you destroy it. Customs stamping is **removed entirely** — capacity and the stock/demand pools carry the balance.

## Opening the hold
- Sitting in the boat + right-click — chest boat or not.
- Sneak + right-click the boat entity.
- Right-click the boat item in your inventory.

## Obtaining and upgrading boats

Three routes: **shop**, **craft**, **pick up a dropped boat item**.

### Shop
- Shops list only boats **bigger** than your current boat, priced per the quadratic formula above, and only up to the island's **tech level**: ranks sold ≤ TL × 3 on a 1–7 scale (config), so top boats exist only at high-tech ports. Tech gates shops only — never docking, riding, or crafting.
- Purchase replaces your current boat — **the replaced boat is destroyed**; you are buying an upgrade, not a second boat. (Grants a boat if you had none.) Contents persist untouched — capacity simply increases. Example: Pale Oak Boat (18 slots, full of granite) upgraded to Cherry Chest Boat (20) → same 18 stacks of granite, 2 new empty slots.

### Crafting
- Same replacement semantics. Crafting a boat **smaller** than your current boat is blocked (craft event cancelled with a locale message).
- A player may add a chest to their current boat at any time (vanilla boat+chest recipe) — an in-place upgrade to the chest variant's capacity.

### Dropped boat items
Boat items exist when thrown, dropped on death, or when a boat entity is broken. The hold contents travel with the item.

1. **TTL:** every dropped boat item gets an expiry timestamp **stored in the database** (survives restarts/crashes). A periodic task removes overdue items by UUID, along with their contents.
2. Pickup, item **bigger** than your boat: it immediately replaces your boat — **the replaced boat is destroyed** (upgrade semantics, same as the shop); your capacity grows; your existing contents remain and the captured contents merge in.
3. Pickup, item **smaller or equal**: you keep your boat; contents transfer from the captured item **most-valuable-first** (BlueBook base price as the value oracle) until your hold is full.
4. If your hold fills mid-transfer, you're told the transfer is partial; the remainder stays in the dropped item (still under TTL) for anyone — including you, after destroying or selling to make room.
5. **Sanctioned use:** dropping a loaded boat so another player can pick it up is the intended way to hand goods over. Not an exploit.
6. Pickup with **no current boat**: the item becomes your boat with its contents intact *(assumption — confirm)*.

### Edge cases (from Ben's doc)
- If the dropped item entity is destroyed by external means (another plugin, etc.), the inventory is lost — accept and clean the DB record.
- Boats must never be destroyed **except by lava**: trident hits, collisions, and other breakage always yield the item.
- On player death, the boat drops as an item with contents (except lava deaths). **`keepInventory` servers/regions:** the player simply keeps their boat and hold. **DeathChest servers:** the boat goes into the death chest; the player must buy a boat and fuel and physically travel there (no teleporting per DeathChest rules); on retrieving their old boat, normal pickup/upgrade rules apply and the recovery boat — being smaller — is the one removed.
- A mob (e.g. zombie) picking up the boat item: **works for free under this model** — contents are DB-keyed via the ItemStack's PDC, which survives mob pickup; kill the zombie, the item drops, hold intact. TTL handling while mob-held: pause or extend TTL while an entity holds it (recommend: pause).
- Overfull situations (e.g. capacity shrinks by admin action): player must choose what to keep; open the GUI in triage mode where excess must be destroyed before normal use resumes.

## Data model

```
PlayerHold (DataObject)         DroppedBoat (DataObject)
  uniqueId  : player UUID         uniqueId   : hold/item UUID (in item PDC)
  boat      : material or none    boat       : material
  contents  : [{material, amt}]   contents   : [{material, amt}]
  fuel      : [{material, amt}]   fuel       : [{material, amt}]
                                  expiresAt  : timestamp (TTL)
```
Ownership transitions (drop → pickup → replace) are atomic DB moves between the two objects. Duplicating a key item cannot duplicate cargo — contents exist once, server-side.

## Trade & customs integration
- Plaza trading transacts against the player's hold — with One Boat there is never ambiguity.
- Customs scans the player's hold (and the dropped-boat record if they're carrying a boat item — not possible under One Boat, but guard anyway).
- Smuggler evidence-dumping = destroying contraband via the TNT slot before police close.
- Fuel checks (warp dialog, low-fuel warnings) read the fuel slots only.

## Resolved rulings (this revision)
1. Expanders **kept**, Pale-Oak-Chest-only, nested-inventory model (see Expanders).
2. Boat pricing **quadratic**, expanders **doubling** (see Pricing) — proposed defaults awaiting veto.
3. Replaced boats are **destroyed** — purchases and bigger-boat pickups are upgrades.
4. Customs stamping **removed entirely**.
5. `keepInventory` / DeathChest behavior defined (see edge cases).
6. Destroy-only jettison confirmed.

Remaining proposed-not-ruled defaults, all in the Expanders section: nested size 21, no nesting, empty-before-destroy, openable only while installed in a Pale Oak Chest Boat, sold at industrial islands.

## Acceptance tests
- One Boat invariant holds through every path: shop, craft, pickup, death, admin gives.
- Upgrade (shop/craft/chest-add) preserves contents exactly; smaller purchases/crafts impossible.
- Dropped-item TTL fires across a server restart; mob-held items survive with contents.
- No GUI interaction sequence extracts cargo to player inventory; fuel moves freely both ways; TNT destroys cargo, never fuel.
- Pickup transfer is most-valuable-first, partial transfers leave the remainder intact and claimable.
- Death always yields the boat item with contents (except lava); trident/collision yields the item, never destruction.
- Expanders: installable only in Pale Oak Chest Boats; nested GUI never accepts containers or expanders; TNT refuses a non-empty expander; nth-expander price = base × 2ⁿ; expander riding in a smaller boat after pickup is inert but its contents survive round-trips.
- Tech gating: a TL n shop never lists boats above rank 3n or (below TL7) expanders; crafting is unaffected by tech level.
