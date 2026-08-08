package world.bentobox.tradewinds.ocean;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;

/**
 * The seeded ocean: which cells host trading islands, where exactly, and what
 * each island is (type, security band, biome, name). Every answer is a pure
 * function of (seed, cell) - same seed, same ocean, on any server (spec
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
public class OceanEngine {

    // Salts for the independent per-cell attribute rolls
    private static final long SALT_OCCUPANCY = 0x0CCA9A9CL;
    private static final long SALT_JITTER_X = 0x7177E12AL;
    private static final long SALT_JITTER_Z = 0x7177E12BL;
    private static final long SALT_TYPE = 0x7E9BE001L;
    private static final long SALT_TECH = 0x7EC81EE1L;
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
    private static final long SALT_COAST = 0x0C0A5701L;
    private static final long SALT_HILLS = 0x81115001L;

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

    /**
     * Vanilla biomes wild islets draw from - Minecraft-stuff land - grouped by
     * the sea temperature at the islet (index order matches
     * {@link #OCEAN_BIOMES}: frozen through warm). A snowfield rises out of
     * frozen water and a jungle out of warm, and between the five bands every
     * generatable overworld land biome can turn up somewhere at sea - a cherry
     * grove sandbar and a pale garden islet are rare finds, not absences.
     * (Cave, river, ocean, Nether and End biomes are deliberately not islet
     * material.)
     */
    static final List<List<String>> WILD_BIOMES = List.of(
            // Frozen seas
            List.of("minecraft:snowy_plains", "minecraft:snowy_taiga", "minecraft:ice_spikes",
                    "minecraft:snowy_slopes", "minecraft:grove", "minecraft:frozen_peaks"),
            // Cold
            List.of("minecraft:taiga", "minecraft:old_growth_pine_taiga", "minecraft:old_growth_spruce_taiga",
                    "minecraft:windswept_hills", "minecraft:windswept_forest",
                    "minecraft:windswept_gravelly_hills", "minecraft:jagged_peaks", "minecraft:stony_shore"),
            // Temperate
            List.of("minecraft:plains", "minecraft:sunflower_plains", "minecraft:forest",
                    "minecraft:birch_forest", "minecraft:old_growth_birch_forest", "minecraft:flower_forest",
                    "minecraft:dark_forest", "minecraft:pale_garden", "minecraft:meadow",
                    "minecraft:cherry_grove", "minecraft:swamp", "minecraft:stony_peaks"),
            // Lukewarm
            List.of("minecraft:savanna", "minecraft:savanna_plateau", "minecraft:windswept_savanna",
                    "minecraft:sparse_jungle", "minecraft:jungle", "minecraft:wooded_badlands"),
            // Warm
            List.of("minecraft:desert", "minecraft:badlands", "minecraft:eroded_badlands",
                    "minecraft:bamboo_jungle", "minecraft:mangrove_swamp"));

    /** The rare islet biome - no hostile spawns, mycelium, mooshrooms. */
    public static final String MUSHROOM_BIOME = "minecraft:mushroom_fields";

    /** Sandy fringes: where beached shipwrecks and buried treasure belong. */
    private static final String BEACH_BIOME = "minecraft:beach";
    private static final String SNOWY_BEACH_BIOME = "minecraft:snowy_beach";

    /**
     * Ground truth, literally: what the top of a land column is made of, by the
     * biome that governs the column. Anything unlisted stands on grass. Applies
     * to trading islands and islets alike - a desert port is a sand island, not
     * a lawn with a desert sky.
     */
    private static final Map<String, SurfaceKind> BIOME_SURFACES = Map.ofEntries(
            Map.entry("minecraft:desert", SurfaceKind.SAND),
            Map.entry(BEACH_BIOME, SurfaceKind.SAND),
            Map.entry(SNOWY_BEACH_BIOME, SurfaceKind.SAND),
            Map.entry("minecraft:badlands", SurfaceKind.RED_SAND),
            Map.entry("minecraft:eroded_badlands", SurfaceKind.RED_SAND),
            Map.entry("minecraft:wooded_badlands", SurfaceKind.RED_SAND),
            Map.entry("minecraft:old_growth_pine_taiga", SurfaceKind.PODZOL),
            Map.entry("minecraft:old_growth_spruce_taiga", SurfaceKind.PODZOL),
            Map.entry("minecraft:stony_shore", SurfaceKind.STONE),
            Map.entry("minecraft:stony_peaks", SurfaceKind.STONE),
            Map.entry("minecraft:jagged_peaks", SurfaceKind.STONE),
            Map.entry("minecraft:windswept_gravelly_hills", SurfaceKind.GRAVEL),
            Map.entry("minecraft:mangrove_swamp", SurfaceKind.MUD),
            Map.entry("minecraft:grove", SurfaceKind.SNOW),
            Map.entry("minecraft:snowy_slopes", SurfaceKind.SNOW),
            Map.entry("minecraft:frozen_peaks", SurfaceKind.SNOW),
            Map.entry("minecraft:ice_spikes", SurfaceKind.SNOW),
            Map.entry(MUSHROOM_BIOME, SurfaceKind.MYCELIUM));
    /**
     * How far above sea level land still counts as shore. Measured from the
     * finished terrain rather than from a radius, so a beach follows the real
     * waterline however ragged the coast is.
     */
    private static final int SHORE_HEIGHT = 4;
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

    /** Highest tech level an island can roll. */
    public static final int MAX_TECH_LEVEL = 7;
    /** The tech level the starter cluster guarantees at least one of. */
    private static final int STARTER_TECH_FLOOR = 3;

    /**
     * Base tech level per island type - industry and luxury skew high, farms
     * and fisheries low. The seeded wobble of +/-2 around these means an
     * "Advanced Agricultural" island exists, it is just uncommon.
     */
    private static final Map<IslandType, Integer> TECH_BASE = Map.of(
            IslandType.AGRICULTURAL, 2,
            IslandType.FISHING, 2,
            IslandType.FOREST, 3,
            IslandType.FROZEN, 3,
            IslandType.MINING, 4,
            IslandType.INDUSTRIAL, 5,
            IslandType.LUXURY, 5);

    private final OceanConfig config;
    private final Set<Long> starterCells;
    private final Map<Long, Optional<IslandSpec>> cache = new ConcurrentHashMap<>();
    private final Seabed seabed;
    /** The starter cell whose island is lifted to the tech floor, if any needs it. */
    private final long boostedTechCell;

    public OceanEngine(OceanConfig config) {
        this.config = config;
        this.starterCells = computeStarterCells();
        this.seabed = new Seabed(config.seed(), config.seaLevel(), config.seabed());
        this.boostedTechCell = computeBoostedTechCell();
    }

    /**
     * The starter cluster must hold at least one island of the tech floor
     * (spec 2.2), or a fresh sailor could be walled off from every useful
     * shop by seed luck. If no starter cell rolls it naturally, the cell with
     * the highest natural roll (ties by key order) is lifted to the floor -
     * deterministically, in the constructor, so the answer never depends on
     * query order.
     */
    private long computeBoostedTechCell() {
        long best = Long.MIN_VALUE;
        int bestRoll = -1;
        for (Long key : starterCells.stream().sorted().toList()) {
            int cellX = (int) (key >> 32);
            int cellZ = key.intValue();
            IslandType type = cellX == 0 && cellZ == 0 && config.spawnIslandType() != null
                    ? config.spawnIslandType()
                    : rollType(cellX, cellZ);
            int roll = rollTech(cellX, cellZ, type);
            if (roll >= STARTER_TECH_FLOOR) {
                return Long.MIN_VALUE; // Seed luck already provides one
            }
            if (roll > bestRoll) {
                bestRoll = roll;
                best = key;
            }
        }
        return best;
    }

    /**
     * The ocean floor field - basins, relief, rifts and seamounts.
     *
     * @return this ocean's seabed
     */
    public Seabed getSeabed() {
        return seabed;
    }

    public OceanConfig getConfig() {
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
     * The island hosted by a ocean cell, if any. Pure function of (seed, cell).
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
            // The one direct aesthetic knob in a seeded world: an admin may
            // pick the spawn island's biome outright (it is every player's
            // first sight of the game); blank keeps the seeded roll
            String biome = config.spawnIslandBiome() != null ? config.spawnIslandBiome()
                    : rollBiome(0, 0, type);
            return Optional.of(new IslandSpec(0, 0, 0, 0, type, SecurityBand.SAFE, biome,
                    SPAWN_NAME, techLevel(0, 0, type)));
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
        return Optional.of(new IslandSpec(cellX, cellZ, centerX, centerZ, type, band, biome, name,
                techLevel(cellX, cellZ, type)));
    }

    /**
     * The natural tech roll: the type's base plus a seeded wobble of +/-2,
     * clamped to 1..{@value #MAX_TECH_LEVEL}.
     */
    private int rollTech(int cellX, int cellZ, IslandType type) {
        int base = TECH_BASE.getOrDefault(type, 3);
        int wobble = Math.floorMod(Hashing.cellHash(config.seed(), cellX, cellZ, SALT_TECH), 5) - 2;
        return Math.clamp((long) base + wobble, 1, MAX_TECH_LEVEL);
    }

    /**
     * An island's tech level: the natural roll, lifted to the starter floor
     * for the one boosted starter cell (if the cluster needed one).
     */
    private int techLevel(int cellX, int cellZ, IslandType type) {
        int rolled = rollTech(cellX, cellZ, type);
        return cellKey(cellX, cellZ) == boostedTechCell ? Math.max(rolled, STARTER_TECH_FLOOR) : rolled;
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
        int wobble = Math.floorMod(Hashing.cellHash(config.seed(), cellX, cellZ, SALT_BAND), 3) - 1;
        int index = (int) Math.clamp((long) step + wobble, 0L, (long) SecurityBand.values().length - 1);
        return SecurityBand.values()[index];
    }

    private String rollBiome(int cellX, int cellZ, IslandType type) {
        List<String> keys = type.getBiomeKeys();
        int i = Math.floorMod(Hashing.cellHash(config.seed(), cellX, cellZ, SALT_BIOME), keys.size());
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
        int radius = config.terrainRadius();
        return islandsNear(blockX, blockZ, searchRadius(radius)).stream()
                .filter(s -> shapedDistance(blockX, blockZ, s.centerX(), s.centerZ(), radius) <= radius)
                .findFirst();
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
        for (IslandSpec s : islandsNear(blockX, blockZ, searchRadius(radius))) {
            double d = shapedDistance(blockX, blockZ, s.centerX(), s.centerZ(), radius);
            if (d < radius) {
                best = Math.max(best, 0.5 * (1 + Math.cos(Math.PI * d / radius)));
            }
        }
        // Wild islets: free land between the trading islands. Small islets are
        // lower as well as narrower, so a sandbar is a sandbar and not a spike.
        Optional<Islet> wild = isletAt(blockX, blockZ);
        if (wild.isPresent()) {
            Islet islet = wild.get();
            double d = shapedDistance(blockX, blockZ, islet.centerX(), islet.centerZ(), islet.radius());
            double mask = 0.5 * (1 + Math.cos(Math.PI * Math.min(1.0, d / islet.radius())));
            return (int) Math.round(config.landLift() * mask * isletHeightScale(islet) * hillsAt(blockX, blockZ));
        }
        return (int) Math.round(config.landLift() * best * hillsAt(blockX, blockZ));
    }

    /**
     * Distance from a center, warped by a seeded noise field so that a radial
     * mask draws a ragged coast instead of a circle.
     * <p>
     * A cosine mask on true distance is a perfect disc - which is exactly what
     * it looked like in play. Pushing the distance in and out with noise before
     * the mask sees it turns the same mask into bays and headlands, and costs
     * one noise lookup. The warp scales with the island's radius so a sandbar
     * and a trading island are equally ragged for their size.
     *
     * @param blockX block x
     * @param blockZ block z
     * @param centerX island center x
     * @param centerZ island center z
     * @param radius the island's nominal radius
     * @return the warped distance, never negative
     */
    public double shapedDistance(int blockX, int blockZ, int centerX, int centerZ, int radius) {
        double d = Math.hypot((double) blockX - centerX, (double) blockZ - centerZ);
        if (config.shape().coastRoughness() <= 0) {
            return d;
        }
        // Lattice tied to the radius: features big enough to be a bay, small
        // enough that there are several around one island. Two explicit octaves
        // rather than fbm - averaging octaves pulls the field toward its middle,
        // which cost most of the warp and left the coast nearly round anyway.
        int lattice = Math.max(16, radius / 2);
        double broad = Noise.at(config.seed(), SALT_COAST, blockX, blockZ, lattice) * 2 - 1;
        double detail = Noise.at(config.seed(), SALT_COAST + 1, blockX, blockZ, Math.max(8, lattice / 3)) * 2 - 1;
        double warp = broad * 0.75 + detail * 0.25;
        return Math.max(0, d + warp * radius * config.shape().coastRoughness());
    }

    /**
     * How much the land rises or falls here relative to its plain radial cone:
     * the difference between a smooth dome and something with hills and hollows
     * on it. Multiplied into the lift, so it fades out at the shore rather than
     * calving bits of land off into the sea.
     *
     * @param blockX block x
     * @param blockZ block z
     * @return multiplier around 1
     */
    private double hillsAt(int blockX, int blockZ) {
        double hilliness = config.shape().hilliness();
        if (hilliness <= 0) {
            return 1.0;
        }
        return 1.0 - hilliness / 2 + Noise.fbm(config.seed(), SALT_HILLS, blockX, blockZ, 44, 3, 0.5) * hilliness;
    }

    /**
     * How far to search for islands whose warped coastline could still reach a
     * column.
     *
     * @param radius nominal radius
     * @return the search radius in blocks
     */
    private int searchRadius(int radius) {
        return (int) Math.ceil(radius * config.shape().searchMargin());
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
        return Optional.of(new Islet(x, z, radius, isletBiome(cellX, cellZ, x, z)));
    }

    /**
     * A wild islet's whole-island biome: seeded from its cell, drawn from the
     * band matching the sea temperature at its center - so the land suits the
     * water it stands in. Rarely, mushroom fields instead.
     */
    private String isletBiome(int cellX, int cellZ, int centerX, int centerZ) {
        if (Hashing.toUnit(Hashing.cellHash(config.seed(), cellX, cellZ,
                SALT_WILD_MUSHROOM)) < config.mushroomIsletChance()) {
            return MUSHROOM_BIOME;
        }
        List<String> band = WILD_BIOMES.get(oceanTemperatureIndex(centerX, centerZ));
        long hash = Hashing.cellHash(config.seed(), cellX, cellZ, SALT_WILD_BIOME);
        return band.get(Math.floorMod(hash, band.size()));
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
                    Islet i = islet.get();
                    double d = shapedDistance(blockX, blockZ, i.centerX(), i.centerZ(), i.radius());
                    if (d <= i.radius() * scale) {
                        return islet;
                    }
                }
            }
        }
        return Optional.empty();
    }

    /**
     * The finished height of the land or sea floor at a column - the same
     * number the chunk generator uses as the top of the column.
     *
     * @param blockX block x
     * @param blockZ block z
     * @return the surface Y
     */
    public int surfaceHeightAt(int blockX, int blockZ) {
        return seabedHeightAt(blockX, blockZ) + landLiftAt(blockX, blockZ);
    }

    /**
     * Whether a column is land just above the waterline - a shore. Read from
     * the finished terrain rather than from a radius, so it follows a ragged
     * coast exactly and needs no geometry of its own.
     *
     * @param blockX block x
     * @param blockZ block z
     * @return true on the beach
     */
    public boolean isShoreAt(int blockX, int blockZ) {
        int above = surfaceHeightAt(blockX, blockZ) - config.seaLevel();
        return above > 0 && above <= SHORE_HEIGHT;
    }

    /**
     * The number of resident villagers an island supports (3-5, seeded).
     * Deterministic so the respawn audit knows what "fully staffed" means.
     */
    public int villagerCount(IslandSpec spec) {
        return 3 + Math.floorMod(Hashing.cellHash(config.seed(), spec.cellX(), spec.cellZ(), 0x4E51DE47L), 3);
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
        for (IslandSpec s : islandsNear(blockX, blockZ, searchRadius(radius * 2))) {
            best = Math.max(best,
                    shelfTaper(shapedDistance(blockX, blockZ, s.centerX(), s.centerZ(), radius), radius));
        }
        Optional<Islet> islet = isletNear(blockX, blockZ, 2.0);
        if (islet.isPresent()) {
            Islet i = islet.get();
            best = Math.max(best,
                    shelfTaper(shapedDistance(blockX, blockZ, i.centerX(), i.centerZ(), i.radius()), i.radius()));
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
     * What the top block of a land column should be: whatever suits the biome
     * that governs the column. The biome already knows everything - islet or
     * island, beach fringe at the waterline, mushroom fields - so the surface
     * simply follows it: sand under a desert, mud under a mangrove swamp, red
     * sand in the badlands, snowpack on the slopes, grass for everything else.
     *
     * @param blockX block x
     * @param blockZ block z
     * @return the surface kind
     */
    public SurfaceKind surfaceKindAt(int blockX, int blockZ) {
        return biomeKeyAt(blockX, blockZ)
                .map(key -> BIOME_SURFACES.getOrDefault(key, SurfaceKind.GRASS))
                .orElse(SurfaceKind.GRASS);
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
        List<String> all = new ArrayList<>();
        WILD_BIOMES.forEach(band -> band.stream().filter(k -> !all.contains(k)).forEach(all::add));
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
        Optional<String> ring = Optional.empty();
        for (IslandSpec s : islandsNear(blockX, blockZ, searchRadius(radius * 2))) {
            // The same warped distance the terrain uses, so the biome follows
            // the ragged coast instead of drawing a circle over it
            double d = shapedDistance(blockX, blockZ, s.centerX(), s.centerZ(), radius);
            if (d <= radius) {
                return Optional.of(s.biomeKey());
            }
            if (d <= radius * 2.0 && s.type().isIcyApproach()) {
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
        if (isShoreAt(blockX, blockZ)) {
            // Cold seas get a snowy shore, so the shoreline matches its water
            return oceanTemperatureIndex(blockX, blockZ) <= 1 ? SNOWY_BEACH_BIOME : BEACH_BIOME;
        }
        return islet.biomeKey();
    }
}
