#!/usr/bin/env python3
"""Convert a GeckoLib (Bedrock geometry + animation) entity model from a mod jar
into a Blockbench .bbmodel that BetterModel can load.

Usage:
    python3 -I tools/convert_geckolib_bbmodel.py <mod.jar> <entity> <texture> <output-name>
        [--rename geckolib=bbmodel ...] [--loop name=seconds ...]

Examples (The Sift, see docs/dimensions/the-sift.md):
    python3 -I tools/convert_geckolib_bbmodel.py mielons-the-sift-1.1.1-mc26.3-fabric.jar \\
        blub blub the_sift_blub --rename hopping=walk --rename flapping=idle --loop idle=3.0
    python3 -I tools/convert_geckolib_bbmodel.py mielons-the-sift-1.1.1-mc26.3-fabric.jar \\
        blub tamed_blub the_sift_blub_tamed --rename hopping=walk --rename flapping=idle --loop idle=3.0
    python3 -I tools/convert_geckolib_bbmodel.py mielons-the-sift-1.1.1-mc26.3-fabric.jar \\
        singer singer the_sift_singer
    python3 -I tools/convert_geckolib_bbmodel.py mielons-the-sift-1.1.1-mc26.3-fabric.jar \\
        rift rift/sift the_sift_rift
    python3 -I tools/convert_geckolib_bbmodel.py mielons-the-sift-1.1.1-mc26.3-fabric.jar \\
        rift rift/overworld the_sift_rift_overworld

The jar is read as a ZIP archive; no mod code is loaded. The conversion mirrors
Blockbench's own Bedrock import: X is negated for cube bounds and pivots, bone
and cube rotations negate X and Y, box UVs become per-face UVs, and animation
keyframe values are copied unchanged (Blockbench and BetterModel apply the
Bedrock axis convention when playing them). GeckoLib easings have no bbmodel
equivalent and become linear. UUIDs are derived from names, so output is
reproducible.
"""
import base64
import json
import struct
import sys
import uuid
import zipfile
from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]
OUTPUT = ROOT / "src/main/resources/bettermodel/models"
NAMESPACE = uuid.UUID("5f2b1111-0000-4000-8000-000000005157")
LOOP_MODES = {True: "loop", "loop": "loop", "hold_on_last_frame": "hold", False: "once", None: "once"}


def uid(model, label):
    return str(uuid.uuid5(NAMESPACE, model + "/" + label))


def number(value):
    return int(value) if float(value).is_integer() else round(float(value), 5)


def box_uv_faces(u, v, w, h, d, mirror):
    """Blockbench's box-UV layout, as explicit per-face rectangles."""
    layout = {
        "north": (d, d, w, h),
        "east": (0, d, d, h),
        "south": (d + w + d, d, w, h),
        "west": (d + w, d, d, h),
        "up": (d + w, d, -w, -d),
        "down": (d + w + w, 0, -w, d),
    }
    faces = {}
    for face, (fx, fy, sx, sy) in layout.items():
        x1, y1, x2, y2 = u + fx, v + fy, u + fx + sx, v + fy + sy
        if mirror and face in ("north", "south", "up", "down"):
            x1, x2 = x2, x1
        faces[face] = [number(x1), number(y1), number(x2), number(y2)]
    if mirror:
        faces["east"], faces["west"] = faces["west"], faces["east"]
        for face in ("east", "west"):
            x1, y1, x2, y2 = faces[face]
            faces[face] = [x2, y1, x1, y2]
    return faces


def per_face_uv(uv):
    faces = {}
    for face, entry in uv.items():
        (u, v), (w, h) = entry["uv"], entry["uv_size"]
        faces[face] = [number(u), number(v), number(u + w), number(v + h)]
    return faces


def convert_geometry(model, geometry):
    bones = geometry["bones"]
    elements, groups, children = [], {}, {}
    for bone in bones:
        name = bone["name"]
        pivot = bone.get("pivot", [0, 0, 0])
        rotation = bone.get("rotation", [0, 0, 0])
        groups[name] = {
            "name": name, "uuid": uid(model, "bone/" + name), "export": True,
            "origin": [number(-pivot[0]), number(pivot[1]), number(pivot[2])],
            "rotation": [number(-rotation[0]), number(-rotation[1]), number(rotation[2])],
            "children": [], "visibility": True, "shade": True, "mirror_uv": bool(bone.get("mirror", False)),
        }
        children.setdefault(bone.get("parent"), []).append(name)
        for index, cube in enumerate(bone.get("cubes", [])):
            origin, size = cube["origin"], cube["size"]
            inflate = cube.get("inflate", 0)
            start = [-(origin[0] + size[0]), origin[1], origin[2]]
            end = [start[0] + size[0], start[1] + size[1], start[2] + size[2]]
            uv = cube.get("uv", [0, 0])
            if isinstance(uv, dict):
                faces = per_face_uv(uv)
            else:
                mirror = cube.get("mirror", bone.get("mirror", False))
                faces = box_uv_faces(uv[0], uv[1], int(size[0]), int(size[1]), int(size[2]), mirror)
            cube_pivot = cube.get("pivot", pivot)
            cube_rotation = cube.get("rotation", [0, 0, 0])
            identifier = uid(model, f"cube/{name}/{index}")
            element = {
                "name": f"{name}_{index}", "type": "cube", "uuid": identifier,
                "from": [number(value) for value in start], "to": [number(value) for value in end],
                "origin": [number(-cube_pivot[0]), number(cube_pivot[1]), number(cube_pivot[2])],
                "faces": {face: {"uv": rect, "texture": 0} for face, rect in faces.items()},
                "box_uv": False, "export": True, "autouv": 0, "render_order": "default", "color": 0,
            }
            if inflate:
                element["inflate"] = number(inflate)
            if any(cube_rotation):
                element["rotation"] = [number(-cube_rotation[0]), number(-cube_rotation[1]), number(cube_rotation[2])]
            elements.append(element)
            groups[name]["children"].append(identifier)
    for parent, names in children.items():
        if parent is not None:
            groups[parent]["children"].extend(groups[name]["uuid"] for name in names)

    def tree(name):
        group = groups[name]
        nested = {groups[child]["uuid"]: child for child in children.get(name, [])}
        return {"uuid": group["uuid"], "isOpen": True,
                "children": [tree(nested[child]) if child in nested else child for child in group["children"]]}

    outliner = [tree(name) for name in children.get(None, [])]
    return elements, groups, outliner


