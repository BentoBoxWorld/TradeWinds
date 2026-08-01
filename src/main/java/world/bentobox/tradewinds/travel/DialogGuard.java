package world.bentobox.tradewinds.travel;

import java.util.List;

import org.bukkit.entity.Enemy;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDamageEvent;

import world.bentobox.bentobox.api.user.User;
import world.bentobox.tradewinds.TradeWinds;

/**
 * Shuts a dialog when the world starts hitting you.
 * <p>
 * A menu is a modal screen: a sailor reading the warp list while a patrol
 * closes in cannot see the boat, the water, or the thing shooting at them.
 * Being attacked closes it, on the same principle as a bed refusing to let you
 * sleep with monsters about - and warping is gated the same way, so a warp
 * cannot be a panic button out of a fight or a customs chase.
 * <p>
 * The interstice re-engage is deliberately exempt: it is the way <em>out</em>
 * of a place that is supposed to be dangerous, and gating it behind "no enemies
 * nearby" could strand a player for good. It goes through
 * {@code WarpService#deliver} rather than {@code warp}, so it never meets this
 * check.
 *
 * @author tastybento
 */
public class DialogGuard implements Listener {

    private final TradeWinds addon;

    public DialogGuard(TradeWinds addon) {
        this.addon = addon;
    }

    /**
     * Whether hostile mobs are close enough to stop a warp.
     *
     * @param player the player
     * @return true if something is hunting them
     */
    public boolean enemiesNear(Player player) {
        double radius = addon.getSettings().getWarpEnemyRadius();
        if (radius <= 0) {
            return false;
        }
        return !nearbyEnemies(player, radius).isEmpty();
    }

    /**
     * The hostile mobs within a radius of a player.
     *
     * @param player the player
     * @param radius search radius in blocks
     * @return the enemies found
     */
    public List<Entity> nearbyEnemies(Player player, double radius) {
        return player.getNearbyEntities(radius, radius, radius).stream()
                .filter(Enemy.class::isInstance)
                .filter(entity -> !entity.isDead())
                .toList();
    }

    /**
     * Tell a player why the warp will not engage.
     *
     * @param player the player
     */
    public void refuse(Player player) {
        User.getInstance(player).sendMessage("tradewinds.warp.enemies-near");
    }

    /**
     * Any damage closes any open dialog. Deliberately all damage, not just
     * mobs: drowning while reading a shop menu should also put the menu away.
     */
    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onDamage(EntityDamageEvent event) {
        if (event.getEntity() instanceof Player player && addon.inWorld(player.getWorld())) {
            player.closeDialog();
        }
    }
}
