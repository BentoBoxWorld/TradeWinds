package world.bentobox.tradewinds.dataobjects;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

import org.bukkit.Location;
import org.bukkit.Material;

import world.bentobox.bentobox.database.Database;
import world.bentobox.tradewinds.TradeWinds;

/**
 * Cache-in-front-of-Database manager for {@link BoatHold} - every boat in the
 * ocean that carries anything or belongs to anyone. Player pointers (which
 * boat is theirs, which is their abandoned OLD BOAT) live on
 * {@link TWPlayerData}; this class is the single place that moves ownership
 * between them, so a boat's cargo exists exactly once however its avatar
 * travels.
 *
 * @author tastybento
 */
public class HoldManager {

    private final TradeWinds addon;
    private final Database<BoatHold> boats;
    private final Map<String, BoatHold> cache = new ConcurrentHashMap<>();

    public HoldManager(TradeWinds addon) {
        this(addon, new Database<>(addon, BoatHold.class));
    }

    /**
     * Test constructor with an injectable database, so the ownership rules
     * (which are the fiddly part) can be exercised for real rather than
     * against a stand-in.
     */
    HoldManager(TradeWinds addon, Database<BoatHold> boats) {
        this.addon = addon;
        this.boats = boats;
    }

    /**
     * A boat record by id, loading it if the cache is cold.
     *
     * @param boatId the boat id (from the entity/item PDC)
     * @return the record, or empty if there is none
     */
    public Optional<BoatHold> boat(String boatId) {
        if (boatId == null || boatId.isEmpty()) {
            return Optional.empty();
        }
        BoatHold cached = cache.get(boatId);
        if (cached != null) {
            return Optional.of(cached);
        }
        if (boats.objectExists(boatId)) {
            BoatHold loaded = boats.loadObject(boatId);
            if (loaded != null) {
                cache.put(boatId, loaded);
                return Optional.of(loaded);
            }
        }
        return Optional.empty();
    }

    /**
     * Register a brand new boat.
     *
     * @param material the boat material
     * @param owner the owning player, or null for an unowned hull
     * @return the fresh record
     */
    public BoatHold create(Material material, UUID owner) {
        BoatHold hold = new BoatHold(UUID.randomUUID().toString());
        hold.setMaterial(material.name());
        hold.setOwner(owner == null ? "" : owner.toString());
        cache.put(hold.getUniqueId(), hold);
        save(hold);
        return hold;
    }

    public void save(BoatHold hold) {
        cache.put(hold.getUniqueId(), hold);
        boats.saveObjectAsync(hold);
    }

    public void saveAll() {
        cache.values().forEach(boats::saveObjectAsync);
    }

    public void delete(String boatId) {
        cache.remove(boatId);
        boats.deleteID(boatId);
    }

    /**
     * Every boat record - the TTL sweep and admin tooling walk this.
     */
    public List<BoatHold> allBoats() {
        return boats.loadObjects();
    }

    /**
     * Remember where a boat was last seen, so {@code /tw chart} can point at
     * it from the other side of the ocean.
     */
    public void rememberPosition(BoatHold hold, Location where) {
        if (where == null || where.getWorld() == null) {
            return;
        }
        hold.setWorld(where.getWorld().getName());
        hold.setX(where.getBlockX());
        hold.setY(where.getBlockY());
        hold.setZ(where.getBlockZ());
        save(hold);
    }

    // --------------------------------------------------------- player pointers

    /**
     * The player's active boat record - the one that IS their hold.
     */
    public Optional<BoatHold> activeBoat(UUID playerId) {
        return boat(addon.getPlayerDataManager().get(playerId).getActiveBoat());
    }

    /**
     * The player's abandoned OLD BOAT, if they have one. It is unowned: this
     * is only a memory of where they left it.
     */
    public Optional<BoatHold> oldBoat(UUID playerId) {
        Optional<BoatHold> old = boat(addon.getPlayerDataManager().get(playerId).getOldBoat());
        // An OLD BOAT is only a boat that is still out there AND still
        // unowned. Once someone claims it (or it is gone) the marker is a
        // lie, so forget it rather than point at somebody else's ship.
        if (old.isEmpty() || !old.get().isUnowned()) {
            if (!addon.getPlayerDataManager().get(playerId).getOldBoat().isEmpty()) {
                clearOldBoat(playerId);
            }
            return Optional.empty();
        }
        return old;
    }

    /**
     * Make a boat this player's active boat. Whatever they had becomes their
     * (unowned, capturable) OLD BOAT - exactly one is remembered; anything
     * older is simply forgotten flotsam.
     *
     * @param playerId the player
     * @param hold the boat to make active, or null to leave them boatless
     */
    public void setActiveBoat(UUID playerId, BoatHold hold) {
        TWPlayerData data = addon.getPlayerDataManager().get(playerId);
        // Taking a boat takes it FROM someone: the loser stops owning it here
        // and now, or their record keeps a boat that is no longer theirs -
        // which is how one capture left both players pointing at the same
        // hull, each thinking it was their abandoned OLD BOAT (2026-08-02).
        if (hold != null && !hold.isUnowned() && !hold.getOwner().equals(playerId.toString())) {
            try {
                UUID loser = UUID.fromString(hold.getOwner());
                clearActiveBoat(loser);
                org.bukkit.entity.Player online = org.bukkit.Bukkit.getPlayer(loser);
                if (online != null) {
                    world.bentobox.bentobox.api.user.User.getInstance(online)
                            .sendMessage("tradewinds.boat.taken");
                }
            } catch (IllegalArgumentException e) {
                // Not a UUID - nothing to take it from
            }
        }
        String previous = data.getActiveBoat();
        if (previous != null && !previous.isEmpty() && (hold == null || !previous.equals(hold.getUniqueId()))) {
            boat(previous).ifPresent(old -> {
                old.setOwner("");
                save(old);
            });
            data.setOldBoat(previous);
        }
        if (hold == null) {
            data.setActiveBoat("");
        } else {
            data.setActiveBoat(hold.getUniqueId());
            hold.setOwner(playerId.toString());
            save(hold);
            // Reclaiming the boat you had abandoned clears the OLD BOAT mark
            if (hold.getUniqueId().equals(data.getOldBoat())) {
                data.setOldBoat("");
            }
        }
        addon.getPlayerDataManager().save(playerId);
    }

    /**
     * Stop pointing at a boat WITHOUT abandoning it: the boat was taken from
     * them, or burned. Abandonment (boarding another boat) goes through
     * {@link #setActiveBoat} instead, which is what leaves an OLD BOAT.
     *
     * @param playerId the player who no longer has this boat
     */
    public void clearActiveBoat(UUID playerId) {
        TWPlayerData data = addon.getPlayerDataManager().get(playerId);
        data.setActiveBoat("");
        addon.getPlayerDataManager().save(playerId);
    }

    /**
     * Forget a player's OLD BOAT marker (it was taken, broken or reclaimed).
     */
    public void clearOldBoat(UUID playerId) {
        TWPlayerData data = addon.getPlayerDataManager().get(playerId);
        data.setOldBoat("");
        addon.getPlayerDataManager().save(playerId);
    }

    /**
     * Whoever holds this boat as their OLD BOAT, if anyone - used to tell
     * them it has been taken.
     */
    public Optional<UUID> ownerOfOldBoat(String boatId) {
        return addon.getPlayerDataManager().findByOldBoat(boatId);
    }
}
