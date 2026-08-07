package world.bentobox.tradewinds.travel;

import java.util.Optional;
import java.util.UUID;

import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.entity.Boat;
import org.bukkit.entity.Entity;
import org.bukkit.entity.EntityType;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataType;

import world.bentobox.bentobox.api.user.User;
import world.bentobox.tradewinds.TradeWinds;
import world.bentobox.tradewinds.dataobjects.BoatHold;

/**
 * The physical half of a boat: the entity you ride and the item you carry are
 * both <b>avatars</b> of one {@link BoatHold} record, keyed by a PDC id. This
 * service creates them, keeps the name plate honest (owner's name, or
 * UNOWNED), and moves ownership when a boat is claimed or abandoned.
 *
 * @author tastybento
 */
public class BoatService {

    /** PDC key carrying the BoatHold id on boat entities AND boat items. */
    public static final NamespacedKey BOAT_ID_KEY = NamespacedKey.fromString("tradewinds:boat-id");

    private final TradeWinds addon;

    public BoatService(TradeWinds addon) {
        this.addon = addon;
    }

    // ------------------------------------------------------------ identities

    /**
     * The BoatHold id stamped on an entity, or null.
     */
    public static String boatId(Entity entity) {
        if (entity == null || entity.getPersistentDataContainer() == null) {
            return null;
        }
        return entity.getPersistentDataContainer().get(BOAT_ID_KEY, PersistentDataType.STRING);
    }

    /**
     * The BoatHold id stamped on an item, or null.
     */
    public static String boatId(ItemStack stack) {
        if (stack == null || !stack.hasItemMeta()) {
            return null;
        }
        return stack.getItemMeta().getPersistentDataContainer().get(BOAT_ID_KEY, PersistentDataType.STRING);
    }

    /**
     * The record behind a boat entity, registering one on the spot for a
     * plain vanilla boat (an unowned, empty hull - which is exactly what it
     * is).
     */
    public BoatHold recordFor(Boat boat) {
        String id = boatId(boat);
        Optional<BoatHold> existing = addon.getHoldManager().boat(id);
        if (existing.isPresent()) {
            return existing.get();
        }
        BoatHold hold = addon.getHoldManager().create(materialOf(boat), null);
        stamp(boat, hold);
        addon.getHoldManager().rememberPosition(hold, boat.getLocation());
        return hold;
    }

    /**
     * The record behind a boat item, if it has one.
     */
    public Optional<BoatHold> recordFor(ItemStack stack) {
        return addon.getHoldManager().boat(boatId(stack));
    }

    public void stamp(Entity entity, BoatHold hold) {
        entity.getPersistentDataContainer().set(BOAT_ID_KEY, PersistentDataType.STRING, hold.getUniqueId());
        entity.setPersistent(true);
        label(entity, hold);
    }

    /**
     * Stamp a boat item with its record so the cargo rides along - and wear
     * the manifest on the tin: cargo slots, fuel aboard, and how to open it.
     */
    public ItemStack stamp(ItemStack stack, BoatHold hold) {
        ItemMeta meta = stack.getItemMeta();
        if (meta != null) {
            meta.getPersistentDataContainer().set(BOAT_ID_KEY, PersistentDataType.STRING, hold.getUniqueId());
            User console = User.getInstance(Bukkit.getConsoleSender());
            Material material = Material.matchMaterial(hold.getMaterial());
            meta.lore(java.util.List.of(
                    console.getTranslationAsComponent("tradewinds.item.boat-lore-cargo",
                            "[used]", String.valueOf(HoldService.slotsUsedIn(hold)),
                            "[slots]", String.valueOf(addon.getBoatRanks().slots(material))),
                    console.getTranslationAsComponent("tradewinds.item.boat-lore-fuel",
                            "[units]", String.format("%.0f", addon.getFuelService().unitsOf(hold))),
                    console.getTranslationAsComponent("tradewinds.item.boat-lore-open", new String[0])));
            stack.setItemMeta(meta);
        }
        return stack;
    }

