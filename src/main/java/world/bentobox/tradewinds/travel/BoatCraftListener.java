package world.bentobox.tradewinds.travel;

import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.CraftItemEvent;
import org.bukkit.inventory.ItemStack;

import world.bentobox.bentobox.api.user.User;
import world.bentobox.tradewinds.TradeWinds;

/**
 * Crafting a boat is the exploration route up the ladder (rare wood instead
 * of money) - but it obeys the One Boat Rule like everything else: a crafted
 * boat REPLACES yours, so crafting one smaller than or equal to your current
 * boat is refused (you would be burning wood to destroy cargo capacity).
 * Adding a chest to your boat (the vanilla boat+chest recipe) is the in-place
 * upgrade this deliberately allows.
 * <p>
 * Tech levels gate shops only - crafting works anywhere, including at sea.
 *
 * @author tastybento
 */
public class BoatCraftListener implements Listener {

    private final TradeWinds addon;

    public BoatCraftListener(TradeWinds addon) {
        this.addon = addon;
    }

    @EventHandler(priority = EventPriority.LOW, ignoreCancelled = true)
    public void onCraft(CraftItemEvent event) {
        ItemStack result = event.getRecipe().getResult();
        if (!isValidBoatCraft(event, result)) {
            return;
        }
        Material current = addon.getHoldService().boat(event.getWhoClicked().getUniqueId());
        if (shouldRefuseCraft(event, result, current)) {
            return;
        }
        // An upgrade: the crafted item becomes the boat, the old avatar goes.
        // Done next tick, once the crafted item actually exists in the pack.
        Material old = current;
        org.bukkit.Bukkit.getScheduler().runTask(addon.getPlugin(),
                () -> refitBoat(event.getWhoClicked(), result, old, addon.getBoatRanks().slots(result.getType())));
    }

    private boolean isValidBoatCraft(CraftItemEvent event, ItemStack result) {
        if (!(event.getWhoClicked() instanceof Player player) || result == null
                || !BoatRanks.isBoatItem(result.getType())) {
            return false;
        }
        return player.getWorld().equals(addon.getOverWorld())
                || player.getWorld().equals(addon.getNetherWorld());
    }

    private boolean shouldRefuseCraft(CraftItemEvent event, ItemStack result, Material current) {
        BoatRanks ranks = addon.getBoatRanks();
        int newSlots = ranks.slots(result.getType());
        if (newSlots <= 0) {
            // Not on the ladder: an ordinary vanilla boat, none of our business
            return false;
        }
        if (current != null && newSlots <= ranks.slots(current)) {
            event.setCancelled(true);
            User.getInstance(event.getWhoClicked()).sendMessage("tradewinds.trade.craft-smaller-refused");
            return true;
        }
        return false;
    }

    private void refitBoat(org.bukkit.entity.HumanEntity crafter, ItemStack result, Material old, int newSlots) {
        Player player = (Player) crafter;
        // A refit: the same hold, a bigger hull around it
        var activeRecord = addon.getHoldService().active(player.getUniqueId());
        var hold = activeRecord.orElseGet(() -> addon.getBoatService().createFor(player, result.getType()));
        hold.setMaterial(result.getType().name());
        addon.getHoldManager().save(hold);
        addon.getBoatService().relabel(hold);
        // Stamp the hull they just built, or it is an anonymous boat
        // item that merely matches their record by type - no lore, and
        // right-clicking it opens nothing
        if (!stampCrafted(player, result.getType(), hold)) {
            // Clicking the result puts the boat on the CURSOR and it may
            // not reach a slot for a tick or two - try once more before
            // giving up (playtest 2026-08-02: a crafted acacia boat came
            // out blank because only the inventory was searched)
            org.bukkit.Bukkit.getScheduler().runTaskLater(addon.getPlugin(),
                    () -> stampCrafted(player, result.getType(), hold), 5L);
        }
        removeOldBoat(player, old);
        User.getInstance(player).sendMessage("tradewinds.trade.boat-crafted", "[material]",
                world.bentobox.tradewinds.economy.PriceEngine.prettify(result.getType().name()),
                "[slots]", String.valueOf(newSlots));
    }

    private void removeOldBoat(Player player, Material old) {
        if (old != null) {
            for (ItemStack stack : player.getInventory().getContents()) {
                if (stack != null && stack.getType() == old) {
                    stack.setAmount(stack.getAmount() - 1);
                    break;
                }
            }
        }
    }

    /**
     * Give the freshly crafted hull its record. The item may be on the
     * CURSOR (a normal click on the result slot) rather than in a slot (a
     * shift-click), and only the second was searched before.
     *
     * @param player the crafter
     * @param type the boat they made
     * @param hold their boat record
     * @return true if a hull was stamped
     */
    private boolean stampCrafted(Player player, Material type, world.bentobox.tradewinds.dataobjects.BoatHold hold) {
        ItemStack cursor = player.getItemOnCursor();
        if (cursor.getType() == type && BoatService.boatId(cursor) == null) {
            player.setItemOnCursor(addon.getBoatService().stamp(cursor, hold));
            return true;
        }
        for (ItemStack stack : player.getInventory().getContents()) {
            if (stack != null && stack.getType() == type && BoatService.boatId(stack) == null) {
                addon.getBoatService().stamp(stack, hold);
                return true;
            }
        }
        return false;
    }
}
