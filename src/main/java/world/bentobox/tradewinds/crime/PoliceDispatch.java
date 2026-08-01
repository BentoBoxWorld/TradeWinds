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

    /** How far ahead of the smuggler the patrol surfaces, in blocks. */
    private static final double INTERCEPT_DISTANCE = 22.0;

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
        // Between the player and the island: the patrol comes from the port
        Vector toIsland = new Vector(island.centerX() - from.getX(), 0, island.centerZ() - from.getZ());
        if (toIsland.lengthSquared() < 0.01) {
            toIsland = new Vector(1, 0, 0);
        }
        Location spot = from.clone().add(toIsland.normalize().multiply(INTERCEPT_DISTANCE));
        spot.setY(addon.getSettings().getSeaHeight() - 2.0);
        for (int i = 0; i < count; i++) {
            Location at = spot.clone().add(Math.random() * 6 - 3, 0, Math.random() * 6 - 3);
            Entity unit = spawn(at, i % 3 == 0 ? EntityType.GUARDIAN : EntityType.DROWNED, player);
            if (unit != null) {
                units.add(unit);
            }
        }
        return units;
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
