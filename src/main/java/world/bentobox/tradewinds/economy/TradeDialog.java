package world.bentobox.tradewinds.economy;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import org.bukkit.Material;
import org.bukkit.entity.Player;

import io.papermc.paper.dialog.Dialog;
import io.papermc.paper.registry.data.dialog.ActionButton;
import io.papermc.paper.registry.data.dialog.DialogBase;
import io.papermc.paper.registry.data.dialog.action.DialogAction;
import io.papermc.paper.registry.data.dialog.body.DialogBody;
import io.papermc.paper.registry.data.dialog.type.DialogType;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.event.ClickCallback;
import net.kyori.adventure.text.format.NamedTextColor;
import world.bentobox.bentobox.api.user.User;
import world.bentobox.tradewinds.TradeWinds;
import world.bentobox.tradewinds.galaxy.IslandSpec;

/**
 * The market dialogs: a main menu (sell / buy / shipwright), a sell page
 * listing the hold's sellable cargo at this island's prices, and a buy page
 * listing the island's catalog. Everything transacts through MarketService,
 * hold-only; dialogs reopen after each action with fresh prices.
 *
 * @author tastybento
 */
public class TradeDialog {

    /** Sell/buy quantity steps shown side by side per cargo row. */
    private static final int MID_BATCH = 16;
    private static final int BIG_BATCH = 64;
    /** Rows that fit a dialog without scrolling. */
    private static final int MAX_ROWS = 8;

    private final TradeWinds addon;

    public TradeDialog(TradeWinds addon) {
        this.addon = addon;
    }

    /**
     * Open the market's main menu.
     */
    public void openMain(Player player, IslandSpec spec) {
        List<ActionButton> buttons = new ArrayList<>();
        // No sell button when the hold has nothing this island pays for
        if (!sellOffers(player, spec).isEmpty()) {
            buttons.add(button("Sell cargo", NamedTextColor.YELLOW, "Sell your hold's goods here",
                    () -> openSell(player, spec)));
        }
        if (!addon.getMarketService().saleCatalog(spec).isEmpty()) {
            buttons.add(button("Buy goods", NamedTextColor.AQUA, "Buy this island's trade goods into your hold",
                    () -> openBuy(player, spec, addon.getMarketService().saleCatalog(spec), " - Buying")));
        }
        buttons.add(button("Outfitter", NamedTextColor.GREEN, "Food, fuel and gear - straight to your pack",
                () -> openOutfitter(player, spec)));
        buttons.add(button("Shipwright", NamedTextColor.GOLD, "Boats, chest boats and cargo expanders",
                () -> openShipwright(player, spec)));
        show(player, spec.name() + " Market",
                List.of(spec.type().name() + ", " + spec.band().getDisplayName(), statusLine(player)), buttons,
                closeButton(), 1);
    }

    /**
     * The sell page: one button per hold material this island will pay for.
     */
    public void openSell(Player player, IslandSpec spec) {
        List<SellOffer> offers = sellOffers(player, spec);
        if (offers.isEmpty()) {
            User.getInstance(player).sendMessage("tradewinds.trade.nothing-to-sell");
            openMain(player, spec);
            return;
        }
        List<ActionButton> buttons = new ArrayList<>();
        for (SellOffer offer : offers.subList(0, Math.min(MAX_ROWS, offers.size()))) {
            String name = MarketService.pretty(offer.material());
            String each = String.format("$%.2f each", offer.unitPrice());
            buttons.add(button(name + " x1", NamedTextColor.YELLOW, each,
                    () -> sellThenReopen(player, spec, offer.material(), 1)));
            buttons.add(button(name + " x" + MID_BATCH, NamedTextColor.YELLOW,
                    String.format("%s - up to $%.2f", each, offer.unitPrice() * MID_BATCH),
                    () -> sellThenReopen(player, spec, offer.material(), MID_BATCH)));
            buttons.add(button(String.format("All %d - $%.2f", offer.amount(), offer.total()), NamedTextColor.GOLD,
                    each, () -> sellThenReopen(player, spec, offer.material(), Integer.MAX_VALUE)));
        }
        List<String> body = new ArrayList<>(List.of("The island pays for goods in your hold", statusLine(player)));
        if (offers.size() > MAX_ROWS) {
            body.add("(showing the first " + MAX_ROWS + " cargo types - sell some to see the rest)");
        }
        show(player, spec.name() + " - Selling", body, buttons, backButton(player, spec), 3);
    }

