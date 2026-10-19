#!/usr/bin/env python3
"""Export The Sift's look to the Bedrock resource pack, for players who join through Geyser.

Usage:
    python3 -I tools/export_sift_bedrock.py <geyser mappings/sounds.json> <bedrock sound_definitions.json>

The two arguments are reference tables, only read: Geyser's Java-to-Bedrock sound mapping
(mappings/sounds.json inside the Geyser jar) and the vanilla Bedrock sound list
(resource_pack/sounds/sound_definitions.json of Mojang's bedrock-samples). Many Sift sounds are
aliases of vanilla Java sound events; Bedrock sound definitions cannot alias, so such a sound gets
the vanilla Bedrock sound's file list.

Everything else comes from this repository, so the output always matches what Java players see:

    resourcepack/java/assets/minecraft/blockstates/*mushroom*.json  the mushroom-block states that draw
                                                                    Sift blocks, with their models
    resourcepack/java/assets/the_sift/                              block/item textures and models,
                                                                    item definitions, sounds, names
    src/main/resources/bettermodel/models/the_sift_*.bbmodel        the creatures and rifts (geometry,
                                                                    animations, textures)

Written (the_sift entries of the shared files are replaced, other entries are kept):

    resourcepack/bedrock/textures/the_sift/{blocks,items,entity}/    textures and generated icons
    resourcepack/bedrock/textures/terrain_texture.json             block textures, the_sift_* keys
    resourcepack/bedrock/textures/item_texture.json                item icons, the_sift.* keys
    resourcepack/bedrock/models/entity/the_sift_*.geo.json         creature and rift geometry
    resourcepack/bedrock/animations/the_sift_*.animation.json      their animations
    resourcepack/bedrock/animation_controllers/the_sift.controller.json
    resourcepack/bedrock/render_controllers/the_sift.render.json
    resourcepack/bedrock/entity/the_sift_*.entity.json             client entities the_sift:*
    resourcepack/bedrock/sounds/the_sift/                          the mod's sound files
    resourcepack/bedrock/sounds/sound_definitions.json             the_sift:* sounds
    resourcepack/bedrock/texts/*.lang                              creature names
    resourcepack/bedrock/credits/the_sift/                         the mod's license and credits, as in the
                                                                   Java pack (its art and sounds are the mod's)
    src/main/resources/geyser/the_sift_blocks.json                 Java block state -> Bedrock block,
                                                                   read by the Geyser integration

Geometry is the inverse of tools/convert_geckolib_bbmodel.py (Blockbench's own Bedrock export):
X is negated back for cube bounds and pivots, rotations negate X and Y again, per-face UVs become
Bedrock per-face UVs, and keyframe values are copied unchanged. Block items get an isometric icon
drawn from their block's textures, because Bedrock item icons are flat images.
"""
import base64
import importlib.util
import io
import json
import shutil
import sys
from pathlib import Path

from PIL import Image, ImageEnhance

ROOT = Path(__file__).resolve().parents[1]
JAVA = ROOT / "resourcepack/java/assets"
SIFT = JAVA / "the_sift"
BEDROCK = ROOT / "resourcepack/bedrock"
MODELS = ROOT / "src/main/resources/bettermodel/models"
BLOCK_MAPPING = ROOT / "src/main/resources/geyser/the_sift_blocks.json"
MUSHROOM_BLOCKS = ("brown_mushroom_block", "red_mushroom_block", "mushroom_stem")
FACES = ("up", "down", "north", "south", "east", "west")

