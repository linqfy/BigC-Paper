"""Bloques de Alex's Caves para Bedrock (mapeo de bloques de Geyser, geometrías y atlas de terreno).

Parte solo de lo que ya está en el repo (blocks.yml, capa de pack Java, modelos Java y texturas Bedrock), asi que
corre sin el JAR del mod:

    python -I tools/alexscaves/bedrock_blocks.py <raiz del repo>

Escribe:
- ``src/main/resources/alexscaves/geyser/alexscaves-blocks.json``: un override por estado vanilla que usa el módulo
  (note block, tripwire, chorus plant) y por la losa petrificada (losa de piedra caliza lisa).
- ``resourcepack/bedrock/models/blocks/alexscaves_*.geo.json``: geometría de cada bloque que no es un cubo (plantas,
  losas, huesos...), convertida de su modelo Java.
- Las claves ``alexscaves_*`` de ``resourcepack/bedrock/textures/terrain_texture.json`` (una por textura usada).

Los cubos llevan una textura por cara (``up``, ``down``, ``north``...) y los demás una instancia de material por
textura, nombrada como la textura. El giro del estado (x/y del blockstate) se hornea en la geometria.
"""
import hashlib
import json
import math
import os
import re
import sys

sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))
import block_bases  # noqa: E402

NS = "alexscaves"
BASES = [block_bases.NOTE_BLOCK, block_bases.TRIPWIRE, block_bases.CHORUS_PLANT]
FACES = ["north", "south", "east", "west", "up", "down"]
DIRECTIONS = {"north": (0, 0, -1), "south": (0, 0, 1), "east": (1, 0, 0), "west": (-1, 0, 0),
              "up": (0, 1, 0), "down": (0, -1, 0)}
BY_VECTOR = {v: k for k, v in DIRECTIONS.items()}
PETRIFIED = "petrified_oak_slab"
FILLER = "minecraft:block/note_block"

CUBE_PARENTS = {
    "minecraft:block/cube": lambda t: {f: t.get(f) for f in FACES},
    "minecraft:block/cube_all": lambda t: {f: t.get("all") for f in FACES},
    "minecraft:block/cube_column": lambda t: dict({f: t.get("side") for f in FACES}, up=t.get("end"), down=t.get("end")),
    "minecraft:block/cube_column_horizontal": lambda t: dict({f: t.get("side") for f in FACES}, up=t.get("end"),
                                                              down=t.get("end")),
    "minecraft:block/cube_bottom_top": lambda t: dict({f: t.get("side") for f in FACES}, up=t.get("top"),
                                                       down=t.get("bottom")),
    "minecraft:block/orientable_with_bottom": lambda t: dict({f: t.get("side") for f in FACES}, up=t.get("top"),
                                                              down=t.get("bottom"), north=t.get("front")),
}


def face(texture, uv=None):
    entry = {"texture": texture}
    if uv is not None:
        entry["uv"] = uv
    return entry


def vanilla_elements(parent):
    """Elements of the few vanilla parent models the module's models inherit from."""
    if parent in ("minecraft:block/slab", "minecraft:block/slab_top"):
        low, high = (0, 8) if parent.endswith("slab") else (8, 16)
        return [{"from": [0, low, 0], "to": [16, high, 16], "faces": {
            "down": face("#bottom"), "up": face("#top"), "north": face("#side", [0, 16 - high, 16, 16 - low]),
            "south": face("#side", [0, 16 - high, 16, 16 - low]), "west": face("#side", [0, 16 - high, 16, 16 - low]),
            "east": face("#side", [0, 16 - high, 16, 16 - low])}}]
    if parent == "minecraft:block/cross":
        quad = lambda a, b: {"uv": [0, 0, 16, 16], "texture": "#cross"}  # noqa: E731
        return [{"from": [0.8, 0, 8], "to": [15.2, 16, 8],
                 "rotation": {"origin": [8, 8, 8], "axis": "y", "angle": 45},
                 "faces": {"north": quad(0, 0), "south": quad(0, 0)}},
                {"from": [8, 0, 0.8], "to": [8, 16, 15.2],
                 "rotation": {"origin": [8, 8, 8], "axis": "y", "angle": 45},
                 "faces": {"west": quad(0, 0), "east": quad(0, 0)}}]
    return None


