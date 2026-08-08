package world.bentobox.tradewinds.travel;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Random;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.NamespacedKey;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.entity.Boat;
import org.bukkit.entity.Entity;
import org.bukkit.entity.EntityType;
import org.bukkit.entity.Ghast;
import org.bukkit.entity.Player;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.scheduler.BukkitTask;

import io.papermc.paper.dialog.Dialog;
import io.papermc.paper.registry.data.dialog.ActionButton;
import io.papermc.paper.registry.data.dialog.DialogBase;
import io.papermc.paper.registry.data.dialog.action.DialogAction;
import io.papermc.paper.registry.data.dialog.body.DialogBody;
import io.papermc.paper.registry.data.dialog.type.DialogType;
import net.kyori.adventure.text.event.ClickCallback;
import world.bentobox.bentobox.api.user.User;
import world.bentobox.bentobox.util.Util;
import world.bentobox.tradewinds.TradeWinds;
import world.bentobox.tradewinds.api.events.TWWarpFailedEvent;
import world.bentobox.tradewinds.ocean.IslandSpec;

/**
 * The interstice: warps sometimes fail, dropping the sailor into a hostile
 * Nether sea partway along the route with Ghasts inbound. The fuel was spent
 * at engagement, so <b>re-engaging to the original destination is always
 * free</b> - the failure is a detour, never a loss, and stranding is
 * impossible (spec §3.3). The way out is offered ONCE on arrival, and on
 * demand ever after: {@code /tw go} (or {@code /tw warp}) raises the offer
 * again, and a quiet action-bar line reminds castaways it exists. It used to
 * re-open the dialog every prompt-seconds, which was fine while the
 * interstice was empty water and unbearable once it had wart to harvest,
 * blazes to fight and wrecks to dive (ruled by Ben, 2026-08-07).
 *
 * @author tastybento
 */
public class IntersticeService {

    /**
     * Disambiguates User#getTranslationAsComponent, whose no-variable call is
     * ambiguous between the String... and TagResolver... overloads.
     */
    private static final String[] NO_VARS = new String[0];

    /** Log prefix for interstice events. */
    private static final String LOG_PREFIX = "Interstice: ";

    /** Random for gameplay randomness. */
    private static final Random RANDOM = new Random();

    /** Ghasts spawned by the current stranding, for the follow-up head count. */
    private final List<Ghast> spawnedGhasts = new java.util.ArrayList<>();

    /** Room a ghast needs under the interstice ceiling - they are 4 blocks tall. */
    private static final int GHAST_CLEARANCE = 6;

    /** Pending-destination marker for a home warp: not a ocean cell. */
    private static final String HOME_PENDING = "home";

    /** Player -> the destination cell they are still owed, free of charge. */
    private final Map<UUID, String> pendingDestination = new ConcurrentHashMap<>();
    private final Map<UUID, Long> lastPrompt = new ConcurrentHashMap<>();
    /** Players who have already seen this visit's dialog - it shows ONCE. */
    private final java.util.Set<UUID> prompted = java.util.concurrent.ConcurrentHashMap.newKeySet();
    /** Players whose next warp is rigged to fail, consumed on use. */
    private final java.util.Set<UUID> rigged = java.util.concurrent.ConcurrentHashMap.newKeySet();
    /** Player -> when they were stranded, for the arrival grace. */
    private final Map<UUID, Long> arrivals = new ConcurrentHashMap<>();

    private final TradeWinds addon;
    private BukkitTask task;
    private NamespacedKey intersticeKey;

    public IntersticeService(TradeWinds addon) {
        this.addon = addon;
    }

    public void start() {
        task = Bukkit.getScheduler().runTaskTimer(addon.getPlugin(), this::tick, 100L, 100L);
    }

    public void stop() {
        if (task != null) {
            task.cancel();
        }
    }

    /**
     * Does this warp fail? Pure roll against config (wanted players may face
     * worse odds once reputation lands).
     */
    public boolean rollFailure() {
        return Math.random() < addon.getSettings().getWarpFailureChance();
    }

