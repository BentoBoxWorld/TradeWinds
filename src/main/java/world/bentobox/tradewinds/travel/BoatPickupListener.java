package world.bentobox.tradewinds.travel;

import java.util.ArrayList;
import java.util.List;

import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.entity.Boat;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerTeleportEvent;
import org.bukkit.inventory.ItemStack;

import world.bentobox.tradewinds.TradeWinds;

/**
 * Teleporting while boated brings the boat along: the boat (and any chest-boat
 * cargo) is picked up into the player's inventory, so /tw spawn never strands
 * a hull in the ocean. The warp is unaffected - it dismounts before
 * teleporting, so the player is never mounted when its teleport fires. A boat
 * carrying another player is never picked up (no stealing a shared boat by
 * teleporting away).
 *
 * @author tastybento
 */
public class BoatPickupListener implements Listener {

    private final TradeWinds addon;

    public BoatPickupListener(TradeWinds addon) {
        this.addon = addon;
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onTeleport(PlayerTeleportEvent event) {
        Player player = event.getPlayer();
        if (!(player.getVehicle() instanceof Boat boat)) {
            return;
        }
        if (!boat.getWorld().equals(addon.getOverWorld()) && !boat.getWorld().equals(addon.getNetherWorld())) {
            return;
        }
        // Never confiscate a boat someone else is still sitting in
        boolean shared = boat.getPassengers().stream()
                .anyMatch(passenger -> passenger instanceof Player other && !other.equals(player));
        if (shared) {
            player.leaveVehicle();
            return;
        }
        // Gather the hull, then remove the entity. The item MUST carry the
        // boat's record id: without it a teleport quietly turns a ship into
        // an anonymous hull and orphans its cargo.
        List<ItemStack> items = new ArrayList<>();
        ItemStack hull = boatItem(boat);
        addon.getHoldManager().boat(BoatService.boatId(boat)).ifPresent(hold -> {
            addon.getBoatService().stamp(hull, hold);
            addon.getHoldManager().rememberPosition(hold, player.getLocation());
        });
        items.add(hull);
        // A chest boat's REAL inventory is not the hold any more (the hold is
        // the record), so there is nothing in there to rescue
        boat.eject();
        boat.remove();
        // Into the inventory now (it travels with the player); overflow drops
        // at the destination once the teleport has landed
        List<ItemStack> overflow = new ArrayList<>();
        items.forEach(item -> overflow.addAll(player.getInventory().addItem(item).values()));
        if (!overflow.isEmpty()) {
            Bukkit.getScheduler().runTask(addon.getPlugin(),
                    () -> overflow.forEach(item -> player.getWorld().dropItem(player.getLocation(), item)));
        }
    }

    /**
     * The item form of a boat entity - entity type names and item material
     * names match (OAK_BOAT, OAK_CHEST_BOAT...).
     */
    static ItemStack boatItem(Boat boat) {
        Material material;
        try {
            material = Material.valueOf(boat.getType().name());
        } catch (IllegalArgumentException e) {
            material = Material.OAK_BOAT;
        }
        return new ItemStack(material);
    }
}
