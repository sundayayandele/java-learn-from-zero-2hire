# Plan: in-Moodle code grading with CodeRunner and Jobe

← [Moodle blueprint](BLUEPRINT.md) · [Practice track](../../practice/README.md)

Goal: learners submit Java and SQL answers inside Moodle and get them graded automatically, so the coding practice checkpoint, and later timed coding quizzes, no longer depend on learners pasting test output.

Two parts are needed:

- **CodeRunner** (`qtype_coderunner`): a Moodle question type. It depends on the `qbehaviour_adaptive_adapted_for_coderunner` question behaviour.
- **Jobe**: a separate server that compiles and runs the submitted code in a sandbox and returns the results to Moodle.

## Status (24 October 2026)

**Moodle 5.2 support confirmed.** The Moodle Marketplace lists CodeRunner as supporting Moodle 2.9–5.2. The project's CI ran its full PHPUnit and Behat suites on `MOODLE_502_STABLE` with MariaDB, and they passed for commit `01a0f9f`.

**Backed up**, before any change: `/root/backups/itlearning-pre-coderunner-20261024-1142/` on the server, with a copy on the site owner's Mac. The backup has:

- the database dump;
- the `moodledata` volume;
- `/opt/itlearning-tech` (Dockerfile, plugins, compose file, `.env`, `config.php`);
- SHA-256 checksums.

The previous image is kept under the tag `itlearning-tech-moodle:pre-coderunner-20261024-1142`.

**Installed:**

- CodeRunner **5.10.6** (build 2026092700, commit `01a0f9f`, the CI-tested commit, which adds Twig security patches after the `v5.10.6` tag);
- *Adaptive adapted for CodeRunner* **1.4.8**.

Both live in `/opt/itlearning-tech/moodle/plugins/question/` and are baked into the image like the other add-ons; `question/PINNED.txt` records the commits. The image was rebuilt and the Moodle containers recreated, then the upgrade ran. Maintenance mode lasted 41 seconds. The 19 built-in prototypes were loaded by running CodeRunner's ad hoc task. On Moodle 5 that task is queued to cron rather than run during the install.

**Smoke-tested** against the default public Jobe server (`jobe2.cosc.canterbury.ac.nz`):

- Raw Java runs through Moodle's sandbox layer: a correct program printed 55, a wrong one printed 54, and a compile error returned javac's message.
- A temporary `java_method` question, graded through the question engine:

  | Answer | Fraction | Tests passed | Note |
  | --- | --- | --- | --- |
  | Correct | 1 | 2/2 | |
  | Wrong | 0 | 1/2 | all-or-nothing marking |
  | Compile error | 0 | | the error message was shown |

  The question and its category were deleted afterwards.

Each Java run took about 1.1–1.2 s.

**Our own Jobe server is live** at `https://jobe.itlearning.tech`, on a dedicated VPS (`ssdnodes-48`, 89.233.107.182; Ubuntu 26.04, 12 vCPU, 46 GB RAM). CodeRunner's `jobe_host` points to it, and the API key setting is empty. [`scripts/moodle/jobe/setup-jobe.sh`](../../scripts/moodle/jobe/setup-jobe.sh) builds and configures everything and can be re-run:

