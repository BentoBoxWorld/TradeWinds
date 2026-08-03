# Boat mechanics

## One Boat Rule:

Players only have one boat at a time or no boat.

## Boat Inventory GUI
The inventory of a boat is accessible by sitting in the boat and opening inventory, whether it has a chest on it or not, or sneaking up to the boat and right clicking on it, or by having the boat in your inventory and right clicking on it.

The boat inventory is its own GUI panel (uses the BentoBox Panel API) with up to 21 slots available for cargo, varies by the size of the boat, a TNT icon which is used to destroy items by dragging and dropping them to it, and a set of fuel slots. 

XTXXXXXXX
XCCCCCCCX
XCCCCCCCX
XCCCCCCCX
XXXXXXXXX
XFFFFFFFX

T = TNT, with lore saying Drag cargo here to destroy them.
C = Cargo slot
F = Fuel slot - pale red glass panel if not occupied with lore saying "Fuel Slot"
X = Light blue glass panel border, no lore.

If the boat only has 2 cargo slots, then only two are blank. The rest of the slots are X.

Fuel can be dragged and dropped into the GUI from the player's inventory. If it is dropped anywhere, it will take up a Fuel slot, and stack, if possible. Fuel can be taken out of the GUI or moved within the gui. Fuel will not be destoyed if dragged to the TNT. Only cargo.

## Boat Inventory Management
In general, items are stored in optimal stacks and not spread out, so the number of slots is always optimized. 

### Removing items

You can destory (jetison) items. This removes them from the game and they are not thrown out. Picking up the items and dropping them on the TNT item will destroy them. Trying to transfer items out of the inventory or throw them does not work.

### Adding items

It is possible to add any item you want to the hold except for pouches or shulker boxes. Howeverm it;s a one-way transfer. If you cannot sell it, it will just take up room, and you'll need to destroy it. Items that hold other items are not allowed because they expand the hold size.


## Scenarios

### Upgrading boat (upgrading inventory size)

There are three ways you can obtain a boat:

1. Buy a new boat from the shop
2. Craft a boat
3. Pick up a boat item

#### Shops

You can only buy boats that are bigger than what you have. They cost more based on how many slots of inventory they provide. So an Mangrove Boat with 14 slots is 7x the cost of a Bamboo Raft with 2 slots. When you buy it, it replaces any boat in your inventory, or gives you a boat if there isn't one in your inventory. Remember, it's actually your personal inventory size that its changing. As such when you buy a bigger boat, all the inventory in the previous boat is now in that inventory (it actually does not "move", all that has happened is that your inventory slot size has increased).

Examples:
1. Player has no boat. He buys a Birch Boat. He now has 6 slots that are empty.
2. Player has a Pale Oak Boat with 18 slots in his inventory. They are full of granite. He upgrades to a Cherry Chest Boat, which as 20. When he opens the Cherry Chest Boat's inventory he will see 18 slots full of granite, and 2 empty slots. The Pale Oak Boat is removed from his inventory and replaced.
3. Player has a Birch Boat. They cannot buy anything smaller at the shop.

# Crafting a boat

This is the same as buying a boat, except the player has done it by themselves. If the boat crafted is smaller than the one they have, then it cannot be crafted. Players can take their current boat, and add a chest to it at any time, if they can get a chest or make a chest.

### Dropping and picking up boats

Boats are items and can be dropped. This might be because a player throws it, or dies somehow, or the boat is broken somehow, see edge cases. When the boat is an item, the inventory that was "in" the boat goes with the item. It is now freely available for a player to pick it up. The rules are as follows, and are similar to the shop, but different because the boat item may have items in its inventory:

1. Boat items have a time-to-live. If they are not picked up within a period of time, they are auto removed from the game. See edge case section for how to handle server restarts.
2. If the boat item is a higher rank (bigger) than the boat the player has, then it immediately replaces the player's current boat and the player's boat inventory size becomes bigger as result.
3. If the boat is smaller, they keep their current boat.
4. Inventory from the captured boat is transfered to the player's boat inventory.
5. The game will try and move in the most valuable items from the salvaged boat first. The player's current inventory remains.
6. If the player's inventory is full, the player is told that not all the items could be transfered and transfer stops. 
7. Items that cannot be transfered stay in the item boat item to be picked up by someone else, or by the player if they jetison (destroy) items in their boat's inventory to make space, or go and trade items to make room and then return.
8. If the item boat's TTL expires, it is removed from the game along with any items it had in it.

