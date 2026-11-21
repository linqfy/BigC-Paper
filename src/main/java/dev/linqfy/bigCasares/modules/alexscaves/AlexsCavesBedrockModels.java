package dev.linqfy.bigCasares.modules.alexscaves;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import org.bukkit.plugin.Plugin;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Base64;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.jar.JarFile;
import java.util.logging.Logger;
import java.util.stream.Stream;

/**
 * Gives Bedrock players the module's mobs through GeyserModelEngine: the BetterModel sources
 * ({@code bettermodel/models/alexscaves_*.bbmodel}) are converted to the extension's input format (Bedrock
 * geometry, animations, texture and config, like Blockbench's "Export GeyserModelEngine Model") and copied to
 * {@code plugins/Geyser-Spigot/extensions/geysermodelengineextension/input/}. Without GeyserModelEngine Bedrock
 * players keep seeing the vanilla body of each mob. Geyser reads the folder at startup.
 */
final class AlexsCavesBedrockModels {
    static final String MODEL_ENGINE_PLUGIN = "GeyserModelEngine";
    static final String PREFIX = "alexscaves_";
    private static final String RESOURCES = "bettermodel/models/";
    private static final String[] GEYSER_PLUGINS = {"Geyser-Spigot", "Geyser-Paper", "Geyser"};
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().disableHtmlEscaping().create();

    private AlexsCavesBedrockModels() {
    }

    /** Converts and copies every packaged model. Returns true when something changed (Geyser needs a restart). */
    static boolean install(Plugin plugin) {
        Path input = inputFolder(plugin);
        if (input == null || plugin.getServer().getPluginManager().getPlugin(MODEL_ENGINE_PLUGIN) == null) {
            return false;
        }
        Logger logger = plugin.getLogger();
        boolean changed = false;
        int models = 0;
        for (String key : packagedModels(plugin)) {
            try (InputStream stream = plugin.getResource(RESOURCES + key + ".bbmodel")) {
                if (stream == null) {
                    continue;
                }
                Map<String, byte[]> files = convert(new String(stream.readAllBytes(), StandardCharsets.UTF_8));
                for (Map.Entry<String, byte[]> file : files.entrySet()) {
                    Path target = input.resolve(file.getKey()).normalize();
                    if (!target.startsWith(input) || keepsExisting(file.getKey()) && Files.isRegularFile(target)) {
                        continue;
                    }
                    changed |= write(target, file.getValue());
                }
                models++;
            } catch (IOException | RuntimeException failure) {
                logger.warning("[AlexsCaves] No se pudo convertir el modelo " + key + " para Bedrock: " + failure);
            }
        }
        if (changed) {
            logger.warning("[AlexsCaves] " + models + " modelos instalados en GeyserModelEngine: reiniciá el servidor "
                + "para que Bedrock los vea.");
        }
        return changed;
    }

    /** Removes the converted models (module off), so they stop being added to Geyser's pack. */
    static void uninstall(Plugin plugin) {
        Path input = inputFolder(plugin);
        if (input == null || !Files.isDirectory(input)) {
            return;
        }
        try (Stream<Path> folders = Files.list(input)) {
            for (Path folder : folders.filter(path -> path.getFileName().toString().startsWith(PREFIX)).toList()) {
                try (Stream<Path> files = Files.walk(folder)) {
                    for (Path file : files.sorted(Comparator.reverseOrder()).toList()) {
                        Files.deleteIfExists(file);
                    }
                }
            }
        } catch (IOException failure) {
            plugin.getLogger().warning("[AlexsCaves] No se pudieron quitar los modelos de Bedrock: " + failure.getMessage());
        }
    }

    /** GeyserModelEngineExtension rewrites each model's config.json: only the first write is ours. */
    static boolean keepsExisting(String name) {
        return name.endsWith("/config.json");
    }

