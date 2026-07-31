package world.bentobox.tradewinds.travel;

import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerMoveEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.event.vehicle.VehicleEnterEvent;

import net.kyori.adventure.text.Component;
import world.bentobox.bentobox.api.user.User;
import world.bentobox.tradewinds.TradeWinds;
import world.bentobox.tradewinds.api.events.IslandChartedEvent;
import world.bentobox.tradewinds.dataobjects.TWPlayerData;

/**
 * Charting: the discovery layer over lazy generation. Physically entering an
 * island's range puts it on your chart - only charted islands appear in the
 * warp dialog. Movement checks are gated to chunk crossings to stay cheap.
 *
 * @author tastybento
 */
public class ChartingListener implements Listener {

    private final TradeWinds addon;

    public ChartingListener(TradeWinds addon) {
        this.addon = addon;
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onJoin(PlayerJoinEvent event) {
        // Load (and pre-chart the starter cluster for) the player's data
        addon.getPlayerDataManager().get(event.getPlayer().getUniqueId());
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onQuit(PlayerQuitEvent event) {
        addon.getPlayerDataManager().unload(event.getPlayer().getUniqueId());
        addon.getChartHolograms().clear(event.getPlayer().getUniqueId());
    }

    /**
     * Boarding a boat raises the hologram chart - the game shows new sailors
     * where to go the moment they set sail, no telling needed. (Fades on its
     * own; config chart.show-on-boarding.)
     */
    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onBoardBoat(VehicleEnterEvent event) {
        if (addon.getSettings().isChartOnBoarding()
                && event.getVehicle() instanceof org.bukkit.entity.Boat
                && event.getEntered() instanceof Player player
                && player.getWorld().equals(addon.getOverWorld())) {
            addon.getChartHolograms().show(player);
        }
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onMove(PlayerMoveEvent event) {
        // Only check when crossing a chunk boundary
        if (event.getFrom().getBlockX() >> 4 == event.getTo().getBlockX() >> 4
                && event.getFrom().getBlockZ() >> 4 == event.getTo().getBlockZ() >> 4) {
            return;
        }
        Player player = event.getPlayer();
        if (!player.getWorld().equals(addon.getOverWorld())) {
            return;
        }
        int x = event.getTo().getBlockX();
        int z = event.getTo().getBlockZ();
        int range = addon.getSettings().getIslandDistance();
        TWPlayerData data = addon.getPlayerDataManager().get(player.getUniqueId());
        addon.getGalaxyEngine(player.getWorld().getSeed()).islandsNear(x, z, range).stream()
                .filter(spec -> spec.distanceSquared(x, z) <= (long) range * range)
                .filter(data::chart)
                .forEach(spec -> {
                    User user = User.getInstance(player);
                    player.sendActionBar(Component.text(
                            user.getTranslation("tradewinds.chart.charted", "[name]", spec.name())));
                    addon.getPlayerDataManager().save(player.getUniqueId());
                    Bukkit.getPluginManager().callEvent(new IslandChartedEvent(player.getUniqueId(), spec));
                });
    }
}
