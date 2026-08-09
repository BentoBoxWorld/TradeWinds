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
import world.bentobox.tradewinds.Settings;
import world.bentobox.tradewinds.economy.TypeEconomy;
import world.bentobox.tradewinds.TradeWinds;
import world.bentobox.tradewinds.ocean.DockPlan;
import world.bentobox.tradewinds.ocean.OceanConfig;
import world.bentobox.tradewinds.ocean.OceanEngine;
import world.bentobox.tradewinds.ocean.IslandSpec;
import world.bentobox.tradewinds.ocean.IslandType;
import world.bentobox.tradewinds.ocean.SecurityBand;

/**
 * Tests {@link IslandDecorator}: the plaza chunk gets its bell, stalls and
 * residents; other chunks are untouched.
 *
 * @author tastybento
 */
class IslandDecoratorTest extends CommonTestSetup {

    private static final long SEED = 777L;

    private TradeWinds addon;
    private OceanEngine engine;
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
        when(addon.getSettings()).thenReturn(new Settings());
        engine = new OceanEngine(new OceanConfig(SEED, 2500, 160, 45, 1.0, 0, 5000, 70));
        when(addon.getOceanEngine(anyLong())).thenReturn(engine);
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
        when(region.isInRegion(any(Location.class))).thenReturn(true);
        when(region.createEntity(any(Location.class), any())).thenAnswer(inv -> {
            Class<?> clazz = inv.getArgument(1);
            if (clazz == Villager.class) {
                Villager villager = mock(Villager.class);
                PersistentDataContainer villagerPdc = mock(PersistentDataContainer.class);
                when(villager.getPersistentDataContainer()).thenReturn(villagerPdc);
                villagers.add(villager);
                return villager;
            }
            if (clazz == IronGolem.class) {
                IronGolem golem = mock(IronGolem.class);
                PersistentDataContainer golemPdc = mock(PersistentDataContainer.class);
                when(golem.getPersistentDataContainer()).thenReturn(golemPdc);
                golems.add(golem);
                return golem;
            }
            return mock(clazz);
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
        // The galley: every plaza offers a public workbench and cooking
        // hearth - visitor-usable - so a sailor can craft and cook the catch
        assertTrue(placed.contains(Material.CRAFTING_TABLE), "No public workbench placed");
        assertTrue(placed.contains(Material.CAMPFIRE), "No cooking hearth placed");
        assertTrue(placed.contains(Material.COBBLESTONE), "The campfire must stand on a hearth block");
        // And the tech level furnishes the amenity row beyond it
        IslandPalette.amenities(spec.techLevel())
                .forEach(a -> assertTrue(placed.contains(a), "Missing tech amenity " + a));
        // Workstations and the type landmark are present
        assertTrue(placed.contains(IslandPalette.workstations(spec.type()).get(0)), "No workstation placed");
        assertTrue(placed.stream().anyMatch(LANDMARK_SIGNATURES.get(spec.type())::contains),
                "No landmark signature block for " + spec.type());
        // Professions come from the island type's list
        verify(villagers.get(0)).setProfession(IslandPalette.professions(spec.type()).get(0));
        verify(villagers.get(0)).setPersistent(true);
        verify(villagers.get(0)).setRemoveWhenFarAway(false);
        // Playtest regression: without trade XP the brain resets professions to
        // unemployed (or the stall barrels turn everyone fisherman)
        villagers.forEach(v -> verify(v).setVillagerExperience(1));
    }

    @Test
    void testAmenitiesGrowWithTech() {
        // Low tech gets only the galley; the ladder adds smelting, smithing,
        // brewing, an anvil, and finally enchanting - cumulative
        assertTrue(IslandPalette.amenities(1).isEmpty());
        assertTrue(IslandPalette.amenities(2).isEmpty());
        java.util.List<Material> t3 = IslandPalette.amenities(3);
        assertTrue(t3.contains(Material.FURNACE) && t3.contains(Material.STONECUTTER));
        assertFalse(t3.contains(Material.BREWING_STAND));
        java.util.List<Material> t5 = IslandPalette.amenities(5);
        assertTrue(t5.contains(Material.BREWING_STAND) && t5.contains(Material.CAULDRON));
        assertFalse(t5.contains(Material.ENCHANTING_TABLE));
        java.util.List<Material> t7 = IslandPalette.amenities(7);
        assertTrue(t7.contains(Material.ENCHANTING_TABLE) && t7.contains(Material.ANVIL));
        assertTrue(t7.containsAll(t5), "Tiers must be cumulative");
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

    /** One block that only the landmark of that type places. */
    private static final java.util.Map<IslandType, List<Material>> LANDMARK_SIGNATURES = java.util.Map.of(
            IslandType.AGRICULTURAL, List.of(Material.WHEAT, Material.HAY_BLOCK),
            IslandType.FOREST, List.of(Material.DARK_OAK_LOG),
            IslandType.FISHING, List.of(Material.CAMPFIRE),
            IslandType.MINING, List.of(Material.RAIL, Material.STRIPPED_SPRUCE_LOG),
            IslandType.INDUSTRIAL, List.of(Material.BRICKS, Material.CAMPFIRE),
            IslandType.LUXURY, List.of(Material.CHISELED_QUARTZ_BLOCK),
            IslandType.FROZEN, List.of(Material.PACKED_ICE));

    @Test
    void testPierEndGetsBanner() {
        LimitedRegion region = region();
        int pierX = spec.centerX() + (int) Math.round(Math.cos(plan.bearing()) * (plan.dockEnd() - 2));
        int pierZ = spec.centerZ() + (int) Math.round(Math.sin(plan.bearing()) * (plan.dockEnd() - 2));
        decorator.populate(worldInfo(Environment.NORMAL), new Random(1), pierX >> 4, pierZ >> 4, region);
        assertTrue(placed.contains(IslandPalette.banner(spec.type())), "No banner at pier end");
        assertTrue(placed.contains(Material.LANTERN), "No lantern at pier end");
    }

    @Test
    void testInnPortsGetABedYouCanSleepIn() {
        // Sleeping is otherwise impossible in an ocean: no bed anywhere but an
        // island of your own (playtest 2026-08-08). Ports on the inn list get
        // a room with a made bed - both halves, or it is not a bed at all.
        var innSpec = engine.islandsNear(0, 0, 40000).stream()
                .filter(s -> new Settings().getInnIslandTypes()
                        .contains(s.type().name()))
                .findFirst().orElseThrow();
        var innPlan = engine.dockPlan(innSpec);
        org.bukkit.block.data.type.Bed bed = mock(org.bukkit.block.data.type.Bed.class);
        mockedBukkit.when(() -> org.bukkit.Bukkit.createBlockData(Material.WHITE_BED)).thenReturn(bed);
        LimitedRegion region = region();

        decorator.populate(worldInfo(Environment.NORMAL), new Random(1), innPlan.plazaX() >> 4,
                innPlan.plazaZ() >> 4, region);

        assertTrue(placed.contains(IslandPalette.planks(innSpec.type())), "The inn has no walls");
        verify(bed).setPart(org.bukkit.block.data.type.Bed.Part.FOOT);
        verify(bed).setPart(org.bukkit.block.data.type.Bed.Part.HEAD);
        verify(region, org.mockito.Mockito.times(2)).setBlockData(anyInt(), anyInt(), anyInt(), any());
    }

    @Test
    void testFarmPortsKeepTheirOwnFlockColour() {
        // Seeded, so a sailor after a particular colour has somewhere to sail
        // TO - and the same port always sells the same wool
        Material first = TypeEconomy.localWool(SEED, 3, 7);
        assertEquals(first, TypeEconomy.localWool(SEED, 3, 7));
        assertTrue(java.util.stream.IntStream.range(0, 40)
                .mapToObj(i -> TypeEconomy.localWool(SEED, i, i * 3))
                .distinct().count() > 1, "Every farm port sells the same colour - the roll is not rolling");
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
