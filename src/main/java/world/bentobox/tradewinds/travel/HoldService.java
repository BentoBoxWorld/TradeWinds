package world.bentobox.tradewinds.travel;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;

import world.bentobox.tradewinds.TradeWinds;
import world.bentobox.tradewinds.dataobjects.BoatHold;

/**
 * The hold: the ONLY store trade transacts against (spec principle 1) - and it
 * is the BOAT's, not the player's (ruled 2026-08-02). Every operation here
 * resolves against the player's <b>active boat</b> record; with no boat there
 * is no hold at all, and a two-slot raft carries two slots however rich its
 * captain used to be.
 * <p>
 * Contents are virtual: a list of stacks in the database, one per occupied
 * slot, so no inventory trick can extract them. Cargo carries its full identity
 * - a worn bow, a mint bow and a Silk Touch pick are three different goods -
 * with the slot arithmetic in {@link CargoStore}. Fuel stays material→amount
 * because fuel is genuinely fungible: a lump of coal is a lump of coal.
 * <p>
 * One-way rule: anything non-container can be added; cargo leaves only by being
 * sold or destroyed. Fuel is exempt - it moves freely both ways, and fuel-valued
 * cargo can be shifted into the fuel row.
 *
 * @author tastybento
 */
public class HoldService {

    /** The most cargo slots any boat provides (the GUI renders this many). */
    public static final int MAX_CARGO_SLOTS = 21;
    /** Dedicated fuel slots - fuel never competes with cargo. */
    public static final int FUEL_SLOTS = 7;
    /** Cargo slots inside one installed expander (net +20: it occupies one). */
    public static final int EXPANDER_SLOTS = 21;

    /**
     * Container items may never enter the hold - they would nest capacity.
     * Boats are refused separately (a vessel is a key, not cargo).
     */
    private static final Set<Material> CONTAINERS = Set.of(Material.BUNDLE, Material.CHEST,
            Material.TRAPPED_CHEST, Material.BARREL, Material.ENDER_CHEST, Material.HOPPER, Material.DROPPER,
            Material.DISPENSER, Material.FURNACE, Material.BLAST_FURNACE, Material.SMOKER,
            Material.CHISELED_BOOKSHELF, Material.DECORATED_POT);

    private final TradeWinds addon;

    public HoldService(TradeWinds addon) {
        this.addon = addon;
    }

    /**
     * The player's active boat record - their hold.
     */
    public Optional<BoatHold> active(UUID playerId) {
        return addon.getHoldManager().activeBoat(playerId);
    }

    // ------------------------------------------------------------------ boat

    /**
     * The one boat this player owns, or null for none.
     */
    public Material boat(UUID playerId) {
        return active(playerId).map(h -> Material.matchMaterial(h.getMaterial())).orElse(null);
    }

    public Material boat(Player player) {
        return boat(player.getUniqueId());
    }

    /**
     * Cargo capacity in slots: the active boat's rank size, or 0 with none.
     */
    public int capacitySlots(UUID playerId) {
        return addon.getBoatRanks().slots(boat(playerId));
    }

    public int capacitySlots(Player player) {
        return capacitySlots(player.getUniqueId());
    }

    // ----------------------------------------------------------------- cargo

    /**
     * The main hold's cargo stacks, in slot order. Live references: mutate only
     * through this service so the record gets saved.
     */
    public static List<ItemStack> cargoOf(BoatHold hold) {
        CargoStore.compact(hold.getCargo());
        return hold.getCargo();
    }

    public List<ItemStack> cargo(UUID playerId) {
        return active(playerId).map(HoldService::cargoOf).orElseGet(ArrayList::new);
    }

    public List<ItemStack> cargo(Player player) {
        return cargo(player.getUniqueId());
    }

    /**
     * Everything aboard for trade purposes: main cargo plus every expander, as
     * distinct stacks. Two enchanted swords with different enchantments appear
     * separately, because they are worth different money.
     */
    public List<ItemStack> tradeCargo(Player player) {
        List<ItemStack> all = new ArrayList<>(cargo(player.getUniqueId()));
        active(player.getUniqueId()).ifPresent(hold -> hold.getExpanders().forEach(expander -> {
            CargoStore.compact(expander);
            all.addAll(expander);
        }));
        return all;
    }

