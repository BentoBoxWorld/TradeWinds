package world.bentobox.tradewinds.dataobjects;

import java.util.HashMap;
import java.util.Map;

import com.google.gson.annotations.Expose;

import world.bentobox.bentobox.database.objects.DataObject;
import world.bentobox.bentobox.database.objects.Table;

/**
 * Per-trading-island market state, keyed by galaxy cell ("cellX,cellZ"):
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
     * Stock per trade category name. Positive = players sold a lot here
     * (prices depressed); negative = bought out (prices raised).
     */
    @Expose
    private Map<String, Integer> stock = new HashMap<>();

    /**
     * Epoch millis of the last stock decay pass.
     */
    @Expose
    private long lastDecay;

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

    public Map<String, Integer> getStock() {
        return stock;
    }

    public void setStock(Map<String, Integer> stock) {
        this.stock = stock;
    }

    public long getLastDecay() {
        return lastDecay;
    }

    public void setLastDecay(long lastDecay) {
        this.lastDecay = lastDecay;
    }
}
