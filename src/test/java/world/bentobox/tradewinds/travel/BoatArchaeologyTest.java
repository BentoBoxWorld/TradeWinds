package world.bentobox.tradewinds.travel;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.List;

import org.bukkit.GameMode;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.entity.Boat;
import org.bukkit.entity.Item;
import org.bukkit.event.entity.EntityPickupItemEvent;
import org.bukkit.event.entity.EntityPlaceEvent;
import org.bukkit.event.entity.PlayerDeathEvent;
import org.bukkit.event.vehicle.VehicleDestroyEvent;
import org.bukkit.event.vehicle.VehicleEnterEvent;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataContainer;
import org.bukkit.persistence.PersistentDataType;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import world.bentobox.tradewinds.CommonTestSetup;
import world.bentobox.tradewinds.Settings;
import world.bentobox.tradewinds.TestHolds;
import world.bentobox.tradewinds.TradeWinds;
import world.bentobox.tradewinds.dataobjects.BoatHold;
import world.bentobox.tradewinds.encounters.EncounterService;

/**
 * The 2026-08-06 archaeology, as regression tests. A sailor died 4,400
 * blocks from home carrying their boat: the chart pointed forever at the
 * dock where the hull was last PLACED, the death drop held TWO copies of
 * the hull (creative placement does not consume the item), and the pirate
 * boat from an old encounter had minted itself a database record. Each dig
 * finding gets a guard here.
 *
 * @author tastybento
 */
class BoatArchaeologyTest extends CommonTestSetup {

    private static final String WORLD_NAME = "tradewinds_world";

    private TradeWinds addon;
    private TestHolds holds;
    private BoatService service;
    private BoatListener listener;
    private BoatHold hold;

    @Override
    @BeforeEach
    public void setUp() throws Exception {
        super.setUp();
        addon = mock(TradeWinds.class);
        when(addon.getSettings()).thenReturn(new Settings());
        when(addon.getBoatRanks()).thenReturn(new BoatRanks(addon));
        holds = TestHolds.install(addon);
        when(addon.getFuelService()).thenReturn(new FuelService(addon));
        service = new BoatService(addon);
        when(addon.getBoatService()).thenReturn(service);
        when(addon.getHoldService()).thenReturn(new HoldService(addon));
        when(addon.getOverWorld()).thenReturn(world);
        when(world.getName()).thenReturn(WORLD_NAME);
        listener = new BoatListener(addon);

        hold = holds.giveBoat(uuid, Material.OAK_BOAT);
        // Last seen moored at the dock, nowhere near where things will happen
        hold.setWorld(WORLD_NAME);
        hold.setX(9642);
        hold.setY(70);
        hold.setZ(12955);
    }

    /** An item stack mocked as the stamped avatar of the hold. */
    private ItemStack stamped() {
        ItemStack stack = mock(ItemStack.class);
        ItemMeta meta = mock(ItemMeta.class);
        PersistentDataContainer pdc = mock(PersistentDataContainer.class);
        when(pdc.get(BoatService.BOAT_ID_KEY, PersistentDataType.STRING)).thenReturn(hold.getUniqueId());
        when(meta.getPersistentDataContainer()).thenReturn(pdc);
        when(stack.hasItemMeta()).thenReturn(true);
        when(stack.getItemMeta()).thenReturn(meta);
        when(stack.getType()).thenReturn(Material.OAK_BOAT);
        return stack;
    }

    private Boat boat(boolean encounter) {
        Boat boat = mock(Boat.class);
        PersistentDataContainer pdc = mock(PersistentDataContainer.class);
        when(pdc.get(BoatService.BOAT_ID_KEY, PersistentDataType.STRING)).thenReturn(hold.getUniqueId());
        when(pdc.has(EncounterService.ENCOUNTER_KEY, PersistentDataType.STRING)).thenReturn(encounter);
        when(boat.getPersistentDataContainer()).thenReturn(pdc);
        when(boat.getWorld()).thenReturn(world);
        when(boat.getLocation()).thenReturn(location);
        when(boat.getPassengers()).thenReturn(List.of());
        return boat;
    }

    @Test
    void testDeathDropMovesTheChartToTheWreckSite() {
        // Died at spawn-mock (0,0,0) carrying the boat; the record said the dock
        ItemStack aboard = stamped();
        PlayerDeathEvent event = mock(PlayerDeathEvent.class);
        when(event.getEntity()).thenReturn(mockPlayer);
        when(event.getDrops()).thenReturn(List.of(aboard));

        listener.onDeath(event);

        assertEquals(0, hold.getX(), "The chart must point at the wreck, not the dock");
        assertEquals(0, hold.getZ());
        // And NO clock starts: death drops freeze in unloaded chunks, and a
        // wall-clock TTL would delete the cargo record while the item keeps
        assertEquals(0, hold.getExpiresAt());
    }

