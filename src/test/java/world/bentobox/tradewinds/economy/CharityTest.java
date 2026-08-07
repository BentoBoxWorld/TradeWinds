package world.bentobox.tradewinds.economy;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyDouble;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.Optional;

import org.bukkit.Material;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import world.bentobox.bentobox.hooks.VaultHook;
import world.bentobox.tradewinds.CommonTestSetup;
import world.bentobox.tradewinds.Settings;
import world.bentobox.tradewinds.TradeWinds;
import world.bentobox.tradewinds.dataobjects.IslandDataManager;
import world.bentobox.tradewinds.dataobjects.PlayerDataManager;
import world.bentobox.tradewinds.dataobjects.TWPlayerData;
import world.bentobox.tradewinds.dataobjects.BoatHold;
import world.bentobox.tradewinds.galaxy.IslandSpec;
import world.bentobox.tradewinds.galaxy.IslandType;
import world.bentobox.tradewinds.galaxy.SecurityBand;
import world.bentobox.tradewinds.travel.BoatRanks;
import world.bentobox.tradewinds.travel.BoatService;
import world.bentobox.tradewinds.travel.HoldService;

/**
 * The recovery rules under One Boat: a sailor with no boat has no hold and
 * cannot earn, so destitution must have an exit - the harbourmaster's bamboo
 * raft - and the starting balance must cover re-equipping.
 *
 * @author tastybento
 */
class CharityTest extends CommonTestSetup {

    private TradeWinds addon;
    private MarketService service;
    private HoldService hold;
    private BoatService boats;
    private Settings settings;
    private VaultHook vault;

    @Override
    @BeforeEach
    public void setUp() throws Exception {
        super.setUp();
        addon = mock(TradeWinds.class);
        settings = new Settings();
        when(addon.getSettings()).thenReturn(settings);
        when(addon.getIslandDataManager()).thenReturn(mock(IslandDataManager.class));
        when(addon.getPlugin()).thenReturn(plugin);
        when(addon.getBoatRanks()).thenReturn(new BoatRanks(addon));
        hold = mock(HoldService.class);
        when(addon.getHoldService()).thenReturn(hold);
        boats = mock(BoatService.class);
        when(addon.getBoatService()).thenReturn(boats);
        PlayerDataManager pdm = mock(PlayerDataManager.class);
        when(addon.getPlayerDataManager()).thenReturn(pdm);
        when(pdm.get(any())).thenReturn(new TWPlayerData(uuid.toString()));
        vault = mock(VaultHook.class);
        when(plugin.getVault()).thenReturn(Optional.of(vault));
        service = new MarketService(addon);
    }

    @Test
    void testStartingBalanceCoversTheFirstRaft() {
        // The smallest boat must be affordable from the starting balance, or a
        // player who loses everything early is locked out of the whole economy
        double raftPrice = settings.getBoatPricePerSlotSquared() * 2 * 2; // Bamboo raft: 2 slots
        assertTrue(raftPrice < settings.getStartingBalance(),
                "The bamboo raft must cost less than the starting balance");
    }

    @Test
    void testDestituteMeansNoBoatAndNoMoney() {
        when(vault.getBalance(any())).thenReturn(0.0);
        // No boat, no money: destitute
        when(hold.boat(mockPlayer)).thenReturn(null);
        assertTrue(service.isDestitute(mockPlayer));
        // A boat - any boat - is never destitute
        when(hold.boat(mockPlayer)).thenReturn(Material.BAMBOO_RAFT);
        assertFalse(service.isDestitute(mockPlayer));
        // Money enough for a raft is not destitute either
        when(hold.boat(mockPlayer)).thenReturn(null);
        when(vault.getBalance(any())).thenReturn(10_000.0);
        assertFalse(service.isDestitute(mockPlayer));
    }

    @Test
    void testCharityGrantsTheBambooRaft() {
        when(vault.getBalance(any())).thenReturn(0.0);
        when(hold.boat(mockPlayer)).thenReturn(null);
        assertTrue(service.claimCharity(mockPlayer));
        verify(boats).createFor(mockPlayer, Material.BAMBOO_RAFT);
        // Charity never hands out money
        verify(vault, org.mockito.Mockito.never()).deposit(any(), anyDouble());
    }

    @Test
    void testBuyingWithNoBoatActuallyHandsOneOver() {
        // Playtest 2026-08-02: a boatless player paid $100 for a Bamboo Raft
        // and got nothing - the purchase only knew how to refit a record that
        // already existed, so with no boat it took the money and did nothing.
        when(hold.boat(mockPlayer)).thenReturn(null);
        when(hold.active(any())).thenReturn(java.util.Optional.empty());
        when(vault.has(any(), org.mockito.ArgumentMatchers.anyDouble())).thenReturn(true);
        var raft = new BoatRanks(addon).ladder().get(0);
        BoatHold boatRecord = new BoatHold("test");
        boatRecord.setMaterial(Material.BAMBOO_RAFT.name());
        when(boats.createFor(mockPlayer, Material.BAMBOO_RAFT)).thenReturn(boatRecord);

        assertTrue(service.buyBoat(mockPlayer, spec(), raft));

        verify(boats).createFor(mockPlayer, Material.BAMBOO_RAFT);
        verify(boats).giveBoatItem(mockPlayer, boatRecord);
        verify(vault).withdraw(any(), org.mockito.ArgumentMatchers.eq(1000.0));
    }

    private IslandSpec spec() {
        return new IslandSpec(0, 0, 0, 0, IslandType.FISHING, SecurityBand.SAFE, "minecraft:plains", "Test", 3);
    }

    @Test
    void testCharityRefusedWhenNotDestitute() {
        when(vault.getBalance(any())).thenReturn(0.0);
        when(hold.boat(mockPlayer)).thenReturn(Material.OAK_BOAT);
        assertFalse(service.claimCharity(mockPlayer));
        verify(boats, org.mockito.Mockito.never()).createFor(any(), any());
    }
}
