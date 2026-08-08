package world.bentobox.tradewinds.ocean;

/**
 * The ocean floor as a pure function of (seed, position) - no Bukkit, unit
 * tested headlessly like the rest of the ocean (spec principle 5).
 * <p>
 * Four fields stack to make a sea worth diving in:
 * <ol>
 * <li><b>Basins</b> - a broad, slow field taking the floor from sunlit shelf to
 * abyssal plain over a few thousand blocks. This is the one that matters most:
 * depth picks the ocean biome, and the biome is what lets vanilla decide where
 * monuments, ruins and coral belong.</li>
 * <li><b>Relief</b> - rolling hills and dunes at a scale you notice while
 * swimming.</li>
 * <li><b>Rifts</b> - ridged noise folded to a thin line, cutting narrow
 * canyons that wander across the floor and drop away sharply.</li>
 * <li><b>Seamounts</b> - underwater peaks rising off the deeper plains, capped
 * so they never break the surface (the ocean's islands are the world's only
 * land, spec principle 6).</li>
 * </ol>
 * Near land all of that is blended away toward a standard island shelf, so an
 * island that happens to fall over an abyssal plain still sits in shallow water
 * and still breaks the surface by the same amount.
 *
 * @author tastybento
 */
public class Seabed {

    // Independent fields, each with its own salt
    private static final long SALT_BASIN = 0x5EABED01L;
    private static final long SALT_RELIEF = 0x5EABED02L;
    private static final long SALT_RIFT = 0x5EABED03L;
    private static final long SALT_SEAMOUNT = 0x5EABED04L;
    private static final long SALT_SEDIMENT = 0x5EABED05L;
    private static final long SALT_DUNE = 0x5EABED06L;

    /** Scale of the basins, in blocks: a long swim from shelf to deep water. */
    private static final int BASIN_LATTICE = 2200;
    /** Scale of the rolling relief, in blocks. */
    private static final int RELIEF_LATTICE = 220;
    /** Scale of the rift network, in blocks. */
    private static final int RIFT_LATTICE = 900;
    /** Scale of the seamount field, in blocks. */
    private static final int SEAMOUNT_LATTICE = 1400;
    /** Scale of the sediment patches (sand, gravel, clay), in blocks. */
    private static final int SEDIMENT_LATTICE = 26;
    /** Scale of the short dunes - visible within one glance down from a boat. */
    private static final int DUNE_LATTICE = 32;

    /**
     * Minimum water depth over open sea. Seamounts and shoals stop here so no
     * accidental land appears between the islands.
     */
    private static final int MINIMUM_WATER = 4;

    private final long seed;
    private final SeabedConfig config;
    private final int seaLevel;

    public Seabed(long seed, int seaLevel, SeabedConfig config) {
        this.seed = seed;
        this.seaLevel = seaLevel;
        this.config = config == null ? SeabedConfig.DEFAULT : config;
    }

    public SeabedConfig getConfig() {
        return config;
    }

    /**
     * The natural depth of the open sea at a column, ignoring land: the basin
     * field plus relief, with rifts cut and seamounts raised.
     *
     * @param blockX block x
     * @param blockZ block z
     * @return depth in blocks below sea level, at least {@link #MINIMUM_WATER}
     */
    public double openSeaDepth(int blockX, int blockZ) {
        return depthAt(blockX, blockZ, 0);
    }

    /**
     * Water depth at a column, given how much of an island's shelf it is on.
     * <p>
     * Only the <em>basin</em> is levelled toward the island shelf - that is all
     * the guarantee needs, and levelling everything is what made the shallows
     * around an island a perfectly circular pale ring in play. The rolling
     * relief carries on across the shelf (at half strength, so the guarantee
     * still holds); rifts and seamounts fade out, since a canyon through an
     * island's anchorage helps nobody.
     *
     * @param blockX block x
     * @param blockZ block z
     * @param shelfBlend 0 in open ocean, 1 on an island's own shelf
     * @return depth in blocks below sea level
     */
    private double depthAt(int blockX, int blockZ, double shelfBlend) {
        double blend = Math.clamp(shelfBlend, 0, 1);
        double basin = basinAt(blockX, blockZ);
        double depth = config.shelfDepth() + basin * (config.abyssDepth() - config.shelfDepth());
        depth += (config.islandShelfDepth() - depth) * blend;
        // Rolling relief, centered so it neither raises nor lowers on average
        double relief = (Noise.fbm(seed, SALT_RELIEF, blockX, blockZ, RELIEF_LATTICE, 3, 0.5) - 0.5) * 2
                * config.relief();
        depth += relief * (1 - blend / 2);
        // Short dunes: the 220-block relief swells read as dead flat from a
        // boat ("I don't see the sea floor undulating", 2026-08-07); these
        // are the waves you can actually watch roll under the hull
        if (config.duneHeight() > 0) {
            double dune = (Noise.fbm(seed, SALT_DUNE, blockX, blockZ, DUNE_LATTICE, 2, 0.5) - 0.5) * 2
                    * config.duneHeight();
            depth += dune * (1 - blend / 2);
        }
        depth += riftCut(blockX, blockZ) * (1 - blend);
        depth -= seamountRise(blockX, blockZ, basin) * (1 - blend);
        // Open water everywhere: a shoal that reached the surface would be land
        // the ocean never placed (spec principle 6)
        return Math.max(MINIMUM_WATER, depth);
    }

