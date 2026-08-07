package world.bentobox.tradewinds.commands;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import world.bentobox.bentobox.api.commands.CompositeCommand;
import world.bentobox.bentobox.api.user.User;
import world.bentobox.bentobox.managers.PlayersManager;
import world.bentobox.tradewinds.CommonTestSetup;
import world.bentobox.tradewinds.TradeWinds;
import world.bentobox.tradewinds.dataobjects.TWPlayerData;
import world.bentobox.tradewinds.travel.RankService;

/**
 * Tests for AdminRankCommand: admin command to inspect and set player ranks.
 *
 * @author tastybento
 */
class AdminRankCommandTest extends CommonTestSetup {

    private TradeWinds addon;
    private AdminRankCommand command;
    private User user;
    private RankService rankService;
    private PlayersManager playersManager;

    @Override
    @BeforeEach
    public void setUp() throws Exception {
        super.setUp();
        addon = mock(TradeWinds.class);
        CompositeCommand parent = mock(CompositeCommand.class);
        when(parent.getAddon()).thenReturn(addon);

        command = new AdminRankCommand(parent);
        user = User.getInstance(mockPlayer);

        playersManager = plugin.getPlayers();
        rankService = mock(RankService.class);
        when(addon.getRankService()).thenReturn(rankService);

        RankService.Rank rank1 = mock(RankService.Rank.class);
        when(rank1.slug()).thenReturn("captain");
        when(rank1.threshold()).thenReturn(10);
        when(rank1.localeKey()).thenReturn("rank.captain");

        when(rankService.rankFor(any(Integer.class))).thenReturn(rank1);
        when(rankService.ladder()).thenReturn(List.of(rank1));

        when(addon.getPlayerDataManager()).thenReturn(mock());
        when(addon.getChartLeaderboard()).thenReturn(mock());
    }

    @Test
    void testExecuteNoArgs() {
        boolean result = command.execute(user, "rank", List.of());
        assertFalse(result, "Should fail with no arguments");
    }

    @Test
    void testExecuteTooManyArgs() {
        boolean result = command.execute(user, "rank", List.of("player", "rank", "extra"));
        assertFalse(result, "Should fail with too many arguments");
    }

    @Test
    void testExecuteUnknownPlayer() {
        when(playersManager.getUUID("Unknown")).thenReturn(null);

        boolean result = command.execute(user, "rank", List.of("Unknown"));

        assertFalse(result, "Should fail for unknown player");
        // verify(user).sendMessage("general.errors.unknown-player", new String[] {"[name]", "Unknown"});
    }

    @Test
    void testExecuteSetRankByNumber() {
        UUID target = UUID.randomUUID();
        when(playersManager.getUUID("Player")).thenReturn(target);
        when(playersManager.getName(target)).thenReturn("Player");

        TWPlayerData data = new TWPlayerData(target.toString());
        when(addon.getPlayerDataManager().get(target)).thenReturn(data);

        boolean result = command.execute(user, "rank", List.of("Player", "10"));

        assertTrue(result, "Should accept numeric rank");
        verify(rankService).setEffectiveCharted(target, 10);
    }

    @Test
    void testExecuteSetRankBySlug() {
        UUID target = UUID.randomUUID();
        when(playersManager.getUUID("Player")).thenReturn(target);
        when(playersManager.getName(target)).thenReturn("Player");

        TWPlayerData data = new TWPlayerData(target.toString());
        when(addon.getPlayerDataManager().get(target)).thenReturn(data);

        RankService.Rank captain = mock(RankService.Rank.class);
        when(captain.threshold()).thenReturn(10);
        when(rankService.bySlug("captain")).thenReturn(Optional.of(captain));

        boolean result = command.execute(user, "rank", List.of("Player", "captain"));

        assertTrue(result, "Should accept rank slug");
        verify(rankService).setEffectiveCharted(target, 10);
    }

    @Test
    void testExecuteSetRankUnknownSlug() {
        UUID target = UUID.randomUUID();
        when(playersManager.getUUID("Player")).thenReturn(target);
        when(rankService.bySlug("unknown")).thenReturn(Optional.empty());

        boolean result = command.execute(user, "rank", List.of("Player", "unknown"));

        assertFalse(result, "Should reject unknown rank slug");
        // verify(user).sendMessage("tradewinds.commands.admin.rank.unknown-rank", "[name]", "unknown");
    }

    @Test
    void testExecuteResetRank() {
        UUID target = UUID.randomUUID();
        when(playersManager.getUUID("Player")).thenReturn(target);
        when(playersManager.getName(target)).thenReturn("Player");

        TWPlayerData data = new TWPlayerData(target.toString());
        when(addon.getPlayerDataManager().get(target)).thenReturn(data);

        boolean result = command.execute(user, "rank", List.of("Player", "reset"));

        assertTrue(result, "Should accept reset");
        verify(rankService).clearAdjustment(target);
    }
}
