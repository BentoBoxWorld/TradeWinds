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

    /** PDC key marking island residents (villagers, golems). */
    public static final NamespacedKey RESIDENT_KEY = NamespacedKey.fromString("tradewinds:resident");

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
        // Islands whose plaza center lands in this chunk
        int minX = chunkX << 4;
        int minZ = chunkZ << 4;
        for (IslandSpec spec : engine.islandsNear(minX + 8, minZ + 8, engine.getConfig().terrainRadius() + 16)) {
            DockPlan plan = engine.dockPlan(spec);
            if (plan.plazaX() >= minX && plan.plazaX() < minX + 16 && plan.plazaZ() >= minZ
                    && plan.plazaZ() < minZ + 16) {
                decoratePlaza(worldInfo, spec, plan, limitedRegion);
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

        // Market stalls on a ring: fence corners, wool canopy, a barrel counter
        int stalls = 2 + rand.nextInt(3);
        double startAngle = rand.nextDouble() * Math.PI * 2;
        int stallR = (int) (plan.plazaRadius() * 0.55);
        for (int i = 0; i < stalls; i++) {
            double angle = startAngle + i * (Math.PI * 2 / stalls);
            int sx = plan.plazaX() + (int) Math.round(Math.cos(angle) * stallR);
            int sz = plan.plazaZ() + (int) Math.round(Math.sin(angle) * stallR);
            buildStall(region, spec, sx, y, sz);
        }

        World world = Bukkit.getWorld(worldInfo.getUID());
        spawnResidents(spec, plan, region, rand, world, y);
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
        // Villagers: professions match the island's economy
        List<Villager.Profession> professions = IslandPalette.professions(spec.type());
        int villagers = 3 + rand.nextInt(3);
        for (int i = 0; i < villagers; i++) {
            int vx = plan.plazaX() + rand.nextInt(9) - 4;
            int vz = plan.plazaZ() + rand.nextInt(9) - 4;
            if (!region.isInRegion(vx, y, vz)) {
                continue;
            }
            Location loc = new Location(world, vx + 0.5, y, vz + 0.5);
            Villager villager = region.createEntity(loc, Villager.class);
            villager.setProfession(professions.get(i % professions.size()));
            // A villager with zero trade XP and no claimed job site is reset to
            // unemployed on first tick (and the stall barrels are fisherman job
            // sites, so the survivors all turned fisherman). One XP point locks
            // the assigned profession for good.
            villager.setVillagerExperience(1);
            villager.setVillagerType(villagerType(spec.biomeKey()));
            villager.setPersistent(true);
            villager.setRemoveWhenFarAway(false);
            villager.getPersistentDataContainer().set(RESIDENT_KEY, PersistentDataType.STRING, spec.name());
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
            golem.setPersistent(true);
            golem.setRemoveWhenFarAway(false);
            golem.getPersistentDataContainer().set(RESIDENT_KEY, PersistentDataType.STRING, spec.name());
            region.addEntity(golem);
        }
    }

    /**
     * Resident golems by band - the visible face of island security.
     */
    static int golemCount(SecurityBand band) {
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
