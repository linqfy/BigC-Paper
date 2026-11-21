"""Zeus, mythic boss of the Greek dungeon, plus his summon and prop.

* ``bigcasares_myth_zeus``        ~3.5 blocks: muscular bearded god in a white/gold chiton, laurel
  crown, glowing eyes, lightning bolt in the right hand, leather sandals, himation cape.
* ``bigcasares_myth_zeus_cloud``  electric storm cloud minion with two dangling lightning "arms".
* ``bigcasares_myth_zeus_spear``  the thrown spear (stuck pose for the vulnerability window).

Style follows greek.py: few varied cuboids, detail painted, relief from overlays and alpha planes.
Model faces north (-Z); the creature's right is +X.  Left limbs mirror the right ones.
"""
import sys
from pathlib import Path

sys.path.insert(0, str(Path(__file__).resolve().parents[1]))
from bbkit import (Model, attack_swing, death_fall, idle_breathe, mix, ramp,  # noqa: E402
                   walk_biped)

REQUIRED_ANIMATIONS = ("idle", "death")  # the spear is a prop; Zeus and the cloud check their own below

SKIN = "#d29a6c"
SKIN_D = "#a8724c"
SKIN_L = "#ecbf92"
HAIR = "#e6e6ee"
HAIR_D = "#9c9cb0"
CLOTH = "#f0ece0"
CLOTH_D = "#c9c1ae"
GOLD = "#e3b23c"
GOLD_D = "#9a6d1c"
GOLD_L = "#fff0a0"
LAUREL = "#7fa83e"
LAUREL_D = "#4c6e22"
LEATHER = "#6b4526"
LEATHER_D = "#3e2614"
BOLT = "#ffe14a"
BOLT_CORE = "#fffbe0"
BOLT_EDGE = "#ffae1a"
EYE = "#8fe8ff"
CAPE = "#3b5ea8"
CAPE_D = "#24396e"

GREEK_KEY = [
    "gggggg.g",
    "g....g.g",
    "g.ggg..g",
    "g.g.gggg",
]

ZEUS_ANIMATIONS = ("idle", "walk", "attack", "cast", "rage", "lanza", "vulnerable", "death")
CLOUD_ANIMATIONS = ("idle", "walk", "attack", "death")


def _require(m, names):
    missing = [n for n in names if n not in m.animations]
    if missing:
        raise SystemExit(f"{m.name}: missing animations {missing}")


def _key_band(face, y, color=GOLD):
    for x0 in range(0, face.w, 8):
        face.decal(GREEK_KEY, {"g": color}, x=x0, y=y)
    return face


def _bolt_shape(face, *, core=BOLT_CORE, body=BOLT, edge=BOLT_EDGE, half=2):
    """Zig-zag lightning bolt filling a tall plane (top to bottom)."""
    w, h = face.w, face.h
    pts = [(w * 0.62, 0), (w * 0.30, h * 0.38), (w * 0.62, h * 0.42), (w * 0.22, h * 0.78),
           (w * 0.50, h * 0.80), (w * 0.38, h - 1)]
    for (x0, y0), (x1, y1) in zip(pts, pts[1:]):
        for dx in range(-half, half + 1):
            face.line(int(x0) + dx, int(y0), int(x1) + dx, int(y1), edge if abs(dx) == half else body)
        face.line(int(x0), int(y0), int(x1), int(y1), core)
    return face


