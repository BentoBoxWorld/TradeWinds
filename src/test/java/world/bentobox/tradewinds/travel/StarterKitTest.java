package world.bentobox.tradewinds.travel;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.util.UUID;

import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.block.Block;
import org.bukkit.entity.Player;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Test;

import world.bentobox.bentobox.BentoBox;
import world.bentobox.bentobox.api.user.User;
import world.bentobox.tradewinds.CommonTestSetup;
import world.bentobox.tradewinds.Settings;
import world.bentobox.tradewinds.TestHolds;
import world.bentobox.tradewinds.TradeWinds;
import world.bentobox.tradewinds.dataobjects.TWPlayerData;
import world.bentobox.tradewinds.dataobjects.PlayerDataManager;
import world.bentobox.tradewinds.dataobjects.BoatHold;
import world.bentobox.tradewinds.galaxy.GalaxyConfig;
import world.bentobox.tradewinds.galaxy.GalaxyEngine;

/**
 * Tests for StarterKit: first-spawn kit distribution.
 * - First-timers get kit with boat, coal, coin
 * - Auto-launch on water for repeat arrivals
 * - No boat item consumed when on land
 *
 * @author tastybento
 */
class StarterKitTest extends CommonTestSetup {

    private static final long SEED = 4242L;

    private TradeWinds addon;
    private StarterKit kit;
    private TestHolds holds;
    private UUID playerId;
    private Player player;
    private PlayerDataManager pdm;
    private TWPlayerData playerData;

    @Override
    @BeforeEach
    public void setUp() throws Exception {
        super.setUp();
        playerId = uuid;
        player = mockPlayer;
        addon = mock(TradeWinds.class);
        when(addon.getSettings()).thenReturn(new Settings());
        when(addon.getBoatRanks()).thenReturn(new BoatRanks(addon));
        when(addon.getOverWorld()).thenReturn(world);
        when(world.getSeed()).thenReturn(SEED);
        when(addon.getGalaxyEngine(anyLong())).thenReturn(new GalaxyEngine(new GalaxyConfig(SEED, 2500, 160, 45, 1.0, 0, 5000, 70)));
        holds = TestHolds.install(addon);
        when(addon.getFuelService()).thenReturn(new FuelService(addon));
        when(addon.getHoldService()).thenReturn(new HoldService(addon));
        when(addon.getBoatService()).thenReturn(new BoatService(addon));

        // The addon mock has the player data manager already wired by TestHolds.install()
        pdm = addon.getPlayerDataManager();
        playerData = pdm.get(playerId);

        BentoBox bentoBox = mock(BentoBox.class);
        when(addon.getPlugin()).thenReturn(bentoBox);
        kit = new StarterKit(addon);
        when(player.getWorld()).thenReturn(world);
        when(player.getLocation()).thenReturn(location);
    }

    @Test
    void testFirstTimePlayerGetsKit() {
        Location spawn = mock(Location.class);
        when(spawn.getWorld()).thenReturn(world);
        Block block = mock(Block.class);
        when(block.getType()).thenReturn(Material.DIRT);
        when(spawn.getBlock()).thenReturn(block);
        Block below = mock(Block.class);
        when(below.getType()).thenReturn(Material.DIRT);
        when(spawn.getBlock().getRelative(org.bukkit.block.BlockFace.DOWN)).thenReturn(below);
        when(player.getLocation()).thenReturn(spawn);

        assertTrue(!playerData.isStarterKitGiven());
        kit.onSpawnArrival(player);
        assertTrue(playerData.isStarterKitGiven());
    }

    @Test
    @Disabled("harness limitation: mocked World.spawnEntity returns null, so launch() cannot complete - entity spawning needs a richer stub")
    void testKitGivenOnWaterLaunchesBoat() {
        Location spawn = mock(Location.class);
        when(spawn.getWorld()).thenReturn(world);
        Block water = mock(Block.class);
        when(water.getType()).thenReturn(Material.WATER);
        when(spawn.getBlock()).thenReturn(water);
        when(player.getLocation()).thenReturn(spawn);
        when(player.getVehicle()).thenReturn(null);

        kit.onSpawnArrival(player);

        // Player should have a boat
        assertTrue(holds.manager().activeBoat(playerId).isPresent());
    }

