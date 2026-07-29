package world.bentobox.tradewinds.generator;

import java.util.List;
import java.util.Map;

import org.bukkit.Material;
import org.bukkit.entity.Villager;

import world.bentobox.tradewinds.galaxy.IslandType;

/**
 * The Bukkit-material face of each island type: dock wood, stall canopy color,
 * and resident villager professions. Kept out of the galaxy package so that
 * stays Bukkit-free.
 *
 * @author tastybento
 */
public final class IslandPalette {

    private record Palette(Material planks, Material fence, Material canopy,
            List<Villager.Profession> professions) {
    }

    private static final Map<IslandType, Palette> PALETTES = Map.of(
            IslandType.AGRICULTURAL, new Palette(Material.OAK_PLANKS, Material.OAK_FENCE, Material.YELLOW_WOOL,
                    List.of(Villager.Profession.FARMER, Villager.Profession.BUTCHER, Villager.Profession.SHEPHERD)),
            IslandType.FOREST, new Palette(Material.DARK_OAK_PLANKS, Material.DARK_OAK_FENCE, Material.GREEN_WOOL,
                    List.of(Villager.Profession.FLETCHER, Villager.Profession.CARTOGRAPHER)),
            IslandType.FISHING, new Palette(Material.OAK_PLANKS, Material.OAK_FENCE, Material.BLUE_WOOL,
                    List.of(Villager.Profession.FISHERMAN, Villager.Profession.FISHERMAN,
                            Villager.Profession.LEATHERWORKER)),
            IslandType.MINING, new Palette(Material.SPRUCE_PLANKS, Material.SPRUCE_FENCE, Material.GRAY_WOOL,
                    List.of(Villager.Profession.MASON, Villager.Profession.TOOLSMITH)),
            IslandType.INDUSTRIAL, new Palette(Material.ACACIA_PLANKS, Material.ACACIA_FENCE, Material.ORANGE_WOOL,
                    List.of(Villager.Profession.TOOLSMITH, Villager.Profession.WEAPONSMITH,
                            Villager.Profession.ARMORER)),
            IslandType.LUXURY, new Palette(Material.CHERRY_PLANKS, Material.CHERRY_FENCE, Material.MAGENTA_WOOL,
                    List.of(Villager.Profession.LIBRARIAN, Villager.Profession.CLERIC,
                            Villager.Profession.CARTOGRAPHER)),
            IslandType.FROZEN, new Palette(Material.SPRUCE_PLANKS, Material.SPRUCE_FENCE, Material.LIGHT_BLUE_WOOL,
                    List.of(Villager.Profession.LEATHERWORKER, Villager.Profession.CLERIC)));

    private IslandPalette() {
        // Static use only
    }

    public static Material planks(IslandType type) {
        return PALETTES.get(type).planks();
    }

    public static Material fence(IslandType type) {
        return PALETTES.get(type).fence();
    }

    public static Material canopy(IslandType type) {
        return PALETTES.get(type).canopy();
    }

    public static List<Villager.Profession> professions(IslandType type) {
        return PALETTES.get(type).professions();
    }
}
