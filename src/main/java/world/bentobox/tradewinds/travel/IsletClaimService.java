package world.bentobox.tradewinds.travel;

import java.util.HashMap;
import java.util.Optional;
import java.util.UUID;

import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.entity.Player;

import world.bentobox.bentobox.api.events.island.IslandEvent;
import world.bentobox.bentobox.api.metadata.MetaDataValue;
import world.bentobox.bentobox.api.user.User;
import world.bentobox.bentobox.database.objects.Island;
import world.bentobox.bentobox.hooks.VaultHook;
import world.bentobox.bentobox.managers.IslandsManager;
import world.bentobox.tradewinds.TradeWinds;
import world.bentobox.tradewinds.ocean.OceanEngine;
import world.bentobox.tradewinds.ocean.Islet;

/**
 * Claiming a wild islet: Stage 7's ownership. The land already exists - the
 * generator made it - so a claim registers a real BentoBox island over it, no
 * blueprint, no paste. First claimed, first owned: pillaged, wrecked or
 * built-on makes no difference, and everything on the islet comes with it.
 * <p>
 * The claim is gated twice: by Seafarer rank (earned by charting - money
 * cannot buy it) and by price (config, above the top boat - the boat ladder
 * comes first). Team members need neither: the claimer recruits whoever they
 * like through the ordinary BentoBox team commands.
 * <p>
 * Claimed islands get a SMALL range box - the islet's radius plus the config
 * margin - unlike trading islands' full-distance boxes. BentoBox supports the
 * mix ({@code isEnforceEqualRanges} is false), and it is what lets a base sit
 * a few hundred blocks from a port without the grid refusing it.
 *
 * @author tastybento
 */
public class IsletClaimService {

    /** Island metadata key marking a claimed islet: "cellX,cellZ,radius". */
    public static final String META_CLAIM = "tradewinds-claim";

    /** Why a claim was refused. */
    public enum Refusal {
        NOT_ON_ISLET, ALREADY_CLAIMED, HAS_ISLAND, RANK_TOO_LOW, NO_ECONOMY, CANNOT_AFFORD, WATERS_TAKEN
    }

    /** The outcome: a refusal, or the claimed island. */
    public record Result(Refusal refusal, Islet islet, Island island) {
        public boolean success() {
            return refusal == null;
        }
    }

    private final TradeWinds addon;
    private final RankService ranks;

    public IsletClaimService(TradeWinds addon, RankService ranks) {
        this.addon = addon;
        this.ranks = ranks;
    }

    /**
     * Try to claim the wild islet the player is standing on.
     *
     * @param player the would-be owner, in the overworld
     * @return the result
     */
    public Result claim(Player player) {
        World world = addon.getOverWorld();
        OceanEngine engine = addon.getOceanEngine(world.getSeed());
        Optional<Islet> standing = engine.isletAt(player.getLocation().getBlockX(),
                player.getLocation().getBlockZ());
        if (standing.isEmpty()) {
            return new Result(Refusal.NOT_ON_ISLET, null, null);
        }
        Islet islet = standing.get();
        IslandsManager islands = addon.getIslands();
        // One island per player, the BentoBox rule - membership counts
        if (islands.getIsland(world, player.getUniqueId()) != null) {
            return new Result(Refusal.HAS_ISLAND, islet, null);
        }
        Location center = new Location(world, islet.centerX() + 0.5,
                world.getHighestBlockYAt(islet.centerX(), islet.centerZ()) + 1.0, islet.centerZ() + 0.5);
        // A previous claim over this islet refuses outright; an unowned
        // trading island's box is handled by the grid insert below
        Optional<Island> at = islands.getIslandAt(center);
        if (at.isPresent() && at.get().getOwner() != null) {
            return new Result(Refusal.ALREADY_CLAIMED, islet, null);
        }
        if (ranks.chartedCount(player.getUniqueId()) < ranks.claimThreshold()) {
            return new Result(Refusal.RANK_TOO_LOW, islet, null);
        }
        Optional<VaultHook> vault = addon.getPlugin().getVault();
        if (vault.isEmpty()) {
            addon.logError("Islet claim refused: no Vault economy is installed");
            return new Result(Refusal.NO_ECONOMY, islet, null);
        }
        User user = User.getInstance(player);
        double price = addon.getSettings().getClaimPrice();
        if (vault.get().getBalance(user) < price) {
            return new Result(Refusal.CANNOT_AFFORD, islet, null);
        }
        Island island = register(islet, center, player.getUniqueId());
        if (island == null) {
            return new Result(Refusal.WATERS_TAKEN, islet, null);
        }
        // Withdraw only after the grid accepted the island - the insert is the
        // one step that can still refuse, and a refund path is a bug farm
        vault.get().withdraw(user, price);
        return new Result(null, islet, island);
    }

    /**
     * Register the claim as a BentoBox island. Mimics
     * {@code IslandsManager.createIsland} but sizes the range box to the islet
     * BEFORE the grid insert - createIsland would insert at the full island
     * distance and collide with the nearest port's box.
     */
    private Island register(Islet islet, Location center, UUID owner) {
        int protection = islet.radius() + addon.getSettings().getClaimProtectionMargin();
        Island island = new Island(center, owner, protection);
        String gmName = addon.getDescription().getName();
        island.setGameMode(gmName);
        island.setUniqueId(gmName + island.getUniqueId());
        island.setRange(protection);
        if (!addon.getIslands().getIslandCache().addIsland(island)) {
            return null;
        }
        if (island.getMetaData().isEmpty()) {
            island.setMetaData(new HashMap<>());
        }
        int grid = addon.getSettings().getWildIsletGrid();
        island.putMetaData(META_CLAIM, new MetaDataValue(Math.floorDiv(islet.centerX(), grid) + ","
                + Math.floorDiv(islet.centerZ(), grid) + "," + islet.radius()));
        IslandsManager.saveIsland(island);
        // The ecosystem hook Bank and friends listen for - a claim is a
        // registration of existing land, the admin-register shape
        IslandEvent.builder().island(island).location(center).involvedPlayer(owner)
                .reason(IslandEvent.Reason.REGISTERED).build();
        return island;
    }
}
