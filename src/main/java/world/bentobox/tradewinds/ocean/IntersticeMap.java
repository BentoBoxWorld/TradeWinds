package world.bentobox.tradewinds.ocean;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.OptionalInt;

/**
 * The interstice's features, as pure seeded geometry: wart shoals (small
 * soul-sand banks breaking the dark sea), wither watchtowers, and the ship
 * graveyard (wrecks of hulls that misjumped and never re-engaged). Everything
 * is a pure function of (seed, position) - no Bukkit imports, headlessly
 * testable - mirroring how the ocean treats wild islets (spec principle 5).
 * <p>
 * Interstice-plan sources 1 and 5; the decorator draws crops, groves, quartz
 * and the tower masonry on top of what this class places.
 *
 * @author tastybento
 */
public class IntersticeMap {

    private static final long SALT_SHOAL = 0x5B0A1L;
    private static final long SALT_SHOAL_X = 0x5B0A2L;
    private static final long SALT_SHOAL_Z = 0x5B0A3L;
    private static final long SALT_SHOAL_SIZE = 0x5B0A4L;
    private static final long SALT_GRAND = 0x5B0A5L;
    private static final long SALT_GRAND_KIND = 0x5B0A6L;
    private static final long SALT_TOWER = 0x70E51L;
    private static final long SALT_TOWER_X = 0x70E52L;
    private static final long SALT_TOWER_Z = 0x70E53L;
    private static final long SALT_WRECK = 0x3EC0D1L;
    private static final long SALT_WRECK_X = 0x3EC0D2L;
    private static final long SALT_WRECK_Z = 0x3EC0D3L;
    private static final long SALT_WRECK_KIND = 0x3EC0D4L;
    private static final long SALT_WRECK_ROT = 0x3EC0D5L;
    private static final long SALT_WRECK_GRADE = 0x3EC0D6L;
    private static final long SALT_WRECK_SINK = 0x3EC0D7L;
    private static final long SALT_WRECK_LOOT = 0x3EC0D8L;

    /** Radius of the reef mound raised under every wreck. */
    private static final int WRECK_MOUND_RADIUS = 18;
    /** The shallowest a wreck mound crests: this far under the sea surface.
     * Deep enough that a hull perched on it rides mostly UNDER the water -
     * a stern, a cabin roof, a mast poking out (tuned 2026-08-07: at 2 the
     * hulls stood proud like beached ships; Ben wants them just breaking
     * the surface). */
    private static final int WRECK_CREST_DEPTH = 6;

    /** Fraction of wrecks that are COMMON (fortress-grade loot). */
    private static final double GRADE_COMMON = 0.70;
    /** COMMON plus this fraction are RARE (bastion finds); the rest TREASURE. */
    private static final double GRADE_RARE = 0.25;

    /** Shoal radii roll between these fractions of the nominal radius. */
    private static final double MIN_SCALE = 0.6;
    private static final double MAX_SCALE = 1.4;
    /** How far a shoal's crown rises above the sea at its centre. */
    private static final int CROWN_RISE = 2;
    /** How far below the sea the submerged rim starts. */
    private static final int RIM_DEPTH = 2;

    /**
     * A wart shoal: a soul-sand bank in the interstice.
     *
     * @param centerX block x of the centre
     * @param centerZ block z of the centre
     * @param radius footprint radius in blocks
     * @param grand true for the rare grand shoal carrying a fungus grove
     * @param crimson grove kind for grand shoals: crimson or warped
     */
    public record Shoal(int centerX, int centerZ, int radius, boolean grand, boolean crimson) {
        public long distanceSquared(int blockX, int blockZ) {
            long dx = (long) blockX - centerX;
            long dz = (long) blockZ - centerZ;
            return dx * dx + dz * dz;
        }
    }

    /** A wither watchtower's position. */
    public record Tower(int centerX, int centerZ) {
    }

    /** A wreck's loot grade - most hulls carried ordinary cargo. */
    public enum WreckGrade {
        COMMON, RARE, TREASURE
    }

    /**
     * Tuning parameters for wreck generation and placement.
     *
     * @param grid cell grid size in blocks
     * @param chance per-cell occupancy chance (0-1)
     * @param lootChance fraction of wrecks that carry stocked chests (0-1)
     */
    public record WreckTuning(int grid, double chance, double lootChance) {
    }

