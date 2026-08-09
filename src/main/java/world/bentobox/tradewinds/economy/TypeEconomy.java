package world.bentobox.tradewinds.economy;

import java.util.List;
import java.util.Map;
import java.util.Set;

import org.bukkit.Material;

import world.bentobox.tradewinds.ocean.IslandType;

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

    /**
     * Per-type outfitter stock beyond the universal bread/fuel: smiths arm
     * you at INDUSTRIAL, farms sell beds, fisheries sell rods.
     */
    private static final Map<IslandType, List<Material>> OUTFITTER_EXTRAS = Map.of(
            IslandType.INDUSTRIAL, List.of(Material.IRON_SWORD, Material.SHIELD, Material.IRON_HELMET,
                    Material.IRON_CHESTPLATE, Material.IRON_LEGGINGS, Material.IRON_BOOTS),
            // The farms are where a sailor outfits a home: a bed to sleep in,
            // white wool and meat off the flock. Wool has to be OUTFITTER
            // stock, not trade cargo - trader-bought cargo may only leave the
            // hold by sale or destruction, so wool in the hold could never
            // become a bed (2026-08-08).
            IslandType.AGRICULTURAL,
            List.of(Material.WHITE_BED, Material.WHITE_WOOL, Material.COOKED_BEEF, Material.COOKED_MUTTON),
            // Rod and compass: the fisheries are the navigator's shop. A
            // compass bought by an island member leaves the counter bound to
            // their island (the ship's compass - see MarketService); for
            // everyone else it points at world spawn, the spawn port. The
            // default spawn island is FISHING, so new sailors find one at
            // their first counter.
            IslandType.FISHING, List.of(Material.FISHING_ROD, Material.COMPASS),
            IslandType.FOREST, List.of(Material.WHITE_BED),
            IslandType.MINING, List.of(Material.IRON_PICKAXE),
            IslandType.LUXURY, List.of(Material.GOLDEN_APPLE),
            IslandType.FROZEN, List.of());

    private TypeEconomy() {
        // Static use only
    }

    /**
     * The outfitter's per-type stock (beyond universal essentials).
     */
    public static List<Material> outfitterExtras(IslandType type) {
        return OUTFITTER_EXTRAS.getOrDefault(type, List.of());
    }

    /** Salt for the flock's colour - one dyed wool per farm port. */
    private static final long SALT_WOOL = 0x5EEDF00DL;

    /** The dyed wools, white excepted: white is stocked at every farm. */
    private static final List<Material> DYED_WOOL = List.of(Material.ORANGE_WOOL, Material.MAGENTA_WOOL,
            Material.LIGHT_BLUE_WOOL, Material.YELLOW_WOOL, Material.LIME_WOOL, Material.PINK_WOOL,
            Material.GRAY_WOOL, Material.LIGHT_GRAY_WOOL, Material.CYAN_WOOL, Material.PURPLE_WOOL,
            Material.BLUE_WOOL, Material.BROWN_WOOL, Material.GREEN_WOOL, Material.RED_WOOL,
            Material.BLACK_WOOL);

    /**
     * The colour this farm port's flock happens to be - seeded, so a given
     * island always sells the same wool and a sailor after a particular colour
     * has somewhere to sail TO. Pure arithmetic; no Bukkit state consulted.
     *
     * @param seed the ocean seed
     * @param cellX island cell x
     * @param cellZ island cell z
     * @return the dyed wool this island stocks
     */
    public static Material localWool(long seed, int cellX, int cellZ) {
        long hash = world.bentobox.tradewinds.ocean.Hashing.cellHash(seed, cellX, cellZ, SALT_WOOL);
        return DYED_WOOL.get((int) Math.floorMod(hash, DYED_WOOL.size()));
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

    /**
     * A stand-in good for a category, for quoting a category-level price. The
     * first entry of its catalog: prices move by category, so any member is
     * representative of the rest.
     *
     * @param category the category
     * @return a sample material, empty for categories with no catalog
     */
    public static java.util.Optional<Material> representative(TradeCategory category) {
        List<Material> goods = CATALOG.getOrDefault(category, List.of());
        return goods.isEmpty() ? java.util.Optional.empty() : java.util.Optional.of(goods.get(0));
    }

    /**
     * Every material that is a recognised trade good <i>somewhere</i> in the
     * ocean: the union of all type catalogs plus every outfitter shelf.
     * Anything else a player turns up - mob drops, worn gear, odd blocks - is
     * salvage, priced at a discount into its own stock pool.
     * <p>
     * Defining salvage by the shelves rather than by category leaves the
     * existing trade economy completely untouched: an iron ingot is cargo
     * everywhere it always was, while a rabbit's foot was never on anyone's
     * manifest.
     *
     * @return the trade-good set, immutable
     */
    public static Set<Material> tradeGoods() {
        return TRADE_GOODS;
    }

    private static final Set<Material> TRADE_GOODS = buildTradeGoods();

    private static Set<Material> buildTradeGoods() {
        Set<Material> goods = java.util.EnumSet.noneOf(Material.class);
        CATALOG.values().forEach(goods::addAll);
        OUTFITTER_EXTRAS.values().forEach(goods::addAll);
        // The universal outfitter essentials, guaranteed at every port
        goods.add(Material.BREAD);
        goods.add(Material.CHARCOAL);
        return java.util.Collections.unmodifiableSet(goods);
    }
}