#### Examples

1. Player finds a boat item. The player has a Jungle Boat (8 slots, with 4 of them filled) and the item is a Bamboo Raft (2 slots with items in one of the slots). The player picks up the bamboo raft item and all the items from it transfer to the Jungle Boat and the bamboo raft is removed from the game.
2. Player finds a boat item. The player has a Jungle Boat (8 slots, with all 8 of them filled) and the item is a Bamboo Raft (2 slots with items in one of the slots). The payer cannot pick up the bamboo raft item because their slots are full.
3. Player finds a boat item. The player has a Jungle Boat (8 slots, with  7 of them filled) and the item is a Bamboo Raft (2 slots full with items in both of the slots). The player receives the contents of one of the slots, and the bamboo raft remains with one slot of items in it. The player cannot pick up any more.
4. Player fills up their Oak Boat with 3 stacks of raw iron by buying it at the plaza. They throw it to the ground in the plaza, so they now have no boat. Another player can pick it up. This enables players to transfer bought items between each other. Not an exploit, but allowed.   


#### Edge cases
1. If the item is subsequently destroyed somehow, then the inventory is lost, e.g., the item entitiy is removed by another plugin.
2. Broken boat e.g., by a trident, hitting something, etc.. Boats must not be destoyed except by being thrown in to lava. If a drowned throws a trident and hits the boat, it should just become an item.
3. If a mob picks up the boat, e.g., zombie (not sure if this is possible), then it'd be great if the inventory can be kept if the zombie is killed, but I'm not sure if this will be possible.
4. Boat items have a time to live and this should survive server crashes or restarts. I recommend the time to remove the item is stored in the database as a future timestamp. A period check is done and if any item is overdue, then it is removed from the game by UUID reference.
5. When a player dies, their boat *must* always be a dropped item. It should never be destroyed, unless they fall in lava.
6. If a player is in a situation where there is too much inventory for their hold size, then they have to pick what to keep. 



# Multiple boats

Starting point:
Player has a chest boat with some inventory in it.

Scenarios:
1. It is left somewhere as an entity - e.g., moored by the dock
2. It is in the player's inventory
3. It has become an item, and is floating in the ocean, or on the ground.

What happens in these situations for all the above scenarios?
1. Player picks up or is given a bamboo raft boat item. The bamboo raft has no inventory in it.
2. Player picks up or is given a bamboo raft boat item. The bamboo raft has some inventory in it.


a) yes, you pick up the goods from the smaller hull, if there any any, otherwise, the hull just stays on the ground (and is ultimately removed from the game by the TTL).

For the others, I think we need some rules about boat proximity to the island and trading. i.e., if your boat is not in the island space (this is a BentoBox API check) then you cannot trade at the island. You must have a boat somewhere within the island space.

Next, what happens if you abandon a boat, that is you leave the boat unattended?

1. Running /tw chart will show you where your active BOAT is, similarly to the DOCK option. This works anywhere in the world.
2. If you leave it unattended in any island space, it is protected from other players jumping into it or it being broken, even by mobs. This works for any island except anarchy islands.
3. If you leave a boat unattended outside of any island space, then it is fair game for capture. It is still technically yours but cannot be used for any trading because it is not in an island space. If you get into another boat, then it is no longer your main boat, and the /tw chart will show OLD BOAT until either the boat is taken by another player, or destroyed somehow. This is how players can recover their boat if they die. For example, they leave their boat at a dock, get killed by mobs on the island, go back to spawn, get a bamboo raft, and row back to the old boat, and then get into it.  
4. If another player jumps into an unattended unprotected boat, then they acquire the boat and everything in it. Their current boat, if it exists, shows on the chart as OLD BOAT.
5. Being a passenger in a boat does not take over a boat.

Special situation: logging off while in your boat, or when the boat is moored somewhere.

1. If your boat is in a protected area, then it is protected while you are logged out. Nothing to worry about.
2.  If the boat is not in a protected area or the island is anarchic, your boat stays in the world, but someone could take it. If that happens, then when the player logs in they should get a chat message telling them that it was taken/stolen. If they have no boat and are dropped into water as a result, give them a bamboo raft to enable them to paddle somewhere.

If players want to avoid this, they can  will have to jump out of the boat, break it, and store it in their inventory before logging out if they want it to be protected.

