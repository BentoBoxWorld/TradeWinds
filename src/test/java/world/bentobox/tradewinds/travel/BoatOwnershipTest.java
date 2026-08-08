package world.bentobox.tradewinds.travel;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.UUID;

import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.inventory.ItemStack;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import world.bentobox.tradewinds.CommonTestSetup;
import world.bentobox.tradewinds.Settings;
import world.bentobox.tradewinds.TestHolds;
import world.bentobox.tradewinds.TradeWinds;
import world.bentobox.tradewinds.dataobjects.BoatHold;
import world.bentobox.tradewinds.ocean.OceanConfig;
import world.bentobox.tradewinds.ocean.OceanEngine;
import world.bentobox.tradewinds.ocean.IslandSpec;
import world.bentobox.tradewinds.ocean.SecurityBand;

/**
 * Ownership, abandonment and protection (rules ruled 2026-08-02): an owned
 * boat left in protected island space is untouchable, an unowned one is fair
 * game anywhere, and taking a boat abandons the one you had - with its cargo
 * still aboard.
 *
 * @author tastybento
 */
class BoatOwnershipTest extends CommonTestSetup {

    private static final long SEED = 4242L;

    private TradeWinds addon;
    private BoatListener listener;
    private TestHolds holds;
    private OceanEngine engine;
    private IslandSpec island;

    @Override
    @BeforeEach
    public void setUp() throws Exception {
        super.setUp();
        addon = mock(TradeWinds.class);
        when(addon.getSettings()).thenReturn(new Settings());
        when(addon.getBoatRanks()).thenReturn(new BoatRanks(addon));
        when(addon.getOverWorld()).thenReturn(world);
        when(world.getSeed()).thenReturn(SEED);
        engine = new OceanEngine(new OceanConfig(SEED, 2500, 160, 45, 1.0, 0, 5000, 70));
        when(addon.getOceanEngine(anyLong())).thenReturn(engine);
        holds = TestHolds.install(addon);
        when(addon.getFuelService()).thenReturn(new FuelService(addon));
        when(addon.getHoldService()).thenReturn(new HoldService(addon));
        listener = new BoatListener(addon);
        island = engine.islandInCell(0, 0).orElseThrow();
    }

    private Location atIsland(int offset) {
        Location loc = mock(Location.class);
        when(loc.getWorld()).thenReturn(world);
        when(loc.getBlockX()).thenReturn(island.centerX() + offset);
        when(loc.getBlockZ()).thenReturn(island.centerZ());
        return loc;
    }

    @Test
    void testOwnedBoatInProtectedSpaceIsProtected() {
        BoatHold hold = holds.giveBoat(uuid, Material.OAK_BOAT);
        // Moored at the dock: nobody else may board or break it
        assertTrue(listener.isProtected(hold, atIsland(100)));
        // Out past the protection edge it is on its own
        assertFalse(listener.isProtected(hold, atIsland(900)));
    }

    @Test
    void testUnownedBoatIsNeverProtected() {
        BoatHold hull = holds.unownedBoat(Material.CHERRY_CHEST_BOAT);
        // Even moored in safe island space, an abandoned hull is fair game -
        // this is what makes the OLD BOAT a race to recover
        assertFalse(listener.isProtected(hull, atIsland(100)));
    }

    /** Total of one material in a boat's cargo. */
    private static int cargoCount(BoatHold hold, Material material) {
        return hold.getCargo().stream().filter(s -> s != null && s.getType() == material)
                .mapToInt(ItemStack::getAmount).sum();
    }

    @Test
    void testTakingABoatAbandonsTheOldOneWithItsCargo() {
        BoatHold mine = holds.giveBoat(uuid, Material.OAK_BOAT);
        mine.getCargo().add(new ItemStack(Material.DIAMOND, 12));
        BoatHold prize = holds.unownedBoat(Material.CHERRY_CHEST_BOAT);
        prize.getCargo().add(new ItemStack(Material.EMERALD, 5));

        holds.manager().setActiveBoat(uuid, prize);

        // The prize is mine, cargo and all
        assertEquals(prize.getUniqueId(), holds.manager().activeBoat(uuid).orElseThrow().getUniqueId());
        assertEquals(uuid.toString(), prize.getOwner());
        assertEquals(5, cargoCount(prize, Material.EMERALD));
        // The boat I left keeps MY cargo, and loses its owner: unowned,
        // capturable, and still findable on the chart
        assertTrue(mine.isUnowned(), "The abandoned boat is unowned");
        assertEquals(12, cargoCount(mine, Material.DIAMOND), "Its cargo stays aboard");
        assertEquals(mine.getUniqueId(), holds.manager().oldBoat(uuid).orElseThrow().getUniqueId());
    }

