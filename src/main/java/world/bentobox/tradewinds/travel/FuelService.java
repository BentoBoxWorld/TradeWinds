package world.bentobox.tradewinds.travel;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;

import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;

import world.bentobox.tradewinds.TradeWinds;

/**
 * Warp fuel accounting. Fuel lives ONLY in the hold's seven fuel slots (spec
 * principle 1: nothing gameplay-relevant may bypass the hold) - the virtual
 * record, not any real container. Pocket items never count.
 *
 * @author tastybento
 */
public class FuelService {

    private final TradeWinds addon;

    public FuelService(TradeWinds addon) {
        this.addon = addon;
    }

    /**
     * The fuel unit value of a material, or 0 if it is not fuel.
     */
    public double fuelValue(Material material) {
        return addon.getSettings().getFuelValues().getOrDefault(material.name(), 0.0);
    }

    /**
     * Total fuel units aboard one boat record.
     */
    public double unitsOf(world.bentobox.tradewinds.dataobjects.BoatHold hold) {
        return HoldService.fuelOf(hold).entrySet().stream()
                .mapToDouble(e -> fuelValue(e.getKey()) * e.getValue()).sum();
    }

    /**
     * Total fuel units aboard.
     *
     * @param player the player
     * @return fuel units in the hold's fuel slots
     */
    public double holdFuel(Player player) {
        return addon.getHoldService().fuelContents(player.getUniqueId()).entrySet().stream()
                .mapToDouble(e -> fuelValue(e.getKey()) * e.getValue()).sum();
    }

    /**
     * Consume at least {@code units} of fuel, cheapest items first. Overshoot
     * is burned - warping is not a precise science. Lava buckets leave their
     * empty bucket in the player's pack (an empty bucket is a tool, not
     * cargo).
     *
     * @param player the player
     * @param units fuel units required
     * @return true if consumed; false (nothing removed) if too little aboard
     */
    public boolean consume(Player player, double units) {
        if (holdFuel(player) < units) {
            return false;
        }
        HoldService holdService = addon.getHoldService();
        double remaining = units;
        // Cheapest first: burn wood before coal before lava
        List<Map.Entry<Material, Integer>> stacks = new ArrayList<>(
                holdService.fuelContents(player.getUniqueId()).entrySet());
        stacks.sort(Comparator.comparingDouble(e -> fuelValue(e.getKey())));
        for (Map.Entry<Material, Integer> entry : stacks) {
            if (remaining <= 0) {
                break;
            }
            double value = fuelValue(entry.getKey());
            int needed = (int) Math.ceil(remaining / value);
            int burned = holdService.removeFuel(player.getUniqueId(), entry.getKey(),
                    Math.min(needed, entry.getValue()));
            remaining -= burned * value;
            if (entry.getKey() == Material.LAVA_BUCKET && burned > 0) {
                ItemStack buckets = new ItemStack(Material.BUCKET, burned);
                player.getInventory().addItem(buckets).values()
                        .forEach(left -> player.getWorld().dropItem(player.getLocation(), left));
            }
        }
        return true;
    }
}
