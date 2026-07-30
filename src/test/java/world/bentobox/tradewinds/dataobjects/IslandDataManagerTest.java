package world.bentobox.tradewinds.dataobjects;

import static org.junit.jupiter.api.Assertions.assertEquals;

import org.junit.jupiter.api.Test;

/**
 * Tests the stock decay math: markets trend back to equilibrium.
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
}
