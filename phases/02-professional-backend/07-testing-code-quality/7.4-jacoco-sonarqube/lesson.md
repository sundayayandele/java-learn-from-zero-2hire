## What you will be able to do

- Measure **line and branch coverage** with **JaCoCo** and enforce minimum thresholds in the Maven build.
- Explain what coverage does and does not tell you, and use **mutation testing (PIT)** to measure test strength.
- Run **SonarQube** (or SonarQube Cloud) analysis, read its issues (bugs, vulnerabilities, security hotspots, code smells, duplication) and configure a **quality gate**.
- Add fast static checks to the build (compiler warnings, Error Prone, Checkstyle, ArchUnit) and decide what to enforce.

## Why this matters in a bank or fintech

Banks set measurable quality bars because change is risky and auditors ask for evidence. "No release without 80% coverage on new code and zero new critical issues" is a typical rule, enforced by a quality gate in the pipeline. Knowing how to read and improve these metrics, and knowing their limits, lets you meet the bar without gaming it. SonarQube is installed in most Nigerian banks and consultancies; you will see its dashboard on your first day.

## 1. Coverage with JaCoCo

JaCoCo instruments classes while tests run and reports which lines and branches executed.

```xml
<plugin>
    <groupId>org.jacoco</groupId>
    <artifactId>jacoco-maven-plugin</artifactId>
    <version>0.8.15</version>
    <executions>
        <execution><id>prepare-agent</id><goals><goal>prepare-agent</goal></goals></execution>
        <execution><id>prepare-agent-it</id><goals><goal>prepare-agent-integration</goal></goals></execution>
        <execution><id>report</id><phase>verify</phase><goals><goal>report</goal></goals></execution>
        <execution>
            <id>check</id>
            <phase>verify</phase>
            <goals><goal>check</goal></goals>
            <configuration>
                <rules>
                    <rule>
                        <element>BUNDLE</element>
                        <limits>
                            <limit><counter>LINE</counter><value>COVEREDRATIO</value><minimum>0.80</minimum></limit>
                            <limit><counter>BRANCH</counter><value>COVEREDRATIO</value><minimum>0.70</minimum></limit>
                        </limits>
                    </rule>
                </rules>
                <excludes>
                    <exclude>**/*Application.class</exclude>
                    <exclude>**/config/**</exclude>
                </excludes>
            </configuration>
        </execution>
    </executions>
</plugin>
```

`./mvnw verify` now fails if coverage drops below the thresholds; the HTML report is in `target/site/jacoco/index.html`. Red lines are never executed; yellow diamonds are partially covered branches (an `if` whose `else` never ran).

### What coverage means, and what it does not

- **Low coverage is a reliable warning**: code that never runs in a test is certainly not tested.
- **High coverage is not proof of good tests**: a test that calls `transfer()` with no assertions covers the whole method and checks nothing.
- **Branch coverage** matters more than line coverage for rule-heavy code (fees, limits, eligibility).
- Set thresholds on **new code** where possible (SonarQube does this), so legacy code does not block every change, and the codebase improves as it is touched.
- Exclude what has no logic (generated code, configuration, `main`), but do not exclude code to reach a number.

## 2. Mutation testing with PIT

Mutation testing asks: *if the code were wrong, would a test fail?* PIT makes small changes (**mutants**) to your bytecode (flip `>` to `>=`, replace a return value, remove a call) and runs the tests. A mutant that makes a test fail is **killed**; one that survives shows a gap.

```xml
<plugin>
    <groupId>org.pitest</groupId>
    <artifactId>pitest-maven</artifactId>
    <version>1.30.1</version>
    <dependencies>
        <dependency><groupId>org.pitest</groupId><artifactId>pitest-junit5-plugin</artifactId><version>1.2.3</version></dependency>
    </dependencies>
    <configuration>
        <targetClasses><param>ng.itlearning.wallet.domain.*</param></targetClasses>
        <mutationThreshold>75</mutationThreshold>
    </configuration>
</plugin>
```

Run `./mvnw org.pitest:pitest-maven:mutationCoverage` and open `target/pit-reports`. A surviving mutant such as *"changed conditional boundary: amount > 5000 → amount >= 5000 — SURVIVED"* tells you there is no test exactly at ₦5,000.00. Mutation testing is slower than coverage, so run it on domain packages, nightly or on demand, rather than on every commit.

## 3. SonarQube

SonarQube analyses source code and test reports and classifies findings:

| Type | Meaning | Example |
| --- | --- | --- |
| Bug | Code that is probably wrong | `BigDecimal.equals` used to compare amounts; a resource never closed |
| Vulnerability | Code that is exploitable | SQL built by concatenation; weak cipher |
| Security hotspot | Security-sensitive code a human must review | Use of a random generator, CORS configuration, a disabled CSRF protection |
| Code smell | Maintainability problem | Method with cognitive complexity 42; duplicated block |

