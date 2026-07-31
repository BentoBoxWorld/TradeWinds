package world.bentobox.tradewinds.encounters;

import java.util.List;
import java.util.Optional;

import world.bentobox.tradewinds.galaxy.SecurityBand;

/**
 * Pure encounter selection: how likely the sea is to find you, and what it
 * sends. Risk rises with lawlessness and with distance from the nearest
 * island - the water off a safe dock is quiet, the deep between two lawless
 * islands is not.
 *
 * @author tastybento
 */
public final class EncounterTable {

    private EncounterTable() {
        // Static use only
    }

    /**
     * Chance of an encounter on one roll.
     *
     * @param distanceFromIsland blocks to the nearest island center
     * @param islandRange island space radius
     * @param baseChance configured base chance for the governing band
     * @return probability 0-1
     */
    public static double chance(double distanceFromIsland, int islandRange, double baseChance) {
        // Inside island space the sea is tamer, scaling to full risk in the deep
        double exposure = Math.clamp(distanceFromIsland / (double) islandRange, 0.25, 1.0);
        return Math.clamp(baseChance * exposure, 0.0, 1.0);
    }

    /**
     * What the sea sends, if anything: a pick from the encounters that suit
     * this band and time of day.
     *
     * @param band the governing band
     * @param isDay daylight
     * @param roll uniform 0-1 selection roll
     * @return the encounter, or empty if nothing fits
     */
    public static Optional<EncounterType> pick(SecurityBand band, boolean isDay, double roll) {
        List<EncounterType> candidates = java.util.Arrays.stream(EncounterType.values())
                .filter(type -> band.ordinal() >= type.getMinBand().ordinal())
                .filter(type -> type.getTime().matches(isDay))
                .toList();
        if (candidates.isEmpty()) {
            return Optional.empty();
        }
        int index = Math.min(candidates.size() - 1, (int) (roll * candidates.size()));
        return Optional.of(candidates.get(index));
    }
}