# Bedrock creature: bbmodel(s), the animations a movement controller plays, and extras.
# idle/walk name the bbmodel animations played standing still and moving; "always" ones play all the
# time; "sitting" plays while the Java body sits (the wolf's flag). The Java side maps bodies to these
# identifiers in GeyserSiftContent.
CREATURES = {
    "blub": {"model": "the_sift_blub", "tamed": "the_sift_blub_tamed", "idle": "idle", "walk": "walk",
             "sitting": "sitting"},
    "sifter": {"model": "the_sift_sifter", "walk": "walk"},
    "singer": {"model": "the_sift_singer", "idle": "stand", "walk": "stride"},
    "echo_golem": {"model": "the_sift_echo_golem", "walk": "search_walk", "always": ["soul_hidden"]},
    "dark_sniffer": {"model": "the_sift_dark_sniffer", "idle": "idle", "walk": "walk"},
    "rift": {"model": "the_sift_rift", "always": ["appear"], "material": "entity_alphablend"},
    "rift_overworld": {"model": "the_sift_rift_overworld", "always": ["appear"], "material": "entity_alphablend"},
}
CREATURE_NAMES = {"blub": "Blub", "sifter": "Sifter", "singer": "Singer", "echo_golem": "Echo Golem",
                  "dark_sniffer": "Dark Sniffer", "rift": "Rift", "rift_overworld": "Rift"}


def number(value):
    return int(value) if float(value).is_integer() else round(float(value), 5)


def read_json(path):
    return json.loads(path.read_text(encoding="utf-8"))


def write_json(path, data):
    path.parent.mkdir(parents=True, exist_ok=True)
    path.write_text(json.dumps(data, indent=2, ensure_ascii=False) + "\n", encoding="utf-8")


def texture_path(reference):
    """'the_sift:block/siftslate' -> the PNG in the Java pack."""
    namespace, _, path = reference.partition(":")
    if namespace != "the_sift":
        raise ValueError(f"texture outside the Sift's assets: {reference}")
    return SIFT / "textures" / (path + ".png")


def load_model(reference):
    namespace, _, path = reference.partition(":")
    return read_json(SIFT / "models" / (path + ".json")) if namespace == "the_sift" else None


def resolved_model(reference):
    """Follows the parent chain inside the Sift's models: merged textures and the first vanilla parent."""
    textures, elements = {}, None
    while reference:
        model = load_model(reference)
        if model is None:
            break
        textures = {**model.get("textures", {}), **textures}
        if elements is None and "elements" in model:
            elements = model["elements"]
        reference = model.get("parent")
    resolved = {}
    for key, value in textures.items():
        while isinstance(value, str) and value.startswith("#"):
            value = textures.get(value[1:])
        if value:
            resolved[key] = value if ":" in value else "minecraft:" + value
    return resolved, reference, elements


# --- blocks -----------------------------------------------------------------------------------------

def cube_faces(textures, parent, elements):
    """Per-face textures of a full-cube block model."""
    name = (parent or "").split("/")[-1]
    if name in ("cube_all", "leaves") or (elements and "all" in textures):
        return {face: textures["all"] for face in FACES}
    if name == "cube_bottom_top":
        return {"up": textures["top"], "down": textures["bottom"],
                **{face: textures["side"] for face in ("north", "south", "east", "west")}}
    if name in ("cube_column", "cube_column_horizontal"):
        return {"up": textures["end"], "down": textures["end"],
                **{face: textures["side"] for face in ("north", "south", "east", "west")}}
    raise ValueError(f"not a full-cube model: {parent} {textures}")


def blockstate_string(block, when):
    properties = ",".join(f"{key}={when[key]}" for key in sorted(when))
    return f"minecraft:{block}[{properties}]"


