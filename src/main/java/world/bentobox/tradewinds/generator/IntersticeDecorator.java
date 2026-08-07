package world.bentobox.tradewinds.generator;

import java.util.Random;

import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.block.BlockState;
import org.bukkit.block.Chest;
import org.bukkit.block.CreatureSpawner;
import org.bukkit.block.data.Ageable;
import org.bukkit.entity.EntityType;
import org.bukkit.generator.BlockPopulator;
import org.bukkit.generator.LimitedRegion;
import org.bukkit.generator.WorldInfo;
import org.bukkit.loot.LootTable;
import org.eclipse.jdt.annotation.NonNull;

import world.bentobox.tradewinds.TradeWinds;
import world.bentobox.tradewinds.galaxy.IntersticeMap;

/**
 * The interstice's furniture (tradewinds-interstice-plan.md). Braziers were
 * here first - the only light in the place; the plan grew them company:
 * <ul>
 * <li>Nether wart crops on the shoals, and crimson/warped groves on the
 * grand ones - the potion base, farmable in the dark.</li>
 * <li>Blaze pickets: some braziers carry a nether-brick crow's nest with a
 * blaze spawner - rods, fought from a boat.</li>
 * <li>Glowstone clusters hanging from the ceiling lid - light as navigation
 * and destination at once.</li>
 * <li>Quartz ore seeded through shoal cores and brazier roots.</li>
 * <li>Wither watchtowers: the interstice's only structure - wither skeleton
 * spawner below, loot chest above, skulls to a nether star to a beacon.</li>
 * </ul>
 * Geometry (where things are) comes from the pure {@link IntersticeMap};
 * this class only draws what the map declares.
 *
 * @author tastybento
 */
public class IntersticeDecorator extends BlockPopulator {

    /** How far a brazier can rise above the waterline. */
    private static final int MAX_RISE = 4;
    /** How far below the surface the outcrop is rooted. */
    private static final int ROOT_DEPTH = 3;
    /** Matches the generator's interstice lid thickness. */
    private static final int ROOF_THICKNESS = 4;
    /** Watchtower half-width: a 5x5 tower. */
    private static final int TOWER_HALF = 2;

    private final TradeWinds addon;

    public IntersticeDecorator(TradeWinds addon) {
        this.addon = addon;
    }

    @Override
    public void populate(@NonNull WorldInfo worldInfo, @NonNull Random random, int chunkX, int chunkZ,
            @NonNull LimitedRegion region) {
        int sea = addon.getSettings().getIntersticeSeaHeight();
        IntersticeMap map = addon.getIntersticeMap(worldInfo.getSeed());
        brazier(random, region, chunkX, chunkZ, sea);
        shoals(map, random, region, chunkX, chunkZ, sea);
        glowstoneCeiling(random, region, chunkX, chunkZ, worldInfo, sea);
        watchtower(map, region, chunkX, chunkZ, sea);
    }

    // ------------------------------------------------------------- braziers

    private void brazier(Random random, LimitedRegion region, int chunkX, int chunkZ, int sea) {
        double chance = addon.getSettings().getIntersticeBrazierChance();
        if (chance <= 0 || random.nextDouble() >= chance) {
            return;
        }
        int x = (chunkX << 4) + random.nextInt(16);
        int z = (chunkZ << 4) + random.nextInt(16);
        if (!region.isInRegion(x, sea, z) || region.getType(x, sea, z) != Material.WATER) {
            return;
        }
        int rise = 1 + random.nextInt(MAX_RISE);
        int top = sea + rise;
        // A squat outcrop: widest at the waterline, tapering as it rises, so it
        // reads as a rock rather than a pillar
        double quartz = addon.getSettings().getIntersticeQuartzChance();
        for (int y = sea - ROOT_DEPTH; y <= top; y++) {
            int spread = y <= sea ? 1 : 0;
            for (int dx = -spread; dx <= spread; dx++) {
                for (int dz = -spread; dz <= spread; dz++) {
                    if (region.isInRegion(x + dx, y, z + dz)) {
                        // Quartz lives in the submerged roots (plan source 4)
                        boolean ore = y < sea && random.nextDouble() < quartz;
                        region.setType(x + dx, y, z + dz,
                                ore ? Material.NETHER_QUARTZ_ORE : Material.NETHERRACK);
                    }
                }
            }
        }
        if (random.nextDouble() < addon.getSettings().getIntersticeBlazePicketChance()) {
            picket(region, x, top, z);
            return;
        }
        // The flame, and a little glow under the waterline so the rock is
        // visible from below as well as across the water
        if (region.isInRegion(x, top + 1, z)) {
            region.setType(x, top + 1, z, Material.FIRE);
        }
        if (random.nextBoolean() && region.isInRegion(x, sea - ROOT_DEPTH, z)) {
            region.setType(x, sea - ROOT_DEPTH, z, Material.GLOWSTONE);
        }
    }

