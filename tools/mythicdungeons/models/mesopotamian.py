"""Mesopotamian creatures for the mythic dungeons: Ereshkigal (queen of Irkalla) and a galla demon.

Cuboids of different sizes, detail painted into the textures, relief from overlay layers and
alpha planes (flounces, hair, wings).  The model faces north (-Z); the creature's right is +X.
"""
import sys
from pathlib import Path

sys.path.insert(0, str(Path(__file__).resolve().parents[1]))
from bbkit import (Model, death_fall, idle_breathe, mix, ramp,  # noqa: E402
                   walk_biped)

GOLD = "#d9aa3a"
GOLD_D = "#8e6618"
GOLD_L = "#fbe48a"
LAPIS = "#1d2f7a"
LAPIS_D = "#10193f"
LAPIS_L = "#3f62c8"
BLACK = "#15121c"


def scallop(face, period=4, depth=2):
    """Cut a scalloped hem into the bottom edge of a plane."""
    for x in range(face.w):
        k = x % period
        d = depth if k in (0, period - 1) else (1 if k in (1, period - 2) else 0)
        if d:
            face.clear(x, face.h - d, 1, d)
    return face


def gold_band(face, y, h=1, x=0, w=None):
    face.rect(x, y, w if w is not None else face.w, h, GOLD)
    face.rect(x, y, w if w is not None else face.w, 1, GOLD_L) if h > 1 else None
    face.rect(x, y + h - 1, w if w is not None else face.w, 1, GOLD_D)
    return face


# ======================================================================================
# ERESHKIGAL
# ======================================================================================
SKIN = "#cfc7c4"
SKIN_D = "#8f8590"
WING = "#2e2a3c"
WING_L = "#d8ccb4"
TALON = "#8a7a52"


def robe(cube, base=LAPIS, faces=("north", "south", "east", "west")):
    cube.paint(base, jitter=0.2, noise=0.05)
    for n in faces:
        f = cube.face(n)
        f.stripe(mix(base, "#000", 0.35), every=4, axis="v", offset=1)
        f.stripe(mix(base, "#fff", 0.1), every=4, axis="v", offset=3)
    return cube


