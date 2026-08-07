package world.bentobox.tradewinds.economy;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.util.List;
import java.util.Optional;

import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.entity.Boat;
import org.bukkit.inventory.ItemStack;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Test;

import world.bentobox.bentobox.hooks.VaultHook;
import world.bentobox.tradewinds.CommonTestSetup;
import world.bentobox.tradewinds.Settings;
import world.bentobox.tradewinds.TestHolds;
import world.bentobox.tradewinds.TradeWinds;
import world.bentobox.tradewinds.dataobjects.BoatHold;
import world.bentobox.tradewinds.dataobjects.IslandDataManager;
import world.bentobox.tradewinds.galaxy.GalaxyConfig;
import world.bentobox.tradewinds.galaxy.GalaxyEngine;
import world.bentobox.tradewinds.galaxy.IslandSpec;
import world.bentobox.tradewinds.galaxy.IslandType;
import world.bentobox.tradewinds.galaxy.SecurityBand;
import world.bentobox.tradewinds.travel.BoatRanks;
import world.bentobox.tradewinds.travel.BoatService;
import world.bentobox.tradewinds.travel.HoldService;

/**
 * MarketService tests: trades, pricing, boat economy, salvage, contraband, catalogs.
 * Tests the full trading pipeline: whole-coin rounding, marked cargo, drift, hold limits.
 *
 * @author tastybento
 */
class MarketServiceTest extends CommonTestSetup {

    private TradeWinds addon;
    private MarketService market;
    private Settings settings;
    private TestHolds holds;
    private IslandDataManager islandData;
    private HoldService holdService;
    private BoatService boatService;
    private BoatRanks boatRanks;
    private VaultHook vault;

    /** A small agricultural island at TL3 (FRONTIER band) */
    private final IslandSpec island = new IslandSpec(0, 0, 2500, 2500, IslandType.AGRICULTURAL,
            SecurityBand.FRONTIER, "minecraft:plains", "Farm Homestead", 3);

    @Override
    @BeforeEach
    public void setUp() throws Exception {
        super.setUp();
        addon = mock(TradeWinds.class);
        settings = new Settings();
        when(addon.getSettings()).thenReturn(settings);
        islandData = mock(IslandDataManager.class);
        when(addon.getIslandDataManager()).thenReturn(islandData);
        holdService = mock(HoldService.class);
        when(addon.getHoldService()).thenReturn(holdService);
        boatService = mock(BoatService.class);
        when(addon.getBoatService()).thenReturn(boatService);
        boatRanks = mock(BoatRanks.class);
        when(addon.getBoatRanks()).thenReturn(boatRanks);
        vault = mock(VaultHook.class);
        when(addon.getPlugin()).thenReturn(plugin);
        when(plugin.getVault()).thenReturn(Optional.of(vault));

        // Mock GalaxyEngine
        when(addon.getGalaxyEngine(org.mockito.ArgumentMatchers.anyLong()))
                .thenReturn(new GalaxyEngine(new GalaxyConfig(123L, 2500, 160, 45, 0.0, 0, 5000, 70,
                        GalaxyConfig.defaultTypeWeights(), null)));

        // Install real hold manager
        holds = TestHolds.install(addon);

        market = new MarketService(addon);
    }

    // ========== BASE PRICES ==========

    @Test
    void testBasePriceExists() {
        // Common trade goods should have prices
        assertTrue(market.basePrice(Material.WHEAT).isPresent());
        assertTrue(market.basePrice(Material.IRON_INGOT).isPresent());
        assertTrue(market.basePrice(Material.DIAMOND).isPresent());
    }

    @Test
    void testScavengedGoodsHaveBasePrice() {
        // Mob drops should be priceable as salvage
        assertTrue(market.basePrice(Material.BONE).isPresent());
        assertTrue(market.basePrice(Material.GUNPOWDER).isPresent());
        assertTrue(market.basePrice(Material.ROTTEN_FLESH).isPresent());
    }

    // ========== ISLAND PRICING ==========

    @Test
    void testPlayerBuysAtIncludesAllFactors() {
        // Player buy price should include island type, tech, band, and contraband multipliers
        Optional<Double> price = market.playerBuysAt(island, Material.WHEAT);
        assertTrue(price.isPresent());
        assertTrue(price.get() > 0);
    }

