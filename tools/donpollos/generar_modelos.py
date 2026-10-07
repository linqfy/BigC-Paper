"""Genera los 5 modelos BetterModel (.bbmodel) de Don Pollo con sus texturas.

Estilo: pocos cubos grandes con el detalle pintado en la textura (no voxel).
La ropa que se quiere remarcar va en planos de grosor 0 apenas delante del cuerpo.
El modelo mira al norte (-Z). Derecha del personaje = +X.

Uso: python tools/generar_modelos.py
Salida: src/main/resources/bettermodel/models/*.bbmodel y build/model-previews/*.png
"""
import base64
import sys
import io
import json
import random
import uuid as uuidlib
from pathlib import Path

from PIL import Image, ImageDraw

ROOT = Path(__file__).resolve().parents[2]  # tools/donpollos -> raiz de BigC-Paper
FUENTES = Path(__file__).resolve().parent / "fuentes"
sys.path.insert(0, str(Path(__file__).resolve().parent))
OUT_MODELS = ROOT / "src" / "main" / "resources" / "bettermodel" / "models"
OUT_PREVIEWS = ROOT / "build" / "model-previews"
TEX = 128
PLANE_OFFSET = 0.05

SKIN = (112, 68, 46)
SKIN_SHADE = (90, 54, 36)
SKIN_LIGHT = (132, 84, 58)
BEARD = (38, 27, 22)
EYE_WHITE = (236, 232, 222)
PUPIL = (28, 20, 16)
LIPS = (138, 78, 66)
CLEAR = (0, 0, 0, 0)


def stable_uuid(*parts):
    return str(uuidlib.uuid5(uuidlib.NAMESPACE_URL, "donpollos/" + "/".join(parts)))


def shade(color, amount):
    return tuple(max(0, min(255, c + amount)) for c in color[:3])


class Canvas:
    """Pinta una cara de w x h texeles. x=0 es la izquierda tal como la ve quien mira esa cara."""

    def __init__(self, w, h, seed, transparent=False):
        self.w, self.h = w, h
        self.img = Image.new("RGBA", (w, h), CLEAR if transparent else (255, 0, 255, 255))
        self.rng = random.Random(seed)

    def fill(self, color, noise=6):
        for y in range(self.h):
            for x in range(self.w):
                self.px(x, y, shade(color, self.rng.randint(-noise, noise)))

    def px(self, x, y, color):
        if 0 <= x < self.w and 0 <= y < self.h:
            rgba = color if len(color) == 4 else (*color, 255)
            self.img.putpixel((x, y), rgba)

    def rect(self, x1, y1, x2, y2, color, noise=0):
        for y in range(y1, y2):
            for x in range(x1, x2):
                self.px(x, y, shade(color, self.rng.randint(-noise, noise)) if noise else color)

    def row(self, y, color, noise=4):
        self.rect(0, y, self.w, y + 1, color, noise)

    def col(self, x, color, noise=4):
        self.rect(x, 0, x + 1, self.h, color, noise)

    def edge_shade(self, amount=-12):
        for y in range(self.h):
            for x in (0, self.w - 1):
                r, g, b, a = self.img.getpixel((x, y))
                if a:
                    self.px(x, y, shade((r, g, b), amount))


# ---------------------------------------------------------------- variantes

VARIANTS = {
    "comun": {
        "shirt": (238, 238, 242), "shirt_shade": (210, 210, 222), "sleeves": "long",
        "pants": (196, 178, 134), "belt": (28, 28, 30), "shoes": (34, 30, 28),
        "beard": "full", "belly": "normal", "pattern": None,
    },
    "gordito": {
        "shirt": (240, 242, 248), "shirt_shade": (214, 220, 236), "sleeves": "short",
        "pants": (92, 122, 160), "belt": (40, 34, 30), "shoes": (36, 32, 30),
        "beard": "full", "belly": "big", "pattern": "hawaiian",
    },
    "salsero": {
        "shirt": (146, 166, 182), "shirt_shade": (122, 142, 160), "sleeves": "tank",
        "pants": (26, 31, 52), "belt": (20, 20, 22), "shoes": (24, 22, 22),
        "beard": "full", "belly": "small", "pattern": None,
    },
    "aura_67": {
        "shirt": (222, 48, 30), "shirt_shade": (182, 30, 20), "sleeves": "long",
        "pants": (36, 30, 32), "belt": None, "shoes": (26, 24, 24),
        "beard": "full", "belly": "normal", "pattern": None, "long_top": True,
    },
    "fino": {
        "shirt": (198, 182, 146), "shirt_shade": (172, 156, 122), "sleeves": "suit",
        "pants": (190, 174, 138), "belt": None, "shoes": (52, 38, 30),
        "beard": "full", "belly": "normal", "pattern": None, "long_top": True, "hat": True,
    },
}


# ---------------------------------------------------------------- geometria

