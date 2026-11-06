package dev.linqfy.bigCasares.modules.tideframe.engine;

import net.kyori.adventure.text.Component;

/**
 * Turns off the game's per-glyph drop shadow (a 25 % copy drawn offset behind every title glyph) for auto-fit menus:
 * the shader scales marked glyphs, and a shadow keeps no marker, so it would stay behind, unscaled. Text shadows
 * exist as a component style from Adventure 4.18 ({@code Component.shadowColor(ARGBLike)}, the server of Minecraft
 * 26.x); the build compiles against an older API, so it is reached by reflection and reported as unavailable on a
 * server without it.
 */
final class Shadow {
    private Shadow() { }

    /** {@code Component.shadowColor(none)}, resolved against one class loader. */
    static final class Handle {
        private final java.lang.reflect.Method set;
        private final Object none;
        private Handle(java.lang.reflect.Method set, Object none) { this.set = set; this.none = none; }
        /** {@code component} (an Adventure Component of the same loader) with no drop shadow. */
        Object apply(Object component) throws ReflectiveOperationException { return set.invoke(component, none); }
    }

    /** The shadow switch of the Adventure that {@code loader} sees, or null when it has no text shadows. */
    static Handle resolve(ClassLoader loader) {
        try {
            Class<?> argb = loader.loadClass("net.kyori.adventure.util.ARGBLike");
            Class<?> color = loader.loadClass("net.kyori.adventure.text.format.ShadowColor");
            Class<?> component = loader.loadClass("net.kyori.adventure.text.Component");
            return new Handle(component.getMethod("shadowColor", argb), color.getMethod("none").invoke(null));
        } catch (ReflectiveOperationException | LinkageError | RuntimeException unavailable) { return null; }
    }

    private static final Handle HANDLE = resolve(Component.class.getClassLoader());
    /** True when this server can switch the shadow off. */
    static boolean available() { return HANDLE != null; }
    /** {@code component} with no drop shadow; unchanged when the server cannot. */
    static Component off(Component component) {
        if (HANDLE == null) return component;
        try { return (Component) HANDLE.apply(component); }
        catch (ReflectiveOperationException | RuntimeException failure) { return component; }
    }
}
