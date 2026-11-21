"""Medieval-European creatures for the mythic dungeons: treasure dragon, kobold, goblin, ghost.

Same approach as greek.py: a modest set of cuboids, detail painted into the texture, relief
from overlay layers and alpha planes.  The model faces north (-Z); the creature's right is +X.
"""
import sys
from pathlib import Path

import numpy as np

sys.path.insert(0, str(Path(__file__).resolve().parents[1]))
from bbkit import (Model, attack_swing, death_fall, idle_breathe, mix, ramp,  # noqa: E402
                   walk_biped, walk_quadruped)

GOLD = "#e8b838"
GOLD_D = "#a8741e"
GOLD_L = "#fff0a0"


def coin(face, x, y, base=GOLD):
    """2x2 coin sprite with a highlight and a shaded rim."""
    face.rect(x, y, 2, 2, base).pixel(x, y, GOLD_L).pixel(x + 1, y + 1, GOLD_D)


def membrane(face, depth, fingers, color, vein, edge=None):
    """Wing membrane on a plane face: ``depth(col)`` rows of membrane hang from the top row
    (leading edge), finger bones fan out from the wrist (0, 0) to ``fingers`` columns."""
    for x in range(face.w):
        d = max(1, min(face.h, int(round(depth(x)))))
        face.rect(x, 0, 1, d, color)
    face.noise(0.06)
    face.rect(0, 0, face.w, 1, vein)
    for fx in fingers:
        d = max(1, min(face.h, int(round(depth(fx)))))
        face.line(0, 0, fx, d - 1, vein)
    if edge:
        for x in range(face.w):
            d = max(1, min(face.h, int(round(depth(x)))))
            face.pixel(x, d - 1, edge)
    return face


# ======================================================================================
# TREASURE DRAGON
# ======================================================================================
D_SCALE = "#a8342a"
D_SCALE_D = "#6a1c1a"
D_BELLY = "#d89a4c"
D_HORN = "#e8dcb4"
D_WING = "#8a2e2c"
D_BONE = "#5c1816"


def scaled(cube, base, rim=None, faces=("north", "south", "east", "west", "up"), size=3):
    cube.paint(base, jitter=0.2)
    rim = rim or mix(base, "#000", 0.2)
    for name in faces:
        f = cube.face(name)
        f.scales(rim, size=size, highlight=mix(base, "#fff", 0.25))
    return cube


