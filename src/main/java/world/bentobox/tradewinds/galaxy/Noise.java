package world.bentobox.tradewinds.galaxy;

/**
 * Smooth value noise over a seeded lattice. Pure maths, no Bukkit: the ocean's
 * temperature field is built from this, so it is deterministic from the galaxy
 * seed like everything else.
 *
 * @author tastybento
 */
public final class Noise {

    private Noise() {
        // Static use only
    }

    /**
     * Smooth noise in [0, 1] at a world position.
     *
     * @param seed galaxy seed
     * @param salt distinguishes independent fields
     * @param x world x
     * @param z world z
     * @param lattice spacing of the lattice in blocks - the scale of the
     *        features; larger means broader, slower-changing regions
     * @return value in [0, 1]
     */
    public static double at(long seed, long salt, double x, double z, int lattice) {
        double gx = x / lattice;
        double gz = z / lattice;
        int x0 = (int) Math.floor(gx);
        int z0 = (int) Math.floor(gz);
        double fx = smoothstep(gx - x0);
        double fz = smoothstep(gz - z0);
        double v00 = corner(seed, salt, x0, z0);
        double v10 = corner(seed, salt, x0 + 1, z0);
        double v01 = corner(seed, salt, x0, z0 + 1);
        double v11 = corner(seed, salt, x0 + 1, z0 + 1);
        return lerp(lerp(v00, v10, fx), lerp(v01, v11, fx), fz);
    }

    /**
     * Fractal (multi-octave) noise in [0, 1]: each octave halves the lattice and
     * shrinks its contribution, so one call gives broad shapes with fine detail
     * riding on top. This is what turns a flat noise field into terrain.
     *
     * @param seed galaxy seed
     * @param salt distinguishes independent fields
     * @param x world x
     * @param z world z
     * @param lattice spacing of the first (broadest) octave, in blocks
     * @param octaves number of octaves, at least 1
     * @param persistence how much of the previous octave's amplitude each
     *        successive octave keeps - 0.5 is the usual choice
     * @return value in [0, 1]
     */
    public static double fbm(long seed, long salt, double x, double z, int lattice, int octaves,
            double persistence) {
        double total = 0;
        double amplitude = 1;
        double sum = 0;
        int span = lattice;
        for (int i = 0; i < Math.max(1, octaves); i++) {
            total += at(seed, salt + i * 0x9E3779B9L, x, z, Math.max(1, span)) * amplitude;
            sum += amplitude;
            amplitude *= persistence;
            span /= 2;
        }
        return total / sum;
    }

    /**
     * Ridged noise in [0, 1]: value noise folded about its midpoint so the field
     * peaks along thin lines rather than in broad blobs. Sea-floor rifts and
     * canyons are cut where this runs high - folding is what makes them long and
     * narrow instead of round.
     *
     * @param seed galaxy seed
     * @param salt distinguishes independent fields
     * @param x world x
     * @param z world z
     * @param lattice spacing of the lattice in blocks
     * @return value in [0, 1], 1 along the ridge lines
     */
    public static double ridge(long seed, long salt, double x, double z, int lattice) {
        // Two octaves: the second breaks up the first's regularity so canyons
        // wander and branch instead of running dead straight
        double a = 1 - Math.abs(2 * at(seed, salt, x, z, lattice) - 1);
        double b = 1 - Math.abs(2 * at(seed, salt + 0x5DEECE66L, x, z, Math.max(1, lattice / 3)) - 1);
        return a * 0.75 + b * 0.25;
    }

    private static double corner(long seed, long salt, int x, int z) {
        return Hashing.toUnit(Hashing.cellHash(seed, x, z, salt));
    }

    private static double smoothstep(double t) {
        return t * t * (3 - 2 * t);
    }

    private static double lerp(double a, double b, double t) {
        return a + (b - a) * t;
    }
}
