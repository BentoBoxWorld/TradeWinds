package world.bentobox.tradewinds.generator;

import java.util.List;
import java.util.Random;

import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.World;
import org.bukkit.entity.IronGolem;
import org.bukkit.entity.Villager;
import org.bukkit.entity.boat.OakBoat;
import org.bukkit.generator.BlockPopulator;
import org.bukkit.generator.LimitedRegion;
import org.bukkit.generator.WorldInfo;
import org.bukkit.persistence.PersistentDataType;
import org.eclipse.jdt.annotation.NonNull;

import world.bentobox.tradewinds.TradeWinds;
import world.bentobox.tradewinds.galaxy.DockPlan;
import world.bentobox.tradewinds.galaxy.GalaxyEngine;
import world.bentobox.tradewinds.galaxy.Hashing;
import world.bentobox.tradewinds.galaxy.IslandSpec;
import world.bentobox.tradewinds.galaxy.IslandType;
import world.bentobox.tradewinds.galaxy.SecurityBand;

/**
 * Decorates each island's market plaza when its chunk generates: a bell,
 * lamp posts, market stalls in the island's palette, resident villagers with
 * professions matching the island's economy, and iron golems scaled by
 * security band. Everything is placed deterministically from the island's
 * seed, inland of the quay (villagers never stand at the waterline).
 * <p>
 * Structures are code-built for now; per-type blueprint sets can replace the
 * stall builder later without touching placement or population.
 *
 * @author tastybento
 */
public class IslandDecorator extends BlockPopulator {

    /** PDC key marking island residents (villagers, golems); value = island name. */
    public static final NamespacedKey RESIDENT_KEY = NamespacedKey.fromString("tradewinds:resident");
    /** PDC key holding a resident's home position ("x,y,z") for the tether. */
    public static final NamespacedKey HOME_KEY = NamespacedKey.fromString("tradewinds:home");

    private static final long SALT_DECOR = 0xDEC0AA7EL;

    private final TradeWinds addon;

    public IslandDecorator(TradeWinds addon) {
        this.addon = addon;
    }

    @Override
    public void populate(@NonNull WorldInfo worldInfo, @NonNull Random random, int chunkX, int chunkZ,
            @NonNull LimitedRegion limitedRegion) {
        if (worldInfo.getEnvironment() != World.Environment.NORMAL) {
            return;
        }
        GalaxyEngine engine = addon.getGalaxyEngine(worldInfo.getSeed());
        // Islands whose plaza center or pier end lands in this chunk
        int minX = chunkX << 4;
        int minZ = chunkZ << 4;
        for (IslandSpec spec : engine.islandsNear(minX + 8, minZ + 8, engine.getConfig().terrainRadius() + 16)) {
            DockPlan plan = engine.dockPlan(spec);
            if (plan.plazaX() >= minX && plan.plazaX() < minX + 16 && plan.plazaZ() >= minZ
                    && plan.plazaZ() < minZ + 16) {
                decoratePlaza(worldInfo, spec, plan, limitedRegion);
            }
            int pierX = spec.centerX() + (int) Math.round(Math.cos(plan.bearing()) * (plan.dockEnd() - 2));
            int pierZ = spec.centerZ() + (int) Math.round(Math.sin(plan.bearing()) * (plan.dockEnd() - 2));
            if (pierX >= minX && pierX < minX + 16 && pierZ >= minZ && pierZ < minZ + 16) {
                decoratePierEnd(worldInfo, spec, pierX, pierZ, limitedRegion);
            }
        }
    }

    /**
     * Pier-end dressing: the island's banner flying at the seaward end of the
     * quay (spot your destination's color from open water), a lantern, and on
     * FISHING islands a moored rowboat.
     */
    private void decoratePierEnd(WorldInfo worldInfo, IslandSpec spec, int pierX, int pierZ, LimitedRegion region) {
        int deckY = addon.getGalaxyEngine(worldInfo.getSeed()).getConfig().seaLevel() + GalaxyEngine.DOCK_RISE;
        setIfPossible(region, pierX, deckY + 1, pierZ, IslandPalette.banner(spec.type()));
        // Lantern on a post one block to the side
        int lx = pierX + (spec.cellX() % 2 == 0 ? 2 : -2);
        setIfPossible(region, lx, deckY + 1, pierZ, IslandPalette.fence(spec.type()));
        setIfPossible(region, lx, deckY + 2, pierZ, Material.LANTERN);
        if (spec.type() == IslandType.FISHING) {
            World world = Bukkit.getWorld(worldInfo.getUID());
            // Moored rowboat on the water beside the deck
            Location loc = new Location(world, pierX + 0.5, deckY - GalaxyEngine.DOCK_RISE + 1.0, pierZ + 4.5);
            if (region.isInRegion(loc)) {
                OakBoat boat = region.createEntity(loc, OakBoat.class);
                boat.setPersistent(true);
                region.addEntity(boat);
            }
        }
    }

