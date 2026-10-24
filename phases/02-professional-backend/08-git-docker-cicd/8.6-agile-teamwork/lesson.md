## What you will be able to do

- Work in Scrum and Kanban teams: sprints, backlog refinement, stand-ups, reviews, retrospectives and WIP limits.
- Turn a request into a well-formed ticket: a user story with acceptance criteria, a definition of ready and a definition of done.
- Estimate with story points or t-shirt sizes, split large stories, and raise blockers early.
- Write bug reports that get fixed fast, and give a clear stand-up update.
- Explain how your own capstone work maps to tickets, pull requests and releases.

## Why this matters in a bank or fintech

Nigerian fintechs mostly run Scrum or Kanban with Jira, Linear or Azure Boards; banks often run "Scrum inside a change-management process" with release windows, approvals and audit trails. As a junior you will be judged as much on how you work in the team as on your code: Do you understand a ticket before starting? Do you say early when you're stuck? Are your bug reports reproducible? Interviewers ask "Tell me how you would pick up a ticket", "How do you estimate?", "What do you do when you're blocked?" and "Describe a bug report you wrote". This lesson gives you the vocabulary and habits, and Module 8's team project lets you practise them.

## 1. Scrum and Kanban in one page

**Scrum** works in fixed **sprints** (usually two weeks):

| Event | Purpose | Your part as a junior |
| --- | --- | --- |
| Sprint planning | Choose sprint backlog items and a sprint goal | Ask questions until you understand the tickets you'll take |
| Daily stand-up (15 min) | Coordinate and surface blockers | Short update, blockers first |
| Backlog refinement | Clarify and estimate upcoming items | Ask about edge cases and acceptance criteria |
| Sprint review | Demo what's done to stakeholders | Demo your feature with real data |
| Retrospective | Improve how the team works | Bring one concrete observation and one suggestion |

Roles: the **product owner** decides what is most valuable; the **Scrum master** helps the team remove obstacles; the **developers** (you) decide how to build it and own quality.

**Kanban** has no sprints: work flows through columns (To do → In progress → Review → Test → Done) with **WIP limits** (for example at most three items in Review), so the team finishes work before starting more. Operations and platform teams often use Kanban; product teams often use Scrum. Many teams mix them.

## 2. A good ticket

```text
Title: Customer can reverse a transfer within 24 hours

As a retail customer
I want to reverse a transfer I sent by mistake within 24 hours
So that I can recover money sent to the wrong account

Acceptance criteria (Given / When / Then):
1. Given a transfer of ₦20,000 posted 2 hours ago and the beneficiary still holds ₦20,000,
   When I request a reversal, Then both balances are restored and a reversal journal references the original.
2. Given a transfer posted 25 hours ago, When I request a reversal, Then I get 422 "reversal-window-expired".
3. Given a transfer already reversed, When I request a reversal, Then I get 409 "already-reversed".
4. Every reversal is recorded in the audit log with the customer and the reason.

Out of scope: partial reversals; reversals of inter-bank (NIP) transfers.
Notes: API contract PR #142; fee is refunded (confirmed by product, 21 Oct).
```

Acceptance criteria become your tests (topic 7.1): write them as automated tests first and the ticket is done when they pass.

- **Definition of ready**: the team understands the ticket, it has acceptance criteria and no unresolved dependencies, and it's small enough for one sprint.
- **Definition of done**: code reviewed and merged, tests (including the acceptance criteria) green in CI, quality gates passing, documentation and API contract updated, deployed to the test environment, product owner accepted. Your team's version is written down; follow it.

## 3. Estimation and splitting

Teams estimate relative size, not hours: **story points** (1, 2, 3, 5, 8, 13) or **t-shirt sizes** (S, M, L). In **planning poker** everyone reveals an estimate at once; big differences are useful because they reveal a misunderstanding ("I assumed the provider supports refunds", "I forgot the migration").

As a junior:

- Estimate honestly, including tests, review and documentation, not just "writing the code".
- Split anything above about 5 points or two days. Split by behaviour, not by layer: "reversal within window" and "reject expired reversal" are better slices than "database part" and "API part", because each slice delivers something testable.
- Re-estimate when you learn something big, and say so at stand-up.

## 4. Stand-ups and blockers

