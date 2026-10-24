#!/usr/bin/env python3
"""Build the CodeRunner coding-practice question banks (Moodle XML) from practice/dsa.

    python3 scripts/moodle/coderunner/build_coderunner.py           # build and verify
    python3 scripts/moodle/coderunner/build_coderunner.py --check   # fail if the committed XML is stale

For each problem in problems.py it:
  * takes the learner's starting code from practice/dsa/src/main/java (the generated stub) and the sample
    answer from practice/dsa/solutions;
  * renders the same Java tester that the CodeRunner template produces, runs it with the reference solution
    in Docker (eclipse-temurin:21-jdk) and stores the output of each test as its expected output;
  * runs it again with the stub and fails if any test passes, so a question can never be passed with the
    starting code.

Output: lms/moodle/import/coderunner/phase-<n>-coding-practice.xml (category
"Java Zero-to-Hired/Phase <n>/Coding practice"). Needs Docker.
"""
from __future__ import annotations

import html
import re
import shutil
import subprocess
import sys
from pathlib import Path

sys.path.insert(0, str(Path(__file__).resolve().parent))
from problems import PROBLEMS, SUPPORT  # noqa: E402

ROOT = Path(__file__).resolve().parents[3]
DSA = ROOT / "practice" / "dsa"
PKG = "ng/itlearning/practice"
OUT = ROOT / "lms" / "moodle" / "import" / "coderunner"
BUILD = ROOT / "build" / "coderunner"
REPO_URL = "https://github.com/sundayayandele/java-zero-hire/blob/main"
COURSE_CATEGORY = "Java Zero-to-Hired"
JDK_IMAGE = "eclipse-temurin:21-jdk"

SEPARATOR = "#<ab@17943918#@>#"
SPLITTER_RE = "|#<ab@17943918#@>#\\n|ms"
# CodeRunner names the Java file after the first top-level public class it finds, by counting braces in
# the program with comments and strings removed. Character literals such as '{' are not removed, so an
# answer that uses them (BalancedBrackets) throws the count off. So no class in the program is public: then
# CodeRunner falls back to the file name NO_PUBLIC_CLASS_FOUND.java, and the tester class has that name.
TESTER = "NO_PUBLIC_CLASS_FOUND"
PUBLIC_TYPES = {"public final class ": "final class ", "public abstract class ": "abstract class ",
                "public class ": "class ", "public record ": "record ", "public interface ": "interface ",
                "public enum ": "enum "}
IMPORTS = "\n".join(f"import {p}.*;" for p in
                    ["java.math", "java.time", "java.util", "java.util.concurrent", "java.util.function",
                     "java.util.stream"])
# show() prints arrays readably. within() gives a large-input test a wall-clock budget: a too-slow answer
# prints "Too slow" for that test instead of timing out the whole run, which would make CodeRunner rerun
# every test separately (about a minute on our Jobe server).
SHOW = """    static void show(Object o) {
        if (o instanceof int[] a) {
            System.out.println(Arrays.toString(a));
        } else if (o instanceof long[] a) {
            System.out.println(Arrays.toString(a));
        } else if (o instanceof double[] a) {
            System.out.println(Arrays.toString(a));
        } else {
            System.out.println(o);
        }
    }

    static void within(long millis, Supplier<Object> test) {
        CompletableFuture<Object> result = new CompletableFuture<>();
        Thread worker = new Thread(() -> {
            try {
                result.complete(test.get());
            } catch (Throwable e) {
                result.completeExceptionally(e);
            }
        });
        worker.setDaemon(true);
        worker.start();
        try {
            show(result.get(millis, TimeUnit.MILLISECONDS));
        } catch (TimeoutException e) {
            System.out.println("Too slow: no answer within " + millis / 1000 + " s");
        } catch (ExecutionException e) {
            System.out.println(e.getCause().getClass().getSimpleName());
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }"""
CATCH = """        } catch (RuntimeException | StackOverflowError e) {
            System.out.println(e.getClass().getSimpleName());
        }"""


def without_package(source: str) -> str:
    """Drop the package line and the generator banner; Jobe compiles everything in one file."""
    lines = [ln for ln in source.splitlines()
             if not ln.startswith("package ") and not ln.startswith("// Generated from practice/dsa")]
    return "\n".join(lines).strip() + "\n"


