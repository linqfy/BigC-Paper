package dev.linqfy.bigCasares.modules.legendary;

import dev.linqfy.bigCasares.module.PluginModule;
import dev.linqfy.bigCasares.module.runtime.BukkitRuntimeRegistrations;
import dev.linqfy.bigCasares.module.runtime.RuntimeRegistrationScope;
import dev.linqfy.bigCasares.BigCasares;

public final class LegendaryWeaponsModule implements PluginModule {
    public static final String MODULE_ID = "legendary-weapons";
    private final BigCasares plugin;
    private RuntimeRegistrationScope compatibilityScope;
    private LegendaryRuntime runtime;
    private LegendaryCraftingEvents events;
    private LegendaryModelAnimations animations;
    private LegendaryPvpLevels levels;
    private LegendaryElementalAbilities elemental;
    private LegendaryAltarAbilities altar;

    public LegendaryWeaponsModule(BigCasares plugin) {
        this.plugin = plugin;
    }

    @Override
    public String getId() {
        return MODULE_ID;
    }

    @Override
    public void onEnable() {
        compatibilityScope = new RuntimeRegistrationScope();
        onEnable(compatibilityScope);
    }

    @Override
    public void onEnable(RuntimeRegistrationScope scope) {
        try {
            var legacy = plugin.getServer().getPluginManager().getPlugin("LegendaryWeapons");
            if (legacy != null && legacy.isEnabled()) {
                throw new IllegalStateException("Retirá el antiguo LegendaryWeapons antes de activar este módulo.");
            }
            var folder = plugin.getDataFolder().toPath();
            LegendaryStateStore store = new LegendaryStateStore(LegendaryLegacyData.migrate(
                    folder, folder.resolveSibling("LegendaryWeapons")));
            BukkitRuntimeRegistrations registrations =
                    new BukkitRuntimeRegistrations(plugin, scope);
            if (store.snapshot().keySet().stream()
                    .anyMatch(key -> key.startsWith("altar.") || key.startsWith("craft."))) {
                throw new IllegalStateException(
                        "Hay datos de altares anteriores. Conserva una copia y usa una instalación"
                            + " nueva para Crafting PvP.");
            }
            for (var weapon : LegendaryWeapon.available()) {
                plugin.getCustomItemRegistry().register(new LegendaryWeaponItem(weapon));
                scope.register("legendary-item-" + weapon.id,
                        () -> plugin.getCustomItemRegistry().unregister(weapon.id));
            }
            events = new LegendaryCraftingEvents(plugin, store, registrations);
            levels = new LegendaryPvpLevels(store);
            registrations.scheduleRepeating("legendary-lore", new LegendaryWeaponLore(plugin, levels), 20, 20);
            var progression =
                    registrations.registerListener(
                            "legendary-pvp-progression",
                            new LegendaryPvpProgression(plugin, levels));
            var carrier = registrations.registerListener("legendary-item-carrier", new LegendaryItemCarrier(registrations));
            registrations.ownCloseable("legendary-carrier-state", carrier);
            var vanilla = new LegendaryVanillaBridge();
            var enchanting = registrations.registerListener("legendary-enchanting",
                    new LegendaryEnchanting(vanilla, registrations));
            registrations.ownCloseable("legendary-enchanting-state", enchanting);
            var carrierCombat = registrations.registerListener("legendary-carrier-combat",
                    new LegendaryCarrierCombat(vanilla, progression, registrations));
            registrations.ownCloseable("legendary-carrier-combat-state", carrierCombat);
            animations =
                    registrations.registerListener(
                            "legendary-model-animations",
                            new LegendaryModelAnimations(
                                    plugin,
                                    player ->
                                            events.pending(player.getUniqueId())
                                                    || store.get("craft." + player.getUniqueId())
                                                            != null));
            runtime = new LegendaryRuntime(plugin, store, registrations, events::pending);
            scope.register("legendary-model-animation-state", animations::shutdown);
            scope.register("legendary-effects", runtime::shutdown);
            scope.register("legendary-events", events::shutdown);
            registrations.registerListener("legendary-event-listener", events);
            registrations.registerListener("legendary-listener", runtime);
            elemental =
                    registrations.registerListener(
                            "legendary-elemental-abilities",
                            new LegendaryElementalAbilities(
                                    plugin,
                                    store,
                                    levels,
                                    progression,
                                    animations,
                                    runtime::blocked,
                                    this::activeEffects,
                                    registrations));
            altar =
                    registrations.registerListener(
                            "legendary-altar-abilities",
                            new LegendaryAltarAbilities(
                                    plugin,
                                    store,
                                    levels,
                                    progression,
                                    animations,
                                    runtime::blocked,
                                    this::activeEffects,
                                    registrations));
            scope.register("legendary-elemental-state", elemental::shutdown);
            scope.register("legendary-altar-state", altar::shutdown);
            registrations.scheduleRepeating("legendary-elemental-tick", elemental, 1, 1);
            registrations.scheduleRepeating("legendary-altar-tick", altar, 1, 1);
            registrations.scheduleRepeating("legendary-effects-tick", runtime, 20, 20);
            registrations.scheduleRepeating("legendary-model-animation-tick", animations, 1, 1);
            registrations.scheduleRepeating("legendary-event-tick", events, 10, 10);
            var command = plugin.getCommand("weapons");
            if (command == null)
                throw new IllegalStateException("Falta el comando weapons en plugin.yml.");
            var menu = new LegendaryAbilityMenu(plugin, levels, altar, elemental, scope);
            var executor = new LegendaryWeaponsCommand(plugin, store, events, levels, menu);
            registrations.bindCommand("legendary-command", command, executor, executor);
            for (var world : plugin.getServer().getWorlds()) {
                for (var chunk : world.getLoadedChunks()) {
                    runtime.cleanTemporary(chunk.getEntities());
                    events.clean(chunk.getEntities());
                }
            }
            for (var player : plugin.getServer().getOnlinePlayers()) {
                LegendaryItemCarrier.inventory(player);
                events.recover(player);
                events.discover(player);
                runtime.recover(player);
            }
        } catch (java.io.IOException failure) {
            throw new IllegalStateException(
                    "No se pudo abrir el estado persistente de armas legendarias.", failure);
        }
    }

    @Override
    public void onDisable() {
        if (compatibilityScope != null) {
            compatibilityScope.close();
            compatibilityScope = null;
        }
        if (runtime != null) runtime.shutdown();
        if (elemental != null) elemental.shutdown();
        if (altar != null) altar.shutdown();
        if (animations != null) animations.shutdown();
        if (events != null) events.shutdown();
        runtime = null;
        events = null;
    }

    private int activeEffects() {
        return (elemental == null ? 0 : elemental.activeEffects()) + (altar == null ? 0 : altar.activeEffects());
    }
}
