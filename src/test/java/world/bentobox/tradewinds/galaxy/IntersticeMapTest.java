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
        return new IntersticeMap(SEED, 256, 0.5, 9, 0.2, 1536, 0.6);
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
        IntersticeMap other = new IntersticeMap(SEED + 1, 256, 0.5, 9, 0.2, 1536, 0.6);
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
        IntersticeMap off = new IntersticeMap(SEED, 256, 0.0, 9, 0.2, 1536, 0.0);
        for (int cx = 0; cx < 50; cx++) {
            assertTrue(off.shoalInCell(cx, 0).isEmpty());
            assertTrue(off.towerInCell(cx, 0).isEmpty());
        }
        assertTrue(off.isOpenWater(12345, -6789));
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
