package world.bentobox.tradewinds.generator;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;

import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.World.Environment;
import org.bukkit.entity.IronGolem;
import org.bukkit.entity.Villager;
import org.bukkit.generator.LimitedRegion;
import org.bukkit.generator.WorldInfo;
import org.bukkit.persistence.PersistentDataContainer;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.stubbing.Answer;

import world.bentobox.tradewinds.CommonTestSetup;
import world.bentobox.tradewinds.TradeWinds;
import world.bentobox.tradewinds.galaxy.DockPlan;
import world.bentobox.tradewinds.galaxy.GalaxyConfig;
import world.bentobox.tradewinds.galaxy.GalaxyEngine;
import world.bentobox.tradewinds.galaxy.IslandSpec;
import world.bentobox.tradewinds.galaxy.SecurityBand;

/**
 * Tests {@link IslandDecorator}: the plaza chunk gets its bell, stalls and
 * residents; other chunks are untouched.
 *
 * @author tastybento
 */
class IslandDecoratorTest extends CommonTestSetup {

    private static final long SEED = 777L;

    private TradeWinds addon;
    private GalaxyEngine engine;
    private IslandDecorator decorator;
    private IslandSpec spec;
    private DockPlan plan;

    private final List<Material> placed = new ArrayList<>();
    private final List<Villager> villagers = new ArrayList<>();
    private final List<IronGolem> golems = new ArrayList<>();

    @Override
    @BeforeEach
    public void setUp() throws Exception {
        super.setUp();
        addon = mock(TradeWinds.class);
        engine = new GalaxyEngine(new GalaxyConfig(SEED, 2500, 160, 45, 1.0, 0, 5000, 70));
        when(addon.getGalaxyEngine(anyLong())).thenReturn(engine);
        decorator = new IslandDecorator(addon);
        spec = engine.islandInCell(0, 0).orElseThrow();
        plan = engine.dockPlan(spec);
    }

    private WorldInfo worldInfo(Environment env) {
        WorldInfo wi = mock(WorldInfo.class);
        when(wi.getEnvironment()).thenReturn(env);
        when(wi.getSeed()).thenReturn(SEED);
        when(wi.getUID()).thenReturn(java.util.UUID.randomUUID());
        return wi;
    }

    private LimitedRegion region() {
        LimitedRegion region = mock(LimitedRegion.class);
        when(region.isInRegion(anyInt(), anyInt(), anyInt())).thenReturn(true);
        org.mockito.Mockito.doAnswer((Answer<Void>) inv -> {
            placed.add(inv.getArgument(3));
            return null;
        }).when(region).setType(anyInt(), anyInt(), anyInt(), any(Material.class));
        when(region.createEntity(any(Location.class), any())).thenAnswer(inv -> {
            Class<?> clazz = inv.getArgument(1);
            if (clazz == Villager.class) {
                Villager villager = mock(Villager.class);
                when(villager.getPersistentDataContainer()).thenReturn(mock(PersistentDataContainer.class));
                villagers.add(villager);
                return villager;
            }
            IronGolem golem = mock(IronGolem.class);
            when(golem.getPersistentDataContainer()).thenReturn(mock(PersistentDataContainer.class));
            golems.add(golem);
            return golem;
        });
        return region;
    }

    @Test
    void testPlazaChunkIsDecorated() {
        LimitedRegion region = region();
        decorator.populate(worldInfo(Environment.NORMAL), new Random(1), plan.plazaX() >> 4, plan.plazaZ() >> 4,
                region);
        // Landmark, lamps and stalls
        assertTrue(placed.contains(Material.BELL), "No bell placed");
        assertTrue(placed.contains(Material.LANTERN), "No lanterns placed");
        assertTrue(placed.contains(IslandPalette.canopy(spec.type())), "No stall canopy placed");
        assertTrue(placed.contains(Material.BARREL), "No stall counter placed");
        // Residents
        assertFalse(villagers.isEmpty(), "No villagers spawned");
        assertEquals(IslandDecorator.golemCount(spec.band()), golems.size());
        // Professions come from the island type's list
        verify(villagers.get(0)).setProfession(IslandPalette.professions(spec.type()).get(0));
        verify(villagers.get(0)).setPersistent(true);
        verify(villagers.get(0)).setRemoveWhenFarAway(false);
    }

    @Test
    void testDeterministicDecoration() {
        LimitedRegion region = region();
        decorator.populate(worldInfo(Environment.NORMAL), new Random(1), plan.plazaX() >> 4, plan.plazaZ() >> 4,
                region);
        List<Material> first = new ArrayList<>(placed);
        int villagerCount = villagers.size();
        placed.clear();
        // Different java Random, same island -> identical build
        decorator.populate(worldInfo(Environment.NORMAL), new Random(999), plan.plazaX() >> 4, plan.plazaZ() >> 4,
                region());
        assertEquals(first, placed.subList(0, first.size()));
        assertEquals(villagerCount * 2, villagers.size());
    }

    @Test
    void testNonPlazaChunkUntouched() {
        LimitedRegion region = region();
        decorator.populate(worldInfo(Environment.NORMAL), new Random(1), (plan.plazaX() >> 4) + 10,
                (plan.plazaZ() >> 4) + 10, region);
        verify(region, never()).setType(anyInt(), anyInt(), anyInt(), any(Material.class));
        assertTrue(villagers.isEmpty());
    }

    @Test
    void testIntersticeNeverDecorated() {
        LimitedRegion region = region();
        decorator.populate(worldInfo(Environment.NETHER), new Random(1), plan.plazaX() >> 4, plan.plazaZ() >> 4,
                region);
        verify(region, never()).setType(anyInt(), anyInt(), anyInt(), any(Material.class));
    }

    @Test
    void testGolemCountsByBand() {
        assertEquals(3, IslandDecorator.golemCount(SecurityBand.SAFE));
        assertEquals(2, IslandDecorator.golemCount(SecurityBand.POLICED));
        assertEquals(1, IslandDecorator.golemCount(SecurityBand.FRONTIER));
        assertEquals(1, IslandDecorator.golemCount(SecurityBand.LAWLESS));
        assertEquals(0, IslandDecorator.golemCount(SecurityBand.ANARCHIC));
    }

    @Test
    void testVillagerTypeByBiome() {
        assertEquals(Villager.Type.SNOW, IslandDecorator.villagerType("minecraft:snowy_plains"));
        assertEquals(Villager.Type.DESERT, IslandDecorator.villagerType("minecraft:badlands"));
        assertEquals(Villager.Type.SAVANNA, IslandDecorator.villagerType("minecraft:savanna_plateau"));
        assertEquals(Villager.Type.SWAMP, IslandDecorator.villagerType("minecraft:mangrove_swamp"));
        assertEquals(Villager.Type.TAIGA, IslandDecorator.villagerType("minecraft:windswept_hills"));
        assertEquals(Villager.Type.PLAINS, IslandDecorator.villagerType("minecraft:plains"));
    }
}
