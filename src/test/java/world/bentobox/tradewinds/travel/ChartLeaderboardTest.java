package world.bentobox.tradewinds.travel;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.HashSet;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import world.bentobox.tradewinds.CommonTestSetup;
import world.bentobox.tradewinds.TradeWinds;
import world.bentobox.tradewinds.api.events.IslandChartedEvent;
import world.bentobox.tradewinds.dataobjects.PlayerDataManager;
import world.bentobox.tradewinds.dataobjects.TWPlayerData;

/**
 * Tests the charted-islands leaderboard: event-driven updates and ordering.
 *
 * @author tastybento
 */
class ChartLeaderboardTest extends CommonTestSetup {

    private TradeWinds addon;
    private PlayerDataManager pdm;
    private ChartLeaderboard leaderboard;

    @Override
    @BeforeEach
    public void setUp() throws Exception {
        super.setUp();
        addon = mock(TradeWinds.class);
        pdm = mock(PlayerDataManager.class);
        when(addon.getPlayerDataManager()).thenReturn(pdm);
        leaderboard = new ChartLeaderboard(addon);
    }

    private UUID sailor(int charted) {
        UUID id = UUID.randomUUID();
        TWPlayerData data = new TWPlayerData(id.toString());
        Set<String> cells = IntStream.range(0, charted).mapToObj(i -> i + "," + i)
                .collect(Collectors.toCollection(HashSet::new));
        data.setChartedIslands(cells);
        when(pdm.get(id)).thenReturn(data);
        leaderboard.onCharted(new IslandChartedEvent(id, null));
        return id;
    }

    @Test
    void testTopOrdersMostCharted() {
        UUID low = sailor(3);
        UUID high = sailor(40);
        UUID mid = sailor(12);
        var top = leaderboard.top(10);
        assertEquals(3, top.size());
        assertEquals(high, top.get(0).playerId());
        assertEquals(40, top.get(0).charted());
        assertEquals(mid, top.get(1).playerId());
        assertEquals(low, top.get(2).playerId());
    }

    @Test
    void testTopIsCapped() {
        for (int i = 0; i < 15; i++) {
            sailor(i + 1);
        }
        assertEquals(10, leaderboard.top(10).size());
        // Best first, and the cap keeps the best, not the first-seen
        assertEquals(15, leaderboard.top(10).get(0).charted());
        assertEquals(6, leaderboard.top(10).get(9).charted());
    }

    @Test
    void testRechartingUpdatesInPlace() {
        UUID id = sailor(4);
        // The same sailor charts more: count replaces, no duplicate row
        TWPlayerData data = new TWPlayerData(id.toString());
        data.setChartedIslands(Set.of("9,9", "8,8", "7,7", "6,6", "5,5"));
        when(pdm.get(id)).thenReturn(data);
        leaderboard.onCharted(new IslandChartedEvent(id, null));
        var top = leaderboard.top(10);
        assertEquals(1, top.size());
        assertEquals(5, top.get(0).charted());
    }

    @Test
    void testEmptyBoard() {
        assertTrue(leaderboard.top(10).isEmpty());
    }

    @Test
    void testPlaceholderRegistration() {
        RankService ranks = mock(RankService.class);
        when(addon.getPlugin()).thenReturn(plugin);
        leaderboard.registerPlaceholders(ranks);
        // rank + charted + 10 names + 10 counts
        verify(phm, times(22))
                .registerPlaceholder(any(TradeWinds.class), any(String.class), any());
    }
}
