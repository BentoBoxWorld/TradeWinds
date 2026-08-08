package world.bentobox.tradewinds.travel;

import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.UUID;

import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.event.inventory.CraftItemEvent;
import org.bukkit.inventory.ItemStack;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import world.bentobox.tradewinds.CommonTestSetup;
import world.bentobox.tradewinds.Settings;
import world.bentobox.tradewinds.TestHolds;
import world.bentobox.tradewinds.TradeWinds;
import world.bentobox.tradewinds.ocean.OceanConfig;
import world.bentobox.tradewinds.ocean.OceanEngine;

/**
 * Tests for BoatCraftListener: craft-based boat upgrades following the One Boat Rule.
 * - Crafting a larger boat replaces current
 * - Crafting smaller/equal sized boat is refused
 * - Creative mode handles cursor items correctly
 *
 * @author tastybento
 */
class BoatCraftListenerTest extends CommonTestSetup {

    private static final long SEED = 4242L;

    private TradeWinds addon;
    private BoatCraftListener listener;
    private TestHolds holds;
    private UUID playerId;
    private Player player;

    @Override
    @BeforeEach
    public void setUp() throws Exception {
        super.setUp();
        playerId = uuid;
        player = mockPlayer;
        addon = mock(TradeWinds.class);
        when(addon.getSettings()).thenReturn(new Settings());
        when(addon.getBoatRanks()).thenReturn(new BoatRanks(addon));
        when(addon.getOverWorld()).thenReturn(world);
        when(addon.getNetherWorld()).thenReturn(null);
        when(world.getSeed()).thenReturn(SEED);
        when(addon.getOceanEngine(anyLong())).thenReturn(new OceanEngine(new OceanConfig(SEED, 2500, 160, 45, 1.0, 0, 5000, 70)));
        holds = TestHolds.install(addon);
        when(addon.getFuelService()).thenReturn(new FuelService(addon));
        when(addon.getHoldService()).thenReturn(new HoldService(addon));
        when(addon.getBoatService()).thenReturn(new BoatService(addon));
        listener = new BoatCraftListener(addon);
        when(player.getWorld()).thenReturn(world);
    }

    @Test
    void testCraftingLargerBoatReplacesCurrent() {
        // Player has OAK_BOAT (2 slots), crafts CHERRY_BOAT (6 slots)
        holds.giveBoat(playerId, Material.OAK_BOAT);

        // Mock HoldService first with its behavior
        HoldService holdService = mock(HoldService.class);
        when(holdService.boat(playerId)).thenReturn(Material.OAK_BOAT);
        when(addon.getHoldService()).thenReturn(holdService);

        ItemStack result = new ItemStack(Material.CHERRY_BOAT);
        CraftItemEvent event = mock(CraftItemEvent.class);
        when(event.getWhoClicked()).thenReturn(player);
        org.bukkit.inventory.Recipe recipe = mock(org.bukkit.inventory.Recipe.class);
        when(event.getRecipe()).thenReturn(recipe);
        when(recipe.getResult()).thenReturn(result);

        listener.onCraft(event);

        verify(event, never()).setCancelled(true);
    }

    @Test
    void testCraftingSmallerBoatIsRefused() {
        // Player has CHERRY_BOAT (6 slots), tries to craft OAK_BOAT (2 slots)
        holds.giveBoat(playerId, Material.CHERRY_BOAT);

        // Mock HoldService first with its behavior
        HoldService holdService = mock(HoldService.class);
        when(holdService.boat(playerId)).thenReturn(Material.CHERRY_BOAT);
        when(addon.getHoldService()).thenReturn(holdService);

        ItemStack result = new ItemStack(Material.OAK_BOAT);
        CraftItemEvent event = mock(CraftItemEvent.class);
        when(event.getWhoClicked()).thenReturn(player);
        org.bukkit.inventory.Recipe recipe1 = mock(org.bukkit.inventory.Recipe.class);
        when(event.getRecipe()).thenReturn(recipe1);
        when(recipe1.getResult()).thenReturn(result);

        listener.onCraft(event);

        verify(event).setCancelled(true);
    }

    @Test
    void testCraftingEqualSizedBoatIsRefused() {
        // Player has OAK_BOAT, tries to craft another OAK_BOAT
        holds.giveBoat(playerId, Material.OAK_BOAT);

        // Mock HoldService first with its behavior
        HoldService holdService = mock(HoldService.class);
        when(holdService.boat(playerId)).thenReturn(Material.OAK_BOAT);
        when(addon.getHoldService()).thenReturn(holdService);

        ItemStack result = new ItemStack(Material.OAK_BOAT);
        CraftItemEvent event = mock(CraftItemEvent.class);
        when(event.getWhoClicked()).thenReturn(player);
        org.bukkit.inventory.Recipe recipe2 = mock(org.bukkit.inventory.Recipe.class);
        when(event.getRecipe()).thenReturn(recipe2);
        when(recipe2.getResult()).thenReturn(result);

        listener.onCraft(event);

        verify(event).setCancelled(true);
    }

    @Test
    void testCraftingBoatWithoutCurrentBoat() {
        // Player has no boat, crafts OAK_BOAT

        // Mock HoldService first with null return
        HoldService holdService = mock(HoldService.class);
        when(holdService.boat(playerId)).thenReturn(null);
        when(addon.getHoldService()).thenReturn(holdService);

        ItemStack result = new ItemStack(Material.OAK_BOAT);
        CraftItemEvent event = mock(CraftItemEvent.class);
        when(event.getWhoClicked()).thenReturn(player);
        org.bukkit.inventory.Recipe recipe3 = mock(org.bukkit.inventory.Recipe.class);
        when(event.getRecipe()).thenReturn(recipe3);
        when(recipe3.getResult()).thenReturn(result);

        listener.onCraft(event);

        verify(event, never()).setCancelled(true);
    }

    @Test
    void testCraftingNonBoatIsIgnored() {
        ItemStack result = new ItemStack(Material.DIAMOND_PICKAXE);
        CraftItemEvent event = mock(CraftItemEvent.class);
        when(event.getWhoClicked()).thenReturn(player);
        org.bukkit.inventory.Recipe recipe4 = mock(org.bukkit.inventory.Recipe.class);
        when(event.getRecipe()).thenReturn(recipe4);
        when(recipe4.getResult()).thenReturn(result);

        listener.onCraft(event);

        verify(event, never()).setCancelled(true);
    }

    @Test
    void testChestBoatUpgradeAllowed() {
        // OAK_BOAT + chest -> OAK_CHEST_BOAT (same rank, chest is upgrade)
        holds.giveBoat(playerId, Material.OAK_BOAT);

        // Mock HoldService first with its behavior
        HoldService holdService = mock(HoldService.class);
        when(holdService.boat(playerId)).thenReturn(Material.OAK_BOAT);
        when(addon.getHoldService()).thenReturn(holdService);

        ItemStack result = new ItemStack(Material.OAK_CHEST_BOAT);
        CraftItemEvent event = mock(CraftItemEvent.class);
        when(event.getWhoClicked()).thenReturn(player);
        org.bukkit.inventory.Recipe recipe5 = mock(org.bukkit.inventory.Recipe.class);
        when(event.getRecipe()).thenReturn(recipe5);
        when(recipe5.getResult()).thenReturn(result);

        listener.onCraft(event);

        verify(event, never()).setCancelled(true);
    }
}
