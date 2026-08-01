package world.bentobox.tradewinds.galaxy;

/**
 * How ragged the land is. A radial mask on its own draws a perfect circle -
 * these two numbers are what stop an island looking like a coin.
 *
 * @param coastRoughness how far the coastline wanders in or out from the
 *        nominal radius, as a fraction of it. 0.25 means bays and headlands of
 *        up to a quarter of the island's radius; 0 gives a perfect circle
 * @param hilliness how much the land height varies across an island, as a
 *        fraction of its full lift. 0 gives a smooth dome
 *
 * @author tastybento
 */
public record ShapeConfig(double coastRoughness, double hilliness) {

    /** Ragged enough to look like land, not so ragged it breaks up into islets. */
    public static final ShapeConfig DEFAULT = new ShapeConfig(0.25, 0.30);

    /** Perfect circles and smooth domes - the pre-fix shape. */
    public static final ShapeConfig ROUND = new ShapeConfig(0.0, 0.0);

    public ShapeConfig {
        // Beyond about a third the coastline starts detaching into fragments
        coastRoughness = Math.clamp(coastRoughness, 0.0, 0.35);
        hilliness = Math.clamp(hilliness, 0.0, 0.6);
    }

    /**
     * How much wider than its nominal radius an island's footprint can reach
     * once the coastline is warped outward. Neighbour searches have to look
     * this bit further or a column in a headland is missed.
     *
     * @return search multiplier, at least 1
     */
    public double searchMargin() {
        return 1.0 + coastRoughness + 0.05;
    }
}