    /**
     * A blaze picket: a nether-brick crow's nest on the brazier with a blaze
     * spawner burning where the fire would have been.
     */
    private void picket(LimitedRegion region, int x, int top, int z) {
        for (int dx = -1; dx <= 1; dx++) {
            for (int dz = -1; dz <= 1; dz++) {
                if (region.isInRegion(x + dx, top + 1, z + dz)) {
                    region.setType(x + dx, top + 1, z + dz, Material.NETHER_BRICKS);
                }
                if (dx != 0 && dz != 0 && region.isInRegion(x + dx, top + 2, z + dz)) {
                    region.setType(x + dx, top + 2, z + dz, Material.NETHER_BRICK_WALL);
                }
            }
        }
        spawner(region, x, top + 2, z, EntityType.BLAZE);
    }

    // --------------------------------------------------------------- shoals

    private void shoals(IntersticeMap map, Random random, LimitedRegion region, int chunkX, int chunkZ,
            int sea) {
        double wart = addon.getSettings().getIntersticeWartDensity();
        double quartz = addon.getSettings().getIntersticeQuartzChance();
        int minX = chunkX << 4;
        int minZ = chunkZ << 4;
        for (int dx = 0; dx < 16; dx++) {
            for (int dz = 0; dz < 16; dz++) {
                int x = minX + dx;
                int z = minZ + dz;
                var surface = map.shoalSurfaceAt(x, z, sea);
                if (surface.isEmpty()) {
                    continue;
                }
                int floorTop = surface.getAsInt();
                // Wart on the dry soul sand (plan source 1)
                if (floorTop > sea + 1 && wart > 0 && random.nextDouble() < wart
                        && region.isInRegion(x, floorTop, z)
                        && region.getType(x, floorTop - 1, z) == Material.SOUL_SAND
                        && region.getType(x, floorTop, z) == Material.AIR) {
                    Ageable crop = (Ageable) Material.NETHER_WART.createBlockData();
                    crop.setAge(random.nextInt(crop.getMaximumAge() + 1));
                    region.setBlockData(x, floorTop, z, crop);
                }
                // Quartz in the submerged core (plan source 4)
                for (int y = sea - 5; y < Math.min(floorTop - 2, sea); y++) {
                    if (quartz > 0 && random.nextDouble() < quartz && region.isInRegion(x, y, z)
                            && region.getType(x, y, z) == Material.NETHERRACK) {
                        region.setType(x, y, z, Material.NETHER_QUARTZ_ORE);
                    }
                }
            }
        }
        // The grand shoal's grove, drawn once, from the chunk holding its centre
        map.shoalsNear(minX + 8, minZ + 8, 8).stream()
                .filter(IntersticeMap.Shoal::grand)
                .filter(s -> s.centerX() >= minX && s.centerX() < minX + 16
                        && s.centerZ() >= minZ && s.centerZ() < minZ + 16)
                .forEach(s -> grove(map, random, region, s, sea));
    }

