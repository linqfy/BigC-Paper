"""Render con textura de un .bbmodel desde varios angulos (pose de reposo o un instante de una animacion).

Uso: python tools/render_modelo.py <modelo.bbmodel> <salida.png> [animacion tiempo]
"""
import math
import sys

from PIL import Image, ImageDraw

from render_animacion import Affine, load, mul, rot_matrix, sample

CORNERS = {
    "north": lambda x1, y1, z1, x2, y2, z2: [(x2, y2, z1), (x1, y2, z1), (x1, y1, z1), (x2, y1, z1)],
    "south": lambda x1, y1, z1, x2, y2, z2: [(x1, y2, z2), (x2, y2, z2), (x2, y1, z2), (x1, y1, z2)],
    "east": lambda x1, y1, z1, x2, y2, z2: [(x2, y2, z2), (x2, y2, z1), (x2, y1, z1), (x2, y1, z2)],
    "west": lambda x1, y1, z1, x2, y2, z2: [(x1, y2, z1), (x1, y2, z2), (x1, y1, z2), (x1, y1, z1)],
    "up": lambda x1, y1, z1, x2, y2, z2: [(x1, y2, z1), (x2, y2, z1), (x2, y2, z2), (x1, y2, z2)],
    "down": lambda x1, y1, z1, x2, y2, z2: [(x1, y1, z2), (x2, y1, z2), (x2, y1, z1), (x1, y1, z1)],
}


def render(path, yaw, pitch=22, size=420, anim_name=None, t=0.0, background=(32, 32, 36)):
    data, tex, groups, parent, element_group = load(path)
    channels = {}
    anim = next((a for a in data["animations"] if a["name"] == anim_name), None) if anim_name else None
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
        if uuid not in cache:
            pivot = groups[uuid]["origin"]
            rot = sample(channels[(uuid, "rotation")], t) if (uuid, "rotation") in channels else [0, 0, 0]
            # rotacion fija del hueso (pose de reposo) + la de la animacion
            rot = [a + b for a, b in zip(rot, groups[uuid].get("rotation", [0, 0, 0]))]
            pos = sample(channels[(uuid, "position")], t) if (uuid, "position") in channels else [0, 0, 0]
            local = Affine(t=[p + q for p, q in zip(pivot, pos)]).then(Affine(m=rot_matrix(*rot))).then(
                Affine(t=[-p for p in pivot]))
            cache[uuid] = world(parent[uuid]).then(local)
        return cache[uuid]

    view = Affine(m=mul(rot_matrix(pitch, 0, 0), rot_matrix(0, yaw, 0)))
    light = [0.35, 0.85, -0.4]
    faces = []
    lo = [1e9, 1e9]
    hi = [-1e9, -1e9]
    for el in data["elements"]:
        transform = view.then(world(element_group[el["uuid"]]))
        for face, corners in CORNERS.items():
            info = el["faces"].get(face)
            if not info:
                continue
            p = [transform.point(list(c)) for c in corners(*el["from"], *el["to"])]
            a = [p[1][i] - p[0][i] for i in range(3)]
            b = [p[3][i] - p[0][i] for i in range(3)]
            n = [a[1] * b[2] - a[2] * b[1], a[2] * b[0] - a[0] * b[2], a[0] * b[1] - a[1] * b[0]]
            length = math.sqrt(sum(c * c for c in n))
            if length == 0:
                continue
            n = [-c / length for c in n]  # normal hacia afuera
            if n[2] > 1e-6:  # mira para atras (la camara esta en -z mirando hacia +z)
                continue
            light_f = 0.62 + 0.38 * max(0.0, sum(n[i] * light[i] for i in range(3)))
            for q in p:
                lo = [min(lo[0], q[0]), min(lo[1], q[1])]
                hi = [max(hi[0], q[0]), max(hi[1], q[1])]
            faces.append((sum(q[2] for q in p) / 4, p, info["uv"], light_f))
    ss = 2
    img = Image.new("RGB", (size * ss, size * ss), background)
    draw = ImageDraw.Draw(img)
    span = max(hi[0] - lo[0], hi[1] - lo[1]) * 1.15
    scale = size * ss / span
    cx, cy = (lo[0] + hi[0]) / 2, (lo[1] + hi[1]) / 2

    def screen(q):
        return (size * ss / 2 - (q[0] - cx) * scale, size * ss / 2 - (q[1] - cy) * scale)

    quads = []
    for _, p, uv, light_f in faces:
        u1, v1, u2, v2 = (int(round(v)) for v in uv)
        w, h = max(1, u2 - u1), max(1, v2 - v1)
        tl, tr, bl = p[0], p[1], p[3]
        for j in range(h):
            for i in range(w):
                r, g, bb, alpha = tex.getpixel((min(u1 + i, tex.width - 1), min(v1 + j, tex.height - 1)))
                if alpha == 0:
                    continue

                def at(fu, fv):
                    return [tl[k] + (tr[k] - tl[k]) * fu + (bl[k] - tl[k]) * fv for k in range(3)]

                quad = [at(i / w, j / h), at((i + 1) / w, j / h), at((i + 1) / w, (j + 1) / h), at(i / w, (j + 1) / h)]
                color = tuple(int(c * light_f) for c in (r, g, bb))
                quads.append((sum(q[2] for q in quad) / 4, quad, color))
    for _, quad, color in sorted(quads, key=lambda item: -item[0]):
        draw.polygon([screen(q) for q in quad], fill=color, outline=color)
    return img.resize((size, size), Image.LANCZOS)


def main():
    path, out = sys.argv[1], sys.argv[2]
    anim = sys.argv[3] if len(sys.argv) > 3 else None
    t = float(sys.argv[4]) if len(sys.argv) > 4 else 0.0
    views = [(-35, 22), (-90, 8), (180, 8), (0, 8), (145, 30)]
    sheet = Image.new("RGB", (420 * len(views), 420), (32, 32, 36))
    for i, (yaw, pitch) in enumerate(views):
        sheet.paste(render(path, yaw, pitch, anim_name=anim, t=t), (420 * i, 0))
    sheet.save(out)
    print(out)


if __name__ == "__main__":
    main()
