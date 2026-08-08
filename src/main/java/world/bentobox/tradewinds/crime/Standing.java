package world.bentobox.tradewinds.crime;

/**
 * A player's legal standing, from a single global reputation score.
 * <p>
 * The bands are what the world reacts to: prices, scan odds, whether police
 * launch, and whether anyone may lawfully kill you. Thresholds are configurable
 * ({@link ReputationScale}); the ordering is not.
 *
 * @author tastybento
 */
public enum Standing {

    /** Better than clean: cheaper goods, fewer scans. A status worth chasing. */
    UPSTANDING,
    /** The default. Nobody is watching you. */
    CLEAN,
    /** Known to customs: scanned more often, and it costs you at the till. */
    OFFENDER,
    /** Police respond at civilized islands, and killing you is lawful work. */
    WANTED,
    /** Shoot on sight, and no safe island will trade with you. */
    FUGITIVE;

    /**
     * Whether police launch against this standing at a civilized island.
     *
     * @return true if the law is actively hunting
     */
    public boolean isHunted() {
        return this == WANTED || this == FUGITIVE;
    }

    /**
     * Whether anyone may kill this player without a reputation penalty - the
     * bounty-hunting licence.
     *
     * @return true if lawfully killable
     */
    public boolean isLawfulTarget() {
        return isHunted();
    }

    /**
     * Whether the safest islands refuse to trade at all (spec principle 4:
     * crime pays, into danger).
     *
     * @return true if barred from safe-island markets
     */
    public boolean isBarredFromSafeTrade() {
        return this == FUGITIVE;
    }

    /**
     * The locale key for this standing's display name.
     *
     * @return locale key
     */
    public String getLocaleKey() {
        return "tradewinds.standing." + name().toLowerCase(java.util.Locale.ENGLISH);
    }
}
