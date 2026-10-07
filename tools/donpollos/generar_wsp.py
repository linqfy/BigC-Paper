"""Genera los modelos de WhatsApp: la gallina WhatsApp y el logo de WhatsApp (voxelizado).

- Gallina WhatsApp: las proporciones de la gallina de Minecraft (al doble de tamano), verde, con el logo de
  WhatsApp pintado en las alas y una cola de plumas como en referencias_modelo/gallinawsp.png.
- Logo WhatsApp: el globito con el telefono armado con cubos de 1x1 (unidos en rectangulos), que gira y flota.

Uso: python tools/generar_wsp.py
"""
import base64
import io
import json
import sys
from pathlib import Path

from PIL import Image

sys.path.insert(0, str(Path(__file__).resolve().parent))
import generar_modelos as gm  # noqa: E402

GALLINA = "bigcasares_gallina_wsp"
PACK = gm.ROOT / "resourcepack" / "java" / "assets" / "donpollos"
METEORITO = "meteorito_wsp"
LOGO = "bigcasares_logo_wsp"

GREEN = (104, 196, 78)
GREEN_DARK = (76, 160, 58)
GREEN_LIGHT = (150, 222, 120)
LOGO_WHITE = (226, 246, 218)
WSP_GREEN = (44, 178, 76)
WSP_GREEN_DARK = (30, 140, 56)
WHITE = (236, 236, 232)
BEAK = (214, 150, 62)
BEAK_DARK = (150, 96, 40)
WATTLE = (214, 32, 30)
LEG = (232, 196, 74)
LEG_DARK = (196, 156, 46)


# ------------------------------------------------------------------ logo (mascara)

# telefono y colita dibujados a mano en una grilla de 24x24 (fila: columnas desde-hasta inclusive)
PHONE_24 = {6: [(6, 8)], 7: [(6, 9)], 8: [(6, 9)], 9: [(7, 8)], 10: [(7, 9)], 11: [(8, 10)], 12: [(9, 11)],
            13: [(10, 12), (15, 17)], 14: [(11, 17)], 15: [(12, 17)], 16: [(14, 16)]}
TAIL_24 = {17: [(2, 4)], 18: [(2, 5)], 19: [(1, 5)], 20: [(1, 6)], 21: [(0, 6)], 22: [(0, 2)]}


def logo_mask(size, ring=2):
    """Mascara del logo de WhatsApp: 0 nada, 1 globito (verde), 2 borde blanco, 3 telefono blanco."""
    c, r = (size - 1) / 2, size / 2 - 0.9
    k = 24 / size

    def hand(table, x, y):
        gy, gx = int((y + 0.5) * k), int((x + 0.5) * k)
        return any(a <= gx <= b for a, b in table.get(gy, ()))

    grid = [[0] * size for _ in range(size)]
    for y in range(size):
        for x in range(size):
            d = ((x - c) ** 2 + (y - c) ** 2) ** 0.5
            if d <= r:
                grid[y][x] = 2 if d > r - ring else (3 if hand(PHONE_24, x, y) else 1)
            elif hand(TAIL_24, x, y):
                grid[y][x] = 2
    return grid


# ------------------------------------------------------------------ modelo generico

