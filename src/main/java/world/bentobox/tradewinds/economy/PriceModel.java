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
 * @param techPriceStep price tilt per tech-level step from 4: high tech sells
 *        finished goods cheap and buys raw dear, low tech the inverse
 *
 * @author tastybento
 */
public record PriceModel(double produceFactor, double demandFactor, double bandDemandBonus, double buySpread,
        double sellSpread, int driftScale, double driftMin, double driftMax, double techPriceStep) {

    /**
     * Convenience constructor without the tech tilt (tests, tech-neutral use).
     */
    public PriceModel(double produceFactor, double demandFactor, double bandDemandBonus, double buySpread,
            double sellSpread, int driftScale, double driftMin, double driftMax) {
        this(produceFactor, demandFactor, bandDemandBonus, buySpread, sellSpread, driftScale, driftMin, driftMax,
                0.0);
    }

    /**
     * The tech tilt for a good at an island of the given tech level. Finished
     * goods get cheaper as tech rises, raw goods dearer; TL4 is neutral.
     * Categories that are neither (gems, luxuries, misc) are untouched.
     *
     * @param finished the category is finished goods
     * @param raw the category is raw goods
     * @param techLevel the island's tech level (1-7)
     * @return multiplier around 1
     */
    public double techFactor(boolean finished, boolean raw, int techLevel) {
        if (finished == raw) {
            return 1.0;
        }
        double shift = techPriceStep * (techLevel - 4);
        return Math.max(0.1, finished ? 1.0 - shift : 1.0 + shift);
    }

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
     * What a player pays the island per unit: rounded UP to a whole unit of
     * currency, and never free.
     * <p>
     * Prices are whole numbers (adopted 2026-08-03): cents made every chart
     * ragged - "Oak Log x1 - $0.97" - and Minecraft economies are usually
     * counted in whole coins. The rounding DIRECTION is the load-bearing part:
     * buying rounds up and selling rounds down, so the buy/sell spread can
     * never close. Rounding both to nearest would let a same-island round trip
     * break even or profit on some prices, which is free money.
     */
    public double playerBuysAt(double base, double economicFactor) {
        return Math.max(1, Math.ceil(base * economicFactor * buySpread));
    }

    /**
     * What the island pays a player per unit: rounded DOWN to a whole unit of
     * currency (see {@link #playerBuysAt} for why the direction matters). A
     * good can be worth nothing here - that is a market saying it does not
     * want it.
     */
    public double playerSellsAt(double base, double economicFactor) {
        return Math.max(0, Math.floor(base * economicFactor * sellSpread));
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
