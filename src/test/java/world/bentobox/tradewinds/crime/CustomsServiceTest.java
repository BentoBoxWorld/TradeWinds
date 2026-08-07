package world.bentobox.tradewinds.crime;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

import org.bukkit.Material;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import world.bentobox.bentobox.api.user.User;
import world.bentobox.bentobox.hooks.VaultHook;
import world.bentobox.tradewinds.CommonTestSetup;
import world.bentobox.tradewinds.Settings;
import world.bentobox.tradewinds.TestHolds;
import world.bentobox.tradewinds.TradeWinds;
import world.bentobox.tradewinds.dataobjects.PlayerDataManager;
import world.bentobox.tradewinds.dataobjects.TWPlayerData;
import world.bentobox.tradewinds.galaxy.IslandSpec;
import world.bentobox.tradewinds.galaxy.SecurityBand;
import world.bentobox.tradewinds.galaxy.IslandType;
import world.bentobox.tradewinds.travel.HoldService;

/**
 * Tests for CustomsService: scan chance scaling, contraband detection,
 * and chase lifecycle.
 *
 * @author tastybento
 */
class CustomsServiceTest extends CommonTestSetup {

    private TradeWinds addon;
    private CustomsService service;
    private HoldService holdService;
    private UUID testPlayerId;

    @Override
    @BeforeEach
    public void setUp() throws Exception {
        super.setUp();
        testPlayerId = uuid;
        addon = mock(TradeWinds.class);
        when(addon.getSettings()).thenReturn(new TestSettings());
        when(addon.getPlugin()).thenReturn(plugin);

        // Set up in-memory player data manager with persistence
        Map<UUID, TWPlayerData> playerData = new HashMap<>();
        PlayerDataManager pdm = mock(PlayerDataManager.class);
        when(addon.getPlayerDataManager()).thenReturn(pdm);
        when(pdm.get(any(UUID.class)))
                .thenAnswer(inv -> playerData.computeIfAbsent(
                        inv.<UUID>getArgument(0),
                        id -> new TWPlayerData(id.toString())));
        doAnswer(inv -> {
            return null;
        }).when(pdm).save(any(UUID.class));

        // Set up hold/boat system with TestHolds
        TestHolds.install(addon);

        // Set up hold service for cargo counting
        holdService = mock(HoldService.class);
        when(addon.getHoldService()).thenReturn(holdService);
        // By default, no contraband aboard
        when(holdService.count(any(org.bukkit.entity.Player.class), any(Material.class))).thenReturn(0);

        // Set up reputation service
        ReputationService reputationService = new ReputationService(addon);
        when(addon.getReputationService()).thenReturn(reputationService);

        // Set up Vault for fine charging
        VaultHook vault = mock(VaultHook.class);
        when(plugin.getVault()).thenReturn(java.util.Optional.of(vault));
        when(vault.has(any(User.class), any(Double.class))).thenReturn(true);
        when(vault.getBalance(any(User.class))).thenReturn(10_000.0);

        when(addon.inWorld(any(org.bukkit.World.class))).thenReturn(true);
        when(addon.getOverWorld()).thenReturn(world);

        // Mock player inventory to return an empty array (mocked via CommonTestSetup's inv field)
        org.bukkit.inventory.ItemStack[] emptyInventory = new org.bukkit.inventory.ItemStack[36];
        when(mockPlayer.getInventory()).thenReturn(inv);
        when(inv.getContents()).thenReturn(emptyInventory);

        service = new CustomsService(addon);
    }

    @Test
    void testScanChanceScalesUpWithCriminal() {
        // Upstanding player
        double upstandingChance = Contraband.scanChance(0.5, Standing.UPSTANDING, 0.5, 2.0);
        // Criminal player
        double criminalChance = Contraband.scanChance(0.5, Standing.OFFENDER, 0.5, 2.0);
        assertTrue(criminalChance > upstandingChance, "Criminal players are scanned more");
    }

    @Test
    void testScanChanceScalesDownForUpstanding() {
        double baseChance = 0.5;
        double upstandingChance = Contraband.scanChance(baseChance, Standing.UPSTANDING, 0.5, 2.0);
        assertTrue(upstandingChance < baseChance, "Upstanding players get reduced scan chance");
    }

    @Test
    void testScanChanceClampsToOne() {
        double tooHigh = Contraband.scanChance(100.0, Standing.OFFENDER, 0.5, 2.0);
        assertEquals(1.0, tooHigh, "Scan chance is clamped to 1.0");
    }

    @Test
    void testScanChanceClampsToZero() {
        double negative = Contraband.scanChance(-10.0, Standing.UPSTANDING, 0.5, 2.0);
        assertEquals(0.0, negative, "Scan chance is clamped to 0.0");
    }

    @Test
    void testCleanPlayerGetsBandChance() {
        // Clean player gets base chance unchanged
        double chance = Contraband.scanChance(0.5, Standing.CLEAN, 0.5, 2.0);
        assertEquals(0.5, chance, "Clean player gets band chance unchanged");
    }

    @Test
    void testContrabandIsConfigurable() {
        assertTrue(service.isContraband(Material.SUGAR), "SUGAR should be contraband");
        assertTrue(service.isContraband(Material.REDSTONE), "REDSTONE should be contraband");
    }

