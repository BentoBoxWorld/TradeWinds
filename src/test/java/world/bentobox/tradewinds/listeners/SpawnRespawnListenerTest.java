package world.bentobox.tradewinds.listeners;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.event.player.PlayerRespawnEvent;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import world.bentobox.tradewinds.CommonTestSetup;
import world.bentobox.tradewinds.Settings;
import world.bentobox.tradewinds.TestHolds;
import world.bentobox.tradewinds.TradeWinds;
import world.bentobox.tradewinds.travel.BoatRanks;
import world.bentobox.tradewinds.travel.BoatService;
import world.bentobox.tradewinds.travel.HoldService;

/**
 * Tests bed-less respawns land on the spawn islet, never in the seabed.
 *
 * @author tastybento
 */
class SpawnRespawnListenerTest extends CommonTestSetup {

    private TradeWinds addon;
    private SpawnRespawnListener listener;
    private Location deathBed;

    @Override
    @BeforeEach
    public void setUp() throws Exception {
        super.setUp();
        addon = mock(TradeWinds.class);
        when(addon.getSettings()).thenReturn(new Settings());
        when(addon.getOverWorld()).thenReturn(world);
        when(addon.getIslands()).thenReturn(im);
        // The loaner boat path
        when(addon.getBoatRanks()).thenReturn(new BoatRanks(addon));
        holds = TestHolds.install(addon);
        hold = new HoldService(addon);
        when(addon.getHoldService()).thenReturn(hold);
        boats = mock(BoatService.class);
        when(addon.getBoatService()).thenReturn(boats);
        // The spawn island's spawn point: the market plaza
        when(im.getSpawnPoint(world)).thenReturn(new Location(world, 72.5, 73, 72.5));
        when(mockPlayer.getWorld()).thenReturn(world);
        when(mockPlayer.isOnline()).thenReturn(true);
        // A REAL post-respawn location on the plaza: the shared mock Location
        // answers 0 to every distanceSquared, so "my boat is an ocean away"
        // and "my boat is at my feet" were the same test
        when(world.getName()).thenReturn("tradewinds_world");
        when(mockPlayer.getLocation()).thenReturn(new Location(world, 72, 73, 72));
        // The loaner is granted a tick after the respawn: run it inline
        when(sch.runTask(org.mockito.ArgumentMatchers.any(),
                org.mockito.ArgumentMatchers.any(Runnable.class))).thenAnswer(inv -> {
                    inv.getArgument(1, Runnable.class).run();
                    return null;
                });
        listener = new SpawnRespawnListener(addon);
        deathBed = new Location(world, 500, 42, 500);
    }

    private HoldService hold;
    private BoatService boats;
    private TestHolds holds;

    @Test
    void testUnsafeSpawnPointIsSidestepped() {
        // One seed put the plaza campfire exactly under the spawn point: a
        // respawn death-loop in open flame (playtest 2026-08-07). The
        // listener must spiral to the nearest safe column.
        Location point = new Location(world, 72.5, 73, 72.5);
        Location safe = point.clone().add(-1, 0, 0);
        when(im.isSafeLocation(org.mockito.ArgumentMatchers.any(Location.class)))
                .thenAnswer(inv -> {
                    Location l = inv.getArgument(0);
                    return l.getX() == safe.getX() && l.getY() == safe.getY() && l.getZ() == safe.getZ();
                });
        PlayerRespawnEvent event = new PlayerRespawnEvent(mockPlayer, deathBed, false, false);
        listener.onRespawn(event);
        assertEquals(safe.getX(), event.getRespawnLocation().getX());
        assertEquals(safe.getZ(), event.getRespawnLocation().getZ());
    }

    @Test
    void testSafeSpawnPointIsUsedAsIs() {
        when(im.isSafeLocation(org.mockito.ArgumentMatchers.any(Location.class))).thenReturn(true);
        PlayerRespawnEvent event = new PlayerRespawnEvent(mockPlayer, deathBed, false, false);
        listener.onRespawn(event);
        assertEquals(72.5, event.getRespawnLocation().getX());
        assertEquals(72.5, event.getRespawnLocation().getZ());
    }

    @Test
    void testBoatlessRespawnerGetsTheLoaner() {
        PlayerRespawnEvent event = new PlayerRespawnEvent(mockPlayer, deathBed, false, false);
        listener.onRespawn(event);
        verify(boats).createFor(mockPlayer, org.bukkit.Material.BAMBOO_RAFT);
    }

    @Test
    void testIslandMembersKeepTheirIslandRespawn() {
        // BentoBox's ISLAND_RESPAWN listener (NORMAL) already set the island
        // home; this listener (HIGH) used to stomp it with the spawn plaza
        // (playtest 2026-08-05). Island members must pass through untouched.
        when(im.getIsland(world, uuid)).thenReturn(island);
        Location islandHome = new Location(world, 45_000.5, 75, -8_000.5);
        PlayerRespawnEvent event = new PlayerRespawnEvent(mockPlayer, islandHome, false, false);
        listener.onRespawn(event);
        assertEquals(islandHome, event.getRespawnLocation(),
                "An island member's respawn must not be redirected to spawn");
        // The loaner still applies - their real boat is where they died
        verify(boats).createFor(mockPlayer, org.bukkit.Material.BAMBOO_RAFT);
    }

