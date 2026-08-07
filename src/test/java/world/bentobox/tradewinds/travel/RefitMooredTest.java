package world.bentobox.tradewinds.travel;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.List;

import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.entity.Boat;
import org.bukkit.entity.EntityType;
import org.bukkit.entity.Item;
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

/**
 * The moored-refit bug (playtest 2026-08-04): buy a bigger hull with your
 * boat left at the dock, and the record upgraded while the old hull kept
 * floating - the swap searched a 6-block box around the LAST-REMEMBERED
 * position, and dismounted boats glide, drift and go stale. The fix finds
 * the placed avatar by IDENTITY among the world's loaded entities.
 *
 * @author tastybento
 */
class RefitMooredTest extends CommonTestSetup {

    private static final String WORLD_NAME = "tradewinds_world";

    private TradeWinds addon;
    private TestHolds holds;
    private BoatService service;
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

        when(world.getName()).thenReturn(WORLD_NAME);
        mockedBukkit.when(() -> Bukkit.getWorld(WORLD_NAME)).thenReturn(world);

        hold = holds.giveBoat(uuid, Material.OAK_BOAT);
        // Last seen at the dock - deliberately NOWHERE NEAR where the boat
        // actually floats now, which is what broke the old box search
        hold.setWorld(WORLD_NAME);
        hold.setX(100);
        hold.setY(70);
        hold.setZ(100);
    }

    /** A boat entity stamped with the hold's id, floating wherever it likes. */
    private Boat mooredBoat(int x, int z) {
        Boat boat = mock(Boat.class);
        PersistentDataContainer pdc = mock(PersistentDataContainer.class);
        when(pdc.get(BoatService.BOAT_ID_KEY, PersistentDataType.STRING)).thenReturn(hold.getUniqueId());
        when(boat.getPersistentDataContainer()).thenReturn(pdc);
        Location at = mock(Location.class);
        when(at.getWorld()).thenReturn(world);
        when(at.getBlockX()).thenReturn(x);
        when(at.getBlockY()).thenReturn(70);
        when(at.getBlockZ()).thenReturn(z);
        when(boat.getLocation()).thenReturn(at);
        when(boat.getPassengers()).thenReturn(List.of());
        return boat;
    }

    @Test
    void testMooredRefitSwapsTheDriftedHull() {
        // The boat drifted 40 blocks from where the chart last saw it
        Boat old = mooredBoat(140, 100);
        when(world.getEntitiesByClass(Boat.class)).thenReturn(List.of(old));
        Boat fresh = mooredBoat(140, 100);
        when(world.spawnEntity(any(Location.class), eq(EntityType.SPRUCE_BOAT))).thenReturn(fresh);
        // Not riding, not carrying: the pack is empty
        when(inv.getContents()).thenReturn(new ItemStack[0]);

        service.refit(mockPlayer, hold, Material.SPRUCE_BOAT);

        assertEquals("SPRUCE_BOAT", hold.getMaterial());
        verify(old).remove();
        verify(world).spawnEntity(any(Location.class), eq(EntityType.SPRUCE_BOAT));
        // The chart is honest again: position re-remembered from the entity
        assertEquals(140, hold.getX());
    }

    @Test
    void testDroppedItemHullIsRefitInPlace() {
        when(world.getEntitiesByClass(Boat.class)).thenReturn(List.of());
        // The hull lies on the sand as a dropped item
        ItemStack stack = mock(ItemStack.class);
        ItemMeta meta = mock(ItemMeta.class);
        PersistentDataContainer pdc = mock(PersistentDataContainer.class);
        when(pdc.get(BoatService.BOAT_ID_KEY, PersistentDataType.STRING)).thenReturn(hold.getUniqueId());
        when(meta.getPersistentDataContainer()).thenReturn(pdc);
        when(stack.hasItemMeta()).thenReturn(true);
        when(stack.getItemMeta()).thenReturn(meta);
        Item item = mock(Item.class);
        when(item.getItemStack()).thenReturn(stack);
        Location at = mock(Location.class);
        when(at.getWorld()).thenReturn(world);
        when(item.getLocation()).thenReturn(at);
        when(world.getEntitiesByClass(Item.class)).thenReturn(List.of(item));
        when(inv.getContents()).thenReturn(new ItemStack[0]);

        service.refit(mockPlayer, hold, Material.SPRUCE_BOAT);

        assertEquals("SPRUCE_BOAT", hold.getMaterial());
        verify(stack).setType(Material.SPRUCE_BOAT);
        verify(item).setItemStack(stack);
        verify(world, never()).spawnEntity(any(), any(EntityType.class));
    }

    @Test
    void testFindPlacedIgnoresStrangersAndFindsByIdentity() {
        Boat stranger = mock(Boat.class);
        PersistentDataContainer none = mock(PersistentDataContainer.class);
        when(stranger.getPersistentDataContainer()).thenReturn(none);
        Boat mine = mooredBoat(5000, -3000); // half a sea away from last-seen
        when(world.getEntitiesByClass(Boat.class)).thenReturn(List.of(stranger, mine));

        assertTrue(service.findPlaced(hold).isPresent());
        assertEquals(mine, service.findPlaced(hold).orElseThrow());
        // And looking healed the chart
        assertEquals(5000, hold.getX());
        assertEquals(-3000, hold.getZ());
    }

    @Test
    void testNothingLoadedMeansNothingFound() {
        when(world.getEntitiesByClass(Boat.class)).thenReturn(List.of());
        when(world.getEntitiesByClass(Item.class)).thenReturn(List.of());
        assertTrue(service.findPlaced(hold).isEmpty());
        // The refit still updates the record and survives without a hull
        when(inv.getContents()).thenReturn(new ItemStack[0]);
        service.refit(mockPlayer, hold, Material.SPRUCE_BOAT);
        assertEquals("SPRUCE_BOAT", hold.getMaterial());
        verify(world, never()).spawnEntity(any(), any(EntityType.class));
    }

    @Test
    void testCarriedHullStillWinsOverTheWorldSearch() {
        // In the pack: the boat is wherever the sailor is - the world search
        // must not even run, or a stamped stranger could shadow the carried one
        ItemStack carried = mock(ItemStack.class);
        ItemMeta meta = mock(ItemMeta.class);
        PersistentDataContainer pdc = mock(PersistentDataContainer.class);
        when(pdc.get(BoatService.BOAT_ID_KEY, PersistentDataType.STRING)).thenReturn(hold.getUniqueId());
        when(meta.getPersistentDataContainer()).thenReturn(pdc);
        when(carried.hasItemMeta()).thenReturn(true);
        when(carried.getItemMeta()).thenReturn(meta);
        when(inv.getContents()).thenReturn(new ItemStack[] { carried });

        service.refit(mockPlayer, hold, Material.SPRUCE_BOAT);

        verify(carried).setType(Material.SPRUCE_BOAT);
        verify(world, never()).getEntitiesByClass(Boat.class);
    }
}
