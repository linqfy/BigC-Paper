"""Genera la textura, el modelo 3D (item model de Java) y una vista previa del celular.

Uso:  python tools/celular/generar_celular.py
Escribe en resourcepack/java/assets/celular/ y deja vista_previa_celular.png en tools/celular/.

La textura es de 64x64: cada unidad de UV (0-16) son 4 pixeles. Zonas del atlas (en UV):
  frente (pantalla)   [0, 0, 7, 15]
  espalda (metal)     [7, 0, 14, 15]
  costados            [14, 0, 15.5, 12]
  lente               [15.5, 0, 16, 2]
  botones             [15.5, 2, 16, 4]
  modulo de camara    [14, 12, 16, 15]
  arriba / abajo      [0, 15, 7, 16]
  flash               [7, 15, 8, 16]
"""

import json
import random
from pathlib import Path

import numpy as np
from PIL import Image, ImageDraw

HERE = Path(__file__).resolve().parent
ROOT = HERE.parent.parent
ASSETS = ROOT / "resourcepack" / "java" / "assets" / "celular"
SIZE = 64
PX = SIZE // 16

random.seed(67)


def rect(draw, x0, y0, x1, y1, color):
    """Rectangulo con extremos incluidos (en pixeles)."""
    draw.rectangle([x0, y0, x1, y1], fill=color)


def lerp(a, b, t):
    return tuple(int(round(a[i] + (b[i] - a[i]) * t)) for i in range(len(a)))


def pintar_frente(img):
    d = ImageDraw.Draw(img)
    ox, oy, w, h = 0, 0, 28, 60
    rect(d, ox, oy, ox + w - 1, oy + h - 1, (40, 42, 48, 255))           # marco
    rect(d, ox + 1, oy + 1, ox + w - 2, oy + h - 2, (10, 10, 14, 255))  # borde negro
    # fondo de pantalla: degrade azul -> violeta -> rosa
    sx0, sy0, sx1, sy1 = ox + 2, oy + 2, ox + w - 3, oy + h - 3
    top, mid, bot = (40, 90, 210), (110, 60, 200), (230, 90, 150)
    for y in range(sy0, sy1 + 1):
        t = (y - sy0) / (sy1 - sy0)
        c = lerp(top, mid, t * 2) if t < 0.5 else lerp(mid, bot, (t - 0.5) * 2)
        for x in range(sx0, sx1 + 1):
            n = random.randint(-4, 4)
            img.putpixel((x, y), (max(0, min(255, c[0] + n)), max(0, min(255, c[1] + n)),
                                  max(0, min(255, c[2] + n)), 255))
    # isla de la camara frontal
    rect(d, ox + 10, oy + 3, ox + 17, oy + 4, (5, 5, 8, 255))
    # barra de estado: hora a la izquierda, bateria a la derecha
    for x in (3, 4, 6, 7):
        img.putpixel((ox + x, oy + 4), (245, 245, 245, 255))
    rect(d, ox + 21, oy + 3, ox + 24, oy + 4, (245, 245, 245, 255))
    img.putpixel((ox + 24, oy + 4), (120, 220, 120, 255))
    # grilla de apps: 4 columnas x 5 filas, iconos de 4x4
    colores = [
        (37, 211, 102), (255, 59, 48), (255, 204, 0), (0, 122, 255),
        (255, 149, 0), (175, 82, 222), (255, 45, 85), (90, 200, 250),
        (52, 199, 89), (88, 86, 214), (255, 255, 255), (142, 142, 147),
        (255, 112, 67), (0, 199, 190), (230, 70, 160), (255, 214, 10),
        (60, 60, 67), (48, 176, 199), (255, 69, 58), (100, 210, 255),
    ]
    i = 0
    for fila in range(5):
        for col in range(4):
            x = ox + 3 + col * 6
            y = oy + 8 + fila * 7
            c = colores[i % len(colores)]
            rect(d, x, y, x + 3, y + 3, c + (255,))
            # brillo arriba a la izquierda y esquinas redondeadas
            img.putpixel((x + 1, y + 1), lerp(c, (255, 255, 255), 0.5) + (255,))
            fondo = img.getpixel((x - 1, y))
            for cx, cy in ((x, y), (x + 3, y), (x, y + 3), (x + 3, y + 3)):
                img.putpixel((cx, cy), lerp(c, fondo[:3], 0.55) + (255,))
            i += 1
    # dock: barra translucida con 4 apps
    for y in range(oy + 46, oy + 53):
        for x in range(ox + 3, ox + 25):
            r, g, b, _ = img.getpixel((x, y))
            img.putpixel((x, y), lerp((r, g, b), (255, 255, 255), 0.28) + (255,))
    dock = [(37, 211, 102), (0, 122, 255), (255, 59, 48), (255, 204, 0)]
    for col, c in enumerate(dock):
        x = ox + 4 + col * 5 + (1 if col > 1 else 0)
        rect(d, x, oy + 48, x + 3, oy + 51, c + (255,))
        img.putpixel((x + 1, oy + 49), lerp(c, (255, 255, 255), 0.5) + (255,))
    # barra de inicio
    rect(d, ox + 10, oy + 55, ox + 17, oy + 55, (250, 250, 250, 255))
    # esquinas del marco mas oscuras (se ven redondeadas)
    for cx, cy in ((ox, oy), (ox + w - 1, oy), (ox, oy + h - 1), (ox + w - 1, oy + h - 1)):
        img.putpixel((cx, cy), (20, 20, 24, 255))


