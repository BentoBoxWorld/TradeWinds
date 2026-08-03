package world.bentobox.tradewinds.travel;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

import org.bukkit.Material;
import org.bukkit.entity.Player;

import world.bentobox.tradewinds.TradeWinds;
import world.bentobox.tradewinds.dataobjects.BoatHold;

/**
 * The hold: the ONLY store trade transacts against (spec principle 1) - and it
 * is the BOAT's, not the player's (ruled 2026-08-02). Every operation here
 * resolves against the player's <b>active boat</b> record; with no boat there
 * is no hold at all, and a two-slot raft carries two slots however rich its
 * captain used to be.
 * <p>
 * Contents are virtual: material → amount in the database, auto-consolidated,
 * so no inventory trick can extract them. One-way rule: anything non-container
 * can be added; cargo leaves only by being sold or destroyed. Fuel is exempt -
 * it moves freely both ways, and fuel-valued cargo can be shifted into the
 * fuel row.
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
     * Cargo contents of a boat, materials in display order.
     */
    public static Map<Material, Integer> contentsOf(BoatHold hold) {
        Map<Material, Integer> result = new LinkedHashMap<>();
        hold.getContents().forEach((name, amount) -> {
            Material material = Material.matchMaterial(name);
            if (material != null && amount != null && amount > 0) {
                result.put(material, amount);
            }
        });
        return result;
    }

    public Map<Material, Integer> contents(Player player) {
        return contents(player.getUniqueId());
    }

    public Map<Material, Integer> contents(UUID playerId) {
        return active(playerId).map(HoldService::contentsOf).orElseGet(LinkedHashMap::new);
    }

    /**
     * How many of one material the whole ship carries, expanders included.
     */
    public int count(Player player, Material material) {
        return active(player.getUniqueId()).map(hold -> {
            int total = hold.getContents().getOrDefault(material.name(), 0);
            for (Map<String, Integer> expander : hold.getExpanders()) {
                total += expander.getOrDefault(material.name(), 0);
            }
            return total;
        }).orElse(0);
    }

    /**
     * Everything aboard for trade purposes: main cargo plus every expander.
     */
    public Map<Material, Integer> tradeContents(Player player) {
        Map<Material, Integer> result = contents(player.getUniqueId());
        active(player.getUniqueId()).ifPresent(hold -> hold.getExpanders().forEach(expander ->
                expander.forEach((name, amount) -> {
                    Material material = Material.matchMaterial(name);
                    if (material != null && amount != null && amount > 0) {
                        result.merge(material, amount, Integer::sum);
                    }
                })));
        return result;
    }

    /**
     * Slots one amount of a material occupies, consolidated.
     */
    public static int slotsFor(Material material, int amount) {
        int max = Math.max(1, material.getMaxStackSize());
        return (amount + max - 1) / max;
    }

    /**
     * Cargo slots in use: consolidated stacks plus one per installed expander.
     */
    public int slotsUsed(UUID playerId) {
        return active(playerId).map(HoldService::slotsUsedIn).orElse(0);
    }

    static int slotsUsedIn(BoatHold hold) {
        int used = hold.getExpanders().size();
        for (Map.Entry<String, Integer> entry : hold.getContents().entrySet()) {
            Material material = Material.matchMaterial(entry.getKey());
            if (material != null && entry.getValue() != null && entry.getValue() > 0) {
                used += slotsFor(material, entry.getValue());
            }
        }
        return used;
    }

    public int slotsFree(UUID playerId) {
        return Math.max(0, capacitySlots(playerId) - slotsUsed(playerId));
    }

    /**
     * How many MORE of a material fit anywhere aboard: the main hold's free
     * slots and headroom, plus every installed expander's.
     */
    public int capacityFor(UUID playerId, Material material) {
        if (refuses(material)) {
            return 0;
        }
        return active(playerId).map(hold -> {
            int total = mainCapacityFor(hold, capacitySlots(playerId), material);
            for (Map<String, Integer> expander : hold.getExpanders()) {
                total += storeCapacityFor(expander, EXPANDER_SLOTS, material);
            }
            return total;
        }).orElse(0);
    }

    private static int mainCapacityFor(BoatHold hold, int capacitySlots, Material material) {
        int max = Math.max(1, material.getMaxStackSize());
        int current = hold.getContents().getOrDefault(material.name(), 0);
        int headroom = current == 0 ? 0 : slotsFor(material, current) * max - current;
        int free = Math.max(0, capacitySlots - slotsUsedIn(hold));
        return free * max + headroom;
    }

    /**
     * Room in one bounded store (an expander's contents map).
     */
    private static int storeCapacityFor(Map<String, Integer> store, int slotBudget, Material material) {
        int used = 0;
        for (Map.Entry<String, Integer> entry : store.entrySet()) {
            Material m = Material.matchMaterial(entry.getKey());
            if (m != null && entry.getValue() != null && entry.getValue() > 0) {
                used += slotsFor(m, entry.getValue());
            }
        }
        int free = Math.max(0, slotBudget - used);
        int max = Math.max(1, material.getMaxStackSize());
        int current = store.getOrDefault(material.name(), 0);
        int headroom = current == 0 ? 0 : slotsFor(material, current) * max - current;
        return free * max + headroom;
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
    public int add(Player player, Material material, int amount) {
        return active(player.getUniqueId())
                .map(hold -> addTo(hold, capacitySlots(player.getUniqueId()), material, amount)).orElse(0);
    }

    /**
     * Add cargo to a specific boat - used when merging a salvaged hold.
     *
     * @return how many were actually added
     */
    public int addTo(BoatHold hold, int capacitySlots, Material material, int amount) {
        if (refuses(material) || amount <= 0) {
            return 0;
        }
        int left = amount;
        int main = Math.min(left, mainCapacityFor(hold, capacitySlots, material));
        if (main > 0) {
            hold.getContents().merge(material.name(), main, Integer::sum);
            left -= main;
        }
        for (Map<String, Integer> expander : hold.getExpanders()) {
            if (left <= 0) {
                break;
            }
            int fit = Math.min(left, storeCapacityFor(expander, EXPANDER_SLOTS, material));
            if (fit > 0) {
                expander.merge(material.name(), fit, Integer::sum);
                left -= fit;
            }
        }
        int added = amount - left;
        if (added > 0) {
            addon.getHoldManager().save(hold);
        }
        return added;
    }

    /**
     * Remove cargo - ONLY the market (sell) and the TNT slot (destroy) may
     * call this; there is no path from here to a player inventory.
     *
     * @return how many were actually removed
     */
    public int remove(Player player, Material material, int amount) {
        return active(player.getUniqueId()).map(hold -> removeFrom(hold, material, amount)).orElse(0);
    }

    /**
     * Remove cargo from a specific boat.
     */
    public int removeFrom(BoatHold hold, Material material, int amount) {
        int left = amount;
        left -= drain(hold.getContents(), material, left);
        for (Map<String, Integer> expander : hold.getExpanders()) {
            if (left <= 0) {
                break;
            }
            left -= drain(expander, material, left);
        }
        int taken = amount - left;
        if (taken > 0) {
            addon.getHoldManager().save(hold);
        }
        return taken;
    }

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
        int taken = remove(player, material, fit);
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
            hold.getExpanders().add(new LinkedHashMap<>());
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

    public Map<Material, Integer> expanderContents(UUID playerId, int index) {
        Map<Material, Integer> result = new LinkedHashMap<>();
        active(playerId).ifPresent(hold -> {
            List<Map<String, Integer>> expanders = hold.getExpanders();
            if (index >= 0 && index < expanders.size()) {
                expanders.get(index).forEach((name, amount) -> {
                    Material material = Material.matchMaterial(name);
                    if (material != null && amount != null && amount > 0) {
                        result.put(material, amount);
                    }
                });
            }
        });
        return result;
    }

    public int addToExpander(Player player, int index, Material material, int amount) {
        if (refuses(material) || amount <= 0) {
            return 0;
        }
        return active(player.getUniqueId()).map(hold -> {
            List<Map<String, Integer>> expanders = hold.getExpanders();
            if (index < 0 || index >= expanders.size()) {
                return 0;
            }
            Map<String, Integer> store = expanders.get(index);
            int fit = Math.min(amount, storeCapacityFor(store, EXPANDER_SLOTS, material));
            if (fit <= 0) {
                return 0;
            }
            store.merge(material.name(), fit, Integer::sum);
            addon.getHoldManager().save(hold);
            return fit;
        }).orElse(0);
    }

    public int removeFromExpander(UUID playerId, int index, Material material, int amount) {
        return active(playerId).map(hold -> {
            List<Map<String, Integer>> expanders = hold.getExpanders();
            if (index < 0 || index >= expanders.size()) {
                return 0;
            }
            int taken = drain(expanders.get(index), material, amount);
            if (taken > 0) {
                addon.getHoldManager().save(hold);
            }
            return taken;
        }).orElse(0);
    }

    /**
     * Destroy an EMPTY expander (the TNT refuses a loaded one).
     */
    public boolean destroyExpander(UUID playerId, int index) {
        return active(playerId).map(hold -> {
            List<Map<String, Integer>> expanders = hold.getExpanders();
            if (index < 0 || index >= expanders.size() || !expanders.get(index).isEmpty()) {
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
