package world.bentobox.tradewinds.commands;

import java.util.List;

import world.bentobox.bentobox.api.commands.CompositeCommand;
import world.bentobox.bentobox.api.user.User;
import world.bentobox.tradewinds.TradeWinds;

/**
 * Hands the player a Star Chart: a live map of their charted islands, always
 * centered on the holder - the rower's navigation aid.
 *
 * @author tastybento
 */
public class TWStarChartCommand extends CompositeCommand {

    public TWStarChartCommand(CompositeCommand parent) {
        super(parent, "starchart");
    }

    @Override
    public void setup() {
        setPermission("island.starchart");
        setOnlyPlayer(true);
        setDescription("tradewinds.commands.starchart.description");
    }

    @Override
    public boolean execute(User user, String label, List<String> args) {
        TradeWinds addon = getAddon();
        addon.getStarChartService().give(user.getPlayer());
        user.sendMessage("tradewinds.chart.starchart-given");
        return true;
    }
}
