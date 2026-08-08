package world.bentobox.tradewinds.ocean;

/**
 * What the top block of a land column should be, expressed without Bukkit so
 * the ocean stays pure. {@code IslandPalette} maps these to materials. The
 * kind follows the column's governing biome, so a desert stands on sand and a
 * mangrove swamp on mud instead of everything wearing grass.
 *
 * @author tastybento
 */
public enum SurfaceKind {
    /** Ordinary land: grass, and whatever vanilla decoration plants on it. */
    GRASS,
    /** Deserts and beach fringes - where shipwrecks beach and treasure buries. */
    SAND,
    /** A mushroom island's mycelium. */
    MYCELIUM,
    /** The badlands: red sand over terracotta. */
    RED_SAND,
    /** Old-growth taiga floor. */
    PODZOL,
    /** Bare rock: stony shores and the peaks. */
    STONE,
    /** Windswept gravelly hills. */
    GRAVEL,
    /** Mangrove swamp floor. */
    MUD,
    /** Permanent snowpack: groves, snowy slopes, ice spike fields. */
    SNOW
}
