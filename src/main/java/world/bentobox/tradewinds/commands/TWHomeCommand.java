package world.bentobox.tradewinds.commands;

import java.util.List;
import java.util.Optional;

import world.bentobox.bentobox.api.commands.CompositeCommand;
import world.bentobox.bentobox.api.user.User;
import world.bentobox.bentobox.database.objects.Island;
import world.bentobox.tradewinds.TradeWinds;

/**
 * {@code /tw home} - step to your home point, while already on your island.
 * <p>
 * This is NOT a way home across the sea: it refuses anywhere but inside your
 * own island's protection range (owner or team member), so the only travel it
 * saves is the walk up your own beach. Getting to the island in the first
 * place is still sailing or a warp - the no-teleport rule stands.
 *
 * @author tastybento
 */
public class TWHomeCommand extends CompositeCommand {

    public TWHomeCommand(CompositeCommand parent) {
        super(parent, "home");
    }

    @Override
    public void setup() {
        setPermission("island.home");
        setOnlyPlayer(true);
        setDescription("tradewinds.commands.home.description");
    }

    @Override
    public boolean execute(User user, String label, List<String> args) {
        TradeWinds addon = getAddon();
        if (addon.getIslands().getIsland(addon.getOverWorld(), user.getUniqueId()) == null) {
            user.sendMessage("tradewinds.home.no-island");
            return false;
        }
        Optional<Island> island = TWSetHomeCommand.islandUnderfoot(addon, user);
        if (island.isEmpty()) {
            user.sendMessage("tradewinds.home.not-on-island");
            return false;
        }
        addon.getIslands().homeTeleportAsync(addon.getOverWorld(), user.getPlayer())
                .thenAccept(done -> {
                    if (Boolean.TRUE.equals(done)) {
                        user.sendMessage("tradewinds.home.teleported");
                    }
                });
        return true;
    }
}
