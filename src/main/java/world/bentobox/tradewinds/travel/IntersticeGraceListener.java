package world.bentobox.tradewinds.travel;

import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.event.entity.EntityTargetLivingEntityEvent;

import world.bentobox.tradewinds.TradeWinds;

/**
 * A moment to get your bearings after a failed warp.
 * <p>
 * Playtest: a player's first ever warp failed, ghasts opened fire immediately,
 * and the dialog offering the free way out went unread because reading it meant
 * dying. The interstice is meant to be a detour with a way out, not a death
 * sentence handed to someone who did nothing wrong - so for a few seconds after
 * arrival nothing in there may target or hurt them.
 * <p>
 * The grace covers the arrival only. Stay and pick a fight and it is a fight.
 *
 * @author tastybento
 */
public class IntersticeGraceListener implements Listener {

    private final TradeWinds addon;

    public IntersticeGraceListener(TradeWinds addon) {
        this.addon = addon;
    }

    private boolean isProtected(Player player) {
        return addon.getNetherWorld() != null && player.getWorld().equals(addon.getNetherWorld())
                && addon.getIntersticeService().isInGrace(player.getUniqueId());
    }

    /**
     * Nothing in the interstice notices a new arrival for the grace window.
     */
    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onTarget(EntityTargetLivingEntityEvent event) {
        if (event.getTarget() instanceof Player player && isProtected(player)) {
            event.setTarget(null);
            event.setCancelled(true);
        }
    }

    /**
     * ... and a fireball already in the air cannot land on them either. Clicking
     * "re-engage the warp" and dying to a shot fired before you read it is
     * exactly the experience this is here to prevent.
     */
    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onDamage(EntityDamageEvent event) {
        if (event.getEntity() instanceof Player player && isProtected(player)
                && event.getCause() != EntityDamageEvent.DamageCause.VOID) {
            event.setCancelled(true);
        }
    }
}
