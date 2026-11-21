"""Norse mythology creatures for the mythic dungeons: draugr, dvergr smith, Nidhogg, Hel.

Same approach as greek.py: a modest number of varied cuboids, relief painted into shaded
textures, overlay layers (helmet, mail, bracers, beard fringe) and alpha planes (cloaks,
braids, wings, robes).  Model faces north (-Z); the creature's right is +X.
"""
import sys
from pathlib import Path

sys.path.insert(0, str(Path(__file__).resolve().parents[1]))
from bbkit import (Model, attack_swing, death_fall, idle_breathe, mix, ramp,  # noqa: E402
                   walk_biped, walk_quadruped)


def dk(c, t=0.4):
    return mix(c, "#000000", t)


def lt(c, t=0.3):
    return mix(c, "#ffffff", t)


KNOT = [  # interlace band, 8 px repeat, 3 rows
    ".gg..gg.",
    "g..gg..g",
    ".gg..gg.",
]
RUNES = {  # 3x5 glyphs
    "fehu": ["g.g", "ggg", "g..", "g..", "g.."],
    "uruz": ["g.g", "g.g", "g.g", "g.g", "ggg"],
    "thurs": ["g..", "gg.", "ggg", "gg.", "g.."],
    "ansuz": ["gg.", "g.g", "gg.", "g.g", "g.g"],
    "sowilo": ["..g", ".g.", "g.g", ".g.", "g.."],
    "tiwaz": [".g.", "ggg", ".g.", ".g.", ".g."],
    "hagal": ["g.g", "ggg", "g.g", "g.g", "g.g"],
    "nauth": [".g.", "ggg", ".g.", "g..", "g.."],
    "isa": [".g.", ".g.", ".g.", ".g.", ".g."],
    "ing": [".g.", "g.g", ".g.", "g.g", ".g."],
}


def flecks(face, color, n, size=(1, 2), **kw):
    return face.spots(color, n=n, size=size, **kw)


# =============================================================================== DRAUGR

