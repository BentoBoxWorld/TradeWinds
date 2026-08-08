package world.bentobox.tradewinds.commands;

import java.util.List;

import org.bukkit.Location;

import world.bentobox.bentobox.api.commands.CompositeCommand;
import world.bentobox.bentobox.api.user.User;
import world.bentobox.bentobox.util.Util;
import world.bentobox.tradewinds.TradeWinds;
import world.bentobox.tradewinds.ocean.IslandSpec;

/**
 * Teleports an admin to a trading island by its index in the
 * {@code /twadmin islands} listing (1 = nearest). Loads the target chunk to
 * land on top of the island terrain.
 *
 * @author tastybento
 */
public class AdminTpIslandCommand extends CompositeCommand {

    public AdminTpIslandCommand(CompositeCommand parent) {
        super(parent, "tpisland");
    }

    @Override
    public void setup() {
        setPermission("admin.tpisland");
        setOnlyPlayer(true);
        setParametersHelp("tradewinds.commands.admin.tpisland.parameters");
        setDescription("tradewinds.commands.admin.tpisland.description");
    }

    @Override
    public boolean execute(User user, String label, List<String> args) {
        int index = 1;
        if (!args.isEmpty()) {
            try {
                index = Integer.parseInt(args.get(0));
            } catch (NumberFormatException e) {
                showHelp(this, user);
                return false;
            }
        }
        TradeWinds addon = getAddon();
        int x = getWorld().equals(user.getWorld()) ? user.getLocation().getBlockX() : 0;
        int z = getWorld().equals(user.getWorld()) ? user.getLocation().getBlockZ() : 0;
        List<IslandSpec> list = AdminIslandsCommand.nearest(addon, x, z);
        if (index < 1 || index > list.size()) {
            user.sendMessage("tradewinds.commands.admin.tpisland.unknown");
            return false;
        }
        IslandSpec s = list.get(index - 1);
        // Load the center chunk so we can land on the island surface
        int y = getWorld().getChunkAt(s.centerX() >> 4, s.centerZ() >> 4).getWorld()
                .getHighestBlockYAt(s.centerX(), s.centerZ()) + 1;
        Location loc = new Location(getWorld(), s.centerX() + 0.5, y, s.centerZ() + 0.5);
        Util.teleportAsync(user.getPlayer(), loc).thenAccept(done -> user.sendMessage(
                "tradewinds.commands.admin.tpisland.teleported", "[name]", s.name()));
        return true;
    }
}
