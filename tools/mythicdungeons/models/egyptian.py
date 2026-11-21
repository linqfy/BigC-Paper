"""Egyptian mythology creatures for the mythic dungeons (Ammit, Apophis, guardian mummy).

Same approach as the Minotaur: a modest number of stretched cuboids, relief from overlay
layers and alpha planes, detail painted into the texture.  Model faces north (-Z); the
creature's right is +X.
"""
import sys
from pathlib import Path

import numpy as np

sys.path.insert(0, str(Path(__file__).resolve().parents[1]))
from bbkit import (Model, attack_swing, death_fall, idle_breathe, mix, ramp,  # noqa: E402
                   walk_biped, walk_quadruped)

GOLD = "#e0b13c"
GOLD_D = "#9a6e1e"
LAPIS = "#2a4fa8"
TURQ = "#2fb5a6"
CARNELIAN = "#b8321f"
BLACK = "#14101a"


def speckle(face, color, p=0.2):
    """Recolour a random share of the already painted pixels (keeps transparency)."""
    ys, xs = np.nonzero(face.mask())
    for y, x in zip(ys, xs):
        if face.rng.random() < p:
            face.pixel(int(x), int(y), color)
    return face


def fur(cube, base, dark, density=0.3, length=(2, 3)):
    cube.paint(base, noise=0.05, jitter=0.2)
    for f in cube.sides():
        f.strands(dark, density=density, length=length)
    return cube


def scaly(cube, base, dark, hi=None, size=3):
    cube.paint(base, noise=0.04, jitter=0.15)
    for f in cube.faces():
        f.scales(dark, size=size, highlight=hi)
    return cube


def wrap(face, base, slope=1, width=3, x=0, y=0, w=None, h=None):
    """Diagonal linen bandage strips (slanted bands, dark seam, light edge, tone per band)."""
    pal = ramp(base, 4)
    x0, y0, x1, y1 = face._region(x, y, w, h)
    tone = {}
    for yy in range(y0, y1):
        for xx in range(x0, x1):
            u = (yy - y0) + ((xx - x0) * slope) // 2
            band = u // width
            r = u % width
            if band not in tone:
                tone[band] = face.rng.choice((1, 2, 2, 3))
            if r == width - 1:
                c = pal[0]
            elif r == 0:
                c = pal[min(3, tone[band] + 1)]
            else:
                c = pal[tone[band]]
            face.pixel(xx, yy, c)
    return face


# =====================================================================================
# AMMIT - crocodile head, lion forequarters and mane, hippo hindquarters
# =====================================================================================
LION = "#c18a45"
LION_D = "#7e4f26"
LION_L = "#e0b878"
MANE = "#5e3217"
MANE_HI = "#a8662c"
HIPPO = "#7d6872"
HIPPO_D = "#4f3f4c"
CROC = "#566a2e"
CROC_D = "#2f3d1a"
CROC_BELLY = "#cfc690"
TOOTH = "#f1e9cc"
AMBER = "#ffc928"
MOUTH = "#a8324a"


