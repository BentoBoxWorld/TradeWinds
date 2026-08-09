package world.bentobox.tradewinds.travel;

import static org.junit.jupiter.api.Assertions.assertNull;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;


import org.bukkit.Material;
import org.bukkit.entity.Item;
import org.bukkit.event.entity.EntityPickupItemEvent;
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
 * The 2026-08-08 playtest, as regression tests: a sailor died, was lent a
 * bamboo raft, picked an oak hull off the quay in total silence, and then
 * watched the oak boat vanish from the pack. Two of the three findings were
 * the same old sin in new places - <b>matching a boat by MATERIAL instead of
 * identity</b> - and the third was a hold changing hands with no word said.
 *
 * @author tastybento
 */
class BoatIdentityTest extends CommonTestSetup {

    private static final String WORLD_NAME = "tradewinds_world";

    private TradeWinds addon;
    private TestHolds holds;
    private BoatService service;
    private BoatListener listener;
    private BoatCraftListener crafting;
    private StarterKit kit;
    private BoatHold mine;

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
        when(inv.addItem(any())).thenReturn(new java.util.HashMap<>());
        listener = new BoatListener(addon);
        crafting = new BoatCraftListener(addon);
        kit = new StarterKit(addon);
        mine = holds.giveBoat(uuid, Material.OAK_BOAT);
    }

    /** A hull item carrying a given boat id (null for a plain vanilla hull). */
    private ItemStack hull(Material material, String boatId) {
        ItemStack stack = mock(ItemStack.class);
        ItemMeta meta = mock(ItemMeta.class);
        PersistentDataContainer pdc = mock(PersistentDataContainer.class);
        when(pdc.get(BoatService.BOAT_ID_KEY, PersistentDataType.STRING)).thenReturn(boatId);
        when(meta.getPersistentDataContainer()).thenReturn(pdc);
        when(stack.hasItemMeta()).thenReturn(true);
        when(stack.getItemMeta()).thenReturn(meta);
        when(stack.getType()).thenReturn(material);
        when(stack.getAmount()).thenReturn(1);
        return stack;
    }

    // ------------------------------------------------------- crafting refit

    @Test
    void testCraftRefitBreaksUpTheHullThatIsTheRecord() {
        ItemStack ours = hull(Material.OAK_BOAT, mine.getUniqueId());
        when(inv.getContents()).thenReturn(new ItemStack[] { ours });

        crafting.removeOldBoat(mockPlayer, Material.OAK_BOAT, mine);

        verify(ours).setAmount(0);
    }

    @Test
    void testCraftRefitSparesSomeoneElsesHullOfTheSameWood() {
        // The playtest: an oak hull fished off the quay, sitting in the pack
        // beside our own oak boat. Matching by material ate whichever came
        // first - and the sailor's report was "the oak boat disappeared".
        BoatHold theirs = holds.manager().create(Material.OAK_BOAT, null);
        ItemStack stranger = hull(Material.OAK_BOAT, theirs.getUniqueId());
        ItemStack ours = hull(Material.OAK_BOAT, mine.getUniqueId());
        when(inv.getContents()).thenReturn(new ItemStack[] { stranger, ours });

        crafting.removeOldBoat(mockPlayer, Material.OAK_BOAT, mine);

        verify(stranger, never()).setAmount(0);
        verify(ours).setAmount(0);
    }

    @Test
    void testCraftRefitFallsBackToAnUnstampedHull() {
        // A legacy or hand-given hull has no stamp; it is still the only
        // candidate, so it may be broken up when nothing carries the id
        ItemStack legacy = hull(Material.OAK_BOAT, null);
        when(inv.getContents()).thenReturn(new ItemStack[] { legacy });

        crafting.removeOldBoat(mockPlayer, Material.OAK_BOAT, mine);

        verify(legacy).setAmount(0);
    }

    // ------------------------------------------------------ spawn auto-launch

    @Test
    void testSpawnLaunchConsumesOurOwnHull() {
        BoatHold theirs = holds.manager().create(Material.OAK_BOAT, null);
        ItemStack stranger = hull(Material.OAK_BOAT, theirs.getUniqueId());
        ItemStack ours = hull(Material.OAK_BOAT, mine.getUniqueId());
        when(inv.getContents()).thenReturn(new ItemStack[] { stranger, ours });

        kit.consumeBoatItem(mockPlayer);

        verify(stranger, never()).setAmount(0);
        verify(ours).setAmount(0);
    }

    @Test
    void testSpawnLaunchTakesNothingWhenOnlyAStrangersHullIsCarried() {
        BoatHold theirs = holds.manager().create(Material.OAK_BOAT, null);
        ItemStack stranger = hull(Material.OAK_BOAT, theirs.getUniqueId());
        when(inv.getContents()).thenReturn(new ItemStack[] { stranger });

        assertNull(kit.consumeBoatItem(mockPlayer), "A hull that is not ours is not ours to launch");
        verify(stranger, never()).setAmount(0);
    }

    // ------------------------------------------------------------- pickups

    /** The pickup event for a hull lying on the ground. */
    private EntityPickupItemEvent pickup(ItemStack stack) {
        Item ground = mock(Item.class);
        when(ground.getItemStack()).thenReturn(stack);
        when(ground.getLocation()).thenReturn(location);
        EntityPickupItemEvent event = mock(EntityPickupItemEvent.class);
        when(event.getEntity()).thenReturn(mockPlayer);
        when(event.getItem()).thenReturn(ground);
        return event;
    }

    @Test
    void testPickingUpYourOwnHullSaysWhatItDidToTheBoatYouWereSailing() {
        // Died, was lent a raft, then walked over the old hull: the oak boat
        // becomes the hold again and the loaner is flotsam. In silence, the
        // sailor had no way to know either had happened.
        BoatHold raft = holds.giveBoat(uuid, Material.BAMBOO_RAFT);
        mine.setOwner(uuid.toString()); // still theirs, just not the active one
        holds.manager().save(mine);
        when(inv.getContents()).thenReturn(new ItemStack[0]);

        listener.onPickup(pickup(hull(Material.OAK_BOAT, mine.getUniqueId())));

        org.mockito.ArgumentCaptor<net.kyori.adventure.text.Component> said =
                org.mockito.ArgumentCaptor.forClass(net.kyori.adventure.text.Component.class);
        verify(mockPlayer, org.mockito.Mockito.atLeastOnce()).sendMessage(said.capture());
        org.junit.jupiter.api.Assertions.assertTrue(
                said.getAllValues().stream().map(Object::toString).anyMatch(s -> s.contains("own-resumed")),
                "The sailor must be told their hold has moved to the hull they picked up");
        org.junit.jupiter.api.Assertions.assertEquals(mine.getUniqueId(),
                holds.manager().activeBoat(uuid).map(BoatHold::getUniqueId).orElse(null));
        org.junit.jupiter.api.Assertions.assertTrue(raft.isUnowned(), "The loaner is an OLD BOAT now");
    }

    @Test
    void testClaimingAnUnownedHullWithNoBoatCancelsTheVanillaPickup() {
        // Vanilla adds the stack it captured whatever happens to the entity,
        // so an uncancelled claim landed the ground hull AND the stamped copy
        // giveBoatItem hands over: two items, one record.
        holds.manager().setActiveBoat(uuid, null);
        BoatHold flotsam = holds.manager().create(Material.SPRUCE_BOAT, null);
        when(inv.getContents()).thenReturn(new ItemStack[0]);
        EntityPickupItemEvent event = pickup(hull(Material.SPRUCE_BOAT, flotsam.getUniqueId()));

        listener.onPickup(event);

        verify(event).setCancelled(true);
    }
}
