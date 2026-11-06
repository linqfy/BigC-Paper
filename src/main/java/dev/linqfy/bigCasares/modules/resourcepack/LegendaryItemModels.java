package dev.linqfy.bigCasares.modules.resourcepack;

import com.google.gson.Gson;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import org.joml.Matrix4d;
import org.joml.Vector3d;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.Base64;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HexFormat;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/** Compiles editable Blockbench cuboids into native 26.2 item models. */
public final class LegendaryItemModels {
    static final String VERSION = "legendary-bbmodel-1";
    private static final Gson JSON = new Gson();
    private static final double SCALE = .45;
    private static final String[][] SIDES = {{"west", "east"}, {"down", "up"}, {"north", "south"}};

    private record Bone(String id, Vector3d origin, Vector3d rotation, Bone parent, boolean visible) { }
    private record Cube(JsonObject data, Bone bone) { }
    private record Source(String id, JsonObject settings, JsonObject project) { }
    private record Clip(String name, boolean loop, int ticks, int first, JsonObject animation) {
        int frame(int tick) {
            int stride = loop ? 4 : 2;
            int sample = !loop && tick == ticks - 1 ? tick : tick / stride * stride;
            return first + sample;
        }
    }

    public void compile(Path sourceRoot, Path javaRoot) throws IOException {
        var sources = sources(sourceRoot);
        if (sources.isEmpty()) return;
        Path atlas = javaRoot.resolve("assets/minecraft/atlases/items.json");
        JsonObject atlasJson = Files.exists(atlas) ? read(atlas) : object("sources", new JsonArray());
        JsonArray atlasSources = atlasJson.getAsJsonArray("sources");
        Set<String> resources = new java.util.HashSet<>();
        for (var entry : atlasSources) {
            var value = entry.getAsJsonObject();
            if (value.has("resource")) resources.add(value.get("resource").getAsString());
        }
        for (Source source : sources) {
            new Model(source, javaRoot).write();
            String texture = "bigcasares:item/user/" + source.id();
            if (resources.add(texture)) atlasSources.add(object("type", "minecraft:single", "resource", texture));
        }
        write(atlas, atlasJson);
    }

    /** The same deterministic frame IDs are used by the pack and server timeline. */
    public ByteArrayInputStream animationCatalog(Path sourceRoot) throws IOException {
        var result = new StringBuilder("fps=20\n");
        for (Source source : sources(sourceRoot)) {
            for (Clip clip : clips(source.project())) {
                String prefix = source.id() + "." + clip.name();
                result.append(prefix).append(".frames=");
                for (int tick = 0; tick < clip.ticks(); tick++) {
                    if (tick > 0) result.append(',');
                    result.append(clip.frame(tick));
                }
                result.append('\n').append(prefix).append(".loop=").append(clip.loop()).append('\n');
            }
        }
        return new ByteArrayInputStream(result.toString().getBytes(StandardCharsets.UTF_8));
    }

    private static List<Source> sources(Path root) throws IOException {
        Path directory = root.resolve("legendary").toAbsolutePath().normalize();
        Path settings = directory.resolve("items.json");
        if (!Files.exists(settings)) return List.of();
        var sources = new ArrayList<Source>();
        for (var entry : read(settings).entrySet()) {
            if (!entry.getKey().matches("[a-z0-9_]+")) throw new IOException("Invalid legendary item ID");
            JsonObject value = entry.getValue().getAsJsonObject();
            Path input = directory.resolve(value.get("source").getAsString()).normalize();
            if (!input.startsWith(directory)) throw new IOException("Model source escapes legendary directory");
            sources.add(new Source(entry.getKey(), value, read(input)));
        }
        return sources;
    }

    private static List<Clip> clips(JsonObject project) {
        var result = new ArrayList<Clip>();
        int first = 1;
        if (!project.has("animations")) return result;
        for (var value : project.getAsJsonArray("animations")) {
            var animation = value.getAsJsonObject();
            int ticks = (int) Math.ceil(number(animation, "length", 0) * 20);
            if (ticks <= 0 || ticks > 12000) throw new IllegalArgumentException("Invalid animation length");
            String name = animation.get("name").getAsString();
            if (!name.matches("[a-zA-Z0-9_-]+")) throw new IllegalArgumentException("Invalid animation name");
            result.add(new Clip(name, "loop".equals(text(animation, "loop", "once")), ticks, first, animation));
            first += ticks;
        }
        return result;
    }

