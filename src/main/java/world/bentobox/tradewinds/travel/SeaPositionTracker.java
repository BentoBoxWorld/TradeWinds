package world.bentobox.tradewinds.travel;

import java.util.Optional;

import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerTeleportEvent;
import org.bukkit.event.player.PlayerQuitEvent;

import world.bentobox.tradewinds.TradeWinds;
import world.bentobox.tradewinds.dataobjects.TWPlayerData;

/**
 * Remembers where a sailor left the ocean.
 * <p>
 * This is what lets {@code /tw} be a door rather than a teleport. Removing the
 * old spawn command closed a free ride home, but it also closed the only way
 * <em>in</em> from another world - and on a server running several game modes,
 * simply putting the door back at spawn would have re-opened the exploit
 * through the side: hop to another gamemode, hop back, arrive at a market.
 * <p>
 * So coming back returns you to the water you left. Only a sailor who has
 * never set out goes to spawn.
 *
 * @author tastybento
 */
public class SeaPositionTracker implements Listener {

    private final TradeWinds addon;

    public SeaPositionTracker(TradeWinds addon) {
        this.addon = addon;
    }

    /**
     * Record a player's position if they are in a TradeWinds world.
     *
     * @param player the player
     */
    public void recordPosition(Player player) {
        if (addon.inWorld(player.getWorld())) {
            store(player, player.getLocation());
        }
    }

    /**
     * Where a player left the ocean, if they ever set out.
     *
     * @param player the player
     * @return their last position, or empty for a sailor who has never sailed
     */
    public Optional<Location> lastKnown(Player player) {
        String stored = addon.getPlayerDataManager().get(player.getUniqueId()).getLastSeaPosition();
        if (stored == null || stored.isBlank()) {
            return Optional.empty();
        }
        String[] parts = stored.split(";");
        if (parts.length != 4) {
            return Optional.empty();
        }
        World world = addon.getPlugin().getServer().getWorld(parts[0]);
        if (world == null || !addon.inWorld(world)) {
            return Optional.empty();
        }
        try {
            return Optional.of(new Location(world, Integer.parseInt(parts[1]) + 0.5,
                    Integer.parseInt(parts[2]), Integer.parseInt(parts[3]) + 0.5));
        } catch (NumberFormatException e) {
            return Optional.empty();
        }
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onQuit(PlayerQuitEvent event) {
        recordPosition(event.getPlayer());
    }

    /**
     * Record the spot they left from when a teleport takes them out of a
     * TradeWinds world.
     * <p>
     * The teleport event is used rather than {@code PlayerChangedWorldEvent}
     * because by the time that fires the player has already moved, and their
     * location is the destination - the one position that is no use here.
     */
    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onTeleport(PlayerTeleportEvent event) {
        if (event.getTo() == null || !addon.inWorld(event.getFrom().getWorld())
                || addon.inWorld(event.getTo().getWorld())) {
            return;
        }
        store(event.getPlayer(), event.getFrom());
    }

    private void store(Player player, Location at) {
        TWPlayerData data = addon.getPlayerDataManager().get(player.getUniqueId());
        data.setLastSeaPosition(
                at.getWorld().getName() + ";" + at.getBlockX() + ";" + at.getBlockY() + ";" + at.getBlockZ());
        addon.getPlayerDataManager().save(player.getUniqueId());
    }
}
