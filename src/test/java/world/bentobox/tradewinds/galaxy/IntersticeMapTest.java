package world.bentobox.tradewinds.galaxy;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.Optional;

import org.junit.jupiter.api.Test;

/**
 * Tests the interstice feature map: pure seeded geometry for wart shoals and
 * wither watchtowers - determinism, dome shape, and the open-water test that
 * keeps strandings out of the masonry.
 *
 * @author tastybento
 */
class IntersticeMapTest {

    private static final long SEED = 987654L;
    private static final int SEA = 70;

    private IntersticeMap map() {
        return new IntersticeMap(SEED, 256, 0.5, 9, 0.2, 1536, 0.6, 320, 0.4, 0.3);
    }

    private IntersticeMap.Shoal firstShoal(IntersticeMap map) {
        for (int cx = 0; cx < 50; cx++) {
            Optional<IntersticeMap.Shoal> shoal = map.shoalInCell(cx, 3);
            if (shoal.isPresent()) {
                return shoal.get();
            }
        }
        throw new IllegalStateException("No shoal in 50 cells");
    }

    @Test
    void testDeterminism() {
        IntersticeMap a = map();
        IntersticeMap b = map();
        for (int cx = -20; cx < 20; cx++) {
            assertEquals(a.shoalInCell(cx, cx), b.shoalInCell(cx, cx));
            assertEquals(a.towerInCell(cx, cx), b.towerInCell(cx, cx));
        }
        // A different seed disagrees somewhere
        IntersticeMap other = new IntersticeMap(SEED + 1, 256, 0.5, 9, 0.2, 1536, 0.6, 320, 0.4, 0.3);
        boolean differs = false;
        for (int cx = -50; cx < 50 && !differs; cx++) {
            differs = !a.shoalInCell(cx, 0).equals(other.shoalInCell(cx, 0));
        }
        assertTrue(differs, "Two seeds should not lay the same shoals");
    }

    @Test
    void testShoalDomeShape() {
        IntersticeMap map = map();
        IntersticeMap.Shoal shoal = firstShoal(map);
        // Crown: proud of the sea, plantable land
        int crown = map.shoalSurfaceAt(shoal.centerX(), shoal.centerZ(), SEA).orElseThrow();
        assertTrue(crown > SEA + 1, "Crown should be dry land, got " + crown);
        // Rim: the footprint's edge is submerged - wadable, not a cliff
        int rim = map.shoalSurfaceAt(shoal.centerX() + shoal.radius(), shoal.centerZ(), SEA).orElseThrow();
        assertTrue(rim <= SEA, "Rim should be under water, got " + rim);
        // Beyond the footprint: nothing
        assertTrue(map.shoalSurfaceAt(shoal.centerX() + shoal.radius() * 3, shoal.centerZ(), SEA).isEmpty());
    }

    @Test
    void testOpenWaterAvoidsFeatures() {
        IntersticeMap map = map();
        IntersticeMap.Shoal shoal = firstShoal(map);
        assertFalse(map.isOpenWater(shoal.centerX(), shoal.centerZ()),
                "A shoal crown is not open water");
        // Somewhere provably empty: a cell that rolled no shoal (nor its
        // neighbours), probed at its centre

        for (int cx = 0; cx < 50; cx++) {
            if (map.shoalInCell(cx, 7).isEmpty() && map.shoalInCell(cx - 1, 7).isEmpty()
                    && map.shoalInCell(cx + 1, 7).isEmpty() && map.shoalInCell(cx, 6).isEmpty()
                    && map.shoalInCell(cx, 8).isEmpty()) {
                int x = cx * 256 + 128;
                int z = 7 * 256 + 128;
                if (map.towersNear(x, z, 12).isEmpty()) {
                    assertTrue(map.isOpenWater(x, z), "Empty cells should be open water");
                    return;
                }
            }
        }
    }

    @Test
    void testZeroChanceDisables() {
        IntersticeMap off = new IntersticeMap(SEED, 256, 0.0, 9, 0.2, 1536, 0.0, 320, 0.0, 0.3);
        for (int cx = 0; cx < 50; cx++) {
            assertTrue(off.shoalInCell(cx, 0).isEmpty());
            assertTrue(off.towerInCell(cx, 0).isEmpty());
        }
        assertTrue(off.isOpenWater(12345, -6789));
    }

