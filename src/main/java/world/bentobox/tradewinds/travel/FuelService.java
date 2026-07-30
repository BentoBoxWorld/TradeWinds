package world.bentobox.tradewinds.travel;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;

import org.bukkit.Material;
import org.bukkit.entity.ChestBoat;
import org.bukkit.entity.Player;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;

import world.bentobox.tradewinds.TradeWinds;

/**
 * Warp fuel accounting. Fuel lives ONLY in the hold - the chest boat's
 * inventory plus trading bundles carried by the player (spec principle 1:
 * nothing gameplay-relevant may bypass the hold). Plain inventory items in the
 * player's pockets do not count.
 * <p>
 * Stage 3 hold definition: chest-boat inventory + bundle contents. Stage 4
 * extends the same accessor to cargo expanders.
 *
 * @author tastybento
 */
public class FuelService {

    private final TradeWinds addon;

    public FuelService(TradeWinds addon) {
        this.addon = addon;
    }

    /**
     * One consumable stack found in the hold, with a handle to remove from the
     * right container.
     */
    private record FuelStack(ItemStack stack, double unitValue, Runnable shrinkOne) {
    }

    /**
     * The fuel unit value of a material, or 0 if it is not fuel.
     */
    public double fuelValue(Material material) {
        return addon.getSettings().getFuelValues().getOrDefault(material.name(), 0.0);
    }

    /**
     * Total fuel units aboard.
     *
     * @param player the (boated) player
     * @return fuel units in the hold
     */
    public double holdFuel(Player player) {
        return holdStacks(player).stream().mapToDouble(f -> f.unitValue() * f.stack().getAmount()).sum();
    }

    /**
     * Consume at least {@code units} of fuel from the hold, cheapest items
     * first. Overshoot is burned - warping is not a precise science. Lava
     * buckets leave their empty bucket behind.
     *
     * @param player the player
     * @param units fuel units required
     * @return true if consumed; false (nothing removed) if the hold has too little
     */
    public boolean consume(Player player, double units) {
        if (holdFuel(player) < units) {
            return false;
        }
        double remaining = units;
        // Cheapest first: burn wood before coal before lava
        List<FuelStack> stacks = holdStacks(player);
        stacks.sort(Comparator.comparingDouble(FuelStack::unitValue));
        for (FuelStack fuel : stacks) {
            while (remaining > 0 && fuel.stack().getAmount() > 0) {
                fuel.shrinkOne().run();
                remaining -= fuel.unitValue();
            }
            if (remaining <= 0) {
                break;
            }
        }
        return true;
    }

    /**
     * Every fuel stack in the hold: chest-boat inventory plus bundles in the
     * player's inventory.
     */
    private List<FuelStack> holdStacks(Player player) {
        List<FuelStack> result = new ArrayList<>();
        if (player.getVehicle() instanceof ChestBoat boat) {
            Inventory inv = boat.getInventory();
            for (ItemStack stack : inv.getContents()) {
                addIfFuel(result, stack, () -> shrinkInInventory(inv, stack));
            }
        }
        // Bundles anywhere in the player inventory count as hold space
        for (ItemStack carried : player.getInventory().getContents()) {
            if (carried != null && carried.getItemMeta() instanceof org.bukkit.inventory.meta.BundleMeta bundle) {
                for (ItemStack inside : bundle.getItems()) {
                    addIfFuel(result, inside, () -> shrinkInBundle(carried, inside));
                }
            }
        }
        return result;
    }

    private void addIfFuel(List<FuelStack> result, ItemStack stack, Runnable shrinkOne) {
        if (stack == null || stack.getType().isAir()) {
            return;
        }
        double value = fuelValue(stack.getType());
        if (value > 0) {
            result.add(new FuelStack(stack, value, shrinkOne));
        }
    }

    private void shrinkInInventory(Inventory inv, ItemStack stack) {
        if (stack.getType() == Material.LAVA_BUCKET) {
            stack.setType(Material.BUCKET);
        } else {
            stack.setAmount(stack.getAmount() - 1);
            if (stack.getAmount() <= 0) {
                inv.remove(stack);
            }
        }
    }

    private void shrinkInBundle(ItemStack bundleItem, ItemStack inside) {
        if (!(bundleItem.getItemMeta() instanceof org.bukkit.inventory.meta.BundleMeta bundle)) {
            return;
        }
        List<ItemStack> items = new ArrayList<>(bundle.getItems());
        for (ItemStack item : items) {
            if (item.equals(inside) || item.isSimilar(inside)) {
                if (item.getType() == Material.LAVA_BUCKET) {
                    item.setType(Material.BUCKET);
                    inside.setType(Material.BUCKET);
                } else {
                    item.setAmount(item.getAmount() - 1);
                    inside.setAmount(Math.max(0, inside.getAmount() - 1));
                    if (item.getAmount() <= 0) {
                        items.remove(item);
                    }
                }
                break;
            }
        }
        bundle.setItems(items);
        bundleItem.setItemMeta(bundle);
    }
}