def de_public(source: str) -> str:
    for old, new in PUBLIC_TYPES.items():
        source = source.replace(old, new)
    return source


def support_code(path: str) -> str:
    folder = path.split("/")[0]
    return "\n".join(de_public(without_package((DSA / "solutions" / PKG / f"{p}.java").read_text()))
                     for p in SUPPORT.get(folder, []))


def twig_template(support: str) -> str:
    """The question's CodeRunner template: one compile and one run for all tests (a combinator template)."""
    replace = ", ".join(f"'{k}': '{v}'" for k, v in PUBLIC_TYPES.items())
    return f"""{IMPORTS}

{{{{ STUDENT_ANSWER | replace({{{replace}}}) }}}}

{support}

final class {TESTER} {{
{SHOW}

    public static void main(String[] args) {{
{{% for TEST in TESTCASES %}}
        try {{
{{{{ TEST.testcode }}}}
{CATCH}
{{% if not loop.last %}}
        System.out.println("{SEPARATOR}");
{{% endif %}}
{{% endfor %}}
    }}
}}
"""


def tester_java(answer: str, support: str, tests: list[str]) -> str:
    """What the Twig template renders to, built in Python for the local runs."""
    blocks = []
    for i, code in enumerate(tests):
        blocks.append(f"        try {{\n{code}\n{CATCH}")
        if i < len(tests) - 1:
            blocks.append(f'        System.out.println("{SEPARATOR}");')
    body = "\n".join(blocks)
    return (f"{IMPORTS}\n\n{de_public(answer)}\n\n{support}\n\nfinal class {TESTER} {{\n{SHOW}\n\n"
            f"    public static void main(String[] args) {{\n{body}\n    }}\n}}\n")


def coderunner_main_class(prog: str) -> str | None:
    """A port of qtype_coderunner jobesandbox::get_main_class (CodeRunner 5.10.6): the class name CodeRunner
    will name the Java file after, or None (then it uses NO_PUBLIC_CLASS_FOUND.java)."""
    prog += " "
    kept, i, n = [], 0, len(prog) - 1
    while i < n:
        two = prog[i:i + 2]
        if two == "//":
            j = prog.find("\n", i + 2)
            if j == -1:
                break
            i = j
        elif two == "/*":
            j = prog.find("*/", i + 2)
            if j == -1:
                break
            kept.append(" ")
            i = j + 2
        elif prog[i] == '"':
            m = re.compile(r'"((\\.)|[^\\"])*"').match(prog, i)
            if not m:
                break
            i += len(m.group(0))
        else:
            kept.append(prog[i])
            i += 1
    depth = 0
    for k, ch in enumerate(kept):
        if ch == "{":
            depth += 1
        if ch == "}":
            depth -= 1
        if ch != "\n" and depth > 0 and not (depth == 1 and ch == "{"):
            kept[k] = " "
    m = re.search(r"public\s(\w*\s)*class\s*(\w+)[^\w]", "".join(kept))
    return m.group(2) if m else None


def javadoc(stub: str) -> tuple[str, str]:
    """The class Javadoc as HTML, and its first line (for example "Week 2. Pattern: hash map lookup.")."""
    m = re.search(r"/\*\*(.*?)\*/\s*public", stub, re.S)
    text = "\n".join(re.sub(r"^\s*\* ?", "", ln) for ln in m.group(1).strip().splitlines())
    first, _, rest = text.partition("\n")
    rest = html.escape(rest.replace("<p>", "\n\n"), quote=False)
    rest = re.sub(r"\{@code ([^}]*)\}", r"<code>\1</code>", rest)
    rest = re.sub(r"\{@link ([^}]*)\}", r"<code>\1</code>", rest)
    paras = [" ".join(p.split()) for p in rest.split("\n\n") if p.strip()]
    return "".join(f"<p>{p}</p>" for p in paras), first.strip()


def signatures(stub: str) -> list[str]:
    sigs = []
    for ln in stub.splitlines():
        s = ln.strip()
        if s.startswith("public ") and "(" in s and s.endswith("{") and " class " not in s:
            sigs.append(s[:-1].strip())
        elif re.match(r"public (final )?(class|record) ", s) and "(" in s:
            sigs.append(s[:-1].strip())
    return sigs


