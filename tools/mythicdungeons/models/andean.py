"""Andean / South American creatures for the mythic dungeons (Supay, Salamanca viper and goat).

A modest number of stretched cuboids; relief from overlay layers (poncho, fur, scale keels) and
alpha planes (streamers, fur skirts, tongue, glow); detail painted into the texture.  Model
faces north (-Z); the creature's right is +X.
"""
import math
import sys
from pathlib import Path

import numpy as np

sys.path.insert(0, str(Path(__file__).resolve().parents[1]))
from bbkit import Model, death_fall, idle_breathe, mix, ramp, walk_biped, walk_quadruped  # noqa: E402

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


def sine(anim, bone, amp, phase=0.0, steps=8, base=(0, 0, 0), channel="rot"):
    vals = []
    for k in range(steps + 1):
        a = 2 * math.pi * (k % steps) / steps - math.radians(phase)
        vals.append(tuple(base[i] + amp[i] * math.sin(a) for i in range(3)))
    cyc(anim, bone, vals, channel)


# =====================================================================================
# SUPAY / EL TIO - lord of the mines
# =====================================================================================
SKIN = "#a23d27"
SKIN_D = "#5e1e14"
BLACK = "#14100f"
BONE = "#eadfc2"
YELLOW = "#ffd23a"
HELM = "#d9a21b"
IRON = "#8a9096"
WOOD = "#6e4a2a"
TROUSER = "#4d3a2e"
RIB_COLORS = ("#d02a7c", "#f2c230", "#2aa6d0", "#3aa84a", "#d83a2a")


def devil(cube, base=SKIN):
    cube.paint(base, noise=0.05, jitter=0.2)
    for f in cube.faces():
        f.spots(mix(base, SKIN_D, 0.5), n=2, size=(1, 1))
    return cube