def full_cube(textures):
    return [{"from": [0, 0, 0], "to": [16, 16, 16], "faces": {f: face("#" + f) for f in FACES}}], textures


class Models:
    def __init__(self, repo):
        self.root = os.path.join(repo, "resourcepack/java/assets", NS, "models")

    def load(self, ref):
        with open(os.path.join(self.root, ref.split(":", 1)[1] + ".json"), encoding="utf-8") as handle:
            return json.load(handle)

    def resolve(self, ref):
        """(elements, textures {slot: ref}, render_type, cube face textures or None) of a model."""
        chain, render_type = [], None
        while ref and ref.startswith(NS + ":"):
            model = self.load(ref)
            chain.append(model)
            render_type = render_type or model.get("render_type")
            ref = model.get("parent")
            if ref and ":" not in ref:
                ref = "minecraft:" + ref
        textures = {}
        for model in reversed(chain):
            textures.update(model.get("textures", {}))
        elements = next((m["elements"] for m in chain if "elements" in m), None)
        cube = None
        if elements is None:
            if ref in CUBE_PARENTS:
                cube = {f: t for f, t in CUBE_PARENTS[ref](textures).items()}
                elements = full_cube(None)[0]
                textures = dict(textures, **{f: cube[f] for f in FACES if cube.get(f)})
            elif ref == "minecraft:block/note_block":
                elements = full_cube(None)[0]
                textures = dict(textures, **{f: textures.get("particle") or "minecraft:block/note_block" for f in FACES})
            else:
                elements = vanilla_elements(ref)
            if elements is None and textures.get("particle"):
                # Drawn by a block entity in Java (copper valve): a cube with its particle texture.
                elements = full_cube(None)[0]
                textures = dict(textures, **{f: textures["particle"] for f in FACES})
        if elements is None:
            raise ValueError("modelo sin elementos: %s" % ref)
        return elements, textures, render_type


def texture_of(textures, slot):
    seen = 0
    while isinstance(slot, str) and slot.startswith("#") and seen < 10:
        slot = textures.get(slot[1:])
        seen += 1
    return slot if isinstance(slot, str) and not slot.startswith("#") else None


def texture_key(ref):
    namespace, path = ref.split(":", 1) if ":" in ref else ("minecraft", ref)
    if namespace == "minecraft":
        return path.split("/")[-1]  # Bedrock's own terrain atlas already has it (bone_block_side...)
    return "%s_%s" % (NS, re.sub(r"[^a-z0-9]+", "_", path.lower()).strip("_"))


def rotate(vector, x, y):
    """Java blockstate rotation (x, then y, clockwise degrees) of a centered vector."""
    px, py, pz = vector
    for axis, angle in (("x", -x), ("y", -y)):
        if not angle:
            continue
        c, s = round(math.cos(math.radians(angle))), round(math.sin(math.radians(angle)))
        if axis == "x":
            py, pz = py * c - pz * s, py * s + pz * c
        else:
            px, pz = px * c + pz * s, -px * s + pz * c
    return (px, py, pz)


def default_uv(name, frm, to):
    x1, y1, z1 = frm
    x2, y2, z2 = to
    return {"north": [16 - x2, 16 - y2, 16 - x1, 16 - y1], "south": [x1, 16 - y2, x2, 16 - y1],
            "west": [z1, 16 - y2, z2, 16 - y1], "east": [16 - z2, 16 - y2, 16 - z1, 16 - y1],
            "up": [x1, z1, x2, z2], "down": [x1, 16 - z2, x2, 16 - z1]}[name]


