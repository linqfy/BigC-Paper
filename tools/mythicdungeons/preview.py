#!/usr/bin/env python3
"""Software preview of any ``.bbmodel`` (cubes and meshes), for judging models by eye.

Renders the bone hierarchy (optionally posed by a named animation at time t) with a
z-buffer, nearest-neighbour texture sampling, cut-out alpha (alpha < 0.1 is discarded, like
Minecraft) and Minecraft's fixed per-axis face shading (top 1.0, north/south 0.8,
east/west 0.6, bottom 0.5).  Orthographic camera, ground grid in 1-block squares, height
label in blocks.  Animation sampling follows Blockbench/BetterModel: rotation keys are
applied as (-x, -y, +z) and position keys as (-x, +y, +z) on top of the bone's static
rotation; linear, step and catmullrom interpolation; loop/once/hold timing.

Usage
-----
    python tools/mythicdungeons/preview.py <file.bbmodel> --out work/previews/<name>.png
        [--views front,three_quarter,side,back]   views: front three_quarter side left back top
        [--anim walk [--frames 6]]                  rows = views, columns = frames over the clip
        [--anim attack --t 0.4]                     one pose of a clip in every view
        [--size 320] [--atlas]                      panel size in px; append the texture atlas

Output is refused outside the repository's git-ignored ``work/`` folder.

From Python (used by build.py --preview):
    from preview import render_sheet
    render_sheet("model.bbmodel", "work/previews/x.png", views=("front", "side"), anim="walk", frames=6)
"""
from __future__ import annotations

import argparse
import base64
import io
import json
import math
import sys

sys.dont_write_bytecode = True  # keep __pycache__ out of the source tree
from pathlib import Path

import numpy as np
from PIL import Image, ImageDraw, ImageFont

sys.path.insert(0, str(Path(__file__).resolve().parent))
from bbkit import affine, outliner_groups, rotation_matrix  # noqa: E402

REPO = Path(__file__).resolve().parents[2]
WORK = REPO / "work"
VIEWS = {  # name: (yaw, pitch) degrees; yaw 0 looks at the face (camera on the -Z side)
    "front": (0, 8), "three_quarter": (-38, 22), "side": (-90, 6), "left": (90, 6),
    "back": (180, 8), "top": (0, 80), "back_three_quarter": (145, 22),
}
ALPHA_CUTOFF = 26  # Minecraft cut-out: alpha < 0.1 is discarded
BACKGROUND = (46, 50, 58)
GRID = (70, 76, 88)
NORMALS = {"north": (0, 0, -1), "south": (0, 0, 1), "east": (1, 0, 0), "west": (-1, 0, 0),
           "up": (0, 1, 0), "down": (0, -1, 0)}


def _corners(face, x1, y1, z1, x2, y2, z2):
    """Face corners in UV order: top-left, top-right, bottom-right, bottom-left as seen from
    outside (up: north edge on top; down: south edge on top)."""
    return {
        "north": [(x2, y2, z1), (x1, y2, z1), (x1, y1, z1), (x2, y1, z1)],
        "south": [(x1, y2, z2), (x2, y2, z2), (x2, y1, z2), (x1, y1, z2)],
        "east": [(x2, y2, z2), (x2, y2, z1), (x2, y1, z1), (x2, y1, z2)],
        "west": [(x1, y2, z1), (x1, y2, z2), (x1, y1, z2), (x1, y1, z1)],
        "up": [(x1, y2, z1), (x2, y2, z1), (x2, y2, z2), (x1, y2, z2)],
        "down": [(x1, y1, z2), (x2, y1, z2), (x2, y1, z1), (x1, y1, z1)],
    }[face]


def _num(v):
    try:
        return float(v)
    except (TypeError, ValueError):
        return 0.0  # molang expressions are not evaluated


