"""Odin, the All-Father (boss of the Norse dungeon), and his guardians: the ravens Hugin and
Munin, the wolves Geri and Freki and the einherjar (fallen warriors of Valhalla).

Same approach as norse.py: varied cuboids, relief painted into shaded textures, overlay layers
(mail, bracers, fur fringes) and alpha planes (cloak, beard, hat brim, wings, blades).
Odin carries BOTH headgears: the wide-brimmed wanderer hat (bone ``hat``) and the horned and
winged war helmet (bone ``helmet``).  Every Odin clip keys the scale of both bones, so phase-1
clips (idle, cast) show the hat and phase-2 clips (rage ends with the helmet on, idle_casco,
carga, embestida, stunned, walk, death) show the helmet.  The encounter stops the previous loop
before starting the next one.  Model faces north (-Z); the creature's right is +X.
"""
import sys
from pathlib import Path

sys.path.insert(0, str(Path(__file__).resolve().parents[1]))
from bbkit import (Model, attack_swing, death_fall, idle_breathe, mix,  # noqa: E402
                   walk_biped, walk_quadruped)

# Odin's own clips have no generic "attack"; the guardians are checked for it in their builders.
REQUIRED_ANIMATIONS = ("idle", "walk", "death")
ODIN_CLIPS = ("idle", "cast", "rage", "idle_casco", "walk", "carga", "embestida", "stunned", "death")
MOB_CLIPS = ("idle", "walk", "attack", "death")

HIDDEN = (0.001, 0.001, 0.001)
SHOWN = (1, 1, 1)


def dk(c, t=0.4):
    return mix(c, "#000000", t)


def lt(c, t=0.3):
    return mix(c, "#ffffff", t)


KNOT = [".gg..gg.", "g..gg..g", ".gg..gg."]
VALKNUT = [  # three interlaced triangles, 7x5
    "...g...",
    "..g.g..",
    ".ggggg.",
    "g.g.g.g",
    "ggggggg",
]


def _headgear(anim, helmet_on):
    """Keys the hat/helmet visibility over the whole clip."""
    for t in (0, anim.length):
        anim.scale("hat", {t: HIDDEN if helmet_on else SHOWN}, interp="linear")
        anim.scale("helmet", {t: SHOWN if helmet_on else HIDDEN}, interp="linear")


def _check(m, clips):
    missing = [c for c in clips if c not in m.animations]
    if missing:
        raise ValueError(f"{m.name}: missing clips {missing}")
    return m


# =============================================================================== ODIN

