"""Convierte schema/ciudad.schematic (MCEdit, IDs numericos pre-1.13) al formato que usa el plugin.

Salida: src/main/resources/donpollos/structures/ciudad.json.gz
  {"size": [ancho, alto, largo], "palette": ["minecraft:...[props]", ...], "runs": [[indice, cantidad], ...]}
Orden de los bloques: y, luego z, luego x (igual que el schematic). Incluye el aire, asi al pegarla
se limpia el terreno que quede adentro.

Uso: python tools/convertir_schematic.py
"""
import gzip
import json
import sys
from pathlib import Path

sys.path.insert(0, str(Path(__file__).resolve().parent))
import nbt  # noqa: E402

ROOT = Path(__file__).resolve().parents[2]  # tools/donpollos -> raiz de BigC-Paper
FUENTES = Path(__file__).resolve().parent / "fuentes"
SOURCE = FUENTES / "schema" / "ciudad.schematic"
TARGET = ROOT / "src" / "main" / "resources" / "donpollos" / "structures" / "ciudad.json.gz"

# (id, data) -> bloque moderno. Solo los que usa la ciudad; si aparece otro, falla y avisa.
LEGACY = {
    (0, 0): "minecraft:air",
    (1, 0): "minecraft:stone",
    (2, 0): "minecraft:grass_block",
    (35, 1): "minecraft:orange_wool",
    (35, 8): "minecraft:light_gray_wool",
    (35, 15): "minecraft:black_wool",
    (44, 0): "minecraft:smooth_stone_slab[type=bottom]",
    (44, 7): "minecraft:quartz_slab[type=bottom]",
    (155, 0): "minecraft:quartz_block",
    (159, 8): "minecraft:light_gray_terracotta",
    (160, 0): "minecraft:white_stained_glass_pane",
    (160, 3): "minecraft:light_blue_stained_glass_pane",
    (160, 8): "minecraft:light_gray_stained_glass_pane",
    (160, 15): "minecraft:black_stained_glass_pane",
}
PANE_IDS = {160, 102}
SLAB_IDS = {44}


def main():
    _, s = nbt.read(SOURCE)
    w, h, l = s["Width"], s["Height"], s["Length"]
    blocks, data = s["Blocks"], s["Data"]

    def at(x, y, z):
        if 0 <= x < w and 0 <= y < h and 0 <= z < l:
            i = (y * l + z) * w + x
            return blocks[i], data[i] & 0x0F
        return 0, 0

    def connects(block_id):
        # los paneles se unen a otros paneles y a bloques enteros (no a aire ni a losas)
        return block_id != 0 and block_id not in SLAB_IDS

    palette, index, runs = [], {}, []
    for y in range(h):
        for z in range(l):
            for x in range(w):
                key = at(x, y, z)
                if key not in LEGACY:
                    raise SystemExit(f"Bloque sin traducir {key} en {x},{y},{z}: agregalo a LEGACY")
                state = LEGACY[key]
                if key[0] in PANE_IDS:
                    sides = {
                        "north": connects(at(x, y, z - 1)[0]),
                        "south": connects(at(x, y, z + 1)[0]),
                        "east": connects(at(x + 1, y, z)[0]),
                        "west": connects(at(x - 1, y, z)[0]),
                    }
                    props = ",".join(f"{k}={'true' if v else 'false'}" for k, v in sorted(sides.items()))
                    state = f"{state}[{props},waterlogged=false]"
                if state not in index:
                    index[state] = len(palette)
                    palette.append(state)
                i = index[state]
                if runs and runs[-1][0] == i:
                    runs[-1][1] += 1
                else:
                    runs.append([i, 1])
    TARGET.parent.mkdir(parents=True, exist_ok=True)
    payload = json.dumps({"size": [w, h, l], "palette": palette, "runs": runs}, separators=(",", ":"))
    TARGET.write_bytes(gzip.compress(payload.encode("utf-8")))
    print(f"{TARGET.name}: {w}x{h}x{l}, {len(palette)} estados, {len(runs)} tramos, {TARGET.stat().st_size} bytes")


if __name__ == "__main__":
    main()
