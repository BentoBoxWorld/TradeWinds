package world.bentobox.tradewinds.economy;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.util.List;
import java.util.Map;
import java.util.function.Function;

import org.bukkit.Material;
import org.bukkit.enchantments.Enchantment;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.Recipe;
import org.bukkit.inventory.ShapelessRecipe;
import org.bukkit.inventory.meta.Damageable;
import org.bukkit.inventory.meta.ItemMeta;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import world.bentobox.tradewinds.CommonTestSetup;
import world.bentobox.tradewinds.Settings;
import world.bentobox.tradewinds.TradeWinds;

/**
 * NBT is part of the price (Stage 7.5 Phase 4): a worn bow is worth less than a
 * mint one and a Silk Touch pick more than a plain one.
 * <p>
 * ItemStacks are MOCKED here rather than constructed. Under the patched
 * MockBukkit a real {@code new ItemStack(...)} gets a phantom UNSPECIFIC meta and
 * no working item factory, so damage and enchantments cannot be set on one - the
 * only way to exercise the meta paths is to mock the stack and its meta.
 *
 * @author tastybento
 */
class PriceEngineNbtTest extends CommonTestSetup {

    private static final double SWORD_BASE = 2 * 90.0;

    private TradeWinds addon;
    private Settings settings;
    private PriceEngine engine;

    @Override
    @BeforeEach
    public void setUp() throws Exception {
        super.setUp();
        addon = mock(TradeWinds.class);
        settings = new Settings();
        when(addon.getSettings()).thenReturn(settings);
        // An iron sword is 2 iron ingots (90 each) and a stick
        ShapelessRecipe recipe = mock(ShapelessRecipe.class);
        when(recipe.getResult()).thenReturn(new ItemStack(Material.IRON_SWORD, 1));
        when(recipe.getIngredientList()).thenReturn(List.of(new ItemStack(Material.IRON_INGOT, 2)));
        Function<ItemStack, List<Recipe>> source = item -> item.getType() == Material.IRON_SWORD
                ? List.of(recipe) : List.of();
        engine = new PriceEngine(addon, source);
    }

    /** A mocked stack of a real material carrying a mocked meta. */
    private ItemStack stackWith(Material material, ItemMeta meta, Map<Enchantment, Integer> enchants) {
        ItemStack item = mock(ItemStack.class);
        when(item.getType()).thenReturn(material);
        when(item.getItemMeta()).thenReturn(meta);
        when(item.getEnchantments()).thenReturn(enchants);
        return item;
    }

    @Test
    void testAMintSwordIsWorthItsParts() {
        ItemStack mint = stackWith(Material.IRON_SWORD, mock(ItemMeta.class), Map.of());
        assertEquals(SWORD_BASE, engine.getPrice(mint));
    }

    @Test
    void testAWornSwordIsWorthLess() {
        Damageable half = mock(Damageable.class, org.mockito.Mockito.withSettings()
                .extraInterfaces(ItemMeta.class));
        when(half.hasDamage()).thenReturn(true);
        // An iron sword has 250 durability; spend half of it
        when(half.getDamage()).thenReturn(125);
        ItemStack worn = stackWith(Material.IRON_SWORD, (ItemMeta) half, Map.of());
        double price = engine.getPrice(worn);
        assertTrue(price < SWORD_BASE, "A worn sword must be worth less: " + price);
        assertEquals(SWORD_BASE * (1 - 125.0 / 250.0), price, 0.01);
    }

    @Test
    void testAnAlmostBrokenSwordIsNearlyWorthless() {
        Damageable spent = mock(Damageable.class, org.mockito.Mockito.withSettings()
                .extraInterfaces(ItemMeta.class));
        when(spent.hasDamage()).thenReturn(true);
        when(spent.getDamage()).thenReturn(249);
        ItemStack junk = stackWith(Material.IRON_SWORD, (ItemMeta) spent, Map.of());
        assertTrue(engine.getPrice(junk) < SWORD_BASE * 0.01,
                "One hit from breaking should be worth nearly nothing");
    }

    @Test
    void testEnchantmentsAddAPremium() {
        ItemStack plain = stackWith(Material.IRON_SWORD, mock(ItemMeta.class), Map.of());
        ItemStack sharp = stackWith(Material.IRON_SWORD, mock(ItemMeta.class),
                Map.of(Enchantment.SILK_TOUCH, 1));
        double premium = engine.getPrice(sharp);
        assertTrue(premium > engine.getPrice(plain), "Silk Touch must be worth something: " + premium);
        // Weight 10 x level 1 x factor 0.05 = +50%
        assertEquals(SWORD_BASE * 1.5, premium, 0.01);
    }

    @Test
    void testHigherLevelsAreWorthMore() {
        ItemStack one = stackWith(Material.IRON_SWORD, mock(ItemMeta.class),
                Map.of(Enchantment.SHARPNESS, 1));
        ItemStack five = stackWith(Material.IRON_SWORD, mock(ItemMeta.class),
                Map.of(Enchantment.SHARPNESS, 5));
        assertTrue(engine.getPrice(five) > engine.getPrice(one));
    }

    @Test
    void testTheEnchantmentPremiumCanBeSwitchedOff() {
        ItemStack sharp = stackWith(Material.IRON_SWORD, mock(ItemMeta.class),
                Map.of(Enchantment.SILK_TOUCH, 1));
        settings.setEnchantmentPriceFactor(0);
        assertEquals(SWORD_BASE, engine.getPrice(sharp));
    }

    @Test
    void testDamageAndEnchantmentsCompound() {
        Damageable half = mock(Damageable.class, org.mockito.Mockito.withSettings()
                .extraInterfaces(ItemMeta.class));
        when(half.hasDamage()).thenReturn(true);
        when(half.getDamage()).thenReturn(125);
        ItemStack wornButGood = stackWith(Material.IRON_SWORD, (ItemMeta) half,
                Map.of(Enchantment.SILK_TOUCH, 1));
        // Half the durability, half again the enchantment premium on top
        assertEquals(SWORD_BASE * 0.5 * 1.5, engine.getPrice(wornButGood), 0.01);
    }
}
