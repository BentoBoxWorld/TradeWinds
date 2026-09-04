package world.bentobox.tradewinds.travel;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.Sound;
import org.bukkit.entity.Boat;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.inventory.ClickType;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryCloseEvent;
import org.bukkit.event.inventory.InventoryDragEvent;
import org.bukkit.event.player.PlayerInteractEntityEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;

import world.bentobox.bentobox.api.user.User;
import world.bentobox.tradewinds.economy.ItemNames;
import world.bentobox.tradewinds.TradeWinds;

/**
 * The hold GUI: a 54-slot window onto the player's VIRTUAL hold. The
 * inventory shown is a rendering, never storage - every click is cancelled
 * and interpreted as a command, so no inventory trick can extract cargo:
 * <ul>
 * <li>click an item in your pack → deposit it (fuel routes to the fuel row,
 * containers and vessels are refused, cargo is one-way),</li>
 * <li>click a cargo stack → select it; click the TNT slot → destroy it,</li>
 * <li>click a fuel stack → take it back (fuel moves freely both ways),</li>
 * <li>click an installed expander → its own nested 21-slot panel (Pale Oak
 * Chest Boat only; elsewhere it rides along inert). Right-click an EMPTY
 * expander to select it for the TNT.</li>
 * </ul>
 * Opening: sneak-right-click the boat entity; right-click while riding
 * (empty hand); right-click the boat item in your inventory screen; and on
 * chest-variant boats, simply opening your inventory while riding (the
 * vanilla chest-boat gesture - plain boats cannot send that signal).
 * Layout (6 rows): border panes; TNT destroy slot top-left; 21 cargo slots
 * (locked ones render as panes until the boat rank unlocks them); a divider;
 * 7 fuel slots. The nested expander panel is the same idea without the fuel
 * row.
 *
 * @author tastybento
 */
public class HoldGui implements Listener {
    /**
     * Disambiguates User#getTranslationAsComponent, whose no-variable call is
     * ambiguous between the String... and TagResolver... overloads - and a
     * shared constant is not an array creation, which is Sonar's complaint.
     */
    private static final String[] NO_VARS = new String[0];


    private static final int SIZE = 54;
    private static final int NESTED_SIZE = 45;
    private static final int TNT_SLOT = 1;
    private static final String PLACEHOLDER_NUMBER = "[number]";
    private static final String MESSAGE_FUEL_FULL = "tradewinds.hold.fuel-full";
    private static final String SUFFIX_STAINED_GLASS_PANE = "STAINED_GLASS_PANE";
    private static final String PLACEHOLDER_AMOUNT = "[amount]";
    private static final String PLACEHOLDER_MATERIAL = "[material]";
    /** Cargo slots: rows 1-3, columns 1-7 (both windows). */
    private static final int[] CARGO = new int[HoldService.MAX_CARGO_SLOTS];
    /** Fuel slots: row 5, columns 1-7 (main window only). */
    private static final int[] FUEL = new int[HoldService.FUEL_SLOTS];
    static {
        int i = 0;
        for (int row = 1; row <= 3; row++) {
            for (int col = 1; col <= 7; col++) {
                CARGO[i++] = row * 9 + col;
            }
        }
        for (int col = 1; col <= 7; col++) {
            FUEL[col - 1] = 5 * 9 + col;
        }
    }

    /**
     * A selection waiting for the TNT: a cargo stack (material + amount) or
     * an empty expander (expanderIndex >= 0, material null).
     */
    private record Selection(ItemStack item, int amount, int expanderIndex) {
    }

    /** A nested expander window. */
    private record Nested(Inventory inventory, int index) {
    }

    /**
     * A cargo stack "on the cursor". The pickup is cosmetic - nothing leaves
     * the database until the stack is dropped somewhere that means something -
     * so closing the window with a full cursor loses nothing.
     */
    private record Held(ItemStack item, int amount) {
    }

