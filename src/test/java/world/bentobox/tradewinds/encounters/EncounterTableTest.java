package world.bentobox.tradewinds.encounters;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.HashSet;
import java.util.Optional;
import java.util.Set;

import org.junit.jupiter.api.Test;

import world.bentobox.tradewinds.galaxy.SecurityBand;

/**
 * Pure tests of sea-encounter selection: risk rises with lawlessness and open
 * water, rosters respect band and time of day.
 *
 * @author tastybento
 */
class EncounterTableTest {

    @Test
    void testRiskRisesWithDistance() {
        double nearDock = EncounterTable.chance(50, 1000, 0.2);
        double midWater = EncounterTable.chance(500, 1000, 0.2);
        double deep = EncounterTable.chance(1000, 1000, 0.2);
        assertTrue(nearDock < midWater, "Open water must be riskier than the harbor");
        assertTrue(midWater < deep);
        assertEquals(0.2, deep, 0.0001);
        // Clamped: never zero risk right at the dock, never above the base
        assertEquals(0.05, EncounterTable.chance(0, 1000, 0.2), 0.0001);
        assertEquals(0.2, EncounterTable.chance(99999, 1000, 0.2), 0.0001);
    }

    @Test
    void testSafeWatersAreTamest() {
        // Nothing at all hunts in SAFE space: every encounter needs POLICED+
        assertTrue(EncounterTable.pick(SecurityBand.SAFE, true, 0.5).isEmpty());
        assertTrue(EncounterTable.pick(SecurityBand.SAFE, false, 0.5).isEmpty());
    }

    @Test
    void testDayAndNightRosters() {
        // Day in policed water: guardians, never drowned raiders
        Set<EncounterType> day = rollAll(SecurityBand.POLICED, true);
        assertTrue(day.contains(EncounterType.GUARDIAN_PICKET));
        assertFalse(day.contains(EncounterType.DROWNED_RAIDERS));
        // Night: trident drowned, no guardian picket
        Set<EncounterType> night = rollAll(SecurityBand.POLICED, false);
        assertTrue(night.contains(EncounterType.DROWNED_RAIDERS));
        assertFalse(night.contains(EncounterType.GUARDIAN_PICKET));
    }

    @Test
    void testLawlessnessUnlocksWorseThings() {
        Set<EncounterType> policed = rollAll(SecurityBand.POLICED, true);
        assertFalse(policed.contains(EncounterType.NAUTILUS_HORROR));
        assertFalse(policed.contains(EncounterType.PIRATE_CREW));
        Set<EncounterType> frontier = rollAll(SecurityBand.FRONTIER, true);
        assertTrue(frontier.contains(EncounterType.NAUTILUS_HORROR));
        assertFalse(frontier.contains(EncounterType.PIRATE_CREW));
        Set<EncounterType> anarchic = rollAll(SecurityBand.ANARCHIC, true);
        assertTrue(anarchic.contains(EncounterType.PIRATE_CREW));
        assertTrue(anarchic.contains(EncounterType.SEA_WITCH));
    }

    @Test
    void testCrewsArriveByBoat() {
        assertTrue(EncounterType.PIRATE_CREW.isBoated());
        assertTrue(EncounterType.SEA_WITCH.isBoated());
        assertFalse(EncounterType.DROWNED_RAIDERS.isBoated());
        // Nothing overwhelms a boat: encounters stay small
        for (EncounterType type : EncounterType.values()) {
            assertTrue(type.getMax() <= 3, type + " is too large a pack for a rowboat");
            assertTrue(type.getMin() >= 1 && type.getMin() <= type.getMax());
        }
    }

    private Set<EncounterType> rollAll(SecurityBand band, boolean isDay) {
        Set<EncounterType> seen = new HashSet<>();
        for (int i = 0; i < 100; i++) {
            Optional<EncounterType> pick = EncounterTable.pick(band, isDay, i / 100.0);
            pick.ifPresent(seen::add);
        }
        return seen;
    }
}
