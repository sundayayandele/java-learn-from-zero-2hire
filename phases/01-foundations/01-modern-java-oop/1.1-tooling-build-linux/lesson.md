## What you will be able to do

- Install a Java 21 JDK and explain the difference between the JDK, the JRE and the JVM.
- Compile and run Java from the terminal, and from IntelliJ IDEA.
- Read and edit a Maven `pom.xml` and a Gradle `build.gradle.kts`, and run the standard build lifecycle with the wrapper scripts.
- Move around a Linux machine confidently from the command line: files, permissions, processes, environment variables, pipes and remote login.

## Why this matters in a bank or fintech

Every backend engineer's day starts in three places: the IDE, the build tool and a terminal. In a bank you will build code on a CI server you never see, deploy it to Linux machines you reach only over SSH, and debug it from log files with `grep` and `tail`. Interviewers rarely ask "what is Maven?", but they notice within minutes if you cannot run a build, read a dependency tree or find a log line. This topic gives you those reflexes before you write any real code.

## 1. The JDK, the JRE and the JVM

Java source code (`.java` files) is compiled by `javac` into **bytecode** (`.class` files). Bytecode is not machine code; it runs on the **Java Virtual Machine (JVM)**, which translates it to machine code for whatever operating system and CPU it is running on. That is the "write once, run anywhere" promise: the same `.jar` runs on your Mac, a Windows laptop and a Linux server in a data centre.

| Term | What it is | You need it to... |
| --- | --- | --- |
| **JVM** | The engine that loads, verifies, interprets and JIT-compiles bytecode, and manages memory | Run any Java program |
| **JRE** | The JVM plus the standard class library | Run programs (historical term; modern JDKs no longer ship a separate JRE) |
| **JDK** | The JRE plus developer tools: `javac`, `jar`, `jshell`, `jdeps`, `jcmd`, `jfr` | Build programs. **This is what you install.** |

Java ships a new version every six months. Every two years one of them is a **Long-Term Support (LTS)** release that vendors patch for many years: 8, 11, 17, 21 and 25. Banks standardise on LTS releases. This programme uses **Java 21** as the baseline, because most new enterprise services run on it, and points out where Java 25 adds something useful.

The JDK itself is open source (OpenJDK). Several vendors build and support it. **Eclipse Temurin** (from the Adoptium project) is free, TCK-certified and the default choice for most teams; Amazon Corretto, Microsoft Build of OpenJDK, Azul Zulu and Oracle JDK are equivalent for learning purposes.

### Installing Java 21

The cleanest way on macOS, Linux and WSL is **SDKMAN!**, which lets you install several JDKs side by side and switch between them, which you will need when maintaining older services:

```bash
curl -s "https://get.sdkman.io" | bash
source "$HOME/.sdkman/bin/sdkman-init.sh"
sdk list java            # find the exact Temurin 21 identifier
sdk install java 21.0.8-tem   # use the latest 21.x the list shows
sdk default java 21.0.8-tem
java -version
```

On Windows, use `winget install EclipseAdoptium.Temurin.21.JDK`, or the MSI installer from adoptium.net, then open a new terminal. Either way, check that `java -version` and `javac -version` both print 21, and that `JAVA_HOME` points at the JDK folder: build tools and IDEs read it.

### Your first program, without an IDE

```java
// Interest.java
public class Interest {
    public static void main(String[] args) {
        double principal = 250_000.00;   // naira
        double annualRate = 0.18;        // 18% per year
        int months = 12;
        double interest = principal * annualRate * months / 12;
        System.out.println("Simple interest for one year: " + interest);
    }
}
```

```bash
javac Interest.java     # produces Interest.class
java Interest           # runs it on the JVM
java Interest.java      # since Java 11: compile in memory and run a single file
jshell                  # an interactive Java shell; try 250_000 * 0.18
```

Using `double` for money is a deliberate mistake here. You will see why and fix it with `BigDecimal` in topic 1.3.

## 2. IntelliJ IDEA