    /**
     * A grand shoal's fungus grove: a few hand-rolled crimson or warped
     * "trees" - stem, wart-block cap, shroomlights - the interstice's only
     * wood.
     */
    private void grove(IntersticeMap map, Random random, LimitedRegion region, IntersticeMap.Shoal shoal,
            int sea) {
        Material stem = shoal.crimson() ? Material.CRIMSON_STEM : Material.WARPED_STEM;
        Material cap = shoal.crimson() ? Material.NETHER_WART_BLOCK : Material.WARPED_WART_BLOCK;
        int trees = 2 + random.nextInt(3);
        for (int i = 0; i < trees; i++) {
            int fx = shoal.centerX() + random.nextInt(shoal.radius()) - shoal.radius() / 2;
            int fz = shoal.centerZ() + random.nextInt(shoal.radius()) - shoal.radius() / 2;
            var ground = map.shoalSurfaceAt(fx, fz, sea);
            if (ground.isEmpty() || ground.getAsInt() <= sea + 1) {
                continue; // roots want dry land
            }
            int base = ground.getAsInt();
            int height = 4 + random.nextInt(3);
            for (int y = 0; y < height; y++) {
                if (region.isInRegion(fx, base + y, fz)) {
                    region.setType(fx, base + y, fz, stem);
                }
            }
            int capY = base + height;
            for (int dx = -1; dx <= 1; dx++) {
                for (int dz = -1; dz <= 1; dz++) {
                    if (region.isInRegion(fx + dx, capY, fz + dz)) {
                        boolean light = dx != 0 && dz != 0 && random.nextInt(3) == 0;
                        region.setType(fx + dx, capY, fz + dz, light ? Material.SHROOMLIGHT : cap);
                    }
                }
            }
            if (region.isInRegion(fx, capY + 1, fz)) {
                region.setType(fx, capY + 1, fz, cap);
            }
        }
    }

    // -------------------------------------------------------------- ceiling

    private void glowstoneCeiling(Random random, LimitedRegion region, int chunkX, int chunkZ,
            WorldInfo worldInfo, int sea) {
        double chance = addon.getSettings().getIntersticeGlowstoneClusterChance();
        int ceiling = addon.getSettings().getIntersticeCeilingHeight();
        if (chance <= 0 || ceiling <= 0 || random.nextDouble() >= chance) {
            return;
        }
        // The lid's underside, as the generator builds it
        int roof = Math.min(worldInfo.getMaxHeight() - 1, sea + ceiling);
        int underside = Math.max(sea + 1, roof - ROOF_THICKNESS);
        int x = (chunkX << 4) + 2 + random.nextInt(12);
        int z = (chunkZ << 4) + 2 + random.nextInt(12);
        // A hanging blob: a patch in the lid itself and a drip below it
        for (int dx = -1; dx <= 1; dx++) {
            for (int dz = -1; dz <= 1; dz++) {
                if ((dx == 0 && dz == 0 || random.nextBoolean())
                        && region.isInRegion(x + dx, underside, z + dz)) {
                    region.setType(x + dx, underside, z + dz, Material.GLOWSTONE);
                }
            }
        }
        if (region.isInRegion(x, underside - 1, z)) {
            region.setType(x, underside - 1, z, Material.GLOWSTONE);
        }
        if (random.nextBoolean() && region.isInRegion(x, underside - 2, z)) {
            region.setType(x, underside - 2, z, Material.GLOWSTONE);
        }
    }

    // ---------------------------------------------------------- watchtowers

    private void watchtower(IntersticeMap map, LimitedRegion region, int chunkX, int chunkZ, int sea) {
        int minX = chunkX << 4;
        int minZ = chunkZ << 4;
        map.towersNear(minX + 8, minZ + 8, 16).stream()
                .filter(t -> t.centerX() >= minX && t.centerX() < minX + 16
                        && t.centerZ() >= minZ && t.centerZ() < minZ + 16)
                .forEach(t -> buildTower(region, t.centerX(), t.centerZ(), sea));
    }