    /**
     * Does this player's warp fail? Honours a rigged failure first, consuming
     * it, then falls back to the ordinary roll.
     *
     * @param playerId the warping player
     * @return true if this warp drops them into the interstice
     */
    public boolean rollFailure(UUID playerId) {
        if (rigged.remove(playerId)) {
            return true;
        }
        return rollFailure();
    }

    /**
     * Rig a player's next warp to fail.
     * <p>
     * Written for testing - a 5% failure is miserable to reproduce on demand -
     * but it is also a live tool: an admin can put a specific sailor into the
     * interstice to liven up a session. The flag is consumed by the next warp,
     * so it can never sit forgotten on someone's account.
     *
     * @param playerId the player to rig
     * @return true if they were not already rigged
     */
    public boolean rigNextWarp(UUID playerId) {
        return rigged.add(playerId);
    }

    /**
     * Clear a rigged failure without using it.
     *
     * @param playerId the player
     * @return true if a rig was cleared
     */
    public boolean clearRig(UUID playerId) {
        return rigged.remove(playerId);
    }

    /**
     * Whether a player's next warp is rigged to fail.
     *
     * @param playerId the player
     * @return true if rigged
     */
    public boolean isRigged(UUID playerId) {
        return rigged.contains(playerId);
    }

    /**
     * Drop a player into the interstice partway along their route.
     */
    public void strand(Player player, IslandSpec from, IslandSpec to) {
        if (addon.getNetherWorld() == null) {
            return;
        }
        // A home destination is not a ocean cell: mark it, and resolve it
        // back through the player's island on re-engage (Stage 7b)
        pendingDestination.put(player.getUniqueId(),
                HomePort.isHome(to) ? HOME_PENDING : to.cellX() + "," + to.cellZ());
        // Partway along the route (seeded-ish: middle third)
        double fraction = 0.35 + Math.random() * 0.3;
        int x = (int) Math.round(from.centerX() + (to.centerX() - from.centerX()) * fraction);
        int z = (int) Math.round(from.centerZ() + (to.centerZ() - from.centerZ()) * fraction);
        // Open water here too: the interstice has its own sea floor, and
        // dropping a castaway inside it would be the same suffocation bug.
        // The interstice now HAS land (wart shoals) and masonry (watchtowers)
        // - the feature map is the column test the ocean cannot provide
        var map = addon.getIntersticeMap(addon.getNetherWorld().getSeed());
        Location target = SeaArrival.openSeaNear(map::isOpenWater, addon.getNetherWorld(), x, z,
                addon.getSettings().getIntersticeSeaHeight());

        Entity vehicle = player.getVehicle();
        if (vehicle != null) {
            player.leaveVehicle();
        }
        // Everything after the teleport runs on the MAIN THREAD, explicitly.
        // A teleport future's continuation is not guaranteed to, and spawning
        // an entity off-thread throws "Asynchronous entity add!" - an exception
        // a CompletableFuture then swallows whole. That is why the arrival
        // sound played and the ghasts never appeared: the throw took out
        // everything after it, silently, including the warp-failed event.
        Util.teleportAsync(player, target)
                .thenRun(() -> Bukkit.getScheduler().runTask(addon.getPlugin(),
                        () -> onArrival(player, vehicle, target, from, to)))
                .exceptionally(error -> {
                    addon.logError("Interstice teleport failed for " + player.getName() + ": " + error);
                    return null;
                });
    }

