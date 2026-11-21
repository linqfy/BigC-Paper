"""Diablo, the mythic boss of the medieval dungeon, and the two props of his encounter.

* ``bigcasares_myth_diablo``: ~3.6-block red devil with curved horns, goat legs (zig-zag
  thigh/shin/pastern blocks, black hooves), spade-tipped tail, high black-and-gold collar and
  a tattered black cape lined in crimson, and a pitchfork in the left hand.  The forearms
  have their own ``hand_*`` child bones so ``grab`` can stretch the forearm (scale y) while
  the hand is counter-scaled back to its size.
* ``bigcasares_myth_diablo_bottle``: the homing wine bottle (tumbles in ``idle``).
* ``bigcasares_myth_diablo_hand``: the stretched hand that holds each player in phase 2.  It
  is centred on the player; the forearm runs toward +Z (backward), i.e. back to Diablo when the
  anchor faces away from him.

Model faces north (-Z); the creature's right is +X.
"""
import sys
from pathlib import Path

sys.path.insert(0, str(Path(__file__).resolve().parents[1]))
from bbkit import Model, death_fall, idle_breathe, mix, ramp, walk_biped  # noqa: E402

SKIN = "#b3261e"
SKIN_D = "#6e120e"
SKIN_L = "#d8503a"
FUR = "#2c1a16"
FUR_D = "#140b0a"
HOOF = "#1a1414"
HORN = "#e6d6b4"
HORN_TIP = "#3a2a22"
EYE = "#ffd23a"
CAPE = "#17121a"
CAPE_L = "#2c2232"
LINING = "#7a0c18"
GOLD = "#d8a830"
GOLD_D = "#8a6418"
IRON = "#4a4850"
WOOD = "#3a241a"
CLAW = "#1c1416"
GLASS = "#1f4a2a"
WINE = "#6e0a1e"
CORK = "#a07a4a"
PARCH = "#e0cfa0"

# Seated pose shared by sit/grab/stunned: hips drop to the floor, legs stretch forward.
SEAT_DROP = -21.0
SEAT_THIGH = -86.0
SEAT_TAIL = 60.0  # the tail lies back along the floor instead of sinking into it
SEAT_CAPE = 30.0


def skin(cube, base=SKIN, density=0.0):
    cube.paint(base, noise=0.05, jitter=0.2)
    if density:
        for f in cube.sides():
            f.strands(mix(base, "#000", 0.25), density=density, length=(1, 2))
    return cube


def fur(cube, base=FUR, strands=FUR_D, density=0.45):
    cube.paint(base, noise=0.06, jitter=0.25)
    for f in cube.faces():
        if f.name not in ("up", "down"):
            f.strands(strands, density=density, length=(2, 4))
    return cube


def seated(anim, t0, t1=None):
    """Keys for the seated pose from ``t0`` (ramping in) to ``t1``/end (held)."""
    t1 = anim.length if t1 is None else t1
    anim.pos("body", {0: (0, 0, 0), t0: (0, SEAT_DROP, 0), t1: (0, SEAT_DROP, 0)})
    for leg, z in (("leg_left", -6), ("leg_right", 6)):
        anim.rot(leg, {0: (0, 0, 0), t0: (SEAT_THIGH, 0, z), t1: (SEAT_THIGH, 0, z)})
    for shin in ("shin_left", "shin_right"):
        anim.rot(shin, {0: (0, 0, 0), t0: (6, 0, 0), t1: (6, 0, 0)})
    anim.rot("tail", {0: (0, 0, 0), t0: (SEAT_TAIL, 0, 0), t1: (SEAT_TAIL, 0, 0)})
    anim.rot("cape", {0: (0, 0, 0), t0: (SEAT_CAPE, 0, 0), t1: (SEAT_CAPE, 0, 0)})


