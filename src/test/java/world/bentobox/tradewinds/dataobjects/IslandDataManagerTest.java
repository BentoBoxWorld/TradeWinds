package world.bentobox.tradewinds.dataobjects;

import static org.junit.jupiter.api.Assertions.assertEquals;

import org.junit.jupiter.api.Test;

/**
 * Tests the stock math: markets trend back to equilibrium, and a port's
 * capacity to absorb goods is measured in coins it will spend.
 *
 * @author tastybento
 */
class IslandDataManagerTest {

    @Test
    void testDecayTowardZero() {
        // Glut decays down
        assertEquals(150, IslandDataManager.decayed(200, 50));
        assertEquals(0, IslandDataManager.decayed(30, 50));
        // Shortage decays up
        assertEquals(-150, IslandDataManager.decayed(-200, 50));
        assertEquals(0, IslandDataManager.decayed(-30, 50));
        // Equilibrium stays put
        assertEquals(0, IslandDataManager.decayed(0, 50));
    }

    @Test
    void testSaturationIsWhereTheFloorBites() {
        // Only the first (1 - driftMin) x scale coins move the price at all:
        // with the shipped 0.7 / 30000, a port saturates at 9000 coins
        assertEquals(9000, IslandDataManager.saturationValue(0.7, 30000));
        // A deeper floor means a port that keeps paying for longer
        assertEquals(15000, IslandDataManager.saturationValue(0.5, 30000));
    }

    @Test
    void testAbsorbableHeadroom() {
        // A fresh port will take the full purse
        assertEquals(9000, IslandDataManager.absorbable(9000, 0));
        // Half sold in, half left
        assertEquals(4500, IslandDataManager.absorbable(9000, 4500));
        // Saturated, and never negative - "no more" is the answer, not "-3000 more"
        assertEquals(0, IslandDataManager.absorbable(9000, 9000));
        assertEquals(0, IslandDataManager.absorbable(9000, 12000));
        // A port that has been BOUGHT out is hungrier than a fresh one
        assertEquals(11000, IslandDataManager.absorbable(9000, -2000));
    }
}
