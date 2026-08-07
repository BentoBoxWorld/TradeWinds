package world.bentobox.tradewinds.commands;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.entity.Player;

import world.bentobox.bentobox.api.commands.CompositeCommand;
import world.bentobox.bentobox.api.localization.TextVariables;
import world.bentobox.bentobox.api.user.User;
import world.bentobox.bentobox.util.Util;
import world.bentobox.tradewinds.TradeWinds;
import world.bentobox.tradewinds.dataobjects.BoatHold;
import world.bentobox.tradewinds.travel.HoldService;

/**
 * {@code /twadmin boat <player> [restore]} - the admin's answer to "where did
 * my boat go?".
 * <p>
 * Without {@code restore} it reads the player's boat records straight from
 * the database: material, cargo aboard, fuel, the last position the chart
 * remembers, and whether an avatar (entity or dropped item) is actually
 * loaded there right now. With {@code restore} it regenerates the ACTIVE
 * boat as a stamped item in the player's pack - the cargo record was never
 * in danger, only the physical avatar - refusing while a loaded avatar
 * exists, because restoring next to a live hull mints a duplicate. Born of
 * the 2026-08-06 archaeology, when finding one lost hull took region-file
 * forensics.
 *
 * @author tastybento
 */
public class AdminBoatCommand extends CompositeCommand {

    public AdminBoatCommand(CompositeCommand parent) {
        super(parent, "boat");
    }

    @Override
    public void setup() {
        setPermission("admin.boat");
        setOnlyPlayer(false);
        setParametersHelp("tradewinds.commands.admin.boat.parameters");
        setDescription("tradewinds.commands.admin.boat.description");
    }

    @Override
    public boolean execute(User user, String label, List<String> args) {
        if (args.isEmpty() || args.size() > 2) {
            showHelp(this, user);
            return false;
        }
        UUID target = getPlayers().getUUID(args.get(0));
        if (target == null) {
            user.sendMessage("general.errors.unknown-player", TextVariables.NAME, args.get(0));
            return false;
        }
        TradeWinds addon = getAddon();
        if (args.size() == 2) {
            if (!args.get(1).equalsIgnoreCase("restore")) {
                showHelp(this, user);
                return false;
            }
            return restore(user, addon, target, args.get(0));
        }
        Optional<BoatHold> active = addon.getHoldManager().activeBoat(target);
        Optional<BoatHold> old = addon.getHoldManager().oldBoat(target);
        user.sendMessage("tradewinds.commands.admin.boat.header", TextVariables.NAME,
                name(target, args.get(0)));
        if (active.isEmpty() && old.isEmpty()) {
            user.sendMessage("tradewinds.commands.admin.boat.none");
            return true;
        }
        active.ifPresent(hold -> report(user, addon, "tradewinds.commands.admin.boat.active", hold));
        old.ifPresent(hold -> report(user, addon, "tradewinds.commands.admin.boat.old", hold));
        return true;
    }

    private void report(User user, TradeWinds addon, String key, BoatHold hold) {
        Material material = Material.matchMaterial(hold.getMaterial());
        String avatar = addon.getBoatService().findPlaced(hold)
                .map(entity -> user.getTranslation("tradewinds.commands.admin.boat.avatar-loaded",
                        "[type]", entity.getType().name(),
                        "[x]", String.valueOf(entity.getLocation().getBlockX()),
                        "[y]", String.valueOf(entity.getLocation().getBlockY()),
                        "[z]", String.valueOf(entity.getLocation().getBlockZ())))
                .orElseGet(() -> carrier(user, addon, hold));
        user.sendMessage(key,
                "[material]", hold.getMaterial(),
                "[id]", hold.getUniqueId(),
                "[used]", String.valueOf(HoldService.slotsUsedIn(hold)),
                "[slots]", String.valueOf(addon.getBoatRanks().slots(material)),
                "[fuel]", String.format("%.0f", addon.getFuelService().unitsOf(hold)),
                "[world]", hold.getWorld(),
                "[x]", String.valueOf(hold.getX()),
                "[y]", String.valueOf(hold.getY()),
                "[z]", String.valueOf(hold.getZ()),
                "[avatar]", avatar);
    }

    /**
     * Where the avatar is when no placed one is loaded: in someone's pack, or
     * out of sight (an unloaded chunk - the chart position is the lead).
     */
    private String carrier(User user, TradeWinds addon, BoatHold hold) {
        for (Player online : Bukkit.getOnlinePlayers()) {
            if (addon.getBoatService().isCarrying(online, hold)
                    || (online.getVehicle() != null && hold.getUniqueId()
                            .equals(world.bentobox.tradewinds.travel.BoatService.boatId(online.getVehicle())))) {
                return user.getTranslation("tradewinds.commands.admin.boat.avatar-with", TextVariables.NAME,
                        online.getName());
            }
        }
        return user.getTranslation("tradewinds.commands.admin.boat.avatar-unloaded");
    }

    private boolean restore(User user, TradeWinds addon, UUID target, String name) {
        Optional<BoatHold> active = addon.getHoldManager().activeBoat(target);
        if (active.isEmpty()) {
            user.sendMessage("tradewinds.commands.admin.boat.none");
            return false;
        }
        BoatHold hold = active.get();
        Player player = Bukkit.getPlayer(target);
        if (player == null) {
            user.sendMessage("general.errors.offline-player");
            return false;
        }
        // A loaded avatar means the boat is NOT lost - restoring would mint a
        // duplicate hull. An avatar frozen in an unloaded chunk cannot be
        // seen from here; the admin decides, and the logbook records it.
        Optional<org.bukkit.entity.Entity> placed = addon.getBoatService().findPlaced(hold);
        if (placed.isPresent()) {
            user.sendMessage("tradewinds.commands.admin.boat.hull-exists",
                    "[x]", String.valueOf(placed.get().getLocation().getBlockX()),
                    "[y]", String.valueOf(placed.get().getLocation().getBlockY()),
                    "[z]", String.valueOf(placed.get().getLocation().getBlockZ()));
            return false;
        }
        if (addon.getBoatService().isCarrying(player, hold)) {
            user.sendMessage("tradewinds.commands.admin.boat.hull-carried");
            return false;
        }
        addon.getBoatService().logbook("restored from the database by " + user.getName(), hold,
                player.getLocation());
        addon.getBoatService().giveBoatItem(player, hold);
        user.sendMessage("tradewinds.commands.admin.boat.restored", TextVariables.NAME,
                name(target, name), "[material]", hold.getMaterial());
        return true;
    }

    private String name(UUID target, String fallback) {
        String known = getPlayers().getName(target);
        return known.isEmpty() ? fallback : known;
    }

    @Override
    public Optional<List<String>> tabComplete(User user, String alias, List<String> args) {
        // Raw chain includes this command's label: ["boat", "<partial>"...]
        if (args.size() <= 2) {
            return Optional.of(Util.getOnlinePlayerList(user));
        }
        return Optional.of(Util.tabLimit(List.of("restore"), args.get(args.size() - 1)));
    }
}
