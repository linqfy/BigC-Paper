package dev.linqfy.bigCasares.modules.mythicdungeons;

import kr.toxicity.model.api.BetterModel;
import kr.toxicity.model.api.BetterModelPlatform;
import org.bukkit.plugin.java.JavaPlugin;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.logging.Logger;

/**
 * Pone en BetterModel los modelos {@code bigcasares_myth_*.bbmodel} que vienen en el jar (mobs, jefes y los extra que
 * aporten los encuentros) y recarga BetterModel solo si cambio algo o falta alguno que el jar si trae. A diferencia del
 * instalador del Mutango es tolerante: muchos modelos todavia no existen, asi que un modelo ausente no es un error y
 * cualquier falla solo deja un aviso (el modulo sigue con los mobs vanilla).
 */
final class MythicModelInstaller {

    /** Lo que hizo: que claves estan en el jar y cuales todavia no. */
    record Report(List<String> packaged, List<String> notPackaged, boolean reloaded) {
    }

    private MythicModelInstaller() {
    }

    /** Todas las claves que el modulo conoce: los mobs de las nueve culturas, los jefes que existen y las extra. */
    static List<String> knownKeys(MythicMobCatalog catalog, Collection<String> extraKeys) {
        Set<String> keys = new LinkedHashSet<>();
        catalog.all().forEach(type -> keys.add(type.modelKey()));
        for (MythicCulture culture : MythicCulture.bossCultures()) {
            keys.add(culture.requireBoss().modelKey());
        }
        keys.addAll(extraKeys);
        return List.copyOf(keys);
    }

    static Report install(JavaPlugin plugin, List<String> keys) {
        Logger logger = plugin.getLogger();
        List<String> packaged = new ArrayList<>();
        List<String> notPackaged = new ArrayList<>();
        boolean changed = false;
        Path models;
        try {
            models = BetterModel.platform().dataFolder().toPath().resolve("models");
            Files.createDirectories(models);
        } catch (IOException | RuntimeException | LinkageError unavailable) {
            logger.warning("[Mazmorras] No se pudo abrir la carpeta de modelos de BetterModel: " + unavailable);
            return new Report(List.of(), keys, false);
        }
        for (String key : keys) {
            String resource = "bettermodel/models/" + key + ".bbmodel";
            try (InputStream stream = plugin.getResource(resource)) {
                if (stream == null) {
                    notPackaged.add(key);
                    continue;
                }
                byte[] bytes = stream.readAllBytes();
                packaged.add(key);
                Path target = models.resolve(key + ".bbmodel");
                if (!Files.isRegularFile(target) || !Arrays.equals(bytes, Files.readAllBytes(target))) {
                    Files.write(target, bytes);
                    changed = true;
                }
            } catch (IOException | RuntimeException failure) {
                logger.warning("[Mazmorras] No se pudo instalar el modelo " + key + ": " + failure);
            }
        }
        boolean reloaded = false;
        try {
            boolean missing = packaged.stream().anyMatch(key -> BetterModel.model(key).isEmpty());
            if (changed || missing) {
                logger.info("[Mazmorras] Instalando " + packaged.size() + " modelos en BetterModel y recargando...");
                BetterModelPlatform.ReloadResult result = BetterModel.platform().reload();
                if (result instanceof BetterModelPlatform.ReloadResult.Failure failure) {
                    logger.warning("[Mazmorras] BetterModel no pudo recargar los modelos: " + failure.throwable());
                } else {
                    reloaded = true;
                }
            }
        } catch (RuntimeException | LinkageError failure) {
            logger.warning("[Mazmorras] No se pudo recargar BetterModel: " + failure);
        }
        if (!notPackaged.isEmpty()) {
            logger.info("[Mazmorras] " + notPackaged.size() + " modelos todavia no existen; esos mobs y jefes se ven"
                + " como su entidad vanilla.");
        }
        return new Report(List.copyOf(packaged), List.copyOf(notPackaged), reloaded);
    }
}