    private final TradeWinds addon;
    private final Map<UUID, Inventory> open = new HashMap<>();
    private final Map<UUID, Selection> selected = new HashMap<>();
    private final Map<UUID, Nested> nested = new HashMap<>();
    private final Map<UUID, Selection> nestedSelected = new HashMap<>();
    private final Map<UUID, Held> held = new HashMap<>();

    public HoldGui(TradeWinds addon) {
        this.addon = addon;
    }

    /**
     * Open the hold window.
     */
    public void open(Player player) {
        Inventory inv = Bukkit.createInventory(null, SIZE,
                User.getInstance(player).getTranslationAsComponent("tradewinds.hold.title", "[slots]",
                        String.valueOf(addon.getHoldService().capacitySlots(player))));
        render(player, inv);
        player.openInventory(inv);
        open.put(player.getUniqueId(), inv);
        player.playSound(player.getLocation(), Sound.BLOCK_BARREL_OPEN, 0.6f, 1.1f);
    }

    /**
     * Open one expander's nested panel.
     */
    private void openNested(Player player, int index) {
        Inventory inv = Bukkit.createInventory(null, NESTED_SIZE,
                User.getInstance(player).getTranslationAsComponent("tradewinds.hold.expander-title",
                        PLACEHOLDER_NUMBER, String.valueOf(index + 1)));
        renderNested(player, inv, index);
        player.openInventory(inv);
        nested.put(player.getUniqueId(), new Nested(inv, index));
    }

    private void render(Player player, Inventory inv) {
        HoldService hold = addon.getHoldService();
        User user = User.getInstance(player);
        UUID id = player.getUniqueId();
        ItemStack border = pane(Material.LIGHT_BLUE_STAINED_GLASS_PANE, user, "tradewinds.hold.border",
                "tradewinds.hold.border-lore");
        for (int slot = 0; slot < SIZE; slot++) {
            inv.setItem(slot, border);
        }
        inv.setItem(TNT_SLOT, tnt(user));
        // Cargo: consolidated stacks, then installed expanders, then space.
        // A stack riding the cursor is hidden here, or it would show twice.
        List<ItemStack> stacks = displayCargo(id);
        int expanders = hold.expanderCount(id);
        int capacity = hold.capacitySlots(player);
        for (int i = 0; i < CARGO.length; i++) {
            if (i < stacks.size()) {
                inv.setItem(CARGO[i], stacks.get(i));
            } else if (i < stacks.size() + expanders) {
                inv.setItem(CARGO[i], expanderItem(user, id, i - stacks.size()));
            } else if (i < capacity) {
                inv.setItem(CARGO[i], null);
            } else {
                inv.setItem(CARGO[i], pane(Material.GRAY_STAINED_GLASS_PANE, user, "tradewinds.hold.locked",
                        "tradewinds.hold.locked-lore"));
            }
        }
        // Fuel row
        List<ItemStack> fuelStacks = stacksOf(hold.fuelContents(id));
        for (int i = 0; i < FUEL.length; i++) {
            inv.setItem(FUEL[i], i < fuelStacks.size() ? fuelStacks.get(i)
                    : pane(Material.PINK_STAINED_GLASS_PANE, user, "tradewinds.hold.fuel-slot",
                            "tradewinds.hold.fuel-slot-lore"));
        }
    }

    private void renderNested(Player player, Inventory inv, int index) {
        HoldService hold = addon.getHoldService();
        User user = User.getInstance(player);
        ItemStack border = pane(Material.WHITE_STAINED_GLASS_PANE, user, "tradewinds.hold.border", null);
        for (int slot = 0; slot < NESTED_SIZE; slot++) {
            inv.setItem(slot, border);
        }
        inv.setItem(TNT_SLOT, tnt(user));
        List<ItemStack> stacks = hold.expanderCargo(player.getUniqueId(), index);
        for (int i = 0; i < HoldService.EXPANDER_SLOTS; i++) {
            inv.setItem(CARGO[i], i < stacks.size() ? stacks.get(i) : null);
        }
    }