    private static final class Model {
        private final Source source;
        private final Path assets;
        private final Map<String, JsonObject> elements = new LinkedHashMap<>();
        private final List<Cube> cubes = new ArrayList<>();
        private final Map<String, Bone> bones = new LinkedHashMap<>();
        private final Map<String, String> meshes = new HashMap<>();
        private final Matrix4d normalization;
        private final Matrix4d inverseNormalization;
        private BufferedImage texture;
        private JsonObject presentation;

        Model(Source source, Path javaRoot) {
            this.source = source;
            assets = javaRoot.resolve("assets/bigcasares");
            Vector3d center = vector(source.settings(), "center", 0);
            normalization = new Matrix4d().translation(8, 8, 8).scale(SCALE)
                    .mul(rotation(vector(source.settings(), "rotation", 0))).translate(center.negate());
            inverseNormalization = new Matrix4d(normalization).invert();
            for (var e : source.project().getAsJsonArray("elements")) {
                JsonObject cube = e.getAsJsonObject();
                if (!"cube".equals(text(cube, "type", "cube"))) {
                    throw new IllegalArgumentException("Only cuboid item sources are supported");
                }
                elements.put(cube.get("uuid").getAsString(), cube);
            }
            collect(source.project().getAsJsonArray("outliner"), null);
        }

        private void collect(JsonArray nodes, Bone parent) {
            for (var node : nodes) {
                if (node.isJsonPrimitive()) {
                    JsonObject element = elements.get(node.getAsString());
                    if (element == null) throw new IllegalArgumentException("Missing outliner cube");
                    if (visible(element) && (parent == null || parent.visible())) cubes.add(new Cube(element, parent));
                } else {
                    var data = node.getAsJsonObject();
                    Bone bone = new Bone(data.get("uuid").getAsString(), vector(data, "origin", 0),
                            vector(data, "rotation", 0), parent, visible(data) && (parent == null || parent.visible()));
                    bones.put(bone.id(), bone);
                    collect(data.getAsJsonArray("children"), bone);
                }
            }
        }

        void write() throws IOException {
            String encoded = source.project().getAsJsonArray("textures").get(0).getAsJsonObject()
                    .get("source").getAsString();
            String prefix = "data:image/png;base64,";
            if (!encoded.startsWith(prefix)) throw new IOException("Model requires an embedded PNG texture");
            byte[] png = Base64.getDecoder().decode(encoded.substring(prefix.length()));
            texture = ImageIO.read(new ByteArrayInputStream(png));
            if (texture == null) throw new IOException("Invalid embedded texture");
            Path pngPath = assets.resolve("textures/item/user/" + source.id() + ".png");
            Files.createDirectories(pngPath.getParent());
            Files.write(pngPath, png);

            var rest = bake(cubes, null, 0);
            presentation = presentation(rest);
            JsonObject root = presentation.deepCopy();
            root.add("elements", cull(rest));
            LegendaryItemModels.write(assets.resolve("models/item/" + source.id() + ".json"), root);
            JsonObject restGraph = reference("item/" + source.id());
            JsonArray entries = new JsonArray();
            entries.add(object("threshold", 0, "model", restGraph));
            for (Clip clip : clips(source.project())) {
                var samples = new LinkedHashMap<Integer, JsonObject>();
                for (int tick = 0; tick < clip.ticks(); tick++) {
                    int frame = clip.frame(tick);
                    if (!samples.containsKey(frame)) {
                        samples.put(frame, graph(clip.animation(), (frame - clip.first()) / 20.0));
                    }
                }
                // Hold sparse poses over the original 20 Hz playback clock.
                for (int tick = 0; tick < clip.ticks(); tick++) {
                    entries.add(object("threshold", clip.first() + tick, "model", samples.get(clip.frame(tick))));
                }
            }
            JsonObject animated = object("type", "minecraft:range_dispatch", "property", "minecraft:custom_model_data",
                    "index", 0, "entries", entries, "fallback", restGraph);
            JsonObject select = object("type", "minecraft:select", "property", "minecraft:display_context",
                    "cases", JSON.toJsonTree(List.of(object("when", "gui", "model", restGraph))), "fallback", animated);
            LegendaryItemModels.write(assets.resolve("items/" + source.id() + ".json"),
                    object("hand_animation_on_swap", false, "model", select));
        }

