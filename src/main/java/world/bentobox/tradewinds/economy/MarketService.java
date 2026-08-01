package world.bentobox.tradewinds.economy;

import java.util.Optional;

import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.Sound;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataType;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import world.bentobox.bentobox.api.user.User;
import world.bentobox.bentobox.hooks.VaultHook;
import world.bentobox.tradewinds.TradeWinds;
import world.bentobox.tradewinds.api.events.TWTradeEvent;
import world.bentobox.tradewinds.galaxy.IslandSpec;

/**
 * The market: prices and trades. Base prices come from the embedded
 * {@link PriceEngine} (config table plus recipe derivation - BlueBook logic
 * carried in-addon). Island prices apply the type/band/stock model
 * ({@link PriceModel}); all transactions move items through the hold ONLY and
 * money through Vault.
 *
 * @author tastybento
 */
public class MarketService {

    /** PDC key marking cargo expander items. */
    public static final NamespacedKey EXPANDER_KEY = NamespacedKey.fromString("tradewinds:expander");
    /** PDC key marking customs-stamped (trader-bought) goods - the API marker. */
    public static final NamespacedKey STAMP_KEY = NamespacedKey.fromString("tradewinds:stamp");

    private final TradeWinds addon;
    private final PriceEngine priceEngine;

    public MarketService(TradeWinds addon) {
        this.addon = addon;
        this.priceEngine = new PriceEngine(addon);
    }

    /**
     * @return the embedded price engine
     */
    public PriceEngine getPriceEngine() {
        return priceEngine;
    }

    /**
     * What this island trades: its type's produce catalog.
     */
    public java.util.List<Material> saleCatalog(IslandSpec spec) {
        // Traders never stock contraband. If they did, a smuggler could buy it
        // over the counter at the honest price and sell it back at the black
        // market premium with no farming and no risk at all.
        return TypeEconomy.catalog(spec.type()).stream()
                .filter(m -> addon.getCustomsService() == null || !addon.getCustomsService().isContraband(m))
                .toList();
    }

    /**
     * The outfitter's shelf: survival essentials every island guarantees.
     * Bread always; fuel (CHARCOAL) unless the trade catalog already sells
     * fuel - only the skint AND fuel-less row; plus per-type gear (smiths at
     * INDUSTRIAL, beds at AGRICULTURAL, rods at FISHING).
     */
    public java.util.List<Material> outfitterCatalog(IslandSpec spec) {
        java.util.List<Material> shelf = new java.util.ArrayList<>();
        shelf.add(Material.BREAD);
        boolean tradeHasFuel = saleCatalog(spec).stream()
                .anyMatch(m -> addon.getSettings().getFuelValues().getOrDefault(m.name(), 0.0) > 0);
        if (!tradeHasFuel) {
            shelf.add(Material.CHARCOAL);
        }
        shelf.addAll(TypeEconomy.outfitterExtras(spec.type()));
        return shelf;
    }

    /**
     * The shipwright's slipway: hulls, bought straight to the player's
     * inventory (a shipless sailor has no hold to receive into - a vessel is
     * not cargo). Cargo expanders complete the shipwright's stock via
     * {@link #buyExpander}.
     */
    public java.util.List<Material> shipwrightCatalog() {
        return java.util.List.of(Material.OAK_BOAT, Material.OAK_CHEST_BOAT);
    }

