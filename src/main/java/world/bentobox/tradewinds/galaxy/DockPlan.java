package world.bentobox.tradewinds.galaxy;

/**
 * Where an island's dock and market plaza sit - pure geometry derived from the
 * island's seeded bearing. The plaza is inland of the quay so villagers stay
 * behind the waterline, out of reach of drive-by boat raids.
 *
 * @param island the island this plan belongs to
 * @param bearing dock bearing from the island center, radians
 * @param plazaX plaza center block x
 * @param plazaZ plaza center block z
 * @param plazaRadius plaza radius in blocks
 * @param dockEnd distance from island center to the seaward end of the quay
 *
 * @author tastybento
 */
public record DockPlan(IslandSpec island, double bearing, int plazaX, int plazaZ, int plazaRadius, int dockEnd) {
}
