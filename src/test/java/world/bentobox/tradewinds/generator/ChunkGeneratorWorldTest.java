package world.bentobox.tradewinds.generator;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.util.HashMap;
import java.util.Map;
import java.util.Random;

import org.bukkit.Material;
import org.bukkit.World.Environment;
import org.bukkit.generator.ChunkGenerator.ChunkData;
import org.bukkit.generator.WorldInfo;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.stubbing.Answer;

import world.bentobox.tradewinds.CommonTestSetup;
import world.bentobox.tradewinds.Settings;
import world.bentobox.tradewinds.TradeWinds;
import world.bentobox.tradewinds.galaxy.DockPlan;
import world.bentobox.tradewinds.galaxy.GalaxyConfig;
import world.bentobox.tradewinds.galaxy.GalaxyEngine;
import world.bentobox.tradewinds.galaxy.IslandSpec;

/**
 * Tests the ocean generation of {@link ChunkGeneratorWorld}: determinism, sea
 * levels, palettes, and the Stage 1 terrain-scale hook.
 *
 * @author tastybento
 */
class ChunkGeneratorWorldTest extends CommonTestSetup {

    private static final long SEED = 12345L;

    private TradeWinds addon;
    private Settings settings;

    /**
     * Simple in-memory ChunkData recording every block set.
     */
    private static class RecordingChunkData {
        final Map<Long, Material> blocks = new HashMap<>();

        static long key(int x, int y, int z) {
            return ((long) y << 16) | ((long) x << 8) | z;
        }

        Material get(int x, int y, int z) {
            return blocks.getOrDefault(key(x, y, z), Material.AIR);
        }
    }

    @Override
    @BeforeEach
    public void setUp() throws Exception {
        super.setUp();
        addon = mock(TradeWinds.class);
        settings = new Settings();
        when(addon.getSettings()).thenReturn(settings);
        // Default: an empty galaxy (density 0, no starter islands, no spawn islet) - pure ocean
        when(addon.getGalaxyEngine(org.mockito.ArgumentMatchers.anyLong()))
                .thenReturn(new GalaxyEngine(new GalaxyConfig(SEED, 2500, 160, 45, 0.0, 0, 5000, 70,
                        GalaxyConfig.defaultTypeWeights(), null)));
    }

    private WorldInfo worldInfo(Environment env, long seed) {
        WorldInfo wi = mock(WorldInfo.class);
        when(wi.getEnvironment()).thenReturn(env);
        when(wi.getSeed()).thenReturn(seed);
        when(wi.getMinHeight()).thenReturn(-64);
        when(wi.getMaxHeight()).thenReturn(320);
        return wi;
    }

    private ChunkData chunkData(RecordingChunkData recorder) {
        ChunkData cd = mock(ChunkData.class);
        // setBlock(x, y, z, Material)
        org.mockito.Mockito.doAnswer((Answer<Void>) inv -> {
            recorder.blocks.put(RecordingChunkData.key(inv.getArgument(0), inv.getArgument(1), inv.getArgument(2)),
                    inv.getArgument(3));
            return null;
        }).when(cd).setBlock(org.mockito.ArgumentMatchers.anyInt(), org.mockito.ArgumentMatchers.anyInt(),
                org.mockito.ArgumentMatchers.anyInt(), org.mockito.ArgumentMatchers.any(Material.class));
        // setRegion(x1, y1, z1, x2, y2, z2, Material)
        org.mockito.Mockito.doAnswer((Answer<Void>) inv -> {
            int x1 = inv.getArgument(0);
            int y1 = inv.getArgument(1);
            int z1 = inv.getArgument(2);
            int x2 = inv.getArgument(3);
            int y2 = inv.getArgument(4);
            int z2 = inv.getArgument(5);
            Material m = inv.getArgument(6);
            for (int x = x1; x < x2; x++) {
                for (int y = y1; y < y2; y++) {
                    for (int z = z1; z < z2; z++) {
                        recorder.blocks.put(RecordingChunkData.key(x, y, z), m);
                    }
                }
            }
            return null;
        }).when(cd).setRegion(org.mockito.ArgumentMatchers.anyInt(), org.mockito.ArgumentMatchers.anyInt(),
                org.mockito.ArgumentMatchers.anyInt(), org.mockito.ArgumentMatchers.anyInt(),
                org.mockito.ArgumentMatchers.anyInt(), org.mockito.ArgumentMatchers.anyInt(),
                org.mockito.ArgumentMatchers.any(Material.class));
        return cd;
    }

