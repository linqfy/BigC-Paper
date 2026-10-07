"""Genera el modelo del Don Pollo Boss: una estatua con los brazos abiertos que sale de un auto.

Mismo estilo que los Don Pollos (pocos cubos grandes, detalle pintado) y la textura del Don Pollo
Comun: su cara con barba, camisa blanca, manos de piel; el auto es caqui como sus pantalones, con
ruedas negras como sus zapatos. Dos rayos laser salen de los ojos (hueso glow_laser, escondido
con escala casi 0 y desplegado en la animacion "laser").

Uso: python tools/generar_boss.py
"""
import base64
import io
import json
import sys
from pathlib import Path

from PIL import Image

sys.path.insert(0, str(Path(__file__).resolve().parent))
import generar_modelos as gm  # noqa: E402

TEX = 256
MODEL = "bigcasares_don_pollo_boss"
V = gm.VARIANTS["comun"]
KHAKI, KHAKI_DARK = (196, 178, 134), (164, 146, 104)
TIRE, RIM = (32, 30, 28), (70, 66, 60)

# grupo: (pivote, padre)
GROUPS = {
    "root": ([0, 0, 0], None),
    "auto": ([0, 8, 0], "root"),
    "torso": ([0, 17, 4], "root"),
    "hi_head": ([0, 40, 4], "torso"),
    "glow_laser": ([0, 45.5, -1.5], "hi_head"),
    "rightarm": ([8, 37, 4], "torso"),
    "leftarm": ([-8, 37, 4], "torso"),
}


def cube(name, group, frm, to, part):
    return {"kind": "cube", "name": name, "group": group, "from": frm, "to": to, "part": part}


def geometry():
    els = []
    # ruedas (zapatos negros)
    for sx in (1, -1):
        for z1, z2 in ((-17, -9), (9, 17)):
            # la rueda asoma 2 px del costado: ninguna cara queda en el mismo plano que la carroceria
            x1, x2 = (14, 18) if sx > 0 else (-18, -14)
            els.append(cube(f"rueda_{'der' if sx > 0 else 'izq'}_{'del' if z1 < 0 else 'tra'}", "auto",
                            [x1, 0, z1], [x2, 6, z2], "tire"))
    els.append(cube("auto_carroceria", "auto", [-16, 5, -18], [16, 14, 18], "car_body"))
    els.append(cube("auto_cabina", "auto", [-13, 14, -8], [13, 17, 16], "car_deck"))
    # estatua: torso escalonado que se angosta hacia arriba (camisa blanca)
    els.append(cube("torso_base", "torso", [-10, 17, -2], [10, 25, 10], "shirt_plain"))
    els.append(cube("torso_medio", "torso", [-8, 25, -1], [8, 33, 9], "shirt_plain"))
    els.append(cube("torso_pecho", "torso", [-7, 33, 0], [7, 40, 8], "shirt_chest"))
    # cabeza con la misma proporcion que los otros Don Pollos (10x10x10) y la cara del Comun
    els.append(cube("cabeza", "hi_head", [-5, 40, -1], [5, 50, 9], "head"))
    # brazos abiertos con puños
    els.append(cube("brazo_der", "rightarm", [7, 34, 1], [23, 40, 7], "sleeve"))
    els.append(cube("puno_der", "rightarm", [23, 33, 0], [29, 41, 8], "fist"))
    els.append(cube("brazo_izq", "leftarm", [-23, 34, 1], [-7, 40, 7], "sleeve"))
    els.append(cube("puno_izq", "leftarm", [-29, 33, 0], [-23, 41, 8], "fist"))
    # rayos laser desde los ojos hacia adelante
    els.append(cube("laser_der", "glow_laser", [2, 45, -31.5], [3, 46, -1.5], "laser"))
    els.append(cube("laser_izq", "glow_laser", [-3, 45, -31.5], [-2, 46, -1.5], "laser"))
    return els