    /**
     * Buy stores delivered to the player's INVENTORY, not the hold: outfitter
     * supplies (food, gear, beds, rods) and hulls. These are for using, not
     * for resale, so they are deliberately NOT customs stamped - which also
     * stops the outfitter's shelf becoming an arbitrage route. Anything that
     * will not fit is dropped at the player's feet.
     *
     * @return how many were bought
     */
    public int buyToInventory(Player player, IslandSpec spec, Material material, int amount) {
        Optional<Double> unitPrice = playerBuysAt(spec, material);
        Optional<VaultHook> vault = addon.getPlugin().getVault();
        if (unitPrice.isEmpty() || vault.isEmpty() || amount <= 0) {
            return 0;
        }
        User user = User.getInstance(player);
        double balance = vault.get().getBalance(user);
        int affordable = (int) Math.min(amount, Math.floor(balance / unitPrice.get()));
        if (affordable <= 0) {
            user.sendMessage("tradewinds.trade.cannot-afford");
            thud(player);
            return 0;
        }
        TWTradeEvent event = new TWTradeEvent(player, spec, material, affordable, unitPrice.get(), false);
        org.bukkit.Bukkit.getPluginManager().callEvent(event);
        if (event.isCancelled()) {
            thud(player);
            return 0;
        }
        double total = PriceModel.round2(affordable * unitPrice.get());
        vault.get().withdraw(user, total);
        ItemStack stores = new ItemStack(material, affordable);
        player.getInventory().addItem(stores).values()
                .forEach(left -> player.getWorld().dropItem(player.getLocation(), left));
        user.sendMessage("tradewinds.trade.bought", "[amount]", String.valueOf(affordable), "[material]",
                pretty(material), "[price]", String.format("%.2f", total));
        chime(player);
        return affordable;
    }

    /**
     * Apply the customs stamp: trader-bought goods carry a PDC marker and a
     * lore line. Only stamped goods can be sold back to traders - homegrown
     * and homemade items are for living with, not for selling (with the
     * illegal exceptions the customs office would rather not discuss).
     */
    public ItemStack stamp(ItemStack item) {
        ItemMeta meta = item.getItemMeta();
        if (meta == null) {
            return item;
        }
        meta.getPersistentDataContainer().set(STAMP_KEY, PersistentDataType.STRING, "stamped");
        java.util.List<Component> lore = meta.lore() == null ? new java.util.ArrayList<>()
                : new java.util.ArrayList<>(meta.lore());
        lore.add(text("tradewinds.item.stamp"));
        meta.lore(lore);
        if (addon.getSettings().isStampGlint()) {
            meta.setEnchantmentGlintOverride(true);
        }
        item.setItemMeta(meta);
        return item;
    }

    /**
     * Is this item customs-stamped (trader-bought)? Public API for other
     * addons.
     */
    public boolean isStamped(ItemStack item) {
        return item != null && item.hasItemMeta()
                && item.getItemMeta().getPersistentDataContainer().has(STAMP_KEY, PersistentDataType.STRING);
    }

    /**
     * The filter deciding what traders will buy: stamped goods, plus the
     * configured unstamped exceptions (contraband - if the illegal trade is
     * enabled at all).
     */
    public java.util.function.Predicate<ItemStack> sellableFilter() {
        return stack -> isStamped(stack)
                || (addon.getSettings().isIllegalTradeEnabled()
                        && addon.getSettings().getUnstampedSellables().contains(stack.getType().name()));
    }

    /**
     * The configured price model.
     */
    public PriceModel model() {
        var s = addon.getSettings();
        return new PriceModel(s.getProduceFactor(), s.getDemandFactor(), s.getBandDemandBonus(), s.getBuySpread(),
                s.getSellSpread(), s.getDriftScale(), s.getDriftMin(), s.getDriftMax());
    }

    /**
     * Base price of a material: configured, or derived from crafting recipes
     * by the embedded price engine. Empty means not tradeable.
     */
    public Optional<Double> basePrice(Material material) {
        double price = priceEngine.getPrice(new ItemStack(material));
        return price > 0 ? Optional.of(price) : Optional.empty();
    }

    /**
     * What a player pays this island per unit, or empty if not tradeable.
     */
    public Optional<Double> playerBuysAt(IslandSpec spec, Material material) {
        return basePrice(material)
                .map(base -> model().playerBuysAt(base * contrabandPremium(material), factor(spec, material)));
    }

    /**
     * What this island pays a player per unit, or empty if not tradeable.
     */
    public Optional<Double> playerSellsAt(IslandSpec spec, Material material) {
        return basePrice(material)
                .map(base -> model().playerSellsAt(base * contrabandPremium(material), factor(spec, material)));
    }

