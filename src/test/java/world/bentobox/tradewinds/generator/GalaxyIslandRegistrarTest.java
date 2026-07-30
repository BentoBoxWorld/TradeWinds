package world.bentobox.tradewinds.generator;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.HashMap;
import java.util.Optional;

import org.bukkit.Chunk;
import org.bukkit.Location;
import org.bukkit.event.world.ChunkLoadEvent;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import world.bentobox.bentobox.lists.Flags;
import world.bentobox.tradewinds.CommonTestSetup;
import world.bentobox.tradewinds.Settings;
import world.bentobox.tradewinds.TradeWinds;
import world.bentobox.tradewinds.galaxy.GalaxyConfig;
import world.bentobox.tradewinds.galaxy.GalaxyEngine;
import world.bentobox.tradewinds.galaxy.IslandSpec;

/**
 * Tests {@link GalaxyIslandRegistrar}: island center chunks register unowned,
 * named, band-flagged BentoBox islands exactly once.
 *
 * @author tastybento
 */
class GalaxyIslandRegistrarTest extends CommonTestSetup {

    private static final long SEED = 31337L;

    private TradeWinds addon;
    private GalaxyEngine engine;
    private GalaxyIslandRegistrar registrar;
    private IslandSpec spec;

    @Override
    @BeforeEach
    public void setUp() throws Exception {
        super.setUp();
        addon = mock(TradeWinds.class);
        Settings settings = new Settings();
        when(addon.getSettings()).thenReturn(settings);
        engine = new GalaxyEngine(new GalaxyConfig(SEED, 2500, 160, 45, 1.0, 0, 5000, 70));
        when(addon.getGalaxyEngine(anyLong())).thenReturn(engine);
        when(addon.getOverWorld()).thenReturn(world);
        when(addon.getIslands()).thenReturn(im);
        when(world.getSeed()).thenReturn(SEED);
        registrar = new GalaxyIslandRegistrar(addon);
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

        registrar.onChunkLoad(chunkLoad(spec.centerX() >> 4, spec.centerZ() >> 4));

        ArgumentCaptor<Location> loc = ArgumentCaptor.forClass(Location.class);
        verify(im).createIsland(loc.capture(), isNull());
        assertEquals(spec.centerX() + 0.5, loc.getValue().getX());
        assertEquals(spec.centerZ() + 0.5, loc.getValue().getZ());
        verify(island).setName(spec.name());
        // Band policy: PvP, hostile spawning, villager protection
        Settings settings = new Settings();
        verify(island).setSettingsFlag(Flags.PVP_OVERWORLD,
                settings.getBandPvp().get(spec.band().name()));
        verify(island).setSettingsFlag(Flags.MONSTER_NATURAL_SPAWN,
                settings.getBandMonsterSpawn().get(spec.band().name()));
        verify(island).setFlag(Flags.HURT_VILLAGERS,
                settings.getBandHurtVillagersRank().get(spec.band().name()));
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
