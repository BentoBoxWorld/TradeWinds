package world.bentobox.tradewinds.commands;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import world.bentobox.bentobox.api.commands.CompositeCommand;
import world.bentobox.bentobox.api.localization.TextVariables;
import world.bentobox.bentobox.api.user.User;
import world.bentobox.bentobox.util.Util;
import world.bentobox.tradewinds.TradeWinds;
import world.bentobox.tradewinds.travel.IntersticeService;

/**
 * Rig a player's next warp to fail, dropping them into the interstice.
 * <p>
 * Written because a 5% failure chance is miserable to reproduce on demand, and
 * the interstice needs testing far more often than it happens. It is also a
 * live tool: an admin can put a particular sailor into the dark to liven up a
 * session. Running it again on the same player clears the flag, and the flag is
 * consumed by their next warp either way - it can never sit forgotten on
 * somebody's account.
 *
 * @author tastybento
 */
public class AdminWarpFailCommand extends CompositeCommand {

    public AdminWarpFailCommand(CompositeCommand parent) {
        super(parent, "warpfail");
    }

    @Override
    public void setup() {
        setPermission("admin.warpfail");
        setOnlyPlayer(false);
        setParametersHelp("tradewinds.commands.admin.warpfail.parameters");
        setDescription("tradewinds.commands.admin.warpfail.description");
    }

    @Override
    public boolean execute(User user, String label, List<String> args) {
        if (args.size() != 1) {
            showHelp(this, user);
            return false;
        }
        UUID target = getPlayers().getUUID(args.get(0));
        if (target == null) {
            user.sendMessage("general.errors.unknown-player", TextVariables.NAME, args.get(0));
            return false;
        }
        String name = getPlayers().getName(target);
        IntersticeService interstice = ((TradeWinds) getAddon()).getIntersticeService();
        // Toggle, so the same command undoes a rig set by mistake
        if (interstice.isRigged(target)) {
            interstice.clearRig(target);
            user.sendMessage("tradewinds.commands.admin.warpfail.cleared", TextVariables.NAME, name);
            return true;
        }
        interstice.rigNextWarp(target);
        user.sendMessage("tradewinds.commands.admin.warpfail.rigged", TextVariables.NAME, name);
        return true;
    }

    @Override
    public Optional<List<String>> tabComplete(User user, String alias, List<String> args) {
        return Optional.of(Util.getOnlinePlayerList(user));
    }
}
