package world.bentobox.tradewinds.generator;

import java.util.Random;

import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.block.BlockState;
import org.bukkit.block.Chest;
import org.bukkit.block.CreatureSpawner;
import org.bukkit.block.data.Ageable;
import org.bukkit.block.structure.StructureRotation;
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
 * <li>Wither watchtowers: wither skeleton spawner below, loot chest above,
 * skulls to a nether star to a beacon.</li>
 * <li>The ship graveyard: seeded vanilla shipwrecks on the seabed, chests
 * re-pointed at nether-tier loot by wreck grade.</li>
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
        wrecks(map, random, region, chunkX, chunkZ, worldInfo, sea);
        seafloor(random, region, chunkX, chunkZ, worldInfo, sea);
    }

    // ------------------------------------------------------------- seafloor

    /**
     * Bottom life: glow lichen beds, basalt spikes, magma vents, soul-sand
     * seeps and blackstone boulders scattered on the seabed. None of it is a
     * resource - it is what makes looking down through the water worth doing
     * ("the floor does seem too flat, especially because nothing is growing
     * on it" - playtest 2026-08-07).
     */
    private void seafloor(Random random, LimitedRegion region, int chunkX, int chunkZ, WorldInfo worldInfo,
            int sea) {
        double chance = addon.getSettings().getIntersticeSeafloorClutter();
        if (chance <= 0) {
            return;
        }
        for (int dx = 0; dx < 16; dx++) {
            for (int dz = 0; dz < 16; dz++) {
                if (random.nextDouble() < chance) {
                    int x = (chunkX << 4) + dx;
                    int z = (chunkZ << 4) + dz;
                    decorateSeabedSpot(random, region, x, z, sea, worldInfo.getMinHeight());
                }
            }
        }
    }

    private void decorateSeabedSpot(Random random, LimitedRegion region, int x, int z, int sea, int minHeight) {
        int floor = seabedAt(region, x, z, sea - 2, minHeight);
        if (floor <= minHeight) {
            return; // dry land, a shoal crown, or nothing at all
        }
        double roll = random.nextDouble();
        if (roll < 0.40) {
            lichenBed(random, region, x, floor, z);
        } else if (roll < 0.65) {
            // A basalt spike, 2-5 tall, always fully submerged
            int height = Math.min(2 + random.nextInt(4), sea - 1 - floor);
            for (int y = 1; y <= height; y++) {
                if (region.isInRegion(x, floor + y, z)) {
                    region.setType(x, floor + y, z, Material.BASALT);
                }
            }
        } else if (roll < 0.80) {
            // A magma vent glowing up through the water
            patch(random, region, x, floor, z, Material.MAGMA_BLOCK);
        } else if (roll < 0.90) {
            // A soul-sand seep
            patch(random, region, x, floor, z, Material.SOUL_SAND);
        } else {
            // A blackstone boulder
            if (region.isInRegion(x, floor + 1, z)) {
                region.setType(x, floor + 1, z, Material.BLACKSTONE);
            }
            patch(random, region, x, floor, z, Material.BLACKSTONE);
        }
    }

    /** Swap the floor block and a couple of neighbours to a material. */
    private void patch(Random random, LimitedRegion region, int x, int floor, int z, Material material) {
        region.setType(x, floor, z, material);
        for (int i = 0; i < 2; i++) {
            int nx = x + random.nextInt(3) - 1;
            int nz = z + random.nextInt(3) - 1;
            if (region.isInRegion(nx, floor, nz) && region.getType(nx, floor, nz) != Material.WATER) {
                region.setType(nx, floor, nz, material);
            }
        }
    }

    /** A patch of waterlogged glow lichen lying on the seabed. */
    private void lichenBed(Random random, LimitedRegion region, int x, int floor, int z) {
        for (int dx = -1; dx <= 1; dx++) {
            for (int dz = -1; dz <= 1; dz++) {
                if ((dx == 0 && dz == 0 || random.nextBoolean())
                        && region.isInRegion(x + dx, floor + 1, z + dz)
                        && region.getType(x + dx, floor + 1, z + dz) == Material.WATER
                        && region.getType(x + dx, floor, z + dz) != Material.WATER) {
                    org.bukkit.block.data.type.GlowLichen lichen =
                            (org.bukkit.block.data.type.GlowLichen) Material.GLOW_LICHEN.createBlockData();
                    lichen.setFace(org.bukkit.block.BlockFace.DOWN, true);
                    lichen.setWaterlogged(true);
                    region.setBlockData(x + dx, floor + 1, z + dz, lichen);
                }
            }
        }
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
        buildBrazierOutcrop(random, region, x, z, sea, top);
        if (random.nextDouble() < addon.getSettings().getIntersticeBlazePicketChance()) {
            picket(region, x, top, z);
            return;
        }
        decorateBrazierTop(random, region, x, top, z, sea);
    }

    private void buildBrazierOutcrop(Random random, LimitedRegion region, int x, int z, int sea, int top) {
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
    }

    private void decorateBrazierTop(Random random, LimitedRegion region, int x, int top, int z, int sea) {
        // The flame, and a little glow under the waterline so the rock is
        // visible from below as well as across the water
        if (region.isInRegion(x, top + 1, z)) {
            region.setType(x, top + 1, z, Material.FIRE);
        }
        if (random.nextBoolean() && region.isInRegion(x, sea - ROOT_DEPTH, z)) {
            region.setType(x, sea - ROOT_DEPTH, z, Material.GLOWSTONE);
        }
    }

    /** Blaze deck half-width: spawners place mobs up to 4 blocks out, so the
     * deck must cover the full spawn radius or the blazes land in the sea
     * and fizzle on the spot (playtest 2026-08-07 - the 3x3 crow's nest
     * drowned every blaze it ever spawned). */
    private static final int DECK_HALF = 4;

    /**
     * A blaze picket: a nether-brick platform on the brazier, wide enough
     * that everything the spawner produces lands on dry deck, with a wall
     * ring and legs down to the waterline.
     */
    private void picket(LimitedRegion region, int x, int top, int z) {
        for (int dx = -DECK_HALF; dx <= DECK_HALF; dx++) {
            for (int dz = -DECK_HALF; dz <= DECK_HALF; dz++) {
                picketBlock(region, x, top, z, dx, dz);
            }
        }
        spawner(region, x, top + 2, z, EntityType.BLAZE);
    }

    private void picketBlock(LimitedRegion region, int x, int top, int z, int dx, int dz) {
        if (region.isInRegion(x + dx, top + 1, z + dz)) {
            region.setType(x + dx, top + 1, z + dz, Material.NETHER_BRICKS);
        }
        boolean edge = Math.abs(dx) == DECK_HALF || Math.abs(dz) == DECK_HALF;
        boolean corner = Math.abs(dx) == DECK_HALF && Math.abs(dz) == DECK_HALF;
        if (edge && region.isInRegion(x + dx, top + 2, z + dz)) {
            region.setType(x + dx, top + 2, z + dz,
                    corner ? Material.NETHER_BRICKS : Material.NETHER_BRICK_WALL);
        }
        // Corner legs down into the water: a stilt fort, not a UFO
        if (corner) {
            for (int y = top; y > top - 6 && region.isInRegion(x + dx, y, z + dz); y--) {
                Material below = region.getType(x + dx, y, z + dz);
                if (below != Material.WATER && below != Material.AIR) {
                    break; // found footing
                }
                region.setType(x + dx, y, z + dz, Material.NETHER_BRICK_WALL);
            }
        }
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
                decorateShoalColumn(map, random, region, minX, minZ, dx, dz, wart, quartz, sea);
            }
        }
        decorateGrandShoals(map, random, region, minX, minZ, sea);
    }

    private void decorateShoalColumn(IntersticeMap map, Random random, LimitedRegion region, int minX, int minZ,
            int dx, int dz, double wart, double quartz, int sea) {
        int x = minX + dx;
        int z = minZ + dz;
        var surface = map.shoalSurfaceAt(x, z, sea);
        if (surface.isEmpty()) {
            return;
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

    private void decorateGrandShoals(IntersticeMap map, Random random, LimitedRegion region, int minX, int minZ,
            int sea) {
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
            grovePlant(map, random, region, shoal, sea, stem, cap);
        }
    }

    private void grovePlant(IntersticeMap map, Random random, LimitedRegion region, IntersticeMap.Shoal shoal,
            int sea, Material stem, Material cap) {
        int fx = shoal.centerX() + random.nextInt(shoal.radius()) - shoal.radius() / 2;
        int fz = shoal.centerZ() + random.nextInt(shoal.radius()) - shoal.radius() / 2;
        var ground = map.shoalSurfaceAt(fx, fz, sea);
        if (ground.isEmpty() || ground.getAsInt() <= sea + 1) {
            return; // roots want dry land
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
        buildTowerOpenings(region, cx, cz, sea);
        buildTowerCrenellations(region, cx, cz, sea);
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

    private void buildTowerOpenings(LimitedRegion region, int cx, int cz, int sea) {
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
    }

    private void buildTowerCrenellations(LimitedRegion region, int cx, int cz, int sea) {
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
    }

    /** A 5x5 slab or shell between two heights, inclusive. */
    private void fill(LimitedRegion region, int cx, int cz, int fromY, int toY, Material material,
            boolean hollow) {
        for (int y = fromY; y <= toY; y++) {
            fillLayer(region, cx, cz, y, material, hollow);
        }
    }

    private void fillLayer(LimitedRegion region, int cx, int cz, int y, Material material, boolean hollow) {
        for (int dx = -TOWER_HALF; dx <= TOWER_HALF; dx++) {
            for (int dz = -TOWER_HALF; dz <= TOWER_HALF; dz++) {
                if (!region.isInRegion(cx + dx, y, cz + dz)) {
                    continue;
                }
                boolean wall = Math.abs(dx) == TOWER_HALF || Math.abs(dz) == TOWER_HALF;
                region.setType(cx + dx, y, cz + dz, !hollow || wall ? material : Material.AIR);
            }
        }
    }

    // --------------------------------------------------------------- wrecks

    /**
     * The ship graveyard: hulls that misjumped and never re-engaged, lying
     * where the interstice keeps them. Each wreck is a vanilla shipwreck
     * template (config-swappable), seeded for position, variant, rotation and
     * burial by the pure map. Drawn once, from the chunk holding its centre -
     * templates are at most 28 blocks long, so a centre-anchored wreck always
     * fits inside this chunk plus the populator's buffer.
     */
    private void wrecks(IntersticeMap map, Random random, LimitedRegion region, int chunkX, int chunkZ,
            WorldInfo worldInfo, int sea) {
        java.util.List<String> templates = addon.getSettings().getIntersticeWreckTemplates();
        if (templates.isEmpty()) {
            return;
        }
        int minX = chunkX << 4;
        int minZ = chunkZ << 4;
        map.wrecksNear(minX + 8, minZ + 8, 16).stream()
                .filter(w -> w.centerX() >= minX && w.centerX() < minX + 16
                        && w.centerZ() >= minZ && w.centerZ() < minZ + 16)
                .forEach(w -> placeWreck(region, random, worldInfo, sea, w,
                        templates.get(w.variant() % templates.size())));
    }

    private void placeWreck(LimitedRegion region, Random random, WorldInfo worldInfo, int sea,
            IntersticeMap.Wreck wreck, String templateKey) {
        NamespacedKey key = NamespacedKey.fromString(templateKey);
        org.bukkit.structure.Structure template = key == null ? null
                : Bukkit.getStructureManager().loadStructure(key);
        if (template == null) {
            addon.logError("interstice.wreck-templates: '" + templateKey + "' is not a structure");
            return;
        }
        // Keel on the seabed (minus the seeded burial), found at the centre
        int floor = seabedAt(region, wreck.centerX(), wreck.centerZ(), sea, worldInfo.getMinHeight());
        if (floor <= worldInfo.getMinHeight()) {
            return; // no seabed here (should not happen; be safe)
        }
        org.bukkit.util.BlockVector size = template.getSize();
        int sx = size.getBlockX();
        int sz = size.getBlockZ();
        // Keel settled one block into the reef crest: the mound (generator)
        // already set the height so the hull rides half out of the water -
        // wreck.sink() shaped the CREST, so it is not applied again here
        int y = Math.max(worldInfo.getMinHeight() + 1, floor);
        StructureRotation rotation = switch (wreck.rotation()) {
            case 1 -> StructureRotation.CLOCKWISE_90;
            case 2 -> StructureRotation.CLOCKWISE_180;
            case 3 -> StructureRotation.COUNTERCLOCKWISE_90;
            default -> StructureRotation.NONE;
        };
        // place() rotates around the origin block, so the origin that CENTRES
        // the hull on the wreck point depends on the rotation. Centring is
        // what keeps every block inside this chunk plus the buffer.
        org.bukkit.util.BlockVector origin = switch (rotation) {
            case CLOCKWISE_90 -> new org.bukkit.util.BlockVector(wreck.centerX() + (sz - 1) / 2, y,
                    wreck.centerZ() - (sx - 1) / 2);
            case CLOCKWISE_180 -> new org.bukkit.util.BlockVector(wreck.centerX() + (sx - 1) / 2, y,
                    wreck.centerZ() + (sz - 1) / 2);
            case COUNTERCLOCKWISE_90 -> new org.bukkit.util.BlockVector(wreck.centerX() - (sz - 1) / 2, y,
                    wreck.centerZ() + (sx - 1) / 2);
            default -> new org.bukkit.util.BlockVector(wreck.centerX() - (sx - 1) / 2, y,
                    wreck.centerZ() - (sz - 1) / 2);
        };
        try {
            template.place(region, origin, false, rotation, org.bukkit.block.structure.Mirror.NONE, -1, 1.0f,
                    random);
        } catch (Exception e) {
            addon.logError("Wreck " + templateKey + " at " + wreck.centerX() + "," + wreck.centerZ()
                    + " failed to place: " + e.getMessage());
            return;
        }
        wreckLoot(region, wreck, size, y);
        graveLight(region, wreck, sea);
    }

    /**
     * A soul flame over every wreck - the grave candle. On a hull riding
     * above the water it burns on the highest timber; on a sunken one a
     * charred basalt mast carries it up past the surface. At night the
     * graveyard is a field of cold blue lights.
     */
    private void graveLight(LimitedRegion region, IntersticeMap.Wreck wreck, int sea) {
        if (!addon.getSettings().isIntersticeWreckFlames()) {
            return;
        }
        int x = wreck.centerX();
        int z = wreck.centerZ();
        // The hull's highest timber at the centre column
        int top = findWreckTop(region, x, z, sea);
        if (top == Integer.MIN_VALUE) {
            return; // nothing under the centre at all
        }
        if (top < sea) {
            // Sunken: a mast up out of the water to carry the flame
            for (int y = top + 1; y <= sea; y++) {
                if (region.isInRegion(x, y, z)) {
                    region.setType(x, y, z, Material.BASALT);
                }
            }
            top = sea;
        }
        graveCandleFlame(region, x, top, z);
    }

    private int findWreckTop(LimitedRegion region, int x, int z, int sea) {
        for (int y = sea + 24; y >= sea - 24; y--) {
            if (!region.isInRegion(x, y, z)) {
                return Integer.MIN_VALUE;
            }
            Material here = region.getType(x, y, z);
            if (here != Material.WATER && here != Material.AIR) {
                return y;
            }
        }
        return Integer.MIN_VALUE;
    }

    private void graveCandleFlame(LimitedRegion region, int x, int top, int z) {
        if (region.isInRegion(x, top + 1, z)) {
            region.setType(x, top + 1, z, Material.SOUL_SAND);
        }
        if (region.isInRegion(x, top + 2, z)) {
            region.setType(x, top + 2, z, Material.SOUL_FIRE);
        }
    }

    /** The first solid column top at or under the sea surface. */
    private int seabedAt(LimitedRegion region, int x, int z, int sea, int minHeight) {
        for (int y = sea; y > minHeight; y--) {
            if (!region.isInRegion(x, y, z)) {
                return minHeight;
            }
            Material type = region.getType(x, y, z);
            if (type != Material.WATER && type != Material.AIR) {
                return y;
            }
        }
        return minHeight;
    }

    /**
     * Settle every chest the template shipped. Most wrecks are SCENERY
     * (ruled 2026-08-07: the graveyard is there to see, not to farm) - their
     * chests are emptied, because a template chest left alone rolls
     * vanilla's shipwreck tables, buried-treasure map included. On a loot
     * wreck the first chest carries the wreck's grade; on a TREASURE wreck
     * any further chest is the captain's locker (piglin-bartering goods).
     */
    private void wreckLoot(LimitedRegion region, IntersticeMap.Wreck wreck,
            org.bukkit.util.BlockVector size, int keelY) {
        // The rotated hull lies somewhere inside this box around the centre
        int reach = Math.max(size.getBlockX(), size.getBlockZ()) / 2 + 1;
        ChestCounter counter = new ChestCounter();
        for (int y = keelY; y <= keelY + size.getBlockY(); y++) {
            for (int x = wreck.centerX() - reach; x <= wreck.centerX() + reach; x++) {
                for (int z = wreck.centerZ() - reach; z <= wreck.centerZ() + reach; z++) {
                    settleWreckChest(region, wreck, x, y, z, counter);
                }
            }
        }
    }

    private static class ChestCounter {
        int count;
    }

    private void settleWreckChest(LimitedRegion region, IntersticeMap.Wreck wreck, int x, int y, int z,
            ChestCounter counter) {
        if (!region.isInRegion(x, y, z)) {
            return;
        }
        Material type = region.getType(x, y, z);
        if (type != Material.CHEST && type != Material.TRAPPED_CHEST) {
            return;
        }
        LootTable loot = getLootTableForWreck(wreck, counter.count);
        counter.count++;
        BlockState state = region.getBlockState(x, y, z);
        if (state instanceof Chest chestState) {
            // null clears the template's vanilla table: an empty
            // sea chest, not a broken treasure map
            chestState.setLootTable(loot);
            chestState.update();
        }
    }

    private LootTable getLootTableForWreck(IntersticeMap.Wreck wreck, int chestIndex) {
        if (!wreck.loot()) {
            return null;
        }
        String gradeKey = chestIndex > 0 && wreck.grade() == IntersticeMap.WreckGrade.TREASURE ? "LOCKER"
                : wreck.grade().name();
        String tableName = addon.getSettings().getIntersticeWreckLoot().get(gradeKey);
        NamespacedKey tableKey = tableName == null ? null : NamespacedKey.fromString(tableName);
        LootTable loot = tableKey == null ? null : Bukkit.getLootTable(tableKey);
        if (loot == null) {
            addon.logError("interstice.wreck-loot." + gradeKey + " '" + tableName + "' is not a loot table");
        }
        return loot;
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
