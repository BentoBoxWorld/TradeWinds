package world.bentobox.tradewinds.travel;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

import org.bukkit.Bukkit;
import org.bukkit.Color;
import org.bukkit.Location;
import org.bukkit.entity.Display.Billboard;
import org.bukkit.entity.Player;
import org.bukkit.entity.TextDisplay;

import net.kyori.adventure.text.Component;
import world.bentobox.bentobox.api.user.User;
import world.bentobox.tradewinds.TradeWinds;
import world.bentobox.tradewinds.ocean.DockPlan;
import world.bentobox.tradewinds.ocean.IslandSpec;

/**
 * The hologram compass: {@code /tw chart} in a boat raises text holograms
 * around the player, each hanging in the true direction of a charted island -
 * a visual target to row toward. They zoom out from the player to a ring
 * (display teleport interpolation), stack vertically when islands share a
 * bearing (nearest lowest, like distance layers above the horizon), and fade
 * after a few seconds. Only the caller sees them.
 *
 * @author tastybento
 */
public class ChartHolograms {

    /** Bearing sector width for stacking, degrees. */
    private static final double SECTOR_DEGREES = 20.0;
    private static final double BASE_HEIGHT = 2.5;
    private static final String PLACEHOLDER_DISTANCE = "[distance]";
    /**
     * The dock marker hangs below the island names, clear of their stack, but
     * never below the horizon: heights are relative to a sailor sitting at sea
     * level, and at 0.2 the sign floated IN the water ten blocks out.
     */
    private static final double DOCK_HEIGHT = 1.5;
    private static final double STACK_STEP = 0.8;
    /**
     * A boat closer than this needs no marker: an arrow pointing eight blocks
     * at your own feet is noise, and a boat in your pack is not lost at all.
     */
    private static final int MARKER_MIN_DISTANCE = 32;
    private static final int ZOOM_TICKS = 12;

    private final TradeWinds addon;
    private final Map<UUID, List<TextDisplay>> active = new ConcurrentHashMap<>();
    /**
     * Which set of holograms is current per player. Each show() bumps it, and
     * the fade timer only clears the generation it belongs to - otherwise the
     * FIRST call's timer wipes the SECOND call's holograms seconds after they
     * appear, which is exactly the "run it again and it vanishes" the
     * 2026-08-02 playtest saw (arriving raised a set, /tw chart raised
     * another, the older timer killed it).
     */
    private final Map<UUID, Integer> generation = new ConcurrentHashMap<>();

    public ChartHolograms(TradeWinds addon) {
        this.addon = addon;
    }

    /**
     * One hologram's placement: offset from the player and its label parts.
     *
     * @param dock true for the marker pointing at the island's own pier rather
     *        than at a distant island
     */
    record Marker(double dx, double dy, double dz, IslandSpec island, int distance, boolean dock) {
    }

    /** A marker pointing at a boat rather than an island. */
    record BoatMarker(double dx, double dy, double dz, int distance, boolean old) {
    }

    /**
     * Where your boat is, from anywhere in the world (ruled 2026-08-02): the
     * active BOAT, and the OLD BOAT you abandoned by boarding another. Pure,
     * so the bearing is testable.
     *
     * @param boatX boat block x
     * @param boatZ boat block z
     * @param px player block x
     * @param pz player block z
     * @param radius ring radius in blocks
     * @param old true for the abandoned boat
     * @return the marker
     */
    static BoatMarker boatMarker(int boatX, int boatZ, int px, int pz, double radius, boolean old) {
        double dx = (double) boatX - px;
        double dz = (double) boatZ - pz;
        double dist = Math.max(1.0, Math.hypot(dx, dz));
        // Above the dock marker, below the island stack: your own ship is the
        // second thing you look for after the quay
        return new BoatMarker(dx / dist * radius, old ? DOCK_HEIGHT + 0.6 : DOCK_HEIGHT + 1.2,
                dz / dist * radius, (int) dist, old);
    }

    /**
     * The marker pointing at the pier of the island you are currently in the
     * waters of, if you are in any.
     * <p>
     * The chart answers "where is everywhere else" perfectly well and used to
     * say nothing at all about the one thing a sailor actually needs next:
     * which way is the dock. Pure, so the bearing is testable.
     *
     * @param island the island whose waters the player is in
     * @param plan its dock plan
     * @param px player block x
     * @param pz player block z
     * @param radius ring radius in blocks
     * @return the dock marker
     */
    static Marker dockMarker(IslandSpec island, DockPlan plan, int px, int pz, double radius) {
        int pierX = island.centerX() + (int) Math.round(Math.cos(plan.bearing()) * plan.dockEnd());
        int pierZ = island.centerZ() + (int) Math.round(Math.sin(plan.bearing()) * plan.dockEnd());
        double dx = (double) pierX - px;
        double dz = (double) pierZ - pz;
        double dist = Math.max(1.0, Math.hypot(dx, dz));
        // Sits below the island names: it is the nearest thing, and keeping it
        // out of their stack means it never collides with one
        return new Marker(dx / dist * radius, DOCK_HEIGHT, dz / dist * radius, island, (int) dist, true);
    }

