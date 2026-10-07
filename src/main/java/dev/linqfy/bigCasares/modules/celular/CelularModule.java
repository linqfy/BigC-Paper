package dev.linqfy.bigCasares.modules.celular;

import dev.linqfy.bigCasares.BigCasares;
import dev.linqfy.bigCasares.module.PluginModule;
import dev.linqfy.bigCasares.module.runtime.BukkitRuntimeRegistrations;
import dev.linqfy.bigCasares.module.runtime.RuntimeRegistrationScope;
import dev.linqfy.bigCasares.platform.ClientPlatform;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.command.PluginCommand;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ShapedRecipe;

import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;

public final class CelularModule implements PluginModule {

    public static final String MODULE_ID = "celular";
    /** Bedrock icon key, generated from the "celular" entry of resourcepack/shared/registry.yml. */
    public static final String BEDROCK_ICON = "bigcasares.celular";
    public static final String DISPLAY_NAME = "Celular";

    private final BigCasares plugin;
    private RuntimeRegistrationScope compatibilityScope;
    private CelularService service;

    public CelularModule(BigCasares plugin) {
        this.plugin = plugin;
    }

    @Override
    public String getId() {
        return MODULE_ID;
    }

    @Override
    public void onEnable() {
        RuntimeRegistrationScope scope = new RuntimeRegistrationScope();
        compatibilityScope = scope;
        onEnable(scope);
    }

    @Override
    public void onEnable(RuntimeRegistrationScope scope) {
        if (plugin == null) {
            return;
        }
        CelularSettings settings = CelularSettingsLoader.load(plugin.getConfig());
        service = new CelularService(loadVideos(), settings);
        CelularItems items = new CelularItems(plugin, service);
        BukkitRuntimeRegistrations registrations = new BukkitRuntimeRegistrations(plugin, scope);

        NamespacedKey recipeKey = null;
        if (settings.recipeEnabled()) {
            recipeKey = new NamespacedKey(plugin, CelularRecipe.KEY);
            ShapedRecipe recipe = new ShapedRecipe(recipeKey, items.create());
            recipe.shape(CelularRecipe.SHAPE.toArray(String[]::new));
            for (Map.Entry<Character, String> ingredient : CelularRecipe.INGREDIENTS.entrySet()) {
                recipe.setIngredient(ingredient.getKey(), Material.valueOf(ingredient.getValue()));
            }
            registrations.registerRecipe("celular-recipe", recipeKey, recipe);
            for (Player player : plugin.getServer().getOnlinePlayers()) {
                player.discoverRecipe(recipeKey);
            }
        }
        registrations.registerListener("celular-listener",
            new CelularListener(service, items, new CelularVillages(plugin), recipeKey,
                playerId -> plugin.resolvePlayerPlatform(playerId) == ClientPlatform.BEDROCK));

        PluginCommand pluginCommand = plugin.getCommand("celular");
        if (pluginCommand == null) {
            throw new IllegalStateException("Required command is not declared: celular");
        }
        CelularCommand command = new CelularCommand(plugin.getServer(), items);
        registrations.bindCommand("celular-command", pluginCommand, command, command);
        plugin.getLogger().info("[Celular] Module enabled with " + service.size() + " videos.");
    }

    @Override
    public void onDisable() {
        RuntimeRegistrationScope scope = compatibilityScope;
        compatibilityScope = null;
        if (scope != null) {
            scope.close();
        }
        service = null;
    }

    /** Java item models of the phone, for the Geyser mappings. Read from the jar, so it works before onEnable. */
    public List<String> itemModels() {
        if (plugin == null) {
            return List.of();
        }
        try {
            return new CelularService(loadVideos(), CelularSettings.defaults()).itemModels();
        } catch (IllegalStateException | IllegalArgumentException unavailable) {
            plugin.getLogger().warning("[Celular] No Bedrock items: " + unavailable.getMessage());
            return List.of();
        }
    }

    public int videoCount() {
        return service == null ? 0 : service.size();
    }

    private List<CelularVideo> loadVideos() {
        try (InputStream stream = plugin.getResource(CelularVideosLoader.RESOURCE)) {
            if (stream == null) {
                throw new IllegalStateException("Missing " + CelularVideosLoader.RESOURCE + " in the plugin jar");
            }
            return CelularVideosLoader.load(new InputStreamReader(stream, StandardCharsets.UTF_8));
        } catch (IOException exception) {
            throw new IllegalStateException("Could not read " + CelularVideosLoader.RESOURCE, exception);
        }
    }
}
