package world.bentobox.tradewinds.economy;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
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
        IslandDataManager idm = mock(IslandDataManager.class);
        when(addon.getIslandDataManager()).thenReturn(idm);
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
        assertEquals(20, ladder.size(), "Ladder has " + ladder.size() + " rungs");
        assertEquals(Material.BAMBOO_RAFT, ladder.get(0).material());
        assertEquals(2, ladder.get(0).slots());
        assertEquals(Material.PALE_OAK_CHEST_BOAT, ladder.get(19).material());
        assertEquals(21, ladder.get(19).slots());
        // Quadratic prices, in whole coins: raft 1000, top boat 110250
        assertEquals(1000.0, ranks.price(ladder.get(0)));
        assertEquals(110250.0, ranks.price(ladder.get(19)));
        // Shops are tech-gated: TL1 sells ranks 1-3 only
        var tl1 = ranks.shopListing(null, 1);
        assertEquals(3, tl1.size());
        assertEquals(Material.SPRUCE_BOAT, tl1.get(2).material());
        // The yard ALWAYS sells (ruled 2026-08-05): with a spruce boat at TL1
        // the smaller hulls are still on offer - bought outright, the spruce
        // left unowned wherever it lies. Only the size you sail is excluded.
        var downgrade = ranks.shopListing(Material.SPRUCE_BOAT, 1);
        assertEquals(2, downgrade.size(), "TL1 with a spruce should offer the two smaller hulls");
        assertTrue(downgrade.stream().noneMatch(r -> r.material() == Material.SPRUCE_BOAT));
        // TL7 sells the whole ladder except the size you sail
        assertEquals(19, ranks.shopListing(Material.PALE_OAK_CHEST_BOAT, 7).size(),
                "Top of the tree still gets everything smaller");
    }

    @Test
    void testSmithsArmYou() {
        List<Material> smith = service.outfitterCatalog(spec(IslandType.INDUSTRIAL));
        assertTrue(smith.contains(Material.IRON_SWORD));
        assertTrue(smith.contains(Material.IRON_CHESTPLATE));
    }
}
