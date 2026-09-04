package world.bentobox.tradewinds.ocean;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * Elite-BBC-style procedural island name generator: names are built from 2-4
 * two-letter tokens drawn from the classic digraph table, so they come out
 * pronounceable and distinct ("Lave", "Zaonce", "Tibedied"...). Pure function
 * of the input hash.
 * <p>
 * The token sequence is exposed as well as the joined name because a locale
 * may transliterate each token into its own script (GitHub #7): "LA"+"VE"
 * is "Lave" in English and whatever the Chinese locale says those syllables
 * are. The joined English name is the island's IDENTITY - island registry,
 * resident tags, logs - and must never change for a given hash.
 *
 * @author tastybento
 */
public final class NameGenerator {

    /**
     * The classic Elite token table: 32 digraphs, '.' means "skip this slot",
     * which is what produces the variable-length names.
     */
    private static final String[] PAIRS = { "..", "LE", "XE", "GE", "ZA", "CE", "BI", "SO", "US", "ES", "AR", "MA",
            "IN", "DI", "RE", "A.", "ER", "AT", "EN", "BE", "RA", "LA", "VE", "TI", "ED", "OR", "QU", "AN", "TE", "IS",
            "RI", "ON" };

    private NameGenerator() {
        // Static use only
    }

    /**
     * Generate a name from a hash. The same hash always yields the same name.
     *
     * @param hash seed for this name
     * @return a capitalized, pronounceable island name (2-8 letters)
     */
    public static String name(long hash) {
        return join(tokens(hash));
    }

    /**
     * The tokens a name is built from, in order, skips removed: upper-case
     * digraphs from the table, plus the single letter "A" (the table's "A."
     * slot). {@link #name(long)} is exactly the join of these.
     *
     * @param hash seed for this name
     * @return the tokens, never fewer than needed for a 3-letter name
     */
    public static List<String> tokens(long hash) {
        long h = hash;
        List<String> tokens = new ArrayList<>();
        int letters = 0;
        // 3 or 4 token slots
        int slots = 3 + (int) (Hashing.mix(h) & 1);
        for (int i = 0; i < slots; i++) {
            h = Hashing.mix(h + i + 1);
            letters += add(tokens, PAIRS[Math.floorMod(h, PAIRS.length)]);
        }
        while (letters < 3) {
            // Degenerate skip-heavy roll: keep appending until pronounceable
            h = Hashing.mix(h);
            letters += add(tokens, PAIRS[1 + Math.floorMod(h, PAIRS.length - 1)]);
        }
        return tokens;
    }

    /**
     * Every token the table can produce, for a locale to transliterate: the
     * 30 digraphs and the lone "A".
     */
    public static List<String> vocabulary() {
        List<String> all = new ArrayList<>();
        for (String pair : PAIRS) {
            add(all, pair);
        }
        return all;
    }

    /**
     * The canonical English rendering of a token sequence: lower case, first
     * letter capitalized.
     */
    public static String join(List<String> tokens) {
        String s = String.join("", tokens).toLowerCase(Locale.ENGLISH);
        return Character.toUpperCase(s.charAt(0)) + s.substring(1);
    }

    /** Add a table entry minus its skip markers; returns the letters added. */
    private static int add(List<String> into, String pair) {
        String token = pair.replace(".", "");
        if (!token.isEmpty()) {
            into.add(token);
        }
        return token.length();
    }
}
