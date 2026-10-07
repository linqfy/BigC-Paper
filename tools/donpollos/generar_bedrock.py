"""Genera lo que necesita Geyser para que los jugadores de Bedrock vean y escuchen lo de Don Pollos.

Salidas (van dentro del plugin, que las instala solo en la carpeta de Geyser):
  src/main/resources/donpollos/bedrock/DonPollos-bedrock.mcpack   pack de recursos de Bedrock
      textures/item_texture.json + textures/items/*.png  iconos de los items propios
      sounds/sound_definitions.json + sounds/donpollos/  los audios de Don Pollo
  src/main/resources/donpollos/bedrock/donpollos-geyser.json       mapeo de items (Geyser custom items v2, por item_model)

Se arma a partir del pack de Java (resourcepack/java/assets/donpollos), asi que primero hay que generar ese.

Uso: python tools/generar_bedrock.py
"""
import hashlib
import io
import json
import uuid
import zipfile
from pathlib import Path

from PIL import Image

ROOT = Path(__file__).resolve().parents[2]  # tools/donpollos -> raiz de BigC-Paper
FUENTES = Path(__file__).resolve().parent / "fuentes"
JAVA = ROOT / "resourcepack" / "java" / "assets" / "donpollos"
OUT = ROOT / "src" / "main" / "resources" / "donpollos" / "bedrock"
PACK_NAME = "DonPollos-bedrock.mcpack"
MAPPINGS_NAME = "donpollos-geyser.json"

# item de Java -> [(item_model, nombre, textura del icono, componentes que el servidor siempre le pone, opciones)]
ITEMS = {
    "minecraft:cooked_chicken": [
        ("pollo_frito", "Pollo Frito", "item/pollo_frito",
         {"minecraft:food": {"nutrition": 8, "saturation": 9.6}}, {}),
    ],
    "minecraft:cookie": [
        ("salsa", "Salsa", "item/salsa",
         {"minecraft:consumable": {"consume_seconds": 1.0, "animation": "drink"},
          "minecraft:food": {"nutrition": 1, "saturation": 0.4, "can_always_eat": True}}, {}),
        ("picante", "Picante", "item/picante",
         {"minecraft:consumable": {"consume_seconds": 1.0, "animation": "drink"},
          "minecraft:food": {"nutrition": 1, "saturation": 0.4, "can_always_eat": True}}, {}),
    ],
    "minecraft:paper": [
        ("balde_pollo_frito", "Balde de Pollo Frito", "item/balde_pollo_frito", {}, {}),
        # el balde de KFC se ve con el icono del balde y se puede poner en la cabeza
        ("balde_kfc_casco", "Balde de KFC dado vuelta", "item/balde_pollo_frito",
         {"minecraft:equippable": {"slot": "head"}, "minecraft:max_stack_size": 1},
         {"allow_offhand": False}),
    ],
    # solo aparecen en item displays (meteoritos y la cara del bloque del secreto): con GeyserDisplayEntity se ven
    "minecraft:paper#displays": [
        ("meteorito_wsp", "Meteorito de WhatsApp", "@logo", {}, {}),
        ("bloque_don_pollo_bueno", "Don Pollo", "block/bloque_don_pollo_bueno", {}, {"id": "cara_don_pollo_bueno"}),
        ("bloque_don_pollo_malo", "Don Pollo Malo", "block/bloque_don_pollo_malo", {}, {"id": "cara_don_pollo_malo"}),
    ],
    "minecraft:beacon": [
        ("bloque_don_pollo_bueno", "Secreto del Don Pollo", "block/bloque_don_pollo_bueno", {}, {}),
    ],
}


def logo_icon():
    """Icono del logo de WhatsApp (el modelo del meteorito es 3D, no tiene una textura plana)."""
    import sys
    sys.path.insert(0, str(Path(__file__).resolve().parent))
    from generar_wsp import logo_mask
    size = 28
    grid = logo_mask(size)
    img = Image.new("RGBA", (32, 32), (0, 0, 0, 0))
    colors = {1: (44, 178, 76, 255), 2: (236, 236, 232, 255), 3: (236, 236, 232, 255)}
    for y in range(size):
        for x in range(size):
            if grid[y][x]:
                img.putpixel((x + 2, y + 2), colors[grid[y][x]])
    return img


def bedrock_id(model, options):
    return options.get("id", model)


