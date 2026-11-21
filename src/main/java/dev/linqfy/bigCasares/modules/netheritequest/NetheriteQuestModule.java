package dev.linqfy.bigCasares.modules.netheritequest;

import dev.linqfy.bigCasares.BigCasares;
import dev.linqfy.bigCasares.module.PluginModule;
import dev.linqfy.bigCasares.module.runtime.BukkitRuntimeRegistrations;
import dev.linqfy.bigCasares.module.runtime.RuntimeRegistrationScope;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.command.PluginCommand;
import org.bukkit.inventory.Recipe;
import org.bukkit.inventory.ShapelessRecipe;

import java.io.IOException;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.logging.Level;

/**
 * Netherite progression: ingots need gold blocks, and netherite armor is earned through timed quests that each
 * consume a boss soul (top three damage dealers of a boss get one) plus a growing XP level cost.
 */
public final class NetheriteQuestModule implements PluginModule {

    public static final String MODULE_ID = "netherite-quests";
    static final NamespacedKey VANILLA_INGOT_RECIPE = NamespacedKey.minecraft("netherite_ingot");

    private final BigCasares plugin;
    private RuntimeRegistrationScope compatibilityScope;
    private NetheriteQuestRuntime runtime;

    public NetheriteQuestModule(BigCasares plugin) {
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
        BukkitRuntimeRegistrations registrations = new BukkitRuntimeRegistrations(plugin, scope);
        NetheriteQuestSettings settings = NetheriteQuestSettings.load(plugin.getConfig());
        NetheriteQuestBook book = new NetheriteQuestBook(settings);
        NetheriteQuestStore store = new NetheriteQuestStore(
            plugin.getDataFolder().toPath().resolve("data").resolve("netherite-quests.yml"));
        try {
            store.loadInto(book);
        } catch (IOException failure) {
            // refuse to start rather than overwrite the players' progress with an empty file
            throw new IllegalStateException("No se pudieron cargar las misiones de netherita", failure);
        }
        NetheriteQuestRuntime created = new NetheriteQuestRuntime(plugin.getServer(), plugin.getLogger(), book,
            store, new BossSoulItem(new NamespacedKey(plugin, "boss_soul")), System::currentTimeMillis);

        registrations.ownCleanup("netherite-quests-state", () -> {
            created.save();
            if (runtime == created) {
                runtime = null;
            }
        });
        registrations.registerListener("netherite-quests-listener",
            new NetheriteQuestListener(created, settings.blockSmithingArmor(), settings.maxStepBlocks()));
        registrations.scheduleRepeating("netherite-quests-expiry", created::expireQuests, 20L * 30L, 20L * 30L);
        registrations.scheduleRepeating("netherite-quests-autosave", created::saveIfDirty, 20L * 60L, 20L * 60L);
        if (settings.goldBlockRecipe()) {
            swapIngotRecipe(registrations);
        }

        PluginCommand pluginCommand = plugin.getCommand("netherita");
        if (pluginCommand == null) {
            throw new IllegalStateException("Required command is not declared: netherita");
        }
        NetheriteQuestCommand command = new NetheriteQuestCommand(created);
        registrations.bindCommand("netherite-quests-command", pluginCommand, command, command);
        runtime = created;
        plugin.getServer().getOnlinePlayers().forEach(created::deliverPending);
    }

    /** Replaces the vanilla 4 scrap + 4 gold ingots recipe with 4 scrap + 4 gold blocks; restored on disable. */
    private void swapIngotRecipe(BukkitRuntimeRegistrations registrations) {
        Recipe vanilla = plugin.getServer().getRecipe(VANILLA_INGOT_RECIPE);
        if (vanilla != null && plugin.getServer().removeRecipe(VANILLA_INGOT_RECIPE)) {
            registrations.ownCleanup("netherite-ingot-vanilla-restore", () -> {
                if (plugin.getServer().getRecipe(VANILLA_INGOT_RECIPE) == null) {
                    plugin.getServer().addRecipe(vanilla);
                }
            });
        }
        NamespacedKey key = new NamespacedKey(plugin, "netherite_ingot_from_gold_blocks");
        ShapelessRecipe recipe = new ShapelessRecipe(key, new org.bukkit.inventory.ItemStack(Material.NETHERITE_INGOT));
        recipe.addIngredient(4, Material.NETHERITE_SCRAP);
        recipe.addIngredient(4, Material.GOLD_BLOCK);
        if (!registrations.registerRecipe("netherite-ingot-gold-blocks", key, recipe)) {
            plugin.getLogger().warning("[Netherita] No se pudo registrar la receta del lingote con bloques de oro.");
        }
    }

    @Override
    public void onDisable() {
        RuntimeRegistrationScope scope = compatibilityScope;
        compatibilityScope = null;
        if (scope != null) {
            scope.close();
        }
        runtime = null;
    }

    public boolean isEnabled() {
        return runtime != null;
    }

    /** Gives a soul of this boss to the three players that dealt it the most damage. */
    public void awardBossSouls(String bossId, String bossName, Map<UUID, Double> damageByPlayer) {
        if (damageByPlayer != null) {
            awardBossSouls(bossId, bossName, BossSoulRanking.topThree(Map.copyOf(damageByPlayer)));
        }
    }

    /**
     * Gives a soul of this boss to each of the top three damage dealers (in placement order). Bosses call this
     * when they die; offline players receive their soul when they log in.
     */
    public void awardBossSouls(String bossId, String bossName, List<UUID> topDamagers) {
        NetheriteQuestRuntime current = runtime;
        if (current == null || bossId == null || bossId.isBlank() || topDamagers == null) {
            return;
        }
        try {
            current.awardBossSouls(bossId, bossName == null || bossName.isBlank() ? bossId : bossName, topDamagers);
        } catch (RuntimeException failure) {
            plugin.getLogger().log(Level.SEVERE, "[Netherita] No se pudieron dar las almas de " + bossId, failure);
        }
    }
}
