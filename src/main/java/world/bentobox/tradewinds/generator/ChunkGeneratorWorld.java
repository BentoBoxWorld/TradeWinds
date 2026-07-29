package world.bentobox.tradewinds.generator;

import java.util.EnumMap;
import java.util.Map;
import java.util.Random;

import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.World.Environment;
import org.bukkit.generator.BiomeProvider;
import org.bukkit.generator.ChunkGenerator;
import org.bukkit.generator.WorldInfo;
import org.bukkit.util.noise.PerlinOctaveGenerator;
import org.eclipse.jdt.annotation.NonNull;

import world.bentobox.tradewinds.TradeWinds;

/**
 * Generates the TradeWinds ocean: a noised ocean floor under open sea, everywhere.
 * <p>
 * Both the overworld and the interstice (NETHER environment) use this generator;
 * only the sea levels, water block and floor palette differ. Vanilla noise is off
 * so no vanilla continents can appear: from Stage 1 the only land in the world
 * comes from the seeded galaxy's radial island masks via {@link #terrainScale}.
 *
 * @author tastybento
 */
public class ChunkGeneratorWorld extends ChunkGenerator {

    /**
     * Floor palette: base fill under the surface layer, and the two materials the
     * noised floor surface is randomly built from.
     */
    private record FloorMats(Material deepBase, Material base, Material top) {
    }

    /**
     * Per-environment sea shape.
     */
    private record WorldConfig(int seaHeight, int seaFloor, Material waterBlock) {
    }

    /** Maximum height variation of the ocean floor noise, in blocks. */
    private static final int NOISE_MAX = 25;
    private static final double NOISE_SCALE = 1.0 / 30.0;
    private static final int NOISE_OCTAVES = 8;

    private final TradeWinds addon;
    private final Map<Environment, WorldConfig> seaConfig = new EnumMap<>(Environment.class);
    private static final Map<Environment, FloorMats> FLOOR_MATS = Map.of(
            Environment.NORMAL, new FloorMats(Material.STONE, Material.SANDSTONE, Material.SAND),
            Environment.NETHER, new FloorMats(Material.NETHERRACK, Material.BASALT, Material.SOUL_SAND));

    private final Map<Environment, PerlinOctaveGenerator> noiseGens = new EnumMap<>(Environment.class);
    // Deterministic palette randomness; re-seeded per chunk from (world seed, chunk coords)
    private final Random rand = new Random();

    public ChunkGeneratorWorld(TradeWinds addon) {
        super();
        this.addon = addon;
        seaConfig.put(Environment.NORMAL, new WorldConfig(addon.getSettings().getSeaHeight(),
                addon.getSettings().getSeaFloor(), addon.getSettings().getWaterBlock()));
        seaConfig.put(Environment.NETHER, new WorldConfig(addon.getSettings().getIntersticeSeaHeight(),
                addon.getSettings().getIntersticeSeaFloor(), addon.getSettings().getIntersticeWaterBlock()));
    }

    /**
     * Terrain lift in blocks at a world column - the seeded galaxy's radial
     * island mask. This is the only source of land in the world: 0 is plain
     * ocean floor; near an island center the lift raises the floor above sea
     * level. The interstice has no islands.
     *
     * @param worldInfo world being generated
     * @param worldX world x of the column
     * @param worldZ world z of the column
     * @return lift in blocks, >= 0
     */
    protected int terrainLift(WorldInfo worldInfo, int worldX, int worldZ) {
        if (worldInfo.getEnvironment() != Environment.NORMAL) {
            return 0;
        }
        return addon.getGalaxyEngine(worldInfo.getSeed()).landLiftAt(worldX, worldZ);
    }

    @Override
    public void generateNoise(@NonNull WorldInfo worldInfo, @NonNull Random random, int chunkX, int chunkZ,
            @NonNull ChunkData chunkData) {
        WorldConfig wc = seaConfig.get(worldInfo.getEnvironment());
        if (wc == null) {
            return; // Only NORMAL and NETHER are ever created
        }
        FloorMats mats = FLOOR_MATS.get(worldInfo.getEnvironment());
        PerlinOctaveGenerator gen = noiseGens.computeIfAbsent(worldInfo.getEnvironment(), env -> {
            PerlinOctaveGenerator g = new PerlinOctaveGenerator(worldInfo.getSeed(), NOISE_OCTAVES);
            g.setScale(NOISE_SCALE);
            return g;
        });
        // Deterministic per-chunk palette randomness so regeneration is identical
        rand.setSeed(worldInfo.getSeed() ^ (chunkX * 341873128712L + chunkZ * 132897987541L));

        int minHeight = worldInfo.getMinHeight();
        // Bedrock floor
        chunkData.setRegion(0, minHeight, 0, 16, minHeight + 1, 16, Material.BEDROCK);
        // Solid base up to the sea floor
        if (wc.seaFloor() > minHeight + 1) {
            chunkData.setRegion(0, minHeight + 1, 0, 16, wc.seaFloor(), 16, mats.deepBase());
        }
        // Noised floor surface (plus island lift), then water up to sea level
        for (int x = 0; x < 16; x++) {
            for (int z = 0; z < 16; z++) {
                int worldX = (chunkX << 4) + x;
                int worldZ = (chunkZ << 4) + z;
                double noiseVal = gen.noise(worldX, worldZ, 0.5, 0.5, true);
                int lift = terrainLift(worldInfo, worldX, worldZ);
                int floorTop = wc.seaFloor() + (int) (NOISE_MAX + NOISE_MAX * noiseVal) + lift;
                floorTop = Math.min(floorTop, worldInfo.getMaxHeight() - 1);
                boolean land = floorTop > wc.seaHeight() + 1;
                for (int y = wc.seaFloor(); y < floorTop; y++) {
                    chunkData.setBlock(x, y, z, columnMaterial(mats, y, floorTop, land));
                }
                // Water column above the floor
                for (int y = Math.max(floorTop, wc.seaFloor()); y <= wc.seaHeight(); y++) {
                    chunkData.setBlock(x, y, z, wc.waterBlock());
                }
            }
        }
    }

    /**
     * Material for one block of a floor column. Underwater columns are the
     * sea-floor palette; island columns that clear the sea get a soil profile
     * (stone core, dirt subsoil, grass on top) so vanilla decoration can plant
     * on them.
     */
    private Material columnMaterial(FloorMats mats, int y, int floorTop, boolean land) {
        if (!land) {
            return rand.nextBoolean() ? mats.top() : mats.base();
        }
        if (y == floorTop - 1) {
            return Material.GRASS_BLOCK;
        }
        if (y >= floorTop - 4) {
            return Material.DIRT;
        }
        return Material.STONE;
    }

    @Override
    public boolean shouldGenerateNoise() {
        // No vanilla terrain: land only ever comes from the island masks
        return false;
    }

    @Override
    public boolean shouldGenerateSurface() {
        return false;
    }

    @Override
    public boolean shouldGenerateCaves() {
        return addon.getSettings().isMakeCaves();
    }

    @Override
    public boolean shouldGenerateDecorations() {
        return addon.getSettings().isMakeDecorations();
    }

    @Override
    public boolean shouldGenerateMobs() {
        return true;
    }

    @Override
    public boolean shouldGenerateStructures() {
        return addon.getSettings().isMakeStructures();
    }

    @Override
    public BiomeProvider getDefaultBiomeProvider(WorldInfo worldInfo) {
        return addon.getBiomeProvider();
    }

    @Override
    public boolean canSpawn(World world, int x, int z) {
        return true;
    }
}
