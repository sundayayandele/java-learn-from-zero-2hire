#!/usr/bin/env python3
"""Online check of every external http(s) link in the Markdown sources.

    python scripts/check_links.py

Reports links that return 4xx/5xx or can't be reached. Some sites block automated requests (403/429);
check those by hand before removing them. Standard library only.
"""
from __future__ import annotations

import concurrent.futures as cf
import re
import sys
import urllib.error
import urllib.request
from pathlib import Path

ROOT = Path(__file__).resolve().parent.parent
URL = re.compile(r"\]\((https?://[^)\s]+)\)|<(https?://[^>\s]+)>")
SKIP_DIRS = {"target", ".git", "out", "private"}
SKIP_HOSTS = ("localhost", "127.0.0.1", "example", "yourname.dev", "<")
UA = {"User-Agent": "Mozilla/5.0 (java-zero-hire link check)"}
# These sites answer 403 to scripts but work in a browser (checked by hand on 2026-10-22).
BOT_BLOCKING_HOSTS = ("dev.mysql.com", "consumerfinance.gov")


def urls() -> dict[str, set[str]]:
    found: dict[str, set[str]] = {}
    for md in ROOT.rglob("*.md"):
        if any(p in SKIP_DIRS for p in md.relative_to(ROOT).parts) or md.name == "README.md" and "GENERATED" in md.read_text(encoding="utf-8")[:200]:
            continue
        for m in URL.finditer(md.read_text(encoding="utf-8")):
            u = (m.group(1) or m.group(2)).rstrip(".,")
            if not any(h in u for h in SKIP_HOSTS):
                found.setdefault(u, set()).add(str(md.relative_to(ROOT)))
    return found


def status(url: str) -> str | None:
    for method in ("HEAD", "GET"):
        try:
            req = urllib.request.Request(url, headers=UA, method=method)
            with urllib.request.urlopen(req, timeout=20):
                return None
        except urllib.error.HTTPError as e:
            if method == "HEAD" and e.code in (403, 404, 405, 429, 500, 501):
                continue
            if e.code == 403 and any(h in url for h in BOT_BLOCKING_HOSTS):
                return None
            return f"HTTP {e.code}"
        except Exception as e:  # noqa: BLE001
            if method == "GET":
                return type(e).__name__
    return None


def main() -> int:
    found = urls()
    with cf.ThreadPoolExecutor(16) as pool:
        results = dict(zip(found, pool.map(status, found)))
    bad = {u: s for u, s in results.items() if s}
    print(f"links: {len(found)} checked, {len(bad)} problem(s)")
    for u, s in sorted(bad.items()):
        print(f"  {s}: {u}  (in {', '.join(sorted(found[u]))})")
    return 1 if bad else 0


if __name__ == "__main__":
    sys.exit(main())
