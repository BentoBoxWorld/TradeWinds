package world.bentobox.tradewinds.tasks;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.entity.Entity;
import org.bukkit.entity.IronGolem;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Villager;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.scheduler.BukkitTask;

import world.bentobox.tradewinds.TradeWinds;
import world.bentobox.tradewinds.galaxy.DockPlan;
import world.bentobox.tradewinds.galaxy.GalaxyEngine;
import world.bentobox.tradewinds.galaxy.IslandSpec;
import world.bentobox.tradewinds.generator.IslandDecorator;

/**
 * The resident keeper: a periodic sweep over loaded residents and plazas that
 * (a) teleports stray residents home (villagers scatter when players hit them;
 * the tether quietly puts the market back together) and (b) respawns killed
 * residents after a configurable delay - a massacred market always recovers.
 *
 * @author tastybento
 */
public class ResidentAuditTask implements Runnable {

    private final TradeWinds addon;
    private BukkitTask task;
    // Island name -> epoch millis when a deficit was first noticed
    private final Map<String, Long> deficitSince = new HashMap<>();

    public ResidentAuditTask(TradeWinds addon) {
        this.addon = addon;
    }

    public void start() {
        long period = addon.getSettings().getResidentAuditPeriodSeconds() * 20L;
        task = Bukkit.getScheduler().runTaskTimer(addon.getPlugin(), this, period, period);
    }

    public void stop() {
        if (task != null) {
            task.cancel();
        }
    }

    @Override
    public void run() {
        World world = addon.getOverWorld();
        if (world == null) {
            return;
        }
        GalaxyEngine engine = addon.getGalaxyEngine(world.getSeed());
        // Tether: return loaded strays to their home
        for (Entity entity : world.getEntities()) {
            String home = entity.getPersistentDataContainer().get(IslandDecorator.HOME_KEY,
                    PersistentDataType.STRING);
            if (home != null) {
                tether(entity, home);
            }
        }
        // Respawn: audit islands whose plaza chunks are loaded
        for (org.bukkit.entity.Player player : world.getPlayers()) {
            engine.islandsNear(player.getLocation().getBlockX(), player.getLocation().getBlockZ(),
                    engine.getConfig().terrainRadius() * 2).forEach(spec -> audit(world, engine, spec));
        }
    }

    private void tether(Entity entity, String home) {
        String[] parts = home.split(",");
        Location homeLoc = new Location(entity.getWorld(), Integer.parseInt(parts[0]) + 0.5,
                Integer.parseInt(parts[1]) + 1.0, Integer.parseInt(parts[2]) + 0.5);
        int radius = addon.getSettings().getResidentTetherRadius();
        if (entity.getLocation().distanceSquared(homeLoc) > (double) radius * radius) {
            entity.teleport(homeLoc);
        }
    }

    private void audit(World world, GalaxyEngine engine, IslandSpec spec) {
        DockPlan plan = engine.dockPlan(spec);
        if (!world.isChunkLoaded(plan.plazaX() >> 4, plan.plazaZ() >> 4)) {
            return;
        }
        int surface = engine.getConfig().seaLevel() + GalaxyEngine.PLAZA_RISE;
        Location plaza = new Location(world, plan.plazaX() + 0.5, surface + 1.0, plan.plazaZ() + 0.5);
        List<Entity> nearby = List.copyOf(world.getNearbyEntities(plaza, 48, 24, 48,
                e -> spec.name().equals(e.getPersistentDataContainer().get(IslandDecorator.RESIDENT_KEY,
                        PersistentDataType.STRING))));
        long villagers = nearby.stream().filter(Villager.class::isInstance).count();
        long golems = nearby.stream().filter(IronGolem.class::isInstance).count();
        int wantVillagers = engine.villagerCount(spec);
        int wantGolems = IslandDecorator.golemCount(spec.band());
        if (villagers >= wantVillagers && golems >= wantGolems) {
            deficitSince.remove(spec.name());
            return;
        }
        long since = deficitSince.computeIfAbsent(spec.name(), k -> System.currentTimeMillis());
        if (System.currentTimeMillis() - since < addon.getSettings().getResidentRespawnDelayMinutes() * 60_000L) {
            return;
        }
        // The market recovers: respawn the missing residents at the plaza
        for (long i = villagers; i < wantVillagers; i++) {
            Villager villager = world.spawn(plaza, Villager.class);
            IslandDecorator.configureVillager(villager, spec, (int) i, plan.plazaX(), surface, plan.plazaZ());
        }
        for (long i = golems; i < wantGolems; i++) {
            IronGolem golem = world.spawn(plaza, IronGolem.class);
            IslandDecorator.configureResident(golem, spec, plan.plazaX(), surface, plan.plazaZ());
        }
        deficitSince.remove(spec.name());
        addon.log("Respawned residents at " + spec.name() + " (" + (wantVillagers - villagers) + " villagers, "
                + (wantGolems - golems) + " golems)");
    }

    /**
     * Visible for tests: whether an entity would be tethered from a position.
     */
    boolean wouldTether(LivingEntity entity, Location homeLoc) {
        int radius = addon.getSettings().getResidentTetherRadius();
        return entity.getLocation().distanceSquared(homeLoc) > (double) radius * radius;
    }
}
