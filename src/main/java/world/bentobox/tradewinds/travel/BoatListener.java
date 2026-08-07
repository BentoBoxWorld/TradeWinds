package world.bentobox.tradewinds.travel;

import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.entity.Boat;
import org.bukkit.entity.Item;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityPickupItemEvent;
import org.bukkit.event.entity.ItemDespawnEvent;
import org.bukkit.event.player.PlayerDropItemEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.vehicle.VehicleDestroyEvent;
import org.bukkit.event.vehicle.VehicleEnterEvent;
import org.bukkit.event.vehicle.VehicleExitEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.scheduler.BukkitTask;

import world.bentobox.bentobox.api.user.User;
import world.bentobox.tradewinds.TradeWinds;
import world.bentobox.tradewinds.dataobjects.BoatHold;
import world.bentobox.tradewinds.galaxy.IslandSpec;

/**
 * A boat's life in the world (rules ruled 2026-08-02):
 * <ul>
 * <li><b>Capture by boarding.</b> Boarding a boat that is not yours makes it
 * yours - after a confirmation, always, whatever its size, because the boat
 * you leave behind becomes an unowned OLD BOAT with your cargo still in it.
 * Riding as a passenger never takes over.</li>
 * <li><b>Capture by pickup.</b> Same confirmation for a boat ITEM: pickups
 * are automatic, so the first attempt is cancelled and prompts instead
 * (re-prompts suppressed briefly so walking about does not spam).</li>
 * <li><b>Protection.</b> An OWNED boat left unattended inside an island's
 * protected space cannot be boarded or broken by anyone else - not even by
 * mobs - except on ANARCHIC islands. Unowned boats are never protected,
 * anywhere: that is what makes an abandoned hull fair game.</li>
 * <li><b>Death</b> leaves the boat floating where it is; it stays yours.</li>
 * <li><b>Logging out</b> outside protection risks the boat being taken; the
 * loss is reported at the next login.</li>
 * </ul>
 *
 * @author tastybento
 */
public class BoatListener implements Listener {

    /** Placeholder for boat material in locale messages. */
    private static final String MATERIAL_PLACEHOLDER = "[material]";
    /** How long a refused item pickup stays quiet before prompting again. */
    private static final long PROMPT_SUPPRESSION_MS = 15_000;
    /**
     * How long after a swap boat items are ignored entirely. Taking a boat
     * leaves the old hull at your feet, and walking over it asked at once
     * whether you would like to swap back (playtest 2026-08-02). The
     * per-hull suppression cannot help: the hull you just shed is a
     * different boat, seen for the first time.
     */
    private static final long SWAP_QUIET_MS = 5_000;
    /** How often ridden boats write their position back (chart accuracy). */
    private static final long TRACK_PERIOD_TICKS = 40L;

    private final TradeWinds addon;
    /** player -> boatId -> when we last prompted about it. */
    private final Map<UUID, Map<String, Long>> prompted = new HashMap<>();
    /** player -> when boat items become interesting again after a swap. */
    private final Map<UUID, Long> swapQuietUntil = new HashMap<>();
    private BukkitTask tracker;

    public BoatListener(TradeWinds addon) {
        this.addon = addon;
    }

    public void start() {
        tracker = Bukkit.getScheduler().runTaskTimer(addon.getPlugin(), () -> {
            trackRiddenBoats();
            if (++sweepCounter % SWEEPS_PER_MINUTE == 0) {
                sweepExpiredItems();
            }
        }, TRACK_PERIOD_TICKS, TRACK_PERIOD_TICKS);
    }

    private int sweepCounter;
    /** 40-tick tracker ticks per TTL sweep (about a minute). */
    private static final int SWEEPS_PER_MINUTE = 30;

    public void stop() {
        if (tracker != null) {
            tracker.cancel();
        }
    }

