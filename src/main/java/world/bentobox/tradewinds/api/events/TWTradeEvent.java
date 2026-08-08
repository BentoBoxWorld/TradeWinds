package world.bentobox.tradewinds.api.events;

import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.event.Cancellable;
import org.bukkit.event.Event;
import org.bukkit.event.HandlerList;

import world.bentobox.tradewinds.ocean.IslandSpec;

/**
 * Fired before a market trade executes. Cancel to veto (Stage 6 uses this for
 * contraband and Fugitive market bans).
 *
 * @author tastybento
 */
public class TWTradeEvent extends Event implements Cancellable {

    private static final HandlerList HANDLERS = new HandlerList();

    private final Player player;
    private final IslandSpec island;
    private final Material material;
    private final int amount;
    private final double unitPrice;
    private final boolean playerSelling;
    private boolean cancelled;

    public TWTradeEvent(Player player, IslandSpec island, Material material, int amount, double unitPrice,
            boolean playerSelling) {
        this.player = player;
        this.island = island;
        this.material = material;
        this.amount = amount;
        this.unitPrice = unitPrice;
        this.playerSelling = playerSelling;
    }

    public Player getPlayer() {
        return player;
    }

    public IslandSpec getIsland() {
        return island;
    }

    public Material getMaterial() {
        return material;
    }

    public int getAmount() {
        return amount;
    }

    public double getUnitPrice() {
        return unitPrice;
    }

    /**
     * @return true if the player is selling to the island; false if buying
     */
    public boolean isPlayerSelling() {
        return playerSelling;
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
