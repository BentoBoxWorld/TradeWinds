package world.bentobox.tradewinds.galaxy;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;

/**
 * The seeded galaxy: which cells host trading islands, where exactly, and what
 * each island is (type, security band, biome, name). Every answer is a pure
 * function of (seed, cell) - same seed, same galaxy, on any server (spec
 * principle 5). No Bukkit imports; results are cached but cache state never
 * affects outcomes.
 * <p>
 * Placement scheme: the ocean is a grid of cells of side 2 x min-separation.
 * Each cell rolls occupancy against the density, and an occupied cell places
 * its island at the cell's nominal center plus a jitter of at most
 * min-separation / 2 in each axis. Any two islands are therefore at least
 * min-separation apart by construction - deterministic, order-independent, and
 * no rejection loop.
 * <p>
 * Density floor: the {@code starterMinIslands} cells nearest spawn are forced
 * occupied (and SAFE) regardless of their density roll, so every seed has a
 * playable starter cluster.
 *
 * @author tastybento
 */
public class GalaxyEngine {

    // Salts for the independent per-cell attribute rolls
    private static final long SALT_OCCUPANCY = 0x0CCA9A9CL;
    private static final long SALT_JITTER_X = 0x7177E12AL;
    private static final long SALT_JITTER_Z = 0x7177E12BL;
    private static final long SALT_TYPE = 0x7E9BE001L;
    private static final long SALT_BAND = 0xBA9D0001L;
    private static final long SALT_BIOME = 0xB10E0001L;
    private static final long SALT_NAME = 0x9A3E0001L;
    private static final long SALT_DOCK = 0xD0C4B0A7L;
    private static final long SALT_WILD = 0x317DBEA7L;
    private static final long SALT_WILD_X = 0x317DBEA8L;
    private static final long SALT_WILD_Z = 0x317DBEA9L;
    private static final long SALT_WILD_BIOME = 0x317DBEAAL;
    private static final long SALT_WILD_SIZE = 0x317DBEABL;
    private static final long SALT_WILD_MUSHROOM = 0x317DBEACL;
    private static final long SALT_OCEAN_TEMP = 0x0CEA17E1L;

    /**
     * Scale of the ocean's temperature regions, in blocks. Broad enough that a
     * voyage crosses a few of them.
     */
    private static final int OCEAN_TEMPERATURE_SCALE = 3000;

    /**
     * Ocean biomes in temperature order. Because the temperature field is
     * continuous and this mapping is monotonic, neighbouring water can only
     * differ by one step - warm sea never borders frozen sea.
     */
    private static final List<String> OCEAN_BIOMES = List.of("minecraft:frozen_ocean", "minecraft:cold_ocean",
            "minecraft:ocean", "minecraft:lukewarm_ocean", "minecraft:warm_ocean");

    /**
     * The deep-water counterpart of each entry in {@link #OCEAN_BIOMES}, in the
     * same temperature order. Depth picks between the two lists, and that is
     * what tells vanilla where ocean monuments belong - there is no deep warm
     * ocean in Minecraft, so warm water deepens into lukewarm.
     */
    private static final List<String> DEEP_OCEAN_BIOMES = List.of("minecraft:deep_frozen_ocean",
            "minecraft:deep_cold_ocean", "minecraft:deep_ocean", "minecraft:deep_lukewarm_ocean",
            "minecraft:deep_lukewarm_ocean");

    /**
     * Where the shelf ends and deep water begins, as a fraction of the way from
     * the shelf depth to the abyss depth.
     */
    private static final double DEEP_WATER_FRACTION = 0.6;

    /** Vanilla biomes wild islets draw from - Minecraft-stuff land. */
    private static final List<String> WILD_BIOMES = List.of("minecraft:plains", "minecraft:forest",
            "minecraft:birch_forest", "minecraft:jungle", "minecraft:savanna", "minecraft:swamp",
            "minecraft:flower_forest", "minecraft:dark_forest");

