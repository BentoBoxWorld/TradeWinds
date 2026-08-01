package world.bentobox.tradewinds.crime;

import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.scheduler.BukkitTask;

import world.bentobox.bentobox.api.localization.TextVariables;
import world.bentobox.bentobox.api.user.User;
import world.bentobox.bentobox.hooks.VaultHook;
import world.bentobox.tradewinds.TradeWinds;
import world.bentobox.tradewinds.galaxy.IslandSpec;
import world.bentobox.tradewinds.galaxy.SecurityBand;

/**
 * Customs: the scan on entering island space, and the chase that follows a
 * detection.
 * <p>
 * Detection is deliberately <b>not</b> a fine (spec section 6). Being caught
 * at the border with cargo you should not have starts a chase, and the water
 * between you and the horizon is a decision: <b>run</b> for the border,
 * <b>fight</b> the patrol, or <b>jettison</b> the cargo - which clears you, at
 * the cost of the cargo, and leaves it floating for anyone to pick up. That
 * last one is where emergent piracy comes from.
 * <p>
 * Two counters stop the obvious abuses: a per-island scan cooldown so a
 * smuggler cannot bounce in and out re-rolling the dice, and a flee flag so
 * that running from a scan is remembered - come back to that port soon and
 * there is no roll at all, the patrol simply launches.
 *
 * @author tastybento
 */
public class CustomsService {

    /** How often the chase is re-examined, in ticks. */
    private static final long CHASE_PERIOD = 20L;

    /**
     * One chase in progress.
     *
     * @param island the island whose customs are chasing
     * @param started epoch millis
     * @param units the patrol dispatched
     */
    private record Chase(IslandSpec island, long started, List<Entity> units) {
    }

    private final TradeWinds addon;
    private final PoliceDispatch police;
    /** Which island's space each player is currently inside. */
    private final Map<UUID, String> insideIsland = new ConcurrentHashMap<>();
    private final Map<UUID, Chase> chases = new ConcurrentHashMap<>();
    /** player+island -> epoch millis of the last scan there. */
    private final Map<String, Long> scanCooldowns = new ConcurrentHashMap<>();
    /** player+island -> epoch millis until which the player is flagged there. */
    private final Map<String, Long> fleeFlags = new ConcurrentHashMap<>();
    private BukkitTask task;

    public CustomsService(TradeWinds addon) {
        this.addon = addon;
        this.police = new PoliceDispatch(addon);
    }

    public PoliceDispatch getPolice() {
        return police;
    }

    public void start() {
        task = Bukkit.getScheduler().runTaskTimer(addon.getPlugin(), this::tick, CHASE_PERIOD, CHASE_PERIOD);
    }

    public void stop() {
        if (task != null) {
            task.cancel();
        }
        chases.values().forEach(chase -> police.recall(chase.units()));
        chases.clear();
    }

    /**
     * Whether the crime layer is running at all.
     */
    private boolean enabled() {
        return addon.getSettings().isCrimeEnabled() && addon.getSettings().isIllegalTradeEnabled();
    }

    /**
     * The configured contraband materials.
     *
     * @return material names customs care about
     */
    public Set<String> contrabandNames() {
        return new HashSet<>(addon.getSettings().getUnstampedSellables());
    }

    /**
     * Whether a material is contraband.
     *
     * @param material the material
     * @return true if customs would confiscate it
     */
    public boolean isContraband(Material material) {
        return Contraband.isContraband(material.name(), contrabandNames(),
                addon.getSettings().isIllegalTradeEnabled());
    }

    /**
     * How much contraband a player is carrying in their hold. Loose pockets do
     * not count: like everything else in this game, cargo means the hold
     * (spec principle 1).
     *
     * @param player the player
     * @return total contraband items aboard
     */
    public int contrabandAboard(Player player) {
        int total = 0;
        for (String name : contrabandNames()) {
            Material material = Material.matchMaterial(name);
            if (material != null) {
                total += addon.getHoldService().count(player, material, stack -> true);
            }
        }
        return total;
    }

