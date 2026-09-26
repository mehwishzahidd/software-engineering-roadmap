# Maven — Résumé Defense

**Target level:** L2 (L3 on dependency conflicts and test phases) · **Learned:** Week 3 (P1 is a Maven project from day one); deepened Week 12 (Surefire/Failsafe in CI) · **Version:** Maven 3.9.x
Method: [`../RESUME_TECH_DEFENSE.md`](../RESUME_TECH_DEFENSE.md) · Index: [`README.md`](./README.md)

---

## 1. Beginner questions

<details><summary><b>Q1. What is Maven?</b></summary>

A build and dependency-management tool for Java. Declarative `pom.xml` describes the project
(coordinates, dependencies, plugins); Maven downloads dependencies from repositories (Maven Central)
and runs a standard lifecycle to compile, test, and package.
</details>

<details><summary><b>Q2. What are Maven coordinates?</b></summary>

`groupId:artifactId:version` (GAV), plus optional `packaging` (jar, war, pom) and `classifier`.
E.g. `org.postgresql:postgresql:42.7.x`.
</details>

<details><summary><b>Q3. What is the standard directory layout?</b></summary>

`src/main/java`, `src/main/resources`, `src/test/java`, `src/test/resources`, output in `target/`.
Convention over configuration: follow it and plugins need no config.
</details>

<details><summary><b>Q4. Name the default lifecycle phases in order.</b></summary>

Key ones: `validate → compile → test → package → verify → install → deploy`. Running a phase
runs all earlier phases. `clean` is a separate lifecycle.
</details>

<details><summary><b>Q5. What's the local repository?</b></summary>

`~/.m2/repository` — cache of downloaded artifacts and anything you `mvn install`. Remote repos
(Central, company Nexus/Artifactory) are consulted when missing.
</details>

<details><summary><b>Q6. What does the Maven Wrapper (<code>mvnw</code>) do?</b></summary>

Script + `.mvn/wrapper/maven-wrapper.properties` that downloads a pinned Maven version, so every
developer and CI uses the same Maven. Commit it; run `./mvnw verify`.
</details>

## 2. Intermediate questions

<details><summary><b>Q7. Explain dependency scopes.</b></summary>

`compile` (default; compile + runtime + transitive), `provided` (needed to compile, supplied at
runtime by container, e.g. Lombok/servlet API), `runtime` (not for compile, e.g. JDBC driver),
`test` (test classpath only, e.g. JUnit, Mockito), `import` (only in `dependencyManagement` for BOMs),
`system` (avoid).
</details>

<details><summary><b>Q8. How are transitive dependency conflicts resolved?</b></summary>

"Nearest wins": the version closest to your project in the dependency tree; ties → first declared.
Not "highest wins." Control it with `<dependencyManagement>` (pins versions), BOM imports, or
`<exclusions>`. Diagnose with `mvn dependency:tree -Dverbose` (or `-Dincludes=groupId:artifactId`).
</details>

<details><summary><b>Q9. <code>dependencies</code> vs <code>dependencyManagement</code>?</b></summary>

