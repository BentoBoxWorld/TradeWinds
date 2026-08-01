package world.bentobox.tradewinds.crime;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

/**
 * Headless tests of the reputation number line - no Bukkit, like the galaxy.
 *
 * @author tastybento
 */
class ReputationScaleTest {

    private final ReputationScale scale = ReputationScale.DEFAULT;

    @Test
    void testBandsInOrderAcrossTheWholeScale() {
        // Walking the scale from best to worst must pass through every band
        // exactly once, in order - a score that matched two bands would make
        // the world's reaction to a player undefined
        Standing previous = null;
        java.util.List<Standing> seen = new java.util.ArrayList<>();
        for (int score = scale.ceiling(); score >= scale.floor(); score--) {
            Standing standing = scale.standingOf(score);
            if (standing != previous) {
                assertFalse(seen.contains(standing), "Band " + standing + " reappeared at " + score);
                seen.add(standing);
                previous = standing;
            }
        }
        assertEquals(java.util.List.of(Standing.UPSTANDING, Standing.CLEAN, Standing.OFFENDER, Standing.WANTED,
                Standing.FUGITIVE), seen);
    }

    @Test
    void testDefaultThresholds() {
        assertEquals(Standing.UPSTANDING, scale.standingOf(250));
        assertEquals(Standing.CLEAN, scale.standingOf(249));
        assertEquals(Standing.CLEAN, scale.standingOf(0));
        assertEquals(Standing.OFFENDER, scale.standingOf(-1));
        assertEquals(Standing.OFFENDER, scale.standingOf(-199));
        assertEquals(Standing.WANTED, scale.standingOf(-200));
        assertEquals(Standing.FUGITIVE, scale.standingOf(-500));
        assertEquals(Standing.FUGITIVE, scale.standingOf(-100_000));
    }

    @Test
    void testOutOfOrderThresholdsAreRepaired() {
        // An admin who types the thresholds in the wrong order must still get a
        // usable scale, not a score that belongs to two bands
        ReputationScale mangled = new ReputationScale(-1000, 1000, -50, 0, 400, 900);
        assertTrue(mangled.upstanding() > mangled.offender());
        assertTrue(mangled.wanted() < mangled.offender());
        assertTrue(mangled.fugitive() < mangled.wanted());
        for (int score = 1000; score >= -1000; score -= 7) {
            // Every score still resolves to exactly one band
            assertTrue(mangled.standingOf(score) != null);
        }
    }

    @Test
    void testConsequencesFollowTheBand() {
        // The bands are only meaningful through what the world does about them
        assertFalse(Standing.CLEAN.isHunted());
        assertFalse(Standing.OFFENDER.isHunted());
        assertTrue(Standing.WANTED.isHunted());
        assertTrue(Standing.FUGITIVE.isHunted());
        // Killing a wanted player is lawful work - no penalty for the killer
        assertTrue(Standing.WANTED.isLawfulTarget());
        assertFalse(Standing.UPSTANDING.isLawfulTarget());
        // Only a fugitive is refused by the safe ports (spec principle 4)
        assertTrue(Standing.FUGITIVE.isBarredFromSafeTrade());
        assertFalse(Standing.WANTED.isBarredFromSafeTrade());
    }

    @Test
    void testDebtIsWhatAFineHasToBuy() {
        assertEquals(0, scale.debt(0));
        assertEquals(0, scale.debt(500));
        assertEquals(200, scale.debt(-200));
        assertEquals(1000, scale.debt(-1000));
    }

    @Test
    void testClampKeepsScoresOnTheScale() {
        assertEquals(1000, scale.clamp(99_999));
        assertEquals(-1000, scale.clamp(-99_999));
        assertEquals(-17, scale.clamp(-17));
    }

    @Test
    void testDecayAlwaysWalksTowardZeroAndStops() {
        // From either side, and never overshooting: a decay that sailed past
        // zero would turn a murderer upstanding by waiting
        assertEquals(-95, ReputationService.decayed(-100, 5));
        assertEquals(0, ReputationService.decayed(-3, 5));
        assertEquals(95, ReputationService.decayed(100, 5));
        assertEquals(0, ReputationService.decayed(3, 5));
        assertEquals(0, ReputationService.decayed(0, 5));
        // A negative step is treated as its magnitude, not as a way to grow
        assertEquals(-95, ReputationService.decayed(-100, -5));
    }

    @Test
    void testEveryCrimeCostsSomethingAndTheWorstCostMost() {
        for (Crime crime : Crime.values()) {
            assertTrue(crime.getDefaultPenalty() < 0, crime + " should cost reputation");
            assertTrue(crime.getDefaultBounty() >= 0, crime + " should not pay the offender");
        }
        assertTrue(Crime.KILL_INNOCENT.getDefaultPenalty() < Crime.KILL_VILLAGER.getDefaultPenalty());
        assertTrue(Crime.KILL_VILLAGER.getDefaultPenalty() < Crime.HURT_VILLAGER.getDefaultPenalty());
        // One murder must be enough to make you Wanted - the headline crime
        // cannot leave you merely an Offender
        assertEquals(Standing.WANTED, scale.standingOf(Crime.KILL_INNOCENT.getDefaultPenalty() * 2));
    }
}
