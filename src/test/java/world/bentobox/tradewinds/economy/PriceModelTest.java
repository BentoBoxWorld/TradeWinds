package world.bentobox.tradewinds.economy;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

/**
 * Pure tests of the price model: the invariants that make trading a game.
 *
 * @author tastybento
 */
class PriceModelTest {

    private final PriceModel model = new PriceModel(0.6, 1.4, 0.125, 1.15, 0.85, 500, 0.7, 1.3);

    @Test
    void testProfitableRouteExists() {
        // Buy CROPS at an AGRICULTURAL island (produces), sell at a FISHING island (demands)
        double buyAt = model.playerBuysAt(10.0, model.economicFactor(true, false, 0, 0));
        double sellAt = model.playerSellsAt(10.0, model.economicFactor(false, true, 0, 0));
        assertTrue(sellAt > buyAt, "Produce->demand route must be profitable: buy " + buyAt + " sell " + sellAt);
        // Whole coins, rounded AGAINST the player either way:
        // buy ceil(10 * 0.6 * 1.15) = 7 ; sell floor(10 * 1.4 * 0.85) = 11
        assertEquals(7.0, buyAt);
        assertEquals(11.0, sellAt);
    }

    @Test
    void testSameIslandRoundTripLosesMoney() {
        for (boolean produces : new boolean[] { true, false }) {
            double factor = model.economicFactor(produces, !produces, 2, 0);
            assertTrue(model.playerSellsAt(10.0, factor) < model.playerBuysAt(10.0, factor),
                    "Same-island arbitrage must be impossible");
        }
    }

    @Test
    void testMarginsScaleWithDanger() {
        // The same demanded good pays better in dangerous space
        double safe = model.playerSellsAt(10.0, model.economicFactor(false, true, 0, 0));
        double anarchic = model.playerSellsAt(10.0, model.economicFactor(false, true, 4, 0));
        assertTrue(anarchic > safe);
        // ANARCHIC pays about 1.5x the SAFE demand price; whole-coin rounding
        // moves it by less than a coin (11 -> 17 rather than 16.5)
        assertEquals(safe * 1.5, anarchic, 1.0);
    }

    @Test
    void testStockDrift() {
        // Flooding an island with goods depresses its prices
        assertEquals(1.0, model.driftFactor(0));
        assertTrue(model.driftFactor(250) < 1.0);
        assertEquals(0.7, model.driftFactor(5000)); // clamped
        // Buying it out raises them
        assertTrue(model.driftFactor(-250) > 1.0);
        assertEquals(1.3, model.driftFactor(-5000)); // clamped
    }

    @Test
    void testExpanderPricingDoubles() {
        assertEquals(5000.0, PriceModel.expanderPrice(5000, 0));
        assertEquals(10000.0, PriceModel.expanderPrice(5000, 1));
        assertEquals(40000.0, PriceModel.expanderPrice(5000, 3));
    }

    @Test
    void testEveryPriceIsAWholeCoin() {
        // Adopted 2026-08-03: cents made the market charts ragged, and the
        // rounding direction is what keeps the spread from closing
        for (double base = 0.5; base < 2000; base += 3.7) {
            for (boolean produces : new boolean[] { true, false }) {
                double factor = model.economicFactor(produces, !produces, 2, 0);
                double buy = model.playerBuysAt(base, factor);
                double sell = model.playerSellsAt(base, factor);
                assertEquals(buy, Math.floor(buy), "Buy price not whole: " + buy);
                assertEquals(sell, Math.floor(sell), "Sell price not whole: " + sell);
                assertTrue(buy >= 1, "Nothing is ever free: " + buy);
                assertTrue(sell < buy, "Same-island round trip must lose: buy " + buy + " sell " + sell);
            }
        }
    }

    @Test
    void testTechTilt() {
        PriceModel tech = new PriceModel(0.6, 1.4, 0.125, 1.15, 0.85, 500, 0.7, 1.3, 0.03);
        // TL4 is neutral for everything
        assertEquals(1.0, tech.techFactor(true, false, 4));
        assertEquals(1.0, tech.techFactor(false, true, 4));
        // High tech sells finished cheap (TL7: -9%) and buys raw dear (+9%)
        assertEquals(0.91, tech.techFactor(true, false, 7), 1e-9);
        assertEquals(1.09, tech.techFactor(false, true, 7), 1e-9);
        // Low tech the inverse
        assertEquals(1.09, tech.techFactor(true, false, 1), 1e-9);
        assertEquals(0.91, tech.techFactor(false, true, 1), 1e-9);
        // Neither raw nor finished: untouched at any tech
        assertEquals(1.0, tech.techFactor(false, false, 7));
        // The tech-less convenience constructor is neutral everywhere
        assertEquals(1.0, model.techFactor(true, false, 7));
    }
}