    private void decoratePlaza(WorldInfo worldInfo, IslandSpec spec, DockPlan plan, LimitedRegion region) {
        // Island-deterministic randomness: the same island decorates the same way
        Random rand = new Random(Hashing.cellHash(engineSeed(worldInfo), spec.cellX(), spec.cellZ(), SALT_DECOR));
        int surface = addon.getGalaxyEngine(worldInfo.getSeed()).getConfig().seaLevel() + GalaxyEngine.PLAZA_RISE;
        int y = surface + 1; // first air block above the plaza

        // Bell at the plaza center - the market's landmark
        setIfPossible(region, plan.plazaX(), y, plan.plazaZ(), Material.BELL);

        // Lamp posts at the four compass points of the plaza edge
        int lampR = plan.plazaRadius() - 2;
        int[][] lamps = { { lampR, 0 }, { -lampR, 0 }, { 0, lampR }, { 0, -lampR } };
        for (int[] offset : lamps) {
            int lx = plan.plazaX() + offset[0];
            int lz = plan.plazaZ() + offset[1];
            setIfPossible(region, lx, y, lz, IslandPalette.fence(spec.type()));
            setIfPossible(region, lx, y + 1, lz, IslandPalette.fence(spec.type()));
            setIfPossible(region, lx, y + 2, lz, Material.LANTERN);
        }

        // The galley: a public workbench and cooking hearth beside the quay
        // entrance, so any sailor can craft and cook their catch the moment
        // they step ashore. CRAFTING and FURNACE (which governs campfires) are
        // visitor-rank at every port, and BREAK_BLOCKS is not - usable by all,
        // removable by none. The campfire stands on a cobblestone hearth so
        // residents do not path across open flame.
        double galleyAngle = plan.bearing() + 0.7;
        int galleyR = plan.plazaRadius() - 4;
        int gx = plan.plazaX() + (int) Math.round(Math.cos(galleyAngle) * galleyR);
        int gz = plan.plazaZ() + (int) Math.round(Math.sin(galleyAngle) * galleyR);
        setIfPossible(region, gx, y, gz, Material.CRAFTING_TABLE);
        int tangentX = (int) Math.round(-Math.sin(galleyAngle));
        int tangentZ = (int) Math.round(Math.cos(galleyAngle));
        setIfPossible(region, gx + tangentX * 2, y, gz + tangentZ * 2, Material.COBBLESTONE);
        setIfPossible(region, gx + tangentX * 2, y + 1, gz + tangentZ * 2, Material.CAMPFIRE);

        // Higher tech furnishes the plaza further: the amenity row continues
        // the ring on from the galley - smelting and stonework where there is
        // industry, brewing at developed ports, and at the top of the tree an
        // enchanting table with a bookshelf arc behind it
        List<Material> amenities = IslandPalette.amenities(spec.techLevel());
        for (int i = 0; i < amenities.size(); i++) {
            double angle = galleyAngle + 0.25 * (i + 1);
            int ax = plan.plazaX() + (int) Math.round(Math.cos(angle) * galleyR);
            int az = plan.plazaZ() + (int) Math.round(Math.sin(angle) * galleyR);
            Material amenity = amenities.get(i);
            setIfPossible(region, ax, y, az, amenity);
            if (amenity == Material.ENCHANTING_TABLE) {
                for (int k = -2; k <= 2; k++) {
                    int bx = plan.plazaX() + (int) Math.round(Math.cos(angle + k * 0.12) * (galleyR + 2));
                    int bz = plan.plazaZ() + (int) Math.round(Math.sin(angle + k * 0.12) * (galleyR + 2));
                    setIfPossible(region, bx, y, bz, Material.BOOKSHELF);
                    setIfPossible(region, bx, y + 1, bz, Material.BOOKSHELF);
                }
            }
        }

        // Market stalls on a ring: fence corners, wool canopy, a barrel counter,
        // and a profession workstation beside each stall
        int stalls = 2 + rand.nextInt(3);
        double startAngle = rand.nextDouble() * Math.PI * 2;
        int stallR = (int) (plan.plazaRadius() * 0.55);
        List<Material> workstations = IslandPalette.workstations(spec.type());
        for (int i = 0; i < stalls; i++) {
            double angle = startAngle + i * (Math.PI * 2 / stalls);
            int sx = plan.plazaX() + (int) Math.round(Math.cos(angle) * stallR);
            int sz = plan.plazaZ() + (int) Math.round(Math.sin(angle) * stallR);
            buildStall(region, spec, sx, y, sz);
            // Workstation just outside the stall, facing the ring center
            int wx = plan.plazaX() + (int) Math.round(Math.cos(angle) * (stallR + 3));
            int wz = plan.plazaZ() + (int) Math.round(Math.sin(angle) * (stallR + 3));
            setIfPossible(region, wx, y, wz, workstations.get(i % workstations.size()));
        }

        // The island's signature landmark on the inland edge of the plaza,
        // opposite the dock - this is what makes an INDUSTRIAL island read
        // industrial before you ever talk to a villager
        int landmarkDist = plan.plazaRadius() - 5;
        int lx = plan.plazaX() - (int) Math.round(Math.cos(plan.bearing()) * landmarkDist);
        int lz = plan.plazaZ() - (int) Math.round(Math.sin(plan.bearing()) * landmarkDist);
        buildLandmark(region, spec, lx, y, lz);

        World world = Bukkit.getWorld(worldInfo.getUID());
        spawnResidents(spec, plan, region, rand, world, y);
    }

