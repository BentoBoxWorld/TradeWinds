package world.bentobox.tradewinds.travel;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;

import java.util.List;

import org.junit.jupiter.api.Test;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer;
import world.bentobox.bentobox.api.user.User;
import world.bentobox.tradewinds.CommonTestSetup;

/**
 * Hold tooltips are locale strings broken on newlines into lore lines: a
 * tooltip never wraps, so the six-clause border instruction was one clipped
 * line (playtest 2026-09-04).
 */
class HoldLoreTest extends CommonTestSetup {

    @Test
    void testLoreBreaksOnNewlines() {
        when(lm.get(any(), eq("tradewinds.hold.border-lore")))
                .thenReturn("<gray>Pick up a stack:</gray>\n<gray>drop it here.</gray>\n<red>Or not.</red>");
        List<Component> lines = HoldGui.loreLines(User.getInstance(mockPlayer), "tradewinds.hold.border-lore");
        assertEquals(3, lines.size());
        assertEquals("Pick up a stack:", PlainTextComponentSerializer.plainText().serialize(lines.get(0)));
        assertEquals("Or not.", PlainTextComponentSerializer.plainText().serialize(lines.get(2)));
        assertEquals(NamedTextColor.RED, lines.get(2).color());
    }

    @Test
    void testSingleLineLoreIsOneLine() {
        when(lm.get(any(), eq("tradewinds.hold.locked-lore"))).thenReturn("<gray>A bigger boat unlocks this.</gray>");
        assertEquals(1, HoldGui.loreLines(User.getInstance(mockPlayer), "tradewinds.hold.locked-lore").size());
    }
}
