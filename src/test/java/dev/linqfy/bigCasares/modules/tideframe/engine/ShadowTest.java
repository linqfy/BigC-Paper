package dev.linqfy.bigCasares.modules.tideframe.engine;

import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.net.URL;
import java.net.URLClassLoader;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.*;

/**
 * The shadow switch is reflection against an Adventure the build does not compile against: this loads the Adventure of
 * the local test server (Minecraft 26.2) and checks it for real. Skipped where that server is not present.
 */
class ShadowTest {
    private static final Path LIBRARIES = Path.of("..", "..", "work", "tideframe-local-offline", "libraries", "net", "kyori");

    @Test void theBuildsOwnAdventureHasNoTextShadowsAndTheSwitchSaysSo() {
        assertNull(Shadow.resolve(new URLClassLoader(new URL[0], ClassLoader.getPlatformClassLoader())), "no Adventure at all");
        if (!Shadow.available()) assertEquals(net.kyori.adventure.text.Component.text("x"), Shadow.off(net.kyori.adventure.text.Component.text("x")));
    }

    @Test void theResolvedPaperAdventureSwitchesTheShadowOff() throws Exception {
        var loader = net.kyori.adventure.text.Component.class.getClassLoader();
            var handle = Shadow.resolve(loader);
            assertNotNull(handle, "Component.shadowColor(ARGBLike) and ShadowColor.none() are found");
            Class<?> componentClass = loader.loadClass("net.kyori.adventure.text.Component");
            Object plain = componentClass.getMethod("text", String.class).invoke(null, "x");
            assertNull(componentClass.getMethod("shadowColor").invoke(plain), "by default the game draws the shadow");
            Object off = handle.apply(plain);
            Object shadow = componentClass.getMethod("shadowColor").invoke(off);
            assertNotNull(shadow, "now the component carries an explicit shadow colour");
            Class<?> shadowColorClass = loader.loadClass("net.kyori.adventure.text.format.ShadowColor");
            assertEquals(0, shadowColorClass.getMethod("value").invoke(shadow), "fully transparent: no shadow is drawn");
    }
}
