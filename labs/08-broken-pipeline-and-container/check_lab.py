#!/usr/bin/env python3
"""Checker for Lab 8.D. Run from this folder:  python3 check_lab.py            (static checks)
                                               python3 check_lab.py --docker   (also build and run the image)

Each rule prints PASS or FAIL with a hint. The lab is done when every rule passes. Standard library only.
"""
from __future__ import annotations

import re
import subprocess
import sys
import time
import urllib.request
from pathlib import Path

APP = Path(__file__).resolve().parent / "app"
results: list[tuple[bool, str, str]] = []


def rule(ok: bool, name: str, hint: str) -> None:
    results.append((bool(ok), name, hint))


def read(rel: str) -> str:
    p = APP / rel
    return p.read_text(encoding="utf-8") if p.exists() else ""


def dockerfile_checks() -> None:
    d = read("Dockerfile")
    lines = [l.strip() for l in d.splitlines() if l.strip() and not l.strip().startswith("#")]
    froms = [l for l in lines if l.upper().startswith("FROM ")]
    rule(len(froms) >= 2, "Dockerfile: multi-stage build", "build with a JDK/Maven stage, run on a smaller runtime stage")
    rule(froms and all(re.search(r":[\w.\-]+", f.split()[1]) and ":latest" not in f for f in froms),
         "Dockerfile: base images pinned to a version", "use tags such as eclipse-temurin:21-jre, never latest or no tag")
    last = froms[-1].lower() if froms else ""
    rule("jre" in last or "distroless" in last, "Dockerfile: runtime image is a JRE (or distroless)", "the runtime stage doesn't need Maven or a JDK")
    after_last = d[d.lower().rfind("\nfrom "):] if froms else ""
    users = re.findall(r"^\s*USER\s+(\S+)", after_last, re.M)
    rule(users and users[-1] not in ("root", "0", "0:0"), "Dockerfile: runs as a non-root user", "create a user and switch with USER")
    rule(re.search(r"^\s*HEALTHCHECK\s", d, re.M), "Dockerfile: has a HEALTHCHECK", "probe the app (for example a TCP check on 8080)")
    entry = [l for l in lines if l.upper().startswith(("ENTRYPOINT", "CMD"))]
    rule(entry and entry[-1].split(None, 1)[1].lstrip().startswith("["), "Dockerfile: exec-form ENTRYPOINT/CMD",
         "use the JSON array form so the JVM receives SIGTERM and shuts down gracefully")
    rule("MaxRAMPercentage" in d, "Dockerfile: JVM sized for the container", "set -XX:MaxRAMPercentage (for example via JAVA_TOOL_OPTIONS)")
    ignore = read(".dockerignore")
    rule(all(x in ignore for x in ("target", ".git", ".env")), ".dockerignore excludes target, .git and .env",
         "keep build output, history and secrets out of the build context")


def compose_checks() -> None:
    c = read("compose.yaml")
    rule(c and not re.search(r"(PASSWORD|SECRET|TOKEN)\w*:\s*['\"]?[A-Za-z0-9][^$\s]*\s*$", c, re.M),
         "compose: no secrets written in the file", "use ${DB_PASSWORD} with a .env file that is git-ignored")
    rule(not re.search(r"['\"]?(0\.0\.0\.0:)?5432:5432", c), "compose: database port not published to the world",
         "remove the ports mapping, or bind to 127.0.0.1 for local tools only")
    rule(re.search(r"image:\s*postgres:\d+", c), "compose: database image pinned", "for example postgres:17-alpine")
    rule("healthcheck:" in c and "service_healthy" in c, "compose: app waits for a healthy database",
         "add a pg_isready healthcheck and depends_on: condition: service_healthy")


def workflow_checks() -> None:
    w = read(".github/workflows/ci.yml")
    rule(w and not any("echo" in l and "secrets." in l and "--password-stdin" not in l for l in w.splitlines()),
         "workflow: secrets are never printed", "never echo secrets; use docker/login-action")
    rule("|| true" not in w, "workflow: failing tests fail the build", "remove || true")
    test_at = min([i for i in (w.find("mvnw"), w.find("mvn ")) if i >= 0], default=-1)
    push_at = max(w.find("docker push"), w.find("push: true"))
    rule(test_at >= 0 and push_at >= 0 and test_at < push_at, "workflow: tests run before the image is pushed",
         "a job or step that runs ./mvnw verify must come first")
    rule(re.search(r"if:.*refs/heads/main", w), "workflow: images are published only from main",
         "guard the publishing job with if: github.ref == 'refs/heads/main' (and not on pull requests)")
    rule(re.search(r"^permissions:\s*\n\s+contents:\s*read", w, re.M), "workflow: least-privilege permissions",
         "add a top-level permissions: contents: read and grant packages: write only to the publishing job")
    old = re.findall(r"uses:\s*actions/(checkout|setup-java)@v([0-9]+)", w)
    rule(old and all(int(v) >= 4 for _, v in old), "workflow: current action versions", "actions/checkout@v5, actions/setup-java@v5")
    rule("github.sha" in w, "workflow: image tagged with the commit SHA", "tag ghcr.io/<owner>/<repo>:${{ github.sha }} so deployments are traceable")


def docker_checks() -> None:
    tag = "lab8-rates:check"
    built = subprocess.run(["docker", "build", "-t", tag, str(APP)], capture_output=True, text=True)
    rule(built.returncode == 0, "docker: image builds", built.stderr[-300:] if built.returncode else "")
    if built.returncode:
        return
    user = subprocess.run(["docker", "run", "--rm", "--entrypoint", "id", tag, "-u"], capture_output=True, text=True).stdout.strip()
    rule(user not in ("", "0"), "docker: container user is not root", f"id -u returned {user!r}")
    cid = subprocess.run(["docker", "run", "-d", "-p", "127.0.0.1:18080:8080", tag], capture_output=True, text=True).stdout.strip()
    ok = False
    try:
        for _ in range(60):
            try:
                with urllib.request.urlopen("http://127.0.0.1:18080/api/rates/USD", timeout=2) as r:
                    ok = r.status == 200
                    break
            except OSError:
                time.sleep(2)
    finally:
        subprocess.run(["docker", "rm", "-f", cid], capture_output=True)
    rule(ok, "docker: the app answers on port 8080", "check the logs with docker logs")


def main(argv: list[str]) -> int:
    dockerfile_checks()
    compose_checks()
    workflow_checks()
    if "--docker" in argv:
        docker_checks()
    for ok, name, hint in results:
        print(f"{'PASS' if ok else 'FAIL'}  {name}" + ("" if ok else f"\n      hint: {hint}"))
    failed = sum(1 for ok, *_ in results if not ok)
    print(f"\n{len(results) - failed}/{len(results)} rules pass")
    return 1 if failed else 0


if __name__ == "__main__":
    sys.exit(main(sys.argv[1:]))
