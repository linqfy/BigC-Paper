#!/usr/bin/env python3
"""bbkit: author BetterModel 3.2.0 ``.bbmodel`` entity models from Python.

A model is a handful of cuboids hung on a few bones, painted with pixel-art textures and
animated with named clips.  Build relief from textures, layered "overlay" cubes (like the
vanilla hat layer) and transparent planes (wings, manes, cloth, blades), never from piles of
micro-cubes: the cube budget enforces that.

Conventions (checked against the packaged models and the Sift/GeckoLib converter)
--------------------------------------------------------------------------------
* Units are Blockbench pixels: 16 units = 1 block.  Y is up.  Ground contact is y = 0 and the
  model stands centred on x = z = 0.
* The model FACES NORTH (-Z).  The creature's RIGHT side is +X, its LEFT side is -X, its back
  is +Z.  (Don Pollo, Sift and Mutango models all put the face on the north face.)
* ``root`` is created for you as the single top-level bone at (0, 0, 0).  Every other bone
  hangs below it (``parent="root"`` by default).
* A bone's ``pivot`` is the point it rotates about (shoulder, hip, neck...).  Static bone and
  cube ``rotation`` values are Blockbench's own Euler angles in degrees, applied Z*Y*X.
* Cubes: prefer axis-aligned cubes and rotate BONES.  Java item models only allow a single
  axis rotated by a multiple of 22.5 degrees in [-45, 45] per element; the packaged
  ``toxic_mob``/Sift models prove BetterModel accepts other angles, but how it maps them is
  engine-side, so ``validate()`` warns on anything outside the safe set.
* Every cube gets all six faces (BetterModel 3.2.0 aborts the whole reload when a face is
  missing - see ``BetterModelCubeFacesTest``); UVs stay inside the texture; a cube never
  ends up with zero textured faces (a 26.2 client rejects such an element).
* Mirrored/back faces use reversed UV rectangles (x1 > x2), exactly like the shipped Sift
  models do for Blockbench box-UV ``up`` faces.

Animation channel convention (what you would type in Blockbench; BetterModel plays the
same Bedrock-style convention, see tools/convert_geckolib_bbmodel.py)
-------------------------------------------------------------------------------------
* rotation X: POSITIVE tips the top of the bone FORWARD (bow, nod, lean in) so anything
  hanging below the pivot swings BACKWARD.  NEGATIVE raises a hanging arm/leg FORWARD and
  lifts a head's nose up.  -90 = arm pointing straight ahead (zombie pose).
* rotation Y: positive turns the bone's front toward the creature's RIGHT (+X).
* rotation Z: positive swings a hanging limb toward +X: OUTWARD for the right arm, INWARD for
  the left arm (use negative Z to raise the left arm sideways).
* position: x is mirrored (positive x moves toward the creature's LEFT, -X), y up, z positive
  = backward.  Units are model units.  scale: plain multipliers.
* The helpers (``walk_biped``, ``attack_swing``...) already apply these signs.

Painting
--------
Each cube owns per-face canvases in FACE PIXELS: x grows to the right and y downward as you
look at that face from outside.  ``up`` is seen from above with north (the front) at the top
row; ``down`` has south at the top row.  The texture atlas is packed automatically (one
box-UV island per cube, single rectangles for planes/overlays); never place UVs by hand.
One texel per unit by default (vanilla density): a 8x8x8 head has 8x8-pixel faces.

Cheat-sheet
-----------
    m = Model("bigcasares_myth_<id>", texture_size=(128, 128))     # root bone already exists
    b = m.bone(name, parent="root", pivot=(x, y, z), rotation=None) -> Bone
    c = m.cube(bone, name, frm, to, *, inflate=0, rotation=None, pivot=None,
               uv=None | other_cube, mirror=False)                   -> Cube
    p = m.plane(bone, name, frm, to, *, double_sided=True, rotation=None, pivot=None)
        # one axis of frm/to equal: flat quad; paint p.front (alpha = cut-out shape)
    c2 = c.mirror_x(bone, name)        # X-mirrored copy that shares (mirrored) texture
    o = c.overlay(inflate=0.25)        # hat-style layer; transparent until painted
    c.paint(base, *, noise=0.04, jitter=0.18, shade=True, edges=0.0, faces=None)
    c.face("north") / c.north / c.sides() / c.faces()            -> Face
    Face: fill, shaded, pixel, rect, line, outline, ellipse, stripe, dither, gradient,
          eyes, decal, strands, scales, cracks, spots, rivets, planks, jagged, tatter,
          darken, lighten, noise, clear, symmetrize, flip
    ramp(base, n=4) -> [dark .. light] colour list (base is index n-2); mix(a, b, t); rgba(c)
    a = m.animation(name, length, loop="loop"|"once"|"hold", interp="catmullrom"|"linear")
    a.rot(bone, {t: (x, y, z), ...}, interp=None); a.pos(...); a.scale(...); a.clear(bone)
    walk_biped(a, ...), walk_quadruped(a, ...), idle_breathe(a, ...),
    attack_swing(a, ...), death_fall(a, ...), hurt_flinch(a, ...), flap(a, ...)
    m.validate(require=("idle", "walk")) -> warnings (raises ValidationError on errors)
    m.save(path)                          # .bbmodel with embedded base64 PNG

Minimal example
---------------
    from bbkit import Model, walk_biped, idle_breathe

    def build():
        m = Model("bigcasares_myth_imp", texture_size=(64, 64))
        body = m.bone("body", pivot=(0, 10, 0))
        head = m.bone("head", "body", pivot=(0, 16, 0))
        leg_l = m.bone("leg_left", "body", pivot=(-2, 6, 0))
        leg_r = m.bone("leg_right", "body", pivot=(2, 6, 0))
        torso = m.cube(body, "torso", (-4, 6, -2), (4, 16, 2))
        torso.paint("#8a3b2a")
        torso.north.rect(2, 3, 4, 1, "#5a2018")            # belt line
        skull = m.cube(head, "skull", (-3, 16, -3), (3, 22, 3))
        skull.paint("#9c4632")
        skull.north.eyes(3, 2, w=1, gap=2, white="#ffd23a", pupil="#ffd23a")
        horn = m.cube(head, "horn_left", (-3, 21, -1), (-2, 25, 0), rotation=(0, 0, 22.5))
        horn.paint("#e8dcc0")
        thigh = m.cube(leg_l, "thigh_left", (-3.5, 0, -1.5), (-0.5, 6, 1.5))
        thigh.paint("#6e2c20")
        thigh.mirror_x(leg_r, "thigh_right")
        cape = m.plane(body, "cape", (-4, 3, 2.05), (4, 15, 2.05))
        cape.front.fill("#30204a").tatter(3)
        hat = skull.overlay(0.3)
        hat.up.fill("#222").noise(0.1)
        idle_breathe(m.animation("idle", 2.0), body="body", head="head")
        walk_biped(m.animation("walk", 0.8), legs=("leg_left", "leg_right"), body="body")
        return m

    MODELS = {"bigcasares_myth_imp": build}          # in tools/mythicdungeons/models/<x>.py

Run ``python tools/mythicdungeons/build.py bigcasares_myth_imp --preview`` to build, validate,
save and render previews into work/previews/.

CLI
---
    python tools/mythicdungeons/bbkit.py check <file.bbmodel>
        re-loads a saved model: bone tree, cube counts, animations and the packaged-model
        contracts (six faces, UVs inside the texture, single ``root``).
"""
from __future__ import annotations

import base64
import colorsys
import hashlib
import io
import json
import math
import random
import sys
import uuid
from pathlib import Path

import numpy as np
from PIL import Image

FACES = ("north", "east", "south", "west", "up", "down")
SIDES = ("north", "east", "south", "west")
LOOP_MODES = ("loop", "once", "hold")
INTERPOLATIONS = ("linear", "catmullrom", "step")
NAMESPACE = uuid.UUID("6d1f3c52-8a0e-5b7c-9e44-2f6a1d0b7c31")
# Plane orientation: normal axis -> (front face, back face, reversed UV component on the back).
PLANE_FACES = {"z": ("north", "south", "u"), "x": ("east", "west", "u"), "y": ("up", "down", "v")}
SAFE_ANGLES = (-45.0, -22.5, 0.0, 22.5, 45.0)


class ValidationError(ValueError):
    """Raised by ``Model.validate`` with every blocking problem listed."""


class TextureOverflow(ValueError):
    """The automatic atlas did not fit into the declared texture size."""


# ----------------------------------------------------------------------------- colours

