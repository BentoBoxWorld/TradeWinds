package world.bentobox.tradewinds.economy;

import java.util.List;
import java.util.Map;
import java.util.Set;

import org.bukkit.Material;

import world.bentobox.tradewinds.galaxy.IslandType;

/**
 * What each island type produces (sells cheap) and demands (buys dear), and
 * the catalog of goods a producing island offers for sale. The gradient is the
 * game: haul wood from FOREST to AGRICULTURAL, ore from MINING to INDUSTRIAL,
 * luxuries to LUXURY resorts in dangerous space.
 *
 * @author tastybento
 */
public final class TypeEconomy {

    private static final Map<IslandType, Set<TradeCategory>> PRODUCES = Map.of(
            IslandType.AGRICULTURAL, Set.of(TradeCategory.CROPS, TradeCategory.FOOD),
            IslandType.FOREST, Set.of(TradeCategory.WOOD),
            IslandType.FISHING, Set.of(TradeCategory.FISH),
            IslandType.MINING, Set.of(TradeCategory.STONE, TradeCategory.ORES),
            IslandType.INDUSTRIAL, Set.of(TradeCategory.METALS),
            IslandType.LUXURY, Set.of(),
            IslandType.FROZEN, Set.of(TradeCategory.FISH));

    private static final Map<IslandType, Set<TradeCategory>> DEMANDS = Map.of(
            IslandType.AGRICULTURAL, Set.of(TradeCategory.METALS, TradeCategory.WOOD),
            IslandType.FOREST, Set.of(TradeCategory.FOOD, TradeCategory.METALS),
            IslandType.FISHING, Set.of(TradeCategory.WOOD, TradeCategory.CROPS),
            IslandType.MINING, Set.of(TradeCategory.FOOD, TradeCategory.WOOD),
            IslandType.INDUSTRIAL, Set.of(TradeCategory.ORES, TradeCategory.STONE),
            IslandType.LUXURY, Set.of(TradeCategory.LUXURY, TradeCategory.GEMS, TradeCategory.FISH),
            IslandType.FROZEN, Set.of(TradeCategory.FOOD, TradeCategory.WOOD));

    /**
     * Sale catalog per category - the goods a producing island offers.
     * Content list, not balance numbers; can move to config when servers want
     * custom goods.
     */
    private static final Map<TradeCategory, List<Material>> CATALOG = Map.of(
            TradeCategory.CROPS, List.of(Material.WHEAT, Material.CARROT, Material.POTATO, Material.SUGAR_CANE,
                    Material.PUMPKIN),
            TradeCategory.FOOD, List.of(Material.BREAD, Material.COOKED_BEEF, Material.COOKED_COD),
            TradeCategory.FISH, List.of(Material.COD, Material.SALMON, Material.TROPICAL_FISH),
            TradeCategory.WOOD, List.of(Material.OAK_LOG, Material.SPRUCE_LOG, Material.DARK_OAK_LOG),
            TradeCategory.STONE, List.of(Material.STONE, Material.GRANITE, Material.DEEPSLATE),
            TradeCategory.ORES, List.of(Material.COAL, Material.RAW_IRON, Material.RAW_COPPER, Material.RAW_GOLD),
            TradeCategory.METALS, List.of(Material.IRON_INGOT, Material.COPPER_INGOT, Material.GOLD_INGOT),
            TradeCategory.GEMS, List.of(Material.DIAMOND, Material.EMERALD, Material.AMETHYST_SHARD),
            TradeCategory.LUXURY, List.of(Material.CAKE, Material.GOLDEN_APPLE),
            TradeCategory.MISC, List.of());

    private TypeEconomy() {
        // Static use only
    }

    public static Set<TradeCategory> produces(IslandType type) {
        return PRODUCES.get(type);
    }

    public static Set<TradeCategory> demands(IslandType type) {
        return DEMANDS.get(type);
    }

    /**
     * The goods an island of this type offers for sale.
     */
    public static List<Material> catalog(IslandType type) {
        return produces(type).stream().flatMap(cat -> CATALOG.get(cat).stream()).toList();
    }
}
