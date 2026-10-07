"""Las 5 coreografias del Don Pollo Salsero, sacadas de video_referencia/.

Convencion de Blockbench 5 (la que lee BetterModel con format_version 5.0):
  rotacion X +  -> el miembro va hacia ADELANTE (brazo levantado al frente, rodilla arriba)
  rotacion X -  -> hacia atras (canilla doblada = rodilla flexionada)
  rotacion Z +  -> brazo DERECHO hacia afuera; brazo IZQUIERDO hacia afuera es Z -
  rotacion Y +  -> gira hacia la izquierda del personaje
  torso X -     -> se inclina hacia adelante
  posicion en pixeles (16 = un bloque)

Huesos: root > hip > torso > hi_head / rightarm > rightforearm / leftarm > leftforearm
                hip > rightleg > rightshin / leftleg > leftshin
"""


def P(**bones):
    """Pose: hueso=(rx, ry, rz) o hueso=dict(rot=(...), pos=(...))."""
    return bones


def mirror(pose):
    """Espeja una pose izquierda <-> derecha (Y y Z cambian de signo)."""
    swap = {"right": "left", "left": "right"}
    out = {}
    for bone, value in pose.items():
        name = bone
        for a, b in swap.items():
            if bone.startswith(a):
                name = b + bone[len(a):]
                break
        if isinstance(value, dict):
            out[name] = {k: (v[0], -v[1], -v[2]) if k == "rot" else (-v[0], v[1], v[2]) for k, v in value.items()}
        else:
            out[name] = (value[0], -value[1], -value[2])
    return out


# ------------------------------------------------------------------ poses base

BOXER_R = dict(rightarm=(35, 0, 12), rightforearm=(85, 0, 0))
BOXER_L = dict(leftarm=(35, 0, -12), leftforearm=(85, 0, 0))
KNEES_DOWN = dict(rightleg=(22, 0, 0), rightshin=(-40, 0, 0), leftleg=(22, 0, 0), leftshin=(-40, 0, 0))
KNEES_UP = dict(rightleg=(0, 0, 0), rightshin=(0, 0, 0), leftleg=(0, 0, 0), leftshin=(0, 0, 0))


def merge(*parts):
    out = {}
    for part in parts:
        out.update(part)
    return out


# ------------------------------------------------------------------ baile 1
# Video 1: rebote con rodillas flexionadas, brazos de boxeador cruzando golpes alternados,
# el torso gira hacia el golpe y alterna pasitos levantando una pierna.
def baile1():
    down = dict(root={"pos": (0, -1.2, 0)}, **KNEES_DOWN)
    up = dict(root={"pos": (0, 0, 0)})
    cross_r = merge(BOXER_L, dict(rightarm=(85, 0, -40), rightforearm=(15, 0, 0),
                                  torso=(-8, 30, 0), hi_head=(0, -15, 0)))
    step_r = dict(rightleg=(45, 0, 0), rightshin=(-65, 0, 0), leftleg=(8, 0, 0), leftshin=(-14, 0, 0))
    guard = merge(BOXER_R, BOXER_L, dict(torso=(-8, 0, 0), hi_head=(-6, 0, 0)))
    return 1.6, "linear", {
        0.0: merge(guard, up, KNEES_UP),
        0.2: merge(guard, down),
        0.4: merge(cross_r, up, step_r),
        0.6: merge(guard, down),
        0.8: merge(guard, up, KNEES_UP),
        1.0: merge(guard, down),
        1.2: merge(mirror(cross_r), up, mirror(step_r)),
        1.4: merge(guard, down),
        1.6: merge(guard, up, KNEES_UP),
    }


