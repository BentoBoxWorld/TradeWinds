package world.bentobox.tradewinds.ocean;

/**
 * Terraform instruction for one world column that falls on an island's dock or
 * plaza. {@code surfaceY} is the Y of the intended top solid block; {@code blend}
 * is 1 inside the feature and falls to 0 across the transition ring so the
 * flattened plaza meets natural terrain without cliffs.
 *
 * @param feature what this column is part of
 * @param island the island owning the feature
 * @param surfaceY Y of the top solid block the feature wants
 * @param blend 1 = fully flattened, less than 1 = transition ring toward natural terrain
 *
 * @author tastybento
 */
public record ColumnPlan(Feature feature, IslandSpec island, int surfaceY, double blend) {

    /**
     * Feature kind for a planned column.
     */
    public enum Feature {
        /** Market plaza - flat, inland, where stalls and villagers live. */
        PLAZA,
        /** Dock quay - solid pier at the waterline, running from plaza to open water. */
        DOCK
    }
}
