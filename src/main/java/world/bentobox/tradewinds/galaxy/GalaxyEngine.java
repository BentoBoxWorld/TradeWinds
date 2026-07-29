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
     * The island hosted by a galaxy cell, if any. Pure function of (seed, cell).
     *
     * @param cellX cell x
     * @param cellZ cell z
     * @return the island spec, or empty if the cell is open ocean
     */
    public Optional<IslandSpec> islandInCell(int cellX, int cellZ) {
        return cache.computeIfAbsent(cellKey(cellX, cellZ), k -> computeIsland(cellX, cellZ));
    }

    private Optional<IslandSpec> computeIsland(int cellX, int cellZ) {
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

        IslandType type = rollType(cellX, cellZ);
        SecurityBand band = starter ? SecurityBand.SAFE : rollBand(cellX, cellZ, centerX, centerZ);
        String biome = rollBiome(cellX, cellZ, type);
        String name = NameGenerator.name(Hashing.cellHash(config.seed(), cellX, cellZ, SALT_NAME));
        return Optional.of(new IslandSpec(cellX, cellZ, centerX, centerZ, type, band, biome, name));
    }

    private IslandType rollType(int cellX, int cellZ) {
        int total = 0;
        for (IslandType t : IslandType.values()) {
            total += t.getWeight();
        }
        long roll = Math.floorMod(Hashing.cellHash(config.seed(), cellX, cellZ, SALT_TYPE), total);
        for (IslandType t : IslandType.values()) {
            roll -= t.getWeight();
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
        return (int) Math.round(config.landLift() * best);
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
