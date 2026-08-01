package world.bentobox.tradewinds.galaxy;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import org.junit.jupiter.api.Test;

/**
 * Headless tests of the seeded galaxy - no Bukkit anywhere (spec principle 5).
 *
 * @author tastybento
 */
class GalaxyEngineTest {

    private static final long SEED = 987654321L;

    private GalaxyConfig config(long seed, double density) {
        return new GalaxyConfig(seed, 2500, 160, 45, density, 5, 5000, 70);
    }

    private List<IslandSpec> islands(GalaxyEngine engine, int cellRange) {
        List<IslandSpec> list = new ArrayList<>();
        for (int cx = -cellRange; cx <= cellRange; cx++) {
            for (int cz = -cellRange; cz <= cellRange; cz++) {
                engine.islandInCell(cx, cz).ifPresent(list::add);
            }
        }
        return list;
    }

    @Test
    void testSameSeedSameGalaxy() {
        GalaxyEngine a = new GalaxyEngine(config(SEED, 0.5));
        GalaxyEngine b = new GalaxyEngine(config(SEED, 0.5));
        List<IslandSpec> islandsA = islands(a, 10);
        List<IslandSpec> islandsB = islands(b, 10);
        assertFalse(islandsA.isEmpty());
        // Positions, types, bands, biomes AND names all identical
        assertEquals(islandsA, islandsB);
    }

    @Test
    void testDifferentSeedDifferentGalaxy() {
        List<IslandSpec> islandsA = islands(new GalaxyEngine(config(SEED, 0.5)), 10);
        List<IslandSpec> islandsB = islands(new GalaxyEngine(config(SEED + 1, 0.5)), 10);
        assertNotEquals(islandsA, islandsB);
    }

    @Test
    void testQueryOrderIndependence() {
        GalaxyEngine a = new GalaxyEngine(config(SEED, 0.5));
        GalaxyEngine b = new GalaxyEngine(config(SEED, 0.5));
        // Query b in reverse order, and via a different entry point first
        b.landLiftAt(-31000, 17000);
        List<IslandSpec> reversed = new ArrayList<>();
        for (int cx = 10; cx >= -10; cx--) {
            for (int cz = 10; cz >= -10; cz--) {
                b.islandInCell(cx, cz).ifPresent(reversed::add);
            }
        }
        assertEquals(islands(a, 10).size(), reversed.size());
        assertTrue(islands(a, 10).containsAll(reversed));
    }

    @Test
    void testMinimumSeparation() {
        GalaxyEngine engine = new GalaxyEngine(config(SEED, 1.0)); // every cell occupied - worst case
        List<IslandSpec> list = islands(engine, 8);
        for (int i = 0; i < list.size(); i++) {
            for (int j = i + 1; j < list.size(); j++) {
                IslandSpec a = list.get(i);
                IslandSpec b = list.get(j);
                double dist = Math.sqrt(a.distanceSquared(b.centerX(), b.centerZ()));
                assertTrue(dist >= 2500,
                        "Islands too close: " + a.name() + " and " + b.name() + " at " + dist + " blocks");
            }
        }
    }

    @Test
    void testStarterClusterDensityFloor() {
        // Even with density 0, the starter cells host islands - and they are SAFE
        GalaxyEngine engine = new GalaxyEngine(config(SEED, 0.0));
        List<IslandSpec> list = islands(engine, 5);
        assertEquals(5, list.size(), "Starter density floor must guarantee the configured island count");
        list.forEach(s -> assertEquals(SecurityBand.SAFE, s.band(), s.name() + " must be SAFE"));
    }

    @Test
    void testBandsGetLawlessWithDistance() {
        GalaxyEngine engine = new GalaxyEngine(config(SEED, 1.0));
        // Far from spawn, islands must be lawless-side; near spawn, safe-side
        Optional<IslandSpec> far = engine.islandInCell(40, 40); // ~283km out
        assertTrue(far.isPresent());
        assertEquals(SecurityBand.ANARCHIC, far.get().band());
        Optional<IslandSpec> near = engine.islandInCell(0, 0);
        assertTrue(near.isPresent());
        assertTrue(near.get().band().ordinal() <= SecurityBand.POLICED.ordinal());
    }

