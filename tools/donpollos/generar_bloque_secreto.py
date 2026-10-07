"""Genera el bloque del Secreto del Don Pollo (bueno y malo) para el pack de items.

Recorta la cara de las fotos de fotosbloque/ (donpollobueno.jpg y donpollomalvado.jpg), las achica a 64x64 y arma
un cubo (block/cube_all) con esa cara en los 6 lados. El plugin lo muestra con un item display encima del beacon.

Uso: python tools/generar_bloque_secreto.py
"""
import json
from pathlib import Path

from PIL import Image

ROOT = Path(__file__).resolve().parents[2]  # tools/donpollos -> raiz de BigC-Paper
FUENTES = Path(__file__).resolve().parent / "fuentes"
PACK = ROOT / "resourcepack" / "java" / "assets" / "donpollos"
SIZE = 64
# (foto, recorte cuadrado de la cara, nombre)
FACES = [
    ("donpollobueno.jpg", (50, 20, 270, 240), "bloque_don_pollo_bueno"),
    ("donpollomalvado.jpg", (40, 10, 270, 240), "bloque_don_pollo_malo"),
]


def main():
    (PACK / "textures" / "block").mkdir(parents=True, exist_ok=True)
    (PACK / "models" / "item").mkdir(parents=True, exist_ok=True)
    (PACK / "items").mkdir(parents=True, exist_ok=True)
    for photo, box, name in FACES:
        img = Image.open(FUENTES / "fotosbloque" / photo).convert("RGB").crop(box)
        img = img.resize((SIZE, SIZE), Image.LANCZOS).convert("RGBA")
        img.save(PACK / "textures" / "block" / f"{name}.png")
        model = {"parent": "minecraft:block/cube_all", "textures": {"all": f"donpollos:block/{name}"}}
        (PACK / "models" / "item" / f"{name}.json").write_text(json.dumps(model, indent=2), encoding="utf-8")
        (PACK / "items" / f"{name}.json").write_text(json.dumps(
            {"model": {"type": "minecraft:model", "model": f"donpollos:item/{name}"}}, indent=2), encoding="utf-8")
        print("ok", name)


if __name__ == "__main__":
    main()
