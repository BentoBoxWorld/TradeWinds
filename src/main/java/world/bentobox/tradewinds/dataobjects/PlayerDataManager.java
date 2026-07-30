package world.bentobox.tradewinds.dataobjects;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

import world.bentobox.bentobox.database.Database;
import world.bentobox.tradewinds.TradeWinds;
import world.bentobox.tradewinds.galaxy.GalaxyEngine;

/**
 * Cache-in-front-of-Database manager for {@link TWPlayerData} (AOneBlock
 * pattern). New players start with the spawn starter cluster pre-charted so
 * the warp dialog is never empty.
 *
 * @author tastybento
 */
public class PlayerDataManager {

    private final TradeWinds addon;
    private final Database<TWPlayerData> handler;
    private final Map<UUID, TWPlayerData> cache = new ConcurrentHashMap<>();

    public PlayerDataManager(TradeWinds addon) {
        this.addon = addon;
        this.handler = new Database<>(addon, TWPlayerData.class);
    }

    /**
     * Get (loading or creating as needed) a player's data.
     */
    public TWPlayerData get(UUID playerId) {
        return cache.computeIfAbsent(playerId, id -> {
            String key = id.toString();
            if (handler.objectExists(key)) {
                TWPlayerData loaded = handler.loadObject(key);
                if (loaded != null) {
                    return loaded;
                }
            }
            TWPlayerData fresh = new TWPlayerData(key);
            chartStarterCluster(fresh);
            handler.saveObjectAsync(fresh);
            return fresh;
        });
    }

    /**
     * Pre-chart the guaranteed starter islands nearest spawn.
     */
    private void chartStarterCluster(TWPlayerData data) {
        if (addon.getOverWorld() == null) {
            return;
        }
        GalaxyEngine engine = addon.getGalaxyEngine(addon.getOverWorld().getSeed());
        // The starter cells are the nearest guaranteed islands; chart everything
        // within the starter cluster radius for good measure
        engine.islandsNear(0, 0, addon.getSettings().getStarterClusterRadius() * 2).forEach(data::chart);
    }

    public void save(UUID playerId) {
        TWPlayerData data = cache.get(playerId);
        if (data != null) {
            handler.saveObjectAsync(data);
        }
    }

    public void saveAll() {
        cache.values().forEach(handler::saveObjectAsync);
    }

    /**
     * Save and drop a player from the cache (call on quit).
     */
    public void unload(UUID playerId) {
        TWPlayerData data = cache.remove(playerId);
        if (data != null) {
            handler.saveObjectAsync(data);
        }
    }
}
