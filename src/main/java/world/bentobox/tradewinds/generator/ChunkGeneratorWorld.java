package world.bentobox.tradewinds.generator;

import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.Random;

import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.World.Environment;
import org.bukkit.generator.BiomeProvider;
import org.bukkit.generator.BlockPopulator;
import org.bukkit.generator.ChunkGenerator;
import org.bukkit.generator.WorldInfo;
import org.eclipse.jdt.annotation.NonNull;

import world.bentobox.tradewinds.TradeWinds;
import world.bentobox.tradewinds.galaxy.ColumnPlan;
import world.bentobox.tradewinds.galaxy.GalaxyEngine;
import world.bentobox.tradewinds.galaxy.Seabed;
import world.bentobox.tradewinds.galaxy.SeabedConfig;
import world.bentobox.tradewinds.galaxy.SurfaceKind;

/**
 * Generates the TradeWinds ocean: a sea floor of shelves, basins, rifts and
 * seamounts under open water, everywhere.
 * <p>
 * Vanilla noise is off so no vanilla continents can appear - the only land in
 * the world comes from the seeded galaxy's radial island masks. Everything
 * else vanilla offers is left switched on: its carvers cut the caves under the
 * sea floor, and its structure placement supplies the shipwrecks, ocean ruins,
 * monuments and trial chambers, guided entirely by the biomes the galaxy hands
 * out. That is the hybrid: we own the shape of the floor, vanilla furnishes it.
 * <p>
 * Both the overworld and the interstice (NETHER environment) use this
 * generator; the interstice gets its own drab, shallow sea floor and none of
 * the galaxy.
 *
 * @author tastybento
 */
public class ChunkGeneratorWorld extends ChunkGenerator {

    /**
     * Per-environment sea shape.
     */
    private record WorldConfig(int seaHeight, int seaFloor, Material waterBlock) {
    }

    /**
     * The interstice's sea floor: shallower than the ocean's but restless -
     * pale banks near the surface, basins falling away dark, rifts, blunt
     * seamounts looming under the hull. It was flat-by-design back when the
     * interstice was a dead end nobody explored; once it grew wart, wrecks
     * and blazes worth visiting, "the floor does seem too flat" (playtest
     * 2026-08-07) - a destination deserves scenery.
     */
    private static final SeabedConfig INTERSTICE_SEABED = new SeabedConfig(6, 34, 10, 10, 16, 0.82, 12, 4);
    /** Salt so the interstice floor does not mirror the overworld's. */
    private static final long INTERSTICE_SALT = 0x1E7E2571CEL;

    /** Depth below sea level at which sediment gives way to bare rock. */
    private static final int ROCK_DEPTH = 34;
    /** Thickness of the sediment layer over the rock, in blocks. */
    private static final int SEDIMENT_THICKNESS = 4;
    /** How far the varied rock reaches below the floor before it is all stone. */
    private static final int ROCK_BAND = 8;
    /**
     * How thick a crust the sea floor keeps over vanilla's carvers. Thick enough
     * that a cave roof is not one block of rock holding back the ocean.
     */
    private static final int CRUST_THICKNESS = 5;
    /** Soil depth under a land surface before it turns to stone. */
    private static final int SOIL_THICKNESS = 4;
    /** Y below which the bulk rock is deepslate, as in vanilla. */
    private static final int DEEPSLATE_TOP = 0;
    /** Layers of rock under the interstice's bedrock lid. */
    private static final int ROOF_THICKNESS = 4;

    private final TradeWinds addon;
    private final Map<Environment, WorldConfig> seaConfig = new EnumMap<>(Environment.class);
    private final Map<Environment, Seabed> seabeds = new EnumMap<>(Environment.class);
    private IslandDecorator decorator;

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

    /**
     * The sea floor field for an environment. The overworld shares the galaxy's
     * so terrain and biomes agree on where the deep water is; the interstice
     * gets its own.
     */
    private Seabed seabed(WorldInfo worldInfo) {
        return seabeds.computeIfAbsent(worldInfo.getEnvironment(), env -> {
            if (env == Environment.NORMAL) {
                return addon.getGalaxyEngine(worldInfo.getSeed()).getSeabed();
            }
            return new Seabed(worldInfo.getSeed() ^ INTERSTICE_SALT,
                    seaConfig.get(Environment.NETHER).seaHeight(),
                    addon.getSettings().isVarySeabed() ? INTERSTICE_SEABED
                            : SeabedConfig.flat(INTERSTICE_SEABED.shelfDepth()));
        });
    }

