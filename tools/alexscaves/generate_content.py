"""Genera el contenido de Alex's Caves para los packs y el catálogo de BigCasares.

Uso manual (no corre en el build):
    python -I tools/alexscaves/generate_content.py <alexscaves-2.0.2.jar> <raíz del repo> [<código decompilado>]

El código decompilado (fuera del repo) solo hace falta para ítems con modelo 3D en
código (``citadel`` en content_tables.py).

El JAR del mod solo se lee como zip. Lo que se genera (todo con namespace
``alexscaves``):
- ``src/main/resources/alexscaves/blocks.yml``: asignación ESTABLE de estados vanilla
  (note block, tripwire o chorus plant, ver ``block_bases.py``) a bloques del módulo.
  Las asignaciones existentes nunca se cambian (están guardadas en los mundos); los
  bloques nuevos toman índices libres y los que cambian de base guardan su estado
  viejo de note block como ``legacy``.
- ``src/main/resources/alexscaves/pack-layer/``: capa de pack con ``note_block.json``
  (``instrument=harp`` queda vanilla), ``tripwire.json`` y ``chorus_plant.json`` (los
  estados que el módulo no usa se ven vanilla). Solo se agrega al pack cuando el
  módulo está activo.
- Modelos, texturas, definiciones de ítem y traducciones Java en
  ``resourcepack/java/assets/alexscaves/``.
- Texturas Bedrock, ``terrain_texture.json`` y el fragmento de íconos.
- Mapeos de Geyser (ítems v2 y bloques v1) en ``src/main/resources/alexscaves/geyser/``.
- YAML del catálogo ``src/main/resources/content/items/ac_*.yml`` y la lista
  ``src/main/resources/alexscaves/items.txt``.
Las tablas de contenido por bioma están en ``content_tables.py``.
"""
import json
import os
import re
import sys
import zipfile

sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))
from content_tables import (BLOCKS, CAVE_PAINTINGS, ENCHANTMENT_EXCLUSIVE, ENCHANTMENTS, EQUIPMENT, ITEMS, SOUNDS,  # noqa: E402
                            SUBSTITUTES, WATERLOGGABLE, CLIENT_REMAP_LAST)
import block_bases  # noqa: E402
import bedrock_blocks  # noqa: E402
import display_models  # noqa: E402
import citadel_to_item_model  # noqa: E402
import shutil  # noqa: E402
import subprocess  # noqa: E402
import tempfile  # noqa: E402

# Every instrument of 26.2: the note block blockstate must cover all of them (the extra ones stay vanilla).
ALL_INSTRUMENTS = block_bases.INSTRUMENTS + [
    "trumpet", "trumpet_exposed", "trumpet_oxidized", "trumpet_weathered",
    "zombie", "skeleton", "creeper", "dragon", "wither_skeleton", "piglin", "custom_head",
]
NS = "alexscaves"
BASE_BLOCKS = [block_bases.NOTE_BLOCK, block_bases.TRIPWIRE, block_bases.CHORUS_PLANT]


class Mod:
    def __init__(self, jar_path):
        self.jar = zipfile.ZipFile(jar_path)
        self.names = set(self.jar.namelist())
        self.lang = {
            "es_es": json.loads(self.jar.read("assets/alexscaves/lang/es_es.json")),
            "en_us": json.loads(self.jar.read("assets/alexscaves/lang/en_us.json")),
        }

    def json(self, path):
        return json.loads(self.jar.read(path))

    def has(self, path):
        return path in self.names

    def read(self, path):
        return self.jar.read(path)

    def name(self, kind, mod_id, lang="es_es"):
        # Items that are blocks in the mod (signs placed from an item) are named by their block key.
        keys = ["%s.alexscaves.%s" % (k, mod_id) for k in (kind, "block" if kind == "item" else "item")]
        if mod_id.startswith("cave_painting_"):
            # CavePaintingBlock: every painting is named "cave_painting"; its own name is the tooltip (.desc).
            keys.append("block.alexscaves.cave_painting")
        for key in keys:
            name = self.lang[lang].get(key) or self.lang["en_us"].get(key)
            if name:
                return name
        return mod_id


def write(path, data, binary=False):
    os.makedirs(os.path.dirname(path), exist_ok=True)
    if binary:
        with open(path, "wb") as handle:
            handle.write(data)
        return
    with open(path, "w", encoding="utf-8", newline="\n") as handle:
        if isinstance(data, str):
            handle.write(data)
        else:
            json.dump(data, handle, indent=2, ensure_ascii=False)
            handle.write("\n")


def save_allocation(path, allocation):
    lines = [
        "# Estados vanilla que dibujan los bloques de Alex's Caves. NO cambiar ni reordenar:",
        "# los mundos guardan estos estados. tools/alexscaves/generate_content.py solo agrega.",
        "# variante: índice            -> note block: instrument = índice / 50, note = (índice % 50) / 2,",
        "#                                powered = índice impar. 0-49 (instrument=harp) quedan vanilla.",
        "# variante: base índice       -> tripwire o chorus_plant (codificación en tools/alexscaves/block_bases.py).",
        "#           ... legacy índice -> estado de note block que tenía antes: reservado, se migra al cargar el chunk.",
    ]
    for block in sorted(allocation):
        lines.append(block + ":")
        for variant, entry in sorted(allocation[block].items(), key=lambda item: (item[1][0] != "note_block",
                                                                                   item[1][0], item[1][1])):
            lines.append("  %s: %s" % (variant, block_bases.format_entry(entry)))
    write(path, "\n".join(lines) + "\n")


