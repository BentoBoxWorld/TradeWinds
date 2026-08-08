package world.bentobox.tradewinds.travel;

import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;

import world.bentobox.bentobox.api.user.User;
import world.bentobox.tradewinds.TradeWinds;
import world.bentobox.tradewinds.dataobjects.TWPlayerData;

/**
 * The start of every trading career: an Oak Boat (rank 2 of the ladder - the
 * boat IS the hold) with the starter coal already in its fuel slots, and the
 * starting balance. Given once, on the player's first spawn. When the spawn
 * point is water the boat is launched and the player seated in it; on land
 * the boat item goes in the pack instead. Repeat visitors to spawn who own a
 * boat item get auto-launched too - nobody treads water at spawn.
 *
 * @author tastybento
 */
public class StarterKit {

    /** PDC key on boats recording the owning player's UUID (spec §4). */
    public static final NamespacedKey BOAT_OWNER_KEY = NamespacedKey.fromString("tradewinds:owner");

    /** The rung a career starts on. */
    public static final Material STARTER_BOAT = Material.OAK_BOAT;

    private final TradeWinds addon;

    public StarterKit(TradeWinds addon) {
        this.addon = addon;
    }

    /**
     * Handle a player arriving at spawn: first-timers get the kit; anyone with
     * a boat to hand gets launched if the spawn is on water.
     *
     * @param player the player, already teleported to spawn
     */
    public void onSpawnArrival(Player player) {
        TWPlayerData data = addon.getPlayerDataManager().get(player.getUniqueId());
        Location spawn = player.getLocation();
        boolean onWater = spawn.getBlock().getType() == Material.WATER
                || spawn.getBlock().getRelative(org.bukkit.block.BlockFace.DOWN).getType() == Material.WATER;
        if (!data.isStarterKitGiven()) {
            data.setStarterKitGiven(true);
            addon.getPlayerDataManager().save(player.getUniqueId());
            give(player, spawn, onWater);
            return;
        }
        // Repeat arrival: auto-launch the carried boat so nobody swims at spawn
        if (onWater && player.getVehicle() == null) {
            addon.getHoldService().active(player.getUniqueId()).ifPresent(hold -> {
                if (consumeBoatItem(player) != null) {
                    addon.getBoatService().launch(player, spawn, hold);
                }
            });
        }
    }

    /**
     * The kit itself: boat on the hold record, coal in the fuel slots, coin
     * in the purse. Also used by {@code /tw restart}.
     */
    public void give(Player player, Location spawn, boolean onWater) {
        var hold = addon.getBoatService().createFor(player, STARTER_BOAT);
        // Coal straight into the fuel slots: the first island hop can be a
        // warp instead of a long row
        int coal = addon.getSettings().getStarterCoal();
        if (coal > 0) {
            addon.getHoldService().addFuel(player, Material.COAL, coal);
        }
        if (onWater) {
            addon.getBoatService().launch(player, spawn, hold);
        } else {
            addon.getBoatService().giveBoatItem(player, hold);
        }
        addon.getPlugin().getVault()
                .ifPresent(vault -> vault.deposit(User.getInstance(player),
                        addon.getSettings().getStartingBalance()));
        User.getInstance(player).sendMessage("tradewinds.starter-kit.given");
    }

    /**
     * Remove one boat item the player OWNS from the inventory - the hold
     * record's boat, so a stray vanilla boat is never mistaken for theirs.
     *
     * @return the removed boat material, or null if none carried
     */
    private Material consumeBoatItem(Player player) {
        Material owned = addon.getHoldService().boat(player);
        if (owned == null) {
            return null;
        }
        for (ItemStack stack : player.getInventory().getContents()) {
            if (stack != null && stack.getType() == owned) {
                stack.setAmount(stack.getAmount() - 1);
                return owned;
            }
        }
        return null;
    }
}