def cdata(s: str) -> str:
    return "<![CDATA[" + s.replace("]]>", "]]]]><![CDATA[>") + "]]>"


def run_all(jobs: dict[str, str]) -> dict[str, str]:
    """Compile and run each tester in one container. Returns stdout per job name."""
    if BUILD.exists():
        shutil.rmtree(BUILD)
    for name, java in jobs.items():
        found = coderunner_main_class(java)
        if found is not None:
            raise SystemExit(f"{name}: CodeRunner would name the file {found}.java; no class may be public")
        d = BUILD / name
        d.mkdir(parents=True)
        (d / f"{TESTER}.java").write_text(java)
    script = (f"for d in /b/*/; do cd $d && javac -nowarn -d . {TESTER}.java 2> compile.txt "
              f"&& timeout 60 java -cp . {TESTER} > out.txt 2> err.txt; done")
    subprocess.run(["docker", "run", "--rm", "-v", f"{BUILD}:/b", JDK_IMAGE, "sh", "-c", script], check=True)
    results = {}
    for name in jobs:
        d = BUILD / name
        compile_errors = (d / "compile.txt").read_text()
        if compile_errors.strip():
            raise SystemExit(f"{name}: compile error\n{compile_errors}")
        results[name] = (d / "out.txt").read_text()
        err = (d / "err.txt").read_text() if (d / "err.txt").exists() else ""
        if err.strip():
            raise SystemExit(f"{name}: stderr\n{err}")
    return results


def split_outputs(out: str, n: int, name: str) -> list[str]:
    parts = out.split(SEPARATOR + "\n")
    if len(parts) != n:
        raise SystemExit(f"{name}: expected {n} test outputs, got {len(parts)}")
    return [p.rstrip("\n") for p in parts]


def question_xml(phase: int, number: int, path: str, tests: list[tuple[str, bool]], expected: list[str]) -> str:
    cls = path.split("/")[1]
    stub = (DSA / "src/main/java" / PKG / f"{path}.java").read_text()
    reference = (DSA / "solutions" / PKG / f"{path}.java").read_text()
    statement, week_line = javadoc(stub)
    sigs = "".join(f"<li><code>{html.escape(s)}</code></li>" for s in signatures(stub))
    support = SUPPORT.get(path.split("/")[0], [])
    support_note = ("<p>The <code>" + "</code>, <code>".join(p.split("/")[1] for p in support) +
                    "</code> class is provided; don't include it in your answer.</p>") if support else ""
    text = (f"<p><strong>{html.escape(week_line)}</strong></p>{statement}"
            f"<p>Complete the class <code>{cls}</code> in the answer box, keeping these signatures:</p>"
            f"<ul>{sigs}</ul>{support_note}"
            "<p>Use only the Java standard library. Click <em>Check</em> to run the tests; you can check as often "
            "as you like with no penalty. Say your approach and its time complexity out loud before you code.</p>")
    feedback = (f"<p>Pattern: {html.escape(week_line.split('Pattern:')[-1].strip().rstrip('.'))}. "
                f"Compare your answer with the reference solution in "
                f"<a href=\"{REPO_URL}/practice/dsa/solutions/{PKG}/{path}.java\">practice/dsa/solutions</a>, "
                "then write one line in your NOTES.md: the pattern, the complexity, and the mistake you made.</p>")
    cases = []
    for (code, example), exp in zip(tests, expected):
        cases.append(
            f'      <testcase testtype="0" useasexample="{1 if example else 0}" hiderestiffail="0" mark="1.0000000">\n'
            f"        <testcode><text>{cdata(code)}</text></testcode>\n"
            f"        <stdin><text></text></stdin>\n"
            f"        <expected><text>{cdata(exp)}</text></expected>\n"
            f"        <extra><text></text></extra>\n"
            f"        <display><text>SHOW</text></display>\n"
            f"      </testcase>")
    fields = {
        "coderunnertype": "java_class", "prototypetype": "0", "allornothing": "1", "penaltyregime": "0",
        "precheck": "0", "hidecheck": "0", "showsource": "0", "answerboxlines": "30", "answerboxcolumns": "100",
        "answerpreload": cdata(without_package(stub)), "globalextra": "", "useace": "1", "resultcolumns": "",
        "template": cdata(twig_template(support_code(path))), "iscombinatortemplate": "1",
        "allowmultiplestdins": "0", "answer": cdata(without_package(reference)), "validateonsave": "1",
        "testsplitterre": cdata(SPLITTER_RE), "language": "java", "acelang": "java", "sandbox": "",
        "grader": "EqualityGrader", "cputimelimitsecs": "10", "memlimitmb": "", "sandboxparams": "",
        "templateparams": "", "templateparamslang": "None", "templateparamsevalpertry": "0",
        "templateparamsevald": "", "hoisttemplateparams": "1", "extractcodefromjson": "1", "twigall": "0",
        "uiplugin": "ace", "uiparameters": "", "attachments": "0", "attachmentsrequired": "0",
        "maxfilesize": "10240", "filenamesregex": "", "filenamesexplain": "", "displayfeedback": "1",
        "giveupallowed": "0",
    }
    body = "\n".join(f"    <{k}>{v}</{k}>" for k, v in fields.items())
    name = f"P{phase}-CODE-{number:02d} {cls}"
    return (f'  <question type="coderunner">\n'
            f"    <name><text>{html.escape(name)}</text></name>\n"
            f'    <questiontext format="html"><text>{cdata(text)}</text></questiontext>\n'
            f'    <generalfeedback format="html"><text>{cdata(feedback)}</text></generalfeedback>\n'
            f"    <defaultgrade>1</defaultgrade>\n    <penalty>0</penalty>\n    <hidden>0</hidden>\n"
            f"    <idnumber></idnumber>\n{body}\n    <testcases>\n" + "\n".join(cases) +
            "\n    </testcases>\n  </question>")


