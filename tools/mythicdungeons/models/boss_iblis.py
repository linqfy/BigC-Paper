"""Iblis, the mythic boss of the Arabian dungeon, and the two props of his encounter.

* ``bigcasares_myth_iblis``: ~3.8-block fallen jinn made of smokeless fire: a charcoal body
  cracked with embers, curved black horns, a flaming mane, dark oxblood robes with a gold
  hem, burnt tattered wings and a lower body that dissolves into a tail of fire (he floats).
  Clips: idle, walk, dash, attack (fire throw), trapped, rage, death.
* ``bigcasares_myth_iblis_solomon``: King Solomon (~2.2 blocks), crowned, white beard, royal
  robes and a staff; the seal ring glows on his right hand.  Clips: idle, give.
* ``bigcasares_myth_iblis_vessel``: a sealed brass jar with the seal of Solomon on its lead
  lid.  Clips: idle, armed (rattles, lid lifting), capture (lid open, jar pulsing).

Model faces north (-Z); the creature's right is +X.
"""
import math
import sys
from pathlib import Path

sys.path.insert(0, str(Path(__file__).resolve().parents[1]))
from bbkit import Model, mix  # noqa: E402

CHAR = "#2a1c1c"
CHAR_D = "#140c0e"
EMBER = "#ff6a1a"
EMBER_H = "#ffc040"
EMBER_W = "#fff2b0"
ROBE = "#4a1418"
ROBE_D = "#260a0e"
ROBE_L = "#6c2228"
GOLD = "#d8a830"
GOLD_D = "#8a6418"
GOLD_L = "#fff0a0"
HORN = "#1c1416"
HORN_L = "#4a3a3a"
FEATHER = "#1a1418"
FEATHER_L = "#3a2c30"
FIRE_RAMP = ["#a8200a", EMBER, EMBER_H, EMBER_W]

BRASS = "#c08a3a"
BRASS_D = "#7a5420"
BRASS_L = "#f0c878"
LEAD = "#6a6e78"
LEAD_D = "#3e424a"

KING_ROBE = "#3a2a7a"
KING_ROBE_D = "#1e1446"
KING_WHITE = "#ece6da"
SKIN = "#b88358"
SKIN_D = "#8a5a38"
RUBY = "#d02a3a"
EMERALD = "#2ab06a"
WOOD = "#5a3a22"


def cyc(anim, bone, vals, channel="rot"):
    """Evenly spaced keys over the clip; the last value should equal the first for loops."""
    n = len(vals) - 1
    getattr(anim, channel)(bone, {anim.length * i / n: v for i, v in enumerate(vals)})


def tongues(face, colors, *, base=0.4, amp=0.55, k=2.0):
    """Rising flame tongues painted into an alpha plane."""
    h, w = face.h, face.w
    p1, p2 = face.rng.random() * 6.28, face.rng.random() * 6.28
    for x in range(w):
        t = x / max(1, w - 1)
        val = base + amp * (0.5 + 0.5 * math.sin(2 * math.pi * t * k + p1)) * (
            0.65 + 0.35 * math.sin(2 * math.pi * t * k * 2.3 + p2))
        n = max(1, min(h, int(val * h)))
        for j in range(n):
            frac = j / max(1, n - 1)
            face.pixel(x, h - 1 - j, colors[min(len(colors) - 1, int(frac * len(colors)))])
    return face


