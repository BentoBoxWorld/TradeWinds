package world.bentobox.tradewinds.crime;

/**
 * The kinds of unit the law can field, chosen by mobility rather than by
 * flavour (spec section 7).
 * <p>
 * The point is that each one denies a different escape: a golem takes anyone
 * who lands, guardians hold the water, and only phantoms can actually follow a
 * boat. Fielding all three is what makes a policed island somewhere you have
 * to think about rather than somewhere you row past.
 *
 * @author tastybento
 */
public enum PoliceUnit {

    /** Takes anyone who sets foot ashore. Cannot follow a boat. */
    GOLEM,
    /** Holds the water around the island: area denial, never a pursuit. */
    GUARDIAN,
    /** Wades and throws tridents - the sea patrol's muscle. */
    DROWNED,
    /** The only unit that can chase a boat. Does not burn at dawn. */
    PHANTOM
}
