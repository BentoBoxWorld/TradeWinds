package world.bentobox.tradewinds.travel;

import org.bukkit.NamespacedKey;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataType;

/**
 * Marks cargo bought from a trader, which is the one kind of cargo that may not
 * come back out of the hold.
 * <p>
 * The hold is two-way for goods a player loaded themselves - a scavenger needs
 * to use their hold as a hold - but <b>trader-bought cargo stays one-way</b>
 * (sale or destruction). That is what preserves cargo commitment: speculate on a
 * cargo and you must find a buyer for it, rather than warehousing it ashore and
 * waiting. It also keeps the hold from becoming an off-market channel for moving
 * goods between players.
 * <p>
 * The mark lives in the item's own persistent data, so it survives the hold's
 * serialisation, travels with the goods through a boat capture, and stays visible
 * if a bug ever leaks one into a player's inventory. It also makes marked and
 * unmarked stacks fail {@link ItemStack#isSimilar}, which is exactly right: they
 * must never merge, or one bought diamond would lock up twenty mined ones.
 *
 * @author tastybento
 */
public final class CargoMark {

    /**
     * The mark. Built with {@code fromString} to match {@code BoatService}'s
     * key: it needs no Plugin instance, so it neither holds a reference that
     * outlives a reload nor needs one mocked in tests.
     */
    public static final NamespacedKey TRADED_KEY = NamespacedKey.fromString("tradewinds:traded");

    private CargoMark() {
        // Static use only
    }

    /**
     * A copy of an item marked as bought from a trader.
     *
     * @param item the item
     * @return the marked copy, or the original if it has no usable meta
     */
    public static ItemStack marked(ItemStack item) {
        ItemStack copy = item.clone();
        ItemMeta meta = copy.getItemMeta();
        if (meta == null) {
            return item;
        }
        meta.getPersistentDataContainer().set(TRADED_KEY, PersistentDataType.BYTE, (byte) 1);
        copy.setItemMeta(meta);
        return copy;
    }

    /**
     * Whether this item was bought from a trader, and so may only leave the hold
     * by being sold or destroyed.
     *
     * @param item the item
     * @return true if it carries the mark
     */
    public static boolean isTraded(ItemStack item) {
        if (item == null) {
            return false;
        }
        ItemMeta meta = item.getItemMeta();
        return meta != null && meta.getPersistentDataContainer().has(TRADED_KEY, PersistentDataType.BYTE);
    }
}
