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
import world.bentobox.tradewinds.PortNames;
import world.bentobox.tradewinds.TradeWinds;
import world.bentobox.tradewinds.ocean.IslandSpec;
import world.bentobox.tradewinds.ocean.SecurityBand;

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

    /** Variable key for island name in locale messages. */
    private static final String NAME_VAR = "[name]";

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
        return new HashSet<>(addon.getSettings().getContrabandMaterials());
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
     * How much contraband a player is carrying, anywhere on them.
     * <p>
     * Hold <em>and</em> pockets. Scanning only the hold looked principled -
     * cargo means the hold everywhere else in this game - but it handed
     * smugglers a free pass: tip the sugar into your pockets before the border,
     * cross, and stow it again on the far side. A customs officer searches the
     * sailor, not just the cargo manifest.
     *
     * @param player the player
     * @return total contraband items on them
     */
    public int contrabandAboard(Player player) {
        int total = 0;
        for (String name : contrabandNames()) {
            Material material = Material.matchMaterial(name);
            if (material == null) {
                continue;
            }
            total += addon.getHoldService().count(player, material);
            total += loose(player, material);
        }
        return total;
    }

    /**
     * Contraband in the player's own inventory slots, ignoring the hold - the
     * hold is counted separately, and a pouch sitting in a slot must not be
     * counted twice.
     */
    private int loose(Player player, Material material) {
        int total = 0;
        for (ItemStack stack : player.getInventory().getContents()) {
            if (stack != null && stack.getType() == material) {
                total += stack.getAmount();
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
     * <p>
     * A title alone was not enough in play - it flashes and is gone, and a
     * player who blinks has no idea what happened. The chat line stays.
     */
    private void detect(Player player, IslandSpec island, int aboard, boolean flagged) {
        User user = User.getInstance(player);
        List<Entity> units = police.dispatch(player, island);
        if (units.isEmpty()) {
            // Nowhere to launch a boat from: the smuggler is ashore in the
            // market itself, so there is no chase to have - customs just take
            // the cargo off them
            seize(player, island);
            return;
        }
        String port = PortNames.display(addon, user, island);
        user.sendMessage(flagged ? "tradewinds.customs.flagged" : "tradewinds.customs.detected",
                NAME_VAR, port, TextVariables.NUMBER, String.valueOf(aboard));
        user.sendMessage("tradewinds.customs.detected-chat", NAME_VAR, port, TextVariables.NUMBER,
                String.valueOf(aboard));
        chases.put(player.getUniqueId(), new Chase(island, System.currentTimeMillis(), units));
        addon.log("Customs at " + island.name() + " detected " + aboard + " contraband on " + player.getName()
                + " - patrol of " + units.size() + " dispatched");
    }

    /**
     * Confiscation with no chase: the player is standing in the market, and
     * there is nowhere for a patrol boat to come from.
     */
    private void seize(Player player, IslandSpec island) {
        int seized = confiscate(player);
        double fine = charge(player, seized);
        User user = User.getInstance(player);
        user.sendMessage("tradewinds.customs.seized", TextVariables.NUMBER,
                String.valueOf(seized), "[amount]", format(fine), NAME_VAR, PortNames.display(addon, user, island));
        addon.getReputationService().recordCrime(player, Crime.SMUGGLING);
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
                // Timed out, not outrun. Reporting this as an escape told a
                // player standing beside the dock that they had reached open
                // water, and flagged them at the port for running - when they
                // had done nothing of the sort.
                calledOff(player, chase);
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
        int seized = confiscate(player);
        double fine = charge(player, seized);
        User user = User.getInstance(player);
        user.sendMessage("tradewinds.customs.caught", TextVariables.NUMBER, String.valueOf(seized), "[amount]",
                format(fine), NAME_VAR, PortNames.display(addon, user, chase.island()));
        addon.getReputationService().recordCrime(player, Crime.SMUGGLING);
    }

    /**
     * Take every scrap of contraband out of the hold.
     *
     * @param player the smuggler
     * @return how much was seized
     */
    private int confiscate(Player player) {
        int seized = 0;
        for (String name : contrabandNames()) {
            Material material = Material.matchMaterial(name);
            if (material == null) {
                continue;
            }
            seized += addon.getHoldService().remove(player, material, Integer.MAX_VALUE);
            // Pockets too, or the search that found it could not take it
            for (ItemStack stack : player.getInventory().getContents()) {
                if (stack != null && stack.getType() == material) {
                    seized += stack.getAmount();
                    stack.setAmount(0);
                }
            }
        }
        return seized;
    }

    /**
     * Charge the smuggling fine, taking what they have if they cannot cover it
     * - a debt would only be one more thing to run from.
     *
     * @param player the smuggler
     * @param seized how much contraband was taken
     * @return what was actually paid
     */
    private double charge(Player player, int seized) {
        double fine = seized * addon.getSettings().getSmugglingFinePerItem();
        VaultHook vault = addon.getPlugin().getVault().orElse(null);
        if (vault == null || fine <= 0) {
            return 0;
        }
        User user = User.getInstance(player);
        double payable = Math.min(fine, vault.getBalance(user));
        vault.withdraw(user, payable);
        return payable;
    }

    private String format(double amount) {
        return world.bentobox.tradewinds.economy.Money.format(addon, amount);
    }

    /**
     * The patrol gave up without ever catching them - the chase simply ran out
     * of time. No flee flag: nobody ran, so the port has nothing to remember.
     */
    private void calledOff(Player player, Chase chase) {
        User user = User.getInstance(player);
        user.sendMessage("tradewinds.customs.called-off", NAME_VAR, PortNames.display(addon, user, chase.island()));
    }

    /**
     * Ran for it and made it. The port remembers: come back soon and there is
     * no scan roll, the patrol simply launches.
     */
    private void escaped(Player player, Chase chase) {
        String key = key(player, chase.island());
        fleeFlags.put(key, System.currentTimeMillis() + addon.getSettings().getFleeFlagMinutes() * 60_000L);
        User user = User.getInstance(player);
        user.sendMessage("tradewinds.customs.escaped", NAME_VAR, PortNames.display(addon, user, chase.island()));
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
        updatePosition(player, true);
    }

    /**
     * Track which island's space a player is in.
     *
     * @param player the player
     * @param scan false to record the position without triggering a scan - used
     *        on login, so a player who logged out inside a port is not searched
     *        the moment they take a step
     */
    public void updatePosition(Player player, boolean scan) {
        if (!addon.inWorld(player.getWorld()) || addon.getOverWorld() == null
                || !player.getWorld().equals(addon.getOverWorld())) {
            insideIsland.remove(player.getUniqueId());
            return;
        }
        Optional<IslandSpec> here = islandSpaceAt(player);
        String was = insideIsland.get(player.getUniqueId());
        IslandSpec hereSpec = here.orElse(null);
        String now = hereSpec == null ? null : key(hereSpec);
        if (java.util.Objects.equals(now, was)) {
            return;
        }
        // Leaving an island's space ends any chase it had running BEFORE the
        // new island is considered. Warping out mid-chase used to leave the
        // chase live, which then blocked the destination's scan entirely - the
        // smuggler simply stopped being inspected anywhere.
        if (was != null) {
            leftIslandSpace(player, was);
        }
        if (now == null) {
            insideIsland.remove(player.getUniqueId());
            return;
        }
        insideIsland.put(player.getUniqueId(), now);
        if (scan) {
            onEntry(player, hereSpec);
        }
    }

    /**
     * A player has left an island's space: if that island was chasing them,
     * they got away.
     */
    private void leftIslandSpace(Player player, String islandKey) {
        Chase chase = chases.get(player.getUniqueId());
        if (chase != null && key(chase.island()).equals(islandKey)) {
            escaped(player, chase);
            end(player.getUniqueId(), chase);
        }
    }

    /**
     * The island whose protection range covers a player, if any - "island
     * space" as the rest of the game means it.
     */
    private Optional<IslandSpec> islandSpaceAt(Player player) {
        int x = player.getLocation().getBlockX();
        int z = player.getLocation().getBlockZ();
        int range = addon.getSettings().getIslandProtectionRange();
        return addon.getOceanEngine(addon.getOverWorld().getSeed()).islandsNear(x, z, range).stream()
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
     * Whether an item may be sold at this island. Since customs stamping went
     * (hold plan, 2026-08-01) the contraband band rule is the ONLY thing that
     * refuses a sale: anything else with a price, a port will take.
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
