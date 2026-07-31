package world.bentobox.tradewinds.encounters;

import java.util.List;

import org.bukkit.entity.EntityType;

import world.bentobox.tradewinds.galaxy.SecurityBand;

/**
 * The things that find you on the open sea. Each encounter names its mobs, the
 * time it hunts, the minimum lawlessness that lets it happen, and how many
 * turn up.
 *
 * @author tastybento
 */
public enum EncounterType {

    /** Slow swimmers that block the way ahead rather than chase - daytime nuisance. */
    GUARDIAN_PICKET(List.of(EntityType.GUARDIAN), Time.DAY, SecurityBand.POLICED, 1, 2, false),
    /** Trident throwers: the real danger to a boat, after dark. */
    DROWNED_RAIDERS(List.of(EntityType.DROWNED), Time.NIGHT, SecurityBand.POLICED, 2, 3, false),
    /** 26.2's undead sea horror - the deep water signature. */
    NAUTILUS_HORROR(List.of(EntityType.ZOMBIE_NAUTILUS), Time.ANY, SecurityBand.FRONTIER, 1, 2, false),
    /** The sky is not safe either. */
    PHANTOM_FLIGHT(List.of(EntityType.PHANTOM), Time.NIGHT, SecurityBand.FRONTIER, 2, 3, false),
    /** A crewed boat of raiders: NPC piracy, and a warning of the player kind. */
    PIRATE_CREW(List.of(EntityType.PILLAGER, EntityType.PILLAGER), Time.ANY, SecurityBand.LAWLESS, 1, 1, true),
    /** A potion-throwing witch adrift in her own boat. */
    SEA_WITCH(List.of(EntityType.WITCH), Time.ANY, SecurityBand.LAWLESS, 1, 1, true);

    /**
     * When an encounter hunts.
     */
    public enum Time {
        DAY, NIGHT, ANY;

        public boolean matches(boolean isDay) {
            return this == ANY || (this == DAY) == isDay;
        }
    }

    private final List<EntityType> mobs;
    private final Time time;
    private final SecurityBand minBand;
    private final int min;
    private final int max;
    private final boolean boated;

    EncounterType(List<EntityType> mobs, Time time, SecurityBand minBand, int min, int max, boolean boated) {
        this.mobs = mobs;
        this.time = time;
        this.minBand = minBand;
        this.min = min;
        this.max = max;
        this.boated = boated;
    }

    public List<EntityType> getMobs() {
        return mobs;
    }

    public Time getTime() {
        return time;
    }

    /**
     * @return the tamest band in which this encounter can happen at all
     */
    public SecurityBand getMinBand() {
        return minBand;
    }

    public int getMin() {
        return min;
    }

    public int getMax() {
        return max;
    }

    /**
     * @return true if the crew arrives in a boat of their own
     */
    public boolean isBoated() {
        return boated;
    }
}
