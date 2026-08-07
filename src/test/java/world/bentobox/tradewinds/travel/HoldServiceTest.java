package world.bentobox.tradewinds.travel;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import org.bukkit.Material;
import org.bukkit.inventory.ItemStack;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import world.bentobox.tradewinds.CommonTestSetup;
import world.bentobox.tradewinds.Settings;
import world.bentobox.tradewinds.TradeWinds;
import world.bentobox.tradewinds.TestHolds;

/**
 * Tests the VIRTUAL hold: capacity comes from the one boat, cargo is one stack
 * per slot in the database, containers and vessels are refused, and fuel lives
 * in its own seven slots.
 *
 * @author tastybento
 */
class HoldServiceTest extends CommonTestSetup {

    private TradeWinds addon;
    private HoldService service;
    private TestHolds holds;

    @Override
    @BeforeEach
    public void setUp() throws Exception {
        super.setUp();
        addon = mock(TradeWinds.class);
        Settings settings = new Settings();
        when(addon.getSettings()).thenReturn(settings);
        when(addon.getBoatRanks()).thenReturn(new BoatRanks(addon));
        when(addon.getFuelService()).thenReturn(new FuelService(addon));
        holds = TestHolds.install(addon);
        service = new HoldService(addon);
        when(addon.getHoldService()).thenReturn(service);
    }

    @Test
    void testTheHoldIsTwoWayForYourOwnGoods() {
        // A scavenger has to be able to use their hold as a hold (2026-08-03)
        holds.giveBoat(uuid, Material.OAK_BOAT);
        ItemStack mined = new ItemStack(Material.COBBLESTONE);
        assertEquals(40, service.add(mockPlayer, mined, 40));
        assertEquals(25, service.withdraw(mockPlayer, mined, 25));
        assertEquals(15, service.count(mockPlayer, mined), "Only what was taken leaves");
        assertEquals(15, service.withdraw(mockPlayer, mined, 999), "Asking for too much takes the rest");
        assertEquals(0, service.count(mockPlayer, mined));
    }

    @Test
    void testTraderBoughtCargoStaysOneWay() {
        // The load-bearing half of the rule: speculate on a cargo and you must
        // find a buyer, not warehouse it ashore
        holds.giveBoat(uuid, Material.OAK_BOAT);
        ItemStack bought = markedStack(Material.DIAMOND);
        assertEquals(10, service.add(mockPlayer, bought, 10));
        assertEquals(-1, service.withdraw(mockPlayer, bought, 10), "Bought cargo cannot be withdrawn");
        assertEquals(10, service.count(mockPlayer, bought), "and it is still aboard");
        // Selling and destroying still work - remove() is not gated
        assertEquals(10, service.remove(mockPlayer, bought, 10));
    }

    @Test
    void testBoughtAndMinedGoodsNeverMerge() {
        // One bought diamond must not lock up twenty mined ones, so marked and
        // unmarked stacks are different goods and keep separate slots
        holds.giveBoat(uuid, Material.PALE_OAK_CHEST_BOAT);
        ItemStack bought = markedStack(Material.DIAMOND);
        ItemStack mined = new ItemStack(Material.DIAMOND);
        assertEquals(5, service.add(mockPlayer, bought, 5));
        assertEquals(20, service.add(mockPlayer, mined, 20));
        assertFalse(CargoStore.stacksTogether(bought, mined), "Provenance must break similarity");
        assertEquals(5, service.count(mockPlayer, bought));
        assertEquals(20, service.count(mockPlayer, mined));
        assertEquals(2, service.cargo(uuid).size(), "Two goods, two slots");
        // And the mined ones come out while the bought ones stay put
        assertEquals(20, service.withdraw(mockPlayer, mined, 20));
        assertEquals(5, service.count(mockPlayer, bought));
    }

    /** Every marked mock made in this test, so they can recognise each other. */
    private final java.util.Set<ItemStack> markedMocks = java.util.Collections.newSetFromMap(
            new java.util.IdentityHashMap<>());