# Models made by merging the parts of a multipart block state (path -> model).
MERGED_MODELS = {}


PETRIFIED_SLAB = "petrified_oak_slab"


def write_petrified_slab(mod, layer, models, textures):
    """Draws the unobtainable vanilla petrified oak slab as the mod's smooth limestone slab (module pack layer).

    The vanilla blockstate sends the double slab to oak planks, so the blockstate is replaced (not its models). The
    item definition shows the mod slab too. Returns the layer paths written.
    """
    states = mod.json("assets/alexscaves/blockstates/smooth_limestone_slab.json")["variants"]
    for state in states.values():
        collect_model(mod, state["model"], models, textures)
    write(os.path.join(layer, "assets/minecraft/blockstates/%s.json" % PETRIFIED_SLAB), {"variants": states})
    write(os.path.join(layer, "assets/minecraft/items/%s.json" % PETRIFIED_SLAB),
          {"model": {"type": "minecraft:model", "model": NS + ":item/smooth_limestone_slab"}})
    return ["assets/minecraft/blockstates/%s.json" % PETRIFIED_SLAB, "assets/minecraft/items/%s.json" % PETRIFIED_SLAB]


def collect_model(mod, model_ref, out_models, textures):
    """Copies a block model and its parents/textures from the mod (alexscaves namespace only)."""
    if not model_ref.startswith(NS + ":"):
        return
    path = model_ref.split(":", 1)[1]
    if path in out_models:
        return
    source = "assets/alexscaves/models/%s.json" % path
    if path in MERGED_MODELS:
        model = MERGED_MODELS[path]
    elif not mod.has(source):
        raise SystemExit("falta el modelo " + source)
    else:
        model = mod.json(source)
    out_models[path] = model
    parent = model.get("parent")
    if parent and parent.startswith(NS + ":"):
        collect_model(mod, parent, out_models, textures)
    for texture in model.get("textures", {}).values():
        if isinstance(texture, str) and texture.startswith(NS + ":"):
            textures.add(texture.split(":", 1)[1])


def pick_texture(models, model_ref):
    """Most representative texture of a block model (for Bedrock and icons)."""
    path = model_ref.split(":", 1)[1]
    seen = set()
    while path and path not in seen:
        seen.add(path)
        model = models.get(path, {})
        textures = model.get("textures", {})
        for key in ("all", "side", "texture", "particle", "end", "top"):
            value = textures.get(key)
            if isinstance(value, str) and value.startswith(NS + ":"):
                return value.split(":", 1)[1]
        for value in textures.values():
            if isinstance(value, str) and value.startswith(NS + ":"):
                return value.split(":", 1)[1]
        parent = model.get("parent", "")
        path = parent.split(":", 1)[1] if parent.startswith(NS + ":") else None
    raise SystemExit("sin textura para " + model_ref)


def part_matches(when, props):
    if "OR" in when:
        return any(part_matches(option, props) for option in when["OR"])
    if "AND" in when:
        return all(part_matches(option, props) for option in when["AND"])
    return all(props.get(k, "false") in str(v).lower().split("|") for k, v in when.items())


def merge_models(mod, block_id, variant, applies):
    """One model with the elements of every matching multipart part (a note block state shows one model)."""
    merged = {"textures": {}, "elements": []}
    for apply in applies:
        if any(key in apply for key in ("x", "y")):
            raise SystemExit("%s: partes rotadas sin soporte para '%s'" % (block_id, variant))
        model = mod.json("assets/alexscaves/models/%s.json" % apply["model"].split(":", 1)[1])
        if "elements" not in model:
            raise SystemExit("%s: parte sin elementos (%s)" % (block_id, apply["model"]))
        for key, value in model.get("textures", {}).items():
            if merged["textures"].setdefault(key, value) != value:
                raise SystemExit("%s: texturas en conflicto en '%s'" % (block_id, variant))
        merged.setdefault("parent", model.get("parent", "block/block"))
        for key in ("ambient_occlusion", "display", "render_type"):
            if key in model:
                merged.setdefault(key, model[key])
        merged["elements"].extend(model["elements"])
    path = "block/%s_%s" % (block_id, re.sub(r"[^a-z0-9]+", "_", variant))
    MERGED_MODELS[path] = merged
    return {"model": "%s:%s" % (NS, path)}


# Vanilla parents whose model is one full cube.
FULL_CUBE_PARENTS = {"minecraft:block/" + name for name in (
    "cube", "cube_all", "cube_column", "cube_column_horizontal", "cube_bottom_top", "cube_top", "cube_directional",
    "cube_mirrored_all", "orientable", "orientable_vertical", "orientable_with_bottom", "leaves")}


