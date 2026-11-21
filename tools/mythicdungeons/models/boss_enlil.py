"""Enlil, lord of the wind, mythic boss of the Mesopotamian dungeon, and his Tablets of Destiny.

Enlil (~3.6 blocks): horned crown of stacked tiers, lapis curled beard, a bare right shoulder and a
flounced kaunakes robe in three tiers (fleece tufts are an overlay layer), four feathered wings
(alpha planes) and two rings of wind trails (alpha planes) that spin around the hem.  He hovers in
game: the server lifts the invisible Warden, so the model keeps its hem at y = 0.

The tablet (~1.2 blocks) is two clay halves with painted cuneiform, a glowing overlay and a halo
plane; ``break`` splits the halves and ``reform`` puts them back together.

The model faces north (-Z); the creature's right is +X.
"""
import sys
from pathlib import Path

sys.path.insert(0, str(Path(__file__).resolve().parents[1]))
from bbkit import Model, death_fall, mix  # noqa: E402

# Only idle is common to both models; build_enlil/build_tablet assert their own clip lists.
REQUIRED_ANIMATIONS = ("idle",)
ENLIL_ANIMATIONS = ("idle", "walk", "cast", "tornado", "descend", "land", "ascend", "rage", "fall", "death")
TABLET_ANIMATIONS = ("idle", "break", "reform")

SKIN = "#b9875a"
SKIN_D = "#7e5534"
SKIN_L = "#d8a77a"
ROBE = "#6f9bd1"
ROBE_D = "#3f6aa6"
WOOL = "#ece6d2"
WOOL_D = "#b9b19a"
GOLD = "#d9aa3a"
GOLD_D = "#8e6618"
GOLD_L = "#fbe48a"
LAPIS = "#1d2f7a"
LAPIS_L = "#4a74d8"
BEARD = "#18203f"
BEARD_L = "#3c56a8"
FEATHER = "#c9d3e4"
FEATHER_D = "#7d8aa6"
WIND = (226, 242, 255, 150)
WIND_HOT = (255, 255, 255, 215)
CLAY = "#a7744a"
CLAY_D = "#6b4426"
CLAY_L = "#c99a68"
GLYPH = "#3a2414"
GLOW = "#7fe6ff"


def gold_band(face, y, h=1):
    face.rect(0, y, face.w, h, GOLD)
    if h > 1:
        face.rect(0, y, face.w, 1, GOLD_L)
    face.rect(0, y + h - 1, face.w, 1, GOLD_D)
    return face


def fleece(face, rows=3, tip="#c8dcf2", root=ROBE_D):
    """Kaunakes tufts: staggered rows of 2-px wool locks with a light tip, a gap between locks."""
    for r, top in enumerate(range(0, face.h, rows)):
        for x in range(face.w):
            phase = (x + r) % 3
            if phase == 2:
                continue
            n = rows if phase == 0 else rows - 1
            for k in range(n):
                face.pixel(x, top + k, tip if k == n - 1 else (root if k == 0 else ROBE))
    return face


