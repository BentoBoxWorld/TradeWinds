package world.bentobox.tradewinds.travel;

import java.util.Optional;
import java.util.UUID;

import world.bentobox.bentobox.api.user.User;
import world.bentobox.bentobox.database.objects.Island;
import world.bentobox.tradewinds.TradeWinds;
import world.bentobox.tradewinds.ocean.IslandSpec;
import world.bentobox.tradewinds.ocean.IslandType;
import world.bentobox.tradewinds.ocean.SecurityBand;

/**
 * A claimed islet seen through the travel system's eyes.
 * <p>
 * Everything about warping is shaped around {@link IslandSpec} - destinations,
 * route costs, arrival geometry, the interstice's owed jump - so a member's
 * claimed island travels as a <b>synthetic spec</b>: its wild-grid cell, its
 * real centre, and a sentinel tech level ({@link #HOME_TECH}) that marks it as
 * a home wherever the difference matters (no dock to aim at, its own dialog
 * labels). Only members ever see one, which is what makes the claim a
 * member-only warp node.
 *
 * @author tastybento
 */
public final class HomePort {

    /** Sentinel tech level marking a synthetic home spec. */
    public static final int HOME_TECH = -1;

    private HomePort() {
        // Static use only
    }

    /**
     * Whether a spec is a synthetic home rather than a trading island.
     */
    public static boolean isHome(IslandSpec spec) {
        return spec.techLevel() == HOME_TECH;
    }

    /**
     * The claimed island a player belongs to, if any.
     */
    public static Optional<Island> islandOf(TradeWinds addon, UUID playerId) {
        Island island = addon.getIslands().getIsland(addon.getOverWorld(), playerId);
        if (island == null || !island.getMemberSet().contains(playerId)
                || island.getMetaData(IsletClaimService.META_CLAIM).isEmpty()) {
            return Optional.empty();
        }
        return Optional.of(island);
    }

    /**
     * The player's home as a warp destination, if they have one. The name is
     * the island's own if an admin gave it one, else the localized "Home".
     */
    public static Optional<IslandSpec> specFor(TradeWinds addon, User user) {
        return islandOf(addon, user.getUniqueId()).map(island -> {
            int centerX = island.getCenter().getBlockX();
            int centerZ = island.getCenter().getBlockZ();
            int grid = Math.max(1, addon.getSettings().getWildIsletGrid());
            String name = island.getName() != null && !island.getName().isBlank() ? island.getName()
                    : user.getTranslation("tradewinds.home.port-name");
            return new IslandSpec(Math.floorDiv(centerX, grid), Math.floorDiv(centerZ, grid),
                    centerX, centerZ, IslandType.FISHING, bandAt(addon, centerX, centerZ), "", name, HOME_TECH);
        });
    }

    /**
     * The security band a position sits in, from plain distance to spawn -
     * cosmetic here (home dialogs use their own labels), but honest enough
     * for anything that colors by band.
     */
    private static SecurityBand bandAt(TradeWinds addon, int x, int z) {
        int bandRadius = Math.max(1, addon.getSettings().getBandRadius());
        int step = (int) (Math.hypot(x, z) / bandRadius);
        return SecurityBand.values()[Math.clamp(step, 0, SecurityBand.values().length - 1)];
    }
}
