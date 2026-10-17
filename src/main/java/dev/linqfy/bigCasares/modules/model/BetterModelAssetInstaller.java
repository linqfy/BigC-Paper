package dev.linqfy.bigCasares.modules.model;

import kr.toxicity.model.api.BetterModel;
import kr.toxicity.model.api.BetterModelPlatform;
import org.bukkit.plugin.java.JavaPlugin;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

public final class BetterModelAssetInstaller {

    private static final List<String> SAHUR_ASSETS = List.of(
        "boss_sahur.bbmodel",
        "bat_boss.bbmodel",
        "sakur.png"
    );

    private static final List<String> SAHUR_MODEL_KEYS = List.of("boss_sahur", "bat_boss");

    private static final List<String> BALLESTA_ASSETS = List.of(
        "ballesta_arkbien_animado.bbmodel"
    );

    private static final List<String> BALLESTA_MODEL_KEYS = List.of("ballesta_arkbien_animado");

    private static final List<String> TOXIC_MOB_ASSETS = List.of(
        "toxic_mob.bbmodel",
        "toxic_mob.png",
        "toxic_brute.bbmodel",
        "toxic_brute.png",
        "toxic_spitter.bbmodel",
        "toxic_spitter.png"
    );

    private static final List<String> TOXIC_MOB_MODEL_KEYS = List.of(
        "toxic_mob", "toxic_brute", "toxic_spitter"
    );

    private static final List<String> SIFT_ASSETS = List.of(
        "the_sift_blub.bbmodel",
        "the_sift_blub_tamed.bbmodel",
        "the_sift_singer.bbmodel",
        "the_sift_rift.bbmodel",
        "the_sift_rift_overworld.bbmodel"
    );

    private static final List<String> SIFT_MODEL_KEYS = List.of("the_sift_blub", "the_sift_blub_tamed", "the_sift_singer",
        "the_sift_rift", "the_sift_rift_overworld");

    private BetterModelAssetInstaller() { }

    public static void installSahurModels(JavaPlugin plugin) {
        install(plugin, SAHUR_ASSETS, SAHUR_MODEL_KEYS, "Sahur");
    }

    public static void installBallestaModel(JavaPlugin plugin) {
        install(plugin, BALLESTA_ASSETS, BALLESTA_MODEL_KEYS, "Ballesta Ark");
    }

    /**
     * Installs the three independent BetterModel visuals used by the Acid
     * Rain crawler, brute and spitter families.
     */
    public static void installToxicMobModel(JavaPlugin plugin) {
        install(plugin, TOXIC_MOB_ASSETS, TOXIC_MOB_MODEL_KEYS, "Toxic Mobs");
    }

    /**
     * Installs The Sift creature models converted from Mielon's The Sift by
     * {@code tools/convert_geckolib_bbmodel.py}.
     */
    public static void installSiftModels(JavaPlugin plugin) {
        install(plugin, SIFT_ASSETS, SIFT_MODEL_KEYS, "The Sift");
    }

    private static void install(JavaPlugin plugin, List<String> assetNames, List<String> modelKeys, String label) {
        Path models = BetterModel.platform().dataFolder().toPath().resolve("models");
        try {
            Files.createDirectories(models);
            for (String fileName : assetNames) {
                String resource = "bettermodel/models/" + fileName;
                byte[] packaged;
                try (InputStream stream = plugin.getResource(resource)) {
                    if (stream == null) throw new IllegalStateException("Missing packaged BetterModel asset: " + resource);
                    packaged = stream.readAllBytes();
                }
                Path target = models.resolve(fileName);
                if (!Files.isRegularFile(target) || !java.util.Arrays.equals(packaged, Files.readAllBytes(target))) {
                    Files.write(target, packaged);
                }
            }
        } catch (IOException exception) {
            throw new IllegalStateException("Could not install " + label + " BetterModel assets", exception);
        }
        BetterModelPlatform.ReloadResult result = BetterModel.platform().reload();
        if (result instanceof BetterModelPlatform.ReloadResult.Failure failure) {
            throw new IllegalStateException("BetterModel could not reload " + label + " assets", failure.throwable());
        }
        for (String modelKey : modelKeys) {
            if (BetterModel.model(modelKey).isEmpty()) {
                throw new IllegalStateException("BetterModel did not load " + modelKey);
            }
        }
    }
}
