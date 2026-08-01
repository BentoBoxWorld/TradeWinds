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
