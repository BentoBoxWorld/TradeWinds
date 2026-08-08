package world.bentobox.tradewinds.listeners;

import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerRespawnEvent;

import world.bentobox.bentobox.api.user.User;
import world.bentobox.tradewinds.TradeWinds;

/**
 * Respawn safety: a player who dies in a TradeWinds world without a bed or
 * respawn anchor respawns on the spawn islet - never in the seabed. (Vanilla
 * hunts for solid ground near world spawn; in an ocean world that used to
 * mean being entombed at the bottom and dying on repeat.)
 * <p>
 * Death also drops the boat where they fell, so a respawned sailor would be
 * marooned ashore with no way back to their own wreck. The port lends them a
 * boat ({@code boats.respawn-boat}, default a bamboo raft; NONE disables) -
 * but only if they have none within reach, so keepInventory deaths and bed
 * respawns are never double-boated. The loaner becomes their ACTIVE boat on
 * the spot, demoting the wreck's hull to an OLD BOAT: a hull nobody owns is
 * not a hold, and its owner cannot trade.
 *
 * @author tastybento
 */
public class SpawnRespawnListener implements Listener {

    private final TradeWinds addon;

    public SpawnRespawnListener(TradeWinds addon) {
        this.addon = addon;
    }

    @EventHandler(priority = EventPriority.HIGH)
    public void onRespawn(PlayerRespawnEvent event) {
        World world = event.getPlayer().getWorld();
        if (!world.equals(addon.getOverWorld()) && !world.equals(addon.getNetherWorld())) {
            return;
        }
        // The loaner is granted a tick AFTER the respawn: items handed out
        // during PlayerRespawnEvent are wiped when the respawn restores the
        // inventory, which is why no raft ever arrived (playtest 2026-08-05).
        // A tick later also means the player's REAL position judges whether
        // their own boat is within reach - island respawners included.
        org.bukkit.Bukkit.getScheduler().runTask(addon.getPlugin(),
                () -> grantRespawnBoat(event.getPlayer()));
        if (event.isBedSpawn() || event.isAnchorSpawn()) {
            return; // they made a home somewhere - honor it
        }
        World overworld = addon.getOverWorld();
        if (overworld == null) {
            return;
        }
        // Island members respawn at their claim: BentoBox's ISLAND_RESPAWN
        // listener already set the location (it runs at NORMAL, this handler
        // at HIGH) - overwriting it here sent island owners to the spawn
        // port (playtest 2026-08-05). This listener predates player islands:
        // its job is only the ISLANDLESS, who would otherwise respawn in the
        // seabed.
        var island = addon.getIslands().getIsland(overworld, event.getPlayer().getUniqueId());
        if (island != null && island.getMemberSet().contains(event.getPlayer().getUniqueId())) {
            // ...but even a member's respawn deserves the safety net: a stale
            // island record over a regenerated world sent a member through
            // this branch into the campfire death-loop with no spiral to
            // save them (playtest 2026-08-07). WHERE they respawn stays
            // BentoBox's choice; we only nudge to the nearest safe column.
            event.setRespawnLocation(safeNear(event.getRespawnLocation()));
            return;
        }
        // The spawn island's own spawn point - the market plaza, and wherever
        // an admin has since moved it. The island CENTRE is wooded ground and
        // must not be used: players were respawning in the treetops.
        Location point = addon.getIslands().getSpawnPoint(overworld);
        event.setRespawnLocation(safeNear(point != null ? point : overworld.getSpawnLocation()));
    }

