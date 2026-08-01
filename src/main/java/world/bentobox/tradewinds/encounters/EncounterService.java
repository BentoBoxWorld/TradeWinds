package world.bentobox.tradewinds.encounters;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.Sound;
import org.bukkit.entity.Boat;
import org.bukkit.entity.Entity;
import org.bukkit.entity.EntityType;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Mob;
import org.bukkit.entity.Player;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.scheduler.BukkitTask;
import org.bukkit.util.Vector;

import world.bentobox.bentobox.api.user.User;
import world.bentobox.tradewinds.TradeWinds;
import world.bentobox.tradewinds.galaxy.GalaxyEngine;
import world.bentobox.tradewinds.galaxy.IslandSpec;
import world.bentobox.tradewinds.galaxy.SecurityBand;

/**
 * Random sea encounters: the risk that makes rowing a choice rather than a
 * free ride (spec principle 2 - warping risks the interstice, rowing risks the
 * lawless ocean). Mobs appear at a visible distance ahead, so fleeing is
 * always an option; killing them yields customs-stamped salvage, making
 * adventuring a third income beside honest margins and smuggling.
 *
 * @author tastybento
 */
public class EncounterService {

    /** PDC key marking encounter mobs (booty, cleanup). */
    public static final NamespacedKey ENCOUNTER_KEY = NamespacedKey.fromString("tradewinds:encounter");

    /** How close before a boated crew abandons ship to attack. */
    private static final double BOARDING_RANGE = 14.0;
    /** How far encounter mobs keep hunting. */
    private static final double HUNT_RANGE = 48.0;

    private final TradeWinds addon;
    private BukkitTask task;
    private BukkitTask aggression;

    public EncounterService(TradeWinds addon) {
        this.addon = addon;
    }

    public void start() {
        long period = Math.max(1, addon.getSettings().getEncounterCheckSeconds()) * 20L;
        task = Bukkit.getScheduler().runTaskTimer(addon.getPlugin(), this::tick, period, period);
        // Mobs forget, and a passenger cannot fight: keep them hunting
        aggression = Bukkit.getScheduler().runTaskTimer(addon.getPlugin(), this::hunt, 40L, 40L);
    }

    public void stop() {
        if (task != null) {
            task.cancel();
        }
        if (aggression != null) {
            aggression.cancel();
        }
    }

    /**
     * Keep encounters dangerous: re-assert targets (mobs lose interest, and a
     * freshly spawned target is forgotten within moments) and put crews over
     * the side when their quarry is close - a witch or pillager riding a boat
     * cannot run its attack goals at all, which made the sea witch a harmless
     * ornament.
     */
    private void hunt() {
        if (addon.getOverWorld() == null) {
            return;
        }
        for (Player player : addon.getOverWorld().getPlayers()) {
            if (player.getGameMode() != org.bukkit.GameMode.SURVIVAL || player.isDead()) {
                continue;
            }
            for (Entity entity : player.getNearbyEntities(HUNT_RANGE, 32, HUNT_RANGE)) {
                if (!entity.getPersistentDataContainer().has(ENCOUNTER_KEY, PersistentDataType.STRING)
                        || !(entity instanceof Mob mob)) {
                    continue;
                }
                if (mob.getTarget() == null || mob.getTarget().isDead()) {
                    mob.setTarget(player);
                }
                if (mob.getVehicle() instanceof Boat
                        && mob.getLocation().distanceSquared(player.getLocation()) < BOARDING_RANGE * BOARDING_RANGE) {
                    mob.leaveVehicle();
                }
            }
        }
    }

    private void tick() {
        if (!addon.getSettings().isEncountersEnabled() || addon.getOverWorld() == null) {
            return;
        }
        for (Player player : addon.getOverWorld().getPlayers()) {
            if (player.getGameMode() != org.bukkit.GameMode.SURVIVAL || player.isDead()) {
                continue;
            }
            // At sea means at sea: in or on water, away from dry land
            if (!isAtSea(player)) {
                continue;
            }
            maybeSpawn(player);
        }
    }

    private boolean isAtSea(Player player) {
        Location loc = player.getLocation();
        return loc.getBlock().getType() == Material.WATER
                || (player.getVehicle() instanceof Boat
                        && loc.getBlock().getRelative(org.bukkit.block.BlockFace.DOWN).getType() == Material.WATER);
    }

