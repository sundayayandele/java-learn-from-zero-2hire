> **Why this topic is here.** "Packaging and Deploying Java Code" is an objective group in the Oracle OCP Java SE 21 and 25 exams: modules, services, jars, runtime images and migration. It also explains what the JDK itself is made of, which helps when containers and upgrades misbehave (topics 1.8 and 2.4). See the [certification mapping](../../../../docs/certifications.md).

## What you will be able to do

- Compile and run Java code from the command line: classpath, single-file and multi-file source launch, and (Java 25) compact source files.
- Build executable and library jars, and explain the classpath versus the module path.
- Write `module-info.java` with `requires`, `exports`, `opens`, `uses` and `provides`, including transitive and qualified forms.
- Use services with `ServiceLoader` (providers and consumers).
- Migrate code with unnamed and automatic modules, and create a trimmed runtime image with `jdeps` and `jlink`.

## Why this matters in a bank or fintech

Spring Boot fat jars run on the classpath, so many teams never write a `module-info.java`. But the JDK is modular, and you meet the module system constantly: `InaccessibleObjectException` after an upgrade (topic 1.8), `--add-opens` flags in old Dockerfiles, and libraries published as named modules. `jlink` builds small, patched runtimes for containers, shrinking the attack surface security teams care about. The OCP exam tests all of this directly.

## 1. Compiling and running

```bash
javac -d out src/ng/itlearning/fx/Main.java src/ng/itlearning/fx/Rates.java   # compile to out/
java -cp out ng.itlearning.fx.Main                                             # run from the classpath
java src/ng/itlearning/fx/Main.java                                            # single-file source launch (Java 11+)
java -cp lib/* src/Main.java                                                   # with dependencies on the classpath
```

- Since **Java 22** the source launcher runs **multi-file** programs: `java Main.java` compiles other source files it references in the same directory tree.
- Since **Java 25**, **compact source files** and **instance main methods** let a small program be just:

  ```java
  void main() {
      IO.println("Hello from a compact source file");
  }
  ```

  with no class declaration and no `static`; it automatically imports `java.base` packages. Good for scripts and teaching; services still use normal classes.

## 2. Jars

```bash
jar --create --file fx.jar -C out .                                     # library jar
jar --create --file fx-app.jar --main-class ng.itlearning.fx.Main -C out .   # executable jar (Main-Class in MANIFEST.MF)
java -jar fx-app.jar
jar --list --file fx-app.jar
jar --describe-module --file fx.jar                                     # module descriptor, if any
```

Maven does this for you (`./mvnw package`); the Spring Boot plugin builds a nested "fat" jar with its own launcher (topic 4.1).

## 3. The module system (JPMS)

Since Java 9 the JDK is split into ~70 modules (`java.base`, `java.sql`, `java.net.http`, `jdk.jfr`...). A **module** is a named group of packages with a descriptor, `module-info.java`, at the source root:

```java
module ng.itlearning.fx {
    requires java.net.http;                         // I depend on this module
    requires transitive ng.itlearning.money;        // and so does anyone who requires me (it's in my API)
    requires static com.github.spotbugs.annotations;   // needed at compile time only

    exports ng.itlearning.fx.api;                   // public types in this package are accessible to everyone
    exports ng.itlearning.fx.spi to ng.itlearning.fx.ecb;   // qualified export: only to this module
    opens ng.itlearning.fx.dto to tools.jackson.databind;     // allow deep reflection (Jackson 3; Jackson 2 is com.fasterxml.jackson.databind)

    uses ng.itlearning.fx.spi.RateProvider;         // I consume this service
    provides ng.itlearning.fx.spi.RateProvider with ng.itlearning.fx.internal.CachedProvider;   // and provide one
}
```

What modules give you:

- **Strong encapsulation:** packages that aren't exported are inaccessible to other modules, even their `public` types. Internal packages stay internal.
- **Reliable configuration:** missing or duplicate modules fail at startup, not with `NoClassDefFoundError` at 3 a.m.
- **Explicit reflection:** frameworks need `opens` to access private members; `exports` alone only allows public access at compile and run time.

`opens` versus `exports`: `exports` gives compile-time and runtime access to public members; `opens` gives runtime-only reflective access to all members (including private). A whole module can be declared `open module ...`.

Compile and run on the **module path**:

```bash
javac -d mods/ng.itlearning.fx --module-path libs $(find src -name '*.java')
java --module-path mods:libs --module ng.itlearning.fx/ng.itlearning.fx.Main
java --list-modules                                   # modules in this JDK
java --describe-module java.sql
```

Java 25 also adds **module import declarations**: `import module java.sql;` imports every package that module exports, which is handy in small programs.

## 4. Services: decoupling providers from consumers

A service is an interface (or abstract class) in one module, with implementations in others, discovered at runtime:

```java
// in module ng.itlearning.fx (consumer): uses ng.itlearning.fx.spi.RateProvider;
ServiceLoader<RateProvider> providers = ServiceLoader.load(RateProvider.class);
for (RateProvider p : providers) {
    System.out.println(p.source() + " → " + p.rate("USD", "NGN"));
}
// or providers.stream().map(ServiceLoader.Provider::get)...

// in module ng.itlearning.fx.ecb (provider):
// provides ng.itlearning.fx.spi.RateProvider with ng.itlearning.fx.ecb.EcbRateProvider;
```

