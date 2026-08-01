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
import world.bentobox.tradewinds.TradeWinds;

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
        // The spawn island's spawn point: the market plaza
        when(im.getSpawnPoint(world)).thenReturn(new Location(world, 72.5, 73, 72.5));
        when(mockPlayer.getWorld()).thenReturn(world);
        listener = new SpawnRespawnListener(addon);
        deathBed = new Location(world, 500, 42, 500);
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