    /**
     * The wither watchtower: solid nether-brick footing out of the sea, a
     * spawner room with a doorway at the waterline, a sealed loot floor
     * above, open crenellations on top. Skulls below, treasure above, and
     * every fight happens over open water.
     */
    private void buildTower(LimitedRegion region, int cx, int cz, int sea) {
        // Footing: solid to two above the waterline
        fill(region, cx, cz, sea - 16, sea + 2, Material.NETHER_BRICKS, false);
        // Spawner room, then the loot floor over it
        fill(region, cx, cz, sea + 3, sea + 7, Material.NETHER_BRICKS, true);
        fill(region, cx, cz, sea + 8, sea + 8, Material.NETHER_BRICKS, false);
        fill(region, cx, cz, sea + 9, sea + 11, Material.NETHER_BRICKS, true);
        // Doorway to the spawner room, at boat height, facing north
        for (int y = sea + 3; y <= sea + 4; y++) {
            if (region.isInRegion(cx, y, cz - TOWER_HALF)) {
                region.setType(cx, y, cz - TOWER_HALF, Material.AIR);
            }
        }
        // Window slits on the loot floor
        for (int y = sea + 9; y <= sea + 10; y++) {
            if (region.isInRegion(cx + TOWER_HALF, y, cz)) {
                region.setType(cx + TOWER_HALF, y, cz, Material.AIR);
            }
            if (region.isInRegion(cx - TOWER_HALF, y, cz)) {
                region.setType(cx - TOWER_HALF, y, cz, Material.AIR);
            }
        }
        // Crenellated open top
        for (int dx = -TOWER_HALF; dx <= TOWER_HALF; dx++) {
            for (int dz = -TOWER_HALF; dz <= TOWER_HALF; dz++) {
                boolean wall = Math.abs(dx) == TOWER_HALF || Math.abs(dz) == TOWER_HALF;
                boolean corner = Math.abs(dx) == TOWER_HALF && Math.abs(dz) == TOWER_HALF;
                if (wall && region.isInRegion(cx + dx, sea + 12, cz + dz)) {
                    region.setType(cx + dx, sea + 12, cz + dz,
                            corner ? Material.NETHER_BRICKS : Material.NETHER_BRICK_WALL);
                }
            }
        }
        // The tenant and the prize
        spawner(region, cx, sea + 4, cz, EntityType.WITHER_SKELETON);
        chest(region, cx, sea + 9, cz);
        // A soul-sand pile in the spawner room: the summoning is meant to be
        // POSSIBLE here, not convenient - skulls still have to be earned
        if (region.isInRegion(cx + 1, sea + 3, cz + 1)) {
            region.setType(cx + 1, sea + 3, cz + 1, Material.SOUL_SAND);
        }
        if (region.isInRegion(cx - 1, sea + 3, cz + 1)) {
            region.setType(cx - 1, sea + 3, cz + 1, Material.SOUL_SAND);
        }
    }

    /** A 5x5 slab or shell between two heights, inclusive. */
    private void fill(LimitedRegion region, int cx, int cz, int fromY, int toY, Material material,
            boolean hollow) {
        for (int y = fromY; y <= toY; y++) {
            for (int dx = -TOWER_HALF; dx <= TOWER_HALF; dx++) {
                for (int dz = -TOWER_HALF; dz <= TOWER_HALF; dz++) {
                    boolean wall = Math.abs(dx) == TOWER_HALF || Math.abs(dz) == TOWER_HALF;
                    if (!region.isInRegion(cx + dx, y, cz + dz)) {
                        continue;
                    }
                    region.setType(cx + dx, y, cz + dz, !hollow || wall ? material : Material.AIR);
                }
            }
        }
    }

    // ---------------------------------------------------------------- tiles

    private void spawner(LimitedRegion region, int x, int y, int z, EntityType type) {
        if (!region.isInRegion(x, y, z)) {
            return;
        }
        region.setType(x, y, z, Material.SPAWNER);
        BlockState state = region.getBlockState(x, y, z);
        if (state instanceof CreatureSpawner cs) {
            cs.setSpawnedType(type);
            cs.update();
        }
    }

    private void chest(LimitedRegion region, int x, int y, int z) {
        if (!region.isInRegion(x, y, z)) {
            return;
        }
        region.setType(x, y, z, Material.CHEST);
        NamespacedKey key = NamespacedKey.fromString(addon.getSettings().getIntersticeWatchtowerLootTable());
        LootTable loot = key == null ? null : Bukkit.getLootTable(key);
        if (loot == null) {
            addon.logError("interstice.watchtower-loot-table '"
                    + addon.getSettings().getIntersticeWatchtowerLootTable() + "' is not a loot table");
            return;
        }
        BlockState state = region.getBlockState(x, y, z);
        if (state instanceof Chest chestState) {
            chestState.setLootTable(loot);
            chestState.update();
        }
    }
}
