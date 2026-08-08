package world.bentobox.tradewinds.commands;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.util.List;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import world.bentobox.tradewinds.CommonTestSetup;
import world.bentobox.tradewinds.TradeWinds;
import world.bentobox.tradewinds.ocean.OceanConfig;
import world.bentobox.tradewinds.ocean.OceanEngine;
import world.bentobox.tradewinds.ocean.IslandSpec;

/**
 * Tests the nearest-islands query behind /twadmin islands and tpisland.
 *
 * @author tastybento
 */
class AdminIslandsCommandTest extends CommonTestSetup {

    private static final long SEED = 5555L;

    private TradeWinds addon;

    @Override
    @BeforeEach
    public void setUp() throws Exception {
        super.setUp();
        addon = mock(TradeWinds.class);
        OceanEngine engine = new OceanEngine(new OceanConfig(SEED, 2500, 160, 45, 0.5, 5, 5000, 70));
        when(addon.getOceanEngine(anyLong())).thenReturn(engine);
        when(addon.getOverWorld()).thenReturn(world);
        when(world.getSeed()).thenReturn(SEED);
    }

    @Test
    void testNearestSortedAndCapped() {
        List<IslandSpec> list = AdminIslandsCommand.nearest(addon, 0, 0);
        assertFalse(list.isEmpty());
        assertTrue(list.size() <= AdminIslandsCommand.COUNT);
        // Sorted nearest-first
        for (int i = 1; i < list.size(); i++) {
            assertTrue(list.get(i - 1).distanceSquared(0, 0) <= list.get(i).distanceSquared(0, 0));
        }
        // The starter cluster guarantees the first hits are close to spawn
        assertTrue(Math.sqrt(list.get(0).distanceSquared(0, 0)) < 5000,
                "Nearest island should be a starter island near spawn");
    }

    @Test
    void testNearestFromRemotePosition() {
        List<IslandSpec> remote = AdminIslandsCommand.nearest(addon, 40_000, -40_000);
        assertFalse(remote.isEmpty());
        // Nearest to the remote point, not to spawn
        assertTrue(Math.sqrt(remote.get(0).distanceSquared(40_000, -40_000)) < 15_000);
        assertEquals(remote, remote.stream()
                .sorted((a, b) -> Long.compare(a.distanceSquared(40_000, -40_000), b.distanceSquared(40_000, -40_000)))
                .toList());
    }
}