# ------------------------------------------------------------------ baile 2
# Video 2 (Steve): brazos abiertos que se balancean con los codos doblados y rodillazos;
# despues se pone de perfil y hace el "arm roll" con los antebrazos girando adelante del pecho.
def baile2():
    open_r = dict(rightarm=(25, 0, 65), rightforearm=(45, 0, 0), leftarm=(10, 0, -35), leftforearm=(70, 0, 0),
                  root=(0, 0, -6), rightleg=(30, 0, 0), rightshin=(-50, 0, 0), leftleg=(0, 0, 0), leftshin=(0, 0, 0),
                  hi_head=(0, -10, 6), torso=(0, -8, 0))
    roll_a = dict(root=(0, 45, 0), torso=(-10, 0, 0), hi_head=(0, -20, 0),
                  rightarm=(70, 0, -12), rightforearm=(110, 0, 0), leftarm=(55, 0, 12), leftforearm=(60, 0, 0),
                  rightleg=(18, 0, 0), rightshin=(-34, 0, 0), leftleg=(18, 0, 0), leftshin=(-34, 0, 0))
    roll_b = dict(roll_a, rightarm=(55, 0, -12), rightforearm=(60, 0, 0), leftarm=(70, 0, 12), leftforearm=(110, 0, 0),
                  rightleg=(5, 0, 0), rightshin=(-8, 0, 0), leftleg=(5, 0, 0), leftshin=(-8, 0, 0))
    rest = dict(root=(0, 0, 0), torso=(0, 0, 0), hi_head=(0, 0, 0), rightarm=(15, 0, 30), rightforearm=(50, 0, 0),
                leftarm=(15, 0, -30), leftforearm=(50, 0, 0), **KNEES_UP)
    return 2.0, "catmullrom", {
        0.0: rest,
        0.25: open_r,
        0.5: rest,
        0.75: mirror(open_r),
        1.0: dict(rest, root=(0, 30, 0)),
        1.125: roll_a, 1.25: roll_b, 1.375: roll_a, 1.5: roll_b, 1.625: roll_a, 1.75: roll_b,
        2.0: rest,
    }


# ------------------------------------------------------------------ baile 3
# Video 3 (freestyle): brazos abiertos en avion inclinando el cuerpo, patadas hacia atras,
# un giro completo y un salto en estrella.
def baile3():
    plane_r = dict(rightarm=(0, 0, 85), rightforearm=(10, 0, 0), leftarm=(0, 0, -85), leftforearm=(10, 0, 0),
                   root={"rot": (0, 0, 14), "pos": (0, 0, 0)}, hi_head=(0, 0, -8))
    kick_r = dict(rightleg=(-25, 0, 0), rightshin=(-95, 0, 0), leftleg=(10, 0, 0), leftshin=(-18, 0, 0))
    flat = dict(**KNEES_UP)
    spin_mid = dict(root={"rot": (0, 180, 0), "pos": (0, -1, 0)}, rightarm=(0, 0, 70), leftarm=(0, 0, -70),
                    rightforearm=(30, 0, 0), leftforearm=(30, 0, 0), **KNEES_DOWN)
    spin_end = dict(root={"rot": (0, 360, 0), "pos": (0, 0, 0)}, rightarm=(0, 0, 85), leftarm=(0, 0, -85), **KNEES_UP)
    jump = dict(root={"rot": (0, 360, 0), "pos": (0, 5, 0)}, rightarm=(0, 0, 150), leftarm=(0, 0, -150),
                rightforearm=(0, 0, 0), leftforearm=(0, 0, 0),
                rightleg=(0, 0, 28), leftleg=(0, 0, -28), rightshin=(0, 0, 0), leftshin=(0, 0, 0), hi_head=(-10, 0, 0))
    land = dict(root={"rot": (0, 360, 0), "pos": (0, -1.5, 0)}, rightarm=(0, 0, 85), leftarm=(0, 0, -85),
                rightforearm=(10, 0, 0), leftforearm=(10, 0, 0), hi_head=(0, 0, 0), **KNEES_DOWN)
    start = dict(plane_r, root={"rot": (0, 0, 0), "pos": (0, 0, 0)}, hi_head=(0, 0, 0), **flat)
    return 2.4, "catmullrom", {
        0.0: start,
        0.3: merge(plane_r, kick_r),
        0.6: dict(start),
        0.9: merge(mirror(plane_r), mirror(kick_r)),
        1.2: dict(start),
        1.5: spin_mid,
        1.8: spin_end,
        2.05: jump,
        2.25: land,
        2.4: dict(start, root={"rot": (0, 360, 0), "pos": (0, 0, 0)}),
    }