def ribbon(face, color, accent):
    """Paper serpentine: coloured strip with a wavy ragged edge and a lighter centre fold."""
    face.fill(color, noise=0.05)
    face.stripe(mix(color, "#fff", 0.28), every=4, axis="h", offset=0, width=1)
    face.stripe(mix(color, "#000", 0.25), every=4, axis="h", offset=2, width=1)
    face.rect(face.w // 2, 0, 1, face.h, mix(color, "#fff", 0.2))
    speckle(face, accent, 0.05)
    face.tatter(3, edge="bottom", chance=0.9)
    face.tatter(1, edge="left", chance=0.5)
    face.tatter(1, edge="right", chance=0.5)
    return face


def build_supay():
    m = Model("bigcasares_myth_supay", texture_size=(256, 128))

    body = m.bone("body", pivot=(0, 12.5, 0))
    torso = m.bone("torso", body, pivot=(0, 16, 0))
    head = m.bone("head", torso, pivot=(0, 29, 0))
    jaw = m.bone("jaw", head, pivot=(0, 29.6, -3.2))
    arm_r = m.bone("arm_right", torso, pivot=(10, 28, 0))
    fore_r = m.bone("forearm_right", arm_r, pivot=(10, 20, 0))
    arm_l = m.bone("arm_left", torso, pivot=(-10, 28, 0))
    fore_l = m.bone("forearm_left", arm_l, pivot=(-10, 20, 0))
    pick = m.bone("pick", fore_r, pivot=(10.2, 12, 0), rotation=(58, 0, 0))
    leg_r = m.bone("leg_right", body, pivot=(3.5, 12.5, 0))
    shin_r = m.bone("shin_right", leg_r, pivot=(3.5, 7.5, 0))
    leg_l = m.bone("leg_left", body, pivot=(-3.5, 12.5, 0))
    shin_l = m.bone("shin_left", leg_l, pivot=(-3.5, 7.5, 0))
    str_r = m.bone("streamers_right", torso, pivot=(5.5, 29, -5.3))
    str_l = m.bone("streamers_left", torso, pivot=(-5.5, 29, -5.3))
    str_b = m.bone("streamers_back", torso, pivot=(0, 29, 5.3))
    glow = m.bone("lamp_glow", head, pivot=(0, 37.5, -8.5))

    # --- legs: miner trousers, rubber boots
    thigh = m.cube(leg_r, "thigh_right", (0.8, 7.5, -2.9), (6.3, 12.8, 2.9))
    thigh.paint(TROUSER, jitter=0.18)
    for f in thigh.sides():
        f.strands(mix(TROUSER, "#000", 0.35), density=0.3, length=(2, 4))
        f.spots(mix(TROUSER, "#c9a064", 0.5), n=1, size=(2, 2))
    shin = m.cube(shin_r, "lower_leg_right", (1.0, 4.2, -2.6), (6.0, 7.8, 2.6))
    shin.paint(TROUSER, jitter=0.18)
    boot = m.cube(shin_r, "boot_right", (0.6, 0, -4.4), (6.4, 5, 3.0))
    boot.paint("#17171b", jitter=0.1)
    boot.north.rect(0, 0, 6, 1, mix("#17171b", "#fff", 0.2))
    boot.east.rect(0, 0, boot.east.w, 1, "#2c2c34")
    boot.up.fill("#2a2a30")
    for f in boot.sides():
        f.rect(0, 0, f.w, 1, "#101014")
        f.rect(1, 2, 1, 2, mix("#17171b", "#fff", 0.18))
    thigh.mirror_x(leg_l, "thigh_left")
    shin.mirror_x(shin_l, "lower_leg_left")
    boot.mirror_x(shin_l, "boot_left")

    # --- pelvis, belt, coca pouch
    pelvis = m.cube(body, "pelvis", (-6, 12, -3.4), (6, 16, 3.4))
    pelvis.paint(TROUSER, jitter=0.18)
    belt = m.cube(body, "belt", (-6.3, 14.6, -3.7), (6.3, 16.6, 3.7))
    belt.paint("#3b2517", jitter=0.1)
    belt.north.rect(5, 0, 3, 2, GOLD).outline(GOLD_D, 5, 0, 3, 2).pixel(6, 1, GOLD_L)
    for f in belt.sides():
        f.stripe(mix("#3b2517", "#000", 0.3), every=3, axis="v", offset=1)
    pouch = m.cube(body, "coca_pouch", (6.3, 11.5, -2), (8.8, 15.8, 2))
    pouch.paint("#b8331f", jitter=0.12)
    for f in pouch.sides():
        f.stripe(YELLOW, every=3, axis="h", offset=1, y=1, h=3)
        f.stripe("#1f7a7a", every=3, axis="h", offset=2, y=1, h=3)
        f.rect(0, 0, f.w, 1, "#3b2517")
    pouch.east.decal([".g.", "ggg", ".g."], {"g": "#3aa84a"}, x=1, y=1)

    # --- torso: bare abdomen, chest with medallion, poncho capelet, leaf garland
    abd = devil(m.cube(torso, "abdomen", (-6, 16, -3.4), (6, 21, 3.4)))
    an = abd.north
    for yy in (0, 2):
        an.rect(1, yy, 4, 1, mix(SKIN, "#e0805a", 0.3)).rect(7, yy, 4, 1, mix(SKIN, "#e0805a", 0.3))
        an.rect(1, yy + 1, 4, 1, mix(SKIN, "#000", 0.3)).rect(7, yy + 1, 4, 1, mix(SKIN, "#000", 0.3))
    an.rect(5, 0, 2, 4, mix(SKIN, "#000", 0.35)).pixel(5, 4, SKIN_D)
    chest = devil(m.cube(torso, "chest", (-8, 21, -4.5), (8, 29, 4.5)))
    cn = chest.north
    cn.rect(1, 1, 6, 4, mix(SKIN, "#e0805a", 0.25)).rect(9, 1, 6, 4, mix(SKIN, "#e0805a", 0.25))
    cn.rect(1, 5, 6, 1, mix(SKIN, "#000", 0.35)).rect(9, 5, 6, 1, mix(SKIN, "#000", 0.35))
    cn.rect(7, 1, 2, 7, mix(SKIN, "#000", 0.3))
    cn.line(2, 6, 5, 7, "#d9a089").line(11, 2, 13, 5, "#d9a089")  # old scars
    poncho = chest.overlay(0.9, "poncho")
    wool = "#8e2420"
    for f in (poncho.north, poncho.south, poncho.east, poncho.west):
        f.fill(wool, noise=0.07)
        f.rect(0, 0, f.w, 1, "#1f7a7a").rect(0, 1, f.w, 1, BONE).rect(0, 3, f.w, 1, "#d9a21b")
        f.rect(0, 5, f.w, 1, "#14100f")
        for xx in range(0, f.w, 2):
            f.pixel(xx, 4, BONE).pixel(xx + 1, 5, "#1f7a7a")
        f.rect(0, 6, f.w, 1, "#d9a21b").rect(0, 7, f.w, 1, wool)
        f.tatter(2, edge="bottom", chance=0.9)
    pn = poncho.north
    for yy in range(pn.h):  # V neck opening shows the bare chest and the gold sun
        hw = 4.8 - yy * 0.55
        for xx in range(pn.w):
            if abs(xx + 0.5 - pn.w / 2) < hw:
                pn.clear(xx, yy, 1, 1)
    poncho.up.fill(wool, noise=0.07).clear(4, 2, 8, 5)
    poncho.up.rect(0, 0, 16, 1, "#1f7a7a")
    poncho.down.fill(wool)
    garland = m.cube(torso, "garland", (-5.6, 28, -4.2), (5.6, 30.2, 4.2))
    garland.paint("#3aa84a", jitter=0.2)
    for f in garland.faces():
        f.dither(mix("#3aa84a", "#000", 0.4), density=0.3, pattern="noise")
        speckle(f, "#e8e0d0", 0.1)
        speckle(f, "#d83a2a", 0.08)
    garland.up.fill("#2e8a3a", noise=0.1).rect(1, 1, 9, 6, SKIN_D)
    pendant = m.plane(torso, "sun_pendant", (-2, 21.5, -5.6), (2, 26.5, -5.6))
    pf = pendant.front
    pf.ellipse(2, 3.5, 2, 2.4, GOLD)
    pf.ellipse(2, 3.5, 1.1, 1.4, GOLD_L)
    pf.rect(0, 0, 4, 1, GOLD_D).pixel(2, 2, "#d02a3a")
    pf.rect(1, 5, 2, 1, GOLD_D)

    # --- head: wide-grin devil face, horns, ears, goat beard
    skull = devil(m.cube(head, "skull", (-4, 29, -4), (4, 36, 4)))
    sn = skull.north
    sn.rect(0, 1, 8, 1, mix(SKIN, "#000", 0.3))
    sn.eyes(4, 2, w=2, h=1, gap=2, glow=YELLOW, socket="#2a0c08", brow=BLACK)
    sn.rect(0, 5, 8, 2, "#2a0a0a")  # the grin: upper teeth row over the dark mouth
    for xx in range(0, 8):
        sn.pixel(xx, 5, BONE if xx % 2 == 0 else mix(BONE, "#7a6a50", 0.5))
    sn.pixel(0, 4, mix(SKIN, "#000", 0.35)).pixel(7, 4, mix(SKIN, "#000", 0.35))
    sn.pixel(2, 3, mix(SKIN, "#e0805a", 0.2)).pixel(5, 3, mix(SKIN, "#e0805a", 0.2))
    for f in (skull.east, skull.west):
        f.rect(0, 5, f.w, 1, mix(BONE, "#7a6a50", 0.3))
    skull.up.fill(SKIN_D, noise=0.06)
    nose = m.cube(head, "nose", (-1.3, 31.2, -5.2), (1.3, 34, -4))
    nose.paint(mix(SKIN, "#e0805a", 0.15), jitter=0.12)
    nose.north.rect(0, 2, 1, 1, "#2a0a0a").rect(2, 2, 1, 1, "#2a0a0a")
    jw = m.cube(jaw, "lower_jaw", (-3.2, 26.6, -3.9), (3.2, 29.8, 3.2))
    jw.paint(SKIN, jitter=0.15)
    jw.north.rect(0, 0, 6, 1, "#2a0a0a")
    for xx in range(0, 6, 2):
        jw.north.pixel(xx, 0, BONE)
    jw.up.fill("#7a1c24", noise=0.05).rect(1, 2, 4, 5, "#c0586a")
    beard = m.cube(jaw, "goat_beard", (-1.6, 22.8, -5.2), (1.6, 27.6, -3.8))
    beard.paint(BLACK, jitter=0.2)
    for f in beard.sides():
        f.strands(mix(BLACK, "#6a5a50", 0.4), density=0.3, length=(2, 4))
        f.tatter(3, chance=0.9)
    cig = m.cube(head, "cigarette", (1.4, 29.8, -9.2), (2.3, 30.7, -4.0))
    cig.paint(BONE, jitter=0.06)
    cig.north.fill("#ff7a1c").pixel(0, 0, "#ffe27a")
    cig.up.rect(0, 0, 1, 2, "#7a6a50")
    ear = devil(m.cube(head, "ear_right", (4, 31, -1.6), (7.2, 34.2, 0.4), rotation=(0, 0, 22.5)))
    ear.north.rect(0, 2, 1, 1, SKIN_D)
    ear.mirror_x(head, "ear_left")
    # curved horns: a horizontal root, a tall bend and a tip curling inward
    horn_c = "#2b1a14"
    h1 = m.cube(head, "horn_root_right", (3.6, 33.2, -1.4), (7.4, 35.8, 1.4))
    h2 = m.cube(head, "horn_bend_right", (6, 34.6, -1.2), (8.4, 38.6, 1.2), rotation=(0, 0, -22.5))
    h3 = m.cube(head, "horn_tip_right", (7.2, 38.6, -1.0), (9.0, 42.4, 1.0), rotation=(0, 0, 22.5))
    for c, tip in ((h1, 0.0), (h2, 0.35), (h3, 0.75)):
        c.paint(mix(horn_c, "#c9b48a", 0.25), jitter=0.12)
        for f in c.sides():
            f.stripe(mix(horn_c, "#000", 0.3), every=3, axis="h", offset=1)
            f.gradient(mix(horn_c, "#c9b48a", 0.4 - tip * 0.3), mix(horn_c, "#000", 0.2 + tip * 0.2), steps=4)
            f.stripe(mix(horn_c, "#000", 0.4), every=3, axis="h", offset=1)
    h1.mirror_x(head, "horn_root_left")
    h2.mirror_x(head, "horn_bend_left")
    h3.mirror_x(head, "horn_tip_left")

    # --- miner's helmet and carbide lamp
    dome = m.cube(head, "helmet", (-4.9, 35.2, -4.9), (4.9, 38.8, 4.9))
    dome.paint(HELM, jitter=0.12)
    for f in dome.sides():
        f.rect(0, f.h - 1, f.w, 1, mix(HELM, "#000", 0.35))
        f.rect(0, 0, f.w, 1, mix(HELM, "#fff", 0.3))
        f.dither(mix(HELM, "#000", 0.25), density=0.12, pattern="noise")
    dome.up.fill(HELM, noise=0.06).rect(4, 0, 2, 10, mix(HELM, "#000", 0.2))
    brim = m.cube(head, "helmet_brim", (-4.9, 34.6, -6.8), (4.9, 35.8, -4.8))
    brim.paint(mix(HELM, "#000", 0.15), jitter=0.12)
    brim.north.rect(0, 0, 10, 1, mix(HELM, "#000", 0.4))
    lamp = m.cube(head, "lamp", (-1.5, 36, -8.6), (1.5, 39, -6.6))
    lamp.paint("#b08a3a", jitter=0.1)
    lamp.north.fill("#7a5a20").ellipse(1.5, 1.5, 1.4, 1.4, "#fff6c0")
    lamp.north.pixel(1, 1, "#ffffff")
    lamp.up.fill("#d2a84a").rect(1, 0, 1, 2, "#7a5a20")
    halo = m.plane(glow, "lamp_halo", (-4, 34, -8.9), (4, 42, -8.9))
    hf = halo.front
    for yy in range(hf.h):
        for xx in range(hf.w):
            d = math.hypot((xx + 0.5 - hf.w / 2) / (hf.w / 2), (yy + 0.5 - hf.h / 2) / (hf.h / 2))
            if d < 1.0:
                a = int(90 * (1 - d) ** 1.6)
                if a > 20:
                    hf.pixel(xx, yy, (255, 236, 150, a))

    # --- arms: gold bracers, wrapped hands; pickaxe in the right fist
    upper = devil(m.cube(arm_r, "upper_arm_right", (8, 20.5, -2.9), (12.6, 28.6, 2.9)))
    upper.north.rect(0, 1, 5, 3, mix(SKIN, "#e0805a", 0.25)).rect(0, 4, 5, 1, mix(SKIN, "#000", 0.3))
    fore = devil(m.cube(fore_r, "lower_arm_right", (8.2, 12.8, -2.6), (12.4, 20.6, 2.6)))
    hand = devil(m.cube(fore_r, "fist_right", (8.0, 9.4, -2.8), (12.6, 12.9, 2.8)))
    hand.north.stripe(mix(SKIN, "#000", 0.4), every=2, axis="v", offset=1, y=1, h=2)
    brace = fore.overlay(0.45, "bracer_right")
    for f in brace.sides():
        f.rect(0, 2, f.w, 4, GOLD).rect(0, 2, f.w, 1, GOLD_L).rect(0, 5, f.w, 1, GOLD_D)
        f.stripe(GOLD_D, every=2, axis="v", offset=1, y=3, h=2)
        f.clear(0, 0, f.w, 2).clear(0, 6, f.w, f.h - 6)
    armlet = upper.overlay(0.45, "armlet_right")
    for f in armlet.sides():
        f.rect(0, 5, f.w, 2, GOLD).rect(0, 5, f.w, 1, GOLD_L).rect(0, 6, f.w, 1, GOLD_D)
        f.clear(0, 0, f.w, 5).clear(0, 7, f.w, f.h - 7)
    upper.mirror_x(arm_l, "upper_arm_left")
    fore.mirror_x(fore_l, "lower_arm_left")
    hand.mirror_x(fore_l, "fist_left")
    brace.mirror_x(fore_l, "bracer_left")
    armlet.mirror_x(arm_l, "armlet_left")
    # pickaxe: handle, iron head with a crescent blade (alpha plane)
    handle = m.cube(pick, "pick_handle", (9.4, -3, -0.9), (11, 17, 0.9))
    handle.paint(WOOD, jitter=0.2)
    for f in handle.sides():
        f.stripe(mix(WOOD, "#000", 0.3), every=3, axis="h", offset=1)
    pk_head = m.cube(pick, "pick_head", (8.2, -6.5, -1.5), (12.2, -2, 1.5))
    pk_head.paint(IRON, jitter=0.1)
    pk_head.north.rect(0, 0, 4, 1, mix(IRON, "#fff", 0.3))
    blade = m.plane(pick, "pick_blade", (1.6, -7, 0), (18.8, -0.5, 0))
    bf = blade.front
    for xx in range(bf.w):
        t = abs((xx + 0.5) - bf.w / 2) / (bf.w / 2)
        y1 = int(bf.h - 1 - t * t * 5)
        y0 = max(0, y1 - 2 + int(t * 2))
        for yy in range(y0, y1 + 1):
            bf.pixel(xx, yy, mix(IRON, "#fff", 0.3) if yy == y0 else IRON)
    bf.noise(0.06)

    # --- paper streamers and confetti ribbons
    for bone, tag, sx in ((str_r, "r", 1), (str_l, "l", -1)):
        for i in range(1):
            x = sx * 6.2
            z = -5.45 - i * 0.12
            pl = m.plane(bone, f"streamer_{tag}{i}", (x - 1.0, 9, z), (x + 1.0, 28, z))
            ribbon(pl.front, RIB_COLORS[(i * 2 + (0 if sx > 0 else 1)) % 5], BONE)
    for i, col in enumerate(RIB_COLORS[:3]):
        x = -4 + i * 4
        pl = m.plane(str_b, f"streamer_back{i}", (x - 1.3, 3 + i * 2, 5.3 + i * 0.12), (x + 1.3, 29, 5.3 + i * 0.12))
        ribbon(pl.front, col, BONE)

    # --- animations
    idle = m.animation("idle", 2.6, "loop")
    idle_breathe(idle, body="torso", head="head", arms=("arm_left", "arm_right"), amount=0.35, nod=2.5)
    cyc(idle, "streamers_right", [(0, 0, 0), (4, 0, 3), (0, 0, 0)])
    cyc(idle, "streamers_left", [(0, 0, 0), (4, 0, -3), (0, 0, 0)])
    cyc(idle, "streamers_back", [(2, 0, 0), (9, 0, 0), (2, 0, 0)])
    cyc(idle, "jaw", [(2, 0, 0), (6, 0, 0), (2, 0, 0)])
    cyc(idle, "lamp_glow", [(1, 1, 1), (1.12, 1.12, 1.12), (1, 1, 1)], "scale")

    walk = m.animation("walk", 1.2, "loop")
    walk_biped(walk, legs=("leg_left", "leg_right"), shins=("shin_left", "shin_right"), arms=("arm_left", "arm_right"),
               body="body", head="head", swing=24, arm_swing=14, knee=24, bob=0.8, sway=4)
    cyc(walk, "torso", [(-3, 0, 0), (-3, 0, 0), (-3, 0, 0)])
    cyc(walk, "arm_right", [(-6, 0, 2), (0, 0, 2), (8, 0, 2), (0, 0, 2), (-6, 0, 2)])
    cyc(walk, "streamers_right", [(-6, 0, 3), (6, 0, 0), (-6, 0, 3), (6, 0, 0), (-6, 0, 3)])
    cyc(walk, "streamers_left", [(6, 0, 0), (-6, 0, -3), (6, 0, 0), (-6, 0, -3), (6, 0, 0)])
    cyc(walk, "streamers_back", [(8, 0, 0), (-2, 0, 0), (8, 0, 0), (-2, 0, 0), (8, 0, 0)])

    atk = m.animation("attack", 1.2, "once")
    atk.rot("arm_right", {0: (0, 0, 0), 0.45: (-165, 0, 0), 0.62: (-52, 0, 0), 0.8: (-45, 0, 0), 1.2: (0, 0, 0)}, interp="linear")
    atk.rot("forearm_right", {0: (0, 0, 0), 0.45: (-20, 0, 0), 0.62: (-4, 0, 0), 1.2: (0, 0, 0)}, interp="linear")
    atk.rot("arm_left", {0: (0, 0, 0), 0.45: (-60, 0, -30), 0.62: (10, 0, -10), 1.2: (0, 0, 0)}, interp="linear")
    atk.rot("torso", {0: (0, 0, 0), 0.45: (-14, 0, 0), 0.62: (20, 0, 0), 0.9: (10, 0, 0), 1.2: (0, 0, 0)}, interp="linear")
    atk.pos("body", {0: (0, 0, 0), 0.45: (0, 0.6, 2), 0.62: (0, -2, -4), 1.2: (0, 0, 0)}, interp="linear")
    atk.rot("head", {0: (0, 0, 0), 0.45: (-14, 0, 0), 0.62: (14, 0, 0), 1.2: (0, 0, 0)}, interp="linear")
    atk.rot("jaw", {0: (2, 0, 0), 0.45: (30, 0, 0), 0.62: (40, 0, 0), 1.2: (2, 0, 0)}, interp="linear")
    atk.rot("leg_right", {0: (0, 0, 0), 0.62: (-22, 0, 0), 1.2: (0, 0, 0)}, interp="linear")
    atk.rot("leg_left", {0: (0, 0, 0), 0.62: (14, 0, 0), 1.2: (0, 0, 0)}, interp="linear")
    atk.rot("streamers_back", {0: (0, 0, 0), 0.62: (26, 0, 0), 1.2: (0, 0, 0)}, interp="linear")

    death = m.animation("death", 2.0, "once")
    death_fall(death, direction="back")
    death.rot("arm_right", {0: (0, 0, 0), 0.5: (-50, 0, 30), 1.3: (-110, 0, 40), 2.0: (-110, 0, 40)}, interp="linear")
    death.rot("arm_left", {0: (0, 0, 0), 0.5: (-40, 0, -25), 1.3: (-100, 0, -35), 2.0: (-100, 0, -35)}, interp="linear")
    death.rot("head", {0: (0, 0, 0), 0.4: (-22, 0, 0), 1.3: (8, 24, 0), 2.0: (8, 24, 0)}, interp="linear")
    death.rot("jaw", {0: (2, 0, 0), 0.6: (36, 0, 0), 2.0: (30, 0, 0)}, interp="linear")
    death.scale("lamp_glow", {0: (1, 1, 1), 0.5: (1.4, 1.4, 1.4), 1.2: (0.2, 0.2, 0.2), 2.0: (0.01, 0.01, 0.01)}, interp="linear")
    return m


# =====================================================================================
# SALAMANCA VIPER - thick patterned serpent on a short bone chain
# =====================================================================================
V_TAN = "#9a7a42"
V_DARK = "#3a2614"
V_RING = "#dcc48a"
V_BELLY = "#ece0b4"
V_EYE = "#c8f03a"
V_TONGUE = "#c01e3a"
V_FANG = "#f4ecd2"


def viper_paint(cube, z0, period=9.0):
    """Yarara-style diamond chain painted with a continuous z phase so segments line up."""
    cube.paint(V_TAN, noise=0.04, jitter=0.15)
    for f in cube.faces():
        f.scales(mix(V_TAN, "#000", 0.16), size=3)
    up = cube.up
    for r in range(up.h):
        z = z0 + r + 0.5
        v = ((z % period) / period) * 2 - 1
        for x in range(up.w):
            u = (x + 0.5 - up.w / 2) / (up.w / 2)
            d = abs(u) * 1.15 + abs(v) * 1.0
            if d < 0.78:
                up.pixel(x, r, V_DARK)
            elif d < 1.02:
                up.pixel(x, r, V_RING)
    up.noise(0.05)
    for f in (cube.east, cube.west):
        for c in range(f.w):
            zz = z0 + (c + 0.5 if f is cube.west else f.w - c - 0.5)
            v = (((zz + period / 2) % period) / period) * 2 - 1
            for r in range(f.h):
                t = r / max(1, f.h - 1)
                if t > 0.72:
                    f.pixel(c, r, V_BELLY)
                elif abs(v) < 0.55 * (1 - t * 0.9) and t < 0.62:
                    f.pixel(c, r, V_DARK if abs(v) < 0.4 * (1 - t * 0.9) else V_RING)
        f.noise(0.05)
    cube.down.fill(V_BELLY, noise=0.04)
    cube.down.stripe(mix(V_BELLY, "#7a6a40", 0.5), every=3, axis="h", offset=1)
    cube.north.fill(V_TAN, noise=0.05).scales(mix(V_TAN, "#000", 0.16), size=3)
    cube.south.fill(V_TAN, noise=0.05).scales(mix(V_TAN, "#000", 0.16), size=3)
    return cube


def build_viper():
    m = Model("bigcasares_myth_salamanca_viper", texture_size=(128, 128))

    body = m.bone("body", pivot=(0, 3.5, 3))
    neck_1 = m.bone("neck_1", body, pivot=(0, 3, -3))
    neck_2 = m.bone("neck_2", neck_1, pivot=(0, 2.7, -9))
    head = m.bone("head", neck_2, pivot=(0, 2.5, -14))
    jaw = m.bone("jaw", head, pivot=(0, 1.6, -14.4))
    tongue = m.bone("tongue", jaw, pivot=(0, 1.1, -22))
    tail_1 = m.bone("tail_1", body, pivot=(0, 3, 9))
    tail_2 = m.bone("tail_2", tail_1, pivot=(0, 2.5, 16))
    tail_3 = m.bone("tail_3", tail_2, pivot=(0, 1.9, 22))
    tail_4 = m.bone("tail_4", tail_3, pivot=(0, 1.3, 27))

    segs = [
        (body, "trunk", 9.0, 7.0, -3.4, 9.4),
        (neck_1, "neck_a", 7.4, 6.0, -9.4, -2.6),
        (neck_2, "neck_b", 6.0, 5.4, -14.4, -8.6),
        (tail_1, "tail_a", 7.6, 6.0, 8.6, 16.4),
        (tail_2, "tail_b", 6.0, 5.0, 15.6, 22.4),
        (tail_3, "tail_c", 4.4, 3.8, 21.6, 27.4),
        (tail_4, "tail_d", 2.8, 2.6, 26.6, 32.0),
    ]
    for bone, name, w, h, za, zb in segs:
        c = m.cube(bone, name, (-w / 2, 0, za), (w / 2, h, zb))
        viper_paint(c, za)
        keel = c.overlay(0.3, name + "_keel")
        for r in range(keel.up.h):
            z = za + r + 0.5
            v = ((z % 9.0) / 9.0) * 2 - 1
            for x in range(keel.up.w):
                u = (x + 0.5 - keel.up.w / 2) / (keel.up.w / 2)
                if abs(u) * 1.15 + abs(v) < 0.52:
                    keel.up.pixel(x, r, mix(V_DARK, "#6a4a24", 0.35))
        keel.up.rect(keel.up.w // 2, 0, 1, keel.up.h, mix(V_TAN, "#fff", 0.2))
        keel.up.noise(0.08)
        speckle(keel.up, V_DARK, 0.1)
    m.bones["tail_4"].cubes[0].north.fill(V_DARK)

    # --- head: triangular skull, snout, fangs, jaw, glowing eyes, forked tongue
    skull = m.cube(head, "skull", (-3.4, 1.4, -19.4), (3.4, 5.4, -14))
    skull.paint(V_TAN, noise=0.04, jitter=0.15)
    for f in skull.faces():
        f.scales(mix(V_TAN, "#000", 0.14), size=3)
    su = skull.up
    su.line(0, 0, 3, 3, V_DARK).line(6, 0, 3, 3, V_DARK).line(1, 0, 3, 2, V_RING).line(5, 0, 3, 2, V_RING)
    su.rect(3, 3, 1, 3, V_DARK)
    su.pixel(0, 1, V_EYE).pixel(6, 1, V_EYE)
    for f, flip in ((skull.east, False), (skull.west, True)):
        f.rect(1, 1, 2, 2, V_EYE)
        f.rect(1 if not flip else 2, 1, 1, 2, V_DARK)  # vertical slit pupil
        f.rect(1, 1, 2, 1, mix(V_EYE, "#fff", 0.4)) if False else None
        f.line(3, 2, f.w - 1, 3, V_DARK)  # dark stripe behind the eye
        f.rect(0, f.h - 1, f.w, 1, V_BELLY)
        f.rect(0, 0, f.w, 1, mix(V_TAN, "#000", 0.25))
    skull.north.rect(0, 0, 7, 1, mix(V_TAN, "#000", 0.25))
    snout = m.cube(head, "snout", (-2.4, 1.4, -22.4), (2.4, 4.6, -19))
    snout.paint(V_TAN, noise=0.04, jitter=0.15)
    snout.up.pixel(1, 0, "#120a06").pixel(3, 0, "#120a06")
    snout.north.rect(0, 0, 5, 1, V_BELLY)
    for f in (snout.east, snout.west):
        f.rect(0, f.h - 1, f.w, 1, V_BELLY)
    fang = m.cube(head, "fang_right", (1.6, -0.2, -22.0), (2.5, 2.4, -21.1))
    fang.paint(V_FANG, jitter=0.08)
    fang.mirror_x(head, "fang_left")
    jw = m.cube(jaw, "lower_jaw", (-2.6, 0, -21.6), (2.6, 1.6, -14.4))
    jw.paint(V_BELLY, noise=0.04, jitter=0.12)
    jw.up.fill("#a02a3e", noise=0.05).rect(2, 1, 1, 6, "#d86a7c")
    for f in (jw.east, jw.west):
        f.rect(0, 0, f.w, 1, mix(V_TAN, V_BELLY, 0.4))
    tp = m.plane(tongue, "tongue_plane", (-1.6, 1.2, -29), (1.6, 1.2, -22))
    tf = tp.front
    for yy in range(tf.h):
        for xx in range(tf.w):
            if yy < 3:  # fork
                if xx in (0, 1, tf.w - 2, tf.w - 1) and xx not in (2,):
                    tf.pixel(xx, yy, V_TONGUE)
            elif abs(xx + 0.5 - tf.w / 2) < 1.0:
                tf.pixel(xx, yy, V_TONGUE)
    tf.pixel(tf.w // 2, tf.h - 1, mix(V_TONGUE, "#000", 0.3))

    # --- animations
    chain = ["head", "neck_2", "neck_1", "body", "tail_1", "tail_2", "tail_3", "tail_4"]

    def wave(anim, amp, phase_deg, steps=8, lift=None, amp_scale=None):
        for i, name in enumerate(chain):
            a = amp * (amp_scale[i] if amp_scale else 1.0)
            vals = []
            for k in range(steps + 1):
                ang = a * math.sin(2 * math.pi * (k % steps) / steps - math.radians(phase_deg) * i)
                vals.append(((lift or {}).get(name, 0), ang, 0))
            cyc(anim, name, vals)

    raise_head = {"neck_1": -12, "neck_2": -18, "head": 22}
    idle = m.animation("idle", 3.0, "loop")
    wave(idle, 3.5, 50, steps=6, lift=raise_head, amp_scale=[0.8, 0.8, 0.8, 0.4, 0.8, 1.2, 1.6, 2.0])
    cyc(idle, "jaw", [(0, 0, 0), (0, 0, 0), (10, 0, 0), (0, 0, 0), (0, 0, 0)])
    cyc(idle, "tongue", [(0, 0, 0), (0, 0, 0), (0, 0, 0), (0, 0, 0), (0, 0, 0)])
    idle.scale("tongue", {0: (1, 1, 0.1), 1.0: (1, 1, 0.1), 1.3: (1, 1, 1), 1.8: (1, 1, 1), 2.1: (1, 1, 0.1), 3.0: (1, 1, 0.1)})
    idle.pos("body", {0: (0, 0, 0), 1.5: (0, 0.3, 0), 3.0: (0, 0, 0)})

    walk = m.animation("walk", 1.6, "loop")
    wave(walk, 14, 55, steps=8, lift={"neck_1": -5, "neck_2": -8, "head": 10},
         amp_scale=[1.0, 0.9, 0.8, 0.4, 0.9, 1.2, 1.5, 1.8])
    walk.scale("tongue", {0: (1, 1, 0.1), 0.5: (1, 1, 1), 0.9: (1, 1, 0.1), 1.6: (1, 1, 0.1)})

    atk = m.animation("attack", 1.3, "once")
    atk.rot("neck_1", {0: (-12, 0, 0), 0.45: (-34, 0, 0), 0.6: (4, 0, 0), 0.85: (-2, 0, 0), 1.3: (-12, 0, 0)}, interp="linear")
    atk.rot("neck_2", {0: (-18, 0, 0), 0.45: (-42, 0, 0), 0.6: (-2, 0, 0), 0.85: (-4, 0, 0), 1.3: (-18, 0, 0)}, interp="linear")
    atk.rot("head", {0: (22, 0, 0), 0.45: (40, 0, 0), 0.6: (2, 0, 0), 0.85: (10, 0, 0), 1.3: (22, 0, 0)}, interp="linear")
    atk.rot("jaw", {0: (0, 0, 0), 0.4: (0, 0, 0), 0.5: (54, 0, 0), 0.64: (58, 0, 0), 0.76: (2, 0, 0), 1.3: (0, 0, 0)}, interp="linear")
    atk.pos("body", {0: (0, 0, 0), 0.45: (0, 0, 4), 0.6: (0, 0, -7), 0.85: (0, 0, -3), 1.3: (0, 0, 0)}, interp="linear")
    atk.rot("tail_2", {0: (0, 0, 0), 0.45: (0, 20, 0), 0.85: (0, -16, 0), 1.3: (0, 0, 0)}, interp="linear")
    atk.rot("tail_4", {0: (0, 0, 0), 0.45: (0, 30, 0), 0.85: (0, -24, 0), 1.3: (0, 0, 0)}, interp="linear")
    atk.scale("tongue", {0: (1, 1, 0.1), 0.4: (1, 1, 1), 0.5: (1, 1, 0.1), 1.3: (1, 1, 0.1)}, interp="linear")

    death = m.animation("death", 2.2, "once")
    death.rot("root", {0: (0, 0, 0), 0.5: (0, 0, 45), 1.1: (0, 0, 130), 1.5: (0, 0, 190), 1.8: (0, 0, 178), 2.2: (0, 0, 180)},
              interp="linear")
    death.pos("root", {0: (0, 0, 0), 0.5: (0, 3, 0), 1.1: (0, 6, 0), 1.5: (0, 7, 0), 2.2: (0, 7, 0)}, interp="linear")
    for i, name in enumerate(chain):
        death.rot(name, {0: (0, 0, 0), 0.5: (6 if i < 3 else -4, 16 * (-1) ** i, 0), 1.0: (-8, -24 * (-1) ** i, 0),
                         1.6: (4, 10 * (-1) ** i, 0), 2.2: (0, 6 * (-1) ** i, 0)}, interp="linear")
    death.rot("jaw", {0: (0, 0, 0), 0.6: (48, 0, 0), 2.2: (36, 0, 0)}, interp="linear")
    return m


# =====================================================================================
# SALAMANCA GOAT - the black he-goat (chivo)
# =====================================================================================
FUR_B = "#241f2a"
FUR_L = "#4a4455"
EMBER = "#ff5a1a"
EMBER_H = "#ffc23a"
HORNC = "#cfc4a2"
HORN_D = "#7a6e50"
HOOFC = "#0c0a0c"


def shaggy(cube, density=0.4, length=(2, 4)):
    cube.paint(FUR_B, noise=0.06, jitter=0.22)
    for f in cube.faces():
        f.strands(FUR_L, density=density, length=length)
        f.strands(mix(FUR_B, "#000", 0.5), density=density * 0.6, length=length)
    return cube


def build_goat():
    m = Model("bigcasares_myth_salamanca_goat", texture_size=(128, 128))

    body = m.bone("body", pivot=(0, 15, 0))
    neck = m.bone("neck", body, pivot=(0, 18, -7))
    head = m.bone("head", neck, pivot=(0, 21, -11))
    fl = m.bone("leg_front_left", body, pivot=(-3.4, 11, -5.2))
    fr = m.bone("leg_front_right", body, pivot=(3.4, 11, -5.2))
    bl = m.bone("leg_back_left", body, pivot=(-3.4, 11, 5.6))
    br = m.bone("leg_back_right", body, pivot=(3.4, 11, 5.6))
    tail = m.bone("tail", body, pivot=(0, 18, 8))
    skirt = m.bone("fur_skirt", body, pivot=(0, 17, 0))

    # --- shaggy body, withers, fur skirts and a dorsal mane
    torso = shaggy(m.cube(body, "torso", (-4.8, 10.5, -7.5), (4.8, 19.5, 8)))
    torso.north.rect(0, 5, 10, 4, mix(FUR_B, FUR_L, 0.3))
    for f in (torso.east, torso.west):  # faint ember runes along the ribs
        for xx in range(2, f.w - 2, 4):
            f.pixel(xx, 3, EMBER).pixel(xx, 4, mix(EMBER, "#000", 0.4)).pixel(xx + 1, 3, mix(EMBER, "#000", 0.5))
        f.pixel(f.w // 2, 2, EMBER_H)
    withers = m.cube(body, "withers", (-4.4, 17.5, -9.5), (4.4, 21.2, -3))
    shaggy(withers, 0.5)
    mane = withers.overlay(0.5, "withers_mane")
    mane.up.fill(FUR_B).noise(0.12).dither(FUR_L, density=0.3, pattern="noise")
    for f in mane.sides():
        f.jagged(FUR_B, edge="top", depth=(2, 4))
        speckle(f, FUR_L, 0.2)
    for sx, tag in ((1, "r"), (-1, "l")):
        sk = m.plane(skirt, f"flank_fur_{tag}", (sx * 5.05, 6.5, -7.5), (sx * 5.05, 17.5, 7.5))
        sf = sk.front
        sf.fill(FUR_B, noise=0.08)
        sf.strands(FUR_L, density=0.5, length=(3, 6))
        sf.strands(mix(FUR_B, "#000", 0.5), density=0.3, length=(3, 6))
        sf.tatter(4, chance=0.95)
        sf.jagged(FUR_B, edge="top", depth=(3, 5))
    spine = m.plane(skirt, "dorsal_mane", (0, 18.5, -9), (0, 23.5, 7))
    pf = spine.front
    pf.fill(FUR_B, noise=0.08)
    pf.strands(FUR_L, density=0.5, length=(2, 4), direction="down")
    pf.tatter(3, edge="top", chance=0.95)
    pf.tatter(2, edge="left", chance=0.5)
    pf.tatter(2, edge="right", chance=0.5)

    # --- neck, head, beard, eyes
    neck_c = shaggy(m.cube(neck, "neck_block", (-2.8, 16.8, -11.8), (2.8, 22.8, -5.6)), 0.5)
    skull = shaggy(m.cube(head, "skull", (-2.6, 19.4, -16.4), (2.6, 24.6, -11)))
    sn = skull.north
    sn.pixel(0, 1, EMBER_H).pixel(4, 1, EMBER_H).pixel(0, 2, "#d02010").pixel(4, 2, "#d02010")
    for f in (skull.east, skull.west):
        f.rect(1, 1, 3, 2, "#d02010")
        f.rect(1, 1, 3, 1, EMBER_H)
        f.rect(2, 1, 1, 2, "#0c0a0c") if False else f.rect(1, 2, 3, 1, "#d02010")
        f.rect(1, 1, 3, 1, EMBER_H).pixel(2, 2, "#0c0a0c")
        f.rect(0, 0, f.w, 1, FUR_B)
    skull.up.dither(FUR_L, density=0.3, pattern="noise")
    snout = m.cube(head, "snout", (-1.9, 18, -20.4), (1.9, 22.2, -16.4))
    snout.paint(mix(FUR_B, "#4a4350", 0.35), noise=0.05, jitter=0.18)
    snout.north.rect(0, 0, 4, 1, "#241f26").rect(0, 3, 4, 1, "#241f26").pixel(0, 1, "#080608").pixel(3, 1, "#080608")
    snout.up.rect(0, 0, 4, 1, "#241f26")
    for f in (snout.east, snout.west):
        f.rect(0, f.h - 1, f.w, 1, "#241f26")
    beard = m.cube(head, "beard", (-1, 13.2, -19.4), (1, 18.6, -17.8))
    beard.paint(FUR_B, jitter=0.2)
    for f in beard.sides():
        f.strands(FUR_L, density=0.4, length=(2, 5))
        f.tatter(3, chance=0.9)
    ear = shaggy(m.cube(head, "ear_right", (2.4, 22.2, -14), (6.4, 23.5, -12), rotation=(0, 0, -22.5)), 0.2)
    ear.up.rect(1, 0, 2, 3, mix(FUR_B, "#7a4a4a", 0.5))
    ear.mirror_x(head, "ear_left")
    # big horns: root rises, the bend sweeps back, the tip curls down
    for sx, tag in ((1, "r"), (-1, "l")):
        pass
    hr1 = m.cube(head, "horn_root_right", (1.4, 24.2, -14.4), (3.6, 27.6, -12), rotation=(22.5, 0, 0))
    hr2 = m.cube(head, "horn_bend_right", (2.2, 26.4, -13.6), (4.6, 28.8, -7.8))
    hr3 = m.cube(head, "horn_sweep_right", (3.2, 25.4, -8.6), (5.4, 28.0, -3.2), rotation=(22.5, 0, 0))
    hr4 = m.cube(head, "horn_tip_right", (4.0, 22.6, -4.8), (5.6, 26.0, -1.0), rotation=(45, 0, 0))
    for i, c in enumerate((hr1, hr2, hr3, hr4)):
        c.paint(HORNC, jitter=0.12)
        for f in c.faces():
            f.stripe(mix(HORNC, HORN_D, 0.7), every=3, axis="h" if f.name in ("north", "south") else "v", offset=1)
        if i == 3:
            for f in c.faces():
                f.gradient(mix(HORNC, HORN_D, 0.4), "#2a241c", steps=4)
    hr1.mirror_x(head, "horn_root_left")
    hr2.mirror_x(head, "horn_bend_left")
    hr3.mirror_x(head, "horn_sweep_left")
    hr4.mirror_x(head, "horn_tip_left")

    # --- legs with cloven hooves
    def leg(bone, tag, z, back):
        s = 1.0
        up_c = shaggy(m.cube(bone, f"upper_{tag}", (2.1, 5.6, z - 1.7), (4.7, 11.2, z + 1.7 + (0.6 if back else 0))), 0.4)
        low = m.cube(bone, f"lower_{tag}", (2.5, 2.6, z - 1.1), (4.3, 5.8, z + 1.1))
        low.paint(mix(FUR_B, FUR_L, 0.2), jitter=0.2)
        hoof = m.cube(bone, f"hoof_{tag}", (2.3, 0, z - 1.5), (4.5, 2.8, z + 1.5))
        hoof.paint(HOOFC, jitter=0.1)
        hoof.north.rect(1, 0, 1, 3, "#000").rect(0, 0, 2, 1, mix(HOOFC, "#fff", 0.2))
        hoof.up.fill(FUR_B)
        fur_ov = low.overlay(0.35, f"fetlock_{tag}")
        for f in fur_ov.sides():
            f.jagged(FUR_B, edge="top", depth=(2, 3))
            speckle(f, FUR_L, 0.25)
        return up_c, low, hoof, fur_ov

    parts = leg(fr, "front_right", -5.2, False)
    for c, nm in zip(parts, ("upper_front_left", "lower_front_left", "hoof_front_left", "fetlock_front_left")):
        c.mirror_x(fl, nm)
    parts = leg(br, "back_right", 5.6, True)
    for c, nm in zip(parts, ("upper_back_left", "lower_back_left", "hoof_back_left", "fetlock_back_left")):
        c.mirror_x(bl, nm)
    tail_c = shaggy(m.cube(tail, "tail_stub", (-0.9, 17, 7.8), (0.9, 21, 9.4), rotation=(-22.5, 0, 0)), 0.3)

    # --- animations
    idle = m.animation("idle", 2.8, "loop")
    idle_breathe(idle, body="body", head="head", tail="tail", amount=0.3, nod=2)
    cyc(idle, "neck", [(0, 0, 0), (-3, 4, 0), (0, 0, 0), (2, -4, 0), (0, 0, 0)])
    cyc(idle, "fur_skirt", [(0, 0, 0), (0, 0, 2), (0, 0, 0), (0, 0, -2), (0, 0, 0)])
    cyc(idle, "leg_front_right", [(0, 0, 0), (0, 0, 0), (-14, 0, 0), (-14, 0, 0), (0, 0, 0)])

    walk = m.animation("walk", 0.9, "loop")
    walk_quadruped(walk, front=("leg_front_left", "leg_front_right"), back=("leg_back_left", "leg_back_right"),
                   swing=26, body="body", head="head", tail="tail", bob=0.5, gait="trot")
    cyc(walk, "neck", [(2, 0, 0), (-2, 0, 0), (2, 0, 0)])
    cyc(walk, "fur_skirt", [(0, 0, 4), (0, 0, -4), (0, 0, 4)])

    atk = m.animation("attack", 1.1, "once")
    atk.rot("neck", {0: (0, 0, 0), 0.3: (-10, 0, 0), 0.5: (22, 0, 0), 0.75: (16, 0, 0), 1.1: (0, 0, 0)}, interp="linear")
    atk.rot("head", {0: (0, 0, 0), 0.3: (-14, 0, 0), 0.5: (46, 0, 0), 0.75: (40, 0, 0), 1.1: (0, 0, 0)}, interp="linear")
    atk.pos("body", {0: (0, 0, 0), 0.3: (0, 0.5, 3), 0.5: (0, -1, -9), 0.75: (0, -0.5, -7), 1.1: (0, 0, 0)}, interp="linear")
    atk.rot("body", {0: (0, 0, 0), 0.3: (-8, 0, 0), 0.5: (7, 0, 0), 1.1: (0, 0, 0)}, interp="linear")
    for n, a, b in (("leg_front_left", -34, 24), ("leg_front_right", -34, 28), ("leg_back_left", 22, -26), ("leg_back_right", 22, -30)):
        atk.rot(n, {0: (0, 0, 0), 0.3: (a, 0, 0), 0.5: (b, 0, 0), 0.75: (b * 0.4, 0, 0), 1.1: (0, 0, 0)}, interp="linear")
    atk.rot("tail", {0: (0, 0, 0), 0.5: (-30, 0, 0), 1.1: (0, 0, 0)}, interp="linear")

    death = m.animation("death", 1.8, "once")
    death_fall(death, direction="right")
    death.rot("head", {0: (0, 0, 0), 0.5: (-18, 20, 0), 1.2: (-8, 30, 0), 1.8: (-8, 30, 0)}, interp="linear")
    for n, a in (("leg_front_left", -40), ("leg_front_right", -55), ("leg_back_left", 30), ("leg_back_right", 40)):
        death.rot(n, {0: (0, 0, 0), 0.6: (a, 0, 0), 1.8: (a, 0, 0)}, interp="linear")
    return m


MODELS = {
    "bigcasares_myth_supay": build_supay,
    "bigcasares_myth_salamanca_viper": build_viper,
    "bigcasares_myth_salamanca_goat": build_goat,
}
