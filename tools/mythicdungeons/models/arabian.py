"""Arabian mythology creatures for the mythic dungeons (ghul, djinn).

Same approach as the Greek/Egyptian models: a modest number of stretched cuboids, relief from
overlay layers and alpha planes, detail painted into the texture.  Model faces north (-Z);
the creature's right is +X.
"""
import math
import sys
from pathlib import Path

import numpy as np

sys.path.insert(0, str(Path(__file__).resolve().parents[1]))
from bbkit import Model, death_fall, idle_breathe, mix, ramp, walk_biped  # noqa: E402

GOLD = "#e0b13c"
GOLD_D = "#94681c"
GOLD_L = "#fff0a8"


def speckle(face, color, p=0.2):
    """Recolour a random share of the already painted pixels (keeps transparency)."""
    ys, xs = np.nonzero(face.mask())
    for y, x in zip(ys, xs):
        if face.rng.random() < p:
            face.pixel(int(x), int(y), color)
    return face


def cyc(anim, bone, vals, channel="rot"):
    """Evenly spaced keys over the clip; the last value should equal the first for loops."""
    n = len(vals) - 1
    getattr(anim, channel)(bone, {anim.length * i / n: v for i, v in enumerate(vals)})


def sine(anim, bone, amp, phase=0.0, steps=8, base=(0, 0, 0), channel="rot", cosine=(0, 0, 0)):
    """Sine (per axis ``amp``) plus cosine (``cosine``) loop around ``base``; ``phase`` in degrees."""
    vals = []
    for k in range(steps + 1):
        a = 2 * math.pi * (k % steps) / steps - math.radians(phase)
        vals.append(tuple(base[i] + amp[i] * math.sin(a) + cosine[i] * math.cos(a) for i in range(3)))
    cyc(anim, bone, vals, channel)


# =====================================================================================
# GHUL - hunched grave-haunting desert ghoul
# =====================================================================================
ASH = "#8e9383"
ASH_D = "#5c6458"
VEIN = "#566878"
LINEN = "#cdc1a0"
LINEN_D = "#8b7d5a"
STAIN = "#6a5638"
BLOOD = "#6b2a24"
BONE = "#e6dec4"
CLAWC = "#d9d1b4"
HOOF = "#2a2420"
GLOWY = "#dcec55"
MOUTH = "#5a1c24"
HAIR = "#2f2a26"


def ash(cube, base=ASH, blotch=2):
    cube.paint(base, noise=0.05, jitter=0.2)
    for f in cube.faces():
        f.spots(mix(base, VEIN, 0.45), n=blotch, size=(1, 2))
    return cube


def rag(face, base=LINEN, tatter=3, stains=0.08):
    face.fill(base, noise=0.05)
    face.stripe(mix(base, LINEN_D, 0.55), every=3, axis="v", offset=1)
    speckle(face, STAIN, stains)
    speckle(face, BLOOD, 0.025)
    face.tatter(tatter, edge="bottom", chance=0.9)
    face.tatter(2, edge="left", chance=0.5)
    face.tatter(2, edge="right", chance=0.5)
    return face