    @Test
    void testReclaimingTheOldBoatClearsTheMarker() {
        BoatHold first = holds.giveBoat(uuid, Material.OAK_BOAT);
        BoatHold second = holds.unownedBoat(Material.SPRUCE_BOAT);
        holds.manager().setActiveBoat(uuid, second);
        assertTrue(holds.manager().oldBoat(uuid).isPresent());
        // Row back and board the old one again
        holds.manager().setActiveBoat(uuid, first);
        assertEquals(first.getUniqueId(), holds.manager().activeBoat(uuid).orElseThrow().getUniqueId());
        assertEquals(uuid.toString(), first.getOwner());
    }

    @Test
    void testOnlyOneOldBoatIsRemembered() {
        BoatHold a = holds.giveBoat(uuid, Material.OAK_BOAT);
        BoatHold b = holds.unownedBoat(Material.SPRUCE_BOAT);
        BoatHold c = holds.unownedBoat(Material.BIRCH_BOAT);
        holds.manager().setActiveBoat(uuid, b);
        holds.manager().setActiveBoat(uuid, c);
        // Only the most recent abandonment is charted; the first is forgotten
        // flotsam (still floating, just nobody's marker)
        assertEquals(b.getUniqueId(), holds.manager().oldBoat(uuid).orElseThrow().getUniqueId());
        assertTrue(a.isUnowned());
    }

    @Test
    void testTakingABoatStripsItsFormerOwner() {
        // Playtest 2026-08-02: player 1 took player 2's boat and player 2 was
        // left still pointing at it - so he had phantom cargo slots, fuel
        // warnings, and on login the "stolen" path handed the boat BACK by
        // unowning it. Losing a boat is not abandoning one.
        UUID victim = UUID.randomUUID();
        BoatHold prize = holds.giveBoat(victim, Material.OAK_BOAT);
        BoatHold mine = holds.giveBoat(uuid, Material.BAMBOO_RAFT);

        holds.manager().setActiveBoat(uuid, prize);

        assertEquals(uuid.toString(), prize.getOwner(), "The taker owns it");
        assertTrue(holds.manager().activeBoat(victim).isEmpty(), "The victim has no boat");
        assertTrue(holds.manager().oldBoat(victim).isEmpty(),
                "A boat someone TOOK is not an abandoned OLD BOAT");
        // The taker's own raft is the one abandoned
        assertEquals(mine.getUniqueId(), holds.manager().oldBoat(uuid).orElseThrow().getUniqueId());
    }

    @Test
    void testOldBoatMarkerDropsOnceSomeoneClaimsIt() {
        BoatHold mine = holds.giveBoat(uuid, Material.OAK_BOAT);
        BoatHold other = holds.unownedBoat(Material.SPRUCE_BOAT);
        holds.manager().setActiveBoat(uuid, other);
        assertTrue(holds.manager().oldBoat(uuid).isPresent(), "Abandoned, so charted");
        // Another sailor takes the hull I abandoned: my marker is a lie now
        holds.manager().setActiveBoat(UUID.randomUUID(), mine);
        assertTrue(holds.manager().oldBoat(uuid).isEmpty(), "Someone owns it - no OLD BOAT marker");
    }

