## What you will be able to do

- Store passwords correctly with **Argon2id** or **BCrypt** through Spring Security's `PasswordEncoder`, and migrate old hashes safely.
- Keep **secrets** (database passwords, API keys, signing keys) out of code and Git, and load them from a **secrets manager** or vault.
- Detect leaked secrets with **Gitleaks** and GitHub secret scanning, and respond when one leaks.
- Scan dependencies for known vulnerabilities (**OWASP Dependency-Check**, Dependabot, `dependency-review-action`) and generate an **SBOM**.
- Encrypt sensitive data at rest when the database alone is not enough.

## Why this matters in a bank or fintech

Leaked credentials and vulnerable dependencies cause a large share of real breaches. A database password committed to a public GitHub repository is found by automated scanners within minutes. Log4Shell (2021) showed that one vulnerable library deep in a dependency tree can expose a whole bank. Regulators and card schemes (PCI DSS) require strong password storage, key management and vulnerability management; customers' BVNs, NINs and card data must be protected even from your own engineers.

## 1. Password hashing

Never store passwords, not even encrypted. Store a **slow, salted, one-way hash**, so a stolen database does not reveal them.

| Algorithm | Use? |
| --- | --- |
| MD5, SHA-1, SHA-256 (even salted) | **No**: designed to be fast, so billions of guesses per second on a GPU |
| PBKDF2 | Acceptable where FIPS compliance requires it, with a high iteration count |
| BCrypt | Good; widely supported (cost factor 10–12) |
| **Argon2id** | Best current choice; memory-hard, resists GPU attacks |
| scrypt | Good; memory-hard |

Spring Security's `DelegatingPasswordEncoder` stores the algorithm with the hash, so you can upgrade later:

```java
@Bean
PasswordEncoder passwordEncoder() {
    String defaultId = "argon2@SpringSecurity_v5_8";
    Map<String, PasswordEncoder> encoders = Map.of(
            defaultId, Argon2PasswordEncoder.defaultsForSpringSecurity_v5_8(),
            "bcrypt", new BCryptPasswordEncoder(12));
    return new DelegatingPasswordEncoder(defaultId, encoders);
}
```

Stored values look like `{argon2@SpringSecurity_v5_8}$argon2id$v=19$m=16384,t=2,p=1$...` or `{bcrypt}$2a$12$...`. To migrate users from BCrypt to Argon2id, re-hash on successful login: Spring Security calls `UserDetailsPasswordService.updatePassword` when `upgradeEncoding` says the stored hash is outdated.

Related controls: minimum length (12+) rather than complexity rules, checking new passwords against known-breached lists, account lockout or progressive delays after failed attempts, and multi-factor authentication for staff and high-risk customer actions. For customers' **transaction PINs**, the same hashing applies, with strict attempt limits because a 4-digit PIN has only 10,000 values.

## 2. Secrets: keep them out of code

A **secret** is anything that grants access: database passwords, API keys for payment gateways, JWT signing keys, OAuth client secrets, encryption keys, webhook secrets.

Rules:

1. **Never commit secrets**, not even to a private repository, not even "temporarily". Git history keeps them forever.
2. Configuration files in Git contain **placeholders** only: `spring.datasource.password=${DB_PASSWORD}`.
3. Locally, use a git-ignored `.env` file (and commit an `.env.example` with fake values).
4. In production, inject secrets from a **secrets manager**:

| Platform | Secrets manager | Spring integration |
| --- | --- | --- |
| AWS | Secrets Manager / SSM Parameter Store | Spring Cloud AWS (`spring.config.import=aws-secretsmanager:prod/wallet/`) or ECS task secrets |
| Azure | Key Vault | Spring Cloud Azure (Key Vault property source) |
| GCP | Secret Manager | Spring Cloud GCP (`${sm@db-password}`) |
| Self-hosted / multi-cloud | HashiCorp Vault or OpenBao | Spring Cloud Vault (`spring.config.import=vault://`) |
| Kubernetes | Secrets (encrypted at rest) or External Secrets Operator | mounted as environment variables or files |

5. **Least privilege and rotation:** each service gets its own credentials with only the rights it needs, and secrets are rotated regularly and immediately after anyone with access leaves. Vault can issue short-lived **dynamic database credentials**, so there is no long-lived password to leak.
6. **Do not log configuration** (`/actuator/env`, debug logging of properties).

Spring Boot also supports **SSL bundles** (`spring.ssl.bundle.*`) so certificates and private keys are loaded from mounted files rather than baked into images.

## 3. Secret scanning

- **Gitleaks** scans the working tree and history for patterns like AWS keys, private keys and high-entropy strings. Run it as a pre-commit hook and in CI:

```yaml
# .github/workflows/ci.yml (job excerpt)
  secret-scan:
    runs-on: ubuntu-latest
    steps:
      - uses: actions/checkout@v5
        with: { fetch-depth: 0 }
      - uses: gitleaks/gitleaks-action@v2
        env: { GITHUB_TOKEN: "${{ secrets.GITHUB_TOKEN }}" }
```

- **GitHub secret scanning with push protection** blocks pushes that contain known secret formats.

### When a secret leaks

1. **Revoke and rotate it immediately.** Deleting the commit is not enough; assume it has been copied.
2. Check access logs of the affected system for misuse.
3. Remove it from history if needed (`git filter-repo`), and force everyone to re-clone.
4. Write a short incident note: how it leaked, how it was found, what changed so it cannot happen again.