    /**
     * The arrival itself, on the main thread.
     */
    private void onArrival(Player player, Entity vehicle, Location target, IslandSpec from, IslandSpec to) {
        try {
            if (vehicle instanceof Boat boat && boat.isValid()) {
                boat.teleportAsync(target).thenRun(() -> Bukkit.getScheduler().runTask(addon.getPlugin(),
                        () -> boat.addPassenger(player)));
            }
            target.getWorld().spawnParticle(Particle.PORTAL, target, 120, 2, 2, 2, 0.6);
            target.getWorld().playSound(target, Sound.ENTITY_GHAST_SCREAM, 1.0f, 0.7f);
            User.getInstance(player).sendMessage("tradewinds.interstice.stranded");
            arrivals.put(player.getUniqueId(), System.currentTimeMillis());
            spawnGhasts(player, target);
            Bukkit.getPluginManager().callEvent(new TWWarpFailedEvent(player, from, to));
        } catch (Exception e) {
            // Never let an arrival fail silently again
            addon.logError("Interstice arrival failed for " + player.getName() + ": " + e);
            e.printStackTrace();
        }
    }

    /**
     * Put whatever is out there in the dark, at a distance.
     * <p>
     * The first cut spawned ghasts at 20-35 blocks and called
     * {@code setTarget} on arrival - inside a ghast's 64-block detection range
     * and already hunting. A player whose very first warp failed was under fire
     * before they could read the dialog offering them the way out, and died
     * having lost everything. A failed warp is meant to be a detour, not an
     * execution.
     * <p>
     * So: sometimes nothing comes at all, and when it does it starts beyond its
     * own detection range and is not told where the player is. Engaging is the
     * player's choice - re-engage the warp and leave, or go hunting.
     */
    private void spawnGhasts(Player player, Location around) {
        if (Math.random() >= addon.getSettings().getIntersticeGhastChance()) {
            addon.log(LOG_PREFIX + player.getName() + " stranded at " + describe(around)
                    + " - nothing came (ghast-chance roll)");
            return; // Dark water and nothing in it. The interstice is unsettling enough.
        }
        int min = addon.getSettings().getIntersticeGhastsMin();
        int count = min + RANDOM.nextInt(Math.max(1,
                addon.getSettings().getIntersticeGhastsMax() - min + 1));
        double near = addon.getSettings().getIntersticeGhastDistance();
        int spawned = 0;
        for (int i = 0; i < count; i++) {
            double angle = Math.random() * Math.PI * 2;
            // A tight band around the configured distance. This used to add up
            // to 30 blocks on top of it, which quietly undid the setting: at a
            // base of 44 they were arriving as far out as 74.
            double distance = near * (0.85 + Math.random() * 0.3);
            Location spot = around.clone().add(Math.cos(angle) * distance, 6 + Math.random() * 8,
                    Math.sin(angle) * distance);
            // A ghast is 4 blocks across, and the interstice now has a lid:
            // keep it clear of both the ceiling and the water
            int ceiling = addon.getSettings().getIntersticeCeilingHeight();
            int sea = addon.getSettings().getIntersticeSeaHeight();
            if (ceiling > 0) {
                spot.setY(Math.min(spot.getY(), (double) sea + ceiling - GHAST_CLEARANCE));
            }
            spot.setY(Math.max(spot.getY(), sea + 3.0));
            Entity ghast = around.getWorld().spawnEntity(spot, EntityType.GHAST);
            if (ghast instanceof Ghast g) {
                // Deliberately NOT targeted: let the player decide whether this
                // is a fight. But they must NOT despawn: they arrive right at
                // the 32-block random-despawn boundary, so removeWhenFarAway
                // was quietly deleting them moments after they appeared.
                // Cleanup is ours instead - see sweep().
                g.setRemoveWhenFarAway(false);
                g.getPersistentDataContainer().set(intersticeKey(), PersistentDataType.BYTE, (byte) 1);
                spawned++;
                spawnedGhasts.add(g);
            }
            addon.log(LOG_PREFIX + "ghast spawned at " + describe(spot) + " ("
                    + (int) spot.distance(around) + " blocks from the player)");
        }
        addon.log(LOG_PREFIX + player.getName() + " stranded at " + describe(around) + " in "
                + around.getWorld().getName() + " - " + spawned + " of " + count + " ghasts spawned");
        // Check again shortly: "spawned" only means the call returned. Anything
        // that removes them does so afterwards, and silently.
        List<Ghast> watch = List.copyOf(spawnedGhasts);
        spawnedGhasts.clear();
        Bukkit.getScheduler().runTaskLater(addon.getPlugin(), () -> {
            long alive = watch.stream().filter(Entity::isValid).count();
            addon.log(LOG_PREFIX + alive + " of " + watch.size()
                    + " ghasts still present 5s after spawning");
        }, 100L);
    }

