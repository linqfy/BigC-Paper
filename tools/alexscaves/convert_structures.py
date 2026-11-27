"""Convierte las plantillas de estructuras del mod (ruinas magnéticas y tóxicas, casas de cavernícola, copas de
chupetín y barras de chocolate) y sus tablas de botín para el módulo.

- Bloques del mod: el estado de note block que les asigna blocks.yml (eligiendo la variante por sus propiedades), el
  bloque vanilla de SUBSTITUTES, o un reemplazo de SPECIAL. Un bloque del mod sin equivalente corta la conversión.
- Marcadores "loot_chest" (bloques de estructura en modo DATA): el marcador queda como aire y el cofre de abajo
  recibe la tabla de botín del mod, como en UndergroundRuinsFeature.
- Botín: los ítems del mod que existen en el módulo salen como papel marcado (bigcasares:ac_loot) que el plugin
  cambia por el ítem real al generarse el botín; los que no existen quedan como entrada vacía con el mismo peso.

Uso: python convert_structures.py <alexscaves-2.0.2.jar> <repo>
"""
import json
import os
import sys
import zipfile

sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))
import content_tables as T  # noqa: E402
import nbt  # noqa: E402
from block_bases import load_allocation, properties  # noqa: E402
from generate_content import write  # noqa: E402

# Grupo de plantillas -> (marcador de datos -> tabla de botín del mod para el cofre de abajo, nombres de las plantillas
# o None para todas las que empiezan con "<grupo>_").
GROUPS = {
    "magnetic_ruins": ({"loot_chest": "magnetic_ruins"}, None),
    "toxic_ruins": ({"loot_chest": "toxic_ruins"}, None),
    "caveman_house": ({"loot_chest": "caveman_house"}, None),
    "big_lollipop_top": ({}, None),
    "small_lollipop_top": ({}, None),
    # Mod candy_ruins: chest_loot gingerbread_town.
    "chocolate_bar": ({"loot_chest": "gingerbread_town"}, None),
    "soda_bottle": ({}, ["soda_bottle", "soda_bottle_side"]),
    "licowitch_tower": ({"loot_chest": "licowitch_tower", "secret_loot_chest": "licowitch_tower_secret"},
                        ["licowitch_tower"]),
    "gingerbread_house": ({"loot_chest": "gingerbread_town"}, None),
    "gingerbread_town_center": ({"loot_chest": "gingerbread_town"}, ["gingerbread_town_center"]),
    "whalefall_skull": ({}, None),
    "whalefall_body": ({}, None),
    "whalefall_tail": ({}, None),
    # Mod deep_one_ruins: chest_loot abyssal_ruins.
    "deep_one_ruins": ({"loot_chest": "abyssal_ruins"}, None),
    "abyssal_ruins": ({"loot_chest": "abyssal_ruins"}, None),
    # Mod forlorn_ruins (chozas y el ídolo) y forlorn_posts: chest_loot forlorn_ruins.
    "forlorn_hut": ({"loot_chest": "forlorn_ruins"}, None),
    "forlorn_idol": ({"loot_chest": "forlorn_ruins"}, ["forlorn_idol"]),
    "forlorn_post": ({"loot_chest": "forlorn_ruins"}, None),
}
# Marcadores de datos con su bloque propio (mod processMarker): en las ruinas Deep One el submarino dañado queda lleno
# de agua.
GROUP_MARKERS = {
    # Mod AbyssalRuinsStructurePiece.handleDataMarker: todos sus marcadores quedan como aire de cueva (el submarino
    # que puede aparecer en "submarine_damaged" es una máquina fuera del alcance).
    "abyssal_ruins": {"submarine_damaged": ("minecraft:cave_air", {}), "submarine": ("minecraft:cave_air", {})},
    "deep_one_ruins": {"submarine_damaged": ("minecraft:water", {"level": "0"})},
}
# Propiedades del mod que no existen en los bloques vanilla que lo sustituyen.
MOD_ONLY_PROPS = {"distance", "active", "liquid_logged"}
# Reemplazos por grupo (mod: procesadores de la estructura). SodaBottleProcessor: el concreto es soda violeta, que en
# el módulo es el agua del bioma.
GROUP_SPECIAL = {
    "soda_bottle": {"minecraft:orange_concrete": ("minecraft:water", {"level": "0"}),
                    "minecraft:purple_concrete": ("minecraft:water", {"level": "0"})},
}
# Contenedores del mod que guardan el botín; en el módulo son barriles vanilla con la misma orientación.
MOD_BARRELS = ("alexscaves:metal_barrel", "alexscaves:rusty_barrel", "alexscaves:gingerbarrel")
SPECIAL = {
    # El ácido del mod es agua del bioma tóxico en el módulo (ver ToxicAcid).
    "alexscaves:acid": ("minecraft:water", {"level": "0"}),
    "alexscaves:archaic_vine": ("minecraft:cave_vines", {"age": "25", "berries": "false"}),
    "alexscaves:archaic_vine_plant": ("minecraft:cave_vines_plant", {"berries": "false"}),
    "alexscaves:metal_rebar": ("minecraft:iron_bars", None),
    "alexscaves:rusty_rebar": ("minecraft:iron_bars", None),
    # Sin bloque en el módulo: la estrella de árbol es un ítem.
    "alexscaves:tree_star": ("minecraft:air", {}),
    # Plantas sin colisión que el módulo todavía no dibuja.
    "alexscaves:candy_cane": ("minecraft:air", {}),
    "alexscaves:licoroot_sprout": ("minecraft:air", {}),
    "alexscaves:sweet_puff": ("minecraft:air", {}),
    "alexscaves:sundrop": ("minecraft:air", {}),
    "alexscaves:underweed": ("minecraft:air", {}),
    # Retoño de thornwood en maceta: maceta con un arbusto muerto (el retoño no es un bloque del módulo).
    "alexscaves:potted_thornwood_sapling": ("minecraft:potted_dead_bush", {}),
    # El crisol de conversión es una máquina fuera del alcance (D4): queda un caldero.
    "alexscaves:conversion_crucible": ("minecraft:cauldron", {}),
    # El desagüe es una máquina fuera del alcance (D4): queda una trampilla de hierro.
    "alexscaves:drain": ("minecraft:iron_trapdoor", {}),
}
IRON_BARS_PROPS = {"north", "east", "south", "west", "waterlogged"}
# Las pinturas rupestres del mod son caliza con un dibujo; no entran en el presupuesto de estados (ver PLAN).
PAINTING_STAND_IN = "limestone"


