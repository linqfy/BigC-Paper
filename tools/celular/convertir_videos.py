"""Convierte los videos de tools/celular/videos/ en texturas animadas para la pantalla del celular.

Uso:  python tools/celular/convertir_videos.py   (necesita ffmpeg en el PATH y haber corrido generar_celular.py)

Los videos originales no se suben al repo (tools/celular/videos/ esta en .gitignore).

Por cada video escribe en resourcepack/java/assets/celular/:
  textures/item/video_<n>.png (+ .mcmeta)   todos los cuadros de 72x128 apilados, a 10 por segundo
  models/item/celular_video_<n>.json         el celu con el video en la pantalla
  items/video_<n>.json                       en la mano muestra el video; en el inventario, el celu quieto
y la lista de videos en src/main/resources/celular/videos.yml (la lee el modulo).

Toma todos los videos de la carpeta, ordenados por cuando se agregaron (los nuevos van al final).
"""

import json
import re
import subprocess
import sys
from pathlib import Path

import numpy as np
from PIL import Image

sys.path.insert(0, str(Path(__file__).resolve().parent))
import generar_celular  # noqa: E402

HERE = Path(__file__).resolve().parent
ROOT = HERE.parent.parent
VIDEOS = HERE / "videos"
ASSETS = ROOT / "resourcepack" / "java" / "assets" / "celular"
LISTA = ROOT / "src" / "main" / "resources" / "celular" / "videos.yml"

ANCHO, ALTO = 72, 128     # 9:16, el lado largo topeado en 128
FPS = 10                  # 1 cuadro cada 2 ticks de Minecraft
MAX_SEGUNDOS = 60         # los videos mas largos se cortan: cada cuadro ocupa memoria en el cliente
NIVELES = 32              # niveles por color: lo deja un poquito mas pixelado (y el PNG pesa menos)

EXTENSIONES = {".mp4", ".webm", ".mkv", ".mov"}

# Pantalla del celu (ver generar_celular.py): x 5..11, y 1..15, frente en z = 8.75.
PANTALLA_X = (5.0, 11.0)
PANTALLA_Y = (1.0, 15.0)
VIDEO_ALTO = (PANTALLA_X[1] - PANTALLA_X[0]) * ALTO / ANCHO
VIDEO_Y = (8.0 - VIDEO_ALTO / 2, 8.0 + VIDEO_ALTO / 2)
NEGRO = [0.3, 0.3, 0.45, 0.45]  # un pixel del borde negro del frente, en el atlas del celu

MANOS = ["firstperson_righthand", "firstperson_lefthand", "thirdperson_righthand", "thirdperson_lefthand"]


def videos():
    """Todos los videos de la carpeta, del mas viejo al mas nuevo."""
    lista = [v for v in VIDEOS.iterdir() if v.suffix.lower() in EXTENSIONES]
    if not lista:
        raise SystemExit(f"No hay videos en {VIDEOS}")
    return sorted(lista, key=lambda v: (v.stat().st_mtime, v.name))


def titulo(video):
    """El nombre del archivo sin el [id] de YouTube, los #hashtags ni los emojis (la fuente de Minecraft no los tiene)."""
    texto = re.sub(r"\[[^\]]*\]", "", video.stem)
    texto = re.sub(r"#\S+", "", texto)
    texto = "".join(c for c in texto if ord(c) < 0x2000 or c in "’¿¡")
    texto = re.sub(r"\s+", " ", texto.replace("＂", "").replace("？", "?")).strip(" -|")
    texto = texto.replace("’", "'").replace('"', "'")
    return (texto[:37].rstrip() + "...") if len(texto) > 40 else texto


