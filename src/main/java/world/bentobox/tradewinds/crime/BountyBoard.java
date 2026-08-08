package world.bentobox.tradewinds.crime;

import java.util.UUID;

import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.scheduler.BukkitTask;
import org.bukkit.scoreboard.Scoreboard;
import org.bukkit.scoreboard.Team;

import net.kyori.adventure.text.Component;
import world.bentobox.bentobox.api.user.User;
import world.bentobox.tradewinds.TradeWinds;

/**
 * Shows what a player is worth, above their head and to other plugins.
 * <p>
 * A bounty nobody can see is not a bounty: bounty hunting only works if a
 * hunter can tell at a glance that the person in the boat is worth money.
 * Boats prevent sneaking, so a travelling pirate is always visible - that is a
 * feature, not an oversight.
 * <p>
 * Servers running TAB or a nametag plugin will fight a scoreboard team suffix,
 * so this is config-gated and a PlaceholderAPI placeholder is exposed
 * alongside: turn the nameplate off and render {@code %tradewinds_bounty%}
 * however the server already renders names.
 *
 * @author tastybento
 */
public class BountyBoard {

    /** Scoreboard team names are length-limited, so the UUID is truncated. */
    private static final int TEAM_KEY_LENGTH = 12;
    private static final String TEAM_PREFIX = "tw_b_";
    private static final long PERIOD = 100L;

    private final TradeWinds addon;
    private BukkitTask task;

    public BountyBoard(TradeWinds addon) {
        this.addon = addon;
    }

    public void start() {
        registerPlaceholders();
        if (!addon.getSettings().isBountyNameplate()) {
            return;
        }
        task = Bukkit.getScheduler().runTaskTimer(addon.getPlugin(), this::refresh, PERIOD, PERIOD);
    }

    public void stop() {
        if (task != null) {
            task.cancel();
        }
        Bukkit.getOnlinePlayers().forEach(player -> clear(player.getUniqueId()));
    }

    /**
     * Update every online player's nameplate.
     */
    void refresh() {
        for (Player player : Bukkit.getOnlinePlayers()) {
            if (!addon.inWorld(player.getWorld())) {
                clear(player.getUniqueId());
                continue;
            }
            double bounty = addon.getReputationService().bounty(player.getUniqueId());
            if (bounty <= 0) {
                clear(player.getUniqueId());
            } else {
                show(player, bounty);
            }
        }
    }

    private void show(Player player, double bounty) {
        Scoreboard board = Bukkit.getScoreboardManager().getMainScoreboard();
        Team team = team(board, player.getUniqueId());
        // The suffix is one string for every viewer, so it takes the server's
        // own locale rather than any one player's
        Component suffix = User.getInstance(Bukkit.getConsoleSender()).getTranslationAsComponent(
                "tradewinds.police.bounty-tag", "[amount]", format(bounty));
        team.suffix(suffix);
        if (!team.hasEntry(player.getName())) {
            team.addEntry(player.getName());
        }
    }

    private Team team(Scoreboard board, UUID playerId) {
        String name = TEAM_PREFIX + playerId.toString().substring(0, TEAM_KEY_LENGTH);
        Team team = board.getTeam(name);
        return team == null ? board.registerNewTeam(name) : team;
    }

    /**
     * Drop a player's nameplate - paid out, or never had one.
     *
     * @param playerId the player
     */
    public void clear(UUID playerId) {
        Scoreboard board = Bukkit.getScoreboardManager().getMainScoreboard();
        Team team = board.getTeam(TEAM_PREFIX + playerId.toString().substring(0, TEAM_KEY_LENGTH));
        if (team != null) {
            team.unregister();
        }
    }

    private String format(double amount) {
        return addon.getPlugin().getVault().map(vault -> vault.format(amount))
                .orElse(world.bentobox.tradewinds.economy.Money.format(addon, amount));
    }

    /**
     * Expose the law's view of a player to PlaceholderAPI, so servers that
     * already own the nametag can render it themselves instead of fighting the
     * scoreboard team.
     */
    private void registerPlaceholders() {
        var placeholders = addon.getPlugin().getPlaceholdersManager();
        placeholders.registerPlaceholder(addon, "bounty",
                user -> user == null ? "0" : format(addon.getReputationService().bounty(user.getUniqueId())));
        placeholders.registerPlaceholder(addon, "bounty_raw", user -> user == null ? "0"
                : world.bentobox.tradewinds.economy.Money.format(addon, addon.getReputationService().bounty(user.getUniqueId())));
        placeholders.registerPlaceholder(addon, "standing",
                user -> user == null ? "" : addon.getPlayerStanding(user, user.getUniqueId()));
        placeholders.registerPlaceholder(addon, "reputation", user -> user == null ? "0"
                : String.valueOf(addon.getReputationService().score(user.getUniqueId())));
        placeholders.registerPlaceholder(addon, "wanted", user -> user == null ? "false"
                : String.valueOf(addon.getReputationService().standing(user.getUniqueId()).isHunted()));
    }
}
