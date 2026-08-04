package world.bentobox.tradewinds;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.bukkit.Material;
import org.bukkit.block.Biome;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/**
 * Tests the Stage 0 defaults of {@link Settings}.
 *
 * @author tastybento
 */
class SettingsTest extends CommonTestSetup {

    private Settings settings;

    @Override
    @BeforeEach
    public void setUp() throws Exception {
        super.setUp();
        settings = new Settings();
    }

    @Test
    void testCommands() {
        assertEquals("tw tradewinds", settings.getPlayerCommandAliases());
        assertEquals("twadmin", settings.getAdminCommandAliases());
        // 'go' is the door into the ocean, and refuses once you are at sea
        assertEquals("go", settings.getDefaultNewPlayerAction());
        assertEquals("go", settings.getDefaultPlayerAction());
    }

    @Test
    void testWorldDefaults() {
        assertEquals("TradeWinds", settings.getFriendlyName());
        assertEquals("tradewinds_world", settings.getWorldName());
        assertEquals("tradewinds", settings.getPermissionPrefix());
        assertEquals(1000, settings.getIslandDistance());
        assertEquals(400, settings.getIslandProtectionRange());
        assertEquals(70, settings.getSeaHeight());
        assertEquals(25, settings.getSeaFloor());
        assertEquals(Material.WATER, settings.getWaterBlock());
        assertEquals(Biome.OCEAN, settings.getDefaultBiome());
        assertEquals(Biome.OCEAN, settings.getDefaultAirBiome());
    }

    @Test
    void testIntersticeDefaults() {
        // The interstice is on by default and reachable only via warp failure
        assertTrue(settings.isNetherGenerate());
        assertTrue(settings.isNetherIslands());
        assertFalse(settings.isMakeNetherPortals());
        assertEquals(70, settings.getIntersticeSeaHeight());
        assertEquals(25, settings.getIntersticeSeaFloor());
        assertEquals(Material.WATER, settings.getIntersticeWaterBlock());
        assertEquals(Biome.NETHER_WASTES, settings.getDefaultNetherBiome());
    }

    @Test
    void testNoEndWorld() {
        // Spec principle 7: no End -> shulker shells unobtainable -> cargo expanders stay a money sink
        assertFalse(settings.isEndGenerate());
        assertFalse(settings.isEndIslands());
    }

    @Test
    void testGalaxyDefaults() {
        assertEquals(0, settings.getGalaxySeed());
        assertTrue(settings.getGalaxyMinSeparation() >= 2 * settings.getIslandDistance());
        assertEquals(5000, settings.getStarterClusterRadius());
        assertEquals(5, settings.getStarterClusterMinIslands());
        assertEquals(0.5, settings.getGalaxyDensity());
        assertEquals(160, settings.getIslandTerrainRadius());
        assertEquals(45, settings.getLandLift());
        assertEquals(5000, settings.getBandRadius());
        // Terrain must fit well inside the protection range
        assertTrue(settings.getIslandTerrainRadius() < settings.getIslandProtectionRange());
        // Type weights default to the enum weights, keyed by name
        assertEquals(7, settings.getTypeWeights().size());
        assertEquals(10, settings.getTypeWeights().get("AGRICULTURAL"));
        assertEquals(4, settings.getTypeWeights().get("LUXURY"));
    }

    @Test
    void testBandPolicies() {
        // No hostile spawns in civilized space; PvP only in lawless space
        assertFalse(settings.getBandMonsterSpawn().get("SAFE"));
        assertFalse(settings.getBandMonsterSpawn().get("POLICED"));
        assertTrue(settings.getBandMonsterSpawn().get("FRONTIER"));
        assertTrue(settings.getBandPvp().get("ANARCHIC"));
        assertFalse(settings.getBandPvp().get("FRONTIER"));
        // SAFE protects villagers outright; elsewhere crime is possible
        assertEquals(500, settings.getBandHurtVillagersRank().get("SAFE"));
        assertEquals(0, settings.getBandHurtVillagersRank().get("ANARCHIC"));
        assertEquals(24, settings.getResidentTetherRadius());
        assertEquals(10, settings.getResidentRespawnDelayMinutes());
        assertTrue(settings.isNavigationBossbar());
    }

    @Test
    void testEveryPricedMaterialIsReal() {
        // A typo or a Minecraft rename (SCUTE -> TURTLE_SCUTE) would silently
        // make a good unsellable rather than fail anything
        for (String name : settings.getBasePrices().keySet()) {
            assertTrue(Material.matchMaterial(name) != null, "Not a material: " + name);
        }
        for (String name : settings.getFuelValues().keySet()) {
            assertTrue(Material.matchMaterial(name) != null, "Not a fuel material: " + name);
        }
    }

    @Test
    void testShippedConfigCarriesEveryPrice() throws Exception {
        // BentoBox REPLACES map settings from config.yml instead of merging them
        // (YamlDatabaseHandler.deserializeMap), so a price missing from the
        // shipped config is a good that cannot be sold at all - it does not fall
        // back to the built-in table. The shipped config was 27 of 112 entries,
        // which is most of why scavenged goods looked untradeable.
        var config = org.bukkit.configuration.file.YamlConfiguration.loadConfiguration(
                new java.io.InputStreamReader(
                        getClass().getClassLoader().getResourceAsStream("config.yml")));
        var section = config.getConfigurationSection("economy.base-prices");
        assertTrue(section != null, "config.yml has no economy.base-prices");
        for (String name : settings.getBasePrices().keySet()) {
            assertTrue(section.contains(name), "config.yml is missing a base price for " + name);
        }
        assertEquals(settings.getBasePrices().size(), section.getKeys(false).size(),
                "config.yml and the code default table have drifted");
    }

    @Test
    void testGameplayGates() {
        assertTrue(settings.isIllegalTradeEnabled());
        assertEquals(0.01, settings.getFuelPerBlock());
        assertEquals(0.05, settings.getWarpFailureChance());
        assertFalse(settings.isDebug());
    }
}
