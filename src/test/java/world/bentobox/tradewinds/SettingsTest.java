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
        // 'spawn' is not a registered player command - it would be a free warp
        // back to the spawn trading post - so bare /tw lists the commands
        assertEquals("help", settings.getDefaultNewPlayerAction());
        assertEquals("help", settings.getDefaultPlayerAction());
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
    void testGameplayGates() {
        assertTrue(settings.isIllegalTradeEnabled());
        assertEquals(0.01, settings.getFuelPerBlock());
        assertEquals(0.05, settings.getWarpFailureChance());
        assertFalse(settings.isDebug());
    }
}