def export_blocks(names):
    entries, terrain = [], {}
    for block in MUSHROOM_BLOCKS:
        for part in read_json(JAVA / "minecraft/blockstates" / (block + ".json"))["multipart"]:
            apply = part["apply"] if isinstance(part["apply"], list) else [part["apply"]]
            # Random variants (mirrored or turned copies) become the first one: Bedrock blocks have no variants.
            variant = apply[0]
            if not variant["model"].startswith("the_sift:"):
                continue
            when = part["when"]
            if sorted(when) != sorted(FACES):
                raise ValueError(f"{block}: a Sift case that is not one exact state: {when}")
            textures, parent, elements = resolved_model(variant["model"])
            faces = cube_faces(textures, parent, elements)
            keys = {}
            for face, reference in faces.items():
                key = "the_sift_" + reference.split("/")[-1]
                terrain[key] = reference
                keys[face] = key
            model_name = variant["model"].split("/")[-1]
            leaves = (parent or "").endswith("/leaves")
            rotation = [variant.get("x", 0), variant.get("y", 0), 0]
            # One Bedrock block per state: a model turned two ways (a log lying along x or z) is two blocks.
            suffix = "".join(f"_{axis}{angle}" for axis, angle in zip("xyz", rotation) if angle)
            entries.append({
                "state": blockstate_string(block, when),
                "name": "the_sift_" + model_name + suffix,
                "display_name": names.get(f"block.the_sift.{model_name}", pretty(model_name)),
                "faces": keys,
                "render_method": "alpha_test" if leaves else "opaque",
                "rotation": rotation,
            })
    names = [entry["name"] for entry in entries]
    if len(set(names)) != len(names):
        raise ValueError(f"Bedrock block names repeat: {sorted(names)}")
    for key, reference in sorted(terrain.items()):
        copy_png(texture_path(reference), BEDROCK / "textures/the_sift/blocks" / (key[len("the_sift_"):] + ".png"))
    texture_data = {key: {"textures": "textures/the_sift/blocks/" + key[len("the_sift_"):]} for key in sorted(terrain)}
    merge_texture_atlas(BEDROCK / "textures/terrain_texture.json", "atlas.terrain", "the_sift_", texture_data)
    write_json(BLOCK_MAPPING, {
        "_comment": "Generated by tools/export_sift_bedrock.py: the mushroom-block states that draw Sift blocks "
                    "and the Bedrock custom block Geyser shows for each. Edit the tool, not this file.",
        "blocks": entries,
    })
    return len(entries), len(terrain)


def copy_png(source, target):
    target.parent.mkdir(parents=True, exist_ok=True)
    shutil.copyfile(source, target)


def merge_texture_atlas(path, atlas, prefix, entries):
    data = read_json(path) if path.exists() else {
        "resource_pack_name": "bigcasares", "texture_name": atlas, "padding": 8, "num_mip_levels": 4}
    if atlas == "atlas.terrain":
        data.setdefault("padding", 8)
        data.setdefault("num_mip_levels", 4)
    kept = {key: value for key, value in data.get("texture_data", {}).items() if not key.startswith(prefix)}
    data["texture_data"] = {**kept, **entries}
    write_json(path, data)


# --- items ------------------------------------------------------------------------------------------

def item_model_reference(node):
    """The model an item definition shows in an inventory."""
    kind = node.get("type", "").removeprefix("minecraft:")
    if kind == "model":
        return node["model"]
    if kind == "special":
        return node["base"]
    if kind == "select":
        for case in node.get("cases", []):
            when = case["when"] if isinstance(case["when"], list) else [case["when"]]
            if node.get("property", "").endswith("display_context") and "gui" in when:
                return item_model_reference(case["model"])
        if "fallback" in node:
            return item_model_reference(node["fallback"])
        return item_model_reference(node["cases"][0]["model"])
    if kind == "condition":
        return item_model_reference(node["on_false"])
    if kind == "range_dispatch":
        return item_model_reference(node.get("fallback") or node["entries"][0]["model"])
    if kind == "composite":
        return item_model_reference(node["models"][0])
    raise ValueError(f"unsupported item model node: {node}")


def load_texture(reference, size=None):
    image = Image.open(texture_path(reference)).convert("RGBA")
    if image.height > image.width:
        image = image.crop((0, 0, image.width, image.width))  # first frame of an animated strip
    return image.resize((size, size), Image.NEAREST) if size and image.size != (size, size) else image


def isometric_icon(top, left, right):
    """A 32x32 inventory-style block drawn from three 16x16 faces (top, the left side, the right side)."""
    canvas = Image.new("RGBA", (32, 32), (0, 0, 0, 0))
    faces = (
        (left, (1, 0, 0, -0.5, 1, -8), 0.8),
        (right, (1, 0, -16, 0.5, 1, -24), 0.62),
        (top, (0.5, -1, 8, 0.5, 1, -8), 1.0),
    )
    for texture, matrix, light in faces:
        face = texture.transform((32, 32), Image.AFFINE, matrix, resample=Image.NEAREST)
        if light != 1.0:
            alpha = face.getchannel("A")
            face = ImageEnhance.Brightness(face.convert("RGB")).enhance(light).convert("RGBA")
            face.putalpha(alpha)
        canvas.alpha_composite(face)
    return canvas


