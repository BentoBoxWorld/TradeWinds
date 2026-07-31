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

    /** Player -> the destination cell they are still owed, free of charge. */
    private final Map<UUID, String> pendingDestination = new ConcurrentHashMap<>();
    private final Map<UUID, Long> lastPrompt = new ConcurrentHashMap<>();

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
        Location target = new Location(addon.getNetherWorld(), x + 0.5,
                addon.getSettings().getIntersticeSeaHeight() + 1.0, z + 0.5);

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
            spawnGhasts(player, target);
            Bukkit.getPluginManager().callEvent(new TWWarpFailedEvent(player, from, to));
        });
    }

    private void spawnGhasts(Player player, Location around) {
        int min = addon.getSettings().getIntersticeGhastsMin();
        int count = min + (int) (Math.random() * Math.max(1,
                addon.getSettings().getIntersticeGhastsMax() - min + 1));
        for (int i = 0; i < count; i++) {
            double angle = Math.random() * Math.PI * 2;
            double distance = 20 + Math.random() * 15;
            Location spot = around.clone().add(Math.cos(angle) * distance, 12 + Math.random() * 8,
                    Math.sin(angle) * distance);
            Entity ghast = around.getWorld().spawnEntity(spot, EntityType.GHAST);
            if (ghast instanceof Ghast g) {
                g.setTarget(player);
                g.setRemoveWhenFarAway(true);
            }
        }
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
        ActionButton engage = ActionButton.create(
                Component.text("Re-engage the warp to " + target.name(), NamedTextColor.AQUA),
                Component.text("Free - the fuel was spent when you first engaged"), 320,
                DialogAction.customClick((response, audience) -> reEngage(player, target),
                        ClickCallback.Options.builder().build()));
        Dialog dialog = Dialog.create(factory -> factory.empty()
                .base(DialogBase.builder(Component.text("The Interstice"))
                        .body(java.util.List.of(DialogBody.plainMessage(Component.text(
                                "Dark water, and something screaming above it. Your course is still paid for.",
                                NamedTextColor.GRAY))))
                        .build())
                .type(DialogType.multiAction(java.util.List.of(engage)).columns(1).build()));
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
