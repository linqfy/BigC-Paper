"""Convierte un modelo Citadel del mod (ítems con render 3D en código) a un modelo de ítem Java.

Lo usa generate_content.py para ítems como la Galena Gauntlet o el Resistor Shield.
Las rotaciones múltiplos de 90° se aplican de forma exacta; las demás (unos pocos
grados) se ignoran. El modelo se centra en el bloque de ítem y se escala para
caber en el rango permitido (-16..32).
"""
import math
import os
import sys

sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))
from citadel_to_bbmodel import parse_model, box_faces  # noqa: E402

FACE_ORDER = ["north", "east", "south", "west"]


def rotation_steps(angle):
    quarter = angle / (math.pi / 2)
    steps = round(quarter)
    return steps % 4 if abs(quarter - steps) < 0.05 else 0


def rotate_y(point, pivot, steps):
    x, y, z = point[0] - pivot[0], point[1], point[2] - pivot[2]
    for _ in range(steps):
        x, z = -z, x
    return [x + pivot[0], y, z + pivot[2]]


def remap_faces(faces, steps):
    # A Y rotation moves side faces around; up/down keep their texture.
    result = dict(faces)
    for index, face in enumerate(FACE_ORDER):
        result[FACE_ORDER[(index + steps) % 4]] = faces[face]
    return result


def collect_boxes(part, inherited_steps=0, chain_pivots=()):
    """Absolute boxes in Java model space with their faces, after 90-degree Y rotations."""
    pivot = list(part.pivot)
    node = part.parent
    while node is not None:
        pivot = [pivot[i] + node.pivot[i] for i in range(3)]
        node = node.parent
    steps = (inherited_steps + rotation_steps(part.rotation[1])) % 4
    pivots = chain_pivots + ((pivot, rotation_steps(part.rotation[1])),)
    boxes = []
    for box in part.boxes:
        x, y, z = box["pos"]
        w, h, d = box["size"]
        corners = [[pivot[0] + x, pivot[1] + y, pivot[2] + z], [pivot[0] + x + w, pivot[1] + y + h, pivot[2] + z + d]]
        for chain_pivot, chain_steps in reversed(pivots):
            corners = [rotate_y(c, chain_pivot, chain_steps) for c in corners]
        low = [min(corners[0][i], corners[1][i]) for i in range(3)]
        high = [max(corners[0][i], corners[1][i]) for i in range(3)]
        faces = box_faces(box["tex"][0], box["tex"][1], w, h, d, box["mirror"])
        boxes.append((low, high, remap_faces(faces, steps), box["inflate"]))
    for child in part.children:
        boxes.extend(collect_boxes(child, steps, pivots))
    return boxes


def convert(source_path, texture_ref):
    with open(source_path, encoding="utf-8") as handle:
        _, roots, tex_w, tex_h = parse_model(handle.read())
    boxes = []
    for root in roots:
        boxes.extend(collect_boxes(root))
    # Java model space (Y down, X mirrored in Blockbench terms) -> item model space.
    converted = []
    for low, high, faces, inflate in boxes:
        frm = [-high[0], 24 - high[1], low[2]]
        to = [-low[0], 24 - low[1], high[2]]
        converted.append((frm, to, faces, inflate))
    mins = [min(c[0][i] for c in converted) for i in range(3)]
    maxs = [max(c[1][i] for c in converted) for i in range(3)]
    size = max(maxs[i] - mins[i] for i in range(3))
    scale = min(1.0, 46.0 / size) if size > 0 else 1.0
    center = [(mins[i] + maxs[i]) / 2 for i in range(3)]
    elements = []
    for frm, to, faces, inflate in converted:
        def place(value, axis):
            # Java item models only accept element coordinates in -16..32.
            return round(max(-16.0, min(32.0, 8 + (value - center[axis]) * scale)), 4)
        f = [place(frm[i] - inflate, i) for i in range(3)]
        t = [place(to[i] + inflate, i) for i in range(3)]
        elements.append({
            "from": f, "to": t,
            "faces": {name: {"uv": [round(uv[0] * 16 / tex_w, 4), round(uv[1] * 16 / tex_h, 4),
                                    round(uv[2] * 16 / tex_w, 4), round(uv[3] * 16 / tex_h, 4)],
                             "texture": "#0"}
                      for name, uv in faces.items()},
        })
    display_scale = 0.6 / scale if scale < 1 else 0.6
    return {
        "textures": {"0": texture_ref, "particle": texture_ref},
        "elements": elements,
        "display": {
            "thirdperson_righthand": {"rotation": [0, 0, 0], "translation": [0, 3, 1], "scale": [display_scale * 0.9] * 3},
            "thirdperson_lefthand": {"rotation": [0, 0, 0], "translation": [0, 3, 1], "scale": [display_scale * 0.9] * 3},
            "firstperson_righthand": {"rotation": [0, -10, 0], "translation": [1, 3, 0], "scale": [display_scale * 0.8] * 3},
            "firstperson_lefthand": {"rotation": [0, 10, 0], "translation": [1, 3, 0], "scale": [display_scale * 0.8] * 3},
            "gui": {"rotation": [30, 225, 0], "translation": [0, 0, 0], "scale": [display_scale * 0.9] * 3},
            "ground": {"rotation": [0, 0, 0], "translation": [0, 2, 0], "scale": [display_scale * 0.5] * 3},
            "fixed": {"rotation": [0, 0, 0], "translation": [0, 0, 0], "scale": [display_scale] * 3},
        },
    }


def front_icon(model, texture_bytes, size=32):
    """Orthographic front view (north faces, back to front) as a small PNG for Bedrock icons."""
    import io
    from PIL import Image

    texture = Image.open(io.BytesIO(texture_bytes)).convert("RGBA")
    tw, th = texture.size
    elements = model["elements"]
    min_x = min(e["from"][0] for e in elements)
    max_x = max(e["to"][0] for e in elements)
    min_y = min(e["from"][1] for e in elements)
    max_y = max(e["to"][1] for e in elements)
    span = max(max_x - min_x, max_y - min_y, 1e-6)
    canvas = Image.new("RGBA", (size, size), (0, 0, 0, 0))
    for element in sorted(elements, key=lambda e: -e["from"][2]):
        uv = element["faces"]["north"]["uv"]
        u0, v0, u1, v1 = (uv[0] * tw / 16, uv[1] * th / 16, uv[2] * tw / 16, uv[3] * th / 16)
        left, right = min(u0, u1), max(u0, u1)
        top, bottom = min(v0, v1), max(v0, v1)
        if right - left < 1 or bottom - top < 1:
            continue
        face = texture.crop((int(left), int(top), int(math.ceil(right)), int(math.ceil(bottom))))
        x0 = (max_x - element["to"][0]) / span * size
        x1 = (max_x - element["from"][0]) / span * size
        y0 = (max_y - element["to"][1]) / span * size
        y1 = (max_y - element["from"][1]) / span * size
        width, height = max(1, int(round(x1 - x0))), max(1, int(round(y1 - y0)))
        canvas.alpha_composite(face.resize((width, height), Image.NEAREST), (int(round(x0)), int(round(y0))))
    out = io.BytesIO()
    canvas.save(out, "PNG")
    return out.getvalue()