def item_icon(item_id):
    definition = read_json(SIFT / "items" / (item_id + ".json"))
    textures, parent, elements = resolved_model(item_model_reference(definition["model"]))
    parent = parent or ""
    if parent.startswith("minecraft:item/") or parent.startswith("item/"):
        layers = [textures[key] for key in sorted(textures) if key.startswith("layer")]
        icon = load_texture(layers[0])
        for layer in layers[1:]:
            icon.alpha_composite(load_texture(layer, icon.width))
        return icon
    name = parent.split("/")[-1]
    if name in ("cross", "tinted_cross", "flower_pot_cross"):
        return load_texture(textures["cross"])
    pick = lambda *keys: next(textures[key] for key in keys if key in textures)
    top = load_texture(pick("top", "end", "all", "texture", "wall", "side", "particle"), 16)
    side = load_texture(pick("side", "all", "texture", "wall", "front", "end", "particle"), 16)
    front = load_texture(pick("front", "side", "all", "texture", "wall", "end", "particle"), 16)
    return isometric_icon(top, front, side)


def export_items():
    icons = {}
    for definition in sorted((SIFT / "items").glob("*.json")):
        item_id = definition.stem
        icon = item_icon(item_id)
        target = BEDROCK / "textures/the_sift/items" / (item_id + ".png")
        target.parent.mkdir(parents=True, exist_ok=True)
        icon.save(target, optimize=True)
        icons["the_sift." + item_id] = {"textures": "textures/the_sift/items/" + item_id}
    merge_texture_atlas(BEDROCK / "textures/item_texture.json", "atlas.items", "the_sift.", icons)
    return len(icons)


# --- creatures ----------------------------------------------------------------------------------------

def bedrock_geometry(identifier, bbmodel):
    groups = {group["uuid"]: group for group in bbmodel["groups"]}
    elements = {element["uuid"]: element for element in bbmodel["elements"]}
    bones, extent = [], [0.0, 0.0]

    def visit(node, parent):
        group = groups[node["uuid"]]
        origin, rotation = group.get("origin", [0, 0, 0]), group.get("rotation", [0, 0, 0])
        bone = {"name": group["name"], "pivot": [number(-origin[0]), number(origin[1]), number(origin[2])]}
        if parent:
            bone["parent"] = parent
        if any(rotation):
            bone["rotation"] = [number(-rotation[0]), number(-rotation[1]), number(rotation[2])]
        cubes = []
        for child in node["children"]:
            if isinstance(child, dict):
                continue
            element = elements[child]
            start, end = element["from"], element["to"]
            cube = {
                "origin": [number(-end[0]), number(start[1]), number(start[2])],
                "size": [number(end[axis] - start[axis]) for axis in range(3)],
                "uv": {},
            }
            for face, data in element["faces"].items():
                if data.get("texture") is None:
                    continue
                u0, v0, u1, v1 = data["uv"]
                cube["uv"][face] = {"uv": [number(u0), number(v0)], "uv_size": [number(u1 - u0), number(v1 - v0)]}
            if element.get("inflate"):
                cube["inflate"] = number(element["inflate"])
            pivot, turn = element.get("origin", [0, 0, 0]), element.get("rotation", [0, 0, 0])
            if any(turn):
                cube["pivot"] = [number(-pivot[0]), number(pivot[1]), number(pivot[2])]
                cube["rotation"] = [number(-turn[0]), number(-turn[1]), number(turn[2])]
            cubes.append(cube)
            extent[0] = max(extent[0], abs(start[0]), abs(end[0]), abs(start[2]), abs(end[2]))
            extent[1] = max(extent[1], abs(start[1]), abs(end[1]))
        if cubes:
            bone["cubes"] = cubes
        bones.append(bone)
        for child in node["children"]:
            if isinstance(child, dict):
                visit(child, group["name"])

    for root in bbmodel["outliner"]:
        if isinstance(root, dict):
            visit(root, None)
    resolution = bbmodel["resolution"]
    return {
        "format_version": "1.12.0",
        "minecraft:geometry": [{
            "description": {
                "identifier": identifier,
                "texture_width": resolution["width"], "texture_height": resolution["height"],
                "visible_bounds_width": number(round(extent[0] * 2 / 16 + 1, 1)),
                "visible_bounds_height": number(round(extent[1] / 16 + 1, 1)),
                "visible_bounds_offset": [0, number(round(extent[1] / 32, 2)), 0],
            },
            "bones": bones,
        }],
    }


