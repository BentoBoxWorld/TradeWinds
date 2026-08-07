package world.bentobox.tradewinds.crime;

import java.util.ArrayList;
import java.util.List;

import org.bukkit.Location;
import org.bukkit.entity.Drowned;
import org.bukkit.entity.Entity;
import org.bukkit.entity.EntityType;
import org.bukkit.entity.Guardian;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Mob;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.util.Vector;

import world.bentobox.tradewinds.TradeWinds;
import world.bentobox.tradewinds.galaxy.DockPlan;
import world.bentobox.tradewinds.galaxy.IslandSpec;
import world.bentobox.tradewinds.galaxy.SecurityBand;

/**
 * Launches a customs patrol from an island toward a smuggler.
 * <p>
 * Police are PDC-tagged so they can be told from ordinary sea life, cleaned up
 * when a chase ends, and - critically - made to <b>drop nothing</b>. Loot-
 * bearing police would turn a criminal record into an iron farm, which is
 * exactly backwards (spec section 7).
 * <p>
 * Stage 6b spawns the sea patrol that makes a customs detection a chase. Stage
 * 6c builds the rest of the force on this: land golems, pursuing phantoms, and
 * the standing response to a wanted player anywhere in policed water.
 *
 * @author tastybento
 */
public class PoliceDispatch {

    private final TradeWinds addon;

    public PoliceDispatch(TradeWinds addon) {
        this.addon = addon;
    }

    /**
     * Send a patrol after a player. Units surface between the smuggler and the
     * island they were trying to enter, so running for open sea is a real
     * option and running for the port is not.
     *
     * @param player the smuggler
     * @param island the island whose customs launched them
     * @return the units dispatched
     */
    public List<Entity> dispatch(Player player, IslandSpec island) {
        int count = patrolSize(island.band());
        List<Entity> units = new ArrayList<>();
        Location from = player.getLocation();
        // Customs launch from the HARBOUR, not from thin air beside the boat.
        // Spawning them near the quarry was an ambush however far out it was
        // set - the fix is not a bigger radius, it is the right origin. A
        // patrol that has to row out from the pier is one you can see coming
        // and outrun, which is the whole point of the decision window.
        Location pier = pierOf(island, from.getWorld());
        Location launch = openWater(launchPoint(pier, from), from, minimumStandoff());
        if (launch == null) {
            addon.log("Customs at " + island.name() + ": no open water to launch from near "
                    + describe(pier) + " - confiscating instead of chasing");
            return units;
        }
        addon.log("Customs at " + island.name() + ": player at " + describe(from) + ", pier at "
                + describe(pier) + ", launching from " + describe(launch) + " ("
                + (int) launch.distance(from) + " blocks from the player)");
        for (PoliceUnit kind : PoliceRoster.forCustoms(count)) {
            Location at = spawnPoint(kind, launch, from);
            Entity unit = at == null ? null : spawn(at, entityType(kind), player);
            if (unit != null) {
                units.add(unit);
                addon.log("   " + kind + " at " + describe(at) + " - "
                        + (int) at.distance(from) + " blocks from the player");
            } else {
                addon.log("   " + kind + " could not be placed near the pier");
            }
        }
        return units;
    }

    /**
     * Where the patrol actually appears: from the pier if the smuggler is near
     * it, otherwise as far along the way from the pier as the server will
     * actually simulate.
     * <p>
     * Launching literally from the quay was right in spirit and wrong in
     * practice. A patrol dispatched 200 blocks away sits outside the
     * simulation distance (10 chunks, 160 blocks, by default) and never ticks
     * - so it does not swim, does not chase, and does nothing at all. The
     * player watches four mobs spawn in the log and meets none of them. So the
     * launch point is pulled along the line from pier to smuggler until it is
     * close enough to be alive, which reads as a patrol that has already rowed
     * most of the way out.
     *
     * @param pier the island's quay end
     * @param player where the smuggler is
     * @return the point to launch from
     */
    private Location launchPoint(Location pier, Location player) {
        double max = addon.getSettings().getPatrolDistance();
        double distance = pier.distance(player);
        if (distance <= max) {
            return pier;
        }
        // Along the line from the player toward the pier, at the maximum range
        // that still ticks and can still be seen
        Vector toward = pier.toVector().subtract(player.toVector()).setY(0);
        if (toward.lengthSquared() < 0.01) {
            toward = new Vector(1, 0, 0);
        }
        Location spot = player.clone().add(toward.normalize().multiply(max));
        spot.setY(addon.getSettings().getSeaHeight() - 1.0);
        return spot;
    }

