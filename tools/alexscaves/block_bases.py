"""Bloques vanilla que dibujan los bloques del módulo (bases) y su asignación estable.

Cada base es un bloque vanilla con estados que el mundo vanilla no usa solo, que el pack
de la capa del módulo redibuja con el modelo del mod:

- ``note_block``: cubos opacos. Índice ``instrument * 50 + note * 2 + powered``; los
  ``instrument=harp`` (0-49) quedan vanilla.
- ``tripwire``: bloques sin colisión ni oclusión (mejillones, gusanos de hueso...).
  Índice ``attached*64 + disarmed*32 + powered*16 + north*8 + east*4 + south*2 + west``.
  Solo se asignan los ``disarmed=true``: un hilo vanilla solo pasa por ese estado en el
  mismo tick en que unas tijeras lo rompen, así que ningún hilo de jugador ni de
  estructura (templos de la jungla) cambia de aspecto ni se confunde con un bloque.
- ``chorus_plant``: bloques sólidos que no son cubos. Índice
  ``down*32 + up*16 + north*8 + east*4 + south*2 + west``. La generación del End solo
  produce plantas con ``down=true`` o con ``down=false`` y exactamente una conexión
  horizontal (la rama hacia su tallo); se asignan los demás (``down=false`` con dos o
  más conexiones horizontales, o solo ``up``) menos el estado por defecto (todo en
  false), que queda vanilla.

blocks.yml guarda ``variante: índice`` (note block) o ``variante: base índice`` y, si el
bloque estaba antes en un note block (o es nuevo en tripwire / chorus plant: su estado de reserva para el modo
legacy), ``legacy índice``: ese estado de note block queda
reservado (los mundos viejos lo tienen) y se migra al cargar el chunk.
"""

NOTE_BLOCK = "note_block"
TRIPWIRE = "tripwire"
CHORUS_PLANT = "chorus_plant"

INSTRUMENTS = [
    "harp", "basedrum", "snare", "hat", "bass", "flute", "bell", "guitar", "chime", "xylophone",
    "iron_xylophone", "cow_bell", "didgeridoo", "bit", "banjo", "pling",
]
FIRST_CUSTOM_NOTE = 50
# Rarest natural instruments first (block below a note block that vanilla players rarely use).
NOTE_ORDER = ["pling", "bit", "banjo", "didgeridoo", "cow_bell", "chime", "iron_xylophone", "xylophone",
              "flute", "bell", "guitar", "hat", "snare", "bass", "basedrum"]

COUNTS = {NOTE_BLOCK: 800, TRIPWIRE: 128, CHORUS_PLANT: 64}


def _bit(index, shift):
    return "true" if (index >> shift) & 1 else "false"


