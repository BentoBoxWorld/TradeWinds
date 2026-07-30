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
import world.bentobox.tradewinds.galaxy.GalaxyConfig;
import world.bentobox.tradewinds.galaxy.GalaxyEngine;
import world.bentobox.tradewinds.galaxy.IslandSpec;

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
        GalaxyEngine engine = new GalaxyEngine(new GalaxyConfig(SEED, 2500, 160, 45, 1.0, 0, 5000, 70));
        when(addon.getGalaxyEngine(anyLong())).thenReturn(engine);
        when(addon.getOverWorld()).thenReturn(world);
        when(world.getSeed()).thenReturn(SEED);
        listener = new BorderPromptListener(addon);
        spec = engine.islandInCell(0, 0).orElseThrow();
    }

    @Test
    void testOfferedJustInsideBorder() {
        // 10 blocks inside the border (range 1000, trigger 30)
        Optional<IslandSpec> hit = listener.originIslandNearBorder(spec.centerX() + 990, spec.centerZ());
        assertTrue(hit.isPresent());
        assertEquals(spec, hit.get());
    }

    @Test
    void testNotOfferedDeepInside() {
        assertTrue(listener.originIslandNearBorder(spec.centerX() + 100, spec.centerZ()).isEmpty());
        assertTrue(listener.originIslandNearBorder(spec.centerX(), spec.centerZ()).isEmpty());
    }

    @Test
    void testNotOfferedOutside() {
        assertTrue(listener.originIslandNearBorder(spec.centerX() + 1100, spec.centerZ()).isEmpty());
    }
}
