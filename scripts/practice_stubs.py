#!/usr/bin/env python3
"""Generate the learner stubs in practice/dsa/src/main/java and practice/sql/queries from the reference solutions.

    python scripts/practice_stubs.py           # write the stubs
    python scripts/practice_stubs.py --check   # exit 1 if a stub is out of date (run by check_repo.py)

In practice/dsa/solutions, a method body between `//>>` and `//<<` becomes
`throw new UnsupportedOperationException("Not implemented yet");`, and a block between `//>>hide` and `//<<`
(fields or helper methods that give the answer away) is removed. In practice/sql/solutions, everything after the
`-- Your query below:` line is removed.
"""
from __future__ import annotations

import sys
from pathlib import Path

ROOT = Path(__file__).resolve().parent.parent
SOLUTIONS = ROOT / "practice" / "dsa" / "solutions"
STUBS = ROOT / "practice" / "dsa" / "src" / "main" / "java"
SQL_SOLUTIONS = ROOT / "practice" / "sql" / "solutions"
SQL_QUERIES = ROOT / "practice" / "sql" / "queries"
SQL_MARKER = "-- Your query below:"
BANNER = "// Generated from practice/dsa/solutions by scripts/practice_stubs.py: implement the TODOs, keep the signatures.\n"


def stub(text: str) -> str:
    out, mode = [], None
    for line in text.splitlines():
        marker = line.strip()
        if mode is None and marker == "//>>":
            indent = line[: len(line) - len(line.lstrip())]
            out.append(f'{indent}throw new UnsupportedOperationException("Not implemented yet");')
            mode = "body"
        elif mode is None and marker == "//>>hide":
            mode = "hide"
        elif mode is not None and marker == "//<<":
            mode = None
        elif mode is None:
            out.append(line)
    if mode is not None:
        raise ValueError("unclosed //>> block")
    return BANNER + "\n".join(out) + "\n"


def sql_stub(text: str) -> str:
    return text[: text.index(SQL_MARKER) + len(SQL_MARKER)] + "\n\n"


def outputs() -> dict[Path, str]:
    files = {STUBS / f.relative_to(SOLUTIONS): stub(f.read_text(encoding="utf-8")) for f in sorted(SOLUTIONS.rglob("*.java"))}
    files.update({SQL_QUERIES / f.name: sql_stub(f.read_text(encoding="utf-8")) for f in sorted(SQL_SOLUTIONS.glob("*.sql"))})
    return files


def main(argv: list[str]) -> int:
    files = outputs()
    expected = set(files)
    existing = (set(STUBS.rglob("*.java")) if STUBS.exists() else set()) | (set(SQL_QUERIES.glob("*.sql")) if SQL_QUERIES.exists() else set())
    stale = [p for p, t in files.items() if not p.exists() or p.read_text(encoding="utf-8") != t] + sorted(existing - expected)
    if "--check" in argv:
        for p in stale:
            print(f"stale: {p.relative_to(ROOT)} (run python scripts/practice_stubs.py)", file=sys.stderr)
        return 1 if stale else 0
    for p in existing - expected:
        p.unlink()
    for p, t in files.items():
        p.parent.mkdir(parents=True, exist_ok=True)
        p.write_text(t, encoding="utf-8")
    print(f"practice stubs: {len(files)} files")
    return 0


if __name__ == "__main__":
    sys.exit(main(sys.argv[1:]))
