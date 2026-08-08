package world.bentobox.tradewinds.commands;

import static org.junit.jupiter.api.Assertions.assertEquals;

import org.junit.jupiter.api.Test;

/**
 * The eight-point bearing behind "/tw go" pointing home: north is -Z, east is
 * +X, and the boundaries split at 22.5 degrees either side of each point.
 *
 * @author tastybento
 */
class CompassBearingTest {

    @Test
    void testCardinals() {
        assertEquals("tradewinds.direction.north", TWSpawnCommand.compassKey(0, -100));
        assertEquals("tradewinds.direction.south", TWSpawnCommand.compassKey(0, 100));
        assertEquals("tradewinds.direction.east", TWSpawnCommand.compassKey(100, 0));
        assertEquals("tradewinds.direction.west", TWSpawnCommand.compassKey(-100, 0));
    }

    @Test
    void testDiagonals() {
        assertEquals("tradewinds.direction.north-east", TWSpawnCommand.compassKey(100, -100));
        assertEquals("tradewinds.direction.south-east", TWSpawnCommand.compassKey(100, 100));
        assertEquals("tradewinds.direction.south-west", TWSpawnCommand.compassKey(-100, 100));
        assertEquals("tradewinds.direction.north-west", TWSpawnCommand.compassKey(-100, -100));
    }

    @Test
    void testBoundariesRoundToTheNearestPoint() {
        // 20 degrees east of north is still north; 25 degrees is north-east
        assertEquals("tradewinds.direction.north", TWSpawnCommand.compassKey(36, -100));
        assertEquals("tradewinds.direction.north-east", TWSpawnCommand.compassKey(47, -100));
    }
}
