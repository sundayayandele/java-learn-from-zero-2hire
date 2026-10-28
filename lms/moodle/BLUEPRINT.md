# Moodle course blueprint: Java Backend Engineer, Zero to Hired

How to run Phase 1 as a Moodle course on [itlearning.tech](https://itlearning.tech). Unlike the FDE course, **the reading content and quizzes live in Moodle** (learners read, watch and take graded CBTs there), while GitHub stays the source of truth: every Moodle page is generated from the repository, so you rebuild and re-import rather than edit in Moodle.

Built for Moodle 4.5 / 5.x with the standard Page, URL, Quiz and Assignment activities. Optional: `mod_customcert` for certificates, and the `local_wsmanagesections` plugin if you want section summaries set by the API.

## 1. One-time setup: web services and a token

Do this yourself in Moodle as an administrator. **Never paste the token into chat, tickets, Git or CI logs.**

1. *Site administration → Advanced features*: enable **web services**.
2. *Site administration → Server → Web services → Manage protocols*: enable **REST**.
3. Create a dedicated user, for example `course-builder`, with the **Course creator** role at system level (or Manager on the target category only).
4. *External services → Add*: name `java-zero-hire`, enabled, **Authorised users only**; authorise `course-builder`. Add these functions:
   - `core_webservice_get_site_info`
   - `core_course_get_categories`, `core_course_create_categories`
   - `core_course_get_courses_by_field`, `core_course_create_courses`, `core_course_get_contents`
   - `core_update_inplace_editable`
   - optional: `core_user_add_user_private_files` (for `push.py upload`, also tick "Can upload files" on the service), `local_wsmanagesections_update_sections` (if that plugin is installed)
5. *Manage tokens → Create token* for `course-builder` on the `java-zero-hire` service. Set an expiry date and an IP restriction if you can.
6. On your machine, store it in one of two places:

   ```bash
   export MOODLE_URL=https://itlearning.tech        # your site's base URL (include a subpath such as /moodle if it has one)
   export MOODLE_TOKEN=...                          # paste only into your own terminal
   ```

   or `~/.config/java-zero-hire/moodle.ini` with `chmod 600`:

   ```ini
   [moodle]
   url = https://itlearning.tech
   token = ...
   ```

## 2. Build and push

```bash
python scripts/moodle/build_moodle.py       # needs pandoc; writes lms/moodle/out/
python scripts/moodle/push.py plan          # offline preview of the course
python scripts/moodle/push.py check         # confirms the token works and lists missing functions
python scripts/moodle/push.py apply         # asks you to type the host name before changing anything
python scripts/moodle/push.py upload        # optional: puts the XML/HTML files in your Moodle private files
```

`apply` creates the category **Java Zero-to-Hired**, the course **Java Backend Engineer: Phase 1 Foundations** (short name `JAVA-Z2H-P1`, *hidden*, completion tracking on, Custom sections format) with eight sections, and names them. It is safe to re-run: existing category and course are reused.

Core Moodle web services can't create activities or import questions, so those are done in the UI (section 4).

## 3. Course settings

| Setting | Value |
| --- | --- |
| Category | Java Zero-to-Hired |
| Full name / short name / ID | Java Backend Engineer: Phase 1 Foundations / JAVA-Z2H-P1 / java-z2h-2026-p1 |
| Format | Custom sections (`topics`), 8 sections |
| Visibility | Hidden until reviewed |
| Completion tracking | On; show activity dates and completion conditions |
| Groups | One group per cohort (separate groups) |

## 4. Sections and activities

| # | Section | Activities |
| --- | --- | --- |
| 1 | Phase 1: start here | URL: Phase 1 plan; Page: how the course works (read → watch → CBT → practice → project); Forum: questions and help |
| 2 | Module 1: Modern Java & OOP (weeks 1–4) | For each topic 1.1–1.11: **Page** + **Quiz**; URL: Module 1 projects; Assignment: Module 1 project submission |
| 3 | Module 2: Concurrency & JVM (weeks 5–6) | Topics 2.1–2.5: Page + Quiz; URL: projects; Assignment: project 2.C `FINDINGS.md` |
| 4 | Module 3: Databases (weeks 7–9) | Topics 3.1–3.6: Page + Quiz; URL: projects; Assignments: 3.B ledger schema, 3.D `TUNING.md` |
| 5 | Module 4: Spring Boot 4 (weeks 10–13) | Topics 4.1–4.7: Page + Quiz; URL: projects; Assignments: 4.A, 4.D `UPGRADE.md` |
| 6 | Phase 1 capstones | **Page: capstones overview and rubric**; **Page per capstone** (brief, five architecture diagrams, step-by-step build guide); URL: deployment guide; **Assignment per capstone** (10) |
| 7 | End-of-phase exam | URL: exam information; **Quiz: Phase 1 end-of-phase exam** |
| 8 | Getting hired | URL: Getting hired guide; Assignment: Getting Hired checklist; optional BigBlueButton room for mock interviews |

### Topic pages

*Add an activity → Page*. Name: the topic title (e.g. "2.3 Virtual threads (Project Loom) and structured concurrency"). Content: open `lms/moodle/out/pages/2.3.html`, switch the editor to HTML source view, paste. The YouTube videos are embedded with `youtube-nocookie.com`; if your site's HTML purifier strips iframes, enable *Site administration → Security → Site security settings → Allow EMBED and OBJECT tags* for trusted editors, or use Moodle's media filter by pasting plain YouTube links. Completion: **view**.

### Topic quizzes

1. *Course → More → Question bank → Import*, format **Moodle XML**, upload `lms/moodle/out/questions/topic-2.3.xml` (each file creates its own category).
2. *Add an activity → Quiz*, name "2.3 Check your understanding". Settings: no time limit; attempts unlimited; grading method highest; **grade to pass 70%**; questions per page 1; shuffle within questions **on**; review options: show correctness and general feedback after each attempt.
3. *Questions → Add → from question bank*: select all questions in the topic's category.
4. Completion: **receive a passing grade**. Restrict access: page viewed.

### End-of-phase exam

Import `lms/moodle/out/questions/phase-1-exam.xml` (built from the private bank: clone the private repo `sundayayandele/java-zero-hire-assessments` into `assessments/private/` first). Quiz settings:

| Setting | Value |
| --- | --- |
| Time limit | **120 minutes**; when time expires: open attempts are submitted automatically |
| Attempts | 2, with an enforced delay of 48 hours between attempts |
| Grade to pass | **70%** |
| Question order | Shuffled; shuffle within questions on; 5 questions per page |
| Review options | Show marks only during the open period; show correct answers and feedback after the quiz closes |
| Restrict access | All 32 topic quizzes passed (activity completion) |

The import creates one sub-category per module under *End-of-phase exam*. Add **random questions** from each module category (Phase 1: 20, 12, 14 and 14; Phase 2: 15, 13, 12, 11 and 9), so every attempt draws a different 60 from the bank of 120 with fixed module weights (blueprints in each phase's `exam/README.md`).

### Capstone assignments

One assignment per capstone: online text submission (repository URL, deployed health URL, short demo video link); grade **20 points via a rubric** with the five criteria in `phases/01-foundations/capstones/README.md` (each 1–4); grade to pass **14**; completion "receive a passing grade". Grader rule: if any criterion scores 1, override the grade to 13 and say which criterion to fix (Moodle can't enforce "no criterion below 2"). In every rubric tick **"Calculate grade having a minimum score of the minimum achievable grade for the rubric"**: the levels start at 1, and with the option unticked Moodle maps the lowest possible total to a grade of 0, so a rubric total of 14/20 would be recorded as 12 and fail.

### Course completion and certificate

Completion requires: all topic quizzes passed, Module project assignments submitted, Capstone 1 plus two other capstone assignments passed, and the exam passed. Issue the ITLearning Phase 1 certificate (`mod_customcert`) on course completion.

## 5. Status on itlearning.tech (course id 4)

Done (24 October 2026):

| Section | Live activities |
| --- | --- |
| 1 Start here | Page: how the course works and the Phase 1 plan; Forum: Questions and help |
| 1 Start here | URL: coding practice track (auto-graded Java and SQL problems); **Coding practice checkpoint** assignment (online text, 10 points, pass 7) |
| 2–5 Modules 1–4 | 32 lesson pages (text, embedded YouTube videos, official references; completion on view; topics 1.12 design patterns, 2.6 debugging and 4.8 Spring under the hood added on 24 October 2026) each before its topic quiz (236 questions in all; pass 7/10, unlimited attempts, immediate feedback; completion on pass); per module a **project submission** assignment (online text, rubric from the module's assessment table: five criteria scored Not yet 1 / Competent 2 / Strong 3, 15 points, pass 10, resubmit until pass) |
| 6 Capstones | Overview and rubric page; per capstone a page (brief, five architecture diagrams, step-by-step guide) followed by its **submission assignment** (online text, 20 points, pass 14, unlimited resubmission until pass, rubric with five criteria scored 1–4, "minimum achievable grade" option ticked so the grade equals the rubric total); **Capstone defence (viva)** assignment (15-point rubric from `docs/assessments/capstone-defence.md`, pass 10); deployment and CI/CD guide pages |
| 7 End-of-phase assessments | Exam information page; exam quiz: **60 random questions per attempt from the 120-question bank** (20/12/14/14 by module), shuffled, 5 per page, 120 minutes, 2 attempts with 48 hours between them, pass 70%, review shows marks only (answers and feedback only after a close date, if one is set), available only after all 32 topic quizzes are passed; **Timed live-coding assessment** assignment (15-point rubric from `docs/assessments/live-coding.md`, pass 10) |
| 8 Getting hired | Getting hired page with videos; URLs: Oracle certification map, Java fundamentals revision sheet; Getting Hired checklist assignment (online text, ungraded, completion on submit); **Phase 1 certificate** (`mod_customcert`, A4 landscape, name, issue date, verification code and QR; issued and emailed automatically; available only when all 32 topic quizzes, the exam, the four module projects, Capstone 1 and any two other capstones (36 OR-ed pairs), the defence, the live-coding assessment and the practice checkpoint are passed) |

**Course completion**: all of 43 activities: the 32 topic quizzes, the exam, the four module projects, Capstone 1, the defence, the live-coding assessment, the practice checkpoint, the Getting Hired checklist and the certificate (whose restriction enforces the "two other capstones" rule). Pages load from `lms/moodle/import/{pages,capstones,guides}/` (regenerate with `build_moodle.py`, then replace a page's content to update it).

CodeRunner 5.10.6 (with its behaviour plugin 1.4.8) was installed on 24 October 2026. It runs code on our own Jobe server at `https://jobe.itlearning.tech` (Java 21, reachable only from the Moodle server) and passed the smoke and load tests; see [CODERUNNER.md](CODERUNNER.md). Each course's Start here section has an optional **Coding practice quiz** of 15 auto-graded Java problems from the practice track (Phase 1 cmid 379, Phase 2 cmid 380; pass 7/10, unlimited attempts). The coding practice checkpoint assignment still covers the whole track, including the SQL drills, which stay graded by JUnit and Testcontainers on the learner's machine.

### Phase 2 on itlearning.tech (course id 5, `JAVA-Z2H-P2`)

Done (24 October 2026), built with the same settings as Phase 1 (each activity's settings form was cloned from its Phase 1 counterpart):

| Section | Live activities |
| --- | --- |
| 1 Start here | Page: how the course works and the Phase 2 plan; Forum: Questions and help; URL: coding practice track; **Coding practice checkpoint** assignment (10 points, pass 7) |
| 2–6 Modules 5–9 | 27 lesson pages (text, embedded videos, references; completion on view; topics 5.6 system design basics and 8.6 agile teamwork added on 24 October 2026) each before its topic quiz (192 questions; pass 7/10, unlimited attempts, immediate feedback; completion on pass); per module a **project submission** assignment with a five-criterion rubric from the module's assessment table (15 points, pass 10); per module a URL to its **broken-service lab** (5.E, 6.D, 7.D, 8.D, 9.D) |
| 7 Capstones | Overview and rubric page; per capstone a page (brief, five architecture diagrams, step-by-step guide) and a **submission assignment** (online text, **24 points, pass 17**, unlimited resubmission until pass, six-criterion rubric scored 1–4); **Capstone defence (viva)** assignment (15-point rubric, pass 10); deployment (with identity provider) and CI/CD quality-gates pages |
| 8 End-of-phase assessments | Exam information page; exam quiz: **60 random questions per attempt from the 120-question bank** (15/13/12/11/9 by module), shuffled, 5 per page, 120 minutes, 2 attempts 48 hours apart, pass 70%, available only after all 27 topic quizzes are passed. The bank was uploaded directly from the private assessments repository; **Timed live-coding assessment** assignment (15-point rubric, pass 10) |
| 9 Getting hired | Getting hired page with videos; URLs: certification map, Java fundamentals revision sheet; Getting Hired checklist assignment; **Phase 2 certificate** (`mod_customcert`, same A4 landscape design as Phase 1; available when all 27 topic quizzes, the exam, the five module projects, Capstone 1 and **any one** other capstone (an OR of nine), the defence, the live-coding assessment and the practice checkpoint are passed) |

**Course completion**: all of 39 activities: the 27 topic quizzes, the exam, the five module projects, Capstone 1, the defence, the live-coding assessment, the practice checkpoint, the Getting Hired checklist and the certificate. Pages load from `lms/moodle/import/pages/` and `lms/moodle/import/{capstones,guides}/phase-2/`. Both Phase 1 (id 4) and Phase 2 (id 5) were made visible on 24 October 2026. The site requires login. Enrolment is **gated** (since 28 October 2026). Learners join in one of two ways:

- **Enrolment key.** Self enrolment now needs the course's enrolment key: a random key per course, shown in *Participants → Enrolment methods → Enrolment key*. The key is the instant route.
- **Approval.** Learners without a key ask through *Contact site support* (which emails admin@itlearning.tech). You approve them by adding them to the cohort **Approved learners: Phase 1** or **Phase 2** (*Site administration → Users → Cohorts*). A cohort-sync enrolment method enrols cohort members as students automatically.

Each course summary, which is shown on the enrolment page, explains both routes. The approval plugin `enrol_apply` was not used: it supports Moodle only up to 4.1. Email sign-up has reCAPTCHA, manual enrolment stays available and guest access is off.

**Monitoring, alerting and backups:**

- Prometheus and Grafana on the Moodle server watch both servers, the websites, mail, the database, Moodle cron and the mail queue.
- 27 alert rules, including p90 latency rules, email admin@itlearning.tech and saamsoftsystems@gmail.com.
- A verified backup of the database, moodledata and configuration runs every night at 01:30 UTC; the newest 7 are kept.

See [ops/monitoring](../../ops/monitoring/README.md).

**Multi-factor authentication** (Moodle's built-in `tool_mfa`, on since 28 October 2026):

- **Who needs it:** administrators and the manager, course creator, teacher and non-editing teacher roles. They need an authenticator app (TOTP) or an email code. Students pass on the *Roles* factor and are never asked.
- **Grace period:** staff had 7 days to set up an authenticator app (*Preferences → Multi-factor authentication preferences*), after which Moodle takes them to that page.
- **If an administrator is locked out:** on the server, run `docker exec -u www-data moodle php admin/cli/cfg.php --component=tool_mfa --name=enabled --set=0`, log in, fix the factor, then set it back to 1.

## 6. Updating content

Edit the source in the repository (`lesson.md`, `cbt.json`, `lms/videos.json`, `assessments/private/...`), run `python scripts/build.py`, `python scripts/check_repo.py` and `python scripts/moodle/build_moodle.py`, then replace the Page content and re-import changed question banks (import into the same category, then swap questions in the quiz). Run `python lms/check_videos.py --quick` monthly to catch removed videos.
