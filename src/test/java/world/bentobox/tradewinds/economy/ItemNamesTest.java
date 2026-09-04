package world.bentobox.tradewinds.economy;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.enchantments.Enchantment;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.serializer.gson.GsonComponentSerializer;
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer;
import world.bentobox.bentobox.api.user.User;
import world.bentobox.bentobox.util.Util;
import world.bentobox.tradewinds.CommonTestSetup;

/**
 * Item names for #7: the locale's own line wins, else the client translates
 * through a MiniMessage tag whose fallback is the English name.
 */
class ItemNamesTest extends CommonTestSetup {

    private User user;

    @BeforeEach
    void setUpUser() {
        user = User.getInstance(mockPlayer);
    }

    @Test
    void testNoOverrideAsksTheClientToTranslate() {
        String label = ItemNames.label(user, Material.OAK_LOG);
        assertTrue(label.startsWith("<lang_or:'"), label);
        assertTrue(label.contains(".oak_log':'Oak Log'>"), label);
    }

    @Test
    void testLocaleLineOverridesTheClient() {
        when(lm.get(any(), eq("tradewinds.materials.oak_log"))).thenReturn("橡木原木");
        assertEquals("橡木原木", ItemNames.label(user, Material.OAK_LOG));
        // A blank line is "no override", not an empty name
        when(lm.get(any(), eq("tradewinds.materials.wheat"))).thenReturn("");
        assertTrue(ItemNames.label(user, Material.WHEAT).startsWith("<lang_or:'"));
    }

    @Test
    void testNullMaterialIsTheUnknownMark() {
        // The test locale renders every key as itself
        assertEquals("tradewinds.general.unknown", ItemNames.label(user, null));
    }

    @Test
    void testFallbackQuotesAreEscaped() {
        String label = ItemNames.label(user, Material.JACK_O_LANTERN);
        assertTrue(label.contains("Jack O Lantern") || label.contains("\\'"), label);
        assertEquals("<lang_or:'k':'it\\'s'>", ItemNames.tag("k", "it's"));
    }

    @Test
    void testEnchantmentCarriesNameAndLevel() {
        Enchantment sharpness = mock(Enchantment.class);
        when(sharpness.getKey()).thenReturn(NamespacedKey.minecraft("sharpness"));
        when(sharpness.translationKey()).thenReturn("enchantment.minecraft.sharpness");
        assertEquals("<lang_or:'enchantment.minecraft.sharpness':'Sharpness'> <lang_or:'enchantment.level.3':'3'>",
                ItemNames.label(sharpness, 3));
    }

    @Test
    void testPlayerTextCannotInjectTags() {
        String escaped = ItemNames.escape("<red>Sword of <bold>Doom");
        // Every tag opener is escaped, so MiniMessage renders it as literal text
        assertEquals("\\<red>Sword of \\<bold>Doom", escaped);
        assertFalse(escaped.matches(".*(?<!\\\\)<.*"), escaped);
    }

    /**
     * The whole design rests on two BentoBox behaviours: variables are
     * substituted and THEN the string is parsed as MiniMessage, and the
     * legacy flattener chat goes through prints a translatable's fallback.
     * Pin both to the real BentoBox code so a core change fails here.
     */
    @Test
    void testBentoBoxRendersTheTagBothWays() {
        String label = ItemNames.label(user, Material.OAK_LOG);
        // Component path (dialogs, GUIs): a translatable the client resolves
        Component component = Util.parseMiniMessage("<white>[material]</white>".replace("[material]", label));
        String plain = PlainTextComponentSerializer.plainText()
                .serialize(component);
        assertEquals("Oak Log", plain, "plain-text flattening should use the fallback");
        assertTrue(GsonComponentSerializer.gson().serialize(component)
                .contains("\"translate\""), "the client should receive a translatable component");
        // Chat path: BentoBox flattens to legacy text using the fallback
        assertEquals("§fOak Log", Util.componentToLegacy(component));
    }

    @Test
    void testDerivedKeyUsesBlockOrItemPrefix() {
        // Whatever Paper answers, the key must be a translation key for that material
        assertTrue(ItemNames.translationKey(Material.OAK_LOG).endsWith(".minecraft.oak_log"));
        assertTrue(ItemNames.translationKey(Material.DIAMOND).endsWith(".minecraft.diamond"));
    }
}
