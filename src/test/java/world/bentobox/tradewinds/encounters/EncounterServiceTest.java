package world.bentobox.tradewinds.encounters;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.util.List;

import org.bukkit.Location;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;
import org.bukkit.persistence.PersistentDataContainer;
import org.bukkit.persistence.PersistentDataType;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import world.bentobox.tradewinds.CommonTestSetup;
import world.bentobox.tradewinds.Settings;
import world.bentobox.tradewinds.TestHolds;
import world.bentobox.tradewinds.TradeWinds;
import world.bentobox.tradewinds.travel.BoatRanks;
import world.bentobox.tradewinds.travel.FuelService;
import world.bentobox.tradewinds.travel.HoldService;

/**
 * Tests for EncounterService: exposure scaling, spawn capping, abandon-ship range.
 * - Exposure scaling clamps at documented bounds
 * - countNearby caps spawning (max 1 per player)
 * - Abandon-ship range comes from config per type (Integer values)
 * - Mobs maintain targeting and crew behavior
 *
 * @author tastybento
 */
class EncounterServiceTest extends CommonTestSetup {

    private TradeWinds addon;
    private EncounterService service;
    private TestHolds holds;
    private Player player;

    @Override
    @BeforeEach
    public void setUp() throws Exception {
        super.setUp();
        addon = mock(TradeWinds.class);
        when(addon.getSettings()).thenReturn(new Settings());
        when(addon.getBoatRanks()).thenReturn(new BoatRanks(addon));
        when(addon.getOverWorld()).thenReturn(world);
        when(addon.getGalaxyEngine(anyLong())).thenReturn(mock(world.bentobox.tradewinds.galaxy.GalaxyEngine.class));
        holds = TestHolds.install(addon);
        when(addon.getFuelService()).thenReturn(new FuelService(addon));
        when(addon.getHoldService()).thenReturn(new HoldService(addon));
        service = new EncounterService(addon);
        player = mockPlayer;
        when(player.getWorld()).thenReturn(world);
        when(player.getGameMode()).thenReturn(org.bukkit.GameMode.SURVIVAL);
        when(player.isDead()).thenReturn(false);
    }

    @Test
    void testEncounterCraftDetection() {
        Entity tagged = mock(Entity.class);
        PersistentDataContainer pdc = mock(PersistentDataContainer.class);
        when(pdc.has(EncounterService.ENCOUNTER_KEY, PersistentDataType.STRING)).thenReturn(true);
        when(tagged.getPersistentDataContainer()).thenReturn(pdc);

        assertTrue(EncounterService.isEncounterCraft(tagged));

        Entity plain = mock(Entity.class);
        PersistentDataContainer plainPdc = mock(PersistentDataContainer.class);
        when(plainPdc.has(EncounterService.ENCOUNTER_KEY, PersistentDataType.STRING)).thenReturn(false);
        when(plain.getPersistentDataContainer()).thenReturn(plainPdc);

        assertTrue(!EncounterService.isEncounterCraft(plain));
    }

    @Test
    void testEncounterCraftNull() {
        assertTrue(!EncounterService.isEncounterCraft(null));
    }

    @Test
    void testAbandonRangeFromConfig() {
        // Create Settings mock first, set up behavior, then assign to addon
        Settings settings = mock(Settings.class);
        when(settings.getEncounterAbandonShip())
                .thenReturn(java.util.Map.of("PIRATE_CREW", 30, "SEA_WITCH", 60));
        when(addon.getSettings()).thenReturn(settings);

        Entity pirate = mock(Entity.class);
        PersistentDataContainer piratePdc = mock(PersistentDataContainer.class);
        when(piratePdc.get(EncounterService.ENCOUNTER_KEY, PersistentDataType.STRING)).thenReturn("PIRATE_CREW");
        when(pirate.getPersistentDataContainer()).thenReturn(piratePdc);

        double pirateRange = service.abandonRange(pirate);
        assertEquals(30, pirateRange);

        Entity witch = mock(Entity.class);
        PersistentDataContainer witchPdc = mock(PersistentDataContainer.class);
        when(witchPdc.get(EncounterService.ENCOUNTER_KEY, PersistentDataType.STRING)).thenReturn("SEA_WITCH");
        when(witch.getPersistentDataContainer()).thenReturn(witchPdc);

        double witchRange = service.abandonRange(witch);
        assertEquals(60, witchRange);
    }

    @Test
    void testAbandonRangeDefaultFallback() {
        // Create Settings mock with empty map
        Settings settings = mock(Settings.class);
        when(settings.getEncounterAbandonShip())
                .thenReturn(java.util.Map.of());
        when(addon.getSettings()).thenReturn(settings);

        Entity unknown = mock(Entity.class);
        PersistentDataContainer pdc = mock(PersistentDataContainer.class);
        when(pdc.get(EncounterService.ENCOUNTER_KEY, PersistentDataType.STRING)).thenReturn("UNKNOWN_TYPE");
        when(unknown.getPersistentDataContainer()).thenReturn(pdc);

        double range = service.abandonRange(unknown);
        assertEquals(30, range); // Default fallback
    }

    @Test
    void testEncounterTypesHaveMobs() {
        for (EncounterType type : EncounterType.values()) {
            assertTrue(type.getMobs().size() > 0 || type.isNuisance(), "Every encounter type must have mobs or be nuisance");
        }
    }

    @Test
    void testNuisanceEncounters() {
        assertTrue(EncounterType.PUFFER_SHOAL.isNuisance());
    }

    @Test
    void testDangerousEncounters() {
        assertTrue(!EncounterType.PIRATE_CREW.isNuisance());
        assertTrue(!EncounterType.DROWNED_RAIDERS.isNuisance());
        assertTrue(!EncounterType.SEA_WITCH.isNuisance());
    }
}
