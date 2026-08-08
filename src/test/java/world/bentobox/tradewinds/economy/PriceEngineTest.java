package world.bentobox.tradewinds.economy;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.util.List;
import java.util.Map;
import java.util.function.Function;

import org.bukkit.Material;
import org.bukkit.inventory.FurnaceRecipe;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.Recipe;
import org.bukkit.inventory.ShapelessRecipe;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import world.bentobox.tradewinds.CommonTestSetup;
import world.bentobox.tradewinds.Settings;
import world.bentobox.tradewinds.TradeWinds;

/**
 * Tests the embedded (ex-BlueBook) price engine: config prices, recipe
 * derivation, smelting fuel share, unpriceable goods, cycle safety.
 *
 * @author tastybento
 */
class PriceEngineTest extends CommonTestSetup {

    private TradeWinds addon;
    private Map<ItemStack, List<Recipe>> recipes;
    private PriceEngine engine;

    @Override
    @BeforeEach
    public void setUp() throws Exception {
        super.setUp();
        addon = mock(TradeWinds.class);
        when(addon.getSettings()).thenReturn(new Settings());
        recipes = new java.util.HashMap<>();
        Function<ItemStack, List<Recipe>> source = item -> recipes.entrySet().stream()
                .filter(e -> e.getKey().getType() == item.getType()).map(Map.Entry::getValue).findFirst()
                .orElse(List.of());
        engine = new PriceEngine(addon, source);
    }

    @Test
    void testConfiguredPrice() {
        // WHEAT is in the default base price table at 2.0
        assertEquals(20.0, engine.getPrice(new ItemStack(Material.WHEAT)));
    }

    @Test
    void testUnpriceableWithoutRecipe() {
        assertTrue(engine.getPrice(new ItemStack(Material.BEDROCK)) < 0);
        assertTrue(engine.getPrice(null) < 0);
    }

    @Test
    void testShapelessDerivation() {
        // Hypothetical: 1 GOLDEN_CARROT from 8 GOLD_NUGGET + 1 CARROT... use a
        // recipe of priced parts: BONE_MEAL-like: 1 HAY_BLOCK -> 9 WHEAT is
        // shaped in vanilla; emulate shapeless: HAY_BLOCK from 9 wheat
        ShapelessRecipe recipe = mock(ShapelessRecipe.class);
        when(recipe.getResult()).thenReturn(new ItemStack(Material.HAY_BLOCK, 1));
        when(recipe.getIngredientList()).thenReturn(List.of(new ItemStack(Material.WHEAT, 9)));
        recipes.put(new ItemStack(Material.HAY_BLOCK), List.of(recipe));
        // HAY_BLOCK is configured at 18.0 in defaults - remove to force derivation
        addonSettingsWithout("HAY_BLOCK");
        assertEquals(9 * 20.0, engine.getPrice(new ItemStack(Material.HAY_BLOCK)));
    }

    @Test
    void testOutputCountDividesCost() {
        // 1 WHEAT (2.0) -> 4 of something: each is 0.5
        ShapelessRecipe recipe = mock(ShapelessRecipe.class);
        when(recipe.getResult()).thenReturn(new ItemStack(Material.PAPER, 4));
        when(recipe.getIngredientList()).thenReturn(List.of(new ItemStack(Material.WHEAT, 1)));
        recipes.put(new ItemStack(Material.PAPER), List.of(recipe));
        assertEquals(5.0, engine.getPrice(new ItemStack(Material.PAPER)));
    }

    @Test
    void testSmeltingAddsFuelShare() {
        // COOKED_MUTTON from MUTTON (2.0 configured) + COAL(4.0)/8 = 2.5
        FurnaceRecipe recipe = mock(FurnaceRecipe.class);
        when(recipe.getResult()).thenReturn(new ItemStack(Material.COOKED_MUTTON, 1));
        when(recipe.getInput()).thenReturn(new ItemStack(Material.MUTTON));
        recipes.put(new ItemStack(Material.COOKED_MUTTON), List.of(recipe));
        assertEquals(20.0 + 40.0 / 8.0, engine.getPrice(new ItemStack(Material.COOKED_MUTTON)));
    }

    @Test
    void testRecursiveDerivation() {
        // BONE_BLOCK <- 9 BONE_MEAL <- (3 from) BONE... chain: A from B from configured WHEAT
        ShapelessRecipe inner = mock(ShapelessRecipe.class);
        when(inner.getResult()).thenReturn(new ItemStack(Material.PAPER, 1));
        when(inner.getIngredientList()).thenReturn(List.of(new ItemStack(Material.WHEAT, 2)));
        recipes.put(new ItemStack(Material.PAPER), List.of(inner));
        ShapelessRecipe outer = mock(ShapelessRecipe.class);
        when(outer.getResult()).thenReturn(new ItemStack(Material.BOOK, 1));
        when(outer.getIngredientList()).thenReturn(List.of(new ItemStack(Material.PAPER, 3)));
        recipes.put(new ItemStack(Material.BOOK), List.of(outer));
        // BOOK = 3 x PAPER = 3 x (2 x 2.0) = 12.0
        assertEquals(120.0, engine.getPrice(new ItemStack(Material.BOOK)));
    }

    @Test
    void testCircularRecipesAreSafe() {
        // A crafts from B, B crafts from A - must return unpriceable, not hang
        ShapelessRecipe a = mock(ShapelessRecipe.class);
        when(a.getResult()).thenReturn(new ItemStack(Material.PAPER, 1));
        when(a.getIngredientList()).thenReturn(List.of(new ItemStack(Material.BOOK, 1)));
        recipes.put(new ItemStack(Material.PAPER), List.of(a));
        ShapelessRecipe b = mock(ShapelessRecipe.class);
        when(b.getResult()).thenReturn(new ItemStack(Material.BOOK, 1));
        when(b.getIngredientList()).thenReturn(List.of(new ItemStack(Material.PAPER, 1)));
        recipes.put(new ItemStack(Material.BOOK), List.of(b));
        assertTrue(engine.getPrice(new ItemStack(Material.BOOK)) < 0);
    }

    @Test
    void testPrettify() {
        assertEquals("Oak Log", PriceEngine.prettify("OAK_LOG"));
        assertEquals("Wheat", PriceEngine.prettify("WHEAT"));
    }

    private void addonSettingsWithout(String material) {
        Settings settings = new Settings();
        settings.getBasePrices().remove(material);
        when(addon.getSettings()).thenReturn(settings);
    }
}
