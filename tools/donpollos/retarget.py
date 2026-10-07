"""Convierte la pose 3D de MediaPipe (extraer_pose.py) en una animacion para el esqueleto de Don Pollo.

Uso: python tools/retarget.py <pose.json> <salida.json> [inicio_s] [duracion_s] [fps_claves]

Convencion de salida (Blockbench 5): rotacion en grados, orden Z*Y*X, X+ lleva el miembro
hacia adelante; posicion en pixeles. Salida: {"length": s, "frames": {hueso: [[canal, t, [x,y,z]], ...]}}

Pasos:
  1. Pose por cuadro -> rotacion local de cada hueso.
  2. Limites articulares reales: cada brazo/pierna se separa en "hacia donde apunta" (swing) y
     "cuanto gira sobre su propio eje" (twist); el twist se limita y la direccion tambien
     (el brazo no se mete adentro del cuerpo ni se va muy atras, el muslo no cruza, etc.).
  3. Donde el detector no ve bien un miembro (visibilidad baja) se interpola en vez de inventar.
  4. Un poco mas de amplitud y un "groove" al ritmo de la musica del video (BPM sacado del audio):
     rebote del cuerpo, cabeceo y bombeo del torso en cada golpe.
"""
import json
import math
import subprocess
import sys

import numpy as np

# Indices de MediaPipe (izquierda/derecha del bailarin)
NOSE, L_EAR, R_EAR = 0, 7, 8
L_SH, R_SH, L_EL, R_EL, L_WR, R_WR = 11, 12, 13, 14, 15, 16
L_HIP, R_HIP, L_KN, R_KN, L_AN, R_AN = 23, 24, 25, 26, 27, 28

LEG_PIXELS = 12.0  # cadera (y=12) al piso
BIAS_FREE = {"torso": [0], "hi_head": [0, 1, 2], "hip": [0, 2]}
MIN_VISIBILITY = 0.4

# Cuantos grados puede girar cada miembro sobre su propio eje.
TWIST_LIMIT = {"rightarm": 70, "leftarm": 70, "rightleg": 35, "leftleg": 35}
# Limites de direccion del miembro (en el sistema del padre; +x derecha, -z adelante, -y abajo).
DIRECTION_LIMITS = {
    "rightarm": dict(min_x=-0.45, max_z=0.55),
    "leftarm": dict(max_x=0.45, max_z=0.55),
    "rightleg": dict(min_x=-0.3, max_z=0.55, max_y=0.2),
    "leftleg": dict(max_x=0.3, max_z=0.55, max_y=0.2),
}
# Limites en grados (x, y, z) despues de todo; forearm/shin solo doblan en X.
EULER_LIMITS = {
    "hip": [(-15, 15), (-720, 720), (-15, 15)],
    "torso": [(-30, 20), (-60, 60), (-20, 20)],
    "hi_head": [(-30, 30), (-40, 40), (-25, 25)],
    "rightforearm": [(0, 145), (0, 0), (0, 0)],
    "leftforearm": [(0, 145), (0, 0), (0, 0)],
    "rightshin": [(-145, 0), (0, 0), (0, 0)],
    "leftshin": [(-145, 0), (0, 0), (0, 0)],
}
# Un poco mas de amplitud (multiplica la variacion alrededor del promedio del tramo).
AMPLIFY = {"torso": 1.3, "hip": 1.15, "hi_head": 1.2, "rightarm": 1.2, "leftarm": 1.2,
           "rightforearm": 1.15, "leftforearm": 1.15, "rightleg": 1.15, "leftleg": 1.15,
           "rightshin": 1.15, "leftshin": 1.15}
# Que puntos tienen que verse para confiar en cada hueso.
BONE_POINTS = {
    "rightarm": [R_SH, R_EL, R_WR], "rightforearm": [R_EL, R_WR],
    "leftarm": [L_SH, L_EL, L_WR], "leftforearm": [L_EL, L_WR],
    "rightleg": [R_HIP, R_KN, R_AN], "rightshin": [R_KN, R_AN],
    "leftleg": [L_HIP, L_KN, L_AN], "leftshin": [L_KN, L_AN],
}


