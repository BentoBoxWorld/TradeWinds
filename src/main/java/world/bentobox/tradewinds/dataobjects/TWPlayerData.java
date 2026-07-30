package world.bentobox.tradewinds.dataobjects;

import java.util.HashSet;
import java.util.Set;

import com.google.gson.annotations.Expose;

import world.bentobox.bentobox.database.objects.DataObject;
import world.bentobox.bentobox.database.objects.Table;
import world.bentobox.tradewinds.galaxy.IslandSpec;

/**
 * Per-player TradeWinds state, keyed by player UUID. Stage 3 holds the chart;
 * later stages add reputation, bounty, flags and cooldowns (spec §9).
 *
 * @author tastybento
 */
@Table(name = "TWPlayerData")
public class TWPlayerData implements DataObject {

    @Expose
    private String uniqueId;

    /**
     * Charted island cell keys ("cellX,cellZ"). Only charted islands appear in
     * the warp dialog.
     */
    @Expose
    private Set<String> chartedIslands = new HashSet<>();

    /**
     * Whether the one-time starter kit (boat + trading bundle) has been given.
     */
    @Expose
    private boolean starterKitGiven;

    public TWPlayerData() {
        // Required by the database
    }

    public TWPlayerData(String uniqueId) {
        this.uniqueId = uniqueId;
    }

    /**
     * The chart key for an island.
     */
    public static String chartKey(IslandSpec spec) {
        return spec.cellX() + "," + spec.cellZ();
    }

    /**
     * @return true if the island is on this player's chart
     */
    public boolean isCharted(IslandSpec spec) {
        return chartedIslands.contains(chartKey(spec));
    }

    /**
     * Add an island to the chart.
     * @return true if it was newly charted
     */
    public boolean chart(IslandSpec spec) {
        return chartedIslands.add(chartKey(spec));
    }

    @Override
    public String getUniqueId() {
        return uniqueId;
    }

    @Override
    public void setUniqueId(String uniqueId) {
        this.uniqueId = uniqueId;
    }

    public boolean isStarterKitGiven() {
        return starterKitGiven;
    }

    public void setStarterKitGiven(boolean starterKitGiven) {
        this.starterKitGiven = starterKitGiven;
    }

    public Set<String> getChartedIslands() {
        return chartedIslands;
    }

    public void setChartedIslands(Set<String> chartedIslands) {
        this.chartedIslands = chartedIslands;
    }
}