def build_ghul():
    m = Model("bigcasares_myth_ghul", texture_size=(128, 128))

    body = m.bone("body", pivot=(0, 16, 0))
    torso = m.bone("torso", body, pivot=(0, 16.5, 0), rotation=(-24, 0, 0))
    head = m.bone("head", torso, pivot=(0, 27.5, -0.5), rotation=(-8, 0, 0))
    jaw = m.bone("jaw", head, pivot=(0, 27.5, -4.5))
    arm_r = m.bone("arm_right", torso, pivot=(6.5, 27, 0), rotation=(24, 0, 0))
    fore_r = m.bone("forearm_right", arm_r, pivot=(6.5, 19.5, 0), rotation=(8, 0, 0))
    arm_l = m.bone("arm_left", torso, pivot=(-6.5, 27, 0), rotation=(24, 0, 0))
    fore_l = m.bone("forearm_left", arm_l, pivot=(-6.5, 19.5, 0), rotation=(8, 0, 0))
    leg_r = m.bone("leg_right", body, pivot=(2.5, 16, 0), rotation=(10, 0, 0))
    shin_r = m.bone("shin_right", leg_r, pivot=(2.5, 9, 0), rotation=(-22, 0, 0))
    leg_l = m.bone("leg_left", body, pivot=(-2.5, 16, 0), rotation=(10, 0, 0))
    shin_l = m.bone("shin_left", leg_l, pivot=(-2.5, 9, 0), rotation=(-22, 0, 0))
    shroud = m.bone("shroud", body, pivot=(0, 17, 0))

    # --- pelvis and thin donkey-hoofed legs
    pelvis = ash(m.cube(body, "pelvis", (-4, 13, -2.5), (4, 17.5, 2.5)))
    pelvis.down.fill(ASH_D)
    thigh = ash(m.cube(leg_r, "thigh_right", (1, 9, -2), (4, 16, 2)))
    shin = ash(m.cube(shin_r, "lower_leg_right", (1.3, 3, -1.4), (3.7, 9, 1.4)))
    for f in shin.sides():
        f.strands(ASH_D, density=0.4, length=(2, 4))
    hoof = m.cube(shin_r, "hoof_right", (1, 0, -2.4), (4, 3, 1.8))
    hoof.paint(HOOF, jitter=0.12)
    hoof.north.rect(1, 0, 1, 3, "#0c0806").rect(0, 0, 3, 1, mix(HOOF, "#fff", 0.2))
    hoof.up.fill(mix(HOOF, ASH, 0.3))
    for t, s in ((thigh, thigh), (shin, shin)):
        t.north.rect(0, 0, 1, t.north.h, ASH_D)
    thigh.mirror_x(leg_l, "thigh_left")
    shin.mirror_x(shin_l, "lower_leg_left")
    hoof.mirror_x(shin_l, "hoof_left")

    # --- sunken belly, ribcage chest
    abd = ash(m.cube(torso, "abdomen", (-3.6, 16.5, -2.2), (3.6, 22, 2.2)), ASH_D)
    abd.north.rect(2, 1, 3, 4, mix(ASH_D, "#000", 0.3))
    abd.north.line(1, 4, 5, 2, "#9a5a50").pixel(3, 2, "#9a5a50").pixel(2, 3, "#9a5a50")  # old scar
    chest = ash(m.cube(torso, "chest", (-5, 21.5, -3), (5, 28.5, 3.2)))
    rib = mix(ASH, BONE, 0.38)
    gap = mix(ASH_D, "#000", 0.35)
    cn = chest.north
    for yy in (1, 3, 5):
        cn.rect(1, yy, 3, 1, gap).rect(6, yy, 3, 1, gap)
        cn.rect(1, yy + 1, 3, 1, rib).rect(6, yy + 1, 3, 1, rib)
    cn.rect(4, 0, 2, 7, mix(ASH, BONE, 0.2))
    cs = chest.south
    for yy in range(0, 7, 2):  # spine knobs
        cs.rect(4, yy, 2, 1, mix(ASH, BONE, 0.45))
        cs.pixel(3, yy, ASH_D).pixel(6, yy, ASH_D)
    cs.rect(0, 1, 3, 3, ASH_D).rect(7, 1, 3, 3, ASH_D)  # shoulder blades
    hair = chest.overlay(0.35, "back_hair")
    hair.south.strands(HAIR, density=0.5, length=(2, 4), y=0, h=5)
    hair.up.dither(HAIR, density=0.5, pattern="noise")
    for f in (hair.east, hair.west):
        f.strands(HAIR, density=0.3, length=(2, 3), y=0, h=4)

    # --- head: hyena skull, snout, jaw, ears, mane
    skull = ash(m.cube(head, "skull", (-3, 28, -5), (3, 34, 1)))
    sk = skull.north
    sk.rect(0, 1, 6, 1, ASH_D)
    sk.eyes(3, 3, w=1, h=1, gap=2, glow=GLOWY, socket="#1d1d22")
    sk.pixel(0, 4, ASH_D).pixel(5, 4, ASH_D).pixel(2, 0, ASH_D).pixel(3, 0, ASH_D)
    for f in (skull.east, skull.west):
        f.rect(1, 2, 2, 2, "#1d1d22").pixel(1, 3, GLOWY)
    mane = skull.overlay(0.45, "skull_mane")
    mane.up.fill(HAIR, noise=0.1).dither(mix(HAIR, "#8a8a7a", 0.3), density=0.25, pattern="noise")
    mane.south.fill(HAIR, noise=0.1).jagged(HAIR, edge="bottom", depth=(2, 5))
    for f in (mane.east, mane.west):
        f.jagged(HAIR, edge="top", depth=(1, 3))
        f.jagged(HAIR, edge="right", depth=(1, 3))
    snout = ash(m.cube(head, "snout", (-2, 28, -9), (2, 32, -5)))
    snout.north.rect(1, 0, 2, 1, "#14100e").rect(0, 3, 4, 1, mix(MOUTH, "#000", 0.2))
    snout.up.rect(1, 0, 2, 1, "#14100e")
    for f in (snout.east, snout.west):
        f.rect(0, 3, f.w, 1, BONE)
        for xx in range(0, f.w, 2):
            f.pixel(xx, 3, mix(BONE, "#000", 0.3))
        f.rect(0, 2, f.w, 1, mix(ASH, "#000", 0.35))
    snout.down.fill(MOUTH)
    jw = m.cube(jaw, "lower_jaw", (-1.8, 26, -9), (1.8, 28, -4.2))
    jw.paint(ASH_D, jitter=0.12)
    jw.up.fill(MOUTH, noise=0.05)
    jw.up.rect(1, 1, 2, 3, "#a8505a")  # tongue
    for f in (jw.east, jw.west):
        f.rect(0, 0, f.w, 1, BONE)
    jw.north.rect(0, 0, 4, 1, BONE)
    fang_up = m.cube(head, "fang_up_right", (1.3, 25.4, -8.8), (2.1, 28, -8))
    fang_up.paint(BONE, jitter=0.08)
    fang_up.mirror_x(head, "fang_up_left")
    fang_lo = m.cube(jaw, "fang_low_right", (0.6, 27.6, -8.8), (1.4, 30.2, -8))
    fang_lo.paint(BONE, jitter=0.08)
    fang_lo.mirror_x(jaw, "fang_low_left")
    ear = m.cube(head, "ear_right", (2.8, 33, -2.2), (4.6, 37.5, -0.6), rotation=(0, 0, -22.5))
    ear.paint(ASH, jitter=0.15)
    ear.north.rect(0, 1, 1, 3, mix(ASH, "#a06a60", 0.5))
    ear.mirror_x(head, "ear_left")
    neck = ash(m.cube(torso, "neck", (-1.8, 27, -2), (1.8, 30, 1)))

    # --- long arms with clawed hands
    upper = ash(m.cube(arm_r, "upper_arm_right", (5.2, 19.5, -1.6), (7.8, 27.5, 1.6)))
    fore = ash(m.cube(fore_r, "lower_arm_right", (5.4, 10.5, -1.5), (7.6, 19.8, 1.5)))
    for c in (upper, fore):
        for f in c.sides():
            f.strands(ASH_D, density=0.3, length=(2, 4))
    hand = ash(m.cube(fore_r, "hand_right", (5, 7, -1.8), (8, 10.5, 1.8)))
    hand.north.rect(1, 1, 1, 2, ASH_D)
    claws = m.cube(fore_r, "claws_right", (5.1, 3, -1.8), (7.9, 7.1, 1.8))
    claws.paint(CLAWC, jitter=0.1)
    for f in claws.sides():
        f.clear()
        cols = (0, f.w - 1) if f.w > 3 else (0, 2)
        for xx in cols:
            for yy in range(f.h):
                f.pixel(xx, yy, mix(CLAWC, "#3a3226", yy / (f.h - 1) * 0.65))
        f.rect(0, 0, f.w, 1, ASH_D)
    claws.north.pixel(1, 0, ASH_D).pixel(1, 1, mix(CLAWC, "#3a3226", 0.2))
    upper.mirror_x(arm_l, "upper_arm_left")
    fore.mirror_x(fore_l, "lower_arm_left")
    hand.mirror_x(fore_l, "hand_left")
    claws.mirror_x(fore_l, "claws_left")

    # --- burial shroud: sash across the chest, hems, strips off the forearms
    sash = m.plane(torso, "sash", (-5.6, 17.5, -3.4), (5.6, 28.5, -3.4))
    sf = sash.front
    for yy in range(sf.h):
        for xx in range(sf.w):
            d = abs(yy / sf.h - xx / sf.w)
            if d < 0.2 + 0.08 * sf.rng.random():
                sf.pixel(xx, yy, LINEN)
    sf.noise(0.05)
    speckle(sf, STAIN, 0.1)
    speckle(sf, BLOOD, 0.03)
    for xx in range(0, sf.w, 3):
        sf.rect(xx, 0, 1, sf.h, mix(LINEN, LINEN_D, 0.5))
    for yy in range(sf.h):  # darker edge on the band
        pass
    sf.tatter(2, edge="bottom", chance=0.6)
    hem_f = m.plane(shroud, "hem_front", (-4.4, 5, -2.95), (4.4, 17, -2.95))
    rag(hem_f.front)
    hem_f.front.rect(0, 0, 9, 1, LINEN_D)
    hem_f.front.clear(2, 4, 1, 12).clear(5, 6, 1, 12).clear(7, 3, 1, 4)
    hem_b = m.plane(shroud, "hem_back", (-4.4, 3, 2.95), (4.4, 17, 2.95))
    rag(hem_b.front, tatter=5)
    hem_b.front.rect(0, 0, 9, 1, LINEN_D)
    hem_b.front.clear(3, 5, 1, 12).clear(6, 3, 1, 12)
    strip_r = m.plane(fore_r, "strip_right", (7.9, 5, -2.2), (7.9, 18, 2.2))
    rag(strip_r.front, tatter=5)
    strip_r.front.rect(0, 0, 4, 2, LINEN_D)
    strip_r.mirror_x(fore_l, "strip_left")

    # --- animations
    idle = m.animation("idle", 2.4, "loop")
    idle_breathe(idle, body="body", head="head", amount=0.3, nod=4)
    sine(idle, "arm_right", (3, 0, 0), 0, 4, base=(0, 0, 3))
    sine(idle, "arm_left", (3, 0, 0), 180, 4, base=(0, 0, -3))
    cyc(idle, "forearm_right", [(-8, 0, 0), (-16, 0, 0), (-8, 0, 0)])
    cyc(idle, "forearm_left", [(-14, 0, 0), (-6, 0, 0), (-14, 0, 0)])
    cyc(idle, "jaw", [(10, 0, 0), (22, 0, 0), (10, 0, 0)])
    cyc(idle, "shroud", [(0, 0, 0), (5, 0, 0), (0, 0, 0)])
    cyc(idle, "torso", [(0, -3, 0), (2, 0, 0), (0, 3, 0), (2, 0, 0), (0, -3, 0)])

    walk = m.animation("walk", 1.0, "loop")
    walk_biped(walk, legs=("leg_left", "leg_right"), shins=("shin_left", "shin_right"), body="body",
               swing=26, knee=30, bob=1.0, sway=6)
    cyc(walk, "arm_right", [(-14, 0, 6), (6, 0, 3), (18, 0, 3), (6, 0, 3), (-14, 0, 6)])
    cyc(walk, "arm_left", [(18, 0, -3), (6, 0, -3), (-14, 0, -6), (6, 0, -3), (18, 0, -3)])
    cyc(walk, "forearm_right", [(-18, 0, 0), (-10, 0, 0), (-4, 0, 0), (-10, 0, 0), (-18, 0, 0)])
    cyc(walk, "forearm_left", [(-4, 0, 0), (-10, 0, 0), (-18, 0, 0), (-10, 0, 0), (-4, 0, 0)])
    cyc(walk, "torso", [(0, -7, 2), (4, 0, 0), (0, 7, -2), (4, 0, 0), (0, -7, 2)])
    cyc(walk, "head", [(4, 8, 0), (0, 0, 0), (4, -8, 0), (0, 0, 0), (4, 8, 0)])
    cyc(walk, "jaw", [(16, 0, 0), (8, 0, 0), (16, 0, 0)])
    cyc(walk, "shroud", [(-6, 0, 0), (6, 0, 0), (-6, 0, 0), (6, 0, 0), (-6, 0, 0)])

    atk = m.animation("attack", 1.0, "once")
    atk.rot("torso", {0: (0, 0, 0), 0.3: (-12, 18, 0), 0.5: (14, -22, 0), 0.75: (8, -8, 0), 1.0: (0, 0, 0)}, interp="linear")
    atk.pos("body", {0: (0, 0, 0), 0.3: (0, -1, 3), 0.5: (0, -1.5, -7), 0.75: (0, 0, -3), 1.0: (0, 0, 0)}, interp="linear")
    atk.rot("arm_right", {0: (0, 0, 3), 0.3: (-50, 45, 20), 0.5: (-88, -50, 0), 0.75: (-60, -30, 0), 1.0: (0, 0, 3)}, interp="linear")
    atk.rot("arm_left", {0: (0, 0, -3), 0.34: (-45, -40, -18), 0.56: (-85, 48, 0), 0.8: (-50, 20, 0), 1.0: (0, 0, -3)}, interp="linear")
    atk.rot("forearm_right", {0: (-8, 0, 0), 0.3: (-30, 0, 0), 0.5: (-6, 0, 0), 1.0: (-8, 0, 0)}, interp="linear")
    atk.rot("forearm_left", {0: (-14, 0, 0), 0.34: (-30, 0, 0), 0.56: (-6, 0, 0), 1.0: (-14, 0, 0)}, interp="linear")
    atk.rot("head", {0: (0, 0, 0), 0.3: (-18, 0, 0), 0.5: (22, 0, 0), 0.75: (10, 0, 0), 1.0: (0, 0, 0)}, interp="linear")
    atk.rot("jaw", {0: (10, 0, 0), 0.3: (50, 0, 0), 0.5: (62, 0, 0), 0.58: (4, 0, 0), 0.68: (28, 0, 0), 1.0: (10, 0, 0)}, interp="linear")
    atk.rot("leg_right", {0: (0, 0, 0), 0.3: (14, 0, 0), 0.5: (-30, 0, 0), 1.0: (0, 0, 0)}, interp="linear")
    atk.rot("leg_left", {0: (0, 0, 0), 0.3: (-6, 0, 0), 0.5: (24, 0, 0), 1.0: (0, 0, 0)}, interp="linear")
    atk.rot("shroud", {0: (0, 0, 0), 0.5: (-14, 0, 0), 1.0: (0, 0, 0)}, interp="linear")

    death = m.animation("death", 1.8, "once")
    death_fall(death, direction="back")
    death.rot("arm_right", {0: (0, 0, 3), 0.5: (-40, 0, 25), 1.2: (-120, 0, 30), 1.8: (-120, 0, 30)}, interp="linear")
    death.rot("arm_left", {0: (0, 0, -3), 0.5: (-30, 0, -25), 1.2: (-100, 0, -40), 1.8: (-100, 0, -40)}, interp="linear")
    death.rot("head", {0: (0, 0, 0), 0.4: (-25, 0, 0), 1.2: (6, 28, 0), 1.8: (6, 28, 0)}, interp="linear")
    death.rot("jaw", {0: (10, 0, 0), 0.5: (50, 0, 0), 1.8: (42, 0, 0)}, interp="linear")
    return m