    private ItemStack tnt(User user) {
        ItemStack tnt = new ItemStack(Material.TNT);
        ItemMeta meta = tnt.getItemMeta();
        meta.displayName(user.getTranslationAsComponent("tradewinds.hold.tnt", NO_VARS));
        meta.lore(List.of(user.getTranslationAsComponent("tradewinds.hold.tnt-lore", NO_VARS)));
        tnt.setItemMeta(meta);
        return tnt;
    }

    private ItemStack expanderItem(User user, UUID id, int index) {
        ItemStack item = new ItemStack(Material.WHITE_SHULKER_BOX);
        ItemMeta meta = item.getItemMeta();
        meta.displayName(user.getTranslationAsComponent("tradewinds.hold.expander",
                PLACEHOLDER_NUMBER, String.valueOf(index + 1)));
        String loreKey = addon.getHoldService().expandersOpenable(id) ? "tradewinds.hold.expander-lore"
                : "tradewinds.hold.expander-inert-lore";
        meta.lore(List.of(user.getTranslationAsComponent(loreKey, NO_VARS)));
        item.setItemMeta(meta);
        return item;
    }

    /**
     * The cargo stacks as the window should draw them: the database's, minus
     * whatever is currently riding the cursor. Copies - the database's own
     * stacks must never be mutated for display.
     */
    private List<ItemStack> displayCargo(UUID id) {
        Held holding = held.get(id);
        List<ItemStack> out = new ArrayList<>();
        int hide = holding == null ? 0 : holding.amount();
        for (ItemStack stack : addon.getHoldService().cargo(id)) {
            int amount = stack.getAmount();
            if (hide > 0 && CargoStore.stacksTogether(stack, holding.item())) {
                int hidden = Math.min(hide, amount);
                amount -= hidden;
                hide -= hidden;
            }
            if (amount > 0) {
                out.add(CargoStore.copyOf(stack, amount));
            }
        }
        return out;
    }

    /**
     * A FUEL map as max-size stacks, display order preserved. Cargo no longer
     * needs this - it is already stored one stack per slot.
     */
    private static List<ItemStack> stacksOf(Map<Material, Integer> contents) {
        List<ItemStack> stacks = new ArrayList<>();
        contents.forEach((material, amount) -> {
            int left = amount;
            int max = Math.max(1, material.getMaxStackSize());
            while (left > 0) {
                int size = Math.min(left, max);
                stacks.add(new ItemStack(material, size));
                left -= size;
            }
        });
        return stacks;
    }

    private ItemStack pane(Material material, User user, String nameKey, String loreKey) {
        ItemStack pane = new ItemStack(material);
        ItemMeta meta = pane.getItemMeta();
        meta.displayName(user.getTranslationAsComponent(nameKey, NO_VARS));
        if (loreKey != null) {
            meta.lore(List.of(user.getTranslationAsComponent(loreKey, NO_VARS)));
        }
        pane.setItemMeta(meta);
        return pane;
    }

    // ------------------------------------------------------------ open gestures

    /**
     * Riding your boat, empty main hand, right-click: open the hold.
     */
    @EventHandler(priority = EventPriority.LOW)
    public void onRightClickRiding(PlayerInteractEvent event) {
        if (event.getHand() != EquipmentSlot.HAND
                || (event.getAction() != Action.RIGHT_CLICK_AIR && event.getAction() != Action.RIGHT_CLICK_BLOCK)
                || event.useItemInHand() == org.bukkit.event.Event.Result.DENY
                || (event.getItem() != null && !event.getItem().getType().isAir())
                || !(event.getPlayer().getVehicle() instanceof Boat)) {
            return;
        }
        event.setCancelled(true);
        open(event.getPlayer());
    }

