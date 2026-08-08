package world.bentobox.tradewinds;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.InputStreamReader;
import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;

import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import world.bentobox.bentobox.api.configuration.ConfigEntry;

/**
 * The shipped {@code config.yml} must agree with {@link Settings}' own defaults.
 * <p>
 * This is not tidiness. BentoBox <b>replaces</b> map settings from config rather
 * than merging them ({@code YamlDatabaseHandler.deserializeMap}), so whatever the
 * shipped config says WINS over the code default - and every unit test in this
 * suite reads the code default, so the two can disagree indefinitely with a green
 * build and a broken game. It has happened twice:
 * <ul>
 * <li>{@code economy.base-prices} shipped 27 of 112 entries, so ~50 goods could
 * not be sold at all on a real server.</li>
 * <li>{@code boats.ranks} shipped every value multiplied by ten - cargo SLOT
 * counts caught up in the whole-coin money migration - so an oak boat carried 30
 * slots instead of 3.</li>
 * </ul>
 * Both were invisible to the tests and obvious in play. This test closes that gap
 * for every map setting at once, present and future.
 *
 * @author tastybento
 */
class ConfigAgreementTest extends CommonTestSetup {

    private Settings settings;
    private YamlConfiguration config;

    @Override
    @BeforeEach
    public void setUp() throws Exception {
        super.setUp();
        settings = new Settings();
        config = YamlConfiguration.loadConfiguration(new InputStreamReader(
                getClass().getClassLoader().getResourceAsStream("config.yml")));
    }

    @Test
    void testEveryMapSettingMatchesItsCodeDefault() throws Exception {
        List<String> problems = new ArrayList<>();
        int checked = 0;
        for (Field field : Settings.class.getDeclaredFields()) {
            ConfigEntry entry = field.getAnnotation(ConfigEntry.class);
            if (entry == null || !Map.class.isAssignableFrom(field.getType())) {
                continue;
            }
            checked++;
            field.setAccessible(true);
            Map<?, ?> expected = (Map<?, ?>) field.get(settings);
            ConfigurationSection section = config.getConfigurationSection(entry.path());
            Map<String, Object> actual = new TreeMap<>();
            if (section != null) {
                section.getKeys(false).forEach(key -> actual.put(key, section.get(key)));
            }
            compare(entry.path(), expected, actual, problems);
        }
        assertTrue(checked > 0, "Found no map settings to check - has Settings changed shape?");
        assertTrue(problems.isEmpty(), "config.yml disagrees with the code defaults:\n"
                + String.join("\n", problems));
    }

    private void compare(String path, Map<?, ?> expected, Map<String, Object> actual, List<String> problems) {
        for (Map.Entry<?, ?> want : expected.entrySet()) {
            String key = String.valueOf(want.getKey());
            if (!actual.containsKey(key)) {
                problems.add("  " + path + ": missing '" + key + "' (code says " + want.getValue() + ")");
                continue;
            }
            if (!sameValue(want.getValue(), actual.get(key))) {
                problems.add("  " + path + "." + key + ": config says " + actual.get(key) + ", code says "
                        + want.getValue());
            }
        }
        for (String key : actual.keySet()) {
            if (!expected.containsKey(key)) {
                problems.add("  " + path + ": config has '" + key + "' which the code default does not");
            }
        }
    }

    /**
     * Numbers compare by value, not by boxed type: YAML types a number by how it
     * is written, so 20 and 20.0 are the same setting written two ways.
     */
    private boolean sameValue(Object expected, Object actual) {
        if (expected instanceof Number want && actual instanceof Number got) {
            return Math.abs(want.doubleValue() - got.doubleValue()) < 1e-9;
        }
        return String.valueOf(expected).equals(String.valueOf(actual));
    }

    @Test
    void testBoatRanksAreSlotsNotMoney() {
        // The specific shape of the ranks bug: slot counts are small integers, and
        // the ladder runs 2..21. Anything an order of magnitude out means a money
        // migration has been applied to a table that is not money.
        Map<String, Integer> ranks = settings.getBoatRanks();
        assertEquals(20, ranks.size(), "The ladder has 20 rungs");
        assertEquals(2, ranks.get("BAMBOO_RAFT"), "The smallest hull carries 2");
        assertEquals(3, ranks.get("OAK_BOAT"), "An oak boat carries 3, not 30");
        assertEquals(21, ranks.get("PALE_OAK_CHEST_BOAT"), "The top hull carries 21");
        ranks.forEach((material, slots) -> assertTrue(slots >= 1 && slots <= 21,
                material + " has " + slots + " slots, which is not on a 2-21 ladder"));
    }
}
