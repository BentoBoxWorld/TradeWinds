package world.bentobox.tradewinds.commands;

import java.io.IOException;
import java.io.PrintWriter;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;

import org.bukkit.Material;
import org.bukkit.inventory.ItemStack;

import world.bentobox.bentobox.api.commands.CompositeCommand;
import world.bentobox.bentobox.api.localization.TextVariables;
import world.bentobox.bentobox.api.user.User;
import world.bentobox.tradewinds.TradeWinds;
import world.bentobox.tradewinds.economy.TypeEconomy;

/**
 * Audits price coverage: which materials can be priced, which cannot, and which
 * are salvage rather than recognised trade goods.
 * <p>
 * This has to run on a real server rather than as a unit test. Most prices are
 * <i>derived</i> from crafting recipes, and MockBukkit ships no vanilla recipe
 * set - headless, almost everything would report as unpriceable, which is a
 * confident wrong answer. The live registry is the only place the truth lives.
 * <p>
 * An unpriceable material is a good a player can carry and never sell, so the
 * report is the to-do list for {@code economy.base-prices}.
 *
 * @author tastybento
 */
public class AdminPriceAuditCommand extends CompositeCommand {

    public AdminPriceAuditCommand(CompositeCommand parent) {
        super(parent, "priceaudit");
    }

    @Override
    public void setup() {
        setPermission("admin.priceaudit");
        setOnlyPlayer(false);
        setDescription("tradewinds.commands.admin.priceaudit.description");
    }

    @Override
    public boolean execute(User user, String label, List<String> args) {
        TradeWinds addon = getAddon();
        var engine = addon.getMarketService().getPriceEngine();
        Map<String, Double> tradeGoods = new TreeMap<>();
        Map<String, Double> salvage = new TreeMap<>();
        List<String> unpriceable = new ArrayList<>();

        for (Material material : Material.values()) {
            // Legacy aliases and non-item blocks are not goods and would only
            // pad the report
            if (material.isLegacy() || !material.isItem() || material.isAir()) {
                continue;
            }
            double price = engine.getPrice(new ItemStack(material));
            if (price <= 0) {
                unpriceable.add(material.name());
            } else if (TypeEconomy.tradeGoods().contains(material)) {
                tradeGoods.put(material.name(), price);
            } else {
                salvage.put(material.name(), price);
            }
        }

        user.sendMessage("tradewinds.commands.admin.priceaudit.header");
        user.sendMessage("tradewinds.commands.admin.priceaudit.trade-goods", TextVariables.NUMBER,
                String.valueOf(tradeGoods.size()));
        user.sendMessage("tradewinds.commands.admin.priceaudit.salvage", TextVariables.NUMBER,
                String.valueOf(salvage.size()));
        user.sendMessage("tradewinds.commands.admin.priceaudit.unpriceable", TextVariables.NUMBER,
                String.valueOf(unpriceable.size()));

        Path report = addon.getDataFolder().toPath().resolve("price-audit.txt");
        try {
            write(report, tradeGoods, salvage, unpriceable);
            user.sendMessage("tradewinds.commands.admin.priceaudit.written", "[file]", report.toString());
        } catch (IOException e) {
            addon.logError("Could not write the price audit: " + e.getMessage());
            user.sendMessage("tradewinds.commands.admin.priceaudit.write-failed");
        }
        return true;
    }

    private void write(Path report, Map<String, Double> tradeGoods, Map<String, Double> salvage,
            List<String> unpriceable) throws IOException {
        Files.createDirectories(report.getParent());
        try (PrintWriter out = new PrintWriter(Files.newBufferedWriter(report))) {
            out.println("TradeWinds price audit");
            out.println();
            out.println("UNPRICEABLE (" + unpriceable.size() + ") - carryable but unsellable.");
            out.println("Add a base price for anything here a player could plausibly acquire.");
            unpriceable.forEach(name -> out.println("  " + name));
            out.println();
            out.println("SALVAGE (" + salvage.size() + ") - priced, but on no island's shelves,");
            out.println("so sold at the salvage discount into its own stock pool.");
            salvage.forEach((name, price) -> out.printf("  %-34s %10.0f%n", name, price));
            out.println();
            out.println("TRADE GOODS (" + tradeGoods.size() + ") - stocked by some island type.");
            tradeGoods.forEach((name, price) -> out.printf("  %-34s %10.0f%n", name, price));
        }
    }
}