    @Test
    void testTakingABoatWhileCarryingOneMergesRatherThanDuplicating() {
        // Playtest 2026-08-02: recovering an old boat while carrying your
        // current one left TWO boat items in the pack, both opening the same
        // hold, and the spare could be dropped and stripped for free fuel.
        when(world.getName()).thenReturn("tradewinds_world");
        world.bentobox.tradewinds.economy.MarketService marketService = mock(world.bentobox.tradewinds.economy.MarketService.class);
        when(addon.getMarketService())
                .thenReturn(marketService);
        when(addon.getMarketService().basePrice(any(ItemStack.class)))
                .thenReturn(java.util.Optional.of(1.0));
        BoatHold carried = holds.giveBoat(uuid, Material.OAK_BOAT);
        carried.getCargo().add(new ItemStack(Material.COD, 20));
        carried.setWorld("tradewinds_world");
        BoatHold found = holds.unownedBoat(Material.CHERRY_CHEST_BOAT);

        // The carried hull is in the pack, stamped with its id. (ItemStack
        // meta does not work under MockBukkit - the item factory is a bare
        // mock - so the PDC is stubbed directly.)
        ItemStack spare = mock(ItemStack.class);
        when(spare.getType()).thenReturn(Material.OAK_BOAT);
        when(spare.getAmount()).thenReturn(1);
        when(spare.hasItemMeta()).thenReturn(true);
        org.bukkit.inventory.meta.ItemMeta meta = mock(org.bukkit.inventory.meta.ItemMeta.class);
        org.bukkit.persistence.PersistentDataContainer pdc =
                mock(org.bukkit.persistence.PersistentDataContainer.class);
        when(pdc.get(BoatService.BOAT_ID_KEY, org.bukkit.persistence.PersistentDataType.STRING))
                .thenReturn(carried.getUniqueId());
        when(meta.getPersistentDataContainer()).thenReturn(pdc);
        when(spare.getItemMeta()).thenReturn(meta);
        org.bukkit.inventory.PlayerInventory pack = mock(org.bukkit.inventory.PlayerInventory.class);
        when(pack.getContents()).thenReturn(new ItemStack[] { spare });
        when(mockPlayer.getInventory()).thenReturn(pack);
        when(addon.getBoatService()).thenReturn(new BoatService(addon));
        Location where = mock(Location.class);
        when(where.getWorld()).thenReturn(world);
        when(where.getX()).thenReturn(0.0);
        when(where.getZ()).thenReturn(0.0);
        when(where.getBlockX()).thenReturn(0);
        when(where.getBlockZ()).thenReturn(0);
        when(mockPlayer.getLocation()).thenReturn(where);
        when(mockPlayer.getWorld()).thenReturn(world);

        listener.takeBoat(mockPlayer, found);

        // The prize is theirs, the cargo came across, and the spare hull is
        // no longer in the pack
        assertEquals(found.getUniqueId(), holds.manager().activeBoat(uuid).orElseThrow().getUniqueId());
        assertEquals(20, cargoCount(found, Material.COD), "Cargo moved into the new hull");
        assertTrue(carried.getCargo().isEmpty(), "The old hull was emptied");
        verify(spare).setAmount(0);
    }

    @Test
    void testCargoNeverCrossesTheOcean() {
        // Playtest 2026-08-02: a hull thrown to a player on ANOTHER island
        // moved its cargo into their boat thousands of blocks away. The
        // transfer option is gated on their own boat being alongside.
        when(world.getName()).thenReturn("tradewinds_world");
        BoatHold mine = holds.giveBoat(uuid, Material.OAK_BOAT);
        mine.setWorld(world.getName());
        mine.setX(0);
        mine.setZ(0);
        Location here = mock(Location.class);
        when(here.getWorld()).thenReturn(world);
        // Standing next to my own boat: loading is fine
        when(here.getX()).thenReturn(50.0);
        when(here.getZ()).thenReturn(0.0);
        when(mockPlayer.getLocation()).thenReturn(here);
        when(mockPlayer.getWorld()).thenReturn(world);
        assertTrue(listener.ownBoatWithinReach(mockPlayer));
        // An island away: not offered at all
        when(here.getX()).thenReturn(4000.0);
        assertFalse(listener.ownBoatWithinReach(mockPlayer));
        // Another world entirely: likewise
        when(here.getX()).thenReturn(50.0);
        mine.setWorld("somewhere_else");
        assertFalse(listener.ownBoatWithinReach(mockPlayer));
    }

    @Test
    void testAnarchicIslandsProtectNothing() {
        BoatHold hold = holds.giveBoat(UUID.randomUUID(), Material.OAK_BOAT);
        IslandSpec anarchic = new IslandSpec(9, 9, 60_000, 60_000, island.type(),
                SecurityBand.ANARCHIC, island.biomeKey(), "Lawless", 3);
        Location loc = mock(Location.class);
        when(loc.getWorld()).thenReturn(world);
        when(loc.getBlockX()).thenReturn(anarchic.centerX());
        when(loc.getBlockZ()).thenReturn(anarchic.centerZ());
        // No island of ours is at that spot in this ocean, so nothing shields
        // it - the same answer an anarchic port gives
        assertFalse(listener.isProtected(hold, loc));
    }
}
