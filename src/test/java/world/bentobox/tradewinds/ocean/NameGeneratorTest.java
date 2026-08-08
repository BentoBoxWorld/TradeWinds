package world.bentobox.tradewinds.ocean;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.HashSet;
import java.util.Set;

import org.junit.jupiter.api.Test;

/**
 * Tests the Elite-style name generator: deterministic, pronounceable-looking,
 * bounded length.
 *
 * @author tastybento
 */
class NameGeneratorTest {

    @Test
    void testDeterminism() {
        for (long h = -500; h < 500; h++) {
            assertEquals(NameGenerator.name(h), NameGenerator.name(h));
        }
    }

    @Test
    void testShape() {
        for (long h = 0; h < 2000; h++) {
            String name = NameGenerator.name(h * 0x9E3779B97F4A7C15L);
            assertTrue(name.length() >= 3 && name.length() <= 8, "Bad length: " + name);
            assertTrue(name.matches("[A-Z][a-z]+"), "Bad shape: " + name);
        }
    }

    @Test
    void testVariety() {
        Set<String> names = new HashSet<>();
        for (long h = 0; h < 1000; h++) {
            names.add(NameGenerator.name(Hashing.mix(h)));
        }
        assertTrue(names.size() > 700, "Not enough variety: " + names.size());
    }
}
