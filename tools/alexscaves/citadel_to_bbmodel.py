"""Convierte modelos de entidad de Alex's Caves (Citadel, código Java) a .bbmodel para BetterModel.

Uso manual (no corre en el build; el código decompilado nunca entra al repo):
    python -I tools/alexscaves/citadel_to_bbmodel.py <fuente_decompilada> <assets_del_mod> <salida>
        [--report <informe.json>] [--specs tools/alexscaves/mob_models.txt] [<Modelo>:<textura>:<clave> ...]

    <fuente_decompilada>  carpeta con com/github/alexmodguy/alexscaves/{client/model,server/entity}/*.java
    <assets_del_mod>      carpeta assets/alexscaves del JAR descomprimido (texturas)
    <Modelo>              clase sin .java, p. ej. TeletorModel
    <textura>             ruta bajo textures/, p. ej. entity/teletor.png
    <clave>               nombre del modelo en BetterModel, p. ej. alexscaves_teletor
    --specs               archivo con una especificación por línea (la lista de todos los mobs del módulo)
    --report              JSON con, por modelo, cada animación, su origen en el mod y dónde la dispara el mod
                          (se fusiona con el existente)

Geometría: se traduce exactamente (pivotes, cajas, rotaciones, UV de caja y
espejado), incluidas las piezas de la superclase del modelo (SauropodBaseModel)
y las HideableModelBoxWithChildren.

Animaciones: citadel_anim.py ejecuta el setupAnim/animate del propio modelo y
se muestrea la pose tick a tick: "idle" y "walk" (bucles de duración exacta),
un bucle por cada estado de la entidad que cambia la pose (sit, fly, swim...),
una animación "una vez" por cada Animation de la entidad (fotogramas exactos de
ModelAnimator), "attack" (copia del golpe principal del mod o genérico) y
"death" (caída vanilla). Lo que el intérprete no puede saber (mundo, vectores,
otras entidades) vale 0 y se avisa.
"""
import base64
import json
import math
import os
import re
import struct
import sys
import uuid as uuidlib

sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))
import citadel_anim  # noqa: E402

FACES = ["north", "east", "south", "west", "up", "down"]
NUM = r"(-?\d+(?:\.\d+)?)F?"


def stable_uuid(*parts):
    return str(uuidlib.uuid5(uuidlib.NAMESPACE_URL, "alexscaves/" + "/".join(parts)))


def png_size(data):
    if data[:8] != b"\x89PNG\r\n\x1a\n":
        raise ValueError("no es PNG")
    return struct.unpack(">II", data[16:24])


class Part:
    def __init__(self, name):
        self.name = name
        self.pivot = [0.0, 0.0, 0.0]
        self.rotation = [0.0, 0.0, 0.0]
        self.parent = None
        self.children = []
        self.boxes = []
        self.tex = (0, 0)
        self.mirror = False


def model_source(source_dir, class_name):
    path = os.path.join(source_dir, "com", "github", "alexmodguy", "alexscaves", "client", "model", class_name + ".java")
    with open(path, encoding="utf-8") as handle:
        return handle.read()