        private JsonObject graph(JsonObject animation, double time) throws IOException {
            // Reuse rigid bones rather than baking the entire weapon per frame.
            var groups = new LinkedHashMap<String, List<Cube>>();
            var transforms = new HashMap<String, Matrix4d>();
            for (Cube cube : cubes) {
                Matrix4d pose = world(cube.bone(), animation, time);
                if (Math.abs(pose.determinant3x3()) < 1e-10) continue;
                Matrix4d delta = new Matrix4d(normalization).mul(pose)
                        .mul(world(cube.bone(), null, 0).invert()).mul(inverseNormalization);
                String key = array(delta).toString();
                groups.computeIfAbsent(key, ignored -> new ArrayList<>()).add(cube);
                transforms.put(key, delta);
            }
            var sorted = new ArrayList<>(groups.entrySet());
            sorted.sort(Comparator.<Map.Entry<String, List<Cube>>>comparingInt(e -> e.getValue().size()).reversed());
            JsonArray children = new JsonArray();
            var remainder = new ArrayList<Cube>();
            for (int i = 0; i < sorted.size(); i++) {
                var group = sorted.get(i);
                if (i >= 3 && sorted.size() > 4) {
                    remainder.addAll(group.getValue());
                } else {
                    JsonObject child = mesh(bake(group.getValue(), null, 0));
                    Matrix4d delta = new Matrix4d(transforms.get(group.getKey()));
                    delta.m30(delta.m30() / 16).m31(delta.m31() / 16).m32(delta.m32() / 16);
                    child.add("transformation", array(delta));
                    children.add(child);
                }
            }
            if (!remainder.isEmpty()) children.add(mesh(bake(remainder, animation, time)));
            return object("type", "minecraft:composite", "models", children);
        }

        private JsonObject mesh(JsonArray geometry) throws IOException {
            JsonArray optimized = cull(geometry);
            String payload = optimized.toString();
            String path = meshes.get(payload);
            if (path == null) {
                path = "item/legendary/" + source.id() + "/" + hash(payload);
                meshes.put(payload, path);
                JsonObject model = presentation.deepCopy();
                model.add("elements", optimized);
                LegendaryItemModels.write(assets.resolve("models/" + path + ".json"), model);
            }
            return reference(path);
        }

        private JsonArray bake(List<Cube> selected, JsonObject animation, double time) {
            JsonArray result = new JsonArray();
            for (Cube cube : selected) {
                JsonObject data = cube.data();
                Vector3d origin = vector(data, "origin", 0);
                Matrix4d matrix = new Matrix4d(normalization).mul(world(cube.bone(), animation, time))
                        .translate(origin).mul(rotation(vector(data, "rotation", 0))).translate(new Vector3d(origin).negate());
                Vector3d low = vector(data, "from", 0), high = vector(data, "to", 0);
                double inflate = number(data, "inflate", 0);
                low.sub(inflate, inflate, inflate);
                high.add(inflate, inflate, inflate);
                Vector3d center = matrix.transformPosition(new Vector3d(low).add(high).mul(.5));
                Vector3d lengths = matrix.getScale(new Vector3d());
                Vector3d half = new Vector3d(high).sub(low).mul(lengths).mul(.5);
                if (Math.min(half.x, Math.min(half.y, half.z)) < 1e-7) continue;
                Matrix4d axes = new Matrix4d(matrix).scale(1 / lengths.x, 1 / lengths.y, 1 / lengths.z);
                if (Math.abs(axes.getColumn(0, new Vector3d()).dot(axes.getColumn(1, new Vector3d()))) > 1e-5
                        || Math.abs(axes.getColumn(0, new Vector3d()).dot(axes.getColumn(2, new Vector3d()))) > 1e-5
                        || Math.abs(axes.getColumn(1, new Vector3d()).dot(axes.getColumn(2, new Vector3d()))) > 1e-5) {
                    throw new IllegalArgumentException("Sheared cuboid in " + source.id());
                }
                Vector3d angles = euler(axes);
                JsonObject faces = new JsonObject();
                for (var entry : data.getAsJsonObject("faces").entrySet()) {
                    JsonObject face = entry.getValue().getAsJsonObject();
                    if (!face.has("texture") || face.get("texture").isJsonNull()) continue;
                    JsonObject copy = object("uv", face.get("uv"), "texture", "#body");
                    if (face.has("rotation")) copy.add("rotation", face.get("rotation"));
                    faces.add(entry.getKey(), copy);
                }
                if (faces.size() == 0) continue;
                JsonObject part = object("from", array(new Vector3d(center).sub(half)),
                        "to", array(new Vector3d(center).add(half)), "faces", faces, "shade", true);
                if (angles.lengthSquared() > 1e-10) {
                    part.add("rotation", object("origin", array(center), "x", angles.x, "y", angles.y, "z", angles.z));
                }
                if (data.has("light_emission")) part.add("light_emission", data.get("light_emission"));
                result.add(part);
            }
            return result;
        }

