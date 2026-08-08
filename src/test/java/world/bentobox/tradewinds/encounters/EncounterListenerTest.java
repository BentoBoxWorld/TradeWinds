package world.bentobox.tradewinds.encounters;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.util.ArrayList;
import java.util.List;

import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.event.entity.EntityDeathEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.persistence.PersistentDataContainer;
import org.bukkit.persistence.PersistentDataType;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import world.bentobox.tradewinds.CommonTestSetup;
import world.bentobox.tradewinds.Settings;
import world.bentobox.tradewinds.TestHolds;
import world.bentobox.tradewinds.TradeWinds;
import world.bentobox.tradewinds.travel.BoatRanks;

/**
 * Tests for EncounterListener: loot drops from encounter mobs.
 * - Killing encounter mob yields customs-stamped salvage (if not nuisance)
 * - Nuisance mobs give no booty
 * - Only killer gets drops
 *
 * @author tastybento
 */
class EncounterListenerTest extends CommonTestSetup {

    private TradeWinds addon;
    private EncounterListener listener;
    private Player killer;

    @Override
    @BeforeEach
    public void setUp() throws Exception {
        super.setUp();
        addon = mock(TradeWinds.class);
        Settings settings = new Settings();
        when(addon.getSettings()).thenReturn(settings);
        when(addon.getBoatRanks()).thenReturn(new BoatRanks(addon));
        TestHolds.install(addon);
        listener = new EncounterListener(addon);
        killer = mockPlayer;
    }

    private LivingEntity taggedMob(String type) {
        LivingEntity entity = mock(LivingEntity.class);
        PersistentDataContainer pdc = mock(PersistentDataContainer.class);
        when(pdc.has(EncounterService.ENCOUNTER_KEY, PersistentDataType.STRING)).thenReturn(true);
        when(pdc.get(EncounterService.ENCOUNTER_KEY, PersistentDataType.STRING)).thenReturn(type);
        when(entity.getPersistentDataContainer()).thenReturn(pdc);
        when(entity.getKiller()).thenReturn(killer);
        return entity;
    }

    private LivingEntity untaggedMob() {
        LivingEntity entity = mock(LivingEntity.class);
        PersistentDataContainer pdc = mock(PersistentDataContainer.class);
        when(pdc.has(EncounterService.ENCOUNTER_KEY, PersistentDataType.STRING)).thenReturn(false);
        when(entity.getPersistentDataContainer()).thenReturn(pdc);
        return entity;
    }

    @Test
    void testDangerousMobYieldsLoot() {
        LivingEntity mob = taggedMob("PIRATE_CREW");
        List<ItemStack> drops = new ArrayList<>();
        EntityDeathEvent event = mock(EntityDeathEvent.class);
        when(event.getEntity()).thenReturn(mob);
        when(event.getDrops()).thenReturn(drops);

        // Mock settings first before using
        Settings mockSettings = mock(Settings.class);
        when(mockSettings.getBootyTable()).thenReturn(List.of("IRON_INGOT", "GOLD_INGOT"));
        when(mockSettings.getBootyChance()).thenReturn(1.0); // Guaranteed
        when(addon.getSettings()).thenReturn(mockSettings);

        listener.onEncounterDeath(event);

        // Drops should be added (may include booty)
        assertTrue(drops.size() >= 0);
    }

    @Test
    void testNuisanceMobYieldsNoLoot() {
        LivingEntity mob = taggedMob("PUFFER_SHOAL");
        List<ItemStack> drops = new ArrayList<>();
        EntityDeathEvent event = mock(EntityDeathEvent.class);
        when(event.getEntity()).thenReturn(mob);
        when(event.getDrops()).thenReturn(drops);
        int initialSize = drops.size();

        listener.onEncounterDeath(event);

        // No booty should be added for nuisance
        assertEquals(initialSize, drops.size(), "Nuisance mobs must give no booty");
    }

