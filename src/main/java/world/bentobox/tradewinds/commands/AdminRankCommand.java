package world.bentobox.tradewinds.commands;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import world.bentobox.bentobox.api.commands.CompositeCommand;
import world.bentobox.bentobox.api.localization.TextVariables;
import world.bentobox.bentobox.api.user.User;
import world.bentobox.bentobox.util.Util;
import world.bentobox.tradewinds.TradeWinds;
import world.bentobox.tradewinds.travel.RankService;

/**
 * {@code /twadmin rank <player> [rank|islands|reset]} - inspect or set a
 * player's Seafarer rank.
 * <p>
 * Rank is derived from the charted-island count, so "setting a rank" stores
 * the DIFFERENCE from the player's real chart as a persistent adjustment:
 * promotion and demotion both work, real charting keeps counting on top, and
 * the claim gate, `/tw rank` and the leaderboard all agree because they all
 * read the same effective count. Written for testing the Stage 7 claim gate
 * without sailing 36 islands, and kept for live servers - rewarding an event
 * winner, or undoing a mistaken reward.
 *
 * @author tastybento
 */
public class AdminRankCommand extends CompositeCommand {

    public AdminRankCommand(CompositeCommand parent) {
        super(parent, "rank");
    }

    @Override
    public void setup() {
        setPermission("admin.rank");
        setOnlyPlayer(false);
        setParametersHelp("tradewinds.commands.admin.rank.parameters");
        setDescription("tradewinds.commands.admin.rank.description");
    }

    @Override
    public boolean execute(User user, String label, List<String> args) {
        if (args.isEmpty() || args.size() > 2) {
            showHelp(this, user);
            return false;
        }
        UUID target = getPlayers().getUUID(args.get(0));
        if (target == null) {
            user.sendMessage("general.errors.unknown-player", TextVariables.NAME, args.get(0));
            return false;
        }
        TradeWinds addon = getAddon();
        RankService ranks = addon.getRankService();
        if (args.size() == 1) {
            report(user, addon, ranks, target, args.get(0));
            return true;
        }
        String wanted = args.get(1);
        if (wanted.equalsIgnoreCase("reset")) {
            ranks.clearAdjustment(target);
        } else if (wanted.chars().allMatch(Character::isDigit)) {
            ranks.setEffectiveCharted(target, Integer.parseInt(wanted));
        } else {
            Optional<RankService.Rank> rank = ranks.bySlug(wanted);
            if (rank.isEmpty()) {
                user.sendMessage("tradewinds.commands.admin.rank.unknown-rank", TextVariables.NAME, wanted);
                return false;
            }
            ranks.setEffectiveCharted(target, rank.get().threshold());
        }
        addon.getChartLeaderboard().refresh(target);
        report(user, addon, ranks, target, args.get(0));
        return true;
    }

    private void report(User user, TradeWinds addon, RankService ranks, UUID target, String name) {
        var data = addon.getPlayerDataManager().get(target);
        user.sendMessage("tradewinds.commands.admin.rank.current",
                TextVariables.NAME, getPlayers().getName(target).isEmpty() ? name : getPlayers().getName(target),
                "[rank]", user.getTranslation(ranks.rankFor(data.effectiveCharted()).localeKey()),
                TextVariables.NUMBER, String.valueOf(data.effectiveCharted()),
                "[real]", String.valueOf(data.getChartedIslands().size()),
                "[bonus]", String.valueOf(data.getChartedBonus()));
    }

    @Override
    public Optional<List<String>> tabComplete(User user, String alias, List<String> args) {
        // args is the RAW argument chain including this command's own label:
        // "/twadmin rank <partial>" arrives as ["rank", "<partial>"]
        if (args.size() <= 2) {
            return Optional.of(Util.getOnlinePlayerList(user));
        }
        TradeWinds addon = getAddon();
        List<String> options = new ArrayList<>(
                addon.getRankService().ladder().stream().map(RankService.Rank::slug).toList());
        options.add("reset");
        return Optional.of(Util.tabLimit(options, args.get(args.size() - 1)));
    }
}