class Model:
    def __init__(self, name, groups, density, tex):
        self.name, self.groups, self.density, self.tex = name, groups, density, tex
        self.els = []
        self.out_dir = None

    def cube(self, name, group, frm, to, part):
        self.els.append({"name": name, "group": group, "from": frm, "to": to, "part": part})

    def build(self, paint, anims):
        dens = self.density
        rects = []
        for i, el in enumerate(self.els):
            for f in gm.FACES:
                w, h = gm.face_size(el, f)
                rects.append(((i, f), max(1, round(w * dens)), max(1, round(h * dens))))
        layout, x, y, shelf = {}, 0, 0, 0
        for rid, w, h in sorted(rects, key=lambda r: (-r[2], -r[1])):
            if x + w > self.tex:
                x, y, shelf = 0, y + shelf, 0
            if y + h > self.tex:
                raise RuntimeError(f"la textura de {self.name} no entra en {self.tex}x{self.tex}")
            layout[rid] = (x, y)
            x += w
            shelf = max(shelf, h)
        atlas = Image.new("RGBA", (self.tex, self.tex), gm.CLEAR)
        sizes = {rid: (w, h) for rid, w, h in rects}
        for (i, f), (w, h) in sizes.items():
            atlas.paste(paint(self.els[i], f, w, h, f"{self.name}/{self.els[i]['name']}/{f}"), layout[(i, f)])
        buf = io.BytesIO()
        atlas.save(buf, "PNG")

        guid = {g: gm.stable_uuid(self.name, "group", g) for g in self.groups}
        elements, children = [], {g: [] for g in self.groups}
        for i, el in enumerate(self.els):
            euid = gm.stable_uuid(self.name, "el", el["name"])
            faces = {}
            for f in gm.FACES:
                fx, fy = layout[(i, f)]
                w, h = sizes[(i, f)]
                faces[f] = {"uv": [fx, fy, fx + w, fy + h], "texture": 0}
            elements.append({"name": el["name"], "type": "cube", "uuid": euid, "box_uv": False, "rescale": False,
                             "locked": False, "render_order": "default", "allow_mirror_modeling": True,
                             "from": el["from"], "to": el["to"], "autouv": 0, "color": 0, "inflate": 0,
                             "origin": self.groups[el["group"]][0], "rotation": [0, 0, 0], "faces": faces,
                             "export": True, "visibility": True, "shade": True, "mirror_uv": False,
                             "light_emission": 0})
            children[el["group"]].append(euid)
        # cada grupo es (pivote, padre) o (pivote, padre, rotacion fija de reposo)
        groups = [{"name": g, "uuid": guid[g], "export": True, "locked": False, "scope": 0, "origin": v[0],
                   "rotation": list(v[2]) if len(v) > 2 else [0, 0, 0], "color": 0, "reset": False, "shade": True,
                   "mirror_uv": False, "visibility": True, "autouv": 0, "isOpen": True}
                  for g, v in self.groups.items()]

        def tree(g):
            kids = list(children[g])
            for child, v in self.groups.items():
                if v[1] == g:
                    kids.append(tree(child))
            return {"uuid": guid[g], "isOpen": True, "children": kids}

        out_anims = []
        for name, (length, interp, bones) in anims.items():
            animators = {}
            for bone, frames in bones.items():
                kfs = [gm.keyframe(channel, t, xyz, self.name, name, bone, i, interp)
                       for i, (channel, t, xyz) in enumerate(frames)]
                animators[guid[bone]] = {"name": bone, "type": "bone", "rotation_global": False,
                                         "quaternion_interpolation": False, "keyframes": kfs}
            out_anims.append({"uuid": gm.stable_uuid(self.name, "anim", name), "name": name, "loop": "loop",
                              "override": False, "length": length, "snapping": 24, "selected": False,
                              "anim_time_update": "", "blend_weight": "", "start_delay": "", "loop_delay": "",
                              "animators": animators})

        data = {
            "meta": {"format_version": "5.0", "model_format": "free", "box_uv": False},
            "name": self.name, "model_identifier": "", "visible_box": [2, 2, 0],
            "variable_placeholders": "", "variable_placeholder_buttons": [], "timeline_setups": [],
            "unhandled_root_fields": {}, "resolution": {"width": self.tex, "height": self.tex},
            "elements": elements, "groups": groups, "outliner": [tree("root")],
            "textures": [{"path": "", "name": f"{self.name}.png", "folder": "", "namespace": "", "id": "0",
                          "group": "", "width": self.tex, "height": self.tex, "uv_width": self.tex,
                          "uv_height": self.tex, "particle": False, "use_as_default": False,
                          "layers_enabled": False, "sync_to_project": "", "render_mode": "default",
                          "render_sides": "auto", "pbr_channel": "color", "frame_time": 1,
                          "frame_order_type": "loop", "frame_order": "", "frame_interpolate": False,
                          "visible": True, "internal": True, "saved": True,
                          "uuid": gm.stable_uuid(self.name, "texture"), "relative_path": f"{self.name}.png",
                          "source": "data:image/png;base64," + base64.b64encode(buf.getvalue()).decode()}],
            "animations": out_anims,
        }
        out = (self.out_dir or gm.OUT_MODELS) / f"{self.name}.bbmodel"
        out.write_text(json.dumps(data, ensure_ascii=False), encoding="utf-8")
        gm.OUT_PREVIEWS.mkdir(parents=True, exist_ok=True)
        atlas.resize((self.tex * 2, self.tex * 2), Image.NEAREST).save(gm.OUT_PREVIEWS / f"{self.name}_textura.png")
        print("ok", out.name, len(elements), "elementos")
        return data, atlas


# ------------------------------------------------------------------ gallina WhatsApp