    /**
     * How many matching items the whole ship carries, expanders included.
     */
    public int count(Player player, ItemStack like) {
        return active(player.getUniqueId()).map(hold -> {
            int total = CargoStore.count(hold.getCargo(), like);
            for (List<ItemStack> expander : hold.getExpanders()) {
                total += CargoStore.count(expander, like);
            }
            return total;
        }).orElse(0);
    }

    /**
     * Material convenience: how many plain items of this type are aboard.
     */
    public int count(Player player, Material material) {
        return count(player, new ItemStack(material));
    }

    /**
     * Cargo slots in use: one per stack, plus one per installed expander.
     */
    public int slotsUsed(UUID playerId) {
        return active(playerId).map(HoldService::slotsUsedIn).orElse(0);
    }

    static int slotsUsedIn(BoatHold hold) {
        CargoStore.compact(hold.getCargo());
        return hold.getExpanders().size() + hold.getCargo().size();
    }

    public int slotsFree(UUID playerId) {
        return Math.max(0, capacitySlots(playerId) - slotsUsed(playerId));
    }

    /**
     * How many MORE of a material fit anywhere aboard: the main hold's free
     * slots and headroom, plus every installed expander's.
     */
    public int capacityFor(UUID playerId, ItemStack like) {
        if (like == null || refuses(like.getType())) {
            return 0;
        }
        return active(playerId).map(hold -> {
            int total = mainCapacityFor(hold, capacitySlots(playerId), like);
            for (List<ItemStack> expander : hold.getExpanders()) {
                total += CargoStore.capacityFor(expander, EXPANDER_SLOTS, like);
            }
            return total;
        }).orElse(0);
    }

    public int capacityFor(UUID playerId, Material material) {
        return capacityFor(playerId, new ItemStack(material));
    }

    /**
     * Room in the main hold. The expanders each occupy a cargo slot, so the
     * budget for loose stacks is the boat's capacity less the expander count.
     */
    private static int mainCapacityFor(BoatHold hold, int capacitySlots, ItemStack like) {
        int budget = Math.max(0, capacitySlots - hold.getExpanders().size());
        return CargoStore.capacityFor(hold.getCargo(), budget, like);
    }

    /**
     * Whether the hold refuses this material outright: containers would nest
     * capacity, and a vessel is a key, not cargo.
     */
    public boolean refuses(Material material) {
        return material == null || material.isAir() || CONTAINERS.contains(material)
                || material.name().endsWith("SHULKER_BOX") || BoatRanks.isBoatItem(material);
    }

    /**
     * Add cargo to the player's boat, one-way, capacity- and
     * container-checked: the main hold fills first, then the expanders.
     *
     * @return how many were actually added
     */
    public int add(Player player, ItemStack like, int amount) {
        return active(player.getUniqueId())
                .map(hold -> addTo(hold, capacitySlots(player.getUniqueId()), like, amount)).orElse(0);
    }

    public int add(Player player, Material material, int amount) {
        return add(player, new ItemStack(material), amount);
    }

    /**
     * Add cargo to a specific boat - used when merging a salvaged hold.
     *
     * @return how many were actually added
     */
    public int addTo(BoatHold hold, int capacitySlots, ItemStack like, int amount) {
        if (like == null || refuses(like.getType()) || amount <= 0) {
            return 0;
        }
        int budget = Math.max(0, capacitySlots - hold.getExpanders().size());
        int left = amount - CargoStore.added(hold.getCargo(), budget, like, amount);
        for (List<ItemStack> expander : hold.getExpanders()) {
            if (left <= 0) {
                break;
            }
            left -= CargoStore.added(expander, EXPANDER_SLOTS, like, left);
        }
        int added = amount - left;
        if (added > 0) {
            addon.getHoldManager().save(hold);
        }
        return added;
    }

    public int addTo(BoatHold hold, int capacitySlots, Material material, int amount) {
        return addTo(hold, capacitySlots, new ItemStack(material), amount);
    }

    /**
     * Remove cargo - ONLY the market (sell) and the TNT slot (destroy) may
     * call this; there is no path from here to a player inventory.
     *
     * @return how many were actually removed
     */
    public int remove(Player player, ItemStack like, int amount) {
        return active(player.getUniqueId()).map(hold -> removeFrom(hold, like, amount)).orElse(0);
    }

    public int remove(Player player, Material material, int amount) {
        return remove(player, new ItemStack(material), amount);
    }

