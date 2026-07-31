package world.bentobox.tradewinds.commands;

import java.util.List;

import world.bentobox.bentobox.api.commands.ConfirmableCommand;
import world.bentobox.bentobox.api.commands.CompositeCommand;
import world.bentobox.bentobox.api.localization.TextVariables;
import world.bentobox.bentobox.api.user.User;
import world.bentobox.tradewinds.TradeWinds;
import world.bentobox.tradewinds.dataobjects.TWPlayerData;

/**
 * The destitute's mercy: restart the trading career - balance back to the
 * starting amount, a fresh starter kit at spawn, chart kept (knowledge is not
 * wealth). Limited uses so it cannot be farmed.
 *
 * @author tastybento
 */
public class TWRestartCommand extends ConfirmableCommand {

    public TWRestartCommand(CompositeCommand parent) {
        super(parent, "restart");
    }

    @Override
    public void setup() {
        setPermission("island.restart");
        setOnlyPlayer(true);
        setDescription("tradewinds.commands.restart.description");
    }

    @Override
    public boolean execute(User user, String label, List<String> args) {
        TradeWinds addon = getAddon();
        TWPlayerData data = addon.getPlayerDataManager().get(user.getUniqueId());
        int max = addon.getSettings().getMaxRestarts();
        if (max >= 0 && data.getRestartsUsed() >= max) {
            user.sendMessage("tradewinds.restart.none-left", TextVariables.NUMBER, String.valueOf(max));
            return false;
        }
        askConfirmation(user, user.getTranslation("tradewinds.restart.confirm"), () -> restart(addon, user, data));
        return true;
    }

    private void restart(TradeWinds addon, User user, TWPlayerData data) {
        data.setRestartsUsed(data.getRestartsUsed() + 1);
        // Zero the balance; the starter kit deposit brings it back to starting
        addon.getPlugin().getVault().ifPresent(vault -> vault.withdraw(user, vault.getBalance(user)));
        data.setStarterKitGiven(false);
        addon.getPlayerDataManager().save(user.getUniqueId());
        // Back to the spawn islet with a fresh kit
        user.getPlayer().teleport(addon.getOverWorld().getSpawnLocation());
        addon.getStarterKit().onSpawnArrival(user.getPlayer());
        int max = addon.getSettings().getMaxRestarts();
        user.sendMessage("tradewinds.restart.done", TextVariables.NUMBER,
                max < 0 ? "unlimited" : String.valueOf(max - data.getRestartsUsed()));
    }
}
