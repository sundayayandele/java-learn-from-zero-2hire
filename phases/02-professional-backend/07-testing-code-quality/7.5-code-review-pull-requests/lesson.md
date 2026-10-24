## What you will be able to do

- Prepare a **pull request** that is easy to review: small, focused, well described, with test evidence.
- **Review** someone else's code systematically, using a checklist for correctness, security, money handling, tests and readability.
- Write review comments that are **specific, kind and actionable**, and receive feedback without defensiveness.
- Use **conventions** (comment prefixes, required checks, CODEOWNERS) that make reviews fast in a team.

## Why this matters in a bank or fintech

In regulated organisations every change to production code is reviewed: it is a control auditors check ("four-eyes principle"), and it is how knowledge spreads in a team. Your first months as a junior engineer will be judged largely through your pull requests: how easy they are to review and how well you respond to feedback. Being a good reviewer is also how juniors become trusted mid-level engineers. Interviewers sometimes give you a diff and ask "what would you comment on?"

## 1. Preparing a pull request

### Keep it small and focused

Review quality drops sharply as size grows: reviewers skim large diffs. Aim for changes a reviewer can read in 15–30 minutes (roughly under 400 changed lines of real code). Split work into steps that each make sense alone: a migration, then the repository, then the endpoint; or a pure refactor PR followed by the behaviour change. Never mix formatting changes with logic changes.

### Write a description that answers the reviewer's questions

```markdown
## What
Adds idempotency to POST /api/v1/transfers using the Idempotency-Key header (ADR-0007).

## Why
Mobile retries after timeouts created duplicate transfers (incident INC-1432).

## How
- New table idempotency_key (V12 migration); key inserted first under a PK, response stored in the same transaction.
- Same key + same payload → stored response; same key + different payload → 422.

## Testing
- Unit tests for the hashing and replay logic.
- IT: two concurrent identical requests create one transfer (TransferIdempotencyIT).
- Manual: curl script in docs/manual-tests/idempotency.md.

## Risks / rollout
Migration is additive. Old clients without the header get 400 after 2026-11-15 (announced); until then the header is optional.

Closes #214
```

### Review your own diff first

Before requesting review, read the diff on GitHub as if someone else wrote it. Remove debug logs, commented-out code, unrelated changes and TODOs without tickets. Make sure CI is green; reviewers should not have to find what the pipeline could.

## 2. Reviewing: what to look for

Work from the most important to the least:

1. **Does it do the right thing?** Read the ticket and description first. Is the behaviour correct, including edge cases (zero, negative, boundary amounts, empty lists, time zones, month-end)?
2. **Money and data integrity:** `BigDecimal` with an explicit rounding policy; transactions around multi-step updates; locking and idempotency for money movement; no lost updates.
3. **Security:** authorisation on every new endpoint, including object ownership (topic 6.3); input validation; no secrets, PII or tokens in code or logs; parameterised SQL.
4. **Failure handling:** timeouts and retries on external calls; meaningful Problem Details; no swallowed exceptions; behaviour when the database or a provider is down.
5. **Tests:** do they test the new behaviour and its failure modes? Would they fail if the code were wrong? Are they readable?
6. **Design and readability:** clear names; small methods; code in the right layer; no premature abstractions; consistent with existing patterns.
7. **Operations:** logs, metrics and alerts for new flows; migration safety (locks on large tables, backward compatibility during rolling deploys); configuration documented.
8. **Style:** only what the formatter and linters do not already enforce.

Read the tests early: they tell you what the author thinks the code does.

### A banking-flavoured checklist

- [ ] Amounts are `BigDecimal`/minor units, never `double`; rounding is explicit.
- [ ] Every new endpoint has authentication, authorisation and an ownership check, with negative tests.
- [ ] Database changes are in a new Flyway migration (never edit an applied one) and are backward compatible.
- [ ] External calls have timeouts; retries only for idempotent operations.
- [ ] No secrets, card numbers, BVNs or tokens in code, tests, fixtures or logs.
- [ ] Errors return documented Problem Details types.
- [ ] Tests cover boundaries and failure paths, not only the happy path.

## 3. Writing review comments

Good comments are **about the code, specific, and actionable**, and they say how important they are. Many teams use prefixes (from the "Conventional Comments" convention):