    /**
     * A shipwreck perched on its own reef mound, half in and half out of the
     * water: a ship that misjumped and never re-engaged. Most are scenery -
     * only {@code loot} wrecks carry stocked chests.
     *
     * @param centerX block x of the hull's centre
     * @param centerZ block z of the hull's centre
     * @param variant non-negative pick for the template list (mod list size)
     * @param rotation quarter-turns, 0-3
     * @param grade what its chests are worth, when they are worth anything
     * @param sink how much deeper than the shallowest crest this reef sits,
     *        0-2 - the undulation that puts some hulls high and dry and some
     *        just awash
     * @param loot whether this wreck's chests are stocked at all
     */
    public record Wreck(int centerX, int centerZ, int variant, int rotation, WreckGrade grade, int sink,
            boolean loot) {
        public long distanceSquared(int blockX, int blockZ) {
            long dx = (long) blockX - centerX;
            long dz = (long) blockZ - centerZ;
            return dx * dx + dz * dz;
        }
    }

    private final long seed;
    private final int shoalGrid;
    private final double shoalChance;
    private final int shoalRadius;
    private final double grandShoalChance;
    private final int towerGrid;
    private final double towerChance;
    private final int wreckGrid;
    private final double wreckChance;
    private final double wreckLootChance;

    public IntersticeMap(long seed, int shoalGrid, double shoalChance, int shoalRadius,
            double grandShoalChance, int towerGrid, double towerChance, WreckTuning wreck) {
        this.seed = seed;
        this.shoalGrid = Math.max(64, shoalGrid);
        this.shoalChance = shoalChance;
        this.shoalRadius = shoalRadius;
        this.grandShoalChance = grandShoalChance;
        this.towerGrid = Math.max(256, towerGrid);
        this.towerChance = towerChance;
        this.wreckGrid = Math.max(64, wreck.grid());
        this.wreckChance = wreck.chance();
        this.wreckLootChance = wreck.lootChance();
    }

    /**
     * The shoal hosted by a shoal-grid cell, if any.
     */
    public Optional<Shoal> shoalInCell(int cellX, int cellZ) {
        if (shoalChance <= 0 || shoalRadius <= 0) {
            return Optional.empty();
        }
        if (Hashing.toUnit(Hashing.cellHash(seed, cellX, cellZ, SALT_SHOAL)) >= shoalChance) {
            return Optional.empty();
        }
        int jitter = shoalGrid / 4;
        double jx = Hashing.toUnit(Hashing.cellHash(seed, cellX, cellZ, SALT_SHOAL_X)) * 2 - 1;
        double jz = Hashing.toUnit(Hashing.cellHash(seed, cellX, cellZ, SALT_SHOAL_Z)) * 2 - 1;
        int x = (int) Math.round((cellX + 0.5) * shoalGrid + jx * jitter);
        int z = (int) Math.round((cellZ + 0.5) * shoalGrid + jz * jitter);
        double scale = MIN_SCALE
                + Hashing.toUnit(Hashing.cellHash(seed, cellX, cellZ, SALT_SHOAL_SIZE)) * (MAX_SCALE - MIN_SCALE);
        int radius = Math.max(4, (int) Math.round(shoalRadius * scale));
        boolean grand = Hashing.toUnit(Hashing.cellHash(seed, cellX, cellZ, SALT_GRAND)) < grandShoalChance;
        boolean crimson = Hashing.cellHash(seed, cellX, cellZ, SALT_GRAND_KIND) % 2 == 0;
        return Optional.of(new Shoal(x, z, radius, grand, crimson));
    }

    /**
     * Shoals whose footprint could reach a position.
     */
    public List<Shoal> shoalsNear(int blockX, int blockZ, int range) {
        List<Shoal> found = new ArrayList<>();
        int reach = range + (int) Math.ceil(shoalRadius * MAX_SCALE) + shoalGrid / 4;
        int minCellX = Math.floorDiv(blockX - reach, shoalGrid);
        int maxCellX = Math.floorDiv(blockX + reach, shoalGrid);
        int minCellZ = Math.floorDiv(blockZ - reach, shoalGrid);
        int maxCellZ = Math.floorDiv(blockZ + reach, shoalGrid);
        for (int cx = minCellX; cx <= maxCellX; cx++) {
            for (int cz = minCellZ; cz <= maxCellZ; cz++) {
                shoalInCell(cx, cz).ifPresent(found::add);
            }
        }
        return found;
    }

