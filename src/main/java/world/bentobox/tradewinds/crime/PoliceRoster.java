package world.bentobox.tradewinds.crime;

import java.util.ArrayList;
import java.util.List;

/**
 * Who the law sends, and how many. Pure decision logic, unit tested headlessly
 * like the ocean - the Bukkit half only turns these into entities.
 * <p>
 * Two rules shape every roster. Units are picked by <b>mobility</b>, so each
 * one denies a different escape (spec section 7): golems take anyone ashore,
 * guardians hold the water, and phantoms are the only thing that can follow a
 * boat. And the response scales with the <b>band</b>, so safe space answers
 * hard and lawless space barely answers at all - which is the whole reason to
 * run cargo outward.
 *
 * @author tastybento
 */
public final class PoliceRoster {

    private PoliceRoster() {
        // Static use only
    }

    /**
     * The response to a wanted player.
     *
     * @param size the band's configured patrol size
     * @param ashore whether the target is standing on land
     * @param fugitive whether the target is a fugitive rather than merely wanted
     * @return the units to field, in spawn order
     */
    public static List<PoliceUnit> forWanted(int size, boolean ashore, boolean fugitive) {
        List<PoliceUnit> units = new ArrayList<>();
        if (size <= 0) {
            return units; // This band has nobody to send
        }
        int total = fugitive ? size + 1 : size;
        for (int i = 0; i < total; i++) {
            if (ashore) {
                // Golems hold the ground, with air support so leaving by boat
                // is not a free escape
                units.add(i % 3 == 2 ? PoliceUnit.PHANTOM : PoliceUnit.GOLEM);
            } else {
                // At sea: guardians deny the water, drowned close in, and a
                // phantom keeps up if the target simply rows away
                units.add(switch (i % 3) {
                case 0 -> PoliceUnit.GUARDIAN;
                case 1 -> PoliceUnit.DROWNED;
                default -> PoliceUnit.PHANTOM;
                });
            }
        }
        return units;
    }

    /**
     * The customs patrol sent after a smuggler. Always a sea response - customs
     * launch from the harbour, and a detection happens at the border.
     *
     * @param size the band's configured patrol size
     * @return the units to field
     */
    public static List<PoliceUnit> forCustoms(int size) {
        List<PoliceUnit> units = new ArrayList<>();
        for (int i = 0; i < Math.max(0, size); i++) {
            // A phantom is not optional. Launching from the dock puts the
            // swimmers a long way behind a boat and they will never close the
            // gap, so without something that can pursue, "run" stops being a
            // choice and becomes the answer every time.
            units.add(switch (i % 3) {
            case 0 -> PoliceUnit.GUARDIAN;
            case 1 -> PoliceUnit.DROWNED;
            default -> PoliceUnit.PHANTOM;
            });
        }
        return units;
    }

    /**
     * Whether the law has broken off.
     * <p>
     * Police pursue inside the island's protection zone and give up at the
     * border (spec section 7). Without this a wanted player could be followed
     * across the ocean by an ever-growing escort, and the bands would stop
     * meaning anything: the whole design is that lawless water is where the law
     * is not.
     *
     * @param distanceFromIsland the target's distance from the island centre
     * @param protectionRange the island's protection range
     * @param breakOff how far past the border the law will still follow
     * @return true if the pursuit should be called off
     */
    public static boolean shouldBreakOff(double distanceFromIsland, int protectionRange, int breakOff) {
        return distanceFromIsland > protectionRange + breakOff;
    }
}
