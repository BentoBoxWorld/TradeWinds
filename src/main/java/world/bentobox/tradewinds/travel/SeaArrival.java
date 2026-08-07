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
    /** How much clear water an arrival wants around it, in blocks. */
    private static final int CLEARANCE = 8;

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
     * Open water at or beyond an intended arrival distance, stepping outward
     * along the approach bearing.
     * <p>
     * Warps arrive on a ring a fixed distance from the island centre, and a
     * ragged coast can reach that far - so the nominal point can land on the
     * beach. Searching in every direction would happily fix that by moving the
     * sailor <em>inward</em> into a bay, which is how a warp ends up putting
     * someone on top of an island. Outward is the only direction that helps.
     *
     * @param engine the galaxy
     * @param world the world to arrive in
     * @param centerX island centre x
     * @param centerZ island centre z
     * @param x intended block x
     * @param z intended block z
     * @param seaLevel the sea surface Y
     * @return a location on open water
     */
    public static Location openSeaOutward(GalaxyEngine engine, World world, int centerX, int centerZ, int x,
            int z, int seaLevel) {
        double dx = (double) x - centerX;
        double dz = (double) z - centerZ;
        double length = Math.hypot(dx, dz);
        if (engine == null || length < 1) {
            return openSeaNear(engine, world, x, z, seaLevel);
        }
        double ux = dx / length;
        double uz = dz / length;
        for (int out = 0; out <= SEARCH_RADIUS; out += STEP) {
            int cx = centerX + (int) Math.round(ux * (length + out));
            int cz = centerZ + (int) Math.round(uz * (length + out));
            if (isClearWater(engine, cx, cz, seaLevel)) {
                return new Location(world, cx + 0.5, seaLevel + 1.0, cz + 0.5);
            }
        }
        return openSeaNear(engine, world, x, z, seaLevel);
    }

    /**
     * Open water with elbow room - not a one-block puddle between two
     * headlands, which is technically water and no use to a boat.
     */
    private static boolean isClearWater(GalaxyEngine engine, int x, int z, int seaLevel) {
        if (!isOpenSea(engine, x, z, seaLevel)) {
            return false;
        }
        for (int dx = -CLEARANCE; dx <= CLEARANCE; dx += CLEARANCE) {
            for (int dz = -CLEARANCE; dz <= CLEARANCE; dz += CLEARANCE) {
                if (!isOpenSea(engine, x + dx, z + dz, seaLevel)) {
                    return false;
                }
            }
        }
        return true;
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

    /** A column test for worlds the galaxy does not describe. */
    public interface ColumnTest {
        boolean isOpen(int x, int z);
    }

    /**
     * The nearest open water by an arbitrary column test - the interstice's
     * variant. Its floor could never reach the surface, so a stranding used
     * to take the intended point unexamined; wart shoals and watchtowers
     * (interstice plan) ended that innocence.
     *
     * @param open the column test
     * @param world the world to arrive in
     * @param x intended block x
     * @param z intended block z
     * @param seaLevel the sea surface Y
     * @return a location on open water (the intended point if the search
     *         finds nothing, which shoal densities keep effectively impossible)
     */
    public static Location openSeaNear(ColumnTest open, World world, int x, int z, int seaLevel) {
        for (int[] offset : searchOffsets(SEARCH_RADIUS, STEP)) {
            int cx = x + offset[0];
            int cz = z + offset[1];
            if (open.isOpen(cx, cz)) {
                return new Location(world, cx + 0.5, seaLevel + 1.0, cz + 0.5);
            }
        }
        return new Location(world, x + 0.5, seaLevel + 1.0, z + 0.5);
    }
}
