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
    GUARDIAN_PICKET(List.of(EntityType.GUARDIAN), Time.DAY, SecurityBand.POLICED, 1, 2, false, Habitat.WATER),
    /** Trident throwers: the real danger to a boat, after dark. */
    DROWNED_RAIDERS(List.of(EntityType.DROWNED), Time.NIGHT, SecurityBand.POLICED, 2, 3, false, Habitat.WATER),
    /**
     * The deep stirs: an elder guardian, far too much for a lone sailor to
     * fight lightly - the encounter where fleeing is the sensible answer.
     * (26.2's nautilus, zombie or not, is a tameable MOUNT, not a monster: it
     * simply swims away.)
     */
    DEEP_TERROR(List.of(EntityType.ELDER_GUARDIAN), Time.ANY, SecurityBand.FRONTIER, 1, 1, false, Habitat.WATER),
    /** The sky is not safe either. */
    PHANTOM_FLIGHT(List.of(EntityType.PHANTOM), Time.NIGHT, SecurityBand.FRONTIER, 2, 3, false, Habitat.AIR),
    /** A crewed boat of raiders: NPC piracy, and a warning of the player kind. */
    PIRATE_CREW(List.of(EntityType.PILLAGER, EntityType.PILLAGER), Time.ANY, SecurityBand.LAWLESS, 1, 1, true,
            Habitat.SURFACE),
    /** A potion-throwing witch adrift in her own boat. */
    SEA_WITCH(List.of(EntityType.WITCH), Time.ANY, SecurityBand.LAWLESS, 1, 1, true, Habitat.SURFACE);

    /**
     * Where an encounter belongs relative to the waterline. Spawning a water
     * mob in the air above the sea leaves it flopping instead of hunting.
     */
    public enum Habitat {
        /** Below the surface, where swimmers can actually swim. */
        WATER(-3),
        /** On the waterline, for boats and their crews. */
        SURFACE(1),
        /** Above the mast. */
        AIR(14);

        private final int offset;

        Habitat(int offset) {
            this.offset = offset;
        }

        /**
         * @param seaLevel the world sea level
         * @return the Y to spawn at
         */
        public int spawnY(int seaLevel) {
            return seaLevel + offset;
        }
    }

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
    private final Habitat habitat;

    EncounterType(List<EntityType> mobs, Time time, SecurityBand minBand, int min, int max, boolean boated,
            Habitat habitat) {
        this.mobs = mobs;
        this.time = time;
        this.minBand = minBand;
        this.min = min;
        this.max = max;
        this.boated = boated;
        this.habitat = habitat;
    }

    public Habitat getHabitat() {
        return habitat;
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