def properties(base, index):
    """Properties of a state, sorted by name (the order of Java state strings and Geyser keys)."""
    if base == NOTE_BLOCK:
        return {"instrument": INSTRUMENTS[index // 50], "note": str((index % 50) // 2),
                "powered": "true" if index % 2 else "false"}
    if base == TRIPWIRE:
        return {"attached": _bit(index, 6), "disarmed": _bit(index, 5), "east": _bit(index, 2),
                "north": _bit(index, 3), "powered": _bit(index, 4), "south": _bit(index, 1), "west": _bit(index, 0)}
    if base == CHORUS_PLANT:
        return {"down": _bit(index, 5), "east": _bit(index, 2), "north": _bit(index, 3), "south": _bit(index, 1),
                "up": _bit(index, 4), "west": _bit(index, 0)}
    raise ValueError(base)


def state_key(base, index):
    return ",".join("%s=%s" % item for item in sorted(properties(base, index).items()))


def allocatable(base, index):
    """States that never occur in a vanilla world (see the module docstring)."""
    if base == NOTE_BLOCK:
        return index >= FIRST_CUSTOM_NOTE
    if base == TRIPWIRE:
        return (index >> 5) & 1 == 1
    if base == CHORUS_PLANT:
        down = (index >> 5) & 1
        up = (index >> 4) & 1
        horizontal = bin(index & 0b1111).count("1")
        return down == 0 and (horizontal >= 2 or (horizontal == 0 and up == 1))
    raise ValueError(base)


def free_order(base):
    if base == NOTE_BLOCK:
        return [index for name in NOTE_ORDER
                for index in range(INSTRUMENTS.index(name) * 50, INSTRUMENTS.index(name) * 50 + 50)
                if allocatable(base, index)]
    return [index for index in range(COUNTS[base]) if allocatable(base, index)]


def base_of(spec, variant):
    """``base`` of a BLOCKS entry: a name, or {variant: name} with ``*`` for the rest."""
    base = spec.get("base", NOTE_BLOCK)
    if isinstance(base, dict):
        return base.get(variant, base.get("*", NOTE_BLOCK))
    return base


def load_allocation(path):
    """blocks.yml as {block: {variant: (base, index, legacy note index or None)}}."""
    import os
    allocation = {}
    if not os.path.isfile(path):
        return allocation
    current = None
    for line in open(path, encoding="utf-8"):
        if line.startswith("#") or not line.strip():
            continue
        if not line.startswith(" "):
            current = line.strip().rstrip(":")
            allocation[current] = {}
            continue
        variant, value = line.strip().split(":")
        tokens = value.split()
        if len(tokens) == 1:
            allocation[current][variant.strip()] = (NOTE_BLOCK, int(tokens[0]), None)
        else:
            legacy = int(tokens[3]) if len(tokens) == 4 and tokens[2] == "legacy" else None
            allocation[current][variant.strip()] = (tokens[0], int(tokens[1]), legacy)
    return allocation


def format_entry(entry):
    base, index, legacy = entry
    if base == NOTE_BLOCK:
        return str(index)
    return "%s %d" % (base, index) + ("" if legacy is None else " legacy %d" % legacy)


def allocate(allocation, blocks):
    """Append-only: existing states never move. A block whose base changed takes a new state
    of its new base and keeps its old note block state as ``legacy`` (reserved forever)."""
    used = {base: set() for base in COUNTS}
    for variants in allocation.values():
        for base, index, legacy in variants.values():
            used[base].add(index)
            if legacy is not None:
                used[NOTE_BLOCK].add(legacy)
    free = {base: [index for index in free_order(base) if index not in used[base]] for base in COUNTS}
    for block_id, spec in blocks.items():
        variants = allocation.setdefault(block_id, {})
        for variant in spec["variants"]:
            want = base_of(spec, variant)
            current = variants.get(variant)
            if current is not None and current[0] == want:
                continue
            legacy = None
            if current is not None:
                if current[0] != NOTE_BLOCK:
                    raise SystemExit("%s %s: solo se puede mover un bloque desde note_block" % (block_id, variant))
                legacy = current[1]
            elif want != NOTE_BLOCK:
                # A new tripwire / chorus plant block also takes a note block state as its fallback: without
                # Paper's update options (legacy mode) every block needs a note block state to live on.
                if not free[NOTE_BLOCK]:
                    raise SystemExit("no quedan estados libres de " + NOTE_BLOCK)
                legacy = free[NOTE_BLOCK].pop(0)
            if not free[want]:
                raise SystemExit("no quedan estados libres de " + want)
            variants[variant] = (want, free[want].pop(0), legacy)
    return allocation


# --- vanilla look of the states the module does not use ---------------------------------------------------

def vanilla_tripwire(index):
    """Vanilla tripwire model of a state (26.2 ``blockstates/tripwire.json``: by attached and the 4 sides)."""
    props = properties(TRIPWIRE, index)
    prefix = "minecraft:block/tripwire_attached_" if props["attached"] == "true" else "minecraft:block/tripwire_"
    sides = [name for name in ("north", "east", "south", "west") if props[name] == "true"]
    order = ["north", "east", "south", "west"]
    if len(sides) == 0:
        return {"model": prefix + "ns"}
    if len(sides) == 4:
        return {"model": prefix + "nsew"}
    if len(sides) == 2 and order.index(sides[1]) - order.index(sides[0]) == 2:
        return dict({"model": prefix + "ns"}, **({"y": 90} if sides[0] == "east" else {}))
    # One side, two adjacent sides or three sides: the vanilla model of the run starting at its first side
    # clockwise, turned to that side.
    shape = {1: "n", 2: "ne", 3: "nse"}[len(sides)]
    present = set(sides)
    start = next(side for side in order
                 if side in present and order[(order.index(side) - 1) % 4] not in present)
    turn = order.index(start) * 90
    return dict({"model": prefix + shape}, **({"y": turn} if turn else {}))


CHORUS_SIDE_PARTS = [
    ("north", {"model": "minecraft:block/chorus_plant_side"}),
    ("east", {"model": "minecraft:block/chorus_plant_side", "uvlock": True, "y": 90}),
    ("south", {"model": "minecraft:block/chorus_plant_side", "uvlock": True, "y": 180}),
    ("west", {"model": "minecraft:block/chorus_plant_side", "uvlock": True, "y": 270}),
    ("up", {"model": "minecraft:block/chorus_plant_side", "uvlock": True, "x": 270}),
    ("down", {"model": "minecraft:block/chorus_plant_side", "uvlock": True, "x": 90}),
]


def _noside(rotation, order):
    names = ["chorus_plant_noside", "chorus_plant_noside1", "chorus_plant_noside2", "chorus_plant_noside3"]
    out = []
    for i in order:
        entry = {"model": "minecraft:block/" + names[i]}
        if rotation:
            entry["uvlock"] = True
            entry.update(rotation)
        if i == 0:
            entry["weight"] = 2
        out.append(entry)
    return out


# Same parts and random weights as the 26.2 chorus_plant.json.
CHORUS_NOSIDE_PARTS = [
    ("north", _noside({}, [0, 1, 2, 3])),
    ("east", _noside({"y": 90}, [1, 2, 3, 0])),
    ("south", _noside({"y": 180}, [2, 3, 0, 1])),
    ("west", _noside({"y": 270}, [3, 0, 1, 2])),
    ("up", _noside({"x": 270}, [0, 3, 1, 2])),
    ("down", _noside({"x": 90}, [3, 2, 1, 0])),
]


def blockstate(base, custom):
    """Blockstate definition of a base: ``custom`` {index: rendered variant}; other states look vanilla."""
    if base == TRIPWIRE:
        variants = {}
        for index in range(COUNTS[base]):
            variants[state_key(base, index)] = custom.get(index) or vanilla_tripwire(index)
        return {"variants": variants}
    if base == CHORUS_PLANT:
        plain = [index for index in range(COUNTS[base]) if index not in custom]
        parts = []
        for side, apply in CHORUS_SIDE_PARTS:
            states = [properties(base, i) for i in plain if properties(base, i)[side] == "true"]
            parts.append({"when": {"OR": states}, "apply": apply})
        for side, apply in CHORUS_NOSIDE_PARTS:
            states = [properties(base, i) for i in plain if properties(base, i)[side] == "false"]
            parts.append({"when": {"OR": states}, "apply": apply})
        for index in sorted(custom):
            parts.append({"when": properties(base, index), "apply": custom[index]})
        return {"multipart": parts}
    raise ValueError(base)


# --- client-only remap (packet layer) -----------------------------------------------------------------------
#
# With ``blocks.packet-remap`` and packetevents, Java players receive some module states as a state of another vanilla
# block (a "client family") whose extra properties the client never draws nor simulates, so the pack can draw the
# module model on the borrowed values while real blocks of that family are sent with one canonical value.
# The server never stores these client states: the table is recomputed on every
# generation (the pack and the plugin ship together).
#
# Rules:
# - Server collision must never be bigger than the client's (the server would pull the player back), so solid
#   server states (note block, chorus plant) only borrow full-collision families; tripwire states (no collision)
#   may borrow anything.
# - Dry: only note block states that are not full opaque cubes (the client culls the faces around a note block).
#   Tripwire and chorus plant states do not hide their neighbours: they stay as they are while dry.
# - Wet: waterloggable module blocks placed in water (tracked per position by the plugin) get a waterlogged family.
# - Translucent models stay on note blocks: every family draws in the cutout layer.

LEAVES_TYPES = ["flowering_azalea_leaves", "azalea_leaves", "pale_oak_leaves", "mangrove_leaves", "cherry_leaves",
                "dark_oak_leaves", "jungle_leaves", "acacia_leaves", "birch_leaves", "spruce_leaves", "oak_leaves"]
# Lightning rods: powered only lights the rod for 8 ticks after a strike; real ones are sent unpowered.
ROD_TYPES = ["waxed_oxidized_lightning_rod", "waxed_weathered_lightning_rod", "waxed_exposed_lightning_rod",
             "waxed_lightning_rod", "oxidized_lightning_rod", "weathered_lightning_rod", "exposed_lightning_rod",
             "lightning_rod"]
ROD_FACINGS = ["down", "east", "north", "south", "up", "west"]
ROD_ROTATION = {"down": {"x": 180}, "east": {"x": 90, "y": 90}, "north": {"x": 90}, "south": {"x": 90, "y": 180},
                "up": {}, "west": {"x": 90, "y": 270}}

# Every property value of each borrowed vanilla block (sorted by name: the order of Java state strings).
FAMILY_PROPERTIES = dict(
    [(kind, [("distance", [str(d) for d in range(1, 8)]), ("persistent", ["false", "true"]),
             ("waterlogged", ["false", "true"])]) for kind in LEAVES_TYPES]
    + [(kind, [("facing", ROD_FACINGS), ("powered", ["false", "true"]), ("waterlogged", ["false", "true"])])
       for kind in ROD_TYPES]
    + [("kelp", [("age", [str(a) for a in range(26)])]), ("chorus_flower", [("age", [str(a) for a in range(6)])])])


def client_state(block, props):
    return "minecraft:%s[%s]" % (block, ",".join("%s=%s" % (k, props[k]) for k in sorted(props)))


def canonical(block, props):
    """The value real blocks of a borrowed state are sent with (never borrowed itself)."""
    props = dict(props)
    if block in LEAVES_TYPES:
        props.update(distance="7", persistent="false")
    elif block in ROD_TYPES:
        props["powered"] = "false"
    elif block == "kelp":
        props["age"] = "25"
    elif block == "chorus_flower":
        props["age"] = "4"
    else:
        raise ValueError(block)
    return props


def vanilla_variant(block, props):
    """The 26.2 vanilla model of a state of a borrowed block (assets/minecraft/blockstates)."""
    if block in LEAVES_TYPES or block == "kelp":
        return {"model": "minecraft:block/" + block}
    if block == "chorus_flower":
        return {"model": "minecraft:block/chorus_flower" + ("_dead" if props["age"] == "5" else "")}
    if block in ROD_TYPES:
        model = "lightning_rod_on" if props["powered"] == "true" else block.replace("waxed_", "")
        return dict({"model": "minecraft:block/" + model}, **ROD_ROTATION[props["facing"]])
    raise ValueError(block)


def _leaves_slots(waterlogged):
    # Persistent ones first: natural leaves are never persistent, so the first slots only touch player leaves.
    return [(kind, {"distance": str(d), "persistent": p, "waterlogged": waterlogged})
            for p, distances in (("true", range(1, 8)), ("false", range(1, 7)))
            for kind in LEAVES_TYPES for d in distances]


def slot_pools():
    """Borrowable client states by use, best first. Leaves: full collision, never occlude, no client simulation of
    distance or persistent. Chorus flower ages 0-4 share one model (full collision, no occlusion). Kelp: no collision,
    always water, ages share one model. Lightning rods: thin collision, waterloggable."""
    return {
        "dry_solid": _leaves_slots("false") + [("chorus_flower", {"age": str(a)}) for a in range(4)],
        "wet_solid": _leaves_slots("true"),
        # Rods first: a powered rod is rare in vanilla, while every ocean has kelp of every age (borrowing kelp would
        # make every ocean chunk be decoded and re-encoded on its way to the client).
        "wet_free": [(kind, {"facing": f, "powered": "true", "waterlogged": "true"})
                     for kind in ROD_TYPES for f in ROD_FACINGS]
                    + [("kelp", {"age": str(a)}) for a in range(25)],
    }


def client_remap(blocks, allocation, shapes, waterloggable, dry_last=()):
    """Client states of the module states.

    ``shapes`` {(block, variant): "full" | "partial" | "translucent"} (from the mod models); ``waterloggable``: block
    ids in priority order; ``dry_last``: blocks that only get a dry state if one is left. A block gets a family for all
    its variants or for none (one block never looks half done).
    Returns (entries [(block, variant, base, index, dry or None, wet or None)], {borrowed state: canonical state},
    uncovered [(block, reason)])."""
    pools = slot_pools()
    dry, wet, uncovered = {}, {}, []

    def take(pool, count):
        if len(pools[pool]) < count:
            return None
        taken, pools[pool] = pools[pool][:count], pools[pool][count:]
        return taken

    order = [b for b in blocks if b not in dry_last] + [b for b in dry_last if b in blocks]
    for block_id in order:
        # Full cube variants look the same on the note block: only the others borrow a state.
        variants = [v for v in blocks[block_id]["variants"]
                    if allocation[block_id][v][0] == NOTE_BLOCK and shapes[(block_id, v)] != "full"]
        if not variants:
            continue
        if any(shapes[(block_id, v)] == "translucent" for v in variants):
            uncovered.append((block_id, "translúcido (seco)"))
            continue
        slots = take("dry_solid", len(variants))
        if slots is None:
            uncovered.append((block_id, "sin estados secos libres"))
            continue
        dry.update({(block_id, v): s for v, s in zip(variants, slots)})
    for block_id in waterloggable:
        variants = list(blocks[block_id]["variants"])
        if any(shapes[(block_id, v)] == "translucent" for v in variants):
            uncovered.append((block_id, "translúcido (en agua)"))
            continue
        free = allocation[block_id][variants[0]][0] == TRIPWIRE
        slots = (take("wet_free", len(variants)) if free else None) or take("wet_solid", len(variants))
        if slots is None:
            uncovered.append((block_id, "sin estados mojados libres"))
            continue
        wet.update({(block_id, v): s for v, s in zip(variants, slots)})
    entries, normalize = [], {}
    for block_id, spec in blocks.items():
        for variant in spec["variants"]:
            pair = [dry.get((block_id, variant)), wet.get((block_id, variant))]
            if pair == [None, None]:
                continue
            base, index, _ = allocation[block_id][variant]
            states = []
            for slot in pair:
                if slot is None:
                    states.append(None)
                    continue
                state = client_state(*slot)
                normalize[state] = client_state(slot[0], canonical(*slot))
                states.append(state)
            entries.append((block_id, variant, base, index, states[0], states[1]))
    return entries, normalize, uncovered


def family_blockstates(borrowed):
    """{vanilla block: blockstate} for every borrowed block: ``borrowed`` {client state: rendered variant}; every other
    state keeps its vanilla 26.2 model."""
    out = {}
    blocks = sorted({state.split("[")[0][len("minecraft:"):] for state in borrowed})
    for block in blocks:
        names = [name for name, _ in FAMILY_PROPERTIES[block]]
        combos = [{}]
        for name, values in FAMILY_PROPERTIES[block]:
            combos = [dict(c, **{name: v}) for c in combos for v in values]
        variants = {}
        for props in combos:
            key = ",".join("%s=%s" % (n, props[n]) for n in names)
            variants[key] = borrowed.get(client_state(block, props)) or vanilla_variant(block, props)
        out[block] = {"variants": variants}
    return out