    /**
     * Whether the player is carrying this boat as an item right now - in
     * which case pointing a chart marker at it is pointing at their pocket.
     *
     * @param player the player
     * @param hold the boat
     * @return true if it is in their pack
     */
    public boolean isCarrying(Player player, BoatHold hold) {
        for (ItemStack stack : player.getInventory().getContents()) {
            if (stack != null && hold.getUniqueId().equals(boatId(stack))) {
                return true;
            }
        }
        return false;
    }

    /**
     * Re-write the lore of a carried avatar of this record, so the manifest
     * stays true after the hold GUI closes.
     */
    public void refreshItemLore(Player player, BoatHold hold) {
        for (ItemStack stack : player.getInventory().getContents()) {
            if (stack != null && hold.getUniqueId().equals(boatId(stack))) {
                stamp(stack, hold);
                return;
            }
        }
    }

    // ---------------------------------------------------------------- labels

    /**
     * Put the owner's name over the hull - or UNOWNED, which is the sign a
     * passer-by is actually looking for. Hidden while someone is aboard: the
     * rider is the obvious answer.
     */
    public void label(Entity entity, BoatHold hold) {
        if (!(entity instanceof Boat boat)) {
            return;
        }
        if (!boat.getPassengers().isEmpty()) {
            boat.setCustomNameVisible(false);
            return;
        }
        User console = User.getInstance(Bukkit.getConsoleSender());
        if (hold.isUnowned()) {
            boat.customName(console.getTranslationAsComponent("tradewinds.boat.label-unowned", new String[0]));
        } else {
            String name = Bukkit.getOfflinePlayer(UUID.fromString(hold.getOwner())).getName();
            boat.customName(console.getTranslationAsComponent("tradewinds.boat.label-owned", "[name]",
                    name == null ? "?" : name));
        }
        boat.setCustomNameVisible(true);
    }

    /**
     * The record's placed avatar in the world, if any is loaded: the Boat
     * entity it rides as, or the dropped item that carries its stamp.
     * <p>
     * Found by IDENTITY across the world's loaded entities, never by a box
     * around the last-remembered position - a dismounted boat glides and
     * drifts, so position boxes miss and then the caller mutates a record
     * whose hull it never touched (the moored-refit bug, 2026-08-04: the
     * yard sold a spruce hull and the oak boat kept floating at the dock).
     * A hit also re-remembers the position, so the chart heals as a side
     * effect of anyone looking.
     *
     * @param hold the boat
     * @return its placed avatar, or empty if none is loaded
     */
    public Optional<Entity> findPlaced(BoatHold hold) {
        if (hold.getWorld() == null || hold.getWorld().isEmpty()) {
            return Optional.empty();
        }
        org.bukkit.World world = Bukkit.getWorld(hold.getWorld());
        if (world == null) {
            return Optional.empty();
        }
        for (Boat boat : world.getEntitiesByClass(Boat.class)) {
            if (hold.getUniqueId().equals(boatId(boat))) {
                addon.getHoldManager().rememberPosition(hold, boat.getLocation());
                return Optional.of(boat);
            }
        }
        for (org.bukkit.entity.Item item : world.getEntitiesByClass(org.bukkit.entity.Item.class)) {
            if (hold.getUniqueId().equals(boatId(item.getItemStack()))) {
                addon.getHoldManager().rememberPosition(hold, item.getLocation());
                return Optional.of(item);
            }
        }
        return Optional.empty();
    }

    /**
     * Re-read the label of whatever avatar this record has in the world, if
     * it is loaded.
     */
    public void relabel(BoatHold hold) {
        findPlaced(hold).ifPresent(e -> label(e, hold));
    }

    // ------------------------------------------------------------- ownership

    /**
     * Make this boat the player's active boat: they own it, and whatever they
     * had becomes their unowned OLD BOAT wherever it lies.
     */
    public void claim(Player player, BoatHold hold) {
        Optional<BoatHold> abandoned = addon.getHoldManager().activeBoat(player.getUniqueId())
                .filter(old -> !old.getUniqueId().equals(hold.getUniqueId()));
        addon.getHoldManager().setActiveBoat(player.getUniqueId(), hold);
        // Relabel AFTER the switch: setActiveBoat is what strips the old
        // boat's owner, and relabelling before it kept the owner's name on
        // the plate (playtest 2026-08-02: still "BoxManager's boat")
        abandoned.ifPresent(this::relabel);
        relabel(hold);
    }

