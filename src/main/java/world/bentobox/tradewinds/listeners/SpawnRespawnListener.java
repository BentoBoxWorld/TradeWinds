package world.bentobox.tradewinds.listeners;

import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerRespawnEvent;

import world.bentobox.tradewinds.TradeWinds;

/**
 * Respawn safety: a player who dies in a TradeWinds world without a bed or
 * respawn anchor respawns on the spawn islet - never in the seabed. (Vanilla
 * hunts for solid ground near world spawn; in an ocean world that used to
 * mean being entombed at the bottom and dying on repeat.)
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
}
