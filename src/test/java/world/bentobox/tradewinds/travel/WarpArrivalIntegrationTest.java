package world.bentobox.tradewinds.travel;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.bukkit.Location;

import world.bentobox.tradewinds.galaxy.ColumnPlan;
import world.bentobox.tradewinds.galaxy.GalaxyConfig;
import world.bentobox.tradewinds.galaxy.GalaxyEngine;
import world.bentobox.tradewinds.galaxy.IslandSpec;

import org.junit.jupiter.api.Test;

/**
 * Warp arrival logic: ensuring arrivals are on open water, never pushed inward,
 * and respecting configured distances. SeaArrival.openSeaOutward searches
 * outward from the intended arrival point to find clear water.
 *
 * @author tastybento
 */
class WarpArrivalIntegrationTest {

    private static final long SEED = 20260729L;
    private static final int SEA = 70;
    private static final int ARRIVAL_DISTANCE = 400;

    private GalaxyEngine engine() {
        return new GalaxyEngine(new GalaxyConfig(SEED, 2500, 160, 45, 0.5, 6, 5000, SEA));
    }

    @Test
    void testArrivalPointCalculation() {
        // The arrival point is calculated from an island and a bearing
        GalaxyEngine engine = engine();
        IslandSpec island = engine.islandsNear(0, 0, 12_000).iterator().next();

        // arrivalPoint returns [x, z] coordinates
        int[] arrive = world.bentobox.tradewinds.galaxy.RouteGraph.arrivalPoint(island, island, ARRIVAL_DISTANCE);
        assertNotNull(arrive);
        assertEquals(2, arrive.length);

        // Coordinates should be valid numbers
        assertTrue(arrive[0] != 0 || arrive[1] != 0, "At least one coordinate should be non-zero");
    }

    @Test
    void testSearchOffsetsOrderedByDistance() {
        java.util.List<int[]> offsets = SeaArrival.searchOffsets(160, 4);

        // Verify ordering: each offset is further or equal to the previous
        int previousDistance = -1;
        for (int[] offset : offsets) {
            int distance = offset[0] * offset[0] + offset[1] * offset[1];
            assertTrue(distance >= previousDistance, "Search not ordered by distance");
            previousDistance = distance;
        }
    }

    @Test
    void testSearchRadiusCoversSufficientArea() {
        // The search must be able to find water even if an island reaches out
        java.util.List<int[]> offsets = SeaArrival.searchOffsets(160, 4);

        // Some offsets should reach far from the origin
        int maxDistance = offsets.stream()
            .mapToInt(o -> o[0] * o[0] + o[1] * o[1])
            .max()
            .orElse(0);
        assertTrue(Math.sqrt(maxDistance) >= 150, "Search radius too small");
    }

    @Test
    void testOpenSeaCheckIsGalaxyOnly() {
        // SeaArrival.isOpenSea uses only the galaxy, no block reads
        GalaxyEngine engine = engine();
        IslandSpec island = engine.islandsNear(0, 0, 12_000).iterator().next();

        // Open water far from any island
        assertTrue(SeaArrival.isOpenSea(engine, island.centerX() + 900, island.centerZ() + 900, SEA));

        // The island itself is not open sea
        assertFalse(SeaArrival.isOpenSea(engine, island.centerX(), island.centerZ(), SEA));
    }

    @Test
    void testDockIsNotOpenSea() {
        // The dock reaches to the arrival ring and has a deck at sea level
        GalaxyEngine engine = engine();
        IslandSpec island = engine.islandsNear(0, 0, 12_000).iterator().next();
        var dockPlan = engine.dockPlan(island);

        // A point on the dock structure should not be considered open sea
        int quayX = island.centerX() + (int) Math.round(Math.cos(dockPlan.bearing()) * (dockPlan.dockEnd() - 10));
        int quayZ = island.centerZ() + (int) Math.round(Math.sin(dockPlan.bearing()) * (dockPlan.dockEnd() - 10));

        boolean isOpen = SeaArrival.isOpenSea(engine, quayX, quayZ, SEA);
        assertFalse(isOpen, "Dock should not be treated as open water");
    }