    /**
     * Keep the chart honest about where a boat under way actually is.
     */
    private void trackRiddenBoats() {
        for (World world : ourWorlds()) {
            for (Player player : world.getPlayers()) {
                if (player.getVehicle() instanceof Boat boat) {
                    String id = BoatService.boatId(boat);
                    addon.getHoldManager().boat(id)
                            .ifPresent(hold -> addon.getHoldManager().rememberPosition(hold, boat.getLocation()));
                }
            }
        }
    }

    private java.util.List<World> ourWorlds() {
        java.util.List<World> worlds = new java.util.ArrayList<>();
        if (addon.getOverWorld() != null) {
            worlds.add(addon.getOverWorld());
        }
        if (addon.getNetherWorld() != null) {
            worlds.add(addon.getNetherWorld());
        }
        return worlds;
    }

    /**
     * Placing a boat item spawns a plain vanilla entity that knows nothing
     * about the hold it came from - so the sailor who launched their own boat
     * was met with "take the Oak Boat?" over their own ship. Carry the
     * record's id from the item onto the entity as it is placed, and the hull
     * in the water IS the boat they were holding.
     */
    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onPlace(org.bukkit.event.entity.EntityPlaceEvent event) {
        if (!(event.getEntity() instanceof Boat boat) || event.getPlayer() == null
                || !inOurWorlds(boat.getWorld())) {
            return;
        }
        ItemStack used = event.getHand() == org.bukkit.inventory.EquipmentSlot.OFF_HAND
                ? event.getPlayer().getInventory().getItemInOffHand()
                : event.getPlayer().getInventory().getItemInMainHand();
        Optional<BoatHold> hold = addon.getBoatService().recordFor(used);
        if (hold.isEmpty()) {
            return; // A boat with no record: recordFor(entity) registers it later
        }
        BoatHold holdRecord = hold.get();
        holdRecord.setExpiresAt(0); // Afloat again: only ITEM hulls run a clock
        addon.getBoatService().stamp(boat, holdRecord);
        addon.getHoldManager().rememberPosition(holdRecord, boat.getLocation());
        addon.getBoatService().logbook("placed by " + event.getPlayer().getName(), holdRecord, boat.getLocation());
        // Creative placement does not consume the item, so the pack still
        // holds a stamped copy of the hull now floating in the water - the
        // duplication that put TWO spruce boats in one death drop
        // (archaeology, 2026-08-06). The entity is the boat now; every
        // carried copy of its id must go.
        if (event.getPlayer().getGameMode() == org.bukkit.GameMode.CREATIVE) {
            for (ItemStack stack : event.getPlayer().getInventory().getContents()) {
                if (stack != null && holdRecord.getUniqueId().equals(BoatService.boatId(stack))) {
                    stack.setAmount(0);
                }
            }
        }
    }