def gallina(ojos="mob", out_dir=None):
    groups = {
        "root": ([0, 0, 0], None),
        "body": ([0, 16, 0], "root"),
        "hi_head": ([0, 20, -8], "body"),
        "rightwing": ([6, 21, 0], "body"),
        "leftwing": ([-6, 21, 0], "body"),
        "cola": ([0, 20, 7], "body"),
        "rightleg": ([3, 10, 0], "root"),
        "leftleg": ([-3, 10, 0], "root"),
    }
    m = Model(GALLINA, groups, density=2, tex=256)
    m.out_dir = out_dir
    m.cube("cuerpo", "body", [-6, 10, -8], [6, 22, 8], "body")
    m.cube("cabeza", "hi_head", [-4, 18, -12], [4, 30, -6], "head")
    m.cube("pico", "hi_head", [-4, 22, -16], [4, 26, -12], "beak")
    m.cube("moco", "hi_head", [-1.5, 18, -13.5], [1.5, 22, -12], "wattle")
    m.cube("ala_der", "rightwing", [6, 11, -7], [8, 21, 7], "wing")
    m.cube("ala_izq", "leftwing", [-8, 11, -7], [-6, 21, 7], "wing")
    # cola de plumas escalonada hacia arriba y atras
    # la del medio va corrida medio bloque para que ninguna cara quede en el mismo plano que las de al lado
    for name, x1, top, dz in (("pluma_izq", -4, 28, 0), ("pluma_medio", -1.5, 32, 0.5), ("pluma_der", 1, 29, 0)):
        m.cube(f"{name}_base", "cola", [x1, 20, 4 + dz], [x1 + 3, 27 + dz, 9 + dz], "feather")
        m.cube(f"{name}_punta", "cola", [x1 + 0.25, 27 + dz, 6 + dz], [x1 + 2.75, top, 11 + dz], "feather_tip")
    for side, sx in (("der", 1), ("izq", -1)):
        g = "rightleg" if sx > 0 else "leftleg"
        lx1, lx2 = (2, 4) if sx > 0 else (-4, -2)
        m.cube(f"pata_{side}", g, [lx1, 1, -1], [lx2, 10, 1], "leg")
        m.cube(f"dedo_medio_{side}", g, [lx1, 0, -6], [lx2, 1, 2], "toe")
        m.cube(f"dedo_a_{side}", g, [lx1 - 2, 0, -3], [lx1, 1, -1], "toe")
        m.cube(f"dedo_b_{side}", g, [lx2, 0, -3], [lx2 + 2, 1, -1], "toe")

    def mottled(c, base, dark, light):
        c.fill(base, 6)
        for _ in range(c.w * c.h // 9):
            x, y = c.rng.randrange(c.w), c.rng.randrange(c.h)
            col = dark if c.rng.random() < 0.55 else light
            c.rect(x, y, x + c.rng.choice((1, 2)), y + c.rng.choice((1, 2)), col, 5)

    def paint(el, face, w, h, seed):
        c = gm.Canvas(w, h, seed)
        part = el["part"]
        if part in ("body", "head", "feather", "feather_tip"):
            mottled(c, GREEN, GREEN_DARK, GREEN_LIGHT)
            if part == "body" and face == "down":
                c.fill(GREEN_DARK, 5)
            if part == "head":
                if ojos == "mob" and face == "north":
                    # como la gallina comun de Minecraft: un pixel negro en cada esquina de arriba
                    c.rect(0, 3, 4, 7, (16, 16, 18))
                    c.rect(w - 4, 3, w, 7, (16, 16, 18))
                elif ojos == "mob" and face in ("east", "west"):
                    x1 = w - 4 if face == "east" else 0
                    c.rect(x1, 3, x1 + 4, 7, (16, 16, 18))
                elif face == "north":
                    # cara clara alrededor de los ojos, como la referencia
                    for (x1, x2) in ((0, 5), (w - 5, w)):
                        c.rect(x1, 1, x2, 7, (196, 232, 178), 4)
                    c.rect(0, 2, 3, 6, (244, 244, 240))
                    c.rect(3, 2, 5, 6, (16, 16, 18))
                    c.rect(w - 3, 2, w, 6, (244, 244, 240))
                    c.rect(w - 5, 2, w - 3, 6, (16, 16, 18))
                elif face in ("east", "west"):
                    front = w - 1 if face == "east" else 0
                    x1 = front - 2 if face == "east" else front
                    c.rect(x1, 2, x1 + 3, 6, (244, 244, 240))
                    c.rect(x1 + (2 if face == "east" else 0), 3, x1 + (3 if face == "east" else 1), 5, (16, 16, 18))
            if part == "feather_tip" and face == "up":
                c.fill(GREEN_LIGHT, 6)
            c.edge_shade(-10)
            return c.img
        if part == "wing":
            mottled(c, GREEN, GREEN_DARK, GREEN_LIGHT)
            if face in ("east", "west") and ((face == "east") == (el["group"] == "rightwing")):
                size = min(w, h) - 2
                grid = logo_mask(size)
                ox, oy = (w - size) // 2, (h - size) // 2
                for y in range(size):
                    for x in range(size):
                        if grid[y][x] in (2, 3):
                            c.px(ox + x, oy + y, gm.shade(LOGO_WHITE, c.rng.randint(-8, 4)))
            c.edge_shade(-14)
            return c.img
        if part == "beak":
            c.fill(BEAK, 5)
            if face == "north":
                c.rect(1, 1, 3, 2, BEAK_DARK)
                c.rect(w - 3, 1, w - 1, 2, BEAK_DARK)
            c.row(h - 1, BEAK_DARK, 3)
            c.edge_shade(-12)
            return c.img
        if part == "wattle":
            c.fill(WATTLE, 6)
            c.edge_shade(-18)
            return c.img
        if part in ("leg", "toe"):
            c.fill(LEG, 5)
            if part == "toe" and face == "up":
                c.edge_shade(-20)
            c.edge_shade(-14)
            return c.img
        c.fill((255, 0, 255), 0)
        return c.img

    anims = {
        "idle": (2.0, "catmullrom", {
            "hi_head": [("rotation", 0, (0, 0, 0)), ("rotation", 0.5, (6, 0, 0)), ("rotation", 1.0, (0, 0, 0)),
                        ("rotation", 1.5, (0, 12, 0)), ("rotation", 2.0, (0, 0, 0))],
            "cola": [("rotation", 0, (0, 0, 0)), ("rotation", 1.0, (-6, 0, 0)), ("rotation", 2.0, (0, 0, 0))],
        }),
        "walk": (0.6, "linear", {
            "rightleg": [("rotation", 0, (30, 0, 0)), ("rotation", 0.3, (-30, 0, 0)), ("rotation", 0.6, (30, 0, 0))],
            "leftleg": [("rotation", 0, (-30, 0, 0)), ("rotation", 0.3, (30, 0, 0)), ("rotation", 0.6, (-30, 0, 0))],
            "hi_head": [("rotation", 0, (0, 0, 0)), ("rotation", 0.15, (10, 0, 0)), ("rotation", 0.3, (0, 0, 0)),
                        ("rotation", 0.45, (10, 0, 0)), ("rotation", 0.6, (0, 0, 0))],
            "rightwing": [("rotation", 0, (0, 0, 0)), ("rotation", 0.3, (0, 0, 10)), ("rotation", 0.6, (0, 0, 0))],
            "leftwing": [("rotation", 0, (0, 0, 0)), ("rotation", 0.3, (0, 0, -10)), ("rotation", 0.6, (0, 0, 0))],
        }),
        "aletear": (0.4, "linear", {
            "rightwing": [("rotation", 0, (0, 0, 0)), ("rotation", 0.2, (0, 0, 70)), ("rotation", 0.4, (0, 0, 0))],
            "leftwing": [("rotation", 0, (0, 0, 0)), ("rotation", 0.2, (0, 0, -70)), ("rotation", 0.4, (0, 0, 0))],
        }),
    }
    m.build(paint, anims)


# ------------------------------------------------------------------ logo WhatsApp (voxel)

def rectangles(cells):
    """Une celdas (x, y) en rectangulos: primero por filas, despues junta filas iguales."""
    cells = set(cells)
    runs = []
    for y in sorted({c[1] for c in cells}):
        xs = sorted(x for x, yy in cells if yy == y)
        start = prev = None
        for x in xs + [None]:
            if start is None:
                start = prev = x
            elif x is not None and x == prev + 1:
                prev = x
            else:
                runs.append((start, prev + 1, y))
                start = prev = x
    merged = []
    for x1, x2, y in sorted(runs, key=lambda r: (r[0], r[1], r[2])):
        if merged and merged[-1][0] == x1 and merged[-1][1] == x2 and merged[-1][3] == y:
            merged[-1][3] = y + 1
        else:
            merged.append([x1, x2, y, y + 1])
    return merged


def logo():
    n = 24
    base_y = 6
    center = base_y + n / 2
    groups = {"root": ([0, 0, 0], None), "logo": ([0, center, 0], "root")}
    m = Model(LOGO, groups, density=1, tex=256)
    grid = logo_mask(n)
    bubble = [(x, y) for y in range(n) for x in range(n) if grid[y][x]]
    white = [(x, y) for y in range(n) for x in range(n) if grid[y][x] in (2, 3)]

    def box(x1, x2, y1, y2):
        # columna x de la grilla (vista desde adelante, izquierda = +x); fila y desde arriba
        return n / 2 - x2, base_y + n - y2, n / 2 - x1, base_y + n - y1

    for i, (x1, x2, y1, y2) in enumerate(rectangles(bubble)):
        a, b, c, d = box(x1, x2, y1, y2)
        m.cube(f"globo_{i}", "logo", [a, b, -2], [c, d, 2], "green")
    for i, (x1, x2, y1, y2) in enumerate(rectangles(white)):
        a, b, c, d = box(x1, x2, y1, y2)
        m.cube(f"blanco_frente_{i}", "logo", [a, b, -3], [c, d, -2], "white")
        m.cube(f"blanco_atras_{i}", "logo", [a, b, 2], [c, d, 3], "white")

    def paint(el, face, w, h, seed):
        c = gm.Canvas(w, h, seed)
        if el["part"] == "white":
            c.fill(WHITE, 4)
            for _ in range(w * h // 6):
                c.px(c.rng.randrange(w), c.rng.randrange(h), (214, 214, 210))
            if face not in ("north", "south"):
                c.fill((206, 206, 202), 4)
        else:
            c.fill(WSP_GREEN, 6)
            for _ in range(w * h // 5):
                c.px(c.rng.randrange(w), c.rng.randrange(h), gm.shade(WSP_GREEN, c.rng.choice((-14, 12))))
            if face not in ("north", "south"):
                c.fill(WSP_GREEN_DARK, 5)
        return c.img

    anims = {
        "idle": (4.0, "linear", {
            "logo": [("rotation", 0, (0, 0, 0)), ("rotation", 1, (0, 90, 0)), ("rotation", 2, (0, 180, 0)),
                     ("rotation", 3, (0, 270, 0)), ("rotation", 4, (0, 360, 0)),
                     ("position", 0, (0, 0, 0)), ("position", 1, (0, 1.5, 0)), ("position", 2, (0, 0, 0)),
                     ("position", 3, (0, 1.5, 0)), ("position", 4, (0, 0, 0))],
        }),
        "walk": (4.0, "linear", {
            "logo": [("rotation", 0, (0, 0, 0)), ("rotation", 1, (0, 90, 0)), ("rotation", 2, (0, 180, 0)),
                     ("rotation", 3, (0, 270, 0)), ("rotation", 4, (0, 360, 0))],
        }),
    }
    data, atlas = m.build(paint, anims)
    item_model(data, atlas, center_y=center)


def item_model(data, atlas, center_y):
    """El mismo logo como modelo de item (para los meteoritos del boss, que son item displays).

    Blockbench centra x/z en 0 y Minecraft usa la esquina: se corre +8 en x/z y el centro del logo
    queda en (8, 8, 8), que es el punto donde el item display pone el modelo.
    """
    tex = data["resolution"]["width"]
    k = 16 / tex
    elements = []
    for el in data["elements"]:
        shift = (8, 8 - center_y, 8)
        faces = {f: {"uv": [round(v * k, 4) for v in info["uv"]], "texture": "#logo"}
                 for f, info in el["faces"].items()}
        elements.append({"from": [round(a + b, 4) for a, b in zip(el["from"], shift)],
                         "to": [round(a + b, 4) for a, b in zip(el["to"], shift)], "faces": faces})
    for el in elements:
        for v in el["from"] + el["to"]:
            if not -16 <= v <= 32:
                raise RuntimeError("el logo no entra en el rango de un modelo de item")
    (PACK / "textures" / "item").mkdir(parents=True, exist_ok=True)
    (PACK / "models" / "item").mkdir(parents=True, exist_ok=True)
    (PACK / "items").mkdir(parents=True, exist_ok=True)
    atlas.save(PACK / "textures" / "item" / f"{METEORITO}.png")
    model = {"textures": {"logo": f"donpollos:item/{METEORITO}", "particle": f"donpollos:item/{METEORITO}"},
             "elements": elements}
    (PACK / "models" / "item" / f"{METEORITO}.json").write_text(json.dumps(model), encoding="utf-8")
    (PACK / "items" / f"{METEORITO}.json").write_text(json.dumps(
        {"model": {"type": "minecraft:model", "model": f"donpollos:item/{METEORITO}"}}, indent=2), encoding="utf-8")
    print("ok item", METEORITO, len(elements), "elementos")


if __name__ == "__main__":
    if "--ojos-grandes" in sys.argv:  # solo vista previa con los ojos de la referencia, no toca el plugin
        gallina("grandes", gm.OUT_PREVIEWS)
    else:
        gallina()
        logo()