IntelliJ IDEA is the IDE most Java teams use. The free edition covers everything in Phases 1 and 2; the paid Ultimate tier adds deeper Spring and database tooling that you can live without. Install it with the JetBrains Toolbox app so updates are painless.

The habits that matter from day one:

- **Open the project folder, not a single file.** IntelliJ detects `pom.xml` or `build.gradle.kts` and imports the project. If it does not, right-click the build file and choose *Add as Maven/Gradle project*.
- **Set the Project SDK** (*File → Project Structure → Project*) to your Java 21 JDK.
- **Learn five shortcuts**: search everywhere (double Shift), find action (Cmd/Ctrl+Shift+A), go to definition (Cmd/Ctrl+B), rename (Shift+F6) and reformat (Cmd/Ctrl+Alt+L). Refactoring with the IDE is safer than editing by hand because it updates every reference.
- **Use the debugger, not `println`.** Click in the gutter to set a breakpoint, run with *Debug*, then step over (F8), step into (F7) and inspect variables. Five minutes with the debugger often saves an hour of guessing.
- **Run builds the way CI does.** The IDE's green "build" button is not your source of truth; `./mvnw verify` in the terminal is, because that is what the pipeline runs.

## 3. Maven

Maven is the most common build tool in enterprise Java. It does four jobs: downloads dependencies, compiles code, runs tests and packages the result, all driven by one declarative file, `pom.xml` (Project Object Model).

### Coordinates and the standard layout

Every artifact has coordinates, usually written `groupId:artifactId:version` (GAV), for example `org.postgresql:postgresql:42.7.14`. Your own project has coordinates too:

```xml
<project xmlns="http://maven.apache.org/POM/4.0.0">
  <modelVersion>4.0.0</modelVersion>
  <groupId>ng.itlearning.bank</groupId>
  <artifactId>interest-calculator</artifactId>
  <version>0.1.0-SNAPSHOT</version>

  <properties>
    <maven.compiler.release>21</maven.compiler.release>
    <project.build.sourceEncoding>UTF-8</project.build.sourceEncoding>
  </properties>

  <dependencies>
    <dependency>
      <groupId>org.junit.jupiter</groupId>
      <artifactId>junit-jupiter</artifactId>
      <version>6.1.3</version>
      <scope>test</scope>
    </dependency>
  </dependencies>
</project>
```

Maven expects a fixed folder layout, and following it means zero configuration:

```text
interest-calculator/
├── pom.xml
├── mvnw, mvnw.cmd, .mvn/        <- the Maven Wrapper (commit these)
└── src/
    ├── main/java/ng/itlearning/bank/...    production code
    ├── main/resources/                      config files
    └── test/java/ng/itlearning/bank/...    tests
```

`SNAPSHOT` versions are "work in progress" and may change; release versions (`1.0.0`) must never change once published.

### The lifecycle

Maven runs **phases** in order. Asking for a later phase runs every earlier one:

`validate → compile → test → package → verify → install → deploy`

| Command | What happens |
| --- | --- |
| `./mvnw compile` | Compiles `src/main/java` |
| `./mvnw test` | Compiles, then runs unit tests |
| `./mvnw package` | ...then builds `target/interest-calculator-0.1.0-SNAPSHOT.jar` |
| `./mvnw verify` | ...then runs integration tests and checks. **This is what CI runs.** |
| `./mvnw install` | ...then copies the jar to your local repository `~/.m2/repository` |
| `./mvnw clean verify` | Deletes `target/` first, for a clean build |

### Dependency scopes and the dependency tree

| Scope | On the compile classpath | In tests | Packaged at runtime | Typical example |
| --- | --- | --- | --- | --- |
| `compile` (default) | yes | yes | yes | Spring, Jackson |
| `provided` | yes | yes | no | Servlet API inside an app server |
| `runtime` | no | yes | yes | JDBC driver |
| `test` | no | yes | no | JUnit, Mockito, Testcontainers |

