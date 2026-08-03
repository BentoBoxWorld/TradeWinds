package world.bentobox.tradewinds.galaxy;

import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * Which small vanilla structure - if any - stands at the heart of a wild
 * islet. Pure selection, no Bukkit: the generator loads the chosen template
 * by name and places it; this class only decides, deterministically from
 * (seed, islet), so the same galaxy grows the same ruins everywhere.
 * <p>
 * The menu is deliberately restricted to vanilla's single-piece NBT templates
 * (fossils, igloo tops, ruined portals, pillager camp props) - jigsaw-built
 * structures would leave connector blocks behind if placed raw. Each biome
 * family gets what belongs there: an igloo on the snowfields, bones in the
 * desert, a sunken portal in the jungle, an abandoned camp in the woods.
 * Mushroom islets and pale gardens stay empty on purpose: there the biome
 * itself is the find.
 *
 * @author tastybento
 */
public final class IsletStructures {

    private static final long SALT_ROLL = 0x157C7BE0L;
    private static final long SALT_CATEGORY = 0x157C7BE1L;
    private static final long SALT_VARIANT = 0x157C7BE2L;

    /**
     * One structure to place: a vanilla template key (under the minecraft
     * namespace), how many blocks to sink its base below the surface (bones
     * lie half-buried, tents sit on top), and what its JIGSAW blocks must
     * become.
     * <p>
     * Several vanilla templates carry jigsaw connector blocks. Vanilla's own
     * generator swaps each for its {@code final_state} as it assembles the
     * structure; pasting the template raw leaves them standing, which is
     * exactly the row of glowing jigsaws found on an islet in the 2026-08-02
     * playtest. The right replacement differs per template - camps want air,
     * most ruined portals want netherrack - so it is read from the template's
     * own NBT and recorded here rather than guessed at runtime.
     *
     * @param template the template key, e.g. {@code fossil/spine_1}
     * @param sink blocks below the surface to set the template's base
     * @param jigsawFill what a jigsaw block in this template becomes
     */
    public record Placement(String template, int sink, String jigsawFill) {

        /** A template with no jigsaws, or one whose connectors vanish. */
        public Placement(String template, int sink) {
            this(template, sink, "AIR");
        }
    }

    /** An igloo, floor flush with the snow. */
    private static final List<Placement> IGLOO = List.of(new Placement("igloo/top", 1));

    /** Old bones, half-buried where the ground is soft. */
    private static final List<Placement> FOSSILS = List.of(
            new Placement("fossil/spine_1", 3), new Placement("fossil/spine_2", 3),
            new Placement("fossil/spine_3", 3), new Placement("fossil/spine_4", 3),
            new Placement("fossil/skull_1", 3), new Placement("fossil/skull_2", 3),
            new Placement("fossil/skull_3", 3), new Placement("fossil/skull_4", 3));

    /** A ruined nether portal, settled a couple of blocks into the ground. */
    private static final List<Placement> PORTALS = List.of(
            new Placement("ruined_portal/portal_1", 2, "NETHERRACK"),
            new Placement("ruined_portal/portal_2", 2, "NETHERRACK"),
            new Placement("ruined_portal/portal_3", 2),
            new Placement("ruined_portal/portal_4", 2, "NETHERRACK"),
            new Placement("ruined_portal/portal_5", 2, "NETHERRACK"),
            new Placement("ruined_portal/portal_6", 2), new Placement("ruined_portal/portal_7", 2),
            new Placement("ruined_portal/portal_8", 2), new Placement("ruined_portal/portal_9", 2),
            new Placement("ruined_portal/portal_10", 2));

    /** An abandoned camp: tents, a log pile, a practice range. */
    private static final List<Placement> CAMP = List.of(
            new Placement("pillager_outpost/feature_tent1", 0),
            new Placement("pillager_outpost/feature_tent2", 0),
            new Placement("pillager_outpost/feature_logs", 0),
            new Placement("pillager_outpost/feature_targets", 0));

    /** Nothing, ever - the biome is its own event. */
    private static final List<List<Placement>> NONE = List.of();

    private static final List<List<Placement>> SNOWY = List.of(IGLOO);
    private static final List<List<Placement>> DRYLANDS = List.of(FOSSILS, PORTALS);
    private static final List<List<Placement>> WOODS = List.of(CAMP, PORTALS);
    private static final List<List<Placement>> JUNGLES = List.of(PORTALS, FOSSILS);
    private static final List<List<Placement>> FELLS = List.of(FOSSILS, PORTALS);