# ------------------------------------------------------------------ geometria

def to_model(points):
    """MediaPipe (x imagen-derecha, y abajo, z hacia la camara negativo) -> modelo (x derecha del
    personaje, y arriba, z atras). El bailarin mira a la camara, que esta delante (-z) del modelo."""
    p = np.array(points)[:, :3]
    return np.stack([-p[:, 0], -p[:, 1], p[:, 2]], axis=1)


def normalize(v):
    n = np.linalg.norm(v)
    return v / n if n > 1e-9 else v


def frame_from(x_axis, y_hint):
    x = normalize(x_axis)
    y = normalize(y_hint - np.dot(y_hint, x) * x)
    z = np.cross(x, y)
    return np.stack([x, y, z], axis=1)


def euler_zyx(m):
    """R = Rz * Ry * Rx -> (rx, ry, rz) en grados."""
    sy = max(-1.0, min(1.0, -m[2, 0]))
    ry = math.asin(sy)
    if abs(sy) < 0.9999:
        rx = math.atan2(m[2, 1], m[2, 2])
        rz = math.atan2(m[1, 0], m[0, 0])
    else:
        rx = math.atan2(-m[1, 2], m[1, 1])
        rz = 0.0
    return np.degrees([rx, ry, rz])


def quat_from_matrix(m):
    w = math.sqrt(max(0.0, 1 + m[0, 0] + m[1, 1] + m[2, 2])) / 2
    x = math.sqrt(max(0.0, 1 + m[0, 0] - m[1, 1] - m[2, 2])) / 2
    y = math.sqrt(max(0.0, 1 - m[0, 0] + m[1, 1] - m[2, 2])) / 2
    z = math.sqrt(max(0.0, 1 - m[0, 0] - m[1, 1] + m[2, 2])) / 2
    x = math.copysign(x, m[2, 1] - m[1, 2])
    y = math.copysign(y, m[0, 2] - m[2, 0])
    z = math.copysign(z, m[1, 0] - m[0, 1])
    return np.array([w, x, y, z])


def matrix_from_quat(q):
    w, x, y, z = q / np.linalg.norm(q)
    return np.array([
        [1 - 2 * (y * y + z * z), 2 * (x * y - z * w), 2 * (x * z + y * w)],
        [2 * (x * y + z * w), 1 - 2 * (x * x + z * z), 2 * (y * z - x * w)],
        [2 * (x * z - y * w), 2 * (y * z + x * w), 1 - 2 * (x * x + y * y)],
    ])


def rotation_between(a, b):
    """Rotacion minima que lleva el vector a al vector b."""
    a, b = normalize(a), normalize(b)
    v = np.cross(a, b)
    c = float(np.dot(a, b))
    if c < -0.9999:
        return matrix_from_quat(np.array([0.0, 1.0, 0.0, 0.0]))
    vx = np.array([[0, -v[2], v[1]], [v[2], 0, -v[0]], [-v[1], v[0], 0]])
    return np.eye(3) + vx + vx @ vx / (1 + c)


def constrain_limb(local, bone):
    """Limita el giro sobre el propio eje (twist) y la direccion (swing) de un miembro."""
    rest = np.array([0.0, -1.0, 0.0])
    d = local @ rest
    swing = rotation_between(rest, d)
    twist_m = swing.T @ local                       # rotacion pura alrededor del eje Y local
    twist = math.degrees(math.atan2(twist_m[0, 2], twist_m[0, 0]))
    limit = TWIST_LIMIT[bone]
    twist = max(-limit, min(limit, twist))
    t = math.radians(twist)
    twist_m = np.array([[math.cos(t), 0, math.sin(t)], [0, 1, 0], [-math.sin(t), 0, math.cos(t)]])

    lim = DIRECTION_LIMITS[bone]
    d = d.copy()
    if "min_x" in lim:
        d[0] = max(d[0], lim["min_x"])
    if "max_x" in lim:
        d[0] = min(d[0], lim["max_x"])
    if "max_z" in lim:
        d[2] = min(d[2], lim["max_z"])
    if "max_y" in lim:
        d[1] = min(d[1], lim["max_y"])
    swing = rotation_between(rest, normalize(d))
    return swing @ twist_m


