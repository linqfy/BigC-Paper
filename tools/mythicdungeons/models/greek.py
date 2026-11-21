"""Greek mythology creatures for the mythic dungeons.

Style reference for every bbkit author: ~40 cuboids of varied sizes, detail painted into the
texture, relief from overlay layers (pauldron, bracers, fetlock fur, forehead tuft, hump mane)
and alpha planes (loincloth flaps, axe blade, nose ring).  Left limbs reuse the right limbs'
texture with ``mirror_x``.  Model faces north (-Z); the creature's right is +X.
"""
import sys
from pathlib import Path

sys.path.insert(0, str(Path(__file__).resolve().parents[1]))
from bbkit import (Model, attack_swing, death_fall, flap, idle_breathe, mix, ramp,  # noqa: E402
                   walk_biped, walk_quadruped)
import numpy as np  # noqa: E402

FUR = "#5b3a26"
FUR_DARK = "#3c2518"
CHEST = "#74503a"
MUZZLE = "#7e6454"
NOSE = "#2e1c18"
HORN = "#e4d8b6"
HORN_TIP = "#4e4236"
EYE = "#ff4326"
LEATHER = "#3b2517"
GOLD = "#dcaa3c"
CLOTH = "#8e2b22"
BRONZE = "#a0703a"
HOOF = "#2c2420"
WOOD = "#6e4a2a"
IRON = "#a3abb3"

GREEK_KEY = [  # meander band, 8 px repeat, 4 rows
    "gggggg.g",
    "g....g.g",
    "g.ggg..g",
    "g.g.gggg",
]


def fur(cube, base=FUR, strands=FUR_DARK, density=0.35):
    cube.paint(base, noise=0.05, jitter=0.2)
    for f in cube.faces():
        if f.name != "up":
            f.strands(strands, density=density, length=(2, 3))
    return cube