    /**
     * The absolute floor-top Y a shoal wants at a column, if any shoal's
     * footprint covers it: a low dome - crown two blocks proud of the sea,
     * rim starting under it, so every shoal has plantable top and wadable
     * edge. The generator takes the max of this and the natural floor.
     *
     * @param blockX block x
     * @param blockZ block z
     * @param seaLevel the interstice sea surface Y
     * @return the wanted floor-top Y, or empty when no shoal reaches here
     */
    public OptionalInt shoalSurfaceAt(int blockX, int blockZ, int seaLevel) {
        for (Shoal shoal : shoalsNear(blockX, blockZ, 0)) {
            double r = Math.sqrt(shoal.distanceSquared(blockX, blockZ));
            if (r <= shoal.radius()) {
                double t = 1.0 - r / shoal.radius();
                int y = seaLevel - RIM_DEPTH + (int) Math.round(t * (CROWN_RISE + RIM_DEPTH + 1));
                return OptionalInt.of(Math.min(seaLevel + 1 + CROWN_RISE, y));
            }
        }
        return OptionalInt.empty();
    }

    /**
     * Whether a column is clear of every feature - where a stranding may
     * safely drop a sailor. Towers get a fixed margin.
     *
     * @param blockX block x
     * @param blockZ block z
     * @return true if open water as far as this map is concerned
     */
    public boolean isOpenWater(int blockX, int blockZ) {
        if (shoalSurfaceAt(blockX, blockZ, 0).isPresent()) {
            return false;
        }
        // Wreck reefs crest just under the surface - not water to strand in
        if (!wrecksNear(blockX, blockZ, WRECK_MOUND_RADIUS + 2).isEmpty()) {
            return false;
        }
        return towersNear(blockX, blockZ, 12).isEmpty();
    }

    /**
     * The watchtower hosted by a tower-grid cell, if any.
     */
    public Optional<Tower> towerInCell(int cellX, int cellZ) {
        if (towerChance <= 0) {
            return Optional.empty();
        }
        if (Hashing.toUnit(Hashing.cellHash(seed, cellX, cellZ, SALT_TOWER)) >= towerChance) {
            return Optional.empty();
        }
        int jitter = towerGrid / 4;
        double jx = Hashing.toUnit(Hashing.cellHash(seed, cellX, cellZ, SALT_TOWER_X)) * 2 - 1;
        double jz = Hashing.toUnit(Hashing.cellHash(seed, cellX, cellZ, SALT_TOWER_Z)) * 2 - 1;
        int x = (int) Math.round((cellX + 0.5) * towerGrid + jx * jitter);
        int z = (int) Math.round((cellZ + 0.5) * towerGrid + jz * jitter);
        return Optional.of(new Tower(x, z));
    }

    /**
     * The wreck hosted by a wreck-grid cell, if any. Grade is weighted -
     * most hulls carried ordinary cargo, a few were worth chasing.
     */
    public Optional<Wreck> wreckInCell(int cellX, int cellZ) {
        if (wreckChance <= 0) {
            return Optional.empty();
        }
        if (Hashing.toUnit(Hashing.cellHash(seed, cellX, cellZ, SALT_WRECK)) >= wreckChance) {
            return Optional.empty();
        }
        int jitter = wreckGrid / 4;
        double jx = Hashing.toUnit(Hashing.cellHash(seed, cellX, cellZ, SALT_WRECK_X)) * 2 - 1;
        double jz = Hashing.toUnit(Hashing.cellHash(seed, cellX, cellZ, SALT_WRECK_Z)) * 2 - 1;
        int x = (int) Math.round((cellX + 0.5) * wreckGrid + jx * jitter);
        int z = (int) Math.round((cellZ + 0.5) * wreckGrid + jz * jitter);
        int variant = (int) (Math.abs(Hashing.cellHash(seed, cellX, cellZ, SALT_WRECK_KIND)) % 1024);
        int rotation = Math.floorMod(Hashing.cellHash(seed, cellX, cellZ, SALT_WRECK_ROT), 4);
        double roll = Hashing.toUnit(Hashing.cellHash(seed, cellX, cellZ, SALT_WRECK_GRADE));
        WreckGrade grade;
        if (roll < GRADE_COMMON) {
            grade = WreckGrade.COMMON;
        } else if (roll < GRADE_COMMON + GRADE_RARE) {
            grade = WreckGrade.RARE;
        } else {
            grade = WreckGrade.TREASURE;
        }
        int sink = Math.floorMod(Hashing.cellHash(seed, cellX, cellZ, SALT_WRECK_SINK), 3);
        boolean loot = Hashing.toUnit(Hashing.cellHash(seed, cellX, cellZ, SALT_WRECK_LOOT)) < wreckLootChance;
        return Optional.of(new Wreck(x, z, variant, rotation, grade, sink, loot));
    }

