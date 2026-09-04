package world.bentobox.tradewinds.ocean;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.HashSet;
import java.util.List;
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

    /**
     * The generator as it shipped before the tokens were exposed (#7). A
     * port's name is its identity in every existing world, so the refactor
     * must be byte-identical - this is the reference it is held to.
     */
    private static final String[] PAIRS = { "..", "LE", "XE", "GE", "ZA", "CE", "BI", "SO", "US", "ES", "AR", "MA",
            "IN", "DI", "RE", "A.", "ER", "AT", "EN", "BE", "RA", "LA", "VE", "TI", "ED", "OR", "QU", "AN", "TE", "IS",
            "RI", "ON" };

    private static String reference(long hash) {
        long h = hash;
        StringBuilder sb = new StringBuilder();
        int tokens = 3 + (int) (Hashing.mix(h) & 1);
        for (int i = 0; i < tokens; i++) {
            h = Hashing.mix(h + i + 1);
            String pair = PAIRS[Math.floorMod(h, PAIRS.length)];
            for (char c : pair.toCharArray()) {
                if (c != '.') {
                    sb.append(c);
                }
            }
        }
        while (sb.length() < 3) {
            h = Hashing.mix(h);
            for (char c : PAIRS[1 + Math.floorMod(h, PAIRS.length - 1)].toCharArray()) {
                if (c != '.') {
                    sb.append(c);
                }
            }
        }
        String s = sb.toString().toLowerCase();
        return Character.toUpperCase(s.charAt(0)) + s.substring(1);
    }

    @Test
    void testNamesAreUnchangedByTheTokenRefactor() {
        for (long h = -20000; h < 20000; h++) {
            long hash = Hashing.mix(h * 0x9E3779B97F4A7C15L);
            assertEquals(reference(hash), NameGenerator.name(hash), "hash " + hash);
        }
    }

    @Test
    void testTokensJoinToTheName() {
        for (long h = 0; h < 5000; h++) {
            long hash = Hashing.mix(h);
            List<String> tokens = NameGenerator.tokens(hash);
            assertEquals(NameGenerator.name(hash), NameGenerator.join(tokens));
            for (String token : tokens) {
                assertTrue(NameGenerator.vocabulary().contains(token), "Unknown token " + token);
                assertTrue(token.equals("A") || token.length() == 2, token);
            }
        }
    }

    @Test
    void testVocabularyIsThirtyOneTokens() {
        assertEquals(31, NameGenerator.vocabulary().size());
        assertTrue(NameGenerator.vocabulary().contains("A"));
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
