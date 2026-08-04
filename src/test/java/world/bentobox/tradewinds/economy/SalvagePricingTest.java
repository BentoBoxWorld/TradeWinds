package world.bentobox.tradewinds.economy;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import org.bukkit.Material;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import world.bentobox.tradewinds.CommonTestSetup;
import world.bentobox.tradewinds.Settings;
import world.bentobox.tradewinds.TradeWinds;
import world.bentobox.tradewinds.dataobjects.IslandDataManager;
import world.bentobox.tradewinds.galaxy.IslandSpec;
import world.bentobox.tradewinds.galaxy.IslandType;
import world.bentobox.tradewinds.galaxy.SecurityBand;

/**
 * The salvage economy (Stage 7.5 Phase 2): scavenged goods sell, but at a
 * discount and into a stock pool of their own.
 *
 * @author tastybento
 */
class SalvagePricingTest extends CommonTestSetup {

    private TradeWinds addon;
    private Settings settings;
    private MarketService service;

    /** A fishing port, which neither produces nor demands metals or salvage. */
    private final IslandSpec port = new IslandSpec(0, 0, 2500, 2500, IslandType.FISHING, SecurityBand.SAFE,
            "minecraft:plains", "Test", 4);

    @Override
    @BeforeEach
    public void setUp() throws Exception {
        super.setUp();
        addon = mock(TradeWinds.class);
        settings = new Settings();
        when(addon.getSettings()).thenReturn(settings);
        when(addon.getIslandDataManager()).thenReturn(mock(IslandDataManager.class));
        service = new MarketService(addon);
    }

    @Test
    void testScavengedGoodsAreSellableAtAll() {
        // The complaint that started this work: a scavenger's haul had no price
        // and could only be destroyed. Mob drops have no recipe, so these can
        // only ever come from the base price table.
        for (Material loot : new Material[] { Material.ROTTEN_FLESH, Material.BONE, Material.GUNPOWDER,
                Material.ENDER_PEARL, Material.FEATHER, Material.STRING, Material.RABBIT_FOOT,
                Material.PHANTOM_MEMBRANE, Material.NAUTILUS_SHELL }) {
            assertTrue(service.basePrice(loot).isPresent(), loot + " cannot be priced");
            assertTrue(service.playerSellsAt(port, loot).orElse(0.0) > 0, loot + " sells for nothing");
        }
    }

    @Test
    void testSalvageIsDiscountedAgainstBook() {
        double discounted = service.playerSellsAt(port, Material.ENDER_PEARL).orElseThrow();
        // Flip the discount off and the same pearl fetches book price - which
        // isolates the discount from band, tech and drift entirely
        settings.setSalvageDiscount(1.0);
        double full = service.playerSellsAt(port, Material.ENDER_PEARL).orElseThrow();
        assertTrue(discounted < full, "Salvage must pay less than book: " + discounted + " vs " + full);
        assertEquals(0.375, discounted / full, 0.02);
    }

    @Test
    void testTheDiscountNeverTouchesRealCargo() {
        // The regression that would quietly wreck the trade game: legitimate
        // cargo must price identically however the salvage knob is set
        double before = service.playerSellsAt(port, Material.IRON_INGOT).orElseThrow();
        double buyBefore = service.playerBuysAt(port, Material.WHEAT).orElseThrow();
        settings.setSalvageDiscount(0.05);
        assertEquals(before, service.playerSellsAt(port, Material.IRON_INGOT).orElseThrow());
        assertEquals(buyBefore, service.playerBuysAt(port, Material.WHEAT).orElseThrow());
    }