    @Test
    void testWrecksAreSeededAndWellFormed() {
        IntersticeMap a = map();
        IntersticeMap b = map();
        boolean found = false;
        for (int cx = -30; cx < 30; cx++) {
            var wreck = a.wreckInCell(cx, 5);
            assertEquals(wreck, b.wreckInCell(cx, 5), "Wrecks must be pure functions of the seed");
            if (wreck.isPresent()) {
                found = true;
                IntersticeMap.Wreck w = wreck.get();
                assertTrue(w.variant() >= 0, "Variant indexes a template list");
                assertTrue(w.rotation() >= 0 && w.rotation() <= 3, "Rotation is quarter-turns");
                assertTrue(w.sink() >= 0 && w.sink() <= 2, "Burial stays shallow");
                // Jitter never leaves the cell
                assertEquals(cx, Math.floorDiv(w.centerX(), 320));
            }
        }
        assertTrue(found, "No wreck in 60 cells at 0.4 chance - wrong seed?");
    }

    @Test
    void testWreckGradesAreWeightedTowardCommon() {
        IntersticeMap map = map();
        int common = 0;
        int treasure = 0;
        int total = 0;
        for (int cx = -100; cx < 100; cx++) {
            for (int cz = -10; cz < 10; cz++) {
                var wreck = map.wreckInCell(cx, cz);
                if (wreck.isPresent()) {
                    total++;
                    if (wreck.get().grade() == IntersticeMap.WreckGrade.COMMON) {
                        common++;
                    } else if (wreck.get().grade() == IntersticeMap.WreckGrade.TREASURE) {
                        treasure++;
                    }
                }
            }
        }
        assertTrue(total > 100, "Expected a real sample, got " + total);
        // 70/25/5 by design: ordinary cargo is the rule, the prize is rare
        assertTrue(common > total / 2, "COMMON should dominate: " + common + "/" + total);
        assertTrue(treasure < total / 10, "TREASURE should be rare: " + treasure + "/" + total);
        assertTrue(treasure > 0, "TREASURE should still exist in a real sample");
    }

    @Test
    void testWreckReefLiftsTheHullToTheSurface() {
        IntersticeMap map = map();
        IntersticeMap.Wreck wreck = null;
        for (int cx = 0; cx < 60 && wreck == null; cx++) {
            wreck = map.wreckInCell(cx, 9).orElse(null);
        }
        assertTrue(wreck != null, "No wreck in 60 cells");
        // The reef crests a few blocks down, so the hull perched on it rides
        // mostly submerged with its top works just breaking the surface
        int crest = map.wreckSurfaceAt(wreck.centerX(), wreck.centerZ(), SEA).orElseThrow();
        assertTrue(crest >= SEA - 8 && crest <= SEA - 6,
                "Crest should sit a hull-height under the surface, got " + crest);
        // The mound fades: beyond its radius the natural floor decides
        assertTrue(map.wreckSurfaceAt(wreck.centerX() + 60, wreck.centerZ(), SEA).isEmpty());
        // And a wreck site is never open water for strandings
        assertFalse(map.isOpenWater(wreck.centerX(), wreck.centerZ()));
    }

    @Test
    void testWreckLootIsRationed() {
        // Most wrecks are scenery: only the loot-chance fraction carry gold
        IntersticeMap none = new IntersticeMap(SEED, 256, 0.5, 9, 0.2, 1536, 0.6, 320, 0.5, 0.0);
        IntersticeMap all = new IntersticeMap(SEED, 256, 0.5, 9, 0.2, 1536, 0.6, 320, 0.5, 1.0);
        int seen = 0;
        for (int cx = -50; cx < 50; cx++) {
            var bare = none.wreckInCell(cx, 1);
            var rich = all.wreckInCell(cx, 1);
            assertEquals(bare.isPresent(), rich.isPresent(), "Loot chance must not move wrecks");
            if (bare.isPresent()) {
                seen++;
                assertFalse(bare.get().loot(), "Chance 0: every wreck is scenery");
                assertTrue(rich.get().loot(), "Chance 1: every wreck is stocked");
            }
        }
        assertTrue(seen > 10, "Expected a real sample, got " + seen);
    }

    @Test
    void testWreckZeroChanceDisablesTheGraveyard() {
        IntersticeMap off = new IntersticeMap(SEED, 256, 0.5, 9, 0.2, 1536, 0.6, 320, 0.0, 0.3);
        for (int cx = -50; cx < 50; cx++) {
            assertTrue(off.wreckInCell(cx, 0).isEmpty());
        }
        assertTrue(off.wrecksNear(0, 0, 10000).isEmpty());
    }

    @Test
    void testTowersExistAndSitInTheirCells() {
        IntersticeMap map = map();
        boolean found = false;
        for (int cx = 0; cx < 20 && !found; cx++) {
            var tower = map.towerInCell(cx, 2);
            if (tower.isPresent()) {
                found = true;
                // Jitter never leaves the cell
                assertEquals(cx, Math.floorDiv(tower.get().centerX(), 1536));
            }
        }
        assertTrue(found, "No tower in 20 cells at 0.6 chance - wrong seed?");
    }
}