    /**
     * Called when a player enters an island's space, however they got there.
     *
     * @param player the arriving player
     * @param island the island
     */
    public void onEntry(Player player, IslandSpec island) {
        if (!enabled() || player.getGameMode() != org.bukkit.GameMode.SURVIVAL) {
            return;
        }
        if (chases.containsKey(player.getUniqueId())) {
            return; // Already being chased - one at a time
        }
        String key = key(player, island);
        boolean flagged = isFlagged(key);
        if (!flagged && !rollScan(player, island, key)) {
            return;
        }
        scanCooldowns.put(key, System.currentTimeMillis());
        int aboard = contrabandAboard(player);
        if (aboard <= 0) {
            if (!flagged && addon.getSettings().isAnnounceCleanScans()) {
                User.getInstance(player).sendMessage("tradewinds.customs.clean");
            }
            return;
        }
        detect(player, island, aboard, flagged);
    }

    /**
     * Roll the dice on a scan, honouring the per-island cooldown that stops a
     * smuggler bouncing across the border re-rolling until they get a pass.
     */
    private boolean rollScan(Player player, IslandSpec island, String key) {
        Long last = scanCooldowns.get(key);
        long cooldown = addon.getSettings().getScanCooldownMinutes() * 60_000L;
        if (last != null && System.currentTimeMillis() - last < cooldown) {
            return false;
        }
        double base = addon.getSettings().getScanChance().getOrDefault(island.band().name(), 0.0);
        double chance = Contraband.scanChance(base, addon.getReputationService().standing(player.getUniqueId()),
                addon.getSettings().getScanUpstandingFactor(), addon.getSettings().getScanOffenderFactor());
        return Math.random() < chance;
    }

    /**
     * Caught at the border: launch the patrol and start the clock.
     */
    private void detect(Player player, IslandSpec island, int aboard, boolean flagged) {
        User user = User.getInstance(player);
        user.sendMessage(flagged ? "tradewinds.customs.flagged" : "tradewinds.customs.detected",
                "[name]", island.name(), TextVariables.NUMBER, String.valueOf(aboard));
        List<Entity> units = police.dispatch(player, island);
        chases.put(player.getUniqueId(), new Chase(island, System.currentTimeMillis(), units));
    }

    /**
     * Progress every chase: cleared, escaped, caught, or timed out.
     */
    void tick() {
        chases.forEach((id, chase) -> {
            Player player = Bukkit.getPlayer(id);
            if (player == null || player.isDead() || !addon.inWorld(player.getWorld())) {
                end(id, chase);
                return;
            }
            // Jettisoned: the cargo is gone, and so is the reason to chase
            if (contrabandAboard(player) <= 0) {
                User.getInstance(player).sendMessage("tradewinds.customs.jettisoned");
                end(id, chase);
                return;
            }
            // Caught: a unit closed to arm's reach
            if (nearestUnitDistance(player, chase) <= addon.getSettings().getCaughtRadius()) {
                caught(player, chase);
                end(id, chase);
                return;
            }
            // Escaped: across the border and away
            double fromIsland = Math.sqrt(chase.island().distanceSquared(player.getLocation().getBlockX(),
                    player.getLocation().getBlockZ()));
            if (fromIsland > addon.getSettings().getIslandProtectionRange()
                    + addon.getSettings().getChaseBreakOffDistance()) {
                escaped(player, chase);
                end(id, chase);
                return;
            }
            if (System.currentTimeMillis() - chase.started() > addon.getSettings().getChaseSeconds() * 1000L) {
                escaped(player, chase);
                end(id, chase);
            }
        });
    }

    private double nearestUnitDistance(Player player, Chase chase) {
        return chase.units().stream().filter(Entity::isValid)
                .mapToDouble(unit -> unit.getLocation().distance(player.getLocation())).min()
                .orElse(Double.MAX_VALUE);
    }

    /**
     * Caught with the cargo: confiscation, a fine, and a reputation hit. The
     * cargo is destroyed rather than dropped - customs seized it.
     */
    void caught(Player player, Chase chase) {
        int seized = 0;
        for (String name : contrabandNames()) {
            Material material = Material.matchMaterial(name);
            if (material != null) {
                seized += addon.getHoldService().remove(player, material, Integer.MAX_VALUE, stack -> true);
            }
        }
        double fine = seized * addon.getSettings().getSmugglingFinePerItem();
        VaultHook vault = addon.getPlugin().getVault().orElse(null);
        User user = User.getInstance(player);
        if (vault != null && fine > 0) {
            // Take what they have if they cannot cover it - a debt would just
            // be another thing to run from
            double payable = Math.min(fine, vault.getBalance(user));
            vault.withdraw(user, payable);
            fine = payable;
        }
        user.sendMessage("tradewinds.customs.caught", TextVariables.NUMBER, String.valueOf(seized),
                "[amount]", vault == null ? "0" : vault.format(fine), "[name]", chase.island().name());
        addon.getReputationService().record(player, Crime.SMUGGLING);
    }

