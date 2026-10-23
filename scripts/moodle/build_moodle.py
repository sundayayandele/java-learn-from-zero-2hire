#!/usr/bin/env python3
"""Build Moodle import files for every built phase.

    python scripts/moodle/build_moodle.py

Writes to lms/moodle/out/ (git-ignored):
    questions/topic-<id>.xml          Moodle XML question bank, one category per topic (graded topic quizzes)
    questions/phase-<n>-exam.xml      end-of-phase exam bank (only if assessments/private/phase-<n>-exam.json exists)
    pages/<id>.html                   lesson as HTML with embedded videos, ready for a Moodle Page activity
    course-structure.json             sections and activities, used by push.py

Requires pandoc (https://pandoc.org) to convert lessons to HTML. Standard library otherwise.
"""
from __future__ import annotations

import html
import json
import re
import shutil
import subprocess
import sys
from pathlib import Path

ROOT = Path(__file__).resolve().parents[2]
OUT = ROOT / "lms" / "moodle" / "out"
REPO_URL = "https://github.com/sundayayandele/java-zero-hire/blob/main"
RAW_URL = "https://raw.githubusercontent.com/sundayayandele/java-zero-hire/main"
COURSE_CATEGORY = "Java Zero-to-Hired"

# Moodle only accepts these grade fractions for multichoice answers.
FRACTIONS = {1: "100", 2: "50", 3: "33.33333", 4: "25", 5: "20"}


def load(path: Path):
    return json.loads(path.read_text(encoding="utf-8"))


def inline_md(text: str) -> str:
    """Escape HTML, then render `code` and **bold** (the only inline markdown used in questions)."""
    out = html.escape(text, quote=False)
    out = re.sub(r"`([^`]+)`", r"<code>\1</code>", out)
    return re.sub(r"\*\*([^*]+)\*\*", r"<strong>\1</strong>", out)


def cdata(s: str) -> str:
    return "<![CDATA[" + s.replace("]]>", "]]]]><![CDATA[>") + "]]>"


def question_xml(name: str, q: dict) -> str:
    answers = q["answer"] if isinstance(q["answer"], list) else [q["answer"]]
    single = q["type"] != "multi"
    n_right = len(answers)
    n_wrong = len(q["options"]) - n_right
    parts = [
        '  <question type="multichoice">',
        f"    <name><text>{html.escape(name)}</text></name>",
        f'    <questiontext format="html"><text>{cdata("<p>" + inline_md(q["scenario"]) + "</p><p><strong>" + inline_md(q["question"]) + "</strong></p>")}</text></questiontext>',
        f'    <generalfeedback format="html"><text>{cdata("<p>" + inline_md(q["explanation"]) + "</p>")}</text></generalfeedback>',
        "    <defaultgrade>1</defaultgrade>",
        "    <penalty>0.3333333</penalty>",
        "    <hidden>0</hidden>",
        f"    <single>{'true' if single else 'false'}</single>",
        "    <shuffleanswers>true</shuffleanswers>",
        "    <answernumbering>ABCD</answernumbering>",
    ]
    for i, option in enumerate(q["options"]):
        if i in answers:
            fraction = "100" if single else FRACTIONS[n_right]
        else:
            fraction = "0" if single else "-" + FRACTIONS[max(n_wrong, 1)]
        parts.append(f'    <answer fraction="{fraction}" format="html"><text>{cdata(inline_md(option))}</text>'
                     '<feedback format="html"><text></text></feedback></answer>')
    parts.append("  </question>")
    return "\n".join(parts)


def bank_xml(category: str, prefix: str, questions: list[dict]) -> str:
    body = [
        '<?xml version="1.0" encoding="UTF-8"?>',
        "<quiz>",
        '  <question type="category">',
        f"    <category><text>$course$/top/{html.escape(category)}</text></category>",
        "  </question>",
    ]
    body += [question_xml(f"{prefix}-Q{i:02d}", q) for i, q in enumerate(questions, 1)]
    body.append("</quiz>")
    return "\n".join(body) + "\n"


def lesson_html(md: str) -> str:
    result = subprocess.run(["pandoc", "--from", "gfm", "--to", "html5", "--no-highlight"],
                            input=md, capture_output=True, text=True, check=True)
    return result.stdout


def absolutise(page: str, src_dir: Path) -> str:
    """Point relative links at GitHub and relative images at raw files, so they work inside Moodle."""
    def fix(m: re.Match) -> str:
        attr, target = m.group(1), m.group(2)
        if re.match(r"^([a-z]+:|#|//)", target):
            return m.group(0)
        path, _, anchor = target.partition("#")
        rel = (src_dir / path).resolve().relative_to(ROOT).as_posix()
        base = RAW_URL if attr == "src" else REPO_URL
        return f'{attr}="{base}/{rel}' + (f"#{anchor}" if anchor else "") + '"'
    return re.sub(r'(href|src)="([^"]+)"', fix, page)


