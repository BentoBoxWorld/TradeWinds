package world.bentobox.tradewinds.travel;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

import org.bukkit.Material;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.PlayerInventory;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import world.bentobox.tradewinds.CommonTestSetup;
import world.bentobox.tradewinds.Settings;
import world.bentobox.tradewinds.TradeWinds;
import world.bentobox.tradewinds.TestHolds;

/**
 * Tests fuel accounting against the virtual hold's fuel slots: hold-only
 * sourcing (pockets never count), cheapest-first consumption, the lava-bucket
 * rule, and refusing rather than part-burning when short.
 *
 * @author tastybento
 */
class FuelServiceTest extends CommonTestSetup {

    private TradeWinds addon;
    private FuelService service;
    private HoldService hold;
    private PlayerInventory pockets;

    @Override
    @BeforeEach
    public void setUp() throws Exception {
        super.setUp();
        addon = mock(TradeWinds.class);
        when(addon.getSettings()).thenReturn(new Settings());
        when(addon.getBoatRanks()).thenReturn(new BoatRanks(addon));
        TestHolds holds = TestHolds.install(addon);
        service = new FuelService(addon);
        when(addon.getFuelService()).thenReturn(service);
        hold = new HoldService(addon);
        when(addon.getHoldService()).thenReturn(hold);
        // Player pockets: loose coal that must NOT count (not in the hold),
        // and room for returned buckets
        pockets = mock(PlayerInventory.class);
        when(pockets.addItem(any())).thenReturn(new HashMap<>());
        when(mockPlayer.getInventory()).thenReturn(pockets);
        // The fuel row: 4 coal (32) + 10 logs (10) + 1 lava bucket (100) = 142
        holds.giveBoat(uuid, Material.OAK_BOAT);
        hold.addFuel(mockPlayer, Material.COAL, 4);
        hold.addFuel(mockPlayer, Material.OAK_LOG, 10);
        hold.addFuel(mockPlayer, Material.LAVA_BUCKET, 1);
    }

    @Test
    void testHoldFuelCountsOnlyTheFuelSlots() {
        assertEquals(142.0, service.holdFuel(mockPlayer));
        // Cargo coal is cargo, not fuel
        hold.add(mockPlayer, Material.COAL, 64);
        assertEquals(142.0, service.holdFuel(mockPlayer));
    }

    @Test
    void testConsumeCheapestFirst() {
        // 12 units: all 10 logs (1 each), then 1 coal (8) - overshoot burned
        assertTrue(service.consume(mockPlayer, 12));
        Map<Material, Integer> left = hold.fuelContents(uuid);
        assertFalse(left.containsKey(Material.OAK_LOG));
        assertEquals(3, (int) left.get(Material.COAL));
        assertEquals(1, (int) left.get(Material.LAVA_BUCKET));
    }

    @Test
    void testConsumeRefusesWhenShort() {
        assertFalse(service.consume(mockPlayer, 143));
        assertEquals(142.0, service.holdFuel(mockPlayer), "Nothing burned on refusal");
    }

    @Test
    void testLavaBucketLeavesEmptyBucket() {
        // 140 burns everything including the lava; the empty bucket comes back
        assertTrue(service.consume(mockPlayer, 142));
        assertEquals(0.0, service.holdFuel(mockPlayer));
        org.mockito.ArgumentCaptor<ItemStack> given = org.mockito.ArgumentCaptor.forClass(ItemStack.class);
        org.mockito.Mockito.verify(pockets).addItem(given.capture());
        assertEquals(Material.BUCKET, given.getValue().getType());
    }

    @Test
    void testEmptyFuelRowMeansNoWarp() {
        hold.removeFuel(uuid, Material.COAL, 999);
        hold.removeFuel(uuid, Material.OAK_LOG, 999);
        hold.removeFuel(uuid, Material.LAVA_BUCKET, 999);
        assertEquals(0.0, service.holdFuel(mockPlayer));
        assertFalse(service.consume(mockPlayer, 1));
    }
}