    /**
     * Refit a boat into a bigger hull: the SAME record (so the cargo never
     * moves), with the avatar in the world swapped to match. Whatever form
     * the boat is in - ridden, carried, or moored alongside - has to change
     * too, or the yard hands back a record that says Cherry Chest Boat over
     * an oak hull.
     *
     * @param player the owner
     * @param hold their boat
     * @param material the hull they bought
     */
    public void refit(Player player, BoatHold hold, Material material) {
        hold.setMaterial(material.name());
        addon.getHoldManager().save(hold);
        // Riding it: swap the vessel under them without a swim
        if (player.getVehicle() instanceof Boat riding && hold.getUniqueId().equals(boatId(riding))) {
            Location where = riding.getLocation();
            riding.eject();
            riding.remove();
            launch(player, where, hold);
            return;
        }
        // Carried: the item becomes the new hull, keeping its identity
        for (ItemStack stack : player.getInventory().getContents()) {
            if (stack != null && hold.getUniqueId().equals(boatId(stack))) {
                stack.setType(material);
                stamp(stack, hold);
                return;
            }
        }
        // Moored alongside (or lying about as a dropped item): swap the hull
        // where it actually is, found by identity - never by where the chart
        // last saw it, because dismounted boats glide and drift
        findPlaced(hold).ifPresentOrElse(placed -> {
            if (placed instanceof Boat old) {
                Location at = old.getLocation();
                old.remove();
                org.bukkit.entity.EntityType entityType;
                try {
                    entityType = org.bukkit.entity.EntityType.valueOf(material.name());
                } catch (IllegalArgumentException e) {
                    entityType = org.bukkit.entity.EntityType.OAK_BOAT;
                }
                stamp(at.getWorld().spawnEntity(at, entityType), hold);
                addon.getHoldManager().rememberPosition(hold, at);
            } else if (placed instanceof org.bukkit.entity.Item item) {
                ItemStack stack = item.getItemStack();
                stack.setType(material);
                stamp(stack, hold);
                item.setItemStack(stack);
            }
        }, () -> addon.logWarning("Refit paid for boat " + hold.getUniqueId()
                + " but no hull was loaded to swap - record now says " + hold.getMaterial()
                + " over the old hull. Should be impossible: the purchase gate saw the hull moments ago."));
    }

    /**
     * Give a player a brand new boat of this material as their active boat.
     *
     * @return the new record
     */
    public BoatHold createFor(Player player, Material material) {
        BoatHold hold = addon.getHoldManager().create(material, player.getUniqueId());
        addon.getHoldManager().setActiveBoat(player.getUniqueId(), hold);
        return hold;
    }

    /**
     * Put a boat item in the player's pack (dropped at their feet if full),
     * stamped with its record.
     */
    public void giveBoatItem(Player player, BoatHold hold) {
        Material material = Material.matchMaterial(hold.getMaterial());
        if (material == null) {
            return;
        }
        ItemStack item = stamp(new ItemStack(material), hold);
        addon.getHoldManager().rememberPosition(hold, player.getLocation());
        player.getInventory().addItem(item).values()
                .forEach(left -> player.getWorld().dropItem(player.getLocation(), left));
    }

    /**
     * Spawn a boat entity for a record and seat the player in it.
     */
    public Entity launch(Player player, Location where, BoatHold hold) {
        Material material = Material.matchMaterial(hold.getMaterial());
        EntityType type;
        try {
            // Boat item materials and boat entity types share names
            type = EntityType.valueOf(material == null ? "OAK_BOAT" : material.name());
        } catch (IllegalArgumentException e) {
            type = EntityType.OAK_BOAT;
        }
        Entity boat = where.getWorld().spawnEntity(where, type);
        stamp(boat, hold);
        addon.getHoldManager().rememberPosition(hold, where);
        boat.addPassenger(player);
        return boat;
    }

    /**
     * The material a boat entity corresponds to.
     */
    public static Material materialOf(Boat boat) {
        try {
            return Material.valueOf(boat.getType().name());
        } catch (IllegalArgumentException e) {
            return Material.OAK_BOAT;
        }
    }
}
