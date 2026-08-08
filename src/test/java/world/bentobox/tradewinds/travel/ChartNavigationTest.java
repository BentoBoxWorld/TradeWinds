package world.bentobox.tradewinds.travel;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;

import org.junit.jupiter.api.Test;

import world.bentobox.tradewinds.ocean.DockPlan;
import world.bentobox.tradewinds.ocean.OceanConfig;
import world.bentobox.tradewinds.ocean.OceanEngine;
import world.bentobox.tradewinds.ocean.IslandSpec;
import world.bentobox.tradewinds.ocean.SecurityBand;

/**
 * Pure tests of the rower navigation math: hologram marker geometry and star
 * chart pixel mapping.
 *
 * @author tastybento
 */
class ChartNavigationTest {

    private final OceanEngine engine = new OceanEngine(new OceanConfig(77L, 2500, 160, 45, 1.0, 0, 5000, 70));

    @Test
    void testFuelRangeIsWhatTheFuelActuallyBuys() {
        // The chart draws the reachable set as a ring, so the radius has to be
        // the real thing: fuel divided by the per-block cost
        assertEquals(1000L, StarChartRenderer.fuelRangeBlocks(10, 0.01));
        assertEquals(0L, StarChartRenderer.fuelRangeBlocks(0, 0.01));
        // Floor, never round up - a ring you cannot quite reach is worse than none
        assertEquals(1L, StarChartRenderer.fuelRangeBlocks(1.99, 1.0));
        // Free warps are not a circle at all, so draw nothing
        assertEquals(0L, StarChartRenderer.fuelRangeBlocks(50, 0));
        assertEquals(0L, StarChartRenderer.fuelRangeBlocks(-5, 0.01));
    }

    @Test
    void testMapTextIsColouredWhite() {
        // MapCanvas takes its text colour from a palette-index prefix; without
        // one the names are a mid grey that vanishes against the ocean
        String coloured = StarChartRenderer.colored("Spawn");
        assertTrue(coloured.startsWith("\u00A7"), "Missing the map colour prefix");
        assertTrue(coloured.endsWith(";Spawn"), "The name must follow the prefix: " + coloured);
    }

    @Test
    void testDockMarkerPointsAtThePier() {
        // The chart answered "where is everywhere else" and said nothing about
        // the one bearing a sailor in these waters actually needs
        IslandSpec island = engine.islandInCell(1, 1).orElseThrow();
        DockPlan plan = engine.dockPlan(island);
        int pierX = island.centerX() + (int) Math.round(Math.cos(plan.bearing()) * plan.dockEnd());
        int pierZ = island.centerZ() + (int) Math.round(Math.sin(plan.bearing()) * plan.dockEnd());
        // Stand just off the island, not at the origin
        int px = island.centerX() + 300;
        int pz = island.centerZ() + 300;

        ChartHolograms.Marker marker = ChartHolograms.dockMarker(island, plan, px, pz, 10.0);
        assertTrue(marker.dock(), "The dock marker must be flagged so it is labelled and coloured as one");
        // On the ring, pointing at the pier - NOT at the island centre
        assertEquals(10.0, Math.hypot(marker.dx(), marker.dz()), 0.01);
        assertEquals(Math.atan2((double) pierZ - pz, (double) pierX - px),
                Math.atan2(marker.dz(), marker.dx()), 0.001);
        assertEquals((int) Math.hypot((double) pierX - px, (double) pierZ - pz), marker.distance());
        // Below the island names so it never collides with their stack, but
        // above the waterline - a sailor at sea level must not see it submerged
        assertTrue(marker.dy() < 2.5, "Dock marker should hang below the island markers");
        assertTrue(marker.dy() >= 1.0, "Dock marker must sit above the waterline, not in the sea");
    }

