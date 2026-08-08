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
import world.bentobox.tradewinds.ocean.IslandSpec;

/**
 * Registers trading islands as unowned BentoBox islands, lazily: when the chunk
 * containing an island's center first loads, the island data object is created,
 * named, flagged per its security band, and persisted by BentoBox. Terrain is
 * NOT pasted - the chunk generator is the only source of land (spec principle
 * 6); this listener only creates the protection/data layer on top.
 *
 * @author tastybento
 */
public class OceanIslandRegistrar implements Listener {

    public static final String META_TYPE = "tradewinds-type";
    public static final String META_BAND = "tradewinds-band";

    private final TradeWinds addon;
    // Cells already handled this session - cheap re-load guard
    private final Set<Long> handled = ConcurrentHashMap.newKeySet();

    public OceanIslandRegistrar(TradeWinds addon) {
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
        addon.getOceanEngine(event.getWorld().getSeed()).islandsNear(minX + 8, minZ + 8, 8).stream()
                .filter(s -> s.centerX() >= minX && s.centerX() < minX + 16
                        && s.centerZ() >= minZ && s.centerZ() < minZ + 16)
                .forEach(s -> register(s, event));
    }

    private void register(IslandSpec spec, ChunkLoadEvent event) {
        long key = ((long) spec.cellX() << 32) ^ (spec.cellZ() & 0xFFFFFFFFL);
        if (!handled.add(key)) {
            return;
        }
        if (register(spec, event.getWorld()) == null) {
            handled.remove(key);
        }
    }

    /**
     * Register a trading island as an unowned BentoBox island: named, flagged
     * for its security band, and tagged with its type. Returns the existing
     * island if it was registered in a previous session.
     *
     * @param spec the island
     * @param world the overworld
     * @return the island, or null if registration failed
     */
    public Island register(IslandSpec spec, org.bukkit.World world) {
        Location center = new Location(world, spec.centerX() + 0.5,
                addon.getSettings().getSeaHeight() + 1.0, spec.centerZ() + 0.5);
        Island existing = addon.getIslands().getIslandAt(center).orElse(null);
        if (existing != null) {
            // Adopt an island from an earlier session: it may predate these
            // rules (or a config change), so re-apply name and flags
            if (existing.getName() == null || existing.getName().isBlank()) {
                existing.setName(spec.name());
            }
            applyBandFlags(existing, spec);
            sizeRangeBox(existing);
            return existing;
        }
        Island island = addon.getIslands().createIsland(center, null);
        if (island == null) {
            addon.logError("Could not register trading island " + spec.name() + " at " + spec.centerX() + ","
                    + spec.centerZ());
            return null;
        }
        island.setName(spec.name());
        applyBandFlags(island, spec);
        sizeRangeBox(island);
        if (island.getMetaData().isEmpty()) {
            island.setMetaData(new HashMap<>());
        }
        island.putMetaData(META_TYPE, new MetaDataValue(spec.type().name()));
        island.putMetaData(META_BAND, new MetaDataValue(spec.band().name()));
        addon.log("Registered trading island '" + spec.name() + "' (" + spec.type() + ", " + spec.band() + ") at "
                + spec.centerX() + "," + spec.centerZ());
        return island;
    }

    /**
     * Size a trading island's BentoBox range box to its protection range.
     * <p>
     * {@code createIsland} hands out the full island-distance box (1000 -> a
     * 2000-block square), which reserved the whole neighbourhood: no Stage 7
     * islet claim could fit within ~1.1km of any port. The band flag configs
     * always documented themselves as applying "inside the protection range",
     * so the small box is what the settings promised all along. Adopted
     * islands from older sessions are shrunk and re-gridded here too.
     */
    private void sizeRangeBox(Island island) {
        int protection = addon.getSettings().getIslandProtectionRange();
        if (island.getRange() != protection) {
            island.setRange(protection);
            // Re-add: the grid keeps the box from insertion time, so without
            // this the sea only frees up after a restart
            addon.getIslands().getIslandCache().addIsland(island);
            world.bentobox.bentobox.managers.IslandsManager.saveIsland(island);
        }
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
        // A port is a public market: everything is allowed at visitor rank
        // except an explicit deny list. See PortFlags - enumerating what IS
        // allowed meant finding the gaps one bug report at a time.
        setRanks(island, PortFlags.ranks(addon.getSettings().getBandHurtVillagersRank().getOrDefault(band, 0),
                Set.copyOf(addon.getSettings().getPortDeniedFlags()),
                Set.copyOf(addon.getSettings().getPortAllowedFlags())));
    }

    /**
     * Set protection-flag ranks on an island.
     * <p>
     * NOTE: {@code Island.setFlag} silently does nothing when the flag is not
     * already present in the island's flag map, and islands we create start
     * with an empty map - so every rank we set that way was being discarded.
     * Writing through the map and calling {@code setFlags} is the reliable
     * route.
     *
     * @param island the island
     * @param ranks flag to minimum rank
     */
    public static void setRanks(Island island, java.util.Map<world.bentobox.bentobox.api.flags.Flag, Integer> ranks) {
        java.util.Map<String, Integer> flags = new HashMap<>(island.getFlags());
        ranks.forEach((flag, rank) -> flags.put(flag.getID(), rank));
        island.setFlags(flags);
    }
}
