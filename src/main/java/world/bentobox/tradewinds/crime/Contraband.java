package world.bentobox.tradewinds.crime;

import java.util.Set;

import world.bentobox.tradewinds.ocean.SecurityBand;

/**
 * What customs would rather you were not carrying.
 * <p>
 * Contraband is the one thing a player can <em>make</em> and still sell (spec
 * 5.0): everything else traders buy must carry a customs stamp, so money only
 * ever enters the game through trade margins. Contraband is the deliberate
 * hole in that rule, and scan risk is what it is priced against - crime pays,
 * into danger (principle 4).
 * <p>
 * Pure logic, no Bukkit: which bands deal in it, and how likely a search is.
 * The material list itself lives in config.
 *
 * @author tastybento
 */
public final class Contraband {

    private Contraband() {
        // Static use only
    }

    /**
     * Whether a port of this band deals in contraband at all. The safest ports
     * refuse it outright, which is what pushes smuggling runs outward into the
     * bands where the police are thinner but the pirates are not.
     *
     * @param band the island's security band
     * @param safestBuyer the safest band that will still buy, from config
     * @return true if this port buys contraband
     */
    public static boolean buysContraband(SecurityBand band, SecurityBand safestBuyer) {
        return band.ordinal() >= safestBuyer.ordinal();
    }

    /**
     * Whether a material is contraband under the configured list.
     *
     * @param materialName the material's name
     * @param contrabandNames configured contraband material names
     * @param illegalTradeEnabled the master gate
     * @return true if it is contraband
     */
    public static boolean isContraband(String materialName, Set<String> contrabandNames,
            boolean illegalTradeEnabled) {
        return illegalTradeEnabled && contrabandNames.contains(materialName);
    }

    /**
     * The chance a customs scan actually happens, given the band's base chance
     * and the smuggler's standing. A clean name is worth something at the
     * border: an upstanding trader is waved through more often, a known
     * offender is searched harder. This is the "positive rep must pay" half of
     * spec section 7.
     *
     * @param bandChance the band's base scan chance (0-1)
     * @param standing the player's standing
     * @param upstandingFactor multiplier applied to an upstanding player
     * @param offenderFactor multiplier applied to an offender or worse
     * @return the effective chance, clamped to [0, 1]
     */
    public static double scanChance(double bandChance, Standing standing, double upstandingFactor,
            double offenderFactor) {
        double chance = bandChance;
        if (standing == Standing.UPSTANDING) {
            chance *= upstandingFactor;
        } else if (standing != Standing.CLEAN) {
            chance *= offenderFactor;
        }
        return Math.clamp(chance, 0.0, 1.0);
    }
}