def bake(element, x, y):
    """The element turned by the blockstate rotation: (from, to, rotation or None, faces {name: (texture, uv)})."""
    frm, to = element["from"], element["to"]
    corners = [rotate([frm[0] - 8, frm[1] - 8, frm[2] - 8], x, y), rotate([to[0] - 8, to[1] - 8, to[2] - 8], x, y)]
    new_from = [min(a, b) + 8 for a, b in zip(*corners)]
    new_to = [max(a, b) + 8 for a, b in zip(*corners)]
    rotation = element.get("rotation")
    out_rotation = None
    if rotation and rotation.get("angle"):
        axis = {"x": (1, 0, 0), "y": (0, 1, 0), "z": (0, 0, 1)}[rotation["axis"]]
        turned = rotate(axis, x, y)
        angle = rotation["angle"] * (1 if sum(turned) > 0 else -1)
        origin = rotate([c - 8 for c in rotation["origin"]], x, y)
        out_rotation = (next(a for a, v in zip("xyz", turned) if v), angle, [round(c + 8, 4) for c in origin])
    faces = {}
    for name, data in element.get("faces", {}).items():
        target = BY_VECTOR[tuple(int(v) for v in rotate(DIRECTIONS[name], x, y))]
        faces[target] = (data.get("texture"), data.get("uv"), name)
    return new_from, new_to, out_rotation, faces


def clamp(value):
    return max(0.0, min(16.0, value))


def geometry_for(identifier, elements, textures, x, y, instance_names):
    """Bedrock geometry of a model; ``instance_names`` collects the texture keys the cubes use."""
    cubes = []
    for element in elements:
        frm, to, rotation, faces = bake(element, x, y)
        frm = [clamp(v) for v in frm]
        to = [clamp(v) for v in to]
        cube = {"origin": [round(8 - to[0], 4), round(frm[1], 4), round(frm[2] - 8, 4)],
                "size": [round(to[i] - frm[i], 4) for i in range(3)]}
        if rotation:
            axis, angle, origin = rotation
            cube["pivot"] = [round(8 - origin[0], 4), origin[1], round(origin[2] - 8, 4)]
            cube["rotation"] = [angle if axis == "x" else 0, -angle if axis == "y" else 0,
                                -angle if axis == "z" else 0]
        uv = {}
        for name, (slot, uv_box, original) in faces.items():
            texture = texture_of(textures, slot)
            if texture is None:
                continue
            key = texture_key(texture)
            instance_names[key] = texture
            box = uv_box or default_uv(original, element["from"], element["to"])
            uv[name] = {"uv": [box[0], box[1]], "uv_size": [round(box[2] - box[0], 4), round(box[3] - box[1], 4)],
                        "material_instance": key}
        if uv:
            cube["uv"] = uv
            cubes.append(cube)
    return {"format_version": "1.21.0", "minecraft:geometry": [{
        "description": {"identifier": identifier, "texture_width": 16, "texture_height": 16},
        "bones": [{"name": "block", "pivot": [0, 0, 0], "cubes": cubes}]}]}


def is_cube(elements):
    if len(elements) != 1:
        return False
    element = elements[0]
    return (element["from"] == [0, 0, 0] and element["to"] == [16, 16, 16] and not element.get("rotation")
            and all(f in element.get("faces", {}) for f in FACES))


def match(condition, properties):
    if "OR" in condition:
        return any(match(c, properties) for c in condition["OR"])
    if "AND" in condition:
        return all(match(c, properties) for c in condition["AND"])
    return all(properties[k] in str(v).split("|") for k, v in condition.items())


def state_entry(blockstate, base, index):
    if "variants" in blockstate:
        entry = blockstate["variants"].get(block_bases.state_key(base, index))
        return entry[0] if isinstance(entry, list) else entry
    properties = block_bases.properties(base, index)
    for part in blockstate["multipart"]:
        if match(part.get("when", {}), properties):
            apply = part["apply"]
            return apply[0] if isinstance(apply, list) else apply
    return None


