package world.bentobox.tradewinds.crime;

import org.bukkit.entity.IronGolem;
import org.bukkit.entity.Player;
import org.bukkit.entity.Projectile;
import org.bukkit.entity.Villager;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.entity.EntityDeathEvent;
import org.bukkit.event.entity.PlayerDeathEvent;

import world.bentobox.bentobox.api.user.User;
import world.bentobox.bentobox.hooks.VaultHook;
import world.bentobox.tradewinds.TradeWinds;

/**
 * Watches for crimes and reports them to the {@link ReputationService}.
 * <p>
 * The anti-bait guard matters here (spec section 7): the innocent-kill penalty
 * applies to direct kills only, and a victim who struck first forfeits it -
 * otherwise standing next to a wanted player and dying at them is a weapon.
 *
 * @author tastybento
 */
public class CrimeListener implements Listener {

    /** How long a victim's own aggression exempts their killer, in millis. */
    private static final long PROVOCATION_WINDOW = 30_000L;

    private final TradeWinds addon;
    /** Who hit whom last, for the anti-bait guard: "victim>attacker" to time. */
    private final java.util.Map<String, Long> provocations = new java.util.concurrent.ConcurrentHashMap<>();

    public CrimeListener(TradeWinds addon) {
        this.addon = addon;
    }

    /**
     * Striking a resident villager. The kill is handled on death, so this only
     * charges for non-fatal blows.
     */
    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onDamage(EntityDamageByEntityEvent event) {
        Player attacker = attacker(event);
        if (attacker == null || !addon.inWorld(attacker.getWorld())) {
            return;
        }
        if (event.getEntity() instanceof Player victim) {
            // Remember that the victim's attacker was provoked, for the guard
            provocations.put(key(attacker, victim), System.currentTimeMillis());
            return;
        }
        if (event.getEntity() instanceof Villager villager && villager.getHealth() > event.getFinalDamage()) {
            addon.getReputationService().recordCrime(attacker, Crime.HURT_VILLAGER);
        }
    }

    /**
     * Killing a resident villager, or a police unit.
     */
    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onEntityDeath(EntityDeathEvent event) {
        Player killer = event.getEntity().getKiller();
        if (killer == null || !addon.inWorld(killer.getWorld())) {
            return;
        }
        if (event.getEntity() instanceof Villager) {
            addon.getReputationService().recordCrime(killer, Crime.KILL_VILLAGER);
        } else if (event.getEntity() instanceof IronGolem || isPolice(event)) {
            addon.getReputationService().recordCrime(killer, Crime.KILL_POLICE);
        }
    }

    /**
     * Killing a player: lawful if they were a wanted target, a serious crime if
     * they were not. Bounty payout lands in Stage 6c; the ledger is cleared
     * here so it can only ever pay once.
     */
    @EventHandler(priority = EventPriority.MONITOR)
    public void onPlayerDeath(PlayerDeathEvent event) {
        Player victim = event.getEntity();
        Player killer = victim.getKiller();
        if (killer == null || killer.equals(victim) || !addon.inWorld(victim.getWorld())) {
            return;
        }
        Standing victimStanding = addon.getReputationService().standing(victim.getUniqueId());
        if (victimStanding.isLawfulTarget()) {
            // Bounty hunting is lawful work: no penalty, and the bounty is paid
            double bounty = addon.getReputationService().claimBounty(victim.getUniqueId());
            VaultHook vault = addon.getPlugin().getVault().orElse(null);
            if (bounty > 0 && vault != null) {
                vault.deposit(User.getInstance(killer), bounty);
                User.getInstance(killer).sendMessage("tradewinds.crime.bounty-paid",
                        "[name]", victim.getName(),
                        "[amount]", vault.format(bounty));
            }
            return;
        }
        if (wasProvokedBy(victim, killer)) {
            // The victim struck first - this was a fight, not a murder
            return;
        }
        addon.getReputationService().recordCrime(killer, Crime.KILL_INNOCENT);
    }

    /**
     * Whether the victim had recently attacked their killer, which forfeits the
     * innocent-kill protection.
     */
    private boolean wasProvokedBy(Player victim, Player killer) {
        Long when = provocations.get(key(victim, killer));
        return when != null && System.currentTimeMillis() - when < PROVOCATION_WINDOW;
    }

    private static String key(Player attacker, Player victim) {
        return attacker.getUniqueId() + ">" + victim.getUniqueId();
    }

    /**
     * The player behind a damage event, whether they swung or shot.
     */
    static Player attacker(EntityDamageByEntityEvent event) {
        if (event.getDamager() instanceof Player player) {
            return player;
        }
        if (event.getDamager() instanceof Projectile projectile
                && projectile.getShooter() instanceof Player shooter) {
            return shooter;
        }
        return null;
    }

    /**
     * Whether a dead entity was a police unit. Police are PDC-tagged when they
     * are dispatched (Stage 6c); until then only golems count.
     */
    private boolean isPolice(EntityDeathEvent event) {
        return event.getEntity().getPersistentDataContainer()
                .has(addon.getPoliceKey(), org.bukkit.persistence.PersistentDataType.BYTE);
    }

    /**
     * Drop remembered provocations for a player who logged out.
     *
     * @param playerId the player
     */
    public void forget(java.util.UUID playerId) {
        provocations.keySet().removeIf(k -> k.contains(playerId.toString()));
    }
}
