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
import world.bentobox.tradewinds.travel.FuelWarning;

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
        // The cargo is IN the boat, so buying and selling need the ship at
        // the quay - but the OUTFITTER and the SHIPWRIGHT must always be
        // open, or a sailor who has lost their boat can never get another
        // and is stranded on the island for good (playtest 2026-08-02).
        boolean boatHere = addon.getMarketService().boatIsHere(player, spec);
        // Too poor in fuel to warp anywhere from here? Then the most useful
        // thing this screen can do is point at where the fuel is sold. An
        // action bar fades; a button that says "buy fuel here" does not.
        boolean lowFuel = isLowOnFuel(player, spec);
        boolean fuelInTradeCatalog = lowFuel && sellsFuel(addon.getMarketService().saleCatalog(spec));

        List<ActionButton> buttons = new ArrayList<>();
        // No sell button when the hold has nothing this island pays for
        if (boatHere && !sellOffers(player, spec).isEmpty()) {
            buttons.add(button(player, "market.sell", "market.sell-tooltip", () -> openSell(player, spec)));
        }
        if (boatHere && !addon.getMarketService().saleCatalog(spec).isEmpty()) {
            buttons.add(button(player, fuelInTradeCatalog ? "market.buy-fuel" : "market.buy",
                    fuelInTradeCatalog ? "market.buy-fuel-tooltip" : "market.buy-tooltip",
                    () -> openBuy(player, spec, addon.getMarketService().saleCatalog(spec), "buying")));
        }
        // The outfitter only stocks charcoal when the trade catalog has no fuel
        // of its own, so highlight whichever one can actually help
        boolean fuelAtOutfitter = lowFuel && !fuelInTradeCatalog;
        buttons.add(button(player, fuelAtOutfitter ? "market.outfitter-fuel" : "market.outfitter",
                fuelAtOutfitter ? "market.outfitter-fuel-tooltip" : "market.outfitter-tooltip",
                () -> openOutfitter(player, spec)));
        buttons.add(button(player, "market.shipwright", "market.shipwright-tooltip",
                () -> openShipwright(player, spec)));
        List<Component> body = new ArrayList<>(List.of(
                ui(player, "market.subtitle", "[type]", spec.type().name(), "[tech]",
                        String.valueOf(spec.techLevel()), "[band]",
                        User.getInstance(player).getTranslation(spec.band().getLocaleKey())),
                statusLine(player)));
        if (!boatHere) {
            body.add(ui(player, "market.no-ship-here", NO_VARS));
        }
        if (lowFuel && boatHere) {
            body.add(ui(player, "market.low-fuel", NO_VARS));
        }
        show(player, ui(player, "market.title", "[name]", spec.name()), body, buttons, closeButton(player), 1);
    }

    /**
     * Whether the player cannot afford to warp anywhere from this island.
     */
    private boolean isLowOnFuel(Player player, IslandSpec spec) {
        if (!addon.getSettings().isFuelWarningEnabled()) {
            return false;
        }
        double fuel = addon.getFuelService().holdFuel(player);
        int cheapest = FuelWarning.cheapestRoute(addon.getWarpService().destinations(player, spec, fuel));
        return FuelWarning.isLow(fuel, cheapest, addon.getSettings().getFuelWarningMargin());
    }

    /**
     * Whether a catalog contains anything that burns.
     */
    private boolean sellsFuel(List<Material> catalog) {
        return catalog.stream().anyMatch(m -> addon.getSettings().getFuelValues().getOrDefault(m.name(), 0.0) > 0);
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
                    Money.format(addon, offer.unitPrice()));
            buttons.add(button(ui(player, "market.sell-one", "[material]", name), each,
                    () -> sellThenReopen(player, spec, offer.material(), 1)));
            buttons.add(button(ui(player, "market.sell-batch", "[material]", name, "[amount]",
                    String.valueOf(MID_BATCH)), each,
                    () -> sellThenReopen(player, spec, offer.material(), MID_BATCH)));
            buttons.add(button(ui(player, "market.sell-all", "[amount]", String.valueOf(offer.amount()),
                    "[price]", Money.format(addon, offer.total())), each,
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
                Component each = ui(player, "market.buy-tooltip-each", "[price]", Money.format(addon, unit));
                buttons.add(button(ui(player, "market.buy-one", "[material]", name, "[price]",
                        Money.format(addon, unit)), each,
                        () -> buyThenReopen(player, spec, material, 1, catalog, page)));
                buttons.add(button(ui(player, "market.buy-batch", "[amount]", String.valueOf(MID_BATCH),
                        "[price]", Money.format(addon, unit * MID_BATCH)), each,
                        () -> buyThenReopen(player, spec, material, MID_BATCH, catalog, page)));
                buttons.add(button(ui(player, "market.buy-batch", "[amount]", String.valueOf(BIG_BATCH),
                        "[price]", Money.format(addon, unit * BIG_BATCH)), each,
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
                Component each = ui(player, "market.outfit-tooltip", "[price]", Money.format(addon, unit));
                buttons.add(button(ui(player, "market.outfit-one", "[material]", name, "[price]",
                        Money.format(addon, unit)), each,
                        () -> outfitThenReopen(player, spec, material, 1)));
                if (new org.bukkit.inventory.ItemStack(material).getMaxStackSize() > 1) {
                    buttons.add(button(ui(player, "market.outfit-batch", "[amount]", String.valueOf(MID_BATCH),
                            "[price]", Money.format(addon, unit * MID_BATCH)), each,
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
     * The shipwright's slipway: the boat ladder. Only boats BIGGER than yours
     * are listed (an upgrade destroys the old boat; contents stay put), and
     * only up to what this island's tech can build. Crafting bypasses all of
     * this - the shop is the money route, rare wood is the exploration route.
     */
    public void openShipwright(Player player, IslandSpec spec) {
        List<ActionButton> buttons = new ArrayList<>();
        Material current = addon.getHoldService().boat(player);
        for (world.bentobox.tradewinds.travel.BoatRanks.Rank rank : addon.getBoatRanks()
                .shopListing(current, spec.techLevel())) {
            double price = addon.getBoatRanks().price(rank);
            buttons.add(button(
                    ui(player, "market.hull", "[material]", MarketService.pretty(rank.material()), "[price]",
                            Money.format(addon, price)),
                    ui(player, "market.hull-tooltip", "[slots]", String.valueOf(rank.slots())),
                    () -> {
                        addon.getMarketService().buyBoat(player, spec, rank);
                        openShipwright(player, spec);
                    }));
        }
        // Expanders: the endgame sink, sold only where the tech tops out
        if (spec.techLevel() >= world.bentobox.tradewinds.galaxy.GalaxyEngine.MAX_TECH_LEVEL) {
            int installed = addon.getHoldService().expanderCount(player.getUniqueId());
            double price = PriceModel.expanderPrice(addon.getSettings().getExpanderBasePrice(), installed);
            buttons.add(button(ui(player, "market.expander", "[price]", Money.format(addon, price)),
                    ui(player, "market.expander-tooltip", "[owned]", String.valueOf(installed)),
                    () -> {
                        addon.getMarketService().buyExpander(player, spec);
                        openShipwright(player, spec);
                    }));
        }
        if (buttons.isEmpty()) {
            // Nothing on the slipway: the sailor has out-teched this port
            buttons.add(button(player, "market.no-hulls", "market.no-hulls-tooltip", () -> openMain(player, spec)));
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
     * The confirmation every boat capture gets: taking this hull abandons
     * your own, cargo and all, wherever it lies. Yes/no, no default.
     *
     * @param player the player
     * @param taking pretty name of the boat they would take
     * @param leaving pretty name of the boat they would abandon
     * @param onConfirm what to do if they accept
     */
    public void confirmBoatCapture(Player player, String taking, String leaving, Runnable onConfirm) {
        confirmBoatFound(player, taking, leaving, null, onConfirm, null);
    }

    /**
     * What to do with a hull you have come across (ruled 2026-08-02). Taking
     * it abandons your own boat where it lies; moving its cargo across is
     * offered ONLY when your own boat is close enough to load - cargo is not
     * teleported across the ocean.
     *
     * @param player the player
     * @param taking pretty name of the hull found
     * @param leaving pretty name of their own boat, or null if they have none
     * @param cargo a description of what it carries, or null if empty
     * @param onTake take the hull
     * @param onTransfer move the cargo into their own boat, or null if their
     *        boat is out of range (or the hull is empty)
     */
    public void confirmBoatFound(Player player, String taking, String leaving, String cargo, Runnable onTake,
            Runnable onTransfer) {
        List<ActionButton> buttons = new ArrayList<>();
        buttons.add(button(ui(player, "capture.confirm", "[material]", taking),
                ui(player, "capture.confirm-tooltip", NO_VARS), onTake));
        if (onTransfer != null) {
            buttons.add(button(ui(player, "capture.transfer", NO_VARS),
                    ui(player, "capture.transfer-tooltip", NO_VARS), onTransfer));
        }
        List<Component> body = new ArrayList<>();
        if (leaving == null) {
            body.add(ui(player, "capture.body-no-boat", "[taking]", taking));
        } else {
            body.add(ui(player, "capture.body", "[taking]", taking, "[leaving]", leaving));
            if (onTransfer == null && cargo != null) {
                // Say WHY the cargo option is missing, or it reads as a bug
                body.add(ui(player, "capture.body-too-far", "[leaving]", leaving));
            }
        }
        if (cargo != null) {
            body.add(ui(player, "capture.body-cargo", "[cargo]", cargo));
        }
        show(player, ui(player, "capture.title", "[material]", taking), body, buttons,
                ActionButton.builder(ui(player, "capture.cancel", NO_VARS)).width(300).build(), 1);
    }

    /**
     * One sellable line: what the hold has and what this island pays. Package
     * visible for tests.
     */
    record SellOffer(Material material, int amount, double unitPrice, double total) {
    }

    List<SellOffer> sellOffers(Player player, IslandSpec spec) {
        List<SellOffer> offers = new ArrayList<>();
        // No stamps: everything aboard (expanders included) is sellable
        // wherever a price and the port's own rules (contraband bands) allow
        for (Map.Entry<Material, Integer> entry : addon.getHoldService().tradeContents(player).entrySet()) {
            // Do not quote a price for something this port will refuse at the
            // counter: the sell page used to offer a dollar for contraband that
            // a safe island would then decline, which reads as a broken market
            if (addon.getCustomsService() != null && addon.getCustomsService().isContraband(entry.getKey())
                    && !addon.getCustomsService().buysContraband(spec.band())) {
                continue;
            }
            // Nor for goods too rich for this port's tech to handle
            if (!addon.getMarketService().handlesValue(spec, entry.getKey())) {
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
        return ui(player, "market.status", "[balance]", Money.format(addon, balance), "[space]",
                addon.getHoldService().slotsUsed(player.getUniqueId()) + "/"
                        + addon.getHoldService().capacitySlots(player));
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