    /**
     * Marks a mob as one the interstice put there, so it can be cleaned up.
     *
     * @return the PDC key
     */
    private NamespacedKey intersticeKey() {
        if (intersticeKey == null) {
            intersticeKey = new NamespacedKey(addon.getPlugin(), "interstice");
        }
        return intersticeKey;
    }

    /**
     * Clear out ghasts nobody is left to be menaced by.
     * <p>
     * They no longer despawn on their own - being spawned right at the
     * 32-block random-despawn boundary was deleting them moments after they
     * appeared - so the tidying is ours to do.
     */
    private void sweep() {
        for (Entity entity : addon.getNetherWorld().getEntities()) {
            if (!entity.getPersistentDataContainer().has(intersticeKey(), PersistentDataType.BYTE)) {
                continue;
            }
            boolean watched = entity.getNearbyEntities(160, 160, 160).stream().anyMatch(Player.class::isInstance);
            if (!watched) {
                entity.remove();
            }
        }
    }

    /**
     * Compact coordinates for the console.
     */
    private static String describe(Location location) {
        return location.getBlockX() + "," + location.getBlockY() + "," + location.getBlockZ();
    }

    /**
     * How long a freshly stranded sailor is left alone, in millis.
     */
    private long graceMillis() {
        return addon.getSettings().getIntersticeGraceSeconds() * 1000L;
    }

    /**
     * Whether a player is still inside their arrival grace - long enough to
     * read the dialog and take the free way out.
     *
     * @param playerId the player
     * @return true during the grace window
     */
    public boolean isInGrace(UUID playerId) {
        Long arrived = arrivals.get(playerId);
        return arrived != null && System.currentTimeMillis() - arrived < graceMillis();
    }

    /**
     * Offer the free re-engage to interstice castaways, and clean up. The
     * DIALOG shows once per visit; after that the offer lives on the action
     * bar (every prompt-seconds; 0 silences it) and behind {@code /tw go}.
     */
    private void tick() {
        if (addon.getNetherWorld() == null) {
            return;
        }
        long now = System.currentTimeMillis() / 1000;
        java.util.Set<UUID> present = new java.util.HashSet<>();
        for (Player player : addon.getNetherWorld().getPlayers()) {
            present.add(player.getUniqueId());
            if (prompted.add(player.getUniqueId())) {
                lastPrompt.put(player.getUniqueId(), now);
                openReEngageDialog(player);
                continue;
            }
            int period = addon.getSettings().getIntersticePromptSeconds();
            long last = lastPrompt.getOrDefault(player.getUniqueId(), 0L);
            if (period > 0 && now - last >= period) {
                lastPrompt.put(player.getUniqueId(), now);
                User.getInstance(player).sendMessage("tradewinds.interstice.way-out");
            }
        }
        // Leaving the interstice (or logging out) re-arms the one-time dialog
        prompted.retainAll(present);
        hunt();
        sweep();
    }

    /**
     * Once a castaway's grace has run out, whatever is out there notices them.
     * <p>
     * Ghasts are spawned without a target so that arriving is not an ambush,
     * but nothing ever re-aimed them - so they drifted, and the encounter
     * simply never happened. The free way out is always on offer; staying is
     * the choice that has consequences.
     */
    private void hunt() {
        for (Player player : addon.getNetherWorld().getPlayers()) {
            if (isInGrace(player.getUniqueId()) || player.isDead()
                    || player.getGameMode() != org.bukkit.GameMode.SURVIVAL) {
                continue;
            }
            double radius = addon.getSettings().getIntersticeGhastDistance() * 2;
            player.getNearbyEntities(radius, radius, radius).stream()
                    .filter(Ghast.class::isInstance).map(Ghast.class::cast)
                    .filter(ghast -> ghast.getTarget() == null)
                    .forEach(ghast -> ghast.setTarget(player));
        }
    }