def build_odin():
    SKIN, SKIN_D = "#c99a7c", "#94664e"
    BEARD, BEARD_D, BEARD_L = "#b9b9b3", "#76767a", "#e2e2dc"
    CLOAK, CLOAK_D = "#2c4c8e", "#16274f"
    TUNIC, MAIL = "#4d5566", "#8a929c"
    GOLD, LEATHER, FUR, FUR_D = "#d9aa3a", "#5a3a22", "#6e5a46", "#3e3127"
    HAT, IRON, WING, WOOD = "#3a3f4c", "#a3acb5", "#e9edf0", "#6a4a2c"
    EYE = "#9fe3ff"

    m = Model("bigcasares_myth_odin", texture_size=(256, 256))
    body = m.bone("body", pivot=(0, 20, 0))
    torso = m.bone("torso", body, pivot=(0, 24, 0))
    head = m.bone("head", torso, pivot=(0, 40, 0))
    hat = m.bone("hat", head, pivot=(0, 46, 0))
    helmet = m.bone("helmet", head, pivot=(0, 46, 0))
    beard = m.bone("beard", head, pivot=(0, 42, -5))
    arm_r = m.bone("arm_right", torso, pivot=(11, 38, 0))
    fore_r = m.bone("forearm_right", arm_r, pivot=(11, 29, 0))
    spear = m.bone("spear", fore_r, pivot=(11, 19.5, 0))
    arm_l = m.bone("arm_left", torso, pivot=(-11, 38, 0))
    fore_l = m.bone("forearm_left", arm_l, pivot=(-11, 29, 0))
    cloak = m.bone("cloak", torso, pivot=(0, 40, 6))
    skirt = m.bone("skirt", body, pivot=(0, 24, 0))
    leg_r = m.bone("leg_right", body, pivot=(4, 20, 0))
    shin_r = m.bone("shin_right", leg_r, pivot=(4, 10, 0))
    leg_l = m.bone("leg_left", body, pivot=(-4, 20, 0))
    shin_l = m.bone("shin_left", leg_l, pivot=(-4, 10, 0))

    # --- legs: dark wool trousers, wrapped shins, fur-topped boots
    thigh = m.cube(leg_r, "thigh_right", (1, 10, -3), (7, 20, 3))
    thigh.paint("#3d3a3a", jitter=0.2)
    for f in thigh.sides():
        f.stripe(dk("#3d3a3a", 0.3), every=3, axis="v", offset=1)
    shin = m.cube(shin_r, "shin_wrap_right", (1.25, 3, -3), (6.75, 10, 3))
    shin.paint("#7a6a55", jitter=0.2)
    for f in shin.sides():
        f.line(0, 0, f.w - 1, 2, LEATHER).line(0, 3, f.w - 1, 5, LEATHER).line(0, 6, f.w - 1, 8, dk(LEATHER, 0.2))
    boot = m.cube(shin_r, "boot_right", (1, 0, -4), (7, 3.5, 3))
    boot.paint(LEATHER, jitter=0.15)
    boot.north.rect(0, 0, 6, 1, lt(LEATHER, 0.2))
    cuff = boot.overlay(0.35, "boot_cuff_right")
    for f in cuff.sides():
        f.jagged(FUR, edge="top", depth=(1, 2)).noise(0.1)
    thigh.mirror_x(leg_l, "thigh_left")
    shin.mirror_x(shin_l, "shin_wrap_left")
    boot.mirror_x(shin_l, "boot_left")
    cuff.mirror_x(shin_l, "boot_cuff_left")

    # --- hips, belt and the long tunic skirt (front/back planes so the legs can walk)
    pelvis = m.cube(body, "pelvis", (-7, 18, -4), (7, 24, 4))
    pelvis.paint(TUNIC, jitter=0.2)
    belt = m.cube(torso, "belt", (-8.5, 23, -5), (8.5, 26, 5))
    belt.paint(LEATHER, jitter=0.12)
    belt.north.rect(7, 0, 3, 3, GOLD).outline(dk(GOLD, 0.4), 7, 0, 3, 3).pixel(8, 1, lt(GOLD, 0.5))
    for f in belt.sides():
        f.rect(0, 0, f.w, 1, lt(LEATHER, 0.25)).rivets(GOLD, every=4)
    for name, z, flip in (("skirt_front", -4.9, False), ("skirt_back", 4.7, True)):
        p = m.plane(skirt, name, (-7.5, 7, z), (7.5, 24, z))
        pf = p.front
        pf.gradient(TUNIC, dk(TUNIC, 0.35), steps=4).noise(0.07)
        pf.stripe(dk(TUNIC, 0.4), every=4, axis="v", offset=2)
        pf.rect(0, pf.h - 3, pf.w, 2, CLOAK).decal(KNOT, {"g": GOLD}, x=3, y=pf.h - 4)
        pf.tatter(2, chance=0.35)

    # --- chest: tunic over mail, gold valknut brooch
    chest = m.cube(torso, "chest", (-8, 25, -4.5), (8, 40, 4.5))
    chest.paint(TUNIC, jitter=0.22)
    for f in chest.faces():
        f.dither((0, 0, 0, 25), pattern="checker")
    n = chest.north
    n.rect(5, 0, 6, 15, MAIL).dither(dk(MAIL, 0.35), pattern="checker", x=5, w=6).stripe(lt(MAIL, 0.2), every=2, axis="h", x=5, w=6)
    n.rect(4, 0, 1, 15, GOLD).rect(11, 0, 1, 15, GOLD)
    n.decal(VALKNUT, {"g": GOLD}, x=1, y=9)
    hem = chest.overlay(0.35, "mail_hem")
    for f in hem.sides():
        f.rect(0, f.h - 3, f.w, 3, MAIL).dither((0, 0, 0, 70), y=f.h - 3, h=3).jagged(dk(MAIL, 0.2), edge="bottom", depth=(0, 1))

    # --- fur mantle across the shoulders
    mantle = m.cube(torso, "mantle", (-9.5, 36, -5.5), (9.5, 41, 5.5))
    mantle.paint(FUR, noise=0.05, jitter=0.2)
    for f in mantle.sides():
        f.strands(FUR_D, density=0.45, length=(2, 3)).strands(lt(FUR, 0.25), density=0.15, length=(1, 2))
    mantle.up.fill(FUR_D).noise(0.12).dither(lt(FUR, 0.2), density=0.25, pattern="noise")
    fringe = mantle.overlay(0.4, "mantle_fringe")
    for f in fringe.sides():
        f.jagged(FUR_D, edge="bottom", depth=(1, 3)).noise(0.1)
    fringe.north.rect(8, 1, 3, 3, GOLD).pixel(9, 2, lt(GOLD, 0.6))  # cloak clasp

    # --- blue travelling cloak
    back = m.plane(cloak, "cloak_back", (-9.5, 4, 6), (9.5, 40, 6))
    cb = back.front
    cb.gradient(CLOAK, CLOAK_D, steps=5).noise(0.07)
    cb.stripe(dk(CLOAK, 0.35), every=5, axis="v", offset=2, y=4)
    cb.rect(0, cb.h - 4, cb.w, 2, dk(CLOAK_D, 0.2)).decal(KNOT, {"g": GOLD}, x=2, y=cb.h - 5)
    cb.decal(KNOT, {"g": GOLD}, x=10, y=cb.h - 5).tatter(3, chance=0.5)
    for side, cx in (("right", 9.6), ("left", -9.6)):
        flap = m.plane(cloak, f"cloak_side_{side}", (cx, 12, -3), (cx, 40, 6))
        ff = flap.front
        ff.gradient(CLOAK, CLOAK_D, steps=4).noise(0.07).stripe(dk(CLOAK, 0.3), every=4, axis="h", offset=1, y=20)
        ff.rect(0, ff.h - 2, ff.w, 1, GOLD).tatter(3, chance=0.5)

    # --- head: weathered old king, eye patch over his left eye, grey beard and hair
    skull = m.cube(head, "skull", (-5, 40, -5), (5, 50, 5))
    skull.paint(SKIN, jitter=0.18)
    f = skull.north
    f.rect(0, 0, 10, 3, BEARD).rect(0, 3, 1, 3, BEARD).rect(9, 3, 1, 3, BEARD)
    f.rect(1, 4, 3, 1, BEARD_L)  # brow over the seeing eye
    f.rect(2, 5, 2, 1, EYE).pixel(2, 5, lt(EYE, 0.5))  # his right eye (viewer's left)
    f.rect(6, 4, 3, 3, "#1e1a18").line(4, 3, 9, 6, "#1e1a18").line(0, 2, 5, 4, dk(LEATHER, 0.3))  # patch + strap
    f.rect(4, 5, 2, 3, SKIN_D).pixel(4, 7, dk(SKIN_D, 0.3))  # nose shadow
    f.rect(1, 7, 8, 3, BEARD).rect(2, 8, 6, 1, BEARD_D)  # moustache over the beard
    for s in (skull.east, skull.west):
        s.rect(0, 0, 10, 3, BEARD).rect(4, 0, 6, 10, BEARD).strands(BEARD_D, density=0.5, length=(2, 5), x=4)
        s.rect(2, 4, 2, 2, SKIN_D)
    skull.south.fill(BEARD).strands(BEARD_D, density=0.6, length=(3, 6)).strands(BEARD_L, density=0.3, length=(2, 4))
    skull.up.fill(BEARD).noise(0.1)
    nose = m.cube(head, "nose", (-1, 43, -6), (1, 46, -5))
    nose.paint(SKIN, jitter=0.1)
    nose.down.fill(SKIN_D)
    bp = m.plane(beard, "beard_plane", (-5.5, 24, -5.6), (5.5, 44, -5.6)).front
    bp.gradient(BEARD_L, BEARD_D, steps=4).noise(0.1)
    bp.strands(lt(BEARD, 0.3), density=0.5, length=(3, 7)).strands(BEARD_D, density=0.45, length=(3, 6))
    for yy in range(bp.h):  # wide at the jaw, braided point at the bottom
        half = (bp.w / 2) * (1 - 0.7 * max(0, yy - 4) / bp.h)
        bp.clear(0, yy, int(bp.w / 2 - half), 1).clear(int(bp.w / 2 + half + 0.5), yy, bp.w, 1)
    bp.clear(3, 0, 5, 3)  # mouth gap under the moustache
    bp.rect(4, 14, 3, 1, GOLD).rect(4, 15, 3, 1, dk(GOLD, 0.3))  # beard ring
    bp.tatter(2, chance=0.4)

    # --- wide-brimmed wanderer hat (phase 1)
    brim = m.plane(hat, "hat_brim", (-11, 48, -11), (11, 48, 11))
    hb = brim.front
    hb.ellipse(11, 11, 11, 11, HAT).noise(0.08)
    for yy in range(hb.h):
        for xx in range(hb.w):
            r2 = ((xx + 0.5 - 11) / 11) ** 2 + ((yy + 0.5 - 11) / 11) ** 2
            if 0.82 < r2 <= 1.0:
                hb.pixel(xx, yy, dk(HAT, 0.35))
    crown = m.cube(hat, "hat_crown", (-5.5, 48, -5.5), (5.5, 54, 5.5))
    crown.paint(HAT, jitter=0.18)
    for s in crown.sides():
        s.rect(0, s.h - 2, s.w, 2, LEATHER).rect(0, s.h - 2, s.w, 1, lt(LEATHER, 0.2))
        s.stripe(dk(HAT, 0.3), every=4, axis="v", offset=1, h=s.h - 2)
    crown.north.rect(4, crown.north.h - 2, 3, 2, GOLD)
    top = m.cube(hat, "hat_top", (-4, 54, -3.5), (4, 56.5, 4.5), rotation=(-22.5, 0, 0), pivot=(0, 54, 0))
    top.paint(dk(HAT, 0.1), jitter=0.2)
    top.up.line(0, 4, 7, 3, dk(HAT, 0.45))  # dent

    # --- horned and winged war helmet (phase 2)
    helm = m.cube(helmet, "helm", (-5.6, 45, -5.6), (5.6, 51.4, 5.6))
    helm.paint(IRON, jitter=0.2)
    for s in helm.sides():
        s.rect(0, s.h - 2, s.w, 2, GOLD).rect(0, s.h - 2, s.w, 1, lt(GOLD, 0.35)).rivets(dk(GOLD, 0.3), every=3)
        s.rect(s.w // 2 - 1, 0, 2, s.h - 2, dk(IRON, 0.25))
    helm.up.fill(IRON).noise(0.08).line(5, 0, 5, 10, dk(IRON, 0.35)).line(0, 5, 10, 5, dk(IRON, 0.35))
    helm.north.rect(1, 3, 3, 1, "#151515").rect(7, 3, 3, 1, "#151515")  # spectacle guard slits
    nasal = m.cube(helmet, "helm_nasal", (-0.75, 42, -6.3), (0.75, 46, -5.6))
    nasal.paint(GOLD, jitter=0.1)
    for side, x0 in (("right", 5.6), ("left", -9.1)):
        horn = m.cube(helmet, f"horn_{side}", (x0, 47, -1.5), (x0 + 3.5, 49.5, 1))
        horn.paint("#e6dcc0", jitter=0.15)
        tip_x = (8.1, 9.6) if side == "right" else (-9.6, -8.1)
        tip = m.cube(helmet, f"horn_tip_{side}", (tip_x[0], 49, -1), (tip_x[1], 55, 0.5),
                     rotation=(-22.5, 0, 0), pivot=((tip_x[0] + tip_x[1]) / 2, 49, 0))
        tip.paint("#e6dcc0", jitter=0.15)
        for s in tip.sides():
            s.gradient(dk("#e6dcc0", 0.45), "#e6dcc0", steps=3).rect(0, 0, s.w, 1, dk("#e6dcc0", 0.6))
        wing_x = 5.7 if side == "right" else -5.7
        wing = m.plane(helmet, f"wing_{side}", (wing_x, 49, 0.5), (wing_x, 57, 8))
        wf = wing.front
        for yy in range(wf.h):  # swept-back feathers, longest at the top
            length = int(wf.w * (1 - 0.65 * yy / wf.h))
            wf.rect(0, yy, length, 1, WING if yy % 2 else lt(WING, 0.2))
        wf.stripe(dk(WING, 0.2), every=2, axis="v", offset=1).noise(0.05)

    # --- arms: blue sleeves, fur pauldrons, gold-banded bracers
    up_r = m.cube(arm_r, "upper_arm_right", (8, 29, -3), (14, 38, 3))
    up_r.paint(CLOAK, jitter=0.2)
    for s in up_r.sides():
        s.stripe(dk(CLOAK, 0.3), every=3, axis="h", offset=2)
    pauld = m.cube(arm_r, "pauldron_right", (7.5, 35, -3.7), (14.6, 39.5, 3.7))
    pauld.paint(FUR, jitter=0.2)
    for s in pauld.sides():
        s.strands(FUR_D, density=0.5, length=(1, 3)).jagged(FUR_D, edge="bottom", depth=(0, 1))
    low_r = m.cube(fore_r, "forearm_right_c", (8.5, 21, -3), (13.5, 29, 3))
    low_r.paint(SKIN, jitter=0.15)
    bracer = low_r.overlay(0.35, "bracer_right")
    for s in bracer.sides():
        s.rect(0, 2, s.w, 6, LEATHER).noise(0.08).rect(0, 2, s.w, 1, GOLD).rect(0, 7, s.w, 1, GOLD)
    hand_r = m.cube(fore_r, "hand_right", (9, 18, -2.5), (13, 21, 2.5))
    hand_r.paint(SKIN, jitter=0.15)
    hand_r.north.stripe(SKIN_D, every=2, axis="v", offset=1)
    up_r.mirror_x(arm_l, "upper_arm_left")
    pauld.mirror_x(arm_l, "pauldron_left")
    low_r.mirror_x(fore_l, "forearm_left_c")
    bracer.mirror_x(fore_l, "bracer_left")
    hand_r.mirror_x(fore_l, "hand_left")

    # --- Gungnir: ash shaft, gold collar, rune-cut leaf blade (two crossed planes)
    shaft_lo = m.cube(spear, "shaft_low", (10.25, 1, -0.75), (11.75, 26, 0.75))
    shaft_lo.paint(WOOD, jitter=0.2)
    for s in shaft_lo.sides():
        s.stripe(dk(WOOD, 0.35), every=6, axis="h", offset=3)
    shaft_hi = m.cube(spear, "shaft_high", (10.25, 26, -0.75), (11.75, 50, 0.75))
    shaft_hi.paint(WOOD, jitter=0.2)
    for s in shaft_hi.sides():
        s.stripe(dk(WOOD, 0.35), every=6, axis="h", offset=1).rect(0, 14, s.w, 3, LEATHER)
    collar = m.cube(spear, "spear_collar", (9.75, 48.5, -1.25), (12.25, 51, 1.25))
    collar.paint(GOLD, jitter=0.15)
    for name, frm, to in (("blade_x", (11, 51, -2.5), (11, 62, 2.5)), ("blade_z", (8.5, 51, 0), (13.5, 62, 0))):
        bl = m.plane(spear, name, frm, to).front
        for yy in range(bl.h):
            t = yy / (bl.h - 1)  # 0 = tip
            half = 2.5 * min(1.0, 0.25 + 1.6 * t) * (1.0 if t < 0.75 else (1 - t) * 4)
            x0 = int(round(2.5 - half))
            x1 = int(round(2.5 + half))
            if x1 > x0:
                bl.rect(x0, yy, x1 - x0, 1, IRON)
                bl.pixel(x0, yy, lt(IRON, 0.5))
        bl.line(2, 2, 2, bl.h - 3, dk(IRON, 0.35)).pixel(2, 5, EYE).pixel(2, 7, EYE)

    # --- animations
    idle = m.animation("idle", 3.2, "loop")
    idle_breathe(idle, body="body", chest="torso", head="head", arms=("arm_left", "arm_right"), amount=0.35, nod=2)
    idle.rot("cloak", {0: (0, 0, 0), 1.6: (4, 0, 0), 3.2: (0, 0, 0)})
    idle.rot("beard", {0: (0, 0, 0), 1.6: (3, 0, 0), 3.2: (0, 0, 0)})
    idle.rot("spear", {0: (0, 0, 0), 1.6: (2, 0, -2), 3.2: (0, 0, 0)})
    _headgear(idle, False)

    cast = m.animation("cast", 1.8, "once")
    cast.rot("arm_right", {0: (0, 0, 0), 0.5: (-150, 0, 10), 1.1: (-160, 0, 12), 1.8: (0, 0, 0)}, interp="linear")
    cast.rot("spear", {0: (0, 0, 0), 0.5: (20, 0, 0), 1.8: (0, 0, 0)}, interp="linear")
    cast.rot("arm_left", {0: (0, 0, 0), 0.5: (-70, 0, -30), 1.1: (-80, 0, -40), 1.8: (0, 0, 0)}, interp="linear")
    cast.rot("head", {0: (0, 0, 0), 0.5: (-15, 0, 0), 1.1: (-18, 0, 0), 1.8: (0, 0, 0)}, interp="linear")
    cast.rot("cloak", {0: (0, 0, 0), 0.6: (-15, 0, 0), 1.8: (0, 0, 0)}, interp="linear")
    _headgear(cast, False)

    rage = m.animation("rage", 2.5, "hold", interp="linear")
    rage.rot("arm_left", {0: (0, 0, 0), 0.5: (-165, 0, -15), 0.9: (-150, 0, -40), 1.4: (-60, 0, -50),
                          1.9: (-40, 0, -70), 2.5: (-20, 0, -15)})
    rage.rot("forearm_left", {0: (0, 0, 0), 0.5: (-40, 0, 0), 1.0: (-10, 0, 0), 2.5: (0, 0, 0)})
    rage.scale("hat", {0: SHOWN, 0.7: SHOWN, 0.95: HIDDEN, 2.5: HIDDEN})
    rage.pos("hat", {0: (0, 0, 0), 0.7: (0, 3, 0), 0.95: (-4, 6, 0), 2.5: (0, 0, 0)})
    rage.scale("helmet", {0: HIDDEN, 0.95: HIDDEN, 1.2: (1.15, 1.15, 1.15), 1.4: SHOWN, 2.5: SHOWN})
    rage.rot("head", {0: (0, 0, 0), 0.9: (10, 0, 0), 1.4: (-30, 0, 0), 2.0: (-25, 0, 0), 2.5: (8, 0, 0)})
    rage.rot("torso", {0: (0, 0, 0), 1.4: (-12, 0, 0), 2.0: (-10, 0, 0), 2.5: (10, 0, 0)})
    rage.rot("arm_right", {0: (0, 0, 0), 1.4: (-30, 0, 60), 2.0: (-40, 0, 70), 2.5: (-25, 0, 10)})
    rage.rot("cloak", {0: (0, 0, 0), 1.4: (-25, 0, 0), 2.0: (-20, 0, 0), 2.5: (5, 0, 0)})
    rage.pos("body", {0: (0, 0, 0), 1.4: (0, 1, 0), 2.5: (0, -1, 0)})

    idle2 = m.animation("idle_casco", 2.4, "loop")
    idle_breathe(idle2, body="body", chest="torso", amount=0.5, nod=0)
    idle2.rot("torso", {0: (10, 0, 0), 1.2: (12, 0, 0), 2.4: (10, 0, 0)})
    idle2.rot("head", {0: (8, 0, 0), 1.2: (10, 4, 0), 2.4: (8, 0, 0)})
    idle2.rot("arm_right", {0: (-25, 0, 10), 1.2: (-28, 0, 12), 2.4: (-25, 0, 10)})
    idle2.rot("arm_left", {0: (-15, 0, -15), 1.2: (-18, 0, -18), 2.4: (-15, 0, -15)})
    idle2.rot("leg_right", {0: (-12, 0, 4), 2.4: (-12, 0, 4)})
    idle2.rot("leg_left", {0: (10, 0, -4), 2.4: (10, 0, -4)})
    idle2.rot("cloak", {0: (6, 0, 0), 1.2: (9, 0, 0), 2.4: (6, 0, 0)})
    _headgear(idle2, True)

    walk = m.animation("walk", 1.5, "loop")
    walk_biped(walk, legs=("leg_left", "leg_right"), shins=("shin_left", "shin_right"),
               arms=("arm_left", "arm_right"), body="body", head="head", swing=24, arm_swing=14, knee=26,
               bob=0.8, sway=3)
    L = walk.length
    walk.rot("skirt", {0: (-6, 0, 0), L / 2: (6, 0, 0), L: (-6, 0, 0)})
    walk.rot("cloak", {0: (8, 0, 0), L / 2: (3, 0, 0), L: (8, 0, 0)})
    walk.rot("spear", {0: (-4, 0, 0), L / 2: (6, 0, 0), L: (-4, 0, 0)})
    _headgear(walk, True)

    carga = m.animation("carga", 1.0, "loop")
    carga.rot("torso", {0: (28, 0, 0), 0.5: (30, 0, 0), 1.0: (28, 0, 0)})
    carga.rot("head", {0: (22, 0, 0), 0.25: (26, -6, 0), 0.75: (26, 6, 0), 1.0: (22, 0, 0)})
    carga.pos("body", {0: (0, -1.5, 0), 0.5: (0, -2, 0), 1.0: (0, -1.5, 0)})
    carga.rot("leg_right", {0: (25, 0, 0), 0.3: (45, 0, 0), 0.6: (15, 0, 0), 1.0: (25, 0, 0)})
    carga.rot("shin_right", {0: (10, 0, 0), 0.3: (30, 0, 0), 1.0: (10, 0, 0)})
    carga.rot("leg_left", {0: (-30, 0, 0), 1.0: (-30, 0, 0)})
    carga.rot("shin_left", {0: (35, 0, 0), 1.0: (35, 0, 0)})
    carga.rot("arm_right", {0: (20, 0, 20), 0.5: (25, 0, 22), 1.0: (20, 0, 20)})
    carga.rot("spear", {0: (30, 0, 0), 1.0: (30, 0, 0)})
    carga.rot("arm_left", {0: (25, 0, -20), 0.5: (30, 0, -22), 1.0: (25, 0, -20)})
    carga.rot("cloak", {0: (-10, 0, 0), 0.5: (-14, 0, 0), 1.0: (-10, 0, 0)})
    _headgear(carga, True)

    run = m.animation("embestida", 0.6, "loop")
    walk_biped(run, legs=("leg_left", "leg_right"), shins=("shin_left", "shin_right"), swing=45, knee=50, bob=1.5,
               sway=0)
    L = run.length
    run.rot("torso", {0: (35, 0, 0), L / 2: (38, 0, 0), L: (35, 0, 0)})
    run.rot("head", {0: (30, 0, 0), L: (30, 0, 0)})
    run.rot("arm_right", {0: (40, 0, 25), L / 2: (50, 0, 25), L: (40, 0, 25)})
    run.rot("spear", {0: (15, 0, 0), L: (15, 0, 0)})
    run.rot("arm_left", {0: (45, 0, -25), L / 2: (35, 0, -25), L: (45, 0, -25)})
    run.rot("cloak", {0: (-45, 0, 0), L / 2: (-52, 0, 0), L: (-45, 0, 0)})
    run.rot("skirt", {0: (-15, 0, 0), L / 2: (-20, 0, 0), L: (-15, 0, 0)})
    run.rot("beard", {0: (-25, 0, 0), L: (-25, 0, 0)})
    _headgear(run, True)

    stun = m.animation("stunned", 2.0, "loop")
    stun.pos("body", {0: (0, -2.5, 0), 1.0: (0, -3, 0), 2.0: (0, -2.5, 0)})
    stun.rot("torso", {0: (18, 6, 6), 0.5: (20, 0, 0), 1.0: (18, -6, -6), 1.5: (20, 0, 0), 2.0: (18, 6, 6)})
    stun.rot("head", {0: (20, 0, 14), 0.5: (28, 12, 0), 1.0: (20, 0, -14), 1.5: (28, -12, 0), 2.0: (20, 0, 14)})
    stun.rot("arm_right", {0: (15, 0, 12), 1.0: (10, 0, 6), 2.0: (15, 0, 12)})
    stun.rot("spear", {0: (25, 0, 20), 2.0: (25, 0, 20)})
    stun.rot("arm_left", {0: (10, 0, -6), 1.0: (15, 0, -12), 2.0: (10, 0, -6)})
    stun.rot("leg_right", {0: (-20, 0, 6), 2.0: (-20, 0, 6)})
    stun.rot("shin_right", {0: (30, 0, 0), 2.0: (30, 0, 0)})
    stun.rot("leg_left", {0: (-14, 0, -6), 2.0: (-14, 0, -6)})
    stun.rot("shin_left", {0: (24, 0, 0), 2.0: (24, 0, 0)})
    _headgear(stun, True)

    death = m.animation("death", 2.5, "once")
    death_fall(death, direction="back")
    death.rot("arm_right", {0: (0, 0, 0), 0.7: (-30, 0, 30), 1.6: (10, 0, 80), 2.5: (10, 0, 80)}, interp="linear")
    death.rot("arm_left", {0: (0, 0, 0), 0.7: (-60, 0, -25), 1.6: (-20, 0, -75), 2.5: (-20, 0, -75)}, interp="linear")
    death.rot("head", {0: (0, 0, 0), 0.6: (-20, 0, 0), 1.6: (15, 25, 0), 2.5: (15, 25, 0)}, interp="linear")
    death.rot("spear", {0: (0, 0, 0), 1.2: (0, 0, 40), 2.5: (0, 0, 70)}, interp="linear")
    death.rot("cloak", {0: (0, 0, 0), 1.0: (-12, 0, 0), 2.5: (-6, 0, 0)}, interp="linear")
    _headgear(death, True)
    return _check(m, ODIN_CLIPS)


# =============================================================================== RAVEN (Hugin / Munin)

def build_raven():
    BLACK, SHEEN, BEAK, EYE = "#1d1f2a", "#3a4a78", "#2a2622", "#f0c040"
    m = Model("bigcasares_myth_odin_raven", texture_size=(64, 64))
    body = m.bone("body", pivot=(0, 6, 0))
    head = m.bone("head", body, pivot=(0, 8, -2.5))
    wing_r = m.bone("wing_right", body, pivot=(2.5, 8, 0))
    wing_l = m.bone("wing_left", body, pivot=(-2.5, 8, 0))
    tail = m.bone("tail", body, pivot=(0, 6.5, 4))
    leg_r = m.bone("leg_right", body, pivot=(1, 4, 0.5))
    leg_l = m.bone("leg_left", body, pivot=(-1, 4, 0.5))

    torso = m.cube(body, "torso", (-2.5, 4, -3), (2.5, 8.5, 4.5))
    torso.paint(BLACK, jitter=0.25)
    for fc in torso.faces():
        fc.dither(SHEEN + "70", density=0.25, pattern="noise").stripe(dk(BLACK, 0.3), every=2, axis="h", offset=1)
    torso.down.fill(dk(BLACK, 0.2)).dither(SHEEN + "50", density=0.2, pattern="noise")
    skull = m.cube(head, "skull", (-2, 7, -6), (2, 11, -2))
    skull.paint(BLACK, jitter=0.2)
    skull.north.rect(0, 1, 1, 1, EYE).rect(3, 1, 1, 1, EYE)
    skull.east.rect(0, 1, 1, 1, EYE).pixel(1, 2, SHEEN)
    skull.west.rect(3, 1, 1, 1, EYE).pixel(2, 2, SHEEN)
    skull.up.dither(SHEEN + "80", density=0.3, pattern="noise")
    beak = m.cube(head, "beak", (-0.75, 7.5, -9.5), (0.75, 9.5, -6))
    beak.paint(BEAK, jitter=0.15)
    beak.up.rect(0, 0, 2, 1, lt(BEAK, 0.25))
    throat = m.plane(head, "throat_feathers", (-1.75, 5.5, -5.2), (1.75, 7.5, -5.2))
    throat.front.fill(BLACK).jagged(dk(BLACK, 0.4), edge="bottom", depth=(0, 1)).tatter(1)
    for side, wing, x0, x1 in (("right", wing_r, 2.5, 13.5), ("left", wing_l, -13.5, -2.5)):
        w = m.plane(wing, f"wing_feathers_{side}", (x0, 8, -2.5), (x1, 8, 5)).front
        span = w.w
        for xx in range(span):
            d = xx if side == "right" else span - 1 - xx  # distance from the body
            depth = w.h - int(d * 0.25)
            w.rect(xx, 0, 1, max(3, depth), BLACK)
            if d % 2 == 0:
                w.rect(xx, max(2, depth - 3), 1, 3, SHEEN + "a0")
        w.stripe(dk(BLACK, 0.4), every=3, axis="h", offset=1).tatter(2, chance=0.6)
    t = m.plane(tail, "tail_fan", (-2.5, 6.5, 4), (2.5, 6.5, 9.5)).front
    t.fill(BLACK).stripe(SHEEN + "90", every=2, axis="v").tatter(2, edge="bottom", chance=0.8)
    for side, bone, x in (("right", leg_r, 0.5), ("left", leg_l, -1.5)):
        leg = m.cube(bone, f"shank_{side}", (x, 0.5, 0), (x + 1, 4, 1))
        leg.paint("#3c3530", jitter=0.1)
        foot = m.plane(bone, f"foot_{side}", (x - 1, 0.05, -1.5), (x + 2, 0.05, 1.5))
        foot.front.decal(["g.g", ".g.", ".g."], {"g": "#3c3530"})

    idle = m.animation("idle", 1.0, "loop")
    idle.rot("wing_right", {0: (0, 0, -35), 0.5: (0, 0, 25), 1.0: (0, 0, -35)})
    idle.rot("wing_left", {0: (0, 0, 35), 0.5: (0, 0, -25), 1.0: (0, 0, 35)})
    idle.pos("body", {0: (0, 0, 0), 0.5: (0, 1.2, 0), 1.0: (0, 0, 0)})
    idle.rot("head", {0: (0, 0, 0), 0.25: (0, 15, 0), 0.75: (0, -15, 0), 1.0: (0, 0, 0)})
    idle.rot("leg_right", {0: (30, 0, 0), 1.0: (30, 0, 0)})
    idle.rot("leg_left", {0: (30, 0, 0), 1.0: (30, 0, 0)})

    walk = m.animation("walk", 0.5, "loop")  # flight
    walk.rot("wing_right", {0: (0, 0, -45), 0.25: (0, 0, 35), 0.5: (0, 0, -45)})
    walk.rot("wing_left", {0: (0, 0, 45), 0.25: (0, 0, -35), 0.5: (0, 0, 45)})
    walk.rot("body", {0: (12, 0, 0), 0.5: (12, 0, 0)})
    walk.pos("body", {0: (0, -0.5, 0), 0.25: (0, 0.8, 0), 0.5: (0, -0.5, 0)})
    walk.rot("tail", {0: (-8, 0, 0), 0.25: (6, 0, 0), 0.5: (-8, 0, 0)})
    walk.rot("leg_right", {0: (60, 0, 0), 0.5: (60, 0, 0)})
    walk.rot("leg_left", {0: (60, 0, 0), 0.5: (60, 0, 0)})

    attack = m.animation("attack", 0.6, "once", interp="linear")
    attack.rot("head", {0: (0, 0, 0), 0.2: (-30, 0, 0), 0.32: (45, 0, 0), 0.6: (0, 0, 0)})
    attack.rot("body", {0: (0, 0, 0), 0.2: (-15, 0, 0), 0.32: (25, 0, 0), 0.6: (0, 0, 0)})
    attack.rot("wing_right", {0: (0, 0, 0), 0.2: (0, 0, 60), 0.4: (0, 0, -30), 0.6: (0, 0, 0)})
    attack.rot("wing_left", {0: (0, 0, 0), 0.2: (0, 0, -60), 0.4: (0, 0, 30), 0.6: (0, 0, 0)})

    death = m.animation("death", 1.2, "once", interp="linear")
    death.rot("body", {0: (0, 0, 0), 0.4: (0, 0, 60), 1.2: (0, 0, 180)})
    death.pos("body", {0: (0, 0, 0), 0.4: (0, 2, 0), 1.2: (0, 2.5, 0)})
    death.rot("wing_right", {0: (0, 0, 0), 1.2: (0, 0, 40)})
    death.rot("wing_left", {0: (0, 0, 0), 1.2: (0, 0, -40)})
    return _check(m, MOB_CLIPS)


# =============================================================================== WOLF (Geri / Freki)

def build_wolf():
    FUR, FUR_D, FUR_L, EYE = "#5c5f66", "#2e3036", "#9ea2a8", "#ffd23a"
    m = Model("bigcasares_myth_odin_wolf", texture_size=(128, 128))
    body = m.bone("body", pivot=(0, 12, 0))
    head = m.bone("head", body, pivot=(0, 15, -8))
    jaw = m.bone("jaw", head, pivot=(0, 12.5, -13))
    tail = m.bone("tail", body, pivot=(0, 15, 8))
    legs = {}
    for name, x, z in (("leg_front_right", 2.5, -5), ("leg_front_left", -2.5, -5),
                       ("leg_back_right", 2.5, 6), ("leg_back_left", -2.5, 6)):
        legs[name] = m.bone(name, body, pivot=(x, 11, z))

    trunk = m.cube(body, "trunk", (-4, 9.5, -6), (4, 16.5, 9))
    trunk.paint(FUR, jitter=0.25)
    for fc in trunk.sides():
        fc.strands(FUR_D, density=0.5, length=(2, 4)).strands(FUR_L, density=0.15, length=(1, 3))
    trunk.up.fill(FUR_D).noise(0.1).strands(FUR, density=0.4, length=(2, 4), direction="right")
    trunk.down.fill(FUR_L).noise(0.08)
    mane = m.cube(body, "mane", (-5, 8.5, -8), (5, 18, -1))
    mane.paint(FUR_D, jitter=0.25)
    for fc in mane.faces():
        fc.strands(FUR, density=0.5, length=(2, 4)).strands(FUR_L, density=0.15, length=(1, 3))
    ruff = mane.overlay(0.6, "mane_ruff")
    for fc in ruff.sides():
        fc.jagged(FUR_D, edge="bottom", depth=(1, 3)).jagged(FUR, edge="top", depth=(0, 2))
    skull = m.cube(head, "skull", (-3.5, 12, -14), (3.5, 18.5, -8))
    skull.paint(FUR, jitter=0.22)
    f = skull.north
    f.rect(0, 0, 7, 2, FUR_D).eyes(3.5, 2, w=1, h=1, gap=3, glow=EYE, socket="#151515")
    f.rect(2, 4, 3, 3, FUR_L)
    for side, x0 in (("right", 1.5), ("left", -3.5)):
        ear = m.cube(head, f"ear_{side}", (x0, 18.5, -11), (x0 + 2, 21, -9.5))
        ear.paint(FUR_D, jitter=0.15)
        ear.north.rect(0, 1, 2, 1, "#6b4a4a")
    snout = m.cube(head, "snout", (-2, 13.5, -18.5), (2, 16, -14))
    snout.paint(FUR_L, jitter=0.15)
    snout.north.rect(1, 0, 2, 1, "#111111").rect(0, 2, 4, 1, "#f2ead8").pixel(1, 2, "#111111")
    snout.up.rect(0, 0, 4, 2, FUR).rect(1, 0, 2, 1, "#111111")
    jaw_c = m.cube(jaw, "jaw_c", (-1.75, 12, -18), (1.75, 13.5, -13))
    jaw_c.paint(FUR_L, jitter=0.15)
    jaw_c.up.fill("#7a2a2a").rect(0, 0, 4, 1, "#f2ead8")
    for name, bone in legs.items():
        x = bone.pivot[0]
        z = bone.pivot[2]
        leg = m.cube(bone, f"{name}_c", (x - 1.5, 0, z - 1.5), (x + 1.5, 11, z + 1.5))
        leg.paint(FUR, jitter=0.2)
        for fc in leg.sides():
            fc.rect(0, fc.h - 2, fc.w, 2, FUR_D).strands(FUR_D, density=0.3, length=(1, 3))
    t = m.cube(tail, "tail_c", (-1.5, 6, 8), (1.5, 15, 11), rotation=(22.5, 0, 0), pivot=(0, 15, 9.5))
    t.paint(FUR, jitter=0.2)
    for fc in t.sides():
        fc.strands(FUR_D, density=0.5, length=(2, 4)).rect(0, fc.h - 2, fc.w, 2, FUR_L)

    idle = m.animation("idle", 2.0, "loop")
    idle_breathe(idle, body="body", head="head", tail="tail", amount=0.3, nod=3)
    idle.rot("jaw", {0: (0, 0, 0), 1.0: (8, 0, 0), 2.0: (0, 0, 0)})

    walk = m.animation("walk", 0.6, "loop")
    walk_quadruped(walk, swing=34, body="body", head="head", tail="tail", bob=0.8)

    attack = m.animation("attack", 0.7, "once", interp="linear")
    attack.pos("body", {0: (0, 0, 0), 0.2: (0, 0, 2), 0.35: (0, 1, -4), 0.7: (0, 0, 0)})
    attack.rot("body", {0: (0, 0, 0), 0.2: (-8, 0, 0), 0.35: (6, 0, 0), 0.7: (0, 0, 0)})
    attack.rot("head", {0: (0, 0, 0), 0.2: (-15, 0, 0), 0.35: (10, 0, 0), 0.7: (0, 0, 0)})
    attack.rot("jaw", {0: (0, 0, 0), 0.2: (35, 0, 0), 0.35: (0, 0, 0), 0.45: (25, 0, 0), 0.7: (0, 0, 0)})
    attack.rot("leg_front_right", {0: (0, 0, 0), 0.35: (-50, 0, 0), 0.7: (0, 0, 0)})
    attack.rot("leg_front_left", {0: (0, 0, 0), 0.35: (-50, 0, 0), 0.7: (0, 0, 0)})

    death = m.animation("death", 1.4, "once")
    death_fall(death, direction="right")
    death.rot("head", {0: (0, 0, 0), 1.0: (20, 0, 0), 1.4: (20, 0, 0)}, interp="linear")
    death.rot("tail", {0: (0, 0, 0), 1.4: (40, 0, 0)}, interp="linear")
    return _check(m, MOB_CLIPS)


# =============================================================================== EINHERJAR

def build_einherjar():
    SPIRIT, SPIRIT_D, GLOW = "#a9c8d8", "#6a8ea4", "#c8f4ff"
    MAIL, IRON, GOLD = "#7b848e", "#a3acb5", "#d9aa3a"
    TUNIC, LEATHER, WOOD, SHIELD = "#7a2f2a", "#4e3420", "#6a4a2c", "#2c4c8e"
    m = Model("bigcasares_myth_odin_einherjar", texture_size=(128, 128))
    body = m.bone("body", pivot=(0, 14, 0))
    torso = m.bone("torso", body, pivot=(0, 16, 0))
    head = m.bone("head", torso, pivot=(0, 26, 0))
    arm_r = m.bone("arm_right", torso, pivot=(7.75, 24.5, 0))
    fore_r = m.bone("forearm_right", arm_r, pivot=(7.75, 16.5, 0))
    sword = m.bone("sword", fore_r, pivot=(7.75, 8.5, 0), rotation=(-25, 0, 0))
    arm_l = m.bone("arm_left", torso, pivot=(-7.75, 24.5, 0))
    fore_l = m.bone("forearm_left", arm_l, pivot=(-7.75, 16.5, 0))
    shield = m.bone("shield", fore_l, pivot=(-9.5, 12, 0))
    leg_r = m.bone("leg_right", body, pivot=(2.75, 14, 0))
    shin_r = m.bone("shin_right", leg_r, pivot=(2.75, 7, 0))
    leg_l = m.bone("leg_left", body, pivot=(-2.75, 14, 0))
    shin_l = m.bone("shin_left", leg_l, pivot=(-2.75, 7, 0))
    cape = m.bone("cape", torso, pivot=(0, 26, 4))

    pelvis = m.cube(body, "pelvis", (-5, 12, -3), (5, 16, 3))
    pelvis.paint(TUNIC, jitter=0.2)
    pelvis.north.stripe(dk(TUNIC, 0.35), every=3, axis="v", offset=1)
    thigh = m.cube(leg_r, "thigh_right", (0.5, 7, -2.5), (5, 14, 2.5))
    thigh.paint("#5a4a3a", jitter=0.2)
    shin = m.cube(shin_r, "shin_wrap_right", (0.75, 1.5, -2.5), (4.75, 7, 2.5))
    shin.paint(SPIRIT, jitter=0.2)
    greave = shin.overlay(0.35, "greave_right")
    for fc in greave.sides():
        fc.rect(0, 0, fc.w, 4, LEATHER).noise(0.1).line(0, 0, fc.w - 1, 3, dk(LEATHER, 0.4))
    boot = m.cube(shin_r, "boot_right", (0.5, 0, -3.5), (5, 2, 2.5))
    boot.paint(LEATHER, jitter=0.15)
    thigh.mirror_x(leg_l, "thigh_left")
    shin.mirror_x(shin_l, "shin_wrap_left")
    greave.mirror_x(shin_l, "greave_left")
    boot.mirror_x(shin_l, "boot_left")

    chest = m.cube(torso, "chest", (-6, 16, -3.5), (6, 26.5, 3.5))
    chest.paint(MAIL, jitter=0.25)
    for fc in chest.faces():
        fc.dither((0, 0, 0, 40), pattern="checker").stripe(lt(MAIL, 0.15), every=2, axis="h")
    chest.north.rect(0, 7, 12, 2, LEATHER).rect(5, 7, 2, 2, GOLD)  # belt
    chest.north.line(0, 0, 11, 6, LEATHER)  # baldric
    tabard = m.plane(torso, "tabard", (-3.5, 9, -3.7), (3.5, 24, -3.7))
    tf = tabard.front
    tf.gradient(TUNIC, dk(TUNIC, 0.3), steps=4).noise(0.07).outline(GOLD, sides="lr")
    tf.decal(VALKNUT, {"g": GOLD}, x=0, y=3).tatter(2, chance=0.5)
    for side, x0, x1 in (("right", 5.5, 10), ("left", -10, -5.5)):
        p = m.cube(torso, f"pauldron_{side}", (x0, 23.5, -3), (x1, 26.5, 3))
        p.paint(IRON, jitter=0.15)
        p.up.outline(dk(IRON, 0.4)).rivets(GOLD, every=3)

    skull = m.cube(head, "skull", (-4, 26, -4), (4, 34, 4))
    skull.paint(SPIRIT, jitter=0.2)
    f = skull.north
    f.eyes(4, 4, w=2, h=1, gap=2, glow=GLOW, socket=SPIRIT_D)
    f.rect(1, 6, 6, 2, "#c9b48a").strands(dk("#c9b48a", 0.4), density=0.6, length=(1, 2), y=6)  # beard
    helm = skull.overlay(0.55, "helm")
    helm.up.shaded(IRON, jitter=0.2, shade=False).line(4, 0, 4, 7, GOLD)
    for fc in (helm.north, helm.south, helm.east, helm.west):
        fc.rect(0, 0, fc.w, 3, IRON).noise(0.08).rect(0, 2, fc.w, 1, GOLD)
    helm.north.rect(1, 3, 6, 2, IRON).clear(2, 3, 1, 1).clear(5, 3, 1, 1).rect(3, 3, 2, 3, IRON)  # spectacle guard
    helm.north.rect(2, 3, 1, 1, GLOW).rect(5, 3, 1, 1, GLOW)

    up_r = m.cube(arm_r, "upper_arm_right", (6, 16.5, -2), (9.5, 25, 2))
    up_r.paint(MAIL, jitter=0.22)
    low_r = m.cube(fore_r, "lower_arm_right", (6.25, 9.5, -2), (9.25, 16.5, 2))
    low_r.paint(SPIRIT, jitter=0.2)
    bracer = low_r.overlay(0.3, "bracer_right")
    for fc in bracer.sides():
        fc.rect(0, 0, fc.w, 4, LEATHER).noise(0.08).rect(0, 0, fc.w, 1, GOLD)
    hand_r = m.cube(fore_r, "hand_right", (6.5, 7, -1.75), (9, 9.5, 1.75))
    hand_r.paint(SPIRIT_D, jitter=0.2)
    up_r.mirror_x(arm_l, "upper_arm_left")
    low_r.mirror_x(fore_l, "lower_arm_left")
    bracer.mirror_x(fore_l, "bracer_left")
    hand_r.mirror_x(fore_l, "hand_left")

    grip = m.cube(sword, "sword_grip", (7, 6, -0.75), (8.5, 11, 0.75))
    grip.paint(LEATHER, jitter=0.15)
    guard = m.cube(sword, "sword_guard", (5.75, 11, -1), (9.75, 12, 1))
    guard.paint(GOLD, jitter=0.15)
    blade = m.plane(sword, "sword_blade", (6.5, 12, 0), (9, 28, 0)).front
    blade.rect(0, 0, blade.w, blade.h, IRON).rect(1, 0, 1, blade.h, lt(IRON, 0.4)).noise(0.05)
    blade.clear(0, 0, 1, 2).clear(2, 0, 1, 2).rect(1, 2, 1, blade.h - 3, GLOW + "90")

    board = m.cube(shield, "shield_board", (-11, 5, -6), (-10, 19, 6))
    board.paint(SHIELD, jitter=0.15)
    for fc in (board.east, board.west):
        fc.planks(dk(SHIELD, 0.3), every=3).ellipse(6, 7, 2, 2, IRON).ellipse(6, 7, 1, 1, lt(IRON, 0.4))
        fc.outline(dk(WOOD, 0.2)).rect(0, 6, 12, 2, GOLD + "c0")
    board.west.rivets(IRON, every=3)

    cape_p = m.plane(cape, "cape_back", (-6, 6, 4.2), (6, 26, 4.2)).front
    cape_p.gradient(TUNIC, dk(TUNIC, 0.45), steps=4).noise(0.07).stripe(dk(TUNIC, 0.4), every=4, axis="v")
    cape_p.tatter(4, chance=0.8).dither(GLOW + "50", density=0.12, pattern="noise")

    idle = m.animation("idle", 2.6, "loop")
    idle_breathe(idle, body="body", chest="torso", head="head", arms=("arm_left", "arm_right"), amount=0.3)
    idle.rot("cape", {0: (0, 0, 0), 1.3: (5, 0, 0), 2.6: (0, 0, 0)})
    idle.rot("forearm_left", {0: (-40, 0, 0), 2.6: (-40, 0, 0)})

    walk = m.animation("walk", 1.2, "loop")
    walk_biped(walk, legs=("leg_left", "leg_right"), shins=("shin_left", "shin_right"),
               arms=("arm_left", "arm_right"), forearms=("forearm_left", "forearm_right"),
               body="body", head="head", swing=26, arm_swing=12, knee=28, bob=0.6, sway=3)
    L = walk.length
    walk.rot("forearm_left", {0: (-45, 0, 0), L: (-45, 0, 0)})
    walk.rot("cape", {0: (8, 0, 0), L / 2: (2, 0, 0), L: (8, 0, 0)})

    attack = m.animation("attack", 0.9, "once")
    attack_swing(attack, "arm_right", forearms=("forearm_right",), body="torso", head="head", style="swipe",
                 windup=0.35, strike=0.55)
    attack.rot("arm_left", {0: (0, 0, 0), 0.3: (-50, 20, -10), 0.6: (-40, 0, -5), 0.9: (0, 0, 0)}, interp="linear")
    attack.rot("forearm_left", {0: (-40, 0, 0), 0.3: (-60, 0, 0), 0.9: (-40, 0, 0)}, interp="linear")

    death = m.animation("death", 1.8, "once")
    death_fall(death, direction="forward")
    death.rot("arm_right", {0: (0, 0, 0), 1.2: (-150, 0, 20), 1.8: (-150, 0, 20)}, interp="linear")
    death.rot("cape", {0: (0, 0, 0), 1.0: (-30, 0, 0), 1.8: (-20, 0, 0)}, interp="linear")
    return _check(m, MOB_CLIPS)


MODELS = {
    "bigcasares_myth_odin": build_odin,
    "bigcasares_myth_odin_raven": build_raven,
    "bigcasares_myth_odin_wolf": build_wolf,
    "bigcasares_myth_odin_einherjar": build_einherjar,
}