# =====================================================================================
# ZEUS
# =====================================================================================
def build_zeus():
    m = Model("bigcasares_myth_zeus", texture_size=(128, 128))

    body = m.bone("body", pivot=(0, 24, 0))
    torso = m.bone("torso", body, pivot=(0, 27, 0))
    head = m.bone("head", torso, pivot=(0, 43, -1))
    arm_r = m.bone("arm_right", torso, pivot=(12.5, 40, 0))
    fore_r = m.bone("forearm_right", arm_r, pivot=(12.5, 31, 0))
    bolt_b = m.bone("bolt", fore_r, pivot=(12.5, 20, -1))
    arm_l = m.bone("arm_left", torso, pivot=(-12.5, 40, 0))
    fore_l = m.bone("forearm_left", arm_l, pivot=(-12.5, 31, 0))
    leg_r = m.bone("leg_right", body, pivot=(4.25, 24, 0))
    shin_r = m.bone("shin_right", leg_r, pivot=(4.25, 13, 0))
    leg_l = m.bone("leg_left", body, pivot=(-4.25, 24, 0))
    shin_l = m.bone("shin_left", leg_l, pivot=(-4.25, 13, 0))
    cape = m.bone("cape", torso, pivot=(0, 41, 5.5))
    skirt = m.bone("skirt", body, pivot=(0, 25, 0))

    # --- legs: thigh (mostly under the chiton), shin with sandal straps, sandal
    thigh = m.cube(leg_r, "thigh_right", (1.5, 13, -2.75), (7, 24, 2.75))
    thigh.paint(SKIN, jitter=0.12)
    shin = m.cube(shin_r, "lower_leg_right", (1.75, 2, -2.5), (6.75, 13, 2.5))
    shin.paint(SKIN, jitter=0.12)
    for f in shin.sides():
        for y in (1, 4, 7):
            f.line(0, y + 2, f.w - 1, y, LEATHER)
        f.rect(0, f.h - 2, f.w, 2, LEATHER_D)
    shin.north.rect(2, 0, 1, 2, SKIN_D)  # knee
    sandal = m.cube(shin_r, "sandal_right", (1.5, 0, -5), (7, 2, 2.75))
    sandal.paint(LEATHER, jitter=0.1)
    sandal.up.fill(SKIN).rect(0, 0, sandal.up.w, 2, mix(SKIN, SKIN_D, 0.4))
    sandal.up.line(0, 3, sandal.up.w - 1, 5, LEATHER).line(sandal.up.w - 1, 3, 0, 5, LEATHER)
    sandal.north.rect(0, 0, 6, 1, GOLD).pixel(2, 0, GOLD_L)
    for f in sandal.sides():
        f.rect(0, f.h - 1, f.w, 1, LEATHER_D)
    thigh.mirror_x(leg_l, "thigh_left")
    shin.mirror_x(shin_l, "lower_leg_left")
    sandal.mirror_x(shin_l, "sandal_left")

    # --- chiton skirt (static on the body, like a robe) with folds and a gold meander hem
    hem = m.cube(skirt, "chiton_skirt", (-8.5, 13, -4.75), (8.5, 26, 4.75))
    hem.paint(CLOTH, jitter=0.1)
    for f in hem.sides():
        f.stripe(CLOTH_D, every=3, axis="v", offset=1)
        f.rect(0, f.h - 4, f.w, 4, GOLD_D)
        _key_band(f, f.h - 4)
    hem.down.fill(CLOTH_D)
    fold = m.plane(skirt, "chiton_flap", (-4, 11, -5.0), (4, 24, -5.0))
    fold.front.gradient(CLOTH, CLOTH_D, steps=3).stripe(mix(CLOTH_D, "#000", 0.15), every=2, axis="v")
    fold.front.rect(0, fold.front.h - 2, 8, 2, GOLD).tatter(1)

    # --- torso: belly with gold belt, broad chest, chiton over the left shoulder
    belly = m.cube(torso, "belly", (-8, 25, -4.25), (8, 31.5, 4.25))
    belly.paint(SKIN, jitter=0.12)
    belly.north.stripe(SKIN_D, every=3, axis="h", offset=1, x=4, w=8)
    belly.north.rect(7, 0, 2, 7, SKIN_D)
    belt = belly.overlay(0.45, "belt")
    for f in belt.sides():
        f.rect(0, 0, f.w, 3, GOLD).rect(0, 2, f.w, 1, GOLD_D).rect(0, 0, f.w, 1, GOLD_L)
    belt.north.rect(6, 0, 4, 3, GOLD_L).outline(GOLD_D, 6, 0, 4, 3).pixel(7, 1, BOLT)
    chest = m.cube(torso, "chest", (-10, 31, -5), (10, 42, 5))
    chest.paint(SKIN, jitter=0.12)
    pal = ramp(SKIN)
    cn = chest.north
    cn.line(10, 2, 10, 9, pal[0]).line(3, 7, 9, 8, pal[1]).line(11, 8, 17, 7, pal[1])  # pecs
    cn.rect(4, 3, 5, 4, pal[3]).rect(12, 3, 5, 4, pal[3])
    chiton = chest.overlay(0.35, "chiton_top")
    # diagonal drape from the left shoulder (-X, right of the north face as seen) to the right hip
    cf = chiton.north
    for y in range(cf.h):
        start = max(0, int(round(cf.w - 5 - y * 1.1)))
        cf.rect(start, y, cf.w - start, 1, CLOTH)
        cf.stripe(CLOTH_D, every=3, axis="v", offset=2, x=start + 1, y=y, w=max(1, cf.w - start - 1), h=1)
        cf.pixel(start, y, GOLD)
    cs = chiton.south
    cs.fill(CLOTH).stripe(CLOTH_D, every=3, axis="v").rect(0, cs.h - 2, cs.w, 2, GOLD)
    chiton.west.fill(CLOTH).stripe(CLOTH_D, every=3, axis="v")
    chiton.up.rect(0, 0, 7, chiton.up.h, CLOTH).rect(6, 0, 1, chiton.up.h, GOLD)
    brooch = m.cube(torso, "brooch", (-8.5, 39.5, -5.6), (-6, 42, -4.9))
    brooch.paint(GOLD, jitter=0.1)
    brooch.north.pixel(1, 1, GOLD_L)

    # --- cape (himation) hanging from the shoulders
    cape_p = m.plane(cape, "himation", (-9, 16, 5.6), (9, 41, 5.6))
    cface = cape_p.front
    cface.gradient(CAPE, CAPE_D, steps=4).stripe(mix(CAPE_D, "#000", 0.2), every=3, axis="v", offset=1)
    cface.rect(0, 0, cface.w, 2, GOLD).rect(0, cface.h - 4, cface.w, 4, GOLD_D)
    _key_band(cface, cface.h - 4)
    cface.noise(0.05)

    # --- head: skull, hair cap with laurel band, big beard, glowing eyes
    skull = m.cube(head, "skull", (-5, 43, -5.5), (5, 53, 3.5))
    skull.paint(SKIN, jitter=0.1)
    sn = skull.north
    sn.rect(0, 0, 10, 2, HAIR)
    sn.eyes(5, 4, w=2, h=1, gap=2, glow=EYE, socket=SKIN_D, brow=HAIR_D)
    sn.rect(4, 5, 2, 2, SKIN_D).pixel(4, 6, SKIN_L)  # nose
    hair = skull.overlay(0.5, "hair")
    for f in (hair.east, hair.west, hair.south):
        f.fill(HAIR).strands(HAIR_D, density=0.35, length=(2, 4)).noise(0.05)
        f.rect(0, 0, f.w, 1, mix(HAIR, "#fff", 0.4))
    hair.up.fill(mix(HAIR, HAIR_D, 0.25)).strands(HAIR_D, density=0.55, length=(2, 4)).noise(0.08)
    hair.up.dither(mix(HAIR, "#fff", 0.3), density=0.25, pattern="noise")
    hair.north.jagged(HAIR, edge="top", depth=(1, 2))
    hair.east.clear(0, 3, 3, 5)
    hair.west.clear(hair.west.w - 3, 3, 3, 5)
    laurel = skull.overlay(1.0, "laurel")
    for f in laurel.sides():
        f.rect(0, 2, f.w, 2, LAUREL_D)
        for x in range(0, f.w, 2):
            f.pixel(x, 1, LAUREL).pixel(x + 1, 2, LAUREL).pixel(x, 3, GOLD if x % 4 == 0 else LAUREL)
    laurel.north.clear(3, 0, 4, 4).pixel(4, 3, GOLD_L).pixel(5, 3, GOLD_L)
    beard = m.cube(head, "beard", (-5, 36.5, -6.75), (5, 46, -3))
    beard.paint(HAIR, jitter=0.15)
    bn = beard.north
    bn.strands(HAIR_D, density=0.4, length=(2, 4))
    bn.rect(2, 0, 6, 1, HAIR_D).rect(1, 1, 3, 1, mix(HAIR, HAIR_D, 0.5)).rect(6, 1, 3, 1, mix(HAIR, HAIR_D, 0.5))
    bn.rect(3, 2, 4, 1, "#5a2f2a")  # mouth
    bn.jagged(HAIR, edge="bottom", depth=(1, 3))
    bn.clear(0, 0, 1, 3).clear(9, 0, 1, 3)  # cheeks show beside the moustache
    for f in (beard.east, beard.west):
        f.strands(HAIR_D, density=0.35, length=(2, 3))
    beard_tip = m.plane(head, "beard_tip", (-4, 32, -6.8), (4, 36.5, -6.8))
    beard_tip.front.fill(HAIR).strands(HAIR_D, density=0.5, length=(1, 3)).tatter(3)

    # --- arms: deltoid, upper arm with gold armband, forearm with bracer, fist
    delt = m.cube(arm_r, "deltoid_right", (9.5, 35.5, -3.75), (15.5, 42.5, 3.75))
    delt.paint(SKIN, jitter=0.12)
    delt.up.lighten(0.08)
    upper = m.cube(arm_r, "upper_arm_right", (10, 30.5, -3), (15, 36, 3))
    upper.paint(SKIN, jitter=0.12)
    band = upper.overlay(0.3, "armband_right")
    for f in band.sides():
        f.rect(0, 1, f.w, 2, GOLD).rect(0, 1, f.w, 1, GOLD_L)
    fore = m.cube(fore_r, "lower_arm_right", (10.25, 22, -2.75), (14.75, 31, 2.75))
    fore.paint(SKIN, jitter=0.12)
    bracer = fore.overlay(0.35, "bracer_right")
    for f in bracer.sides():
        f.rect(0, f.h - 5, f.w, 4, GOLD).noise(0.06, y=f.h - 5, h=4)
        f.rect(0, f.h - 5, f.w, 1, GOLD_L).rect(0, f.h - 2, f.w, 1, GOLD_D)
    fist = m.cube(fore_r, "fist_right", (10.5, 17.5, -2.75), (14.5, 22, 2.75))
    fist.paint(mix(SKIN, SKIN_D, 0.3), jitter=0.1)
    fist.north.stripe(SKIN_D, every=2, axis="v", h=2)
    delt.mirror_x(arm_l, "deltoid_left")
    upper.mirror_x(arm_l, "upper_arm_left")
    band.mirror_x(arm_l, "armband_left")
    fore.mirror_x(fore_l, "lower_arm_left")
    bracer.mirror_x(fore_l, "bracer_left")
    fist.mirror_x(fore_l, "fist_left")

    # --- the thunderbolt: a gold grip in the fist and two crossed alpha planes of lightning
    grip = m.cube(bolt_b, "bolt_grip", (11.75, 16.5, -4.5), (13.25, 23, -3))
    grip.paint(GOLD, jitter=0.1)
    grip.north.rect(0, 0, 2, 1, GOLD_L)
    blade_a = m.plane(bolt_b, "bolt_up", (12.5, 22, -12), (12.5, 40, 2))
    _bolt_shape(blade_a.front)
    blade_b = m.plane(bolt_b, "bolt_down", (12.5, 9, -7), (12.5, 17, -1))
    _bolt_shape(blade_b.front)
    cross = m.plane(bolt_b, "bolt_side", (8, 24, -3.75), (17, 36, -3.75))
    _bolt_shape(cross.front)

    _zeus_animations(m)
    _require(m, ZEUS_ANIMATIONS)
    return m