    @Test
    void testLandLift() {
        GalaxyEngine engine = new GalaxyEngine(config(SEED, 1.0));
        IslandSpec spec = engine.islandInCell(2, 3).orElseThrow();
        // Near full lift at the center - hilliness varies it a little either way
        int center = engine.landLiftAt(spec.centerX(), spec.centerZ());
        assertTrue(center > 36 && center <= 52, "Center lift off: " + center);
        // Tapered on the flank
        int flank = engine.landLiftAt(spec.centerX() + 80, spec.centerZ());
        assertTrue(flank > 0 && flank < center, "Flank lift should taper: " + flank);
        // Zero well beyond the terrain radius, even allowing for a headland
        assertEquals(0, engine.landLiftAt(spec.centerX() + 250, spec.centerZ()));
        // Zero in open ocean (empty cell far out with density check impossible at 1.0 -> use ocean point between islands)
        assertEquals(0, new GalaxyEngine(config(SEED, 0.0)).landLiftAt(1_000_000, 1_000_000));
    }

    @Test
    void testIslandAtAndBiomeKey() {
        GalaxyEngine engine = new GalaxyEngine(config(SEED, 1.0));
        IslandSpec spec = engine.islandInCell(-4, 6).orElseThrow();
        assertEquals(spec, engine.islandAt(spec.centerX(), spec.centerZ()).orElseThrow());
        assertTrue(engine.islandAt(spec.centerX() + 500, spec.centerZ() + 500).isEmpty());
        assertEquals(spec.biomeKey(), engine.biomeKeyAt(spec.centerX(), spec.centerZ()).orElseThrow());
        assertTrue(spec.type().getBiomeKeys().contains(spec.biomeKey()));
    }

    @Test
    void testFrozenApproachRing() {
        GalaxyEngine engine = new GalaxyEngine(config(SEED, 1.0));
        // Find a FROZEN island
        IslandSpec frozen = islands(engine, 15).stream().filter(s -> s.type() == IslandType.FROZEN).findFirst()
                .orElseThrow();
        // Just outside the terrain radius but inside 2x: frozen ocean
        assertEquals(Optional.of("minecraft:frozen_ocean"),
                engine.biomeKeyAt(frozen.centerX() + 200, frozen.centerZ()));
    }

    @Test
    void testDockPlanGeometry() {
        GalaxyEngine engine = new GalaxyEngine(config(SEED, 1.0));
        IslandSpec spec = engine.islandInCell(3, -2).orElseThrow();
        DockPlan plan = engine.dockPlan(spec);
        // Deterministic
        assertEquals(plan, engine.dockPlan(spec));
        assertEquals(plan, new GalaxyEngine(config(SEED, 1.0)).dockPlan(spec));
        // Plaza sits inside the island's terrain footprint
        double plazaDist = Math.sqrt(spec.distanceSquared(plan.plazaX(), plan.plazaZ()));
        assertTrue(plazaDist < 160, "Plaza outside terrain: " + plazaDist);
        // Quay ends inside the terrain radius but beyond the plaza
        assertTrue(plan.dockEnd() > plazaDist && plan.dockEnd() < 160);
    }

    @Test
    void testColumnPlans() {
        GalaxyEngine engine = new GalaxyEngine(config(SEED, 1.0));
        IslandSpec spec = engine.islandInCell(1, 1).orElseThrow();
        DockPlan plan = engine.dockPlan(spec);
        // Plaza center: fully flattened at sea level + PLAZA_RISE
        ColumnPlan plaza = engine.columnPlanAt(plan.plazaX(), plan.plazaZ()).orElseThrow();
        assertEquals(ColumnPlan.Feature.PLAZA, plaza.feature());
        assertEquals(70 + GalaxyEngine.PLAZA_RISE, plaza.surfaceY());
        assertEquals(1.0, plaza.blend());
        assertEquals(spec, plaza.island());
        // Seaward end of the quay: DOCK at sea level + DOCK_RISE
        int dockX = spec.centerX() + (int) Math.round(Math.cos(plan.bearing()) * (plan.dockEnd() - 2));
        int dockZ = spec.centerZ() + (int) Math.round(Math.sin(plan.bearing()) * (plan.dockEnd() - 2));
        ColumnPlan dock = engine.columnPlanAt(dockX, dockZ).orElseThrow();
        assertEquals(ColumnPlan.Feature.DOCK, dock.feature());
        assertEquals(70 + GalaxyEngine.DOCK_RISE, dock.surfaceY());
        // Island center is natural terrain (no feature)
        assertTrue(engine.columnPlanAt(spec.centerX(), spec.centerZ()).isEmpty());
        // Open ocean has no plans
        assertTrue(new GalaxyEngine(config(SEED, 0.0)).columnPlanAt(500_000, 500_000).isEmpty());
    }

