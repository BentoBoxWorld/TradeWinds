package world.bentobox.tradewinds.generator;

import java.util.List;
import java.util.Map;

import org.bukkit.Material;
import org.bukkit.entity.Villager;

import world.bentobox.tradewinds.galaxy.IslandType;
import world.bentobox.tradewinds.galaxy.SurfaceKind;

/**
 * The Bukkit-material face of each island type: dock wood, plaza surface,
 * stall canopy color, pier banner, villager professions and their workstation
 * blocks. Kept out of the galaxy package so that stays Bukkit-free.
 *
 * @author tastybento
 */
public final class IslandPalette {

    private record Palette(Material planks, Material fence, Material canopy, Material plazaSurface, Material banner,
            List<Villager.Profession> professions, List<Material> workstations) {
    }

    private static final Map<IslandType, Palette> PALETTES = Map.of(
            IslandType.AGRICULTURAL,
            new Palette(Material.OAK_PLANKS, Material.OAK_FENCE, Material.YELLOW_WOOL, Material.DIRT_PATH,
                    Material.YELLOW_BANNER,
                    List.of(Villager.Profession.FARMER, Villager.Profession.BUTCHER, Villager.Profession.SHEPHERD),
                    List.of(Material.COMPOSTER, Material.SMOKER, Material.LOOM)),
            IslandType.FOREST,
            new Palette(Material.DARK_OAK_PLANKS, Material.DARK_OAK_FENCE, Material.GREEN_WOOL, Material.PODZOL,
                    Material.GREEN_BANNER,
                    List.of(Villager.Profession.FLETCHER, Villager.Profession.CARTOGRAPHER),
                    List.of(Material.FLETCHING_TABLE, Material.CARTOGRAPHY_TABLE)),
            IslandType.FISHING,
            new Palette(Material.OAK_PLANKS, Material.OAK_FENCE, Material.BLUE_WOOL, Material.OAK_PLANKS,
                    Material.BLUE_BANNER,
                    List.of(Villager.Profession.FISHERMAN, Villager.Profession.FISHERMAN,
                            Villager.Profession.LEATHERWORKER),
                    List.of(Material.BARREL, Material.CAULDRON, Material.SMOKER)),
            IslandType.MINING,
            new Palette(Material.SPRUCE_PLANKS, Material.SPRUCE_FENCE, Material.GRAY_WOOL, Material.COBBLESTONE,
                    Material.GRAY_BANNER,
                    List.of(Villager.Profession.MASON, Villager.Profession.TOOLSMITH),
                    List.of(Material.STONECUTTER, Material.SMITHING_TABLE)),
            IslandType.INDUSTRIAL,
            new Palette(Material.ACACIA_PLANKS, Material.ACACIA_FENCE, Material.ORANGE_WOOL,
                    Material.POLISHED_BLACKSTONE, Material.ORANGE_BANNER,
                    List.of(Villager.Profession.TOOLSMITH, Villager.Profession.WEAPONSMITH,
                            Villager.Profession.ARMORER),
                    List.of(Material.BLAST_FURNACE, Material.GRINDSTONE, Material.ANVIL)),
            IslandType.LUXURY,
            new Palette(Material.CHERRY_PLANKS, Material.CHERRY_FENCE, Material.MAGENTA_WOOL, Material.SMOOTH_QUARTZ,
                    Material.MAGENTA_BANNER,
                    List.of(Villager.Profession.LIBRARIAN, Villager.Profession.CLERIC,
                            Villager.Profession.CARTOGRAPHER),
                    List.of(Material.LECTERN, Material.BREWING_STAND, Material.CARTOGRAPHY_TABLE)),
            IslandType.FROZEN,
            new Palette(Material.SPRUCE_PLANKS, Material.SPRUCE_FENCE, Material.LIGHT_BLUE_WOOL,
                    Material.SPRUCE_PLANKS, Material.LIGHT_BLUE_BANNER,
                    List.of(Villager.Profession.LEATHERWORKER, Villager.Profession.CLERIC),
                    List.of(Material.CAULDRON, Material.BREWING_STAND)));

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

    public static Material plazaSurface(IslandType type) {
        return PALETTES.get(type).plazaSurface();
    }

    public static Material banner(IslandType type) {
        return PALETTES.get(type).banner();
    }

    public static List<Villager.Profession> professions(IslandType type) {
        return PALETTES.get(type).professions();
    }

    public static List<Material> workstations(IslandType type) {
        return PALETTES.get(type).workstations();
    }

    /**
     * The top block of a land column. Vanilla decoration reads this to decide
     * what will grow: grass gets trees and flowers, mycelium gets huge
     * mushrooms, sand gets nothing much - which is what a beach should be.
     *
     * @param kind the surface kind
     * @return the surface material
     */
    /**
     * The public plaza amenities an island's tech level provides, cumulative:
     * every port has the galley (workbench + campfire, placed separately);
     * real industry brings smelting and stonework; high tech brews; the top of
     * the tree enchants. All governed by visitor-rank flags (FURNACE covers
     * the brewing stand per BentoBox's interaction listener; ENCHANTING,
     * ANVIL, GRINDSTONE, SMITHING, STONECUTTING are their own flags, none
     * denied at ports).
     *
     * @param techLevel the island's tech level (1-7)
     * @return amenity blocks in placement order
     */
    public static List<Material> amenities(int techLevel) {
        List<Material> list = new java.util.ArrayList<>();
        if (techLevel >= 3) {
            list.add(Material.FURNACE);
            list.add(Material.STONECUTTER);
        }
        if (techLevel >= 4) {
            list.add(Material.SMITHING_TABLE);
            list.add(Material.GRINDSTONE);
        }
        if (techLevel >= 5) {
            list.add(Material.BREWING_STAND);
            list.add(Material.CAULDRON);
        }
        if (techLevel >= 6) {
            list.add(Material.ANVIL);
        }
        if (techLevel >= 7) {
            list.add(Material.ENCHANTING_TABLE);
        }
        return list;
    }

    public static Material surface(SurfaceKind kind) {
        return switch (kind) {
        case SAND -> Material.SAND;
        case MYCELIUM -> Material.MYCELIUM;
        case RED_SAND -> Material.RED_SAND;
        case PODZOL -> Material.PODZOL;
        case STONE -> Material.STONE;
        case GRAVEL -> Material.GRAVEL;
        case MUD -> Material.MUD;
        case SNOW -> Material.SNOW_BLOCK;
        case GRASS -> Material.GRASS_BLOCK;
        };
    }

    /**
     * What sits under that surface, before the column turns to stone. Sand
     * needs sandstone under it (and red sand its terracotta) or the first
     * shovelful finds lawn under the dunes.
     *
     * @param kind the surface kind
     * @return the subsoil material
     */
    public static Material subsoil(SurfaceKind kind) {
        return switch (kind) {
        case SAND -> Material.SANDSTONE;
        case RED_SAND -> Material.TERRACOTTA;
        case STONE -> Material.STONE;
        case GRAVEL -> Material.GRAVEL;
        case MUD -> Material.MUD;
        case GRASS, MYCELIUM, PODZOL, SNOW -> Material.DIRT;
        };
    }
}
