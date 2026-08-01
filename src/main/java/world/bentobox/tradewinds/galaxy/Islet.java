package world.bentobox.tradewinds.galaxy;

/**
 * A wild islet: unowned, unnamed land between the trading islands. Free to
 * mine, farm, build on and live on - the "Minecraft stuff" half of the game
 * (spec section 5.0, the two economies).
 * <p>
 * Unlike trading islands these vary in size and in biome, so the sea is dotted
 * with anything from a sandbar with three trees on it to a proper little
 * island with a cave system under it.
 *
 * @param centerX block x of the islet's center
 * @param centerZ block z of the islet's center
 * @param radius terrain radius in blocks
 * @param biomeKey the islet's whole-island biome key
 *
 * @author tastybento
 */
public record Islet(int centerX, int centerZ, int radius, String biomeKey) {

    /**
     * Squared distance from a block position to this islet's center.
     *
     * @param blockX block x
     * @param blockZ block z
     * @return squared distance in blocks
     */
    public long distanceSquared(int blockX, int blockZ) {
        long dx = (long) blockX - centerX;
        long dz = (long) blockZ - centerZ;
        return dx * dx + dz * dz;
    }

    /**
     * Whether this islet is a rare mushroom island - no hostile spawns, mycelium
     * underfoot, and mooshrooms if vanilla decoration is on.
     *
     * @return true for mushroom fields
     */
    public boolean isMushroom() {
        return GalaxyEngine.MUSHROOM_BIOME.equals(biomeKey);
    }
}