    /**
     * Remove cargo from a specific boat.
     */
    public int removeFrom(BoatHold hold, ItemStack like, int amount) {
        int left = amount - CargoStore.removed(hold.getCargo(), like, amount);
        for (List<ItemStack> expander : hold.getExpanders()) {
            if (left <= 0) {
                break;
            }
            left -= CargoStore.removed(expander, like, left);
        }
        int taken = amount - left;
        if (taken > 0) {
            addon.getHoldManager().save(hold);
        }
        return taken;
    }

    public int removeFrom(BoatHold hold, Material material, int amount) {
        return removeFrom(hold, new ItemStack(material), amount);
    }

    /**
     * Move fuel-valued CARGO into the fuel row (e.g. coal bought at market) -
     * a sanctioned third exit from the cargo slots, into the tank.
     *
     * @return how many moved
     */
    public int moveCargoToFuel(Player player, Material material, int amount) {
        if (addon.getFuelService().fuelValue(material) <= 0 || amount <= 0) {
            return 0;
        }
        UUID playerId = player.getUniqueId();
        Optional<BoatHold> hold = active(playerId);
        if (hold.isEmpty()) {
            return 0;
        }
        int fit = Math.min(amount, fuelCapacityFor(playerId, material));
        if (fit <= 0) {
            return 0;
        }
        int taken = remove(player, new ItemStack(material), fit);
        if (taken <= 0) {
            return 0;
        }
        hold.get().getFuel().merge(material.name(), taken, Integer::sum);
        addon.getHoldManager().save(hold.get());
        return taken;
    }

    // ------------------------------------------------------------- expanders

    public int expanderCount(UUID playerId) {
        return active(playerId).map(h -> h.getExpanders().size()).orElse(0);
    }

    /**
     * Whether one more expander can be installed: only in the top boat, and
     * only with a free cargo slot for it to occupy.
     */
    public boolean canInstallExpander(UUID playerId) {
        return boat(playerId) == Material.PALE_OAK_CHEST_BOAT && slotsFree(playerId) >= 1;
    }

    public boolean installExpander(UUID playerId) {
        if (!canInstallExpander(playerId)) {
            return false;
        }
        return active(playerId).map(hold -> {
            hold.getExpanders().add(new ArrayList<>());
            addon.getHoldManager().save(hold);
            return true;
        }).orElse(false);
    }

    /**
     * Whether the expanders' nested panels may be opened: only while the top
     * boat carries them. Anywhere else they ride along inert, contents safe.
     */
    public boolean expandersOpenable(UUID playerId) {
        return boat(playerId) == Material.PALE_OAK_CHEST_BOAT;
    }

    /**
     * One expander's cargo stacks, in slot order.
     */
    public List<ItemStack> expanderCargo(UUID playerId, int index) {
        return active(playerId).map(hold -> {
            List<List<ItemStack>> expanders = hold.getExpanders();
            if (index < 0 || index >= expanders.size()) {
                return new ArrayList<ItemStack>();
            }
            CargoStore.compact(expanders.get(index));
            return new ArrayList<>(expanders.get(index));
        }).orElseGet(ArrayList::new);
    }

    public int addToExpander(Player player, int index, ItemStack like, int amount) {
        if (like == null || refuses(like.getType()) || amount <= 0) {
            return 0;
        }
        return active(player.getUniqueId()).map(hold -> {
            List<List<ItemStack>> expanders = hold.getExpanders();
            if (index < 0 || index >= expanders.size()) {
                return 0;
            }
            int fit = CargoStore.added(expanders.get(index), EXPANDER_SLOTS, like, amount);
            if (fit > 0) {
                addon.getHoldManager().save(hold);
            }
            return fit;
        }).orElse(0);
    }

    public int addToExpander(Player player, int index, Material material, int amount) {
        return addToExpander(player, index, new ItemStack(material), amount);
    }

    public int removeFromExpander(UUID playerId, int index, ItemStack like, int amount) {
        return active(playerId).map(hold -> {
            List<List<ItemStack>> expanders = hold.getExpanders();
            if (index < 0 || index >= expanders.size()) {
                return 0;
            }
            int taken = CargoStore.removed(expanders.get(index), like, amount);
            if (taken > 0) {
                addon.getHoldManager().save(hold);
            }
            return taken;
        }).orElse(0);
    }

    public int removeFromExpander(UUID playerId, int index, Material material, int amount) {
        return removeFromExpander(playerId, index, new ItemStack(material), amount);
    }

