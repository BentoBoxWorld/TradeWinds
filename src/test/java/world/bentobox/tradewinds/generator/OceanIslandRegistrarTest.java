package world.bentobox.tradewinds.generator;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.HashMap;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;

import org.bukkit.Chunk;
import org.bukkit.Location;
import org.bukkit.event.world.ChunkLoadEvent;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import world.bentobox.bentobox.database.Database;
import world.bentobox.bentobox.database.objects.Island;
import world.bentobox.bentobox.lists.Flags;
import world.bentobox.bentobox.managers.IslandsManager;
import world.bentobox.bentobox.managers.island.IslandCache;
import world.bentobox.tradewinds.CommonTestSetup;
import world.bentobox.tradewinds.Settings;
import world.bentobox.tradewinds.TradeWinds;
import world.bentobox.tradewinds.WhiteBox;
import world.bentobox.tradewinds.ocean.OceanConfig;
import world.bentobox.tradewinds.ocean.OceanEngine;
import world.bentobox.tradewinds.ocean.IslandSpec;

/**
 * Tests {@link OceanIslandRegistrar}: island center chunks register unowned,
 * named, band-flagged BentoBox islands exactly once.
 *
 * @author tastybento
 */
class OceanIslandRegistrarTest extends CommonTestSetup {

    private static final long SEED = 31337L;

    private TradeWinds addon;
    private OceanEngine engine;
    private OceanIslandRegistrar registrar;
    private IslandSpec spec;

    @Override
    @BeforeEach
    public void setUp() throws Exception {
        super.setUp();
        addon = mock(TradeWinds.class);
        Settings settings = new Settings();
        when(addon.getSettings()).thenReturn(settings);
        engine = new OceanEngine(new OceanConfig(SEED, 2500, 160, 45, 1.0, 0, 5000, 70));
        when(addon.getOceanEngine(anyLong())).thenReturn(engine);
        when(addon.getOverWorld()).thenReturn(world);
        when(addon.getIslands()).thenReturn(im);
        when(world.getSeed()).thenReturn(SEED);
        // The range-box shrink re-grids through the cache and saves statically
        IslandCache cache = mock(IslandCache.class);
        when(cache.addIsland(any(Island.class))).thenReturn(true);
        when(im.getIslandCache()).thenReturn(cache);
        @SuppressWarnings("unchecked")
        Database<Island> db = mock(Database.class);
        when(db.saveObjectAsync(any())).thenReturn(CompletableFuture.completedFuture(true));
        WhiteBox.setInternalState(IslandsManager.class, "handler", db);
        registrar = new OceanIslandRegistrar(addon);
        spec = engine.islandInCell(0, 0).orElseThrow();
    }

    private ChunkLoadEvent chunkLoad(int chunkX, int chunkZ) {
        Chunk chunk = mock(Chunk.class);
        when(chunk.getX()).thenReturn(chunkX);
        when(chunk.getZ()).thenReturn(chunkZ);
        when(chunk.getWorld()).thenReturn(world);
        return new ChunkLoadEvent(chunk, true);
    }