    /**
     * The nearest SAFE column to a respawn point. The spawn pad is kept bare
     * by the decorator, but a respawn must never gamble: an admin can move
     * the spawn point anywhere, and one seed put the plaza campfire exactly
     * under it - a death-loop in open flame (playtest 2026-08-07). Spirals a
     * few columns out using BentoBox's own safety test; spawn chunks are
     * always loaded, so the check is cheap and synchronous.
     */
    private Location safeNear(Location point) {
        if (addon.getIslands().isSafeLocation(point)) {
            return point;
        }
        for (int r = 1; r <= 4; r++) {
            for (int dx = -r; dx <= r; dx++) {
                for (int dz = -r; dz <= r; dz++) {
                    if (Math.max(Math.abs(dx), Math.abs(dz)) != r) {
                        continue; // ring, not disc: nearest first
                    }
                    for (int dy = 0; dy <= 2; dy++) {
                        Location candidate = point.clone().add(dx, dy, dz);
                        if (addon.getIslands().isSafeLocation(candidate)) {
                            return candidate;
                        }
                    }
                }
            }
        }
        return point; // nothing better nearby - at least it is the plaza
    }

    /**
     * The port's loaner: a boatless respawner gets the configured hull (their
     * own boat is floating back where they died, and rowing out to reclaim it
     * is the intended recovery trip). Runs a tick after the respawn, on the
     * player's real post-respawn state.
     */
    private void grantRespawnBoat(org.bukkit.entity.Player player) {
        if (!player.isOnline()) {
            return;
        }
        Material boat = respawnBoat();
        if (boat == null || stillHasTheirBoat(player) || boatWithinReach(player)) {
            return;
        }
        // The loaner is theirs the MOMENT it is handed over. An unowned hull
        // is not a hold: the harbourmaster's raft sat in the pack while every
        // trader turned its owner away for having no ship at the quay, and it
        // only became a boat once it was placed and boarded (playtest
        // 2026-08-08). Whatever they were sailing becomes their OLD BOAT -
        // still charted, still carrying its cargo, still theirs to row back
        // out and reclaim.
        boolean replacing = addon.getHoldService().active(player.getUniqueId()).isPresent();
        var hold = addon.getBoatService().createFor(player, boat);
        addon.getBoatService().giveBoatItem(player, hold);
        User.getInstance(player).sendMessage(
                replacing ? "tradewinds.boat.respawn-given-replacing" : "tradewinds.boat.respawn-given",
                "[material]", world.bentobox.tradewinds.economy.PriceEngine.prettify(boat.name()));
    }

    /**
     * Whether their own boat is already in their pack - a keepInventory death,
     * where the hull never left them. The loaner would demote a boat they are
     * literally holding, cargo and all, so there is nothing to lend.
     */
    private boolean stillHasTheirBoat(org.bukkit.entity.Player player) {
        var hold = addon.getHoldService().active(player.getUniqueId());
        return hold.isPresent() && addon.getBoatService().isCarrying(player, hold.get());
    }

    /**
     * Whether their own boat is close enough to where they now stand to walk
     * to - otherwise a sailor is stranded ashore with a ship an ocean away.
     */
    private boolean boatWithinReach(org.bukkit.entity.Player player) {
        var hold = addon.getHoldService().active(player.getUniqueId());
        if (hold.isEmpty() || hold.get().getWorld() == null || hold.get().getWorld().isEmpty()) {
            return false;
        }
        Location at = player.getLocation();
        if (at.getWorld() == null || !at.getWorld().getName().equals(hold.get().getWorld())) {
            return false;
        }
        int reach = addon.getSettings().getIslandProtectionRange();
        return at.distanceSquared(new Location(at.getWorld(), hold.get().getX(), hold.get().getY(),
                hold.get().getZ())) <= (double) reach * reach;
    }

    /**
     * The configured respawn boat, or null when disabled (NONE/blank/unknown
     * or off-ladder names all disable - an admin typo must not hand out air).
     */
    private Material respawnBoat() {
        String name = addon.getSettings().getRespawnBoat();
        if (name == null || name.isBlank() || "NONE".equalsIgnoreCase(name)) {
            return null;
        }
        Material material = Material.matchMaterial(name);
        return material != null && addon.getBoatRanks().slots(material) > 0 ? material : null;
    }
}
