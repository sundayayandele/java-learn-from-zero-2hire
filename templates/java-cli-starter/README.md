# Java CLI starter (Module 1 projects)

A minimal Java 21 Maven project with JUnit, AssertJ, the Maven wrapper, GitHub Actions (primary) and GitLab CI (secondary). Use it for the Module 1 and Module 2 projects.

```bash
./mvnw verify                                   # compile and test
java -jar target/java-cli-starter-0.1.0-SNAPSHOT.jar 250000 18 12
```

Rename the package `ng.itlearning.cli` and the `artifactId` for your project, replace `Main` and `Interest` with your own code, and keep the tests green on every push.
