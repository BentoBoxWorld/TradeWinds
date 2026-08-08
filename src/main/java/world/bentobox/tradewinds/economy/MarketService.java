package world.bentobox.tradewinds.economy;

import java.util.List;
import java.util.Optional;

import org.bukkit.Material;
import org.bukkit.Sound;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;

import world.bentobox.bentobox.api.user.User;
import world.bentobox.bentobox.hooks.VaultHook;
import world.bentobox.tradewinds.TradeWinds;
import world.bentobox.tradewinds.api.events.TWTradeEvent;
import world.bentobox.tradewinds.ocean.IslandSpec;
import world.bentobox.tradewinds.travel.BoatRanks;
import world.bentobox.tradewinds.travel.CargoStore;

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
    /**
     * Disambiguates User#getTranslationAsComponent, whose no-variable call is
     * ambiguous between the String... and TagResolver... overloads - and a
     * shared constant is not an array creation, which is Sonar's complaint.
     */
    private static final String[] NO_VARS = new String[0];


    private final TradeWinds addon;
    private final PriceEngine priceEngine;

    // S1192: Duplicate string constants
    private static final String KEY_CANNOT_AFFORD = "tradewinds.trade.cannot-afford";
    private static final String VAR_MATERIAL = "[material]";
    private static final String VAR_AMOUNT = "[amount]";
    private static final String VAR_PRICE = "[price]";
    private static final String KEY_BOAT_NOT_HERE = "tradewinds.trade.boat-not-here";
    private static final String VAR_SLOTS = "[slots]";

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
            user.sendMessage(KEY_CANNOT_AFFORD);
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
        if (material == Material.COMPASS) {
            bindShipsCompass(player, stores);
        }
        player.getInventory().addItem(stores).values()
                .forEach(left -> player.getWorld().dropItem(player.getLocation(), left));
        user.sendMessage("tradewinds.trade.bought", VAR_AMOUNT, String.valueOf(affordable), VAR_MATERIAL,
                pretty(material), VAR_PRICE, Money.format(addon, total));
        chime(player);
        return affordable;
    }

    /**
     * The ship's compass (Stage 7b): a compass bought at the outfitter by an
     * island member leaves the counter bound to their island - it points
     * home from anywhere, no lodestone block required (the binding is
     * untracked, so nothing has to exist at the target). Physical, lossable,
     * giftable to a crewmate. Without an island it stays a plain compass -
     * which points at world spawn, the spawn port, so it is never useless.
     */
    private void bindShipsCompass(Player player, ItemStack compass) {
        world.bentobox.tradewinds.travel.HomePort.islandOf(addon, player.getUniqueId()).ifPresent(island -> {
            if (!(compass.getItemMeta() instanceof org.bukkit.inventory.meta.CompassMeta meta)) {
                return;
            }
            org.bukkit.Location home = island.getCenter().clone();
            meta.setLodestone(home);
            meta.setLodestoneTracked(false);
            User console = User.getInstance(org.bukkit.Bukkit.getConsoleSender());
            meta.displayName(console.getTranslationAsComponent("tradewinds.item.ships-compass", NO_VARS));
            meta.lore(java.util.List.of(console.getTranslationAsComponent(
                    "tradewinds.item.ships-compass-lore", NO_VARS)));
            compass.setItemMeta(meta);
        });
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
    public Optional<Double> basePrice(ItemStack item) {
        double price = priceEngine.getPrice(item);
        return price > 0 ? Optional.of(price) : Optional.empty();
    }

    public Optional<Double> basePrice(Material material) {
        return basePrice(new ItemStack(material));
    }

    /**
     * What a player pays this island per unit of this exact item, or empty if
     * not tradeable. The ITEM matters, not just its type: a worn bow and a mint
     * one are different goods at the counter.
     */
    public Optional<Double> playerBuysAt(IslandSpec spec, ItemStack item) {
        return basePrice(item).map(base -> model().playerBuysAt(base * contrabandPremium(item.getType()),
                factor(spec, item.getType())));
    }

    public Optional<Double> playerBuysAt(IslandSpec spec, Material material) {
        return playerBuysAt(spec, new ItemStack(material));
    }

    /**
     * What this island pays a player per unit of this exact item, or empty if
     * not tradeable.
     */
    public Optional<Double> playerSellsAt(IslandSpec spec, ItemStack item) {
        return basePrice(item).map(base -> model().playerSellsAt(base * contrabandPremium(item.getType()),
                factor(spec, item.getType())));
    }

    public Optional<Double> playerSellsAt(IslandSpec spec, Material material) {
        return playerSellsAt(spec, new ItemStack(material));
    }

    /**
     * Whether an item is worth putting back on a shelf: enchanted, renamed, or
     * simply valuable. Ordinary cargo is not interesting to find, and would only
     * bury the things that are.
     *
     * @param item the item
     * @return true if it should resurface
     */
    public boolean isNotable(ItemStack item) {
        if (item == null || !addon.getSettings().isResaleEnabled()) {
            return false;
        }
        if (!item.getEnchantments().isEmpty()) {
            return true;
        }
        var meta = item.getItemMeta();
        if (meta != null && meta.hasDisplayName()) {
            return true;
        }
        return basePrice(item).orElse(0.0) >= addon.getSettings().getResaleNotableValue();
    }

    /**
     * Ship a notable item on to ANOTHER port's shelf, so it can be found by
     * someone else. Never the port it was sold at: an unpredictable destination
     * is what keeps shelves from becoming an alt-account laundering channel.
     *
     * @param soldAt where it was sold
     * @param item the item, one unit
     */
    public void consign(IslandSpec soldAt, ItemStack item) {
        if (!isNotable(item) || addon.getOverWorld() == null) {
            return;
        }
        List<IslandSpec> elsewhere = addon.getOceanEngine(addon.getOverWorld().getSeed())
                .islandsNear(soldAt.centerX(), soldAt.centerZ(), addon.getSettings().getResaleShipRadius())
                .stream().filter(other -> other.cellX() != soldAt.cellX() || other.cellZ() != soldAt.cellZ())
                .toList();
        if (elsewhere.isEmpty()) {
            return;
        }
        // Seeded by the item and the port, so it is arbitrary but not random -
        // scripts cannot re-roll it, and it stays the same on a resumed sale
        // floorMod, not Math.abs(...) %: abs(Integer.MIN_VALUE) is still
        // negative, and one unlucky hash would throw on the index below
        int pick = Math.floorMod((item.getType().name() + soldAt.cellX() + "," + soldAt.cellZ()).hashCode(),
                elsewhere.size());
        ItemStack one = CargoStore.copyOf(item, 1);
        addon.getIslandDataManager().consign(elsewhere.get(pick), one, addon.getSettings().getResaleSlots());
    }

    /**
     * A port's secondhand shelf.
     */
    public List<ItemStack> shelf(IslandSpec spec) {
        if (!addon.getSettings().isResaleEnabled()) {
            return List.of();
        }
        return addon.getIslandDataManager().shelf(spec, addon.getSettings().getResaleTtlHours()).stream()
                .map(world.bentobox.tradewinds.dataobjects.TWIslandData.ShelfItem::getItem)
                .filter(java.util.Objects::nonNull).toList();
    }

    /**
     * What a shelf item costs: book price plus the trader's markup. Above what
     * the same port would pay for it, or the shelf would be a money printer.
     */
    public Optional<Double> shelfPrice(ItemStack item) {
        return basePrice(item)
                .map(base -> Math.max(1, Math.ceil(base * addon.getSettings().getResaleMarkup())));
    }

    /**
     * Buy one listing off a port's shelf, into the hold. It arrives MARKED as
     * trader-bought, like anything else a market sells.
     *
     * @return true if bought
     */
    public boolean buyFromShelf(Player player, IslandSpec spec, int index) {
        User user = User.getInstance(player);
        Optional<VaultHook> vault = addon.getPlugin().getVault();
        List<ItemStack> shelf = shelf(spec);
        if (vault.isEmpty() || index < 0 || index >= shelf.size()) {
            return false;
        }
        if (!boatIsHere(player, spec)) {
            user.sendMessage(KEY_BOAT_NOT_HERE);
            thud(player);
            return false;
        }
        ItemStack item = shelf.get(index);
        Optional<Double> price = shelfPrice(item);
        if (price.isEmpty()) {
            return false;
        }
        if (vault.get().getBalance(user) < price.get()) {
            user.sendMessage(KEY_CANNOT_AFFORD);
            thud(player);
            return false;
        }
        ItemStack marked = world.bentobox.tradewinds.travel.CargoMark.marked(item);
        if (addon.getHoldService().add(player, marked, 1) <= 0) {
            user.sendMessage("tradewinds.trade.no-hold-space");
            thud(player);
            return false;
        }
        // Only now take it off the shelf, so a full hold cannot destroy a listing
        addon.getIslandDataManager().takeFromShelf(spec, index);
        vault.get().withdraw(user, price.get());
        user.sendMessage("tradewinds.trade.shelf-bought", VAR_MATERIAL, pretty(item.getType()), VAR_PRICE,
                Money.format(addon, price.get()));
        chime(player);
        return true;
    }

    /**
     * A snapshot of what this port pays, by trade category - what goes into a
     * trader's logbook. One representative good per category, because prices
     * move by category: type, tech, band and drift all shift a whole category
     * together.
     *
     * @param spec the island
     * @return category name -> unit sell price
     */
    public java.util.Map<String, Integer> currentPrices(IslandSpec spec) {
        java.util.Map<String, Integer> prices = new java.util.HashMap<>();
        for (TradeCategory category : TradeCategory.values()) {
            TypeEconomy.representative(category).ifPresent(sample -> playerSellsAt(spec, sample)
                    .ifPresent(unit -> prices.put(category.name(), unit.intValue())));
        }
        return prices;
    }

    /**
     * Buy a harbour report: the broker fills in your logbook for the ports
     * within reach of this one. Reach scales with the port's tech level, which
     * gives a developed island a role beyond what is on its shelves - and a
     * reason to call at a hub you are not trading with.
     *
     * @param player the player
     * @param spec the island whose broker is selling
     * @return how many ports were reported on, 0 if none or unaffordable
     */
    public int buyMarketReport(Player player, IslandSpec spec) {
        User user = User.getInstance(player);
        Optional<VaultHook> vault = addon.getPlugin().getVault();
        if (!addon.getSettings().isPriceLogbookEnabled() || vault.isEmpty() || addon.getOverWorld() == null) {
            return 0;
        }
        List<IslandSpec> ports = reportablePorts(spec);
        if (ports.isEmpty()) {
            user.sendMessage("tradewinds.trade.report-nothing");
            thud(player);
            return 0;
        }
        double cost = Math.ceil(ports.size() * addon.getSettings().getMarketReportPricePerIsland());
        if (vault.get().getBalance(user) < cost) {
            user.sendMessage(KEY_CANNOT_AFFORD);
            thud(player);
            return 0;
        }
        vault.get().withdraw(user, cost);
        var data = addon.getPlayerDataManager().get(player.getUniqueId());
        long now = System.currentTimeMillis();
        for (IslandSpec port : ports) {
            data.chart(port);
            data.logPrices(port, currentPrices(port), now);
        }
        addon.getPlayerDataManager().save(player.getUniqueId());
        user.sendMessage("tradewinds.trade.report-bought", world.bentobox.bentobox.api.localization
                .TextVariables.NUMBER, String.valueOf(ports.size()), VAR_PRICE, Money.format(addon, cost));
        chime(player);
        return ports.size();
    }

    /**
     * The ports a broker here can report on: everything within this island's
     * tech-scaled reach, itself excluded (you are standing in it).
     */
    public List<IslandSpec> reportablePorts(IslandSpec spec) {
        int radius = (int) Math.round(addon.getSettings().getMarketReportRadiusPerTechLevel()
                * spec.techLevel());
        if (radius <= 0 || addon.getOverWorld() == null) {
            return List.of();
        }
        return addon.getOceanEngine(addon.getOverWorld().getSeed())
                .islandsNear(spec.centerX(), spec.centerZ(), radius).stream()
                .filter(other -> other.cellX() != spec.cellX() || other.cellZ() != spec.cellZ()).toList();
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
        // Otherwise it must be PLACED here: found by identity among loaded
        // entities and judged on its true position - the remembered position
        // goes stale the moment a dismounted boat drifts, and trusting it let
        // the yard sell refits to hulls it could not reach (2026-08-04)
        return addon.getBoatService().findPlaced(hold.get())
                .map(placed -> spec.distanceSquared(placed.getLocation().getBlockX(),
                        placed.getLocation().getBlockZ()) <= (long) range * range)
                .orElse(false);
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
     * Whether a material is a recognised trade good somewhere in the ocean.
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
    public boolean handlesValue(IslandSpec spec, ItemStack item) {
        double perLevel = addon.getSettings().getSalvageValuePerTechLevel();
        if (perLevel <= 0 || isTradeGood(item.getType())) {
            return true;
        }
        // The ITEM's value, so an enchanted sword can outgrow a port its plain
        // twin would have been welcome at
        return basePrice(item).orElse(0.0) <= perLevel * spec.techLevel();
    }

    public boolean handlesValue(IslandSpec spec, Material material) {
        return handlesValue(spec, new ItemStack(material));
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
        return sell(player, spec, new ItemStack(material), amount);
    }

    /**
     * Sell up to {@code amount} of one exact item from the hold.
     *
     * @return amount sold
     */
    public int sell(Player player, IslandSpec spec, ItemStack item, int amount) {
        Material material = item.getType();
        if (!boatIsHere(player, spec)) {
            User.getInstance(player).sendMessage(KEY_BOAT_NOT_HERE);
            thud(player);
            return 0;
        }
        Optional<Double> unitPrice = playerSellsAt(spec, item);
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
        if (!handlesValue(spec, item)) {
            User.getInstance(player).sendMessage("tradewinds.trade.too-advanced", VAR_MATERIAL, pretty(material),
                    "[name]", spec.name());
            thud(player);
            return 0;
        }
        int count = Math.min(addon.getHoldService().count(player, item), amount);
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
        int removed = addon.getHoldService().remove(player, item, count);
        double total = PriceModel.round2(removed * unitPrice.get());
        vault.get().deposit(User.getInstance(player), total);
        // Drift moves on the VALUE of the trade, not the item count: a port that
        // has just paid out for ten diamonds is far closer to saturated than one
        // that bought ten wheat
        addon.getIslandDataManager().adjustStockValue(spec, driftPool(material), (int) Math.round(total));
        // Notable goods go back out for sale somewhere else rather than vanishing
        consign(spec, item);
        User.getInstance(player).sendMessage("tradewinds.trade.sold", VAR_AMOUNT, String.valueOf(removed),
                VAR_MATERIAL, pretty(material), VAR_PRICE, Money.format(addon, total));
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
            User.getInstance(player).sendMessage(KEY_BOAT_NOT_HERE);
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
            user.sendMessage(KEY_CANNOT_AFFORD);
            thud(player);
            return 0;
        }
        TWTradeEvent event = new TWTradeEvent(player, spec, material, affordable, unitPrice.get(), false);
        org.bukkit.Bukkit.getPluginManager().callEvent(event);
        if (event.isCancelled()) {
            thud(player);
            return 0;
        }
        // Limit by hold slots: add first, pay for what fit. Bought cargo is
        // MARKED, so it cannot be withdrawn ashore later - speculate on a cargo
        // and you must find a buyer for it
        int added = addon.getHoldService().add(player,
                world.bentobox.tradewinds.travel.CargoMark.marked(new ItemStack(material)), affordable);
        if (added <= 0) {
            user.sendMessage("tradewinds.trade.no-hold-space");
            thud(player);
            return 0;
        }
        double total = PriceModel.round2(added * unitPrice.get());
        vault.get().withdraw(user, total);
        addon.getIslandDataManager().adjustStockValue(spec, driftPool(material), -(int) Math.round(total));
        user.sendMessage("tradewinds.trade.bought", VAR_AMOUNT, String.valueOf(added), VAR_MATERIAL,
                pretty(material), VAR_PRICE, Money.format(addon, total));
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
        var owned = addon.getHoldService().active(player.getUniqueId());
        if (!vault.get().has(user, price)) {
            user.sendMessage(KEY_CANNOT_AFFORD);
            thud(player);
            return false;
        }
        vault.get().withdraw(user, price);
        if (owned.isEmpty()) {
            // No ship at all: they are buying one outright, hull in hand
            var fresh = addon.getBoatService().createFor(player, rank.material());
            addon.getBoatService().logbook("bought by " + player.getName() + " at " + spec.name(), fresh,
                    player.getLocation());
            addon.getBoatService().giveBoatItem(player, fresh);
            user.sendMessage("tradewinds.trade.boat-bought-first", VAR_MATERIAL, pretty(rank.material()),
                    VAR_SLOTS, String.valueOf(rank.slots()), VAR_PRICE, Money.format(addon, price));
        } else if (isTradeIn(player, spec, rank)) {
            // The ship is at the quay and the new hull is bigger: a trade-in
            // - same record, cargo stays, old hull broken up
            addon.getBoatService().refit(player, owned.get(), rank.material());
            user.sendMessage("tradewinds.trade.boat-bought", VAR_MATERIAL, pretty(rank.material()),
                    VAR_SLOTS, String.valueOf(rank.slots()), VAR_PRICE, Money.format(addon, price));
        } else {
            // Bought outright (ruled 2026-08-05): the yard ALWAYS sells - a
            // sailor whose ship is an ocean away, or who wants a smaller
            // hull, walks out with a new boat and their old one is left
            // unowned wherever it lies, cargo aboard, first come first
            // served. The dialog confirmed this before the money moved.
            var old = owned.get();
            String oldName = Material.matchMaterial(old.getMaterial()) == null ? old.getMaterial()
                    : pretty(Material.matchMaterial(old.getMaterial()));
            var fresh = addon.getBoatService().createFor(player, rank.material());
            addon.getBoatService().logbook("bought by " + player.getName() + " at " + spec.name()
                    + ", replacing their " + old.getMaterial(), fresh, player.getLocation());
            addon.getBoatService().logbook("demoted to OLD BOAT (owner bought another)", old, null);
            addon.getBoatService().giveBoatItem(player, fresh);
            // "Wherever it lies" may be the buyer's own pack: a carried hull
            // is shed at the quay, or the sailor walks out with two boats -
            // the 2026-08-02 two-hulls exploit through the shop door
            addon.getBoatListener().quietSwaps(player.getUniqueId());
            addon.getBoatService().shedCarriedHull(player, old);
            // The plate on the abandoned hull flips to UNOWNED, if it is loaded
            addon.getBoatService().relabel(old);
            user.sendMessage("tradewinds.trade.boat-replaced", VAR_MATERIAL, pretty(rank.material()),
                    VAR_SLOTS, String.valueOf(rank.slots()), VAR_PRICE, Money.format(addon, price),
                    "[old]", oldName);
        }
        chime(player);
        return true;
    }

    /**
     * Whether buying this rank would be a trade-in refit: the current ship is
     * at THIS island and the new hull is bigger. Everything else is an
     * outright purchase that abandons the current boat where it lies.
     */
    public boolean isTradeIn(Player player, IslandSpec spec, BoatRanks.Rank rank) {
        Material current = addon.getHoldService().boat(player);
        return current != null && boatIsHere(player, spec)
                && rank.slots() > addon.getBoatRanks().slots(current);
    }

    /**
     * Whether buying this rank would abandon the player's current boat - the
     * case the shipwright dialog puts a confirmation in front of.
     */
    public boolean wouldReplaceCurrent(Player player, IslandSpec spec, BoatRanks.Rank rank) {
        return addon.getHoldService().active(player.getUniqueId()).isPresent()
                && !isTradeIn(player, spec, rank);
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
        if (spec.techLevel() < world.bentobox.tradewinds.ocean.OceanEngine.MAX_TECH_LEVEL) {
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
            user.sendMessage(KEY_CANNOT_AFFORD);
            thud(player);
            return false;
        }
        vault.get().withdraw(user, price);
        addon.getHoldService().installExpander(id);
        user.sendMessage("tradewinds.trade.expander-bought", VAR_PRICE, Money.format(addon, price));
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
