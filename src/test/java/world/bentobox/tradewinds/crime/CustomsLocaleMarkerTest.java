package world.bentobox.tradewinds.crime;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.InputStream;
import java.util.LinkedHashMap;
import java.util.Map;

import org.junit.jupiter.api.Test;
import org.yaml.snakeyaml.Yaml;

/**
 * Guards the locale's BentoBox routing markers.
 * <p>
 * Playtest: a customs alert rendered as "CUSTOMS" followed by a literal
 * line-feed glyph. BentoBox splits a [title]/[subtitle] string on the raw
 * marker text and hands each half to MiniMessage separately - so a
 * {@code <newline>} inside the title half is parsed into a real newline
 * character in the title component, which a title cannot render. Titles are
 * one line by definition; the subtitle is the second line.
 *
 * @author tastybento
 */
class CustomsLocaleMarkerTest {

    @SuppressWarnings("unchecked")
    private Map<String, Object> locale() {
        try (InputStream in = getClass().getClassLoader().getResourceAsStream("locales/en-US.yml")) {
            return new Yaml().loadAs(in, LinkedHashMap.class);
        } catch (Exception e) {
            throw new IllegalStateException("Could not read en-US.yml", e);
        }
    }

    /**
     * Walk every leaf string in the locale.
     */
    @SuppressWarnings("unchecked")
    private void walk(Map<String, Object> node, String path, java.util.function.BiConsumer<String, String> leaf) {
        node.forEach((key, value) -> {
            String here = path.isEmpty() ? String.valueOf(key) : path + "." + key;
            if (value instanceof Map<?, ?> child) {
                walk((Map<String, Object>) child, here, leaf);
            } else if (value != null) {
                leaf.accept(here, String.valueOf(value));
            }
        });
    }

    @Test
    void testNoNewlinesInsideTitles() {
        walk(locale(), "", (path, text) -> {
            if (!text.toLowerCase(java.util.Locale.ENGLISH).startsWith("[title]")) {
                return;
            }
            String titleHalf = text.split("(?i)\\[subtitle]", 2)[0];
            assertFalse(titleHalf.contains("<newline>") || titleHalf.contains("\\n"),
                    path + " puts a newline inside a title - it renders as a line-feed glyph. "
                            + "Use [subtitle] for the second line.");
        });
    }

    @Test
    void testRoutingMarkersAreAtTheStart() {
        // BentoBox anchors [title] and [actionbar] markers to the start of the string.
        // Any marker buried mid-message is simply printed to chat as text
        walk(locale(), "", (path, text) -> {
            String lower = text.toLowerCase(java.util.Locale.ENGLISH);
            if (lower.contains("[title]")) {
                assertTrue(lower.startsWith("[title]"), path + " has [title] but not at the start");
            }
            if (lower.contains("[actionbar]")) {
                assertTrue(lower.startsWith("[actionbar]"), path + " has [actionbar] but not at the start");
            }
            // A subtitle with no title is silently dropped
            if (lower.contains("[subtitle]")) {
                assertTrue(lower.startsWith("[title]"), path + " has [subtitle] without a [title]");
            }
        });
    }
}