    @Test
    void testBoatWithinReachMeansNoLoaner() {
        // Their own boat is moored at the respawn point: no charity needed
        var boat = holds.giveBoat(uuid, org.bukkit.Material.OAK_BOAT);
        boat.setWorld(world.getName());
        boat.setX(72);
        boat.setY(73);
        boat.setZ(72);
        listener.onRespawn(new PlayerRespawnEvent(mockPlayer, deathBed, false, false));
        verify(boats, never()).createFor(
                org.mockito.ArgumentMatchers.any(), org.mockito.ArgumentMatchers.any());
    }

    @Test
    void testDistantBoatStillGetsALoaner() {
        // Ship an ocean away: lend a raft rather than strand them ashore
        var boat = holds.giveBoat(uuid, org.bukkit.Material.OAK_BOAT);
        boat.setWorld(world.getName());
        boat.setX(9000);
        boat.setZ(9000);
        listener.onRespawn(new PlayerRespawnEvent(mockPlayer, deathBed, false, false));
        // The loaner is their ACTIVE boat at once - an unowned hull is not a
        // hold, and its carrier is turned away at every quay (playtest
        // 2026-08-08). The distant hull becomes their OLD BOAT.
        verify(boats).createFor(mockPlayer, org.bukkit.Material.BAMBOO_RAFT);
        verify(boats).giveBoatItem(org.mockito.ArgumentMatchers.eq(mockPlayer),
                org.mockito.ArgumentMatchers.any());
    }

    @Test
    void testCarriedBoatMeansNoLoaner() {
        // keepInventory: the hull never left their pack, so lending would
        // demote a boat they are holding - cargo and all
        var boat = holds.giveBoat(uuid, org.bukkit.Material.OAK_BOAT);
        boat.setWorld(world.getName());
        boat.setX(9000);
        boat.setZ(9000);
        when(boats.isCarrying(mockPlayer, boat)).thenReturn(true);
        listener.onRespawn(new PlayerRespawnEvent(mockPlayer, deathBed, false, false));
        verify(boats, never()).createFor(
                org.mockito.ArgumentMatchers.any(), org.mockito.ArgumentMatchers.any());
        verify(boats, never()).giveBoatItem(org.mockito.ArgumentMatchers.any(),
                org.mockito.ArgumentMatchers.any());
    }

    @Test
    void testLoanerDisabledByConfig() {
        Settings settings = new Settings();
        settings.setRespawnBoat("NONE");
        when(addon.getSettings()).thenReturn(settings);
        listener.onRespawn(new PlayerRespawnEvent(mockPlayer, deathBed, false, false));
        verify(boats, never()).createFor(
                org.mockito.ArgumentMatchers.any(), org.mockito.ArgumentMatchers.any());
        // An admin typo (off-ladder name) disables rather than granting junk
        settings.setRespawnBoat("DIRT");
        listener.onRespawn(new PlayerRespawnEvent(mockPlayer, deathBed, false, false));
        verify(boats, never()).createFor(
                org.mockito.ArgumentMatchers.any(), org.mockito.ArgumentMatchers.any());
    }

    @Test
    void testBedlessRespawnGoesToThePlaza() {
        // NOT the island centre (wooded - players were respawning in treetops)
        PlayerRespawnEvent event = new PlayerRespawnEvent(mockPlayer, deathBed, false, false);
        listener.onRespawn(event);
        assertEquals(73, event.getRespawnLocation().getBlockY());
        assertEquals(72, event.getRespawnLocation().getBlockX());
        assertEquals(72, event.getRespawnLocation().getBlockZ());
    }

    @Test
    void testFallsBackToWorldSpawnWithoutASpawnIsland() {
        when(im.getSpawnPoint(world)).thenReturn(null);
        Location worldSpawn = new Location(world, 10, 72, 10);
        when(world.getSpawnLocation()).thenReturn(worldSpawn);
        PlayerRespawnEvent event = new PlayerRespawnEvent(mockPlayer, deathBed, false, false);
        listener.onRespawn(event);
        assertEquals(worldSpawn, event.getRespawnLocation());
    }

    @Test
    void testBedRespawnIsHonored() {
        PlayerRespawnEvent event = new PlayerRespawnEvent(mockPlayer, deathBed, true, false);
        listener.onRespawn(event);
        assertEquals(deathBed, event.getRespawnLocation());
    }

    @Test
    void testOtherWorldsUntouched() {
        World otherWorld = mock(World.class);
        when(mockPlayer.getWorld()).thenReturn(otherWorld);
        PlayerRespawnEvent event = new PlayerRespawnEvent(mockPlayer, deathBed, false, false);
        listener.onRespawn(event);
        assertEquals(deathBed, event.getRespawnLocation());
    }
}
