package world.bentobox.tradewinds.tasks;

import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.scheduler.BukkitTask;

import net.kyori.adventure.bossbar.BossBar;
import net.kyori.adventure.text.Component;
import world.bentobox.bentobox.api.user.User;
import world.bentobox.tradewinds.TradeWinds;
import world.bentobox.tradewinds.galaxy.DockPlan;
import world.bentobox.tradewinds.galaxy.GalaxyEngine;
import world.bentobox.tradewinds.galaxy.IslandSpec;
import world.bentobox.tradewinds.galaxy.SecurityBand;

/**
 * The navigation boss bar: while in an island's waters it shows the island
 * name, the player's standing, and the distance to the dock - steer toward the
 * dock and watch the number fall. Bar color tracks the security band, and the
 * bar fills as you close on the pier.
 *
 * @author tastybento
 */
public class NavigationBarTask implements Runnable {

    private final TradeWinds addon;
    private final Map<UUID, BossBar> bars = new ConcurrentHashMap<>();
    private BukkitTask task;

    public NavigationBarTask(TradeWinds addon) {
        this.addon = addon;
    }

    public void start() {
        task = Bukkit.getScheduler().runTaskTimer(addon.getPlugin(), this, 20L, 20L);
    }

    public void stop() {
        if (task != null) {
            task.cancel();
        }
        bars.forEach((id, bar) -> {
            Player player = Bukkit.getPlayer(id);
            if (player != null) {
                player.hideBossBar(bar);
            }
        });
        bars.clear();
    }

    @Override
    public void run() {
        if (!addon.getSettings().isNavigationBossbar() || addon.getOverWorld() == null) {
            return;
        }
        for (Player player : Bukkit.getOnlinePlayers()) {
            if (!player.getWorld().equals(addon.getOverWorld())) {
                hide(player);
                continue;
            }
            GalaxyEngine engine = addon.getGalaxyEngine(addon.getOverWorld().getSeed());
            Optional<Reading> reading = reading(engine, player.getLocation().getBlockX(),
                    player.getLocation().getBlockZ(), addon.getSettings().getIslandDistance());
            if (reading.isEmpty()) {
                hide(player);
                continue;
            }
            show(player, reading.get());
        }
    }

    /**
     * What the bar should show at a position, if anything. Pure and testable.
     *
     * @param engine the galaxy
     * @param x player block x
     * @param z player block z
     * @param range island space radius
     * @return the reading, or empty in open ocean
     */
    static Optional<Reading> reading(GalaxyEngine engine, int x, int z, int range) {
        return engine.islandsNear(x, z, range).stream()
                .filter(spec -> spec.distanceSquared(x, z) <= (long) range * range)
                .findFirst()
                .map(spec -> {
                    DockPlan plan = engine.dockPlan(spec);
                    int pierX = spec.centerX() + (int) Math.round(Math.cos(plan.bearing()) * plan.dockEnd());
                    int pierZ = spec.centerZ() + (int) Math.round(Math.sin(plan.bearing()) * plan.dockEnd());
                    int dist = (int) Math.hypot((double) x - pierX, (double) z - pierZ);
                    float progress = (float) Math.clamp(1.0 - (double) dist / range, 0.0, 1.0);
                    return new Reading(spec, dist, progress);
                });
    }

    /**
     * One bar state: the island, distance to its pier, and bar fill.
     */
    record Reading(IslandSpec island, int dockDistance, float progress) {
    }

    private void show(Player player, Reading reading) {
        Component name = User.getInstance(player).getTranslationAsComponent("tradewinds.hud.navigation",
                "[name]", reading.island().name(),
                "[standing]", addon.getPlayerStanding(player.getUniqueId()),
                "[distance]", String.valueOf(reading.dockDistance()));
        BossBar bar = bars.computeIfAbsent(player.getUniqueId(),
                id -> BossBar.bossBar(name, reading.progress(), color(reading.island().band()),
                        BossBar.Overlay.PROGRESS));
        bar.name(name);
        bar.progress(reading.progress());
        bar.color(color(reading.island().band()));
        player.showBossBar(bar);
    }

    private void hide(Player player) {
        BossBar bar = bars.remove(player.getUniqueId());
        if (bar != null) {
            player.hideBossBar(bar);
        }
    }

    /**
     * Bar color tracks the band: calm blue in high-sec, red in lawless space.
     */
    static BossBar.Color color(SecurityBand band) {
        return switch (band) {
        case SAFE -> BossBar.Color.BLUE;
        case POLICED -> BossBar.Color.GREEN;
        case FRONTIER -> BossBar.Color.YELLOW;
        case LAWLESS -> BossBar.Color.RED;
        case ANARCHIC -> BossBar.Color.PURPLE;
        };
    }
}
