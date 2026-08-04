package world.bentobox.tradewinds.dataobjects;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

import world.bentobox.tradewinds.galaxy.GalaxyConfig;
import world.bentobox.tradewinds.galaxy.GalaxyEngine;
import world.bentobox.tradewinds.galaxy.IslandSpec;
import world.bentobox.tradewinds.galaxy.IslandType;
import world.bentobox.tradewinds.galaxy.SecurityBand;

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

    @Test
    void testThePriceLogbookRemembersWhatWasSeenAndWhen() {
        TWPlayerData data = new TWPlayerData(java.util.UUID.randomUUID().toString());
        IslandSpec port = new IslandSpec(3, 4, 3000, 4000, IslandType.FISHING, SecurityBand.SAFE,
                "minecraft:plains", "Cold Harbour", 3);
        // Nothing is knowable about a port you have never called at
        assertEquals(0L, data.lastSeenPrices(port));
        assertTrue(data.loggedPrices(port).isEmpty());

        data.logPrices(port, java.util.Map.of("FISH", 31, "METALS", 12), 1_000_000L);
        assertEquals(1_000_000L, data.lastSeenPrices(port));
        assertEquals(31, data.loggedPrices(port).get("FISH"));

        // A later call overwrites: the logbook holds the LAST reading, not a history
        data.logPrices(port, java.util.Map.of("FISH", 18), 2_000_000L);
        assertEquals(2_000_000L, data.lastSeenPrices(port));
        assertEquals(18, data.loggedPrices(port).get("FISH"));
        assertFalse(data.loggedPrices(port).containsKey("METALS"), "Stale entries are replaced, not merged");
    }

    @Test
    void testTheLogbookIsPerIsland() {
        TWPlayerData data = new TWPlayerData(java.util.UUID.randomUUID().toString());
        IslandSpec one = new IslandSpec(1, 1, 1000, 1000, IslandType.MINING, SecurityBand.SAFE,
                "minecraft:plains", "One", 3);
        IslandSpec two = new IslandSpec(9, 9, 9000, 9000, IslandType.MINING, SecurityBand.SAFE,
                "minecraft:plains", "Two", 3);
        data.logPrices(one, java.util.Map.of("ORES", 50), 500L);
        assertTrue(data.loggedPrices(two).isEmpty(), "Visiting one port teaches nothing about another");
        assertEquals(50, data.loggedPrices(one).get("ORES"));
    }
}