`dependencies` actually adds a dependency. `dependencyManagement` only declares versions/scopes
to be used *if* the dependency is added (by you or transitively). Parent POMs and BOMs (e.g.
Spring Boot's) use it so you can omit versions.
</details>

<details><summary><b>Q10. Plugins vs goals vs phases?</b></summary>

Plugins provide goals (e.g. `maven-compiler-plugin:compile`). Goals are bound to lifecycle phases
(by packaging defaults or `<executions>`). `mvn test` runs the phase; `mvn dependency:tree` runs a goal directly.
</details>

<details><summary><b>Q11. Surefire vs Failsafe?</b></summary>

Surefire runs unit tests (`*Test`, `*Tests`, `Test*`, `*TestCase`) in the `test` phase and fails the build immediately.
Failsafe runs integration tests (`*IT`, `IT*`, `*ITCase`) in `integration-test`, then checks results in `verify`, so `post-integration-test`
cleanup still runs. That's why CI runs `mvn verify`, not `mvn test`.
</details>

<details><summary><b>Q12. How do you set the Java version?</b></summary>

`<properties><maven.compiler.release>21</maven.compiler.release></properties>` (the `release`
flag also checks API usage against that JDK). Spring Boot parent: `<java.version>21</java.version>`.
</details>

<details><summary><b>Q13. What are multi-module projects?</b></summary>

A parent `pom` with `<packaging>pom</packaging>` and `<modules>`; each module has its own pom
inheriting shared config. Reactor builds them in dependency order. `mvn -pl api -am verify` builds one module plus what it needs.
</details>

## 3. Realistic interview questions

<details><summary><b>R1. "How was your build set up in your previous role?"</b></summary>

- Describe truthfully what you remember (Maven vs Gradle, CI server, whether you edited the pom or just ran builds).
- If you only ran builds: say so, then: "Since then I've set up builds myself — Ledger's pom from
  scratch, and TicketHold uses the Boot parent, Surefire + Failsafe, and `mvn verify` in GitHub Actions."
</details>

<details><summary><b>R2. "You get a <code>NoSuchMethodError</code> at runtime but it compiled fine. What's going on?"</b></summary>

- Classic version conflict: compiled against version A, a different version B won on the runtime classpath.
- `mvn dependency:tree -Dverbose -Dincludes=<group>` → find which path brings which version → pin in
  `dependencyManagement` or add an exclusion → rerun tests.
</details>

<details><summary><b>R3. "Maven or Gradle — which would you pick?"</b></summary>

- Maven: declarative, predictable, ubiquitous in enterprise Java, easy for newcomers.
- Gradle: faster (incremental builds, build cache, daemon), flexible Kotlin DSL, standard for Android.
- "For a Spring Boot service with a small team I'd pick Maven for predictability; I'd consider Gradle for large multi-module builds where build time hurts."
</details>

<details><summary><b>R4. "What's in a Spring Boot fat JAR, and how is it built?"</b></summary>

- `spring-boot-maven-plugin` `repackage` goal: your classes in `BOOT-INF/classes`, dependencies in
  `BOOT-INF/lib`, a launcher as `Main-Class` that sets up a nested-JAR class loader and calls your `Start-Class`.
- For Docker, layered JARs (`java -Djarmode=tools -jar app.jar extract --layers` in Boot 3.3+, `layertools` earlier) improve cache reuse.
</details>

<details><summary><b>R5. "How do you speed up a slow Maven build in CI?"</b></summary>

- Cache `~/.m2` (`actions/setup-java` with `cache: maven`), `-B` batch mode, `-T 1C` parallel modules,
  run only needed modules, split unit/integration stages, avoid `clean` if not needed, `-o` offline where possible.
</details>

## 4. Practical tasks (live)

- [ ] Create a Java 21 project from scratch (`mvn archetype:generate` or hand-written pom) with JUnit 5 and run `mvn test`.
- [ ] Add a dependency, then find and explain a transitive conflict with `mvn dependency:tree`.
- [ ] Configure Failsafe so `*IT` tests run in `mvn verify` but not `mvn test`.
- [ ] Build an executable JAR (`maven-jar-plugin` manifest `mainClass` or Boot plugin) and run it with `java -jar`.
- [ ] Run a single test: `mvn -Dtest=CsvImporterTest#rejectsBadDate test`.
- [ ] Add the Maven Wrapper (`mvn wrapper:wrapper`) and commit it.

## 5. Debugging questions

<details><summary><b>D1. "Could not resolve dependencies… not found in central".</b></summary>

Typo in GAV, version doesn't exist, needs a private repo not configured in `settings.xml`, or a
cached failure (`.lastUpdated` files) — rerun with `-U` to force update. Check proxy/network.
</details>

<details><summary><b>D2. Tests pass in the IDE but <code>mvn test</code> runs 0 tests.</b></summary>

Old Surefire version without JUnit Platform support (use ≥ 3.x; 3.9 defaults are fine), test class
naming doesn't match patterns, JUnit 4 vs 5 mixed without vintage engine, or tests are in `src/main/java`.
</details>

<details><summary><b>D3. "invalid target release: 21".</b></summary>

Maven is running on an older JDK. Check `mvn -v` (shows the JDK Maven uses) and `JAVA_HOME`. In CI set `setup-java` to 21.
</details>

<details><summary><b>D4. Build works locally, fails in CI with a different dependency version.</b></summary>

Unpinned/`SNAPSHOT` or version ranges, or a locally `install`ed artifact CI doesn't have. Pin
versions, avoid ranges, use the wrapper, and reproduce with a clean `~/.m2` or `mvn -U`.
</details>

## 6. Architecture questions

- When would you split Ledger into modules (`ledger-core`, `ledger-cli`, `ledger-jdbc`)? What does it enforce? (Dependency direction: core depends on nothing.)
- How do you share dependency versions across several services (company parent POM vs BOM)?
- How would you structure the build so integration tests with Testcontainers don't slow down every developer run?

## 7. Common mistakes

- Running `mvn install` in CI when `verify` is enough.
- Hardcoding versions everywhere instead of properties/BOMs.
- Putting test libraries in `compile` scope (they ship in the JAR).
- Using `system` scope for local JARs.
- Not committing the wrapper; relying on whatever Maven is installed.
- Believing "newest version wins" in conflicts.

## 8. Terminology I must know

| Term | One-line meaning |
|---|---|
| POM | Project Object Model — `pom.xml` describing the build |
| GAV | groupId, artifactId, version coordinates |
| Lifecycle / phase | Ordered build stages; running one runs all before it |
| Goal | A single plugin task (`plugin:goal`) |
| Scope | Which classpaths a dependency is on |
| Transitive dependency | Dependency of a dependency |
| BOM | POM listing managed versions to import |
| Parent POM | Inherited configuration |
| Reactor | Multi-module build orchestrator |
| SNAPSHOT | Mutable, in-development version |
| Surefire / Failsafe | Unit / integration test runners |
| Effective POM | Fully merged POM (`mvn help:effective-pom`) |

## 9. When to use it

Standard Java/Spring projects, teams wanting predictable, declarative builds, and anywhere the ecosystem expects it.

## 10. When NOT to use it

Highly custom build logic (Gradle is more flexible), Android (Gradle), very large monorepos
needing aggressive incremental/remote caching (Gradle/Bazel), non-JVM projects.

## 11. Trade-offs

| Gain | Cost |
|---|---|
| Convention, readability, predictable lifecycle | Verbose XML; custom logic is awkward |
| Huge plugin ecosystem, universal CI support | Slower than Gradle for incremental builds |
| BOMs make version alignment easy | Nearest-wins resolution can surprise |

## 12. How it interacts with the rest of my stack

- **Java**: compiles with `release 21`; packages the JAR.
- **Spring Boot**: parent POM manages hundreds of versions; Boot plugin creates the executable JAR and can build OCI images (`spring-boot:build-image`).
- **JUnit**: Surefire/Failsafe discover and run JUnit Platform tests.
- **PostgreSQL**: driver as `runtime` scope; Testcontainers as `test` scope.
- **Docker**: multi-stage Dockerfile runs `./mvnw -B package -DskipTests` (tests already ran in CI) in the build stage; copying `pom.xml` first and running `dependency:go-offline` caches dependency layers.
- **CI**: `./mvnw -B verify` is the gate; `~/.m2` cached between runs.

## 13. Hands-on exercise

**Build a 2-module Ledger skeleton.**

Acceptance criteria:
- [ ] Parent pom (`packaging pom`) with `ledger-core` and `ledger-cli` modules; versions in parent `dependencyManagement`.
- [ ] `ledger-cli` depends on `ledger-core`; core has no dependency on cli.
- [ ] `mvn verify` runs unit tests via Surefire and one `*IT` via Failsafe.
- [ ] `java -jar ledger-cli/target/ledger-cli-*.jar --help` runs.
- [ ] You can show and explain one transitive dependency using `dependency:tree`.

## 14. Mastery checklist

- [ ] Recite lifecycle phases and what `mvn verify` does.
- [ ] Explain all scopes with an example each.
- [ ] Explain nearest-wins and fix a conflict with `dependencyManagement`/exclusion.
- [ ] Configure Surefire + Failsafe.
- [ ] Explain parent POM vs BOM.
- [ ] Use `-pl`, `-am`, `-Dtest`, `-U`, `-o`, `-B`.
- [ ] Explain how the Boot fat JAR works.

## Evidence in my projects

| Project | What it demonstrates | Fill in: file / commit |
|---|---|---|
| P1 Ledger | Hand-written `pom.xml` from Week 3, JUnit 5, executable CLI JAR, Postgres driver `runtime` scope | |
| P2 TicketHold | Boot parent, Surefire + Failsafe/Testcontainers, `mvn verify` in GitHub Actions, JAR in Dockerfile | |
| P4 PulseWatch | Maven cache in CI/CD pipeline, image build after tests | |

## Where to learn it in this repo

[`../01-java/08-maven-build.md`](../01-java/08-maven-build.md) · [`../09-testing/junit5.md`](../09-testing/junit5.md) ·
[`../09-testing/testcontainers.md`](../09-testing/testcontainers.md) · [`../13-cicd/github-actions.md`](../13-cicd/github-actions.md) ·
[`../11-docker/dockerfiles.md`](../11-docker/dockerfiles.md)
