package world.bentobox.tradewinds.travel;

import java.util.List;

import org.bukkit.inventory.ItemStack;

/**
 * Slot arithmetic for a hold: how much fits, what stacks with what, and where
 * goods land. A store is a mutable list of stacks, <b>one entry per occupied
 * slot</b>, exactly like a real inventory - so a unique item takes a slot of its
 * own and identical items consolidate.
 * <p>
 * Cargo is a list of {@link ItemStack} rather than material→amount because a
 * worn bow, a mint bow and a Silk Touch pick are not the same good, and a map
 * keyed by material said they were.
 * <p>
 * Everything here is static and side-effect-free except for the explicit
 * mutations, which is what makes the arithmetic testable instead of buried in
 * {@link HoldService}. ItemStack <i>serialisation</i> does not work headlessly
 * (the CraftBukkit delegate is absent), so persistence is a manual check - but
 * the packing, stacking and capacity rules are all covered by unit tests.
 *
 * @author tastybento
 */
public final class CargoStore {

    private CargoStore() {
        // Static use only
    }

    /**
     * Whether two stacks consolidate. {@link ItemStack#isSimilar} is the
     * authority: it ignores amount and compares everything else, so enchanted,
     * renamed and damaged items each keep their own slot.
     */
    public static boolean stacksTogether(ItemStack a, ItemStack b) {
        return a != null && b != null && a.isSimilar(b);
    }

    /**
     * The stack size limit for an item, never below 1.
     */
    public static int maxStack(ItemStack like) {
        return Math.max(1, like.getMaxStackSize());
    }

    /**
     * Whether an item carries nothing but its type - no enchantments, no name,
     * no damage, nothing worth preserving.
     * <p>
     * Compared against a pristine stack of the same type rather than by asking
     * {@code hasItemMeta()}, which is not trustworthy: MockBukkit attaches a
     * phantom UNSPECIFIC meta to every freshly built stack and then drops it on
     * {@code clone()}, so a cloned plain item stops being "similar" to an
     * identical one. Comparing against a pristine reference gives the same
     * answer in both worlds.
     *
     * @param like the item
     * @return true if a plain {@code new ItemStack(type, amount)} would do
     */
    public static boolean isPlain(ItemStack like) {
        return like.isSimilar(new ItemStack(like.getType()));
    }

    /**
     * A copy of an item at a given amount, preserving anything that makes it
     * distinctive. Plain goods are rebuilt from their type - there is nothing to
     * carry, and it avoids {@code clone()} entirely - while enchanted, renamed or
     * damaged items are cloned so their NBT survives.
     *
     * @param like the item to copy
     * @param amount the amount the copy should hold
     * @return the copy
     */
    public static ItemStack copyOf(ItemStack like, int amount) {
        if (isPlain(like)) {
            return new ItemStack(like.getType(), amount);
        }
        ItemStack copy = like.clone();
        copy.setAmount(amount);
        return copy;
    }

    /**
     * How many of this item the store already holds.
     */
    public static int count(List<ItemStack> store, ItemStack like) {
        int total = 0;
        for (ItemStack held : store) {
            if (stacksTogether(held, like)) {
                total += held.getAmount();
            }
        }
        return total;
    }

    /**
     * How many MORE of this item fit: the headroom in stacks it consolidates
     * with, plus a full stack for every free slot.
     *
     * @param store the store
     * @param slotBudget total slots this store may occupy
     * @param like the item
     * @return how many more fit
     */
    public static int capacityFor(List<ItemStack> store, int slotBudget, ItemStack like) {
        int max = maxStack(like);
        int headroom = 0;
        for (ItemStack held : store) {
            if (stacksTogether(held, like)) {
                headroom += Math.max(0, max - held.getAmount());
            }
        }
        int freeSlots = Math.max(0, slotBudget - store.size());
        return headroom + freeSlots * max;
    }

    /**
     * Add up to {@code amount} of an item, topping up existing stacks before
     * opening new slots.
     *
     * @param store the store, mutated
     * @param slotBudget total slots this store may occupy
     * @param like the item to add (not mutated; copies are stored)
     * @param amount how many to add
     * @return how many were actually added
     */
    public static int added(List<ItemStack> store, int slotBudget, ItemStack like, int amount) {
        if (like == null || like.getType().isAir() || amount <= 0) {
            return 0;
        }
        int max = maxStack(like);
        int left = amount;
        // Top up what is already aboard first, so cargo stays consolidated
        for (ItemStack held : store) {
            if (left <= 0) {
                break;
            }
            if (stacksTogether(held, like)) {
                int room = Math.max(0, max - held.getAmount());
                int fit = Math.min(room, left);
                if (fit > 0) {
                    held.setAmount(held.getAmount() + fit);
                    left -= fit;
                }
            }
        }
        // Then open new slots
        while (left > 0 && store.size() < slotBudget) {
            int fit = Math.min(max, left);
            store.add(copyOf(like, fit));
            left -= fit;
        }
        return amount - left;
    }

    /**
     * Remove up to {@code amount} of an item, emptying partial stacks first so
     * the store does not fragment.
     *
     * @param store the store, mutated
     * @param like the item to remove
     * @param amount how many to remove
     * @return how many were actually removed
     */
    public static int removed(List<ItemStack> store, ItemStack like, int amount) {
        if (like == null || amount <= 0) {
            return 0;
        }
        int left = amount;
        var iterator = store.iterator();
        while (iterator.hasNext() && left > 0) {
            ItemStack held = iterator.next();
            if (!stacksTogether(held, like)) {
                continue;
            }
            int taken = Math.min(held.getAmount(), left);
            left -= taken;
            if (taken >= held.getAmount()) {
                iterator.remove();
            } else {
                held.setAmount(held.getAmount() - taken);
            }
        }
        return amount - left;
    }

    /**
     * Drop null and zero-amount entries - a store should never render an empty
     * slot as an occupied one.
     *
     * @param store the store, mutated
     */
    public static void compact(List<ItemStack> store) {
        store.removeIf(held -> held == null || held.getType().isAir() || held.getAmount() <= 0);
    }
}