def load_converter():
    """The GeckoLib converter's functions (it is a script, not a package)."""
    spec = importlib.util.spec_from_file_location("convert_geckolib_bbmodel", ROOT / "tools/convert_geckolib_bbmodel.py")
    module = importlib.util.module_from_spec(spec)
    spec.loader.exec_module(module)
    return module


def check_round_trip(converter, model_name, bbmodel, geometry):
    """Converting the Bedrock geometry back with the forward converter must give the bbmodel's cubes again."""
    elements, groups, _ = converter.convert_geometry(model_name, geometry["minecraft:geometry"][0])
    shape = lambda element: (element["from"], element["to"], element["origin"] if element.get("rotation") else None,
                             element.get("rotation"), element.get("inflate"),
                             {face: data["uv"] for face, data in element["faces"].items()
                              if data.get("texture") is not None})
    original = sorted((json.dumps(shape(element), sort_keys=True) for element in bbmodel["elements"]))
    returned = sorted((json.dumps(shape(element), sort_keys=True) for element in elements))
    if original != returned:
        raise ValueError(f"{model_name}: the Bedrock geometry does not convert back to the bbmodel")
    bones = {group["name"]: (group["origin"], group["rotation"]) for group in bbmodel["groups"]}
    if bones != {name: (group["origin"], group["rotation"]) for name, group in groups.items()}:
        raise ValueError(f"{model_name}: bones do not convert back")


def keyframe_value(point):
    values = []
    for axis in "xyz":
        raw = str(point.get(axis, "0")).strip() or "0"
        try:
            values.append(number(float(raw)))
        except ValueError:
            values.append(raw)
    return values


def bedrock_animations(entity, bbmodel):
    groups = {group["uuid"]: group["name"] for group in bbmodel["groups"]}
    loops = {"loop": True, "hold": "hold_on_last_frame", "once": False}
    animations = {}
    for animation in bbmodel.get("animations", []):
        bones = {}
        for key, animator in animation.get("animators", {}).items():
            name = groups.get(key, animator.get("name"))
            channels = {}
            for frame in sorted(animator.get("keyframes", []), key=lambda frame: float(frame["time"])):
                value = keyframe_value(frame["data_points"][0])
                if frame.get("interpolation") == "catmullrom":
                    value = {"post": value, "lerp_mode": "catmullrom"}
                elif frame.get("interpolation") == "step":
                    value = {"pre": value, "post": value}
                channels.setdefault(frame["channel"], {})[str(number(float(frame["time"])))] = value
            if channels:
                bones[name] = channels
        animations[f"animation.the_sift.{entity}.{animation['name']}"] = {
            "loop": loops.get(animation.get("loop"), False),
            "animation_length": number(animation["length"]),
            "bones": bones,
        }
    return {"format_version": "1.8.0", "animations": animations}


def embedded_texture(bbmodel):
    source = bbmodel["textures"][0]["source"]
    return Image.open(io.BytesIO(base64.b64decode(source.split(",", 1)[1]))).convert("RGBA")