    /**
     * Category tables per islet biome. Two-level so a camp is as likely as a
     * portal even though there are ten portal variants: the first roll picks
     * the category, the second the variant.
     */
    private static final Map<String, List<List<Placement>>> TABLES = Map.ofEntries(
            // Frozen seas
            Map.entry("minecraft:snowy_plains", SNOWY),
            Map.entry("minecraft:snowy_taiga", SNOWY),
            Map.entry("minecraft:ice_spikes", SNOWY),
            Map.entry("minecraft:snowy_slopes", SNOWY),
            Map.entry("minecraft:grove", SNOWY),
            Map.entry("minecraft:frozen_peaks", SNOWY),
            // Cold
            Map.entry("minecraft:taiga", WOODS),
            Map.entry("minecraft:old_growth_pine_taiga", WOODS),
            Map.entry("minecraft:old_growth_spruce_taiga", WOODS),
            Map.entry("minecraft:windswept_hills", FELLS),
            Map.entry("minecraft:windswept_forest", WOODS),
            Map.entry("minecraft:windswept_gravelly_hills", FELLS),
            Map.entry("minecraft:jagged_peaks", FELLS),
            Map.entry("minecraft:stony_shore", FELLS),
            // Temperate
            Map.entry("minecraft:plains", WOODS),
            Map.entry("minecraft:sunflower_plains", WOODS),
            Map.entry("minecraft:forest", WOODS),
            Map.entry("minecraft:birch_forest", WOODS),
            Map.entry("minecraft:old_growth_birch_forest", WOODS),
            Map.entry("minecraft:flower_forest", WOODS),
            Map.entry("minecraft:dark_forest", WOODS),
            Map.entry("minecraft:pale_garden", NONE),
            Map.entry("minecraft:meadow", WOODS),
            Map.entry("minecraft:cherry_grove", WOODS),
            Map.entry("minecraft:swamp", List.of(FOSSILS, PORTALS)),
            Map.entry("minecraft:stony_peaks", FELLS),
            // Lukewarm
            Map.entry("minecraft:savanna", List.of(CAMP, FOSSILS)),
            Map.entry("minecraft:savanna_plateau", List.of(CAMP, FOSSILS)),
            Map.entry("minecraft:windswept_savanna", List.of(CAMP, FOSSILS)),
            Map.entry("minecraft:sparse_jungle", JUNGLES),
            Map.entry("minecraft:jungle", JUNGLES),
            Map.entry("minecraft:wooded_badlands", DRYLANDS),
            // Warm
            Map.entry("minecraft:desert", DRYLANDS),
            Map.entry("minecraft:badlands", DRYLANDS),
            Map.entry("minecraft:eroded_badlands", DRYLANDS),
            Map.entry("minecraft:bamboo_jungle", JUNGLES),
            Map.entry("minecraft:mangrove_swamp", List.of(FOSSILS)),
            // Pristine
            Map.entry(GalaxyEngine.MUSHROOM_BIOME, NONE));

    private IsletStructures() {
        // Static use only
    }

    /**
     * The structure standing on an islet, if any. Same seed, same islet, same
     * answer - which is what lets the placing chunk be generated in any order
     * on any server.
     *
     * @param seed the galaxy seed
     * @param islet the islet
     * @param chance the configured per-islet chance (0-1)
     * @return the placement, or empty for an untouched islet
     */
    public static Optional<Placement> pick(long seed, Islet islet, double chance) {
        List<List<Placement>> categories = TABLES.getOrDefault(islet.biomeKey(), NONE);
        if (chance <= 0 || categories.isEmpty()) {
            return Optional.empty();
        }
        if (Hashing.toUnit(Hashing.cellHash(seed, islet.centerX(), islet.centerZ(), SALT_ROLL)) >= chance) {
            return Optional.empty();
        }
        List<Placement> variants = categories.get((int) Math.floorMod(
                Hashing.cellHash(seed, islet.centerX(), islet.centerZ(), SALT_CATEGORY), categories.size()));
        return Optional.of(variants.get((int) Math.floorMod(
                Hashing.cellHash(seed, islet.centerX(), islet.centerZ(), SALT_VARIANT), variants.size())));
    }

    /**
     * Every islet biome this table covers - a completeness handle for tests:
     * a new islet biome with no entry here should fail loudly, not silently
     * never decorate.
     *
     * @return the covered biome keys
     */
    public static java.util.Set<String> coveredBiomes() {
        return TABLES.keySet();
    }
}