def parse_model(source, source_dir=None):
    """Parses the parts built by the model constructor (after those of a Citadel model superclass, if any)."""
    text = re.sub(r"\s+", " ", source)
    parts = {}
    tex_w = tex_h = None
    sm = re.search(r"public (?:abstract )?class (\w+)(?:<[^{]*?>)? extends (\w+)", text)
    if source_dir and sm and os.path.exists(os.path.join(source_dir, "com", "github", "alexmodguy", "alexscaves",
                                                          "client", "model", sm.group(2) + ".java")):
        parts, _, tex_w, tex_h = parse_model(model_source(source_dir, sm.group(2)), source_dir)
    tw = re.search(r"this\.texWidth = (\d+);", text)
    th = re.search(r"this\.texHeight = (\d+);", text)
    tex_w = int(tw.group(1)) if tw else (tex_w or 64)
    tex_h = int(th.group(1)) if th else (tex_h or 32)
    class_name = re.search(r"public (?:abstract )?class (\w+)", text).group(1)
    ctor_at = text.find("public " + class_name + "(")
    for m in re.finditer(r"(?:AdvancedModelBox|HideableModelBoxWithChildren) (\w+) = new (?:AdvancedModelBox|HideableModelBoxWithChildren)\(this(?:, \"[^\"]*\")?\);", text[:ctor_at]):
        parts[m.group(1)] = Part(m.group(1))
    ctor = text[ctor_at:]
    ctor = ctor[:ctor.find("this.updateDefaultPose();")] if "this.updateDefaultPose();" in ctor else ctor
    for statement in ctor.split(";"):
        s = statement.strip()
        m = re.match(r"this\.(\w+) = new (?:AdvancedModelBox|HideableModelBoxWithChildren)\(this(?:, \"[^\"]*\")?\)", s)
        if m:
            parts[m.group(1)] = Part(m.group(1))
            continue
        m = re.match(r"this\.(\w+)\.setRotationPoint\(" + NUM + r", " + NUM + r", " + NUM + r"\)", s)
        if m and m.group(1) in parts:
            parts[m.group(1)].pivot = [float(m.group(i)) for i in (2, 3, 4)]
            continue
        m = re.match(r"this\.(\w+)\.addChild\(this\.(\w+)\)", s)
        if m and m.group(1) in parts and m.group(2) in parts:
            child = parts[m.group(2)]
            child.parent = parts[m.group(1)]
            parts[m.group(1)].children.append(child)
            continue
        m = re.match(r"this\.setRotateAngle\(this\.(\w+), " + NUM + r", " + NUM + r", " + NUM + r"\)", s)
        if m and m.group(1) in parts:
            parts[m.group(1)].rotation = [float(m.group(i)) for i in (2, 3, 4)]
            continue
        m = re.match(r"this\.(\w+)\.mirror = (true|false)", s)
        if m and m.group(1) in parts:
            parts[m.group(1)].mirror = m.group(2) == "true"
            continue
        m = re.match(r"this\.(\w+)((?:\.setTextureOffset\(\d+, \d+\))?)\.addBox\((.*)\)$", s)
        if m and m.group(1) in parts:
            part = parts[m.group(1)]
            if m.group(2):
                u, v = re.findall(r"\d+", m.group(2))
                part.tex = (int(u), int(v))
            args = [a.strip() for a in m.group(3).split(",")]
            nums = [float(a.rstrip("F")) for a in args[:6]]
            inflate = float(args[6].rstrip("F")) if len(args) > 6 and re.match(NUM + "$", args[6]) else 0.0
            mirror = part.mirror
            if len(args) > 7:
                mirror = args[7] == "true"
            elif len(args) == 7 and args[6] in ("true", "false"):
                mirror = args[6] == "true"
            part.boxes.append({"pos": nums[:3], "size": nums[3:6], "tex": part.tex, "inflate": inflate, "mirror": mirror})
            continue
        m = re.match(r"this\.(\w+)\.setTextureOffset\((\d+), (\d+)\)$", s)
        if m and m.group(1) in parts:
            parts[m.group(1)].tex = (int(m.group(2)), int(m.group(3)))
    roots = [p for p in parts.values() if p.parent is None]
    return parts, roots, tex_w, tex_h


def absolute_pivot(part):
    x, y, z = part.pivot
    node = part.parent
    while node is not None:
        x, y, z = x + node.pivot[0], y + node.pivot[1], z + node.pivot[2]
        node = node.parent
    return x, y, z


def bb_origin(part):
    # Java model space: Y down, origin at the feet + 24. Blockbench: Y up, X mirrored.
    x, y, z = absolute_pivot(part)
    return [-x, 24 - y, z]


def box_faces(u, v, w, h, d, mirror):
    faces = {
        "east": [u, v + d, u + d, v + d + h],
        "west": [u + d + w, v + d, u + 2 * d + w, v + d + h],
        "up": [u + d + w, v + d, u + d, v],
        "down": [u + d + 2 * w, v, u + d + w, v + d],
        "south": [u + 2 * d + w, v + d, u + 2 * d + 2 * w, v + d + h],
        "north": [u + d, v + d, u + d + w, v + d + h],
    }
    if mirror:
        faces["east"], faces["west"] = faces["west"], faces["east"]
        faces = {k: [uv[2], uv[1], uv[0], uv[3]] for k, uv in faces.items()}
    return faces


def clamp_uvs(elements, width, height):
    """Keeps every face inside the texture. Some of the mod's boxes reach past its edge; BetterModel drops such faces
    and a 26.2 client then refuses the faceless element, so the bone would vanish. Clamped, the face shows the edge."""
    for element in elements:
        for face in element["faces"].values():
            face["uv"] = [min(max(c, 0), width if i % 2 == 0 else height) for i, c in enumerate(face["uv"])]


