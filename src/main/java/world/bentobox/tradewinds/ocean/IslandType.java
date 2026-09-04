package world.bentobox.tradewinds.ocean;

import java.util.List;

/**
 * Economic character of a trading island. Drives the market modifiers (Stage 4),
 * the blueprint decoration set (Stage 2), and the island's biome. Biomes are
 * plain namespaced keys here - the ocean package has no Bukkit imports; the
 * generator resolves keys to Biome instances at the edge.
 *
 * @author tastybento
 */
public enum IslandType {
    /** Farms: sells food cheap, wants tools and materials. */
    AGRICULTURAL(10, List.of("minecraft:plains", "minecraft:sunflower_plains", "minecraft:meadow")),
    /** Lumber and living wood. */
    FOREST(10, List.of("minecraft:forest", "minecraft:birch_forest", "minecraft:dark_forest")),
    /** Fisheries: sells fish and sea goods. */
    FISHING(10, List.of("minecraft:beach", "minecraft:stony_shore", "minecraft:mangrove_swamp")),
    /** Ore and stone: sells minerals, wants food. */
    MINING(10, List.of("minecraft:windswept_hills", "minecraft:stony_peaks", "minecraft:savanna_plateau")),
    /** Smelters and works: wants raw materials, sells finished goods. */
    INDUSTRIAL(8, List.of("minecraft:desert", "minecraft:badlands")),
    /** Rich resorts: buys luxuries dear. */
    LUXURY(4, List.of("minecraft:cherry_grove", "minecraft:flower_forest")),
    /** Frozen outposts: icy approaches are high-speed boat lanes. */
    FROZEN(8, List.of("minecraft:snowy_plains", "minecraft:snowy_taiga", "minecraft:ice_spikes"));

    private final int weight;
    private final List<String> biomeKeys;

    IslandType(int weight, List<String> biomeKeys) {
        this.weight = weight;
        this.biomeKeys = biomeKeys;
    }

    public int getWeight() {
        return weight;
    }

    public List<String> getBiomeKeys() {
        return biomeKeys;
    }

    /**
     * The locale key for this type's name, for every player-facing line that
     * says what kind of island a port is. The enum name is an identifier
     * (config keys, island metadata), never text a sailor should read.
     *
     * @return locale key
     */
    public String getLocaleKey() {
        return "tradewinds.type." + name().toLowerCase(java.util.Locale.ENGLISH);
    }

    /**
     * @return true if this type's surrounding waters freeze into fast ice lanes
     */
    public boolean isIcyApproach() {
        return this == FROZEN;
    }
}