        private Matrix4d world(Bone bone, JsonObject animation, double time) {
            if (bone == null) return new Matrix4d();
            Vector3d position = new Vector3d(bone.origin());
            JsonObject animator = animation == null ? null : animation.getAsJsonObject("animators").getAsJsonObject(bone.id());
            Vector3d angles = new Vector3d(bone.rotation());
            Vector3d scale = new Vector3d(1);
            if (animator != null) {
                boolean loop = "loop".equals(text(animation, "loop", "once"));
                position.add(sample(animator, "position", time, 0, loop));
                angles.add(sample(animator, "rotation", time, 0, loop));
                scale = sample(animator, "scale", time, 1, loop);
            }
            return world(bone.parent(), animation, time).translate(position).mul(rotation(angles)).scale(scale)
                    .translate(new Vector3d(bone.origin()).negate());
        }

        private JsonObject presentation(JsonArray rest) {
            double span = 1;
            for (int axis = 0; axis < 3; axis++) {
                double low = Double.POSITIVE_INFINITY, high = Double.NEGATIVE_INFINITY;
                for (var part : rest) {
                    low = Math.min(low, part.getAsJsonObject().getAsJsonArray("from").get(axis).getAsDouble());
                    high = Math.max(high, part.getAsJsonObject().getAsJsonArray("to").get(axis).getAsDouble());
                }
                span = Math.max(span, high - low);
            }
            double hand = number(source.settings(), "hand-scale", 1.15);
            JsonObject display = object("gui", display(0, -24, 0, 0, 0, 0, 12 / span),
                    "firstperson_righthand", display(0, -90, 25, 1.1, 2.5, 1.1, hand),
                    "firstperson_lefthand", display(0, 90, -25, 1.1, 2.5, 1.1, hand),
                    "thirdperson_righthand", display(0, -90, 55, 0, 2, 0, hand),
                    "thirdperson_lefthand", display(0, 90, -55, 0, 2, 0, hand),
                    "ground", display(0, 0, 0, 0, 2, 0, .6), "fixed", display(0, 0, 0, 0, 0, 0, 1));
            String textureId = "bigcasares:item/user/" + source.id();
            return object("textures", object("body", textureId, "particle", textureId), "gui_light", "front", "display", display);
        }

        private JsonArray cull(JsonArray parts) {
            JsonArray result = parts.deepCopy();
            for (var value : result) {
                JsonObject part = value.getAsJsonObject();
                if (part.has("rotation")) continue;
                for (int axis = 0; axis < 3; axis++) {
                    for (int sign = 0; sign < 2; sign++) {
                        String side = SIDES[axis][sign];
                        if (!part.getAsJsonObject("faces").has(side)) continue;
                        for (var other : parts) {
                            JsonObject solid = other.getAsJsonObject();
                            if (solid.has("rotation") || solid.equals(value) || !opaque(solid)) continue;
                            if (covered(part, solid, axis, sign)) {
                                part.getAsJsonObject("faces").remove(side);
                                break;
                            }
                        }
                    }
                }
            }
            return result;
        }