class Scene:
    def __init__(self, path):
        self.path = Path(path)
        self.data = json.loads(self.path.read_text(encoding="utf-8"))
        res = self.data.get("resolution", {"width": 16, "height": 16})
        self.textures = {}
        for i, t in enumerate(self.data.get("textures", [])):
            src = t.get("source", "")
            if src.startswith("data:"):
                img = Image.open(io.BytesIO(base64.b64decode(src.split(",", 1)[1]))).convert("RGBA")
            else:
                side = self.path.with_name(t.get("relative_path") or t.get("name", ""))
                img = Image.open(side).convert("RGBA") if side.is_file() else Image.new("RGBA", (16, 16), (255, 0, 255, 255))
            entry = (np.asarray(img), float(t.get("uv_width", res["width"])), float(t.get("uv_height", res["height"])))
            self.textures[str(i)] = entry
            if "id" in t:
                self.textures.setdefault(str(t["id"]), entry)
        self.bones, self.parent, self.owner, self.order = {}, {}, {}, []
        for g, parent, cubes, _depth in outliner_groups(self.data):
            self.bones[g["uuid"]] = g
            self.parent[g["uuid"]] = parent
            self.order.append(g["uuid"])
            for c in cubes:
                self.owner[c] = g["uuid"]
        self.elements = [e for e in self.data.get("elements", []) if e.get("visibility", True) is not False]
        self.animations = {a["name"]: a for a in self.data.get("animations", [])}

    # -- animation
    def _channels(self, anim):
        out = {}
        for uuid, animator in (anim or {}).get("animators", {}).items():
            for kf in animator.get("keyframes", []):
                dp = (kf.get("data_points") or [{}])[0]
                out.setdefault((uuid, kf["channel"]), []).append(
                    (float(kf["time"]), np.array([_num(dp.get(k, 0)) for k in "xyz"]), kf.get("interpolation", "linear")))
        for v in out.values():
            v.sort(key=lambda k: k[0])
        return out

    @staticmethod
    def _sample(keys, t):
        if t <= keys[0][0]:
            return keys[0][1]
        if t >= keys[-1][0]:
            return keys[-1][1]
        for i in range(len(keys) - 1):
            (t0, v0, i0), (t1, v1, i1) = keys[i], keys[i + 1]
            if t0 <= t <= t1:
                f = 0.0 if t1 == t0 else (t - t0) / (t1 - t0)
                if i0 == "step":
                    return v0
                if "catmullrom" in (i0, i1):
                    p0 = keys[i - 1][1] if i > 0 else v0
                    p3 = keys[i + 2][1] if i + 2 < len(keys) else v1
                    f2, f3 = f * f, f * f * f
                    return 0.5 * ((2 * v0) + (-p0 + v1) * f + (2 * p0 - 5 * v0 + 4 * v1 - p3) * f2
                                  + (-p0 + 3 * v0 - 3 * v1 + p3) * f3)
                return v0 + (v1 - v0) * f
        return keys[-1][1]

    def matrices(self, anim_name=None, t=0.0):
        anim = self.animations.get(anim_name) if anim_name else None
        if anim_name and anim is None:
            raise SystemExit(f"{self.path.name} has no animation {anim_name!r}; has {sorted(self.animations)}")
        ch = self._channels(anim)
        if anim:
            length = float(anim.get("length") or 0)
            if length > 0:
                t = t % length if anim.get("loop") == "loop" and t != length else min(max(t, 0.0), length)
        mats = {}
        for uuid in self.order:
            g = self.bones[uuid]
            rot = np.array(g.get("rotation", [0, 0, 0]), float)
            pos, scl = np.zeros(3), np.ones(3)
            if (uuid, "rotation") in ch:
                a = self._sample(ch[(uuid, "rotation")], t)
                rot = rot + np.array([-a[0], -a[1], a[2]])
            if (uuid, "position") in ch:
                p = self._sample(ch[(uuid, "position")], t)
                pos = np.array([-p[0], p[1], p[2]])
            if (uuid, "scale") in ch:
                scl = self._sample(ch[(uuid, "scale")], t)
            local = affine(rot, g.get("origin", [0, 0, 0]), pos, scl)
            parent = self.parent[uuid]
            mats[uuid] = (mats[parent] @ local) if parent else local
        return mats

    # -- geometry
    def triangles(self, mats):
        """-> list of (pts 3x3 world, uv 3x2 texture px, texture key, world normal)."""
        tris = []
        for e in self.elements:
            bone = mats.get(self.owner.get(e["uuid"]), np.eye(4))
            em = bone @ affine(e.get("rotation", [0, 0, 0]), e.get("origin", [0, 0, 0]))
            rot3 = em[:3, :3]
            if e.get("type", "cube") == "mesh":
                # Mesh vertices are local to the element origin: bone * T(origin) * R.  The
                # packaged Sahur meshes only assemble with R = Rx @ Ry @ Rz (three.js 'XYZ'),
                # unlike cubes/bones (Rz @ Ry @ Rx); kept that way to read them correctly.
                rx, ry, rz = e.get("rotation", [0, 0, 0])
                mm = np.eye(4)
                mm[:3, :3] = rotation_matrix(rx, 0, 0) @ rotation_matrix(0, ry, 0) @ rotation_matrix(0, 0, rz)
                mm[:3, 3] = e.get("origin", [0, 0, 0])
                tris.extend(self._mesh(e, bone @ mm))
                continue
            inf = float(e.get("inflate", 0) or 0)
            lo = np.array(e["from"], float) - inf
            hi = np.array(e["to"], float) + inf
            for face, fd in e.get("faces", {}).items():
                if fd.get("texture") is None or face not in NORMALS:
                    continue
                key = str(fd["texture"])
                if key not in self.textures:
                    continue
                pts = np.array(_corners(face, *lo, *hi), float)
                pts = (np.c_[pts, np.ones(4)] @ em.T)[:, :3]
                u1, v1, u2, v2 = (float(v) for v in fd.get("uv", [0, 0, 0, 0]))
                uv = np.array([(u1, v1), (u2, v1), (u2, v2), (u1, v2)])
                k = int(fd.get("rotation", 0) or 0) // 90
                if k:
                    uv = np.roll(uv, k, axis=0)
                n = rot3 @ np.array(NORMALS[face], float)
                n /= np.linalg.norm(n) or 1
                tris.append((pts[[0, 1, 2]], uv[[0, 1, 2]], key, n))
                tris.append((pts[[0, 2, 3]], uv[[0, 2, 3]], key, n))
        return tris

    def _mesh(self, e, em):
        out = []
        verts = {k: np.array(v, float) for k, v in e.get("vertices", {}).items()}
        for fd in e.get("faces", {}).values():
            if fd.get("texture") is None or str(fd["texture"]) not in self.textures:
                continue
            keys = [k for k in fd.get("vertices", []) if k in verts]
            if len(keys) < 3:
                continue
            p = np.array([verts[k] for k in keys])
            uvd = fd.get("uv", {})
            uv = np.array([uvd.get(k, [0, 0]) for k in keys], float)
            if len(keys) == 4:
                c = p.mean(axis=0)
                n0 = np.cross(p[1] - p[0], p[2] - p[0])
                if np.linalg.norm(n0) < 1e-9:
                    n0 = np.cross(p[2] - p[0], p[3] - p[0])
                ax = p[0] - c
                ay = np.cross(n0, ax)
                ang = [math.atan2(np.dot(q - c, ay), np.dot(q - c, ax)) for q in p]
                idx = np.argsort(ang)
                p, uv = p[idx], uv[idx]
            w = (np.c_[p, np.ones(len(p))] @ em.T)[:, :3]
            for j in range(1, len(keys) - 1):
                tri = w[[0, j, j + 1]]
                n = np.cross(tri[1] - tri[0], tri[2] - tri[0])
                ln = np.linalg.norm(n)
                if ln < 1e-9:
                    continue
                # Mesh winding is not guaranteed, so mesh faces are drawn two-sided (normal None).
                out.append((tri, uv[[0, j, j + 1]], str(fd["texture"]), None))
        return out


