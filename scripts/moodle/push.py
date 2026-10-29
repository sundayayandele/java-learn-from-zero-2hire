#!/usr/bin/env python3
"""Push the course skeleton to Moodle through its web services (REST) API.

    python scripts/moodle/push.py check                 # who am I, which functions can this token call?
    python scripts/moodle/push.py plan                  # offline: what apply would create (no token needed)
    python scripts/moodle/push.py apply                 # create/update category, course and section names
    python scripts/moodle/push.py upload                # upload generated XML/HTML files to your private files

Run scripts/moodle/build_moodle.py first. Standard library only.

Configuration (never commit it, never paste the token into chat or tickets):
    MOODLE_URL      base URL of the Moodle site, e.g. https://itlearning.tech (required, no default on purpose)
    MOODLE_TOKEN    web-services token for a dedicated service user
or the same keys in ~/.config/java-zero-hire/moodle.ini under [moodle] (url = ..., token = ...), chmod 600.

What core Moodle web services can and can't do: they create categories and courses and rename sections, and
can upload files. They can't create Page or Quiz activities or import question banks; do those in the Moodle
UI from the files in lms/moodle/out/ (see lms/moodle/BLUEPRINT.md). If the optional plugin
local_wsmanagesections is installed and enabled for the token's service, section summaries are set too.
"""
from __future__ import annotations

import argparse
import configparser
import json
import os
import stat
import sys
import urllib.error
import urllib.parse
import urllib.request
import uuid
from pathlib import Path

ROOT = Path(__file__).resolve().parents[2]
OUT = ROOT / "lms" / "moodle" / "out"
CONFIG = Path.home() / ".config" / "java-zero-hire" / "moodle.ini"

REQUIRED = [
    "core_webservice_get_site_info",
    "core_course_get_categories",
    "core_course_create_categories",
    "core_course_get_courses_by_field",
    "core_course_create_courses",
    "core_course_get_contents",
    "core_update_inplace_editable",
]
OPTIONAL = ["local_wsmanagesections_update_sections", "core_user_add_user_private_files"]


class MoodleError(RuntimeError):
    pass


def load_settings(need_token: bool) -> tuple[str, str | None]:
    url = os.environ.get("MOODLE_URL")
    token = os.environ.get("MOODLE_TOKEN")
    if CONFIG.exists():
        mode = CONFIG.stat().st_mode
        if mode & (stat.S_IRGRP | stat.S_IROTH):
            print(f"warning: {CONFIG} is readable by other users; run: chmod 600 {CONFIG}", file=sys.stderr)
        cp = configparser.ConfigParser()
        cp.read(CONFIG)
        url = url or cp.get("moodle", "url", fallback=None)
        token = token or cp.get("moodle", "token", fallback=None)
    if not url:
        raise SystemExit("Set MOODLE_URL (or url in ~/.config/java-zero-hire/moodle.ini).")
    if need_token and not token:
        raise SystemExit("Set MOODLE_TOKEN (or token in ~/.config/java-zero-hire/moodle.ini). "
                         "Create it in Moodle: Site administration > Server > Web services > Manage tokens.")
    return url.rstrip("/"), token


def flatten(value, prefix: str = "") -> list[tuple[str, str]]:
    """Moodle REST expects nested params as courses[0][fullname]=..."""
    if isinstance(value, dict):
        items = []
        for k, v in value.items():
            items += flatten(v, f"{prefix}[{k}]" if prefix else k)
        return items
    if isinstance(value, list):
        items = []
        for i, v in enumerate(value):
            items += flatten(v, f"{prefix}[{i}]")
        return items
    if isinstance(value, bool):
        return [(prefix, "1" if value else "0")]
    return [(prefix, str(value))]


