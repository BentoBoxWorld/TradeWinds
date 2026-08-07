package world.bentobox.tradewinds.dataobjects;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
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
     * The boat that IS this player's hold (a {@link BoatHold} id), or "" if
     * they have none. One boat, always.
     */
    @Expose
    private String activeBoat = "";

    /**
     * The boat they abandoned by boarding another: unowned, capturable, and
     * remembered only so the chart can point them back to it. Exactly one.
     */
    @Expose
    private String oldBoat = "";

    /**
     * Charted island cell keys ("cellX,cellZ"). Only charted islands appear in
     * the warp dialog.
     */
    @Expose
    private Set<String> chartedIslands = new HashSet<>();

    /**
     * Admin adjustment to the charted count for rank purposes: set by
     * {@code /twadmin rank}, positive to promote, negative to demote. 0 for
     * everyone the admins have left alone.
     */
    private int chartedBonus;

    /**
     * Whether the one-time starter kit (boat + trading bundle) has been given.
     */
    @Expose
    private boolean starterKitGiven;

    /**
     * Career restarts consumed (/tw restart).
     */
    @Expose
    private int restartsUsed;

    /**
     * Epoch millis of the last harbourmaster's charity claim.
     */
    @Expose
    private long lastCharity;

    /**
     * Where this sailor last was in a TradeWinds world, so leaving the world
     * and coming back does not move them. Empty until they first set sail.
     */
    @Expose
    private String lastSeaPosition;

    /**
     * Reputation score - one global number, positive is good. The standing
     * bands derive from it (see ReputationScale).
     */
    @Expose
    private int reputation;

    /**
     * Money on this player's head, paid once to whoever kills them while they
     * are a lawful target.
     */
    @Expose
    private double bounty;

    /**
     * Epoch millis when clean play last paid a decay tick, so time offline does
     * not launder a reputation.
     */
    @Expose
    private long lastDecay;

    public TWPlayerData() {
        // Required by the database
    }

    public TWPlayerData(String uniqueId) {
        this.uniqueId = uniqueId;
    }

    /**
     * The price logbook: island key -> trade category -> the unit price SEEN
     * there, and when. Prices for ports you have never visited are not knowable
     * (Stage 7.5 Phase 6) - the market is learned, not published, which is what
     * makes a veteran trader skilled rather than merely rich.
     * <p>
     * Category-level rather than per-material because that is the granularity
     * prices actually vary at: type, tech, band and drift all move a whole
     * category together.
     */
    @Expose
    private Map<String, Map<String, Integer>> priceLog = new HashMap<>();

    /** Island key -> when its prices were last observed, epoch millis. */
    @Expose
    private Map<String, Long> priceLogSeen = new HashMap<>();

    /**
     * Record what a port was paying, now.
     *
     * @param spec the island
     * @param prices category name -> unit price seen
     * @param now epoch millis
     */
    public void logPrices(IslandSpec spec, Map<String, Integer> prices, long now) {
        priceLog.put(chartKey(spec), new HashMap<>(prices));
        priceLogSeen.put(chartKey(spec), now);
    }

    /**
     * @return remembered prices at an island, category name -> unit price
     */
    public Map<String, Integer> loggedPrices(IslandSpec spec) {
        return priceLog.getOrDefault(chartKey(spec), Map.of());
    }

    /**
     * @return when this island's prices were seen, or 0 if never
     */
    public long lastSeenPrices(IslandSpec spec) {
        return priceLogSeen.getOrDefault(chartKey(spec), 0L);
    }

    public Map<String, Map<String, Integer>> getPriceLog() {
        return priceLog;
    }

    public void setPriceLog(Map<String, Map<String, Integer>> priceLog) {
        this.priceLog = priceLog == null ? new HashMap<>() : priceLog;
    }

    public Map<String, Long> getPriceLogSeen() {
        return priceLogSeen;
    }

    public void setPriceLogSeen(Map<String, Long> priceLogSeen) {
        this.priceLogSeen = priceLogSeen == null ? new HashMap<>() : priceLogSeen;
    }

    /**
     * The chart key for an island.
     */
    public static String chartKey(IslandSpec spec) {
        return spec.cellX() + "," + spec.cellZ();
    }

    /**
     * Charted islands as the rank ladder sees them: the real chart plus the
     * admin adjustment, never below zero. Rank, the claim gate and the
     * leaderboard all read this one number, so an admin promotion (or
     * demotion) is consistent everywhere - and real charting keeps counting
     * on top of it.
     */
    public int effectiveCharted() {
        return Math.max(0, chartedIslands.size() + chartedBonus);
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

    public String getActiveBoat() {
        return activeBoat;
    }

    public void setActiveBoat(String activeBoat) {
        this.activeBoat = activeBoat == null ? "" : activeBoat;
    }

    public String getOldBoat() {
        return oldBoat;
    }

    public void setOldBoat(String oldBoat) {
        this.oldBoat = oldBoat == null ? "" : oldBoat;
    }

    public long getLastCharity() {
        return lastCharity;
    }

    public void setLastCharity(long lastCharity) {
        this.lastCharity = lastCharity;
    }

    public int getRestartsUsed() {
        return restartsUsed;
    }

    public void setRestartsUsed(int restartsUsed) {
        this.restartsUsed = restartsUsed;
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

    public int getChartedBonus() {
        return chartedBonus;
    }

    public void setChartedBonus(int chartedBonus) {
        this.chartedBonus = chartedBonus;
    }

    public String getLastSeaPosition() {
        return lastSeaPosition;
    }

    public void setLastSeaPosition(String lastSeaPosition) {
        this.lastSeaPosition = lastSeaPosition;
    }

    public int getReputation() {
        return reputation;
    }

    public void setReputation(int reputation) {
        this.reputation = reputation;
    }

    public double getBounty() {
        return bounty;
    }

    public void setBounty(double bounty) {
        this.bounty = bounty;
    }

    public long getLastDecay() {
        return lastDecay;
    }

    public void setLastDecay(long lastDecay) {
        this.lastDecay = lastDecay;
    }
}
