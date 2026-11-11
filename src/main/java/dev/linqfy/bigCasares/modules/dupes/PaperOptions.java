package dev.linqfy.bigCasares.modules.dupes;

import java.util.function.Function;

/** Read-only snapshot of Paper's documented native duplication options. */
record PaperOptions(boolean pistons, boolean hooks) {
    static final String PISTONS = "unsupported-settings.allow-piston-duplication";
    static final String HOOKS = "unsupported-settings.skip-tripwire-hook-placement-validation";

    static PaperOptions read(Function<String, Object> values) {
        return new PaperOptions(readBoolean(values, PISTONS), readBoolean(values, HOOKS));
    }

    private static boolean readBoolean(Function<String, Object> values, String key) {
        Object value = values.apply(key);
        if (value == null) return false;
        if (value instanceof Boolean enabled) return enabled;
        throw new IllegalArgumentException("La opción de Paper " + key + " no es un booleano.");
    }
}
