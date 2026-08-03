package world.bentobox.tradewinds.tasks;

import static org.junit.jupiter.api.Assertions.assertEquals;

import org.bukkit.Color;
import org.junit.jupiter.api.Test;

/**
 * The curtain's color parsing: forgiving, clamped, and never able to strip
 * the border off the sea over a typo.
 *
 * @author tastybento
 */
class BorderCurtainTaskTest {

    private static final Color FALLBACK = Color.fromRGB(1, 2, 3);

    @Test
    void testParsesTriplets() {
        assertEquals(Color.fromRGB(255, 64, 64), BorderCurtainTask.parseColor("255,64,64", FALLBACK));
        assertEquals(Color.fromRGB(0, 128, 255), BorderCurtainTask.parseColor(" 0 , 128 , 255 ", FALLBACK));
    }

    @Test
    void testClampsOutOfRange() {
        assertEquals(Color.fromRGB(255, 0, 255), BorderCurtainTask.parseColor("300,-5,999", FALLBACK));
    }

    @Test
    void testFallsBackOnNonsense() {
        assertEquals(FALLBACK, BorderCurtainTask.parseColor(null, FALLBACK));
        assertEquals(FALLBACK, BorderCurtainTask.parseColor("", FALLBACK));
        assertEquals(FALLBACK, BorderCurtainTask.parseColor("red", FALLBACK));
        assertEquals(FALLBACK, BorderCurtainTask.parseColor("1,2", FALLBACK));
        assertEquals(FALLBACK, BorderCurtainTask.parseColor("a,b,c", FALLBACK));
    }
}
