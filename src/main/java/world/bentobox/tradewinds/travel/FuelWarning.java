package world.bentobox.tradewinds.travel;

import java.util.List;

import world.bentobox.tradewinds.travel.WarpService.Destination;

/**
 * Whether a sailor can afford to leave the port they are standing in.
 * <p>
 * Running out of fuel at a port is not a soft failure - the only way onward is
 * rowing, and a player who has not noticed will simply be stuck wondering why
 * the warp dialog refuses them. The check is deliberately made <b>at the port</b>,
 * where fuel is for sale and the problem is still fixable, rather than out at
 * the border where it is not.
 * <p>
 * Pure arithmetic, so the rule is testable without a server.
 *
 * @author tastybento
 */
public final class FuelWarning {

    private FuelWarning() {
        // Static use only
    }

    /**
     * The cheapest way out of here, in fuel units.
     *
     * @param destinations the warp destinations offered at this island
     * @return the lowest fuel cost, or -1 if there is nowhere to go
     */
    public static int cheapestRoute(List<Destination> destinations) {
        return destinations.stream().mapToInt(Destination::fuelCost).min().orElse(-1);
    }

    /**
     * Whether a sailor is too low on fuel to warp anywhere from here.
     *
     * @param fuelAboard fuel units in the hold
     * @param cheapestRoute the cheapest route out, or -1 if there is none
     * @param margin multiplier on the cheapest route before warning; 1.0 warns
     *        only when genuinely stuck, higher warns with something in reserve
     * @return true if they should be told
     */
    public static boolean isLow(double fuelAboard, int cheapestRoute, double margin) {
        if (cheapestRoute < 0) {
            return false; // Nowhere charted to warp to: fuel is not the problem
        }
        return fuelAboard < cheapestRoute * Math.max(1.0, margin);
    }

    /**
     * How much more fuel is needed, rounded up - a number a player can act on.
     *
     * @param fuelAboard fuel units in the hold
     * @param cheapestRoute the cheapest route out
     * @return units short, never negative
     */
    public static int shortfall(double fuelAboard, int cheapestRoute) {
        return (int) Math.max(0, Math.ceil(cheapestRoute - fuelAboard));
    }
}