    @Test
    void testIslandMarkersAreNotDockMarkers() {
        IslandSpec spec = engine.islandInCell(1, 1).orElseThrow();
        ChartHolograms.markers(List.of(spec), 0, 0, 10.0, 12)
                .forEach(m -> assertTrue(!m.dock(), "Island markers must not be flagged as docks"));
    }

    @Test
    void testMarkersPointTheRightWay() {
        // Not cell 0,0 - that is the spawn island, directly under the player
        IslandSpec spec = engine.islandInCell(1, 1).orElseThrow();
        List<ChartHolograms.Marker> markers = ChartHolograms.markers(List.of(spec), 0, 0, 10.0, 12);
        assertEquals(1, markers.size());
        ChartHolograms.Marker marker = markers.get(0);
        // On the 10-block ring
        assertEquals(10.0, Math.hypot(marker.dx(), marker.dz()), 0.01);
        // Pointing at the island: same bearing as the island itself
        double markerBearing = Math.atan2(marker.dz(), marker.dx());
        double islandBearing = Math.atan2(spec.centerZ(), spec.centerX());
        assertEquals(islandBearing, markerBearing, 0.001);
        assertEquals((int) Math.hypot(spec.centerX(), spec.centerZ()), marker.distance());
    }

    @Test
    void testSharedBearingStacksByDistance() {
        // Two islands due east at different ranges: nearest sits lowest
        IslandSpec near = new IslandSpec(0, 0, 3000, 0, world.bentobox.tradewinds.ocean.IslandType.FISHING,
                SecurityBand.SAFE, "minecraft:beach", "Nearby");
        IslandSpec far = new IslandSpec(1, 0, 8000, 0, world.bentobox.tradewinds.ocean.IslandType.MINING,
                SecurityBand.SAFE, "minecraft:stony_peaks", "Distant");
        List<ChartHolograms.Marker> markers = ChartHolograms.markers(List.of(far, near), 0, 0, 10.0, 12);
        assertEquals("Nearby", markers.get(0).island().name());
        assertTrue(markers.get(0).dy() < markers.get(1).dy(),
                "Nearest island should sit below the further one on the same bearing");
    }

    @Test
    void testMarkerCap() {
        List<IslandSpec> many = new java.util.ArrayList<>();
        for (int cx = -3; cx <= 3; cx++) {
            for (int cz = -3; cz <= 3; cz++) {
                engine.islandInCell(cx, cz).ifPresent(many::add);
            }
        }
        assertTrue(many.size() > 12);
        assertEquals(12, ChartHolograms.markers(many, 0, 0, 10.0, 12).size());
    }

    @Test
    void testSpawnIslandIsUnderfoot() {
        // The spawn island sits at the origin: zero distance, no bearing
        IslandSpec spawn = engine.spawnIsland();
        assertEquals(0, spawn.centerX());
        assertEquals(0, spawn.centerZ());
    }

    @Test
    void testStarChartPixels() {
        // On-map island: simple scaled offset from center
        int[] pixel = StarChartRenderer.toPixel(640, -1280, 64);
        assertEquals(64 + 10, pixel[0]);
        assertEquals(64 - 20, pixel[1]);
        assertEquals(0, pixel[2]);
        // Beyond the map: clamped to the edge, flagged
        int[] edge = StarChartRenderer.toPixel(64 * 200, 0, 64);
        assertEquals(1, edge[2]);
        assertEquals(64 + 60, edge[0]);
        assertEquals(64, edge[1]);
        // Diagonal clamp keeps direction
        int[] diag = StarChartRenderer.toPixel(64 * 200, 64 * 200, 64);
        assertEquals(1, diag[2]);
        assertEquals(64 + 60, diag[0]);
        assertEquals(64 + 60, diag[1]);
    }

    @Test
    void testBandColorsDistinct() {
        long distinct = java.util.Arrays.stream(SecurityBand.values()).map(StarChartRenderer::bandColor).distinct()
                .count();
        assertEquals(SecurityBand.values().length, distinct);
    }
}
