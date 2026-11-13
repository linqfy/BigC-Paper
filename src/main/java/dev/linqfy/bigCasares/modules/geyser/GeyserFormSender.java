package dev.linqfy.bigCasares.modules.geyser;

import java.lang.reflect.InvocationTargetException;
import java.util.UUID;
import java.util.function.Consumer;
import java.util.function.IntConsumer;

/** Resolves forms in Geyser's loader: Floodgate also exports unrelocated Cumulus classes. */
final class GeyserFormSender {
    private GeyserFormSender() { }

    static boolean send(Object api, UUID playerId, BedrockShopForm form, IntConsumer response) {
        try {
            ClassLoader loader = api.getClass().getClassLoader();
            Class<?> formType = loader.loadClass("org.geysermc.cumulus.form.Form");
            Object built = build(loader, form, response);
            return (boolean) api.getClass().getMethod("sendForm", UUID.class, formType)
                .invoke(api, playerId, built);
        } catch (ReflectiveOperationException failure) {
            throw new IllegalStateException("No se pudo enviar el menú de Geyser", cause(failure));
        }
    }

    static Object build(ClassLoader loader, BedrockShopForm form, IntConsumer response)
            throws ReflectiveOperationException {
        Class<?> simple = loader.loadClass("org.geysermc.cumulus.form.SimpleForm");
        Class<?> builderType = loader.loadClass("org.geysermc.cumulus.form.SimpleForm$Builder");
        Class<?> responseType = loader.loadClass("org.geysermc.cumulus.response.SimpleFormResponse");
        Object builder = simple.getMethod("builder").invoke(null);
        builderType.getMethod("title", String.class).invoke(builder, form.title());
        builderType.getMethod("content", String.class).invoke(builder, form.content());
        for (BedrockShopForm.Button button : form.buttons()) {
            if (button.iconUrl() == null || button.iconUrl().isBlank()) {
                builderType.getMethod("button", String.class).invoke(builder, button.text());
            } else {
                Class<?> imageType = loader.loadClass("org.geysermc.cumulus.util.FormImage$Type");
                Object url = imageType.getField("URL").get(null);
                builderType.getMethod("button", String.class, imageType, String.class)
                    .invoke(builder, button.text(), url, button.iconUrl());
            }
        }
        Consumer<Object> callback = result -> {
            try {
                response.accept((int) responseType.getMethod("clickedButtonId").invoke(result));
            } catch (ReflectiveOperationException failure) {
                throw new IllegalStateException("No se pudo leer la respuesta de Geyser", cause(failure));
            }
        };
        builderType.getMethod("validResultHandler", Consumer.class).invoke(builder, callback);
        return builderType.getMethod("build").invoke(builder);
    }

    private static Throwable cause(ReflectiveOperationException failure) {
        return failure instanceof InvocationTargetException invocation ? invocation.getCause() : failure;
    }
}