    @Test
    void testWalkwayIsContinuousFromPlazaToPierEnd() {
        // Playtest regression: the quay used to start ~10 blocks offshore because
        // the plaza blend ring beat the dock strip. Walking the dock axis from the
        // plaza center to the pier end must never leave planned ground.
        GalaxyEngine engine = new GalaxyEngine(config(SEED, 1.0));
        for (int cell = 0; cell < 12; cell++) {
            IslandSpec spec = engine.islandInCell(cell, -cell - 1).orElseThrow();
            DockPlan plan = engine.dockPlan(spec);
            int plazaDist = (int) Math.round(Math.sqrt(spec.distanceSquared(plan.plazaX(), plan.plazaZ())));
            for (int d = plazaDist; d <= plan.dockEnd() - 1; d++) {
                int x = spec.centerX() + (int) Math.round(Math.cos(plan.bearing()) * d);
                int z = spec.centerZ() + (int) Math.round(Math.sin(plan.bearing()) * d);
                ColumnPlan plaza = engine.columnPlanAt(x, z).orElse(null);
                assertTrue(plaza != null && (plaza.blend() >= 1.0),
                        spec.name() + ": walkway hole at distance " + d + " (" + plaza + ")");
            }
        }
    }

    @Test
    void testTypeWeightOverrides() {
        // Only LUXURY weighted -> every island is LUXURY
        GalaxyConfig cfg = new GalaxyConfig(SEED, 2500, 160, 45, 1.0, 5, 5000, 70,
                java.util.Map.of(IslandType.LUXURY, 1));
        islands(new GalaxyEngine(cfg), 5).forEach(s -> assertEquals(IslandType.LUXURY, s.type()));
        // A zero/empty weight table falls back to the built-in defaults
        GalaxyConfig broken = new GalaxyConfig(SEED, 2500, 160, 45, 1.0, 5, 5000, 70, java.util.Map.of());
        assertEquals(GalaxyConfig.defaultTypeWeights(), broken.typeWeights());
        // And the default-weights galaxy is unchanged by the new parameter
        assertEquals(islands(new GalaxyEngine(config(SEED, 1.0)), 5),
                islands(new GalaxyEngine(new GalaxyConfig(SEED, 2500, 160, 45, 1.0, 5, 5000, 70,
                        GalaxyConfig.defaultTypeWeights())), 5));
    }

    @Test
    void testSpawnIslandAtOrigin() {
        // The origin cell is reserved: a full SAFE trading island at exactly 0,0
        GalaxyEngine engine = new GalaxyEngine(config(SEED, 0.0));
        IslandSpec spawn = engine.spawnIsland();
        assertEquals(0, spawn.centerX());
        assertEquals(0, spawn.centerZ());
        assertEquals(SecurityBand.SAFE, spawn.band());
        assertEquals(GalaxyEngine.SPAWN_NAME, spawn.name());
        // It has land and a dock like any trading island
        assertEquals(45, engine.landLiftAt(0, 0));
        assertEquals(spawn, engine.islandAt(0, 0).orElseThrow());
        assertTrue(engine.dockPlan(spawn).dockEnd() > 0);
        // Its economy is configurable
        GalaxyEngine industrial = new GalaxyEngine(new GalaxyConfig(SEED, 2500, 160, 45, 0.0, 5, 5000, 70,
                GalaxyConfig.defaultTypeWeights(), IslandType.INDUSTRIAL));
        assertEquals(IslandType.INDUSTRIAL, industrial.spawnIsland().type());
    }

    @Test
    void testNothingCrowdsTheSpawnIsland() {
        // Every other island keeps min separation from the origin
        GalaxyEngine engine = new GalaxyEngine(config(SEED, 1.0));
        for (IslandSpec spec : islands(engine, 6)) {
            if (spec.cellX() == 0 && spec.cellZ() == 0) {
                continue;
            }
            double distance = Math.hypot(spec.centerX(), spec.centerZ());
            assertTrue(distance >= 2500, spec.name() + " crowds spawn at " + distance);
        }
    }

