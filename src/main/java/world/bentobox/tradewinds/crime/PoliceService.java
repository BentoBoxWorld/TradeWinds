package world.bentobox.tradewinds.crime;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

import org.bukkit.Bukkit;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Mob;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityCombustEvent;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.entity.EntityTargetEvent;
import org.bukkit.event.player.PlayerChangedWorldEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.scheduler.BukkitTask;

import world.bentobox.bentobox.api.user.User;
import world.bentobox.tradewinds.TradeWinds;
import world.bentobox.tradewinds.galaxy.IslandSpec;

/**
 * The standing response to a wanted player: while you are inside a policed
 * island's waters and the law wants you, the law comes.
 * <p>
 * Three things keep this from becoming either a nuisance or a farm. Police
 * <b>break off at the border</b> - the bands only mean something if lawless
 * water is genuinely where the law is not, so an escort that followed you
 * across the ocean would flatten the whole map into one difficulty. Police
 * <b>never leak</b>: they are recalled when the pursuit ends, when the target
 * logs out, changes world or stops being wanted, and they are non-persistent
 * so an unloaded chunk takes them with it. And police <b>drop nothing</b>,
 * which {@link CustomsListener} enforces for every tagged unit.
 *
 * @author tastybento
 */
public class PoliceService implements Listener {

    /** How often the response is re-examined, in ticks. */
    private static final long PERIOD = 40L;

    /**
     * One standing response in progress.
     *
     * @param island the island whose law is responding
     * @param units the units fielded
     */
    private record Response(IslandSpec island, List<Entity> units) {
    }

    private final TradeWinds addon;
    private final Map<UUID, Response> responses = new ConcurrentHashMap<>();
    private BukkitTask task;

    public PoliceService(TradeWinds addon) {
        this.addon = addon;
    }

    public void start() {
        task = Bukkit.getScheduler().runTaskTimer(addon.getPlugin(), this::tick, PERIOD, PERIOD);
    }

    public void stop() {
        if (task != null) {
            task.cancel();
        }
        responses.values().forEach(response -> dispatch().recall(response.units()));
        responses.clear();
    }

    private PoliceDispatch dispatch() {
        return addon.getCustomsService().getPolice();
    }

    /**
     * Re-examine every online player: dispatch, re-target, or break off.
     */
    void tick() {
        if (!addon.getSettings().isCrimeEnabled() || addon.getOverWorld() == null) {
            return;
        }
        for (Player player : addon.getOverWorld().getPlayers()) {
            if (player.getGameMode() != org.bukkit.GameMode.SURVIVAL || player.isDead()) {
                recall(player.getUniqueId());
                continue;
            }
            Standing standing = addon.getReputationService().standing(player.getUniqueId());
            if (!standing.isHunted()) {
                // Paid your fine, or decayed back to merely disreputable: the
                // patrol stands down the moment you are no longer wanted
                recall(player.getUniqueId());
                continue;
            }
            update(player, standing);
        }
    }

    private void update(Player player, Standing standing) {
        Optional<IslandSpec> here = islandSpaceAt(player);
        Response active = responses.get(player.getUniqueId());
        if (here.isEmpty()) {
            recall(player.getUniqueId());
            return;
        }
        IslandSpec island = here.get();
        double distance = Math.sqrt(island.distanceSquared(player.getLocation().getBlockX(),
                player.getLocation().getBlockZ()));
        if (PoliceRoster.shouldBreakOff(distance, addon.getSettings().getIslandProtectionRange(),
                addon.getSettings().getChaseBreakOffDistance())) {
            recall(player.getUniqueId());
            return;
        }
        if (active == null) {
            List<Entity> units = dispatch().dispatchWanted(player, island, isAshore(player),
                    standing == Standing.FUGITIVE);
            if (units.isEmpty()) {
                return; // This band has nobody to send - lawless water
            }
            responses.put(player.getUniqueId(), new Response(island, units));
            User.getInstance(player).sendMessage("tradewinds.police.responding", "[name]", island.name());
            return;
        }
        // Mobs forget, and a target seated in a boat is forgotten fast: keep
        // re-asserting, and drop units that have died
        active.units().removeIf(unit -> !unit.isValid());
        if (active.units().isEmpty()) {
            responses.remove(player.getUniqueId());
            return;
        }
        active.units().stream().filter(Mob.class::isInstance).map(Mob.class::cast)
                .forEach(mob -> mob.setTarget(player));
    }