def variant_for(block_id, props):
    variants = T.BLOCKS[block_id]["variants"]
    for name, condition in variants.items():
        pairs = [part.split("=", 1) for part in condition.split(",") if part]
        if all(props.get(key) == value for key, value in pairs):
            return name
    return "default" if "default" in variants else next(iter(variants))


def note_block(allocation, block_id, props):
    base, index, _ = allocation[block_id][variant_for(block_id, props)]
    return "minecraft:" + base, properties(base, index)


def map_state(allocation, name, props, group=None):
    special = GROUP_SPECIAL.get(group, {})
    if name in special:
        return special[name]
    if name == "alexscaves:licoroot_vine":
        # Enredadera de regaliz: enredadera llorona vanilla si cuelga, retorcida si sube (la punta, crecida del todo).
        up = props.get("facing") == "up"
        if props.get("end") == "true":
            return ("minecraft:twisting_vines" if up else "minecraft:weeping_vines"), {"age": "25"}
        return ("minecraft:twisting_vines_plant" if up else "minecraft:weeping_vines_plant"), {}
    if not name.startswith("alexscaves:"):
        return name, props
    if name in SPECIAL:
        target, fixed = SPECIAL[name]
        if fixed is None:
            return target, {k: v for k, v in props.items() if k in IRON_BARS_PROPS}
        return target, fixed
    block_id = name.split(":", 1)[1]
    if block_id.startswith("cave_painting_"):
        return note_block(allocation, PAINTING_STAND_IN, {})
    if block_id in T.BLOCKS:
        return note_block(allocation, block_id, props)
    if block_id in T.SUBSTITUTES:
        material = T.SUBSTITUTES[block_id]["material"]
        kept = {} if material in ("TINTED_GLASS",) else {k: v for k, v in props.items() if k not in MOD_ONLY_PROPS}
        return "minecraft:" + material.lower(), kept
    raise SystemExit("bloque del mod sin equivalente en la plantilla: " + name)


