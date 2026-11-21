"""Mesoamerican mythology creatures for the mythic dungeons (Xibalba lord, Xolotl, Camazotz).

Cuboids of varied sizes with painted relief: overlay layers (headbands, bracelets) and alpha
planes (feathers, cloth, membranes).  Model faces north (-Z); the creature's right is +X.
"""
import math
import sys
from pathlib import Path

import numpy as np

sys.path.insert(0, str(Path(__file__).resolve().parents[1]))
from bbkit import (Model, attack_swing, death_fall, flap, idle_breathe, mix, ramp,  # noqa: E402
                   walk_biped, walk_quadruped)

BONE = "#d9ceb0"
BONE_D = "#8d8368"
VOID = "#1a1410"
JADE = "#2fb58a"
JADE_D = "#1b7459"
JADE_L = "#92e6c4"
RED = "#b3281f"
GOLD = "#d8a830"
QUETZAL = "#1f9a5a"
QUETZAL_D = "#0f5a38"
TURQ = "#2fb5c5"
OBS = "#15121c"
OBS_L = "#5a5a7a"
LINEN = "#e6dcc0"
WOOD = "#7a4a28"


def speckle(face, color, p=0.2):
    """Recolour a random share of the already painted pixels (keeps transparency)."""
    ys, xs = np.nonzero(face.mask())
    for y, x in zip(ys, xs):
        if face.rng.random() < p:
            face.pixel(int(x), int(y), color)
    return face


def bony(cube, base=BONE, dark=BONE_D):
    """Weathered bone: shaded coat, crack lines and dark flecks."""
    cube.paint(base, noise=0.05, jitter=0.18)
    for f in cube.faces():
        f.cracks(dark, n=1, length=3)
    return cube


