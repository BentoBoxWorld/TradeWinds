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

    private static final int BUY_BATCH = 16;

    private final TradeWinds addon;

    public TradeDialog(TradeWinds addon) {
        this.addon = addon;
    }

    /**
     * Open the market's main menu.
     */
    public void openMain(Player player, IslandSpec spec) {
        double balance = addon.getPlugin().getVault().map(v -> v.getBalance(User.getInstance(player))).orElse(0.0);
        List<ActionButton> buttons = new ArrayList<>();
        buttons.add(button("Sell cargo", NamedTextColor.YELLOW, "Sell your hold's goods here",
                () -> openSell(player, spec)));
        if (!TypeEconomy.catalog(spec.type()).isEmpty()) {
            buttons.add(button("Buy goods", NamedTextColor.AQUA, "Buy this island's produce into your hold",
                    () -> openBuy(player, spec)));
        }
        int owned = addon.getPlayerDataManager().get(player.getUniqueId()).getExpandersPurchased();
        if (owned < addon.getSettings().getExpanderCap()) {
            double price = PriceModel.expanderPrice(addon.getSettings().getExpanderBasePrice(), owned);
            buttons.add(button(String.format("Shipwright: Cargo Expander - $%.0f", price), NamedTextColor.GOLD,
                    "Shulker hold expansion (" + owned + "/" + addon.getSettings().getExpanderCap()
                            + " owned). Price doubles each time.",
                    () -> {
                        addon.getMarketService().buyExpander(player);
                        openMain(player, spec);
                    }));
        }
        show(player, spec.name() + " Market", "Balance: $" + String.format("%.2f", balance) + "  |  "
                + spec.type().name() + ", " + spec.band().getDisplayName(), buttons);
    }

    /**
     * The sell page: one button per hold material this island will pay for.
     */
    public void openSell(Player player, IslandSpec spec) {
        List<ActionButton> buttons = new ArrayList<>();
        for (SellOffer offer : sellOffers(player, spec)) {
            buttons.add(button(
                    String.format("Sell %d x %s - $%.2f", offer.amount(), MarketService.pretty(offer.material()),
                            offer.total()),
                    NamedTextColor.YELLOW, String.format("$%.2f each", offer.unitPrice()),
                    () -> {
                        addon.getMarketService().sell(player, spec, offer.material());
                        openSell(player, spec);
                    }));
        }
        if (buttons.isEmpty()) {
            User.getInstance(player).sendMessage("tradewinds.trade.nothing-to-sell");
            return;
        }
        show(player, spec.name() + " - Selling", "The island pays for goods in your hold", buttons);
    }

    /**
     * The buy page: the island's catalog in batches.
     */
    public void openBuy(Player player, IslandSpec spec) {
        List<ActionButton> buttons = new ArrayList<>();
        for (Material material : TypeEconomy.catalog(spec.type())) {
            Optional<Double> price = addon.getMarketService().playerBuysAt(spec, material);
            price.ifPresent(unit -> buttons.add(button(
                    String.format("Buy %d x %s - $%.2f", BUY_BATCH, MarketService.pretty(material),
                            unit * BUY_BATCH),
                    NamedTextColor.AQUA, String.format("$%.2f each; limited by balance and hold space", unit),
                    () -> {
                        addon.getMarketService().buy(player, spec, material, BUY_BATCH);
                        openBuy(player, spec);
                    })));
        }
        if (buttons.isEmpty()) {
            User.getInstance(player).sendMessage("tradewinds.trade.nothing-for-sale");
            return;
        }
        show(player, spec.name() + " - Buying", "Goods sold into your hold", buttons);
    }

    /**
     * One sellable line: what the hold has and what this island pays. Package
     * visible for tests.
     */
    record SellOffer(Material material, int amount, double unitPrice, double total) {
    }

    List<SellOffer> sellOffers(Player player, IslandSpec spec) {
        List<SellOffer> offers = new ArrayList<>();
        for (Map.Entry<Material, Integer> entry : addon.getHoldService().contents(player).entrySet()) {
            addon.getMarketService().playerSellsAt(spec, entry.getKey())
                    .ifPresent(unit -> offers.add(new SellOffer(entry.getKey(), entry.getValue(), unit,
                            PriceModel.round2(unit * entry.getValue()))));
        }
        return offers;
    }

    private ActionButton button(String label, NamedTextColor color, String tooltip, Runnable action) {
        return ActionButton.create(Component.text(label, color), Component.text(tooltip), 300,
                DialogAction.customClick((response, audience) -> action.run(),
                        ClickCallback.Options.builder().build()));
    }

    private void show(Player player, String title, String body, List<ActionButton> buttons) {
        Dialog dialog = Dialog.create(factory -> factory.empty()
                .base(DialogBase.builder(Component.text(title))
                        .body(List.of(DialogBody.plainMessage(Component.text(body, NamedTextColor.GRAY))))
                        .build())
                .type(DialogType.multiAction(buttons).columns(1).build()));
        player.showDialog(dialog);
    }
}
