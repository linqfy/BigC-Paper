package dev.linqfy.bigCasares.modules.customcrossbow;

import dev.linqfy.bigCasares.BigCasares;
import dev.linqfy.bigCasares.module.PluginModule;
import dev.linqfy.bigCasares.module.runtime.BukkitRuntimeRegistrations;
import dev.linqfy.bigCasares.module.runtime.RuntimeRegistrationScope;
import org.bukkit.NamespacedKey;

public final class CustomCrossbowModule implements PluginModule {

    private final BigCasares plugin;
    private final NamespacedKey chargeTypeKey;
    private final NamespacedKey fireworkPowerKey;
    private final NamespacedKey chargeCountKey;
    private final NamespacedKey originalItemModelKey;
    private final NamespacedKey prismarineArrowKey;
    private final NamespacedKey echoArrowKey;
    private final NamespacedKey amethystArrowKey;
    private final NamespacedKey recipeKey;

    private PrismarineArrowItem prismarineArrowItem;
    private EchoArrowItem echoArrowItem;
    private GoldenTippedAmethystArrowItem amethystArrowItem;
    private PrismarineArrowListener prismarineArrowListener;
    private RuntimeRegistrationScope compatibilityScope;

    public CustomCrossbowModule(BigCasares plugin) {
        this.plugin = plugin;
        this.chargeTypeKey = plugin == null ? null : new NamespacedKey(plugin, "custom_crossbow_charge");
        this.fireworkPowerKey = plugin == null ? null : new NamespacedKey(plugin, "custom_crossbow_firework_power");
        this.chargeCountKey = plugin == null ? null : new NamespacedKey(plugin, "custom_crossbow_charge_count");
        this.originalItemModelKey = plugin == null ? null : new NamespacedKey(plugin, "custom_crossbow_original_item_model");
        this.prismarineArrowKey = plugin == null ? null : new NamespacedKey(plugin, "prismarine_arrow");
        this.echoArrowKey = plugin == null ? null : new NamespacedKey(plugin, "echo_arrow");
        this.amethystArrowKey = plugin == null ? null : new NamespacedKey(plugin, "golden_tipped_amethyst_arrow");
        this.recipeKey = plugin == null ? null : new NamespacedKey(plugin, "prismarine_arrow_recipe");
    }

    @Override
    public String getId() {
        return "custom-crossbow";
    }

    @Override
    public void onEnable() {
        RuntimeRegistrationScope scope = new RuntimeRegistrationScope();
        this.compatibilityScope = scope;
        onEnable(scope);
    }

