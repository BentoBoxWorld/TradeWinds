package world.bentobox.tradewinds;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.HashMap;
import java.util.Map;

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
    void testWholeNumberConfigPricesDoNotExplode() {
        // The playtest crash of 2026-08-03: BentoBox's YAML deserializer promotes
        // Integer to Long but NOT to Double, so a price written "20" instead of
        // "20.0" arrives as an Integer inside a Map<String, Double>. Generics are
        // erased, so nothing complains until the first read throws
        // ClassCastException - at the trader, mid-dialog.
        Map<String, Object> raw = new HashMap<>();
        raw.put("WHEAT", 20);            // Integer, as YAML "20" gives
        raw.put("DIAMOND", 1000L);       // Long, as BentoBox's Long promotion gives
        raw.put("COAL", 40.0);           // already a Double
        raw.put("BEDROCK", "nonsense");  // not a number at all
        @SuppressWarnings({ "unchecked", "rawtypes" })
        Map<String, Double> pretendDoubles = (Map) raw;
        settings.setBasePrices(pretendDoubles);

        // Reading these must not throw, and must give the right numbers
        assertEquals(20.0, settings.getBasePrices().get("WHEAT"));
        assertEquals(1000.0, settings.getBasePrices().get("DIAMOND"));
        assertEquals(40.0, settings.getBasePrices().get("COAL"));
        assertFalse(settings.getBasePrices().containsKey("BEDROCK"), "Junk values are dropped, not kept");
        // And every value really is a Double now
        settings.getBasePrices().values().forEach(value -> assertEquals(Double.class, value.getClass()));
    }

    @Test
    void testEveryNumericMapSettingIsCoerced() {
        // Same trap, same fix, for every Map<String, Double> the config carries
        Map<String, Object> raw = new HashMap<>();
        raw.put("COAL", 8);
        @SuppressWarnings({ "unchecked", "rawtypes" })
        Map<String, Double> ints = (Map) raw;
        settings.setFuelValues(ints);
        settings.setCrimeBounties(ints);
        settings.setEdgeOverrides(ints);
        assertEquals(8.0, settings.getFuelValues().get("COAL"));
        assertEquals(8.0, settings.getCrimeBounties().get("COAL"));
        assertEquals(8.0, settings.getEdgeOverrides().get("COAL"));
    }

    @Test
    void testShippedConfigPricesAreWrittenAsDecimals() throws Exception {
        // Belt and braces: the coercion above makes this cosmetic, but a config
        // that reads "20.0" tells an admin the field is a decimal
        var config = org.bukkit.configuration.file.YamlConfiguration.loadConfiguration(
                new java.io.InputStreamReader(
                        getClass().getClassLoader().getResourceAsStream("config.yml")));
        var section = config.getConfigurationSection("economy.base-prices");
        for (String key : section.getKeys(false)) {
            assertTrue(section.get(key) instanceof Double,
                    key + " is written as " + section.get(key).getClass().getSimpleName()
                            + " - write it with a decimal point");
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
