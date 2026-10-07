"""Genera al Coronel (el de KFC): la morfologia exacta de un jugador (Steve) con su skin.

Cabeza 8x8x8, torso 8x12x4, brazos y piernas 4x12x4, 1 pixel de textura por unidad, como una skin de
Minecraft (referencias_modelo/coronel.jpg): pelo blanco, anteojos negros, cejas, bigote y chivita blancos,
traje blanco con corbatin negro, delantal rojo adelante, manos de piel y zapatos negros.

Uso: python tools/generar_coronel.py
"""
import sys
from pathlib import Path

sys.path.insert(0, str(Path(__file__).resolve().parent))
import generar_modelos as gm  # noqa: E402
from generar_wsp import Model  # noqa: E402

NAME = "bigcasares_coronel"

W = (236, 236, 234)     # traje y pelo blanco
WS = (206, 206, 204)    # sombra del blanco
S = (228, 170, 124)     # piel
SS = (204, 146, 102)    # piel sombra
K = (24, 24, 28)        # negro (anteojos, corbatin, zapatos)
L = (70, 76, 104)       # vidrio de los anteojos
R = (150, 38, 40)       # delantal rojo
RS = (124, 28, 30)      # delantal sombra

# letras -> colores para dibujar las caras a mano (filas de arriba a abajo, columnas como las ve quien mira esa cara)
PALETTE = {"W": W, "w": WS, "S": S, "s": SS, "K": K, "L": L, "R": R, "r": RS}

HEAD = {
    "north": ["WWWWWWWW",
              "WSSSSSSW",
              "SWWSSWWS",
              "KLLKKLLK",
              "SKKSSKKS",
              "SSWWWWSS",
              "SSWSSWSS",
              "SSSWWSSS"],
    # costados: pelo arriba y atras, oreja, patilla del anteojo hacia adelante (la izquierda de la imagen es la nuca en "east")
    "east": ["WWWWWWWW",
             "WWWWWWWW",
             "WWWWSSSW",
             "WWWSKKKK",
             "WWWSSSSS",
             "WWSSSSSS",
             "WWSSSSSS",
             "WWSSSSSS"],
    "west": ["WWWWWWWW",
             "WWWWWWWW",
             "WSSSWWWW",
             "KKKKSWWW",
             "SSSSSWWW",
             "SSSSSSWW",
             "SSSSSSWW",
             "SSSSSSWW"],
    "south": ["WWWWWWWW"] * 6 + ["wWWWWWWw", "SSSSSSSS"],
    "up": ["WWWWWWWW"] * 8,
    "down": ["SSSSSSSS"] * 8,
}

BODY = {
    "north": ["WWWKKWWW",
              "WWWKKWWW",
              "WWRRKRRW",
              "WRRRKRRW",
              "WRRKRRRW",
              "WRRRRRRW",
              "WRRRRRRW",
              "WRRRRRRW",
              "WRRRRRRW",
              "WRRRRRRW",
              "WrRRRRrW",
              "WrrrrrrW"],
    "south": ["WWWWWWWW"] * 11 + ["wwwwwwww"],
    "east": ["WWWW"] * 11 + ["wwww"],
    "west": ["WWWW"] * 11 + ["wwww"],
    "up": ["WWWWWWWW"] * 4,
    "down": ["wwwwwwww"] * 4,
}

ARM = {f: ["WWWW"] * 8 + ["wwww", "SSSS", "SSSS", "ssss"] for f in ("north", "south", "east", "west")}
ARM["up"] = ["WWWW"] * 4
ARM["down"] = ["SSSS"] * 4

# el delantal tapa el frente de las dos piernas; la derecha tiene la raya blanca del borde
LEG_RIGHT = {
    "north": ["RRRR", "RRRR", "RRWR", "RRWR", "RRWR", "RRWR", "RRWR", "RRWR", "rrWr", "WWWW", "WWWW", "KKKK"],
    "south": ["WWWW"] * 10 + ["wwww", "KKKK"],
    "east": ["WWWW"] * 10 + ["wwww", "KKKK"],
    "west": ["WWWW"] * 10 + ["wwww", "KKKK"],
    "up": ["WWWW"] * 4,
    "down": ["KKKK"] * 4,
}
LEG_LEFT = dict(LEG_RIGHT, north=["RRRR"] * 8 + ["rrrr", "WWWW", "WWWW", "KKKK"])


def build():
    groups = {
        "root": ([0, 0, 0], None),
        "body": ([0, 24, 0], "root"),
        "hi_head": ([0, 24, 0], "body"),
        "rightarm": ([5, 22, 0], "body"),
        "leftarm": ([-5, 22, 0], "body"),
        "rightleg": ([2, 12, 0], "root"),
        "leftleg": ([-2, 12, 0], "root"),
    }
    m = Model(NAME, groups, density=1, tex=64)
    m.cube("cabeza", "hi_head", [-4, 24, -4], [4, 32, 4], HEAD)
    m.cube("torso", "body", [-4, 12, -2], [4, 24, 2], BODY)
    m.cube("brazo_der", "rightarm", [4, 12, -2], [8, 24, 2], ARM)
    m.cube("brazo_izq", "leftarm", [-8, 12, -2], [-4, 24, 2], ARM)
    m.cube("pierna_der", "rightleg", [0, 0, -2], [4, 12, 2], LEG_RIGHT)
    m.cube("pierna_izq", "leftleg", [-4, 0, -2], [0, 12, 2], LEG_LEFT)

    def paint(el, face, w, h, seed):
        c = gm.Canvas(w, h, seed)
        rows = el["part"][face]
        for y, row in enumerate(rows):
            for x, ch in enumerate(row):
                base = PALETTE[ch]
                c.px(x, y, gm.shade(base, c.rng.randint(-5, 5)))
        return c.img

    anims = {
        "idle": (3.0, "catmullrom", {
            "rightarm": [("rotation", 0, (0, 0, 0)), ("rotation", 1.5, (0, 0, 3)), ("rotation", 3.0, (0, 0, 0))],
            "leftarm": [("rotation", 0, (0, 0, 0)), ("rotation", 1.5, (0, 0, -3)), ("rotation", 3.0, (0, 0, 0))],
            "hi_head": [("rotation", 0, (0, 0, 0)), ("rotation", 1.5, (-3, 4, 0)), ("rotation", 3.0, (0, 0, 0))],
        }),
        "walk": (0.8, "linear", {
            "rightleg": [("rotation", 0, (30, 0, 0)), ("rotation", 0.4, (-30, 0, 0)), ("rotation", 0.8, (30, 0, 0))],
            "leftleg": [("rotation", 0, (-30, 0, 0)), ("rotation", 0.4, (30, 0, 0)), ("rotation", 0.8, (-30, 0, 0))],
            "rightarm": [("rotation", 0, (-30, 0, 0)), ("rotation", 0.4, (30, 0, 0)), ("rotation", 0.8, (-30, 0, 0))],
            "leftarm": [("rotation", 0, (30, 0, 0)), ("rotation", 0.4, (-30, 0, 0)), ("rotation", 0.8, (30, 0, 0))],
        }),
    }
    m.build(paint, anims)


if __name__ == "__main__":
    build()