def _zeus_animations(m):
    idle = m.animation("idle", 3.0, "loop")
    idle_breathe(idle, body="body", chest="torso", head="head", arms=("arm_left", "arm_right"), amount=0.5)
    idle.rot("cape", {0: (4, 0, 0), 1.5: (8, 0, 0), 3.0: (4, 0, 0)})
    idle.scale("bolt", {0: (1, 1, 1), 0.2: (1.06, 1.06, 1.06), 0.4: (1, 1, 1), 2.2: (1, 1, 1),
                        2.35: (1.08, 1.08, 1.08), 2.5: (1, 1, 1), 3.0: (1, 1, 1)}, interp="linear")

    walk = m.animation("walk", 1.4, "loop")
    walk_biped(walk, legs=("leg_left", "leg_right"), shins=("shin_left", "shin_right"),
               arms=("arm_left", "arm_right"), forearms=("forearm_left", "forearm_right"),
               body="body", head="head", swing=24, bob=0.8, sway=3)
    L = walk.length
    walk.rot("cape", {0: (10, 0, 0), L / 2: (16, 0, 0), L: (10, 0, 0)})

    attack = m.animation("attack", 1.0, "once")
    attack_swing(attack, "arm_right", forearms=("forearm_right",), body="torso", head="head",
                 style="overhead", windup=0.4, strike=0.55)
    attack.rot("arm_left", {0: (0, 0, 0), 0.4: (-25, 0, -30), 0.55: (15, 0, -15), 1.0: (0, 0, 0)}, interp="linear")
    attack.scale("bolt", {0: (1, 1, 1), 0.5: (1, 1, 1), 0.56: (1.35, 1.35, 1.35), 0.75: (1, 1, 1), 1.0: (1, 1, 1)},
                 interp="linear")

    cast = m.animation("cast", 1.4, "once")
    cast.rot("arm_right", {0: (0, 0, 0), 0.45: (-170, 0, 10), 0.9: (-170, 0, 10), 1.15: (-60, 0, 0),
                           1.4: (0, 0, 0)}, interp="linear")
    cast.rot("arm_left", {0: (0, 0, 0), 0.45: (-150, 0, -25), 0.9: (-150, 0, -25), 1.15: (-40, 0, -10),
                          1.4: (0, 0, 0)}, interp="linear")
    cast.rot("torso", {0: (0, 0, 0), 0.45: (-12, 0, 0), 0.9: (-12, 0, 0), 1.1: (10, 0, 0), 1.4: (0, 0, 0)},
             interp="linear")
    cast.rot("head", {0: (0, 0, 0), 0.45: (-25, 0, 0), 0.9: (-25, 0, 0), 1.4: (0, 0, 0)}, interp="linear")
    cast.scale("bolt", {0: (1, 1, 1), 0.45: (1.4, 1.4, 1.4), 0.9: (1.5, 1.5, 1.5), 1.15: (1, 1, 1),
                        1.4: (1, 1, 1)}, interp="linear")
    cast.pos("body", {0: (0, 0, 0), 0.45: (0, 1.5, 0), 0.9: (0, 1.5, 0), 1.1: (0, -1, 0), 1.4: (0, 0, 0)},
             interp="linear")

    rage = m.animation("rage", 2.4, "once")
    rage.rot("arm_right", {0: (0, 0, 0), 0.5: (-20, 0, 95), 1.6: (-20, 0, 100), 2.4: (0, 0, 0)})
    rage.rot("arm_left", {0: (0, 0, 0), 0.5: (-20, 0, -95), 1.6: (-20, 0, -100), 2.4: (0, 0, 0)})
    rage.rot("forearm_right", {0: (0, 0, 0), 0.5: (-60, 0, 0), 1.6: (-60, 0, 0), 2.4: (0, 0, 0)})
    rage.rot("forearm_left", {0: (0, 0, 0), 0.5: (-60, 0, 0), 1.6: (-60, 0, 0), 2.4: (0, 0, 0)})
    rage.rot("torso", {0: (0, 0, 0), 0.5: (-18, 0, 0), 0.8: (-14, 3, 0), 1.0: (-18, -3, 0), 1.2: (-14, 3, 0),
                       1.6: (-18, 0, 0), 2.4: (0, 0, 0)})
    rage.rot("head", {0: (0, 0, 0), 0.5: (-35, 0, 0), 1.6: (-35, 0, 0), 2.4: (0, 0, 0)})
    rage.pos("body", {0: (0, 0, 0), 0.5: (0, 2, 0), 1.6: (0, 2, 0), 2.4: (0, 0, 0)})
    rage.scale("bolt", {0: (1, 1, 1), 0.5: (1.6, 1.6, 1.6), 1.6: (1.6, 1.6, 1.6), 2.4: (1, 1, 1)})
    rage.rot("cape", {0: (5, 0, 0), 0.5: (35, 0, 0), 1.6: (30, 0, 0), 2.4: (5, 0, 0)})

    # lean back, cock the right arm behind the head, release at 0.9 s (THROW_AT = 18 ticks), follow through
    lanza = m.animation("lanza", 1.5, "once")
    lanza.rot("torso", {0: (0, 0, 0), 0.6: (-22, 25, 0), 0.85: (-22, 25, 0), 1.0: (18, -15, 0), 1.5: (0, 0, 0)},
              interp="linear")
    lanza.rot("arm_right", {0: (0, 0, 0), 0.6: (-150, 0, 25), 0.85: (-165, 0, 25), 1.0: (-70, 0, 0),
                            1.5: (0, 0, 0)}, interp="linear")
    lanza.rot("forearm_right", {0: (0, 0, 0), 0.6: (-50, 0, 0), 0.85: (-60, 0, 0), 1.0: (0, 0, 0),
                                1.5: (0, 0, 0)}, interp="linear")
    lanza.rot("arm_left", {0: (0, 0, 0), 0.6: (-80, 0, -10), 0.85: (-85, 0, -10), 1.0: (10, 0, -15),
                           1.5: (0, 0, 0)}, interp="linear")
    lanza.rot("leg_right", {0: (0, 0, 0), 0.6: (14, 0, 0), 1.0: (-12, 0, 0), 1.5: (0, 0, 0)}, interp="linear")
    lanza.rot("leg_left", {0: (0, 0, 0), 0.6: (-14, 0, 0), 1.0: (12, 0, 0), 1.5: (0, 0, 0)}, interp="linear")
    lanza.rot("head", {0: (0, 0, 0), 0.6: (-8, -20, 0), 1.0: (6, 10, 0), 1.5: (0, 0, 0)}, interp="linear")

    # stuck / vulnerable: down on one knee, panting, bolt dimmed
    vul = m.animation("vulnerable", 2.0, "loop")
    vul.pos("body", {0: (0, -6, 0), 1.0: (0, -6.5, 0), 2.0: (0, -6, 0)})
    vul.rot("leg_right", {0: (-80, 0, 0), 2.0: (-80, 0, 0)})
    vul.rot("shin_right", {0: (80, 0, 0), 2.0: (80, 0, 0)})
    vul.rot("leg_left", {0: (-10, 0, 0), 2.0: (-10, 0, 0)})
    vul.rot("shin_left", {0: (88, 0, 0), 2.0: (88, 0, 0)})
    vul.rot("torso", {0: (22, 0, 0), 1.0: (26, 0, 0), 2.0: (22, 0, 0)})
    vul.rot("head", {0: (18, 0, 0), 1.0: (24, 0, 0), 2.0: (18, 0, 0)})
    vul.rot("arm_right", {0: (-20, 0, 8), 1.0: (-16, 0, 8), 2.0: (-20, 0, 8)})
    vul.rot("arm_left", {0: (-55, 0, -10), 1.0: (-50, 0, -10), 2.0: (-55, 0, -10)})
    vul.scale("bolt", {0: (0.7, 0.7, 0.7), 2.0: (0.7, 0.7, 0.7)})

    death = m.animation("death", 2.5, "once")
    death_fall(death, direction="back")
    death.rot("arm_right", {0: (0, 0, 0), 0.6: (-60, 0, 30), 1.5: (-120, 0, 40), 2.5: (-120, 0, 40)},
              interp="linear")
    death.rot("arm_left", {0: (0, 0, 0), 0.6: (-60, 0, -30), 1.5: (-115, 0, -40), 2.5: (-115, 0, -40)},
              interp="linear")
    death.rot("head", {0: (0, 0, 0), 0.5: (-30, 0, 0), 1.5: (10, 20, 0), 2.5: (10, 20, 0)}, interp="linear")
    death.scale("bolt", {0: (1, 1, 1), 0.6: (1.5, 1.5, 1.5), 1.2: (0.01, 0.01, 0.01), 2.5: (0.01, 0.01, 0.01)},
                interp="linear")


