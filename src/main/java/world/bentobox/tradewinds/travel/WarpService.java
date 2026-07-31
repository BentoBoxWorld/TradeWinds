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
import net.kyori.adventure.text.format.NamedTextColor;
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
        return addon.getPlayerDataManager().get(player.getUniqueId()).getChartedIslands().stream()
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
    }

    /**
     * Show the warp dialog to a boated player at an island border.
     */
    public void openDialog(Player player, IslandSpec origin) {
        double fuelAboard = addon.getFuelService().holdFuel(player);
        List<Destination> destinations = destinations(player, origin, fuelAboard);
        if (destinations.isEmpty()) {
            player.sendMessage(user(player).getTranslation("tradewinds.warp.no-destinations"));
            return;
        }
        List<ActionButton> buttons = destinations.stream().map(dest -> button(player, origin, dest)).toList();
        Dialog dialog = Dialog.create(factory -> factory.empty()
                .base(DialogBase.builder(Component.text("Warp - " + origin.name()))
                        .body(List.of(DialogBody.plainMessage(Component
                                .text("Fuel aboard: " + (int) fuelAboard + " units", NamedTextColor.AQUA))))
                        .build())
                .type(DialogType.multiAction(buttons).columns(1).build()));
        player.showDialog(dialog);
    }

    private ActionButton button(Player player, IslandSpec origin, Destination dest) {
        IslandSpec spec = dest.island();
        Component label = Component.text(spec.name() + " - " + dest.fuelCost() + " fuel",
                dest.affordable() ? NamedTextColor.WHITE : NamedTextColor.DARK_GRAY);
        Component tooltip = Component.text(spec.type().name() + ", " + spec.band().getDisplayName() + ", "
                + (int) Math.sqrt(spec.distanceSquared(origin.centerX(), origin.centerZ())) + " blocks");
        DialogAction action = DialogAction.customClick(
                (response, audience) -> {
                    if (dest.affordable()) {
                        warp(player, origin, spec, dest.fuelCost());
                    } else {
                        player.sendMessage(user(player).getTranslation("tradewinds.warp.not-enough-fuel"));
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
        TWWarpEvent event = new TWWarpEvent(player, from, to, fuelCost);
        Bukkit.getPluginManager().callEvent(event);
        if (event.isCancelled()) {
            return;
        }
        if (!addon.getFuelService().consume(player, fuelCost)) {
            player.sendMessage(user(player).getTranslation("tradewinds.warp.not-enough-fuel"));
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
            jump.run();
        }, seconds * 20L);
    }

    /**
     * Give back fuel value after an aborted warp, as charcoal into the hold
     * (or at the player's feet if the hold is full).
     */
    private void refund(Player player, int fuelUnits) {
        double charcoalValue = Math.max(1.0, addon.getSettings().getFuelValues().getOrDefault("CHARCOAL", 3.0));
        int amount = (int) Math.ceil(fuelUnits / charcoalValue);
        ItemStack refund = new ItemStack(Material.CHARCOAL, Math.max(1, amount));
        int added = addon.getHoldService().add(player, refund);
        if (added < refund.getAmount()) {
            ItemStack rest = refund.clone();
            rest.setAmount(refund.getAmount() - added);
            player.getWorld().dropItem(player.getLocation(), rest);
        }
    }

    /**
     * The jump itself: effects, teleport, re-seat.
     */
    private void jump(Player player, IslandSpec from, IslandSpec to, int fuelCost) {
        // Departure effects
        Location here = player.getLocation();
        here.getWorld().spawnParticle(Particle.PORTAL, here, 80, 1, 1, 1, 0.5);
        here.getWorld().playSound(here, Sound.BLOCK_PORTAL_TRAVEL, 0.4f, 1.2f);

        // Arrive close to the destination, on the bearing of the origin -
        // inside view distance, so the island is right there in front of you
        int[] arrive = RouteGraph.arrivalPoint(from, to, addon.getSettings().getWarpArrivalDistance());
        Location target = new Location(addon.getOverWorld(), arrive[0] + 0.5,
                addon.getSettings().getSeaHeight() + 1.0, arrive[1] + 0.5);

        // Dismount -> teleport player and boat -> re-seat (AcidIsland /ai pattern)
        Entity vehicle = player.getVehicle();
        if (vehicle != null) {
            player.leaveVehicle();
        }
        Util.teleportAsync(player, target).thenRun(() -> {
            if (vehicle instanceof Boat boat && boat.isValid()) {
                boat.teleportAsync(target).thenRun(() -> Bukkit.getScheduler().runTask(addon.getPlugin(),
                        () -> boat.addPassenger(player)));
            }
            arrivalEffects(player, target);
            player.sendMessage(user(player).getTranslation("tradewinds.warp.arrived", "[name]", to.name()));
            Bukkit.getPluginManager().callEvent(new TWWarpCompletedEvent(player, from, to, fuelCost));
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

    private world.bentobox.bentobox.api.user.User user(Player player) {
        return world.bentobox.bentobox.api.user.User.getInstance(player);
    }
}