    /**
     * Sneak + right-click a boat entity: open the hold instead of mounting.
     */
    @EventHandler(priority = EventPriority.LOW, ignoreCancelled = true)
    public void onSneakClickBoat(PlayerInteractEntityEvent event) {
        if (event.getHand() != EquipmentSlot.HAND || !(event.getRightClicked() instanceof Boat)
                || !event.getPlayer().isSneaking()) {
            return;
        }
        event.setCancelled(true);
        open(event.getPlayer());
    }

    /**
     * Pressing the inventory key while riding a CHEST-variant boat: the
     * client asks the server to open the vehicle's chest, and the hold opens
     * instead - exactly the vanilla chest-boat gesture, showing the hold on
     * top and the player's inventory below. (Plain boats never send that
     * packet - the client draws the ordinary inventory entirely on its own -
     * so for them the in-boat right-click and the boat-item click serve.)
     * This also closes the last route to a chest boat's REAL inventory: no
     * shadow storage.
     */
    @EventHandler(priority = EventPriority.LOW, ignoreCancelled = true)
    public void onVehicleInventoryOpen(org.bukkit.event.inventory.InventoryOpenEvent event) {
        if (!(event.getPlayer() instanceof Player player) || !(player.getVehicle() instanceof Boat)
                || !(event.getInventory().getHolder() instanceof org.bukkit.entity.ChestBoat)) {
            return;
        }
        event.setCancelled(true);
        Bukkit.getScheduler().runTask(addon.getPlugin(), () -> open(player));
    }

    // ---------------------------------------------------------------- windows

    @EventHandler(priority = EventPriority.LOW)
    public void onClick(InventoryClickEvent event) {
        if (!(event.getWhoClicked() instanceof Player player)) {
            return;
        }
        UUID id = player.getUniqueId();
        Inventory top = event.getView().getTopInventory();
        Nested nestedView = nested.get(id);
        if (nestedView != null && top.equals(nestedView.inventory())) {
            event.setCancelled(true);
            Inventory clicked = event.getClickedInventory();
            if (clicked != null && clicked != top) {
                depositNested(player, event.getCurrentItem(), nestedView.index());
            } else if (clicked == top) {
                nestedTopClick(player, event.getSlot(), event.getCurrentItem(), nestedView.index());
            }
            renderNested(player, nestedView.inventory(), nestedView.index());
            return;
        }
        if (top.equals(open.get(id))) {
            event.setCancelled(true);
            Inventory clicked = event.getClickedInventory();
            Held holding = held.get(id);
            if (holding != null) {
                heldClick(player, clicked, top, event.getSlot(), event.getClick(), holding);
            } else if (clicked != null && clicked != top) {
                deposit(player, event.getCurrentItem());
            } else if (clicked == top) {
                topClick(player, event.getSlot(), event.getCurrentItem(), event.getClick());
            }
            render(player, top);
            return;
        }
        boatItemGesture(event, player);
    }

    /**
     * Right-clicking your boat item in the inventory screen opens the hold -
     * the bundle-like gesture players reach for first.
     */
    private void boatItemGesture(InventoryClickEvent event, Player player) {
        ItemStack item = event.getCurrentItem();
        if (event.getClick() != ClickType.RIGHT || item == null
                || !event.getCursor().getType().isAir()
                || event.getClickedInventory() != player.getInventory()) {
            return;
        }
        // By IDENTITY, not by type: matching on material meant any oak boat in
        // the pack opened the hold of whatever oak boat you owned (2026-08-02)
        String clickedId = BoatService.boatId(item);
        var active = addon.getHoldService().active(player.getUniqueId());
        boolean mine = active.map(hold -> hold.getUniqueId().equals(clickedId)).orElse(false);
        if (!mine && clickedId == null && active.isPresent()
                && item.getType() == Material.matchMaterial(active.get().getMaterial())
                && !addon.getBoatService().isCarrying(player, active.get())) {
            // An unstamped hull of exactly our boat's type, and no other
            // avatar of it in the pack: adopt it. Self-heals a hull that
            // reached the player without its record - a crafted boat left on
            // the cursor did exactly that (2026-08-02), and such an item can
            // otherwise never be opened at all.
            addon.getBoatService().stamp(item, active.get());
            mine = true;
        }
        if (!mine) {
            return;
        }
        event.setCancelled(true);
        Bukkit.getScheduler().runTask(addon.getPlugin(), () -> open(player));
    }