def pintar_metal(img, x0, y0, x1, y1, claro, oscuro, vertical=True):
    for y in range(y0, y1 + 1):
        for x in range(x0, x1 + 1):
            t = (x - x0) / max(1, x1 - x0) if vertical else (y - y0) / max(1, y1 - y0)
            c = lerp(claro, oscuro, abs(t - 0.35) * 1.3)
            n = random.randint(-5, 5)
            img.putpixel((x, y), tuple(max(0, min(255, v + n)) for v in c) + (255,))


def pintar_espalda(img):
    d = ImageDraw.Draw(img)
    ox, oy, w, h = 28, 0, 28, 60
    pintar_metal(img, ox, oy, ox + w - 1, oy + h - 1, (214, 218, 224), (150, 156, 166))
    # borde del marco
    for x in range(ox, ox + w):
        img.putpixel((x, oy), (110, 114, 122, 255))
        img.putpixel((x, oy + h - 1), (110, 114, 122, 255))
    for y in range(oy, oy + h):
        img.putpixel((ox, y), (110, 114, 122, 255))
        img.putpixel((ox + w - 1, y), (110, 114, 122, 255))
    # logo: un lingote de hierro chiquito en el centro
    lx, ly = ox + 10, oy + 30
    rect(d, lx + 1, ly, lx + 6, ly, (240, 242, 246, 255))
    rect(d, lx, ly + 1, lx + 7, ly + 3, (196, 200, 208, 255))
    rect(d, lx, ly + 4, lx + 7, ly + 4, (120, 124, 132, 255))
    img.putpixel((lx + 2, ly + 1), (255, 255, 255, 255))
    # texto "CELU" abajo (4 rayitas)
    for i in range(4):
        rect(d, ox + 9 + i * 3, oy + 50, ox + 10 + i * 3, oy + 50, (120, 124, 132, 255))


def pintar_resto(img):
    d = ImageDraw.Draw(img)
    # costados: marco de acero
    pintar_metal(img, 56, 0, 61, 47, (190, 194, 202), (120, 124, 134))
    # lente: negro con brillo azul
    rect(d, 62, 0, 63, 7, (12, 12, 18, 255))
    img.putpixel((62, 1), (90, 140, 230, 255))
    img.putpixel((63, 5), (40, 60, 110, 255))
    # botones
    pintar_metal(img, 62, 8, 63, 15, (170, 174, 182), (110, 114, 124))
    # modulo de camara: vidrio oscuro
    rect(d, 56, 48, 63, 59, (52, 54, 62, 255))
    rect(d, 57, 49, 62, 58, (72, 75, 85, 255))
    img.putpixel((57, 49), (130, 134, 146, 255))
    # arriba / abajo
    pintar_metal(img, 0, 60, 27, 63, (180, 184, 192), (120, 124, 134), vertical=False)
    for x in (11, 13, 15):  # parlante
        img.putpixel((x, 62), (30, 30, 34, 255))
    rect(d, 13, 61, 14, 61, (40, 40, 44, 255))  # puerto USB-C
    # flash
    rect(d, 28, 60, 31, 63, (255, 238, 180, 255))
    img.putpixel((29, 61), (255, 255, 240, 255))


def textura():
    img = Image.new("RGBA", (SIZE, SIZE), (0, 0, 0, 0))
    pintar_frente(img)
    pintar_espalda(img)
    pintar_resto(img)
    return img


def cara(uv, rot=0):
    f = {"uv": uv, "texture": "#0"}
    if rot:
        f["rotation"] = rot
    return f


def caja(nombre, desde, hasta, caras):
    return {"name": nombre, "from": desde, "to": hasta, "faces": caras}


LADO = [14, 0, 15.5, 12]
TAPA = [0, 15, 7, 16]
LENTE = [15.5, 0, 16, 2]
BOTON = [15.5, 2, 16, 4]
CAMARA = [14, 12, 16, 15]
FLASH = [7, 15, 8, 16]


