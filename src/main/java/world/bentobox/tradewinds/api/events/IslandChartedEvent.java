package world.bentobox.tradewinds.api.events;

import java.util.UUID;

import org.bukkit.event.Event;
import org.bukkit.event.HandlerList;

import world.bentobox.tradewinds.ocean.IslandSpec;

/**
 * Fired when a player charts a trading island (enters its range for the first
 * time, or gains it another way). Ecosystem hook.
 *
 * @author tastybento
 */
public class IslandChartedEvent extends Event {

    private static final HandlerList HANDLERS = new HandlerList();

    private final UUID playerId;
    private final IslandSpec island;

    public IslandChartedEvent(UUID playerId, IslandSpec island) {
        this.playerId = playerId;
        this.island = island;
    }

    public UUID getPlayerId() {
        return playerId;
    }

    public IslandSpec getIsland() {
        return island;
    }

    @Override
    public HandlerList getHandlers() {
        return getHandlerList();
    }

    public static HandlerList getHandlerList() {
        return HANDLERS;
    }
}