        private boolean opaque(JsonObject solid) {
            JsonObject faces = solid.getAsJsonObject("faces");
            if (faces.size() != 6) return false;
            for (var f : faces.entrySet()) {
                JsonArray uv = f.getValue().getAsJsonObject().getAsJsonArray("uv");
                int x0 = (int) Math.floor(Math.min(uv.get(0).getAsDouble(), uv.get(2).getAsDouble()) * texture.getWidth() / 16);
                int x1 = (int) Math.ceil(Math.max(uv.get(0).getAsDouble(), uv.get(2).getAsDouble()) * texture.getWidth() / 16);
                int y0 = (int) Math.floor(Math.min(uv.get(1).getAsDouble(), uv.get(3).getAsDouble()) * texture.getHeight() / 16);
                int y1 = (int) Math.ceil(Math.max(uv.get(1).getAsDouble(), uv.get(3).getAsDouble()) * texture.getHeight() / 16);
                if (x1 <= x0 || y1 <= y0 || x0 < 0 || y0 < 0 || x1 > texture.getWidth() || y1 > texture.getHeight()) return false;
                for (int y = y0; y < y1; y++) for (int x = x0; x < x1; x++) {
                    if ((texture.getRGB(x, y) >>> 24) != 255) return false;
                }
            }
            return true;
        }
    }

    static boolean covered(JsonObject part, JsonObject solid, int axis, int sign) {
        JsonArray low = part.getAsJsonArray("from"), high = part.getAsJsonArray("to");
        JsonArray a = solid.getAsJsonArray("from"), b = solid.getAsJsonArray("to");
        double plane = (sign == 0 ? low : high).get(axis).getAsDouble();
        if (plane < a.get(axis).getAsDouble() - 1e-7 || plane > b.get(axis).getAsDouble() + 1e-7) return false;
        // The occluder must extend outward from this face, not merely share it.
        if (sign == 0 && a.get(axis).getAsDouble() >= plane - 1e-7) return false;
        if (sign == 1 && b.get(axis).getAsDouble() <= plane + 1e-7) return false;
        for (int n = 0; n < 3; n++) if (n != axis) {
            if (a.get(n).getAsDouble() > low.get(n).getAsDouble() + 1e-7
                    || b.get(n).getAsDouble() < high.get(n).getAsDouble() - 1e-7) return false;
        }
        return true;
    }

    static Vector3d sample(JsonObject animator, String channel, double time, double fallback) {
        return sample(animator, channel, time, fallback, false);
    }

    static Vector3d sample(JsonObject animator, String channel, double time, double fallback, boolean loop) {
        var keys = new ArrayList<JsonObject>();
        for (var value : animator.getAsJsonArray("keyframes")) {
            JsonObject key = value.getAsJsonObject();
            if (channel.equals(text(key, "channel", ""))) keys.add(key);
        }
        keys.sort(Comparator.comparingDouble(k -> number(k, "time", 0)));
        if (keys.isEmpty()) return new Vector3d(fallback);
        int next = 0;
        while (next < keys.size() && number(keys.get(next), "time", 0) < time - 1e-7) next++;
        if (next == 0) return point(keys.getFirst(), false);
        if (next == keys.size()) return point(keys.getLast(), true);
        JsonObject before = keys.get(next - 1), after = keys.get(next);
        double end = number(after, "time", 0);
        if (Math.abs(end - time) < 1e-7) return point(after, false);
        if ("step".equals(text(before, "interpolation", "linear"))) return point(before, true);
        double start = number(before, "time", 0), t = (time - start) / (end - start);
        Vector3d p1 = point(before, true), p2 = point(after, false);
        if ("catmullrom".equals(text(before, "interpolation", "linear"))
                || "catmullrom".equals(text(after, "interpolation", "linear"))) {
            Vector3d p0 = next > 1 ? point(keys.get(next - 2), true)
                    : loop && keys.size() >= 3 ? point(keys.get(keys.size() - 2), true) : p1;
            Vector3d p3 = next + 1 < keys.size() ? point(keys.get(next + 1), false)
                    : loop && keys.size() >= 3 ? point(keys.get(1), false) : p2;
            return new Vector3d(p1).mul(2).add(new Vector3d(p2).sub(p0).mul(t))
                    .add(new Vector3d(p0).mul(2).sub(new Vector3d(p1).mul(5)).add(new Vector3d(p2).mul(4)).sub(p3).mul(t * t))
                    .add(new Vector3d(p0).negate().add(new Vector3d(p1).mul(3)).sub(new Vector3d(p2).mul(3)).add(p3).mul(t * t * t)).mul(.5);
        }
        if (!"linear".equals(text(before, "interpolation", "linear"))) {
            throw new IllegalArgumentException("Unsupported interpolation");
        }
        return p1.lerp(p2, t);
    }