def modelo():
    # El celu esta parado: ancho en X, alto en Y, finito en Z. La pantalla mira al sur (+Z).
    elementos = [
        caja("cuerpo", [4.5, 0.5, 7.25], [11.5, 15.5, 8.75], {
            "south": cara([0, 0, 7, 15]),
            "north": cara([7, 0, 14, 15]),
            "east": cara(LADO),
            "west": cara(LADO),
            "up": cara(TAPA),
            "down": cara(TAPA),
        }),
        # Modulo de camara en la espalda (arriba a la izquierda mirando desde atras)
        caja("modulo_camara", [8.25, 11.25, 6.85], [11, 15, 7.25], {
            "north": cara(CAMARA), "east": cara(LADO), "west": cara(LADO),
            "up": cara(LADO), "down": cara(LADO),
        }),
        caja("lente_1", [9.5, 13.1, 6.55], [10.75, 14.35, 6.85], {
            "north": cara(LENTE), "east": cara(LENTE), "west": cara(LENTE),
            "up": cara(LENTE), "down": cara(LENTE),
        }),
        caja("lente_2", [9.5, 11.5, 6.55], [10.75, 12.75, 6.85], {
            "north": cara(LENTE), "east": cara(LENTE), "west": cara(LENTE),
            "up": cara(LENTE), "down": cara(LENTE),
        }),
        caja("flash", [8.5, 13.5, 6.7], [9.25, 14.25, 6.85], {
            "north": cara(FLASH), "east": cara(FLASH), "west": cara(FLASH),
            "up": cara(FLASH), "down": cara(FLASH),
        }),
        # Boton de encendido (derecha) y volumen (izquierda)
        caja("boton_encendido", [11.5, 10, 7.65], [11.75, 12.25, 8.35], {
            "east": cara(BOTON), "north": cara(BOTON), "south": cara(BOTON),
            "up": cara(BOTON), "down": cara(BOTON),
        }),
        caja("volumen_mas", [4.25, 11.5, 7.65], [4.5, 13, 8.35], {
            "west": cara(BOTON), "north": cara(BOTON), "south": cara(BOTON),
            "up": cara(BOTON), "down": cara(BOTON),
        }),
        caja("volumen_menos", [4.25, 9.5, 7.65], [4.5, 11, 8.35], {
            "west": cara(BOTON), "north": cara(BOTON), "south": cara(BOTON),
            "up": cara(BOTON), "down": cara(BOTON),
        }),
    ]
    return {
        "credit": "Celular - generado por tools/celular/generar_celular.py",
        "texture_size": [SIZE, SIZE],
        "textures": {"0": "celular:item/celular", "particle": "celular:item/celular"},
        "elements": elementos,
        "display": {
            "gui": {"rotation": [15, -25, 0], "translation": [0, 0, 0], "scale": [0.95, 0.95, 0.95]},
            "fixed": {"rotation": [0, 180, 0], "scale": [1, 1, 1]},
            "ground": {"translation": [0, 2, 0], "scale": [0.5, 0.5, 0.5]},
            "firstperson_righthand": {"rotation": [-10, -20, 0], "translation": [-1, 3, -2],
                                      "scale": [0.55, 0.55, 0.55]},
            # Minecraft espeja solo la mano izquierda, asi que van los mismos valores que la derecha
            "firstperson_lefthand": {"rotation": [-10, -20, 0], "translation": [-1, 3, -2],
                                     "scale": [0.55, 0.55, 0.55]},
            "thirdperson_righthand": {"rotation": [0, -90, 0], "translation": [0, 3, 1],
                                      "scale": [0.45, 0.45, 0.45]},
            "thirdperson_lefthand": {"rotation": [0, -90, 0], "translation": [0, 3, 1],
                                     "scale": [0.45, 0.45, 0.45]},
            "head": {"translation": [0, 12, 0], "scale": [0.8, 0.8, 0.8]},
        },
    }


# ---------------------------------------------------------------- vista previa

NORMALES = {
    "south": (0, 0, 1), "north": (0, 0, -1), "east": (1, 0, 0),
    "west": (-1, 0, 0), "up": (0, 1, 0), "down": (0, -1, 0),
}


def esquinas(f, t, nombre):
    """Esquinas de la cara en el orden de la textura: arriba-izq, arriba-der, abajo-der, abajo-izq."""
    (x0, y0, z0), (x1, y1, z1) = f, t
    return {
        "south": [(x0, y1, z1), (x1, y1, z1), (x1, y0, z1), (x0, y0, z1)],
        "north": [(x1, y1, z0), (x0, y1, z0), (x0, y0, z0), (x1, y0, z0)],
        "east": [(x1, y1, z1), (x1, y1, z0), (x1, y0, z0), (x1, y0, z1)],
        "west": [(x0, y1, z0), (x0, y1, z1), (x0, y0, z1), (x0, y0, z0)],
        "up": [(x0, y1, z0), (x1, y1, z0), (x1, y1, z1), (x0, y1, z1)],
        "down": [(x0, y0, z1), (x1, y0, z1), (x1, y0, z0), (x0, y0, z0)],
    }[nombre]


