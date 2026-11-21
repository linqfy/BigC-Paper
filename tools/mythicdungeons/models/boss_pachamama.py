"""Pachamama corrupted by Mandinga, mythic boss of the South American dungeon, and Mandinga himself.

Pachamama (~4.4 blocks): the earth mother.  She has no legs: from the waist down she is a mound of
soil and mossy stone with roots hanging from the hem (an alpha fringe), so ``rise`` can push her out
of the floor and ``death`` sinks her back.  Clay skin, an aguayo shawl (striped Andean cloth) over
the shoulders, root-and-bark forearms, long hair of dark leaves with corn silk, a crown of maize
cobs.  The corruption: violet veins painted over skin and cloth, glowing violet eyes and violet
crystals growing out of her back, her head and her left forearm.

Mandinga (~1.9 blocks, sitting): the devil of gaucho folklore riding on her back.  Black wide hat
with a red band, red eyes and goatee, black poncho with gold stripes and fringe, a red neckerchief,
goat legs with black hooves and, instead of a seat, a smoky serpent coil that sinks into her back.
He holds a guitar across his chest: ``strum`` sweeps the right hand over the strings, ``offer``
holds the guitar out, ``emerge`` uncoils him out of her back.

Both models face north (-Z); the creature's right is +X.
"""
import sys
from pathlib import Path

sys.path.insert(0, str(Path(__file__).resolve().parents[1]))
from bbkit import Model, mix  # noqa: E402

REQUIRED_ANIMATIONS = ("idle",)
PACHAMAMA_ANIMATIONS = ("idle", "walk", "attack", "cast", "rise", "rage", "death")
MANDINGA_ANIMATIONS = ("idle", "emerge", "strum", "offer")

SOIL = "#6b4a2e"
SOIL_D = "#3f2b1a"
SOIL_L = "#8f6b45"
STONE = "#7c786e"
STONE_D = "#4f4c46"
STONE_L = "#a39f93"
MOSS = "#5d7b2c"
MOSS_L = "#8fb043"
CORN = "#e8c34a"
CORN_D = "#a97f22"
HUSK = "#b4b45c"
HUSK_D = "#7d7d34"
SKIN = "#a8673d"
SKIN_D = "#73411f"
SKIN_L = "#c88a5c"
BARK = "#5a3d26"
BARK_D = "#362315"
BARK_L = "#7f5a3a"
HAIR = "#2d3a1e"
HAIR_L = "#4f6a2c"
VIOLET = "#8a3dd1"
VIOLET_D = "#4b1a7a"
VIOLET_L = "#d29cff"
AGUAYO = ("#c0283c", "#e86a1e", "#f2c230", "#2f8f4e", "#2b5fb0", "#e04a8a")

BLACK = "#16131a"
BLACK_L = "#2f2a36"
DEVIL = "#c7a99a"
DEVIL_D = "#8e6f62"
RED = "#b3121f"
RED_L = "#ff3b3b"
GOLD = "#d4a640"
GOLD_D = "#8a6618"
WOOD = "#8a4f22"
WOOD_D = "#5a2f12"
WOOD_L = "#b8773c"
SMOKE = "#2a2233"
SMOKE_L = "#4a3a5e"


def veins(face, n=3, colour=VIOLET, glow=VIOLET_L):
    """Corruption: branching violet veins with a bright pixel here and there."""
    rng = face.rng
    for _ in range(n):
        x, y = rng.randrange(face.w), rng.randrange(face.h)
        for step in range(rng.randint(4, 8)):
            face.pixel(x, y, glow if step % 4 == 0 else colour)
            x += rng.choice((-1, 0, 1))
            y += rng.choice((-1, 1, 1))
            if not (0 <= x < face.w and 0 <= y < face.h):
                break
    return face


