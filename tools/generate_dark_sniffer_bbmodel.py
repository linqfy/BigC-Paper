#!/usr/bin/env python3
"""Generate the Dark Sniffer's BetterModel model: the vanilla sniffer model with the
mod's Dark Sniffer texture.

Usage:
    python3 -I tools/generate_dark_sniffer_bbmodel.py <mod.jar>

The mod's Dark Sniffer is a vanilla sniffer drawn with its own texture, so it has no GeckoLib
geometry to convert (see tools/convert_geckolib_bbmodel.py). This script builds the model from
two checked-in descriptions instead:

    tools/sift/sniffer_parts.json              the vanilla sniffer's parts, cubes and texture offsets,
                                               in the numbers of SnifferModel.createBodyLayer
    tools/sift/dark_sniffer_animations.json    the animations (authored for this port, in the
                                               Bedrock/GeckoLib animation format)

and from assets/the_sift/textures/entity/dark_sniffer.png in the mod jar (the texture is
ImpSteve's, credited by the mod; the jar is read as a ZIP archive and no mod code is loaded).

Vanilla model space has y pointing down, the ground at y = 24 and the front at -z; the renderer
mirrors x. The parts are first turned into the Bedrock geometry the existing converter reads
(pivot = (x, 24 - y, z), cube origin = (x, 24 - (y + height), z)), then converted with the same
functions, so the output has the same shape and conventions as the other Sift models and is
reproducible (UUIDs are derived from names).
"""
import base64
import importlib.util
import json
import struct
import sys
import zipfile
from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]
PARTS = ROOT / "tools/sift/sniffer_parts.json"
ANIMATIONS = ROOT / "tools/sift/dark_sniffer_animations.json"
TEXTURE = "assets/the_sift/textures/entity/dark_sniffer.png"
MODEL = "the_sift_dark_sniffer"
GROUND = 24


def load_converter():
    """The GeckoLib converter's geometry and animation functions (it is a script, not a package)."""
    path = ROOT / "tools/convert_geckolib_bbmodel.py"
    spec = importlib.util.spec_from_file_location("convert_geckolib_bbmodel", path)
    module = importlib.util.module_from_spec(spec)
    spec.loader.exec_module(module)
    return module


def number(value):
    return int(value) if float(value).is_integer() else round(float(value), 5)


def bedrock_geometry(description):
    """Vanilla model parts to a Bedrock geometry: absolute pivots, y up, cubes as origin and size."""
    absolute = {}
    bones = []
    for part in description["parts"]:
        parent = part.get("parent")
        base = absolute[parent] if parent else (0.0, 0.0, 0.0)
        offset = part["offset"]
        here = (base[0] + offset[0], base[1] + offset[1], base[2] + offset[2])
        absolute[part["name"]] = here
        bone = {"name": part["name"], "pivot": [number(here[0]), number(GROUND - here[1]), number(here[2])]}
        if parent:
            bone["parent"] = parent
        cubes = []
        for cube in part.get("cubes", []):
            x, y, z, width, height, depth = cube["box"]
            entry = {
                "origin": [number(here[0] + x), number(GROUND - (here[1] + y + height)), number(here[2] + z)],
                "size": [number(width), number(height), number(depth)],
                "uv": cube["texOffs"],
            }
            if cube.get("deform"):
                entry["inflate"] = cube["deform"]
            cubes.append(entry)
        if cubes:
            bone["cubes"] = cubes
        bones.append(bone)
    texture = description["texture"]
    return {
        "description": {"identifier": "geometry.sniffer", "texture_width": texture["width"],
                        "texture_height": texture["height"]},
        "bones": bones,
    }


def drop_degenerate_faces(elements):
    """A cube with no thickness along an axis (the sniffer's belly and mouth planes) only shows the two faces
    across that axis; the others have no area and, in box UV layout, reach past the texture. They stay in
    the cube without a texture: BetterModel expects all six faces of a cube."""
    across = {0: ("east", "west"), 1: ("up", "down"), 2: ("north", "south")}
    for element in elements:
        for axis, faces in across.items():
            if element["from"][axis] == element["to"][axis]:
                element["faces"] = {name: (face if name in faces else {"uv": [0, 0, 0, 0], "texture": None})
                                    for name, face in element["faces"].items()}


def main(arguments):
    if len(arguments) != 1:
        print(__doc__)
        return 2
    converter = load_converter()
    with zipfile.ZipFile(arguments[0]) as archive:
        texture = archive.read(TEXTURE)
    width, height = struct.unpack(">II", texture[16:24])
    geometry = bedrock_geometry(json.loads(PARTS.read_text(encoding="utf-8")))
    description = geometry["description"]
    elements, groups, outliner = converter.convert_geometry(MODEL, geometry)
    drop_degenerate_faces(elements)
    animations = json.loads(ANIMATIONS.read_text(encoding="utf-8"))["animations"]
    bbmodel = {
        "meta": {"format_version": "5.0", "model_format": "free", "box_uv": False},
        "name": MODEL, "model_identifier": MODEL, "visible_box": [3, 2.5, 1],
        "resolution": {"width": description["texture_width"], "height": description["texture_height"]},
        "elements": elements, "groups": list(groups.values()), "outliner": outliner,
        "animations": converter.convert_animations(MODEL, animations, groups, {}, {}, {}),
        "textures": [{
            "name": MODEL + ".png", "relative_path": MODEL + ".png", "id": "0",
            "uuid": converter.uid(MODEL, "texture"), "width": width, "height": height,
            "uv_width": description["texture_width"], "uv_height": description["texture_height"],
            "internal": True, "saved": True, "frame_time": 1, "frame_interpolate": False,
            "visible": True, "render_mode": "default", "render_sides": "front",
            "source": "data:image/png;base64," + base64.b64encode(texture).decode("ascii"),
        }],
    }
    output = converter.OUTPUT
    output.mkdir(parents=True, exist_ok=True)
    (output / (MODEL + ".bbmodel")).write_text(json.dumps(bbmodel, indent=2) + "\n", encoding="utf-8")
    print(f"{MODEL}: {len(elements)} cubes, {len(groups)} bones, "
          f"animations {[animation['name'] for animation in bbmodel['animations']]}")
    return 0


if __name__ == "__main__":
    sys.exit(main(sys.argv[1:]))
