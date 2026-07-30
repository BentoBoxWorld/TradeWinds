package world.bentobox.tradewinds.commands;

import java.util.List;

import org.bukkit.Location;

import world.bentobox.bentobox.api.commands.CompositeCommand;
import world.bentobox.bentobox.api.commands.DelayedTeleportCommand;
import world.bentobox.bentobox.api.user.User;
import world.bentobox.bentobox.util.Util;
import world.bentobox.tradewinds.TradeWinds;

/**
 * Teleports the player to the ocean spawn. TradeWinds has no spawn island (and
 * players own no island until Stage 7), so unlike the core island spawn command
 * this needs no Island object - it goes straight to the world spawn location on
 * the sea surface.
 *
 * @author tastybento
 */
public class TWSpawnCommand extends DelayedTeleportCommand {

    public TWSpawnCommand(CompositeCommand parent) {
        super(parent, "spawn");
    }

    @Override
    public void setup() {
        setPermission("island.spawn");
        setOnlyPlayer(true);
        setDescription("tradewinds.commands.spawn.description");
    }

    @Override
    public boolean execute(User user, String label, List<String> args) {
        Location spawn = getWorld().getSpawnLocation();
        TradeWinds addon = getAddon();
        this.delayCommand(user, () -> Util.teleportAsync(user.getPlayer(), spawn)
                .thenAccept(done -> {
                    user.sendMessage("tradewinds.spawn.teleported");
                    // First-timers get boat + bundle; boat carriers get launched
                    addon.getStarterKit().onSpawnArrival(user.getPlayer());
                }));
        return true;
    }
}