    private void deposit(Player player, ItemStack stack) {
        if (stack == null || stack.getType().isAir()) {
            return;
        }
        HoldService hold = addon.getHoldService();
        Material material = stack.getType();
        User user = User.getInstance(player);
        if (addon.getFuelService().fuelValue(material) > 0) {
            int added = hold.addFuel(player, material, stack.getAmount());
            if (added <= 0) {
                user.sendMessage(MESSAGE_FUEL_FULL);
                thud(player);
            } else {
                stack.setAmount(stack.getAmount() - added);
                chime(player);
            }
            return;
        }
        if (hold.refuses(material)) {
            user.sendMessage(BoatRanks.isBoatItem(material) ? "tradewinds.hold.boat-refused"
                    : "tradewinds.hold.container-refused");
            thud(player);
            return;
        }
        int added = hold.add(player, stack, stack.getAmount());
        if (added <= 0) {
            user.sendMessage("tradewinds.hold.cargo-full");
            thud(player);
        } else {
            stack.setAmount(stack.getAmount() - added);
            chime(player);
        }
    }

    /**
     * Deposits while a nested panel is open: fuel still routes to the main
     * fuel row (fuel dropped anywhere in the GUI finds a fuel slot); cargo
     * goes into THIS expander.
     */
    private void depositNested(Player player, ItemStack stack, int index) {
        if (stack == null || stack.getType().isAir()) {
            return;
        }
        HoldService hold = addon.getHoldService();
        Material material = stack.getType();
        User user = User.getInstance(player);
        if (addon.getFuelService().fuelValue(material) > 0) {
            int added = hold.addFuel(player, material, stack.getAmount());
            if (added <= 0) {
                user.sendMessage(MESSAGE_FUEL_FULL);
                thud(player);
            } else {
                stack.setAmount(stack.getAmount() - added);
                chime(player);
            }
            return;
        }
        if (hold.refuses(material)) {
            user.sendMessage(BoatRanks.isBoatItem(material) ? "tradewinds.hold.boat-refused"
                    : "tradewinds.hold.container-refused");
            thud(player);
            return;
        }
        int added = hold.addToExpander(player, index, material, stack.getAmount());
        if (added <= 0) {
            user.sendMessage("tradewinds.hold.cargo-full");
            thud(player);
        } else {
            stack.setAmount(stack.getAmount() - added);
            chime(player);
        }
    }

    private void topClick(Player player, int slot, ItemStack shown, ClickType click) {
        UUID id = player.getUniqueId();
        HoldService hold = addon.getHoldService();
        if (slot == TNT_SLOT) {
            destroySelection(player, selected.remove(id));
            return;
        }
        if (isFuelSlot(slot)) {
            withdrawFuel(player, shown);
            return;
        }
        int index = cargoIndex(slot);
        if (index < 0 || shown == null || shown.getType().isAir()
                || shown.getType().name().endsWith(SUFFIX_STAINED_GLASS_PANE)) {
            selected.remove(id);
            return;
        }
        if (handleExpanderClick(player, hold, id, index, click)) {
            return;
        }
        handleCargoClick(player, id, shown, click);
    }

    private boolean handleExpanderClick(Player player, HoldService hold, UUID id, int index,
            ClickType click) {
        int stackCount = hold.cargo(id).size();
        if (index >= stackCount && index < stackCount + hold.expanderCount(id)) {
            int expander = index - stackCount;
            if (click == ClickType.RIGHT) {
                selectExpanderForTnt(player, hold, id, expander);
            } else {
                openExpanderPanel(player, hold, id, expander);
            }
            return true;
        }
        return false;
    }