def model_shape(mod, model_ref):
    """"full" (one opaque full cube, the look of the note block that carries it), "translucent" (translucent render
    type) or "partial", from the mod model and its parents."""
    render_type, elements, ref = None, None, model_ref
    while ref and ref.startswith(NS + ":"):
        path = ref.split(":", 1)[1]
        model = MERGED_MODELS.get(path) or mod.json("assets/alexscaves/models/%s.json" % path)
        render_type = render_type or model.get("render_type")
        if elements is None and "elements" in model:
            elements = model["elements"]
        ref = model.get("parent")
        if ref and ":" not in ref:
            ref = "minecraft:" + ref
    if render_type == "translucent":
        return "translucent"
    if elements is None:
        return "full" if ref in FULL_CUBE_PARENTS else "partial"
    return "full" if any(e["from"] == [0, 0, 0] and e["to"] == [16, 16, 16] for e in elements) else "partial"


def variant_model(mod, block_id, variant):
    states = mod.json("assets/alexscaves/blockstates/%s.json" % block_id)
    if "multipart" in states:
        # Multipart (connecting) blocks: every part whose condition matches the variant's properties, merged
        # into one model when there are several.
        props = dict(part.split("=", 1) for part in variant.split(",") if part)
        applies = []
        for part in states["multipart"]:
            if part_matches(part.get("when", {}), props):
                apply = part["apply"]
                applies.append(apply[0] if isinstance(apply, list) else apply)
        if not applies:
            raise SystemExit("%s no tiene una parte para '%s'" % (block_id, variant))
        return applies[0] if len(applies) == 1 else merge_models(mod, block_id, variant, applies)
    variants = states.get("variants", {})
    entry = variants.get(variant)
    if entry is None:
        raise SystemExit("%s no tiene la variante '%s'" % (block_id, variant))
    if isinstance(entry, list):
        entry = entry[0]
    return entry


