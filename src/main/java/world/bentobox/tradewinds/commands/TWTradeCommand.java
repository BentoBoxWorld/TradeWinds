package world.bentobox.tradewinds.commands;

import java.util.List;
import java.util.Optional;

import world.bentobox.bentobox.api.commands.CompositeCommand;
import world.bentobox.bentobox.api.user.User;
import world.bentobox.tradewinds.TradeWinds;
import world.bentobox.tradewinds.galaxy.IslandSpec;

/**
 * Opens the island market from anywhere inside its protection range - the
 * polite alternative to hunting down a trader on the plaza.
 *
 * @author tastybento
 */
public class TWTradeCommand extends CompositeCommand {

    public TWTradeCommand(CompositeCommand parent) {
        super(parent, "trade");
    }

    @Override
    public void setup() {
        setPermission("island.trade");
        setOnlyPlayer(true);
        setDescription("tradewinds.commands.trade.description");
    }

    @Override
    public boolean execute(User user, String label, List<String> args) {
        TradeWinds addon = getAddon();
        if (!getWorld().equals(user.getWorld())) {
            user.sendMessage("general.errors.wrong-world");
            return false;
        }
        int x = user.getLocation().getBlockX();
        int z = user.getLocation().getBlockZ();
        int range = addon.getSettings().getIslandProtectionRange();
        Optional<IslandSpec> spec = addon.getGalaxyEngine(getWorld().getSeed()).islandsNear(x, z, range).stream()
                .filter(s -> s.distanceSquared(x, z) <= (long) range * range).findFirst();
        if (spec.isEmpty()) {
            user.sendMessage("tradewinds.trade.not-at-market");
            return false;
        }
        addon.getTradeDialog().openMain(user.getPlayer(), spec.get());
        return true;
    }
}