    /**
     * The end of an island's quay - where a harbour's boats put out from.
     */
    private Location pierOf(IslandSpec island, org.bukkit.World world) {
        DockPlan plan = addon.getGalaxyEngine(world.getSeed()).dockPlan(island);
        int pierX = island.centerX() + (int) Math.round(Math.cos(plan.bearing()) * plan.dockEnd());
        int pierZ = island.centerZ() + (int) Math.round(Math.sin(plan.bearing()) * plan.dockEnd());
        return new Location(world, pierX + 0.5, addon.getSettings().getSeaHeight() - 1.0, pierZ + 0.5);
    }

    /**
     * Where one unit of a patrol starts: swimmers in the water off the pier,
     * the phantom in the air above it.
     */
    private Location spawnPoint(PoliceUnit kind, Location launch, Location player) {
        Location scattered = launch.clone().add(Math.random() * 8 - 4, 0, Math.random() * 8 - 4);
        if (kind == PoliceUnit.PHANTOM) {
            scattered.setY(addon.getSettings().getSeaHeight() + 12 + Math.random() * 6);
            return scattered;
        }
        return openWater(scattered, player, minimumStandoff());
    }

    /**
     * Compact coordinates for the console.
     */
    private static String describe(Location location) {
        return location.getBlockX() + "," + location.getBlockY() + "," + location.getBlockZ();
    }

    /**
     * Send the standing response to a wanted player: whatever the band can
     * field, picked for where the target is standing.
     *
     * @param player the wanted player
     * @param island the island whose law is responding
     * @param ashore whether the target is on land
     * @param fugitive whether they are a fugitive rather than merely wanted
     * @return the units dispatched
     */
    public List<Entity> dispatchWanted(Player player, IslandSpec island, boolean ashore, boolean fugitive) {
        List<Entity> units = new ArrayList<>();
        Location from = player.getLocation();
        for (PoliceUnit kind : PoliceRoster.forWanted(patrolSize(island.band()), ashore,
                fugitive)) {
            Location at = spawnPoint(kind, from);
            Entity unit = at == null ? null : spawn(at, entityType(kind), player);
            if (unit != null) {
                units.add(unit);
            }
        }
        return units;
    }

    /**
     * Where a unit of this kind can stand: golems need ground, swimmers need
     * water, phantoms need only air - and none of them may appear inside the
     * arrest radius.
     */
    private Location spawnPoint(PoliceUnit kind, Location from) {
        double standoff = minimumStandoff();
        return switch (kind) {
        case PHANTOM -> from.clone().add(offset(standoff), 12 + Math.random() * 6, offset(standoff));
        case GOLEM -> ground(from, standoff);
        default -> openWater(from.clone().add(offset(standoff * 1.5), 0, offset(standoff * 1.5)), from, standoff);
        };
    }

    private static double offset(double standoff) {
        double sign = Math.random() < 0.5 ? -1 : 1;
        return sign * (standoff + Math.random() * standoff);
    }

    /**
     * A solid footing near the target for a golem, or null if there is none -
     * a golem dropped into deep water is just a drowning golem.
     */
    private Location ground(Location from, double standoff) {
        for (int attempt = 0; attempt < 12; attempt++) {
            double angle = attempt * Math.PI / 6;
            double radius = standoff + Math.random() * standoff;
            Location at = from.clone().add(Math.cos(angle) * radius, 0, Math.sin(angle) * radius);
            at.setY(from.getWorld().getHighestBlockYAt(at) + 1.0);
            if (at.getY() > addon.getSettings().getSeaHeight()
                    && at.getBlock().getType() == org.bukkit.Material.AIR) {
                return at;
            }
        }
        return null;
    }

    private static EntityType entityType(PoliceUnit unit) {
        return switch (unit) {
        case GOLEM -> EntityType.IRON_GOLEM;
        case GUARDIAN -> EntityType.GUARDIAN;
        case DROWNED -> EntityType.DROWNED;
        case PHANTOM -> EntityType.PHANTOM;
        };
    }