def main(jar_path, repo, decompiled=None):
    mod = Mod(jar_path)
    res = os.path.join(repo, "src/main/resources")
    pack = os.path.join(repo, "resourcepack")
    java = os.path.join(pack, "java/assets")
    bedrock = os.path.join(pack, "bedrock")

    allocation_path = os.path.join(res, "alexscaves/blocks.yml")
    allocation = block_bases.allocate(block_bases.load_allocation(allocation_path), BLOCKS)
    save_allocation(allocation_path, allocation)

    models, textures = {}, set()
    custom = {base: {} for base in BASE_BLOCKS}
    terrain = {}
    block_overrides = {base: {} for base in BASE_BLOCKS}
    shapes = {}
    for block_id, spec in BLOCKS.items():
        for variant, (base, index, legacy) in allocation[block_id].items():
            if variant not in spec["variants"]:
                continue
            entry = variant_model(mod, block_id, spec["variants"][variant])
            collect_model(mod, entry["model"], models, textures)
            shapes[(block_id, variant)] = model_shape(mod, entry["model"])
            rendered = {"model": entry["model"]}
            for key in ("x", "y", "uvlock"):
                if key in entry:
                    rendered[key] = entry[key]
            custom[base][index] = rendered
            bedrock_texture = spec.get("bedrock_texture") or pick_texture(models, entry["model"])
            spec.setdefault("resolved_texture", bedrock_texture)
            textures.add(bedrock_texture)
            terrain["%s_%s" % (NS, block_id)] = {"textures": "textures/%s/%s" % (NS, bedrock_texture)}
            override = {
                "name": "%s_%s_%s" % (NS, block_id, re.sub(r"[^a-z0-9]+", "_", variant) or "default"),
                "display_name": mod.name("block", block_id),
                "material_instances": {"*": {"texture": "%s_%s" % (NS, block_id),
                                             "render_method": "opaque" if base == block_bases.NOTE_BLOCK
                                             else "alpha_test"}},
            }
            block_overrides[base][block_bases.state_key(base, index)] = override
            if legacy is not None:
                # Chunks not migrated yet still hold the old note block state: it keeps the block's look.
                custom[block_bases.NOTE_BLOCK][legacy] = rendered
                block_overrides[block_bases.NOTE_BLOCK][block_bases.state_key(block_bases.NOTE_BLOCK, legacy)] =                     dict(override, name=override["name"] + "_legacy")
    note_variants = {block_bases.state_key(block_bases.NOTE_BLOCK, index): rendered
                     for index, rendered in custom[block_bases.NOTE_BLOCK].items()}
    for instrument in ALL_INSTRUMENTS:
        for note in range(25):
            for powered in ("false", "true"):
                key = "instrument=%s,note=%d,powered=%s" % (instrument, note, powered)
                note_variants.setdefault(key, {"model": "minecraft:block/note_block"})
    # The overrides only ship in the module's own pack layer, added to the stack when the module is enabled: in
    # the main pack they would restyle players' blocks. Tripwire and chorus plant keep every state the module
    # does not use with its vanilla model.
    layer = os.path.join(res, "alexscaves/pack-layer")
    write(os.path.join(layer, "assets/minecraft/blockstates/note_block.json"), {"variants": note_variants})
    for base in (block_bases.TRIPWIRE, block_bases.CHORUS_PLANT):
        write(os.path.join(layer, "assets/minecraft/blockstates/%s.json" % base),
              block_bases.blockstate(base, custom[base]))
    petrified_files = write_petrified_slab(mod, layer, models, textures)
    write(os.path.join(layer, "files.txt"),
          "# Capa de pack Java del módulo (solo con alexs-caves activo).\n"
          + "".join("assets/minecraft/blockstates/%s.json\n" % base for base in BASE_BLOCKS)
          + "".join("%s\n" % path for path in petrified_files))
    # Packet remap: the borrowed vanilla blockstates only ship when the remap is on (with the remap off real blocks
    # in those states would draw module blocks), listed apart in files-packet-remap.txt.
    entries, normalize, uncovered = block_bases.client_remap(BLOCKS, allocation, shapes, WATERLOGGABLE,
                                                                CLIENT_REMAP_LAST)
    borrowed = {}
    for block_id, variant, base, index, dry, wet in entries:
        for state in (dry, wet):
            if state is not None:
                borrowed[state] = custom[base][index]
    families = block_bases.family_blockstates(borrowed)
    for block, definition in families.items():
        write(os.path.join(layer, "assets/minecraft/blockstates/%s.json" % block), definition)
    for stale in os.listdir(os.path.join(layer, "assets/minecraft/blockstates")):
        if stale[:-5] not in families and stale[:-5] not in BASE_BLOCKS and stale[:-5] != PETRIFIED_SLAB:
            os.remove(os.path.join(layer, "assets/minecraft/blockstates", stale))
    write(os.path.join(layer, "files-packet-remap.txt"),
          "# Solo con blocks.packet-remap y packetevents (ver alexscaves/client-remap.txt).\n"
          + "".join("assets/minecraft/blockstates/%s.json\n" % block for block in families))
    write(os.path.join(res, "alexscaves/client-remap.txt"),
          "# Generado por tools/alexscaves/generate_content.py: no editar.\n"
          "# block <bloque> <variante> <base> <índice> <estado seco|-> <estado en agua|->: lo que reciben los\n"
          "#   clientes Java en vez del estado del servidor (en agua: posiciones marcadas en el chunk).\n"
          "# normalize <estado prestado> <estado canónico>: bloques vanilla reales en un estado prestado.\n"
          + "".join("block %s %s %s %d %s %s\n" % (b, v, base, i, d or "-", w or "-")
                    for b, v, base, i, d, w in entries)
          + "".join("normalize %s %s\n" % item for item in normalize.items()))
    for block_id, reason in uncovered:
        print("client-remap: %s queda sin estado de cliente (%s)" % (block_id, reason))

    for path, model in models.items():
        write(os.path.join(java, NS, "models", path + ".json"), model)

    yaml_files, item_mappings, icons, lang_es, lang_en = [], {}, {}, {}, {}

    def add_item(item_id, spec, is_block):
        catalog_id = "ac_" + item_id
        kind = "block" if is_block else "item"
        # Own items (not in the mod) bring their names, and their texture already lives in resourcepack/.
        name_es, name_en = spec["name"] if "name" in spec else (mod.name(kind, item_id), mod.name(kind, item_id, "en_us"))
        material = spec.get("material", "NOTE_BLOCK" if is_block else "PAPER")
        model_path = "item/" + item_id
        if is_block and not spec.get("item_texture"):
            source = "assets/alexscaves/models/item/%s.json" % item_id
            item_model = mod.json(source) if mod.has(source) else {"parent": "alexscaves:block/" + item_id}
            parent = item_model.get("parent", "")
            if parent.startswith(NS + ":"):
                collect_model(mod, parent, models, textures)
                write(os.path.join(java, NS, "models", parent.split(":", 1)[1] + ".json"),
                      models[parent.split(":", 1)[1]])
            # The item model's own textures (a flat item icon such as the mussel's) ship too.
            for texture in item_model.get("textures", {}).values():
                if isinstance(texture, str) and texture.startswith(NS + ":"):
                    textures.add(texture.split(":", 1)[1])
            write(os.path.join(java, NS, "models", model_path + ".json"), item_model)
            icon_texture = spec.get("bedrock_texture") or spec.get("resolved_texture") or pick_texture(models, parent)
        elif "citadel" in spec:
            if not decompiled:
                raise SystemExit("%s necesita el código decompilado (tercer argumento)" % item_id)
            model_class, entity_texture = spec["citadel"]
            source = os.path.join(decompiled, "com/github/alexmodguy/alexscaves/client/model", model_class + ".java")
            texture_path = "item/" + item_id
            texture_bytes = mod.read("assets/alexscaves/textures/%s.png" % entity_texture)
            write(os.path.join(java, NS, "textures", texture_path + ".png"), texture_bytes, binary=True)
            item_model = citadel_to_item_model.convert(source, "%s:%s" % (NS, texture_path))
            write(os.path.join(java, NS, "models", model_path + ".json"), item_model)
            icon_texture = "item/%s_icon" % item_id
            write(os.path.join(bedrock, "textures", NS, icon_texture + ".png"),
                  citadel_to_item_model.front_icon(item_model, texture_bytes), binary=True)
        else:
            texture = spec.get("item_texture", "item/" + item_id)
            if not spec.get("own_texture"):
                textures.add(texture)
            write(os.path.join(java, NS, "models", model_path + ".json"),
                  {"parent": "minecraft:item/generated", "textures": {"layer0": "%s:%s" % (NS, texture)}})
            icon_texture = texture
        definition = {"model": {"type": "minecraft:model", "model": "%s:%s" % (NS, model_path)}}
        if spec.get("bow"):
            definition = bow_definition(item_id, java, textures)
        elif spec.get("totem"):
            definition = totem_definition(item_id, java, textures)
        write(os.path.join(java, NS, "items", item_id + ".json"), definition)
        if "citadel" not in spec and not spec.get("own_texture"):
            textures.add(icon_texture)
        icon = "%s_%s" % (NS, item_id)
        icons[icon] = {"textures": "textures/%s/%s" % (NS, icon_texture)}
        item_mappings.setdefault("minecraft:" + material.lower(), []).append({
            "type": "definition", "model": "%s:%s" % (NS, item_id),
            "bedrock_identifier": "%s:%s" % (NS, item_id), "display_name": name_es,
            "bedrock_options": {"icon": icon},
        })
        lang_es["item.%s.%s" % (NS, item_id)] = name_es
        lang_en["item.%s.%s" % (NS, item_id)] = name_en
        lines = [
            "# Generado por tools/alexscaves/generate_content.py (ítem propio de BigCasares, no está en el mod)."
            if "name" in spec else
            "# Generado por tools/alexscaves/generate_content.py desde Alex's Caves 2.0.2 (AlexModGuy, LGPL).",
            "id: " + catalog_id,
            "mechanic: alexs-caves",
            "material: " + material,
            "item-model: %s:%s" % (NS, item_id),
            "display:",
            "  translation-key: item.%s.%s" % (NS, item_id),
            '  fallback-name: "%s"' % name_es.replace('"', "'"),
            "  lore:",
            '    - "§8Alex\'s Caves"',
        ]
        desc = mod.lang["es_es"].get("block.%s.%s.desc" % (NS, item_id))
        if desc:
            # Cave paintings: the painting's own name is the mod's tooltip.
            lines.append('    - "§7%s"' % desc.replace('"', ""))
        lines += [
            "max-stack-size: %d" % spec.get("stack", 64),
        ]
        components = []
        if "max_damage" in spec:
            components.append("  max-damage: %d" % spec["max_damage"])
        if "food" in spec:
            # (nutrition, saturation modifier) as in the mod's FoodProperties; the catalog takes the
            # absolute saturation, which vanilla computes as nutrition * modifier * 2.
            nutrition, modifier = spec["food"]
            components += ["  food:", "    nutrition: %d" % nutrition,
                           "    saturation: %s" % round(nutrition * modifier * 2, 2),
                           "    can-always-eat: %s" % ("true" if spec.get("always_eat") else "false")]
        if "armor" in spec:
            slot, defense, toughness, equipment = spec["armor"]
            components += ["  armor:", "    slot: " + slot, "    defense: %s" % defense,
                           "    toughness: %s" % toughness, "    equipment-model: %s:%s" % (NS, equipment)]
            if "water_efficiency" in spec:
                components.append("    water-efficiency: %s" % spec["water_efficiency"])
            if "repair" in spec:
                components.append("    repair-item: " + spec["repair"])
            if "enchantability" in spec:
                components.append("    enchantability: %d" % spec["enchantability"])
        if "combat" in spec:
            # Totals as the player sees them (the catalog subtracts the player's base 1 damage / 4 speed).
            damage, speed = spec["combat"]
            components += ["  combat:", "    attack-damage: %s" % damage, "    attack-speed: %s" % speed]
        if components:
            lines += ["components:"] + components
        recipe = mod_recipe(mod, spec.get("recipe", item_id))
        if recipe is not None:
            lines += recipe_lines(item_id, recipe)
        lines += [
            "appearance:",
            "  java-item-definition: java/assets/%s/items/%s.json" % (NS, item_id),
            "  bedrock-texture: bedrock/textures/%s/%s.png" % (NS, icon_texture),
        ]
        write(os.path.join(res, "content/items", catalog_id + ".yml"), "\n".join(lines) + "\n")
        yaml_files.append(catalog_id)

    for item_id, spec in ITEMS.items():
        # block_item: the mod item is a block with a 3D model (e.g. the dinosaur chop), placed here as a plain item.
        add_item(item_id, spec, spec.get("block_item", False))
    for block_id, spec in BLOCKS.items():
        if spec.get("item", True):
            add_item(block_id, spec, True)
    for block_id, spec in SUBSTITUTES.items():
        add_item(block_id, dict(spec, item_texture=spec.get("item_texture")), spec.get("item_texture") is None)
    # ItemDisplay models (cave paintings, dinosaur chops): see display_models.py.
    display_models.write_display_models(mod, java, models, textures, CAVE_PAINTINGS, collect_model, write)
    display_models.write_painting_marks(mod, res, write)
    # Parents reached only through item models (wall_inventory, templates) were collected after the block models
    # were written: write every collected model again.
    for path, model in models.items():
        write(os.path.join(java, NS, "models", path + ".json"), model)

    for texture in sorted(textures):
        source = "assets/alexscaves/textures/%s.png" % texture
        if not mod.has(source):
            raise SystemExit("falta la textura " + source)
        data = mod.read(source)
        write(os.path.join(java, NS, "textures", texture + ".png"), data, binary=True)
        write(os.path.join(bedrock, "textures", NS, texture + ".png"), data, binary=True)
        meta = source + ".mcmeta"
        if mod.has(meta):
            write(os.path.join(java, NS, "textures", texture + ".png.mcmeta"), mod.read(meta), binary=True)

    # Worn armor: an equipment asset per set, drawn with the mod's armor layers.
    for name, (body, legs) in EQUIPMENT.items():
        write(os.path.join(java, NS, "equipment", name + ".json"), {"layers": {
            "humanoid": [{"texture": "%s:%s" % (NS, name)}],
            "humanoid_leggings": [{"texture": "%s:%s" % (NS, name)}]}})
        for layer, texture in (("humanoid", body), ("humanoid_leggings", legs)):
            data = robe_texture() if texture.startswith("generated:") else mod.read("assets/alexscaves/textures/%s.png" % texture)
            write(os.path.join(java, NS, "textures", "entity", "equipment", layer, name + ".png"), data, binary=True)

    write_sounds(mod, java, lang_es, lang_en)
    write_enchantments(mod, res, lang_es, lang_en)
    write(os.path.join(java, NS, "lang/es_es.json"), dict(sorted(lang_es.items())))
    write(os.path.join(java, NS, "lang/en_us.json"), dict(sorted(lang_en.items())))
    # The atlas is shared with other modules (the_sift_*): keep their entries, replace only ours.
    terrain_path = os.path.join(bedrock, "textures/terrain_texture.json")
    others = {}
    if os.path.exists(terrain_path):
        with open(terrain_path, encoding="utf-8") as handle:
            others = {key: value for key, value in json.load(handle).get("texture_data", {}).items()
                      if not key.startswith("alexscaves_")}
    write(terrain_path, {
        "resource_pack_name": "bigcasares", "texture_name": "atlas.terrain", "padding": 8, "num_mip_levels": 4,
        "texture_data": dict(sorted({**others, **terrain}.items())),
    })
    write(os.path.join(bedrock, "textures/item_texture.alexscaves.json"), {"texture_data": dict(sorted(icons.items()))})
    write(os.path.join(res, "alexscaves/geyser/alexscaves-items.json"),
          {"format_version": 2, "items": dict(sorted(item_mappings.items()))})
    # Block overrides, geometries and the block textures of the terrain atlas (needs the items mapping above).
    bedrock_blocks.build(repo, {block_id: mod.name("block", block_id) for block_id in BLOCKS})
    write(os.path.join(res, "alexscaves/items.txt"),
          "# Ítems del catálogo de Alex's Caves (mechanic alexs-caves). Generado; no editar a mano.\n"
          + "\n".join(sorted(yaml_files)) + "\n")
    print("bloques:", len(BLOCKS), "estados usados:", {base: sum(1 for v in allocation.values() for e in v.values()
                                                                  if e[0] == base) for base in BASE_BLOCKS},
          "ítems:", len(yaml_files), "texturas:", len(textures))