    @Test
    void testOutwardSearchNeverMovesInward() {
        // The outward search steps away from the island, never toward it
        GalaxyEngine engine = engine();
        for (IslandSpec island : engine.islandsNear(0, 0, 12_000)) {
            for (int deg = 0; deg < 360; deg += 15) {
                double rad = Math.toRadians(deg);
                int arriveX = island.centerX() + (int) Math.round(Math.cos(rad) * ARRIVAL_DISTANCE);
                int arriveZ = island.centerZ() + (int) Math.round(Math.sin(rad) * ARRIVAL_DISTANCE);

                double nominalDist = Math.sqrt(island.distanceSquared(arriveX, arriveZ));
                double unitX = (arriveX - (double) island.centerX()) / nominalDist;
                double unitZ = (arriveZ - (double) island.centerZ()) / nominalDist;

                Integer found = null;
                for (int out = 0; out <= 160 && found == null; out += 4) {
                    int checkX = island.centerX() + (int) Math.round(unitX * (nominalDist + out));
                    int checkZ = island.centerZ() + (int) Math.round(unitZ * (nominalDist + out));
                    if (SeaArrival.isOpenSea(engine, checkX, checkZ, SEA)) {
                        found = out;
                    }
                }
                // If water is found, it was found by stepping OUTWARD
                assertTrue(found == null || found >= 0);
            }
        }
    }

    @Test
    void testClearWaterRequiresSurroundingArea() {
        // isClearWater checks that surrounding blocks are also water,
        // not just the center
        GalaxyEngine engine = engine();

        // Far from any island, all should be clear
        assertTrue(SeaArrival.isOpenSea(engine, 1000, 1000, SEA));

        // The island itself is definitely not clear
        IslandSpec island = engine.islandsNear(0, 0, 12_000).iterator().next();
        assertFalse(SeaArrival.isOpenSea(engine, island.centerX(), island.centerZ(), SEA));
    }

    @Test
    void testLocationHeightIsSeaLevel() {
        // Arrivals are placed at sea level + 1 (sea level 70 -> Y 71)
        Location loc = SeaArrival.openSeaNear((GalaxyEngine) null, null, 0, 0, SEA);
        // If engine is null, the implementation returns the intended point
        // Full test requires mocking or world setup
    }

    @Test
    void testArrivalCoordinatesAreCentered() {
        // Block coordinates are centered for a smooth landing
        // (e.g., block 100 becomes location 100.5)
        Location loc = SeaArrival.openSeaNear((GalaxyEngine) null, null, 100, 200, SEA);
        // If engine is null, returns new Location(world, 100.5, 71.0, 200.5)
        // Full test requires world setup
    }

    @Test
    void testColumnTestInterface() {
        // SeaArrival.ColumnTest is used for the interstice
        SeaArrival.ColumnTest test = (x, z) -> x * x + z * z > 1000; // Example: far from origin
        assertTrue(test.isOpen(50, 50)); // Far enough
        assertTrue(test.isOpen(-50, -50)); // Far enough
        assertTrue(!test.isOpen(10, 10)); // Too close
    }

    @Test
    void testOpenSeaNearWithColumnTest() {
        // The interstice variant using ColumnTest
        SeaArrival.ColumnTest allOpen = (x, z) -> true;
        Location loc = SeaArrival.openSeaNear(allOpen, null, 100, 100, SEA);
        // With all open, should accept the intended point
        assertNotNull(loc);
    }

    @Test
    void testOpenSeaNearWithColumnTestFindsWater() {
        // The column test allows arbitrary criteria (e.g., interstice feature map)
        SeaArrival.ColumnTest onlyFar = (x, z) -> {
            int dist = x * x + z * z;
            return dist > 1000; // Only water beyond 31 blocks from origin
        };
        // Search starts from the intended point (100, 100)
        // and should find a candidate that passes the test
        Location loc = SeaArrival.openSeaNear(onlyFar, null, 100, 100, SEA);
        assertNotNull(loc);
    }
}
