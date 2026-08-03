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
 * @param techLevel 1-7; gates what the shops SELL (boat ranks, expanders) and
 *        tilts prices (high tech sells finished cheap, buys raw dear) - never
 *        docking, riding or crafting
 *
 * @author tastybento
 */
public record IslandSpec(int cellX, int cellZ, int centerX, int centerZ, IslandType type, SecurityBand band,
        String biomeKey, String name, int techLevel) {

    /**
     * Convenience constructor for callers (mostly tests) that do not care
     * about tech: a plain TL1 island.
     */
    public IslandSpec(int cellX, int cellZ, int centerX, int centerZ, IslandType type, SecurityBand band,
            String biomeKey, String name) {
        this(cellX, cellZ, centerX, centerZ, type, band, biomeKey, name, 1);
    }

    /**
     * Squared distance from a block position to this island's center.
     */
    public long distanceSquared(int blockX, int blockZ) {
        long dx = (long) blockX - centerX;
        long dz = (long) blockZ - centerZ;
        return dx * dx + dz * dz;
    }
}