def build() -> dict[Path, str]:
    jobs = {}
    for phase, path, tests in PROBLEMS:
        codes = [c for c, _ in tests]
        support = support_code(path)
        name = path.replace("/", "_")
        jobs[name + "__ref"] = tester_java(without_package((DSA / "solutions" / PKG / f"{path}.java").read_text()),
                                           support, codes)
        jobs[name + "__stub"] = tester_java(without_package((DSA / "src/main/java" / PKG / f"{path}.java").read_text()),
                                            support, codes)
    results = run_all(jobs)

    files: dict[Path, list[str]] = {}
    numbers: dict[int, int] = {}
    for phase, path, tests in PROBLEMS:
        name = path.replace("/", "_")
        expected = split_outputs(results[name + "__ref"], len(tests), name)
        got_by_stub = split_outputs(results[name + "__stub"], len(tests), name + " (stub)")
        passing = [i + 1 for i, (e, s) in enumerate(zip(expected, got_by_stub)) if e == s]
        if passing:
            raise SystemExit(f"{path}: the stub already passes test(s) {passing}; make those tests stronger")
        if any(e in ("UnsupportedOperationException", "") for e in expected):
            raise SystemExit(f"{path}: the reference solution gave a suspicious output: {expected}")
        numbers[phase] = numbers.get(phase, 0) + 1
        out = OUT / f"phase-{phase}-coding-practice.xml"
        if out not in files:
            files[out] = ['<?xml version="1.0" encoding="UTF-8"?>', "<quiz>", '  <question type="category">',
                          f"    <category><text>$course$/top/{COURSE_CATEGORY}/Phase {phase}/Coding practice"
                          "</text></category>", "  </question>"]
        files[out].append(question_xml(phase, numbers[phase], path, tests, expected))
        print(f"P{phase} {numbers[phase]:02d} {path}: {len(tests)} tests, stub fails all")
    return {p: "\n".join(lines + ["</quiz>"]) + "\n" for p, lines in files.items()}


def main() -> None:
    check = "--check" in sys.argv
    built = build()
    stale = []
    for path, xml in built.items():
        if check:
            if not path.exists() or path.read_text() != xml:
                stale.append(path.relative_to(ROOT))
        else:
            path.parent.mkdir(parents=True, exist_ok=True)
            path.write_text(xml)
            print(f"wrote {path.relative_to(ROOT)}")
    if stale:
        raise SystemExit("stale CodeRunner banks (run build_coderunner.py): " + ", ".join(map(str, stale)))


if __name__ == "__main__":
    main()
