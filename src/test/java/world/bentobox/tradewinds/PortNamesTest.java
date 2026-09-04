package world.bentobox.tradewinds;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.util.List;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import world.bentobox.bentobox.api.user.User;
import world.bentobox.tradewinds.ocean.IslandSpec;
import world.bentobox.tradewinds.ocean.IslandType;
import world.bentobox.tradewinds.ocean.NameGenerator;
import world.bentobox.tradewinds.ocean.OceanConfig;
import world.bentobox.tradewinds.ocean.OceanEngine;
import world.bentobox.tradewinds.ocean.SecurityBand;
import world.bentobox.tradewinds.travel.StarChartRenderer;

/**
 * Port names for #7: tokens transliterated through the locale, the canonical
 * name kept as identity.
 */
class PortNamesTest extends CommonTestSetup {

    private TradeWinds addon;
    private OceanEngine engine;
    private User user;
    private IslandSpec port;

    @BeforeEach
    void setUpOcean() {
        addon = mock(TradeWinds.class);
        engine = new OceanEngine(new OceanConfig(11L, 2500, 160, 45, 1.0, 0, 5000, 70));
        when(addon.getOceanEngine()).thenReturn(engine);
        user = User.getInstance(mockPlayer);
        port = null;
        for (int cx = -5; cx <= 5 && port == null; cx++) {
            for (int cz = -5; cz <= 5 && port == null; cz++) {
                port = engine.islandInCell(cx, cz).orElse(null);
            }
        }
    }

    @Test
    void testUnmappedTokensReadAsTheCanonicalName() {
        assertEquals(port.name(), PortNames.display(addon, user, port));
    }

    @Test
    void testTokensAreTransliterated() {
        List<String> tokens = engine.nameTokens(port);
        assertEquals(port.name(), NameGenerator.join(tokens));
        StringBuilder expected = new StringBuilder();
        for (String token : tokens) {
            String glyph = "\u4e00" + token.toLowerCase();
            when(lm.get(any(), eq("tradewinds.name-token." + token.toLowerCase()))).thenReturn(glyph);
            expected.append(glyph);
        }
        assertEquals(expected.toString(), PortNames.display(addon, user, port));
        // Identity untouched
        assertEquals(NameGenerator.join(tokens), port.name());
    }

    @Test
    void testAsciiTransliterationKeepsTheCapital() {
        List<String> tokens = engine.nameTokens(port);
        String first = tokens.get(0).toLowerCase();
        when(lm.get(any(), eq("tradewinds.name-token." + first))).thenReturn("zz");
        String shown = PortNames.display(addon, user, port);
        assertEquals("Zz" + port.name().substring(first.length()).toLowerCase(), shown);
    }

    @Test
    void testNoEngineOrForeignSpecFallsBackToCanonical() {
        IslandSpec foreign = new IslandSpec(0, 0, 0, 0, IslandType.FISHING, SecurityBand.SAFE, "minecraft:beach",
                "Handmade");
        assertEquals("Handmade", PortNames.display(addon, user, foreign));
        when(addon.getOceanEngine()).thenReturn(null);
        assertEquals(port.name(), PortNames.display(addon, user, port));
    }

    @Test
    void testChartDrawsOnlyWhatTheMapFontCan() {
        assertEquals("Lave", StarChartRenderer.drawableName(port, "Lave"));
        assertEquals(port.name(), StarChartRenderer.drawableName(port, "\u62c9\u7ef4"));
    }
}