    /**
     * How close a patrol may surface to its quarry. This must stay comfortably
     * beyond the arrest radius: the first cut let the water search fall back to
     * the player's own position, so patrols materialised alongside the boat,
     * opened fire, and the chase tick registered an arrest before the player
     * had read the warning. A chase you cannot run from is not a chase.
     *
     * @return the minimum spawn distance in blocks
     */
    private double minimumStandoff() {
        return Math.max(12.0, addon.getSettings().getCaughtRadius() * 3);
    }

    /**
     * The nearest sea-level water column to a spot, searching outward, or null
     * if there is none within reach. Police are sailors: they need somewhere to
     * be - but never within {@code standoff} blocks of the player.
     *
     * @param spot the preferred position
     * @param player where the quarry is, which the patrol must not spawn on top of
     * @param standoff minimum distance from the player
     * @return a water location, or null
     */
    private Location openWater(Location spot, Location player, double standoff) {
        int seaY = addon.getSettings().getSeaHeight();
        double standoffSquared = standoff * standoff;
        // Widen the ring around the intended spot until water turns up, then
        // fall back to a ring around the player at the standoff distance
        for (int radius = 0; radius <= 32; radius += 4) {
            for (int attempt = 0; attempt < 12; attempt++) {
                double angle = attempt * Math.PI / 6;
                Location at = spot.clone().add(Math.cos(angle) * radius, 0, Math.sin(angle) * radius);
                at.setY(seaY - 1.0);
                if (at.distanceSquared(player) >= standoffSquared
                        && at.getBlock().getType() == org.bukkit.Material.WATER) {
                    return at;
                }
            }
        }
        for (int radius = (int) standoff; radius <= standoff + 32; radius += 4) {
            for (int attempt = 0; attempt < 12; attempt++) {
                double angle = attempt * Math.PI / 6;
                Location at = player.clone().add(Math.cos(angle) * radius, 0, Math.sin(angle) * radius);
                at.setY(seaY - 1.0);
                if (at.getBlock().getType() == org.bukkit.Material.WATER) {
                    return at;
                }
            }
        }
        return null;
    }

    /**
     * How many units a band's customs office can field. Safe space answers
     * hard; out in the lawless bands there is nobody to send - which is the
     * whole reason to run cargo out there.
     */
    int patrolSize(SecurityBand band) {
        return addon.getSettings().getPatrolSize().getOrDefault(band.name(), 0);
    }

    private Entity spawn(Location at, EntityType type, Player target) {
        if (at.getWorld() == null) {
            return null;
        }
        Entity entity = at.getWorld().spawnEntity(at, type);
        if (!(entity instanceof LivingEntity living)) {
            entity.remove();
            return null;
        }
        tag(living);
        if (living instanceof Drowned drowned) {
            // Armed and dangerous: a trident thrower is a threat to a boat
            drowned.getEquipment().setItemInMainHand(new ItemStack(org.bukkit.Material.TRIDENT));
            drowned.getEquipment().setItemInMainHandDropChance(0);
        }
        if (living instanceof Guardian guardian) {
            guardian.setRemoveWhenFarAway(true);
        }
        if (living instanceof Mob mob) {
            mob.setTarget(target);
        }
        return living;
    }

    /**
     * Mark a unit as police: identifies it for the chase, for cleanup, and for
     * the no-drops rule.
     *
     * @param entity the unit
     */
    public void tag(LivingEntity entity) {
        entity.getPersistentDataContainer().set(addon.getPoliceKey(), PersistentDataType.BYTE, (byte) 1);
        entity.setPersistent(false);
        // Police never yield loot or XP - otherwise a wanted player is an
        // infinite farm and crime literally pays
        entity.setCanPickupItems(false);
        if (entity.getEquipment() != null) {
            entity.getEquipment().setArmorContents(null);
        }
    }

    /**
     * Whether an entity is a police unit.
     *
     * @param entity the entity
     * @return true if tagged
     */
    public boolean isPolice(Entity entity) {
        return entity.getPersistentDataContainer().has(addon.getPoliceKey(), PersistentDataType.BYTE);
    }

    /**
     * Stand a patrol down and remove it.
     *
     * @param units the units to recall
     */
    public void recall(List<Entity> units) {
        units.stream().filter(Entity::isValid).forEach(Entity::remove);
    }
}