def quat_mul(a, b):
    w1, x1, y1, z1 = a
    w2, x2, y2, z2 = b
    return np.array([w1 * w2 - x1 * x2 - y1 * y2 - z1 * z2, w1 * x2 + x1 * w2 + y1 * z2 - z1 * y2,
                     w1 * y2 - x1 * z2 + y1 * w2 + z1 * x2, w1 * z2 + x1 * y2 - y1 * x2 + z1 * w2])


def quat_conj(q):
    return np.array([q[0], -q[1], -q[2], -q[3]])


def quat_scale(q, factor):
    """Escala el angulo de una rotacion (factor > 1 la exagera)."""
    q = q if q[0] >= 0 else -q
    angle = 2 * math.acos(max(-1.0, min(1.0, q[0])))
    axis = q[1:] / (np.linalg.norm(q[1:]) or 1)
    angle *= factor
    return np.concatenate([[math.cos(angle / 2)], axis * math.sin(angle / 2)])


def continuous_quats(quats):
    out = [quats[0]]
    for q in quats[1:]:
        out.append(-q if np.dot(q, out[-1]) < 0 else q)
    return np.array(out)


def nlerp_series(times, quats, at):
    out = np.stack([np.interp(at, times, quats[:, i]) for i in range(4)], axis=1)
    return out / np.linalg.norm(out, axis=1, keepdims=True)


def closest_euler(m, previous):
    """De los angulos equivalentes de la rotacion, el mas cercano al cuadro anterior: asi la
    interpolacion del juego nunca da una vuelta de mas."""
    e = euler_zyx(m)
    candidates = [e, np.array([e[0] + 180, 180 - e[1], e[2] + 180])]
    best, best_dist = None, None
    for c in candidates:
        if previous is not None:
            c = c + 360 * np.round((previous - c) / 360)
        dist = 0 if previous is None else float(np.sum((c - previous) ** 2))
        if best is None or dist < best_dist:
            best, best_dist = c, dist
    return best


REST = np.array([0.0, -1.0, 0.0])
TWIST_RATE = 30.0  # grados maximos de giro sobre el eje entre dos claves
MAX_SPEED = {"rightarm": 45.0, "leftarm": 45.0, "rightleg": 40.0, "leftleg": 40.0}  # grados por clave


PIVOTS = {"rightarm": np.array([1.0, 0, 0]), "leftarm": np.array([-1.0, 0, 0])}


def swing_for(d, bone):
    """Rotacion que lleva el reposo (colgando) a la direccion d. Para los brazos pasa por la
    horizontal hacia afuera, asi el unico punto singular queda cruzando el pecho (prohibido por
    los limites) y levantar el brazo arriba no da vueltas."""
    pivot = PIVOTS.get(bone)
    if pivot is None:
        return rotation_between(REST, d)
    return rotation_between(pivot, d) @ rotation_between(REST, pivot)


def limb_signal(bone_vec, child_vec, bend_sign, bone):
    """Direccion del miembro (en el sistema del padre) y el giro sobre su eje que hace que el
    codo/rodilla doble hacia su lado natural. El giro es NaN si el miembro esta casi recto
    (no se puede medir) y se completa por interpolacion."""
    d = normalize(bone_vec)
    bend = child_vec - np.dot(child_vec, d) * d
    if np.linalg.norm(bend) < 0.25 * np.linalg.norm(child_vec):
        return ("limb", d, float("nan"))
    desired = normalize(-bend_sign * bend)
    swing = swing_for(d, bone)
    sx, sz = swing[:, 0], swing[:, 2]
    tau = math.degrees(math.atan2(float(np.dot(desired, sx)), float(np.dot(desired, sz))))
    return ("limb", d, tau)


def _pin(d, axis, value):
    """Fija una componente del vector unitario y reparte el resto en las otras dos."""
    d = d.copy()
    others = [i for i in range(3) if i != axis]
    rest = math.sqrt(max(0.0, 1 - value * value))
    norm = math.hypot(d[others[0]], d[others[1]])
    if norm < 1e-9:
        d[others[0]], d[others[1]] = rest, 0.0
    else:
        d[others[0]] *= rest / norm
        d[others[1]] *= rest / norm
    d[axis] = value
    return d


