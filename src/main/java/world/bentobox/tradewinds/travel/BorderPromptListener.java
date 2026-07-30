package world.bentobox.tradewinds.travel;

import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

import org.bukkit.entity.Boat;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerMoveEvent;
import org.bukkit.event.vehicle.VehicleMoveEvent;

import world.bentobox.tradewinds.TradeWinds;
import world.bentobox.tradewinds.galaxy.GalaxyEngine;
import world.bentobox.tradewinds.galaxy.IslandSpec;

/**
 * The warp trigger: reaching an island's border in a boat offers the warp
 * dialog (proximity trigger - the packet-level "click the wall" idea was
 * rejected as needless complexity). Rowing on through does nothing special;
 * the prompt has a per-island cooldown so it never spams.
 *
 * @author tastybento
 */
public class BorderPromptListener implements Listener {

    private final TradeWinds addon;
    // player -> "cellX,cellZ@epochSeconds" of the last prompt
    private final Map<UUID, String> lastPrompt = new ConcurrentHashMap<>();

    public BorderPromptListener(TradeWinds addon) {
        this.addon = addon;
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onVehicleMove(VehicleMoveEvent event) {
        if (!(event.getVehicle() instanceof Boat boat) || boat.getPassengers().isEmpty()
                || !(boat.getPassengers().get(0) instanceof Player player)) {
            return;
        }
        // Gate to block crossings
        if (event.getFrom().getBlockX() == event.getTo().getBlockX()
                && event.getFrom().getBlockZ() == event.getTo().getBlockZ()) {
            return;
        }
        check(player, event.getTo().getBlockX(), event.getTo().getBlockZ());
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onPlayerMove(PlayerMoveEvent event) {
        // Some boat movement arrives as PlayerMoveEvent while seated
        if (event.getPlayer().getVehicle() instanceof Boat
                && (event.getFrom().getBlockX() != event.getTo().getBlockX()
                        || event.getFrom().getBlockZ() != event.getTo().getBlockZ())) {
            check(event.getPlayer(), event.getTo().getBlockX(), event.getTo().getBlockZ());
        }
    }

    private void check(Player player, int x, int z) {
        if (!player.getWorld().equals(addon.getOverWorld())) {
            return;
        }
        Optional<IslandSpec> origin = originIslandNearBorder(x, z);
        if (origin.isEmpty()) {
            return;
        }
        IslandSpec spec = origin.get();
        String key = spec.cellX() + "," + spec.cellZ();
        long now = System.currentTimeMillis() / 1000;
        String last = lastPrompt.get(player.getUniqueId());
        if (last != null) {
            String[] parts = last.split("@");
            if (parts[0].equals(key)
                    && now - Long.parseLong(parts[1]) < addon.getSettings().getWarpPromptCooldownSeconds()) {
                return;
            }
        }
        lastPrompt.put(player.getUniqueId(), key + "@" + now);
        addon.getWarpService().openDialog(player, spec);
    }

    /**
     * The island whose VISIBLE border the position is at, if any. The visible
     * border is the protection range (where BentoBox announces "Now leaving
     * ..." and the Border addon draws the wall) - the warp offer fires right
     * at that moment, not out at the far edge of island space. The ring spans
     * trigger-distance either side of the line so an outbound crossing cannot
     * skip it.
     */
    Optional<IslandSpec> originIslandNearBorder(int x, int z) {
        GalaxyEngine engine = addon.getGalaxyEngine(addon.getOverWorld().getSeed());
        int border = addon.getSettings().getIslandProtectionRange();
        int trigger = addon.getSettings().getWarpTriggerDistance();
        long outerSq = (long) (border + trigger) * (border + trigger);
        long innerSq = (long) (border - trigger) * (border - trigger);
        return engine.islandsNear(x, z, border + trigger).stream()
                .filter(spec -> {
                    long d2 = spec.distanceSquared(x, z);
                    return d2 <= outerSq && d2 >= innerSq;
                })
                .findFirst();
    }
}