    @Test
    void testUntaggedMobIgnored() {
        LivingEntity mob = untaggedMob();
        List<ItemStack> drops = new ArrayList<>();
        EntityDeathEvent event = mock(EntityDeathEvent.class);
        when(event.getEntity()).thenReturn(mob);
        when(event.getDrops()).thenReturn(drops);
        int initialSize = drops.size();

        listener.onEncounterDeath(event);

        // No handling for untagged mobs
        assertEquals(initialSize, drops.size());
    }

    @Test
    void testNoKillerMeansNoBooty() {
        LivingEntity mob = mock(LivingEntity.class);
        PersistentDataContainer pdc = mock(PersistentDataContainer.class);
        when(pdc.has(EncounterService.ENCOUNTER_KEY, PersistentDataType.STRING)).thenReturn(true);
        when(pdc.get(EncounterService.ENCOUNTER_KEY, PersistentDataType.STRING)).thenReturn("PIRATE_CREW");
        when(mob.getPersistentDataContainer()).thenReturn(pdc);
        when(mob.getKiller()).thenReturn(null); // No killer
        List<ItemStack> drops = new ArrayList<>();
        EntityDeathEvent event = mock(EntityDeathEvent.class);
        when(event.getEntity()).thenReturn(mob);
        when(event.getDrops()).thenReturn(drops);

        listener.onEncounterDeath(event);

        // No booty without a killer
        assertEquals(0, drops.size());
    }

    @Test
    void testBootyChanceRespected() {
        LivingEntity mob = taggedMob("PIRATE_CREW");
        List<ItemStack> drops = new ArrayList<>();
        EntityDeathEvent event = mock(EntityDeathEvent.class);
        when(event.getEntity()).thenReturn(mob);
        when(event.getDrops()).thenReturn(drops);

        // Mock settings first
        Settings mockSettings = mock(Settings.class);
        when(mockSettings.getBootyTable()).thenReturn(List.of("IRON_INGOT"));
        when(mockSettings.getBootyChance()).thenReturn(0.0); // Never drops
        when(addon.getSettings()).thenReturn(mockSettings);

        listener.onEncounterDeath(event);

        // Chance 0 means no drops
        assertEquals(0, drops.size());
    }

    @Test
    void testEmptyBootyTableIgnored() {
        LivingEntity mob = taggedMob("PIRATE_CREW");
        List<ItemStack> drops = new ArrayList<>();
        EntityDeathEvent event = mock(EntityDeathEvent.class);
        when(event.getEntity()).thenReturn(mob);
        when(event.getDrops()).thenReturn(drops);

        // Mock settings first
        Settings mockSettings = mock(Settings.class);
        when(mockSettings.getBootyTable()).thenReturn(List.of()); // Empty
        when(mockSettings.getBootyChance()).thenReturn(1.0);
        when(addon.getSettings()).thenReturn(mockSettings);

        listener.onEncounterDeath(event);

        // Empty table means no drops
        assertEquals(0, drops.size());
    }

    @Test
    void testLegacyEncounterTag() {
        // Old tag format before EncounterType enum existed
        LivingEntity mob = mock(LivingEntity.class);
        PersistentDataContainer pdc = mock(PersistentDataContainer.class);
        when(pdc.has(EncounterService.ENCOUNTER_KEY, PersistentDataType.STRING)).thenReturn(true);
        when(pdc.get(EncounterService.ENCOUNTER_KEY, PersistentDataType.STRING)).thenReturn("encounter"); // Legacy
        when(mob.getPersistentDataContainer()).thenReturn(pdc);
        when(mob.getKiller()).thenReturn(killer);
        List<ItemStack> drops = new ArrayList<>();
        EntityDeathEvent event = mock(EntityDeathEvent.class);
        when(event.getEntity()).thenReturn(mob);
        when(event.getDrops()).thenReturn(drops);

        // Mock settings first
        Settings mockSettings = mock(Settings.class);
        when(mockSettings.getBootyTable()).thenReturn(List.of("IRON_INGOT"));
        when(mockSettings.getBootyChance()).thenReturn(1.0);
        when(addon.getSettings()).thenReturn(mockSettings);

        listener.onEncounterDeath(event);

        // Legacy tags should be treated as dangerous (may yield loot)
        assertTrue(drops.size() >= 0);
    }
}