def read_json(path):
    with open(path, encoding="utf-8") as handle:
        return json.load(handle)


def write_json(path, data):
    os.makedirs(os.path.dirname(path), exist_ok=True)
    with open(path, "w", encoding="utf-8", newline="\n") as handle:
        json.dump(data, handle, indent=2, ensure_ascii=False)
        handle.write("\n")


def build(repo, names=None):
    res = os.path.join(repo, "src/main/resources")
    layer = os.path.join(res, "alexscaves/pack-layer/assets/minecraft/blockstates")
    geyser_path = os.path.join(res, "alexscaves/geyser/alexscaves-blocks.json")
    previous = read_json(geyser_path)["blocks"] if os.path.exists(geyser_path) else {}
    harvested = {}
    for definition in previous.values():
        for override in definition.get("state_overrides", {}).values():
            harvested[override["name"]] = override.get("display_name")
    models = Models(repo)
    allocation = block_bases.load_allocation(os.path.join(res, "alexscaves/blocks.yml"))
    blockstates = {base: read_json(os.path.join(layer, base + ".json")) for base in BASES}
    bedrock_textures = os.path.join(repo, "resourcepack/bedrock/textures")
    geometries, geometry_ids, used_textures = {}, {}, {}
    overrides = {base: {} for base in BASES}

    def display(name, block_id):
        return (names or {}).get(block_id) or harvested.get(name) or harvested.get(name + "_legacy") \
            or block_id.replace("_", " ").capitalize()

    def make_override(name, block_id, elements, textures, render_type, x, y, base, double_slab=False):
        instances = {}
        override = {"name": name, "display_name": display(name, block_id)}
        translucent = render_type == "translucent"
        method = "blend" if translucent else ("opaque" if base == block_bases.NOTE_BLOCK else "alpha_test")
        cross = any(e.get("rotation", {}).get("angle") == 45 for e in elements)
        shading = {} if cross or translucent else {"face_dimming": True, "ambient_occlusion": True}
        if is_cube(elements):
            _, _, _, faces = bake(elements[0], x, y)
            per_face = {}
            for face_name, (slot, uv, original) in faces.items():
                texture = texture_of(textures, slot)
                if texture:
                    per_face[face_name] = texture
                    used_textures[texture_key(texture)] = texture
            keys = set(per_face.values())
            if len(keys) == 1:
                texture = next(iter(keys))
                override["material_instances"] = {"*": dict({"texture": texture_key(texture), "render_method": method},
                                                            **shading)}
            else:
                override["material_instances"] = {
                    face_name: dict({"texture": texture_key(texture), "render_method": method}, **shading)
                    for face_name, texture in sorted(per_face.items())}
            return override
        key = "geometry." + NS + "_" + name.split("_", 1)[1] if name.startswith(NS + "_") else name
        geometry = geometry_for(key, elements, textures, x, y, instances)
        used_textures.update(instances)
        # identical geometries (the same shape in several blocks or states) share one file
        body = json.dumps(geometry["minecraft:geometry"][0]["bones"], sort_keys=True)
        digest = hashlib.sha1(body.encode()).hexdigest()
        if digest in geometry_ids:
            identifier = geometry_ids[digest]
        else:
            identifier = key
            geometry_ids[digest] = identifier
            geometry["minecraft:geometry"][0]["description"]["identifier"] = identifier
            geometries[identifier] = geometry
        override["geometry"] = identifier
        override["material_instances"] = {
            instance: dict({"texture": instance, "render_method": method}, **shading) for instance in sorted(instances)}
        return override

    skipped = []
    for block_id, variants in allocation.items():
        for variant, (base, index, legacy) in variants.items():
            entry = state_entry(blockstates[base], base, index)
            if entry is None or entry["model"] == FILLER:
                continue  # reserved, not drawn by the module any more
            try:
                elements, textures, render_type = models.resolve(entry["model"])
            except (ValueError, FileNotFoundError) as failure:
                skipped.append((block_id, variant, str(failure)))
                continue
            name = "%s_%s_%s" % (NS, block_id, re.sub(r"[^a-z0-9]+", "_", variant) or "default")
            override = make_override(name, block_id, elements, textures, render_type,
                                     entry.get("x", 0), entry.get("y", 0), base)
            overrides[base][block_bases.state_key(base, index)] = override
            if legacy is not None:
                overrides[block_bases.NOTE_BLOCK][block_bases.state_key(block_bases.NOTE_BLOCK, legacy)] = \
                    dict(override, name=name + "_legacy")

    blocks = {"minecraft:" + base: {"name": "alexscaves_" + base, "only_override_states": True,
                                    "state_overrides": dict(sorted(overrides[base].items()))} for base in BASES}
    # Smooth limestone slab: the module draws it on the (unobtainable) petrified oak slab.
    slab_states = read_json(os.path.join(layer, PETRIFIED + ".json"))["variants"]
    slab = {}
    items = read_json(os.path.join(res, "alexscaves/geyser/alexscaves-items.json"))["items"]
    slab_name = next((d["display_name"] for ds in items.values() for d in ds if d["model"] == NS + ":smooth_limestone_slab"),
                     "Smooth limestone slab")
    for state, entry in slab_states.items():
        elements, textures, render_type = models.resolve(entry["model"])
        for waterlogged in ("false", "true"):
            key = "%s,waterlogged=%s" % (state, waterlogged)
            slab[key] = make_override("%s_smooth_limestone_slab_%s%s" % (NS, state.split("=")[1],
                                                                          "_wet" if waterlogged == "true" else ""),
                                      "smooth_limestone_slab", elements, textures, render_type, 0, 0, "slab")
            slab[key]["display_name"] = slab_name
    blocks["minecraft:" + PETRIFIED] = {"name": "alexscaves_" + PETRIFIED, "only_override_states": True,
                                        "state_overrides": dict(sorted(slab.items()))}
    write_json(geyser_path, {"format_version": 1, "blocks": blocks})

    models_dir = os.path.join(repo, "resourcepack/bedrock/models/blocks")
    for stale in (os.listdir(models_dir) if os.path.isdir(models_dir) else []):
        if stale.startswith(NS + "_") and stale.endswith(".geo.json"):
            os.remove(os.path.join(models_dir, stale))
    for identifier, geometry in geometries.items():
        write_json(os.path.join(models_dir, identifier.split(".", 1)[1] + ".geo.json"), geometry)

    terrain_path = os.path.join(bedrock_textures, "terrain_texture.json")
    terrain = read_json(terrain_path)
    data = {k: v for k, v in terrain["texture_data"].items() if not k.startswith(NS + "_")}
    missing = []
    for key, ref in sorted(used_textures.items()):
        path = ref.split(":", 1)[1] if ref.startswith(NS + ":") else None
        if ref.startswith("minecraft:"):
            continue  # vanilla atlas key (see texture_key)
        if path is None or not os.path.exists(os.path.join(bedrock_textures, NS, path + ".png")):
            missing.append((key, ref))
            continue
        data[key] = {"textures": "textures/%s/%s" % (NS, path)}
    terrain["texture_data"] = dict(sorted(data.items()))
    write_json(terrain_path, terrain)
    display_items(repo)
    sound_events = sounds(repo)
    return {"geometries": len(geometries), "textures": len(used_textures), "skipped": skipped, "missing": missing,
            "overrides": {base: len(o) for base, o in overrides.items()}, "sounds": sound_events}


