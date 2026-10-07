package dev.linqfy.bigCasares.modules.donpollos;

import kr.toxicity.model.api.BetterModel;
import kr.toxicity.model.api.BetterModelPlatform;
import org.bukkit.plugin.java.JavaPlugin;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.List;

final class DonPollosModelInstaller {

    static final List<String> MODELS = List.of(
        "bigcasares_don_pollo_comun",
        "bigcasares_don_pollo_gordito",
        "bigcasares_don_pollo_salsero",
        "bigcasares_don_pollo_aura_67",
        "bigcasares_don_pollo_fino"
    );

    /** Modelos sueltos (sin Don Pollo detras): la gallina WhatsApp, el logo de WhatsApp, los 4 Labubus y el Coronel. */
    static final List<String> EXTRA_MODELS = List.of(
        "bigcasares_gallina_wsp",
        "bigcasares_logo_wsp",
        "bigcasares_labubu_verde",
        "bigcasares_labubu_rojo",
        "bigcasares_labubu_rosa",
        "bigcasares_labubu_azul",
        "bigcasares_coronel"
    );

    static List<String> allModels() {
        List<String> all = new java.util.ArrayList<>(MODELS);
        all.add(DonPollosBossManager.MODEL);
        all.addAll(EXTRA_MODELS);
        return all;
    }

    private DonPollosModelInstaller() {
    }

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
            throw new IllegalStateException("No se pudieron instalar los modelos de Don Pollos", exception);
        }
        boolean missing = allModels().stream().anyMatch(model -> BetterModel.model(model).isEmpty());
        if (changed || missing) {
            plugin.getLogger().info("Instalando modelos de Don Pollos en BetterModel y recargando...");
            BetterModelPlatform.ReloadResult result = BetterModel.platform().reload();
            if (result instanceof BetterModelPlatform.ReloadResult.Failure failure) {
                throw new IllegalStateException("BetterModel no pudo recargar los modelos", failure.throwable());
            }
        }
    }
}
