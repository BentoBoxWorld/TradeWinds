package world.bentobox.tradewinds;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

import org.bukkit.Material;

import world.bentobox.tradewinds.dataobjects.BoatHold;
import world.bentobox.tradewinds.dataobjects.HoldManager;
import world.bentobox.tradewinds.dataobjects.PlayerDataManager;
import world.bentobox.tradewinds.dataobjects.TWPlayerData;
import world.bentobox.tradewinds.dataobjects.TestHoldManager;

/**
 * Boat plumbing for tests: a REAL {@link HoldManager} over an in-memory
 * database, and a real player-data store behind it. The ownership rules (who
 * loses a boat when one is taken, what becomes an OLD BOAT) are the part that
 * has actually gone wrong in play, so tests must run the real thing rather
 * than a stand-in that can drift from it.
 *
 * @author tastybento
 */
public final class TestHolds {

    private final Map<UUID, TWPlayerData> players = new HashMap<>();
    private HoldManager manager;

    private TestHolds() {
        // Built via install()
    }

    /**
     * Wire an in-memory hold manager and player store into a mocked addon.
     *
     * @param addon the mocked addon
     * @return the helper, for handing out boats
     */
    public static TestHolds install(TradeWinds addon) {
        TestHolds holds = new TestHolds();
        PlayerDataManager pdm = mock(PlayerDataManager.class);
        when(pdm.get(any(UUID.class))).thenAnswer(inv -> holds.players
                .computeIfAbsent(inv.getArgument(0), id -> new TWPlayerData(id.toString())));
        when(pdm.findByOldBoat(any())).thenAnswer(inv -> {
            String boatId = inv.getArgument(0);
            return holds.players.entrySet().stream()
                    .filter(e -> boatId != null && boatId.equals(e.getValue().getOldBoat()))
                    .map(Map.Entry::getKey).findFirst();
        });
        when(addon.getPlayerDataManager()).thenReturn(pdm);
        holds.manager = TestHoldManager.create(addon);
        when(addon.getHoldManager()).thenReturn(holds.manager);
        return holds;
    }

    /**
     * Give a player a boat of this material and make it active.
     *
     * @param playerId the player
     * @param material the boat
     * @return the record
     */
    public BoatHold giveBoat(UUID playerId, Material material) {
        BoatHold hold = manager.create(material, playerId);
        players.computeIfAbsent(playerId, id -> new TWPlayerData(id.toString()))
                .setActiveBoat(hold.getUniqueId());
        return hold;
    }

    /**
     * Take away whatever boat a player has, without abandoning it.
     */
    public void clearBoat(UUID playerId) {
        manager.clearActiveBoat(playerId);
    }

    /**
     * A loose boat nobody owns - salvage for the taking.
     */
    public BoatHold unownedBoat(Material material) {
        return manager.create(material, null);
    }

    public HoldManager manager() {
        return manager;
    }
}