A good update takes 30 seconds:

```text
Yesterday: finished the reversal service and its unit tests.
Today: the API endpoint and the Testcontainers test for the 25-hour case.
Blocker: I need the fee-refund rule confirmed by product; I asked Ada at 10:00 and will follow up at 14:00.
```

Raise a blocker when you have been stuck for about half a day on the same problem after trying the obvious things (topic 2.6's method). Say what you've tried. Asking early is a strength; silently losing three days is the most common junior failure.

## 5. Bug reports that get fixed

```text
Title: Statement closing balance wrong when a reversal is in the period

Environment: staging, build 2026.10.24-3 (commit a1b2c3d)
Steps to reproduce:
1. POST /api/v1/transfers (₦20,000 from 0123456789 to 0987654321), reference TRF-TEST-1
2. POST /api/v1/transfers/TRF-TEST-1/reversal
3. GET /api/v1/accounts/0123456789/statement?from=2026-10-24&to=2026-10-24
Expected: closing balance = opening balance (transfer and reversal cancel out)
Actual: closing balance is ₦20,026.88 lower (fee not refunded in the statement)
Evidence: response body attached; correlation id 9f2c...; log lines attached
Severity: high (customer-visible balance); Frequency: every time
```

Include: environment and version, exact steps, expected versus actual, evidence (correlation id, logs, screenshots, no personal data), severity and frequency. A failing test that reproduces the bug is the best attachment of all.

## 6. Tickets, branches, pull requests and releases

Connect your work so anyone can trace it (topic 8.1):

- Branch name with the ticket key: `feature/ITL-142-transfer-reversal`.
- Commit messages and PR titles reference the key: `ITL-142: reject reversals after 24 hours`.
- The PR description links the ticket and lists how each acceptance criterion is tested.
- The ticket moves to Review when the PR opens, and to Done only when the definition of done is met.
- In banks, a **change request** (CAB approval) may be needed before a production release; your traceable tickets and PRs are the evidence auditors look at.

## 7. Practising this in the programme

Module 8's [team project](../README.md#module-projects) runs as a **two-week mini-sprint** with a peer or small group: a board with at least six tickets written in the format above, estimates from planning poker, short daily async stand-up notes in the repository's discussion or chat, PRs linked to tickets, a demo, and a one-page retrospective. Submit the board link and the retrospective with your Module 8 project.

## Common mistakes

- Starting to code before understanding the acceptance criteria.
- "It's done" when it works on your laptop but isn't reviewed, merged, tested in CI or deployed.
- Estimating only the coding time.
- Splitting stories by layer, so nothing is testable until the last piece lands.
- Waiting days before mentioning a blocker.
- Bug reports that say "it doesn't work" with no steps, version or evidence.
- Stand-up updates that are long status reports instead of coordination.

## Practice

1. Rewrite three features from your capstone brief as user stories with Given/When/Then acceptance criteria.
2. Split one large story ("customers can manage beneficiaries") into at least four independently testable stories.
3. Write a bug report in the format above for a real defect you found in your capstone or in the Phase 2 labs.
4. Run the Module 8 mini-sprint: board, estimates, stand-up notes, demo and retrospective.
5. Prepare a 60-second answer to "Walk me through how you take a ticket from the backlog to production."

## Key terms

Agile, Scrum, sprint, sprint goal, product owner, Scrum master, backlog, refinement, stand-up, sprint review, retrospective, Kanban, WIP limit, user story, acceptance criteria, Given/When/Then, definition of ready, definition of done, story points, planning poker, t-shirt sizing, vertical slicing, blocker, bug report, severity, change request, CAB, traceability.

## Further reading

- [The Scrum Guide (2020)](https://scrumguides.org/scrum-guide.html)
- [Atlassian: Kanban](https://www.atlassian.com/agile/kanban)
- [Atlassian: User stories with examples and a template](https://www.atlassian.com/agile/project-management/user-stories)
- [Mountain Goat Software: Planning poker](https://www.mountaingoatsoftware.com/agile/planning-poker)
- [Martin Fowler: Given When Then](https://martinfowler.com/bliki/GivenWhenThen.html)
- [Mozilla: Bug writing guidelines](https://bugzilla.mozilla.org/page.cgi?id=bug-writing.html)
