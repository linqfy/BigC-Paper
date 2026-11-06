package dev.linqfy.bigCasares.modules.mutango;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import org.bukkit.plugin.Plugin;
import org.bukkit.plugin.java.JavaPlugin;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.lang.reflect.Method;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.Locale;
import java.util.UUID;
import java.util.logging.Logger;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

/**
 * Soporte para jugadores de Bedrock que entran por Geyser + Floodgate (como DonPollosBedrock en BigC).
 *
 * <ul>
 *   <li>Sabe quien es de Bedrock (con la API de Floodgate o de Geyser si estan; si no, por la UUID de Floodgate).</li>
 *   <li>Bedrock no dibuja los modelos de BetterModel: sin GeyserModelEngine a los de Bedrock no se les manda el modelo,
 *       asi BetterModel tampoco les esconde la vaca de abajo y ven vacas en vez de nada.</li>
 *   <li>Con GeyserModelEngine instala los modelos convertidos (mutango/bedrock/geysermodelengine.zip) en la
 *       carpeta input de GeyserModelEngineExtension. Geyser los carga al reiniciar.</li>
 * </ul>
 */
final class MutangoBedrock {

    /** Los modelos convertidos para GeyserModelEngine. */
    static final String MODEL_ENGINE_RESOURCE = "mutango/bedrock/geysermodelengine.zip";
    /** Plugin que dibuja los modelos de BetterModel para Bedrock. */
    static final String MODEL_ENGINE_PLUGIN = "GeyserModelEngine";
    /** Opcion de config.yml: auto (si esta GeyserModelEngine), true o false. */
    static final String MODELS_SETTING = "bedrock.modelos-3d";
    static final String[] GEYSER_PLUGINS = {"Geyser-Spigot", "Geyser-Paper", "Geyser"};

    private final Object floodgate;
    private final Method floodgateCheck;
    private final Object geyser;
    private final Method geyserCheck;

    MutangoBedrock() {
        Object floodgateApi = null;
        Method floodgateMethod = null;
        try {
            Class<?> api = Class.forName("org.geysermc.floodgate.api.FloodgateApi");
            floodgateApi = api.getMethod("getInstance").invoke(null);
            floodgateMethod = api.getMethod("isFloodgatePlayer", UUID.class);
        } catch (ReflectiveOperationException | LinkageError notInstalled) {
            // sin Floodgate
        }
        Object geyserApi = null;
        Method geyserMethod = null;
        try {
            Class<?> api = Class.forName("org.geysermc.geyser.api.GeyserApi");
            geyserApi = api.getMethod("api").invoke(null);
            geyserMethod = api.getMethod("isBedrockPlayer", UUID.class);
        } catch (ReflectiveOperationException | LinkageError notInstalled) {
            // sin Geyser en este servidor (puede estar en el proxy)
        }
        this.floodgate = floodgateApi;
        this.floodgateCheck = floodgateMethod;
        this.geyser = geyserApi;
        this.geyserCheck = geyserMethod;
    }

    /** Si los de Bedrock ven los modelos 3D: "auto" (si esta GeyserModelEngine), "true" o "false". */
    static boolean bedrockModels(String setting, boolean modelEngineInstalled) {
        String value = setting == null ? "auto" : setting.trim().toLowerCase(Locale.ROOT);
        return switch (value) {
            case "true", "si", "yes" -> true;
            case "false", "no" -> false;
            default -> modelEngineInstalled;
        };
    }

    /** Las UUID que inventa Floodgate para los de Bedrock tienen la primera mitad en cero (00000000-0000-0000-...). */
    static boolean looksLikeFloodgate(UUID id) {
        return id.getMostSignificantBits() == 0 && id.getLeastSignificantBits() != 0;
    }

    /**
     * A quien se le manda el modelo 3D: a los de Java siempre; a los de Bedrock solo si tienen GeyserModelEngine
     * (si no, sin modelo ven la vaca de abajo).
     */
    static boolean showModel(boolean bedrockPlayer, boolean bedrockModels) {
        return !bedrockPlayer || bedrockModels;
    }

    boolean isBedrock(UUID id) {
        if (ask(floodgate, floodgateCheck, id) || ask(geyser, geyserCheck, id)) {
            return true;
        }
        return looksLikeFloodgate(id);
    }

    private static boolean ask(Object api, Method check, UUID id) {
        if (api == null || check == null) {
            return false;
        }
        try {
            return Boolean.TRUE.equals(check.invoke(api, id));
        } catch (ReflectiveOperationException | RuntimeException failed) {
            return false;
        }
    }

