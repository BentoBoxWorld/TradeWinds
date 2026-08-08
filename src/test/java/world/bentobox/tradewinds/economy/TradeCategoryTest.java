package world.bentobox.tradewinds.economy;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.bukkit.Material;
import org.junit.jupiter.api.Test;

import world.bentobox.tradewinds.CommonTestSetup;
import world.bentobox.tradewinds.ocean.IslandType;

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
        // Charcoal is charred timber, not a mined ore - FOREST islands sell it cheap
        assertEquals(TradeCategory.WOOD, TradeCategory.of(Material.CHARCOAL));
        assertEquals(TradeCategory.METALS, TradeCategory.of(Material.IRON_INGOT));
        assertEquals(TradeCategory.GEMS, TradeCategory.of(Material.DIAMOND));
        assertEquals(TradeCategory.LUXURY, TradeCategory.of(Material.CAKE));
        assertEquals(TradeCategory.MISC, TradeCategory.of(Material.DIRT));
    }

    @Test
    void testTradeGoodsAreTheShelves() {
        // Salvage is defined by the shelves, not by category name, so that the
        // existing trade economy is untouched: an iron ingot is cargo wherever
        // it always was, and a rabbit's foot was never on anyone's manifest
        assertTrue(TypeEconomy.tradeGoods().contains(Material.IRON_INGOT));
        assertTrue(TypeEconomy.tradeGoods().contains(Material.WHEAT));
        assertTrue(TypeEconomy.tradeGoods().contains(Material.DIAMOND));
        // Outfitter stock counts - a smith who sells iron swords will buy one back
        assertTrue(TypeEconomy.tradeGoods().contains(Material.IRON_SWORD));
        assertTrue(TypeEconomy.tradeGoods().contains(Material.FISHING_ROD));
        // The universal essentials
        assertTrue(TypeEconomy.tradeGoods().contains(Material.BREAD));
        assertTrue(TypeEconomy.tradeGoods().contains(Material.CHARCOAL));
        // Scavenged loot is not
        assertFalse(TypeEconomy.tradeGoods().contains(Material.RABBIT_FOOT));
        assertFalse(TypeEconomy.tradeGoods().contains(Material.ROTTEN_FLESH));
        assertFalse(TypeEconomy.tradeGoods().contains(Material.DIRT));
        assertFalse(TypeEconomy.tradeGoods().contains(Material.ENDER_PEARL));
    }

    @Test
    void testSalvageIsItsOwnPoolAndNobodyTradesIt() {
        // No island type produces or demands salvage - that is what makes it
        // neutral, and what keeps it out of the real categories' stock pools
        for (IslandType type : IslandType.values()) {
            assertFalse(TypeEconomy.produces(type).contains(TradeCategory.SALVAGE));
            assertFalse(TypeEconomy.demands(type).contains(TradeCategory.SALVAGE));
        }
        // And it is tech-neutral: neither raw nor finished
        assertFalse(TradeCategory.SALVAGE.isRaw());
        assertFalse(TradeCategory.SALVAGE.isFinished());
        assertTrue(TradeCategory.SALVAGE.isSalvage());
        // of() never returns it: name heuristics do not decide salvage
        for (Material material : new Material[] { Material.DIRT, Material.ROTTEN_FLESH, Material.RABBIT_FOOT,
                Material.DIAMOND, Material.WHEAT }) {
            assertFalse(TradeCategory.of(material).isSalvage(), material + " classified as salvage by name");
        }
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
