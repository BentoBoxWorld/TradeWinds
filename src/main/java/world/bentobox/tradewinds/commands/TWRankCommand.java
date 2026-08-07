package world.bentobox.tradewinds.commands;

import java.util.List;

import world.bentobox.bentobox.api.commands.CompositeCommand;
import world.bentobox.bentobox.api.localization.TextVariables;
import world.bentobox.bentobox.api.user.User;
import world.bentobox.tradewinds.TradeWinds;
import world.bentobox.tradewinds.travel.ChartLeaderboard;
import world.bentobox.tradewinds.travel.RankService;

/**
 * {@code /tw rank} - your Seafarer rank, the next rung, and the top ten
 * sailors by islands charted. The kid-readable face of the rank ladder;
 * scoreboard plugins get the same numbers via placeholders.
 *
 * @author tastybento
 */
public class TWRankCommand extends CompositeCommand {

    /** Rows shown from the leaderboard. */
    static final int TOP = 10;

    public TWRankCommand(CompositeCommand parent) {
        super(parent, "rank", "ranks");
    }

    @Override
    public void setup() {
        setPermission("island.rank");
        setOnlyPlayer(true);
        setDescription("tradewinds.commands.rank.description");
    }

    @Override
    public boolean execute(User user, String label, List<String> args) {
        TradeWinds addon = getAddon();
        RankService ranks = addon.getRankService();
        int charted = ranks.chartedCount(user.getUniqueId());
        user.sendMessage("tradewinds.rank.own",
                "[rank]", user.getTranslation(ranks.rankFor(charted).localeKey()),
                TextVariables.NUMBER, String.valueOf(charted));
        ranks.next(charted).ifPresentOrElse(
                next -> user.sendMessage("tradewinds.rank.next",
                        "[rank]", user.getTranslation(next.localeKey()),
                        TextVariables.NUMBER, String.valueOf(next.threshold() - charted)),
                () -> user.sendMessage("tradewinds.rank.top-of-ladder"));
        List<ChartLeaderboard.Entry> board = addon.getChartLeaderboard().top(TOP);
        if (board.isEmpty()) {
            return true;
        }
        user.sendMessage("tradewinds.rank.board-header");
        for (int place = 1; place <= board.size(); place++) {
            ChartLeaderboard.Entry entry = board.get(place - 1);
            String name = addon.getPlayers().getName(entry.playerId());
            user.sendMessage("tradewinds.rank.board-entry",
                    "[place]", String.valueOf(place),
                    TextVariables.NAME, name == null || name.isEmpty() ? "?" : name,
                    TextVariables.NUMBER, String.valueOf(entry.charted()),
                    "[rank]", user.getTranslation(ranks.rankFor(entry.charted()).localeKey()));
        }
        return true;
    }
}
