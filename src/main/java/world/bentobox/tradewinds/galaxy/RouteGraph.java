package world.bentobox.tradewinds.galaxy;

import java.util.Map;

/**
 * Warp route costs between islands. Default cost is Euclidean distance times
 * the fuel-per-block multiplier; per-edge overrides (config) create cheap
 * "warp lanes" or expensive frontiers without regenerating the world - the
 * lane is the in-fiction excuse for any cost/distance mismatch. Pure math, no
 * Bukkit.
 *
 * @author tastybento
 */
public class RouteGraph {

    private final double fuelPerBlock;
    private final Map<String, Double> edgeOverrides;

    /**
     * @param fuelPerBlock fuel units per block of route distance
     * @param edgeOverrides absolute fuel cost per edge key (see {@link #edgeKey}),
     *        applied in both directions
     */
    public RouteGraph(double fuelPerBlock, Map<String, Double> edgeOverrides) {
        this.fuelPerBlock = fuelPerBlock;
        this.edgeOverrides = Map.copyOf(edgeOverrides);
    }

    /**
     * Canonical config key for an edge between two islands, direction-independent:
     * the lexicographically smaller "cellX,cellZ" first, joined by '>'.
     */
    public static String edgeKey(IslandSpec a, IslandSpec b) {
        String ka = a.cellX() + "," + a.cellZ();
        String kb = b.cellX() + "," + b.cellZ();
        return ka.compareTo(kb) <= 0 ? ka + ">" + kb : kb + ">" + ka;
    }

    /**
     * Fuel cost of warping between two islands, in whole fuel units (rounded up,
     * minimum 1).
     *
     * @param from origin island
     * @param to destination island
     * @return fuel units
     */
    public int cost(IslandSpec from, IslandSpec to) {
        Double override = edgeOverrides.get(edgeKey(from, to));
        double cost = override != null ? override
                : Math.sqrt(from.distanceSquared(to.centerX(), to.centerZ())) * fuelPerBlock;
        return Math.max(1, (int) Math.ceil(cost));
    }

    /**
     * Warp arrival point: just inside the destination's border, on the bearing
     * of the origin island - you arrive on the side you notionally came from.
     *
     * @param from origin island
     * @param to destination island
     * @param islandRange the island space radius (border distance)
     * @param margin how far inside the border to arrive
     * @return {x, z} block position
     */
    public static int[] arrivalPoint(IslandSpec from, IslandSpec to, int islandRange, int margin) {
        double dx = (double) from.centerX() - to.centerX();
        double dz = (double) from.centerZ() - to.centerZ();
        double len = Math.max(1.0, Math.hypot(dx, dz));
        double dist = (double) islandRange - margin;
        return new int[] { to.centerX() + (int) Math.round(dx / len * dist),
                to.centerZ() + (int) Math.round(dz / len * dist) };
    }
}
