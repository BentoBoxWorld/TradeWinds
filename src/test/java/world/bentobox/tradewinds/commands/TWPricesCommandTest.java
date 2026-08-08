package world.bentobox.tradewinds.commands;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import world.bentobox.bentobox.api.commands.CompositeCommand;
import world.bentobox.bentobox.api.user.User;
import world.bentobox.tradewinds.CommonTestSetup;
import world.bentobox.tradewinds.TradeWinds;
import world.bentobox.tradewinds.dataobjects.PlayerDataManager;
import world.bentobox.tradewinds.dataobjects.TWPlayerData;
import world.bentobox.tradewinds.economy.TradeCategory;
import world.bentobox.tradewinds.ocean.OceanEngine;
import world.bentobox.tradewinds.ocean.IslandSpec;

/**
 * Tests for TWPricesCommand: the trader's logbook showing ports visited and
 * their best-paying categories.
 *
 * @author tastybento
 */
class TWPricesCommandTest extends CommonTestSetup {

    private TradeWinds addon;
    private TWPricesCommand command;
    private User user;
    private OceanEngine engine;

    @Override
    @BeforeEach
    public void setUp() throws Exception {
        super.setUp();
        addon = mock(TradeWinds.class);
        CompositeCommand parent = mock(CompositeCommand.class);
        when(parent.getAddon()).thenReturn(addon);
        when(parent.getWorld()).thenReturn(world);

        command = new TWPricesCommand(parent);
        user = User.getInstance(mockPlayer);

        // Setup ocean engine and addon mocks
        engine = mock(OceanEngine.class);
        when(addon.getOceanEngine(anyLong())).thenReturn(engine);
        when(addon.getOverWorld()).thenReturn(world);
        when(world.getSeed()).thenReturn(1234L);
    }

    @Test
    void testExecuteEmptyLogbook() {
        TWPlayerData data = new TWPlayerData(mockPlayer.getUniqueId().toString());
        PlayerDataManager playerDataManager = mock(PlayerDataManager.class);
        when(addon.getPlayerDataManager()).thenReturn(playerDataManager);
        when(playerDataManager.get(mockPlayer.getUniqueId())).thenReturn(data);

        boolean result = command.execute(user, "prices", List.of());

        assertTrue(result, "Command should return true for empty logbook");
    }

    @Test
    void testExecuteUnknownCategory() {
        TWPlayerData data = new TWPlayerData(mockPlayer.getUniqueId().toString());
        PlayerDataManager playerDataManager = mock(PlayerDataManager.class);
        when(addon.getPlayerDataManager()).thenReturn(playerDataManager);
        when(playerDataManager.get(mockPlayer.getUniqueId())).thenReturn(data);

        boolean result = command.execute(user, "prices", List.of("unknown"));

        assertFalse(result, "Command should return false for unknown category");
    }

    @Test
    void testExecuteWithValidCategory() {
        TWPlayerData data = new TWPlayerData(mockPlayer.getUniqueId().toString());
        Map<String, Integer> prices = new HashMap<>();
        prices.put(TradeCategory.METALS.name(), 100);
        data.getPriceLog().put("1,2", prices);

        PlayerDataManager playerDataManager = mock(PlayerDataManager.class);
        when(addon.getPlayerDataManager()).thenReturn(playerDataManager);
        when(playerDataManager.get(mockPlayer.getUniqueId())).thenReturn(data);

        IslandSpec island = mock(IslandSpec.class);
        when(island.name()).thenReturn("TestIsland");
        when(engine.islandInCell(1, 2)).thenReturn(Optional.of(island));

        boolean result = command.execute(user, "prices", List.of("metals"));

        assertTrue(result, "Command should return true with valid category");
    }
}