def rgba(color) -> tuple:
    """'#rgb', '#rrggbb', '#rrggbbaa', (r, g, b) or (r, g, b, a) -> (r, g, b, a) ints."""
    if isinstance(color, str):
        s = color.strip().lstrip("#")
        if len(s) in (3, 4):
            s = "".join(ch * 2 for ch in s)
        if len(s) == 6:
            s += "ff"
        if len(s) != 8:
            raise ValueError(f"bad colour {color!r}")
        return tuple(int(s[i:i + 2], 16) for i in (0, 2, 4, 6))
    values = [int(round(v)) for v in color]
    if len(values) == 3:
        values.append(255)
    if len(values) != 4:
        raise ValueError(f"bad colour {color!r}")
    return tuple(max(0, min(255, v)) for v in values)


def mix(a, b, t: float) -> tuple:
    """Linear blend of two colours (t=0 -> a, t=1 -> b)."""
    a, b = rgba(a), rgba(b)
    return tuple(int(round(a[i] + (b[i] - a[i]) * t)) for i in range(4))


def shade(color, amount: float) -> tuple:
    """amount < 0 darkens toward a cool shadow, > 0 lightens toward a warm highlight."""
    return _tone(color, amount)


def _hue_toward(h: float, target: float, f: float) -> float:
    d = ((target - h + 0.5) % 1.0) - 0.5
    return (h + d * f) % 1.0


def _tone(color, k: float) -> tuple:
    r, g, b, a = rgba(color)
    h, s, v = colorsys.rgb_to_hsv(r / 255, g / 255, b / 255)
    if k < 0:
        v = v * max(0.0, 1 + k)
        s = min(1.0, s * (1 - 0.6 * k) + (0.04 if s > 0.05 else 0))
        h = _hue_toward(h, 0.70, min(0.5, -k * 0.35)) if s > 0.08 else h
    else:
        v = min(1.0, v * (1 + k) + 0.02 * k)
        s = max(0.0, s * (1 - 0.45 * k))
        h = _hue_toward(h, 0.13, min(0.5, k * 0.30)) if s > 0.08 else h
    rr, gg, bb = colorsys.hsv_to_rgb(h, s, v)
    return (int(round(rr * 255)), int(round(gg * 255)), int(round(bb * 255)), a)


def ramp(base, n: int = 4, spread: float = 0.2) -> list:
    """Minecraft-style tone ramp, darkest first.  ``ramp(c)[-2]`` is ``c`` itself (n >= 2);
    lower indices are hue-shifted shadows, the last index is a warm highlight."""
    if n < 2:
        return [rgba(base)]
    base_index = n - 2
    return [rgba(base) if i == base_index else _tone(base, (i - base_index) * spread) for i in range(n)]


def _seed(*parts) -> int:
    return int.from_bytes(hashlib.sha256("/".join(str(p) for p in parts).encode()).digest()[:8], "big")


def _num(value: float):
    value = round(float(value), 4)
    return int(value) if value.is_integer() else value


def _vec(values, n=3) -> list:
    values = list(values)
    if len(values) != n:
        raise ValueError(f"expected {n} numbers, got {values!r}")
    return [_num(v) for v in values]


def _str_num(value: float) -> str:
    value = round(float(value), 4)
    return str(int(value)) if value.is_integer() else repr(value)


# ----------------------------------------------------------------------------- math

def rotation_matrix(rx: float, ry: float, rz: float) -> np.ndarray:
    """Blockbench Euler rotation in degrees, matrix = Rz @ Ry @ Rx (X applied first)."""
    x, y, z = (math.radians(a) for a in (rx, ry, rz))
    cx, sx, cy, sy, cz, sz = math.cos(x), math.sin(x), math.cos(y), math.sin(y), math.cos(z), math.sin(z)
    mx = np.array([[1, 0, 0], [0, cx, -sx], [0, sx, cx]])
    my = np.array([[cy, 0, sy], [0, 1, 0], [-sy, 0, cy]])
    mz = np.array([[cz, -sz, 0], [sz, cz, 0], [0, 0, 1]])
    return mz @ my @ mx


def affine(rotation=(0, 0, 0), pivot=(0, 0, 0), offset=(0, 0, 0), scale=(1, 1, 1)) -> np.ndarray:
    """4x4: translate(pivot + offset) * R * S * translate(-pivot)."""
    m = np.eye(4)
    m[:3, :3] = rotation_matrix(*rotation) @ np.diag(scale)
    p = np.asarray(pivot, float)
    m[:3, 3] = p + np.asarray(offset, float) - m[:3, :3] @ p
    return m


# ----------------------------------------------------------------------------- face canvas