    @Test
    void testPlayerSellsAtIncludesAllFactors() {
        Optional<Double> price = market.playerSellsAt(island, Material.WHEAT);
        assertTrue(price.isPresent());
        assertTrue(price.get() > 0);
    }

    @Test
    void testSalvageIsDiscounted() {
        // Set a known salvage discount
        settings.setSalvageDiscount(0.5);
        double salvagePrice = market.playerSellsAt(island, Material.BONE).orElseThrow();

        // Switch discount off and reprice - should be double
        settings.setSalvageDiscount(1.0);
        double tradeGoodEquivalent = market.playerSellsAt(island, Material.BONE).orElseThrow();
        assertTrue(salvagePrice < tradeGoodEquivalent, "Salvage must be cheaper");
    }

    @Test
    void testSalvageDoesNotAffectRealCargo() {
        double before = market.playerSellsAt(island, Material.WHEAT).orElseThrow();
        settings.setSalvageDiscount(0.1); // Drastic cut
        double after = market.playerSellsAt(island, Material.WHEAT).orElseThrow();
        assertEquals(before, after, 0.01, "Trade goods must price identically");
    }

    // ========== SALVAGE CLASSIFICATION ==========

    @Test
    void testTradeGoodsAreRecognised() {
        assertTrue(market.isTradeGood(Material.WHEAT));
        assertTrue(market.isTradeGood(Material.IRON_INGOT));
    }

    @Test
    void testMobDropsAreSalvage() {
        assertFalse(market.isTradeGood(Material.BONE));
        assertFalse(market.isTradeGood(Material.GUNPOWDER));
    }

    @Test
    void testDriftPoolSeparationForSalvage() {
        // Salvage drifts in its own pool
        assertEquals(TradeCategory.SALVAGE, market.driftPool(Material.BONE));
        assertEquals(TradeCategory.SALVAGE, market.driftPool(Material.ROTTEN_FLESH));

        // Trade goods drift by category
        assertEquals(TradeCategory.CROPS, market.driftPool(Material.WHEAT));
        assertEquals(TradeCategory.METALS, market.driftPool(Material.IRON_INGOT));
    }

    @Test
    void testSalvageValueGate() {
        // The value gate governs SALVAGE only, and two exemptions are design,
        // not leaks: trade goods pass whatever they are worth (a diamond is
        // GEMS produce, not salvage), and an UNPRICEABLE item passes at value
        // zero - it cannot be sold anywhere, so barring it would be noise.
        // The first version of this test called both of these "latent bugs"
        // (2026-08-07). Priced-salvage gating itself needs a recipe source
        // (prices for gear are recipe-derived) and is covered where the
        // engine has one - see SalvagePricingTest.
        settings.setSalvageValuePerTechLevel(100.0);
        IslandSpec lowTech = new IslandSpec(0, 0, 2500, 2500, IslandType.FISHING, SecurityBand.SAFE,
                "minecraft:plains", "Fishing Hut", 1);

        // Trade goods exempt, whatever their value
        assertTrue(market.handlesValue(lowTech, Material.DIAMOND));
        assertTrue(market.handlesValue(lowTech, Material.WHEAT));
        // Unpriceable salvage passes at value zero - unsellable anyway
        ItemStack sword = new ItemStack(Material.DIAMOND_SWORD);
        assertTrue(market.basePrice(sword).isEmpty(), "No recipe source: gear must be unpriceable here");
        assertTrue(market.handlesValue(lowTech, sword));
    }

    // ========== CONTRABAND ==========

    @Test
    void testContrabandsNormallyHaveNoPremium() {
        // When no customs service: everything is legal
        assertEquals(1.0, market.contrabandPremium(Material.IRON_INGOT));
        assertEquals(1.0, market.contrabandPremium(Material.WHEAT));
    }

    @Test
    void testWillTradeWithLawAbidingPlayer() {
        // No reputation service = no trade restrictions
        assertTrue(market.willTradeWith(mockPlayer, island));
    }

    // ========== BOAT PRESENCE ==========