    private void sellThenReopen(Player player, IslandSpec spec, Material material, int amount) {
        addon.getMarketService().sell(player, spec, material, amount);
        openSell(player, spec);
    }

    /**
     * The buy page: the island's catalog in batches.
     */
    public void openBuy(Player player, IslandSpec spec, List<Material> catalog, String titleSuffix) {
        List<ActionButton> buttons = new ArrayList<>();
        for (Material material : catalog) {
            Optional<Double> price = addon.getMarketService().playerBuysAt(spec, material);
            price.ifPresent(unit -> {
                String name = MarketService.pretty(material);
                String each = String.format("$%.2f each; limited by balance and hold space", unit);
                buttons.add(button(String.format("%s x1 - $%.2f", name, unit), NamedTextColor.AQUA, each,
                        () -> buyThenReopen(player, spec, material, 1, catalog, titleSuffix)));
                buttons.add(button(String.format("x%d - $%.2f", MID_BATCH, unit * MID_BATCH), NamedTextColor.AQUA,
                        each, () -> buyThenReopen(player, spec, material, MID_BATCH, catalog, titleSuffix)));
                buttons.add(button(String.format("x%d - $%.2f", BIG_BATCH, unit * BIG_BATCH), NamedTextColor.AQUA,
                        each, () -> buyThenReopen(player, spec, material, BIG_BATCH, catalog, titleSuffix)));
            });
        }
        if (buttons.isEmpty()) {
            User.getInstance(player).sendMessage("tradewinds.trade.nothing-for-sale");
            openMain(player, spec);
            return;
        }
        show(player, spec.name() + titleSuffix,
                List.of("Goods sold into your hold - customs stamped", statusLine(player)), buttons,
                backButton(player, spec), 3);
    }

    private void buyThenReopen(Player player, IslandSpec spec, Material material, int amount, List<Material> catalog,
            String titleSuffix) {
        addon.getMarketService().buy(player, spec, material, amount);
        openBuy(player, spec, catalog, titleSuffix);
    }

    /**
     * The outfitter's shelf: stores for living, bought into the player's pack
     * rather than the hold - a fishing rod is not cargo, and gear that will
     * not stack could never fit a bundle anyway.
     */
    public void openOutfitter(Player player, IslandSpec spec) {
        List<ActionButton> buttons = new ArrayList<>();
        for (Material material : addon.getMarketService().outfitterCatalog(spec)) {
            addon.getMarketService().playerBuysAt(spec, material).ifPresent(unit -> {
                String name = MarketService.pretty(material);
                String each = String.format("$%.2f each - carried in your pack, not the hold", unit);
                buttons.add(button(String.format("%s x1 - $%.2f", name, unit), NamedTextColor.GREEN, each,
                        () -> outfitThenReopen(player, spec, material, 1)));
                if (new org.bukkit.inventory.ItemStack(material).getMaxStackSize() > 1) {
                    buttons.add(button(String.format("x%d - $%.2f", MID_BATCH, unit * MID_BATCH),
                            NamedTextColor.GREEN, each,
                            () -> outfitThenReopen(player, spec, material, MID_BATCH)));
                }
            });
        }
        if (buttons.isEmpty()) {
            User.getInstance(player).sendMessage("tradewinds.trade.nothing-for-sale");
            openMain(player, spec);
            return;
        }
        show(player, spec.name() + " - Outfitter",
                List.of("Stores for the sailing life - food, fuel and gear", statusLine(player)), buttons,
                backButton(player, spec), 2);
    }

    private void outfitThenReopen(Player player, IslandSpec spec, Material material, int amount) {
        addon.getMarketService().buyToInventory(player, spec, material, amount);
        openOutfitter(player, spec);
    }

