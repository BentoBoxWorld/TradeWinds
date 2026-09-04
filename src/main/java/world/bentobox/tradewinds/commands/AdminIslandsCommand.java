package world.bentobox.tradewinds.commands;

import java.util.Comparator;
import java.util.List;

import world.bentobox.bentobox.api.commands.CompositeCommand;
import world.bentobox.bentobox.api.localization.TextVariables;
import world.bentobox.bentobox.api.user.User;
import world.bentobox.tradewinds.TradeWinds;
import world.bentobox.tradewinds.ocean.IslandSpec;

/**
 * Lists the trading islands nearest the caller (ocean-engine query, so it
 * works before any chunks are generated - this is the discovery tool for
 * admins and testing). The index shown is the argument for
 * {@code /twadmin tpisland <index>}.
 *
 * @author tastybento
 */
public class AdminIslandsCommand extends CompositeCommand {

    /** How far out to search, in blocks. */
    static final int SEARCH_RADIUS = 50_000;
    static final int COUNT = 10;

    public AdminIslandsCommand(CompositeCommand parent) {
        super(parent, "islands");
    }

    @Override
    public void setup() {
        setPermission("admin.islands");
        setDescription("tradewinds.commands.admin.islands.description");
    }

    /**
     * The trading islands nearest a position, closest first.
     */
    static List<IslandSpec> nearest(TradeWinds addon, int x, int z) {
        return addon.getOceanEngine(addon.getOverWorld().getSeed()).islandsNear(x, z, SEARCH_RADIUS).stream()
                .sorted(Comparator.comparingLong(s -> s.distanceSquared(x, z))).limit(COUNT).toList();
    }

    @Override
    public boolean execute(User user, String label, List<String> args) {
        TradeWinds addon = getAddon();
        int x = user.isPlayer() && getWorld().equals(user.getWorld()) ? user.getLocation().getBlockX() : 0;
        int z = user.isPlayer() && getWorld().equals(user.getWorld()) ? user.getLocation().getBlockZ() : 0;
        List<IslandSpec> list = nearest(addon, x, z);
        if (list.isEmpty()) {
            user.sendMessage("tradewinds.commands.admin.islands.none");
        } else {
            user.sendMessage("tradewinds.commands.admin.islands.header");
            for (int i = 0; i < list.size(); i++) {
                IslandSpec s = list.get(i);
                int dist = (int) Math.sqrt(s.distanceSquared(x, z));
                user.sendMessage("tradewinds.commands.admin.islands.entry",
                        "[index]", String.valueOf(i + 1),
                        TextVariables.NAME, s.name(),
                        "[type]", user.getTranslation(s.type().getLocaleKey()),
                        "[tech]", String.valueOf(s.techLevel()),
                        "[band]", user.getTranslation(s.band().getLocaleKey()),
                        "[x]", String.valueOf(s.centerX()),
                        "[z]", String.valueOf(s.centerZ()),
                        "[distance]", String.valueOf(dist));
            }
        }
        return true;
    }
}