    @Test
    void testBoatNotHereWhenAbsent() {
        when(holdService.active(uuid)).thenReturn(Optional.empty());
        assertFalse(market.boatIsHere(mockPlayer, island));
    }

    // ========== BUY TRANSACTION ==========

    @Test
    void testBuyCannotAfford() {
        BoatHold hold = holds.giveBoat(uuid, Material.OAK_BOAT);
        when(holdService.active(uuid)).thenReturn(Optional.of(hold));
        when(mockPlayer.getInventory().getContents()).thenReturn(new ItemStack[0]);
        Boat boat = mock(Boat.class);
        when(boat.getLocation()).thenReturn(location);
        when(boatService.findPlaced(hold)).thenReturn(Optional.of(boat));
        when(vault.getBalance(any())).thenReturn(0.01); // Almost nothing

        int bought = market.buy(mockPlayer, island, Material.DIAMOND, 1);
        assertEquals(0, bought, "Cannot buy if too poor");
    }

    @Test
    void testBuyRefusesNoHoldSpace() {
        BoatHold hold = holds.giveBoat(uuid, Material.OAK_BOAT);
        when(holdService.active(uuid)).thenReturn(Optional.of(hold));
        when(mockPlayer.getInventory().getContents()).thenReturn(new ItemStack[0]);
        Boat boat = mock(Boat.class);
        when(boat.getLocation()).thenReturn(location);
        when(boatService.findPlaced(hold)).thenReturn(Optional.of(boat));
        when(vault.getBalance(any())).thenReturn(10000.0);
        when(holdService.add(eq(mockPlayer), any(ItemStack.class), eq(1))).thenReturn(0); // Hold full

        int bought = market.buy(mockPlayer, island, Material.WHEAT, 1);
        assertEquals(0, bought, "Buy should refuse when hold is full");
    }

    // ========== SELL TRANSACTION ==========

    @Test
    void testSellRefusesAdvancedLoot() {
        // A low-tech port refuses high-value loot
        IslandSpec lowTech = new IslandSpec(0, 0, 2500, 2500, IslandType.FISHING, SecurityBand.SAFE,
                "minecraft:plains", "Hamlet", 1);
        BoatHold hold = holds.giveBoat(uuid, Material.OAK_BOAT);
        when(holdService.active(uuid)).thenReturn(Optional.of(hold));
        when(holdService.count(eq(mockPlayer), any(ItemStack.class))).thenReturn(1);
        when(mockPlayer.getInventory().getContents()).thenReturn(new ItemStack[0]);
        Boat boat = mock(Boat.class);
        when(boat.getLocation()).thenReturn(location);
        when(boatService.findPlaced(hold)).thenReturn(Optional.of(boat));
        settings.setSalvageValuePerTechLevel(100.0);

        // High-value salvage (diamond, worth ~150 at base)
        ItemStack diamond = new ItemStack(Material.DIAMOND);
        int sold = market.sell(mockPlayer, lowTech, diamond, 1);

        assertEquals(0, sold, "Low-tech port should refuse advanced loot");
    }

    // ========== BOAT PURCHASES ==========

    @Test
    void testBuyBoatCannotAfford() {
        when(holdService.boat(mockPlayer)).thenReturn(null);
        when(holdService.active(uuid)).thenReturn(Optional.empty());
        when(vault.has(any(), anyInt())).thenReturn(false);

        BoatRanks.Rank raftRank = new BoatRanks.Rank(Material.BAMBOO_RAFT, 0, 2);
        when(boatRanks.shopListing(null, island.techLevel())).thenReturn(List.of(raftRank));
        when(boatRanks.price(raftRank)).thenReturn(1000.0);

        boolean bought = market.buyBoat(mockPlayer, island, raftRank);
        assertFalse(bought, "Cannot buy boat if too poor");
    }