def build_geometry(model_key, parts, roots):
    elements, groups, outliner = [], [], []

    def group_of(part):
        origin = bb_origin(part)
        gid = stable_uuid(model_key, "group", part.name)
        groups.append({"name": part.name, "uuid": gid, "export": True, "locked": False, "scope": 0,
                       "origin": [round(c, 4) for c in origin],
                       "rotation": [round(-math.degrees(part.rotation[0]), 4), round(-math.degrees(part.rotation[1]), 4),
                                    round(math.degrees(part.rotation[2]), 4)],
                       "color": 0, "reset": False, "shade": True, "mirror_uv": False, "visibility": True,
                       "autouv": 0, "isOpen": True})
        kids = []
        for index, box in enumerate(part.boxes):
            x, y, z = box["pos"]
            w, h, d = box["size"]
            to_x = origin[0] - x
            from_x = to_x - w
            to_y = origin[1] - y
            from_y = to_y - h
            from_z = z + origin[2]
            to_z = from_z + d
            eid = stable_uuid(model_key, "el", part.name, str(index))
            uvs = box_faces(box["tex"][0], box["tex"][1], w, h, d, box["mirror"])
            elements.append({
                "name": f"{part.name}_{index}", "type": "cube", "uuid": eid, "box_uv": False, "rescale": False,
                "locked": False, "render_order": "default", "allow_mirror_modeling": True,
                "from": [round(from_x, 4), round(from_y, 4), round(from_z, 4)],
                "to": [round(to_x, 4), round(to_y, 4), round(to_z, 4)],
                "autouv": 0, "color": 0, "inflate": box["inflate"], "origin": [round(c, 4) for c in origin],
                "rotation": [0, 0, 0],
                "faces": {f: {"uv": [round(c, 4) for c in uvs[f]], "texture": 0} for f in FACES},
                "export": True, "visibility": True, "shade": True, "mirror_uv": False, "light_emission": 0,
            })
            kids.append(eid)
        for child in part.children:
            kids.append(group_of(child))
        return {"uuid": gid, "isOpen": True, "children": kids}

    for root in roots:
        outliner.append(group_of(root))
    return elements, groups, outliner


# ---------------------------------------------------------------------------------------------------------------------
# Animations: the model's own setupAnim/animate code is run by citadel_anim and sampled per tick (20 tps).

WALK_AMOUNT = 0.6       # limbSwingAmount of a mob walking at its usual pace; limbSwing then advances 0.6 per tick
MAX_LOOP_TICKS = 400    # longest loop searched for the procedural cycles (20 s)
TOLERANCE = (0.4, 0.03, 0.003)  # degrees, pixels, scale: samples the interpolated curve already passes are dropped
STATE_NAME = re.compile(r"^(get|is|has)(\w+?)(Progress|Amount|Amt)?$")
STATE_KINDS = re.compile(r"(Progress|Amount|Amt)$|^(is|has)[A-Z]")
ATTACK_HINTS = ("MELEE", "ATTACK", "BITE", "SLASH", "SWIPE", "SCRATCH", "STAB", "PUNCH", "SLAP", "STRIKE", "SMASH",
                "SLAM", "STOMP", "SWING", "PECK", "LUNGE", "RAM", "CLAW", "HEADBUTT", "TAIL", "SPIT", "SHOOT", "CAST", "SPELL")
RESERVED = ("idle", "walk", "attack", "death")
STATE_ATTACK = re.compile(r"Bite|Attack|Lunge|Strike|Snap|Ram")
STATE_ATTACK_TICKS = 10
IGNORED_STATES = {"isFakeEntity"}  # only the guide-book/menu renders of the mod
MOVING_STATE = re.compile(r"Run|Sprint|Swim|Gallop|Charge|Dash|Roll")


def snake(name):
    return re.sub(r"(?<=[a-z0-9])(?=[A-Z])", "_", name).lower()


def state_animation_name(getter):
    m = STATE_NAME.match(getter)
    return snake(m.group(2) if m else getter)