def export_creatures():
    controllers, render_controllers, lang = {}, {}, {}
    converter = load_converter()
    for entity, spec in CREATURES.items():
        bbmodel = read_json(MODELS / (spec["model"] + ".bbmodel"))
        geometry_id = f"geometry.the_sift.{entity}"
        geometry = bedrock_geometry(geometry_id, bbmodel)
        check_round_trip(converter, spec["model"], bbmodel, geometry)
        write_json(BEDROCK / "models/entity" / f"the_sift_{entity}.geo.json", geometry)
        animation_file = bedrock_animations(entity, bbmodel)
        write_json(BEDROCK / "animations" / f"the_sift_{entity}.animation.json", animation_file)
        texture_dir = BEDROCK / "textures/the_sift/entity"
        texture_dir.mkdir(parents=True, exist_ok=True)
        embedded_texture(bbmodel).save(texture_dir / f"{entity}.png", optimize=True)
        textures = {"default": f"textures/the_sift/entity/{entity}"}
        if "tamed" in spec:
            embedded_texture(read_json(MODELS / (spec["tamed"] + ".bbmodel"))).save(
                texture_dir / f"{entity}_tamed.png", optimize=True)
            textures["tamed"] = f"textures/the_sift/entity/{entity}_tamed"
        names = {key.rsplit(".", 1)[1]: key for key in animation_file["animations"]}
        animations, animate = {}, []
        for role in ("idle", "walk", "sitting"):
            if role in spec:
                animations[role] = names[spec[role]]
        for extra in spec.get("always", []):
            animations[extra] = names[extra]
            animate.append(extra)
        if "walk" in spec:
            controller = f"controller.animation.the_sift.{entity}.move"
            controllers[controller] = movement_controller(spec)
            animations["move"] = controller
            animate.insert(0, "move")
        render = "controller.render.the_sift.tamed" if "tamed" in spec else "controller.render.the_sift.default"
        description = {
            "identifier": f"the_sift:{entity}",
            "materials": {"default": spec.get("material", "entity_alphatest")},
            "textures": textures,
            "geometry": {"default": geometry_id},
            "animations": animations,
            "render_controllers": [render],
        }
        if animate:
            description["scripts"] = {"animate": animate}
        write_json(BEDROCK / "entity" / f"the_sift_{entity}.entity.json",
                   {"format_version": "1.10.0", "minecraft:client_entity": {"description": description}})
        lang[f"entity.the_sift:{entity}.name"] = CREATURE_NAMES[entity]
    render_controllers["controller.render.the_sift.default"] = {
        "geometry": "Geometry.default", "materials": [{"*": "Material.default"}], "textures": ["Texture.default"]}
    # The Java Blub swaps between two models when tamed; here the wolf's tamed flag picks the texture.
    render_controllers["controller.render.the_sift.tamed"] = {
        "geometry": "Geometry.default", "materials": [{"*": "Material.default"}],
        "textures": ["query.is_tamed ? Texture.tamed : Texture.default"]}
    write_json(BEDROCK / "animation_controllers/the_sift.controller.json",
               {"format_version": "1.10.0", "animation_controllers": controllers})
    write_json(BEDROCK / "render_controllers/the_sift.render.json",
               {"format_version": "1.8.0", "render_controllers": render_controllers})
    return lang


def movement_controller(spec):
    moving = "query.modified_move_speed > 0.05"
    still = "query.modified_move_speed <= 0.05"
    states = {
        "default": {"animations": ["idle"] if "idle" in spec else [], "transitions": [{"moving": moving}]},
        "moving": {"animations": ["walk"], "transitions": [{"default": still}], "blend_transition": 0.2},
    }
    if "sitting" in spec:
        states["default"]["transitions"].insert(0, {"sitting": "query.is_sitting"})
        states["moving"]["transitions"].insert(0, {"sitting": "query.is_sitting"})
        states["sitting"] = {"animations": ["sitting"], "transitions": [{"default": "!query.is_sitting"}],
                             "blend_transition": 0.2}
    return {"initial_state": "default", "states": states}


# --- sounds -------------------------------------------------------------------------------------------

