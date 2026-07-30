package world.bentobox.tradewinds.galaxy;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.Map;

import org.junit.jupiter.api.Test;

/**
 * Pure tests of warp route costs and arrival geometry.
 *
 * @author tastybento
 */
class RouteGraphTest {

    private final GalaxyEngine engine = new GalaxyEngine(new GalaxyConfig(42L, 2500, 160, 45, 1.0, 0, 5000, 70));
    private final IslandSpec a = engine.islandInCell(0, 0).orElseThrow();
    private final IslandSpec b = engine.islandInCell(2, 1).orElseThrow();

    @Test
    void testDistanceCost() {
        RouteGraph graph = new RouteGraph(0.01, Map.of());
        double dist = Math.sqrt(a.distanceSquared(b.centerX(), b.centerZ()));
        assertEquals((int) Math.ceil(dist * 0.01), graph.cost(a, b));
        // Symmetric
        assertEquals(graph.cost(a, b), graph.cost(b, a));
        // Never free
        assertTrue(new RouteGraph(0.0000001, Map.of()).cost(a, b) >= 1);
    }

    @Test
    void testEdgeKeyIsDirectionIndependent() {
        assertEquals(RouteGraph.edgeKey(a, b), RouteGraph.edgeKey(b, a));
    }

    @Test
    void testEdgeOverride() {
        RouteGraph graph = new RouteGraph(0.01, Map.of(RouteGraph.edgeKey(a, b), 3.0));
        assertEquals(3, graph.cost(a, b));
        assertEquals(3, graph.cost(b, a));
        // Other edges unaffected
        IslandSpec c = engine.islandInCell(-3, 4).orElseThrow();
        double dist = Math.sqrt(a.distanceSquared(c.centerX(), c.centerZ()));
        assertEquals((int) Math.ceil(dist * 0.01), graph.cost(a, c));
    }

    @Test
    void testArrivalPoint() {
        int[] p = RouteGraph.arrivalPoint(a, b, 130);
        // 130 blocks from the destination center (inside view distance)...
        double distFromDest = Math.sqrt(b.distanceSquared(p[0], p[1]));
        assertEquals(130, distFromDest, 2.0);
        // ...on the origin side: closer to the origin than the destination center is
        double destToOrigin = Math.sqrt(b.distanceSquared(a.centerX(), a.centerZ()));
        double arrivalToOrigin = Math.sqrt(a.distanceSquared(p[0], p[1]));
        assertEquals(destToOrigin - 130, arrivalToOrigin, 2.0);
    }
}
