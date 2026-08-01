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

    /** Disambiguates the no-variable getTranslationAsComponent overloads. */
    private static final String[] NO_VARS = new String[0];

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
        // A fugitive's money is no good in the safe bands at all
        if (!addon.getMarketService().willTradeWith(player, spec)) {
            User.getInstance(player).sendMessage("tradewinds.trade.barred");
            return;
        }
        List<ActionButton> buttons = new ArrayList<>();
        // No sell button when the hold has nothing this island pays for
        if (!sellOffers(player, spec).isEmpty()) {
            buttons.add(button(player, "market.sell", "market.sell-tooltip", () -> openSell(player, spec)));
        }
        if (!addon.getMarketService().saleCatalog(spec).isEmpty()) {
            buttons.add(button(player, "market.buy", "market.buy-tooltip",
                    () -> openBuy(player, spec, addon.getMarketService().saleCatalog(spec), "buying")));
        }
        buttons.add(button(player, "market.outfitter", "market.outfitter-tooltip",
                () -> openOutfitter(player, spec)));
        buttons.add(button(player, "market.shipwright", "market.shipwright-tooltip",
                () -> openShipwright(player, spec)));
        show(player, ui(player, "market.title", "[name]", spec.name()),
                List.of(ui(player, "market.subtitle", "[type]", spec.type().name(), "[band]",
                        spec.band().getDisplayName()), statusLine(player)),
                buttons, closeButton(player), 1);
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
            Component each = ui(player, "market.sell-tooltip-each", "[price]",
                    String.format("%.2f", offer.unitPrice()));
            buttons.add(button(ui(player, "market.sell-one", "[material]", name), each,
                    () -> sellThenReopen(player, spec, offer.material(), 1)));
            buttons.add(button(ui(player, "market.sell-batch", "[material]", name, "[amount]",
                    String.valueOf(MID_BATCH)), each,
                    () -> sellThenReopen(player, spec, offer.material(), MID_BATCH)));
            buttons.add(button(ui(player, "market.sell-all", "[amount]", String.valueOf(offer.amount()),
                    "[price]", String.format("%.2f", offer.total())), each,
                    () -> sellThenReopen(player, spec, offer.material(), Integer.MAX_VALUE)));
        }
        List<Component> body = new ArrayList<>(List.of(ui(player, "market.selling-body", NO_VARS),
                statusLine(player)));
        if (offers.size() > MAX_ROWS) {
            body.add(ui(player, "market.selling-truncated", "[number]", String.valueOf(MAX_ROWS)));
        }
        show(player, ui(player, "market.selling-title", "[name]", spec.name()), body, buttons,
                backButton(player, spec), 3);
    }

    private void sellThenReopen(Player player, IslandSpec spec, Material material, int amount) {
        addon.getMarketService().sell(player, spec, material, amount);
        openSell(player, spec);
    }

    /**
     * The buy page: the island's catalog in batches.
     */
    public void openBuy(Player player, IslandSpec spec, List<Material> catalog, String page) {
        List<ActionButton> buttons = new ArrayList<>();
        for (Material material : catalog) {
            Optional<Double> price = addon.getMarketService().playerBuysAt(spec, material);
            price.ifPresent(unit -> {
                String name = MarketService.pretty(material);
                Component each = ui(player, "market.buy-tooltip-each", "[price]", String.format("%.2f", unit));
                buttons.add(button(ui(player, "market.buy-one", "[material]", name, "[price]",
                        String.format("%.2f", unit)), each,
                        () -> buyThenReopen(player, spec, material, 1, catalog, page)));
                buttons.add(button(ui(player, "market.buy-batch", "[amount]", String.valueOf(MID_BATCH),
                        "[price]", String.format("%.2f", unit * MID_BATCH)), each,
                        () -> buyThenReopen(player, spec, material, MID_BATCH, catalog, page)));
                buttons.add(button(ui(player, "market.buy-batch", "[amount]", String.valueOf(BIG_BATCH),
                        "[price]", String.format("%.2f", unit * BIG_BATCH)), each,
                        () -> buyThenReopen(player, spec, material, BIG_BATCH, catalog, page)));
            });
        }
        if (buttons.isEmpty()) {
            User.getInstance(player).sendMessage("tradewinds.trade.nothing-for-sale");
            openMain(player, spec);
            return;
        }
        show(player, ui(player, "market." + page + "-title", "[name]", spec.name()),
                List.of(ui(player, "market." + page + "-body", NO_VARS), statusLine(player)), buttons,
                backButton(player, spec), 3);
    }

    private void buyThenReopen(Player player, IslandSpec spec, Material material, int amount, List<Material> catalog,
            String page) {
        addon.getMarketService().buy(player, spec, material, amount);
        openBuy(player, spec, catalog, page);
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
                Component each = ui(player, "market.outfit-tooltip", "[price]", String.format("%.2f", unit));
                buttons.add(button(ui(player, "market.outfit-one", "[material]", name, "[price]",
                        String.format("%.2f", unit)), each,
                        () -> outfitThenReopen(player, spec, material, 1)));
                if (new org.bukkit.inventory.ItemStack(material).getMaxStackSize() > 1) {
                    buttons.add(button(ui(player, "market.outfit-batch", "[amount]", String.valueOf(MID_BATCH),
                            "[price]", String.format("%.2f", unit * MID_BATCH)), each,
                            () -> outfitThenReopen(player, spec, material, MID_BATCH)));
                }
            });
        }
        if (buttons.isEmpty()) {
            User.getInstance(player).sendMessage("tradewinds.trade.nothing-for-sale");
            openMain(player, spec);
            return;
        }
        show(player, ui(player, "market.outfitter-title", "[name]", spec.name()),
                List.of(ui(player, "market.outfitter-body", NO_VARS), statusLine(player)), buttons,
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
                    ui(player, "market.hull", "[material]", MarketService.pretty(hull), "[price]",
                            String.format("%.2f", unit)),
                    ui(player, "market.hull-tooltip", NO_VARS),
                    () -> {
                        addon.getMarketService().buyToInventory(player, spec, hull, 1);
                        openShipwright(player, spec);
                    })));
        }
        int pouches = addon.getHoldService().pouchCount(player);
        int maxPouches = addon.getSettings().getMaxBundles();
        if (pouches < maxPouches) {
            buttons.add(button(ui(player, "market.pouch", "[price]",
                    String.format("%.0f", addon.getMarketService().pouchPrice(player))),
                    ui(player, "market.pouch-tooltip", "[owned]", String.valueOf(pouches), "[cap]",
                            String.valueOf(maxPouches)),
                    () -> {
                        addon.getMarketService().buyPouch(player);
                        openShipwright(player, spec);
                    }));
        }
        int owned = addon.getPlayerDataManager().get(player.getUniqueId()).getExpandersPurchased();
        if (owned < addon.getSettings().getExpanderCap()) {
            double price = PriceModel.expanderPrice(addon.getSettings().getExpanderBasePrice(), owned);
            buttons.add(button(ui(player, "market.expander", "[price]", String.format("%.0f", price)),
                    ui(player, "market.expander-tooltip", "[owned]", String.valueOf(owned), "[cap]",
                            String.valueOf(addon.getSettings().getExpanderCap())),
                    () -> {
                        addon.getMarketService().buyExpander(player);
                        openShipwright(player, spec);
                    }));
        }
        if (addon.getMarketService().isDestitute(player)) {
            buttons.add(button(player, "market.charity", "market.charity-tooltip",
                    () -> {
                        addon.getMarketService().claimCharity(player);
                        openShipwright(player, spec);
                    }));
        }
        show(player, ui(player, "market.shipwright-title", "[name]", spec.name()),
                List.of(ui(player, "market.shipwright-body", NO_VARS), statusLine(player)), buttons,
                backButton(player, spec), 1);
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
            // Do not quote a price for something this port will refuse at the
            // counter: the sell page used to offer a dollar for contraband that
            // a safe island would then decline, which reads as a broken market
            if (addon.getCustomsService() != null && addon.getCustomsService().isContraband(entry.getKey())
                    && !addon.getCustomsService().buysContraband(spec.band())) {
                continue;
            }
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
    /**
     * A translated UI component. All dialog text goes through the locale so it
     * can be translated - never build player-facing strings in code.
     */
    private Component ui(Player player, String key, String... variables) {
        return User.getInstance(player).getTranslationAsComponent("tradewinds.ui." + key, variables);
    }

    private Component statusLine(Player player) {
        double balance = addon.getPlugin().getVault().map(v -> v.getBalance(User.getInstance(player))).orElse(0.0);
        return ui(player, "market.status", "[balance]", String.format("%.2f", balance), "[space]",
                String.valueOf(addon.getHoldService().freeSpace(player)));
    }

    private ActionButton backButton(Player player, IslandSpec spec) {
        return button(player, "market.back", "market.back-tooltip", () -> openMain(player, spec));
    }

    private ActionButton closeButton(Player player) {
        // No action: clicking simply closes the dialog
        return ActionButton.builder(ui(player, "market.close", NO_VARS)).width(300).build();
    }

    private ActionButton button(Player player, String labelKey, String tooltipKey, Runnable action) {
        return button(ui(player, labelKey, NO_VARS), ui(player, tooltipKey, NO_VARS), action);
    }

    private ActionButton button(Component label, Component tooltip, Runnable action) {
        return ActionButton.create(label, tooltip, 140,
                DialogAction.customClick((response, audience) -> action.run(),
                        ClickCallback.Options.builder().build()));
    }

    private void show(Player player, Component title, List<Component> bodyLines, List<ActionButton> buttons,
            ActionButton exitButton, int columns) {
        List<DialogBody> body = bodyLines.stream().map(line -> (DialogBody) DialogBody.plainMessage(line)).toList();
        Dialog dialog = Dialog.create(factory -> factory.empty()
                .base(DialogBase.builder(title).body(body).build())
                .type(DialogType.multiAction(buttons).exitAction(exitButton).columns(columns).build()));
        player.showDialog(dialog);
    }
}
