package world.bentobox.tradewinds.generator;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.Map;
import java.util.Set;

import org.junit.jupiter.api.Test;

import world.bentobox.bentobox.api.flags.Flag;
import world.bentobox.bentobox.lists.Flags;
import world.bentobox.bentobox.managers.RanksManager;
import world.bentobox.tradewinds.CommonTestSetup;

/**
 * The port flag policy: a trading island is a public market, so everything is
 * allowed at visitor rank except an explicit deny list.
 * <p>
 * This is the test that should have existed from Stage 2. BentoBox protection
 * flags default to MEMBER rank and nobody is ever a member of an unowned
 * island, so allow-listing meant finding each missing permission one playtest
 * bug at a time: no boat interaction, no chest boat, no workbench, no doors.
 *
 * @author tastybento
 */
class PortFlagsTest extends CommonTestSetup {

    private Map<Flag, Integer> ranks() {
        return PortFlags.ranks(RanksManager.MEMBER_RANK, Set.of(), Set.of());
    }

    @Test
    void testEveryProtectionFlagHasARulingExceptTheOwnersOwn() {
        Map<Flag, Integer> ranks = ranks();
        for (Flag flag : Flags.values()) {
            if (flag.getType() != Flag.Type.PROTECTION) {
                continue;
            }
            if (Flags.CHANGE_SETTINGS.equals(flag) || Flags.LOCK.equals(flag)) {
                // Deliberately untouched: these belong to whoever owns the island
                assertFalse(ranks.containsKey(flag), flag.getID() + " must stay the owner's");
                continue;
            }
            assertTrue(ranks.containsKey(flag),
                    "No ruling for " + flag.getID() + " - a flag left at its default is denied to everyone");
        }
    }

    @Test
    void testAPortWorksAsAPort() {
        Map<Flag, Integer> ranks = ranks();
        // Every one of these was a playtest bug report before the policy was
        // inverted. They are the difference between a port and a locked gate.
        for (Flag flag : new Flag[] { Flags.BOAT, Flags.CHEST, Flags.SHULKER_BOX, Flags.MOUNT_INVENTORY,
                Flags.CRAFTING, Flags.DOOR, Flags.GATE, Flags.TRAPDOOR, Flags.ITEM_DROP, Flags.ITEM_PICKUP,
                Flags.HURT_MONSTERS, Flags.RIDING, Flags.ANVIL, Flags.FURNACE, Flags.BED,
                Flags.BELL_RINGING, Flags.ENDER_PEARL }) {
            assertEquals(RanksManager.VISITOR_RANK, ranks.get(flag),
                    flag.getID() + " must be allowed at a port");
        }
    }

    @Test
    void testTheMarketCannotBeGriefedOrRobbed() {
        Map<Flag, Integer> ranks = ranks();
        // Anti-grief: the plaza and dock are the only hand-built terrain there is
        for (Flag flag : new Flag[] { Flags.BREAK_BLOCKS, Flags.PLACE_BLOCKS, Flags.FLINT_AND_STEEL,
                Flags.TNT_PRIMING, Flags.BUCKET, Flags.ITEM_FRAME, Flags.ARMOR_STAND }) {
            assertTrue(ranks.get(flag) > RanksManager.VISITOR_RANK, flag.getID() + " must be denied at a port");
        }
        // The two economies: money enters the game ONLY through trade margins,
        // so an island's crops, stores and livestock are not a free income
        for (Flag flag : new Flag[] { Flags.HARVEST, Flags.CONTAINER, Flags.BARREL, Flags.HOPPER,
                Flags.SHEARING, Flags.MILKING, Flags.HURT_ANIMALS, Flags.HIVE }) {
            assertTrue(ranks.get(flag) > RanksManager.VISITOR_RANK,
                    flag.getID() + " would mint value outside the market");
        }
        // Trading transacts through the hold only (spec principle 1) - a direct
        // villager trade would bypass the entire market
        assertTrue(ranks.get(Flags.TRADING) > RanksManager.VISITOR_RANK);
    }

    @Test
    void testBandControlsHurtVillagers() {
        // SAFE forbids it outright, rougher bands allow it - and Stage 6 punishes it
        assertEquals(RanksManager.MEMBER_RANK,
                PortFlags.ranks(RanksManager.MEMBER_RANK, Set.of(), Set.of()).get(Flags.HURT_VILLAGERS));
        assertEquals(RanksManager.VISITOR_RANK,
                PortFlags.ranks(RanksManager.VISITOR_RANK, Set.of(), Set.of()).get(Flags.HURT_VILLAGERS));
    }

    @Test
    void testAdminOverridesBothWays() {
        // Deny something normally allowed
        Map<Flag, Integer> stricter = PortFlags.ranks(0, Set.of(Flags.BED.getID()), Set.of());
        assertTrue(stricter.get(Flags.BED) > RanksManager.VISITOR_RANK);
        // ... and re-open something the deny list closes
        Map<Flag, Integer> looser = PortFlags.ranks(0, Set.of(), Set.of(Flags.HARVEST.getID()));
        assertEquals(RanksManager.VISITOR_RANK, looser.get(Flags.HARVEST));
        // An allow overrides a deny, so an admin can always open a port back up
        Map<Flag, Integer> both = PortFlags.ranks(0, Set.of(Flags.BED.getID()), Set.of(Flags.BED.getID()));
        assertEquals(RanksManager.VISITOR_RANK, both.get(Flags.BED));
    }

    @Test
    void testPolicyIsAllowByDefault() {
        // The point of the inversion, and the thing that must not quietly rot:
        // the policy walks Flags.values() at RUNTIME rather than naming an
        // allow list, so a flag added by a future BentoBox is open at a port on
        // day one instead of silently locking something. (The compile target,
        // BentoBox 3.18.1, already has a different flag set from core HEAD -
        // exactly the drift this protects against.)
        Map<Flag, Integer> ranks = ranks();
        long allowed = ranks.values().stream().filter(r -> r == RanksManager.VISITOR_RANK).count();
        long denied = ranks.values().stream().filter(r -> r > RanksManager.VISITOR_RANK).count();
        assertTrue(allowed > denied,
                "Denied " + denied + " of " + ranks.size() + " - is this still allow-by-default?");
        assertFalse(PortFlags.deniedIds().isEmpty());
    }
}