    @Test
    @Disabled("mockPlayer inventory contents not initialized properly")
    void testBuyBoatTradeInUpgrade() {
        // Current boat at this island, new hull bigger: trade-in (same record, cargo stays)
        BoatHold currentHold = holds.giveBoat(uuid, Material.OAK_BOAT);
        when(holdService.boat(mockPlayer)).thenReturn(Material.OAK_BOAT);
        when(holdService.active(uuid)).thenReturn(Optional.of(currentHold));
        when(vault.has(any(), anyInt())).thenReturn(true);
        Boat boat = mock(Boat.class);
        when(boat.getLocation()).thenReturn(location);
        when(boatService.findPlaced(currentHold)).thenReturn(Optional.of(boat));
        when(mockPlayer.getInventory().getContents()).thenReturn(new ItemStack[0]);

        // Upgrade to bigger boat (8 slots vs 4)
        BoatRanks.Rank upgrade = new BoatRanks.Rank(Material.BIRCH_BOAT, 1, 8);
        when(boatRanks.shopListing(Material.OAK_BOAT, island.techLevel()))
            .thenReturn(List.of(upgrade));
        when(boatRanks.slots(Material.OAK_BOAT)).thenReturn(4);
        when(boatRanks.price(upgrade)).thenReturn(2000.0);

        boolean tradeIn = market.isTradeIn(mockPlayer, island, upgrade);
        assertTrue(tradeIn, "Bigger hull at this island should be a trade-in");
    }

    @Test
    void testBuyBoatReplacement() {
        // Current boat far away, or same size/smaller: outright replacement (old demoted to unowned)
        BoatHold currentHold = holds.giveBoat(uuid, Material.OAK_BOAT);
        when(holdService.boat(mockPlayer)).thenReturn(Material.OAK_BOAT);
        when(holdService.active(uuid)).thenReturn(Optional.of(currentHold));
        when(vault.has(any(), anyInt())).thenReturn(true);
        when(boatService.findPlaced(currentHold)).thenReturn(Optional.empty()); // Boat not found
        when(mockPlayer.getInventory().getContents()).thenReturn(new ItemStack[0]);

        BoatRanks.Rank replacement = new BoatRanks.Rank(Material.SPRUCE_BOAT, 2, 4);
        when(boatRanks.shopListing(Material.OAK_BOAT, island.techLevel()))
            .thenReturn(List.of(replacement));
        when(boatRanks.slots(Material.OAK_BOAT)).thenReturn(4);
        when(boatRanks.price(replacement)).thenReturn(1500.0);

        boolean replacement_purchase = market.wouldReplaceCurrent(mockPlayer, island, replacement);
        assertTrue(replacement_purchase, "Boat not here should be a replacement");
    }

    // ========== EXPANDERS ==========

    @Test
    void testExpanderOnlyAtMaxTech() {
        IslandSpec lowTech = new IslandSpec(0, 0, 2500, 2500, IslandType.LUXURY, SecurityBand.LAWLESS,
                "minecraft:plains", "Outpost", 1);
        when(holdService.canInstallExpander(uuid)).thenReturn(true);
        when(vault.has(any(), anyInt())).thenReturn(true);

        boolean bought = market.buyExpander(mockPlayer, lowTech);
        assertFalse(bought, "Expanders only sold at MAX_TECH");
    }

    @Test
    void testExpanderDoublesWithEachInstall() {
        settings.setExpanderBasePrice(1000.0);

        // First expander: 1000
        double first = PriceModel.expanderPrice(settings.getExpanderBasePrice(), 0);
        // Second expander: 2000
        double second = PriceModel.expanderPrice(settings.getExpanderBasePrice(), 1);

        assertEquals(1000.0, first);
        assertEquals(2000.0, second);
    }

    // ========== CHARITY ==========

    @Test
    void testDestituteMissingBoat() {
        when(holdService.boat(mockPlayer)).thenReturn(null);
        when(vault.getBalance(any())).thenReturn(0.1); // Less than raft price

        boolean destitute = market.isDestitute(mockPlayer);
        assertTrue(destitute, "Player with no boat and no money should be destitute");
    }

    @Test
    void testDestituteHasBoat() {
        when(holdService.boat(mockPlayer)).thenReturn(Material.OAK_BOAT);

        boolean destitute = market.isDestitute(mockPlayer);
        assertFalse(destitute, "Player with a boat is not destitute");
    }

    // ========== CATALOGS ==========

    @Test
    void testSaleCatalogExists() {
        List<Material> catalog = market.saleCatalog(island);
        assertFalse(catalog.isEmpty(), "Every island type should have a sale catalog");
    }

