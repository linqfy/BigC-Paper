package dev.linqfy.bigCasares.modules.alexscaves.core.mob;

import kr.toxicity.model.api.BetterModel;
import kr.toxicity.model.api.BetterModelPlatform;
import org.bukkit.plugin.Plugin;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.Collection;
import java.util.List;
import java.util.logging.Level;

/**
 * Copies the module's BetterModel sources into BetterModel's model folder and
 * reloads only when something changed or is missing (Don Pollos pattern).
 * Unlike the boss installer it never throws: a mob without its model still
 * works, so a BetterModel problem must not roll back every BigCasares module.
 */
public final class ModModelInstaller {
    private ModModelInstaller() {
    }

    /** Returns the model keys that are still missing after the install. */
    public static List<String> install(Plugin plugin, Collection<String> modelKeys) {
        if (modelKeys.isEmpty()) {
            return List.of();
        }
        try {
            Path models = BetterModel.platform().dataFolder().toPath().resolve("models");
            Files.createDirectories(models);
            boolean changed = false;
            for (String key : modelKeys) {
                String resource = "bettermodel/models/" + key + ".bbmodel";
                byte[] packaged;
                try (InputStream stream = plugin.getResource(resource)) {
                    if (stream == null) {
                        throw new IllegalStateException("Falta el modelo empaquetado: " + resource);
                    }
                    packaged = stream.readAllBytes();
                }
                Path target = models.resolve(key + ".bbmodel");
                if (!Files.isRegularFile(target) || !Arrays.equals(packaged, Files.readAllBytes(target))) {
                    Files.write(target, packaged);
                    changed = true;
                }
            }
            boolean missing = modelKeys.stream().anyMatch(key -> BetterModel.model(key).isEmpty());
            if (changed || missing) {
                BetterModelPlatform.ReloadResult result = BetterModel.platform().reload();
                if (result instanceof BetterModelPlatform.ReloadResult.Failure failure) {
                    plugin.getLogger().log(Level.WARNING, "[AlexsCaves] BetterModel no pudo recargar los modelos", failure.throwable());
                }
            }
        } catch (IOException | RuntimeException failure) {
            plugin.getLogger().log(Level.WARNING, "[AlexsCaves] No se pudieron instalar los modelos de los mobs", failure);
        }
        List<String> stillMissing = modelKeys.stream().filter(key -> {
            try {
                return BetterModel.model(key).isEmpty();
            } catch (RuntimeException failure) {
                return true;
            }
        }).toList();
        if (!stillMissing.isEmpty()) {
            plugin.getLogger().warning("[AlexsCaves] Modelos sin cargar (los mobs aparecen sin modelo): " + stillMissing);
        }
        return stillMissing;
    }
}
