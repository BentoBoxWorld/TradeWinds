package world.bentobox.tradewinds.generator;

import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import world.bentobox.bentobox.api.flags.Flag;
import world.bentobox.bentobox.lists.Flags;
import world.bentobox.bentobox.managers.RanksManager;

/**
 * What a visitor may do on a trading island.
 * <p>
 * BentoBox protection flags default to MEMBER rank, and <b>nobody is ever a
 * member of an unowned island</b> - so on a trading island every one of the 90
 * flags that takes the default is denied to every player, including the ones
 * that make a port a port. That was invisible while testing as an operator and
 * surfaced one bug report at a time: no boat interaction, no chest boat, no
 * workbench, no doors.
 * <p>
 * So the policy is inverted here. A trading island is a <b>public market</b>:
 * everything is allowed at visitor rank <em>except</em> an explicit deny list,
 * and the deny list is the design statement - the things that would wreck the
 * one hand-built part of the world, or let a player earn money without trading
 * for it.
 *
 * @author tastybento
 */
public final class PortFlags {

    private PortFlags() {
        // Static use only
    }

    /**
     * Denied to everyone on a trading island. Grouped by why, because the "why"
     * is the part worth keeping.
     */
    private static final List<Flag> DENIED = List.of(
            // 1. Anti-grief: the plaza, dock, stalls and landmarks are the only
            // hand-built terrain in the world
            Flags.BREAK_BLOCKS, Flags.PLACE_BLOCKS, Flags.BREAK_SPAWNERS, Flags.BREAK_HOPPERS,
            Flags.FLINT_AND_STEEL, Flags.TNT_PRIMING, Flags.BUCKET, Flags.COLLECT_LAVA,
            Flags.ITEM_FRAME, Flags.ARMOR_STAND, Flags.SIGN_EDITING, Flags.SPAWN_EGGS, Flags.DRAGON_EGG,

            // 2. The two economies (spec 5.0): money enters the game ONLY through
            // trade margins. Harvesting an island's crops, robbing its stores or
            // shearing its livestock would all mint value out of nothing.
            Flags.HARVEST, Flags.CROP_PLANTING, Flags.CROP_TRAMPLE, Flags.HIVE,
            Flags.CONTAINER, Flags.BARREL, Flags.TRAPPED_CHEST, Flags.HOPPER, Flags.DROPPER,
            Flags.DISPENSER, Flags.CRAFTER, Flags.LECTERN, Flags.BOOKSHELF, Flags.BEACON,

            // 3. Trading transacts through the market only (spec principle 1).
            // Direct villager trades would bypass the hold entirely.
            Flags.TRADING,

            // 4. The island's residents and livestock are not a larder
            Flags.HURT_ANIMALS, Flags.HURT_TAMED_ANIMALS, Flags.SHEARING, Flags.MILKING,
            Flags.BREEDING, Flags.LEASH, Flags.NAME_TAG, Flags.DYE, Flags.EGGS, Flags.TURTLE_EGGS,

            // 5. No portals out of the ocean
            Flags.NETHER_PORTAL, Flags.END_PORTAL);

    /**
     * Flags whose rank the security band decides, so the blanket policy must
     * not overwrite them.
     */
    private static final Set<Flag> BAND_CONTROLLED = Set.of(Flags.HURT_VILLAGERS);

    /**
     * Every protection flag TradeWinds has an opinion about, mapped to the
     * minimum rank required. Visitor rank (0) means "anyone"; member rank means
     * nobody, on an island no one can join.
     *
     * @param hurtVillagersRank the band's rank for hurting villagers
     * @param extraDenied flag IDs an admin has additionally denied
     * @param extraAllowed flag IDs an admin has forced back to visitor rank
     * @return flag to rank
     */
    public static Map<Flag, Integer> ranks(int hurtVillagersRank, Set<String> extraDenied,
            Set<String> extraAllowed) {
        Set<String> denied = new HashSet<>(DENIED.stream().map(Flag::getID).toList());
        if (extraDenied != null) {
            denied.addAll(extraDenied);
        }
        if (extraAllowed != null) {
            denied.removeAll(extraAllowed);
        }
        Map<Flag, Integer> ranks = new HashMap<>();
        for (Flag flag : Flags.values()) {
            if (flag.getType() == Flag.Type.PROTECTION && !BAND_CONTROLLED.contains(flag)
                    && !Flags.CHANGE_SETTINGS.equals(flag) && !Flags.LOCK.equals(flag)) {
                ranks.put(flag, denied.contains(flag.getID()) ? RanksManager.MEMBER_RANK : RanksManager.VISITOR_RANK);
            }
        }
        ranks.put(Flags.HURT_VILLAGERS, hurtVillagersRank);
        return ranks;
    }

    /**
     * The default deny list, for documentation and admin commands.
     *
     * @return flag IDs denied to visitors at a port
     */
    public static List<String> deniedIds() {
        return DENIED.stream().map(Flag::getID).toList();
    }
}