    /**
     * One signature structure per island type. Code-built and deterministic,
     * like the stalls; replaceable by blueprint sets later.
     */
    private void buildLandmark(LimitedRegion region, IslandSpec spec, int x, int y, int z) {
        switch (spec.type()) {
        case INDUSTRIAL -> industrialLandmark(region, x, y, z);
        case MINING -> miningLandmark(region, x, y, z);
        case AGRICULTURAL -> agriculturalLandmark(region, x, y, z);
        case FISHING -> fishingLandmark(region, x, y, z);
        case FOREST -> forestLandmark(region, x, y, z);
        case LUXURY -> luxuryLandmark(region, x, y, z);
        case FROZEN -> frozenLandmark(region, x, y, z);
        }
    }

    private void industrialLandmark(LimitedRegion region, int x, int y, int z) {
        // Brick chimney with a signal fire on top: a smoke column visible
        // from open water. Smelter yard at its foot.
        for (int dy = 0; dy < 2; dy++) {
            for (int dx = -1; dx <= 1; dx++) {
                for (int dz = -1; dz <= 1; dz++) {
                    setIfPossible(region, x + dx, y + dy, z + dz, Material.BRICKS);
                }
            }
        }
        for (int dy = 2; dy < 9; dy++) {
            setIfPossible(region, x, y + dy, z, Material.BRICKS);
        }
        setIfPossible(region, x, y + 9, z, Material.HAY_BLOCK);
        setIfPossible(region, x, y + 10, z, Material.CAMPFIRE);
        setIfPossible(region, x + 2, y, z, Material.BLAST_FURNACE);
        setIfPossible(region, x + 2, y, z + 1, Material.BLAST_FURNACE);
        setIfPossible(region, x + 2, y, z - 1, Material.ANVIL);
        setIfPossible(region, x - 2, y, z, Material.COAL_BLOCK);
        setIfPossible(region, x - 2, y, z + 1, Material.IRON_BLOCK);
    }

    private void miningLandmark(LimitedRegion region, int x, int y, int z) {
        // Timbered shaft head with rails and a spoil heap
        for (int dy = 0; dy < 3; dy++) {
            setIfPossible(region, x - 1, y + dy, z, Material.STRIPPED_SPRUCE_LOG);
            setIfPossible(region, x + 1, y + dy, z, Material.STRIPPED_SPRUCE_LOG);
        }
        setIfPossible(region, x, y + 3, z, Material.SPRUCE_PLANKS);
        setIfPossible(region, x, y, z + 1, Material.RAIL);
        setIfPossible(region, x, y, z + 2, Material.RAIL);
        setIfPossible(region, x + 2, y, z + 1, Material.COBBLESTONE);
        setIfPossible(region, x + 2, y, z + 2, Material.GRAVEL);
        setIfPossible(region, x + 3, y, z + 1, Material.IRON_ORE);
        setIfPossible(region, x - 2, y, z + 1, Material.COAL_ORE);
    }

