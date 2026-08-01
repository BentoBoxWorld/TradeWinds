package world.bentobox.tradewinds.api.events;

import org.bukkit.entity.Player;
import org.bukkit.event.Event;
import org.bukkit.event.HandlerList;
import org.jetbrains.annotations.NotNull;

import world.bentobox.tradewinds.crime.Crime;
import world.bentobox.tradewinds.crime.Standing;

/**
 * Fired when a player's reputation changes because of a crime. Not cancellable
 * - the crime already happened; this is the notification other addons hook to
 * (chat prefixes, scoreboards, logging).
 *
 * @author tastybento
 */
public class TWReputationChangeEvent extends Event {

    private static final HandlerList HANDLERS = new HandlerList();

    private final Player player;
    private final Standing before;
    private final Standing after;
    private final int score;
    private final Crime crime;

    public TWReputationChangeEvent(Player player, Standing before, Standing after, int score, Crime crime) {
        this.player = player;
        this.before = before;
        this.after = after;
        this.score = score;
        this.crime = crime;
    }

    public Player getPlayer() {
        return player;
    }

    public Standing getBefore() {
        return before;
    }

    public Standing getAfter() {
        return after;
    }

    public int getScore() {
        return score;
    }

    public Crime getCrime() {
        return crime;
    }

    /**
     * @return true if this change moved the player between standings
     */
    public boolean isBandChange() {
        return before != after;
    }

    @Override
    public @NotNull HandlerList getHandlers() {
        return HANDLERS;
    }

    public static HandlerList getHandlerList() {
        return HANDLERS;
    }
}
