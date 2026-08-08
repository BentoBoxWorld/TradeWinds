package world.bentobox.tradewinds.travel;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.util.Map;
import java.util.Optional;
import java.util.Set;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import world.bentobox.bentobox.api.metadata.MetaDataValue;
import world.bentobox.bentobox.api.user.User;
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
 * The member-only warp node (Stage 7b): a claimed islet travels as a
 * synthetic home spec, pinned first in its members' warp dialogs and
 * invisible to everyone else.
 *
 * @author tastybento
 */
class HomeWarpTest extends CommonTestSetup {

    private static final long SEED = 77777L;

    private TradeWinds addon;
    private Settings settings;
    private WarpService service;
    private OceanEngine engine;
    private IslandSpec origin;

    @Override
    @BeforeEach
    public void setUp() throws Exception {
        super.setUp();
        addon = mock(TradeWinds.class);
        settings = new Settings();
        when(addon.getSettings()).thenReturn(settings);
        when(addon.getOverWorld()).thenReturn(world);
        when(world.getSeed()).thenReturn(SEED);
        engine = new OceanEngine(new OceanConfig(SEED, 2500, 160, 45, 1.0, 0, 5000, 70));
        when(addon.getOceanEngine(anyLong())).thenReturn(engine);
        when(addon.getRouteGraph()).thenReturn(new RouteGraph(0.01, Map.of()));
        when(addon.getIslands()).thenReturn(im);
        origin = engine.islandInCell(0, 0).orElseThrow();

        // The player has charted two neighbouring islands
        PlayerDataManager pdm = mock(PlayerDataManager.class);
        when(addon.getPlayerDataManager()).thenReturn(pdm);
        TWPlayerData data = new TWPlayerData(uuid.toString());
        data.setChartedIslands(Set.of("1,0", "0,1"));
        when(pdm.get(uuid)).thenReturn(data);

        service = new WarpService(addon);
    }

    /** Give the player a claimed island at the given centre. */
    private void claimAt(int x, int z) {
        when(im.getIsland(world, uuid)).thenReturn(island);
        when(island.getMetaData(anyString()))
                .thenReturn(Optional.of(new MetaDataValue(x / 900 + "," + z / 900 + ",80")));
        org.bukkit.Location center = mock(org.bukkit.Location.class);
        when(center.getBlockX()).thenReturn(x);
        when(center.getBlockZ()).thenReturn(z);
        when(island.getCenter()).thenReturn(center);
        when(island.getProtectionRange()).thenReturn(112);
    }

    @Test
    void testHomeSpecIsSyntheticAndMarked() {
        claimAt(45_000, -8_000);
        IslandSpec home = HomePort.specFor(addon, User.getInstance(mockPlayer)).orElseThrow();
        assertTrue(HomePort.isHome(home));
        assertEquals(45_000, home.centerX());
        assertEquals(-8_000, home.centerZ());
        assertEquals(Math.floorDiv(45_000, 900), home.cellX());
        assertEquals(Math.floorDiv(-8_000, 900), home.cellZ());
        // No admin name: the locale's "Home" (the mock returns the key)
        assertEquals("tradewinds.home.port-name", home.name());
    }

    @Test
    void testHomeIsPinnedFirstForMembers() {
        claimAt(45_000, -8_000);
        var destinations = service.destinations(mockPlayer, origin, 1_000_000);
        assertFalse(destinations.isEmpty());
        assertTrue(HomePort.isHome(destinations.get(0).island()), "Home should be pinned first");
        // And exactly once - the ports follow
        assertEquals(1, destinations.stream().filter(d -> HomePort.isHome(d.island())).count());
        assertTrue(destinations.size() >= 3, "Charted ports should still be offered");
    }

    @Test
    void testNoClaimMeansNoHomeEntry() {
        // No island at all
        var destinations = service.destinations(mockPlayer, origin, 1_000_000);
        assertTrue(destinations.stream().noneMatch(d -> HomePort.isHome(d.island())));
        // An island the player merely visits (no claim metadata) adds nothing
        when(im.getIsland(world, uuid)).thenReturn(island);
        when(island.getMetaData(anyString())).thenReturn(Optional.empty());
        destinations = service.destinations(mockPlayer, origin, 1_000_000);
        assertTrue(destinations.stream().noneMatch(d -> HomePort.isHome(d.island())));
    }

    @Test
    void testHomeCostsNormalFuel() {
        claimAt(45_000, -8_000);
        var home = service.destinations(mockPlayer, origin, 1_000_000).get(0);
        long dist = Math.round(Math.sqrt(home.island().distanceSquared(origin.centerX(), origin.centerZ())));
        assertEquals((int) Math.ceil(dist * 0.01), home.fuelCost(), 1.0,
                "Home warp pays the same fuel-per-block as any port");
    }
}