    private void agriculturalLandmark(LimitedRegion region, int x, int y, int z) {
        // Fenced wheat plot with an irrigation channel and hay stack
        for (int dx = -2; dx <= 2; dx++) {
            for (int dz = -2; dz <= 2; dz++) {
                if (dx == 0) {
                    setIfPossible(region, x + dx, y - 1, z + dz, Material.WATER);
                } else {
                    setIfPossible(region, x + dx, y - 1, z + dz, Material.FARMLAND);
                    setIfPossible(region, x + dx, y, z + dz, Material.WHEAT);
                }
            }
        }
        setIfPossible(region, x + 4, y, z, Material.HAY_BLOCK);
        setIfPossible(region, x + 4, y + 1, z, Material.HAY_BLOCK);
        setIfPossible(region, x + 4, y, z + 1, Material.HAY_BLOCK);
    }

    private void fishingLandmark(LimitedRegion region, int x, int y, int z) {
        // Smokehouse corner: campfire, barrel stack
        setIfPossible(region, x, y, z, Material.CAMPFIRE);
        setIfPossible(region, x + 1, y, z, Material.BARREL);
        setIfPossible(region, x + 1, y + 1, z, Material.BARREL);
        setIfPossible(region, x + 1, y, z + 1, Material.BARREL);
    }

    private void forestLandmark(LimitedRegion region, int x, int y, int z) {
        // Log pile at the sawyer's yard
        Material log = Material.DARK_OAK_LOG;
        for (int dx = 0; dx < 3; dx++) {
            for (int dz = 0; dz < 2; dz++) {
                setIfPossible(region, x + dx, y, z + dz, log);
            }
        }
        setIfPossible(region, x, y + 1, z, Material.STRIPPED_DARK_OAK_LOG);
        setIfPossible(region, x + 1, y + 1, z, Material.STRIPPED_DARK_OAK_LOG);
    }

    private void luxuryLandmark(LimitedRegion region, int x, int y, int z) {
        // Quartz fountain
        for (int dx = -1; dx <= 1; dx++) {
            for (int dz = -1; dz <= 1; dz++) {
                setIfPossible(region, x + dx, y, z + dz,
                        dx == 0 && dz == 0 ? Material.WATER : Material.CHISELED_QUARTZ_BLOCK);
            }
        }
        setIfPossible(region, x + 1, y + 1, z + 1, Material.POTTED_PINK_TULIP);
        setIfPossible(region, x - 1, y + 1, z - 1, Material.POTTED_BLUE_ORCHID);
    }

    private void frozenLandmark(LimitedRegion region, int x, int y, int z) {
        // Ice beacon: packed-ice cairn with a lantern
        for (int dy = 0; dy < 4; dy++) {
            setIfPossible(region, x, y + dy, z, Material.PACKED_ICE);
        }
        setIfPossible(region, x, y + 4, z, Material.LANTERN);
        setIfPossible(region, x + 1, y, z, Material.SNOW_BLOCK);
        setIfPossible(region, x - 1, y, z + 1, Material.SNOW_BLOCK);
    }

    private long engineSeed(WorldInfo worldInfo) {
        return addon.getGalaxyEngine(worldInfo.getSeed()).getConfig().seed();
    }

    private void buildStall(LimitedRegion region, IslandSpec spec, int sx, int y, int sz) {
        Material fence = IslandPalette.fence(spec.type());
        Material canopy = IslandPalette.canopy(spec.type());
        // Corner posts
        for (int dx = -1; dx <= 1; dx += 2) {
            for (int dz = -1; dz <= 1; dz += 2) {
                setIfPossible(region, sx + dx, y, sz + dz, fence);
                setIfPossible(region, sx + dx, y + 1, sz + dz, fence);
            }
        }
        // Canopy
        for (int dx = -1; dx <= 1; dx++) {
            for (int dz = -1; dz <= 1; dz++) {
                setIfPossible(region, sx + dx, y + 2, sz + dz, canopy);
            }
        }
        // Counter
        setIfPossible(region, sx, y, sz, Material.BARREL);
    }

