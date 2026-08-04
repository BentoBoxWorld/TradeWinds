package world.bentobox.tradewinds.economy;

import java.util.Optional;

import org.bukkit.Material;
import org.bukkit.Sound;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;

import world.bentobox.bentobox.api.user.User;
import world.bentobox.bentobox.hooks.VaultHook;
import world.bentobox.tradewinds.TradeWinds;
import world.bentobox.tradewinds.api.events.TWTradeEvent;
import world.bentobox.tradewinds.galaxy.IslandSpec;
import world.bentobox.tradewinds.travel.BoatRanks;

/**
 * The market: prices and trades. Base prices come from the embedded
 * {@link PriceEngine} (config table plus recipe derivation - BlueBook logic
 * carried in-addon). Island prices apply the type/tech/band/stock model
 * ({@link PriceModel}); all transactions move goods through the player's
 * VIRTUAL hold only and money through Vault.
 * <p>
 * Customs stamping is gone (hold plan, 2026-08-01): the market buys anything
 * the hold carries. What keeps farming from minting free money now is
 * capacity (a hold slot is scarce) and the per-island stock pools (selling
 * into an island crushes its price toward the drift floor).
 *
 * @author tastybento
 */
public class MarketService {

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
     * fuel; plus per-type gear (smiths at INDUSTRIAL, beds at AGRICULTURAL,
     * rods at FISHING).
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
     * Buy stores delivered to the player's INVENTORY, not the hold: outfitter
     * supplies (food, gear, beds, rods). These are for using, not for resale.
     * Anything that will not fit is dropped at the player's feet.
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
                pretty(material), "[price]", Money.format(addon, total));
        chime(player);
        return affordable;
    }

    /**
     * The configured price model.
     */
    public PriceModel model() {
        var s = addon.getSettings();
        return new PriceModel(s.getProduceFactor(), s.getDemandFactor(), s.getBandDemandBonus(), s.getBuySpread(),
                s.getSellSpread(), s.getDriftValueScale(), s.getDriftMin(), s.getDriftMax(), s.getTechPriceStep());
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
     * Whether the player's boat is at this island - the cargo lives in the
     * boat, so trading needs the ship at the quay. "Here" means inside the
     * island's PROTECTED space (ruled 2026-08-02): a hull a thousand blocks
     * out is not a port call. Carrying the boat as an item counts - it is
     * with you.
     *
     * @param player the player
     * @param spec the island
     * @return true if they may trade here
     */
    public boolean boatIsHere(Player player, IslandSpec spec) {
        var hold = addon.getHoldService().active(player.getUniqueId());
        if (hold.isEmpty()) {
            return false;
        }
        int range = addon.getSettings().getIslandProtectionRange();
        // In hand or in the pack: the boat is wherever the sailor is
        for (ItemStack stack : player.getInventory().getContents()) {
            if (stack != null && hold.get().getUniqueId()
                    .equals(world.bentobox.tradewinds.travel.BoatService.boatId(stack))) {
                return spec.distanceSquared(player.getLocation().getBlockX(),
                        player.getLocation().getBlockZ()) <= (long) range * range;
            }
        }
        // Otherwise: wherever we last saw it
        if (hold.get().getWorld() == null || hold.get().getWorld().isEmpty()) {
            return false;
        }
        return spec.distanceSquared(hold.get().getX(), hold.get().getZ()) <= (long) range * range;
    }

    /**
     * Whether this island will deal with this player at all.
     * <p>
     * A fugitive is barred from the safe bands entirely (spec principle 4):
     * crime pays, into danger.
     */
    public boolean willTradeWith(Player player, IslandSpec spec) {
        if (addon.getReputationService() == null || !addon.getSettings().isCrimeEnabled()) {
            return true;
        }
        return !addon.getReputationService().standing(player.getUniqueId()).isBarredFromSafeTrade()
                || spec.band().ordinal() >= addon.getSettings().safestFugitiveTrader().ordinal();
    }

    /**
     * The smuggler's premium: what a black market pays over the honest price
     * of the same goods, and the main lever for how profitable smuggling is.
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

    /**
     * Whether a material is a recognised trade good somewhere in the galaxy.
     * Everything else is salvage: still sellable, but at a discount and into a
     * pool of its own.
     *
     * @param material the material
     * @return true if any island type stocks it
     */
    public boolean isTradeGood(Material material) {
        // Contraband is nobody's shelf stock but is emphatically not junk - it
        // has the smuggler's premium instead
        if (addon.getCustomsService() != null && addon.getCustomsService().isContraband(material)) {
            return true;
        }
        return TypeEconomy.tradeGoods().contains(material);
    }

    /**
     * Which stock pool a material's trade drifts against. Salvage has its own,
     * so dumping junk cannot crater the legitimate cargo of the same category.
     *
     * @param material the material
     * @return the pool
     */
    public TradeCategory driftPool(Material material) {
        return isTradeGood(material) ? TradeCategory.of(material) : TradeCategory.SALVAGE;
    }

    /**
     * Whether this port is developed enough to deal in a single item of this
     * value at all. A TL1 fishing hamlet has no use for a diamond sword and
     * nobody there could pay for one; a TL7 hub will take anything. This is what
     * gives loot a destination, and a destination is a voyage.
     * <p>
     * Recognised trade goods are exempt. A port must always deal in what its own
     * shelves stock, or a low-tech luxury island would refuse the very gems it
     * demands - which reads as a broken market, not as a tech gate.
     *
     * @param spec the island
     * @param material the material
     * @return true if the port will handle it
     */
    public boolean handlesValue(IslandSpec spec, Material material) {
        double perLevel = addon.getSettings().getSalvageValuePerTechLevel();
        if (perLevel <= 0 || isTradeGood(material)) {
            return true;
        }
        return basePrice(material).orElse(0.0) <= perLevel * spec.techLevel();
    }

    private double factor(IslandSpec spec, Material material) {
        TradeCategory category = TradeCategory.of(material);
        boolean contraband = addon.getCustomsService() != null
                && addon.getCustomsService().isContraband(material);
        boolean salvage = !isTradeGood(material);
        // A port that deals in contraband always wants it - it is not on
        // anybody's official produce list, and demand is what makes the band
        // bonus apply, so the rougher the port the better it pays.
        // Salvage is on nobody's manifest either, but unlike contraband nobody
        // is short of it: neutral affinity, no band bonus.
        boolean demands = contraband || (!salvage && TypeEconomy.demands(spec.type()).contains(category));
        boolean produces = !contraband && !salvage && TypeEconomy.produces(spec.type()).contains(category);
        double economic = model().economicFactor(produces, demands, spec.band().ordinal(),
                addon.getIslandDataManager().getStockValue(spec, driftPool(material)));
        // The tech tilt: high tech sells finished cheap and buys raw dear, so
        // the best routes are tech DIFFERENTIALS. Contraband is exempt - the
        // black market premium is its own lever and answers to nothing else.
        if (!contraband) {
            economic *= model().techFactor(category.isFinished(), category.isRaw(), spec.techLevel());
        }
        // Salvage pays worse than proper cargo, or scavenging and piracy would
        // out-earn the trade game they are supposed to orbit
        if (salvage) {
            economic *= addon.getSettings().getSalvageDiscount();
        }
        return economic;
    }

    /**
     * Sell up to {@code amount} of one material from the player's hold to the
     * island (Integer.MAX_VALUE = everything). No stamps, no filters: if it
     * is in the hold and this port will touch it, it sells.
     *
     * @return amount sold
     */
    public int sell(Player player, IslandSpec spec, Material material, int amount) {
        if (!boatIsHere(player, spec)) {
            User.getInstance(player).sendMessage("tradewinds.trade.boat-not-here");
            thud(player);
            return 0;
        }
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
        // Too rich for this port to handle - take it somewhere more developed
        if (!handlesValue(spec, material)) {
            User.getInstance(player).sendMessage("tradewinds.trade.too-advanced", "[material]", pretty(material),
                    "[name]", spec.name());
            thud(player);
            return 0;
        }
        int count = Math.min(addon.getHoldService().count(player, material), amount);
        if (count <= 0) {
            User.getInstance(player).sendMessage("tradewinds.trade.nothing-of-that");
            thud(player);
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
        // Drift moves on the VALUE of the trade, not the item count: a port that
        // has just paid out for ten diamonds is far closer to saturated than one
        // that bought ten wheat
        addon.getIslandDataManager().adjustStockValue(spec, driftPool(material), (int) Math.round(total));
        User.getInstance(player).sendMessage("tradewinds.trade.sold", "[amount]", String.valueOf(removed),
                "[material]", pretty(material), "[price]", Money.format(addon, total));
        chime(player);
        return removed;
    }

    /**
     * Buy up to {@code amount} of a material from the island into the hold,
     * limited by balance and hold slots.
     *
     * @return amount bought
     */
    public int buy(Player player, IslandSpec spec, Material material, int amount) {
        if (!boatIsHere(player, spec)) {
            User.getInstance(player).sendMessage("tradewinds.trade.boat-not-here");
            thud(player);
            return 0;
        }
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
        // Limit by hold slots: add first, pay for what fit
        int added = addon.getHoldService().add(player, material, affordable);
        if (added <= 0) {
            user.sendMessage("tradewinds.trade.no-hold-space");
            thud(player);
            return 0;
        }
        double total = PriceModel.round2(added * unitPrice.get());
        vault.get().withdraw(user, total);
        addon.getIslandDataManager().adjustStockValue(spec, driftPool(material), -(int) Math.round(total));
        user.sendMessage("tradewinds.trade.bought", "[amount]", String.valueOf(added), "[material]",
                pretty(material), "[price]", Money.format(addon, total));
        chime(player);
        return added;
    }

    /**
     * Buy a boat from the shipwright: an UPGRADE, never a second boat. The
     * replaced boat is destroyed; the hold's contents stay put and the slot
     * count grows. The listing is validated against the island's tech level
     * and the player's current boat, so a stale dialog cannot downgrade or
     * out-tech the port.
     *
     * @return true if bought
     */
    public boolean buyBoat(Player player, IslandSpec spec, BoatRanks.Rank rank) {
        Optional<VaultHook> vault = addon.getPlugin().getVault();
        if (vault.isEmpty()) {
            return false;
        }
        User user = User.getInstance(player);
        Material current = addon.getHoldService().boat(player);
        boolean listed = addon.getBoatRanks().shopListing(current, spec.techLevel()).stream()
                .anyMatch(r -> r.material() == rank.material());
        if (!listed) {
            user.sendMessage("tradewinds.trade.boat-not-sold-here");
            thud(player);
            return false;
        }
        double price = addon.getBoatRanks().price(rank);
        // A refit is a trade-in: the yard needs the old hull in front of it.
        // (Buying your FIRST boat has nothing to trade in, and must always be
        // possible - it is how a sailor who lost their ship gets off the
        // island.)
        var owned = addon.getHoldService().active(player.getUniqueId());
        if (owned.isPresent() && !boatIsHere(player, spec)) {
            user.sendMessage("tradewinds.trade.refit-needs-ship");
            thud(player);
            return false;
        }
        if (!vault.get().has(user, price)) {
            user.sendMessage("tradewinds.trade.cannot-afford");
            thud(player);
            return false;
        }
        vault.get().withdraw(user, price);
        if (owned.isEmpty()) {
            // No ship at all: they are buying one outright, hull in hand
            var fresh = addon.getBoatService().createFor(player, rank.material());
            addon.getBoatService().giveBoatItem(player, fresh);
        } else {
            addon.getBoatService().refit(player, owned.get(), rank.material());
        }
        user.sendMessage("tradewinds.trade.boat-bought", "[material]", pretty(rank.material()),
                "[slots]", String.valueOf(rank.slots()), "[price]", Money.format(addon, price));
        chime(player);
        return true;
    }

    /**
     * Buy and install a cargo expander: the endgame money sink. Sold only at
     * top-tech ports (the sink has a home port), installs only into a Pale
     * Oak Chest Boat with a free slot, and the price doubles per expander
     * already installed - the wallet is the cap.
     *
     * @return true if bought
     */
    public boolean buyExpander(Player player, IslandSpec spec) {
        Optional<VaultHook> vault = addon.getPlugin().getVault();
        if (vault.isEmpty()) {
            return false;
        }
        User user = User.getInstance(player);
        if (spec.techLevel() < world.bentobox.tradewinds.galaxy.GalaxyEngine.MAX_TECH_LEVEL) {
            user.sendMessage("tradewinds.trade.expander-not-sold-here");
            thud(player);
            return false;
        }
        java.util.UUID id = player.getUniqueId();
        if (!addon.getHoldService().canInstallExpander(id)) {
            user.sendMessage("tradewinds.trade.expander-needs-flagship");
            thud(player);
            return false;
        }
        double price = PriceModel.expanderPrice(addon.getSettings().getExpanderBasePrice(),
                addon.getHoldService().expanderCount(id));
        if (!vault.get().has(user, price)) {
            user.sendMessage("tradewinds.trade.cannot-afford");
            thud(player);
            return false;
        }
        vault.get().withdraw(user, price);
        addon.getHoldService().installExpander(id);
        user.sendMessage("tradewinds.trade.expander-bought", "[price]", Money.format(addon, price));
        chime(player);
        return true;
    }

    /**
     * Is this sailor destitute - no boat, and unable to buy even the smallest?
     */
    public boolean isDestitute(Player player) {
        if (addon.getHoldService().boat(player) != null) {
            return false;
        }
        double raftPrice = addon.getBoatRanks().ladder().stream().findFirst()
                .map(addon.getBoatRanks()::price).orElse(100.0);
        return addon.getPlugin().getVault()
                .map(vault -> vault.getBalance(User.getInstance(player)) < raftPrice)
                .orElse(false);
    }

    /**
     * The harbourmaster's charity: a sailor with no boat has no hold, and
     * with no hold they can neither earn nor leave - so the port gives them
     * the barest hull that floats (a bamboo raft).
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
        Material raft = addon.getBoatRanks().ladder().stream().findFirst()
                .map(BoatRanks.Rank::material).orElse(Material.BAMBOO_RAFT);
        var hold = addon.getBoatService().createFor(player, raft);
        addon.getBoatService().giveBoatItem(player, hold);
        user.sendMessage("tradewinds.trade.charity-given");
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
