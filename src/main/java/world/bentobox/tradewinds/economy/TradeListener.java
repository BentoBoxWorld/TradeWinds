package world.bentobox.tradewinds.economy;

import java.util.Optional;

import org.bukkit.entity.Player;
import org.bukkit.entity.Villager;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerInteractEntityEvent;
import org.bukkit.persistence.PersistentDataType;

import world.bentobox.tradewinds.TradeWinds;
import world.bentobox.tradewinds.galaxy.IslandSpec;
import world.bentobox.tradewinds.generator.IslandDecorator;

/**
 * Right-clicking a resident trader opens the island's market instead of the
 * (empty) vanilla trade screen.
 *
 * @author tastybento
 */
public class TradeListener implements Listener {

    private final TradeWinds addon;

    public TradeListener(TradeWinds addon) {
        this.addon = addon;
    }

    @EventHandler(priority = EventPriority.LOWEST, ignoreCancelled = true)
    public void onTraderClick(PlayerInteractEntityEvent event) {
        if (!(event.getRightClicked() instanceof Villager villager) || !villager.getPersistentDataContainer()
                .has(IslandDecorator.RESIDENT_KEY, PersistentDataType.STRING)) {
            return;
        }
        event.setCancelled(true);
        Player player = event.getPlayer();
        Optional<IslandSpec> spec = addon.getGalaxyEngine(addon.getOverWorld().getSeed())
                .islandAt(villager.getLocation().getBlockX(), villager.getLocation().getBlockZ());
        spec.ifPresent(island -> addon.getTradeDialog().openMain(player, island));
    }
}
