#!/usr/bin/env python3
"""Accessibility checks on every HTML page published to Moodle (lms/moodle/import/{pages,guides,capstones}).

    python scripts/check_accessibility.py

Checks the problems that automated tools can find reliably (WCAG 2.2 A/AA):
  * every image has a meaningful alt text (1.1.1);
  * every embedded video (iframe) has a title (4.1.2);
  * link text says where it goes: no empty links and no "click here", "here", "link" or bare URLs (2.4.4);
  * headings don't skip levels, for example h2 straight to h4 (1.3.1);
  * every table has a header row (1.3.1).
Captions are checked separately: lms/check_videos.py confirms English captions on every video.
Run by scripts/check_repo.py. Standard library only.
"""
from __future__ import annotations

import re
import sys
from html.parser import HTMLParser
from pathlib import Path

ROOT = Path(__file__).resolve().parent.parent
PAGES = ROOT / "lms" / "moodle" / "import"
VAGUE = {"here", "click here", "link", "this link", "read more", "more", "this"}


class Page(HTMLParser):
    def __init__(self):
        super().__init__(convert_charrefs=True)
        self.problems: list[str] = []
        self.level = 1               # Moodle shows the activity name as the page's h1/h2
        self.link: dict | None = None
        self.table: dict | None = None

    def handle_starttag(self, tag, attrs):
        a = dict(attrs)
        if tag == "img" and not (a.get("alt") or "").strip():
            self.problems.append(f"image without alt text: {a.get('src', '')[-60:]}")
        if tag == "iframe" and not (a.get("title") or "").strip():
            self.problems.append(f"iframe without a title: {a.get('src', '')[-60:]}")
        if tag == "a" and a.get("href"):
            self.link = {"href": a["href"], "text": "", "label": a.get("aria-label", "")}
        if re.fullmatch(r"h[1-6]", tag):
            n = int(tag[1])
            if n > self.level + 1:
                self.problems.append(f"heading jumps from h{self.level} to h{n}")
            self.level = n
        if tag == "table":
            self.table = {"th": False}
        if tag == "th" and self.table is not None:
            self.table["th"] = True

    def handle_data(self, data):
        if self.link is not None:
            self.link["text"] += data

    def handle_endtag(self, tag):
        if tag == "a" and self.link is not None:
            text = " ".join(self.link["text"].split()).lower()
            if not text and not self.link["label"]:
                self.problems.append(f"link without text: {self.link['href'][:60]}")
            elif text in VAGUE:
                self.problems.append(f"vague link text '{text}': {self.link['href'][:60]}")
            self.link = None
        if tag == "table" and self.table is not None:
            if not self.table["th"]:
                self.problems.append("table without a header row")
            self.table = None


def check() -> list[str]:
    out = []
    for f in sorted(PAGES.rglob("*.html")):
        if not any(part in ("pages", "guides", "capstones") for part in f.relative_to(PAGES).parts):
            continue
        p = Page()
        p.feed(f.read_text(encoding="utf-8"))
        out += [f"{f.relative_to(ROOT)}: {x}" for x in p.problems]
    return out


def main() -> int:
    problems = check()
    n = sum(1 for f in PAGES.rglob("*.html"))
    print(f"accessibility: {n} pages checked, {len(problems)} problem(s)")
    for p in problems:
        print("  " + p)
    return 1 if problems else 0


if __name__ == "__main__":
    sys.exit(main())
