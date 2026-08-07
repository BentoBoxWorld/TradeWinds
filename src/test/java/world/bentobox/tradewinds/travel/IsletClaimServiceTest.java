package world.bentobox.tradewinds.travel;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.HashSet;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import world.bentobox.bentobox.api.addons.AddonDescription;
import world.bentobox.bentobox.api.user.User;
import world.bentobox.bentobox.database.Database;
import world.bentobox.bentobox.database.objects.Island;
import world.bentobox.bentobox.hooks.VaultHook;
import world.bentobox.bentobox.managers.IslandsManager;
import world.bentobox.bentobox.managers.island.IslandCache;
import world.bentobox.tradewinds.CommonTestSetup;
import world.bentobox.tradewinds.Settings;
import world.bentobox.tradewinds.TradeWinds;
import world.bentobox.tradewinds.WhiteBox;
import world.bentobox.tradewinds.dataobjects.PlayerDataManager;
import world.bentobox.tradewinds.dataobjects.TWPlayerData;
import world.bentobox.tradewinds.galaxy.GalaxyConfig;
import world.bentobox.tradewinds.galaxy.GalaxyEngine;
import world.bentobox.tradewinds.galaxy.Islet;

/**
 * Tests islet claiming end to end against the real rank service and a real
 * galaxy engine - only BentoBox's island grid and the economy are mocked.
 *
 * @author tastybento
 */
class IsletClaimServiceTest extends CommonTestSetup {

    private static final long SEED = 424242L;

    private TradeWinds addon;
    private Settings settings;
    private PlayerDataManager pdm;
    private IslandCache cache;
    private VaultHook vault;
    private IsletClaimService service;
    private GalaxyEngine engine;
    private Islet islet;

    @Override
    @BeforeEach
    public void setUp() throws Exception {
        super.setUp();
        addon = mock(TradeWinds.class);
        settings = new Settings();
        when(addon.getSettings()).thenReturn(settings);
        when(addon.getPlugin()).thenReturn(plugin);
        when(addon.getOverWorld()).thenReturn(world);
        when(world.getSeed()).thenReturn(SEED);
        when(world.getHighestBlockYAt(anyInt(), anyInt())).thenReturn(75);

        // Empty galaxy (no trading islands), wild islets on at defaults
        engine = new GalaxyEngine(new GalaxyConfig(SEED, 2500, 160, 45, 0.0, 0, 5000, 70,
                GalaxyConfig.defaultTypeWeights(), null, 0.55, 75, 900));
        when(addon.getGalaxyEngine(anyLong())).thenReturn(engine);

        // Find a wild islet to stand on
        islet = findIslet();
        when(location.getBlockX()).thenReturn(islet.centerX());
        when(location.getBlockZ()).thenReturn(islet.centerZ());

        // BentoBox island grid
        cache = mock(IslandCache.class);
        when(cache.addIsland(any(Island.class))).thenReturn(true);
        when(addon.getIslands()).thenReturn(im);
        when(im.getIslandCache()).thenReturn(cache);
        when(im.getIslandAt(any())).thenReturn(Optional.empty());
        WhiteBox.setInternalState(IslandsManager.class, "handler", islandDb());

        // A named addon, for the island id prefix
        AddonDescription desc = new AddonDescription.Builder("world.bentobox.tradewinds", "TradeWinds", "1.0")
                .build();
        when(addon.getDescription()).thenReturn(desc);

        // Economy: rich enough by default
        vault = mock(VaultHook.class);
        when(plugin.getVault()).thenReturn(Optional.of(vault));
        when(vault.getBalance(any(User.class))).thenReturn(200_000.0);

        // A well-travelled claimer by default
        pdm = mock(PlayerDataManager.class);
        when(addon.getPlayerDataManager()).thenReturn(pdm);
        chart(40);

        service = new IsletClaimService(addon, new RankService(addon));
    }

    @SuppressWarnings("unchecked")
    private Database<Island> islandDb() {
        Database<Island> db = mock(Database.class);
        when(db.saveObjectAsync(any())).thenReturn(CompletableFuture.completedFuture(true));
        return db;
    }

