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
import world.bentobox.tradewinds.ocean.DockPlan;
import world.bentobox.tradewinds.ocean.OceanEngine;
import world.bentobox.tradewinds.ocean.IslandSpec;
import world.bentobox.tradewinds.ocean.SecurityBand;

/**
 * The navigation boss bar: while in an island's waters it shows the island
 * name, its <b>security band</b>, the player's standing, and the distance to
 * the dock - steer toward the dock and watch the number fall. Bar color tracks
 * the band too, and the bar fills as you close on the pier.
 * <p>
 * The band is spelled out because the bar colour alone was not enough in play:
 * "I arrived in an anarchy and I couldn't remember what kind of island it was,
 * and couldn't work out how to tell." Whether the law is watching is the single
 * most consequential fact about where you are.
 *
 * @author tastybento
 */
public class NavigationBarTask implements Runnable {

    private final TradeWinds addon;
    private final Map<UUID, BossBar> bars = new ConcurrentHashMap<>();
    /** Players with the course-home bar on (/tw go at sea toggles it). */
    private final java.util.Set<UUID> course = ConcurrentHashMap.newKeySet();
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
            OceanEngine engine = addon.getOceanEngine(addon.getOverWorld().getSeed());
            Optional<Reading> reading = reading(engine, player.getLocation().getBlockX(),
                    player.getLocation().getBlockZ(), addon.getSettings().getIslandDistance());
            if (reading.isPresent()) {
                show(player, reading.get());
                continue;
            }
            if (course.contains(player.getUniqueId()) && showCourse(player)) {
                continue;
            }
            hide(player);
        }
    }

    /**
     * Toggle the course-home bar for a player.
     *
     * @param player the player
     * @return true if the course is now ON
     */
    public boolean toggleCourse(Player player) {
        if (!course.remove(player.getUniqueId())) {
            course.add(player.getUniqueId());
            return true;
        }
        hide(player);
        return false;
    }

    /**
     * The course-home bar: read the sky, follow the number (Stage 7b). By
     * day the sun gives only the QUARTER the wind should sit on - an
     * eight-point direction; by night the stars give the exact distance too,
     * so waiting for dark is rewarded, never required. Arriving in your own
     * island's waters clears the course by itself.
     *
     * @return true if a course bar was shown
     */
    private boolean showCourse(Player player) {
        var island = world.bentobox.tradewinds.travel.HomePort.islandOf(addon, player.getUniqueId())
                .orElse(null);
        if (island == null) {
            course.remove(player.getUniqueId());
            return false;
        }
        int px = player.getLocation().getBlockX();
        int pz = player.getLocation().getBlockZ();
        int dx = island.getCenter().getBlockX() - px;
        int dz = island.getCenter().getBlockZ() - pz;
        double dist = Math.hypot(dx, dz);
        if (dist <= island.getProtectionRange()) {
            // Landfall: the course has done its job
            course.remove(player.getUniqueId());
            User.getInstance(player).sendMessage("tradewinds.home.course-arrived");
            return false;
        }
        User user = User.getInstance(player);
        long time = player.getWorld().getTime();
        boolean night = time >= 12542 && time <= 23460;
        String direction = user.getTranslation(
                world.bentobox.tradewinds.commands.TWSpawnCommand.compassKey(dx, dz));
        Component name = night
                ? user.getTranslationAsComponent("tradewinds.hud.course-night", "[direction]", direction,
                        "[distance]", String.valueOf(Math.round(dist)))
                : user.getTranslationAsComponent("tradewinds.hud.course-day", "[direction]", direction);
        float progress = night
                ? (float) Math.clamp(1.0 - dist / addon.getSettings().getCourseBarScale(), 0.0, 1.0)
                : 1.0f;
        BossBar bar = bars.computeIfAbsent(player.getUniqueId(),
                id -> BossBar.bossBar(name, progress, BossBar.Color.WHITE, BossBar.Overlay.PROGRESS));
        bar.name(name);
        bar.progress(progress);
        bar.color(night ? BossBar.Color.BLUE : BossBar.Color.WHITE);
        player.showBossBar(bar);
        return true;
    }

    /**
     * What the bar should show at a position, if anything. Pure and testable.
     *
     * @param engine the ocean
     * @param x player block x
     * @param z player block z
     * @param range island space radius
     * @return the reading, or empty in open ocean
     */
    static Optional<Reading> reading(OceanEngine engine, int x, int z, int range) {
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
        User user = User.getInstance(player);
        Component name = user.getTranslationAsComponent("tradewinds.hud.navigation",
                "[name]", reading.island().name(),
                "[tech]", String.valueOf(reading.island().techLevel()),
                "[band]", user.getTranslation(reading.island().band().getLocaleKey()),
                "[standing]", addon.getPlayerStanding(user, player.getUniqueId()),
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