def build_draugr():
    SKIN, SKIN_D, ROT = "#6f8794", "#475d69", "#566b55"
    MAIL, RUST, FROST = "#58626b", "#8a5230", "#dcf2fa"
    FUR, FUR_D, LEATHER = "#5a4b40", "#30271f", "#3a2a1e"
    EYE, IRON, WOOD, BONE = "#8ff0ff", "#7d868e", "#5b3d27", "#cfc8b0"
    frost = FROST + "c8"
    rust = RUST + "b0"

    m = Model("bigcasares_myth_draugr", texture_size=(128, 128))
    body = m.bone("body", pivot=(0, 14, 0))
    torso = m.bone("torso", body, pivot=(0, 16, 0))
    head = m.bone("head", torso, pivot=(0, 26, 0))
    arm_r = m.bone("arm_right", torso, pivot=(7.75, 24.5, 0))
    fore_r = m.bone("forearm_right", arm_r, pivot=(7.75, 16.5, 0))
    weapon = m.bone("axe", fore_r, pivot=(7.75, 8.5, 0), rotation=(-20, 0, 0))
    arm_l = m.bone("arm_left", torso, pivot=(-7.75, 24.5, 0))
    fore_l = m.bone("forearm_left", arm_l, pivot=(-7.75, 16.5, 0))
    leg_r = m.bone("leg_right", body, pivot=(2.75, 14, 0))
    shin_r = m.bone("shin_right", leg_r, pivot=(2.75, 7, 0))
    leg_l = m.bone("leg_left", body, pivot=(-2.75, 14, 0))
    shin_l = m.bone("shin_left", leg_l, pivot=(-2.75, 7, 0))
    cloak = m.bone("cloak", torso, pivot=(0, 26, 4))
    skirt = m.bone("loincloth", body, pivot=(0, 14, -3.2))

    # --- pelvis, belt, rag loincloth
    pelvis = m.cube(body, "pelvis", (-5, 12, -3), (5, 16, 3))
    pelvis.paint(LEATHER, jitter=0.2)
    for f in pelvis.sides():
        f.stripe(dk(LEATHER, 0.3), every=3, axis="v", offset=1).spots(rust, n=3)
    belt = m.cube(body, "belt", (-5.5, 14, -3.5), (5.5, 16.5, 3.5))
    belt.paint("#2b1e15", jitter=0.12)
    belt.north.rect(4, 0, 3, 2, IRON).outline(dk(IRON), 4, 0, 3, 2).pixel(5, 1, lt(IRON, 0.5))
    for f in belt.sides():
        f.rect(0, 0, f.w, 1, lt("#2b1e15", 0.2))
    rag = m.plane(skirt, "loincloth_front", (-3.5, 6, -3.7), (3.5, 14.2, -3.7))
    rag.front.gradient("#4a3a2c", "#241a12", steps=4).noise(0.08)
    rag.front.stripe(dk("#4a3a2c", 0.5), every=3, axis="v", offset=1, y=0, h=4).tatter(4)
    rag.front.spots(FROST + "90", n=3)

    # --- legs: wrapped shins, rusted greaves, frozen boots
    thigh = m.cube(leg_r, "thigh_right", (0.5, 6.5, -2.5), (5, 14, 2.5))
    thigh.paint("#4a4540", jitter=0.2)
    for f in thigh.sides():
        f.stripe(dk("#4a4540", 0.35), every=3, axis="h", offset=1).spots(SKIN_D, n=2)
    thigh.north.rect(1, 3, 2, 3, dk("#4a4540", 0.25)).pixel(2, 4, SKIN_D)  # torn patch
    shin = m.cube(shin_r, "shin_plate_right", (0.75, 1.5, -2.5), (4.75, 7, 2.5))
    shin.paint(SKIN, jitter=0.2)
    greave = shin.overlay(0.4, "greave_right")
    for f in greave.sides():
        f.rect(0, 0, f.w, 4, IRON).noise(0.1).rect(0, 0, f.w, 1, lt(IRON, 0.35))
        f.rect(0, 3, f.w, 1, dk(IRON, 0.45)).spots(rust, n=3).pixel(1, 1, lt(IRON, 0.5))
    greave.up.fill(dk(IRON, 0.2))
    boot = m.cube(shin_r, "boot_right", (0.5, 0, -3.5), (5, 2, 2.5))
    boot.paint("#2d231b", jitter=0.15)
    boot.north.rect(0, 0, 4, 1, FROST + "b0")
    boot.up.dither(FROST + "a0", pattern="noise", density=0.35)
    thigh.mirror_x(leg_l, "thigh_left")
    shin.mirror_x(shin_l, "shin_plate_left")
    greave.mirror_x(shin_l, "greave_left")
    boot.mirror_x(shin_l, "boot_left")

    # --- torso: ribs showing through torn mail, belts, frost
    waist = m.cube(torso, "waist", (-5, 16, -3), (5, 20, 3))
    waist.paint(SKIN_D, jitter=0.2)
    waist.north.stripe(dk(SKIN_D, 0.4), every=2, axis="h", offset=1, x=2, w=6).spots(ROT, n=3)
    chest = m.cube(torso, "chest", (-6, 19.5, -3.5), (6, 26.5, 3.5))
    chest.paint(MAIL, jitter=0.25)
    for f in chest.faces():
        f.dither((0, 0, 0, 30), pattern="checker")
        f.stripe((0, 0, 0, 40), every=3, axis="h", offset=2)
        f.spots(rust, n=3)
    n = chest.north
    n.rect(7, 2, 4, 4, SKIN_D)  # torn hole, ribs inside
    n.stripe(dk(SKIN_D, 0.5), every=2, axis="h", offset=0, x=7, y=2, w=4, h=4)
    n.pixel(7, 2, MAIL).pixel(10, 5, MAIL).pixel(11, 2, dk(MAIL, 0.3))
    n.line(0, 0, 5, 6, LEATHER).line(1, 0, 6, 6, dk(LEATHER, 0.25))  # baldric
    n.jagged(frost, edge="top", depth=(1, 2))
    chest.up.dither(frost, pattern="noise", density=0.45)
    chest.south.jagged(frost, edge="top", depth=(1, 3))
    mail_skirt = chest.overlay(0.35, "mail_hem")
    for f in mail_skirt.sides():
        f.rect(0, 5, f.w, 2, MAIL).dither((0, 0, 0, 70), y=5, h=2).jagged(dk(MAIL, 0.2), edge="bottom", depth=(0, 1))
        f.rect(0, 5, f.w, 1, lt(MAIL, 0.25)).spots(rust, n=2, y=5, h=2).jagged(frost, edge="bottom", depth=(0, 1))
    mantle = m.cube(torso, "mantle", (-7.5, 24, -4.2), (7.5, 27.8, 4.2))
    mantle.paint(FUR, noise=0.05, jitter=0.18)
    for f in mantle.sides():
        f.strands(FUR_D, density=0.4, length=(2, 3)).strands(lt(FUR, 0.25), density=0.15, length=(1, 2))
    mantle.up.fill(FUR_D).noise(0.12).dither(lt(FUR, 0.2), density=0.25, pattern="noise")
    mantle.up.dither(frost, density=0.3, pattern="noise")
    fringe = mantle.overlay(0.35, "mantle_fringe")
    for f in fringe.sides():
        f.jagged(FUR_D, edge="bottom", depth=(1, 3)).noise(0.1)
    fringe.up.dither(frost, density=0.25, pattern="noise")

    # --- head: skull face, iron helmet, braided beard
    skull = m.cube(head, "skull", (-4, 26, -4), (4, 34, 4))
    skull.paint(SKIN, jitter=0.2)
    f = skull.north
    f.rect(0, 3, 1, 4, SKIN_D).rect(7, 3, 1, 4, SKIN_D)  # hollow cheeks
    f.rect(3, 3, 2, 3, mix(SKIN, SKIN_D, 0.4)).pixel(3, 5, SKIN_D).pixel(4, 5, SKIN_D)  # nose bridge
    f.eyes(4, 3, w=2, h=1, gap=2, glow=EYE, socket="#10181d")
    f.rect(2, 6, 4, 1, "#1a1210").pixel(3, 6, BONE).pixel(5, 6, BONE).pixel(4, 7, "#1a1210")
    f.pixel(1, 4, ROT).pixel(6, 5, ROT).pixel(2, 3, ROT)
    for s in (skull.east, skull.west):
        s.rect(0, 4, 3, 2, SKIN_D).spots(ROT, n=2)
    helmet = skull.overlay(0.55, "helmet")
    helmet.up.shaded(IRON, jitter=0.2, shade=False).outline(dk(IRON, 0.4)).spots(rust, n=4)
    helmet.up.line(4, 0, 4, 7, dk(IRON, 0.5))
    helmet.up.dither(frost, density=0.3, pattern="noise")
    for fc in (helmet.north, helmet.south, helmet.east, helmet.west):
        fc.rect(0, 0, fc.w, 3, IRON).noise(0.1).rect(0, 0, fc.w, 1, lt(IRON, 0.35))
        fc.rect(0, 2, fc.w, 1, dk(IRON, 0.5)).spots(rust, n=2, h=3)
        fc.pixel(1, 1, lt(IRON, 0.55)).pixel(fc.w - 2, 1, lt(IRON, 0.55))
        fc.jagged(frost, edge="top", depth=(0, 1))
    helmet.north.rect(3, 3, 2, 3, IRON).rect(3, 3, 1, 3, lt(IRON, 0.2)).pixel(3, 5, dk(IRON, 0.4)).pixel(4, 5, dk(IRON, 0.4))
    helmet.south.rect(0, 3, 8, 3, dk(IRON, 0.15)).stripe(dk(IRON, 0.5), every=2, axis="v", y=3, h=3)
    crest = m.cube(head, "helmet_crest", (-0.75, 34, -4.25), (0.75, 35.25, 4.25))
    crest.paint(IRON, jitter=0.2)
    crest.up.spots(rust, n=3)
    for horn_x, rz in ((4.4, -22.5), (-6.4, 22.5)):
        horn = m.cube(head, f"horn_{'right' if horn_x > 0 else 'left'}", (horn_x, 30.5, -1.5), (horn_x + 2, 36, 0.5),
                      rotation=(0, 0, rz))
        horn.paint(BONE, jitter=0.15)
        for s in horn.sides():
            s.gradient(BONE, dk(BONE, 0.45), steps=4, y=0).rect(0, 0, s.w, 2, dk(BONE, 0.55))
        horn.up.fill(dk(BONE, 0.5))
    beard = m.plane(head, "beard", (-4, 17.5, -5.0), (4, 27.2, -5.0))
    bf = beard.front
    bf.gradient("#8c9ca4", "#3c4a52", steps=4).noise(0.1)
    bf.strands(lt("#8c9ca4", 0.3), density=0.5, length=(3, 6)).strands(dk("#3c4a52", 0.3), density=0.4, length=(3, 5))
    for yy in range(bf.h):  # taper to a point
        half = (bf.w / 2) * (1 - 0.75 * yy / bf.h)
        bf.clear(0, yy, int(bf.w / 2 - half), 1).clear(int(bf.w / 2 + half), yy, bf.w, 1)
    bf.tatter(2, chance=0.4).spots(FROST + "c0", n=5)
    for side, bx in (("right", 2.2), ("left", -4.2)):
        braid = m.plane(head, f"braid_{side}", (bx, 15, -5.3), (bx + 2, 26, -5.3))
        bp = braid.front
        bp.fill("#7d8e97").noise(0.1)
        for yy in range(0, bp.h - 3):
            bp.pixel((yy // 2) % 2, yy, dk("#7d8e97", 0.3)).pixel(1 - (yy // 2) % 2, yy, lt("#7d8e97", 0.12))
        bp.rect(0, bp.h - 3, bp.w, 1, LEATHER).tatter(2).rect(0, 0, 2, 1, "#7d8e97")
        bp.pixel(0, bp.h - 3, rust).spots(FROST + "c0", n=2)

    # --- arms: right one holds the axe, left hangs ragged
    up_r = m.cube(arm_r, "upper_arm_right", (6, 16.5, -2), (9.5, 25, 2))
    up_r.paint(MAIL, jitter=0.22)
    for fc in up_r.faces():
        fc.dither((0, 0, 0, 55), pattern="checker").spots(rust, n=3)
    up_r.north.rect(0, 5, 4, 3, SKIN).rect(0, 5, 4, 1, dk(MAIL, 0.3)).pixel(1, 6, ROT)
    pauld = m.cube(arm_r, "pauldron_right", (6, 23.5, -3), (10.25, 26.2, 3))
    pauld.paint(IRON, jitter=0.15)
    pauld.up.outline(dk(IRON, 0.45)).rivets(lt(IRON, 0.4), every=3).spots(rust, n=3)
    for s in pauld.sides():
        s.rect(0, 0, s.w, 1, lt(IRON, 0.3)).jagged(dk(IRON, 0.3), edge="bottom", depth=(0, 1)).spots(rust, n=2)
    pauld.up.dither(frost, density=0.4, pattern="noise")
    low_r = m.cube(fore_r, "lower_arm_right", (6.25, 9.5, -2), (9.25, 16.5, 2))
    low_r.paint(SKIN, jitter=0.2)
    bracer = low_r.overlay(0.3, "bracer_right")
    for s in bracer.sides():
        s.rect(0, 0, s.w, 3, "#3d2c1f").noise(0.08).stripe(dk("#3d2c1f", 0.3), every=2, axis="h", offset=1, h=3)
        s.pixel(1, 1, IRON)
    hand_r = m.cube(fore_r, "hand_right", (6.5, 7, -1.75), (9, 9.5, 1.75))
    hand_r.paint(dk(SKIN, 0.1), jitter=0.2)
    hand_r.north.stripe(dk(SKIN, 0.4), every=2, axis="v", offset=1, y=0, h=2)

    up_l = m.cube(arm_l, "upper_arm_left", (-9.5, 16.5, -2), (-6, 25, 2))
    up_l.paint(SKIN, jitter=0.2)
    for s in up_l.sides():
        s.rect(0, 0, s.w, 3, FUR_D).jagged(FUR_D, edge="top", depth=(2, 4)).spots(ROT, n=2, y=4)
    up_l.north.rect(2, 5, 2, 2, SKIN_D).pixel(2, 6, "#2d3b45")
    low_l = m.cube(fore_l, "lower_arm_left", (-9.25, 9.5, -2), (-6.25, 16.5, 2))
    low_l.paint(SKIN, jitter=0.2)
    low_l.north.line(0, 1, 2, 4, ROT).line(0, 5, 2, 6, SKIN_D)
    wrap = low_l.overlay(0.3, "wrap_left")
    for s in wrap.sides():
        s.stripe("#4a4038", every=2, axis="h", offset=0, h=4).noise(0.1).tatter(1, edge="bottom")
    hand_l = m.cube(fore_l, "hand_left", (-9, 7, -1.75), (-6.5, 9.5, 1.75))
    hand_l.paint(dk(SKIN, 0.1), jitter=0.2)
    hand_l.north.stripe(dk(SKIN, 0.4), every=2, axis="v", offset=1, y=0, h=2)

    # --- rusted bearded axe (vertical handle, bone tipped forward at rest)
    handle = m.cube(weapon, "axe_handle", (7, 3, -0.75), (8.5, 25, 0.75))
    handle.paint(WOOD, jitter=0.2)
    for s in handle.sides():
        s.stripe(dk(WOOD, 0.4), every=5, axis="h", offset=2).spots(FROST + "b0", n=1)
    handle.north.rect(0, 7, 2, 4, LEATHER).stripe(dk(LEATHER, 0.4), every=2, axis="h", offset=0, y=7, h=4)
    pommel = m.cube(weapon, "axe_pommel", (6.75, 1.8, -1), (8.75, 3.3, 1))
    pommel.paint(IRON, jitter=0.15)
    socket = m.cube(weapon, "axe_socket", (6.75, 19, -1), (8.75, 25.5, 1))
    socket.paint(IRON, jitter=0.2)
    socket.north.stripe(dk(IRON, 0.4), every=3, axis="h", offset=1)
    blade = m.plane(weapon, "axe_blade", (7.75, 14, -10.5), (7.75, 27, -0.75))
    bp = blade.front  # east face: x=0 at the handle (+Z), x=w-1 cutting edge (-Z)
    w, h = bp.w, bp.h
    for y in range(h):
        t = abs(y - (h - 1) / 2) / ((h - 1) / 2)
        x0 = int(round((w - 5) * t * t))
        x1 = w - 1 - int(round(2.5 * t))
        bp.rect(x0, y, x1 - x0 + 1, 1, IRON)
        bp.pixel(x1, y, "#e4eef2").pixel(x0, y, "#4c535a")
    bp.noise(0.07).line(1, h // 2, w - 4, h // 2, dk(IRON, 0.3))
    bp.spots(rust, n=8, size=(1, 2)).dither(frost, density=0.12, pattern="noise")
    bp.clear(w - 1, 3, 1, 1).clear(w - 2, h - 5, 1, 2).clear(w - 1, h // 2 + 2, 1, 1)  # nicks
    bp.decal(RUNES["isa"], {"g": dk(IRON, 0.5)}, x=3, y=4)

    # --- tattered fur cloak (back plane + side flaps)
    back = m.plane(cloak, "cloak_back", (-7, 3, 4.5), (7, 27, 4.5))
    cb = back.front
    cb.gradient(FUR, FUR_D, steps=5, y=4).noise(0.08)
    cb.rect(0, 0, cb.w, 4, FUR)
    cb.strands(FUR_D, density=0.55, length=(3, 6)).strands(lt(FUR, 0.2), density=0.2, length=(2, 4))
    cb.stripe(FUR_D, every=6, axis="v", offset=2, y=3, h=18)
    cb.tatter(7, chance=0.9).spots(FROST + "c0", n=10).dither(FROST + "90", density=0.12, pattern="noise", h=6)
    for side, cx in (("right", 6.9), ("left", -6.9)):
        flap = m.plane(cloak, f"cloak_side_{side}", (cx, 12, -2.5), (cx, 25, 4.2))
        ff = flap.front
        ff.gradient(FUR, FUR_D, steps=4, y=2).noise(0.08).strands(FUR_D, density=0.5, length=(3, 5))
        ff.tatter(5, chance=0.85).spots(FROST + "c0", n=4)

    # --- animations
    idle = m.animation("idle", 2.8, "loop")
    idle_breathe(idle, body="body", chest="torso", head="head", arms=("arm_left", "arm_right"), amount=0.3, nod=3)
    idle.rot("cloak", {0: (0, 0, 0), 1.4: (3, 0, 0), 2.8: (0, 0, 0)})
    idle.rot("head", {0: (4, 0, 0), 0.7: (6, -4, 3), 1.4: (4, 0, 0), 2.1: (7, 5, -2), 2.8: (4, 0, 0)})
    idle.rot("arm_right", {0: (0, 0, 2), 1.4: (-4, 0, 4), 2.8: (0, 0, 2)})
    idle.rot("axe", {0: (0, 0, 0), 1.4: (4, 0, 0), 2.8: (0, 0, 0)})

    walk = m.animation("walk", 1.7, "loop")
    walk_biped(walk, legs=("leg_left", "leg_right"), shins=("shin_left", "shin_right"),
               arms=("arm_left", "arm_right"), forearms=("forearm_left", "forearm_right"),
               body="body", head="head", swing=20, arm_swing=10, knee=24, bob=0.5, sway=5, elbow=10)
    L = walk.length
    walk.rot("torso", {0: (10, 6, 0), L / 4: (12, 0, 3), L / 2: (10, -6, 0), 3 * L / 4: (12, 0, -3), L: (10, 6, 0)})
    walk.rot("head", {0: (6, -6, 4), L / 2: (4, 6, -4), L: (6, -6, 4)})
    walk.rot("leg_left", {0: (-18, 0, 0), L / 4: (-4, 0, 0), L / 2: (24, 0, 0), 3 * L / 4: (6, 0, 0), L: (-18, 0, 0)})  # dragged foot
    walk.rot("arm_left", {0: (14, 0, 3), L / 2: (-6, 0, 6), L: (14, 0, 3)})
    walk.rot("forearm_left", {0: (-22, 0, 0), L / 2: (-10, 0, 0), L: (-22, 0, 0)})
    walk.rot("arm_right", {0: (-14, 0, 2), L / 2: (-26, 0, 2), L: (-14, 0, 2)})
    walk.rot("forearm_right", {0: (-24, 0, 0), L / 2: (-34, 0, 0), L: (-24, 0, 0)})
    walk.rot("axe", {0: (-6, 0, 0), L / 2: (8, 0, 0), L: (-6, 0, 0)})
    walk.rot("cloak", {0: (4, 0, 0), L / 4: (-3, 3, 0), L / 2: (4, 0, 0), 3 * L / 4: (-3, -3, 0), L: (4, 0, 0)})
    walk.rot("loincloth", {0: (-5, 0, 0), L / 2: (6, 0, 0), L: (-5, 0, 0)})

    attack = m.animation("attack", 1.3, "once")
    attack_swing(attack, "arm_right", forearms=("forearm_right",), body="torso", head="head", style="overhead",
                 windup=0.42, strike=0.58)
    attack.rot("axe", {0: (0, 0, 0), 0.546: (150, 0, 0), 0.754: (160, 0, 0), 0.954: (125, 0, 0), 1.3: (0, 0, 0)}, interp="linear")
    attack.rot("arm_left", {0: (0, 0, 0), 0.55: (-70, 0, -18), 0.75: (-30, 0, -10), 1.3: (0, 0, 0)}, interp="linear")
    attack.rot("forearm_left", {0: (0, 0, 0), 0.55: (-40, 0, 0), 0.75: (-30, 0, 0), 1.3: (0, 0, 0)}, interp="linear")
    attack.pos("body", {0: (0, 0, 0), 0.55: (0, 0.4, 1), 0.75: (0, -1.4, -2), 1.3: (0, 0, 0)}, interp="linear")
    attack.rot("cloak", {0: (0, 0, 0), 0.55: (-8, 0, 0), 0.75: (12, 0, 0), 1.3: (0, 0, 0)}, interp="linear")

    death = m.animation("death", 2.2, "once")
    death_fall(death, direction="back")
    death.rot("arm_right", {0: (0, 0, 0), 0.6: (-20, 0, 30), 1.4: (10, 0, 75), 2.2: (10, 0, 75)}, interp="linear")
    death.rot("arm_left", {0: (0, 0, 0), 0.6: (-50, 0, -20), 1.4: (-20, 0, -70), 2.2: (-20, 0, -70)}, interp="linear")
    death.rot("head", {0: (0, 0, 0), 0.5: (-20, 0, 0), 1.5: (18, 30, 0), 2.2: (18, 30, 0)}, interp="linear")
    death.rot("leg_right", {0: (0, 0, 0), 0.8: (-10, 0, 6), 2.2: (-10, 0, 6)}, interp="linear")
    death.rot("shin_right", {0: (0, 0, 0), 0.8: (30, 0, 0), 2.2: (30, 0, 0)}, interp="linear")
    death.rot("cloak", {0: (0, 0, 0), 0.8: (-10, 0, 0), 2.2: (-6, 0, 0)}, interp="linear")
    return m


# =============================================================================== DWARF (dvergr smith)

def build_dwarf():
    SKIN, BEARD, BEARD_D = "#d8a07c", "#a8501f", "#6a2e14"
    IRON, GOLD, TUNIC, LEATHER = "#8c939b", "#dcab3c", "#35503e", "#5d3b22"
    EMBER, SOOT, STEEL = "#ff9a3c", "#241a14", "#a9b2ba"
    m = Model("bigcasares_myth_dwarf", texture_size=(128, 128))
    body = m.bone("body", pivot=(0, 4, 0))
    torso = m.bone("torso", body, pivot=(0, 5, 0))
    head = m.bone("head", torso, pivot=(0, 9, 0))
    beard = m.bone("beard", torso, pivot=(0, 11, -4))
    arm_r = m.bone("arm_right", torso, pivot=(6, 9, 0))
    fore_r = m.bone("forearm_right", arm_r, pivot=(6, 5, 0))
    hammer = m.bone("hammer", fore_r, pivot=(6, 3.2, 0), rotation=(-32, 0, 0))
    arm_l = m.bone("arm_left", torso, pivot=(-6, 9, 0))
    fore_l = m.bone("forearm_left", arm_l, pivot=(-6, 5, 0))
    leg_r = m.bone("leg_right", body, pivot=(2.25, 4.2, 0))
    leg_l = m.bone("leg_left", body, pivot=(-2.25, 4.2, 0))
    apron_b = m.bone("apron", body, pivot=(0, 6, -3.3))

    # legs: stubby, wrapped, big boots
    thigh = m.cube(leg_r, "thigh_right", (0.2, 1.6, -1.8), (4.3, 4.4, 1.8))
    thigh.paint("#4a3a2c", jitter=0.2)
    for f in thigh.sides():
        f.stripe(dk("#4a3a2c", 0.35), every=2, axis="h", offset=1).spots(SOOT, n=2)
    boot = m.cube(leg_r, "boot_right", (-0.1, 0, -2.6), (4.6, 2.2, 2.2))
    boot.paint(LEATHER, jitter=0.15)
    boot.north.rect(0, 0, 5, 1, dk(LEATHER, 0.3)).rect(2, 1, 1, 1, GOLD)
    boot.up.fill(dk(LEATHER, 0.3))
    cuff = boot.overlay(0.3, "boot_cuff_right")
    for f in cuff.sides():
        f.rect(0, 0, f.w, 1, "#c9b28a").jagged("#c9b28a", edge="top", depth=(0, 1))
    thigh.mirror_x(leg_l, "thigh_left")
    boot.mirror_x(leg_l, "boot_left")
    cuff.mirror_x(leg_l, "boot_cuff_left")

    # torso: tunic, studded belt, chest straps
    pelvis = m.cube(body, "pelvis", (-4.6, 3.6, -2.8), (4.6, 5.2, 2.8))
    pelvis.paint(dk(TUNIC, 0.2), jitter=0.2)
    chest = m.cube(torso, "chest", (-5, 4.6, -3), (5, 9.4, 3))
    chest.paint(TUNIC, jitter=0.2)
    for f in chest.sides():
        f.stripe(dk(TUNIC, 0.3), every=3, axis="v", offset=1).spots(SOOT + "a0", n=3)
    chest.north.line(0, 0, 5, 4, LEATHER).line(1, 0, 6, 4, dk(LEATHER, 0.3)).pixel(2, 2, GOLD)
    chest.north.rect(7, 1, 2, 2, GOLD).outline(dk(GOLD, 0.4), 7, 1, 2, 2)
    belt = m.cube(body, "belt", (-5.3, 4.2, -3.3), (5.3, 6, 3.3))
    belt.paint("#2a1c12", jitter=0.1)
    belt.north.rect(4, 0, 2, 2, GOLD).outline(dk(GOLD, 0.45), 4, 0, 2, 2).pixel(4, 0, lt(GOLD, 0.5))
    belt.north.rivets(GOLD, every=3, inset=0)
    apron = m.plane(apron_b, "apron_cloth", (-4, 0.8, -3.6), (4, 6, -3.6))
    af = apron.front
    af.gradient(LEATHER, dk(LEATHER, 0.35), steps=4).noise(0.07)
    af.stripe(dk(LEATHER, 0.45), every=1, axis="v", offset=0, x=0, w=1).stripe(dk(LEATHER, 0.45), every=1, axis="v", x=7, w=1)
    af.rect(1, 1, 3, 2, dk(LEATHER, 0.3)).outline(dk(LEATHER, 0.6), 1, 1, 3, 2).pixel(2, 1, IRON)  # pouch
    af.spots(SOOT + "b0", n=5, size=(1, 2)).spots(EMBER + "90", n=1, size=(1, 1)).tatter(1, chance=0.5)
    tools = m.plane(apron_b, "apron_tools", (1.2, 3, -3.7), (3.6, 6.4, -3.7))
    tf = tools.front
    tf.decal(["s.s", "s.s", "s.s", ".s.", ".b.", ".b."], {"s": STEEL, "b": WOOD if False else "#6a4628"}, x=0, y=0)
    tools.front.clear(0, 6, 3, 1)

    # head: round face, big nose, iron helmet with horns
    skull = m.cube(head, "skull", (-3.5, 9, -3), (3.5, 15, 3))
    skull.paint(SKIN, jitter=0.18)
    f = skull.north
    f.rect(0, 3, 1, 2, "#e0907a").rect(6, 3, 1, 2, "#e0907a")  # ruddy cheeks
    f.eyes(3.5, 2, w=1, h=1, gap=3, white="#f4ede0", pupil="#1a1410", look="center")
    f.rect(1, 1, 2, 1, BEARD_D).rect(4, 1, 2, 1, BEARD_D)  # heavy brows
    f.rect(3, 4, 1, 2, dk(SKIN, 0.15))
    for s in (skull.east, skull.west):
        s.rect(0, 0, 6, 1, BEARD_D).pixel(1, 2, SKIN).strands(BEARD_D, density=0.3, y=2, h=4)
    nose = m.cube(head, "nose", (-1.25, 10.8, -4.3), (1.25, 12.8, -3))
    nose.paint("#e29a80", jitter=0.15)
    nose.north.rect(0, 1, 1, 1, "#c0705a").rect(2, 1, 1, 1, "#c0705a")
    helm = skull.overlay(0.5, "helmet")
    helm.up.shaded(IRON, jitter=0.18, shade=False).outline(dk(IRON, 0.4)).line(3, 0, 3, 5, dk(IRON, 0.4))
    for fc in (helm.north, helm.south, helm.east, helm.west):
        fc.rect(0, 0, fc.w, 2, IRON).noise(0.1).rect(0, 0, fc.w, 1, lt(IRON, 0.35))
        fc.rect(0, 1, fc.w, 1, dk(IRON, 0.35)).stripe(GOLD, every=5, axis="v", offset=2, h=1)
        fc.pixel(0, 0, dk(IRON, 0.3))
    helm.north.rect(3, 2, 1, 2, IRON).rect(3, 2, 1, 1, lt(IRON, 0.2))
    for side, sx, rz in (("right", 3.4, -22.5), ("left", -5.4, 22.5)):
        hb = m.cube(head, f"horn_base_{side}", (sx, 12.6, -1.2), (sx + 2, 14.4, 0.8))
        hb.paint("#e8dcc0", jitter=0.12)
        hb.north.rect(0, 0, 1, 2, dk("#e8dcc0", 0.25))
        ht = m.cube(head, f"horn_tip_{side}", (sx + (1 if side == "right" else -1), 13.2, -1), (sx + (3 if side == "right" else 1), 18, 0.6),
                    rotation=(0, 0, rz))
        ht.paint("#e8dcc0", jitter=0.12)
        for s in ht.sides():
            s.gradient("#e8dcc0", "#6a5e48", steps=4).stripe(dk("#e8dcc0", 0.25), every=2, axis="h", offset=1, y=2)
        ht.up.fill("#5e5440")

    # beard: block of braided hair on its own bone + fringe overlay + two braids (planes)
    bd = m.cube(beard, "beard_mass", (-3.8, 3.2, -5.4), (3.8, 10.6, -3.1))
    bd.paint(BEARD, noise=0.06, jitter=0.2)
    for fc in bd.sides():
        fc.strands(BEARD_D, density=0.55, length=(3, 6)).strands(lt(BEARD, 0.25), density=0.2, length=(2, 4))
    bd.north.stripe(BEARD_D, every=3, axis="v", offset=0, y=1)
    bd.north.rect(0, 0, 8, 2, BEARD_D).strands(lt(BEARD, 0.2), density=0.5, length=(1, 2), y=0, h=2)
    bd.north.rect(3, 0, 2, 1, SKIN)  # gap: mouth
    bd.up.fill(BEARD_D).noise(0.12)
    fringe = bd.overlay(0.35, "beard_fringe")
    for fc in fringe.sides():
        fc.jagged(BEARD, edge="bottom", depth=(1, 3)).strands(BEARD_D, density=0.2, length=(2, 3)).noise(0.1)
    fringe.north.rect(2, 3, 1, 1, GOLD).rect(5, 5, 1, 1, GOLD)
    for side, bx in (("right", 1.6), ("left", -3.6)):
        br = m.plane(beard, f"braid_{side}", (bx, 0.4, -5.8), (bx + 2, 7.2, -5.8))
        bp = br.front
        bp.fill(BEARD).noise(0.08)
        for yy in range(0, bp.h - 1):
            bp.pixel((yy // 2) % 2, yy, BEARD_D).pixel(1 - (yy // 2) % 2, yy, lt(BEARD, 0.2))
        bp.rect(0, bp.h - 2, 2, 1, GOLD).rect(0, 2, 2, 1, IRON).rect(0, bp.h - 1, 2, 1, BEARD_D).tatter(1, chance=0.3)
    mus = m.plane(beard, "moustache", (-3, 9.6, -5.5), (3, 11.4, -5.5))
    mp = mus.front
    mp.rect(0, 0, 6, 2, BEARD).strands(BEARD_D, density=0.6, length=(1, 2))
    mp.clear(2, 1, 2, 1).clear(0, 1, 1, 1).clear(5, 1, 1, 1).pixel(0, 0, BEARD_D).pixel(5, 0, BEARD_D)

    # arms with iron pauldrons, bracers; hammer in the right fist
    up_r = m.cube(arm_r, "upper_arm_right", (4.5, 4.8, -1.6), (7.5, 9.2, 1.6))
    up_r.paint(TUNIC, jitter=0.2)
    for s in up_r.sides():
        s.stripe(dk(TUNIC, 0.3), every=3, axis="v", offset=1)
    paul = m.cube(arm_r, "pauldron_right", (4.3, 8, -2.2), (8, 9.9, 2.2))
    paul.paint(IRON, jitter=0.15)
    paul.up.outline(dk(IRON, 0.4)).rivets(GOLD, every=2, inset=1)
    for s in paul.sides():
        s.rect(0, 0, s.w, 1, lt(IRON, 0.3)).jagged(dk(IRON, 0.3), edge="bottom", depth=(0, 1))
    low_r = m.cube(fore_r, "lower_arm_right", (4.6, 2.6, -1.5), (7.4, 5, 1.5))
    low_r.paint(SKIN, jitter=0.15)
    brc = low_r.overlay(0.3, "bracer_right")
    for s in brc.sides():
        s.rect(0, 0, s.w, 2, LEATHER).stripe(dk(LEATHER, 0.4), every=2, axis="v", offset=0, h=2).pixel(1, 0, GOLD)
    fist = m.cube(fore_r, "fist_right", (4.8, 1.2, -1.5), (7.2, 3.2, 1.5))
    fist.paint(dk(SKIN, 0.05), jitter=0.15)
    fist.north.stripe(dk(SKIN, 0.35), every=1, axis="v", offset=1, y=0, h=1)
    up_r.mirror_x(arm_l, "upper_arm_left")
    paul.mirror_x(arm_l, "pauldron_left")
    low_r.mirror_x(fore_l, "lower_arm_left")
    brc.mirror_x(fore_l, "bracer_left")
    fist.mirror_x(fore_l, "fist_left")

    shaft = m.cube(hammer, "hammer_shaft", (5.25, 0, -0.75), (6.75, 16, 0.75))
    shaft.paint("#6a4628", jitter=0.2)
    for s in shaft.sides():
        s.stripe(dk("#6a4628", 0.4), every=4, axis="h", offset=1)
    shaft.north.rect(0, 2, 2, 4, LEATHER).stripe(dk(LEATHER, 0.4), every=2, axis="h", offset=1, y=2, h=4)
    hh = m.cube(hammer, "hammer_head", (2.5, 13.2, -2.5), (9.5, 18.2, 2.5))
    hh.paint(IRON, jitter=0.15)
    for s in hh.sides():
        s.rect(0, 0, s.w, 1, lt(IRON, 0.3)).rect(0, s.h - 1, s.w, 1, dk(IRON, 0.4))
    hh.up.fill(dk(IRON, 0.1)).noise(0.1).rivets(lt(IRON, 0.4), every=2, inset=1)
    hh.north.decal(RUNES["thurs"], {"g": EMBER}, x=3, y=0)
    hh.south.decal(RUNES["uruz"], {"g": EMBER}, x=3, y=0)
    cap_r = m.cube(hammer, "hammer_cap_right", (9.4, 12.6, -2.9), (10.9, 18.8, 2.9))
    cap_r.paint(STEEL, jitter=0.12)
    cap_r.east.rect(1, 1, 4, 4, dk(STEEL, 0.15)).outline(dk(STEEL, 0.5)).pixel(2, 2, lt(STEEL, 0.4))
    m.cube(hammer, "hammer_cap_left", (1.1, 12.6, -2.9), (2.6, 18.8, 2.9), uv=cap_r, mirror=True)
    band = m.cube(hammer, "hammer_band", (5, 11.8, -1), (7, 13.3, 1))
    band.paint(GOLD, jitter=0.1)

    # animations
    idle = m.animation("idle", 2.2, "loop")
    idle_breathe(idle, body="body", head="head", arms=("arm_left", "arm_right"), amount=0.25, nod=2, arm_out=2)
    idle.rot("beard", {0: (0, 0, 0), 1.1: (4, 0, 2), 2.2: (0, 0, 0)})
    idle.rot("hammer", {0: (0, 0, 0), 1.1: (3, 0, 0), 2.2: (0, 0, 0)})
    idle.rot("head", {0: (0, 0, 0), 0.55: (3, 8, 0), 1.1: (1, 0, 0), 1.65: (3, -8, 0), 2.2: (0, 0, 0)})

    walk = m.animation("walk", 0.7, "loop")
    walk_biped(walk, legs=("leg_left", "leg_right"), arms=("arm_left", "arm_right"),
               forearms=("forearm_left", "forearm_right"), body="body", head="head",
               swing=32, arm_swing=22, bob=0.9, sway=7, elbow=14)
    L = walk.length
    walk.rot("beard", {0: (4, 0, -3), L / 4: (-4, 0, 0), L / 2: (4, 0, 3), 3 * L / 4: (-4, 0, 0), L: (4, 0, -3)})
    walk.rot("hammer", {0: (-4, 0, 0), L / 2: (6, 0, 0), L: (-4, 0, 0)})
    walk.rot("apron", {0: (-6, 0, 0), L / 4: (5, 0, 0), L / 2: (-6, 0, 0), 3 * L / 4: (5, 0, 0), L: (-6, 0, 0)})
    walk.rot("torso", {0: (3, 4, 0), L / 2: (3, -4, 0), L: (3, 4, 0)})

    attack = m.animation("attack", 1.0, "once")
    attack_swing(attack, "arm_right", forearms=("forearm_right",), body="torso", head="head", style="overhead",
                 windup=0.45, strike=0.6)
    attack.rot("hammer", {0: (0, 0, 0), 0.45: (140, 0, 0), 0.6: (160, 0, 0), 0.8: (110, 0, 0), 1.0: (0, 0, 0)}, interp="linear")
    attack.rot("arm_left", {0: (0, 0, 0), 0.45: (-60, 0, -15), 0.62: (-25, 0, -10), 1.0: (0, 0, 0)}, interp="linear")
    attack.rot("beard", {0: (0, 0, 0), 0.45: (10, 0, 0), 0.62: (-14, 0, 0), 1.0: (0, 0, 0)}, interp="linear")
    attack.pos("body", {0: (0, 0, 0), 0.45: (0, 0.3, 0.8), 0.62: (0, -0.8, -1.5), 1.0: (0, 0, 0)}, interp="linear")

    death = m.animation("death", 1.6, "once")
    death_fall(death, direction="back")
    death.rot("arm_right", {0: (0, 0, 0), 0.5: (-30, 0, 40), 1.1: (-20, 0, 80), 1.6: (-20, 0, 80)}, interp="linear")
    death.rot("arm_left", {0: (0, 0, 0), 0.5: (-40, 0, -30), 1.1: (-20, 0, -80), 1.6: (-20, 0, -80)}, interp="linear")
    death.rot("head", {0: (0, 0, 0), 0.5: (-18, 0, 0), 1.2: (10, 20, 0), 1.6: (10, 20, 0)}, interp="linear")
    death.rot("beard", {0: (0, 0, 0), 0.7: (-30, 0, 0), 1.6: (-20, 0, 0)}, interp="linear")
    return m


# =============================================================================== NIDHOGG

def _nid_wing_image(W=23, H=20):
    """Bat-wing membrane for the whole wing, shoulder at column 0, leading spar on row 0."""
    import numpy as np
    from PIL import Image, ImageDraw
    from bbkit import rgba
    big = Image.new("L", (W * 8, H * 8), 0)
    poly = [(0, 0), (22.8, 0), (22.8, 2.5), (19.5, 12), (16.2, 9.4), (13, 17.8), (9.6, 13.2), (5.2, 20), (0, 17)]
    ImageDraw.Draw(big).polygon([(x * 8, y * 8) for x, y in poly], fill=255)
    mask = np.array(big.resize((W, H), Image.BOX)) >= 110
    MEM, MEM_D, VEIN, SPAR, LIGHT = "#4f8a68", "#1b3a2c", "#12261d", "#1b2b22", "#8fcaa6"
    img = Image.new("RGBA", (W, H), (0, 0, 0, 0))
    px = img.load()
    for y in range(H):
        for x in range(W):
            if mask[y, x]:
                t = min(1.0, (y / H) * 0.75 + (x / W) * 0.2)
                t = round(t * 3) / 3
                px[x, y] = tuple(mix(MEM, MEM_D, t))
    d = ImageDraw.Draw(img)
    for (x0, y0, x1, y1) in ((11, 1, 19, 12), (11, 1, 13, 17), (11, 1, 16, 9), (1, 1, 5, 19), (1, 1, 0, 16), (3, 1, 9, 13)):
        for (xx, yy) in _bres(x0, y0, x1, y1):
            if 0 <= xx < W and 0 <= yy < H and mask[yy, xx]:
                px[xx, yy] = rgba(VEIN)
    for x in range(W):
        for y in (0, 1):
            if mask[y, x]:
                px[x, y] = rgba(SPAR) if y == 1 else rgba(LIGHT)
    # edge darkening and a few tears
    edge = []
    for y in range(H):
        for x in range(W):
            if mask[y, x] and any(not (0 <= yy < H and 0 <= xx < W and mask[yy, xx]) for xx, yy in ((x + 1, y), (x - 1, y), (x, y + 1), (x, y - 1))):
                edge.append((x, y))
    for x, y in edge:
        if y > 1:
            px[x, y] = rgba(MEM_D)
    for (x, y) in ((6, 12), (7, 12), (7, 13), (15, 12), (16, 12), (11, 8), (18, 8), (3, 15)):
        if 0 <= x < W and 0 <= y < H and mask[y, x] and y > 3:
            px[x, y] = (0, 0, 0, 0)
    return np.array(img)


def _bres(x0, y0, x1, y1):
    dx, dy = abs(x1 - x0), -abs(y1 - y0)
    sx, sy = (1 if x0 < x1 else -1), (1 if y0 < y1 else -1)
    err = dx + dy
    out = []
    while True:
        out.append((x0, y0))
        if x0 == x1 and y0 == y1:
            return out
        e2 = 2 * err
        if e2 >= dy:
            err += dy
            x0 += sx
        if e2 <= dx:
            err += dx
            y0 += sy


def build_nidhogg():
    SC, SC_D = "#1f3328", "#0f1b15"
    SC_L, BELLY = "#37573f", "#8c8a4c"
    EYE, FANG, MOUTH = "#d4ff3a", "#ebe4cc", "#7a1c2a"
    ROOT, ROOT_D, MOSS = "#4d3826", "#2a1d12", "#4f7a3a"
    m = Model("bigcasares_myth_nidhogg", texture_size=(128, 128))
    body = m.bone("body", pivot=(0, 12, 0))
    neck = m.bone("neck", body, pivot=(0, 15, -6), rotation=(26, 0, 0))
    head = m.bone("head", neck, pivot=(0, 15, -13), rotation=(-22, 0, 0))
    jaw = m.bone("jaw", head, pivot=(0, 12.4, -14))
    wing_r = m.bone("wing_right", body, pivot=(4.5, 18.5, -2), rotation=(0, 0, 38))
    wing_ro = m.bone("wing_right_tip", wing_r, pivot=(15.5, 19, -2), rotation=(0, 0, -30))
    wing_l = m.bone("wing_left", body, pivot=(-4.5, 18.5, -2), rotation=(0, 0, -38))
    wing_lo = m.bone("wing_left_tip", wing_l, pivot=(-15.5, 19, -2), rotation=(0, 0, 30))
    legs = {}
    for side, sx in (("right", 1), ("left", -1)):
        legs["fr" + side] = m.bone(f"front_{side}", body, pivot=(sx * 7.4, 14.5, -3.7), rotation=(0, 0, sx * 16))
        legs["fl" + side] = m.bone(f"front_{side}_low", legs["fr" + side], pivot=(sx * 7.4, 6.8, -3.7), rotation=(0, 0, -sx * 16))
        legs["br" + side] = m.bone(f"back_{side}", body, pivot=(sx * 7.5, 15, 9), rotation=(0, 0, sx * 20))
        legs["bl" + side] = m.bone(f"back_{side}_low", legs["br" + side], pivot=(sx * 7.5, 6.4, 9.5), rotation=(0, 0, -sx * 20))
    t1 = m.bone("tail1", body, pivot=(0, 12.5, 11))
    t2 = m.bone("tail2", t1, pivot=(0, 12, 16))
    t3 = m.bone("tail3", t2, pivot=(0, 11.5, 20.5))

    def scale_paint(c, base=SC, hi=SC_L):
        c.paint(base, noise=0.04, jitter=0.1)
        for f in c.sides():
            f.scales(dk(base, 0.4), size=3, highlight=lt(base, 0.22))
        c.up.scales(dk(base, 0.4), size=3, highlight=hi)
        return c

    # --- torso
    chest = scale_paint(m.cube(body, "chest", (-6, 8.5, -7.5), (6, 18.5, 3)))
    chest.down.fill(BELLY).stripe(dk(BELLY, 0.4), every=2, axis="h", offset=1)
    for f in chest.sides():
        f.rect(0, f.h - 3, f.w, 3, BELLY).stripe(dk(BELLY, 0.4), every=2, axis="v", offset=0, y=f.h - 3, h=3)
        f.rect(0, f.h - 3, f.w, 1, dk(BELLY, 0.5))
    chest.north.rect(2, 3, 8, 7, BELLY).stripe(dk(BELLY, 0.45), every=2, axis="h", offset=1, x=2, y=3, w=8, h=7)
    chest.north.rect(2, 3, 8, 7, BELLY).stripe(dk(BELLY, 0.45), every=2, axis="h", offset=1, x=2, y=3, w=8, h=7)
    chest.north.line(6, 3, 6, 9, dk(BELLY, 0.5)).line(5, 3, 5, 9, SC)
    plates = chest.overlay(0.5, "chest_plates")
    plates.up.rect(4, 1, 4, 9, SC_D).line(5, 1, 5, 9, SC_L).line(6, 1, 6, 9, SC_L).noise(0.1)
    for f in plates.sides():
        f.rect(0, 0, f.w, 2, SC_D).jagged(SC_D, edge="top", depth=(1, 3)).spots(MOSS, n=3, h=3)
    abdomen = scale_paint(m.cube(body, "abdomen", (-5.2, 8, 3), (5.2, 16.5, 11)))
    abdomen.down.fill(BELLY).stripe(dk(BELLY, 0.4), every=2, axis="h", offset=1)
    for f in abdomen.sides():
        f.rect(0, f.h - 3, f.w, 3, BELLY).stripe(dk(BELLY, 0.4), every=2, axis="v", offset=0, y=f.h - 3, h=3)
    abdomen.up.rect(3, 0, 4, 8, SC_D).noise(0.1)
    fin = m.plane(body, "spine_fin", (0, 18.2, -7), (0, 24, 10.5))
    ff = fin.front  # east face: x=0 at +Z (back); spikes lean backward
    for x in range(ff.w):
        k = (ff.w - 1 - x) % 4  # position along the spike, front spikes bigger
        height = int(ff.h * (0.4 + 0.6 * (x / ff.w))) if k != 3 else 1
        spike = max(2, int(ff.h * (0.35 + 0.65 * (1 - abs(x - ff.w * 0.45) / (ff.w * 0.6)))))
        hgt = max(1, spike - k * 2)
        ff.rect(x, ff.h - hgt, 1, hgt, SC_D)
        ff.pixel(x, ff.h - hgt, lt(SC_L, 0.1))
    ff.rect(0, ff.h - 2, ff.w, 2, SC).noise(0.1)
    ff.line(0, ff.h - 1, ff.w - 1, ff.h - 1, dk(SC, 0.5))

    # --- neck and head
    nk = scale_paint(m.cube(neck, "neck_seg", (-2.8, 12.5, -13.5), (2.8, 18.5, -5.5)))
    nk.down.fill(BELLY).stripe(dk(BELLY, 0.4), every=2, axis="v", offset=1)
    nk.up.rect(2, 0, 2, 8, SC_D)
    skull = scale_paint(m.cube(head, "skull", (-4, 12, -21), (4, 19, -13)))
    skull.down.fill(dk(SC, 0.3))
    skull.north.rect(0, 2, 2, 1, EYE).rect(6, 2, 2, 1, EYE).pixel(1, 2, lt(EYE, 0.5)).pixel(6, 2, lt(EYE, 0.5))
    for s in (skull.east, skull.west):
        s.rect(5, 2, 3, 2, "#0d1510").rect(5, 2, 3, 1, EYE).pixel(6, 2, lt(EYE, 0.6)).pixel(7, 3, dk(EYE, 0.4))
        s.rect(4, 1, 4, 1, SC_D)
    brow = skull.overlay(0.5, "brow")
    brow.up.rect(0, 0, 8, 3, SC_D).noise(0.1).line(0, 3, 2, 1, SC_L).line(7, 3, 5, 1, SC_L)
    brow.north.rect(0, 0, 8, 2, SC_D).jagged(SC_L, edge="top", depth=(0, 1)).rect(0, 1, 2, 1, dk(SC, 0.6)).rect(6, 1, 2, 1, dk(SC, 0.6))
    for s in (brow.east, brow.west):
        s.rect(4, 0, 4, 2, SC_D).noise(0.1).line(3, 1, 6, 1, SC_L)
    snout = scale_paint(m.cube(head, "snout", (-2.5, 12.5, -26), (2.5, 17, -21)))
    snout.north.rect(0, 0, 1, 2, "#050806").rect(4, 0, 1, 2, "#050806").pixel(0, 0, MOSS).pixel(4, 0, MOSS)  # nostrils
    snout.north.rect(0, 3, 5, 1, dk(SC, 0.4))
    snout.down.fill(MOUTH).line(0, 0, 4, 0, FANG).line(0, 4, 4, 4, FANG)
    snout.up.dither(MOSS + "80", pattern="noise", density=0.2)
    jw = scale_paint(m.cube(jaw, "jaw_seg", (-3.2, 10.6, -25), (3.2, 12.6, -14)))
    jw.up.fill(MOUTH).noise(0.1).stripe(FANG, every=2, axis="v", offset=0, x=0, w=1).stripe(FANG, every=2, axis="v", offset=0, x=5, w=1)
    jw.north.rect(1, 0, 4, 1, FANG)
    jw.down.fill(BELLY)
    for sx, nm in ((1, "right"), (-1, "left")):
        for z, ln in ((-24.5, 3.2), (-19.5, 2.4)):
            fang = m.cube(head, f"upper_fang_{nm}_{int(abs(z))}", (sx * 2.7 - 0.45, 12.6 - ln, z - 0.45), (sx * 2.7 + 0.45, 12.6, z + 0.45))
            fang.paint(FANG, jitter=0.1, shade=False)
            fang.north.gradient(FANG, dk(FANG, 0.3), steps=3)
    for sx, nm in ((1, "right"), (-1, "left")):
        lf = m.cube(jaw, f"lower_fang_{nm}", (sx * 2.4 - 0.45, 12.6, -24.5), (sx * 2.4 + 0.45, 15.2, -23.6))
        lf.paint(FANG, jitter=0.1, shade=False)
        lf.north.gradient(dk(FANG, 0.3), FANG, steps=3)
    for sx, nm in ((1, "right"), (-1, "left")):
        a, b = sorted((sx * 3.4, sx * 5.6))
        hb = m.cube(head, f"horn_base_{nm}", (a, 16.5, -17.5), (b, 19, -9.5), rotation=(-22.5, 0, 0))
        hb.paint(ROOT, jitter=0.22)
        for f in hb.sides():
            f.stripe(ROOT_D, every=3, axis="v", offset=1).spots(MOSS, n=2)
        ht = m.cube(head, f"horn_tip_{nm}", (a + sx * 0.4, 19.4, -9.5), (b - sx * 0.4, 21.4, -1.5), rotation=(-45, 0, 0))
        ht.paint(ROOT, jitter=0.22)
        for f in ht.sides():
            f.gradient(ROOT, ROOT_D, axis="h", steps=3).stripe(ROOT_D, every=3, axis="v", offset=1)
        root = m.plane(head, f"horn_root_{nm}", (sx * 4.5, 17, -17), (sx * 4.5, 25, -2))
        rp = root.front  # east face: x=0 at +Z
        rp.clear()
        w, h = rp.w, rp.h
        # branching root silhouette: trunk rising to the back, with twigs
        for x in range(w):
            y = h - 3 - int((w - 1 - x) * 0.45)
            rp.rect(x, max(0, y), 1, 3, ROOT)
            rp.pixel(x, max(0, y), lt(ROOT, 0.2))
        for (x, y, ln) in ((2, 0, 5), (5, 0, 4), (8, 2, 5), (10, 4, 4)):
            rp.rect(x, y, 1, ln, ROOT_D).pixel(x, y, lt(ROOT, 0.25))
        rp.noise(0.1).spots(MOSS, n=4)

    # --- legs
    def leg(side, sx):
        up = scale_paint(m.cube(legs["fr" + side], f"fore_upper_{side}", (sx * 7.4 - 2, 7, -5.8), (sx * 7.4 + 2, 14.8, -1.6)))
        low = scale_paint(m.cube(legs["fl" + side], f"fore_lower_{side}", (sx * 7.4 - 1.6, 1.6, -5.2), (sx * 7.4 + 1.6, 7.2, -2.2)))
        paw = m.cube(legs["fl" + side], f"fore_paw_{side}", (sx * 7.4 - 2.4, 0, -8.6), (sx * 7.4 + 2.4, 2.2, -2.0))
        paw.paint(dk(SC, 0.1), jitter=0.2)
        paw.up.rect(0, 0, 5, 2, SC_D)
        claws = m.plane(legs["fl" + side], f"fore_claws_{side}", (sx * 7.4 - 2.5, 0.2, -8.7), (sx * 7.4 + 2.5, 2.4, -8.7))
        for x in (0, 2, 4):
            claws.front.pixel(x, 0, FANG).pixel(x, 1, FANG).pixel(x, 2, dk(FANG, 0.3)).pixel(x, 0, lt(FANG, 0.2))
        bup = scale_paint(m.cube(legs["br" + side], f"hind_upper_{side}", (sx * 7.5 - 2.6, 6.4, 5.8), (sx * 7.5 + 2.6, 16, 12.8)))
        bup.north.rect(0, 0, 5, 2, SC_D)
        blow = scale_paint(m.cube(legs["bl" + side], f"hind_lower_{side}", (sx * 7.5 - 1.7, 1.6, 8), (sx * 7.5 + 1.7, 6.8, 11.4)))
        bpaw = m.cube(legs["bl" + side], f"hind_paw_{side}", (sx * 7.5 - 2.4, 0, 5.8), (sx * 7.5 + 2.4, 2.2, 12))
        bpaw.paint(dk(SC, 0.1), jitter=0.2)
        bclaws = m.plane(legs["bl" + side], f"hind_claws_{side}", (sx * 7.5 - 2.5, 0.2, 5.7), (sx * 7.5 + 2.5, 2.4, 5.7))
        for x in (0, 2, 4):
            bclaws.front.pixel(x, 0, FANG).pixel(x, 1, FANG).pixel(x, 2, dk(FANG, 0.3)).pixel(x, 0, lt(FANG, 0.2))
    leg("right", 1)
    leg("left", -1)

    # --- tail chain
    tl1 = scale_paint(m.cube(t1, "tail_seg1", (-4.2, 9, 11), (4.2, 15.5, 16)))
    tl1.down.fill(BELLY)
    tl2 = scale_paint(m.cube(t2, "tail_seg2", (-3, 9.5, 16), (3, 14.5, 20.5)))
    tl2.down.fill(BELLY)
    tl3 = scale_paint(m.cube(t3, "tail_seg3", (-2, 9.5, 20.5), (2, 13.5, 24.5)))
    tl3.down.fill(BELLY)
    for tb, nm, z0, z1, y0, hgt in ((t1, "tail_fin1", 11.2, 16, 15.4, 3), (t2, "tail_fin2", 16, 20.5, 14.4, 3)):
        tfin = m.plane(tb, nm, (0, y0, z0), (0, y0 + hgt, z1))
        fp = tfin.front
        for x in range(fp.w):
            hh = max(1, fp.h - ((fp.w - 1 - x) % 2) * 1)
            fp.rect(x, fp.h - hh, 1, hh, SC_D)
        fp.rect(0, fp.h - 1, fp.w, 1, SC)
    spike = m.cube(t3, "tail_spike", (-0.8, 10.2, 24.5), (0.8, 12.6, 28.2))
    spike.paint(ROOT, jitter=0.2)
    for f in spike.sides():
        f.gradient(ROOT, ROOT_D, steps=3)
    tip = m.plane(t3, "tail_blade", (0, 8.6, 24.5), (0, 15.2, 30))
    tp = tip.front
    for x in range(tp.w):
        t = x / (tp.w - 1)  # x=0 at back (+Z)
        hh = int(round(tp.h * (1 - t * 0.0) * (1 - abs(0.55 - t) * 0.9)))
        y0 = (tp.h - hh) // 2
        tp.rect(x, y0, 1, max(1, hh), SC_D if x % 2 else SC)
    tp.noise(0.1)

    # --- wings: arm bones as cubes + membrane planes painted from one big image
    wing = _nid_wing_image()
    for side, sx, wb, wo in (("right", 1, wing_r, wing_ro), ("left", -1, wing_l, wing_lo)):
        a, b = sorted((sx * 4.5, sx * 15.8))
        hum = scale_paint(m.cube(wb, f"wing_arm_{side}", (a, 17.8, -3.3), (b, 20.2, -0.7)))
        a, b = sorted((sx * 15.2, sx * 27))
        fa = scale_paint(m.cube(wo, f"wing_forearm_{side}", (a, 18.2, -3), (b, 19.8, -1.2)))
        a, b = sorted((sx * 14.6, sx * 15.8))
        thumb = m.cube(wo, f"wing_thumb_{side}", (a, 19, -5), (b, 21.8, -3), rotation=(22.5, 0, 0))
        thumb.paint(FANG, jitter=0.1)
        thumb.north.gradient(FANG, dk(FANG, 0.4), steps=3)
        a, b = sorted((sx * 15.3, sx * 16.6))
        fb = m.cube(wo, f"wing_finger_b_{side}", (a, 18.4, -2), (b, 19.4, 11))
        fb.paint(SC_D, jitter=0.2)
        fa2 = m.cube(wo, f"wing_finger_a_{side}", (sx * 16 - (0 if sx > 0 else 9.5), 18.4, -1.8), (sx * 16 + (9.5 if sx > 0 else 0), 19.4, -0.8),
                     rotation=(0, -45 * sx * 1.0 if False else 45 * (-1 if sx > 0 else 1), 0), pivot=(sx * 16, 18.9, -1.3))
        fa2.paint(SC_D, jitter=0.2)
        inner = m.plane(wb, f"membrane_{side}", (min(sx * 4.5, sx * 15.5), 18.6, -2.5), (max(sx * 4.5, sx * 15.5), 18.6, 17.5))
        outer = m.plane(wo, f"membrane_tip_{side}", (min(sx * 15.5, sx * 27.5), 18.6, -2.5), (max(sx * 15.5, sx * 27.5), 18.6, 17.5))
        for pl, c0, c1 in ((inner, 0, 11), (outer, 11, 23)):
            cols = wing[:, c0:c1]
            pl.front.px[:, :] = cols if sx > 0 else cols[:, ::-1]

    # --- animations
    idle = m.animation("idle", 3.2, "loop")
    idle.pos("body", {0: (0, 0, 0), 1.6: (0, 0.5, 0), 3.2: (0, 0, 0)})
    idle.rot("neck", {0: (0, -4, 0), 0.8: (2, 0, 1), 1.6: (3, 4, 0), 2.4: (1, 0, -1), 3.2: (0, -4, 0)})
    idle.rot("head", {0: (0, 6, 0), 1.6: (-3, -6, 2), 3.2: (0, 6, 0)})
    idle.rot("jaw", {0: (0, 0, 0), 1.0: (6, 0, 0), 1.5: (2, 0, 0), 2.4: (8, 0, 0), 3.2: (0, 0, 0)})
    idle.rot("tail1", {0: (0, -8, 0), 1.6: (0, 8, 0), 3.2: (0, -8, 0)})
    idle.rot("tail2", {0: (0, -10, 0), 1.6: (0, 10, 0), 3.2: (0, -10, 0)})
    idle.rot("tail3", {0: (0, -12, 0), 1.6: (0, 12, 0), 3.2: (0, -12, 0)})
    idle.rot("wing_right", {0: (0, 0, 0), 1.6: (0, 0, 4), 3.2: (0, 0, 0)})
    idle.rot("wing_left", {0: (0, 0, 0), 1.6: (0, 0, -4), 3.2: (0, 0, 0)})
    idle.rot("wing_right_tip", {0: (0, 0, 0), 1.6: (0, 0, 6), 3.2: (0, 0, 0)})
    idle.rot("wing_left_tip", {0: (0, 0, 0), 1.6: (0, 0, -6), 3.2: (0, 0, 0)})

    walk = m.animation("walk", 1.2, "loop")
    walk_quadruped(walk, front=("front_left", "front_right"), back=("back_left", "back_right"), swing=22,
                   body="body", head="head", tail="tail1", bob=0.6, gait="trot")
    L = walk.length
    kn = lambda ph: {0: (ph[0], 0, 0), L / 4: (ph[1], 0, 0), L / 2: (ph[2], 0, 0), 3 * L / 4: (ph[3], 0, 0), L: (ph[0], 0, 0)}
    walk.rot("front_left_low", kn((0, 0, 4, 28)))
    walk.rot("front_right_low", kn((4, 28, 0, 0)))
    walk.rot("back_left_low", kn((4, 28, 0, 0)))
    walk.rot("back_right_low", kn((0, 0, 4, 28)))
    walk.rot("neck", {0: (0, 6, 0), L / 4: (3, 0, 0), L / 2: (0, -6, 0), 3 * L / 4: (3, 0, 0), L: (0, 6, 0)})
    walk.rot("tail1", {0: (0, -12, 0), L / 2: (0, 12, 0), L: (0, -12, 0)})
    walk.rot("tail2", {0: (0, 10, 0), L / 2: (0, -10, 0), L: (0, 10, 0)})
    walk.rot("tail3", {0: (0, 14, 0), L / 2: (0, -14, 0), L: (0, 14, 0)})
    walk.rot("body", {0: (0, 3, 1), L / 2: (0, -3, -1), L: (0, 3, 1)})
    walk.rot("wing_right", {0: (0, 0, 0), L / 4: (0, 0, 5), L / 2: (0, 0, 0), 3 * L / 4: (0, 0, 5), L: (0, 0, 0)})
    walk.rot("wing_left", {0: (0, 0, 0), L / 4: (0, 0, -5), L / 2: (0, 0, 0), 3 * L / 4: (0, 0, -5), L: (0, 0, 0)})
    walk.rot("wing_right_tip", {0: (0, 0, 0), L / 4: (0, 0, 10), L / 2: (0, 0, 0), 3 * L / 4: (0, 0, 10), L: (0, 0, 0)})
    walk.rot("wing_left_tip", {0: (0, 0, 0), L / 4: (0, 0, -10), L / 2: (0, 0, 0), 3 * L / 4: (0, 0, -10), L: (0, 0, 0)})

    attack = m.animation("attack", 1.2, "once")
    attack.rot("neck", {0: (0, 0, 0), 0.4: (-22, 0, 0), 0.58: (26, 0, 0), 0.85: (10, 0, 0), 1.2: (0, 0, 0)}, interp="linear")
    attack.pos("neck", {0: (0, 0, 0), 0.4: (0, 0, 3), 0.58: (0, -2, -6), 0.85: (0, 0, -2), 1.2: (0, 0, 0)}, interp="linear")
    attack.rot("head", {0: (0, 0, 0), 0.4: (-12, 0, 0), 0.58: (14, 0, 0), 1.2: (0, 0, 0)}, interp="linear")
    attack.rot("jaw", {0: (0, 0, 0), 0.4: (38, 0, 0), 0.56: (46, 0, 0), 0.68: (0, 0, 0), 0.8: (10, 0, 0), 1.2: (0, 0, 0)}, interp="linear")
    attack.pos("body", {0: (0, 0, 0), 0.4: (0, 0.4, 1.5), 0.58: (0, -1, -3.5), 1.2: (0, 0, 0)}, interp="linear")
    attack.rot("body", {0: (0, 0, 0), 0.4: (-4, 0, 0), 0.58: (6, 0, 0), 1.2: (0, 0, 0)}, interp="linear")
    attack.rot("wing_right", {0: (0, 0, 0), 0.4: (0, 0, 36), 0.6: (0, 0, 44), 1.2: (0, 0, 0)}, interp="linear")
    attack.rot("wing_left", {0: (0, 0, 0), 0.4: (0, 0, -36), 0.6: (0, 0, -44), 1.2: (0, 0, 0)}, interp="linear")
    attack.rot("wing_right_tip", {0: (0, 0, 0), 0.4: (0, 0, 22), 0.6: (0, 0, 30), 1.2: (0, 0, 0)}, interp="linear")
    attack.rot("wing_left_tip", {0: (0, 0, 0), 0.4: (0, 0, -22), 0.6: (0, 0, -30), 1.2: (0, 0, 0)}, interp="linear")
    attack.rot("front_right", {0: (0, 0, 0), 0.4: (12, 0, 0), 0.58: (-24, 0, 0), 1.2: (0, 0, 0)}, interp="linear")
    attack.rot("front_left", {0: (0, 0, 0), 0.4: (12, 0, 0), 0.58: (-24, 0, 0), 1.2: (0, 0, 0)}, interp="linear")
    attack.rot("tail1", {0: (0, 0, 0), 0.4: (0, 16, 0), 0.7: (0, -20, 0), 1.2: (0, 0, 0)}, interp="linear")
    attack.rot("tail2", {0: (0, 0, 0), 0.5: (0, 18, 0), 0.8: (0, -22, 0), 1.2: (0, 0, 0)}, interp="linear")
    attack.rot("tail3", {0: (0, 0, 0), 0.6: (0, 20, 0), 0.9: (0, -26, 0), 1.2: (0, 0, 0)}, interp="linear")

    death = m.animation("death", 2.4, "once")
    death.pos("body", {0: (0, 0, 0), 0.5: (0, -1, 0), 1.2: (0, -5.2, 0), 2.4: (0, -5.4, 0)}, interp="linear")
    death.rot("body", {0: (0, 0, 0), 0.5: (-6, 0, 0), 1.2: (8, 0, 6), 2.4: (8, 0, 6)}, interp="linear")
    for nm, sx in (("right", 1), ("left", -1)):
        death.rot(f"front_{nm}", {0: (0, 0, 0), 1.2: (-20, 0, sx * 62), 2.4: (-20, 0, sx * 62)}, interp="linear")
        death.rot(f"back_{nm}", {0: (0, 0, 0), 1.2: (12, 0, sx * 62), 2.4: (12, 0, sx * 62)}, interp="linear")
        death.rot(f"front_{nm}_low", {0: (0, 0, 0), 1.2: (10, 0, 0), 2.4: (10, 0, 0)}, interp="linear")
        death.rot(f"back_{nm}_low", {0: (0, 0, 0), 1.2: (30, 0, 0), 2.4: (30, 0, 0)}, interp="linear")
        death.rot(f"wing_{nm}", {0: (0, 0, 0), 0.5: (0, 0, sx * 36), 1.4: (0, 0, sx * -4), 2.4: (0, 0, sx * -10)}, interp="linear")
        death.rot(f"wing_{nm}_tip", {0: (0, 0, 0), 1.4: (0, 0, sx * -16), 2.4: (0, 0, sx * -24)}, interp="linear")
    death.rot("neck", {0: (0, 0, 0), 0.5: (-24, 0, 0), 1.3: (24, 8, 0), 2.4: (26, 8, 0)}, interp="linear")
    death.pos("neck", {0: (0, 0, 0), 1.3: (0, -0.5, -2), 2.4: (0, -0.5, -2)}, interp="linear")
    death.rot("head", {0: (0, 0, 0), 0.5: (-10, 0, 0), 1.3: (5, 0, 8), 2.4: (6, 0, 8)}, interp="linear")
    death.rot("jaw", {0: (0, 0, 0), 0.5: (30, 0, 0), 1.5: (24, 0, 0), 2.4: (24, 0, 0)}, interp="linear")
    death.rot("tail1", {0: (0, 0, 0), 1.2: (-4, 22, 0), 2.4: (-4, 22, 0)}, interp="linear")
    death.rot("tail2", {0: (0, 0, 0), 1.4: (0, 26, 0), 2.4: (0, 26, 0)}, interp="linear")
    death.rot("tail3", {0: (0, 0, 0), 1.6: (0, 30, 0), 2.4: (0, 30, 0)}, interp="linear")
    return m


# =============================================================================== HEL

def build_hel():
    ROBE, ROBE_L, ROBE_D = "#171b30", "#2b3253", "#0b0d18"
    TRIM, BONE, BONE_D = "#7f92c8", "#c3cbd4", "#7d8793"
    GLOW, GLOW_HOT = "#78dcff", "#e6faff"
    PALE, PALE_D, LIPS = "#e6d6d0", "#c4aaa8", "#9c3a4a"
    SKULL, HAIR = "#1c2438", "#0c0e19"
    m = Model("bigcasares_myth_hel", texture_size=(128, 128))
    body = m.bone("body", pivot=(0, 18, 0))
    torso = m.bone("torso", body, pivot=(0, 19, 0))
    head = m.bone("head", torso, pivot=(0, 27.5, 0))
    arm_r = m.bone("arm_right", torso, pivot=(6.6, 26.5, 0))
    fore_r = m.bone("forearm_right", arm_r, pivot=(6.6, 18.5, 0))
    staff = m.bone("staff", fore_r, pivot=(6.65, 10.4, 0))
    arm_l = m.bone("arm_left", torso, pivot=(-6.6, 26.5, 0))
    fore_l = m.bone("forearm_left", arm_l, pivot=(-6.6, 18.5, 0))
    orb_b = m.bone("orb", staff, pivot=(6.7, 34.8, 0))
    robe = m.bone("robe", body, pivot=(0, 10, 0))
    cloak = m.bone("cloak", torso, pivot=(0, 27, 5.6))
    mist = m.bone("mist", pivot=(0, 0.3, 0))

    def cloth(c, base=ROBE, trim=TRIM):
        c.paint(base, noise=0.05, jitter=0.16)
        for f in c.sides():
            f.stripe(dk(base, 0.35), every=4, axis="v", offset=1)
        return c

    # --- skirt, flared hem and runes
    skirt = cloth(m.cube(body, "skirt_top", (-5.5, 9, -3.8), (5.5, 19, 3.8)))
    skirt.north.rect(0, 0, 5, 10, ROBE_L, noise=0.05)
    skirt.north.line(5, 0, 5, 9, dk(ROBE, 0.5)).stripe(dk(ROBE_L, 0.3), every=3, axis="v", offset=1, x=0, w=5)
    belt = m.cube(body, "sash", (-5.9, 17.2, -4.2), (5.9, 19.2, 4.2))
    belt.paint("#2a2038", jitter=0.1)
    belt.north.rect(4, 0, 3, 2, BONE).outline(BONE_D, 4, 0, 3, 2).pixel(5, 1, GLOW)
    for f in belt.sides():
        f.stripe(TRIM + "90", every=4, axis="v", offset=0)
    flare = cloth(m.cube(robe, "skirt_flare", (-7.5, 0.4, -5.2), (7.5, 9.4, 5.2)))
    for f in flare.sides():
        f.rect(0, f.h - 4, f.w, 3, ROBE_D).line(0, f.h - 3, f.w - 1, f.h - 3, TRIM).stripe(TRIM + "90", every=4, axis="v", offset=1, y=f.h - 2, h=1)
        f.jagged(ROBE_D, edge="bottom", depth=(0, 1))
    flare.north.rect(0, 0, 8, 4, ROBE_L).stripe(dk(ROBE_L, 0.3), every=3, axis="v", offset=1, h=4).noise(0.06)
    flare.north.decal(RUNES["isa"], {"g": GLOW}, x=3, y=0).decal(RUNES["hagal"], {"g": GLOW}, x=11, y=0)
    for nm, frm, to, rot in (("hem_front", (-7.5, 0.3, -5.9), (7.5, 6.8, -5.9), (-22.5, 0, 0)),
                              ("hem_back", (-7.5, 0.3, 5.9), (7.5, 6.8, 5.9), (22.5, 0, 0))):
        pl = m.plane(robe, nm, frm, to, rotation=rot, pivot=(0, 6.8, frm[2]))
        pf = pl.front
        pf.gradient(ROBE_L, ROBE_D, steps=4).noise(0.06).stripe(ROBE_D, every=3, axis="v", offset=1).tatter(5, chance=0.9)
        pf.line(0, 0, pf.w - 1, 0, TRIM + "b0")
    for nm, sx in (("hem_right", 1), ("hem_left", -1)):
        pl = m.plane(robe, nm, (sx * 7.9, 0.3, -4.5), (sx * 7.9, 6.8, 4.5))
        pl.front.gradient(ROBE_L, ROBE_D, steps=4).noise(0.06).stripe(ROBE_D, every=3, axis="v", offset=1).tatter(5, chance=0.9)

    # --- bodice split down the middle: living half (right, +X) vs dead half (left)
    bod = cloth(m.cube(torso, "bodice", (-5, 18.5, -3), (5, 27.5, 3)))
    n = bod.north  # pixel x=0 is the creature's right side
    n.rect(0, 0, 5, 9, "#3a4468", noise=0.05)
    n.stripe(dk("#3a4468", 0.3), every=3, axis="v", offset=1, x=0, w=5)
    n.rect(5, 0, 5, 9, ROBE_D)
    for yy in (1, 3, 5):  # ribs on the dead side
        n.line(5, yy, 8, yy, BONE_D).line(6, yy - 1, 9, yy - 1, BONE_D) if False else n.line(5, yy, 9, yy, BONE_D).pixel(5, yy, BONE)
    n.line(5, 0, 5, 8, BONE).line(4, 0, 4, 8, TRIM + "c0")
    n.decal(RUNES["ansuz"], {"g": GLOW}, x=1, y=2)
    for s in (bod.east,):
        s.rect(0, 0, s.w, s.h, "#3a4468", noise=0.05)
    bod.west.fill(ROBE_D, noise=0.05).line(2, 0, 2, 8, BONE_D)
    bod.south.fill(ROBE, noise=0.06)
    shawl = m.cube(torso, "shawl", (-6.8, 26, -3.8), (6.8, 28.7, 3.8))
    shawl.paint(ROBE_D, noise=0.08, jitter=0.25)
    for s in shawl.sides():
        s.jagged(ROBE, edge="bottom", depth=(1, 2)).strands(lt(ROBE_D, 0.3), density=0.35, length=(1, 2))
    shawl.up.fill(ROBE_D).noise(0.14).dither(TRIM + "70", density=0.15, pattern="noise")
    shawl.north.rect(0, 0, 14, 1, "#3a4468").rect(7, 0, 7, 1, ROBE)
    shawl.north.pixel(6, 1, BONE).pixel(7, 1, BONE)
    # bone spike pauldron on the dead (left) shoulder
    for k, (px, ln) in enumerate(((-7.2, 3.6), (-6.2, 2.6))):
        sp = m.cube(torso, f"shoulder_spike_{k}", (px - 0.6, 28.4, -0.6 - k), (px + 0.6, 28.4 + ln, 0.6 - k), rotation=(0, 0, 22.5))
        sp.paint(BONE, jitter=0.12)
        for f in sp.sides():
            f.gradient(BONE, BONE_D, steps=3)

    # --- head: split face, hood, hair, bone crown
    skull = m.cube(head, "face", (-4, 27.5, -4), (4, 35.5, 4))
    skull.paint(PALE, noise=0.03, jitter=0.08)
    f = skull.north
    for yy in range(8):  # jagged dividing line: pixel x<4 alive, x>=4 dead
        edge = 4 + (1 if yy in (2, 5) else 0) - (1 if yy in (3, 6) else 0)
        f.rect(edge, yy, 8 - edge, 1, SKULL)
    f.rect(4, 0, 4, 8, SKULL).rect(0, 0, 4, 8, PALE, noise=0.03)
    f.rect(0, 7, 4, 1, PALE_D)
    f.rect(0, 3, 3, 1, "#f4eeee").pixel(1, 3, "#5a8ec8").pixel(2, 2, dk(HAIR, 0.1)).line(0, 2, 2, 2, HAIR)  # living eye + lash
    f.pixel(3, 4, PALE_D).pixel(3, 5, PALE_D)
    f.rect(0, 6, 3, 1, LIPS).pixel(3, 6, dk(LIPS, 0.3))
    f.rect(0, 5, 1, 1, "#e8b4b0" + "a0")
    f.rect(5, 2, 3, 2, "#05070d").rect(5, 3, 2, 1, GLOW).pixel(6, 3, GLOW_HOT)  # skull socket
    f.pixel(4, 5, "#05070d").pixel(5, 5, "#05070d").line(5, 4, 5, 4, "#05070d")
    for xx in (4, 5, 6, 7):
        f.pixel(xx, 6, BONE if xx % 2 == 0 else dk(BONE, 0.5))
    f.line(7, 0, 6, 2, BONE_D).pixel(7, 4, BONE_D)  # cracks
    skull.east.fill(PALE, noise=0.03)
    skull.west.fill(SKULL, noise=0.06).line(2, 1, 4, 4, BONE_D).line(4, 4, 3, 7, BONE_D).rect(5, 3, 2, 2, "#05070d")
    skull.up.fill(PALE)
    skull.south.fill(SKULL)
    hood = skull.overlay(0.6, "hood")
    hood.up.fill(ROBE, noise=0.06).stripe(dk(ROBE, 0.4), every=3, axis="v", offset=1)
    for s in (hood.east, hood.west, hood.south):
        s.fill(ROBE, noise=0.07).stripe(dk(ROBE, 0.4), every=3, axis="v", offset=1)
        s.rect(0, 0, s.w, 1, ROBE_L).jagged(ROBE_D, edge="bottom", depth=(0, 2))
    hood.east.rect(0, 0, 3, 8, ROBE_D)  # inner shadow towards the face
    hood.west.rect(0, 0, 3, 8, ROBE_D)
    hood.north.rect(0, 0, 8, 2, ROBE, noise=0.06).rect(0, 0, 8, 1, ROBE_L).rect(0, 0, 1, 8, ROBE).rect(7, 0, 1, 8, ROBE)
    hood.north.rect(0, 0, 1, 8, ROBE_D).rect(7, 0, 1, 8, ROBE_D).line(0, 2, 1, 2, ROBE_D)
    hood.north.jagged(TRIM + "c0", edge="top", depth=(0, 0))
    hood_back = m.cube(head, "hood_back", (-4.8, 21, 3), (4.8, 35, 5.6))
    hood_back.paint(ROBE, noise=0.06, jitter=0.15)
    hood_back.south.stripe(dk(ROBE, 0.4), every=3, axis="v", offset=1).jagged(ROBE_D, edge="bottom", depth=(0, 2))
    hair = m.plane(head, "hair_back", (-4.4, 14, 5.75), (4.4, 31, 5.75))
    hf = hair.front
    hf.gradient("#1b2036", HAIR, steps=3).noise(0.1).strands(lt(HAIR, 0.5), density=0.35, length=(3, 7))
    hf.tatter(6, chance=0.9)
    lock = m.plane(head, "hair_lock", (3, 21, -4.75), (4.9, 33, -4.75))
    lf = lock.front
    lf.fill(HAIR).noise(0.1).strands(lt(HAIR, 0.6), density=0.5, length=(3, 6))
    lf.tatter(4, chance=0.9).clear(0, 0, 1, 4)
    band = m.cube(head, "crown_band", (-4.7, 35.6, -4.7), (4.7, 37, 4.7))
    band.paint(BONE, jitter=0.1)
    band.up.fill(ROBE_D).outline(BONE).noise(0.08)
    for f in band.sides():
        f.stripe(BONE_D, every=3, axis="v", offset=1).pixel(f.w // 2, 0, GLOW)
    for nm, frm, to in (("crown_front", (-4.5, 36.9, -4.75), (4.5, 40, -4.75)), ("crown_back", (-4.5, 36.9, 4.75), (4.5, 40, 4.75)),
                         ("crown_right", (4.75, 36.9, -4.5), (4.75, 40, 4.5)), ("crown_left", (-4.75, 36.9, -4.5), (-4.75, 40, 4.5))):
        cp = m.plane(head, nm, frm, to)
        cf = cp.front
        w, h = cf.w, cf.h
        for x in range(w):
            d = abs(x - (w - 1) / 2)
            hh = 1 if x % 2 else max(2, h - int(d / 2.2))
            cf.rect(x, h - hh, 1, hh, BONE)
            cf.pixel(x, h - hh, GLOW_HOT if x % 4 == 0 else lt(BONE, 0.4))
        cf.rect(0, h - 1, w, 1, BONE_D).noise(0.06)

    # --- arms: living right arm (pale hand, staff), skeletal left arm; both in wide ragged sleeves
    up_r = cloth(m.cube(arm_r, "upper_arm_right", (5, 18.5, -2), (8.2, 27, 2)), "#3a4468")
    low_r = cloth(m.cube(fore_r, "lower_arm_right", (5.1, 11.5, -1.8), (8.1, 18.5, 1.8)), "#3a4468")
    slv_r = low_r.overlay(0.55, "sleeve_right")
    for s in slv_r.sides():
        s.rect(0, 3, s.w, 5, "#3a4468", noise=0.06).stripe(dk("#3a4468", 0.3), every=3, axis="v", offset=1, y=3, h=5)
        s.rect(0, 3, s.w, 1, TRIM).jagged("#3a4468", edge="bottom", depth=(0, 2), x=0)
    hand_r = m.cube(fore_r, "hand_right", (5.4, 9.2, -1.5), (7.9, 11.5, 1.5))
    hand_r.paint(PALE, jitter=0.06)
    hand_r.north.stripe(PALE_D, every=1, axis="v", offset=1, y=0, h=2)
    up_l = cloth(m.cube(arm_l, "upper_arm_left", (-8.2, 18.5, -2), (-5, 27, 2)), ROBE)
    low_l = m.cube(fore_l, "lower_arm_left", (-7.6, 11.5, -1.2), (-5.6, 18.5, 1.2))
    low_l.paint(BONE, jitter=0.1)
    for s in low_l.sides():
        s.stripe(BONE_D, every=3, axis="h", offset=1).pixel(0, 0, BONE_D)
    low_l.north.rect(0, 2, 2, 1, "#05070d").rect(0, 5, 2, 1, "#05070d")
    slv_l = m.cube(fore_l, "sleeve_left", (-8.4, 14.5, -2.4), (-5, 18.9, 2.4))
    slv_l.paint(ROBE, noise=0.06)
    for s in slv_l.sides():
        s.jagged(ROBE_D, edge="bottom", depth=(1, 2)).stripe(dk(ROBE, 0.4), every=3, axis="v", offset=1)
    hand_l = m.cube(fore_l, "hand_left", (-7.9, 9.2, -1.4), (-5.5, 11.5, 1.4))
    hand_l.paint(BONE, jitter=0.1)
    hand_l.north.stripe("#05070d", every=2, axis="v", offset=1, y=0, h=2)
    hand_l.down.fill(GLOW + "c0")

    # --- staff with a cold orb
    shaft = m.cube(staff, "staff_shaft", (6, 0.6, -0.7), (7.3, 33, 0.6))
    shaft.paint("#2a2430", jitter=0.15)
    for s in shaft.sides():
        s.stripe(BONE_D, every=6, axis="h", offset=2).spots(TRIM, n=2)
    shaft.north.decal(RUNES["isa"][:3], {"g": GLOW}, x=0, y=8) if False else shaft.north.pixel(0, 10, GLOW).pixel(0, 18, GLOW).pixel(0, 26, GLOW)
    grip = m.cube(staff, "staff_grip", (5.8, 8.8, -0.9), (7.5, 12.6, 0.8))
    grip.paint("#3a2d36", jitter=0.12)
    grip.north.stripe(dk("#3a2d36", 0.4), every=2, axis="h", offset=0)
    orb = m.cube(orb_b, "staff_orb", (5.3, 33.2, -1.7), (8.1, 36.4, 1.1))
    orb.paint(GLOW, noise=0.04, jitter=0.1, shade=False)
    for f in orb.faces():
        f.rect(1, 1, max(1, f.w - 2), max(1, f.h - 2), lt(GLOW, 0.5))
    for nm, frm, to in (("staff_claw_a", (6.65, 31.5, -3), (6.65, 38.5, 2.5)), ):
        cl = m.plane(staff, nm, frm, to)
        cp = cl.front  # east face: x=0 at +Z
        w, h = cp.w, cp.h
        for y in range(h):
            for x in (0, w - 1):
                cp.pixel(x, y, BONE if y > 1 else lt(BONE, 0.3))
        cp.rect(0, h - 1, w, 1, BONE_D).rect(0, 0, 1, 2, lt(BONE, 0.4)).rect(w - 1, 0, 1, 2, lt(BONE, 0.4))
        cp.line(1, h - 1, w - 2, h - 1, BONE_D)
    cl2 = m.plane(staff, "staff_claw_b", (3.9, 31.5, -0.3), (9.4, 38.5, -0.3))
    c2 = cl2.front
    for y in range(c2.h):
        for x in (0, c2.w - 1):
            c2.pixel(x, y, BONE if y > 1 else lt(BONE, 0.3))
    c2.rect(0, c2.h - 1, c2.w, 1, BONE_D)
    halo = m.plane(orb_b, "staff_glow", (2.4, 31, -0.1), (10.9, 39.5, -0.1))
    hp = halo.front
    for y in range(hp.h):
        for x in range(hp.w):
            r = ((x + 0.5 - hp.w / 2) ** 2 + (y + 0.5 - hp.h / 2) ** 2) ** 0.5 / (hp.w / 2)
            if r < 1.0:
                a = int(max(0, 150 * (1 - r) ** 1.4))
                a = (a // 30) * 30
                if a > 0:
                    hp.pixel(x, y, GLOW + f"{a:02x}")

    # --- tattered cloak and ground mist
    back = m.plane(cloak, "cloak_back", (-8, 1.5, 5.6), (8, 28, 5.6))
    cf = back.front
    cf.gradient(ROBE_L, ROBE_D, steps=5).noise(0.07).stripe(dk(ROBE_D, 0.2), every=4, axis="v", offset=1)
    cf.rect(0, 0, cf.w, 3, ROBE_D).stripe(TRIM + "80", every=6, axis="v", offset=2, y=3, h=1)
    cf.line(0, cf.h - 9, cf.w - 1, cf.h - 9, TRIM + "a0")
    cf.decal(RUNES["hagal"], {"g": GLOW + "c0"}, x=cf.w // 2 - 1, y=8)
    cf.tatter(8, chance=0.95).tatter(3, edge="left", chance=0.4).tatter(3, edge="right", chance=0.4)
    fog = m.plane(mist, "mist_ring", (-11, 0.3, -11), (11, 0.3, 11))
    mf = fog.front
    for y in range(mf.h):
        for x in range(mf.w):
            r = ((x + 0.5 - mf.w / 2) ** 2 + (y + 0.5 - mf.h / 2) ** 2) ** 0.5 / (mf.w / 2)
            if 0.45 < r < 1.0 and mf.rng.random() < 0.9 - 0.7 * abs(r - 0.7) * 2:
                a = 100 if mf.rng.random() < 0.5 else 55
                mf.pixel(x, y, "#9fd8f0" + f"{a:02x}")

    # --- animations
    idle = m.animation("idle", 3.2, "loop")
    idle.pos("body", {0: (0, 0.8, 0), 1.6: (0, 2.0, 0), 3.2: (0, 0.8, 0)})
    idle.rot("torso", {0: (0, 0, 0), 1.6: (-2, 0, 0), 3.2: (0, 0, 0)})
    idle.rot("head", {0: (0, 5, 0), 1.6: (-3, -5, 2), 3.2: (0, 5, 0)})
    idle.rot("robe", {0: (0, 0, 2), 1.6: (0, 0, -2), 3.2: (0, 0, 2)})
    idle.rot("cloak", {0: (4, 0, 2), 1.6: (8, 0, -2), 3.2: (4, 0, 2)})
    idle.rot("arm_right", {0: (-4, 0, 3), 1.6: (-6, 0, 4), 3.2: (-4, 0, 3)})
    idle.rot("forearm_right", {0: (-14, 0, 0), 1.6: (-16, 0, 0), 3.2: (-14, 0, 0)})
    idle.rot("staff", {0: (18, 0, 0), 1.6: (20, 0, 0), 3.2: (18, 0, 0)})
    idle.rot("arm_left", {0: (-6, 0, -4), 0.8: (-10, 0, -6), 1.6: (-6, 0, -4), 2.4: (-8, 0, -8), 3.2: (-6, 0, -4)})
    idle.rot("forearm_left", {0: (-22, 0, 0), 1.6: (-34, 0, 0), 3.2: (-22, 0, 0)})
    idle.scale("orb", {0: (1, 1, 1), 1.6: (1.12, 1.12, 1.12), 3.2: (1, 1, 1)})
    idle.scale("mist", {0: (1, 1, 1), 1.6: (1.08, 1, 1.08), 3.2: (1, 1, 1)})

    walk = m.animation("walk", 1.6, "loop")
    L = walk.length
    walk.pos("body", {0: (0, 1.4, 0), L / 4: (0, 2.2, 0), L / 2: (0, 1.4, 0), 3 * L / 4: (0, 2.2, 0), L: (0, 1.4, 0)})
    walk.rot("body", {0: (7, 0, 2), L / 2: (7, 0, -2), L: (7, 0, 2)})
    walk.rot("torso", {0: (2, 3, 0), L / 2: (2, -3, 0), L: (2, 3, 0)})
    walk.rot("head", {0: (-4, -3, 0), L / 2: (-4, 3, 0), L: (-4, -3, 0)})
    walk.rot("robe", {0: (10, 0, 3), L / 4: (16, 0, 0), L / 2: (10, 0, -3), 3 * L / 4: (16, 0, 0), L: (10, 0, 3)})
    walk.rot("cloak", {0: (16, 0, 3), L / 4: (24, 0, 0), L / 2: (16, 0, -3), 3 * L / 4: (24, 0, 0), L: (16, 0, 3)})
    walk.rot("arm_right", {0: (-8, 0, 3), L / 2: (-14, 0, 3), L: (-8, 0, 3)})
    walk.rot("forearm_right", {0: (-16, 0, 0), L / 2: (-22, 0, 0), L: (-16, 0, 0)})
    walk.rot("staff", {0: (22, 0, 0), L / 2: (28, 0, 0), L: (22, 0, 0)})
    walk.rot("arm_left", {0: (8, 0, -5), L / 2: (-12, 0, -8), L: (8, 0, -5)})
    walk.rot("forearm_left", {0: (-16, 0, 0), L / 2: (-30, 0, 0), L: (-16, 0, 0)})
    walk.scale("orb", {0: (1, 1, 1), L / 2: (1.15, 1.15, 1.15), L: (1, 1, 1)})

    attack = m.animation("attack", 1.3, "once")
    attack.rot("arm_right", {0: (0, 0, 0), 0.5: (-125, 0, 6), 0.7: (-88, 0, 0), 0.95: (-80, 0, 0), 1.3: (0, 0, 0)}, interp="linear")
    attack.rot("forearm_right", {0: (0, 0, 0), 0.5: (-14, 0, 0), 0.7: (-6, 0, 0), 1.3: (0, 0, 0)}, interp="linear")
    attack.rot("staff", {0: (0, 0, 0), 0.5: (136, 0, 0), 0.7: (150, 0, 0), 0.95: (130, 0, 0), 1.3: (0, 0, 0)}, interp="linear")
    attack.rot("arm_left", {0: (0, 0, 0), 0.5: (-120, 0, -12), 0.7: (-92, 0, -6), 0.95: (-85, 0, -8), 1.3: (0, 0, 0)}, interp="linear")
    attack.rot("forearm_left", {0: (0, 0, 0), 0.5: (-10, 0, 0), 0.7: (-4, 0, 0), 1.3: (0, 0, 0)}, interp="linear")
    attack.rot("torso", {0: (0, 0, 0), 0.5: (-14, 0, 0), 0.7: (16, 0, 0), 1.3: (0, 0, 0)}, interp="linear")
    attack.rot("head", {0: (0, 0, 0), 0.5: (-16, 0, 0), 0.7: (10, 0, 0), 1.3: (0, 0, 0)}, interp="linear")
    attack.pos("body", {0: (0, 0, 0), 0.5: (0, 3, 1.5), 0.7: (0, 0.5, -4), 1.3: (0, 0, 0)}, interp="linear")
    attack.rot("cloak", {0: (0, 0, 0), 0.5: (-10, 0, 0), 0.7: (28, 0, 0), 1.3: (0, 0, 0)}, interp="linear")
    attack.rot("robe", {0: (0, 0, 0), 0.7: (14, 0, 0), 1.3: (0, 0, 0)}, interp="linear")
    attack.scale("orb", {0: (1, 1, 1), 0.5: (1.3, 1.3, 1.3), 0.7: (1.9, 1.9, 1.9), 1.3: (1, 1, 1)}, interp="linear")

    death = m.animation("death", 2.4, "once")
    death_fall(death, direction="back")
    death.rot("arm_right", {0: (0, 0, 0), 0.6: (-30, 0, 20), 1.5: (-10, 0, 62), 2.4: (-10, 0, 62)}, interp="linear")
    death.rot("arm_left", {0: (0, 0, 0), 0.6: (-50, 0, -20), 1.5: (-20, 0, -60), 2.4: (-20, 0, -60)}, interp="linear")
    death.rot("staff", {0: (0, 0, 0), 0.8: (-20, 0, 12), 1.5: (-8, 0, 16), 2.4: (-8, 0, 16)}, interp="linear")
    death.rot("head", {0: (0, 0, 0), 0.5: (-20, 0, 0), 1.5: (12, 22, 0), 2.4: (12, 22, 0)}, interp="linear")
    death.rot("cloak", {0: (0, 0, 0), 0.8: (20, 0, 0), 2.4: (6, 0, 0)}, interp="linear")
    death.scale("orb", {0: (1, 1, 1), 1.0: (1.5, 1.5, 1.5), 2.4: (0.4, 0.4, 0.4)}, interp="linear")
    death.scale("mist", {0: (1, 1, 1), 0.7: (1.4, 1, 1.4), 1.0: (0.01, 0.01, 0.01), 2.4: (0.01, 0.01, 0.01)}, interp="linear")
    return m


MODELS = {
    "bigcasares_myth_draugr": build_draugr,
    "bigcasares_myth_dwarf": build_dwarf,
    "bigcasares_myth_nidhogg": build_nidhogg,
    "bigcasares_myth_hel": build_hel,
}
