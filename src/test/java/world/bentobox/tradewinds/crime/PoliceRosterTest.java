package world.bentobox.tradewinds.crime;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.Test;

import world.bentobox.tradewinds.ocean.SecurityBand;

/**
 * Headless tests of who the law sends and when it gives up.
 *
 * @author tastybento
 */
class PoliceRosterTest {

    /** The configured default patrol sizes. */
    private static final Map<SecurityBand, Integer> SIZES = Map.of(SecurityBand.SAFE, 4, SecurityBand.POLICED, 3,
            SecurityBand.FRONTIER, 2, SecurityBand.LAWLESS, 1, SecurityBand.ANARCHIC, 0);

    @Test
    void testLawlessSpaceSendsNobody() {
        // The bands only mean something if the far ones are genuinely unpoliced
        assertTrue(PoliceRoster.forWanted(0, false, false).isEmpty());
        assertTrue(PoliceRoster.forCustoms(0).isEmpty());
    }

    @Test
    void testResponseShrinksAsTheLawThinsOut() {
        int previous = Integer.MAX_VALUE;
        for (SecurityBand band : SecurityBand.values()) {
            int size = PoliceRoster.forWanted(SIZES.get(band), false, false).size();
            assertTrue(size <= previous, "Response grew at " + band);
            previous = size;
        }
        assertTrue(PoliceRoster.forWanted(4, false, false).size() > PoliceRoster
                .forWanted(1, false, false).size());
    }

    @Test
    void testOnlyPhantomsCanFollowABoat() {
        // Each unit denies a different escape. A sea response with no phantom
        // would be a response a player simply rows away from.
        List<PoliceUnit> atSea = PoliceRoster.forWanted(4, false, false);
        assertTrue(atSea.contains(PoliceUnit.PHANTOM), "A sea response must be able to pursue: " + atSea);
        assertTrue(atSea.contains(PoliceUnit.GUARDIAN), "Guardians hold the water: " + atSea);
        assertFalse(atSea.contains(PoliceUnit.GOLEM), "A golem cannot swim after a boat: " + atSea);
    }

    @Test
    void testAshoreTheGolemsTakeOver() {
        List<PoliceUnit> ashore = PoliceRoster.forWanted(4, true, false);
        assertTrue(ashore.contains(PoliceUnit.GOLEM), "Golems take anyone who lands: " + ashore);
        // ... with air support, so bolting for the boat is not free
        assertTrue(ashore.contains(PoliceUnit.PHANTOM), "Ashore still needs pursuit: " + ashore);
        assertFalse(ashore.contains(PoliceUnit.GUARDIAN), "A guardian on a plaza is an ornament: " + ashore);
    }

    @Test
    void testAFugitiveDrawsMore() {
        int wanted = PoliceRoster.forWanted(3, false, false).size();
        int fugitive = PoliceRoster.forWanted(3, false, true).size();
        assertTrue(fugitive > wanted, "Shoot on sight should mean more of them");
    }

    @Test
    void testCustomsAlwaysAnswerFromTheSea() {
        // Customs launch from a harbour at a border crossing, so the roster is
        // never a land one however the roll came up
        List<PoliceUnit> customs = PoliceRoster.forCustoms(4);
        assertEquals(4, customs.size());
        assertFalse(customs.contains(PoliceUnit.GOLEM));
        assertTrue(customs.contains(PoliceUnit.GUARDIAN));
        assertTrue(customs.contains(PoliceUnit.DROWNED));
        // And something that can actually pursue. Launching from the dock puts
        // the swimmers a long way behind a boat, so without a phantom "run"
        // stops being a choice and becomes the answer every time.
        assertTrue(customs.contains(PoliceUnit.PHANTOM), "A customs patrol needs a pursuer: " + customs);
    }

    @Test
    void testPursuitBreaksOffAtTheBorder() {
        int range = 400;
        int breakOff = 400;
        // Inside the protection zone the law follows
        assertFalse(PoliceRoster.shouldBreakOff(0, range, breakOff));
        assertFalse(PoliceRoster.shouldBreakOff(399, range, breakOff));
        // Just past the border it still follows a way
        assertFalse(PoliceRoster.shouldBreakOff(700, range, breakOff));
        // Beyond that it gives up: lawless water has to stay lawless, or the
        // security bands flatten into one difficulty everywhere
        assertTrue(PoliceRoster.shouldBreakOff(801, range, breakOff));
        assertTrue(PoliceRoster.shouldBreakOff(10_000, range, breakOff));
    }
}