# =====================================================================================
# ELECTRIC CLOUD (minion)
# =====================================================================================
CLOUD = "#c9ccd8"
CLOUD_L = "#eef0f8"
CLOUD_D = "#6c7088"
CLOUD_DD = "#3e4256"


def _puff(cube, base=CLOUD):
    cube.paint(base, noise=0.05, jitter=0.15)
    cube.up.lighten(0.12)
    cube.down.fill(CLOUD_D).noise(0.06)
    for f in cube.sides():
        f.rect(0, f.h - 2, f.w, 2, mix(base, CLOUD_D, 0.6))
        f.spots(CLOUD_L, n=3, size=(1, 2), h=max(1, f.h - 3))
    return cube


def build_cloud():
    m = Model("bigcasares_myth_zeus_cloud", texture_size=(128, 128))
    body = m.bone("body", pivot=(0, 20, 0))
    puffs = m.bone("puffs", body, pivot=(0, 26, 0))
    arm_r = m.bone("arm_right", body, pivot=(5, 16, -1))
    arm_l = m.bone("arm_left", body, pivot=(-5, 16, -1))

    core = _puff(m.cube(body, "core", (-9, 15, -7), (9, 25, 7)))
    cn = core.north
    cn.eyes(9, 3, w=3, h=2, gap=4, glow="#ffe95a", socket=CLOUD_DD, brow=CLOUD_DD)
    cn.rect(5, 7, 8, 1, CLOUD_DD).pixel(6, 6, CLOUD_DD).pixel(9, 6, CLOUD_DD).pixel(12, 6, CLOUD_DD)
    cn.pixel(7, 7, "#ffe95a").pixel(10, 7, "#ffe95a")
    belly = m.cube(body, "underside", (-8, 13, -6), (8, 15.5, 6))
    belly.paint(CLOUD_D, jitter=0.15)
    for f in belly.sides():
        f.jagged(CLOUD_DD, edge="bottom", depth=(1, 2))
    top = _puff(m.cube(puffs, "puff_top", (-5, 24, -4.5), (4, 30, 4.5)), CLOUD_L)
    top.north.spots(CLOUD, n=4)
    side = _puff(m.cube(puffs, "puff_right", (7, 17, -5), (12, 23.5, 4)))
    side.mirror_x(puffs, "puff_left")
    back = _puff(m.cube(puffs, "puff_back", (-6, 17, 5), (7, 24, 9)))
    front = _puff(m.cube(puffs, "puff_front", (2, 23, -6.5), (8, 27, -1)), CLOUD_L)
    del back, front
    sparks = core.overlay(0.6, "static")
    for f in sparks.sides():
        for x in range(1, f.w, 5):
            f.line(x, f.h - 1, x + 1, f.h - 3, "#fff79a").pixel(x + 2, f.h - 4, "#ffe14a")
    # dangling lightning "arms" (attack like a zombie: they swing forward)
    bolt_r = m.plane(arm_r, "bolt_right", (5, 0, 0), (5, 15, -8))
    _bolt_shape(bolt_r.front, half=1)
    bolt_r2 = m.plane(arm_r, "bolt_right_b", (1, 0, -4), (9, 15, -4))
    _bolt_shape(bolt_r2.front, half=1)
    bolt_l = bolt_r.mirror_x(arm_l, "bolt_left")
    bolt_l2 = bolt_r2.mirror_x(arm_l, "bolt_left_b")
    del bolt_l, bolt_l2

    idle = m.animation("idle", 2.0, "loop")
    idle.pos("body", {0: (0, 0, 0), 1.0: (0, 1.2, 0), 2.0: (0, 0, 0)})
    idle.scale("puffs", {0: (1, 1, 1), 1.0: (1.05, 1.03, 1.05), 2.0: (1, 1, 1)})
    idle.rot("arm_right", {0: (0, 0, 4), 1.0: (6, 0, -4), 2.0: (0, 0, 4)})
    idle.rot("arm_left", {0: (0, 0, -4), 1.0: (6, 0, 4), 2.0: (0, 0, -4)})

    walk = m.animation("walk", 1.0, "loop")
    walk.pos("body", {0: (0, 0, 0), 0.5: (0, 1.5, 0), 1.0: (0, 0, 0)})
    walk.rot("body", {0: (10, -4, 0), 0.5: (10, 4, 0), 1.0: (10, -4, 0)})
    walk.rot("arm_right", {0: (25, 0, 0), 0.5: (40, 0, 0), 1.0: (25, 0, 0)})
    walk.rot("arm_left", {0: (40, 0, 0), 0.5: (25, 0, 0), 1.0: (40, 0, 0)})

    attack = m.animation("attack", 0.6, "once")
    attack.rot("arm_right", {0: (0, 0, 0), 0.2: (40, 0, 0), 0.35: (-95, 0, 0), 0.6: (0, 0, 0)}, interp="linear")
    attack.rot("arm_left", {0: (0, 0, 0), 0.2: (40, 0, 0), 0.35: (-95, 0, 0), 0.6: (0, 0, 0)}, interp="linear")
    attack.rot("body", {0: (0, 0, 0), 0.2: (-10, 0, 0), 0.35: (18, 0, 0), 0.6: (0, 0, 0)}, interp="linear")
    attack.scale("puffs", {0: (1, 1, 1), 0.35: (1.15, 1.1, 1.15), 0.6: (1, 1, 1)}, interp="linear")

    death = m.animation("death", 0.8, "once")
    death.scale("body", {0: (1, 1, 1), 0.2: (1.25, 1.2, 1.25), 0.8: (0.05, 0.05, 0.05)}, interp="linear")
    death.rot("body", {0: (0, 0, 0), 0.8: (0, 180, 0)}, interp="linear")
    death.pos("body", {0: (0, 0, 0), 0.8: (0, -8, 0)}, interp="linear")

    _require(m, CLOUD_ANIMATIONS)
    return m


