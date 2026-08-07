package world.bentobox.tradewinds.travel;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.Set;
import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import world.bentobox.tradewinds.CommonTestSetup;
import world.bentobox.tradewinds.Settings;
import world.bentobox.tradewinds.TradeWinds;
import world.bentobox.tradewinds.dataobjects.PlayerDataManager;
import world.bentobox.tradewinds.dataobjects.TWPlayerData;

/**
 * Tests the Seafarer rank ladder: thresholds, boundaries, the next rung, and
 * the fail-closed claim gate.
 *
 * @author tastybento
 */
class RankServiceTest extends CommonTestSetup {

    private TradeWinds addon;
    private Settings settings;
    private RankService service;

    @Override
    @BeforeEach
    public void setUp() throws Exception {
        super.setUp();
        addon = mock(TradeWinds.class);
        settings = new Settings();
        when(addon.getSettings()).thenReturn(settings);
        service = new RankService(addon);
    }

    @Test
    void testLadderBoundaries() {
        // The starter cluster pre-charts 5 - still a Deck Hand
        assertEquals("deck-hand", service.rankFor(0).slug());
        assertEquals("deck-hand", service.rankFor(5).slug());
        // The first self-charted island is the first promotion
        assertEquals("cabin-scout", service.rankFor(6).slug());
        assertEquals("cabin-scout", service.rankFor(7).slug());
        // Exact thresholds promote
        assertEquals("tide-captain", service.rankFor(36).slug());
        assertEquals("archipelago-ace", service.rankFor(35).slug());
        // Nothing above the top
        assertEquals("mythic-mariner", service.rankFor(100).slug());
        assertEquals("mythic-mariner", service.rankFor(5000).slug());
    }

    @Test
    void testNextRung() {
        assertEquals("cabin-scout", service.next(0).orElseThrow().slug());
        assertEquals("cabin-scout", service.next(5).orElseThrow().slug());
        assertEquals("coast-finder", service.next(6).orElseThrow().slug());
        assertEquals("mythic-mariner", service.next(99).orElseThrow().slug());
        assertTrue(service.next(100).isEmpty());
    }

    @Test
    void testLocaleKey() {
        assertEquals("tradewinds.rank.deck-hand", service.rankFor(0).localeKey());
    }

    @Test
    void testClaimThresholdDefault() {
        // Default gate: Tide Captain, 36 charted
        assertEquals(36, service.claimThreshold());
    }

    @Test
    void testClaimThresholdFailsClosedOnTypo() {
        settings.setClaimMinimumRank("tide-kaptain");
        // A slug that names no rung requires the TOP rank, not none
        assertEquals(100, service.claimThreshold());
        verify(addon).logError(any(String.class));
    }

    @Test
    void testChartedCountReadsPlayerData() {
        TWPlayerData data = sailorWith(Set.of("0,0", "1,1", "2,2"));
        assertEquals(3, service.chartedCount(uuid));
        assertEquals(0, data.getChartedBonus());
    }

    @Test
    void testAdminPromotionStoresTheDifference() {
        TWPlayerData data = sailorWith(Set.of("0,0", "1,1", "2,2"));
        // Make them a Tide Captain for claim testing
        service.setEffectiveCharted(uuid, 36);
        assertEquals(33, data.getChartedBonus());
        assertEquals(36, service.chartedCount(uuid));
        assertEquals("tide-captain", service.rankFor(service.chartedCount(uuid)).slug());
        // Real charting keeps counting on top of the adjustment
        data.getChartedIslands().add("5,5");
        assertEquals(37, service.chartedCount(uuid));
    }

    @Test
    void testAdminDemotionClampsAtZero() {
        TWPlayerData data = sailorWith(new java.util.HashSet<>(Set.of("0,0", "1,1", "2,2")));
        service.setEffectiveCharted(uuid, 0);
        assertEquals(-3, data.getChartedBonus());
        assertEquals(0, service.chartedCount(uuid));
        assertEquals("deck-hand", service.rankFor(service.chartedCount(uuid)).slug());
    }

    @Test
    void testResetReturnsToTheRealChart() {
        TWPlayerData data = sailorWith(Set.of("0,0", "1,1", "2,2"));
        service.setEffectiveCharted(uuid, 100);
        service.clearAdjustment(uuid);
        assertEquals(0, data.getChartedBonus());
        assertEquals(3, service.chartedCount(uuid));
    }

    private TWPlayerData sailorWith(Set<String> cells) {
        PlayerDataManager pdm = mock(PlayerDataManager.class);
        when(addon.getPlayerDataManager()).thenReturn(pdm);
        TWPlayerData data = new TWPlayerData(uuid.toString());
        data.setChartedIslands(new java.util.HashSet<>(cells));
        when(pdm.get(any(UUID.class))).thenReturn(data);
        return data;
    }
}
