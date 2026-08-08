package world.bentobox.tradewinds.ocean;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;

import org.junit.jupiter.api.Test;

/**
 * The islet structure lottery - pure, seeded, and biome-complete.
 *
 * @author tastybento
 */
class IsletStructuresTest {

    private static final long SEED = 424242L;

    /** Biomes that deliberately never decorate: the biome is the find. */
    private static final Set<String> PRISTINE = Set.of("minecraft:pale_garden", OceanEngine.MUSHROOM_BIOME);

    private Islet islet(int x, int z, String biome) {
        return new Islet(x, z, 60, biome);
    }

    @Test
    void testEveryIsletBiomeHasATableEntry() {
        // A new islet biome with no entry would silently never decorate -
        // fail loudly instead. (Beaches are fringes, not whole-islet biomes.)
        for (String biome : OceanEngine.isletBiomes()) {
            if (biome.endsWith("beach")) {
                continue;
            }
            assertTrue(IsletStructures.coveredBiomes().contains(biome),
                    biome + " has no structure table entry (empty is fine, missing is not)");
        }
    }

    @Test
    void testChanceZeroPlacesNothing() {
        for (int i = 0; i < 50; i++) {
            assertTrue(IsletStructures.pick(SEED, islet(i * 977, -i * 1231, "minecraft:desert"), 0.0).isEmpty());
        }
    }

    @Test
    void testChanceOnePlacesOnEveryEligibleIslet() {
        for (String biome : OceanEngine.isletBiomes()) {
            if (biome.endsWith("beach")) {
                continue;
            }
            Optional<IsletStructures.Placement> pick = IsletStructures.pick(SEED, islet(1000, 2000, biome), 1.0);
            if (PRISTINE.contains(biome)) {
                assertTrue(pick.isEmpty(), biome + " must stay pristine");
            } else {
                assertTrue(pick.isPresent(), biome + " should place at chance 1.0");
                assertTrue(pick.get().template().matches("[a-z0-9_/]+"),
                        "Suspicious template key: " + pick.get().template());
                assertTrue(pick.get().sink() >= 0 && pick.get().sink() <= 4,
                        "Sink out of range for " + pick.get().template());
            }
        }
    }

    @Test
    void testEveryPlacementDeclaresItsJigsawFill() {
        // Several vanilla templates carry jigsaw connectors that vanilla's
        // own assembler would have swapped for a final_state. Placed raw they
        // stand there glowing (playtest 2026-08-02), so every placement must
        // name a real material to put in their place - and the ruined portals
        // that sit on netherrack must say so, or the portal loses its footing.
        for (String biome : OceanEngine.isletBiomes()) {
            if (biome.endsWith("beach") || PRISTINE.contains(biome)) {
                continue;
            }
            for (int i = 0; i < 60; i++) {
                IsletStructures.pick(SEED + i, islet(i * 811, i * 47, biome), 1.0).ifPresent(p -> {
                    assertNotNull(p.jigsawFill(),
                            p.template() + " does not say what its jigsaws become");
                    assertTrue(!p.jigsawFill().isBlank(),
                            p.template() + " jigsaw fill is blank");
                    assertNotNull(org.bukkit.Material.matchMaterial(p.jigsawFill()),
                            p.template() + " names an unknown material: " + p.jigsawFill());
                    if (p.template().matches("ruined_portal/portal_[1245]")) {
                        assertEquals("NETHERRACK", p.jigsawFill(),
                                p.template() + " stands on netherrack in its own NBT");
                    }
                });
            }
        }
    }

    @Test
    void testDeterministicAndVaried() {
        Set<String> seen = new HashSet<>();
        int placed = 0;
        for (int i = 0; i < 400; i++) {
            Islet spot = islet(i * 913, i * -1717, List.of("minecraft:desert", "minecraft:forest",
                    "minecraft:snowy_plains", "minecraft:jungle", "minecraft:savanna").get(i % 5));
            Optional<IsletStructures.Placement> a = IsletStructures.pick(SEED, spot, 0.25);
            assertEquals(a, IsletStructures.pick(SEED, spot, 0.25), "Same seed+islet must pick the same");
            if (a.isPresent()) {
                placed++;
                seen.add(a.get().template());
            }
        }
        // ~25% place, drawn from a real menu, not one stamp
        assertTrue(placed > 50 && placed < 150, "Placement rate off: " + placed + "/400");
        assertTrue(seen.size() > 8, "Only " + seen.size() + " distinct templates: " + seen);
    }

    @Test
    void testBiomeAppropriateness() {
        // The headline pairings hold: igloos on snow, bones in the sand
        for (int i = 0; i < 200; i++) {
            IsletStructures.pick(SEED, islet(i * 331, i * 733, "minecraft:snowy_plains"), 1.0)
                    .ifPresent(p -> assertTrue(p.template().startsWith("igloo/"),
                            "Snowfields place igloos, got " + p.template()));
            IsletStructures.pick(SEED, islet(i * 331, i * 733, "minecraft:desert"), 1.0)
                    .ifPresent(p -> assertTrue(
                            p.template().startsWith("fossil/") || p.template().startsWith("ruined_portal/"),
                            "Deserts place bones or portals, got " + p.template()));
        }
    }
}
