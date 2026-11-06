package dev.linqfy.bigCasares.modules.tideframe.engine;

import java.lang.reflect.Method;
import java.lang.reflect.Proxy;
import java.time.Duration;
import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Consumer;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.event.ClickCallback;
import org.bukkit.entity.Player;

/**
 * Text input and yes/no questions through the game's own dialog screen (Paper 1.21.6+ / 26.x dialog API), reached
 * reflectively so the plugin still loads where it is missing. Input: one text field, OK and Cancel. Confirm: a
 * message with two buttons. Escape counts as Cancel / no.
 */
final class InputDialogs {
    private final boolean available;
    private Method create, baseBuilder, baseInputs, baseBody, baseEscape, baseBuild, plainMessage, textBuilder, textInitial, textMaxLength, textWidth, textBuild;
    private Method buttonBuilder, buttonAction, buttonBuild, customClick, confirmation, factoryEmpty, entryBase, entryType, showDialog, getText;
    private Class<?> callbackType, consumerType;

    InputDialogs() {
        boolean found;
        try {
            ClassLoader loader = Player.class.getClassLoader();
            Class<?> dialog = Class.forName("io.papermc.paper.dialog.Dialog", false, loader);
            Class<?> base = Class.forName("io.papermc.paper.registry.data.dialog.DialogBase", false, loader);
            Class<?> baseBuilderType = Class.forName("io.papermc.paper.registry.data.dialog.DialogBase$Builder", false, loader);
            Class<?> input = Class.forName("io.papermc.paper.registry.data.dialog.input.DialogInput", false, loader);
            Class<?> body = Class.forName("io.papermc.paper.registry.data.dialog.body.DialogBody", false, loader);
            Class<?> textBuilderType = Class.forName("io.papermc.paper.registry.data.dialog.input.TextDialogInput$Builder", false, loader);
            Class<?> button = Class.forName("io.papermc.paper.registry.data.dialog.ActionButton", false, loader);
            Class<?> buttonBuilderType = Class.forName("io.papermc.paper.registry.data.dialog.ActionButton$Builder", false, loader);
            Class<?> action = Class.forName("io.papermc.paper.registry.data.dialog.action.DialogAction", false, loader);
            Class<?> type = Class.forName("io.papermc.paper.registry.data.dialog.type.DialogType", false, loader);
            Class<?> factory = Class.forName("io.papermc.paper.registry.RegistryBuilderFactory", false, loader);
            Class<?> entryBuilder = Class.forName("io.papermc.paper.registry.data.dialog.DialogRegistryEntry$Builder", false, loader);
            Class<?> response = Class.forName("io.papermc.paper.dialog.DialogResponseView", false, loader);
            Class<?> dialogLike = Class.forName("net.kyori.adventure.dialog.DialogLike", false, loader);
            callbackType = Class.forName("io.papermc.paper.registry.data.dialog.action.DialogActionCallback", false, loader);
            consumerType = java.util.function.Consumer.class;
            create = dialog.getMethod("create", Consumer.class);
            baseBuilder = base.getMethod("builder", Component.class);
            baseInputs = baseBuilderType.getMethod("inputs", List.class);
            baseBody = baseBuilderType.getMethod("body", List.class);
            plainMessage = body.getMethod("plainMessage", Component.class);
            baseEscape = baseBuilderType.getMethod("canCloseWithEscape", boolean.class);
            baseBuild = baseBuilderType.getMethod("build");
            textBuilder = input.getMethod("text", String.class, Component.class);
            textInitial = textBuilderType.getMethod("initial", String.class);
            textMaxLength = textBuilderType.getMethod("maxLength", int.class);
            textWidth = textBuilderType.getMethod("width", int.class);
            textBuild = textBuilderType.getMethod("build");
            buttonBuilder = button.getMethod("builder", Component.class);
            buttonAction = buttonBuilderType.getMethod("action", action);
            buttonBuild = buttonBuilderType.getMethod("build");
            customClick = action.getMethod("customClick", callbackType, ClickCallback.Options.class);
            confirmation = type.getMethod("confirmation", button, button);
            factoryEmpty = factory.getMethod("empty");
            entryBase = entryBuilder.getMethod("base", base);
            entryType = entryBuilder.getMethod("type", type);
            showDialog = Player.class.getMethod("showDialog", dialogLike);
            getText = response.getMethod("getText", String.class);
            found = true;
        } catch (ReflectiveOperationException | LinkageError unavailable) {
            found = false;
        }
        available = found;
    }
    boolean available() { return available; }