    private void selectExpanderForTnt(Player player, HoldService hold, UUID id, int expander) {
        User user = User.getInstance(player);
        if (hold.expanderCargo(id, expander).isEmpty()) {
            selected.put(id, new Selection(null, 0, expander));
            user.sendMessage("tradewinds.hold.expander-selected", PLACEHOLDER_NUMBER,
                    String.valueOf(expander + 1));
        } else {
            user.sendMessage("tradewinds.hold.expander-not-empty");
            thud(player);
        }
    }

    private void openExpanderPanel(Player player, HoldService hold, UUID id, int expander) {
        User user = User.getInstance(player);
        if (!hold.expandersOpenable(id)) {
            user.sendMessage("tradewinds.hold.expander-inert");
            thud(player);
            return;
        }
        Bukkit.getScheduler().runTask(addon.getPlugin(), () -> openNested(player, expander));
    }

    private void handleCargoClick(Player player, UUID id, ItemStack shown, ClickType click) {
        if (click == ClickType.RIGHT) {
            selected.put(id, new Selection(shown, shown.getAmount(), -1));
            User user = User.getInstance(player);
            user.sendMessage("tradewinds.hold.selected", PLACEHOLDER_AMOUNT, String.valueOf(shown.getAmount()),
                    PLACEHOLDER_MATERIAL, ItemNames.label(user, shown.getType()));
            return;
        }
        if (click.isShiftClick()) {
            if (addon.getFuelService().fuelValue(shown.getType()) > 0) {
                moveCargoFuel(player, shown);
            } else {
                withdrawCargo(player, shown);
            }
            return;
        }
        held.put(id, new Held(shown, shown.getAmount()));
        cursor(player, CargoStore.copyOf(shown, shown.getAmount()));
    }

    /**
     * A click while a cargo stack rides the cursor. Vanilla semantics as far as
     * they translate: drop on the fuel row to fuel it (right-click feeds one at
     * a time), drop in your own inventory to take it ashore (refused for
     * trader-bought cargo), anywhere else in the window puts it back.
     */
    private void heldClick(Player player, Inventory clicked, Inventory top, int slot, ClickType click,
            Held holding) {
        User user = User.getInstance(player);
        if (clicked == top) {
            if (isFuelSlot(slot)) {
                placeFuel(player, holding, click == ClickType.RIGHT ? 1 : holding.amount());
                return;
            }
            putBack(player);
            return;
        }
        if (clicked == null) {
            // Clicked outside the window entirely: put it back, never overboard
            putBack(player);
            return;
        }
        // Dropped in the player's own inventory: take it ashore
        int taken = addon.getHoldService().withdraw(player, holding.item(), holding.amount());
        if (taken < 0) {
            user.sendMessage("tradewinds.hold.bought-cargo-locked");
            thud(player);
            putBack(player);
            return;
        }
        if (taken == 0) {
            user.sendMessage("tradewinds.hold.withdraw-failed");
            thud(player);
            putBack(player);
            return;
        }
        user.sendMessage("tradewinds.hold.withdrawn", PLACEHOLDER_AMOUNT, String.valueOf(taken), PLACEHOLDER_MATERIAL,
                ItemNames.label(user, holding.item().getType()));
        chime(player);
        clearHeld(player);
    }

    /**
     * Feed the tank from the cursor. Whatever does not fit stays on the cursor,
     * exactly as a furnace refuses what its fuel slot cannot take.
     */
    private void placeFuel(Player player, Held holding, int amount) {
        UUID id = player.getUniqueId();
        User user = User.getInstance(player);
        if (addon.getFuelService().fuelValue(holding.item().getType()) <= 0) {
            user.sendMessage("tradewinds.hold.fuel-only");
            thud(player);
            return;
        }
        int moved = addon.getHoldService().moveCargoToFuel(player, holding.item(),
                Math.min(amount, holding.amount()));
        if (moved <= 0) {
            user.sendMessage(MESSAGE_FUEL_FULL);
            thud(player);
            return;
        }
        chime(player);
        int left = holding.amount() - moved;
        if (left <= 0) {
            clearHeld(player);
        } else {
            held.put(id, new Held(holding.item(), left));
            cursor(player, CargoStore.copyOf(holding.item(), left));
        }
    }

