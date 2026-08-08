package world.bentobox.tradewinds.economy;

import world.bentobox.tradewinds.TradeWinds;

/**
 * Renders an amount of money the TradeWinds way: whole coins, thousands
 * separators, one currency symbol - "$27", "$1,728".
 * <p>
 * This deliberately does NOT delegate to Vault's {@code Economy.format} any
 * more (reversed 2026-08-03). That was tried, and the server's economy plugin
 * rendered "27.00 Dollars": TradeWinds rounds every price to a whole coin
 * precisely so the charts read clean, and handing the number to a formatter
 * that puts the cents back - plus a currency name after a locale that already
 * carries the symbol - defeated both halves at once. Dialogs are dense with
 * prices; they need the compact form.
 * <p>
 * The symbol is a config knob ({@code economy.currency-symbol}) so an admin
 * whose economy is gems or credits is not stuck with a dollar sign.
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
     * @return the amount as whole coins with the configured symbol: "$1,728"
     */
    public static String format(TradeWinds addon, double amount) {
        return addon.getSettings().getCurrencySymbol()
                + String.format(java.util.Locale.US, "%,d", Math.round(amount));
    }
}