def build_ammit():
    m = Model("bigcasares_myth_ammit", texture_size=(256, 128))

    body = m.bone("body", pivot=(0, 14, 0))
    chest = m.bone("chest", body, pivot=(0, 16, -6))
    head = m.bone("head", chest, pivot=(0, 20, -12))
    jaw = m.bone("jaw", head, pivot=(0, 18, -14))
    fl = m.bone("leg_front_left", body, pivot=(-5, 16, -9))
    fr = m.bone("leg_front_right", body, pivot=(5, 16, -9))
    bl = m.bone("leg_back_left", body, pivot=(-7.5, 18, 8.5))
    br = m.bone("leg_back_right", body, pivot=(7.5, 18, 8.5))
    tail = m.bone("tail", body, pivot=(0, 19, 12))
    tail2 = m.bone("tail_tip", tail, pivot=(0, 18, 21))

    # --- lion forequarters + mane
    ch = fur(m.cube(chest, "foreparts", (-7, 10, -12), (7, 25, 0)), LION, LION_D)
    ch.north.rect(4, 8, 6, 6, LION_L, noise=0.05)  # pale chest
    ch.south.darken(0.1)
    mane = ch.overlay(0.7, "mane")
    mane.up.fill(MANE, noise=0.12)
    mane.up.dither(MANE_HI, density=0.3, pattern="noise")
    for f, depth in ((mane.east, (4, 8)), (mane.west, (4, 8)), (mane.south, (3, 6))):
        f.jagged(MANE, edge="top", depth=depth)
        speckle(f, MANE_HI, 0.2)
        speckle(f, mix(MANE, "#000", 0.4), 0.15)
        f.strands(mix(MANE, "#000", 0.5), density=0.25, length=(2, 4), y=0, h=6)
    ruff = m.plane(chest, "mane_ruff", (-11.5, 8, -12.4), (11.5, 34, -12.4))
    rf = ruff.front
    mid = (rf.w - 1) / 2
    for yy in range(rf.h):  # fan-shaped lion ruff: wide behind the head, tapering toward the throat
        t = yy / rf.h
        half = (rf.w / 2) * (0.35 + 0.65 * (1 - abs(t - 0.55) / 0.55) ** 0.7) if t < 1 else 0
        for xx in range(rf.w):
            if abs(xx - mid) <= half:
                rf.pixel(xx, yy, MANE)
    speckle(rf, MANE_HI, 0.25)
    speckle(rf, mix(MANE, "#000", 0.45), 0.22)
    rf.strands(mix(MANE, "#000", 0.5), density=0.3, length=(3, 6))
    rf.tatter(5, edge="bottom", chance=0.7)
    rf.tatter(3, edge="top", chance=0.8)
    rf.tatter(2, edge="left", chance=0.6)
    rf.tatter(2, edge="right", chance=0.6)
    breast = m.cube(chest, "breastplate", (-5.5, 11, -12.9), (5.5, 16.5, -12))
    breast.paint(GOLD, jitter=0.1)
    bn = breast.north
    bn.rect(0, 0, 11, 1, GOLD_D)
    bn.rect(0, 1, 11, 1, LAPIS)
    bn.rect(0, 2, 11, 1, TURQ)
    bn.rect(0, 3, 11, 1, CARNELIAN)
    for xx in range(0, 11, 2):
        bn.pixel(xx, 4, LAPIS)
    bn.pixel(5, 5, "#fff2b0")
    bn.tatter(1, edge="bottom", chance=0.5)

    # --- hippo hindquarters
    barrel = m.cube(body, "barrel", (-8.5, 9, -1), (8.5, 22, 12))
    barrel.paint(HIPPO, noise=0.05, jitter=0.2)
    for f in barrel.faces():
        f.cracks(HIPPO_D, n=3, length=5)
        f.spots(mix(HIPPO, "#d99aa0", 0.35), n=4, size=(1, 2))
    for f in barrel.sides():
        f.gradient(HIPPO, mix(HIPPO, "#d8a8a8", 0.45), steps=5, y=f.h // 2)
        f.noise(0.06)
    barrel.down.fill(mix(HIPPO, "#d8b8b0", 0.5), noise=0.06)
    barrel.up.rect(7, 0, 3, 13, HIPPO_D)  # dorsal groove
    barrel.up.dither(mix(HIPPO, "#000", 0.2), density=0.5, pattern="bayer", x=6, w=5)
    # skin folds on the flanks
    for f in (barrel.east, barrel.west):
        f.stripe(mix(HIPPO, "#000", 0.3), every=4, axis="v", offset=1, y=1, h=5)

    # --- legs
    def lion_leg(side):
        leg = fur(m.cube(side[0], "foreleg_" + side[1], (3, 4, -11), (7, 16, -7)), LION, LION_D, 0.35)
        leg.north.rect(0, 0, 1, 12, LION_D)
        paw = m.cube(side[0], "paw_" + side[1], (2.6, 0, -12.6), (7.4, 4.2, -6.4))
        paw.paint(LION, jitter=0.18)
        for f in paw.sides():
            f.rect(0, 0, 1, 3, mix(LION, "#000", 0.25))
        paw.north.rect(0, 3, 5, 1, mix(LION, "#000", 0.4))
        for xx in (0, 2, 4):
            paw.north.pixel(xx, 3, "#2a2018").pixel(xx, 2, "#f2ead0")
        arm = leg.overlay(0.35, "armlet_" + side[1])
        for f in arm.sides():
            f.rect(0, 5, f.w, 3, GOLD).rect(0, 5, f.w, 1, mix(GOLD, "#fff", 0.35)).rect(0, 7, f.w, 1, GOLD_D)
            f.pixel(f.w // 2, 6, LAPIS)
        return leg, paw, arm

    leg, paw, arm = lion_leg((fr, "right"))
    leg.mirror_x(fl, "foreleg_left")
    paw.mirror_x(fl, "paw_left")
    arm.mirror_x(fl, "armlet_left")

    thigh = m.cube(br, "thigh_right", (4.5, 9, 5), (10.5, 20, 12))
    thigh.paint(HIPPO, noise=0.05, jitter=0.2)
    for f in thigh.faces():
        f.cracks(HIPPO_D, n=2, length=4)
    low = m.cube(br, "shin_right", (5.2, 2.5, 5.8), (9.8, 9.5, 11.2))
    low.paint(HIPPO_D, jitter=0.15)
    foot = m.cube(br, "foot_right", (5, 0, 3.8), (10, 2.7, 12))
    foot.paint(HIPPO, jitter=0.15)
    for xx in (0, 2, 4):
        foot.north.rect(xx, 0, 1, 2, "#2a2024")
    foot.north.stripe(mix(HIPPO, "#000", 0.35), every=2, axis="v", y=0, h=2, offset=1)
    thigh.mirror_x(bl, "thigh_left")
    low.mirror_x(bl, "shin_left")
    foot.mirror_x(bl, "foot_left")

    # --- crocodile head
    cran = scaly(m.cube(head, "skull", (-5.2, 16.5, -22), (5.2, 25.5, -12)), CROC, CROC_D, "#7a9040")
    cran.north.fill(CROC, noise=0.06)
    cran.north.line(1, 0, 3, 2, CROC_D).line(8, 0, 6, 2, CROC_D)
    cran.east.rect(0, 5, 9, 3, mix(CROC, CROC_BELLY, 0.35))
    cran.west.rect(0, 5, 9, 3, mix(CROC, CROC_BELLY, 0.35))
    band = cran.overlay(0.45, "headband")
    for f in band.sides():
        f.rect(0, 0, f.w, 2, GOLD).rect(0, 0, f.w, 1, mix(GOLD, "#fff", 0.35)).rect(0, 2, f.w, 1, LAPIS)
        f.pixel(f.w // 2, 0, "#fff2b0")
    band.up.rect(0, 0, 8, 1, GOLD).rect(0, 7, 8, 1, GOLD)
    snout = scaly(m.cube(head, "snout", (-3.8, 18, -28.5), (3.8, 24.5, -22)), CROC, CROC_D, "#7a9040")
    tip = scaly(m.cube(head, "snout_tip", (-4.6, 18, -35), (4.6, 24, -28.5)), CROC, CROC_D, "#7a9040")
    for sn in (snout, tip):
        for side in (sn.east, sn.west):
            side.rect(0, side.h - 2, side.w, 2, TOOTH)  # upper teeth row
            for xx in range(0, side.w, 2):
                side.pixel(xx, side.h - 1, mix(TOOTH, "#000", 0.35))
        sn.down.fill(MOUTH, noise=0.05)
    tip.up.rect(1, 0, 1, 2, "#10140a").rect(7, 0, 1, 2, "#10140a")  # nostrils
    tip.north.rect(0, 4, 9, 1, TOOTH)
    brow = m.cube(head, "brow_right", (2.2, 25.5, -21), (5.2, 28, -16.5))
    brow.paint(CROC, jitter=0.15)
    brow.up.fill(mix(CROC, "#000", 0.2)).rect(1, 1, 1, 2, AMBER).rect(2, 1, 1, 2, "#fff6b0")
    brow.east.rect(1, 0, 2, 1, AMBER)
    brow.north.rect(1, 0, 1, 1, AMBER)
    brow.mirror_x(head, "brow_left")
    fang = m.cube(head, "fang_right", (3.9, 15.0, -34.6), (4.9, 18.4, -33.4))
    fang.paint(TOOTH, jitter=0.1)
    fang.mirror_x(head, "fang_left")

    # --- lower jaw
    jw = scaly(m.cube(jaw, "lower_jaw", (-3.8, 15.4, -28.5), (3.8, 18, -14.2)), CROC_BELLY, mix(CROC_BELLY, "#000", 0.25), None)
    jw.up.fill(MOUTH, noise=0.05)
    jw.up.rect(2, 3, 2, 8, "#d86a7c")  # tongue
    for side in (jw.east, jw.west):
        side.rect(0, 0, side.w, 1, TOOTH)
        for xx in range(1, side.w, 2):
            side.pixel(xx, 1, mix(TOOTH, "#000", 0.3))
        side.rect(0, 2, side.w, 1, mix(CROC, CROC_BELLY, 0.5))
    jt = scaly(m.cube(jaw, "jaw_tip", (-4.4, 15.4, -35), (4.4, 18, -28.5)), CROC_BELLY, mix(CROC_BELLY, "#000", 0.25), None)
    jt.up.fill(MOUTH, noise=0.05)
    for side in (jt.east, jt.west):
        side.rect(0, 0, side.w, 1, TOOTH)

    # --- tail (stubby, croc scutes)
    t1 = scaly(m.cube(tail, "tail_base", (-2.8, 15.5, 12), (2.8, 20.5, 21)), CROC, CROC_D, "#7a9040")
    t1.up.stripe(CROC_D, every=3, axis="h", offset=2)
    t2 = scaly(m.cube(tail2, "tail_end", (-1.7, 16.3, 21), (1.7, 19.5, 28.5)), CROC, CROC_D, "#7a9040")

    # --- animations
    idle = m.animation("idle", 2.6, "loop")
    idle_breathe(idle, body="body", head="head", tail="tail", amount=0.35, nod=3)
    idle.rot("jaw", {0: (0, 0, 0), 1.3: (5, 0, 0), 2.6: (0, 0, 0)})
    idle.rot("tail_tip", {0: (0, 10, 0), 1.3: (0, -10, 0), 2.6: (0, 10, 0)})

    walk = m.animation("walk", 1.1, "loop")
    walk_quadruped(walk, front=("leg_front_left", "leg_front_right"), back=("leg_back_left", "leg_back_right"),
                   swing=22, body="body", head="head", tail="tail", bob=0.5, gait="trot")
    L = walk.length
    walk.rot("chest", {0: (1, -3, 0), L / 2: (1, 3, 0), L: (1, -3, 0)})
    walk.rot("tail_tip", {0: (0, 14, 0), L / 2: (0, -14, 0), L: (0, 14, 0)})

    atk = m.animation("attack", 1.2, "once")
    atk.rot("head", {0: (0, 0, 0), 0.42: (-28, 0, 0), 0.56: (14, 0, 0), 0.7: (6, 0, 0), 1.2: (0, 0, 0)}, interp="linear")
    atk.rot("jaw", {0: (0, 0, 0), 0.42: (52, 0, 0), 0.56: (-4, 0, 0), 0.64: (24, 0, 0), 0.72: (-2, 0, 0), 1.2: (0, 0, 0)},
            interp="linear")
    atk.pos("body", {0: (0, 0, 0), 0.42: (0, -1.5, 3), 0.56: (0, 0.5, -7), 0.8: (0, 0, -3), 1.2: (0, 0, 0)}, interp="linear")
    atk.rot("chest", {0: (0, 0, 0), 0.42: (-6, 0, 0), 0.56: (8, 0, 0), 1.2: (0, 0, 0)}, interp="linear")
    atk.rot("leg_front_left", {0: (0, 0, 0), 0.42: (-10, 0, 0), 0.56: (18, 0, 0), 1.2: (0, 0, 0)}, interp="linear")
    atk.rot("leg_front_right", {0: (0, 0, 0), 0.42: (-10, 0, 0), 0.56: (18, 0, 0), 1.2: (0, 0, 0)}, interp="linear")
    atk.rot("tail", {0: (0, 0, 0), 0.5: (-10, 12, 0), 1.2: (0, 0, 0)}, interp="linear")

    death = m.animation("death", 2.0, "once")
    death_fall(death, direction="right")
    death.rot("head", {0: (0, 0, 0), 0.5: (-15, 10, 0), 1.2: (14, 20, 0), 2.0: (14, 20, 0)}, interp="linear")
    death.rot("jaw", {0: (0, 0, 0), 0.8: (30, 0, 0), 2.0: (34, 0, 0)}, interp="linear")
    for n, ang in (("leg_front_left", -25), ("leg_front_right", -30), ("leg_back_left", 20), ("leg_back_right", 25)):
        death.rot(n, {0: (0, 0, 0), 0.6: (ang, 0, 0), 2.0: (ang, 0, 0)}, interp="linear")
    death.rot("tail", {0: (0, 0, 0), 0.8: (0, 25, 0), 2.0: (0, 25, 0)}, interp="linear")
    return m


# =====================================================================================
# APOPHIS - giant chaos serpent on a chain of bones
# =====================================================================================
SCALE = "#3b2260"
SCALE_D = "#1c1030"
SCALE_HI = "#7d52b4"
SAND = "#d9b878"
SAND_D = "#9c7a44"
GLOW = "#ffb21e"
FANG = "#f4ecd2"


def _serpent_body(cube, top=SCALE, belly=SAND, lead=True):
    """Scale top, sandstone belly plates on the flanks' lower third and underside."""
    cube.paint(top, noise=0.05, jitter=0.2)
    for f in cube.faces():
        f.scales(SCALE_D, size=3, highlight=SCALE_HI)
    up = cube.up
    # gold chaos diamonds along the spine
    cx = up.w // 2
    for yy in range(0, up.h, 4):
        up.decal([".g.", "gGg", ".g."], {"g": GOLD_D, "G": GOLD}, x=cx - 1, y=yy)
        up.pixel(cx - 2, yy + 1, SCALE_HI).pixel(cx + 2, yy + 1, SCALE_HI)
    for f in cube.sides():
        rows = max(2, f.h * 2 // 5)
        f.rect(0, f.h - rows, f.w, rows, belly, noise=0.05)
        f.rect(0, f.h - rows, f.w, 1, SAND_D)
        f.stripe(SAND_D, every=3, axis="v", y=f.h - rows + 1, h=rows - 1)
    cube.down.fill(belly, noise=0.05)
    cube.down.stripe(SAND_D, every=3, axis="h", offset=2)
    return cube


def build_apophis():
    m = Model("bigcasares_myth_apophis", texture_size=(256, 128))

    coil = m.bone("coil", pivot=(0, 4, 0))
    # (name, parent, z-start, z-end, width, height) - front chain goes to -Z, rear chain to +Z
    front = [("neck_1", "coil", -4, -11, 10.5, 8.5), ("neck_2", "neck_1", -11, -18, 9.4, 8),
             ("neck_3", "neck_2", -18, -25, 8.4, 7.4), ("neck_4", "neck_3", -25, -32, 7.4, 6.6)]
    rear = [("tail_1", "coil", 4, 11, 10.5, 8.5), ("tail_2", "tail_1", 11, 18, 9, 7.5),
            ("tail_3", "tail_2", 18, 25, 7.3, 6.3), ("tail_4", "tail_3", 25, 32, 5.2, 4.8),
            ("tail_5", "tail_4", 32, 39, 3.2, 3.2)]
    m.cube(coil, "coil_body", (-6, 0, -4.4), (6, 9, 4.4))
    _serpent_body(m.cubes[-1])
    ring = m.cubes[-1].overlay(0.3, "coil_ring")
    for f in ring.sides():
        f.rect(0, 0, f.w, 2, GOLD_D).rect(0, 0, f.w, 1, GOLD)
    ring.up.rect(0, 0, 12, 1, GOLD_D)

    for chain, sign in ((front, -1), (rear, 1)):
        for name, parent, z0, z1, w, h in chain:
            lo, hi = min(z0, z1), max(z0, z1)
            lo -= 0.5 if sign > 0 else 0
            hi += 0.5 if sign < 0 else 0
            pz = z0
            b = m.bone(name, parent, pivot=(0, h / 2 + (0 if h > 4 else 0), pz))
            c = m.cube(b, name + "_c", (-w / 2, 0, lo), (w / 2, h, hi))
            _serpent_body(c)
    tipb = m.bones["tail_5"]
    tipb.cubes[0].north.fill(SCALE_D)
    # rattle-like gold cap on the tail tip
    cap = m.cube(tipb, "tail_tip_cap", (-1.2, 0.4, 38.5), (1.2, 2.8, 42))
    cap.paint(GOLD, jitter=0.12)
    # one plate ring per second segment
    for name in ("neck_2", "tail_2", "tail_4"):
        c = m.bones[name].cubes[0].overlay(0.4, name + "_ring")
        for f in c.sides():
            f.rect(0, 0, f.w, 1, GOLD_D)
        c.up.rect(0, 0, c.up.w, 1, GOLD)

    # --- hood (cobra fan) on the last neck segment
    n3 = m.bones["neck_4"]
    hood = m.plane(n3, "hood", (-12.5, 0, -32.6), (12.5, 18, -32.6))
    hf = hood.front
    W, H = hf.w, hf.h
    cxx, cyy = (W - 1) / 2, H * 0.62
    for yy in range(H):
        for xx in range(W):
            dx = (xx - cxx) / (W / 2)
            dy = (yy - cyy) / (H * 0.62)
            if dx * dx * 1.0 + (dy * dy if yy < cyy else dy * dy * 0.35) <= 1.0:
                t = min(1, dx * dx + dy * dy)
                hf.pixel(xx, yy, mix(SCALE, SCALE_D, t * 0.7))
    speckle(hf, SCALE_HI, 0.12)
    # gold rim and chaos eye-mark
    mask = hf.mask()
    for yy in range(H):
        for xx in range(W):
            if mask[yy, xx]:
                edge = False
                for dx2, dy2 in ((1, 0), (-1, 0), (0, 1), (0, -1)):
                    nx, ny = xx + dx2, yy + dy2
                    if not (0 <= nx < W and 0 <= ny < H) or not mask[ny, nx]:
                        edge = True
                if edge:
                    hf.pixel(xx, yy, GOLD)
    hf.decal(["..ggggg..", ".g.....g.", "g..rrr..g", "g.rGGGr.g", "g..rrr..g", ".g.....g.", "..ggggg.."],
             {"g": GOLD, "r": CARNELIAN, "G": GLOW}, x=int(cxx) - 4, y=2)
    hf.rect(int(cxx) - 1, 9, 3, 6, GOLD_D).rect(int(cxx), 9, 1, 6, GOLD)
    hf.stripe(GOLD_D, every=4, axis="v", offset=1, y=7, h=7, x=2, w=W - 4)
    hood_back = m.cube(n3, "hood_core", (-4.2, 0, -34), (4.2, 10, -31.5))
    hood_back.paint(SCALE, jitter=0.15)
    hood_back.north.rect(0, 0, 9, 10, SCALE_D)

    # --- head
    head = m.bone("head", n3, pivot=(0, 4, -32))
    jaw = m.bone("jaw", head, pivot=(0, 2.5, -33))
    skull = m.cube(head, "skull", (-5, 1.5, -40), (5, 8, -31.5))
    skull.paint(SCALE, jitter=0.18)
    for f in skull.faces():
        f.scales(SCALE_D, size=3, highlight=SCALE_HI)
    skull.up.rect(3, 0, 2, 8, SCALE_D).rect(4, 0, 1, 7, GOLD_D)
    snout = m.cube(head, "snout", (-3.4, 2.5, -45), (3.4, 7, -40))
    snout.paint(SCALE, jitter=0.15)
    snout.up.rect(1, 0, 1, 1, "#0a060e").rect(4, 0, 1, 1, "#0a060e")
    snout.down.fill("#b8304e", noise=0.05)
    for side in (snout.east, snout.west):
        side.rect(0, side.h - 1, side.w, 1, FANG)
        for xx in range(0, side.w, 2):
            side.pixel(xx, side.h - 2, FANG)
    brow = m.cube(head, "brow_right", (4, 6, -39), (5.5, 9, -34.5))
    brow.paint(SCALE_D, jitter=0.1)
    brow.east.rect(0, 0, 4, 2, GLOW).rect(1, 0, 2, 1, "#fff2a0").rect(0, 2, 4, 1, SCALE_D)
    brow.north.rect(0, 0, 1, 2, GLOW)
    brow.up.rect(0, 1, 2, 2, GLOW)
    brow.mirror_x(head, "brow_left")
    fang = m.cube(head, "fang_right", (2.8, 0.2, -44.2), (3.7, 3.4, -43.2))
    fang.paint(FANG, jitter=0.08)
    fang.mirror_x(head, "fang_left")
    jw = m.cube(jaw, "lower_jaw", (-2.9, 0, -44.5), (2.9, 2.5, -33))
    jw.paint(SAND, jitter=0.12)
    jw.up.fill("#b8304e", noise=0.05).rect(1, 3, 3, 8, "#e0707e")
    for side in (jw.east, jw.west):
        side.rect(0, 0, side.w, 1, FANG)
        side.rect(0, 1, side.w, 1, SCALE_D)

    # --- animations (slither: travelling sine along the chain, head leading)
    import math
    chain_order = ["head", "neck_4", "neck_3", "neck_2", "neck_1", "coil", "tail_1", "tail_2", "tail_3", "tail_4", "tail_5"]

    def wave(anim, amp, phase_deg, steps=8, base=None, amp_scale=None):
        L = anim.length
        for i, name in enumerate(chain_order):
            a = amp * (amp_scale[i] if amp_scale else 1.0)
            keys = {}
            for k in range(steps + 1):
                t = L * k / steps
                ang = a * math.sin(2 * math.pi * k / steps - math.radians(phase_deg) * i)
                bx = base.get(name, 0) if base else 0
                keys[t] = (bx, ang, 0)
            anim.rot(name, keys)

    idle = m.animation("idle", 3.0, "loop")
    lift = {"neck_1": -5, "neck_2": -6, "neck_3": -6, "neck_4": -4, "head": 14}
    wave(idle, 3.0, 45, steps=6, base=lift)
    idle.pos("coil", {0: (0, 0, 0), 1.5: (0, 0.25, 0), 3.0: (0, 0, 0)})
    idle.rot("jaw", {0: (0, 0, 0), 0.5: (0, 0, 0), 0.75: (12, 0, 0), 1.2: (0, 0, 0), 3.0: (0, 0, 0)})
    idle.scale("neck_4", {0: (1, 1, 1), 1.5: (1.05, 1.03, 1), 3.0: (1, 1, 1)})

    walk = m.animation("walk", 1.6, "loop")
    wave(walk, 13, 52, steps=8, base={"neck_1": -3, "neck_2": -4, "neck_3": -4, "neck_4": -3, "head": 10},
         amp_scale=[1.0, 0.9, 0.8, 0.7, 0.8, 0.3, 0.9, 1.2, 1.5, 1.8, 2.0])
    walk.pos("coil", {0: (0, 0, 0), 0.8: (0, 0.3, 0), 1.6: (0, 0, 0)})

    atk = m.animation("attack", 1.3, "once")
    atk.rot("neck_1", {0: (-5, 0, 0), 0.5: (-11, 0, 0), 0.62: (4, 0, 0), 0.8: (2, 0, 0), 1.3: (-5, 0, 0)}, interp="linear")
    atk.rot("neck_2", {0: (-6, 0, 0), 0.5: (-13, 0, 0), 0.62: (2, 0, 0), 0.8: (0, 0, 0), 1.3: (-6, 0, 0)}, interp="linear")
    atk.rot("neck_3", {0: (-6, 0, 0), 0.5: (-13, 0, 0), 0.62: (5, 0, 0), 0.8: (3, 0, 0), 1.3: (-6, 0, 0)}, interp="linear")
    atk.rot("neck_4", {0: (-4, 0, 0), 0.5: (-6, 0, 0), 0.62: (7, 0, 0), 0.8: (4, 0, 0), 1.3: (-4, 0, 0)}, interp="linear")
    atk.rot("head", {0: (14, 0, 0), 0.5: (24, 0, 0), 0.62: (-8, 0, 0), 0.8: (-4, 0, 0), 1.3: (14, 0, 0)}, interp="linear")
    atk.rot("jaw", {0: (0, 0, 0), 0.45: (0, 0, 0), 0.52: (46, 0, 0), 0.66: (50, 0, 0), 0.74: (2, 0, 0), 1.3: (0, 0, 0)},
            interp="linear")
    atk.pos("coil", {0: (0, 0, 0), 0.5: (0, 0, 3), 0.62: (0, 0, -4), 1.3: (0, 0, 0)}, interp="linear")
    atk.scale("neck_4", {0: (1, 1, 1), 0.5: (1.15, 1.1, 1), 1.3: (1, 1, 1)}, interp="linear")
    atk.rot("tail_3", {0: (0, 0, 0), 0.5: (0, 22, 0), 0.8: (0, -18, 0), 1.3: (0, 0, 0)}, interp="linear")
    atk.rot("tail_5", {0: (0, 0, 0), 0.5: (0, 30, 0), 0.8: (0, -26, 0), 1.3: (0, 0, 0)}, interp="linear")

    death = m.animation("death", 2.4, "once")
    death.rot("root", {0: (0, 0, 0), 0.5: (0, 0, 40), 1.2: (0, 0, 120), 1.6: (0, 0, 165), 2.0: (0, 0, 180), 2.4: (0, 0, 180)},
              interp="linear")
    death.pos("root", {0: (0, 0, 0), 0.5: (0, 3, 0), 1.2: (0, 7, 0), 2.0: (0, 8.5, 0), 2.4: (0, 8.5, 0)}, interp="linear")
    L = 2.4
    for i, name in enumerate(chain_order):
        death.rot(name, {0: (0, 0, 0), 0.4: (6 if i < 4 else -4, 14 * (-1) ** i, 0), 0.9: (-10, -22 * (-1) ** i, 0),
                         1.5: (4, 12 * (-1) ** i, 0), L: (0, 6 * (-1) ** i, 0)}, interp="linear")
    death.rot("jaw", {0: (0, 0, 0), 0.6: (50, 0, 0), L: (36, 0, 0)}, interp="linear")
    return m


# =====================================================================================
# MUMMY - guardian with gold-and-lapis nemes and collar
# =====================================================================================
LINEN = "#cbb98f"
LINEN_D = "#8f7c58"
FLESH = "#3a2418"
EYEC = "#8cf7d2"


def build_mummy():
    m = Model("bigcasares_myth_mummy", texture_size=(256, 128))

    body = m.bone("body", pivot=(0, 13, 0))
    torso = m.bone("torso", body, pivot=(0, 14.5, 0), rotation=(0, 0, 0))
    head = m.bone("head", torso, pivot=(0, 24.5, 0))
    arm_r = m.bone("arm_right", torso, pivot=(6.5, 23.5, 0))
    fore_r = m.bone("forearm_right", arm_r, pivot=(6.5, 17, 0))
    arm_l = m.bone("arm_left", torso, pivot=(-6.5, 23.5, 0))
    fore_l = m.bone("forearm_left", arm_l, pivot=(-6.5, 17, 0))
    leg_r = m.bone("leg_right", body, pivot=(2.4, 12.5, 0))
    shin_r = m.bone("shin_right", leg_r, pivot=(2.4, 6.5, 0))
    leg_l = m.bone("leg_left", body, pivot=(-2.4, 12.5, 0))
    shin_l = m.bone("shin_left", leg_l, pivot=(-2.4, 6.5, 0))
    rags = m.bone("rags", body, pivot=(0, 12, 2.9))
    kilt = m.bone("kilt", body, pivot=(0, 13.5, -2.9))
    ribbon_r = m.bone("ribbon_right", fore_r, pivot=(9, 15, 0))
    ribbon_l = m.bone("ribbon_left", fore_l, pivot=(-9, 15, 0))

    def bandaged(c, base=LINEN, slope=1, width=3, faces=None):
        for f in (c.faces(*faces) if faces else c.faces()):
            wrap(f, base, slope=slope if f.name not in ("east", "west") else -slope, width=width)
        # stains, grime in the folds
        for f in c.faces():
            speckle(f, mix(base, "#5a4a30", 0.45), 0.05)
        return c

    # --- pelvis / legs
    pelvis = bandaged(m.cube(body, "pelvis", (-4.6, 11.5, -2.6), (4.6, 15.8, 2.6)))
    thigh = bandaged(m.cube(leg_r, "thigh_right", (0.3, 6.5, -2.3), (4.5, 13, 2.3)))
    shin = bandaged(m.cube(shin_r, "lower_leg_right", (0.6, 2, -2), (4.2, 6.7, 2)), slope=-1)
    shin_over = shin.overlay(0.3, "shin_wrap_right")
    for f in shin_over.sides():
        f.rect(0, 1, f.w, 1, LINEN).rect(0, 2, f.w, 1, LINEN_D).rect(0, 3, f.w, 1, LINEN)
        f.jagged(LINEN, edge="bottom", depth=(0, 1))
    foot = bandaged(m.cube(shin_r, "foot_right", (0.2, 0, -4.5), (4.6, 2.2, 2.4)))
    foot.north.rect(0, 0, 5, 2, LINEN_D)
    thigh.mirror_x(leg_l, "thigh_left")
    shin.mirror_x(shin_l, "lower_leg_left")
    shin_over.mirror_x(shin_l, "shin_wrap_left")
    foot.mirror_x(shin_l, "foot_left")

    # --- torso: abdomen + chest, ribs showing through torn wrap
    belly = bandaged(m.cube(torso, "abdomen", (-4.2, 14.5, -2.6), (4.2, 19.5, 2.6)), slope=-1)
    chest = bandaged(m.cube(torso, "chest", (-5.4, 19, -3.3), (5.4, 25, 3.3)))
    cn = chest.north
    cn.rect(1, 1, 4, 3, FLESH)  # torn gap showing dried flesh and ribs
    cn.line(1, 2, 4, 2, mix(FLESH, "#c8b890", 0.45)).line(1, 3, 4, 3, mix(FLESH, "#c8b890", 0.35))
    cn.pixel(5, 1, LINEN_D).pixel(0, 3, LINEN_D).pixel(5, 4, FLESH)
    belly.north.rect(5, 2, 2, 2, FLESH)
    cross = chest.overlay(0.35, "chest_straps")
    cross.north.line(0, 0, 10, 5, LINEN).line(0, 1, 10, 6, LINEN).line(1, 0, 10, 4, LINEN_D)
    cross.north.line(10, 0, 0, 5, LINEN).line(10, 1, 0, 6, LINEN).line(9, 0, 0, 4, LINEN_D)
    cross.south.stripe(LINEN, every=3, axis="h", offset=0).stripe(LINEN_D, every=3, axis="h", offset=1)
    for f in (cross.east, cross.west):
        f.stripe(LINEN, every=4, axis="h", offset=1, width=2).stripe(LINEN_D, every=4, axis="h", offset=3)
    belt = m.cube(torso, "belt", (-4.9, 13.2, -3), (4.9, 15.2, 3))
    belt.paint(GOLD_D, jitter=0.1)
    belt.north.rect(3, 0, 4, 2, GOLD).rect(4, 0, 2, 2, LAPIS).outline(GOLD_D, 3, 0, 4, 2)
    belt.north.stripe(GOLD, every=2, axis="v", x=0, w=3).stripe(GOLD, every=2, axis="v", x=7, w=3)

    # --- collar (usekh), lappets and back tail of the nemes
    collar = m.cube(torso, "collar", (-6.4, 22.4, -4.2), (6.4, 26, 4.2))
    collar.paint(GOLD, jitter=0.08)
    rows = [(GOLD_D,), (LAPIS,), (TURQ,), (CARNELIAN,), ]
    for f in collar.sides():
        f.rect(0, 0, f.w, 1, GOLD)
        f.rect(0, 1, f.w, 1, LAPIS)
        f.rect(0, 2, f.w, 1, TURQ)
        f.rect(0, 3, f.w, 1, CARNELIAN)
        for xx in range(0, f.w, 2):
            f.pixel(xx, 3, GOLD)
        for xx in range(1, f.w, 2):
            f.clear(xx, 3, 1, 1)
    collar.up.fill(GOLD, noise=0.05).rect(3, 3, 8, 6, "#2c1c12")
    collar.up.rect(0, 0, 13, 1, GOLD_D)
    lap = m.cube(torso, "lappet_right", (2.8, 15.5, -4.6), (5.8, 24.8, -3.2))
    lap.paint(GOLD, jitter=0.08)
    for f in lap.faces("north"):
        for yy in range(0, f.h, 2):
            f.rect(0, yy, f.w, 1, LAPIS)
        f.stripe(GOLD_D, every=2, axis="v", offset=1, w=f.w, y=0, h=1)
        f.rect(0, f.h - 1, f.w, 1, GOLD_D)
    lap.mirror_x(torso, "lappet_left")
    back = m.cube(torso, "nemes_back", (-2.4, 14.5, 3.2), (2.4, 26, 4.6))
    back.paint(GOLD, jitter=0.08)
    for f in back.faces("north", "south"):
        for yy in range(0, f.h, 2):
            f.rect(0, yy, f.w, 1, LAPIS)

    # --- head: dark face gap with glowing eyes, bandaged cheeks, nemes overlay
    skull = m.cube(head, "skull", (-4, 24.5, -4), (4, 32.5, 4))
    skull.paint(LINEN, jitter=0.15)
    for f in skull.faces():
        wrap(f, LINEN, slope=1, width=3)
    sn = skull.north
    sn.rect(1, 3, 6, 3, "#0c0806")
    sn.rect(1, 3, 6, 1, mix("#0c0806", LINEN_D, 0.25))
    sn.eyes(4, 4, w=2, h=1, gap=2, glow=EYEC)
    sn.pixel(3, 6, LINEN_D).pixel(5, 6, LINEN_D).pixel(4, 7, LINEN_D)
    sn.jagged(LINEN_D, edge="top", depth=(0, 1))
    sn.rect(0, 6, 1, 2, LINEN).rect(7, 6, 1, 2, LINEN)
    speckle(sn, mix(LINEN, "#5a4a30", 0.5), 0.07)
    nemes = skull.overlay(0.9, "nemes")
    for f in nemes.sides():
        for xx in range(f.w):
            c = GOLD if xx % 2 == 0 else LAPIS
            f.rect(xx, 0, 1, f.h, c)
        f.rect(0, 0, f.w, 1, GOLD_D)
    nemes.north.clear(2, 2, 4, 6)
    nemes.north.clear(0, 2, 2, 6)
    nemes.north.clear(6, 2, 2, 6)
    nemes.north.rect(0, 2, 1, 6, GOLD).rect(7, 2, 1, 6, GOLD)  # temple strips frame the face
    nemes.north.rect(0, 0, 8, 2, GOLD).rect(0, 1, 8, 1, LAPIS)
    nemes.north.pixel(3, 0, CARNELIAN).pixel(4, 0, CARNELIAN).pixel(3, 1, GOLD).pixel(4, 1, GOLD)
    nemes.up.fill(GOLD)
    nemes.up.stripe(LAPIS, every=2, axis="v", offset=1)
    nemes.up.rect(0, 0, 8, 1, GOLD_D)
    for f in (nemes.east, nemes.west, nemes.south):
        f.rect(0, f.h - 1, f.w, 1, GOLD_D)
    flare_r = m.cube(head, "nemes_flare_right", (4.9, 24.6, -2), (6.6, 29.5, 3.4))
    flare_r.paint(GOLD, jitter=0.08)
    for f in flare_r.sides():
        f.stripe(LAPIS, every=2, axis="v", offset=1)
    flare_r.mirror_x(head, "nemes_flare_left")

    # --- arms: wrapped, forward-reaching, gold bracers, claw-like wrapped hands
    up = bandaged(m.cube(arm_r, "upper_arm_right", (4.6, 17.2, -2.2), (8.6, 24.3, 2.2)))
    fo = bandaged(m.cube(fore_r, "forearm_wrap_right", (4.8, 10.8, -2.1), (8.4, 17.4, 2.1)), slope=-1)
    hand = bandaged(m.cube(fore_r, "hand_right", (4.9, 7.5, -2.2), (8.3, 11.2, 2.2)), LINEN_D)
    for xx in range(0, 4, 1):
        hand.north.pixel(xx, 2, LINEN)
    brace = fo.overlay(0.4, "bracer_right")
    for f in brace.sides():
        f.rect(0, 3, f.w, 3, GOLD).rect(0, 3, f.w, 1, mix(GOLD, "#fff", 0.35)).rect(0, 5, f.w, 1, GOLD_D)
        f.pixel(f.w // 2, 4, LAPIS)
    over = up.overlay(0.3, "arm_wrap_right")
    for f in over.sides():
        f.rect(0, 1, f.w, 1, LINEN).rect(0, 2, f.w, 1, LINEN_D)
        f.rect(0, 4, f.w, 1, LINEN).rect(0, 5, f.w, 1, LINEN_D)
        f.jagged(LINEN, edge="bottom", depth=(0, 1))
    up.mirror_x(arm_l, "upper_arm_left")
    fo.mirror_x(fore_l, "forearm_wrap_left")
    hand.mirror_x(fore_l, "hand_left")
    brace.mirror_x(fore_l, "bracer_left")
    over.mirror_x(arm_l, "arm_wrap_left")

    # --- hanging cloth: kilt, back rags, ribbons off the wrists
    kp = m.plane(kilt, "shendyt", (-4.2, 4, -3.1), (4.2, 13.5, -3.1))
    kf = kp.front
    kf.fill(mix(LINEN, "#ffffff", 0.2), noise=0.04)
    kf.stripe(LINEN_D, every=3, axis="v", offset=1)
    kf.rect(0, 0, 9, 1, GOLD).rect(0, 1, 9, 1, LAPIS)
    for xx in range(0, 9, 2):
        kf.pixel(xx, 2, GOLD_D)
    kf.tatter(3)
    rg = m.plane(rags, "back_rags", (-4.4, 2.5, 2.9), (4.4, 12.5, 2.9))
    wrap(rg.front, LINEN, slope=0, width=3)
    rg.front.tatter(4, chance=0.85)
    rg.front.stripe(LINEN_D, every=3, axis="v", offset=2)
    for bone, name, x in ((ribbon_r, "ribbon_right", 8.65), (ribbon_l, "ribbon_left", -8.65)):
        r = m.plane(bone, name + "_cloth", (x, 3, -1.8), (x, 15, 1.6))
        wrap(r.front, LINEN, slope=0, width=3)
        r.front.stripe(LINEN_D, every=4, axis="h", offset=1)
        r.front.tatter(6, chance=0.9)

    # --- animations
    idle = m.animation("idle", 2.8, "loop")
    idle_breathe(idle, body="torso", head="head", arms=("arm_left", "arm_right"), amount=0.25, nod=3)
    idle.rot("arm_right", {0: (-62, 0, 4), 1.4: (-68, 0, 6), 2.8: (-62, 0, 4)})
    idle.rot("arm_left", {0: (-66, 0, -4), 1.4: (-60, 0, -6), 2.8: (-66, 0, -4)})
    idle.rot("forearm_right", {0: (-10, 0, 0), 1.4: (-18, 0, 0), 2.8: (-10, 0, 0)})
    idle.rot("forearm_left", {0: (-16, 0, 0), 1.4: (-8, 0, 0), 2.8: (-16, 0, 0)})
    idle.rot("rags", {0: (0, 0, 0), 1.4: (6, 0, 0), 2.8: (0, 0, 0)})
    idle.rot("kilt", {0: (0, 0, 0), 1.4: (-5, 0, 0), 2.8: (0, 0, 0)})
    idle.rot("ribbon_right", {0: (0, 0, -2), 1.4: (0, 0, 5), 2.8: (0, 0, -2)})
    idle.rot("ribbon_left", {0: (0, 0, 2), 1.4: (0, 0, -5), 2.8: (0, 0, 2)})

    walk = m.animation("walk", 1.4, "loop")
    walk_biped(walk, legs=("leg_left", "leg_right"), shins=("shin_left", "shin_right"),
               arms=("arm_left", "arm_right"), forearms=("forearm_left", "forearm_right"),
               body="body", head="head", swing=20, arm_swing=6, bob=0.6, sway=5, knee=24)
    L = walk.length
    for b in ("arm_right", "arm_left", "forearm_right", "forearm_left", "head"):
        walk.clear(b)
    walk.rot("torso", {0: (9, -3, 0), L / 2: (9, 3, 0), L: (9, -3, 0)})
    walk.rot("arm_right", {0: (-82, 0, 4), L / 2: (-70, 0, 4), L: (-82, 0, 4)})
    walk.rot("arm_left", {0: (-70, 0, -4), L / 2: (-82, 0, -4), L: (-70, 0, -4)})
    walk.rot("forearm_right", {0: (-8, 0, 0), L / 2: (-18, 0, 0), L: (-8, 0, 0)})
    walk.rot("forearm_left", {0: (-18, 0, 0), L / 2: (-8, 0, 0), L: (-18, 0, 0)})
    walk.rot("head", {0: (6, -6, 4), L / 2: (10, 6, -4), L: (6, -6, 4)})
    walk.rot("rags", {0: (-6, 0, 0), L / 4: (6, 0, 0), L / 2: (-6, 0, 0), 3 * L / 4: (6, 0, 0), L: (-6, 0, 0)})
    walk.rot("kilt", {0: (6, 0, 0), L / 4: (-5, 0, 0), L / 2: (6, 0, 0), 3 * L / 4: (-5, 0, 0), L: (6, 0, 0)})
    walk.rot("ribbon_right", {0: (0, 0, -8), L / 2: (0, 0, 8), L: (0, 0, -8)})
    walk.rot("ribbon_left", {0: (0, 0, 8), L / 2: (0, 0, -8), L: (0, 0, 8)})

    attack = m.animation("attack", 1.2, "once")
    attack_swing(attack, ("arm_right", "arm_left"), forearms=("forearm_right", "forearm_left"), body="torso",
                 head="head", style="overhead", windup=0.4, strike=0.55)
    attack.pos("body", {0: (0, 0, 0), 0.48: (0, 0.5, 1.5), 0.66: (0, -2, -3), 1.2: (0, 0, 0)}, interp="linear")
    attack.rot("ribbon_right", {0: (0, 0, 0), 0.48: (0, 0, 20), 0.66: (0, 0, -15), 1.2: (0, 0, 0)}, interp="linear")
    attack.rot("ribbon_left", {0: (0, 0, 0), 0.48: (0, 0, -20), 0.66: (0, 0, 15), 1.2: (0, 0, 0)}, interp="linear")
    attack.rot("leg_right", {0: (0, 0, 0), 0.66: (-16, 0, 0), 1.2: (0, 0, 0)}, interp="linear")
    attack.rot("leg_left", {0: (0, 0, 0), 0.66: (10, 0, 0), 1.2: (0, 0, 0)}, interp="linear")

    death = m.animation("death", 2.2, "once")
    death_fall(death, direction="back")
    death.rot("arm_right", {0: (0, 0, 0), 0.5: (-60, 0, 30), 1.3: (-130, 0, 30), 2.2: (-130, 0, 30)}, interp="linear")
    death.rot("arm_left", {0: (0, 0, 0), 0.5: (-30, 0, -25), 1.3: (-100, 0, -40), 2.2: (-100, 0, -40)}, interp="linear")
    death.rot("head", {0: (0, 0, 0), 0.5: (-20, 0, 0), 1.4: (8, 28, 0), 2.2: (8, 28, 0)}, interp="linear")
    return m


MODELS = {
    "bigcasares_myth_ammit": build_ammit,
    "bigcasares_myth_apophis": build_apophis,
    "bigcasares_myth_mummy": build_mummy,
}
