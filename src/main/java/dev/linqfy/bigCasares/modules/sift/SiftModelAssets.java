package dev.linqfy.bigCasares.modules.sift;

import kr.toxicity.model.api.BetterModel;
import kr.toxicity.model.api.BetterModelPlatform;
import org.bukkit.plugin.java.JavaPlugin;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.List;
import java.util.logging.Level;

/**
 * Installs the Sift creature models (converted from the mod's GeckoLib models by
 * {@code tools/convert_geckolib_bbmodel.py}) into BetterModel's model folder and
 * reloads BetterModel only when a file changed.
 */
final class SiftModelAssets {

    static final List<String> MODELS = List.of("the_sift_blub.bbmodel");

    private SiftModelAssets() {
    }

    static void install(JavaPlugin plugin) {
        try {
            Path models = BetterModel.platform().dataFolder().toPath().resolve("models");
            Files.createDirectories(models);
            boolean changed = false;
            for (String file : MODELS) {
                byte[] packaged;
                try (InputStream stream = plugin.getResource("bettermodel/models/" + file)) {
                    if (stream == null) {
                        plugin.getLogger().warning("[TheSift] Packaged model missing: " + file);
                        continue;
                    }
                    packaged = stream.readAllBytes();
                }
                Path target = models.resolve(file);
                if (!Files.isRegularFile(target) || !Arrays.equals(packaged, Files.readAllBytes(target))) {
                    Files.write(target, packaged);
                    changed = true;
                }
            }
            if (changed) {
                BetterModelPlatform.ReloadResult result = BetterModel.platform().reload();
                if (result instanceof BetterModelPlatform.ReloadResult.Failure failure) {
                    plugin.getLogger().warning("[TheSift] BetterModel reload failed after installing models: " + failure);
                }
            }
        } catch (IOException | RuntimeException failure) {
            plugin.getLogger().log(Level.WARNING, "[TheSift] Could not install creature models", failure);
        }
    }
}
