package world.bentobox.tradewinds.travel;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;

import org.junit.jupiter.api.Test;

import world.bentobox.tradewinds.ocean.IslandSpec;
import world.bentobox.tradewinds.ocean.IslandType;
import world.bentobox.tradewinds.ocean.SecurityBand;
import world.bentobox.tradewinds.travel.WarpService.Destination;

/**
 * Headless tests of "can this sailor afford to leave?".
 *
 * @author tastybento
 */
class FuelWarningTest {

    private Destination dest(int cost) {
        IslandSpec spec = new IslandSpec(1, 1, 100, 100, IslandType.FISHING, SecurityBand.SAFE,
                "minecraft:plains", "Somewhere");
        return new Destination(spec, cost, true);
    }

    @Test
    void testCheapestRouteIsTheOneThatMatters() {
        // Being able to afford the far island is irrelevant; being unable to
        // afford the nearest one is what strands you
        assertEquals(12, FuelWarning.cheapestRoute(List.of(dest(40), dest(12), dest(97))));
        assertEquals(-1, FuelWarning.cheapestRoute(List.of()));
    }

    @Test
    void testWarnsOnlyWhenGenuinelyShort() {
        assertTrue(FuelWarning.isLow(0, 12, 1.0));
        assertTrue(FuelWarning.isLow(11.9, 12, 1.0));
        assertFalse(FuelWarning.isLow(12, 12, 1.0));
        assertFalse(FuelWarning.isLow(500, 12, 1.0));
    }

    @Test
    void testNowhereToGoIsNotAFuelProblem() {
        // A sailor with nothing charted is not stranded by fuel, and telling
        // them to buy some would be a lie
        assertFalse(FuelWarning.isLow(0, -1, 1.0));
        assertFalse(FuelWarning.isLow(0, -1, 5.0));
    }

    @Test
    void testMarginWarnsWithSomethingInReserve() {
        // At 1.5 a sailor is told while they can still just about leave
        assertTrue(FuelWarning.isLow(15, 12, 1.5));
        assertFalse(FuelWarning.isLow(18, 12, 1.5));
        // A margin below 1 must not silence a genuine shortfall
        assertTrue(FuelWarning.isLow(5, 12, 0.1));
    }

    @Test
    void testShortfallIsActionable() {
        // Round up: telling someone they need 3.2 charcoal helps nobody
        assertEquals(12, FuelWarning.shortfall(0, 12));
        assertEquals(4, FuelWarning.shortfall(8.1, 12));
        assertEquals(1, FuelWarning.shortfall(11.5, 12));
        assertEquals(0, FuelWarning.shortfall(12, 12));
        assertEquals(0, FuelWarning.shortfall(99, 12));
    }
}