def loop_length(frequencies):
    """Smallest length (ticks) after which every detected oscillation completes whole cycles (or the best one)."""
    freqs = sorted({round(f, 6) for f in frequencies if 1e-4 < f < 50})
    if not freqs:
        return 20.0, 0.0
    candidates = sorted({k * 2 * math.pi / f for f in freqs for k in range(1, 64)
                         if 4 <= k * 2 * math.pi / f <= MAX_LOOP_TICKS})
    if not candidates:
        candidates = [min(MAX_LOOP_TICKS, 2 * math.pi / freqs[0])]

    def error(length):
        return max(abs(f * length / (2 * math.pi) - round(f * length / (2 * math.pi))) for f in freqs)

    for length in candidates:
        if error(length) <= 0.01:
            return length, error(length)
    best = min(candidates, key=lambda length: (round(error(length), 2), length))
    return best, error(best)


def pose_delta(pose, default):
    """Citadel pose minus the rest pose, in Blockbench units: rotation degrees, position pixels, scale factor."""
    rx, ry, rz, px, py, pz, sx, sy, sz = pose
    drx, dry, drz, dpx, dpy, dpz = default[:6]
    return ((-math.degrees(rx - drx), -math.degrees(ry - dry), math.degrees(rz - drz)),
            (-(px - dpx), -(py - dpy), pz - dpz), (sx, sy, sz))


def prune(times, values, tol):
    """Indices of the samples to keep so linear interpolation between them stays within tol of every sample and no
    step reaches 90 (rotations would take the short way round). Linear keys reproduce the per-tick samples exactly;
    BetterModel's Catmull-Rom assumes evenly spaced keys and overshoots once keys are thinned out."""
    n = len(values)
    if n <= 2:
        return list(range(n))
    keep = [0]
    anchor = 0
    for j in range(2, n):
        ok = all(abs(values[j][c] - values[anchor][c]) < 90 for c in range(3))
        if ok:
            for k in range(anchor + 1, j):
                f = (times[k] - times[anchor]) / (times[j] - times[anchor])
                if any(abs(values[anchor][c] + (values[j][c] - values[anchor][c]) * f - values[k][c]) > tol
                       for c in range(3)):
                    ok = False
                    break
        if not ok:
            anchor = j - 1
            keep.append(anchor)
    keep.append(n - 1)
    return keep


def fmt(v):
    text = ("%.3f" % v).rstrip("0").rstrip(".")
    return "0" if text in ("-0", "") else text


def channel_keyframes(model_key, anim_name, bone, channel, times, values, tol, length):
    keyframes = []
    for index in prune(times, values, tol):
        x, y, z = values[index]
        # No per-keyframe uuid/color: BetterModel ignores them and Blockbench regenerates them; files stay small.
        keyframes.append({"channel": channel, "data_points": [{"x": fmt(x), "y": fmt(y), "z": fmt(z)}],
                          "time": min(round(times[index] / 20.0, 4), length), "interpolation": "linear"})
    return keyframes


def animation(model_key, name, loop, length, animators, override=False):
    return {"uuid": stable_uuid(model_key, "anim", name), "name": name, "loop": loop, "override": override,
            "length": length, "snapping": 20, "selected": False, "anim_time_update": "", "blend_weight": "",
            "start_delay": "", "loop_delay": "", "animators": animators}


def animators_from_samples(model_key, name, group_ids, defaults, times, poses, bones, length):
    animators = {}
    for bone in bones:
        if bone not in group_ids:
            continue
        deltas = [pose_delta(pose[bone], defaults[bone]) for pose in poses]
        keyframes = []
        for c, channel in enumerate(("rotation", "position", "scale")):
            values = [d[c] for d in deltas]
            rest = (1.0, 1.0, 1.0) if channel == "scale" else (0.0, 0.0, 0.0)
            if not all(math.isfinite(v) for value in values for v in value):
                # BetterModel rejects the whole model on inf/nan: keep the rest pose for this channel instead.
                print(f"  aviso: {name}/{bone}/{channel}: valores no finitos, se deja la pose de reposo",
                      file=sys.stderr)
                continue
            if all(abs(v[k] - rest[k]) <= TOLERANCE[c] / 4 for v in values for k in range(3)):
                continue
            keyframes.extend(channel_keyframes(model_key, name, bone, channel, times, values, TOLERANCE[c], length))
        if keyframes:
            animators[group_ids[bone]] = {"name": bone, "type": "bone", "rotation_global": False,
                                          "quaternion_interpolation": False, "keyframes": keyframes}
    return animators