    @Override
    public void generateNoise(@NonNull WorldInfo worldInfo, @NonNull Random random, int chunkX, int chunkZ,
            @NonNull ChunkData chunkData) {
        WorldConfig wc = seaConfig.get(worldInfo.getEnvironment());
        if (wc == null) {
            return; // Only NORMAL and NETHER are ever created
        }
        boolean overworld = worldInfo.getEnvironment() == Environment.NORMAL;
        Seabed floor = seabed(worldInfo);
        GalaxyEngine engine = overworld ? addon.getGalaxyEngine(worldInfo.getSeed()) : null;

        int minHeight = worldInfo.getMinHeight();
        // Bedrock floor
        chunkData.setRegion(0, minHeight, 0, 16, minHeight + 1, 16, Material.BEDROCK);

        // Sound the whole chunk first, so the solid rock underneath everything
        // can go in as one region fill rather than block by block - with rifts
        // reaching this far down, that is the difference between 256 columns of
        // a few blocks each and 256 columns of sixty
        int[] floorTops = new int[256];
        int lowest = Integer.MAX_VALUE;
        for (int x = 0; x < 16; x++) {
            for (int z = 0; z < 16; z++) {
                int top = floorTopAt(worldInfo, wc, floor, engine, (chunkX << 4) + x, (chunkZ << 4) + z);
                floorTops[(x << 4) | z] = top;
                lowest = Math.min(lowest, top);
            }
        }
        // Stop the bulk fill short of the shallowest column so every column
        // still lays its own surface layers - a flat chunk (a market plaza, or
        // dead level sea floor) would otherwise come out as bare base rock
        int baseTop = Math.max(minHeight + 1, lowest - ROCK_BAND);
        if (overworld) {
            int slateTop = Math.clamp(DEEPSLATE_TOP, minHeight + 1, baseTop);
            chunkData.setRegion(0, minHeight + 1, 0, 16, slateTop, 16, Material.DEEPSLATE);
            chunkData.setRegion(0, slateTop, 0, 16, baseTop, 16, Material.STONE);
        } else {
            chunkData.setRegion(0, minHeight + 1, 0, 16, baseTop, 16, Material.NETHERRACK);
        }

        for (int x = 0; x < 16; x++) {
            for (int z = 0; z < 16; z++) {
                generateColumn(chunkData, worldInfo, wc, floor, engine, x, z, (chunkX << 4) + x, (chunkZ << 4) + z,
                        baseTop, floorTops[(x << 4) | z]);
            }
        }
        if (!overworld) {
            roofOver(chunkData, worldInfo, wc);
        }
    }

    /**
     * Lid the interstice.
     * <p>
     * An open sky over a black sea reads as an empty void - somewhere the game
     * forgot to finish - rather than somewhere you are trapped. A ceiling gives
     * it a shape, and stops anything leaving upward.
     */
    private void roofOver(ChunkData chunkData, WorldInfo worldInfo, WorldConfig wc) {
        int height = addon.getSettings().getIntersticeCeilingHeight();
        if (height <= 0) {
            return;
        }
        int roof = Math.min(worldInfo.getMaxHeight() - 1, wc.seaHeight() + height);
        int underside = Math.max(wc.seaHeight() + 1, roof - ROOF_THICKNESS);
        chunkData.setRegion(0, underside, 0, 16, roof, 16, Material.NETHERRACK);
        chunkData.setRegion(0, roof, 0, 16, roof + 1, 16, Material.BEDROCK);
    }

    /**
     * Seal the sea floor after vanilla's carvers have run.
     * <p>
     * Carvers cut caves and ravines with no idea there is an ocean overhead, so
     * left alone they open dry craters straight through the floor - generated
     * chunks get no block updates, so nothing ever flows in to fill them. This
     * is the Poseidon fix, narrowed: rather than filling every carved space
     * under the sea, only the crust is put back. Anything deeper than
     * {@link #CRUST_THICKNESS} below the floor stays hollow, so there are still
     * caves down there to find - they just have a sea floor over them.
     */
    @Override
    public void generateCaves(@NonNull WorldInfo worldInfo, @NonNull Random random, int chunkX, int chunkZ,
            @NonNull ChunkData chunkData) {
        WorldConfig wc = seaConfig.get(worldInfo.getEnvironment());
        if (wc == null) {
            return;
        }
        Seabed floor = seabed(worldInfo);
        GalaxyEngine engine = worldInfo.getEnvironment() == Environment.NORMAL
                ? addon.getGalaxyEngine(worldInfo.getSeed())
                : null;
        int lowestY = worldInfo.getMinHeight() + 1;
        for (int x = 0; x < 16; x++) {
            for (int z = 0; z < 16; z++) {
                int worldX = (chunkX << 4) + x;
                int worldZ = (chunkZ << 4) + z;
                int floorTop = floorTopAt(worldInfo, wc, floor, engine, worldX, worldZ);
                int from = Math.max(lowestY, floorTop - CRUST_THICKNESS);
                if (from > wc.seaHeight()) {
                    continue; // Land: a cave mouth in an island's flank is fine
                }
                double sediment = floor.sedimentAt(worldX, worldZ);
                int depth = wc.seaHeight() - floorTop;
                for (int y = from; y <= wc.seaHeight(); y++) {
                    if (chunkData.getType(x, y, z) != Material.AIR) {
                        continue;
                    }
                    // Above the floor is sea; below it is the crust it carved through
                    chunkData.setBlock(x, y, z, y >= floorTop ? wc.waterBlock()
                            : crustMaterial(worldInfo, y, floorTop, depth, sediment));
                }
            }
        }
    }

