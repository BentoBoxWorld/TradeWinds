package world.bentobox.tradewinds.api.events;

import org.bukkit.entity.Player;
import org.bukkit.event.Event;
import org.bukkit.event.HandlerList;

import world.bentobox.tradewinds.galaxy.IslandSpec;

/**
 * Fired when a warp fails and drops the player into the interstice. The fuel
 * was already spent at engagement - re-engaging to the same destination is
 * free, so this is a detour, never a loss.
 *
 * @author tastybento
 */
public class TWWarpFailedEvent extends Event {

    private static final HandlerList HANDLERS = new HandlerList();

    private final Player player;
    private final IslandSpec from;
    private final IslandSpec to;

    public TWWarpFailedEvent(Player player, IslandSpec from, IslandSpec to) {
        this.player = player;
        this.from = from;
        this.to = to;
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

    @Override
    public HandlerList getHandlers() {
        return HANDLERS;
    }

    public static HandlerList getHandlerList() {
        return HANDLERS;
    }
}
