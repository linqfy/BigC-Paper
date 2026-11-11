package dev.linqfy.bigCasares.module;

import dev.linqfy.bigCasares.module.runtime.RuntimeCleanupOutcome;
import dev.linqfy.bigCasares.module.runtime.RuntimeCleanupReport;
import dev.linqfy.bigCasares.module.runtime.RuntimeGeneration;
import dev.linqfy.bigCasares.module.runtime.RuntimeRegistrationScope;
import org.bukkit.configuration.Configuration;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.logging.Level;
import java.util.logging.Logger;

public final class ModuleManager {

    private final ConfigurationSection config;
    private final Logger logger;
    private final Map<String, PluginModule> registeredModules = new LinkedHashMap<>();
    private final Map<String, ActiveModule> activeModules = new LinkedHashMap<>();
    private long nextGenerationId;
    private RuntimeGeneration activeGeneration;

    public ModuleManager(JavaPlugin plugin, FileConfiguration config) {
        this(config, plugin.getLogger());
    }

    ModuleManager(ConfigurationSection config, Logger logger) {
        this.config = Objects.requireNonNull(config, "config");
        this.logger = Objects.requireNonNull(logger, "logger");
    }

    public void register(PluginModule module) {
        Objects.requireNonNull(module, "module");
        String normalizedId = normalizeModuleId(module.getId());
        if (registeredModules.containsKey(normalizedId)) {
            throw new IllegalArgumentException("Duplicate module id: " + module.getId());
        }
        registeredModules.put(normalizedId, module);
    }

    public ModuleLifecycleReport enableRegisteredModules() {
        return enableRegisteredModules(new RuntimeGeneration(++nextGenerationId));
    }

    public ModuleLifecycleReport enableRegisteredModules(RuntimeGeneration generation) {
        Objects.requireNonNull(generation, "generation");
        if (activeGeneration != null) {
            throw new IllegalStateException("Active modules must be disabled before activation");
        }
        if (!generation.isActive()) {
            throw new IllegalStateException("Cannot activate modules with a retired generation");
        }

        nextGenerationId = Math.max(nextGenerationId, generation.id());
        activeGeneration = generation;
        List<ModuleLifecycleOutcome> outcomes = new ArrayList<>();
        for (Map.Entry<String, PluginModule> entry : registeredModules.entrySet()) {
            String moduleId = entry.getKey();
            PluginModule module = entry.getValue();
            if (!isEnabledInConfig(moduleId)) {
                logger.info("Module disabled by config: " + moduleId);
                outcomes.add(outcome(moduleId, ModuleLifecycleStatus.DISABLED_BY_CONFIG, null));
                continue;
            }

            RuntimeRegistrationScope scope = new RuntimeRegistrationScope(generation, moduleId);
            try {
                module.onEnable(scope);
                activeModules.put(moduleId, new ActiveModule(module, scope));
                outcomes.add(outcome(moduleId, ModuleLifecycleStatus.ENABLED, null));
                logger.info("Enabled module: " + moduleId);
            } catch (Throwable throwable) {
                logger.log(Level.SEVERE, "Failed to enable module: " + moduleId, throwable);
                outcomes.add(outcome(moduleId, ModuleLifecycleStatus.ENABLE_FAILED, throwable));
                generation.retire();
                disableOne(moduleId, new ActiveModule(module, scope), outcomes);
                disableActiveModulesInto(outcomes);
                activeGeneration = null;
                return new ModuleLifecycleReport(outcomes);
            }
        }
        return new ModuleLifecycleReport(outcomes);
    }

    public ModuleLifecycleReport disableActiveModules() {
        if (activeGeneration != null) {
            activeGeneration.retire();
        }
        List<ModuleLifecycleOutcome> outcomes = new ArrayList<>();
        disableActiveModulesInto(outcomes);
        activeGeneration = null;
        return new ModuleLifecycleReport(outcomes);
    }

    public int getRegisteredModuleCount() {
        return registeredModules.size();
    }

    public int getActiveModuleCount() {
        return activeModules.size();
    }

    public RuntimeGeneration getActiveGeneration() {
        return activeGeneration;
    }

    private void disableActiveModulesInto(List<ModuleLifecycleOutcome> outcomes) {
        List<Map.Entry<String, ActiveModule>> entries = new ArrayList<>(activeModules.entrySet());
        activeModules.clear();
        for (int index = entries.size() - 1; index >= 0; index--) {
            Map.Entry<String, ActiveModule> entry = entries.get(index);
            disableOne(entry.getKey(), entry.getValue(), outcomes);
        }
    }

    private void disableOne(String moduleId, ActiveModule activeModule, List<ModuleLifecycleOutcome> outcomes) {
        try {
            activeModule.module().onDisable();
            outcomes.add(outcome(moduleId, ModuleLifecycleStatus.DISABLED, null));
            logger.info("Disabled module: " + moduleId);
        } catch (Throwable throwable) {
            outcomes.add(outcome(moduleId, ModuleLifecycleStatus.DISABLE_FAILED, throwable));
            logger.log(Level.SEVERE, "Failed to disable module: " + moduleId, throwable);
        }

        RuntimeCleanupReport cleanupReport = activeModule.scope().close();
        for (RuntimeCleanupOutcome cleanupFailure : cleanupReport.failures()) {
            outcomes.add(outcome(moduleId, ModuleLifecycleStatus.CLEANUP_FAILED, cleanupFailure.failure()));
            logger.log(Level.SEVERE,
                "Failed to clean runtime resource for module " + moduleId + ": " + cleanupFailure.resourceId(),
                cleanupFailure.failure());
        }
    }

    private boolean isEnabledInConfig(String moduleId) {
        return isEnabledInConfig(config, moduleId);
    }

    static boolean isEnabledInConfig(ConfigurationSection config, String moduleId) {
        String nestedPath = "modules." + moduleId + ".enabled";
        if (config.isSet(nestedPath)) {
            return config.getBoolean(nestedPath);
        }
        String flatPath = "modules." + moduleId;
        if (config.isBoolean(flatPath)) {
            return config.getBoolean(flatPath);
        }
        if ("entity-shop-system".equals(moduleId)) {
            String legacyPath = "modules.shop-system";
            if (config.isBoolean(legacyPath)) {
                return config.getBoolean(legacyPath);
            }
        }
        // A server's config.yml from before a module existed has no switch for it: the one bundled in the JAR
        // decides (a module shipped turned off stays off on upgrade). Without a bundled switch, it is on.
        Configuration root = config.getRoot();
        Configuration defaults = root == null ? null : root.getDefaults();
        if (defaults != null) {
            String prefix = config.getCurrentPath() == null || config.getCurrentPath().isEmpty() ? "" : config.getCurrentPath() + ".";
            if (defaults.isBoolean(prefix + nestedPath)) {
                return defaults.getBoolean(prefix + nestedPath);
            }
            if (defaults.isBoolean(prefix + flatPath)) {
                return defaults.getBoolean(prefix + flatPath);
            }
        }
        return true;
    }

    private String normalizeModuleId(String moduleId) {
        return moduleId.toLowerCase(Locale.ROOT).trim();
    }

    private ModuleLifecycleOutcome outcome(String moduleId, ModuleLifecycleStatus status, Throwable failure) {
        return new ModuleLifecycleOutcome(moduleId, status, failure);
    }

    private record ActiveModule(PluginModule module, RuntimeRegistrationScope scope) {
    }
}
