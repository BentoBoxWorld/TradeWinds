package world.bentobox.tradewinds.economy;

import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.util.List;

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
 * The fuel guarantee: every island type sells at least one warp fuel, so
 * anyone with money can always buy their way off rowing.
 *
 * @author tastybento
 */
class SaleCatalogTest extends CommonTestSetup {

    private TradeWinds addon;
    private MarketService service;
    private Settings settings;

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

    private IslandSpec spec(IslandType type) {
        return new IslandSpec(0, 0, 2500, 2500, type, SecurityBand.SAFE, "minecraft:plains", "Test");
    }

    @Test
    void testEveryIslandSellsFuelAndFood() {
        for (IslandType type : IslandType.values()) {
            List<Material> shelf = new java.util.ArrayList<>(service.saleCatalog(spec(type)));
            shelf.addAll(service.outfitterCatalog(spec(type)));
            assertTrue(shelf.stream()
                    .anyMatch(m -> settings.getFuelValues().getOrDefault(m.name(), 0.0) > 0),
                    type + " must sell some kind of fuel");
            assertTrue(shelf.contains(Material.BREAD) || shelf.contains(Material.COOKED_BEEF)
                    || shelf.contains(Material.COOKED_COD), type + " must sell food");
        }
    }

    @Test
    void testCharcoalIsTheFallbackNotTheRule() {
        // FOREST trades logs (fuel) already - no charcoal on the shelf
        assertTrue(!service.outfitterCatalog(spec(IslandType.FOREST)).contains(Material.CHARCOAL));
        // MINING trades coal - no charcoal
        assertTrue(!service.outfitterCatalog(spec(IslandType.MINING)).contains(Material.CHARCOAL));
        // LUXURY trades nothing burnable: charcoal appears on the outfitter shelf
        assertTrue(service.outfitterCatalog(spec(IslandType.LUXURY)).contains(Material.CHARCOAL));
        assertTrue(service.outfitterCatalog(spec(IslandType.AGRICULTURAL)).contains(Material.CHARCOAL));
    }

    @Test
    void testOutfitterFitsTheDialog() {
        for (IslandType type : IslandType.values()) {
            assertTrue(service.outfitterCatalog(spec(type)).size() <= 8,
                    type + " outfitter must fit the dialog without scrolling");
        }
    }

    @Test
    void testBoatLadderShop() {
        world.bentobox.tradewinds.travel.BoatRanks ranks = new world.bentobox.tradewinds.travel.BoatRanks(addon);
        // The full ladder: 20 rungs, bamboo raft to pale oak chest boat
        var ladder = ranks.ladder();
        assertTrue(ladder.size() == 20, "Ladder has " + ladder.size() + " rungs");
        assertTrue(ladder.get(0).material() == Material.BAMBOO_RAFT && ladder.get(0).slots() == 2);
        assertTrue(ladder.get(19).material() == Material.PALE_OAK_CHEST_BOAT && ladder.get(19).slots() == 21);
        // Quadratic prices, in whole coins: raft 1000, top boat 110250
        assertTrue(ranks.price(ladder.get(0)) == 1000.0);
        assertTrue(ranks.price(ladder.get(19)) == 110250.0);
        // Shops list bigger-only, tech-gated: TL1 sells ranks 1-3 only
        var tl1 = ranks.shopListing(null, 1);
        assertTrue(tl1.size() == 3 && tl1.get(2).material() == Material.SPRUCE_BOAT);
        // With a spruce boat at TL1 there is nothing left to buy
        assertTrue(ranks.shopListing(Material.SPRUCE_BOAT, 1).isEmpty());
        // TL7 sells everything bigger than yours
        assertTrue(ranks.shopListing(Material.CHERRY_CHEST_BOAT, 7).size() == 1);
        assertTrue(ranks.shopListing(Material.PALE_OAK_CHEST_BOAT, 7).isEmpty(), "Top of the tree");
    }

    @Test
    void testSmithsArmYou() {
        List<Material> smith = service.outfitterCatalog(spec(IslandType.INDUSTRIAL));
        assertTrue(smith.contains(Material.IRON_SWORD));
        assertTrue(smith.contains(Material.IRON_CHESTPLATE));
    }
}
