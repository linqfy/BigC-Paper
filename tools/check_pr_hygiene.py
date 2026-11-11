#!/usr/bin/env python3
"""Block forbidden agent artifacts and standalone plugins in pull requests."""
from __future__ import annotations

import os
from pathlib import PurePosixPath
import re
import subprocess
import sys

OWNER = "linqfy"
POLICY = {"AGENTS.md", "GUIDELINES.MD", ".github/CODEOWNERS",
          ".github/workflows/build-jar.yml", "tools/check_pr_hygiene.py"}
DOC_DIRS = ("docs/superpowers/", "docs/audits/")
SCRATCH = (".agents/runs/", ".agents/reports/", ".claude/plans/",
           "work/", "reports/", "audit/", "audits/", "plans/")
ARCHIVES = (".jar", ".zip", ".mcpack", ".mcaddon", ".mcworld",
            ".7z", ".rar", ".tar", ".tar.gz", ".tgz")
MODEL_NOTES = re.compile(
    r"(^|/)(?:agent[_-]?audit|model[_-]?audit|(?:ai|agent)[_-]?"
    r"(?:report|notes|plan|review|handoff|changelog|todo|log|credits)|"
    r"(?:plan|audit|retro|handoff|review)[_-]?\d{4}[-_]\d{2})",
    re.IGNORECASE,
)


def changes(base: str) -> list[tuple[str, str]]:
    data = subprocess.check_output(
        ["git", "diff", "--name-status", "-z", f"{base}...HEAD"]
    ).decode("utf-8", "replace").split("\0")
    out = []
    i = 0
    while i < len(data) and data[i]:
        status = data[i]
        i += 1
        if status[0] in ("R", "C"):
            i += 1  # Skip rename/copy source; inspect destination.
        path = data[i]
        i += 1
        out.append((status, path.replace("\\", "/")))
    return out


def main() -> int:
    if len(sys.argv) != 2:
        print("usage: check_pr_hygiene.py <trusted-base-ref>", file=sys.stderr)
        return 2
    base = sys.argv[1]
    owner_pr = os.environ.get("PR_AUTHOR", "").lower() == OWNER
    problems = []
    try:
        modified = changes(base)
    except subprocess.CalledProcessError:
        print("REFUSED: upstream diff could not be verified.", file=sys.stderr)
        return 1

    for status, path in modified:
        lower = path.lower()
        basename = PurePosixPath(path).name
        added = status[0] in ("A", "C", "R")
        deleted = status[0] == "D"
        if deleted:
            # Removing files is cleanup, not new paperwork; only originals are protected.
            if lower.startswith(("agent_uploads/", "tmp/sahur-uv-backup/")):
                problems.append(f"{path}: source originals/backups must be preserved")
            continue
        if path in POLICY and not owner_pr:
            problems.append(f"{path}: policy/CI file requires repository-owner PR")
        if path == "README.md":
            problems.append(f"{path}: no opportunistic README changes")
        if lower.startswith(DOC_DIRS):
            problems.append(f"{path}: no historical agent plans/audits")
        if lower.startswith(SCRATCH) or MODEL_NOTES.search(path):
            problems.append(f"{path}: private agent working material")
        if (lower.endswith((".md", ".mdx", ".rst")) and not owner_pr
                and path not in {"AGENTS.md", "GUIDELINES.MD"}):
            problems.append(f"{path}: no agent-authored/modified Markdown documentation")
        if added and lower.endswith(ARCHIVES):
            problems.append(f"{path}: generated or packaged artifact")
        if added and (basename == "paper-plugin.yml" or
                      (basename == "plugin.yml" and path != "src/main/resources/plugin.yml")):
            problems.append(f"{path}: second plugin descriptor")
        if added and "/" in path and basename in {
            "build.gradle", "build.gradle.kts", "settings.gradle",
            "settings.gradle.kts", "pom.xml"
        }:
            problems.append(f"{path}: nested plugin/build project")

    try:
        patch = subprocess.check_output(
            ["git", "diff", "--unified=0", f"{base}...HEAD", "--", "src/main/java"],
            text=True, errors="replace"
        )
        if any(
            line.startswith("+") and not line.startswith("+++")
            and re.search(r"\bextends\s+(?:org\.bukkit\.plugin\.java\.)?JavaPlugin\b", line)
            for line in patch.splitlines()
        ):
            problems.append("New JavaPlugin subclass: use existing PluginModule instead")
    except subprocess.CalledProcessError:
        problems.append("Unable to inspect Java diff for second plugins")

    if problems:
        print("REFUSED: BigCasares hygiene gate triggered:", file=sys.stderr)
        for problem in sorted(set(problems)):
            print("  - " + problem, file=sys.stderr)
        print(
            "IMPROVE: unstage/delete generated paperwork and compiled packs; "
            "retain only editable source assets; keep plans private; "
            "integrate using existing PluginModule. Request owner review for "
            "exceptional policy changes and rerun all checks.",
            file=sys.stderr,
        )
        return 1
    print("PR hygiene gate: PASS; all repository-agent rules still apply")
    return 0


if __name__ == "__main__":
    sys.exit(main())
