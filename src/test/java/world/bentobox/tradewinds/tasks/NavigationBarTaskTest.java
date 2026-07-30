package world.bentobox.tradewinds.tasks;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.Optional;

import org.junit.jupiter.api.Test;

import net.kyori.adventure.bossbar.BossBar;
import world.bentobox.tradewinds.galaxy.DockPlan;
import world.bentobox.tradewinds.galaxy.GalaxyConfig;
import world.bentobox.tradewinds.galaxy.GalaxyEngine;
import world.bentobox.tradewinds.galaxy.IslandSpec;
import world.bentobox.tradewinds.galaxy.SecurityBand;

/**
 * Tests the navigation bar reading: shown only in island waters, distance to
 * the pier, fill rises as you close in.
 *
 * @author tastybento
 */
class NavigationBarTaskTest {

    private final GalaxyEngine engine = new GalaxyEngine(new GalaxyConfig(11L, 2500, 160, 45, 1.0, 0, 5000, 70));

    @Test
    void testReadingInIslandWaters() {
        IslandSpec spec = engine.islandInCell(0, 0).orElseThrow();
        DockPlan plan = engine.dockPlan(spec);
        int pierX = spec.centerX() + (int) Math.round(Math.cos(plan.bearing()) * plan.dockEnd());
        int pierZ = spec.centerZ() + (int) Math.round(Math.sin(plan.bearing()) * plan.dockEnd());

        Optional<NavigationBarTask.Reading> atPier = NavigationBarTask.reading(engine, pierX, pierZ, 1000);
        assertTrue(atPier.isPresent());
        assertEquals(spec, atPier.get().island());
        assertEquals(0, atPier.get().dockDistance());
        assertEquals(1.0f, atPier.get().progress(), 0.01f);

        // At the island edge: far from the dock, low fill
        Optional<NavigationBarTask.Reading> atEdge = NavigationBarTask.reading(engine, spec.centerX() + 995,
                spec.centerZ(), 1000);
        assertTrue(atEdge.isPresent());
        assertTrue(atEdge.get().dockDistance() > 700);
        assertTrue(atEdge.get().progress() < atPier.get().progress());
    }

    @Test
    void testNoReadingInOpenOcean() {
        GalaxyEngine empty = new GalaxyEngine(new GalaxyConfig(11L, 2500, 160, 45, 0.0, 0, 5000, 70));
        assertTrue(NavigationBarTask.reading(empty, 500_000, 500_000, 1000).isEmpty());
    }

    @Test
    void testBandColors() {
        assertEquals(BossBar.Color.BLUE, NavigationBarTask.color(SecurityBand.SAFE));
        assertEquals(BossBar.Color.GREEN, NavigationBarTask.color(SecurityBand.POLICED));
        assertEquals(BossBar.Color.YELLOW, NavigationBarTask.color(SecurityBand.FRONTIER));
        assertEquals(BossBar.Color.RED, NavigationBarTask.color(SecurityBand.LAWLESS));
        assertEquals(BossBar.Color.PURPLE, NavigationBarTask.color(SecurityBand.ANARCHIC));
    }
}