    @Test
    void testWildIslets() {
        GalaxyEngine engine = new GalaxyEngine(config(SEED, 0.0));
        // With density 0 almost every cell is empty: most roll wild islets
        int found = 0;
        Islet sample = null;
        for (int cx = -10; cx <= 10; cx++) {
            for (int cz = -10; cz <= 10; cz++) {
                if (engine.islandInCell(cx, cz).isEmpty() && engine.wildIsletInCell(cx, cz).isPresent()) {
                    found++;
                    sample = engine.wildIsletInCell(cx, cz).get();
                }
            }
        }
        // ~55% of 441 cells
        assertTrue(found > 180 && found < 320, "Wild islet count off: " + found);
        // Deterministic across engines
        GalaxyEngine again = new GalaxyEngine(config(SEED, 0.0));
        assertEquals(sample, again.isletAt(sample.centerX(), sample.centerZ()).orElseThrow());
        // Terrain rises there, with a vanilla wild biome
        assertTrue(engine.landLiftAt(sample.centerX(), sample.centerZ()) > 20);
        assertTrue(engine.biomeKeyAt(sample.centerX(), sample.centerZ()).orElseThrow().startsWith("minecraft:"));
        // Islets keep well clear of trading islands (terrain + islet + margin)
        GalaxyEngine dense = new GalaxyEngine(config(SEED, 1.0));
        for (int cx = -20; cx <= 20; cx++) {
            for (int cz = -20; cz <= 20; cz++) {
                Optional<Islet> islet = dense.wildIsletInCell(cx, cz);
                if (islet.isEmpty()) {
                    continue;
                }
                Islet i = islet.get();
                for (IslandSpec spec : dense.islandsNear(i.centerX(), i.centerZ(), 2000)) {
                    double d = Math.sqrt(spec.distanceSquared(i.centerX(), i.centerZ()));
                    assertTrue(d >= 160 + i.radius(),
                            "Islet at " + i.centerX() + "," + i.centerZ() + " crowds " + spec.name());
                }
            }
        }
    }

    @Test
    void testIsletsVaryInSize() {
        // Sandbars and proper little islands, not one stamped shape
        GalaxyEngine engine = new GalaxyEngine(config(SEED, 0.0));
        int smallest = Integer.MAX_VALUE;
        int largest = 0;
        for (int cx = -12; cx <= 12; cx++) {
            for (int cz = -12; cz <= 12; cz++) {
                Optional<Islet> islet = engine.wildIsletInCell(cx, cz);
                if (islet.isPresent()) {
                    smallest = Math.min(smallest, islet.get().radius());
                    largest = Math.max(largest, islet.get().radius());
                }
            }
        }
        assertTrue(largest > smallest * 1.8, "Islets are all much the same size: " + smallest + ".." + largest);
    }

    @Test
    void testMushroomIsletsAreRareButReal() {
        GalaxyEngine engine = new GalaxyEngine(config(SEED, 0.0));
        int islets = 0;
        int mushroom = 0;
        for (int cx = -25; cx <= 25; cx++) {
            for (int cz = -25; cz <= 25; cz++) {
                Optional<Islet> islet = engine.wildIsletInCell(cx, cz);
                if (islet.isPresent()) {
                    islets++;
                    if (islet.get().isMushroom()) {
                        mushroom++;
                        // A mushroom island is mycelium all over, with no beach
                        assertEquals(SurfaceKind.MYCELIUM,
                                engine.surfaceKindAt(islet.get().centerX(), islet.get().centerZ()));
                        assertEquals(GalaxyEngine.MUSHROOM_BIOME,
                                engine.biomeKeyAt(islet.get().centerX(), islet.get().centerZ()).orElseThrow());
                    }
                }
            }
        }
        assertTrue(mushroom > 0, "No mushroom islets in " + islets + " islets");
        assertTrue(mushroom < islets * 0.2, "Mushroom islets are meant to be rare: " + mushroom + "/" + islets);
    }

