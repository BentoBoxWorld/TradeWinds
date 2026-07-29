package org.mockbukkit.mockbukkit.adventure;

import java.util.function.Consumer;

import org.jetbrains.annotations.NotNull;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.flattener.ComponentFlattener;
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer;

/**
 * CLASSPATH SHADOW of MockBukkit's PlainTextComponentSerializer provider.
 * <p>
 * MockBukkit v26.1.2 (no 26.2 build exists yet) was compiled against Adventure
 * 4.x; Paper API 26.2 ships Adventure 5.2.0, where
 * {@code ComponentFlattener.toBuilder()} changed signature, so the original
 * class dies with {@link NoSuchMethodError} during BentoBox's
 * {@code Util.<clinit>}. Because {@code target/test-classes} precedes the
 * MockBukkit jar on the test classpath, this Adventure-5-compatible copy is
 * loaded instead.
 * <p>
 * Delete together with src/test/resources/keyed and /registries when a
 * MockBukkit build for 26.2+ is released.
 */
public class PlainTextComponentProviderImpl implements PlainTextComponentSerializer.Provider {

    /**
     * A minimal serializer that flattens components to plain text. Built
     * directly (not via {@code PlainTextComponentSerializer.builder()}) to
     * avoid re-entrant initialization of the serializer's service holder.
     */
    private static final PlainTextComponentSerializer SIMPLE = new PlainTextComponentSerializer() {

        @Override
        public void serialize(@NotNull StringBuilder sb, @NotNull Component component) {
            ComponentFlattener.basic().flatten(component, sb::append);
        }

        @Override
        public @NotNull Builder toBuilder() {
            throw new UnsupportedOperationException("Shadow serializer does not support toBuilder()");
        }
    };

    @Override
    public @NotNull PlainTextComponentSerializer plainTextSimple() {
        return SIMPLE;
    }

    @Override
    public @NotNull Consumer<PlainTextComponentSerializer.Builder> plainText() {
        return builder -> {
            // No customization
        };
    }
}
