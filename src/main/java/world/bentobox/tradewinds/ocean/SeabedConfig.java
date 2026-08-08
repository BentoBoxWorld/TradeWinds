package world.bentobox.tradewinds.ocean;

/**
 * Shape of the ocean floor. All depths are in blocks below sea level, so the
 * numbers read the way a chart does: bigger means deeper water.
 *
 * @param shelfDepth depth of the shallowest open water - sunlit banks where
 *        coral, kelp and ocean ruins sit
 * @param abyssDepth depth of the deepest basins - dark water, and where ocean
 *        monuments can generate
 * @param islandShelfDepth depth of the shelf every trading island and islet
 *        sits on; the natural floor is blended toward this near land so islands
 *        always break the surface by the same amount wherever the basins fall
 * @param relief amplitude in blocks of the rolling hills riding on top of the
 *        basin field - the difference between a dune field and a flat plain
 * @param riftDepth how far below the surrounding floor a rift cuts at its
 *        deepest, in blocks; 0 disables rifts
 * @param riftThreshold ridge value (0-1) above which a rift starts to open -
 *        higher means rarer and narrower canyons
 * @param seamountHeight how far a seamount rises above the surrounding floor at
 *        its peak, in blocks; 0 disables them. Seamounts never break the
 *        surface - the generator keeps open water above them
 * @param duneHeight amplitude in blocks of short dunes riding on everything
 *        else - the wavelength you can SEE roll while looking down from a
 *        boat, where the relief field's 220-block swells read as flat; 0
 *        disables them (and keeps pre-dune worlds' terrain unchanged)
 *
 * @author tastybento
 */
public record SeabedConfig(int shelfDepth, int abyssDepth, int islandShelfDepth, int relief, int riftDepth,
        double riftThreshold, int seamountHeight, int duneHeight) {

    /** The default sea floor: shelves at 14 down to basins at 46. */
    public static final SeabedConfig DEFAULT = new SeabedConfig(14, 46, 18, 9, 26, 0.80, 20);

    /**
     * The pre-dune shape: everything but dunes, dunes off. Every caller
     * predating 2026-08-07 uses this form, and their worlds' floors must not
     * shift under already-generated chunks.
     */
    public SeabedConfig(int shelfDepth, int abyssDepth, int islandShelfDepth, int relief, int riftDepth,
            double riftThreshold, int seamountHeight) {
        this(shelfDepth, abyssDepth, islandShelfDepth, relief, riftDepth, riftThreshold, seamountHeight, 0);
    }

    /**
     * A flat floor at a fixed depth - the pre-Stage-6 ocean, and what
     * {@code world.seabed.vary: false} gives you.
     *
     * @param depth depth below sea level
     * @return a featureless seabed config
     */
    public static SeabedConfig flat(int depth) {
        return new SeabedConfig(depth, depth, depth, 0, 0, 1.0, 0);
    }

    public SeabedConfig {
        // A floor that rises above the waves would make land outside the
        // ocean's islands (spec principle 6), so depths are kept positive and
        // ordered shallow-to-deep
        shelfDepth = Math.max(1, shelfDepth);
        abyssDepth = Math.max(shelfDepth, abyssDepth);
        islandShelfDepth = Math.max(1, islandShelfDepth);
        relief = Math.max(0, relief);
        riftDepth = Math.max(0, riftDepth);
        riftThreshold = Math.clamp(riftThreshold, 0.0, 1.0);
        seamountHeight = Math.max(0, seamountHeight);
        duneHeight = Math.max(0, duneHeight);
    }

    /**
     * The deepest the floor can ever be, in blocks below sea level - a rift cut
     * into the deepest basin. The generator fills solid rock below this.
     *
     * @return maximum depth in blocks
     */
    public int maxDepth() {
        return abyssDepth + riftDepth;
    }
}
