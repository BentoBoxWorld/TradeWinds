package world.bentobox.tradewinds.generator;

import java.util.List;

import org.bukkit.block.Biome;
import org.bukkit.generator.BiomeProvider;
import org.bukkit.generator.WorldInfo;

import world.bentobox.tradewinds.TradeWinds;

/**
 * Biomes for the TradeWinds worlds. Stage 0: uniform ocean (sea biome at and
 * below sea level, air biome above) and a single interstice biome. Stage 1 adds
 * per-island whole-island biomes from the seeded galaxy.
 *
 * @author tastybento
 */
public class TradeWindsBiomeProvider extends BiomeProvider {

    private final TradeWinds addon;

    public TradeWindsBiomeProvider(TradeWinds addon) {
        this.addon = addon;
    }

    @Override
    public Biome getBiome(WorldInfo worldInfo, int x, int y, int z) {
        return switch (worldInfo.getEnvironment()) {
        case NETHER -> addon.getSettings().getDefaultNetherBiome();
        default -> y <= addon.getSettings().getSeaHeight()
                ? addon.getSettings().getDefaultBiome()
                : addon.getSettings().getDefaultAirBiome();
        };
    }

    @Override
    public List<Biome> getBiomes(WorldInfo worldInfo) {
        return switch (worldInfo.getEnvironment()) {
        case NETHER -> List.of(addon.getSettings().getDefaultNetherBiome());
        default -> List.of(addon.getSettings().getDefaultBiome(), addon.getSettings().getDefaultAirBiome());
        };
    }
}