    /** The rare islet biome - no hostile spawns, mycelium, mooshrooms. */
    public static final String MUSHROOM_BIOME = "minecraft:mushroom_fields";
    /** Sandy fringes: where beached shipwrecks and buried treasure belong. */
    private static final String BEACH_BIOME = "minecraft:beach";
    private static final String SNOWY_BEACH_BIOME = "minecraft:snowy_beach";
    /** Half-width of an islet's beach ring, in blocks, either side of the shoreline. */
    private static final int BEACH_RING = 7;
    /** Smallest and largest islet radius as a fraction of the configured mean. */
    private static final double ISLET_MIN_SCALE = 0.55;
    private static final double ISLET_MAX_SCALE = 1.55;

    // Dock/plaza geometry, as fractions of the terrain radius. The plaza sits
    // at ~45% out (where natural terrain is already near sea level, so the
    // flattening cuts little), the quay runs from the plaza to 85% (open water).
    private static final double PLAZA_DIST_FRACTION = 0.45;
    private static final double DOCK_END_FRACTION = 0.85;
    private static final int PLAZA_BLEND_WIDTH = 10;
    private static final int DOCK_HALF_WIDTH = 3;
    /** Plaza walking surface sits this many blocks above sea level. */
    public static final int PLAZA_RISE = 2;
    /** Dock deck sits this many blocks above sea level. */
    public static final int DOCK_RISE = 1;

    private final GalaxyConfig config;
    private final Set<Long> starterCells;
    private final Map<Long, Optional<IslandSpec>> cache = new ConcurrentHashMap<>();
    private final Seabed seabed;

    public GalaxyEngine(GalaxyConfig config) {
        this.config = config;
        this.starterCells = computeStarterCells();
        this.seabed = new Seabed(config.seed(), config.seaLevel(), config.seabed());
    }

    /**
     * The ocean floor field - basins, relief, rifts and seamounts.
     *
     * @return this galaxy's seabed
     */
    public Seabed getSeabed() {
        return seabed;
    }

    public GalaxyConfig getConfig() {
        return config;
    }

    private static long cellKey(int cellX, int cellZ) {
        return ((long) cellX << 32) ^ (cellZ & 0xFFFFFFFFL);
    }

    /**
     * The starterMinIslands cells whose nominal centers are nearest spawn (0,0),
     * deterministically ordered by (distance, cellX, cellZ).
     */
    private Set<Long> computeStarterCells() {
        int need = config.starterMinIslands();
        // Search enough rings to certainly contain the k nearest cells
        int rings = 1;
        while ((2 * rings) * (2L * rings) < need * 4L) {
            rings++;
        }
        rings++;
        record Candidate(int cx, int cz, double dist) {
        }
        List<Candidate> candidates = new ArrayList<>();
        for (int cx = -rings - 1; cx <= rings; cx++) {
            for (int cz = -rings - 1; cz <= rings; cz++) {
                double x = (cx + 0.5) * config.cellSize();
                double z = (cz + 0.5) * config.cellSize();
                candidates.add(new Candidate(cx, cz, Math.hypot(x, z)));
            }
        }
        return candidates.stream()
                .sorted(Comparator.comparingDouble(Candidate::dist).thenComparingInt(Candidate::cx)
                        .thenComparingInt(Candidate::cz))
                .limit(need).map(c -> cellKey(c.cx(), c.cz())).collect(Collectors.toSet());
    }

    /**
     * The spawn island: the trading island reserved at the origin.
     *
     * @return the spawn island spec
     */
    public IslandSpec spawnIsland() {
        return islandInCell(0, 0).orElseThrow();
    }

    /**
     * The island hosted by a galaxy cell, if any. Pure function of (seed, cell).
     *
     * @param cellX cell x
     * @param cellZ cell z
     * @return the island spec, or empty if the cell is open ocean
     */
    public Optional<IslandSpec> islandInCell(int cellX, int cellZ) {
        return cache.computeIfAbsent(cellKey(cellX, cellZ), k -> computeIsland(cellX, cellZ));
    }

    /** The name every spawn island is born with (admins may rename it). */
    public static final String SPAWN_NAME = "Spawn";