    private void maybeSpawn(Player player) {
        GalaxyEngine engine = addon.getGalaxyEngine(addon.getOverWorld().getSeed());
        int x = player.getLocation().getBlockX();
        int z = player.getLocation().getBlockZ();
        int range = addon.getSettings().getIslandDistance();
        // The governing band: the nearest island's, or ANARCHIC out in the deep
        Optional<IslandSpec> nearest = engine.islandsNear(x, z, range).stream()
                .min(java.util.Comparator.comparingLong(spec -> spec.distanceSquared(x, z)));
        SecurityBand band = nearest.map(IslandSpec::band).orElse(SecurityBand.ANARCHIC);
        double distance = nearest.map(spec -> Math.sqrt(spec.distanceSquared(x, z))).orElse((double) range);
        double base = addon.getSettings().getEncounterChance().getOrDefault(band.name(), 0.0);
        if (Math.random() >= EncounterTable.chance(distance, range, base)) {
            return;
        }
        // One encounter at a time per player
        if (countNearbyEncounterMobs(player) > 0) {
            return;
        }
        boolean isDay = addon.getOverWorld().isDayTime();
        EncounterTable.pick(band, isDay, Math.random()).ifPresent(type -> spawn(player, type));
    }

    private int countNearbyEncounterMobs(Player player) {
        return (int) player.getNearbyEntities(64, 32, 64).stream()
                .filter(entity -> entity.getPersistentDataContainer().has(ENCOUNTER_KEY, PersistentDataType.STRING))
                .count();
    }

    /**
     * Spawn an encounter ahead of the player, far enough to be seen and fled.
     */
    public void spawn(Player player, EncounterType type) {
        Location origin = player.getLocation();
        Vector ahead = origin.getDirection().setY(0);
        if (ahead.lengthSquared() < 0.01) {
            ahead = new Vector(1, 0, 0);
        }
        ahead.normalize().multiply(addon.getSettings().getEncounterDistance());
        Location spot = origin.clone().add(ahead);
        spot.setY(addon.getSettings().getSeaHeight() + 1.0);

        int count = type.getMin()
                + (int) (Math.random() * Math.max(1, type.getMax() - type.getMin() + 1));
        List<Entity> spawned = new ArrayList<>();
        for (int i = 0; i < count; i++) {
            Location at = spot.clone().add(Math.random() * 6 - 3, 0, Math.random() * 6 - 3);
            Boat crewBoat = null;
            if (type.isBoated()) {
                crewBoat = (Boat) at.getWorld().spawnEntity(at, EntityType.OAK_BOAT);
                tag(crewBoat);
                spawned.add(crewBoat);
            }
            for (EntityType mobType : type.getMobs()) {
                Entity entity = at.getWorld().spawnEntity(type.isBoated() ? at : at.clone().add(0, 0.5, 0), mobType);
                equip(entity, mobType);
                tag(entity);
                if (entity instanceof Mob mob) {
                    mob.setTarget(player);
                    mob.setRemoveWhenFarAway(true);
                }
                if (crewBoat != null) {
                    crewBoat.addPassenger(entity);
                }
                spawned.add(entity);
            }
        }
        if (spawned.isEmpty()) {
            return;
        }
        User.getInstance(player).sendMessage("tradewinds.encounter." + type.name().toLowerCase(java.util.Locale.ENGLISH));
        player.playSound(player.getLocation(), Sound.AMBIENT_UNDERWATER_LOOP_ADDITIONS_RARE, 1.0f, 0.6f);
    }

    /**
     * Drowned throw tridents - that is what makes them dangerous to a boat.
     */
    private void equip(Entity entity, EntityType type) {
        if (type == EntityType.DROWNED && entity instanceof LivingEntity living && living.getEquipment() != null
                && Math.random() < addon.getSettings().getDrownedTridentChance()) {
            living.getEquipment().setItemInMainHand(new org.bukkit.inventory.ItemStack(Material.TRIDENT));
            living.getEquipment().setItemInMainHandDropChance(0.15f);
        }
    }

    private void tag(Entity entity) {
        entity.getPersistentDataContainer().set(ENCOUNTER_KEY, PersistentDataType.STRING, "encounter");
    }
}