    /**
     * The smuggler's premium.
     * <p>
     * Contraband is priced from its crafting recipe like everything else, and
     * sugar's recipe price is about one currency unit - so the first playtest
     * ran cargo past a customs patrol and was offered a dollar for it. The risk
     * was built and the reward was not. This is the reward: what a black market
     * pays over the honest price of the same goods, and the main lever for how
     * profitable smuggling is.
     *
     * @param material the material
     * @return the multiplier, 1.0 for anything legal
     */
    public double contrabandPremium(Material material) {
        if (addon.getCustomsService() == null || !addon.getCustomsService().isContraband(material)) {
            return 1.0;
        }
        return Math.max(1.0, addon.getSettings().getContrabandPriceMultiplier());
    }

    private double factor(IslandSpec spec, Material material) {
        TradeCategory category = TradeCategory.of(material);
        boolean contraband = addon.getCustomsService() != null
                && addon.getCustomsService().isContraband(material);
        // A port that deals in contraband always wants it - it is not on
        // anybody's official produce list, and demand is what makes the band
        // bonus apply, so the rougher the port the better it pays
        boolean demands = contraband || TypeEconomy.demands(spec.type()).contains(category);
        boolean produces = !contraband && TypeEconomy.produces(spec.type()).contains(category);
        return model().economicFactor(produces, demands, spec.band().ordinal(),
                addon.getIslandDataManager().getStock(spec, category));
    }

    /**
     * Sell up to {@code amount} of one material from the player's hold to the
     * island (Integer.MAX_VALUE = everything).
     *
     * @return amount sold
     */
    public int sell(Player player, IslandSpec spec, Material material, int amount) {
        Optional<Double> unitPrice = playerSellsAt(spec, material);
        Optional<VaultHook> vault = addon.getPlugin().getVault();
        if (unitPrice.isEmpty() || vault.isEmpty() || amount <= 0) {
            return 0;
        }
        // The safest ports will not touch contraband at any price, which is
        // what makes a smuggling run a voyage outward rather than a shortcut
        if (addon.getCustomsService() != null && addon.getCustomsService().isContraband(material)
                && !addon.getCustomsService().buysContraband(spec.band())) {
            User.getInstance(player).sendMessage("tradewinds.trade.contraband-refused");
            thud(player);
            return 0;
        }
        int count = Math.min(addon.getHoldService().count(player, material, sellableFilter()), amount);
        if (count <= 0) {
            User.getInstance(player).sendMessage("tradewinds.trade.not-stamped");
            thud(player);
            return 0;
        }
        TWTradeEvent event = new TWTradeEvent(player, spec, material, count, unitPrice.get(), true);
        org.bukkit.Bukkit.getPluginManager().callEvent(event);
        if (event.isCancelled()) {
            return 0;
        }
        int removed = addon.getHoldService().remove(player, material, count, sellableFilter());
        double total = PriceModel.round2(removed * unitPrice.get());
        vault.get().deposit(User.getInstance(player), total);
        addon.getIslandDataManager().adjustStock(spec, TradeCategory.of(material), removed);
        User.getInstance(player).sendMessage("tradewinds.trade.sold", "[amount]", String.valueOf(removed),
                "[material]", pretty(material), "[price]", String.format("%.2f", total));
        chime(player);
        return removed;
    }

    /**
     * Buy up to {@code amount} of a material from the island into the hold,
     * limited by balance and hold space.
     *
     * @return amount bought
     */
    public int buy(Player player, IslandSpec spec, Material material, int amount) {
        Optional<Double> unitPrice = playerBuysAt(spec, material);
        Optional<VaultHook> vault = addon.getPlugin().getVault();
        if (unitPrice.isEmpty() || vault.isEmpty() || amount <= 0) {
            return 0;
        }
        User user = User.getInstance(player);
        // Limit by balance
        double balance = vault.get().getBalance(user);
        int affordable = (int) Math.min(amount, Math.floor(balance / unitPrice.get()));
        if (affordable <= 0) {
            user.sendMessage("tradewinds.trade.cannot-afford");
            thud(player);
            return 0;
        }
        TWTradeEvent event = new TWTradeEvent(player, spec, material, affordable, unitPrice.get(), false);
        org.bukkit.Bukkit.getPluginManager().callEvent(event);
        if (event.isCancelled()) {
            thud(player);
            return 0;
        }
        // Limit by hold space: add first, pay for what fit. Bought goods are
        // customs stamped - the only goods traders will buy back.
        int added = addon.getHoldService().add(player, stamp(new ItemStack(material, affordable)));
        if (added <= 0) {
            user.sendMessage("tradewinds.trade.no-hold-space");
            thud(player);
            return 0;
        }
        double total = PriceModel.round2(added * unitPrice.get());
        vault.get().withdraw(user, total);
        addon.getIslandDataManager().adjustStock(spec, TradeCategory.of(material), -added);
        user.sendMessage("tradewinds.trade.bought", "[amount]", String.valueOf(added), "[material]",
                pretty(material), "[price]", String.format("%.2f", total));
        chime(player);
        return added;
    }

