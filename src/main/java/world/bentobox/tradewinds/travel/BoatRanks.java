package world.bentobox.tradewinds.travel;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import org.bukkit.Material;

import world.bentobox.tradewinds.TradeWinds;

/**
 * The boat ladder: which boats exist as cargo ranks, how many hold slots each
 * carries, and what the shop charges. Built from config
 * ({@code boats.ranks}); rank order is slot order. The One Boat Rule means
 * this ladder IS the cargo progression - a player's hold is exactly as big as
 * the one boat they own.
 *
 * @author tastybento
 */
public class BoatRanks {

    /**
     * One rung of the ladder.
     *
     * @param material the boat item/entity material
     * @param rank 1-based position, smallest first
     * @param slots hold cargo slots this boat provides
     */
    public record Rank(Material material, int rank, int slots) {
    }

    private final TradeWinds addon;

    public BoatRanks(TradeWinds addon) {
        this.addon = addon;
    }

    /**
     * The ladder, smallest first, from config. Unknown material names are
     * skipped (logged once by Settings validation, not here - this is called
     * often).
     *
     * @return ranks in ascending slot order
     */
    public List<Rank> ladder() {
        List<Rank> ladder = new ArrayList<>();
        List<Map.Entry<String, Integer>> entries = new ArrayList<>(
                addon.getSettings().getBoatRanks().entrySet());
        entries.sort(Comparator.comparingInt(Map.Entry::getValue));
        int rank = 1;
        for (Map.Entry<String, Integer> entry : entries) {
            Material material = Material.matchMaterial(entry.getKey());
            if (material != null && entry.getValue() > 0) {
                ladder.add(new Rank(material, rank++, entry.getValue()));
            }
        }
        return ladder;
    }

    /**
     * The rank a boat material occupies, if it is on the ladder.
     */
    public Optional<Rank> rankOf(Material material) {
        return ladder().stream().filter(r -> r.material() == material).findFirst();
    }

    /**
     * Cargo slots for a boat material; 0 for null or off-ladder materials.
     */
    public int slots(Material material) {
        return material == null ? 0 : rankOf(material).map(Rank::slots).orElse(0);
    }

    /**
     * What the shop charges for a boat: quadratic in slots, so early rungs
     * are pocket change and the top rungs cost real trading profit.
     */
    public double price(Rank rank) {
        return addon.getSettings().getBoatPricePerSlotSquared() * rank.slots() * rank.slots();
    }

    /**
     * The rungs a shop at this tech level offers a player with this boat:
     * strictly bigger than what they own (upgrades only - there is no
     * downgrade and no second boat), and no higher than the island can build
     * (rank <= tech x ranks-per-tech-level). Crafting bypasses all of this.
     *
     * @param current the player's boat, or null for none
     * @param techLevel the island's tech level
     * @return purchasable rungs, smallest first
     */
    public List<Rank> shopListing(Material current, int techLevel) {
        int currentSlots = slots(current);
        int maxRank = techLevel * addon.getSettings().getBoatRanksPerTechLevel();
        // Every rung the tech can build EXCEPT the one you already sail
        // (ruled 2026-08-05): a yard always sells - a bigger hull with your
        // ship at the quay is a trade-in, anything else is bought outright
        // and your old boat is left unowned where it lies. Upgrade-only
        // listings stranded sailors whose ship outranked the local tech.
        return ladder().stream()
                .filter(r -> r.slots() != currentSlots)
                .filter(r -> r.rank() <= maxRank)
                .toList();
    }

    /**
     * Whether a material is any boat or raft item - on the ladder or not.
     */
    public static boolean isBoatItem(Material material) {
        return material != null && (material.name().endsWith("_BOAT") || material.name().endsWith("_RAFT"));
    }
}
