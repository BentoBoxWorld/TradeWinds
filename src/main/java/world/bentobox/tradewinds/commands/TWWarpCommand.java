package world.bentobox.tradewinds.commands;

import java.util.List;
import java.util.Optional;

import org.bukkit.entity.Boat;

import world.bentobox.bentobox.api.commands.CompositeCommand;
import world.bentobox.bentobox.api.user.User;
import world.bentobox.tradewinds.TradeWinds;
import world.bentobox.tradewinds.galaxy.IslandSpec;

/**
 * Opens the warp dialog. Requires being in a boat inside an island's space -
 * the warp engages from island borders, and this command just saves rowing the
 * last few blocks to the line.
 *
 * @author tastybento
 */
public class TWWarpCommand extends CompositeCommand {

    public TWWarpCommand(CompositeCommand parent) {
        super(parent, "warp");
    }

    @Override
    public void setup() {
        setPermission("island.warp");
        setOnlyPlayer(true);
        setDescription("tradewinds.commands.warp.description");
    }

    @Override
    public boolean execute(User user, String label, List<String> args) {
        TradeWinds addon = getAddon();
        // A castaway asking to warp wants the way out: raise the free
        // re-engage. Before the boat check - castaways may be swimming.
        if (user.getWorld().equals(addon.getNetherWorld())) {
            addon.getIntersticeService().openReEngageDialog(user.getPlayer());
            return true;
        }
        if (!(user.getPlayer().getVehicle() instanceof Boat)) {
            user.sendMessage("tradewinds.warp.not-in-boat");
            return false;
        }
        if (!getWorld().equals(user.getWorld())) {
            user.sendMessage("general.errors.wrong-world");
            return false;
        }
        int x = user.getLocation().getBlockX();
        int z = user.getLocation().getBlockZ();
        int range = addon.getSettings().getIslandDistance();
        Optional<IslandSpec> origin = addon.getGalaxyEngine(getWorld().getSeed()).islandsNear(x, z, range).stream()
                .filter(spec -> spec.distanceSquared(x, z) <= (long) range * range).findFirst();
        if (origin.isEmpty()) {
            user.sendMessage("tradewinds.warp.not-at-island");
            return false;
        }
        addon.getWarpService().openDialog(user.getPlayer(), origin.get());
        return true;
    }
}
