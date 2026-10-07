"""Genera el celular para los jugadores de Bedrock (Geyser): modelo 3D en la mano con "Pobre" en la pantalla.

Uso:  python tools/celular/generar_bedrock.py   (despues de generar_celular.py y convertir_videos.py)

Escribe en el pack de Bedrock de BigCasares (resourcepack/bedrock/):
  textures/entity/celular_bedrock.png        la textura del celu (la de Java con "Pobre" en la pantalla)
  models/entity/celular.geo.json             el modelo 3D (las mismas piezas que el de Java)
  animations/celular.animation.json          como se ve en la mano, en la cabeza y en tercera persona
  attachables/celular_<item>.json            uno por cada item de Bedrock (celular y video_1..N)
y el icono del inventario en resourcepack/shared/textures/item/celular_bedrock.png (registry.yml: "celular",
  con java-model celular:item/celular y texture item/celular_bedrock).

El modulo geyser-integration registra en Geyser un item de Bedrock por cada modelo de Java del celu
(celular:celular y celular:video_N); todos se ven igual. En Bedrock no hay videos: la pantalla dice "Pobre".

La conversion del modelo copia la de java2bedrock (la que usan los packs de items 3D para Geyser).
Sin probar con un cliente de Bedrock: si se ve espejado o mal ubicado en la mano, se corrige aca.
"""

import json
import sys
from pathlib import Path

from PIL import Image

HERE = Path(__file__).resolve().parent
sys.path.insert(0, str(HERE))
import generar_celular  # noqa: E402

ROOT = HERE.parent.parent
JAVA = ROOT / "resourcepack" / "java" / "assets" / "celular"
BEDROCK = ROOT / "resourcepack" / "bedrock"
SHARED = ROOT / "resourcepack" / "shared"
LISTA = ROOT / "src" / "main" / "resources" / "celular" / "videos.yml"

SIZE = generar_celular.SIZE
GEOMETRY = "geometry.celular"
TEXTURE = "textures/entity/celular_bedrock"

# Letras de 3x5 para escribir "Pobre" en la pantalla.
LETRAS = {
    "P": ["111", "101", "111", "100", "100"],
    "o": ["000", "111", "101", "101", "111"],
    "b": ["100", "100", "111", "101", "111"],
    "r": ["000", "111", "100", "100", "100"],
    "e": ["000", "111", "111", "100", "111"],
}
TEXTO = "Pobre"
ROJO = (255, 70, 70, 255)


def textura():
    """La textura de Java con la pantalla apagada y "Pobre" en el medio."""
    tex = Image.open(JAVA / "textures" / "item" / "celular.png").convert("RGBA")
    for y in range(2, 58):
        for x in range(2, 26):
            tex.putpixel((x, y), (8, 8, 12, 255))
    ancho = len(TEXTO) * 4 - 1
    x0, y0 = 2 + (24 - ancho) // 2, 2 + (56 - 5) // 2
    for i, letra in enumerate(TEXTO):
        for fila, bits in enumerate(LETRAS[letra]):
            for col, bit in enumerate(bits):
                if bit == "1":
                    tex.putpixel((x0 + i * 4 + col, y0 + fila), ROJO)
    return tex


def icono(tex):
    """Icono de 32x32 para el inventario: el frente del celu."""
    frente = tex.crop((0, 0, 28, 60)).resize((14, 30), Image.NEAREST)
    img = Image.new("RGBA", (32, 32), (0, 0, 0, 0))
    img.paste(frente, (9, 1))
    return img


def uv(face, flip=False):
    """Cara de Java (uv en 0-16) a cara de Bedrock (uv y uv_size en pixeles), como java2bedrock."""
    u0, v0, u1, v1 = [c * SIZE / 16 for c in face["uv"]]
    if flip:
        return {"uv": [u1, v1], "uv_size": [u0 - u1, v0 - v1]}
    return {"uv": [u0, v0], "uv_size": [u1 - u0, v1 - v0]}


def cubo(elemento):
    desde, hasta = elemento["from"], elemento["to"]
    caras = {}
    for nombre, cara in elemento["faces"].items():
        if nombre == "up":
            caras["up"] = uv(cara, flip=True)
        elif nombre == "down":
            u0, v0, u1, v1 = [c * SIZE / 16 for c in cara["uv"]]
            caras["down"] = {"uv": [u1, v0], "uv_size": [u0 - u1, v1 - v0]}
        else:
            caras[nombre] = uv(cara)
    return {
        "origin": [round(-hasta[0] + 8, 4), desde[1], round(desde[2] - 8, 4)],
        "size": [round(hasta[i] - desde[i], 4) for i in range(3)],
        "uv": caras,
    }


def geometria(modelo):
    pivote = [0, 8, 0]
    return {
        "format_version": "1.16.0",
        "minecraft:geometry": [{
            "description": {
                "identifier": GEOMETRY,
                "texture_width": SIZE,
                "texture_height": SIZE,
                "visible_bounds_width": 2,
                "visible_bounds_height": 2,
                "visible_bounds_offset": [0, 0.5, 0],
            },
            "bones": [
                {"name": "geyser_custom", "pivot": pivote},
                {"name": "geyser_custom_x", "parent": "geyser_custom", "pivot": pivote},
                {"name": "geyser_custom_y", "parent": "geyser_custom_x", "pivot": pivote},
                {"name": "geyser_custom_z", "parent": "geyser_custom_y", "pivot": pivote,
                 "cubes": [cubo(e) for e in modelo["elements"]]},
            ],
        }],
    }


