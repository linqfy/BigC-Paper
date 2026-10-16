package dev.linqfy.bigCasares.modules.dimension;

import java.util.Optional;

/**
 * Remembers which datapack hash was on disk when this JVM first looked, i.e.
 * the content the server actually loaded at startup. Static so that it survives
 * {@code /bc reload}, which recreates modules but not the running registries.
 */
final class DatapackStartupLedger {

    private static final Object LOCK = new Object();
    private static boolean observed;
    private static String startupHash;

    private DatapackStartupLedger() {
    }

    /** Records the on-disk hash the first time it is called in this JVM. */
    static void observe(String hashOnDisk) {
        synchronized (LOCK) {
            if (!observed) {
                observed = true;
                startupHash = hashOnDisk;
            }
        }
    }

    static Optional<String> startupHash() {
        synchronized (LOCK) {
            return Optional.ofNullable(startupHash);
        }
    }
}