    private Optional<IslandSpec> computeIsland(int cellX, int cellZ) {
        // The origin cell is reserved for the spawn island: a full trading
        // island (dock, plaza, market, warp zone) sitting exactly at 0,0, so
        // new sailors start in a working port instead of empty water.
        if (cellX == 0 && cellZ == 0) {
            IslandType type = config.spawnIslandType() != null ? config.spawnIslandType() : rollType(0, 0);
            return Optional.of(new IslandSpec(0, 0, 0, 0, type, SecurityBand.SAFE, rollBiome(0, 0, type),
                    SPAWN_NAME));
        }
        boolean starter = starterCells.contains(cellKey(cellX, cellZ));
        if (!starter
                && Hashing.toUnit(Hashing.cellHash(config.seed(), cellX, cellZ, SALT_OCCUPANCY)) >= config.density()) {
            return Optional.empty();
        }
        int size = config.cellSize();
        int jitter = config.jitter();
        double jx = Hashing.toUnit(Hashing.cellHash(config.seed(), cellX, cellZ, SALT_JITTER_X)) * 2 - 1;
        double jz = Hashing.toUnit(Hashing.cellHash(config.seed(), cellX, cellZ, SALT_JITTER_Z)) * 2 - 1;
        int centerX = (int) Math.round((cellX + 0.5) * size + jx * jitter);
        int centerZ = (int) Math.round((cellZ + 0.5) * size + jz * jitter);
        // Keep clear of the spawn island at the origin
        double fromOrigin = Math.hypot(centerX, centerZ);
        if (fromOrigin < config.minSeparation()) {
            double scale = config.minSeparation() / Math.max(1.0, fromOrigin);
            centerX = (int) Math.round(centerX * scale);
            centerZ = (int) Math.round(centerZ * scale);
        }

        IslandType type = rollType(cellX, cellZ);
        SecurityBand band = starter ? SecurityBand.SAFE : rollBand(cellX, cellZ, centerX, centerZ);
        String biome = rollBiome(cellX, cellZ, type);
        String name = NameGenerator.name(Hashing.cellHash(config.seed(), cellX, cellZ, SALT_NAME));
        return Optional.of(new IslandSpec(cellX, cellZ, centerX, centerZ, type, band, biome, name));
    }

    private IslandType rollType(int cellX, int cellZ) {
        int total = 0;
        for (IslandType t : IslandType.values()) {
            total += Math.max(0, config.typeWeights().getOrDefault(t, 0));
        }
        long roll = Math.floorMod(Hashing.cellHash(config.seed(), cellX, cellZ, SALT_TYPE), total);
        for (IslandType t : IslandType.values()) {
            roll -= Math.max(0, config.typeWeights().getOrDefault(t, 0));
            if (roll < 0) {
                return t;
            }
        }
        return IslandType.AGRICULTURAL; // Unreachable
    }

    /**
     * Security by distance from spawn - the map itself communicates risk: the
     * further out, the more lawless, with a one-step seeded wobble so band
     * borders are not perfect circles.
     */
    private SecurityBand rollBand(int cellX, int cellZ, int centerX, int centerZ) {
        double dist = Math.hypot(centerX, centerZ);
        int step = (int) (dist / config.bandRadius());
        // Wobble -1, 0 or +1
        int wobble = (int) Math.floorMod(Hashing.cellHash(config.seed(), cellX, cellZ, SALT_BAND), 3) - 1;
        int index = Math.clamp(step + wobble, 0, SecurityBand.values().length - 1);
        return SecurityBand.values()[index];
    }

    private String rollBiome(int cellX, int cellZ, IslandType type) {
        List<String> keys = type.getBiomeKeys();
        int i = (int) Math.floorMod(Hashing.cellHash(config.seed(), cellX, cellZ, SALT_BIOME), keys.size());
        return keys.get(i);
    }