- **Versions:** JobeInABox `aad6f5c` with Jobe `bfca53c` (both pinned), Java **21.0.12** (`openjdk-21-jdk-headless` instead of the image's default JDK), time zone Africa/Lagos.
- **Network:** Jobe listens on `127.0.0.1:4000` only. Caddy serves it over HTTPS with a Let's Encrypt certificate (renewed automatically) and answers only the Moodle server (187.127.78.116).
- **Firewall:** ufw allows SSH, port 80 (certificate challenges only) and port 443 from the Moodle server only. Checked from outside: 443 times out and 4000 is closed.
- **Java tuning, needed on this VPS.** Its Xeon Silver 4216 cores are about 3.6× slower per core than the Moodle server's EPYC (a pure Python loop took 8.65 s against 2.38 s). Compiling even hello-world used 4–5 s of CPU time, over the ~3 s limit CodeRunner sends, so most valid answers failed with "timelimit exceeded". Two changes fix it:
  - `javac` runs with `-J-XX:ActiveProcessorCount=1 -J-XX:TieredStopAtLevel=1 -J-XX:+UseSerialGC`, and programs run with `-XX:ActiveProcessorCount=1`. This halves the CPU time of a compile.
  - The minimum CPU time for the **compile step only** is raised from 2 s to 20 s, because with 16 compiles at once each one used up to 7.3 s. Learners' programs still run under the question's own limit.
- **Results after tuning:**

  | Test | Result |
  | --- | --- |
  | Sandbox runs through Moodle | 9/9 |
  | Graded `java_method` questions | 9/9 (correct 1.0; wrong 0 with 1/2 tests; compile error reported) |
  | Direct bursts on Jobe | 8 at once 24/24; 16 at once 109/112 |
  | 16 at once through Moodle | 32/32 |

  The 3 burst failures came in the first burst after a restart, and weren't time limits. They didn't recur in the next 96 direct runs or the 32 through Moodle. A single Java answer takes about 2.5–4.5 s; with 16 at once, 10–19 s.

**Abuse protection** (added 24 October 2026; all in the setup script). The threat is a learner, or anyone who signs up and enrols, submitting a "Java answer" that attacks other systems from our VPS. On the day it was added, the VPS had logged 16,841 failed SSH logins in 24 hours. The protections:

- **SSH: keys only.** `/etc/ssh/sshd_config.d/00-itl-hardening.conf` sets `PasswordAuthentication no` and `PermitRootLogin prohibit-password`. fail2ban bans an address after 5 failures in 10 minutes, for an hour at first and up to a week for repeat offenders. It banned 5 addresses within minutes.
- **No outbound network from learner code.** `jobe-egress-block.service` drops every new connection that starts in a container: to the internet (`DOCKER-USER` chain) and to the host's own services (`INPUT`). It runs at boot and again whenever Docker restarts. The script ends by checking it with Jobe jobs that try to reach 1.1.1.1:443, the Docker host and the server's public address; all three are blocked. Image builds need the network, so a rebuild stops Jobe and lifts the block until the new container is running.
- **Container limits:** 10 of 12 CPUs, 60% of the RAM (about 28 GB) and 8,192 processes. A flood of submissions can't starve SSH or Caddy. Jobe still limits each job's CPU time and runs at most 16 jobs at once.
- **Re-tested after the change:** all 30 questions scored correctly, and 16 at once through Moodle gave 32/32 correct.

**If Jobe is down.** The monitoring stack ([ops/monitoring](../../ops/monitoring/README.md)) emails admin@itlearning.tech when `https://jobe.itlearning.tech` stops answering for 3 minutes. While it's down:

- **Learners:** each coding quiz says what to do if *Check* isn't working. Every question is a `practice/dsa` problem with the same tests, so they can carry on locally with `./mvnw test -Dtest=ClassNameTest` and check here later. Their quiz attempts stay open.
- **Emergency fallback (short outages only):** point CodeRunner at the University of Canterbury's public test Jobe. Run `docker exec -u www-data moodle php admin/cli/cfg.php --component=qtype_coderunner --name=jobe_host --set=jobe2.cosc.canterbury.ac.nz` on the Moodle server, and set it back to `https://jobe.itlearning.tech` afterwards. That server is shared and rate-limited, and learners' code goes to a third party, so don't leave it on.
- **Why not list both servers:** CodeRunner does accept several hosts separated by `;`, but it picks one at random for each run, so that spreads load rather than failing over.
- **Why not another playground:** CodeRunner can only use a Jobe server (or the retired Ideone service), so playgrounds such as Judge0 or Piston can't stand in.

**Capacity.** This server suits practice quizzes and moderate class sizes. For a timed coding exam with many learners submitting at once, use a VPS with faster cores (or add a second Jobe server: CodeRunner accepts several hosts separated by `;`) and load-test first.

## What we know about itlearning.tech today

| Item | Value (checked 24 October 2026) |
| --- | --- |
| Moodle | 5.2.4+ (Build 20261021) |
| PHP / database | PHP 8.3.36, MariaDB 11.4 |
| Hosting | Apache inside a Docker container on an Ubuntu 24.04 host |
| CodeRunner | Not installed |
| Latest CodeRunner | 5.10.6 (27 September 2026); requires Moodle 4.3 or later, PHP 8.1 or later |

The plugin directory hasn't been confirmed to list Moodle 5.2 as supported. Its `version.php` sets no upper limit, and it is actively maintained. Treat 5.2 as **likely but untested**: back up first and smoke-test before learners see it.

## Decisions for the site admin

1. **Where Jobe runs.** Use a separate small VPS: Jobe runs untrusted learner code, and the Jobe authors strongly recommend a dedicated, firewalled server even inside a container. Size it at 2–4 vCPU and 4–8 GB RAM (Java compiles are CPU- and memory-hungry). Running Jobe on the Moodle host is cheaper, but one escape or one runaway job then affects the live site.
2. **Java version on Jobe.** JobeInABox installs Ubuntu's `default-jdk`, which is newer than the course's Java 21. Newer JDKs compile Java 21 code, so either is acceptable. To match the course exactly, change `default-jdk` to `openjdk-21-jdk` in the Dockerfile before building.
3. **How the Moodle container keeps plugins.** Plugin code lives in the Moodle code directory. If the container is rebuilt from a stock image, plugins installed through the web UI disappear unless that directory is a volume or the image is customised. Install CodeRunner the same way `mod_customcert` and `mod_exammanagement` were installed, so it survives the same way.

## Steps for the site admin

These steps need server access and site-admin rights.

### 1. Back up

Take a database dump and a copy of `moodledata`, or snapshot the VPS. This gives you a way back if the plugin upgrade misbehaves on 5.2.

### 2. Build and run Jobe (on the Jobe server)

```bash
sudo apt-get update && sudo apt-get install -y docker.io git ufw
git clone https://github.com/trampgeek/jobeinabox.git && cd jobeinabox
# optional: in Dockerfile, replace default-jdk with openjdk-21-jdk
sudo docker build . -t itl/jobeinabox --build-arg TZ="Africa/Lagos"
sudo docker run -d -p 4000:80 --restart unless-stopped --name jobe itl/jobeinabox
```

Firewall Jobe so that only the Moodle server can reach it. Replace `<moodle-host-ip>` with the public IP address of the itlearning.tech server.

```bash
sudo ufw allow OpenSSH
sudo ufw allow from <moodle-host-ip> to any port 4000 proto tcp
sudo ufw enable
```

Docker can publish ports around ufw. Check from another machine that port 4000 is **not** reachable. If it is, bind the port to a private network interface, or add the rule in the `DOCKER-USER` chain.

Check that Jobe works (from the Moodle server):

```bash
curl -s http://<jobe-host>:4000/jobe/index.php/restapi/languages
```

The response should list `java` and its version. For a load test, `testsubmit.py --perf java` (in the Jobe repository) shows how many Java runs per second the server handles. Jobe runs 8 jobs at once by default; raise `jobe_max_users` if the server has more cores.

### 3. Install the plugins in Moodle

1. Download **CodeRunner 5.10.6** (or newer) and **Adaptive adapted for CodeRunner** from moodle.org/plugins, or install them with `git clone` into `question/type/coderunner` and `question/behaviour/adaptive_adapted_for_coderunner` inside the Moodle code directory.
2. *Site administration → Notifications*: run the upgrade.
3. *Site administration → Plugins → Question types → CodeRunner*:
   - set **Jobe server** to `<jobe-host>:4000`;
   - leave the API key empty (the firewall restricts access instead).
4. If test runs fail with a "blocked URL" error: add port `4000` under *Site administration → General → Security → HTTP security → cURL allowed ports*. If Jobe is on a private IP address, also remove that address range from *cURL blocked hosts list*. A better option is to put Jobe on port 443 behind a reverse proxy with TLS.

### 4. Smoke test

1. In a hidden test course, create a CodeRunner question of type `java_method`.
2. Write a correct answer and a wrong one, and preview both.
3. Check that the correct one passes, the wrong one fails with test details, and a compile error is reported clearly.
4. Delete the test course afterwards.

**Rollback:** uninstall CodeRunner under *Plugins → Plugins overview*, then stop the Jobe container. Nothing in courses 4 and 5 depends on CodeRunner until the steps below are done.

## Coding practice questions and quizzes (done 24 October 2026)

**Questions.** [`scripts/moodle/coderunner/build_coderunner.py`](../../scripts/moodle/coderunner/build_coderunner.py) turns 15 `practice/dsa` problems per phase into CodeRunner questions, listed in [`problems.py`](../../scripts/moodle/coderunner/problems.py):

- Phase 1: arrays 4, windows 4, strings 3, stacks 2, search 2.
- Phase 2: lists 3, search 1, trees 3, graphs 3, dynamic programming 3, design 2.

Each question is built like this:

- **Starting code** in the editor: the generated stub. **Sample answer:** the reference solution.
- **Tests:** taken from the JUnit tests, 3–7 per problem. Their **expected outputs are computed** by running the reference solution through the same tester Jobe uses (in Docker, `eclipse-temurin:21-jdk`). The build fails if a stub passes any test, or if CodeRunner's file-naming logic (ported to Python) would misread a program.
- **Marking:** all or nothing, unlimited checks, no penalty. Output to `lms/moodle/import/coderunner/phase-<n>-coding-practice.xml`, category *Java Zero-to-Hired/Phase N/Coding practice*.

Three design points matter on our Jobe server:

- **One compile per check.** It uses a combinator template, so all tests run in one compile and one run. CodeRunner's own `java_class` template compiles once per test (about 3 s each here).
- **No public class.** The tester class is a non-public `NO_PUBLIC_CLASS_FOUND`, and the template removes `public` from top-level types in the answer. CodeRunner's detection of the main class counts braces but not character literals (`'{'` in BalancedBrackets), so with any public class some answers wouldn't compile. With none, CodeRunner always falls back to `NO_PUBLIC_CLASS_FOUND.java`.
- **A time budget for large inputs.** Large-input tests run with a 3-second wall-clock budget (`within(...)`), so a too-slow answer gets "Too slow: no answer within 3 s" in about 8 seconds. Without it, CodeRunner reruns every test separately after a timeout, which took about a minute. This relies on the Jobe flag `UseCountedLoopSafepoints` (see the setup script).

**Verified on Jobe** through Moodle's question engine:

- All 30 sample answers score 1 and all 30 stubs score 0, with no test passing. One check takes about 3–8 s.
- In a quiz preview in the browser, a correct HashMap answer scored 1/1. An O(n²) answer passed the five small tests and showed "Too slow" on the large one (0/1).

**Quizzes.** One *Coding practice quiz: 15 auto-graded Java problems* per course, in the Start here section:

- Phase 1: cmid 379. Phase 2: cmid 380.
- Adaptive behaviour (a Check button per question), one question per page, in practice-plan order.
- Unlimited attempts, highest grade, pass 7/10, and completion on a pass. Learners see the reference solutions after finishing an attempt.
- It is optional: it isn't in course completion or the certificate restrictions. The mentor-checked coding practice checkpoint assignment (which also covers SQL) is unchanged.

**Updating questions.** Edit `problems.py`, rebuild, then re-import. Because the questions are in a quiz, first remove the quiz's question slots, then import with `--replace`, then add the questions back (in order, one per page). `build_coderunner.py --check` reports stale XML.

**Not covered by CodeRunner:**

- **SQL drills.** CodeRunner's SQL support needs a database inside Jobe, and JobeInABox has no PostgreSQL, so they stay graded by Testcontainers.
- **The live-coding assessment.** It stays human-assessed, because communication is part of its rubric.

## Security and running costs

- Jobe executes arbitrary code. Keep the Jobe server free of secrets, update it monthly (`docker pull` of the base image and a rebuild), and keep it firewalled.
- Moodle sends each answer to Jobe over the network. Plain HTTP is acceptable only inside a private network; across the internet, use TLS (a reverse proxy in front of Jobe).
- Cost: one small VPS. Java runs take roughly a second or more each. Size the server from the `--perf` result and the expected number of learners submitting at the same time.

## Sources

- [CodeRunner on GitHub](https://github.com/trampgeek/moodle-qtype_coderunner) (`version.php`: 5.10.6, requires Moodle 4.3)
- [CodeRunner in the Moodle plugins directory](https://moodle.org/plugins/qtype_coderunner)
- [Jobe](https://github.com/trampgeek/jobe) (firewalling, API keys, performance testing)
- [JobeInABox](https://github.com/trampgeek/jobeinabox) (Docker build, time zone, API keys)
