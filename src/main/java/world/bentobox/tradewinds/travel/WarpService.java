package world.bentobox.tradewinds.travel;

import java.util.Comparator;
import java.util.List;

import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.inventory.ItemStack;
import org.bukkit.entity.Boat;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;

import io.papermc.paper.dialog.Dialog;
import io.papermc.paper.registry.data.dialog.ActionButton;
import io.papermc.paper.registry.data.dialog.DialogBase;
import io.papermc.paper.registry.data.dialog.action.DialogAction;
import io.papermc.paper.registry.data.dialog.body.DialogBody;
import io.papermc.paper.registry.data.dialog.type.DialogType;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.event.ClickCallback;
import world.bentobox.bentobox.util.Util;
import world.bentobox.tradewinds.TradeWinds;
import world.bentobox.tradewinds.api.events.TWWarpCompletedEvent;
import world.bentobox.tradewinds.api.events.TWWarpEvent;
import world.bentobox.tradewinds.galaxy.GalaxyEngine;
import world.bentobox.tradewinds.galaxy.IslandSpec;
import world.bentobox.tradewinds.galaxy.RouteGraph;

/**
 * The warp: fuel-powered jump between charted islands. Opens the Paper dialog
 * at the border, consumes fuel from the hold, executes the proven
 * dismount/teleport/re-seat pattern, and lands the player just inside the
 * destination border on the bearing of the origin island.
 * <p>
 * Warp failure (the interstice detour) arrives in Stage 5 - until then every
 * engaged warp delivers.
 *
 * @author tastybento
 */
public class WarpService {

    /** Placeholder for island name in locale messages. */
    private static final String NAME_PLACEHOLDER = "[name]";
    /** Locale key for insufficient fuel message. */
    private static final String NOT_ENOUGH_FUEL_MSG = "tradewinds.warp.not-enough-fuel";

    private final TradeWinds addon;

    public WarpService(TradeWinds addon) {
        this.addon = addon;
    }

    /**
     * One entry in the warp dialog.
     */
    public record Destination(IslandSpec island, int fuelCost, boolean affordable) {
    }

    /**
     * The destinations a player may warp to from an origin island: charted,
     * not the origin, nearest first, capped by config. Pure selection logic,
     * kept separate from dialog rendering for tests.
     */
    public List<Destination> destinations(Player player, IslandSpec origin, double fuelAboard) {
        GalaxyEngine engine = addon.getGalaxyEngine(addon.getOverWorld().getSeed());
        RouteGraph routes = addon.getRouteGraph();
        List<Destination> ports = addon.getPlayerDataManager().get(player.getUniqueId()).getChartedIslands()
                .stream()
                .map(key -> key.split(","))
                .map(cell -> engine.islandInCell(Integer.parseInt(cell[0]), Integer.parseInt(cell[1])))
                .flatMap(java.util.Optional::stream)
                .filter(spec -> !(spec.cellX() == origin.cellX() && spec.cellZ() == origin.cellZ()))
                .sorted(Comparator.comparingLong(spec -> spec.distanceSquared(origin.centerX(), origin.centerZ())))
                .limit(addon.getSettings().getMaxWarpDestinations())
                .map(spec -> {
                    int cost = routes.cost(origin, spec);
                    return new Destination(spec, cost, cost <= fuelAboard);
                })
                .toList();
        // A member's claimed islet is a warp node for THEM: pinned at the
        // top, normal fuel rules, invisible to everyone else (Stage 7b)
        java.util.Optional<IslandSpec> home = HomePort.specFor(addon, user(player)).filter(
                spec -> !(spec.cellX() == origin.cellX() && spec.cellZ() == origin.cellZ()));
        if (home.isEmpty()) {
            return ports;
        }
        List<Destination> all = new java.util.ArrayList<>();
        int cost = routes.cost(origin, home.get());
        all.add(new Destination(home.get(), cost, cost <= fuelAboard));
        all.addAll(ports);
        return all;
    }

