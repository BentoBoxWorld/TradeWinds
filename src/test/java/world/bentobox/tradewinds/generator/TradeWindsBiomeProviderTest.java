package world.bentobox.tradewinds.generator;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.util.List;

import org.bukkit.World.Environment;
import org.bukkit.block.Biome;
import org.bukkit.generator.WorldInfo;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import world.bentobox.tradewinds.CommonTestSetup;
import world.bentobox.tradewinds.Settings;
import world.bentobox.tradewinds.TradeWinds;
import world.bentobox.tradewinds.ocean.OceanConfig;
import world.bentobox.tradewinds.ocean.OceanEngine;
import world.bentobox.tradewinds.ocean.IslandSpec;

/**
 * Tests {@link TradeWindsBiomeProvider}.
 *
 * @author tastybento
 */
class TradeWindsBiomeProviderTest extends CommonTestSetup {

    private static final long SEED = 424242L;

    private TradeWinds addon;
    private Settings settings;
    private TradeWindsBiomeProvider provider;
    private OceanEngine emptyOcean;
    private OceanEngine denseOcean;

    @Override
    @BeforeEach
    public void setUp() throws Exception {
        super.setUp();
        addon = mock(TradeWinds.class);
        settings = new Settings();
        when(addon.getSettings()).thenReturn(settings);
        emptyOcean = new OceanEngine(new OceanConfig(SEED, 2500, 160, 45, 0.0, 0, 5000, 70,
                OceanConfig.defaultTypeWeights(), null));
        denseOcean = new OceanEngine(new OceanConfig(SEED, 2500, 160, 45, 1.0, 0, 5000, 70,
                OceanConfig.defaultTypeWeights(), null));
        when(addon.getOceanEngine(anyLong())).thenReturn(emptyOcean);
        provider = new TradeWindsBiomeProvider(addon);
    }

    private WorldInfo worldInfo(Environment env) {
        WorldInfo wi = mock(WorldInfo.class);
        when(wi.getEnvironment()).thenReturn(env);
        when(wi.getSeed()).thenReturn(SEED);
        return wi;
    }

    @Test
    void testOpenOceanVariesThroughOceanBiomes() {
        // Sampled far from the origin - the spawn island sits at 0,0
        WorldInfo wi = worldInfo(Environment.NORMAL);
        java.util.Set<Biome> seen = new java.util.HashSet<>();
        java.util.Set<Biome> oceans = OceanEngine.oceanBiomes().stream()
                .map(key -> org.bukkit.Registry.BIOME.get(org.bukkit.NamespacedKey.fromString(key)))
                .collect(java.util.stream.Collectors.toSet());
        for (int x = 100_000; x < 140_000; x += 500) {
            if (emptyOcean.isletAt(x, 12_345).isPresent()) {
                continue; // A wild islet is land, not sea - it has its own biome
            }
            Biome biome = provider.getBiome(wi, x, settings.getSeaHeight(), 12_345);
            assertTrue(oceans.contains(biome), "Open sea should be an ocean biome, got " + biome.getKey());
            // The same column above water reads the same sea
            assertEquals(biome, provider.getBiome(wi, x, settings.getSeaHeight() + 1, 12_345));
            seen.add(biome);
        }
        assertTrue(seen.size() > 1, "The open sea should vary");
    }

    @Test
    void testUniformOceanWhenVaryingIsOff() {
        settings.setVaryOceanBiomes(false);
        WorldInfo wi = worldInfo(Environment.NORMAL);
        assertEquals(Biome.OCEAN, provider.getBiome(wi, 100_000, settings.getSeaHeight(), 100_000));
        assertEquals(settings.getDefaultAirBiome(),
                provider.getBiome(wi, 100_000, settings.getSeaHeight() + 1, 100_000));
    }

    @Test
    void testIslandBiome() {
        when(addon.getOceanEngine(anyLong())).thenReturn(denseOcean);
        IslandSpec spec = denseOcean.islandInCell(0, 0).orElseThrow();
        WorldInfo wi = worldInfo(Environment.NORMAL);
        Biome islandBiome = provider.getBiome(wi, spec.centerX(), settings.getSeaHeight() + 5, spec.centerZ());
        // The island biome applies to the whole column and is one of the type's table
        assertEquals(islandBiome, provider.getBiome(wi, spec.centerX(), 30, spec.centerZ()));
        assertTrue(spec.type().getBiomeKeys().contains(islandBiome.getKey().toString()),
                "Island biome " + islandBiome.getKey() + " not in type table " + spec.type().getBiomeKeys());
    }

    @Test
    void testIntersticeBiome() {
        WorldInfo wi = worldInfo(Environment.NETHER);
        assertEquals(Biome.NETHER_WASTES, provider.getBiome(wi, 0, 64, 0));
        assertEquals(List.of(Biome.NETHER_WASTES), provider.getBiomes(wi));
    }

    @Test
    void testGetBiomesListsAllPossible() {
        List<Biome> biomes = provider.getBiomes(worldInfo(Environment.NORMAL));
        assertTrue(biomes.contains(Biome.OCEAN));
        // All ocean island biomes must be declared to the world
        assertTrue(biomes.contains(Biome.PLAINS));
        assertTrue(biomes.contains(Biome.SNOWY_PLAINS));
        assertTrue(biomes.contains(Biome.FROZEN_OCEAN));
        assertTrue(biomes.contains(Biome.CHERRY_GROVE));
    }
}