def camera(view):
    yaw, pitch = VIEWS[view] if isinstance(view, str) else view
    y, p = math.radians(yaw), math.radians(pitch)
    cam = np.array([-math.sin(y) * math.cos(p), math.sin(p), -math.cos(y) * math.cos(p)])
    f = -cam
    up = np.array([0.0, 1.0, 0.0])
    u = up - np.dot(up, f) * f
    u /= np.linalg.norm(u)
    r = np.cross(f, u)
    return f, u, r


def _shade(n):
    if n is None:
        return 0.85
    return float(n[0] ** 2 * 0.6 + n[2] ** 2 * 0.8 + n[1] ** 2 * (1.0 if n[1] > 0 else 0.5))


def rasterize(scene, tris, view, size, scale, center2d):
    f, u, r = camera(view)
    W = H = size
    color = np.zeros((H, W, 3), float)
    alpha = np.zeros((H, W), bool)
    zbuf = np.full((H, W), np.inf)
    cx, cy = center2d
    for pts, uv, key, n in tris:
        if n is not None and np.dot(n, f) > 1e-6:
            continue
        sx = (pts @ r - cx) * scale + W / 2
        sy = H / 2 - (pts @ u - cy) * scale
        sz = pts @ f
        x0, x1 = int(max(0, math.floor(sx.min()))), int(min(W - 1, math.ceil(sx.max())))
        y0, y1 = int(max(0, math.floor(sy.min()))), int(min(H - 1, math.ceil(sy.max())))
        if x1 < x0 or y1 < y0:
            continue
        area = (sx[1] - sx[0]) * (sy[2] - sy[0]) - (sx[2] - sx[0]) * (sy[1] - sy[0])
        if abs(area) < 1e-9:
            continue
        gx, gy = np.meshgrid(np.arange(x0, x1 + 1) + 0.5, np.arange(y0, y1 + 1) + 0.5)
        w0 = ((sx[1] - gx) * (sy[2] - gy) - (sx[2] - gx) * (sy[1] - gy)) / area
        w1 = ((sx[2] - gx) * (sy[0] - gy) - (sx[0] - gx) * (sy[2] - gy)) / area
        w2 = 1 - w0 - w1
        inside = (w0 >= -1e-6) & (w1 >= -1e-6) & (w2 >= -1e-6)
        if not inside.any():
            continue
        depth = w0 * sz[0] + w1 * sz[1] + w2 * sz[2]
        tex, uw, uh = scene.textures[key]
        th, tw = tex.shape[:2]
        tu = w0 * uv[0, 0] + w1 * uv[1, 0] + w2 * uv[2, 0]
        tv = w0 * uv[0, 1] + w1 * uv[1, 1] + w2 * uv[2, 1]
        ix = np.clip(np.floor(tu * tw / uw), 0, tw - 1).astype(int)
        iy = np.clip(np.floor(tv * th / uh), 0, th - 1).astype(int)
        texel = tex[iy, ix]
        sub_z = zbuf[y0:y1 + 1, x0:x1 + 1]
        ok = inside & (texel[..., 3] >= ALPHA_CUTOFF) & (depth < sub_z)
        if not ok.any():
            continue
        sub_z[ok] = depth[ok]
        color[y0:y1 + 1, x0:x1 + 1][ok] = texel[..., :3][ok] * _shade(n)
        alpha[y0:y1 + 1, x0:x1 + 1][ok] = True
    return color, alpha


