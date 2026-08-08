package world.bentobox.tradewinds.ocean;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

/**
 * The dune field (2026-08-07): the 220-block relief swells read as dead flat
 * from a boat, so short dunes ride on top - and they must be OFF by default,
 * or every pre-dune world's floor would shift under its generated chunks.
 *
 * @author tastybento
 */
class SeabedTest {

    private static final long SEED = 424242L;
    private static final int SEA = 70;

    @Test
    void testDunesMakeTheFloorRollWithinAGlance() {
        Seabed duned = new Seabed(SEED, SEA, new SeabedConfig(6, 34, 10, 10, 16, 0.82, 12, 4));
        // A 64-block transect - about what one glance down from a boat covers
        int min = Integer.MAX_VALUE;
        int max = Integer.MIN_VALUE;
        for (int x = 0; x < 64; x++) {
            int y = duned.heightAt(x, 9000, 0);
            min = Math.min(min, y);
            max = Math.max(max, y);
        }
        assertTrue(max - min >= 3,
                "A duned floor should visibly roll within 64 blocks, spread was " + (max - min));
    }

    @Test
    void testZeroDunesKeepsTheOldFloorExactly() {
        // The 7-arg form is the pre-dune shape: existing worlds must not shift
        Seabed old = new Seabed(SEED, SEA, new SeabedConfig(14, 46, 18, 9, 26, 0.80, 20));
        Seabed explicit = new Seabed(SEED, SEA, new SeabedConfig(14, 46, 18, 9, 26, 0.80, 20, 0));
        for (int x = -200; x < 200; x += 7) {
            assertEquals(old.heightAt(x, x * 3, 0), explicit.heightAt(x, x * 3, 0),
                    "duneHeight 0 must be byte-identical to the pre-dune floor");
        }
    }
}