    /**
     * Show the warp dialog to a boated player at an island border.
     */
    public void openDialog(Player player, IslandSpec origin) {
        // No warping out of a fight - the same rule a bed applies to sleeping
        if (addon.getDialogGuard().enemiesNear(player)) {
            addon.getDialogGuard().refuse(player);
            return;
        }
        double fuelAboard = addon.getFuelService().holdFuel(player);
        List<Destination> destinations = destinations(player, origin, fuelAboard);
        if (destinations.isEmpty()) {
            user(player).sendMessage("tradewinds.warp.no-destinations");
            return;
        }
        List<ActionButton> buttons = destinations.stream().map(dest -> button(player, origin, dest)).toList();
        Dialog dialog = Dialog.create(factory -> factory.empty()
                .base(DialogBase.builder(user(player).getTranslationAsComponent("tradewinds.ui.warp.title",
                        NAME_PLACEHOLDER, origin.name()))
                        .body(List.of(DialogBody.plainMessage(
                                user(player).getTranslationAsComponent("tradewinds.ui.warp.fuel", "[amount]",
                                        String.valueOf((int) fuelAboard)))))
                        .build())
                .type(DialogType.multiAction(buttons).columns(1).build()));
        player.showDialog(dialog);
    }

    private ActionButton button(Player player, IslandSpec origin, Destination dest) {
        IslandSpec spec = dest.island();
        String distance = String.valueOf((int) Math.sqrt(spec.distanceSquared(origin.centerX(),
                origin.centerZ())));
        if (HomePort.isHome(spec)) {
            // A home has no port sheet - no type, no tech, no market
            Component homeLabel = user(player).getTranslationAsComponent(
                    dest.affordable() ? "tradewinds.ui.warp.home" : "tradewinds.ui.warp.home-poor",
                    NAME_PLACEHOLDER, spec.name(), "[fuel]", String.valueOf(dest.fuelCost()));
            Component homeTooltip = user(player).getTranslationAsComponent(
                    "tradewinds.ui.warp.home-tooltip", "[distance]", distance);
            return ActionButton.create(homeLabel, homeTooltip, 250, DialogAction.customClick(
                    (response, audience) -> {
                        if (dest.affordable()) {
                            warp(player, origin, spec, dest.fuelCost());
                        } else {
                            user(player).sendMessage(NOT_ENOUGH_FUEL_MSG);
                        }
                    }, ClickCallback.Options.builder().build()));
        }
        Component label = user(player).getTranslationAsComponent(
                dest.affordable() ? "tradewinds.ui.warp.destination" : "tradewinds.ui.warp.destination-poor",
                NAME_PLACEHOLDER, spec.name(), "[fuel]", String.valueOf(dest.fuelCost()));
        Component tooltip = user(player).getTranslationAsComponent("tradewinds.ui.warp.destination-tooltip",
                "[type]", spec.type().name(), "[tech]", String.valueOf(spec.techLevel()),
                "[band]", spec.band().getDisplayName(), "[distance]", distance);
        DialogAction action = DialogAction.customClick(
                (response, audience) -> {
                    if (dest.affordable()) {
                        warp(player, origin, spec, dest.fuelCost());
                    } else {
                        user(player).sendMessage(NOT_ENOUGH_FUEL_MSG);
                    }
                }, ClickCallback.Options.builder().build());
        return ActionButton.create(label, tooltip, 250, action);
    }

    /**
     * Engage the warp: fuel is consumed here; the jump follows after any
     * configured stand-still period (moving aborts it and the fuel is
     * refunded - see {@link #standStillThen}).
     */
    public void warp(Player player, IslandSpec from, IslandSpec to, int fuelCost) {
        // Checked again here, not only at the dialog: a patrol can arrive while
        // the menu is open, and the fuel is spent the moment this is allowed
        if (addon.getDialogGuard().enemiesNear(player)) {
            addon.getDialogGuard().refuse(player);
            return;
        }
        TWWarpEvent event = new TWWarpEvent(player, from, to, fuelCost);
        Bukkit.getPluginManager().callEvent(event);
        if (event.isCancelled()) {
            return;
        }
        if (!addon.getFuelService().consume(player, fuelCost)) {
            user(player).sendMessage(NOT_ENOUGH_FUEL_MSG);
            return;
        }
        standStillThen(player, () -> jump(player, from, to, fuelCost), fuelCost);
    }

