"""Convierte los biomas de Alex's Caves 2.0.2 (1.20.1) al formato de datapack de 26.2.

Uso manual (no corre en el build):
    python -I tools/alexscaves/convert_biomes.py <alexscaves-2.0.2.jar> <biome vanilla 26.2 de referencia> <salida>

El JAR del mod solo se lee como zip (no se ejecuta). El bioma de referencia
(por ejemplo ``data/minecraft/worldgen/biome/plains.json`` extraído del server
vanilla 26.2) aporta las listas de features y carvers: el populator del módulo
asigna el bioma después de que corren las features vanilla, así que solo hace
falta que esas listas sean válidas en 26.2. Los mobs del mod los spawnea el
módulo; acá quedan solo los spawns vanilla de cada bioma.
"""
import json
import os
import sys
import zipfile

BIOMES = [
    "magnetic_caves",
    "primordial_caves",
    "toxic_caves",
    "abyssal_chasm",
    "forlorn_hollows",
    "candy_cavity",
]

# Partículas propias del mod -> partícula vanilla parecida (sin opciones).
PARTICLES = {
    "magnetic_caves": "minecraft:electric_spark",
    "toxic_caves": "minecraft:warped_spore",
    "forlorn_hollows": "minecraft:ash",
    "candy_cavity": "minecraft:white_ash",
}


# Efectos de cliente del mod (ACBiomeRegistry/ClientEvents) como atributos de entorno de 26.2:
# niebla (fog nearness), luz ambiental y color de la luz del bioma.
EXTRA_ATTRIBUTES = {
    "primordial_caves": {
        "minecraft:visual/fog_start_distance": {
            "argument": 0.5,
            "modifier": "multiply"
        },
        "minecraft:visual/ambient_light_color": "#2a2a2a"
    },
    "toxic_caves": {
        "minecraft:visual/fog_start_distance": {
            "argument": -0.15,
            "modifier": "multiply"
        },
        "minecraft:visual/ambient_light_color": "#0b140b",
        "minecraft:visual/block_light_tint": "#7fff7f"
    },
    "abyssal_chasm": {
        "minecraft:visual/fog_start_distance": {
            "argument": -0.3,
            "modifier": "multiply"
        },
        "minecraft:visual/block_light_tint": "#7f7fff"
    },
    "forlorn_hollows": {
        "minecraft:visual/fog_start_distance": {
            "argument": -0.2,
            "modifier": "multiply"
        },
        "minecraft:visual/block_light_tint": "#5a524d"
    },
    "candy_cavity": {
        "minecraft:visual/fog_start_distance": {
            "argument": 0.75,
            "modifier": "multiply"
        },
        "minecraft:visual/ambient_light_color": "#2e262a",
        "minecraft:visual/block_light_tint": "#ffd1e8"
    }
}

SPAWN_GROUPS = [
    "ambient", "axolotls", "creature", "misc", "monster",
    "underground_water_creature", "water_ambient", "water_creature",
]


# Biomes whose water stands in for the mod's acid (colour of the acid texture).
ACID_WATER = {"toxic_caves": "#7fd11b"}


def color(value):
    return "#%06x" % value


def sound_event(name):
    # El registro de eventos de sonido del servidor no es data-driven: los sonidos
    # del mod van inline y el cliente los resuelve con el sounds.json del pack.
    return {"sound_id": name}


def convert(mod_biome, reference, name):
    effects = mod_biome["effects"]
    attributes = {
        "minecraft:visual/sky_color": color(effects["sky_color"]),
        "minecraft:visual/fog_color": color(effects["fog_color"]),
        "minecraft:visual/water_fog_color": color(effects["water_fog_color"]),
        "minecraft:audio/background_music": {
            "default": {
                "max_delay": effects["music"]["max_delay"],
                "min_delay": effects["music"]["min_delay"],
                "sound": sound_event(effects["music"]["sound"]),
            }
        },
        "minecraft:audio/ambient_sounds": {
            "additions": {
                "sound": sound_event(effects["additions_sound"]["sound"]),
                "tick_chance": effects["additions_sound"]["tick_chance"],
            },
            "loop": sound_event(effects["ambient_sound"]),
            "mood": {
                "block_search_extent": effects["mood_sound"]["block_search_extent"],
                "offset": effects["mood_sound"]["offset"],
                "sound": sound_event(effects["mood_sound"]["sound"]),
                "tick_delay": effects["mood_sound"]["tick_delay"],
            },
        },
    }
    if name in PARTICLES and "particle" in effects:
        attributes["minecraft:visual/ambient_particles"] = [{
            "particle": {"type": PARTICLES[name]},
            "probability": effects["particle"]["probability"],
        }]
    attributes.update(EXTRA_ATTRIBUTES.get(name, {}))

    new_effects = {"water_color": color(effects["water_color"])}
    if name in ACID_WATER:
        # The plugin cannot add the mod's acid fluid: water in this biome is acid, coloured like it.
        new_effects["water_color"] = ACID_WATER[name]
        attributes["minecraft:visual/water_fog_color"] = ACID_WATER[name]
    for key in ("grass_color", "foliage_color"):
        if key in effects:
            new_effects[key] = color(effects[key])

    spawners = {group: [] for group in SPAWN_GROUPS}
    for group, entries in mod_biome.get("spawners", {}).items():
        if group not in spawners:
            continue
        for entry in entries:
            if entry["type"].startswith("minecraft:"):
                spawners[group].append({
                    "type": entry["type"],
                    "maxCount": entry["maxCount"],
                    "minCount": entry["minCount"],
                    "weight": entry["weight"],
                })

    result = {
        "attributes": dict(sorted(attributes.items())),
        "carvers": reference["carvers"],
        "downfall": mod_biome["downfall"],
        "effects": new_effects,
        "features": reference["features"],
        "has_precipitation": mod_biome["has_precipitation"],
        "spawn_costs": {},
        "spawners": spawners,
        "temperature": mod_biome["temperature"],
    }
    if "creature_spawn_probability" in mod_biome:
        result["creature_spawn_probability"] = mod_biome["creature_spawn_probability"]
    return result


def main(mod_jar, reference_path, out_dir):
    with open(reference_path, encoding="utf-8") as handle:
        reference = json.load(handle)
    os.makedirs(out_dir, exist_ok=True)
    with zipfile.ZipFile(mod_jar) as jar:
        for name in BIOMES:
            mod_biome = json.loads(jar.read("data/alexscaves/worldgen/biome/%s.json" % name))
            converted = convert(mod_biome, reference, name)
            target = os.path.join(out_dir, name + ".json")
            with open(target, "w", encoding="utf-8", newline="\n") as handle:
                json.dump(converted, handle, indent=2, sort_keys=False)
                handle.write("\n")
            print("escrito", target)


if __name__ == "__main__":
    if len(sys.argv) != 4:
        print(__doc__)
        sys.exit(2)
    main(sys.argv[1], sys.argv[2], sys.argv[3])
