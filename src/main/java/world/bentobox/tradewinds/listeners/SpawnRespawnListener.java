package world.bentobox.tradewinds.listeners;

import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerRespawnEvent;

import world.bentobox.bentobox.api.user.User;
import world.bentobox.tradewinds.TradeWinds;

/**
 * Respawn safety: a player who dies in a TradeWinds world without a bed or
 * respawn anchor respawns on the spawn islet - never in the seabed. (Vanilla
 * hunts for solid ground near world spawn; in an ocean world that used to
 * mean being entombed at the bottom and dying on repeat.)
 * <p>
 * Death also drops the boat where they fell, so a respawned sailor would be
 * marooned ashore with no way back to their own wreck. The port lends them a
 * boat ({@code boats.respawn-boat}, default a bamboo raft; NONE disables) -
 * but only if they have none, so keepInventory deaths and bed respawns are
 * never double-boated.
 *
 * @author tastybento
 */
public class SpawnRespawnListener implements Listener {

    private final TradeWinds addon;

    public SpawnRespawnListener(TradeWinds addon) {
        this.addon = addon;
    }

    @EventHandler(priority = EventPriority.HIGH)
    public void onRespawn(PlayerRespawnEvent event) {
        World world = event.getPlayer().getWorld();
        if (!world.equals(addon.getOverWorld()) && !world.equals(addon.getNetherWorld())) {
            return;
        }
        grantRespawnBoat(event);
        if (event.isBedSpawn() || event.isAnchorSpawn()) {
            return; // they made a home somewhere - honor it
        }
        World overworld = addon.getOverWorld();
        if (overworld == null) {
            return;
        }
        // The spawn island's own spawn point - the market plaza, and wherever
        // an admin has since moved it. The island CENTRE is wooded ground and
        // must not be used: players were respawning in the treetops.
        Location point = addon.getIslands().getSpawnPoint(overworld);
        event.setRespawnLocation(point != null ? point : overworld.getSpawnLocation());
    }

    /**
     * The port's loaner: a boatless respawner gets the configured hull (their
     * own boat is floating back where they died, and rowing out to reclaim it
     * is the intended recovery trip).
     */
    private void grantRespawnBoat(PlayerRespawnEvent event) {
        Material boat = respawnBoat();
        if (boat == null || boatWithinReach(event)) {
            return;
        }
        // They have no boat, or theirs is far away: lend a hull. If they had
        // one, boarding this raft is what abandons it (with the standard
        // confirmation) - the loaner itself takes nothing from them.
        var hold = addon.getHoldService().active(event.getPlayer().getUniqueId()).isEmpty()
                ? addon.getBoatService().createFor(event.getPlayer(), boat)
                : addon.getHoldManager().create(boat, null);
        addon.getBoatService().giveBoatItem(event.getPlayer(), hold);
        User.getInstance(event.getPlayer()).sendMessage("tradewinds.boat.respawn-given", "[material]",
                world.bentobox.tradewinds.economy.PriceEngine.prettify(boat.name()));
    }

    /**
     * Whether their own boat is close enough to the respawn point to walk to
     * - otherwise a sailor is stranded ashore with a ship an ocean away.
     */
    private boolean boatWithinReach(PlayerRespawnEvent event) {
        var hold = addon.getHoldService().active(event.getPlayer().getUniqueId());
        if (hold.isEmpty() || hold.get().getWorld() == null || hold.get().getWorld().isEmpty()) {
            return false;
        }
        Location respawn = event.getRespawnLocation();
        if (respawn.getWorld() == null || !respawn.getWorld().getName().equals(hold.get().getWorld())) {
            return false;
        }
        int reach = addon.getSettings().getIslandProtectionRange();
        return respawn.distanceSquared(new Location(respawn.getWorld(), hold.get().getX(), hold.get().getY(),
                hold.get().getZ())) <= (double) reach * reach;
    }

    /**
     * The configured respawn boat, or null when disabled (NONE/blank/unknown
     * or off-ladder names all disable - an admin typo must not hand out air).
     */
    private Material respawnBoat() {
        String name = addon.getSettings().getRespawnBoat();
        if (name == null || name.isBlank() || "NONE".equalsIgnoreCase(name)) {
            return null;
        }
        Material material = Material.matchMaterial(name);
        return material != null && addon.getBoatRanks().slots(material) > 0 ? material : null;
    }
}