    @Override
    public void onEnable(RuntimeRegistrationScope scope) {
        BukkitRuntimeRegistrations registrations = new BukkitRuntimeRegistrations(plugin, scope);
        CustomCrossbowSettings settings = CustomCrossbowSettings.load(plugin.getConfig());
        CustomCrossbowData crossbowData = new CustomCrossbowData(
            chargeTypeKey,
            fireworkPowerKey,
            chargeCountKey,
            originalItemModelKey
        );
        CustomCrossbowDurabilityService durabilityService = new CustomCrossbowDurabilityService();
        EchoShardCooldownService echoShardCooldownService = new EchoShardCooldownService(settings.echoShardCooldownTicks());
        this.prismarineArrowItem = new PrismarineArrowItem(plugin.getCustomItemRegistry(), prismarineArrowKey);
        this.echoArrowItem = new EchoArrowItem(plugin.getCustomItemRegistry(), echoArrowKey);
        this.amethystArrowItem = new GoldenTippedAmethystArrowItem(plugin.getCustomItemRegistry(), amethystArrowKey);
        this.prismarineArrowListener = new PrismarineArrowListener(prismarineArrowItem, registrations);
        RocketJumpAirController rocketJumpAirController = new RocketJumpAirController(plugin, registrations);

        CrossbowEnchantmentData enchantmentData = new CrossbowEnchantmentData();
        SniperDamageService sniperDamage = new SniperDamageService();
        for (CrossbowEnchantment enchantment : CrossbowEnchantment.values()) {
            plugin.getCustomItemRegistry().register(new CrossbowEnchantmentBook(enchantment));
            scope.register(enchantment.bookId(), () -> plugin.getCustomItemRegistry().unregister(enchantment.bookId()));
        }
        registrations.registerListener("crossbow-enchantments", new CrossbowEnchantmentListener(enchantmentData));
        registrations.registerListener("sniper-damage", sniperDamage);
        SniperCrossbowListener sniper = new SniperCrossbowListener(
            plugin.getServer(), enchantmentData, sniperDamage, new NmsSniperScopeView(plugin));
        registrations.ownCloseable("sniper-runtime", sniper);
        registrations.scheduleRepeating("sniper-scope", sniper::tick, 1L, 1L);
        registrations.registerListener("sniper-crossbow", sniper);
        registrations.registerListener("multicharge-crossbow",
            new MultichargeListener(enchantmentData, echoArrowItem, amethystArrowItem, registrations));

        plugin.getCustomItemRegistry().register(prismarineArrowItem);
        plugin.getCustomItemRegistry().register(echoArrowItem);
        plugin.getCustomItemRegistry().register(amethystArrowItem);
        scope.register("custom-item", () -> plugin.getCustomItemRegistry().unregister(PrismarineArrowItem.ID));
        scope.register("echo-arrow-item", () -> plugin.getCustomItemRegistry().unregister(EchoArrowItem.ID));
        scope.register("amethyst-arrow-item", () -> plugin.getCustomItemRegistry().unregister(GoldenTippedAmethystArrowItem.ID));
        scope.register("prismarine-runtime", prismarineArrowListener::shutdown);
        registerListeners(registrations, crossbowData, settings, durabilityService,
            echoShardCooldownService, rocketJumpAirController);
    }

    @Override
    public void onDisable() {
        if (prismarineArrowListener != null) {
            prismarineArrowListener.shutdown();
        }
        RuntimeRegistrationScope scope = compatibilityScope;
        compatibilityScope = null;
        if (scope != null) {
            scope.close();
        }
        prismarineArrowListener = null;
        prismarineArrowItem = null;
        echoArrowItem = null;
        amethystArrowItem = null;
    }

    private void registerListeners(
        BukkitRuntimeRegistrations registrations,
        CustomCrossbowData crossbowData,
        CustomCrossbowSettings settings,
        CustomCrossbowDurabilityService durabilityService,
        EchoShardCooldownService echoShardCooldownService,
        RocketJumpAirController rocketJumpAirController
    ) {
        registrations.registerListener(
            "native-load-listener",
            new NativeCrossbowLoadListener(crossbowData, echoArrowItem, amethystArrowItem, settings, registrations)
        );
        registrations.registerListener(
            "echo-conversion-listener",
            new EchoArrowConversionListener(echoArrowItem, settings.maxEchoArrows(), registrations)
        );
        registrations.registerListener(
            "custom-arrow-limit-listener",
            new CustomArrowLimitListener(echoArrowItem, amethystArrowItem, settings)
        );
        registrations.registerListener(
            "shoot-listener",
            new CustomCrossbowShootListener(
                plugin,
                crossbowData,
                prismarineArrowItem,
                echoArrowItem,
                amethystArrowItem,
                settings,
                durabilityService,
                echoShardCooldownService,
                prismarineArrowListener::track,
                rocketJumpAirController::activate,
                registrations
            )
        );
        registrations.registerListener("prismarine-arrow-listener", prismarineArrowListener);
        registrations.registerListener("durability-listener", new CustomCrossbowDurabilityListener(durabilityService));
        registrations.registerListener("inventory-limit-listener", new CustomCrossbowInventoryLimitListener(crossbowData, settings));
        registrations.registerListener("craft-listener", new PrismarineArrowCraftListener(recipeKey, prismarineArrowItem));
        registrations.registerListener("loot-listener", new CustomCrossbowLootListener(settings, echoArrowItem));
    }

}
