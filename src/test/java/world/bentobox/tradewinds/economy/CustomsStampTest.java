package world.bentobox.tradewinds.economy;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import org.bukkit.Material;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataContainer;
import org.bukkit.persistence.PersistentDataType;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import world.bentobox.tradewinds.CommonTestSetup;
import world.bentobox.tradewinds.Settings;
import world.bentobox.tradewinds.TradeWinds;
import world.bentobox.tradewinds.dataobjects.IslandDataManager;

/**
 * The customs stamp: only trader-bought (stamped) goods are sellable - except
 * the merchandise the customs office pretends not to know about.
 *
 * @author tastybento
 */
class CustomsStampTest extends CommonTestSetup {

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

    private ItemStack stack(Material material, boolean stamped) {
        ItemStack stack = mock(ItemStack.class);
        when(stack.getType()).thenReturn(material);
        when(stack.hasItemMeta()).thenReturn(true);
        ItemMeta meta = mock(ItemMeta.class);
        PersistentDataContainer pdc = mock(PersistentDataContainer.class);
        when(pdc.has(MarketService.STAMP_KEY, PersistentDataType.STRING)).thenReturn(stamped);
        when(meta.getPersistentDataContainer()).thenReturn(pdc);
        when(stack.getItemMeta()).thenReturn(meta);
        return stack;
    }

    @Test
    void testOnlyStampedGoodsAreSellable() {
        assertTrue(service.sellableFilter().test(stack(Material.WHEAT, true)));
        // Homegrown wheat is for eating, not selling
        assertFalse(service.sellableFilter().test(stack(Material.WHEAT, false)));
        assertFalse(service.sellableFilter().test(stack(Material.DIAMOND, false)));
    }

    @Test
    void testContrabandExceptionSellsUnstamped() {
        // Homegrown sugar is the smallholder economy - sellable unstamped
        assertTrue(service.sellableFilter().test(stack(Material.SUGAR, false)));
        // ...but only while the illegal trade exists at all (family servers)
        settings.setIllegalTradeEnabled(false);
        assertFalse(service.sellableFilter().test(stack(Material.SUGAR, false)));
        // Stamped sugar (bought legitimately) is always fine
        assertTrue(service.sellableFilter().test(stack(Material.SUGAR, true)));
    }
}
