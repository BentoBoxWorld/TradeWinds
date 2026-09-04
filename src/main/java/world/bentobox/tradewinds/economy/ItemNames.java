package world.bentobox.tradewinds.economy;

import java.util.Locale;

import org.bukkit.Material;
import org.bukkit.enchantments.Enchantment;

import net.kyori.adventure.text.minimessage.MiniMessage;
import world.bentobox.bentobox.api.user.User;

/**
 * How a vanilla item is named to a player (GitHub #7).
 * <p>
 * A material's name used to be its enum prettified - "Oak Log" - which no
 * locale could touch. Now the name is, in order:
 * <ol>
 * <li>the locale's own line, {@code tradewinds.materials.<key>}, when the
 * translator wrote one;</li>
 * <li>otherwise a MiniMessage {@code <lang_or:key:fallback>} tag carrying the
 * item's client translation key, with the English name as fallback.</li>
 * </ol>
 * The second is the interesting one. BentoBox substitutes variables into a
 * locale string by plain replacement and then parses the whole thing as
 * MiniMessage, so wherever the string becomes a Component - dialogs, GUI item
 * names and lore, holograms, boss bars - the CLIENT renders the item name in
 * its own language, for every language Mojang ships, with no translator
 * effort. Plain chat is flattened to legacy text by BentoBox, and its
 * flattener uses the tag's fallback, so chat degrades to the English name (or
 * the locale's override) rather than to a raw key.
 *
 * @author tastybento
 */
public final class ItemNames {

    private static final String MATERIAL_PREFIX = "tradewinds.materials.";
    private static final String UNKNOWN_KEY = "tradewinds.general.unknown";

    private ItemNames() {
        // Static use only
    }

    /**
     * The name of a material for this player, ready to substitute into a
     * locale string as {@code [material]}.
     *
     * @param user the viewer - decides which locale's override applies
     * @param material the material; null renders as the locale's unknown mark
     * @return the locale's line for it, else a client-translated tag with an
     *         English fallback
     */
    public static String label(User user, Material material) {
        if (material == null) {
            return user.getTranslation(UNKNOWN_KEY);
        }
        String override = user.getTranslationOrNothing(MATERIAL_PREFIX + material.name().toLowerCase(Locale.ENGLISH));
        if (!override.isBlank()) {
            return override;
        }
        return tag(translationKey(material), PriceEngine.prettify(material.name()));
    }

    /**
     * An enchantment with its level, client-translated: "Sharpness III" in
     * whatever language the client speaks. Vanilla has level names for I to X;
     * beyond that the number itself is the fallback.
     */
    public static String label(Enchantment enchantment, int level) {
        String name = tag(translationKey(enchantment), PriceEngine.prettify(enchantment.getKey().getKey()));
        return name + " " + tag("enchantment.level." + level, String.valueOf(level));
    }

    /**
     * Player-written text (an item's given name) about to be substituted into
     * a locale string. Substitution is by plain replacement and the result is
     * parsed as MiniMessage, so a sword named {@code <red>} would otherwise
     * inject formatting into the dialog. This is escaping, not rendering - the
     * User API still does all the rendering.
     */
    public static String escape(String playerText) {
        return MiniMessage.miniMessage().escapeTags(playerText);
    }

    /**
     * The MiniMessage tag that asks the client to translate a key, with the
     * fallback the server prints where a Component cannot reach.
     */
    static String tag(String key, String fallback) {
        return "<lang_or:'" + quote(key) + "':'" + quote(fallback) + "'>";
    }

    /**
     * Escape a value for a single-quoted MiniMessage tag argument.
     */
    private static String quote(String text) {
        return text.replace("\\", "\\\\").replace("'", "\\'");
    }

    /**
     * The client translation key for a material. Paper resolves it through the
     * item registry, which the headless test harness does not have; there we
     * derive the obvious key from the namespaced key instead. Both give
     * {@code block.minecraft.oak_log} for a log; only a real server gets the
     * special cases (wheat the item versus wheat the crop) right, which is
     * exactly where it matters.
     */
    static String translationKey(Material material) {
        try {
            String key = material.translationKey();
            if (key != null && !key.isBlank()) {
                return key;
            }
        } catch (RuntimeException e) {
            // Registry not available (tests) - derive below
        }
        return (material.isBlock() ? "block." : "item.") + material.getKey().getNamespace() + "."
                + material.getKey().getKey();
    }

    private static String translationKey(Enchantment enchantment) {
        try {
            String key = enchantment.translationKey();
            if (key != null && !key.isBlank()) {
                return key;
            }
        } catch (RuntimeException e) {
            // Registry not available (tests) - derive below
        }
        return "enchantment." + enchantment.getKey().getNamespace() + "." + enchantment.getKey().getKey();
    }
}
