package world.bentobox.tradewinds.encounters;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.HashSet;
import java.util.Optional;
import java.util.Set;

import org.junit.jupiter.api.Test;

import world.bentobox.tradewinds.ocean.SecurityBand;

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
        // SAFE space gets the puffer shoal and NOTHING else - spice, not
        // danger (ruled 2026-08-06; before that the 2% roll picked from an
        // empty table and the config entry was decorative)
        Set<EncounterType> day = rollAll(SecurityBand.SAFE, true);
        Set<EncounterType> night = rollAll(SecurityBand.SAFE, false);
        assertEquals(Set.of(EncounterType.PUFFER_SHOAL), day);
        assertEquals(Set.of(EncounterType.PUFFER_SHOAL), night);
    }

    @Test
    void testNuisanceStaysOutOfTheRoughBands() {
        // Beyond POLICED the puffer shoal would only dilute real danger
        assertTrue(rollAll(SecurityBand.POLICED, true).contains(EncounterType.PUFFER_SHOAL));
        assertFalse(rollAll(SecurityBand.FRONTIER, true).contains(EncounterType.PUFFER_SHOAL));
        assertFalse(rollAll(SecurityBand.ANARCHIC, false).contains(EncounterType.PUFFER_SHOAL));
        // And it is booty-less by classification: nothing to farm
        assertTrue(EncounterType.PUFFER_SHOAL.isNuisance());
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
        assertFalse(policed.contains(EncounterType.DEEP_TERROR));
        assertFalse(policed.contains(EncounterType.PIRATE_CREW));
        Set<EncounterType> frontier = rollAll(SecurityBand.FRONTIER, true);
        assertTrue(frontier.contains(EncounterType.DEEP_TERROR));
        assertFalse(frontier.contains(EncounterType.PIRATE_CREW));
        Set<EncounterType> anarchic = rollAll(SecurityBand.ANARCHIC, true);
        assertTrue(anarchic.contains(EncounterType.PIRATE_CREW));
        assertTrue(anarchic.contains(EncounterType.SEA_WITCH));
    }

    @Test
    void testMobsSpawnInTheirElement() {
        // A swimmer spawned above the waterline just flops; a flier below it drowns
        assertTrue(EncounterType.DROWNED_RAIDERS.getHabitat().spawnY(70) < 70,
                "Swimmers must spawn under water");
        assertTrue(EncounterType.DEEP_TERROR.getHabitat().spawnY(70) < 70);
        assertTrue(EncounterType.GUARDIAN_PICKET.getHabitat().spawnY(70) < 70);
        assertTrue(EncounterType.PHANTOM_FLIGHT.getHabitat().spawnY(70) > 70 + 5,
                "Phantoms belong above the mast");
        assertEquals(71, EncounterType.SEA_WITCH.getHabitat().spawnY(70), "Boats float on the surface");
        assertEquals(71, EncounterType.PIRATE_CREW.getHabitat().spawnY(70));
    }

    @Test
    void testEveryEncounterMobIsActuallyHostile() {
        // The zombie nautilus taught this lesson: it is a tameable MOUNT
        // (AbstractNautilus extends Tameable, Vehicle), so it just swam away.
        // Every encounter mob must be an Enemy or the encounter is scenery -
        // except a declared NUISANCE, which is a hazard, not a hunter (the
        // pufferfish stings on contact without ever being an Enemy).
        for (EncounterType type : EncounterType.values()) {
            if (type.isNuisance()) {
                continue;
            }
            for (org.bukkit.entity.EntityType mob : type.getMobs()) {
                Class<?> clazz = mob.getEntityClass();
                assertTrue(clazz != null && org.bukkit.entity.Enemy.class.isAssignableFrom(clazz),
                        type + " spawns " + mob + ", which is not hostile");
            }
        }
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