def wind_streaks(face, n=3, slope=0.18):
    """Translucent wind bands that rise to the right: mostly transparent so the ring reads as motion."""
    rng = face.rng
    for i in range(n):
        length = rng.randint(face.w // 2, max(face.w // 2 + 1, face.w - 2))
        x0 = rng.randint(0, max(0, face.w - length))
        y0 = int(face.h * (i + 0.8) / n)
        for k in range(length):
            y = int(round(y0 - k * slope))
            alpha = int(60 + 150 * k / max(1, length - 1))
            face.pixel(x0 + k, y, (226, 242, 255, alpha))
            if k > length // 3:
                face.pixel(x0 + k, y - 1, (240, 248, 255, alpha // 2))
        face.pixel(x0 + length - 1, int(round(y0 - (length - 1) * slope)), WIND_HOT)
    return face


def wing_shape(face, upper):
    """Alpha outline of a wing seen from the front: x = 0 is the tip (+X for the right wing)."""
    w, h = face.w, face.h
    for x in range(w):
        t = x / max(1, w - 1)  # 0 at the tip, 1 at the root
        if upper:
            top, bottom = h * 0.30 * t, h * (0.42 + 0.58 * t)
        else:
            top, bottom = h * 0.42 * (1 - t), h * (0.55 + 0.45 * (1 - t))
        for y in range(h):
            if not top <= y < bottom:
                face.clear(x, y, 1, 1)
    return face


def paint_wing(face, upper):
    face.gradient(mix(FEATHER, "#fff", 0.25), FEATHER_D, steps=4)
    face.noise(0.06)
    # coverts near the leading edge, long primaries toward the trailing edge
    face.scales(mix(FEATHER_D, "#000", 0.15), size=3, highlight="#ffffff", y=0, h=face.h // 3 if upper else face.h // 2)
    for x in range(1, face.w, 3):  # primaries: a light shaft and a soft dark edge per feather
        face.rect(x, face.h // 3, 1, face.h, mix(FEATHER, "#fff", 0.4))
        face.rect(x + 1, face.h // 3, 1, face.h, mix(FEATHER_D, FEATHER, 0.3))
    face.rect(0, 0, face.w, 1, GOLD_D) if upper else None
    wing_shape(face, upper)
    for x in range(0, face.w, 2):  # feather points along the trailing edge
        col = face.px[:, x, 3]
        ys = [y for y in range(face.h) if col[y]]
        if ys:
            face.clear(x, ys[-1], 1, 1)
    return face


def cuneiform(face, x0=1, y0=1, rows=None, colour=GLYPH):
    """Rows of wedge marks (a 2-px head and a 1-px tail) like a clay tablet."""
    rows = rows or (face.h - y0 - 1) // 3
    for r in range(rows):
        y = y0 + r * 3
        x = x0
        face.line(x0 - 1 if x0 else 0, y + 2, face.w - 2, y + 2, mix(CLAY_D, CLAY, 0.4))
        while x < face.w - 2:
            kind = face.rng.random()
            if kind < 0.45:
                face.rect(x, y, 2, 1, colour).pixel(x, y + 1, colour)
            elif kind < 0.75:
                face.pixel(x, y, colour).pixel(x + 1, y + 1, colour)
            else:
                face.rect(x, y, 1, 2, colour)
            x += face.rng.randint(2, 3)
    return face


# ======================================================================================
# ENLIL
# ======================================================================================

def build_enlil():
    m = Model("bigcasares_myth_enlil", texture_size=(256, 256))
    body = m.bone("body", pivot=(0, 28, 0))
    skirt = m.bone("skirt", body, pivot=(0, 28, 0))
    wind_low = m.bone("wind_low", body, pivot=(0, 0, 0))
    wind_high = m.bone("wind_high", body, pivot=(0, 0, 0))
    torso = m.bone("torso", body, pivot=(0, 28, 0))
    head = m.bone("head", torso, pivot=(0, 41, 0))
    beard = m.bone("beard", head, pivot=(0, 44, -4.5))
    crown = m.bone("crown", head, pivot=(0, 49, 0))
    arm_r = m.bone("arm_right", torso, pivot=(8.3, 39.5, 0), rotation=(0, 0, 6))
    fore_r = m.bone("forearm_right", arm_r, pivot=(8.3, 32, 0))
    arm_l = m.bone("arm_left", torso, pivot=(-8.3, 39.5, 0), rotation=(0, 0, -6))
    fore_l = m.bone("forearm_left", arm_l, pivot=(-8.3, 32, 0))
    wing_ur = m.bone("wing_upper_right", torso, pivot=(3, 38, 4.5), rotation=(0, 22.5, 18))
    wing_ul = m.bone("wing_upper_left", torso, pivot=(-3, 38, 4.5), rotation=(0, -22.5, -18))
    wing_lr = m.bone("wing_lower_right", torso, pivot=(3, 33, 4.5), rotation=(0, 22.5, -12))
    wing_ll = m.bone("wing_lower_left", torso, pivot=(-3, 33, 4.5), rotation=(0, -22.5, 12))

    # --- flounced robe: three tiers, wool tufts as an overlay layer, gold hems
    tiers = (("tier_low", (-10, 0, -8), (10, 10, 8)),
             ("tier_mid", (-8.5, 10, -6.8), (8.5, 19, 6.8)),
             ("tier_high", (-7, 19, -5.6), (7, 28, 5.6)))
    for name, lo, hi in tiers:
        t = m.cube(skirt, name, lo, hi)
        t.paint(ROBE, jitter=0.2, noise=0.05)
        for f in t.sides():
            f.stripe(ROBE_D, every=3, axis="v", offset=1)
            gold_band(f, f.h - 2, 2)
            for gx in range(2, f.w - 1, 5):
                f.pixel(gx, f.h - 2, LAPIS_L)
        t.up.fill(ROBE_D, noise=0.06)
        t.down.fill(mix(ROBE_D, "#000", 0.4))
        tufts = t.overlay(0.55, f"{name}_fleece")
        for f in tufts.sides():
            fleece(f)
            f.clear(0, f.h - 2, f.w, 2)  # keep the gold hem visible

    # --- torso: bare chest, robe drawn over the left shoulder, belt
    chest = m.cube(torso, "chest", (-6.5, 29.5, -4.5), (6.5, 41, 4.5))
    chest.paint(SKIN, jitter=0.12, tones=5)
    cn = chest.north
    cn.rect(2, 3, 4, 1, SKIN_D).rect(7, 3, 4, 1, SKIN_D)  # pectorals
    cn.rect(6, 4, 1, 6, SKIN_D)
    cn.rect(4, 6, 2, 1, mix(SKIN, SKIN_L, 0.5)).rect(7, 6, 2, 1, mix(SKIN, SKIN_L, 0.5))
    drape = chest.overlay(0.35, "robe_drape")
    for f, start in ((drape.north, 6), (drape.south, 0)):
        for y in range(f.h):  # diagonal edge from the left shoulder down to the right hip
            cut = start + (f.h - 1 - y) * 0.55 if f.name == "north" else f.w - 7 + y * 0.55
            for x in range(f.w):
                inside = x >= cut if f.name == "north" else x <= cut
                if inside:
                    f.pixel(x, y, ROBE if (x + y) % 4 else ROBE_D)
        f.noise(0.05)
    drape.west.fill(ROBE, noise=0.05).stripe(ROBE_D, every=3, axis="v", offset=1)
    drape.up.rect(0, 0, drape.up.w // 2, drape.up.h, ROBE)
    fleece(drape.west, rows=3)
    belt = m.cube(torso, "belt", (-7.4, 27, -5.8), (7.4, 30, 5.8))
    belt.paint(GOLD, jitter=0.1, tones=5)
    for f in belt.sides():
        f.rect(0, 1, f.w, 1, LAPIS)
        for gx in range(1, f.w - 1, 3):
            f.pixel(gx, 1, LAPIS_L)
    belt.north.rect(6, 0, 3, 3, LAPIS).pixel(7, 1, GOLD_L)

    # --- arms: bare right arm with gold armlets, left upper arm in the robe
    upper = m.cube(arm_r, "upper_arm_right", (6.5, 31.5, -2.3), (10.1, 40.5, 2.3))
    upper.paint(SKIN, jitter=0.12, tones=5)
    for f in upper.sides():
        f.rect(0, 5, f.w, 1, GOLD).rect(0, 6, f.w, 1, GOLD_D)
    upper_l = m.cube(arm_l, "upper_arm_left", (-10.1, 31.5, -2.3), (-6.5, 40.5, 2.3))
    upper_l.paint(ROBE, jitter=0.2)
    for f in upper_l.sides():
        f.stripe(ROBE_D, every=3, axis="v", offset=1)
        gold_band(f, f.h - 1, 1)
    sleeve = upper_l.overlay(0.4, "sleeve_left_fleece")
    for f in sleeve.sides():
        fleece(f)
    fore = m.cube(fore_r, "forearm_right_c", (6.7, 24.5, -2.1), (9.9, 32.3, 2.1))
    fore.paint(SKIN, jitter=0.12, tones=5)
    for f in fore.sides():
        gold_band(f, 1, 2)
        f.pixel(1, 1, LAPIS_L)
    hand = m.cube(fore_r, "hand_right", (6.8, 21, -2), (9.8, 24.6, 2))
    hand.paint(SKIN, jitter=0.1, tones=5)
    hand.north.rect(0, 2, 3, 1, SKIN_D)
    hand.down.fill(SKIN_D)
    fore.mirror_x(fore_l, "forearm_left_c")
    hand.mirror_x(fore_l, "hand_left")

    # --- head: almond eyes, heavy brows, lapis hair with a fringe of curls
    skull = m.cube(head, "skull", (-4.5, 41, -4.5), (4.5, 50, 4.5))
    skull.paint(SKIN, jitter=0.08, tones=5)
    f = skull.north
    f.rect(0, 0, 9, 2, BEARD)
    f.rect(1, 3, 3, 1, BEARD).rect(5, 3, 3, 1, BEARD)  # brows
    f.rect(1, 4, 3, 1, "#f2ead8").rect(5, 4, 3, 1, "#f2ead8")
    f.pixel(2, 4, "#101018").pixel(6, 4, "#101018")
    f.pixel(4, 4, SKIN_D).rect(4, 5, 1, 2, SKIN_D).pixel(3, 6, SKIN_D).pixel(5, 6, SKIN_D)
    f.rect(1, 7, 7, 2, BEARD).rect(3, 7, 3, 1, "#5a2a22")  # moustache over the mouth
    hair = skull.overlay(0.35, "hair_cap")
    hair.up.fill(BEARD, noise=0.08)
    hair.north.rect(0, 0, 9, 2, BEARD)
    for x in range(0, 9, 2):
        hair.north.pixel(x, 1, BEARD_L)
    for ff in (hair.east, hair.west, hair.south):
        ff.fill(BEARD, noise=0.08)
        ff.stripe(BEARD_L, every=3, axis="h", offset=2)
        ff.clear(0, ff.h - 1, ff.w, 1)
    hair.east.clear(0, 3, 4, 4)
    hair.west.clear(hair.west.w - 4, 3, 4, 4)
    chignon = m.cube(head, "hair_bun", (-3.5, 40, 4.5), (3.5, 45, 7.5))
    chignon.paint(BEARD, jitter=0.25)
    for ff in chignon.faces():
        ff.stripe(BEARD_L, every=2, axis="h", offset=1)
    chignon.south.rect(0, 2, chignon.south.w, 1, GOLD)
    ear = m.cube(head, "ear_right", (4.5, 44, -1), (5.5, 47, 1))
    ear.paint(SKIN_D, jitter=0.1)
    ear.north.pixel(0, 2, GOLD_L)
    ear.mirror_x(head, "ear_left")

    # --- the long beard: rows of spiral curls
    bc = m.cube(beard, "beard_c", (-4.2, 32, -6.8), (4.2, 44.5, -4.4))
    bc.paint(BEARD, jitter=0.15)
    bf = bc.north
    for row, y in enumerate(range(0, bf.h - 1, 3)):
        for x in range((row % 2), bf.w, 3):
            bf.pixel(x, y, BEARD_L).pixel(x + 1, y, BEARD_L).pixel(x + 1, y + 1, mix(BEARD_L, "#fff", 0.2))
            bf.pixel(x, y + 1, mix(BEARD, "#000", 0.3))
    bf.rect(0, bf.h - 3, bf.w, 1, GOLD)  # gold ring binding the tip
    for ff in (bc.east, bc.west):
        ff.stripe(BEARD_L, every=3, axis="h", offset=1)
    curls = bc.overlay(0.35, "beard_curls")
    curls.north.jagged(BEARD, edge="bottom", depth=(1, 2)).jagged(BEARD, edge="left", depth=(0, 1))
    curls.north.jagged(BEARD, edge="right", depth=(0, 1))
    for x in range(1, curls.north.w, 3):
        curls.north.pixel(x, curls.north.h - 1, BEARD_L)

    # --- horned crown: gold band, three tiers ringed by horns, a knob, and bull horns
    band = m.cube(crown, "crown_band", (-5, 49, -5), (5, 51.5, 5))
    band.paint(GOLD, jitter=0.1, tones=5)
    for ff in band.sides():
        ff.rect(0, 1, ff.w, 1, LAPIS)
        for gx in range(1, ff.w - 1, 3):
            ff.pixel(gx, 1, LAPIS_L)
    for name, lo, hi in (("crown_tier_a", (-4.6, 51.5, -4.6), (4.6, 54.5, 4.6)),
                         ("crown_tier_b", (-3.8, 54.5, -3.8), (3.8, 57.2, 3.8)),
                         ("crown_tier_c", (-2.8, 57.2, -2.8), (2.8, 59.6, 2.8))):
        tier = m.cube(crown, name, lo, hi)
        tier.paint(WOOL, jitter=0.12)
        for ff in tier.sides():
            # a pair of horns sweeping up from the centre of every side
            mid = ff.w // 2
            ff.line(mid - 1, ff.h - 1, 0, 0, GOLD).line(mid, ff.h - 1, ff.w - 1, 0, GOLD)
            ff.line(mid - 2, ff.h - 1, 0, 1, GOLD_D).line(mid + 1, ff.h - 1, ff.w - 1, 1, GOLD_D)
            ff.rect(0, ff.h - 1, ff.w, 1, GOLD_D)
        tier.up.fill(mix(WOOL, "#000", 0.1))
    knob = m.cube(crown, "crown_knob", (-1.4, 59.6, -1.4), (1.4, 61.6, 1.4))
    knob.paint(GOLD_L, jitter=0.1)
    knob.north.pixel(1, 0, "#fff6c0")
    horn = m.cube(crown, "horn_right", (5, 50, -1.1), (7.6, 52.2, 1.1))
    horn.paint(GOLD, jitter=0.1, tones=5)
    horn.up.stripe(GOLD_D, every=2, axis="v")
    tip = m.cube(crown, "horn_tip_right", (6.4, 51.8, -0.9), (8.2, 56.2, 0.9), rotation=(0, 0, -22.5),
                 pivot=(7.3, 52, 0))
    tip.paint(GOLD_L, jitter=0.1)
    for ff in tip.sides():
        ff.stripe(GOLD_D, every=2, axis="h", offset=1)
    horn.mirror_x(crown, "horn_left")
    tip.mirror_x(crown, "horn_tip_left")

    # --- four wings
    up_cov = m.cube(wing_ur, "wing_covert_upper_right", (3, 43.5, 4.2), (17, 46, 5.8))
    up_cov.paint(FEATHER, jitter=0.2)
    for ff in up_cov.faces():
        ff.scales(FEATHER_D, size=3, highlight="#ffffff")
    up_cov.north.rect(0, 0, up_cov.north.w, 1, GOLD)
    up = m.plane(wing_ur, "wing_upper_right_p", (3, 28, 5), (21, 46, 5))
    paint_wing(up.front, upper=True)
    low = m.plane(wing_lr, "wing_lower_right_p", (3, 14, 5.2), (17, 34, 5.2))
    paint_wing(low.front, upper=False)
    up_cov.mirror_x(wing_ul, "wing_covert_upper_left")
    up.mirror_x(wing_ul, "wing_upper_left_p")
    low.mirror_x(wing_ll, "wing_lower_left_p")

    # --- wind trails: two square rings of translucent streak planes that spin around the hem
    for bone, tag, r, y0, y1, n in ((wind_low, "low", 13, 1, 13, 3), (wind_high, "high", 10.5, 15, 25, 2)):
        front = m.plane(bone, f"wind_{tag}_n", (-r, y0, -r), (r, y1, -r))
        wind_streaks(front.front, n)
        back = m.plane(bone, f"wind_{tag}_s", (-r, y0, r), (r, y1, r))
        wind_streaks(back.front, n)
        east = m.plane(bone, f"wind_{tag}_e", (r, y0, -r), (r, y1, r))
        wind_streaks(east.front, n)
        west = m.plane(bone, f"wind_{tag}_w", (-r, y0, -r), (-r, y1, r))
        wind_streaks(west.front, n)

    animate_enlil(m)
    missing = [name for name in ENLIL_ANIMATIONS if name not in m.animations]
    assert not missing, f"enlil animations missing: {missing}"
    return m


def animate_enlil(m):
    # idle: the hover.  Cheap: a bob, a slow wing beat and the wind rings turning (4-fold symmetric).
    idle = m.animation("idle", 2.4, "loop")
    L = idle.length
    idle.pos("body", {0: (0, 0, 0), L / 2: (0, 1.4, 0), L: (0, 0, 0)})
    idle.rot("wing_upper_right", {0: (0, 0, 4), L / 2: (0, 0, 16), L: (0, 0, 4)})
    idle.rot("wing_upper_left", {0: (0, 0, -4), L / 2: (0, 0, -16), L: (0, 0, -4)})
    idle.rot("wing_lower_right", {0: (0, 0, -2), L / 2: (0, 0, -10), L: (0, 0, -2)})
    idle.rot("wing_lower_left", {0: (0, 0, 2), L / 2: (0, 0, 10), L: (0, 0, 2)})
    idle.rot("wind_low", {0: (0, 0, 0), L: (0, 90, 0)}, interp="linear")
    idle.rot("wind_high", {0: (0, 0, 0), L: (0, -90, 0)}, interp="linear")
    idle.rot("beard", {0: (0, 0, 0), L / 2: (-3, 0, 0), L: (0, 0, 0)})
    idle.rot("forearm_right", {0: (-18, 0, 0), L / 2: (-24, 0, 0), L: (-18, 0, 0)})
    idle.rot("forearm_left", {0: (-18, 0, 0), L / 2: (-24, 0, 0), L: (-18, 0, 0)})

    # walk: a forward glide, wings swept back, robe trailing
    walk = m.animation("walk", 1.2, "loop")
    L = walk.length
    walk.rot("body", {0: (10, 0, 0), L / 2: (12, 0, 0), L: (10, 0, 0)})
    walk.pos("body", {0: (0, 0, 0), L / 2: (0, 1.0, 0), L: (0, 0, 0)})
    walk.rot("skirt", {0: (-8, 0, 2), L / 2: (-10, 0, -2), L: (-8, 0, 2)})
    walk.rot("wing_upper_right", {0: (0, 30, 10), L / 2: (0, 36, 22), L: (0, 30, 10)})
    walk.rot("wing_upper_left", {0: (0, -30, -10), L / 2: (0, -36, -22), L: (0, -30, -10)})
    walk.rot("wing_lower_right", {0: (0, 24, 0), L / 2: (0, 28, -8), L: (0, 24, 0)})
    walk.rot("wing_lower_left", {0: (0, -24, 0), L / 2: (0, -28, 8), L: (0, -24, 0)})
    walk.rot("arm_right", {0: (20, 0, 8), L: (20, 0, 8)})
    walk.rot("arm_left", {0: (20, 0, -8), L: (20, 0, -8)})
    walk.rot("beard", {0: (-10, 0, 0), L: (-10, 0, 0)})
    walk.rot("wind_low", {0: (0, 0, 0), L: (0, 90, 0)}, interp="linear")
    walk.rot("wind_high", {0: (0, 0, 0), L: (0, -90, 0)}, interp="linear")

    # cast (gust): draw both arms back, then thrust the palms forward with the wings flung open
    cast = m.animation("cast", 1.2, "once")
    cast.rot("arm_right", {0: (0, 0, 6), 0.45: (40, -20, 30), 0.7: (-95, 10, 4), 1.2: (0, 0, 6)}, interp="linear")
    cast.rot("arm_left", {0: (0, 0, -6), 0.45: (40, 20, -30), 0.7: (-95, -10, -4), 1.2: (0, 0, -6)}, interp="linear")
    cast.rot("forearm_right", {0: (-18, 0, 0), 0.45: (-60, 0, 0), 0.7: (-5, 0, 0), 1.2: (-18, 0, 0)}, interp="linear")
    cast.rot("forearm_left", {0: (-18, 0, 0), 0.45: (-60, 0, 0), 0.7: (-5, 0, 0), 1.2: (-18, 0, 0)}, interp="linear")
    cast.rot("torso", {0: (0, 0, 0), 0.45: (-12, 0, 0), 0.7: (14, 0, 0), 1.2: (0, 0, 0)}, interp="linear")
    cast.rot("head", {0: (0, 0, 0), 0.45: (-10, 0, 0), 0.7: (8, 0, 0), 1.2: (0, 0, 0)}, interp="linear")
    cast.rot("wing_upper_right", {0: (0, 0, 4), 0.45: (0, -10, 30), 0.7: (0, 40, -10), 1.2: (0, 0, 4)}, interp="linear")
    cast.rot("wing_upper_left", {0: (0, 0, -4), 0.45: (0, 10, -30), 0.7: (0, -40, 10), 1.2: (0, 0, -4)}, interp="linear")
    cast.rot("wing_lower_right", {0: (0, 0, 0), 0.45: (0, -10, -14), 0.7: (0, 36, 6), 1.2: (0, 0, 0)}, interp="linear")
    cast.rot("wing_lower_left", {0: (0, 0, 0), 0.45: (0, 10, 14), 0.7: (0, -36, -6), 1.2: (0, 0, 0)}, interp="linear")
    cast.rot("beard", {0: (0, 0, 0), 0.7: (-18, 0, 0), 1.2: (0, 0, 0)}, interp="linear")
    cast.scale("wind_low", {0: (1, 1, 1), 0.7: (1.35, 1.0, 1.35), 1.2: (1, 1, 1)}, interp="linear")

    # tornado: arms overhead, one full turn of the body while the wind rings race
    tor = m.animation("tornado", 1.6, "once")
    tor.rot("arm_right", {0: (0, 0, 6), 0.3: (-170, 0, 20), 1.3: (-170, 0, 20), 1.6: (0, 0, 6)}, interp="linear")
    tor.rot("arm_left", {0: (0, 0, -6), 0.3: (-170, 0, -20), 1.3: (-170, 0, -20), 1.6: (0, 0, -6)}, interp="linear")
    tor.rot("body", {0: (0, 0, 0), 0.3: (0, 0, 0), 1.3: (0, 360, 0), 1.6: (0, 360, 0)}, interp="linear")
    tor.pos("body", {0: (0, 0, 0), 0.5: (0, 3, 0), 1.3: (0, 3, 0), 1.6: (0, 0, 0)})
    tor.rot("wind_low", {0: (0, 0, 0), 1.6: (0, 720, 0)}, interp="linear")
    tor.rot("wind_high", {0: (0, 0, 0), 1.6: (0, -720, 0)}, interp="linear")
    tor.scale("wind_low", {0: (1, 1, 1), 0.5: (1.2, 1.6, 1.2), 1.3: (1.2, 1.6, 1.2), 1.6: (1, 1, 1)})
    tor.rot("wing_upper_right", {0: (0, 0, 4), 0.4: (0, 0, 40), 1.3: (0, 0, 40), 1.6: (0, 0, 4)})
    tor.rot("wing_upper_left", {0: (0, 0, -4), 0.4: (0, 0, -40), 1.3: (0, 0, -40), 1.6: (0, 0, -4)})

    # descend: wings raised high to brake, arms out for balance
    des = m.animation("descend", 1.0, "once")
    des.rot("wing_upper_right", {0: (0, 0, 4), 0.4: (0, -10, 50), 1.0: (0, -10, 50)})
    des.rot("wing_upper_left", {0: (0, 0, -4), 0.4: (0, 10, -50), 1.0: (0, 10, -50)})
    des.rot("wing_lower_right", {0: (0, 0, 0), 0.4: (0, -10, 30), 1.0: (0, -10, 30)})
    des.rot("wing_lower_left", {0: (0, 0, 0), 0.4: (0, 10, -30), 1.0: (0, 10, -30)})
    des.rot("arm_right", {0: (0, 0, 6), 0.4: (-20, 0, 50), 1.0: (-20, 0, 50)})
    des.rot("arm_left", {0: (0, 0, -6), 0.4: (-20, 0, -50), 1.0: (-20, 0, -50)})
    des.rot("beard", {0: (0, 0, 0), 0.5: (-25, 0, 0), 1.0: (-25, 0, 0)})
    des.rot("skirt", {0: (0, 0, 0), 0.5: (-4, 0, 0), 1.0: (-4, 0, 0)})

    # land: impact crouch and recovery
    land = m.animation("land", 0.7, "once")
    land.pos("body", {0: (0, 0, 0), 0.15: (0, -3, 0), 0.7: (0, 0, 0)})
    land.scale("skirt", {0: (1, 1, 1), 0.15: (1.12, 0.85, 1.12), 0.7: (1, 1, 1)})
    land.rot("torso", {0: (0, 0, 0), 0.15: (14, 0, 0), 0.7: (0, 0, 0)})
    land.rot("wing_upper_right", {0: (0, -10, 50), 0.15: (0, 10, -10), 0.7: (0, 0, 4)})
    land.rot("wing_upper_left", {0: (0, 10, -50), 0.15: (0, -10, 10), 0.7: (0, 0, -4)})
    land.rot("wing_lower_right", {0: (0, -10, 30), 0.15: (0, 0, -6), 0.7: (0, 0, 0)})
    land.rot("wing_lower_left", {0: (0, 10, -30), 0.15: (0, 0, 6), 0.7: (0, 0, 0)})
    land.rot("arm_right", {0: (-20, 0, 50), 0.15: (-30, 0, 30), 0.7: (0, 0, 6)})
    land.rot("arm_left", {0: (-20, 0, -50), 0.15: (-30, 0, -30), 0.7: (0, 0, -6)})

    # ascend: a deep downbeat that throws him back up
    asc = m.animation("ascend", 1.0, "once")
    asc.rot("wing_upper_right", {0: (0, 0, 40), 0.35: (0, 0, -30), 1.0: (0, 0, 4)})
    asc.rot("wing_upper_left", {0: (0, 0, -40), 0.35: (0, 0, 30), 1.0: (0, 0, -4)})
    asc.rot("wing_lower_right", {0: (0, 0, 20), 0.35: (0, 0, -30), 1.0: (0, 0, 0)})
    asc.rot("wing_lower_left", {0: (0, 0, -20), 0.35: (0, 0, 30), 1.0: (0, 0, 0)})
    asc.pos("body", {0: (0, -2, 0), 0.35: (0, 2, 0), 1.0: (0, 0, 0)})
    asc.rot("arm_right", {0: (0, 0, 30), 0.35: (0, 0, 10), 1.0: (0, 0, 6)})
    asc.rot("arm_left", {0: (0, 0, -30), 0.35: (0, 0, -10), 1.0: (0, 0, -6)})

    # rage: fists raised, head thrown back, wings fully spread, the wind roaring
    rage = m.animation("rage", 2.0, "once")
    rage.rot("arm_right", {0: (0, 0, 6), 0.4: (-150, 0, 40), 1.6: (-150, 0, 40), 2.0: (0, 0, 6)}, interp="linear")
    rage.rot("arm_left", {0: (0, 0, -6), 0.4: (-150, 0, -40), 1.6: (-150, 0, -40), 2.0: (0, 0, -6)}, interp="linear")
    rage.rot("forearm_right", {0: (-18, 0, 0), 0.4: (-40, 0, 0), 1.6: (-40, 0, 0), 2.0: (-18, 0, 0)})
    rage.rot("forearm_left", {0: (-18, 0, 0), 0.4: (-40, 0, 0), 1.6: (-40, 0, 0), 2.0: (-18, 0, 0)})
    rage.rot("head", {0: (0, 0, 0), 0.4: (-28, 0, 0), 0.8: (-24, 6, 0), 1.2: (-28, -6, 0), 1.6: (-28, 0, 0), 2.0: (0, 0, 0)})
    rage.rot("torso", {0: (0, 0, 0), 0.4: (-10, 0, 0), 1.6: (-10, 0, 0), 2.0: (0, 0, 0)})
    rage.rot("wing_upper_right", {0: (0, 0, 4), 0.4: (0, -20, 45), 1.6: (0, -20, 45), 2.0: (0, 0, 4)})
    rage.rot("wing_upper_left", {0: (0, 0, -4), 0.4: (0, 20, -45), 1.6: (0, 20, -45), 2.0: (0, 0, -4)})
    rage.rot("wing_lower_right", {0: (0, 0, 0), 0.4: (0, -20, -30), 1.6: (0, -20, -30), 2.0: (0, 0, 0)})
    rage.rot("wing_lower_left", {0: (0, 0, 0), 0.4: (0, 20, 30), 1.6: (0, 20, 30), 2.0: (0, 0, 0)})
    rage.rot("wind_low", {0: (0, 0, 0), 2.0: (0, 720, 0)}, interp="linear")
    rage.rot("wind_high", {0: (0, 0, 0), 2.0: (0, -720, 0)}, interp="linear")
    rage.scale("wind_low", {0: (1, 1, 1), 0.4: (1.4, 1.3, 1.4), 1.6: (1.4, 1.3, 1.4), 2.0: (1, 1, 1)})
    rage.pos("body", {0: (0, 0, 0), 0.5: (0.6, 1, 0), 0.6: (-0.6, 1, 0), 0.7: (0.6, 1, 0), 0.8: (-0.6, 1, 0),
                      1.6: (0, 1, 0), 2.0: (0, 0, 0)}, interp="linear")

    # fall: the tablets break and he slumps, wings drooping; held until the server stands him up
    fall = m.animation("fall", 1.4, "hold")
    fall.rot("body", {0: (0, 0, 0), 0.3: (-10, 0, 0), 0.9: (24, 0, 4), 1.4: (22, 0, 4)})
    fall.pos("body", {0: (0, 0, 0), 0.3: (0, 1, 0), 0.9: (0, -7, -2), 1.4: (0, -6.5, -2)})
    fall.scale("skirt", {0: (1, 1, 1), 0.9: (1.15, 0.6, 1.15), 1.4: (1.15, 0.62, 1.15)})
    fall.rot("head", {0: (0, 0, 0), 0.9: (22, -8, 0), 1.4: (20, -8, 0)})
    fall.rot("arm_right", {0: (0, 0, 6), 0.9: (-30, 0, 24), 1.4: (-28, 0, 22)})
    fall.rot("arm_left", {0: (0, 0, -6), 0.9: (-30, 0, -24), 1.4: (-28, 0, -22)})
    fall.rot("wing_upper_right", {0: (0, 0, 4), 0.9: (0, 30, -40), 1.4: (0, 30, -38)})
    fall.rot("wing_upper_left", {0: (0, 0, -4), 0.9: (0, -30, 40), 1.4: (0, -30, 38)})
    fall.rot("wing_lower_right", {0: (0, 0, 0), 0.9: (0, 20, -20), 1.4: (0, 20, -20)})
    fall.rot("wing_lower_left", {0: (0, 0, 0), 0.9: (0, -20, 20), 1.4: (0, -20, 20)})
    fall.scale("wind_low", {0: (1, 1, 1), 0.6: (0.1, 0.1, 0.1), 1.4: (0.1, 0.1, 0.1)})
    fall.scale("wind_high", {0: (1, 1, 1), 0.6: (0.1, 0.1, 0.1), 1.4: (0.1, 0.1, 0.1)})

    # death: the winds die out, the wings fold and he topples backwards
    death = m.animation("death", 3.0, "once")
    death.scale("wind_low", {0: (1, 1, 1), 0.8: (0.05, 0.05, 0.05), 3.0: (0.05, 0.05, 0.05)})
    death.scale("wind_high", {0: (1, 1, 1), 0.8: (0.05, 0.05, 0.05), 3.0: (0.05, 0.05, 0.05)})
    death.rot("wing_upper_right", {0: (0, 0, 4), 1.0: (0, 40, -30), 3.0: (0, 40, -30)})
    death.rot("wing_upper_left", {0: (0, 0, -4), 1.0: (0, -40, 30), 3.0: (0, -40, 30)})
    death.rot("arm_right", {0: (0, 0, 6), 0.8: (-80, 0, 30), 2.0: (-150, 0, 40), 3.0: (-150, 0, 40)}, interp="linear")
    death.rot("arm_left", {0: (0, 0, -6), 0.8: (-80, 0, -30), 2.0: (-150, 0, -40), 3.0: (-150, 0, -40)}, interp="linear")
    death.rot("head", {0: (0, 0, 0), 0.8: (-25, 0, 0), 2.0: (6, 18, 0), 3.0: (6, 18, 0)}, interp="linear")
    death_fall(death, direction="back")


# ======================================================================================
# TABLET OF DESTINY
# ======================================================================================

def build_tablet():
    m = Model("bigcasares_myth_enlil_tablet", texture_size=(64, 64))
    tablet = m.bone("tablet", pivot=(0, 10, 0))
    half_r = m.bone("half_right", tablet, pivot=(3, 10, 0))
    half_l = m.bone("half_left", tablet, pivot=(-3, 10, 0))
    halo = m.bone("halo", tablet, pivot=(0, 10, 0))

    slab_r = m.cube(half_r, "slab_right", (0, 2, -1.5), (6, 18, 1.5))
    slab_l = m.cube(half_l, "slab_left", (-6, 2, -1.5), (0, 18, 1.5))
    for slab, crack_x in ((slab_r, 0), (slab_l, None)):
        slab.paint(CLAY, jitter=0.2, noise=0.06)
        for ff in (slab.north, slab.south):
            cuneiform(ff, x0=1, y0=2)
            ff.rect(0, 0, ff.w, 1, CLAY_L)
        for ff in slab.faces():
            ff.cracks(CLAY_D, n=1, length=4)
        slab.up.fill(CLAY_L, noise=0.05)
    # the jagged break line where the halves meet
    slab_r.north.line(slab_r.north.w - 1, 0, slab_r.north.w - 2, 6, CLAY_D).line(
        slab_r.north.w - 2, 6, slab_r.north.w - 1, 15, CLAY_D)
    slab_l.north.line(0, 0, 1, 6, CLAY_D).line(1, 6, 0, 15, CLAY_D)
    cap_r = m.cube(half_r, "cap_right", (0, 18, -1.2), (5, 19.5, 1.2))
    cap_r.paint(CLAY_L, jitter=0.15)
    cap_l = m.cube(half_l, "cap_left", (-5, 18, -1.2), (0, 19.5, 1.2))
    cap_l.paint(CLAY_L, jitter=0.15)
    for slab in (slab_r, slab_l):
        glow = slab.overlay(0.2, f"{slab.name}_glow")
        for ff in (glow.north, glow.south):
            for _ in range(7):
                gx, gy = ff.rng.randrange(1, ff.w - 1), ff.rng.randrange(2, ff.h - 1)
                ff.pixel(gx, gy, GLOW)
            ff.outline(mix(GLOW, GOLD_L, 0.5), sides="tb")
    ring = m.plane(halo, "halo_p", (-8, 0, 2.4), (8, 20, 2.4))
    rf = ring.front
    rf.ellipse(8, 10, 8, 10, (127, 230, 255, 170))
    rf.ellipse(8, 10, 7, 9, (255, 238, 160, 200))
    inner = rf.px.copy()
    rf.ellipse(8, 10, 6, 8, (255, 255, 255, 255))
    rf.px[rf.px[..., 0] == 255] = 0  # punch the centre out: a thin ring behind the tablet
    rf.px[inner[..., 3] == 0] = 0

    idle = m.animation("idle", 2.0, "loop")
    L = idle.length
    idle.pos("tablet", {0: (0, 0, 0), L / 2: (0, 1.2, 0), L: (0, 0, 0)})
    idle.rot("tablet", {0: (0, -12, 0), L / 2: (0, 12, 0), L: (0, -12, 0)})
    idle.scale("halo", {0: (1, 1, 1), L / 2: (1.12, 1.12, 1.12), L: (1, 1, 1)})

    brk = m.animation("break", 0.8, "hold")
    brk.rot("half_right", {0: (0, 0, 0), 0.5: (20, 0, -40), 0.8: (40, 0, -70)})
    brk.rot("half_left", {0: (0, 0, 0), 0.5: (-15, 0, 40), 0.8: (-30, 0, 70)})
    brk.pos("half_right", {0: (0, 0, 0), 0.5: (-4, -2, 0), 0.8: (-6, -9, 0)})
    brk.pos("half_left", {0: (0, 0, 0), 0.5: (4, -2, 0), 0.8: (6, -9, 0)})
    brk.scale("half_right", {0: (1, 1, 1), 0.6: (0.9, 0.9, 0.9), 0.8: (0.1, 0.1, 0.1)})
    brk.scale("half_left", {0: (1, 1, 1), 0.6: (0.9, 0.9, 0.9), 0.8: (0.1, 0.1, 0.1)})
    brk.scale("halo", {0: (1.3, 1.3, 1.3), 0.3: (0.01, 0.01, 0.01), 0.8: (0.01, 0.01, 0.01)})

    ref = m.animation("reform", 1.0, "once")
    ref.scale("tablet", {0: (0.05, 0.05, 0.05), 0.7: (1.1, 1.1, 1.1), 1.0: (1, 1, 1)})
    ref.rot("tablet", {0: (0, -180, 0), 0.7: (0, 0, 0), 1.0: (0, 0, 0)})
    ref.scale("halo", {0: (0.01, 0.01, 0.01), 0.7: (0.2, 0.2, 0.2), 1.0: (1, 1, 1)})

    missing = [name for name in TABLET_ANIMATIONS if name not in m.animations]
    assert not missing, f"tablet animations missing: {missing}"
    return m


MODELS = {
    "bigcasares_myth_enlil": build_enlil,
    "bigcasares_myth_enlil_tablet": build_tablet,
}