def barrel_state(palette, facing):
    """Índice en la paleta de un barril vanilla cerrado con esa orientación (se agrega si falta)."""
    for index, entry in enumerate(palette):
        state = entry.value
        if state["Name"].value == "minecraft:barrel" and "Properties" in state                 and state["Properties"].value["facing"].value == facing:
            return index
    palette.append(nbt.Tag(nbt.COMPOUND, {
        "Name": nbt.Tag(nbt.STRING, "minecraft:barrel"),
        "Properties": nbt.Tag(nbt.COMPOUND, {"facing": nbt.Tag(nbt.STRING, facing),
                                             "open": nbt.Tag(nbt.STRING, "false")}),
    }))
    return len(palette) - 1


def convert_template(data, allocation, group, loot):
    name, root = nbt.load(data)
    tag = root.value
    palette = tag["palette"].value.value
    new_palette = []
    structure_block = None
    for index, entry in enumerate(palette):
        state = entry.value
        props = {k: v.value for k, v in state["Properties"].value.items()} if "Properties" in state else {}
        if state["Name"].value == "minecraft:structure_block":
            structure_block = index
        target, target_props = map_state(allocation, state["Name"].value, props, group)
        out = {"Name": nbt.Tag(nbt.STRING, target)}
        if target_props:
            out["Properties"] = nbt.Tag(nbt.COMPOUND, {k: nbt.Tag(nbt.STRING, v) for k, v in target_props.items()})
        new_palette.append(nbt.Tag(nbt.COMPOUND, out))
    air = len(new_palette)
    new_palette.append(nbt.Tag(nbt.COMPOUND, {"Name": nbt.Tag(nbt.STRING, "minecraft:cave_air")}))

    blocks = tag["blocks"].value.value
    by_pos = {tuple(t.value for t in b.value["pos"].value.value): b for b in blocks}
    markers = 0
    for block in blocks:
        entry = block.value
        original = palette[entry["state"].value].value["Name"].value
        if entry["state"].value == structure_block:
            meta = entry["nbt"].value.get("metadata") if "nbt" in entry else None
            entry["state"] = nbt.Tag(nbt.INT, air)
            marker_block = GROUP_MARKERS.get(group, {}).get(meta.value if meta is not None else None)
            if marker_block is not None:
                new_palette.append(nbt.Tag(nbt.COMPOUND, {
                    "Name": nbt.Tag(nbt.STRING, marker_block[0]),
                    "Properties": nbt.Tag(nbt.COMPOUND, {k: nbt.Tag(nbt.STRING, v) for k, v in marker_block[1].items()})}))
                entry["state"] = nbt.Tag(nbt.INT, len(new_palette) - 1)
            entry.pop("nbt", None)
            if meta is not None and meta.value in loot:
                x, y, z = (t.value for t in entry["pos"].value.value)
                chest = by_pos.get((x, y - 1, z))
                if chest is None:
                    raise SystemExit("marcador de botín sin contenedor debajo en " + group)
                container = palette[chest.value["state"].value].value
                container_name = container["Name"].value
                if container_name in MOD_BARRELS:
                    # Los barriles del mod guardan el botín; en el módulo son bloques sin inventario, así que el
                    # contenedor de botín es un barril vanilla con la misma orientación.
                    facing = container["Properties"].value["facing"].value if "Properties" in container else "up"
                    chest.value["state"] = nbt.Tag(nbt.INT, barrel_state(new_palette, facing))
                elif container_name not in ("minecraft:chest", "minecraft:barrel", "minecraft:trapped_chest"):
                    raise SystemExit("contenedor de botín desconocido en %s: %s" % (group, container_name))
                data_tag = chest.value["nbt"].value if "nbt" in chest.value else {}
                data_tag.pop("Items", None)
                data_tag["id"] = nbt.Tag(nbt.STRING, "minecraft:barrel" if "barrel" in container_name else "minecraft:chest")
                data_tag["LootTable"] = nbt.Tag(nbt.STRING, "alexscaves:chests/" + loot[meta.value])
                chest.value["nbt"] = nbt.Tag(nbt.COMPOUND, data_tag)
                markers += 1
        elif original.startswith("alexscaves:"):
            # Las entidades de bloque del mod no existen en el módulo.
            entry.pop("nbt", None)
    tag["palette"] = nbt.Tag(nbt.LIST, nbt.Tag(nbt.LIST, new_palette, nbt.COMPOUND), nbt.COMPOUND)
    tag["entities"] = nbt.Tag(nbt.LIST, nbt.Tag(nbt.LIST, [], nbt.COMPOUND), nbt.COMPOUND)
    return nbt.dump(name, root), markers


