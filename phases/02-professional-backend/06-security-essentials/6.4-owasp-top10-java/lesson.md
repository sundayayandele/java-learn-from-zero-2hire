## What you will be able to do

- Name the **OWASP Top 10 (2025)** and **OWASP API Security Top 10 (2023)** risks and recognise each in Spring Boot code.
- Prevent **injection** (SQL, JPQL, log, command, LDAP), **SSRF**, insecure deserialisation and XML attacks in Java.
- Avoid **security misconfiguration** in Spring Boot (Actuator, error pages, default credentials).
- Handle **logging and monitoring** of security events without leaking personal data.
- Use the OWASP lists as a **review checklist** for your own pull requests.

## Why this matters in a bank or fintech

Penetration tests, regulators' IT examinations and many vendor due-diligence questionnaires are organised around the OWASP lists. When an auditor writes "finding: A01 Broken Access Control on /api/v1/statements", you need to know what it means and how to fix it in Spring. Interviewers for fintech roles regularly ask "how would you prevent SQL injection?" or "what is SSRF?" and expect concrete Java answers, not definitions.

## 1. The two lists

**OWASP Top 10 (2025)**, for web applications:

| ID | Risk | In a Spring API, for example |
| --- | --- | --- |
| A01 | Broken Access Control | Another customer's statement by changing an ID (topic 6.3); SSRF is now included here |
| A02 | Security Misconfiguration | Actuator `env` and `heapdump` exposed; stack traces in errors; permissive CORS |
| A03 | Software Supply Chain Failures | Vulnerable or malicious dependencies, unpinned build plugins, compromised CI (topic 6.5) |
| A04 | Cryptographic Failures | MD5 password hashes, TLS disabled for "internal" calls, homemade encryption |
| A05 | Injection | SQL built by string concatenation; untrusted input in log lines |
| A06 | Insecure Design | No rate limit on OTP verification; transfers without idempotency |
| A07 | Authentication Failures | No lockout, long-lived tokens, unchecked JWT audience (topic 6.2) |
| A08 | Software or Data Integrity Failures | Unsigned webhooks, Java deserialisation of untrusted data |
| A09 | Security Logging and Alerting Failures | Failed logins not logged; logs full of card numbers; no alerts |
| A10 | Mishandling of Exceptional Conditions | Failing open on errors; leaking details in error messages |

**OWASP API Security Top 10 (2023)**, specific to APIs: API1 Broken Object Level Authorization, API2 Broken Authentication, API3 Broken Object Property Level Authorization (mass assignment and excessive data exposure), API4 Unrestricted Resource Consumption, API5 Broken Function Level Authorization, API6 Unrestricted Access to Sensitive Business Flows, API7 Server Side Request Forgery, API8 Security Misconfiguration, API9 Improper Inventory Management, API10 Unsafe Consumption of APIs.

(The 2025 edition reorganised the web list; when you read older reports you will see the 2021 numbering, where A03 was Injection and A10 was SSRF.)

## 2. Injection

### SQL and JPQL

Vulnerable:

```java
String sql = "SELECT * FROM account WHERE customer_id = '" + customerId + "' ORDER BY " + sort;
```

A `customerId` of `' OR '1'='1` returns every account. Fix with **parameters**, always:

```java
jdbc.sql("SELECT * FROM account WHERE customer_id = :customerId")
    .param("customerId", customerId)
    .query(Account.class).list();

@Query("select a from Account a where a.customerId = :customerId")
List<Account> findForCustomer(@Param("customerId") String customerId);
```

Identifiers (column names in `ORDER BY`, table names) cannot be parameters; use an **allow-list**:

```java
private static final Map<String, String> SORTABLE = Map.of("date", "value_date", "amount", "amount");
String column = Optional.ofNullable(SORTABLE.get(sort)).orElseThrow(() -> new InvalidSortException(sort));
```

Spring Data derived queries and `Sort` with validated property names are safe; `@Query(nativeQuery = true)` with concatenated strings is not.

### Log injection

Untrusted input with line breaks can forge log entries (`"alice\n2026-10-24 INFO Transfer approved by supervisor"`), and some log pipelines once evaluated lookups in messages (Log4Shell). Use parameterised logging, structured (JSON) logs that escape values, and never log secrets:

```java
log.info("Login failed for user={} ip={}", sanitise(username), ip);   // not "Login failed for " + username
```

### OS command, LDAP, expression and template injection

Avoid `Runtime.exec` with user input (use `ProcessBuilder` with a fixed command and separate arguments); escape LDAP filters; never evaluate SpEL, OGNL or template expressions built from user input.

## 3. Server-side request forgery (SSRF)

A "fetch logo from URL" or "verify webhook URL" feature that lets users supply a URL can be pointed at internal addresses: `http://169.254.169.254/latest/meta-data/` (cloud instance credentials), `http://localhost:8080/actuator/env`, internal admin services. Defences:

- Do not fetch user-supplied URLs if you can avoid it.
- Otherwise **allow-list** hosts and schemes (`https` only), resolve the DNS name and **reject private, loopback and link-local addresses** (and re-check after redirects, or disable redirects).
- Run outbound fetches through an egress proxy; use IMDSv2 on AWS.

## 4. Deserialisation and XML

