package world.bentobox.tradewinds.travel;

import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.server.MapInitializeEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.MapMeta;
import org.bukkit.map.MapView;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import world.bentobox.bentobox.database.Database;
import world.bentobox.tradewinds.TradeWinds;
import world.bentobox.tradewinds.dataobjects.TWWorldData;

/**
 * Hands out the Star Chart: a shared contextual MapView rendered per holder by
 * {@link StarChartRenderer}. The view's id persists in TWWorldData so the
 * renderer re-attaches on MapInitializeEvent after restarts - old chart items
 * keep working forever.
 *
 * @author tastybento
 */
public class StarChartService implements Listener {

    private final TradeWinds addon;
    private final Database<TWWorldData> handler;
    private TWWorldData data;
    private MapView view;

    public StarChartService(TradeWinds addon) {
        this.addon = addon;
        this.handler = new Database<>(addon, TWWorldData.class);
        this.data = handler.objectExists(TWWorldData.KEY) ? handler.loadObject(TWWorldData.KEY) : null;
        if (data == null) {
            data = new TWWorldData();
        }
    }

    /**
     * Give (or hand another copy of) the Star Chart to a player.
     */
    public void give(Player player) {
        ItemStack item = new ItemStack(Material.FILLED_MAP);
        if (item.getItemMeta() instanceof MapMeta meta) {
            meta.setMapView(chartView());
            meta.displayName(Component.text("Star Chart", NamedTextColor.AQUA));
            item.setItemMeta(meta);
        }
        player.getInventory().addItem(item).values()
                .forEach(left -> player.getWorld().dropItem(player.getLocation(), left));
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