    private static Path inputFolder(Plugin plugin) {
        for (String name : GEYSER_PLUGINS) {
            Plugin geyser = plugin.getServer().getPluginManager().getPlugin(name);
            if (geyser != null) {
                return geyser.getDataFolder().toPath().resolve("extensions").resolve("geysermodelengineextension")
                    .resolve("input").toAbsolutePath().normalize();
            }
        }
        return null;
    }

    /** Keys of the models inside the plugin JAR. */
    private static List<String> packagedModels(Plugin plugin) {
        List<String> keys = new ArrayList<>();
        try {
            Path jar = Path.of(plugin.getClass().getProtectionDomain().getCodeSource().getLocation().toURI());
            if (Files.isRegularFile(jar)) {
                try (JarFile file = new JarFile(jar.toFile())) {
                    file.stream().map(entry -> entry.getName())
                        .filter(name -> name.startsWith(RESOURCES + PREFIX) && name.endsWith(".bbmodel"))
                        .forEach(name -> keys.add(name.substring(RESOURCES.length(), name.length() - ".bbmodel".length())));
                }
            }
        } catch (Exception failure) {
            plugin.getLogger().warning("[AlexsCaves] No se pudo listar los modelos del JAR: " + failure.getMessage());
        }
        keys.sort(String::compareTo);
        return keys;
    }

    private static boolean write(Path target, byte[] content) throws IOException {
        if (Files.isRegularFile(target) && Arrays.equals(Files.readAllBytes(target), content)) {
            return false;
        }
        Files.createDirectories(target.getParent());
        Files.write(target, content);
        return true;
    }

    // ---- bbmodel -> GeyserModelEngine input (same conversion as tools/donpollos/generar_geysermodelengine.py) ----

    /** Files of one model by path relative to the input folder: {@code <name>/<name>.geo.json}, animation, texture, config. */
    static Map<String, byte[]> convert(String bbmodel) {
        JsonObject data = GSON.fromJson(bbmodel, JsonObject.class);
        String name = data.get("name").getAsString();
        JsonObject texture = data.getAsJsonArray("textures").get(0).getAsJsonObject();
        JsonObject resolution = data.getAsJsonObject("resolution");
        double texWidth = number(texture, "uv_width", number(texture, "width", resolution.get("width").getAsDouble()));
        double texHeight = number(texture, "uv_height", number(texture, "height", resolution.get("height").getAsDouble()));
        String source = texture.get("source").getAsString();
        byte[] png = Base64.getDecoder().decode(source.substring(source.indexOf(',') + 1));
        String textureName = texture.has("name") && !texture.get("name").isJsonNull()
            ? stem(texture.get("name").getAsString()) : name;

        Map<String, JsonObject> groups = new HashMap<>();
        for (JsonElement group : data.getAsJsonArray("groups")) {
            groups.put(group.getAsJsonObject().get("uuid").getAsString(), group.getAsJsonObject());
        }
        Map<String, JsonObject> elements = new HashMap<>();
        for (JsonElement element : data.getAsJsonArray("elements")) {
            elements.put(element.getAsJsonObject().get("uuid").getAsString(), element.getAsJsonObject());
        }
        JsonArray bones = new JsonArray();
        double[] low = {1e9, 1e9, 1e9};
        double[] high = {-1e9, -1e9, -1e9};
        for (JsonElement root : data.getAsJsonArray("outliner")) {
            walk(root.getAsJsonObject(), null, groups, elements, bones, low, high);
        }
        double width = Math.max(Math.max(Math.abs(low[0]), Math.abs(high[0])), Math.max(Math.abs(low[2]), Math.abs(high[2])))
            * 2 / 16 + 1;
        double height = Math.max(high[1], 1) / 16 + 1;

        JsonObject description = new JsonObject();
        description.addProperty("identifier", "geometry." + name);
        description.addProperty("texture_width", num(texWidth));
        description.addProperty("texture_height", num(texHeight));
        description.addProperty("visible_bounds_width", num(width));
        description.addProperty("visible_bounds_height", num(height));
        description.add("visible_bounds_offset", array(0, num(height / 2), 0));
        JsonObject geometry = new JsonObject();
        geometry.add("description", description);
        geometry.add("bones", bones);
        JsonObject geoFile = new JsonObject();
        geoFile.addProperty("format_version", "1.12.0");
        JsonArray geometries = new JsonArray();
        geometries.add(geometry);
        geoFile.add("minecraft:geometry", geometries);

        JsonObject animations = new JsonObject();
        JsonElement animationList = data.get("animations");
        if (animationList != null && animationList.isJsonArray()) {
            for (JsonElement animation : animationList.getAsJsonArray()) {
                animations.add(animation.getAsJsonObject().get("name").getAsString(), animation(animation.getAsJsonObject()));
            }
        }
        JsonObject animationFile = new JsonObject();
        animationFile.addProperty("format_version", "1.8.0");
        animationFile.add("animations", animations);

        JsonObject config = new JsonObject();
        config.addProperty("head_rotation", true);
        config.addProperty("material", "entity_alphatest_change_color_one_sided");
        config.addProperty("blend_transition", true);
        JsonObject perTexture = new JsonObject();
        perTexture.add(textureName, array(num(texWidth), num(texHeight)));
        config.add("per_texture_uv_size", perTexture);
        config.add("binding_bones", new JsonObject());
        config.add("anim_textures", new JsonObject());

        Map<String, byte[]> files = new LinkedHashMap<>();
        files.put(name + "/" + name + ".geo.json", json(geoFile));
        files.put(name + "/config.json", json(config));
        files.put(name + "/" + textureName + ".png", png);
        files.put(name + "/" + name + ".animation.json", json(animationFile));
        return files;
    }

