package world.bentobox.tradewinds.travel;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;

import org.junit.jupiter.api.Test;

import world.bentobox.tradewinds.galaxy.ColumnPlan;
import world.bentobox.tradewinds.galaxy.GalaxyConfig;
import world.bentobox.tradewinds.galaxy.GalaxyEngine;
import world.bentobox.tradewinds.galaxy.IslandSpec;
import world.bentobox.tradewinds.galaxy.RouteGraph;

/**
 * Why warp arrivals have to look before they land.
 *
 * @author tastybento
 */
class SeaArrivalTest {

    private static final long SEED = 20260729L;
    private static final int SEA = 70;
    private static final int ARRIVAL_DISTANCE = 130;

    private GalaxyEngine engine() {
        return new GalaxyEngine(new GalaxyConfig(SEED, 2500, 160, 45, 0.5, 6, 5000, SEA));
    }

    @Test
    void testTheArrivalRingCanLandOnTheQuay() {
        // The bug behind "suffocated in a wall". The arrival point is a fixed
        // 130 blocks from an island's centre at sea level + 1 - and the quay
        // runs out to 136 blocks with its plank deck at exactly that height.
        // Line the approach bearing up with the dock bearing and the warp
        // materialises the sailor inside the decking.
        //
        // Natural land is NOT the culprit: the coast stops short of the ring.
        // It is the one structure that deliberately reaches past it.
        GalaxyEngine engine = engine();
        int onQuay = 0;
        int onLand = 0;
        int tested = 0;
        for (IslandSpec island : engine.islandsNear(0, 0, 12_000)) {
            for (int deg = 0; deg < 360; deg += 5) {
                double rad = Math.toRadians(deg);
                IslandSpec from = new IslandSpec(0, 0,
                        island.centerX() + (int) Math.round(Math.cos(rad) * 5000),
                        island.centerZ() + (int) Math.round(Math.sin(rad) * 5000), island.type(),
                        island.band(), island.biomeKey(), "approach");
                int[] arrive = RouteGraph.arrivalPoint(from, island, ARRIVAL_DISTANCE);
                tested++;
                if (engine.columnPlanAt(arrive[0], arrive[1])
                        .filter(plan -> plan.feature() == ColumnPlan.Feature.DOCK).isPresent()) {
                    onQuay++;
                }
                if (engine.surfaceHeightAt(arrive[0], arrive[1]) > SEA) {
                    onLand++;
                }
            }
        }
        assertTrue(tested > 0, "No islands to test");
        assertTrue(onQuay > 0, "No arrival point lands on a quay - has the dock or arrival distance moved?");
        // The deck sits exactly at the arrival height, which is what turned a
        // rare unlucky bearing into a death
        assertEquals(SEA + 1, SEA + GalaxyEngine.DOCK_RISE);
        // And confirm the thing that is NOT to blame, so nobody re-fixes it
        assertEquals(0, onLand, "Natural land now reaches the arrival ring too - widen the search");
    }

    @Test
    void testSearchOffsetsAreOrderedNearestFirst() {
        // The ordering is the guarantee that an arrival lands as close to its
        // intended spot as the terrain allows
        List<int[]> offsets = SeaArrival.searchOffsets(32, 4);
        assertEquals(0, offsets.get(0)[0]);
        assertEquals(0, offsets.get(0)[1]);
        int previous = -1;
        for (int[] offset : offsets) {
            int distance = offset[0] * offset[0] + offset[1] * offset[1];
            assertTrue(distance >= previous, "Search order jumped backwards");
            previous = distance;
        }
        // Every candidate is inside the search radius
        offsets.forEach(o -> assertTrue(o[0] * o[0] + o[1] * o[1] <= 32 * 32));
        assertTrue(offsets.size() > 100, "Too few candidates to find water: " + offsets.size());
    }

    @Test
    void testSearchIsWideEnoughToClearAnIsland() {
        // A headland can reach well past the arrival ring, so the search has to
        // be able to get back out to open water from inside one
        List<int[]> offsets = SeaArrival.searchOffsets(160, 4);
        int furthest = offsets.stream().mapToInt(o -> o[0] * o[0] + o[1] * o[1]).max().orElse(0);
        assertTrue(Math.sqrt(furthest) >= 150, "Search radius is smaller than an island");
    }
}
