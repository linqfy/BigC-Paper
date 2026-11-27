"""Genera los .bbmodel de los bloques del mod que el módulo dibuja como modelos de BetterModel: el barril de residuos que
lanza el Brainiac y los huevos de dinosaurio (un modelo por especie y etapa de eclosión).

Uso manual (no corre en el build):
    python -I tools/alexscaves/block_model_to_bbmodel.py <alexscaves-2.0.2.jar> <repo>

Lee los modelos JSON de bloque del JAR (assets/alexscaves/models/block), resuelve sus "parent" (el único padre de
Minecraft que usan es block/orientable_with_bottom, copiado abajo; block/block no aporta elementos), reúne todas las
texturas que tocan las caras en UN atlas (un bbmodel y Bedrock solo admiten una textura) y escribe un cubo de
Blockbench por elemento. Las caras con "rotation" o UV invertido se hornean en el atlas (se recorta, se voltea y se gira
el trozo de textura) para que el UV final sea un rectángulo normal: ni BetterModel ni la conversión a Bedrock leen la
rotación de cara. El modelo queda centrado en X/Z (el bloque va de -8 a 8) con los pies en Y = 0.

Las etapas de grietas son un bbmodel por etapa (<especie>_egg, _egg_cracked, _egg_very_cracked): la pasarela de
modelos no puede ocultar huesos, así que el módulo cambia de etapa cerrando un modelo y abriendo el siguiente.
"""
import base64
import io
import json
import math
import os
import sys
import zipfile

from PIL import Image

sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))
import citadel_to_bbmodel as cb  # noqa: E402

BLOCK_MODELS = "assets/alexscaves/models/block/"
TEXTURES = "assets/alexscaves/textures/"
FACES = ["north", "east", "south", "west", "up", "down"]
STAGES = ("", "_cracked", "_very_cracked")
GLOW_INFLATE = 0.02

# net/minecraft block/orientable_with_bottom: one full cube; the front is north, the sides are the other three.
ORIENTABLE_WITH_BOTTOM = {
    "elements": [{
        "from": [0, 0, 0], "to": [16, 16, 16],
        "faces": {
            "down": {"uv": [0, 0, 16, 16], "texture": "#bottom"},
            "up": {"uv": [0, 0, 16, 16], "texture": "#top"},
            "north": {"uv": [0, 0, 16, 16], "texture": "#front"},
            "south": {"uv": [0, 0, 16, 16], "texture": "#side"},
            "west": {"uv": [0, 0, 16, 16], "texture": "#side"},
            "east": {"uv": [0, 0, 16, 16], "texture": "#side"},
        },
    }],
}
VANILLA_PARENTS = {"minecraft:block/orientable_with_bottom": ORIENTABLE_WITH_BOTTOM,
                   "block/block": {}, "minecraft:block/block": {}}


def specs():
    """(model key, block model, glow bone name or None) for everything the module draws."""
    out = [("alexscaves_waste_drum", "waste_drum")]
    for kind in ("atlatitan", "grottoceratops", "relicheirus", "tremorsaurus"):
        out += [("alexscaves_%s_egg%s" % (kind, stage), "%s_egg%s" % (kind, stage)) for stage in STAGES]
    # A cluster of 1-4 eggs in the mod; the module tracks one egg per placed item, so the 1-egg model.
    for kind in ("subterranodon", "vallumraptor"):
        out += [("alexscaves_%s_egg%s" % (kind, stage), "%s_egg_1%s" % (kind, stage)) for stage in STAGES]
    out.append(("alexscaves_tremorzilla_egg", "tremorzilla_egg"))
    return out


def resolve(jar, name):
    """Merged {"elements": [...], "textures": {...}} of a block model, following its parents."""
    chain = []
    current = name
    while current is not None:
        if current in VANILLA_PARENTS:
            chain.append(VANILLA_PARENTS[current])
            break
        short = current.split(":", 1)[-1]
        path = BLOCK_MODELS + short.replace("block/", "", 1) + ".json"
        # Some of the mod's files end with a stray comma after the object: read just the first value.
        data = json.JSONDecoder().raw_decode(jar.read(path).decode("utf-8").lstrip())[0]
        chain.append(data)
        current = data.get("parent")
    textures, elements = {}, []
    for data in reversed(chain):
        textures.update(data.get("textures", {}))
        if data.get("elements"):
            elements = data["elements"]
    return {"elements": elements, "textures": textures}