def limit_direction(d, bone):
    lim = DIRECTION_LIMITS[bone]
    d = normalize(d)
    for _ in range(4):
        changed = False
        if "min_x" in lim and d[0] < lim["min_x"]:
            d, changed = _pin(d, 0, lim["min_x"]), True
        if "max_x" in lim and d[0] > lim["max_x"]:
            d, changed = _pin(d, 0, lim["max_x"]), True
        if "max_z" in lim and d[2] > lim["max_z"]:
            d, changed = _pin(d, 2, lim["max_z"]), True
        if "max_y" in lim and d[1] > lim["max_y"]:
            d, changed = _pin(d, 1, lim["max_y"]), True
        if not changed:
            break
    return d


def rotate_towards(a, b, max_deg):
    """Gira a hacia b como mucho max_deg grados."""
    angle = angle_between(a, b)
    if angle <= max_deg or angle < 1e-6:
        return b
    axis = np.cross(a, b)
    if np.linalg.norm(axis) < 1e-9:
        return b
    r = rotation_between(a, b)
    q = quat_scale(quat_from_matrix(r), max_deg / angle)
    return normalize(matrix_from_quat(q) @ a)


def limb_series(signals, times, ok, key_times, amp, bone):
    """Serie de rotaciones de un miembro: direccion suavizada/limitada + giro limitado y continuo."""
    dirs = np.array([s[1] for s in signals])
    taus = np.array([s[2] for s in signals])
    # direccion: interpolar huecos, suavizar, exagerar un poco y llevar a las claves
    dirs = np.stack([np.interp(times, times[ok], dirs[ok, i]) for i in range(3)], axis=1)
    dirs = smooth(dirs, 3)
    dirs = np.stack([np.interp(key_times, times, dirs[:, i]) for i in range(3)], axis=1)
    dirs /= np.linalg.norm(dirs, axis=1, keepdims=True)
    mean = normalize(dirs.mean(axis=0))
    out_dirs = []
    for d in dirs:
        angle = angle_between(mean, d)
        target = d
        if angle > 1e-3:
            target = rotate_towards(mean, d, angle * amp) if amp <= 1 else _extend(mean, d, amp)
        target = limit_direction(target, bone)
        if out_dirs:
            target = limit_direction(rotate_towards(out_dirs[-1], target, MAX_SPEED[bone]), bone)
        out_dirs.append(target)
    # giro: solo donde se puede medir, desenrollado, limitado y sin saltos
    valid = ok & ~np.isnan(taus)
    if valid.sum() >= 2:
        unwrapped = np.degrees(np.unwrap(np.radians(taus[valid])))
        taus = np.interp(times, times[valid], unwrapped)
    else:
        taus = np.zeros(len(times))
    limit = TWIST_LIMIT[bone]
    taus = np.clip(np.interp(key_times, times, smooth(taus[:, None], 5)[:, 0]), -limit, limit)
    for i in range(1, len(taus)):
        taus[i] = taus[i - 1] + max(-TWIST_RATE, min(TWIST_RATE, taus[i] - taus[i - 1]))
    eulers, previous = [], None
    for d, tau in zip(out_dirs, taus):
        t = math.radians(tau)
        twist = np.array([[math.cos(t), 0, math.sin(t)], [0, 1, 0], [-math.sin(t), 0, math.cos(t)]])
        previous = closest_euler(swing_for(d, bone) @ twist, previous)
        eulers.append(previous)
    return np.array(eulers)


def _extend(mean, d, amp):
    """Lleva d mas lejos del promedio (amp > 1), sobre el mismo arco."""
    angle = angle_between(mean, d)
    r = rotation_between(mean, d)
    q = quat_scale(quat_from_matrix(r), amp)
    return normalize(matrix_from_quat(q) @ mean)