    /**
     * Buy a cargo expander: price doubles per expander already owned, capped.
     *
     * @return true if bought
     */
    public boolean buyExpander(Player player) {
        Optional<VaultHook> vault = addon.getPlugin().getVault();
        if (vault.isEmpty()) {
            return false;
        }
        User user = User.getInstance(player);
        int owned = addon.getPlayerDataManager().get(player.getUniqueId()).getExpandersPurchased();
        if (owned >= addon.getSettings().getExpanderCap()) {
            user.sendMessage("tradewinds.trade.expander-cap");
            thud(player);
            return false;
        }
        double price = PriceModel.expanderPrice(addon.getSettings().getExpanderBasePrice(), owned);
        if (!vault.get().has(user, price)) {
            user.sendMessage("tradewinds.trade.cannot-afford");
            thud(player);
            return false;
        }
        // An expander IS cargo space - it is carried, so it can always be
        // opened and filled (a moored or pocketed chest boat cannot be)
        vault.get().withdraw(user, price);
        player.getInventory().addItem(expanderItem()).values()
                .forEach(left -> player.getWorld().dropItem(player.getLocation(), left));
        var data = addon.getPlayerDataManager().get(player.getUniqueId());
        data.setExpandersPurchased(owned + 1);
        addon.getPlayerDataManager().save(player.getUniqueId());
        user.sendMessage("tradewinds.trade.expander-bought", "[price]", String.format("%.2f", price));
        chime(player);
        return true;
    }

    /**
     * The cargo expander item: a lore-marked shulker box. Purchase-only - with
     * no End there are no shulker shells to craft one (spec principle 7).
     */
    public ItemStack expanderItem() {
        // White, so it never reads as a vanilla purple shulker box
        ItemStack item = new ItemStack(Material.WHITE_SHULKER_BOX);
        ItemMeta meta = item.getItemMeta();
        if (meta != null) {
            meta.displayName(text("tradewinds.item.expander"));
            meta.lore(java.util.List.of(text("tradewinds.item.expander-lore")));
            meta.getPersistentDataContainer().set(EXPANDER_KEY, PersistentDataType.STRING, "expander");
            item.setItemMeta(meta);
        }
        return item;
    }

    /**
     * A translated component from the console locale - for item names and lore,
     * which belong to the item rather than to any one viewer.
     */
    private Component text(String key) {
        return User.getInstance(org.bukkit.Bukkit.getConsoleSender()).getTranslationAsComponent(key,
                new String[0]);
    }

    /**
     * A trading pouch: the first rung of cargo space, and the only hold a new
     * sailor has.
     */
    public ItemStack pouchItem() {
        ItemStack pouch = new ItemStack(Material.BUNDLE);
        ItemMeta meta = pouch.getItemMeta();
        if (meta != null) {
            meta.displayName(text("tradewinds.item.pouch"));
            meta.lore(java.util.List.of(text("tradewinds.item.pouch-lore")));
            pouch.setItemMeta(meta);
        }
        return pouch;
    }

    /**
     * Buy a trading pouch, up to the hold's pouch limit.
     *
     * @return true if bought
     */
    /**
     * What a pouch costs. Flat: pricing by how many the sailor carries would be
     * defeated by dropping one before buying and picking it up afterwards. The
     * max-bundles cap does the limiting instead.
     */
    public double pouchPrice(Player player) {
        return addon.getSettings().getPouchPrice();
    }

