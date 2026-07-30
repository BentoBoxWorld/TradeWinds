package world.bentobox.tradewinds.api.events;

import org.bukkit.entity.Player;
import org.bukkit.event.Event;
import org.bukkit.event.HandlerList;

import world.bentobox.tradewinds.galaxy.IslandSpec;

/**
 * Fired after a warp has delivered the player (fuel consumed, teleport done).
 *
 * @author tastybento
 */
public class TWWarpCompletedEvent extends Event {

    private static final HandlerList HANDLERS = new HandlerList();

    private final Player player;
    private final IslandSpec from;
    private final IslandSpec to;
    private final int fuelSpent;

    public TWWarpCompletedEvent(Player player, IslandSpec from, IslandSpec to, int fuelSpent) {
        this.player = player;
        this.from = from;
        this.to = to;
        this.fuelSpent = fuelSpent;
    }

    public Player getPlayer() {
        return player;
    }

    public IslandSpec getFrom() {
        return from;
    }

    public IslandSpec getTo() {
        return to;
    }

    public int getFuelSpent() {
        return fuelSpent;
    }

    @Override
    public HandlerList getHandlers() {
        return HANDLERS;
    }

    public static HandlerList getHandlerList() {
        return HANDLERS;
    }
}