class Face:
    """Pixel canvas of one cube face (x right, y down as seen from outside the face).

    Every drawing method returns ``self`` so calls chain.  Colours may carry alpha: alpha
    255 replaces, partial alpha blends over what is there, ``clear`` makes pixels transparent.
    """

    def __init__(self, cube: "Cube", name: str, w: int, h: int):
        self.cube, self.name, self.w, self.h = cube, name, w, h
        self.px = np.zeros((h, w, 4), np.uint8)
        seed = _seed(cube.model.name, cube.name, name)
        self.rng = random.Random(seed)
        self.np_rng = np.random.default_rng(seed)

    # -- internals
    def _region(self, x, y, w, h):
        w = self.w - x if w is None else w
        h = self.h - y if h is None else h
        x0, y0 = max(0, int(x)), max(0, int(y))
        x1, y1 = min(self.w, int(x) + int(w)), min(self.h, int(y) + int(h))
        return x0, y0, x1, y1

    def _put(self, ys, xs, color):
        c = np.array(rgba(color), float)
        if c[3] >= 255:
            self.px[ys, xs] = c.astype(np.uint8)
            return
        if c[3] <= 0:
            return
        dst = self.px[ys, xs].astype(float)
        a = c[3] / 255.0
        empty = dst[..., 3] == 0
        out = dst.copy()
        out[..., :3] = dst[..., :3] * (1 - a) + c[:3] * a
        out[..., 3] = np.maximum(dst[..., 3], c[3])
        out[empty] = c
        self.px[ys, xs] = np.clip(np.round(out), 0, 255).astype(np.uint8)

    def mask(self) -> np.ndarray:
        """Boolean array (h, w) of painted (non-transparent) pixels."""
        return self.px[..., 3] > 0

    def is_blank(self) -> bool:
        return not self.mask().any()

    # -- fills
    def fill(self, color, noise: float = 0.0):
        """Fill the whole face, with optional per-pixel brightness noise (0.05 = +-5%)."""
        self.px[:, :] = rgba(color)
        return self.noise(noise) if noise else self

    def shaded(self, base, *, noise=0.04, jitter=0.18, shade=True, edges=0.0, tones=4):
        """Minecraft-like fill: quantised tone ramp, lighter top face, darker bottom face and
        bottom rows on the sides, random +-1 tone flecks and subtle per-pixel noise."""
        pal = np.array(ramp(base, tones), float)
        n = len(pal)
        idx = np.full((self.h, self.w), n - 2, int)
        if shade:
            if self.name == "up":
                idx += 1
            elif self.name == "down":
                idx -= 1
            elif self.h >= 4:
                rows = max(1, self.h // 5)
                idx[self.h - rows:, :] -= 1
        if jitter:
            r = self.np_rng.random((self.h, self.w))
            idx[r < jitter / 2] -= 1
            idx[(r >= jitter / 2) & (r < jitter)] += 1
        idx = np.clip(idx, 0, n - 1)
        out = pal[idx]
        if noise:
            out[..., :3] *= 1 + self.np_rng.uniform(-noise, noise, (self.h, self.w, 1))
        self.px[:, :] = np.clip(np.round(out), 0, 255).astype(np.uint8)
        if edges:
            self.outline((0, 0, 0, int(255 * min(1.0, edges))))
        return self

    def gradient(self, top, bottom, *, axis="v", steps=4, x=0, y=0, w=None, h=None):
        """Banded (pixel-art) gradient from ``top`` to ``bottom`` (axis 'v') or left->right ('h')."""
        x0, y0, x1, y1 = self._region(x, y, w, h)
        length = (y1 - y0) if axis == "v" else (x1 - x0)
        for i in range(max(0, length)):
            t = 0 if length <= 1 else i / (length - 1)
            if steps:
                t = round(t * (steps - 1)) / max(1, steps - 1)
            c = mix(top, bottom, t)
            if axis == "v":
                self._put(slice(y0 + i, y0 + i + 1), slice(x0, x1), c)
            else:
                self._put(slice(y0, y1), slice(x0 + i, x0 + i + 1), c)
        return self

    # -- primitives
    def pixel(self, x, y, color):
        if 0 <= x < self.w and 0 <= y < self.h:
            self._put(slice(int(y), int(y) + 1), slice(int(x), int(x) + 1), color)
        return self

    def rect(self, x, y, w, h, color, noise: float = 0.0):
        x0, y0, x1, y1 = self._region(x, y, w, h)
        if x1 > x0 and y1 > y0:
            self._put(slice(y0, y1), slice(x0, x1), color)
            if noise:
                self.noise(noise, x=x0, y=y0, w=x1 - x0, h=y1 - y0)
        return self

    def line(self, x0, y0, x1, y1, color):
        """Bresenham line, endpoints inclusive."""
        x0, y0, x1, y1 = int(x0), int(y0), int(x1), int(y1)
        dx, dy = abs(x1 - x0), -abs(y1 - y0)
        sx, sy = (1 if x0 < x1 else -1), (1 if y0 < y1 else -1)
        err = dx + dy
        while True:
            self.pixel(x0, y0, color)
            if x0 == x1 and y0 == y1:
                break
            e2 = 2 * err
            if e2 >= dy:
                err += dy
                x0 += sx
            if e2 <= dx:
                err += dx
                y0 += sy
        return self

    def outline(self, color, x=0, y=0, w=None, h=None, sides="tblr", width=1):
        """1-px (or ``width``) frame of a rectangle (default: the whole face); ``sides`` picks
        top/bottom/left/right."""
        x0, y0, x1, y1 = self._region(x, y, w, h)
        for i in range(width):
            if "t" in sides:
                self.rect(x0, y0 + i, x1 - x0, 1, color)
            if "b" in sides:
                self.rect(x0, y1 - 1 - i, x1 - x0, 1, color)
            if "l" in sides:
                self.rect(x0 + i, y0, 1, y1 - y0, color)
            if "r" in sides:
                self.rect(x1 - 1 - i, y0, 1, y1 - y0, color)
        return self

    def ellipse(self, cx, cy, rx, ry, color):
        """Filled pixel ellipse centred on (cx, cy) (floats allowed)."""
        ys, xs = np.mgrid[0:self.h, 0:self.w]
        m = ((xs + 0.5 - cx) / max(rx, 0.01)) ** 2 + ((ys + 0.5 - cy) / max(ry, 0.01)) ** 2 <= 1.0
        if m.any():
            self._put_mask(m, color)
        return self

    def _put_mask(self, m, color):
        ys, xs = np.nonzero(m)
        for yy, xx in zip(ys, xs):
            self._put(slice(yy, yy + 1), slice(xx, xx + 1), color)

    def stripe(self, color, *, every=2, axis="h", offset=0, width=1, x=0, y=0, w=None, h=None):
        """Repeating bands: horizontal rows (axis 'h') or vertical columns ('v')."""
        x0, y0, x1, y1 = self._region(x, y, w, h)
        if axis == "h":
            for yy in range(y0, y1):
                if (yy - y0 - offset) % every < width:
                    self.rect(x0, yy, x1 - x0, 1, color)
        else:
            for xx in range(x0, x1):
                if (xx - x0 - offset) % every < width:
                    self.rect(xx, y0, 1, y1 - y0, color)
        return self

    def dither(self, color, *, density=0.5, pattern="checker", x=0, y=0, w=None, h=None):
        """Checkerboard (density ignored), ordered 'bayer' or seeded 'noise' sprinkle."""
        x0, y0, x1, y1 = self._region(x, y, w, h)
        bayer = np.array([[0, 8, 2, 10], [12, 4, 14, 6], [3, 11, 1, 9], [15, 7, 13, 5]]) / 16.0
        for yy in range(y0, y1):
            for xx in range(x0, x1):
                if pattern == "checker":
                    hit = (xx + yy) % 2 == 0
                elif pattern == "bayer":
                    hit = bayer[yy % 4, xx % 4] < density
                else:
                    hit = self.rng.random() < density
                if hit:
                    self.pixel(xx, yy, color)
        return self

    def eyes(self, cx, y, *, w=2, h=1, gap=2, white="#f2ead8", pupil="#1c1210", look="in",
             socket=None, glow=None, brow=None):
        """Symmetric pair of eyes around column ``cx`` (face centre is ``face.w / 2``).

        look: 'in' | 'out' | 'center' | 'none' (pupil placement); ``socket`` paints a darker
        frame; ``glow`` (colour) makes the whole eye glow with a lighter centre; ``brow`` draws
        a slanted angry brow line above (colour)."""
        lx = int(round(cx - gap / 2 - w))
        rx = int(round(cx + gap / 2))
        for side, x0 in (("left", lx), ("right", rx)):
            if socket:
                self.rect(x0 - 1, y - 1, w + 2, h + 2, socket)
            if glow:
                self.rect(x0, y, w, h, glow)
                hot = mix(glow, "#fff3a0", 0.55)
                if w > 2:
                    self.rect(x0 + 1, y, w - 2, h, hot)
                else:  # hot pixel on the inner side
                    self.rect((x0 + w - 1) if side == "left" else x0, y, 1, h, hot)
                if brow:
                    self.line(x0 - 1, y - 2 if side == "left" else y - 1, x0 + w, y - 1 if side == "left" else y - 2, brow)
                continue
            self.rect(x0, y, w, h, white)
            if look != "none" and w >= 2:
                inner = (x0 + w - 1) if side == "left" else x0
                outer = x0 if side == "left" else (x0 + w - 1)
                px = {"in": inner, "out": outer}.get(look, x0 + w // 2)
                self.rect(px, y, 1, h, pupil)
            elif look != "none":
                self.rect(x0, y, w, h, pupil)
            if brow:
                if side == "left":
                    self.line(x0 - 1, y - 2, x0 + w, y - 1, brow)
                else:
                    self.line(x0 - 1, y - 1, x0 + w, y - 2, brow)
        return self

    def decal(self, rows, colors: dict, x=0, y=0, flip=False):
        """Stamp a small sprite drawn as strings: each char maps to a colour in ``colors``;
        '.' and ' ' are skipped and '_' clears to transparent.  ``flip`` mirrors it."""
        for j, row in enumerate(rows):
            if flip:
                row = row[::-1]
            for i, ch in enumerate(row):
                if ch in ". ":
                    continue
                if ch == "_":
                    self.clear(x + i, y + j, 1, 1)
                else:
                    self.pixel(x + i, y + j, colors[ch])
        return self

    # -- material patterns
    def strands(self, color, *, density=0.3, length=(2, 4), direction="down", x=0, y=0, w=None, h=None):
        """Fur/hair streaks: short vertical (or 'right') runs of ``color``."""
        x0, y0, x1, y1 = self._region(x, y, w, h)
        cols = range(x0, x1) if direction == "down" else range(y0, y1)
        for c in cols:
            if self.rng.random() > density:
                continue
            n = self.rng.randint(*length)
            start = self.rng.randint(y0, max(y0, y1 - 1)) if direction == "down" else self.rng.randint(x0, max(x0, x1 - 1))
            for k in range(n):
                if direction == "down":
                    self.pixel(c, start + k, color)
                else:
                    self.pixel(start + k, c, color)
        return self

    def scales(self, color, *, size=3, highlight=None, x=0, y=0, w=None, h=None):
        """Overlapping scale rows: dark U-shaped rims every ``size`` px, offset per row."""
        x0, y0, x1, y1 = self._region(x, y, w, h)
        for yy in range(y0, y1):
            row = (yy - y0) // size
            ly = (yy - y0) % size
            for xx in range(x0, x1):
                lx = (xx - x0 + (row % 2) * (size // 2)) % size
                if ly == size - 1 or (lx == 0 and ly >= size // 2):
                    self.pixel(xx, yy, color)
                elif highlight and ly == 0 and lx == size // 2:
                    self.pixel(xx, yy, highlight)
        return self

    def cracks(self, color, *, n=3, length=5):
        """Random-walk crack lines (stone, bark, scars)."""
        for _ in range(n):
            x, y = self.rng.randrange(self.w), self.rng.randrange(self.h)
            for _ in range(length):
                self.pixel(x, y, color)
                x += self.rng.choice((-1, 0, 1))
                y += self.rng.choice((0, 1, 1))
        return self

    def spots(self, color, *, n=6, size=(1, 2), x=0, y=0, w=None, h=None):
        """Scattered small rectangles (spots, moss, rust, freckles)."""
        x0, y0, x1, y1 = self._region(x, y, w, h)
        for _ in range(n):
            s = self.rng.randint(*size)
            self.rect(self.rng.randint(x0, max(x0, x1 - s)), self.rng.randint(y0, max(y0, y1 - s)), s, s, color)
        return self

    def rivets(self, color, *, every=4, inset=1, highlight=None):
        """Dots along the face border (armour plates, shields, chests)."""
        for xx in range(inset, self.w - inset, every):
            for yy in (inset, self.h - 1 - inset):
                self.pixel(xx, yy, color)
                if highlight:
                    self.pixel(xx, yy - 1 if yy > 0 else yy, highlight)
        return self

    def planks(self, color, *, every=4, joint=None):
        """Horizontal plank seams with staggered vertical joints (wood, bark, crates)."""
        self.stripe(color, every=every, axis="h", offset=every - 1)
        joint = joint or color
        for band in range(0, self.h, every):
            xx = self.rng.randrange(max(1, self.w))
            self.rect(xx, band, 1, every - 1, joint)
        return self

    def jagged(self, color, *, edge="top", depth=(2, 4), x=0, w=None):
        """Band hanging from ``edge`` ('top'/'bottom'/'left'/'right') with a ragged inner
        border: hanging fur, moss, drips, icicles.  Ideal on overlays."""
        lo, hi = depth
        if edge in ("top", "bottom"):
            x0, _, x1, _ = self._region(x, 0, w, None)
            for xx in range(x0, x1):
                n = self.rng.randint(lo, hi)
                ys = range(n) if edge == "top" else range(self.h - n, self.h)
                for yy in ys:
                    self.pixel(xx, yy, color)
        else:
            for yy in range(self.h):
                n = self.rng.randint(lo, hi)
                xs = range(n) if edge == "left" else range(self.w - n, self.w)
                for xx in xs:
                    self.pixel(xx, yy, color)
        return self

    def tatter(self, depth=3, *, edge="bottom", chance=0.7):
        """Clear a ragged strip from an edge (torn cloth, ragged wings, mane tips)."""
        for i in range(self.w if edge in ("top", "bottom") else self.h):
            if self.rng.random() > chance:
                continue
            n = self.rng.randint(1, depth)
            if edge == "bottom":
                self.clear(i, self.h - n, 1, n)
            elif edge == "top":
                self.clear(i, 0, 1, n)
            elif edge == "left":
                self.clear(0, i, n, 1)
            else:
                self.clear(self.w - n, i, n, 1)
        return self

    # -- adjustments
    def _scale_rgb(self, factor, x, y, w, h):
        x0, y0, x1, y1 = self._region(x, y, w, h)
        block = self.px[y0:y1, x0:x1].astype(float)
        block[..., :3] *= factor
        self.px[y0:y1, x0:x1] = np.clip(np.round(block), 0, 255).astype(np.uint8)

    def darken(self, amount=0.2, x=0, y=0, w=None, h=None):
        self._scale_rgb(1 - amount, x, y, w, h)
        return self

    def lighten(self, amount=0.2, x=0, y=0, w=None, h=None):
        self._scale_rgb(1 + amount, x, y, w, h)
        return self

    def noise(self, amount=0.05, x=0, y=0, w=None, h=None):
        """Seeded per-pixel brightness jitter on already painted pixels."""
        x0, y0, x1, y1 = self._region(x, y, w, h)
        if x1 <= x0 or y1 <= y0:
            return self
        block = self.px[y0:y1, x0:x1].astype(float)
        block[..., :3] *= 1 + self.np_rng.uniform(-amount, amount, (y1 - y0, x1 - x0, 1))
        self.px[y0:y1, x0:x1] = np.clip(np.round(block), 0, 255).astype(np.uint8)
        return self

    def clear(self, x=0, y=0, w=None, h=None):
        x0, y0, x1, y1 = self._region(x, y, w, h)
        self.px[y0:y1, x0:x1] = 0
        return self

    def symmetrize(self, keep="left"):
        """Copy one half onto the other mirrored (faces, chests, symmetric emblems)."""
        half = self.w // 2
        if keep == "left":
            self.px[:, self.w - half:] = self.px[:, :half][:, ::-1]
        else:
            self.px[:, :half] = self.px[:, self.w - half:][:, ::-1]
        return self

    def flip(self):
        self.px = self.px[:, ::-1].copy()
        return self

    def copy_from(self, other: "Face", flip=False):
        """Copy another face's pixels (sizes must match)."""
        if (other.w, other.h) != (self.w, self.h):
            raise ValueError(f"face size mismatch {other.w}x{other.h} vs {self.w}x{self.h}")
        self.px = (other.px[:, ::-1] if flip else other.px).copy()
        return self


# ----------------------------------------------------------------------------- model parts

class Bone:
    def __init__(self, model, name, parent, pivot, rotation):
        self.model, self.name, self.parent = model, name, parent
        self.pivot = _vec(pivot)
        self.rotation = _vec(rotation or (0, 0, 0))
        self.uuid = model.uid("bone", name)
        self.cubes: list = []
        self.children: list = []

    def __repr__(self):
        return f"Bone({self.name!r})"


class Cube:
    def __init__(self, model, bone, name, frm, to, inflate, rotation, pivot, share, mirror, plane_axis, kind):
        self.model, self.bone, self.name = model, bone, name
        self.frm, self.to = frm, to
        self.inflate = float(inflate)
        self.rotation = _vec(rotation or (0, 0, 0))
        if pivot is None:
            pivot = [(a + b) / 2 for a, b in zip(frm, to)] if rotation else bone.pivot
        self.origin = _vec(pivot)
        self.share, self.mirror = share, mirror
        self.plane_axis, self.kind = plane_axis, kind  # kind: 'cube' | 'plane' | 'overlay'
        self.uuid = model.uid("cube", name)
        self._faces: dict = {}
        if share is None:
            for f in self.textured_faces():
                w, h = self.face_size(f)
                self._faces[f] = Face(self, f, w, h)

    def __repr__(self):
        return f"Cube({self.name!r} on {self.bone.name!r})"

    @property
    def size(self):
        return [b - a for a, b in zip(self.frm, self.to)]

    def face_size(self, face):
        d = self.model.density
        px = lambda v: max(1, int(round(v * d)))
        sx, sy, sz = self.size
        return {"north": (px(sx), px(sy)), "south": (px(sx), px(sy)), "east": (px(sz), px(sy)),
                "west": (px(sz), px(sy)), "up": (px(sx), px(sz)), "down": (px(sx), px(sz))}[face]

    def textured_faces(self):
        if self.plane_axis:
            front, back, _ = PLANE_FACES[self.plane_axis]
            return (front,)
        return FACES

    @property
    def front(self) -> Face:
        """The painted side of a plane (north / east / up for z / x / y planes)."""
        if not self.plane_axis:
            return self.face("north")
        return self.face(PLANE_FACES[self.plane_axis][0])

    def face(self, name) -> Face:
        if self.share is not None:
            raise ValueError(f"{self.name} shares its texture with {self.share.name}; paint that cube")
        if name not in self._faces:
            raise ValueError(f"{self.name} has no paintable face {name!r} (planes: use .front)")
        return self._faces[name]

    north = property(lambda self: self.face("north"))
    south = property(lambda self: self.face("south"))
    east = property(lambda self: self.face("east"))
    west = property(lambda self: self.face("west"))
    up = property(lambda self: self.face("up"))
    down = property(lambda self: self.face("down"))

    def sides(self) -> list:
        return [self._faces[f] for f in SIDES if f in self._faces]

    def faces(self, *names) -> list:
        return [self._faces[f] for f in (names or self._faces)]

    def paint(self, base, *, noise=0.04, jitter=0.18, shade=True, edges=0.0, faces=None, tones=4):
        """Shaded Minecraft-like base coat on every own face (or ``faces``)."""
        for f in faces or list(self._faces):
            self.face(f).shaded(base, noise=noise, jitter=jitter, shade=shade, edges=edges, tones=tones)
        return self

    def overlay(self, inflate=0.25, name=None) -> "Cube":
        """Second, slightly inflated cube on the same bone with a transparent texture: paint
        only the pixels that should stand out (hair, armour trims, moss, hat layer)."""
        if self.plane_axis:
            raise ValueError("overlays are for cubes; add another plane instead")
        name = name or f"{self.name}_overlay"
        return self.model._add_cube(self.bone, name, self.frm, self.to, self.inflate + inflate,
                                    self.rotation, self.origin, None, False, None, "overlay")

    def mirror_x(self, bone, name) -> "Cube":
        """X-mirrored twin (left <-> right) that reuses this cube's texture mirrored."""
        frm = [-self.to[0], self.frm[1], self.frm[2]]
        to = [-self.frm[0], self.to[1], self.to[2]]
        rot = [self.rotation[0], -self.rotation[1], -self.rotation[2]]
        origin = [-self.origin[0], self.origin[1], self.origin[2]]
        bone = self.model._bone(bone)
        return self.model._add_cube(bone, name, frm, to, self.inflate, rot if any(rot) else None, origin,
                                    self.share or self, not self.mirror if self.share else True,
                                    self.plane_axis, self.kind)


class Animation:
    def __init__(self, model, name, length, loop, interp):
        if loop not in LOOP_MODES:
            raise ValueError(f"loop must be one of {LOOP_MODES}")
        if interp not in INTERPOLATIONS:
            raise ValueError(f"interp must be one of {INTERPOLATIONS}")
        self.model, self.name, self.length, self.loop, self.interp = model, name, float(length), loop, interp
        self.tracks: dict = {}  # (bone, channel) -> {time: (xyz, interp)}

    @property
    def L(self):
        return self.length

    def _track(self, bone, channel, keys, interp):
        bone = bone.name if isinstance(bone, Bone) else bone
        items = keys.items() if isinstance(keys, dict) else keys
        track = self.tracks.setdefault((bone, channel), {})
        for t, xyz in items:
            t = round(float(t), 4)
            track[t] = (tuple(float(v) for v in xyz), interp or self.interp)
        return self

    def rot(self, bone, keys, interp=None):
        """Rotation keyframes ``{time: (x, y, z)}`` in degrees (Blockbench animation signs)."""
        return self._track(bone, "rotation", keys, interp)

    def pos(self, bone, keys, interp=None):
        """Position offsets ``{time: (x, y, z)}`` in units (x mirrored, see module docs)."""
        return self._track(bone, "position", keys, interp)

    def scale(self, bone, keys, interp=None):
        return self._track(bone, "scale", keys, interp)

    def clear(self, bone, channel=None):
        bone = bone.name if isinstance(bone, Bone) else bone
        for key in [k for k in self.tracks if k[0] == bone and (channel is None or k[1] == channel)]:
            del self.tracks[key]
        return self

    def to_json(self):
        animators = {}
        for (bone, channel), frames in self.tracks.items():
            b = self.model.bones.get(bone)
            if b is None:
                continue  # reported by validate()
            entry = animators.setdefault(b.uuid, {"name": bone, "type": "bone", "rotation_global": False,
                                                  "quaternion_interpolation": False, "keyframes": []})
            for i, t in enumerate(sorted(frames)):
                xyz, interp = frames[t]
                entry["keyframes"].append({
                    "channel": channel,
                    "data_points": [{"x": _str_num(xyz[0]), "y": _str_num(xyz[1]), "z": _str_num(xyz[2])}],
                    "uuid": self.model.uid("keyframe", f"{self.name}/{bone}/{channel}/{i}"),
                    "time": _num(t), "color": -1, "interpolation": interp})
        return {"uuid": self.model.uid("animation", self.name), "name": self.name, "loop": self.loop,
                "override": False, "length": _num(self.length), "snapping": 24, "selected": False,
                "anim_time_update": "", "blend_weight": "", "start_delay": "", "loop_delay": "",
                "animators": animators}


class Model:
    """A BetterModel entity model.  ``root`` exists from the start.

    texture_size: atlas size in pixels (64/128/256 square or 2:1); density: texels per unit
    (1 = vanilla).  bone_budget/cube_budget: (warn_above, error_above)."""

    def __init__(self, name, texture_size=(128, 128), *, density=1, bone_budget=(24, 40), cube_budget=(60, 120)):
        if not name or any(ch not in "abcdefghijklmnopqrstuvwxyz0123456789_" for ch in name):
            raise ValueError("model name must be lowercase [a-z0-9_] (it is the BetterModel key)")
        self.name = name
        self.tex_w, self.tex_h = texture_size
        self.density = density
        self.bone_budget, self.cube_budget = bone_budget, cube_budget
        self.bones: dict = {}
        self.cubes: list = []
        self.animations: dict = {}
        self._errors: list = []
        self.bone("root", None, (0, 0, 0))

    def uid(self, kind, name) -> str:
        return str(uuid.uuid5(NAMESPACE, f"{self.name}/{kind}/{name}"))

    def _bone(self, bone) -> Bone:
        if isinstance(bone, Bone):
            return bone
        if bone not in self.bones:
            raise ValueError(f"unknown bone {bone!r}")
        return self.bones[bone]

    # -- construction
    def bone(self, name, parent="root", pivot=(0, 0, 0), rotation=None) -> Bone:
        if name in self.bones:
            raise ValueError(f"duplicate bone name {name!r}")
        if parent is None and self.bones:
            raise ValueError("only 'root' may be top-level; pass parent='root' or another bone")
        b = Bone(self, name, None, pivot, rotation)
        if parent is not None:
            p = self._bone(parent)
            b.parent = p
            p.children.append(b)
        self.bones[name] = b
        return b

    def cube(self, bone, name, frm, to, *, inflate=0, rotation=None, pivot=None, uv=None, mirror=False) -> Cube:
        """Axis-aligned box from ``frm`` to ``to`` (units).  ``uv=<Cube>`` reuses that cube's
        texture (same size required); ``mirror=True`` mirrors it left/right."""
        frm, to = [min(a, b) for a, b in zip(frm, to)], [max(a, b) for a, b in zip(frm, to)]
        if any(b - a <= 0 for a, b in zip(frm, to)):
            raise ValueError(f"cube {name!r} has a zero/negative size; use plane() for flat quads")
        if mirror and uv is None:
            raise ValueError("mirror=True needs uv=<cube to mirror>")
        return self._add_cube(self._bone(bone), name, _vec(frm), _vec(to), inflate, rotation, pivot, uv, mirror, None, "cube")

    def plane(self, bone, name, frm, to, *, double_sided=True, rotation=None, pivot=None) -> Cube:
        """Zero-thickness quad (one axis of frm/to equal).  Paint ``.front``; the back shows
        the same pixels (mirrored as seen from behind) when ``double_sided``.  Use alpha for
        the outline: wings, manes, fins, cloth, blades, leaves, flames."""
        frm, to = [min(a, b) for a, b in zip(frm, to)], [max(a, b) for a, b in zip(frm, to)]
        flat = [i for i in range(3) if abs(to[i] - frm[i]) < 1e-9]
        if len(flat) != 1:
            raise ValueError(f"plane {name!r} needs exactly one flat axis, got from={frm} to={to}")
        axis = "xyz"[flat[0]]
        c = self._add_cube(self._bone(bone), name, _vec(frm), _vec(to), 0, rotation, pivot, None, False, axis, "plane")
        c.double_sided = double_sided
        return c

    def _add_cube(self, bone, name, frm, to, inflate, rotation, pivot, share, mirror, plane_axis, kind) -> Cube:
        if any(c.name == name for c in self.cubes):
            raise ValueError(f"duplicate cube name {name!r}")
        c = Cube(self, bone, name, list(frm), list(to), inflate, rotation, pivot, share, mirror, plane_axis, kind)
        c.double_sided = True
        if share is not None:
            if share.plane_axis != plane_axis or any(share.face_size(f) != c.face_size(f) for f in FACES):
                raise ValueError(f"{name}: uv sharing needs the same size as {share.name}")
            if share.share is not None:
                raise ValueError("share the original cube, not a copy")
        bone.cubes.append(c)
        self.cubes.append(c)
        return c

    def animation(self, name, length, loop="loop", interp="catmullrom") -> Animation:
        if name in self.animations:
            raise ValueError(f"duplicate animation {name!r}")
        a = Animation(self, name, length, loop, interp)
        self.animations[name] = a
        return a

    # -- geometry queries
    def rest_matrix(self, bone: Bone) -> np.ndarray:
        m = affine(bone.rotation, bone.pivot)
        return m if bone.parent is None else self.rest_matrix(bone.parent) @ m

    def cube_corners(self, c: Cube, matrix=None) -> np.ndarray:
        lo = np.array(c.frm, float) - c.inflate
        hi = np.array(c.to, float) + c.inflate
        pts = np.array([[x, y, z, 1] for x in (lo[0], hi[0]) for y in (lo[1], hi[1]) for z in (lo[2], hi[2])])
        m = (self.rest_matrix(c.bone) if matrix is None else matrix) @ affine(c.rotation, c.origin)
        return (pts @ m.T)[:, :3]

    def bounds(self):
        """Rest-pose (min_xyz, max_xyz) over every cube, bone rotations included."""
        pts = np.vstack([self.cube_corners(c) for c in self.cubes]) if self.cubes else np.zeros((1, 3))
        return pts.min(axis=0), pts.max(axis=0)

    # -- texture
    def _uv_items(self):
        """Packing items: one cross island per cube, single rects for planes/overlay faces."""
        items = []
        for c in self.cubes:
            if c.share is not None:
                continue
            if c.kind == "cube":
                w, h, d = c.face_size("north")[0], c.face_size("north")[1], c.face_size("east")[0]
                items.append((("island", c.name), 2 * d + 2 * w, d + h))
            else:
                for f, face in c._faces.items():
                    if c.kind == "overlay" and face.is_blank():
                        continue
                    items.append((("face", c.name, f), face.w, face.h))
        return items

    def _pack(self):
        items = self._uv_items()
        order = sorted(range(len(items)), key=lambda i: (-items[i][2], -items[i][1], i))
        pos, x, y, shelf = {}, 0, 0, 0
        for i in order:
            key, w, h = items[i]
            if w > self.tex_w:
                raise TextureOverflow(f"{key} is {w}px wide, texture is {self.tex_w}px")
            if x + w > self.tex_w:
                x, y, shelf = 0, y + shelf, 0
            if y + h > self.tex_h:
                used = sum(it[1] * it[2] for it in items)
                raise TextureOverflow(
                    f"{self.name}: atlas {self.tex_w}x{self.tex_h} overflowed ({used} px of islands for "
                    f"{self.tex_w * self.tex_h} px); use texture_size=(256, 256), share textures with "
                    f"mirror_x()/uv=, or drop needless overlays")
            pos[key] = (x, y)
            x += w
            shelf = max(shelf, h)
        return pos

    def _layout(self):
        """-> (atlas image, {cube name: {face: uv list or None}})."""
        pos = self._pack()
        atlas = np.zeros((self.tex_h, self.tex_w, 4), np.uint8)
        own = {}
        for c in self.cubes:
            if c.share is not None:
                continue
            rects = {}
            if c.kind == "cube":
                ix, iy = pos[("island", c.name)]
                w, h, d = c.face_size("north")[0], c.face_size("north")[1], c.face_size("east")[0]
                offsets = {"up": (d, 0), "down": (d + w, 0), "east": (0, d), "north": (d, d),
                           "west": (d + w, d), "south": (2 * d + w, d)}
                for f, (ox, oy) in offsets.items():
                    rects[f] = (ix + ox, iy + oy)
            else:
                for f in c._faces:
                    if ("face", c.name, f) in pos:
                        rects[f] = pos[("face", c.name, f)]
            uvs = {f: None for f in FACES}
            for f, (x, y) in rects.items():
                face = c._faces[f]
                atlas[y:y + face.h, x:x + face.w] = face.px
                if c.kind == "overlay" and face.is_blank():
                    continue
                uvs[f] = [x, y, x + face.w, y + face.h]
            if c.plane_axis:
                front, back, flip = PLANE_FACES[c.plane_axis]
                if uvs[front] and c.double_sided:
                    x1, y1, x2, y2 = uvs[front]
                    uvs[back] = [x2, y1, x1, y2] if flip == "u" else [x1, y2, x2, y1]
            own[c.name] = uvs
        result = dict(own)
        for c in self.cubes:
            if c.share is None:
                continue
            src = own[c.share.name]
            uvs = {}
            for f in FACES:
                sf = {"east": "west", "west": "east"}.get(f, f) if c.mirror else f
                uv = src[sf]
                if uv is not None and c.mirror:
                    uv = [uv[2], uv[1], uv[0], uv[3]]
                uvs[f] = uv
            result[c.name] = uvs
        return Image.fromarray(atlas, "RGBA"), result

    def texture_png(self) -> bytes:
        img, _ = self._layout()
        buf = io.BytesIO()
        img.save(buf, "PNG", optimize=False)
        return buf.getvalue()

    # -- validation
    def validate(self, require=(), strict=False, quiet=False) -> list:
        """Check the authoring contract.  Returns warnings; raises ValidationError on errors
        (or on warnings too with ``strict``).  ``require`` lists animation names that must exist."""
        errors, warnings = [], []
        tops = [b for b in self.bones.values() if b.parent is None]
        if [b.name for b in tops] != ["root"]:
            errors.append(f"top-level bones must be exactly ['root'], got {[b.name for b in tops]}")
        animated = {bone for a in self.animations.values() for (bone, _ch) in a.tracks}
        for b in self.bones.values():
            if b.name != "root" and not b.cubes and not b.children and b.name not in animated:
                errors.append(f"bone {b.name!r} has no cubes, no child bones and no animation")
        names = [c.name for c in self.cubes]
        if len(names) != len(set(names)):
            errors.append("duplicate cube names")
        if set(names) & set(self.bones):
            warnings.append(f"cube names equal to bone names: {sorted(set(names) & set(self.bones))}")
        nb, nc = len(self.bones), len(self.cubes)
        for count, (warn, err), what in ((nb, self.bone_budget, "bones"), (nc, self.cube_budget, "cubes")):
            if count > err:
                errors.append(f"{count} {what} > hard budget {err} (cuboids, not voxels)")
            elif count > warn:
                warnings.append(f"{count} {what} > soft budget {warn}")
        for c in self.cubes:
            size = c.size
            if max(size) > 80:
                errors.append(f"cube {c.name!r} is {max(size):g} units long (> 80, 5 blocks)")
            elif max(size) > 48:
                warnings.append(f"cube {c.name!r} is {max(size):g} units long (> 3 blocks)")
            if c.kind == "cube" and max(size) < 0.75:
                warnings.append(f"cube {c.name!r} is a crumb ({size}); paint detail instead")
            if c.inflate < -min(s for s in size if s > 0) / 2:
                errors.append(f"cube {c.name!r} inflate {c.inflate} inverts it")
            nonzero = [a for a in c.rotation if abs(a) > 1e-6]
            if len(nonzero) > 1 or any(min(abs(a - s) for s in SAFE_ANGLES) > 1e-6 for a in nonzero):
                warnings.append(f"cube {c.name!r} rotation {c.rotation} is outside the Java-safe set "
                                f"(one axis, -45..45 in 22.5 steps); rotate a bone instead")
        for a in self.animations.values():
            if a.length <= 0:
                errors.append(f"animation {a.name!r} has length {a.length}")
            for (bone, channel), frames in a.tracks.items():
                if bone not in self.bones:
                    errors.append(f"animation {a.name!r} animates unknown bone {bone!r}")
                bad = [t for t in frames if t < 0 or t > a.length + 1e-6]
                if bad:
                    errors.append(f"animation {a.name!r} {bone}/{channel} keyframes outside 0..{a.length}: {bad}")
                if a.loop == "loop" and frames and (0.0 in frames) and (round(a.length, 4) in frames):
                    if any(abs(p - q) > 1e-6 for p, q in zip(frames[0.0][0], frames[round(a.length, 4)][0])):
                        warnings.append(f"loop {a.name!r} {bone}/{channel}: first and last keys differ (pops)")
        for name in require:
            if name not in self.animations:
                errors.append(f"required animation {name!r} is missing")
        try:
            _img, uvs = self._layout()
        except TextureOverflow as exc:
            errors.append(str(exc))
            uvs = None
        if uvs:
            for c in self.cubes:
                textured = [f for f in FACES if uvs[c.name][f] is not None]
                if not textured:
                    errors.append(f"cube {c.name!r} has no painted face (a 26.2 client rejects it)")
                if c.share is None and c.kind == "cube":
                    blank = [f for f in c._faces if c._faces[f].is_blank()]
                    if blank:
                        warnings.append(f"cube {c.name!r} has unpainted faces {blank} (they render invisible)")
                if c.kind == "plane" and c.share is None and c.front.is_blank():
                    errors.append(f"plane {c.name!r} is unpainted")
        if self.cubes:
            lo, hi = self.bounds()
            if abs(lo[1]) > 0.5:
                warnings.append(f"lowest point is y={lo[1]:.2f}; ground contact should be y=0")
            if abs((lo[0] + hi[0]) / 2) > 4 or abs((lo[2] + hi[2]) / 2) > 6:
                warnings.append(f"model is off-centre (x {lo[0]:.1f}..{hi[0]:.1f}, z {lo[2]:.1f}..{hi[2]:.1f})")
        if strict and warnings:
            errors.extend("strict: " + w for w in warnings)
        if errors:
            raise ValidationError(f"{self.name}: " + "; ".join(errors))
        if not quiet:
            for w in warnings:
                print(f"  warning [{self.name}]: {w}")
        return warnings

    # -- serialisation
    def to_json(self) -> dict:
        img, uvs = self._layout()
        buf = io.BytesIO()
        img.save(buf, "PNG", optimize=False)
        source = "data:image/png;base64," + base64.b64encode(buf.getvalue()).decode("ascii")
        elements = []
        for i, c in enumerate(self.cubes):
            faces = {}
            for f in FACES:
                uv = uvs[c.name][f]
                faces[f] = {"uv": [_num(v) for v in uv], "texture": 0} if uv else {"uv": [0, 0, 0, 0], "texture": None}
            elements.append({
                "name": c.name, "type": "cube", "uuid": c.uuid, "box_uv": False, "rescale": False,
                "locked": False, "render_order": "default", "allow_mirror_modeling": True,
                "from": _vec(c.frm), "to": _vec(c.to), "autouv": 0, "color": i % 8, "inflate": _num(c.inflate),
                "origin": c.origin, "rotation": c.rotation, "faces": faces, "export": True,
                "visibility": True, "shade": True, "mirror_uv": False, "light_emission": 0})
        groups = [{"name": b.name, "uuid": b.uuid, "export": True, "locked": False, "scope": 0,
                   "origin": b.pivot, "rotation": b.rotation, "color": 0, "reset": False, "shade": True,
                   "mirror_uv": False, "visibility": True, "autouv": 0, "isOpen": True}
                  for b in self.bones.values()]

        def tree(b):
            return {"uuid": b.uuid, "isOpen": True,
                    "children": [c.uuid for c in b.cubes] + [tree(ch) for ch in b.children]}

        lo, hi = self.bounds()
        width = max(hi[0] - lo[0], hi[2] - lo[2]) / 16
        visible = [_num(math.ceil(width * 2) / 2 or 1), _num(math.ceil((hi[1] - max(lo[1], 0)) / 8) / 2 or 1), 0]
        return {
            "meta": {"format_version": "5.0", "model_format": "free", "box_uv": False},
            "name": self.name, "model_identifier": "", "visible_box": visible,
            "variable_placeholders": "", "variable_placeholder_buttons": [],
            "timeline_setups": [], "unhandled_root_fields": {},
            "resolution": {"width": self.tex_w, "height": self.tex_h},
            "elements": elements, "groups": groups, "outliner": [tree(self.bones["root"])],
            "textures": [{
                "path": "", "name": f"{self.name}.png", "folder": "", "namespace": "", "id": "0", "group": "",
                "width": self.tex_w, "height": self.tex_h, "uv_width": self.tex_w, "uv_height": self.tex_h,
                "particle": False, "use_as_default": False, "layers_enabled": False, "sync_to_project": "",
                "render_mode": "default", "render_sides": "auto", "pbr_channel": "color",
                "frame_time": 1, "frame_order_type": "loop", "frame_order": "", "frame_interpolate": False,
                "visible": True, "internal": True, "saved": True, "uuid": self.uid("texture", "0"),
                "relative_path": f"{self.name}.png", "source": source}],
            "animations": [a.to_json() for a in self.animations.values()],
        }

    def save(self, path) -> Path:
        path = Path(path)
        path.parent.mkdir(parents=True, exist_ok=True)
        text = json.dumps(self.to_json(), ensure_ascii=False, separators=(",", ":"))
        path.write_text(text + "\n", encoding="utf-8", newline="\n")
        return path


# ----------------------------------------------------------------------------- animation helpers
# All helpers write keys at 0 and at ``anim.length`` (equal values for loops) and may be
# called first and then tweaked with anim.rot/pos on the same bones (later keys override).

def _cycle(anim, values):
    """values: list of xyz at evenly spaced times 0..L (last == first for loops)."""
    n = len(values) - 1
    return {anim.length * i / n: v for i, v in enumerate(values)}


def walk_biped(anim, *, legs=("leg_left", "leg_right"), shins=None, arms=None, forearms=None,
               body=None, head=None, swing=28, arm_swing=None, knee=30, bob=0.6, sway=3, elbow=12):
    """Two-legged walk: legs swing opposite, arms counter-swing, shins bend on the forward
    pass, body bobs twice per cycle and sways.  Pass only the bones you have."""
    s = swing
    a = s * 0.7 if arm_swing is None else arm_swing
    ll, lr = legs
    anim.rot(ll, _cycle(anim, [(-s, 0, 0), (0, 0, 0), (s, 0, 0), (0, 0, 0), (-s, 0, 0)]))
    anim.rot(lr, _cycle(anim, [(s, 0, 0), (0, 0, 0), (-s, 0, 0), (0, 0, 0), (s, 0, 0)]))
    if shins:
        sl, sr = shins
        anim.rot(sl, _cycle(anim, [(knee * 0.3, 0, 0), (knee * 0.1, 0, 0), (0, 0, 0), (knee, 0, 0), (knee * 0.3, 0, 0)]))
        anim.rot(sr, _cycle(anim, [(0, 0, 0), (knee, 0, 0), (knee * 0.3, 0, 0), (knee * 0.1, 0, 0), (0, 0, 0)]))
    if arms:
        al, ar = arms
        anim.rot(al, _cycle(anim, [(a, 0, 0), (0, 0, 0), (-a, 0, 0), (0, 0, 0), (a, 0, 0)]))
        anim.rot(ar, _cycle(anim, [(-a, 0, 0), (0, 0, 0), (a, 0, 0), (0, 0, 0), (-a, 0, 0)]))
    if forearms:
        fl, fr = forearms
        anim.rot(fl, _cycle(anim, [(-elbow * 0.5, 0, 0), (-elbow, 0, 0), (-elbow * 1.5, 0, 0), (-elbow, 0, 0), (-elbow * 0.5, 0, 0)]))
        anim.rot(fr, _cycle(anim, [(-elbow * 1.5, 0, 0), (-elbow, 0, 0), (-elbow * 0.5, 0, 0), (-elbow, 0, 0), (-elbow * 1.5, 0, 0)]))
    if body:
        anim.pos(body, _cycle(anim, [(0, 0, 0), (0, bob, 0), (0, 0, 0), (0, bob, 0), (0, 0, 0)]))
        anim.rot(body, _cycle(anim, [(2, sway, 0), (2, 0, 0), (2, -sway, 0), (2, 0, 0), (2, sway, 0)]))
    if head:
        anim.rot(head, _cycle(anim, [(0, -sway, 0), (-2, 0, 0), (0, sway, 0), (-2, 0, 0), (0, -sway, 0)]))
    return anim


def walk_quadruped(anim, *, front=("leg_front_left", "leg_front_right"), back=("leg_back_left", "leg_back_right"),
                   swing=24, body=None, head=None, tail=None, bob=0.4, gait="trot"):
    """Four legs: 'trot' moves diagonal pairs together, 'pace' same-side pairs."""
    s = swing
    fwd = _cycle(anim, [(-s, 0, 0), (0, 0, 0), (s, 0, 0), (0, 0, 0), (-s, 0, 0)])
    bwd = _cycle(anim, [(s, 0, 0), (0, 0, 0), (-s, 0, 0), (0, 0, 0), (s, 0, 0)])
    fl, fr = front
    bl, br = back
    anim.rot(fl, fwd)
    anim.rot(fr, bwd)
    anim.rot(bl, bwd if gait == "trot" else fwd)
    anim.rot(br, fwd if gait == "trot" else bwd)
    if body:
        anim.pos(body, _cycle(anim, [(0, 0, 0), (0, bob, 0), (0, 0, 0), (0, bob, 0), (0, 0, 0)]))
    if head:
        anim.rot(head, _cycle(anim, [(2, 0, 0), (-2, 0, 0), (2, 0, 0), (-2, 0, 0), (2, 0, 0)]))
    if tail:
        anim.rot(tail, _cycle(anim, [(0, -10, 0), (0, 0, 0), (0, 10, 0), (0, 0, 0), (0, -10, 0)]))
    return anim


def idle_breathe(anim, *, body=None, chest=None, head=None, arms=(), tail=None, amount=0.35, nod=2.5, arm_out=3):
    """Slow breathing: body bob, chest swell, small head nod, arms drift outward."""
    if body:
        anim.pos(body, _cycle(anim, [(0, 0, 0), (0, amount, 0), (0, 0, 0)]))
    if chest:
        anim.scale(chest, _cycle(anim, [(1, 1, 1), (1.03, 1.02, 1.04), (1, 1, 1)]))
    if head:
        anim.rot(head, _cycle(anim, [(0, 0, 0), (nod, 2, 0), (0, 0, 0), (-nod * 0.4, -2, 0), (0, 0, 0)]))
    if arms:
        al, ar = arms
        anim.rot(al, _cycle(anim, [(0, 0, 0), (0, 0, -arm_out), (0, 0, 0)]))
        anim.rot(ar, _cycle(anim, [(0, 0, 0), (0, 0, arm_out), (0, 0, 0)]))
    if tail:
        anim.rot(tail, _cycle(anim, [(0, -8, 0), (4, 0, 0), (0, 8, 0), (4, 0, 0), (0, -8, 0)]))
    return anim


def attack_swing(anim, arms, *, forearms=(), body=None, head=None, style="overhead", windup=0.35, strike=0.55):
    """One-shot melee.  ``arms``: one bone or a tuple (two-handed).  style: 'overhead' (raise
    above the head, slam down in front), 'swipe' (side slash across the body) or 'thrust'.
    windup/strike are fractions of the clip length."""
    arms = (arms,) if isinstance(arms, (str, Bone)) else tuple(arms)
    L = anim.length
    tw, ts, tr = L * windup, L * strike, L * min(0.95, strike + 0.2)
    pose = {"overhead": ((-165, 0, 0), (-55, 0, 0), (-40, 0, 0)),
            "swipe": ((-80, -40, 0), (-70, 50, 0), (-60, 40, 0)),
            "thrust": ((-40, 0, 0), (-95, 0, 0), (-90, 0, 0))}[style]
    for arm in arms:
        anim.rot(arm, {0: (0, 0, 0), tw: pose[0], ts: pose[1], tr: pose[2], L: (0, 0, 0)}, interp="linear")
    for fa in forearms:
        anim.rot(fa, {0: (0, 0, 0), tw: (-35, 0, 0), ts: (-5, 0, 0), L: (0, 0, 0)}, interp="linear")
    if body:
        lean = {"overhead": ((-12, 0, 0), (18, 0, 0)), "swipe": ((0, -25, 0), (6, 30, 0)),
                "thrust": ((-5, -10, 0), (12, 8, 0))}[style]
        anim.rot(body, {0: (0, 0, 0), tw: lean[0], ts: lean[1], tr: lean[1], L: (0, 0, 0)}, interp="linear")
    if head:
        anim.rot(head, {0: (0, 0, 0), tw: (-12, 0, 0), ts: (10, 0, 0), L: (0, 0, 0)}, interp="linear")
    return anim


def death_fall(anim, *, bone="root", direction="back", lift=None, stagger=8):
    """One-shot collapse: stagger, then tip 90 degrees onto the back/front/side and settle.
    ``lift`` raises the body so it lies on the ground instead of sinking (default: computed
    from the rest-pose bounds of the model)."""
    L = anim.length
    lo, hi = anim.model.bounds()
    axis, sign, default_lift = {"back": (0, -1, hi[2]), "forward": (0, 1, -lo[2]),
                                "left": (2, 1, -lo[0]), "right": (2, -1, hi[0])}[direction]
    lift = max(0.0, float(default_lift)) if lift is None else lift

    def r(angle):
        v = [0.0, 0.0, 0.0]
        v[axis] = angle * sign
        return tuple(v)

    anim.rot(bone, {0: (0, 0, 0), L * 0.18: r(-stagger), L * 0.62: r(92), L * 0.75: r(84), L * 0.88: r(90), L: r(90)},
             interp="linear")
    # lift follows sin(angle) so the body neither hovers nor sinks while it tips over
    anim.pos(bone, {0: (0, 0, 0), L * 0.18: (0, 0, 0), L * 0.4: (0, lift * 0.55, 0), L * 0.62: (0, lift, 0),
                    L: (0, lift, 0)}, interp="linear")
    return anim


def hurt_flinch(anim, *, body=None, head=None, amount=10):
    """Short recoil: lean back then recover."""
    L = anim.length
    if body:
        anim.rot(body, {0: (0, 0, 0), L * 0.3: (-amount, 0, amount * 0.4), L: (0, 0, 0)})
    if head:
        anim.rot(head, {0: (0, 0, 0), L * 0.3: (-amount * 1.2, amount * 0.6, 0), L: (0, 0, 0)})
    return anim


def flap(anim, *, wings=("wing_left", "wing_right"), amount=40, body=None, bob=1.0):
    """Wing beat about Z: wings hinge at the shoulder, left uses negative Z to lift."""
    wl, wr = wings
    anim.rot(wl, _cycle(anim, [(0, 0, amount), (0, 0, -amount * 0.6), (0, 0, amount)]))
    anim.rot(wr, _cycle(anim, [(0, 0, -amount), (0, 0, amount * 0.6), (0, 0, -amount)]))
    if body:
        anim.pos(body, _cycle(anim, [(0, -bob, 0), (0, bob, 0), (0, -bob, 0)]))
    return anim


# ----------------------------------------------------------------------------- loading / CLI

def load_bbmodel(path) -> dict:
    return json.loads(Path(path).read_text(encoding="utf-8"))


def outliner_groups(data):
    """Yield (group_dict, parent_uuid, cube_uuids, depth) over the outliner, supporting both
    the format-5 'groups' list and the older inline outliner groups."""
    groups = {g["uuid"]: g for g in data.get("groups", [])}

    def walk(node, parent, depth):
        g = groups.get(node["uuid"], node)
        cubes = [ch for ch in node.get("children", []) if isinstance(ch, str)]
        yield g, parent, cubes, depth
        for ch in node.get("children", []):
            if isinstance(ch, dict):
                yield from walk(ch, node["uuid"], depth + 1)

    for node in data.get("outliner", []):
        if isinstance(node, dict):
            yield from walk(node, None, 0)


def check_file(path) -> int:
    data = load_bbmodel(path)
    problems = []
    res = data.get("resolution", {})
    print(f"{Path(path).name}: name={data.get('name')!r} format={data.get('meta', {}).get('format_version')} "
          f"resolution={res.get('width')}x{res.get('height')}")
    elements = {e["uuid"]: e for e in data.get("elements", [])}
    tops = [n for n in data.get("outliner", []) if isinstance(n, dict)]
    stray = [n for n in data.get("outliner", []) if isinstance(n, str)]
    top_names = [({g["uuid"]: g for g in data.get("groups", [])}.get(n["uuid"], n)).get("name") for n in tops]
    if top_names != ["root"] or stray:
        problems.append(f"top level is {top_names} (+{len(stray)} loose cubes); contract wants only 'root'")
    for g, _parent, cubes, depth in outliner_groups(data):
        print(f"  {'  ' * depth}{g.get('name')}  pivot={g.get('origin')} rot={g.get('rotation', [0, 0, 0])} cubes={len(cubes)}")
    sizes = {}
    for i, t in enumerate(data.get("textures", [])):
        size = (t.get("uv_width", res.get("width")), t.get("uv_height", res.get("height")))
        sizes[str(i)] = size
        if "id" in t:
            sizes[str(t["id"])] = size
    for e in elements.values():
        if e.get("type", "cube") != "cube":
            continue
        if set(e.get("faces", {})) != set(FACES):
            problems.append(f"cube {e.get('name')} does not have all six faces")
        textured = 0
        for f, fd in e.get("faces", {}).items():
            if fd.get("texture") is None:
                continue
            textured += 1
            w, h = sizes.get(str(fd["texture"]), (res.get("width"), res.get("height")))
            uv = fd.get("uv", [0, 0, 0, 0])
            if any(v < 0 for v in uv) or uv[0] > w or uv[2] > w or uv[1] > h or uv[3] > h:
                problems.append(f"cube {e.get('name')} face {f} uv {uv} outside {w}x{h}")
        if not textured:
            problems.append(f"cube {e.get('name')} has no textured face")
    kinds = {}
    for e in elements.values():
        kinds[e.get("type", "cube")] = kinds.get(e.get("type", "cube"), 0) + 1
    print(f"  bones={len(list(outliner_groups(data)))} elements={len(elements)} {kinds}")
    for a in data.get("animations", []):
        print(f"  animation {a['name']!r}: length={a.get('length')} loop={a.get('loop')} tracks={len(a.get('animators', {}))}")
    for p in problems:
        print("  PROBLEM:", p)
    return 1 if problems else 0


def main(argv):
    if len(argv) >= 2 and argv[0] == "check":
        return max(check_file(p) for p in argv[1:])
    print(__doc__.split("CLI\n---")[-1].strip())
    return 2


if __name__ == "__main__":
    sys.exit(main(sys.argv[1:]))