    private void spawnResidents(IslandSpec spec, DockPlan plan, LimitedRegion region, Random rand, World world,
            int y) {
        // Villagers: professions match the island's economy. Count is engine-
        // deterministic so the respawn audit knows what fully-staffed means.
        int villagers = addon.getGalaxyEngine(addon.getOverWorld() == null ? 0 : addon.getOverWorld().getSeed())
                .villagerCount(spec);
        for (int i = 0; i < villagers; i++) {
            int vx = plan.plazaX() + rand.nextInt(9) - 4;
            int vz = plan.plazaZ() + rand.nextInt(9) - 4;
            if (!region.isInRegion(vx, y, vz)) {
                continue;
            }
            Location loc = new Location(world, vx + 0.5, y, vz + 0.5);
            Villager villager = region.createEntity(loc, Villager.class);
            configureVillager(villager, spec, i, plan.plazaX(), y, plan.plazaZ());
            region.addEntity(villager);
        }
        // Resident golems: civilization shows - none in anarchic space
        for (int i = 0; i < golemCount(spec.band()); i++) {
            int gx = plan.plazaX() + rand.nextInt(13) - 6;
            int gz = plan.plazaZ() + rand.nextInt(13) - 6;
            if (!region.isInRegion(gx, y, gz)) {
                continue;
            }
            Location loc = new Location(world, gx + 0.5, y, gz + 0.5);
            IronGolem golem = region.createEntity(loc, IronGolem.class);
            configureResident(golem, spec, plan.plazaX(), y, plan.plazaZ());
            region.addEntity(golem);
        }
    }

    /**
     * Shared resident setup for spawn and respawn: persistence, tags, home.
     */
    public static void configureResident(org.bukkit.entity.LivingEntity entity, IslandSpec spec, int homeX, int homeY,
            int homeZ) {
        entity.setPersistent(true);
        entity.setRemoveWhenFarAway(false);
        entity.getPersistentDataContainer().set(RESIDENT_KEY, PersistentDataType.STRING, spec.name());
        entity.getPersistentDataContainer().set(HOME_KEY, PersistentDataType.STRING, homeX + "," + homeY + "," + homeZ);
    }

    /**
     * Shared villager setup for spawn and respawn.
     */
    public static void configureVillager(Villager villager, IslandSpec spec, int index, int homeX, int homeY,
            int homeZ) {
        List<Villager.Profession> professions = IslandPalette.professions(spec.type());
        villager.setProfession(professions.get(index % professions.size()));
        // A villager with zero trade XP and no claimed job site is reset to
        // unemployed on first tick (and the stall barrels are fisherman job
        // sites, so the survivors all turned fisherman). One XP point locks
        // the assigned profession for good.
        villager.setVillagerExperience(1);
        villager.setVillagerType(villagerType(spec.biomeKey()));
        configureResident(villager, spec, homeX, homeY, homeZ);
    }

    /**
     * Resident golems by band - the visible face of island security.
     */
    public static int golemCount(SecurityBand band) {
        return switch (band) {
        case SAFE -> 3;
        case POLICED -> 2;
        case FRONTIER, LAWLESS -> 1;
        case ANARCHIC -> 0;
        };
    }

    /**
     * Villager skin variant matching the island biome.
     */
    static Villager.Type villagerType(String biomeKey) {
        if (biomeKey.contains("snow") || biomeKey.contains("ice") || biomeKey.contains("frozen")) {
            return Villager.Type.SNOW;
        }
        if (biomeKey.contains("desert") || biomeKey.contains("badlands")) {
            return Villager.Type.DESERT;
        }
        if (biomeKey.contains("savanna")) {
            return Villager.Type.SAVANNA;
        }
        if (biomeKey.contains("mangrove") || biomeKey.contains("swamp")) {
            return Villager.Type.SWAMP;
        }
        if (biomeKey.contains("windswept") || biomeKey.contains("stony")) {
            return Villager.Type.TAIGA;
        }
        return Villager.Type.PLAINS;
    }

    private void setIfPossible(LimitedRegion region, int x, int y, int z, Material material) {
        if (region.isInRegion(x, y, z)) {
            region.setType(x, y, z, material);
        }
    }
}