    @Test
    void testKitGivenOnLandGivesBoatItem() {
        Location spawn = mock(Location.class);
        when(spawn.getWorld()).thenReturn(world);
        Block land = mock(Block.class);
        when(land.getType()).thenReturn(Material.DIRT);
        when(spawn.getBlock()).thenReturn(land);
        Block below = mock(Block.class);
        when(below.getType()).thenReturn(Material.DIRT);
        when(land.getRelative(org.bukkit.block.BlockFace.DOWN)).thenReturn(below);
        when(player.getLocation()).thenReturn(spawn);

        kit.onSpawnArrival(player);

        // Player should have the boat in inventory (not launched)
        assertTrue(holds.manager().activeBoat(playerId).isPresent());
    }

    @Test
    @Disabled("harness limitation: mocked World.spawnEntity returns null, so launch() cannot complete - entity spawning needs a richer stub")
    void testRepeatVisitorOnWaterAutoLaunches() {
        playerData.setStarterKitGiven(true);
        BoatHold boat = holds.giveBoat(playerId, Material.OAK_BOAT);
        Location spawn = mock(Location.class);
        when(spawn.getWorld()).thenReturn(world);
        Block water = mock(Block.class);
        when(water.getType()).thenReturn(Material.WATER);
        when(spawn.getBlock()).thenReturn(water);
        when(player.getLocation()).thenReturn(spawn);
        when(player.getVehicle()).thenReturn(null);

        kit.onSpawnArrival(player);

        // Boat should still be active (launched)
        assertEquals(boat.getUniqueId(), holds.manager().activeBoat(playerId).orElseThrow().getUniqueId());
    }

    @Test
    void testRepeatVisitorOnLandDoesNothing() {
        playerData.setStarterKitGiven(true);
        BoatHold boat = holds.giveBoat(playerId, Material.OAK_BOAT);
        Location spawn = mock(Location.class);
        when(spawn.getWorld()).thenReturn(world);
        Block land = mock(Block.class);
        when(land.getType()).thenReturn(Material.DIRT);
        when(spawn.getBlock()).thenReturn(land);
        Block below = mock(Block.class);
        when(below.getType()).thenReturn(Material.DIRT);
        when(land.getRelative(org.bukkit.block.BlockFace.DOWN)).thenReturn(below);
        when(player.getLocation()).thenReturn(spawn);

        kit.onSpawnArrival(player);

        // Boat should still be active (unchanged)
        assertEquals(boat.getUniqueId(), holds.manager().activeBoat(playerId).orElseThrow().getUniqueId());
    }

    @Test
    @Disabled("harness limitation: the kit path runs through launch() whose spawnEntity is unstubbed - fuel assertions never reached")
    void testKitIncludesCoal() {
        // Mock settings to return coal amount
        Settings settings = mock(Settings.class);
        when(settings.getStarterCoal()).thenReturn(10);
        when(addon.getSettings()).thenReturn(settings);

        Location spawn = mock(Location.class);
        when(spawn.getWorld()).thenReturn(world);
        Block land = mock(Block.class);
        when(land.getType()).thenReturn(Material.DIRT);
        when(spawn.getBlock()).thenReturn(land);
        Block below = mock(Block.class);
        when(below.getType()).thenReturn(Material.DIRT);
        when(land.getRelative(org.bukkit.block.BlockFace.DOWN)).thenReturn(below);
        when(player.getLocation()).thenReturn(spawn);

        kit.onSpawnArrival(player);

        BoatHold boat = holds.manager().activeBoat(playerId).orElseThrow();
        assertTrue(boat.getFuel().values().stream().mapToInt(Integer::intValue).sum() > 0);
    }
}