def paint(part, face, w, h, seed):
    c = gm.Canvas(w, h, seed)
    if part == "head":
        return gm.paint_face("comun", V, "head", face, w, h, seed)
    if part == "shirt_chest":
        return gm.paint_face("comun", V, "torso", face, w, h, seed)
    if part == "shirt_plain":
        c.fill(V["shirt"], 5)
        for _ in range(w * h // 12):
            c.px(c.rng.randrange(w), c.rng.randrange(h), V["shirt_shade"])
        if face == "north":
            mid = w // 2
            c.col(mid, (196, 196, 208), 1)
            for y in range(1, h, 3):
                c.px(mid, y, (150, 150, 162))
        if face in ("north", "south", "east", "west"):
            c.row(h - 1, V["shirt_shade"], 2)
        c.edge_shade(-10)
        return c.img
    if part == "sleeve":
        c.fill(V["shirt"], 5)
        if face in ("east", "west"):
            c.col(0 if face == "west" else w - 1, V["shirt_shade"], 2)
        c.edge_shade(-10)
        return c.img
    if part == "fist":
        c.fill(gm.SKIN, 4)
        if face in ("north", "up", "east", "west"):
            # nudillos
            for x in range(1, w - 1, 2):
                c.px(x, 1, gm.SKIN_SHADE)
                c.px(x, h // 2, gm.SKIN_SHADE)
        c.edge_shade(-12)
        return c.img
    if part == "tire":
        c.fill(TIRE, 3)
        if face in ("east", "west"):
            c.rect(w // 2 - 1, h // 2 - 1, w // 2 + 2, h // 2 + 2, RIM)
        return c.img
    if part == "car_body":
        c.fill(KHAKI, 5)
        if face == "north":  # frente: faros y parrilla
            c.rect(3, 3, 8, 6, (240, 240, 236))
            c.rect(w - 8, 3, w - 3, 6, (240, 240, 236))
            c.rect(11, 4, w - 11, 6, (34, 34, 36))
            c.row(h - 1, KHAKI_DARK, 2)
        elif face == "south":  # atras: luces rojas
            c.rect(3, 3, 8, 6, (200, 30, 30))
            c.rect(w - 8, 3, w - 3, 6, (200, 30, 30))
            c.rect(12, 6, w - 12, 7, KHAKI_DARK)
        elif face in ("east", "west"):
            c.row(3, KHAKI_DARK, 2)
            c.row(h - 1, KHAKI_DARK, 2)
        elif face == "down":
            c.fill(KHAKI_DARK, 4)
        c.edge_shade(-12)
        return c.img
    if part == "car_deck":
        c.fill(KHAKI_DARK, 4)
        c.edge_shade(-10)
        return c.img
    if part == "laser":
        c.fill((255, 40, 30), 10)
        return c.img
    c.fill((255, 0, 255), 0)
    return c.img


def animations(guid):
    hide = (0.01, 0.01, 0.01)
    show = (1, 1, 1)
    defs = {
        "idle": (3.0, "catmullrom", False, {
            "glow_laser": [("scale", 0, hide), ("scale", 3, hide)],
            "rightarm": [("rotation", 0, (0, 0, 0)), ("rotation", 1.5, (0, 0, 4)), ("rotation", 3, (0, 0, 0))],
            "leftarm": [("rotation", 0, (0, 0, 0)), ("rotation", 1.5, (0, 0, -4)), ("rotation", 3, (0, 0, 0))],
            "hi_head": [("rotation", 0, (0, 0, 0)), ("rotation", 1.5, (-3, 6, 0)), ("rotation", 3, (0, 0, 0))],
        }),
        "walk": (0.8, "linear", False, {
            "glow_laser": [("scale", 0, hide), ("scale", 0.8, hide)],
            "auto": [("position", 0, (0, 0, 0)), ("position", 0.2, (0, 0.6, 0)), ("position", 0.4, (0, 0, 0)),
                     ("position", 0.6, (0, 0.6, 0)), ("position", 0.8, (0, 0, 0))],
            "torso": [("rotation", 0, (0, 0, -3)), ("rotation", 0.4, (0, 0, 3)), ("rotation", 0.8, (0, 0, -3))],
            "rightarm": [("rotation", 0, (12, 0, 4)), ("rotation", 0.4, (-12, 0, 4)), ("rotation", 0.8, (12, 0, 4))],
            "leftarm": [("rotation", 0, (-12, 0, -4)), ("rotation", 0.4, (12, 0, -4)), ("rotation", 0.8, (-12, 0, -4))],
        }),
        "laser": (2.2, "linear", True, {
            "glow_laser": [("scale", 0, hide), ("scale", 0.35, hide), ("scale", 0.5, show), ("scale", 1.9, show),
                           ("scale", 2.05, hide), ("scale", 2.2, hide)],
            "hi_head": [("rotation", 0, (0, 0, 0)), ("rotation", 0.35, (8, 0, 0)), ("rotation", 0.5, (-4, 0, 0)),
                        ("rotation", 1.9, (-4, 0, 0)), ("rotation", 2.2, (0, 0, 0))],
            "rightarm": [("rotation", 0, (0, 0, 0)), ("rotation", 0.4, (0, 0, 25)), ("rotation", 1.9, (0, 0, 25)),
                         ("rotation", 2.2, (0, 0, 0))],
            "leftarm": [("rotation", 0, (0, 0, 0)), ("rotation", 0.4, (0, 0, -25)), ("rotation", 1.9, (0, 0, -25)),
                        ("rotation", 2.2, (0, 0, 0))],
            "torso": [("rotation", 0, (0, 0, 0)), ("rotation", 0.4, (6, 0, 0)), ("rotation", 1.9, (6, 0, 0)),
                      ("rotation", 2.2, (0, 0, 0))],
        }),
        "golpe": (0.7, "catmullrom", True, {
            "glow_laser": [("scale", 0, hide), ("scale", 0.7, hide)],
            "rightarm": [("rotation", 0, (0, 0, 0)), ("rotation", 0.25, (0, 0, 40)), ("rotation", 0.45, (0, 60, -10)),
                         ("rotation", 0.7, (0, 0, 0))],
            "leftarm": [("rotation", 0, (0, 0, 0)), ("rotation", 0.25, (0, 0, -40)), ("rotation", 0.45, (0, -60, 10)),
                        ("rotation", 0.7, (0, 0, 0))],
            "torso": [("rotation", 0, (0, 0, 0)), ("rotation", 0.45, (-10, 0, 0)), ("rotation", 0.7, (0, 0, 0))],
        }),
    }
    out = []
    for name, (length, interp, override, bones) in defs.items():
        animators = {}
        for bone, frames in bones.items():
            kfs = [gm.keyframe(channel, t, xyz, MODEL, name, bone, i, interp)
                   for i, (channel, t, xyz) in enumerate(frames)]
            animators[guid[bone]] = {"name": bone, "type": "bone", "rotation_global": False,
                                     "quaternion_interpolation": False, "keyframes": kfs}
        out.append({"uuid": gm.stable_uuid(MODEL, "anim", name), "name": name, "loop": "loop" if not override else "once",
                    "override": override, "length": length, "snapping": 24, "selected": False,
                    "anim_time_update": "", "blend_weight": "", "start_delay": "", "loop_delay": "",
                    "animators": animators})
    return out


def pack(rects):
    out, x, y, shelf = {}, 0, 0, 0
    for rid, w, h in sorted(rects, key=lambda r: (-r[2], -r[1])):
        if x + w > TEX:
            x, y, shelf = 0, y + shelf, 0
        if y + h > TEX:
            raise RuntimeError("la textura del boss no entra en 256x256")
        out[rid] = (x, y)
        x += w
        shelf = max(shelf, h)
    return out


def size(v):
    return max(1, int(round(v)))


def main():
    els = geometry()
    rects = []
    for i, el in enumerate(els):
        for f in gm.FACES:
            w, h = gm.face_size(el, f)
            rects.append(((i, f), size(w), size(h)))
    layout = pack(rects)
    atlas = Image.new("RGBA", (TEX, TEX), gm.CLEAR)
    for (i, f), w, h in rects:
        atlas.paste(paint(els[i]["part"], f, w, h, f"boss/{els[i]['name']}/{f}"), layout[(i, f)])
    buf = io.BytesIO()
    atlas.save(buf, "PNG")

    guid = {g: gm.stable_uuid(MODEL, "group", g) for g in GROUPS}
    elements, children = [], {g: [] for g in GROUPS}
    for i, el in enumerate(els):
        euid = gm.stable_uuid(MODEL, "el", el["name"])
        faces = {}
        for f in gm.FACES:
            x, y = layout[(i, f)]
            w, h = gm.face_size(el, f)
            faces[f] = {"uv": [x, y, x + size(w), y + size(h)], "texture": 0}
        elements.append({"name": el["name"], "type": "cube", "uuid": euid, "box_uv": False, "rescale": False,
                         "locked": False, "render_order": "default", "allow_mirror_modeling": True,
                         "from": el["from"], "to": el["to"], "autouv": 0, "color": 0, "inflate": 0,
                         "origin": GROUPS[el["group"]][0], "rotation": [0, 0, 0], "faces": faces,
                         "export": True, "visibility": True, "shade": True, "mirror_uv": False, "light_emission": 0})
        children[el["group"]].append(euid)
    groups = [{"name": g, "uuid": guid[g], "export": True, "locked": False, "scope": 0, "origin": o,
               "rotation": [0, 0, 0], "color": 0, "reset": False, "shade": True, "mirror_uv": False,
               "visibility": True, "autouv": 0, "isOpen": True} for g, (o, _) in GROUPS.items()]

    def tree(g):
        kids = list(children[g])
        for child, (_, parent) in GROUPS.items():
            if parent == g:
                kids.append(tree(child))
        return {"uuid": guid[g], "isOpen": True, "children": kids}

    data = {
        "meta": {"format_version": "5.0", "model_format": "free", "box_uv": False},
        "name": MODEL, "model_identifier": "", "visible_box": [4, 4, 0],
        "variable_placeholders": "", "variable_placeholder_buttons": [], "timeline_setups": [],
        "unhandled_root_fields": {}, "resolution": {"width": TEX, "height": TEX},
        "elements": elements, "groups": groups, "outliner": [tree("root")],
        "textures": [{"path": "", "name": f"{MODEL}.png", "folder": "", "namespace": "", "id": "0", "group": "",
                      "width": TEX, "height": TEX, "uv_width": TEX, "uv_height": TEX, "particle": False,
                      "use_as_default": False, "layers_enabled": False, "sync_to_project": "",
                      "render_mode": "default", "render_sides": "auto", "pbr_channel": "color", "frame_time": 1,
                      "frame_order_type": "loop", "frame_order": "", "frame_interpolate": False, "visible": True,
                      "internal": True, "saved": True, "uuid": gm.stable_uuid(MODEL, "texture"),
                      "relative_path": f"{MODEL}.png",
                      "source": "data:image/png;base64," + base64.b64encode(buf.getvalue()).decode()}],
        "animations": animations(guid),
    }
    out = gm.OUT_MODELS / f"{MODEL}.bbmodel"
    out.write_text(json.dumps(data, ensure_ascii=False), encoding="utf-8")
    gm.OUT_PREVIEWS.mkdir(parents=True, exist_ok=True)
    atlas.resize((TEX * 2, TEX * 2), Image.NEAREST).save(gm.OUT_PREVIEWS / f"{MODEL}_textura.png")
    print("ok", out.name, len(elements), "elementos")


if __name__ == "__main__":
    main()
