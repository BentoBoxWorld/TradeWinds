package world.bentobox.tradewinds.economy;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import world.bentobox.tradewinds.CommonTestSetup;
import world.bentobox.tradewinds.Settings;
import world.bentobox.tradewinds.TradeWinds;

/**
 * Money renders as whole coins with one symbol - "$27", "$1,728" - and never
 * as "$27.00 Dollars" (playtest 2026-08-03: delegating to the server economy's
 * formatter reintroduced the cents that whole-coin pricing exists to remove,
 * plus a currency name on top of the symbol the locale already carried).
 *
 * @author tastybento
 */
class MoneyTest extends CommonTestSetup {

    private TradeWinds addon;
    private Settings settings;

    @Override
    @BeforeEach
    public void setUp() throws Exception {
        super.setUp();
        addon = mock(TradeWinds.class);
        settings = new Settings();
        when(addon.getSettings()).thenReturn(settings);
    }

    @Test
    void testWholeCoinsWithThousandsSeparators() {
        assertEquals("$27", Money.format(addon, 27.0));
        assertEquals("$432", Money.format(addon, 432.0));
        assertEquals("$1,728", Money.format(addon, 1728.0));
        assertEquals("$110,250", Money.format(addon, 110250.0));
        assertEquals("$0", Money.format(addon, 0.0));
    }

    @Test
    void testStrayFractionsRoundAwayInsteadOfShowing() {
        // Prices are whole by design; if a fraction ever leaks in, display must
        // not resurrect the cents
        assertEquals("$27", Money.format(addon, 27.0000001));
        assertEquals("$28", Money.format(addon, 27.5));
    }

    @Test
    void testTheSymbolIsConfigurable() {
        settings.setCurrencySymbol("¤");
        assertEquals("¤1,728", Money.format(addon, 1728.0));
        // A null symbol falls back rather than printing "null27"
        settings.setCurrencySymbol(null);
        assertEquals("$27", Money.format(addon, 27.0));
    }
}
