package world.bentobox.tradewinds.travel;

import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataType;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import world.bentobox.bentobox.api.user.User;
import world.bentobox.tradewinds.TradeWinds;
import world.bentobox.tradewinds.dataobjects.TWPlayerData;

/**
 * The start of every trading career: a boat and one trading bundle (spec §4).
 * Given once, on the player's first spawn. When the spawn point is water the
 * boat is launched and the player seated in it; on land the boat goes in the
 * inventory instead. Repeat visitors to spawn who carry a boat item get
 * auto-launched too - nobody treads water at spawn.
 *
 * @author tastybento
 */
public class StarterKit {

    /** PDC key on boats recording the owning player's UUID (spec §4). */
    public static final NamespacedKey BOAT_OWNER_KEY = NamespacedKey.fromString("tradewinds:owner");

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
            player.getInventory().addItem(tradingBundle());
            if (onWater) {
                launchBoat(player, spawn, Material.OAK_BOAT);
            } else {
                player.getInventory().addItem(new ItemStack(Material.OAK_BOAT));
            }
            // Seed money for the first cargo
            addon.getPlugin().getVault()
                    .ifPresent(vault -> vault.deposit(User.getInstance(player),
                            addon.getSettings().getStartingBalance()));
            User.getInstance(player).sendMessage("tradewinds.starter-kit.given");
            return;
        }
        // Repeat arrival: auto-launch a carried boat so nobody swims at spawn
        if (onWater && player.getVehicle() == null) {
            Material carried = consumeBoatItem(player);
            if (carried != null) {
                launchBoat(player, spawn, carried);
            }
        }
    }

    private ItemStack tradingBundle() {
        ItemStack bundle = new ItemStack(Material.BUNDLE);
        ItemMeta meta = bundle.getItemMeta();
        if (meta != null) {
            meta.displayName(Component.text("Trading Bundle", NamedTextColor.GOLD));
            // A little coal in the bundle: it is hold cargo, so the first
            // island hop can be a warp instead of a long row
            int coal = addon.getSettings().getStarterCoal();
            if (coal > 0 && meta instanceof org.bukkit.inventory.meta.BundleMeta bundleMeta) {
                bundleMeta.setItems(java.util.List.of(new ItemStack(Material.COAL, coal)));
            }
            bundle.setItemMeta(meta);
        }
        return bundle;
    }

    private void launchBoat(Player player, Location spawn, Material boatMaterial) {
        // Boat item materials and boat entity types share names (OAK_BOAT...)
        org.bukkit.entity.EntityType type;
        try {
            type = org.bukkit.entity.EntityType.valueOf(boatMaterial.name());
        } catch (IllegalArgumentException e) {
            type = org.bukkit.entity.EntityType.OAK_BOAT;
        }
        org.bukkit.entity.Entity boat = spawn.getWorld().spawnEntity(spawn, type);
        boat.getPersistentDataContainer().set(BOAT_OWNER_KEY, PersistentDataType.STRING,
                player.getUniqueId().toString());
        boat.setPersistent(true);
        boat.addPassenger(player);
    }

    /**
     * Remove one boat item from the inventory, plain boats preferred over
     * chest boats.
     * @return the removed boat material, or null if none carried
     */
    private Material consumeBoatItem(Player player) {
        Material found = null;
        for (boolean allowChest : new boolean[] { false, true }) {
            for (ItemStack stack : player.getInventory().getContents()) {
                if (stack != null && stack.getType().name().endsWith("_BOAT")
                        && (allowChest || !stack.getType().name().contains("CHEST"))) {
                    found = stack.getType();
                    stack.setAmount(stack.getAmount() - 1);
                    return found;
                }
            }
        }
        return null;
    }
}
