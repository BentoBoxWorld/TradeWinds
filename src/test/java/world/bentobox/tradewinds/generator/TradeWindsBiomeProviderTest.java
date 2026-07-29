package world.bentobox.tradewinds.generator;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import org.bukkit.World.Environment;
import org.bukkit.block.Biome;
import org.bukkit.generator.WorldInfo;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import world.bentobox.tradewinds.CommonTestSetup;
import world.bentobox.tradewinds.Settings;
import world.bentobox.tradewinds.TradeWinds;

/**
 * Tests {@link TradeWindsBiomeProvider}.
 *
 * @author tastybento
 */
class TradeWindsBiomeProviderTest extends CommonTestSetup {

    private TradeWinds addon;
    private Settings settings;
    private TradeWindsBiomeProvider provider;

    @Override
    @BeforeEach
    public void setUp() throws Exception {
        super.setUp();
        addon = mock(TradeWinds.class);
        settings = new Settings();
        when(addon.getSettings()).thenReturn(settings);
        provider = new TradeWindsBiomeProvider(addon);
    }

    private WorldInfo worldInfo(Environment env) {
        WorldInfo wi = mock(WorldInfo.class);
        when(wi.getEnvironment()).thenReturn(env);
        return wi;
    }

    @Test
    void testOceanBiomes() {
        WorldInfo wi = worldInfo(Environment.NORMAL);
        assertEquals(Biome.OCEAN, provider.getBiome(wi, 0, settings.getSeaHeight(), 0));
        assertEquals(Biome.OCEAN, provider.getBiome(wi, 0, 10, 0));
        assertEquals(settings.getDefaultAirBiome(), provider.getBiome(wi, 0, settings.getSeaHeight() + 1, 0));
    }

    @Test
    void testIntersticeBiome() {
        WorldInfo wi = worldInfo(Environment.NETHER);
        assertEquals(Biome.NETHER_WASTES, provider.getBiome(wi, 0, 64, 0));
        assertEquals(Biome.NETHER_WASTES, provider.getBiome(wi, 0, 100, 0));
    }

    @Test
    void testGetBiomes() {
        assertTrue(provider.getBiomes(worldInfo(Environment.NORMAL)).contains(Biome.OCEAN));
        assertEquals(java.util.List.of(Biome.NETHER_WASTES), provider.getBiomes(worldInfo(Environment.NETHER)));
    }
}