    /**
     * Compute marker placements - pure and testable. Islands sorted nearest
     * first, capped; when several share a bearing sector the nearest sits at
     * the base height and the further ones stack above it.
     *
     * @param islands charted islands
     * @param px player block x
     * @param pz player block z
     * @param radius ring radius in blocks
     * @param max maximum markers
     * @return marker placements
     */
    static List<Marker> markers(List<IslandSpec> islands, int px, int pz, double radius, int max) {
        List<IslandSpec> sorted = islands.stream()
                .sorted(Comparator.comparingLong(s -> s.distanceSquared(px, pz)))
                .limit(max)
                .toList();
        Map<Integer, Integer> sectorRank = new HashMap<>();
        List<Marker> result = new ArrayList<>();
        for (IslandSpec spec : sorted) {
            double dx = (double) spec.centerX() - px;
            double dz = (double) spec.centerZ() - pz;
            double dist = Math.max(1.0, Math.hypot(dx, dz));
            double bearing = Math.toDegrees(Math.atan2(dz, dx));
            int sector = (int) Math.floor((bearing + 180.0) / SECTOR_DEGREES);
            int rank = sectorRank.merge(sector, 1, Integer::sum) - 1;
            result.add(new Marker(dx / dist * radius, BASE_HEIGHT + rank * STACK_STEP, dz / dist * radius, spec,
                    (int) dist, false));
        }
        return result;
    }

    /**
     * Whether a boat is far enough away - and out of hand - to be worth a
     * marker at all.
     */
    private boolean worthMarking(Player player, world.bentobox.tradewinds.dataobjects.BoatHold hold,
            Location eye) {
        if (!hold.getWorld().equals(player.getWorld().getName())
                || addon.getBoatService().isCarrying(player, hold)) {
            return false;
        }
        double dx = (double) hold.getX() - eye.getBlockX();
        double dz = (double) hold.getZ() - eye.getBlockZ();
        return dx * dx + dz * dz > (double) MARKER_MIN_DISTANCE * MARKER_MIN_DISTANCE;
    }

    /**
     * Show the compass to a player: clears any previous set, spawns the
     * holograms at the player and zooms them out to the ring.
     */
    public void show(Player player) {
        clear(player.getUniqueId());
        // Boarding or charting at a port copies its harbour charts
        List<IslandSpec> scanned = addon.getPlayerDataManager().portScan(player);
        if (!scanned.isEmpty()) {
            User.getInstance(player).sendMessage("tradewinds.chart.port-scan",
                    world.bentobox.bentobox.api.localization.TextVariables.NUMBER,
                    String.valueOf(scanned.size()));
        }
        Location eye = player.getLocation();
        double radius = addon.getSettings().getChartHologramDistance();
        List<Marker> markers = new ArrayList<>();
        // Which way is the dock - the one bearing a sailor in these waters
        // actually needs, and the one the chart never used to give them
        dockIsland(eye.getBlockX(), eye.getBlockZ()).ifPresent(island -> markers.add(dockMarker(island,
                addon.getOceanEngine(addon.getOverWorld().getSeed()).dockPlan(island), eye.getBlockX(),
                eye.getBlockZ(), radius)));
        markers.addAll(markers(chartedIslands(player), eye.getBlockX(), eye.getBlockZ(), radius,
                addon.getSettings().getChartHologramMax()));
        List<BoatMarker> boats = new ArrayList<>();
        // Only mark a boat you have to GO to: not the one under you, not the
        // one in your pack, and not one a few paces away (2026-08-02: both
        // markers pointed 8m at the player's own feet).
        if (!(player.getVehicle() instanceof org.bukkit.entity.Boat)) {
            addon.getHoldManager().activeBoat(player.getUniqueId())
                    .filter(hold -> worthMarking(player, hold, eye))
                    .ifPresent(hold -> boats.add(boatMarker(hold.getX(), hold.getZ(), eye.getBlockX(),
                            eye.getBlockZ(), radius, false)));
        }
        addon.getHoldManager().oldBoat(player.getUniqueId())
                .filter(hold -> worthMarking(player, hold, eye))
                .ifPresent(hold -> boats.add(boatMarker(hold.getX(), hold.getZ(), eye.getBlockX(),
                        eye.getBlockZ(), radius, true)));
        // HOME: a member's claimed islet rides on every raise of the chart -
        // above the boat markers, because home is the longest bearing
        BoatMarker home = HomePort.islandOf(addon, player.getUniqueId())
                .map(island -> {
                    BoatMarker m = boatMarker(island.getCenter().getBlockX(), island.getCenter().getBlockZ(),
                            eye.getBlockX(), eye.getBlockZ(), radius, false);
                    return new BoatMarker(m.dx(), m.dy() + 1.2, m.dz(), m.distance(), false);
                })
                .filter(m -> m.distance() > MARKER_MIN_DISTANCE)
                .orElse(null);
        if (markers.isEmpty() && boats.isEmpty() && home == null) {
            return;
        }
        List<TextDisplay> spawned = new ArrayList<>();
        if (home != null) {
            Component text = User.getInstance(player).getTranslationAsComponent("tradewinds.hologram.home",
                    PLACEHOLDER_DISTANCE, String.valueOf(home.distance()));
            Color bgColor = Color.fromARGB(140, 60, 45, 0);
            spawnAndZoomHologram(player, eye, spawned, text, home.dx(), home.dy(), home.dz(), bgColor);
        }
        for (BoatMarker boat : boats) {
            Component text = User.getInstance(player).getTranslationAsComponent(
                    boat.old() ? "tradewinds.hologram.old-boat" : "tradewinds.hologram.boat",
                    PLACEHOLDER_DISTANCE, String.valueOf(boat.distance()));
            Color bgColor = boat.old() ? Color.fromARGB(140, 60, 0, 0) : Color.fromARGB(140, 0, 60, 30);
            spawnAndZoomHologram(player, eye, spawned, text, boat.dx(), boat.dy(), boat.dz(), bgColor);
        }
        for (Marker marker : markers) {
            Component text = label(player, marker);
            Color bgColor = marker.dock() ? Color.fromARGB(140, 60, 40, 0) : Color.fromARGB(120, 0, 20, 40);
            spawnAndZoomHologram(player, eye, spawned, text, marker.dx(), marker.dy(), marker.dz(), bgColor);
        }
        active.put(player.getUniqueId(), spawned);
        int mine = generation.merge(player.getUniqueId(), 1, Integer::sum);
        // Fade out after the configured time - but only if nothing newer has
        // replaced this set in the meantime
        Bukkit.getScheduler().runTaskLater(addon.getPlugin(), () -> {
            if (generation.getOrDefault(player.getUniqueId(), 0) == mine) {
                clear(player.getUniqueId());
            }
        }, addon.getSettings().getChartHologramSeconds() * 20L);
    }

