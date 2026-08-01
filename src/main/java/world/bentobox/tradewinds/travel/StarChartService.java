package world.bentobox.tradewinds.travel;

import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.PlayerDeathEvent;
import org.bukkit.event.player.PlayerDropItemEvent;
import org.bukkit.event.player.PlayerItemHeldEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.event.server.MapInitializeEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.PlayerInventory;
import org.bukkit.inventory.meta.MapMeta;
import org.bukkit.map.MapView;
import org.bukkit.persistence.PersistentDataType;

import world.bentobox.bentobox.api.user.User;
import world.bentobox.bentobox.database.Database;
import world.bentobox.tradewinds.TradeWinds;
import world.bentobox.tradewinds.dataobjects.TWWorldData;

/**
 * Hands out the Star Chart: a shared contextual MapView rendered per holder by
 * {@link StarChartRenderer}. The view's id persists in TWWorldData so the
 * renderer re-attaches on MapInitializeEvent after restarts - old chart items
 * keep working forever.
 * <p>
 * The chart is <b>ephemeral</b>: it exists only while you are looking at it.
 * Switch to another slot, drop it, die or log out and it is gone. It is an
 * instrument you consult, not cargo - and a permanent one would have meant a
 * new map item every time the command was run, filling inventories and
 * littering the sea with maps nobody wanted.
 *
 * @author tastybento
 */
public class StarChartService implements Listener {

    /** Marks an item as a Star Chart, so it can be recognised and reclaimed. */
    private static final String CHART_KEY = "starchart";

    private final TradeWinds addon;
    private final Database<TWWorldData> handler;
    private TWWorldData data;
    private MapView view;
    private NamespacedKey chartKey;

    public StarChartService(TradeWinds addon) {
        this.addon = addon;
        this.handler = new Database<>(addon, TWWorldData.class);
        this.data = handler.objectExists(TWWorldData.KEY) ? handler.loadObject(TWWorldData.KEY) : null;
        if (data == null) {
            data = new TWWorldData();
        }
    }

    private NamespacedKey key() {
        if (chartKey == null) {
            chartKey = new NamespacedKey(addon.getPlugin(), CHART_KEY);
        }
        return chartKey;
    }

    /**
     * Whether an item is a Star Chart.
     *
     * @param item the item
     * @return true if it is a chart
     */
    public boolean isStarChart(ItemStack item) {
        return item != null && item.getType() == Material.FILLED_MAP && item.hasItemMeta()
                && item.getItemMeta().getPersistentDataContainer().has(key(), PersistentDataType.BYTE);
    }

    /**
     * Put a Star Chart in the player's hand.
     * <p>
     * Any chart they already hold is reclaimed first, and the new one goes
     * into the held slot so that the "put it away and it is gone" rule applies
     * from the moment they receive it.
     *
     * @param player the player
     */
    public void give(Player player) {
        reclaim(player);
        ItemStack item = new ItemStack(Material.FILLED_MAP);
        if (item.getItemMeta() instanceof MapMeta meta) {
            meta.setMapView(chartView());
            meta.displayName(User.getInstance(player).getTranslationAsComponent("tradewinds.item.starchart",
                    new String[0]));
            meta.getPersistentDataContainer().set(key(), PersistentDataType.BYTE, (byte) 1);
            item.setItemMeta(meta);
        }
        PlayerInventory inventory = player.getInventory();
        int slot = freeHotbarSlot(inventory);
        if (slot < 0) {
            // Every hotbar slot is full: hand it over anyway rather than
            // refusing, and let them find it
            inventory.addItem(item);
            return;
        }
        inventory.setItem(slot, item);
        inventory.setHeldItemSlot(slot);
    }

    /**
     * A free hotbar slot, preferring the one already in hand.
     */
    private int freeHotbarSlot(PlayerInventory inventory) {
        if (inventory.getItemInMainHand().getType().isAir()) {
            return inventory.getHeldItemSlot();
        }
        for (int slot = 0; slot < 9; slot++) {
            if (inventory.getItem(slot) == null || inventory.getItem(slot).getType().isAir()) {
                return slot;
            }
        }
        return -1;
    }

    /**
     * Take back every Star Chart a player is carrying.
     *
     * @param player the player
     * @return how many were reclaimed
     */
    public int reclaim(Player player) {
        int taken = 0;
        PlayerInventory inventory = player.getInventory();
        for (int slot = 0; slot < inventory.getSize(); slot++) {
            if (isStarChart(inventory.getItem(slot))) {
                inventory.setItem(slot, null);
                taken++;
            }
        }
        return taken;
    }

    /**
     * Looking away puts the chart away. Only fires when the slot being left
     * actually held one, so a chart sitting in another slot is not snatched
     * before its owner has looked at it.
     */
    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onHeldItemChange(PlayerItemHeldEvent event) {
        ItemStack was = event.getPlayer().getInventory().getItem(event.getPreviousSlot());
        if (isStarChart(was) && !isStarChart(event.getPlayer().getInventory().getItem(event.getNewSlot()))) {
            reclaim(event.getPlayer());
        }
    }

    /**
     * Dropping it destroys it rather than leaving a map bobbing in the sea.
     */
    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onDrop(PlayerDropItemEvent event) {
        if (isStarChart(event.getItemDrop().getItemStack())) {
            event.getItemDrop().remove();
        }
    }

    /**
     * A chart is an instrument, not cargo: it is not part of anyone's death
     * loot, and there is no salvaging one from a wreck.
     */
    @EventHandler(priority = EventPriority.HIGHEST)
    public void onDeath(PlayerDeathEvent event) {
        event.getDrops().removeIf(this::isStarChart);
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onQuit(PlayerQuitEvent event) {
        reclaim(event.getPlayer());
    }

    private MapView chartView() {
        if (view == null) {
            if (data.getStarChartMapId() >= 0) {
                view = Bukkit.getMap(data.getStarChartMapId());
            }
            if (view == null) {
                view = Bukkit.createMap(addon.getOverWorld());
                data.setStarChartMapId(view.getId());
                handler.saveObjectAsync(data);
            }
            attachRenderer(view);
        }
        return view;
    }

    private void attachRenderer(MapView mapView) {
        mapView.getRenderers().forEach(mapView::removeRenderer);
        mapView.addRenderer(new StarChartRenderer(addon));
        mapView.setScale(MapView.Scale.FARTHEST);
        mapView.setTrackingPosition(false);
        mapView.setUnlimitedTracking(false);
        mapView.setLocked(true);
    }

    /**
     * After a restart, old Star Chart items re-initialize their view here.
     */
    @EventHandler
    public void onMapInitialize(MapInitializeEvent event) {
        if (data.getStarChartMapId() >= 0 && event.getMap().getId() == data.getStarChartMapId()) {
            view = event.getMap();
            attachRenderer(view);
        }
    }
}
