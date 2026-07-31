package world.bentobox.tradewinds.dataobjects;

import com.google.gson.annotations.Expose;

import world.bentobox.bentobox.database.objects.DataObject;
import world.bentobox.bentobox.database.objects.Table;

/**
 * Singleton world-level state (spec §9). Currently: the star chart's map id so
 * the custom renderer can be re-attached after restarts.
 *
 * @author tastybento
 */
@Table(name = "TWWorldData")
public class TWWorldData implements DataObject {

    public static final String KEY = "world";

    @Expose
    private String uniqueId = KEY;

    /**
     * Bukkit map id of the shared Star Chart view, or -1 if never created.
     */
    @Expose
    private int starChartMapId = -1;

    @Override
    public String getUniqueId() {
        return uniqueId;
    }

    @Override
    public void setUniqueId(String uniqueId) {
        this.uniqueId = uniqueId;
    }

    public int getStarChartMapId() {
        return starChartMapId;
    }

    public void setStarChartMapId(int starChartMapId) {
        this.starChartMapId = starChartMapId;
    }
}