    /**
     * All islands whose centers lie within a square search radius of a block
     * position. Scans just the overlapping cells - O(1) for radii up to a few
     * cells.
     *
     * @param blockX block x
     * @param blockZ block z
     * @param radius search radius in blocks
     * @return islands found, possibly empty
     */
    public List<IslandSpec> islandsNear(int blockX, int blockZ, int radius) {
        int size = config.cellSize();
        int minCellX = Math.floorDiv(blockX - radius, size);
        int maxCellX = Math.floorDiv(blockX + radius, size);
        int minCellZ = Math.floorDiv(blockZ - radius, size);
        int maxCellZ = Math.floorDiv(blockZ + radius, size);
        List<IslandSpec> result = new ArrayList<>();
        for (int cx = minCellX; cx <= maxCellX; cx++) {
            for (int cz = minCellZ; cz <= maxCellZ; cz++) {
                islandInCell(cx, cz).filter(
                        s -> Math.abs(s.centerX() - blockX) <= radius && Math.abs(s.centerZ() - blockZ) <= radius)
                        .ifPresent(result::add);
            }
        }
        return result;
    }

    /**
     * The island whose terrain footprint covers a block position, if any.
     *
     * @param blockX block x
     * @param blockZ block z
     * @return the island spec, or empty in open ocean
     */
    public Optional<IslandSpec> islandAt(int blockX, int blockZ) {
        long r2 = (long) config.terrainRadius() * config.terrainRadius();
        return islandsNear(blockX, blockZ, config.terrainRadius()).stream()
                .filter(s -> s.distanceSquared(blockX, blockZ) <= r2).findFirst();
    }

    /**
     * Terrain lift in blocks at a column: {@code landLift} at an island center,
     * cosine-tapered to 0 at the terrain radius, 0 in open ocean. This is what
     * the chunk generator adds to the ocean floor height - it is the only
     * source of land in the world.
     *
     * @param blockX block x
     * @param blockZ block z
     * @return lift in blocks, 0 in open ocean
     */
    public int landLiftAt(int blockX, int blockZ) {
        int radius = config.terrainRadius();
        double best = 0;
        for (IslandSpec s : islandsNear(blockX, blockZ, radius)) {
            double d = Math.sqrt(s.distanceSquared(blockX, blockZ));
            if (d < radius) {
                double mask = 0.5 * (1 + Math.cos(Math.PI * d / radius));
                best = Math.max(best, mask);
            }
        }
        // Wild islets: free land between the trading islands. Small islets are
        // lower as well as narrower, so a sandbar is a sandbar and not a spike.
        Optional<Islet> wild = isletAt(blockX, blockZ);
        if (wild.isPresent()) {
            Islet islet = wild.get();
            double d = Math.sqrt(islet.distanceSquared(blockX, blockZ));
            double mask = 0.5 * (1 + Math.cos(Math.PI * d / islet.radius()));
            return (int) Math.round(config.landLift() * mask * isletHeightScale(islet));
        }
        return (int) Math.round(config.landLift() * best);
    }

    /**
     * How tall an islet stands relative to a trading island, from its size: big
     * islets rise the full land lift, sandbars barely clear the water.
     *
     * @param islet the islet
     * @return scale factor for the land lift
     */
    private double isletHeightScale(Islet islet) {
        if (config.wildIsletRadius() <= 0) {
            return 1.0;
        }
        return Math.clamp((double) islet.radius() / config.wildIsletRadius(), 0.5, 1.15);
    }