    /**
     * Hold the player still for the configured seconds, then run the jump.
     * Moving beyond a block aborts and refunds the fuel - the warp is a
     * course, not a panic button. Ops and bypass-permission holders skip it.
     */
    private void standStillThen(Player player, Runnable jump, int fuelCost) {
        int seconds = addon.getSettings().getWarpStandStillSeconds();
        if (seconds <= 0 || player.isOp()
                || player.hasPermission(addon.getPermissionPrefix() + "mod.bypassdelays")) {
            jump.run();
            return;
        }
        Location start = player.getLocation().clone();
        user(player).sendMessage("tradewinds.warp.hold-course", "[seconds]", String.valueOf(seconds));
        Bukkit.getScheduler().runTaskLater(addon.getPlugin(), () -> {
            if (!player.isOnline()) {
                return;
            }
            Location now = player.getLocation();
            if (!now.getWorld().equals(start.getWorld()) || now.distanceSquared(start) > 4.0) {
                // Course broken: refund the fuel that was spent on engagement
                refund(player, fuelCost);
                user(player).sendMessage("tradewinds.warp.course-broken");
                return;
            }
            if (addon.getDialogGuard().enemiesNear(player)) {
                // Something reached them while the warp was spinning up
                refund(player, fuelCost);
                addon.getDialogGuard().refuse(player);
                return;
            }
            jump.run();
        }, seconds * 20L);
    }

    /**
     * Give back fuel value after an aborted warp, as charcoal into the fuel
     * slots (or at the player's feet if the fuel row is full).
     */
    private void refund(Player player, int fuelUnits) {
        double charcoalValue = Math.max(1.0, addon.getSettings().getFuelValues().getOrDefault("CHARCOAL", 3.0));
        int amount = Math.max(1, (int) Math.ceil(fuelUnits / charcoalValue));
        int added = addon.getHoldService().addFuel(player, Material.CHARCOAL, amount);
        if (added < amount) {
            player.getWorld().dropItem(player.getLocation(), new ItemStack(Material.CHARCOAL, amount - added));
        }
    }

    /**
     * The jump itself: departure effects, then either the interstice (on a
     * failed warp) or delivery to the destination.
     */
    private void jump(Player player, IslandSpec from, IslandSpec to, int fuelCost) {
        // Departure effects
        Location here = player.getLocation();
        here.getWorld().spawnParticle(Particle.PORTAL, here, 80, 1, 1, 1, 0.5);
        here.getWorld().playSound(here, Sound.BLOCK_PORTAL_TRAVEL, 0.4f, 1.2f);

        // Arrive close to the destination, on the bearing of the origin -
        // inside view distance, so the island is right there in front of you
        // The warp does not always hold: a failure drops the sailor into the
        // interstice, still owed this destination (spec 3.3)
        if (addon.getIntersticeService().rollFailure(player.getUniqueId())) {
            addon.getIntersticeService().strand(player, from, to);
            return;
        }
        deliver(player, to, from, fuelCost);
    }

    /**
     * Deliver a player (and their boat) to an island - the arrival half of a
     * warp. Also used by the interstice's free re-engage.
     */
    public void deliver(Player player, IslandSpec to) {
        deliver(player, to, to, 0);
    }