    /**
     * Ran for it and made it. The port remembers: come back soon and there is
     * no scan roll, the patrol simply launches.
     */
    private void escaped(Player player, Chase chase) {
        String key = key(player, chase.island());
        fleeFlags.put(key, System.currentTimeMillis() + addon.getSettings().getFleeFlagMinutes() * 60_000L);
        User.getInstance(player).sendMessage("tradewinds.customs.escaped", "[name]", chase.island().name());
    }

    private void end(UUID id, Chase chase) {
        chases.remove(id);
        police.recall(chase.units());
    }

    /**
     * Whether a player is currently flagged at an island.
     */
    private boolean isFlagged(String key) {
        Long until = fleeFlags.get(key);
        if (until == null) {
            return false;
        }
        if (System.currentTimeMillis() > until) {
            fleeFlags.remove(key);
            return false;
        }
        return true;
    }

    /**
     * Whether a player is being chased right now.
     *
     * @param playerId the player
     * @return true during a chase
     */
    public boolean isChased(UUID playerId) {
        return chases.containsKey(playerId);
    }

    /**
     * A police unit landed a hit: that counts as caught, wherever the chase had
     * got to.
     *
     * @param player the smuggler
     */
    public void onPoliceHit(Player player) {
        Chase chase = chases.get(player.getUniqueId());
        if (chase != null && contrabandAboard(player) > 0) {
            caught(player, chase);
            end(player.getUniqueId(), chase);
        }
    }

    /**
     * Track which island's space a player is in, and fire the scan on entry.
     * Called from movement and from warp arrival, so rowing in and warping in
     * are treated identically (spec section 6: a scan on <em>every</em> entry).
     *
     * @param player the player
     */
    public void updatePosition(Player player) {
        if (!addon.inWorld(player.getWorld()) || addon.getOverWorld() == null
                || !player.getWorld().equals(addon.getOverWorld())) {
            insideIsland.remove(player.getUniqueId());
            return;
        }
        Optional<IslandSpec> here = islandSpaceAt(player);
        String was = insideIsland.get(player.getUniqueId());
        if (here.isEmpty()) {
            insideIsland.remove(player.getUniqueId());
            return;
        }
        String now = key(here.get());
        if (now.equals(was)) {
            return;
        }
        insideIsland.put(player.getUniqueId(), now);
        onEntry(player, here.get());
    }

    /**
     * The island whose protection range covers a player, if any - "island
     * space" as the rest of the game means it.
     */
    private Optional<IslandSpec> islandSpaceAt(Player player) {
        int x = player.getLocation().getBlockX();
        int z = player.getLocation().getBlockZ();
        int range = addon.getSettings().getIslandProtectionRange();
        return addon.getGalaxyEngine(addon.getOverWorld().getSeed()).islandsNear(x, z, range).stream()
                .filter(s -> s.distanceSquared(x, z) <= (long) range * range).findFirst();
    }

    /**
     * Whether this port deals in contraband at all.
     *
     * @param band the island's band
     * @return true if it buys
     */
    public boolean buysContraband(SecurityBand band) {
        SecurityBand safest = addon.getSettings().safestContrabandBuyer();
        return Contraband.buysContraband(band, safest);
    }

    /**
     * Whether an item may be sold at this island - the contraband rule on top
     * of the customs stamp.
     *
     * @param spec the island
     * @param item the item
     * @return true if the port will take it
     */
    public boolean sellableAt(IslandSpec spec, ItemStack item) {
        if (item == null) {
            return false;
        }
        if (!isContraband(item.getType())) {
            return true;
        }
        return buysContraband(spec.band());
    }

    private static String key(IslandSpec spec) {
        return spec.cellX() + "," + spec.cellZ();
    }

    private static String key(Player player, IslandSpec spec) {
        return player.getUniqueId() + "@" + key(spec);
    }

    /**
     * Forget a player's transient customs state on logout.
     *
     * @param playerId the player
     */
    public void forget(UUID playerId) {
        insideIsland.remove(playerId);
        Chase chase = chases.remove(playerId);
        if (chase != null) {
            police.recall(chase.units());
        }
    }
}
