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
    CROPS, FOOD, FISH, WOOD, STONE, ORES, METALS, GEMS, LUXURY, MISC,

    /**
     * Not a recognised trade good anywhere in the ocean - scavenged loot,
     * worn gear, mob drops, odd blocks. No island type produces or demands it,
     * so it gets neutral type affinity, its own stock pool, and a discount
     * (see {@code economy.salvage-discount}).
     * <p>
     * A separate pool is the load-bearing part: without it, dumping a boatload
     * of junk would crater the price of the same category's legitimate cargo
     * for every honest trader at that port - a griefing vector on a small
     * server. {@link #of(Material)} never returns this; salvage is decided by
     * whether the material is on any island's shelves, not by its name.
     */
    SALVAGE;

    /**
     * The locale key for this category's name. The enum name stays the
     * command argument and the logbook key; this is what the player reads.
     *
     * @return locale key
     */
    public String getLocaleKey() {
        return "tradewinds.category." + name().toLowerCase(Locale.ENGLISH);
    }

    /**
     * Raw goods - what low-tech islands live on. High-tech ports pay over the
     * odds for these (they feed the works), low-tech ports sell them cheap.
     */
    public boolean isRaw() {
        return this == ORES || this == CROPS || this == WOOD || this == FISH || this == STONE;
    }

    /**
     * Whether this is the salvage pool rather than a real trade category.
     */
    public boolean isSalvage() {
        return this == SALVAGE;
    }

    /**
     * Finished goods - what high-tech islands produce. High-tech ports sell
     * these cheap, low-tech ports pay dearly for them.
     */
    public boolean isFinished() {
        return this == METALS || this == FOOD;
    }

    /**
     * Classify a material by name heuristics. MISC means "no special island
     * affinity" - still tradeable at base price everywhere.
     */
    public static TradeCategory of(Material material) {
        String name = material.name().toLowerCase(Locale.ENGLISH);
        if (isFish(name)) {
            return FISH;
        }
        if (isCrops(name)) {
            return CROPS;
        }
        if (isFood(name)) {
            return FOOD;
        }
        if (isWood(name)) {
            return WOOD;
        }
        if (isOres(name)) {
            return ORES;
        }
        if (isMetals(name)) {
            return METALS;
        }
        if (isGems(name)) {
            return GEMS;
        }
        if (isLuxury(name)) {
            return LUXURY;
        }
        if (isStone(name)) {
            return STONE;
        }
        return MISC;
    }

    private static boolean isFish(String name) {
        return name.contains("cod") || name.contains("salmon") || name.contains("tropical_fish")
                || name.contains("pufferfish") || name.equals("kelp") || name.contains("nautilus");
    }

    private static boolean isCrops(String name) {
        return name.contains("wheat") || name.contains("carrot") || name.contains("potato")
                || name.contains("beetroot") || name.contains("melon") || name.contains("pumpkin")
                || name.contains("sugar_cane") || name.contains("cocoa") || name.contains("seeds")
                || name.contains("hay_block");
    }

    private static boolean isFood(String name) {
        return name.contains("bread") || name.contains("cooked") || name.contains("beef") || name.contains("porkchop")
                || name.contains("chicken") || name.contains("mutton") || name.contains("rabbit")
                || name.contains("egg") && !name.contains("spawn") || name.contains("milk")
                || name.contains("stew") || name.contains("soup") || name.contains("sugar");
    }

    private static boolean isWood(String name) {
        return name.endsWith("_log") || name.endsWith("_planks") || name.endsWith("_wood") || name.contains("stripped")
                || name.contains("sapling") || name.equals("stick") || name.contains("bamboo")
                || name.equals("charcoal");
    }

    private static boolean isOres(String name) {
        return name.contains("raw_") || name.endsWith("_ore") || name.equals("coal") || name.contains("flint");
    }

    private static boolean isMetals(String name) {
        return name.endsWith("_ingot") || name.endsWith("_block") && (name.contains("iron") || name.contains("copper")
                || name.contains("gold")) || name.contains("nugget") || name.contains("anvil")
                || name.contains("chain");
    }

    private static boolean isGems(String name) {
        return name.contains("diamond") || name.contains("emerald") || name.contains("amethyst")
                || name.contains("quartz") || name.contains("lapis");
    }

    private static boolean isLuxury(String name) {
        return name.contains("cake") || name.contains("golden_apple") || name.contains("firework")
                || name.contains("music_disc") || name.contains("candle") || name.contains("banner_pattern")
                || name.contains("glazed");
    }

    private static boolean isStone(String name) {
        return name.contains("stone") || name.contains("granite") || name.contains("diorite")
                || name.contains("andesite") || name.contains("deepslate") || name.contains("tuff")
                || name.contains("gravel") || name.equals("sand") || name.contains("sandstone");
    }
}