    /**
     * The wild islet hosted by a cell, if any: cells without a trading island
     * may roll a small unnamed island - free land for mining, farming and
     * building ("Minecraft stuff"), and claim material for later stages. Each
     * rolls its own radius and biome, so no two look alike.
     *
     * @param cellX cell x
     * @param cellZ cell z
     * @return the islet, or empty
     */
    public Optional<Islet> wildIsletInCell(int cellX, int cellZ) {
        if (config.wildIsletChance() <= 0 || config.wildIsletRadius() <= 0) {
            return Optional.empty();
        }
        if (Hashing.toUnit(Hashing.cellHash(config.seed(), cellX, cellZ, SALT_WILD)) >= config.wildIsletChance()) {
            return Optional.empty();
        }
        int size = config.wildIsletGrid();
        int jitter = size / 4;
        double jx = Hashing.toUnit(Hashing.cellHash(config.seed(), cellX, cellZ, SALT_WILD_X)) * 2 - 1;
        double jz = Hashing.toUnit(Hashing.cellHash(config.seed(), cellX, cellZ, SALT_WILD_Z)) * 2 - 1;
        int x = (int) Math.round((cellX + 0.5) * size + jx * jitter);
        int z = (int) Math.round((cellZ + 0.5) * size + jz * jitter);
        double scale = ISLET_MIN_SCALE + Hashing.toUnit(Hashing.cellHash(config.seed(), cellX, cellZ, SALT_WILD_SIZE))
                * (ISLET_MAX_SCALE - ISLET_MIN_SCALE);
        int radius = Math.max(20, (int) Math.round(config.wildIsletRadius() * scale));
        // Never crowd a trading island: its terrain, its dock and a margin
        int clearance = config.terrainRadius() + radius + 80;
        for (IslandSpec spec : islandsNear(x, z, clearance)) {
            if (spec.distanceSquared(x, z) < (long) clearance * clearance) {
                return Optional.empty();
            }
        }
        return Optional.of(new Islet(x, z, radius, isletBiome(cellX, cellZ)));
    }

    /**
     * A wild islet's whole-island biome (seeded from its cell). Mostly ordinary
     * vanilla land; rarely, mushroom fields.
     */
    private String isletBiome(int cellX, int cellZ) {
        if (Hashing.toUnit(Hashing.cellHash(config.seed(), cellX, cellZ,
                SALT_WILD_MUSHROOM)) < config.mushroomIsletChance()) {
            return MUSHROOM_BIOME;
        }
        long hash = Hashing.cellHash(config.seed(), cellX, cellZ, SALT_WILD_BIOME);
        return WILD_BIOMES.get((int) Math.floorMod(hash, WILD_BIOMES.size()));
    }

    /**
     * The wild islet whose footprint covers a column, if any.
     *
     * @param blockX block x
     * @param blockZ block z
     * @return the islet, or empty
     */
    public Optional<Islet> isletAt(int blockX, int blockZ) {
        return isletNear(blockX, blockZ, 1.0);
    }

    /**
     * The wild islet whose footprint - optionally widened - covers a column.
     *
     * @param blockX block x
     * @param blockZ block z
     * @param scale multiplier on the islet radius; 1.0 is the islet itself, 2.0
     *        includes its surrounding shelf
     * @return the islet, or empty
     */
    private Optional<Islet> isletNear(int blockX, int blockZ, double scale) {
        if (config.wildIsletRadius() <= 0) {
            return Optional.empty();
        }
        int size = config.wildIsletGrid();
        int cellX = Math.floorDiv(blockX, size);
        int cellZ = Math.floorDiv(blockZ, size);
        // A big islet's shelf can reach past its own cell, so check the ring
        for (int cx = cellX - 1; cx <= cellX + 1; cx++) {
            for (int cz = cellZ - 1; cz <= cellZ + 1; cz++) {
                Optional<Islet> islet = wildIsletInCell(cx, cz);
                if (islet.isPresent()) {
                    long reach = Math.round(islet.get().radius() * scale);
                    if (islet.get().distanceSquared(blockX, blockZ) <= reach * reach) {
                        return islet;
                    }
                }
            }
        }
        return Optional.empty();
    }

    /**
     * The distance from an islet's center at which its land meets the water -
     * exactly, because the shelf under an islet is flat by construction. The
     * beach ring and the sand surface hang off this.
     *
     * @param islet the islet
     * @return shoreline radius in blocks, 0 if the islet never breaks the surface
     */
    public double isletShoreRadius(Islet islet) {
        double lift = config.landLift() * isletHeightScale(islet);
        if (lift <= 0) {
            return 0;
        }
        // Land where lift * mask > shelf depth; invert the cosine mask for the
        // exact crossing rather than sampling for it
        double mask = config.seabed().islandShelfDepth() / lift;
        if (mask >= 1.0) {
            return 0;
        }
        return islet.radius() / Math.PI * Math.acos(Math.clamp(2 * mask - 1, -1.0, 1.0));
    }

