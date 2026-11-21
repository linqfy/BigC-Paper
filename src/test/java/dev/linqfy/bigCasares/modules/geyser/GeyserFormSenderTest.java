package dev.linqfy.bigCasares.modules.geyser;

import org.junit.jupiter.api.Test;

import java.net.URL;
import java.net.URLClassLoader;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotSame;

class GeyserFormSenderTest {
    @Test
    void buildsAndHandlesResponsesInGeysersCopyRatherThanFloodgatesCopy() throws Exception {
        Class<?> otherCopy = Class.forName("org.geysermc.cumulus.form.SimpleForm");
        URL jar = otherCopy.getProtectionDomain().getCodeSource().getLocation();
        try (var loader = new URLClassLoader(new URL[]{jar}, getClass().getClassLoader()) {
            @Override
            protected Class<?> loadClass(String name, boolean resolve) throws ClassNotFoundException {
                if (!name.startsWith("org.geysermc.cumulus.")) return super.loadClass(name, resolve);
                synchronized (getClassLoadingLock(name)) {
                    Class<?> loaded = findLoadedClass(name);
                    if (loaded == null) loaded = findClass(name);
                    if (resolve) resolveClass(loaded);
                    return loaded;
                }
            }
        }) {
            Class<?> simple = loader.loadClass(otherCopy.getName());
            assertNotSame(otherCopy, simple);
            AtomicInteger clicked = new AtomicInteger(-1);
            var form = new BedrockShopForm("Armas", "Elegí", List.of(
                new BedrockShopForm.Button("Primera", null),
                new BedrockShopForm.Button("Segunda", "https://example.test/icon.png")), (uuid, index) -> { });
            Object built = GeyserFormSender.build(loader, form, clicked::set);
            assertEquals(loader, built.getClass().getClassLoader());
            assertEquals("Armas", simple.getMethod("title").invoke(built));
            assertEquals("Elegí", simple.getMethod("content").invoke(built));
            List<?> buttons = (List<?>) simple.getMethod("buttons").invoke(built);
            assertEquals(2, buttons.size());
            Class<?> component = loader.loadClass("org.geysermc.cumulus.component.ButtonComponent");
            Object answer = loader.loadClass("org.geysermc.cumulus.response.impl.SimpleFormResponseImpl")
                .getMethod("of", int.class, component).invoke(null, 1, buttons.get(1));
            Class<?> response = loader.loadClass("org.geysermc.cumulus.response.FormResponse");
            Class<?> result = loader.loadClass("org.geysermc.cumulus.response.result.FormResponseResult");
            Object valid = result.getMethod("valid", response).invoke(null, answer);
            built.getClass().getMethod("callResultHandler", result).invoke(built, valid);
            assertEquals(1, clicked.get());
            clicked.set(-1);
            built.getClass().getMethod("callResultHandler", result)
                .invoke(built, result.getMethod("closed").invoke(null));
            assertEquals(-1, clicked.get(), "closing a form must not activate a skill");
        }
    }
}