def display_items(repo):
    """Item mappings and icons of the item models the module's ItemDisplays wear (cave paintings, dinosaur chops).

    They are PAPER with an ``item_model`` of ``alexscaves:display/...``: mapped, Geyser (and GeyserDisplayEntity, which
    draws display entities from the mapped item) knows them. The icon is the painted face / the chop's texture.
    """
    res = os.path.join(repo, "src/main/resources")
    items_path = os.path.join(res, "alexscaves/geyser/alexscaves-items.json")
    icons_path = os.path.join(repo, "resourcepack/bedrock/textures/item_texture.alexscaves.json")
    items, icons = read_json(items_path), read_json(icons_path)
    display = os.path.join(repo, "resourcepack/java/assets", NS, "items/display")
    entries, new_icons = [], {}
    for name in sorted(os.listdir(display)):
        key = name[:-5]
        if key.startswith("cave_painting_"):
            texture = "block/cave_painting/" + key[len("cave_painting_"):]
        else:
            chop = key.rsplit("_", 1)[0]
            texture = "block/" + {"dinosaur_chop": "dinosaur_chop_raw", "cooked_dinosaur_chop": "dinosaur_chop_cooked"}[chop]
        icon = "%s_display_%s" % (NS, key)
        new_icons[icon] = {"textures": "textures/%s/%s" % (NS, texture)}
        entries.append({"type": "definition", "model": "%s:display/%s" % (NS, key),
                        "bedrock_identifier": "%s:display_%s" % (NS, key),
                        "display_name": key.replace("_", " ").capitalize(), "bedrock_options": {"icon": icon}})
    paper = [d for d in items["items"].get("minecraft:paper", []) if not d["model"].startswith(NS + ":display/")]
    items["items"]["minecraft:paper"] = paper + entries
    items["items"] = dict(sorted(items["items"].items()))
    icons["texture_data"] = dict(sorted({**{k: v for k, v in icons["texture_data"].items()
                                           if not k.startswith(NS + "_display_")}, **new_icons}.items()))
    write_json(items_path, items)
    write_json(icons_path, icons)
    return len(entries)


