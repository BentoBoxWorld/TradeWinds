package world.bentobox.tradewinds.travel;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.util.HashMap;
import java.util.Map;

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
 * Tests the hold: chest-boat contents count, pockets never do, removal takes
 * from the boat, addition respects inventory space.
 *
 * @author tastybento
 */
class HoldServiceTest extends CommonTestSetup {

    private TradeWinds addon;
    private HoldService service;
    private Inventory boatInv;
    private ItemStack wheat;
    private ItemStack coal;

    @Override
    @BeforeEach
    public void setUp() throws Exception {
        super.setUp();
        addon = mock(TradeWinds.class);
        when(addon.getSettings()).thenReturn(new Settings());
        service = new HoldService(addon);

        wheat = new ItemStack(Material.WHEAT, 40);
        coal = new ItemStack(Material.COAL, 10);
        ChestBoat boat = mock(ChestBoat.class);
        boatInv = mock(Inventory.class);
        when(boat.getInventory()).thenReturn(boatInv);
        when(boatInv.getContents()).thenReturn(new ItemStack[] { wheat, coal, null });
        when(mockPlayer.getVehicle()).thenReturn(boat);
        // Pockets full of diamonds that must never count
        PlayerInventory pockets = mock(PlayerInventory.class);
        when(pockets.getContents()).thenReturn(new ItemStack[] { new ItemStack(Material.DIAMOND, 64), null });
        when(mockPlayer.getInventory()).thenReturn(pockets);
    }

    @Test
    void testContentsAreHoldOnly() {
        Map<Material, Integer> contents = service.contents(mockPlayer);
        assertEquals(40, contents.get(Material.WHEAT));
        assertEquals(10, contents.get(Material.COAL));
        // Pocket diamonds are invisible to the market (spec principle 1)
        assertEquals(0, service.count(mockPlayer, Material.DIAMOND));
    }

    @Test
    void testNoBoatNoHold() {
        when(mockPlayer.getVehicle()).thenReturn(null);
        assertEquals(0, service.count(mockPlayer, Material.WHEAT));
        assertEquals(0, service.remove(mockPlayer, Material.WHEAT, 10));
    }

    @Test
    void testRemove() {
        assertEquals(25, service.remove(mockPlayer, Material.WHEAT, 25));
        assertEquals(15, wheat.getAmount());
        // Removing more than present takes what there is
        assertEquals(15, service.remove(mockPlayer, Material.WHEAT, 99));
    }

    @Test
    void testFreeSpace() {
        // Boat: wheat 40/64 (+24), coal 10/64 (+54), one empty slot (+64)
        when(boatInv.getStorageContents()).thenReturn(new ItemStack[] { wheat, coal, null });
        assertEquals(24 + 54 + 64, service.freeSpace(mockPlayer));
        // No boat, no bundles: no hold at all
        when(mockPlayer.getVehicle()).thenReturn(null);
        assertEquals(0, service.freeSpace(mockPlayer));
    }

    @Test
    void testAddUsesBoatInventory() {
        when(boatInv.addItem(org.mockito.ArgumentMatchers.any(ItemStack.class))).thenReturn(new HashMap<>());
        assertEquals(16, service.add(mockPlayer, new ItemStack(Material.STONE, 16)));
    }

    @Test
    void testAddReportsLeftover() {
        // Boat rejects 10 of 16
        HashMap<Integer, ItemStack> leftover = new HashMap<>();
        leftover.put(0, new ItemStack(Material.STONE, 10));
        when(boatInv.addItem(org.mockito.ArgumentMatchers.any(ItemStack.class))).thenReturn(leftover);
        assertEquals(6, service.add(mockPlayer, new ItemStack(Material.STONE, 16)));
    }
}
