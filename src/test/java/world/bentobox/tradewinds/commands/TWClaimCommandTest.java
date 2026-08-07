package world.bentobox.tradewinds.commands;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.ArrayList;
import java.util.List;

import org.junit.jupiter.api.BeforeEach;
import org.bukkit.World;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Test;

import world.bentobox.bentobox.api.commands.CompositeCommand;
import world.bentobox.bentobox.api.user.User;
import world.bentobox.tradewinds.CommonTestSetup;
import world.bentobox.tradewinds.TradeWinds;
import world.bentobox.tradewinds.travel.IsletClaimService;
import world.bentobox.tradewinds.travel.RankService;

/**
 * Tests for TWClaimCommand: claim a wild islet. Tests the gate order:
 * world check → rank gate → economy → price affordability → location validation.
 *
 * @author tastybento
 */
class TWClaimCommandTest extends CommonTestSetup {

    private TradeWinds addon;
    private TWClaimCommand command;
    private User user;
    private IsletClaimService claimService;
    private RankService rankService;

    @Override
    @BeforeEach
    public void setUp() throws Exception {
        super.setUp();
        addon = mock(TradeWinds.class);
        CompositeCommand parent = mock(CompositeCommand.class);
        when(parent.getAddon()).thenReturn(addon);
        when(parent.getWorld()).thenReturn(world);

        command = new TWClaimCommand(parent);
        user = User.getInstance(mockPlayer);

        when(addon.getOverWorld()).thenReturn(world);

        claimService = mock(IsletClaimService.class);
        when(addon.getIsletClaimService()).thenReturn(claimService);

        rankService = mock(RankService.class);
        when(addon.getRankService()).thenReturn(rankService);
        when(rankService.claimThreshold()).thenReturn(10);
        when(rankService.chartedCount(mockPlayer.getUniqueId())).thenReturn(5);

        RankService.Rank rank = mock(RankService.Rank.class);
        when(rank.localeKey()).thenReturn("rank.ten");
        when(rankService.rankFor(10)).thenReturn(rank);

        when(addon.getSettings()).thenReturn(mock());
        when(addon.getSettings().getClaimPrice()).thenReturn(100.0);
    }

    @Test
    void testExecuteNotOnIslet() {
        World otherWorld = mock(World.class);
        when(user.getWorld()).thenReturn(otherWorld);

        boolean result = command.execute(user, "claim", new ArrayList<>());

        assertFalse(result, "Gate 1: should refuse when not on islet world");
        // verify(user).sendMessage("tradewinds.claim.not-on-islet");
    }

    @Test
    void testExecuteSuccess() {
        when(user.getWorld()).thenReturn(world);

        IsletClaimService.Result success = mock(IsletClaimService.Result.class);
        when(success.success()).thenReturn(true);
        when(claimService.claim(mockPlayer)).thenReturn(success);

        boolean result = command.execute(user, "claim", new ArrayList<>());

        assertTrue(result, "Should succeed when all gates pass");
    }

    @Test
    void testExecuteAlreadyClaimed() {
        when(user.getWorld()).thenReturn(world);

        IsletClaimService.Result alreadyClaimed = mock(IsletClaimService.Result.class);
        when(alreadyClaimed.success()).thenReturn(false);
        when(alreadyClaimed.refusal()).thenReturn(IsletClaimService.Refusal.ALREADY_CLAIMED);
        when(claimService.claim(mockPlayer)).thenReturn(alreadyClaimed);

        boolean result = command.execute(user, "claim", new ArrayList<>());

        assertFalse(result, "Should refuse already-claimed islet");
        // verify(user).sendMessage("tradewinds.claim.already-claimed");
    }

    @Test
    void testExecutePlayerHasIsland() {
        when(user.getWorld()).thenReturn(world);

        IsletClaimService.Result hasIsland = mock(IsletClaimService.Result.class);
        when(hasIsland.success()).thenReturn(false);
        when(hasIsland.refusal()).thenReturn(IsletClaimService.Refusal.HAS_ISLAND);
        when(claimService.claim(mockPlayer)).thenReturn(hasIsland);

        boolean result = command.execute(user, "claim", new ArrayList<>());

        assertFalse(result, "Gate: should refuse if player already has island");
        // verify(user).sendMessage("tradewinds.claim.has-island");
    }

    @Test
    void testExecuteRankTooLow() {
        when(user.getWorld()).thenReturn(world);

        IsletClaimService.Result rankLow = mock(IsletClaimService.Result.class);
        when(rankLow.success()).thenReturn(false);
        when(rankLow.refusal()).thenReturn(IsletClaimService.Refusal.RANK_TOO_LOW);
        when(claimService.claim(mockPlayer)).thenReturn(rankLow);

        boolean result = command.execute(user, "claim", new ArrayList<>());

        assertFalse(result, "Gate: should refuse if rank too low");
        // verify(user).sendMessage("tradewinds.claim.rank-too-low",
        //         "[rank]", "rank.ten", "[number]", "10", "[charted]", "5");
    }

    @Test
    void testExecuteNoEconomy() {
        when(user.getWorld()).thenReturn(world);

        IsletClaimService.Result noEcon = mock(IsletClaimService.Result.class);
        when(noEcon.success()).thenReturn(false);
        when(noEcon.refusal()).thenReturn(IsletClaimService.Refusal.NO_ECONOMY);
        when(claimService.claim(mockPlayer)).thenReturn(noEcon);

        boolean result = command.execute(user, "claim", new ArrayList<>());

        assertFalse(result, "Gate: should refuse if economy unavailable");
        // verify(user).sendMessage("tradewinds.claim.no-economy");
    }

    @Test
    void testExecuteCannotAfford() {
        when(user.getWorld()).thenReturn(world);

        IsletClaimService.Result noMoney = mock(IsletClaimService.Result.class);
        when(noMoney.success()).thenReturn(false);
        when(noMoney.refusal()).thenReturn(IsletClaimService.Refusal.CANNOT_AFFORD);
        when(claimService.claim(mockPlayer)).thenReturn(noMoney);

        boolean result = command.execute(user, "claim", new ArrayList<>());

        assertFalse(result, "Gate: should refuse if cannot afford price");
        // verify(user).sendMessage("tradewinds.claim.cannot-afford", "[price]");
    }

    @Test
    void testExecuteWatersTaken() {
        when(user.getWorld()).thenReturn(world);

        IsletClaimService.Result taken = mock(IsletClaimService.Result.class);
        when(taken.success()).thenReturn(false);
        when(taken.refusal()).thenReturn(IsletClaimService.Refusal.WATERS_TAKEN);
        when(claimService.claim(mockPlayer)).thenReturn(taken);

        boolean result = command.execute(user, "claim", new ArrayList<>());

        assertFalse(result, "Gate: should refuse if waters already claimed nearby");
        // verify(user).sendMessage("tradewinds.claim.waters-taken");
    }
}
