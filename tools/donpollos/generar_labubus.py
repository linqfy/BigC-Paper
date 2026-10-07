"""Genera los 4 Labubus (verde, rojo, rosa y azul), parados, copiando referencias_modelo/labubus.png.

Morfologia de la referencia: cabeza enorme casi cubica (la cara pintada plana en el frente, ocupando casi
todo), orejas gruesas y largas arriba, cuerpo como una batita que se ensancha abajo, brazos abiertos en
diagonal y piernas color piel (aca paradas en vez de sentadas). Textura: traje del color con pelito
moteado, cara piel con cejas en diagonal, ojos blancos con la pupila hacia adentro, nariz del color del
traje, cachetes colorados y sonrisa fina negra con dientitos blancos colgando.

Todo se arma en pixeles de textura (1 pixel = 1 texel) y se pasa a unidades de Blockbench con K.

Uso: python tools/generar_labubus.py
"""
import sys
from pathlib import Path

sys.path.insert(0, str(Path(__file__).resolve().parent))
import generar_modelos as gm  # noqa: E402
from generar_wsp import Model  # noqa: E402

K = 0.5  # unidades de Blockbench por pixel de textura: mide ~1.2 bloques

VARIANTS = {
    "verde": (40, 206, 30),
    "rojo": (232, 18, 18),
    "rosa": (232, 18, 112),
    "azul": (70, 24, 240),
}
SKIN = (226, 168, 126)
SKIN_LIGHT = (240, 192, 154)
SKIN_SHADE = (204, 144, 104)
BROW = (176, 116, 84)
EYE_WHITE = (244, 244, 244)
PUPIL = (64, 44, 34)
BLUSH = (236, 128, 108)
MOUTH = (30, 22, 20)
TOOTH = (250, 250, 250)
EAR_INNER = (232, 184, 150)
EAR_BLUE = (30, 140, 210)
ARM_ANGLE = 28  # brazos abiertos en diagonal, como en la referencia


def P(v):
    return [round(c * K, 4) for c in v]


GROUPS_PX = {
    "root": ([0, 0, 0], None),
    "body": ([0, 6, 0], "root"),
    "hi_head": ([0, 14, 0], "body"),
    "rightear": ([4, 29, 0], "hi_head"),
    "leftear": ([-4, 29, 0], "hi_head"),
    "rightarm": ([7.5, 13.5, 0], "body", [0, 0, ARM_ANGLE]),
    "leftarm": ([-7.5, 13.5, 0], "body", [0, 0, -ARM_ANGLE]),
    "rightleg": ([3.5, 6, -0.5], "root"),
    "leftleg": ([-3.5, 6, -0.5], "root"),
}