    /**
     * What to put back into a hole carved in the sea floor: the same sediment
     * and rock the floor is made of, so the repair is invisible.
     */
    private Material crustMaterial(WorldInfo worldInfo, int y, int floorTop, int depth, double sediment) {
        return columnMaterial(worldInfo, y, floorTop, false, depth, sediment, null, SurfaceKind.GRASS);
    }

    /**
     * The Y of the topmost floor block in a column: the sea floor, plus the
     * galaxy's island lift, overridden by any dock or plaza terraforming.
     */
    private int floorTopAt(WorldInfo worldInfo, WorldConfig wc, Seabed floor, GalaxyEngine engine, int worldX,
            int worldZ) {
        double shelfBlend = engine == null ? 0 : engine.shelfBlendAt(worldX, worldZ);
        int floorTop = floor.heightAt(worldX, worldZ, shelfBlend) + terrainLift(worldInfo, worldX, worldZ);
        ColumnPlan plan = engine == null ? null : engine.columnPlanAt(worldX, worldZ).orElse(null);
        if (plan != null) {
            int wanted = plan.surfaceY() + 1;
            floorTop = plan.blend() >= 1.0 ? wanted : (int) Math.round(floorTop + (wanted - floorTop) * plan.blend());
        }
        if (worldInfo.getEnvironment() == Environment.NETHER) {
            // Wart shoals: the interstice's only land, a low soul-sand dome
            // the natural floor rises to meet (interstice plan, source 1)
            var map = addon.getIntersticeMap(worldInfo.getSeed());
            var wanted = map.shoalSurfaceAt(worldX, worldZ, wc.seaHeight());
            if (wanted.isPresent()) {
                floorTop = Math.max(floorTop, wanted.getAsInt());
            }
            // Wreck reefs: the seabed rises under every wreck so the hull
            // perches half out of the water instead of vanishing into the
            // deep (interstice plan, source 7)
            var reef = map.wreckSurfaceAt(worldX, worldZ, wc.seaHeight());
            if (reef.isPresent()) {
                floorTop = Math.max(floorTop, reef.getAsInt());
            }
        }
        return Math.clamp(floorTop, worldInfo.getMinHeight() + 2, worldInfo.getMaxHeight() - 1);
    }

    /**
     * Lay down one column: floor material up to its top, then water to the sea
     * surface.
     */
    private void generateColumn(ChunkData chunkData, WorldInfo worldInfo, WorldConfig wc, Seabed floor,
            GalaxyEngine engine, int x, int z, int worldX, int worldZ, int baseTop, int floorTop) {
        ColumnPlan plan = engine == null ? null : engine.columnPlanAt(worldX, worldZ).orElse(null);
        boolean land = floorTop > wc.seaHeight() + 1;
        int depth = wc.seaHeight() - floorTop;
        double sediment = floor.sedimentAt(worldX, worldZ);
        SurfaceKind surface = land && engine != null ? engine.surfaceKindAt(worldX, worldZ) : SurfaceKind.GRASS;

        for (int y = baseTop; y < floorTop; y++) {
            chunkData.setBlock(x, y, z,
                    columnMaterial(worldInfo, y, floorTop, land, depth, sediment, plan, surface));
        }
        for (int y = Math.max(floorTop, baseTop); y <= wc.seaHeight(); y++) {
            chunkData.setBlock(x, y, z, wc.waterBlock());
        }
    }