def rot(yaw, pitch):
    a, b = np.radians(yaw), np.radians(pitch)
    ry = np.array([[np.cos(a), 0, np.sin(a)], [0, 1, 0], [-np.sin(a), 0, np.cos(a)]])
    rx = np.array([[1, 0, 0], [0, np.cos(b), -np.sin(b)], [0, np.sin(b), np.cos(b)]])
    return rx @ ry


def coeficientes(destino, origen):
    """Coeficientes de Image.PERSPECTIVE que llevan cada punto destino a su punto origen."""
    m, v = [], []
    for (x, y), (u, w) in zip(destino, origen):
        m.append([x, y, 1, 0, 0, 0, -u * x, -u * y]); v.append(u)
        m.append([0, 0, 0, x, y, 1, -w * x, -w * y]); v.append(w)
    return np.linalg.solve(np.array(m, float), np.array(v, float)).tolist()


def render(model, tex, yaw, pitch, lado=360, zoom=20):
    r = rot(yaw, pitch)
    escala = 4
    big = lado * escala
    out = Image.new("RGBA", (big, big), (0, 0, 0, 0))
    luz = np.array([0.3, 0.8, 0.6]); luz /= np.linalg.norm(luz)
    caras = []
    for el in model["elements"]:
        for nombre, f in el["faces"].items():
            n = r @ np.array(NORMALES[nombre])
            if n[2] <= 1e-6:
                continue
            pts = [r @ (np.array(p) - 8) for p in esquinas(el["from"], el["to"], nombre)]
            prof = sum(p[2] for p in pts) / 4
            caras.append((prof, pts, f["uv"], max(0.45, float(n @ luz))))
    caras.sort(key=lambda c: c[0])
    for _, pts, uv, brillo in caras:
        u0, v0, u1, v1 = [c * PX for c in uv]
        w, h = max(1, round(abs(u1 - u0))), max(1, round(abs(v1 - v0)))
        trozo = tex.crop((min(u0, u1), min(v0, v1), min(u0, u1) + w, min(v0, v1) + h))
        trozo = trozo.resize((w * 16, h * 16), Image.NEAREST)
        trozo = Image.eval(trozo.convert("RGB"), lambda c: int(c * brillo)).convert("RGBA")
        tw, th = trozo.size
        dst = [(big / 2 + p[0] * zoom * escala, big / 2 - p[1] * zoom * escala) for p in pts]
        src = [(0, 0), (tw, 0), (tw, th), (0, th)]
        capa = trozo.transform((big, big), Image.PERSPECTIVE, coeficientes(dst, src), Image.NEAREST)
        mascara = Image.new("L", (big, big), 0)
        ImageDraw.Draw(mascara).polygon(dst, fill=255)
        out.paste(capa, (0, 0), mascara)
    return out.resize((lado, lado), Image.LANCZOS)


def vista_previa(model, tex):
    vistas = [render(model, tex, -25, 12), render(model, tex, 160, 12), render(model, tex, -70, 5)]
    hoja = Image.new("RGBA", (360 * 3 + 64 * 4 + 40, 380), (54, 57, 63, 255))
    for i, v in enumerate(vistas):
        hoja.paste(v, (i * 360, 10), v)
    grande = tex.resize((256, 256), Image.NEAREST)
    hoja.paste(grande, (360 * 3 + 20, 60), grande)
    return hoja


def icono_bedrock(tex):
    """Icono plano de 32x32 (frente del celu) para quien quiera usarlo en Bedrock o en un menu."""
    frente = tex.crop((0, 0, 28, 60)).resize((14, 30), Image.LANCZOS)
    icono = Image.new("RGBA", (32, 32), (0, 0, 0, 0))
    icono.paste(frente, (9, 1))
    return icono


def main():
    tex = textura()
    model = modelo()
    (ASSETS / "textures" / "item").mkdir(parents=True, exist_ok=True)
    (ASSETS / "models" / "item").mkdir(parents=True, exist_ok=True)
    (ASSETS / "items").mkdir(parents=True, exist_ok=True)
    tex.save(ASSETS / "textures" / "item" / "celular.png")
    (ASSETS / "models" / "item" / "celular.json").write_text(json.dumps(model, indent=2), encoding="utf-8", newline="\n")
    (ASSETS / "items" / "celular.json").write_text(json.dumps(
        {"model": {"type": "minecraft:model", "model": "celular:item/celular"}}, indent=2), encoding="utf-8", newline="\n")
    vista_previa(model, tex).save(HERE / "vista_previa_celular.png")
    print("Listo: textura, modelo y vista previa generados.")


if __name__ == "__main__":
    main()
