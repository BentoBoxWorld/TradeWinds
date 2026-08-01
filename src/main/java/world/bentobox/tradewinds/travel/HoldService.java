package world.bentobox.tradewinds.travel;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Predicate;

import org.bukkit.Material;
import org.bukkit.block.ShulkerBox;
import org.bukkit.entity.ChestBoat;
import org.bukkit.entity.Player;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.BlockStateMeta;
import org.bukkit.inventory.meta.BundleMeta;

import world.bentobox.tradewinds.TradeWinds;

/**
 * The hold: the ONLY container trade transacts against (spec principle 1).
 * It is what the sailor carries as cargo -
 * <ul>
 * <li>trading bundles (pouches), up to max-bundles,</li>
 * <li>cargo expanders (shulker boxes) - wherever they are carried,</li>
 * <li>and the chest boat's own inventory while riding one.</li>
 * </ul>
 * Loose items in pockets are invisible to the market: goods must be stowed in
 * a pouch, an expander or the boat to be cargo. That keeps the fuel/cargo
 * tension (principle 1) while leaving the hold always openable - a chest boat
 * moored ashore, or sitting as an item in the pack, cannot be filled.
 * <p>
 * The cargo progression: pouch -> more pouches -> expanders -> chest boat.
 *
 * @author tastybento
 */
public class HoldService {

    private final TradeWinds addon;

    public HoldService(TradeWinds addon) {
        this.addon = addon;
    }

    /**
     * Everything sellable in the hold, aggregated by material, in encounter
     * order.
     */
    public Map<Material, Integer> contents(Player player) {
        return contents(player, stack -> true);
    }

    /**
     * Hold contents matching a stack filter (e.g. customs-stamped only).
     */
    public Map<Material, Integer> contents(Player player, Predicate<ItemStack> filter) {
        Map<Material, Integer> result = new LinkedHashMap<>();
        forEachHoldStack(player, stack -> {
            if (filter.test(stack)) {
                result.merge(stack.getType(), stack.getAmount(), Integer::sum);
            }
        });
        return result;
    }

    /**
     * How many of a material the hold contains.
     */
    public int count(Player player, Material material) {
        return contents(player).getOrDefault(material, 0);
    }

    /**
     * How many of a material, counting only stacks passing the filter.
     */
    public int count(Player player, Material material, Predicate<ItemStack> filter) {
        return contents(player, filter).getOrDefault(material, 0);
    }

    /**
     * Approximate free capacity of the hold in items, assuming 64-stacks:
     * empty boat/expander slots count 64, partial stacks their headroom,
     * bundles their remaining weight.
     */
    public int freeSpace(Player player) {
        int free = 0;
        if (player.getVehicle() instanceof ChestBoat boat) {
            free += freeIn(boat.getInventory());
        }
        for (ItemStack expander : expanders(player)) {
            if (expander.getItemMeta() instanceof BlockStateMeta meta
                    && meta.getBlockState() instanceof ShulkerBox box) {
                free += freeIn(box.getInventory());
            }
        }
        for (ItemStack bundleItem : bundles(player)) {
            if (bundleItem.getItemMeta() instanceof BundleMeta bundle) {
                free += Math.max(0, 64 - bundle.getItems().stream().mapToInt(ItemStack::getAmount).sum());
            }
        }
        return free;
    }

    private int freeIn(Inventory inventory) {
        int free = 0;
        for (ItemStack stack : inventory.getStorageContents()) {
            if (stack == null || stack.getType().isAir()) {
                free += 64;
            } else if (stack.getMaxStackSize() >= 64 && !isExpander(stack)) {
                free += stack.getMaxStackSize() - stack.getAmount();
            }
        }
        return free;
    }

    /**
     * Remove up to {@code amount} of a material from the hold (chest boat
     * first, then expanders, then bundles).
     *
     * @return how many were actually removed
     */
    public int remove(Player player, Material material, int amount) {
        return remove(player, material, amount, stack -> true);
    }

