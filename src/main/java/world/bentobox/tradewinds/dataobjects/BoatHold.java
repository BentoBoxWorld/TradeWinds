package world.bentobox.tradewinds.dataobjects;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.bukkit.inventory.ItemStack;

import com.google.gson.annotations.Expose;

import world.bentobox.bentobox.database.objects.DataObject;
import world.bentobox.bentobox.database.objects.Table;

/**
 * A boat and everything in it - the hold, keyed by the BOAT, not the player
 * (ruled 2026-08-02). The boat IS the hold: capture it and you take the
 * cargo with it, abandon it and the cargo stays aboard, and a two-slot raft
 * carries two slots however rich its captain used to be.
 * <p>
 * The record's id lives in a PDC key on both the boat entity and its item
 * form, so breaking a boat, dropping it or having a mob carry the item never
 * touches the cargo: the avatar is a key, the cargo exists once, here.
 * <p>
 * An <b>unowned</b> boat (owner blank) is fair game for anyone - that is what
 * an abandoned OLD BOAT becomes, and unowned boats are never protected, even
 * in safe island space.
 *
 * @author tastybento
 */
@Table(name = "BoatHold")
public class BoatHold implements DataObject {

    @Expose
    private String uniqueId;

    /** Boat material name - this is what sets the slot count. */
    @Expose
    private String material = "";

    /** Owning player UUID, or "" for an unowned (capturable) boat. */
    @Expose
    private String owner = "";

    /**
     * Cargo, <b>one stack per occupied slot</b>, exactly like a real inventory.
     * <p>
     * A list of stacks rather than material→amount because a worn bow, a mint
     * bow and a Silk Touch pick are not the same good and must not merge.
     * BentoBox's {@code ItemStackTypeAdapter} persists these through Bukkit's
     * own YAML serializer, so enchantments, damage and potion data all survive
     * a restart. Note the adapter clamps a stack to 99, which one-stack-per-slot
     * can never exceed.
     */
    @Expose
    private List<ItemStack> cargo = new ArrayList<>();

    @Expose
    private Map<String, Integer> fuel = new LinkedHashMap<>();

    /** Installed cargo expanders, each its own slot-per-stack store. */
    @Expose
    private List<List<ItemStack>> expanders = new ArrayList<>();

    /** Last known position, so the chart can point at it from anywhere. */
    @Expose
    private String world = "";
    @Expose
    private int x;
    @Expose
    private int y;
    @Expose
    private int z;

    /**
     * Epoch millis when an ITEM-form boat is claimed by the sea. 0 means no
     * expiry: boat ENTITIES never expire (ruled 2026-08-02) - only a hull
     * lying about as an item does.
     */
    @Expose
    private long expiresAt;

    public BoatHold() {
        // Required by the database
    }

    public BoatHold(String uniqueId) {
        this.uniqueId = uniqueId;
    }

    @Override
    public String getUniqueId() {
        return uniqueId;
    }

    @Override
    public void setUniqueId(String uniqueId) {
        this.uniqueId = uniqueId;
    }

    public String getMaterial() {
        return material;
    }

    public void setMaterial(String material) {
        this.material = material == null ? "" : material;
    }

    public String getOwner() {
        return owner;
    }

    public void setOwner(String owner) {
        this.owner = owner == null ? "" : owner;
    }

    /**
     * @return true if nobody owns this boat - anyone may take or break it
     */
    public boolean isUnowned() {
        return owner == null || owner.isEmpty();
    }

    public List<ItemStack> getCargo() {
        return cargo;
    }

    public void setCargo(List<ItemStack> cargo) {
        this.cargo = cargo == null ? new ArrayList<>() : cargo;
    }

    public Map<String, Integer> getFuel() {
        return fuel;
    }

    public void setFuel(Map<String, Integer> fuel) {
        this.fuel = fuel;
    }

    public List<List<ItemStack>> getExpanders() {
        return expanders;
    }

    public void setExpanders(List<List<ItemStack>> expanders) {
        this.expanders = expanders == null ? new ArrayList<>() : expanders;
    }

    public String getWorld() {
        return world;
    }

    public void setWorld(String world) {
        this.world = world == null ? "" : world;
    }

    public int getX() {
        return x;
    }

    public void setX(int x) {
        this.x = x;
    }

    public int getY() {
        return y;
    }

    public void setY(int y) {
        this.y = y;
    }

    public int getZ() {
        return z;
    }

    public void setZ(int z) {
        this.z = z;
    }

    public long getExpiresAt() {
        return expiresAt;
    }

    public void setExpiresAt(long expiresAt) {
        this.expiresAt = expiresAt;
    }

    /**
     * @return true if this boat carries nothing at all
     */
    public boolean isEmpty() {
        return cargo.isEmpty() && fuel.isEmpty() && expanders.isEmpty();
    }
}