def _project_extent(tris, view):
    f, u, r = camera(view)
    pts = np.vstack([t[0] for t in tris]) if tris else np.zeros((1, 3))
    return (pts @ r).min(), (pts @ r).max(), (pts @ u).min(), (pts @ u).max()


def _draw_grid(img, view, scale, center2d, blocks=3):
    f, u, r = camera(view)
    d = ImageDraw.Draw(img)
    W, H = img.size
    cx, cy = center2d

    def scr(p):
        p = np.asarray(p, float)
        return ((p @ r - cx) * scale + W / 2, H / 2 - (p @ u - cy) * scale)

    span = blocks * 16
    for k in range(-blocks, blocks + 1):
        d.line([scr((k * 16, 0, -span)), scr((k * 16, 0, span))], fill=GRID, width=1)
        d.line([scr((-span, 0, k * 16)), scr((span, 0, k * 16))], fill=GRID, width=1)
    d.line([scr((0, 0, -span)), scr((0, 0, -span - 6))], fill=(200, 90, 90), width=2)  # north marker


def render_panels(scene, poses, views, size, diagonal=False):
    """poses: list of (anim, t, label).  Returns a grid image, rows=views and cols=poses, or a
    single row pairing views[i] with poses[i] when ``diagonal``."""
    all_tris = {i: scene.triangles(scene.matrices(a, t)) for i, (a, t, _l) in enumerate(poses)}
    span = 1.0
    centers = {}
    for v in views:
        ext = [_project_extent(all_tris[i], v) for i in all_tris]
        x0, x1 = min(e[0] for e in ext), max(e[1] for e in ext)
        y0, y1 = min(e[2] for e in ext), max(e[3] for e in ext)
        centers[v] = ((x0 + x1) / 2, (y0 + y1) / 2)
        span = max(span, x1 - x0, y1 - y0)
    scale = size * 0.86 / span
    font = ImageFont.load_default()
    rows = 1 if diagonal else len(views)
    sheet = Image.new("RGB", (size * len(poses), size * rows), BACKGROUND)
    first = all_tris[0] or [(np.zeros((3, 3)),)]  # height label from the first (rest/t=0) pose
    lo_y = min(t[0][:, 1].min() for t in first)
    hi_y = max(t[0][:, 1].max() for t in first)
    for row, v in enumerate(views):
        for col, (a, t, label) in enumerate(poses):
            if diagonal and col != row:
                continue
            panel = Image.new("RGB", (size, size), BACKGROUND)
            _draw_grid(panel, v, scale, centers[v])
            color, mask = rasterize(scene, all_tris[col], v, size, scale, centers[v])
            arr = np.asarray(panel).copy()
            arr[mask] = np.clip(color[mask], 0, 255).astype(np.uint8)
            panel = Image.fromarray(arr)
            d = ImageDraw.Draw(panel)
            d.text((6, 4), f"{v}  {label}", fill=(230, 230, 230), font=font)
            if row == 0 and col == 0:
                d.text((6, size - 14), f"{scene.data.get('name')}  h={(hi_y - lo_y) / 16:.2f} blocks", fill=(200, 200, 200), font=font)
            d.rectangle([0, 0, size - 1, size - 1], outline=(30, 32, 38))
            sheet.paste(panel, (col * size, 0 if diagonal else row * size))
    return sheet