def build_minotaur():
    m = Model("bigcasares_myth_minotaur", texture_size=(128, 128))

    body = m.bone("body", pivot=(0, 15, 0))
    torso = m.bone("torso", body, pivot=(0, 17, 0))
    head = m.bone("head", torso, pivot=(0, 32, -4))
    arm_r = m.bone("arm_right", torso, pivot=(11.5, 29.5, 0))
    fore_r = m.bone("forearm_right", arm_r, pivot=(11.5, 21.5, 0))
    arm_l = m.bone("arm_left", torso, pivot=(-11.5, 29.5, 0))
    fore_l = m.bone("forearm_left", arm_l, pivot=(-11.5, 21.5, 0))
    leg_r = m.bone("leg_right", body, pivot=(4.25, 15, 0))
    shin_r = m.bone("shin_right", leg_r, pivot=(4.25, 8, 0))
    leg_l = m.bone("leg_left", body, pivot=(-4.25, 15, 0))
    shin_l = m.bone("shin_left", leg_l, pivot=(-4.25, 8, 0))
    tail = m.bone("tail", body, pivot=(0, 16, 4.5), rotation=(-20, 0, 0))
    loin = m.bone("loincloth", body, pivot=(0, 15.5, -4.75))

    # --- hips, belt, loincloth
    pelvis = fur(m.cube(body, "pelvis", (-6.5, 12.5, -4), (6.5, 17, 4)))
    pelvis.down.fill(FUR_DARK, noise=0.06)
    belt = m.cube(body, "belt", (-7, 14.5, -4.5), (7, 17.5, 4.5))
    belt.paint(LEATHER, jitter=0.1)
    for f in belt.sides():
        f.stripe(mix(LEATHER, "#000", 0.35), every=3, axis="h", offset=2)
    belt.north.rect(5, 0, 4, 3, GOLD).outline(mix(GOLD, "#000", 0.4), 5, 0, 4, 3).pixel(6, 1, "#fff2b0")
    belt.north.rivets(GOLD, every=3, inset=0)

    front = m.plane(loin, "loincloth_front", (-4, 5.5, -4.75), (4, 15.5, -4.75))
    front.front.gradient(CLOTH, mix(CLOTH, "#000", 0.35), steps=4)
    front.front.noise(0.06).decal(GREEK_KEY, {"g": GOLD}, x=0, y=1)
    front.front.rect(0, 0, 8, 1, mix(GOLD, "#000", 0.3)).outline(mix(CLOTH, "#000", 0.5), sides="lr")
    front.front.tatter(3)
    back = m.plane(body, "loincloth_back", (-5, 6.5, 4.75), (5, 15.5, 4.75))
    back.front.gradient(CLOTH, mix(CLOTH, "#000", 0.4), steps=4).noise(0.06)
    back.front.decal(GREEK_KEY, {"g": GOLD}, x=1, y=1).tatter(3)

    # --- legs (right built, left mirrored)
    thigh = fur(m.cube(leg_r, "thigh_right", (1.5, 7.5, -3), (7, 15, 3)))
    thigh.north.darken(0.15, 0, 0, 1)
    shin = fur(m.cube(shin_r, "lower_leg_right", (2, 2, -2.5), (6.5, 8, 2.5)))
    hoof = m.cube(shin_r, "hoof_right", (1.5, 0, -3.5), (7, 2.5, 2.5))
    hoof.paint(HOOF, jitter=0.1)
    hoof.north.rect(2, 0, 1, 3, "#120c0a").rect(0, 0, 6, 1, mix(HOOF, "#fff", 0.2))
    hoof.up.fill(FUR_DARK)
    fetlock = shin.overlay(0.4, "fetlock_right")
    for f in fetlock.sides():
        f.jagged(FUR_DARK, edge="bottom", depth=(1, 3))
    thigh.mirror_x(leg_l, "thigh_left")
    shin.mirror_x(shin_l, "lower_leg_left")
    hoof.mirror_x(shin_l, "hoof_left")
    fetlock.mirror_x(shin_l, "fetlock_left")

    # --- torso: belly, chest, shoulder hump with mane
    belly = m.cube(torso, "belly", (-7, 17, -4), (7, 24, 4.5))
    belly.paint(CHEST, jitter=0.15)
    belly.north.stripe(mix(CHEST, "#000", 0.25), every=3, axis="h", offset=1, x=4, w=6)
    belly.north.rect(7, 0, 1, 7, mix(CHEST, "#000", 0.25)).pixel(6, 5, "#2a1810")
    for f in (belly.east, belly.west, belly.south):
        f.strands(FUR_DARK, density=0.3)
    chest = m.cube(torso, "chest", (-9, 23, -5.5), (9, 31, 5))
    fur(chest)
    pal = ramp(CHEST)
    chest.north.rect(2, 2, 14, 6, pal[2], noise=0.05)
    chest.north.line(9, 3, 9, 7, pal[0]).line(8, 3, 8, 7, pal[0])
    chest.north.line(2, 7, 7, 7, pal[1]).line(10, 7, 15, 7, pal[1])
    chest.north.line(4, 3, 6, 5, "#c88c78").line(12, 2, 13, 5, "#c88c78")  # scars
    chest.north.jagged(FUR, edge="top", depth=(1, 3))
    hump = fur(m.cube(torso, "hump", (-6.5, 29, -1.5), (6.5, 33, 5.5)))
    mane = hump.overlay(0.5, "hump_mane")
    mane.up.fill(FUR_DARK).noise(0.15).dither(FUR, density=0.3, pattern="noise")
    for f in mane.sides():
        f.jagged(FUR_DARK, edge="top", depth=(2, 4))

    # --- head: skull, muzzle, horns, ears, tuft, nose ring
    skull = fur(m.cube(head, "skull", (-5, 31, -12), (5, 40, -3)))
    skull.north.rect(3, 2, 4, 3, mix(FUR, MUZZLE, 0.3))  # pale blaze between the eyes
    skull.north.eyes(5, 3, w=2, h=1, gap=4, glow=EYE, socket="#1a0e0a", brow=FUR_DARK)
    muzzle = m.cube(head, "muzzle", (-3.5, 31, -15), (3.5, 35.5, -12))
    muzzle.paint(MUZZLE, jitter=0.12)
    muzzle.north.rect(1, 1, 2, 1, NOSE).rect(4, 1, 2, 1, NOSE).rect(0, 3, 7, 1, mix(MUZZLE, "#000", 0.35))
    muzzle.up.rect(1, 0, 5, 1, mix(MUZZLE, FUR, 0.5))
    muzzle.down.fill(mix(MUZZLE, "#000", 0.3))
    ring = m.plane(head, "nose_ring", (-1.5, 29.5, -15.1), (1.5, 32, -15.1))
    ring.front.decal([".g.", "g.g", "g.g"], {"g": GOLD}).pixel(0, 1, "#fff0a0")
    tuft = skull.overlay(0.4, "forehead_tuft")
    tuft.north.jagged(FUR_DARK, edge="top", depth=(1, 2), x=1, w=8)
    tuft.up.fill(FUR_DARK).dither(FUR, density=0.35, pattern="noise")
    tuft.east.jagged(FUR_DARK, edge="top", depth=(1, 2))
    tuft.west.jagged(FUR_DARK, edge="top", depth=(1, 2))
    horn_base = m.cube(head, "horn_base_right", (5, 37, -9.5), (8.5, 39, -7))
    horn_base.paint(HORN, jitter=0.15)
    horn_base.north.rect(0, 0, 1, 2, mix(HORN, "#000", 0.25))
    horn_tip = m.cube(head, "horn_tip_right", (7.5, 38, -9.25), (9.5, 44, -7.25), rotation=(0, 0, -22.5),
                      pivot=(8.5, 38, -8.25))
    horn_tip.paint(HORN, jitter=0.12)
    for f in horn_tip.sides():
        f.gradient(HORN, HORN_TIP, steps=4).rect(0, 0, f.w, 2, mix(HORN_TIP, "#000", 0.2))
        f.stripe(mix(HORN, "#000", 0.2), every=2, axis="h", offset=1, y=3)
    horn_tip.up.fill(HORN_TIP)
    ear = m.cube(head, "ear_right", (5, 34.5, -7), (8, 36, -5), rotation=(0, 0, 22.5), pivot=(5, 35, -6))
    fur(ear, density=0.2)
    ear.down.fill(mix(MUZZLE, "#000", 0.2))
    horn_base.mirror_x(head, "horn_base_left")
    horn_tip.mirror_x(head, "horn_tip_left")
    ear.mirror_x(head, "ear_left")

    # --- arms (right built, left mirrored) with a bronze pauldron and bracers
    upper = fur(m.cube(arm_r, "upper_arm_right", (9, 21, -3), (14, 30, 3)))
    shoulder = fur(m.cube(arm_r, "shoulder_right", (8.5, 25.5, -3.75), (14.75, 32, 3.75)))
    pauldron = shoulder.overlay(0.6, "pauldron_right")
    pauldron.up.shaded(BRONZE, jitter=0.12, shade=False).outline(mix(BRONZE, "#000", 0.35))
    pauldron.up.rect(1, 1, pauldron.up.w - 2, 1, mix(BRONZE, "#fff", 0.3)).rivets("#f0d27a", every=3)
    for f in (pauldron.north, pauldron.south, pauldron.east):
        f.rect(0, 0, f.w, 3, BRONZE).noise(0.08, h=3).rect(0, 2, f.w, 1, mix(BRONZE, "#000", 0.4))
        f.rect(0, 0, f.w, 1, mix(BRONZE, "#fff", 0.3))
    pauldron.east.rect(2, 3, 4, 2, BRONZE).pixel(3, 3, mix(BRONZE, "#fff", 0.4))
    forearm = fur(m.cube(fore_r, "lower_arm_right", (9, 13, -3), (14.25, 21.5, 3)))
    fist = m.cube(fore_r, "fist_right", (9.25, 9.5, -3), (14, 13.5, 3))
    fist.paint(mix(FUR, MUZZLE, 0.3), jitter=0.15)
    fist.north.stripe(mix(FUR, "#000", 0.4), every=2, axis="v", y=0, h=2)
    bracer = forearm.overlay(0.35, "bracer_right")
    for f in bracer.sides():
        f.rect(0, 1, f.w, 5, BRONZE).noise(0.08, y=1, h=5)
        f.rect(0, 1, f.w, 1, mix(BRONZE, "#fff", 0.3)).rect(0, 5, f.w, 1, mix(BRONZE, "#000", 0.4))
        f.stripe(mix(BRONZE, "#000", 0.3), every=3, axis="v", y=2, h=3)
    upper.mirror_x(arm_l, "upper_arm_left")
    shoulder.mirror_x(arm_l, "shoulder_left")
    forearm.mirror_x(fore_l, "lower_arm_left")
    fist.mirror_x(fore_l, "fist_left")
    bracer.mirror_x(fore_l, "bracer_left")

    # --- labrys-style axe in the right fist: handle along -Z, blade is an alpha plane
    handle = m.cube(fore_r, "axe_handle", (10.9, 10.75, -17), (12.4, 12.25, 6))
    handle.paint(WOOD, jitter=0.2)
    for f in handle.faces("north", "south", "east", "west", "up", "down"):
        if f.w > 4:
            f.stripe(LEATHER, every=3, axis="v", offset=0, x=f.w - 12, w=7)
    socket = m.cube(fore_r, "axe_socket", (10.6, 10, -18), (12.7, 13, -14))
    socket.paint(IRON, jitter=0.1).up.rivets("#4a5058", every=2)
    blade = m.plane(fore_r, "axe_blade", (11.65, 3.5, -20), (11.65, 19.5, -12))
    face = blade.front  # east face: x=0 at the handle side, x=w-1 at the cutting edge
    w, h = face.w, face.h
    for y in range(h):
        t = abs(y - (h - 1) / 2) / ((h - 1) / 2)
        x0 = int(round((w - 3) * t * t))
        x1 = w - 1 - int(round(1.5 * t))
        face.rect(x0, y, x1 - x0 + 1, 1, IRON)
        face.pixel(x1, y, "#eef4f8").pixel(x0, y, "#5c636b")
    face.noise(0.05).line(1, h // 2, w - 3, h // 2, mix(IRON, "#000", 0.25))
    face.pixel(w - 4, h // 2 - 3, GOLD).pixel(w - 4, h // 2 + 3, GOLD)

    # --- tail with a dark tuft
    tail_rope = m.cube(tail, "tail_rope", (-0.75, 5, 4.5), (0.75, 16, 6))
    tail_rope.paint(FUR, jitter=0.2)
    tuft_c = m.cube(tail, "tail_tuft", (-1.25, 1.5, 4.25), (1.25, 5.5, 6.25))
    tuft_c.paint(FUR_DARK, jitter=0.25)
    for f in tuft_c.sides():
        f.tatter(2)

    # --- animations
    idle = m.animation("idle", 2.4, "loop")
    idle_breathe(idle, body="torso", head="head", arms=("arm_left", "arm_right"), tail="tail", amount=0.4)
    idle.rot("loincloth", {0: (0, 0, 0), 1.2: (2, 0, 0), 2.4: (0, 0, 0)})

    walk = m.animation("walk", 1.2, "loop")
    walk_biped(walk, legs=("leg_left", "leg_right"), shins=("shin_left", "shin_right"),
               arms=("arm_left", "arm_right"), forearms=("forearm_left", "forearm_right"),
               body="body", head="head", swing=26, bob=0.8, sway=4)
    L = walk.length
    walk.rot("torso", {0: (6, 0, 0), L / 2: (6, 0, 0), L: (6, 0, 0)})  # heavy forward hunch
    walk.rot("loincloth", {0: (-6, 0, 0), L / 4: (4, 0, 0), L / 2: (-6, 0, 0), 3 * L / 4: (4, 0, 0), L: (-6, 0, 0)})
    walk.rot("tail", {0: (0, -14, 0), L / 2: (0, 14, 0), L: (0, -14, 0)})

    attack = m.animation("attack", 1.1, "once")
    attack_swing(attack, "arm_right", forearms=("forearm_right",), body="torso", head="head", style="overhead",
                 windup=0.4, strike=0.55)
    attack.rot("arm_left", {0: (0, 0, 0), 0.44: (-30, 0, -25), 0.6: (20, 0, -10), 1.1: (0, 0, 0)}, interp="linear")
    attack.pos("body", {0: (0, 0, 0), 0.44: (0, 0.5, 1), 0.6: (0, -1.2, -1.5), 1.1: (0, 0, 0)}, interp="linear")

    death = m.animation("death", 2.0, "once")
    death_fall(death, direction="back")
    death.rot("arm_right", {0: (0, 0, 0), 0.5: (-40, 0, 20), 1.3: (-115, 0, 30), 2.0: (-115, 0, 30)}, interp="linear")
    death.rot("arm_left", {0: (0, 0, 0), 0.5: (-40, 0, -20), 1.3: (-110, 0, -35), 2.0: (-110, 0, -35)}, interp="linear")
    death.rot("head", {0: (0, 0, 0), 0.4: (-25, 0, 0), 1.4: (10, 25, 0), 2.0: (10, 25, 0)}, interp="linear")
    return m




# =====================================================================================
# CERBERUS - three-headed hellhound
# =====================================================================================
CFUR = "#3b3b4a"
CFUR_D = "#1b1b25"
CFUR_L = "#6c6c86"
EMBER = "#ff5a1f"
EMBER_H = "#ffc23a"
MOUTH_RED = "#a8240f"
FANG_C = "#e8dcc0"
COLLAR_C = "#1d1210"
IRON_C = "#9aa0a8"
SERP = "#2f4a2c"
SERP_D = "#142414"
SERP_L = "#5f9440"


def _speckle(face, color, p=0.2):
    ys, xs = np.nonzero(face.mask())
    for y, x in zip(ys, xs):
        if face.rng.random() < p:
            face.pixel(int(x), int(y), color)
    return face


def _cfur(cube, base=CFUR, dark=CFUR_D, light=CFUR_L, density=0.35):
    cube.paint(base, noise=0.05, jitter=0.22)
    for f in cube.faces():
        if f.name != "down":
            f.strands(dark, density=density, length=(2, 4))
            f.strands(light, density=density * 0.4, length=(1, 2))
    return cube


def _cerberus_head(m, neck_b, head_b, jaw_b, dx, k, ybase, dz, tag):
    """One head in 'local' coordinates of the centre head, scaled by k and moved by dx/ybase/dz.
    Returns the list of (cube, bone-name) so the mirrored twin can be built from it."""
    T = lambda x, y, z: (dx + k * x, ybase + k * (y - 22), -12 + dz + k * (z + 12))
    made = []

    def cube(bone, name, a, b, **kw):
        c = m.cube(bone, f"{name}_{tag}", T(*a), T(*b), **kw)
        made.append((c, bone.name))
        return c

    neck = _cfur(cube(neck_b, "nape", (-3, 17, -13), (3, 28, -8)))
    collar = neck.overlay(0.7, f"collar_{tag}")
    made.append((collar, neck_b.name))
    for f in collar.sides():
        f.rect(0, 3, f.w, 4, COLLAR_C, noise=0.05)
        f.rect(0, 3, f.w, 1, mix(COLLAR_C, "#fff", 0.2))
        for xx in range(1, f.w, 2):
            f.pixel(xx, 4, IRON_C).pixel(xx, 5, mix(IRON_C, "#000", 0.4))
    flange = m.plane(neck_b, f"collar_spikes_{tag}", T(-5.2, 22.5, -13.5), T(5.2, 22.5, -7))
    made.append((flange, neck_b.name))
    ff = flange.front
    cx, cy = (ff.w - 1) / 2, (ff.h - 1) / 2
    import math
    for i in range(16):
        a = 2 * math.pi * i / 16
        r = 1.0 if i % 2 == 0 else 0.62
        ff.line(cx, cy, cx + math.cos(a) * (ff.w / 2 - 0.5) * r, cy + math.sin(a) * (ff.h / 2 - 0.5) * r, IRON_C)
    ff.ellipse(cx + 0.5, cy + 0.5, ff.w * 0.28, ff.h * 0.28, mix(IRON_C, "#000", 0.35))
    ff.noise(0.08)

    skull = _cfur(cube(head_b, "skull", (-4, 23, -21), (4, 30, -12.5)))
    sn = skull.north
    sn.rect(0, 2, 1, 2, EMBER).rect(7, 2, 1, 2, EMBER)
    sn.rect(0, 1, 2, 1, CFUR_D).rect(6, 1, 2, 1, CFUR_D)
    for side, xs in ((skull.east, (0, 1, 2)), (skull.west, (0, 1, 2))):
        side.rect(0, 2, 3, 2, EMBER)
        side.rect(0, 2, 1, 2, EMBER_H)
        side.rect(0, 1, 4, 1, CFUR_D).line(0, 4, 3, 4, CFUR_D)
    skull.up.rect(3, 0, 2, 8, CFUR_D, noise=0.1)
    brow = skull.overlay(0.35, f"brow_{tag}")
    brow.north.rect(0, 0, 8, 2, CFUR_D).jagged(CFUR_D, edge="top", depth=(2, 3))
    for f in (brow.east, brow.west):
        f.rect(0, 0, f.w, 2, CFUR_D).jagged(CFUR_D, edge="top", depth=(2, 3))
    brow.up.fill(CFUR_D).dither(CFUR, density=0.3, pattern="noise")
    made.append((brow, head_b.name))
    snout = cube(head_b, "snout", (-2.8, 23.5, -27.5), (2.8, 27.5, -21))
    snout.paint(mix(CFUR, "#000", 0.2), jitter=0.2)
    snout.north.rect(1, 0, 4, 2, "#0a0a0e").pixel(2, 1, "#2a2a30").pixel(3, 1, "#2a2a30")
    for side in (snout.east, snout.west):
        side.rect(0, side.h - 1, side.w, 1, mix(MOUTH_RED, "#000", 0.2))
        for xx in range(1, side.w, 3):
            side.pixel(xx, side.h - 2, FANG_C).pixel(xx, side.h - 1, FANG_C)
        side.strands(CFUR_L, density=0.25, length=(1, 2), h=3)
    snout.down.fill(MOUTH_RED, noise=0.05).rect(0, 0, 1, 7, FANG_C).rect(5, 0, 1, 7, FANG_C)
    ear = cube(head_b, "ear_right", (2.4, 29.5, -17.5), (4.6, 34.5, -15.2), rotation=(0, 0, -22.5), pivot=(3.5, 30, -16.3))
    _cfur(ear, density=0.2)
    ear.east.rect(0, 1, 2, 3, CFUR_D)
    ear.north.rect(1, 0, 1, 5, "#5a2a28")
    ear2 = ear.mirror_x(head_b, f"ear_left_{tag}")
    made.append((ear2, head_b.name))
    jaw = cube(jaw_b, "lowerjaw", (-2.7, 20.8, -27), (2.7, 23.4, -15))
    jaw.paint(mix(CFUR, "#000", 0.15), jitter=0.2)
    jaw.up.fill(MOUTH_RED, noise=0.06).rect(2, 3, 2, 8, "#e0503a").rect(0, 0, 1, 12, FANG_C).rect(4, 0, 1, 12, FANG_C)
    for side in (jaw.east, jaw.west):
        side.rect(0, 0, side.w, 1, mix(MOUTH_RED, "#000", 0.2))
        for xx in range(2, side.w, 3):
            side.pixel(xx, 0, FANG_C)
    return made


def build_cerberus():
    m = Model("bigcasares_myth_cerberus", texture_size=(256, 128))

    body = m.bone("body", pivot=(0, 14, 0))
    chest = m.bone("chest", body, pivot=(0, 18, -6))
    mane_b = m.bone("mane", chest, pivot=(0, 26, -8))
    heads = {}
    for tag, dx in (("c", 0.0), ("r", 9.0), ("l", -9.0)):
        yb = 23.0 if tag == "c" else 20.0
        rot = None if tag == "c" else (0, -26 if tag == "r" else 26, 0)
        nb = m.bone(f"neck_{tag}", chest, pivot=(dx * 0.8, 21, -9), rotation=rot)
        hb = m.bone(f"head_{tag}", nb, pivot=(dx, yb + 2, -13.5))
        jb = m.bone(f"jaw_{tag}", hb, pivot=(dx, yb + 1, -14.5))
        heads[tag] = (nb, hb, jb)
    fl = m.bone("leg_front_left", body, pivot=(-5.5, 16, -9))
    fr = m.bone("leg_front_right", body, pivot=(5.5, 16, -9))
    bl = m.bone("leg_back_left", body, pivot=(-6.5, 17, 6.5))
    br = m.bone("leg_back_right", body, pivot=(6.5, 17, 6.5))
    tail = m.bone("tail", body, pivot=(0, 19, 11.5))
    tail2 = m.bone("tail_tip", tail, pivot=(0, 19, 19))

    # --- torso
    ch = _cfur(m.cube(chest, "chest_fur", (-7.5, 11, -12), (7.5, 26, 0)))
    ch.north.rect(4, 6, 8, 7, mix(CFUR, CFUR_L, 0.35)).strands(CFUR_L, density=0.4, length=(2, 3), x=4, y=6, w=8, h=7)
    ruff = ch.overlay(0.6, "chest_ruff")
    for f in ruff.sides():
        f.jagged(CFUR_D, edge="top", depth=(3, 6))
        _speckle(f, CFUR, 0.25)
    ruff.up.fill(CFUR_D, noise=0.1).dither(CFUR_L, density=0.25, pattern="noise")
    hind = _cfur(m.cube(body, "haunch", (-6.8, 10.5, -1), (6.8, 23, 12)))
    hind.up.rect(5, 0, 4, 13, CFUR_D, noise=0.1)
    # ember cracks in the hide
    for c in (ch, hind):
        for f in c.sides():
            f.cracks(mix(EMBER, "#000", 0.35), n=2, length=4)

    crest = m.plane(mane_b, "mane_crest", (0, 25.5, -10), (0, 31.5, 4))
    cf = crest.front
    cf.fill(CFUR_D, noise=0.1)
    cf.strands(CFUR, density=0.5, length=(2, 5))
    for xx in range(cf.w):  # crest tall in front, fading toward the rump
        keep = int(cf.h * (0.55 + 0.45 * (1 - xx / cf.w)))
        cf.clear(xx, 0, 1, cf.h - keep)
    cf.tatter(2, edge="top", chance=0.8)
    ruffp = m.plane(mane_b, "mane_collar", (-12, 19, -9.2), (12, 30, -9.2))
    rp = ruffp.front
    rp.fill(CFUR_D, noise=0.1)
    rp.strands(CFUR, density=0.5, length=(2, 5))
    rp.strands(CFUR_L, density=0.15, length=(1, 3))
    for yy in range(rp.h):
        t = abs(yy - rp.h * 0.55) / rp.h
        cut = int(t * t * 22)
        rp.clear(0, yy, cut, 1)
        rp.clear(rp.w - cut, yy, cut, 1)
    rp.tatter(3, edge="top", chance=0.8)
    rp.tatter(3, edge="bottom", chance=0.8)

    # --- legs
    def foreleg(bone, side_tag):
        leg = _cfur(m.cube(bone, f"foreleg_{side_tag}", (3.3, 3.5, -11), (7.7, 15.5, -6.8)))
        paw = m.cube(bone, f"paw_{side_tag}", (3, 0, -12.8), (8, 3.8, -6.5))
        paw.paint(CFUR_D, jitter=0.2)
        paw.north.rect(0, 3, 5, 1, "#0a0a0e")
        for xx in (0, 2, 4):
            paw.north.pixel(xx, 2, "#cfc4a8").pixel(xx, 3, "#cfc4a8")
        paw.up.fill(CFUR, noise=0.1)
        fet = leg.overlay(0.4, f"fetlock_{side_tag}")
        for f in fet.sides():
            f.jagged(CFUR_D, edge="bottom", depth=(1, 4))
            _speckle(f, CFUR, 0.2)
        return leg, paw, fet

    leg, paw, fet = foreleg(fr, "right")
    leg.mirror_x(fl, "foreleg_left")
    paw.mirror_x(fl, "paw_left")
    fet.mirror_x(fl, "fetlock_left")
    thigh = _cfur(m.cube(br, "thigh_right", (4.3, 8, 2.5), (8.7, 19.5, 10.7)))
    low = _cfur(m.cube(br, "hock_right", (4.9, 1.5, 6.2), (8.1, 9, 10)))
    hp = m.cube(br, "hind_paw_right", (4.5, 0, 4.8), (8.5, 2.7, 11.5))
    hp.paint(CFUR_D, jitter=0.2)
    for xx in (0, 2, 3):
        hp.north.pixel(xx, 1, "#cfc4a8")
    thigh.mirror_x(bl, "thigh_left")
    low.mirror_x(bl, "hock_left")
    hp.mirror_x(bl, "hind_paw_left")

    # --- three heads (right built, left mirrored from it)
    _cerberus_head(m, *heads["c"], 0.0, 1.1, 23.0, -3.0, "c")
    right = _cerberus_head(m, *heads["r"], 9.0, 0.92, 20.0, 1.5, "r")
    nb, hb, jb = heads["l"]
    mapping = {heads["r"][0].name: nb, heads["r"][1].name: hb, heads["r"][2].name: jb}
    for c, bname in right:
        c.mirror_x(mapping[bname], c.name[:-1] + "l")

    # --- serpent tail
    ta = m.cube(tail, "tail_a", (-1.5, 17.7, 11.5), (1.5, 20.7, 19.5))
    ta.paint(SERP, jitter=0.2)
    for f in ta.faces():
        f.scales(SERP_D, size=3, highlight=SERP_L)
    ta.up.rect(1, 0, 1, 8, EMBER)
    tb = m.cube(tail2, "tail_b", (-1.1, 17.9, 19.5), (1.1, 20, 26.5))
    tb.paint(SERP, jitter=0.2)
    for f in tb.faces():
        f.scales(SERP_D, size=3, highlight=SERP_L)
    sh = m.cube(tail2, "serpent_head", (-1.9, 17.4, 26.5), (1.9, 20.4, 31))
    sh.paint(SERP, jitter=0.15)
    sh.north.rect(0, 0, 1, 1, EMBER_H).rect(3, 0, 1, 1, EMBER_H)
    sh.up.rect(0, 1, 1, 1, EMBER).rect(3, 1, 1, 1, EMBER)
    sh.down.fill(MOUTH_RED)
    tongue = m.plane(tail2, "serpent_tongue", (-1, 18.9, 31), (1, 18.9, 34))
    tongue.front.rect(0, 0, 2, 3, "#d02a2a").clear(0, 2, 1, 1)

    # --- animations
    idle = m.animation("idle", 2.8, "loop")
    idle_breathe(idle, body="body", head="head_c", tail="tail", amount=0.4, nod=3)
    idle.rot("head_l", {0: (0, 0, 0), 0.9: (4, 6, 0), 1.9: (-3, -5, 0), 2.8: (0, 0, 0)})
    idle.rot("head_r", {0: (2, 0, 0), 1.0: (-3, -6, 0), 2.0: (4, 5, 0), 2.8: (2, 0, 0)})
    idle.rot("jaw_l", {0: (0, 0, 0), 1.4: (9, 0, 0), 2.8: (0, 0, 0)})
    idle.rot("jaw_r", {0: (4, 0, 0), 1.2: (0, 0, 0), 2.8: (4, 0, 0)})
    idle.rot("tail_tip", {0: (0, 14, 0), 1.4: (0, -14, 0), 2.8: (0, 14, 0)})

    walk = m.animation("walk", 0.9, "loop")
    walk_quadruped(walk, front=("leg_front_left", "leg_front_right"), back=("leg_back_left", "leg_back_right"),
                   swing=30, body="body", head="head_c", tail="tail", bob=0.7, gait="trot")
    L = walk.length
    walk.rot("head_l", {0: (3, 8, 0), L / 2: (-3, -8, 0), L: (3, 8, 0)})
    walk.rot("head_r", {0: (-3, -8, 0), L / 2: (3, 8, 0), L: (-3, -8, 0)})
    walk.rot("chest", {0: (1, -3, 0), L / 2: (1, 3, 0), L: (1, -3, 0)})
    walk.rot("tail_tip", {0: (0, 18, 0), L / 2: (0, -18, 0), L: (0, 18, 0)})
    walk.rot("mane", {0: (-2, 0, 0), L / 4: (3, 0, 0), L / 2: (-2, 0, 0), 3 * L / 4: (3, 0, 0), L: (-2, 0, 0)})

    atk = m.animation("attack", 1.1, "once")
    atk.pos("body", {0: (0, 0, 0), 0.3: (0, -1.5, 3), 0.5: (0, 0.5, -7), 0.8: (0, 0, -2), 1.1: (0, 0, 0)}, interp="linear")
    atk.rot("chest", {0: (0, 0, 0), 0.3: (-6, 0, 0), 0.5: (8, 0, 0), 1.1: (0, 0, 0)}, interp="linear")
    for tag, t0, ang_y in (("c", 0.28, 0), ("l", 0.42, 18), ("r", 0.56, -18)):
        atk.rot(f"head_{tag}", {0: (0, 0, 0), t0 - 0.1: (-22, ang_y * -0.5, 0), t0 + 0.12: (14, ang_y, 0),
                                t0 + 0.3: (4, 0, 0), 1.1: (0, 0, 0)}, interp="linear")
        atk.rot(f"jaw_{tag}", {0: (0, 0, 0), t0 - 0.08: (46, 0, 0), t0 + 0.1: (0, 0, 0), t0 + 0.17: (30, 0, 0),
                               t0 + 0.26: (-2, 0, 0), 1.1: (0, 0, 0)}, interp="linear")
        atk.rot(f"neck_{tag}", {0: (0, 0, 0), t0 - 0.1: (-8, 0, 0), t0 + 0.12: (12, 0, 0), 1.1: (0, 0, 0)}, interp="linear")
    atk.rot("leg_front_left", {0: (0, 0, 0), 0.3: (-12, 0, 0), 0.5: (20, 0, 0), 1.1: (0, 0, 0)}, interp="linear")
    atk.rot("leg_front_right", {0: (0, 0, 0), 0.3: (-12, 0, 0), 0.5: (20, 0, 0), 1.1: (0, 0, 0)}, interp="linear")
    atk.rot("tail", {0: (0, 0, 0), 0.5: (-14, 10, 0), 1.1: (0, 0, 0)}, interp="linear")

    death = m.animation("death", 2.0, "once")
    death_fall(death, direction="right")
    for tag, yaw in (("c", 15), ("l", -30), ("r", 40)):
        death.rot(f"head_{tag}", {0: (0, 0, 0), 0.5: (-14, yaw * 0.4, 0), 1.3: (16, yaw, 0), 2.0: (16, yaw, 0)}, interp="linear")
        death.rot(f"jaw_{tag}", {0: (0, 0, 0), 0.8: (28, 0, 0), 2.0: (34, 0, 0)}, interp="linear")
    for n, ang in (("leg_front_left", -30), ("leg_front_right", -25), ("leg_back_left", 20), ("leg_back_right", 28)):
        death.rot(n, {0: (0, 0, 0), 0.6: (ang, 0, 0), 2.0: (ang, 0, 0)}, interp="linear")
    death.rot("tail", {0: (0, 0, 0), 0.8: (0, 30, 0), 2.0: (0, 30, 0)}, interp="linear")
    return m


# =====================================================================================
# ERINYS - Fury: hovering winged spirit with snake hair, scourge and torch
# =====================================================================================
ASH = "#908f9c"
ASH_D = "#5e5c6c"
ROBE = "#1a1722"
ROBE_L = "#322c42"
BLOOD = "#7a1626"
FEATHER = "#17141f"
FEATHER_L = "#403b58"
FEATHER_S = "#2a2638"
EYE_R = "#ff2e3e"
SNAKE = "#3d5a34"
SNAKE_D = "#1b2c1a"
SNAKE_L = "#7aa850"
BONE_C = "#cfc6ae"
FLAME_A = "#ff7a1c"
FLAME_B = "#ffc93a"
FLAME_C = "#fff4b8"


def _feathers(face, taper=0.0, base=FEATHER, lead=2, seed=0):
    """Paint a wing plane: shafts running down each feather, scalloped trailing edge.  Rows run
    front->back (leading edge on top), columns run along the wing."""
    w, h = face.w, face.h
    face.fill(base, noise=0.06)
    for xx in range(w):
        t = xx / max(1, w - 1)
        length = int(h * (1 - taper * t))
        phase = xx % 4
        length -= (0, 2, 4, 1)[phase] if xx > 1 else 0
        face.clear(xx, max(lead + 2, length), 1, h)
        if xx % 4 == 1:
            face.rect(xx, lead, 1, max(0, length - lead - 1), FEATHER_S)
        if xx % 4 == 0:
            face.rect(xx, lead + 2, 1, max(0, length - lead - 3), mix(base, FEATHER_L, 0.45))
    face.rect(0, 0, w, lead, mix(base, FEATHER_L, 0.5))
    face.rect(0, lead, w, 1, FEATHER_S)
    for yy in (lead + 2, lead + 5, lead + 8):  # covert rows
        for xx in range(0, w, 2):
            face.pixel(xx, yy, FEATHER_S)
    face.noise(0.04)
    return face


def build_erinys():
    m = Model("bigcasares_myth_erinys", texture_size=(256, 128))

    body = m.bone("body", pivot=(0, 14, 0))
    torso = m.bone("torso", body, pivot=(0, 16, 0))
    head = m.bone("head", torso, pivot=(0, 26.5, 0))
    hair_r = m.bone("snakes_right", head, pivot=(3.5, 32, 0))
    hair_l = m.bone("snakes_left", head, pivot=(-3.5, 32, 0))
    arm_r = m.bone("arm_right", torso, pivot=(4.9, 25, 0))
    fore_r = m.bone("forearm_right", arm_r, pivot=(4.9, 19, 0))
    arm_l = m.bone("arm_left", torso, pivot=(-4.9, 25, 0))
    fore_l = m.bone("forearm_left", arm_l, pivot=(-4.9, 19, 0))
    lash = m.bone("scourge", fore_r, pivot=(4.9, 13, -9))
    flame = m.bone("flame", fore_l, pivot=(-4.9, 24, -0.5))
    wing_r = m.bone("wing_right", torso, pivot=(3, 24, 2.5), rotation=(0, 0, 38))
    tip_r = m.bone("wing_tip_right", wing_r, pivot=(16, 24, 2.5))
    wing_l = m.bone("wing_left", torso, pivot=(-3, 24, 2.5), rotation=(0, 0, -38))
    tip_l = m.bone("wing_tip_left", wing_l, pivot=(-16, 24, 2.5))
    rags_f = m.bone("rags_front", body, pivot=(0, 9, -3))
    rags_b = m.bone("rags_back", body, pivot=(0, 9, 3))

    # --- torso and robe
    waist = m.cube(torso, "waist", (-2.8, 13.5, -1.8), (2.8, 17.5, 1.8))
    waist.paint(ROBE, jitter=0.2)
    waist.north.rect(0, 0, 6, 1, BLOOD).rect(1, 1, 1, 3, BLOOD)
    chest = m.cube(torso, "chest", (-3.6, 17.2, -2.1), (3.6, 26, 2.1))
    chest.paint(ROBE, noise=0.05, jitter=0.2)
    cn = chest.north
    for yy in range(0, 5):  # V-neck of grey skin with ribs
        cn.rect(yy, 0, 7 - 2 * yy, 1, ASH) if False else None
    for yy in range(5):
        x0 = yy
        cn.rect(x0 + 1, yy, max(0, 5 - 2 * yy), 1, ASH, noise=0.05)
    cn.line(2, 1, 4, 1, ASH_D).line(3, 3, 3, 3, ASH_D)
    cn.line(0, 8, 6, 3, BLOOD).line(1, 8, 7, 3, BLOOD)  # torn sash
    cn.tatter(1, edge="bottom", chance=0.4)
    for f in (chest.east, chest.west):
        f.jagged(ROBE_L, edge="top", depth=(1, 2))
    chest.up.fill(ASH, noise=0.05).rect(0, 0, 7, 1, ROBE)
    cape = m.cube(torso, "shoulders", (-4.4, 23.8, -2.3), (4.4, 26.4, 2.3))
    cape.paint(ROBE, jitter=0.25)
    cape.up.rect(1, 1, 6, 3, ROBE_L, noise=0.1)
    for f in cape.sides():
        f.jagged(ROBE_L, edge="bottom", depth=(0, 1))
    robe_a = m.cube(body, "robe_hips", (-4.2, 8.5, -2.7), (4.2, 14.2, 2.7))
    robe_a.paint(ROBE, jitter=0.25)
    for f in robe_a.sides():
        f.stripe(ROBE_L, every=3, axis="v", offset=1)
        f.jagged(mix(ROBE, "#000", 0.3), edge="bottom", depth=(0, 2))
    robe_b = m.cube(body, "robe_skirt", (-5.2, 5, -3.4), (5.2, 8.7, 3.4))
    robe_b.paint(ROBE, jitter=0.25)
    for f in robe_b.sides():
        f.stripe(ROBE_L, every=3, axis="v", offset=0)
        f.tatter(3, edge="bottom", chance=0.9)

    def rag(bone, name, frm, to, seed_base=ROBE):
        p = m.plane(bone, name, frm, to)
        f = p.front
        f.fill(seed_base, noise=0.07)
        f.stripe(ROBE_L, every=3, axis="v", offset=1)
        f.tatter(max(4, f.h // 2), edge="bottom", chance=0.95)
        f.tatter(2, edge="left", chance=0.5)
        f.tatter(2, edge="right", chance=0.5)
        return p

    rag(rags_f, "rag_front_a", (-4.5, 0.5, -3.6), (0, 6.5, -3.6))
    rag(rags_f, "rag_front_b", (0, 1.5, -3.8), (4.8, 6.8, -3.8))
    rag(rags_b, "rag_back_a", (-5.2, 0.8, 3.6), (0.4, 7, 3.6))
    rag(rags_b, "rag_back_b", (0, 0.3, 3.8), (5.4, 6.5, 3.8))
    rag(rags_f, "rag_side_r", (5.35, 1.2, -2.8), (5.35, 6.8, 2.6)) if False else None
    side_r = m.plane(rags_f, "rag_side_r", (5.3, 1.0, -2.6), (5.3, 7, 2.6))
    side_r.front.fill(ROBE, noise=0.07).tatter(4, chance=0.95)
    side_r.front.stripe(ROBE_L, every=3, axis="v", offset=1)
    side_r.mirror_x(rags_f, "rag_side_l")

    # --- head: grey face with red eyes and fangs, hair overlay, back hair plane
    skull = m.cube(head, "skull", (-3.5, 26.5, -3.5), (3.5, 33.5, 3.5))
    skull.paint(ASH, noise=0.05, jitter=0.15)
    sn = skull.north
    sn.eyes(3.5, 3, w=2, h=1, gap=1, glow=EYE_R, brow=ASH_D)
    sn.rect(3, 3, 1, 3, ASH_D).pixel(3, 5, mix(ASH_D, "#000", 0.3))  # nose
    sn.rect(1, 5, 5, 1, "#2a1018")
    sn.pixel(1, 6, "#f2ecda").pixel(3, 6, "#f2ecda").pixel(5, 6, "#f2ecda").pixel(2, 5, "#f2ecda").pixel(4, 5, "#f2ecda")
    sn.rect(0, 4, 1, 2, ASH_D).rect(6, 4, 1, 2, ASH_D)  # hollow cheeks
    sn.rect(1, 3, 1, 1, mix(EYE_R, "#fff", 0.4))
    for f in (skull.east, skull.west):
        f.rect(1, 3, 2, 1, ASH_D)
    skull.down.fill(ASH_D)
    hair = skull.overlay(0.55, "hair")
    hair.up.fill(FEATHER, noise=0.1)
    hair.north.jagged(FEATHER, edge="top", depth=(1, 3))
    hair.north.rect(0, 0, 1, 5, FEATHER).rect(6, 0, 1, 5, FEATHER)
    for f in (hair.east, hair.west):
        f.rect(2, 0, f.w - 2, 4, FEATHER).jagged(FEATHER, edge="top", depth=(2, 4))
        f.rect(f.w - 3, 0, 3, f.h, FEATHER)
        f.strands(FEATHER_L, density=0.3, length=(2, 3))
    hair.south.fill(FEATHER, noise=0.1).strands(FEATHER_L, density=0.4, length=(2, 4))
    back = m.plane(head, "hair_back", (-4, 19, 4.2), (4, 31, 4.2))
    bf = back.front
    bf.fill(FEATHER, noise=0.1)
    bf.strands(FEATHER_L, density=0.4, length=(3, 6))
    bf.tatter(5, chance=0.95)
    for xx in range(bf.w):
        cut = int(abs(xx - bf.w / 2) * 0.9)
        bf.clear(xx, bf.h - cut, 1, cut)

    # --- snake hair: three snakes on each side
    def snake(bone, tag, x0, y0, z0, length, rot=None, pivot=None):
        c = m.cube(bone, f"snake_{tag}", (x0, y0, z0), (x0 + 1.5, y0 + length, z0 + 1.5), rotation=rot, pivot=pivot)
        c.paint(SNAKE, jitter=0.2)
        for f in c.faces():
            f.scales(SNAKE_D, size=2, highlight=SNAKE_L)
        c.north.rect(0, 0, 1, c.north.h, SNAKE_L)
        hd = m.cube(bone, f"snake_head_{tag}", (x0 - 0.3, y0 - 1.8, z0 - 1.4), (x0 + 1.8, y0 + 0.4, z0 + 1.6),
                    rotation=None)
        hd.paint(SNAKE, jitter=0.15)
        hd.north.rect(0, 0, 1, 1, EYE_R).rect(2, 0, 1, 1, EYE_R)
        hd.down.fill(BLOOD)
        return c, hd

    s1 = snake(hair_r, "r1", 4.0, 22.5, -1.2, 10)
    s2 = snake(hair_r, "r2", 4.3, 21.0, 1.6, 11)
    s3 = snake(hair_r, "r3", 2.4, 33.5, -0.8, 6, rot=(0, 0, -22.5))
    for i, (c, hd) in enumerate((s1, s2, s3), 1):
        c.mirror_x(hair_l, f"snake_l{i}")
        hd.mirror_x(hair_l, f"snake_head_l{i}")

    # --- arms (right built, left mirrored); right holds the scourge, left a torch
    up = m.cube(arm_r, "upper_arm_r", (3.6, 19.2, -1.3), (6.2, 25.2, 1.3))
    up.paint(ASH, jitter=0.12)
    up.north.rect(0, 0, 3, 1, ROBE)
    lo = m.cube(fore_r, "forearm_r", (3.7, 13.8, -1.2), (6.1, 19.4, 1.2))
    lo.paint(ASH, jitter=0.12)
    for f in lo.sides():
        f.rect(0, 0, f.w, 2, ROBE_L).jagged(ROBE, edge="top", depth=(2, 3))
        f.rect(0, 4, f.w, 1, ASH_D)
    hand = m.cube(fore_r, "hand_r", (3.5, 11.8, -1.5), (6.3, 14, 1.5))
    hand.paint(ASH_D, jitter=0.12)
    for xx in range(3):
        hand.north.pixel(xx, 2, BONE_C)
    up.mirror_x(arm_l, "upper_arm_l")
    lo.mirror_x(fore_l, "forearm_l")
    hand.mirror_x(fore_l, "hand_l")
    handle = m.cube(fore_r, "scourge_handle", (4.3, 12.4, -9.5), (5.5, 13.6, 2))
    handle.paint("#4a3020", jitter=0.2)
    handle.north.fill("#c8a248")
    for f in (handle.up, handle.east):
        f.stripe("#2a1a10", every=3, axis="v", offset=1)
    for k, (x, name, plane_axis) in enumerate(((4.9, "lash_a", "x"), (4.9, "lash_b", "z"))):
        if plane_axis == "x":
            p = m.plane(lash, name, (x, 4.5, -13), (x, 13, -9))
        else:
            p = m.plane(lash, name, (2.9, 5.5, -10.6), (6.9, 13, -10.6))
        lf = p.front
        lf.fill("#3a2a1e", noise=0.1)
        for xx in range(0, lf.w):
            h0 = lf.h - (xx * 5) % 6 - 2
            lf.clear(xx, 0, 1, 0)
            lf.clear(xx, h0, 1, lf.h)
        lf.stripe("#1a100a", every=2, axis="v", offset=1)
        lf.stripe("#6a4a2e", every=4, axis="h", offset=1)
        for xx in range(1, lf.w, 2):
            ys = [yy for yy in range(lf.h) if lf.px[yy, xx, 3] > 0]
            if ys:
                lf.pixel(xx, max(ys), IRON_C)
    torch = m.cube(fore_l, "torch_stick", (-5.5, 11.5, -0.6), (-4.3, 25, 0.6))
    torch.paint("#4a3020", jitter=0.2)
    torch.up.fill("#1c1008")
    head_t = m.cube(fore_l, "torch_head", (-6, 22.2, -1.1), (-3.8, 25, 1.1))
    head_t.paint("#2a1c14", jitter=0.2)
    head_t.up.fill(FLAME_A)
    head_t.north.rect(0, 0, 2, 1, "#6a4a2e")
    fl = m.plane(flame, "flame_a", (-8.2, 24.8, -0.3), (-1.8, 35.5, -0.3))
    ff = fl.front
    for yy in range(ff.h):
        t = yy / ff.h
        half = (ff.w / 2) * (1 - t) ** 0.6 * (0.45 + 0.55 * min(1, t * 3.5))
        for xx in range(ff.w):
            d = abs(xx + 0.5 - ff.w / 2)
            if d <= half:
                col = FLAME_C if (d < half * 0.35 and t > 0.45) else FLAME_B if d < half * 0.65 else FLAME_A
                ff.pixel(xx, ff.h - 1 - yy, col)
    ff.noise(0.05)
    fl2 = m.plane(flame, "flame_b", (-4.9, 24.8, -3.2), (-4.9, 34, 2.2))
    gf = fl2.front
    for yy in range(gf.h):
        t = yy / gf.h
        half = (gf.w / 2) * (1 - t) ** 0.7 * (0.4 + 0.6 * min(1, t * 3.5))
        for xx in range(gf.w):
            d = abs(xx + 0.5 - gf.w / 2)
            if d <= half:
                gf.pixel(xx, gf.h - 1 - yy, FLAME_C if (d < half * 0.3 and t > 0.4) else FLAME_B if d < half * 0.6 else FLAME_A)

    # --- wings: bone arm + feather planes (right built, left mirrored)
    def wing(side_bone, tip_bone, sx, tag):
        arm = m.cube(side_bone, f"wing_arm_{tag}", (3, 23.2, 2.0) if sx > 0 else (-16, 23.2, 2.0),
                     (16, 25, 3.6) if sx > 0 else (-3, 25, 3.6))
        arm.paint(ASH_D, jitter=0.15)
        arm.up.stripe(ASH, every=4, axis="v", offset=1)
        claw = m.cube(tip_bone, f"wing_claw_{tag}", (15.5, 23.4, 1.2), (18.5, 24.8, 3.0))
        claw.paint(BONE_C, jitter=0.1)
        inner = m.plane(side_bone, f"wing_in_{tag}", (3, 24.1, 3.4), (16, 24.1, 21))
        _feathers(inner.front, taper=0.15, seed=0)
        outer = m.plane(tip_bone, f"wing_out_{tag}", (16, 24.1, 2.2), (32, 24.1, 20))
        _feathers(outer.front, taper=0.75, base=FEATHER, seed=1)
        return arm, claw, inner, outer

    a, c, i, o = wing(wing_r, tip_r, 1, "r")
    a.mirror_x(wing_l, "wing_arm_l")
    c.mirror_x(tip_l, "wing_claw_l")
    i.mirror_x(wing_l, "wing_in_l")
    o.mirror_x(tip_l, "wing_out_l")

    # --- animations
    import math

    def wing_cycle(anim, amp, rest=18, lag=14, steps=8, tip_amp=1.0):
        L = anim.length
        for sgn, wb, tb in ((1, "wing_right", "wing_tip_right"), (-1, "wing_left", "wing_tip_left")):
            kw, kt = {}, {}
            for k in range(steps + 1):
                t = L * k / steps
                ph = 2 * math.pi * k / steps
                kw[t] = (0, 0, sgn * (rest + amp * math.sin(ph)))
                kt[t] = (0, 0, sgn * (tip_amp * amp * 0.7 * math.sin(ph - math.radians(lag * 4))))
            anim.rot(wb, kw)
            anim.rot(tb, kt)

    idle = m.animation("idle", 2.4, "loop")
    wing_cycle(idle, 14, rest=0)
    idle.pos("body", {0: (0, 0, 0), 0.6: (0, 1.0, 0), 1.2: (0, 0, 0), 1.8: (0, -1.0, 0), 2.4: (0, 0, 0)})
    idle.rot("torso", {0: (0, 0, 0), 1.2: (3, 0, 0), 2.4: (0, 0, 0)})
    idle.rot("head", {0: (0, 8, 0), 1.2: (3, -8, 0), 2.4: (0, 8, 0)})
    idle.rot("snakes_right", {0: (0, 0, 8), 0.8: (6, 0, -6), 1.6: (-5, 0, 10), 2.4: (0, 0, 8)})
    idle.rot("snakes_left", {0: (0, 0, -8), 0.8: (-6, 0, 6), 1.6: (5, 0, -10), 2.4: (0, 0, -8)})
    idle.rot("arm_right", {0: (-12, 0, 6), 1.2: (-20, 0, 8), 2.4: (-12, 0, 6)})
    idle.rot("arm_left", {0: (-30, 0, -6), 1.2: (-24, 0, -8), 2.4: (-30, 0, -6)})
    idle.rot("forearm_left", {0: (-40, 0, 0), 1.2: (-46, 0, 0), 2.4: (-40, 0, 0)})
    idle.rot("scourge", {0: (0, 0, 0), 0.8: (-6, 0, 10), 1.6: (4, 0, -10), 2.4: (0, 0, 0)})
    idle.rot("rags_front", {0: (0, 0, 0), 1.2: (6, 0, 0), 2.4: (0, 0, 0)})
    idle.rot("rags_back", {0: (0, 0, 0), 1.2: (-6, 0, 0), 2.4: (0, 0, 0)})
    idle.scale("flame", {0: (1, 1, 1), 0.3: (1.05, 1.15, 1), 0.6: (0.95, 0.9, 1), 0.9: (1.05, 1.1, 1), 1.2: (1, 1, 1),
                         1.5: (0.95, 1.12, 1), 1.8: (1.05, 0.92, 1), 2.4: (1, 1, 1)})

    walk = m.animation("walk", 1.0, "loop")
    wing_cycle(walk, 34, rest=-6, tip_amp=1.2, steps=8)
    walk.pos("body", {0: (0, 0, 0), 0.25: (0, 1.8, 0), 0.5: (0, 0, 0), 0.75: (0, 1.8, 0), 1.0: (0, 0, 0)})
    walk.rot("body", {0: (14, 0, 0), 0.5: (14, 0, 0), 1.0: (14, 0, 0)})
    walk.rot("torso", {0: (4, -3, 0), 0.5: (4, 3, 0), 1.0: (4, -3, 0)})
    walk.rot("head", {0: (-8, 0, 0), 0.5: (-8, 0, 0), 1.0: (-8, 0, 0)})
    walk.rot("snakes_right", {0: (-18, 0, 16), 0.5: (-30, 0, 8), 1.0: (-18, 0, 16)})
    walk.rot("snakes_left", {0: (-18, 0, -16), 0.5: (-30, 0, -8), 1.0: (-18, 0, -16)})
    walk.rot("arm_right", {0: (-10, 0, 10), 0.5: (-20, 0, 14), 1.0: (-10, 0, 10)})
    walk.rot("arm_left", {0: (-34, 0, -8), 0.5: (-26, 0, -12), 1.0: (-34, 0, -8)})
    walk.rot("forearm_left", {0: (-40, 0, 0), 0.5: (-48, 0, 0), 1.0: (-40, 0, 0)})
    walk.rot("scourge", {0: (-10, 0, 14), 0.5: (14, 0, -14), 1.0: (-10, 0, 14)})
    walk.rot("rags_front", {0: (14, 0, 0), 0.5: (20, 0, 0), 1.0: (14, 0, 0)})
    walk.rot("rags_back", {0: (-16, 0, 0), 0.5: (-24, 0, 0), 1.0: (-16, 0, 0)})
    walk.scale("flame", {0: (1, 1, 1), 0.25: (1, 1.2, 1), 0.5: (1, 0.9, 1), 0.75: (1, 1.15, 1), 1.0: (1, 1, 1)})

    atk = m.animation("attack", 1.2, "once")
    atk.rot("body", {0: (0, 0, 0), 0.4: (-14, 0, 0), 0.6: (62, 0, 0), 0.82: (40, 0, 0), 1.2: (0, 0, 0)}, interp="linear")
    atk.pos("body", {0: (0, 0, 0), 0.4: (0, 3, 5), 0.6: (0, -6, -16), 0.82: (0, -3, -10), 1.2: (0, 0, 0)}, interp="linear")
    atk.rot("arm_right", {0: (-12, 0, 6), 0.4: (-165, 0, 10), 0.58: (-25, 20, 0), 0.82: (-20, 40, 0), 1.2: (-12, 0, 6)},
            interp="linear")
    atk.rot("forearm_right", {0: (0, 0, 0), 0.4: (-30, 0, 0), 0.6: (-5, 0, 0), 1.2: (0, 0, 0)}, interp="linear")
    atk.rot("scourge", {0: (0, 0, 0), 0.4: (30, 0, 0), 0.6: (-40, 0, 0), 0.82: (-10, 0, 20), 1.2: (0, 0, 0)}, interp="linear")
    atk.rot("arm_left", {0: (-30, 0, -6), 0.5: (-60, 0, -40), 0.8: (-30, 0, -20), 1.2: (-30, 0, -6)}, interp="linear")
    atk.rot("forearm_left", {0: (-40, 0, 0), 1.2: (-40, 0, 0)}, interp="linear")
    atk.rot("wing_right", {0: (0, 0, 0), 0.4: (0, 0, 45), 0.62: (0, 0, -35), 1.2: (0, 0, 0)}, interp="linear")
    atk.rot("wing_left", {0: (0, 0, 0), 0.4: (0, 0, -45), 0.62: (0, 0, 35), 1.2: (0, 0, 0)}, interp="linear")
    atk.rot("wing_tip_right", {0: (0, 0, 0), 0.4: (0, 0, 20), 0.62: (0, 0, -30), 1.2: (0, 0, 0)}, interp="linear")
    atk.rot("wing_tip_left", {0: (0, 0, 0), 0.4: (0, 0, -20), 0.62: (0, 0, 30), 1.2: (0, 0, 0)}, interp="linear")
    atk.rot("head", {0: (0, 0, 0), 0.4: (-12, 0, 0), 0.6: (-30, 0, 0), 1.2: (0, 0, 0)}, interp="linear")
    atk.rot("snakes_right", {0: (0, 0, 0), 0.6: (-50, 0, 20), 1.2: (0, 0, 0)}, interp="linear")
    atk.rot("snakes_left", {0: (0, 0, 0), 0.6: (-50, 0, -20), 1.2: (0, 0, 0)}, interp="linear")
    atk.rot("rags_front", {0: (0, 0, 0), 0.6: (-40, 0, 0), 1.2: (0, 0, 0)}, interp="linear")
    atk.rot("rags_back", {0: (0, 0, 0), 0.6: (-45, 0, 0), 1.2: (0, 0, 0)}, interp="linear")

    death = m.animation("death", 2.2, "once")
    death_fall(death, direction="back")
    for sgn, wb, tb in ((1, "wing_right", "wing_tip_right"), (-1, "wing_left", "wing_tip_left")):
        death.rot(wb, {0: (0, 0, 0), 0.4: (0, 0, sgn * 30), 1.0: (-90, 0, sgn * -20), 2.2: (-90, 0, sgn * -20)},
                  interp="linear")
        death.rot(tb, {0: (0, 0, 0), 0.8: (0, 0, sgn * -25), 2.2: (0, 0, sgn * -25)}, interp="linear")
    death.rot("arm_right", {0: (-12, 0, 6), 0.8: (-70, 0, 40), 2.2: (-70, 0, 40)}, interp="linear")
    death.rot("arm_left", {0: (-30, 0, -6), 0.8: (-60, 0, -40), 2.2: (-60, 0, -40)}, interp="linear")
    death.rot("head", {0: (0, 0, 0), 0.6: (-20, 0, 0), 1.4: (12, 22, 0), 2.2: (12, 22, 0)}, interp="linear")
    death.rot("snakes_right", {0: (0, 0, 0), 1.0: (40, 0, 40), 2.2: (40, 0, 40)}, interp="linear")
    death.rot("snakes_left", {0: (0, 0, 0), 1.0: (40, 0, -40), 2.2: (40, 0, -40)}, interp="linear")
    death.scale("flame", {0: (1, 1, 1), 1.0: (0.5, 0.4, 1), 1.6: (0.01, 0.01, 1), 2.2: (0.01, 0.01, 1)}, interp="linear")
    return m


MODELS = {
    "bigcasares_myth_minotaur": build_minotaur,
    "bigcasares_myth_cerberus": build_cerberus,
    "bigcasares_myth_erinys": build_erinys,
}
