#!/usr/bin/env python3
"""Offline consistency checks, run in CI.

    python scripts/check_repo.py

1. Generated READMEs are up to date (scripts/build.py --check).
2. Capstone starters match the template (scripts/new_starter.py --check).
3. lms/videos.json is well-formed, and every built topic has videos.
4. Every cbt.json is valid: 5-10 questions, answers in range, no duplicate options.
5. Every relative Markdown link points to an existing file.
"""
from __future__ import annotations

import json
import re
import subprocess
import sys
from pathlib import Path

ROOT = Path(__file__).resolve().parent.parent
LINK = re.compile(r"\[[^\]]*\]\(([^)\s]+)\)")
SKIP_DIRS = {"target", ".git", "node_modules", "out", "private"}


def run(*cmd: str) -> bool:
    return subprocess.run([sys.executable, *cmd], cwd=ROOT).returncode == 0


def check_cbts() -> list[str]:
    problems = []
    cur = json.loads((ROOT / "curriculum.json").read_text(encoding="utf-8"))
    videos = json.loads((ROOT / "lms" / "videos.json").read_text(encoding="utf-8"))["topics"]
    for phase in cur["phases"]:
        if phase["status"] != "built":
            continue
        for mod in phase["modules"]:
            for t in mod["topics"]:
                tdir = ROOT / "phases" / phase["slug"] / mod["slug"] / t["slug"]
                if not videos.get(t["id"]):
                    problems.append(f"topic {t['id']}: no videos in lms/videos.json")
                if not (tdir / "lesson.md").exists():
                    problems.append(f"topic {t['id']}: missing lesson.md")
                    continue
                cbt = json.loads((tdir / "cbt.json").read_text(encoding="utf-8"))
                qs = cbt["questions"]
                if not 5 <= len(qs) <= 10:
                    problems.append(f"topic {t['id']}: {len(qs)} CBT questions (need 5-10)")
                for i, q in enumerate(qs, 1):
                    answers = q["answer"] if isinstance(q["answer"], list) else [q["answer"]]
                    if any(not 0 <= a < len(q["options"]) for a in answers):
                        problems.append(f"topic {t['id']} Q{i}: answer out of range")
                    if len(set(q["options"])) != len(q["options"]):
                        problems.append(f"topic {t['id']} Q{i}: duplicate options")
                    if (q["type"] == "multi") != isinstance(q["answer"], list):
                        problems.append(f"topic {t['id']} Q{i}: type/answer mismatch")
    return problems


def check_links() -> list[str]:
    problems = []
    for md in ROOT.rglob("*.md"):
        if any(part in SKIP_DIRS for part in md.relative_to(ROOT).parts):
            continue
        text = md.read_text(encoding="utf-8")
        text = re.sub(r"```.*?```", "", text, flags=re.S)          # ignore code blocks
        for target in LINK.findall(text):
            if re.match(r"^[a-z]+:", target) or target.startswith("#"):
                continue
            path = target.split("#", 1)[0]
            if path and not (md.parent / path).resolve().exists():
                problems.append(f"{md.relative_to(ROOT)}: broken link {target}")
    return problems


def main() -> int:
    ok = True
    ok &= run("scripts/build.py", "--check")
    ok &= run("scripts/new_starter.py", "--check")
    ok &= run("lms/check_videos.py", "--schema")
    problems = check_cbts() + check_links()
    for p in problems:
        print(p, file=sys.stderr)
    if problems or not ok:
        print("check_repo: FAILED", file=sys.stderr)
        return 1
    print("check_repo: all checks passed")
    return 0


if __name__ == "__main__":
    sys.exit(main())
