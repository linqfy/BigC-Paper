"""Enma, mythic boss of the Japanese dungeon, plus his mirror prop.

* ``bigcasares_myth_enma``         ~3.8 blocks: red-faced king and judge of the dead in crimson court robes
  with gold trim, black beard, judge's cap with the gold 王 plaque, side wings and bead tassels, and a big
  lacquered shaku (sceptre tablet) in the right hand.
* ``bigcasares_myth_enma_mirror``  the Johari mirror (jouhari no kagami): a round bronze mirror with a flame
  halo hanging from a torii-like wooden stand.

Style follows japanese.py / boss_zeus.py: few varied cuboids, detail painted, relief from overlays and alpha
planes.  Model faces north (-Z); the creature's right is +X.  Left limbs mirror the right ones.
"""
import math
import sys
from pathlib import Path

sys.path.insert(0, str(Path(__file__).resolve().parents[1]))
from bbkit import Model, death_fall, idle_breathe, mix, walk_biped  # noqa: E402

REQUIRED_ANIMATIONS = ("idle", "death")  # the mirror is a prop; Enma checks his own clips below

SKIN = "#c43a2c"
SKIN_D = "#8a2219"
SKIN_L = "#e2614a"
BEARD = "#1d1418"
BEARD_L = "#3a2a30"
ROBE = "#8e1b1f"
ROBE_D = "#5a0f14"
ROBE_L = "#b0332f"
GOLD = "#d9a53a"
GOLD_D = "#8f6418"
GOLD_L = "#ffe08a"
LACQUER = "#17121a"
LACQUER_L = "#3a3040"
INNER = "#e9e1cf"
JADE = "#3f9a6a"
JADE_L = "#8fe0b0"
EYE = "#ffd23a"
IVORY = "#e8dcc0"
IVORY_D = "#b8a888"

# the 王 plaque (7x7): gold character on deep red inside a dark gold frame
KING = [
    "bbbbbbb",
    "bGGGGGb",
    "brrGrrb",
    "brGGGrb",
    "brrGrrb",
    "bGGGGGb",
    "bbbbbbb",
]

ENMA_ANIMATIONS = ("idle", "walk", "attack", "cast", "mirror", "slam", "spin", "rage", "death")
MIRROR_ANIMATIONS = ("idle", "summon", "exposed", "death")


def _require(m, names):
    missing = [n for n in names if n not in m.animations]
    if missing:
        raise SystemExit(f"{m.name}: missing animations {missing}")