def differs(a, b):
    return any(abs(x - y) > 1e-4 for x, y in zip(a, b))


def build_animations(source_dir, class_name, model_key, parts, roots, group_ids):
    """Returns (bbmodel animations, report) for every animation the model's code can produce."""
    rt = citadel_anim.Runtime(source_dir, class_name, parts)
    defaults = {name: p.default for name, p in rt.parts.items()}
    report = {"model": class_name, "entity": rt.entity_class, "animations": [], "warnings": rt.warnings}
    # Dry runs: discover the Animation constants, the entity getters and the oscillation frequencies.
    for t in (0.37, 11.3, 47.9):
        rt.run(citadel_anim.Context(t))
        rt.run(citadel_anim.Context(t, WALK_AMOUNT, WALK_AMOUNT))
    durations = {}
    owners = {a.owner for a in rt.seen_animations | rt.compared_animations}
    for owner in sorted(owners | ({rt.entity_class} if rt.entity_class else set())):
        for const, duration in citadel_anim.entity_animations(source_dir, owner).items():
            durations.setdefault(const, duration)
    rt.durations = durations
    states = {}
    for getter in sorted(rt.state_calls):
        if STATE_KINDS.search(getter) and getter not in IGNORED_STATES:
            states[getter] = True if getter.startswith(("is", "has")) else 1.0
    animations = []
    names = set()

    def add(anim, info):
        names.add(anim["name"])
        animations.append(anim)
        report["animations"].append(dict(name=anim["name"], loop=anim["loop"], length_s=anim["length"],
                                         override=anim["override"], **info))

    def sample_loop(name, rate, amount, state):
        rt.frequencies = []
        for t in (0.37, 5.1, 11.3, 23.7, 47.9):
            rt.run(citadel_anim.Context(t, rate, amount, states=state))
        length_ticks, seam = loop_length(rt.frequencies)
        steps = max(2, int(round(length_ticks)))
        times = [length_ticks * i / steps for i in range(steps + 1)]
        poses = [rt.run(citadel_anim.Context(t, rate, amount, states=state)) for t in times]
        length = round(length_ticks / 20.0, 4)
        bones = [b for b in rt.parts if any(differs(p[b], defaults[b]) for p in poses)]
        animators = animators_from_samples(model_key, name, group_ids, defaults, times, poses, bones, length)
        return animation(model_key, name, "loop", length, animators), poses, seam

    idle, _, seam = sample_loop("idle", 0.0, 0.0, {})
    add(idle, {"source": "setupAnim (limbSwingAmount=0, ageInTicks)", "seam_cycles": round(seam, 3)})
    walk, _, seam = sample_loop("walk", WALK_AMOUNT, WALK_AMOUNT, {})
    add(walk, {"source": f"setupAnim (limbSwingAmount={WALK_AMOUNT}, limbSwing += {WALK_AMOUNT}/tick)",
               "seam_cycles": round(seam, 3)})
    for getter, value in states.items():
        name = state_animation_name(getter)
        if name in RESERVED or name in names:
            name = name + "_state"
        moving = bool(MOVING_STATE.search(getter))
        rate = WALK_AMOUNT if moving else 0.0
        probe = (0.37, 5.1, 11.3, 23.7)
        if not any(differs(p[b], q[b])
                   for t in probe
                   for p, q in [(rt.run(citadel_anim.Context(t, rate, rate, states={getter: value})),
                                 rt.run(citadel_anim.Context(t, rate, rate)))]
                   for b in rt.parts):
            report.setdefault("unused_states", []).append(getter)
            continue
        anim, _, seam = sample_loop(name, rate, rate, {getter: value})
        add(anim, {"mod_triggers": citadel_anim.state_triggers(source_dir, rt.entity_class or "", getter),
                   "source": f"setupAnim with entity.{getter}() = {value}"
                             + (f" while moving (limbSwingAmount={WALK_AMOUNT})" if moving else " (standing)"),
                   "state": getter, "seam_cycles": round(seam, 3)})
    seen = {a.name for a in rt.seen_animations}
    keyframed = sorted(seen | {a.name for a in rt.compared_animations} | set(durations))
    actions = []
    for const in keyframed:
        duration = durations.get(const)
        if not duration:
            rt.warn(f"{const}: duration not found in the entity classes, skipped")
            continue
        ref = citadel_anim.AnimRef(rt.entity_class, const)
        times = list(range(duration + 1))
        poses = [rt.run(citadel_anim.Context(float(t), animation=ref if t < duration else citadel_anim.NO_ANIMATION,
                                             animation_tick=t)) for t in times]
        base = [rt.run(citadel_anim.Context(float(t))) for t in times]
        bones = [b for b in rt.parts if any(differs(p[b], q[b]) for p, q in zip(poses, base))]
        if not bones:
            report.setdefault("empty_animations", []).append(const)
            continue
        name = snake(const[len("ANIMATION_"):] if const.startswith("ANIMATION_") else const)
        if name in names or name in RESERVED:
            name = name + "_anim"
        length = round(duration / 20.0, 4)
        animators = animators_from_samples(model_key, name, group_ids, defaults, times, poses, bones, length)
        kind = "ModelAnimator keyframes + setupAnim" if const in seen else "setupAnim (getAnimation() == constant)"
        add(animation(model_key, name, "once", length, animators, override=True),
            {"source": f"{const} ({duration} ticks, {kind})", "constant": const, "ticks": duration,
             "mod_triggers": citadel_anim.animation_triggers(source_dir, rt.entity_class or "", const)})
        actions.append((const, name))
    # "attack" keeps working for existing callers: a copy of the mob's main melee animation when it has one.
    main = None
    for hint in ATTACK_HINTS:
        main = next((pair for pair in actions if hint in pair[0]), None)
        if main:
            break
    if main:
        attack = json.loads(json.dumps(next(a for a in animations if a["name"] == main[1])))
        attack["name"] = "attack"
        attack["uuid"] = stable_uuid(model_key, "anim", "attack")
        add(attack, {"source": f"copy of {main[1]} ({main[0]})", "alias_of": main[1]})
    elif any(STATE_ATTACK.search(g) for g in states):
        # Attacks the mod drives with a progress value (e.g. getBiteProgress): the value rises and falls once.
        getter = next(g for g in states if STATE_ATTACK.search(g))
        times = list(range(STATE_ATTACK_TICKS + 1))
        poses = [rt.run(citadel_anim.Context(float(t), states={getter: math.sin(math.pi * t / STATE_ATTACK_TICKS)}))
                 for t in times]
        base = [rt.run(citadel_anim.Context(float(t))) for t in times]
        bones = [b for b in rt.parts if any(differs(p[b], q[b]) for p, q in zip(poses, base))]
        length = round(STATE_ATTACK_TICKS / 20.0, 4)
        animators = animators_from_samples(model_key, "attack", group_ids, defaults, times, poses, bones, length)
        add(animation(model_key, "attack", "once", length, animators, override=True),
            {"source": f"entity.{getter}() rising 0->1->0 over {STATE_ATTACK_TICKS} ticks", "state": getter})
    else:
        add(generic_attack(model_key, roots, group_ids),
            {"source": "generic nod (the mod has no attack animation for this model)"})
    add(vanilla_death(model_key, roots, group_ids), {"source": "vanilla LivingEntityRenderer death tilt (20 ticks)"})
    return animations, report


