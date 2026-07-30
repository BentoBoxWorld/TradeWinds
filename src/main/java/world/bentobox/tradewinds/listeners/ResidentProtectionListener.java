package world.bentobox.tradewinds.listeners;

import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;
import org.bukkit.entity.Projectile;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.event.entity.EntityTargetLivingEntityEvent;
import org.bukkit.persistence.PersistentDataType;

import world.bentobox.tradewinds.generator.IslandDecorator;

/**
 * Keeps island residents (traders, golems) alive without making markets
 * timeless fortresses: hostile mobs never target or hurt them, the environment
 * cannot kill them - but PLAYERS can, where the island's HURT_VILLAGERS flag
 * allows. Player violence against traders is a crime, and crime is handled by
 * the reputation system (Stage 6), not by invincibility.
 *
 * @author tastybento
 */
public class ResidentProtectionListener implements Listener {

    private boolean isResident(Entity entity) {
        return entity.getPersistentDataContainer().has(IslandDecorator.RESIDENT_KEY, PersistentDataType.STRING);
    }

    /**
     * Mobs never even look at residents - a zombie in an anarchic market
     * wanders straight past the traders.
     */
    @EventHandler(priority = EventPriority.LOWEST, ignoreCancelled = true)
    public void onTarget(EntityTargetLivingEntityEvent event) {
        if (event.getTarget() != null && isResident(event.getTarget()) && !(event.getEntity() instanceof Player)) {
            event.setCancelled(true);
        }
    }

    /**
     * Residents only take damage from players (directly or via projectiles).
     */
    @EventHandler(priority = EventPriority.LOWEST, ignoreCancelled = true)
    public void onDamage(EntityDamageEvent event) {
        if (!isResident(event.getEntity())) {
            return;
        }
        if (event instanceof EntityDamageByEntityEvent byEntity && isPlayerCaused(byEntity.getDamager())) {
            return; // a player's problem is the reputation system's problem
        }
        event.setCancelled(true);
    }

    private boolean isPlayerCaused(Entity damager) {
        return damager instanceof Player
                || (damager instanceof Projectile projectile && projectile.getShooter() instanceof Player);
    }
}
