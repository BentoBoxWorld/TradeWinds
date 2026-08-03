package world.bentobox.tradewinds.encounters;

import java.util.List;

import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDeathEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.persistence.PersistentDataType;

import world.bentobox.tradewinds.TradeWinds;

/**
 * Booty: killing an encounter mob yields customs-stamped salvage - the only
 * money the sea itself pays, and the adventurer's answer to the trader's
 * margins and the smuggler's sugar.
 *
 * @author tastybento
 */
public class EncounterListener implements Listener {

    private final TradeWinds addon;

    public EncounterListener(TradeWinds addon) {
        this.addon = addon;
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onEncounterDeath(EntityDeathEvent event) {
        if (!event.getEntity().getPersistentDataContainer().has(EncounterService.ENCOUNTER_KEY,
                PersistentDataType.STRING)) {
            return;
        }
        Player killer = event.getEntity().getKiller();
        if (killer == null) {
            return;
        }
        List<String> table = addon.getSettings().getBootyTable();
        if (table.isEmpty() || Math.random() >= addon.getSettings().getBootyChance()) {
            return;
        }
        String entry = table.get((int) (Math.random() * table.size()));
        Material material = Material.matchMaterial(entry);
        if (material == null) {
            addon.logError("Unknown material in encounters.booty-table: " + entry);
            return;
        }
        // Plain loot: stow it in the hold and any port will buy it
        event.getDrops().add(new ItemStack(material));
    }
}