    /**
     * Deja los modelos convertidos en plugins/BigCasares/mutango/bedrock/ y, si Geyser y GeyserModelEngine estan en este servidor,
     * los descomprime en extensions/geysermodelengineextension/input/ de Geyser. Devuelve true si cambio algo en
     * Geyser (hay que reiniciar para que lo cargue).
     */
    boolean install(JavaPlugin plugin) {
        Logger logger = plugin.getLogger();
        byte[] models = read(plugin, MODEL_ENGINE_RESOURCE);
        if (models == null) {
            logger.warning("Faltan los modelos de Bedrock en el plugin (mutango/bedrock/geysermodelengine.zip).");
            return false;
        }
        write(plugin.getDataFolder().toPath().resolve("mutango").resolve("bedrock")
            .resolve("geysermodelengine-modelos.zip"), models, logger);
        Plugin geyserPlugin = null;
        for (String name : GEYSER_PLUGINS) {
            geyserPlugin = plugin.getServer().getPluginManager().getPlugin(name);
            if (geyserPlugin != null) {
                break;
            }
        }
        if (geyserPlugin == null || plugin.getServer().getPluginManager().getPlugin(MODEL_ENGINE_PLUGIN) == null) {
            return false;
        }
        Path input = geyserPlugin.getDataFolder().toPath().resolve("extensions")
            .resolve("geysermodelengineextension").resolve("input");
        boolean changed = unzip(models, input, logger);
        if (changed) {
            logger.warning("Se instalaron los modelos del Mutango en GeyserModelEngine: reinicia el servidor para que "
                + "Geyser los cargue.");
        }
        return changed;
    }

    /** Descomprime zip en folder (solo lo que cambio y nunca afuera de folder). Devuelve true si escribio algo. */
    static boolean unzip(byte[] zip, Path folder, Logger logger) {
        boolean changed = false;
        Path root = folder.toAbsolutePath().normalize();
        try (ZipInputStream entries = new ZipInputStream(new ByteArrayInputStream(zip))) {
            for (ZipEntry entry = entries.getNextEntry(); entry != null; entry = entries.getNextEntry()) {
                Path target = root.resolve(entry.getName()).normalize();
                if (entry.isDirectory() || !target.startsWith(root)) {
                    continue;
                }
                byte[] wanted = entries.readAllBytes();
                if (entry.getName().endsWith("config.json") && Files.isRegularFile(target)
                    && configCovers(Files.readAllBytes(target), wanted)) {
                    continue; // la extension le agrega cosas al cargarlo: si ya tiene lo nuestro, no se pisa
                }
                changed |= write(target, wanted, logger);
            }
        } catch (IOException failed) {
            logger.warning("No se pudieron instalar los modelos en GeyserModelEngine: " + failed.getMessage());
        }
        return changed;
    }

    /**
     * El config.json que ya esta tiene todo lo que queremos poner (la extension de GeyserModelEngine lo modifica al
     * cargarlo y, si se lo pisara en cada arranque, se avisaria de un reinicio que no hace falta). Un archivo que no se
     * entiende como JSON no cubre nada.
     */
    static boolean configCovers(byte[] existing, byte[] wanted) {
        try {
            JsonObject have = JsonParser
                .parseString(new String(existing, StandardCharsets.UTF_8)).getAsJsonObject();
            JsonObject want = JsonParser
                .parseString(new String(wanted, StandardCharsets.UTF_8)).getAsJsonObject();
            for (var entry : want.entrySet()) {
                JsonElement current = have.get(entry.getKey());
                if (current == null) {
                    return false;
                }
                // un {} o [] nuestro es "sin nada": la extension lo llena (por ejemplo binding_bones), no se pisa
                if (!isEmpty(entry.getValue()) && !entry.getValue().equals(current)) {
                    return false;
                }
            }
            return true;
        } catch (RuntimeException invalid) {
            return false;
        }
    }

    private static boolean isEmpty(JsonElement value) {
        return value.isJsonObject() && value.getAsJsonObject().isEmpty()
            || value.isJsonArray() && value.getAsJsonArray().isEmpty();
    }

    private static byte[] read(JavaPlugin plugin, String resource) {
        try (InputStream stream = plugin.getResource(resource)) {
            return stream == null ? null : stream.readAllBytes();
        } catch (IOException failed) {
            return null;
        }
    }

    /** Escribe solo si cambio. Devuelve true si escribio. */
    static boolean write(Path target, byte[] content, Logger logger) {
        try {
            if (Files.isRegularFile(target) && Arrays.equals(Files.readAllBytes(target), content)) {
                return false;
            }
            Files.createDirectories(target.getParent());
            Files.write(target, content);
            return true;
        } catch (IOException failed) {
            logger.warning("No se pudo escribir " + target + ": " + failed.getMessage());
            return false;
        }
    }
}
