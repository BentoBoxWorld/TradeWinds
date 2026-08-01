package world.bentobox.tradewinds.crime;

/**
 * The catalogue of crimes, each with its reputation cost and the bounty it adds
 * to the offender's head. Costs are the spec section 7 proposals and every one
 * is overridable in config (`crime.penalties.*`, `crime.bounties.*`).
 *
 * @author tastybento
 */
public enum Crime {

    /** Struck a resident villager. Cheap on its own; it adds up. */
    HURT_VILLAGER(-5, 0),
    /** Killed a resident. The market recovers; your name does not. */
    KILL_VILLAGER(-25, 100),
    /** Killed a police unit - golem, guardian or phantom. */
    KILL_POLICE(-15, 150),
    /** Killed a player who was not a lawful target. */
    KILL_INNOCENT(-100, 500),
    /** Caught by a customs scan with contraband aboard. */
    SMUGGLING(-30, 200),
    /** Sold a villager as a passenger. */
    PASSENGER_TRADE(-20, 150);

    private final int defaultPenalty;
    private final int defaultBounty;

    Crime(int defaultPenalty, int defaultBounty) {
        this.defaultPenalty = defaultPenalty;
        this.defaultBounty = defaultBounty;
    }

    /**
     * @return reputation points lost, negative
     */
    public int getDefaultPenalty() {
        return defaultPenalty;
    }

    /**
     * @return money added to the offender's bounty
     */
    public int getDefaultBounty() {
        return defaultBounty;
    }

    /**
     * The locale key announcing this crime to the offender.
     *
     * @return locale key
     */
    public String getLocaleKey() {
        return "tradewinds.crime." + name().toLowerCase(java.util.Locale.ENGLISH).replace('_', '-');
    }
}
