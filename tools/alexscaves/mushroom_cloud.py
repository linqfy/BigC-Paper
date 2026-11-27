"""Genera alexscaves_mushroom_cloud.bbmodel: la nube de hongo de la explosión nuclear del mod.

Uso manual (no corre en el build):
    python -I tools/alexscaves/mushroom_cloud.py <alexscaves-2.0.2.jar> <repo>

En el mod la nube es una partícula del cliente (MushroomCloudParticle) que dibuja MushroomCloudModel; aquí es un
modelo de BetterModel con una animación por tamaño de explosión, muestreada tick a tick con las fórmulas de
MushroomCloudModel.animateParticle y del render de la partícula:

- dura ceil(133.33 * tamaño) ticks y se dibuja a escala tamaño + 0.2 (la escala la pone el plugin);
- los 10 primeros ticks solo se ve la bola de fuego (hideFireball);
- progreso = smin(2 * ln(1 + (edad - 10) / (duración - 10)), 1, 0.5);
- columna (1, 1.5p, 1), nube de arriba clamp(p + 0.3, 0.4, 1.5), nube de abajo p + p², anillos
  clamp(p, 0, 0.5) * 45, clamp(p, 0.1, 0.65) * 20 y clamp(p, 0.2, 0.65) * 15, girando 0.1, -0.2 y 0.1 rad por tick.

La geometría (cajas, pivotes, rotaciones y UV de MushroomCloudModel) está copiada abajo como datos; la textura se
lee del JAR del mod.
"""
import base64
import json
import math
import os
import sys
import zipfile

sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))
import citadel_to_bbmodel as cb  # noqa: E402

MODEL_KEY = "alexscaves_mushroom_cloud"
TEXTURE = "assets/alexscaves/textures/particle/mushroom_cloud.png"
TEX_W = TEX_H = 512
# Tamaños de explosión del módulo (NukeMath): Nucleeper, Nucleeper cargado y bomba nuclear.
SIZES = (1.0, 1.75, 9.0)
Q = 0.7854

# name, parent, pivot, rotation (rad), texture offset, boxes [(x, y, z, w, h, d, mirror)]
PARTS = [
    ("mushroom_cloud", None, (0, 24, 0), (0, 0, 0), None, []),
    ("lightBall", "mushroom_cloud", (0, -19, 0), (0, 0, 0), (300, 0), [(-16, -16, -16, 32, 32, 32, False)]),
    ("lowerCloud", "lightBall", (0, 5, 0), (0, 0, 0), (0, 0), [(-50, -9, -50, 100, 16, 100, False)]),
    ("lowerCloud_planes", "lowerCloud", (0, 0, 0), (0, 0, 0), None, []),
    ("cube_r1", "lowerCloud_planes", (-16, -20, 16), (0, Q, 0), (142, 342), [(-66, -13, 0, 66, 40, 0, True)]),
    ("cube_r2", "lowerCloud_planes", (16, -20, -16), (0, Q, 0), (142, 342), [(0, -13, 0, 66, 40, 0, False)]),
    ("cube_r3", "lowerCloud_planes", (16, -20, 16), (0, -Q, 0), (142, 342), [(0, -13, 0, 66, 40, 0, False)]),
    ("cube_r4", "lowerCloud_planes", (-16, -20, -16), (0, -Q, 0), (142, 342), [(-66, -13, 0, 66, 40, 0, True)]),
    ("plume", "lightBall", (0, 9, 0), (0, 0, 0), None, []),
    ("cube_r5", "plume", (0, -44.8333, 0), (0, -Q, 0), (256, 220), [(-32, -61, 0, 64, 122, 0, False)]),
    ("cube_r6", "plume", (0, -44.8333, 0), (0, Q, 0), (256, 220), [(-32, -61, 0, 64, 122, 0, False)]),
    ("plumeBlast", "plume", (0, -80.8333, 0), (0, 0, 0), (240, 116), [(-18, 0, -18, 36, 29, 36, False)]),
    ("plumeRing", "plume", (0, -35.8333, 0), (0, 0, 0), (296, 440), [(-36, 0, -36, 72, 0, 72, False)]),
    ("upperCloud", "plume", (0, -80.8333, 0), (0, 0, 0), (0, 116), [(-40, -24, -40, 80, 24, 80, False)]),
    ("upperCloud1", "upperCloud", (0, -24, 0), (0, 0, 0), (0, 220), [(-32, -15, -32, 64, 15, 64, False)]),
    ("upperCloud_planes", "upperCloud", (0, -12, 0), (0, 0, 0), None, []),
    ("cube_r7", "upperCloud_planes", (16, 112, -16), (0, Q, 0), (0, 299), [(-23, -152, 0, 71, 69, 0, False)]),
    ("cube_r8", "upperCloud_planes", (16, 112, 16), (0, -Q, 0), (0, 299), [(-23, -152, 0, 71, 69, 0, False)]),
    ("cube_r9", "upperCloud_planes", (-16, 112, 16), (0, Q, 0), (0, 299), [(-48, -152, 0, 71, 69, 0, True)]),
    ("cube_r10", "upperCloud_planes", (-16, 112, -16), (0, -Q, 0), (0, 299), [(-48, -152, 0, 71, 69, 0, True)]),
    ("rings", "upperCloud", (0, -55, 0), (0, 0, 0), None, []),
    ("upperRing", "rings", (0, -4, 0), (0, 0, 0), (296, 440), [(-36, 0, -36, 72, 0, 72, False)]),
    ("lowerRing", "rings", (0, 4, 0), (0, 0, 0), (296, 440), [(-36, 0, -36, 72, 0, 72, False)]),
]
# hideFireball: hidden for the first 10 ticks (their children with them).
HIDDEN_AT_FIRST = ("plume", "lowerCloud", "upperCloud")