## 4. Dependency vulnerabilities and the supply chain

A typical Spring Boot service has 100–200 transitive dependencies. Known vulnerabilities are published as **CVEs** and scored with **CVSS**.

**OWASP Dependency-Check** (Maven plugin) compares your dependencies with the National Vulnerability Database and fails the build above a threshold:

```xml
<plugin>
    <groupId>org.owasp</groupId>
    <artifactId>dependency-check-maven</artifactId>
    <version>13.0.0</version>
    <configuration>
        <failBuildOnCVSS>7</failBuildOnCVSS>
        <suppressionFiles>
            <suppressionFile>dependency-check-suppressions.xml</suppressionFile>
        </suppressionFiles>
        <nvdApiKey>${env.NVD_API_KEY}</nvdApiKey>
    </configuration>
</plugin>
```

(The NVD API key is free and makes database updates much faster; store it as a CI secret.)

Other layers:

- **Dependabot** (or Renovate) opens pull requests to update vulnerable or outdated dependencies; your test suite is what makes merging them safe.
- **`actions/dependency-review-action`** fails a pull request that *adds* a vulnerable dependency.
- **Image scanning** (Trivy, Grype) covers the OS packages and JRE in your container (topic 8.2).
- **SBOM:** a Software Bill of Materials lists every component and version. Generate one in CycloneDX format so that when the next Log4Shell happens you can answer "are we affected?" in minutes:

```xml
<plugin>
    <groupId>org.cyclonedx</groupId>
    <artifactId>cyclonedx-maven-plugin</artifactId>
    <version>2.9.3</version>
    <executions><execution><phase>package</phase><goals><goal>makeAggregateBom</goal></goals></execution></executions>
</plugin>
```

Supply-chain hygiene also means: keep the Spring Boot parent current (it manages most versions for you), pin plugin and GitHub Action versions, use the Maven Central or an internal proxy repository only, and review new dependencies before adding them (licence, maintenance, popularity, transitive weight).

### Triage, do not just suppress

When a scanner reports a CVE: check whether the vulnerable code path is reachable in your application, upgrade if a fix exists, and only then suppress, with a written reason and an expiry date in the suppression file. A growing suppression file nobody reviews is a finding in itself.

## 5. Encrypting sensitive data at rest

Disk and database encryption (managed by the cloud provider) protect against stolen disks, not against someone who can query the database. For the most sensitive fields (BVN, NIN, card data, account numbers in some contexts), add **application-level encryption**:

- Use authenticated encryption (**AES-256-GCM**) with a unique IV per value, via a well-tested library (Google Tink) or your cloud KMS.
- Use **envelope encryption**: a data key encrypts the data; a master key in a KMS or HSM encrypts the data key. Rotate master keys without re-encrypting everything at once.
- For values you must search by (find a customer by BVN), store a separate keyed hash (HMAC) for lookups.
- For card numbers, prefer **tokenisation** by a PCI-compliant vault or processor, so your systems never store the PAN at all (Phase 2 Capstone 3).

## Common mistakes

- Fast hashes (SHA-256) or reversible encryption for passwords.
- Secrets in `application.yml`, Dockerfiles, CI logs or Postman collections committed to Git.
- Deleting a leaked secret from Git without rotating it.
- One shared database superuser for every service.
- Ignoring Dependabot pull requests for months.
- Suppressing scanner findings with no reason or expiry.
- Homemade encryption (AES-ECB, static IVs, keys in the code).

## Practice

1. Switch your capstone to a `DelegatingPasswordEncoder` with Argon2id as default, and write a test proving a BCrypt hash still verifies and is upgraded on login.
2. Move every secret to environment variables with an `.env.example`; add Gitleaks as a pre-commit hook and a CI job, and prove it blocks a fake AWS key.
3. Add OWASP Dependency-Check with `failBuildOnCVSS=7`, Dependabot for Maven and GitHub Actions, and a CycloneDX SBOM attached to each build.
4. Run HashiCorp Vault in dev mode with Docker and load your database password through Spring Cloud Vault.

## Key terms

Hash, salt, work factor, BCrypt, Argon2id, PBKDF2, `PasswordEncoder`, `DelegatingPasswordEncoder`, password upgrade, credential stuffing, secret, secrets manager, Vault, dynamic credentials, rotation, `.env.example`, secret scanning, Gitleaks, push protection, CVE, CVSS, NVD, OWASP Dependency-Check, Dependabot, dependency review, SBOM, CycloneDX, software supply chain, envelope encryption, KMS, HSM, AES-GCM, tokenisation.

## Further reading

- [OWASP Cheat Sheet: Password Storage](https://cheatsheetseries.owasp.org/cheatsheets/Password_Storage_Cheat_Sheet.html)
- [Spring Security reference: Password storage](https://docs.spring.io/spring-security/reference/features/authentication/password-storage.html)
- [OWASP Cheat Sheet: Secrets Management](https://cheatsheetseries.owasp.org/cheatsheets/Secrets_Management_Cheat_Sheet.html)
- [OWASP Dependency-Check](https://owasp.org/www-project-dependency-check/)
- [Gitleaks](https://github.com/gitleaks/gitleaks)
- [CycloneDX: Software Bill of Materials standard](https://cyclonedx.org/)
