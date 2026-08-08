package world.bentobox.tradewinds.listeners;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.util.List;

import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.entity.Player;
import org.bukkit.event.entity.EntityPortalEvent;
import org.bukkit.event.player.PlayerPortalEvent;
import org.bukkit.event.world.PortalCreateEvent;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import world.bentobox.tradewinds.CommonTestSetup;
import world.bentobox.tradewinds.TradeWinds;

/**
 * Tests {@link IntersticePortalListener}: no portal forms and no portal
 * teleports in either TradeWinds world; other worlds are untouched.
 *
 * @author tastybento
 */
class IntersticePortalListenerTest extends CommonTestSetup {

    private TradeWinds addon;
    private IntersticePortalListener listener;
    private World overworld;
    private World interstice;
    private World elsewhere;

    @Override
    @BeforeEach
    public void setUp() throws Exception {
        super.setUp();
        addon = mock(TradeWinds.class);
        overworld = mock(World.class);
        interstice = mock(World.class);
        elsewhere = mock(World.class);
        when(addon.getOverWorld()).thenReturn(overworld);
        when(addon.getNetherWorld()).thenReturn(interstice);
        listener = new IntersticePortalListener(addon);
    }

    @Test
    void testPortalCreationBlockedInTradeWindsWorlds() {
        PortalCreateEvent inOverworld = new PortalCreateEvent(List.of(), overworld, mockPlayer,
                PortalCreateEvent.CreateReason.FIRE);
        listener.onPortalCreate(inOverworld);
        assertTrue(inOverworld.isCancelled());

        PortalCreateEvent inInterstice = new PortalCreateEvent(List.of(), interstice, mockPlayer,
                PortalCreateEvent.CreateReason.FIRE);
        listener.onPortalCreate(inInterstice);
        assertTrue(inInterstice.isCancelled());

        PortalCreateEvent outside = new PortalCreateEvent(List.of(), elsewhere, mockPlayer,
                PortalCreateEvent.CreateReason.FIRE);
        listener.onPortalCreate(outside);
        assertFalse(outside.isCancelled());
    }

    @Test
    void testPlayerPortalBlocked() {
        Player player = mock(Player.class);
        PlayerPortalEvent fromOverworld = new PlayerPortalEvent(player, new Location(overworld, 0, 70, 0),
                new Location(elsewhere, 0, 70, 0));
        listener.onPlayerPortal(fromOverworld);
        assertTrue(fromOverworld.isCancelled());

        // Portals from other worlds must not reach the interstice either
        PlayerPortalEvent intoInterstice = new PlayerPortalEvent(player, new Location(elsewhere, 0, 70, 0),
                new Location(interstice, 0, 70, 0));
        listener.onPlayerPortal(intoInterstice);
        assertTrue(intoInterstice.isCancelled());

        PlayerPortalEvent unrelated = new PlayerPortalEvent(player, new Location(elsewhere, 0, 70, 0),
                new Location(elsewhere, 0, 70, 0));
        listener.onPlayerPortal(unrelated);
        assertFalse(unrelated.isCancelled());
    }

    @Test
    void testEntityPortalBlocked() {
        EntityPortalEvent event = new EntityPortalEvent(mockPlayer, new Location(overworld, 0, 70, 0),
                new Location(elsewhere, 0, 70, 0));
        listener.onEntityPortal(event);
        assertTrue(event.isCancelled());

        EntityPortalEvent unrelated = new EntityPortalEvent(mockPlayer, new Location(elsewhere, 0, 70, 0),
                new Location(elsewhere, 0, 70, 0));
        listener.onEntityPortal(unrelated);
        assertFalse(unrelated.isCancelled());
    }
}
