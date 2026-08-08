package world.bentobox.tradewinds.api.events;

import org.bukkit.entity.Player;
import org.bukkit.event.Cancellable;
import org.bukkit.event.Event;
import org.bukkit.event.HandlerList;

import world.bentobox.tradewinds.ocean.IslandSpec;

/**
 * Fired before a warp executes (after fuel is confirmed but before it is
 * consumed). Cancel to veto the warp at no cost to the player.
 *
 * @author tastybento
 */
public class TWWarpEvent extends Event implements Cancellable {

    private static final HandlerList HANDLERS = new HandlerList();

    private final Player player;
    private final IslandSpec from;
    private final IslandSpec to;
    private final int fuelCost;
    private boolean cancelled;

    public TWWarpEvent(Player player, IslandSpec from, IslandSpec to, int fuelCost) {
        this.player = player;
        this.from = from;
        this.to = to;
        this.fuelCost = fuelCost;
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

    public int getFuelCost() {
        return fuelCost;
    }

    @Override
    public boolean isCancelled() {
        return cancelled;
    }

    @Override
    public void setCancelled(boolean cancel) {
        this.cancelled = cancel;
    }

    @Override
    public HandlerList getHandlers() {
        return getHandlerList();
    }

    public static HandlerList getHandlerList() {
        return HANDLERS;
    }
}
