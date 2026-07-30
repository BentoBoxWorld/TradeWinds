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
     * The island's current stock for a category, decayed to now.
     */
    public int getStock(IslandSpec spec, TradeCategory category) {
        TWIslandData data = get(spec);
        decay(data);
        return data.getStock().getOrDefault(category.name(), 0);
    }

    /**
     * Adjust stock after a trade: players selling to the island push stock up
     * (prices fall); buying pulls it down (prices rise).
     */
    public void adjustStock(IslandSpec spec, TradeCategory category, int delta) {
        TWIslandData data = get(spec);
        decay(data);
        data.getStock().merge(category.name(), delta, Integer::sum);
        handler.saveObjectAsync(data);
    }

    /**
     * Lazy decay: every category trends toward equilibrium 0 at the configured
     * units per hour since the last pass.
     */
    private void decay(TWIslandData data) {
        long now = System.currentTimeMillis();
        long hours = (now - data.getLastDecay()) / 3_600_000L;
        if (hours <= 0) {
            return;
        }
        int perHour = addon.getSettings().getStockDecayPerHour();
        data.getStock().replaceAll((cat, stock) -> decayed(stock, (int) Math.min(Integer.MAX_VALUE, hours * perHour)));
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