The consumer never names the implementation; add a provider module to the module path and it's discovered. On the classpath the same works with `META-INF/services/<interface name>` files. (JDBC drivers, logging back ends and Spring Boot's auto-configuration loading use the same idea.)

## 5. Migration: unnamed and automatic modules

You rarely modularise a large application in one step:

| Kind | How it arises | Reads | Exports |
| --- | --- | --- | --- |
| **Named module** | Jar with `module-info.class`, on the module path | What it `requires` | What it `exports` |
| **Automatic module** | Plain jar (no descriptor) placed on the **module path**; name from `Automatic-Module-Name` in the manifest, or derived from the file name | Every module | All its packages |
| **Unnamed module** | Everything on the **classpath** | Every module | All packages, but named modules can't `require` it |

Two migration strategies:

- **Bottom-up:** modularise libraries with no dependencies first, then the modules that use them.
- **Top-down:** put the application's jar on the module path as a named module, and its not-yet-modular dependencies on the module path as automatic modules; replace them as they gain descriptors.

`jdeps` analyses what each jar needs (`jdeps --print-module-deps app.jar`, `jdeps --generate-module-info out lib.jar`), and `--add-opens`/`--add-exports` are temporary escape hatches, to be removed as libraries update (topic 1.8).

## 6. Runtime images with `jlink`

`jlink` assembles a custom JRE containing only the modules your application needs:

```bash
jdeps --ignore-missing-deps --print-module-deps --multi-release 21 target/app.jar
# e.g. java.base,java.desktop,java.instrument,java.management,java.naming,java.net.http,java.prefs,java.security.jgss,java.sql,jdk.jfr,jdk.unsupported

jlink --add-modules java.base,java.sql,java.naming,java.management,java.net.http,jdk.unsupported,jdk.crypto.ec \
      --strip-debug --no-man-pages --no-header-files --compress zip-6 --output build/runtime
build/runtime/bin/java -jar target/app.jar
```

A Spring Boot service's jlinked runtime is often well under half the size of a full JDK. Smaller images pull faster and contain fewer components that could need patching. A multi-stage Dockerfile can run `jlink` in the build stage and copy the runtime into a minimal base image (Phase 2, Module 8). `jpackage` goes one step further and creates installers for desktop applications.

## Worked example: a pluggable FX rate source

```text
fx-api/        module ng.itlearning.fx.api        exports ng.itlearning.fx.spi (RateProvider)
fx-ecb/        module ng.itlearning.fx.ecb        requires ng.itlearning.fx.api; provides RateProvider with EcbRateProvider
fx-treasury/   module ng.itlearning.fx.treasury   requires ng.itlearning.fx.api; provides RateProvider with TreasuryRateProvider
fx-app/        module ng.itlearning.fx.app        requires ng.itlearning.fx.api; uses RateProvider
```

`fx-app` loads whatever providers are on the module path, picks the first that supports a currency pair, and never imports an implementation class. Dropping `fx-treasury.jar` onto the module path adds NGN support without recompiling the app, which is the open/closed principle (topic 1.3) at the deployment level.

## Common mistakes

- Expecting `public` to mean accessible across modules (the package must be exported).
- Using `exports` when a framework needs reflection (`opens`), or opening everything "to make it work".
- Split packages: the same package in two modules is an error.
- Leaving `--add-opens` flags in production long after the library was fixed.
- Forgetting that classpath code is in the unnamed module and can't be `require`d by named modules.

## Practice

1. Compile and run a two-class program with `javac`/`java -cp`, then run it directly as a multi-file source program, then (on a Java 25 JDK) as a compact source file.
2. Build the pluggable FX example as four modules with plain `javac` and `java --module-path`, and show that removing a provider jar changes the output without recompiling.
3. Write a `module-info.java` with a qualified export and an `opens` for Jackson, and observe the error when a non-listed module tries to use the package.
4. Run `jdeps` on a Spring Boot capstone jar, build a `jlink` runtime with the reported modules, and compare its size with the full JDK image.

## Key terms

Classpath, module path, source launcher, compact source file, instance main method, jar, manifest, JPMS, `module-info.java`, `requires`, `requires transitive`, `requires static`, `exports`, qualified export, `opens`, `uses`, `provides ... with`, `ServiceLoader`, named/automatic/unnamed module, split package, `jdeps`, `jlink`, runtime image, `--add-opens`.

## Further reading

- [Oracle: Understanding Java 9 Modules](https://www.oracle.com/corporate/features/understanding-java-9-modules.html)
- [JEP 261: Module System](https://openjdk.org/jeps/261)
- [`jlink` tool reference (Java 21)](https://docs.oracle.com/en/java/javase/21/docs/specs/man/jlink.html)
- [JEP 458: Launch Multi-File Source-Code Programs](https://openjdk.org/jeps/458)
- [JEP 512: Compact Source Files and Instance Main Methods](https://openjdk.org/jeps/512)
