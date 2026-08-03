package world.bentobox.tradewinds.dataobjects;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.util.HashMap;
import java.util.Map;

import world.bentobox.bentobox.database.Database;
import world.bentobox.tradewinds.TradeWinds;

/**
 * A real {@link HoldManager} over an in-memory database - so tests exercise
 * the actual ownership rules (who loses a boat, what becomes an OLD BOAT)
 * instead of a hand-written stand-in that can quietly disagree with them.
 *
 * @author tastybento
 */
public final class TestHoldManager {

    private TestHoldManager() {
        // Static use only
    }

    /**
     * @param addon a mocked addon (its PlayerDataManager must already work)
     * @return a real manager backed by a map
     */
    @SuppressWarnings("unchecked")
    public static HoldManager create(TradeWinds addon) {
        Map<String, BoatHold> stored = new HashMap<>();
        Database<BoatHold> db = mock(Database.class);
        when(db.objectExists(anyString())).thenAnswer(inv -> stored.containsKey(inv.<String>getArgument(0)));
        when(db.loadObject(anyString())).thenAnswer(inv -> stored.get(inv.<String>getArgument(0)));
        when(db.loadObjects()).thenAnswer(inv -> new java.util.ArrayList<>(stored.values()));
        doAnswer(inv -> {
            BoatHold hold = inv.getArgument(0);
            stored.put(hold.getUniqueId(), hold);
            return java.util.concurrent.CompletableFuture.completedFuture(true);
        }).when(db).saveObjectAsync(any(BoatHold.class));
        doAnswer(inv -> {
            stored.remove(inv.<String>getArgument(0));
            return null;
        }).when(db).deleteID(anyString());
        return new HoldManager(addon, db);
    }
}
