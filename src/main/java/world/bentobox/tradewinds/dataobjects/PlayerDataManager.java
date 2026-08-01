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

    /** How far a port's harbour charts reach when scanning for neighbours. */
    private static final int PORT_SCAN_RADIUS = 50_000;

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

    /**
     * The port scan: a sailor standing at a trading island may read its
     * harbour charts, so the nearest islands are added to their own chart
     * free of charge. Dock anywhere and you leave knowing your neighbours.
     *
     * @param player the sailor, expected to be at an island
     * @return the islands newly charted
     */
    public java.util.List<world.bentobox.tradewinds.galaxy.IslandSpec> portScan(org.bukkit.entity.Player player) {
        int count = addon.getSettings().getPortScan();
        if (count <= 0 || addon.getOverWorld() == null || !player.getWorld().equals(addon.getOverWorld())) {
            return java.util.List.of();
        }
        GalaxyEngine engine = addon.getGalaxyEngine(addon.getOverWorld().getSeed());
        int x = player.getLocation().getBlockX();
        int z = player.getLocation().getBlockZ();
        // Must actually be AT a port, not merely in its waters
        int range = addon.getSettings().getIslandProtectionRange();
        java.util.Optional<world.bentobox.tradewinds.galaxy.IslandSpec> port = engine.islandsNear(x, z, range)
                .stream().filter(spec -> spec.distanceSquared(x, z) <= (long) range * range).findFirst();
        if (port.isEmpty()) {
            return java.util.List.of();
        }
        world.bentobox.tradewinds.galaxy.IslandSpec here = port.get();
        TWPlayerData data = get(player.getUniqueId());
        java.util.List<world.bentobox.tradewinds.galaxy.IslandSpec> charted = engine
                .islandsNear(here.centerX(), here.centerZ(), PORT_SCAN_RADIUS).stream()
                .sorted(java.util.Comparator
                        .comparingLong(spec -> spec.distanceSquared(here.centerX(), here.centerZ())))
                .limit(count)
                .filter(data::chart)
                .toList();
        if (!charted.isEmpty()) {
            save(player.getUniqueId());
            charted.forEach(spec -> org.bukkit.Bukkit.getPluginManager()
                    .callEvent(new world.bentobox.tradewinds.api.events.IslandChartedEvent(player.getUniqueId(),
                            spec)));
        }
        return charted;
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