class Moodle:
    def __init__(self, url: str, token: str):
        self.url = url
        self._token = token

    def call(self, function: str, **params):
        data = urllib.parse.urlencode(
            [("wstoken", self._token), ("wsfunction", function), ("moodlewsrestformat", "json")] + flatten(params)
        ).encode()
        req = urllib.request.Request(f"{self.url}/webservice/rest/server.php", data=data, method="POST")
        try:
            with urllib.request.urlopen(req, timeout=60) as r:
                body = json.loads(r.read().decode("utf-8") or "null")
        except urllib.error.URLError as e:
            raise MoodleError(f"{function}: cannot reach {self.url} ({getattr(e, 'reason', e)})") from None
        if isinstance(body, dict) and "exception" in body:
            raise MoodleError(f"{function}: {body.get('errorcode')}: {body.get('message')}")
        return body

    def upload(self, path: Path, itemid: int = 0) -> int:
        boundary = uuid.uuid4().hex
        payload = (f"--{boundary}\r\nContent-Disposition: form-data; name=\"file_1\"; filename=\"{path.name}\"\r\n"
                   f"Content-Type: application/octet-stream\r\n\r\n").encode() + path.read_bytes() + \
                  f"\r\n--{boundary}--\r\n".encode()
        query = urllib.parse.urlencode({"token": self._token, "filearea": "draft", "itemid": itemid})
        req = urllib.request.Request(f"{self.url}/webservice/upload.php?{query}", data=payload, method="POST",
                                     headers={"Content-Type": f"multipart/form-data; boundary={boundary}"})
        with urllib.request.urlopen(req, timeout=120) as r:
            result = json.loads(r.read().decode("utf-8"))
        if isinstance(result, dict) and "error" in result:
            raise MoodleError(f"upload {path.name}: {result['error']}")
        return int(result[0]["itemid"])


def structure() -> dict:
    f = OUT / "course-structure.json"
    if not f.exists():
        raise SystemExit("Run python scripts/moodle/build_moodle.py first.")
    return json.loads(f.read_text(encoding="utf-8"))


def cmd_plan(_args) -> int:
    s = structure()
    print(f"Category: {s['category']}")
    for phase in s["phases"]:
        c = phase["course"]
        print(f"\nCourse: {c['fullname']}  (shortname {c['shortname']}, format topics, {len(phase['sections'])} sections, hidden)")
        for i, sec in enumerate(phase["sections"], 1):
            print(f"  Section {i}: {sec['name']}")
            for a in sec["activities"]:
                print(f"      - {a['type']}: {a['name']}  [activity: create in the Moodle UI, see BLUEPRINT.md]")
    return 0


def cmd_check(_args) -> int:
    url, token = load_settings(need_token=True)
    m = Moodle(url, token)
    info = m.call("core_webservice_get_site_info")
    available = {f["name"] for f in info.get("functions", [])}
    print(f"Site:    {info.get('sitename')} ({url}), Moodle release {info.get('release')}")
    print(f"User:    {info.get('fullname')} (user id {info.get('userid')})")
    missing = [f for f in REQUIRED if f not in available]
    for f in REQUIRED:
        print(f"  {'ok     ' if f in available else 'MISSING'} {f}")
    for f in OPTIONAL:
        print(f"  {'ok     ' if f in available else 'absent '} {f} (optional)")
    if missing:
        print("\nAdd the missing functions to this token's external service "
              "(Site administration > Server > Web services > External services > Functions).")
        return 1
    return 0


def confirm(url: str, assume_yes: bool) -> None:
    if assume_yes:
        return
    host = urllib.parse.urlparse(url).hostname
    answer = input(f"This will create or change courses on {url}. Type the host name ({host}) to continue: ")
    if answer.strip() != host:
        raise SystemExit("Cancelled.")


def find_or_create_category(m: Moodle, name: str) -> int:
    for cat in m.call("core_course_get_categories", criteria=[{"key": "name", "value": name}]):
        if cat["name"] == name:
            return int(cat["id"])
    created = m.call("core_course_create_categories", categories=[{"name": name, "parent": 0,
                     "description": "Java Backend Engineer: Zero to Hired (ITLearning)"}])
    print(f"created category '{name}'")
    return int(created[0]["id"])