def build_diablo():
    m = Model("bigcasares_myth_diablo", texture_size=(256, 128))

    body = m.bone("body", pivot=(0, 28, 0))
    torso = m.bone("torso", body, pivot=(0, 32, 0))
    head = m.bone("head", torso, pivot=(0, 48, -1))
    arm_r = m.bone("arm_right", torso, pivot=(12, 46, 0))
    fore_r = m.bone("forearm_right", arm_r, pivot=(12.25, 35, 0))
    hand_r = m.bone("hand_right", fore_r, pivot=(12.25, 25, 0))
    arm_l = m.bone("arm_left", torso, pivot=(-12, 46, 0))
    fore_l = m.bone("forearm_left", arm_l, pivot=(-12.25, 35, 0))
    hand_l = m.bone("hand_left", fore_l, pivot=(-12.25, 25, 0))
    leg_r = m.bone("leg_right", body, pivot=(4.5, 28, 0))
    shin_r = m.bone("shin_right", leg_r, pivot=(4.5, 18, 0))
    leg_l = m.bone("leg_left", body, pivot=(-4.5, 28, 0))
    shin_l = m.bone("shin_left", leg_l, pivot=(-4.5, 18, 0))
    tail = m.bone("tail", body, pivot=(0, 28, 4.5), rotation=(-35, 0, 0))
    tail_tip = m.bone("tail_tip", tail, pivot=(0, 17, 5.5))
    cape = m.bone("cape", torso, pivot=(0, 47, 5.5))

    # --- hips: dark fur shorts and a gold-buckled belt
    pelvis = fur(m.cube(body, "pelvis", (-7, 25, -4.5), (7, 32, 4.5)))
    belt = m.cube(body, "belt", (-7.5, 30, -5), (7.5, 33, 5))
    belt.paint("#241614", jitter=0.1)
    for f in belt.sides():
        f.stripe(GOLD_D, every=4, axis="v", offset=1)
    belt.north.rect(5, 0, 5, 3, GOLD).outline(GOLD_D, 5, 0, 5, 3).pixel(7, 1, "#fff2b0")
    pelvis.north.rect(6, 0, 2, 4, FUR_D)

    # --- goat legs: forward thigh, back shin, forward pastern, black hoof (right built, left mirrored)
    thigh = fur(m.cube(leg_r, "thigh_right", (1.5, 18, -4.5), (7.5, 28, 3)))
    shin = fur(m.cube(shin_r, "shin_right_c", (2, 8, -1), (7, 18.5, 4)))
    shin.south.rect(1, 0, 3, 10, FUR_D)
    pastern = fur(m.cube(shin_r, "pastern_right", (2.5, 2, -2.5), (6.5, 8.5, 1.5)), density=0.3)
    hoof = m.cube(shin_r, "hoof_right", (2, 0, -4), (7, 2.5, 2))
    hoof.paint(HOOF, jitter=0.1)
    hoof.north.rect(2, 0, 1, 3, "#000").rect(0, 0, 5, 1, mix(HOOF, "#fff", 0.2))
    tuft = shin.overlay(0.5, "fetlock_right")
    for f in tuft.sides():
        f.jagged(FUR_D, edge="bottom", depth=(1, 3))
    thigh.mirror_x(leg_l, "thigh_left")
    shin.mirror_x(shin_l, "shin_left_c")
    pastern.mirror_x(shin_l, "pastern_left")
    hoof.mirror_x(shin_l, "hoof_left")
    tuft.mirror_x(shin_l, "fetlock_left")

    # --- torso: red belly and a broad muscled chest
    belly = skin(m.cube(torso, "belly", (-6.5, 32, -4), (6.5, 39, 4)))
    belly.north.stripe(SKIN_D, every=3, axis="h", offset=1, x=3, w=7).rect(6, 0, 1, 7, SKIN_D)
    chest = skin(m.cube(torso, "chest", (-10, 38, -5.5), (10, 48, 5)))
    pal = ramp(SKIN)
    chest.north.rect(2, 2, 16, 6, pal[2], noise=0.04)
    chest.north.line(10, 2, 10, 8, pal[0]).line(9, 2, 9, 8, pal[0])
    chest.north.line(2, 7, 8, 8, pal[1]).line(11, 8, 17, 7, pal[1])
    chest.north.rect(0, 0, 20, 1, SKIN_L)
    chest.up.fill(SKIN_L, noise=0.05)
    chest.south.fill(SKIN_D, noise=0.05)

    # --- collar and cape
    collar = m.cube(torso, "collar", (-10.5, 45, 3), (10.5, 53, 6.5))
    collar.paint(CAPE, jitter=0.12)
    collar.north.gradient(LINING, mix(LINING, "#000", 0.4), steps=3).outline(GOLD, sides="tlr")
    for f in (collar.south, collar.east, collar.west):
        f.outline(GOLD, sides="t")
        f.jagged(CAPE_L, edge="top", depth=(1, 2))
    collar.up.fill(GOLD).noise(0.08)
    cloak = m.plane(cape, "cape_back", (-11, 12, 6.6), (11, 47, 6.6))
    cf = cloak.front
    cf.gradient(CAPE_L, CAPE, steps=4).noise(0.06)
    cf.stripe(mix(CAPE, "#000", 0.3), every=4, axis="v", offset=2, y=4)
    cf.rect(0, 0, cf.w, 2, GOLD).rect(0, 2, cf.w, 1, GOLD_D)
    cf.outline(GOLD_D, sides="lr")
    cf.decal(["..g..", ".ggg.", "ggggg", ".g.g."], {"g": GOLD}, x=cf.w // 2 - 2, y=5)
    cf.tatter(4)
    lining = m.plane(cape, "cape_lining", (-10.5, 13, 6.1), (10.5, 46, 6.1))
    lining.front.gradient(LINING, mix(LINING, "#000", 0.45), steps=4).noise(0.07).tatter(3)

    # --- head: skull, brow, snout, goatee, ears, curved horns
    skull = skin(m.cube(head, "skull", (-5, 48, -6.5), (5, 57, 3)))
    sn = skull.north
    sn.eyes(5, 4, w=2, h=1, gap=2, glow=EYE, socket="#3a0806", brow="#2a0604")
    sn.rect(3, 6, 4, 1, SKIN_D).rect(2, 7, 6, 1, "#2a0604").pixel(2, 7, "#f2ead8").pixel(7, 7, "#f2ead8")
    sn.rect(4, 1, 2, 2, SKIN_D)
    skull.up.fill(SKIN_D, noise=0.05).stripe(mix(SKIN_D, "#000", 0.3), every=3, axis="v", offset=1)
    beard = m.plane(head, "goatee", (-2, 44, -6.6), (2, 49, -6.6))
    beard.front.fill(FUR_D).strands(FUR, density=0.4, length=(1, 2)).tatter(2)
    ear = skin(m.cube(head, "ear_right", (5, 52, -3), (8, 54, -1), rotation=(0, 0, 22.5), pivot=(5, 53, -2)))
    ear.east.rect(0, 0, 1, 2, SKIN_D)
    ear.mirror_x(head, "ear_left")
    horn1 = m.cube(head, "horn_root_right", (4.5, 54.5, -3.5), (9, 57.5, -0.5))
    horn1.paint(HORN, jitter=0.15)
    for f in horn1.sides():
        f.stripe(mix(HORN, "#000", 0.2), every=2, axis="v", offset=1)
    horn2 = m.cube(head, "horn_mid_right", (7.5, 56, -3.25), (10.5, 63, -0.25), rotation=(0, 0, -22.5),
                   pivot=(9, 56, -1.75))
    horn2.paint(HORN, jitter=0.12)
    for f in horn2.sides():
        f.gradient(HORN, mix(HORN, HORN_TIP, 0.45), steps=3).stripe(mix(HORN, "#000", 0.25), every=2, axis="h")
    horn3 = m.cube(head, "horn_tip_right", (10, 61.5, -2.75), (12, 68, -0.75), rotation=(0, 0, 22.5),
                   pivot=(11, 61.5, -1.75))
    horn3.paint(HORN_TIP, jitter=0.1)
    for f in horn3.sides():
        f.gradient(mix(HORN, HORN_TIP, 0.5), HORN_TIP, steps=3).rect(0, 0, f.w, 1, "#120c0a")
    horn1.mirror_x(head, "horn_root_left")
    horn2.mirror_x(head, "horn_mid_left")
    horn3.mirror_x(head, "horn_tip_left")

    # --- arms: shoulder, upper arm, long forearm, clawed hand with a gold bracer
    shoulder = skin(m.cube(arm_r, "shoulder_right", (9.5, 40, -3.75), (15.5, 48.5, 3.75)))
    shoulder.up.fill(SKIN_L, noise=0.05)
    upper = skin(m.cube(arm_r, "upper_arm_right", (10, 34.5, -3), (14.5, 41, 3)))
    fore = skin(m.cube(fore_r, "forearm_right_c", (10.25, 25, -2.75), (14.25, 35, 2.75)))
    bracer = fore.overlay(0.35, "bracer_right")
    for f in bracer.sides():
        f.rect(0, 0, f.w, 4, GOLD).noise(0.08, h=4).rect(0, 3, f.w, 1, GOLD_D).rect(0, 0, f.w, 1, "#fff0a0")
    hand = skin(m.cube(hand_r, "hand_right_c", (10, 20.5, -3), (14.5, 25, 3)), base=SKIN_D)
    hand.north.stripe("#2a0604", every=2, axis="v", y=0, h=3)
    claws = m.plane(hand_r, "claws_right", (10, 17, -3.1), (14.5, 20.5, -3.1))
    claws.front.decal(["c.c.c", "c.c.c", "c.c.c", ".c.c."], {"c": CLAW}).pixel(0, 0, "#5a4a48")
    shoulder.mirror_x(arm_l, "shoulder_left")
    upper.mirror_x(arm_l, "upper_arm_left")
    fore.mirror_x(fore_l, "forearm_left_c")
    bracer.mirror_x(fore_l, "bracer_left")
    hand.mirror_x(hand_l, "hand_left_c")
    claws.mirror_x(hand_l, "claws_left")

    # --- pitchfork in the left hand: iron-shod shaft, three-pronged head as an alpha plane
    shaft = m.cube(hand_l, "fork_shaft", (-13, 2, -5), (-11.5, 44, -3.5))
    shaft.paint(WOOD, jitter=0.2)
    for f in shaft.sides():
        f.stripe(mix(WOOD, "#000", 0.35), every=6, axis="h", offset=2)
        f.rect(0, 14, f.w, 3, IRON)
    prongs = m.plane(hand_l, "fork_head", (-16, 43, -4.25), (-8.5, 55, -4.25))
    pf = prongs.front
    w = pf.w
    mid = w // 2
    for x in (0, mid, w - 1):
        pf.rect(x, 1, 1, 7, IRON).pixel(x, 0, "#c8c8d0")  # three prongs with bright points
    pf.pixel(1, 1, IRON).pixel(w - 2, 1, IRON)            # barbs
    pf.rect(0, 8, w, 1, IRON).rect(1, 9, w - 2, 1, mix(IRON, "#000", 0.3))
    pf.rect(mid - 1, 10, 3, 1, IRON).rect(mid, 11, 1, pf.h - 11, IRON)

    # --- tail with a spade tip
    rope = skin(m.cube(tail, "tail_base", (-1, 17, 4.5), (1, 28, 6.5)), base=SKIN_D)
    rope.south.stripe(mix(SKIN_D, "#000", 0.3), every=3, axis="h")
    rope2 = skin(m.cube(tail_tip, "tail_end", (-0.75, 9, 4.75), (0.75, 17.5, 6.25)), base=SKIN_D)
    spade = m.plane(tail_tip, "tail_spade", (0, 3, 3), (0, 10, 8))
    spade.front.decal(["..s..",
                       ".sss.",
                       "sssss",
                       "sssss",
                       ".sss.",
                       "..s..",
                       "..s.."], {"s": SKIN_D}).pixel(2, 1, SKIN_L)
    rope2.down.fill(SKIN_D)

    # --- animations
    idle = m.animation("idle", 2.4, "loop")
    idle_breathe(idle, body="torso", head="head", arms=("arm_left", "arm_right"), tail="tail", amount=0.5)
    idle.rot("tail_tip", {0: (0, 10, 0), 1.2: (0, -10, 0), 2.4: (0, 10, 0)})
    idle.rot("cape", {0: (0, 0, 0), 1.2: (3, 0, 0), 2.4: (0, 0, 0)})

    walk = m.animation("walk", 1.0, "loop")
    walk_biped(walk, legs=("leg_left", "leg_right"), shins=("shin_left", "shin_right"),
               arms=("arm_left", "arm_right"), forearms=("forearm_left", "forearm_right"),
               body="body", head="head", swing=24, bob=0.8, sway=4)
    L = walk.length
    walk.rot("cape", {0: (8, 0, 0), L / 2: (14, 0, 0), L: (8, 0, 0)})
    walk.rot("tail", {0: (0, -14, 0), L / 2: (0, 14, 0), L: (0, -14, 0)})

    circle = m.animation("circle", 1.2, "loop")
    L = circle.length
    circle.pos("body", {0: (0, 2, 0), L / 2: (0, 3.5, 0), L: (0, 2, 0)})
    circle.rot("body", {0: (6, 0, -10), L / 2: (6, 0, -14), L: (6, 0, -10)})
    circle.rot("head", {0: (0, 25, 8), L / 2: (-4, 30, 8), L: (0, 25, 8)})
    circle.rot("arm_right", {0: (-20, 0, 55), L / 2: (-30, 0, 65), L: (-20, 0, 55)})
    circle.rot("arm_left", {0: (-20, 0, -55), L / 2: (-30, 0, -65), L: (-20, 0, -55)})
    circle.rot("forearm_right", {0: (-30, 0, 0), L / 2: (-40, 0, 0), L: (-30, 0, 0)})
    circle.rot("forearm_left", {0: (-30, 0, 0), L / 2: (-40, 0, 0), L: (-30, 0, 0)})
    circle.rot("leg_right", {0: (-25, 0, 0), L / 2: (-30, 0, 0), L: (-25, 0, 0)})
    circle.rot("leg_left", {0: (10, 0, 0), L / 2: (5, 0, 0), L: (10, 0, 0)})
    circle.rot("shin_right", {0: (40, 0, 0), L: (40, 0, 0)})
    circle.rot("cape", {0: (25, 0, 0), L / 2: (35, 0, 0), L: (25, 0, 0)})
    circle.rot("tail", {0: (20, -25, 0), L / 2: (20, 25, 0), L: (20, -25, 0)})

    throw = m.animation("throw", 1.0, "once")
    throw.rot("arm_right", {0: (0, 0, 0), 0.35: (40, 0, 15), 0.55: (-165, 0, 5), 0.7: (-110, 0, 0),
                            1.0: (0, 0, 0)}, interp="linear")
    throw.rot("forearm_right", {0: (0, 0, 0), 0.35: (-60, 0, 0), 0.55: (-10, 0, 0), 1.0: (0, 0, 0)},
              interp="linear")
    throw.rot("torso", {0: (0, 0, 0), 0.35: (-8, 20, 0), 0.6: (12, -15, 0), 1.0: (0, 0, 0)}, interp="linear")
    throw.rot("head", {0: (0, 0, 0), 0.35: (-10, 0, 0), 0.6: (8, 0, 0), 1.0: (0, 0, 0)}, interp="linear")

    attack = m.animation("attack", 0.8, "once")
    attack.rot("arm_right", {0: (0, 0, 0), 0.3: (-80, -40, 30), 0.45: (-70, 55, -10), 0.8: (0, 0, 0)},
               interp="linear")
    attack.rot("arm_left", {0: (0, 0, 0), 0.3: (-80, 40, -30), 0.5: (-70, -55, 10), 0.8: (0, 0, 0)},
               interp="linear")
    attack.rot("torso", {0: (0, 0, 0), 0.3: (-6, -20, 0), 0.45: (12, 25, 0), 0.8: (0, 0, 0)}, interp="linear")

    rage = m.animation("rage", 2.0, "once")
    rage.rot("arm_right", {0: (0, 0, 0), 0.4: (-30, 0, 75), 1.4: (-35, 0, 80), 2.0: (0, 0, 0)})
    rage.rot("arm_left", {0: (0, 0, 0), 0.4: (-30, 0, -75), 1.4: (-35, 0, -80), 2.0: (0, 0, 0)})
    rage.rot("forearm_right", {0: (0, 0, 0), 0.4: (-50, 0, 0), 1.4: (-60, 0, 0), 2.0: (0, 0, 0)})
    rage.rot("forearm_left", {0: (0, 0, 0), 0.4: (-50, 0, 0), 1.4: (-60, 0, 0), 2.0: (0, 0, 0)})
    rage.rot("head", {0: (0, 0, 0), 0.4: (-30, 0, 0), 0.6: (-28, 4, 0), 0.8: (-30, -4, 0), 1.0: (-28, 4, 0),
                      1.4: (-30, 0, 0), 2.0: (0, 0, 0)})
    rage.rot("torso", {0: (0, 0, 0), 0.4: (-12, 0, 0), 1.4: (-12, 0, 0), 2.0: (0, 0, 0)})
    rage.scale("torso", {0: (1, 1, 1), 0.4: (1.08, 1.04, 1.08), 1.4: (1.08, 1.04, 1.08), 2.0: (1, 1, 1)})
    rage.rot("tail", {0: (0, 0, 0), 0.4: (30, 0, 0), 1.4: (30, 0, 0), 2.0: (0, 0, 0)})

    sit = m.animation("sit", 2.0, "loop")
    sit.pos("body", {0: (0, SEAT_DROP, 0), 1.0: (0, SEAT_DROP + 0.4, 0), 2.0: (0, SEAT_DROP, 0)})
    for leg, z in (("leg_left", -6), ("leg_right", 6)):
        sit.rot(leg, {0: (SEAT_THIGH, 0, z), 2.0: (SEAT_THIGH, 0, z)})
    for shin_b in ("shin_left", "shin_right"):
        sit.rot(shin_b, {0: (6, 0, 0), 2.0: (6, 0, 0)})
    sit.rot("tail", {0: (SEAT_TAIL, -12, 0), 1.0: (SEAT_TAIL, 12, 0), 2.0: (SEAT_TAIL, -12, 0)})
    sit.rot("cape", {0: (SEAT_CAPE, 0, 0), 2.0: (SEAT_CAPE, 0, 0)})
    sit.rot("torso", {0: (8, 0, 0), 1.0: (10, 0, 0), 2.0: (8, 0, 0)})
    sit.rot("arm_right", {0: (-30, 0, 20), 1.0: (-34, 0, 24), 2.0: (-30, 0, 20)})
    sit.rot("arm_left", {0: (-30, 0, -20), 1.0: (-34, 0, -24), 2.0: (-30, 0, -20)})
    sit.rot("head", {0: (-10, 0, 0), 1.0: (-12, 0, 0), 2.0: (-10, 0, 0)})

    grab = m.animation("grab", 1.6, "hold")
    seated(grab, 0.2)
    grab.rot("torso", {0: (0, 0, 0), 0.6: (14, 0, 0), 1.6: (14, 0, 0)})
    grab.rot("arm_right", {0: (0, 0, 0), 0.6: (-95, -20, 0), 1.0: (-90, -28, 0), 1.6: (-92, -24, 0)})
    grab.rot("arm_left", {0: (0, 0, 0), 0.6: (-95, 20, 0), 1.0: (-90, 28, 0), 1.6: (-92, 24, 0)})
    for fore_b, hand_b in (("forearm_right", "hand_right"), ("forearm_left", "hand_left")):
        grab.scale(fore_b, {0: (1, 1, 1), 0.3: (1, 1, 1), 0.9: (1, 3.2, 1), 1.6: (1, 3.2, 1)})
        grab.scale(hand_b, {0: (1, 1, 1), 0.3: (1, 1, 1), 0.9: (1.15, 0.36, 1.15), 1.6: (1.15, 0.36, 1.15)})
    grab.rot("head", {0: (0, 0, 0), 0.6: (-18, 0, 0), 1.6: (-15, 0, 0)})

    stunned = m.animation("stunned", 2.0, "loop")
    seated(stunned, 0.0)
    stunned.rot("torso", {0: (22, 0, -6), 1.0: (22, 0, 6), 2.0: (22, 0, -6)})
    stunned.rot("head", {0: (25, -15, 10), 1.0: (25, 15, -10), 2.0: (25, -15, 10)})
    stunned.rot("arm_right", {0: (10, 0, 12), 2.0: (10, 0, 12)})
    stunned.rot("arm_left", {0: (10, 0, -12), 2.0: (10, 0, -12)})

    death = m.animation("death", 2.4, "once")
    death_fall(death, direction="back")
    death.rot("arm_right", {0: (0, 0, 0), 0.6: (-40, 0, 30), 1.5: (-120, 0, 40), 2.4: (-120, 0, 40)},
              interp="linear")
    death.rot("arm_left", {0: (0, 0, 0), 0.6: (-40, 0, -30), 1.5: (-115, 0, -45), 2.4: (-115, 0, -45)},
              interp="linear")
    death.rot("head", {0: (0, 0, 0), 0.5: (-30, 0, 0), 1.6: (10, 25, 0), 2.4: (10, 25, 0)}, interp="linear")
    death.rot("tail", {0: (0, 0, 0), 1.6: (40, 0, 0), 2.4: (40, 0, 0)}, interp="linear")

    m.validate(require=("idle", "walk", "circle", "throw", "attack", "rage", "sit", "grab", "stunned", "death"),
               quiet=True)
    return m


def build_bottle():
    m = Model("bigcasares_myth_diablo_bottle", texture_size=(64, 64), bone_budget=(6, 8), cube_budget=(8, 10))
    spin = m.bone("bottle", pivot=(0, 5, 0))
    glass = m.cube(spin, "glass", (-2, 0, -2), (2, 7, 2))
    glass.paint(GLASS, jitter=0.12)
    for f in glass.sides():
        f.rect(0, 2, f.w, 5, mix(GLASS, WINE, 0.6)).pixel(0, 1, "#8ad0a0").rect(0, 0, 1, 6, mix(GLASS, "#fff", 0.25))
    glass.north.rect(0, 3, 4, 3, PARCH).rect(1, 4, 2, 1, WINE)
    neck = m.cube(spin, "neck", (-1, 7, -1), (1, 10.5, 1))
    neck.paint(GLASS, jitter=0.1)
    cork = m.cube(spin, "cork", (-0.75, 10.5, -0.75), (0.75, 12, 0.75))
    cork.paint(CORK, jitter=0.15)
    seal = neck.overlay(0.2, "seal")
    for f in seal.sides():
        f.rect(0, 0, f.w, 1, GOLD)

    idle = m.animation("idle", 0.6, "loop", interp="linear")
    idle.rot("bottle", {0: (0, 0, 0), 0.3: (180, 0, 0), 0.6: (360, 0, 0)})
    m.validate(require=("idle",), quiet=True)
    return m


def build_hand():
    m = Model("bigcasares_myth_diablo_hand", texture_size=(128, 128), bone_budget=(8, 10), cube_budget=(16, 20))
    arm = m.bone("arm", pivot=(0, 16, 8))
    palm_b = m.bone("palm", arm, pivot=(0, 16, 7))
    fingers_r = m.bone("fingers_right", palm_b, pivot=(8, 16, 5))
    fingers_l = m.bone("fingers_left", palm_b, pivot=(-8, 16, 5))

    fore = skin(m.cube(arm, "forearm", (-4, 11, 10), (4, 20, 42)))
    for f in (fore.east, fore.west, fore.up):
        f.stripe(SKIN_D, every=5, axis="v", offset=2)
    cuff = m.cube(arm, "cuff", (-4.5, 10.5, 10), (4.5, 20.5, 14))
    cuff.paint(GOLD, jitter=0.12)
    for f in cuff.sides():
        f.rect(0, 0, f.w, 1, "#fff0a0").rect(0, f.h - 1, f.w, 1, GOLD_D)
    palm = skin(m.cube(palm_b, "palm_c", (-8, 7, 5), (8, 25, 10)), base=SKIN_D)
    palm.north.stripe(mix(SKIN_D, "#000", 0.3), every=4, axis="h", offset=1)
    knuckles = palm.overlay(0.3, "knuckles")
    knuckles.south.stripe(SKIN, every=4, axis="h", offset=2)
    for idx, (y0, y1) in enumerate(((8, 12), (13, 17), (18, 22))):
        f = skin(m.cube(fingers_r, f"finger_right_{idx}", (7, y0, -7), (10, y1, 6)))
        f.north.fill(CLAW).rect(0, 0, 3, 1, "#5a4a48")
        f.up.stripe(SKIN_D, every=4, axis="h", offset=1)
        f.mirror_x(fingers_l, f"finger_left_{idx}")
    tip_r = m.cube(fingers_r, "claw_tip_right", (4, 8, -8), (7, 22, -6))
    tip_r.paint(CLAW, jitter=0.1)
    tip_r.east.stripe("#5a4a48", every=5, axis="h")
    tip_r.mirror_x(fingers_l, "claw_tip_left")

    idle = m.animation("idle", 0.8, "loop")
    idle.rot("fingers_right", {0: (0, -4, 0), 0.4: (0, 4, 0), 0.8: (0, -4, 0)})
    idle.rot("fingers_left", {0: (0, 4, 0), 0.4: (0, -4, 0), 0.8: (0, 4, 0)})
    idle.scale("palm", {0: (1, 1, 1), 0.4: (1.04, 1.02, 1.0), 0.8: (1, 1, 1)})
    idle.pos("arm", {0: (0, 0, 0), 0.4: (0, 0.4, 0), 0.8: (0, 0, 0)})
    m.validate(require=("idle",), quiet=True)
    return m


REQUIRED_ANIMATIONS = ("idle",)

MODELS = {
    "bigcasares_myth_diablo": build_diablo,
    "bigcasares_myth_diablo_bottle": build_bottle,
    "bigcasares_myth_diablo_hand": build_hand,
}
