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


def exam_xml(phase: int, questions: list[dict]) -> str:
    """The exam bank with one sub-category per module, so the quiz can draw a fixed number of random questions
    from each module (the blueprint weights) for every attempt."""
    body = ['<?xml version="1.0" encoding="UTF-8"?>', "<quiz>"]
    modules = sorted({q["topic"].split(".")[0] for q in questions}, key=int)
    for m in modules:
        category = f"{COURSE_CATEGORY}/Phase {phase}/End-of-phase exam/Module {m}"
        body += ['  <question type="category">', f"    <category><text>$course$/top/{html.escape(category)}</text></category>",
                 "  </question>"]
        group = [q for q in questions if q["topic"].split(".")[0] == m]
        body += [question_xml(f"P{phase}-EXAM-M{m}-Q{i:02d}", q) for i, q in enumerate(group, 1)]
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


def combine_topic_banks(phase_number: int, topic_ids: list[str]) -> None:
    """One file per phase with every topic category, so all topic quizzes import in a single step. Public: these
    answers are already in the topic READMEs. Written to lms/moodle/import/ (committed) for Moodle's URL downloader."""
    blocks = []
    files = [OUT / "questions" / f"topic-{tid}.xml" for tid in topic_ids]
    for f in sorted(files, key=lambda p: [int(x) for x in p.stem[6:].split(".")]):
        text = f.read_text(encoding="utf-8")
        blocks.append(text.split("<quiz>", 1)[1].rsplit("</quiz>", 1)[0].strip("\n"))
    target = ROOT / "lms" / "moodle" / "import" / f"phase-{phase_number}-topic-quizzes.xml"
    target.parent.mkdir(parents=True, exist_ok=True)
    target.write_text('<?xml version="1.0" encoding="UTF-8"?>\n<quiz>\n' + "\n".join(blocks) + "\n</quiz>\n",
                      encoding="utf-8")


# Per phase: number of topic quizzes, week capstones start, capstone rubric total and pass mark.
PHASE_FACTS = {1: (32, 5, 20, 14), 2: (27, 15, 24, 17)}
# Per phase: how many capstones besides Capstone 1 are required.
OTHER_CAPSTONES = {1: "two others", 2: "one other"}

START_HERE = """<h2>How this course works on ITLearning</h2>
<ol>
<li><strong>Work through the modules in order.</strong> Each topic has a <em>Lesson</em> page (read it and type the code yourself, then watch the embedded videos) followed by a <em>Check your understanding</em> quiz.</li>
<li><strong>Pass every topic quiz at 70%.</strong> Attempts are unlimited and you see feedback straight away, so use the explanations to learn. A topic counts as complete when you've viewed the lesson and passed its quiz.</li>
<li><strong>Do the practice tasks and module projects</strong> listed at the end of each lesson and module.</li>
<li><strong>Build your capstones from week {week}</strong>: Capstone 1 plus {others}, then defend them in a 20-minute <em>capstone defence</em> (demo, code questions and a live change). Each capstone page has the brief, the architecture diagrams and a step-by-step build guide. Submit each one in its <em>Submission</em> assignment; it is marked out of {total} with the rubric, and {passmark} is a pass.</li>
<li><strong>Keep up the coding practice track</strong> (about 3 hours a week): auto-graded Java and SQL interview problems. Submit your progress at the <em>coding practice checkpoint</em>.</li>
<li><strong>Sit the end-of-phase assessments</strong>: the exam once you've passed all {quizzes} topic quizzes (60 questions drawn at random, 2 hours, pass mark 70%, two attempts at least 48 hours apart) and the 90-minute <em>live-coding assessment</em>.</li>
<li><strong>Get hired</strong>: work through the Getting hired section and submit your checklist.</li>
</ol>
<p>Stuck? Ask in the <em>Questions and help</em> forum in this section. Say which topic or capstone, what you tried, and paste the error message.</p>
<hr>
"""