    /**
     * Destroy an EMPTY expander (the TNT refuses a loaded one).
     */
    public boolean destroyExpander(UUID playerId, int index) {
        return active(playerId).map(hold -> {
            List<List<ItemStack>> expanders = hold.getExpanders();
            if (index < 0 || index >= expanders.size()) {
                return false;
            }
            CargoStore.compact(expanders.get(index));
            if (!expanders.get(index).isEmpty()) {
                return false;
            }
            expanders.remove(index);
            addon.getHoldManager().save(hold);
            return true;
        }).orElse(false);
    }

    // ------------------------------------------------------------------ fuel

    public Map<Material, Integer> fuelContents(UUID playerId) {
        return active(playerId).map(HoldService::fuelOf).orElseGet(LinkedHashMap::new);
    }

    public static Map<Material, Integer> fuelOf(BoatHold hold) {
        Map<Material, Integer> result = new LinkedHashMap<>();
        hold.getFuel().forEach((name, amount) -> {
            Material material = Material.matchMaterial(name);
            if (material != null && amount != null && amount > 0) {
                result.put(material, amount);
            }
        });
        return result;
    }

    /**
     * Slots one amount of a material occupies, consolidated. Fuel only - cargo
     * counts slots by stack now (see {@link CargoStore}).
     */
    public static int slotsFor(Material material, int amount) {
        int max = Math.max(1, material.getMaxStackSize());
        return (amount + max - 1) / max;
    }

    public int fuelSlotsUsed(UUID playerId) {
        int used = 0;
        for (Map.Entry<Material, Integer> entry : fuelContents(playerId).entrySet()) {
            used += slotsFor(entry.getKey(), entry.getValue());
        }
        return used;
    }

    public int fuelCapacityFor(UUID playerId, Material material) {
        if (addon.getFuelService().fuelValue(material) <= 0 || active(playerId).isEmpty()) {
            return 0;
        }
        int max = Math.max(1, material.getMaxStackSize());
        int current = active(playerId).map(h -> h.getFuel().getOrDefault(material.name(), 0)).orElse(0);
        int headroom = current == 0 ? 0 : slotsFor(material, current) * max - current;
        int freeSlots = Math.max(0, FUEL_SLOTS - fuelSlotsUsed(playerId));
        return freeSlots * max + headroom;
    }

    public int addFuel(Player player, Material material, int amount) {
        UUID playerId = player.getUniqueId();
        int accepted = Math.min(amount, fuelCapacityFor(playerId, material));
        if (accepted <= 0) {
            return 0;
        }
        return active(playerId).map(hold -> {
            hold.getFuel().merge(material.name(), accepted, Integer::sum);
            addon.getHoldManager().save(hold);
            return accepted;
        }).orElse(0);
    }

    /**
     * Add fuel to a specific boat - used when merging a salvaged hold.
     */
    public int addFuelTo(BoatHold hold, Material material, int amount) {
        if (addon.getFuelService().fuelValue(material) <= 0 || amount <= 0) {
            return 0;
        }
        int max = Math.max(1, material.getMaxStackSize());
        int used = 0;
        for (Map.Entry<Material, Integer> entry : fuelOf(hold).entrySet()) {
            used += slotsFor(entry.getKey(), entry.getValue());
        }
        int current = hold.getFuel().getOrDefault(material.name(), 0);
        int headroom = current == 0 ? 0 : slotsFor(material, current) * max - current;
        int fit = Math.min(amount, Math.max(0, FUEL_SLOTS - used) * max + headroom);
        if (fit <= 0) {
            return 0;
        }
        hold.getFuel().merge(material.name(), fit, Integer::sum);
        addon.getHoldManager().save(hold);
        return fit;
    }

    /**
     * Drain a fuel entry. Fuel is material-keyed and fungible, so it keeps the
     * simple map arithmetic that cargo has outgrown.
     */
    private static int drain(Map<String, Integer> store, Material material, int amount) {
        int current = store.getOrDefault(material.name(), 0);
        int taken = Math.min(current, amount);
        if (taken <= 0) {
            return 0;
        }
        if (taken == current) {
            store.remove(material.name());
        } else {
            store.put(material.name(), current - taken);
        }
        return taken;
    }

    /**
     * Remove fuel - unlike cargo, fuel moves freely back out.
     */
    public int removeFuel(UUID playerId, Material material, int amount) {
        return active(playerId).map(hold -> {
            int taken = drain(hold.getFuel(), material, amount);
            if (taken > 0) {
                addon.getHoldManager().save(hold);
            }
            return taken;
        }).orElse(0);
    }
}