def find_or_create_course(m: Moodle, course: dict, category_id: int, n_sections: int) -> int:
    found = m.call("core_course_get_courses_by_field", field="shortname", value=course["shortname"])
    if found.get("courses"):
        print(f"course {course['shortname']} already exists; updating section names only")
        return int(found["courses"][0]["id"])
    created = m.call("core_course_create_courses", courses=[{
        "fullname": course["fullname"], "shortname": course["shortname"], "idnumber": course["idnumber"],
        "categoryid": category_id, "format": "topics", "visible": 0, "enablecompletion": 1,
        "courseformatoptions": [{"name": "numsections", "value": n_sections}],
        "summary": "Self-paced, project-based Java backend training built on financial-institution use cases. "
                   "Content and code: https://github.com/sundayayandele/java-learn-from-zero-2hire",
        "summaryformat": 1}])
    print(f"created course {course['shortname']} (hidden until reviewed)")
    return int(created[0]["id"])


def cmd_apply(args) -> int:
    url, token = load_settings(need_token=True)
    m = Moodle(url, token)
    confirm(url, args.yes)
    s = structure()
    available = {f["name"] for f in m.call("core_webservice_get_site_info").get("functions", [])}
    category_id = find_or_create_category(m, s["category"])
    for phase in s["phases"]:
        sections = phase["sections"]
        course_id = find_or_create_course(m, phase["course"], category_id, len(sections))
        contents = [c for c in m.call("core_course_get_contents", courseid=course_id) if int(c["section"]) > 0]
        if len(contents) < len(sections):
            print(f"warning: course has {len(contents)} sections, {len(sections)} needed; add sections in the UI and re-run")
        for sec, existing in zip(sections, contents):
            if existing["name"] != sec["name"]:
                m.call("core_update_inplace_editable", component="format_topics", itemtype="sectionname",
                       itemid=existing["id"], value=sec["name"])
        if "local_wsmanagesections_update_sections" in available:
            m.call("local_wsmanagesections_update_sections", courseid=course_id, sections=[
                {"type": "id", "section": existing["id"], "summary": sec["summary"], "summaryformat": 1}
                for sec, existing in zip(sections, contents)])
        print(f"course {phase['course']['shortname']}: {min(len(sections), len(contents))} sections named"
              f" -> {url}/course/view.php?id={course_id}")
    print("\nNext: create the Page and Quiz activities and import the question banks (lms/moodle/BLUEPRINT.md).")
    return 0


def cmd_upload(args) -> int:
    url, token = load_settings(need_token=True)
    m = Moodle(url, token)
    confirm(url, args.yes)
    files = sorted((OUT / "questions").glob("*.xml")) + sorted((OUT / "pages").glob("*.html"))
    if not files:
        raise SystemExit("Nothing to upload; run build_moodle.py first.")
    itemid = 0
    for f in files:
        itemid = m.upload(f, itemid)
    m.call("core_user_add_user_private_files", draftid=itemid)
    print(f"uploaded {len(files)} files to your Moodle private files (usable from the file picker when importing)")
    return 0


def main(argv: list[str]) -> int:
    parser = argparse.ArgumentParser(description=__doc__, formatter_class=argparse.RawDescriptionHelpFormatter)
    sub = parser.add_subparsers(dest="command", required=True)
    sub.add_parser("check")
    sub.add_parser("plan")
    for name in ("apply", "upload"):
        p = sub.add_parser(name)
        p.add_argument("--yes", action="store_true", help="skip the interactive host-name confirmation")
    args = parser.parse_args(argv)
    try:
        return {"check": cmd_check, "plan": cmd_plan, "apply": cmd_apply, "upload": cmd_upload}[args.command](args)
    except MoodleError as e:
        print(f"Moodle error: {e}", file=sys.stderr)
        return 1


if __name__ == "__main__":
    sys.exit(main(sys.argv[1:]))
