#!/usr/bin/env python3
"""Export what the server needs to draw the mod's non-cube blocks over their vanilla look-alikes.

Usage:
    python3 -I tools/export_sift_block_models.py <mods jar> <Minecraft 26.2 client jar>

Slabs, stairs, walls, doors, plants and the other non-cube blocks of the mod are placed as vanilla look-alikes
(SiftStandIns). For players close by, SiftBlockModels draws the mod's block model over each placed look-alike
with an item display. This writes:

    src/main/resources/the_sift/block_models.json        per look-alike, the mod's blockstate (variants or
                                                         multipart) with each model as an item model id
    resourcepack/java/assets/the_sift/items/block/*.json one item definition per block model, so that an item
                                                         display can draw it
    resourcepack/java/assets/the_sift/models/block/overlay/*.json
                                                         those models with their geometry written out (vanilla
                                                         templates such as slab and stairs read from the client
                                                         jar) and every element grown by INFLATE, so each face of
                                                         the model lies just outside the look-alike's face instead
                                                         of flickering with it
    resourcepack/java/assets/the_sift/models/block/ichor_glass*.json
                                                         the Ichor glass and pane models: the mod's use 26.3-only
                                                         syntax and are not imported, so these are written with
                                                         26.2's glass templates and the mod's textures

The vanilla look-alike has the same block state properties as the mod's block (a slab is a slab), so the
placed block's state picks the model. Run it after tools/port_mielon_sift.py, which replaces the Java assets.
Both jars are read as ZIP archives; no code is loaded.
"""
import json
import re
import sys
import zipfile
from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]
STAND_INS = ROOT / "src/main/java/dev/linqfy/bigCasares/modules/sift/SiftStandIns.java"
SIFT = ROOT / "resourcepack/java/assets/the_sift"
OUTPUT = ROOT / "src/main/resources/the_sift/block_models.json"
ITEMS = SIFT / "items/block"
OVERLAYS = SIFT / "models/block/overlay"
GLASS_PANE_PARTS = ("post", "side", "side_alt", "noside", "noside_alt")
# In model units (16 to a block): 0.0025 blocks, enough for the depth buffer at the overlay radius, far below a pixel.
INFLATE = 0.04


def write_json(path, data):
    path.parent.mkdir(parents=True, exist_ok=True)
    path.write_text(json.dumps(data, indent=2) + "\n", encoding="utf-8")


def glass_models():
    write_json(SIFT / "models/block/ichor_glass.json",
               {"parent": "minecraft:block/cube_all", "textures": {"all": "the_sift:block/ichor_glass"}})
    for part in GLASS_PANE_PARTS:
        write_json(SIFT / f"models/block/ichor_glass_pane_{part}.json", {
            "parent": f"minecraft:block/template_glass_pane_{part}",
            "textures": {"pane": "the_sift:block/ichor_glass", "edge": "the_sift:block/ichor_glass_pane_top"}})


def item_model(model, used):
    """the_sift:block/<name> -> the item model the_sift:block/<name>, whose definition draws that block model."""
    if not model.startswith("the_sift:block/"):
        raise ValueError(f"unexpected model {model}")
    if not (SIFT / "models" / (model.split(":", 1)[1] + ".json")).is_file():
        raise ValueError(f"{model} is not in the Java pack")
    used.add(model)
    return model


def piece(apply, used):
    """One model of a blockstate entry; random choices keep the first, as the custom blocks do."""
    chosen = apply[0] if isinstance(apply, list) else apply
    return {"model": item_model(chosen["model"], used), "x": chosen.get("x", 0), "y": chosen.get("y", 0)}


def load_model(reference, client):
    namespace, _, path = reference.partition(":") if ":" in reference else ("minecraft", "", reference)
    if namespace == "the_sift":
        return json.loads((SIFT / "models" / (path + ".json")).read_text(encoding="utf-8"))
    return json.loads(client.read(f"assets/minecraft/models/{path}.json"))


def flattened(model, client):
    """The model's textures (child over parent) and the first elements along its parent chain."""
    textures, elements, reference = {}, None, model
    while reference:
        data = load_model(reference, client)
        textures = {**data.get("textures", {}), **textures}
        if elements is None and "elements" in data:
            elements = data["elements"]
        reference = data.get("parent")
    if elements is None:
        raise ValueError(f"{model} has no elements")
    return textures, elements


def inflated(element):
    grown = dict(element)
    grown["from"] = [round(value - INFLATE, 4) for value in element["from"]]
    grown["to"] = [round(value + INFLATE, 4) for value in element["to"]]
    return grown


def write_overlay(model, client):
    textures, elements = flattened(model, client)
    name = model.split("/", 1)[1]
    write_json(OVERLAYS / (name + ".json"), {"textures": textures, "elements": [inflated(e) for e in elements]})
    return f"the_sift:block/overlay/{name}"


def main(arguments):
    if len(arguments) != 2:
        print(__doc__)
        return 2
    paths = re.findall(r'(?:block|plant)\("([a-z_]+)"', STAND_INS.read_text(encoding="utf-8"))
    glass_models()
    used, states = set(), {}
    with zipfile.ZipFile(arguments[0]) as jar:
        for path in paths:
            blockstate = json.loads(jar.read(f"assets/the_sift/blockstates/{path}.json"))
            if "variants" in blockstate:
                states[path] = {"variants": {key: [piece(apply, used)] for key, apply in blockstate["variants"].items()}}
            else:
                states[path] = {"multipart": [{"when": part.get("when", {}), "apply": [piece(part["apply"], used)]}
                                              for part in blockstate["multipart"]]}
    write_json(OUTPUT, {
        "_comment": "Generated by tools/export_sift_block_models.py from the mod's blockstates. Edit the tool, not this file.",
        "blocks": states,
    })
    if ITEMS.exists():
        for old in ITEMS.glob("*.json"):
            old.unlink()
    if OVERLAYS.exists():
        for old in OVERLAYS.glob("*.json"):
            old.unlink()
    with zipfile.ZipFile(arguments[1]) as client:
        for model in sorted(used):
            overlay = write_overlay(model, client)
            write_json(ITEMS / (model.split("/", 1)[1] + ".json"), {"model": {"type": "minecraft:model", "model": overlay}})
    print(f"{len(states)} look-alikes, {len(used)} block models")
    return 0


if __name__ == "__main__":
    sys.exit(main(sys.argv[1:]))