def build_dragon():
    m = Model("bigcasares_myth_treasure_dragon", texture_size=(256, 256))

    body = m.bone("body", pivot=(0, 16, 0))
    neck1 = m.bone("neck_low", body, pivot=(0, 22, -9))
    neck2 = m.bone("neck_high", neck1, pivot=(0, 28, -14))
    head = m.bone("head", neck2, pivot=(0, 31, -17))
    jaw = m.bone("jaw", head, pivot=(0, 29.5, -23))
    flame = m.bone("flame", head, pivot=(0, 29, -31))
    leg_fr = m.bone("leg_front_right", body, pivot=(5, 13, -7))
    leg_fl = m.bone("leg_front_left", body, pivot=(-5, 13, -7))
    leg_br = m.bone("leg_back_right", body, pivot=(5.5, 14, 7.5))
    leg_bl = m.bone("leg_back_left", body, pivot=(-5.5, 14, 7.5))
    wing_r = m.bone("wing_right", body, pivot=(6, 24.6, -3.5))
    wing_r2 = m.bone("wing_right_tip", wing_r, pivot=(14, 24.6, -3.5))
    wing_l = m.bone("wing_left", body, pivot=(-6, 24.6, -3.5))
    wing_l2 = m.bone("wing_left_tip", wing_l, pivot=(-14, 24.6, -3.5))
    tail1 = m.bone("tail_1", body, pivot=(0, 17, 10))
    tail2 = m.bone("tail_2", tail1, pivot=(0, 17, 15))
    tail3 = m.bone("tail_3", tail2, pivot=(0, 17, 20))
    tail4 = m.bone("tail_4", tail3, pivot=(0, 16.5, 25))

    # --- torso: scaled back, bronze belly plates, treasure embedded in the spine
    torso = scaled(m.cube(body, "torso", (-7, 11, -10), (7, 24, 10)), D_SCALE)
    torso.down.fill(D_BELLY, noise=0.05)
    belly = m.cube(body, "belly_plates", (-5, 10.5, -9.5), (5, 13, 9.5))
    belly.paint(D_BELLY, jitter=0.15)
    for f in (belly.north, belly.south, belly.east, belly.west, belly.down):
        f.stripe(mix(D_BELLY, "#000", 0.35), every=2, axis="h" if f.name != "down" else "v", offset=1)
    chest = scaled(m.cube(body, "chest", (-7.5, 12, -14), (7.5, 25, -8)), D_SCALE)
    chest.north.rect(3, 1, 7, 10, D_BELLY).stripe(mix(D_BELLY, "#000", 0.35), every=2, axis="h", offset=1, x=3, y=1, w=7, h=10)
    chest.down.fill(D_BELLY)
    embedded = torso.overlay(0.25, "torso_embedded")
    for i, (x, y) in enumerate(((1, 2), (8, 4), (3, 7), (12, 1), (15, 6), (6, 9))):
        for f in (embedded.east, embedded.west):
            if x + 2 <= f.w and y + 2 <= f.h:
                coin(f, x, y)
    embedded.east.pixel(10, 3, "#d02840").pixel(11, 3, "#8a1428").pixel(4, 9, "#2fb870")
    embedded.west.pixel(5, 3, "#3a74d8").pixel(14, 8, "#d02840")
    hoard = m.cube(body, "hoard_saddle", (-5.5, 24, -6), (5.5, 26, 8))
    hoard.paint(GOLD, jitter=0.25, noise=0.08)
    for fc in hoard.faces():
        fc.dither(GOLD_D, density=0.3, pattern="noise")
    for i, (x, y) in enumerate(((0, 1), (3, 5), (6, 2), (1, 9), (7, 8), (4, 11), (8, 0))):
        coin(hoard.up, x, y)
    hoard.up.pixel(2, 3, "#d02840").pixel(3, 3, "#ff7080").pixel(8, 5, "#2fb870").pixel(6, 12, "#3a74d8")
    for f in hoard.sides():
        f.dither(GOLD_L, density=0.12, pattern="noise")
    gem_defs = (("gem_ruby", (-3, 26, -3), "#d02840", 0), ("gem_emerald", (1.5, 26, 1), "#2fb870", 22.5),
                ("gem_sapphire", (-2.5, 26, 5), "#3a74d8", 0))
    for n, (x, y, z), col, rot in gem_defs:
        g = m.cube(body, n, (x, y, z), (x + 2, y + 3.5, z + 2), rotation=(0, rot, 0) if rot else None)
        g.paint(col, jitter=0.1, tones=5)
        g.up.pixel(0, 0, "#ffffff")
        g.north.pixel(0, 0, mix(col, "#fff", 0.6))
    # spine ridge plates
    for i, z in enumerate((-9, -4, 1, 5)):
        r = m.cube(body, f"spine_{i}", (-0.75, 24.0, z), (0.75, 27.0 if i % 2 else 26.5, z + 2))
        r.paint(D_BONE, jitter=0.15)

    # --- neck and head
    n1 = scaled(m.cube(neck1, "neck_low_c", (-3.5, 21, -16), (3.5, 28.5, -9)), D_SCALE)
    n1.north.rect(1, 0, 5, 7, D_BELLY)
    n1.north.stripe(mix(D_BELLY, "#000", 0.35), every=2, axis="h", offset=1, x=1, w=5)
    n2 = scaled(m.cube(neck2, "neck_high_c", (-3, 27, -20), (3, 34, -14)), D_SCALE)
    n2.north.rect(1, 0, 4, 7, D_BELLY).stripe(mix(D_BELLY, "#000", 0.35), every=2, axis="h", offset=1, x=1, w=4)
    skull = scaled(m.cube(head, "skull", (-4, 30, -25), (4, 37, -17)), D_SCALE)
    skull.north.eyes(4, 2, w=2, h=1, gap=2, glow="#ffd020", socket="#2a0808", brow=D_SCALE_D)
    skull.up.rect(3, 0, 2, 8, D_SCALE_D)
    snout = scaled(m.cube(head, "snout", (-3, 29.5, -31.5), (3, 34.5, -25)), D_SCALE, size=2)
    snout.north.rect(1, 1, 1, 1, "#1a0505").rect(4, 1, 1, 1, "#1a0505").rect(0, 4, 6, 1, "#f2ead0")
    snout.down.fill("#7a1c24").stripe("#f2ead0", every=2, axis="v", x=0, y=0, w=6, h=1)
    snout.down.rect(0, 0, 1, snout.down.h, "#f2ead0").rect(5, 0, 1, snout.down.h, "#f2ead0")
    jawc = m.cube(jaw, "jaw_c", (-2.6, 27, -30.5), (2.6, 29.5, -23.5))
    jawc.paint(D_SCALE_D, jitter=0.2)
    jawc.up.fill("#7a1c24").rect(0, 0, 1, jawc.up.h, "#f2ead0").rect(jawc.up.w - 1, 0, 1, jawc.up.h, "#f2ead0")
    jawc.north.rect(0, 0, 5, 1, "#f2ead0").stripe(mix(D_SCALE_D, "#000", 0.2), every=2, axis="v", y=1, h=1)
    brow = skull.overlay(0.35, "brow_ridge")
    brow.north.rect(0, 1, 8, 1, D_SCALE_D)
    brow.north.rect(0, 0, 1, 2, D_SCALE_D).rect(7, 0, 1, 2, D_SCALE_D)
    brow.up.rect(0, 0, 8, 2, D_SCALE_D)
    horn_a = m.cube(head, "horn_base_right", (3.5, 34, -22.5), (6.5, 37, -19), rotation=(-22.5, 0, 0),
                    pivot=(4.75, 35, -21))
    horn_a.paint(D_HORN, jitter=0.15)
    horn_a.north.rect(0, 0, 1, 3, mix(D_HORN, "#000", 0.25))
    horn_b = m.cube(head, "horn_tip_right", (3.75, 35.8, -19), (5.75, 38.2, -12.5), rotation=(-22.5, 0, 0),
                    pivot=(4.75, 37, -19))
    horn_b.paint(D_HORN, jitter=0.12)
    for f in horn_b.sides():
        f.gradient(D_HORN, "#6a5a40", steps=4, axis="v")
    horn_b.north.fill("#6a5a40")
    horn_a.mirror_x(head, "horn_base_left")
    horn_b.mirror_x(head, "horn_tip_left")
    frill = m.plane(head, "head_frill", (-0.01, 36, -17), (-0.01, 40, -9))
    ff = frill.front
    for x in range(ff.w):
        h = 4 - (x // 2) % 3
        ff.rect(x, ff.h - h - (x * 0), 1, h, D_BONE)
    ff.noise(0.1)
    fin_n = m.plane(neck2, "neck_fin", (0, 33, -19), (0, 36, -13))
    fin_n.front.decal(["..a..a", ".aaaaaa", "aaaaaaa"], {"a": D_BONE}, x=0, y=0)
    fin_n.front.noise(0.1)

    # --- flame for the breath attack (scaled to ~0 in the other clips)
    fl_len = 14
    fl_up = m.plane(flame, "flame_flat", (-7, 29, -31 - fl_len), (7, 29, -31))
    fl_side = m.plane(flame, "flame_side", (0, 21, -31 - fl_len), (0, 37, -31))
    for pl, vertical in ((fl_up, True), (fl_side, False)):
        f = pl.front
        W, H = (f.w, f.h)
        for y in range(H):
            for x in range(W):
                if vertical:
                    t = (H - 1 - y) / (H - 1)          # 0 at the mouth (bottom row), 1 at the tip
                    span = abs(x - (W - 1) / 2) / ((W - 1) / 2)
                else:
                    t = x / (W - 1)                   # col 0 = mouth (south end)
                    span = abs(y - (H - 1) / 2) / ((H - 1) / 2)
                width = (0.18 + 0.82 * t ** 0.6) * (1 - max(0.0, t - 0.7) * 2.6)
                width *= 0.85 + 0.3 * f.rng.random()
                if span > width:
                    continue
                k = span / max(width, 0.01) + t * 0.5
                col = "#fff4b8" if k < 0.3 else "#ffc22e" if k < 0.62 else "#ff7418" if k < 0.95 else "#c8300f"
                f.pixel(x, y, col)
        f.noise(0.06)

    # --- legs: front pair slimmer, back pair thick haunches with clawed feet
    fleg = scaled(m.cube(leg_fr, "foreleg_right", (3.2, 3, -9.5), (7.2, 13, -5.5)), D_SCALE)
    fpaw = m.cube(leg_fr, "forepaw_right", (2.8, 0, -12), (7.6, 3.2, -5))
    fpaw.paint(D_SCALE_D, jitter=0.15)
    fpaw.north.rect(0, 1, 5, 2, D_SCALE_D).pixel(1, 2, "#e8dcb4").pixel(2, 2, "#e8dcb4").pixel(3, 2, "#e8dcb4")
    fpaw.up.fill(D_SCALE_D)
    bleg = scaled(m.cube(leg_br, "hindleg_right", (3, 4, 4), (8, 15.5, 11.5)), D_SCALE)
    bleg.north.rect(0, 0, 5, 3, mix(D_SCALE, "#fff", 0.1))
    bpaw = m.cube(leg_br, "hindpaw_right", (3, 0, 1.5), (8, 4, 11))
    bpaw.paint(D_SCALE_D, jitter=0.15)
    bpaw.north.pixel(0, 3, "#e8dcb4").pixel(2, 3, "#e8dcb4").pixel(4, 3, "#e8dcb4")
    bpaw.up.fill(D_SCALE_D)
    fleg.mirror_x(leg_fl, "foreleg_left")
    fpaw.mirror_x(leg_fl, "forepaw_left")
    bleg.mirror_x(leg_bl, "hindleg_left")
    bpaw.mirror_x(leg_bl, "hindpaw_left")

    # --- wings: bone arm cubes + membrane planes (alpha cut-outs)
    arm = m.cube(wing_r, "wing_arm_right", (6, 23.3, -5), (14, 25.9, -2.2))
    arm.paint(D_SCALE, jitter=0.2)
    arm.up.stripe(D_SCALE_D, every=3, axis="v")
    fore = m.cube(wing_r2, "wing_forearm_right", (14, 23.5, -4.8), (26, 25.7, -2.6))
    fore.paint(D_BONE, jitter=0.2)
    claw = m.cube(wing_r2, "wing_claw_right", (25.5, 23.7, -5.2), (28, 25.5, -3.4))
    claw.paint(D_HORN, jitter=0.1)
    inner = m.plane(wing_r, "wing_membrane_right", (6, 24.7, -2.2), (14, 24.7, 11))
    membrane(inner.front, lambda x: 13 - x * 0.5 + (1 if x % 3 == 0 else 0), (3, 6, 8), D_WING, D_BONE, edge=mix(D_WING, "#000", 0.3))
    outer = m.plane(wing_r2, "wing_membrane_tip_right", (14, 24.7, -2.6), (27, 24.7, 9.5))
    membrane(outer.front, lambda x: 12 - x * 0.7 + (2 if x % 4 < 2 else 0), (4, 7, 10, 12), D_WING, D_BONE,
             edge=mix(D_WING, "#000", 0.3))
    arm.mirror_x(wing_l, "wing_arm_left")
    fore.mirror_x(wing_l2, "wing_forearm_left")
    claw.mirror_x(wing_l2, "wing_claw_left")
    inner.mirror_x(wing_l, "wing_membrane_left")
    outer.mirror_x(wing_l2, "wing_membrane_tip_left")

    # --- tail chain with a spade tip
    t1 = scaled(m.cube(tail1, "tail_1_c", (-3, 13.5, 9.5), (3, 20, 15.5)), D_SCALE)
    t2 = scaled(m.cube(tail2, "tail_2_c", (-2.2, 14, 15), (2.2, 19.5, 20.5)), D_SCALE)
    t3 = scaled(m.cube(tail3, "tail_3_c", (-1.5, 14.5, 20), (1.5, 18.5, 25.5)), D_SCALE, size=2)
    t4 = scaled(m.cube(tail4, "tail_4_c", (-1, 15, 25), (1, 18, 28.5)), D_SCALE_D, size=2)
    for i, (b, z0, z1, top) in enumerate(((tail1, 10.5, 14, 22.5), (tail2, 16, 19, 21), (tail3, 21, 24, 19.8))):
        sp = m.cube(b, f"tail_spike_{i}", (-0.6, top - 3 + 0.0, z0 + 0.5), (0.6, top, z0 + 2.5))
        sp.paint(D_BONE, jitter=0.15)
    spade = m.plane(tail4, "tail_spade", (0, 12, 28), (0, 22, 35))
    sf = spade.front
    for x in range(sf.w):
        for y in range(sf.h):
            cx = (y - (sf.h - 1) / 2) / ((sf.h - 1) / 2)
            if x < sf.w * 0.35:
                ok = abs(cx) < 0.25 + 0.75 * x / (sf.w * 0.35)
            else:
                ok = abs(cx) < 1 - ((x - sf.w * 0.35) / (sf.w * 0.65)) * 1.0
            if ok:
                sf.pixel(x, y, D_BONE if abs(cx) < 0.12 else D_SCALE_D)
    sf.noise(0.1)

    # --- animations
    idle = m.animation("idle", 3.0, "loop")
    idle_breathe(idle, body="body", head="head", tail="tail_1", amount=0.4, nod=3)
    L = idle.length
    idle.rot("neck_low", {0: (0, 0, 0), L / 2: (2, 3, 0), L: (0, 0, 0)})
    idle.rot("jaw", {0: (2, 0, 0), L * 0.5: (6, 0, 0), L: (2, 0, 0)})
    idle.rot("wing_right", {0: (0, 0, 14), L / 2: (0, 0, 20), L: (0, 0, 14)})
    idle.rot("wing_left", {0: (0, 0, -14), L / 2: (0, 0, -20), L: (0, 0, -14)})
    idle.rot("wing_right_tip", {0: (0, 0, -18), L / 2: (0, 0, -24), L: (0, 0, -18)})
    idle.rot("wing_left_tip", {0: (0, 0, 18), L / 2: (0, 0, 24), L: (0, 0, 18)})
    for t in ("tail_2", "tail_3", "tail_4"):
        idle.rot(t, {0: (0, 8, 0), L / 2: (0, -8, 0), L: (0, 8, 0)})

    walk = m.animation("walk", 1.0, "loop")
    walk_quadruped(walk, front=("leg_front_left", "leg_front_right"), back=("leg_back_left", "leg_back_right"),
                   swing=24, body="body", head="head", tail="tail_1", bob=0.5)
    L = walk.length
    walk.rot("neck_low", {0: (0, 4, 0), L / 2: (0, -4, 0), L: (0, 4, 0)})
    for t in ("tail_2", "tail_3", "tail_4"):
        walk.rot(t, {0: (0, -12, 0), L / 2: (0, 12, 0), L: (0, -12, 0)})
    walk.rot("wing_right", {0: (0, 0, 16), L / 2: (0, 0, 8), L: (0, 0, 16)})
    walk.rot("wing_left", {0: (0, 0, -16), L / 2: (0, 0, -8), L: (0, 0, -16)})
    walk.rot("wing_right_tip", {0: (0, 0, -20), L / 2: (0, 0, -12), L: (0, 0, -20)})
    walk.rot("wing_left_tip", {0: (0, 0, 20), L / 2: (0, 0, 12), L: (0, 0, 20)})
    walk.rot("jaw", {0: (3, 0, 0), L: (3, 0, 0)})

    attack = m.animation("attack", 1.6, "once")
    L = attack.length
    attack.rot("body", {0: (0, 0, 0), 0.4: (-8, 0, 0), 0.7: (6, 0, 0), 1.3: (4, 0, 0), L: (0, 0, 0)}, interp="linear")
    attack.pos("body", {0: (0, 0, 0), 0.4: (0, 0, 2), 0.7: (0, -0.5, -3), 1.3: (0, -0.5, -3), L: (0, 0, 0)}, interp="linear")
    attack.rot("neck_low", {0: (0, 0, 0), 0.4: (-14, 0, 0), 0.7: (18, 0, 0), 1.3: (18, 0, 0), L: (0, 0, 0)}, interp="linear")
    attack.rot("neck_high", {0: (0, 0, 0), 0.4: (-12, 0, 0), 0.7: (6, 0, 0), 1.3: (6, 0, 0), L: (0, 0, 0)}, interp="linear")
    attack.rot("head", {0: (0, 0, 0), 0.4: (-24, 0, 0), 0.7: (-20, 0, 0), 1.3: (-20, 0, 0), L: (0, 0, 0)}, interp="linear")
    attack.rot("jaw", {0: (2, 0, 0), 0.4: (10, 0, 0), 0.7: (42, 0, 0), 1.3: (42, 0, 0), L: (2, 0, 0)}, interp="linear")
    attack.scale("flame", {0: (0.001, 0.001, 0.001), 0.62: (0.001, 0.001, 0.001), 0.8: (1, 1, 1.4), 1.0: (1.15, 1.1, 1.7),
                           1.2: (1, 1, 1.4), 1.35: (0.5, 0.5, 0.7), 1.45: (0.001, 0.001, 0.001), L: (0.001, 0.001, 0.001)},
                 interp="linear")
    attack.rot("wing_right", {0: (0, 0, 14), 0.4: (0, 0, 60), 0.7: (0, 0, 40), L: (0, 0, 14)}, interp="linear")
    attack.rot("wing_left", {0: (0, 0, -14), 0.4: (0, 0, -60), 0.7: (0, 0, -40), L: (0, 0, -14)}, interp="linear")
    attack.rot("wing_right_tip", {0: (0, 0, -18), 0.4: (0, 0, -10), 0.7: (0, 0, -20), L: (0, 0, -18)}, interp="linear")
    attack.rot("wing_left_tip", {0: (0, 0, 18), 0.4: (0, 0, 10), 0.7: (0, 0, 20), L: (0, 0, 18)}, interp="linear")
    attack.rot("leg_front_left", {0: (0, 0, 0), 0.4: (10, 0, 0), 0.7: (-8, 0, 0), L: (0, 0, 0)}, interp="linear")
    attack.rot("leg_front_right", {0: (0, 0, 0), 0.4: (10, 0, 0), 0.7: (-8, 0, 0), L: (0, 0, 0)}, interp="linear")
    attack.rot("tail_1", {0: (0, 0, 0), 0.5: (0, 14, 0), 1.0: (0, -14, 0), L: (0, 0, 0)}, interp="linear")

    death = m.animation("death", 2.2, "once")
    L = death.length
    death_fall(death, direction="left", lift=7.5)
    death.rot("head", {0: (0, 0, 0), 0.5: (-20, 0, 0), 1.4: (10, 0, 15), L: (10, 0, 15)}, interp="linear")
    death.rot("jaw", {0: (2, 0, 0), 0.6: (30, 0, 0), L: (24, 0, 0)}, interp="linear")
    death.rot("neck_low", {0: (0, 0, 0), 1.2: (8, 0, 0), L: (8, 0, 0)}, interp="linear")
    death.rot("wing_right", {0: (0, 0, 14), 0.6: (0, 0, 55), 1.5: (0, 0, 10), L: (0, 0, 10)}, interp="linear")
    death.rot("wing_left", {0: (0, 0, -14), 0.6: (0, 0, -55), 1.5: (0, 0, -10), L: (0, 0, -10)}, interp="linear")
    death.rot("wing_right_tip", {0: (0, 0, -18), 1.5: (0, 0, -70), L: (0, 0, -70)}, interp="linear")
    death.rot("wing_left_tip", {0: (0, 0, 18), 1.5: (0, 0, 70), L: (0, 0, 70)}, interp="linear")
    for clip in (idle, walk, death):
        clip.scale("flame", {0: (0.001, 0.001, 0.001), clip.length: (0.001, 0.001, 0.001)})
    return m




# ======================================================================================
# KOBOLD
# ======================================================================================
K_SKIN = "#c4602a"
K_SKIN_D = "#8e3c1e"
K_BELLY = "#e8a868"
K_LEATHER = "#5a3a22"
K_METAL = "#8a8f96"
K_BRASS = "#c8982e"
K_WOOD = "#7a5330"


def kobold_scales(cube, base=K_SKIN, size=2, faces=("north", "south", "east", "west", "up")):
    cube.paint(base, jitter=0.2)
    for n in faces:
        cube.face(n).scales(mix(base, "#000", 0.22), size=size, highlight=mix(base, "#fff", 0.2))
    return cube


def build_kobold():
    m = Model("bigcasares_myth_kobold", texture_size=(128, 128))
    body = m.bone("body", pivot=(0, 4.5, 0))
    torso = m.bone("torso", body, pivot=(0, 5, 0), rotation=(-12, 0, 0))
    head = m.bone("head", torso, pivot=(0, 10, -1.5))
    arm_r = m.bone("arm_right", torso, pivot=(3.6, 9.5, 0))
    arm_l = m.bone("arm_left", torso, pivot=(-3.6, 9.5, 0))
    leg_r = m.bone("leg_right", body, pivot=(1.6, 4.5, 0))
    leg_l = m.bone("leg_left", body, pivot=(-1.6, 4.5, 0))
    tail = m.bone("tail", body, pivot=(0, 4.8, 2))
    tail2 = m.bone("tail_tip", tail, pivot=(0, 4.4, 6.5))
    flame = m.bone("candle_flame", head, pivot=(0, 17.7, -2.3))

    pelvis = m.cube(body, "loincloth_belt", (-2.8, 3.4, -2.2), (2.8, 5.6, 2.2))
    pelvis.paint(K_LEATHER, jitter=0.2)
    pelvis.north.rect(2, 0, 2, 2, K_BRASS).pixel(2, 0, GOLD_L)
    cloth = m.plane(body, "loin_flap", (-2, 1.2, -2.35), (2, 4.2, -2.35))
    cloth.front.gradient("#7a4a2a", "#4a2c18", steps=3).noise(0.08).rect(1, 1, 2, 1, "#a8743c").tatter(2)

    thigh = kobold_scales(m.cube(leg_r, "thigh_right", (0.4, 1.8, -1.6), (2.9, 5, 1.6)), K_SKIN_D)
    foot = m.cube(leg_r, "foot_right", (0.2, 0, -3.6), (3.1, 1.8, 1.4))
    foot.paint(K_SKIN_D, jitter=0.15)
    foot.north.rect(0, 0, 3, 2, K_SKIN_D).pixel(0, 1, "#f0e0b8").pixel(1, 1, "#f0e0b8").pixel(2, 1, "#f0e0b8")
    foot.up.fill(K_SKIN_D, noise=0.06)
    thigh.mirror_x(leg_l, "thigh_left")
    foot.mirror_x(leg_l, "foot_left")

    chest = kobold_scales(m.cube(torso, "chest", (-3, 5, -2.3), (3, 10.5, 2.3)), K_SKIN)
    chest.north.rect(1, 1, 4, 4, K_BELLY).stripe(mix(K_BELLY, "#000", 0.25), every=2, axis="h", offset=1, x=1, y=1, w=4, h=4)
    harness = chest.overlay(0.25, "harness")
    harness.north.line(0, 0, 5, 5, K_LEATHER).line(1, 0, 5, 4, K_LEATHER).rect(0, 5, 6, 1, K_LEATHER)
    harness.south.line(5, 0, 0, 5, K_LEATHER).line(4, 0, 0, 4, K_LEATHER).rect(0, 5, 6, 1, K_LEATHER)
    harness.east.rect(0, 5, 5, 1, K_LEATHER)
    harness.west.rect(0, 5, 5, 1, K_LEATHER)
    harness.north.pixel(3, 3, K_BRASS)

    upper = kobold_scales(m.cube(arm_r, "arm_right_c", (3, 4.6, -1.1), (5, 10, 1.1)), K_SKIN)
    hand = m.cube(arm_r, "hand_right", (2.9, 3, -1.4), (5.1, 4.8, 1.4))
    hand.paint(K_SKIN_D, jitter=0.15)
    hand.north.pixel(0, 1, "#f0e0b8").pixel(1, 1, "#f0e0b8").pixel(2, 1, "#f0e0b8")
    upper.mirror_x(arm_l, "arm_left_c")
    hand.mirror_x(arm_l, "hand_left")

    handle = m.cube(arm_r, "pick_handle", (3.9, 0.8, -0.4), (4.7, 8.6, 0.4))
    handle.paint(K_WOOD, jitter=0.2)
    handle.north.stripe(mix(K_WOOD, "#000", 0.3), every=3, axis="h", offset=1)
    pickhead = m.cube(arm_r, "pick_head", (3.5, 7.7, -4.2), (5.1, 9.2, 4.2))
    pickhead.paint(K_METAL, jitter=0.12)
    pickhead.up.stripe(mix(K_METAL, "#000", 0.3), every=2, axis="v", x=0, w=3).stripe(mix(K_METAL, "#000", 0.3), every=2, axis="v", x=pickhead.up.h - 3, w=3)
    pickhead.east.rect(0, 0, 1, 2, "#e8f0f4")

    t1 = kobold_scales(m.cube(tail, "tail_c", (-1, 3.6, 2), (1, 5.8, 6.5)), K_SKIN, faces=("north", "south", "east", "west", "up"))
    t2 = kobold_scales(m.cube(tail2, "tail_tip_c", (-0.6, 3.8, 6.5), (0.6, 5.2, 10.5)), K_SKIN_D)

    skull = kobold_scales(m.cube(head, "skull", (-3.1, 9.8, -3.6), (3.1, 14.6, 1.6)), K_SKIN, size=2)
    skull.north.eyes(3, 1, w=2, h=2, gap=2, white="#f0d84a", pupil="#120a06", look="center")
    skull.north.rect(0, 0, 6, 1, K_SKIN_D)
    snout = m.cube(head, "snout", (-1.7, 9.8, -6.6), (1.7, 12.6, -3.6))
    snout.paint(mix(K_SKIN, "#fff", 0.1), jitter=0.15)
    snout.north.rect(0, 0, 1, 1, "#1a0c08").rect(2, 0, 1, 1, "#1a0c08").rect(0, 2, 3, 1, "#f0e0b8").pixel(1, 2, K_SKIN_D)
    snout.down.fill(K_BELLY)
    ear = m.cube(head, "ear_right", (3.1, 10.8, -0.5), (5.9, 13.4, 0.5), rotation=(0, 0, -22.5), pivot=(3.1, 11, 0))
    ear.paint(K_SKIN, jitter=0.15)
    ear.north.rect(0, 1, 2, 1, "#e08a7a")
    ear.mirror_x(head, "ear_left")
    helm = m.cube(head, "helmet", (-3.5, 13.6, -4.0), (3.5, 15.9, 2.0))
    helm.paint(K_BRASS, jitter=0.15, tones=5)
    helm.north.rect(0, 1, 7, 1, mix(K_BRASS, "#000", 0.3))
    helm.up.rivets(mix(K_BRASS, "#fff", 0.4), every=3, inset=1)
    brim = m.cube(head, "helmet_brim", (-3.8, 13.4, -4.8), (3.8, 14.1, 2.3))
    brim.paint(mix(K_BRASS, "#000", 0.25), jitter=0.15)
    lamp = m.cube(head, "helmet_lamp", (-1, 13.8, -5.4), (1, 15.4, -4.1))
    lamp.paint(K_METAL, jitter=0.1)
    lamp.north.rect(0, 0, 2, 2, "#fff0a0").pixel(1, 1, "#ffd24a")
    candle = m.cube(head, "candle", (-0.55, 15.9, -2.9), (0.55, 17.7, -1.7))
    candle.paint("#efe6c8", jitter=0.1)
    candle.up.fill("#3a2a18")
    candle.east.pixel(0, 0, "#c8b890").pixel(0, 1, "#c8b890")
    for n, plane in (("flame_a", m.plane(flame, "flame_a", (-1.2, 17.7, -2.3), (1.2, 21, -2.3))),
                     ("flame_b", m.plane(flame, "flame_b", (0, 17.7, -3.5), (0, 21, -1.1)))):
        f = plane.front
        for y in range(f.h):
            t = y / (f.h - 1)  # 0 top
            half = [0.2, 0.7, 1.0, 0.9, 0.6][min(4, int(t * 5))]
            for x in range(f.w):
                d = abs(x - (f.w - 1) / 2) / ((f.w) / 2)
                if d <= half:
                    f.pixel(x, y, "#fff4b0" if t > 0.55 and d < 0.4 else "#ffb02e" if t > 0.3 else "#ff6a1a")

    idle = m.animation("idle", 1.8, "loop")
    idle_breathe(idle, body="body", head="head", arms=("arm_left", "arm_right"), tail="tail", amount=0.25, nod=4, arm_out=5)
    L = idle.length
    idle.rot("tail_tip", {0: (0, 10, 0), L / 2: (0, -10, 0), L: (0, 10, 0)})
    idle.rot("torso", {0: (0, 0, 0), L / 2: (-3, 0, 0), L: (0, 0, 0)})
    idle.scale("candle_flame", {0: (1, 1, 1), L * 0.25: (0.9, 1.15, 0.9), L * 0.5: (1.05, 0.9, 1.05), L * 0.75: (0.9, 1.1, 0.9), L: (1, 1, 1)})

    walk = m.animation("walk", 0.5, "loop")
    walk_biped(walk, legs=("leg_left", "leg_right"), arms=("arm_left", "arm_right"), body="body", head="head",
               swing=42, arm_swing=26, bob=0.7, sway=6)
    L = walk.length
    walk.rot("tail", {0: (0, -18, 0), L / 2: (0, 18, 0), L: (0, -18, 0)})
    walk.rot("tail_tip", {0: (0, 20, 0), L / 2: (0, -20, 0), L: (0, 20, 0)})
    walk.rot("torso", {0: (-4, 0, 0), L / 2: (-4, 0, 0), L: (-4, 0, 0)})
    walk.scale("candle_flame", {0: (1, 1.1, 1), L / 2: (0.9, 0.9, 0.9), L: (1, 1.1, 1)})

    attack = m.animation("attack", 0.7, "once")
    attack.rot("arm_right", {0: (0, 0, 0), 0.2: (-40, 0, 12), 0.34: (-105, 0, -4), 0.48: (-100, 0, -4), 0.7: (0, 0, 0)}, interp="linear")
    attack.rot("arm_left", {0: (0, 0, 0), 0.2: (15, 0, 0), 0.34: (-30, 0, 0), 0.7: (0, 0, 0)}, interp="linear")
    attack.rot("torso", {0: (0, 0, 0), 0.2: (10, 0, 0), 0.34: (-20, 0, 0), 0.48: (-16, 0, 0), 0.7: (0, 0, 0)}, interp="linear")
    attack.pos("body", {0: (0, 0, 0), 0.2: (0, 0, 1), 0.34: (0, -0.4, -3.5), 0.48: (0, -0.4, -3), 0.7: (0, 0, 0)}, interp="linear")
    attack.rot("head", {0: (0, 0, 0), 0.2: (-8, 0, 0), 0.34: (10, 0, 0), 0.7: (0, 0, 0)}, interp="linear")
    attack.rot("leg_right", {0: (0, 0, 0), 0.34: (-30, 0, 0), 0.7: (0, 0, 0)}, interp="linear")
    attack.rot("leg_left", {0: (0, 0, 0), 0.34: (22, 0, 0), 0.7: (0, 0, 0)}, interp="linear")

    death = m.animation("death", 1.4, "once")
    death_fall(death, direction="back", lift=3.4)
    death.rot("tail", {0: (0, 0, 0), 0.6: (0, 0, 0), 1.0: (85, 0, 0), 1.4: (85, 0, 0)}, interp="linear")
    death.rot("tail_tip", {0: (0, 0, 0), 1.0: (25, 0, 0), 1.4: (25, 0, 0)}, interp="linear")
    death.rot("arm_right", {0: (0, 0, 0), 0.4: (-40, 0, 20), 1.0: (-120, 0, 30), 1.4: (-120, 0, 30)}, interp="linear")
    death.rot("arm_left", {0: (0, 0, 0), 0.4: (-40, 0, -20), 1.0: (-110, 0, -30), 1.4: (-110, 0, -30)}, interp="linear")
    death.rot("head", {0: (0, 0, 0), 0.5: (-20, 0, 0), 1.2: (8, 20, 0), 1.4: (8, 20, 0)}, interp="linear")
    return m


# ======================================================================================
# GOBLIN
# ======================================================================================
G_SKIN = "#6aa83a"
G_SKIN_D = "#3f7424"
G_LEATHER = "#5e3d24"
G_LEATHER_D = "#3c2616"
G_CLOTH = "#6a5a3a"
G_BURLAP = "#a88a56"
G_STEEL = "#b8c0c8"


def patches(face, cols, rects):
    for (x, y, w, h), c in zip(rects, cols):
        face.rect(x, y, w, h, c).outline(mix(c, "#000", 0.45), x, y, w, h)
        face.pixel(x + 1, y + 1, mix(c, "#000", 0.3)) if w > 2 and h > 2 else None


def build_goblin():
    m = Model("bigcasares_myth_goblin", texture_size=(128, 128))
    body = m.bone("body", pivot=(0, 6, 0))
    torso = m.bone("torso", body, pivot=(0, 6.5, 0), rotation=(-14, 0, 0))
    head = m.bone("head", torso, pivot=(0, 12, -1))
    arm_r = m.bone("arm_right", torso, pivot=(4.4, 12, 0))
    arm_l = m.bone("arm_left", torso, pivot=(-4.4, 12, 0))
    leg_r = m.bone("leg_right", body, pivot=(1.8, 6, 0))
    leg_l = m.bone("leg_left", body, pivot=(-1.8, 6, 0))
    sack = m.bone("sack", torso, pivot=(0, 11, 3))

    belt = m.cube(body, "belt", (-3.4, 5.4, -2.2), (3.4, 7.2, 2.2))
    belt.paint(G_LEATHER_D, jitter=0.15)
    belt.north.rect(2, 0, 3, 2, "#b09040").pixel(3, 1, "#403010")
    pouch = m.cube(body, "pouch", (-5.2, 4.2, -1.2), (-3.4, 6.8, 1))
    pouch.paint(G_LEATHER, jitter=0.2)
    pouch.north.rect(0, 0, 2, 1, G_LEATHER_D)
    pouch.north.pixel(1, 2, "#b09040")

    thigh = m.cube(leg_r, "trouser_right", (0.5, 2.4, -1.6), (3.2, 6.2, 1.6))
    thigh.paint(G_CLOTH, jitter=0.2)
    patches(thigh.north, ["#8a4a3a"], [(0, 1, 2, 2)])
    patches(thigh.east, ["#4a6a8a"], [(1, 0, 2, 2)])
    boot = m.cube(leg_r, "boot_right", (0.3, 0, -2.8), (3.4, 2.6, 1.7))
    boot.paint(G_LEATHER, jitter=0.2)
    boot.north.rect(0, 0, 4, 1, G_LEATHER_D).stripe(G_LEATHER_D, every=2, axis="h", offset=1, y=1)
    boot.up.fill(G_LEATHER_D)
    thigh.mirror_x(leg_l, "trouser_left")
    boot.mirror_x(leg_l, "boot_left")

    chest = m.cube(torso, "chest", (-3.5, 6.5, -2.2), (3.5, 12.5, 2.2))
    chest.paint(G_SKIN, jitter=0.15)
    vest = chest.overlay(0.35, "vest")
    for f in vest.faces("north"):
        f.rect(0, 0, 2, 6, G_LEATHER).rect(5, 0, 2, 6, G_LEATHER).rect(0, 4, 7, 2, G_LEATHER).noise(0.08)
        f.line(2, 0, 2, 3, G_LEATHER_D).line(4, 0, 4, 3, G_LEATHER_D)
        patches(f, ["#7a3a30"], [(0, 1, 2, 2)])
        f.pixel(3, 3, "#b09040").pixel(3, 4, "#b09040")
    for f in vest.faces("east", "west"):
        f.rect(0, 0, f.w, 6, G_LEATHER).noise(0.08).stripe(G_LEATHER_D, every=3, axis="h", offset=2)
    vest.south.rect(0, 0, 7, 6, G_LEATHER).noise(0.08)
    patches(vest.south, ["#5a7a4a", "#8a6a2a"], [(1, 1, 3, 3), (4, 3, 2, 2)])
    vest.up.rect(0, 0, 7, 1, G_LEATHER)
    arm_c = m.cube(arm_r, "arm_right_c", (3.5, 7.4, -1.2), (5.7, 12, 1.2))
    arm_c.paint(G_SKIN, jitter=0.15)
    arm_c.north.rect(0, 0, 2, 2, G_LEATHER)
    arm_c.east.rect(0, 0, 2, 2, G_LEATHER).rect(0, 2, 2, 1, G_LEATHER_D)
    arm_c.west.rect(0, 0, 2, 2, G_LEATHER).rect(0, 2, 2, 1, G_LEATHER_D)
    arm_c.south.rect(0, 0, 2, 2, G_LEATHER)
    wrap = arm_c.overlay(0.3, "arm_wrap_right")
    for f in wrap.sides():
        f.stripe(G_CLOTH, every=2, axis="h", offset=0, y=4, h=3)
    hand = m.cube(arm_r, "hand_right", (3.5, 5.8, -1.4), (5.7, 7.6, 1.4))
    hand.paint(G_SKIN_D, jitter=0.15)
    hand.north.pixel(0, 1, "#e8e0b0").pixel(1, 1, "#e8e0b0").pixel(2, 1, "#e8e0b0")
    arm_c.mirror_x(arm_l, "arm_left_c")
    wrap.mirror_x(arm_l, "arm_wrap_left")
    hand.mirror_x(arm_l, "hand_left")

    # crooked dagger
    grip = m.cube(arm_r, "dagger_grip", (4.1, 6.2, -2.4), (5.1, 7.2, 0.4))
    grip.paint(G_LEATHER_D, jitter=0.15)
    grip.up.stripe("#b09040", every=2, axis="v")
    guard = m.cube(arm_r, "dagger_guard", (3.6, 5.8, -3), (5.6, 7.6, -2.4))
    guard.paint("#8a7030", jitter=0.12)
    blade1 = m.plane(arm_r, "dagger_blade_1", (4.6, 6, -7), (4.6, 7.6, -3))
    blade2 = m.plane(arm_r, "dagger_blade_2", (4.6, 6.1, -11), (4.6, 7.5, -7), rotation=(22.5, 0, 0), pivot=(4.6, 6.8, -7))
    for bl, tip in ((blade1, False), (blade2, True)):
        f = bl.front
        for x in range(f.w):
            # east face: x grows toward -Z (north), so the tip is at the right
            if tip and x > f.w - 3:
                span = max(0, f.h - 1 - (x - (f.w - 3)))
            else:
                span = f.h
            for y in range(f.h):
                if y < span if not tip or x <= f.w - 3 else y == f.h // 2 - 0:
                    f.pixel(x, y, G_STEEL)
        f.rect(0, 0, f.w, 1, "#eef4f8").rect(0, f.h - 1, f.w, 1, "#6a727a").noise(0.05)
        if not tip:
            f.pixel(2, 1, "#6a727a").pixel(3, 2, "#6a727a")

    skull = m.cube(head, "skull", (-3.6, 12, -3.4), (3.6, 18, 2.4))
    skull.paint(G_SKIN, jitter=0.18)
    skull.north.eyes(3.6, 2, w=2, h=2, gap=1.5, white="#f2e060", pupil="#b01818", look="center", brow=G_SKIN_D)
    skull.north.rect(1, 5, 5, 1, "#2a1410").pixel(2, 5, "#f0e8c0").pixel(5, 5, "#f0e8c0")
    skull.north.pixel(0, 3, G_SKIN_D).pixel(6, 4, G_SKIN_D).pixel(5, 0, G_SKIN_D)
    nose = m.cube(head, "nose", (-1, 13, -5.8), (1, 15.8, -3.4))
    nose.paint(mix(G_SKIN, "#c8a050", 0.15), jitter=0.2)
    nose.north.pixel(0, 2, "#1c3010").pixel(1, 2, "#1c3010").pixel(1, 0, "#d8c070")
    nose.down.fill(G_SKIN_D)
    cap = skull.overlay(0.4, "ragged_cap")
    cap.up.shaded(G_LEATHER, jitter=0.2, shade=False)
    cap.up.rect(2, 3, 3, 2, "#7a3a30").outline(G_LEATHER_D, 2, 3, 3, 2)
    for f in cap.sides():
        f.jagged(G_LEATHER, edge="top", depth=(1, 2)).noise(0.1)
    cap.north.jagged(G_LEATHER, edge="top", depth=(1, 1))
    ear = m.plane(head, "ear_right", (3.6, 13.2, -0.2), (10, 18.6, -0.2), rotation=(0, -22.5, 0), pivot=(3.6, 15, -0.2))
    ef = ear.front
    for y in range(ef.h):
        for x in range(ef.w):
            t = x / (ef.w - 1)
            mid = ef.h * (0.62 - 0.45 * t)
            half = (1 - t) * ef.h * 0.42 + 0.5
            if abs(y - mid) <= half:
                ef.pixel(x, y, G_SKIN)
    ef.noise(0.08)
    for x in range(1, ef.w - 2):
        ef.pixel(x, int(ef.h * (0.62 - 0.45 * x / (ef.w - 1))), "#d88a8a")
    ear.mirror_x(head, "ear_left")
    tusk_l = m.cube(head, "tusk_right", (1.6, 11.6, -3.6), (2.4, 13.2, -2.8))
    tusk_l.paint("#f0e8c0", jitter=0.1)
    tusk_l.mirror_x(head, "tusk_left")

    bag = m.cube(sack, "sack_c", (-3.2, 5, 2.2), (3.2, 12, 6.4))
    bag.paint(G_BURLAP, jitter=0.2, noise=0.08)
    for f in bag.faces():
        f.stripe(mix(G_BURLAP, "#000", 0.15), every=2, axis="h" if f.name != "down" else "v", offset=1)
    patches(bag.south, ["#6a7a4a", "#8a4a3a"], [(1, 1, 3, 3), (3, 4, 3, 3)])
    bag.south.pixel(4, 2, "#f0e0a0")
    bag_top = m.cube(sack, "sack_top", (-1.8, 12, 3), (1.8, 14, 5.6))
    bag_top.paint(G_BURLAP, jitter=0.2)
    bag_top.north.rect(0, 1, 4, 1, G_LEATHER_D)
    lumps = bag.overlay(0.35, "sack_lumps")
    lumps.south.rect(0, 5, 6, 1, G_BURLAP).rect(2, 2, 2, 3, mix(G_BURLAP, "#fff", 0.1))
    lumps.east.rect(1, 1, 3, 2, G_BURLAP)
    lumps.west.rect(1, 3, 3, 2, G_BURLAP)
    lumps.up.rect(1, 1, 4, 3, G_BURLAP)

    idle = m.animation("idle", 1.6, "loop")
    idle_breathe(idle, body="body", head="head", arms=("arm_left", "arm_right"), amount=0.25, nod=4, arm_out=4)
    L = idle.length
    idle.rot("sack", {0: (0, 0, 0), L / 2: (3, 0, 0), L: (0, 0, 0)})
    idle.rot("head", {0: (0, -8, 0), L * 0.3: (-3, 8, 4), L * 0.6: (2, 14, -3), L: (0, -8, 0)})

    walk = m.animation("walk", 0.7, "loop")
    walk_biped(walk, legs=("leg_left", "leg_right"), arms=("arm_left", "arm_right"), body="body", head="head",
               swing=34, arm_swing=22, bob=0.7, sway=9)
    L = walk.length
    walk.rot("torso", {0: (-6, 0, 3), L / 2: (-6, 0, -3), L: (-6, 0, 3)})
    walk.rot("sack", {0: (-4, 0, 0), L / 4: (4, 0, 0), L / 2: (-4, 0, 0), 3 * L / 4: (4, 0, 0), L: (-4, 0, 0)})

    attack = m.animation("attack", 0.9, "once")
    attack.rot("arm_right", {0: (0, 0, 0), 0.12: (-45, 0, 10), 0.22: (-100, 0, -6), 0.34: (-45, 0, 8), 0.46: (-50, 0, 10),
                             0.56: (-102, 0, -6), 0.7: (-60, 0, 0), 0.9: (0, 0, 0)}, interp="linear")
    attack.rot("arm_left", {0: (0, 0, 0), 0.2: (-30, 0, 10), 0.56: (-40, 0, 10), 0.9: (0, 0, 0)}, interp="linear")
    attack.rot("torso", {0: (0, 0, 0), 0.12: (8, 0, 0), 0.22: (-14, 0, 0), 0.34: (4, 0, 0), 0.46: (8, 0, 0), 0.56: (-16, 0, 0), 0.9: (0, 0, 0)}, interp="linear")
    attack.pos("body", {0: (0, 0, 0), 0.22: (0, -0.5, -3), 0.34: (0, 0, -1), 0.56: (0, -0.5, -3.5), 0.9: (0, 0, 0)}, interp="linear")
    attack.rot("head", {0: (0, 0, 0), 0.22: (8, 0, 0), 0.56: (10, 0, 0), 0.9: (0, 0, 0)}, interp="linear")

    death = m.animation("death", 1.5, "once")
    death_fall(death, direction="forward", lift=6.2)
    death.rot("arm_right", {0: (0, 0, 0), 0.5: (-30, 0, 20), 1.0: (-150, 0, 25), 1.5: (-150, 0, 25)}, interp="linear")
    death.rot("arm_left", {0: (0, 0, 0), 0.5: (-30, 0, -20), 1.0: (-150, 0, -25), 1.5: (-150, 0, -25)}, interp="linear")
    death.rot("sack", {0: (0, 0, 0), 1.0: (-20, 0, 0), 1.5: (-20, 0, 0)}, interp="linear")
    return m


# ======================================================================================
# GHOST (alma en pena)
# ======================================================================================
GH = "#cfe3f0"
GH_L = "#f0faff"
GH_D = "#8aaecb"
GH_DARK = "#0c1119"
GH_GLOW = "#8defff"
GH_IRON = "#59626c"


def ghostly(face, lo=170, hi=255, chance=0.45):
    """Give a share of the painted pixels partial alpha (a see-through spirit look)."""
    px = face.px
    ys, xs = np.nonzero(px[..., 3] > 0)
    for y, x in zip(ys, xs):
        if face.rng.random() < chance:
            px[y, x, 3] = face.rng.randint(lo, hi)
    return face


def fade_down(face, start=0.35, floor=70):
    """Alpha falls off toward the bottom rows (wisp tips dissolve)."""
    for y in range(face.h):
        t = y / max(1, face.h - 1)
        if t > start:
            k = 1 - (t - start) / (1 - start) * (1 - floor / 255)
            face.px[y, :, 3] = (face.px[y, :, 3] * k).astype(np.uint8)
    return face


def wisp_plane(pl, light=GH, dark=GH_D, tatter=5, seed_shift=0, floor=60):
    f = pl.front
    f.gradient(mix(light, "#fff", 0.25), dark, steps=5)
    f.noise(0.06)
    for x in range(0, f.w, 3):
        f.rect(x, 0, 1, f.h, mix(light, dark, 0.5 if (x // 3) % 2 else 0.2))
    f.tatter(tatter, chance=0.9)
    # ragged tongues: carve alternating long teeth
    for x in range(f.w):
        n = (x * 7 + seed_shift * 3) % 5
        f.clear(x, f.h - n - 1, 1, n + 1) if n > 1 else None
    ghostly(f, 140, 235, 0.5)
    fade_down(f, 0.4, floor)
    return f


def build_ghost():
    m = Model("bigcasares_myth_ghost", texture_size=(128, 128))
    body = m.bone("body", pivot=(0, 18, 0))
    torso = m.bone("torso", body, pivot=(0, 19, 0))
    head = m.bone("head", torso, pivot=(0, 28, 0))
    jaw = m.bone("jaw", head, pivot=(0, 28.5, -2.5))
    arm_r = m.bone("arm_right", torso, pivot=(6, 26.5, 0))
    fore_r = m.bone("forearm_right", arm_r, pivot=(6, 20.5, 0))
    arm_l = m.bone("arm_left", torso, pivot=(-6, 26.5, 0))
    fore_l = m.bone("forearm_left", arm_l, pivot=(-6, 20.5, 0))
    shroud_a = m.bone("shroud_front", body, pivot=(0, 18, 0))
    shroud_b = m.bone("shroud_back", body, pivot=(0, 18, 0))

    # --- torso: ragged burial shroud over a pale chest
    chest = m.cube(torso, "chest", (-4.5, 18.5, -3), (4.5, 27.5, 3))
    chest.paint(GH, jitter=0.15, tones=5)
    for f in (chest.north, chest.south):
        f.stripe(GH_D, every=3, axis="v", offset=1, y=4)
        f.rect(1, 0, 1, 3, GH_D).rect(f.w - 2, 0, 1, 3, GH_D)
    chest.north.line(2, 2, 6, 6, GH_D).line(6, 2, 2, 6, GH_D).line(3, 3, 5, 3, GH_L)
    for f in chest.faces():
        ghostly(f, 190, 245, 0.25)
    rags = chest.overlay(0.35, "shroud_rags")
    for f in rags.sides():
        f.jagged(mix(GH, "#fff", 0.2), edge="top", depth=(1, 3)).noise(0.06)
        ghostly(f, 150, 230, 0.4)
    rags.up.fill(mix(GH, "#fff", 0.2)).noise(0.06)
    ghostly(rags.up, 150, 230, 0.4)

    # --- lower body: crossing tattered planes (no legs)
    spans = (("front", (-6.8, 0.4, -3.0), (6.8, 19, -3.0), 0, shroud_a),
             ("back", (-7.2, 0.3, 3.2), (7.2, 19, 3.2), 0, shroud_b))
    for name, frm, to, rot, bone in spans:
        pl = m.plane(bone, f"wisp_{name}", frm, to)
        wisp_plane(pl, seed_shift=len(name))
    for sign, (name, bone) in zip((1, -1), (("cross_a", shroud_a), ("cross_b", shroud_b))):
        pl = m.plane(bone, f"wisp_{name}", (-8, 0.5, 0), (8, 19, 0), rotation=(0, 45 * sign, 0), pivot=(0, 10, 0))
        wisp_plane(pl, light=mix(GH, "#fff", 0.1), seed_shift=3 + len(name), floor=40)
    side_r = m.plane(shroud_a, "wisp_side_right", (5.4, 1.5, -3.5), (5.4, 18, 3.5))
    wisp_plane(side_r, seed_shift=1)
    side_r.mirror_x(shroud_b, "wisp_side_left")
    skirt = m.cube(body, "waist", (-4.2, 15.5, -2.8), (4.2, 19.5, 2.8))
    skirt.paint(GH, jitter=0.18, tones=5)
    for f in skirt.sides():
        f.stripe(GH_D, every=3, axis="v", offset=1)
        f.jagged(GH_D, edge="bottom", depth=(1, 2))
        ghostly(f, 170, 240, 0.35)
    skirt.down.clear()
    skirt.down.fill(GH_D)

    # --- head: hollow eyes, wailing mouth, hood wisps
    skull = m.cube(head, "skull", (-4, 28.5, -3.8), (4, 36, 3.8))
    skull.paint(GH_L, jitter=0.1, tones=5)
    f = skull.north
    f.ellipse(2.5, 3.3, 1.9, 2.3, GH_DARK).ellipse(5.5, 3.3, 1.9, 2.3, GH_DARK)
    f.pixel(2, 3, "#2a6a88").pixel(5, 3, "#2a6a88").pixel(2, 4, GH_GLOW).pixel(5, 4, GH_GLOW)
    f.line(3, 6, 5, 6, GH_D).pixel(4, 5, GH_D)
    f.rect(2, 6, 4, 2, GH_DARK).pixel(2, 6, GH_D).pixel(5, 6, GH_D)
    f.rect(0, 0, 8, 1, GH).darken(0.1, 0, 0, 8, 1)
    for ff in skull.faces():
        ghostly(ff, 200, 250, 0.2)
    jawc = m.cube(jaw, "jaw_c", (-2.8, 26.2, -3.4), (2.8, 28.8, 2.6))
    jawc.paint(GH, jitter=0.12)
    jawc.up.fill(GH_DARK)
    jawc.north.rect(0, 0, 6, 1, GH_L)
    hood = skull.overlay(0.6, "hood")
    for ff in hood.faces("up", "east", "west", "south"):
        ff.shaded(mix(GH, GH_D, 0.3), jitter=0.2, shade=False).noise(0.06)
    hood.north.rect(0, 0, 8, 2, mix(GH, GH_D, 0.3)).rect(0, 0, 1, 8, mix(GH, GH_D, 0.3)).rect(7, 0, 1, 8, mix(GH, GH_D, 0.3))
    hood.north.noise(0.06)
    hood.east.jagged(mix(GH, GH_D, 0.3), edge="bottom", depth=(0, 2))
    hood.west.jagged(mix(GH, GH_D, 0.3), edge="bottom", depth=(0, 2))
    for ff in hood.faces():
        ghostly(ff, 140, 235, 0.45)
    tail = m.plane(head, "hood_tail", (-5, 20, 4.5), (5, 35, 4.5))
    wisp_plane(tail, light=GH, seed_shift=2, floor=35)
    wisp_tail = m.plane(head, "hood_tail_side", (4.6, 25, -3.5), (4.6, 35, 4))
    wisp_plane(wisp_tail, light=GH_D, seed_shift=4, floor=45)
    wisp_tail.mirror_x(head, "hood_tail_side_left")

    # --- arms with chained wrists
    sleeve = m.cube(arm_r, "sleeve_right", (4.5, 21, -2), (7.8, 27.8, 2))
    sleeve.paint(GH, jitter=0.15, tones=5)
    for ff in sleeve.sides():
        ff.stripe(GH_D, every=3, axis="v", offset=0)
        ff.jagged(GH_D, edge="bottom", depth=(1, 3))
        ghostly(ff, 160, 240, 0.35)
    fore = m.cube(fore_r, "forearm_right_c", (4.9, 14.5, -1.5), (7.4, 21.3, 1.5))
    fore.paint(GH_D, jitter=0.15)
    for ff in fore.sides():
        ff.stripe(GH, every=2, axis="h", offset=1)
        ghostly(ff, 170, 240, 0.35)
    hand = m.cube(fore_r, "hand_right", (4.9, 11.5, -1.3), (7.4, 14.8, 1.3))
    hand.paint(GH_L, jitter=0.1)
    hand.north.stripe(GH_D, every=2, axis="v", offset=1, y=1, h=2)
    for ff in hand.faces():
        ghostly(ff, 170, 245, 0.3)
    cuff = m.cube(fore_r, "shackle_right", (4.6, 14.6, -1.9), (7.7, 16.4, 1.9))
    cuff.paint(GH_IRON, jitter=0.15)
    cuff.up.fill(mix(GH_IRON, "#fff", 0.2))
    cuff.north.pixel(1, 0, "#a8b4c0").pixel(0, 1, "#2a3038")
    chain = m.plane(fore_r, "chain_right", (6.9, 4, -1.0), (6.9, 14.6, 1.0))
    for y in range(chain.front.h):
        chain.front.rect(0, y, 2, 1, GH_IRON) if y % 3 != 2 else chain.front.pixel(0, y, "#a8b4c0").pixel(1, y, "#a8b4c0")
    chain2 = m.plane(fore_r, "chain_right_b", (6.2, 5, 0), (7.6, 14.6, 0))
    for y in range(chain2.front.h):
        if y % 3 == 0:
            chain2.front.rect(0, y, 1, 2, GH_IRON).pixel(1, y, "#a8b4c0")
        elif y % 3 == 1:
            chain2.front.rect(1, y, 1, 1, "#a8b4c0")
    ring = m.cube(fore_r, "chain_weight_right", (6.0, 3, -1.1), (7.8, 5.2, 0.7))
    ring.paint(GH_IRON, jitter=0.15)
    sleeve.mirror_x(arm_l, "sleeve_left")
    fore.mirror_x(fore_l, "forearm_left_c")
    hand.mirror_x(fore_l, "hand_left")
    cuff.mirror_x(fore_l, "shackle_left")
    chain.mirror_x(fore_l, "chain_left")
    chain2.mirror_x(fore_l, "chain_left_b")
    ring.mirror_x(fore_l, "chain_weight_left")

    # --- animations (the whole spirit hovers; "body" carries the float)
    idle = m.animation("idle", 3.2, "loop")
    L = idle.length
    idle.pos("body", {0: (0, 0, 0), L / 2: (0, 1.8, 0), L: (0, 0, 0)})
    idle.rot("body", {0: (1, 0, 3), L / 2: (-1, 0, -3), L: (1, 0, 3)})
    idle.rot("head", {0: (3, -6, 0), L / 2: (-2, 6, 2), L: (3, -6, 0)})
    idle.rot("jaw", {0: (10, 0, 0), L / 2: (16, 0, 0), L: (10, 0, 0)})
    idle.rot("arm_right", {0: (-10, 0, 8), L / 2: (-18, 0, 14), L: (-10, 0, 8)})
    idle.rot("arm_left", {0: (-10, 0, -8), L / 2: (-18, 0, -14), L: (-10, 0, -8)})
    idle.rot("forearm_right", {0: (-12, 0, 0), L / 2: (-22, 0, 0), L: (-12, 0, 0)})
    idle.rot("forearm_left", {0: (-12, 0, 0), L / 2: (-22, 0, 0), L: (-12, 0, 0)})
    idle.rot("shroud_front", {0: (3, 0, 4), L / 2: (-3, 0, -4), L: (3, 0, 4)})
    idle.rot("shroud_back", {0: (-3, 0, -4), L / 2: (4, 0, 4), L: (-3, 0, -4)})

    walk = m.animation("walk", 1.6, "loop")
    L = walk.length
    walk.pos("body", {0: (0, 0, 0), L / 4: (0, 1.2, 0), L / 2: (0, 0, 0), 3 * L / 4: (0, 1.2, 0), L: (0, 0, 0)})
    walk.rot("body", {0: (10, 0, 4), L / 2: (10, 0, -4), L: (10, 0, 4)})
    walk.rot("head", {0: (-4, -5, 0), L / 2: (-4, 5, 0), L: (-4, -5, 0)})
    walk.rot("jaw", {0: (14, 0, 0), L: (14, 0, 0)})
    walk.rot("arm_right", {0: (-22, 0, 10), L / 2: (-34, 0, 16), L: (-22, 0, 10)})
    walk.rot("arm_left", {0: (-34, 0, -16), L / 2: (-22, 0, -10), L: (-34, 0, -16)})
    walk.rot("forearm_right", {0: (-18, 0, 0), L / 2: (-30, 0, 0), L: (-18, 0, 0)})
    walk.rot("forearm_left", {0: (-30, 0, 0), L / 2: (-18, 0, 0), L: (-30, 0, 0)})
    walk.rot("shroud_front", {0: (-14, 0, 6), L / 2: (-20, 0, -6), L: (-14, 0, 6)})
    walk.rot("shroud_back", {0: (-26, 0, -6), L / 2: (-16, 0, 6), L: (-26, 0, -6)})

    attack = m.animation("attack", 1.2, "once")
    attack.pos("body", {0: (0, 0, 0), 0.35: (0, 1.5, 3), 0.55: (0, -0.5, -7), 0.9: (0, 0, -6), 1.2: (0, 0, 0)}, interp="linear")
    attack.rot("body", {0: (0, 0, 0), 0.35: (-12, 0, 0), 0.55: (18, 0, 0), 0.9: (14, 0, 0), 1.2: (0, 0, 0)}, interp="linear")
    attack.rot("head", {0: (0, 0, 0), 0.35: (-25, 0, 0), 0.55: (-6, 0, 0), 0.9: (-8, 0, 0), 1.2: (0, 0, 0)}, interp="linear")
    attack.rot("jaw", {0: (10, 0, 0), 0.35: (20, 0, 0), 0.55: (48, 0, 0), 0.9: (48, 0, 0), 1.2: (10, 0, 0)}, interp="linear")
    for side, sg in (("right", 1), ("left", -1)):
        attack.rot(f"arm_{side}", {0: (-10, 0, 8 * sg), 0.35: (-30, 0, 40 * sg), 0.55: (-92, 0, 8 * sg), 0.9: (-88, 0, 12 * sg), 1.2: (-10, 0, 8 * sg)}, interp="linear")
        attack.rot(f"forearm_{side}", {0: (-12, 0, 0), 0.35: (-30, 0, 0), 0.55: (-6, 0, 0), 0.9: (-6, 0, 0), 1.2: (-12, 0, 0)}, interp="linear")
    attack.rot("shroud_front", {0: (0, 0, 0), 0.55: (-30, 0, 0), 0.9: (-26, 0, 0), 1.2: (0, 0, 0)}, interp="linear")
    attack.rot("shroud_back", {0: (0, 0, 0), 0.55: (-40, 0, 0), 0.9: (-34, 0, 0), 1.2: (0, 0, 0)}, interp="linear")

    death = m.animation("death", 2.0, "once")
    death.rot("head", {0: (0, 0, 0), 0.5: (-30, 0, 0), 1.4: (-20, 0, 0), 2.0: (-20, 0, 0)}, interp="linear")
    death.rot("jaw", {0: (10, 0, 0), 0.5: (50, 0, 0), 2.0: (50, 0, 0)}, interp="linear")
    death.rot("arm_right", {0: (-10, 0, 8), 0.6: (-150, 0, 20), 2.0: (-150, 0, 25)}, interp="linear")
    death.rot("arm_left", {0: (-10, 0, -8), 0.6: (-150, 0, -20), 2.0: (-150, 0, -25)}, interp="linear")
    death.pos("root", {0: (0, 0, 0), 0.5: (0, 2, 0), 1.6: (0, -3, 0), 2.0: (0, -4, 0)}, interp="linear")
    death.scale("root", {0: (1, 1, 1), 0.5: (1.1, 1.05, 1.1), 1.4: (0.7, 0.35, 0.7), 2.0: (0.05, 0.02, 0.05)}, interp="linear")
    return m


MODELS = {
    "bigcasares_myth_treasure_dragon": build_dragon,
    "bigcasares_myth_kobold": build_kobold,
    "bigcasares_myth_goblin": build_goblin,
    "bigcasares_myth_ghost": build_ghost,
}
