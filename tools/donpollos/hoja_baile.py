"""Hoja de poses de un video de baile sobre pantalla verde.

Uso: python tools/hoja_baile.py <video> <inicio_s> <duracion_s> <fps> <salida.png>
Recorta cada cuadro al bailarin (pixeles no verdes) y los pone en una grilla con su tiempo.
"""
import subprocess
import sys
import tempfile
from pathlib import Path

from PIL import Image, ImageDraw


def is_green(px):
    r, g, b = px[:3]
    return g > 90 and g > r * 1.35 and g > b * 1.35


def bbox(img):
    small = img.resize((img.width // 4, img.height // 4))
    w, h = small.size
    pix = small.load()
    xs, ys = [], []
    for y in range(h):
        for x in range(w):
            if not is_green(pix[x, y]) and sum(pix[x, y][:3]) > 30:
                xs.append(x)
                ys.append(y)
    if not xs:
        return None
    return min(xs) * 4, min(ys) * 4, (max(xs) + 1) * 4, (max(ys) + 1) * 4


def main():
    video, start, duration, fps, out = sys.argv[1], float(sys.argv[2]), float(sys.argv[3]), float(sys.argv[4]), sys.argv[5]
    with tempfile.TemporaryDirectory() as tmp:
        subprocess.run(["ffmpeg", "-v", "error", "-ss", str(start), "-t", str(duration), "-i", video,
                        "-vf", f"fps={fps},scale=-2:540", f"{tmp}/f%04d.png"], check=True)
        frames = sorted(Path(tmp).glob("f*.png"))
        images = [Image.open(f).convert("RGB") for f in frames]
    boxes = [b for b in (bbox(i) for i in images) if b]
    x1 = min(b[0] for b in boxes); y1 = min(b[1] for b in boxes)
    x2 = max(b[2] for b in boxes); y2 = max(b[3] for b in boxes)
    pad = 10
    crop = (max(0, x1 - pad), max(0, y1 - pad), x2 + pad, y2 + pad)
    cell_h = int(__import__('os').environ.get('CELL', '200'))
    cw = int((crop[2] - crop[0]) * cell_h / (crop[3] - crop[1]))
    cols = int(__import__('os').environ.get('COLS', '10'))
    rows = (len(images) + cols - 1) // cols
    sheet = Image.new("RGB", (cols * cw, rows * (cell_h + 14)), (30, 30, 30))
    draw = ImageDraw.Draw(sheet)
    for i, img in enumerate(images):
        tile = img.crop(crop).resize((cw, cell_h))
        x, y = (i % cols) * cw, (i // cols) * (cell_h + 14)
        sheet.paste(tile, (x, y + 14))
        draw.text((x + 3, y + 1), f"{start + i / fps:.2f}s", fill=(255, 255, 0))
    sheet.save(out)
    print(out, len(images), "cuadros", crop)


if __name__ == "__main__":
    main()