    private static void walk(JsonObject node, String parent, Map<String, JsonObject> groups,
                             Map<String, JsonObject> elements, JsonArray bones, double[] low, double[] high) {
        JsonObject group = groups.get(node.get("uuid").getAsString());
        double[] origin = vector(group.getAsJsonArray("origin"));
        JsonObject bone = new JsonObject();
        String groupName = group.get("name").getAsString();
        bone.addProperty("name", groupName);
        bone.add("pivot", array(num(-origin[0]), num(origin[1]), num(origin[2])));
        if (parent != null) {
            bone.addProperty("parent", parent);
        }
        if (group.has("rotation")) {
            double[] rotation = vector(group.getAsJsonArray("rotation"));
            if (rotation[0] != 0 || rotation[1] != 0 || rotation[2] != 0) {
                bone.add("rotation", array(num(-rotation[0]), num(-rotation[1]), num(rotation[2])));
            }
        }
        JsonArray cubes = new JsonArray();
        JsonArray children = node.getAsJsonArray("children");
        for (JsonElement child : children) {
            if (!child.isJsonPrimitive()) {
                continue;
            }
            JsonObject element = elements.get(child.getAsString());
            double[] from = vector(element.getAsJsonArray("from"));
            double[] to = vector(element.getAsJsonArray("to"));
            JsonObject uv = new JsonObject();
            for (Map.Entry<String, JsonElement> face : element.getAsJsonObject("faces").entrySet()) {
                double[] box = vector(face.getValue().getAsJsonObject().getAsJsonArray("uv"));
                JsonObject entry = new JsonObject();
                if (face.getKey().equals("up") || face.getKey().equals("down")) {
                    entry.add("uv", array(num(box[2]), num(box[3])));
                    entry.add("uv_size", array(num(box[0] - box[2]), num(box[1] - box[3])));
                } else {
                    entry.add("uv", array(num(box[0]), num(box[1])));
                    entry.add("uv_size", array(num(box[2] - box[0]), num(box[3] - box[1])));
                }
                uv.add(face.getKey(), entry);
            }
            JsonObject cube = new JsonObject();
            cube.add("origin", array(num(-to[0]), num(from[1]), num(from[2])));
            cube.add("size", array(num(to[0] - from[0]), num(to[1] - from[1]), num(to[2] - from[2])));
            if (element.has("inflate") && element.get("inflate").getAsDouble() != 0) {
                cube.addProperty("inflate", num(element.get("inflate").getAsDouble()));
            }
            cube.add("uv", uv);
            cubes.add(cube);
            for (int i = 0; i < 3; i++) {
                low[i] = Math.min(low[i], from[i]);
                high[i] = Math.max(high[i], to[i]);
            }
        }
        if (!cubes.isEmpty()) {
            bone.add("cubes", cubes);
        }
        bones.add(bone);
        for (JsonElement child : children) {
            if (child.isJsonObject()) {
                walk(child.getAsJsonObject(), groupName, groups, elements, bones, low, high);
            }
        }
    }

