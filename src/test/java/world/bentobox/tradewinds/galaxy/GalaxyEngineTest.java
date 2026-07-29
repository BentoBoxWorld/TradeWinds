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
        return new GalaxyConfig(seed, 2500, 160, 45, density, 5, 5000);
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
    void testNamesAreDistinctEnough() {
        GalaxyEngine engine = new GalaxyEngine(config(SEED, 1.0));
        List<IslandSpec> list = islands(engine, 7);
        long distinct = list.stream().map(IslandSpec::name).distinct().count();
        // Names are not globally unique by design, but collisions must be rare
        assertTrue(distinct > list.size() * 0.8,
                "Too many name collisions: " + distinct + " distinct of " + list.size());
    }
}