    /**
     * A stack carrying the trader-bought mark, with a mocked PDC and a working
     * amount. Marked stacks are similar to each other and to nothing else, which
     * is what the PDC mark does on a real server.
     */
    private ItemStack markedStack(Material material) {
        ItemStack item = mock(ItemStack.class);
        org.bukkit.inventory.meta.ItemMeta meta = mock(org.bukkit.inventory.meta.ItemMeta.class);
        org.bukkit.persistence.PersistentDataContainer pdc =
                mock(org.bukkit.persistence.PersistentDataContainer.class);
        java.util.concurrent.atomic.AtomicInteger amount = new java.util.concurrent.atomic.AtomicInteger(1);
        when(item.getType()).thenReturn(material);
        when(item.getMaxStackSize()).thenReturn(material.getMaxStackSize());
        when(item.getItemMeta()).thenReturn(meta);
        when(meta.getPersistentDataContainer()).thenReturn(pdc);
        when(pdc.has(any(), any())).thenReturn(true);
        when(item.getAmount()).thenAnswer(inv -> amount.get());
        org.mockito.Mockito.doAnswer(inv -> {
            amount.set(inv.getArgument(0));
            return null;
        }).when(item).setAmount(org.mockito.ArgumentMatchers.anyInt());
        when(item.isSimilar(any())).thenAnswer(inv -> {
            Object other = inv.getArgument(0);
            return other instanceof ItemStack stack && markedMocks.contains(stack)
                    && stack.getType() == material;
        });
        when(item.clone()).thenAnswer(inv -> markedStack(material));
        markedMocks.add(item);
        return item;
    }

    @Test
    void testBoughtFuelStillFuels() {
        // "I bought coal for fuel, but I cannot take it out of the hold"
        // (playtest 2026-08-03): the purchase mark made bought coal fail
        // isSimilar against the plain stack the fuel path matched with, so the
        // transfer silently moved nothing. Fuel is exempt from the one-way rule.
        holds.giveBoat(uuid, Material.OAK_BOAT);
        ItemStack boughtCoal = markedStack(Material.COAL);
        assertEquals(16, service.add(mockPlayer, boughtCoal, 16));
        assertEquals(16, service.moveCargoToFuel(mockPlayer, boughtCoal, 16),
                "Bought coal must reach the tank");
        assertEquals(0, service.count(mockPlayer, boughtCoal), "and leave the cargo slots");
        // The tank is material-keyed - the mark evaporates - so it comes back
        // out freely, like any fuel
        assertEquals(16, service.removeFuel(uuid, Material.COAL, 16));
    }

    @Test
    void testPartialFuelTransferLeavesTheRestAboard() {
        holds.giveBoat(uuid, Material.OAK_BOAT);
        ItemStack coal = markedStack(Material.COAL);
        assertEquals(16, service.add(mockPlayer, coal, 16));
        // One lump at a time - the right-click gesture
        assertEquals(1, service.moveCargoToFuel(mockPlayer, coal, 1));
        assertEquals(15, service.count(mockPlayer, coal));
    }

    @Test
    void testNoBoatNoHold() {
        holds.clearBoat(uuid);
        assertNull(service.boat(mockPlayer));
        assertEquals(0, service.capacitySlots(mockPlayer));
        assertEquals(0, service.add(mockPlayer, Material.WHEAT, 10), "No boat means nowhere to stow");
    }

    @Test
    void testBoatSetsCapacity() {
        holds.giveBoat(uuid, Material.OAK_BOAT);
        assertEquals(3, service.capacitySlots(mockPlayer), "Oak Boat is rank 2: 3 slots");
        holds.giveBoat(uuid, Material.PALE_OAK_CHEST_BOAT);
        assertEquals(21, service.capacitySlots(mockPlayer), "Top of the ladder: 21 slots");
    }

    @Test
    void testUpgradeKeepsContents() {
        // An upgrade is a refit of the SAME hold - a bigger hull around the
        // same cargo, never a new boat
        var boat = holds.giveBoat(uuid, Material.OAK_BOAT);
        assertEquals(100, service.add(mockPlayer, Material.WHEAT, 100));
        boat.setMaterial(Material.CHERRY_CHEST_BOAT.name());
        assertEquals(20, service.capacitySlots(mockPlayer));
        assertEquals(100, service.count(mockPlayer, Material.WHEAT),
                "Upgrading never moves items - the slot count just grows");
    }

    @Test
    void testCapacityAndConsolidation() {
        holds.giveBoat(uuid, Material.OAK_BOAT); // 3 slots = 192 wheat
        assertEquals(192, service.add(mockPlayer, Material.WHEAT, 500), "3 slots x 64");
        assertEquals(0, service.add(mockPlayer, Material.COAL, 1), "Full is full");
        assertEquals(3, service.slotsUsed(uuid));
        // Consolidation: 40 + 30 wheat = 70 = 2 slots, not two spread stacks
        holds.giveBoat(uuid, Material.OAK_BOAT);
        service.add(mockPlayer, Material.WHEAT, 40);
        service.add(mockPlayer, Material.WHEAT, 30);
        assertEquals(2, service.slotsUsed(uuid));
        assertEquals(70, service.count(mockPlayer, Material.WHEAT));
        // Headroom math: 70 wheat in 2 slots leaves 58 headroom + 1 free slot
        assertEquals(58 + 64, service.capacityFor(uuid, Material.WHEAT));
    }