    @Test
    void testContrabandAboardChecksHold() {
        // Set up: player has 5 sugar in hold
        when(holdService.count(mockPlayer, Material.SUGAR)).thenReturn(5);

        int aboard = service.contrabandAboard(mockPlayer);
        assertTrue(aboard > 0, "Should detect contraband in hold");
    }

    @Test
    void testContrabandCountFromHold() {
        // Mock hold returns some contraband
        when(holdService.count(mockPlayer, Material.SUGAR)).thenReturn(10);
        when(holdService.count(mockPlayer, Material.REDSTONE)).thenReturn(5);

        int total = service.contrabandAboard(mockPlayer);
        assertEquals(15, total, "Should count all contraband types");
    }

    @Test
    void testCleanScanDoesNotStartChase() {
        IslandSpec island = island(0, 0, SecurityBand.POLICED);
        // No contraband aboard
        when(holdService.count(mockPlayer, Material.SUGAR)).thenReturn(0);
        when(holdService.count(mockPlayer, Material.REDSTONE)).thenReturn(0);

        // No chase should exist even after entry
        assertFalse(service.isChased(testPlayerId), "Clean scan should not start chase");

        service.onEntry(mockPlayer, island);

        // Should remain unchased
        assertFalse(service.isChased(testPlayerId), "Clean scan does not start chase");
    }

    @Test
    void testOnEntryCallableWithContraband() {
        IslandSpec island = island(0, 0, SecurityBand.POLICED);
        // Player has contraband
        when(holdService.count(mockPlayer, Material.SUGAR)).thenReturn(3);

        // Should not throw and should complete without error
        service.onEntry(mockPlayer, island);
    }

    @Test
    void testIsChaseStartedIsQuery() {
        // isChased is a simple query that returns false when no chase
        assertFalse(service.isChased(testPlayerId), "New player has no chase");
    }

    @Test
    void testContrabandBuyerChecksByBand() {
        // Safe band doesn't buy contraband
        assertFalse(service.buysContraband(SecurityBand.SAFE), "Safe band refuses contraband");

        // Policed and up buy it
        assertTrue(service.buysContraband(SecurityBand.POLICED), "Policed band buys contraband");
        assertTrue(service.buysContraband(SecurityBand.FRONTIER), "Frontier buys contraband");
        assertTrue(service.buysContraband(SecurityBand.LAWLESS), "Lawless buys contraband");
    }

    private IslandSpec island(int cellX, int cellZ, SecurityBand band) {
        return new IslandSpec(cellX, cellZ, cellX * 100, cellZ * 100, IslandType.AGRICULTURAL, band,
                "minecraft:plains", "test-island");
    }

    private static class TestSettings extends Settings {
        public TestSettings() {
            super();
        }

        @Override
        public boolean isCrimeEnabled() {
            return true;
        }

        @Override
        public boolean isIllegalTradeEnabled() {
            return true;
        }

        @Override
        public ReputationScale reputationScale() {
            return ReputationScale.DEFAULT;
        }

        @Override
        public int penaltyFor(Crime crime) {
            return crime.getDefaultPenalty();
        }

        @Override
        public double bountyFor(Crime crime) {
            return crime.getDefaultBounty();
        }

        @Override
        public double getFinePerPoint() {
            return 10.0;
        }

        @Override
        public int getReputationDecayPoints() {
            return 1;
        }

        @Override
        public int getReputationDecayMinutes() {
            return 1;
        }

        @Override
        public int getIslandProtectionRange() {
            return 400;
        }

        @Override
        public int getSeaHeight() {
            return 62;
        }

        @Override
        public int getScanCooldownMinutes() {
            return 5;
        }

        @Override
        public java.util.Map<String, Double> getScanChance() {
            return java.util.Map.of(
                    SecurityBand.SAFE.name(), 0.1,
                    SecurityBand.POLICED.name(), 0.3,
                    SecurityBand.FRONTIER.name(), 0.5,
                    SecurityBand.LAWLESS.name(), 0.8,
                    SecurityBand.ANARCHIC.name(), 0.95);
        }

        @Override
        public double getScanUpstandingFactor() {
            return 0.5;
        }

        @Override
        public double getScanOffenderFactor() {
            return 2.0;
        }

        @Override
        public java.util.List<String> getContrabandMaterials() {
            return java.util.List.of("SUGAR", "REDSTONE", "GUNPOWDER");
        }

        @Override
        public double getSmugglingFinePerItem() {
            return 50.0;
        }

        @Override
        public double getCaughtRadius() {
            return 5.0;
        }

        @Override
        public int getChaseSeconds() {
            return 300;
        }

        @Override
        public int getChaseBreakOffDistance() {
            return 100;
        }

        @Override
        public int getFleeFlagMinutes() {
            return 10;
        }

        @Override
        public boolean isAnnounceCleanScans() {
            return false;
        }

        @Override
        public SecurityBand safestContrabandBuyer() {
            return SecurityBand.POLICED;
        }

        @Override
        public double getPatrolDistance() {
            return 150.0;
        }

        @Override
        public boolean isBountyNameplate() {
            return true;
        }
    }
}
