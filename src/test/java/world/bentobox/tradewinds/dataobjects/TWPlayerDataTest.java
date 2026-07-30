package world.bentobox.tradewinds.dataobjects;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

import world.bentobox.tradewinds.galaxy.GalaxyConfig;
import world.bentobox.tradewinds.galaxy.GalaxyEngine;
import world.bentobox.tradewinds.galaxy.IslandSpec;

/**
 * Tests the player chart data object.
 *
 * @author tastybento
 */
class TWPlayerDataTest {

    private final GalaxyEngine engine = new GalaxyEngine(new GalaxyConfig(9L, 2500, 160, 45, 1.0, 0, 5000, 70));

    @Test
    void testCharting() {
        TWPlayerData data = new TWPlayerData("uuid");
        IslandSpec spec = engine.islandInCell(1, 2).orElseThrow();
        assertFalse(data.isCharted(spec));
        assertTrue(data.chart(spec));
        assertTrue(data.isCharted(spec));
        // Charting twice is not "newly charted"
        assertFalse(data.chart(spec));
        assertEquals(1, data.getChartedIslands().size());
        assertEquals("1,2", TWPlayerData.chartKey(spec));
    }
}