    /**
     * The number of resident villagers an island supports (3-5, seeded).
     * Deterministic so the respawn audit knows what "fully staffed" means.
     */
    public int villagerCount(IslandSpec spec) {
        return 3 + (int) Math.floorMod(Hashing.cellHash(config.seed(), spec.cellX(), spec.cellZ(), 0x4E51DE47L), 3);
    }

    /**
     * The dock/market plan for an island - pure geometry from the island's
     * seeded bearing.
     *
     * @param spec the island
     * @return its dock plan
     */
    public DockPlan dockPlan(IslandSpec spec) {
        double bearing = Hashing.toUnit(Hashing.cellHash(config.seed(), spec.cellX(), spec.cellZ(), SALT_DOCK)) * 2
                * Math.PI;
        int plazaDist = (int) (config.terrainRadius() * PLAZA_DIST_FRACTION);
        int plazaX = spec.centerX() + (int) Math.round(Math.cos(bearing) * plazaDist);
        int plazaZ = spec.centerZ() + (int) Math.round(Math.sin(bearing) * plazaDist);
        int plazaRadius = Math.max(12, config.terrainRadius() / 10);
        int dockEnd = (int) (config.terrainRadius() * DOCK_END_FRACTION);
        return new DockPlan(spec, bearing, plazaX, plazaZ, plazaRadius, dockEnd);
    }

    /**
     * The terraform instruction for a column that falls on an island's plaza or
     * dock, or empty for natural terrain. The plaza is a flattened disc (with a
     * blend ring); the dock is a solid quay strip from the plaza to open water.
     *
     * @param blockX block x
     * @param blockZ block z
     * @return the column plan, or empty
     */
    public Optional<ColumnPlan> columnPlanAt(int blockX, int blockZ) {
        for (IslandSpec s : islandsNear(blockX, blockZ, config.terrainRadius())) {
            DockPlan plan = dockPlan(s);
            double pd = Math.hypot((double) blockX - plan.plazaX(), (double) blockZ - plan.plazaZ());
            int plazaSurface = config.seaLevel() + PLAZA_RISE;
            // Plaza disc proper
            if (pd <= plan.plazaRadius()) {
                return Optional.of(new ColumnPlan(ColumnPlan.Feature.PLAZA, s, plazaSurface, 1.0));
            }
            // Dock strip in bearing-aligned coordinates. Checked BEFORE the plaza
            // blend ring: the ring blends toward natural (often submerged) terrain
            // on the seaward side, which used to cut a water gap between the plaza
            // and the quay. The quay must run unbroken from plaza edge to pier end.
            double dx = (double) blockX - s.centerX();
            double dz = (double) blockZ - s.centerZ();
            double along = dx * Math.cos(plan.bearing()) + dz * Math.sin(plan.bearing());
            double across = -dx * Math.sin(plan.bearing()) + dz * Math.cos(plan.bearing());
            int plazaDist = (int) (config.terrainRadius() * PLAZA_DIST_FRACTION);
            if (along >= plazaDist && along <= plan.dockEnd() && Math.abs(across) <= DOCK_HALF_WIDTH) {
                return Optional.of(new ColumnPlan(ColumnPlan.Feature.DOCK, s, config.seaLevel() + DOCK_RISE, 1.0));
            }
            // Plaza blend ring
            if (pd <= plan.plazaRadius() + PLAZA_BLEND_WIDTH) {
                double blend = 1.0 - (pd - plan.plazaRadius()) / PLAZA_BLEND_WIDTH;
                return Optional.of(new ColumnPlan(ColumnPlan.Feature.PLAZA, s, plazaSurface, blend));
            }
        }
        return Optional.empty();
    }