    private void putBack(Player player) {
        // The pickup never touched the database, so putting back is forgetting
        clearHeld(player);
    }

    private void clearHeld(Player player) {
        held.remove(player.getUniqueId());
        cursor(player, null);
    }

    /**
     * Show (or clear) the cursor stack. A tick later, because the server
     * restates the cursor after a cancelled click and would wipe it.
     */
    private void cursor(Player player, ItemStack item) {
        Bukkit.getScheduler().runTask(addon.getPlugin(), () -> player.setItemOnCursor(item));
    }

    private static boolean isFuelSlot(int slot) {
        for (int fuelSlot : FUEL) {
            if (fuelSlot == slot) {
                return true;
            }
        }
        return false;
    }

    /**
     * Take cargo out of the hold. Trader-bought cargo is refused: the one-way
     * rule still holds for anything a market sold you.
     */
    private void withdrawCargo(Player player, ItemStack shown) {
        User user = User.getInstance(player);
        int taken = addon.getHoldService().withdraw(player, shown, shown.getAmount());
        if (taken < 0) {
            user.sendMessage("tradewinds.hold.bought-cargo-locked");
            thud(player);
        } else if (taken == 0) {
            user.sendMessage("tradewinds.hold.withdraw-failed");
            thud(player);
        } else {
            user.sendMessage("tradewinds.hold.withdrawn", PLACEHOLDER_AMOUNT, String.valueOf(taken), PLACEHOLDER_MATERIAL,
                    ItemNames.label(user, shown.getType()));
            chime(player);
        }
    }

    private void moveCargoFuel(Player player, ItemStack shown) {
        int moved = addon.getHoldService().moveCargoToFuel(player, shown.getType(), shown.getAmount());
        User user = User.getInstance(player);
        if (moved > 0) {
            user.sendMessage("tradewinds.hold.moved-to-fuel", PLACEHOLDER_AMOUNT, String.valueOf(moved),
                    PLACEHOLDER_MATERIAL, ItemNames.label(user, shown.getType()));
            chime(player);
        } else {
            user.sendMessage(MESSAGE_FUEL_FULL);
            thud(player);
        }
    }

    private void nestedTopClick(Player player, int slot, ItemStack shown, int index) {
        User user = User.getInstance(player);
        UUID id = player.getUniqueId();
        if (slot == TNT_SLOT) {
            Selection selection = nestedSelected.remove(id);
            if (selection == null || selection.item() == null) {
                user.sendMessage("tradewinds.hold.select-first");
                return;
            }
            int destroyed = addon.getHoldService().removeFromExpander(id, index, selection.item(),
                    selection.amount());
            user.sendMessage("tradewinds.hold.destroyed", PLACEHOLDER_AMOUNT, String.valueOf(destroyed), PLACEHOLDER_MATERIAL,
                    ItemNames.label(user, selection.item().getType()));
            player.playSound(player.getLocation(), Sound.ENTITY_GENERIC_EXPLODE, 0.4f, 1.4f);
            return;
        }
        if (cargoIndex(slot) < 0 || shown == null || shown.getType().isAir()
                || shown.getType().name().endsWith(SUFFIX_STAINED_GLASS_PANE)) {
            nestedSelected.remove(id);
            return;
        }
        if (addon.getFuelService().fuelValue(shown.getType()) > 0) {
            moveCargoFuel(player, shown);
            return;
        }
        nestedSelected.put(id, new Selection(shown, shown.getAmount(), -1));
        user.sendMessage("tradewinds.hold.selected", PLACEHOLDER_AMOUNT, String.valueOf(shown.getAmount()),
                PLACEHOLDER_MATERIAL, ItemNames.label(user, shown.getType()));
    }