    private static Vector3d point(JsonObject key, boolean last) {
        JsonArray points = key.getAsJsonArray("data_points");
        JsonObject p = points.get(last ? points.size() - 1 : 0).getAsJsonObject();
        return new Vector3d(number(p, "x", 0), number(p, "y", 0), number(p, "z", 0));
    }

    private static Matrix4d rotation(Vector3d angles) {
        return new Matrix4d().rotationZYX(Math.toRadians(angles.z), Math.toRadians(angles.y), Math.toRadians(angles.x));
    }

    private static Vector3d euler(Matrix4d m) {
        double cy = Math.hypot(m.m00(), m.m01());
        return new Vector3d(cy > 1e-8 ? Math.atan2(m.m12(), m.m22()) : 0,
                Math.atan2(-m.m02(), cy), cy > 1e-8 ? Math.atan2(m.m01(), m.m00()) : Math.atan2(-m.m10(), m.m11()))
                .mul(180 / Math.PI);
    }

    private static Vector3d vector(JsonObject object, String key, double fallback) {
        if (!object.has(key)) return new Vector3d(fallback);
        JsonArray a = object.getAsJsonArray(key);
        return new Vector3d(a.get(0).getAsDouble(), a.get(1).getAsDouble(), a.get(2).getAsDouble());
    }

    private static boolean visible(JsonObject o) {
        return (!o.has("visibility") || o.get("visibility").getAsBoolean())
                && (!o.has("export") || o.get("export").getAsBoolean());
    }

    private static double number(JsonObject o, String key, double fallback) {
        double value = o.has(key) ? o.get(key).getAsDouble() : fallback;
        if (!Double.isFinite(value)) throw new IllegalArgumentException("Nonfinite model value");
        return value;
    }

    private static String text(JsonObject o, String key, String fallback) {
        return o.has(key) ? o.get(key).getAsString() : fallback;
    }

    private static JsonArray array(Vector3d v) { return JSON.toJsonTree(new double[] {round(v.x), round(v.y), round(v.z)}).getAsJsonArray(); }

    private static JsonArray array(Matrix4d m) {
        JsonArray a = new JsonArray();
        for (int row = 0; row < 4; row++) for (int col = 0; col < 4; col++) a.add(round(m.get(col, row)));
        return a;
    }

    private static double round(double n) {
        if (!Double.isFinite(n)) throw new IllegalArgumentException("Nonfinite transform");
        return Math.rint(n * 1e7) / 1e7;
    }

    private static JsonObject display(double x, double y, double z, double tx, double ty, double tz, double scale) {
        return object("rotation", array(new Vector3d(x, y, z)), "translation", array(new Vector3d(tx, ty, tz)),
                "scale", array(new Vector3d(scale)));
    }

    private static JsonObject reference(String path) { return object("type", "minecraft:model", "model", "bigcasares:" + path); }

    private static JsonObject object(Object... fields) {
        JsonObject o = new JsonObject();
        for (int i = 0; i < fields.length; i += 2) o.add((String) fields[i], JSON.toJsonTree(fields[i + 1]));
        return o;
    }

    private static JsonObject read(Path path) throws IOException {
        try (var reader = Files.newBufferedReader(path)) { return JsonParser.parseReader(reader).getAsJsonObject(); }
    }

    private static void write(Path path, JsonObject value) throws IOException {
        Files.createDirectories(path.getParent());
        Files.writeString(path, JSON.toJson(value) + "\n");
    }

    private static String hash(String data) {
        try { return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(data.getBytes(StandardCharsets.UTF_8))).substring(0, 16); }
        catch (NoSuchAlgorithmException impossible) { throw new IllegalStateException(impossible); }
    }
}
