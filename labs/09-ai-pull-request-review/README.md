# Lab 9.D: Review an AI-generated pull request

← [Module 9](../../phases/02-professional-backend/09-ai-assisted-engineering-portfolio/README.md) · [Topic 9.2](../../phases/02-professional-backend/09-ai-assisted-engineering-portfolio/9.2-reviewing-ai-generated-code/README.md) · [Topic 7.5](../../phases/02-professional-backend/07-testing-code-quality/7.5-code-review-pull-requests/README.md)

A teammate opened [PR #214](PULL_REQUEST.md): transfer fees and daily limits, written with an AI assistant. "All tests pass, ready to merge." The assistant's tests in `TransferFeeServiceTest` do pass. They also prove almost nothing.

The product owner's acceptance criteria are in the PR description. The reviewer (you) has turned them into `spec/FeePolicySpecTest`, and **most of those tests fail**. One of them (the concurrency test) fails only sometimes, which is exactly how race conditions behave: it can pass by luck, and mentors will check the code, not just the test result.

## How to work

1. **Review before you fix.** Read `PULL_REQUEST.md` and `TransferFeeService.java` as if you were the reviewer, and write `REVIEW.md` as the comments you would leave on the PR. For each finding give the line, the problem, the impact (money, security, compliance) and a suggested fix, and label it *blocking* or *non-blocking*. Aim for at least seven findings; at least five are blocking.
2. Comment on the AI's tests too: what would each one fail to catch?
3. **Then fix it** so all specification tests pass, run the suite five times, and replace the weak AI tests with ones that would have caught the bugs (keep the spec tests as they are).
4. In `REVIEW.md`, finish with a short note to the author: what to check next time they accept AI-generated code (topic 9.2's checklist).

Things to look for include money arithmetic, boundaries, rounding rules, exception handling that hides failures, thread safety, logging of personal data, and tests that can't fail.

No solution is published; mentors compare your `REVIEW.md` with their answer key.