    @Test
    void testSalvageDriftsInItsOwnPool() {
        // Dumping junk must not crater the same category's honest cargo: a
        // rotten flesh sale is STONE to nobody and METALS to nobody
        assertEquals(TradeCategory.SALVAGE, service.driftPool(Material.ROTTEN_FLESH));
        assertEquals(TradeCategory.SALVAGE, service.driftPool(Material.DIRT));
        assertEquals(TradeCategory.SALVAGE, service.driftPool(Material.ENDER_PEARL));
        // While real cargo keeps its own pool
        assertEquals(TradeCategory.METALS, service.driftPool(Material.IRON_INGOT));
        assertEquals(TradeCategory.CROPS, service.driftPool(Material.WHEAT));
        assertEquals(TradeCategory.GEMS, service.driftPool(Material.DIAMOND));
    }

    @Test
    void testTradeGoodsAndSalvageAreDisjoint() {
        assertTrue(service.isTradeGood(Material.IRON_INGOT));
        assertTrue(service.isTradeGood(Material.BREAD));
        assertFalse(service.isTradeGood(Material.ROTTEN_FLESH));
        assertFalse(service.isTradeGood(Material.DIRT));
    }

    @Test
    void testTechGatesWhatAPortWillHandle() {
        // 1500/level: a TL1 hamlet takes mob loot but not diamond-grade gear
        IslandSpec hamlet = new IslandSpec(0, 0, 2500, 2500, IslandType.FISHING, SecurityBand.SAFE,
                "minecraft:plains", "Hamlet", 1);
        IslandSpec hub = new IslandSpec(0, 0, 2500, 2500, IslandType.INDUSTRIAL, SecurityBand.SAFE,
                "minecraft:plains", "Hub", 7);
        assertTrue(service.handlesValue(hamlet, Material.ENDER_PEARL));
        assertTrue(service.handlesValue(hamlet, Material.BONE));
        assertFalse(service.handlesValue(hamlet, Material.NETHER_STAR), "TL1 cannot handle a nether star");
        assertFalse(service.handlesValue(hamlet, Material.TOTEM_OF_UNDYING));
        // The hub takes the lot - loot has a destination, and that is a voyage
        assertTrue(service.handlesValue(hub, Material.NETHER_STAR));
        assertTrue(service.handlesValue(hub, Material.TOTEM_OF_UNDYING));
    }

    @Test
    void testTheTechGateNeverRefusesAPortsOwnStock() {
        // A low-tech LUXURY island DEMANDS gems. If the value gate applied to
        // trade goods it would refuse the very diamonds it wants, which reads as
        // a broken market rather than as a tech gate.
        IslandSpec poorLuxury = new IslandSpec(0, 0, 2500, 2500, IslandType.LUXURY, SecurityBand.SAFE,
                "minecraft:plains", "Faded Grandeur", 1);
        assertTrue(service.handlesValue(poorLuxury, Material.DIAMOND));
        assertTrue(service.handlesValue(poorLuxury, Material.GOLDEN_APPLE));
        assertTrue(service.playerSellsAt(poorLuxury, Material.DIAMOND).orElseThrow() > 0);
    }

    @Test
    void testTheGateCanBeSwitchedOff() {
        IslandSpec hamlet = new IslandSpec(0, 0, 2500, 2500, IslandType.FISHING, SecurityBand.SAFE,
                "minecraft:plains", "Hamlet", 1);
        assertFalse(service.handlesValue(hamlet, Material.NETHER_STAR));
        settings.setSalvageValuePerTechLevel(0);
        assertTrue(service.handlesValue(hamlet, Material.NETHER_STAR));
    }

    @Test
    void testSalvageStillCannotBeArbitraged() {
        // Whatever the discount does to the level, the spread must stay open or
        // a port becomes a money printer
        for (double discount : new double[] { 0.05, 0.375, 1.0 }) {
            settings.setSalvageDiscount(discount);
            double buy = service.playerBuysAt(port, Material.ENDER_PEARL).orElseThrow();
            double sell = service.playerSellsAt(port, Material.ENDER_PEARL).orElseThrow();
            assertTrue(sell < buy, "Round trip profits at discount " + discount + ": " + sell + " >= " + buy);
        }
    }
}
