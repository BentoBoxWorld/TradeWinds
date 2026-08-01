package world.bentobox.tradewinds.crime;

import org.bukkit.entity.Boat;
import org.bukkit.entity.Player;
import org.bukkit.entity.Projectile;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.entity.EntityDeathEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerMoveEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.event.vehicle.VehicleMoveEvent;

import world.bentobox.tradewinds.TradeWinds;
import world.bentobox.tradewinds.api.events.TWWarpCompletedEvent;

/**
 * Drives customs: notices players entering island space (rowed or warped),
 * turns a police hit into an arrest, and enforces the rule that police never
 * drop anything.
 *
 * @author tastybento
 */
public class CustomsListener implements Listener {

    private final TradeWinds addon;

    public CustomsListener(TradeWinds addon) {
        this.addon = addon;
    }

    private CustomsService customs() {
        return addon.getCustomsService();
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onPlayerMove(PlayerMoveEvent event) {
        if (event.getFrom().getBlockX() == event.getTo().getBlockX()
                && event.getFrom().getBlockZ() == event.getTo().getBlockZ()) {
            return;
        }
        customs().updatePosition(event.getPlayer());
    }

    /**
     * A seated passenger's own movement events are unreliable, so the boat's
     * are watched too - a smuggler is nearly always in a boat.
     */
    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onVehicleMove(VehicleMoveEvent event) {
        if (!(event.getVehicle() instanceof Boat boat) || boat.getPassengers().isEmpty()) {
            return;
        }
        if (event.getFrom().getBlockX() == event.getTo().getBlockX()
                && event.getFrom().getBlockZ() == event.getTo().getBlockZ()) {
            return;
        }
        boat.getPassengers().stream().filter(Player.class::isInstance).map(Player.class::cast)
                .forEach(customs()::updatePosition);
    }

    /**
     * Warping in is an entry like any other: the scan does not care how you
     * arrived (spec section 6).
     */
    @EventHandler(priority = EventPriority.MONITOR)
    public void onWarpArrival(TWWarpCompletedEvent event) {
        customs().updatePosition(event.getPlayer());
    }

    /**
     * A patrol landing a hit ends the chase then and there.
     */
    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onDamage(EntityDamageByEntityEvent event) {
        if (!(event.getEntity() instanceof Player player)) {
            return;
        }
        if (isPoliceSource(event) && customs().isChased(player.getUniqueId())) {
            customs().onPoliceHit(player);
        }
    }

    private boolean isPoliceSource(EntityDamageByEntityEvent event) {
        if (customs().getPolice().isPolice(event.getDamager())) {
            return true;
        }
        return event.getDamager() instanceof Projectile projectile
                && projectile.getShooter() instanceof org.bukkit.entity.Entity shooter
                && customs().getPolice().isPolice(shooter);
    }

    /**
     * Police drop nothing at all - no items, no experience. A criminal record
     * must never become a farm (spec section 7).
     */
    @EventHandler(priority = EventPriority.HIGHEST)
    public void onPoliceDeath(EntityDeathEvent event) {
        if (customs().getPolice().isPolice(event.getEntity())) {
            event.getDrops().clear();
            event.setDroppedExp(0);
        }
    }

    /**
     * Record where a joining player is without searching them. A player who
     * logged out inside a port would otherwise be scanned the instant they
     * took a step, which is not an "entry" by any reading.
     */
    @EventHandler(priority = EventPriority.MONITOR)
    public void onJoin(PlayerJoinEvent event) {
        customs().updatePosition(event.getPlayer(), false);
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent event) {
        customs().forget(event.getPlayer().getUniqueId());
        addon.getCrimeListener().forget(event.getPlayer().getUniqueId());
    }
}
