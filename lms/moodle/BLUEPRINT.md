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
| Format | Custom sections (`topics`), 9 sections |
| Visibility | Hidden until reviewed |
| Completion tracking | On; show activity dates and completion conditions; **relative dates** (each learner's dates count from their enrolment) |
| Groups | One group per cohort (separate groups) |

## 4. Sections and activities

| # | Section | Activities |
| --- | --- | --- |
| 1 | Phase 1: start here | URL: Phase 1 plan; Page: how the course works (read → watch → CBT → practice → project); Forums: questions and help, cohort lounge; URL: weekly office hour |
| 1a | Week 0: programming on-ramp (optional) | **Quiz: entry check** (15 questions, 12 to skip the on-ramp); topics 0.1–0.3: Page + Quiz |
| 2 | Module 1: Modern Java & OOP (weeks 1–5) | For each topic 1.1–1.15 (1.13–1.15 optional): **Page** + **Quiz**; URL: Module 1 projects; Assignment: Module 1 project submission |
| 3 | Module 2: Concurrency & JVM (weeks 6–7) | Topics 2.1–2.6 (2.5 optional): Page + Quiz; URL: projects; Assignment: project 2.C `FINDINGS.md` |
| 4 | Module 3: Databases (weeks 8–10) | Topics 3.1–3.6: Page + Quiz; URL: projects; Assignments: 3.B ledger schema, 3.D `TUNING.md` |
| 5 | Module 4: Spring Boot 4 (weeks 11–14) | Topics 4.1–4.9 (4.9 optional): Page + Quiz; URL: projects; Assignments: 4.A, 4.D `UPGRADE.md` |
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
| Restrict access | All 31 core topic quizzes attempted (activity completion); the on-ramp and optional topics aren't required |

The import creates one sub-category per module under *End-of-phase exam*. Add **random questions** from each module category (Phase 1: 20, 12, 14 and 14; Phase 2: 15, 13, 12, 11 and 9), so every attempt draws a different 60 from the bank of 200 with fixed module weights (blueprints in each phase's `exam/README.md`).

### Capstone assignments

One assignment per capstone: online text submission (repository URL, deployed health URL, short demo video link); grade **20 points via a rubric** with the five criteria in `phases/01-foundations/capstones/README.md` (each 1–4); grade to pass **14**; completion "receive a passing grade". Grader rule: if any criterion scores 1, override the grade to 13 and say which criterion to fix (Moodle can't enforce "no criterion below 2"). In every rubric tick **"Calculate grade having a minimum score of the minimum achievable grade for the rubric"**: the levels start at 1, and with the option unticked Moodle maps the lowest possible total to a grade of 0, so a rubric total of 14/20 would be recorded as 12 and fail.

### Course completion and certificate

Completion requires: all core topic quizzes attempted, Module project assignments submitted, Capstone 1 plus two other capstone assignments passed, and the exam passed. Issue the ITLearning Phase 1 certificate (`mod_customcert`) on course completion.

## 5. Status on itlearning.tech (course id 4)

Done (24 October 2026):

| Section | Live activities |
| --- | --- |
| 1 Start here | Page: how the course works and the Phase 1 plan; Forum: Questions and help |
| 1 Start here | URL: coding practice track (auto-graded Java and SQL problems); **Coding practice checkpoint** assignment (online text, 10 points, pass 7) |
| 2–5 Modules 1–4 | 35 lesson pages (text, embedded YouTube videos, official references; completion on view; design patterns, debugging and Spring under the hood added on 24 October 2026, Git, JUnit and HTTP basics on 29 October 2026), plus 3 on-ramp pages in week 0, each before its topic quiz (281 questions in all; pass mark 7/10 shown as a target, unlimited attempts, immediate feedback; complete after one finished attempt at any score); per module a **project submission** assignment (online text, rubric from the module's assessment table: five criteria scored Not yet 1 / Competent 2 / Strong 3, 15 points, pass 10, resubmit until pass) |
| 6 Capstones | Overview and rubric page; per capstone a page (brief, five architecture diagrams, step-by-step guide) followed by its **submission assignment** (online text, 20 points, pass 14, unlimited resubmission until pass, rubric with five criteria scored 1–4, "minimum achievable grade" option ticked so the grade equals the rubric total); **Capstone defence (viva)** assignment (15-point rubric from `docs/assessments/capstone-defence.md`, pass 10); deployment and CI/CD guide pages |
| 7 End-of-phase assessments | Exam information page; exam quiz: **60 random questions per attempt from the 200-question bank** (20/12/14/14 by module; 67/40/47/46 in the pools), shuffled, 5 per page, 120 minutes, 2 attempts with 48 hours between them, pass 70%, review shows marks only (answers and feedback only after a close date, if one is set), available only after all 32 topic quizzes are attempted; **Timed live-coding assessment** assignment (15-point rubric from `docs/assessments/live-coding.md`, pass 10) |
| 8 Getting hired | Getting hired page with videos; URLs: Oracle certification map, Java fundamentals revision sheet; Getting Hired checklist assignment (online text, ungraded, completion on submit); **Phase 1 certificate** (`mod_customcert`, A4 landscape, name, issue date, verification code and QR; issued and emailed automatically; available only when all 32 topic quizzes are attempted and the exam, the four module projects, Capstone 1 and any two other capstones (36 OR-ed pairs), the defence, the live-coding assessment and the practice checkpoint are passed) |

**Course completion**: all of 42 activities: the 31 core topic quizzes, the exam, the four module projects, Capstone 1, the defence, the live-coding assessment, the practice checkpoint, the Getting Hired checklist and the certificate (whose restriction enforces the "two other capstones" rule). Pages load from `lms/moodle/import/{pages,capstones,guides}/` (regenerate with `build_moodle.py`, then replace a page's content to update it).

CodeRunner 5.10.6 (with its behaviour plugin 1.4.8) was installed on 24 October 2026. It runs code on our own Jobe server at `https://jobe.itlearning.tech` (Java 21, reachable only from the Moodle server) and passed the smoke and load tests; see [CODERUNNER.md](CODERUNNER.md). Each course's Start here section has an optional **Coding practice quiz** of 15 auto-graded Java problems from the practice track (Phase 1 cmid 379, Phase 2 cmid 380; pass 7/10, unlimited attempts). The coding practice checkpoint assignment still covers the whole track, including the SQL drills, which stay graded by JUnit and Testcontainers on the learner's machine.

### Phase 2 on itlearning.tech (course id 5, `JAVA-Z2H-P2`)

Done (24 October 2026), built with the same settings as Phase 1 (each activity's settings form was cloned from its Phase 1 counterpart):

| Section | Live activities |
| --- | --- |
| 1 Start here | Page: how the course works and the Phase 2 plan; Forum: Questions and help; URL: coding practice track; **Coding practice checkpoint** assignment (10 points, pass 7) |
| 2–6 Modules 5–9 | 27 lesson pages (text, embedded videos, references; completion on view; topics 5.6 system design basics and 8.6 agile teamwork added on 24 October 2026) each before its topic quiz (192 questions; pass mark 7/10 shown as a target, unlimited attempts, immediate feedback; complete after one finished attempt at any score); per module a **project submission** assignment with a five-criterion rubric from the module's assessment table (15 points, pass 10); per module a URL to its **broken-service lab** (5.E, 6.D, 7.D, 8.D, 9.D) |
| 7 Capstones | Overview and rubric page; per capstone a page (brief, five architecture diagrams, step-by-step guide) and a **submission assignment** (online text, **24 points, pass 17**, unlimited resubmission until pass, six-criterion rubric scored 1–4); **Capstone defence (viva)** assignment (15-point rubric, pass 10); deployment (with identity provider) and CI/CD quality-gates pages |
| 8 End-of-phase assessments | Exam information page; exam quiz: **60 random questions per attempt from the 200-question bank** (15/13/12/11/9 by module; 50/43/40/37/30 in the pools), shuffled, 5 per page, 120 minutes, 2 attempts 48 hours apart, pass 70%, available only after all 27 topic quizzes are attempted. The bank was uploaded directly from the private assessments repository; **Timed live-coding assessment** assignment (15-point rubric, pass 10) |
| 9 Getting hired | Getting hired page with videos; URLs: certification map, Java fundamentals revision sheet; Getting Hired checklist assignment; **Phase 2 certificate** (`mod_customcert`, same A4 landscape design as Phase 1; available when all 27 topic quizzes are attempted and the exam, the five module projects, Capstone 1 and **any one** other capstone (an OR of nine), the defence, the live-coding assessment and the practice checkpoint are passed) |

**Course completion**: all of 39 activities: the 27 topic quizzes, the exam, the five module projects, Capstone 1, the defence, the live-coding assessment, the practice checkpoint, the Getting Hired checklist and the certificate. Pages load from `lms/moodle/import/pages/` and `lms/moodle/import/{capstones,guides}/phase-2/`. Both Phase 1 (id 4) and Phase 2 (id 5) were made visible on 24 October 2026. The site requires login. Enrolment is **gated** (since 28 October 2026). Learners join in one of two ways:

- **Enrolment key.** Self enrolment now needs the course's enrolment key: a random key per course, shown in *Participants → Enrolment methods → Enrolment key*. The key is the instant route.
- **Approval.** Learners without a key ask through *Contact site support* (which emails admin@itlearning.tech). You approve them by adding them to the cohort **Approved learners: Phase 1** or **Phase 2** (*Site administration → Users → Cohorts*). A cohort-sync enrolment method enrols cohort members as students automatically.

Each course summary, which is shown on the enrolment page, explains both routes. The approval plugin `enrol_apply` was not used: it supports Moodle only up to 4.1. Email sign-up has reCAPTCHA, manual enrolment stays available and guest access is off.

**Topic quizzes are pacing aids** (since 29 October 2026, review item 4): their answers are in the public topic READMEs, so passing them proves little. Each counts as complete after one finished attempt at any score (quiz rule "minimum attempts: 1"; grade-based completion would store a low score as "complete, failed", which the exam restriction rejects). The 7/10 pass mark stays as feedback. The exam and certificate need every topic quiz attempted. The private-bank exam, the capstone defence and the live-coding assessment are the real checks. Capstone reference solutions moved to the private assessments repository (`capstone-references/`); mentors share one after the learner's submission passes.

**Mentor booking** (since 29 October 2026, review item 5): the Scheduler plugin (`mod_scheduler` v5.2-r1, commit 8a05f853, built into the image under `plugins/mod/scheduler`) gives each course two booking activities, each just above the assessment it serves:
- *Book your capstone defence*: 30-minute slots, carrying the viva's "Capstone 1 passed" restriction (cm 381 and 383);
- *Book your live-coding assessment*: 90-minute slots (cm 382 and 384).

In both, learners hold one booking at a time and can't book within 24 hours of a slot. Mentors are Non-editing teachers and publish their own slots weekly; the lead mentor (Editing teacher) manages every booking. The start-here pages promise grading within 5 working days. The mentor process (turnaround, calibration samples, escalation, onboarding) is in the private repository: `mentors/mentor-guide.md`.

**Learner journey test** (`scripts/moodle/journey_test.php`, run on 29 October 2026): it creates test learners and walks Phase 1 end to end through Moodle's APIs, from sign-up to course completion and the certificate. Everything passed. Re-run it before each cohort. The certificates' scope text was updated to the current requirements (32 or 27 topic quizzes, viva, live coding, checkpoint; Phase 2 needs two capstones). The capstone defence now requires Capstone 1 passed.

**Curriculum and pacing changes** (since 29 October 2026, review items 8–12; full backup `20261029-1110` taken first):

- **Week 0 on-ramp** (Phase 1 section 2): an *Entry check* quiz (15 questions; 12 or more means skip, with feedback saying so; not required for anything) and topics 0.1–0.3 for complete beginners. Not part of the exam, the certificate or course completion.
- **New topics, renumbered:** 1.2 Git and GitHub essentials, 1.6 Unit testing with JUnit, 4.2 HTTP and REST basics. Module 1 is now 1.1–1.14 over weeks 1–5 and Module 4 is 4.1–4.9; everything after week 4 moved one week later, so Phase 1 runs weeks 0–14 and Phase 2 weeks 15–25. Existing activities, question categories and question names were renamed in place, so learner data stays attached.
- **Optional topics:** 1.13 Kotlin, 1.14 Localization, 2.5 Modules and jlink, 4.9 Spring Boot with Kotlin, and (from item 17) 1.15 Java 22 to 25. Their names say *(optional)*; they were removed from the exam and certificate restrictions and from course completion. Their 11 exam questions moved to *End-of-phase exam / Retired (optional topics)*, outside the random draw.
- **Exam banks:** 200 questions per phase (Phase 1: 91 new, Phase 2: 80 new), imported into the existing module categories, so the random-question slots needed no change. After the first cohort, run *Results → Statistics* on each exam and retire questions with a facility above about 90% or a discrimination index below about 0.2.
- **Pacing:** the site's *course relative dates* setting is on and both courses use it. Every graded activity has an expected-completion date for the end of its plan week, which each learner sees counted from their own enrolment date (in the timeline and on the course page).
- **Weekly plan messages:** `scripts/moodle/weekly/` (installed with its `install.sh`) runs `itl-weekly-plan.timer` every Monday at 06:00 UTC. Each active learner gets that week's topics and milestone, how many core topic quizzes they're behind, a nudge after 7 days without a visit, and the office hour link once it's visible. Editing teachers, and non-editing teachers for their groups, get a digest of learners silent for 14 days or more. Messages use the core *Course content changes* notification. Grafana alerts if a run fails or nothing has gone out for 8 days.
- **Community:** a *Cohort lounge* forum in each start-here section, and a **hidden** *Weekly office hour (Google Meet)* link with a placeholder URL and the text "Wednesday, 19:00–20:00 (Lagos time)". Set the real Meet link and time, then make it visible.
- **Run Moodle CLI scripts as `www-data`** (`docker exec -u www-data moodle php ...`). Scripts run as root earlier had left 9,462 root-owned files in moodledata (including `temp/backup`, which activity duplication needs); they were given back to www-data on 29 October 2026.

**Items 15–22 of the review** (29 October 2026):

- **Repository name:** every stored link (1,261, in pages, URL activities, assignment and quiz descriptions, rubrics, calendar events and question feedback) now points at `sundayayandele/java-learn-from-zero-2hire`, as do the build scripts.
- **Topic 1.15 *Java 22 to 25*** (optional, week 5) added with its quiz; Phase 1 now has 36 topics, 5 of them optional.
- **Graduate survey** (Phase 2, *Getting hired* section): a Feedback activity, not anonymous, several submissions allowed, 7 questions (situation, job title, employer type, time to first offer, how much the programme helped, one improvement). The Monday job invites graduates 3 and 6 months after completing Phase 2; `scripts/moodle/metrics/funnel.php` counts a "job" when the situation is a tech job, internship or freelance work. The survey intro tells learners how their answers are used; mention it in the privacy notice at its next revision.
- **Accessibility:** `scripts/check_accessibility.py` (run in CI by `check_repo.py`) checks every published page for image alt text, iframe titles, vague or empty link text, skipped heading levels and tables without headers; all 98 pages pass. Every video has English captions (`lms/check_videos.py`). Moodle's own Accessibility toolkit (`tool_brickfield`) isn't registered; registering it at *Site administration → Plugins → Admin tools → Accessibility toolkit* is free but means accepting Brickfield's terms, so that is the site owner's call.
- **Mobile app:** CodeRunner has no Moodle app support, so the app can't run coding questions. The start-here pages and both *Coding practice quiz* descriptions tell learners to open those in a browser; lessons, topic quizzes, forums and messages work in the app.

**Privacy and policies** (since 29 October 2026):

- **Policies:** a privacy notice and terms of use (texts in [policies/](policies/)) are compulsory in Moodle's policies tool. New learners accept them at sign-up and existing users at their next sign-in. Anyone can read them at `/admin/tool/policy/viewall.php`. The operator is ITLearning, a subsidiary of SaamSoft Systems Nig Ltd, Lagos; privacy requests go to info@itlearning.ng.
- **Age:** learners must be 16 or over. Digital age of consent is checked at 18, so under-18s can't self-register: they're told to contact support, and staff create the account after confirming they're 16 or over and have a parent's or guardian's consent.
- **Privacy officer:** the contact form is on; with no data protection officer role assigned, messages go to the site admin.
- **Retention:** activity logs are kept for 365 days. The data registry keeps learner records for 3 years after the end of their context (for a user, their last sign-in), then flags them for deletion with privacy officer approval.
- **Review needed:** the texts were drafted from the platform's actual data flows, but have not been reviewed by a lawyer. Have them reviewed, and update them whenever data flows change (a new plugin, provider or server).

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
