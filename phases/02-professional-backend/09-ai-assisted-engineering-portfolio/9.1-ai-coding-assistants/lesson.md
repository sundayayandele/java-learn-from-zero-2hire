## What you will be able to do

- Describe the main kinds of AI coding tools: inline completion, chat, and **agentic** assistants that read your repository, run commands and edit files (GitHub Copilot, Claude Code, Cursor and similar).
- Use them effectively for everyday Java work: understanding unfamiliar code, scaffolding, writing tests, debugging and refactoring.
- Write **good prompts and context**: goals, constraints, examples, and project instruction files.
- Work in a **tight loop**: small steps, tests as the safety net, review every change.
- Follow your employer's **policy** on data, secrets and approved tools.

## Why this matters in a bank or fintech

Most engineering teams now use AI assistants daily, and job descriptions increasingly mention them. Used well, they remove drudgery (boilerplate, test data, mapping code, explaining legacy code) and help juniors ramp up on large codebases quickly. Used badly, they produce plausible code you do not understand, leak confidential code or customer data to external services, and introduce subtle bugs in exactly the places banks cannot afford them. Employers want engineers who are faster *and* still accountable for every line they ship.

## 1. Kinds of tools

| Kind | What it does | Examples |
| --- | --- | --- |
| Inline completion | Suggests the next lines as you type | GitHub Copilot, JetBrains AI, Cursor Tab |
| Chat in the IDE | Answers questions about selected code, proposes edits | Copilot Chat, JetBrains AI Assistant, Cursor chat |
| Agentic assistant | Plans a task, reads files, runs tests and commands, edits multiple files, iterates | Claude Code, Copilot agent mode and coding agent, Cursor agent, Codex, Gemini CLI |
| Review bots | Comment on pull requests | Copilot code review, CodeRabbit and others |

All of them are built on large language models: systems that predict likely text from enormous training data. They are very good at common patterns and very confident even when wrong. They do not *know* your bank's rules unless you tell them.

## 2. What they are good at in Java work

- **Explaining code:** "Explain what `LedgerPostingService.post` does, including the locking order and what happens on `DataIntegrityViolationException`."
- **Navigating a large codebase:** "Where is the transfer fee calculated, and which tests cover it?"
- **Boilerplate:** DTO records, mappers, Flyway migration skeletons, `@ConfigurationProperties` classes, OpenAPI annotations.
- **Tests:** parameterised boundary cases, test data builders, WireMock stubs (topic 9.3).
- **Debugging:** paste a stack trace and the relevant code; ask for hypotheses, then verify them yourself.
- **Upgrades and refactors:** Boot 3 to 4 migration steps, replacing `RestTemplate` with `RestClient`, extracting a class, with tests guarding behaviour.
- **Learning:** "Show me how Spring Security's `BearerTokenAuthenticationFilter` fits into the filter chain" (then check the docs).

They are weaker at: business rules nobody wrote down, subtle concurrency and transaction behaviour, security edge cases, the newest library versions (training data lags), and anything requiring knowledge of your production systems.

## 3. Prompting: give context, constraints and a definition of done

Weak: "write a transfer service".

Better:

```text
In this Spring Boot 4.1 / Java 21 project, add a TransferService.transfer(TransferCommand) method.

Context: accounts are in the `account` table (see V3__accounts.sql); AccountRepository has
lockForUpdate(id). Money is the Money record in domain/Money.java; never use double.

Requirements:
- Lock both accounts in ascending account-number order to avoid deadlocks.
- Reject if the source is frozen (AccountFrozenException) or has insufficient funds
  (InsufficientFundsException); both map to 422 in ApiExceptionHandler.
- Post a balanced journal (debit source, credit destination) in one @Transactional method.
- Idempotent on command.reference(): if a journal with that reference exists, return it.

Done when: existing tests pass and you add unit tests for frozen, insufficient funds and
idempotent replay, plus an integration test with 20 concurrent transfers (Testcontainers).
Do not change the public API of existing classes. Ask before adding dependencies.
```

Patterns that help:

- **Point at real files** rather than describing them; agentic tools can read them.
- **State constraints**: versions, libraries allowed, patterns to follow, what *not* to change.
- **Give a definition of done** that the tool can check itself: tests to pass, commands to run.
- **Ask for a plan first** on bigger tasks, review it, then let it implement step by step.
- **One task at a time**; start a fresh session for unrelated work so stale context does not mislead it.