| Prefix | Meaning |
| --- | --- |
| `issue:` / `blocking:` | Must be fixed before merge |
| `suggestion:` | Would improve the code; author decides |
| `question:` | I don't understand; please explain (maybe in the code) |
| `nit:` | Minor, optional |
| `praise:` | Something done well, worth saying |

Compare:

- Weak: "This is wrong."
- Better: "**issue:** if two requests with the same Idempotency-Key arrive together, both can pass the `findByKey` check before either inserts, so the transfer runs twice. Could we insert the key first and rely on the primary key, as in ADR-0007?"

- Weak: "Why didn't you use streams here?"
- Better: "**nit:** this loop could be `transactions.stream().map(Transaction::amount).reduce(ZERO, BigDecimal::add)`; fine either way."

Guidelines:

- Comment on the code, not the person ("this method", not "you").
- Explain *why*, and link to a standard, ADR or document when there is one.
- Ask questions when you are unsure; you may be the one who learns something.
- Approve when the remaining comments are optional; do not block on preferences.
- If a discussion goes back and forth more than twice, talk (call or chat) and record the outcome in the PR.
- Respond within a working day. Slow reviews slow the whole team.

## 4. Receiving feedback

- Assume good intent; the reviewer is protecting the codebase and the customers, not attacking you.
- Reply to every comment: fix it ("Done in a1b2c3"), explain your reasoning, or agree to follow up with a ticket.
- Push fixes as new commits during review so reviewers can see what changed; squash or tidy when merging if your team prefers.
- If you disagree, explain with evidence (a test, a benchmark, a link), and accept the team's decision.
- Thank reviewers who caught real problems; they saved you an incident.

## 5. Team conventions that speed reviews up

- **Pull-request templates** (`.github/pull_request_template.md`) with What/Why/How/Testing/Risks sections.
- **CODEOWNERS** so the right people are requested automatically (for example the security team for `security/**`, DBAs for `db/migration/**`).
- **Required status checks** and **required approvals** on `main` (topic 8.1).
- **Automated formatting and linting** so humans never discuss style.
- **Draft pull requests** for early feedback on direction.
- **Small, frequent merges** behind feature flags instead of long-lived branches.

## 6. AI tools in review

AI assistants can summarise a large diff, suggest missing tests, or flag suspicious patterns, and some platforms post automated review comments. Treat them as an extra reviewer, not the reviewer: they miss business context and can be confidently wrong. The human approval remains the control (topic 9.2 covers reviewing AI-generated code itself).

## Common mistakes

- 2,000-line pull requests that mix refactoring, formatting and features.
- Empty descriptions ("fixes stuff") and no test evidence.
- Requesting review before CI is green.
- Reviewers who approve without reading ("LGTM") or who block on personal style.
- Vague or harsh comments with no suggestion.
- Arguing in comments for days instead of talking.
- Force-pushing over review history so reviewers cannot see what changed.

## Practice

1. Take your last capstone feature and split it into a sequence of three to five pull requests that each make sense alone; write the description for each using the template above.
2. Review a classmate's pull request (or one from an open-source Spring project) using the checklist, writing at least five comments with prefixes, including one `praise:`.
3. Add a pull-request template and a CODEOWNERS file to your capstone repository.
4. Find a merged pull request in an open-source Java project with a long review discussion; summarise what changed because of the review.

## Key terms

Pull request, merge request, four-eyes principle, code review, reviewer, author, diff, pull-request template, self-review, review checklist, Conventional Comments, blocking comment, nit, approval, request changes, CODEOWNERS, required status checks, draft pull request, feature flag, squash merge.

## Further reading

- [Google Engineering Practices: How to do a code review](https://google.github.io/eng-practices/review/reviewer/)
- [Google Engineering Practices: The CL author's guide](https://google.github.io/eng-practices/review/developer/)
- [Conventional Comments](https://conventionalcomments.org/)
- [GitHub Docs: About pull request reviews](https://docs.github.com/en/pull-requests/collaborating-with-pull-requests/reviewing-changes-in-pull-requests/about-pull-request-reviews)
- [GitHub Docs: About code owners](https://docs.github.com/en/repositories/managing-your-repositorys-settings-and-features/customizing-your-repository/about-code-owners)
- [SmartBear: Best practices for peer code review](https://smartbear.com/learn/code-review/best-practices-for-peer-code-review/)
