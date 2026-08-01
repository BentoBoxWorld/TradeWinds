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

    /** Vanilla biomes wild islets draw from - Minecraft-stuff land. */
    private static final List<String> WILD_BIOMES = List.of("minecraft:plains", "minecraft:forest",
            "minecraft:birch_forest", "minecraft:jungle", "minecraft:savanna", "minecraft:swamp",
            "minecraft:flower_forest", "minecraft:dark_forest");

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

    public GalaxyEngine(GalaxyConfig config) {
        this.config = config;
        this.starterCells = computeStarterCells();
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
        // Wild islets: free land between the trading islands
        Optional<int[]> wild = wildIsletAt(blockX, blockZ);
        if (wild.isPresent()) {
            double d = Math.hypot((double) blockX - wild.get()[0], (double) blockZ - wild.get()[1]);
            best = Math.max(best, 0.5 * (1 + Math.cos(Math.PI * d / config.wildIsletRadius())));
        }
        return (int) Math.round(config.landLift() * best);
    }

    /**
     * The wild islet hosted by a cell, if any: cells without a trading island
     * may roll a small unnamed island - free land for mining, farming and
     * building ("Minecraft stuff"), and claim material for later stages.
     *
     * @param cellX cell x
     * @param cellZ cell z
     * @return {centerX, centerZ} or empty
     */
    public Optional<int[]> wildIsletInCell(int cellX, int cellZ) {
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
        // Never crowd a trading island: its terrain, its dock and a margin
        int clearance = config.terrainRadius() + config.wildIsletRadius() + 80;
        for (IslandSpec spec : islandsNear(x, z, clearance)) {
            if (spec.distanceSquared(x, z) < (long) clearance * clearance) {
                return Optional.empty();
            }
        }
        return Optional.of(new int[] { x, z });
    }

    /**
     * The wild islet whose footprint covers a column, if any.
     *
     * @param blockX block x
     * @param blockZ block z
     * @return {centerX, centerZ} or empty
     */
    public Optional<int[]> wildIsletAt(int blockX, int blockZ) {
        int radius = config.wildIsletRadius();
        if (radius <= 0) {
            return Optional.empty();
        }
        int size = config.wildIsletGrid();
        int cellX = Math.floorDiv(blockX, size);
        int cellZ = Math.floorDiv(blockZ, size);
        for (int cx = cellX - 1; cx <= cellX + 1; cx++) {
            for (int cz = cellZ - 1; cz <= cellZ + 1; cz++) {
                Optional<int[]> islet = wildIsletInCell(cx, cz);
                if (islet.isPresent()) {
                    long dx = (long) blockX - islet.get()[0];
                    long dz = (long) blockZ - islet.get()[1];
                    if (dx * dx + dz * dz <= (long) radius * radius) {
                        return islet;
                    }
                }
            }
        }
        return Optional.empty();
    }

    /**
     * A wild islet's whole-island biome (seeded from its cell).
     */
    public String wildIsletBiome(int centerX, int centerZ) {
        int size = config.wildIsletGrid();
        long hash = Hashing.cellHash(config.seed(), Math.floorDiv(centerX, size), Math.floorDiv(centerZ, size),
                SALT_WILD_BIOME);
        return WILD_BIOMES.get((int) Math.floorMod(hash, WILD_BIOMES.size()));
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
        double temperature = Noise.at(config.seed(), SALT_OCEAN_TEMP, blockX, blockZ, OCEAN_TEMPERATURE_SCALE);
        int index = Math.clamp((int) (temperature * OCEAN_BIOMES.size()), 0, OCEAN_BIOMES.size() - 1);
        return OCEAN_BIOMES.get(index);
    }

    /**
     * Every ocean biome the sea can take - the world must declare them all.
     */
    public static List<String> oceanBiomes() {
        return OCEAN_BIOMES;
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
        Optional<int[]> wild = wildIsletAt(blockX, blockZ);
        if (wild.isPresent()) {
            return Optional.of(wildIsletBiome(wild.get()[0], wild.get()[1]));
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
}