def cube(name, group, frm, to, part, split=None):
    """split = (alto_total, fila_inicial): la pieza es un tramo de un miembro mas largo y su
    textura se recorta de la del miembro entero para que la union quede continua."""
    return {"kind": "cube", "name": name, "group": group, "from": frm, "to": to, "part": part, "split": split}


def plane(name, group, x1, y1, x2, y2, z, part):
    """Plano de grosor 0 mirando al norte en z (se le resta el offset para quedar delante)."""
    zz = z - PLANE_OFFSET
    return {"kind": "plane", "name": name, "group": group,
            "from": [x1, y1, zz], "to": [x2, y2, zz], "part": part}


def build_geometry(key, v):
    els = [
        cube("cabeza", "hi_head", [-5, 24, -5], [5, 34, 5], "head"),
        cube("torso", "torso", [-7, 14, -4], [7, 24, 4], "torso"),
        cube("cadera", "hip", [-6, 11, -3], [6, 14, 3], "hips"),
        # brazo (11 de alto) dividido en brazo (6) y antebrazo (5): el codo queda en y=18
        cube("brazo_der", "rightarm", [7, 18, -3], [12, 24, 2], "arm_r", (11, 0)),
        cube("antebrazo_der", "rightforearm", [7, 13, -3], [12, 18, 2], "arm_r", (11, 6)),
        cube("brazo_izq", "leftarm", [-12, 18, -3], [-7, 24, 2], "arm_l", (11, 0)),
        cube("antebrazo_izq", "leftforearm", [-12, 13, -3], [-7, 18, 2], "arm_l", (11, 6)),
        # pierna (10 de alto) dividida en muslo (5) y canilla (5): la rodilla queda en y=7
        cube("muslo_der", "rightleg", [1, 7, -3], [6, 12, 3], "leg", (10, 0)),
        cube("canilla_der", "rightshin", [1, 2, -3], [6, 7, 3], "leg", (10, 5)),
        cube("muslo_izq", "leftleg", [-6, 7, -3], [-1, 12, 3], "leg", (10, 0)),
        cube("canilla_izq", "leftshin", [-6, 2, -3], [-1, 7, 3], "leg", (10, 5)),
        cube("zapato_der", "rightshin", [1, 0, -4], [6, 2, 3], "shoe"),
        cube("zapato_izq", "leftshin", [-6, 0, -4], [-1, 2, 3], "shoe"),
    ]
    belly = v["belly"]
    if belly == "small":
        els.append(cube("panza", "torso", [-5, 15, -5], [5, 20, -4], "belly"))
        belly_front, belly_box = -5, (-5, 15, 5, 20)
    elif belly == "big":
        els.append(cube("panza", "torso", [-6, 13, -7], [6, 21, -4], "belly"))
        belly_front, belly_box = -7, (-6, 13, 6, 21)
    else:
        els.append(cube("panza", "torso", [-6, 14, -6], [6, 21, -4], "belly"))
        belly_front, belly_box = -6, (-6, 14, 6, 21)
    if v.get("hat"):
        els.append(cube("sombrero_ala", "hi_head", [-7, 34, -7], [7, 35, 7], "hat_brim"))
        els.append(cube("sombrero_copa", "hi_head", [-5, 35, -5], [5, 39, 5], "hat_crown"))

    # Capa de ropa de grosor 0: remarca prendas y accesorios.
    bx1, by1, bx2, by2 = belly_box
    torso_front = -4
    hips_front = -3
    head_front = -5
    arm_front = -3
    if key == "comun":
        els.append(plane("ropa_cuello", "torso", -3, 21, 3, 24, torso_front, "o_collar_v"))
        els.append(plane("ropa_botones", "torso", bx1, by1, bx2, by2, belly_front, "o_placket"))
        els.append(plane("ropa_cinturon", "hip", -6, 13, 6, 14, hips_front, "o_belt"))
    elif key == "gordito":
        els.append(plane("ropa_cuello", "torso", -4, 20, 4, 24, torso_front, "o_open_collar"))
        els.append(plane("ropa_botones", "torso", bx1, by1, bx2, by2, belly_front, "o_hawaii_placket"))
        els.append(plane("ropa_reloj", "rightforearm", 7, 14, 12, 15, arm_front, "o_watch"))
    elif key == "salsero":
        els.append(plane("ropa_musculosa", "torso", -7, 20, 7, 24, torso_front, "o_tank_trim"))
        els.append(plane("ropa_hebilla", "hip", -6, 13, 6, 14, hips_front, "o_buckle"))
        els.append(plane("ropa_pulsera", "rightforearm", 7, 14, 12, 15, arm_front, "o_bracelet"))
    elif key == "aura_67":
        # Retexturizado segun "RETEXTURIZADO REFERENCIA.jpg": cuello mao bordado, dos franjas
        # doradas a los lados de la tira central con botones, bordado en los punos.
        els.append(plane("ropa_bordado_pecho", "torso", -7, 14, 7, 24, torso_front, "o_aura_chest"))
        els.append(plane("ropa_bordado_panza", "torso", bx1, by1, bx2, by2, belly_front, "o_aura_belly"))
        els.append(plane("ropa_bordado_cadera", "hip", -6, 11, 6, 14, hips_front, "o_aura_hips"))
        els.append(plane("ropa_puno_der", "rightforearm", 7, 15, 12, 17, arm_front, "o_cuff_gold"))
        els.append(plane("ropa_puno_izq", "leftforearm", -12, 15, -7, 17, arm_front, "o_cuff_gold"))
    elif key == "fino":
        els.append(plane("ropa_solapas", "torso", -7, 14, 7, 24, torso_front, "o_lapels_top"))
        els.append(plane("ropa_botones", "torso", bx1, by1, bx2, by2, belly_front, "o_suit_buttons"))
        els.append(plane("ropa_cinta", "hi_head", -5, 35, 5, 36, -5, "o_hat_band"))
        els.append(plane("ropa_puno_der", "rightforearm", 7, 15, 12, 16, arm_front, "o_shirt_cuff"))
        els.append(plane("ropa_puno_izq", "leftforearm", -12, 15, -7, 16, arm_front, "o_shirt_cuff"))
    return els