    /**
     * The shipwright's slipway: hulls to your inventory, expanders to your
     * hold. The cargo progression lives here: boat -> chest boat -> expanders.
     */
    public void openShipwright(Player player, IslandSpec spec) {
        List<ActionButton> buttons = new ArrayList<>();
        for (Material hull : addon.getMarketService().shipwrightCatalog()) {
            addon.getMarketService().playerBuysAt(spec, hull).ifPresent(unit -> buttons.add(button(
                    String.format("%s - $%.2f", MarketService.pretty(hull), unit), NamedTextColor.GOLD,
                    "Delivered to your inventory - place it at the dock",
                    () -> {
                        addon.getMarketService().buyToInventory(player, spec, hull, 1);
                        openShipwright(player, spec);
                    })));
        }
        int pouches = addon.getHoldService().pouchCount(player);
        int maxPouches = addon.getSettings().getMaxBundles();
        if (pouches < maxPouches) {
            buttons.add(button(String.format("Trading Pouch - $%.0f", addon.getSettings().getPouchPrice()),
                    NamedTextColor.GOLD,
                    "Holds 64 items (" + pouches + "/" + maxPouches + " carried). Cheap to start with; a "
                            + "cargo expander is far better value once you can afford one.",
                    () -> {
                        addon.getMarketService().buyPouch(player);
                        openShipwright(player, spec);
                    }));
        }
        int owned = addon.getPlayerDataManager().get(player.getUniqueId()).getExpandersPurchased();
        if (owned < addon.getSettings().getExpanderCap()) {
            double price = PriceModel.expanderPrice(addon.getSettings().getExpanderBasePrice(), owned);
            buttons.add(button(String.format("Cargo Expander - $%.0f", price), NamedTextColor.GOLD,
                    "27 stacks of cargo space (" + owned + "/" + addon.getSettings().getExpanderCap()
                            + " owned). Price doubles each time. Carried in your pack - open it anywhere.",
                    () -> {
                        addon.getMarketService().buyExpander(player);
                        openShipwright(player, spec);
                    }));
        }
        show(player, spec.name() + " - Shipwright",
                List.of("Hulls and holds. Smart sailors craft their own boats from wild timber.",
                        statusLine(player)),
                buttons, backButton(player, spec), 1);
    }

    /**
     * One sellable line: what the hold has and what this island pays. Package
     * visible for tests.
     */
    record SellOffer(Material material, int amount, double unitPrice, double total) {
    }

    List<SellOffer> sellOffers(Player player, IslandSpec spec) {
        List<SellOffer> offers = new ArrayList<>();
        // Traders only buy customs-stamped goods (plus the illegal exceptions)
        for (Map.Entry<Material, Integer> entry : addon.getHoldService()
                .contents(player, addon.getMarketService().sellableFilter()).entrySet()) {
            addon.getMarketService().playerSellsAt(spec, entry.getKey())
                    .ifPresent(unit -> offers.add(new SellOffer(entry.getKey(), entry.getValue(), unit,
                            PriceModel.round2(unit * entry.getValue()))));
        }
        return offers;
    }

    /**
     * Balance and hold space - shown on every market screen so traders always
     * know what they can afford and what they can carry.
     */
    private String statusLine(Player player) {
        double balance = addon.getPlugin().getVault().map(v -> v.getBalance(User.getInstance(player))).orElse(0.0);
        return String.format("Balance: $%.2f  |  Hold space: ~%d items", balance,
                addon.getHoldService().freeSpace(player));
    }

    private ActionButton backButton(Player player, IslandSpec spec) {
        return button("< Back", NamedTextColor.GRAY, "Back to the market menu", () -> openMain(player, spec));
    }

    private ActionButton closeButton() {
        // No action: clicking simply closes the dialog
        return ActionButton.builder(Component.text("Close", NamedTextColor.GRAY)).width(300).build();
    }

    private ActionButton button(String label, NamedTextColor color, String tooltip, Runnable action) {
        return ActionButton.create(Component.text(label, color), Component.text(tooltip), 140,
                DialogAction.customClick((response, audience) -> action.run(),
                        ClickCallback.Options.builder().build()));
    }

    private void show(Player player, String title, List<String> bodyLines, List<ActionButton> buttons,
            ActionButton exitButton, int columns) {
        List<DialogBody> body = bodyLines.stream()
                .map(line -> (DialogBody) DialogBody.plainMessage(Component.text(line, NamedTextColor.GRAY)))
                .toList();
        Dialog dialog = Dialog.create(factory -> factory.empty()
                .base(DialogBase.builder(Component.text(title)).body(body).build())
                .type(DialogType.multiAction(buttons).exitAction(exitButton).columns(columns).build()));
        player.showDialog(dialog);
    }
}