    // -------------------------------------------------------------- boarding

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onEnter(VehicleEnterEvent event) {
        if (!(event.getVehicle() instanceof Boat boat) || !(event.getEntered() instanceof Player player)
                || !inOurWorlds(boat.getWorld())) {
            return;
        }
        // A passenger climbing aboard an occupied boat takes nothing over
        if (!boat.getPassengers().isEmpty()) {
            return;
        }
        // An encounter crew's boat is scenery: sit in it if you like, but it
        // can never be captured or registered as a hull of your own
        if (world.bentobox.tradewinds.encounters.EncounterService.isEncounterCraft(boat)) {
            return;
        }
        BoatHold hold = addon.getBoatService().recordFor(boat);
        UUID playerId = player.getUniqueId();
        if (playerId.toString().equals(hold.getOwner())) {
            // Their own boat: make sure it is the active one and board
            addon.getBoatService().claim(player, hold);
            hideLabel(boat);
            return;
        }
        if (isProtected(hold, boat.getLocation())) {
            event.setCancelled(true);
            User.getInstance(player).sendMessage("tradewinds.boat.protected");
            return;
        }
        // Capture: always ask - the boat they leave behind keeps their cargo,
        // and if their own boat is alongside they may prefer just the cargo
        event.setCancelled(true);
        offerFoundBoat(player, hold, () -> {
            takeBoat(player, hold);
            boat.addPassenger(player);
            hideLabel(boat);
        });
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onExit(VehicleExitEvent event) {
        if (!(event.getVehicle() instanceof Boat boat) || !inOurWorlds(boat.getWorld())) {
            return;
        }
        addon.getHoldManager().boat(BoatService.boatId(boat)).ifPresent(hold -> {
            addon.getHoldManager().rememberPosition(hold, boat.getLocation());
            // Show the plate again a tick later, once the seat is empty
            Bukkit.getScheduler().runTask(addon.getPlugin(), () -> addon.getBoatService().label(boat, hold));
        });
    }

    private void hideLabel(Boat boat) {
        Bukkit.getScheduler().runTask(addon.getPlugin(), () -> boat.setCustomNameVisible(false));
    }

    // ------------------------------------------------------------ protection

    /**
     * An owned boat sitting unattended in an island's PROTECTED space is
     * untouchable by anyone but its owner - unless the island is ANARCHIC.
     * Unowned boats are never protected.
     */
    boolean isProtected(BoatHold hold, Location where) {
        if (hold.isUnowned() || where == null || where.getWorld() == null
                || !where.getWorld().equals(addon.getOverWorld())) {
            return false;
        }
        Optional<IslandSpec> island = islandProtecting(where);
        return island.isPresent() && island.get().band() != world.bentobox.tradewinds.galaxy.SecurityBand.ANARCHIC;
    }

    /**
     * The island whose PROTECTED space (not merely its island space) covers a
     * position - the trading gate and boat protection share this reach.
     */
    public Optional<IslandSpec> islandProtecting(Location where) {
        if (where == null || where.getWorld() == null || !where.getWorld().equals(addon.getOverWorld())) {
            return Optional.empty();
        }
        int range = addon.getSettings().getIslandProtectionRange();
        return addon.getGalaxyEngine(where.getWorld().getSeed())
                .islandsNear(where.getBlockX(), where.getBlockZ(), range).stream()
                .filter(spec -> spec.distanceSquared(where.getBlockX(), where.getBlockZ()) <= (long) range * range)
                .findFirst();
    }

    /**
     * Breaking a boat: the owner always may (that is the logout-safety
     * route); anyone else is refused while it is protected. A broken boat
     * always yields its item, cargo record and all - lava is the only thing
     * that truly destroys one.
     */
    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onDestroy(VehicleDestroyEvent event) {
        if (!(event.getVehicle() instanceof Boat boat) || !inOurWorlds(boat.getWorld())) {
            return;
        }
        // An encounter crew's boat splinters to nothing: no drop, no record.
        // A hull per pirate attack would be farmable, and registering them
        // littered the database with unowned phantoms (2026-08-06).
        if (world.bentobox.tradewinds.encounters.EncounterService.isEncounterCraft(boat)) {
            event.setCancelled(true);
            boat.remove();
            return;
        }
        BoatHold hold = addon.getBoatService().recordFor(boat);
        boolean byOwner = event.getAttacker() instanceof Player breaker
                && breaker.getUniqueId().toString().equals(hold.getOwner());
        if (!byOwner && isProtected(hold, boat.getLocation())) {
            event.setCancelled(true);
            if (event.getAttacker() instanceof Player breaker) {
                User.getInstance(breaker).sendMessage("tradewinds.boat.protected");
            }
            return;
        }
        Location where = boat.getLocation();
        if (boat.isInLava()) {
            // The one true destroyer: the hull and everything in it burn
            addon.getBoatService().logbook("burned in lava - hull, cargo and record are gone", hold, where);
            forgetBoat(hold);
            return;
        }
        event.setCancelled(true);
        boat.eject();
        boat.remove();
        Material material = Material.matchMaterial(hold.getMaterial());
        ItemStack item = addon.getBoatService()
                .stamp(new ItemStack(material == null ? Material.OAK_BOAT : material), hold);
        // Only an ITEM ever expires - boat entities stay until someone takes them
        hold.setExpiresAt(System.currentTimeMillis() + addon.getSettings().getDroppedBoatTtlMinutes() * 60_000L);
        addon.getHoldManager().rememberPosition(hold, where);
        addon.getBoatService().logbook("broken up into an item"
                + (event.getAttacker() == null ? "" : " by " + event.getAttacker().getName()), hold, where);
        where.getWorld().dropItemNaturally(where, item);
    }

    /**
     * A sailor going down with a boat item in their pack: the hull scatters
     * with the rest of the kit, so the record's position must follow it to
     * the wreck site - or the chart points forever at wherever the boat was
     * last PLACED. That stale bearing sent its owner searching an empty dock
     * while the real hull lay 4,400 blocks away (archaeology, 2026-08-06).
     * No TTL is started: death drops freeze in unloaded chunks, and a
     * wall-clock expiry would delete the cargo record while the item was
     * still recoverable. Vanilla's despawn clock is already held off by
     * {@link #onDespawn}.
     */
    @EventHandler(priority = EventPriority.MONITOR)
    public void onDeath(org.bukkit.event.entity.PlayerDeathEvent event) {
        Player player = event.getEntity();
        if (!inOurWorlds(player.getWorld())) {
            return;
        }
        for (ItemStack stack : event.getDrops()) {
            addon.getBoatService().recordFor(stack).ifPresent(hold -> {
                addon.getHoldManager().rememberPosition(hold, player.getLocation());
                addon.getBoatService().logbook("went down with " + player.getName(), hold,
                        player.getLocation());
            });
        }
    }

    /**
     * A boat that burned: the record goes, and anyone pointing at it forgets.
     */
    private void forgetBoat(BoatHold hold) {
        if (!hold.isUnowned()) {
            UUID owner = UUID.fromString(hold.getOwner());
            addon.getHoldManager().clearActiveBoat(owner);
        }
        addon.getHoldManager().ownerOfOldBoat(hold.getUniqueId())
                .ifPresent(addon.getHoldManager()::clearOldBoat);
        addon.getHoldManager().delete(hold.getUniqueId());
    }

    // ---------------------------------------------------------------- items

    /**
     * Dropping your own boat item just puts it on the ground - the cargo was
     * always the boat's, so nothing has to move. It keeps its owner (and so
     * its protection) until someone claims it.
     */
    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onDrop(PlayerDropItemEvent event) {
        ItemStack stack = event.getItemDrop().getItemStack();
        addon.getBoatService().recordFor(stack).ifPresent(hold -> {
            hold.setExpiresAt(
                    System.currentTimeMillis() + addon.getSettings().getDroppedBoatTtlMinutes() * 60_000L);
            addon.getHoldManager().rememberPosition(hold, event.getItemDrop().getLocation());
        });
    }

    /**
     * Picking up a boat item. Yours: just a pickup. Smaller or equal: you
     * take the goods and leave the hull (you never pocket a spare boat).
     * Bigger, or you have none: it becomes your boat - after the same
     * confirmation boarding gets, because your old boat keeps its cargo.
     */
    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onPickup(EntityPickupItemEvent event) {
        if (!(event.getEntity() instanceof Player player)) {
            return;
        }
        ItemStack stack = event.getItem().getItemStack();
        if (!inOurWorlds(player.getWorld()) || !BoatRanks.isBoatItem(stack.getType())
                || addon.getBoatRanks().slots(stack.getType()) <= 0) {
            return;
        }
        UUID playerId = player.getUniqueId();
        BoatHold hold = addon.getBoatService().recordFor(stack).orElseGet(() -> {
            // A plain vanilla boat item: register it as the unowned hull it is
            BoatHold fresh = addon.getHoldManager().create(stack.getType(), null);
            addon.getBoatService().stamp(stack, fresh);
            event.getItem().setItemStack(stack);
            return fresh;
        });
        // Already carrying this very boat? Then the ground copy is a ghost -
        // two items, one hold, each opening the same cargo (the 2026-08-02
        // exploit, reborn as creative-placement duplication). The sea keeps
        // the spare.
        if (addon.getBoatService().isCarrying(player, hold)) {
            event.setCancelled(true);
            event.getItem().remove();
            addon.getBoatService().logbook("duplicate hull dissolved (already in " + player.getName()
                    + "'s pack)", hold, event.getItem().getLocation());
            return;
        }
        if (playerId.toString().equals(hold.getOwner())) {
            hold.setExpiresAt(0);
            addon.getHoldManager().save(hold);
            addon.getHoldManager().setActiveBoat(playerId, hold);
            addon.getBoatService().logbook("picked up by its owner", hold, event.getItem().getLocation());
            return; // their own boat, back in the pack
        }
        if (System.currentTimeMillis() < swapQuietUntil.getOrDefault(playerId, 0L)) {
            // Just swapped: leave the hull we shed alone for a moment rather
            // than offer to swap straight back
            event.setCancelled(true);
            return;
        }
        if (addon.getHoldService().active(playerId).isEmpty()) {
            // Nothing to lose: claim it outright, no dialog
            event.getItem().remove();
            hold.setExpiresAt(0);
            addon.getBoatService().claim(player, hold);
            addon.getBoatService().giveBoatItem(player, hold);
            User.getInstance(player).sendMessage("tradewinds.boat.claimed", MATERIAL_PLACEHOLDER,
                    pretty(Material.matchMaterial(hold.getMaterial())));
            return;
        }
        // Someone else's hull, and they have a boat of their own: ask. Pickup
        // is automatic, so refuse it and prompt (rate-limited - standing near
        // an item fires this every tick)
        event.setCancelled(true);
        if (!shouldPrompt(playerId, hold.getUniqueId())) {
            return;
        }
        offerFoundBoat(player, hold, () -> {
            event.getItem().remove();
            hold.setExpiresAt(0);
            takeBoat(player, hold);
            addon.getBoatService().giveBoatItem(player, hold);
        });
    }

    /**
     * Ignore boat items around this player for a moment: a swap or an
     * outright purchase has just shed a hull at their feet, and the pickup
     * listener would otherwise offer it straight back.
     */
    public void quietSwaps(UUID playerId) {
        swapQuietUntil.put(playerId, System.currentTimeMillis() + SWAP_QUIET_MS);
    }

    /**
     * Take a hull as your own, dealing with the boat you already had.
     * <p>
     * If your old boat is <b>with you</b> - carried in your pack, or moored
     * within loading range - you cannot keep both (One Boat), so its cargo
     * comes across into the new hull and the empty hull is left at your feet,
     * unowned. Carrying two boat items was the 2026-08-02 exploit: both
     * opened the same hold, and the spare could be dropped and stripped over
     * and over for free fuel.
     * <p>
     * If the old boat is far away it simply stays where it is with its cargo
     * aboard - that is the OLD BOAT you row back to.
     *
     * @param player the player
     * @param hold the hull they are taking
     */
    void takeBoat(Player player, BoatHold hold) {
        quietSwaps(player.getUniqueId());
        Optional<BoatHold> previous = addon.getHoldService().active(player.getUniqueId())
                .filter(old -> !old.getUniqueId().equals(hold.getUniqueId()));
        boolean carried = previous.isPresent() && addon.getBoatService().isCarrying(player, previous.get());
        boolean alongside = carried || ownBoatWithinReach(player);
        addon.getBoatService().claim(player, hold);
        if (previous.isEmpty() || !alongside) {
            return;
        }
        // One hull, all the cargo: pour the old boat into the new one
        BoatHold old = previous.get();
        salvage(player, old);
        // The hull cannot ride in your pack alongside your ship - leave it
        addon.getBoatService().shedCarriedHull(player, old);
        // Shedding a hull on purpose, emptied, at your own feet is not
        // "losing" a boat - do not chart it as one to go back for
        if (old.isEmpty()) {
            addon.getHoldManager().clearOldBoat(player.getUniqueId());
        }
        User.getInstance(player).sendMessage(old.isEmpty() ? "tradewinds.boat.merged-empty"
                : "tradewinds.boat.merged-partial", MATERIAL_PLACEHOLDER, pretty(Material.matchMaterial(old.getMaterial())));
    }

    /**
     * The three ways to answer a hull you have found (ruled 2026-08-02): take
     * it (abandoning your own where it lies), move its cargo into your own
     * boat - offered ONLY while your boat is close enough to load, since
     * cargo is never teleported across the ocean - or leave it be.
     *
     * @param player the finder
     * @param hold the hull found
     * @param onTake what taking it does (board it, or pocket the item)
     */
    private void offerFoundBoat(Player player, BoatHold hold, Runnable onTake) {
        Material taking = Material.matchMaterial(hold.getMaterial());
        Material leaving = addon.getHoldService().boat(player.getUniqueId());
        if (leaving == null) {
            onTake.run();
            return;
        }
        int carried = hold.getCargo().stream().filter(java.util.Objects::nonNull)
                .mapToInt(ItemStack::getAmount).sum()
                + hold.getFuel().values().stream().mapToInt(Integer::intValue).sum();
        String cargo = carried > 0 ? String.valueOf(carried) : null;
        Runnable onTransfer = carried > 0 && ownBoatWithinReach(player) ? () -> {
            int moved = salvage(player, hold);
            String message = moved > 0 ? "tradewinds.boat.salvage-emptied"
                    : "tradewinds.hold.cargo-full";
            User.getInstance(player).sendMessage(message, "[amount]", String.valueOf(moved));
        } : null;
        addon.getTradeDialog().confirmBoatFound(player, pretty(taking), pretty(leaving), cargo, onTake,
                onTransfer);
    }

    /**
     * Whether the player's own boat is close enough to load cargo into. A
     * hull an ocean away cannot receive anything: the playtest moved eight
     * items into a boat thousands of blocks off, which is teleporting cargo.
     *
     * @param player the player
     * @return true if their boat is within the configured transfer range
     */
    boolean ownBoatWithinReach(Player player) {
        return addon.getHoldService().active(player.getUniqueId()).map(mine -> {
            if (mine.getWorld() == null || mine.getWorld().isEmpty()
                    || !mine.getWorld().equals(player.getWorld().getName())) {
                return false;
            }
            long range = addon.getSettings().getCargoTransferRange();
            double dx = mine.getX() - player.getLocation().getX();
            double dz = mine.getZ() - player.getLocation().getZ();
            return dx * dx + dz * dz <= (double) range * range;
        }).orElse(false);
    }

    /**
     * Pour a wreck's cargo into the player's hold, most valuable first (the
     * base-price table is the value oracle), fuel into the fuel row.
     *
     * @return how many items moved
     */
    int salvage(Player player, BoatHold wreck) {
        int moved = 0;
        // Most valuable first: with limited slots, a partial rescue should save
        // the cargo worth saving
        CargoStore.compact(wreck.getCargo());
        java.util.List<ItemStack> stacks = new java.util.ArrayList<>(wreck.getCargo());
        stacks.sort(java.util.Comparator.comparingDouble(
                (ItemStack stack) -> addon.getMarketService().basePrice(stack).orElse(0.0)).reversed());
        for (ItemStack stack : stacks) {
            int added = addon.getHoldService().add(player, stack, stack.getAmount());
            moved += added;
            if (added >= stack.getAmount()) {
                wreck.getCargo().remove(stack);
            } else if (added > 0) {
                stack.setAmount(stack.getAmount() - added);
            }
        }
        for (Map.Entry<String, Integer> entry : new java.util.ArrayList<>(wreck.getFuel().entrySet())) {
            Material material = Material.matchMaterial(entry.getKey());
            if (material == null) {
                wreck.getFuel().remove(entry.getKey());
                continue;
            }
            int added = addon.getHoldService().addFuel(player, material, entry.getValue());
            moved += added;
            if (added >= entry.getValue()) {
                wreck.getFuel().remove(entry.getKey());
            } else if (added > 0) {
                wreck.getFuel().put(entry.getKey(), entry.getValue() - added);
            }
        }
        addon.getHoldManager().save(wreck);
        return moved;
    }

    /**
     * Only prompt about the same hull every so often - item pickup fires
     * every tick you stand near it.
     */
    private boolean shouldPrompt(UUID playerId, String boatId) {
        Map<String, Long> mine = prompted.computeIfAbsent(playerId, k -> new HashMap<>());
        long now = System.currentTimeMillis();
        Long last = mine.get(boatId);
        if (last != null && now - last < PROMPT_SUPPRESSION_MS) {
            return false;
        }
        mine.put(boatId, now);
        return true;
    }

    /**
     * A tagged boat item never despawns on vanilla's clock - its own TTL owns
     * its death.
     */
    @EventHandler(priority = EventPriority.LOW, ignoreCancelled = true)
    public void onDespawn(ItemDespawnEvent event) {
        if (BoatService.boatId(event.getEntity().getItemStack()) != null) {
            event.setCancelled(true);
        }
    }

    // ------------------------------------------------------------ join/leave

    /**
     * Report a boat taken while they were away, and hand a raft to anyone
     * left treading water without one.
     */
    @EventHandler(priority = EventPriority.MONITOR)
    public void onJoin(PlayerJoinEvent event) {
        Player player = event.getPlayer();
        if (!inOurWorlds(player.getWorld())) {
            return;
        }
        Bukkit.getScheduler().runTaskLater(addon.getPlugin(), () -> {
            UUID id = player.getUniqueId();
            Optional<BoatHold> active = addon.getHoldService().active(id);
            boolean stolen = active.isPresent() && !id.toString().equals(active.get().getOwner());
            if (stolen || active.isEmpty()) {
                if (stolen) {
                    // Cleared, NOT abandoned: it belongs to whoever took it,
                    // so it must not become their OLD BOAT (and must not have
                    // its new owner stripped, which used to hand it back)
                    addon.getHoldManager().clearActiveBoat(id);
                    User.getInstance(player).sendMessage("tradewinds.boat.stolen");
                }
                if (player.getLocation().getBlock().isLiquid()) {
                    BoatHold raft = addon.getBoatService().createFor(player, respawnBoat());
                    addon.getBoatService().giveBoatItem(player, raft);
                    User.getInstance(player).sendMessage("tradewinds.boat.adrift-raft", MATERIAL_PLACEHOLDER,
                            pretty(respawnBoat()));
                }
            }
        }, 20L);
    }

    private Material respawnBoat() {
        Material material = Material.matchMaterial(String.valueOf(addon.getSettings().getRespawnBoat()));
        return material != null && addon.getBoatRanks().slots(material) > 0 ? material : Material.BAMBOO_RAFT;
    }

    // ---------------------------------------------------------------- sweep

    /**
     * Remove expired ITEM-form boats. Boat entities never expire.
     */
    public void sweepExpiredItems() {
        long now = System.currentTimeMillis();
        for (BoatHold hold : addon.getHoldManager().allBoats()) {
            if (hold.getExpiresAt() > 0 && hold.getExpiresAt() <= now) {
                removeItemAvatar(hold);
                forgetBoat(hold);
            }
        }
    }

    private void removeItemAvatar(BoatHold hold) {
        World world = hold.getWorld().isEmpty() ? null : Bukkit.getWorld(hold.getWorld());
        if (world == null || !world.isChunkLoaded(hold.getX() >> 4, hold.getZ() >> 4)) {
            return;
        }
        Location where = new Location(world, hold.getX(), hold.getY(), hold.getZ());
        world.getNearbyEntities(where, 8, 8, 8).stream().filter(Item.class::isInstance).map(Item.class::cast)
                .filter(item -> hold.getUniqueId().equals(BoatService.boatId(item.getItemStack())))
                .forEach(Item::remove);
    }

    private boolean inOurWorlds(World world) {
        return world.equals(addon.getOverWorld()) || world.equals(addon.getNetherWorld());
    }

    private static String pretty(Material material) {
        return material == null ? "?" : world.bentobox.tradewinds.economy.PriceEngine.prettify(material.name());
    }
}