    /**
     * Shows the dialog. Exactly one of the callbacks runs, on whatever thread Paper answers on (callers schedule
     * onto the player's owner thread); {@code cancel} also runs when the dialog cannot be shown.
     */
    void show(Player player, Component title, Component label, String initial, int maxLength, Consumer<String> submit, Runnable cancel) {
        var answered = new AtomicBoolean();
        Consumer<String> once = text -> { if (answered.compareAndSet(false, true)) submit.accept(text); };
        Runnable cancelOnce = () -> { if (answered.compareAndSet(false, true)) cancel.run(); };
        if (!available) { cancelOnce.run(); return; }
        try {
            Object textBuilderValue = textBuilder.invoke(null, "value", label);
            textInitial.invoke(textBuilderValue, initial.length() > maxLength ? initial.substring(0, maxLength) : initial);
            textMaxLength.invoke(textBuilderValue, maxLength);
            textWidth.invoke(textBuilderValue, 300);
            Object field = textBuild.invoke(textBuilderValue);
            Object baseBuilderValue = baseBuilder.invoke(null, title);
            baseEscape.invoke(baseBuilderValue, true);
            baseInputs.invoke(baseBuilderValue, List.of(field));
            Object base = baseBuild.invoke(baseBuilderValue);
            var options = ClickCallback.Options.builder().uses(1).lifetime(Duration.ofMinutes(10)).build();
            Object ok = button(Component.text("OK"), options, (view) -> {
                Object typed;
                try { typed = getText.invoke(view, "value"); } catch (ReflectiveOperationException failure) { typed = null; }
                if (typed == null) cancelOnce.run(); else once.accept(String.valueOf(typed));
            });
            Object no = button(Component.text("Cancel"), options, view -> cancelOnce.run());
            open(player, base, confirmation.invoke(null, ok, no));
        } catch (ReflectiveOperationException | RuntimeException | LinkageError failure) {
            cancelOnce.run();
        }
    }

    /**
     * Asks a yes/no question: {@code message} under {@code title}, with the two button labels. Exactly one callback
     * runs, on whatever thread Paper answers on; {@code onNo} also runs on Escape or when the dialog cannot be shown.
     */
    void confirm(Player player, Component title, Component message, Component yes, Component no, Runnable onYes, Runnable onNo) {
        var answered = new AtomicBoolean();
        Runnable yesOnce = () -> { if (answered.compareAndSet(false, true)) onYes.run(); };
        Runnable noOnce = () -> { if (answered.compareAndSet(false, true)) onNo.run(); };
        if (!available) { noOnce.run(); return; }
        try {
            Object baseBuilderValue = baseBuilder.invoke(null, title);
            baseEscape.invoke(baseBuilderValue, true);
            if (!message.equals(Component.empty())) baseBody.invoke(baseBuilderValue, List.of(plainMessage.invoke(null, message)));
            Object base = baseBuild.invoke(baseBuilderValue);
            var options = ClickCallback.Options.builder().uses(1).lifetime(Duration.ofMinutes(10)).build();
            // The game uses the second ("no") button when the player presses Escape.
            Object type = confirmation.invoke(null, button(yes, options, view -> yesOnce.run()), button(no, options, view -> noOnce.run()));
            open(player, base, type);
        } catch (ReflectiveOperationException | RuntimeException | LinkageError failure) {
            noOnce.run();
        }
    }
    private void open(Player player, Object base, Object type) throws ReflectiveOperationException {
        Consumer<Object> builder = factoryValue -> {
            try {
                Object entry = factoryEmpty.invoke(factoryValue);
                entryBase.invoke(entry, base);
                entryType.invoke(entry, type);
            } catch (ReflectiveOperationException failure) { throw new IllegalStateException(failure); }
        };
        Object dialog = create.invoke(null, builder);
        showDialog.invoke(player, dialog);
    }
    private Object button(Component label, ClickCallback.Options options, Consumer<Object> onClick) throws ReflectiveOperationException {
        Object callback = Proxy.newProxyInstance(callbackType.getClassLoader(), new Class<?>[]{callbackType}, (proxy, method, args) -> {
            if (method.getDeclaringClass() == Object.class) return switch (method.getName()) {
                case "hashCode" -> System.identityHashCode(proxy);
                case "equals" -> proxy == args[0];
                default -> "TideframeDialogCallback";
            };
            if (method.getName().equals("accept") && args != null && args.length >= 1) onClick.accept(args[0]);
            return null;
        });
        Object action = customClick.invoke(null, callback, options);
        Object builder = buttonBuilder.invoke(null, label);
        buttonAction.invoke(builder, action);
        return buttonBuild.invoke(builder);
    }
}
