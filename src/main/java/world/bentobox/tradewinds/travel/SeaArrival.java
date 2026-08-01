package world.bentobox.tradewinds.travel;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

import org.bukkit.Location;
import org.bukkit.World;

import world.bentobox.tradewinds.galaxy.GalaxyEngine;

/**
 * Finds open water to arrive on.
 * <p>
 * A warp used to land the player at a fixed distance from the island centre,
 * at sea level, without ever asking what was there - and the quay reaches past
 * that ring with its deck at exactly that height, so an unlucky bearing
 * materialised a sailor inside the decking ("suffocated in a wall").
 * <p>
 * The search asks the <b>galaxy</b>, not the world. Everything about the sea
 * floor and the island masks is a pure function of (seed, position), so
 * "is this open water?" can be answered by arithmetic - no block reads, and
 * therefore no chunk loading. The first cut did read blocks, which meant a
 * spiral scan out to 160 blocks could force the main thread to <em>generate</em>
 * dozens of chunks one after another before the teleport could even begin.
 *
 * @author tastybento
 */
public final class SeaArrival {

    /** How far to search outward for water before giving up, in blocks. */
    private static final int SEARCH_RADIUS = 160;
    /** Step between rings - a boat-width apart is fine and keeps this cheap. */
    private static final int STEP = 4;

    private SeaArrival() {
        // Static use only
    }

    /**
     * Candidate offsets around a point, nearest first. Pure and testable: the
     * ordering is what guarantees an arrival lands as close to its intended
     * spot as the terrain allows.
     *
     * @param radius how far out to search
     * @param step spacing between candidates
     * @return offsets ordered by distance from the origin
     */
    public static List<int[]> searchOffsets(int radius, int step) {
        List<int[]> offsets = new ArrayList<>();
        for (int dx = -radius; dx <= radius; dx += step) {
            for (int dz = -radius; dz <= radius; dz += step) {
                if (dx * dx + dz * dz <= radius * radius) {
                    offsets.add(new int[] { dx, dz });
                }
            }
        }
        offsets.sort(Comparator.comparingInt(o -> o[0] * o[0] + o[1] * o[1]));
        return offsets;
    }

    /**
     * Whether a column is open water, from the galaxy alone.
     * <p>
     * Two ways a column can fail: the sea floor plus any island lift reaches
     * the surface, or a dock or plaza has been terraformed over it - the quay
     * is solid to a block above sea level, which is exactly where an arrival
     * lands.
     *
     * @param engine the galaxy
     * @param x block x
     * @param z block z
     * @param seaLevel the sea surface Y
     * @return true if it is somewhere to arrive
     */
    public static boolean isOpenSea(GalaxyEngine engine, int x, int z, int seaLevel) {
        return engine.surfaceHeightAt(x, z) < seaLevel && engine.columnPlanAt(x, z).isEmpty();
    }

    /**
     * The nearest open water to an intended arrival point.
     *
     * @param engine the galaxy, or null for a world it does not describe (the
     *        interstice, which has no islands and no docks - its floor can
     *        never reach the surface, so the intended point always serves)
     * @param world the world to arrive in
     * @param x intended block x
     * @param z intended block z
     * @param seaLevel the sea surface Y
     * @return a location on open water
     */
    public static Location openSeaNear(GalaxyEngine engine, World world, int x, int z, int seaLevel) {
        if (engine == null) {
            return new Location(world, x + 0.5, seaLevel + 1.0, z + 0.5);
        }
        for (int[] offset : searchOffsets(SEARCH_RADIUS, STEP)) {
            int cx = x + offset[0];
            int cz = z + offset[1];
            if (isOpenSea(engine, cx, cz, seaLevel)) {
                return new Location(world, cx + 0.5, seaLevel + 1.0, cz + 0.5);
            }
        }
        // Nothing but land for 160 blocks: arrive above it rather than inside
        // it. Better a strange arrival than a suffocation.
        return new Location(world, x + 0.5, engine.surfaceHeightAt(x, z) + 1.0, z + 0.5);
    }
}
