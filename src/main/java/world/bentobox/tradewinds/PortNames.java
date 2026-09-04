package world.bentobox.tradewinds;

import java.util.List;
import java.util.Locale;

import world.bentobox.bentobox.api.user.User;
import world.bentobox.tradewinds.ocean.IslandSpec;
import world.bentobox.tradewinds.ocean.NameGenerator;
import world.bentobox.tradewinds.ocean.OceanEngine;

/**
 * How a port is named to a player (GitHub #7).
 * <p>
 * Port names are procedural, so no locale can list them - but every name is a
 * short sequence of syllable tokens, and a locale CAN list those. Each token
 * is looked up as {@code tradewinds.name-token.<token>}; a Chinese locale maps
 * "la" to one character and "ve" to another, and "Lave" comes out in Chinese
 * for every Chinese-speaking sailor, the same for all of them. A blank or
 * missing line leaves that token as it is, so en-US reads unchanged.
 * <p>
 * {@link IslandSpec#name()} stays the canonical English name and the island's
 * identity everywhere that is not a player's screen: island registry, resident
 * tags, logs, admin commands that take a name as an argument. This class is
 * display only.
 *
 * @author tastybento
 */
public final class PortNames {

    private static final String TOKEN_PREFIX = "tradewinds.name-token.";
    private static final String SPAWN_KEY = "tradewinds.spawn.name";

    private PortNames() {
        // Static use only
    }

    /**
     * The name of a port as this player should read it.
     *
     * @param addon the addon, for the ocean the port belongs to
     * @param user the viewer - decides which locale's tokens apply
     * @param spec the port
     * @return the transliterated name, or the canonical name where the ocean
     *         engine is not up or the spec is not one of its islands
     */
    public static String display(TradeWinds addon, User user, IslandSpec spec) {
        if (OceanEngine.SPAWN_NAME.equals(spec.name())) {
            // The one island with a given name rather than a generated one
            return user.getTranslation(SPAWN_KEY);
        }
        OceanEngine engine = addon.getOceanEngine();
        if (engine == null) {
            return spec.name();
        }
        List<String> tokens = engine.nameTokens(spec);
        if (!NameGenerator.join(tokens).equals(spec.name())) {
            // Not this ocean's island (a renamed or hand-built spec): no tokens to map
            return spec.name();
        }
        StringBuilder sb = new StringBuilder();
        for (String token : tokens) {
            String syllable = token.toLowerCase(Locale.ENGLISH);
            String mapped = user.getTranslationOrNothing(TOKEN_PREFIX + syllable);
            sb.append(mapped.isBlank() ? syllable : mapped);
        }
        // Capitalize the way the canonical name is; a no-op for scripts without case
        return Character.toUpperCase(sb.charAt(0)) + sb.substring(1);
    }
}
