package world.bentobox.tradewinds.economy;

import world.bentobox.tradewinds.TradeWinds;

/**
 * Renders an amount of money the way THIS server's economy renders it.
 * <p>
 * Vault's {@code Economy.format(double)} knows the server's currency name and
 * how many decimal places it keeps ({@code fractionalDigits()}); an admin who
 * runs a whole-coin economy has said so, and printing "$0.97" over the top of
 * that ignores them. Every price in TradeWinds used to be hand-formatted to
 * two decimals, which is where the ragged market charts came from.
 * <p>
 * Without Vault there is nothing to ask, so the fallback prints whole units -
 * which is what TradeWinds' own prices now are (see
 * {@link PriceModel#playerBuysAt}).
 *
 * @author tastybento
 */
public final class Money {

    private Money() {
        // Static use only
    }

    /**
     * @param addon the addon
     * @param amount an amount of money
     * @return the amount as this server's economy would write it
     */
    public static String format(TradeWinds addon, double amount) {
        return addon.getPlugin().getVault().map(vault -> vault.format(amount))
                .orElseGet(() -> String.format("%.0f", amount));
    }
}
