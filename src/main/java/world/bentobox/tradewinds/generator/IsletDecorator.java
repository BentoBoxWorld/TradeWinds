package world.bentobox.tradewinds.generator;

import java.util.HashMap;
import java.util.Map;
import java.util.Random;

import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.World;
import org.bukkit.block.structure.Mirror;
import org.bukkit.block.structure.StructureRotation;
import org.bukkit.generator.BlockPopulator;
import org.bukkit.generator.LimitedRegion;
import org.bukkit.generator.WorldInfo;
import org.bukkit.structure.Structure;
import org.bukkit.util.BlockVector;
import org.eclipse.jdt.annotation.NonNull;

import world.bentobox.tradewinds.TradeWinds;
import world.bentobox.tradewinds.galaxy.GalaxyEngine;
import world.bentobox.tradewinds.galaxy.Islet;
import world.bentobox.tradewinds.galaxy.IsletStructures;

/**
 * Places the small vanilla structure some wild islets carry - an igloo on the
 * snow, fossils in the sand, a ruined portal in the jungle, an abandoned camp
 * in the woods. {@link IsletStructures} decides WHAT (pure, seeded); this
 * populator only loads the chosen template and stamps it down when the chunk
 * holding the islet's center generates. The whole template goes down in one
 * populate call, so there is nothing to keep consistent across chunks.
 *
 * @author tastybento
 */
public class IsletDecorator extends BlockPopulator {

    private final TradeWinds addon;
    /** Template cache - the same few NBTs place on thousands of islets. */
    private final Map<String, Structure> templates = new HashMap<>();

    public IsletDecorator(TradeWinds addon) {
        this.addon = addon;
    }

    @Override
    public void populate(@NonNull WorldInfo worldInfo, @NonNull Random random, int chunkX, int chunkZ,
            @NonNull LimitedRegion limitedRegion) {
        if (worldInfo.getEnvironment() != World.Environment.NORMAL) {
            return;
        }
        double chance = addon.getSettings().getIsletStructureChance();
        if (chance <= 0) {
            return;
        }
        GalaxyEngine engine = addon.getGalaxyEngine(worldInfo.getSeed());
        int grid = engine.getConfig().wildIsletGrid();
        if (grid <= 0) {
            return;
        }
        int minX = chunkX << 4;
        int minZ = chunkZ << 4;
        // Islets whose center falls in this chunk - the anchor that makes the
        // placement happen exactly once however chunks are visited
        for (int cellX = Math.floorDiv(minX, grid); cellX <= Math.floorDiv(minX + 15, grid); cellX++) {
            for (int cellZ = Math.floorDiv(minZ, grid); cellZ <= Math.floorDiv(minZ + 15, grid); cellZ++) {
                Islet islet = engine.wildIsletInCell(cellX, cellZ).orElse(null);
                if (islet == null || islet.centerX() < minX || islet.centerX() >= minX + 16
                        || islet.centerZ() < minZ || islet.centerZ() >= minZ + 16) {
                    continue;
                }
                IsletStructures.pick(engine.getConfig().seed(), islet, chance).ifPresent(
                        placement -> place(engine, islet, placement, limitedRegion, worldInfo, random));
            }
        }
    }

    private void place(GalaxyEngine engine, Islet islet, IsletStructures.Placement placement,
            LimitedRegion region, WorldInfo worldInfo, Random random) {
        Structure structure = templates.computeIfAbsent(placement.template(),
                key -> Bukkit.getStructureManager().loadStructure(NamespacedKey.minecraft(key)));
        if (structure == null) {
            return;
        }
        BlockVector size = structure.getSize();
        // Centered on the islet's heart, base sunk per the placement - the
        // surface height is pure galaxy arithmetic, no block reads
        int baseX = islet.centerX() - size.getBlockX() / 2;
        int baseZ = islet.centerZ() - size.getBlockZ() / 2;
        int baseY = engine.surfaceHeightAt(islet.centerX(), islet.centerZ()) + 1 - placement.sink();
        if (baseY < worldInfo.getMinHeight() + 1
                || baseY + size.getBlockY() >= worldInfo.getMaxHeight()) {
            return;
        }
        // The populate region has a limited buffer around the chunk: a
        // template that would poke out is skipped rather than truncated
        if (!region.isInRegion(baseX, baseY, baseZ)
                || !region.isInRegion(baseX + size.getBlockX() - 1, baseY, baseZ + size.getBlockZ() - 1)) {
            return;
        }
        structure.place(region, new BlockVector(baseX, baseY, baseZ), false, StructureRotation.NONE,
                Mirror.NONE, -1, 1.0f, random);
        scrubScaffolding(region, placement, baseX, baseY, baseZ, size);
    }

    /**
     * Take out the pieces vanilla's assembler would have consumed: JIGSAW
     * connectors become the template's own {@code final_state}, and STRUCTURE
     * blocks (markers for entities and further pieces) simply go. Pasting a
     * template raw leaves both standing - a row of glowing jigsaws holding up
     * a tent, as found on an islet in the 2026-08-02 playtest.
     */
    private void scrubScaffolding(LimitedRegion region, IsletStructures.Placement placement, int baseX,
            int baseY, int baseZ, BlockVector size) {
        Material fill = Material.matchMaterial(placement.jigsawFill());
        if (fill == null) {
            fill = Material.AIR;
        }
        for (int x = baseX; x < baseX + size.getBlockX(); x++) {
            for (int y = baseY; y < baseY + size.getBlockY(); y++) {
                for (int z = baseZ; z < baseZ + size.getBlockZ(); z++) {
                    if (!region.isInRegion(x, y, z)) {
                        continue;
                    }
                    Material here = region.getType(x, y, z);
                    if (here == Material.JIGSAW) {
                        region.setType(x, y, z, fill);
                    } else if (here == Material.STRUCTURE_BLOCK || here == Material.STRUCTURE_VOID) {
                        region.setType(x, y, z, Material.AIR);
                    }
                }
            }
        }
    }
}
