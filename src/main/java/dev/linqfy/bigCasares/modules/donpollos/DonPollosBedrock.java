package dev.linqfy.bigCasares.modules.donpollos;

import org.bukkit.plugin.Plugin;
import org.bukkit.plugin.java.JavaPlugin;

import java.io.IOException;
import java.io.InputStream;
import java.lang.reflect.Method;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.UUID;
import java.util.logging.Logger;

/**
 * Soporte para jugadores de Bedrock que entran por Geyser + Floodgate.
 *
 * <ul>
 *   <li>Sabe quien es de Bedrock (con la API de Floodgate o de Geyser si estan; si no, por la UUID de Floodgate).</li>
 *   <li>Instala en Geyser el pack de Bedrock (iconos de los items y audios de Don Pollo) y el mapeo de items
 *       (generados con tools/generar_bedrock.py). Geyser los carga al reiniciar.</li>
 * </ul>
 */
final class DonPollosBedrock {

    static final String PACK_RESOURCE = "donpollos/bedrock/DonPollos-bedrock.mcpack";
    static final String MAPPINGS_RESOURCE = "donpollos/bedrock/donpollos-geyser.json";
    /** Los modelos convertidos para GeyserModelEngine (tools/generar_geysermodelengine.py). */
    static final String MODEL_ENGINE_RESOURCE = "donpollos/bedrock/geysermodelengine.zip";
    /** Plugin que dibuja los modelos de BetterModel para Bedrock. */
    static final String MODEL_ENGINE_PLUGIN = "GeyserModelEngine";
    private static final String[] GEYSER_PLUGINS = {"Geyser-Spigot", "Geyser-Paper", "Geyser"};

    private final Object floodgate;
    private final Method floodgateCheck;
    private final Object geyser;
    private final Method geyserCheck;

    DonPollosBedrock() {
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

    /** Ropa de aldeano de cada Don Pollo (para los de Bedrock, que ven el aldeano en vez del modelo). */
    static String villagerLook(DonPolloVariant variant) {
        return switch (variant) {
            case COMUN -> "plains";
            case GORDITO -> "desert";
            case SALSERO -> "jungle";
            case AURA_67 -> "savanna";
            case FINO -> "snow";
        };
    }

    /**
     * Si los de Bedrock ven los modelos 3D: "auto" (si esta GeyserModelEngine), "true" o "false".
     */
    static boolean bedrockModels(String setting, boolean modelEngineInstalled) {
        String value = setting == null ? "auto" : setting.trim().toLowerCase(java.util.Locale.ROOT);
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
     * Deja el pack y el mapeo en plugins/BigCasares/don-pollos/bedrock/ y, si Geyser esta en este servidor, en sus carpetas
     * packs/ y custom_mappings/. Devuelve true si cambio algo en Geyser (hay que reiniciar para que lo cargue).
     */
    boolean install(JavaPlugin plugin) {
        Logger logger = plugin.getLogger();
        byte[] pack = read(plugin, PACK_RESOURCE);
        byte[] mappings = read(plugin, MAPPINGS_RESOURCE);
        if (pack == null || mappings == null) {
            logger.warning("Faltan los archivos de Bedrock en el plugin (tools/generar_bedrock.py).");
            return false;
        }
        Path own = plugin.getDataFolder().toPath().resolve("don-pollos").resolve("bedrock");
        write(own.resolve("DonPollos-bedrock.mcpack"), pack, logger);
        write(own.resolve("donpollos-geyser.json"), mappings, logger);
        byte[] models = read(plugin, MODEL_ENGINE_RESOURCE);
        if (models != null) {
            write(own.resolve("geysermodelengine-modelos.zip"), models, logger);
        }
        Plugin geyserPlugin = null;
        for (String name : GEYSER_PLUGINS) {
            geyserPlugin = plugin.getServer().getPluginManager().getPlugin(name);
            if (geyserPlugin != null) {
                break;
            }
        }
        if (geyserPlugin == null) {
            logger.info("Geyser no esta en este servidor. Si lo usas en el proxy, copia los archivos de "
                + "plugins/BigCasares/don-pollos/bedrock/ a packs/ y custom_mappings/ de Geyser (y los modelos a "
                + "extensions/geysermodelengineextension/input/).");
            return false;
        }
        Path folder = geyserPlugin.getDataFolder().toPath();
        boolean changed = write(folder.resolve("packs").resolve("DonPollos-bedrock.mcpack"), pack, logger);
        changed |= write(folder.resolve("custom_mappings").resolve("donpollos.json"), mappings, logger);
        if (plugin.getServer().getPluginManager().getPlugin(MODEL_ENGINE_PLUGIN) != null) {
            changed |= installModels(plugin, folder.resolve("extensions").resolve("geysermodelengineextension")
                .resolve("input"), logger);
        }
        if (changed) {
            logger.warning("Se instalo el pack de Bedrock de Don Pollos en Geyser: reinicia el servidor para que lo cargue.");
        }
        return changed;
    }

    /** Descomprime los modelos de Don Pollos en la carpeta input de GeyserModelEngineExtension. */
    private static boolean installModels(JavaPlugin plugin, Path input, Logger logger) {
        byte[] zip = read(plugin, MODEL_ENGINE_RESOURCE);
        if (zip == null) {
            logger.warning("Faltan los modelos para GeyserModelEngine en el plugin (tools/generar_geysermodelengine.py).");
            return false;
        }
        boolean changed = false;
        try (java.util.zip.ZipInputStream entries = new java.util.zip.ZipInputStream(new java.io.ByteArrayInputStream(zip))) {
            for (java.util.zip.ZipEntry entry = entries.getNextEntry(); entry != null; entry = entries.getNextEntry()) {
                Path target = input.resolve(entry.getName()).normalize();
                if (entry.isDirectory() || !target.startsWith(input)) {
                    continue;
                }
                changed |= write(target, entries.readAllBytes(), logger);
            }
        } catch (IOException failed) {
            logger.warning("No se pudieron instalar los modelos en GeyserModelEngine: " + failed.getMessage());
        }
        if (changed) {
            logger.info("Modelos de Don Pollos instalados en GeyserModelEngine (" + input + ").");
        }
        return changed;
    }

    private static byte[] read(JavaPlugin plugin, String resource) {
        try (InputStream stream = plugin.getResource(resource)) {
            return stream == null ? null : stream.readAllBytes();
        } catch (IOException failed) {
            return null;
        }
    }

    /** Escribe solo si cambio. Devuelve true si escribio. */
    private static boolean write(Path target, byte[] content, Logger logger) {
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