def stable_uuid(name):
    return str(uuid.uuid5(uuid.NAMESPACE_URL, "donpollos/bedrock/" + name))


def icon_key(model):
    return "donpollos_" + model


def build_pack():
    files = {}
    texture_data = {}
    for definitions in ITEMS.values():
        for model, _, texture, _, options in definitions:
            key = icon_key(bedrock_id(model, options))
            if texture == "@logo":
                img = logo_icon()
            else:
                img = Image.open(JAVA / "textures" / f"{texture}.png").convert("RGBA")
            if img.width > 32:
                img = img.resize((32, 32), Image.LANCZOS)
            buf = io.BytesIO()
            img.save(buf, "PNG")
            files[f"textures/items/{key}.png"] = buf.getvalue()
            texture_data[key] = {"textures": f"textures/items/{key}"}
    files["textures/item_texture.json"] = json.dumps(
        {"resource_pack_name": "donpollos", "texture_name": "atlas.items", "texture_data": texture_data},
        indent=2).encode()

    # audios: con el nombre de Java ("donpollos:don_pollo_1") y sin el namespace, por las dudas
    catalog = json.loads((ROOT / "src" / "main" / "resources" / "donpollos" / "sonidos.json").read_text(encoding="utf-8"))
    definitions = {}
    for clip in catalog:
        key = clip["sound"]
        name = key.split(":", 1)[1]
        files[f"sounds/donpollos/{name}.ogg"] = (JAVA / "sounds" / f"{name}.ogg").read_bytes()
        entry = {"category": "neutral",
                 "sounds": [{"name": f"sounds/donpollos/{name}", "stream": True, "load_on_low_memory": True}]}
        definitions[key] = entry
        definitions[name] = entry
    files["sounds/sound_definitions.json"] = json.dumps(
        {"format_version": "1.14.0", "sound_definitions": definitions}, indent=2).encode()

    icon = Image.open(JAVA / "textures" / "item" / "balde_pollo_frito.png").convert("RGBA").resize((64, 64), Image.NEAREST)
    buf = io.BytesIO()
    icon.save(buf, "PNG")
    files["pack_icon.png"] = buf.getvalue()

    # la version sale del contenido: si cambia algo, Bedrock baja el pack de nuevo
    digest = hashlib.sha256(b"".join(files[k] for k in sorted(files))).digest()
    version = [1, 0, int.from_bytes(digest[:2], "big") % 10000]
    files["manifest.json"] = json.dumps({
        "format_version": 2,
        "header": {"name": "Don Pollos", "description": "Items y sonidos de Don Pollo para Bedrock (Geyser)",
                   "uuid": stable_uuid("header"), "version": version, "min_engine_version": [1, 21, 0]},
        "modules": [{"type": "resources", "uuid": stable_uuid("resources"), "version": version}],
    }, indent=2).encode()

    out = io.BytesIO()
    with zipfile.ZipFile(out, "w", zipfile.ZIP_DEFLATED) as z:
        for path in sorted(files):
            info = zipfile.ZipInfo(path, date_time=(2026, 1, 1, 0, 0, 0))
            info.compress_type = zipfile.ZIP_DEFLATED
            z.writestr(info, files[path])
    return out.getvalue(), version


def build_mappings():
    items = {}
    for group, definitions in ITEMS.items():
        java_item = group.split("#", 1)[0]
        for model, name, _, components, options in definitions:
            bedrock = bedrock_id(model, options)
            extra = {k: v for k, v in options.items() if k != "id"}
            definition = {
                "type": "definition",
                "model": f"donpollos:{model}",
                "bedrock_identifier": f"donpollos:{bedrock}",
                "display_name": name,
                "bedrock_options": {"icon": icon_key(bedrock), **extra},
            }
            if components:
                definition["components"] = components
            items.setdefault(java_item, []).append(definition)
    return json.dumps({"format_version": 2, "items": items}, indent=2, ensure_ascii=False)


def main():
    OUT.mkdir(parents=True, exist_ok=True)
    pack, version = build_pack()
    (OUT / PACK_NAME).write_bytes(pack)
    (OUT / MAPPINGS_NAME).write_text(build_mappings(), encoding="utf-8")
    print("ok", PACK_NAME, "version", ".".join(map(str, version)), f"{len(pack) // 1024} KB")
    print("ok", MAPPINGS_NAME)


if __name__ == "__main__":
    main()
