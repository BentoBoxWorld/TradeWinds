package world.bentobox.tradewinds.crime;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import org.bukkit.Bukkit;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import world.bentobox.bentobox.api.user.User;
import world.bentobox.bentobox.hooks.VaultHook;
import world.bentobox.tradewinds.CommonTestSetup;
import world.bentobox.tradewinds.Settings;
import world.bentobox.tradewinds.TradeWinds;
import world.bentobox.tradewinds.ocean.SecurityBand;

/**
 * Tests for ReputationService: crime recording, fines, bounties, and decay.
 *
 * @author tastybento
 */
class ReputationServiceTest extends CommonTestSetup {

    private TradeWinds addon;
    private ReputationService service;
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
        Map<UUID, world.bentobox.tradewinds.dataobjects.TWPlayerData> playerData = new HashMap<>();
        world.bentobox.tradewinds.dataobjects.PlayerDataManager pdm =
                mock(world.bentobox.tradewinds.dataobjects.PlayerDataManager.class);
        when(addon.getPlayerDataManager()).thenReturn(pdm);
        when(pdm.get(any(UUID.class)))
                .thenAnswer(inv -> playerData.computeIfAbsent(
                        inv.<UUID>getArgument(0),
                        id -> new world.bentobox.tradewinds.dataobjects.TWPlayerData(id.toString())));
        doAnswer(inv -> {
            // Save simply persists back to the map (already there via reference)
            return null;
        }).when(pdm).save(any(UUID.class));

        VaultHook vault = mock(VaultHook.class);
        when(plugin.getVault()).thenReturn(java.util.Optional.of(vault));
        when(vault.has(any(User.class), any(Double.class))).thenReturn(true);
        when(vault.getBalance(any(User.class))).thenReturn(10_000.0);

        when(addon.inWorld(any(org.bukkit.World.class))).thenReturn(true);

        // Mock Bukkit.getOnlinePlayers() for decay tick
        mockedBukkit.when(Bukkit::getOnlinePlayers).thenReturn(List.of(mockPlayer));

