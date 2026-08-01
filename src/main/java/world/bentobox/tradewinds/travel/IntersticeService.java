package world.bentobox.tradewinds.travel;

import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.entity.Boat;
import org.bukkit.entity.Entity;
import org.bukkit.entity.EntityType;
import org.bukkit.entity.Ghast;
import org.bukkit.entity.Player;
import org.bukkit.scheduler.BukkitTask;

import io.papermc.paper.dialog.Dialog;
import io.papermc.paper.registry.data.dialog.ActionButton;
import io.papermc.paper.registry.data.dialog.DialogBase;
import io.papermc.paper.registry.data.dialog.action.DialogAction;
import io.papermc.paper.registry.data.dialog.body.DialogBody;
import io.papermc.paper.registry.data.dialog.type.DialogType;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.event.ClickCallback;
import net.kyori.adventure.text.format.NamedTextColor;
import world.bentobox.bentobox.api.user.User;
import world.bentobox.bentobox.util.Util;
import world.bentobox.tradewinds.TradeWinds;
import world.bentobox.tradewinds.api.events.TWWarpFailedEvent;
import world.bentobox.tradewinds.galaxy.IslandSpec;

/**
 * The interstice: warps sometimes fail, dropping the sailor into a hostile
 * Nether sea partway along the route with Ghasts inbound. The fuel was spent
 * at engagement, so <b>re-engaging to the original destination is always
 * free</b> - the failure is a detour, never a loss, and stranding is
 * impossible (spec §3.3). The way out is offered automatically while the
 * player floats there.
 *
 * @author tastybento
 */
public class IntersticeService {

    /**
     * Disambiguates User#getTranslationAsComponent, whose no-variable call is
     * ambiguous between the String... and TagResolver... overloads.
     */
    private static final String[] NO_VARS = new String[0];

    /** Player -> the destination cell they are still owed, free of charge. */
    private final Map<UUID, String> pendingDestination = new ConcurrentHashMap<>();
    private final Map<UUID, Long> lastPrompt = new ConcurrentHashMap<>();
    /** Players whose next warp is rigged to fail, consumed on use. */
    private final java.util.Set<UUID> rigged = java.util.concurrent.ConcurrentHashMap.newKeySet();
    /** Player -> when they were stranded, for the arrival grace. */
    private final Map<UUID, Long> arrivals = new ConcurrentHashMap<>();

    private final TradeWinds addon;
    private BukkitTask task;

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
        pendingDestination.put(player.getUniqueId(), to.cellX() + "," + to.cellZ());
        // Partway along the route (seeded-ish: middle third)
        double fraction = 0.35 + Math.random() * 0.3;
        int x = (int) Math.round(from.centerX() + (to.centerX() - from.centerX()) * fraction);
        int z = (int) Math.round(from.centerZ() + (to.centerZ() - from.centerZ()) * fraction);
        // Open water here too: the interstice has its own sea floor, and
        // dropping a castaway inside it would be the same suffocation bug
        // No galaxy in the interstice: no islands, no docks, and a floor that
        // cannot reach the surface, so the intended point always serves
        Location target = SeaArrival.openSeaNear(null, addon.getNetherWorld(), x, z,
                addon.getSettings().getIntersticeSeaHeight());

        Entity vehicle = player.getVehicle();
        if (vehicle != null) {
            player.leaveVehicle();
        }
        Util.teleportAsync(player, target).thenRun(() -> {
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
        });
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
            return; // Dark water and nothing in it. The interstice is unsettling enough.
        }
        int min = addon.getSettings().getIntersticeGhastsMin();
        int count = min + (int) (Math.random() * Math.max(1,
                addon.getSettings().getIntersticeGhastsMax() - min + 1));
        double near = addon.getSettings().getIntersticeGhastDistance();
        for (int i = 0; i < count; i++) {
            double angle = Math.random() * Math.PI * 2;
            // A tight band around the configured distance. This used to add up
            // to 30 blocks on top of it, which quietly undid the setting: at a
            // base of 44 they were arriving as far out as 74.
            double distance = near * (0.85 + Math.random() * 0.3);
            Location spot = around.clone().add(Math.cos(angle) * distance, 6 + Math.random() * 8,
                    Math.sin(angle) * distance);
            Entity ghast = around.getWorld().spawnEntity(spot, EntityType.GHAST);
            if (ghast instanceof Ghast g) {
                // Deliberately NOT targeted: let the player decide whether this
                // is a fight
                g.setRemoveWhenFarAway(true);
            }
        }
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
     * Offer the free re-engage to interstice castaways, and clean up.
     */
    private void tick() {
        if (addon.getNetherWorld() == null) {
            return;
        }
        long now = System.currentTimeMillis() / 1000;
        for (Player player : addon.getNetherWorld().getPlayers()) {
            long last = lastPrompt.getOrDefault(player.getUniqueId(), 0L);
            if (now - last < addon.getSettings().getIntersticePromptSeconds()) {
                continue;
            }
            lastPrompt.put(player.getUniqueId(), now);
            openReEngageDialog(player);
        }
        hunt();
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
                // A way to put the dialog down. The offer repeats every
                // prompt-seconds and the fuel is already spent, so declining
                // costs nothing - but without an exit the only way to look at
                // the sea was to take the warp.
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
        addon.getWarpService().deliver(player, target);
    }

    private Optional<IslandSpec> owedDestination(Player player) {
        String cell = pendingDestination.get(player.getUniqueId());
        if (cell == null) {
            return Optional.empty();
        }
        String[] parts = cell.split(",");
        return addon.getGalaxyEngine(addon.getOverWorld().getSeed())
                .islandInCell(Integer.parseInt(parts[0]), Integer.parseInt(parts[1]));
    }

    private IslandSpec nearestCharted(Player player) {
        var engine = addon.getGalaxyEngine(addon.getOverWorld().getSeed());
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