    private Islet findIslet() {
        for (int cx = 0; cx < 50; cx++) {
            Optional<Islet> found = engine.wildIsletInCell(cx, 7);
            if (found.isPresent()) {
                return found.get();
            }
        }
        throw new IllegalStateException("No islet in 50 cells - wrong seed?");
    }

    private void chart(int count) {
        TWPlayerData data = new TWPlayerData(uuid.toString());
        Set<String> cells = IntStream.range(0, count).mapToObj(i -> i + "," + i)
                .collect(Collectors.toCollection(HashSet::new));
        data.setChartedIslands(cells);
        when(pdm.get(uuid)).thenReturn(data);
    }

    @Test
    void testClaimSucceeds() {
        IsletClaimService.Result result = service.claim(mockPlayer);
        assertTrue(result.success(), "Expected success, got " + result.refusal());
        assertNotNull(result.island());
        // Sized to the islet, not the island distance
        int expected = islet.radius() + settings.getClaimProtectionMargin();
        assertEquals(expected, result.island().getProtectionRange());
        assertEquals(expected, result.island().getRange());
        assertEquals(uuid, result.island().getOwner());
        assertTrue(result.island().getUniqueId().startsWith("TradeWinds"));
        // Marked as a claim, for the Stage 7b warp node
        assertTrue(result.island().getMetaData(IsletClaimService.META_CLAIM).isPresent());
        // Paid in full, once
        verify(vault).withdraw(any(User.class), org.mockito.ArgumentMatchers.eq(150_000.0));
    }

    @Test
    void testRefusesOffIslet() {
        // A wild-islet cell corner is never inside an islet: centers keep at
        // least a quarter-cell from the edge and the radius cannot reach it
        when(location.getBlockX()).thenReturn(900 * 20);
        when(location.getBlockZ()).thenReturn(900 * 20);
        IsletClaimService.Result result = service.claim(mockPlayer);
        assertEquals(IsletClaimService.Refusal.NOT_ON_ISLET, result.refusal());
    }

    @Test
    void testRefusesWhenAlreadyInAnIsland() {
        when(im.getIsland(world, uuid)).thenReturn(island);
        IsletClaimService.Result result = service.claim(mockPlayer);
        assertEquals(IsletClaimService.Refusal.HAS_ISLAND, result.refusal());
    }

    @Test
    void testRefusesAClaimedIslet() {
        when(im.getIslandAt(any())).thenReturn(Optional.of(island)); // owned mock island
        IsletClaimService.Result result = service.claim(mockPlayer);
        assertEquals(IsletClaimService.Refusal.ALREADY_CLAIMED, result.refusal());
    }

    @Test
    void testRefusesLowRank() {
        chart(35); // one short of Tide Captain
        IsletClaimService.Result result = service.claim(mockPlayer);
        assertEquals(IsletClaimService.Refusal.RANK_TOO_LOW, result.refusal());
        verify(vault, never()).withdraw(any(), any(Double.class));
    }

    @Test
    void testRefusesPoverty() {
        when(vault.getBalance(any(User.class))).thenReturn(149_999.0);
        IsletClaimService.Result result = service.claim(mockPlayer);
        assertEquals(IsletClaimService.Refusal.CANNOT_AFFORD, result.refusal());
        verify(vault, never()).withdraw(any(), any(Double.class));
    }

    @Test
    void testRefusesWithoutEconomy() {
        when(plugin.getVault()).thenReturn(Optional.empty());
        IsletClaimService.Result result = service.claim(mockPlayer);
        assertEquals(IsletClaimService.Refusal.NO_ECONOMY, result.refusal());
    }

    @Test
    void testGridRefusalCostsNothing() {
        when(cache.addIsland(any(Island.class))).thenReturn(false);
        IsletClaimService.Result result = service.claim(mockPlayer);
        assertEquals(IsletClaimService.Refusal.WATERS_TAKEN, result.refusal());
        assertFalse(result.success());
        // The grid said no AFTER the checks - but the money never moved
        verify(vault, never()).withdraw(any(), any(Double.class));
    }
}
