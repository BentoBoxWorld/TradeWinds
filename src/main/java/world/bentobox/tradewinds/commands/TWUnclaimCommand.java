package world.bentobox.tradewinds.commands;

import java.util.List;

import world.bentobox.bentobox.api.commands.CompositeCommand;
import world.bentobox.bentobox.api.commands.ConfirmableCommand;
import world.bentobox.bentobox.api.user.User;
import world.bentobox.bentobox.database.objects.Island;
import world.bentobox.tradewinds.TradeWinds;
import world.bentobox.tradewinds.travel.IsletClaimService;

/**
 * {@code /tw unclaim} - give a claimed islet back to the wild.
 * <p>
 * Owner only, and deliberately a pain (ruled 2026-08-04): the team must be
 * emptied first - kicking your crew is part of the decision, not a side
 * effect. No refund. The island record is removed the admin-unregister way -
 * every block stays exactly as it was left - and the islet becomes claimable
 * by the next sailor who lands on it.
 *
 * @author tastybento
 */
public class TWUnclaimCommand extends ConfirmableCommand {

    public TWUnclaimCommand(CompositeCommand parent) {
        super(parent, "unclaim");
    }

    @Override
    public void setup() {
        setPermission("island.unclaim");
        setOnlyPlayer(true);
        setDescription("tradewinds.commands.unclaim.description");
    }

    @Override
    public boolean execute(User user, String label, List<String> args) {
        TradeWinds addon = getAddon();
        Island island = addon.getIslands().getIsland(addon.getOverWorld(), user.getUniqueId());
        if (island == null) {
            user.sendMessage("tradewinds.home.no-island");
            return false;
        }
        if (!user.getUniqueId().equals(island.getOwner())) {
            user.sendMessage("tradewinds.unclaim.owner-only");
            return false;
        }
        if (island.getMetaData(IsletClaimService.META_CLAIM).isEmpty()) {
            // Not a claimed islet (the spawn island, say): not ours to drop
            user.sendMessage("tradewinds.unclaim.not-a-claim");
            return false;
        }
        if (island.getMemberSet().size() > 1) {
            user.sendMessage("tradewinds.unclaim.team-first");
            return false;
        }
        askConfirmation(user, user.getTranslation("tradewinds.unclaim.confirm"), () -> {
            // Unregister semantics: record gone, grid freed, every block
            // stays. The islet is claimable again the moment this returns.
            addon.getIslands().hardDeleteIsland(island);
            user.sendMessage("tradewinds.unclaim.done");
        });
        return true;
    }
}
