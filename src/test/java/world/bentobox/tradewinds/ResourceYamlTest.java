package world.bentobox.tradewinds;

import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.junit.jupiter.api.Test;

/**
 * The shipped YAML must not carry duplicate keys. Bukkit's loader only WARNS
 * ("duplicate keys found : description") and then silently keeps one of them,
 * so a mis-indented block can quietly delete a message or a command
 * description - twice now, from bulk locale edits. This catches it at build
 * time instead of in the console.
 *
 * @author tastybento
 */
class ResourceYamlTest {

    /** Keys are "word chars, dashes, dots or a star", then a colon. */
    private static final Pattern KEY = Pattern.compile("^(\\s*)([A-Za-z0-9_.*\\-]+):(.*)$");

    private List<String> duplicatesIn(Path file) throws IOException {
        List<String> duplicates = new ArrayList<>();
        Map<String, Integer> seen = new HashMap<>();
        // (indent, key) of the enclosing mappings
        Deque<int[]> indents = new ArrayDeque<>();
        Deque<String> names = new ArrayDeque<>();
        List<String> lines = Files.readAllLines(file);
        for (int i = 0; i < lines.size(); i++) {
            String line = lines.get(i);
            if (line.isBlank() || line.stripLeading().startsWith("#") || line.stripLeading().startsWith("-")) {
                continue;
            }
            Matcher m = KEY.matcher(line);
            if (!m.matches()) {
                continue;
            }
            int indent = m.group(1).length();
            String key = m.group(2);
            while (!indents.isEmpty() && indents.peek()[0] >= indent) {
                indents.pop();
                names.pop();
            }
            List<String> parts = new ArrayList<>(names.stream().toList());
            java.util.Collections.reverse(parts);
            parts.add(key);
            String full = String.join(".", parts);
            Integer first = seen.put(full, i + 1);
            if (first != null) {
                duplicates.add(full + " (lines " + first + " and " + (i + 1) + ")");
            }
            indents.push(new int[] { indent });
            names.push(key);
        }
        return duplicates;
    }

    @Test
    void testShippedYamlHasNoDuplicateKeys() throws IOException {
        List<Path> files = new ArrayList<>(List.of(Path.of("src/main/resources/addon.yml"),
                Path.of("src/main/resources/config.yml")));
        Path locales = Path.of("src/main/resources/locales");
        if (Files.isDirectory(locales)) {
            try (var stream = Files.list(locales)) {
                stream.filter(p -> p.toString().endsWith(".yml")).forEach(files::add);
            }
        }
        List<String> problems = new ArrayList<>();
        for (Path file : files) {
            if (Files.exists(file)) {
                duplicatesIn(file).forEach(d -> problems.add(file.getFileName() + ": " + d));
            }
        }
        assertTrue(problems.isEmpty(), "Duplicate YAML keys (Bukkit only warns, then drops one): " + problems);
    }
}
