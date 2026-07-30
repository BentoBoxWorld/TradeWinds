package world.bentobox.tradewinds.economy;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.bukkit.Material;
import org.junit.jupiter.api.Test;

import world.bentobox.tradewinds.CommonTestSetup;
import world.bentobox.tradewinds.galaxy.IslandType;

/**
 * Tests category classification and the type economy tables.
 *
 * @author tastybento
 */
class TradeCategoryTest extends CommonTestSetup {

    @Test
    void testClassification() {
        assertEquals(TradeCategory.CROPS, TradeCategory.of(Material.WHEAT));
        assertEquals(TradeCategory.FOOD, TradeCategory.of(Material.BREAD));
        assertEquals(TradeCategory.FOOD, TradeCategory.of(Material.SUGAR));
        assertEquals(TradeCategory.FISH, TradeCategory.of(Material.COD));
        assertEquals(TradeCategory.WOOD, TradeCategory.of(Material.OAK_LOG));
        assertEquals(TradeCategory.STONE, TradeCategory.of(Material.GRANITE));
        assertEquals(TradeCategory.ORES, TradeCategory.of(Material.RAW_IRON));
        assertEquals(TradeCategory.ORES, TradeCategory.of(Material.COAL));
        assertEquals(TradeCategory.METALS, TradeCategory.of(Material.IRON_INGOT));
        assertEquals(TradeCategory.GEMS, TradeCategory.of(Material.DIAMOND));
        assertEquals(TradeCategory.LUXURY, TradeCategory.of(Material.CAKE));
        assertEquals(TradeCategory.MISC, TradeCategory.of(Material.DIRT));
    }

    @Test
    void testEveryTypeDemandsSomething() {
        for (IslandType type : IslandType.values()) {
            assertFalse(TypeEconomy.demands(type).isEmpty(), type + " must demand something");
        }
    }

    @Test
    void testProduceAndDemandNeverOverlap() {
        for (IslandType type : IslandType.values()) {
            TypeEconomy.produces(type)
                    .forEach(cat -> assertFalse(TypeEconomy.demands(type).contains(cat),
                            type + " both produces and demands " + cat));
        }
    }

    @Test
    void testProfitableRoutePairsExistBetweenTypes() {
        // For every produced category somewhere, some other type demands it
        for (IslandType producer : IslandType.values()) {
            for (TradeCategory cat : TypeEconomy.produces(producer)) {
                boolean demanded = false;
                for (IslandType consumer : IslandType.values()) {
                    if (consumer != producer && TypeEconomy.demands(consumer).contains(cat)) {
                        demanded = true;
                        break;
                    }
                }
                assertTrue(demanded, cat + " produced by " + producer + " is demanded nowhere");
            }
        }
    }

    @Test
    void testCatalogsMatchProduction() {
        assertFalse(TypeEconomy.catalog(IslandType.AGRICULTURAL).isEmpty());
        assertTrue(TypeEconomy.catalog(IslandType.AGRICULTURAL).contains(Material.WHEAT));
        assertTrue(TypeEconomy.catalog(IslandType.MINING).contains(Material.RAW_IRON));
        // LUXURY produces nothing - it only buys
        assertTrue(TypeEconomy.catalog(IslandType.LUXURY).isEmpty());
    }
}