    private static JsonObject animation(JsonObject animation) {
        JsonObject bones = new JsonObject();
        JsonObject animators = animation.has("animators") ? animation.getAsJsonObject("animators") : new JsonObject();
        for (JsonElement animatorElement : animators.asMap().values()) {
            JsonObject animator = animatorElement.getAsJsonObject();
            Map<String, JsonObject> channels = new LinkedHashMap<>();
            JsonElement keyframes = animator.get("keyframes");
            if (keyframes != null) {
                for (JsonElement keyframeElement : keyframes.getAsJsonArray()) {
                    JsonObject keyframe = keyframeElement.getAsJsonObject();
                    JsonObject point = keyframe.getAsJsonArray("data_points").get(0).getAsJsonObject();
                    JsonElement value = array(num(point.get("x").getAsDouble()), num(point.get("y").getAsDouble()),
                        num(point.get("z").getAsDouble()));
                    if (keyframe.has("interpolation") && "catmullrom".equals(keyframe.get("interpolation").getAsString())) {
                        JsonObject smooth = new JsonObject();
                        smooth.add("post", value);
                        smooth.addProperty("lerp_mode", "catmullrom");
                        value = smooth;
                    }
                    channels.computeIfAbsent(keyframe.get("channel").getAsString(), key -> new JsonObject())
                        .add(String.valueOf(num(keyframe.get("time").getAsDouble())), value);
                }
            }
            if (!channels.isEmpty()) {
                JsonObject channelObject = new JsonObject();
                channels.forEach(channelObject::add);
                bones.add(animator.get("name").getAsString(), channelObject);
            }
        }
        JsonObject entry = new JsonObject();
        entry.addProperty("animation_length", num(animation.get("length").getAsDouble()));
        entry.add("bones", bones);
        if (animation.has("loop") && "loop".equals(animation.get("loop").getAsString())) {
            entry.addProperty("loop", true);
        }
        return entry;
    }

    private static double[] vector(JsonArray array) {
        double[] values = new double[array.size()];
        for (int i = 0; i < values.length; i++) {
            values[i] = array.get(i).getAsDouble();
        }
        return values;
    }

    private static double number(JsonObject object, String key, double fallback) {
        return object.has(key) && !object.get(key).isJsonNull() ? object.get(key).getAsDouble() : fallback;
    }

    private static Number num(double value) {
        double rounded = Math.round(value * 10000.0) / 10000.0;
        if (rounded == Math.rint(rounded) && Math.abs(rounded) < 1e15) {
            return (long) rounded;
        }
        return rounded;
    }

    private static JsonArray array(Number... values) {
        JsonArray array = new JsonArray();
        for (Number value : values) {
            array.add(value);
        }
        return array;
    }

    private static String stem(String fileName) {
        int dot = fileName.lastIndexOf('.');
        return dot > 0 ? fileName.substring(0, dot) : fileName;
    }

    private static byte[] json(JsonObject object) {
        return GSON.toJson(object).getBytes(StandardCharsets.UTF_8);
    }
}