    @Test
    void testRegistersIslandOnCenterChunkLoad() {
        when(im.getIslandAt(any())).thenReturn(Optional.empty());
        when(im.createIsland(any(), isNull())).thenReturn(island);
        when(island.getMetaData()).thenReturn(Optional.of(new HashMap<>()));
        when(island.getFlags()).thenReturn(new HashMap<>());

        registrar.onChunkLoad(chunkLoad(spec.centerX() >> 4, spec.centerZ() >> 4));

        ArgumentCaptor<Location> loc = ArgumentCaptor.forClass(Location.class);
        verify(im).createIsland(loc.capture(), isNull());
        assertEquals(spec.centerX() + 0.5, loc.getValue().getX());
        assertEquals(spec.centerZ() + 0.5, loc.getValue().getZ());
        verify(island).setName(spec.name());
        // The range box is the protection range, not the island distance -
        // the full-distance box reserved ~1.1km around every port and no
        // islet claim could ever fit near one
        verify(island).setRange(new Settings().getIslandProtectionRange());
        // Band policy: PvP, hostile spawning, villager protection
        Settings settings = new Settings();
        verify(island).setSettingsFlag(Flags.PVP_OVERWORLD,
                settings.getBandPvp().get(spec.band().name()));
        verify(island).setSettingsFlag(Flags.MONSTER_NATURAL_SPAWN,
                settings.getBandMonsterSpawn().get(spec.band().name()));
        // Rank flags go through setFlags: core's setFlag ignores absent keys
        ArgumentCaptor<java.util.Map<String, Integer>> flags = ArgumentCaptor.forClass(java.util.Map.class);
        verify(island).setFlags(flags.capture());
        assertEquals(settings.getBandHurtVillagersRank().get(spec.band().name()),
                flags.getValue().get(Flags.HURT_VILLAGERS.getID()));
        assertEquals(0, flags.getValue().get(Flags.ITEM_DROP.getID()));
        assertEquals(0, flags.getValue().get(Flags.ITEM_PICKUP.getID()));
        // Every trading island is a port: visitors must be able to use boats,
        // or they cannot get back into their own boat after shopping
        assertEquals(0, flags.getValue().get(Flags.BOAT.getID()));
        assertEquals(0, flags.getValue().get(Flags.HURT_MONSTERS.getID()));
        assertEquals(0, flags.getValue().get(Flags.CRAFTING.getID()));
        assertEquals(0, flags.getValue().get(Flags.DOOR.getID()));
        assertEquals(0, flags.getValue().get(Flags.GATE.getID()));
    }

    @Test
    void testRankFlagsSurviveAnEmptyFlagMap() {
        // The bug: islands are created with an empty flag map, and core's
        // Island.setFlag silently drops writes for keys that are not present
        Island fresh = mock(Island.class);
        when(fresh.getFlags()).thenReturn(new HashMap<>());
        OceanIslandRegistrar.setRanks(fresh, java.util.Map.of(Flags.BOAT, 0));
        ArgumentCaptor<java.util.Map<String, Integer>> flags = ArgumentCaptor.forClass(java.util.Map.class);
        verify(fresh).setFlags(flags.capture());
        assertEquals(0, flags.getValue().get(Flags.BOAT.getID()));
    }

    @Test
    void testDoesNotRegisterTwice() {
        when(im.getIslandAt(any())).thenReturn(Optional.empty());
        when(im.createIsland(any(), isNull())).thenReturn(island);
        when(island.getMetaData()).thenReturn(Optional.of(new HashMap<>()));

        registrar.onChunkLoad(chunkLoad(spec.centerX() >> 4, spec.centerZ() >> 4));
        registrar.onChunkLoad(chunkLoad(spec.centerX() >> 4, spec.centerZ() >> 4));
        verify(im).createIsland(any(), isNull()); // exactly once
    }

    @Test
    void testSkipsAlreadyPersistedIsland() {
        // Island already in the database from a previous session
        when(im.getIslandAt(any())).thenReturn(Optional.of(island));
        registrar.onChunkLoad(chunkLoad(spec.centerX() >> 4, spec.centerZ() >> 4));
        verify(im, never()).createIsland(any(), any());
    }

    @Test
    void testIgnoresChunksWithoutIslandCenter() {
        // A chunk two cells away holds no island center
        registrar.onChunkLoad(chunkLoad((spec.centerX() >> 4) + 40, (spec.centerZ() >> 4) + 40));
        verify(im, never()).createIsland(any(), any());
    }

    @Test
    void testIgnoresOtherWorlds() {
        Chunk chunk = mock(Chunk.class);
        org.bukkit.World other = mock(org.bukkit.World.class);
        when(chunk.getWorld()).thenReturn(other);
        registrar.onChunkLoad(new ChunkLoadEvent(chunk, true));
        verify(im, never()).createIsland(any(), any());
    }
}