    /**
     * The island whose waters the player is in, if any - the same reach the
     * navigation bar uses, so the dock marker appears exactly when the bar
     * starts counting down the distance to the pier.
     */
    private java.util.Optional<IslandSpec> dockIsland(int x, int z) {
        if (addon.getOverWorld() == null) {
            return java.util.Optional.empty();
        }
        int range = addon.getSettings().getIslandDistance();
        return addon.getOceanEngine(addon.getOverWorld().getSeed()).islandsNear(x, z, range).stream()
                .filter(spec -> spec.distanceSquared(x, z) <= (long) range * range).findFirst();
    }

    /**
     * Spawn a hologram at the player's location and schedule it to zoom out to a target location.
     */
    private void spawnAndZoomHologram(Player player, Location eye, List<TextDisplay> spawned,
            Component text, double dx, double dy, double dz, Color bgColor) {
        TextDisplay display = eye.getWorld().spawn(eye.clone().add(0, 1.0, 0), TextDisplay.class);
        display.text(text);
        display.setBillboard(Billboard.CENTER);
        display.setSeeThrough(true);
        display.setBackgroundColor(bgColor);
        display.setPersistent(false);
        display.setTeleportDuration(ZOOM_TICKS);
        for (Player other : Bukkit.getOnlinePlayers()) {
            if (!other.equals(player)) {
                other.hideEntity(addon.getPlugin(), display);
            }
        }
        spawned.add(display);
        Location target = eye.clone().add(dx, dy, dz);
        Bukkit.getScheduler().runTask(addon.getPlugin(), () -> {
            if (display.isValid()) {
                display.teleport(target);
            }
        });
    }

    private Component label(Player player, Marker marker) {
        IslandSpec spec = marker.island();
        if (marker.dock()) {
            return User.getInstance(player).getTranslationAsComponent("tradewinds.hologram.dock",
                    "[name]", spec.name(), PLACEHOLDER_DISTANCE, String.valueOf(marker.distance()));
        }
        return User.getInstance(player).getTranslationAsComponent("tradewinds.hologram.island",
                "[name]", spec.name(),
                "[type]", spec.type().name(),
                "[tech]", String.valueOf(spec.techLevel()),
                "[band]", User.getInstance(player).getTranslation(spec.band().getLocaleKey()),
                PLACEHOLDER_DISTANCE, String.valueOf(marker.distance()));
    }

    private List<IslandSpec> chartedIslands(Player player) {
        var engine = addon.getOceanEngine(addon.getOverWorld().getSeed());
        return addon.getPlayerDataManager().get(player.getUniqueId()).getChartedIslands().stream()
                .map(key -> key.split(","))
                .map(cell -> engine.islandInCell(Integer.parseInt(cell[0]), Integer.parseInt(cell[1])))
                .flatMap(java.util.Optional::stream)
                .toList();
    }

    /**
     * Remove a player's holograms.
     */
    public void clear(UUID playerId) {
        List<TextDisplay> displays = active.remove(playerId);
        if (displays != null) {
            displays.forEach(display -> {
                if (display.isValid()) {
                    display.remove();
                }
            });
        }
    }

    /**
     * Remove everything (disable).
     */
    public void clearAll() {
        List.copyOf(active.keySet()).forEach(this::clear);
    }
}
