package world.bentobox.tradewinds.travel;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.ArrayList;
import java.util.List;

import org.bukkit.Material;
import org.bukkit.inventory.ItemStack;
import org.junit.jupiter.api.Test;

import world.bentobox.tradewinds.CommonTestSetup;

/**
 * The hold's slot arithmetic. This is where the NBT-aware hold is actually
 * testable: ItemStack serialisation does not work headlessly, but
 * {@code isSimilar}, {@code getMaxStackSize} and {@code clone} all do.
 *
 * @author tastybento
 */
class CargoStoreTest extends CommonTestSetup {

    private final List<ItemStack> store = new ArrayList<>();

    @Test
    void testConsolidatesBeforeOpeningSlots() {
        assertEquals(64, CargoStore.added(store, 3, new ItemStack(Material.WHEAT), 64));
        assertEquals(1, store.size());
        // The next 10 top up the existing stack rather than taking a new slot
        assertEquals(10, CargoStore.added(store, 3, new ItemStack(Material.WHEAT), 10));
        assertEquals(2, store.size());
        assertEquals(74, CargoStore.count(store, new ItemStack(Material.WHEAT)));
    }

    @Test
    void testSlotBudgetIsHard() {
        // Two slots of wheat is 128 and not a grain more
        assertEquals(128, CargoStore.added(store, 2, new ItemStack(Material.WHEAT), 500));
        assertEquals(2, store.size());
        assertEquals(0, CargoStore.added(store, 2, new ItemStack(Material.WHEAT), 1));
        assertEquals(0, CargoStore.added(store, 2, new ItemStack(Material.DIRT), 1));
    }

    @Test
    void testCapacityForCountsHeadroomAndFreeSlots() {
        CargoStore.added(store, 5, new ItemStack(Material.WHEAT), 100);
        // 100 wheat = 2 slots (64 + 36), so 28 headroom + 3 free slots x 64
        assertEquals(28 + 3 * 64, CargoStore.capacityFor(store, 5, new ItemStack(Material.WHEAT)));
        // A different item gets no headroom, only the free slots
        assertEquals(3 * 64, CargoStore.capacityFor(store, 5, new ItemStack(Material.DIRT)));
        // And a full store has room for nothing
        CargoStore.added(store, 5, new ItemStack(Material.DIRT), 1000);
        assertEquals(0, CargoStore.capacityFor(store, 5, new ItemStack(Material.DIRT)));
    }

    @Test
    void testUniqueItemsEachTakeASlot() {
        // A tool that does not stack occupies one slot per item, which is what
        // makes a hold full of scavenged gear behave as players expect
        assertEquals(1, CargoStore.added(store, 4, new ItemStack(Material.IRON_SWORD), 1));
        assertEquals(1, CargoStore.added(store, 4, new ItemStack(Material.IRON_SWORD), 1));
        assertEquals(2, store.size());
        assertEquals(1, new ItemStack(Material.IRON_SWORD).getMaxStackSize());
    }

    @Test
    void testRemoveEmptiesPartialsAndTidiesUp() {
        CargoStore.added(store, 5, new ItemStack(Material.WHEAT), 100);
        assertEquals(2, store.size());
        // Taking 64 clears the first stack entirely and leaves no empty slot
        assertEquals(64, CargoStore.removed(store, new ItemStack(Material.WHEAT), 64));
        assertEquals(1, store.size());
        assertEquals(36, CargoStore.count(store, new ItemStack(Material.WHEAT)));
        // Asking for more than is there takes what there is
        assertEquals(36, CargoStore.removed(store, new ItemStack(Material.WHEAT), 500));
        assertTrue(store.isEmpty());
    }

    @Test
    void testRemoveOnlyTakesWhatMatches() {
        CargoStore.added(store, 5, new ItemStack(Material.WHEAT), 10);
        CargoStore.added(store, 5, new ItemStack(Material.DIRT), 10);
        assertEquals(0, CargoStore.removed(store, new ItemStack(Material.STONE), 5));
        assertEquals(10, CargoStore.count(store, new ItemStack(Material.WHEAT)));
        assertEquals(10, CargoStore.count(store, new ItemStack(Material.DIRT)));
    }

    @Test
    void testStacksTogetherIsSimilarityNotType() {
        assertTrue(CargoStore.stacksTogether(new ItemStack(Material.WHEAT, 1), new ItemStack(Material.WHEAT, 40)));
        assertFalse(CargoStore.stacksTogether(new ItemStack(Material.WHEAT), new ItemStack(Material.DIRT)));
        assertFalse(CargoStore.stacksTogether(null, new ItemStack(Material.WHEAT)));
    }

    @Test
    void testNothingIsAddedForNonsense() {
        // NB: no AIR ItemStack is constructed anywhere - MockBukkit throws
        // AbstractMethodError on new ItemStack(Material.AIR), so air is only ever
        // detected on items handed to us, never manufactured
        assertEquals(0, CargoStore.added(store, 5, null, 10));
        assertEquals(0, CargoStore.added(store, 5, new ItemStack(Material.WHEAT), 0));
        assertEquals(0, CargoStore.added(store, 5, new ItemStack(Material.WHEAT), -5));
        assertEquals(0, CargoStore.removed(store, null, 5));
        assertTrue(store.isEmpty());
    }

    @Test
    void testCompactDropsEmpties() {
        store.add(new ItemStack(Material.WHEAT, 5));
        store.add(null);
        CargoStore.compact(store);
        assertEquals(1, store.size());
    }

    @Test
    void testPlainGoodsRoundTripAsThemselves() {
        // The trap this design avoids: a stored copy must still consolidate with
        // an identical item arriving later, or every sale finds nothing aboard
        assertTrue(CargoStore.isPlain(new ItemStack(Material.WHEAT)));
        ItemStack stored = CargoStore.copyOf(new ItemStack(Material.WHEAT), 7);
        assertEquals(7, stored.getAmount());
        assertTrue(CargoStore.stacksTogether(stored, new ItemStack(Material.WHEAT)));
        assertTrue(CargoStore.stacksTogether(new ItemStack(Material.WHEAT), stored));
    }
}
