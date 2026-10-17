#!/usr/bin/env python3
"""Import the data and art BigCasares uses from Mielon's The Sift mod jar.

Usage:
    python3 -I tools/port_mielon_sift.py [--include-music] <path/to/mielons-the-sift-*.jar>

The jar is read as a ZIP archive only; no mod code is loaded or executed.
Mielon's The Sift is MIT licensed (see LICENSE_the_sift inside the jar); the
licence text is copied next to every imported tree.

Outputs (overwritten on every run, so local edits belong in Java code):
  src/main/resources/the_sift/mielon/   worldgen/dimension data read by
                                          dev.linqfy.bigCasares.modules.sift
                                          (minecraft/: vanilla templates the mod
                                          overrides, e.g. Ancient City centres)
  resourcepack/java/assets/the_sift/     textures, models, item definitions,
                                          sounds and lang shipped to Java clients

Only formats that Minecraft 26.2 clients understand are copied. Fluid sprites,
shaders, custom particles, GUI sprites and models using 26.3-only syntax are
skipped because unmodified 26.2 clients cannot use them.

The two music disc tracks ("Rift" by Fuzja Jądrowa, "Wellspring" remix by
Echo_Dev) are third-party contributions credited by the mod. They are left out
unless --include-music is passed, after their authors' permission for this
server has been confirmed.
"""
import json
import shutil
import sys
import zipfile
from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]
DATA_OUT = ROOT / "src/main/resources/the_sift/mielon"
ASSET_OUT = ROOT / "resourcepack/java/assets/the_sift"

DATA_PREFIXES = (
    "data/the_sift/worldgen/density_function/",
    "data/the_sift/worldgen/noise/",
    "data/the_sift/worldgen/noise_settings/",
    "data/the_sift/worldgen/biome/",
    "data/the_sift/worldgen/feature/",
    "data/the_sift/worldgen/placed_feature/",
    "data/the_sift/worldgen/carver/",
    "data/the_sift/worldgen/processor_list/",
    "data/the_sift/dimension/",
    "data/the_sift/dimension_type/",
    "data/the_sift/timeline/",
    "data/the_sift/structure/",
    "data/the_sift/tags/block/",
    "data/the_sift/loot_table/",
    "data/the_sift/recipe/",
)

# Vanilla templates the mod overrides: Ancient City centres with Sonorous Deepslate.
MINECRAFT_DATA_PREFIXES = (
    "data/minecraft/structure/ancient_city/city_center/",
)

ASSET_PREFIXES = (
    "assets/the_sift/models/block/",
    "assets/the_sift/models/item/",
    "assets/the_sift/items/",
    "assets/the_sift/textures/block/",
    "assets/the_sift/textures/item/",
    "assets/the_sift/textures/entity/equipment/",
    "assets/the_sift/equipment/",
    "assets/the_sift/sounds/",
    "assets/the_sift/sounds.json",
    "assets/the_sift/lang/en_us.json",
)

# Animated fluid sheets and glass need client features the port does not use.
SKIPPED_ASSETS = (
    "assets/the_sift/textures/block/ichor_flow",
    "assets/the_sift/textures/block/ichor_still",
    "assets/the_sift/models/block/ichor_glass",
    "assets/the_sift/models/item/ichor_glass",
    "assets/the_sift/items/ichor_glass",
    "assets/the_sift/models/block/ichor_cauldron",
)


MUSIC_PREFIX = "assets/the_sift/sounds/music/"
MUSIC_SOUND_PREFIX = "the_sift:music/"


def without_music_events(raw: bytes) -> bytes:
    """Drops sound events that only play the excluded music tracks."""
    sounds = json.loads(raw)
    kept = {}
    for event, definition in sounds.items():
        names = [entry if isinstance(entry, str) else entry.get("name", "") for entry in definition.get("sounds", [])]
        if names and all(name.startswith(MUSIC_SOUND_PREFIX) for name in names):
            continue
        kept[event] = definition
    return (json.dumps(kept, indent=2, ensure_ascii=False) + "\n").encode("utf-8")


def safe_name(name: str) -> bool:
    return not name.startswith("/") and ".." not in name.split("/") and "\\" not in name


def uses_unsupported_model_syntax(raw: bytes) -> bool:
    """26.3 lets textures be objects ({"sprite": ...}); 26.2 clients reject them."""
    try:
        model = json.loads(raw)
    except ValueError:
        return True
    textures = model.get("textures", {}) if isinstance(model, dict) else {}
    return any(not isinstance(value, str) for value in textures.values())


def main() -> int:
    arguments = sys.argv[1:]
    include_music = "--include-music" in arguments
    arguments = [argument for argument in arguments if argument != "--include-music"]
    if len(arguments) != 1:
        print(__doc__)
        return 2
    jar = Path(arguments[0])
    with zipfile.ZipFile(jar) as archive:
        names = [name for name in archive.namelist() if not name.endswith("/") and safe_name(name)]
        if "LICENSE_the_sift" not in names:
            print("Refusing to import: LICENSE_the_sift not found in", jar)
            return 1
        licence = archive.read("LICENSE_the_sift")
        manifest = json.loads(archive.read("fabric.mod.json"))

        for target in (DATA_OUT, ASSET_OUT):
            if target.exists():
                shutil.rmtree(target)
            target.mkdir(parents=True)

        copied_data = copied_assets = skipped = 0
        for name in sorted(names):
            if name.startswith(MINECRAFT_DATA_PREFIXES):
                destination = DATA_OUT / "minecraft" / name[len("data/minecraft/"):]
                destination.parent.mkdir(parents=True, exist_ok=True)
                destination.write_bytes(archive.read(name))
                copied_data += 1
            elif name.startswith(DATA_PREFIXES):
                destination = DATA_OUT / name[len("data/the_sift/"):]
                destination.parent.mkdir(parents=True, exist_ok=True)
                destination.write_bytes(archive.read(name))
                copied_data += 1
            elif name.startswith(ASSET_PREFIXES):
                if name.startswith(SKIPPED_ASSETS) or (not include_music and name.startswith(MUSIC_PREFIX)):
                    skipped += 1
                    continue
                raw = archive.read(name)
                if name == "assets/the_sift/sounds.json" and not include_music:
                    raw = without_music_events(raw)
                if "/models/" in name and name.endswith(".json") and uses_unsupported_model_syntax(raw):
                    skipped += 1
                    continue
                destination = ASSET_OUT / name[len("assets/the_sift/"):]
                destination.parent.mkdir(parents=True, exist_ok=True)
                destination.write_bytes(raw)
                copied_assets += 1

        credits = (
            "Imported from {name} {version} by {authors}.\n"
            "Contributors credited by the mod: {contributors}.\n"
            "Licence: {licence}. See LICENSE_the_sift in this directory.\n"
        ).format(
            name=manifest.get("name"), version=manifest.get("version"),
            authors=", ".join(manifest.get("authors", [])),
            contributors="; ".join(manifest.get("contributors", [])),
            licence=manifest.get("license"),
        )
        for target in (DATA_OUT, ASSET_OUT):
            (target / "LICENSE_the_sift").write_bytes(licence)
            (target / "CREDITS.txt").write_text(credits, encoding="utf-8")

    print(f"data files: {copied_data}, asset files: {copied_assets}, skipped: {skipped}")
    return 0


if __name__ == "__main__":
    sys.exit(main())