# =====================================================================================
# DJINN - elite djinn of smokeless fire
# =====================================================================================
BLUE = "#3b6cd0"
BLUE_D = "#16224a"
CYAN = "#7fd8ff"
FIRE = "#ff7a1c"
FIRE_H = "#ffd24a"
RUBY = "#d02a3a"
CREAM = "#e9e0c8"
TURB = "#1f8f7c"


def tongues(face, colors, *, base=0.4, amp=0.55, rise=True, k=2.0, step=1):
    """Flame tongues (rise) or hanging wisps (not rise) painted into an alpha plane."""
    h, w = face.h, face.w
    p1, p2 = face.rng.random() * 6.28, face.rng.random() * 6.28
    for x in range(w):
        t = x / max(1, w - 1)
        val = base + amp * (0.5 + 0.5 * math.sin(2 * math.pi * t * k + p1)) * (0.65 + 0.35 * math.sin(2 * math.pi * t * k * 2.3 + p2))
        n = max(1, min(h, int(val * h)))
        for j in range(n):
            y = h - 1 - j if rise else j
            frac = j / max(1, n - 1)
            face.pixel(x, y, colors[min(len(colors) - 1, int(frac * len(colors)))])
    return face


def swirl_plane(face, colors, amp=0.28, freq=1.4, taper=True):
    """A curling ribbon of smoke running down the plane."""
    h, w = face.h, face.w
    for y in range(h):
        t = y / max(1, h - 1)
        cx = w / 2 + amp * w * math.sin(t * math.pi * 2 * freq)
        half = (w * 0.22) * ((1 - t * 0.85) if taper else 1)
        for x in range(w):
            if abs(x + 0.5 - cx) <= half:
                edge = abs(x + 0.5 - cx) / max(half, 0.01)
                ci = min(len(colors) - 1, int(edge * len(colors) * 0.999))
                face.pixel(x, y, colors[ci])
    return face


