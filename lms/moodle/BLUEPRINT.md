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
| Restrict access | All 29 topic quizzes passed (activity completion) |

Add all 60 questions. The exam samples every topic (blueprint in `phases/01-foundations/exam/README.md`).

### Capstone assignments

One assignment per capstone: online text submission (repository URL, deployed health URL, short demo video link); grade **20 points via a rubric** with the five criteria in `phases/01-foundations/capstones/README.md` (each 1–4); grade to pass **14**; completion "receive a passing grade". Grader rule: if any criterion scores 1, override the grade to 13 and say which criterion to fix (Moodle can't enforce "no criterion below 2").

### Course completion and certificate

Completion requires: all topic quizzes passed, Module project assignments submitted, Capstone 1 plus two other capstone assignments passed, and the exam passed. Issue the ITLearning Phase 1 certificate (`mod_customcert`) on course completion.

## 5. Status on itlearning.tech (course id 4)

Done (24 October 2026):

- 29 topic **pages** (lesson text, embedded YouTube videos, official references) placed before their quizzes; completion on view.
- 29 topic **quizzes** with all 211 questions; pass mark 7/10 (70%), immediate feedback, completion on passing grade.
- **End-of-phase exam** quiz: 60 questions, 120 minutes, 2 attempts, pass mark 70%.
- Question bank (qbank activity, cmid 171): topic bank and exam bank imported.
- Section 6: capstones overview page plus a page per capstone (brief, architecture diagrams, step-by-step build guide).

Still to do by hand: capstone and module-project assignments with the rubric, exam extras (48-hour delay between attempts, shuffled order, review options, restrict access to "all topic quizzes passed"), the "start here" page and forum, Getting Hired URL and checklist assignment, and the certificate. Lesson and capstone pages load from `lms/moodle/import/pages/` and `lms/moodle/import/capstones/` (regenerate with `build_moodle.py`, then replace a page's content to update it).

## 6. Updating content

Edit the source in the repository (`lesson.md`, `cbt.json`, `lms/videos.json`, `assessments/private/...`), run `python scripts/build.py`, `python scripts/check_repo.py` and `python scripts/moodle/build_moodle.py`, then replace the Page content and re-import changed question banks (import into the same category, then swap questions in the quiz). Run `python lms/check_videos.py --quick` monthly to catch removed videos.