def _clouds(face, color, every=6, y0=1):
    """Small gold cloud scrolls (embroidery) scattered on a robe face."""
    for y in range(y0, face.h - 2, every):
        shift = (y // every) % 2 * (every // 2)
        for x in range(shift, face.w - 2, every):
            face.pixel(x, y, color).pixel(x + 1, y, color).pixel(x + 1, y + 1, color).pixel(x, y + 1, mix(color, ROBE, 0.5))
    return face


def _trim(face, rows, color=GOLD, edge=GOLD_D):
    face.rect(0, face.h - rows, face.w, rows, color)
    face.rect(0, face.h - rows, face.w, 1, GOLD_L)
    face.rect(0, face.h - 1, face.w, 1, edge)
    return face


# =====================================================================================
# ENMA
# =====================================================================================
def build_enma():
    m = Model("bigcasares_myth_enma", texture_size=(256, 256))

    body = m.bone("body", pivot=(0, 26, 0))
    skirt = m.bone("skirt", body, pivot=(0, 30, 0))
    torso = m.bone("torso", body, pivot=(0, 30, 0))
    head = m.bone("head", torso, pivot=(0, 46, -1))
    crown = m.bone("crown", head, pivot=(0, 56, -1))
    beard_b = m.bone("beard", head, pivot=(0, 47, -5))
    arm_r = m.bone("arm_right", torso, pivot=(12.5, 44, 0))
    fore_r = m.bone("forearm_right", arm_r, pivot=(12.5, 35, 0))
    shaku = m.bone("shaku", fore_r, pivot=(12.5, 24, -2))
    sleeve_r = m.bone("sleeve_right", fore_r, pivot=(12.5, 34, 0))
    arm_l = m.bone("arm_left", torso, pivot=(-12.5, 44, 0))
    fore_l = m.bone("forearm_left", arm_l, pivot=(-12.5, 35, 0))
    sleeve_l = m.bone("sleeve_left", fore_l, pivot=(-12.5, 34, 0))
    leg_r = m.bone("leg_right", body, pivot=(4.5, 26, 0))
    shin_r = m.bone("shin_right", leg_r, pivot=(4.5, 13, 0))
    leg_l = m.bone("leg_left", body, pivot=(-4.5, 26, 0))
    shin_l = m.bone("shin_left", leg_l, pivot=(-4.5, 13, 0))
    apron = m.bone("apron", skirt, pivot=(0, 29, -7))
    tails = m.bone("tails", torso, pivot=(0, 42, 6))

    # --- legs: mostly under the robe; black court boots with gold toe caps show below the hem
    thigh = m.cube(leg_r, "thigh_right", (2, 13, -3), (7.5, 26, 3))
    thigh.paint(ROBE_D, jitter=0.1)
    shin = m.cube(shin_r, "lower_leg_right", (2.25, 3, -2.75), (7.25, 13, 2.75))
    shin.paint(INNER, jitter=0.08)
    for f in shin.sides():
        f.stripe(mix(INNER, "#000", 0.12), every=3, axis="h", offset=1)
    boot = m.cube(shin_r, "boot_right", (1.75, 0, -5.5), (7.75, 5, 3))
    boot.paint(LACQUER, jitter=0.12)
    for f in boot.sides():
        f.rect(0, 0, f.w, 1, GOLD_D)
    boot.north.rect(1, 1, 4, 3, GOLD).pixel(2, 2, GOLD_L)
    boot.up.fill(LACQUER_L).noise(0.06)
    thigh.mirror_x(leg_l, "thigh_left")
    shin.mirror_x(shin_l, "lower_leg_left")
    boot.mirror_x(shin_l, "boot_left")

    # --- the robe skirt (static on the body), embroidered, wide gold hem
    robe = m.cube(skirt, "robe_skirt", (-10.5, 5, -7), (10.5, 30, 7))
    robe.paint(ROBE, jitter=0.1)
    for f in robe.sides():
        f.stripe(ROBE_D, every=4, axis="v", offset=1)
        _clouds(f, mix(GOLD, ROBE, 0.35), every=7, y0=2)
        _trim(f, 4)
        f.stripe(ROBE_L, every=4, axis="v", offset=3, h=f.h - 4)
    robe.down.fill(ROBE_D)
    robe.north.rect(9, 0, 3, robe.north.h - 4, GOLD_D).rect(10, 0, 1, robe.north.h - 4, GOLD)  # front seam
    hem = robe.overlay(0.6, "robe_hem")
    for f in hem.sides():
        f.rect(0, f.h - 5, f.w, 2, ROBE_D).jagged(ROBE_D, edge="bottom", depth=(1, 2))
    # front apron panel (bixi) with a jade disc and gold tassel border
    panel = m.plane(apron, "apron_panel", (-4.5, 8, -7.4), (4.5, 29, -7.4))
    pf = panel.front
    pf.gradient(ROBE_L, ROBE_D, steps=4)
    pf.outline(GOLD, sides="lr").rect(0, 0, pf.w, 1, GOLD)
    pf.ellipse(4.5, 6.5, 2.6, 2.6, JADE).ellipse(4.5, 6.5, 1.0, 1.0, ROBE_D).pixel(3, 5, JADE_L)
    for x in range(1, pf.w - 1, 2):
        pf.line(x, pf.h - 5, x, pf.h - 1, GOLD)
    pf.tatter(2)

    # --- torso: chest with crossed collar (white inner, black outer), gold belt with jade plaque
    chest = m.cube(torso, "chest", (-10, 29, -6.5), (10, 45, 6.5))
    chest.paint(ROBE, jitter=0.1)
    for f in chest.sides():
        _clouds(f, mix(GOLD, ROBE, 0.4), every=6, y0=3)
    cn = chest.north
    for y in range(cn.h - 5):
        half = max(0, 6 - y // 2)
        cn.rect(10 - half - 2, y, 2, 1, LACQUER).rect(10 + half, y, 2, 1, LACQUER)
        if half > 0:
            cn.rect(10 - half, y, half * 2, 1, INNER if y < 6 else ROBE)
    cn.rect(9, 0, 2, 2, SKIN_D)  # throat
    belt = chest.overlay(0.5, "belt")
    for f in belt.sides():
        f.rect(0, f.h - 5, f.w, 4, LACQUER).rect(0, f.h - 5, f.w, 1, GOLD).rect(0, f.h - 2, f.w, 1, GOLD)
        for x in range(1, f.w, 4):
            f.pixel(x, f.h - 3, GOLD_D)
    belt.north.rect(7, belt.north.h - 6, 6, 6, GOLD).rect(8, belt.north.h - 5, 4, 4, JADE).pixel(9, belt.north.h - 4,
                                                                                                    JADE_L)
    mantle = chest.overlay(1.0, "mantle")
    for f in (mantle.east, mantle.west, mantle.south):
        f.rect(0, 0, f.w, 4, ROBE_D).rect(0, 4, f.w, 1, GOLD)
    mantle.up.fill(ROBE_D).outline(GOLD).noise(0.05)
    mantle.up.clear(7, 4, 6, 5)  # neck hole
    mantle.north.rect(0, 0, 5, 4, ROBE_D).rect(15, 0, 5, 4, ROBE_D).rect(0, 4, 5, 1, GOLD).rect(15, 4, 5, 1, GOLD)
    # long ribbons hanging at the back of the cap / shoulders
    tail = m.plane(tails, "back_ribbons", (-6, 14, 6.9), (6, 42, 6.9))
    tf = tail.front
    tf.rect(1, 0, 3, tf.h, LACQUER).rect(8, 0, 3, tf.h, LACQUER)
    tf.rect(1, tf.h - 3, 3, 3, GOLD).rect(8, tf.h - 3, 3, 3, GOLD)
    tf.rect(4, 0, 4, 10, ROBE_D).rect(4, 9, 4, 1, GOLD)
    tf.noise(0.05)

    # --- head: red face, golden glaring eyes, thick brows, snarling mouth
    skull = m.cube(head, "skull", (-5.5, 45, -6.5), (5.5, 56, 4))
    skull.paint(SKIN, jitter=0.1)
    sn = skull.north
    sn.eyes(5.5, 4, w=2, h=1, gap=3, glow=EYE, socket=SKIN_D, brow=BEARD)
    sn.rect(1, 2, 3, 1, BEARD).rect(7, 2, 3, 1, BEARD)  # heavy brows
    sn.rect(5, 5, 1, 2, SKIN_D).rect(4, 7, 3, 1, SKIN_D).pixel(5, 6, SKIN_L)  # nose
    sn.rect(3, 8, 5, 2, "#2a0c0c").pixel(3, 8, IVORY).pixel(7, 8, IVORY)  # open mouth with fangs
    sn.rect(0, 0, 11, 1, BEARD)
    for f in (skull.east, skull.west):
        f.rect(0, 0, f.w, 3, BEARD).rect(f.w - 4, 3, 4, 5, BEARD)
        f.rect(3, 4, 2, 3, SKIN_D)  # ear
    skull.south.fill(BEARD).strands(BEARD_L, density=0.4, length=(2, 4))
    skull.up.fill(BEARD)
    beard = m.cube(beard_b, "beard_main", (-5.5, 39, -7.75), (5.5, 49, -4.5))
    beard.paint(BEARD, jitter=0.15)
    bn = beard.north
    bn.strands(BEARD_L, density=0.45, length=(2, 4))
    bn.clear(3, 0, 5, 3)  # mouth shows through the moustache gap
    bn.clear(0, 0, 1, 4).clear(10, 0, 1, 4)
    bn.rect(0, 2, 3, 1, BEARD).rect(8, 2, 3, 1, BEARD)  # moustache ends
    bn.jagged(BEARD, edge="bottom", depth=(1, 3))
    for f in (beard.east, beard.west):
        f.strands(BEARD_L, density=0.35, length=(2, 3))
    beard_tip = m.plane(beard_b, "beard_tip", (-4.5, 32, -7.8), (4.5, 39.5, -7.8))
    beard_tip.front.fill(BEARD).strands(BEARD_L, density=0.5, length=(1, 3)).tatter(3)

    # --- judge's cap: black lacquer with gold band, the 王 plaque, flat top board, bead tassels, side wings
    cap = m.cube(crown, "cap", (-5, 55, -6), (5, 61, 3.5))
    cap.paint(LACQUER, jitter=0.12)
    for f in cap.sides():
        f.rect(0, f.h - 2, f.w, 2, GOLD).rect(0, f.h - 2, f.w, 1, GOLD_L)
        f.stripe(LACQUER_L, every=3, axis="v", offset=1, h=f.h - 2)
    plaque = m.cube(crown, "king_plaque", (-3.5, 55.5, -7), (3.5, 62.5, -6))
    plaque.paint(GOLD_D, jitter=0.05)
    plaque.north.decal(KING, {"b": GOLD_D, "G": GOLD_L, "r": "#7a1010"})
    plaque.south.decal(KING, {"b": GOLD_D, "G": GOLD_L, "r": "#7a1010"}, flip=True)
    board = m.cube(crown, "top_board", (-7.5, 61, -10), (7.5, 62.5, 6.5))
    board.paint(LACQUER, jitter=0.1)
    board.up.outline(GOLD).stripe(LACQUER_L, every=4, axis="h", offset=2, x=1, w=board.up.w - 2)
    for f in board.sides():
        f.fill(GOLD_D).rect(0, 0, f.w, 1, GOLD)
    beads = m.plane(crown, "bead_tassels", (-7, 56, -10.1), (7, 61, -10.1))
    bf = beads.front
    for x in list(range(0, 4, 2)) + list(range(bf.w - 3, bf.w, 2)):
        for y in range(0, bf.h, 2):
            bf.pixel(x, y, JADE if (x // 2 + y // 2) % 3 else GOLD_L)
    bf.rect(0, 0, bf.w, 1, GOLD_D)  # bead strings only at the corners: the face and the plaque stay readable
    wing = m.cube(crown, "cap_wing_right", (5, 58.5, 0), (16, 59.5, 1.5))
    wing.paint(LACQUER, jitter=0.1)
    for f in wing.faces("north", "south", "up"):
        f.rect(f.w - 2, 0, 2, f.h, GOLD)
    wing.mirror_x(crown, "cap_wing_left")

    # --- arms: shoulder with mantle, forearm inside a huge sleeve, red hand
    upper = m.cube(arm_r, "upper_arm_right", (10, 34.5, -3.75), (15.5, 45, 3.75))
    upper.paint(ROBE, jitter=0.1)
    for f in upper.sides():
        _clouds(f, mix(GOLD, ROBE, 0.4), every=5, y0=2)
    upper.up.fill(ROBE_D).outline(GOLD)
    sleeve = m.cube(sleeve_r, "sleeve_cuff_right", (9.25, 25, -5), (16, 35, 5))
    sleeve.paint(ROBE, jitter=0.1)
    for f in sleeve.sides():
        f.stripe(ROBE_D, every=3, axis="v", offset=1)
        _trim(f, 3)
    sleeve.down.fill(ROBE_D).rect(2, 2, sleeve.down.w - 4, sleeve.down.h - 4, LACQUER)
    drape = m.plane(sleeve_r, "sleeve_drape_right", (16.1, 14, -5), (16.1, 33, 5))
    df = drape.front
    df.gradient(ROBE, ROBE_D, steps=4).stripe(mix(ROBE_D, "#000", 0.2), every=3, axis="v", offset=1)
    df.rect(0, df.h - 3, df.w, 3, GOLD).rect(0, df.h - 3, df.w, 1, GOLD_L)
    df.tatter(1)
    df.clear(0, 0, df.w, 2)
    hand = m.cube(fore_r, "hand_right", (10.5, 21, -2.75), (14.5, 25.5, 2.75))
    hand.paint(SKIN, jitter=0.1)
    hand.north.stripe(SKIN_D, every=2, axis="v", h=2)
    upper.mirror_x(arm_l, "upper_arm_left")
    sleeve.mirror_x(sleeve_l, "sleeve_cuff_left")
    drape.mirror_x(sleeve_l, "sleeve_drape_left")
    hand.mirror_x(fore_l, "hand_left")

    # --- the shaku: a big lacquered tablet with an ivory face, gold caps and a red cord at the grip
    tablet = m.cube(shaku, "shaku_tablet", (11, 18, -6.5), (14, 46, -4.5))
    tablet.paint(LACQUER, jitter=0.1)
    tf2 = tablet.north
    tf2.fill(IVORY).outline(GOLD).rect(1, 1, 1, tf2.h - 2, IVORY_D)
    for y in range(4, tf2.h - 6, 4):
        tf2.pixel(1, y, LACQUER).pixel(2, y + 1, LACQUER)  # painted sutra marks
    tablet.south.fill(LACQUER).outline(GOLD_D).stripe(LACQUER_L, every=5, axis="h", offset=2, x=1, w=1)
    cap_top = m.cube(shaku, "shaku_cap", (10.5, 44, -7), (14.5, 47.5, -4))
    cap_top.paint(GOLD, jitter=0.1)
    cap_top.up.fill(GOLD_L).outline(GOLD_D)
    grip = m.cube(shaku, "shaku_cord", (10.75, 21, -6.9), (14.25, 26, -4.1))
    grip.paint("#a8201c", jitter=0.12)
    for f in grip.sides():
        f.stripe("#5a0c0c", every=2, axis="h")
    tassel = m.plane(shaku, "shaku_tassel", (12.5, 12, -7.5), (12.5, 22, -3.5))
    tf3 = tassel.front
    tf3.rect(1, 0, 2, tf3.h, "#a8201c").rect(1, tf3.h - 3, 2, 3, GOLD).tatter(2)

    _enma_animations(m)
    _require(m, ENMA_ANIMATIONS)
    return m


def _enma_animations(m):
    idle = m.animation("idle", 3.0, "loop")
    idle_breathe(idle, body="body", chest="torso", head="head", arms=("arm_left", "arm_right"), amount=0.5)
    idle.rot("forearm_right", {0: (-35, 0, 0), 1.5: (-38, 0, 0), 3.0: (-35, 0, 0)})
    idle.rot("forearm_left", {0: (-20, 0, 0), 1.5: (-24, 0, 0), 3.0: (-20, 0, 0)})
    idle.rot("beard", {0: (0, 0, 0), 1.5: (4, 0, 0), 3.0: (0, 0, 0)})
    idle.rot("tails", {0: (6, 0, 0), 1.5: (10, 0, 0), 3.0: (6, 0, 0)})
    idle.rot("apron", {0: (0, 0, 0), 1.5: (-3, 0, 0), 3.0: (0, 0, 0)})

    walk = m.animation("walk", 1.6, "loop")
    walk_biped(walk, legs=("leg_left", "leg_right"), shins=("shin_left", "shin_right"), body="body", head="head",
               swing=20, bob=0.7, sway=2)
    L = walk.length
    walk.rot("arm_right", {0: (-8, 0, 0), L / 2: (6, 0, 0), L: (-8, 0, 0)})
    walk.rot("arm_left", {0: (6, 0, 0), L / 2: (-8, 0, 0), L: (6, 0, 0)})
    walk.rot("forearm_right", {0: (-35, 0, 0), L: (-35, 0, 0)})
    walk.rot("forearm_left", {0: (-20, 0, 0), L: (-20, 0, 0)})
    walk.rot("apron", {0: (-6, 0, 0), L / 2: (4, 0, 0), L: (-6, 0, 0)})
    walk.rot("tails", {0: (12, 0, 0), L / 2: (18, 0, 0), L: (12, 0, 0)})

    # point the shaku at a sinner (the aimed volleys)
    attack = m.animation("attack", 1.0, "once")
    attack.rot("arm_right", {0: (0, 0, 0), 0.35: (-60, 0, 10), 0.55: (-95, 0, 0), 0.8: (-90, 0, 0), 1.0: (0, 0, 0)},
               interp="linear")
    attack.rot("forearm_right", {0: (-35, 0, 0), 0.35: (-60, 0, 0), 0.55: (0, 0, 0), 0.8: (0, 0, 0),
                                 1.0: (-35, 0, 0)}, interp="linear")
    attack.rot("torso", {0: (0, 0, 0), 0.35: (-6, 15, 0), 0.55: (8, -10, 0), 1.0: (0, 0, 0)}, interp="linear")
    attack.rot("head", {0: (0, 0, 0), 0.55: (6, -8, 0), 1.0: (0, 0, 0)}, interp="linear")

    # both arms up, shaku high: the bullet patterns gather (0.8 s windup)
    cast = m.animation("cast", 1.6, "once")
    cast.rot("arm_right", {0: (0, 0, 0), 0.6: (-165, 0, 15), 0.85: (-170, 0, 15), 1.1: (-80, 0, 30),
                           1.6: (0, 0, 0)}, interp="linear")
    cast.rot("forearm_right", {0: (-35, 0, 0), 0.6: (-10, 0, 0), 1.6: (-35, 0, 0)}, interp="linear")
    cast.rot("arm_left", {0: (0, 0, 0), 0.6: (-150, 0, -30), 0.85: (-155, 0, -30), 1.1: (-60, 0, -45),
                          1.6: (0, 0, 0)}, interp="linear")
    cast.rot("torso", {0: (0, 0, 0), 0.6: (-12, 0, 0), 0.85: (-12, 0, 0), 1.0: (8, 0, 0), 1.6: (0, 0, 0)},
             interp="linear")
    cast.rot("head", {0: (0, 0, 0), 0.6: (-20, 0, 0), 1.0: (5, 0, 0), 1.6: (0, 0, 0)}, interp="linear")
    cast.pos("body", {0: (0, 0, 0), 0.6: (0, 1.5, 0), 0.85: (0, 1.5, 0), 1.0: (0, -0.5, 0), 1.6: (0, 0, 0)},
             interp="linear")

    # present the mirror: left palm sweeps forward, shaku raised in judgement
    mirror = m.animation("mirror", 1.5, "once")
    mirror.rot("arm_left", {0: (0, 0, 0), 0.5: (-100, 30, -20), 1.0: (-95, -10, -10), 1.5: (0, 0, 0)},
               interp="linear")
    mirror.rot("forearm_left", {0: (-20, 0, 0), 0.5: (-10, 0, 0), 1.5: (-20, 0, 0)}, interp="linear")
    mirror.rot("arm_right", {0: (0, 0, 0), 0.5: (-30, 0, 25), 1.0: (-35, 0, 25), 1.5: (0, 0, 0)}, interp="linear")
    mirror.rot("torso", {0: (0, 0, 0), 0.5: (6, -12, 0), 1.0: (6, 8, 0), 1.5: (0, 0, 0)}, interp="linear")
    mirror.rot("head", {0: (0, 0, 0), 0.5: (8, 0, 0), 1.5: (0, 0, 0)}, interp="linear")

    # two-handed overhead slam: windup until 1.1 s, impact at 1.2 s (SLAM windup = 24 ticks)
    slam = m.animation("slam", 1.8, "once")
    slam.rot("arm_right", {0: (0, 0, 0), 0.8: (-170, 0, -15), 1.1: (-175, 0, -15), 1.2: (-60, 0, -10),
                           1.5: (-55, 0, -10), 1.8: (0, 0, 0)}, interp="linear")
    slam.rot("arm_left", {0: (0, 0, 0), 0.8: (-170, 0, 15), 1.1: (-175, 0, 15), 1.2: (-60, 0, 25),
                          1.5: (-55, 0, 25), 1.8: (0, 0, 0)}, interp="linear")
    slam.rot("forearm_right", {0: (-35, 0, 0), 0.8: (-20, 0, 0), 1.2: (-5, 0, 0), 1.8: (-35, 0, 0)},
             interp="linear")
    slam.rot("torso", {0: (0, 0, 0), 0.8: (-18, 0, 0), 1.1: (-20, 0, 0), 1.2: (28, 0, 0), 1.5: (25, 0, 0),
                       1.8: (0, 0, 0)}, interp="linear")
    slam.rot("head", {0: (0, 0, 0), 0.8: (-15, 0, 0), 1.2: (-10, 0, 0), 1.8: (0, 0, 0)}, interp="linear")
    slam.pos("body", {0: (0, 0, 0), 0.8: (0, 2, 0), 1.1: (0, 2.5, 0), 1.2: (0, -4, 0), 1.5: (0, -3.5, 0),
                      1.8: (0, 0, 0)}, interp="linear")
    slam.rot("leg_right", {0: (0, 0, 0), 1.2: (-25, 0, 0), 1.5: (-25, 0, 0), 1.8: (0, 0, 0)}, interp="linear")
    slam.rot("shin_right", {0: (0, 0, 0), 1.2: (35, 0, 0), 1.5: (35, 0, 0), 1.8: (0, 0, 0)}, interp="linear")
    slam.rot("leg_left", {0: (0, 0, 0), 1.2: (15, 0, 0), 1.5: (15, 0, 0), 1.8: (0, 0, 0)}, interp="linear")
    slam.rot("shin_left", {0: (0, 0, 0), 1.2: (20, 0, 0), 1.5: (20, 0, 0), 1.8: (0, 0, 0)}, interp="linear")

    # spin: 1.0 s windup with the shaku held out, then two seconds of spinning (SPIN 20 + 40 ticks)
    spin = m.animation("spin", 3.0, "once", interp="linear")
    spin.rot("arm_right", {0: (0, 0, 0), 0.6: (-10, 0, 95), 1.0: (-5, 0, 100), 2.8: (-5, 0, 100), 3.0: (0, 0, 0)})
    spin.rot("forearm_right", {0: (-35, 0, 0), 0.6: (0, 0, 0), 2.8: (0, 0, 0), 3.0: (-35, 0, 0)})
    spin.rot("arm_left", {0: (0, 0, 0), 0.6: (-10, 0, -70), 2.8: (-10, 0, -70), 3.0: (0, 0, 0)})
    spin.rot("shaku", {0: (0, 0, 0), 0.6: (90, 0, 0), 2.8: (90, 0, 0), 3.0: (0, 0, 0)})
    keys = {0: (0, 0, 0), 0.6: (0, 40, 0), 1.0: (0, 50, 0)}
    angle = 50
    t = 1.0
    while t < 2.875:
        t = round(t + 0.125, 3)
        angle -= 90
        keys[t] = (0, angle, 0)
    keys[3.0] = (0, angle - 40, 0)
    spin.rot("body", keys)
    spin.rot("sleeve_right", {0: (0, 0, 0), 1.0: (0, 0, 30), 2.8: (0, 0, 30), 3.0: (0, 0, 0)})
    spin.rot("sleeve_left", {0: (0, 0, 0), 1.0: (0, 0, -30), 2.8: (0, 0, -30), 3.0: (0, 0, 0)})
    spin.rot("tails", {0: (0, 0, 0), 1.0: (45, 0, 0), 2.8: (45, 0, 0), 3.0: (0, 0, 0)})

    rage = m.animation("rage", 2.5, "once")
    rage.rot("arm_right", {0: (0, 0, 0), 0.5: (-160, 0, 20), 1.8: (-165, 0, 20), 2.5: (0, 0, 0)})
    rage.rot("arm_left", {0: (0, 0, 0), 0.5: (-30, 0, -85), 1.8: (-30, 0, -90), 2.5: (0, 0, 0)})
    rage.rot("forearm_left", {0: (-20, 0, 0), 0.5: (-70, 0, 0), 1.8: (-70, 0, 0), 2.5: (-20, 0, 0)})
    rage.rot("torso", {0: (0, 0, 0), 0.5: (-16, 0, 0), 0.8: (-12, 4, 0), 1.0: (-16, -4, 0), 1.2: (-12, 4, 0),
                       1.8: (-16, 0, 0), 2.5: (0, 0, 0)})
    rage.rot("head", {0: (0, 0, 0), 0.5: (-35, 0, 0), 1.8: (-35, 0, 0), 2.5: (0, 0, 0)})
    rage.rot("beard", {0: (0, 0, 0), 0.5: (-15, 0, 0), 1.8: (-15, 0, 0), 2.5: (0, 0, 0)})
    rage.pos("body", {0: (0, 0, 0), 0.5: (0, 2, 0), 1.8: (0, 2, 0), 2.5: (0, 0, 0)})
    rage.scale("shaku", {0: (1, 1, 1), 0.5: (1.25, 1.25, 1.25), 1.8: (1.25, 1.25, 1.25), 2.5: (1, 1, 1)})
    rage.rot("tails", {0: (5, 0, 0), 0.5: (40, 0, 0), 1.8: (35, 0, 0), 2.5: (5, 0, 0)})

    death = m.animation("death", 2.5, "once")
    death_fall(death, direction="back")
    death.rot("arm_right", {0: (0, 0, 0), 0.6: (-60, 0, 30), 1.5: (-120, 0, 40), 2.5: (-120, 0, 40)},
              interp="linear")
    death.rot("arm_left", {0: (0, 0, 0), 0.6: (-60, 0, -30), 1.5: (-115, 0, -40), 2.5: (-115, 0, -40)},
              interp="linear")
    death.rot("head", {0: (0, 0, 0), 0.5: (-30, 0, 0), 1.5: (10, 20, 0), 2.5: (10, 20, 0)}, interp="linear")
    death.pos("shaku", {0: (0, 0, 0), 0.8: (0, 0, 0), 1.6: (0, -6, -8), 2.5: (0, -6, -8)}, interp="linear")
    death.rot("crown", {0: (0, 0, 0), 0.7: (0, 0, 0), 1.4: (-35, 0, 20), 2.5: (-35, 0, 20)}, interp="linear")


# =====================================================================================
# THE JOHARI MIRROR (prop)
# =====================================================================================
BRONZE = "#b07a3a"
BRONZE_D = "#6e4a20"
BRONZE_L = "#e0b070"
WOOD = "#5a2a1e"
WOOD_D = "#3a1812"
VERMILION = "#c8361e"
GLASS = "#b8d8f0"
GLASS_D = "#6a8cb8"
GLASS_L = "#f4fbff"
FLAME = "#ff7a1e"
FLAME_D = "#c8281a"
FLAME_L = "#ffd85a"


def _disc(face, r_out, color, rim=None, rim_w=1):
    cx, cy = face.w / 2, face.h / 2
    face.ellipse(cx, cy, r_out, r_out, color)
    if rim:
        face.ellipse(cx, cy, r_out, r_out, rim)
        face.ellipse(cx, cy, r_out - rim_w, r_out - rim_w, color)
    return face


def build_mirror():
    m = Model("bigcasares_myth_enma_mirror", texture_size=(128, 128), bone_budget=(10, 14), cube_budget=(24, 32))
    stand = m.bone("stand", pivot=(0, 0, 0))
    disc = m.bone("disc", stand, pivot=(0, 44, 0))
    glass = m.bone("glass", disc, pivot=(0, 28, 0))
    halo = m.bone("halo", disc, pivot=(0, 28, 1))

    # --- stand: lacquered plinth, two vermilion posts, torii-like top beam with upturned ends
    plinth = m.cube(stand, "plinth", (-14, 0, -6), (14, 4, 6))
    plinth.paint(WOOD_D, jitter=0.12)
    for f in plinth.sides():
        f.rect(0, 0, f.w, 1, BRONZE).planks(WOOD, every=7)
    plinth.up.fill(WOOD).planks(WOOD_D, every=5).outline(BRONZE_D)
    foot = m.cube(stand, "post_foot_right", (10, 4, -3.5), (15, 7, 3.5))
    foot.paint(BRONZE_D, jitter=0.1)
    foot.up.fill(BRONZE)
    foot.mirror_x(stand, "post_foot_left")
    post = m.cube(stand, "post_right", (11, 7, -2), (14, 46, 2))
    post.paint(VERMILION, jitter=0.1)
    for f in post.sides():
        f.stripe(mix(VERMILION, "#000", 0.25), every=6, axis="h", offset=3)
        f.rect(0, 0, f.w, 2, LACQUER)
    post.mirror_x(stand, "post_left")
    beam = m.cube(stand, "top_beam", (-17, 46, -2.5), (17, 49, 2.5))
    beam.paint(LACQUER, jitter=0.1)
    for f in beam.sides():
        f.rect(0, f.h - 1, f.w, 1, VERMILION)
    beam.north.rect(15, 0, 4, 3, BRONZE).pixel(16, 1, BRONZE_L)
    tip = m.cube(stand, "beam_tip_right", (16, 47, -2.5), (19, 51, 2.5), rotation=(0, 0, 22.5), pivot=(16, 47, 0))
    tip.paint(LACQUER, jitter=0.1)
    tip.up.fill(BRONZE)
    tip.mirror_x(stand, "beam_tip_left")
    tie = m.cube(stand, "tie_beam", (-12, 42, -1.5), (12, 44, 1.5))
    tie.paint(VERMILION, jitter=0.1)
    cord = m.plane(disc, "cords", (-6, 38, -0.2), (6, 44, -0.2))
    cf = cord.front
    cf.line(1, 0, 3, cf.h - 1, VERMILION).line(10, 0, 8, cf.h - 1, VERMILION)
    cf.rect(1, 0, 2, 1, BRONZE_L).rect(9, 0, 2, 1, BRONZE_L)

    # --- the mirror: flame halo behind, bronze disc, bright glass face in front
    flames = m.plane(halo, "flame_halo", (-16, 10, 1.4), (16, 44, 1.4), double_sided=False)
    ff = flames.front
    cx, cy = ff.w / 2, ff.h / 2 + 2
    ff.ellipse(cx, cy, 15.5, 16.5, FLAME_D)
    ff.ellipse(cx, cy, 14.0, 15.0, FLAME)
    ff.ellipse(cx, cy, 12.5, 13.5, FLAME_L)
    for x in range(ff.w):
        for y in range(ff.h):
            dx, dy = (x + 0.5 - cx) / 15.5, (y + 0.5 - cy) / 16.5
            if dx * dx + dy * dy > 0.75 and (x * 7 + y * 3) % 5 in (0, 1) and y < cy:
                ff.clear(x, y, 1, 1)
    ff.jagged(FLAME_L, edge="top", depth=(0, 1))
    back = m.plane(disc, "bronze_disc", (-12.5, 15.5, 0.6), (12.5, 40.5, 0.6))
    bk = back.front
    _disc(bk, 12.4, BRONZE, rim=BRONZE_D, rim_w=1)
    bk.ellipse(bk.w / 2, bk.h / 2, 3.0, 3.0, BRONZE_L).ellipse(bk.w / 2, bk.h / 2, 1.5, 1.5, BRONZE_D)
    for i in range(8):
        a = i * math.pi / 4
        x0, y0 = bk.w / 2 + math.cos(a) * 5, bk.h / 2 + math.sin(a) * 5
        x1, y1 = bk.w / 2 + math.cos(a) * 10, bk.h / 2 + math.sin(a) * 10
        bk.line(int(x0), int(y0), int(x1), int(y1), BRONZE_D)
    bk.noise(0.05)
    rim = m.cube(disc, "rim_bottom", (-6, 14.5, -0.8), (6, 16.5, 1.2))
    rim.paint(BRONZE, jitter=0.1)
    rim.up.fill(BRONZE_L)
    face_p = m.plane(glass, "glass_face", (-11, 17, -0.6), (11, 39, -0.6))
    gf = face_p.front
    gcx, gcy = gf.w / 2, gf.h / 2
    gf.ellipse(gcx, gcy, 11.0, 11.0, BRONZE_L)
    gf.ellipse(gcx, gcy, 10.0, 10.0, GLASS_D)
    gf.ellipse(gcx, gcy - 1, 9.0, 9.0, GLASS)
    gf.ellipse(gcx - 3, gcy - 4, 3.5, 2.5, GLASS_L)
    gf.line(int(gcx) + 2, int(gcy) + 6, int(gcx) + 6, int(gcy) + 2, GLASS_L)
    gf.dither(mix(GLASS, GLASS_D, 0.5), density=0.12, pattern="noise", x=3, y=3, w=gf.w - 6, h=gf.h - 6)
    for y in range(gf.h):
        for x in range(gf.w):
            if ((x + 0.5 - gcx) / 11.0) ** 2 + ((y + 0.5 - gcy) / 11.0) ** 2 > 1.0:
                gf.clear(x, y, 1, 1)
    knob = m.cube(disc, "top_knob", (-2, 39.5, -0.5), (2, 42.5, 1.5))
    knob.paint(BRONZE, jitter=0.1)

    idle = m.animation("idle", 3.0, "loop")
    idle.rot("disc", {0: (0, 0, -1.5), 1.5: (0, 0, 1.5), 3.0: (0, 0, -1.5)})
    idle.scale("halo", {0: (1, 1, 1), 0.75: (1.04, 1.06, 1), 1.5: (1, 1, 1), 2.25: (1.05, 1.03, 1), 3.0: (1, 1, 1)})

    summon = m.animation("summon", 1.0, "loop")
    summon.scale("glass", {0: (1, 1, 1), 0.5: (1.12, 1.12, 1), 1.0: (1, 1, 1)})
    summon.scale("halo", {0: (1.1, 1.1, 1), 0.5: (1.3, 1.35, 1), 1.0: (1.1, 1.1, 1)})
    summon.rot("disc", {0: (0, 0, 0), 0.25: (-4, 0, 2), 0.5: (0, 0, 0), 0.75: (-4, 0, -2), 1.0: (0, 0, 0)})

    exposed = m.animation("exposed", 1.6, "loop")
    exposed.rot("disc", {0: (10, 0, -3), 0.8: (12, 0, 3), 1.6: (10, 0, -3)})
    exposed.scale("halo", {0: (0.75, 0.75, 1), 0.8: (0.7, 0.72, 1), 1.6: (0.75, 0.75, 1)})
    exposed.scale("glass", {0: (1, 1, 1), 0.8: (0.96, 0.96, 1), 1.6: (1, 1, 1)})

    death = m.animation("death", 1.4, "hold", interp="linear")
    death.scale("glass", {0: (1, 1, 1), 0.15: (1.3, 1.3, 1), 0.3: (0.01, 0.01, 0.01), 1.4: (0.01, 0.01, 0.01)})
    death.scale("halo", {0: (1, 1, 1), 0.2: (1.4, 1.4, 1), 0.5: (0.01, 0.01, 0.01), 1.4: (0.01, 0.01, 0.01)})
    death.rot("disc", {0: (0, 0, 0), 0.3: (0, 0, 8), 0.9: (75, 0, 15), 1.4: (85, 0, 15)})
    death.pos("disc", {0: (0, 0, 0), 0.3: (0, 0, 0), 0.9: (0, -18, -4), 1.4: (0, -24, -6)})
    death.rot("stand", {0: (0, 0, 0), 0.4: (0, 0, 0), 1.0: (0, 0, -6), 1.4: (0, 0, -5)})

    _require(m, MIRROR_ANIMATIONS)
    return m


MODELS = {
    "bigcasares_myth_enma": build_enma,
    "bigcasares_myth_enma_mirror": build_mirror,
}
