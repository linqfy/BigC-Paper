package dev.linqfy.bigCasares.modules.mutango;

import kr.toxicity.model.api.BetterModel;
import kr.toxicity.model.api.BetterModelPlatform;
import org.bukkit.plugin.java.JavaPlugin;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/** Pone los modelos (.bbmodel) del jar en BetterModel y lo recarga si hace falta. */
final class MutangoModelInstaller {

    private MutangoModelInstaller() {
    }

    /** Copia los .bbmodel del jar a plugins/BetterModel/models y recarga BetterModel si cambio algo o falta alguno. */
    static void install(JavaPlugin plugin) {
        Path models = BetterModel.platform().dataFolder().toPath().resolve("models");
        boolean changed = false;
        try {
            Files.createDirectories(models);
            for (String model : allModels()) {
                String resource = "bettermodel/models/" + model + ".bbmodel";
                byte[] packaged;
                try (InputStream stream = plugin.getResource(resource)) {
                    if (stream == null) {
                        throw new IllegalStateException("Falta el modelo empaquetado: " + resource);
                    }
                    packaged = stream.readAllBytes();
                }
                Path target = models.resolve(model + ".bbmodel");
                if (!Files.isRegularFile(target) || !Arrays.equals(packaged, Files.readAllBytes(target))) {
                    Files.write(target, packaged);
                    changed = true;
                }
            }
        } catch (IOException exception) {
            throw new IllegalStateException("No se pudieron instalar los modelos del Mutango", exception);
        }
        boolean missing = allModels().stream().anyMatch(model -> BetterModel.model(model).isEmpty());
        if (changed || missing) {
            plugin.getLogger().info("Instalando modelos del Mutango en BetterModel y recargando...");
            BetterModelPlatform.ReloadResult result = BetterModel.platform().reload();
            if (result instanceof BetterModelPlatform.ReloadResult.Failure failure) {
                throw new IllegalStateException("BetterModel no pudo recargar los modelos", failure.throwable());
            }
        }
    }

    static List<String> allModels() {
        List<String> all = new ArrayList<>(MutangoModels.MODELS);
        all.addAll(MutangoPhaseThree.MODELS);
        all.addAll(MutangoShrine.STATUES);
        all.add(MutangoShrine.CUBE);
        all.addAll(MutangoArmor.armoredModels());
        return all;
    }
}