Each finding has a **severity**. Metrics include coverage (from the JaCoCo XML report), duplication, and ratings A–E for reliability, security and maintainability.

### Running it

Locally, start SonarQube Community Build with Docker and analyse with the Maven scanner:

```bash
docker run -d --name sonarqube -p 9000:9000 sonarqube:community
./mvnw verify org.sonarsource.scanner.maven:sonar-maven-plugin:sonar \
  -Dsonar.host.url=http://localhost:9000 -Dsonar.token="$SONAR_TOKEN" -Dsonar.projectKey=wallet-api
```

For public GitHub repositories, **SonarQube Cloud** is free and integrates with pull requests (it comments on new issues and reports the quality-gate status as a check).

### Quality gates

A **quality gate** is a set of pass/fail conditions, usually on **new code**:

- Coverage on new code ≥ 80%
- Duplicated lines on new code ≤ 3%
- No new bugs or vulnerabilities rated above "minor"
- All new security hotspots reviewed

In CI, wait for the gate and fail the pipeline if it fails (`-Dsonar.qualitygate.wait=true`). Combined with branch protection (topic 8.1), nothing merges to `main` that fails the gate.

### Handling findings well

- Fix real bugs and vulnerabilities first; they are often small.
- Review hotspots and mark them "safe" **with a comment explaining why**.
- Mark a finding as a false positive only with justification, and discuss recurring false positives with the team (adjust the rule set rather than mass-ignoring).
- Do not refactor code solely to please a metric if it makes the code less clear.

## 4. Fast static checks in the build

Sonar runs after the build. Some checks are cheap enough to run on every compile:

- **Compiler warnings as errors** for important lints: `-Xlint:all -Werror` in `maven-compiler-plugin` (start with selected lints on legacy code).
- **Error Prone** (Google) catches common bug patterns at compile time.
- **Checkstyle** or **Spotless** enforce formatting, so code review is never about spaces.
- **ArchUnit** tests architecture rules as unit tests:

```java
@AnalyzeClasses(packages = "ng.itlearning.wallet")
class ArchitectureTest {
    @ArchTest
    static final ArchRule domainDoesNotDependOnWeb =
            noClasses().that().resideInAPackage("..domain..")
                       .should().dependOnClassesThat().resideInAPackage("..web..");

    @ArchTest
    static final ArchRule controllersDoNotUseRepositories =
            noClasses().that().haveSimpleNameEndingWith("Controller")
                       .should().dependOnClassesThat().haveSimpleNameEndingWith("Repository");
}
```

## 5. Putting it into the pipeline

A typical order in CI (topic 8.3): compile with lints → unit tests → integration tests (Failsafe) → JaCoCo check → Sonar analysis and quality gate → dependency and secret scans → build image. Fast checks first, so feedback arrives quickly.

## Common mistakes

- Chasing 100% coverage with assertion-free tests.
- One global threshold that blocks all work on legacy code, so teams disable it.
- Excluding hard-to-test packages to raise the number.
- Marking every Sonar finding "won't fix".
- Running Sonar but never failing the build on the quality gate.
- Treating security hotspots as noise instead of reviewing them.

## Practice

1. Add JaCoCo with 80% line and 70% branch thresholds to a capstone; find the least covered class and add meaningful tests.
2. Run PIT on your domain package; kill at least five surviving mutants with new boundary tests and record the before/after mutation score.
3. Analyse the capstone with SonarQube (local Docker or SonarQube Cloud), fix all bugs and vulnerabilities, review the hotspots, and configure a quality gate on new code.
4. Add two ArchUnit rules that describe your intended architecture.

## Key terms

Code coverage, line coverage, branch coverage, JaCoCo, `prepare-agent`, `check` goal, coverage threshold, new code, mutation testing, PIT, mutant, killed, survived, mutation score, static analysis, SonarQube, SonarQube Cloud, bug, vulnerability, security hotspot, code smell, technical debt, cognitive complexity, duplication, quality gate, Error Prone, Checkstyle, Spotless, ArchUnit.

## Further reading

- [JaCoCo documentation: Maven plugin](https://www.jacoco.org/jacoco/trunk/doc/maven.html)
- [PIT mutation testing: Quickstart for Maven](https://pitest.org/quickstart/maven/)
- [SonarQube documentation: Quality gates](https://docs.sonarsource.com/sonarqube-server/latest/instance-administration/analysis-functions/quality-gates/)
- [SonarScanner for Maven](https://docs.sonarsource.com/sonarqube-server/latest/analyzing-source-code/scanners/sonarscanner-for-maven/)
- [ArchUnit user guide](https://www.archunit.org/userguide/html/000_Index.html)
- [Martin Fowler: Test Coverage](https://martinfowler.com/bliki/TestCoverage.html)