def cuadros(video):
    """Todos los cuadros del video, ya achicados a 72x128, como un array (n, alto, ancho, 3)."""
    # si no es 9:16 (por ejemplo 3:4) entra entero con barras negras en vez de recortarse
    filtro = (f"fps={FPS},scale={ANCHO}:{ALTO}:force_original_aspect_ratio=decrease:flags=area,"
              f"pad={ANCHO}:{ALTO}:(ow-iw)/2:(oh-ih)/2:black")
    crudo = subprocess.run(
        ["ffmpeg", "-v", "error", "-t", str(MAX_SEGUNDOS), "-i", str(video), "-vf", filtro,
         "-f", "rawvideo", "-pix_fmt", "rgb24", "-"],
        check=True, capture_output=True).stdout
    frames = np.frombuffer(crudo, np.uint8).reshape(-1, ALTO, ANCHO, 3)
    paso = 256 // NIVELES
    return (frames // paso * paso + paso // 2).astype(np.uint8)


def modelo_video(n):
    modelo = generar_celular.modelo()
    modelo["textures"]["1"] = f"celular:item/video_{n}"
    modelo["elements"] += [
        {"name": "pantalla_negra", "from": [PANTALLA_X[0], PANTALLA_Y[0], 8.8], "to": [PANTALLA_X[1], PANTALLA_Y[1], 8.8],
         "shade": False, "light_emission": 15,
         "faces": {"south": {"uv": NEGRO, "texture": "#0"}}},
        {"name": "video", "from": [PANTALLA_X[0], round(VIDEO_Y[0], 3), 8.85],
         "to": [PANTALLA_X[1], round(VIDEO_Y[1], 3), 8.85],
         "shade": False, "light_emission": 15,
         "faces": {"south": {"uv": [0, 0, 16, 16], "texture": "#1"}}},
    ]
    return modelo


def definicion_item(n):
    # hand_animation_on_swap: al cambiar de video el celu no baja y sube en la mano
    return {"hand_animation_on_swap": False, "model": {
        "type": "minecraft:select",
        "property": "minecraft:display_context",
        "cases": [{"when": MANOS, "model": {"type": "minecraft:model", "model": f"celular:item/celular_video_{n}"}}],
        "fallback": {"type": "minecraft:model", "model": "celular:item/celular"},
    }}


def main():
    lista = ["# Generado por tools/celular/convertir_videos.py. Orden en el que se ven en el celu.", "videos:"]
    muestras = []
    todos = videos()
    # se borran los videos generados antes que ya no estan (por ejemplo si se saco uno de la carpeta)
    for viejo in list((ASSETS / "textures" / "item").glob("video_*")) + list((ASSETS / "items").glob("video_*"))             + list((ASSETS / "models" / "item").glob("celular_video_*")):
        viejo.unlink()
    for n, video in enumerate(todos, start=1):
        nombre = titulo(video)
        frames = cuadros(video)
        tira = Image.fromarray(frames.reshape(-1, ANCHO, 3), "RGB")
        tira.save(ASSETS / "textures" / "item" / f"video_{n}.png", optimize=True)
        (ASSETS / "textures" / "item" / f"video_{n}.png.mcmeta").write_text(json.dumps(
            {"animation": {"frametime": 20 // FPS, "width": ANCHO, "height": ALTO, "interpolate": False}}, indent=2),
            encoding="utf-8", newline="\n")
        (ASSETS / "models" / "item" / f"celular_video_{n}.json").write_text(
            json.dumps(modelo_video(n), indent=2), encoding="utf-8", newline="\n")
        (ASSETS / "items" / f"video_{n}.json").write_text(json.dumps(definicion_item(n), indent=2), encoding="utf-8", newline="\n")
        lista += [f"  - id: video_{n}", f"    titulo: \"{nombre}\"", f"    cuadros: {len(frames)}"]
        muestras.append(Image.fromarray(frames[len(frames) // 3]))
        peso = (ASSETS / "textures" / "item" / f"video_{n}.png").stat().st_size / 1e6
        print(f"video_{n}: {nombre:<32} {len(frames):4d} cuadros ({len(frames) / FPS:.1f}s)  {peso:.1f} MB")
    LISTA.write_text("\n".join(lista) + "\n", encoding="utf-8", newline="\n")

    # vista previa: un cuadro de cada video en la pantalla del celu
    atlas = Image.open(ASSETS / "textures" / "item" / "celular.png").convert("RGBA")
    filas = (len(muestras) + 4) // 5
    hoja = Image.new("RGBA", (5 * 240, 380 * filas), (54, 57, 63, 255))
    for i, muestra in enumerate(muestras):
        tex = atlas.copy()
        # pinto el cuadro sobre la zona de la pantalla del atlas solo para la vista previa
        pantalla = Image.new("RGBA", (24, 56), (10, 10, 14, 255))
        alto = round(24 * ALTO / ANCHO)
        pantalla.paste(muestra.resize((24, alto), Image.NEAREST).convert("RGBA"), (0, (56 - alto) // 2))
        tex.paste(pantalla, (2, 2))
        vista = generar_celular.render(generar_celular.modelo(), tex, -15, 8, lado=360, zoom=20)
        vista = vista.crop((60, 0, 300, 380 - 10))
        hoja.paste(vista, ((i % 5) * 240, 5 + (i // 5) * 380), vista)
    hoja.save(HERE / "vista_previa_videos.png")


if __name__ == "__main__":
    main()