# =====================================================================================
# SPEAR (prop)
# =====================================================================================
def build_spear():
    m = Model("bigcasares_myth_zeus_spear", texture_size=(128, 64), bone_budget=(8, 12), cube_budget=(16, 24))
    # pivot at the tip so "stuck" can sink the tip into the ground and lift the butt
    spear = m.bone("spear", pivot=(0, 6, -26))
    shaft = m.cube(spear, "shaft", (-0.75, 5.25, -22), (0.75, 6.75, 20))
    shaft.paint("#7a5230", jitter=0.2)
    for f in shaft.faces("east", "west", "up", "down"):
        f.stripe(mix("#7a5230", "#000", 0.3), every=7, axis="v", offset=3)
    bands = m.cube(spear, "bands", (-1, 5, -22), (1, 7, -18))
    bands.paint(GOLD, jitter=0.1)
    for f in bands.sides():
        f.rect(0, 0, f.w, 1, GOLD_L)
    socket = m.cube(spear, "socket", (-1.25, 4.75, -24), (1.25, 7.25, -21.5))
    socket.paint("#c8a040", jitter=0.1)
    butt = m.cube(spear, "butt", (-1, 5, 19.5), (1, 7, 22.5))
    butt.paint(GOLD, jitter=0.1)
    head_v = m.plane(spear, "head_v", (0, 1.5, -34), (0, 10.5, -23.5))
    face = head_v.front  # east face: x grows toward -Z? draw a symmetric leaf blade
    w, h = face.w, face.h
    for x in range(w):
        t = x / max(1, w - 1)
        half = (h / 2) * (1 - abs(t - 0.55) / 0.55) if t <= 0.55 else (h / 2) * (1 - (t - 0.55) / 0.45)
        half = max(0.5, min(h / 2, half))
        y0, y1 = int(round(h / 2 - half)), int(round(h / 2 + half)) - 1
        face.rect(x, y0, 1, max(1, y1 - y0 + 1), "#d8dde4")
        face.pixel(x, y0, "#f6f8fb").pixel(x, max(y0, y1), "#7c838c")
    face.line(0, h // 2, w - 1, h // 2, mix("#d8dde4", "#000", 0.25)).noise(0.04)
    head_h = m.plane(spear, "head_h", (-4.5, 6, -34), (4.5, 6, -23.5))
    fh = head_h.front
    for y in range(fh.h):
        t = y / max(1, fh.h - 1)
        half = (fh.w / 2) * min(1.0, t * 1.8) * (1 - max(0.0, t - 0.6) / 0.4 * 0.6)
        half = max(0.5, half)
        x0, x1 = int(round(fh.w / 2 - half)), int(round(fh.w / 2 + half)) - 1
        fh.rect(x0, y, max(1, x1 - x0 + 1), 1, "#cfd5dc")
    fh.noise(0.05)
    glow = m.plane(spear, "spark", (0, 2, -20), (0, 10, 12))
    gf = glow.front
    for x in range(2, gf.w - 2, 6):
        gf.line(x, 1, x + 2, 3, "#fff79a").line(x + 2, 3, x + 1, 5, "#ffe14a").line(x + 1, 5, x + 3, 7, "#fff79a")

    idle = m.animation("idle", 0.6, "loop")
    idle.scale("spear", {0: (1, 1, 1), 0.3: (1, 1, 1.02), 0.6: (1, 1, 1)})
    stuck = m.animation("stuck", 1.0, "hold")
    # move the tip (pivot) to the anchor and sink it a little, then lift the butt end
    stuck.pos("spear", {0: (0, -3, 26), 1.0: (0, -3, 26)}, interp="linear")
    stuck.rot("spear", {0: (58, 0, 0), 0.1: (52, 0, 0), 0.2: (60, 0, 0), 0.3: (56, 0, 0), 1.0: (58, 0, 0)})
    death = m.animation("death", 0.6, "once")
    death.pos("spear", {0: (0, -3, 26), 0.6: (0, -10, 26)}, interp="linear")
    death.rot("spear", {0: (58, 0, 0), 0.6: (58, 0, 0)}, interp="linear")
    death.scale("spear", {0: (1, 1, 1), 0.6: (0.05, 0.05, 0.05)}, interp="linear")
    _require(m, ("idle", "stuck", "death"))
    return m


MODELS = {
    "bigcasares_myth_zeus": build_zeus,
    "bigcasares_myth_zeus_cloud": build_cloud,
    "bigcasares_myth_zeus_spear": build_spear,
}
