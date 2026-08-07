package world.bentobox.tradewinds.travel;

import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

import org.bukkit.Bukkit;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;

import world.bentobox.tradewinds.TradeWinds;
import world.bentobox.tradewinds.api.events.IslandChartedEvent;
import world.bentobox.tradewinds.dataobjects.TWPlayerData;

/**
 * The charted-islands leaderboard: who has seen the most of the sea. This is
 * the game's ranking - money cannot be ranked (Vault offers no top-N, and Bank
 * moves value out of sight), but charted count is earned, stored per player,
 * and drives the Seafarer ranks.
 * <p>
 * Counts are cached: seeded from the database off-thread at startup (sailors
 * keep their standing while offline), then kept fresh by
 * {@link IslandChartedEvent} - both the sighting and the port-scan path fire
 * it per newly charted island.
 *
 * @author tastybento
 */
public class ChartLeaderboard implements Listener {

    /** One leaderboard row. */
    public record Entry(UUID playerId, int charted) {
    }

    private final TradeWinds addon;
    private final Map<UUID, Integer> counts = new ConcurrentHashMap<>();

    public ChartLeaderboard(TradeWinds addon) {
        this.addon = addon;
    }

    /**
     * Seed the cache from the database, off the main thread - server start is
     * busy enough, and the leaderboard can be seconds stale for its first
     * moments without anyone noticing.
     */
    public void seed() {
        Bukkit.getScheduler().runTaskAsynchronously(addon.getPlugin(), () -> {
            var handler = new world.bentobox.bentobox.database.Database<>(addon, TWPlayerData.class);
            handler.loadObjects().forEach(data -> {
                try {
                    counts.put(UUID.fromString(data.getUniqueId()), data.effectiveCharted());
                } catch (IllegalArgumentException e) {
                    // Not a player record - skip
                }
            });
        });
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onCharted(IslandChartedEvent event) {
        refresh(event.getPlayerId());
    }

    /**
     * Re-read one player's count - also the hook {@code /twadmin rank} uses
     * after adjusting a player, so the board never shows a stale rank.
     *
     * @param playerId the player
     */
    public void refresh(UUID playerId) {
        counts.put(playerId, addon.getPlayerDataManager().get(playerId).effectiveCharted());
    }

    /**
     * The top of the board, most-charted first.
     *
     * @param limit how many rows
     * @return up to limit entries
     */
    public List<Entry> top(int limit) {
        return counts.entrySet().stream()
                .sorted(Map.Entry.<UUID, Integer>comparingByValue(Comparator.reverseOrder()))
                .limit(limit)
                .map(e -> new Entry(e.getKey(), e.getValue()))
                .toList();
    }

    /**
     * Expose ranks and the board to PlaceholderAPI, so scoreboard and tab
     * plugins can show them without a plugin-side integration:
     * {@code %tradewinds_rank%}, {@code %tradewinds_charted%},
     * {@code %tradewinds_top_name_1..10%}, {@code %tradewinds_top_charted_1..10%}.
     */
    public void registerPlaceholders(RankService ranks) {
        var placeholders = addon.getPlugin().getPlaceholdersManager();
        placeholders.registerPlaceholder(addon, "rank", user -> user == null ? ""
                : user.getTranslation(ranks.rankFor(ranks.chartedCount(user.getUniqueId())).localeKey()));
        placeholders.registerPlaceholder(addon, "charted",
                user -> user == null ? "0" : String.valueOf(ranks.chartedCount(user.getUniqueId())));
        for (int i = 1; i <= 10; i++) {
            final int place = i;
            placeholders.registerPlaceholder(addon, "top_name_" + place, user -> rowName(place));
            placeholders.registerPlaceholder(addon, "top_charted_" + place, user -> rowCount(place));
        }
    }

    private String rowName(int place) {
        List<Entry> board = top(place);
        if (board.size() < place) {
            return "";
        }
        String name = addon.getPlayers().getName(board.get(place - 1).playerId());
        return name == null ? "" : name;
    }

    private String rowCount(int place) {
        List<Entry> board = top(place);
        return board.size() < place ? "" : String.valueOf(board.get(place - 1).charted());
    }
}