def publish_pages() -> None:
    """Copy lesson and capstone pages to lms/moodle/import/ (committed, public: the same content is in the
    Markdown), so the LMS can load them from raw.githubusercontent.com."""
    for kind in ("pages", "capstones", "guides"):
        target = ROOT / "lms" / "moodle" / "import" / kind
        if target.exists():
            shutil.rmtree(target)
        shutil.copytree(OUT / kind, target)


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
    (OUT / "guides").mkdir(parents=True)

    structure = {"category": COURSE_CATEGORY, "phases": []}
    for phase in cur["phases"]:
        if phase["status"] != "built":
            continue
        pdir = ROOT / "phases" / phase["slug"]
        n = phase["number"]
        sub = "" if n == 1 else f"phase-{n}/"          # Phase 1 keeps the original flat paths already used by Moodle
        vprefix = "" if n == 1 else f"p{n}-"
        quizzes, cap_week, cap_total, cap_pass = PHASE_FACTS[n]
        (OUT / "capstones" / sub).mkdir(parents=True, exist_ok=True)
        (OUT / "guides" / sub).mkdir(parents=True, exist_ok=True)
        topic_ids = [t["id"] for mod in phase["modules"] for t in mod["topics"]]
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
                category = f"{COURSE_CATEGORY}/Phase {phase['number']}/{t['id']} {t['title'].split(':')[0][:60].replace('/', '-')}"
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
        guides = {"start-here": (pdir / "phase.md", None),
                  "getting-hired": (pdir / "getting-hired" / "guide.md", vprefix + "getting-hired"),
                  "deploy": (pdir / "deploy" / "guide.md", vprefix + "deploy"),
                  "cicd": (pdir / "cicd" / "guide.md", vprefix + "cicd"),
                  "exam": (pdir / "exam" / "README.md", None)}
        for name, (src, video_key) in guides.items():
            md = src.read_text(encoding="utf-8") if name != "start-here" else (pdir / "README.md").read_text(encoding="utf-8")
            body = absolutise(lesson_html(md), src.parent if name != "start-here" else pdir)
            body = re.sub(r"<p>←.*?</p>", "", body, count=1, flags=re.S)
            if name == "start-here":
                body = START_HERE.format(week=cap_week, total=cap_total, passmark=cap_pass, quizzes=quizzes,
                                         others=OTHER_CAPSTONES[n]) \
                    + re.sub(r"<!--.*?-->", "", body, flags=re.S)
            (OUT / "guides" / f"{sub}{name}.html").write_text(body + "\n" + videos_html(videos.get(video_key, []) if video_key else []),
                                                        encoding="utf-8")
        overview = pdir / "capstones"
        (OUT / "capstones" / f"{sub}00-overview.html").write_text(
            absolutise(lesson_html((overview / "README.md").read_text(encoding="utf-8")), overview), encoding="utf-8")
        for cdir in sorted(p for p in (pdir / "capstones").iterdir() if (p / "GUIDE.md").exists()):
            (OUT / "capstones" / f"{sub}{cdir.name}.html").write_text(capstone_html(cdir), encoding="utf-8")
        sections.append({"name": f"Phase {phase['number']} capstones",
                         "summary": "Ten financial-institution capstones; complete at least three, including Capstone 1.",
                         "activities": [{"type": "url", "name": "Capstone briefs",
                                         "url": f"{REPO_URL}/phases/{phase['slug']}/capstones/README.md"},
                                        {"type": "url", "name": "Deployment and CI/CD guide",
                                         "url": f"{REPO_URL}/phases/{phase['slug']}/deploy/README.md"},
                                        {"type": "assign", "name": "Capstone submissions (one per capstone)",
                                         "settings": {"grade": cap_total, "pass": cap_pass, "rubric": "capstones README rubric"}}]})
        exam_src = ROOT / "assessments" / "private" / f"phase-{phase['number']}-exam.json"
        exam_activity = {"type": "url", "name": "Exam information",
                         "url": f"{REPO_URL}/phases/{phase['slug']}/exam/README.md"}
        exam_acts = [exam_activity]
        if exam_src.exists():
            exam = load(exam_src)
            (OUT / "questions" / f"phase-{phase['number']}-exam.xml").write_text(
                exam_xml(phase["number"], exam["questions"]), encoding="utf-8")
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
        combine_topic_banks(n, topic_ids)

    (OUT / "course-structure.json").write_text(json.dumps(structure, indent=2) + "\n", encoding="utf-8")
    publish_pages()
    n_q = len(list((OUT / "questions").glob("*.xml")))
    n_p = len(list((OUT / "pages").glob("*.html")))
    print(f"moodle: {n_p} pages, {n_q} question banks, course-structure.json in {OUT.relative_to(ROOT)}")
    return 0


if __name__ == "__main__":
    sys.exit(main())