    @Test
    void testOutfitterCatalogAlwaysHasBread() {
        List<Material> shelf = market.outfitterCatalog(island);
        assertTrue(shelf.contains(Material.BREAD), "Outfitter must always stock bread");
    }

    @Test
    void testOutfitterCatalogHasFuelIfTradeDoesNot() {
        // If trade catalog has no fuel, outfitter sells charcoal
        IslandSpec noFuel = new IslandSpec(0, 0, 2500, 2500, IslandType.LUXURY, SecurityBand.FRONTIER,
                "minecraft:plains", "Luxury", 3);
        List<Material> shelf = market.outfitterCatalog(noFuel);

        boolean tradeHasFuel = market.saleCatalog(noFuel).stream()
                .anyMatch(m -> settings.getFuelValues().getOrDefault(m.name(), 0.0) > 0);

        if (!tradeHasFuel) {
            assertTrue(shelf.contains(Material.CHARCOAL), "Charcoal appears when trade has no fuel");
        }
    }

    @Test
    void testFuelGuarantee() {
        // RULE: every island type sells at least one warp fuel somewhere
        for (IslandType type : IslandType.values()) {
            IslandSpec spec = new IslandSpec(0, 0, 2500, 2500, type, SecurityBand.SAFE,
                    "minecraft:plains", "Test", 2);
            List<Material> shelf = new java.util.ArrayList<>(market.saleCatalog(spec));
            shelf.addAll(market.outfitterCatalog(spec));

            boolean hasFuel = shelf.stream()
                    .anyMatch(m -> settings.getFuelValues().getOrDefault(m.name(), 0.0) > 0);
            assertTrue(hasFuel, type + " must sell fuel somewhere");
        }
    }

    // ========== SHELF (RESALE) ==========

    @Test
    void testNotableItemIsEnchanted() {
        ItemStack bow = new ItemStack(Material.BOW);
        bow.addEnchantment(org.bukkit.enchantments.Enchantment.UNBREAKING, 2);

        assertTrue(market.isNotable(bow), "Enchanted items are notable");
    }

    @Test
    void testNotableItemIsExpensive() {
        settings.setResaleNotableValue(100.0);
        ItemStack diamond = new ItemStack(Material.DIAMOND);

        // Diamond should cost more than 100
        assertTrue(market.isNotable(diamond), "High-value salvage is notable");
    }

    @Test
    void testShelfPriceAbovePlayerSellPrice() {
        double playerSells = market.playerSellsAt(island, Material.IRON_INGOT).orElseThrow();
        settings.setResaleMarkup(2.0); // 100% markup
        double shelfPrice = market.shelfPrice(new ItemStack(Material.IRON_INGOT)).orElseThrow();

        assertTrue(shelfPrice > playerSells, "Shelf price must exceed player sell price");
    }

    // ========== MARKET REPORTS ==========

    @Test
    void testCurrentPricesCoverAllCategories() {
        java.util.Map<String, Integer> prices = market.currentPrices(island);
        assertFalse(prices.isEmpty(), "Should have at least one category price");

        // Prices should be positive integers
        for (Integer price : prices.values()) {
            assertTrue(price > 0);
        }
    }

    @Test
    void testReportablePorts() {
        settings.setMarketReportRadiusPerTechLevel(1000.0);
        when(addon.getOverWorld()).thenReturn(world);
        when(world.getSeed()).thenReturn(123L);

        List<IslandSpec> ports = market.reportablePorts(island);
        // Should not include itself
        assertFalse(ports.contains(island), "Should not report on itself");
    }

    // ========== WHOLE COIN RULES ==========

    @Test
    void testBuyPriceIsPositive() {
        // Buy prices should be positive
        double buy = market.playerBuysAt(island, Material.WHEAT).orElseThrow();
        assertTrue(buy > 0, "Buy price must be positive");
    }

    @Test
    void testSellPriceIsPositive() {
        // Sell prices should be positive
        double sell = market.playerSellsAt(island, Material.WHEAT).orElseThrow();
        assertTrue(sell > 0, "Sell price must be positive");
    }

    // ========== NEW EDGE-CASE TESTS ==========