def generic_attack(model_key, roots, group_ids):
    root = roots[0].name
    keys = []
    for index, (time, angle) in enumerate(((0, 0), (0.15, -15), (0.5, 0))):
        keys.append({"channel": "rotation", "data_points": [{"x": str(angle), "y": "0", "z": "0"}],
                     "uuid": stable_uuid(model_key, "attack", root, "rotation", str(index)), "time": time,
                     "color": -1, "interpolation": "linear"})
    return animation(model_key, "attack", "once", 0.5, {group_ids[root]: {
        "name": root, "type": "bone", "rotation_global": False, "quaternion_interpolation": False,
        "keyframes": keys}}, override=True)


def vanilla_death(model_key, roots, group_ids):
    """LivingEntityRenderer.setupRotations: Z tilt of min(1, sqrt((deathTime - 1) / 20 * 1.6)) * 90 about the feet."""
    animators = {}
    for root in roots:
        ox, oy, _ = bb_origin(root)
        times, rotations, positions = [], [], []
        for tick in range(21):
            angle = min(1.0, math.sqrt(max(0.0, (tick - 1) / 20.0 * 1.6))) * 90.0
            a = math.radians(angle)
            # The bone turns about its own pivot; the offset makes the turn happen about the entity's feet.
            times.append(tick)
            rotations.append((0.0, 0.0, angle))
            positions.append((ox * math.cos(a) - oy * math.sin(a) - ox, ox * math.sin(a) + oy * math.cos(a) - oy, 0.0))
        frames = channel_keyframes(model_key, "death", root.name, "rotation", times, rotations, TOLERANCE[0], 1.0)
        frames += channel_keyframes(model_key, "death", root.name, "position", times, positions, TOLERANCE[1], 1.0)
        animators[group_ids[root.name]] = {"name": root.name, "type": "bone", "rotation_global": False,
                                           "quaternion_interpolation": False, "keyframes": frames}
    return animation(model_key, "death", "hold", 1.0, animators)


