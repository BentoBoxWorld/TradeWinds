package world.bentobox.tradewinds.listeners;

import org.bukkit.World;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityPortalEvent;
import org.bukkit.event.player.PlayerPortalEvent;
import org.bukkit.event.world.PortalCreateEvent;

import world.bentobox.tradewinds.TradeWinds;

/**
 * Seals the TradeWinds worlds against portals. The interstice must be
 * unreachable except by warp failure (Stage 5), and there is no Nether or End
 * to travel to - so portals neither form nor teleport in either TradeWinds
 * world, regardless of what other plugins (Multiverse etc.) would do with
 * them. The config flag world.nether.create-and-link-portals only stops
 * BentoBox's own linking; this listener stops everyone else's.
 *
 * @author tastybento
 */
public class IntersticePortalListener implements Listener {

    private final TradeWinds addon;

    public IntersticePortalListener(TradeWinds addon) {
        this.addon = addon;
    }

    private boolean inTradeWindsWorld(World world) {
        return world != null && (world.equals(addon.getOverWorld()) || world.equals(addon.getNetherWorld()));
    }

    /**
     * No portal blocks ever form (lighting an obsidian frame does nothing).
     */
    @EventHandler(priority = EventPriority.LOWEST)
    public void onPortalCreate(PortalCreateEvent event) {
        if (inTradeWindsWorld(event.getWorld())) {
            event.setCancelled(true);
        }
    }

    /**
     * Belt and braces: even an existing portal block teleports nobody.
     */
    @EventHandler(priority = EventPriority.LOWEST)
    public void onPlayerPortal(PlayerPortalEvent event) {
        if (inTradeWindsWorld(event.getFrom().getWorld())
                || (event.getTo() != null && inTradeWindsWorld(event.getTo().getWorld()))) {
            event.setCancelled(true);
        }
    }

    @EventHandler(priority = EventPriority.LOWEST)
    public void onEntityPortal(EntityPortalEvent event) {
        if (inTradeWindsWorld(event.getFrom().getWorld())
                || (event.getTo() != null && inTradeWindsWorld(event.getTo().getWorld()))) {
            event.setCancelled(true);
        }
    }
}
