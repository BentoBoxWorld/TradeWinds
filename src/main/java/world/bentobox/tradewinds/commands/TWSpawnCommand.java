package world.bentobox.tradewinds.commands;

import java.util.List;
import java.util.Optional;

import org.bukkit.Location;

import world.bentobox.bentobox.api.commands.CompositeCommand;
import world.bentobox.bentobox.api.commands.DelayedTeleportCommand;
import world.bentobox.bentobox.api.user.User;
import world.bentobox.bentobox.util.Util;
import world.bentobox.tradewinds.TradeWinds;

/**
 * The door into the ocean - and only the door.
 * <p>
 * This used to be {@code /tw spawn}, a straight teleport to the world spawn.
 * That was harmless while spawn was an empty islet, and became a free warp
 * home to a market the moment spawn was made a working trading post. But
 * simply deleting it locked new players out, and locked anyone in another world
 * out with them.
 * <p>
 * So it is a door with two rules. It refuses to do anything if you are
 * <b>already at sea</b> - travel is the game, and there is no commanding your
 * way across it. And coming in from outside returns you to the water you
 * <b>left</b>, not to spawn, so that hopping to another game mode and back is
 * not a free ride home either. Only a sailor who has never set out starts at
 * the spawn port.
 *
 * @author tastybento
 */
public class TWSpawnCommand extends DelayedTeleportCommand {

    public TWSpawnCommand(CompositeCommand parent) {
        super(parent, "go", "spawn", "sail");
    }

    @Override
    public void setup() {
        setPermission("island.spawn");
        setOnlyPlayer(true);
        setDescription("tradewinds.commands.spawn.description");
    }

    @Override
    public boolean execute(User user, String label, List<String> args) {
        TradeWinds addon = getAddon();
        if (addon.inWorld(user.getWorld())) {
            // Already out there: the way to anywhere is a boat or a warp
            user.sendMessage("tradewinds.spawn.already-at-sea");
            return false;
        }
        Optional<Location> last = addon.getSeaPositionTracker().lastKnown(user.getPlayer());
        Location destination = last.orElseGet(() -> getWorld().getSpawnLocation());
        boolean returning = last.isPresent();
        this.delayCommand(user, () -> Util.teleportAsync(user.getPlayer(), destination).thenAccept(done -> {
            user.sendMessage(returning ? "tradewinds.spawn.resumed" : "tradewinds.spawn.teleported");
            // First-timers get boat + bundle; boat carriers get launched
            addon.getStarterKit().onSpawnArrival(user.getPlayer());
        }));
        return true;
    }
}