    /**
     * Remove up to {@code amount} of a material, taking only stacks that pass
     * the filter (e.g. customs-stamped only).
     *
     * @return how many were actually removed
     */
    public int remove(Player player, Material material, int amount, Predicate<ItemStack> filter) {
        int[] left = { amount };
        if (player.getVehicle() instanceof ChestBoat boat) {
            Inventory inv = boat.getInventory();
            for (ItemStack stack : inv.getContents()) {
                if (left[0] <= 0) {
                    break;
                }
                if (stack == null) {
                    continue;
                }
                if (stack.getType() == material && !isExpander(stack) && filter.test(stack)) {
                    int take = Math.min(left[0], stack.getAmount());
                    stack.setAmount(stack.getAmount() - take);
                    if (stack.getAmount() <= 0) {
                        inv.remove(stack);
                    }
                    left[0] -= take;
                }
            }
        }
        for (ItemStack expander : expanders(player)) {
            if (left[0] <= 0) {
                break;
            }
            left[0] -= removeFromShulker(expander, material, left[0], filter);
        }
        for (ItemStack bundleItem : bundles(player)) {
            if (left[0] <= 0) {
                break;
            }
            left[0] -= removeFromBundle(bundleItem, material, left[0], filter);
        }
        return amount - left[0];
    }

    /**
     * Add items to the hold: chest boat first, then a cargo expander with
     * room, then a bundle with room.
     *
     * @return how many items were actually added (0 if no space)
     */
    public int add(Player player, ItemStack items) {
        int remaining = items.getAmount();
        // Expanders first: they are the purpose-built cargo space
        for (ItemStack expander : expanders(player)) {
            if (remaining <= 0) {
                break;
            }
            remaining -= addToShulker(expander, items, remaining);
        }
        if (remaining > 0 && player.getVehicle() instanceof ChestBoat boat) {
            ItemStack toAdd = items.clone();
            toAdd.setAmount(remaining);
            Map<Integer, ItemStack> leftover = boat.getInventory().addItem(toAdd);
            remaining = leftover.values().stream().mapToInt(ItemStack::getAmount).sum();
        }
        // Bundles: only simple stackables, vanilla capacity 64
        for (ItemStack bundleItem : bundles(player)) {
            if (remaining <= 0) {
                break;
            }
            remaining -= addToBundle(bundleItem, items, remaining);
        }
        return items.getAmount() - remaining;
    }

    /**
     * Is this item a TradeWinds cargo expander (lore-renamed shulker box)?
     */
    public boolean isExpander(ItemStack stack) {
        // Any shulker box colour: expanders ship white to tell them apart from
        // vanilla purple, but older purple ones stay valid
        return stack != null && stack.getType().name().endsWith("SHULKER_BOX") && stack.hasItemMeta()
                && stack.getItemMeta().getPersistentDataContainer().has(
                        org.bukkit.NamespacedKey.fromString("tradewinds:expander"),
                        org.bukkit.persistence.PersistentDataType.STRING);
    }

    /**
     * How many trading pouches the player carries, counting past the cap - the
     * shipwright needs the true number to refuse selling more.
     */
    public int pouchCount(Player player) {
        int count = 0;
        for (ItemStack stack : player.getInventory().getContents()) {
            if (stack != null && stack.getItemMeta() instanceof BundleMeta) {
                count += stack.getAmount();
            }
        }
        return count;
    }

    /**
     * The player's trading bundles, capped at the configured maximum - a
     * fourth bundle is just a bag, not hold space.
     */
    private List<ItemStack> bundles(Player player) {
        List<ItemStack> result = new ArrayList<>();
        int max = addon.getSettings().getMaxBundles();
        for (ItemStack stack : player.getInventory().getContents()) {
            if (result.size() >= max) {
                break;
            }
            if (stack != null && stack.getItemMeta() instanceof BundleMeta) {
                result.add(stack);
            }
        }
        return result;
    }

    /**
     * Every cargo expander the sailor carries: in the pack, or stowed in the
     * chest boat they are riding.
     */
    public List<ItemStack> expanders(Player player) {
        List<ItemStack> result = new ArrayList<>();
        for (ItemStack stack : player.getInventory().getContents()) {
            if (isExpander(stack)) {
                result.add(stack);
            }
        }
        if (player.getVehicle() instanceof ChestBoat boat) {
            for (ItemStack stack : boat.getInventory().getContents()) {
                if (isExpander(stack)) {
                    result.add(stack);
                }
            }
        }
        return result;
    }

