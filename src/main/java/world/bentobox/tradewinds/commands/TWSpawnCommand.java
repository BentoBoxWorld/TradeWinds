package world.bentobox.tradewinds.commands;

import java.util.List;
import java.util.Optional;

import org.bukkit.Location;

import world.bentobox.bentobox.api.commands.CompositeCommand;
import world.bentobox.bentobox.api.commands.DelayedTeleportCommand;
import world.bentobox.bentobox.api.user.User;
import world.bentobox.bentobox.util.Util;
import world.bentobox.tradewinds.TradeWinds;

/**
 * The door into the ocean - and only the door.
 * <p>
 * This used to be {@code /tw spawn}, a straight teleport to the world spawn.
 * That was harmless while spawn was an empty islet, and became a free warp
 * home to a market the moment spawn was made a working trading post. But
 * simply deleting it locked new players out, and locked anyone in another world
 * out with them.
 * <p>
 * So it is a door with two rules. At sea it never moves you toward anything -
 * travel is the game, and there is no commanding your way across it. And
 * coming in from outside returns you to the water you <b>left</b>, not to
 * spawn, so that hopping to another game mode and back is not a free ride
 * home either. Only a sailor who has never set out starts at the spawn port.
 * <p>
 * Since Stage 7a it is also the "go" of going home - within the rules.
 * Standing on your own island, {@code /tw go} steps you to your home point
 * (the same move as {@code /tw home}); anywhere else in the ocean it tells
 * you the WAY home - direction and distance - and leaves the sailing to you.
 *
 * @author tastybento
 */
public class TWSpawnCommand extends DelayedTeleportCommand {

    public TWSpawnCommand(CompositeCommand parent) {
        super(parent, "go", "spawn", "sail");
    }

    @Override
    public void setup() {
        setPermission("island.spawn");
        setOnlyPlayer(true);
        setDescription("tradewinds.commands.spawn.description");
    }

    @Override
    public boolean execute(User user, String label, List<String> args) {
        TradeWinds addon = getAddon();
        if (addon.inWorld(user.getWorld())) {
            return goAtSea(addon, user);
        }
        Optional<Location> last = addon.getSeaPositionTracker().lastKnown(user.getPlayer());
        Location destination = last.orElseGet(() -> getWorld().getSpawnLocation());
        boolean returning = last.isPresent();
        this.delayCommand(user, () -> Util.teleportAsync(user.getPlayer(), destination).thenAccept(done -> {
            user.sendMessage(returning ? "tradewinds.spawn.resumed" : "tradewinds.spawn.teleported");
            // First-timers get boat + bundle; boat carriers get launched
            addon.getStarterKit().onSpawnArrival(user.getPlayer());
        }));
        return true;
    }

    /**
     * {@code /tw go} inside the ocean world (ruled 2026-08-05): standing on
     * your own island it steps you home; away from it, it points the way -
     * direction and distance, never a ride. The islandless get the original
     * refusal: the way to anywhere is a boat or a warp.
     */
    private boolean goAtSea(TradeWinds addon, User user) {
        var island = addon.getIslands().getIsland(addon.getOverWorld(), user.getUniqueId());
        if (island == null || !island.getMemberSet().contains(user.getUniqueId())) {
            user.sendMessage("tradewinds.spawn.already-at-sea");
            return false;
        }
        if (island.onIsland(user.getLocation())) {
            this.delayCommand(user,
                    () -> addon.getIslands().homeTeleportAsync(addon.getOverWorld(), user.getPlayer())
                            .thenAccept(done -> {
                                if (Boolean.TRUE.equals(done)) {
                                    user.sendMessage("tradewinds.home.teleported");
                                }
                            }));
            return true;
        }
        // Away from the island: toggle the course-home bar (Stage 7b) and
        // give the one-shot bearing right away so the answer is immediate
        boolean on = addon.getNavigationBarTask() != null
                && addon.getNavigationBarTask().toggleCourse(user.getPlayer());
        if (!on) {
            user.sendMessage("tradewinds.home.course-off");
            return true;
        }
        int dx = island.getCenter().getBlockX() - user.getLocation().getBlockX();
        int dz = island.getCenter().getBlockZ() - user.getLocation().getBlockZ();
        user.sendMessage("tradewinds.home.bearing",
                "[direction]", user.getTranslation(compassKey(dx, dz)),
                world.bentobox.bentobox.api.localization.TextVariables.NUMBER,
                String.valueOf(Math.round(Math.sqrt((double) dx * dx + (double) dz * dz))));
        user.sendMessage("tradewinds.home.course-on");
        return true;
    }

    /**
     * The eight-point compass direction of an offset, as a locale key -
     * north is -Z, east is +X, the Minecraft way.
     */
    public static String compassKey(int dx, int dz) {
        String[] points = { "north", "north-east", "east", "south-east", "south", "south-west", "west",
                "north-west" };
        double angle = Math.toDegrees(Math.atan2(dx, -dz));
        int index = (int) Math.floorMod(Math.round(angle / 45.0), 8);
        return "tradewinds.direction." + points[index];
    }
}