def keyframes(channel_value):
    """Yields (time, vector) from the GeckoLib/Bedrock channel forms."""
    if isinstance(channel_value, (list, int, float, str)):
        yield 0.0, channel_value
        return
    if "vector" in channel_value:
        yield 0.0, channel_value["vector"]
        return
    for time, frame in sorted(channel_value.items(), key=lambda item: float(item[0])):
        if isinstance(frame, dict):
            frame = frame.get("vector", frame.get("post", frame.get("pre")))
            if isinstance(frame, dict):
                frame = frame.get("vector", [0, 0, 0])
        yield float(time), frame


def vector(value):
    if not isinstance(value, list):
        value = [value, value, value]
    return {axis: str(number(component)) if isinstance(component, (int, float)) else str(component)
            for axis, component in zip("xyz", value)}


def convert_animations(model, animations, groups, renames, loops):
    converted = []
    for source_name, animation in animations.items():
        name = renames.get(source_name, source_name)
        length = float(animation.get("animation_length", 0.0))
        loop = LOOP_MODES.get(animation.get("loop"), "once")
        if name in loops:
            length, loop = max(length, loops[name]), "loop"
        animators = {}
        for bone, channels in animation.get("bones", {}).items():
            if bone not in groups:
                raise ValueError(f"{source_name} animates unknown bone {bone}")
            frames = []
            for channel in ("rotation", "position", "scale"):
                if channel not in channels:
                    continue
                for index, (time, value) in enumerate(keyframes(channels[channel])):
                    frames.append({
                        "channel": channel, "time": number(time), "data_points": [vector(value)],
                        "uuid": uid(model, f"frame/{name}/{bone}/{channel}/{index}"),
                        "color": -1, "interpolation": "linear",
                    })
            animators[groups[bone]["uuid"]] = {
                "name": bone, "type": "bone", "rotation_global": False,
                "quaternion_interpolation": False, "keyframes": frames,
            }
        converted.append({"name": name, "uuid": uid(model, "animation/" + name), "length": number(length),
                          "loop": loop, "override": False, "snapping": 24, "animators": animators})
    return converted


def main(arguments):
    renames, loops, positional = {}, {}, []
    iterator = iter(arguments)
    for argument in iterator:
        if argument == "--rename":
            source, target = next(iterator).split("=", 1)
            renames[source] = target
        elif argument == "--loop":
            name, seconds = next(iterator).split("=", 1)
            loops[name] = float(seconds)
        else:
            positional.append(argument)
    if len(positional) != 4:
        print(__doc__)
        return 2
    jar, entity, texture_name, model = positional
    with zipfile.ZipFile(jar) as archive:
        geo = json.loads(archive.read(f"assets/the_sift/geckolib/models/entity/{entity}.geo.json"))
        animation_file = f"assets/the_sift/geckolib/animations/entity/{entity}.animation.json"
        animations = json.loads(archive.read(animation_file))["animations"] if animation_file in archive.namelist() else {}
        texture = archive.read(f"assets/the_sift/textures/entity/{texture_name}.png")
    geometry = geo["minecraft:geometry"][0]
    width, height = struct.unpack(">II", texture[16:24])
    description = geometry["description"]
    # UVs stay in the geometry's UV space whatever the image size (Blockbench's
    # uv_width/uv_height), as GeckoLib maps them: HD and non-square images both work.
    uv_width, uv_height = description["texture_width"], description["texture_height"]
    elements, groups, outliner = convert_geometry(model, geometry)
    bbmodel = {
        "meta": {"format_version": "5.0", "model_format": "free", "box_uv": False},
        "name": model, "model_identifier": model, "visible_box": [2, 2.5, 0.75],
        "resolution": {"width": uv_width, "height": uv_height},
        "elements": elements, "groups": list(groups.values()), "outliner": outliner,
        "animations": convert_animations(model, animations, groups, renames, loops),
        "textures": [{
            "name": model + ".png", "relative_path": model + ".png", "id": "0", "uuid": uid(model, "texture"),
            "width": width, "height": height, "uv_width": uv_width, "uv_height": uv_height,
            "internal": True, "saved": True, "frame_time": 1, "frame_interpolate": False,
            "visible": True, "render_mode": "default", "render_sides": "front",
            "source": "data:image/png;base64," + base64.b64encode(texture).decode("ascii"),
        }],
    }
    OUTPUT.mkdir(parents=True, exist_ok=True)
    (OUTPUT / (model + ".bbmodel")).write_text(json.dumps(bbmodel, indent=2) + "\n", encoding="utf-8")
    print(f"{model}: {len(elements)} cubes, {len(groups)} bones, "
          f"animations {[animation['name'] for animation in bbmodel['animations']]}")
    return 0


if __name__ == "__main__":
    sys.exit(main(sys.argv[1:]))