    @Test
    void testIsletsHaveSandyShores() {
        // The waterline is sand and beach biome - that is what lets vanilla
        // wash up beached shipwrecks and bury treasure there
        GalaxyEngine engine = new GalaxyEngine(config(SEED, 0.0));
        Islet islet = null;
        for (int cx = 0; cx <= 20 && islet == null; cx++) {
            for (int cz = 0; cz <= 20 && islet == null; cz++) {
                islet = engine.wildIsletInCell(cx, cz).filter(i -> !i.isMushroom()).orElse(null);
            }
        }
        assertTrue(islet != null, "No ordinary islet found");
        // Inland is grass, well above the water
        assertEquals(SurfaceKind.GRASS, engine.surfaceKindAt(islet.centerX(), islet.centerZ()));
        assertTrue(engine.surfaceHeightAt(islet.centerX(), islet.centerZ()) > 70 + 4);
        // Walking out to sea, the last land before the water is sand and beach
        // biome - found by the real waterline, so it tracks a ragged coast
        int sand = 0;
        int beach = 0;
        for (int d = 1; d < islet.radius() * 2; d++) {
            int x = islet.centerX() + d;
            if (!engine.isShoreAt(x, islet.centerZ())) {
                continue;
            }
            if (engine.surfaceKindAt(x, islet.centerZ()) == SurfaceKind.SAND) {
                sand++;
            }
            if (engine.biomeKeyAt(x, islet.centerZ()).orElseThrow().contains("beach")) {
                beach++;
            }
        }
        assertTrue(sand > 0, "No sandy shore on the islet");
        assertEquals(sand, beach, "Every shore column should carry a beach biome");
    }

    @Test
    void testCoastlinesAreNotCircles() {
        // Playtest: "almost comically circular". A cosine mask on true distance
        // draws a perfect disc; the distance is warped before the mask sees it.
        GalaxyEngine engine = new GalaxyEngine(config(SEED, 0.0));
        Islet islet = null;
        for (int cx = 0; cx <= 20 && islet == null; cx++) {
            for (int cz = 0; cz <= 20 && islet == null; cz++) {
                islet = engine.wildIsletInCell(cx, cz).orElse(null);
            }
        }
        assertTrue(islet != null, "No islet found");
        // Walk the compass, recording how far the land reaches on each bearing
        List<Integer> reach = new ArrayList<>();
        for (int deg = 0; deg < 360; deg += 5) {
            double rad = Math.toRadians(deg);
            int last = 0;
            for (int d = 1; d < islet.radius() * 2; d++) {
                int x = islet.centerX() + (int) Math.round(Math.cos(rad) * d);
                int z = islet.centerZ() + (int) Math.round(Math.sin(rad) * d);
                if (engine.surfaceHeightAt(x, z) > 70) {
                    last = d;
                }
            }
            reach.add(last);
        }
        int min = reach.stream().mapToInt(Integer::intValue).min().orElseThrow();
        int max = reach.stream().mapToInt(Integer::intValue).max().orElseThrow();
        // Bays and headlands: the coast must be materially further out on some
        // bearings than others
        assertTrue(max - min > islet.radius() * 0.25,
                "Coastline is near circular: reach " + min + " to " + max + " (r=" + islet.radius() + ")");
        // ... but it is still one island, not a scatter of fragments
        assertTrue(min > 0, "The island broke up: some bearing has no land at all");
    }

    @Test
    void testRoundShapeConfigRestoresPerfectCircles() {
        // The knob that turns it all off again
        GalaxyConfig round = new GalaxyConfig(SEED, 2500, 160, 45, 1.0, 5, 5000, 70,
                GalaxyConfig.defaultTypeWeights(), null, 0.0, 70, 1200, 0.0, SeabedConfig.flat(20),
                ShapeConfig.ROUND);
        GalaxyEngine engine = new GalaxyEngine(round);
        IslandSpec spec = engine.islandInCell(1, 1).orElseThrow();
        int first = engine.landLiftAt(spec.centerX() + 100, spec.centerZ());
        // Same distance, every bearing, identical lift
        for (int deg = 0; deg < 360; deg += 15) {
            double rad = Math.toRadians(deg);
            int x = spec.centerX() + (int) Math.round(Math.cos(rad) * 100);
            int z = spec.centerZ() + (int) Math.round(Math.sin(rad) * 100);
            assertEquals(first, engine.landLiftAt(x, z), 1.0);
        }
    }