def convert(source_dir, assets_dir, out_dir, spec):
    class_name, texture, model_key = spec.split(":")
    source = model_source(source_dir, class_name)
    parts, roots, tex_w, tex_h = parse_model(source, source_dir)
    if not roots:
        raise ValueError(class_name + ": no se encontraron partes")
    with open(os.path.join(assets_dir, "textures", texture), "rb") as handle:
        png = handle.read()
    png_w, png_h = png_size(png)
    elements, groups, outliner = build_geometry(model_key, parts, roots)
    clamp_uvs(elements, tex_w, tex_h)
    group_ids = {g["name"]: g["uuid"] for g in groups}
    animations, report = build_animations(source_dir, class_name, model_key, parts, roots, group_ids)
    data = {
        "meta": {"format_version": "5.0", "model_format": "free", "box_uv": False},
        "name": model_key, "model_identifier": "", "visible_box": [2, 3, 0],
        "variable_placeholders": "", "variable_placeholder_buttons": [], "timeline_setups": [],
        "unhandled_root_fields": {}, "resolution": {"width": tex_w, "height": tex_h},
        "elements": elements, "groups": groups, "outliner": outliner,
        "textures": [{
            "path": "", "name": model_key + ".png", "folder": "", "namespace": "", "id": "0", "group": "",
            "width": png_w, "height": png_h, "uv_width": tex_w, "uv_height": tex_h, "particle": False,
            "use_as_default": False, "layers_enabled": False, "sync_to_project": "", "render_mode": "default",
            "render_sides": "auto", "pbr_channel": "color", "frame_time": 1, "frame_order_type": "loop",
            "frame_order": "", "frame_interpolate": False, "visible": True, "internal": True, "saved": True,
            "uuid": stable_uuid(model_key, "texture"), "relative_path": model_key + ".png",
            "source": "data:image/png;base64," + base64.b64encode(png).decode(),
        }],
        "animations": animations,
    }
    os.makedirs(out_dir, exist_ok=True)
    target = os.path.join(out_dir, model_key + ".bbmodel")
    with open(target, "w", encoding="utf-8", newline="\n") as handle:
        json.dump(data, handle, separators=(",", ":"))
        handle.write("\n")
    print(f"{target}: {len(parts)} partes, {len(elements)} cubos, animaciones {[a['name'] for a in animations]}")
    for warning in report["warnings"]:
        print(f"  aviso: {warning}")
    report["texture"] = texture
    return model_key, report


def main(argv):
    args = list(argv[1:])
    report_path = None
    if "--report" in args:
        at = args.index("--report")
        report_path = args[at + 1]
        del args[at:at + 2]
    if "--specs" in args:
        at = args.index("--specs")
        with open(args[at + 1], encoding="utf-8") as handle:
            specs = [line.strip() for line in handle if line.strip() and not line.startswith("#")]
        del args[at:at + 2]
        args.extend(specs)
    if len(args) < 4:
        print(__doc__)
        return 2
    reports = {}
    for spec in args[3:]:
        key, report = convert(args[0], args[1], args[2], spec)
        reports[key] = report
    if report_path:
        existing = {}
        if os.path.exists(report_path):
            with open(report_path, encoding="utf-8") as handle:
                existing = json.load(handle)
        existing.update(reports)
        with open(report_path, "w", encoding="utf-8", newline="\n") as handle:
            json.dump(dict(sorted(existing.items())), handle, indent=1, ensure_ascii=False)
            handle.write("\n")
    return 0


if __name__ == "__main__":
    sys.exit(main(sys.argv))