    /**
     * Is this sailor destitute - no cargo space, and unable to buy any?
     */
    public boolean isDestitute(Player player) {
        if (addon.getHoldService().pouchCount(player) > 0 || !addon.getHoldService().expanders(player).isEmpty()) {
            return false;
        }
        return addon.getPlugin().getVault()
                .map(vault -> vault.getBalance(User.getInstance(player)) < addon.getSettings().getPouchPrice())
                .orElse(false);
    }

    /**
     * The harbourmaster's charity: a sailor with no hold and no money to buy
     * one cannot earn anything at all - the market needs cargo space on both
     * sides of a trade - so the port gives them the bare minimum to work
     * again. Charity goods are unstamped and therefore unsellable, so there is
     * nothing here to farm.
     *
     * @return true if something was given
     */
    public boolean claimCharity(Player player) {
        User user = User.getInstance(player);
        var data = addon.getPlayerDataManager().get(player.getUniqueId());
        int cooldown = addon.getSettings().getCharityCooldownMinutes();
        if (cooldown <= 0 || !isDestitute(player)) {
            user.sendMessage("tradewinds.trade.charity-not-needed");
            thud(player);
            return false;
        }
        long wait = data.getLastCharity() + cooldown * 60_000L - System.currentTimeMillis();
        if (wait > 0) {
            user.sendMessage("tradewinds.trade.charity-cooldown", "[number]",
                    String.valueOf(Math.max(1, wait / 60_000L)));
            thud(player);
            return false;
        }
        data.setLastCharity(System.currentTimeMillis());
        addon.getPlayerDataManager().save(player.getUniqueId());
        player.getInventory().addItem(pouchItem()).values()
                .forEach(left -> player.getWorld().dropItem(player.getLocation(), left));
        // A hull too, if they have neither boat nor boat item
        if (!hasBoat(player)) {
            player.getInventory().addItem(new ItemStack(Material.OAK_BOAT)).values()
                    .forEach(left -> player.getWorld().dropItem(player.getLocation(), left));
        }
        user.sendMessage("tradewinds.trade.charity-given");
        chime(player);
        return true;
    }

    private boolean hasBoat(Player player) {
        if (player.getVehicle() instanceof org.bukkit.entity.Boat) {
            return true;
        }
        for (ItemStack stack : player.getInventory().getContents()) {
            if (stack != null && stack.getType().name().endsWith("_BOAT")) {
                return true;
            }
        }
        return false;
    }

    public boolean buyPouch(Player player) {
        Optional<VaultHook> vault = addon.getPlugin().getVault();
        if (vault.isEmpty()) {
            return false;
        }
        User user = User.getInstance(player);
        if (addon.getHoldService().pouchCount(player) >= addon.getSettings().getMaxBundles()) {
            user.sendMessage("tradewinds.trade.pouch-cap");
            thud(player);
            return false;
        }
        double price = pouchPrice(player);
        if (!vault.get().has(user, price)) {
            user.sendMessage("tradewinds.trade.cannot-afford");
            thud(player);
            return false;
        }
        vault.get().withdraw(user, price);
        player.getInventory().addItem(pouchItem()).values()
                .forEach(left -> player.getWorld().dropItem(player.getLocation(), left));
        user.sendMessage("tradewinds.trade.pouch-bought", "[price]", String.format("%.2f", price));
        chime(player);
        return true;
    }

    static String pretty(Material material) {
        return PriceEngine.prettify(material.name());
    }

    /**
     * A deal struck: bright coin-on-the-counter chime. Dialogs cover the chat
     * box, so the sound carries the outcome.
     */
    private void chime(Player player) {
        player.playSound(player.getLocation(), Sound.BLOCK_NOTE_BLOCK_PLING, 0.8f, 1.6f);
    }

    /**
     * A deal refused: dull anvil thud.
     */
    private void thud(Player player) {
        player.playSound(player.getLocation(), Sound.BLOCK_ANVIL_LAND, 0.5f, 1.4f);
    }
}
