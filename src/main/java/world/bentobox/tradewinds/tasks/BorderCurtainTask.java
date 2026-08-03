package world.bentobox.tradewinds.tasks;

import org.bukkit.Bukkit;
import org.bukkit.Color;
import org.bukkit.Particle;
import org.bukkit.World;
import org.bukkit.entity.Player;
import org.bukkit.scheduler.BukkitTask;

import com.google.common.base.Enums;

import world.bentobox.bentobox.api.user.User;
import world.bentobox.tradewinds.TradeWinds;
import world.bentobox.tradewinds.galaxy.IslandSpec;

/**
 * Makes the invisible lines visible: every trading island has two boundaries
 * a sailor needs to SEE - the warp-offer ring at the protection edge (where
 * the dialog fires, red by default) and the edge of island space itself
 * (where scans and island rules begin, blue by default). Each renders as a
 * curtain of dust on the arc nearest the player, per player, only when they
 * are close enough for it to matter.
 * <p>
 * Rendering follows the Border addon's proven path exactly
 * ({@code User.spawnParticle}, which resolves DUST across versions, validates
 * the dust options and applies the server's view-distance check) and hangs
 * the curtain around the <b>player's own Y</b> rather than an assumed sea
 * level - a boat sits above the waterline, and the first cut drew the wall at
 * a height that could be behind the horizon of what the client renders.
 * <p>
 * The border stays fully passable: this is paint, not a wall.
 *
 * @author tastybento
 */
public class BorderCurtainTask {

    /** Ticks between redraws - dust lives ~1s, so this keeps it steady. */
    private static final long PERIOD_TICKS = 10L;
    /** Block spacing between particles along the arc. */
    private static final double ARC_STEP = 1.5;
    /** Curtain heights relative to the player's feet. */
    private static final double[] CURTAIN_YS = { -1.0, 0.5, 2.0, 3.5 };

    /** DUST on modern servers, REDSTONE on older, FLAME as a last resort. */
    private static final Particle PARTICLE = Enums.getIfPresent(Particle.class, "DUST")
            .or(Enums.getIfPresent(Particle.class, "REDSTONE").or(Particle.FLAME));

    private final TradeWinds addon;
    private BukkitTask task;

    public BorderCurtainTask(TradeWinds addon) {
        this.addon = addon;
    }

    public void start() {
        task = Bukkit.getScheduler().runTaskTimer(addon.getPlugin(), this::tick, PERIOD_TICKS, PERIOD_TICKS);
        addon.log("Border curtains: " + (addon.getSettings().isBorderParticlesEnabled() ? "on" : "OFF (border.particles-enabled)")
                + ", warp ring " + addon.getSettings().getIslandProtectionRange() + " blocks, island edge "
                + addon.getSettings().getIslandDistance() + " blocks");
    }

    public void stop() {
        if (task != null) {
            task.cancel();
        }
    }

    private void tick() {
        if (!addon.getSettings().isBorderParticlesEnabled()) {
            return;
        }
        World world = addon.getOverWorld();
        if (world == null) {
            return;
        }
        Color warpColor = parseColor(addon.getSettings().getWarpRingColor(), Color.fromRGB(255, 64, 64));
        Color edgeColor = parseColor(addon.getSettings().getEdgeRingColor(), Color.fromRGB(64, 128, 255));
        int view = addon.getSettings().getBorderViewDistance();
        int warpRing = addon.getSettings().getIslandProtectionRange();
        int edgeRing = addon.getSettings().getIslandDistance();
        for (Player player : world.getPlayers()) {
            User user = User.getInstance(player);
            for (IslandSpec spec : addon.getGalaxyEngine(world.getSeed()).islandsNear(
                    player.getLocation().getBlockX(), player.getLocation().getBlockZ(), edgeRing + view)) {
                drawRing(user, player, spec, warpRing, view, warpColor);
                drawRing(user, player, spec, edgeRing, view, edgeColor);
            }
        }
    }

    /**
     * Draw the arc of one ring nearest the player, if they are within view of
     * it: a dust curtain a few blocks tall, hung around their own height.
     */
    private void drawRing(User user, Player player, IslandSpec spec, int radius, int view, Color color) {
        double px = player.getLocation().getX();
        double pz = player.getLocation().getZ();
        double py = player.getLocation().getY();
        double distance = Math.hypot(px - spec.centerX(), pz - spec.centerZ());
        if (Math.abs(distance - radius) > view || radius <= 0) {
            return;
        }
        Particle.DustOptions dust = new Particle.DustOptions(color, 2.0f);
        double bearing = Math.atan2(pz - spec.centerZ(), px - spec.centerX());
        // The arc spanning ~view blocks either side of the player's bearing
        double halfArc = (double) view / radius;
        double step = ARC_STEP / radius;
        for (double theta = bearing - halfArc; theta <= bearing + halfArc; theta += step) {
            double x = spec.centerX() + radius * Math.cos(theta);
            double z = spec.centerZ() + radius * Math.sin(theta);
            for (double dy : CURTAIN_YS) {
                user.spawnParticle(PARTICLE, dust, x, py + dy, z);
            }
        }
    }

    /**
     * Parse a config color: "r,g,b" (0-255 each), falling back rather than
     * failing - a typo must not strip the border off the sea.
     *
     * @param raw the config string
     * @param fallback the default
     * @return the color
     */
    static Color parseColor(String raw, Color fallback) {
        if (raw == null || raw.isBlank()) {
            return fallback;
        }
        String[] parts = raw.split(",");
        if (parts.length != 3) {
            return fallback;
        }
        try {
            int r = Math.clamp(Integer.parseInt(parts[0].trim()), 0, 255);
            int g = Math.clamp(Integer.parseInt(parts[1].trim()), 0, 255);
            int b = Math.clamp(Integer.parseInt(parts[2].trim()), 0, 255);
            return Color.fromRGB(r, g, b);
        } catch (NumberFormatException e) {
            return fallback;
        }
    }
}