def build_ereshkigal():
    m = Model("bigcasares_myth_ereshkigal", texture_size=(256, 256))
    body = m.bone("body", pivot=(0, 22, 0))
    skirt = m.bone("skirt", body, pivot=(0, 22, 0))
    torso = m.bone("torso", body, pivot=(0, 22, 0))
    head = m.bone("head", torso, pivot=(0, 29.5, 0))
    arm_r = m.bone("arm_right", torso, pivot=(5.2, 28.3, 0), rotation=(0, 0, 7))
    fore_r = m.bone("forearm_right", arm_r, pivot=(5.2, 20.8, 0))
    arm_l = m.bone("arm_left", torso, pivot=(-5.2, 28.3, 0), rotation=(0, 0, -7))
    fore_l = m.bone("forearm_left", arm_l, pivot=(-5.2, 20.8, 0))
    wing_r = m.bone("wing_right", torso, pivot=(3.5, 28, 3.6), rotation=(0, 22.5, 0))
    wing_l = m.bone("wing_left", torso, pivot=(-3.5, 28, 3.6), rotation=(0, -22.5, 0))
    leg_r = m.bone("leg_right", body, pivot=(2.6, 7, 0))
    leg_l = m.bone("leg_left", body, pivot=(-2.6, 7, 0))

    # --- talon legs under the hem
    leg = m.cube(leg_r, "leg_right_c", (1.9, 1.4, -1), (3.5, 8, 0.8))
    leg.paint(TALON, jitter=0.2)
    leg.north.stripe(mix(TALON, "#000", 0.3), every=2, axis="h", offset=1)
    foot = m.cube(leg_r, "foot_right", (1.4, 0, -2.2), (4.0, 1.8, 1.4))
    foot.paint(mix(TALON, "#000", 0.1), jitter=0.2)
    toes = m.cube(leg_r, "toes_right", (1.2, 0, -5), (4.2, 1.2, -2.2))
    toes.paint(TALON, jitter=0.15)
    toes.north.rect(0, 0, 1, 1, "#1a1410").rect(1, 0, 1, 1, "#1a1410").rect(2, 0, 1, 1, "#1a1410")
    toes.up.stripe("#3a3020", every=2, axis="v", offset=1)
    leg.mirror_x(leg_l, "leg_left_c")
    foot.mirror_x(leg_l, "foot_left")
    toes.mirror_x(leg_l, "toes_left")

    # --- tiered skirt
    tiers = (("tier_low", (-8, 4, -7), (8, 10, 7)),
             ("tier_mid", (-6.4, 10, -5.6), (6.4, 16.5, 5.6)),
             ("tier_high", (-4.8, 16.5, -4.2), (4.8, 22.4, 4.2)))
    for i, (name, lo, hi) in enumerate(tiers):
        t = m.cube(skirt, name, lo, hi)
        robe(t, LAPIS if i != 1 else LAPIS_D)
        for f in t.sides():
            gold_band(f, f.h - 1, 1)
            f.rect(0, 0, f.w, 1, mix(GOLD, "#000", 0.2))
            for gx in range(2, f.w - 1, 6):
                f.pixel(gx, f.h // 2, LAPIS_L).pixel(gx, f.h // 2 + 1, "#7aa0f0")
        t.up.fill(BLACK, noise=0.06)
        t.down.fill(BLACK)
    flounce_spec = (("low", 10, 6.6, 8.2, 7.1), ("mid", 16.5, 11.5, 6.7, 5.7), ("high", 22.4, 8.5, 5.0, 4.3))
    for tag, top, tall, hw, hd in flounce_spec:
        base = LAPIS_D if tag != "mid" else LAPIS
        # flat hanging flounces: front/back and the two sides, tilted away from the tier
        for side, (frm, to, rot, piv) in {
            "front": ((-hw - 0.4, top - tall, -hd - 0.1), (hw + 0.4, top - 3.5, -hd - 0.1), (-22.5, 0, 0), (0, top - 3.5, -hd - 0.1)),
            "back": ((-hw - 0.4, top - tall, hd + 0.1), (hw + 0.4, top - 3.5, hd + 0.1), (22.5, 0, 0), (0, top - 3.5, hd + 0.1)),
            "right": ((hw + 0.1, top - tall, -hd - 0.4), (hw + 0.1, top - 3.5, hd + 0.4), (0, 0, -22.5), (hw + 0.1, top - 3.5, 0)),
        }.items():
            pl = m.plane(skirt, f"flounce_{tag}_{side}", frm, to, rotation=rot, pivot=piv)
            f = pl.front
            f.gradient(mix(base, "#fff", 0.06), mix(base, "#000", 0.4), steps=4).noise(0.07)
            f.stripe(mix(base, "#000", 0.3), every=3, axis="v", offset=1)
            gold_band(f, f.h - 2, 1)
            f.rect(0, 0, f.w, 1, GOLD_D)
            scallop(f, 4, 2)
            if side == "right":
                left = pl.mirror_x(skirt, f"flounce_{tag}_left")
    # --- bodice, collar, sash
    bodice = m.cube(torso, "bodice", (-4.2, 21.6, -2.6), (4.2, 29.2, 2.6))
    bodice.paint(BLACK, jitter=0.12)
    bodice.north.rect(2, 0, 4, 8, LAPIS_D)
    bodice.north.line(2, 0, 3, 4, GOLD).line(5, 0, 4, 4, GOLD).line(3, 4, 4, 4, GOLD)
    bodice.north.rect(0, 5, 8, 1, GOLD_D)
    bodice.north.pixel(3, 6, LAPIS_L).pixel(4, 6, LAPIS_L).pixel(3, 2, GOLD_L)
    for f in (bodice.east, bodice.west, bodice.south):
        f.stripe(LAPIS_D, every=3, axis="v", offset=1)
        f.rect(0, 5, f.w, 1, GOLD_D)
    sash = m.cube(torso, "sash", (-4.6, 21.2, -3.1), (4.6, 22.8, 3.1))
    sash.paint(GOLD, jitter=0.12, tones=5)
    sash.north.rect(3, 0, 3, 2, LAPIS_L).outline(GOLD_D, 3, 0, 3, 2)
    collar = m.cube(torso, "collar", (-4.9, 28, -3.2), (4.9, 30, 3.2))
    collar.paint(GOLD, jitter=0.1, tones=5)
    for f in collar.sides():
        for gx in range(1, f.w - 1, 2):
            f.pixel(gx, 0, LAPIS_L).pixel(gx, 1, "#0a1030")
    collar.up.fill(GOLD_D, noise=0.08)
    pauld = m.cube(arm_r, "pauldron_right", (4.5, 27, -2.7), (7.9, 29.6, 2.7))
    pauld.paint(GOLD, jitter=0.12, tones=5)
    pauld.up.rivets(GOLD_L, every=2, inset=1)
    pauld.north.rect(1, 1, 2, 1, LAPIS_L)
    pauld.mirror_x(arm_l, "pauldron_left")

    # --- arms: long flared sleeves and pale hands with black nails
    sleeve = m.cube(arm_r, "sleeve_right", (4.5, 21.6, -2.1), (7.4, 27.3, 2.1))
    robe(sleeve, BLACK, faces=("north", "south", "east", "west"))
    for f in sleeve.sides():
        f.rect(0, 0, f.w, 1, GOLD)
    fore = m.cube(fore_r, "forearm_right_c", (4.6, 14.4, -2.0), (7.5, 21.8, 2.0))
    robe(fore, LAPIS, faces=("north", "south", "east", "west"))
    for f in fore.sides():
        gold_band(f, f.h - 2, 2)
    cuff = m.plane(fore_r, "sleeve_flare_right", (7.65, 11.5, -3.4), (7.65, 18, 3.4))
    cf = cuff.front
    cf.gradient(LAPIS, BLACK, steps=4).noise(0.06).stripe(mix(LAPIS, "#000", 0.4), every=3, axis="v", offset=1)
    gold_band(cf, cf.h - 2, 1)
    scallop(cf, 3, 2)
    hand = m.cube(fore_r, "hand_right", (4.9, 12, -1.3), (7.2, 14.8, 1.3))
    hand.paint(SKIN, jitter=0.1, tones=5)
    hand.north.rect(0, 2, 2, 1, "#1a1018").pixel(1, 1, SKIN_D)
    hand.down.fill("#1a1018")
    sleeve.mirror_x(arm_l, "sleeve_left")
    fore.mirror_x(fore_l, "forearm_left_c")
    cuff.mirror_x(fore_l, "sleeve_flare_left")
    hand.mirror_x(fore_l, "hand_left")

    # --- head: grim pale face, long black hair, horned crown
    skull = m.cube(head, "skull", (-3.4, 29.6, -3.4), (3.4, 36.2, 3.4))
    skull.paint(SKIN, jitter=0.08, tones=5)
    f = skull.north
    f.rect(0, 0, f.w, 1, BLACK)
    f.rect(1, 2, 2, 2, "#1a1018").rect(4, 2, 2, 2, "#1a1018")
    f.pixel(2, 3, "#9ad8ff").pixel(4, 3, "#9ad8ff")
    f.line(1, 1, 2, 1, "#2a2030").line(4, 1, 5, 1, "#2a2030")
    f.pixel(3, 4, SKIN_D).rect(2, 5, 3, 1, "#4a2036")
    f.pixel(0, 4, SKIN_D).pixel(6, 4, SKIN_D)
    f.rect(1, 4, 1, 1, mix(SKIN, "#000", 0.12)).rect(5, 4, 1, 1, mix(SKIN, "#000", 0.12))
    hair = skull.overlay(0.3, "hair_cap")
    hair.up.fill(BLACK, noise=0.08)
    hair.north.rect(0, 0, 7, 1, BLACK).rect(0, 1, 1, 4, BLACK).rect(6, 1, 1, 4, BLACK).pixel(2, 1, BLACK).pixel(4, 1, BLACK).noise(0.08)
    for ff in (hair.east, hair.west, hair.south):
        ff.fill(BLACK, noise=0.08)
    back_hair = m.plane(head, "hair_back", (-4.4, 14, 3.9), (4.4, 36, 3.9))
    bf = back_hair.front
    bf.gradient(mix(BLACK, "#fff", 0.1), BLACK, steps=4).noise(0.1)
    bf.stripe(mix(BLACK, "#4a4060", 0.5), every=3, axis="v", offset=1)
    bf.tatter(5, chance=0.9)
    side_hair = m.plane(head, "hair_side_right", (3.9, 21, -3.2), (3.9, 36, 3.6))
    sf = side_hair.front
    sf.gradient(mix(BLACK, "#fff", 0.1), BLACK, steps=4).noise(0.1)
    sf.stripe(mix(BLACK, "#4a4060", 0.5), every=3, axis="v", offset=1)
    sf.tatter(4, chance=0.9)
    side_hair.mirror_x(head, "hair_side_left")
    crown = m.cube(head, "crown_band", (-3.9, 35.4, -3.9), (3.9, 37.8, 3.9))
    crown.paint(GOLD, jitter=0.1, tones=5)
    for ff in crown.sides():
        ff.rect(0, 1, ff.w, 1, LAPIS_D)
        for gx in range(1, ff.w - 1, 3):
            ff.pixel(gx, 1, "#7aa0f0")
    crown.up.fill(GOLD_D, noise=0.1)
    cap = m.cube(head, "crown_cap", (-2.7, 37.8, -2.7), (2.7, 39.6, 2.7))
    cap.paint(GOLD, jitter=0.1, tones=5)
    for ff in cap.sides():
        ff.stripe(GOLD_D, every=2, axis="v", offset=1)
    disc = m.cube(head, "crown_disc", (-1.3, 39.6, -1.3), (1.3, 41.6, 1.3))
    disc.paint(GOLD_L, jitter=0.08)
    disc.north.rect(0, 0, 1, 1, "#fff6c0").rect(1, 1, 1, 1, GOLD)
    for tag, y0, h, out in (("a", 36.3, 4.6, 3.2), ("b", 37.6, 5.2, 2.0)):
        horn = m.cube(head, f"horn_{tag}_right", (out, y0, -1.2), (out + 2.2, y0 + h, 1.2), rotation=(0, 0, -22.5),
                      pivot=(out + 1.1, y0, 0))
        horn.paint(GOLD, jitter=0.1, tones=5)
        for ff in horn.sides():
            ff.gradient(GOLD, mix(GOLD, "#fff6c0", 0.5), steps=3).stripe(GOLD_D, every=3, axis="h", offset=1)
        horn.up.fill(GOLD_L)
        horn.mirror_x(head, f"horn_{tag}_left")

    # --- owl wings folded behind her back
    for tag, bone, sg in (("right", wing_r, 1), ("left", wing_l, -1)):
        if tag == "left":
            continue
        slab = m.cube(bone, "wing_cover_right", (3.5, 15, 3.6), (9, 29.5, 5.2))
        slab.paint(WING, jitter=0.2)
        for ff in (slab.north, slab.south):
            ff.stripe(mix(WING, "#000", 0.35), every=3, axis="h", offset=0)
            for gx in range(1, ff.w - 1, 3):
                for gy in range(2, ff.h - 2, 6):
                    ff.pixel(gx, gy, WING_L).pixel(gx, gy + 1, mix(WING_L, "#000", 0.3))
        slab.up.fill(WING)
        strut = m.cube(bone, "wing_strut_right", (3.3, 27.3, 2.8), (7, 30, 6))
        strut.paint(mix(WING, "#fff", 0.08), jitter=0.2)
        tip = m.plane(bone, "wing_feathers_right", (3.5, 5, 4.4), (9.5, 15.2, 4.4))
        tf = tip.front
        tf.gradient(WING, mix(WING, "#000", 0.3), steps=3).noise(0.08)
        tf.stripe(mix(WING, "#000", 0.35), every=3, axis="v", offset=1)
        for gx in range(1, tf.w, 3):
            tf.pixel(gx, 1, WING_L).pixel(gx, 4, WING_L)
        for x in range(tf.w):
            n = (3 - (x % 3)) * 2
            tf.clear(x, tf.h - n, 1, n)
        slab.mirror_x(wing_l, "wing_cover_left")
        strut.mirror_x(wing_l, "wing_strut_left")
        tip.mirror_x(wing_l, "wing_feathers_left")

    # --- animations
    idle = m.animation("idle", 3.4, "loop")
    idle_breathe(idle, body="body", head="head", amount=0.35, nod=2)
    L = idle.length
    idle.rot("arm_right", {0: (-6, 0, 3), L / 2: (-10, 0, 6), L: (-6, 0, 3)})
    idle.rot("arm_left", {0: (-6, 0, -3), L / 2: (-10, 0, -6), L: (-6, 0, -3)})
    idle.rot("forearm_right", {0: (-26, 0, 0), L / 2: (-30, 0, 0), L: (-26, 0, 0)})
    idle.rot("forearm_left", {0: (-26, 0, 0), L / 2: (-30, 0, 0), L: (-26, 0, 0)})
    idle.rot("skirt", {0: (0, 0, 1.5), L / 2: (0, 0, -1.5), L: (0, 0, 1.5)})
    idle.rot("wing_right", {0: (0, 0, 0), L / 2: (0, 4, 0), L: (0, 0, 0)})
    idle.rot("wing_left", {0: (0, 0, 0), L / 2: (0, -4, 0), L: (0, 0, 0)})

    walk = m.animation("walk", 1.8, "loop")
    walk_biped(walk, legs=("leg_left", "leg_right"), arms=("arm_left", "arm_right"), body="body", head="head",
               swing=14, arm_swing=6, bob=0.35, sway=2)
    L = walk.length
    walk.rot("skirt", {0: (4, 0, 1.5), L / 4: (1, 0, 0), L / 2: (4, 0, -1.5), 3 * L / 4: (1, 0, 0), L: (4, 0, 1.5)})
    walk.rot("forearm_right", {0: (-24, 0, 0), L: (-24, 0, 0)})
    walk.rot("forearm_left", {0: (-24, 0, 0), L: (-24, 0, 0)})
    walk.rot("wing_right", {0: (0, 0, 0), L / 2: (0, 5, 0), L: (0, 0, 0)})
    walk.rot("wing_left", {0: (0, 0, 0), L / 2: (0, -5, 0), L: (0, 0, 0)})
    walk.rot("torso", {0: (3, 0, 0), L: (3, 0, 0)})

    attack = m.animation("attack", 1.6, "once")
    attack.rot("arm_right", {0: (-6, 0, 3), 0.5: (-150, 0, 38), 0.8: (-160, 0, 30), 1.05: (-80, 40, -8), 1.6: (-6, 0, 3)}, interp="linear")
    attack.rot("arm_left", {0: (-6, 0, -3), 0.5: (-150, 0, -38), 0.8: (-160, 0, -30), 1.05: (-80, -40, 8), 1.6: (-6, 0, -3)}, interp="linear")
    attack.rot("forearm_right", {0: (-26, 0, 0), 0.5: (-30, 0, 0), 1.05: (-8, 0, 0), 1.6: (-26, 0, 0)}, interp="linear")
    attack.rot("forearm_left", {0: (-26, 0, 0), 0.5: (-30, 0, 0), 1.05: (-8, 0, 0), 1.6: (-26, 0, 0)}, interp="linear")
    attack.rot("torso", {0: (0, 0, 0), 0.5: (-12, 0, 0), 0.8: (-14, 0, 0), 1.05: (16, 0, 0), 1.6: (0, 0, 0)}, interp="linear")
    attack.rot("head", {0: (0, 0, 0), 0.5: (-18, 0, 0), 0.8: (-20, 0, 0), 1.05: (14, 0, 0), 1.6: (0, 0, 0)}, interp="linear")
    attack.pos("body", {0: (0, 0, 0), 0.5: (0, 0.8, 1), 1.05: (0, -0.4, -3), 1.6: (0, 0, 0)}, interp="linear")
    attack.rot("wing_right", {0: (0, 0, 0), 0.5: (0, 40, 0), 0.8: (0, 44, 0), 1.05: (0, 10, 0), 1.6: (0, 0, 0)}, interp="linear")
    attack.rot("wing_left", {0: (0, 0, 0), 0.5: (0, -40, 0), 0.8: (0, -44, 0), 1.05: (0, -10, 0), 1.6: (0, 0, 0)}, interp="linear")
    attack.rot("skirt", {0: (0, 0, 0), 0.5: (-3, 0, 0), 1.05: (8, 0, 0), 1.6: (0, 0, 0)}, interp="linear")

    death = m.animation("death", 2.4, "once")
    death_fall(death, direction="back")
    death.rot("arm_right", {0: (-6, 0, 3), 0.6: (-60, 0, 30), 1.5: (-130, 0, 40), 2.4: (-130, 0, 40)}, interp="linear")
    death.rot("arm_left", {0: (-6, 0, -3), 0.6: (-60, 0, -30), 1.5: (-130, 0, -40), 2.4: (-130, 0, -40)}, interp="linear")
    death.rot("head", {0: (0, 0, 0), 0.5: (-20, 0, 0), 1.6: (8, 20, 0), 2.4: (8, 20, 0)}, interp="linear")
    death.rot("skirt", {0: (0, 0, 0), 1.0: (6, 0, 0), 2.4: (6, 0, 0)}, interp="linear")
    return m


# ======================================================================================
# GALLA DEMON
# ======================================================================================
CLAY = "#7b6c62"
CLAY_D = "#4a3e3a"
CLAY_L = "#a39386"
ASH = "#9a8f88"
BONE = "#d8cdb0"
EMBER = "#ff9a1c"
MANE = "#2a2220"


def clay(cube, base=CLAY, cracks=3, faces=None):
    cube.paint(base, jitter=0.22, noise=0.06)
    for f in (cube.faces(*faces) if faces else cube.faces()):
        f.cracks(mix(base, "#000", 0.55), n=cracks, length=5)
        f.spots(mix(base, "#fff", 0.2), n=3, size=(1, 1))
    return cube


def build_galla():
    m = Model("bigcasares_myth_galla", texture_size=(256, 256))
    body = m.bone("body", pivot=(0, 17, 0))
    torso = m.bone("torso", body, pivot=(0, 17, 0), rotation=(-16, 0, 0))
    head = m.bone("head", torso, pivot=(0, 27, -2))
    jaw = m.bone("jaw", head, pivot=(0, 27.2, -5))
    arm_r = m.bone("arm_right", torso, pivot=(5.8, 26.5, 0))
    fore_r = m.bone("forearm_right", arm_r, pivot=(5.8, 17.5, 0))
    arm_l = m.bone("arm_left", torso, pivot=(-5.8, 26.5, 0))
    fore_l = m.bone("forearm_left", arm_l, pivot=(-5.8, 17.5, 0))
    leg_r = m.bone("leg_right", body, pivot=(3, 17, 0))
    shin_r = m.bone("shin_right", leg_r, pivot=(3, 10, -3))
    leg_l = m.bone("leg_left", body, pivot=(-3, 17, 0))
    shin_l = m.bone("shin_left", leg_l, pivot=(-3, 10, -3))
    wing_r = m.bone("wing_right", torso, pivot=(3.5, 26, 3))
    wing_l = m.bone("wing_left", torso, pivot=(-3.5, 26, 3))
    tail = m.bone("tail", body, pivot=(0, 15.5, 2.5))

    # --- hips and rag loincloth
    pelvis = clay(m.cube(body, "pelvis", (-4, 14.5, -2.6), (4, 19, 2.6)), CLAY_D)
    rag = m.plane(body, "loin_rag", (-3, 8, -2.7), (3, 16, -2.7))
    rf = rag.front
    rf.gradient("#3a2a24", "#1c1412", steps=4).noise(0.1).stripe("#2a1c18", every=3, axis="v", offset=1)
    rf.rect(1, 2, 2, 1, "#6a3a2a").tatter(4, chance=0.9)
    rag_b = m.plane(body, "loin_rag_back", (-3.5, 9, 2.7), (3.5, 16, 2.7))
    rb = rag_b.front
    rb.gradient("#3a2a24", "#1c1412", steps=4).noise(0.1).tatter(4, chance=0.9)

    # --- digitigrade legs: thigh (angled), shin (angled the other way), clawed foot
    thigh = clay(m.cube(leg_r, "thigh_right", (1.6, 9.5, -3.2), (4.4, 18, -0.2), rotation=(22.5, 0, 0)), CLAY)
    thigh.north.rect(0, 0, 1, 7, CLAY_D)
    shin = clay(m.cube(shin_r, "shin_right_c", (2.0, 1.8, -3.4), (4.0, 10.6, -1.0), rotation=(-22.5, 0, 0)), CLAY_D)
    foot = m.cube(shin_r, "foot_right", (1.6, 0, -5.6), (4.4, 2.2, -0.6))
    foot.paint(CLAY_D, jitter=0.2)
    foot.north.rect(0, 0, 1, 2, BONE).rect(1, 0, 1, 2, BONE).rect(2, 0, 1, 2, BONE)
    foot.up.stripe(mix(CLAY_D, "#000", 0.3), every=2, axis="v", offset=1)
    thigh.mirror_x(leg_l, "thigh_left")
    shin.mirror_x(shin_l, "shin_left_c")
    foot.mirror_x(shin_l, "foot_left")

    # --- gaunt torso with ribs and a hump of spine
    belly = clay(m.cube(torso, "belly", (-3.2, 15.5, -2.1), (3.2, 20.5, 2.3)), CLAY_D, cracks=2)
    chest = clay(m.cube(torso, "chest", (-4.6, 20.2, -2.9), (4.6, 28.2, 2.9)), CLAY)
    cn = chest.north
    for ry in (1, 3, 5):
        cn.rect(1, ry, 3, 1, CLAY_D).rect(5, ry, 3, 1, CLAY_D)
        cn.rect(1, ry + 1, 3, 1, mix(CLAY_L, "#fff", 0.0)).rect(5, ry + 1, 3, 1, CLAY_L)
    cn.rect(4, 0, 1, 7, CLAY_D)
    chest.up.fill(CLAY_L, noise=0.08)
    spine = m.cube(torso, "spine_ridge", (-1, 16, 2.3), (1, 28.5, 3.6))
    spine.paint(BONE, jitter=0.15)
    spine.south.stripe(mix(BONE, "#000", 0.35), every=3, axis="h", offset=1)
    scar = chest.overlay(0.2, "chest_scars")
    scar.north.line(1, 1, 7, 6, "#2a1c18").line(2, 1, 8, 6, "#2a1c18")
    scar.north.pixel(3, 4, EMBER).pixel(6, 3, EMBER)
    scar.south.line(0, 2, 8, 4, "#2a1c18")

    # --- arms: long and thin with bony claws
    arm = clay(m.cube(arm_r, "upper_arm_right", (4.4, 17.4, -1.5), (7.2, 27.2, 1.5)), CLAY)
    shoulder = m.cube(arm_r, "shoulder_right", (4.2, 25.8, -2.3), (8, 28.2, 2.3))
    shoulder.paint(CLAY_L, jitter=0.2)
    for f in shoulder.faces():
        f.spots(CLAY_D, n=3, size=(1, 2))
    forearm = clay(m.cube(fore_r, "forearm_right_c", (4.5, 8.4, -1.4), (7.1, 17.8, 1.4)), CLAY_D)
    hand = m.cube(fore_r, "hand_right", (4.2, 5.2, -1.7), (7.4, 8.8, 1.7))
    hand.paint(CLAY_D, jitter=0.2)
    claw_a = m.cube(fore_r, "claw_a_right", (4.3, 1.2, -1.6), (5.1, 5.6, -0.6), rotation=(-22.5, 0, 0))
    claw_a.paint(BONE, jitter=0.1)
    claw_a.north.rect(0, 3, 1, 2, mix(BONE, "#000", 0.35))
    claw_b = m.cube(fore_r, "claw_b_right", (6.5, 1.2, -1.6), (7.3, 5.6, -0.6), rotation=(-22.5, 0, 0))
    claw_b.paint(BONE, jitter=0.1)
    claw_c = m.cube(fore_r, "claw_c_right", (5.4, 1.0, -1.7), (6.2, 5.6, -0.7), rotation=(-22.5, 0, 0))
    claw_c.paint(BONE, jitter=0.1)
    arm.mirror_x(arm_l, "upper_arm_left")
    shoulder.mirror_x(arm_l, "shoulder_left")
    forearm.mirror_x(fore_l, "forearm_left_c")
    hand.mirror_x(fore_l, "hand_left")
    claw_a.mirror_x(fore_l, "claw_a_left")
    claw_b.mirror_x(fore_l, "claw_b_left")
    claw_c.mirror_x(fore_l, "claw_c_left")

    # --- lion head: skull, muzzle, jaw with fangs, ears, ragged mane
    skull = clay(m.cube(head, "skull", (-3.6, 26.8, -6.4), (3.6, 33.4, 0.6)), CLAY, cracks=2)
    sn = skull.north
    sn.rect(0, 0, 8, 2, CLAY_D)
    sn.eyes(4, 2, w=2, h=2, gap=2, glow=EMBER, socket="#1a100c", brow=CLAY_D)
    sn.pixel(3, 1, CLAY_D).pixel(4, 1, CLAY_D)
    muzzle = m.cube(head, "muzzle", (-2.3, 26.8, -9.8), (2.3, 30.4, -6.4))
    muzzle.paint(CLAY_L, jitter=0.15)
    muzzle.north.rect(1, 0, 3, 1, "#1a100c").pixel(0, 1, CLAY_D).pixel(4, 1, CLAY_D).rect(2, 1, 1, 2, CLAY_D)
    muzzle.north.rect(0, 3, 5, 1, "#2a1410").pixel(0, 3, BONE).pixel(4, 3, BONE)
    muzzle.down.fill("#3a1814")
    jawc = m.cube(jaw, "jaw_c", (-2.0, 25.2, -9.2), (2.0, 27.2, -5.4))
    jawc.paint(CLAY_D, jitter=0.15)
    jawc.up.fill("#3a1814").rect(0, 0, 1, jawc.up.h, BONE).rect(jawc.up.w - 1, 0, 1, jawc.up.h, BONE)
    jawc.north.rect(0, 0, 4, 1, BONE)
    ear = m.cube(head, "ear_right", (2.6, 32.4, -2.4), (4.6, 34.8, -0.4))
    ear.paint(CLAY, jitter=0.2)
    ear.north.rect(0, 1, 2, 1, "#2a1410")
    ear.mirror_x(head, "ear_left")
    mane = skull.overlay(0.5, "mane")
    mane.up.fill(MANE, noise=0.2).dither(mix(MANE, "#6a5a50", 0.5), density=0.3, pattern="noise")
    mane.east.fill(MANE, noise=0.2).jagged(MANE, edge="bottom", depth=(1, 4))
    mane.west.fill(MANE, noise=0.2).jagged(MANE, edge="bottom", depth=(1, 4))
    mane.south.fill(MANE, noise=0.2).jagged(MANE, edge="bottom", depth=(2, 5))
    mane.north.rect(0, 0, 8, 1, MANE).jagged(MANE, edge="top", depth=(1, 2)).noise(0.2)
    ruff = m.plane(head, "mane_ruff", (-6, 21, 0.9), (6, 33, 0.9))
    uf = ruff.front
    uf.gradient(MANE, mix(MANE, "#000", 0.4), steps=3).noise(0.14)
    uf.stripe(mix(MANE, "#5a4a42", 0.5), every=3, axis="v", offset=1)
    for x in range(uf.w):
        n = 2 + (x * 5) % 5
        uf.clear(x, uf.h - n, 1, n)
    uf.tatter(3, edge="left").tatter(3, edge="right")

    # --- tattered wings
    arm_w = m.cube(wing_r, "wing_arm_right", (3.5, 27.3, 2.5), (14, 29, 4))
    arm_w.paint(CLAY_D, jitter=0.2)
    arm_w.up.stripe(mix(CLAY_D, "#000", 0.3), every=3, axis="v")
    spar = m.cube(wing_r, "wing_spar_right", (13, 12, 2.7), (14.6, 28.2, 4))
    spar.paint(CLAY_D, jitter=0.2)
    mem = m.plane(wing_r, "wing_membrane_right", (3.5, 7, 3.4), (13.4, 27.3, 3.4))
    wf = mem.front  # north face: x = 0 at the body side, y = 0 at the top
    for y in range(wf.h):
        for x in range(wf.w):
            lim = wf.h * (0.45 + 0.55 * (x / (wf.w - 1)) ** 0.6)
            if y < lim:
                wf.pixel(x, y, mix("#4a3e3a", "#241c1a", y / wf.h))
    wf.noise(0.1)
    for fx in (2, 5, 8, 10):
        wf.line(0, 0, fx, int(wf.h * (0.5 + 0.4 * fx / wf.w)), mix(CLAY_D, "#000", 0.3))
    for x in range(wf.w):
        n = int(wf.rng.random() * 4)
        wf.clear(x, max(0, int(wf.h * (0.45 + 0.55 * (x / (wf.w - 1)) ** 0.6)) - n), 1, n + 1)
    wf.tatter(4, edge="right", chance=0.6)
    arm_w.mirror_x(wing_l, "wing_arm_left")
    spar.mirror_x(wing_l, "wing_spar_left")
    mem.mirror_x(wing_l, "wing_membrane_left")

    # --- tail with a coarse tuft
    tl = clay(m.cube(tail, "tail_c", (-0.8, 13.6, 2.5), (0.8, 15.6, 11)), CLAY_D, cracks=1)
    tuft = m.cube(tail, "tail_tuft", (-1.6, 12.8, 10.5), (1.6, 16.2, 14.5))
    tuft.paint(MANE, jitter=0.25)
    for f in tuft.sides():
        f.tatter(2)

    # --- animations
    idle = m.animation("idle", 2.2, "loop")
    idle_breathe(idle, body="body", head="head", arms=("arm_left", "arm_right"), tail="tail", amount=0.3, nod=3, arm_out=4)
    L = idle.length
    idle.rot("torso", {0: (0, 0, 0), L / 2: (-3, 0, 0), L: (0, 0, 0)})
    idle.rot("jaw", {0: (4, 0, 0), L / 2: (12, 0, 0), L: (4, 0, 0)})
    idle.rot("wing_right", {0: (0, 8, 0), L / 2: (0, 14, 0), L: (0, 8, 0)})
    idle.rot("wing_left", {0: (0, -8, 0), L / 2: (0, -14, 0), L: (0, -8, 0)})
    idle.rot("forearm_right", {0: (-14, 0, 0), L / 2: (-20, 0, 0), L: (-14, 0, 0)})
    idle.rot("forearm_left", {0: (-14, 0, 0), L / 2: (-20, 0, 0), L: (-14, 0, 0)})

    walk = m.animation("walk", 0.8, "loop")
    walk_biped(walk, legs=("leg_left", "leg_right"), shins=("shin_left", "shin_right"), arms=("arm_left", "arm_right"),
               forearms=("forearm_left", "forearm_right"), body="body", head="head", swing=32, arm_swing=34, knee=34,
               bob=1.0, sway=6, elbow=22)
    L = walk.length
    walk.rot("torso", {0: (-8, 0, 0), L: (-8, 0, 0)})
    walk.rot("wing_right", {0: (0, 14, 0), L / 4: (0, 24, 0), L / 2: (0, 14, 0), 3 * L / 4: (0, 24, 0), L: (0, 14, 0)})
    walk.rot("wing_left", {0: (0, -14, 0), L / 4: (0, -24, 0), L / 2: (0, -14, 0), 3 * L / 4: (0, -24, 0), L: (0, -14, 0)})
    walk.rot("tail", {0: (0, -14, 0), L / 2: (0, 14, 0), L: (0, -14, 0)})
    walk.rot("jaw", {0: (8, 0, 0), L: (8, 0, 0)})

    attack = m.animation("attack", 0.9, "once")
    attack.rot("arm_right", {0: (0, 0, 0), 0.22: (-150, 0, 25), 0.4: (-70, 0, -20), 0.58: (-60, 0, -35), 0.9: (0, 0, 0)}, interp="linear")
    attack.rot("forearm_right", {0: (0, 0, 0), 0.22: (-40, 0, 0), 0.4: (-6, 0, 0), 0.9: (0, 0, 0)}, interp="linear")
    attack.rot("arm_left", {0: (0, 0, 0), 0.3: (-40, 0, -25), 0.5: (-120, 0, 10), 0.68: (-70, 0, 30), 0.9: (0, 0, 0)}, interp="linear")
    attack.rot("forearm_left", {0: (0, 0, 0), 0.5: (-10, 0, 0), 0.9: (0, 0, 0)}, interp="linear")
    attack.rot("torso", {0: (0, 0, 0), 0.22: (-16, 0, 6), 0.4: (22, 0, -8), 0.58: (14, 0, -8), 0.9: (0, 0, 0)}, interp="linear")
    attack.rot("head", {0: (0, 0, 0), 0.22: (-18, 0, 0), 0.4: (14, 0, 0), 0.9: (0, 0, 0)}, interp="linear")
    attack.rot("jaw", {0: (4, 0, 0), 0.22: (30, 0, 0), 0.4: (44, 0, 0), 0.9: (4, 0, 0)}, interp="linear")
    attack.pos("body", {0: (0, 0, 0), 0.22: (0, 0, 2), 0.4: (0, -1, -5), 0.58: (0, -0.5, -4), 0.9: (0, 0, 0)}, interp="linear")
    attack.rot("wing_right", {0: (0, 8, 0), 0.22: (0, 40, 0), 0.4: (0, 4, 0), 0.9: (0, 8, 0)}, interp="linear")
    attack.rot("wing_left", {0: (0, -8, 0), 0.22: (0, -40, 0), 0.4: (0, -4, 0), 0.9: (0, -8, 0)}, interp="linear")

    death = m.animation("death", 1.9, "once")
    death_fall(death, direction="back")
    death.rot("arm_right", {0: (0, 0, 0), 0.5: (-60, 0, 30), 1.2: (-150, 0, 40), 1.9: (-150, 0, 40)}, interp="linear")
    death.rot("arm_left", {0: (0, 0, 0), 0.5: (-60, 0, -30), 1.2: (-130, 0, -40), 1.9: (-130, 0, -40)}, interp="linear")
    death.rot("head", {0: (0, 0, 0), 0.4: (-30, 0, 0), 1.2: (-10, 15, 0), 1.9: (-10, 15, 0)}, interp="linear")
    death.rot("jaw", {0: (4, 0, 0), 0.5: (46, 0, 0), 1.9: (46, 0, 0)}, interp="linear")
    death.rot("wing_right", {0: (0, 8, 0), 0.6: (0, 50, 0), 1.9: (0, 50, 0)}, interp="linear")
    death.rot("wing_left", {0: (0, -8, 0), 0.6: (0, -50, 0), 1.9: (0, -50, 0)}, interp="linear")
    return m


MODELS = {
    "bigcasares_myth_ereshkigal": build_ereshkigal,
    "bigcasares_myth_galla": build_galla,
}
