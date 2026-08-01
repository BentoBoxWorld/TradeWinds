package world.bentobox.tradewinds.economy;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.util.List;

import org.bukkit.inventory.ItemStack;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import world.bentobox.tradewinds.CommonTestSetup;
import world.bentobox.tradewinds.Settings;
import world.bentobox.tradewinds.TradeWinds;
import world.bentobox.tradewinds.dataobjects.IslandDataManager;
import world.bentobox.tradewinds.travel.HoldService;

/**
 * The recovery rules: a sailor with no hold cannot earn anything (the market
 * needs cargo space on both sides), so the first pouch must be cheap and the
 * penniless must have a way back.
 *
 * @author tastybento
 */
class CharityTest extends CommonTestSetup {

    private TradeWinds addon;
    private MarketService service;
    private HoldService hold;
    private Settings settings;

    @Override
    @BeforeEach
    public void setUp() throws Exception {
        super.setUp();
        addon = mock(TradeWinds.class);
        settings = new Settings();
        when(addon.getSettings()).thenReturn(settings);
        when(addon.getIslandDataManager()).thenReturn(mock(IslandDataManager.class));
        when(addon.getPlugin()).thenReturn(plugin);
        when(plugin.getVault()).thenReturn(java.util.Optional.empty());
        hold = mock(HoldService.class);
        when(addon.getHoldService()).thenReturn(hold);
        service = new MarketService(addon);
    }

    @Test
    void testFirstPouchIsCheapEnoughToRecover() {
        // The starting balance must comfortably cover a first pouch AND a boat,
        // or a dead player is soft-locked out of the whole economy
        assertTrue(settings.getPouchPrice() + 20 < settings.getStartingBalance(),
                "A sailor must be able to re-equip from the starting balance");
    }

    @Test
    void testPouchPriceIsFlatSoItCannotBeGamed() {
        // Pricing by carried pouches would be defeated by dropping one before
        // buying and picking it up again; the cap does the limiting instead
        for (int carried = 0; carried < 3; carried++) {
            when(hold.pouchCount(mockPlayer)).thenReturn(carried);
            assertEquals(settings.getPouchPrice(), service.pouchPrice(mockPlayer));
        }
    }

    @Test
    void testDestituteMeansNoHoldAndNoMoney() {
        when(hold.pouchCount(mockPlayer)).thenReturn(0);
        when(hold.expanders(mockPlayer)).thenReturn(List.of());
        // Without an economy there is nothing to be destitute about
        assertFalse(service.isDestitute(mockPlayer));
        // Carrying a pouch is never destitute
        when(hold.pouchCount(mockPlayer)).thenReturn(1);
        assertFalse(service.isDestitute(mockPlayer));
        // Nor is carrying an expander
        when(hold.pouchCount(mockPlayer)).thenReturn(0);
        when(hold.expanders(mockPlayer)).thenReturn(List.of(mock(ItemStack.class)));
        assertFalse(service.isDestitute(mockPlayer));
    }

    @Test
    void testCharityGoodsCannotBeSold() {
        // The pouch the harbourmaster gives is unstamped, so it cannot be sold
        // back for money - there is nothing to farm
        assertFalse(service.isStamped(service.pouchItem()));
    }
}