def limb_frame(d, child_dir, bend_sign, previous):
    """Base de un miembro que apunta en d. El hijo se dobla en X: X+ lo lleva a -Z del miembro.
    bend_sign = +1 para codos (adelante), -1 para rodillas (atras)."""
    y = normalize(-d)
    bend = child_dir - np.dot(child_dir, d) * d
    if np.linalg.norm(bend) > 0.15 * np.linalg.norm(child_dir):
        z = normalize(-bend_sign * bend)
        z = normalize(z - np.dot(z, y) * y)
        x = np.cross(y, z)
    else:
        x_hint = previous[:, 0] if previous is not None else np.array([1.0, 0, 0])
        x = normalize(x_hint - np.dot(x_hint, y) * y)
        z = np.cross(x, y)
    return np.stack([x, y, z], axis=1)


def angle_between(a, b):
    return math.degrees(math.acos(max(-1.0, min(1.0, float(np.dot(normalize(a), normalize(b)))))))


def solve(points, prev):
    P = to_model(points)
    hip_mid = (P[L_HIP] + P[R_HIP]) / 2
    sh_mid = (P[L_SH] + P[R_SH]) / 2
    up = np.array([0.0, 1.0, 0.0])

    hip_g = frame_from(P[R_HIP] - P[L_HIP], up)
    torso_g = frame_from(P[R_SH] - P[L_SH], sh_mid - hip_mid)
    ear_mid = (P[L_EAR] + P[R_EAR]) / 2
    head_back = normalize(ear_mid - P[NOSE])
    head_x = normalize(P[R_EAR] - P[L_EAR])
    head_g = frame_from(head_x, np.cross(head_back, head_x))

    out, frames = {}, {}
    out["hip"] = hip_g
    out["torso"] = hip_g.T @ torso_g
    out["hi_head"] = torso_g.T @ head_g

    for side, sh, el, wr in (("right", R_SH, R_EL, R_WR), ("left", L_SH, L_EL, L_WR)):
        upper, lower = P[el] - P[sh], P[wr] - P[el]
        out[side + "arm"] = limb_signal(torso_g.T @ upper, torso_g.T @ lower, +1, side + "arm")
        out[side + "forearm"] = np.array([angle_between(upper, lower), 0, 0])

    for side, hp, kn, an in (("right", R_HIP, R_KN, R_AN), ("left", L_HIP, L_KN, L_AN)):
        thigh, shin = P[kn] - P[hp], P[an] - P[kn]
        out[side + "leg"] = limb_signal(hip_g.T @ thigh, hip_g.T @ shin, -1, side + "leg")
        out[side + "shin"] = np.array([-angle_between(thigh, shin), 0, 0])

    lowest_foot = min(P[L_AN][1], P[R_AN][1]) - hip_mid[1]
    leg_len = (np.linalg.norm(P[L_KN] - P[L_HIP]) + np.linalg.norm(P[L_AN] - P[L_KN])
               + np.linalg.norm(P[R_KN] - P[R_HIP]) + np.linalg.norm(P[R_AN] - P[R_KN])) / 2
    return out, frames, lowest_foot, leg_len


