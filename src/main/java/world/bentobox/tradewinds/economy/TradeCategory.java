package world.bentobox.tradewinds.economy;

import java.util.Locale;

import org.bukkit.Material;

/**
 * Coarse trade categories. Island types produce some categories (sell cheap)
 * and demand others (buy dear) - the gradient between islands is what makes a
 * route profitable.
 *
 * @author tastybento
 */
public enum TradeCategory {
    CROPS, FOOD, FISH, WOOD, STONE, ORES, METALS, GEMS, LUXURY, MISC;

    /**
     * Classify a material by name heuristics. MISC means "no special island
     * affinity" - still tradeable at base price everywhere.
     */
    public static TradeCategory of(Material material) {
        String name = material.name().toLowerCase(Locale.ENGLISH);
        if (name.contains("cod") || name.contains("salmon") || name.contains("tropical_fish")
                || name.contains("pufferfish") || name.equals("kelp") || name.contains("nautilus")) {
            return FISH;
        }
        if (name.contains("wheat") || name.contains("carrot") || name.contains("potato")
                || name.contains("beetroot") || name.contains("melon") || name.contains("pumpkin")
                || name.contains("sugar_cane") || name.contains("cocoa") || name.contains("seeds")
                || name.contains("hay_block")) {
            return CROPS;
        }
        if (name.contains("bread") || name.contains("cooked") || name.contains("beef") || name.contains("porkchop")
                || name.contains("chicken") || name.contains("mutton") || name.contains("rabbit")
                || name.contains("egg") && !name.contains("spawn") || name.contains("milk")
                || name.contains("stew") || name.contains("soup") || name.contains("sugar")) {
            return FOOD;
        }
        if (name.endsWith("_log") || name.endsWith("_planks") || name.endsWith("_wood") || name.contains("stripped")
                || name.contains("sapling") || name.equals("stick") || name.contains("bamboo")) {
            return WOOD;
        }
        if (name.contains("raw_") || name.endsWith("_ore") || name.equals("coal") || name.equals("charcoal")
                || name.contains("flint")) {
            return ORES;
        }
        if (name.endsWith("_ingot") || name.endsWith("_block") && (name.contains("iron") || name.contains("copper")
                || name.contains("gold")) || name.contains("nugget") || name.contains("anvil")
                || name.contains("chain")) {
            return METALS;
        }
        if (name.contains("diamond") || name.contains("emerald") || name.contains("amethyst")
                || name.contains("quartz") || name.contains("lapis")) {
            return GEMS;
        }
        if (name.contains("cake") || name.contains("golden_apple") || name.contains("firework")
                || name.contains("music_disc") || name.contains("candle") || name.contains("banner_pattern")
                || name.contains("glazed")) {
            return LUXURY;
        }
        if (name.contains("stone") || name.contains("granite") || name.contains("diorite")
                || name.contains("andesite") || name.contains("deepslate") || name.contains("tuff")
                || name.contains("gravel") || name.equals("sand") || name.contains("sandstone")) {
            return STONE;
        }
        return MISC;
    }
}
