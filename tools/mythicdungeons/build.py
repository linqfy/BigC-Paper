#!/usr/bin/env python3
"""Build the mythic-dungeon BetterModel models authored with bbkit.

Every ``tools/mythicdungeons/models/*.py`` (files starting with ``_`` are skipped) exposes
``MODELS = {"bigcasares_myth_<id>": builder}`` (optionally ``REQUIRED_ANIMATIONS``,
default idle/walk/attack/death) where ``builder()`` returns a ``bbkit.Model``
named exactly like its key.  Each built model is validated and written to
``src/main/resources/bettermodel/models/<id>.bbmodel``.

Usage
-----
    python tools/mythicdungeons/build.py --list
    python tools/mythicdungeons/build.py bigcasares_myth_minotaur [more ids...]
    python tools/mythicdungeons/build.py bigcasares_myth_minotaur --preview   # + work/previews/<id>*.png
    python tools/mythicdungeons/build.py --all [--dry-run] [--strict]

--dry-run validates without writing; --strict turns warnings into errors; --preview renders
the rest sheet (with atlas) and one strip per animation into work/previews/.  Build only the
ids you own: other authors may be mid-edit.  Output is deterministic (same source -> same bytes).
"""
from __future__ import annotations

import argparse
import importlib.util
import sys

sys.dont_write_bytecode = True  # keep __pycache__ out of the source tree
import traceback
from pathlib import Path

HERE = Path(__file__).resolve().parent
sys.path.insert(0, str(HERE))
import bbkit  # noqa: E402

REPO = HERE.parents[1]
OUT = REPO / "src/main/resources/bettermodel/models"
PREVIEWS = REPO / "work/previews"
# A models/*.py may override with REQUIRED_ANIMATIONS = (...) (e.g. a turret without walk).
REQUIRED = ("idle", "walk", "attack", "death")


def discover():
    builders = {}
    for path in sorted((HERE / "models").glob("*.py")):
        if path.name.startswith("_"):
            continue
        spec = importlib.util.spec_from_file_location(f"mythic_models_{path.stem}", path)
        module = importlib.util.module_from_spec(spec)
        try:
            spec.loader.exec_module(module)
        except Exception:
            print(f"! cannot import {path.name}:\n{traceback.format_exc()}", file=sys.stderr)
            continue
        for key, fn in getattr(module, "MODELS", {}).items():
            if not key.startswith("bigcasares_myth_"):
                raise SystemExit(f"{path.name}: model id {key!r} must start with bigcasares_myth_")
            if key in builders:
                raise SystemExit(f"duplicate model id {key!r} in {path.name} and {builders[key][0].name}")
            builders[key] = (path, fn, tuple(getattr(module, "REQUIRED_ANIMATIONS", REQUIRED)))
    return builders


def build_one(key, path, fn, required, args):
    model = fn()
    if not isinstance(model, bbkit.Model) or model.name != key:
        raise SystemExit(f"{path.name}: builder for {key} must return bbkit.Model named {key!r}")
    model.validate(require=required, strict=args.strict)
    lo, hi = model.bounds()
    print(f"{key}: {len(model.bones)} bones, {len(model.cubes)} cubes, height {(hi[1] - lo[1]) / 16:.2f} blocks, "
          f"animations {', '.join(f'{a.name}({a.loop} {a.length:g}s)' for a in model.animations.values())}")
    if args.dry_run:
        return
    target = model.save(OUT / f"{key}.bbmodel")
    print(f"  wrote {target.relative_to(REPO)}")
    if args.preview:
        from preview import render_sheet
        print("  preview", render_sheet(target, PREVIEWS / f"{key}.png", atlas=True))
        for anim in model.animations.values():
            print("  preview", render_sheet(target, PREVIEWS / f"{key}_{anim.name}.png",
                                            views=("three_quarter", "side"), anim=anim.name, frames=6, size=260))


def main(argv=None):
    ap = argparse.ArgumentParser(description="Build bbkit models into BetterModel .bbmodel files.")
    ap.add_argument("ids", nargs="*")
    ap.add_argument("--all", action="store_true")
    ap.add_argument("--list", action="store_true")
    ap.add_argument("--dry-run", action="store_true")
    ap.add_argument("--strict", action="store_true")
    ap.add_argument("--preview", action="store_true")
    args = ap.parse_args(argv)
    builders = discover()
    if args.list or (not args.ids and not args.all):
        for key, (path, _fn, _req) in sorted(builders.items()):
            print(f"{key}  ({path.name})")
        return 0
    keys = sorted(builders) if args.all else args.ids
    unknown = [k for k in keys if k not in builders]
    if unknown:
        raise SystemExit(f"unknown model ids {unknown}; known: {sorted(builders)}")
    failed = []
    for key in keys:
        path, fn, required = builders[key]
        try:
            build_one(key, path, fn, required, args)
        except (bbkit.ValidationError, bbkit.TextureOverflow) as exc:
            print(f"FAILED {exc}", file=sys.stderr)
            failed.append(key)
    if failed:
        print(f"{len(failed)} model(s) failed: {failed}", file=sys.stderr)
        return 1
    return 0


if __name__ == "__main__":
    sys.exit(main())
