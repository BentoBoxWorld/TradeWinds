package world.bentobox.tradewinds.travel;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

import org.bukkit.Bukkit;
import org.bukkit.Sound;
import org.bukkit.block.ShulkerBox;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryCloseEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.BlockStateMeta;

import world.bentobox.tradewinds.TradeWinds;

/**
 * Opens a cargo expander in the hand. Java Edition cannot open a shulker box
 * from the inventory, so without this the carried hold could never be filled
 * or emptied by hand - the whole point of carrying it. Right-click opens the
 * expander's contents; closing writes them back into the item.
 * <p>
 * Expanders are never placed as blocks: they are cargo, and a placed one
 * would be a chest anyone could rob.
 *
 * @author tastybento
 */
public class ExpanderListener implements Listener {

    private final TradeWinds addon;
    /** Player -> hotbar slot of the expander they have open. */
    private final Map<UUID, Integer> open = new HashMap<>();

    public ExpanderListener(TradeWinds addon) {
        this.addon = addon;
    }

    @EventHandler(priority = EventPriority.LOW, ignoreCancelled = true)
    public void onRightClick(PlayerInteractEvent event) {
        if (event.getHand() != EquipmentSlot.HAND
                || (event.getAction() != Action.RIGHT_CLICK_AIR && event.getAction() != Action.RIGHT_CLICK_BLOCK)
                || !addon.getHoldService().isExpander(event.getItem())) {
            return;
        }
        // Never place it as a block - it is cargo
        event.setCancelled(true);
        Player player = event.getPlayer();
        ItemStack expander = event.getItem();
        if (!(expander.getItemMeta() instanceof BlockStateMeta meta)
                || !(meta.getBlockState() instanceof ShulkerBox box)) {
            return;
        }
        Inventory view = Bukkit.createInventory(null, 27, world.bentobox.bentobox.api.user.User
                .getInstance(player).getTranslationAsComponent("tradewinds.item.expander-view", new String[0]));
        view.setContents(box.getInventory().getContents());
        open.put(player.getUniqueId(), player.getInventory().getHeldItemSlot());
        player.openInventory(view);
        player.playSound(player.getLocation(), Sound.BLOCK_SHULKER_BOX_OPEN, 0.6f, 1.2f);
    }

    /**
     * No nesting: an expander cannot be stowed inside another.
     */
    @EventHandler(priority = EventPriority.LOW, ignoreCancelled = true)
    public void onClick(InventoryClickEvent event) {
        if (!open.containsKey(event.getWhoClicked().getUniqueId())) {
            return;
        }
        ItemStack moving = event.getCurrentItem() != null && event.getClick().isShiftClick() ? event.getCurrentItem()
                : event.getCursor();
        if (addon.getHoldService().isExpander(moving)) {
            event.setCancelled(true);
        }
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onClose(InventoryCloseEvent event) {
        Integer slot = open.remove(event.getPlayer().getUniqueId());
        if (slot == null || !(event.getPlayer() instanceof Player player)) {
            return;
        }
        ItemStack expander = player.getInventory().getItem(slot);
        if (!addon.getHoldService().isExpander(expander)
                || !(expander.getItemMeta() instanceof BlockStateMeta meta)
                || !(meta.getBlockState() instanceof ShulkerBox box)) {
            // They swapped the item away mid-edit: give the contents back
            event.getInventory().forEach(stack -> {
                if (stack != null) {
                    player.getWorld().dropItem(player.getLocation(), stack);
                }
            });
            return;
        }
        box.getInventory().setContents(event.getInventory().getContents());
        meta.setBlockState(box);
        expander.setItemMeta(meta);
        player.getInventory().setItem(slot, expander);
        player.playSound(player.getLocation(), Sound.BLOCK_SHULKER_BOX_CLOSE, 0.6f, 1.2f);
    }
}