    /**
     * The floor-top Y a wreck's reef mound wants at a column, if any wreck's
     * mound reaches it: a steep dome cresting {@code WRECK_CREST_DEPTH +
     * sink} blocks under the surface, so the hull perched on it rides half
     * in and half out of the water - visible from the deck of a passing
     * boat, which is the whole point of a graveyard (ruled by Ben,
     * 2026-08-07: wrecks on the deep seabed read as empty water). The
     * generator takes the max of this and the natural floor.
     *
     * @param blockX block x
     * @param blockZ block z
     * @param seaLevel the interstice sea surface Y
     * @return the wanted floor-top Y, or empty when no mound reaches here
     */
    public OptionalInt wreckSurfaceAt(int blockX, int blockZ, int seaLevel) {
        int best = Integer.MIN_VALUE;
        for (Wreck wreck : wrecksNear(blockX, blockZ, WRECK_MOUND_RADIUS)) {
            double r = Math.sqrt(wreck.distanceSquared(blockX, blockZ));
            if (r > WRECK_MOUND_RADIUS) {
                continue;
            }
            int crest = seaLevel - WRECK_CREST_DEPTH - wreck.sink();
            int y = crest - (int) Math.round(Math.pow(r / WRECK_MOUND_RADIUS, 1.3) * 14);
            best = Math.max(best, y);
        }
        return best == Integer.MIN_VALUE ? OptionalInt.empty() : OptionalInt.of(best);
    }

    /**
     * Wrecks whose centre lies within range of a position.
     */
    public List<Wreck> wrecksNear(int blockX, int blockZ, int range) {
        List<Wreck> found = new ArrayList<>();
        int reach = range + wreckGrid / 4;
        int minCellX = Math.floorDiv(blockX - reach, wreckGrid);
        int maxCellX = Math.floorDiv(blockX + reach, wreckGrid);
        int minCellZ = Math.floorDiv(blockZ - reach, wreckGrid);
        int maxCellZ = Math.floorDiv(blockZ + reach, wreckGrid);
        for (int cx = minCellX; cx <= maxCellX; cx++) {
            for (int cz = minCellZ; cz <= maxCellZ; cz++) {
                wreckInCell(cx, cz).filter(w -> {
                    long dx = (long) w.centerX() - blockX;
                    long dz = (long) w.centerZ() - blockZ;
                    return dx * dx + dz * dz <= (long) (range + 1) * (range + 1) || range <= 0;
                }).ifPresent(found::add);
            }
        }
        return found;
    }

    /**
     * Towers within range of a position.
     */
    public List<Tower> towersNear(int blockX, int blockZ, int range) {
        List<Tower> found = new ArrayList<>();
        int reach = range + towerGrid / 4;
        int minCellX = Math.floorDiv(blockX - reach, towerGrid);
        int maxCellX = Math.floorDiv(blockX + reach, towerGrid);
        int minCellZ = Math.floorDiv(blockZ - reach, towerGrid);
        int maxCellZ = Math.floorDiv(blockZ + reach, towerGrid);
        for (int cx = minCellX; cx <= maxCellX; cx++) {
            for (int cz = minCellZ; cz <= maxCellZ; cz++) {
                towerInCell(cx, cz).filter(t -> {
                    long dx = (long) t.centerX() - blockX;
                    long dz = (long) t.centerZ() - blockZ;
                    return dx * dx + dz * dz <= (long) (range + 1) * (range + 1) || range <= 0;
                }).ifPresent(found::add);
            }
        }
        return found;
    }
}
