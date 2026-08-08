package world.bentobox.tradewinds.commands;

import java.util.List;
import java.util.Optional;

import world.bentobox.bentobox.api.commands.CompositeCommand;
import world.bentobox.bentobox.api.commands.ConfirmableCommand;
import world.bentobox.bentobox.api.user.User;
import world.bentobox.tradewinds.TradeWinds;
import world.bentobox.tradewinds.crime.Standing;
import world.bentobox.tradewinds.ocean.IslandSpec;
import world.bentobox.tradewinds.ocean.SecurityBand;

/**
 * Pay off a criminal record at a civilized port.
 * <p>
 * One of the three speeds of recovery (spec section 7), and the fastest -
 * but money buys you out of Wanted, never into virtue: settling a fine returns
 * you to Clean and no further. A Fugitive is refused outright at the safest
 * islands; they have to find a rougher port to buy their way back, which is
 * principle 4 (crime pays, into danger) working in reverse.
 *
 * @author tastybento
 */
public class TWFineCommand extends ConfirmableCommand {

    public TWFineCommand(CompositeCommand parent) {
        super(parent, "fine");
    }

    @Override
    public void setup() {
        setPermission("island.fine");
        setOnlyPlayer(true);
        setDescription("tradewinds.commands.fine.description");
    }

    @Override
    public boolean execute(User user, String label, List<String> args) {
        TradeWinds addon = getAddon();
        if (!getWorld().equals(user.getWorld())) {
            user.sendMessage("general.errors.wrong-world");
            return false;
        }
        double owed = addon.getReputationService().fine(user.getUniqueId());
        if (owed <= 0) {
            user.sendMessage("tradewinds.crime.fine-none");
            return false;
        }
        Optional<IslandSpec> port = portHere(addon, user);
        if (port.isEmpty()) {
            user.sendMessage("tradewinds.crime.fine-no-market");
            return false;
        }
        // The safest ports will not take a fugitive's money
        Standing standing = addon.getReputationService().standing(user.getUniqueId());
        if (standing.isBarredFromSafeTrade() && port.get().band() == SecurityBand.SAFE) {
            user.sendMessage("tradewinds.crime.fine-barred");
            return false;
        }
        String amount = world.bentobox.tradewinds.economy.Money.format(addon, owed);
        user.sendMessage("tradewinds.crime.fine-quote", "[amount]", amount);
        askConfirmation(user, () -> {
            if (addon.getReputationService().payFine(user.getPlayer())) {
                user.sendMessage("tradewinds.crime.fine-paid");
            } else {
                user.sendMessage("tradewinds.crime.fine-cannot-afford", "[amount]", amount);
            }
        });
        return true;
    }

    /**
     * The trading island whose protection range the player is standing in.
     */
    private Optional<IslandSpec> portHere(TradeWinds addon, User user) {
        int x = user.getLocation().getBlockX();
        int z = user.getLocation().getBlockZ();
        int range = addon.getSettings().getIslandProtectionRange();
        return addon.getOceanEngine(getWorld().getSeed()).islandsNear(x, z, range).stream()
                .filter(s -> s.distanceSquared(x, z) <= (long) range * range).findFirst();
    }

}
