package world.bentobox.tradewinds.economy;

import java.util.Optional;

import org.bukkit.Material;
import org.bukkit.NamespacedKey;
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
        return basePrice(material).map(base -> model().playerBuysAt(base, factor(spec, material)));
    }

    /**
     * What this island pays a player per unit, or empty if not tradeable.
     */
    public Optional<Double> playerSellsAt(IslandSpec spec, Material material) {
        return basePrice(material).map(base -> model().playerSellsAt(base, factor(spec, material)));
    }

    private double factor(IslandSpec spec, Material material) {
        TradeCategory category = TradeCategory.of(material);
        return model().economicFactor(TypeEconomy.produces(spec.type()).contains(category),
                TypeEconomy.demands(spec.type()).contains(category), spec.band().ordinal(),
                addon.getIslandDataManager().getStock(spec, category));
    }

    /**
     * Sell everything of one material from the player's hold to the island.
     *
     * @return amount sold
     */
    public int sell(Player player, IslandSpec spec, Material material) {
        Optional<Double> unitPrice = playerSellsAt(spec, material);
        Optional<VaultHook> vault = addon.getPlugin().getVault();
        if (unitPrice.isEmpty() || vault.isEmpty()) {
            return 0;
        }
        int count = addon.getHoldService().count(player, material);
        if (count <= 0) {
            return 0;
        }
        TWTradeEvent event = new TWTradeEvent(player, spec, material, count, unitPrice.get(), true);
        org.bukkit.Bukkit.getPluginManager().callEvent(event);
        if (event.isCancelled()) {
            return 0;
        }
        int removed = addon.getHoldService().remove(player, material, count);
        double total = PriceModel.round2(removed * unitPrice.get());
        vault.get().deposit(User.getInstance(player), total);
        addon.getIslandDataManager().adjustStock(spec, TradeCategory.of(material), removed);
        User.getInstance(player).sendMessage("tradewinds.trade.sold", "[amount]", String.valueOf(removed),
                "[material]", pretty(material), "[price]", String.format("%.2f", total));
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
            return 0;
        }
        TWTradeEvent event = new TWTradeEvent(player, spec, material, affordable, unitPrice.get(), false);
        org.bukkit.Bukkit.getPluginManager().callEvent(event);
        if (event.isCancelled()) {
            return 0;
        }
        // Limit by hold space: add first, pay for what fit
        int added = addon.getHoldService().add(player, new ItemStack(material, affordable));
        if (added <= 0) {
            user.sendMessage("tradewinds.trade.no-hold-space");
            return 0;
        }
        double total = PriceModel.round2(added * unitPrice.get());
        vault.get().withdraw(user, total);
        addon.getIslandDataManager().adjustStock(spec, TradeCategory.of(material), -added);
        user.sendMessage("tradewinds.trade.bought", "[amount]", String.valueOf(added), "[material]",
                pretty(material), "[price]", String.format("%.2f", total));
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
            return false;
        }
        double price = PriceModel.expanderPrice(addon.getSettings().getExpanderBasePrice(), owned);
        if (!vault.get().has(user, price)) {
            user.sendMessage("tradewinds.trade.cannot-afford");
            return false;
        }
        // Must go into the hold (the chest boat) - expanders never ride pockets
        int added = addon.getHoldService().add(player, expanderItem());
        if (added <= 0) {
            user.sendMessage("tradewinds.trade.no-hold-space");
            return false;
        }
        vault.get().withdraw(user, price);
        var data = addon.getPlayerDataManager().get(player.getUniqueId());
        data.setExpandersPurchased(owned + 1);
        addon.getPlayerDataManager().save(player.getUniqueId());
        user.sendMessage("tradewinds.trade.expander-bought", "[price]", String.format("%.2f", price));
        return true;
    }

    /**
     * The cargo expander item: a lore-marked shulker box. Purchase-only - with
     * no End there are no shulker shells to craft one (spec principle 7).
     */
    public ItemStack expanderItem() {
        ItemStack item = new ItemStack(Material.SHULKER_BOX);
        ItemMeta meta = item.getItemMeta();
        if (meta != null) {
            meta.displayName(Component.text("Cargo Expander", NamedTextColor.GOLD));
            meta.lore(java.util.List.of(Component.text("Expands your boat's hold.", NamedTextColor.GRAY),
                    Component.text("Stow in a chest boat.", NamedTextColor.GRAY)));
            meta.getPersistentDataContainer().set(EXPANDER_KEY, PersistentDataType.STRING, "expander");
            item.setItemMeta(meta);
        }
        return item;
    }

    static String pretty(Material material) {
        return PriceEngine.prettify(material.name());
    }
}