    /**
     * Whether a player is standing on land rather than in or over water.
     */
    private boolean isAshore(Player player) {
        return player.getLocation().getBlockY() > addon.getSettings().getSeaHeight()
                && player.getLocation().getBlock().getType() == org.bukkit.Material.AIR
                && player.getVehicle() == null;
    }

    private Optional<IslandSpec> islandSpaceAt(Player player) {
        int x = player.getLocation().getBlockX();
        int z = player.getLocation().getBlockZ();
        int range = addon.getSettings().getIslandProtectionRange();
        return addon.getGalaxyEngine(addon.getOverWorld().getSeed()).islandsNear(x, z, range).stream()
                .filter(s -> s.distanceSquared(x, z) <= (long) range * range).findFirst();
    }

    /**
     * Call off the pursuit of a player and remove its units.
     *
     * @param playerId the player
     */
    public void recall(UUID playerId) {
        Response response = responses.remove(playerId);
        if (response != null) {
            dispatch().recall(response.units());
            Player player = Bukkit.getPlayer(playerId);
            if (player != null && player.isOnline()) {
                User.getInstance(player).sendMessage("tradewinds.police.broken-off");
            }
        }
    }

    /**
     * Whether the law is currently after a player.
     *
     * @param playerId the player
     * @return true during a police response
     */
    public boolean isPursued(UUID playerId) {
        return responses.containsKey(playerId);
    }

    /**
     * A wanted player may be attacked anywhere, whatever the island's PvP
     * setting says (spec section 7). BentoBox's own PvP listener cancels the
     * damage at LOW priority, so this un-cancels it afterwards - bounty
     * hunting has to be possible in the safe bands, or a wanted player could
     * simply moor in high security and be untouchable.
     */
    @EventHandler(priority = EventPriority.NORMAL)
    public void onPvpDamage(EntityDamageByEntityEvent event) {
        if (!addon.getSettings().isCrimeEnabled() || !event.isCancelled()) {
            return;
        }
        if (!(event.getEntity() instanceof Player victim) || !addon.inWorld(victim.getWorld())) {
            return;
        }
        Player attacker = CrimeListener.attacker(event);
        if (attacker == null || attacker.equals(victim)) {
            return;
        }
        if (addon.getReputationService().standing(victim.getUniqueId()).isLawfulTarget()) {
            event.setCancelled(false);
        }
    }

    /**
     * Police phantoms do not burn at dawn. A pursuit that ends because the sun
     * came up is not a pursuit.
     */
    @EventHandler(priority = EventPriority.HIGHEST)
    public void onCombust(EntityCombustEvent event) {
        if (addon.getCustomsService() != null && dispatch().isPolice(event.getEntity())) {
            event.setCancelled(true);
        }
    }

    /**
     * Police keep their assigned target and are not distracted by whoever
     * wanders past.
     */
    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onTarget(EntityTargetEvent event) {
        if (addon.getCustomsService() == null || !dispatch().isPolice(event.getEntity())) {
            return;
        }
        if (!(event.getTarget() instanceof Player player)
                || !addon.getReputationService().standing(player.getUniqueId()).isHunted()) {
            // Only cancel a re-target onto someone innocent; the service
            // re-asserts the real target on its next tick
            event.setCancelled(!isChasedByLaw(event.getTarget()));
        }
    }

    private boolean isChasedByLaw(Entity target) {
        return target instanceof Player player
                && (responses.containsKey(player.getUniqueId())
                        || addon.getCustomsService().isChased(player.getUniqueId()));
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent event) {
        recall(event.getPlayer().getUniqueId());
    }

    @EventHandler
    public void onWorldChange(PlayerChangedWorldEvent event) {
        recall(event.getPlayer().getUniqueId());
    }
}