def export_sounds(geyser_sounds, vanilla_sounds):
    java = read_json(SIFT / "sounds.json")
    definitions, copied, unresolved = {}, set(), []
    for event, definition in java.items():
        sounds = []
        for entry in definition["sounds"]:
            entry = {"name": entry} if isinstance(entry, str) else dict(entry)
            name = entry["name"]
            if entry.get("type") == "event":
                vanilla = geyser_sounds.get(name.removeprefix("minecraft:"), {}).get("playsound_mapping")
                vanilla_definition = vanilla_sounds.get(vanilla) if vanilla else None
                if not vanilla_definition:
                    unresolved.append(f"{event} -> {name}")
                    continue
                for sound in vanilla_definition["sounds"]:
                    sound = {"name": sound} if isinstance(sound, str) else dict(sound)
                    if "volume" in entry or "pitch" in entry:
                        sound["volume"] = number(sound.get("volume", 1) * entry.get("volume", 1))
                        sound["pitch"] = number(sound.get("pitch", 1) * entry.get("pitch", 1))
                    sounds.append(sound)
                continue
            namespace, _, path = name.partition(":")
            if namespace != "the_sift":
                unresolved.append(f"{event} -> {name}")
                continue
            source = SIFT / "sounds" / (path + ".ogg")
            target = BEDROCK / "sounds/the_sift" / (path + ".ogg")
            target.parent.mkdir(parents=True, exist_ok=True)
            shutil.copyfile(source, target)
            copied.add(path)
            sound = {"name": "sounds/the_sift/" + path}
            for key in ("volume", "pitch", "stream"):
                if key in entry:
                    sound[key] = entry[key]
            sounds.append(sound)
        if sounds:
            definitions["the_sift:" + event] = {"category": category(event), "sounds": sounds}
    path = BEDROCK / "sounds/sound_definitions.json"
    data = read_json(path)
    kept = {key: value for key, value in data["sound_definitions"].items() if not key.startswith("the_sift:")}
    data["sound_definitions"] = {**kept, **dict(sorted(definitions.items()))}
    write_json(path, data)
    return len(definitions), len(copied), unresolved


def category(event):
    if event.startswith("entity."):
        return "hostile" if event.startswith("entity.sifter.") else "neutral"
    if event.startswith("block.") or "portal" in event:
        return "block"
    if event.startswith("item."):
        return "player"
    return "ambient"


# --- names --------------------------------------------------------------------------------------------

def pretty(path):
    return " ".join(word[:1].upper() + word[1:] for word in path.split("_"))


def merge_lang(entries):
    for path in sorted((BEDROCK / "texts").glob("*.lang")):
        lines = [line for line in path.read_text(encoding="utf-8").splitlines()
                 if line and not line.startswith("entity.the_sift:")]
        lines.extend(f"{key}={value}" for key, value in sorted(entries.items()))
        path.write_text("\n".join(lines) + "\n", encoding="utf-8")


def main(arguments):
    if len(arguments) != 2:
        print(__doc__)
        return 2
    geyser_sounds = read_json(Path(arguments[0]))
    vanilla_sounds = read_json(Path(arguments[1]))["sound_definitions"]
    names = read_json(SIFT / "lang/en_us.json")
    for generated in ("textures/the_sift", "sounds/the_sift", "credits/the_sift"):
        shutil.rmtree(BEDROCK / generated, ignore_errors=True)
    (BEDROCK / "credits/the_sift").mkdir(parents=True)
    for notice in ("LICENSE_the_sift", "CREDITS.txt"):
        shutil.copyfile(SIFT / notice, BEDROCK / "credits/the_sift" / notice)
    states, block_textures = export_blocks(names)
    icons = export_items()
    lang = export_creatures()
    merge_lang(lang)
    sounds, files, unresolved = export_sounds(geyser_sounds, vanilla_sounds)
    print(f"blocks: {states} states, {block_textures} textures; items: {icons} icons; "
          f"creatures: {len(CREATURES)}; sounds: {sounds} definitions, {files} files")
    if unresolved:
        print("sounds left out (no Bedrock equivalent found):", ", ".join(unresolved))
    return 0


if __name__ == "__main__":
    sys.exit(main(sys.argv[1:]))