def build_djinn():
    m = Model("bigcasares_myth_djinn", texture_size=(128, 128))

    body = m.bone("body", pivot=(0, 20, 0))
    t1 = m.bone("tail_1", body, pivot=(0, 20, 0))
    t2 = m.bone("tail_2", t1, pivot=(0, 12, 0))
    t3 = m.bone("tail_3", t2, pivot=(0, 7, 0))
    t4 = m.bone("tail_4", t3, pivot=(0, 3.4, 0))
    torso = m.bone("torso", body, pivot=(0, 22, 0))
    head = m.bone("head", torso, pivot=(0, 31, 0))
    arm_r = m.bone("arm_right", torso, pivot=(9.6, 29, 0), rotation=(6, 0, 14))
    fore_r = m.bone("forearm_right", arm_r, pivot=(9.6, 21.5, 0), rotation=(32, 0, 0))
    arm_l = m.bone("arm_left", torso, pivot=(-9.6, 29, 0), rotation=(6, 0, -14))
    fore_l = m.bone("forearm_left", arm_l, pivot=(-9.6, 21.5, 0), rotation=(32, 0, 0))
    flame_r = m.bone("flame_right", fore_r, pivot=(9.6, 12.5, 0))
    flame_l = m.bone("flame_left", fore_l, pivot=(-9.6, 12.5, 0))
    sash = m.bone("sash", torso, pivot=(0, 24, 4.6))

    # --- tail of smokeless fire: stacked shrinking segments, gradient + swirl + ember rims
    segs = [
        (t1, "tail_a", 11.0, 20.5, 6.8, 5.0, "#2c50b4", "#2a2a8a"),
        (t2, "tail_b", 5.5, 12.4, 5.2, 4.0, "#2f55b8", "#3a46aa"),
        (t3, "tail_c", 2.0, 7.5, 3.6, 2.9, "#3a68c4", "#5aa6e6"),
        (t4, "tail_d", 0.0, 3.4, 2.2, 1.9, "#6ab6ee", "#d8f6ff"),
    ]
    for bone, name, y0, y1, hx, hz, top, bot in segs:
        c = m.cube(bone, name, (-hx, y0, -hz), (hx, y1, hz))
        for f in c.faces():
            if f.name in ("up", "down"):
                f.fill(mix(top, bot, 0.3), noise=0.05)
            else:
                f.gradient(top, bot, steps=5)
                f.noise(0.04)
                for xx in range(f.w):  # curling light swirls
                    yy = int((xx * 0.8 + 1.6 * math.sin(xx * 0.8)) % max(1, f.h - 2))
                    f.pixel(xx, yy, mix(top, CYAN, 0.5))
                    f.pixel(xx, yy + 1, mix(top, CYAN, 0.22))
        sk = c.overlay(0.45, name + "_rim")
        for f in sk.sides():
            f.rect(0, 0, f.w, 1, mix(top, CYAN, 0.55))
            f.jagged(mix(bot, "#9fe0ff", 0.4), edge="bottom", depth=(1, 3))
            f.clear(0, 1, f.w, f.h - 4)
            f.dither(FIRE, density=0.1, pattern="noise", y=f.h - 2, h=2)
    # flame crown licking up around the hips and big curling smoke ribbons trailing off the tail
    z1 = m.plane(t1, "flames_z", (-9, 17, 0), (9, 28, 0))
    x1 = m.plane(t1, "flames_x", (0, 17, -7), (0, 28, 7))
    for pl in (z1, x1):
        tongues(pl.front, [BLUE_D, BLUE, mix(BLUE, CYAN, 0.5), CYAN, "#e0f8ff"], base=0.3, amp=0.55, k=2.5)
        speckle(pl.front, FIRE, 0.08)
    for name, bone, frm, to, amp in (("wisp_a", t2, (-11, 0, 0), (11, 14, 0), 0.3), ("wisp_b", t2, (0, 0, -9), (0, 14, 9), 0.25),
                                    ("wisp_c", t3, (-7, 0, 0), (7, 9, 0), 0.35)):
        pl = m.plane(bone, name, frm, to)
        swirl_plane(pl.front, [CYAN, mix(CYAN, BLUE, 0.45), mix(BLUE, "#000", 0.1)], amp=amp, freq=1.2)
        speckle(pl.front, FIRE_H, 0.06)

    # --- torso: waist, muscular chest, shoulders, collar
    waist = m.cube(torso, "waist", (-4.6, 19, -3.2), (4.6, 23.5, 3.2))
    waist.paint(BLUE, jitter=0.15)
    wn = waist.north
    wn.rect(0, 0, 9, 1, GOLD_D).rect(4, 0, 1, 1, RUBY)  # sash belt
    wn.rect(4, 1, 1, 3, mix(BLUE, "#000", 0.35))
    wn.stripe(mix(BLUE, "#000", 0.3), every=2, axis="h", offset=1, y=1, h=3, x=1, w=3)
    wn.stripe(mix(BLUE, "#000", 0.3), every=2, axis="h", offset=1, y=1, h=3, x=5, w=3)
    chest = m.cube(torso, "chest", (-7, 23, -4.5), (7, 30.5, 4.5))
    chest.paint(BLUE, jitter=0.15)
    cn = chest.north
    hi = mix(BLUE, "#9fc0ff", 0.4)
    lo = mix(BLUE, "#000", 0.35)
    pec = mix(BLUE, "#7aa0f0", 0.28)
    cn.rect(1, 1, 5, 4, pec).rect(8, 1, 5, 4, pec)
    cn.rect(1, 1, 5, 1, hi).rect(8, 1, 5, 1, hi)
    cn.rect(1, 5, 5, 1, lo).rect(8, 5, 5, 1, lo)
    cn.rect(6, 1, 2, 7, lo)
    cn.pixel(3, 3, FIRE).pixel(10, 3, FIRE)
    cs = chest.south
    cs.rect(6, 0, 2, 8, lo)
    cs.rect(1, 1, 4, 4, mix(BLUE, "#7aa0f0", 0.2)).rect(9, 1, 4, 4, mix(BLUE, "#7aa0f0", 0.2))
    shoulder = m.cube(torso, "shoulder_right", (6.8, 27, -3.4), (11.6, 31, 3.4))
    shoulder.paint(BLUE, jitter=0.15)
    cap = shoulder.overlay(0.4, "pauldron_right")
    cap.up.fill(GOLD, noise=0.05).outline(GOLD_D).rect(1, 1, cap.up.w - 2, 1, GOLD_L)
    for f in cap.sides():
        f.rect(0, 0, f.w, 1, GOLD_L).rect(0, 1, f.w, 1, GOLD).rect(0, 2, f.w, 1, GOLD_D)
        f.clear(0, 3, f.w, f.h - 3)
        f.pixel(f.w // 2, 1, RUBY)
    shoulder.mirror_x(torso, "shoulder_left")
    cap.mirror_x(torso, "pauldron_left")
    collar = m.cube(torso, "collar", (-5.6, 29, -5), (5.6, 31.4, 5))
    collar.paint(GOLD, jitter=0.08)
    for f in collar.sides():
        f.rect(0, 0, f.w, 1, GOLD_L).rect(0, 1, f.w, 1, GOLD_D)
        for xx in range(1, f.w, 3):
            f.pixel(xx, 1, CYAN)
    collar.north.rect(5, 0, 1, 2, RUBY).pixel(5, 0, "#ff9aa0")
    collar.up.fill(GOLD, noise=0.06).rect(2, 2, 7, 6, BLUE_D)

    # --- head
    skull = m.cube(head, "skull", (-4, 31, -4), (4, 39, 4))
    skull.paint(BLUE, jitter=0.15)
    sn = skull.north
    sn.rect(0, 1, 8, 1, mix(BLUE, "#000", 0.3))
    sn.eyes(4, 3, w=2, h=1, gap=2, glow=FIRE, brow=BLUE_D)
    sn.rect(1, 5, 6, 1, BLUE_D)  # moustache
    sn.pixel(0, 5, BLUE_D).pixel(7, 5, BLUE_D)
    sn.rect(2, 6, 4, 1, mix(BLUE, "#000", 0.5)).pixel(2, 6, CREAM).pixel(5, 6, CREAM)  # fanged grin
    for f in (skull.east, skull.west):
        f.rect(1, 3, 2, 1, FIRE)
    skull.south.fill(BLUE_D, noise=0.06)
    nose = m.cube(head, "nose", (-1, 33.4, -5), (1, 36.4, -4))
    nose.paint(mix(BLUE, "#9fc0ff", 0.15), jitter=0.12)
    goatee = m.cube(head, "goatee", (-1.5, 29.2, -4.2), (1.5, 31.4, -3))
    goatee.paint(BLUE_D, jitter=0.2)
    for f in goatee.sides():
        f.tatter(2)
    ear = m.cube(head, "ear_right", (4, 33.5, -0.8), (6, 40.5, 0.6), rotation=(0, 0, -22.5))
    ear.paint(BLUE, jitter=0.12)
    ear.north.rect(0, 5, 1, 1, GOLD)
    ear.mirror_x(head, "ear_left")
    turban = m.cube(head, "turban", (-4.8, 38, -4.8), (4.8, 42.4, 4.8))
    turban.paint(CREAM, jitter=0.1)
    for f in turban.sides():
        for xx in range(f.w):  # diagonal cloth wraps
            for yy in range(f.h):
                if (xx + yy * 2 + (3 if f.name in ("east", "west") else 0)) % 6 == 5:
                    f.pixel(xx, yy, mix(CREAM, "#8a7c5a", 0.55))
        f.rect(0, 0, f.w, 1, TURB).rect(0, f.h - 1, f.w, 1, GOLD_D)
    turban.north.rect(0, 0, 10, 2, TURB).rect(0, 2, 10, 1, GOLD)
    turban.north.decal([".r.", "rRr", ".r."], {"r": GOLD, "R": RUBY}, x=3, y=0)
    turban.up.fill(CREAM, noise=0.05)
    turban.up.stripe(mix(CREAM, "#8a7c5a", 0.5), every=3, axis="h", offset=1)
    turban.up.rect(3, 3, 4, 4, TURB)
    topknot = m.cube(head, "topknot", (-1.6, 42.4, -1.6), (1.6, 44.8, 1.6))
    topknot.paint(TURB, jitter=0.12)
    topknot.up.rect(1, 1, 1, 1, GOLD)
    tail_cloth = m.plane(head, "turban_tail", (-1.8, 29, 4.9), (1.8, 40, 4.9))
    rt = tail_cloth.front
    rt.fill(TURB, noise=0.06).stripe(mix(TURB, "#000", 0.3), every=3, axis="h", offset=1)
    rt.rect(0, rt.h - 2, 4, 1, GOLD)
    rt.tatter(3)

    # --- arms: muscular, gold armlets and bracers, flame at the hands
    upper = m.cube(arm_r, "upper_arm_right", (7.4, 21.5, -2.4), (11.8, 29.5, 2.4))
    upper.paint(BLUE, jitter=0.15)
    upper.north.rect(0, 1, 4, 3, mix(BLUE, "#7aa0f0", 0.25)).rect(0, 4, 4, 1, mix(BLUE, "#000", 0.3))
    fore = m.cube(fore_r, "lower_arm_right", (7.6, 14, -2.2), (11.6, 22, 2.2))
    fore.paint(BLUE, jitter=0.15)
    hand = m.cube(fore_r, "hand_right", (7.5, 11, -2.3), (11.7, 14.3, 2.3))
    hand.paint(mix(BLUE, "#9fc0ff", 0.1), jitter=0.12)
    hand.north.stripe(mix(BLUE, "#000", 0.35), every=2, axis="v", offset=1, y=1, h=2)
    arm_band = upper.overlay(0.4, "armlet_right")
    for f in arm_band.sides():
        f.rect(0, 4, f.w, 2, GOLD).rect(0, 4, f.w, 1, GOLD_L).rect(0, 5, f.w, 1, GOLD_D)
        f.pixel(f.w // 2, 5, RUBY)
        f.clear(0, 0, f.w, 4).clear(0, 6, f.w, f.h - 6)
    bracer = fore.overlay(0.4, "bracer_right")
    for f in bracer.sides():
        f.rect(0, 3, f.w, 4, GOLD).rect(0, 3, f.w, 1, GOLD_L).rect(0, 6, f.w, 1, GOLD_D)
        f.stripe(GOLD_D, every=2, axis="v", offset=1, y=4, h=2)
        f.pixel(f.w // 2, 4, CYAN)
    upper.mirror_x(arm_l, "upper_arm_left")
    fore.mirror_x(fore_l, "lower_arm_left")
    hand.mirror_x(fore_l, "hand_left")
    arm_band.mirror_x(arm_l, "armlet_left")
    bracer.mirror_x(fore_l, "bracer_left")
    for bone, sx in ((flame_r, 1), (flame_l, -1)):
        zp = m.plane(bone, f"fire_z_{'r' if sx > 0 else 'l'}", (sx * 9.6 - 3.4, 9, 0), (sx * 9.6 + 3.4, 17, 0))
        xp = m.plane(bone, f"fire_x_{'r' if sx > 0 else 'l'}", (sx * 9.6, 9, -3.4), (sx * 9.6, 17, 3.4))
        for p in (zp, xp):
            tongues(p.front, ["#c2380e", FIRE, FIRE_H, "#fff3c0"], base=0.45, amp=0.5, k=2.0)

    # --- sash tail fluttering behind
    st = m.plane(sash, "sash_tail", (-1.8, 12, 4.6), (1.8, 24, 4.6))
    sf = st.front
    sf.fill(RUBY, noise=0.06).stripe(mix(RUBY, "#000", 0.3), every=4, axis="h", offset=2)
    sf.rect(0, 0, sf.w, 1, GOLD)
    sf.tatter(3)

    # --- animations
    idle = m.animation("idle", 3.0, "loop")
    cyc(idle, "body", [(0, 0, 0), (0, 1.2, 0), (0, 0, 0)], "pos")
    cyc(idle, "torso", [(0, 0, 0), (2, 3, 0), (0, 0, 0), (2, -3, 0), (0, 0, 0)])
    cyc(idle, "head", [(0, 0, 0), (3, 4, 0), (0, 0, 0), (-1, -4, 0), (0, 0, 0)])
    sine(idle, "arm_right", (4, 0, 2), 0, 4, base=(0, 0, 0))
    sine(idle, "arm_left", (4, 0, 2), 180, 4, base=(0, 0, 0))
    cyc(idle, "forearm_right", [(0, 0, 0), (-8, 0, 0), (0, 0, 0)])
    cyc(idle, "forearm_left", [(-6, 0, 0), (2, 0, 0), (-6, 0, 0)])
    for i, b in enumerate(("tail_1", "tail_2", "tail_3", "tail_4")):
        sine(idle, b, (3 + i, 0, 0), 40 * i, 8, cosine=(0, 0, 4 + 2 * i))
    for fb in ("flame_right", "flame_left"):
        cyc(idle, fb, [(1, 1, 1), (1.12, 1.3, 1.12), (0.94, 1.05, 0.94), (1.1, 1.25, 1.1), (1, 1, 1)], "scale")
    cyc(idle, "sash", [(4, 0, 0), (14, 0, 6), (4, 0, 0), (14, 0, -6), (4, 0, 0)])

    walk = m.animation("walk", 1.6, "loop")
    cyc(walk, "body", [(0, 0, 0), (0, 1.4, 0), (0, 0, 0), (0, 1.4, 0), (0, 0, 0)], "pos")
    cyc(walk, "torso", [(8, -4, 0), (9, 0, 0), (8, 4, 0), (9, 0, 0), (8, -4, 0)])
    cyc(walk, "head", [(-4, 3, 0), (-5, 0, 0), (-4, -3, 0), (-5, 0, 0), (-4, 3, 0)])
    sine(walk, "arm_right", (6, 0, 0), 0, 4, base=(-6, 0, 0))
    sine(walk, "arm_left", (6, 0, 0), 180, 4, base=(-6, 0, 0))
    cyc(walk, "forearm_right", [(-4, 0, 0), (4, 0, 0), (-4, 0, 0)])
    cyc(walk, "forearm_left", [(4, 0, 0), (-4, 0, 0), (4, 0, 0)])
    for i, b in enumerate(("tail_1", "tail_2", "tail_3", "tail_4")):
        sine(walk, b, (4, 6 + 2 * i, 0), 55 * i, 8, base=(12 + 2 * i, 0, 0), cosine=(0, 0, 5))
    for fb in ("flame_right", "flame_left"):
        cyc(walk, fb, [(1, 1, 1), (1.15, 1.35, 1.15), (1, 1, 1), (1.1, 1.3, 1.1), (1, 1, 1)], "scale")
    cyc(walk, "sash", [(24, 0, 0), (34, 0, 8), (24, 0, 0), (34, 0, -8), (24, 0, 0)])

    atk = m.animation("attack", 1.1, "once")
    atk.rot("torso", {0: (0, 0, 0), 0.35: (-14, 0, 0), 0.55: (18, 0, 0), 0.8: (10, 0, 0), 1.1: (0, 0, 0)}, interp="linear")
    atk.pos("body", {0: (0, 0, 0), 0.35: (0, 1, 3), 0.55: (0, 0.5, -5), 0.8: (0, 1, -2), 1.1: (0, 0, 0)}, interp="linear")
    atk.rot("arm_right", {0: (0, 0, 0), 0.35: (40, 0, 30), 0.55: (-95, -8, -8), 0.8: (-90, -6, -6), 1.1: (0, 0, 0)}, interp="linear")
    atk.rot("arm_left", {0: (0, 0, 0), 0.35: (40, 0, -30), 0.55: (-95, 8, 8), 0.8: (-90, 6, 6), 1.1: (0, 0, 0)}, interp="linear")
    atk.rot("forearm_right", {0: (0, 0, 0), 0.35: (-30, 0, 0), 0.55: (-30, 0, 0), 1.1: (0, 0, 0)}, interp="linear")
    atk.rot("forearm_left", {0: (0, 0, 0), 0.35: (-30, 0, 0), 0.55: (-30, 0, 0), 1.1: (0, 0, 0)}, interp="linear")
    atk.rot("head", {0: (0, 0, 0), 0.35: (-12, 0, 0), 0.55: (10, 0, 0), 1.1: (0, 0, 0)}, interp="linear")
    for fb in ("flame_right", "flame_left"):
        atk.scale(fb, {0: (1, 1, 1), 0.35: (0.8, 0.8, 0.8), 0.55: (1.5, 1.9, 1.5), 0.8: (1.3, 1.6, 1.3), 1.1: (1, 1, 1)}, interp="linear")
    for i, b in enumerate(("tail_1", "tail_2", "tail_3", "tail_4")):
        atk.rot(b, {0: (0, 0, 0), 0.35: (-4 - i, 0, 0), 0.55: (14 + 3 * i, 0, 0), 1.1: (0, 0, 0)}, interp="linear")

    death = m.animation("death", 2.0, "once")
    death.rot("torso", {0: (0, 0, 0), 0.3: (-12, 0, 6), 0.7: (10, 0, -6), 1.2: (0, 0, 0)}, interp="linear")
    death.rot("arm_right", {0: (0, 0, 0), 0.4: (-80, 0, 40), 1.2: (-140, 0, 10)}, interp="linear")
    death.rot("arm_left", {0: (0, 0, 0), 0.4: (-80, 0, -40), 1.2: (-140, 0, -10)}, interp="linear")
    death.scale("torso", {0: (1, 1, 1), 0.5: (1.1, 1.1, 1.1), 1.1: (0.5, 0.35, 0.5), 1.6: (0.15, 0.05, 0.15), 2.0: (0.02, 0.01, 0.02)},
                interp="linear")
    death.pos("torso", {0: (0, 0, 0), 0.5: (0, 2, 0), 1.1: (0, -6, 0), 1.6: (0, -14, 0), 2.0: (0, -18, 0)}, interp="linear")
    death.scale("root", {0: (1, 1, 1), 0.5: (1.05, 1.0, 1.05), 1.1: (0.9, 0.9, 0.9), 1.6: (0.5, 0.2, 0.5), 2.0: (0.05, 0.01, 0.05)},
                interp="linear")
    death.pos("body", {0: (0, 0, 0), 0.5: (0, 1.5, 0), 2.0: (0, 0, 0)}, interp="linear")
    for i, b in enumerate(("tail_1", "tail_2", "tail_3", "tail_4")):
        death.rot(b, {0: (0, 0, 0), 0.6: (0, 0, 10 * (-1) ** i), 1.2: (0, 0, -14 * (-1) ** i), 2.0: (0, 0, 0)}, interp="linear")
    for fb in ("flame_right", "flame_left"):
        death.scale(fb, {0: (1, 1, 1), 0.6: (1.5, 2.0, 1.5), 1.4: (1.0, 1.2, 1.0), 2.0: (0.1, 0.1, 0.1)}, interp="linear")
    return m


MODELS = {
    "bigcasares_myth_ghul": build_ghul,
    "bigcasares_myth_djinn": build_djinn,
}