def bow_definition(item_id, java, textures):
    """Item definition of a bow with the mod's three pull textures (vanilla bow range dispatch, 20 ticks to draw)."""
    ref = lambda name: {"type": "minecraft:model", "model": "%s:item/%s" % (NS, name)}  # noqa: E731
    for stage in range(3):
        name = "%s_pulling_%d" % (item_id, stage)
        textures.add("item/" + name)
        write(os.path.join(java, NS, "models/item", name + ".json"),
              {"parent": "minecraft:item/bow", "textures": {"layer0": "%s:item/%s" % (NS, name)}})
    write(os.path.join(java, NS, "models/item", item_id + ".json"),
          {"parent": "minecraft:item/bow", "textures": {"layer0": "%s:item/%s" % (NS, item_id)}})
    return {"model": {
        "type": "minecraft:condition", "property": "minecraft:using_item",
        "on_false": ref(item_id),
        "on_true": {"type": "minecraft:range_dispatch", "property": "minecraft:use_duration", "scale": 0.05,
                    "entries": [{"threshold": 0.65, "model": ref(item_id + "_pulling_1")},
                                {"threshold": 0.9, "model": ref(item_id + "_pulling_2")}],
                    "fallback": ref(item_id + "_pulling_0")}}}


def totem_definition(item_id, java, textures):
    """Totem states: bound is custom_model_data flag 0, controlling is flag 1 (set by the module)."""
    ref = lambda name: {"type": "minecraft:model", "model": "%s:item/%s" % (NS, name)}  # noqa: E731
    for state in ("unbound", "bound", "active"):
        name = "%s_%s" % (item_id, state)
        textures.add("item/" + name)
        write(os.path.join(java, NS, "models/item", name + ".json"),
              {"parent": "minecraft:item/generated", "textures": {"layer0": "%s:item/%s" % (NS, name)}})
    write(os.path.join(java, NS, "models/item", item_id + ".json"),
          {"parent": "minecraft:item/generated", "textures": {"layer0": "%s:item/%s_unbound" % (NS, item_id)}})
    return {"model": {
        "type": "minecraft:condition", "property": "minecraft:custom_model_data", "index": 1,
        "on_true": ref(item_id + "_active"),
        "on_false": {"type": "minecraft:condition", "property": "minecraft:custom_model_data", "index": 0,
                     "on_true": ref(item_id + "_bound"), "on_false": ref(item_id + "_unbound")}}}