    /**
     * The way home: the owed destination free of charge, or - if that is
     * somehow lost (a relog, an admin teleport) - any charted island, also
     * free. Nobody is ever stranded.
     */
    public void openReEngageDialog(Player player) {
        Optional<IslandSpec> destination = owedDestination(player);
        IslandSpec target = destination.orElseGet(() -> nearestCharted(player));
        if (target == null) {
            return;
        }
        User user = User.getInstance(player);
        ActionButton engage = ActionButton.create(
                user.getTranslationAsComponent("tradewinds.ui.interstice.re-engage", "[name]", target.name()),
                user.getTranslationAsComponent("tradewinds.ui.interstice.re-engage-tooltip", NO_VARS), 320,
                DialogAction.customClick((response, audience) -> reEngage(player, target),
                        ClickCallback.Options.builder().build()));
        Dialog dialog = Dialog.create(factory -> factory.empty()
                .base(DialogBase.builder(
                        user.getTranslationAsComponent("tradewinds.ui.interstice.title", NO_VARS))
                        .body(java.util.List.of(DialogBody.plainMessage(
                                user.getTranslationAsComponent("tradewinds.ui.interstice.body", NO_VARS))))
                        .build())
                // A way to put the dialog down. The fuel is already spent and
                // /tw go raises the offer again whenever, so declining costs
                // nothing - but without an exit the only way to look at the
                // sea was to take the warp.
                .type(DialogType.multiAction(java.util.List.of(engage))
                        .exitAction(ActionButton.builder(
                                user.getTranslationAsComponent("tradewinds.ui.interstice.stay", NO_VARS))
                                .tooltip(user.getTranslationAsComponent(
                                        "tradewinds.ui.interstice.stay-tooltip", NO_VARS))
                                .width(320).build())
                        .columns(1).build()));
        player.showDialog(dialog);
    }

    private void reEngage(Player player, IslandSpec target) {
        pendingDestination.remove(player.getUniqueId());
        lastPrompt.remove(player.getUniqueId());
        prompted.remove(player.getUniqueId());
        addon.getWarpService().deliver(player, target);
    }

    private Optional<IslandSpec> owedDestination(Player player) {
        String cell = pendingDestination.get(player.getUniqueId());
        if (cell == null) {
            return Optional.empty();
        }
        if (HOME_PENDING.equals(cell)) {
            // Unclaimed while stranded -> empty, and the caller falls back
            // to the nearest charted island: nobody is ever stranded
            return HomePort.specFor(addon, User.getInstance(player));
        }
        String[] parts = cell.split(",");
        return addon.getOceanEngine(addon.getOverWorld().getSeed())
                .islandInCell(Integer.parseInt(parts[0]), Integer.parseInt(parts[1]));
    }

    private IslandSpec nearestCharted(Player player) {
        var engine = addon.getOceanEngine(addon.getOverWorld().getSeed());
        return addon.getPlayerDataManager().get(player.getUniqueId()).getChartedIslands().stream()
                .map(key -> key.split(","))
                .map(cell -> engine.islandInCell(Integer.parseInt(cell[0]), Integer.parseInt(cell[1])))
                .flatMap(Optional::stream)
                .min(java.util.Comparator.comparingLong(
                        spec -> spec.distanceSquared(player.getLocation().getBlockX(),
                                player.getLocation().getBlockZ())))
                .orElse(null);
    }

    /**
     * Whether a player is owed a free jump (test/API visibility).
     */
    public boolean hasPendingDestination(UUID playerId) {
        return pendingDestination.containsKey(playerId);
    }
}
