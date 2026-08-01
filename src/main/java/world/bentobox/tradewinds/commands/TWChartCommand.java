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
        // At a port? Copy its harbour charts first - a free scan of the
        // neighbouring islands
        List<IslandSpec> scanned = addon.getPlayerDataManager().portScan(user.getPlayer());
        if (!scanned.isEmpty()) {
            user.sendMessage("tradewinds.chart.port-scan", TextVariables.NUMBER,
                    String.valueOf(scanned.size()));
        }
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
        // What the fuel aboard can actually reach. A list of places you cannot
        // afford to go is a list of disappointments, so say which is which.
        double fuelAboard = addon.getFuelService().holdFuel(user.getPlayer());
        IslandSpec origin = portAt(engine, x, z).orElse(null);
        user.sendMessage("tradewinds.chart.header", TextVariables.NUMBER, String.valueOf(charted.size()));
        user.sendMessage("tradewinds.chart.fuel-aboard", "[amount]", String.valueOf((int) fuelAboard));
        charted.forEach(spec -> {
            int cost = routeCost(addon, origin, spec, x, z);
            boolean reachable = cost <= fuelAboard;
            user.sendMessage(reachable ? "tradewinds.chart.entry-reachable" : "tradewinds.chart.entry-far",
                    TextVariables.NAME, spec.name(),
                    "[type]", spec.type().name(),
                    "[band]", user.getTranslation(spec.band().getLocaleKey()),
                    "[distance]", String.valueOf((int) Math.sqrt(spec.distanceSquared(x, z))),
                    "[fuel]", String.valueOf(cost));
        });
        return true;
    }

    /**
     * The trading island the player is standing in the waters of, if any -
     * warps launch from an island, so that is the origin a cost is measured
     * from when there is one.
     */
    private java.util.Optional<IslandSpec> portAt(GalaxyEngine engine, int x, int z) {
        int range = ((TradeWinds) getAddon()).getSettings().getIslandProtectionRange();
        return engine.islandsNear(x, z, range).stream()
                .filter(s -> s.distanceSquared(x, z) <= (long) range * range).findFirst();
    }

    /**
     * The fuel a warp to this island would cost. From a port that is the exact
     * route price, honouring any lane overrides; adrift it is the same
     * distance-based estimate the route graph would charge, measured from the
     * player - close enough to plan by, which is what a chart is for.
     */
    private int routeCost(TradeWinds addon, IslandSpec origin, IslandSpec target, int x, int z) {
        if (origin != null) {
            return origin.equals(target) ? 0 : addon.getRouteGraph().cost(origin, target);
        }
        double distance = Math.sqrt(target.distanceSquared(x, z));
        return Math.max(1, (int) Math.ceil(distance * addon.getSettings().getFuelPerBlock()));
    }
}