def robe_texture(seed=7):
    """Dark robe for the humanoid armor layer, with the near-black greys of the mod's darkness armor texture."""
    import io
    import random
    from PIL import Image
    rng = random.Random(seed)
    greys = [(11, 11, 11), (15, 15, 15), (21, 21, 21), (28, 28, 28)]
    image = Image.new("RGBA", (64, 32), (0, 0, 0, 0))
    for x in range(64):
        for y in range(32):
            image.putpixel((x, y), greys[min(3, int(abs(rng.gauss(0.8, 0.9))))] + (255,))
    out = io.BytesIO()
    image.save(out, "PNG")
    return out.getvalue()


# Sonidos propios del port (no son del mod): sus .ogg ya están en el repo y el generador no los copia.
OWN_SOUNDS = {
    "nuclear_bomb_activation": {"sounds": [{"name": "alexscaves:misc/nuke/activation", "stream": True,
                                            "attenuation_distance": 256}]},
    "nuclear_alert": {"sounds": [{"name": "alexscaves:misc/nuke/alert", "stream": True}]},
    "nuclear_detonation": {"sounds": [{"name": "alexscaves:misc/nuke/detonation", "stream": True}]},
}


def write_sounds(mod, java, lang_es, lang_en):
    """Copies the listed sound events; streamed music is re-encoded to mono VBR (q1) when ffmpeg exists."""
    events = mod.json("assets/alexscaves/sounds.json")
    ffmpeg = shutil.which("ffmpeg")
    published = {}
    for event in SOUNDS:
        if event not in events:
            raise SystemExit("evento de sonido inexistente en el mod: " + event)
        entry = events[event]
        published[event] = entry
        for sound in entry["sounds"]:
            name = sound["name"] if isinstance(sound, dict) else sound
            stream = isinstance(sound, dict) and sound.get("stream", False)
            path = name.split(":", 1)[1]
            data = mod.read("assets/alexscaves/sounds/%s.ogg" % path)
            target = os.path.join(java, NS, "sounds", path + ".ogg")
            if stream and ffmpeg:
                data = recompress(ffmpeg, data)
            write(target, data, binary=True)
        subtitle = entry.get("subtitle")
        if subtitle:
            lang_es[subtitle] = mod.lang["es_es"].get(subtitle) or mod.lang["en_us"].get(subtitle, event)
            lang_en[subtitle] = mod.lang["en_us"].get(subtitle, event)
    published.update(OWN_SOUNDS)
    write(os.path.join(java, NS, "sounds.json"), published)


