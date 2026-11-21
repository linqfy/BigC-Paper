"""Japanese mythology creatures for the mythic dungeons (oni, cave yokai, Yomotsu-shikome).

Cuboids of varied sizes with painted relief: overlay layers (hair cap, bracers) and alpha
planes (hair, cloth, pelts).  Model faces north (-Z); the creature's right is +X.
"""
import sys
from pathlib import Path

import numpy as np

sys.path.insert(0, str(Path(__file__).resolve().parents[1]))
from bbkit import (Model, attack_swing, death_fall, idle_breathe, mix, ramp,  # noqa: E402
                   walk_biped)

BLACK = "#14101a"
BONE = "#e8dfc6"


def speckle(face, color, p=0.2):
    """Recolour a random share of the already painted pixels (keeps transparency)."""
    ys, xs = np.nonzero(face.mask())
    for y, x in zip(ys, xs):
        if face.rng.random() < p:
            face.pixel(int(x), int(y), color)
    return face


def skin(cube, base, dark, density=0.12):
    """Shaded skin with sparse darker creases on the side faces."""
    cube.paint(base, noise=0.05, jitter=0.2)
    for f in cube.sides():
        f.strands(dark, density=density, length=(1, 3))
    return cube


def tiger(face, base, dark, step=(3, 4)):
    """Tiger-pelt stripes: tapering wobbling black bars from the top or bottom edge."""
    face.shaded(base, noise=0.04, jitter=0.12, shade=False)
    x = 0
    while x < face.w:
        ln = face.rng.randint(max(2, face.h // 3), max(3, face.h * 2 // 3))
        top = face.rng.random() < 0.5
        wob = 0
        for k in range(ln):
            yy = k if top else face.h - 1 - k
            face.pixel(x + wob, yy, dark)
            if k < ln * 2 // 3:
                face.pixel(x + wob + 1, yy, dark)
            if face.rng.random() < 0.3:
                wob += face.rng.choice((-1, 1))
        x += face.rng.randint(*step)
    return face


def hair_plane(face, base, hi, spikes=True, top_h=0.35):
    """Black hair silhouette: spiky top (or ragged), ragged bottom, glossy highlight streaks."""
    w, h = face.w, face.h
    for x in range(w):
        t = face.rng.random()
        top = int(h * top_h * t) if spikes else face.rng.randint(0, 2)
        bot = h - face.rng.randint(0, max(1, h // 5))
        face.rect(x, top, 1, bot - top, base)
    face.noise(0.15)
    face.strands(hi, density=0.22, length=(3, 6))
    face.strands(mix(base, "#000", 0.5), density=0.2, length=(3, 7))
    return face


# =====================================================================================
# ONI - elite ogre demon with a studded iron kanabo
# =====================================================================================
ONI = "#b3302a"
ONI_D = "#6e1a1e"
ONI_L = "#d85a43"
TIGER_Y = "#e3b02a"
TIGER_K = "#1a1210"
IRON = "#6b7078"
IRON_D = "#33363c"
IRON_L = "#a9b0b8"
HAIR = "#15121a"
HAIR_HI = "#3b3a52"
ROPE = "#7b5a35"
IVORY = "#ece0c0"
GOLD = "#e8c040"


def build_oni():
    m = Model("bigcasares_myth_oni", texture_size=(256, 128))

    body = m.bone("body", pivot=(0, 16, 0))
    torso = m.bone("torso", body, pivot=(0, 17, 0))
    head = m.bone("head", torso, pivot=(0, 32, -1))
    jaw = m.bone("jaw", head, pivot=(0, 33, -3))
    arm_r = m.bone("arm_right", torso, pivot=(12, 30, 0))
    fore_r = m.bone("forearm_right", arm_r, pivot=(12, 21, 0))
    club = m.bone("club", fore_r, pivot=(12, 11, 0), rotation=(-14, 0, 0))
    arm_l = m.bone("arm_left", torso, pivot=(-12, 30, 0))
    fore_l = m.bone("forearm_left", arm_l, pivot=(-12, 21, 0))
    leg_r = m.bone("leg_right", body, pivot=(4.5, 16, 0))
    shin_r = m.bone("shin_right", leg_r, pivot=(4.5, 8, 0))
    leg_l = m.bone("leg_left", body, pivot=(-4.5, 16, 0))
    shin_l = m.bone("shin_left", leg_l, pivot=(-4.5, 8, 0))
    loin_f = m.bone("loin_front", body, pivot=(0, 15, -4.8))
    loin_b = m.bone("loin_back", body, pivot=(0, 15, 4.8))

    # --- pelvis, tiger-skin loincloth, rope belt
    pelvis = skin(m.cube(body, "pelvis", (-6.5, 13, -4), (6.5, 17.5, 4)), ONI, ONI_D)
    wrap = m.cube(body, "loincloth_wrap", (-7, 11.5, -4.6), (7, 16.2, 4.6))
    for f in wrap.faces():
        tiger(f, TIGER_Y, TIGER_K)
    wrap.down.fill(mix(TIGER_Y, "#000", 0.4))
    belt = m.cube(body, "belt", (-7.3, 15.8, -4.9), (7.3, 18, 4.9))
    belt.paint(ROPE, jitter=0.15)
    for f in belt.sides():
        f.stripe(mix(ROPE, "#000", 0.4), every=2, axis="v", offset=1)
        f.stripe(mix(ROPE, "#fff", 0.25), every=4, axis="h", offset=0)
    belt.north.rect(6, 0, 3, 2, mix(ROPE, "#000", 0.4)).pixel(7, 0, mix(ROPE, "#fff", 0.3))
    for name, bone, z, x0, x1, y0 in (("loin_flap_front", loin_f, -4.9, -4.5, 4.5, 4.0),
                                       ("loin_flap_back", loin_b, 4.9, -5, 5, 5.0)):
        flap = m.plane(bone, name, (x0, y0, z), (x1, 15.8, z))
        fc = flap.front
        tiger(fc, TIGER_Y, TIGER_K)
        fc.rect(0, 0, fc.w, 1, mix(TIGER_Y, "#000", 0.35))
        # pointed pelt hem
        for x in range(fc.w):
            cut = abs((x % 4) - 2)
            fc.clear(x, fc.h - 1 - cut + 1 if cut < 2 else fc.h - 1, 1, 3)
        fc.tatter(2, edge="bottom", chance=0.4)

    # --- legs
    thigh = skin(m.cube(leg_r, "thigh_right", (1.5, 8, -3.5), (7.5, 16.5, 3.5)), ONI, ONI_D)
    thigh.north.rect(0, 0, 1, 8, ONI_D)
    shin = skin(m.cube(shin_r, "lower_leg_right", (2, 2.4, -3), (7, 8.6, 3)), ONI, ONI_D)
    shin.north.rect(2, 1, 1, 4, ONI_D).rect(3, 1, 1, 4, ONI_L)
    foot = m.cube(shin_r, "foot_right", (1.5, 0, -5.5), (7.5, 2.8, 3))
    foot.paint(ONI, jitter=0.18)
    foot.north.stripe(IVORY, every=2, axis="v", y=0, h=2, offset=0)
    foot.north.rect(0, 0, foot.north.w, 1, mix(ONI, "#000", 0.35))
    for xx in (0, 2, 4):
        foot.north.pixel(xx, 2, "#2a1814")
    thigh.mirror_x(leg_l, "thigh_left")
    shin.mirror_x(shin_l, "lower_leg_left")
    foot.mirror_x(shin_l, "foot_left")

    # --- torso
    belly = skin(m.cube(torso, "belly", (-6.5, 17, -4), (6.5, 24.5, 4)), ONI, ONI_D)
    bn = belly.north
    bn.rect(6, 0, 1, 7, ONI_D)  # linea alba
    for yy in (2, 4, 6):
        bn.line(2, yy, 5, yy, ONI_D)
        bn.line(8, yy, 11, yy, ONI_D)
    bn.pixel(6, 5, "#2a0c0e")
    bn.rect(2, 1, 4, 1, ONI_L).rect(8, 1, 4, 1, ONI_L)
    chest = skin(m.cube(torso, "chest", (-9.5, 23.5, -5.5), (9.5, 32.5, 5)), ONI, ONI_D)
    cn = chest.north
    cn.rect(1, 1, 7, 4, ONI_L, noise=0.06).rect(10, 1, 7, 4, ONI_L, noise=0.06)  # pecs
    cn.line(1, 5, 8, 5, ONI_D).line(10, 5, 17, 5, ONI_D).rect(9, 1, 1, 5, ONI_D)
    cn.line(12, 2, 15, 6, "#e69a80")  # old scar
    cn.pixel(3, 4, "#4a1014").pixel(14, 4, "#4a1014")
    for f in (chest.east, chest.west, chest.south):
        f.stripe(ONI_D, every=4, axis="v", offset=1, y=2, h=4)
    chest.up.rect(0, 0, chest.up.w, 1, ONI_D)
    trap = m.cube(torso, "neck_muscle", (-4.5, 31.5, -3), (4.5, 34, 3.2))
    skin(trap, ONI, ONI_D)
    # beads (juzu) over the chest
    beads = m.plane(torso, "bead_string", (-5.5, 19, -5.55), (5.5, 31.5, -5.55))
    bf = beads.front
    for k in range(11):
        t = k / 10
        xx = int(round(t * (bf.w - 1)))
        yy = int(round(4 * (t - 0.5) ** 2 * (bf.h - 4)))
        bf.rect(xx, yy, 1, 1, "#e8dcb0")
        if k % 5 == 2:
            bf.rect(xx, yy, 1, 1, "#b8322a")

    # --- head
    skull = skin(m.cube(head, "skull", (-5.5, 32, -8), (5.5, 41, 2)), ONI, ONI_D)
    sn = skull.north
    sn.eyes(5.5, 4, w=2, h=2, gap=5, white="#ffe34a", pupil=BLACK, look="center", socket="#3a0c10")
    sn.line(0, 3, 2, 4, ONI_D).line(10, 3, 8, 4, ONI_D)  # frown
    sn.rect(3, 8, 1, 1, IVORY).rect(7, 8, 1, 1, IVORY)  # upper fangs
    for f in (skull.east, skull.west):
        f.rect(0, 0, f.w, 2, ONI_D)
    brow = m.cube(head, "brow", (-6, 37.8, -8.9), (6, 39.8, -5.5))
    skin(brow, mix(ONI, "#000", 0.2), ONI_D)
    brow.north.line(0, 0, 5, 1, BLACK).line(11, 0, 6, 1, BLACK)
    brow.north.rect(0, 0, 1, 1, ONI_D)
    nose = m.cube(head, "nose", (-1.5, 33.4, -10), (1.5, 36.4, -8))
    nose.paint(ONI_L, jitter=0.15)
    nose.north.rect(0, 2, 1, 1, "#3a0c10").rect(2, 2, 1, 1, "#3a0c10")
    nose.down.fill(mix(ONI, "#000", 0.4))
    jw = skin(m.cube(jaw, "lower_jaw", (-4.6, 29.6, -8.8), (4.6, 33.4, -1.4)), ONI, ONI_D)
    jw.up.fill("#5a1018", noise=0.05).rect(2, 1, 5, 6, "#e0707c")
    jw.up.stripe(IVORY, every=2, axis="v", offset=0, y=0, h=1)
    jw.north.rect(0, 0, jw.north.w, 1, "#5a1018")
    for xx in range(1, jw.north.w, 2):
        jw.north.pixel(xx, 0, IVORY)
    jw.north.rect(3, 2, 3, 1, ONI_D)  # chin crease
    for side, x0 in (("right", 3.9), ("left", -5.5)):
        tusk = m.cube(jaw, f"tusk_{side}", (x0, 32.6, -8.6), (x0 + 1.6, 35.4, -7.0))
        tusk.paint(IVORY, jitter=0.12)
        for f in tusk.sides():
            f.gradient(IVORY, mix(IVORY, "#8a7a58", 0.5), axis="v", steps=3, y=0)
            f.rect(0, f.h - 2, f.w, 2, mix(IVORY, "#000", 0.2))
    # horns
    for side, sx, rot in (("right", 1, -22.5), ("left", -1, 22.5)):
        x0 = 2.2 if sx > 0 else -4.2
        base = m.cube(head, f"horn_base_{side}", (x0, 40.6, -6.4), (x0 + 2.0, 42.8, -4.4))
        base.paint(IVORY, jitter=0.15)
        for f in base.sides():
            f.stripe(mix(IVORY, "#7a6a48", 0.5), every=2, axis="h", offset=1)
        tip = m.cube(head, f"horn_tip_{side}", (x0 + 0.2, 42.4, -6.2), (x0 + 1.8, 47.6, -4.6), rotation=(0, 0, rot),
                     pivot=(x0 + 1.0, 42.4, -5.4))
        tip.paint(IVORY, jitter=0.12)
        for f in tip.sides():
            f.gradient(IVORY, "#4a3a28", steps=4, y=0).stripe(mix(IVORY, "#000", 0.3), every=3, axis="h", offset=1, y=0, h=3)
        tip.up.fill("#3a2a1c")
    for side, sx in (("right", 1), ("left", -1)):
        ear = m.cube(head, f"ear_{side}", (5.5 if sx > 0 else -7.5, 36.5, -4), (7.5 if sx > 0 else -5.5, 39.5, -1.5),
                     rotation=(0, 0, -22.5 * sx))
        skin(ear, ONI, ONI_D)
        ear.north.rect(0, 0, 2, 1, ONI_D)
    # hair: overlay cap plus fanned planes
    hair = skull.overlay(0.5, "hair_cap")
    hair.up.fill(HAIR, noise=0.12).dither(HAIR_HI, density=0.25, pattern="noise")
    for f in (hair.east, hair.west, hair.south):
        f.fill(HAIR, noise=0.12)
        f.strands(HAIR_HI, density=0.3, length=(3, 7))
        f.tatter(3, edge="bottom", chance=0.8)
    hair.north.jagged(HAIR, edge="top", depth=(1, 2))
    back = m.plane(head, "hair_back", (-9, 30, 2.6), (9, 47, 2.6))
    hair_plane(back.front, HAIR, HAIR_HI)
    for side, sx in (("right", 1), ("left", -1)):
        fan = m.plane(head, f"hair_fan_{side}", (sx * 5.6 - 4, 34, -0.5), (sx * 5.6 + 4, 47, -0.5), rotation=(0, 0, -22.5 * sx))
        hair_plane(fan.front, HAIR, HAIR_HI, top_h=0.5)
        fan.front.tatter(3, edge="bottom", chance=0.7)

    # --- arms
    shoulder = skin(m.cube(arm_r, "shoulder_right", (9.5, 26, -4.2), (15.8, 33, 4.2)), ONI, ONI_D)
    shoulder.up.rect(0, 0, shoulder.up.w, 1, ONI_D)
    upper = skin(m.cube(arm_r, "upper_arm_right", (10, 20.5, -3.5), (15, 28, 3.5)), ONI, ONI_D)
    upper.north.rect(1, 1, 3, 4, ONI_L)
    fore = skin(m.cube(fore_r, "lower_arm_right", (9.8, 12.5, -3.2), (15.2, 21.5, 3.2)), ONI, ONI_D)
    wrist = fore.overlay(0.35, "wrist_band_right")
    for f in wrist.sides():
        f.rect(0, f.h - 3, f.w, 3, IRON).rect(0, f.h - 3, f.w, 1, IRON_L).rect(0, f.h - 1, f.w, 1, IRON_D)
        f.pixel(f.w // 2, f.h - 2, "#c8c0a0")
    fist = m.cube(fore_r, "fist_right", (9.8, 8.8, -3.4), (15.2, 12.8, 3.4))
    fist.paint(mix(ONI, "#000", 0.1), jitter=0.2)
    for f in fist.sides():
        f.rect(0, 0, f.w, 1, IVORY)
    fist.north.stripe(ONI_D, every=2, axis="v", y=1, h=3)
    shoulder.mirror_x(arm_l, "shoulder_left")
    upper.mirror_x(arm_l, "upper_arm_left")
    fore.mirror_x(fore_l, "lower_arm_left")
    wrist.mirror_x(fore_l, "wrist_band_left")
    fist.mirror_x(fore_l, "fist_left")

    # --- kanabo: octagonal iron club studded with spikes, leather-wrapped grip
    handle = m.cube(club, "club_handle", (10.7, 4.5, -1.3), (13.3, 27, 1.3))
    handle.paint(ROPE, jitter=0.2)
    for f in handle.sides():
        f.stripe(mix(ROPE, "#000", 0.35), every=3, axis="h", offset=1)
    pommel = m.cube(club, "club_pommel", (10, 2.5, -2), (14, 5, 2))
    pommel.paint(IRON, jitter=0.12).down.fill(IRON_D)
    lower = m.cube(club, "club_neck", (8.9, 26, -3), (14.6, 34, 3))
    head1 = m.cube(club, "club_head", (8.3, 33, -3.6), (15.2, 48, 3.6))
    head2 = m.cube(club, "club_head_rot", (8.3, 33, -3.6), (15.2, 48, 3.6), rotation=(0, 45, 0))
    cap = m.cube(club, "club_cap", (8.9, 48.8, -3), (14.6, 51, 3))
    for c in (lower, head1, head2, cap):
        c.paint(IRON, jitter=0.2, noise=0.07)
    for f in head1.sides() + head2.sides():
        f.stripe(IRON_D, every=15, axis="h", offset=1, y=0)
        f.rect(0, 1, f.w, 1, IRON_L)
        f.cracks(IRON_D, n=2, length=3)
        f.rect(0, f.h - 3, f.w, 1, mix(IRON, "#6a2018", 0.35))  # old blood stain
        speckle(f, mix(IRON, "#7a3a22", 0.45), 0.04)
    for c in (lower, cap):
        for f in c.sides():
            f.rect(0, 0, f.w, 1, IRON_L)
            f.rect(0, f.h - 1, f.w, 1, IRON_D)
    cap.up.rivets(IRON_L, every=3, inset=1)
    for k, yy in enumerate((37.5, 44.5)):
        for s_, (dx, dz) in enumerate(((0, -1), (0, 1), (-1, 0), (1, 0))):
            cx, cz = 11.75 + dx * 3.8, dz * 3.8
            ex = 1.6
            lo = [cx - 0.9, yy - 0.9, cz - 0.9]
            hi = [cx + 0.9, yy + 0.9, cz + 0.9]
            if dx:
                (hi if dx > 0 else lo)[0] = cx + dx * ex
            else:
                (hi if dz > 0 else lo)[2] = cz + dz * ex
            stud = m.cube(club, f"stud_{k}_{s_}", lo, hi)
            stud.paint(IRON_L, jitter=0.1)
            for f in stud.faces():
                f.darken(0.15, y=f.h - 1, h=1)

    # --- animations
    idle = m.animation("idle", 2.6, "loop")
    idle_breathe(idle, body="torso", head="head", arms=("arm_left", "arm_right"), amount=0.45, nod=2)
    idle.rot("jaw", {0: (0, 0, 0), 1.3: (4, 0, 0), 2.6: (0, 0, 0)})
    idle.rot("arm_right", {0: (0, 0, 2), 1.3: (0, 0, 4), 2.6: (0, 0, 2)})
    idle.rot("club", {0: (0, 0, 0), 1.3: (1.5, 0, 0), 2.6: (0, 0, 0)})

    walk = m.animation("walk", 1.2, "loop")
    walk_biped(walk, legs=("leg_left", "leg_right"), shins=("shin_left", "shin_right"),
               arms=("arm_left", "arm_right"), forearms=("forearm_left", "forearm_right"),
               body="body", head="head", swing=24, bob=1.2, sway=4, knee=34)
    L = walk.length
    walk.rot("arm_right", {0: (-6, 0, 3), L / 4: (0, 0, 2), L / 2: (6, 0, 3), 3 * L / 4: (0, 0, 2), L: (-6, 0, 3)})
    walk.rot("forearm_right", {0: (0, 0, 0), L: (0, 0, 0)})
    walk.rot("torso", {0: (7, 0, 0), L / 2: (7, 0, 0), L: (7, 0, 0)})
    walk.rot("jaw", {0: (6, 0, 0), L / 2: (2, 0, 0), L: (6, 0, 0)})
    walk.rot("loin_front", {0: (-5, 0, 0), L / 4: (6, 0, 0), L / 2: (-5, 0, 0), 3 * L / 4: (6, 0, 0), L: (-5, 0, 0)})
    walk.rot("loin_back", {0: (5, 0, 0), L / 4: (-6, 0, 0), L / 2: (5, 0, 0), 3 * L / 4: (-6, 0, 0), L: (5, 0, 0)})

    atk = m.animation("attack", 1.4, "once")
    atk.rot("arm_right", {0: (0, 0, 2), 0.5: (-155, 0, -6), 0.68: (-50, 0, 0), 0.95: (-44, 0, 0), 1.4: (0, 0, 2)},
            interp="linear")
    atk.rot("club", {0: (0, 0, 0), 0.5: (118, 0, 0), 0.68: (150, 0, 0), 0.95: (150, 0, 0), 1.4: (0, 0, 0)}, interp="linear")
    atk.rot("forearm_right", {0: (0, 0, 0), 0.5: (-20, 0, 0), 0.68: (-8, 0, 0), 1.4: (0, 0, 0)}, interp="linear")
    atk.rot("arm_left", {0: (0, 0, 0), 0.5: (-60, 0, -28), 0.68: (10, 0, -10), 1.4: (0, 0, 0)}, interp="linear")
    atk.rot("torso", {0: (0, 0, 0), 0.5: (-14, 0, 0), 0.68: (22, 0, 0), 0.95: (22, 0, 0), 1.4: (0, 0, 0)}, interp="linear")
    atk.rot("head", {0: (0, 0, 0), 0.5: (-10, 0, 0), 0.68: (14, 0, 0), 1.4: (0, 0, 0)}, interp="linear")
    atk.rot("jaw", {0: (0, 0, 0), 0.5: (30, 0, 0), 0.68: (10, 0, 0), 1.4: (0, 0, 0)}, interp="linear")
    atk.pos("body", {0: (0, 0, 0), 0.5: (0, 0.5, 1.5), 0.68: (0, -2, -2.5), 0.95: (0, -1.5, -2), 1.4: (0, 0, 0)}, interp="linear")
    atk.rot("leg_left", {0: (0, 0, 0), 0.68: (-14, 0, 0), 1.4: (0, 0, 0)}, interp="linear")
    atk.rot("leg_right", {0: (0, 0, 0), 0.68: (16, 0, 0), 1.4: (0, 0, 0)}, interp="linear")

    death = m.animation("death", 2.2, "once")
    death_fall(death, direction="back")
    death.rot("arm_right", {0: (0, 0, 2), 0.5: (-30, 0, 20), 1.4: (-100, 0, 40), 2.2: (-100, 0, 40)}, interp="linear")
    death.rot("club", {0: (0, 0, 0), 0.7: (60, 0, 0), 2.2: (60, 0, 0)}, interp="linear")
    death.rot("arm_left", {0: (0, 0, 0), 0.5: (-40, 0, -20), 1.4: (-110, 0, -30), 2.2: (-110, 0, -30)}, interp="linear")
    death.rot("head", {0: (0, 0, 0), 0.4: (-25, 0, 0), 1.5: (10, 25, 0), 2.2: (10, 25, 0)}, interp="linear")
    death.rot("jaw", {0: (0, 0, 0), 0.8: (35, 0, 0), 2.2: (40, 0, 0)}, interp="linear")
    return m


# =====================================================================================
# CAVE YOKAI - tsuchigumo, the earth spider with a pale noh-mask face
# =====================================================================================
EARTH = "#5a4330"
EARTH_D = "#2c2018"
EARTH_L = "#8e6e48"
OCHRE = "#c9a050"
MASK = "#e9e0cc"
MASK_D = "#a89c84"
RED = "#b8281e"
EYE_GOLD = "#ffc41f"


def bands(cube, base, dark, every=4, width=2, axis_long="x"):
    """Spider leg: furry segment banded across its long axis."""
    cube.paint(base, noise=0.05, jitter=0.2)
    for f in cube.faces():
        long_is_w = f.w >= f.h
        f.stripe(dark, every=every, axis="v" if long_is_w else "h", offset=1, width=width)
        f.strands(mix(dark, "#000", 0.3), density=0.2, length=(1, 2))
    return cube


def build_cave_yokai():
    m = Model("bigcasares_myth_cave_yokai", texture_size=(128, 128))

    body = m.bone("body", pivot=(0, 11, 0))
    abdomen = m.bone("abdomen", body, pivot=(0, 12, 5))
    head = m.bone("head", body, pivot=(0, 13, -6))
    jaw = m.bone("jaw", head, pivot=(0, 10, -8))

    # --- cephalothorax and abdomen
    thorax = m.cube(body, "thorax", (-5.2, 7.5, -6), (5.2, 16, 5.5))
    thorax.paint(EARTH, noise=0.06, jitter=0.22)
    for f in thorax.faces():
        f.strands(EARTH_D, density=0.3, length=(1, 3))
        speckle(f, EARTH_L, 0.08)
    thorax.up.line(5, 0, 5, 10, EARTH_D).line(2, 3, 8, 3, EARTH_D).line(2, 7, 8, 7, EARTH_D)
    for xx in (2, 4, 6, 8):
        thorax.up.pixel(xx, 1, RED)  # four small spider eyes on the carapace
    carapace = thorax.overlay(0.3, "carapace")
    carapace.up.rect(1, 1, carapace.up.w - 2, carapace.up.h - 2, EARTH_L, noise=0.08)
    carapace.up.outline(EARTH_D, 1, 1, carapace.up.w - 2, carapace.up.h - 2)
    carapace.up.dither(EARTH_D, density=0.2, pattern="noise")
    ab = m.cube(abdomen, "abdomen_main", (-7, 7, 4.5), (7, 17, 18))
    ab.paint(EARTH_D, noise=0.06, jitter=0.2)
    for f in ab.sides():
        f.strands(mix(EARTH_D, "#000", 0.4), density=0.25, length=(1, 3))
        for y in range(2, f.h, 4):
            f.rect(0, y, f.w, 1, OCHRE)
            f.rect(0, y + 1, f.w, 1, mix(OCHRE, "#000", 0.35))
    ab.up.fill(EARTH, noise=0.06)
    for y in range(1, ab.up.h, 4):  # chevrons across the back
        cx = ab.up.w // 2
        for k in range(cx):
            ab.up.pixel(cx - k, y + k // 2, OCHRE).pixel(cx + k - 1, y + k // 2, OCHRE)
            ab.up.pixel(cx - k, y + k // 2 + 1, EARTH_D).pixel(cx + k - 1, y + k // 2 + 1, EARTH_D)
    ab.south.fill(EARTH_D, noise=0.05)
    ab.south.ellipse(7, 5, 3, 3, EARTH)
    tip = m.cube(abdomen, "abdomen_tip", (-4.5, 8, 18), (4.5, 14, 22))
    tip.paint(EARTH_D, noise=0.06, jitter=0.2)
    tip.up.stripe(OCHRE, every=3, axis="h", offset=1)
    spin = m.cube(abdomen, "spinneret", (-1.5, 9, 22), (1.5, 12, 24))
    spin.paint(EARTH_L, jitter=0.15)
    # soil clumps and a few stones on the back (it digs in caves)
    clump = m.cube(abdomen, "soil_clump", (-3, 17, 7), (2.5, 19, 13))
    clump.paint(EARTH, noise=0.1, jitter=0.3)
    for f in clump.faces():
        speckle(f, mix(EARTH, "#6a6a60", 0.6), 0.2)
    clump.up.dither(mix("#3a6a30", EARTH, 0.4), density=0.3, pattern="noise")

    # --- noh-like oni mask face
    mask = m.cube(head, "mask", (-5, 10, -9), (5, 19.5, -6.2))
    mask.paint(MASK, noise=0.04, jitter=0.1)
    mn = mask.north
    mn.rect(0, 0, mn.w, 2, MASK_D)  # shadowed hairline
    mn.line(1, 3, 3, 3, RED).line(6, 3, 8, 3, RED).pixel(3, 2, RED).pixel(6, 2, RED)
    mn.eyes(5, 4, w=2, h=2, gap=3, white=EYE_GOLD, pupil=BLACK, look="center", socket="#2a0c0c")
    mn.pixel(1, 6, RED).pixel(8, 6, RED).pixel(2, 7, RED).pixel(7, 7, RED)  # cheek marks
    mn.rect(4, 5, 2, 2, MASK_D).pixel(4, 6, "#4a3c34").pixel(5, 6, "#4a3c34")  # nose
    mn.rect(1, 8, 8, 1, RED).rect(1, 9, 8, 1, "#3a0a0c")  # grinning mouth
    for xx in (1, 3, 5, 7):
        mn.pixel(xx, 9, MASK)
    mn.pixel(0, 4, MASK_D).pixel(9, 4, MASK_D)
    for f in mask.sides():
        f.rect(0, 0, f.w, 2, MASK_D)
    mask.down.fill(MASK_D)
    hair = mask.overlay(0.45, "mask_hair")
    hair.up.fill(BLACK, noise=0.1).dither(HAIR_HI, density=0.2, pattern="noise")
    for f in (hair.east, hair.west):
        f.jagged(BLACK, edge="top", depth=(3, 7))
        f.strands(BLACK, density=0.3, length=(2, 5), y=0, h=f.h)
    hair.north.jagged(BLACK, edge="top", depth=(1, 2))
    hair.south.fill(BLACK, noise=0.1)
    for side, sx in (("right", 1), ("left", -1)):
        x0 = 2.6 if sx > 0 else -4.4
        horn = m.cube(head, f"horn_{side}", (x0, 19, -8.2), (x0 + 1.8, 22.6, -6.6), rotation=(0, 0, -22.5 * sx))
        horn.paint(IVORY, jitter=0.12)
        for f in horn.sides():
            f.gradient(IVORY, "#5a4632", steps=3)

    # --- mandibles
    jb = m.cube(jaw, "jaw_plate", (-3.2, 8, -8.8), (3.2, 10.4, -6))
    jb.paint(EARTH_D, jitter=0.15)
    jb.north.rect(0, 0, jb.north.w, 1, "#3a0a0c")
    for side, sx in (("right", 1), ("left", -1)):
        x0 = 1.0 if sx > 0 else -3.0
        fang = m.cube(jaw, f"fang_{side}", (x0, 3.5, -9), (x0 + 2, 8.6, -7), rotation=(0, 0, -22.5 * sx))
        fang.paint(BONE, jitter=0.12)
        for f in fang.sides():
            f.gradient(BONE, "#3a2a1c", steps=4, y=1)
            f.rect(0, 0, f.w, 1, EARTH_D)

    # --- eight striped legs (one bone each; the bent geometry is built from two cubes)
    HIP_Y = 11.0
    legs = [(-4.2, 52), (-1.6, 20), (1.2, -16), (3.8, -50)]  # (hip z, outward yaw toward the front)
    for i, (hz, yaw) in enumerate(legs):
        for side, sx in (("right", 1), ("left", -1)):
            m.bone(f"leg_{side}_{i + 1}", body, pivot=(sx * 5.0, HIP_Y, hz), rotation=(0, yaw * sx, 0))
        for side in ("right",):
            lb = m.bones[f"leg_right_{i + 1}"]
            up = m.cube(lb, f"leg_up_{side}_{i + 1}", (5, HIP_Y - 1.3, hz - 1.3), (13.6, HIP_Y + 1.3, hz + 1.3),
                        rotation=(0, 0, 45), pivot=(5, HIP_Y, hz))
            bands(up, EARTH_D, OCHRE, every=4, width=1)
            kx = 5 + 8.6 * 0.7071
            ky = HIP_Y + 8.6 * 0.7071
            lo = m.cube(lb, f"leg_low_{side}_{i + 1}", (kx - 0.9, ky - 18.0, hz - 0.9), (kx + 0.9, ky + 0.8, hz + 0.9),
                        rotation=(0, 0, 22.5), pivot=(kx, ky, hz))
            bands(lo, EARTH_D, OCHRE, every=4, width=1)
            lo.down.fill("#14100c")
            m_up = up.mirror_x(m.bones[f"leg_left_{i + 1}"], f"leg_up_left_{i + 1}")
            m_lo = lo.mirror_x(m.bones[f"leg_left_{i + 1}"], f"leg_low_left_{i + 1}")

    # --- animations
    idle = m.animation("idle", 2.2, "loop")
    idle_breathe(idle, body="body", head="head", amount=0.3, nod=2)
    idle.rot("abdomen", {0: (0, 0, 0), 1.1: (3, 0, 0), 2.2: (0, 0, 0)})
    idle.rot("jaw", {0: (0, 0, 0), 1.1: (6, 0, 0), 2.2: (0, 0, 0)})
    for i in range(4):
        for side, sx in (("right", 1), ("left", -1)):
            k = 1 if (i + (sx > 0)) % 2 == 0 else -1
            idle.rot(f"leg_{side}_{i + 1}", {0: (0, 0, 0), 0.55: (0, 0, sx * 3 * k), 1.1: (0, 0, 0),
                                              1.65: (0, 0, -sx * 3 * k), 2.2: (0, 0, 0)})

    import math
    walk = m.animation("walk", 0.7, "loop")
    L = walk.length
    A, LIFT = 16, 18
    for i in range(4):
        for side, sx in (("right", 1), ("left", -1)):
            phase = 0.0 if (i % 2 == 0) == (sx > 0) else 0.5
            keys = {}
            for k in range(9):
                t = k / 8
                ang = 2 * math.pi * (t + phase)
                fwd = A * math.cos(ang)
                lift = LIFT * max(0.0, math.sin(ang))
                keys[L * t] = (0, -sx * fwd, sx * lift)
            walk.rot(f"leg_{side}_{i + 1}", keys)
    walk.pos("body", {0: (0, 0, 0), L / 4: (0, 0.5, 0), L / 2: (0, 0, 0), 3 * L / 4: (0, 0.5, 0), L: (0, 0, 0)})
    walk.rot("body", {0: (0, 3, 0), L / 2: (0, -3, 0), L: (0, 3, 0)})
    walk.rot("abdomen", {0: (0, -4, 0), L / 2: (0, 4, 0), L: (0, -4, 0)})
    walk.rot("jaw", {0: (0, 0, 0), L / 2: (8, 0, 0), L: (0, 0, 0)})

    atk = m.animation("attack", 1.0, "once")
    atk.pos("body", {0: (0, 0, 0), 0.3: (0, -1.5, 3), 0.45: (0, 1, -9), 0.65: (0, 0, -6), 1.0: (0, 0, 0)}, interp="linear")
    atk.rot("body", {0: (0, 0, 0), 0.3: (-8, 0, 0), 0.45: (10, 0, 0), 1.0: (0, 0, 0)}, interp="linear")
    atk.rot("head", {0: (0, 0, 0), 0.3: (-14, 0, 0), 0.45: (16, 0, 0), 1.0: (0, 0, 0)}, interp="linear")
    atk.rot("jaw", {0: (0, 0, 0), 0.3: (45, 0, 0), 0.45: (-6, 0, 0), 0.55: (26, 0, 0), 0.65: (-4, 0, 0), 1.0: (0, 0, 0)},
            interp="linear")
    for side, sx in (("right", 1), ("left", -1)):
        for i, lift in ((0, 34), (1, 20)):
            atk.rot(f"leg_{side}_{i + 1}", {0: (0, 0, 0), 0.3: (0, 0, sx * lift), 0.45: (0, -sx * 14, sx * 6),
                                             1.0: (0, 0, 0)}, interp="linear")
    atk.rot("abdomen", {0: (0, 0, 0), 0.3: (8, 0, 0), 0.45: (-6, 0, 0), 1.0: (0, 0, 0)}, interp="linear")

    death = m.animation("death", 1.8, "once")
    death.rot("root", {0: (0, 0, 0), 0.2: (0, 0, 8), 0.7: (0, 0, 120), 1.1: (0, 0, 176), 1.3: (0, 0, 184), 1.8: (0, 0, 180)},
              interp="linear")
    death.pos("root", {0: (0, 0, 0), 0.2: (0, 0, 0), 0.7: (-6, 15, 0), 1.1: (-3, 23.3, 0), 1.8: (0, 22.9, 0)}, interp="linear")
    for i in range(4):
        for side, sx in (("right", 1), ("left", -1)):
            curl = (60 + 8 * i) * (1 if i % 2 == 0 else 0.85)
            death.rot(f"leg_{side}_{i + 1}", {0: (0, 0, 0), 0.8: (0, 0, sx * 0), 1.8: (0, 0, -sx * curl)}, interp="linear")
    death.rot("jaw", {0: (0, 0, 0), 1.0: (30, 0, 0), 1.8: (30, 0, 0)}, interp="linear")
    return m


# =====================================================================================
# YOMOTSU-SHIKOME - hag of Yomi in a stained burial kimono
# =====================================================================================
CORPSE = "#7d8e76"
CORPSE_D = "#46554a"
CORPSE_L = "#a8b79c"
SHROUD = "#e4e0d2"
SHROUD_D = "#a09c8e"
STAIN = "#6a4638"
GLOW = "#cfff72"
NAIL = "#d6cba4"


def cloth(face, base=SHROUD, fold=SHROUD_D, stain=True, hem=0.4):
    """Burial linen: vertical folds, grave stains creeping up from the hem, ragged edge."""
    face.shaded(base, noise=0.05, jitter=0.12, shade=False)
    face.stripe(fold, every=4, axis="v", offset=face.rng.randint(0, 3))
    face.dither(mix(base, fold, 0.5), density=0.12, pattern="noise")
    if stain:
        y0 = int(face.h * (1 - hem))
        for yy in range(y0, face.h):
            p = (yy - y0 + 1) / max(1, face.h - y0)
            for xx in range(face.w):
                if face.rng.random() < p * 0.9:
                    face.pixel(xx, yy, mix(base, STAIN, 0.35 + 0.5 * p))
    return face


def build_shikome():
    m = Model("bigcasares_myth_shikome", texture_size=(128, 128))

    body = m.bone("body", pivot=(0, 14, 0))
    torso = m.bone("torso", body, pivot=(0, 14, 1), rotation=(-16, 0, 0))
    head = m.bone("head", torso, pivot=(0, 25.5, -1))
    jaw = m.bone("jaw", head, pivot=(0, 25, -3))
    arm_r = m.bone("arm_right", torso, pivot=(5, 24, 0))
    fore_r = m.bone("forearm_right", arm_r, pivot=(5, 17, 0))
    arm_l = m.bone("arm_left", torso, pivot=(-5, 24, 0))
    fore_l = m.bone("forearm_left", arm_l, pivot=(-5, 17, 0))
    leg_r = m.bone("leg_right", body, pivot=(2.2, 14, 0))
    shin_r = m.bone("shin_right", leg_r, pivot=(2.2, 7, 0))
    leg_l = m.bone("leg_left", body, pivot=(-2.2, 14, 0))
    shin_l = m.bone("shin_left", leg_l, pivot=(-2.2, 7, 0))
    skirt = m.bone("skirt", body, pivot=(0, 14, 0))

    # --- legs: bare, corpse-grey, bony, long toenails
    thigh = skin(m.cube(leg_r, "thigh_right", (0.6, 7, -1.6), (3.8, 14.2, 1.6)), CORPSE, CORPSE_D)
    shin = skin(m.cube(shin_r, "lower_leg_right", (0.9, 1.6, -1.3), (3.5, 7.4, 1.3)), CORPSE, CORPSE_D)
    shin.north.rect(1, 0, 1, 5, CORPSE_L)
    foot = m.cube(shin_r, "foot_right", (0.5, 0, -4.2), (3.9, 1.8, 1.6))
    foot.paint(CORPSE, jitter=0.2)
    foot.north.rect(0, 0, foot.north.w, 1, NAIL)
    foot.up.stripe(CORPSE_D, every=2, axis="v", y=0, h=3, offset=1)
    thigh.mirror_x(leg_l, "thigh_left")
    shin.mirror_x(shin_l, "lower_leg_left")
    foot.mirror_x(shin_l, "foot_left")

    # --- hips and torso under the shroud
    pelvis = skin(m.cube(body, "pelvis", (-3.6, 11.5, -2.2), (3.6, 15.5, 2.2)), CORPSE, CORPSE_D)
    waist = m.cube(torso, "waist", (-3.4, 14.5, -2), (3.4, 20, 2))
    waist.paint(SHROUD, noise=0.05, jitter=0.12)
    for f in waist.sides():
        f.stripe(SHROUD_D, every=4, axis="v", offset=1)
    chest = m.cube(torso, "chest", (-4.2, 19.5, -2.5), (4.2, 26, 2.5))
    chest.paint(SHROUD, noise=0.05, jitter=0.12)
    for f in chest.sides():
        f.stripe(SHROUD_D, every=4, axis="v", offset=2)
        f.dither(mix(SHROUD, STAIN, 0.4), density=0.05, pattern="noise")
    cn = chest.north
    # funeral wrap: right lapel (viewer's left) crosses OVER the left one, collar edge runs "\"
    for k in range(7):
        cn.pixel(1 + k, 0 + k, SHROUD_D).pixel(2 + k, 0 + k, mix(SHROUD, "#fff", 0.2))
    for k in range(5):
        cn.pixel(7 - k, 0 + k, mix(SHROUD, "#000", 0.2))
    cn.rect(0, 0, 2, 1, CORPSE).rect(6, 0, 2, 1, CORPSE)
    cn.pixel(3, 0, CORPSE_L).pixel(4, 0, CORPSE_L)  # collarbone skin
    cn.dither(STAIN, density=0.06, pattern="noise", y=3)
    chest.up.fill(SHROUD)
    chest.up.rect(2, 1, 4, 3, CORPSE)
    obi = m.cube(torso, "sash", (-4, 14, -2.45), (4, 16.5, 2.45))
    obi.paint("#bcb8a8", jitter=0.1)
    for f in obi.sides():
        f.stripe("#8a8678", every=3, axis="v", offset=0)
    obi.north.rect(2, 0, 3, 3, "#8a8678").pixel(3, 1, "#e4e0d2")
    neck = skin(m.cube(torso, "neck", (-1.4, 25.5, -1.6), (1.4, 28, 1.2)), CORPSE, CORPSE_D)

    # --- head
    skull = m.cube(head, "skull", (-3.5, 25, -4.5), (3.5, 32, 2.5))
    skull.paint(CORPSE, noise=0.05, jitter=0.15)
    sn = skull.north
    sn.rect(0, 0, 7, 1, CORPSE_D)
    sn.rect(1, 2, 2, 2, "#080c0a").rect(4, 2, 2, 2, "#080c0a")  # hollow sockets
    sn.pixel(2, 3, GLOW).pixel(5, 3, GLOW).pixel(2, 2, mix(GLOW, "#000", 0.5)).pixel(5, 2, mix(GLOW, "#000", 0.5))
    sn.line(0, 1, 2, 1, CORPSE_D).line(5, 1, 7, 1, CORPSE_D)
    sn.pixel(3, 4, "#1c2420").pixel(1, 4, CORPSE_D).pixel(5, 4, CORPSE_D).pixel(0, 4, CORPSE_L).pixel(6, 4, CORPSE_L)
    sn.rect(1, 6, 5, 1, "#1a0c0c")
    for xx in (1, 3, 5):
        sn.pixel(xx, 6, BONE)
    for f in (skull.east, skull.west):
        f.rect(0, 0, f.w, 1, CORPSE_D)
    jw = m.cube(jaw, "lower_jaw", (-2.6, 22.6, -4.2), (2.6, 25.2, 1))
    jw.paint(CORPSE, jitter=0.15)
    jw.up.fill("#1a0c0c").rect(1, 0, 3, 1, "#8a2a3a")
    jw.north.rect(0, 0, 5, 1, "#1a0c0c")
    for xx in (0, 2, 4):
        jw.north.pixel(xx, 0, BONE)
    jw.north.pixel(2, 1, CORPSE_D)
    hair = skull.overlay(0.35, "hair_cap")
    hair.up.fill(HAIR, noise=0.12)
    for f in (hair.east, hair.west, hair.south):
        f.fill(HAIR, noise=0.12).strands(HAIR_HI, density=0.25, length=(3, 6)).tatter(2, edge="bottom", chance=0.5)
    hair.north.rect(0, 0, 7, 1, HAIR, noise=0.1)
    hair.north.rect(0, 1, 2, 1, HAIR).rect(5, 1, 2, 1, HAIR).rect(5, 2, 2, 3, HAIR, noise=0.1)
    hair.north.strands(HAIR_HI, density=0.2, length=(1, 3))
    # long tangled hair planes: back, sides, and a forelock over one eye
    hb = m.plane(head, "hair_back", (-5.2, 3.5, 2.95), (5.2, 32.5, 2.95))
    hair_plane(hb.front, HAIR, HAIR_HI, spikes=True, top_h=0.12)
    hb.front.tatter(5, edge="bottom", chance=0.9).tatter(3, edge="left", chance=0.6).tatter(3, edge="right", chance=0.6)
    for side, sx in (("right", 1), ("left", -1)):
        hs = m.plane(head, f"hair_side_{side}", (sx * 3.95, 11, -3.6), (sx * 3.95, 31.5, 2.9),
                     rotation=(0, 0, 22.5 * sx), pivot=(sx * 3.95, 31, 0))
        hair_plane(hs.front, HAIR, HAIR_HI, spikes=False)
        hs.front.tatter(6, edge="bottom", chance=0.9).tatter(2, edge="left", chance=0.6)
    fl = m.plane(head, "forelock", (0.6, 22, -4.95), (3.4, 31.7, -4.95))
    hair_plane(fl.front, HAIR, HAIR_HI, spikes=False)
    fl.front.tatter(4, edge="bottom", chance=0.9)
    fl.front.clear(0, 0, 1, 3)
    band = m.plane(head, "forehead_cloth", (-1.5, 29.4, -4.6), (1.5, 31.6, -4.6))
    band.front.decal(["wwwww", ".www.", "..w.."], {"w": SHROUD}, x=0, y=0)
    band.front.rect(0, 0, band.front.w, 1, SHROUD_D)

    # --- arms: long, gaunt, clawed
    upper = skin(m.cube(arm_r, "upper_arm_right", (4, 17, -1.5), (6.4, 24.5, 1.5)), CORPSE, CORPSE_D)
    fore = skin(m.cube(fore_r, "lower_arm_right", (4.1, 10.5, -1.3), (6.3, 17.5, 1.3)), CORPSE, CORPSE_D)
    fore.north.rect(0, 0, 1, 6, CORPSE_L)
    hand = m.cube(fore_r, "hand_right", (3.8, 7.2, -1.8), (6.6, 10.7, 1.8))
    hand.paint(CORPSE_D, jitter=0.2)
    hand.north.stripe(CORPSE, every=2, axis="v", offset=1, y=0, h=3)
    hand.down.fill(CORPSE_D)
    # claws hang from the knuckles as two crossed alpha planes (long dark nails)
    cl_z = m.plane(fore_r, "claws_front_right", (3.5, 2.0, -1.9), (6.9, 7.6, -1.9))
    cl_x = m.plane(fore_r, "claws_side_right", (5.2, 2.0, -1.7), (5.2, 7.6, 1.7))
    for plane in (cl_z, cl_x):
        fc = plane.front
        for x0 in range(0, fc.w, 1 if plane is cl_z else 2):
            ln = fc.h - fc.rng.randint(0, 2)
            for yy in range(ln):
                fc.pixel(x0, yy, NAIL if yy < ln - 2 else "#6e6a58")
        fc.rect(0, 0, fc.w, 1, mix(NAIL, "#000", 0.3))
    sleeve = m.plane(arm_r, "sleeve_right", (6.5, 8.5, -4.2), (6.5, 24.5, 4.2))
    cloth(sleeve.front, hem=0.5)
    sleeve.front.tatter(4, edge="bottom", chance=0.9)
    sleeve.front.tatter(2, edge="left", chance=0.5)
    sleeve.front.tatter(2, edge="right", chance=0.5)
    sleeve.front.rect(0, 0, sleeve.front.w, 2, SHROUD_D)
    for src, dst, nm in ((upper, arm_l, "upper_arm_left"), (fore, fore_l, "lower_arm_left"),
                         (hand, fore_l, "hand_left"), (cl_z, fore_l, "claws_front_left"),
                         (cl_x, fore_l, "claws_side_left")):
        src.mirror_x(dst, nm)
    sleeve.mirror_x(arm_l, "sleeve_left")

    # --- tattered skirt panels (swing with the stride)
    for name, z, x0, x1, y0 in (("skirt_front", -2.6, -3.6, 3.6, 1.0), ("skirt_back", 2.6, -4, 4, 0.5)):
        pl = m.plane(skirt, name, (x0, y0, z), (x1, 14.6, z))
        cloth(pl.front)
        pl.front.tatter(5, edge="bottom", chance=0.9)
        pl.front.tatter(2, edge="left", chance=0.5).tatter(2, edge="right", chance=0.5)
        pl.front.rect(0, 0, pl.front.w, 1, SHROUD_D)
    for side, sx in (("right", 1), ("left", -1)):
        pl = m.plane(skirt, f"skirt_side_{side}", (sx * 3.7, 2.5, -2.4), (sx * 3.7, 14.6, 2.4))
        cloth(pl.front)
        pl.front.tatter(4, edge="bottom", chance=0.9)

    # --- animations
    idle = m.animation("idle", 2.2, "loop")
    idle_breathe(idle, body="torso", head="head", arms=("arm_left", "arm_right"), amount=0.25, nod=4, arm_out=5)
    idle.rot("forearm_right", {0: (-8, 0, 0), 1.1: (-18, 0, 0), 2.2: (-8, 0, 0)})
    idle.rot("forearm_left", {0: (-14, 0, 0), 1.1: (-6, 0, 0), 2.2: (-14, 0, 0)})
    idle.rot("jaw", {0: (4, 0, 0), 1.1: (12, 0, 0), 2.2: (4, 0, 0)})
    idle.rot("skirt", {0: (0, 0, 1.5), 1.1: (0, 0, -1.5), 2.2: (0, 0, 1.5)})

    walk = m.animation("walk", 0.55, "loop")
    walk_biped(walk, legs=("leg_left", "leg_right"), shins=("shin_left", "shin_right"), body="body", head="head",
               swing=40, bob=0.8, sway=5, knee=50)
    L = walk.length
    walk.rot("torso", {0: (14, 4, 0), L / 2: (14, -4, 0), L: (14, 4, 0)})
    walk.rot("arm_right", {0: (-82, 0, 8), L / 2: (-58, 0, 12), L: (-82, 0, 8)})
    walk.rot("arm_left", {0: (-58, 0, -12), L / 2: (-82, 0, -8), L: (-58, 0, -12)})
    walk.rot("forearm_right", {0: (-18, 0, 0), L / 2: (-34, 0, 0), L: (-18, 0, 0)})
    walk.rot("forearm_left", {0: (-34, 0, 0), L / 2: (-18, 0, 0), L: (-34, 0, 0)})
    walk.rot("jaw", {0: (20, 0, 0), L / 2: (8, 0, 0), L: (20, 0, 0)})
    walk.rot("skirt", {0: (10, 0, 0), L / 4: (-6, 0, 0), L / 2: (10, 0, 0), 3 * L / 4: (-6, 0, 0), L: (10, 0, 0)})

    atk = m.animation("attack", 0.9, "once")
    atk.rot("arm_right", {0: (0, 0, 0), 0.28: (-135, 0, 24), 0.42: (-70, 0, -38), 0.6: (-60, 0, -30), 0.9: (0, 0, 0)},
            interp="linear")
    atk.rot("arm_left", {0: (0, 0, 0), 0.28: (-135, 0, -24), 0.42: (-70, 0, 38), 0.6: (-60, 0, 30), 0.9: (0, 0, 0)},
            interp="linear")
    atk.rot("forearm_right", {0: (0, 0, 0), 0.28: (-30, 0, 0), 0.42: (-10, 0, 0), 0.9: (0, 0, 0)}, interp="linear")
    atk.rot("forearm_left", {0: (0, 0, 0), 0.28: (-30, 0, 0), 0.42: (-10, 0, 0), 0.9: (0, 0, 0)}, interp="linear")
    atk.rot("torso", {0: (0, 0, 0), 0.28: (-12, 0, 0), 0.42: (30, 0, 0), 0.6: (26, 0, 0), 0.9: (0, 0, 0)}, interp="linear")
    atk.rot("head", {0: (0, 0, 0), 0.28: (-18, 0, 0), 0.42: (14, 0, 0), 0.9: (0, 0, 0)}, interp="linear")
    atk.rot("jaw", {0: (0, 0, 0), 0.28: (40, 0, 0), 0.42: (30, 0, 0), 0.9: (0, 0, 0)}, interp="linear")
    atk.pos("body", {0: (0, 0, 0), 0.28: (0, 0, 2), 0.42: (0, -0.5, -4), 0.6: (0, 0, -3), 0.9: (0, 0, 0)}, interp="linear")
    atk.rot("leg_right", {0: (0, 0, 0), 0.42: (-22, 0, 0), 0.9: (0, 0, 0)}, interp="linear")
    atk.rot("leg_left", {0: (0, 0, 0), 0.42: (14, 0, 0), 0.9: (0, 0, 0)}, interp="linear")

    death = m.animation("death", 1.8, "once")
    death_fall(death, direction="back")
    death.rot("arm_right", {0: (0, 0, 0), 0.5: (-60, 0, 30), 1.2: (-100, 0, 40), 1.8: (-100, 0, 40)}, interp="linear")
    death.rot("arm_left", {0: (0, 0, 0), 0.5: (-60, 0, -30), 1.2: (-100, 0, -40), 1.8: (-100, 0, -40)}, interp="linear")
    death.rot("head", {0: (0, 0, 0), 0.4: (-30, 0, 0), 1.3: (-12, 20, 0), 1.8: (-12, 20, 0)}, interp="linear")
    death.rot("jaw", {0: (0, 0, 0), 0.6: (45, 0, 0), 1.8: (50, 0, 0)}, interp="linear")
    death.rot("skirt", {0: (0, 0, 0), 0.6: (-20, 0, 0), 1.8: (-30, 0, 0)}, interp="linear")
    return m


MODELS = {
    "bigcasares_myth_oni": build_oni,
    "bigcasares_myth_cave_yokai": build_cave_yokai,
    "bigcasares_myth_shikome": build_shikome,
}