    private void forEachHoldStack(Player player, java.util.function.Consumer<ItemStack> consumer) {
        if (player.getVehicle() instanceof ChestBoat boat) {
            for (ItemStack stack : boat.getInventory().getContents()) {
                if (stack == null || stack.getType().isAir() || isExpander(stack)) {
                    continue;
                }
                consumer.accept(stack);
            }
        }
        expanders(player).forEach(expander -> shulkerContents(expander).forEach(consumer));
        for (ItemStack bundleItem : bundles(player)) {
            if (bundleItem.getItemMeta() instanceof BundleMeta bundle) {
                bundle.getItems().stream().filter(i -> i != null && !i.getType().isAir()).forEach(consumer);
            }
        }
    }

    private List<ItemStack> shulkerContents(ItemStack expander) {
        if (expander.getItemMeta() instanceof BlockStateMeta meta && meta.getBlockState() instanceof ShulkerBox box) {
            List<ItemStack> list = new ArrayList<>();
            for (ItemStack stack : box.getInventory().getContents()) {
                if (stack != null && !stack.getType().isAir()) {
                    list.add(stack);
                }
            }
            return list;
        }
        return List.of();
    }

    private int removeFromShulker(ItemStack expander, Material material, int amount, Predicate<ItemStack> filter) {
        if (!(expander.getItemMeta() instanceof BlockStateMeta meta)
                || !(meta.getBlockState() instanceof ShulkerBox box)) {
            return 0;
        }
        int removed = 0;
        for (ItemStack stack : box.getInventory().getContents()) {
            if (removed >= amount) {
                break;
            }
            if (stack != null && stack.getType() == material && filter.test(stack)) {
                int take = Math.min(amount - removed, stack.getAmount());
                stack.setAmount(stack.getAmount() - take);
                if (stack.getAmount() <= 0) {
                    box.getInventory().remove(stack);
                }
                removed += take;
            }
        }
        if (removed > 0) {
            meta.setBlockState(box);
            expander.setItemMeta(meta);
        }
        return removed;
    }

    private int addToShulker(ItemStack expander, ItemStack items, int amount) {
        if (!(expander.getItemMeta() instanceof BlockStateMeta meta)
                || !(meta.getBlockState() instanceof ShulkerBox box)) {
            return 0;
        }
        ItemStack toAdd = items.clone();
        toAdd.setAmount(amount);
        Map<Integer, ItemStack> leftover = box.getInventory().addItem(toAdd);
        int added = amount - leftover.values().stream().mapToInt(ItemStack::getAmount).sum();
        if (added > 0) {
            meta.setBlockState(box);
            expander.setItemMeta(meta);
        }
        return added;
    }

    private int removeFromBundle(ItemStack bundleItem, Material material, int amount, Predicate<ItemStack> filter) {
        if (!(bundleItem.getItemMeta() instanceof BundleMeta bundle)) {
            return 0;
        }
        int removed = 0;
        List<ItemStack> items = new ArrayList<>(bundle.getItems());
        List<ItemStack> kept = new ArrayList<>();
        for (ItemStack stack : items) {
            if (removed < amount && stack.getType() == material && filter.test(stack)) {
                int take = Math.min(amount - removed, stack.getAmount());
                removed += take;
                if (stack.getAmount() > take) {
                    ItemStack rest = stack.clone();
                    rest.setAmount(stack.getAmount() - take);
                    kept.add(rest);
                }
            } else {
                kept.add(stack);
            }
        }
        if (removed > 0) {
            bundle.setItems(kept);
            bundleItem.setItemMeta(bundle);
        }
        return removed;
    }

    private int addToBundle(ItemStack bundleItem, ItemStack items, int amount) {
        if (!(bundleItem.getItemMeta() instanceof BundleMeta bundle) || items.getMaxStackSize() < 64) {
            // Vanilla bundle weight rules for oddball items are not worth
            // emulating - only plain stackables ride in bundles
            return 0;
        }
        int used = bundle.getItems().stream().mapToInt(ItemStack::getAmount).sum();
        int space = 64 - used;
        int toAdd = Math.min(space, amount);
        if (toAdd <= 0) {
            return 0;
        }
        List<ItemStack> newItems = new ArrayList<>(bundle.getItems());
        ItemStack adding = items.clone();
        adding.setAmount(toAdd);
        newItems.add(adding);
        bundle.setItems(newItems);
        bundleItem.setItemMeta(bundle);
        return toAdd;
    }
}