    @Test
    void testIsletsAreFindable() {
        // The open sea must not be empty: an islet within a short row of
        // anywhere (playtest: 6000x6000 blocks of nothing at 10000,10000, and
        // nothing found anywhere in 130 explored regions)
        GalaxyEngine engine = new GalaxyEngine(config(SEED, 0.5));
        for (int[] point : new int[][] { { 10000, 10000 }, { -5000, 15000 }, { 30000, -20000 },
                { -120000, 90000 } }) {
            double nearest = Double.MAX_VALUE;
            int grid = GalaxyConfig.DEFAULT_WILD_GRID;
            int cx = Math.floorDiv(point[0], grid);
            int cz = Math.floorDiv(point[1], grid);
            for (int i = cx - 4; i <= cx + 4; i++) {
                for (int j = cz - 4; j <= cz + 4; j++) {
                    Optional<Islet> islet = engine.wildIsletInCell(i, j);
                    if (islet.isPresent()) {
                        nearest = Math.min(nearest,
                                Math.hypot(islet.get().centerX() - point[0], islet.get().centerZ() - point[1]));
                    }
                }
            }
            assertTrue(nearest < 1200, "Nearest islet to " + point[0] + "," + point[1] + " is " + nearest);
        }
    }

    @Test
    void testOceanBiomesVaryButNeverJump() {
        GalaxyEngine engine = new GalaxyEngine(config(SEED, 0.5));
        java.util.List<String> shallow = GalaxyEngine.shallowOceanBiomes();
        java.util.List<String> deep = GalaxyEngine.deepOceanBiomes();
        java.util.Set<String> seen = new java.util.HashSet<>();
        int previous = -1;
        // Sail a long line, sampling every 50 blocks
        for (int x = -40_000; x <= 40_000; x += 50) {
            String key = engine.oceanBiomeKeyAt(x, 12_345);
            int index = engine.oceanTemperatureIndex(x, 12_345);
            // Depth decides shallow or deep; temperature decides which of each
            assertEquals(engine.isDeepWater(x, 12_345) ? deep.get(index) : shallow.get(index), key);
            seen.add(key);
            if (previous >= 0) {
                assertTrue(Math.abs(index - previous) <= 1,
                        "Ocean temperature jumped by more than one step at x=" + x);
            }
            previous = index;
        }
        // The voyage crosses genuinely different water
        assertTrue(seen.size() >= 3, "Ocean is too uniform: only " + seen);
    }

    @Test
    void testDeepWaterExistsForMonuments() {
        // Ocean monuments only generate in the deep ocean biomes, so if the sea
        // never gets deep the whole vanilla structure set is unreachable
        GalaxyEngine engine = new GalaxyEngine(config(SEED, 0.0));
        int deep = 0;
        int total = 0;
        for (int x = -30_000; x <= 30_000; x += 250) {
            for (int z = -2_000; z <= 2_000; z += 250) {
                if (engine.isletAt(x, z).isPresent()) {
                    continue;
                }
                total++;
                if (engine.isDeepWater(x, z)) {
                    deep++;
                    assertTrue(engine.oceanBiomeKeyAt(x, z).startsWith("minecraft:deep_"));
                }
            }
        }
        // Deep basins are a real feature of the map, not a rounding error, and
        // not so much of it that the shallows disappear
        assertTrue(deep > total * 0.05, "Hardly any deep water: " + deep + "/" + total);
        assertTrue(deep < total * 0.75, "Almost everything is deep water: " + deep + "/" + total);
    }

    @Test
    void testSeabedVariesAndNeverBreaksTheSurface() {
        GalaxyEngine engine = new GalaxyEngine(config(SEED, 0.0));
        int shallowest = Integer.MIN_VALUE;
        int deepest = Integer.MAX_VALUE;
        for (int x = -20_000; x <= 20_000; x += 137) {
            for (int z = -600; z <= 600; z += 137) {
                int y = engine.seabedHeightAt(x, z);
                // Open water everywhere: the galaxy's islands are the only land
                assertTrue(y < 70, "Sea floor broke the surface at " + x + "," + z + " (y=" + y + ")");
                shallowest = Math.max(shallowest, y);
                deepest = Math.min(deepest, y);
            }
        }
        // Sunlit banks and dark basins, not one flat plain
        assertTrue(shallowest - deepest > 30,
                "Sea floor is too flat: y " + deepest + " to " + shallowest);
    }

