package dev.linqfy.bigCasares.modules.tideframe.engine.compat;

import java.util.Optional;
import java.util.Objects;
import java.util.function.Function;
import java.util.regex.Pattern;

/** Optional lookup only. The caller owns Bukkit type validation, cloning and rendering. */
public final class ItemsAdderBridge {
    private static final String API = "dev.lone.itemsadder.api.CustomStack";
    private static final Pattern ID = Pattern.compile("[a-z0-9_]+:[a-z0-9_/.-]+");
    private final Function<String, Class<?>> resolver;

    public ItemsAdderBridge() { this(ItemsAdderBridge::resolveOptionalClass); }

    public ItemsAdderBridge(Function<String, Class<?>> resolver) {
        this.resolver = Objects.requireNonNull(resolver, "resolver");
    }

    /** Does not cache registry objects, so each lookup observes provider reloads. */
    public Optional<Object> item(String namespaceId) {
        if (!validId(namespaceId)) return Optional.empty();
        try {
            Class<?> api = resolver.apply(API);
            if (api == null) return Optional.empty();
            Object customStack = api.getMethod("getInstance", String.class).invoke(null, namespaceId);
            if (customStack == null) return Optional.empty();
            return Optional.ofNullable(api.getMethod("getItemStack").invoke(customStack));
        } catch (ReflectiveOperationException | RuntimeException | LinkageError unavailable) {
            return Optional.empty();
        }
    }

    private static boolean validId(String id) {
        if (id == null || id.length() > 128 || !ID.matcher(id).matches()) return false;
        String path = id.substring(id.indexOf(':') + 1);
        for (String part : path.split("/", -1)) if (part.equals("..")) return false;
        return true;
    }

    private static Class<?> resolveOptionalClass(String name) {
        try {
            return Class.forName(name, false, ItemsAdderBridge.class.getClassLoader());
        } catch (ClassNotFoundException absent) {
            return null;
        }
    }
}