    /**
     * The basin field at a column: 0 on the shallowest banks, 1 on the deepest
     * plains.
     * <p>
     * Stacking octaves pulls values toward the middle, which left the first cut
     * of this a narrow band of samey mid-depth water - the "barren and
     * repetitive" sea floor. Stretching the field about its midpoint and then
     * easing it restores real shelves and real abyss, with the transition
     * between them happening over a slope rather than everywhere at once.
     *
     * @param blockX block x
     * @param blockZ block z
     * @return value in [0, 1]
     */
    double basinAt(int blockX, int blockZ) {
        double raw = Noise.fbm(seed, SALT_BASIN, blockX, blockZ, BASIN_LATTICE, 3, 0.5);
        double stretched = Math.clamp((raw - 0.5) * 2.0 + 0.5, 0.0, 1.0);
        return stretched * stretched * (3 - 2 * stretched);
    }

    /**
     * How much deeper a rift makes this column. Zero outside the rift network;
     * inside, it eases in from the threshold so canyon walls are steep but not
     * a sheer one-block cliff.
     *
     * @param blockX block x
     * @param blockZ block z
     * @return extra depth in blocks, 0 or more
     */
    double riftCut(int blockX, int blockZ) {
        if (config.riftDepth() <= 0 || config.riftThreshold() >= 1.0) {
            return 0;
        }
        double ridge = Noise.ridge(seed, SALT_RIFT, blockX, blockZ, RIFT_LATTICE);
        if (ridge <= config.riftThreshold()) {
            return 0;
        }
        double t = (ridge - config.riftThreshold()) / (1.0 - config.riftThreshold());
        // Squared so the canyon floor is narrow and its walls fall away fast
        return config.riftDepth() * t * t;
    }

    /**
     * How high a seamount rises here. They only grow out of the deeper half of
     * the basin field - a pinnacle on a shallow bank would just be an island.
     *
     * @param blockX block x
     * @param blockZ block z
     * @param basin the basin field value at this column, 0 shallow to 1 deep
     * @return height in blocks, 0 or more
     */
    double seamountRise(int blockX, int blockZ, double basin) {
        if (config.seamountHeight() <= 0 || basin < 0.5) {
            return 0;
        }
        double ridge = Noise.ridge(seed, SALT_SEAMOUNT, blockX, blockZ, SEAMOUNT_LATTICE);
        if (ridge <= 0.75) {
            return 0;
        }
        double t = (ridge - 0.75) / 0.25;
        // Fade in with basin depth too, so peaks sit on the plains not the edges
        return config.seamountHeight() * t * t * Math.clamp((basin - 0.5) * 2, 0, 1);
    }

    /**
     * The floor's top Y at a column, given how close it is to land.
     *
     * @param blockX block x
     * @param blockZ block z
     * @param shelfBlend 0 in open ocean, 1 on an island's own shelf; between
     *        the two the natural floor eases toward the island shelf depth
     * @return the Y of the topmost floor block
     */
    public int heightAt(int blockX, int blockZ, double shelfBlend) {
        return seaLevel - (int) Math.round(depthAt(blockX, blockZ, shelfBlend));
    }

    /**
     * The shallowest the floor can ever be in open water - the generator uses
     * this to know that open sea is always open sea.
     *
     * @return minimum floor Y
     */
    public int maxFloorY() {
        return seaLevel - MINIMUM_WATER;
    }

    /**
     * The deepest the floor can ever be, so the generator knows how much solid
     * rock to lay down underneath it.
     *
     * @return minimum floor Y
     */
    public int minFloorY() {
        return seaLevel - config.maxDepth() - config.relief() - config.duneHeight();
    }

    /**
     * A slow patch field used to pick sediment: sand banks, gravel beds and
     * clay pans in broad patches rather than block-by-block static.
     *
     * @param blockX block x
     * @param blockZ block z
     * @return value in [0, 1]
     */
    public double sedimentAt(int blockX, int blockZ) {
        return Noise.fbm(seed, SALT_SEDIMENT, blockX, blockZ, SEDIMENT_LATTICE, 2, 0.5);
    }
}