Dependencies bring their own dependencies (transitive dependencies). When two libraries want different versions of the same jar, Maven picks the one **nearest** to your project in the tree. `./mvnw dependency:tree` shows you the tree; it is the first command to run when you see `NoSuchMethodError` or `ClassNotFoundException` after adding a library. Spring Boot's parent POM (Module 4) manages hundreds of versions for you so these conflicts are rare.

### The Maven Wrapper

`mvnw` is a small script committed to the repository that downloads and runs the exact Maven version the project expects. Everybody, including the CI runner, builds with the same Maven. **Always run `./mvnw`, never a globally installed `mvn`.** Generate it once with `mvn wrapper:wrapper` (or it comes with every project from start.spring.io).

## 4. Gradle

Gradle is the other major build tool; Android and many newer services use it. Instead of XML it uses a script, preferably in Kotlin (`build.gradle.kts`):

```kotlin
plugins {
    java
    application
}

group = "ng.itlearning.bank"
version = "0.1.0-SNAPSHOT"

java { toolchain { languageVersion = JavaLanguageVersion.of(21) } }

repositories { mavenCentral() }

dependencies {
    testImplementation(platform("org.junit:junit-bom:6.1.3"))
    testImplementation("org.junit.jupiter:junit-jupiter")
    testRuntimeOnly("org.junit.platform:junit-platform-launcher")
}

application { mainClass = "ng.itlearning.bank.Interest" }

tasks.test { useJUnitPlatform() }
```

| Maven | Gradle |
| --- | --- |
| `pom.xml` | `build.gradle.kts` + `settings.gradle.kts` |
| `./mvnw verify` | `./gradlew build` |
| `./mvnw test` | `./gradlew test` |
| `compile` / `runtime` / `test` scopes | `implementation` / `runtimeOnly` / `testImplementation` |
| `./mvnw dependency:tree` | `./gradlew dependencies` |
| Phases (fixed lifecycle) | Tasks (a graph you can extend) |
| `target/` | `build/` |

Gradle is faster on large builds because of incremental compilation and its build cache; Maven is more predictable and dominant in banks. You will use Maven for every project in this programme, and you should be able to read and run a Gradle build when you meet one.

## 5. The Linux command line

Production Java runs on Linux. These are the commands you will actually use in your first job. On macOS the Terminal is close enough; on Windows use WSL 2 with Ubuntu.

**Moving around and looking at files**

```bash
pwd                         # where am I?
ls -la                      # list everything, including hidden files, with permissions
cd /var/log/payments        # absolute path
cd ..                       # up one level; cd - goes back
cat application.log         # print a file
less application.log        # page through it (q to quit, / to search)
head -n 20 file; tail -n 50 file
tail -f application.log     # follow a log as it grows: the on-call engineer's best friend
```

**Creating, copying and deleting**

```bash
mkdir -p reports/2026/10    # create nested folders
cp statement.csv backup/    # copy;  mv to move or rename
rm old.csv                  # delete. There is no recycle bin.
rm -r tmp/                  # delete a folder. Read the command twice before pressing Enter.
```

**Searching and pipes**

The pipe `|` sends one command's output into the next. Combining small tools is the Unix superpower:

```bash
grep "ERROR" application.log                       # lines containing ERROR
grep -c "TransferFailed" application.log           # count them
grep -rn "jdbc:postgresql" src/                   # search a whole folder, with line numbers
grep "ERROR" app.log | grep -v "Timeout" | wc -l   # errors that are not timeouts, counted
cut -d, -f3 transactions.csv | sort | uniq -c | sort -rn | head   # most common values in column 3
find . -name "*.sql" -mtime -1                     # SQL files changed in the last day
```

**Permissions**

`ls -l` shows something like `-rwxr-x---`: the owner can read, write and execute, the group can read and execute, everyone else has no access. Change them with `chmod` (`chmod +x mvnw` makes the wrapper executable, a fix you will need when a Windows checkout loses the bit) and ownership with `chown`. `sudo` runs one command as the administrator; use it sparingly.

**Processes, ports and the environment**

