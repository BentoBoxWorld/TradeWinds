package world.bentobox.tradewinds.galaxy;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.OptionalInt;

/**
 * The interstice's features, as pure seeded geometry: wart shoals (small
 * soul-sand banks breaking the dark sea) and wither watchtowers (its only
 * structure). Everything is a pure function of (seed, position) - no Bukkit
 * imports, headlessly testable - mirroring how the galaxy treats wild islets
 * (spec principle 5).
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

    private final long seed;
    private final int shoalGrid;
    private final double shoalChance;
    private final int shoalRadius;
    private final double grandShoalChance;
    private final int towerGrid;
    private final double towerChance;

    public IntersticeMap(long seed, int shoalGrid, double shoalChance, int shoalRadius,
            double grandShoalChance, int towerGrid, double towerChance) {
        this.seed = seed;
        this.shoalGrid = Math.max(64, shoalGrid);
        this.shoalChance = shoalChance;
        this.shoalRadius = shoalRadius;
        this.grandShoalChance = grandShoalChance;
        this.towerGrid = Math.max(256, towerGrid);
        this.towerChance = towerChance;
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
