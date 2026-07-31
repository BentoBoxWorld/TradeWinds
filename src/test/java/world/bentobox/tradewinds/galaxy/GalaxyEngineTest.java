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
        // Full lift at the center
        assertEquals(45, engine.landLiftAt(spec.centerX(), spec.centerZ()));
        // Tapered on the flank
        int flank = engine.landLiftAt(spec.centerX() + 80, spec.centerZ());
        assertTrue(flank > 0 && flank < 45, "Flank lift should taper: " + flank);
        // Zero beyond the terrain radius
        assertEquals(0, engine.landLiftAt(spec.centerX() + 161, spec.centerZ()));
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
        // With density 0 almost every cell is empty: some roll wild islets
        int found = 0;
        int[] sample = null;
        for (int cx = -10; cx <= 10; cx++) {
            for (int cz = -10; cz <= 10; cz++) {
                if (engine.islandInCell(cx, cz).isEmpty() && engine.wildIsletInCell(cx, cz).isPresent()) {
                    found++;
                    sample = engine.wildIsletInCell(cx, cz).get();
                }
            }
        }
        // ~30% of 441 cells
        assertTrue(found > 60 && found < 200, "Wild islet count off: " + found);
        // Deterministic across engines
        GalaxyEngine again = new GalaxyEngine(config(SEED, 0.0));
        assertTrue(again.wildIsletAt(sample[0], sample[1]).isPresent());
        // Terrain rises there, with a vanilla wild biome
        assertEquals(45, engine.landLiftAt(sample[0], sample[1]));
        assertTrue(engine.biomeKeyAt(sample[0], sample[1]).orElseThrow().startsWith("minecraft:"));
        // Never inside a trading island's cell
        GalaxyEngine dense = new GalaxyEngine(config(SEED, 1.0));
        for (int cx = -5; cx <= 5; cx++) {
            for (int cz = -5; cz <= 5; cz++) {
                if (dense.islandInCell(cx, cz).isPresent()) {
                    assertTrue(dense.wildIsletInCell(cx, cz).isEmpty(),
                            "Cell with a trading island must not also host a wild islet");
                }
            }
        }
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
