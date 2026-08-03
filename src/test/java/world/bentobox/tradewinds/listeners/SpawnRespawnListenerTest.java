package world.bentobox.tradewinds.listeners;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.mock;
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
        listener = new SpawnRespawnListener(addon);
        deathBed = new Location(world, 500, 42, 500);
    }

    private HoldService hold;
    private BoatService boats;
    private TestHolds holds;

    @Test
    void testBoatlessRespawnerGetsTheLoaner() {
        PlayerRespawnEvent event = new PlayerRespawnEvent(mockPlayer, deathBed, false, false);
        listener.onRespawn(event);
        org.mockito.Mockito.verify(boats).createFor(mockPlayer, org.bukkit.Material.BAMBOO_RAFT);
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
        org.mockito.Mockito.verify(boats, org.mockito.Mockito.never()).createFor(
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
        // A loaner hull exists for them, and it did NOT displace the boat
        // they still own an ocean away
        org.mockito.Mockito.verify(boats).giveBoatItem(org.mockito.ArgumentMatchers.eq(mockPlayer),
                org.mockito.ArgumentMatchers.any());
        org.mockito.Mockito.verify(boats, org.mockito.Mockito.never()).createFor(
                org.mockito.ArgumentMatchers.any(), org.mockito.ArgumentMatchers.any());
    }

    @Test
    void testLoanerDisabledByConfig() {
        Settings settings = new Settings();
        settings.setRespawnBoat("NONE");
        when(addon.getSettings()).thenReturn(settings);
        listener.onRespawn(new PlayerRespawnEvent(mockPlayer, deathBed, false, false));
        org.mockito.Mockito.verify(boats, org.mockito.Mockito.never()).createFor(
                org.mockito.ArgumentMatchers.any(), org.mockito.ArgumentMatchers.any());
        // An admin typo (off-ladder name) disables rather than granting junk
        settings.setRespawnBoat("DIRT");
        listener.onRespawn(new PlayerRespawnEvent(mockPlayer, deathBed, false, false));
        org.mockito.Mockito.verify(boats, org.mockito.Mockito.never()).createFor(
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
        when(mockPlayer.getWorld()).thenReturn(mock(World.class));
        PlayerRespawnEvent event = new PlayerRespawnEvent(mockPlayer, deathBed, false, false);
        listener.onRespawn(event);
        assertEquals(deathBed, event.getRespawnLocation());
    }
}
