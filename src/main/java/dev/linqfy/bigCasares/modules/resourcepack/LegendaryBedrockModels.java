package dev.linqfy.bigCasares.modules.resourcepack;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.List;

import static dev.linqfy.bigCasares.modules.resourcepack.LegendaryItemModels.object;
import static dev.linqfy.bigCasares.modules.resourcepack.LegendaryItemModels.read;
import static dev.linqfy.bigCasares.modules.resourcepack.LegendaryItemModels.write;

/** Native Bedrock attachables share the Java compiler's rest geometry and original texture. */
final class LegendaryBedrockModels {
    private static final List<String> POSES = List.of("thirdperson_main_hand", "thirdperson_off_hand",
            "firstperson_main_hand", "firstperson_off_hand");
    private static final List<String> DISPLAY = List.of("thirdperson_righthand", "thirdperson_lefthand",
            "firstperson_righthand", "firstperson_lefthand");

    void compile(String id, Path javaRoot, Path bedrockRoot) throws IOException {
        Path assets = javaRoot.resolve("assets/bigcasares");
        JsonObject model = read(assets.resolve("models/item/" + id + ".json"));
        String texture = "textures/item/legendary/" + id;
        Path png = bedrockRoot.resolve(texture + ".png");
        Files.createDirectories(png.getParent());
        Files.copy(assets.resolve("textures/item/user/" + id + ".png"), png, StandardCopyOption.REPLACE_EXISTING);
        BedrockItemIcon.render(model, png, bedrockRoot.resolve(texture + "_icon.png"));
        Path atlas = bedrockRoot.resolve("textures/item_texture.json");
        JsonObject atlasJson = Files.exists(atlas) ? read(atlas)
                : object("resource_pack_name", "bigcasares", "texture_name", "atlas.items", "texture_data", new JsonObject());
        atlasJson.getAsJsonObject("texture_data").add("bigcasares.legendary." + id, object("textures", texture + "_icon"));
        write(atlas, atlasJson);

        JsonArray cubes = new JsonArray();
        for (var element : model.getAsJsonArray("elements")) cubes.add(cube(element.getAsJsonObject()));
        JsonArray bones = new JsonArray();
        List<String> names = List.of("geyser_custom", "geyser_custom_x", "geyser_custom_y", "geyser_custom_z");
        for (int i = 0; i < names.size(); i++) {
            JsonObject bone = object("name", names.get(i), "pivot", List.of(0, 8, 0));
            if (i > 0) bone.addProperty("parent", names.get(i - 1));
            if (i == names.size() - 1) bone.add("cubes", cubes);
            bones.add(bone);
        }
        write(bedrockRoot.resolve("models/entity/legendary/" + id + ".geo.json"), object("format_version", "1.16.0",
                "minecraft:geometry", List.of(object("description", object("identifier", "geometry.bigcasares.legendary." + id,
                        "texture_width", 16, "texture_height", 16, "visible_bounds_width", 4,
                        "visible_bounds_height", 4, "visible_bounds_offset", List.of(0, .5, 0)), "bones", bones))));

        JsonObject animations = new JsonObject();
        JsonObject aliases = new JsonObject();
        JsonArray animate = new JsonArray();
        for (int i = 0; i < POSES.size(); i++) {
            String name = POSES.get(i);
            String key = "animation.bigcasares.legendary." + id + "." + name;
            animations.add(key, object("loop", true, "bones", hold(model.getAsJsonObject("display")
                    .getAsJsonObject(DISPLAY.get(i)), i >= 2, i % 2 == 1)));
            aliases.addProperty(name, key);
            animate.add(object(name, "v." + (i % 2 == 1 ? "off_hand" : "main_hand")
                    + (i >= 2 ? " && c.is_first_person" : " && !c.is_first_person")));
        }
        write(bedrockRoot.resolve("animations/legendary/" + id + ".animation.json"),
                object("format_version", "1.8.0", "animations", animations));
        write(bedrockRoot.resolve("attachables/legendary/" + id + ".json"), object("format_version", "1.10.0",
                "minecraft:attachable", object("description", object("identifier", "bigcasares:" + id,
                        "materials", object("default", "entity_alphatest", "enchanted", "entity_alphatest_glint"),
                        "textures", object("default", texture, "enchanted", "textures/misc/enchanted_item_glint"),
                        "geometry", object("default", "geometry.bigcasares.legendary." + id),
                        "animations", aliases, "scripts", object("pre_animation", List.of(
                                "v.main_hand = c.item_slot == 'main_hand';", "v.off_hand = c.item_slot == 'off_hand';"),
                                "animate", animate), "render_controllers", List.of("controller.render.item_default")))));
    }