def parts():
    built = {}
    for name, parent, pivot, rotation, tex, boxes in PARTS:
        part = cb.Part(name)
        part.pivot = [float(v) for v in pivot]
        part.rotation = [float(v) for v in rotation]
        part.tex = tex or (0, 0)
        part.boxes = [{"pos": [float(v) for v in b[:3]], "size": [float(v) for v in b[3:6]], "tex": part.tex,
                       "inflate": 0.0, "mirror": b[6]} for b in boxes]
        if parent:
            part.parent = built[parent]
            built[parent].children.append(part)
        built[name] = part
    return built


def lifetime(size):
    return int(math.ceil(133.33 * size))


def clamp(v, lo, hi):
    return max(lo, min(hi, v))


def smin(a, b, k):
    h = max(k - abs(a - b), 0.0) / k
    return min(a, b) - h * h * k * 0.25


def pose(built, age, length):
    """MushroomCloudModel.animateParticle(age, smin(progress, 1, 0.5), 0): rotation, position and scale per bone."""
    progress = math.log(1 + (age - 10) / (length - 10)) * 2
    p = smin(progress, 1.0, 0.5)
    lower = p + p * p
    upper = clamp(p + 0.3, 0.4, 1.5)
    plume_ring = clamp(p, 0.0, 0.5) * 45
    lower_ring = clamp(p, 0.1, 0.65) * 20
    upper_ring = clamp(p, 0.2, 0.65) * 15
    scales = {"plume": (1, p * 1.5, 1), "upperCloud": (upper, 1, upper), "plumeRing": (plume_ring, 1, plume_ring),
              "lowerRing": (lower_ring, 1, lower_ring), "upperRing": (upper_ring, 1, upper_ring),
              "lowerCloud": (lower, lower, lower)}
    spin = {"plumeRing": age * 0.1, "lowerRing": -age * 0.2, "upperRing": age * 0.1}
    out = {}
    for name, part in built.items():
        sx, sy, sz = scales.get(name, (1, 1, 1))
        if age < 10 and name in HIDDEN_AT_FIRST:
            sx = sy = sz = 0.001
        rx, ry, rz = part.rotation
        out[name] = (rx, ry + spin.get(name, 0.0), rz, *part.pivot, sx, sy, sz)
    return out


def build(jar_path):
    built = parts()
    roots = [p for p in built.values() if p.parent is None]
    elements, groups, outliner = cb.build_geometry(MODEL_KEY, built, roots)
    cb.clamp_uvs(elements, TEX_W, TEX_H)
    group_ids = {g["name"]: g["uuid"] for g in groups}
    defaults = {name: (*part.rotation, *part.pivot) for name, part in built.items()}
    animations = []
    for size in SIZES:
        length_ticks = lifetime(size)
        times = list(range(length_ticks + 1))
        poses = [pose(built, age, length_ticks) for age in times]
        name = "explode_%d" % length_ticks
        seconds = length_ticks / 20.0
        animators = cb.animators_from_samples(MODEL_KEY, name, group_ids, defaults, times, poses, list(built), seconds)
        animations.append(cb.animation(MODEL_KEY, name, "once", seconds, animators))
    with zipfile.ZipFile(jar_path) as jar:
        png = jar.read(TEXTURE)
    png_w, png_h = cb.png_size(png)
    return {
        "meta": {"format_version": "5.0", "model_format": "free", "box_uv": False},
        "name": MODEL_KEY, "model_identifier": "", "visible_box": [2, 3, 0],
        "variable_placeholders": "", "variable_placeholder_buttons": [], "timeline_setups": [],
        "unhandled_root_fields": {}, "resolution": {"width": TEX_W, "height": TEX_H},
        "elements": elements, "groups": groups, "outliner": outliner,
        "textures": [{
            "path": "", "name": MODEL_KEY + ".png", "folder": "", "namespace": "", "id": "0", "group": "",
            "width": png_w, "height": png_h, "uv_width": TEX_W, "uv_height": TEX_H, "particle": False,
            "use_as_default": False, "layers_enabled": False, "sync_to_project": "", "render_mode": "default",
            "render_sides": "auto", "pbr_channel": "color", "frame_time": 1, "frame_order_type": "loop",
            "frame_order": "", "frame_interpolate": False, "visible": True, "internal": True, "saved": True,
            "uuid": cb.stable_uuid(MODEL_KEY, "texture"), "relative_path": MODEL_KEY + ".png",
            "source": "data:image/png;base64," + base64.b64encode(png).decode(),
        }],
        "animations": animations,
    }


def main(argv):
    if len(argv) != 3:
        print(__doc__)
        return 2
    data = build(argv[1])
    target = os.path.join(argv[2], "src/main/resources/bettermodel/models", MODEL_KEY + ".bbmodel")
    with open(target, "w", encoding="utf-8", newline="\n") as handle:
        json.dump(data, handle, separators=(",", ":"))
        handle.write("\n")
    print(target, len(data["elements"]), "cubos", [a["name"] for a in data["animations"]])
    return 0


if __name__ == "__main__":
    sys.exit(main(sys.argv))
