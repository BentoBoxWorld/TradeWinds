package world.bentobox.tradewinds;

import world.bentobox.bentobox.api.addons.Addon;
import world.bentobox.bentobox.api.addons.Pladdon;

/**
 * Pladdon wrapper so TradeWinds can load as a standard Bukkit plugin.
 */
public class TradeWindsPladdon extends Pladdon {

    private Addon addon;

    @Override
    public Addon getAddon() {
        if (addon == null) {
            addon = new TradeWinds();
        }
        return addon;
    }
}
