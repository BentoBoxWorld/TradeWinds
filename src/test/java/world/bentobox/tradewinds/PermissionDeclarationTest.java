package world.bentobox.tradewinds;

import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Set;
import java.util.TreeSet;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Stream;

import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;
import org.junit.jupiter.api.Test;

/**
 * Every permission a command claims must be declared in {@code addon.yml}, or
 * ordinary players silently lack it: an undeclared permission has no default,
 * so permission plugins deny it and the player is told they may not run the
 * command - which is how {@code /tw prices} shipped unusable (2026-08-03).
 * <p>
 * Third member of the artifact-drift family, after the config maps that
 * BentoBox replaces rather than merges and the locale keys that render as
 * themselves when missing. In each case two artifacts must agree, disagreement
 * breaks the game and not the build, and only a test closes the gap.
 *
 * @author tastybento
 */
class PermissionDeclarationTest {

    /** setPermission("island.x") / setPermission("admin.x") in command setup. */
    private static final Pattern SET_PERMISSION = Pattern.compile("setPermission\\(\"([a-z.-]+)\"\\)");

    @Test
    void testEveryCommandPermissionIsDeclared() throws IOException {
        YamlConfiguration addonYml = YamlConfiguration.loadConfiguration(new InputStreamReader(
                getClass().getClassLoader().getResourceAsStream("addon.yml")));
        ConfigurationSection declared = addonYml.getConfigurationSection("permissions");
        assertNotNull(declared, "addon.yml has no permissions section");

        Set<String> used = new TreeSet<>();
        try (Stream<Path> sources = Files.walk(Path.of("src/main/java"))) {
            for (Path file : sources.filter(p -> p.toString().endsWith(".java")).toList()) {
                Matcher matcher = SET_PERMISSION.matcher(Files.readString(file));
                while (matcher.find()) {
                    // BentoBox prefixes the addon's permission prefix
                    used.add("tradewinds." + matcher.group(1));
                }
            }
        }
        assertTrue(used.size() >= 10, "Found only " + used.size() + " permissions - has the scan broken?");

        List<String> missing = used.stream()
                .filter(permission -> !declared.isConfigurationSection(permission)).toList();
        assertTrue(missing.isEmpty(), "Command permissions not declared in addon.yml (players will be "
                + "denied them):\n  " + String.join("\n  ", missing));
    }

    @Test
    void testEveryDeclaredPermissionHasADefault() {
        // A declaration without a default is the same silent denial by a longer
        // road, and a missing description is an admin guessing.
        // NB: permission names contain dots, which YamlConfiguration reads as
        // NESTING - "tradewinds.island.chart" is a section three levels deep.
        // A node is a permission declaration iff it carries default or
        // description; anything else is just a path segment on the way down.
        YamlConfiguration addonYml = YamlConfiguration.loadConfiguration(new InputStreamReader(
                getClass().getClassLoader().getResourceAsStream("addon.yml")));
        ConfigurationSection declared = addonYml.getConfigurationSection("permissions");
        List<String> broken = declared.getKeys(true).stream()
                .filter(declared::isConfigurationSection)
                .filter(key -> declared.contains(key + ".default") || declared.contains(key + ".description"))
                .filter(key -> !declared.contains(key + ".default") || !declared.contains(key + ".description"))
                .toList();
        assertTrue(broken.isEmpty(),
                "Permissions missing a default or description:\n  " + String.join("\n  ", broken));
    }
}
