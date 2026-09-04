package world.bentobox.tradewinds.economy;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;

import io.papermc.paper.dialog.Dialog;
import io.papermc.paper.registry.data.dialog.ActionButton;
import io.papermc.paper.registry.data.dialog.DialogBase;
import io.papermc.paper.registry.data.dialog.action.DialogAction;
import io.papermc.paper.registry.data.dialog.body.DialogBody;
import io.papermc.paper.registry.data.dialog.type.DialogType;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.event.ClickCallback;
import world.bentobox.bentobox.api.user.User;
import world.bentobox.tradewinds.TradeWinds;
import world.bentobox.tradewinds.ocean.IslandSpec;
import world.bentobox.tradewinds.travel.CargoStore;
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

    // S1192: Duplicate string constants
    private static final String VAR_PRICE = "[price]";
    private static final String VAR_NAME = "[name]";
    private static final String VAR_MATERIAL = "[material]";
    private static final String VAR_AMOUNT = "[amount]";
    private static final String KEY_SELL_QTY_TOOLTIP = "market.sell-qty-tooltip";
    /** The locale's "nothing here" mark - a dash in English, but not everywhere. */
    private static final String KEY_NONE = "tradewinds.general.none";

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
        boolean boatHere = addon.getMarketService().boatIsHere(player, spec);
        if (addon.getSettings().isPriceLogbookEnabled()) {
            recordPrices(player, spec);
        }
        boolean lowFuel = isLowOnFuel(player, spec);
        boolean fuelInTradeCatalog = lowFuel && sellsFuel(addon.getMarketService().saleCatalog(spec));

        List<ActionButton> buttons = buildMainButtons(player, spec, boatHere, lowFuel, fuelInTradeCatalog);
        List<Component> body = buildMainBody(player, spec, boatHere, lowFuel);
        show(player, ui(player, "market.title", VAR_NAME, spec.name()), body, buttons, closeButton(player), 1);
    }

    /**
     * Build the buttons for the main menu dialog.
     */
    private List<ActionButton> buildMainButtons(Player player, IslandSpec spec, boolean boatHere, boolean lowFuel,
            boolean fuelInTradeCatalog) {
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
        // The secondhand shelf, when this port has anything on it
        if (boatHere && !addon.getMarketService().shelf(spec).isEmpty()) {
            buttons.add(button(player, "market.shelf", "market.shelf-tooltip",
                    () -> openShelf(player, spec)));
        }
        addReportButton(buttons, player, spec);
        return buttons;
    }

    private void addReportButton(List<ActionButton> buttons, Player player, IslandSpec spec) {
        // The broker: pay to have your logbook filled in for the ports within
        // this one's reach. No boat needed - it is information, not cargo.
        int reportable = addon.getSettings().isPriceLogbookEnabled()
                ? addon.getMarketService().reportablePorts(spec).size() : 0;
        if (reportable > 0 && addon.getSettings().getMarketReportPricePerIsland() > 0) {
            buttons.add(button(ui(player, "market.report", NO_VARS),
                    ui(player, "market.report-tooltip", "[number]", String.valueOf(reportable),
                            VAR_PRICE, Money.format(addon,
                                    reportable * addon.getSettings().getMarketReportPricePerIsland())),
                    () -> {
                        addon.getMarketService().buyMarketReport(player, spec);
                        openMain(player, spec);
                    }));
        }
    }

    /**
     * Build the body text for the main menu dialog.
     */
    private List<Component> buildMainBody(Player player, IslandSpec spec, boolean boatHere, boolean lowFuel) {
        List<Component> body = new ArrayList<>(List.of(
                ui(player, "market.subtitle", "[type]",
                        User.getInstance(player).getTranslation(spec.type().getLocaleKey()), "[tech]",
                        String.valueOf(spec.techLevel()), "[band]",
                        User.getInstance(player).getTranslation(spec.band().getLocaleKey())),
                statusLine(player)));
        if (!boatHere) {
            body.add(ui(player, "market.no-ship-here", NO_VARS));
        }
        if (lowFuel && boatHere) {
            body.add(ui(player, "market.low-fuel", NO_VARS));
        }
        return body;
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
        // One row per DISTINCT good, not three. The quantity buttons live on the
        // next page, where the item's own icon and a live price sit beside them -
        // an icon cannot go in a button label, and repeating every good three
        // times across a wall of buttons told a player less, not more
        // (playtest 2026-08-03).
        List<ActionButton> buttons = new ArrayList<>();
        for (SellOffer offer : offers.subList(0, Math.min(MAX_ROWS, offers.size()))) {
            buttons.add(button(
                    ui(player, "market.sell-pick", VAR_MATERIAL, itemLabel(player, offer.item()), VAR_AMOUNT,
                            String.valueOf(offer.amount()), VAR_PRICE, Money.format(addon, offer.unitPrice())),
                    ui(player, "market.sell-pick-tooltip", VAR_PRICE, Money.format(addon, offer.unitPrice()),
                            "[depth]", depthOf(player, spec, offer)),
                    () -> openSellItem(player, spec, offer.item())));
        }
        List<Component> body = new ArrayList<>(List.of(ui(player, "market.selling-body", NO_VARS),
                statusLine(player)));
        if (offers.size() > MAX_ROWS) {
            body.add(ui(player, "market.selling-truncated", "[number]", String.valueOf(MAX_ROWS)));
        }
        show(player, ui(player, "market.selling-title", VAR_NAME, spec.name()), body, buttons,
                backButton(player, spec), 2);
    }

    /**
     * The quantity page for ONE good: its icon with the real hover tooltip, the
     * live unit price and how much more this port will absorb, then 1 / 16 / all.
     * <p>
     * Reopened after every sale, so the price and the remaining depth update as
     * the port fills up - which is the whole point of selling in increments.
     */
    public void openSellItem(Player player, IslandSpec spec, ItemStack item) {
        // Re-resolve against the hold: the price moves as you sell, and the good
        // may be gone entirely
        SellOffer offer = sellOffers(player, spec).stream()
                .filter(candidate -> CargoStore.stacksTogether(candidate.item(), item)).findFirst()
                .orElse(null);
        if (offer == null) {
            openSell(player, spec);
            return;
        }
        String each = Money.format(addon, offer.unitPrice());
        List<DialogBody> body = new ArrayList<>();
        body.add(DialogBody.item(offer.item())
                .description(DialogBody.plainMessage(ui(player, "market.sell-item-line", VAR_MATERIAL,
                        itemLabel(player, offer.item()), VAR_AMOUNT, String.valueOf(offer.amount()),
                        VAR_PRICE, each)))
                .showTooltip(true).showDecorations(true).width(32).height(32).build());
        body.add(DialogBody.plainMessage(ui(player, "market.sell-item-depth", "[depth]",
                depthOf(player, spec, offer))));
        body.add(DialogBody.plainMessage(statusLine(player)));

        int batch = Math.min(MID_BATCH, offer.amount());
        List<ActionButton> buttons = new ArrayList<>();
        buttons.add(button(ui(player, "market.sell-qty-one", VAR_PRICE, each),
                ui(player, KEY_SELL_QTY_TOOLTIP, NO_VARS),
                () -> sellThenReopen(player, spec, offer.item(), 1)));
        if (batch > 1) {
            buttons.add(button(
                    ui(player, "market.sell-qty-batch", VAR_AMOUNT, String.valueOf(batch), VAR_PRICE,
                            Money.format(addon, batch * offer.unitPrice())),
                    ui(player, KEY_SELL_QTY_TOOLTIP, NO_VARS),
                    () -> sellThenReopen(player, spec, offer.item(), batch)));
        }
        buttons.add(button(
                ui(player, "market.sell-qty-all", VAR_AMOUNT, String.valueOf(offer.amount()), VAR_PRICE,
                        Money.format(addon, offer.total())),
                ui(player, KEY_SELL_QTY_TOOLTIP, NO_VARS),
                () -> sellThenReopen(player, spec, offer.item(), Integer.MAX_VALUE)));

        showBodies(player, ui(player, "market.selling-title", VAR_NAME, spec.name()), body, buttons,
                button(ui(player, "market.back", NO_VARS), ui(player, "market.back-tooltip", NO_VARS),
                        () -> openSell(player, spec)),
                3);
    }

    /**
     * A short label that tells two otherwise identical goods apart: its given
     * name if it has one, else a marker for enchanted or worn gear. Buttons are
     * too narrow for a full enchantment list - that is what the icon tooltip
     * beside it is for.
     */
    private String itemLabel(Player player, ItemStack item) {
        String material = MarketService.pretty(item.getType());
        var meta = item.getItemMeta();
        if (meta != null && meta.hasDisplayName()) {
            return net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer.plainText()
                    .serialize(meta.displayName());
        }
        if (!item.getEnchantments().isEmpty()) {
            return uiText(player, "market.item-enchanted", VAR_MATERIAL, material);
        }
        if (meta instanceof org.bukkit.inventory.meta.Damageable damaged && damaged.hasDamage()
                && damaged.getDamage() > 0) {
            return uiText(player, "market.item-worn", VAR_MATERIAL, material);
        }
        return material;
    }

    /**
     * The secondhand shelf: notable goods other sailors sold on, shipped here by
     * their traders. One button per listing.
     */
    public void openShelf(Player player, IslandSpec spec) {
        List<ItemStack> shelf = addon.getMarketService().shelf(spec);
        if (shelf.isEmpty()) {
            User.getInstance(player).sendMessage("tradewinds.trade.shelf-empty");
            openMain(player, spec);
            return;
        }
        List<ActionButton> buttons = new ArrayList<>();
        List<DialogBody> body = new ArrayList<>();
        body.add(DialogBody.plainMessage(ui(player, "market.shelf-body", NO_VARS)));
        body.add(DialogBody.plainMessage(statusLine(player)));
        for (int i = 0; i < Math.min(MAX_ROWS, shelf.size()); i++) {
            ItemStack item = shelf.get(i);
            final int index = i;
            String price = addon.getMarketService().shelfPrice(item)
                    .map(value -> Money.format(addon, value))
                    .orElse(User.getInstance(player).getTranslation(KEY_NONE));
            // Secondhand goods are notable by definition, so the icon and its real
            // tooltip are the whole point of the page
            body.add(DialogBody.item(item)
                    .description(DialogBody.plainMessage(ui(player, "market.shelf-item-line", VAR_MATERIAL,
                            itemLabel(player, item), VAR_PRICE, price)))
                    .showTooltip(true).showDecorations(true).build());
            buttons.add(button(ui(player, "market.shelf-buy", VAR_MATERIAL, itemLabel(player, item),
                    VAR_PRICE, price), ui(player, "market.shelf-buy-tooltip", "[details]", details(item)),
                    () -> {
                        addon.getMarketService().buyFromShelf(player, spec, index);
                        openShelf(player, spec);
                    }));
        }
        showBodies(player, ui(player, "market.shelf-title", VAR_NAME, spec.name()), body, buttons,
                backButton(player, spec), 2);
    }

    /**
     * What makes a shelf item worth looking at - its name, or its enchantments.
     */
    private String details(ItemStack item) {
        var meta = item.getItemMeta();
        if (meta != null && meta.hasDisplayName()) {
            return net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer.plainText()
                    .serialize(meta.displayName());
        }
        if (!item.getEnchantments().isEmpty()) {
            return item.getEnchantments().entrySet().stream()
                    .map(e -> PriceEngine.prettify(e.getKey().getKey().getKey()) + " " + e.getValue())
                    .reduce((a, b) -> a + ", " + b).orElse("");
        }
        return MarketService.pretty(item.getType());
    }

    /**
     * How many MORE of this good the port will take before its price hits the
     * floor - the number a seller actually needs, because it says when to stop
     * selling and sail on. Beyond the floor, dumping more is pure waste.
     *
     * @return the count, or "-" where the port is already saturated
     */
    private String depthOf(Player player, IslandSpec spec, SellOffer offer) {
        int headroom = addon.getIslandDataManager().absorbableValue(spec,
                addon.getMarketService().driftPool(offer.material()));
        if (offer.unitPrice() <= 0) {
            return User.getInstance(player).getTranslation(KEY_NONE);
        }
        return String.valueOf((int) Math.floor(headroom / offer.unitPrice()));
    }

    private void sellThenReopen(Player player, IslandSpec spec, ItemStack item, int amount) {
        addon.getMarketService().sell(player, spec, item, amount);
        openSellItem(player, spec, item);
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
                Component each = ui(player, "market.buy-tooltip-each", VAR_PRICE, Money.format(addon, unit));
                buttons.add(button(ui(player, "market.buy-one", VAR_MATERIAL, name, VAR_PRICE,
                        Money.format(addon, unit)), each,
                        () -> buyThenReopen(player, spec, material, 1, catalog, page)));
                buttons.add(button(ui(player, "market.buy-batch", VAR_AMOUNT, String.valueOf(MID_BATCH),
                        VAR_PRICE, Money.format(addon, unit * MID_BATCH)), each,
                        () -> buyThenReopen(player, spec, material, MID_BATCH, catalog, page)));
                buttons.add(button(ui(player, "market.buy-batch", VAR_AMOUNT, String.valueOf(BIG_BATCH),
                        VAR_PRICE, Money.format(addon, unit * BIG_BATCH)), each,
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
                Component each = ui(player, "market.outfit-tooltip", VAR_PRICE, Money.format(addon, unit));
                buttons.add(button(ui(player, "market.outfit-one", VAR_MATERIAL, name, VAR_PRICE,
                        Money.format(addon, unit)), each,
                        () -> outfitThenReopen(player, spec, material, 1)));
                if (new org.bukkit.inventory.ItemStack(material).getMaxStackSize() > 1) {
                    // The batch button names its good too: unstackable gear
                    // gets no batch button, which shifts the two-column
                    // pairing, and a bare "x16 - $7,184" then sells a mystery
                    // (playtest 2026-08-05: the Compass batch went orphan)
                    buttons.add(button(ui(player, "market.outfit-batch", VAR_MATERIAL, name,
                            VAR_AMOUNT, String.valueOf(MID_BATCH),
                            VAR_PRICE, Money.format(addon, unit * MID_BATCH)), each,
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
                    ui(player, "market.hull", VAR_MATERIAL, MarketService.pretty(rank.material()), VAR_PRICE,
                            Money.format(addon, price)),
                    ui(player, "market.hull-tooltip", "[slots]", String.valueOf(rank.slots())),
                    () -> {
                        // Buying outright abandons the current boat where it
                        // lies - that gets the same confirmation a capture
                        // does, because it is the same decision
                        if (addon.getMarketService().wouldReplaceCurrent(player, spec, rank)) {
                            confirmBoatCapture(player, MarketService.pretty(rank.material()),
                                    MarketService.pretty(current), () -> {
                                        addon.getMarketService().buyBoat(player, spec, rank);
                                        openShipwright(player, spec);
                                    });
                            return;
                        }
                        addon.getMarketService().buyBoat(player, spec, rank);
                        openShipwright(player, spec);
                    }));
        }
        // Expanders: the endgame sink, sold only where the tech tops out
        if (spec.techLevel() >= world.bentobox.tradewinds.ocean.OceanEngine.MAX_TECH_LEVEL) {
            int installed = addon.getHoldService().expanderCount(player.getUniqueId());
            double price = PriceModel.expanderPrice(addon.getSettings().getExpanderBasePrice(), installed);
            buttons.add(button(ui(player, "market.expander", VAR_PRICE, Money.format(addon, price)),
                    ui(player, "market.expander-tooltip", "[owned]", String.valueOf(installed)),
                    () -> {
                        addon.getMarketService().buyExpander(player, spec);
                        openShipwright(player, spec);
                    }));
        }
        List<Component> body = new ArrayList<>(List.of(ui(player, "market.shipwright-body", NO_VARS),
                statusLine(player)));
        if (buttons.isEmpty()) {
            // Nothing on the slipway: the sailor has out-teched this port.
            // The notice goes in the BODY - rendered as a button it
            // truncates and reads as a broken shop (playtest 2026-08-05) -
            // and the one button offered is somewhere actually useful
            body.add(ui(player, "market.no-hulls", NO_VARS));
            body.add(ui(player, "market.no-hulls-tooltip", NO_VARS));
            buttons.add(button(player, "market.outfitter", "market.outfitter-tooltip",
                    () -> openOutfitter(player, spec)));
        }
        if (addon.getMarketService().isDestitute(player)) {
            buttons.add(button(player, "market.charity", "market.charity-tooltip",
                    () -> {
                        addon.getMarketService().claimCharity(player);
                        openShipwright(player, spec);
                    }));
        }
        show(player, ui(player, "market.shipwright-title", "[name]", spec.name()), body, buttons,
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
        buttons.add(button(ui(player, "capture.confirm", VAR_MATERIAL, taking),
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
        show(player, ui(player, "capture.title", VAR_MATERIAL, taking), body, buttons,
                ActionButton.builder(ui(player, "capture.cancel", NO_VARS)).width(300).build(), 1);
    }

    /**
     * One sellable line: what the hold has and what this island pays. Package
     * visible for tests.
     */
    record SellOffer(ItemStack item, int amount, double unitPrice, double total) {

        Material material() {
            return item.getType();
        }
    }

    List<SellOffer> sellOffers(Player player, IslandSpec spec) {
        List<SellOffer> offers = new ArrayList<>();
        // No stamps: everything aboard (expanders included) is sellable wherever
        // a price and the port's own rules (contraband bands) allow
        for (Map.Entry<ItemStack, Integer> entry : distinctGoods(player).entrySet()) {
            ItemStack item = entry.getKey();
            // Do not quote a price for something this port will refuse at the
            // counter: the sell page used to offer a dollar for contraband that
            // a safe island would then decline, which reads as a broken market
            if (isContrabandsRefused(item.getType(), spec) || !addon.getMarketService().handlesValue(spec, item)) {
                continue;
            }
            addon.getMarketService().playerSellsAt(spec, item)
                    .ifPresent(unit -> offers.add(new SellOffer(item, entry.getValue(), unit,
                            PriceModel.round2(unit * entry.getValue()))));
        }
        return offers;
    }

    /**
     * Whether this port refuses to buy this contraband item.
     */
    private boolean isContrabandsRefused(Material material, IslandSpec spec) {
        return addon.getCustomsService() != null && addon.getCustomsService().isContraband(material)
                && !addon.getCustomsService().buysContraband(spec.band());
    }

    /**
     * Everything aboard, one entry per DISTINCT good with its total count. Two
     * differently enchanted swords are two entries - they are worth different
     * money and must be sellable separately - while cargo split across several
     * slots collapses back into one line.
     */
    private java.util.Map<ItemStack, Integer> distinctGoods(Player player) {
        java.util.Map<ItemStack, Integer> goods = new java.util.LinkedHashMap<>();
        for (ItemStack stack : addon.getHoldService().tradeCargo(player)) {
            ItemStack key = goods.keySet().stream()
                    .filter(seen -> world.bentobox.tradewinds.travel.CargoStore.stacksTogether(seen, stack))
                    .findFirst().orElse(null);
            if (key == null) {
                goods.put(stack, stack.getAmount());
            } else {
                goods.merge(key, stack.getAmount(), Integer::sum);
            }
        }
        return goods;
    }

    /**
     * A translated UI component. All dialog text goes through the locale so it
     * can be translated - never build player-facing strings in code.
     */
    private Component ui(Player player, String key, String... variables) {
        return User.getInstance(player).getTranslationAsComponent("tradewinds.ui." + key, variables);
    }

    /**
     * The same key space as {@link #ui} but as plain text, for values that get
     * substituted INTO another translation. Sharing the prefix matters: a hand
     * written "tradewinds.market.x" silently renders as the key itself.
     */
    private String uiText(Player player, String key, String... variables) {
        return User.getInstance(player).getTranslation("tradewinds.ui." + key, variables);
    }

    /**
     * Write this port's current prices into the player's logbook. Sell prices,
     * because "where can I get a good price for this" is the question a trader
     * actually asks.
     */
    private void recordPrices(Player player, IslandSpec spec) {
        var data = addon.getPlayerDataManager().get(player.getUniqueId());
        data.logPrices(spec, addon.getMarketService().currentPrices(spec), System.currentTimeMillis());
        addon.getPlayerDataManager().save(player.getUniqueId());
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
        showBodies(player, title,
                bodyLines.stream().map(line -> (DialogBody) DialogBody.plainMessage(line)).toList(), buttons,
                exitButton, columns);
    }

    /**
     * As {@link #show} but with the body built by the caller, so it can carry
     * item icons and not just text.
     */
    private void showBodies(Player player, Component title, List<DialogBody> body, List<ActionButton> buttons,
            ActionButton exitButton, int columns) {
        Dialog dialog = Dialog.create(factory -> factory.empty()
                .base(DialogBase.builder(title).body(body).build())
                .type(DialogType.multiAction(buttons).exitAction(exitButton).columns(columns).build()));
        player.showDialog(dialog);
    }
}
