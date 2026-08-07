package world.bentobox.tradewinds.galaxy;

/**
 * Elite-BBC-style procedural island name generator: names are built from 2-4
 * two-letter tokens drawn from the classic digraph table, so they come out
 * pronounceable and distinct ("Lave", "Zaonce", "Tibedied"...). Pure function
 * of the input hash.
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
        long h = hash;
        StringBuilder sb = new StringBuilder();
        // 3 or 4 token slots
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
            // Degenerate skip-heavy roll: keep appending until pronounceable
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
}