RARITY = {"common": (10, 1), "uncommon": (5, 2), "rare": (2, 4), "very_rare": (1, 8)}


def write_enchantments(mod, res, lang_es, lang_en):
    """Data-driven enchantments of the module datapack; behaviour lives in the module code."""
    datapack = os.path.join(res, "alexscaves/datapack")
    for name, (material, rarity, max_level, min_cost) in ENCHANTMENTS.items():
        weight, anvil = RARITY[rarity]
        data = {
            "anvil_cost": anvil,
            "description": {"translate": "enchantment.alexscaves." + name},
            "effects": {},
            "max_cost": {"base": 31, "per_level_above_first": min_cost},
            "max_level": max_level,
            "min_cost": {"base": 1, "per_level_above_first": min_cost},
            "slots": ["mainhand", "offhand"],
            "supported_items": "minecraft:" + material,
            "weight": weight,
        }
        if name in ENCHANTMENT_EXCLUSIVE:
            data["exclusive_set"] = ["alexscaves:" + other for other in ENCHANTMENT_EXCLUSIVE[name]]
        write(os.path.join(datapack, "data/alexscaves/enchantment/%s.json" % name), data)
        key = "enchantment.alexscaves." + name
        lang_es[key] = mod.lang["es_es"].get(key) or mod.lang["en_us"].get(key, name)
        lang_en[key] = mod.lang["en_us"].get(key, name)
    write(os.path.join(datapack, "data/minecraft/tags/enchantment/in_enchanting_table.json"),
          {"replace": False, "values": ["alexscaves:" + name for name in ENCHANTMENTS]})
    files = []
    for folder, _, names in os.walk(datapack):
        for file_name in names:
            if file_name != "files.txt":
                files.append(os.path.relpath(os.path.join(folder, file_name), datapack).replace(os.sep, "/"))
    write(os.path.join(datapack, "files.txt"),
          "# Archivos del datapack que instala el módulo. Lo valida AlexsCavesDatapackContractTest.\n"
          + "\n".join(sorted(files)) + "\n")