def capstone_html(cdir: Path) -> str:
    brief = absolutise(lesson_html((cdir / "README.md").read_text(encoding="utf-8")), cdir)
    brief = re.sub(r'(src="[^"]*/architecture/[^"]*)\.svg"', r'\1.png" style="max-width:100%"', brief)
    guide = absolutise(lesson_html((cdir / "GUIDE.md").read_text(encoding="utf-8")), cdir)
    guide = re.sub(r"<p>←.*?</p>", "", guide, count=1, flags=re.S)
    return (brief + '\n<hr>\n' + guide +
            f'\n<p>Source on GitHub: <a href="{REPO_URL}/{cdir.relative_to(ROOT).as_posix()}/README.md">brief</a> · '
            f'<a href="{REPO_URL}/{cdir.relative_to(ROOT).as_posix()}/GUIDE.md">build guide</a> · '
            f'<a href="{REPO_URL}/{cdir.relative_to(ROOT).as_posix()}/starter/README.md">starter</a></p>')


def videos_html(videos: list[dict]) -> str:
    if not videos:
        return ""
    blocks = ["<h2>Watch</h2>"]
    for v in videos:
        title = html.escape(v["title"])
        optional = " (optional, longer)" if v.get("optional") else ""
        blocks.append(
            f'<h3>{title}{optional}</h3><p>{html.escape(v["channel"])} · {round(v["minutes"])} min. {html.escape(v["why"])}</p>'
            f'<div style="position:relative;padding-bottom:56.25%;height:0;overflow:hidden;max-width:960px">'
            f'<iframe src="https://www.youtube-nocookie.com/embed/{v["id"]}" title="{title}" '
            f'style="position:absolute;top:0;left:0;width:100%;height:100%" frameborder="0" '
            f'allow="accelerometer; encrypted-media; gyroscope; picture-in-picture" allowfullscreen></iframe></div>')
    return "\n".join(blocks)


def combine_topic_banks() -> None:
    """One file with every topic category, so all topic quizzes import in a single step. Public: these answers
    are already in the topic READMEs. Written to lms/moodle/import/ (committed) for Moodle's URL downloader."""
    blocks = []
    for f in sorted((OUT / "questions").glob("topic-*.xml"), key=lambda p: [int(x) for x in p.stem[6:].split(".")]):
        text = f.read_text(encoding="utf-8")
        blocks.append(text.split("<quiz>", 1)[1].rsplit("</quiz>", 1)[0].strip("\n"))
    target = ROOT / "lms" / "moodle" / "import" / "phase-1-topic-quizzes.xml"
    target.parent.mkdir(parents=True, exist_ok=True)
    target.write_text('<?xml version="1.0" encoding="UTF-8"?>\n<quiz>\n' + "\n".join(blocks) + "\n</quiz>\n",
                      encoding="utf-8")