def plume(face, c_dark, c_mid, c_light, tip=None, taper=0.6):
    """Feather silhouette: pointed oval, light shaft, diagonal barbs, tip colour."""
    w, h = face.w, face.h
    mid = (w - 1) / 2
    for y in range(h):
        t = y / max(1, h - 1)
        half = (w / 2) * (0.35 + 0.65 * math.sin(min(1.0, t * 1.15) * math.pi * 0.62 + 0.5) ** 1.0)
        if t > 1 - taper * 0.5:
            half *= 0.35 + 0.65 * (1 - t) / (taper * 0.5)
        for x in range(w):
            if abs(x - mid) <= half:
                u = abs(x - mid) / max(0.5, half)
                col = c_mid if u < 0.7 else c_dark
                if tip and t > 0.8:
                    col = tip if u < 0.8 else mix(tip, "#000", 0.3)
                face.pixel(x, y, col)
    for y in range(h):
        face.pixel(int(round(mid)), y, c_light)
        if face.w > 5:
            face.pixel(int(round(mid)) - 1, y, mix(c_mid, c_light, 0.3))
    for y in range(1, h, 2):  # barbs
        for k in range(1, int(w / 2)):
            for sx in (-1, 1):
                face.pixel(int(round(mid)) + sx * k, y + k // 2, mix(c_dark, "#000", 0.25)) if face.px[min(h - 1, y + k // 2), min(w - 1, int(round(mid)) + sx * k), 3] else None
    face.noise(0.06)
    return face


GLYPH = [  # stepped fret band, 6 px repeat
    "rrrr..",
    "...r..",
    ".rrr..",
    ".r....",
    ".rrrrr",
]


# =====================================================================================
# XIBALBA LORD - emaciated death god with a macuahuitl and a jade-and-quetzal headdress
# =====================================================================================
def build_xibalba_lord():
    m = Model("bigcasares_myth_xibalba_lord", texture_size=(256, 128))

    body = m.bone("body", pivot=(0, 13, 0))
    torso = m.bone("torso", body, pivot=(0, 17, 0))
    head = m.bone("head", torso, pivot=(0, 28, 0))
    jaw = m.bone("jaw", head, pivot=(0, 29.5, -2.5))
    plumes = m.bone("plumes", head, pivot=(0, 34, 1))
    back_plumes = m.bone("back_plumes", torso, pivot=(0, 27, 3))
    arm_r = m.bone("arm_right", torso, pivot=(6.8, 26, 0))
    fore_r = m.bone("forearm_right", arm_r, pivot=(6.8, 18, 0))
    weapon = m.bone("weapon", fore_r, pivot=(6.8, 10, 0), rotation=(-12, 0, 0))
    arm_l = m.bone("arm_left", torso, pivot=(-6.8, 26, 0))
    fore_l = m.bone("forearm_left", arm_l, pivot=(-6.8, 18, 0))
    leg_r = m.bone("leg_right", body, pivot=(2.6, 13, 0))
    shin_r = m.bone("shin_right", leg_r, pivot=(2.6, 6.5, 0))
    leg_l = m.bone("leg_left", body, pivot=(-2.6, 13, 0))
    shin_l = m.bone("shin_left", leg_l, pivot=(-2.6, 6.5, 0))
    loin_f = m.bone("loin_front", body, pivot=(0, 13, -2.7))
    loin_b = m.bone("loin_back", body, pivot=(0, 13, 2.7))

    # --- legs: bone-thin with knobby knees
    thigh = bony(m.cube(leg_r, "thigh_right", (1.1, 6.5, -1.6), (4.1, 13.5, 1.6)))
    knee = m.cube(shin_r, "knee_right", (0.8, 5.6, -2), (4.4, 7.6, 1.8))
    knee.paint(BONE_D, jitter=0.2)
    knee.north.rect(1, 0, 2, 1, BONE)
    shin = bony(m.cube(shin_r, "lower_leg_right", (1.4, 1.8, -1.2), (3.8, 5.8, 1.2)))
    shin.north.rect(0, 0, 1, 4, VOID)
    foot = m.cube(shin_r, "foot_right", (0.8, 0, -3.6), (4.2, 1.9, 1.4))
    foot.paint(BONE, jitter=0.2)
    foot.north.stripe(VOID, every=2, axis="v", y=0, h=2, offset=1)
    thigh.mirror_x(leg_l, "thigh_left")
    knee.mirror_x(shin_l, "knee_left")
    shin.mirror_x(shin_l, "lower_leg_left")
    foot.mirror_x(shin_l, "foot_left")
    anklet = m.cube(shin_r, "anklet_right", (1.1, 1.2, -1.5), (4.1, 3, 1.5))
    anklet.paint(JADE, jitter=0.12)
    for f in anklet.sides():
        f.stripe(JADE_D, every=2, axis="v", offset=1).rect(0, 0, f.w, 1, JADE_L)
    anklet.mirror_x(shin_l, "anklet_left")

    # --- pelvis, loincloth with a glyph band, belt
    pelvis = bony(m.cube(body, "pelvis", (-3.8, 12, -2.2), (3.8, 16.5, 2.2)))
    pelvis.north.rect(2, 1, 4, 2, BONE_D).rect(3, 2, 2, 1, VOID)
    belt = m.cube(body, "belt", (-4.2, 15.2, -2.6), (4.2, 17.2, 2.6))
    belt.paint(RED, jitter=0.1)
    for f in belt.sides():
        f.stripe(mix(RED, "#000", 0.35), every=3, axis="v", offset=1)
        f.rect(0, 0, f.w, 1, GOLD)
    belt.north.rect(3, 0, 2, 2, JADE).pixel(3, 0, JADE_L)
    for name, bone, z, hw, y0 in (("loin_flap_front", loin_f, -2.7, 3.4, 3.5), ("loin_flap_back", loin_b, 2.7, 3.6, 4.5)):
        pl = m.plane(bone, name, (-hw, y0, z), (hw, 15.4, z))
        fc = pl.front
        fc.shaded(LINEN, noise=0.05, jitter=0.1, shade=False)
        fc.stripe(mix(LINEN, "#000", 0.12), every=3, axis="v", offset=1)
        fc.rect(0, 0, fc.w, 1, RED)
        fc.rect(0, 1, fc.w, 5, mix(LINEN, "#000", 0.05))
        fc.decal(GLYPH, {"r": RED}, x=0, y=1)
        fc.decal(GLYPH, {"r": RED}, x=6, y=1)
        fc.rect(0, 6, fc.w, 1, mix(JADE, "#000", 0.1))
        fc.rect(0, fc.h - 2, fc.w, 2, mix(RED, "#000", 0.2))
        for xx in range(0, fc.w, 2):
            fc.pixel(xx, fc.h - 3, GOLD)
        fc.tatter(2, edge="bottom", chance=0.6)

    # --- torso: spine + ribcage with see-through gaps + dark chest void
    spine = bony(m.cube(torso, "spine", (-1.4, 15.5, -1.2), (1.4, 21, 1.4)))
    spine.north.rect(1, 0, 1, 5, VOID)
    void = m.cube(torso, "chest_void", (-3.9, 19.5, -2.1), (3.9, 27.2, 2.1))
    void.paint(VOID, jitter=0.2, noise=0.08)
    ribs = m.cube(torso, "ribcage", (-4.8, 19, -2.9), (4.8, 27.8, 2.9))
    ribs.paint(BONE, noise=0.05, jitter=0.15)
    for f in (ribs.north, ribs.south):
        f.clear(0, 0, f.w, f.h)
        cx = f.w / 2
        for r in range(0, f.h - 1, 2):  # rib arcs
            w_r = int(cx * (0.75 + 0.25 * math.sin(r / f.h * math.pi)))
            f.rect(int(cx - w_r), r, 2 * w_r, 1, BONE, noise=0.06)
            f.rect(int(cx - w_r), r + 1 if r + 1 < f.h else r, 2 * w_r, 1, BONE_D)
        f.rect(int(cx) - 1, 0, 2, f.h, BONE)  # sternum / spine
        f.rect(int(cx) - 1, 0, 1, f.h, mix(BONE, "#fff", 0.2))
        f.rect(0, 0, f.w, 1, BONE)
    for f in (ribs.east, ribs.west):
        f.clear(0, 0, f.w, f.h)
        for r in range(0, f.h - 1, 2):
            f.rect(0, r, f.w, 1, BONE).rect(0, r + 1 if r + 1 < f.h else r, f.w, 1, BONE_D)
    ribs.up.fill(BONE, noise=0.05)
    ribs.down.fill(BONE_D)
    clav = bony(m.cube(torso, "collarbones", (-6.2, 26.2, -2.2), (6.2, 28.4, 1.8)))
    clav.up.rect(5, 0, 2, clav.up.h, BONE_D)
    neck = bony(m.cube(torso, "neck", (-1.3, 27.8, -1.2), (1.3, 30, 1.2)))
    # jade collar (wide plate) with gold beads and owl pectoral
    collar = m.cube(torso, "jade_collar", (-5.8, 24.4, -3.5), (5.8, 27.8, 3.3))
    collar.paint(JADE, jitter=0.12)
    for f in collar.sides():
        f.rect(0, 0, f.w, 1, JADE_L)
        f.stripe(JADE_D, every=2, axis="v", offset=1, y=1, h=2)
        f.rect(0, 2, f.w, 1, GOLD)
    collar.north.pixel(5, 1, RED).pixel(6, 1, RED)
    collar.up.rect(3, 0, 6, collar.up.h, VOID)
    owl_plate = m.plane(torso, "owl_pectoral", (-3, 17.5, -3.62), (3, 24.4, -3.62))
    of = owl_plate.front
    of.ellipse(3, 3.6, 3, 3.6, GOLD)
    of.ellipse(3, 3.6, 2.4, 3.0, mix(GOLD, "#000", 0.25))
    of.ellipse(3, 3.4, 2.0, 2.6, JADE)
    of.rect(1, 2, 2, 2, "#ffe58a").rect(3, 2, 2, 2, "#ffe58a")  # owl eyes
    of.pixel(1, 3, VOID).pixel(4, 3, VOID).pixel(2, 3, VOID).pixel(3, 3, VOID)
    of.rect(2, 4, 2, 2, GOLD).pixel(2, 5, mix(GOLD, "#000", 0.3))  # beak
    of.line(0, 0, 1, 1, JADE_D).line(5, 0, 4, 1, JADE_D)
    of.noise(0.05)

    # --- skull
    skull = m.cube(head, "skull", (-3.5, 29, -3.8), (3.5, 36, 3.2))
    skull.paint(BONE, noise=0.05, jitter=0.15)
    sn = skull.north
    sn.rect(0, 2, 7, 1, BONE_D)
    sn.rect(0, 3, 3, 2, VOID).rect(4, 3, 3, 2, VOID)  # eye sockets
    sn.pixel(1, 4, "#ff4a2a").pixel(5, 4, "#ff4a2a").pixel(1, 3, mix("#ff4a2a", "#000", 0.5)).pixel(5, 3, mix("#ff4a2a", "#000", 0.5))
    sn.rect(3, 5, 1, 2, VOID)  # nasal hole
    sn.pixel(0, 5, JADE).pixel(6, 5, JADE).pixel(0, 6, JADE_D).pixel(6, 6, JADE_D)  # cheek inlays
    sn.rect(2, 1, 3, 1, JADE).pixel(3, 1, JADE_L).pixel(3, 0, GOLD)  # forehead jade bead
    sn.cracks(BONE_D, n=1, length=3)
    for f in (skull.east, skull.west):
        f.rect(0, 5, f.w, 1, BONE_D).rect(2, 2, 2, 2, VOID)
    jw = bony(m.cube(jaw, "lower_jaw", (-2.6, 27.2, -3.6), (2.6, 29.6, 1.4)))
    jw.up.fill(VOID).rect(0, 0, 5, 1, BONE)
    jw.north.rect(0, 0, 5, 1, BONE)
    for xx in (0, 2, 4):
        jw.north.pixel(xx, 0, VOID)
    jw.north.rect(1, 1, 3, 1, BONE_D)
    for xx in (1, 3, 5):
        sn.pixel(xx, 6, BONE_D)
    # ear flares + headband
    for side, sx in (("right", 1), ("left", -1)):
        flare = m.cube(head, f"ear_flare_{side}", (3.5 if sx > 0 else -4.8, 31.2, -1.6), (4.8 if sx > 0 else -3.5, 34.2, 1.4))
        flare.paint(JADE, jitter=0.1)
        for f in flare.faces("east", "west"):
            f.fill(JADE, noise=0.05).ellipse(f.w / 2, f.h / 2, f.w / 2 - 0.2, f.h / 2 - 0.2, JADE_L)
            f.ellipse(f.w / 2, f.h / 2, f.w / 3, f.h / 3, JADE_D).pixel(f.w // 2, f.h // 2, GOLD)
    band = skull.overlay(0.5, "headband")
    for f in band.sides():
        f.rect(0, 0, f.w, 2, RED)
        f.rect(0, 0, f.w, 1, mix(RED, "#fff", 0.2))
        for xx in range(1, f.w, 3):
            f.rect(xx, 0, 1, 2, JADE)
    band.up.rect(0, 0, band.up.w, 1, RED).rect(0, band.up.h - 1, band.up.w, 1, RED)
    band.up.rect(0, 0, 1, band.up.h, RED).rect(band.up.w - 1, 0, 1, band.up.h, RED)
    # owl on the brow of the headdress
    owl = m.cube(head, "owl_crest", (-2.3, 35.6, -4.4), (2.3, 39.8, -1.6))
    owl.paint("#7a5a38", jitter=0.2)
    on = owl.north
    on.fill("#8a6a44", noise=0.08)
    on.rect(0, 0, 1, 2, VOID).rect(4, 0, 1, 2, VOID)  # ear tufts
    on.ellipse(1.5, 2.2, 1.5, 1.5, "#e8d8b0").ellipse(3.5, 2.2, 1.5, 1.5, "#e8d8b0")
    on.rect(1, 2, 1, 1, "#ffcf3a").rect(3, 2, 1, 1, "#ffcf3a").pixel(1, 2, VOID).pixel(4, 2, VOID)
    on.rect(2, 3, 1, 1, GOLD)  # beak
    on.stripe(mix("#8a6a44", "#000", 0.3), every=2, axis="h", y=3, h=1)
    for f in owl.faces("east", "west", "south"):
        f.stripe(mix("#8a6a44", "#000", 0.3), every=2, axis="h", offset=1)
    # plumes: fan of quetzal feathers
    fan = [("plume_c", 0, 0, 14, 11), ("plume_r1", -22.5, 0, 12, 10), ("plume_r2", -45, 0, 10, 9),
           ("plume_l1", 22.5, 0, 12, 10), ("plume_l2", 45, 0, 10, 9)]
    for name, rot, _, h, w in fan:
        pl = m.plane(plumes, name, (-w / 2, 34, 0.5), (w / 2, 34 + h, 0.5), rotation=(0, 0, rot), pivot=(0, 34, 0.5))
        plume(pl.front, QUETZAL_D, QUETZAL, "#7be8a0", tip=TURQ if abs(rot) > 30 else "#d63a2a")
        pl.front.pixel(pl.front.w // 2, 0, "#ffe58a")
    for k, (off, rot) in enumerate(((-3.8, 0), (3.8, 0))):
        pl = m.plane(plumes, f"plume_back_{k}", (off - 4, 33, 3.4), (off + 4, 45, 3.4))
        plume(pl.front, RED, mix(RED, "#ff6a3a", 0.4), "#ffd0a0", tip=GOLD)
    # --- back plumes (tail feathers falling down the back)
    for k, (cx, hw, ln) in enumerate(((0, 4.5, 24), (-5, 3.5, 20), (5, 3.5, 20))):
        pl = m.plane(back_plumes, f"back_plume_{k}", (cx - hw, 27 - ln, 3.9 + k * 0.1), (cx + hw, 27, 3.9 + k * 0.1))
        pl.front.flip() if False else None
        plume(pl.front, QUETZAL_D, QUETZAL, "#7be8a0", tip="#d63a2a")
        pl.front.px[:] = pl.front.px[::-1]  # base at the neck, tip at the bottom

    # --- arms
    shoulder = bony(m.cube(arm_r, "shoulder_right", (5.2, 24.4, -2.2), (8.6, 27.6, 2)))
    shoulder.up.rect(0, 0, shoulder.up.w, 1, BONE_D)
    upper = bony(m.cube(arm_r, "upper_arm_right", (5.6, 18, -1.3), (8, 25, 1.3)))
    upper.north.rect(0, 0, 1, 6, VOID)
    elbow = m.cube(fore_r, "elbow_right", (5.3, 17, -1.7), (8.3, 19, 1.7))
    elbow.paint(BONE_D, jitter=0.2)
    fore = bony(m.cube(fore_r, "lower_arm_right", (5.7, 11, -1.1), (7.9, 17.5, 1.1)))
    brace = fore.overlay(0.4, "bracelet_right")
    for f in brace.sides():
        f.rect(0, 0, f.w, 2, JADE).rect(0, 0, f.w, 1, JADE_L).rect(0, 2, f.w, 1, JADE_D)
        f.pixel(1, 1, GOLD)
    hand = m.cube(fore_r, "hand_right", (5.4, 8.2, -1.6), (8.2, 11.4, 1.6))
    hand.paint(BONE_D, jitter=0.2)
    hand.north.stripe(BONE, every=2, axis="v", offset=0, y=0, h=3)
    shoulder.mirror_x(arm_l, "shoulder_left")
    upper.mirror_x(arm_l, "upper_arm_left")
    elbow.mirror_x(fore_l, "elbow_left")
    fore.mirror_x(fore_l, "lower_arm_left")
    brace.mirror_x(fore_l, "bracelet_left")
    hand.mirror_x(fore_l, "hand_left")
    # fingers: long bone claws as a plane
    for side, bone, sx in (("right", fore_r, 1), ("left", fore_l, -1)):
        fg = m.plane(bone, f"fingers_{side}", (sx * 5.5 - 1.4, 4.6, -1.65) if False else (sx * 6.8 - 1.4, 4.6, -1.65),
                     (sx * 6.8 + 1.4, 8.4, -1.65))
        for xx in (0, 2):
            fg.front.rect(xx, 0, 1, fg.front.h, BONE)
            fg.front.pixel(xx, fg.front.h - 1, BONE_D)
        fg.front.rect(0, 0, fg.front.w, 1, BONE_D)
        fg.front.clear(1, 0, 1, fg.front.h)

    # --- macuahuitl: wooden paddle edged with obsidian shards
    grip = m.cube(weapon, "weapon_grip", (5.5, 5.5, -1), (7.8, 14, 1))
    grip.paint(WOOD, jitter=0.2)
    for f in grip.sides():
        f.stripe(mix(WOOD, "#000", 0.4), every=2, axis="h", offset=1)
    pommel = m.cube(weapon, "weapon_pommel", (5.1, 3.8, -1.5), (8.2, 5.8, 1.5))
    pommel.paint(JADE, jitter=0.12).down.fill(JADE_D)
    paddle = m.cube(weapon, "weapon_paddle", (5.0, 14, -0.9), (8.6, 32, 0.9))
    paddle.paint(WOOD, jitter=0.2, noise=0.07)
    for f in paddle.sides():
        f.stripe(mix(WOOD, "#000", 0.3), every=5, axis="h", offset=2)
    paddle.north.decal(GLYPH, {"r": GOLD}, x=0, y=3).decal(GLYPH, {"r": GOLD}, x=0, y=10)
    paddle.north.rect(0, 0, paddle.north.w, 1, RED)
    paddle.south.copy_from(paddle.north) if False else None
    for side, x0 in (("l", 4.1), ("r", 8.6)):
        blade = m.cube(weapon, f"obsidian_{side}", (x0, 15, -1.3), (x0 + 0.9, 31.5, 1.3))
        blade.paint(OBS, jitter=0.2, noise=0.1)
        for f in blade.sides():
            speckle(f, OBS_L, 0.18)
            f.stripe(mix(OBS, "#000", 0.3), every=3, axis="h", offset=1)
        blade.north.jagged(OBS, edge="top", depth=(0, 1)) if False else None
    tip = m.cube(weapon, "obsidian_tip", (5.4, 31.8, -0.8), (8.2, 34, 0.8))
    tip.paint(OBS, jitter=0.2)
    speckle(tip.north, OBS_L, 0.25)

    # --- animations
    idle = m.animation("idle", 2.8, "loop")
    idle_breathe(idle, body="torso", head="head", arms=("arm_left", "arm_right"), amount=0.25, nod=2.5, arm_out=3)
    idle.rot("plumes", {0: (0, 0, 2), 1.4: (0, 0, -2), 2.8: (0, 0, 2)})
    idle.rot("back_plumes", {0: (2, 0, 0), 1.4: (-1, 0, 0), 2.8: (2, 0, 0)})
    idle.rot("jaw", {0: (0, 0, 0), 1.4: (3, 0, 0), 2.8: (0, 0, 0)})
    idle.rot("weapon", {0: (0, 0, 0), 1.4: (2, 0, 0), 2.8: (0, 0, 0)})

    walk = m.animation("walk", 1.5, "loop")
    walk_biped(walk, legs=("leg_left", "leg_right"), shins=("shin_left", "shin_right"), arms=("arm_left", "arm_right"),
               body="body", head="head", swing=18, arm_swing=10, bob=0.5, sway=2, knee=22)
    L = walk.length
    walk.rot("arm_right", {0: (-4, 0, 3), L / 2: (4, 0, 3), L: (-4, 0, 3)})
    walk.rot("plumes", {0: (0, 0, 3), L / 4: (-3, 0, 0), L / 2: (0, 0, -3), 3 * L / 4: (-3, 0, 0), L: (0, 0, 3)})
    walk.rot("back_plumes", {0: (4, 0, 0), L / 2: (-2, 0, 0), L: (4, 0, 0)})
    walk.rot("loin_front", {0: (-3, 0, 0), L / 4: (4, 0, 0), L / 2: (-3, 0, 0), 3 * L / 4: (4, 0, 0), L: (-3, 0, 0)})
    walk.rot("loin_back", {0: (3, 0, 0), L / 4: (-4, 0, 0), L / 2: (3, 0, 0), 3 * L / 4: (-4, 0, 0), L: (3, 0, 0)})

    atk = m.animation("attack", 1.3, "once")
    atk.rot("arm_right", {0: (0, 0, 3), 0.45: (-78, 62, 4), 0.62: (-78, -70, 0), 0.82: (-74, -78, 0), 1.3: (0, 0, 3)},
            interp="linear")
    atk.rot("weapon", {0: (0, 0, 0), 0.45: (80, 0, 0), 0.62: (80, 0, 12), 0.82: (80, 0, 12), 1.3: (0, 0, 0)}, interp="linear")
    atk.rot("torso", {0: (0, 0, 0), 0.45: (0, 34, 0), 0.62: (6, -36, 0), 0.82: (6, -36, 0), 1.3: (0, 0, 0)}, interp="linear")
    atk.rot("arm_left", {0: (0, 0, 0), 0.45: (-20, 0, -30), 0.62: (-40, 0, -12), 1.3: (0, 0, 0)}, interp="linear")
    atk.rot("head", {0: (0, 0, 0), 0.45: (-6, 25, 0), 0.62: (6, -30, 0), 1.3: (0, 0, 0)}, interp="linear")
    atk.rot("jaw", {0: (0, 0, 0), 0.45: (10, 0, 0), 0.62: (35, 0, 0), 1.3: (0, 0, 0)}, interp="linear")
    atk.rot("plumes", {0: (0, 0, 0), 0.45: (4, 0, -8), 0.62: (-6, 0, 10), 1.3: (0, 0, 0)}, interp="linear")
    atk.pos("body", {0: (0, 0, 0), 0.45: (0, 0, 1), 0.62: (0, -0.6, -2), 1.3: (0, 0, 0)}, interp="linear")

    death = m.animation("death", 2.4, "once")
    death_fall(death, direction="back")
    death.rot("arm_right", {0: (0, 0, 3), 0.6: (-20, 0, 20), 1.6: (-60, 0, 45), 2.4: (-60, 0, 45)}, interp="linear")
    death.rot("weapon", {0: (0, 0, 0), 0.8: (40, 0, 0), 2.4: (40, 0, 0)}, interp="linear")
    death.rot("arm_left", {0: (0, 0, 0), 0.6: (-30, 0, -20), 1.6: (-70, 0, -40), 2.4: (-70, 0, -40)}, interp="linear")
    death.rot("head", {0: (0, 0, 0), 0.5: (-22, 0, 0), 1.5: (12, 22, 0), 2.4: (12, 22, 0)}, interp="linear")
    death.rot("jaw", {0: (0, 0, 0), 0.8: (40, 0, 0), 2.4: (45, 0, 0)}, interp="linear")
    death.rot("plumes", {0: (0, 0, 0), 1.0: (20, 0, 10), 2.4: (30, 0, 14)}, interp="linear")
    return m


# =====================================================================================
# XOLOTL - hairless slate dog, guide of the dead, painted ribs and a jade collar
# =====================================================================================
SLATE = "#3d4656"
SLATE_D = "#242a36"
SLATE_L = "#5f6b80"
EMBER = "#ff7a1f"
EMBER_H = "#ffd24a"
PAINT = "#e4dcc4"


def build_xolotl():
    m = Model("bigcasares_myth_xolotl", texture_size=(128, 128))

    body = m.bone("body", pivot=(0, 11, 0))
    chest = m.bone("chest", body, pivot=(0, 15, -4))
    head = m.bone("head", chest, pivot=(0, 17, -8))
    jaw = m.bone("jaw", head, pivot=(0, 16.5, -9))
    tail = m.bone("tail", body, pivot=(0, 17, 7))
    fl = m.bone("leg_front_left", body, pivot=(-3.4, 11, -5))
    fr = m.bone("leg_front_right", body, pivot=(3.4, 11, -5))
    bl = m.bone("leg_back_left", body, pivot=(-3.4, 11, 5.5))
    br = m.bone("leg_back_right", body, pivot=(3.4, 11, 5.5))

    # --- body with painted ribs (bone-white paint over slate hide)
    torso = m.cube(body, "torso", (-3.8, 9, -2), (3.8, 17.5, 9))
    torso.paint(SLATE, noise=0.05, jitter=0.18)
    for f in (torso.east, torso.west):
        for k in range(5):
            xx = 1 + k * 2
            f.line(xx, 1, xx + 1, 5, PAINT).line(xx + 1, 1, xx + 2, 5, mix(PAINT, SLATE, 0.5))
        f.rect(0, 0, f.w, 1, SLATE_D)
        f.stripe(SLATE_D, every=5, axis="h", offset=3, y=6, h=3)
    torso.up.rect(torso.up.w // 2 - 1, 0, 1, torso.up.h, PAINT)
    for yy in range(0, torso.up.h, 2):
        torso.up.pixel(torso.up.w // 2 - 1, yy, SLATE_D)
    torso.down.fill(SLATE_L, noise=0.05)
    hips = m.cube(body, "hip_spine", (-3, 11, 7), (3, 16.5, 10.5))
    hips.paint(SLATE, noise=0.06, jitter=0.2)
    hips.up.rect(2, 0, 2, 3, SLATE_L)
    ch = m.cube(chest, "chest_block", (-4.4, 10.5, -8), (4.4, 18.5, -1))
    ch.paint(SLATE, noise=0.05, jitter=0.18)
    for f in ch.sides():
        f.rect(0, 0, f.w, 1, SLATE_D)
    for k in range(4):
        ch.east.line(1 + k * 2, 2, 2 + k * 2, 6, PAINT)
        ch.west.line(1 + k * 2, 2, 2 + k * 2, 6, PAINT)
    ch.north.rect(3, 3, 3, 3, SLATE_L)
    ch.north.line(4, 3, 4, 8, PAINT).line(2, 5, 6, 5, mix(PAINT, SLATE, 0.4))
    collar = m.cube(chest, "jade_collar", (-4.9, 14, -8.7), (4.9, 18.4, -5.6))
    collar.paint(JADE, jitter=0.12)
    for f in collar.sides():
        f.rect(0, 0, f.w, 1, JADE_L)
        f.stripe(JADE_D, every=2, axis="v", offset=1, y=1, h=2)
        f.rect(0, 3, f.w, 1, GOLD)
    collar.north.rect(4, 1, 2, 3, RED).pixel(4, 1, "#ff7a5a")
    collar.up.fill(SLATE_D)

    # --- head: skull, snout, jaw, ragged backswept ears
    skull = m.cube(head, "skull", (-3.4, 16, -13), (3.4, 23, -6.2))
    skull.paint(SLATE, noise=0.05, jitter=0.18)
    sn = skull.north
    sn.rect(1, 3, 2, 1, "#0a0c10").rect(4, 3, 2, 1, "#0a0c10")
    sn.pixel(2, 3, EMBER_H).pixel(4, 3, EMBER_H).pixel(1, 3, EMBER).pixel(5, 3, EMBER)
    sn.line(0, 2, 2, 2, SLATE_D).line(7, 2, 5, 2, SLATE_D)
    sn.rect(3, 0, 1, 2, SLATE_D)  # forehead wrinkle
    skull.up.stripe(SLATE_D, every=3, axis="h", offset=1, x=1, w=skull.up.w - 2)
    snout = m.cube(head, "snout", (-2.2, 16.2, -18), (2.2, 20.8, -12.6))
    snout.paint(SLATE_L, jitter=0.15)
    snout.north.rect(1, 0, 2, 1, "#0a0c10").pixel(0, 3, SLATE_D).pixel(3, 3, SLATE_D)
    snout.up.rect(1, 0, 2, 1, "#0a0c10")
    for f in (snout.east, snout.west):
        f.rect(0, f.h - 1, f.w, 1, PAINT)
        for xx in range(1, f.w, 2):
            f.pixel(xx, f.h - 1, mix(PAINT, "#000", 0.3))
    snout.down.fill("#6a2a38")
    jw = m.cube(jaw, "lower_jaw", (-1.9, 15, -17.4), (1.9, 16.8, -9))
    jw.paint(SLATE, jitter=0.15)
    jw.up.fill("#6a2a38").rect(1, 1, 2, 5, "#c85a6a")
    for f in jw.sides():
        f.rect(0, 0, f.w, 1, PAINT)
    # eye paint: white war stripes under the eyes
    skull.north.pixel(1, 4, PAINT).pixel(5, 4, PAINT).pixel(1, 5, PAINT).pixel(5, 5, PAINT)
    for side, sx in (("right", 1), ("left", -1)):
        ear = m.plane(head, f"ear_{side}", (sx * 2.4 - 2.2, 22, -7.4), (sx * 2.4 + 2.2, 28.6, -7.4), rotation=(22.5, 0, 0),
                      pivot=(0, 22, -7.4))
        ef = ear.front
        for y in range(ef.h):
            half = (ef.w / 2) * (1 - 0.75 * y / ef.h)
            for x in range(ef.w):
                if abs(x + 0.5 - ef.w / 2) <= half:
                    ef.pixel(x, y, SLATE_L if abs(x + 0.5 - ef.w / 2) > half - 1 else SLATE)
        ef.rect(ef.w // 2 - 1, 2, 2, ef.h - 3, mix(SLATE, "#c88aa0", 0.4))
        ef.tatter(2, edge="top", chance=0.0)
        ef.noise(0.06)

    # --- legs and tail
    def leg(bone, name, x0, z0):
        up = m.cube(bone, f"{name}_upper", (x0, 5.5, z0), (x0 + 2.8, 11, z0 + 2.8))
        up.paint(SLATE, noise=0.05, jitter=0.18)
        for f in up.sides():
            f.stripe(SLATE_D, every=4, axis="h", offset=1)
        low = m.cube(bone, f"{name}_lower", (x0 + 0.3, 1.8, z0 + 0.3), (x0 + 2.5, 5.8, z0 + 2.5))
        low.paint(SLATE_D, jitter=0.15)
        low.north.rect(0, 0, 1, 4, SLATE)
        paw = m.cube(bone, f"{name}_paw", (x0 - 0.2, 0, z0 - 1.3), (x0 + 3, 2, z0 + 2.8))
        paw.paint(SLATE_L, jitter=0.18)
        for xx in (0, 1, 2):
            paw.north.pixel(xx, 1, "#14161c")
        return up, low, paw

    parts = leg(fr, "foreleg_right", 2.0, -6.4)
    for src, nm in zip(parts, ("foreleg_left_upper", "foreleg_left_lower", "foreleg_left_paw")):
        src.mirror_x(fl, nm)
    parts = leg(br, "hindleg_right", 2.0, 4.2)
    for src, nm in zip(parts, ("hindleg_left_upper", "hindleg_left_lower", "hindleg_left_paw")):
        src.mirror_x(bl, nm)
    tl = m.cube(tail, "tail_rope", (-0.9, 9.5, 8.6), (0.9, 18, 10.4))
    tl.paint(SLATE, jitter=0.2)
    tl.north.rect(0, 0, 1, 8, SLATE_D)
    tl.up.rect(0, 0, 2, 2, EMBER)

    # --- animations
    idle = m.animation("idle", 2.2, "loop")
    idle_breathe(idle, body="body", head="head", tail="tail", amount=0.3, nod=4)
    idle.rot("jaw", {0: (0, 0, 0), 1.1: (3, 0, 0), 2.2: (0, 0, 0)})
    idle.rot("chest", {0: (0, 0, 0), 1.1: (-2, 0, 0), 2.2: (0, 0, 0)})

    walk = m.animation("walk", 0.7, "loop")
    walk_quadruped(walk, front=("leg_front_left", "leg_front_right"), back=("leg_back_left", "leg_back_right"),
                   swing=30, body="body", head="head", tail="tail", bob=0.6, gait="trot")
    L = walk.length
    walk.rot("chest", {0: (1, -3, 0), L / 2: (1, 3, 0), L: (1, -3, 0)})
    walk.rot("jaw", {0: (2, 0, 0), L / 2: (6, 0, 0), L: (2, 0, 0)})

    atk = m.animation("attack", 0.9, "once")
    atk.rot("head", {0: (0, 0, 0), 0.25: (-18, 0, 0), 0.38: (18, 0, 0), 0.6: (6, 0, 0), 0.9: (0, 0, 0)}, interp="linear")
    atk.rot("jaw", {0: (0, 0, 0), 0.25: (50, 0, 0), 0.38: (-2, 0, 0), 0.46: (24, 0, 0), 0.54: (-2, 0, 0), 0.9: (0, 0, 0)},
            interp="linear")
    atk.pos("body", {0: (0, 0, 0), 0.25: (0, -1, 2.5), 0.38: (0, 0.5, -7), 0.6: (0, 0, -3), 0.9: (0, 0, 0)}, interp="linear")
    atk.rot("chest", {0: (0, 0, 0), 0.25: (-6, 0, 0), 0.38: (8, 0, 0), 0.9: (0, 0, 0)}, interp="linear")
    for n in ("leg_front_left", "leg_front_right"):
        atk.rot(n, {0: (0, 0, 0), 0.25: (-14, 0, 0), 0.38: (22, 0, 0), 0.9: (0, 0, 0)}, interp="linear")
    atk.rot("tail", {0: (0, 0, 0), 0.38: (-14, 14, 0), 0.9: (0, 0, 0)}, interp="linear")

    death = m.animation("death", 1.6, "once")
    death_fall(death, direction="right")
    death.rot("head", {0: (0, 0, 0), 0.5: (-12, 12, 0), 1.2: (10, 22, 0), 1.6: (10, 22, 0)}, interp="linear")
    death.rot("jaw", {0: (0, 0, 0), 0.8: (24, 0, 0), 1.6: (28, 0, 0)}, interp="linear")
    for n, ang in (("leg_front_left", -25), ("leg_front_right", -30), ("leg_back_left", 22), ("leg_back_right", 26)):
        death.rot(n, {0: (0, 0, 0), 0.6: (ang, 0, 0), 1.6: (ang, 0, 0)}, interp="linear")
    death.rot("tail", {0: (0, 0, 0), 0.8: (0, 25, 0), 1.6: (0, 25, 0)}, interp="linear")
    return m


# =====================================================================================
# CAMAZOTZ BAT - great leaf-nosed bat of the House of Bats
# =====================================================================================
FUR = "#2b1f1a"
FUR_D = "#140e0c"
FUR_L = "#54382c"
MEMB = "#52304a"
MEMB_D = "#1e1220"
MEMB_L = "#6a4058"
BLOOD = "#ff2a22"


def membrane(face, wrist, fingers, edge_depth, tip_drop=3):
    """Cut a wing-panel silhouette, then paint leathery veins and finger-bone rays.
    ``wrist``: (x, y) where the finger bones meet; ``fingers``: end points on the trailing edge."""
    w, h = face.w, face.h
    face.shaded(MEMB, noise=0.07, jitter=0.18, shade=False)
    face.dither(MEMB_L, density=0.04, pattern="noise")
    for x in range(w):
        t = x / max(1, w - 1)
        top = int(tip_drop * t)
        face.clear(x, 0, 1, top)
        bottom = h - int(edge_depth * (1 - abs(2 * ((x * len(fingers) / w) % 1) - 1)))
        face.clear(x, bottom, 1, h - bottom)
    for fx, fy in fingers:
        face.line(wrist[0], wrist[1], fx, fy, "#a47a82")
        face.line(wrist[0], wrist[1] + 1, fx, fy, MEMB_D)
    face.rect(0, 0, w, 1, MEMB_D)
    return face


def build_camazotz_bat():
    m = Model("bigcasares_myth_camazotz_bat", texture_size=(128, 128))

    body = m.bone("body", pivot=(0, 10, 0))
    head = m.bone("head", body, pivot=(0, 14, -2))
    jaw = m.bone("jaw", head, pivot=(0, 15, -4))
    wing_r = m.bone("wing_right", body, pivot=(3.2, 12.5, 0))
    wing_r2 = m.bone("wing_right_outer", wing_r, pivot=(10.5, 12.5, 0))
    wing_l = m.bone("wing_left", body, pivot=(-3.2, 12.5, 0))
    wing_l2 = m.bone("wing_left_outer", wing_l, pivot=(-10.5, 12.5, 0))
    foot_r = m.bone("foot_right", body, pivot=(1.6, 5.5, 1))
    foot_l = m.bone("foot_left", body, pivot=(-1.6, 5.5, 1))

    # --- body: furred torso, pale chest tuft
    torso = m.cube(body, "torso", (-3.4, 5.5, -2.8), (3.4, 15, 3))
    torso.paint(FUR, noise=0.06, jitter=0.2)
    for f in torso.faces():
        f.strands(FUR_D, density=0.35, length=(2, 4))
        speckle(f, FUR_L, 0.08)
    tuft = torso.overlay(0.35, "chest_fur")
    tuft.north.rect(1, 1, 5, 6, FUR_L).jagged(FUR_L, edge="bottom", depth=(1, 3), x=1, w=5)
    tuft.north.strands(mix(FUR_L, "#fff", 0.2), density=0.3, length=(2, 3), x=1, w=5)
    tuft.up.rect(1, 0, 5, 1, FUR_L)
    tuft.east.jagged(FUR, edge="top", depth=(1, 2))
    tuft.west.jagged(FUR, edge="top", depth=(1, 2))
    collar = m.cube(body, "ruff", (-3.9, 13, -3.3), (3.9, 15.8, 3.5))
    collar.paint(FUR_L, noise=0.08, jitter=0.25)
    for f in collar.sides():
        f.jagged(FUR_L, edge="bottom", depth=(1, 3))
        f.strands(FUR, density=0.3, length=(1, 3))
    # --- head
    skull = m.cube(head, "skull", (-3, 14.5, -5), (3, 20, 0.5))
    skull.paint(FUR, noise=0.06, jitter=0.2)
    sn = skull.north
    sn.rect(0, 1, 6, 1, FUR_D)
    sn.rect(0, 2, 2, 2, "#0a0608").rect(4, 2, 2, 2, "#0a0608")
    sn.pixel(1, 3, BLOOD).pixel(4, 3, BLOOD).pixel(0, 2, mix(BLOOD, "#000", 0.5)).pixel(5, 2, mix(BLOOD, "#000", 0.5))
    sn.pixel(1, 2, "#ff9a7a").pixel(4, 2, "#ff9a7a")
    for f in skull.faces():
        f.strands(FUR_D, density=0.2, length=(1, 2))
    snout = m.cube(head, "snout", (-1.9, 14.2, -7), (1.9, 17.6, -4.6))
    snout.paint(FUR_L, jitter=0.15)
    snout.north.rect(0, 1, 1, 1, "#0a0608").rect(3, 1, 1, 1, "#0a0608")
    snout.north.rect(0, 2, 4, 1, "#5a2a3a")
    snout.up.fill(FUR_L, noise=0.06)
    jw = m.cube(jaw, "lower_jaw", (-1.5, 13.2, -6.2), (1.5, 14.9, -2))
    jw.paint(FUR_D, jitter=0.15)
    jw.up.fill("#5a1a28").rect(0, 0, 3, 1, BONE)
    for side, sx in (("right", 1), ("left", -1)):
        x0 = 0.5 if sx > 0 else -1.5
        fang = m.cube(head, f"fang_{side}", (x0, 12.2, -6.7), (x0 + 1, 14.6, -5.7))
        fang.paint(BONE, jitter=0.1)
        fang.north.rect(0, fang.north.h - 1, 1, 1, BONE_D)
    # --- nose leaf: obsidian blade (two crossed alpha planes)
    leaf_a = m.plane(head, "nose_leaf_side", (0, 17.2, -7.2), (0, 23.4, -4.2))
    leaf_b = m.plane(head, "nose_leaf_front", (-1.6, 17.2, -5.7), (1.6, 23.4, -5.7))
    for pl, wide in ((leaf_a, 3), (leaf_b, 3)):
        fc = pl.front
        for y in range(fc.h):
            t = y / max(1, fc.h - 1)
            half = (fc.w / 2) * (1 - t * 0.95)
            for x in range(fc.w):
                if abs(x + 0.5 - fc.w / 2) <= max(0.5, half):
                    fc.pixel(x, fc.h - 1 - y, OBS)
        fc.rect(fc.w // 2, 0, 1, fc.h, OBS_L)
        speckle(fc, OBS_L, 0.15)
        fc.pixel(fc.w // 2, 0, "#c8c8ee")
    # --- ears
    for side, sx in (("right", 1), ("left", -1)):
        ear = m.plane(head, f"ear_{side}", (sx * 2.2 - 2, 19.5, -1.4), (sx * 2.2 + 2, 28, -1.4),
                      rotation=(0, 0, -22.5 * sx), pivot=(sx * 2.2, 19.5, -1.4))
        ef = ear.front
        for y in range(ef.h):
            half = (ef.w / 2) * (1 - 0.85 * y / ef.h)
            for x in range(ef.w):
                if abs(x + 0.5 - ef.w / 2) <= half:
                    ef.pixel(x, y, FUR if abs(x + 0.5 - ef.w / 2) > half - 1 else mix("#6a3a4a", FUR, 0.2))
        for yy in range(2, ef.h, 2):
            ef.rect(ef.w // 2 - 1, yy, 2, 1, "#3a1a28")
        ef.noise(0.08)
    # --- wings: two-bone arm, membrane panels, claw thumb
    up_r = m.cube(wing_r, "wing_arm_inner_right", (3.2, 11.7, -1.2), (10.8, 13.7, 1.2))
    up_r.paint(FUR, jitter=0.2)
    for f in up_r.sides():
        f.strands(FUR_D, density=0.3, length=(1, 3))
    out_r = m.cube(wing_r2, "wing_arm_outer_right", (10.5, 11.9, -1), (19, 13.5, 1))
    out_r.paint(FUR_D, jitter=0.2)
    out_r.north.stripe(FUR_L, every=3, axis="v", offset=1)
    thumb = m.cube(wing_r2, "wing_thumb_right", (9.8, 11.4, -4.4), (11.4, 13.2, -1))
    thumb.paint(BONE, jitter=0.1)
    thumb.north.rect(0, thumb.north.h - 1, 1, 1, BONE_D)
    m1 = m.plane(wing_r, "membrane_inner_right", (3.2, 12.6, -0.6), (10.8, 12.6, 11))
    membrane(m1.front, (m1.front.w - 1, 0), [(0, 10), (3, 11)], 3, tip_drop=0)
    m1.front.clear(0, 0, 1, 0)
    m2 = m.plane(wing_r2, "membrane_outer_right", (10.5, 12.6, -0.5), (21, 12.6, 14))
    membrane(m2.front, (0, 0), [(w, h) for w, h in ((3, 12), (6, 13), (9, 12), (10, 7))], 3, tip_drop=5)
    m2b = m.plane(wing_r2, "membrane_tip_right", (19, 12.5, -0.5), (24, 12.5, 7))
    membrane(m2b.front, (0, 0), [(4, 6), (2, 6)], 2, tip_drop=4)
    for src, dst, nm in ((up_r, wing_l, "wing_arm_inner_left"), (out_r, wing_l2, "wing_arm_outer_left"),
                         (thumb, wing_l2, "wing_thumb_left"), (m1, wing_l, "membrane_inner_left"),
                         (m2, wing_l2, "membrane_outer_left"), (m2b, wing_l2, "membrane_tip_left")):
        src.mirror_x(dst, nm)
    # --- feet and tail membrane
    leg = m.cube(foot_r, "leg_right", (1, 1.6, 0.2), (2.4, 6, 1.8))
    leg.paint(FUR_D, jitter=0.2)
    claw = m.cube(foot_r, "claw_right", (0.8, 0, -0.4), (2.6, 1.8, 2.2))
    claw.paint(BONE_D, jitter=0.15)
    claw.north.stripe(BONE, every=2, axis="v", offset=0)
    leg.mirror_x(foot_l, "leg_left")
    claw.mirror_x(foot_l, "claw_left")
    tail = m.plane(body, "tail_membrane", (-3.4, 5, 3), (3.4, 5, 10))
    tf = tail.front
    tf.shaded(MEMB, noise=0.07, jitter=0.15, shade=False)
    for x in range(tf.w):
        tf.clear(x, tf.h - 1 - abs(x - tf.w // 2) // 2 - (x % 2), 1, tf.h)
    tf.line(tf.w // 2, 0, tf.w // 2, tf.h - 2, MEMB_D).line(0, 0, 1, tf.h - 3, MEMB_D).line(tf.w - 1, 0, tf.w - 2, tf.h - 3, MEMB_D)

    # --- animations
    def beat(anim, amp, outer, period, bob):
        L = anim.length
        keys_in = {}
        keys_out = {}
        keys_body = {}
        for k in range(9):
            t = k / 8
            a = math.sin(2 * math.pi * t)
            keys_in[L * t] = a
            keys_out[L * t] = math.sin(2 * math.pi * (t - 0.12))
            keys_body[L * t] = (0, -bob * a, 0)
        anim.rot("wing_right", {t: (0, 0, amp * v) for t, v in keys_in.items()})
        anim.rot("wing_left", {t: (0, 0, -amp * v) for t, v in keys_in.items()})
        anim.rot("wing_right_outer", {t: (0, 0, outer * v) for t, v in keys_out.items()})
        anim.rot("wing_left_outer", {t: (0, 0, -outer * v) for t, v in keys_out.items()})
        anim.pos("body", keys_body)

    idle = m.animation("idle", 0.9, "loop")
    beat(idle, 22, 22, 0.9, 0.7)
    idle.rot("head", {0: (0, 0, 0), 0.45: (3, 0, 0), 0.9: (0, 0, 0)})
    idle.rot("jaw", {0: (0, 0, 0), 0.45: (6, 0, 0), 0.9: (0, 0, 0)})
    idle.rot("foot_right", {0: (8, 0, 0), 0.45: (14, 0, 0), 0.9: (8, 0, 0)})
    idle.rot("foot_left", {0: (14, 0, 0), 0.45: (8, 0, 0), 0.9: (14, 0, 0)})

    walk = m.animation("walk", 0.6, "loop")
    beat(walk, 40, 34, 0.6, 1.2)
    walk.rot("body", {0: (10, 0, 0), 0.3: (14, 0, 0), 0.6: (10, 0, 0)})
    walk.rot("head", {0: (-8, 0, 0), 0.3: (-4, 0, 0), 0.6: (-8, 0, 0)})
    walk.rot("foot_right", {0: (20, 0, 0), 0.6: (20, 0, 0)})
    walk.rot("foot_left", {0: (20, 0, 0), 0.6: (20, 0, 0)})

    atk = m.animation("attack", 0.9, "once")
    atk.pos("body", {0: (0, 0, 0), 0.25: (0, 2.5, 4), 0.42: (0, -3, -10), 0.6: (0, -1, -6), 0.9: (0, 0, 0)}, interp="linear")
    atk.rot("body", {0: (0, 0, 0), 0.25: (-18, 0, 0), 0.42: (40, 0, 0), 0.6: (18, 0, 0), 0.9: (0, 0, 0)}, interp="linear")
    atk.rot("head", {0: (0, 0, 0), 0.25: (-10, 0, 0), 0.42: (10, 0, 0), 0.9: (0, 0, 0)}, interp="linear")
    atk.rot("jaw", {0: (0, 0, 0), 0.25: (10, 0, 0), 0.38: (58, 0, 0), 0.46: (-2, 0, 0), 0.9: (0, 0, 0)}, interp="linear")
    atk.rot("wing_right", {0: (0, 0, 0), 0.25: (0, 0, 50), 0.42: (0, 0, -28), 0.6: (0, 0, 12), 0.9: (0, 0, 0)}, interp="linear")
    atk.rot("wing_left", {0: (0, 0, 0), 0.25: (0, 0, -50), 0.42: (0, 0, 28), 0.6: (0, 0, -12), 0.9: (0, 0, 0)}, interp="linear")
    atk.rot("wing_right_outer", {0: (0, 0, 0), 0.25: (0, 0, 30), 0.42: (0, 0, -34), 0.9: (0, 0, 0)}, interp="linear")
    atk.rot("wing_left_outer", {0: (0, 0, 0), 0.25: (0, 0, -30), 0.42: (0, 0, 34), 0.9: (0, 0, 0)}, interp="linear")
    atk.rot("foot_right", {0: (0, 0, 0), 0.42: (-50, 0, 0), 0.9: (0, 0, 0)}, interp="linear")
    atk.rot("foot_left", {0: (0, 0, 0), 0.42: (-50, 0, 0), 0.9: (0, 0, 0)}, interp="linear")

    death = m.animation("death", 1.6, "once")
    death.rot("wing_right", {0: (0, 0, 0), 0.3: (0, 0, 40), 0.9: (0, 0, -70), 1.6: (0, 0, -78)}, interp="linear")
    death.rot("wing_left", {0: (0, 0, 0), 0.3: (0, 0, -40), 0.9: (0, 0, 70), 1.6: (0, 0, 78)}, interp="linear")
    death.rot("wing_right_outer", {0: (0, 0, 0), 0.9: (0, 0, -40), 1.6: (0, 0, -55)}, interp="linear")
    death.rot("wing_left_outer", {0: (0, 0, 0), 0.9: (0, 0, 40), 1.6: (0, 0, 55)}, interp="linear")
    death.rot("body", {0: (0, 0, 0), 0.3: (-12, 0, 6), 1.0: (70, 0, 10), 1.3: (88, 0, 10), 1.6: (86, 0, 10)}, interp="linear")
    death.pos("body", {0: (0, 0, 0), 0.3: (0, 1, 1), 1.0: (0, -3, -4), 1.3: (0, -6, -5.5), 1.6: (0, -6.2, -5.5)}, interp="linear")
    death.rot("head", {0: (0, 0, 0), 0.5: (-20, 0, 0), 1.6: (15, 20, 0)}, interp="linear")
    death.rot("jaw", {0: (0, 0, 0), 0.8: (40, 0, 0), 1.6: (44, 0, 0)}, interp="linear")
    return m


MODELS = {
    "bigcasares_myth_xibalba_lord": build_xibalba_lord,
    "bigcasares_myth_xolotl": build_xolotl,
    "bigcasares_myth_camazotz_bat": build_camazotz_bat,
}