GROUPS = {
    "root": ([0, 0, 0], None),
    "hip": ([0, 12, 0], "root"),
    "torso": ([0, 13, 0], "hip"),
    "hi_head": ([0, 24, 0], "torso"),
    "rightarm": ([9.5, 23, -0.5], "torso"),
    "rightforearm": ([9.5, 18, -0.5], "rightarm"),
    "leftarm": ([-9.5, 23, -0.5], "torso"),
    "leftforearm": ([-9.5, 18, -0.5], "leftarm"),
    "rightleg": ([3.5, 12, 0], "hip"),
    "rightshin": ([3.5, 7, 0], "rightleg"),
    "leftleg": ([-3.5, 12, 0], "hip"),
    "leftshin": ([-3.5, 7, 0], "leftleg"),
}


# ---------------------------------------------------------------- pintura

def paint_face(key, v, part, face, w, h, seed):
    transparent = part.startswith("o_")
    c = Canvas(w, h, seed, transparent)
    shirt, shirt_shade, pants = v["shirt"], v["shirt_shade"], v["pants"]
    # En caras laterales: este -> el frente queda a la izquierda; oeste -> a la derecha.
    front_left = face == "east"

    def hawaiian():
        for _ in range(max(3, w * h // 5)):
            x, y = c.rng.randrange(w), c.rng.randrange(h)
            col = c.rng.choice([(38, 66, 138), (84, 116, 186), (146, 168, 214), (60, 92, 160)])
            length = c.rng.randint(2, 3)
            dx = c.rng.choice([-1, 1])
            for i in range(length):
                c.px(x + i * dx, y + i, col)
                if c.rng.random() < 0.5:
                    c.px(x + i * dx + 1, y + i, shade(col, 25))

    def shirt_fill():
        c.fill(shirt, 5)
        if v["pattern"] == "hawaiian":
            hawaiian()
        else:
            for _ in range(w * h // 12):
                c.px(c.rng.randrange(w), c.rng.randrange(h), shirt_shade)

    if part == "head":
        # Las filas de barba coinciden en cada arista: cara, costados, nuca y debajo quedan unidos.
        def beard(x, y):
            c.px(x, y, shade(BEARD, c.rng.randint(-4, 4)))
        if face == "up":
            c.fill(SKIN, 4)
            for _ in range(4):
                c.px(c.rng.randint(2, w - 3), c.rng.randint(2, h - 3), SKIN_LIGHT)
        elif face == "down":
            c.fill(BEARD, 4)
        elif face == "south":
            c.fill(SKIN_SHADE, 4)
            for x in range(w):
                beard(x, h - 1)                     # nuca: une las patillas por detras
        elif face in ("east", "west"):
            c.fill(SKIN, 4)
            ear_x = w // 2
            c.rect(ear_x, 4, ear_x + 2, 7, SKIN_SHADE)
            c.px(ear_x, 5, shade(SKIN_SHADE, -15))
            for d in range(w):                      # d = distancia desde la arista de la cara
                x = d if front_left else w - 1 - d
                top = 6 if d == 0 else 7 if d < 3 else 8 if d < 5 else 9
                for y in range(top, h):
                    beard(x, y)
        else:  # north: la cara (igual en las 5 variantes)
            c.fill(SKIN, 4)
            c.row(1, SKIN_LIGHT, 3)
            c.rect(3, 0, 7, 1, SKIN_LIGHT)
            c.rect(1, 3, 4, 4, BEARD)       # cejas
            c.rect(6, 3, 9, 4, BEARD)
            c.px(2, 4, EYE_WHITE); c.px(3, 4, PUPIL)
            c.px(6, 4, PUPIL); c.px(7, 4, EYE_WHITE)
            c.rect(4, 5, 6, 6, SKIN_SHADE)  # nariz
            c.rect(4, 6, 6, 7, shade(SKIN_SHADE, -10))
            for y in range(6, h):           # patillas, siguen en los costados
                beard(0, y); beard(w - 1, y)
            for y in range(7, h):
                beard(1, y); beard(w - 2, y)
            c.rect(2, 7, 8, 8, BEARD, 3)    # bigote
            c.rect(1, 8, 9, 10, BEARD, 3)   # barba
            c.rect(4, 8, 6, 9, LIPS)
        return c.img

    if part in ("torso", "belly", "belly2"):
        shirt_fill()
        if v["sleeves"] == "tank" and part == "torso":
            if face == "north" or face == "south":
                c.rect(0, 0, 3, 3, SKIN, 4); c.rect(w - 3, 0, w, 3, SKIN, 4)
                c.rect(5, 0, w - 5, 2, SKIN, 4)
            elif face in ("east", "west"):
                c.rect(0, 0, w, 3, SKIN, 4)
                c.rect(0, 3, w, 5, SKIN, 4)
            elif face == "up":
                c.fill(SKIN, 4)
                c.rect(3, 0, 5, h, shirt); c.rect(w - 5, 0, w - 3, h, shirt)
        if v["sleeves"] in ("long", "short") and part == "torso" and face == "north" and key != "aura_67":
            mid = w / 2
            for y in range(0, 3):
                half = 2 - y * 0.75
                c.rect(int(mid - half), y, int(mid + half + 0.99), y + 1, SKIN, 3)
        if v.get("sleeves") == "suit" and part == "torso" and face == "north":
            # camisa blanca en V bajo el saco
            for y in range(0, 6):
                half = max(0, 3 - y // 2)
                c.rect(7 - half - 1, y, 7 + half + 1, y + 1, (236, 236, 236))
        if face == "down":
            c.fill(shirt_shade, 4)
        if part != "torso" and face == "north":
            c.row(0, shade(shirt, 14), 2)
            c.row(h - 1, shade(shirt_shade, -16), 2)
            c.row(h - 2, shirt_shade, 2)
        if part == "torso" and face == "north":
            c.row(h - 1, shade(shirt_shade, -20), 2)
        c.edge_shade(-10)
        return c.img

    if part == "hips":
        if v.get("long_top"):
            c.fill(shirt, 5)
            c.row(h - 1, shirt_shade)
        else:
            c.fill(pants, 5)
            if v["belt"] and face not in ("up", "down"):
                c.row(h - 1 - 2, v["belt"], 3)
        c.edge_shade(-10)
        return c.img

    if part == "leg":
        c.fill(pants, 5)
        if face in ("north", "south"):
            c.col(w // 2 if face == "north" else 0, shade(pants, -14), 2)
        c.row(h - 1, shade(pants, -18), 2)
        if v.get("long_top") and face not in ("up", "down"):
            c.row(0, shade(pants, -20))
        c.edge_shade(-12)
        return c.img

    if part == "shoe":
        c.fill(v["shoes"], 4)
        if face == "up":
            c.rect(0, 0, w, 2, shade(v["shoes"], 18))
        if face in ("north", "east", "west"):
            c.row(h - 1, shade(v["shoes"], -12), 1)
        return c.img

    if part in ("arm_r", "arm_l"):
        sleeves = v["sleeves"]
        if face == "up":
            if sleeves == "tank":
                c.fill(SKIN, 4)
            else:
                shirt_fill()
            return c.img
        if face == "down":
            c.fill(SKIN_SHADE, 3)
            return c.img
        if sleeves == "tank":
            c.fill(SKIN, 4)
            c.row(1, SKIN_LIGHT)
        elif sleeves == "short":
            c.fill(SKIN, 4)
            c.rect(0, 0, w, 5, shirt, 4)
            if v["pattern"] == "hawaiian":
                for _ in range(4):
                    c.px(c.rng.randrange(w), c.rng.randrange(5), (60, 92, 160))
            c.row(4, shirt_shade)
        else:
            shirt_fill()
            if sleeves == "suit":
                c.row(h - 3, (236, 236, 236), 2)   # puno de camisa
            else:
                c.row(h - 3, shirt_shade, 2)
            c.rect(0, h - 2, w, h, SKIN, 4)          # manos
        if sleeves in ("tank", "short"):
            c.rect(0, h - 2, w, h, SKIN_SHADE, 3)
        c.edge_shade(-10)
        return c.img

    if part == "hat_brim":
        c.fill((236, 236, 232), 4)
        c.edge_shade(-14)
        return c.img
    if part == "hat_crown":
        c.fill((238, 238, 234), 4)
        if face == "up":
            c.rect(3, 4, w - 3, 6, (214, 214, 210))
        elif face != "down":
            c.row(h - 1, (40, 40, 40), 2)          # cinta negra
        return c.img

    # ---------------------------------------------------- capa de ropa (planos)
    gold, gold_dark = (226, 168, 44), (172, 116, 22)
    if part == "o_collar_v":
        line = (196, 196, 208)
        mid = w // 2
        for y in range(0, h):
            half = max(0, int(2 - y * 0.75)) + 1
            c.px(mid - half - 1, y, line); c.px(mid + half, y, line)
    elif part == "o_placket":
        mid = w // 2
        c.col(mid, (200, 200, 212), 2)
        for y in range(1, h, 2):
            c.px(mid, y, (150, 150, 162))
        c.rect(2, 1, 4, 3, (212, 212, 224))                   # bolsillo
        c.row(1, CLEAR, 0) if False else None
    elif part == "o_belt":
        c.rect(0, 0, w, h, v["belt"], 3)
        c.rect(w // 2 - 1, 0, w // 2 + 1, h, (170, 170, 176))
    elif part == "o_open_collar":
        collar = (228, 232, 244)
        mid = w // 2
        for y in range(0, h):
            half = max(0, int(2 - y * 0.75)) + 1
            c.px(mid - half - 1, y, collar); c.px(mid - half - 2, y, shade(collar, -18))
            c.px(mid + half, y, collar); c.px(mid + half + 1, y, shade(collar, -18))
    elif part == "o_hawaii_placket":
        mid = w // 2
        c.col(mid, (214, 220, 236), 1)
        for y in range(0, h, 2):
            c.px(mid, y, (240, 240, 248))
    elif part == "o_tank_trim":
        # ribete de la musculosa: tiras de los hombros y escote
        trim = (112, 132, 150)
        c.rect(3, 0, 4, h, trim); c.rect(w - 4, 0, w - 3, h, trim)
        for y in range(0, 2):
            c.px(4 + y, y + 1, trim); c.px(w - 5 - y, y + 1, trim)
        c.rect(6, 2, w - 6, 3, trim)
    elif part == "o_watch":
        c.rect(0, 0, w, h, (160, 160, 168)); c.rect(1, 0, 3, h, (210, 210, 220))
    elif part == "o_glasses_clear":
        frame = (30, 30, 34)
        c.rect(0, 0, 4, 1, frame); c.rect(4, 0, 8, 1, frame)
        c.px(0, 1, frame); c.px(3, 1, frame); c.px(4, 1, frame); c.px(7, 1, frame)
    elif part == "o_buckle":
        c.rect(w // 2 - 1, 0, w // 2 + 1, h, (196, 196, 204))
    elif part == "o_bracelet":
        c.rect(0, 0, w, h, gold); c.px(1, 0, (250, 214, 110)); c.px(3, 0, (250, 214, 110))
    elif part == "o_embroidery_top":
        # bordado dorado en el pecho, por encima de la panza
        mid = w // 2
        for y in range(7, h):
            row = h - 1 - y
            span = 2 + row
            for x in range(mid - span, mid + span):
                if (x + y) % 2 == 0 or abs(x - mid + 0.5) > span - 1.5:
                    c.px(x, y, gold if (x + y) % 3 else gold_dark)
    elif part == "o_embroidery_belly":
        mid = w // 2
        for y in range(h):
            span = max(1, 5 - y)
            for x in range(mid - span, mid + span):
                if (x * 3 + y) % 4 != 0:
                    c.px(x, y, gold if (x + y) % 2 else gold_dark)
            c.px(mid - span - 1, y, gold_dark); c.px(mid + span, y, gold_dark)
    elif part == "o_chain":
        mid = w // 2
        for y in range(0, 4):
            c.px(mid - 4 + y, y, (238, 200, 90)); c.px(mid + 3 - y, y, (238, 200, 90))
        c.rect(mid - 1, 3, mid + 1, 4, gold)
        c.rect(mid - 1, 4, mid + 1, 7, (236, 236, 236))    # credencial
        c.px(mid - 1, 5, (90, 110, 150)); c.px(mid, 6, (60, 60, 60))
    elif part == "o_sunglasses":
        c.rect(1, 0, 9, 1, (12, 12, 14))
        c.rect(1, 1, 4, 2, (18, 18, 22)); c.rect(6, 1, 9, 2, (18, 18, 22))
        c.px(2, 1, (90, 90, 110)); c.px(7, 1, (90, 90, 110))
        c.px(0, 0, (60, 60, 64)); c.px(9, 0, (60, 60, 64))
    elif part in ("o_aura_chest", "o_aura_belly", "o_aura_hips"):
        line, light = (190, 84, 22), (252, 214, 104)
        top_y = {"o_aura_chest": 24, "o_aura_belly": 21, "o_aura_hips": 14}[part]
        right_x = w / 2
        reach = 4 if part == "o_aura_hips" else 5
        for row in range(h):
            wy = top_y - row                      # fila del mundo (de arriba hacia abajo)
            for col in range(w):
                ax = abs(right_x - col - 0.5)     # distancia al centro en el mundo
                if ax < 1:                        # tira central roja con botones blancos
                    if col == int(right_x) and wy % 2 == 0:
                        c.px(col, row, (242, 240, 236))
                    continue
                if ax > reach:
                    continue
                k = int(ax - 1)                   # 0 = junto a la tira, reach-2 = borde exterior
                if k == reach - 2:
                    color = line if wy % 2 else gold
                elif k == 0:
                    color = gold
                else:
                    color = (line, light, gold)[(wy + k) % 3]
                c.px(col, row, color)
        if part == "o_aura_chest":                # cuello mao bordado
            for col in range(w):
                ax = abs(right_x - col - 0.5)
                if ax < 3:
                    c.px(col, 0, gold if col % 2 else light)
                    c.px(col, 1, line)
        if part == "o_aura_hips":                 # ruedo de la tunica
            for col in range(w):
                c.px(col, h - 1, gold if col % 2 else line)
    elif part == "o_cuff_gold":
        for x in range(w):
            c.px(x, 0, gold if x % 2 else (252, 214, 104))
        c.row(1, (190, 84, 22), 0)
    elif part == "o_hem_gold":
        for x in range(w):
            c.px(x, 0, gold if x % 2 else gold_dark)
    elif part == "o_lapels_top":
        lapel = (160, 144, 110)
        for y in range(0, h - 7):
            pass
        for y in range(7, h):
            depth = h - 1 - y
            c.px(4 + depth // 2, y, lapel); c.px(w - 5 - depth // 2, y, lapel)
            c.px(3 + depth // 2, y, shade(lapel, -14)); c.px(w - 4 - depth // 2, y, shade(lapel, -14))
        c.rect(1, 8, 3, 9, (236, 236, 236))     # panuelo en el bolsillo
    elif part == "o_suit_buttons":
        mid = w // 2
        c.col(mid, (164, 148, 114), 1)
        for y in (1, 3, 5):
            c.px(mid - 1, y, (110, 92, 66))
        c.rect(1, 4, 4, 5, (164, 148, 114)); c.rect(w - 4, 4, w - 1, 5, (164, 148, 114))  # tapas de bolsillo
    elif part == "o_hat_band":
        c.rect(0, 0, w, h, (34, 34, 36))
        c.px(2, 0, (70, 70, 74))
    elif part == "o_shirt_cuff":
        c.rect(0, 0, w, h, (240, 240, 240)); c.px(1, 0, (200, 200, 200))
    return c.img


# ---------------------------------------------------------------- atlas y bbmodel

FACES = ("north", "east", "south", "west", "up", "down")


def face_size(el, face):
    (x1, y1, z1), (x2, y2, z2) = el["from"], el["to"]
    w, h, d = abs(x2 - x1), abs(y2 - y1), abs(z2 - z1)
    return {"north": (w, h), "south": (w, h), "east": (d, h), "west": (d, h),
            "up": (w, d), "down": (w, d)}[face]


def pack(rects):
    """Shelf packing simple. rects: lista de (id, w, h). Devuelve id -> (x, y)."""
    out, x, y, shelf = {}, 0, 0, 0
    for rid, w, h in sorted(rects, key=lambda r: (-r[2], -r[1])):
        if x + w > TEX:
            x, y, shelf = 0, y + shelf, 0
        if y + h > TEX:
            raise RuntimeError("La textura de 128x128 no alcanza")
        out[rid] = (x, y)
        x += w
        shelf = max(shelf, h)
    return out


def keyframe(channel, t, xyz, model, anim, bone, idx, interpolation):
    return {"channel": channel, "data_points": [{"x": str(xyz[0]), "y": str(xyz[1]), "z": str(xyz[2])}],
            "uuid": stable_uuid(model, anim, bone, channel, str(idx)), "time": t, "color": -1,
            "interpolation": interpolation}


def animator(model, anim, bone, group_uuid, frames, interpolation, length):
    for channel in {f[0] for f in frames}:
        times = [f[1] for f in frames if f[0] == channel]
        if min(times) != 0 or max(times) != length:
            raise ValueError(f"{anim}/{bone}/{channel}: el loop tiene que tener claves en 0 y en {length}")
    kfs = [keyframe(channel, t, xyz, model, anim, bone, i, interpolation)
           for i, (channel, t, xyz) in enumerate(frames)]
    return group_uuid, {"name": bone, "type": "bone", "rotation_global": False,
                        "quaternion_interpolation": False, "keyframes": kfs}


def base_animations():
    return {
        "idle": (2.0, "catmullrom", {
            "hi_head": [("rotation", 0, (0, 0, 0)), ("rotation", 1, (-3, 4, 0)), ("rotation", 2, (0, 0, 0))],
            "torso": [("position", 0, (0, 0, 0)), ("position", 1, (0, 0.3, 0)), ("position", 2, (0, 0, 0))],
            "rightarm": [("rotation", 0, (0, 0, 0)), ("rotation", 1, (0, 0, 3)), ("rotation", 2, (0, 0, 0))],
            "leftarm": [("rotation", 0, (0, 0, 0)), ("rotation", 1, (0, 0, -3)), ("rotation", 2, (0, 0, 0))],
            "rightforearm": [("rotation", 0, (4, 0, 0)), ("rotation", 1, (8, 0, 0)), ("rotation", 2, (4, 0, 0))],
            "leftforearm": [("rotation", 0, (4, 0, 0)), ("rotation", 1, (8, 0, 0)), ("rotation", 2, (4, 0, 0))],
        }),
        "walk": (1.0, "catmullrom", {
            "rightleg": [("rotation", 0, (25, 0, 0)), ("rotation", 0.5, (-25, 0, 0)), ("rotation", 1, (25, 0, 0))],
            "leftleg": [("rotation", 0, (-25, 0, 0)), ("rotation", 0.5, (25, 0, 0)), ("rotation", 1, (-25, 0, 0))],
            "rightshin": [("rotation", 0, (0, 0, 0)), ("rotation", 0.25, (-35, 0, 0)), ("rotation", 0.5, (-10, 0, 0)),
                          ("rotation", 1, (0, 0, 0))],
            "leftshin": [("rotation", 0, (-10, 0, 0)), ("rotation", 0.5, (0, 0, 0)), ("rotation", 0.75, (-35, 0, 0)),
                         ("rotation", 1, (-10, 0, 0))],
            "rightarm": [("rotation", 0, (-20, 0, 0)), ("rotation", 0.5, (20, 0, 0)), ("rotation", 1, (-20, 0, 0))],
            "leftarm": [("rotation", 0, (20, 0, 0)), ("rotation", 0.5, (-20, 0, 0)), ("rotation", 1, (20, 0, 0))],
            "rightforearm": [("rotation", 0, (10, 0, 0)), ("rotation", 0.5, (25, 0, 0)), ("rotation", 1, (10, 0, 0))],
            "leftforearm": [("rotation", 0, (25, 0, 0)), ("rotation", 0.5, (10, 0, 0)), ("rotation", 1, (25, 0, 0))],
            "root": [("position", 0, (0, 0, 0)), ("position", 0.25, (0, 0.5, 0)), ("position", 0.5, (0, 0, 0)),
                     ("position", 0.75, (0, 0.5, 0)), ("position", 1, (0, 0, 0))],
        }),
    }


def animations(model, guid, with_dances):
    import bailes_salsero
    defs = {name: (length, interp, frames, False) for name, (length, interp, frames) in base_animations().items()}
    if with_dances:
        for name in bailes_salsero.DANCES:
            length, interp, frames = bailes_salsero.load(name)
            defs[name] = (length, interp, frames, True)
    out = []
    for name, (length, interp, bones, override) in defs.items():
        animators = dict(animator(model, name, bone, guid[bone], frames, interp, length)
                         for bone, frames in bones.items())
        out.append({"uuid": stable_uuid(model, "anim", name), "name": name, "loop": "loop", "override": override,
                    "length": length, "snapping": 24, "selected": False, "anim_time_update": "",
                    "blend_weight": "", "start_delay": "", "loop_delay": "", "animators": animators})
    return out


def paint_piece(key, v, el, face, w, h):
    split = el.get("split")
    limb = el["name"].split("_")[-1]
    seed = f"{key}/{el['name']}/{face}"
    if not split:
        return paint_face(key, v, el["part"], face, w, h, seed)
    full_h, top = split
    seed = f"{key}/{el['part']}/{limb}/{face}"
    if face in ("north", "south", "east", "west"):
        full = paint_face(key, v, el["part"], face, w, full_h, seed)
        return full.crop((0, top, w, top + h))
    # Tapas: la de arriba del primer tramo y la de abajo del ultimo son las del miembro entero;
    # las de la union usan la fila del frente justo en el corte.
    is_first, is_last = top == 0, top + (el["to"][1] - el["from"][1]) >= full_h
    if (face == "up" and is_first) or (face == "down" and is_last):
        return paint_face(key, v, el["part"], face, w, h, seed)
    front = paint_face(key, v, el["part"], "north", w, full_h, f"{key}/{el['part']}/{limb}/north")
    cut = top if face == "up" else top + int(el["to"][1] - el["from"][1]) - 1
    return front.crop((0, cut, w, cut + 1)).resize((w, h), Image.NEAREST)


def build_model(key):
    v = VARIANTS[key]
    model = f"bigcasares_don_pollo_{key}"
    els = build_geometry(key, v)

    rects = []
    for i, el in enumerate(els):
        faces = ["north"] if el["kind"] == "plane" else FACES
        for f in faces:
            w, h = face_size(el, f)
            rects.append(((i, f), int(round(w)), int(round(h))))
    layout = pack(rects)

    atlas = Image.new("RGBA", (TEX, TEX), CLEAR)
    for (i, f), w, h in rects:
        atlas.paste(paint_piece(key, v, els[i], f, w, h), layout[(i, f)])

    buf = io.BytesIO()
    atlas.save(buf, "PNG")
    source = "data:image/png;base64," + base64.b64encode(buf.getvalue()).decode()

    guid = {g: stable_uuid(model, "group", g) for g in GROUPS}
    elements, children = [], {g: [] for g in GROUPS}
    for i, el in enumerate(els):
        euid = stable_uuid(model, "el", el["name"])
        faces = {}
        for f in FACES:
            if (i, f) in layout:
                x, y = layout[(i, f)]
                w, h = face_size(el, f)
                faces[f] = {"uv": [x, y, x + int(round(w)), y + int(round(h))], "texture": 0}
            else:
                faces[f] = {"uv": [0, 0, 0, 0], "texture": None}
        elements.append({
            "name": el["name"], "type": "cube", "uuid": euid, "box_uv": False, "rescale": False,
            "locked": False, "render_order": "default", "allow_mirror_modeling": True,
            "from": el["from"], "to": el["to"], "autouv": 0, "color": 0, "inflate": 0,
            "origin": GROUPS[el["group"]][0], "rotation": [0, 0, 0], "faces": faces,
            "export": True, "visibility": True, "shade": True, "mirror_uv": False, "light_emission": 0,
        })
        children[el["group"]].append(euid)

    groups = []
    for g, (origin, parent) in GROUPS.items():
        groups.append({"name": g, "uuid": guid[g], "export": True, "locked": False, "scope": 0,
                       "origin": origin, "rotation": [0, 0, 0], "color": 0, "reset": False,
                       "shade": True, "mirror_uv": False, "visibility": True, "autouv": 0, "isOpen": True})

    def tree(g):
        kids = list(children[g])
        for child, (_, parent) in GROUPS.items():
            if parent == g:
                kids.append(tree(child))
        return {"uuid": guid[g], "isOpen": True, "children": kids}

    anims = animations(model, guid, with_dances=key == "salsero")

    data = {
        "meta": {"format_version": "5.0", "model_format": "free", "box_uv": False},
        "name": model, "model_identifier": "", "visible_box": [2, 3, 0],
        "variable_placeholders": "", "variable_placeholder_buttons": [],
        "timeline_setups": [], "unhandled_root_fields": {},
        "resolution": {"width": TEX, "height": TEX},
        "elements": elements, "groups": groups, "outliner": [tree("root")],
        "textures": [{
            "path": "", "name": f"{model}.png", "folder": "", "namespace": "", "id": "0", "group": "",
            "width": TEX, "height": TEX, "uv_width": TEX, "uv_height": TEX, "particle": False,
            "use_as_default": False, "layers_enabled": False, "sync_to_project": "",
            "render_mode": "default", "render_sides": "auto", "pbr_channel": "color",
            "frame_time": 1, "frame_order_type": "loop", "frame_order": "", "frame_interpolate": False,
            "visible": True, "internal": True, "saved": True,
            "uuid": stable_uuid(model, "texture"), "relative_path": f"{model}.png", "source": source,
        }],
        "animations": anims,
    }
    return model, data, els, layout, atlas


# ---------------------------------------------------------------- vistas previas

def render_view(els, layout, atlas, view, scale=10):
    """Ortografica frontal (desde el norte) o lateral (desde el este)."""
    if view == "back":
        face, depth = "south", lambda el: -el["to"][2]
        hx = lambda el: (el["from"][0], el["to"][0])
    elif view == "front":
        face, depth = "north", lambda el: el["from"][2]
        hx = lambda el: (-el["to"][0], -el["from"][0])
    else:
        face, depth = "east", lambda el: -el["to"][0]
        hx = lambda el: (el["from"][2], el["to"][2])
    size_w, size_h = 34, 42
    img = Image.new("RGBA", (size_w * scale, size_h * scale), (205, 210, 220, 255))
    order = sorted(range(len(els)), key=lambda i: -depth(els[i]))
    for i in order:
        if (i, face) not in layout:
            continue
        el = els[i]
        x, y = layout[(i, face)]
        w, h = face_size(el, face)
        w, h = int(round(w)), int(round(h))
        tile = atlas.crop((x, y, x + w, y + h)).resize((w * scale, h * scale), Image.NEAREST)
        left = hx(el)[0] + size_w / 2
        top = size_h - el["to"][1] - 2
        img.alpha_composite(tile, (int(left * scale), int(top * scale)))
    return img


def main():
    OUT_MODELS.mkdir(parents=True, exist_ok=True)
    OUT_PREVIEWS.mkdir(parents=True, exist_ok=True)
    sheet = Image.new("RGBA", (5 * 340, 420 * 3), (255, 255, 255, 255))
    for idx, key in enumerate(VARIANTS):
        model, data, els, layout, atlas = build_model(key)
        (OUT_MODELS / f"{model}.bbmodel").write_text(json.dumps(data, ensure_ascii=False), encoding="utf-8")
        atlas.resize((TEX * 4, TEX * 4), Image.NEAREST).save(OUT_PREVIEWS / f"{model}_textura.png")
        front = render_view(els, layout, atlas, "front")
        side = render_view(els, layout, atlas, "side")
        sheet.paste(front, (idx * 340, 0))
        sheet.paste(side, (idx * 340, 420))
        sheet.paste(render_view(els, layout, atlas, "back"), (idx * 340, 840))
        print("ok", model, len(els), "elementos")
    sheet.save(OUT_PREVIEWS / "todos.png")


if __name__ == "__main__":
    main()
