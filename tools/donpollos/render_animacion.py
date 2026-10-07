"""Renderiza poses de una animacion de un .bbmodel (vista 3/4, colores planos) para revisarla.

Uso: python tools/render_animacion.py <modelo.bbmodel> <animacion> <cuadros> <salida.png> [yaw]
"""
import base64
import io
import json
import math
import sys

from PIL import Image, ImageDraw


def rot_matrix(rx, ry, rz):
    rx, ry, rz = (math.radians(a) for a in (rx, ry, rz))
    cx, sx, cy, sy, cz, sz = math.cos(rx), math.sin(rx), math.cos(ry), math.sin(ry), math.cos(rz), math.sin(rz)
    mx = [[1, 0, 0], [0, cx, -sx], [0, sx, cx]]
    my = [[cy, 0, sy], [0, 1, 0], [-sy, 0, cy]]
    mz = [[cz, -sz, 0], [sz, cz, 0], [0, 0, 1]]
    return mul(mz, mul(my, mx))


def mul(a, b):
    return [[sum(a[i][k] * b[k][j] for k in range(3)) for j in range(3)] for i in range(3)]


def apply(m, v):
    return [sum(m[i][k] * v[k] for k in range(3)) for i in range(3)]


class Affine:
    def __init__(self, m=None, t=None):
        self.m = m or [[1, 0, 0], [0, 1, 0], [0, 0, 1]]
        self.t = t or [0, 0, 0]

    def then(self, other):
        """self * other (other se aplica primero)."""
        return Affine(mul(self.m, other.m), [a + b for a, b in zip(apply(self.m, other.t), self.t)])

    def point(self, v):
        return [a + b for a, b in zip(apply(self.m, v), self.t)]


def sample(keys, t):
    keys = sorted(keys, key=lambda k: k[0])
    if t <= keys[0][0]:
        return keys[0][1]
    for (t0, v0), (t1, v1) in zip(keys, keys[1:]):
        if t0 <= t <= t1:
            f = 0 if t1 == t0 else (t - t0) / (t1 - t0)
            f = f * f * (3 - 2 * f)
            return [a + (b - a) * f for a, b in zip(v0, v1)]
    return keys[-1][1]


def load(path):
    data = json.load(open(path, encoding="utf-8"))
    src = data["textures"][0]["source"].split(",", 1)[1]
    tex = Image.open(io.BytesIO(base64.b64decode(src))).convert("RGBA")
    groups = {g["uuid"]: g for g in data["groups"]}
    parent, element_group = {}, {}

    def walk(node, parent_uuid):
        parent[node["uuid"]] = parent_uuid
        for child in node["children"]:
            if isinstance(child, str):
                element_group[child] = node["uuid"]
            else:
                walk(child, node["uuid"])
    for root in data["outliner"]:
        walk(root, None)
    return data, tex, groups, parent, element_group


def face_color(tex, uv):
    x1, y1, x2, y2 = (int(v) for v in uv)
    if x2 <= x1 or y2 <= y1:
        return None
    pixels = [p for p in tex.crop((x1, y1, x2, y2)).getdata() if p[3] > 0]
    if not pixels:
        return None
    return tuple(sum(p[i] for p in pixels) // len(pixels) for i in range(3))


def render(path, anim_name, t, yaw=35, size=240, view_height=56):
    data, tex, groups, parent, element_group = load(path)
    anim = next((a for a in data["animations"] if a["name"] == anim_name), None)
    channels = {}
    if anim:
        for uuid, animator in anim.get("animators", {}).items():
            for kf in animator["keyframes"]:
                dp = kf["data_points"][0]
                channels.setdefault((uuid, kf["channel"]), []).append(
                    (kf["time"], [float(dp["x"]), float(dp["y"]), float(dp["z"])]))
    cache = {}

    def world(uuid):
        if uuid is None:
            return Affine()
        if uuid in cache:
            return cache[uuid]
        g = groups[uuid]
        pivot = g["origin"]
        rot = sample(channels[(uuid, "rotation")], t) if (uuid, "rotation") in channels else [0, 0, 0]
        pos = sample(channels[(uuid, "position")], t) if (uuid, "position") in channels else [0, 0, 0]
        local = Affine(t=[p + q for p, q in zip(pivot, pos)]).then(Affine(m=rot_matrix(*rot))).then(
            Affine(t=[-p for p in pivot]))
        cache[uuid] = world(parent[uuid]).then(local)
        return cache[uuid]

    view = Affine(m=mul(rot_matrix(4, 0, 0), rot_matrix(0, yaw, 0)))
    light = [0.4, 0.8, -0.45]
    polys = []
    for el in data["elements"]:
        (x1, y1, z1), (x2, y2, z2) = el["from"], el["to"]
        transform = view.then(world(element_group[el["uuid"]]))
        corners = {
            "north": [(x2, y2, z1), (x1, y2, z1), (x1, y1, z1), (x2, y1, z1)],
            "south": [(x1, y2, z2), (x2, y2, z2), (x2, y1, z2), (x1, y1, z2)],
            "east": [(x2, y2, z2), (x2, y2, z1), (x2, y1, z1), (x2, y1, z2)],
            "west": [(x1, y2, z1), (x1, y2, z2), (x1, y1, z2), (x1, y1, z1)],
            "up": [(x1, y2, z1), (x2, y2, z1), (x2, y2, z2), (x1, y2, z2)],
            "down": [(x1, y1, z2), (x2, y1, z2), (x2, y1, z1), (x1, y1, z1)],
        }
        for face, pts in corners.items():
            info = el["faces"].get(face)
            if not info or info.get("texture") is None:
                continue
            color = face_color(tex, info["uv"])
            if color is None:
                continue
            p = [transform.point(list(c)) for c in pts]
            a = [p[1][i] - p[0][i] for i in range(3)]
            b = [p[2][i] - p[0][i] for i in range(3)]
            n = [a[1] * b[2] - a[2] * b[1], a[2] * b[0] - a[0] * b[2], a[0] * b[1] - a[1] * b[0]]
            length = math.sqrt(sum(c * c for c in n)) or 1
            n = [c / length for c in n]
            shade_f = 0.55 + 0.45 * max(0.0, abs(sum(n[i] * light[i] for i in range(3))))
            depth = sum(q[2] for q in p) / 4
            polys.append((depth, [(q[0], q[1]) for q in p], tuple(int(c * shade_f) for c in color)))
    img = Image.new("RGB", (size, size), (205, 212, 225))
    draw = ImageDraw.Draw(img)
    scale = size / view_height
    for depth, pts, color in sorted(polys, key=lambda item: -item[0]):
        draw.polygon([(size / 2 - x * scale, size * 0.86 - y * scale) for x, y in pts], fill=color, outline=(0, 0, 0))
    return img


def main():
    path, anim, frames, out = sys.argv[1], sys.argv[2], int(sys.argv[3]), sys.argv[4]
    yaw = float(sys.argv[5]) if len(sys.argv) > 5 else 35
    data = json.load(open(path, encoding="utf-8"))
    anim_data = next(a for a in data["animations"] if a["name"] == anim)
    length = anim_data["length"]
    tiles = [render(path, anim, length * i / frames, yaw) for i in range(frames)]
    sheet = Image.new("RGB", (240 * len(tiles), 254), (40, 40, 40))
    draw = ImageDraw.Draw(sheet)
    for i, tile in enumerate(tiles):
        sheet.paste(tile, (240 * i, 14))
        draw.text((240 * i + 4, 1), f"{anim} t={length * i / frames:.2f}s", fill=(255, 255, 0))
    sheet.save(out)
    print(out)


if __name__ == "__main__":
    main()
