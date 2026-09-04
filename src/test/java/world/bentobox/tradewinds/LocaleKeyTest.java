package world.bentobox.tradewinds;

import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.TreeSet;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Stream;

import org.bukkit.configuration.file.YamlConfiguration;
import org.junit.jupiter.api.Test;

import world.bentobox.tradewinds.economy.TradeCategory;
import world.bentobox.tradewinds.ocean.IslandType;
import world.bentobox.tradewinds.ocean.NameGenerator;
import world.bentobox.tradewinds.ocean.SecurityBand;

/**
 * Every locale key the code asks for must exist in {@code en-US.yml}.
 * <p>
 * A missing key does not throw - BentoBox renders the key itself, so the game
 * shows a player "tradewinds.ui.market.item-enchanted" where a word should be.
 * That has now happened three times: keys named after YAML 1.1 booleans, a
 * duplicate key silently dropped, and a key written without the {@code ui.}
 * prefix its helper adds. None of them failed a build.
 * <p>
 * Only whole string literals are checked. Keys assembled by concatenation cannot
 * be resolved statically and are skipped - see {@code DYNAMIC}.
 *
 * @author tastybento
 */
class LocaleKeyTest {

    /** Literal "tradewinds.x.y" keys anywhere in the source. */
    private static final Pattern DIRECT = Pattern.compile("\"(tradewinds\\.[a-z0-9-]+(?:\\.[a-z0-9-]+)+)\"");

    /** ui("x.y") and uiText("x.y") calls, which prepend "tradewinds.ui.". */
    private static final Pattern UI_CALL = Pattern.compile(
            "\\bui(?:Text)?\\(player,\\s*\"([a-z0-9.-]+)\"\\s*[,)]");

    /**
     * Keys assembled at runtime ({@code "market." + page + "-title"}), which no
     * static scan can see. Asserted explicitly by
     * {@link #testKeysBuiltAtRuntimeExist} instead of being skipped in silence.
     */
    private static final Set<String> RUNTIME_BUILT = Set.of("tradewinds.ui.market.buying-title",
            "tradewinds.ui.market.buying-body", "tradewinds.ui.market.selling-title",
            "tradewinds.ui.market.selling-body");

    /** Comments - a key quoted in javadoc is documentation, not a lookup. */
    private static final Pattern COMMENTS = Pattern.compile("/\\*.*?\\*/|//[^\n]*", Pattern.DOTALL);

    /** Config paths share the tradewinds. prefix but are not locale keys at all. */
    private static final Pattern CONFIG_PATHS = Pattern.compile("@ConfigEntry\\([^)]*\\)", Pattern.DOTALL);

    @Test
    void testEveryLocaleKeyUsedInCodeExists() throws IOException {
        YamlConfiguration locale = YamlConfiguration.loadConfiguration(new InputStreamReader(
                getClass().getClassLoader().getResourceAsStream("locales/en-US.yml")));
        Set<String> used = new TreeSet<>();
        try (Stream<Path> sources = Files.walk(Path.of("src/main/java"))) {
            for (Path file : sources.filter(p -> p.toString().endsWith(".java")).toList()) {
                // Strip what only LOOKS like a lookup: javadoc quoting a key, and
                // @ConfigEntry paths, which share the prefix but address config
                String code = CONFIG_PATHS.matcher(COMMENTS.matcher(Files.readString(file))
                        .replaceAll(" ")).replaceAll(" ");
                collect(DIRECT.matcher(code), used, "");
                collect(UI_CALL.matcher(code), used, "tradewinds.ui.");
            }
        }
        assertTrue(used.size() > 50, "Found only " + used.size() + " locale keys - has the scan broken?");

        List<String> missing = new ArrayList<>();
        for (String key : used) {
            // A key must resolve to a leaf string, not a section: BentoBox cannot
            // render a section, and a key that became a parent is a silent break
            if (!(locale.get(key) instanceof String)) {
                missing.add("  " + key + (locale.contains(key) ? " (a section, not a message)" : ""));
            }
        }
        assertTrue(missing.isEmpty(),
                "Locale keys used in code but not in en-US.yml:\n" + String.join("\n", missing));
    }

    @Test
    void testKeysBuiltAtRuntimeExist() {
        YamlConfiguration locale = YamlConfiguration.loadConfiguration(new InputStreamReader(
                getClass().getClassLoader().getResourceAsStream("locales/en-US.yml")));
        List<String> missing = RUNTIME_BUILT.stream().filter(key -> !(locale.get(key) instanceof String))
                .sorted().toList();
        assertTrue(missing.isEmpty(), "Runtime-built locale keys missing: " + missing);
    }

    /**
     * Keys built from an enum name at runtime - {@code getLocaleKey()} on the
     * band, island type and trade category. The source scan cannot see them,
     * so every constant is checked here; a new enum value without a locale
     * line would otherwise render as its key.
     */
    @Test
    void testEnumLocaleKeysExist() {
        YamlConfiguration locale = YamlConfiguration.loadConfiguration(new InputStreamReader(
                getClass().getClassLoader().getResourceAsStream("locales/en-US.yml")));
        List<String> keys = new ArrayList<>();
        for (SecurityBand band : SecurityBand.values()) {
            keys.add(band.getLocaleKey());
        }
        for (IslandType type : IslandType.values()) {
            keys.add(type.getLocaleKey());
        }
        for (TradeCategory category : TradeCategory.values()) {
            keys.add(category.getLocaleKey());
        }
        List<String> missing = keys.stream().filter(key -> !(locale.get(key) instanceof String)).sorted()
                .toList();
        assertTrue(missing.isEmpty(), "Enum locale keys missing from en-US.yml: " + missing);
    }

    /**
     * Every good, fuel and hull the ocean trades has a materials line, so a
     * translator who copies en-US sees the whole list. A blank line is the
     * normal state (the client translates), so only presence is checked.
     */
    @Test
    void testEveryTradedMaterialHasALocaleLine() {
        YamlConfiguration locale = YamlConfiguration.loadConfiguration(new InputStreamReader(
                getClass().getClassLoader().getResourceAsStream("locales/en-US.yml")));
        Settings settings = new Settings();
        Set<String> materials = new TreeSet<>();
        materials.addAll(settings.getBasePrices().keySet());
        materials.addAll(settings.getFuelValues().keySet());
        materials.addAll(settings.getBoatRanks().keySet());
        List<String> missing = materials.stream()
                .map(name -> "tradewinds.materials." + name.toLowerCase(java.util.Locale.ENGLISH))
                .filter(key -> !(locale.get(key) instanceof String)).sorted().toList();
        assertTrue(missing.isEmpty(), "Traded materials without a locale line: " + missing);
    }

    /**
     * Every syllable the name generator can produce has a token line, so a
     * translator sees the full table to transliterate.
     */
    @Test
    void testEveryNameTokenHasALocaleLine() {
        YamlConfiguration locale = YamlConfiguration.loadConfiguration(new InputStreamReader(
                getClass().getClassLoader().getResourceAsStream("locales/en-US.yml")));
        List<String> missing = NameGenerator.vocabulary().stream()
                .map(token -> "tradewinds.name-token." + token.toLowerCase(java.util.Locale.ENGLISH))
                .filter(key -> !(locale.get(key) instanceof String)).sorted().toList();
        assertTrue(missing.isEmpty(), "Name tokens without a locale line: " + missing);
    }

    private void collect(Matcher matcher, Set<String> into, String prefix) {
        while (matcher.find()) {
            into.add(prefix + matcher.group(1));
        }
    }
}