    @Test
    void testUnstackablesEatSlots() {
        holds.giveBoat(uuid, Material.OAK_BOAT); // 3 slots
        assertEquals(3, service.add(mockPlayer, Material.IRON_SWORD, 5),
                "Unstackables occupy a slot each");
    }

    @Test
    void testContainersAndVesselsRefused() {
        holds.giveBoat(uuid, Material.PALE_OAK_CHEST_BOAT);
        assertEquals(0, service.add(mockPlayer, Material.BUNDLE, 1));
        assertEquals(0, service.add(mockPlayer, Material.SHULKER_BOX, 1));
        assertEquals(0, service.add(mockPlayer, Material.WHITE_SHULKER_BOX, 1));
        assertEquals(0, service.add(mockPlayer, Material.CHEST, 1));
        assertEquals(0, service.add(mockPlayer, Material.OAK_BOAT, 1), "A vessel is not cargo");
        assertEquals(0, service.add(mockPlayer, Material.BAMBOO_RAFT, 1));
        assertTrue(service.refuses(Material.BARREL));
    }

    @Test
    void testRemoveIsExactAndBounded() {
        holds.giveBoat(uuid, Material.OAK_BOAT);
        service.add(mockPlayer, Material.COD, 50);
        assertEquals(30, service.remove(mockPlayer, Material.COD, 30));
        assertEquals(20, service.count(mockPlayer, Material.COD));
        assertEquals(20, service.remove(mockPlayer, Material.COD, 999));
        assertEquals(0, service.count(mockPlayer, Material.COD));
        assertEquals(0, service.remove(mockPlayer, Material.COD, 1));
    }

    @Test
    void testFuelRow() {
        holds.giveBoat(uuid, Material.OAK_BOAT);
        assertEquals(8, service.addFuel(mockPlayer, Material.COAL, 8));
        assertEquals(8, (int) service.fuelContents(uuid).get(Material.COAL));
        // Non-fuel is refused from the fuel row
        assertEquals(0, service.addFuel(mockPlayer, Material.WHEAT, 8));
        // Cargo capacity is untouched by fuel
        assertEquals(3, service.capacitySlots(mockPlayer));
        assertEquals(0, service.slotsUsed(uuid));
        // Fuel moves freely back out
        assertEquals(5, service.removeFuel(uuid, Material.COAL, 5));
        assertEquals(3, (int) service.fuelContents(uuid).get(Material.COAL));
    }

    @Test
    void testCargoFuelMovesToTheFuelRow() {
        // Coal bought at market lands in CARGO; the GUI gesture moves it to
        // the fuel row - fuel is exempt from the one-way rule (Ben's ruling)
        holds.giveBoat(uuid, Material.OAK_BOAT);
        service.add(mockPlayer, Material.COAL, 100);
        assertEquals(2, service.slotsUsed(uuid));
        assertEquals(100, service.moveCargoToFuel(mockPlayer, Material.COAL, 100));
        assertEquals(0, service.slotsUsed(uuid), "Cargo slots freed");
        assertEquals(100, (int) service.fuelContents(uuid).get(Material.COAL));
        // Non-fuel cargo can never take this exit
        service.add(mockPlayer, Material.WHEAT, 10);
        assertEquals(0, service.moveCargoToFuel(mockPlayer, Material.WHEAT, 10));
        // And the move respects the fuel row's capacity
        service.addFuel(mockPlayer, Material.LAVA_BUCKET, 6); // coal(2 slots)+6 lava = 8 > 7... coal is 2 slots, so 5 fit
        service.add(mockPlayer, Material.COAL, 640);
        assertTrue(service.moveCargoToFuel(mockPlayer, Material.COAL, 640) < 640,
                "A full fuel row bounds the move");
    }

    @Test
    void testFuelSlotsAreSeven() {
        // Lava buckets do not stack: seven fill the row, the eighth is refused
        holds.giveBoat(uuid, Material.OAK_BOAT);
        assertEquals(7, service.addFuel(mockPlayer, Material.LAVA_BUCKET, 8));
        assertEquals(0, service.addFuel(mockPlayer, Material.COAL, 1), "Row full");
    }

