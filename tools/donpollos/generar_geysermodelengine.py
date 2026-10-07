"""Convierte los modelos de BetterModel (.bbmodel) al formato de entrada de GeyserModelEngine, para que los jugadores
de Bedrock vean los modelos 3D.

Hace lo mismo que el plugin de Blockbench "Export GeyserModelEngine Model" (GeyserModelEngineBlockbenchPacker):
por cada modelo, una carpeta <modelo>/ con
  <modelo>.geo.json        geometria de Bedrock (geometry.<modelo>)
  <modelo>.animation.json  animaciones de Bedrock
  <textura>.png            la textura
  config.json              opciones de GeyserModelEngine
Todo junto queda en src/main/resources/donpollos/bedrock/geysermodelengine.zip; el plugin lo descomprime solo en
plugins/Geyser-Spigot/extensions/geysermodelengineextension/input/.

Conversion (como el exportador Bedrock de Blockbench): el eje X se espeja (origen del cubo = -to.x), el pivote del
hueso tambien, la rotacion fija del hueso va como [-x, -y, z], las caras de arriba y abajo invierten su UV y las
animaciones se pasan tal cual.

Uso: python tools/generar_geysermodelengine.py
"""
import base64
import io
import json
import zipfile
from pathlib import Path

ROOT = Path(__file__).resolve().parents[2]  # tools/donpollos -> raiz de BigC-Paper
FUENTES = Path(__file__).resolve().parent / "fuentes"
MODELS = ROOT / "src" / "main" / "resources" / "bettermodel" / "models"
OUT = ROOT / "src" / "main" / "resources" / "donpollos" / "bedrock" / "geysermodelengine.zip"


def num(v):
    v = round(float(v), 4)
    return int(v) if v == int(v) else v


def convert(path):
    data = json.loads(path.read_text(encoding="utf-8"))
    name = data["name"]
    texture = data["textures"][0]
    tex_w = texture.get("uv_width") or texture.get("width") or data["resolution"]["width"]
    tex_h = texture.get("uv_height") or texture.get("height") or data["resolution"]["height"]
    png = base64.b64decode(texture["source"].split(",", 1)[1])
    texture_name = Path(texture.get("name") or f"{name}.png").stem

    groups = {g["uuid"]: g for g in data["groups"]}
    elements = {e["uuid"]: e for e in data["elements"]}
    bones = []
    lo, hi = [1e9, 1e9, 1e9], [-1e9, -1e9, -1e9]

    def walk(node, parent_name):
        group = groups[node["uuid"]]
        origin = group["origin"]
        bone = {"name": group["name"], "pivot": [num(-origin[0]), num(origin[1]), num(origin[2])]}
        if parent_name:
            bone["parent"] = parent_name
        rotation = group.get("rotation") or [0, 0, 0]
        if any(rotation):
            bone["rotation"] = [num(-rotation[0]), num(-rotation[1]), num(rotation[2])]
        cubes = []
        for child in node["children"]:
            if isinstance(child, str):
                el = elements[child]
                (x1, y1, z1), (x2, y2, z2) = el["from"], el["to"]
                uv = {}
                for face, info in el["faces"].items():
                    u1, v1, u2, v2 = info["uv"]
                    if face in ("up", "down"):
                        uv[face] = {"uv": [num(u2), num(v2)], "uv_size": [num(u1 - u2), num(v1 - v2)]}
                    else:
                        uv[face] = {"uv": [num(u1), num(v1)], "uv_size": [num(u2 - u1), num(v2 - v1)]}
                cubes.append({"origin": [num(-x2), num(y1), num(z1)],
                              "size": [num(x2 - x1), num(y2 - y1), num(z2 - z1)], "uv": uv})
                for i, (a, b) in enumerate(((x1, x2), (y1, y2), (z1, z2))):
                    lo[i] = min(lo[i], a)
                    hi[i] = max(hi[i], b)
        if cubes:
            bone["cubes"] = cubes
        bones.append(bone)
        for child in node["children"]:
            if not isinstance(child, str):
                walk(child, group["name"])

    for root in data["outliner"]:
        walk(root, None)

    width = max(abs(lo[0]), abs(hi[0]), abs(lo[2]), abs(hi[2])) * 2 / 16 + 1
    height = max(hi[1], 1) / 16 + 1
    geometry = {
        "format_version": "1.12.0",
        "minecraft:geometry": [{
            "description": {
                "identifier": f"geometry.{name}",
                "texture_width": tex_w,
                "texture_height": tex_h,
                "visible_bounds_width": num(width),
                "visible_bounds_height": num(height),
                "visible_bounds_offset": [0, num(height / 2), 0],
            },
            "bones": bones,
        }],
    }

    animations = {}
    for anim in data.get("animations", []):
        anim_bones = {}
        for animator in anim.get("animators", {}).values():
            channels = {}
            for kf in animator.get("keyframes", []):
                dp = kf["data_points"][0]
                value = [num(dp["x"]), num(dp["y"]), num(dp["z"])]
                if kf.get("interpolation") == "catmullrom":
                    value = {"post": value, "lerp_mode": "catmullrom"}
                channels.setdefault(kf["channel"], {})[str(num(kf["time"]))] = value
            if channels:
                anim_bones[animator["name"]] = channels
        entry = {"animation_length": num(anim["length"]), "bones": anim_bones}
        if anim.get("loop") == "loop":
            entry["loop"] = True
        animations[anim["name"]] = entry

    config = {
        "head_rotation": True,
        "material": "entity_alphatest_change_color_one_sided",
        "blend_transition": True,
        "per_texture_uv_size": {texture_name: [tex_w, tex_h]},
        "binding_bones": {},
        "anim_textures": {},
    }
    files = {
        f"{name}/{name}.geo.json": json.dumps(geometry, indent=1),
        f"{name}/config.json": json.dumps(config, indent=1),
        f"{name}/{texture_name}.png": png,
    }
    if animations or any(b["name"].startswith(("h_", "hi_")) for b in bones):
        files[f"{name}/{name}.animation.json"] = json.dumps(
            {"format_version": "1.8.0", "animations": animations}, indent=1)
    return name, files, len(bones), len(animations)


def main():
    OUT.parent.mkdir(parents=True, exist_ok=True)
    buf = io.BytesIO()
    with zipfile.ZipFile(buf, "w", zipfile.ZIP_DEFLATED) as z:
        for path in sorted(MODELS.glob("bigcasares_*.bbmodel")):  # solo los de Don Pollos
            name, files, bone_count, anim_count = convert(path)
            for file_name, content in sorted(files.items()):
                info = zipfile.ZipInfo(file_name, date_time=(2026, 1, 1, 0, 0, 0))
                info.compress_type = zipfile.ZIP_DEFLATED
                z.writestr(info, content if isinstance(content, bytes) else content.encode("utf-8"))
            print("ok", name, bone_count, "huesos,", anim_count, "animaciones")
    OUT.write_bytes(buf.getvalue())
    print("ok", OUT.name, f"{len(buf.getvalue()) // 1024} KB")


if __name__ == "__main__":
    main()