def texture_path(ref, textures):
    """Resolves '#side' (and the bare 'side' one of the mod's models has) down to an asset path."""
    seen = 0
    while seen < 16:
        key = ref[1:] if ref.startswith("#") else ref
        if ref.startswith("#") or key in textures:
            if key not in textures:
                raise KeyError(ref)
            ref = textures[key]
            seen += 1
            continue
        break
    name = ref.split(":", 1)[-1]
    return TEXTURES + name + ".png"


def face_tile(image, uv, rotation):
    """The part of a texture a face shows, flipped and turned like Minecraft does, as a picture of its own."""
    sx = image.width / 16.0
    sy = image.height / 16.0
    u1, v1, u2, v2 = uv
    box = (round(min(u1, u2) * sx), round(min(v1, v2) * sy), round(max(u1, u2) * sx), round(max(v1, v2) * sy))
    if box[2] <= box[0] or box[3] <= box[1]:
        raise ValueError("empty face uv %s" % (uv,))
    tile = image.crop(box)
    if u1 > u2:
        tile = tile.transpose(Image.FLIP_LEFT_RIGHT)
    if v1 > v2:
        tile = tile.transpose(Image.FLIP_TOP_BOTTOM)
    # Face rotation turns the picture clockwise on the face.
    turns = (rotation // 90) % 4
    for _ in range(turns):
        tile = tile.transpose(Image.ROTATE_270)
    return tile


class Atlas:
    def __init__(self):
        self.tiles = {}   # key -> Image
        self.order = []

    def add(self, key, tile):
        if key not in self.tiles:
            self.tiles[key] = tile
            self.order.append(key)
        return key

    def pack(self, max_width=64, gap=1):
        """Shelf packing; returns ({key: (x, y)}, width, height) with power-of-two sides."""
        keys = sorted(self.order, key=lambda k: (-self.tiles[k].height, -self.tiles[k].width, k))
        positions = {}
        x = y = row = 0
        used_w = 0
        for key in keys:
            tile = self.tiles[key]
            if x and x + tile.width > max_width:
                x, y, row = 0, y + row + gap, 0
            positions[key] = (x, y)
            x += tile.width + gap
            row = max(row, tile.height)
            used_w = max(used_w, x - gap)
        used_h = y + row

        def pow2(n):
            return max(16, 1 << (max(n, 1) - 1).bit_length())
        return positions, pow2(used_w), pow2(used_h)

    def image(self, positions, width, height):
        atlas = Image.new("RGBA", (width, height), (0, 0, 0, 0))
        for key, (x, y) in positions.items():
            atlas.paste(self.tiles[key], (x, y))
        return atlas


def build(jar, key, block_model):
    model = resolve(jar, "alexscaves:block/" + block_model)
    images = {}
    atlas = Atlas()
    prepared = []
    for element in model["elements"]:
        faces = {}
        for face in FACES:
            data = element["faces"].get(face)
            if data is None:
                faces[face] = None
                continue
            path = texture_path(data["texture"], model["textures"])
            if path not in images:
                images[path] = Image.open(io.BytesIO(jar.read(path))).convert("RGBA")
            uv = data.get("uv") or [0, 0, 16, 16]
            rotation = int(data.get("rotation", 0))
            tile_key = (path, tuple(uv), rotation)
            atlas.add(tile_key, face_tile(images[path], uv, rotation))
            faces[face] = tile_key
        prepared.append((element, faces))
    positions, width, height = atlas.pack()
    png_bytes = io.BytesIO()
    atlas.image(positions, width, height).save(png_bytes, "PNG")
    png = png_bytes.getvalue()

    elements = []
    root_children, glow_children = [], []
    for index, (element, faces) in enumerate(prepared):
        glow = element.get("shade") is False
        eid = cb.stable_uuid(key, "el", str(index))
        origin = [0.0, 0.0, 0.0]
        rotation = [0.0, 0.0, 0.0]
        spin = element.get("rotation")
        if spin and spin.get("angle", 0):
            origin = [spin["origin"][0] - 8.0, spin["origin"][1], spin["origin"][2] - 8.0]
            rotation["xyz".index(spin["axis"])] = float(spin["angle"])
        face_out = {}
        for face in FACES:
            tile_key = faces[face]
            if tile_key is None:
                face_out[face] = {"uv": [0, 0, 0, 0], "texture": None}
                continue
            x, y = positions[tile_key]
            tile = atlas.tiles[tile_key]
            face_out[face] = {"uv": [x, y, x + tile.width, y + tile.height], "texture": 0}
        elements.append({
            "name": "%s_%d" % ("glow" if glow else "block", index), "type": "cube", "uuid": eid, "box_uv": False,
            "rescale": False, "locked": False, "render_order": "default", "allow_mirror_modeling": True,
            "from": [element["from"][0] - 8.0, element["from"][1], element["from"][2] - 8.0],
            "to": [element["to"][0] - 8.0, element["to"][1], element["to"][2] - 8.0],
            "autouv": 0, "color": 0, "inflate": GLOW_INFLATE if glow else 0.0, "origin": origin,
            "rotation": rotation, "faces": face_out, "export": True, "visibility": True, "shade": not glow,
            "mirror_uv": False, "light_emission": 15 if glow else 0,
        })
        (glow_children if glow else root_children).append(eid)

    root_id = cb.stable_uuid(key, "group", "root")
    groups = [{"name": "root", "uuid": root_id, "export": True, "locked": False, "scope": 0, "origin": [0, 0, 0],
               "rotation": [0, 0, 0], "color": 0, "reset": False, "shade": True, "mirror_uv": False,
               "visibility": True, "autouv": 0, "isOpen": True}]
    children = list(root_children)
    if glow_children:
        glow_id = cb.stable_uuid(key, "group", "glow")
        groups.append({"name": "glow", "uuid": glow_id, "export": True, "locked": False, "scope": 0,
                       "origin": [0, 0, 0], "rotation": [0, 0, 0], "color": 0, "reset": False, "shade": True,
                       "mirror_uv": False, "visibility": True, "autouv": 0, "isOpen": True})
        children.append({"uuid": glow_id, "isOpen": True, "children": glow_children})
    return {
        "meta": {"format_version": "5.0", "model_format": "free", "box_uv": False},
        "name": key, "model_identifier": "", "visible_box": [1, 1, 0],
        "variable_placeholders": "", "variable_placeholder_buttons": [], "timeline_setups": [],
        "unhandled_root_fields": {}, "resolution": {"width": width, "height": height},
        "elements": elements, "groups": groups, "outliner": [{"uuid": root_id, "isOpen": True, "children": children}],
        "textures": [{
            "path": "", "name": key + ".png", "folder": "", "namespace": "", "id": "0", "group": "",
            "width": width, "height": height, "uv_width": width, "uv_height": height, "particle": False,
            "use_as_default": False, "layers_enabled": False, "sync_to_project": "", "render_mode": "default",
            "render_sides": "auto", "pbr_channel": "color", "frame_time": 1, "frame_order_type": "loop",
            "frame_order": "", "frame_interpolate": False, "visible": True, "internal": True, "saved": True,
            "uuid": cb.stable_uuid(key, "texture"), "relative_path": key + ".png",
            "source": "data:image/png;base64," + base64.b64encode(png).decode(),
        }],
        "animations": [],
    }


def main(argv):
    if len(argv) != 3:
        print(__doc__)
        return 2
    target_dir = os.path.join(argv[2], "src/main/resources/bettermodel/models")
    with zipfile.ZipFile(argv[1]) as jar:
        for key, block_model in specs():
            data = build(jar, key, block_model)
            target = os.path.join(target_dir, key + ".bbmodel")
            with open(target, "w", encoding="utf-8", newline="\n") as handle:
                json.dump(data, handle, separators=(",", ":"))
                handle.write("\n")
            print(target, len(data["elements"]), "cubos", data["resolution"])
    return 0


if __name__ == "__main__":
    sys.exit(main(sys.argv))