def smooth(series, window):
    if window <= 1:
        return series
    kernel = np.ones(window) / window
    padded = np.pad(series, ((window // 2, window - 1 - window // 2), (0, 0)), mode="edge")
    return np.stack([np.convolve(padded[:, i], kernel, mode="valid") for i in range(series.shape[1])], axis=1)


# ------------------------------------------------------------------ ritmo

def estimate_beat(video, start, length):
    """BPM y fase del primer golpe a partir del audio del video (energia + autocorrelacion)."""
    rate = 8000
    try:
        raw = subprocess.run(["ffmpeg", "-v", "error", "-ss", str(start), "-t", str(length), "-i", video,
                              "-ac", "1", "-ar", str(rate), "-f", "s16le", "-"],
                             capture_output=True, check=True).stdout
    except (OSError, subprocess.CalledProcessError):
        return None
    audio = np.frombuffer(raw, dtype=np.int16).astype(float)
    if len(audio) < rate * 2 or np.abs(audio).max() < 100:
        return None
    hop = 80  # 10 ms
    energy = np.array([np.sum(audio[i:i + 256] ** 2) for i in range(0, len(audio) - 256, hop)])
    onset = np.maximum(0, np.diff(np.log1p(energy)))
    onset -= onset.mean()
    best_bpm, best_score = None, -1e18
    for bpm in np.arange(70, 170, 0.5):
        lag = 60 / bpm / (hop / rate)
        i = int(round(lag))
        score = float(np.dot(onset[:-i], onset[i:])) + 0.5 * float(np.dot(onset[:-2 * i], onset[2 * i:]))
        if score > best_score:
            best_bpm, best_score = bpm, score
    period = 60 / best_bpm
    phases = np.linspace(0, period, 40, endpoint=False)
    times = np.arange(len(onset)) * hop / rate
    phase = max(phases, key=lambda p: float(np.sum(onset * np.exp(-((((times - p) % period) / 0.04) ** 2)))))
    return float(best_bpm), float(phase)


def groove(times, length, beat):
    """Pulso 0..1 que pica en cada golpe, con un numero entero de golpes por loop."""
    if beat is None:
        bpm, phase = 110.0, 0.0
    else:
        bpm, phase = beat
    beats = max(1, round(length * bpm / 60))
    period = length / beats
    x = 2 * math.pi * (times - phase) / period
    return (0.5 + 0.5 * np.cos(x)) ** 3, beats * 60 / length


# ------------------------------------------------------------------ principal

def main():
    data = json.load(open(sys.argv[1], encoding="utf-8"))
    out_path = sys.argv[2]
    start = float(sys.argv[3]) if len(sys.argv) > 3 else 0.0
    duration = float(sys.argv[4]) if len(sys.argv) > 4 else None
    key_fps = float(sys.argv[5]) if len(sys.argv) > 5 else 12.0

    frames = [f for f in data["frames"] if f["world"] and f["t"] >= start
              and (duration is None or f["t"] <= start + duration)]
    times = np.array([f["t"] - start for f in frames])
    pts = np.array([[p[:3] for p in f["world"]] for f in frames])
    vis = np.array([[p[3] for p in f["world"]] for f in frames])

    prev, solved, feet, legs = {}, [], [], []
    for p in pts:
        values, prev, foot, leg = solve(p, prev)
        solved.append(values)
        feet.append(foot)
        legs.append(leg)
    length = float(duration if duration is not None else times[-1])
    scale = LEG_PIXELS / float(np.median(legs))
    rest_foot = float(np.percentile(feet, 10))

    key_times = np.round(np.arange(0, length + 1e-6, 1 / key_fps), 4)
    if key_times[-1] < length - 1e-6:
        key_times = np.append(key_times, round(length, 4))
    pulse, bpm = groove(key_times, length, estimate_beat(data["video"], data["start"] + start, length))

    result, poor = {}, {}
    for bone in solved[0]:
        ok = np.ones(len(solved), dtype=bool)
        if bone in BONE_POINTS:
            ok = vis[:, BONE_POINTS[bone]].min(axis=1) >= MIN_VISIBILITY
            if ok.mean() < 0.5:
                # El detector casi nunca esta seguro (p. ej. personajes de Minecraft): se usa igual.
                ok[:] = True
        poor[bone] = 1 - ok.mean()
        amp = AMPLIFY.get(bone, 1.0)
        if isinstance(solved[0][bone], tuple):
            result[bone] = limb_series([s[bone] for s in solved], times, ok, key_times, amp, bone)
            continue
        if np.ndim(solved[0][bone]) == 1:
            # codo / rodilla: un solo angulo
            series = np.array([s[bone] for s in solved])
            series = np.stack([np.interp(times, times[ok], series[ok, i]) for i in range(3)], axis=1)
            series = smooth(series, 3)
            mean = np.median(series, axis=0)
            series = mean + (series - mean) * amp
            result[bone] = np.stack([np.interp(key_times, times, series[:, i]) for i in range(3)], axis=1)
            continue
        quats = continuous_quats(np.array([quat_from_matrix(s[bone]) for s in solved]))
        quats = nlerp_series(times[ok], quats[ok], times)
        quats = smooth(quats, 3)
        quats /= np.linalg.norm(quats, axis=1, keepdims=True)
        if bone == "hi_head":
            quats = np.array([quat_scale(q, 0.7) for q in quats])
        sampled_q = nlerp_series(times, quats, key_times)
        mean_q = sampled_q.mean(axis=0)
        mean_q /= np.linalg.norm(mean_q)
        eulers, previous = [], None
        for q in sampled_q:
            rel = quat_scale(quat_mul(quat_conj(mean_q), q), amp)
            m = matrix_from_quat(quat_mul(mean_q, rel))
            previous = closest_euler(m, previous)
            eulers.append(previous)
        series = np.array(eulers)
        if bone in BIAS_FREE:
            # MediaPipe exagera la inclinacion hacia adelante: se quita el promedio del tramo.
            axes = BIAS_FREE[bone]
            series[:, axes] -= np.median(series[:, axes], axis=0)
        result[bone] = series

    # Groove al ritmo: el cuerpo baja, la cabeza cabecea y el torso bombea en cada golpe.
    # Con GROOVE > 1 (videos que el detector no ve bien) se le pone mas onda a mano, y TILT < 1
    # achica las inclinaciones dudosas de cadera y torso.
    g = float(__import__("os").environ.get("GROOVE", "1"))
    tilt = float(__import__("os").environ.get("TILT", "1"))
    for bone in ("hip", "torso"):
        for axis in (0, 2):
            mid = np.median(result[bone][:, axis])
            result[bone][:, axis] = mid + (result[bone][:, axis] - mid) * tilt
    sway = np.sin(np.pi * key_times * bpm / 60)  # un vaiven cada dos golpes
    result["hi_head"][:, 0] -= 7 * g * pulse
    result["hi_head"][:, 2] += 4 * (g - 1) * sway
    result["torso"][:, 0] -= 4 * g * pulse
    result["torso"][:, 1] += 8 * (g - 1) * sway
    result["hip"][:, 2] += 3 * (g - 1) * sway
    for side, sign in (("right", 1), ("left", -1)):
        result[side + "forearm"][:, 0] += 8 * g * pulse
        result[side + "arm"][:, 0] += 6 * (g - 1) * pulse
        result[side + "arm"][:, 2] += sign * 5 * (g - 1) * pulse
        result[side + "leg"][:, 0] += 6 * g * pulse
        result[side + "shin"][:, 0] -= 12 * g * pulse

    for bone, limits in EULER_LIMITS.items():
        for axis, (lo, hi) in enumerate(limits):
            result[bone][:, axis] = np.clip(result[bone][:, axis], lo, hi)

    root_y = np.clip((rest_foot - np.array(feet)) * scale, -6, 6)
    root_y = np.interp(key_times, times, smooth(root_y[:, None], 3)[:, 0]) - 0.9 * g * pulse

    # Cierre del loop: el ultimo medio segundo se funde con el primer cuadro.
    blend = max(1, int(0.5 * key_fps))
    for bone, sampled in result.items():
        first = sampled[0].copy()
        last_target = first + 360 * np.round((sampled[-1] - first) / 360)
        for i in range(blend):
            k = len(sampled) - blend + i
            w = (i + 1) / blend
            sampled[k] = sampled[k] * (1 - w) + last_target * w
    root_y[-blend:] = root_y[-blend:] * np.linspace(1, 0, blend) + root_y[0] * np.linspace(0, 1, blend)

    frames_out = {bone: [["rotation", float(t), [round(float(v), 2) for v in values]]
                         for t, values in zip(key_times, sampled)] for bone, sampled in result.items()}
    frames_out["root"] = [["position", float(t), [0.0, round(float(y), 2), 0.0]] for t, y in zip(key_times, root_y)]
    json.dump({"length": round(length, 4), "bpm": round(bpm, 1), "frames": frames_out},
              open(out_path, "w", encoding="utf-8"))
    worst = max(poor, key=poor.get)
    print(f"{out_path}: {length:.1f}s, {len(key_times)} claves por hueso, ritmo {bpm:.0f} BPM, "
          f"peor deteccion {worst} ({poor[worst]:.0%} interpolado)")


if __name__ == "__main__":
    main()
