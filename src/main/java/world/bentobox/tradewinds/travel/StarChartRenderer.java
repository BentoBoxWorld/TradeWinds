package world.bentobox.tradewinds.travel;

import java.awt.Color;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

import org.bukkit.entity.Player;
import org.bukkit.map.MapCanvas;
import org.bukkit.map.MapCursor;
import org.bukkit.map.MapCursorCollection;
import org.bukkit.map.MapRenderer;
import org.bukkit.map.MapView;
import org.bukkit.map.MapPalette;
import org.bukkit.map.MinecraftFont;

import world.bentobox.tradewinds.TradeWinds;
import world.bentobox.tradewinds.ocean.OceanEngine;
import world.bentobox.tradewinds.ocean.SecurityBand;

/**
 * The Star Chart: a contextual map renderer that keeps the holder centered and
 * plots their charted islands - dots sized by the real island footprint,
 * names beside them, and islands beyond the map's reach pinned to the edge so
 * you learn which way the rest of the world lies. Scale is config
 * (blocks-per-pixel); redraws at most once a second per holder.
 *
 * @author tastybento
 */
public class StarChartRenderer extends MapRenderer {

    private static final Color OCEAN = new Color(12, 44, 84);
    private static final Color EDGE = new Color(120, 120, 120);
    /** The fuel-range ring: pale enough to read over, solid enough to see. */
    private static final Color RANGE_RING = new Color(120, 220, 235);
    /** Names are drawn white: anything darker vanishes against the ocean. */
    private static final Color NAME = new Color(255, 255, 255);
    /** Home is gold: the one dot on the chart that is YOURS. */
    private static final Color HOME = new Color(235, 190, 50);

    private final TradeWinds addon;
    private final Map<UUID, Long> lastDraw = new HashMap<>();
    private final Map<UUID, int[]> lastPos = new HashMap<>();

    public StarChartRenderer(TradeWinds addon) {
        super(true); // contextual: rendered per player
        this.addon = addon;
    }

    /**
     * How far this much fuel will carry a sailor, in blocks. The warp costs
     * fuel per block of route, so the reachable set really is a circle - which
     * is exactly the thing a chart can draw and a number cannot.
     *
     * @param fuelAboard fuel units in the hold
     * @param fuelPerBlock fuel cost per block of route
     * @return range in blocks, 0 if fuel buys nothing
     */
    static long fuelRangeBlocks(double fuelAboard, double fuelPerBlock) {
        if (fuelPerBlock <= 0 || fuelAboard <= 0) {
            return 0;
        }
        return (long) Math.floor(fuelAboard / fuelPerBlock);
    }

    /**
     * World offset to canvas pixel (center 64,64), or edge-clamped when out of
     * range. Pure and testable.
     *
     * @param dx block offset east of the holder
     * @param dz block offset south of the holder
     * @param blocksPerPixel map scale
     * @return {pixelX, pixelZ, onEdge(0/1)}
     */
    static int[] toPixel(long dx, long dz, int blocksPerPixel) {
        long px = dx / blocksPerPixel;
        long pz = dz / blocksPerPixel;
        boolean edge = Math.abs(px) > 60 || Math.abs(pz) > 60;
        if (edge) {
            double scale = 60.0 / Math.max(Math.abs(px), Math.abs(pz));
            px = Math.round(px * scale);
            pz = Math.round(pz * scale);
        }
        return new int[] { (int) (64 + px), (int) (64 + pz), edge ? 1 : 0 };
    }

    @Override
    public void render(MapView view, MapCanvas canvas, Player player) {
        if (addon.getOverWorld() == null || !player.getWorld().equals(addon.getOverWorld())) {
            return;
        }
        // Redraw once a second, or when the holder has moved a pixel's worth
        long now = System.currentTimeMillis();
        int bpp = Math.max(1, addon.getSettings().getStarChartBlocksPerPixel());
        int px = player.getLocation().getBlockX();
        int pz = player.getLocation().getBlockZ();
        int[] last = lastPos.get(player.getUniqueId());
        boolean moved = last == null || Math.abs(px - last[0]) >= bpp || Math.abs(pz - last[1]) >= bpp;
        if (!moved && now - lastDraw.getOrDefault(player.getUniqueId(), 0L) < 1000) {
            return;
        }
        lastDraw.put(player.getUniqueId(), now);
        lastPos.put(player.getUniqueId(), new int[] { px, pz });

        // Ocean background
        for (int x = 0; x < 128; x++) {
            for (int z = 0; z < 128; z++) {
                canvas.setPixelColor(x, z, OCEAN);
            }
        }
        // How far the fuel aboard will carry them - drawn before the islands so
        // island dots and names stay on top of it
        drawFuelRange(canvas, addon.getFuelService().holdFuel(player), bpp);
        // Charted islands
        OceanEngine engine = addon.getOceanEngine(addon.getOverWorld().getSeed());
        int islandPixelRadius = Math.max(1, engine.getConfig().terrainRadius() / bpp);
        drawChartedIslands(canvas, player, engine, islandPixelRadius, px, pz, bpp);
        // HOME: a member's claimed islet, in gold - drawn after the islands
        // so nothing sits on top of it (Stage 7b)
        drawHomeIsland(canvas, player, px, pz, bpp);
        // The holder: a cursor arrow at center, rotating with their facing
        drawPlayerCursor(canvas, player);
    }

