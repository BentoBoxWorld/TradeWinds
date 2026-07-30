package world.bentobox.tradewinds.travel;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.util.List;
import java.util.Map;
import java.util.Optional;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import world.bentobox.tradewinds.CommonTestSetup;
import world.bentobox.tradewinds.Settings;
import world.bentobox.tradewinds.TradeWinds;
import world.bentobox.tradewinds.dataobjects.PlayerDataManager;
import world.bentobox.tradewinds.dataobjects.TWPlayerData;
import world.bentobox.tradewinds.galaxy.GalaxyConfig;
import world.bentobox.tradewinds.galaxy.GalaxyEngine;
import world.bentobox.tradewinds.galaxy.IslandSpec;
import world.bentobox.tradewinds.galaxy.RouteGraph;

/**
 * Tests the warp dialog's destination selection: charted-only, origin
 * excluded, nearest first, affordability flags, capped.
 *
 * @author tastybento
 */
class WarpSelectionTest extends CommonTestSetup {

    private static final long SEED = 2026L;

    private TradeWinds addon;
    private GalaxyEngine engine;
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
        engine = new GalaxyEngine(new GalaxyConfig(SEED, 2500, 160, 45, 1.0, 0, 5000, 70));
        when(addon.getGalaxyEngine(anyLong())).thenReturn(engine);
        when(addon.getOverWorld()).thenReturn(world);
        when(world.getSeed()).thenReturn(SEED);
        when(addon.getRouteGraph()).thenReturn(new RouteGraph(0.01, Map.of()));
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
}