### Project instruction files

Agentic tools read instruction files from the repository on every session: `CLAUDE.md` (Claude Code), `.github/copilot-instructions.md` (Copilot), `.cursor/rules` (Cursor), `AGENTS.md` (several tools). Put team conventions there once:

```markdown
# Project conventions
- Java 21, Spring Boot 4.1, Maven wrapper. Run `./mvnw verify` before declaring work done.
- Money: domain/Money.java (BigDecimal, HALF_EVEN). Never use double or float for amounts.
- Errors: throw domain exceptions; ApiExceptionHandler maps them to RFC 9457 Problem Details.
- Database: new Flyway migration per change; never edit an applied migration; ddl-auto=validate.
- Security: every endpoint needs authorisation and an ownership check with negative tests.
- Never put real customer data, secrets or tokens in code, tests or prompts.
```

## 4. The working loop

1. **Understand the task yourself first**, at least well enough to judge the result.
2. Ask for a **small** change; let the tool run the tests (agentic tools can run `./mvnw test`).
3. **Read the diff line by line.** You are the author of record (topic 9.2).
4. Run the tests and the application yourself; try the edge cases.
5. Commit in small steps, so you can roll back a bad suggestion easily.
6. When the tool goes in circles, stop, simplify the request, or do that part by hand.

A useful habit: after accepting generated code, explain it out loud (or in the pull request description) without looking at the tool's explanation. If you cannot, you are not ready to ship it.

## 5. Data protection and policy

Banks set rules on AI tools; follow them exactly:

- Use only **approved tools and accounts** (enterprise versions usually guarantee your code is not used for training and is not retained).
- **Never paste** customer data (names, BVNs, account numbers, card numbers), production logs with personal data, secrets, keys or tokens into a prompt. Use synthetic data.
- Be careful with **confidential code and architecture** on personal accounts.
- Agentic tools can run commands: keep them in a sandbox or with permission prompts, never give them production credentials, and review what they intend to run.
- Respect licences: see topic 9.2 on licence risk of generated code.
- Nigeria's Data Protection Act 2023 (NDPA) and the GDPR apply to personal data in prompts as in any other processing.

## 6. Measuring whether it helps

Track your own outcomes rather than impressions: time to a reviewed, merged pull request; defects found in review or after release; how often you had to rewrite generated code. Teams that benefit most combine AI tools with strong tests, CI quality gates and code review, the practices of Modules 7 and 8.

## Common mistakes

- Accepting large generated changes without reading them.
- Vague prompts with no constraints, then blaming the tool.
- Trusting answers about library versions and APIs without checking the documentation (models lag behind releases).
- Pasting secrets, customer data or production logs into prompts.
- Letting an agent run commands against real environments.
- Using AI to skip learning the fundamentals you will be interviewed on.

## Practice

1. Add a `CLAUDE.md` or `copilot-instructions.md` (or `AGENTS.md`) to your capstone with your conventions.
2. Use an assistant to explain an unfamiliar part of a Phase 1 capstone (yours, or the reference solution your mentor shared), then verify each claim against the code and documentation; note what it got wrong.
3. Implement one small capstone feature with an agentic assistant using the plan → small steps → tests loop; record time spent and every correction you made.
4. Write a one-page personal policy: tools you use, what you never paste, how you review output.

## Key terms

Large language model, inline completion, chat assistant, agentic coding assistant, context window, prompt, constraint, definition of done, plan mode, project instruction file (`CLAUDE.md`, `AGENTS.md`, `copilot-instructions.md`), hallucination, training cut-off, synthetic data, data protection, approved tools, sandbox, human in the loop.

## Further reading

- [GitHub Docs: Best practices for using GitHub Copilot](https://docs.github.com/en/copilot/get-started/best-practices)
- [Claude Code documentation: Common workflows](https://docs.claude.com/en/docs/claude-code/common-workflows)
- [Cursor documentation](https://docs.cursor.com/)
- [Anthropic: Claude Code best practices for agentic coding](https://www.anthropic.com/engineering/claude-code-best-practices)
- [OWASP Top 10 for Large Language Model Applications](https://owasp.org/www-project-top-10-for-large-language-model-applications/)
- [Nigeria Data Protection Commission: Nigeria Data Protection Act 2023](https://ndpc.gov.ng/resources/)