    private void destroySelection(Player player, Selection selection) {
        User user = User.getInstance(player);
        if (selection == null) {
            user.sendMessage("tradewinds.hold.select-first");
            return;
        }
        if (selection.expanderIndex() >= 0) {
            if (addon.getHoldService().destroyExpander(player.getUniqueId(), selection.expanderIndex())) {
                user.sendMessage("tradewinds.hold.expander-destroyed");
                player.playSound(player.getLocation(), Sound.ENTITY_GENERIC_EXPLODE, 0.4f, 1.4f);
            } else {
                user.sendMessage("tradewinds.hold.expander-not-empty");
                thud(player);
            }
            return;
        }
        int destroyed = addon.getHoldService().remove(player, selection.item(), selection.amount());
        user.sendMessage("tradewinds.hold.destroyed", PLACEHOLDER_AMOUNT, String.valueOf(destroyed), PLACEHOLDER_MATERIAL,
                ItemNames.label(user, selection.item().getType()));
        player.playSound(player.getLocation(), Sound.ENTITY_GENERIC_EXPLODE, 0.4f, 1.4f);
    }

    private static int cargoIndex(int slot) {
        for (int i = 0; i < CARGO.length; i++) {
            if (CARGO[i] == slot) {
                return i;
            }
        }
        return -1;
    }

    private void withdrawFuel(Player player, ItemStack shown) {
        if (shown == null || shown.getType().isAir() || shown.getType().name().endsWith(SUFFIX_STAINED_GLASS_PANE)) {
            return;
        }
        int taken = addon.getHoldService().removeFuel(player.getUniqueId(), shown.getType(), shown.getAmount());
        if (taken > 0) {
            player.getInventory().addItem(new ItemStack(shown.getType(), taken)).values()
                    .forEach(left -> player.getWorld().dropItem(player.getLocation(), left));
            chime(player);
        }
    }

    @EventHandler(priority = EventPriority.LOW)
    public void onDrag(InventoryDragEvent event) {
        if (!(event.getWhoClicked() instanceof Player player)) {
            return;
        }
        UUID id = player.getUniqueId();
        Inventory top = event.getView().getTopInventory();
        Nested nestedView = nested.get(id);
        boolean ours = top.equals(open.get(id))
                || (nestedView != null && top.equals(nestedView.inventory()));
        // Any drag touching a hold window is refused - deposits are clicks.
        // While cargo rides the cursor, ALL drags are refused: a drag over the
        // player's own slots would deposit the cursor copy for real, which is
        // duplication.
        if (ours && (held.containsKey(id)
                || event.getRawSlots().stream().anyMatch(raw -> raw < top.getSize()))) {
            event.setCancelled(true);
        }
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onClose(InventoryCloseEvent event) {
        UUID id = event.getPlayer().getUniqueId();
        Inventory closing = event.getInventory();
        Nested nestedView = nested.get(id);
        if (nestedView != null && closing.equals(nestedView.inventory())) {
            nested.remove(id);
            nestedSelected.remove(id);
            return;
        }
        if (closing.equals(open.get(id))) {
            open.remove(id);
            selected.remove(id);
            // A stack still on the cursor was never taken out of the database,
            // so clearing the cursor loses nothing - but NOT clearing it would
            // let the server hand the copy to the player, which duplicates it
            if (held.remove(id) != null && event.getPlayer() instanceof Player p) {
                p.setItemOnCursor(null);
            }
            // The carried boat item's lore shows cargo/fuel: keep it true
            if (event.getPlayer() instanceof Player player) {
                addon.getHoldService().active(id)
                        .ifPresent(hold -> addon.getBoatService().refreshItemLore(player, hold));
            }
        }
    }

    private void chime(Player player) {
        player.playSound(player.getLocation(), Sound.ENTITY_ITEM_PICKUP, 0.5f, 1.2f);
    }

    private void thud(Player player) {
        player.playSound(player.getLocation(), Sound.BLOCK_ANVIL_LAND, 0.5f, 1.4f);
    }
}
