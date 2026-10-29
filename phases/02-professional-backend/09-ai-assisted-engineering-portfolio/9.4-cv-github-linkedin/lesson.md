## What you will be able to do

- Write a **one-page CV** for junior Java backend roles with **quantified, evidence-based** project bullets.
- Turn your GitHub account into a **portfolio**: profile README, pinned repositories, READMEs a hiring manager can read in three minutes, green CI and live links.
- Optimise **LinkedIn** (headline, about, featured, skills, experience) for recruiters searching for Java, Spring Boot and fintech skills.
- Tailor applications to a job description with an **evidence map**, and pass applicant tracking systems (ATS) without keyword stuffing.

## Why this matters in a bank or fintech

Recruiters and hiring managers spend seconds on a first screen. For career changers and new graduates, your projects *are* your experience, and a strong GitHub portfolio of deployed, tested, secured financial services is what separates you from hundreds of applicants with the same course certificate. Banks also run background and reference checks, so everything you claim must be true and demonstrable.

## 1. The CV

### Format

- **One page** for junior roles; PDF; simple single-column layout that ATS software can parse (no tables for core content, no text in images, standard section names).
- Sections, in order: **Header** (name, city or "Lagos, Nigeria · open to remote", email, phone, GitHub, LinkedIn, portfolio site) → **Summary** (2–3 lines) → **Skills** → **Projects** (your strongest section) → **Experience** (any work, including non-tech, briefly) → **Education and certifications**.
- No photo, date of birth, marital status or state of origin unless an employer explicitly asks; they invite bias and are not needed.

### Summary

> Junior Java backend engineer who builds and deploys secure, tested Spring Boot services for payments and banking: a double-entry ledger, a payment-gateway integration hub with signed webhooks, and an AML monitoring engine over 5 million transactions. Comfortable with PostgreSQL, Docker, GitHub Actions and Keycloak.

### Skills

Group them and list only what you can discuss in an interview:

- **Languages:** Java 21, SQL, Kotlin (basic)
- **Frameworks:** Spring Boot 4, Spring Security (OAuth2, JWT), Spring Data JPA, JUnit 5, Mockito, Testcontainers
- **Data:** PostgreSQL, Redis, MongoDB, Flyway
- **Delivery:** Docker, GitHub Actions, GitLab CI, SonarQube, AWS/Azure container services, Linux
- **Practices:** REST and OpenAPI, Problem Details, idempotency, TDD, code review

### Project bullets that prove impact

Use the pattern **action + what + how + result (with a number)**:

- Weak: "Worked on a ledger project using Spring Boot."
- Strong: "Built a double-entry core-banking ledger (Spring Boot 4, PostgreSQL) with ordered row locking and idempotency keys; 50 concurrent transfers keep balances exact, verified by Testcontainers integration tests in CI."
- Strong: "Integrated a Paystack-style payment gateway with HMAC-verified, de-duplicated webhooks, timeouts, retries and a circuit breaker; WireMock tests cover 12 failure scenarios."
- Strong: "Wrote SQL window-function scenarios that flag structuring and fan-out across 5M AML transactions in under 4 seconds each after indexing (EXPLAIN ANALYZE before/after in README)."

Every number must be real and reproducible from your repository. Link each project name to its GitHub repository.

### What not to do

- Lists of every technology you have heard of.
- "Expert" or self-ratings (stars, percentages) for skills.
- Generic objectives ("seeking a challenging role…").
- Spelling mistakes in technology names (it is "PostgreSQL", "JavaScript", "GitHub").
- Copying an AI-written CV without editing it into your own words; recruiters see many identical ones.

## 2. GitHub as a portfolio

### Profile README

Create a repository named exactly like your username; its `README.md` appears on your profile:

```markdown
# Hi, I'm <your name> 👋
Junior Java backend engineer building secure, tested payment and banking services.
Looking for backend roles in Lagos or remote (Java, Spring Boot, PostgreSQL).

**Featured projects**
- [core-banking-ledger](https://github.com/your-username/core-banking-ledger): double-entry ledger, concurrency-safe transfers · [live](https://ledger.your-domain.dev/actuator/health)
- [payment-gateway-hub](https://github.com/your-username/payment-gateway-hub): gateway integration, signed webhooks, resilience
- [aml-monitoring](https://github.com/your-username/aml-monitoring): SQL-driven AML scenarios over 5M transactions

**Stack:** Java 21 · Spring Boot 4 · PostgreSQL · Docker · GitHub Actions · Keycloak
**Writing:** [How I made transfers idempotent](https://dev.to/your-username/idempotent-transfers)
```

### Pinned repositories

Pin four to six of your best repositories. Each one needs:

- A **README** a hiring manager can read in three minutes: the business problem, an architecture diagram, how to run it, API examples, design decisions and trade-offs, test and quality evidence (badges), data sources and licences, what you would do next.
- **Green CI** with quality gates visible as badges.
- A **live link** (health endpoint or demo) or a 3-minute demo video.
- A clean, meaningful **commit history** (topic 8.1), and no secrets.
- An **AI usage note** if you used assistants (topic 9.1).

Delete or archive tutorial forks and half-finished experiments, or keep them unpinned. Quality beats quantity.

### Contribution activity

Regular commits show sustained work, but do not game the contribution graph. A few real **open-source contributions** (a documentation fix in a Spring project, a bug fix in a library you used) are worth more than months of empty commits.

## 3. LinkedIn

- **Headline** (searchable): "Java Backend Engineer · Spring Boot · PostgreSQL · Payments & Fintech" rather than "Aspiring developer".
- **About:** 3–5 short paragraphs: what you build, two or three projects with results, what roles you want, how to contact you.
- **Featured:** links to your best repositories, demo videos and one technical post.
- **Experience:** if you have no tech job yet, add your capstone portfolio as a project or a "Software Engineering Trainee, ITLearning" entry, and describe transferable experience from previous jobs (a bank teller who understands reconciliation is valuable to a fintech).
- **Skills:** add the 10–15 most relevant (Java, Spring Boot, PostgreSQL, REST APIs, Docker…) so recruiter searches find you; ask classmates and mentors for endorsements you can justify.
- **Open to work:** set it to recruiters only if you are currently employed.
- **Post occasionally:** a short write-up of a problem you solved (with a diagram) builds visibility more than reposting news.

Make your CV, GitHub and LinkedIn **consistent**: same job titles, dates and project names.

## 4. Tailoring to a job description

For each role, build an **evidence map**: requirement → your proof.

| Job ad says | Evidence |
| --- | --- |
| "Experience building RESTful APIs with Spring Boot" | Payment hub API, OpenAPI document, MockMvc test suite |
| "Knowledge of SQL and relational databases" | Ledger schema with constraints; AML scenarios with window functions and EXPLAIN ANALYZE |
| "Familiarity with CI/CD and Docker" | GitHub Actions pipeline with quality gates; multi-stage Dockerfile; deployed on Azure Container Apps |
| "Understanding of security best practices" | Keycloak-secured API, object-level authorisation tests, dependency scanning |
| "Payments or banking domain is a plus" | Three financial capstones; Phase 1 & 2 programme |

Then adjust your CV's summary, skills order and project bullets to put the matching evidence first, using the employer's vocabulary where it is accurate (ATS systems match keywords, and so do human screeners). Never claim a skill you cannot discuss.

## 5. Applications and referrals

- **Referrals** have far higher response rates than cold applications: tell classmates, mentors and former colleagues what you are looking for, and ask for referrals to specific roles.
- Write a **short, specific note** with each application or LinkedIn message: one sentence on why this company, one on your most relevant project with a link.
- **Track** applications (company, role, date, contact, status, follow-up date) in a spreadsheet or board; follow up once after a week.
- Expect rejections; treat each interview as practice and ask for feedback.

## Common mistakes

- Two-page CVs full of course names and no projects.
- Repositories without READMEs, with failing builds or committed secrets.
- Numbers you cannot reproduce or explain in an interview.
- A LinkedIn headline that hides what you do ("Graduate | Learner | Dreamer").
- The same generic CV sent to every job.
- Inconsistent dates and titles across CV and LinkedIn.

## Practice

1. Write your one-page CV with at least three quantified project bullets; ask a mentor or peer to review it against this lesson.
2. Create your GitHub profile README and pin your four best repositories; bring each README up to the three-minute standard.
3. Rewrite your LinkedIn headline, about section and featured items.
4. Pick three real job ads and build an evidence map for each; tailor the CV for one of them.

## Key terms

ATS (applicant tracking system), one-page CV, summary, quantified bullet, action–what–how–result, portfolio, profile README, pinned repository, badge, demo video, contribution graph, open-source contribution, headline, featured section, endorsement, evidence map, keyword matching, referral, application tracker.

## Further reading

- [GitHub Docs: Managing your profile README](https://docs.github.com/en/account-and-profile/how-tos/profile-customization/managing-your-profile-readme)
- [GitHub Docs: Pinning items to your profile](https://docs.github.com/en/account-and-profile/how-tos/profile-customization/pinning-items-to-your-profile)
- [LinkedIn Help: Add sections to your profile](https://www.linkedin.com/help/linkedin/answer/a540837)
- [Make a README](https://www.makeareadme.com/)
- [freeCodeCamp: How to write a developer resume hiring managers will read](https://www.freecodecamp.org/news/writing-a-killer-software-engineering-resume-b11c91ef699d/)
- [Tech Interview Handbook: Resume guide](https://www.techinterviewhandbook.org/resume/)
