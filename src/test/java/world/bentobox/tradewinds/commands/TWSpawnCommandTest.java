package world.bentobox.tradewinds.commands;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.ArrayList;
import java.util.Optional;

import org.bukkit.World;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import com.google.common.collect.ImmutableSet;

import world.bentobox.bentobox.api.commands.CompositeCommand;
import world.bentobox.bentobox.api.user.User;
import world.bentobox.bentobox.database.objects.Island;
import world.bentobox.bentobox.managers.IslandsManager;
import world.bentobox.tradewinds.CommonTestSetup;
import world.bentobox.tradewinds.TradeWinds;
import world.bentobox.tradewinds.travel.IntersticeService;
import world.bentobox.tradewinds.travel.SeaPositionTracker;
import world.bentobox.tradewinds.travel.StarterKit;

/**
 * Tests for TWSpawnCommand: the four-way branch for spawn/go/sail command.
 * - In interstice: opens re-engage dialog (free cost)
 * - On own island: steps home (via home teleport)
 * - Islandless at sea: refuses with "already-at-sea"
 * - Outside ocean: returns to last known position, else spawn
 *
 * @author tastybento
 */
class TWSpawnCommandTest extends CommonTestSetup {

    private TradeWinds addon;
    private TWSpawnCommand command;
    private User user;
    private World netherWorld;

    @Override
    @BeforeEach
    public void setUp() throws Exception {
        super.setUp();
        addon = mock(TradeWinds.class);
        CompositeCommand parent = mock(CompositeCommand.class);
        when(parent.getAddon()).thenReturn(addon);
        when(parent.getWorld()).thenReturn(world);

        command = new TWSpawnCommand(parent);
        user = User.getInstance(mockPlayer);

        netherWorld = mock(World.class);
        when(addon.getOverWorld()).thenReturn(world);
        when(addon.getNetherWorld()).thenReturn(netherWorld);

        IntersticeService interstice = mock(IntersticeService.class);
        when(addon.getIntersticeService()).thenReturn(interstice);

        SeaPositionTracker posTracker = mock(SeaPositionTracker.class);
        when(addon.getSeaPositionTracker()).thenReturn(posTracker);

        StarterKit starterKit = mock(StarterKit.class);
        when(addon.getStarterKit()).thenReturn(starterKit);

        IslandsManager islands = mock(IslandsManager.class);
        when(addon.getIslands()).thenReturn(islands);
        when(addon.getNavigationBarTask()).thenReturn(null);
    }

    @Test
    void testExecuteInIntersticeOpensDialog() {
        when(user.getWorld()).thenReturn(netherWorld);

        boolean result = command.execute(user, "go", new ArrayList<>());

        assertTrue(result, "Should succeed in interstice");
        verify(addon.getIntersticeService()).openReEngageDialog(mockPlayer);
    }

    @Test
    void testExecuteOutsideOcean() {
        World otherWorld = mock(World.class);
        when(user.getWorld()).thenReturn(otherWorld);
        when(user.getLocation()).thenReturn(location);
        when(addon.getSeaPositionTracker().lastKnown(mockPlayer)).thenReturn(Optional.empty());
        when(world.getSpawnLocation()).thenReturn(location);

        boolean result = command.execute(user, "go", new ArrayList<>());

        assertTrue(result, "Should teleport from outside ocean to spawn");
    }

    @Test
    void testExecuteAtSeaWithoutIsland() {
        when(addon.inWorld(any(World.class))).thenReturn(true);
        when(addon.getIslands().getIsland(addon.getOverWorld(), user.getUniqueId())).thenReturn(null);

        boolean result = command.execute(user, "go", new ArrayList<>());

        assertFalse(result, "Should refuse when islandless at sea");
    }

    @Test
    void testExecuteOnOwnIsland() {
        when(addon.inWorld(any(World.class))).thenReturn(true);

        Island island = mock(Island.class);
        when(addon.getIslands().getIsland(addon.getOverWorld(), user.getUniqueId())).thenReturn(island);
        when(island.getMemberSet()).thenReturn(ImmutableSet.of(user.getUniqueId()));
        when(island.onIsland(user.getLocation())).thenReturn(true);

        boolean result = command.execute(user, "go", new ArrayList<>());

        assertTrue(result, "Should succeed when stepping home from island");
    }

    /**
     * Compass bearing tests - testing the utility method directly
     */
    @Test
    void testCompassKeyCardinals() {
        assertEquals("tradewinds.direction.north", TWSpawnCommand.compassKey(0, -100));
        assertEquals("tradewinds.direction.south", TWSpawnCommand.compassKey(0, 100));
        assertEquals("tradewinds.direction.east", TWSpawnCommand.compassKey(100, 0));
        assertEquals("tradewinds.direction.west", TWSpawnCommand.compassKey(-100, 0));
    }

    @Test
    void testCompassKeyDiagonals() {
        assertEquals("tradewinds.direction.north-east", TWSpawnCommand.compassKey(100, -100));
        assertEquals("tradewinds.direction.south-east", TWSpawnCommand.compassKey(100, 100));
        assertEquals("tradewinds.direction.south-west", TWSpawnCommand.compassKey(-100, 100));
        assertEquals("tradewinds.direction.north-west", TWSpawnCommand.compassKey(-100, -100));
    }
}
