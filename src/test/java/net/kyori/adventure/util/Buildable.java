package net.kyori.adventure.util;

/**
 * ABI STUB - test classpath only.
 * <p>
 * Adventure 4.x had this interface; Adventure 5 (shipped by Paper 26.2)
 * removed it. MockBukkit 26.1.2 classes (BookMetaMock, BlockMock) still
 * reference it in their signatures, so loading them under Adventure 5 throws
 * {@link NoClassDefFoundError}. This empty stand-in lets those classes link;
 * only a code path that actually *invokes* a removed Adventure 4 method would
 * still fail, and none of Gusher's tests do.
 * <p>
 * Delete together with the other MockBukkit 26.2 shims (see
 * scripts/build_patched_mockbukkit.py) when MockBukkit ships a 26.2 build.
 *
 * @param <R> the type built by the builder
 * @param <B> the builder type
 */
public interface Buildable<R, B extends Buildable.Builder<R>> {

    /**
     * @return a builder based on this instance
     */
    B toBuilder();

    /**
     * The builder.
     *
     * @param <R> the built type
     */
    interface Builder<R> {
        /**
         * @return the built instance
         */
        R build();
    }
}
