package dev.linqfy.bigCasares.modules.tideframe;

import dev.linqfy.bigCasares.module.PluginModule;
import dev.linqfy.bigCasares.module.runtime.BukkitRuntimeRegistrations;
import dev.linqfy.bigCasares.module.runtime.RuntimeRegistrationScope;
import dev.linqfy.bigCasares.platform.ClientPlatformGateway;
import dev.linqfy.bigCasares.modules.tideframe.engine.TideframeEngine;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.plugin.java.JavaPlugin;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.logging.Level;

/** BigCasares-owned Tideframe engine, compiled from source in the same JAR. */
public final class TideframeUiModule implements PluginModule {
    public static final String ID = "tideframe-ui";
    static final List<String> SCREENS = List.of("bigc_misiones", "bigc_tienda");
    private final JavaPlugin plugin;
    private final ClientPlatformGateway platforms;
    private volatile TideframeUi ui = TideframeUi.NONE;
    private boolean missions = true;
    private boolean shop = true;
    private RuntimeRegistrationScope compatibilityScope;
    private TideframeEngine runtime;
    private TideframeUiService service;

    public TideframeUiModule(JavaPlugin plugin, ClientPlatformGateway platforms) {
        this.plugin = Objects.requireNonNull(plugin);
        this.platforms = Objects.requireNonNull(platforms);
    }

    @Override public String getId() { return ID; }

    @Override public void onEnable() {
        compatibilityScope = new RuntimeRegistrationScope();
        onEnable(compatibilityScope);
    }

    @Override public void onEnable(RuntimeRegistrationScope scope) {
        BukkitRuntimeRegistrations registrations = new BukkitRuntimeRegistrations(plugin, scope);
        ConfigurationSection config = plugin.getConfig().getConfigurationSection(ID);
        missions = config == null || config.getBoolean("missions", true);
        shop = config == null || config.getBoolean("shop", true);
        if (plugin.getServer().getPluginManager().getPlugin("Tideframe") != null) {
            throw new IllegalStateException("Tideframe ya esta integrado en BigCasares: retirale el JAR independiente para evitar packs y comandos duplicados.");
        }
        installScreens();
        TideframeEngine engine = new TideframeEngine(plugin);
        runtime = engine;
        scope.register("tideframe-engine", this::stopRuntime);
        TideframeUiService connected = new TideframeUiService(new TideframeApiBridge(engine), platforms,
            engine::sendsOwnPack, task -> registrations.scheduleImmediate("tideframe-open", task),
            error -> plugin.getLogger().log(Level.SEVERE, "Fallo interno de Tideframe", error));
        try {
            registrations.registerListener("tideframe-events", TideframeEventRelay.create(connected));
        } catch (NoSuchMethodException impossibleApiMismatch) {
            throw new IllegalStateException("La API interna de Tideframe no coincide", impossibleApiMismatch);
        }
        registrations.registerListener("tideframe-players", new Listener() {
            @EventHandler public void onQuit(PlayerQuitEvent event) { connected.quit(event.getPlayer()); }
        });
        service = connected;
        ui = connected;
        engine.onEnable();
        plugin.getLogger().info("Tideframe integrado: motor y recursos del mismo JAR; no requiere otro plugin.");
    }

    @Override public void onDisable() {
        try {
            stopRuntime();
        } finally {
            if (compatibilityScope != null) {
                compatibilityScope.close();
                compatibilityScope = null;
            }
        }
    }

    public TideframeUi missions() { return missions ? ui : TideframeUi.NONE; }
    public TideframeUi shop() { return shop ? ui : TideframeUi.NONE; }
    public TideframeUi navigation() { return ui; }

    /** For {@code /bigcasares tideframe}: the engine's state and how many online players can see its screens. */
    public TideframeStatus status() {
        TideframeUiService running = service;
        return running == null ? TideframeStatus.off()
            : running.status(plugin.getServer().getOnlinePlayers(), SCREENS, missions, shop);
    }

    private void stopRuntime() {
        ui = TideframeUi.NONE;
        service = null;
        TideframeEngine engine = runtime;
        runtime = null;
        if (engine != null) engine.close();
    }

    private void installScreens() {
        List<TideframeScreenInstaller.BundledScreen> bundled = new ArrayList<>();
        for (String id : SCREENS) {
            try (InputStream input = plugin.getResource("tideframe/screens/" + id + ".json")) {
                if (input == null) throw new IOException("Falta la pantalla " + id);
                bundled.add(new TideframeScreenInstaller.BundledScreen(id, input.readAllBytes()));
            } catch (IOException failure) {
                throw new IllegalStateException("No se pudo leer la pantalla de Tideframe", failure);
            }
        }
        Path data = plugin.getDataFolder().toPath().resolve("tideframe");
        try {
            for (var result : TideframeScreenInstaller.install(data.resolve("engine/screens"), data, bundled)) {
                if (result.outcome() == TideframeScreenInstaller.Outcome.KEPT_EDITED) {
                    plugin.getLogger().info("Se conserva la pantalla editada " + result.id());
                }
            }
        } catch (IOException failure) {
            throw new IllegalStateException("No se pudieron instalar las pantallas de Tideframe", failure);
        }
    }
}
