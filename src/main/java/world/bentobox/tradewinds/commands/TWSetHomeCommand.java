package world.bentobox.tradewinds.commands;

import java.util.List;
import java.util.Optional;

import world.bentobox.bentobox.api.commands.CompositeCommand;
import world.bentobox.bentobox.api.user.User;
import world.bentobox.bentobox.database.objects.Island;
import world.bentobox.tradewinds.TradeWinds;

/**
 * {@code /tw sethome} - move your home point around your claimed islet.
 * <p>
 * Works only while standing INSIDE your own island's protection range, owner
 * and team members alike. That boundary is what keeps homes honest under the
 * no-teleport rule: you can arrange your base, but you cannot plant a home
 * somewhere you would then need a teleport to reach.
 *
 * @author tastybento
 */
public class TWSetHomeCommand extends CompositeCommand {

    public TWSetHomeCommand(CompositeCommand parent) {
        super(parent, "sethome");
    }

    @Override
    public void setup() {
        setPermission("island.sethome");
        setOnlyPlayer(true);
        setDescription("tradewinds.commands.sethome.description");
    }

    /**
     * The island the player belongs to, if they are standing inside its
     * protection range right now - the shared gate for {@code /tw sethome}
     * and {@code /tw home}. Owner or team member, no difference.
     */
    static Optional<Island> islandUnderfoot(TradeWinds addon, User user) {
        Island island = addon.getIslands().getIsland(addon.getOverWorld(), user.getUniqueId());
        if (island == null || !island.getMemberSet().contains(user.getUniqueId())) {
            return Optional.empty();
        }
        return island.onIsland(user.getLocation()) ? Optional.of(island) : Optional.empty();
    }

    @Override
    public boolean execute(User user, String label, List<String> args) {
        TradeWinds addon = getAddon();
        if (addon.getIslands().getIsland(addon.getOverWorld(), user.getUniqueId()) == null) {
            user.sendMessage("tradewinds.home.no-island");
            return false;
        }
        Optional<Island> island = islandUnderfoot(addon, user);
        if (island.isEmpty()) {
            user.sendMessage("tradewinds.home.not-on-island");
            return false;
        }
        addon.getIslands().setHomeLocation(user, user.getLocation());
        user.sendMessage("tradewinds.home.set");
        return true;
    }
}