def number(value):
    """Rangos del mod ({min, max}) con el tipo explícito que pide 26.2."""
    if isinstance(value, dict) and "type" not in value and "min" in value:
        return {"type": "minecraft:uniform", "min": value["min"], "max": value["max"]}
    return value


def convert_loot(table, items):
    pools = []
    for pool in table["pools"]:
        entries = []
        for entry in pool["entries"]:
            weight = entry.get("weight", 1)
            name = entry.get("name", "")
            functions = []
            for function in entry.get("functions", []):
                kind = function["function"].split(":")[-1]
                if kind == "set_nbt":
                    continue
                if kind == "set_count":
                    function = dict(function, count=number(function["count"]))
                if kind == "set_damage":
                    function = dict(function, damage=number(function["damage"]))
                if kind == "enchant_with_levels":
                    function = {"function": "minecraft:enchant_with_levels", "levels": function["levels"]}
                functions.append(function)
            if name.startswith("alexscaves:"):
                item_id = "ac_" + name.split(":", 1)[1]
                if item_id not in items:
                    entries.append({"type": "minecraft:empty", "weight": weight})
                    continue
                functions.append({"function": "minecraft:set_custom_data",
                                  "tag": '{PublicBukkitValues:{"bigcasares:ac_loot":"%s"}}' % item_id})
                name = "minecraft:paper"
            converted = {"type": "minecraft:item", "name": name, "weight": weight}
            if functions:
                converted["functions"] = functions
            entries.append(converted)
        pools.append({"rolls": number(pool["rolls"]), "entries": entries})
    return {"type": "minecraft:chest", "pools": pools}


def main(jar_path, repo):
    mod = zipfile.ZipFile(jar_path)
    res = os.path.join(repo, "src/main/resources")
    allocation = load_allocation(os.path.join(res, "alexscaves/blocks.yml"))
    with open(os.path.join(res, "alexscaves/items.txt"), encoding="utf-8") as handle:
        items = {line.strip() for line in handle if line.strip() and not line.startswith("#")}
    index = {}
    totals = {}
    tables = set()
    for group, (loot, only) in GROUPS.items():
        if only is None:
            names = sorted(n for n in mod.namelist()
                           if n.startswith("data/alexscaves/structures/%s_" % group) and n.endswith(".nbt"))
        else:
            names = ["data/alexscaves/structures/%s.nbt" % name for name in only]
        index[group] = []
        for path in names:
            converted, markers = convert_template(mod.read(path), allocation, group, loot)
            totals[group] = totals.get(group, 0) + markers
            file_name = os.path.basename(path)
            write(os.path.join(res, "alexscaves/structures", file_name), converted, binary=True)
            index[group].append(file_name[:-4])
        tables.update(loot.values())
    for name in sorted(tables):
        table = json.loads(mod.read("data/alexscaves/loot_tables/chests/%s.json" % name))
        write(os.path.join(res, "alexscaves/datapack/data/alexscaves/loot_table/chests/%s.json" % name),
              convert_loot(table, items))
    write(os.path.join(res, "alexscaves/structures/index.json"), index)
    print("plantillas:", {group: len(names) for group, names in index.items()}, "cofres de botín:", totals)


if __name__ == "__main__":
    if len(sys.argv) != 3:
        raise SystemExit(__doc__)
    main(sys.argv[1], sys.argv[2])
