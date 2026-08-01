package world.bentobox.tradewinds.generator;

import java.util.Random;

import org.bukkit.Material;
import org.bukkit.generator.BlockPopulator;
import org.bukkit.generator.LimitedRegion;
import org.bukkit.generator.WorldInfo;
import org.eclipse.jdt.annotation.NonNull;

import world.bentobox.tradewinds.TradeWinds;

/**
 * Braziers in the dark: netherrack outcrops rising out of the interstice sea
 * with fire burning on top.
 * <p>
 * The interstice was a flat black nothing - a player could not see the water,
 * the horizon, or what was coming at them, which made it read as unfinished
 * rather than hostile. These are the only light out there. Fire on netherrack
 * burns forever and there is nothing for it to spread to, so they stay lit.
 * <p>
 * They double as landmarks. Somewhere featureless is disorienting in a way that
 * somewhere dangerous is not, and a burning rock on the horizon gives a
 * castaway something to steer by while they decide what to do.
 *
 * @author tastybento
 */
public class IntersticeDecorator extends BlockPopulator {

    /** How far a brazier can rise above the waterline. */
    private static final int MAX_RISE = 4;
    /** How far below the surface the outcrop is rooted. */
    private static final int ROOT_DEPTH = 3;

    private final TradeWinds addon;

    public IntersticeDecorator(TradeWinds addon) {
        this.addon = addon;
    }

    @Override
    public void populate(@NonNull WorldInfo worldInfo, @NonNull Random random, int chunkX, int chunkZ,
            @NonNull LimitedRegion region) {
        double chance = addon.getSettings().getIntersticeBrazierChance();
        if (chance <= 0 || random.nextDouble() >= chance) {
            return;
        }
        int sea = addon.getSettings().getIntersticeSeaHeight();
        int x = (chunkX << 4) + random.nextInt(16);
        int z = (chunkZ << 4) + random.nextInt(16);
        if (!region.isInRegion(x, sea, z) || region.getType(x, sea, z) != Material.WATER) {
            return;
        }
        int rise = 1 + random.nextInt(MAX_RISE);
        int top = sea + rise;
        // A squat outcrop: widest at the waterline, tapering as it rises, so it
        // reads as a rock rather than a pillar
        for (int y = sea - ROOT_DEPTH; y <= top; y++) {
            int spread = y <= sea ? 1 : 0;
            for (int dx = -spread; dx <= spread; dx++) {
                for (int dz = -spread; dz <= spread; dz++) {
                    if (region.isInRegion(x + dx, y, z + dz)) {
                        region.setType(x + dx, y, z + dz, Material.NETHERRACK);
                    }
                }
            }
        }
        // The flame, and a little glow under the waterline so the rock is
        // visible from below as well as across the water
        if (region.isInRegion(x, top + 1, z)) {
            region.setType(x, top + 1, z, Material.FIRE);
        }
        if (random.nextBoolean() && region.isInRegion(x, sea - ROOT_DEPTH, z)) {
            region.setType(x, sea - ROOT_DEPTH, z, Material.GLOWSTONE);
        }
    }
}
