package world.bentobox.tradewinds.travel;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import world.bentobox.tradewinds.CommonTestSetup;
import world.bentobox.tradewinds.Settings;
import world.bentobox.tradewinds.TradeWinds;
import world.bentobox.tradewinds.dataobjects.PlayerDataManager;
import world.bentobox.tradewinds.dataobjects.TWPlayerData;
import world.bentobox.tradewinds.ocean.OceanConfig;
import world.bentobox.tradewinds.ocean.OceanEngine;
import world.bentobox.tradewinds.ocean.IslandSpec;
import world.bentobox.tradewinds.ocean.RouteGraph;

/**
 * Tests the warp dialog's destination selection: charted-only, origin
 * excluded, nearest first, affordability flags, capped.
 *
 * @author tastybento
 */
class WarpSelectionTest extends CommonTestSetup {

    private static final long SEED = 2026L;

    private TradeWinds addon;
    private OceanEngine engine;
    private WarpService service;
    private TWPlayerData data;
    private IslandSpec origin;

    @Override
    @BeforeEach
    public void setUp() throws Exception {
        super.setUp();
        addon = mock(TradeWinds.class);
        Settings settings = new Settings();
        when(addon.getSettings()).thenReturn(settings);
        engine = new OceanEngine(new OceanConfig(SEED, 2500, 160, 45, 1.0, 0, 5000, 70));
        when(addon.getOceanEngine(anyLong())).thenReturn(engine);
        when(addon.getOverWorld()).thenReturn(world);
        when(world.getSeed()).thenReturn(SEED);
        when(addon.getRouteGraph()).thenReturn(new RouteGraph(0.01, Map.of()));
        // No claimed island: the home pin (Stage 7b) stays out of these tests
        when(addon.getIslands()).thenReturn(im);
        PlayerDataManager pdm = mock(PlayerDataManager.class);
        when(addon.getPlayerDataManager()).thenReturn(pdm);
        data = new TWPlayerData(uuid.toString());
        when(pdm.get(uuid)).thenReturn(data);
        when(mockPlayer.getUniqueId()).thenReturn(uuid);
        service = new WarpService(addon);
        origin = engine.islandInCell(0, 0).orElseThrow();
    }

    @Test
    void testOnlyChartedAndNotOrigin() {
        // Nothing charted -> nothing offered
        assertTrue(service.destinations(mockPlayer, origin, 1000).isEmpty());
        // Chart the origin and two neighbors
        data.chart(origin);
        IslandSpec near = engine.islandInCell(0, 1).orElseThrow();
        IslandSpec far = engine.islandInCell(5, 5).orElseThrow();
        data.chart(near);
        data.chart(far);
        List<WarpService.Destination> list = service.destinations(mockPlayer, origin, 1000);
        assertEquals(2, list.size());
        // Origin never offered; nearest first
        assertEquals(near, list.get(0).island());
        assertEquals(far, list.get(1).island());
    }

    @Test
    void testAffordability() {
        IslandSpec near = engine.islandInCell(0, 1).orElseThrow();
        data.chart(near);
        RouteGraph graph = new RouteGraph(0.01, Map.of());
        int cost = graph.cost(origin, near);
        List<WarpService.Destination> rich = service.destinations(mockPlayer, origin, cost);
        assertTrue(rich.get(0).affordable());
        assertEquals(cost, rich.get(0).fuelCost());
        List<WarpService.Destination> poor = service.destinations(mockPlayer, origin, cost - 1.0);
        assertFalse(poor.get(0).affordable());
    }

    @Test
    void testDestinationCap() {
        // Chart a large area
        for (int cx = -4; cx <= 4; cx++) {
            for (int cz = -4; cz <= 4; cz++) {
                engine.islandInCell(cx, cz).ifPresent(data::chart);
            }
        }
        List<WarpService.Destination> list = service.destinations(mockPlayer, origin, 100000);
        assertEquals(addon.getSettings().getMaxWarpDestinations(), list.size());
        // Sorted by distance from the origin
        for (int i = 1; i < list.size(); i++) {
            assertTrue(list.get(i - 1).island().distanceSquared(origin.centerX(), origin.centerZ())
                    <= list.get(i).island().distanceSquared(origin.centerX(), origin.centerZ()));
        }
    }

    @org.junit.jupiter.api.Test
    void testArrivalYawFacesTheTarget() {
        // Minecraft yaw: 0 = south (+Z), -90 = east (+X), 90 = west, ±180 = north
        assertEquals(0.0f, WarpService.yawToward(0, 0, 0, 100), 0.01f);
        assertEquals(-90.0f, WarpService.yawToward(0, 0, 100, 0), 0.01f);
        assertEquals(90.0f, WarpService.yawToward(0, 0, -100, 0), 0.01f);
        assertEquals(180.0f, Math.abs(WarpService.yawToward(0, 0, 0, -100)), 0.01f);
        // A diagonal: north-east is -135
        assertEquals(-135.0f, WarpService.yawToward(0, 0, 100, -100), 0.01f);
    }

    @org.junit.jupiter.api.Test
    void testTheRealWarpArrivalFromTheLog() {
        // The 2026-08-02 console: arriving (-2919, 2187) with the dock flag at
        // (-3237, 2497). The sailor's own F3 read 46.3 facing the dock, so the
        // plain look-at yaw is the answer - no hull offset (that "fix" turned
        // the boat the other way and had to be reverted).
        assertEquals(45.7f, WarpService.yawToward(-2919, 2187, -3237, 2497), 0.2f);
        // Wrapping still hands back Minecraft's own (-180, 180]
        for (int deg = -360; deg <= 360; deg += 17) {
            float wrapped = WarpService.normalise(deg);
            assertTrue(wrapped > -180.01f && wrapped <= 180.01f, "Out of range: " + wrapped);
        }
    }
}