        service = new ReputationService(addon);
    }

    @Test
    void testRecordCrimeDocksReputation() {
        assertEquals(Standing.CLEAN, service.standing(testPlayerId));
        service.recordCrime(mockPlayer, Crime.HURT_VILLAGER);
        assertTrue(service.score(testPlayerId) < 0);
    }

    @Test
    void testRecordCrimeMovesBandOnKill() {
        assertEquals(Standing.CLEAN, service.standing(testPlayerId));
        service.recordCrime(mockPlayer, Crime.KILL_INNOCENT);
        Standing after = service.standing(testPlayerId);
        // KILL_INNOCENT is -100, which puts the player at OFFENDER, not WANTED
        assertEquals(Standing.OFFENDER, after, "Killing an innocent moves to OFFENDER band");
        assertTrue(after.ordinal() > Standing.CLEAN.ordinal());
    }

    @Test
    void testCrimeAddsToTheBounty() {
        assertEquals(0, service.bounty(testPlayerId));
        service.recordCrime(mockPlayer, Crime.KILL_INNOCENT);
        assertTrue(service.bounty(testPlayerId) > 0, "A serious crime adds a bounty");
    }

    @Test
    void testFineCalculationAfterCrime() {
        service.recordCrime(mockPlayer, Crime.KILL_INNOCENT);
        assertTrue(service.fine(testPlayerId) > 0, "A criminal owes money");

        service.credit(testPlayerId, 1000);
        assertEquals(0, service.fine(testPlayerId), "A clean player owes nothing");
    }

    @Test
    void testFineNeverBuysVirtue() {
        service.recordCrime(mockPlayer, Crime.KILL_INNOCENT);
        service.payFine(mockPlayer);
        Standing cleaned = service.standing(testPlayerId);
        assertEquals(Standing.CLEAN, cleaned, "A fine clears to Clean, never beyond");
        assertNotEquals(Standing.UPSTANDING, cleaned);
    }

    @Test
    void testPayFineChargesThePlayer() {
        VaultHook vault = plugin.getVault().get();
        service.recordCrime(mockPlayer, Crime.KILL_INNOCENT);

        service.payFine(mockPlayer);

        verify(vault).withdraw(any(User.class), any(Double.class));
    }

    @Test
    void testPayFineClearsBounty() {
        service.recordCrime(mockPlayer, Crime.KILL_INNOCENT);
        assertTrue(service.bounty(testPlayerId) > 0);

        service.payFine(mockPlayer);

        assertEquals(0, service.bounty(testPlayerId), "Paying a fine clears the bounty");
    }

    @Test
    void testClaimBountyClears() {
        service.recordCrime(mockPlayer, Crime.KILL_INNOCENT);
        double bounty = service.bounty(testPlayerId);

        double claimed = service.claimBounty(testPlayerId);

        assertEquals(bounty, claimed);
        assertEquals(0, service.bounty(testPlayerId), "Bounty claimed is bounty cleared");
    }

    @Test
    void testCreditReputationTowardZero() {
        service.recordCrime(mockPlayer, Crime.HURT_VILLAGER);
        int scoreAfterCrime = service.score(testPlayerId);
        assertTrue(scoreAfterCrime < 0);

        service.credit(testPlayerId, 100);
        int scoreAfterCredit = service.score(testPlayerId);
        assertTrue(scoreAfterCredit > scoreAfterCrime, "Credit should move toward zero");
    }

    @Test
    void testCreditAnnouncesBandChange() {
        // Get player to WANTED status by recording multiple crimes
        for (int i = 0; i < 3; i++) {
            service.recordCrime(mockPlayer, Crime.KILL_INNOCENT);
        }
        Standing wantedStanding = service.standing(testPlayerId);
        assertTrue(wantedStanding.isHunted());

        // Credit them enough to move back to CLEAN
        service.credit(testPlayerId, 500);

        Standing afterCredit = service.standing(testPlayerId);
        assertNotEquals(wantedStanding, afterCredit, "Enough credit moves the band");
        assertTrue(afterCredit.ordinal() < wantedStanding.ordinal(), "Credit moves toward better standing");
    }

    @Test
    void testDecayWalksTowardZeroFromNegative() {
        int before = -100;
        int decayed = ReputationService.decayed(before, 5);
        assertTrue(decayed > before && decayed < 0, "Negative score should move toward zero");
        assertEquals(-95, decayed);
    }

    @Test
    void testDecayWalksTowardZeroFromPositive() {
        int before = 100;
        int decayed = ReputationService.decayed(before, 5);
        assertTrue(decayed < before && decayed > 0, "Positive score should move toward zero");
        assertEquals(95, decayed);
    }

    @Test
    void testDecayNeverOvershoots() {
        // From negative side
        int result = ReputationService.decayed(-3, 5);
        assertEquals(0, result, "Decay should stop at zero, not overshoot to positive");

        // From positive side
        result = ReputationService.decayed(3, 5);
        assertEquals(0, result, "Decay should stop at zero, not overshoot to negative");
    }

    @Test
    void testNegativeStepTreatedAsMagnitude() {
        int result = ReputationService.decayed(-100, -5);
        assertEquals(-95, result, "Negative step should be treated as magnitude");
    }

    @Test
    void testDisabledCrimeReturnsClean() {
        TestSettings nocrimeSettings = new TestSettings() {
            @Override
            public boolean isCrimeEnabled() {
                return false;
            }
        };
        when(addon.getSettings()).thenReturn(nocrimeSettings);
        service = new ReputationService(addon);

        service.recordCrime(mockPlayer, Crime.KILL_INNOCENT);
        assertEquals(Standing.CLEAN, service.standing(testPlayerId));
    }

    @Test
    void testMultipleCrimesStack() {
        Standing before = service.standing(testPlayerId);
        for (int i = 0; i < 10; i++) {
            service.recordCrime(mockPlayer, Crime.HURT_VILLAGER);
        }
        Standing after = service.standing(testPlayerId);
        assertTrue(after.ordinal() > before.ordinal(), "Multiple crimes should degrade standing");
    }

    @Test
    void testPayFineFailsWithoutCash() {
        VaultHook vault = plugin.getVault().get();
        when(vault.has(any(User.class), any(Double.class))).thenReturn(false);

        service.recordCrime(mockPlayer, Crime.KILL_INNOCENT);
        boolean paid = service.payFine(mockPlayer);

        assertFalse(paid, "Cannot pay fine without sufficient cash");
        assertNotEquals(Standing.CLEAN, service.standing(testPlayerId), "Standing should not change");
    }

    @Test
    void testBandBoundaryAtUpstanding() {
        ReputationScale scale = service.scale();
        // Get the data object and modify it directly
        world.bentobox.tradewinds.dataobjects.TWPlayerData data = addon.getPlayerDataManager().get(testPlayerId);

        // At upstanding threshold
        data.setReputation(scale.upstanding());
        assertEquals(Standing.UPSTANDING, service.standing(testPlayerId));

        // One point below upstanding should be clean
        data.setReputation(scale.upstanding() - 1);
        assertEquals(Standing.CLEAN, service.standing(testPlayerId));
    }

    @Test
    void testBandBoundaryAtOffender() {
        ReputationScale scale = service.scale();
        world.bentobox.tradewinds.dataobjects.TWPlayerData data = addon.getPlayerDataManager().get(testPlayerId);

        // At or above offender threshold (0) should be CLEAN
        data.setReputation(scale.offender());
        assertEquals(Standing.CLEAN, service.standing(testPlayerId));

        // Below offender threshold (-1) should be OFFENDER
        data.setReputation(scale.offender() - 1);
        assertEquals(Standing.OFFENDER, service.standing(testPlayerId));
    }

    @Test
    void testBandBoundaryAtWanted() {
        ReputationScale scale = service.scale();
        world.bentobox.tradewinds.dataobjects.TWPlayerData data = addon.getPlayerDataManager().get(testPlayerId);

        // One point above wanted (-199) should be offender
        data.setReputation(scale.wanted() + 1);
        assertEquals(Standing.OFFENDER, service.standing(testPlayerId));

        // At or below wanted threshold (-200) should be wanted
        data.setReputation(scale.wanted());
        assertEquals(Standing.WANTED, service.standing(testPlayerId));
    }

    @Test
    void testBandBoundaryAtFugitive() {
        ReputationScale scale = service.scale();
        world.bentobox.tradewinds.dataobjects.TWPlayerData data = addon.getPlayerDataManager().get(testPlayerId);

        // One point above fugitive should be wanted
        data.setReputation(scale.fugitive() + 1);
        assertEquals(Standing.WANTED, service.standing(testPlayerId));

        // At fugitive threshold should be fugitive
        data.setReputation(scale.fugitive());
        assertEquals(Standing.FUGITIVE, service.standing(testPlayerId));
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
            return java.util.Map.of();
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
            return java.util.List.of();
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
