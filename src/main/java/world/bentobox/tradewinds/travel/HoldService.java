package world.bentobox.tradewinds.travel;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

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
 * It is the chest boat's inventory, the contents of cargo-expander shulker
 * boxes stored in that inventory, and up to max-bundles trading bundles in
 * the player's own inventory. Pocket items are invisible to the market.
 * <p>
 * The cargo progression IS this definition: bundle(s) -> chest boat ->
 * expanders in the chest boat.
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
        Map<Material, Integer> result = new LinkedHashMap<>();
        forEachHoldStack(player, stack -> result.merge(stack.getType(), stack.getAmount(), Integer::sum));
        return result;
    }

    /**
     * How many of a material the hold contains.
     */
    public int count(Player player, Material material) {
        return contents(player).getOrDefault(material, 0);
    }

    /**
     * Remove up to {@code amount} of a material from the hold (chest boat
     * first, then expanders, then bundles).
     *
     * @return how many were actually removed
     */
    public int remove(Player player, Material material, int amount) {
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
                if (stack.getType() == material) {
                    int take = Math.min(left[0], stack.getAmount());
                    stack.setAmount(stack.getAmount() - take);
                    if (stack.getAmount() <= 0) {
                        inv.remove(stack);
                    }
                    left[0] -= take;
                } else if (isExpander(stack)) {
                    left[0] -= removeFromShulker(stack, material, left[0]);
                }
            }
        }
        for (ItemStack bundleItem : bundles(player)) {
            if (left[0] <= 0) {
                break;
            }
            left[0] -= removeFromBundle(bundleItem, material, left[0]);
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
        if (player.getVehicle() instanceof ChestBoat boat) {
            ItemStack toAdd = items.clone();
            toAdd.setAmount(remaining);
            Map<Integer, ItemStack> leftover = boat.getInventory().addItem(toAdd);
            remaining = leftover.values().stream().mapToInt(ItemStack::getAmount).sum();
            if (remaining <= 0) {
                return items.getAmount();
            }
            // Try expanders in the boat
            for (ItemStack stack : boat.getInventory().getContents()) {
                if (remaining <= 0) {
                    break;
                }
                if (isExpander(stack)) {
                    remaining -= addToShulker(stack, items, remaining);
                }
            }
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
        return stack != null && stack.getType() == Material.SHULKER_BOX && stack.hasItemMeta()
                && stack.getItemMeta().getPersistentDataContainer().has(
                        org.bukkit.NamespacedKey.fromString("tradewinds:expander"),
                        org.bukkit.persistence.PersistentDataType.STRING);
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

    private void forEachHoldStack(Player player, java.util.function.Consumer<ItemStack> consumer) {
        if (player.getVehicle() instanceof ChestBoat boat) {
            for (ItemStack stack : boat.getInventory().getContents()) {
                if (stack == null || stack.getType().isAir()) {
                    continue;
                }
                if (isExpander(stack)) {
                    shulkerContents(stack).forEach(consumer);
                } else {
                    consumer.accept(stack);
                }
            }
        }
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

    private int removeFromShulker(ItemStack expander, Material material, int amount) {
        if (!(expander.getItemMeta() instanceof BlockStateMeta meta)
                || !(meta.getBlockState() instanceof ShulkerBox box)) {
            return 0;
        }
        int removed = 0;
        for (ItemStack stack : box.getInventory().getContents()) {
            if (removed >= amount) {
                break;
            }
            if (stack != null && stack.getType() == material) {
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

    private int removeFromBundle(ItemStack bundleItem, Material material, int amount) {
        if (!(bundleItem.getItemMeta() instanceof BundleMeta bundle)) {
            return 0;
        }
        int removed = 0;
        List<ItemStack> items = new ArrayList<>(bundle.getItems());
        List<ItemStack> kept = new ArrayList<>();
        for (ItemStack stack : items) {
            if (removed < amount && stack.getType() == material) {
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
