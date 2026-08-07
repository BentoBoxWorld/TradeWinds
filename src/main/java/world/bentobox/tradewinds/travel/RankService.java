package world.bentobox.tradewinds.travel;

import java.util.Comparator;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import world.bentobox.tradewinds.TradeWinds;

/**
 * Seafarer ranks: a ladder climbed by charting islands. The rank table lives in
 * config (slug -&gt; charted-island threshold), display names in the locale
 * under {@code tradewinds.rank.<slug>} - so admins retune the ladder without
 * touching code, and every name can be translated.
 * <p>
 * Rank is the game's notion of "level": Stage 7 gates islet claiming on it,
 * and the leaderboard ranks players by the same charted count. It is earned by
 * sailing - money cannot buy it, and the starter cluster the game pre-charts
 * is deliberately below the first real rung.
 *
 * @author tastybento
 */
public class RankService {

    /** A rung of the ladder. */
    public record Rank(String slug, int threshold) {
        /** The locale key carrying this rank's display name. */
        public String localeKey() {
            return "tradewinds.rank." + slug;
        }
    }

    private final TradeWinds addon;
    private final List<Rank> ladder; // ascending by threshold

    public RankService(TradeWinds addon) {
        this.addon = addon;
        this.ladder = addon.getSettings().getRankThresholds().entrySet().stream()
                .map(e -> new Rank(e.getKey(), e.getValue()))
                .sorted(Comparator.comparingInt(Rank::threshold))
                .toList();
        if (ladder.isEmpty() || ladder.get(0).threshold() > 0) {
            addon.logWarning("ranks.thresholds has no rank at 0 charted islands - "
                    + "players below the first threshold will show the lowest configured rank");
        }
    }

    /**
     * How many islands a player has charted, as the ladder sees it: the real
     * chart plus any admin adjustment.
     *
     * @param playerId the player
     * @return effective charted island count
     */
    public int chartedCount(UUID playerId) {
        return addon.getPlayerDataManager().get(playerId).effectiveCharted();
    }

    /**
     * Admin override: make a player's effective charted count equal target,
     * by storing the difference from their real chart as a bonus (negative to
     * demote). Their real charting keeps counting on top, so a promoted
     * player still climbs. The full ladder, for admin tab-completion.
     *
     * @param playerId the player
     * @param target the effective charted count wanted
     */
    public void setEffectiveCharted(UUID playerId, int target) {
        var data = addon.getPlayerDataManager().get(playerId);
        data.setChartedBonus(target - data.getChartedIslands().size());
        addon.getPlayerDataManager().save(playerId);
    }

    /**
     * Clear any admin adjustment: back to the real chart.
     *
     * @param playerId the player
     */
    public void clearAdjustment(UUID playerId) {
        addon.getPlayerDataManager().get(playerId).setChartedBonus(0);
        addon.getPlayerDataManager().save(playerId);
    }

    /**
     * The whole ladder, lowest rung first.
     *
     * @return the ranks
     */
    public List<Rank> ladder() {
        return ladder;
    }

    /**
     * The rank a charted count earns: the highest threshold at or below it.
     *
     * @param charted charted island count
     * @return the rank; the lowest rung if the count is below every threshold
     */
    public Rank rankFor(int charted) {
        Rank current = ladder.get(0);
        for (Rank rank : ladder) {
            if (rank.threshold() > charted) {
                break;
            }
            current = rank;
        }
        return current;
    }

    /**
     * The next rung above a charted count, if any.
     *
     * @param charted charted island count
     * @return the next rank, or empty at the top of the ladder
     */
    public Optional<Rank> next(int charted) {
        return ladder.stream().filter(rank -> rank.threshold() > charted).findFirst();
    }

    /**
     * Look a rank up by its config slug.
     *
     * @param slug the slug
     * @return the rank, or empty if the table has no such rung
     */
    public Optional<Rank> bySlug(String slug) {
        return ladder.stream().filter(rank -> rank.slug().equalsIgnoreCase(slug)).findFirst();
    }

    /**
     * The charted-island count required to claim a wild islet: the threshold of
     * the configured {@code claims.minimum-rank}. A slug that names no rung is
     * a config mistake and fails CLOSED - the top threshold, not zero - so a
     * typo never hands out islands.
     *
     * @return charted islands required to claim
     */
    public int claimThreshold() {
        String slug = addon.getSettings().getClaimMinimumRank();
        Optional<Rank> rank = bySlug(slug);
        if (rank.isEmpty()) {
            addon.logError("claims.minimum-rank '" + slug + "' is not in ranks.thresholds - "
                    + "requiring the top rank instead");
            return ladder.get(ladder.size() - 1).threshold();
        }
        return rank.get().threshold();
    }
}