def huesos(display, base, espejo=False):
    """Pasa una transformacion de display de Java a los huesos de java2bedrock."""
    rx, ry, rz = display.get("rotation", [0, 0, 0])
    tx, ty, tz = display.get("translation", [0, 0, 0])
    escala = display.get("scale", [1, 1, 1])
    signo = -1 if espejo else 1
    return {
        "geyser_custom": base,
        "geyser_custom_x": {"rotation": [-rx, 0, 0], "position": [-tx * signo, ty, tz], "scale": escala},
        "geyser_custom_y": {"rotation": [0, -ry * signo, 0]},
        "geyser_custom_z": {"rotation": [0, 0, rz * signo]},
    }


def animaciones(modelo):
    d = modelo["display"]
    tercera = {"rotation": [90, 0, 0], "position": [0, 13, -3]}
    primera = {"rotation": [90, 60, -40], "position": [4, 10, 4], "scale": 1.5}
    cabeza = {"position": [0, 19.9, 0], "scale": 0.625}
    lista = {
        "thirdperson_main_hand": huesos(d["thirdperson_righthand"], tercera),
        "thirdperson_off_hand": huesos(d["thirdperson_lefthand"], tercera, espejo=True),
        "firstperson_main_hand": huesos(d["firstperson_righthand"], primera),
        "firstperson_off_hand": huesos(d["firstperson_lefthand"], primera, espejo=True),
        "head": huesos(d["head"], cabeza),
    }
    return {
        "format_version": "1.8.0",
        "animations": {f"animation.celular.{k}": {"loop": True, "bones": v} for k, v in lista.items()},
    }


def attachable(identificador):
    return {
        "format_version": "1.10.0",
        "minecraft:attachable": {
            "description": {
                "identifier": identificador,
                "materials": {"default": "entity_alphatest", "enchanted": "entity_alphatest_glint"},
                "textures": {"default": TEXTURE, "enchanted": "textures/misc/enchanted_item_glint"},
                "geometry": {"default": GEOMETRY},
                "animations": {
                    k: f"animation.celular.{k}" for k in
                    ("thirdperson_main_hand", "thirdperson_off_hand", "firstperson_main_hand",
                     "firstperson_off_hand", "head")
                },
                "scripts": {
                    "pre_animation": [
                        "v.main_hand = c.item_slot == 'main_hand';",
                        "v.off_hand = c.item_slot == 'off_hand';",
                        "v.head = c.item_slot == 'head';",
                    ],
                    "animate": [
                        {"thirdperson_main_hand": "v.main_hand && !c.is_first_person"},
                        {"thirdperson_off_hand": "v.off_hand && !c.is_first_person"},
                        {"firstperson_main_hand": "v.main_hand && c.is_first_person"},
                        {"firstperson_off_hand": "v.off_hand && c.is_first_person"},
                        {"head": "v.head"},
                    ],
                },
                "render_controllers": ["controller.render.item_default"],
            }
        },
    }


def items_de_bedrock():
    """celular:celular y celular:video_N (los mismos identificadores que los modelos de Java)."""
    ids = ["celular:celular"]
    for linea in LISTA.read_text(encoding="utf-8").splitlines():
        linea = linea.strip()
        if linea.startswith("- id:"):
            ids.append("celular:" + linea.split(":", 1)[1].strip())
    return ids


def escribir(path, contenido):
    path.parent.mkdir(parents=True, exist_ok=True)
    path.write_text(json.dumps(contenido, indent=2), encoding="utf-8", newline="\n")


def main():
    modelo = generar_celular.modelo()
    tex = textura()
    (BEDROCK / "textures" / "entity").mkdir(parents=True, exist_ok=True)
    tex.save(BEDROCK / "textures" / "entity" / "celular_bedrock.png")
    (SHARED / "textures" / "item").mkdir(parents=True, exist_ok=True)
    icono(tex).save(SHARED / "textures" / "item" / "celular_bedrock.png")
    escribir(BEDROCK / "models" / "entity" / "celular.geo.json", geometria(modelo))
    escribir(BEDROCK / "animations" / "celular.animation.json", animaciones(modelo))
    for viejo in (BEDROCK / "attachables").glob("celular_*.json"):
        viejo.unlink()
    ids = items_de_bedrock()
    for identificador in ids:
        escribir(BEDROCK / "attachables" / f"celular_{identificador.split(':', 1)[1]}.json",
                 attachable(identificador))
    vista = generar_celular.render(modelo, tex, -25, 12, lado=360, zoom=20)
    hoja = Image.new("RGBA", (360, 380), (54, 57, 63, 255))
    hoja.paste(vista, (0, 10), vista)
    hoja.save(HERE / "vista_previa_bedrock.png")
    print(f"Listo: modelo de Bedrock, textura con \"{TEXTO}\" y {len(ids)} attachables.")


if __name__ == "__main__":
    main()