def main() -> int:
    if not shutil.which("pandoc"):
        print("pandoc is required: https://pandoc.org/installing.html", file=sys.stderr)
        return 1
    cur = load(ROOT / "curriculum.json")
    videos = load(ROOT / "lms" / "videos.json")["topics"]
    if OUT.exists():
        shutil.rmtree(OUT)
    (OUT / "questions").mkdir(parents=True)
    (OUT / "pages").mkdir(parents=True)
    (OUT / "capstones").mkdir(parents=True)

    structure = {"category": COURSE_CATEGORY, "phases": []}
    for phase in cur["phases"]:
        if phase["status"] != "built":
            continue
        pdir = ROOT / "phases" / phase["slug"]
        sections = [{
            "name": f"Phase {phase['number']}: start here",
            "summary": f"How this phase works, the weekly plan and the exit criteria. Full content: {REPO_URL}/phases/{phase['slug']}/README.md",
            "activities": [{"type": "url", "name": f"Phase {phase['number']} plan and resources",
                            "url": f"{REPO_URL}/phases/{phase['slug']}/README.md"}],
        }]
        for mod in phase["modules"]:
            activities = []
            for t in mod["topics"]:
                tdir = pdir / mod["slug"] / t["slug"]
                body = absolutise(lesson_html((tdir / "lesson.md").read_text(encoding="utf-8")), tdir)
                page = (f"<p><em>Week {t['week']} · Module {mod['number']}: {html.escape(mod['title'])}</em></p>\n"
                        f"{body}\n{videos_html(videos.get(t['id'], []))}\n"
                        f'<p>Source on GitHub: <a href="{REPO_URL}/phases/{phase["slug"]}/{mod["slug"]}/{t["slug"]}/README.md">'
                        f"{html.escape(t['id'] + ' ' + t['title'])}</a></p>")
                (OUT / "pages" / f"{t['id']}.html").write_text(page, encoding="utf-8")
                cbt = load(tdir / "cbt.json")
                category = f"{COURSE_CATEGORY}/Phase {phase['number']}/{t['id']} {t['title'].split(':')[0][:60]}"
                (OUT / "questions" / f"topic-{t['id']}.xml").write_text(
                    bank_xml(category, t["id"], cbt["questions"]), encoding="utf-8")
                activities += [
                    {"type": "page", "name": f"{t['id']} {t['title']}", "file": f"pages/{t['id']}.html"},
                    {"type": "quiz", "name": f"{t['id']} Check your understanding",
                     "questions": f"questions/topic-{t['id']}.xml", "count": len(cbt["questions"]),
                     "settings": {"grade_to_pass_percent": 70, "attempts": "unlimited", "time_limit": None}},
                ]
            activities.append({"type": "url", "name": f"Module {mod['number']} projects",
                               "url": f"{REPO_URL}/phases/{phase['slug']}/{mod['slug']}/README.md#module-projects"})
            sections.append({"name": f"Module {mod['number']}: {mod['title']} (weeks {mod['weeks']})",
                             "summary": f"Topics, videos and graded checks for Module {mod['number']}.",
                             "activities": activities})
        for cdir in sorted(p for p in (pdir / "capstones").iterdir() if (p / "GUIDE.md").exists()):
            (OUT / "capstones" / f"{cdir.name}.html").write_text(capstone_html(cdir), encoding="utf-8")
        sections.append({"name": f"Phase {phase['number']} capstones",
                         "summary": "Ten financial-institution capstones; complete at least three, including Capstone 1.",
                         "activities": [{"type": "url", "name": "Capstone briefs",
                                         "url": f"{REPO_URL}/phases/{phase['slug']}/capstones/README.md"},
                                        {"type": "url", "name": "Deployment and CI/CD guide",
                                         "url": f"{REPO_URL}/phases/{phase['slug']}/deploy/README.md"},
                                        {"type": "assign", "name": "Capstone submissions (one per capstone)",
                                         "settings": {"grade": 20, "pass": 14, "rubric": "capstones README rubric"}}]})
        exam_src = ROOT / "assessments" / "private" / f"phase-{phase['number']}-exam.json"
        exam_activity = {"type": "url", "name": "Exam information",
                         "url": f"{REPO_URL}/phases/{phase['slug']}/exam/README.md"}
        exam_acts = [exam_activity]
        if exam_src.exists():
            exam = load(exam_src)
            (OUT / "questions" / f"phase-{phase['number']}-exam.xml").write_text(
                bank_xml(f"{COURSE_CATEGORY}/Phase {phase['number']}/End-of-phase exam", f"P{phase['number']}-EXAM",
                         exam["questions"]), encoding="utf-8")
            exam_acts.append({"type": "quiz", "name": f"Phase {phase['number']} end-of-phase exam",
                              "questions": f"questions/phase-{phase['number']}-exam.xml",
                              "count": len(exam["questions"]),
                              "settings": {"time_limit_minutes": exam["time_limit_minutes"],
                                           "grade_to_pass_percent": exam["pass_percent"], "attempts": 2,
                                           "shuffle_questions": True}})
        else:
            print(f"note: {exam_src.relative_to(ROOT)} not found; exam bank not built", file=sys.stderr)
        sections.append({"name": f"Phase {phase['number']} end-of-phase exam", "summary": "2 hours, pass mark 70%.",
                         "activities": exam_acts})
        sections.append({"name": f"Phase {phase['number']}: getting hired",
                         "summary": "Portfolio, CV, interview preparation and applications.",
                         "activities": [{"type": "url", "name": "Getting hired guide",
                                         "url": f"{REPO_URL}/phases/{phase['slug']}/getting-hired/README.md"}]})
        structure["phases"].append({"number": phase["number"], "title": phase["title"],
                                    "course": {"fullname": f"Java Backend Engineer: Phase {phase['number']} {phase['title']}",
                                               "shortname": f"JAVA-Z2H-P{phase['number']}",
                                               "idnumber": f"java-z2h-2026-p{phase['number']}"},
                                    "sections": sections})

    (OUT / "course-structure.json").write_text(json.dumps(structure, indent=2) + "\n", encoding="utf-8")
    combine_topic_banks()
    n_q = len(list((OUT / "questions").glob("*.xml")))
    n_p = len(list((OUT / "pages").glob("*.html")))
    print(f"moodle: {n_p} pages, {n_q} question banks, course-structure.json in {OUT.relative_to(ROOT)}")
    return 0


if __name__ == "__main__":
    sys.exit(main())
