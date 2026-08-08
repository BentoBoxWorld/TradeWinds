package world.bentobox.tradewinds.commands;

import java.util.List;

import world.bentobox.bentobox.api.commands.CompositeCommand;
import world.bentobox.bentobox.api.localization.TextVariables;
import world.bentobox.bentobox.api.user.User;
import world.bentobox.tradewinds.TradeWinds;
import world.bentobox.tradewinds.economy.Money;
import world.bentobox.tradewinds.travel.IsletClaimService;

/**
 * {@code /tw claim} - claim the wild islet you are standing on. The gate is
 * Seafarer rank (earned by charting) and price (config); everything else is
 * ordinary BentoBox island life from the moment it succeeds.
 *
 * @author tastybento
 */
public class TWClaimCommand extends CompositeCommand {

    public TWClaimCommand(CompositeCommand parent) {
        super(parent, "claim");
    }

    @Override
    public void setup() {
        setPermission("island.claim");
        setOnlyPlayer(true);
        setDescription("tradewinds.commands.claim.description");
    }

    @Override
    public boolean execute(User user, String label, List<String> args) {
        TradeWinds addon = getAddon();
        if (!user.getWorld().equals(addon.getOverWorld())) {
            user.sendMessage("tradewinds.claim.not-on-islet");
            return false;
        }
        IsletClaimService.Result result = addon.getIsletClaimService().claim(user.getPlayer());
        if (result.success()) {
            user.sendMessage("tradewinds.claim.success",
                    "[price]", Money.format(addon, addon.getSettings().getClaimPrice()));
            return true;
        }
        switch (result.refusal()) {
        case NOT_ON_ISLET -> user.sendMessage("tradewinds.claim.not-on-islet");
        case ALREADY_CLAIMED -> user.sendMessage("tradewinds.claim.already-claimed");
        case HAS_ISLAND -> user.sendMessage("tradewinds.claim.has-island");
        case RANK_TOO_LOW -> {
            var ranks = addon.getRankService();
            int needed = ranks.claimThreshold();
            user.sendMessage("tradewinds.claim.rank-too-low",
                    "[rank]", user.getTranslation(ranks.rankFor(needed).localeKey()),
                    TextVariables.NUMBER, String.valueOf(needed),
                    "[charted]", String.valueOf(ranks.chartedCount(user.getUniqueId())));
        }
        case NO_ECONOMY -> user.sendMessage("tradewinds.claim.no-economy");
        case CANNOT_AFFORD -> user.sendMessage("tradewinds.claim.cannot-afford",
                "[price]", Money.format(addon, addon.getSettings().getClaimPrice()));
        case WATERS_TAKEN -> user.sendMessage("tradewinds.claim.waters-taken");
        }
        return false;
    }
}
