package world.bentobox.tradewinds.dataobjects;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import world.bentobox.bentobox.database.Database;
import world.bentobox.tradewinds.TradeWinds;
import world.bentobox.tradewinds.economy.TradeCategory;
import world.bentobox.tradewinds.galaxy.IslandSpec;

/**
 * Cache-in-front-of-Database manager for {@link TWIslandData}. Stock decays
 * toward equilibrium lazily on access - markets recover from gluts and
 * shortages over real time.
 * <p>
 * Stock is denominated in coins (the trader's purse), so a port's capacity to
 * absorb goods is a capacity to spend money, not to count crates.
 *
 * @author tastybento
 */
public class IslandDataManager {

    private final TradeWinds addon;
    private final Database<TWIslandData> handler;
    private final Map<String, TWIslandData> cache = new ConcurrentHashMap<>();

    public IslandDataManager(TradeWinds addon) {
        this.addon = addon;
        this.handler = new Database<>(addon, TWIslandData.class);
    }

    private static String key(IslandSpec spec) {
        return spec.cellX() + "," + spec.cellZ();
    }

    private TWIslandData get(IslandSpec spec) {
        return cache.computeIfAbsent(key(spec), k -> {
            if (handler.objectExists(k)) {
                TWIslandData loaded = handler.loadObject(k);
                if (loaded != null) {
                    return loaded;
                }
            }
            return new TWIslandData(k);
        });
    }

    /**
     * The island's current stock position for a category in coins, decayed to
     * now.
     */
    public int getStockValue(IslandSpec spec, TradeCategory category) {
        TWIslandData data = get(spec);
        decay(data);
        return data.getStockValue().getOrDefault(category.name(), 0);
    }

    /**
     * Adjust a category's stock position after a trade, by the <b>coin value</b>
     * of the transaction: players selling to the island push it up (prices
     * fall); buying pulls it down (prices rise).
     *
     * @param spec the island
     * @param category the trade category
     * @param coinDelta transaction value, positive when the player sold here
     */
    public void adjustStockValue(IslandSpec spec, TradeCategory category, int coinDelta) {
        TWIslandData data = get(spec);
        decay(data);
        data.getStockValue().merge(category.name(), coinDelta, Integer::sum);
        handler.saveObjectAsync(data);
    }

    /**
     * How much more value this port will absorb in a category before its price
     * hits the drift floor - the number a seller actually needs, because it says
     * when to stop selling and sail on.
     *
     * @param spec the island
     * @param category the trade category
     * @return coins of headroom, never negative
     */
    public int absorbableValue(IslandSpec spec, TradeCategory category) {
        var settings = addon.getSettings();
        return absorbable(saturationValue(settings.getDriftMin(), settings.getDriftValueScale()),
                getStockValue(spec, category));
    }

    /**
     * The stock position at which a category's price reaches the drift floor -
     * past this a port pays no less however much more you sell it. Pure and
     * package-visible for tests.
     */
    static int saturationValue(double driftMin, int driftValueScale) {
        return (int) Math.round((1.0 - driftMin) * driftValueScale);
    }

    /**
     * Headroom left before saturation. Pure and package-visible for tests.
     */
    static int absorbable(int saturationValue, int stockValue) {
        return Math.max(0, saturationValue - stockValue);
    }

    /**
     * Lazy decay: every category trends toward equilibrium 0 at the configured
     * coins per hour since the last pass.
     */
    private void decay(TWIslandData data) {
        long now = System.currentTimeMillis();
        long hours = (now - data.getLastDecay()) / 3_600_000L;
        if (hours <= 0) {
            return;
        }
        int perHour = addon.getSettings().getStockDecayValuePerHour();
        data.getStockValue()
                .replaceAll((cat, stock) -> decayed(stock, (int) Math.min(Integer.MAX_VALUE, hours * perHour)));
        data.setLastDecay(now);
    }

    /**
     * Move a stock value toward zero by up to {@code amount}. Pure and
     * package-visible for tests.
     */
    static int decayed(int stock, int amount) {
        if (stock > 0) {
            return Math.max(0, stock - amount);
        }
        return Math.min(0, stock + amount);
    }

    public void saveAll() {
        cache.values().forEach(handler::saveObjectAsync);
    }
}
