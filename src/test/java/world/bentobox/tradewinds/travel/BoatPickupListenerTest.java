package world.bentobox.tradewinds.travel;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.HashMap;
import java.util.List;

import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.entity.Boat;
import org.bukkit.entity.ChestBoat;
import org.bukkit.entity.Player;
import org.bukkit.event.player.PlayerTeleportEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.PlayerInventory;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import world.bentobox.tradewinds.CommonTestSetup;
import world.bentobox.tradewinds.TestHolds;
import world.bentobox.tradewinds.TradeWinds;

/**
 * Tests boat pickup on teleport: hull and cargo travel with the player; the
 * warp path (already dismounted) and shared boats are untouched.
 *
 * @author tastybento
 */
class BoatPickupListenerTest extends CommonTestSetup {

    private TradeWinds addon;
    private BoatPickupListener listener;
    private PlayerInventory inventory;

    @Override
    @BeforeEach
    public void setUp() throws Exception {
        super.setUp();
        addon = mock(TradeWinds.class);
        when(addon.getOverWorld()).thenReturn(world);
        TestHolds.install(addon);
        when(addon.getBoatService()).thenReturn(new BoatService(addon));
        listener = new BoatPickupListener(addon);
        inventory = mock(PlayerInventory.class);
        when(inventory.addItem(any(ItemStack.class))).thenReturn(new HashMap<>());
        when(mockPlayer.getInventory()).thenReturn(inventory);
    }

    private PlayerTeleportEvent teleport() {
        return new PlayerTeleportEvent(mockPlayer, new Location(world, 0, 70, 0),
                new Location(world, 500, 70, 500), PlayerTeleportEvent.TeleportCause.COMMAND);
    }

    private Boat boat(Class<? extends Boat> clazz) {
        Boat boat = mock(clazz);
        when(boat.getWorld()).thenReturn(world);
        when(boat.getPassengers()).thenReturn(List.of(mockPlayer));
        when(boat.getType()).thenReturn(org.bukkit.entity.EntityType.OAK_BOAT);
        // A real entity ALWAYS has a container (the API says never-null, and
        // production code is entitled to lean on that) - so the mock must too
        org.bukkit.persistence.PersistentDataContainer pdc =
                mock(org.bukkit.persistence.PersistentDataContainer.class);
        when(boat.getPersistentDataContainer()).thenReturn(pdc);
        when(mockPlayer.getVehicle()).thenReturn(boat);
        return boat;
    }

    @Test
    void testBoatPickedUpOnTeleport() {
        Boat boat = boat(Boat.class);
        listener.onTeleport(teleport());
        verify(boat).remove();
        verify(inventory).addItem(any(ItemStack.class));
    }

    @Test
    void testChestBoatTakesOnlyItsHull() {
        // The cargo is the RECORD's now, not the chest boat's real inventory,
        // so a teleport carries the hull alone - and the hull carries the id
        ChestBoat boat = (ChestBoat) boat(ChestBoat.class);
        when(boat.getType()).thenReturn(org.bukkit.entity.EntityType.OAK_CHEST_BOAT);
        Inventory cargo = mock(Inventory.class);
        when(boat.getInventory()).thenReturn(cargo);
        listener.onTeleport(teleport());
        verify(boat).remove();
        verify(cargo, never()).clear();
        verify(inventory, times(1)).addItem(any(ItemStack.class));
    }

    @Test
    void testSharedBoatNeverConfiscated() {
        Boat boat = boat(Boat.class);
        Player other = mock(Player.class);
        when(boat.getPassengers()).thenReturn(List.of(mockPlayer, other));
        listener.onTeleport(teleport());
        verify(boat, never()).remove();
        verify(mockPlayer).leaveVehicle();
    }

    @Test
    void testUnmountedTeleportUntouched() {
        when(mockPlayer.getVehicle()).thenReturn(null);
        listener.onTeleport(teleport());
        verify(inventory, never()).addItem(any(ItemStack.class));
    }

    @Test
    void testBoatItemMapping() {
        Boat boat = mock(Boat.class);
        when(boat.getType()).thenReturn(org.bukkit.entity.EntityType.CHERRY_BOAT);
        assertEquals(Material.CHERRY_BOAT, BoatPickupListener.boatItem(boat).getType());
    }
}