def fur(c, color):
    """Pelito del traje: base saturada con cuadraditos un poco mas oscuros y mas claros."""
    c.fill(color, 5)
    for _ in range(c.w * c.h // 5):
        x, y = c.rng.randrange(c.w), c.rng.randrange(c.h)
        c.rect(x, y, x + c.rng.choice((1, 1, 2)), y + 1, gm.shade(color, c.rng.choice((-30, -18, 14))), 4)


def face_texture(c, color):
    """Frente de la cabeza (18x15): borde de capucha finito y la cara pintada ocupando casi todo."""
    fur(c, color)
    c.rect(2, 2, 16, 14, SKIN, 3)
    for x, y in ((2, 2), (15, 2), (2, 13), (15, 13)):  # esquinas redondeadas
        c.px(x, y, gm.shade(color, -10))
    c.rect(3, 3, 15, 4, SKIN_LIGHT, 3)  # frente un poco mas clara
    # cejas en diagonal hacia la nariz (cara de picaro)
    for x, y in ((4, 4), (5, 4), (6, 5), (7, 5)):
        c.px(x, y, BROW)
        c.px(17 - x, y, BROW)
    # ojos: blanco con la pupila del lado de adentro
    c.rect(4, 6, 8, 9, EYE_WHITE)
    c.rect(6, 6, 8, 9, PUPIL)
    c.rect(10, 6, 14, 9, EYE_WHITE)
    c.rect(10, 6, 12, 9, PUPIL)
    # nariz del color del traje
    c.rect(8, 8, 10, 9, gm.shade(color, 10))
    # cachetes
    c.rect(2, 9, 4, 11, BLUSH, 4)
    c.rect(14, 9, 16, 11, BLUSH, 4)
    # sonrisa fina con las puntas para arriba y dientitos colgando
    c.rect(4, 11, 14, 12, MOUTH)
    c.px(3, 10, MOUTH)
    c.px(14, 10, MOUTH)
    for x in (5, 7, 10, 12):
        c.px(x, 12, TOOTH)
    c.edge_shade(-18)


def build(key, color):
    light = gm.shade(color, 34)
    dark = gm.shade(color, -46)
    name = f"bigcasares_labubu_{key}"
    groups = {g: (P(v[0]), *v[1:]) for g, v in GROUPS_PX.items()}
    m = Model(name, groups, density=1 / K, tex=128)

    def cube(n, g, a, b, part):
        m.cube(n, g, P(a), P(b), part)

    for side, sx in (("der", 1), ("izq", -1)):
        x1, x2 = sorted((sx * 1, sx * 6))
        cube(f"pierna_{side}", "rightleg" if sx > 0 else "leftleg", [x1, 0, -3], [x2, 7, 2], "leg")
        ax1, ax2 = sorted((sx * 7, sx * 12))
        arm = "rightarm" if sx > 0 else "leftarm"
        cube(f"manga_{side}", arm, [ax1, 6, -2.5], [ax2, 14, 2.5], "sleeve")
        hx1, hx2 = sorted((sx * 7.5, sx * 11.5))
        cube(f"mano_{side}", arm, [hx1, 5, -2], [hx2, 6, 2], "hand")
        ex1, ex2 = sorted((sx * 2, sx * 6))
        cube(f"oreja_{side}", "rightear" if sx > 0 else "leftear", [ex1, 29, -2], [ex2, 39, 2], "ear")
    # batita: parte de abajo mas ancha (con ruedo) y parte de arriba
    cube("bata_abajo", "body", [-9, 6, -5], [9, 10, 5], "robe_low")
    cube("bata_arriba", "body", [-8, 10, -4.5], [8, 14.5, 4.5], "robe_up")
    cube("cabeza", "hi_head", [-9, 14, -7.5], [9, 29, 7.5], "head")

    def paint(el, face, w, h, seed):
        c = gm.Canvas(w, h, seed)
        part = el["part"]
        if part == "head":
            if face == "north":
                face_texture(c, color)
                return c.img
            fur(c, color)
            if face == "down":
                c.fill(dark, 5)
            c.edge_shade(-22)
            return c.img
        if part in ("robe_low", "robe_up", "sleeve"):
            fur(c, color)
            if part == "robe_low" and face in ("north", "south", "east", "west"):
                c.row(0, light, 5)  # ruedo de la batita, mas claro como en la referencia
            if part == "sleeve" and face in ("north", "south", "east", "west"):
                c.row(h - 1, light, 4)
            if face == "down":
                c.fill(dark, 5)
            c.edge_shade(-22)
            return c.img
        if part == "ear":
            fur(c, color)
            if face == "north":
                c.rect(1, 1, w - 1, h - 1, EAR_INNER, 3)
                c.rect(1, 1, 2, h - 1, SKIN_LIGHT, 3)
                c.rect(2, h // 2 - 1, 3, h - 2, EAR_BLUE, 8)  # raya azul abajo del interior
            c.edge_shade(-22)
            return c.img
        if part in ("leg", "hand"):
            c.fill(SKIN, 3)
            if part == "leg" and face == "north":
                c.rect(1, 1, w - 1, h - 1, SKIN_LIGHT, 3)  # panel claro como en la referencia
            if part == "leg" and face == "down":
                c.fill(SKIN_SHADE, 3)
            c.edge_shade(-20)
            return c.img
        c.fill((255, 0, 255), 0)
        return c.img

    anims = {
        "idle": (2.4, "catmullrom", {
            "rightear": [("rotation", 0, (0, 0, 0)), ("rotation", 1.2, (0, 0, -7)), ("rotation", 2.4, (0, 0, 0))],
            "leftear": [("rotation", 0, (0, 0, 0)), ("rotation", 1.2, (0, 0, 7)), ("rotation", 2.4, (0, 0, 0))],
            "rightarm": [("rotation", 0, (0, 0, 0)), ("rotation", 1.2, (0, 0, 6)), ("rotation", 2.4, (0, 0, 0))],
            "leftarm": [("rotation", 0, (0, 0, 0)), ("rotation", 1.2, (0, 0, -6)), ("rotation", 2.4, (0, 0, 0))],
            "hi_head": [("rotation", 0, (0, 0, 0)), ("rotation", 1.2, (-3, 0, 3)), ("rotation", 2.4, (0, 0, 0))],
        }),
        "walk": (0.7, "linear", {
            "rightleg": [("rotation", 0, (26, 0, 0)), ("rotation", 0.35, (-26, 0, 0)), ("rotation", 0.7, (26, 0, 0))],
            "leftleg": [("rotation", 0, (-26, 0, 0)), ("rotation", 0.35, (26, 0, 0)), ("rotation", 0.7, (-26, 0, 0))],
            "rightarm": [("rotation", 0, (-24, 0, 0)), ("rotation", 0.35, (24, 0, 0)), ("rotation", 0.7, (-24, 0, 0))],
            "leftarm": [("rotation", 0, (24, 0, 0)), ("rotation", 0.35, (-24, 0, 0)), ("rotation", 0.7, (24, 0, 0))],
            "body": [("rotation", 0, (0, 0, -3)), ("rotation", 0.35, (0, 0, 3)), ("rotation", 0.7, (0, 0, -3))],
            "rightear": [("rotation", 0, (0, 0, 0)), ("rotation", 0.35, (8, 0, -5)), ("rotation", 0.7, (0, 0, 0))],
            "leftear": [("rotation", 0, (0, 0, 0)), ("rotation", 0.35, (8, 0, 5)), ("rotation", 0.7, (0, 0, 0))],
        }),
    }
    m.build(paint, anims)


if __name__ == "__main__":
    for key, color in VARIANTS.items():
        build(key, color)
