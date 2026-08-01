package world.bentobox.tradewinds.travel;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.World;

/**
 * Finds open water to arrive on.
 * <p>
 * A warp used to land the player at a fixed distance from the island centre,
 * at sea level, without ever asking what was there. That was survivable while
 * coastlines were perfect circles of a known radius - and then ragged coasts
 * arrived, headlands started reaching past the arrival ring, and a warp could
 * materialise a sailor inside a hillside. The death message reads "suffocated
 * in a wall", which is not a sentence any player should have to interpret.
 * <p>
 * Arrivals are by boat, so the rule is simple: put them on open water, as near
 * the intended spot as possible.
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
     * Whether a column is open water a boat can float on: water at sea level,
     * and clear air above it for the sailor's head.
     *
     * @param world the world
     * @param x block x
     * @param z block z
     * @param seaHeight the sea surface Y
     * @return true if it is somewhere to arrive
     */
    public static boolean isOpenSea(World world, int x, int z, int seaHeight) {
        return world.getBlockAt(x, seaHeight, z).getType() == Material.WATER
                && world.getBlockAt(x, seaHeight + 1, z).isEmpty()
                && world.getBlockAt(x, seaHeight + 2, z).isEmpty();
    }

    /**
     * The nearest open water to an intended arrival point.
     *
     * @param world the world to arrive in
     * @param x intended block x
     * @param z intended block z
     * @param seaHeight the sea surface Y
     * @return a location on open water, or the intended point raised above the
     *         terrain if this whole area is somehow solid
     */
    public static Location openSeaNear(World world, int x, int z, int seaHeight) {
        for (int[] offset : searchOffsets(SEARCH_RADIUS, STEP)) {
            int cx = x + offset[0];
            int cz = z + offset[1];
            if (isOpenSea(world, cx, cz, seaHeight)) {
                return new Location(world, cx + 0.5, seaHeight + 1.0, cz + 0.5);
            }
        }
        // Nothing but land for 160 blocks: put them on top of it rather than
        // inside it. Better a strange arrival than a suffocation.
        return new Location(world, x + 0.5, world.getHighestBlockYAt(x, z) + 1.0, z + 0.5);
    }
}