# ------------------------------------------------------------------ baile 4
# Video 4 (disco): con la mano izquierda en la cintura, el brazo derecho apunta al cielo en
# diagonal y baja cruzando el cuerpo, rodillas bombeando; despues el "hand roll" frente al pecho.
def baile4():
    hip_hand = dict(leftarm=(-5, 0, -40), leftforearm=(30, 0, 95))
    point_up = merge(hip_hand, dict(rightarm=(25, 0, 150), rightforearm=(0, 0, 0), root=(0, -15, 0),
                                    torso=(0, 0, 6), hi_head=(-20, 0, 0),
                                    rightleg=(0, 0, 0), rightshin=(0, 0, 0), leftleg=(25, 0, 0), leftshin=(-45, 0, 0)))
    point_down = merge(hip_hand, dict(rightarm=(35, 0, -40), rightforearm=(15, 0, 0), root=(0, 15, 0),
                                      torso=(-6, 0, -4), hi_head=(12, 0, 0),
                                      rightleg=(25, 0, 0), rightshin=(-45, 0, 0), leftleg=(0, 0, 0), leftshin=(0, 0, 0)))
    roll_a = dict(rightarm=(60, 0, -8), rightforearm=(70, 0, 0), leftarm=(60, 0, 8), leftforearm=(110, 0, 0),
                  root={"rot": (0, 10, 0), "pos": (0, -0.8, 0)}, torso=(-6, 0, 0), hi_head=(0, 0, 0), **KNEES_DOWN)
    roll_b = dict(rightarm=(60, 0, -8), rightforearm=(110, 0, 0), leftarm=(60, 0, 8), leftforearm=(70, 0, 0),
                  root={"rot": (0, -10, 0), "pos": (0, 0, 0)}, torso=(-6, 0, 0), hi_head=(0, 0, 0), **KNEES_UP)
    rest = merge(hip_hand, dict(rightarm=(10, 0, 20), rightforearm=(30, 0, 0), root={"rot": (0, 0, 0), "pos": (0, 0, 0)},
                                torso=(0, 0, 0), hi_head=(0, 0, 0)), KNEES_UP)
    return 2.0, "catmullrom", {
        0.0: rest,
        0.25: point_up, 0.5: point_down, 0.75: point_up, 1.0: point_down,
        1.125: roll_a, 1.25: roll_b, 1.375: roll_a, 1.5: roll_b, 1.625: roll_a, 1.75: roll_b,
        2.0: rest,
    }


# ------------------------------------------------------------------ baile 5
# Video 5: rodillas arriba alternadas con saltito, puños al pecho bombeando a contratiempo
# y el torso girando hacia la rodilla que sube.
def baile5():
    knee_r = dict(rightleg=(75, 0, 0), rightshin=(-80, 0, 0), leftleg=(6, 0, 0), leftshin=(-12, 0, 0),
                  root={"pos": (0, 1.2, 0)}, torso=(-10, 15, 0), hi_head=(6, -10, 0),
                  rightarm=(-15, 0, 10), rightforearm=(65, 0, 0), leftarm=(45, 0, -10), leftforearm=(100, 0, 0))
    mid = dict(rightleg=(15, 0, 0), rightshin=(-28, 0, 0), leftleg=(15, 0, 0), leftshin=(-28, 0, 0),
               root={"pos": (0, -0.8, 0)}, torso=(-10, 0, 0), hi_head=(0, 0, 0),
               rightarm=(20, 0, 10), rightforearm=(90, 0, 0), leftarm=(20, 0, -10), leftforearm=(90, 0, 0))
    return 1.0, "catmullrom", {
        0.0: mid,
        0.25: knee_r,
        0.5: mid,
        0.75: mirror(knee_r),
        1.0: mid,
    }


DANCES = {"dance1": baile1, "dance2": baile2, "dance3": baile3, "dance4": baile4, "dance5": baile5}


def to_frames(timeline):
    """{t: pose} -> {hueso: [(canal, t, xyz)]} con el loop cerrado."""
    length, interpolation, poses = timeline
    frames = {}
    for t in sorted(poses):
        for bone, value in poses[t].items():
            channels = value if isinstance(value, dict) else {"rot": value}
            for key, xyz in channels.items():
                channel = "rotation" if key == "rot" else "position"
                frames.setdefault(bone, []).append((channel, t, tuple(float(c) for c in xyz)))
    return length, interpolation, frames


BAILES_DIR = __import__("pathlib").Path(__file__).resolve().parent / "bailes"


def load(name):
    """Si existe tools/bailes/<name>.json (extraido del video con extraer_pose.py + retarget.py)
    se usa ese; si no, la coreografia hecha a mano de este archivo."""
    import json
    path = BAILES_DIR / f"{name}.json"
    if path.exists():
        data = json.load(open(path, encoding="utf-8"))
        frames = {bone: [(channel, t, tuple(xyz)) for channel, t, xyz in keys]
                  for bone, keys in data["frames"].items()}
        return data["length"], "linear", frames
    return to_frames(DANCES[name]())
