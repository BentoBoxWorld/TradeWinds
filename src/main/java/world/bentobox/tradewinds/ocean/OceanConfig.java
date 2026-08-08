package world.bentobox.tradewinds.ocean;

import java.util.Arrays;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * Immutable configuration for the {@link OceanEngine}. Built from the addon
 * Settings by the caller so the ocean package stays free of Bukkit and
 * BentoBox imports (spec principle 5: everything downstream of the seed is a
 * pure function, unit-testable headlessly).
 *
 * @param seed the ocean seed - the whole world derives from this one number
 * @param minSeparation minimum distance in blocks between island centers
 * @param terrainRadius radius in blocks of an island's terrain (land + shelf) mask
 * @param landLift blocks of terrain lift at an island's center; tapers to 0 at terrainRadius
 * @param density chance (0-1) that a ocean grid cell hosts an island
 * @param starterMinIslands the guaranteed number of islands nearest spawn (density floor)
 * @param bandRadius distance from spawn per security-band step, in blocks
 * @param seaLevel the world's sea surface Y - dock and plaza heights hang off it
 * @param typeWeights relative spawn weight per island type; a zero or missing
 *        total falls back to the built-in defaults
 * @param spawnIslandType economy of the trading island reserved at the origin
 *        (null = seeded roll like any other island)
 * @param wildIsletChance chance (0-1) that a wild-islet grid cell hosts one
 * @param wildIsletRadius mean terrain radius of wild islets (0 = none); each
 *        islet rolls its own size around this, so the sea holds everything from
 *        sandbars to proper little islands
 * @param wildIsletGrid grid size in blocks for wild islets - much finer than the
 *        trading island grid, so the open sea is dotted with land to land on
 * @param mushroomIsletChance chance (0-1) that an islet is a mushroom island -
 *        rare enough to be worth the find
 * @param seabed shape of the ocean floor between the islands
 * @param shape how ragged coastlines and island surfaces are
 * @param spawnIslandBiome biome key for the spawn island, e.g.
 *        "minecraft:cherry_grove" (null or blank = seeded roll from the spawn
 *        type's own biome list, like any other island). The one direct
 *        aesthetic knob in an otherwise fully seeded world: spawn is every
 *        player's first sight of it.
 *
 * @author tastybento
 */
public record OceanConfig(long seed, int minSeparation, int terrainRadius, int landLift, double density,
        int starterMinIslands, int bandRadius, int seaLevel, Map<IslandType, Integer> typeWeights,
        IslandType spawnIslandType, double wildIsletChance, int wildIsletRadius, int wildIsletGrid,
        double mushroomIsletChance, SeabedConfig seabed, ShapeConfig shape, String spawnIslandBiome) {

    /** Default wild islet chance / radius / grid. */
    public static final double DEFAULT_WILD_CHANCE = 0.55;
    public static final int DEFAULT_WILD_RADIUS = 75;
    public static final int DEFAULT_WILD_GRID = 900;
    /** Default chance that an islet is a mushroom island. */
    public static final double DEFAULT_MUSHROOM_CHANCE = 0.06;

    public OceanConfig {
        if (typeWeights == null || typeWeights.values().stream().mapToInt(w -> Math.max(0, w)).sum() <= 0) {
            typeWeights = defaultTypeWeights();
        }
        if (seabed == null) {
            seabed = SeabedConfig.DEFAULT;
        }
        if (shape == null) {
            shape = ShapeConfig.DEFAULT;
        }
        if (spawnIslandBiome != null && spawnIslandBiome.isBlank()) {
            spawnIslandBiome = null;
        }
    }

    /**
     * The pre-override form: every caller predating the spawn-biome knob, and
     * every test that does not care, gets the seeded roll.
     */
    public OceanConfig(long seed, int minSeparation, int terrainRadius, int landLift, double density,
            int starterMinIslands, int bandRadius, int seaLevel, Map<IslandType, Integer> typeWeights,
            IslandType spawnIslandType, double wildIsletChance, int wildIsletRadius, int wildIsletGrid,
            double mushroomIsletChance, SeabedConfig seabed, ShapeConfig shape) {
        this(seed, minSeparation, terrainRadius, landLift, density, starterMinIslands, bandRadius, seaLevel,
                typeWeights, spawnIslandType, wildIsletChance, wildIsletRadius, wildIsletGrid,
                mushroomIsletChance, seabed, shape, null);
    }

    /**
     * Convenience constructor with the default island shape.
     */
    public OceanConfig(long seed, int minSeparation, int terrainRadius, int landLift, double density,
            int starterMinIslands, int bandRadius, int seaLevel, Map<IslandType, Integer> typeWeights,
            IslandType spawnIslandType, double wildIsletChance, int wildIsletRadius, int wildIsletGrid,
            double mushroomIsletChance, SeabedConfig seabed) {
        this(seed, minSeparation, terrainRadius, landLift, density, starterMinIslands, bandRadius, seaLevel,
                typeWeights, spawnIslandType, wildIsletChance, wildIsletRadius, wildIsletGrid, mushroomIsletChance,
                seabed, ShapeConfig.DEFAULT);
    }

    /**
     * Convenience constructor with the default seabed, shape and mushroom chance.
     */
    public OceanConfig(long seed, int minSeparation, int terrainRadius, int landLift, double density,
            int starterMinIslands, int bandRadius, int seaLevel, Map<IslandType, Integer> typeWeights,
            IslandType spawnIslandType, double wildIsletChance, int wildIsletRadius, int wildIsletGrid) {
        this(seed, minSeparation, terrainRadius, landLift, density, starterMinIslands, bandRadius, seaLevel,
                typeWeights, spawnIslandType, wildIsletChance, wildIsletRadius, wildIsletGrid,
                DEFAULT_MUSHROOM_CHANCE, SeabedConfig.DEFAULT, ShapeConfig.DEFAULT);
    }

    /**
     * Convenience constructor with default wild-islet parameters.
     */
    public OceanConfig(long seed, int minSeparation, int terrainRadius, int landLift, double density,
            int starterMinIslands, int bandRadius, int seaLevel, Map<IslandType, Integer> typeWeights,
            IslandType spawnIslandType) {
        this(seed, minSeparation, terrainRadius, landLift, density, starterMinIslands, bandRadius, seaLevel,
                typeWeights, spawnIslandType, DEFAULT_WILD_CHANCE, DEFAULT_WILD_RADIUS, DEFAULT_WILD_GRID);
    }

    /**
     * Convenience constructor with the default wild islet grid.
     */
    public OceanConfig(long seed, int minSeparation, int terrainRadius, int landLift, double density,
            int starterMinIslands, int bandRadius, int seaLevel, Map<IslandType, Integer> typeWeights,
            IslandType spawnIslandType, double wildIsletChance, int wildIsletRadius) {
        this(seed, minSeparation, terrainRadius, landLift, density, starterMinIslands, bandRadius, seaLevel,
                typeWeights, spawnIslandType, wildIsletChance, wildIsletRadius, DEFAULT_WILD_GRID);
    }

    /**
     * Convenience constructor with a seeded spawn island economy.
     */
    public OceanConfig(long seed, int minSeparation, int terrainRadius, int landLift, double density,
            int starterMinIslands, int bandRadius, int seaLevel, Map<IslandType, Integer> typeWeights) {
        this(seed, minSeparation, terrainRadius, landLift, density, starterMinIslands, bandRadius, seaLevel,
                typeWeights, null, DEFAULT_WILD_CHANCE, DEFAULT_WILD_RADIUS);
    }

    /**
     * Convenience constructor using the built-in type weights.
     */
    public OceanConfig(long seed, int minSeparation, int terrainRadius, int landLift, double density,
            int starterMinIslands, int bandRadius, int seaLevel) {
        this(seed, minSeparation, terrainRadius, landLift, density, starterMinIslands, bandRadius, seaLevel,
                defaultTypeWeights(), null, DEFAULT_WILD_CHANCE, DEFAULT_WILD_RADIUS, DEFAULT_WILD_GRID);
    }

    /**
     * @return the built-in default weight for every island type
     */
    public static Map<IslandType, Integer> defaultTypeWeights() {
        return Arrays.stream(IslandType.values())
                .collect(Collectors.toUnmodifiableMap(Function.identity(), IslandType::getWeight));
    }

    /**
     * Grid cell size in blocks. With jitter confined to +/- minSeparation/2 of a
     * cell's nominal center, islands in any two cells are constructively
     * guaranteed to be at least minSeparation apart - no runtime rejection loop
     * needed, and the result is order-independent and deterministic.
     * @return cell size in blocks
     */
    public int cellSize() {
        return minSeparation * 2;
    }

    /**
     * Maximum jitter of an island position from its cell's nominal center.
     * @return jitter radius in blocks
     */
    public int jitter() {
        return minSeparation / 2;
    }
}
