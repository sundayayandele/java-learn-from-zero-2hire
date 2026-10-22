#!/usr/bin/env python3
"""Check the curated videos in lms/videos.json.

    python lms/check_videos.py --schema   # offline: structure only (run by scripts/check_repo.py)
    python lms/check_videos.py --quick    # online: each video exists and allows embedding (oEmbed)
    python lms/check_videos.py            # online: also English captions, playable, length
    python lms/check_videos.py --fill     # full check, then write title, channel, minutes, published back

Standard library only. --quick uses YouTube's oEmbed endpoint, which also works from CI.
The full check reads each public watch page; YouTube sometimes blocks that from cloud IPs,
so run it on your own machine when adding or reviewing videos.
"""
from __future__ import annotations

import datetime as dt
import json
import re
import sys
import urllib.error
import urllib.request
from pathlib import Path

ROOT = Path(__file__).resolve().parent.parent
DATA = ROOT / "lms" / "videos.json"
UA = {"User-Agent": "Mozilla/5.0 (java-zero-hire video check)", "Accept-Language": "en-US,en;q=0.9"}
ID_RE = re.compile(r"^[A-Za-z0-9_-]{11}$")
MAX_CORE_MINUTES = 30.5


def videos(data: dict):
    for topic, items in data["topics"].items():
        for v in items:
            yield topic, v


def check_schema(data: dict) -> list[str]:
    problems, seen = [], {}
    for key in ("about", "topics"):
        if key not in data:
            problems.append(f"missing top-level key '{key}'")
    for topic, v in videos(data):
        for key in ("id", "why"):
            if key not in v:
                problems.append(f"{topic}: {v.get('id', '?')} missing '{key}'")
        vid = v.get("id", "")
        if not ID_RE.match(vid):
            problems.append(f"{topic}: bad video id '{vid}'")
        if "minutes" in v and not v.get("optional") and float(v["minutes"]) > MAX_CORE_MINUTES:
            problems.append(f"{topic}: {vid} is {v['minutes']} min; mark it optional or replace it")
        if vid in seen:
            problems.append(f"{topic}: {vid} also listed under {seen[vid]}")
        seen.setdefault(vid, topic)
    return problems


def get(url: str) -> str:
    with urllib.request.urlopen(urllib.request.Request(url, headers=UA), timeout=20) as r:
        return r.read().decode("utf-8", "replace")


def check_online(vid: str, full: bool) -> tuple[list[str], dict]:
    meta: dict = {}
    try:
        o = json.loads(get(f"https://www.youtube.com/oembed?url=https://www.youtube.com/watch?v={vid}&format=json"))
        meta["title"], meta["channel"] = o.get("title", ""), o.get("author_name", "")
    except urllib.error.HTTPError as e:
        return [{401: "embedding disabled", 403: "embedding disabled", 404: "removed or private"}.get(e.code, f"oEmbed HTTP {e.code}")], meta
    except Exception as e:  # noqa: BLE001
        return [f"oEmbed failed: {e}"], meta
    if not full:
        return [], meta
    try:
        html = get(f"https://www.youtube.com/watch?v={vid}&hl=en")
        m = re.search(r"ytInitialPlayerResponse\s*=\s*(\{.+?\});(?:var|</script>)", html, re.S)
        pr = json.loads(m.group(1))
    except Exception as e:  # noqa: BLE001
        return [f"could not read watch page ({e}); try again later or from another network"], meta
    issues = []
    if pr.get("playabilityStatus", {}).get("status") != "OK":
        issues.append(f"not playable: {pr.get('playabilityStatus', {}).get('status')}")
    details = pr.get("videoDetails", {})
    if details.get("lengthSeconds"):
        meta["minutes"] = round(int(details["lengthSeconds"]) / 60, 1)
    micro = pr.get("microformat", {}).get("playerMicroformatRenderer", {})
    if micro.get("publishDate"):
        meta["published"] = micro["publishDate"][:10]
    tracks = pr.get("captions", {}).get("playerCaptionsTracklistRenderer", {}).get("captionTracks", [])
    if not any(t.get("languageCode", "").split("-")[0] == "en" for t in tracks):
        issues.append("no English captions")
    return issues, meta


def main(argv: list[str]) -> int:
    data = json.loads(DATA.read_text(encoding="utf-8"))
    problems = check_schema(data)
    if "--schema" in argv:
        if problems:
            print("videos.json:\n  " + "\n  ".join(problems), file=sys.stderr)
            return 1
        print(f"videos.json: {sum(1 for _ in videos(data))} videos, structure OK")
        return 0
    full = "--quick" not in argv
    bad = list(problems)
    checked = 0
    for topic, v in videos(data):
        checked += 1
        issues, meta = check_online(v["id"], full)
        if "--fill" in argv:
            for key in ("title", "channel", "minutes", "published"):
                if meta.get(key) not in (None, ""):
                    v[key] = meta[key]
        for issue in issues:
            bad.append(f"{topic}: {v['id']} \"{v.get('title', meta.get('title', ''))[:60]}\" - {issue}")
    if "--fill" in argv:
        data["checked"] = dt.date.today().isoformat()
        ordered = {k: data[k] for k in ("about", "checked", "topics")}
        for items in ordered["topics"].values():
            items[:] = [{k: v[k] for k in ("id", "title", "channel", "minutes", "published", "optional", "why") if k in v} for v in items]
        DATA.write_text(json.dumps(ordered, indent=2, ensure_ascii=False) + "\n", encoding="utf-8")
        bad += check_schema(ordered)
    mode = "full" if full else "quick (exists + embeddable)"
    print(f"videos: {checked} checked ({mode}), {len(bad)} problem(s)")
    for line in bad:
        print(f"  PROBLEM {line}")
    return 1 if bad else 0


if __name__ == "__main__":
    sys.exit(main(sys.argv[1:]))
