package world.bentobox.tradewinds.crime;

import java.util.UUID;

import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.scheduler.BukkitTask;

import world.bentobox.bentobox.api.localization.TextVariables;
import world.bentobox.bentobox.api.user.User;
import world.bentobox.bentobox.hooks.VaultHook;
import world.bentobox.tradewinds.TradeWinds;
import world.bentobox.tradewinds.api.events.TWReputationChangeEvent;
import world.bentobox.tradewinds.dataobjects.TWPlayerData;

/**
 * The law's memory: one global reputation score per player, the standing it
 * earns, and the three ways back from it.
 * <p>
 * Recovery has three speeds by design (spec section 7): slow decay from clean
 * play so a crime shadows you for sessions, fines that buy off the worst of it
 * but never buy virtue (they stop at Clean), and positive acts above zero
 * (post-MVP). Decay is credited per elapsed minute of <em>online</em> time, so
 * logging out for a week does not launder a reputation.
 *
 * @author tastybento
 */
public class ReputationService {

    private final TradeWinds addon;
    private BukkitTask decayTask;

    public ReputationService(TradeWinds addon) {
        this.addon = addon;
    }

    /**
     * The configured number line.
     *
     * @return the reputation scale
     */
    public ReputationScale scale() {
        return addon.getSettings().reputationScale();
    }

    /**
     * A player's raw reputation score.
     *
     * @param playerId the player
     * @return the score
     */
    public int score(UUID playerId) {
        return addon.getPlayerDataManager().get(playerId).getReputation();
    }

    /**
     * A player's standing.
     *
     * @param playerId the player
     * @return the band
     */
    public Standing standing(UUID playerId) {
        return scale().standingOf(score(playerId));
    }

    /**
     * The money on a player's head.
     *
     * @param playerId the player
     * @return the bounty
     */
    public double bounty(UUID playerId) {
        return addon.getPlayerDataManager().get(playerId).getBounty();
    }

    /**
     * Record a crime: dock reputation, add to the bounty, tell the player, and
     * announce a band change if one happened.
     *
     * @param player the offender
     * @param crime what they did
     * @return the new standing
     */
    public Standing recordCrime(Player player, Crime crime) {
        if (!addon.getSettings().isCrimeEnabled()) {
            return Standing.CLEAN;
        }
        int penalty = addon.getSettings().penaltyFor(crime);
        double bounty = addon.getSettings().bountyFor(crime);
        Standing before = standing(player.getUniqueId());
        TWPlayerData data = addon.getPlayerDataManager().get(player.getUniqueId());

        int adjusted = scale().clamp(data.getReputation() + penalty);
        if (adjusted == data.getReputation() && bounty <= 0) {
            return before;
        }
        data.setReputation(adjusted);
        data.setBounty(data.getBounty() + bounty);
        addon.getPlayerDataManager().save(player.getUniqueId());

        Standing after = scale().standingOf(adjusted);
        User user = User.getInstance(player);
        user.sendMessage(crime.getLocaleKey(), TextVariables.NUMBER, String.valueOf(Math.abs(penalty)));
        if (after != before) {
            announce(user, after);
        }
        Bukkit.getPluginManager()
                .callEvent(new TWReputationChangeEvent(player, before, after, adjusted, crime));
        return after;
    }

    /**
     * Credit reputation back - clean play, or a delivery contract later.
     *
     * @param playerId the player
     * @param points points to add, positive
     * @return the new standing
     */
    public Standing credit(UUID playerId, int points) {
        TWPlayerData data = addon.getPlayerDataManager().get(playerId);
        Standing before = scale().standingOf(data.getReputation());
        data.setReputation(scale().clamp(data.getReputation() + Math.max(0, points)));
        Standing after = scale().standingOf(data.getReputation());
        if (after != before) {
            Player player = Bukkit.getPlayer(playerId);
            if (player != null) {
                announce(User.getInstance(player), after);
            }
        }
        return after;
    }

    /**
     * What it would cost to buy off a player's criminal record, or 0 if they
     * have nothing to answer for. Money buys you out of Wanted, never into
     * virtue: a fine can only ever take you back to Clean.
     *
     * @param playerId the player
     * @return the fine, in currency
     */
    public double fine(UUID playerId) {
        int debt = scale().debt(score(playerId));
        if (debt <= 0) {
            return 0;
        }
        return debt * addon.getSettings().getFinePerPoint();
    }

    /**
     * Pay a player's fine in full: charge them and clear their record to Clean.
     * The bounty is cleared with it - the law has been satisfied.
     *
     * @param player the player
     * @return true if the fine was paid
     */
    public boolean payFine(Player player) {
        double owed = fine(player.getUniqueId());
        if (owed <= 0) {
            return false;
        }
        VaultHook vault = addon.getPlugin().getVault().orElse(null);
        User user = User.getInstance(player);
        if (vault == null || !vault.has(user, owed)) {
            return false;
        }
        vault.withdraw(user, owed);
        TWPlayerData data = addon.getPlayerDataManager().get(player.getUniqueId());
        data.setReputation(scale().offender());
        data.setBounty(0);
        addon.getPlayerDataManager().save(player.getUniqueId());
        announce(User.getInstance(player), Standing.CLEAN);
        return true;
    }

    /**
     * Clear a bounty after it is paid out, so it pays exactly once.
     *
     * @param playerId the player who was killed
     * @return the bounty that was cleared
     */
    public double claimBounty(UUID playerId) {
        TWPlayerData data = addon.getPlayerDataManager().get(playerId);
        double owed = data.getBounty();
        data.setBounty(0);
        addon.getPlayerDataManager().save(playerId);
        return owed;
    }

    private void announce(User user, Standing standing) {
        user.sendMessage("tradewinds.standing.changed", "[standing]",
                user.getTranslation(standing.getLocaleKey()));
    }

    /**
     * Start the clean-play decay: every online player drifts back toward zero.
     */
    public void start() {
        long period = Math.max(1, addon.getSettings().getReputationDecayMinutes()) * 60L * 20L;
        decayTask = Bukkit.getScheduler().runTaskTimer(addon.getPlugin(), this::decayTick, period, period);
    }

    public void stop() {
        if (decayTask != null) {
            decayTask.cancel();
        }
    }

    /**
     * One decay tick: every online player in a TradeWinds world moves one step
     * toward zero, from whichever side.
     */
    void decayTick() {
        if (!addon.getSettings().isCrimeEnabled()) {
            return;
        }
        int step = addon.getSettings().getReputationDecayPoints();
        for (Player player : Bukkit.getOnlinePlayers()) {
            if (!addon.inWorld(player.getWorld())) {
                continue;
            }
            TWPlayerData data = addon.getPlayerDataManager().get(player.getUniqueId());
            int moved = decayed(data.getReputation(), step);
            if (moved != data.getReputation()) {
                Standing before = scale().standingOf(data.getReputation());
                data.setReputation(moved);
                Standing after = scale().standingOf(moved);
                if (after != before) {
                    announce(User.getInstance(player), after);
                }
            }
        }
    }

    /**
     * One step of decay toward zero, never overshooting it. Pure and testable.
     *
     * @param score current score
     * @param step points per tick
     * @return the decayed score
     */
    static int decayed(int score, int step) {
        if (score < 0) {
            return Math.min(0, score + Math.abs(step));
        }
        if (score > 0) {
            return Math.max(0, score - Math.abs(step));
        }
        return 0;
    }
}
