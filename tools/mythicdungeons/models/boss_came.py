"""Hun Came and Vucub Came, the two lords of Xibalba (boss of the Mesoamerican dungeon), with
their rubber ball and the stone ballcourt hoop.

Both lords come from one builder: skeletal Maya death lords with owl-feather headdresses, jade
collars, a stone ballgame yoke on the hips, a kneepad and a glyph loincloth.  Hun Came ("One
Death") is taller and darker (charred bone, obsidian and blood red, a great owl crest and a tall
black fan of owl feathers); Vucub Came ("Seven Death") is pale bone and jade with the seven-marks
motif (seven red dots on the brow, seven bars on the loincloth, seven discs on the headband).
Relief comes from overlay layers and alpha planes, not from extra cubes.  Model faces north (-Z);
the lord's right is +X.  Clips the fight requests: idle, walk, attack, slam, dash, throw, revive,
rage, death (revive = kneeling with the right hand near the floor beside the fallen brother).

The hoop is a vertical stone ring (octagon of rotated bars) whose centre is 3 blocks above the
ground; two inner rings (``ring_mid``/``ring_inner``) are hidden at first and slide in with each
goal (``narrow_1``/``narrow_2``, held clips).  The ball is about 1.5 blocks wide (the hitbox of a
size-3 slime) and rounded with three crossed boxes.
"""
import math
import sys
from pathlib import Path

import numpy as np

sys.path.insert(0, str(Path(__file__).resolve().parents[1]))
from bbkit import Model, death_fall, idle_breathe, mix, walk_biped  # noqa: E402

VOID = "#140f0c"
JADE = "#2fb58a"
JADE_D = "#1b7459"
JADE_L = "#92e6c4"
RED = "#b3281f"
RED_D = "#6e1610"
GOLD = "#d8a830"
GOLD_L = "#ffe58a"
LINEN = "#e6dcc0"
LEATHER = "#7a4a28"
OWL = "#6a5236"
OWL_D = "#3a2a1c"
OWL_L = "#c8b088"
STONE = "#8a8a78"
STONE_D = "#545446"
STONE_L = "#b4b29c"
RUBBER = "#2e2622"
RUBBER_L = "#5a4a40"

REQUIRED_ANIMATIONS = ("idle", "death")  # props; the lords check their own clips below
LORD_CLIPS = ("idle", "walk", "attack", "slam", "dash", "throw", "revive", "rage", "death")

VARIANTS = {
    # H = hip height in units; Hun is the taller, darker brother
    "hun": dict(H=22, bone="#9c9078", bone_d="#4e463a", eye="#ff3a1a", accent="#1c1820",
                accent_l="#4a4458", sash=RED, yoke=("#3a3a40", "#1e1e24", "#62626c"), fan_h=18, fan_w=7,
                fan=("#1a1614", "#3a302a", "#8a7a68"), fan_tip=RED, marks=False),
    "vucub": dict(H=19, bone="#ddd2b4", bone_d="#8d8368", eye="#c8ff4a", accent=JADE, accent_l=JADE_L,
                  sash=JADE_D, yoke=(JADE, JADE_D, JADE_L), fan_h=13, fan_w=8,
                  fan=("#0f5a38", "#1f9a5a", "#7be8a0"), fan_tip=GOLD, marks=True),
}


def speckle(face, color, p=0.2):
    ys, xs = np.nonzero(face.mask())
    for y, x in zip(ys, xs):
        if face.rng.random() < p:
            face.pixel(int(x), int(y), color)
    return face


def bony(cube, base, dark):
    cube.paint(base, noise=0.05, jitter=0.18)
    for f in cube.faces():
        f.cracks(dark, n=1, length=3)
    return cube


def feather(face, c_dark, c_mid, c_light, tip=None, eyes=False):
    """Long feather: pointed oval, light shaft, barbs, coloured tip (or owl 'eye' bars)."""
    w, h = face.w, face.h
    mid = (w - 1) / 2
    for y in range(h):
        t = y / max(1, h - 1)
        half = (w / 2) * (0.45 + 0.55 * math.sin(min(1.0, t * 1.1) * math.pi * 0.6 + 0.45))
        if t > 0.8:
            half *= 0.3 + 0.7 * (1 - t) / 0.2
        for x in range(w):
            if abs(x - mid) <= half:
                u = abs(x - mid) / max(0.5, half)
                col = c_mid if u < 0.65 else c_dark
                if tip and t > 0.82:
                    col = tip if u < 0.8 else mix(tip, "#000", 0.35)
                elif eyes and int(t * 10) % 3 == 1:
                    col = mix(c_light, c_mid, 0.4)
                face.pixel(x, y, col)
    for y in range(h):
        face.pixel(int(round(mid)), y, c_light)
    face.noise(0.06)
    return face


def ring(m, bone, prefix, r_in, thick, depth, cy, paint):
    """Octagonal ring in the XY plane centred at (0, cy, 0): 8 bars, the diagonals are the top
    and bottom bars rotated +-45 degrees about the centre (a Java-safe single-axis angle)."""
    length = 2 * (r_in + thick) * math.tan(math.radians(22.5))
    z0, z1 = -depth / 2, depth / 2
    bars = []
    top = ((-length / 2, cy + r_in, z0), (length / 2, cy + r_in + thick, z1))
    bottom = ((-length / 2, cy - r_in - thick, z0), (length / 2, cy - r_in, z1))
    for name, (frm, to), rot in (("top", top, None), ("top_r", top, (0, 0, -45)), ("top_l", top, (0, 0, 45)),
                                 ("bottom", bottom, None), ("bottom_r", bottom, (0, 0, 45)),
                                 ("bottom_l", bottom, (0, 0, -45))):
        bars.append(m.cube(bone, f"{prefix}_{name}", frm, to, rotation=rot, pivot=(0, cy, 0) if rot else None))
    bars.append(m.cube(bone, f"{prefix}_right", (r_in, cy - length / 2, z0), (r_in + thick, cy + length / 2, z1)))
    bars.append(m.cube(bone, f"{prefix}_left", (-r_in - thick, cy - length / 2, z0), (-r_in, cy + length / 2, z1)))
    for bar in bars:
        paint(bar)
    return bars


