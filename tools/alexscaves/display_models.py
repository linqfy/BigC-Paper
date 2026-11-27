"""Modelos de los ItemDisplay que dibujan bloques del mod sin estado de bloque propio.

- Pinturas rupestres: solo la cara pintada (un cuadrado en el borde sur del modelo, mirando al norte), para un
  display parado en el bloque de aire delante de la caliza (así toma la luz de ese bloque y no la de adentro de la
  piedra). Ítem: alexscaves:display/cave_painting_<id>.
- Chuletas de dinosaurio: los modelos de bloque del mod por mordisco (0 a 3), crudas y cocidas. Ítem:
  alexscaves:display/<chuleta>_<mordiscos>.
"""
import os

NS = "alexscaves"
# Un poco delante de la cara de la caliza (16 = el borde del bloque), para que no parpadee contra ella.
PAINTING_DEPTH = 15.95
DIRECTIONS = {"down", "up", "north", "south", "west", "east"}


def write_display_models(mod, java, models, textures, paintings, collect_model, write):
    for painting in paintings:
        texture = "block/cave_painting/" + painting
        textures.add(texture)
        ref = "%s:%s" % (NS, texture)
        write(os.path.join(java, NS, "models/display/cave_painting_%s.json" % painting), {
            "textures": {"painting": ref, "particle": ref},
            "elements": [{"from": [0, 0, PAINTING_DEPTH], "to": [16, 16, PAINTING_DEPTH],
                          "faces": {"north": {"uv": [0, 0, 16, 16], "texture": "#painting"}}}],
        })
        write(os.path.join(java, NS, "items/display/cave_painting_%s.json" % painting),
              {"model": {"type": "minecraft:model", "model": "%s:display/cave_painting_%s" % (NS, painting)}})
    # El modelo base del mod usa "cullface": "painting", que no es una cara: se saca.
    base = models.get("block/cave_painting_base")
    if base is not None:
        for element in base.get("elements", []):
            for face in element.get("faces", {}).values():
                if face.get("cullface") not in DIRECTIONS:
                    face.pop("cullface", None)
    for chop, block in (("dinosaur_chop", "dinosaur_chop"), ("cooked_dinosaur_chop", "dinosaur_chop_cooked")):
        for bites in range(4):
            model = "block/" + block + ("" if bites == 0 else "_%d" % bites)
            collect_model(mod, "%s:%s" % (NS, model), models, textures)
            write(os.path.join(java, NS, "items/display/%s_%d.json" % (chop, bites)),
                  {"model": {"type": "minecraft:model", "model": "%s:%s" % (NS, model)}})


def write_painting_marks(mod, res, write):
    """paintings.json: las pinturas de las casas de cavernícola del mod, por plantilla, como [x, y, z, pintura, cara]
    en coordenadas de la plantilla. Las plantillas convertidas tienen caliza en esos lugares; al generar la casa el
    módulo pone ahí la caliza lisa y el display (ver RuinsFeature y CavePaintings)."""
    import sys
    sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))
    import nbt
    prefix = "alexscaves:cave_painting_"
    marks = {}
    for path in sorted(mod.names):
        if not (path.startswith("data/alexscaves/structures/caveman_house_") and path.endswith(".nbt")):
            continue
        _, root = nbt.load(mod.read(path))
        palette = root.value["palette"].value.value
        found = []
        for block in root.value["blocks"].value.value:
            state = palette[block.value["state"].value].value
            name = state["Name"].value
            if name.startswith(prefix):
                facing = state["Properties"].value["facing"].value if "Properties" in state else "south"
                found.append([t.value for t in block.value["pos"].value.value] + [name[len(prefix):], facing])
        marks[os.path.basename(path)[:-4]] = sorted(found)
    write(os.path.join(res, "alexscaves/structures/paintings.json"), marks)
