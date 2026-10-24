## What you will be able to do

- Explain how Git stores history (commits, branches, `HEAD`) well enough to recover from mistakes.
- Use a team branching strategy: **trunk-based development** with short-lived feature branches, compared with Gitflow.
- Choose between **merge**, **rebase** and **squash**, and rebase interactively to tidy your own branch.
- Resolve **merge conflicts** confidently.
- Write **conventional commits** and configure **branch protection** (required reviews, status checks, linear history, signed commits).
- Undo safely: `revert`, `reset`, `reflog`, `restore`.

## Why this matters in a bank or fintech

Banks treat source control as part of their change-management control: every production change must be traceable to a reviewed, approved commit linked to a ticket. Teams of ten or more engineers working on one payments service depend on a disciplined branching model to release several times a day without breaking each other. Messing up Git in your first week (force-pushing over a colleague's work, committing a secret) is memorable for the wrong reasons; recovering calmly from a Git mistake is a quiet sign of seniority.

## 1. How Git thinks

- A **commit** is a snapshot of the whole project plus metadata (author, message, parent commits), identified by a SHA hash.
- A **branch** is just a movable pointer to a commit. `HEAD` points to the branch (or commit) you have checked out.
- Your work moves through three areas: **working tree** → `git add` → **staging area (index)** → `git commit` → **repository**.
- `origin/main` is your local copy of the remote's `main`, updated by `git fetch`.

Because branches are only pointers and commits are never really deleted immediately, almost every mistake is recoverable (section 6).

## 2. Branching strategies

### Trunk-based development (recommended)

- Everyone integrates into `main` (the trunk) at least daily through **short-lived feature branches** (hours to a couple of days).
- `main` is always releasable; CI runs on every pull request and every merge.
- Unfinished features hide behind **feature flags**, so code can merge before the feature is switched on.
- Releases are tags or deployments from `main`.

This is what high-performing teams use (and what the DORA research associates with better delivery performance), and it suits CI/CD.

### Gitflow

Long-lived `develop` and `main` branches, plus `feature/*`, `release/*` and `hotfix/*` branches. You will still meet it in banks with scheduled quarterly releases and long regression cycles. It works, but long-lived branches cause painful merges and slow feedback. If your team uses it, keep feature branches short anyway.

### Branch naming

`feat/TRF-214-idempotency-key`, `fix/TRF-230-fee-rounding`, `chore/upgrade-boot-4.1`: the type, the ticket ID and a short description.

## 3. Commits that tell a story

Small, focused commits that each build and pass tests make review, `git bisect` and reverting easy. Use **Conventional Commits**:

```
feat(transfers): honour Idempotency-Key header on POST /transfers

Stores a SHA-256 fingerprint and the response in idempotency_key and
replays it for identical retries. Different payloads with the same key
return 422.

Refs: TRF-214
```

Types: `feat`, `fix`, `refactor`, `test`, `docs`, `build`, `ci`, `chore`, `perf`; `!` or a `BREAKING CHANGE:` footer for breaking changes. Tools can generate changelogs and semantic version numbers from them.

The subject line: imperative mood ("add", not "added"), under about 72 characters, says *what*; the body says *why*.

## 4. Merge, rebase, squash

You open a branch from `main`; meanwhile colleagues merge to `main`. How do you bring your branch up to date, and how does it land?

**Merge** `main` into your branch: a merge commit joins the histories. Safe, never rewrites history, but busy branches get noisy merge commits.

**Rebase** your branch onto `main`: your commits are replayed on top of the latest `main`, as if you had started there.

```bash
git fetch origin
git rebase origin/main          # replay my commits on top of the latest main
# resolve conflicts if any, then:
git add <files> && git rebase --continue
git push --force-with-lease     # my branch's history changed
```

The golden rule: **only rebase commits that nobody else has based work on** (your own feature branch). Never rebase `main` or a shared branch. Use `--force-with-lease`, never plain `--force`: it refuses to overwrite the remote if someone else pushed in the meantime.

**Interactive rebase** tidies your branch before review:

```bash
git rebase -i origin/main
# pick a1b2c3 feat(transfers): add idempotency table
# fixup d4e5f6 fix typo
# reword 7a8b9c add replay logic
```

**Landing the pull request** (repository setting):

| Strategy | Result on `main` | Good when |
| --- | --- | --- |
| Merge commit | All branch commits + a merge commit | Commits are well curated and you want the branch visible |
| Squash and merge | One commit per PR | Most teams; clean, revertable history, PR title becomes the message |
| Rebase and merge | Branch commits replayed linearly | Curated commits and linear history |

## 5. Resolving conflicts

A conflict means two changes touched the same lines. Git marks them:

```java
<<<<<<< HEAD (main)
private static final BigDecimal VAT_RATE = new BigDecimal("0.075");
=======
private static final BigDecimal VAT_RATE = new BigDecimal("0.0750");
private static final RoundingMode ROUNDING = RoundingMode.HALF_EVEN;
>>>>>>> feat/TRF-230-fee-rounding
```

1. Understand **both** changes (read the other PR if needed); do not just pick "mine".
2. Edit to the correct combined result, remove the markers.
3. Compile and run the tests before continuing; a conflict resolution can compile and still be wrong.
4. `git add` the file and continue the merge or rebase.

Small, frequent integration means small conflicts. A week-old branch means big ones.

## 6. Undoing things safely

| Situation | Command |
| --- | --- |
| Discard uncommitted changes to a file | `git restore <file>` |
| Unstage a file | `git restore --staged <file>` |
| Fix the last commit (not yet pushed) | `git commit --amend` |
| Undo a commit that is already on `main` | `git revert <sha>` (a new commit that reverses it; safe for shared history) |
| Move your branch back (local only) | `git reset --soft HEAD~1` (keep changes staged) or `--hard` (discard) |
| "I lost a commit after a reset or rebase" | `git reflog`, then `git branch rescue <sha>` |
| Find which commit introduced a bug | `git bisect start`, `git bisect bad`, `git bisect good <sha>`, test, repeat |

**Never** rewrite history on `main` (no `reset` + force push). Use `revert`. And if you commit a **secret**, rotating it is the fix; rewriting history is secondary (topic 6.5).

## 7. Branch protection and repository rules

On GitHub (Settings → Rules → Rulesets, or branch protection) for `main`:

- Require a **pull request** before merging, with at least one **approving review**; dismiss stale approvals when new commits are pushed.
- Require review from **CODEOWNERS** for sensitive paths (migrations, security configuration, CI files).
- Require **status checks** to pass (build, tests, quality gate, scans) and require the branch to be **up to date** before merging.
- Require **linear history** (squash or rebase merges only).
- Require **signed commits** if your organisation mandates it (GPG, SSH or S/MIME signing proves who made a commit).
- Block **force pushes** and **deletions**; do not let administrators bypass the rules.

GitLab offers the same through protected branches and merge-request approval rules.

## 8. Useful extras

- `.gitignore` from the start (`target/`, `.idea/`, `.env`, `*.log`).
- `.gitattributes` with `* text=auto` and `*.sh text eol=lf` (no more `^M` in shell scripts on Windows).
- A **pre-commit hook** (for example with the `pre-commit` framework) to run formatting and Gitleaks before each commit.
- `git log --oneline --graph --decorate --all` to see the shape of history.

## Common mistakes

- Long-lived feature branches that drift for weeks.
- `git push --force` over a shared branch.
- Rebasing `main` or a branch others have pulled.
- Giant commits titled "updates" or "fix".
- Resolving conflicts by blindly choosing one side.
- Rewriting `main` to remove a bad commit instead of reverting it.
- Committing build output, IDE files or `.env`.

## Practice

1. In a scratch repository, create a conflict between two branches, resolve it by rebasing, and push with `--force-with-lease`.
2. Use `git rebase -i` to squash fixup commits and reword messages into conventional commits.
3. "Lose" a commit with `git reset --hard`, then recover it with `git reflog`.
4. Use `git bisect` to find the commit that broke a test in a prepared repository.
5. Configure branch protection on your capstone's `main` as in section 7, and prove a direct push is rejected.

## Key terms

Commit, SHA, branch, `HEAD`, working tree, staging area, remote, `origin/main`, fetch, pull, trunk-based development, feature branch, feature flag, Gitflow, Conventional Commits, merge commit, rebase, interactive rebase, squash, fast-forward, `--force-with-lease`, merge conflict, `revert`, `reset`, `reflog`, `bisect`, branch protection, ruleset, required status check, CODEOWNERS, signed commit, `.gitattributes`.

## Further reading

- [Pro Git book (free, official)](https://git-scm.com/book/en/v2)
- [Atlassian Git tutorials: Merging vs. rebasing](https://www.atlassian.com/git/tutorials/merging-vs-rebasing)
- [Trunk Based Development](https://trunkbaseddevelopment.com/)
- [Conventional Commits 1.0.0](https://www.conventionalcommits.org/en/v1.0.0/)
- [GitHub Docs: About protected branches](https://docs.github.com/en/repositories/configuring-branches-and-merges-in-your-repository/managing-protected-branches/about-protected-branches)
- [DORA research: Trunk-based development capability](https://dora.dev/capabilities/trunk-based-development/)
