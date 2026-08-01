package world.bentobox.tradewinds.galaxy;

/**
 * What the top block of a land column should be, expressed without Bukkit so
 * the galaxy stays pure. {@code IslandPalette} maps these to materials.
 *
 * @author tastybento
 */
public enum SurfaceKind {
    /** Ordinary land: grass, and whatever vanilla decoration plants on it. */
    GRASS,
    /** The sandy fringe of an islet - where beached shipwrecks and buried treasure belong. */
    SAND,
    /** A mushroom island's mycelium. */
    MYCELIUM
}