    @Test
    void testExpanderInstallRules() {
        holds.giveBoat(uuid, Material.CHERRY_CHEST_BOAT);
        assertFalse(service.canInstallExpander(uuid), "Only the Pale Oak Chest Boat takes expanders");
        holds.giveBoat(uuid, Material.PALE_OAK_CHEST_BOAT);
        assertTrue(service.installExpander(uuid));
        assertEquals(1, service.expanderCount(uuid));
        // An installed expander occupies one cargo slot
        assertEquals(1, service.slotsUsed(uuid));
        assertEquals(20, service.slotsFree(uuid));
    }

    /** Total of one material across a stack list. */
    private static int countOf(java.util.List<ItemStack> stacks, Material material) {
        return stacks.stream().filter(s -> s != null && s.getType() == material)
                .mapToInt(ItemStack::getAmount).sum();
    }

    @Test
    void testExpanderSpilloverAndAggregation() {
        holds.giveBoat(uuid, Material.PALE_OAK_CHEST_BOAT);
        service.installExpander(uuid);
        // Main capacity: 20 free slots x 64 = 1280; expander adds 21 x 64 = 1344
        assertEquals(1280 + 1344, service.capacityFor(uuid, Material.WHEAT));
        assertEquals(2624, service.add(mockPlayer, Material.WHEAT, 9999));
        // Aggregated count sees everything; main contents map does not
        assertEquals(2624, service.count(mockPlayer, Material.WHEAT));
        assertEquals(1280, countOf(service.cargo(uuid), Material.WHEAT));
        assertEquals(1344, countOf(service.expanderCargo(uuid, 0), Material.WHEAT));
        assertEquals(2624, countOf(service.tradeCargo(mockPlayer), Material.WHEAT));
        // Removal drains the main hold first, then the expander
        assertEquals(1300, service.remove(mockPlayer, Material.WHEAT, 1300));
        assertEquals(0, countOf(service.cargo(uuid), Material.WHEAT));
        assertEquals(1324, countOf(service.expanderCargo(uuid, 0), Material.WHEAT));
    }

    @Test
    void testExpanderDestroyOnlyWhenEmpty() {
        holds.giveBoat(uuid, Material.PALE_OAK_CHEST_BOAT);
        service.installExpander(uuid);
        service.addToExpander(mockPlayer, 0, Material.COD, 10);
        assertFalse(service.destroyExpander(uuid, 0), "The TNT refuses a loaded expander");
        service.removeFromExpander(uuid, 0, Material.COD, 10);
        assertTrue(service.destroyExpander(uuid, 0));
        assertEquals(0, service.expanderCount(uuid));
    }

    @Test
    void testInertExpandersKeepContents() {
        holds.giveBoat(uuid, Material.PALE_OAK_CHEST_BOAT);
        service.installExpander(uuid);
        service.addToExpander(mockPlayer, 0, Material.DIAMOND, 5);
        // After ending up with a smaller boat the expander rides along inert:
        // not openable, contents intact and still counted aboard
        service.active(uuid).orElseThrow().setMaterial(Material.OAK_BOAT.name());
        assertFalse(service.expandersOpenable(uuid));
        assertEquals(5, countOf(service.expanderCargo(uuid, 0), Material.DIAMOND));
        assertEquals(5, service.count(mockPlayer, Material.DIAMOND));
    }

    @Test
    void testFuelServiceBurnsCheapestFirst() {
        holds.giveBoat(uuid, Material.OAK_BOAT);
        service.addFuel(mockPlayer, Material.COAL, 4); // 4 x 8 = 32 units
        service.addFuel(mockPlayer, Material.OAK_LOG, 10); // 10 x 1 = 10 units
        FuelService fuel = addon.getFuelService();
        assertEquals(42.0, fuel.holdFuel(mockPlayer), 1e-9);
        // Burning 6 units takes the cheap logs first
        assertTrue(fuel.consume(mockPlayer, 6));
        assertEquals(4, (int) service.fuelContents(uuid).getOrDefault(Material.OAK_LOG, 0));
        assertEquals(4, (int) service.fuelContents(uuid).get(Material.COAL));
        // Too little is refused outright, nothing burned
        assertTrue(!fuel.consume(mockPlayer, 999));
        assertEquals(36.0, fuel.holdFuel(mockPlayer), 1e-9);
    }
}