```bash
ps aux | grep java          # which Java processes are running?
top                         # live CPU and memory (or htop if installed)
kill 12345                  # ask process 12345 to stop; kill -9 forces it (last resort)
ss -tlnp                    # which ports are listening? Is something already on 8080?
df -h; free -h              # disk and memory
echo $JAVA_HOME             # read an environment variable
export SPRING_PROFILES_ACTIVE=dev   # set one for this shell session
```

**Remote machines and the network**

```bash
ssh deploy@203.0.113.10     # log in to a server (key-based auth; never share private keys)
scp app.jar deploy@203.0.113.10:/opt/app/   # copy a file to it
curl -s http://localhost:8080/actuator/health  # call an HTTP endpoint
```

**Redirection**: `>` writes output to a file (overwriting), `>>` appends, `2>&1` sends errors to the same place. `./mvnw verify > build.log 2>&1` captures a whole build for later reading.

## Worked example: a reproducible interest calculator

1. `mkdir interest-calculator && cd interest-calculator`.
2. Create the `pom.xml` above and `src/main/java/ng/itlearning/bank/Interest.java` with the package line `package ng.itlearning.bank;` at the top.
3. Run `mvn -N wrapper:wrapper` once (needs a global Maven) or copy the wrapper from any Spring Boot project, then `chmod +x mvnw`.
4. `./mvnw package` and run it: `java -cp target/interest-calculator-0.1.0-SNAPSHOT.jar ng.itlearning.bank.Interest`.
5. Open the folder in IntelliJ, set a breakpoint on the `interest` line, and run it in the debugger. Change `months` in the Variables pane and watch the result change.
6. `git init`, add a `.gitignore` containing `target/` and `.idea/`, and commit. The wrapper files are committed; the build output is not.

## Common mistakes

- **Two JDKs fighting.** `java -version` says 21 but Maven compiles with 17. Check `./mvnw -v`, which prints the Java it is using, and fix `JAVA_HOME`.
- **Running `mvn` instead of `./mvnw`.** Works on your laptop, fails on CI with a different Maven version.
- **Committing `target/` or `.idea/`.** Generated files cause merge conflicts and bloat the repository.
- **Editing dependency versions blindly.** Run `dependency:tree` before and after, and read what changed.
- **`rm -rf` with a variable**: `rm -rf $DIR/` deletes `/` if `DIR` is empty. Quote variables and double-check.

## Practice

1. Install Java 21 and a second JDK (17) with SDKMAN. Switch between them with `sdk use java ...` and prove it with `java -version`.
2. Build the interest calculator with Maven, then create the same project with Gradle (`gradle init`, or by hand) and get `./gradlew run` working.
3. Download any public CSV (for example the ECB exchange-rate CSV you will use in Capstone 2) and use only `head`, `cut`, `sort`, `uniq` and `grep` to answer: how many rows are there, and which date has the most entries?
4. Add the `org.postgresql:postgresql` dependency with `runtime` scope to your POM. Run `./mvnw dependency:tree` and explain the output in two sentences.

## Key terms

JDK, JRE, JVM, bytecode, LTS, `JAVA_HOME`, GAV coordinates, lifecycle phase, dependency scope, transitive dependency, wrapper, pipe, redirection, file permissions, SSH.

## Further reading

- [Adoptium: installing Temurin](https://adoptium.net/installation/)
- [Maven: Introduction to the Build Lifecycle](https://maven.apache.org/guides/introduction/introduction-to-the-lifecycle.html)
- [Gradle: Building Java projects](https://docs.gradle.org/current/userguide/building_java_projects.html)
- [IntelliJ IDEA: Getting started](https://www.jetbrains.com/help/idea/getting-started.html)
- [The Linux command line for beginners (Ubuntu tutorial)](https://ubuntu.com/tutorials/command-line-for-beginners)
- [dev.java (Oracle): Getting Started with Java](https://dev.java/learn/first-steps/first-java-code/)
- [Oracle: Java SE 21 documentation home](https://docs.oracle.com/en/java/javase/21/)
