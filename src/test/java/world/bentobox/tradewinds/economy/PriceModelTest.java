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
        // 10.0 * 0.6 * 1.15 = 6.90 ; 10.0 * 1.4 * 0.85 = 11.90
        assertEquals(6.90, buyAt);
        assertEquals(11.90, sellAt);
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
        // ANARCHIC pays 1.5x the SAFE demand price at defaults
        assertEquals(safe * 1.5, anarchic, 0.01);
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
}