def recompress(ffmpeg, data):
    with tempfile.TemporaryDirectory() as folder:
        source = os.path.join(folder, "in.ogg")
        target = os.path.join(folder, "out.ogg")
        with open(source, "wb") as handle:
            handle.write(data)
        subprocess.run([ffmpeg, "-v", "error", "-y", "-i", source, "-ac", "1", "-ar", "44100", "-c:a", "libvorbis", "-q:a", "1", target],
                       check=True)
        with open(target, "rb") as handle:
            return handle.read()


# Ítems del mod que en el módulo son bloques vanilla (las enredaderas de regaliz son enredaderas lloronas).
INGREDIENT_ITEMS = {
    "alexscaves:licoroot_vine": "WEEPING_VINES",
    # 1.20.1 item renamed in 26.x.
    "minecraft:chain": "IRON_CHAIN",
}
INGREDIENT_TAGS = {
    # Solo existe la gelatina roja (un color por mob).
    "alexscaves:gelatins": "bigcasares:ac_gelatin_red",
    "forge:ingots/iron": "IRON_INGOT",
    "forge:glass/colorless": "GLASS",
    "forge:storage_blocks/redstone": "REDSTONE_BLOCK",
    "forge:rods/wooden": "STICK",
    "forge:gems/diamond": "DIAMOND",
    "forge:raw_materials/iron": "RAW_IRON",
    "forge:raw_materials/uranium": "bigcasares:ac_uranium",
    "forge:storage_blocks/uranium": "bigcasares:ac_block_of_uranium",
    "forge:storage_blocks/iron": "IRON_BLOCK",
    "forge:barrels/wooden": "BARREL",
    "forge:dyes/green": "GREEN_DYE",
    "forge:dyes/lime": "LIME_DYE",
    "forge:dyes/white": "WHITE_DYE",
    "forge:dyes/orange": "ORANGE_DYE",
    "forge:dyes/magenta": "MAGENTA_DYE",
    "forge:dyes/light_blue": "LIGHT_BLUE_DYE",
    "forge:dyes/yellow": "YELLOW_DYE",
    "forge:dyes/pink": "PINK_DYE",
    "forge:dyes/gray": "GRAY_DYE",
    "forge:dyes/light_gray": "LIGHT_GRAY_DYE",
    "forge:dyes/cyan": "CYAN_DYE",
    "forge:dyes/purple": "PURPLE_DYE",
    "forge:dyes/blue": "BLUE_DYE",
    "forge:dyes/brown": "BROWN_DYE",
    "forge:dyes/red": "RED_DYE",
    "forge:dyes/black": "BLACK_DYE",
    "forge:gravel": "GRAVEL",
    "forge:nuggets/gold": "GOLD_NUGGET",
    "forge:nuggets/iron": "IRON_NUGGET",
    "alexscaves:ferns": "FERN",
    "alexscaves:pewen_logs": "bigcasares:ac_pewen_log",
    "alexscaves:thornwood_logs": "bigcasares:ac_thornwood_log",
}


def ingredient(value):
    if "tag" in value:
        if value["tag"] not in INGREDIENT_TAGS:
            raise SystemExit("tag sin mapear: " + value["tag"])
        return INGREDIENT_TAGS[value["tag"]]
    item = value["item"]
    if item in INGREDIENT_ITEMS:
        return INGREDIENT_ITEMS[item]
    if item.startswith("alexscaves:"):
        return "bigcasares:ac_" + item.split(":", 1)[1]
    return item.split(":", 1)[1].upper()


def mod_recipe(mod, name):
    """Shaped/shapeless crafting recipe of the mod with that file name, or None."""
    if not name:
        return None
    path = "data/alexscaves/recipes/%s.json" % name
    if not mod.has(path):
        return None
    data = mod.json(path)
    kind = data["type"].split(":", 1)[1]
    if kind == "crafting_shaped":
        return {"type": "shaped", "pattern": data["pattern"], "key": data["key"],
                "count": data["result"].get("count", 1)}
    if kind == "crafting_shapeless":
        return {"type": "shapeless", "ingredients": data["ingredients"], "count": data["result"].get("count", 1)}
    return None


def recipe_lines(item_id, recipe):
    lines = ["recipe:", "  key: ac_%s_recipe" % item_id, "  result-amount: %d" % recipe.get("count", 1)]
    if recipe["type"] == "shaped":
        lines.append("  shape: [%s]" % ", ".join('"%s"' % row for row in recipe["pattern"]))
        lines.append("  ingredients:")
        for symbol, value in recipe["key"].items():
            # Symbols such as "#" must be quoted: unquoted, YAML reads them as a comment.
            key = symbol if symbol.isalnum() else '"%s"' % symbol.replace('"', '\\"')
            lines.append("    %s: %s" % (key, ingredient(value)))
    else:
        lines.append("  type: shapeless")
        lines.append("  ingredients:")
        for value in recipe["ingredients"]:
            lines.append("    - " + ingredient(value))
    return lines


if __name__ == "__main__":
    if len(sys.argv) not in (3, 4):
        print(__doc__)
        sys.exit(2)
    main(sys.argv[1], sys.argv[2], sys.argv[3] if len(sys.argv) == 4 else None)
