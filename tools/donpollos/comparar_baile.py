"""Compara cuadros del video con la animacion retargeteada del Salsero.

Uso: python tools/comparar_baile.py <video> <inicio_video_s> <baile.json> <cuadros> <salida.png>
Genera el .bbmodel temporal en memoria a partir del modelo del Salsero + la animacion del json.
"""
import json
import sys
import tempfile
from pathlib import Path

import cv2
from PIL import Image, ImageDraw

sys.path.insert(0, str(Path(__file__).resolve().parent))
from extraer_pose import crop_dancer  # noqa: E402
from render_animacion import render  # noqa: E402

ROOT = Path(__file__).resolve().parents[2]  # tools/donpollos -> raiz de BigC-Paper
FUENTES = Path(__file__).resolve().parent / "fuentes"
MODEL = ROOT / "src" / "main" / "resources" / "bettermodel" / "models" / "bigcasares_don_pollo_salsero.bbmodel"


def model_with(anim_json):
    data = json.load(open(MODEL, encoding="utf-8"))
    dance = json.load(open(anim_json, encoding="utf-8"))
    guid = {g["name"]: g["uuid"] for g in data["groups"]}
    animators = {}
    for bone, keys in dance["frames"].items():
        animators[guid[bone]] = {"name": bone, "type": "bone", "keyframes": [
            {"channel": c, "time": t, "data_points": [{"x": v[0], "y": v[1], "z": v[2]}]} for c, t, v in keys]}
    data["animations"] = [{"name": "test", "length": dance["length"], "animators": animators}]
    tmp = Path(tempfile.gettempdir()) / "donpollo_compare.bbmodel"
    json.dump(data, open(tmp, "w", encoding="utf-8"))
    return tmp, dance["length"]


def main():
    video, start, anim_json, count, out = sys.argv[1], float(sys.argv[2]), sys.argv[3], int(sys.argv[4]), sys.argv[5]
    model, length = model_with(anim_json)
    cap = cv2.VideoCapture(video)
    S = 200
    sheet = Image.new("RGB", (S * count, S * 2 + 16), (40, 40, 40))
    draw = ImageDraw.Draw(sheet)
    for i in range(count):
        t = length * i / count
        cap.set(cv2.CAP_PROP_POS_MSEC, (start + t) * 1000)
        ok, frame = cap.read()
        if ok:
            frame = cv2.cvtColor(crop_dancer(frame), cv2.COLOR_BGR2RGB)
            sheet.paste(Image.fromarray(frame).resize((S, S)), (i * S, 16))
        sheet.paste(render(str(model), "test", t, 0, S), (i * S, 16 + S))
        draw.text((i * S + 3, 2), f"{t:.2f}s", fill=(255, 255, 0))
    sheet.save(out)
    print(out)


if __name__ == "__main__":
    main()