def aguayo(face, start=0):
    """Andean woven cloth: horizontal bands of saturated colour with a diamond row in the middle."""
    y = 0
    k = start
    while y < face.h:
        colour = AGUAYO[k % len(AGUAYO)]
        h = 2 if k % 3 else 1
        face.rect(0, y, face.w, h, colour)
        if h == 2:
            for x in range((y // 2) % 3, face.w, 3):
                face.pixel(x, y, mix(colour, "#ffffff", 0.35))
        y += h
        k += 1
    return face


def soil(face):
    face.shaded(SOIL, jitter=0.22, noise=0.06, tones=5)
    face.spots(SOIL_D, n=max(3, face.w * face.h // 18), size=(1, 2))
    face.spots(STONE, n=max(2, face.w * face.h // 40), size=(1, 2))
    face.spots(SOIL_L, n=max(2, face.w * face.h // 30), size=(1, 1))
    return face


def crystal(face):
    face.gradient(VIOLET_L, VIOLET_D, steps=4)
    face.rect(1, 0, 1, face.h, mix(VIOLET_L, "#ffffff", 0.5))
    face.outline(VIOLET_D, sides="lr")
    return face


def roots(face, colour=BARK, light=BARK_L):
    """Alpha fringe of hanging roots: thin wavy strands of varied length."""
    face.clear()
    rng = face.rng
    for x in range(0, face.w, 2):
        if rng.random() < 0.25:
            continue
        length = rng.randint(face.h // 3, face.h)
        xx = x
        for y in range(length):
            face.pixel(xx, y, light if y % 5 == 0 else colour)
            if rng.random() < 0.25:
                xx = max(0, min(face.w - 1, xx + rng.choice((-1, 1))))
    return face


# ======================================================================================
# PACHAMAMA
# ======================================================================================

def build_pachamama():
    m = Model("bigcasares_myth_pachamama", texture_size=(256, 256))
    body = m.bone("body", pivot=(0, 20, 0))
    skirt = m.bone("skirt", body, pivot=(0, 20, 0))
    torso = m.bone("torso", body, pivot=(0, 22, 0))
    crystals = m.bone("crystals", torso, pivot=(0, 34, 6))
    head = m.bone("head", torso, pivot=(0, 41, 0))
    hair = m.bone("hair", head, pivot=(0, 50, 4))
    crown = m.bone("crown", head, pivot=(0, 52, 0))
    arm_r = m.bone("arm_right", torso, pivot=(10, 38.5, 0), rotation=(0, 0, 10))
    fore_r = m.bone("forearm_right", arm_r, pivot=(11.5, 29, 0), rotation=(12, 0, 0))
    arm_l = m.bone("arm_left", torso, pivot=(-10, 38.5, 0), rotation=(0, 0, -10))
    fore_l = m.bone("forearm_left", arm_l, pivot=(-11.5, 29, 0), rotation=(12, 0, 0))

    # --- the earth mound she grows out of
    low = m.cube(skirt, "mound_low", (-14, 0, -12), (14, 10, 12))
    for f in low.faces():
        soil(f)
    for f in low.sides():
        f.rect(0, f.h - 2, f.w, 2, SOIL_D)
        f.cracks(SOIL_D, n=3, length=5)
    low.up.fill(MOSS, noise=0.08).spots(MOSS_L, n=30, size=(1, 2)).spots(SOIL, n=20, size=(1, 2))
    moss = low.overlay(0.5, "mound_low_moss")
    for f in moss.sides():
        f.jagged(MOSS, edge="top", depth=(1, 3)).spots(MOSS_L, n=6, size=(1, 1), h=2)
        for x in range(2, f.w, 7):
            f.rect(x, 3, 2, 1, VIOLET).pixel(x + 1, 4, VIOLET_L)
    mid = m.cube(skirt, "mound_mid", (-11, 10, -9.5), (11, 20, 9.5))
    for f in mid.faces():
        soil(f)
    for f in mid.sides():
        veins(f, n=2)
        f.stripe(mix(SOIL, MOSS, 0.4), every=4, axis="h", offset=1)  # terraces of the hillside
    mid.up.fill(SOIL_L, noise=0.08)
    crops = mid.overlay(0.45, "mound_mid_crops")
    for f in crops.sides():
        for x in range(1, f.w, 3):  # little maize and potato plants on the terraces
            f.rect(x, 1, 1, 2, HUSK).pixel(x, 0, CORN)
            f.rect(x + 1, 5, 1, 2, MOSS_L).pixel(x + 1, 4, HUSK)
    stone_r = m.cube(skirt, "boulder_right", (10, 0, -8), (17, 7, -1), rotation=(0, 22.5, 0), pivot=(13.5, 0, -4.5))
    stone_r.paint(STONE, jitter=0.25, tones=5)
    for f in stone_r.faces():
        f.cracks(STONE_D, n=2, length=4)
        f.spots(MOSS, n=3, size=(1, 2))
    stone_r.up.fill(MOSS, noise=0.1).spots(MOSS_L, n=6)
    stone_l = m.cube(skirt, "boulder_left", (-16, 0, 2), (-10, 5, 9), rotation=(0, -22.5, 0), pivot=(-13, 0, 5.5))
    stone_l.paint(STONE_L, jitter=0.25, tones=5)
    for f in stone_l.faces():
        f.cracks(STONE_D, n=2, length=4)
        crystal_spot = f.w // 2
        f.rect(crystal_spot, 1, 1, 2, VIOLET)
    for name, lo, hi in (("roots_north", (-14.5, -2, -12.6), (14.5, 8, -12.6)),
                         ("roots_south", (-14.5, -2, 12.6), (14.5, 8, 12.6)),
                         ("roots_east", (14.6, -2, -12), (14.6, 8, 12)),
                         ("roots_west", (-14.6, -2, -12), (-14.6, 8, 12))):
        fringe = m.plane(skirt, name, lo, hi)
        roots(fringe.front)  # hangs from the moss line down into the ground

    # --- waist and chest: clay skin, a woven belt, the aguayo shawl
    waist = m.cube(torso, "waist", (-8, 19, -6), (8, 28, 6))
    waist.paint(SKIN, jitter=0.12, tones=5)
    for f in waist.sides():
        f.rect(0, 0, f.w, 3, AGUAYO[0]).rect(0, 1, f.w, 1, AGUAYO[2])
        veins(f, n=2)
    waist.north.rect(7, 4, 2, 2, SKIN_D)  # navel line
    chest = m.cube(torso, "chest", (-9, 28, -6.5), (9, 40, 6.5))
    chest.paint(SKIN, jitter=0.12, tones=5)
    cn = chest.north
    cn.rect(3, 4, 5, 1, SKIN_D).rect(10, 4, 5, 1, SKIN_D)
    cn.rect(8, 5, 2, 5, mix(SKIN, SKIN_D, 0.5))
    for f in chest.sides():
        veins(f, n=3)
    shawl = chest.overlay(0.45, "aguayo_shawl")
    for f in shawl.sides():
        aguayo(f, start=0)
        if f.name in ("north", "south"):  # only over the shoulders, with a V opening at the neck
            f.clear(0, 5, f.w, f.h - 5)
            if f.name == "north":
                for y in range(5):
                    half = max(0, 4 - y)
                    f.clear(f.w // 2 - half, y, half * 2, 1)
        else:
            f.clear(0, f.h - 3, f.w, 3)
        veins(f, n=2, colour=VIOLET_D)
    aguayo(shawl.up, start=1)
    tassels = m.plane(torso, "shawl_tassels", (-9.5, 22, 7.0), (9.5, 31, 7.0))
    tf = tassels.front
    aguayo(tf, start=3)
    tf.tatter(4, chance=0.9)
    for x in range(0, tf.w, 3):
        tf.clear(x, 0, 1, tf.h // 2)
    pauldron = m.cube(torso, "stone_shoulder_right", (8, 37, -4), (14, 42, 4))
    pauldron.paint(STONE, jitter=0.25, tones=5)
    for f in pauldron.faces():
        f.cracks(STONE_D, n=2, length=3).spots(MOSS, n=3, size=(1, 2))
    pauldron.up.fill(MOSS, noise=0.1).spots(MOSS_L, n=5)
    pauldron.mirror_x(torso, "stone_shoulder_left")

    # --- the corruption: violet crystals out of her back
    for name, lo, hi, rot in (("crystal_back_a", (-5, 31, 5.5), (-1.5, 45, 9), (0, 0, 22.5)),
                              ("crystal_back_b", (1, 30, 5.5), (4.5, 41, 9), (0, 0, -22.5)),
                              ("crystal_back_c", (-1.5, 34, 6), (1.5, 49, 9.5), (-22.5, 0, 0))):
        c = m.cube(crystals, name, lo, hi, rotation=rot,
                   pivot=((lo[0] + hi[0]) / 2, lo[1], (lo[2] + hi[2]) / 2))
        for f in c.faces():
            crystal(f)
        c.up.fill(VIOLET_L)

    # --- head: clay face, glowing violet eyes, painted cheeks
    neck = m.cube(head, "neck", (-2.5, 39.5, -2.5), (2.5, 42.5, 2.5))
    neck.paint(SKIN_D, jitter=0.1)
    skull = m.cube(head, "skull", (-5, 42, -5), (5, 52, 5))
    skull.paint(SKIN, jitter=0.1, tones=5)
    f = skull.north
    f.rect(0, 0, 10, 2, HAIR)
    f.rect(1, 3, 3, 1, SKIN_D).rect(6, 3, 3, 1, SKIN_D)  # brows
    f.rect(1, 4, 3, 1, VIOLET).rect(6, 4, 3, 1, VIOLET)
    f.pixel(2, 4, VIOLET_L).pixel(7, 4, VIOLET_L)
    f.rect(4, 5, 2, 2, SKIN_D)  # nose
    f.rect(3, 8, 4, 1, mix(SKIN_D, RED, 0.3))  # mouth
    f.rect(1, 6, 1, 2, AGUAYO[3]).rect(8, 6, 1, 2, AGUAYO[3])  # green cheek paint
    f.rect(0, 5, 1, 2, AGUAYO[0]).rect(9, 5, 1, 2, AGUAYO[0])
    veins(skull.east, n=2)
    hair_cap = skull.overlay(0.4, "hair_cap")
    hair_cap.up.fill(HAIR, noise=0.08).spots(HAIR_L, n=12)
    hair_cap.north.rect(0, 0, 10, 2, HAIR).jagged(HAIR, edge="top", depth=(2, 3))
    hair_cap.north.clear(2, 2, 6, 8)
    for ff in (hair_cap.east, hair_cap.west, hair_cap.south):
        ff.fill(HAIR, noise=0.08)
        ff.strands(HAIR_L, density=0.5, length=(2, 5))
    hair_cap.east.clear(0, 4, 5, 6)
    hair_cap.west.clear(hair_cap.west.w - 5, 4, 5, 6)
    long_hair = m.cube(hair, "hair_long", (-6, 28, 4.5), (6, 51, 8))
    long_hair.paint(HAIR, jitter=0.2)
    for ff in long_hair.faces():
        ff.strands(HAIR_L, density=0.6, length=(3, 7))
        ff.strands(CORN, density=0.12, length=(2, 4))  # corn silk
        ff.spots(MOSS_L, n=3, size=(1, 2))  # leaves
    long_hair.south.jagged(HAIR, edge="bottom", depth=(1, 3)).tatter(3)
    crystal_head = m.cube(head, "crystal_head", (-7.5, 47, -1.5), (-4.5, 56, 1.5), rotation=(0, 0, 22.5),
                          pivot=(-6, 47, 0))
    for ff in crystal_head.faces():
        crystal(ff)

    # --- crown of maize
    band = m.cube(crown, "crown_band", (-5.6, 51, -5.6), (5.6, 53.5, 5.6))
    band.paint(HUSK, jitter=0.15)
    for ff in band.sides():
        ff.stripe(HUSK_D, every=2, axis="v")
        ff.rect(0, 1, ff.w, 1, AGUAYO[0])
    for name, lo, hi, rot, pivot in (
            ("corn_front", (-1.4, 53, -6.2), (1.4, 62, -3.4), None, None),
            ("corn_right", (3.0, 52.5, -4.5), (5.6, 60, -1.9), (0, 0, -22.5), (4.3, 52.5, -3.2)),
            ("corn_left", (-5.6, 52.5, -4.5), (-3.0, 60, -1.9), (0, 0, 22.5), (-4.3, 52.5, -3.2))):
        cob = m.cube(crown, name, lo, hi, rotation=rot, pivot=pivot)
        cob.paint(CORN, jitter=0.15, tones=5)
        for ff in cob.sides():
            ff.scales(CORN_D, size=2, highlight="#fff2a8")
        cob.up.fill(CORN_D)
        husk = cob.overlay(0.35, f"{name}_husk")
        for ff in husk.sides():
            ff.jagged(HUSK, edge="bottom", depth=(3, 6))
            ff.strands(HUSK_D, density=0.4, length=(2, 3), y=ff.h // 2)

    # --- arms: clay upper arms, bark forearms ending in root claws
    upper = m.cube(arm_r, "upper_arm_right", (8.5, 29, -2.5), (13, 39, 2.5))
    upper.paint(SKIN, jitter=0.12, tones=5)
    for ff in upper.sides():
        ff.rect(0, 7, ff.w, 2, AGUAYO[1]).rect(0, 8, ff.w, 1, AGUAYO[4])
        veins(ff, n=1)
    fore = m.cube(fore_r, "forearm_right_c", (9, 19, -2.6), (13.5, 29.5, 2.6))
    fore.paint(BARK, jitter=0.2, tones=5)
    for ff in fore.faces():
        ff.stripe(BARK_L, every=3, axis="v", offset=1).cracks(BARK_D, n=2, length=4)
        ff.spots(MOSS, n=2)
    hand = m.cube(fore_r, "hand_right", (9.2, 15, -2.8), (13.3, 19.5, 2.8))
    hand.paint(BARK_L, jitter=0.2)
    for ff in hand.faces():
        ff.stripe(BARK, every=2, axis="v")
    claws = m.plane(fore_r, "root_claws_right", (9.2, 8, -0.2), (13.3, 15, -0.2))
    roots(claws.front, colour=BARK_D, light=BARK_L)
    upper.mirror_x(arm_l, "upper_arm_left")
    fore.mirror_x(fore_l, "forearm_left_c")
    hand.mirror_x(fore_l, "hand_left")
    claws.mirror_x(fore_l, "root_claws_left")
    cuff = m.cube(fore_l, "crystal_cuff_left", (-14.5, 22, -1.5), (-12, 31, 1.5), rotation=(0, 0, 22.5),
                  pivot=(-13, 22, 0))
    for ff in cuff.faces():
        crystal(ff)

    animate_pachamama(m)
    missing = [name for name in PACHAMAMA_ANIMATIONS if name not in m.animations]
    assert not missing, f"pachamama animations missing: {missing}"
    return m


def animate_pachamama(m):
    # idle: slow breathing, the hair and the crystals pulse.  Cheap: few bones, long loop.
    idle = m.animation("idle", 3.0, "loop")
    L = idle.length
    idle.rot("torso", {0: (0, 0, 0), L / 2: (2.5, 0, 0), L: (0, 0, 0)})
    idle.rot("head", {0: (0, -4, 0), L / 2: (-2, 4, 0), L: (0, -4, 0)})
    idle.rot("hair", {0: (0, 0, 0), L / 2: (5, 0, 0), L: (0, 0, 0)})
    idle.rot("arm_right", {0: (0, 0, 0), L / 2: (-3, 0, 3), L: (0, 0, 0)})
    idle.rot("arm_left", {0: (0, 0, 0), L / 2: (-3, 0, -3), L: (0, 0, 0)})
    idle.scale("crystals", {0: (1, 1, 1), L / 2: (1.08, 1.08, 1.08), L: (1, 1, 1)})

    # walk: the mound shuffles forward (a rolling wobble), arms swing
    walk = m.animation("walk", 1.6, "loop")
    L = walk.length
    walk.rot("skirt", {0: (0, 0, -4), L / 2: (0, 0, 4), L: (0, 0, -4)})
    walk.pos("body", {0: (0, 0, 0), L / 4: (0, 0.8, 0), L / 2: (0, 0, 0), 3 * L / 4: (0, 0.8, 0), L: (0, 0, 0)})
    walk.rot("torso", {0: (6, 0, 3), L / 2: (6, 0, -3), L: (6, 0, 3)})
    walk.rot("arm_right", {0: (20, 0, 0), L / 2: (-20, 0, 0), L: (20, 0, 0)})
    walk.rot("arm_left", {0: (-20, 0, 0), L / 2: (20, 0, 0), L: (-20, 0, 0)})
    walk.rot("hair", {0: (8, 0, 0), L / 2: (12, 0, 0), L: (8, 0, 0)})

    # attack: the right arm rises overhead and slams the root claws down
    atk = m.animation("attack", 1.1, "once")
    atk.rot("arm_right", {0: (0, 0, 0), 0.4: (-165, 0, -10), 0.6: (-40, 0, 0), 1.1: (0, 0, 0)}, interp="linear")
    atk.rot("forearm_right", {0: (0, 0, 0), 0.4: (-30, 0, 0), 0.6: (-5, 0, 0), 1.1: (0, 0, 0)}, interp="linear")
    atk.rot("torso", {0: (0, 0, 0), 0.4: (-10, 10, 0), 0.6: (22, -6, 0), 1.1: (0, 0, 0)}, interp="linear")
    atk.pos("body", {0: (0, 0, 0), 0.6: (0, -2, -1), 1.1: (0, 0, 0)})
    atk.rot("head", {0: (0, 0, 0), 0.4: (-12, 0, 0), 0.6: (10, 0, 0), 1.1: (0, 0, 0)})

    # cast: both arms up with the palms to the sky; the floor gets painted
    cast = m.animation("cast", 1.4, "once")
    cast.rot("arm_right", {0: (0, 0, 0), 0.4: (-150, 0, 30), 1.0: (-150, 0, 30), 1.4: (0, 0, 0)})
    cast.rot("arm_left", {0: (0, 0, 0), 0.4: (-150, 0, -30), 1.0: (-150, 0, -30), 1.4: (0, 0, 0)})
    cast.rot("head", {0: (0, 0, 0), 0.4: (-22, 0, 0), 1.0: (-22, 0, 0), 1.4: (0, 0, 0)})
    cast.rot("torso", {0: (0, 0, 0), 0.4: (-6, 0, 0), 1.0: (-6, 0, 0), 1.4: (0, 0, 0)})
    cast.scale("crystals", {0: (1, 1, 1), 0.5: (1.3, 1.3, 1.3), 1.0: (1.3, 1.3, 1.3), 1.4: (1, 1, 1)})

    # rise: she pushes out of the floor (the floor hides what is still below), arms first
    rise = m.animation("rise", 3.5, "once")
    rise.pos("body", {0: (0, -72, 0), 0.8: (0, -48, 0), 1.6: (0, -28, 0), 2.6: (0, -4, 0), 3.0: (0, 1, 0),
                      3.5: (0, 0, 0)})
    rise.rot("body", {0: (0, 0, 0), 0.6: (4, 0, -3), 1.2: (-3, 0, 3), 1.8: (4, 0, -2), 2.6: (-2, 0, 2),
                      3.5: (0, 0, 0)})
    rise.rot("arm_right", {0: (-175, 0, 10), 2.0: (-160, 0, 20), 3.0: (-40, 0, 10), 3.5: (0, 0, 0)})
    rise.rot("arm_left", {0: (-175, 0, -10), 2.0: (-160, 0, -20), 3.0: (-40, 0, -10), 3.5: (0, 0, 0)})
    rise.rot("head", {0: (-30, 0, 0), 2.4: (-20, 0, 0), 3.5: (0, 0, 0)})
    rise.scale("crystals", {0: (0.2, 0.2, 0.2), 2.8: (0.4, 0.4, 0.4), 3.3: (1.15, 1.15, 1.15), 3.5: (1, 1, 1)})

    # rage: Mandinga claws out of her back - she arches, arms flung wide, the crystals flare
    rage = m.animation("rage", 2.5, "once")
    rage.rot("torso", {0: (0, 0, 0), 0.4: (-18, 0, 0), 2.0: (-18, 0, 0), 2.5: (0, 0, 0)})
    rage.rot("arm_right", {0: (0, 0, 0), 0.4: (-120, 0, 55), 2.0: (-120, 0, 55), 2.5: (0, 0, 0)})
    rage.rot("arm_left", {0: (0, 0, 0), 0.4: (-120, 0, -55), 2.0: (-120, 0, -55), 2.5: (0, 0, 0)})
    rage.rot("head", {0: (0, 0, 0), 0.4: (-30, 0, 0), 0.9: (-26, 8, 0), 1.4: (-30, -8, 0), 2.0: (-30, 0, 0),
                      2.5: (0, 0, 0)})
    rage.scale("crystals", {0: (1, 1, 1), 0.5: (1.5, 1.5, 1.5), 2.0: (1.5, 1.5, 1.5), 2.5: (1.1, 1.1, 1.1)})
    rage.pos("body", {0: (0, 0, 0), 0.5: (0.6, 0, 0), 0.6: (-0.6, 0, 0), 0.7: (0.6, 0, 0), 0.8: (-0.6, 0, 0),
                      0.9: (0, 0, 0), 2.5: (0, 0, 0)}, interp="linear")

    # death: she reaches up once more and sinks back into the earth
    death = m.animation("death", 3.5, "once")
    death.rot("arm_right", {0: (0, 0, 0), 0.6: (-170, 0, 15), 3.5: (-175, 0, 5)})
    death.rot("arm_left", {0: (0, 0, 0), 0.6: (-170, 0, -15), 3.5: (-175, 0, -5)})
    death.rot("head", {0: (0, 0, 0), 0.6: (-30, 0, 0), 3.5: (-10, 0, 0)})
    death.rot("torso", {0: (0, 0, 0), 0.6: (-10, 0, 0), 2.0: (10, 0, 4), 3.5: (14, 0, 6)})
    death.pos("body", {0: (0, 0, 0), 0.8: (0, 0, 0), 2.4: (0, -40, 0), 3.5: (0, -74, 0)})
    death.scale("crystals", {0: (1, 1, 1), 1.0: (0.6, 0.6, 0.6), 3.5: (0.1, 0.1, 0.1)})


# ======================================================================================
# MANDINGA
# ======================================================================================

def build_mandinga():
    m = Model("bigcasares_myth_pachamama_mandinga", texture_size=(128, 128),
              bone_budget=(16, 20), cube_budget=(40, 48))
    coil = m.bone("coil", pivot=(0, 0, 0))
    coil_mid = m.bone("coil_mid", coil, pivot=(0, -4, 3))
    coil_tip = m.bone("coil_tip", coil_mid, pivot=(0, -9, 5))
    body = m.bone("body", pivot=(0, 2, 0))
    leg_r = m.bone("leg_right", body, pivot=(2.2, 2.5, -1), rotation=(80, 0, 0))
    leg_l = m.bone("leg_left", body, pivot=(-2.2, 2.5, -1), rotation=(80, 0, 0))
    shin_r = m.bone("shin_right", leg_r, pivot=(2.2, -4.5, -1), rotation=(-80, 0, 0))
    shin_l = m.bone("shin_left", leg_l, pivot=(-2.2, -4.5, -1), rotation=(-80, 0, 0))
    torso = m.bone("torso", body, pivot=(0, 4, 0))
    head = m.bone("head", torso, pivot=(0, 15.5, 0))
    arm_r = m.bone("arm_right", torso, pivot=(5.2, 13.5, 0), rotation=(40, 0, 0))
    fore_r = m.bone("forearm_right", arm_r, pivot=(6.2, 8, 0), rotation=(40, 0, 0))
    arm_l = m.bone("arm_left", torso, pivot=(-5.2, 13.5, 0), rotation=(60, -30, 0))
    fore_l = m.bone("forearm_left", arm_l, pivot=(-6.2, 8, 0), rotation=(30, 0, 0))
    guitar = m.bone("guitar", torso, pivot=(0, 8, -4.5), rotation=(0, 0, -40))

    # --- the smoky serpent coil he rides out of her back
    for bone, name, lo, hi in ((coil, "coil_a", (-5, -4, -4), (5, 1, 6)),
                               (coil_mid, "coil_b", (-4, -9, 0), (4, -3.5, 8)),
                               (coil_tip, "coil_c", (-2.8, -15, 2), (2.8, -8.5, 8))):
        c = m.cube(bone, name, lo, hi)
        c.paint(SMOKE, jitter=0.2)
        for ff in c.faces():
            ff.scales(BLACK, size=3, highlight=SMOKE_L)
            veins(ff, n=1, colour=RED, glow=RED_L)

    # --- goat legs folded under him, black hooves
    hips = m.cube(body, "hips", (-4.2, 1, -3), (4.2, 4.5, 3))
    hips.paint(BLACK_L, jitter=0.15)
    thigh = m.cube(leg_r, "thigh_right", (0.5, -4.5, -2.6), (4, 2.5, 0.6))
    thigh.paint("#3a2a22", jitter=0.25)
    for ff in thigh.faces():
        ff.strands("#5a4232", density=0.5, length=(2, 4))
    shin = m.cube(shin_r, "shin_right_c", (0.8, -11, -2.4), (3.6, -4.5, 0.2))
    shin.paint("#3a2a22", jitter=0.25)
    for ff in shin.faces():
        ff.strands("#5a4232", density=0.5, length=(2, 4))
    hoof = m.cube(shin_r, "hoof_right", (0.6, -13, -2.8), (3.8, -11, 0.6))
    hoof.paint(BLACK, jitter=0.1)
    hoof.north.rect(1, 0, 1, 2, "#000000")  # the split of the hoof
    thigh.mirror_x(leg_l, "thigh_left")
    shin.mirror_x(shin_l, "shin_left_c")
    hoof.mirror_x(shin_l, "hoof_left")

    # --- black shirt, poncho with gold stripes and fringe, red neckerchief
    chest = m.cube(torso, "chest", (-4.2, 4, -2.4), (4.2, 15.5, 2.4))
    chest.paint(BLACK_L, jitter=0.1)
    chest.north.rect(3, 0, 2, 11, BLACK).pixel(4, 3, GOLD).pixel(4, 6, GOLD)
    poncho = m.cube(torso, "poncho", (-6.5, 6.5, -3.6), (6.5, 15.8, 3.6))
    poncho.paint(BLACK, jitter=0.12)
    for ff in poncho.sides():
        ff.rect(0, 2, ff.w, 1, GOLD).rect(0, 4, ff.w, 1, RED).rect(0, 6, ff.w, 1, GOLD_D)
    poncho.up.fill(BLACK, noise=0.05)
    fringe = poncho.overlay(0.4, "poncho_fringe")
    for ff in fringe.sides():
        ff.jagged(GOLD_D, edge="bottom", depth=(1, 2))
        for x in range(0, ff.w, 2):
            ff.pixel(x, ff.h - 1, GOLD)
    scarf = m.cube(torso, "neckerchief", (-3.2, 14, -3.2), (3.2, 16, 3.2))
    scarf.paint(RED, jitter=0.15)
    scarf.north.rect(2, 1, 2, 1, RED_L)

    # --- head: pale reddish skin, red eyes, goatee, small horns, wide black hat
    skull = m.cube(head, "skull", (-3.2, 15.5, -3.2), (3.2, 22, 3.2))
    skull.paint(DEVIL, jitter=0.1, tones=5)
    f = skull.north
    f.rect(0, 1, 6, 1, BLACK)  # brows, frowning
    f.pixel(1, 2, RED_L).pixel(4, 2, RED_L)
    f.pixel(2, 2, RED).pixel(3, 2, DEVIL_D)
    f.rect(1, 4, 4, 1, BLACK)  # moustache
    f.rect(2, 5, 2, 1, "#5a0d14")  # grin
    goatee = m.plane(head, "goatee", (-1.2, 12.5, -3.4), (1.2, 16.5, -3.4))
    goatee.front.fill(BLACK).tatter(2, chance=0.6)
    horn = m.cube(head, "horn_right", (2.6, 21, -1.2), (3.8, 24, 0.2), rotation=(0, 0, -22.5), pivot=(3.2, 21, -0.5))
    horn.paint("#e8dcc0", jitter=0.1)
    horn.mirror_x(head, "horn_left")
    brim = m.cube(head, "hat_brim", (-7, 21.5, -7), (7, 22.5, 7))
    brim.paint(BLACK, jitter=0.08)
    brim.up.outline(BLACK_L)
    crown = m.cube(head, "hat_crown", (-3.8, 22.5, -3.8), (3.8, 27, 3.8))
    crown.paint(BLACK, jitter=0.08)
    for ff in crown.sides():
        ff.rect(0, ff.h - 2, ff.w, 1, RED)
    crown.up.rect(1, 3, 6, 1, BLACK_L)  # the crease

    # --- arms: black sleeves, pale hands
    upper = m.cube(arm_r, "upper_arm_right", (4.4, 7.5, -1.4), (7.2, 14, 1.4))
    upper.paint(BLACK_L, jitter=0.1)
    fore = m.cube(fore_r, "forearm_right_c", (4.6, 2.5, -1.3), (7, 8.2, 1.3))
    fore.paint(BLACK_L, jitter=0.1)
    fore.north.rect(0, 0, fore.north.w, 1, GOLD)
    hand = m.cube(fore_r, "hand_right", (4.7, 0.5, -1.2), (6.9, 2.6, 1.2))
    hand.paint(DEVIL, jitter=0.1)
    upper.mirror_x(arm_l, "upper_arm_left")
    fore.mirror_x(fore_l, "forearm_left_c")
    hand.mirror_x(fore_l, "hand_left")

    # --- the guitar across his chest
    lower = m.cube(guitar, "guitar_lower", (-4.2, 1.5, -6.4), (4.2, 7.5, -4.4))
    upper_bout = m.cube(guitar, "guitar_upper", (-3.4, 7.5, -6.4), (3.4, 11.5, -4.4))
    for part in (lower, upper_bout):
        part.paint(WOOD, jitter=0.15, tones=5)
        part.north.fill(WOOD_L, noise=0.06)
        part.north.outline(WOOD_D)
        for ff in part.sides():
            if ff.name != "north":
                ff.stripe(WOOD_D, every=2, axis="h")
    upper_bout.north.ellipse(upper_bout.north.w // 2, 1, 1, 1, "#1a0c06")  # the sound hole
    lower.north.rect(2, 2, lower.north.w - 4, 1, WOOD_D)  # the bridge
    neck = m.cube(guitar, "guitar_neck", (-0.9, 11.5, -5.9), (0.9, 23, -4.6))
    neck.paint(WOOD_D, jitter=0.1)
    for y in range(1, neck.north.h, 2):
        neck.north.pixel(0, y, "#d8d0c0")  # frets
    head_stock = m.cube(guitar, "guitar_head", (-1.3, 23, -6.1), (1.3, 26, -4.4))
    head_stock.paint(BLACK, jitter=0.1)
    head_stock.north.pixel(0, 1, GOLD).pixel(1, 1, GOLD)
    # strings painted over the top, the neck and the lower bout
    for part in (lower, upper_bout):
        mid = part.north.w // 2
        part.north.rect(mid - 1, 0, 2, part.north.h, mix(WOOD_L, "#e8e0d0", 0.45))

    animate_mandinga(m)
    missing = [name for name in MANDINGA_ANIMATIONS if name not in m.animations]
    assert not missing, f"mandinga animations missing: {missing}"
    return m


def animate_mandinga(m):
    # Keys are offsets from the rest pose (sitting, guitar across the chest).
    # idle (sitting): a lazy sway, a hoof tapping the beat, the coil breathing
    idle = m.animation("idle", 2.0, "loop")
    L = idle.length
    idle.rot("torso", {0: (0, 0, -3), L / 2: (0, 0, 3), L: (0, 0, -3)})
    idle.rot("head", {0: (0, 6, 0), L / 2: (4, -6, 0), L: (0, 6, 0)})
    idle.rot("shin_right", {0: (0, 0, 0), L / 4: (-18, 0, 0), L / 2: (0, 0, 0), 3 * L / 4: (-18, 0, 0),
                            L: (0, 0, 0)})
    idle.scale("coil", {0: (1, 1, 1), L / 2: (1.06, 1, 1.06), L: (1, 1, 1)})
    idle.rot("coil_tip", {0: (0, -10, 0), L / 2: (0, 10, 0), L: (0, -10, 0)})

    # emerge: he uncoils out of her back, then sits up
    emerge = m.animation("emerge", 2.0, "once")
    emerge.scale("coil", {0: (0.05, 0.05, 0.05), 0.8: (1.1, 1.1, 1.1), 1.0: (1, 1, 1)})
    emerge.pos("body", {0: (0, -18, 4), 0.8: (0, -6, 2), 1.4: (0, 1, 0), 2.0: (0, 0, 0)})
    emerge.scale("body", {0: (0.2, 0.2, 0.2), 1.0: (0.9, 0.9, 0.9), 1.4: (1.05, 1.05, 1.05), 2.0: (1, 1, 1)})
    emerge.rot("body", {0: (40, 0, 0), 1.0: (20, 20, 0), 1.6: (-6, 0, 0), 2.0: (0, 0, 0)})
    emerge.rot("head", {0: (30, 0, 0), 1.4: (-15, 0, 0), 2.0: (0, 0, 0)})

    # strum: the right hand sweeps down across the strings, head nods with the chord
    strum = m.animation("strum", 0.9, "once")
    strum.rot("arm_right", {0: (0, 0, 0), 0.25: (-22, 0, 12), 0.45: (18, 0, -8), 0.9: (0, 0, 0)}, interp="linear")
    strum.rot("forearm_right", {0: (0, 0, 0), 0.25: (-28, 0, 0), 0.45: (18, 0, 0), 0.9: (0, 0, 0)},
              interp="linear")
    strum.rot("guitar", {0: (0, 0, 0), 0.45: (-6, 0, 3), 0.9: (0, 0, 0)})
    strum.rot("head", {0: (0, 0, 0), 0.25: (-12, 0, 0), 0.45: (14, 0, 0), 0.9: (0, 0, 0)})
    strum.rot("torso", {0: (0, 0, 0), 0.45: (8, 0, 0), 0.9: (0, 0, 0)})

    # offer: he holds the guitar out toward the players and beckons with the head
    offer = m.animation("offer", 1.6, "once")
    offer.rot("guitar", {0: (0, 0, 0), 0.5: (-30, 0, 30), 1.2: (-30, 0, 30), 1.6: (0, 0, 0)})
    offer.pos("guitar", {0: (0, 0, 0), 0.5: (0, 2, -4), 1.2: (0, 2, -4), 1.6: (0, 0, 0)})
    offer.rot("arm_right", {0: (0, 0, 0), 0.5: (-45, 0, 0), 1.2: (-45, 0, 0), 1.6: (0, 0, 0)})
    offer.rot("arm_left", {0: (0, 0, 0), 0.5: (-25, 20, 0), 1.2: (-25, 20, 0), 1.6: (0, 0, 0)})
    offer.rot("head", {0: (0, 0, 0), 0.5: (-10, 0, 12), 0.9: (6, 0, -6), 1.2: (-10, 0, 12), 1.6: (0, 0, 0)})
    offer.rot("torso", {0: (0, 0, 0), 0.5: (12, 0, 0), 1.2: (12, 0, 0), 1.6: (0, 0, 0)})


MODELS = {
    "bigcasares_myth_pachamama": build_pachamama,
    "bigcasares_myth_pachamama_mandinga": build_mandinga,
}
