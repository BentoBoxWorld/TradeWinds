package world.bentobox.tradewinds.commands;

import java.util.Comparator;
import java.util.List;

import world.bentobox.bentobox.api.commands.CompositeCommand;
import world.bentobox.bentobox.api.localization.TextVariables;
import world.bentobox.bentobox.api.user.User;
import world.bentobox.tradewinds.TradeWinds;
import world.bentobox.tradewinds.ocean.OceanEngine;
import world.bentobox.tradewinds.ocean.IslandSpec;

/**
 * Shows the player's chart: every island they have discovered, nearest first.
 *
 * @author tastybento
 */
public class TWChartCommand extends CompositeCommand {

    private static final String MATERIAL_PLACEHOLDER = "[material]";

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
        // The hologram compass, afloat OR ashore: a sailor hunting their
        // moored boat needs the BOAT marker exactly when they are NOT in it
        // (playtest 2026-08-02: the text list was "not useful" for finding
        // it). The text listing stays behind '/tw chart list'.
        boolean wantList = !args.isEmpty() && args.get(0).equalsIgnoreCase("list");
        if (!wantList && getWorld().equals(user.getWorld())) {
            addon.getChartHolograms().show(user.getPlayer());
            user.sendMessage("tradewinds.chart.holograms-shown");
            // ...and say where the boats are in words as well. The holograms
            // skip a boat that is carried or within a few paces - sensible
            // markers, useless answers: at a quay the compass drew nothing and
            // a sailor could not tell "no OLD BOAT" from "not drawn"
            // (playtest 2026-08-08). One line each, always.
            reportBoat(addon, user, user.getLocation().getBlockX(), user.getLocation().getBlockZ(), false);
            reportBoat(addon, user, user.getLocation().getBlockX(), user.getLocation().getBlockZ(), true);
        } else {
            chartList(addon, user);
        }
        return true;
    }

    /**
     * The text chart: every charted island with distance and the fuel to get
     * there, nearest first, plus where your boats lie.
     */
    private void chartList(TradeWinds addon, User user) {
        OceanEngine engine = addon.getOceanEngine(addon.getOverWorld().getSeed());
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
            return;
        }
        // What the fuel aboard can actually reach. A list of places you cannot
        // afford to go is a list of disappointments, so say which is which.
        double fuelAboard = addon.getFuelService().holdFuel(user.getPlayer());
        IslandSpec origin = portAt(engine, x, z).orElse(null);
        user.sendMessage("tradewinds.chart.header", TextVariables.NUMBER, String.valueOf(charted.size()));
        user.sendMessage("tradewinds.chart.fuel-aboard", "[amount]", String.valueOf((int) fuelAboard));
        reportBoat(addon, user, x, z, false);
        reportBoat(addon, user, x, z, true);
        charted.forEach(spec -> {
            int cost = routeCost(addon, origin, spec, x, z);
            boolean reachable = cost <= fuelAboard;
            user.sendMessage(reachable ? "tradewinds.chart.entry-reachable" : "tradewinds.chart.entry-far",
                    TextVariables.NAME, spec.name(),
                    "[type]", spec.type().name(),
                    "[tech]", String.valueOf(spec.techLevel()),
                    "[band]", user.getTranslation(spec.band().getLocaleKey()),
                    "[distance]", String.valueOf((int) Math.sqrt(spec.distanceSquared(x, z))),
                    "[fuel]", String.valueOf(cost));
        });
    }

    /**
     * Where a boat of yours lies, in words: the chart's holograms only draw
     * boats in THIS world, so the list has to say when one is missing or
     * elsewhere - otherwise a sailor cannot tell "no old boat" from "the
     * marker did not draw".
     *
     * @param addon the addon
     * @param user the player
     * @param x their block x
     * @param z their block z
     * @param old true for the abandoned OLD BOAT, false for their own
     */
    private void reportBoat(TradeWinds addon, User user, int x, int z, boolean old) {
        var boatRecord = old ? addon.getHoldManager().oldBoat(user.getUniqueId())
                : addon.getHoldManager().activeBoat(user.getUniqueId());
        String key = old ? "tradewinds.chart.old-boat" : "tradewinds.chart.your-boat";
        if (boatRecord.isEmpty()) {
            if (!old) {
                user.sendMessage("tradewinds.chart.no-boat");
            }
            return;
        }
        var hold = boatRecord.get();
        String material = world.bentobox.tradewinds.economy.PriceEngine.prettify(hold.getMaterial());
        if (hold.getWorld() == null || hold.getWorld().isEmpty()) {
            user.sendMessage(key + "-lost", MATERIAL_PLACEHOLDER, material);
            return;
        }
        if (!hold.getWorld().equals(user.getWorld().getName())) {
            user.sendMessage(key + "-elsewhere", MATERIAL_PLACEHOLDER, material, "[world]", hold.getWorld());
            return;
        }
        long dx = (long) hold.getX() - x;
        long dz = (long) hold.getZ() - z;
        user.sendMessage(key, MATERIAL_PLACEHOLDER, material, "[x]", String.valueOf(hold.getX()),
                "[z]", String.valueOf(hold.getZ()),
                "[distance]", String.valueOf((int) Math.sqrt((double) dx * dx + (double) dz * dz)));
    }

    /**
     * The trading island the player is standing in the waters of, if any -
     * warps launch from an island, so that is the origin a cost is measured
     * from when there is one.
     */
    private java.util.Optional<IslandSpec> portAt(OceanEngine engine, int x, int z) {
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
