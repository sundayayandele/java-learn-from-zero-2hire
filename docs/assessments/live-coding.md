# Timed live-coding assessment

← [Home](../../README.md) · [Practice track](../../practice/README.md)

Every phase ends with a **90-minute live-coding assessment** in the format junior Java interviews use: one algorithm problem and one small change to a real Spring Boot service, done while explaining your thinking. Multiple-choice quizzes check what you recognise; this checks what you can produce under time pressure.

## Format

| Part | Time | Task | Phase 1 | Phase 2 |
| --- | --- | --- | --- | --- |
| A. Algorithm | 30 min | One problem from the [practice track](../../practice/README.md) patterns, new to you, with tests to make pass | Arrays, hashing, two pointers, sliding window, strings, stacks, sorting | Any pattern up to week 24, including trees, graphs and dynamic programming |
| B. Service change | 60 min | A small, unfamiliar Spring Boot 4 service with a ticket: implement it with tests | Endpoint with validation, persistence and Problem Details | Secured endpoint with ownership checks, or an integration with timeouts and WireMock tests |

- Taken on a video call with a mentor (screen shared) or as a proctored recording, in your own IDE.
- Closed AI. Official documentation (dev.java, docs.oracle.com, docs.spring.io) is allowed, as in many real interviews.
- The task repository is given at the start; push your result to a branch and submit the link and recording on Moodle.
- One retake after 7 days if you don't pass, with a different task.

## Rubric (15 points, pass 10, no criterion at "Not yet")

| Criterion | Not yet (1) | Competent (2) | Strong (3) |
| --- | --- | --- | --- |
| Problem solving | Starts coding without understanding; stuck without a plan | Clarifies, gives a working approach with its complexity | Starts from brute force, improves it, handles edge cases unprompted |
| Correctness | Doesn't compile or most tests fail | Required tests pass | All tests pass, including edge cases the candidate added |
| Code quality | Hard to read; money as `double`; no structure | Clear names, small methods, idiomatic Java and Spring | Reads like the requirements; good use of the standard library |
| Testing | No tests added | Tests for the main behaviour | Tests for failure paths and boundaries, written early |
| Communication | Silent or unclear | Explains decisions when asked | Thinks aloud, asks good questions, manages time |

## How to prepare

- Do the practice track weekly, timed, and say your approach out loud before coding.
- Practise Part B twice with a peer: one writes a tiny ticket for the other's capstone, the other implements it in 60 minutes.
- Know your keyboard shortcuts and how to run one test fast (`./mvnw test -Dtest=ClassName#method`).
