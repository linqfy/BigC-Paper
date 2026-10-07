"""Convierte los audios de sonidos/ (mp3, wav, ogg...) a sonidos de Minecraft.

Para cada archivo genera assets/donpollos/sounds/don_pollo_N.ogg (mono, para que suene desde la
posicion del Don Pollo), el sounds.json del pack y src/main/resources/donpollos/sonidos.json con la duracion
de cada uno (el plugin la usa para no encimar dos sonidos en el mismo Don Pollo).

Uso: python tools/convertir_sonidos.py   (necesita ffmpeg)
"""
import json
import subprocess
from pathlib import Path

ROOT = Path(__file__).resolve().parents[2]  # tools/donpollos -> raiz de BigC-Paper
FUENTES = Path(__file__).resolve().parent / "fuentes"
SOURCE = FUENTES / "sonidos"
PACK = ROOT / "resourcepack" / "java" / "assets" / "donpollos"
CATALOG = ROOT / "src" / "main" / "resources" / "donpollos" / "sonidos.json"
AUDIO = {".mp3", ".wav", ".ogg", ".m4a", ".flac", ".opus", ".webm"}
# Ajuste de volumen en dB para audios puntuales (parte del nombre del archivo -> dB). Negativo = mas bajo.
GAIN_DB = {"SATURADO": -17.0}


def main():
    files = sorted(p for p in SOURCE.iterdir() if p.suffix.lower() in AUDIO)
    out_dir = PACK / "sounds"
    out_dir.mkdir(parents=True, exist_ok=True)
    for old in out_dir.glob("don_pollo_*.ogg"):
        old.unlink()
    sounds, catalog = {}, []
    for i, source in enumerate(files, 1):
        name = f"don_pollo_{i}"
        target = out_dir / f"{name}.ogg"
        gain = sum(db for part, db in GAIN_DB.items() if part.lower() in source.name.lower())
        subprocess.run(["ffmpeg", "-v", "error", "-y", "-i", str(source), "-ac", "1", "-ar", "44100",
                        "-af", f"volume={gain}dB", "-c:a", "libvorbis", "-q:a", "3", str(target)], check=True)
        seconds = float(subprocess.run(["ffprobe", "-v", "error", "-show_entries", "format=duration",
                                        "-of", "csv=p=0", str(target)], capture_output=True, text=True).stdout)
        sounds[name] = {"subtitle": "Don Pollo", "sounds": [{"name": f"donpollos:{name}", "stream": True}]}
        catalog.append({"sound": f"donpollos:{name}", "seconds": round(seconds, 2), "origen": source.name})
        print(f"{name}: {seconds:.1f}s" + (f" (volumen {gain:+.0f} dB)" if gain else ""))
    (PACK / "sounds.json").write_text(json.dumps(sounds, indent=2), encoding="utf-8")
    CATALOG.write_text(json.dumps(catalog, indent=2, ensure_ascii=False), encoding="utf-8")
    print(f"{len(files)} sonidos listos. Recompila el plugin (gradlew.bat deployToServer).")


if __name__ == "__main__":
    main()
