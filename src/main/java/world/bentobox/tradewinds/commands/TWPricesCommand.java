package world.bentobox.tradewinds.commands;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;

import world.bentobox.bentobox.api.commands.CompositeCommand;
import world.bentobox.bentobox.api.localization.TextVariables;
import world.bentobox.bentobox.api.user.User;
import world.bentobox.tradewinds.TradeWinds;
import world.bentobox.tradewinds.economy.Money;
import world.bentobox.tradewinds.economy.TradeCategory;
import world.bentobox.tradewinds.galaxy.IslandSpec;

/**
 * The trader's logbook: what ports you have visited were paying, and how long
 * ago you saw it.
 * <p>
 * Deliberately NOT an oracle. Prices for ports you have never called at are not
 * knowable, and remembered prices are stamped with their age because a market
 * drifts after you leave. Stale data being visibly stale is the feature: it is
 * what makes a well-travelled trader genuinely more capable than a new one,
 * rather than merely richer.
 * <p>
 * Optionally filtered to one category - {@code /tw prices metals} - because the
 * question is usually "where do I take THIS?"
 *
 * @author tastybento
 */
public class TWPricesCommand extends CompositeCommand {

    private static final int MAX_ROWS = 12;

    public TWPricesCommand(CompositeCommand parent) {
        super(parent, "prices");
    }

    @Override
    public void setup() {
        setPermission("island.prices");
        setOnlyPlayer(true);
        setParametersHelp("tradewinds.commands.prices.parameters");
        setDescription("tradewinds.commands.prices.description");
    }

    @Override
    public boolean execute(User user, String label, List<String> args) {
        TradeWinds addon = getAddon();
        TradeCategory filter = null;
        if (!args.isEmpty()) {
            filter = category(args.get(0));
            if (filter == null) {
                user.sendMessage("tradewinds.commands.prices.unknown-category", "[value]", args.get(0));
                return false;
            }
        }
        var data = addon.getPlayerDataManager().get(user.getUniqueId());
        long now = System.currentTimeMillis();

        // Only ports actually CALLED AT have prices - the chart knowing an island
        // exists is not the same as having stood at its counter
        List<IslandSpec> known = new ArrayList<>();
        if (addon.getOverWorld() != null) {
            var engine = addon.getGalaxyEngine(addon.getOverWorld().getSeed());
            for (String key : data.getPriceLog().keySet()) {
                String[] cell = key.split(",");
                if (cell.length != 2) {
                    continue;
                }
                try {
                    engine.islandInCell(Integer.parseInt(cell[0]), Integer.parseInt(cell[1]))
                            .ifPresent(known::add);
                } catch (NumberFormatException e) {
                    // A key we did not write - ignore it rather than fail the command
                }
            }
        }
        known.removeIf(spec -> data.lastSeenPrices(spec) <= 0 || data.loggedPrices(spec).isEmpty());
        if (known.isEmpty()) {
            user.sendMessage("tradewinds.commands.prices.nothing-logged");
            return true;
        }

        final TradeCategory sought = filter;
        if (sought != null) {
            // Best price first: this is the whole point of keeping a logbook
            known.sort(Comparator.comparingInt(
                    (IslandSpec spec) -> data.loggedPrices(spec).getOrDefault(sought.name(), 0)).reversed());
            user.sendMessage("tradewinds.commands.prices.header-category", "[value]",
                    prettyCategory(sought));
        } else {
            known.sort(Comparator.comparingLong(data::lastSeenPrices).reversed());
            user.sendMessage("tradewinds.commands.prices.header");
        }

        for (IslandSpec spec : known.subList(0, Math.min(MAX_ROWS, known.size()))) {
            Map<String, Integer> prices = data.loggedPrices(spec);
            String age = age(user, now - data.lastSeenPrices(spec));
            if (sought != null) {
                Integer price = prices.get(sought.name());
                if (price == null || price <= 0) {
                    continue;
                }
                user.sendMessage("tradewinds.commands.prices.row-category", "[name]", spec.name(), "[price]",
                        Money.format(addon, price), "[age]", age);
            } else {
                user.sendMessage("tradewinds.commands.prices.row", "[name]", spec.name(), "[value]",
                        best(addon, prices), "[age]", age);
            }
        }
        if (known.size() > MAX_ROWS) {
            user.sendMessage("tradewinds.commands.prices.truncated", TextVariables.NUMBER,
                    String.valueOf(MAX_ROWS));
        }
        return true;
    }

    /**
     * The best-paying category remembered at a port - the one line worth showing
     * when not filtering.
     */
    private String best(TradeWinds addon, Map<String, Integer> prices) {
        return prices.entrySet().stream().max(Map.Entry.comparingByValue())
                .map(entry -> prettyCategory(category(entry.getKey())) + " "
                        + Money.format(addon, entry.getValue()))
                .orElse("-");
    }

    private static TradeCategory category(String name) {
        for (TradeCategory category : TradeCategory.values()) {
            if (category.name().equalsIgnoreCase(name)) {
                return category;
            }
        }
        return null;
    }

    private static String prettyCategory(TradeCategory category) {
        return category == null ? "-"
                : world.bentobox.tradewinds.economy.PriceEngine.prettify(category.name());
    }

    /**
     * How stale the reading is, in whole units, via the locale.
     */
    private String age(User user, long millis) {
        long minutes = Math.max(0, millis / 60_000L);
        if (minutes < 60) {
            return user.getTranslation("tradewinds.commands.prices.age-minutes", TextVariables.NUMBER,
                    String.valueOf(minutes));
        }
        long hours = minutes / 60;
        if (hours < 24) {
            return user.getTranslation("tradewinds.commands.prices.age-hours", TextVariables.NUMBER,
                    String.valueOf(hours));
        }
        return user.getTranslation("tradewinds.commands.prices.age-days", TextVariables.NUMBER,
                String.valueOf(hours / 24));
    }
}