SOUND_FOLDERS = ("mob/", "block/", "item/", "misc/")


def sounds(repo):
    """sound_definitions entries ``alexscaves:<event>`` for the events played by name (mobs, blocks, items), with their
    files copied to ``sounds/alexscaves/``. Music and ambient loops stay Java-only: they are ~18 MB for sounds Bedrock
    clients would have to download and nothing in the module plays them to Bedrock by name."""
    import shutil
    java = os.path.join(repo, "resourcepack/java/assets", NS)
    bedrock = os.path.join(repo, "resourcepack/bedrock/sounds")
    events = read_json(os.path.join(java, "sounds.json"))
    definitions_path = os.path.join(bedrock, "sound_definitions.json")
    definitions = read_json(definitions_path)
    kept = {k: v for k, v in definitions["sound_definitions"].items() if not k.startswith(NS + ":")}
    shutil.rmtree(os.path.join(bedrock, NS), ignore_errors=True)
    added = {}
    for event, entry in sorted(events.items()):
        listed = []
        for sound in entry["sounds"]:
            data = sound if isinstance(sound, dict) else {"name": sound}
            path = data["name"].split(":", 1)[1]
            if not path.startswith(SOUND_FOLDERS):
                listed = None
                break
            target = os.path.join(bedrock, NS, path + ".ogg")
            os.makedirs(os.path.dirname(target), exist_ok=True)
            shutil.copyfile(os.path.join(java, "sounds", path + ".ogg"), target)
            item = {"name": "sounds/%s/%s" % (NS, path)}
            for key in ("volume", "pitch", "weight"):
                if key in data:
                    item[key] = data[key]
            listed.append(item)
        if listed:
            added["%s:%s" % (NS, event)] = {"category": "neutral", "sounds": listed}
    definitions["sound_definitions"] = dict(sorted({**kept, **added}.items()))
    write_json(definitions_path, definitions)
    return len(added)


if __name__ == "__main__":
    report = build(sys.argv[1] if len(sys.argv) > 1 else ".")
    print(report)