    /**
     * The open sea's biome at a position: a seeded temperature field mapped to
     * the ocean biomes in temperature order, so the water varies from frozen
     * through to warm as you sail, and always gradually.
     *
     * @param blockX block x
     * @param blockZ block z
     * @return an ocean biome key
     */
    public String oceanBiomeKeyAt(int blockX, int blockZ) {
        int index = oceanTemperatureIndex(blockX, blockZ);
        return isDeepWater(blockX, blockZ) ? DEEP_OCEAN_BIOMES.get(index) : OCEAN_BIOMES.get(index);
    }

    /**
     * The sea's temperature step at a position: 0 frozen through to 4 warm.
     * Because the field is continuous and this mapping is monotonic, adjacent
     * water can only differ by one step.
     *
     * @param blockX block x
     * @param blockZ block z
     * @return index into the ordered ocean biome lists
     */
    public int oceanTemperatureIndex(int blockX, int blockZ) {
        double temperature = Noise.at(config.seed(), SALT_OCEAN_TEMP, blockX, blockZ, OCEAN_TEMPERATURE_SCALE);
        return Math.clamp((int) (temperature * OCEAN_BIOMES.size()), 0, OCEAN_BIOMES.size() - 1);
    }

    /**
     * Whether a column stands over deep water: dark, cold, and the only place
     * vanilla will put an ocean monument.
     *
     * @param blockX block x
     * @param blockZ block z
     * @return true over the deep basins
     */
    public boolean isDeepWater(int blockX, int blockZ) {
        SeabedConfig sb = config.seabed();
        if (sb.abyssDepth() <= sb.shelfDepth()) {
            return false; // A flat sea floor is never "deep" - it is all one shelf
        }
        double threshold = sb.shelfDepth() + (sb.abyssDepth() - sb.shelfDepth()) * DEEP_WATER_FRACTION;
        return config.seaLevel() - seabedHeightAt(blockX, blockZ) >= threshold;
    }

    /**
     * The Y of the topmost sea-floor block at a column, before any island lift:
     * the natural floor in open water, eased toward the island shelf near land.
     *
     * @param blockX block x
     * @param blockZ block z
     * @return the floor's top Y
     */
    public int seabedHeightAt(int blockX, int blockZ) {
        return seabed.heightAt(blockX, blockZ, shelfBlendAt(blockX, blockZ));
    }

    /**
     * How much a column belongs to an island's shelf rather than the open sea:
     * 1 inside a footprint, easing to 0 at twice its radius. This is what keeps
     * an island that happens to sit over an abyssal plain in shallow water.
     *
     * @param blockX block x
     * @param blockZ block z
     * @return blend in [0, 1]
     */
    public double shelfBlendAt(int blockX, int blockZ) {
        double best = 0;
        int radius = config.terrainRadius();
        for (IslandSpec s : islandsNear(blockX, blockZ, radius * 2)) {
            best = Math.max(best, shelfTaper(Math.sqrt(s.distanceSquared(blockX, blockZ)), radius));
        }
        Optional<Islet> islet = isletNear(blockX, blockZ, 2.0);
        if (islet.isPresent()) {
            best = Math.max(best,
                    shelfTaper(Math.sqrt(islet.get().distanceSquared(blockX, blockZ)), islet.get().radius()));
        }
        return best;
    }

    /**
     * Full shelf inside the footprint, cosine-eased to open sea at twice it.
     */
    private static double shelfTaper(double distance, int radius) {
        if (distance <= radius) {
            return 1.0;
        }
        if (distance >= radius * 2.0) {
            return 0.0;
        }
        return 0.5 * (1 + Math.cos(Math.PI * (distance - radius) / radius));
    }

    /**
     * What the top block of a land column should be. Islets get a sandy fringe
     * at the waterline (and mycelium all over, if they are mushroom islands);
     * everything else is ordinary grass.
     *
     * @param blockX block x
     * @param blockZ block z
     * @return the surface kind
     */
    public SurfaceKind surfaceKindAt(int blockX, int blockZ) {
        Optional<Islet> islet = isletAt(blockX, blockZ);
        if (islet.isEmpty()) {
            return SurfaceKind.GRASS;
        }
        if (islet.get().isMushroom()) {
            return SurfaceKind.MYCELIUM;
        }
        double shore = isletShoreRadius(islet.get());
        double d = Math.sqrt(islet.get().distanceSquared(blockX, blockZ));
        return shore > 0 && d >= shore - BEACH_RING ? SurfaceKind.SAND : SurfaceKind.GRASS;
    }

