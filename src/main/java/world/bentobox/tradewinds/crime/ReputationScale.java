package world.bentobox.tradewinds.crime;

/**
 * The reputation number line: where the band boundaries sit, and how far the
 * score can run either way. Pure arithmetic, no Bukkit - the consequences of a
 * score are decided here and unit tested headlessly (spec principle 5).
 *
 * @param floor most negative reachable score
 * @param ceiling most positive reachable score
 * @param upstanding at or above this, a player is UPSTANDING
 * @param offender below this, a player is an OFFENDER
 * @param wanted at or below this, a player is WANTED
 * @param fugitive at or below this, a player is a FUGITIVE
 *
 * @author tastybento
 */
public record ReputationScale(int floor, int ceiling, int upstanding, int offender, int wanted, int fugitive) {

    /** Spec section 7 proposals, adopted as defaults. */
    public static final ReputationScale DEFAULT = new ReputationScale(-1000, 1000, 250, 0, -200, -500);

    public ReputationScale {
        // Thresholds must stay in order or a score could match two bands
        upstanding = Math.max(offender + 1, upstanding);
        wanted = Math.min(offender - 1, wanted);
        fugitive = Math.min(wanted - 1, fugitive);
    }

    /**
     * The standing a score earns.
     *
     * @param score reputation score
     * @return the band
     */
    public Standing standingOf(int score) {
        if (score <= fugitive) {
            return Standing.FUGITIVE;
        }
        if (score <= wanted) {
            return Standing.WANTED;
        }
        if (score < offender) {
            return Standing.OFFENDER;
        }
        return score >= upstanding ? Standing.UPSTANDING : Standing.CLEAN;
    }

    /**
     * Clamp a score into the scale.
     *
     * @param score raw score
     * @return score within [floor, ceiling]
     */
    public int clamp(int score) {
        return Math.clamp(score, floor, ceiling);
    }

    /**
     * How far a score is from being clean again - what a fine has to buy off.
     *
     * @param score reputation score
     * @return points of debt, 0 if already clean or better
     */
    public int debt(int score) {
        return Math.max(0, offender - score);
    }
}
