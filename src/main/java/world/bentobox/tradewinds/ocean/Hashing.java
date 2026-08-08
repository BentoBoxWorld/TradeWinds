package world.bentobox.tradewinds.ocean;

/**
 * Deterministic hashing for the ocean: SplitMix64 finalizer over (seed, cell,
 * salt). Everything the ocean decides flows through these functions - never
 * {@code java.util.Random} state shared across queries, so results are
 * order-independent.
 *
 * @author tastybento
 */
public final class Hashing {

    private Hashing() {
        // Static use only
    }

    /**
     * SplitMix64 finalizer - the standard 64-bit avalanche mix.
     */
    public static long mix(long z) {
        z += 0x9E3779B97F4A7C15L;
        z = (z ^ (z >>> 30)) * 0xBF58476D1CE4E5B9L;
        z = (z ^ (z >>> 27)) * 0x94D049BB133111EBL;
        return z ^ (z >>> 31);
    }

    /**
     * Hash of (seed, cell coordinates, salt).
     */
    public static long cellHash(long seed, int cellX, int cellZ, long salt) {
        long h = mix(seed ^ salt);
        h = mix(h ^ (cellX * 0x632BE59BD9B4E019L));
        h = mix(h ^ (cellZ * 0x9E3779B97F4A7C15L));
        return h;
    }

    /**
     * Uniform double in [0, 1) from a hash.
     */
    public static double toUnit(long hash) {
        return (hash >>> 11) * 0x1.0p-53;
    }
}
