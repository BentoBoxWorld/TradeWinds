package world.bentobox.tradewinds.commands;

import java.util.Comparator;
import java.util.List;

import world.bentobox.bentobox.api.commands.CompositeCommand;
import world.bentobox.bentobox.api.localization.TextVariables;
import world.bentobox.bentobox.api.user.User;
import world.bentobox.tradewinds.TradeWinds;
import world.bentobox.tradewinds.galaxy.GalaxyEngine;
import world.bentobox.tradewinds.galaxy.IslandSpec;

/**
 * Shows the player's chart: every island they have discovered, nearest first.
 *
 * @author tastybento
 */
public class TWChartCommand extends CompositeCommand {

    public TWChartCommand(CompositeCommand parent) {
        super(parent, "chart");
    }

    @Override
    public void setup() {
        setPermission("island.chart");
        setOnlyPlayer(true);
        setDescription("tradewinds.commands.chart.description");
    }

    @Override
    public boolean execute(User user, String label, List<String> args) {
        TradeWinds addon = getAddon();
        // In a boat (and not asking for the text list): raise the hologram
        // compass - visual targets to row toward
        boolean wantList = !args.isEmpty() && args.get(0).equalsIgnoreCase("list");
        if (!wantList && user.getPlayer().getVehicle() instanceof org.bukkit.entity.Boat
                && getWorld().equals(user.getWorld())) {
            addon.getChartHolograms().show(user.getPlayer());
            user.sendMessage("tradewinds.chart.holograms-shown");
            return true;
        }
        GalaxyEngine engine = addon.getGalaxyEngine(addon.getOverWorld().getSeed());
        int x = getWorld().equals(user.getWorld()) ? user.getLocation().getBlockX() : 0;
        int z = getWorld().equals(user.getWorld()) ? user.getLocation().getBlockZ() : 0;
        List<IslandSpec> charted = addon.getPlayerDataManager().get(user.getUniqueId()).getChartedIslands().stream()
                .map(key -> key.split(","))
                .map(cell -> engine.islandInCell(Integer.parseInt(cell[0]), Integer.parseInt(cell[1])))
                .flatMap(java.util.Optional::stream)
                .sorted(Comparator.comparingLong(spec -> spec.distanceSquared(x, z)))
                .toList();
        if (charted.isEmpty()) {
            user.sendMessage("tradewinds.chart.empty");
            return true;
        }
        user.sendMessage("tradewinds.chart.header", TextVariables.NUMBER, String.valueOf(charted.size()));
        charted.forEach(spec -> user.sendMessage("tradewinds.chart.entry",
                TextVariables.NAME, spec.name(),
                "[type]", spec.type().name(),
                "[band]", spec.band().getDisplayName(),
                "[distance]", String.valueOf((int) Math.sqrt(spec.distanceSquared(x, z)))));
        return true;
    }
}
