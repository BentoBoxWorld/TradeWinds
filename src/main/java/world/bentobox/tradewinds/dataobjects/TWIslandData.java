package world.bentobox.tradewinds.dataobjects;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.bukkit.inventory.ItemStack;

import com.google.gson.annotations.Expose;

import world.bentobox.bentobox.database.objects.DataObject;
import world.bentobox.bentobox.database.objects.Table;

/**
 * Per-trading-island market state, keyed by ocean cell ("cellX,cellZ"):
 * stock per trade category (relative to equilibrium 0) and the last decay
 * timestamp. This is what makes markets remember being flooded or bought out
 * across restarts (spec §9).
 *
 * @author tastybento
 */
@Table(name = "TWIslandData")
public class TWIslandData implements DataObject {

    @Expose
    private String uniqueId;

    /**
     * Stock per trade category name, measured in <b>coins of inventory
     * position</b> rather than item count - the trader's purse. Positive =
     * players sold a lot of value here (prices depressed); negative = bought
     * out (prices raised).
     * <p>
     * Coins, not units, because ten diamonds and ten wheat are not the same
     * business to a port, and counting units said they were.
     */
    @Expose
    private Map<String, Integer> stockValue = new HashMap<>();

    /**
     * Epoch millis of the last stock decay pass.
     */
    @Expose
    private long lastDecay;

    /**
     * The secondhand shelf: notable goods a trader took in and put back out for
     * sale. Bounded and swept by TTL.
     * <p>
     * Items surface at a DIFFERENT island from the one they were sold at - the
     * trader shipped it on - which is what stops port shelves becoming an
     * alt-account laundering channel: you cannot predict where your own goods
     * will reappear.
     */
    @Expose
    private List<ShelfItem> shelf = new ArrayList<>();

    /**
     * One listing on a secondhand shelf.
     */
    public static class ShelfItem {

        @Expose
        private ItemStack item;

        @Expose
        private long listedAt;

        public ShelfItem() {
            // Required by the database
        }

        public ShelfItem(ItemStack item, long listedAt) {
            this.item = item;
            this.listedAt = listedAt;
        }

        public ItemStack getItem() {
            return item;
        }

        public void setItem(ItemStack item) {
            this.item = item;
        }

        public long getListedAt() {
            return listedAt;
        }

        public void setListedAt(long listedAt) {
            this.listedAt = listedAt;
        }
    }

    public List<ShelfItem> getShelf() {
        return shelf;
    }

    public void setShelf(List<ShelfItem> shelf) {
        this.shelf = shelf == null ? new ArrayList<>() : shelf;
    }

    public TWIslandData() {
        // Required by the database
    }

    public TWIslandData(String uniqueId) {
        this.uniqueId = uniqueId;
        this.lastDecay = System.currentTimeMillis();
    }

    @Override
    public String getUniqueId() {
        return uniqueId;
    }

    @Override
    public void setUniqueId(String uniqueId) {
        this.uniqueId = uniqueId;
    }

    public Map<String, Integer> getStockValue() {
        return stockValue;
    }

    public void setStockValue(Map<String, Integer> stockValue) {
        this.stockValue = stockValue;
    }

    public long getLastDecay() {
        return lastDecay;
    }

    public void setLastDecay(long lastDecay) {
        this.lastDecay = lastDecay;
    }
}