    /**
     * Every ocean biome the sea can take - the world must declare them all.
     */
    public static List<String> oceanBiomes() {
        List<String> all = new ArrayList<>(OCEAN_BIOMES);
        DEEP_OCEAN_BIOMES.stream().filter(k -> !all.contains(k)).forEach(all::add);
        all.add(BEACH_BIOME);
        all.add(SNOWY_BEACH_BIOME);
        all.add(MUSHROOM_BIOME);
        return List.copyOf(all);
    }

    /**
     * Every land biome a wild islet can take - the world must declare these too.
     *
     * @return the islet biomes
     */
    public static List<String> isletBiomes() {
        List<String> all = new ArrayList<>(WILD_BIOMES);
        all.add(MUSHROOM_BIOME);
        all.add(BEACH_BIOME);
        all.add(SNOWY_BEACH_BIOME);
        return List.copyOf(all);
    }

    /**
     * The ocean biomes in temperature order, shallow water only - the ordering
     * the "never jump more than one step" invariant is checked against.
     *
     * @return the shallow ocean biomes, coldest first
     */
    public static List<String> shallowOceanBiomes() {
        return OCEAN_BIOMES;
    }

    /**
     * The deep-water counterparts, in the same temperature order.
     *
     * @return the deep ocean biomes, coldest first
     */
    public static List<String> deepOceanBiomes() {
        return DEEP_OCEAN_BIOMES;
    }

    /**
     * The biome key governing a column, or empty for default ocean handling.
     * Within an island's terrain radius this is the island biome; in the
     * approach ring (out to 2x the terrain radius) of a FROZEN island it is
     * frozen ocean, so ice forms into fast boat lanes.
     *
     * @param blockX block x
     * @param blockZ block z
     * @return biome key, or empty for open ocean
     */
    public Optional<String> biomeKeyAt(int blockX, int blockZ) {
        Optional<Islet> wild = isletAt(blockX, blockZ);
        if (wild.isPresent()) {
            return Optional.of(isletBiomeAt(wild.get(), blockX, blockZ));
        }
        int radius = config.terrainRadius();
        long r2 = (long) radius * radius;
        long ring2 = 4L * radius * radius;
        Optional<String> ring = Optional.empty();
        for (IslandSpec s : islandsNear(blockX, blockZ, radius * 2)) {
            long d2 = s.distanceSquared(blockX, blockZ);
            if (d2 <= r2) {
                return Optional.of(s.biomeKey());
            }
            if (d2 <= ring2 && s.type().isIcyApproach()) {
                ring = Optional.of("minecraft:frozen_ocean");
            }
        }
        return ring;
    }

    /**
     * The biome of one column of an islet: its own biome inland, and a beach at
     * the waterline - which is what lets vanilla wash up beached shipwrecks and
     * bury treasure there. Mushroom islands have no beach (nor does vanilla).
     *
     * @param islet the islet
     * @param blockX block x
     * @param blockZ block z
     * @return the biome key
     */
    private String isletBiomeAt(Islet islet, int blockX, int blockZ) {
        if (islet.isMushroom()) {
            return islet.biomeKey();
        }
        double shore = isletShoreRadius(islet);
        if (shore > 0) {
            double d = Math.sqrt(islet.distanceSquared(blockX, blockZ));
            if (d >= shore - BEACH_RING && d <= shore + BEACH_RING) {
                // Cold seas get a snowy shore, so the shoreline matches its water
                return oceanTemperatureIndex(blockX, blockZ) <= 1 ? SNOWY_BEACH_BIOME : BEACH_BIOME;
            }
        }
        return islet.biomeKey();
    }
}