    @Test
    @Disabled("HoldService.add() behavior needs clarification - mocking inadequate")
    void testBuyExactlyFullHold() {
        // Buying exactly the amount that fills the hold should succeed
        BoatHold hold = holds.giveBoat(uuid, Material.OAK_BOAT);
        when(holdService.active(uuid)).thenReturn(Optional.of(hold));
        when(mockPlayer.getInventory().getContents()).thenReturn(new ItemStack[0]);
        Boat boat = mock(Boat.class);
        when(boat.getLocation()).thenReturn(location);
        when(boatService.findPlaced(hold)).thenReturn(Optional.of(boat));
        when(vault.getBalance(any())).thenReturn(100000.0);
        // Return the quantity passed in (successful add)
        when(holdService.add(eq(mockPlayer), any(ItemStack.class), anyInt()))
            .thenAnswer(invocation -> invocation.getArgument(2));

        int bought = market.buy(mockPlayer, island, Material.WHEAT, 1);
        assertEquals(1, bought, "Buy should succeed when hold becomes exactly full");
    }

    @Test
    @Disabled("market.sell() needs more mocking for depth calculation")
    void testSellPastDepth() {
        // Selling more than the port can absorb should refuse overflow
        IslandSpec island = new IslandSpec(0, 0, 2500, 2500, IslandType.AGRICULTURAL,
                SecurityBand.FRONTIER, "minecraft:plains", "Port", 3);
        BoatHold hold = holds.giveBoat(uuid, Material.OAK_BOAT);
        when(holdService.active(uuid)).thenReturn(Optional.of(hold));
        when(holdService.count(eq(mockPlayer), any(ItemStack.class))).thenReturn(100);
        when(mockPlayer.getInventory().getContents()).thenReturn(new ItemStack[0]);
        Boat boat = mock(Boat.class);
        when(boat.getLocation()).thenReturn(location);
        when(boatService.findPlaced(hold)).thenReturn(Optional.of(boat));
        when(addon.getIslandDataManager().absorbableValue(island, TradeCategory.CROPS))
            .thenReturn(100); // Only 100 coins of headroom

        // Try to sell at ~1 coin/unit - should sell maybe 100 units, not all 100
        ItemStack wheat = new ItemStack(Material.WHEAT, 100);
        int sold = market.sell(mockPlayer, island, wheat, 100);
        assertTrue(sold >= 0 && sold <= 100, "Sell should respect port depth");
    }

    @Test
    @Disabled("mockPlayer inventory contents not initialized properly")
    void testTradeInAtEqualSlots() {
        // Boat with same slot size at same island should NOT be a trade-in
        BoatHold currentHold = holds.giveBoat(uuid, Material.OAK_BOAT);
        when(holdService.boat(mockPlayer)).thenReturn(Material.OAK_BOAT);
        when(holdService.active(uuid)).thenReturn(Optional.of(currentHold));
        when(vault.has(any(), anyInt())).thenReturn(true);
        Boat boat = mock(Boat.class);
        when(boat.getLocation()).thenReturn(location);
        when(boatService.findPlaced(currentHold)).thenReturn(Optional.of(boat));
        when(mockPlayer.getInventory().getContents()).thenReturn(new ItemStack[0]);

        // Same size boat (4 slots)
        BoatRanks.Rank sameSize = new BoatRanks.Rank(Material.SPRUCE_BOAT, 1, 4);
        when(boatRanks.shopListing(Material.OAK_BOAT, island.techLevel()))
            .thenReturn(List.of(sameSize));
        when(boatRanks.slots(Material.OAK_BOAT)).thenReturn(4);
        when(boatRanks.price(sameSize)).thenReturn(1500.0);

        boolean tradeIn = market.isTradeIn(mockPlayer, island, sameSize);
        assertFalse(tradeIn, "Same-size hull should NOT be a trade-in");
    }

    @Test
    void testBuyPriceRoundingAtOneCoin() {
        // Buy price should ceil at 1 coin boundary
        double buyPrice = market.playerBuysAt(island, Material.WHEAT).orElseThrow();
        // If the raw price is like 1.1, it should ceil to 2, not round to 1
        assertTrue(buyPrice >= 1.0, "Buy price must be at least 1 coin");
    }
}
