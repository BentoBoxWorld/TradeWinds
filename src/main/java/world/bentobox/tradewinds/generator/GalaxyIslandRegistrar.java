package world.bentobox.tradewinds.generator;

import java.util.HashMap;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

import org.bukkit.Location;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.world.ChunkLoadEvent;

import world.bentobox.bentobox.api.metadata.MetaDataValue;
import world.bentobox.bentobox.database.objects.Island;
import world.bentobox.bentobox.lists.Flags;
import world.bentobox.tradewinds.TradeWinds;
import world.bentobox.tradewinds.galaxy.IslandSpec;

/**
 * Registers trading islands as unowned BentoBox islands, lazily: when the chunk
 * containing an island's center first loads, the island data object is created,
 * named, flagged per its security band, and persisted by BentoBox. Terrain is
 * NOT pasted - the chunk generator is the only source of land (spec principle
 * 6); this listener only creates the protection/data layer on top.
 *
 * @author tastybento
 */
public class GalaxyIslandRegistrar implements Listener {

    public static final String META_TYPE = "tradewinds-type";
    public static final String META_BAND = "tradewinds-band";

    private final TradeWinds addon;
    // Cells already handled this session - cheap re-load guard
    private final Set<Long> handled = ConcurrentHashMap.newKeySet();

    public GalaxyIslandRegistrar(TradeWinds addon) {
        this.addon = addon;
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onChunkLoad(ChunkLoadEvent event) {
        if (!event.getWorld().equals(addon.getOverWorld())) {
            return;
        }
        int minX = event.getChunk().getX() << 4;
        int minZ = event.getChunk().getZ() << 4;
        // Islands whose center falls inside this chunk (searching the chunk's
        // center with an 8-block box covers exactly the chunk)
        addon.getGalaxyEngine(event.getWorld().getSeed()).islandsNear(minX + 8, minZ + 8, 8).stream()
                .filter(s -> s.centerX() >= minX && s.centerX() < minX + 16
                        && s.centerZ() >= minZ && s.centerZ() < minZ + 16)
                .forEach(s -> register(s, event));
    }

    private void register(IslandSpec spec, ChunkLoadEvent event) {
        long key = ((long) spec.cellX() << 32) ^ (spec.cellZ() & 0xFFFFFFFFL);
        if (!handled.add(key)) {
            return;
        }
        Location center = new Location(event.getWorld(), spec.centerX() + 0.5,
                addon.getSettings().getSeaHeight() + 1.0, spec.centerZ() + 0.5);
        if (addon.getIslands().getIslandAt(center).isPresent()) {
            // Already registered in a previous session
            return;
        }
        Island island = addon.getIslands().createIsland(center, null);
        if (island == null) {
            addon.logError("Could not register trading island " + spec.name() + " at " + spec.centerX() + ","
                    + spec.centerZ());
            handled.remove(key);
            return;
        }
        island.setName(spec.name());
        applyBandFlags(island, spec);
        if (island.getMetaData().isEmpty()) {
            island.setMetaData(new HashMap<>());
        }
        island.putMetaData(META_TYPE, new MetaDataValue(spec.type().name()));
        island.putMetaData(META_BAND, new MetaDataValue(spec.band().name()));
        addon.log("Registered trading island '" + spec.name() + "' (" + spec.type() + ", " + spec.band() + ") at "
                + spec.centerX() + "," + spec.centerZ());
    }

    /**
     * Apply the security band's configured flags to an island: PvP, hostile
     * spawning within the protection range, and the hurt-villagers rank
     * (SAFE prevents it outright; lower bands allow it - crime is Stage 6's
     * problem).
     */
    public void applyBandFlags(Island island, IslandSpec spec) {
        String band = spec.band().name();
        island.setSettingsFlag(Flags.PVP_OVERWORLD,
                addon.getSettings().getBandPvp().getOrDefault(band, spec.band().isPvp()));
        island.setSettingsFlag(Flags.MONSTER_NATURAL_SPAWN,
                addon.getSettings().getBandMonsterSpawn().getOrDefault(band, true));
        island.setFlag(Flags.HURT_VILLAGERS, addon.getSettings().getBandHurtVillagersRank().getOrDefault(band, 0));
        // Everyone may drop and pick up items on trading islands - trade,
        // jettisoned cargo, and plain convenience all depend on it
        island.setFlag(Flags.ITEM_DROP, 0);
        island.setFlag(Flags.ITEM_PICKUP, 0);
    }
}
