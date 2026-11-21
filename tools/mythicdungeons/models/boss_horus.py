"""Horus, the falcon-headed god (boss of the Egyptian dungeon), and his Eye minion.

Same approach as the culture mobs: stretched cuboids of varied sizes, relief from overlay
layers (nemes cap, armlets, bracers, collar) and alpha planes (the huge wings on the arm
bones, the kilt apron, the pectoral).  The wings are planes hung on the arm bones: at rest
they trail behind the arms like folded wings; with the arms spread (``planeo``) they become a
horizontal falcon wing about five blocks wide.  Model faces north (-Z); the god's right is +X.
"""
import math
import sys
from pathlib import Path

sys.path.insert(0, str(Path(__file__).resolve().parents[1]))
from bbkit import Model, idle_breathe, mix, ramp, walk_biped  # noqa: E402

GOLD = "#e0b13c"
GOLD_D = "#9a6e1e"
GOLD_L = "#fbe08a"
LAPIS = "#2a4fa8"
LAPIS_D = "#162a66"
TURQ = "#2fb5a6"
CARNELIAN = "#b8321f"
SKIN = "#a8643a"
SKIN_D = "#7a4428"
LINEN = "#ece4cc"
LINEN_D = "#b8ab88"
PLUME = "#5a4634"
PLUME_D = "#33261c"
CHEEK = "#eadcb8"
BEAK = "#3b3b44"
CERE = "#e6bf3a"
EYE_GOLD = "#ffcf3a"
INK = "#120e16"

REQUIRED_ANIMATIONS = ("idle", "walk", "attack", "death")
HORUS_CLIPS = ("idle", "walk", "planeo", "dive", "attack", "rage", "cast", "vanish", "death")
EYE_CLIPS = ("idle", "walk", "attack", "death", "rise")


def _speckle(face, color, p=0.15):
    for y in range(face.h):
        for x in range(face.w):
            if face.px[y, x, 3] and face.rng.random() < p:
                face.pixel(x, y, color)
    return face


def _feathered(cube, base=PLUME, dark=PLUME_D):
    cube.paint(base, noise=0.05, jitter=0.2)
    for f in cube.faces():
        f.scales(dark, size=3, highlight=mix(base, "#fff", 0.25))
    return cube


def _gold_band(face, y, h=1):
    face.rect(0, y, face.w, h, GOLD)
    face.rect(0, y, face.w, 1, GOLD_L)
    return face


def _wing(face, *, tip=False):
    """Paint a wing plane seen from its front (east) face: x=0 is the trailing edge (+Z),
    x=w-1 the leading edge along the arm; y=0 is the shoulder side, y=h-1 the hand/tip side.
    Leading edge: gold; coverts: rows of lapis / turquoise / carnelian scales; primaries: long
    lapis feathers with turquoise shafts and gold tips, ragged trailing edge."""
    w, h = face.w, face.h
    covert = max(4, int(w * 0.42))
    first = w - covert
    for yy in range(h):
        # wing outline: the tip plane tapers toward its end (bottom rows)
        t = yy / max(1, h - 1)
        reach = w if not tip else int(round(w * (1.0 - 0.55 * t ** 1.6)))
        lane = yy // 3
        length = reach - (0, 2, 1, 3)[lane % 4]
        for xx in range(w):
            if xx < w - length:
                continue
            if xx >= first:
                band = (w - 1 - xx) // 2
                col = (GOLD, LAPIS, TURQ, LAPIS, CARNELIAN, LAPIS, TURQ)[min(6, band)]
                face.pixel(xx, yy, col)
            else:
                face.pixel(xx, yy, LAPIS if lane % 2 == 0 else mix(LAPIS, LAPIS_D, 0.45))
    # scale rims on the coverts, shafts and gold tips on the primaries
    face.scales(mix(LAPIS_D, INK, 0.3), size=3, x=first, w=covert - 2)
    for yy in range(h):
        row = [xx for xx in range(w) if face.px[yy, xx, 3]]
        if not row:
            continue
        start = row[0]
        if yy % 3 == 1:
            face.rect(start + 1, yy, max(0, first - start - 1), 1, TURQ)
        elif yy % 3 == 2:
            face.rect(start, yy, max(0, first - start), 1, mix(LAPIS_D, INK, 0.2))
        face.pixel(start, yy, GOLD).pixel(start + 1, yy, GOLD_D)
    face.rect(w - 2, 0, 2, h, GOLD).rect(w - 1, 0, 1, h, GOLD_L)
    face.noise(0.05)
    return face