    static JsonObject cube(JsonObject element) {
        JsonArray from = element.getAsJsonArray("from"), to = element.getAsJsonArray("to");
        JsonObject faces = new JsonObject();
        for (var entry : element.getAsJsonObject("faces").entrySet()) {
            var face = entry.getValue().getAsJsonObject();
            JsonArray uv = face.getAsJsonArray("uv");
            double u0 = uv.get(0).getAsDouble(), v0 = uv.get(1).getAsDouble();
            double u1 = uv.get(2).getAsDouble(), v1 = uv.get(3).getAsDouble();
            boolean up = entry.getKey().equals("up"), down = entry.getKey().equals("down");
            var bedrock = object("uv", List.of(up || down ? u1 : u0, up ? v1 : v0),
                    "uv_size", List.of(up || down ? u0 - u1 : u1 - u0, up ? v0 - v1 : v1 - v0));
            if (face.has("rotation")) bedrock.add("uv_rotation", face.get("rotation"));
            faces.add(entry.getKey(), bedrock);
        }
        JsonObject cube = object("origin", List.of(8 - to.get(0).getAsDouble(), from.get(1).getAsDouble(), from.get(2).getAsDouble() - 8),
                "size", List.of(to.get(0).getAsDouble() - from.get(0).getAsDouble(),
                        to.get(1).getAsDouble() - from.get(1).getAsDouble(), to.get(2).getAsDouble() - from.get(2).getAsDouble()), "uv", faces);
        if (element.has("rotation")) {
            var rotation = element.getAsJsonObject("rotation");
            var pivot = rotation.getAsJsonArray("origin");
            cube.add("pivot", object("v", List.of(8 - pivot.get(0).getAsDouble(), pivot.get(1).getAsDouble(),
                    pivot.get(2).getAsDouble() - 8)).get("v"));
            cube.add("rotation", object("v", List.of(-rotation.get("x").getAsDouble(),
                    -rotation.get("y").getAsDouble(), rotation.get("z").getAsDouble())).get("v"));
        }
        return cube;
    }

    private static JsonObject hold(JsonObject display, boolean firstPerson, boolean mirror) {
        var r = display.getAsJsonArray("rotation");
        var t = display.getAsJsonArray("translation");
        int sign = mirror ? -1 : 1;
        return object("geyser_custom", firstPerson
                        ? object("rotation", List.of(90, 60, -40), "position", List.of(4, 10, 4), "scale", 1.5)
                        : object("rotation", List.of(90, 0, 0), "position", List.of(0, 13, -3)),
                "geyser_custom_x", object("rotation", List.of(-r.get(0).getAsDouble(), 0, 0),
                        "position", List.of(-t.get(0).getAsDouble() * sign, t.get(1).getAsDouble(), t.get(2).getAsDouble()),
                        "scale", display.get("scale")),
                "geyser_custom_y", object("rotation", List.of(0, -r.get(1).getAsDouble() * sign, 0)),
                "geyser_custom_z", object("rotation", List.of(0, 0, r.get(2).getAsDouble() * sign)));
    }
}
