package world.bentobox.tradewinds.economy;

/**
 * Pure price math. An island's price for a good is:
 * <pre>base x economicFactor x spread</pre>
 * where the economic factor combines the type affinity (produce cheap, demand
 * dear - demand amplified by the security band: margins scale with danger)
 * and the stock drift (players selling into an island depress its prices;
 * buying it out raises them). The buy/sell spread means a same-island round
 * trip always loses money.
 *
 * @param produceFactor price multiplier where the island produces the category
 * @param demandFactor price multiplier where the island demands the category
 * @param bandDemandBonus extra demand multiplier per security band step
 * @param buySpread multiplier on what players pay the island
 * @param sellSpread multiplier on what the island pays players
 * @param driftScale stock units for full drift swing
 * @param driftMin lower clamp of the drift factor
 * @param driftMax upper clamp of the drift factor
 *
 * @author tastybento
 */
public record PriceModel(double produceFactor, double demandFactor, double bandDemandBonus, double buySpread,
        double sellSpread, int driftScale, double driftMin, double driftMax) {

    /**
     * Stock drift: positive stock (players sold a lot here) depresses prices,
     * negative stock (players bought the island out) raises them.
     *
     * @param stock current stock relative to equilibrium 0
     * @return clamped drift factor
     */
    public double driftFactor(int stock) {
        return Math.clamp(1.0 - (double) stock / driftScale, driftMin, driftMax);
    }

    /**
     * The island's economic factor for a category.
     *
     * @param produces the island produces this category
     * @param demands the island demands this category
     * @param bandOrdinal security band ordinal (0 = SAFE)
     * @param stock current stock
     * @return combined price factor
     */
    public double economicFactor(boolean produces, boolean demands, int bandOrdinal, int stock) {
        double factor = 1.0;
        if (produces) {
            factor = produceFactor;
        } else if (demands) {
            factor = demandFactor * (1.0 + bandDemandBonus * bandOrdinal);
        }
        return factor * driftFactor(stock);
    }

    /**
     * What a player pays the island per unit.
     */
    public double playerBuysAt(double base, double economicFactor) {
        return round2(base * economicFactor * buySpread);
    }

    /**
     * What the island pays a player per unit.
     */
    public double playerSellsAt(double base, double economicFactor) {
        return round2(base * economicFactor * sellSpread);
    }

    /**
     * Cargo expander price: doubles with each one owned.
     */
    public static double expanderPrice(double basePrice, int owned) {
        return basePrice * Math.pow(2, owned);
    }

    static double round2(double value) {
        return Math.round(value * 100.0) / 100.0;
    }
}
