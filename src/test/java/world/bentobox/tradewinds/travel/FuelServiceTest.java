package world.bentobox.tradewinds.travel;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import org.bukkit.Material;
import org.bukkit.entity.ChestBoat;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.PlayerInventory;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import world.bentobox.tradewinds.CommonTestSetup;
import world.bentobox.tradewinds.Settings;
import world.bentobox.tradewinds.TradeWinds;

/**
 * Tests fuel accounting: hold-only sourcing, cheapest-first consumption, the
 * lava-bucket rule, and the no-fuel-outside-the-hold invariant.
 *
 * @author tastybento
 */
class FuelServiceTest extends CommonTestSetup {

    private TradeWinds addon;
    private FuelService service;
    private ChestBoat boat;
    private Inventory boatInv;
    private ItemStack coal;
    private ItemStack logs;
    private ItemStack lava;

    @Override
    @BeforeEach
    public void setUp() throws Exception {
        super.setUp();
        addon = mock(TradeWinds.class);
        when(addon.getSettings()).thenReturn(new Settings());
        service = new FuelService(addon);

        coal = new ItemStack(Material.COAL, 4);          // 32 units
        logs = new ItemStack(Material.OAK_LOG, 10);      // 10 units
        lava = new ItemStack(Material.LAVA_BUCKET, 1);   // 100 units

        boat = mock(ChestBoat.class);
        boatInv = mock(Inventory.class);
        when(boat.getInventory()).thenReturn(boatInv);
        when(boatInv.getContents()).thenReturn(new ItemStack[] { coal, logs, lava, null });
        when(mockPlayer.getVehicle()).thenReturn(boat);
        // Player pockets: loose coal that must NOT count (not in the hold)
        PlayerInventory pockets = mock(PlayerInventory.class);
        when(pockets.getContents()).thenReturn(new ItemStack[] { new ItemStack(Material.COAL, 64), null });
        when(mockPlayer.getInventory()).thenReturn(pockets);
    }

    @Test
    void testHoldFuelCountsOnlyTheHold() {
        // 32 + 10 + 100 = 142; the 64 pocket coal (512 units) must not count
        assertEquals(142.0, service.holdFuel(mockPlayer));
    }

    @Test
    void testNoBoatNoFuel() {
        when(mockPlayer.getVehicle()).thenReturn(null);
        assertEquals(0.0, service.holdFuel(mockPlayer));
        assertFalse(service.consume(mockPlayer, 1));
    }

    @Test
    void testConsumeCheapestFirst() {
        // 12 units: all 10 logs (1 each), then 1 coal (8) - overshoot burned
        assertTrue(service.consume(mockPlayer, 12));
        assertEquals(0, logs.getAmount());
        assertEquals(3, coal.getAmount());
        assertEquals(Material.LAVA_BUCKET, lava.getType());
    }

    @Test
    void testConsumeRefusesWhenShort() {
        assertFalse(service.consume(mockPlayer, 143));
        // Nothing was removed
        assertEquals(4, coal.getAmount());
        assertEquals(10, logs.getAmount());
    }

    @Test
    void testLavaBucketLeavesEmptyBucket() {
        // 140 units forces the lava bucket to burn
        assertTrue(service.consume(mockPlayer, 140));
        assertEquals(Material.BUCKET, lava.getType());
    }

    @Test
    void testFuelValues() {
        assertEquals(8.0, service.fuelValue(Material.COAL));
        assertEquals(1.0, service.fuelValue(Material.OAK_LOG));
        assertEquals(100.0, service.fuelValue(Material.LAVA_BUCKET));
        assertEquals(0.0, service.fuelValue(Material.DIAMOND));
        assertEquals(0.0, service.fuelValue(Material.DIRT));
    }
}