- **Java native serialisation** (`ObjectInputStream`) of untrusted data can execute code through gadget chains. Do not use it for anything that crosses a trust boundary; use JSON with explicit DTOs. If you must, apply a serialisation filter (`jdk.serialFilter`, Phase 1 topic 1.10).
- **Jackson polymorphic typing** (`@JsonTypeInfo(use = Id.CLASS)` or default typing) lets a payload choose which class to create: avoid it, or use an allow-list of subtypes.
- **XML External Entities (XXE):** many bank integrations still use XML (ISO 20022 messages, SOAP). Disable DTDs and external entities on every parser:

```java
DocumentBuilderFactory dbf = DocumentBuilderFactory.newInstance();
dbf.setFeature("http://apache.org/xml/features/disallow-doctype-decl", true);
dbf.setFeature(XMLConstants.FEATURE_SECURE_PROCESSING, true);
dbf.setXIncludeAware(false);
dbf.setExpandEntityReferences(false);
```

## 5. Security misconfiguration in Spring Boot

- **Actuator:** expose only what you need (`management.endpoints.web.exposure.include=health,info,prometheus`), put it on a separate port or behind authentication, and never expose `env`, `heapdump`, `threaddump`, `configprops` or `loggers` publicly. A heap dump contains secrets and customer data in memory.
- **Errors:** `server.error.include-stacktrace=never`, `server.error.include-message=never` (the defaults); return Problem Details without internals.
- **H2 console, Swagger UI, GraphiQL, debug endpoints:** off in production profiles.
- **Default credentials:** no `spring.security.user.password=admin`; change every default password in Compose files used beyond a laptop.
- **TLS everywhere**, including between internal services and to the database (`sslmode=verify-full` for PostgreSQL).
- **Security headers** stay on (topic 6.1).

## 6. Insecure design and business-logic abuse

Some vulnerabilities are not bugs in code but missing controls in the design:

- **OTP and PIN brute force:** a 6-digit OTP with no attempt limit falls in minutes. Limit attempts per OTP and per user, expire OTPs quickly, and alert on spikes.
- **Unrestricted resource consumption** (API4): no pagination limits, no request size limits, no rate limits on expensive endpoints like statement generation.
- **Sensitive business flows** (API6): automated abuse of sign-up bonuses, referral credits or airtime purchase. Use velocity limits, device fingerprinting and fraud rules (Phase 1 Capstone 7).
- **Race conditions on balances:** two concurrent withdrawals both passing a balance check (Phase 1 topics 2.2 and 3.2). Use database locks or constraints.

Threat-model new features with a simple question for each step: *what could a malicious user, partner or insider do here?*

## 7. Logging, monitoring and alerting

Log security-relevant events in a structured form: logins (success and failure), token refresh and reuse detection, authorisation denials, privileged actions, changes to limits, beneficiaries and contact details, webhook signature failures. Include user ID, client ID, IP and trace ID. **Never** log passwords, tokens, full card numbers (PAN), CVV, PINs, BVN/NIN in full or OTPs; mask them (`5399********1234`). Alert on patterns: many 401s from one IP, refresh-token reuse, a spike in 403s on admin paths. Logs that nobody watches are not monitoring.

## 8. Unsafe consumption of APIs

Treat responses from partners and providers as untrusted input (API10): validate them against a schema, apply size limits and timeouts, do not follow redirects blindly, and never execute or evaluate their content. A compromised provider should not be able to compromise you.

## Common mistakes

- String-concatenated SQL in "just this one native query".
- Allowing users to choose arbitrary sort columns or URLs to fetch.
- Actuator fully exposed "for debugging".
- Logging request bodies that contain passwords, tokens or card data.
- Java serialisation or default typing on data from outside.
- XML parsers with default settings for ISO 20022 or SOAP messages.
- Treating security as a penetration-test phase instead of a design and review habit.

## Practice

1. Find and fix the injection bugs in a deliberately vulnerable repository (the Moodle lab links one), writing a failing test for each first.
2. Review your capstone against both OWASP lists and write `SECURITY.md` listing each risk, where it applies, and your mitigation.
3. Lock down Actuator: separate management port, only `health`, `info` and `prometheus` exposed, authentication for everything else; prove with tests.
4. Add an OTP verification endpoint with attempt limits and expiry, and a test that the sixth wrong attempt is refused.

## Key terms

OWASP, Top 10 (2025), API Security Top 10 (2023), broken access control, security misconfiguration, supply chain, cryptographic failures, injection, parameterised query, allow-list, log injection, insecure design, threat modelling, integrity failures, deserialisation gadget, XXE, SSRF, IMDS, Actuator exposure, resource consumption, business-flow abuse, PAN masking, security logging and alerting.

## Further reading

- [OWASP Top 10 (2025)](https://owasp.org/Top10/2025/)
- [OWASP API Security Top 10 (2023)](https://owasp.org/API-Security/editions/2023/en/0x11-t10/)
- [OWASP Cheat Sheet: SQL Injection Prevention](https://cheatsheetseries.owasp.org/cheatsheets/SQL_Injection_Prevention_Cheat_Sheet.html)
- [OWASP Cheat Sheet: Server-Side Request Forgery Prevention](https://cheatsheetseries.owasp.org/cheatsheets/Server_Side_Request_Forgery_Prevention_Cheat_Sheet.html)
- [OWASP Cheat Sheet: XML External Entity Prevention](https://cheatsheetseries.owasp.org/cheatsheets/XML_External_Entity_Prevention_Cheat_Sheet.html)
- [Spring Boot reference: Actuator endpoints and security](https://docs.spring.io/spring-boot/reference/actuator/endpoints.html)
