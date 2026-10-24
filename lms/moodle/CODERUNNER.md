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

**Not done yet:** our own Jobe server. The public Canterbury server is for testing only: it is shared, rate-limited and outside our control, and learners' code would be sent to a third party. Don't put CodeRunner questions in front of learners until `jobe_host` points to our own Jobe (step 2 below), then repeat the smoke test.

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

## Then, in the repository and on Moodle (Claude can do this)

Once you confirm that the smoke test passed and tell me the Jobe host:

1. **Convert practice problems to CodeRunner questions.** A generator script will turn a subset of `practice/dsa` problems into CodeRunner Moodle XML: the starter stub becomes the answer preload, and the JUnit cases become CodeRunner test cases. The subset is about 15 for Phase 1 and 15 more for Phase 2, chosen to cover every topic folder. The script will check that each reference solution passes and each stub fails, as the practice track does now.
2. **SQL drills.** CodeRunner's SQL support needs a database engine inside Jobe; JobeInABox doesn't include PostgreSQL. SQL drills therefore stay graded by Testcontainers unless you also want SQLite drills (simpler, but a different SQL dialect from the course's PostgreSQL).
3. **Add a "Coding practice quiz" per phase**, built from those questions with unlimited attempts and Interactive or Adaptive feedback. Then turn the **coding practice checkpoint** into this quiz with a 70% pass mark, so it's graded automatically instead of by a mentor.
4. Optionally, add a **timed coding quiz** before the live-coding assessment (for example 3 problems in 60 minutes) as a practice run. The live-coding assessment itself stays human-assessed, because communication is part of its rubric.
5. Update the course completion criteria, the certificate restrictions, `BLUEPRINT.md` and the practice track README.

## Security and running costs

- Jobe executes arbitrary code. Keep the Jobe server free of secrets, update it monthly (`docker pull` of the base image and a rebuild), and keep it firewalled.
- Moodle sends each answer to Jobe over the network. Plain HTTP is acceptable only inside a private network; across the internet, use TLS (a reverse proxy in front of Jobe).
- Cost: one small VPS. Java runs take roughly a second or more each. Size the server from the `--perf` result and the expected number of learners submitting at the same time.

## Sources

- [CodeRunner on GitHub](https://github.com/trampgeek/moodle-qtype_coderunner) (`version.php`: 5.10.6, requires Moodle 4.3)
- [CodeRunner in the Moodle plugins directory](https://moodle.org/plugins/qtype_coderunner)
- [Jobe](https://github.com/trampgeek/jobe) (firewalling, API keys, performance testing)
- [JobeInABox](https://github.com/trampgeek/jobeinabox) (Docker build, time zone, API keys)