    @Test
    void testRiftsCutDeepNarrowCanyons() {
        Seabed seabed = new GalaxyEngine(config(SEED, 0.0)).getSeabed();
        int cut = 0;
        int deepCut = 0;
        int samples = 0;
        for (int x = -20_000; x <= 20_000; x += 53) {
            for (int z = -300; z <= 300; z += 53) {
                samples++;
                double rift = seabed.riftCut(x, z);
                if (rift > 0) {
                    cut++;
                }
                if (rift > SeabedConfig.DEFAULT.riftDepth() * 0.5) {
                    deepCut++;
                }
            }
        }
        assertTrue(deepCut > 0, "No rift ever cuts deep");
        // Narrow: canyons are a feature of the floor, not most of it
        assertTrue(cut < samples * 0.3, "Rifts are everywhere: " + cut + "/" + samples);
    }

    @Test
    void testIslandsAlwaysStandOnTheirOwnShelf() {
        // An island that happens to fall over an abyssal plain must still break
        // the surface by the same amount as one over a shelf
        GalaxyEngine engine = new GalaxyEngine(config(SEED, 1.0));
        int shelf = SeabedConfig.DEFAULT.islandShelfDepth();
        for (int cx = -3; cx <= 3; cx++) {
            for (int cz = -3; cz <= 3; cz++) {
                IslandSpec spec = engine.islandInCell(cx, cz).orElse(null);
                if (spec == null) {
                    continue;
                }
                assertEquals(1.0, engine.shelfBlendAt(spec.centerX(), spec.centerZ()), 1e-9);
                // The basin is levelled to the island shelf; the rolling relief
                // carries on across it at half strength, so this is a band, not
                // a number - a dead flat shelf is what made islands look stamped
                int bed = engine.seabedHeightAt(spec.centerX(), spec.centerZ());
                int slack = SeabedConfig.DEFAULT.relief() / 2 + 1;
                assertTrue(Math.abs(bed - (70 - shelf)) <= slack, "Island shelf drifted to y" + bed);
                // ... and its shallows are shallows, whatever is underneath
                assertFalse(engine.isDeepWater(spec.centerX(), spec.centerZ()));
                // ... so the island still clears the waves
                assertTrue(engine.surfaceHeightAt(spec.centerX(), spec.centerZ()) > 70,
                        "Island " + spec.name() + " failed to break the surface");
            }
        }
    }

    @Test
    void testFlatSeabedConfigRestoresTheOldOcean() {
        GalaxyConfig flat = new GalaxyConfig(SEED, 2500, 160, 45, 0.0, 5, 5000, 70,
                GalaxyConfig.defaultTypeWeights(), null, 0.0, 70, 1200, 0.0, SeabedConfig.flat(20));
        GalaxyEngine engine = new GalaxyEngine(flat);
        for (int x = -5_000; x <= 5_000; x += 311) {
            assertEquals(50, engine.seabedHeightAt(x, 700));
            // A floor with no basins has no deep water, so no monuments
            assertFalse(engine.isDeepWater(x, 700));
        }
    }

    @Test
    void testOceanBiomesAreSeeded() {
        GalaxyEngine a = new GalaxyEngine(config(SEED, 0.5));
        GalaxyEngine b = new GalaxyEngine(config(SEED, 0.5));
        GalaxyEngine other = new GalaxyEngine(config(SEED + 1, 0.5));
        boolean differs = false;
        for (int x = 0; x < 20_000; x += 500) {
            assertEquals(a.oceanBiomeKeyAt(x, 0), b.oceanBiomeKeyAt(x, 0));
            differs |= !a.oceanBiomeKeyAt(x, 0).equals(other.oceanBiomeKeyAt(x, 0));
        }
        assertTrue(differs, "A different seed should give a different sea");
    }

    @Test
    void testNamesAreDistinctEnough() {
        GalaxyEngine engine = new GalaxyEngine(config(SEED, 1.0));
        List<IslandSpec> list = islands(engine, 7);
        long distinct = list.stream().map(IslandSpec::name).distinct().count();
        // Names are not globally unique by design, but collisions must be rare
        assertTrue(distinct > list.size() * 0.8,
                "Too many name collisions: " + distinct + " distinct of " + list.size());
    }
}
