package world.bentobox.tradewinds.tasks;

import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.scheduler.BukkitTask;

import world.bentobox.bentobox.api.localization.TextVariables;
import world.bentobox.bentobox.api.user.User;
import world.bentobox.tradewinds.TradeWinds;
import world.bentobox.tradewinds.galaxy.IslandSpec;
import world.bentobox.tradewinds.travel.FuelWarning;

/**
 * Tells a sailor standing in a market that they cannot afford to leave it.
 * <p>
 * Two channels on purpose. The action bar repeats while they are ashore and
 * short, because an action bar is easy to miss and a one-shot would be missed
 * by exactly the players this is for. A chat line goes out once per visit,
 * because chat <em>stays</em> - a child who looks away for ten seconds can
 * still find out what happened. The market dialog carries the third telling
 * (a highlighted "buy fuel here" button), so the warning is never more than
 * one screen from the fix.
 *
 * @author tastybento
 */
public class FuelWarningTask implements Runnable {

    private final TradeWinds addon;
    /** Player -> the island cell they were last warned at, so chat fires once. */
    private final Map<UUID, String> warnedAt = new ConcurrentHashMap<>();
    private BukkitTask task;

    public FuelWarningTask(TradeWinds addon) {
        this.addon = addon;
    }

    public void start() {
        long period = Math.max(1, addon.getSettings().getFuelWarningSeconds()) * 20L;
        task = Bukkit.getScheduler().runTaskTimer(addon.getPlugin(), this, period, period);
    }

    public void stop() {
        if (task != null) {
            task.cancel();
        }
    }

    @Override
    public void run() {
        if (!addon.getSettings().isFuelWarningEnabled() || addon.getOverWorld() == null) {
            return;
        }
        for (Player player : addon.getOverWorld().getPlayers()) {
            Optional<IslandSpec> port = portAt(player);
            if (port.isEmpty()) {
                warnedAt.remove(player.getUniqueId());
                continue;
            }
            check(player, port.get());
        }
    }

    private void check(Player player, IslandSpec port) {
        // No ship, no fuel tank: nagging a boatless sailor about fuel is
        // noise they cannot act on (playtest 2026-08-02)
        if (addon.getHoldService().active(player.getUniqueId()).isEmpty()) {
            warnedAt.remove(player.getUniqueId());
            return;
        }
        double fuel = addon.getFuelService().holdFuel(player);
        int cheapest = FuelWarning.cheapestRoute(addon.getWarpService().destinations(player, port, fuel));
        if (!FuelWarning.isLow(fuel, cheapest, addon.getSettings().getFuelWarningMargin())) {
            warnedAt.remove(player.getUniqueId());
            return;
        }
        User user = User.getInstance(player);
        String key = port.cellX() + "," + port.cellZ();
        int short_ = FuelWarning.shortfall(fuel, cheapest);
        // Repeats while they stand there - an action bar fades, the problem does not
        user.sendMessage("tradewinds.fuel.low-actionbar", "[fuel]", String.valueOf((int) fuel), "[needed]",
                String.valueOf(cheapest));
        if (!key.equals(warnedAt.get(player.getUniqueId()))) {
            // Once per port, in chat, where it stays put
            warnedAt.put(player.getUniqueId(), key);
            user.sendMessage("tradewinds.fuel.low-chat", "[fuel]", String.valueOf((int) fuel), "[needed]",
                    String.valueOf(cheapest), TextVariables.NUMBER, String.valueOf(short_));
        }
    }

    /**
     * The trading island whose space the player is standing in, if any.
     */
    private Optional<IslandSpec> portAt(Player player) {
        int x = player.getLocation().getBlockX();
        int z = player.getLocation().getBlockZ();
        int range = addon.getSettings().getIslandProtectionRange();
        return addon.getGalaxyEngine(addon.getOverWorld().getSeed()).islandsNear(x, z, range).stream()
                .filter(s -> s.distanceSquared(x, z) <= (long) range * range).findFirst();
    }
}
