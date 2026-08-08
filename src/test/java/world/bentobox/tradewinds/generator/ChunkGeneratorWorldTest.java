package world.bentobox.tradewinds.generator;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNotSame;
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
import world.bentobox.tradewinds.ocean.DockPlan;
import world.bentobox.tradewinds.ocean.OceanConfig;
import world.bentobox.tradewinds.ocean.OceanEngine;
import world.bentobox.tradewinds.ocean.IslandSpec;

/**
 * Tests the ocean generation of {@link ChunkGeneratorWorld}: determinism, sea
 * levels, palettes, and the Stage 1 terrain-scale hook.
 *
 * @author tastybento
 */
class ChunkGeneratorWorldTest extends CommonTestSetup {

    private static final long SEED = 12345L;
    /** Mirrors ChunkGeneratorWorld.CRUST_THICKNESS. */
    private static final int CRUST = 5;

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
        // Default: an empty ocean (density 0, no starter islands, no spawn islet) - pure ocean
        when(addon.getOceanEngine(org.mockito.ArgumentMatchers.anyLong()))
                .thenReturn(new OceanEngine(new OceanConfig(SEED, 2500, 160, 45, 0.0, 0, 5000, 70,
                        OceanConfig.defaultTypeWeights(), null)));
        // Default: a featureless interstice (no shoals, no towers) - pure dark sea
        when(addon.getIntersticeMap(org.mockito.ArgumentMatchers.anyLong()))
                .thenReturn(new world.bentobox.tradewinds.ocean.IntersticeMap(SEED, 256, 0.0, 9, 0.2, 1536,
                        0.0, new world.bentobox.tradewinds.ocean.IntersticeMap.WreckTuning(320, 0.0, 0.0)));
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
        // getType, so the cave-sealing pass can read back what the carvers left
        when(cd.getType(org.mockito.ArgumentMatchers.anyInt(), org.mockito.ArgumentMatchers.anyInt(),
                org.mockito.ArgumentMatchers.anyInt()))
                .thenAnswer(inv -> recorder.get(inv.getArgument(0), inv.getArgument(1), inv.getArgument(2)));
        return cd;
    }

    private RecordingChunkData generate(ChunkGeneratorWorld gen, Environment env, long seed, int cx, int cz) {
        RecordingChunkData recorder = new RecordingChunkData();
        gen.generateNoise(worldInfo(env, seed), new Random(seed), cx, cz, chunkData(recorder));
        return recorder;
    }

    /**
     * The Y of the topmost solid floor block in a column, found by sounding
     * down from the sea surface the way a lead line would.
     */
    private int floorTop(RecordingChunkData r, int x, int z, int seaHeight) {
        for (int y = seaHeight; y > -64; y--) {
            Material m = r.get(x, y, z);
            if (m != Material.WATER && m != Material.AIR) {
                return y;
            }
        }
        return -64;
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
                // Solid stone base under the whole floor, deepslate below Y 0
                assertEquals(Material.STONE, r.get(x, 0, z));
                assertEquals(Material.DEEPSLATE, r.get(x, -32, z));
                // Water at sea level
                assertEquals(Material.WATER, r.get(x, settings.getSeaHeight(), z));
                // Air above sea level
                assertEquals(Material.AIR, r.get(x, settings.getSeaHeight() + 1, z));
                // The floor is sediment or rock, and it is under water
                int top = floorTop(r, x, z, settings.getSeaHeight());
                assertTrue(top < settings.getSeaHeight(), "Open sea floor broke the surface at y=" + top);
                Material atFloor = r.get(x, top, z);
                assertTrue(
                        atFloor == Material.SAND || atFloor == Material.GRAVEL || atFloor == Material.CLAY
                                || atFloor == Material.STONE || atFloor == Material.TUFF,
                        "Unexpected material at sea floor: " + atFloor);
            }
        }
    }

    @Test
    void testSeaFloorIsNotFlat() {
        // The playtest complaint: "the sea floor is really barren and
        // repetitive". Sound a long transect and check it actually moves.
        ChunkGeneratorWorld gen = new ChunkGeneratorWorld(addon);
        int shallowest = Integer.MIN_VALUE;
        int deepest = Integer.MAX_VALUE;
        for (int chunk = 20; chunk < 160; chunk += 7) {
            RecordingChunkData r = generate(gen, Environment.NORMAL, SEED, chunk, 40);
            for (int x = 0; x < 16; x += 4) {
                int top = floorTop(r, x, 8, settings.getSeaHeight());
                shallowest = Math.max(shallowest, top);
                deepest = Math.min(deepest, top);
            }
        }
        assertTrue(shallowest - deepest > 25, "Sea floor barely varies: y " + deepest + " to " + shallowest);
    }

    @Test
    void testIntersticeShape() {
        ChunkGeneratorWorld gen = new ChunkGeneratorWorld(addon);
        RecordingChunkData r = generate(gen, Environment.NETHER, SEED, 0, 0);
        for (int x = 0; x < 16; x++) {
            for (int z = 0; z < 16; z++) {
                assertEquals(Material.BEDROCK, r.get(x, -64, z));
                // Netherrack base under the floor
                assertEquals(Material.NETHERRACK, r.get(x, 0, z));
                // Water sea at interstice sea level
                assertEquals(Material.WATER, r.get(x, settings.getIntersticeSeaHeight(), z));
                assertEquals(Material.AIR, r.get(x, settings.getIntersticeSeaHeight() + 1, z));
                // Floor palette is nether-flavored
                Material atFloor = r.get(x, floorTop(r, x, z, settings.getIntersticeSeaHeight()), z);
                assertTrue(atFloor == Material.SOUL_SAND || atFloor == Material.BASALT,
                        "Unexpected material at interstice floor: " + atFloor);
            }
        }
    }

    @Test
    void testWartShoalsBreakTheIntersticeSurface() {
        // Shoals on at defaults: find one and generate its chunk
        world.bentobox.tradewinds.ocean.IntersticeMap map = new world.bentobox.tradewinds.ocean.IntersticeMap(
                SEED, 256, 0.5, 9, 0.2, 1536, 0.0, new world.bentobox.tradewinds.ocean.IntersticeMap.WreckTuning(320, 0.0, 0.0));
        when(addon.getIntersticeMap(org.mockito.ArgumentMatchers.anyLong())).thenReturn(map);
        world.bentobox.tradewinds.ocean.IntersticeMap.Shoal shoal = null;
        for (int cx = 0; cx < 30 && shoal == null; cx++) {
            shoal = map.shoalInCell(cx, 4).orElse(null);
        }
        assertNotNull(shoal, "No shoal in 30 cells - wrong seed?");
        ChunkGeneratorWorld gen = new ChunkGeneratorWorld(addon);
        RecordingChunkData r = generate(gen, Environment.NETHER, SEED, shoal.centerX() >> 4,
                shoal.centerZ() >> 4);
        int x = shoal.centerX() & 15;
        int z = shoal.centerZ() & 15;
        int sea = settings.getIntersticeSeaHeight();
        // The crown stands proud of the sea...
        int top = floorTop(r, x, z, sea + 4);
        assertTrue(top > sea, "Shoal crown should break the surface, top=" + top + " sea=" + sea);
        // ...and it is soul sand - the one block nether wart plants on
        assertEquals(Material.SOUL_SAND, r.get(x, top, z));
    }

    @Test
    void testDeterminism() {
        // Same seed, same chunk -> identical blocks, across generator instances
        RecordingChunkData a = generate(new ChunkGeneratorWorld(addon), Environment.NORMAL, SEED, 3, -7);
        RecordingChunkData b = generate(new ChunkGeneratorWorld(addon), Environment.NORMAL, SEED, 3, -7);
        assertEquals(a.blocks, b.blocks);
        // A different ocean seed -> a different sea floor. The seabed hangs off
        // the ocean seed, not the world seed, so that one number still decides
        // the whole world (spec principle 5).
        when(addon.getOceanEngine(org.mockito.ArgumentMatchers.anyLong()))
                .thenReturn(new OceanEngine(new OceanConfig(SEED + 1, 2500, 160, 45, 0.0, 0, 5000, 70,
                        OceanConfig.defaultTypeWeights(), null)));
        RecordingChunkData c = generate(new ChunkGeneratorWorld(addon), Environment.NORMAL, SEED, 3, -7);
        assertNotEquals(a.blocks, c.blocks);
    }

    @Test
    void testNoLandAboveSeaLevelAtScaleOne() {
        // With no islands and no islets, nothing may poke above the sea surface:
        // seamounts, shoals and relief all stay under water
        when(addon.getOceanEngine(org.mockito.ArgumentMatchers.anyLong()))
                .thenReturn(new OceanEngine(new OceanConfig(SEED, 2500, 160, 45, 0.0, 0, 5000, 70,
                        OceanConfig.defaultTypeWeights(), null, 0.0, 0, 900)));
        ChunkGeneratorWorld gen = new ChunkGeneratorWorld(addon);
        RecordingChunkData r = generate(gen, Environment.NORMAL, SEED, 200, 200);
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
        // A ocean with an island at this chunk must lift grassy land above the sea
        world.bentobox.tradewinds.ocean.OceanEngine denseEngine = new OceanEngine(
                new OceanConfig(SEED, 2500, 160, 45, 1.0, 0, 5000, 70));
        when(addon.getOceanEngine(org.mockito.ArgumentMatchers.anyLong())).thenReturn(denseEngine);
        IslandSpec spec = denseEngine.islandInCell(0, 0).orElseThrow();
        ChunkGeneratorWorld gen = new ChunkGeneratorWorld(addon);
        RecordingChunkData r = generate(gen, Environment.NORMAL, SEED, spec.centerX() >> 4, spec.centerZ() >> 4);

        // Land, not necessarily lawn: the surface follows the island's biome
        // (sand under a desert port, mud under a mangrove one)
        boolean landAboveSea = false;
        for (int x = 0; x < 16 && !landAboveSea; x++) {
            for (int z = 0; z < 16 && !landAboveSea; z++) {
                for (int y = settings.getSeaHeight() + 1; y < settings.getSeaHeight() + 45; y++) {
                    Material m = r.get(x, y, z);
                    if (m != Material.AIR && m != Material.WATER) {
                        landAboveSea = true;
                        break;
                    }
                }
            }
        }
        assertTrue(landAboveSea, "The island mask should lift land above sea level");

        // And the interstice ignores the ocean entirely
        RecordingChunkData nether = generate(gen, Environment.NETHER, SEED, spec.centerX() >> 4, spec.centerZ() >> 4);
        for (int x = 0; x < 16; x++) {
            for (int z = 0; z < 16; z++) {
                assertEquals(Material.AIR, nether.get(x, settings.getIntersticeSeaHeight() + 1, z));
            }
        }
    }

    @Test
    void testPlazaAndDockTerraform() {
        world.bentobox.tradewinds.ocean.OceanEngine denseEngine = new OceanEngine(
                new OceanConfig(SEED, 2500, 160, 45, 1.0, 0, 5000, 70));
        when(addon.getOceanEngine(org.mockito.ArgumentMatchers.anyLong())).thenReturn(denseEngine);
        IslandSpec spec = denseEngine.islandInCell(0, 0).orElseThrow();
        DockPlan plan = denseEngine.dockPlan(spec);
        ChunkGeneratorWorld gen = new ChunkGeneratorWorld(addon);

        // Plaza center chunk: flat dirt-path surface at seaHeight + PLAZA_RISE
        RecordingChunkData plaza = generate(gen, Environment.NORMAL, SEED, plan.plazaX() >> 4, plan.plazaZ() >> 4);
        int px = plan.plazaX() & 15;
        int pz = plan.plazaZ() & 15;
        int plazaSurface = settings.getSeaHeight() + OceanEngine.PLAZA_RISE;
        assertEquals(IslandPalette.plazaSurface(spec.type()), plaza.get(px, plazaSurface, pz));
        assertEquals(Material.AIR, plaza.get(px, plazaSurface + 1, pz));

        // A point on the quay: plank deck at seaHeight + DOCK_RISE over stone bricks, no water above
        int dockDist = plan.dockEnd() - 4;
        int dx = spec.centerX() + (int) Math.round(Math.cos(plan.bearing()) * dockDist);
        int dz = spec.centerZ() + (int) Math.round(Math.sin(plan.bearing()) * dockDist);
        RecordingChunkData dock = generate(gen, Environment.NORMAL, SEED, dx >> 4, dz >> 4);
        int deckY = settings.getSeaHeight() + OceanEngine.DOCK_RISE;
        assertEquals(IslandPalette.planks(spec.type()), dock.get(dx & 15, deckY, dz & 15));
        assertEquals(Material.STONE_BRICKS, dock.get(dx & 15, deckY - 1, dz & 15));
        assertEquals(Material.AIR, dock.get(dx & 15, deckY + 1, dz & 15));
    }

    @Test
    void testSpawnIslandRisesAtOrigin() {
        // Even with an empty ocean, the reserved spawn island makes land at 0,0
        when(addon.getOceanEngine(org.mockito.ArgumentMatchers.anyLong()))
                .thenReturn(new OceanEngine(new OceanConfig(SEED, 2500, 160, 45, 0.0, 0, 5000, 70)));
        ChunkGeneratorWorld gen = new ChunkGeneratorWorld(addon);
        RecordingChunkData r = generate(gen, Environment.NORMAL, SEED, 0, 0);
        // Any solid ground counts: the spawn island's surface follows its
        // biome, which need not be grass
        boolean landAboveSea = false;
        for (int y = settings.getSeaHeight() + 1; y < settings.getSeaHeight() + 50 && !landAboveSea; y++) {
            Material m = r.get(0, y, 0);
            landAboveSea = m != Material.AIR && m != Material.WATER;
        }
        assertTrue(landAboveSea, "The spawn island should rise above the sea at the origin");
    }

    @Test
    void testCarvedSeaFloorIsSealedBackUp() {
        // Playtest: vanilla's carvers cut dry craters straight through the sea
        // floor, because a generated chunk gets no block updates and nothing
        // ever flows in to fill them.
        ChunkGeneratorWorld gen = new ChunkGeneratorWorld(addon);
        RecordingChunkData r = new RecordingChunkData();
        ChunkData cd = chunkData(r);
        WorldInfo wi = worldInfo(Environment.NORMAL, SEED);
        gen.generateNoise(wi, new Random(SEED), 40, 40, cd);

        // Carve a crater the way a cave or ravine would: a column of air from
        // well under the floor up through the sea surface
        int carvedX = 7;
        int carvedZ = 9;
        int solidTop = floorTop(r, carvedX, carvedZ, settings.getSeaHeight());
        for (int y = solidTop - 20; y <= settings.getSeaHeight(); y++) {
            r.blocks.put(RecordingChunkData.key(carvedX, y, carvedZ), Material.AIR);
        }

        gen.generateCaves(wi, new Random(SEED), 40, 40, cd);

        // The sea is back: no air anywhere below the surface in that column
        for (int y = solidTop + 1; y <= settings.getSeaHeight(); y++) {
            assertEquals(Material.WATER, r.get(carvedX, y, carvedZ), "Open water missing at y=" + y);
        }
        // ... standing on a crust of floor, not a one-block roof over the void
        for (int y = solidTop - CRUST + 1; y <= solidTop; y++) {
            assertNotSame(Material.AIR, r.get(carvedX, y, carvedZ), "Sea floor still open at y=" + y);
        }
        // ... and the cave underneath survives: this is not a blanket infill
        assertEquals(Material.AIR, r.get(carvedX, solidTop - 20, carvedZ),
                "The cave under the floor should still be there");
    }

    @Test
    void testCaveMouthsInIslandFlanksAreLeftAlone() {
        // Above the waterline a cave opening is just a cave opening
        OceanEngine denseEngine = new OceanEngine(new OceanConfig(SEED, 2500, 160, 45, 1.0, 0, 5000, 70));
        when(addon.getOceanEngine(org.mockito.ArgumentMatchers.anyLong())).thenReturn(denseEngine);
        ChunkGeneratorWorld gen = new ChunkGeneratorWorld(addon);
        IslandSpec spec = denseEngine.islandInCell(0, 0).orElseThrow();
        RecordingChunkData r = new RecordingChunkData();
        ChunkData cd = chunkData(r);
        WorldInfo wi = worldInfo(Environment.NORMAL, SEED);
        gen.generateNoise(wi, new Random(SEED), spec.centerX() >> 4, spec.centerZ() >> 4, cd);

        int x = spec.centerX() & 15;
        int z = spec.centerZ() & 15;
        int top = floorTop(r, x, z, 320);
        assertTrue(top > settings.getSeaHeight(), "Expected island land at the center");
        int carved = top - 3;
        r.blocks.put(RecordingChunkData.key(x, carved, z), Material.AIR);

        gen.generateCaves(wi, new Random(SEED), spec.centerX() >> 4, spec.centerZ() >> 4, cd);
        assertEquals(Material.AIR, r.get(x, carved, z), "Dry land caves should be left alone");
    }

    @Test
    void testShouldGenerateFlags() {
        ChunkGeneratorWorld gen = new ChunkGeneratorWorld(addon);
        // Vanilla terrain off: land only ever comes from island masks
        assertFalse(gen.shouldGenerateNoise());
        assertFalse(gen.shouldGenerateSurface());
        // Everything else vanilla offers stays on - its carvers cut the caves
        // under the sea floor and its structures furnish the sea
        assertTrue(gen.shouldGenerateMobs());
        assertTrue(gen.shouldGenerateCaves());
        assertTrue(gen.shouldGenerateDecorations());
        assertTrue(gen.shouldGenerateStructures());
    }

    @Test
    void testStructuresAreKeptOffTradingIslands() {
        OceanEngine denseEngine = new OceanEngine(new OceanConfig(SEED, 2500, 160, 45, 1.0, 0, 5000, 70));
        when(addon.getOceanEngine(org.mockito.ArgumentMatchers.anyLong())).thenReturn(denseEngine);
        ChunkGeneratorWorld gen = new ChunkGeneratorWorld(addon);
        IslandSpec spec = denseEngine.islandInCell(0, 0).orElseThrow();
        WorldInfo wi = worldInfo(Environment.NORMAL, SEED);
        Random random = new Random(SEED);

        // No vanilla structure may generate on the island itself: a monument
        // through the market plaza would wreck the hand-built part of the world
        assertFalse(gen.shouldGenerateStructures(wi, random, spec.centerX() >> 4, spec.centerZ() >> 4));
        // ... but the open sea is fair game
        assertTrue(gen.shouldGenerateStructures(wi, random, (spec.centerX() + 2000) >> 4, spec.centerZ() >> 4));
        // The interstice never gets any
        assertFalse(gen.shouldGenerateStructures(worldInfo(Environment.NETHER, SEED), random, 100, 100));

        // Turning the guard off lets vanilla place them anywhere
        settings.setKeepStructuresOffIslands(false);
        assertTrue(gen.shouldGenerateStructures(wi, random, spec.centerX() >> 4, spec.centerZ() >> 4));
        // And turning structures off means none at all
        settings.setMakeStructures(false);
        assertFalse(gen.shouldGenerateStructures(wi, random, 500, 500));
    }
}