    /**
     * Material for one block of a floor column.
     * <p>
     * Underwater, the floor reads its own depth: sunlit banks are sand, the
     * middle depths are gravel and clay in broad patches, and the deep basins
     * and rift floors are bare rock. Island columns that clear the sea get a
     * soil profile so vanilla decoration can plant on them - grass normally,
     * sand along an islet's shoreline, mycelium on a mushroom island. Dock
     * columns are a stone-brick quay with a plank deck; fully flattened plaza
     * columns get a path surface.
     */
    private Material columnMaterial(WorldInfo worldInfo, int y, int floorTop, boolean land, int depth,
            double sediment, ColumnPlan plan, SurfaceKind surface) {
        if (plan != null && plan.feature() == ColumnPlan.Feature.DOCK) {
            return y == floorTop - 1 ? IslandPalette.planks(plan.island().type()) : Material.STONE_BRICKS;
        }
        if (plan != null && plan.feature() == ColumnPlan.Feature.PLAZA && plan.blend() >= 1.0 && y == floorTop - 1) {
            return IslandPalette.plazaSurface(plan.island().type());
        }
        if (worldInfo.getEnvironment() != Environment.NORMAL) {
            return intersticeMaterial(y, floorTop, land, sediment);
        }
        if (land) {
            if (y == floorTop - 1) {
                return IslandPalette.surface(surface);
            }
            if (y >= floorTop - SOIL_THICKNESS) {
                return IslandPalette.subsoil(surface);
            }
            return baseRock(y);
        }
        // Sediment lies in a thin layer over rock; the deep basins and rift
        // floors are scoured down to the rock itself
        int fromTop = floorTop - 1 - y;
        if (fromTop < SEDIMENT_THICKNESS && depth < ROCK_DEPTH) {
            return sedimentMaterial(sediment, depth);
        }
        return fromTop < ROCK_BAND ? deepRock(sediment, depth, y) : baseRock(y);
    }

    /**
     * The bulk rock at a height: deepslate below Y 0, stone above, as in
     * vanilla - the ore veins that decoration adds take their deepslate
     * variants from the block they replace.
     */
    private static Material baseRock(int y) {
        return y < DEEPSLATE_TOP ? Material.DEEPSLATE : Material.STONE;
    }

    /**
     * Sediment for the shallow and middle depths: banks of sand giving way to
     * gravel and the odd clay pan, in patches rather than block-by-block noise.
     */
    private static Material sedimentMaterial(double sediment, int depth) {
        if (depth < 20) {
            return sediment < 0.62 ? Material.SAND : Material.GRAVEL;
        }
        if (sediment < 0.34) {
            return Material.SAND;
        }
        if (sediment < 0.78) {
            return Material.GRAVEL;
        }
        return Material.CLAY;
    }

    /**
     * The rock under the sediment, and the floor of the deep basins and rifts.
     */
    private static Material deepRock(double sediment, int depth, int y) {
        if (depth >= ROCK_DEPTH && sediment > 0.80) {
            return Material.TUFF;
        }
        if (depth >= ROCK_DEPTH && sediment < 0.22) {
            return Material.GRAVEL;
        }
        return baseRock(y);
    }

    /**
     * The interstice's floor: basalt and soul sand, nothing worth mining -
     * except where a wart shoal breaks the surface, whose land is soul sand
     * all the way through its crown (wart plants only on soul sand).
     */
    private static Material intersticeMaterial(int y, int floorTop, boolean land, double sediment) {
        if (land && y >= floorTop - SOIL_THICKNESS) {
            return Material.SOUL_SAND;
        }
        if (y >= floorTop - SEDIMENT_THICKNESS) {
            return sediment < 0.5 ? Material.SOUL_SAND : Material.BASALT;
        }
        return Material.NETHERRACK;
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

    /**
     * Vanilla structures everywhere except on the trading islands themselves -
     * a monument or a village dropped through a market plaza would wreck the
     * one part of the world that is hand-built.
     */
    @Override
    public boolean shouldGenerateStructures(@NonNull WorldInfo worldInfo, @NonNull Random random, int chunkX,
            int chunkZ) {
        if (!addon.getSettings().isMakeStructures() || worldInfo.getEnvironment() != Environment.NORMAL) {
            return false;
        }
        if (!addon.getSettings().isKeepStructuresOffIslands()) {
            return true;
        }
        int centerX = (chunkX << 4) + 8;
        int centerZ = (chunkZ << 4) + 8;
        // A chunk's worth of margin so a structure anchored just outside cannot
        // reach in
        return addon.getGalaxyEngine(worldInfo.getSeed())
                .islandsNear(centerX, centerZ, addon.getSettings().getIslandTerrainRadius() + 16).isEmpty();
    }

    @Override
    public List<BlockPopulator> getDefaultPopulators(World world) {
        if (world.getEnvironment() != Environment.NORMAL) {
            // The interstice gets braziers - the only light in the place
            return List.of(new IntersticeDecorator(addon));
        }
        if (decorator == null) {
            decorator = new IslandDecorator(addon);
        }
        return List.of(decorator, new IsletDecorator(addon));
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
