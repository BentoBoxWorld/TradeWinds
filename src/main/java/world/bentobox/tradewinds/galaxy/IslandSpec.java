package world.bentobox.tradewinds.galaxy;

/**
 * Everything the galaxy knows about one trading island, derived purely from
 * (seed, cell). Two engines with the same seed produce identical specs.
 *
 * @param cellX galaxy grid cell x
 * @param cellZ galaxy grid cell z
 * @param centerX island center block x
 * @param centerZ island center block z
 * @param type economic character
 * @param band security band
 * @param biomeKey namespaced biome key for the whole island
 * @param name procedural island name
 *
 * @author tastybento
 */
public record IslandSpec(int cellX, int cellZ, int centerX, int centerZ, IslandType type, SecurityBand band,
        String biomeKey, String name) {

    /**
     * Squared distance from a block position to this island's center.
     */
    public long distanceSquared(int blockX, int blockZ) {
        long dx = (long) blockX - centerX;
        long dz = (long) blockZ - centerZ;
        return dx * dx + dz * dz;
    }
}