    private void drawChartedIslands(MapCanvas canvas, Player player, OceanEngine engine, int islandPixelRadius,
            int px, int pz, int bpp) {
        for (String key : addon.getPlayerDataManager().get(player.getUniqueId()).getChartedIslands()) {
            String[] cell = key.split(",");
            engine.islandInCell(Integer.parseInt(cell[0]), Integer.parseInt(cell[1])).ifPresent(spec -> {
                int[] pixel = toPixel(spec.centerX() - (long) px, spec.centerZ() - (long) pz, bpp);
                if (pixel[2] == 1) {
                    canvas.setPixelColor(pixel[0], pixel[1], EDGE);
                    drawName(canvas, pixel[0], pixel[1], spec.name());
                } else {
                    Color color = bandColor(spec.band());
                    fillDot(canvas, pixel[0], pixel[1], islandPixelRadius, color);
                    drawName(canvas, pixel[0], pixel[1], spec.name());
                }
            });
        }
    }

    private void drawHomeIsland(MapCanvas canvas, Player player, int px, int pz, int bpp) {
        HomePort.islandOf(addon, player.getUniqueId()).ifPresent(island -> {
            int[] pixel = toPixel(island.getCenter().getBlockX() - (long) px,
                    island.getCenter().getBlockZ() - (long) pz, bpp);
            String name = island.getName() != null && !island.getName().isBlank() ? island.getName()
                    : world.bentobox.bentobox.api.user.User.getInstance(player)
                            .getTranslation("tradewinds.home.port-name");
            if (pixel[2] == 1) {
                canvas.setPixelColor(pixel[0], pixel[1], HOME);
                drawName(canvas, pixel[0], pixel[1], name);
            } else {
                fillDot(canvas, pixel[0], pixel[1],
                        Math.max(1, island.getProtectionRange() / bpp), HOME);
                drawName(canvas, pixel[0], pixel[1], name);
            }
        });
    }

    private void drawPlayerCursor(MapCanvas canvas, Player player) {
        MapCursorCollection cursors = new MapCursorCollection();
        byte direction = (byte) (Math.round(player.getLocation().getYaw() * 16.0 / 360.0) & 15);
        cursors.addCursor(new MapCursor((byte) 0, (byte) 0, direction, MapCursor.Type.PLAYER, true));
        canvas.setCursors(cursors);
    }

    /**
     * A dashed ring at the edge of the fuel's reach. Dashed so it reads as an
     * annotation rather than a wall, and skipped entirely when the range runs
     * off the chart - a ring you cannot see is just a lie about your range.
     */
    private void drawFuelRange(MapCanvas canvas, double fuelAboard, int bpp) {
        long range = fuelRangeBlocks(fuelAboard, addon.getSettings().getFuelPerBlock());
        int radius = (int) (range / bpp);
        if (radius < 4 || radius > 62) {
            return;
        }
        // Step by roughly one pixel of arc, and draw two thirds of each turn
        int steps = Math.max(48, radius * 6);
        for (int i = 0; i < steps; i++) {
            if (i % 3 == 2) {
                continue; // the gaps in the dashes
            }
            double angle = 2 * Math.PI * i / steps;
            int x = 64 + (int) Math.round(Math.cos(angle) * radius);
            int z = 64 + (int) Math.round(Math.sin(angle) * radius);
            if (x >= 0 && x < 128 && z >= 0 && z < 128) {
                canvas.setPixelColor(x, z, RANGE_RING);
            }
        }
    }

    private void fillDot(MapCanvas canvas, int cx, int cz, int radius, Color color) {
        for (int x = -radius; x <= radius; x++) {
            for (int z = -radius; z <= radius; z++) {
                if (x * x + z * z <= radius * radius) {
                    int pixelX = cx + x;
                    int pixelZ = cz + z;
                    if (pixelX >= 0 && pixelX < 128 && pixelZ >= 0 && pixelZ < 128) {
                        canvas.setPixelColor(pixelX, pixelZ, color);
                    }
                }
            }
        }
    }

    /**
     * Island names, in white. MapCanvas takes its text colour from a
     * "section-sign, palette index, semicolon" prefix; the default was a mid
     * grey that all but disappeared against the ocean.
     */
    private void drawName(MapCanvas canvas, int x, int z, String name) {
        int width = MinecraftFont.Font.getWidth(name);
        int textX = (int) Math.clamp((long) x - width / 2, 1L, 127L - width);
        int textZ = (int) Math.clamp((long) z + 3, 1L, 119L);
        canvas.drawText(textX, textZ, MinecraftFont.Font, colored(name));
    }

    /**
     * Prefix a string with the map-palette colour code for white.
     *
     * @param text the text
     * @return the text with a colour prefix
     */
    static String colored(String text) {
        return "\u00A7" + MapPalette.matchColor(NAME) + ";" + text;
    }

    static Color bandColor(SecurityBand band) {
        return switch (band) {
        case SAFE -> new Color(80, 180, 255);
        case POLICED -> new Color(90, 200, 90);
        case FRONTIER -> new Color(230, 200, 60);
        case LAWLESS -> new Color(220, 90, 60);
        case ANARCHIC -> new Color(190, 80, 220);
        };
    }
}
