package world.bentobox.tradewinds.generator;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

import org.bukkit.NamespacedKey;
import org.bukkit.Registry;
import org.bukkit.block.Biome;
import org.bukkit.generator.BiomeProvider;
import org.bukkit.generator.WorldInfo;

import world.bentobox.tradewinds.TradeWinds;
import world.bentobox.tradewinds.galaxy.IslandType;

/**
 * Biomes for the TradeWinds worlds. Open ocean is the sea biome at and below
 * sea level and the air biome above (Poseidon pattern). Columns inside a
 * trading island's terrain footprint take the island's whole-island biome from
 * the seeded galaxy; FROZEN islands additionally freeze their approach ring
 * into fast ice lanes. The interstice has a single biome.
 *
 * @author tastybento
 */
public class TradeWindsBiomeProvider extends BiomeProvider {

    private final TradeWinds addon;
    // Biome key resolution cache; galaxy keys are a small fixed set
    private final Map<String, Biome> resolved = new ConcurrentHashMap<>();

    public TradeWindsBiomeProvider(TradeWinds addon) {
        this.addon = addon;
    }

    @Override
    public Biome getBiome(WorldInfo worldInfo, int x, int y, int z) {
        if (worldInfo.getEnvironment() == org.bukkit.World.Environment.NETHER) {
            return addon.getSettings().getDefaultNetherBiome();
        }
        Optional<String> key = addon.getGalaxyEngine(worldInfo.getSeed()).biomeKeyAt(x, z);
        if (key.isPresent()) {
            return resolve(key.get());
        }
        return y <= addon.getSettings().getSeaHeight()
                ? addon.getSettings().getDefaultBiome()
                : addon.getSettings().getDefaultAirBiome();
    }

    /**
     * Resolve a namespaced biome key to a Biome, falling back to the sea biome
     * for unknown keys (logged once).
     */
    private Biome resolve(String key) {
        return resolved.computeIfAbsent(key, k -> {
            NamespacedKey nk = NamespacedKey.fromString(k);
            Biome biome = nk == null ? null : Registry.BIOME.get(nk);
            if (biome == null) {
                addon.logError("Unknown biome key in galaxy tables: " + k);
                return addon.getSettings().getDefaultBiome();
            }
            return biome;
        });
    }

    @Override
    public List<Biome> getBiomes(WorldInfo worldInfo) {
        if (worldInfo.getEnvironment() == org.bukkit.World.Environment.NETHER) {
            return List.of(addon.getSettings().getDefaultNetherBiome());
        }
        List<Biome> biomes = new ArrayList<>();
        biomes.add(addon.getSettings().getDefaultBiome());
        if (!biomes.contains(addon.getSettings().getDefaultAirBiome())) {
            biomes.add(addon.getSettings().getDefaultAirBiome());
        }
        for (IslandType type : IslandType.values()) {
            type.getBiomeKeys().forEach(k -> {
                Biome b = resolve(k);
                if (!biomes.contains(b)) {
                    biomes.add(b);
                }
            });
        }
        Biome frozen = resolve("minecraft:frozen_ocean");
        if (!biomes.contains(frozen)) {
            biomes.add(frozen);
        }
        return biomes;
    }
}
