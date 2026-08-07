package world.bentobox.tradewinds.commands;

import java.util.List;
import java.util.Optional;

import world.bentobox.bentobox.api.commands.CompositeCommand;
import world.bentobox.bentobox.api.localization.TextVariables;
import world.bentobox.bentobox.api.user.User;
import world.bentobox.tradewinds.TradeWinds;
import world.bentobox.tradewinds.crime.Contraband;
import world.bentobox.tradewinds.galaxy.IslandSpec;

/**
 * Shows what customs would make of you, here, right now.
 * <p>
 * Added after a playtest where the honest report was "not sure if it is
 * working": scans are probabilistic, cooldowns and flags are invisible, and a
 * title card that flashes past tells you nothing about why. This prints the
 * whole state so it can be reasoned about instead of guessed at.
 *
 * @author tastybento
 */
public class AdminCustomsCommand extends CompositeCommand {

    private static final String VALUE_PLACEHOLDER = "[value]";

    public AdminCustomsCommand(CompositeCommand parent) {
        super(parent, "customs");
    }

    @Override
    public void setup() {
        setPermission("admin.customs");
        setOnlyPlayer(true);
        setDescription("tradewinds.commands.admin.customs.description");
    }

    @Override
    public boolean execute(User user, String label, List<String> args) {
        TradeWinds addon = getAddon();
        int x = user.getLocation().getBlockX();
        int z = user.getLocation().getBlockZ();
        int range = addon.getSettings().getIslandProtectionRange();
        Optional<IslandSpec> here = addon.getGalaxyEngine(getWorld().getSeed()).islandsNear(x, z, range).stream()
                .filter(s -> s.distanceSquared(x, z) <= (long) range * range).findFirst();

        user.sendMessage("tradewinds.commands.admin.customs.header");
        user.sendMessage("tradewinds.commands.admin.customs.gate", VALUE_PLACEHOLDER,
                String.valueOf(addon.getSettings().isCrimeEnabled()
                        && addon.getSettings().isIllegalTradeEnabled()));
        user.sendMessage("tradewinds.commands.admin.customs.contraband", VALUE_PLACEHOLDER,
                String.join(", ", addon.getCustomsService().contrabandNames()));
        user.sendMessage("tradewinds.commands.admin.customs.aboard", TextVariables.NUMBER,
                String.valueOf(addon.getCustomsService().contrabandAboard(user.getPlayer())));
        user.sendMessage("tradewinds.commands.admin.customs.standing", VALUE_PLACEHOLDER,
                addon.getPlayerStanding(user, user.getUniqueId()));
        if (here.isEmpty()) {
            user.sendMessage("tradewinds.commands.admin.customs.open-sea");
            return true;
        }
        IslandSpec island = here.get();
        double base = addon.getSettings().getScanChance().getOrDefault(island.band().name(), 0.0);
        double effective = Contraband.scanChance(base,
                addon.getReputationService().standing(user.getUniqueId()),
                addon.getSettings().getScanUpstandingFactor(), addon.getSettings().getScanOffenderFactor());
        user.sendMessage("tradewinds.commands.admin.customs.island", "[name]", island.name(), "[band]",
                island.band().getDisplayName());
        user.sendMessage("tradewinds.commands.admin.customs.scan-chance", VALUE_PLACEHOLDER,
                String.format("%.0f%%", effective * 100));
        user.sendMessage("tradewinds.commands.admin.customs.buys", VALUE_PLACEHOLDER,
                String.valueOf(addon.getCustomsService().buysContraband(island.band())));
        user.sendMessage("tradewinds.commands.admin.customs.patrol", TextVariables.NUMBER,
                String.valueOf(addon.getSettings().getPatrolSize().getOrDefault(island.band().name(), 0)));
        user.sendMessage("tradewinds.commands.admin.customs.chased", VALUE_PLACEHOLDER,
                String.valueOf(addon.getCustomsService().isChased(user.getUniqueId())));
        return true;
    }
}