    private void deliver(Player player, IslandSpec to, IslandSpec bearingFrom, int fuelCost) {
        int[] arrive = RouteGraph.arrivalPoint(bearingFrom, to, addon.getSettings().getWarpArrivalDistance());
        // Arrive on open water. The nominal arrival ring is a fixed distance
        // from the island centre, and since coastlines gained headlands it can
        // fall on land - which used to materialise the sailor inside a hillside
        // and kill them ("suffocated in a wall").
        // Outward from the island, never inward: a ragged coast can reach the
        // arrival ring, and correcting sideways would drop the sailor in a bay
        Location target = SeaArrival.openSeaOutward(addon.getGalaxyEngine(addon.getOverWorld().getSeed()),
                addon.getOverWorld(), to.centerX(), to.centerZ(), arrive[0], arrive[1],
                addon.getSettings().getSeaHeight());
        // Face the boat (and the sailor) at the way in: the destination's
        // PIER for a port - paddling straight ahead from a warp arrival is
        // always the way in - or the islet's centre for a home, which has no
        // dock to steer for
        double pierX;
        double pierZ;
        if (HomePort.isHome(to)) {
            pierX = to.centerX();
            pierZ = to.centerZ();
        } else {
            world.bentobox.tradewinds.galaxy.DockPlan plan = addon
                    .getGalaxyEngine(addon.getOverWorld().getSeed()).dockPlan(to);
            // The dock FLAG: the banner at the pier end, which is what a sailor
            // actually steers for (IslandDecorator plants it 2 blocks short)
            pierX = to.centerX() + Math.cos(plan.bearing()) * (plan.dockEnd() - 2);
            pierZ = to.centerZ() + Math.sin(plan.bearing()) * (plan.dockEnd() - 2);
        }
        // The plain look-at yaw IS correct: the console proved it, computing
        // a yaw within half a degree of the sailor's own F3 reading.
        // The quarter-turn "hull offset" of the first fix was chasing a
        // different problem - the mount was resetting the facing, not the
        // maths being sideways - and it only turned the boat the other way.
        float lookYaw = yawToward(target.getX(), target.getZ(), pierX, pierZ);
        target.setYaw(lookYaw);

        addon.log("Warp: " + player.getName() + " arriving at " + to.name() + " ("
                + target.getBlockX() + "," + target.getBlockY() + "," + target.getBlockZ() + ") - "
                + (int) Math.sqrt(to.distanceSquared(target.getBlockX(), target.getBlockZ()))
                + " blocks from the island centre");
        // Dismount -> teleport player and boat -> re-seat (AcidIsland /ai pattern)
        Entity vehicle = player.getVehicle();
        if (vehicle != null) {
            player.leaveVehicle();
        }
        Util.teleportAsync(player, target).thenRun(() -> {
            if (vehicle instanceof Boat boat && boat.isValid()) {
                boat.teleportAsync(target).thenRun(() -> Bukkit.getScheduler().runTask(addon.getPlugin(), () -> {
                    boat.addPassenger(player);
                    // Mounting drags the facing about (the sailor sees the
                    // boat pointing wherever they are looking), so set BOTH
                    // rotations after the re-seat rather than trusting the
                    // teleport to have stuck
                    Bukkit.getScheduler().runTaskLater(addon.getPlugin(), () -> {
                        float yaw = target.getYaw();
                        boat.setRotation(yaw, 0f);
                        player.setRotation(yaw, player.getLocation().getPitch());
                    }, 2L);
                }));
            }
            arrivalEffects(player, target);
            // The re-seat raises the chart, but arrival blindness hides it -
            // hence "sometimes I see the holograms, sometimes I do not"
            // (playtest 2026-08-02). Raise it again once vision returns.
            if (addon.getSettings().isChartOnBoarding()) {
                Bukkit.getScheduler().runTaskLater(addon.getPlugin(), () -> {
                    if (player.isOnline() && player.getWorld().equals(addon.getOverWorld())) {
                        addon.getChartHolograms().show(player);
                    }
                }, (addon.getSettings().getWarpBlindnessSeconds() + 1) * 20L);
            }
            user(player).sendMessage("tradewinds.warp.arrived", NAME_PLACEHOLDER, to.name());
            Bukkit.getPluginManager().callEvent(new TWWarpCompletedEvent(player, bearingFrom, to, fuelCost));
        });
    }

    /**
     * Warping hurts (lore: it gates the under-equipped): nausea, blindness and
     * configurable minor damage, plus arrival spectacle.
     */
    private void arrivalEffects(Player player, Location target) {
        int nausea = addon.getSettings().getWarpNauseaSeconds();
        int blind = addon.getSettings().getWarpBlindnessSeconds();
        if (nausea > 0) {
            player.addPotionEffect(new PotionEffect(PotionEffectType.NAUSEA, nausea * 20, 0));
        }
        if (blind > 0) {
            player.addPotionEffect(new PotionEffect(PotionEffectType.BLINDNESS, blind * 20, 0));
        }
        double damage = addon.getSettings().getWarpDamage();
        if (damage > 0) {
            player.damage(damage);
        }
        target.getWorld().spawnParticle(Particle.PORTAL, target, 80, 1, 1, 1, 0.5);
        target.getWorld().playSound(target, Sound.BLOCK_PORTAL_TRAVEL, 0.4f, 0.8f);
    }

    /**
     * The Minecraft yaw that looks from one point at another: 0 = south (+Z),
     * -90 = east (+X). Pure, so the bearing is testable.
     *
     * @param fromX looker x
     * @param fromZ looker z
     * @param toX target x
     * @param toZ target z
     * @return yaw in degrees
     */
    static float yawToward(double fromX, double fromZ, double toX, double toZ) {
        return (float) Math.toDegrees(Math.atan2(-(toX - fromX), toZ - fromZ));
    }

    /**
     * Wrap a yaw into (-180, 180] - the range Minecraft hands back, so a
     * corrected value compares like an uncorrected one.
     */
    static float normalise(float yaw) {
        float wrapped = (yaw + 180f) % 360f;
        if (wrapped < 0) {
            wrapped += 360f;
        }
        return wrapped - 180f;
    }

    private world.bentobox.bentobox.api.user.User user(Player player) {
        return world.bentobox.bentobox.api.user.User.getInstance(player);
    }
}
