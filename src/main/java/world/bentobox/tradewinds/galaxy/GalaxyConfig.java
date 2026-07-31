package world.bentobox.tradewinds.galaxy;

import java.util.Arrays;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * Immutable configuration for the {@link GalaxyEngine}. Built from the addon
 * Settings by the caller so the galaxy package stays free of Bukkit and
 * BentoBox imports (spec principle 5: everything downstream of the seed is a
 * pure function, unit-testable headlessly).
 *
 * @param seed the galaxy seed - the whole world derives from this one number
 * @param minSeparation minimum distance in blocks between island centers
 * @param terrainRadius radius in blocks of an island's terrain (land + shelf) mask
 * @param landLift blocks of terrain lift at an island's center; tapers to 0 at terrainRadius
 * @param density chance (0-1) that a galaxy grid cell hosts an island
 * @param starterMinIslands the guaranteed number of islands nearest spawn (density floor)
 * @param bandRadius distance from spawn per security-band step, in blocks
 * @param seaLevel the world's sea surface Y - dock and plaza heights hang off it
 * @param typeWeights relative spawn weight per island type; a zero or missing
 *        total falls back to the built-in defaults
 * @param spawnIsletRadius radius of the safe spawn islet at the origin (0 = none)
 *
 * @author tastybento
 */
public record GalaxyConfig(long seed, int minSeparation, int terrainRadius, int landLift, double density,
        int starterMinIslands, int bandRadius, int seaLevel, Map<IslandType, Integer> typeWeights,
        int spawnIsletRadius) {

    /** Default spawn islet radius in blocks. */
    public static final int DEFAULT_SPAWN_ISLET_RADIUS = 48;

    public GalaxyConfig {
        if (typeWeights == null || typeWeights.values().stream().mapToInt(w -> Math.max(0, w)).sum() <= 0) {
            typeWeights = defaultTypeWeights();
        }
    }

    /**
     * Convenience constructor using the default spawn islet radius.
     */
    public GalaxyConfig(long seed, int minSeparation, int terrainRadius, int landLift, double density,
            int starterMinIslands, int bandRadius, int seaLevel, Map<IslandType, Integer> typeWeights) {
        this(seed, minSeparation, terrainRadius, landLift, density, starterMinIslands, bandRadius, seaLevel,
                typeWeights, DEFAULT_SPAWN_ISLET_RADIUS);
    }

    /**
     * Convenience constructor using the built-in type weights.
     */
    public GalaxyConfig(long seed, int minSeparation, int terrainRadius, int landLift, double density,
            int starterMinIslands, int bandRadius, int seaLevel) {
        this(seed, minSeparation, terrainRadius, landLift, density, starterMinIslands, bandRadius, seaLevel,
                defaultTypeWeights(), DEFAULT_SPAWN_ISLET_RADIUS);
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
