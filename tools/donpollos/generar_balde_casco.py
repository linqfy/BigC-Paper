"""Genera el Balde de KFC dado vuelta (casco) para el pack de items.

En el inventario, la mano y el piso se ve el icono del balde de la polleria del Don Pollo Aura 67
(textura balde_pollo_frito). Puesto en la cabeza se ve un balde 3D rayado rojo y blanco, boca abajo,
agrandado (HEAD_SCALE) para que la cabeza entera, con la capa de afuera de la skin, quede adentro y
no se asome nada.

Uso: python tools/generar_balde_casco.py
"""
import json
import random
from pathlib import Path

from PIL import Image

ROOT = Path(__file__).resolve().parents[2]  # tools/donpollos -> raiz de BigC-Paper
FUENTES = Path(__file__).resolve().parent / "fuentes"
PACK = ROOT / "resourcepack" / "java" / "assets" / "donpollos"
NAME = "balde_kfc_casco"
ICON = "balde_kfc_icono"
# La cabeza mide 8 px y con la capa de afuera de la skin 9 px; el item en la cabeza se dibuja a 0.625 px por
# unidad, asi que el balde (14 unidades de ancho) agrandado 1.3 queda en ~11.4 px: la cabeza no lo atraviesa.
HEAD_SCALE = 1.3

RED = (198, 18, 30)
RED_DARK = (160, 10, 22)
WHITE = (242, 240, 234)
WHITE_SHADE = (214, 210, 202)
INSIDE = (70, 26, 22)


def noisy(color, rng, amount=6):
    return tuple(max(0, min(255, c + rng.randint(-amount, amount))) for c in color) + (255,)


def texture():
    """32x32: rayas (0,0)-(16,12), tapa (16,0)-(32,16), borde (0,16)-(16,20), adentro (16,16)-(32,32)."""
    rng = random.Random(7)
    img = Image.new("RGBA", (32, 32), (0, 0, 0, 0))
    for y in range(12):
        for x in range(16):
            stripe = (x // 2) % 2 == 0
            base = RED if stripe else WHITE
            if y == 11:
                base = RED_DARK if stripe else WHITE_SHADE
            img.putpixel((x, y), noisy(base, rng))
    for y in range(16):
        for x in range(16, 32):
            edge = x in (16, 31) or y in (0, 15)
            img.putpixel((x, y), noisy(WHITE_SHADE if edge else WHITE, rng, 4))
    # en la tapa, un circulo rojo
    for y in range(16):
        for x in range(16):
            if (x - 7.5) ** 2 + (y - 7.5) ** 2 <= 16:
                img.putpixel((16 + x, y), noisy(RED, rng))
    for y in range(16, 20):
        for x in range(16):
            img.putpixel((x, y), noisy(WHITE_SHADE if y == 19 else WHITE, rng, 4))
    for y in range(16, 32):
        for x in range(16, 32):
            img.putpixel((x, y), noisy(INSIDE, rng, 5))
    return img


def faces(side_uv, top_uv, bottom_uv):
    t = f"#{NAME}"
    return {
        "north": {"uv": side_uv, "texture": t}, "south": {"uv": side_uv, "texture": t},
        "east": {"uv": side_uv, "texture": t}, "west": {"uv": side_uv, "texture": t},
        "up": {"uv": top_uv, "texture": t}, "down": {"uv": bottom_uv, "texture": t},
    }


def model():
    stripes = [0, 0, 8, 6]
    top = [8, 0, 16, 8]
    rim = [0, 8, 8, 10]
    inside = [8, 8, 16, 16]
    return {
        "parent": "minecraft:block/block",
        "textures": {NAME: f"donpollos:item/{NAME}", "particle": f"donpollos:item/{NAME}"},
        "display": {"head": {"rotation": [0, 0, 0], "translation": [0, 0, 0],
                             "scale": [HEAD_SCALE, HEAD_SCALE, HEAD_SCALE]}},
        "elements": [
            # boca del balde (ahora abajo): borde blanco
            {"from": [0.5, 0, 0.5], "to": [15.5, 1.5, 15.5], "faces": faces(rim, rim, inside)},
            # cuerpo rayado que tapa la cabeza
            {"from": [1, 1.5, 1], "to": [15, 12, 15], "faces": faces(stripes, top, inside)},
            # se angosta hacia la base (que quedo arriba)
            {"from": [2, 12, 2], "to": [14, 15.5, 14], "faces": faces([0, 0, 8, 2], top, inside)},
        ],
    }


def main():
    (PACK / "textures" / "item").mkdir(parents=True, exist_ok=True)
    (PACK / "models" / "item").mkdir(parents=True, exist_ok=True)
    (PACK / "items").mkdir(parents=True, exist_ok=True)
    texture().save(PACK / "textures" / "item" / f"{NAME}.png")
    (PACK / "models" / "item" / f"{NAME}.json").write_text(json.dumps(model(), indent=2), encoding="utf-8")
    # icono plano (el mismo balde que se ve arriba en el menu de la polleria)
    (PACK / "models" / "item" / f"{ICON}.json").write_text(json.dumps(
        {"parent": "minecraft:item/generated", "textures": {"layer0": "donpollos:item/balde_pollo_frito"}},
        indent=2), encoding="utf-8")
    # en la cabeza el balde 3D; en cualquier otro lado el icono
    (PACK / "items" / f"{NAME}.json").write_text(json.dumps({"model": {
        "type": "minecraft:select",
        "property": "minecraft:display_context",
        "cases": [{"when": "head", "model": {"type": "minecraft:model", "model": f"donpollos:item/{NAME}"}}],
        "fallback": {"type": "minecraft:model", "model": f"donpollos:item/{ICON}"},
    }}, indent=2), encoding="utf-8")
    print("ok", NAME)


if __name__ == "__main__":
    main()