def build_horus():
    m = Model("bigcasares_myth_horus", texture_size=(256, 128))

    body = m.bone("body", pivot=(0, 24, 0))
    torso = m.bone("torso", body, pivot=(0, 27, 0))
    head = m.bone("head", torso, pivot=(0, 43, -0.5))
    arm_r = m.bone("arm_right", torso, pivot=(10.5, 41, 0))
    fore_r = m.bone("forearm_right", arm_r, pivot=(10.5, 30.5, 0))
    wing_r = m.bone("wing_right", arm_r, pivot=(10.5, 41, 0))
    tip_r = m.bone("wing_tip_right", fore_r, pivot=(10.5, 30.5, 0))
    arm_l = m.bone("arm_left", torso, pivot=(-10.5, 41, 0))
    fore_l = m.bone("forearm_left", arm_l, pivot=(-10.5, 30.5, 0))
    wing_l = m.bone("wing_left", arm_l, pivot=(-10.5, 41, 0))
    tip_l = m.bone("wing_tip_left", fore_l, pivot=(-10.5, 30.5, 0))
    leg_r = m.bone("leg_right", body, pivot=(3.75, 22, 0))
    shin_r = m.bone("shin_right", leg_r, pivot=(3.75, 12, 0))
    leg_l = m.bone("leg_left", body, pivot=(-3.75, 22, 0))
    shin_l = m.bone("shin_left", leg_l, pivot=(-3.75, 12, 0))
    apron = m.bone("apron", body, pivot=(0, 24, -4.2))

    # --- legs: bare skin, gold anklets, sandals
    thigh = m.cube(leg_r, "thigh_right", (1.25, 12, -2.5), (6.25, 23, 2.5))
    thigh.paint(SKIN, jitter=0.07, noise=0.03)
    thigh.north.rect(0, 0, 1, 11, SKIN_D)
    shin = m.cube(shin_r, "shin_right_c", (1.5, 2, -2.25), (6, 12.5, 2.25))
    shin.paint(SKIN, jitter=0.07, noise=0.03)
    for f in shin.sides():
        f.rect(0, 0, f.w, 2, mix(SKIN, SKIN_D, 0.5))  # knee shadow
    anklet = shin.overlay(0.35, "anklet_right")
    for f in anklet.sides():
        _gold_band(f, f.h - 3, 2)
        f.rect(0, f.h - 2, f.w, 1, LAPIS)
    foot = m.cube(shin_r, "foot_right", (1.25, 0, -4.75), (6.25, 2, 2.5))
    foot.paint(SKIN, jitter=0.12)
    foot.north.rect(0, 1, 5, 1, "#6a4a22")
    foot.up.rect(2, 0, 1, 4, GOLD).rect(0, 4, 5, 1, GOLD)
    foot.down.fill("#6a4a22")
    thigh.mirror_x(leg_l, "thigh_left")
    shin.mirror_x(shin_l, "shin_left_c")
    anklet.mirror_x(shin_l, "anklet_left")
    foot.mirror_x(shin_l, "foot_left")

    # --- shendyt kilt, belt and apron
    kilt = m.cube(body, "kilt", (-7, 15.5, -4), (7, 25, 4))
    kilt.paint(LINEN, jitter=0.1, noise=0.03)
    for f in kilt.sides():
        f.stripe(LINEN_D, every=2, axis="v", offset=1)
        f.rect(0, f.h - 1, f.w, 1, LINEN_D)
    kilt.north.line(2, 0, 7, 8, LINEN_D).line(11, 0, 7, 8, LINEN_D)  # wrapped front fold
    belt = m.cube(body, "belt", (-7.5, 24, -4.5), (7.5, 26.5, 4.5))
    belt.paint(GOLD, jitter=0.08)
    for f in belt.sides():
        f.stripe(LAPIS, every=3, axis="v", offset=1).rect(0, 0, f.w, 1, GOLD_L)
    belt.north.rect(5, 0, 5, 3, LAPIS).rect(6, 1, 3, 1, GOLD_L).outline(GOLD_D, 5, 0, 5, 3)
    ap = m.plane(apron, "apron_front", (-3, 11, -4.6), (3, 24, -4.6))
    af = ap.front
    for yy in range(af.h):
        inset = max(0, 1 - yy // 6)
        af.rect(inset, yy, af.w - 2 * inset, 1, (LAPIS, GOLD, TURQ, GOLD, CARNELIAN, GOLD)[yy % 6])
    af.rect(0, af.h - 2, af.w, 2, GOLD).pixel(1, af.h - 1, LAPIS).pixel(4, af.h - 1, LAPIS)
    af.noise(0.04)

    # --- torso, broad collar and falcon pectoral
    waist = m.cube(torso, "waist", (-6, 26, -3.5), (6, 33.5, 3.5))
    waist.paint(SKIN, jitter=0.07, noise=0.03)
    waist.north.rect(5, 1, 2, 5, SKIN_D).rect(3, 3, 6, 1, SKIN_D)  # abdominal lines
    chest = m.cube(torso, "chest", (-8, 33, -4), (8, 42, 4))
    chest.paint(SKIN, jitter=0.07, noise=0.03)
    chest.north.rect(2, 6, 5, 1, SKIN_D).rect(9, 6, 5, 1, SKIN_D).rect(7, 4, 2, 5, SKIN_D)
    collar = chest.overlay(0.45, "usekh_collar")
    bands = (GOLD, LAPIS, GOLD, TURQ, GOLD, CARNELIAN)
    for f in collar.sides():
        for i, col in enumerate(bands):
            f.rect(0, i, f.w, 1, col)
        for xx in range(0, f.w, 2):
            f.pixel(xx, len(bands) - 1, GOLD_L)
    for i, col in enumerate(bands[:4]):  # the same rings seen from above
        collar.up.outline(col, i, i, collar.up.w - 2 * i, collar.up.h - 2 * i)
    pect = m.plane(torso, "pectoral", (-4, 30, -4.75), (4, 36, -4.75))
    pf = pect.front
    pf.decal([
        "g..gg..g",
        "lgglgglg",
        ".gtggtg.",
        "..grrg..",
        "...gg...",
        "....g...",
    ], {"g": GOLD, "l": LAPIS, "t": TURQ, "r": CARNELIAN})
    pf.pixel(3, 0, GOLD_L).pixel(4, 0, GOLD_L)

    # --- arms: shoulders with gold armlets, bracers, open hands
    shoulder = m.cube(arm_r, "shoulder_right", (8, 36.5, -3.5), (12.5, 43, 3.5))
    shoulder.paint(SKIN, jitter=0.07, noise=0.03)
    shoulder.up.rect(0, 0, 4, 7, mix(SKIN, "#fff", 0.1))
    upper = m.cube(arm_r, "upper_arm_right", (8.5, 30, -2.5), (12.5, 37, 2.5))
    upper.paint(SKIN, jitter=0.07, noise=0.03)
    armlet = upper.overlay(0.35, "armlet_right")
    for f in armlet.sides():
        _gold_band(f, 0, 2)
        f.rect(0, 1, f.w, 1, LAPIS)
    fore = m.cube(fore_r, "forearm_right_c", (8.75, 21, -2.25), (12.25, 30.5, 2.25))
    fore.paint(SKIN, jitter=0.07, noise=0.03)
    bracer = fore.overlay(0.35, "bracer_right")
    for f in bracer.sides():
        f.rect(0, 4, f.w, 5, GOLD).rect(0, 4, f.w, 1, GOLD_L).rect(0, 8, f.w, 1, GOLD_D)
        f.stripe(LAPIS, every=2, axis="v", y=5, h=3)
    hand = m.cube(fore_r, "hand_right", (9, 17, -2), (12, 21, 2))
    hand.paint(SKIN, jitter=0.12)
    hand.north.stripe(SKIN_D, every=2, axis="v", y=1, h=3)
    shoulder.mirror_x(arm_l, "shoulder_left")
    upper.mirror_x(arm_l, "upper_arm_left")
    armlet.mirror_x(arm_l, "armlet_left")
    fore.mirror_x(fore_l, "forearm_left_c")
    bracer.mirror_x(fore_l, "bracer_left")
    hand.mirror_x(fore_l, "hand_left")

    # --- wings: an inner plane on the upper arm and a long tapering plane past the hand
    w_in = m.plane(wing_r, "wing_inner_right", (12.7, 29, 1), (12.7, 42, 21))
    _wing(w_in.front)
    w_out = m.plane(tip_r, "wing_outer_right", (12.4, 6, 1), (12.4, 31, 22))
    _wing(w_out.front, tip=True)
    w_in.mirror_x(wing_l, "wing_inner_left")
    w_out.mirror_x(tip_l, "wing_outer_left")

    # --- head: falcon skull, beak, nemes cap with lappets and flare, uraeus
    neck = m.cube(head, "neck", (-2.5, 41.5, -2.5), (2.5, 45, 2.5))
    _feathered(neck, CHEEK, LINEN_D)
    skull = m.cube(head, "skull", (-4.5, 44, -5), (4.5, 53, 4))
    _feathered(skull)
    sn = skull.north
    sn.rect(0, 4, 9, 5, CHEEK)  # pale cheeks and throat
    sn.rect(1, 2, 2, 2, INK).rect(6, 2, 2, 2, INK)  # big dark falcon eyes
    sn.pixel(1, 2, EYE_GOLD).pixel(7, 2, EYE_GOLD)
    sn.rect(1, 4, 1, 4, PLUME_D).rect(7, 4, 1, 4, PLUME_D)  # the "falcon tear" malar stripes
    # side faces: x grows toward the front on the east face and toward the back on the west face
    for f in (skull.east, skull.west):
        fx = (lambda x, w=f.w: w - 1 - x) if f.name == "east" else (lambda x: x)
        f.rect(min(fx(0), fx(4)), 4, 5, 5, CHEEK)
        f.rect(min(fx(1), fx(2)), 2, 2, 2, INK).pixel(fx(1), 2, EYE_GOLD)
        f.rect(fx(2), 4, 1, 4, PLUME_D)
    beak = m.cube(head, "beak", (-1.75, 46, -8.5), (1.75, 50.5, -5))
    beak.paint(BEAK, jitter=0.1)
    beak.up.rect(0, 2, 4, 2, CERE)
    beak.north.rect(0, 0, 4, 1, CERE)
    for f in (beak.east, beak.west):
        f.rect(1, 0, 2, 2, CERE).rect(0, 3, 4, 1, mix(BEAK, INK, 0.5))
    hook = m.cube(head, "beak_hook", (-1.25, 44.5, -10), (1.25, 48, -8))
    hook.paint(mix(BEAK, INK, 0.4), jitter=0.1)
    hook.north.rect(0, 0, 3, 1, mix(BEAK, "#fff", 0.25))
    cap = skull.overlay(0.5, "nemes_cap")
    for f in (cap.up, cap.south, cap.east, cap.west):
        f.stripe(LAPIS, every=2, axis="h" if f.name != "up" else "v", offset=0)
        f.stripe(GOLD, every=2, axis="h" if f.name != "up" else "v", offset=1)
    cap.east.clear(cap.east.w - 6, 3, 6, cap.east.h)  # eyes and cheeks stay bare
    cap.west.clear(0, 3, 6, cap.west.h)
    cap.north.rect(0, 0, 9, 2, GOLD).rect(0, 0, 9, 1, GOLD_L).stripe(LAPIS, every=3, axis="v", y=0, h=2)
    flare = m.cube(head, "nemes_flare", (-6.5, 39, 1.5), (6.5, 52, 5.5))
    flare.paint(GOLD, jitter=0.06)
    for f in flare.faces():
        f.stripe(LAPIS, every=2, axis="h" if f.name not in ("up", "down") else "v", offset=1)
    flare.north.clear(3, 0, 7, 13)
    lappet = m.cube(head, "lappet_right", (3.5, 34, -4.5), (6.5, 45.5, -1.5))
    lappet.paint(GOLD, jitter=0.06)
    for f in lappet.faces():
        if f.name not in ("up", "down"):
            f.stripe(LAPIS, every=2, axis="h", offset=1)
    lappet.north.rect(0, lappet.north.h - 2, 3, 2, GOLD_L)
    lappet.mirror_x(head, "lappet_left")
    uraeus = m.cube(head, "uraeus", (-0.75, 52, -5.75), (0.75, 55.5, -4.5))
    uraeus.paint(GOLD, jitter=0.1)
    uraeus.north.rect(0, 0, 2, 1, CARNELIAN).pixel(0, 1, INK).pixel(1, 1, INK)

    # --- animations (all cosmetic: the server never waits for them)
    def wings(anim, keys_r, keys_tip=None, interp=None):
        anim.rot("wing_right", keys_r, interp=interp)
        anim.rot("wing_left", {t: (x, -y, -z) for t, (x, y, z) in keys_r.items()}, interp=interp)
        if keys_tip:
            anim.rot("wing_tip_right", keys_tip, interp=interp)
            anim.rot("wing_tip_left", {t: (x, -y, -z) for t, (x, y, z) in keys_tip.items()}, interp=interp)

    def arms(anim, keys_r, keys_fore=None, interp=None):
        anim.rot("arm_right", keys_r, interp=interp)
        anim.rot("arm_left", {t: (x, -y, -z) for t, (x, y, z) in keys_r.items()}, interp=interp)
        if keys_fore:
            anim.rot("forearm_right", keys_fore, interp=interp)
            anim.rot("forearm_left", {t: (x, -y, -z) for t, (x, y, z) in keys_fore.items()}, interp=interp)

    idle = m.animation("idle", 2.8, "loop")
    idle_breathe(idle, body="torso", head="head", amount=0.4, nod=2)
    wings(idle, {0: (0, 28, 0), 1.4: (0, 24, 3), 2.8: (0, 28, 0)})
    arms(idle, {0: (4, 0, 4), 1.4: (2, 0, 7), 2.8: (4, 0, 4)})
    idle.rot("apron", {0: (0, 0, 0), 1.4: (3, 0, 0), 2.8: (0, 0, 0)})

    walk = m.animation("walk", 1.3, "loop")
    walk_biped(walk, legs=("leg_left", "leg_right"), shins=("shin_left", "shin_right"),
               arms=("arm_left", "arm_right"), forearms=("forearm_left", "forearm_right"),
               body="body", head="head", swing=24, bob=0.7, sway=3)
    wings(walk, {0: (0, 26, 0), 0.65: (0, 22, 2), 1.3: (0, 26, 0)})
    walk.rot("apron", {0: (-6, 0, 0), 0.325: (5, 0, 0), 0.65: (-6, 0, 0), 0.975: (5, 0, 0), 1.3: (-6, 0, 0)})

    # planeo: arms spread sideways, wings flat behind them, legs trailing, slow soaring bob
    planeo = m.animation("planeo", 2.0, "loop")
    arms(planeo, {0: (0, 0, 84), 0.5: (0, 0, 80), 1.0: (0, 0, 86), 1.5: (0, 0, 81), 2.0: (0, 0, 84)},
         {0: (0, 0, 4), 1.0: (0, 0, -6), 2.0: (0, 0, 4)})
    wings(planeo, {0: (8, 0, 0), 1.0: (12, 0, 0), 2.0: (8, 0, 0)},
          {0: (0, 0, 0), 0.5: (6, 0, 0), 1.0: (0, 0, 0), 1.5: (-6, 0, 0), 2.0: (0, 0, 0)})
    planeo.rot("body", {0: (24, 0, 0), 1.0: (24, 0, 4), 2.0: (24, 0, 0)})
    planeo.pos("body", {0: (0, 0, 0), 1.0: (0, 1.2, 0), 2.0: (0, 0, 0)})
    planeo.rot("head", {0: (-22, 0, 0), 2.0: (-22, 0, 0)})
    planeo.rot("leg_right", {0: (16, 0, 2), 1.0: (20, 0, 2), 2.0: (16, 0, 2)})
    planeo.rot("leg_left", {0: (20, 0, -2), 1.0: (16, 0, -2), 2.0: (20, 0, -2)})
    planeo.rot("shin_right", {0: (10, 0, 0), 2.0: (10, 0, 0)})
    planeo.rot("shin_left", {0: (10, 0, 0), 2.0: (10, 0, 0)})
    planeo.rot("apron", {0: (24, 0, 0), 1.0: (30, 0, 0), 2.0: (24, 0, 0)})

    # dive: a held stoop, wings half swept back, body nearly horizontal (looped with a shiver)
    dive = m.animation("dive", 0.6, "loop")
    arms(dive, {0: (-10, 0, 62), 0.3: (-12, 0, 60), 0.6: (-10, 0, 62)}, {0: (0, 0, -18), 0.6: (0, 0, -18)})
    wings(dive, {0: (0, 0, 0), 0.6: (0, 0, 0)}, {0: (0, -14, 0), 0.3: (0, -12, 0), 0.6: (0, -14, 0)})
    dive.rot("body", {0: (52, 0, 0), 0.3: (54, 0, 0), 0.6: (52, 0, 0)})
    dive.rot("head", {0: (-42, 0, 0), 0.6: (-42, 0, 0)})
    dive.rot("leg_right", {0: (34, 0, 0), 0.6: (34, 0, 0)})
    dive.rot("leg_left", {0: (34, 0, 0), 0.6: (34, 0, 0)})
    dive.rot("apron", {0: (50, 0, 0), 0.6: (50, 0, 0)})

    # attack: a wing buffet, both wings beat forward around him
    atk = m.animation("attack", 1.1, "once")
    arms(atk, {0: (0, 0, 4), 0.45: (-20, 0, 110), 0.65: (-70, -40, 40), 1.1: (0, 0, 4)},
         {0: (0, 0, 0), 0.45: (0, 0, 10), 0.65: (0, 0, -20), 1.1: (0, 0, 0)}, interp="linear")
    wings(atk, {0: (0, 28, 0), 0.45: (-10, -10, 0), 0.65: (0, -40, 0), 1.1: (0, 28, 0)}, interp="linear")
    atk.rot("torso", {0: (0, 0, 0), 0.45: (-10, 0, 0), 0.65: (14, 0, 0), 1.1: (0, 0, 0)}, interp="linear")
    atk.rot("head", {0: (0, 0, 0), 0.45: (-14, 0, 0), 0.65: (10, 0, 0), 1.1: (0, 0, 0)}, interp="linear")

    # rage: screech with the head thrown back and the wings flared up
    rage = m.animation("rage", 2.0, "once")
    arms(rage, {0: (0, 0, 4), 0.4: (-40, 0, 120), 1.4: (-44, 0, 126), 2.0: (0, 0, 4)},
         {0: (0, 0, 0), 0.4: (-20, 0, 10), 1.4: (-24, 0, 12), 2.0: (0, 0, 0)})
    wings(rage, {0: (0, 28, 0), 0.4: (-20, 0, 0), 1.4: (-24, 0, 0), 2.0: (0, 28, 0)})
    rage.rot("torso", {0: (0, 0, 0), 0.4: (-16, 0, 0), 0.7: (-14, 3, 0), 1.0: (-16, -3, 0), 1.4: (-16, 0, 0),
                       2.0: (0, 0, 0)})
    rage.rot("head", {0: (0, 0, 0), 0.4: (-34, 0, 0), 0.6: (-30, 6, 0), 0.8: (-34, -6, 0), 1.4: (-30, 0, 0),
                      2.0: (0, 0, 0)})
    rage.pos("body", {0: (0, 0, 0), 0.4: (0, 1.5, 0), 1.4: (0, 1.5, 0), 2.0: (0, 0, 0)})

    # cast: the right hand reaches down to call the eyes out of the ground
    cast = m.animation("cast", 1.6, "once")
    cast.rot("arm_right", {0: (0, 0, 4), 0.5: (-150, 0, 10), 1.0: (-40, 0, 20), 1.6: (0, 0, 4)})
    cast.rot("forearm_right", {0: (0, 0, 0), 0.5: (-20, 0, 0), 1.0: (-10, 0, 0), 1.6: (0, 0, 0)})
    cast.rot("arm_left", {0: (0, 0, -4), 0.6: (-10, 0, -70), 1.6: (0, 0, -4)})
    cast.rot("wing_left", {0: (0, -28, 0), 0.6: (-10, 0, 0), 1.6: (0, -28, 0)})
    cast.rot("wing_right", {0: (0, 28, 0), 0.5: (0, 0, 0), 1.6: (0, 28, 0)})
    cast.rot("torso", {0: (0, 0, 0), 0.5: (-8, 0, 0), 1.0: (14, 0, 0), 1.6: (0, 0, 0)})
    cast.rot("head", {0: (0, 0, 0), 0.5: (-10, 0, 0), 1.0: (18, 0, 0), 1.6: (0, 0, 0)})

    # vanish: he trembles, folds in on himself and crumbles to dust (held at the end)
    vanish = m.animation("vanish", 2.5, "hold", interp="linear")
    shake = {}
    for k in range(11):
        t = 1.4 * k / 10
        shake[t] = (0, 0, (2.5 if k % 2 else -2.5) * (k / 10))
    vanish.rot("torso", {**shake, 1.6: (18, 0, 0), 2.5: (30, 0, 0)})
    arms(vanish, {0: (0, 0, 4), 1.0: (-30, 0, 60), 1.6: (-20, 0, 20), 2.5: (0, 0, 0)})
    wings(vanish, {0: (0, 28, 0), 1.0: (-20, 0, 0), 2.5: (10, 40, 0)})
    vanish.rot("head", {0: (0, 0, 0), 1.0: (-30, 0, 0), 2.0: (20, 0, 0), 2.5: (20, 0, 0)})
    vanish.scale("root", {0: (1, 1, 1), 1.4: (1, 1, 1), 1.9: (0.9, 0.55, 0.9), 2.3: (0.6, 0.12, 0.6),
                          2.5: (0.05, 0.02, 0.05)})

    death = m.animation("death", 1.0, "hold", interp="linear")
    death.scale("root", {0: (1, 1, 1), 0.5: (0.8, 0.4, 0.8), 1.0: (0.02, 0.02, 0.02)})
    death.rot("torso", {0: (0, 0, 0), 0.5: (20, 0, 0), 1.0: (20, 0, 0)})

    missing = [c for c in HORUS_CLIPS if c not in m.animations]
    assert not missing, f"missing Horus clips {missing}"
    return m


def build_eye():
    """The Eye of Horus minion: a floating wedjat eye about 0.9 blocks tall.  It is carried by a
    baby zombie, so the model hovers at the height of that small body."""
    m = Model("bigcasares_myth_horus_eye", texture_size=(64, 64))
    body = m.bone("body", pivot=(0, 8, 0))
    iris_b = m.bone("iris", body, pivot=(0, 8.5, -2.6))
    tail_b = m.bone("marking", body, pivot=(0, 5, -2.6))
    fins = m.bone("fins", body, pivot=(0, 9, 0))

    # two crossed boxes give the orb a rounded, octagonal silhouette
    ball = m.cube(body, "eyeball", (-5.5, 6, -2.5), (5.5, 11, 2.5))
    ball.paint(LAPIS, jitter=0.12)
    bn = ball.north
    bn.fill(LAPIS_D)
    bn.ellipse(5.5, 2.5, 5.4, 2.5, INK)  # kohl outline
    bn.ellipse(5.5, 2.5, 4.3, 1.6, "#f4eedc")  # almond white
    bn.rect(0, 2, 1, 1, INK).rect(9, 1, 2, 1, INK)  # the kohl wing toward the temple
    for f in (ball.east, ball.west, ball.south):
        f.stripe(GOLD, every=3, axis="h", offset=1)
    ball.up.fill(GOLD).stripe(LAPIS, every=2, axis="v")
    ball.down.fill(LAPIS_D)
    core = m.cube(body, "eye_core", (-4, 4.5, -2), (4, 12.5, 2))
    core.paint(GOLD, jitter=0.1)
    for f in core.sides():
        f.stripe(LAPIS, every=2, axis="h", offset=1)
    core.up.fill(LAPIS).outline(GOLD)
    core.down.fill(GOLD_D)
    iris = m.cube(iris_b, "iris_c", (-1.5, 7, -3), (1.5, 10, -2.6))
    iris.paint(GOLD, jitter=0.05)
    iris.north.fill(EYE_GOLD).rect(1, 0, 1, 3, INK).pixel(0, 0, GOLD_L)
    brow = m.cube(body, "brow", (-6, 12.5, -2), (6, 14, 2))
    brow.paint(LAPIS, jitter=0.1)
    for f in brow.sides():
        f.rect(0, 0, f.w, 1, GOLD)
    brow.north.stripe(GOLD_L, every=3, axis="v", offset=1, y=1, h=1)
    # wedjat markings: the teardrop line and the spiral curl hanging under the eye
    curl = m.plane(tail_b, "curl", (-5, 0.5, -2.7), (1, 5, -2.7))
    curl.front.decal([
        "....ll",
        "....l.",
        "...ll.",
        ".lll..",
        "l.....",
        "l..l..",
        ".ll...",
    ], {"l": LAPIS_D}, x=0, y=0)
    curl.front.pixel(5, 0, GOLD).pixel(3, 3, LAPIS)
    tear = m.plane(tail_b, "tear", (1, 0.5, -2.7), (3, 5, -2.7))
    tear.front.rect(1, 0, 1, 6, LAPIS_D).rect(0, 5, 2, 2, LAPIS_D).pixel(1, 6, GOLD)
    fin = m.plane(fins, "fin_right", (5.6, 6, 0), (5.6, 13, 6))
    ff = fin.front
    for yy in range(ff.h):
        n = max(1, ff.w - abs(yy - 3) * 2)
        ff.rect(ff.w - n, yy, n, 1, (GOLD, TURQ, LAPIS)[yy % 3])
    ff.rect(ff.w - 1, 0, 1, ff.h, GOLD_L)
    fin.mirror_x(fins, "fin_left")

    idle = m.animation("idle", 2.0, "loop")
    idle.pos("body", {0: (0, 0, 0), 0.5: (0, 1.2, 0), 1.0: (0, 0, 0), 1.5: (0, -0.6, 0), 2.0: (0, 0, 0)})
    idle.rot("body", {0: (0, -6, 0), 1.0: (0, 6, 0), 2.0: (0, -6, 0)})
    idle.pos("iris", {0: (0, 0, 0), 0.6: (1, 0, 0), 1.2: (-1, 0.3, 0), 2.0: (0, 0, 0)})
    idle.rot("fins", {0: (0, 0, 0), 1.0: (0, 0, 0), 2.0: (0, 0, 0)})
    idle.scale("fins", {0: (1, 1, 1), 1.0: (1, 1.12, 1), 2.0: (1, 1, 1)})

    walk = m.animation("walk", 0.8, "loop")
    walk.pos("body", {0: (0, 0, 0), 0.2: (0, 1.5, 0), 0.4: (0, 0, 0), 0.6: (0, 1.5, 0), 0.8: (0, 0, 0)})
    walk.rot("body", {0: (14, 0, -4), 0.4: (14, 0, 4), 0.8: (14, 0, -4)})
    walk.rot("marking", {0: (-10, 0, 0), 0.4: (-20, 0, 0), 0.8: (-10, 0, 0)})
    walk.scale("fins", {0: (1, 1, 1), 0.2: (1, 1.2, 1), 0.4: (1, 1, 1), 0.6: (1, 1.2, 1), 0.8: (1, 1, 1)})

    atk = m.animation("attack", 0.6, "once", interp="linear")
    atk.pos("body", {0: (0, 0, 0), 0.2: (0, 1, 3), 0.35: (0, -0.5, -6), 0.6: (0, 0, 0)})
    atk.rot("body", {0: (0, 0, 0), 0.2: (-14, 0, 0), 0.35: (20, 0, 0), 0.6: (0, 0, 0)})
    atk.scale("iris", {0: (1, 1, 1), 0.3: (1.5, 1.5, 1), 0.6: (1, 1, 1)})

    death = m.animation("death", 0.8, "hold", interp="linear")
    death.scale("body", {0: (1, 1, 1), 0.25: (1.25, 1.25, 1.25), 0.8: (0.02, 0.02, 0.02)})
    death.rot("body", {0: (0, 0, 0), 0.8: (0, 180, 0)})

    rise = m.animation("rise", 1.0, "once")
    rise.pos("body", {0: (0, -14, 0), 0.55: (0, 1.5, 0), 0.8: (0, -0.5, 0), 1.0: (0, 0, 0)})
    rise.rot("body", {0: (40, 0, 0), 0.55: (-8, 0, 0), 1.0: (0, 0, 0)})
    rise.scale("fins", {0: (1, 0.1, 1), 0.6: (1, 1, 1), 1.0: (1, 1, 1)})

    missing = [c for c in EYE_CLIPS if c not in m.animations]
    assert not missing, f"missing Eye clips {missing}"
    return m


MODELS = {
    "bigcasares_myth_horus": build_horus,
    "bigcasares_myth_horus_eye": build_eye,
}