    private RecordingChunkData generate(ChunkGeneratorWorld gen, Environment env, long seed, int cx, int cz) {
        RecordingChunkData recorder = new RecordingChunkData();
        gen.generateNoise(worldInfo(env, seed), new Random(seed), cx, cz, chunkData(recorder));
        return recorder;
    }

    @Test
    void testOceanShape() {
        // Far from the origin: the spawn island occupies 0,0
        ChunkGeneratorWorld gen = new ChunkGeneratorWorld(addon);
        RecordingChunkData r = generate(gen, Environment.NORMAL, SEED, 40, 40);
        for (int x = 0; x < 16; x++) {
            for (int z = 0; z < 16; z++) {
                // Bedrock at the bottom
                assertEquals(Material.BEDROCK, r.get(x, -64, z));
                // Solid stone base below the sea floor
                assertEquals(Material.STONE, r.get(x, 0, z));
                // Water at sea level
                assertEquals(Material.WATER, r.get(x, settings.getSeaHeight(), z));
                // Air above sea level
                assertEquals(Material.AIR, r.get(x, settings.getSeaHeight() + 1, z));
                // Floor surface starts at or above the sea floor
                Material atFloor = r.get(x, settings.getSeaFloor(), z);
                assertTrue(atFloor == Material.SAND || atFloor == Material.SANDSTONE || atFloor == Material.WATER,
                        "Unexpected material at sea floor: " + atFloor);
            }
        }
    }

    @Test
    void testIntersticeShape() {
        ChunkGeneratorWorld gen = new ChunkGeneratorWorld(addon);
        RecordingChunkData r = generate(gen, Environment.NETHER, SEED, 0, 0);
        for (int x = 0; x < 16; x++) {
            for (int z = 0; z < 16; z++) {
                assertEquals(Material.BEDROCK, r.get(x, -64, z));
                // Netherrack base below the sea floor
                assertEquals(Material.NETHERRACK, r.get(x, 0, z));
                // Water sea at interstice sea level
                assertEquals(Material.WATER, r.get(x, settings.getIntersticeSeaHeight(), z));
                assertEquals(Material.AIR, r.get(x, settings.getIntersticeSeaHeight() + 1, z));
                // Floor palette is nether-flavored
                Material atFloor = r.get(x, settings.getIntersticeSeaFloor(), z);
                assertTrue(atFloor == Material.SOUL_SAND || atFloor == Material.BASALT || atFloor == Material.WATER,
                        "Unexpected material at interstice floor: " + atFloor);
            }
        }
    }

    @Test
    void testDeterminism() {
        // Same seed, same chunk -> identical blocks, across generator instances
        RecordingChunkData a = generate(new ChunkGeneratorWorld(addon), Environment.NORMAL, SEED, 3, -7);
        RecordingChunkData b = generate(new ChunkGeneratorWorld(addon), Environment.NORMAL, SEED, 3, -7);
        assertEquals(a.blocks, b.blocks);
        // Different seed -> different floor
        RecordingChunkData c = generate(new ChunkGeneratorWorld(addon), Environment.NORMAL, SEED + 1, 3, -7);
        assertFalse(a.blocks.equals(c.blocks));
    }

    @Test
    void testNoLandAboveSeaLevelAtScaleOne() {
        // Stage 0: flat ocean everywhere - nothing may poke above the sea surface
        ChunkGeneratorWorld gen = new ChunkGeneratorWorld(addon);
        RecordingChunkData r = generate(gen, Environment.NORMAL, SEED, 5, 5);
        for (int x = 0; x < 16; x++) {
            for (int z = 0; z < 16; z++) {
                for (int y = settings.getSeaHeight() + 1; y < 320; y++) {
                    assertEquals(Material.AIR, r.get(x, y, z));
                }
            }
        }
    }