def _atlas_panel(scene, height):
    tex = Image.fromarray(next(iter(scene.textures.values()))[0]) if scene.textures else Image.new("RGBA", (16, 16))
    k = max(1, height // max(tex.width, tex.height))
    big = tex.resize((tex.width * k, tex.height * k), Image.NEAREST)
    bg = Image.new("RGBA", big.size, (0, 0, 0, 255))
    checker = np.indices((big.height, big.width)).sum(axis=0) // 8 % 2
    bg = Image.fromarray(np.where(checker[..., None] == 1, [70, 70, 70, 255], [100, 100, 100, 255]).astype(np.uint8), "RGBA")
    bg.alpha_composite(big)
    return bg.convert("RGB")


def _check_out(out):
    out = Path(out)
    if not out.is_absolute():
        out = (Path.cwd() / out)
    out = out.resolve()
    try:
        out.relative_to(WORK.resolve())
    except ValueError:
        raise SystemExit(f"refusing to write {out}: previews must go under {WORK}")
    out.parent.mkdir(parents=True, exist_ok=True)
    return out


def render_sheet(model_path, out, views=("front", "three_quarter", "side", "back"), anim=None, frames=None,
                 t=None, size=320, atlas=False):
    out = _check_out(out)
    scene = Scene(model_path)
    if anim and frames:
        a = scene.animations.get(anim)
        if a is None:
            raise SystemExit(f"{scene.path.name} has no animation {anim!r}; has {sorted(scene.animations)}")
        length = float(a.get("length") or 1)
        steps = frames if a.get("loop") == "loop" else max(1, frames - 1)
        poses = [(anim, length * i / steps, f"{anim} t={length * i / steps:.2f}") for i in range(frames)]
        sheet = render_panels(scene, poses, list(views), size)
    else:
        # one pose (rest, or the clip at time t), every view side by side at a shared scale
        tt = 0.0 if t is None else t
        pose = (anim, tt, f"{anim} t={tt:.2f}") if anim else (None, 0.0, "rest")
        sheet = render_panels(scene, [pose] * len(views), list(views), size, diagonal=True)
    if atlas:
        panel = _atlas_panel(scene, sheet.height)
        wide = Image.new("RGB", (sheet.width + panel.width, max(sheet.height, panel.height)), BACKGROUND)
        wide.paste(sheet, (0, 0))
        wide.paste(panel, (sheet.width, 0))
        sheet = wide
    sheet.save(out)
    return out


def main(argv=None):
    ap = argparse.ArgumentParser(description=__doc__.split("\n\n")[0])
    ap.add_argument("model")
    ap.add_argument("--out", required=True, help="PNG path under work/")
    ap.add_argument("--views", default=None, help=f"comma list of {', '.join(VIEWS)}")
    ap.add_argument("--anim")
    ap.add_argument("--frames", type=int)
    ap.add_argument("--t", type=float)
    ap.add_argument("--size", type=int, default=320)
    ap.add_argument("--atlas", action="store_true")
    args = ap.parse_args(argv)
    default = "three_quarter,side" if args.anim and args.frames else "front,three_quarter,side,back"
    views = [v.strip() for v in (args.views or default).split(",") if v.strip()]
    bad = [v for v in views if v not in VIEWS]
    if bad:
        ap.error(f"unknown views {bad}; choose from {list(VIEWS)}")
    if args.anim and args.frames is None and args.t is None:
        args.frames = 6
    out = render_sheet(args.model, args.out, views, args.anim, args.frames, args.t, args.size, args.atlas)
    print(out)


if __name__ == "__main__":
    main()