def embers(face, p=0.06, colors=(EMBER, EMBER_H)):
    """Glowing cracks: a few short bright lines and specks on charcoal."""
    face.cracks(colors[0], n=max(1, (face.w * face.h) // 40), length=4)
    for _ in range(max(1, int(face.w * face.h * p))):
        face.pixel(face.rng.randrange(face.w), face.rng.randrange(face.h), colors[1])
    return face


def robe(face, base=ROBE, hem=True):
    face.gradient(ROBE_L if base == ROBE else base, ROBE_D, steps=4).noise(0.05)
    face.stripe(mix(base, "#000", 0.35), every=3, axis="v", offset=1)
    if hem:
        face.rect(0, face.h - 2, face.w, 1, GOLD).rect(0, face.h - 1, face.w, 1, GOLD_D)
    return face


# =====================================================================================
# IBLIS
# =====================================================================================

def build_iblis():
    m = Model("bigcasares_myth_iblis", texture_size=(256, 128))

    body = m.bone("body", pivot=(0, 22, 0))
    skirt = m.bone("skirt", body, pivot=(0, 24, 0))
    tail_1 = m.bone("tail_1", skirt, pivot=(0, 13, 0))
    tail_2 = m.bone("tail_2", tail_1, pivot=(0, 6, 0))
    torso = m.bone("torso", body, pivot=(0, 26, 0))
    head = m.bone("head", torso, pivot=(0, 40, -0.5))
    mane = m.bone("mane", head, pivot=(0, 46, 3))
    arm_r = m.bone("arm_right", torso, pivot=(9.5, 38, 0))
    fore_r = m.bone("forearm_right", arm_r, pivot=(10, 31, 0))
    flame_r = m.bone("flame_right", fore_r, pivot=(10, 20, -0.5))
    arm_l = m.bone("arm_left", torso, pivot=(-9.5, 38, 0))
    fore_l = m.bone("forearm_left", arm_l, pivot=(-10, 31, 0))
    flame_l = m.bone("flame_left", fore_l, pivot=(-10, 20, -0.5))
    wing_r = m.bone("wing_right", torso, pivot=(3, 37, 5), rotation=(0, -20, 0))
    wing_l = m.bone("wing_left", torso, pivot=(-3, 37, 5), rotation=(0, 20, 0))
    cape = m.bone("cape", torso, pivot=(0, 39, 5.2))

    # --- robes from the waist down, dissolving into a tail of smokeless fire
    upper_skirt = m.cube(skirt, "skirt_upper", (-7.5, 13, -5.5), (7.5, 25, 5.5))
    for f in upper_skirt.sides():
        robe(f)
    upper_skirt.north.rect(7, 0, 1, 10, GOLD_D).rect(6, 0, 3, 1, GOLD)
    upper_skirt.up.fill(ROBE_D)
    upper_skirt.down.fill(EMBER, noise=0.1)
    sash = upper_skirt.overlay(0.35, "sash")
    for f in sash.sides():
        f.rect(0, 0, f.w, 2, GOLD).rect(0, 2, f.w, 1, GOLD_D).clear(0, 3, f.w, f.h - 3)
        f.pixel(f.w // 2, 0, "#e04a20")
    lower_skirt = m.cube(tail_1, "skirt_lower", (-5.5, 5, -4.5), (5.5, 13.5, 4.5))
    for f in lower_skirt.sides():
        f.gradient(ROBE_D, EMBER, steps=4).noise(0.06)
        f.jagged(EMBER_H, edge="bottom", depth=(1, 3))
    lower_skirt.down.fill(EMBER_H, noise=0.1)
    lower_skirt.up.fill(ROBE_D)
    for name, frm, to in (("fire_z", (-8, 0, 0), (8, 14, 0)), ("fire_x", (0, 0, -7), (0, 14, 7))):
        pl = m.plane(tail_2, name, frm, to)
        tongues(pl.front, FIRE_RAMP, base=0.35, amp=0.6, k=2.5)
    hem_fire = m.plane(tail_1, "hem_fire", (-9, 9, -6.5), (9, 19, -6.5))
    tongues(hem_fire.front, FIRE_RAMP[:3], base=0.2, amp=0.5, k=3.0)

    # --- torso: charcoal flesh cracked with fire, robe wrapped open over the chest
    waist = m.cube(torso, "waist", (-5, 24, -3.5), (5, 29, 3.5))
    waist.paint(CHAR, jitter=0.12)
    for f in waist.sides():
        embers(f, 0.04)
    chest = m.cube(torso, "chest", (-8.5, 28.5, -5), (8.5, 39, 5))
    chest.paint(CHAR, jitter=0.12)
    cn = chest.north
    embers(cn, 0.05)
    cn.rect(2, 2, 5, 4, mix(CHAR, EMBER, 0.25)).rect(10, 2, 5, 4, mix(CHAR, EMBER, 0.25))
    cn.line(8, 1, 8, 9, EMBER).line(9, 1, 9, 9, EMBER_H)
    cn.ellipse(8.5, 4, 1.5, 1.5, EMBER_W)  # the furnace heart
    wrap = chest.overlay(0.4, "robe_wrap")
    for f in wrap.sides():
        robe(f, hem=False)
    # open V over the chest: the burning body shows through
    wn = wrap.north
    for y in range(wn.h):
        half = max(0, 5 - y // 2)
        wn.clear(8 - half, y, 2 * half + 1, 1)
    wn.line(3, 0, 8, 9, GOLD).line(14, 0, 9, 9, GOLD)
    wrap.up.fill(ROBE_D)
    collar = m.cube(torso, "collar", (-6.5, 37, 1), (6.5, 44, 5.5))
    collar.paint(ROBE_D, jitter=0.1)
    for f in collar.sides():
        f.jagged(ROBE_L, edge="top", depth=(1, 3))
        f.rect(0, f.h - 1, f.w, 1, GOLD_D)
    collar.north.gradient(EMBER, ROBE_D, steps=3)

    # --- head: charcoal skull, burning eyes and grin, curled horns, flame mane
    skull = m.cube(head, "skull", (-4.5, 39, -5), (4.5, 48, 4))
    skull.paint(CHAR, jitter=0.12)
    sn = skull.north
    sn.eyes(4.5, 3, w=2, h=1, gap=2, glow=EMBER_H, socket=CHAR_D, brow=CHAR_D)
    sn.rect(2, 6, 5, 1, CHAR_D).rect(2, 7, 5, 1, EMBER).pixel(2, 6, EMBER_W).pixel(6, 6, EMBER_W)
    sn.line(4, 1, 4, 2, EMBER)
    for f in (skull.east, skull.west, skull.south):
        embers(f, 0.03)
    skull.up.fill(CHAR_D, noise=0.06)
    jaw = m.cube(head, "jaw", (-3.5, 37.5, -5.3), (3.5, 40, -1))
    jaw.paint(CHAR_D, jitter=0.1)
    jaw.north.rect(1, 0, 5, 1, EMBER).pixel(1, 0, EMBER_W).pixel(5, 0, EMBER_W)
    ear = m.cube(head, "ear_right", (4.5, 43, -2.5), (7, 44.5, -0.5), rotation=(0, 0, 22.5), pivot=(4.5, 43.5, -1.5))
    ear.paint(CHAR, jitter=0.1)
    ear.mirror_x(head, "ear_left")
    h1 = m.cube(head, "horn_root_right", (3.5, 46, -3.5), (7.5, 49, -0.5))
    h2 = m.cube(head, "horn_mid_right", (6, 47.5, -3.25), (9, 54, -0.75), rotation=(0, 0, -22.5), pivot=(7.5, 47.5, -2))
    h3 = m.cube(head, "horn_tip_right", (7.5, 52.5, -2.75), (9.5, 58, -1.25), rotation=(0, 0, 22.5),
                pivot=(8.5, 52.5, -2))
    for i, h in enumerate((h1, h2, h3)):
        h.paint(HORN, jitter=0.12)
        for f in h.sides():
            f.stripe(HORN_L, every=2, axis="h", offset=i % 2)
    for f in h3.sides():
        f.rect(0, 0, f.w, 1, EMBER)
    h1.mirror_x(head, "horn_root_left")
    h2.mirror_x(head, "horn_mid_left")
    h3.mirror_x(head, "horn_tip_left")
    for name, frm, to in (("mane_z", (-6, 40, 0), (6, 56, 0)), ("mane_x", (0, 40, -3), (0, 56, 9))):
        pl = m.plane(mane, name, (frm[0], frm[1], frm[2] + 4.2), (to[0], to[1], to[2] + 4.2))
        tongues(pl.front, FIRE_RAMP, base=0.45, amp=0.5, k=2.0)

    # --- arms: wide robe sleeves, burning forearms, black claws, fire in the palms
    sleeve = m.cube(arm_r, "sleeve_right", (7.5, 30, -3.5), (13, 39.5, 3.5))
    for f in sleeve.sides():
        robe(f)
    sleeve.up.fill(ROBE_L, noise=0.05).outline(GOLD_D)
    sleeve.down.fill(ROBE_D)
    pauldron = sleeve.overlay(0.45, "pauldron_right")
    for f in pauldron.sides():
        f.rect(0, 0, f.w, 2, GOLD).rect(0, 2, f.w, 1, GOLD_D).clear(0, 3, f.w, f.h - 3)
    pauldron.up.fill(GOLD, noise=0.06).outline(GOLD_D)
    fore = m.cube(fore_r, "forearm_right_c", (8.25, 22, -2.25), (11.75, 31, 2.25))
    fore.paint(CHAR, jitter=0.12)
    for f in fore.sides():
        embers(f, 0.06)
    hand = m.cube(fore_r, "hand_right", (8, 19, -2.5), (12, 22.5, 2.5))
    hand.paint(CHAR_D, jitter=0.1)
    hand.north.stripe(EMBER, every=2, axis="v", offset=0, y=2, h=2)
    sleeve.mirror_x(arm_l, "sleeve_left")
    pauldron.mirror_x(arm_l, "pauldron_left")
    fore.mirror_x(fore_l, "forearm_left_c")
    hand.mirror_x(fore_l, "hand_left")
    for bone, sx in ((flame_r, 1), (flame_l, -1)):
        side = "r" if sx > 0 else "l"
        for name, frm, to in ((f"palm_fire_z_{side}", (sx * 10 - 3.5, 14, -0.5), (sx * 10 + 3.5, 23, -0.5)),
                              (f"palm_fire_x_{side}", (sx * 10, 14, -4), (sx * 10, 23, 3))):
            pl = m.plane(bone, name, frm, to)
            tongues(pl.front, FIRE_RAMP, base=0.5, amp=0.45, k=1.6)

    # --- burnt fallen-angel wings and a back cape
    wing = m.plane(wing_r, "wing_right_c", (3, 14, 5.5), (27, 46, 5.5))
    wf = wing.front
    wf.clear()
    for x in range(wf.w):
        t = x / (wf.w - 1)
        top = int(2 + 10 * t * t)
        bottom = int(wf.h - 1 - 18 * t + 6 * math.sin(t * 9))
        for y in range(top, max(top + 1, bottom)):
            c = FEATHER if (x + y // 3) % 4 else FEATHER_L
            wf.pixel(x, y, c)
        wf.pixel(x, top, mix(FEATHER_L, "#8a7a7a", 0.4))
        if bottom > top + 2 and x % 2 == 0:
            wf.pixel(x, bottom - 1, EMBER).pixel(x, bottom - 2, mix(EMBER, FEATHER, 0.5))
    wf.tatter(4, edge="bottom", chance=0.8)
    wing.mirror_x(wing_l, "wing_left_c")
    cloak = m.plane(cape, "cape_back", (-8, 12, 5.6), (8, 39, 5.6))
    cf = cloak.front
    cf.gradient(ROBE_L, ROBE_D, steps=4).noise(0.06)
    cf.stripe(mix(ROBE, "#000", 0.35), every=4, axis="v", offset=2)
    cf.rect(0, 0, cf.w, 1, GOLD)
    cf.tatter(4)
    tongues_back = m.plane(cape, "cape_fire", (-7, 8, 5.9), (7, 16, 5.9))
    tongues(tongues_back.front, FIRE_RAMP[:3], base=0.3, amp=0.6, k=2.2)

    # ------------------------------------------------------------------ animations
    flames = ("flame_right", "flame_left")
    tails = ("skirt", "tail_1", "tail_2")

    idle = m.animation("idle", 3.0, "loop")
    cyc(idle, "body", [(0, 0, 0), (0, 1.5, 0), (0, 0, 0)], "pos")
    cyc(idle, "torso", [(0, 0, 0), (2, 2, 0), (0, 0, 0), (2, -2, 0), (0, 0, 0)])
    cyc(idle, "head", [(0, 0, 0), (2, 5, 0), (0, 0, 0), (-2, -5, 0), (0, 0, 0)])
    cyc(idle, "arm_right", [(0, 0, 4), (-6, 0, 8), (0, 0, 4)])
    cyc(idle, "arm_left", [(-6, 0, -8), (0, 0, -4), (-6, 0, -8)])
    cyc(idle, "forearm_right", [(-20, 0, 0), (-28, 0, 0), (-20, 0, 0)])
    cyc(idle, "forearm_left", [(-28, 0, 0), (-20, 0, 0), (-28, 0, 0)])
    cyc(idle, "wing_right", [(0, 0, 0), (0, -8, 4), (0, 0, 0)])
    cyc(idle, "wing_left", [(0, 0, 0), (0, 8, -4), (0, 0, 0)])
    for i, b in enumerate(tails):
        cyc(idle, b, [(0, 0, 0), (3 + i, 0, 4 + 2 * i), (0, 0, 0), (3 + i, 0, -4 - 2 * i), (0, 0, 0)])
    for fb in flames + ("mane",):
        cyc(idle, fb, [(1, 1, 1), (1.1, 1.25, 1.1), (0.95, 1.05, 0.95), (1.08, 1.2, 1.08), (1, 1, 1)], "scale")
    cyc(idle, "cape", [(4, 0, 0), (8, 0, 2), (4, 0, 0), (8, 0, -2), (4, 0, 0)])

    walk = m.animation("walk", 1.4, "loop")
    cyc(walk, "body", [(0, 1, 0), (0, 2.2, 0), (0, 1, 0), (0, 2.2, 0), (0, 1, 0)], "pos")
    cyc(walk, "torso", [(10, -4, 0), (11, 0, 0), (10, 4, 0), (11, 0, 0), (10, -4, 0)])
    cyc(walk, "head", [(-6, 3, 0), (-7, 0, 0), (-6, -3, 0), (-7, 0, 0), (-6, 3, 0)])
    cyc(walk, "arm_right", [(-10, 0, 10), (6, 0, 10), (-10, 0, 10)])
    cyc(walk, "arm_left", [(6, 0, -10), (-10, 0, -10), (6, 0, -10)])
    for i, b in enumerate(tails):
        cyc(walk, b, [(14 + 4 * i, 0, 3), (18 + 4 * i, 0, -3), (14 + 4 * i, 0, 3)])
    cyc(walk, "wing_right", [(0, -6, 0), (0, -14, 6), (0, -6, 0)])
    cyc(walk, "wing_left", [(0, 6, 0), (0, 14, -6), (0, 6, 0)])
    cyc(walk, "cape", [(18, 0, 0), (26, 0, 0), (18, 0, 0)])
    for fb in flames + ("mane",):
        cyc(walk, fb, [(1, 1, 1), (1.12, 1.3, 1.12), (1, 1, 1)], "scale")

    dash = m.animation("dash", 0.6, "loop")
    cyc(dash, "body", [(0, 3, 0), (0, 3.6, 0), (0, 3, 0)], "pos")
    cyc(dash, "torso", [(32, 0, 0), (34, 0, 0), (32, 0, 0)])
    cyc(dash, "head", [(-24, 0, 0), (-26, 0, 0), (-24, 0, 0)])
    cyc(dash, "arm_right", [(50, 0, 18), (56, 0, 20), (50, 0, 18)])
    cyc(dash, "arm_left", [(50, 0, -18), (56, 0, -20), (50, 0, -18)])
    for i, b in enumerate(tails):
        cyc(dash, b, [(40 + 10 * i, 0, 4), (46 + 10 * i, 0, -4), (40 + 10 * i, 0, 4)])
    cyc(dash, "wing_right", [(10, -40, 0), (12, -44, 4), (10, -40, 0)])
    cyc(dash, "wing_left", [(10, 40, 0), (12, 44, -4), (10, 40, 0)])
    cyc(dash, "cape", [(50, 0, 0), (60, 0, 0), (50, 0, 0)])
    cyc(dash, "mane", [(1.2, 1.4, 1.2), (1.3, 1.6, 1.3), (1.2, 1.4, 1.2)], "scale")

    atk = m.animation("attack", 1.0, "once")
    atk.rot("arm_right", {0: (0, 0, 0), 0.35: (30, 0, 35), 0.5: (-110, -10, 0), 0.7: (-95, -6, 0), 1.0: (0, 0, 0)},
            interp="linear")
    atk.rot("forearm_right", {0: (0, 0, 0), 0.35: (-70, 0, 0), 0.5: (-5, 0, 0), 1.0: (0, 0, 0)}, interp="linear")
    atk.rot("torso", {0: (0, 0, 0), 0.35: (-10, 22, 0), 0.55: (14, -18, 0), 1.0: (0, 0, 0)}, interp="linear")
    atk.rot("head", {0: (0, 0, 0), 0.35: (-10, 0, 0), 0.55: (8, 0, 0), 1.0: (0, 0, 0)}, interp="linear")
    atk.scale("flame_right", {0: (1, 1, 1), 0.35: (1.8, 2.2, 1.8), 0.5: (0.3, 0.3, 0.3), 0.8: (0.8, 0.8, 0.8),
                              1.0: (1, 1, 1)}, interp="linear")
    atk.rot("wing_right", {0: (0, 0, 0), 0.35: (0, -25, 0), 0.55: (0, 10, 0), 1.0: (0, 0, 0)}, interp="linear")
    atk.rot("wing_left", {0: (0, 0, 0), 0.35: (0, 25, 0), 0.55: (0, -10, 0), 1.0: (0, 0, 0)}, interp="linear")

    trapped = m.animation("trapped", 1.2, "loop")
    cyc(trapped, "body", [(0, -4, 0), (0.6, -5, 0), (0, -4, 0), (-0.6, -5, 0), (0, -4, 0)], "pos")
    cyc(trapped, "root", [(0.92, 0.82, 0.92), (0.88, 0.76, 0.88), (0.92, 0.82, 0.92)], "scale")
    cyc(trapped, "torso", [(18, 0, -8), (22, 0, 8), (18, 0, -8)])
    cyc(trapped, "head", [(-30, -15, 0), (-26, 15, 0), (-30, -15, 0)])
    cyc(trapped, "arm_right", [(-150, 0, 20), (-160, 0, 30), (-150, 0, 20)])
    cyc(trapped, "arm_left", [(-160, 0, -30), (-150, 0, -20), (-160, 0, -30)])
    cyc(trapped, "forearm_right", [(-20, 0, 0), (-35, 0, 0), (-20, 0, 0)])
    cyc(trapped, "forearm_left", [(-35, 0, 0), (-20, 0, 0), (-35, 0, 0)])
    for i, b in enumerate(tails):
        cyc(trapped, b, [(0, -40 - 15 * i, 0), (0, 40 + 15 * i, 0), (0, -40 - 15 * i, 0)])
    cyc(trapped, "wing_right", [(30, 20, 30), (34, 26, 36), (30, 20, 30)])
    cyc(trapped, "wing_left", [(30, -20, -30), (34, -26, -36), (30, -20, -30)])
    for fb in flames + ("mane",):
        cyc(trapped, fb, [(0.6, 0.5, 0.6), (0.5, 0.4, 0.5), (0.6, 0.5, 0.6)], "scale")

    rage = m.animation("rage", 2.0, "once")
    rage.rot("arm_right", {0: (0, 0, 0), 0.4: (-30, 0, 80), 1.4: (-35, 0, 85), 2.0: (0, 0, 0)})
    rage.rot("arm_left", {0: (0, 0, 0), 0.4: (-30, 0, -80), 1.4: (-35, 0, -85), 2.0: (0, 0, 0)})
    rage.rot("head", {0: (0, 0, 0), 0.4: (-32, 0, 0), 0.7: (-30, 5, 0), 1.0: (-32, -5, 0), 1.4: (-30, 0, 0),
                      2.0: (0, 0, 0)})
    rage.rot("torso", {0: (0, 0, 0), 0.4: (-14, 0, 0), 1.4: (-14, 0, 0), 2.0: (0, 0, 0)})
    rage.rot("wing_right", {0: (0, 0, 0), 0.4: (0, 40, -10), 1.4: (0, 45, -12), 2.0: (0, 0, 0)})
    rage.rot("wing_left", {0: (0, 0, 0), 0.4: (0, -40, 10), 1.4: (0, -45, 12), 2.0: (0, 0, 0)})
    for fb in flames + ("mane",):
        rage.scale(fb, {0: (1, 1, 1), 0.4: (1.8, 2.4, 1.8), 1.4: (2.0, 2.6, 2.0), 2.0: (1, 1, 1)})
    rage.scale("torso", {0: (1, 1, 1), 0.4: (1.08, 1.05, 1.08), 1.4: (1.08, 1.05, 1.08), 2.0: (1, 1, 1)})

    death = m.animation("death", 2.4, "once")
    death.rot("torso", {0: (0, 0, 0), 0.4: (-20, 0, 0), 1.2: (10, 0, 0)}, interp="linear")
    death.rot("head", {0: (0, 0, 0), 0.4: (-40, 0, 0), 1.2: (-20, 0, 0)}, interp="linear")
    death.rot("arm_right", {0: (0, 0, 0), 0.4: (-60, 0, 60), 1.2: (-150, 0, 20)}, interp="linear")
    death.rot("arm_left", {0: (0, 0, 0), 0.4: (-60, 0, -60), 1.2: (-150, 0, -20)}, interp="linear")
    death.pos("body", {0: (0, 0, 0), 0.6: (0, 4, 0), 2.4: (0, 18, 0)}, interp="linear")
    death.scale("root", {0: (1, 1, 1), 0.6: (1.06, 1.06, 1.06), 1.6: (0.5, 0.7, 0.5), 2.4: (0.02, 0.05, 0.02)},
                interp="linear")
    for fb in flames + ("mane",):
        death.scale(fb, {0: (1, 1, 1), 0.6: (2.2, 2.8, 2.2), 2.4: (0.1, 0.1, 0.1)}, interp="linear")

    m.validate(require=("idle", "walk", "dash", "attack", "trapped", "rage", "death"), quiet=True)
    return m


# =====================================================================================
# KING SOLOMON
# =====================================================================================

def build_solomon():
    m = Model("bigcasares_myth_iblis_solomon", texture_size=(128, 128), bone_budget=(14, 18), cube_budget=(30, 40))
    body = m.bone("body", pivot=(0, 14, 0))
    torso = m.bone("torso", body, pivot=(0, 14, 0))
    head = m.bone("head", torso, pivot=(0, 25, 0))
    arm_r = m.bone("arm_right", torso, pivot=(6, 24, 0))
    fore_r = m.bone("forearm_right", arm_r, pivot=(6.5, 18, 0))
    ring = m.bone("ring", fore_r, pivot=(6.5, 11.5, -1.5))
    arm_l = m.bone("arm_left", torso, pivot=(-6, 24, 0))
    fore_l = m.bone("forearm_left", arm_l, pivot=(-6.5, 18, 0))
    cape = m.bone("cape", torso, pivot=(0, 25, 3.6))

    gown = m.cube(body, "gown", (-5, 0, -3.5), (5, 14, 3.5))
    for f in gown.sides():
        f.gradient(KING_ROBE, KING_ROBE_D, steps=4).noise(0.05)
        f.rect(0, f.h - 2, f.w, 1, GOLD).rect(0, f.h - 1, f.w, 1, GOLD_D)
    gown.north.rect(4, 0, 2, 12, KING_WHITE).rect(4, 0, 2, 12, mix(KING_WHITE, GOLD, 0.2))
    gown.north.stripe(GOLD, every=3, axis="h", offset=1, x=4, w=2, h=12)
    gown.down.fill(KING_ROBE_D)
    gown.up.fill(KING_ROBE_D)
    chest = m.cube(torso, "chest", (-4.5, 13.5, -3), (4.5, 25, 3))
    chest.paint(KING_ROBE, jitter=0.1)
    chest.north.rect(3, 0, 3, 11, KING_WHITE).stripe(GOLD, every=2, axis="h", x=3, w=3)
    chest.north.rect(0, 0, 9, 1, GOLD).rect(0, 1, 9, 1, GOLD_D)
    mantle = chest.overlay(0.4, "mantle")
    for f in mantle.sides():
        f.rect(0, 0, f.w, 3, GOLD).rect(0, 3, f.w, 1, GOLD_D).clear(0, 4, f.w, f.h - 4)
        for x in range(1, f.w, 3):
            f.pixel(x, 1, RUBY if x % 2 else EMERALD)
    mantle.up.fill(GOLD, noise=0.06)
    belt = gown.overlay(0.3, "belt")
    for f in belt.sides():
        f.clear().rect(0, 0, f.w, 2, GOLD).rect(0, 1, f.w, 1, GOLD_D)
    belt.north.pixel(4, 0, RUBY).pixel(5, 0, RUBY)

    skull = m.cube(head, "skull", (-3.5, 25, -3.5), (3.5, 32, 3.5))
    skull.paint(SKIN, jitter=0.1)
    sn = skull.north
    sn.eyes(3.5, 3, w=1, h=1, gap=2, white=KING_WHITE, pupil="#2a1a10", look="center", brow="#d8d0c0")
    sn.rect(3, 4, 1, 1, SKIN_D)
    skull.south.fill("#d8d0c0", noise=0.05)
    for f in (skull.east, skull.west):
        f.rect(0, 0, f.w, 4, "#d8d0c0").rect(0, 4, 3 if f.name == "east" else f.w, 3, "#d8d0c0")
    beard = m.cube(head, "beard", (-3.6, 19, -4.2), (3.6, 27.5, -1.5))
    beard.paint(KING_WHITE, jitter=0.12)
    for f in beard.sides():
        f.strands("#bab2a6", density=0.4, length=(2, 4))
        f.jagged("#d0c8bc", edge="bottom", depth=(1, 2))
    beard.north.rect(2, 0, 3, 1, "#8a6a58")
    crown = m.cube(head, "crown", (-3.9, 31, -3.9), (3.9, 34.5, 3.9))
    crown.paint(GOLD, jitter=0.1)
    for f in crown.sides():
        f.jagged(GOLD_L, edge="top", depth=(1, 2))
        f.rect(0, f.h - 1, f.w, 1, GOLD_D)
        f.pixel(f.w // 2, f.h - 2, RUBY)
        f.pixel(1, f.h - 2, EMERALD).pixel(f.w - 2, f.h - 2, EMERALD)
    crown.up.fill(KING_ROBE, noise=0.06).outline(GOLD)

    sleeve = m.cube(arm_r, "sleeve_right", (4.5, 17.5, -2.25), (8.5, 25, 2.25))
    sleeve.paint(KING_ROBE, jitter=0.1)
    sleeve.up.fill(GOLD)
    fore = m.cube(fore_r, "forearm_right_c", (4.75, 11, -2), (8.25, 18, 2))
    fore.paint(KING_ROBE, jitter=0.1)
    for f in fore.sides():
        f.rect(0, f.h - 3, f.w, 3, SKIN).rect(0, f.h - 4, f.w, 1, GOLD)
    sleeve.mirror_x(arm_l, "sleeve_left")
    fore.mirror_x(fore_l, "forearm_left_c")
    band = m.cube(ring, "ring_band", (5.25, 11, -2.35), (7.75, 12.25, -1.85))
    band.paint(GOLD, jitter=0.05)
    gem = m.cube(ring, "ring_seal", (6, 12.25, -2.6), (7, 13, -1.6))
    gem.paint(RUBY, jitter=0.05)
    gem.up.decal([".g.", "gwg", ".g."], {"g": GOLD_L, "w": "#ffffff"})
    staff = m.cube(fore_l, "staff", (-7.25, 0, -3.5), (-5.75, 30, -2))
    staff.paint(WOOD, jitter=0.15)
    for f in staff.sides():
        f.stripe(mix(WOOD, "#000", 0.3), every=5, axis="h", offset=2)
        f.rect(0, 0, f.w, 2, GOLD)
    knob = m.cube(fore_l, "staff_head", (-8, 30, -4.25), (-5, 33, -1.25))
    knob.paint(GOLD, jitter=0.08)
    knob.north.pixel(1, 1, EMERALD)

    robe_back = m.plane(cape, "cape_back", (-5.5, 1, 3.7), (5.5, 25, 3.7))
    cf = robe_back.front
    cf.gradient(KING_ROBE, KING_ROBE_D, steps=4).noise(0.05)
    cf.rect(0, 0, cf.w, 1, GOLD).outline(GOLD_D, sides="lr")
    cf.decal(["..g..", ".g.g.", "g...g", ".g.g.", "..g.."], {"g": GOLD}, x=cf.w // 2 - 2, y=6)

    idle = m.animation("idle", 3.0, "loop")
    cyc(idle, "torso", [(0, 0, 0), (1.5, 0, 0), (0, 0, 0)])
    cyc(idle, "head", [(0, 0, 0), (2, 6, 0), (0, 0, 0), (2, -6, 0), (0, 0, 0)])
    cyc(idle, "arm_right", [(-4, 0, 2), (-8, 0, 3), (-4, 0, 2)])
    cyc(idle, "forearm_right", [(-30, 0, 0), (-34, 0, 0), (-30, 0, 0)])
    cyc(idle, "arm_left", [(-10, 0, -4), (-12, 0, -4), (-10, 0, -4)])
    cyc(idle, "forearm_left", [(-12, 0, 0), (-14, 0, 0), (-12, 0, 0)])
    cyc(idle, "cape", [(2, 0, 0), (5, 0, 0), (2, 0, 0)])
    cyc(idle, "ring", [(1, 1, 1), (1.2, 1.2, 1.2), (1, 1, 1)], "scale")

    give = m.animation("give", 1.6, "once")
    give.rot("arm_right", {0: (-4, 0, 2), 0.5: (-70, -10, 0), 1.1: (-80, -15, 0), 1.6: (-4, 0, 2)})
    give.rot("forearm_right", {0: (-30, 0, 0), 0.5: (-10, 0, 0), 1.1: (-5, 0, 0), 1.6: (-30, 0, 0)})
    give.rot("torso", {0: (0, 0, 0), 0.5: (8, 0, 0), 1.1: (10, 0, 0), 1.6: (0, 0, 0)})
    give.rot("head", {0: (0, 0, 0), 0.5: (10, 0, 0), 1.1: (12, 0, 0), 1.6: (0, 0, 0)})
    give.scale("ring", {0: (1, 1, 1), 0.5: (1.6, 1.6, 1.6), 1.1: (2.0, 2.0, 2.0), 1.6: (1, 1, 1)})
    m.validate(require=("idle", "give"), quiet=True)
    return m


# =====================================================================================
# SOLOMON'S VESSEL
# =====================================================================================

def build_vessel():
    m = Model("bigcasares_myth_iblis_vessel", texture_size=(128, 64), bone_budget=(6, 8), cube_budget=(12, 16))
    jar = m.bone("jar", pivot=(0, 0, 0))
    lid = m.bone("lid", jar, pivot=(0, 16, 0))

    foot = m.cube(jar, "foot", (-4.5, 0, -4.5), (4.5, 1.5, 4.5))
    foot.paint(BRASS_D, jitter=0.1)
    belly = m.cube(jar, "belly", (-6.5, 1.5, -6.5), (6.5, 11.5, 6.5))
    belly.paint(BRASS, jitter=0.14)
    for f in belly.sides():
        f.gradient(BRASS_L, BRASS_D, steps=5).noise(0.05)
        f.rect(0, 1, f.w, 1, BRASS_D).rect(0, f.h - 2, f.w, 1, BRASS_D)
        f.stripe(mix(BRASS, "#000", 0.25), every=2, axis="v", y=2, h=1)
        f.decal(["...g...",
                 "..g.g..",
                 "ggggggg",
                 ".g...g.",
                 "ggggggg",
                 "..g.g..",
                 "...g..."], {"g": GOLD_L}, x=f.w // 2 - 3, y=2)
        f.pixel(f.w // 2, 5, RUBY)
    belly.up.fill(BRASS_D)
    shoulder = m.cube(jar, "shoulder", (-5, 11.5, -5), (5, 13.5, 5))
    shoulder.paint(BRASS_D, jitter=0.1)
    neck = m.cube(jar, "neck", (-3, 13.5, -3), (3, 16, 3))
    neck.paint(BRASS, jitter=0.1)
    for f in neck.sides():
        f.rect(0, 0, f.w, 1, BRASS_L)
    for side, sx in (("right", 1), ("left", -1)):
        handle = m.plane(jar, f"handle_{side}", (sx * 6.6, 6, -1.5), (sx * 6.6, 15, 1.5))
        hf = handle.front
        hf.clear().outline(BRASS_D).rect(0, 0, hf.w, 1, BRASS_L)
    cap = m.cube(lid, "lid_c", (-3.75, 16, -3.75), (3.75, 18, 3.75))
    cap.paint(LEAD, jitter=0.12)
    for f in cap.sides():
        f.rect(0, 0, f.w, 1, mix(LEAD, "#fff", 0.25))
    cap.up.fill(LEAD_D).decal(["..g..", ".ggg.", "gg.gg", ".ggg.", "..g.."], {"g": GOLD}, x=1, y=1)
    knob = m.cube(lid, "seal_knob", (-1, 18, -1), (1, 19.5, 1))
    knob.paint(RUBY, jitter=0.1)
    glow = belly.overlay(0.5, "glow")
    for f in glow.sides():
        f.clear()
        f.decal(["...y...", "..y.y..", "yyyyyyy", ".y...y.", "yyyyyyy", "..y.y..", "...y..."],
                {"y": "#ffe9a0c0"}, x=f.w // 2 - 3, y=2)

    idle = m.animation("idle", 2.0, "loop")
    cyc(idle, "lid", [(0, 0, 0), (0, 0.15, 0), (0, 0, 0)], "pos")
    cyc(idle, "jar", [(1, 1, 1), (1.01, 1.01, 1.01), (1, 1, 1)], "scale")

    armed = m.animation("armed", 0.5, "loop")
    cyc(armed, "jar", [(0, 0, -3), (0, 8, 3), (0, 0, -3)])
    cyc(armed, "lid", [(0, 0.5, 0), (0, 2.0, 0), (0, 0.5, 0)], "pos")
    cyc(armed, "lid", [(0, -20, 0), (0, 20, 0), (0, -20, 0)])
    cyc(armed, "jar", [(1.05, 1.05, 1.05), (1.12, 1.08, 1.12), (1.05, 1.05, 1.05)], "scale")

    capture = m.animation("capture", 0.8, "loop")
    cyc(capture, "lid", [(0, 6, 0), (0, 7.5, 0), (0, 6, 0)], "pos")
    cyc(capture, "lid", [(-25, -30, 0), (-25, 30, 0), (-25, -30, 0)])
    cyc(capture, "jar", [(1.15, 1.1, 1.15), (1.25, 1.2, 1.25), (1.15, 1.1, 1.15)], "scale")
    cyc(capture, "jar", [(0, 0, -6), (0, 0, 6), (0, 0, -6)])
    m.validate(require=("idle", "armed", "capture"), quiet=True)
    return m


REQUIRED_ANIMATIONS = ("idle",)

MODELS = {
    "bigcasares_myth_iblis": build_iblis,
    "bigcasares_myth_iblis_solomon": build_solomon,
    "bigcasares_myth_iblis_vessel": build_vessel,
}
