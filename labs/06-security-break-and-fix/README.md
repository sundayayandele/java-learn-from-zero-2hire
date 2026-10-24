# Lab 6.D: Break it, then fix it

← [Module 6](../../phases/02-professional-backend/06-security-essentials/README.md) · [Topic 6.3](../../phases/02-professional-backend/06-security-essentials/6.3-rbac-method-security/README.md) · [Topic 6.4](../../phases/02-professional-backend/06-security-essentials/6.4-owasp-top10-java/README.md)

A small banking API secured with Spring Security 7 as an OAuth2 resource server. Every endpoint needs a token, yet it has **five planted vulnerabilities** from the OWASP API and web Top 10 lists. `./mvnw test` runs six tests that act as an attacker; **all six fail**. (An in-memory H2 database keeps the lab self-contained.)

| # | Failing test | Vulnerability | OWASP |
| --- | --- | --- | --- |
| 1 | `bug1_aCustomerCannotReadSomeoneElsesAccount` | Broken object level authorisation (IDOR): any account id works | API1:2023 |
| 2 | `bug2_searchIsNotInjectable`, `bug2_errorsDoNotLeakSqlOrInternals` | SQL injection through string concatenation, and error messages that reveal SQL and exception details | A05:2025 Injection, A02 Security Misconfiguration |
| 3 | `bug3_customersCannotPromoteThemselves` | Mass assignment: the profile update writes any field the client sends, including `role` | API3:2023 |
| 4 | `bug4_adminEndpointsNeedTheAdminRole` | Authorisation rules in the wrong order: the broad `/api/**` rule wins over `/api/admin/**` | A01:2025 Broken Access Control |
| 5 | `bug5_passwordsAreStoredWithAnAdaptiveHash` | Passwords stored as unsalted MD5 | A04:2025 Cryptographic Failures |

## How to work

1. **Attack first.** Before fixing anything, exploit each bug yourself with `curl` or the tests and write down what an attacker gains. For bug 2, explain why the payload in the test returns another customer's beneficiaries.
2. Fix one vulnerability per commit. Use the standard defences from Module 6: ownership in the query (return 404, not 403, for other people's data), bound parameters, an allow-list of updatable fields (or a dedicated DTO), specific matchers before general ones, `PasswordEncoderFactories.createDelegatingPasswordEncoder()`, and generic Problem Details with details logged server-side.
3. Don't make tests pass by special-casing their inputs (for example rejecting the exact payload). A mentor will try other payloads.
4. Write `FINDINGS.md` as a short **security report**: for each issue, severity (high, medium, low with a reason), how it can be exploited, the fix, and a test that now guards it. Finish with three more checks you would add to CI (for example the Trivy scan from Module 8, an authorisation matrix test, secret scanning).

No solution is published; mentors review your report and commits.