    @Test
    void testTerrainLiftMakesIslands() {
        // A galaxy with an island at this chunk must lift grassy land above the sea
        world.bentobox.tradewinds.galaxy.GalaxyEngine denseEngine = new GalaxyEngine(
                new GalaxyConfig(SEED, 2500, 160, 45, 1.0, 0, 5000, 70));
        when(addon.getGalaxyEngine(org.mockito.ArgumentMatchers.anyLong())).thenReturn(denseEngine);
        IslandSpec spec = denseEngine.islandInCell(0, 0).orElseThrow();
        ChunkGeneratorWorld gen = new ChunkGeneratorWorld(addon);
        RecordingChunkData r = generate(gen, Environment.NORMAL, SEED, spec.centerX() >> 4, spec.centerZ() >> 4);

        boolean grassAboveSea = false;
        for (int x = 0; x < 16 && !grassAboveSea; x++) {
            for (int z = 0; z < 16 && !grassAboveSea; z++) {
                for (int y = settings.getSeaHeight() + 1; y < settings.getSeaHeight() + 45; y++) {
                    if (r.get(x, y, z) == Material.GRASS_BLOCK) {
                        grassAboveSea = true;
                        break;
                    }
                }
            }
        }
        assertTrue(grassAboveSea, "The island mask should lift grassy land above sea level");

        // And the interstice ignores the galaxy entirely
        RecordingChunkData nether = generate(gen, Environment.NETHER, SEED, spec.centerX() >> 4, spec.centerZ() >> 4);
        for (int x = 0; x < 16; x++) {
            for (int z = 0; z < 16; z++) {
                assertEquals(Material.AIR, nether.get(x, settings.getIntersticeSeaHeight() + 1, z));
            }
        }
    }

    @Test
    void testPlazaAndDockTerraform() {
        world.bentobox.tradewinds.galaxy.GalaxyEngine denseEngine = new GalaxyEngine(
                new GalaxyConfig(SEED, 2500, 160, 45, 1.0, 0, 5000, 70));
        when(addon.getGalaxyEngine(org.mockito.ArgumentMatchers.anyLong())).thenReturn(denseEngine);
        IslandSpec spec = denseEngine.islandInCell(0, 0).orElseThrow();
        DockPlan plan = denseEngine.dockPlan(spec);
        ChunkGeneratorWorld gen = new ChunkGeneratorWorld(addon);

        // Plaza center chunk: flat dirt-path surface at seaHeight + PLAZA_RISE
        RecordingChunkData plaza = generate(gen, Environment.NORMAL, SEED, plan.plazaX() >> 4, plan.plazaZ() >> 4);
        int px = plan.plazaX() & 15;
        int pz = plan.plazaZ() & 15;
        int plazaSurface = settings.getSeaHeight() + GalaxyEngine.PLAZA_RISE;
        assertEquals(IslandPalette.plazaSurface(spec.type()), plaza.get(px, plazaSurface, pz));
        assertEquals(Material.AIR, plaza.get(px, plazaSurface + 1, pz));

        // A point on the quay: plank deck at seaHeight + DOCK_RISE over stone bricks, no water above
        int dockDist = plan.dockEnd() - 4;
        int dx = spec.centerX() + (int) Math.round(Math.cos(plan.bearing()) * dockDist);
        int dz = spec.centerZ() + (int) Math.round(Math.sin(plan.bearing()) * dockDist);
        RecordingChunkData dock = generate(gen, Environment.NORMAL, SEED, dx >> 4, dz >> 4);
        int deckY = settings.getSeaHeight() + GalaxyEngine.DOCK_RISE;
        assertEquals(IslandPalette.planks(spec.type()), dock.get(dx & 15, deckY, dz & 15));
        assertEquals(Material.STONE_BRICKS, dock.get(dx & 15, deckY - 1, dz & 15));
        assertEquals(Material.AIR, dock.get(dx & 15, deckY + 1, dz & 15));
    }

    @Test
    void testSpawnIslandRisesAtOrigin() {
        // Even with an empty galaxy, the reserved spawn island makes land at 0,0
        when(addon.getGalaxyEngine(org.mockito.ArgumentMatchers.anyLong()))
                .thenReturn(new GalaxyEngine(new GalaxyConfig(SEED, 2500, 160, 45, 0.0, 0, 5000, 70)));
        ChunkGeneratorWorld gen = new ChunkGeneratorWorld(addon);
        RecordingChunkData r = generate(gen, Environment.NORMAL, SEED, 0, 0);
        boolean landAboveSea = false;
        for (int y = settings.getSeaHeight() + 1; y < settings.getSeaHeight() + 50 && !landAboveSea; y++) {
            landAboveSea = r.get(0, y, 0) == Material.GRASS_BLOCK;
        }
        assertTrue(landAboveSea, "The spawn island should rise above the sea at the origin");
    }

    @Test
    void testShouldGenerateFlags() {
        ChunkGeneratorWorld gen = new ChunkGeneratorWorld(addon);
        // Vanilla terrain off: land only ever comes from island masks
        assertFalse(gen.shouldGenerateNoise());
        assertFalse(gen.shouldGenerateSurface());
        assertTrue(gen.shouldGenerateMobs());
        assertFalse(gen.shouldGenerateCaves());
        assertTrue(gen.shouldGenerateDecorations());
        assertFalse(gen.shouldGenerateStructures());
    }
}