# =====================================================================================
# THE LORDS
# =====================================================================================
def build_lord(key, variant):
    v = VARIANTS[variant]
    H = v["H"]
    BONE, BONE_D = v["bone"], v["bone_d"]
    ACC, ACC_L = v["accent"], v["accent_l"]
    m = Model(key, texture_size=(256, 128))

    body = m.bone("body", pivot=(0, H, 0))
    torso = m.bone("torso", body, pivot=(0, H + 4, 0))
    head = m.bone("head", torso, pivot=(0, H + 18, 0))
    jaw = m.bone("jaw", head, pivot=(0, H + 19.5, -2.5))
    crest = m.bone("headdress", head, pivot=(0, H + 26, 1))
    back = m.bone("back_plumes", torso, pivot=(0, H + 16, 3.4))
    arm_r = m.bone("arm_right", torso, pivot=(7.3, H + 16.5, 0))
    fore_r = m.bone("forearm_right", arm_r, pivot=(7.3, H + 7.5, 0))
    arm_l = m.bone("arm_left", torso, pivot=(-7.3, H + 16.5, 0))
    fore_l = m.bone("forearm_left", arm_l, pivot=(-7.3, H + 7.5, 0))
    leg_r = m.bone("leg_right", body, pivot=(2.8, H, 0))
    shin_r = m.bone("shin_right", leg_r, pivot=(2.8, H / 2, 0))
    leg_l = m.bone("leg_left", body, pivot=(-2.8, H, 0))
    shin_l = m.bone("shin_left", leg_l, pivot=(-2.8, H / 2, 0))
    loin_f = m.bone("loin_front", body, pivot=(0, H + 1, -2.8))
    loin_b = m.bone("loin_back", body, pivot=(0, H + 1, 2.8))

    # --- legs: long bones, knobby knees, a leather kneepad on the right knee (ballplayer)
    thigh = bony(m.cube(leg_r, "thigh_right", (1.2, H / 2, -1.7), (4.4, H + 0.5, 1.7)), BONE, BONE_D)
    thigh.north.rect(0, 0, 1, thigh.north.h, BONE_D)
    knee = m.cube(shin_r, "knee_right", (0.8, H / 2 - 1, -2.1), (4.8, H / 2 + 1.2, 1.9))
    knee.paint(LEATHER, jitter=0.2)
    for f in knee.sides():
        f.rect(0, 0, f.w, 1, mix(LEATHER, "#fff", 0.2)).stripe(mix(LEATHER, "#000", 0.35), every=2, axis="v")
    knee.north.rect(1, 0, 2, 2, v["sash"])
    shin = bony(m.cube(shin_r, "lower_leg_right", (1.5, 1.8, -1.2), (4.1, H / 2 - 0.8, 1.2)), BONE, BONE_D)
    shin.north.rect(0, 0, 1, shin.north.h, VOID)
    foot = m.cube(shin_r, "foot_right", (0.9, 0, -3.8), (4.5, 2, 1.4))
    foot.paint(BONE, jitter=0.2)
    foot.north.stripe(VOID, every=2, axis="v", offset=1)
    foot.up.rect(0, 0, foot.up.w, 1, LEATHER)
    thigh.mirror_x(leg_l, "thigh_left")
    knee_l = m.cube(shin_l, "knee_left", (-4.4, H / 2 - 0.8, -1.9), (-1.2, H / 2 + 1, 1.7))
    knee_l.paint(BONE_D, jitter=0.2)
    knee_l.north.rect(1, 0, 1, 1, BONE)
    shin.mirror_x(shin_l, "lower_leg_left")
    foot.mirror_x(shin_l, "foot_left")
    anklet = m.cube(shin_r, "anklet_right", (1.2, 1.4, -1.5), (4.4, 3.2, 1.5))
    anklet.paint(ACC, jitter=0.12)
    for f in anklet.sides():
        f.stripe(mix(ACC, "#000", 0.35), every=2, axis="v", offset=1).rect(0, 0, f.w, 1, ACC_L)
    anklet.mirror_x(shin_l, "anklet_left")

    # --- pelvis, ballgame yoke (yugo) and loincloth
    pelvis = bony(m.cube(body, "pelvis", (-4.2, H - 2, -2.4), (4.2, H + 3, 2.4)), BONE, BONE_D)
    pelvis.north.rect(2, 1, 4, 2, BONE_D).rect(3, 2, 2, 1, VOID)
    y_base, y_dark, y_light = v["yoke"]
    yoke = m.cube(body, "yoke", (-5.6, H + 1, -3.6), (5.6, H + 5, 3.6))
    yoke.paint(y_base, jitter=0.12, noise=0.06)
    for f in yoke.sides():
        f.rect(0, 0, f.w, 1, y_light).rect(0, f.h - 1, f.w, 1, y_dark)
        f.stripe(y_dark, every=4, axis="v", offset=2, y=1, h=2)
    yoke.north.rect(4, 1, 3, 2, GOLD).pixel(5, 1, GOLD_L)  # carved toad/face boss on the front
    yoke.up.rect(2, 2, yoke.up.w - 4, yoke.up.h - 4, VOID)
    for name, bone, z, hw, y0 in (("loin_flap_front", loin_f, -3.7, 3.4, H - 9),
                                  ("loin_flap_back", loin_b, 3.7, 3.8, H - 8)):
        pl = m.plane(bone, name, (-hw, y0, z), (hw, H + 1.5, z))
        fc = pl.front
        fc.shaded(LINEN, noise=0.05, jitter=0.1, shade=False)
        fc.stripe(mix(LINEN, "#000", 0.12), every=3, axis="v", offset=1)
        fc.rect(0, 0, fc.w, 2, v["sash"])
        if v["marks"]:
            for k in range(7):  # the seven bars of Vucub Came
                fc.rect(k, 3, 1, 3, RED if k % 2 == 0 else VOID)
        else:
            for xx in range(0, fc.w, 3):  # Hun: a row of crossed bones / death eyes
                fc.rect(xx, 3, 2, 2, VOID).pixel(xx, 3, RED)
        fc.rect(0, 7, fc.w, 1, ACC)
        fc.rect(0, fc.h - 2, fc.w, 2, mix(v["sash"], "#000", 0.25))
        fc.tatter(2, edge="bottom", chance=0.6)

    # --- torso: spine, see-through ribcage, dark void, jade collar with an owl pectoral
    spine = bony(m.cube(torso, "spine", (-1.5, H + 3, -1.3), (1.5, H + 9, 1.5)), BONE, BONE_D)
    spine.north.rect(1, 0, 1, spine.north.h, VOID)
    void = m.cube(torso, "chest_void", (-4.2, H + 8.5, -2.2), (4.2, H + 16.5, 2.2))
    void.paint(VOID, jitter=0.2, noise=0.08)
    ribs = m.cube(torso, "ribcage", (-5.2, H + 8, -3.0), (5.2, H + 17, 3.0))
    ribs.paint(BONE, noise=0.05, jitter=0.15)
    for f in (ribs.north, ribs.south):
        f.clear()
        cx = f.w / 2
        for r in range(0, f.h - 1, 2):
            w_r = int(cx * (0.72 + 0.28 * math.sin(r / f.h * math.pi)))
            f.rect(int(cx - w_r), r, 2 * w_r, 1, BONE, noise=0.06)
            f.rect(int(cx - w_r), r + 1, 2 * w_r, 1, BONE_D)
        f.rect(int(cx) - 1, 0, 2, f.h, BONE).rect(int(cx) - 1, 0, 1, f.h, mix(BONE, "#fff", 0.2))
    for f in (ribs.east, ribs.west):
        f.clear()
        for r in range(0, f.h - 1, 2):
            f.rect(0, r, f.w, 1, BONE).rect(0, r + 1, f.w, 1, BONE_D)
    ribs.up.fill(BONE, noise=0.05)
    ribs.down.fill(BONE_D)
    neck = bony(m.cube(torso, "neck", (-1.4, H + 16.5, -1.3), (1.4, H + 19.5, 1.3)), BONE, BONE_D)
    neck.north.rect(1, 0, 1, neck.north.h, VOID)
    collar = m.cube(torso, "jade_collar", (-6.6, H + 14, -3.8), (6.6, H + 18, 3.6))
    collar.paint(JADE, jitter=0.12)
    for f in collar.sides():
        f.rect(0, 0, f.w, 1, JADE_L)
        f.stripe(JADE_D, every=2, axis="v", offset=1, y=1, h=2)
        f.rect(0, 3, f.w, 1, GOLD)
    collar.up.rect(3, 1, collar.up.w - 6, collar.up.h - 2, VOID)
    beads = collar.overlay(0.35, "collar_beads")
    for f in beads.sides():
        for xx in range(0, f.w, 2):
            f.pixel(xx, f.h - 1, JADE_L if xx % 4 == 0 else GOLD)
    pect = m.plane(torso, "owl_pectoral", (-3.5, H + 7.5, -3.95), (3.5, H + 14.5, -3.95))
    pf = pect.front
    pf.ellipse(3.5, 3.5, 3.5, 3.5, GOLD).ellipse(3.5, 3.5, 2.8, 2.8, mix(GOLD, "#000", 0.3))
    pf.ellipse(3.5, 3.3, 2.3, 2.3, ACC)
    pf.rect(1, 2, 2, 2, GOLD_L).rect(4, 2, 2, 2, GOLD_L).pixel(2, 3, VOID).pixel(4, 3, VOID)
    pf.rect(3, 4, 1, 2, GOLD)
    if v["marks"]:
        for xx in range(7):
            pf.pixel(xx, 6, RED)
    pf.noise(0.05)

    # --- skull, jaw, ear flares, headband
    skull = m.cube(head, "skull", (-4, H + 19, -4.2), (4, H + 27, 3.8))
    skull.paint(BONE, noise=0.05, jitter=0.15)
    sn = skull.north
    sn.rect(0, 2, 8, 1, BONE_D)
    sn.rect(1, 3, 2, 2, VOID).rect(5, 3, 2, 2, VOID)
    sn.pixel(1, 4, v["eye"]).pixel(6, 4, v["eye"]).pixel(2, 3, mix(v["eye"], "#000", 0.5)).pixel(5, 3, mix(v["eye"], "#000", 0.5))
    sn.rect(3, 5, 2, 2, VOID).pixel(3, 6, BONE_D)
    for xx in (0, 2, 5, 7):
        sn.pixel(xx, 7, BONE_D)
    if v["marks"]:
        for k in range(7):  # seven red dots across the brow (the middle column stays bare)
            sn.pixel(k if k < 4 else k + 1, 1, RED if k % 2 == 0 else RED_D)
    else:
        sn.rect(0, 0, 8, 2, mix(BONE_D, VOID, 0.4)).line(3, 0, 4, 2, RED)  # soot mask
    sn.cracks(BONE_D, n=1, length=3)
    for f in (skull.east, skull.west):
        f.rect(0, 6, f.w, 1, BONE_D).rect(2, 3, 2, 2, VOID)
    jw = bony(m.cube(jaw, "lower_jaw", (-3, H + 16.8, -4.0), (3, H + 19.6, 1.4)), BONE, BONE_D)
    jw.up.fill(VOID).rect(0, 0, 6, 1, BONE)
    jw.north.rect(0, 0, 6, 1, BONE)
    for xx in (0, 2, 4):
        jw.north.pixel(xx, 0, VOID)
    for side, sx in (("right", 1), ("left", -1)):
        flare = m.cube(head, f"ear_flare_{side}", (4 if sx > 0 else -5.4, H + 21, -1.6), (5.4 if sx > 0 else -4, H + 24.4, 1.6))
        flare.paint(ACC, jitter=0.1)
        for f in flare.faces("east", "west"):
            f.fill(ACC, noise=0.05).ellipse(f.w / 2, f.h / 2, f.w / 2 - 0.2, f.h / 2 - 0.2, ACC_L)
            f.ellipse(f.w / 2, f.h / 2, f.w / 3, f.h / 3, mix(ACC, "#000", 0.35)).pixel(f.w // 2, f.h // 2, GOLD)
    band = skull.overlay(0.5, "headband")
    for f in band.sides():
        f.rect(0, 0, f.w, 2, v["sash"]).rect(0, 0, f.w, 1, mix(v["sash"], "#fff", 0.2))
        if v["marks"]:
            for xx in range(0, f.w, 2):
                f.pixel(xx, 1, GOLD)  # the gold discs (seven on the front)
        else:
            for xx in range(1, f.w, 3):
                f.rect(xx, 0, 1, 2, VOID)
    band.up.outline(v["sash"])

    # --- headdress: owl crest on the brow and a fan of feathers
    owl_h = 6 if variant == "hun" else 4
    owl = m.cube(crest, "owl_crest", (-2.8, H + 26, -4.8), (2.8, H + 26 + owl_h, -1.6))
    owl.paint(OWL, jitter=0.2)
    on = owl.north
    on.fill(mix(OWL, OWL_L, 0.2), noise=0.08)
    on.rect(0, 0, 1, 2, OWL_D).rect(5, 0, 1, 2, OWL_D)
    on.ellipse(1.6, 2.4, 1.6, 1.6, OWL_L).ellipse(4.0, 2.4, 1.6, 1.6, OWL_L)
    on.rect(1, 2, 1, 1, GOLD).rect(4, 2, 1, 1, GOLD).pixel(1, 2, VOID).pixel(4, 2, VOID)
    on.rect(2, 3, 2, 2, GOLD).pixel(2, 4, mix(GOLD, "#000", 0.3))
    for f in owl.faces("east", "west", "south", "up"):
        f.stripe(OWL_D, every=2, axis="h", offset=1)
    if variant == "hun":  # owl wings spread along the sides of the headdress
        for side, sx in (("right", 1), ("left", -1)):
            wing = m.plane(crest, f"owl_wing_{side}", (sx * 2.9, H + 25, -4.6), (sx * 2.9, H + 33, 1.4))
            wf = wing.front
            for yy in range(wf.h):
                n = max(1, wf.w - abs(yy - 2) * 1)
                wf.rect(0, yy, n, 1, (OWL_D, OWL, OWL_L)[yy % 3])
            wf.noise(0.05)
    fan_h, fan_w = v["fan_h"], v["fan_w"]
    fd, fm, fl = v["fan"]
    for name, rot, scale_h in (("plume_c", 0, 1.0), ("plume_r1", -22.5, 0.85), ("plume_l1", 22.5, 0.85),
                               ("plume_r2", -45, 0.7), ("plume_l2", 45, 0.7)):
        h = round(fan_h * scale_h)
        pl = m.plane(crest, name, (-fan_w / 2, H + 26, 1.2), (fan_w / 2, H + 26 + h, 1.2),
                     rotation=(0, 0, rot), pivot=(0, H + 26, 1.2))
        feather(pl.front, fd, fm, fl, tip=v["fan_tip"], eyes=variant == "hun")
        pl.front.px[:] = pl.front.px[::-1]  # base at the band, tip at the top
    for k, cx in enumerate((-3.5, 3.5)):
        pl = m.plane(crest, f"plume_back_{k}", (cx - 3.5, H + 24, 3.6), (cx + 3.5, H + 24 + fan_h * 0.6, 3.6))
        feather(pl.front, RED_D, RED, "#ffd0a0", tip=GOLD)
        pl.front.px[:] = pl.front.px[::-1]

    # --- feather cape down the back
    for k, (cx, hw, ln) in enumerate(((0, 4.5, H + 6), (-5, 3.5, H + 1), (5, 3.5, H + 1))):
        pl = m.plane(back, f"back_plume_{k}", (cx - hw, H + 16 - ln, 3.6 + k * 0.1), (cx + hw, H + 16, 3.6 + k * 0.1))
        feather(pl.front, fd, fm, fl, tip=v["fan_tip"], eyes=variant == "hun")

    # --- arms
    shoulder = bony(m.cube(arm_r, "shoulder_right", (5.6, H + 13.8, -2.3), (9.2, H + 17.4, 2.1)), BONE, BONE_D)
    shoulder.up.rect(0, 0, shoulder.up.w, 1, BONE_D)
    pad = shoulder.overlay(0.4, "shoulder_pad_right")
    for f in pad.faces("up", "east", "north", "south"):
        f.rect(0, 0, f.w, 2, ACC).rect(0, 0, f.w, 1, ACC_L)
    upper = bony(m.cube(arm_r, "upper_arm_right", (6, H + 7, -1.3), (8.6, H + 14.5, 1.3)), BONE, BONE_D)
    upper.north.rect(0, 0, 1, upper.north.h, VOID)
    elbow = m.cube(fore_r, "elbow_right", (5.7, H + 6, -1.7), (8.9, H + 8.2, 1.7))
    elbow.paint(BONE_D, jitter=0.2)
    fore = bony(m.cube(fore_r, "lower_arm_right", (6.1, H + 0.2, -1.1), (8.5, H + 6.5, 1.1)), BONE, BONE_D)
    brace = fore.overlay(0.4, "bracelet_right")
    for f in brace.sides():
        f.rect(0, 0, f.w, 2, ACC).rect(0, 0, f.w, 1, ACC_L).rect(0, 2, f.w, 1, mix(ACC, "#000", 0.35))
        f.pixel(1, 1, GOLD)
    hand = m.cube(fore_r, "hand_right", (5.8, H - 3.2, -1.7), (8.8, H + 0.4, 1.7))
    hand.paint(BONE_D, jitter=0.2)
    hand.north.stripe(BONE, every=2, axis="v", offset=0, y=0, h=3)
    shoulder.mirror_x(arm_l, "shoulder_left")
    pad.mirror_x(arm_l, "shoulder_pad_left")
    upper.mirror_x(arm_l, "upper_arm_left")
    elbow.mirror_x(fore_l, "elbow_left")
    fore.mirror_x(fore_l, "lower_arm_left")
    brace.mirror_x(fore_l, "bracelet_left")
    hand.mirror_x(fore_l, "hand_left")
    for side, bone, sx in (("right", fore_r, 1), ("left", fore_l, -1)):
        fg = m.plane(bone, f"fingers_{side}", (sx * 7.3 - 1.5, H - 7.4, -1.75), (sx * 7.3 + 1.5, H - 3.2, -1.75))
        for xx in (0, 2):
            fg.front.rect(xx, 0, 1, fg.front.h, BONE)
            fg.front.pixel(xx, fg.front.h - 1, BONE_D)
        fg.front.rect(0, 0, fg.front.w, 1, BONE_D)

    # --- animations (cosmetic: the server never waits for them)
    def arms(anim, keys_r, keys_fore=None, interp=None):
        anim.rot("arm_right", keys_r, interp=interp)
        anim.rot("arm_left", {t: (x, -y, -z) for t, (x, y, z) in keys_r.items()}, interp=interp)
        if keys_fore:
            anim.rot("forearm_right", keys_fore, interp=interp)
            anim.rot("forearm_left", {t: (x, -y, -z) for t, (x, y, z) in keys_fore.items()}, interp=interp)

    idle = m.animation("idle", 2.8, "loop")
    idle_breathe(idle, body="torso", head="head", arms=("arm_left", "arm_right"), amount=0.3, nod=2.5, arm_out=3)
    idle.rot("headdress", {0: (0, 0, 2), 1.4: (0, 0, -2), 2.8: (0, 0, 2)})
    idle.rot("back_plumes", {0: (3, 0, 0), 1.4: (-1, 0, 0), 2.8: (3, 0, 0)})
    idle.rot("jaw", {0: (0, 0, 0), 1.4: (4, 0, 0), 2.8: (0, 0, 0)})

    walk = m.animation("walk", 1.4, "loop")
    walk_biped(walk, legs=("leg_left", "leg_right"), shins=("shin_left", "shin_right"),
               arms=("arm_left", "arm_right"), forearms=("forearm_left", "forearm_right"),
               body="body", head="head", swing=20, arm_swing=14, bob=0.6, sway=3, knee=24)
    L = walk.length
    walk.rot("headdress", {0: (0, 0, 3), L / 4: (-3, 0, 0), L / 2: (0, 0, -3), 3 * L / 4: (-3, 0, 0), L: (0, 0, 3)})
    walk.rot("back_plumes", {0: (5, 0, 0), L / 2: (-1, 0, 0), L: (5, 0, 0)})
    walk.rot("loin_front", {0: (-4, 0, 0), L / 4: (5, 0, 0), L / 2: (-4, 0, 0), 3 * L / 4: (5, 0, 0), L: (-4, 0, 0)})
    walk.rot("loin_back", {0: (4, 0, 0), L / 4: (-5, 0, 0), L / 2: (4, 0, 0), 3 * L / 4: (-5, 0, 0), L: (4, 0, 0)})

    # attack: a raking two-handed claw swipe from the right
    atk = m.animation("attack", 1.1, "once")
    atk.rot("arm_right", {0: (0, 0, 0), 0.4: (-100, 50, 20), 0.6: (-80, -60, 0), 0.8: (-70, -70, 0), 1.1: (0, 0, 0)},
            interp="linear")
    atk.rot("arm_left", {0: (0, 0, 0), 0.4: (-60, 30, -30), 0.65: (-90, -40, -10), 0.85: (-70, -50, 0), 1.1: (0, 0, 0)},
            interp="linear")
    atk.rot("forearm_right", {0: (0, 0, 0), 0.4: (-40, 0, 0), 0.6: (-10, 0, 0), 1.1: (0, 0, 0)}, interp="linear")
    atk.rot("torso", {0: (0, 0, 0), 0.4: (-6, 35, 0), 0.6: (10, -35, 0), 0.8: (10, -35, 0), 1.1: (0, 0, 0)},
            interp="linear")
    atk.rot("head", {0: (0, 0, 0), 0.4: (-8, 20, 0), 0.6: (8, -25, 0), 1.1: (0, 0, 0)}, interp="linear")
    atk.rot("jaw", {0: (0, 0, 0), 0.4: (12, 0, 0), 0.6: (35, 0, 0), 1.1: (0, 0, 0)}, interp="linear")
    atk.pos("body", {0: (0, 0, 0), 0.4: (0, 0, 1), 0.6: (0, -0.8, -3), 1.1: (0, 0, 0)}, interp="linear")

    # slam: both fists up, then smash the ground in front (impact at 1.0 s)
    slam = m.animation("slam", 1.2, "once")
    arms(slam, {0: (0, 0, 0), 0.55: (-175, 0, 10), 1.0: (-70, 0, 6), 1.2: (0, 0, 0)},
         {0: (0, 0, 0), 0.55: (-20, 0, 0), 1.0: (-5, 0, 0), 1.2: (0, 0, 0)}, interp="linear")
    slam.rot("torso", {0: (0, 0, 0), 0.55: (-14, 0, 0), 1.0: (38, 0, 0), 1.2: (0, 0, 0)}, interp="linear")
    slam.rot("head", {0: (0, 0, 0), 0.55: (-16, 0, 0), 1.0: (-20, 0, 0), 1.2: (0, 0, 0)}, interp="linear")
    slam.pos("body", {0: (0, 0, 0), 0.55: (0, 1.5, 0), 1.0: (0, -3, -1), 1.2: (0, 0, 0)}, interp="linear")
    slam.rot("leg_right", {0: (0, 0, 0), 1.0: (-30, 0, 0), 1.2: (0, 0, 0)}, interp="linear")
    slam.rot("shin_right", {0: (0, 0, 0), 1.0: (40, 0, 0), 1.2: (0, 0, 0)}, interp="linear")
    slam.rot("jaw", {0: (0, 0, 0), 0.9: (30, 0, 0), 1.2: (0, 0, 0)}, interp="linear")

    # dash: bent forward, claws trailing, fast stride
    dash = m.animation("dash", 0.6, "loop")
    walk_biped(dash, legs=("leg_left", "leg_right"), shins=("shin_left", "shin_right"), swing=40, knee=50)
    dash.rot("torso", {0: (32, 0, 0), 0.3: (34, 0, 0), 0.6: (32, 0, 0)})
    dash.rot("head", {0: (-30, 0, 0), 0.6: (-30, 0, 0)})
    arms(dash, {0: (50, 0, 14), 0.3: (56, 0, 16), 0.6: (50, 0, 14)})
    dash.rot("back_plumes", {0: (40, 0, 0), 0.3: (46, 0, 0), 0.6: (40, 0, 0)})
    dash.rot("headdress", {0: (20, 0, 0), 0.6: (20, 0, 0)})
    dash.pos("body", {0: (0, -1.5, 0), 0.15: (0, -0.5, 0), 0.3: (0, -1.5, 0), 0.45: (0, -0.5, 0), 0.6: (0, -1.5, 0)})

    # throw: lift the ball over the head (0 - 1.3 s), hurl it forward at 1.5 s
    throw = m.animation("throw", 1.8, "once")
    arms(throw, {0: (0, 0, 0), 0.6: (-160, 0, 18), 1.3: (-175, 0, 14), 1.5: (-60, 0, 8), 1.8: (0, 0, 0)},
         {0: (0, 0, 0), 0.6: (-12, 0, 0), 1.3: (-20, 0, 0), 1.5: (0, 0, 0), 1.8: (0, 0, 0)}, interp="linear")
    throw.rot("torso", {0: (0, 0, 0), 1.3: (-20, 0, 0), 1.5: (24, 0, 0), 1.8: (0, 0, 0)}, interp="linear")
    throw.rot("head", {0: (0, 0, 0), 1.3: (-14, 0, 0), 1.5: (10, 0, 0), 1.8: (0, 0, 0)}, interp="linear")
    throw.rot("leg_right", {0: (0, 0, 0), 1.3: (14, 0, 0), 1.5: (-24, 0, 0), 1.8: (0, 0, 0)}, interp="linear")
    throw.rot("back_plumes", {0: (0, 0, 0), 1.3: (-10, 0, 0), 1.5: (20, 0, 0), 1.8: (0, 0, 0)}, interp="linear")

    # revive: kneel on the left knee, right hand spread near the floor beside the fallen brother
    kneel = H / 2 - 1.5
    rev = m.animation("revive", 2.0, "loop")
    rev.pos("body", {0: (0, -kneel, 0), 1.0: (0, -kneel - 0.4, 0), 2.0: (0, -kneel, 0)})
    rev.rot("leg_right", {0: (-88, 0, 4), 2.0: (-88, 0, 4)})
    rev.rot("shin_right", {0: (88, 0, 0), 2.0: (88, 0, 0)})
    rev.rot("leg_left", {0: (6, 0, -4), 2.0: (6, 0, -4)})
    rev.rot("shin_left", {0: (84, 0, 0), 2.0: (84, 0, 0)})
    rev.rot("torso", {0: (38, 0, 0), 1.0: (42, 0, 0), 2.0: (38, 0, 0)})
    rev.rot("head", {0: (10, -10, 0), 1.0: (14, -10, 0), 2.0: (10, -10, 0)})
    rev.rot("arm_right", {0: (-12, 0, 20), 1.0: (-18, 0, 22), 2.0: (-12, 0, 20)})
    rev.rot("forearm_right", {0: (-8, 0, 0), 1.0: (-2, 0, 0), 2.0: (-8, 0, 0)})
    rev.rot("arm_left", {0: (-30, 0, 4), 2.0: (-30, 0, 4)})
    rev.rot("forearm_left", {0: (-60, 0, 0), 2.0: (-60, 0, 0)})
    rev.rot("jaw", {0: (0, 0, 0), 0.5: (14, 0, 0), 1.0: (2, 0, 0), 1.5: (14, 0, 0), 2.0: (0, 0, 0)})
    rev.rot("back_plumes", {0: (20, 0, 0), 2.0: (20, 0, 0)})

    # rage: arms flung wide, skull thrown back, jaw open in a scream
    rage = m.animation("rage", 2.0, "once")
    arms(rage, {0: (0, 0, 0), 0.4: (-30, 0, 100), 1.4: (-36, 0, 108), 2.0: (0, 0, 0)},
         {0: (0, 0, 0), 0.4: (-20, 0, 0), 1.4: (-26, 0, 0), 2.0: (0, 0, 0)})
    rage.rot("torso", {0: (0, 0, 0), 0.4: (-16, 0, 0), 0.7: (-14, 4, 0), 1.0: (-16, -4, 0), 1.4: (-16, 0, 0),
                       2.0: (0, 0, 0)})
    rage.rot("head", {0: (0, 0, 0), 0.4: (-36, 0, 0), 0.7: (-30, 8, 0), 1.0: (-36, -8, 0), 1.4: (-30, 0, 0),
                      2.0: (0, 0, 0)})
    rage.rot("jaw", {0: (0, 0, 0), 0.4: (42, 0, 0), 1.4: (40, 0, 0), 2.0: (0, 0, 0)})
    rage.rot("headdress", {0: (0, 0, 0), 0.4: (-10, 0, 0), 1.4: (-12, 0, 0), 2.0: (0, 0, 0)})
    rage.pos("body", {0: (0, 0, 0), 0.4: (0, 1.5, 0), 1.4: (0, 1.5, 0), 2.0: (0, 0, 0)})

    # death: crumple backwards and stay down (held: the brother may still revive him)
    death = m.animation("death", 1.6, "hold")
    death_fall(death, direction="back")
    death.rot("arm_right", {0: (0, 0, 0), 0.5: (-20, 0, 20), 1.2: (-60, 0, 50), 1.6: (-60, 0, 50)}, interp="linear")
    death.rot("arm_left", {0: (0, 0, 0), 0.5: (-30, 0, -20), 1.2: (-70, 0, -40), 1.6: (-70, 0, -40)}, interp="linear")
    death.rot("head", {0: (0, 0, 0), 0.4: (-22, 0, 0), 1.1: (12, 22, 0), 1.6: (12, 22, 0)}, interp="linear")
    death.rot("jaw", {0: (0, 0, 0), 0.6: (40, 0, 0), 1.6: (45, 0, 0)}, interp="linear")
    death.rot("headdress", {0: (0, 0, 0), 0.8: (20, 0, 10), 1.6: (30, 0, 14)}, interp="linear")

    missing = [c for c in LORD_CLIPS if c not in m.animations]
    assert not missing, f"missing {key} clips {missing}"
    m.validate(require=LORD_CLIPS, quiet=True)
    return m


def build_hun():
    return build_lord("bigcasares_myth_came", "hun")


def build_vucub():
    return build_lord("bigcasares_myth_came_vucub", "vucub")


# =====================================================================================
# THE BALL - solid rubber (ulli), ~1.5 blocks, a painted skull and red bands
# =====================================================================================
def build_ball():
    m = Model("bigcasares_myth_came_ball", texture_size=(256, 128), bone_budget=(4, 6), cube_budget=(6, 8))
    ball = m.bone("ball", pivot=(0, 12, 0))

    def rubber(cube):
        cube.paint(RUBBER, jitter=0.16, noise=0.06)
        for f in cube.faces():
            speckle(f, RUBBER_L, 0.08)
        return cube

    core = rubber(m.cube(ball, "core", (-10, 2, -10), (10, 22, 10)))
    bulge_x = rubber(m.cube(ball, "bulge_x", (-12, 4, -8), (12, 20, 8)))
    bulge_y = rubber(m.cube(ball, "bulge_y", (-8, 0, -8), (8, 24, 8)))
    bulge_z = rubber(m.cube(ball, "bulge_z", (-8, 4, -12), (8, 20, 12)))
    # a red band around the middle, gold glyph rings on the poles
    for cube in (core, bulge_x, bulge_z):
        for f in cube.sides():
            mid = f.h // 2
            f.rect(0, mid - 1, f.w, 2, RED).rect(0, mid - 1, f.w, 1, mix(RED, "#fff", 0.2))
    for cube in (bulge_y,):
        for f in (cube.up, cube.down):
            f.outline(GOLD, 3, 3, f.w - 6, f.h - 6).outline(RED_D, 5, 5, f.w - 10, f.h - 10)
    # the skull of the decapitated hero painted on the front (the Popol Vuh ball)
    sk = bulge_z.north
    sk.ellipse(8, 7, 5, 5, "#d9ceb0")
    sk.rect(5, 6, 2, 2, VOID).rect(9, 6, 2, 2, VOID).rect(7, 9, 2, 1, VOID)
    sk.rect(6, 11, 4, 1, "#8d8368").pixel(7, 11, VOID).pixel(9, 11, VOID)
    for f in (core.north, core.south, bulge_x.east, bulge_x.west):
        f.dither(mix(RUBBER, "#000", 0.3), density=0.15)

    idle = m.animation("idle", 4.0, "loop", interp="linear")
    idle.rot("ball", {0: (0, 0, 0), 2.0: (0, 180, 0), 4.0: (0, 360, 0)})
    idle.pos("ball", {0: (0, 0, 0), 1.0: (0, 0.5, 0), 2.0: (0, 0, 0), 3.0: (0, 0.5, 0), 4.0: (0, 0, 0)})
    spin = m.animation("spin", 0.5, "loop", interp="linear")
    spin.rot("ball", {0: (0, 0, 0), 0.25: (-180, 0, 0), 0.5: (-360, 0, 0)})
    death = m.animation("death", 0.4, "hold", interp="linear")
    death.scale("ball", {0: (1, 1, 1), 0.15: (1.3, 0.7, 1.3), 0.4: (0.02, 0.02, 0.02)})
    return m


# =====================================================================================
# THE HOOP - a vertical stone ring of the ballcourt wall (centre 3 blocks up)
# =====================================================================================
HOOP_CY = 48


def build_hoop():
    m = Model("bigcasares_myth_came_hoop", texture_size=(256, 256), bone_budget=(6, 8), cube_budget=(30, 34))
    base = m.bone("base", pivot=(0, 0, 0))
    ring_b = m.bone("ring", pivot=(0, HOOP_CY, 0))
    mid_b = m.bone("ring_mid", ring_b, pivot=(0, HOOP_CY, 0))
    inner_b = m.bone("ring_inner", ring_b, pivot=(0, HOOP_CY, 0))

    def stone(cube, base_c=STONE, dark=STONE_D, light=STONE_L):
        cube.paint(base_c, jitter=0.12, noise=0.07)
        for f in cube.faces():
            f.cracks(dark, n=1, length=4)
            speckle(f, mix(base_c, "#3a6a3a", 0.35), 0.05)  # moss
        for f in cube.sides():
            f.rect(0, 0, f.w, 1, light)
        return cube

    def carved(cube):
        stone(cube)
        for f in (cube.north, cube.south):
            f.stripe(STONE_D, every=6, axis="v", offset=2, y=2, h=max(1, f.h - 4))
            for xx in range(3, f.w - 2, 6):
                f.rect(xx, 3, 2, 2, RED_D)  # flaking red paint in the carved frets
        return cube

    ring(m, ring_b, "outer", 35, 10, 7, HOOP_CY, carved)
    ring(m, mid_b, "mid", 26, 9, 5, HOOP_CY,
         lambda c: stone(c, mix(STONE, JADE, 0.25), STONE_D, mix(STONE_L, JADE_L, 0.3)))
    ring(m, inner_b, "inner", 18, 8, 4, HOOP_CY,
         lambda c: stone(c, mix(STONE, JADE, 0.45), mix(STONE_D, JADE_D, 0.4), JADE_L))
    plinth = stone(m.cube(base, "plinth", (-11, 0, -5), (11, 4, 5)))
    plinth.north.stripe(STONE_D, every=4, axis="v", offset=1, y=1, h=2)
    step = stone(m.cube(base, "plinth_step", (-14, 0, -6.5), (14, 1.5, 6.5)), STONE_D, mix(STONE_D, "#000", 0.3), STONE)
    step.up.fill(STONE_D, noise=0.06)
    # two carved feathered-serpent heads at the sides of the ring, facing the court
    for side, sx in (("right", 1), ("left", -1)):
        head = m.cube(ring_b, f"serpent_{side}", (sx * 44 - 4, HOOP_CY - 4, -6), (sx * 44 + 4, HOOP_CY + 4, 4))
        stone(head, mix(STONE, JADE, 0.3))
        hn = head.north
        hn.rect(1, 1, 2, 2, VOID).rect(5, 1, 2, 2, VOID).pixel(1, 1, GOLD).pixel(6, 1, GOLD)
        hn.rect(1, 5, 6, 2, RED_D).rect(2, 5, 1, 1, LINEN).rect(5, 5, 1, 1, LINEN)
        hn.rect(0, 0, 8, 1, JADE_L)

    hidden = (0.02, 0.02, 0.02)
    shown = (1, 1, 1)
    idle = m.animation("idle", 0.2, "hold", interp="linear")
    idle.scale("ring_mid", {0: hidden, 0.2: hidden})
    idle.scale("ring_inner", {0: hidden, 0.2: hidden})
    n1 = m.animation("narrow_1", 0.8, "hold")
    n1.scale("ring_mid", {0: hidden, 0.5: (1.08, 1.08, 1.08), 0.8: shown})
    n1.scale("ring_inner", {0: hidden, 0.8: hidden})
    n2 = m.animation("narrow_2", 0.8, "hold")
    n2.scale("ring_mid", {0: shown, 0.8: shown})
    n2.scale("ring_inner", {0: hidden, 0.5: (1.08, 1.08, 1.08), 0.8: shown})
    goal = m.animation("goal", 0.8, "once")
    goal.rot("ring", {0: (0, 0, 0), 0.15: (0, 0, 4), 0.3: (0, 0, -4), 0.45: (0, 0, 2), 0.8: (0, 0, 0)})
    goal.scale("ring", {0: shown, 0.15: (1.06, 1.06, 1.06), 0.8: shown})
    death = m.animation("death", 1.0, "hold", interp="linear")
    death.rot("ring", {0: (0, 0, 0), 0.4: (0, 0, 8), 1.0: (60, 0, 12)})
    death.pos("ring", {0: (0, 0, 0), 0.4: (0, 0, 0), 1.0: (0, -40, 0)})
    death.scale("ring", {0: shown, 0.7: shown, 1.0: (0.02, 0.02, 0.02)})
    return m


MODELS = {
    "bigcasares_myth_came": build_hun,
    "bigcasares_myth_came_vucub": build_vucub,
    "bigcasares_myth_came_ball": build_ball,
    "bigcasares_myth_came_hoop": build_hoop,
}