    @Test
    void testDeathWithoutABoatAboardChangesNothing() {
        PlayerDeathEvent event = mock(PlayerDeathEvent.class);
        when(event.getEntity()).thenReturn(mockPlayer);
        ItemStack plain = mock(ItemStack.class);
        when(event.getDrops()).thenReturn(List.of(plain));

        listener.onDeath(event);

        assertEquals(9642, hold.getX(), "A kit without the boat must not move its record");
    }

    @Test
    void testPickupDissolvesADuplicateHull() {
        // Already carrying the boat; its double lies on the ground
        ItemStack carried = stamped();
        ItemStack doubled = stamped();
        when(inv.getContents()).thenReturn(new ItemStack[] { carried });
        Item ground = mock(Item.class);
        when(ground.getItemStack()).thenReturn(doubled);
        when(ground.getLocation()).thenReturn(location);
        EntityPickupItemEvent event = mock(EntityPickupItemEvent.class);
        when(event.getEntity()).thenReturn(mockPlayer);
        when(event.getItem()).thenReturn(ground);

        listener.onPickup(event);

        verify(event).setCancelled(true);
        verify(ground).remove();
    }

    @Test
    void testCreativePlacementStripsTheLeftoverCopy() {
        // Creative placement does not consume the item: after the entity is
        // stamped, the pack still holds a live copy of the same hull
        when(mockPlayer.getGameMode()).thenReturn(GameMode.CREATIVE);
        ItemStack used = stamped();
        ItemStack leftover = stamped();
        when(inv.getItemInMainHand()).thenReturn(used);
        when(inv.getContents()).thenReturn(new ItemStack[] { leftover });
        Boat placed = boat(false);
        EntityPlaceEvent event = mock(EntityPlaceEvent.class);
        when(event.getEntity()).thenReturn(placed);
        when(event.getPlayer()).thenReturn(mockPlayer);
        when(event.getHand()).thenReturn(EquipmentSlot.HAND);

        listener.onPlace(event);

        verify(leftover).setAmount(0);
    }

    @Test
    void testSurvivalPlacementLeavesThePackAlone() {
        // Vanilla consumes the placed item in survival; anything else with
        // the id would be a real second item, and pickup dedupe handles those
        when(mockPlayer.getGameMode()).thenReturn(GameMode.SURVIVAL);
        ItemStack used = stamped();
        when(inv.getItemInMainHand()).thenReturn(used);
        Boat placed = boat(false);
        EntityPlaceEvent event = mock(EntityPlaceEvent.class);
        when(event.getEntity()).thenReturn(placed);
        when(event.getPlayer()).thenReturn(mockPlayer);
        when(event.getHand()).thenReturn(EquipmentSlot.HAND);

        listener.onPlace(event);

        verify(used, never()).setAmount(0);
    }

    @Test
    void testEncounterHullSplintersWithoutARecord() {
        int before = addon.getHoldManager().allBoats().size();
        Boat prop = boat(true);
        VehicleDestroyEvent event = mock(VehicleDestroyEvent.class);
        when(event.getVehicle()).thenReturn(prop);

        listener.onDestroy(event);

        verify(event).setCancelled(true);
        verify(prop).remove();
        assertEquals(before, addon.getHoldManager().allBoats().size(),
                "A pirate's boat must never mint a database record");
    }

    @Test
    void testEncounterHullCannotBeCaptured() {
        int before = addon.getHoldManager().allBoats().size();
        Boat prop = boat(true);
        VehicleEnterEvent event = mock(VehicleEnterEvent.class);
        when(event.getVehicle()).thenReturn(prop);
        when(event.getEntered()).thenReturn(mockPlayer);

        listener.onEnter(event);

        verify(event, never()).setCancelled(true);
        assertEquals(before, addon.getHoldManager().allBoats().size());
    }

    @Test
    void testShedRemovesEveryCopyAndDropsExactlyOne() {
        ItemStack first = stamped();
        ItemStack second = stamped();
        when(inv.getContents()).thenReturn(new ItemStack[] { first, second });

        service.shedCarriedHull(mockPlayer, hold);

        verify(first).setAmount(0);
        verify(second).setAmount(0);
        verify(world).dropItemNaturally(any(Location.class), any(ItemStack.class));
        assertEquals(0, hold.getX(), "The shed hull's position is the sailor's feet");
    }
}
