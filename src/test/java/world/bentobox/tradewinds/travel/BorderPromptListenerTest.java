package world.bentobox.tradewinds.travel;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.util.Optional;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import world.bentobox.tradewinds.CommonTestSetup;
import world.bentobox.tradewinds.Settings;
import world.bentobox.tradewinds.TradeWinds;
import world.bentobox.tradewinds.ocean.OceanConfig;
import world.bentobox.tradewinds.ocean.OceanEngine;
import world.bentobox.tradewinds.ocean.IslandSpec;

/**
 * Tests the warp-offer geometry: the dialog is offered just inside the border,
 * not at the island center, not in open ocean.
 *
 * @author tastybento
 */
class BorderPromptListenerTest extends CommonTestSetup {

    private static final long SEED = 606L;

    private BorderPromptListener listener;
    private IslandSpec spec;

    @Override
    @BeforeEach
    public void setUp() throws Exception {
        super.setUp();
        TradeWinds addon = mock(TradeWinds.class);
        Settings settings = new Settings();
        when(addon.getSettings()).thenReturn(settings);
        OceanEngine engine = new OceanEngine(new OceanConfig(SEED, 2500, 160, 45, 1.0, 0, 5000, 70));
        when(addon.getOceanEngine(anyLong())).thenReturn(engine);
        when(addon.getOverWorld()).thenReturn(world);
        when(world.getSeed()).thenReturn(SEED);
        listener = new BorderPromptListener(addon);
        spec = engine.islandInCell(0, 0).orElseThrow();
    }

    @Test
    void testOfferedAtVisibleBorder() {
        // The ring hugs the protection edge (400) tightly now - you have to
        // touch the RED curtain (ruled 2026-08-02), so +-5 blocks only
        Optional<IslandSpec> inside = listener.originIslandNearBorder(spec.centerX() + 397, spec.centerZ());
        assertTrue(inside.isPresent());
        assertEquals(spec, inside.get());
        Optional<IslandSpec> outside = listener.originIslandNearBorder(spec.centerX() + 403, spec.centerZ());
        assertTrue(outside.isPresent());
        // Just short of the curtain, or just past it: no dialog
        assertTrue(listener.originIslandNearBorder(spec.centerX() + 390, spec.centerZ()).isEmpty());
        assertTrue(listener.originIslandNearBorder(spec.centerX() + 410, spec.centerZ()).isEmpty());
    }

    @Test
    void testNotOfferedDeepInside() {
        assertTrue(listener.originIslandNearBorder(spec.centerX() + 100, spec.centerZ()).isEmpty());
        assertTrue(listener.originIslandNearBorder(spec.centerX(), spec.centerZ()).isEmpty());
    }

    @Test
    void testNotOfferedBeyondTheRing() {
        // Still inside island waters but past the ring: no repeat offers
        assertTrue(listener.originIslandNearBorder(spec.centerX() + 600, spec.centerZ()).isEmpty());
        assertTrue(listener.originIslandNearBorder(spec.centerX() + 1100, spec.centerZ()).isEmpty());
    }
}
