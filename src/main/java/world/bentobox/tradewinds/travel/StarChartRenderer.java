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
import org.bukkit.map.MinecraftFont;

import world.bentobox.tradewinds.TradeWinds;
import world.bentobox.tradewinds.galaxy.GalaxyEngine;
import world.bentobox.tradewinds.galaxy.IslandSpec;
import world.bentobox.tradewinds.galaxy.SecurityBand;

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

    private final TradeWinds addon;
    private final Map<UUID, Long> lastDraw = new HashMap<>();
    private final Map<UUID, int[]> lastPos = new HashMap<>();

    public StarChartRenderer(TradeWinds addon) {
        super(true); // contextual: rendered per player
        this.addon = addon;
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
        // Charted islands
        GalaxyEngine engine = addon.getGalaxyEngine(addon.getOverWorld().getSeed());
        int islandPixelRadius = Math.max(1, engine.getConfig().terrainRadius() / bpp);
        for (String key : addon.getPlayerDataManager().get(player.getUniqueId()).getChartedIslands()) {
            String[] cell = key.split(",");
            engine.islandInCell(Integer.parseInt(cell[0]), Integer.parseInt(cell[1])).ifPresent(spec -> {
                int[] pixel = toPixel(spec.centerX() - (long) px, spec.centerZ() - (long) pz, bpp);
                if (pixel[2] == 1) {
                    // Beyond the chart: pin the name to the edge as a heading hint
                    canvas.setPixelColor(pixel[0], pixel[1], EDGE);
                    drawName(canvas, pixel[0], pixel[1], spec.name());
                } else {
                    Color color = bandColor(spec.band());
                    fillDot(canvas, pixel[0], pixel[1], islandPixelRadius, color);
                    drawName(canvas, pixel[0], pixel[1], spec.name());
                }
            });
        }
        // The holder: a cursor arrow at center, rotating with their facing
        MapCursorCollection cursors = new MapCursorCollection();
        byte direction = (byte) (Math.round(player.getLocation().getYaw() * 16.0 / 360.0) & 15);
        cursors.addCursor(new MapCursor((byte) 0, (byte) 0, direction, MapCursor.Type.PLAYER, true));
        canvas.setCursors(cursors);
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

    private void drawName(MapCanvas canvas, int x, int z, String name) {
        int width = MinecraftFont.Font.getWidth(name);
        int textX = Math.clamp(x - width / 2, 1, 127 - width);
        int textZ = Math.clamp(z + 3, 1, 119);
        canvas.drawText(textX, textZ, MinecraftFont.Font, name);
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
