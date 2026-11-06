package dev.linqfy.bigCasares.modules.tideframe.engine.compat;

import java.util.UUID;
import java.util.Objects;
import java.util.function.Function;

/** Optional API detection. UUID structure never implies a player's client edition. */
public final class BedrockBridge {
    private static final String FLOODGATE = "org.geysermc.floodgate.api.FloodgateApi";
    private static final String GEYSER = "org.geysermc.geyser.api.GeyserApi";
    private final Function<String, Class<?>> resolver;

    public BedrockBridge() { this(BedrockBridge::resolveOptionalClass); }

    public BedrockBridge(Function<String, Class<?>> resolver) {
        this.resolver = Objects.requireNonNull(resolver, "resolver");
    }

    public boolean isBedrock(UUID playerId) {
        if (playerId == null) return false;
        return detected(FLOODGATE, "getInstance", "isFloodgatePlayer", playerId)
                || detected(GEYSER, "api", "isBedrockPlayer", playerId);
    }

    private boolean detected(String className, String factory, String lookup, UUID playerId) {
        try {
            Class<?> api = resolver.apply(className);
            if (api == null) return false;
            Object singleton = api.getMethod(factory).invoke(null);
            if (singleton == null) return false;
            return Boolean.TRUE.equals(api.getMethod(lookup, UUID.class).invoke(singleton, playerId));
        } catch (ReflectiveOperationException | RuntimeException | LinkageError unavailable) {
            return false;
        }
    }

    private static Class<?> resolveOptionalClass(String name) {
        try {
            return Class.forName(name, false, BedrockBridge.class.getClassLoader());
        } catch (ClassNotFoundException absent) {
            return null;
        }
    }
}
