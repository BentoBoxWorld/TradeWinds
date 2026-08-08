package world.bentobox.tradewinds.commands;

import java.util.List;

import world.bentobox.bentobox.api.commands.CompositeCommand;
import world.bentobox.bentobox.api.localization.TextVariables;
import world.bentobox.bentobox.api.user.User;
import world.bentobox.tradewinds.TradeWinds;
import world.bentobox.tradewinds.ocean.OceanEngine;
import world.bentobox.tradewinds.generator.OceanIslandRegistrar;

/**
 * Re-applies the configured security-band flags to every registered trading
 * island - run after changing the bands.* config so existing islands pick up
 * the new policy (newly registered islands always use current config).
 *
 * @author tastybento
 */
public class AdminReflagCommand extends CompositeCommand {

    public AdminReflagCommand(CompositeCommand parent) {
        super(parent, "reflag");
    }

    @Override
    public void setup() {
        setPermission("admin.reflag");
        setDescription("tradewinds.commands.admin.reflag.description");
    }

    @Override
    public boolean execute(User user, String label, List<String> args) {
        TradeWinds addon = getAddon();
        OceanEngine engine = addon.getOceanEngine(addon.getOverWorld().getSeed());
        OceanIslandRegistrar registrar = new OceanIslandRegistrar(addon);
        long count = addon.getIslands().getIslands(addon.getOverWorld()).stream()
                .filter(island -> island.getOwner() == null && island.getCenter() != null)
                .map(island -> engine.islandAt(island.getCenter().getBlockX(), island.getCenter().getBlockZ())
                        .map(spec -> {
                            registrar.applyBandFlags(island, spec);
                            return spec;
                        }))
                .flatMap(java.util.Optional::stream)
                .count();
        user.sendMessage("tradewinds.commands.admin.reflag.done", TextVariables.NUMBER, String.valueOf(count));
        return true;
    }
}
