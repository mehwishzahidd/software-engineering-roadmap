# 08 — Maven & Builds (Week 2 · multi-module W14 · publishing an artifact W22)

> **Outcome:** create a Maven project from scratch without an archetype; read any `pom.xml`;
> explain coordinates, scopes, the lifecycle, and what `mvn verify` actually runs; resolve a
> dependency conflict with `mvn dependency:tree`; use the Maven Wrapper; know how Gradle differs.

Related: [README](./README.md) · résumé defense [../17-resume-tech-defense/maven.md](../17-resume-tech-defense/maven.md) · CI usage [../13-cicd/github-actions.md](../13-cicd/github-actions.md) · tests [../09-testing/junit5.md](../09-testing/junit5.md)

---

## 1. What Maven does

1. **Dependency management** — declare coordinates; Maven downloads jars (and their transitive dependencies) from Maven Central into `~/.m2/repository`.
2. **Standard build lifecycle** — the same commands (`mvn test`, `mvn package`) work on every project.
3. **Convention over configuration** — standard layout means zero config for most builds.

```
java-katas/
├── pom.xml
├── mvnw, mvnw.cmd, .mvn/wrapper/maven-wrapper.properties
├── src/main/java/com/example/katas/...         production code
├── src/main/resources/                           config, schema.sql
├── src/test/java/com/example/katas/...         unit tests (*Test.java)
├── src/test/resources/                           test fixtures (sample CSVs)
└── target/                                       build output — .gitignore it
```

---

## 2. POM anatomy (the Week 2 kata project)

```xml
<?xml version="1.0" encoding="UTF-8"?>
<project xmlns="http://maven.apache.org/POM/4.0.0"
         xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance"
         xsi:schemaLocation="http://maven.apache.org/POM/4.0.0 https://maven.apache.org/xsd/maven-4.0.0.xsd">
  <modelVersion>4.0.0</modelVersion>

  <!-- Coordinates: groupId:artifactId:version (GAV) -->
  <groupId>com.example</groupId>
  <artifactId>java-katas</artifactId>
  <version>1.0.0-SNAPSHOT</version>
  <packaging>jar</packaging>

  <properties>
    <maven.compiler.release>21</maven.compiler.release>
    <project.build.sourceEncoding>UTF-8</project.build.sourceEncoding>
    <junit.version>5.10.2</junit.version>
  </properties>

  <dependencies>
    <dependency>
      <groupId>org.postgresql</groupId>
      <artifactId>postgresql</artifactId>
      <version>42.7.3</version>
      <scope>runtime</scope>            <!-- code uses java.sql interfaces only -->
    </dependency>
    <dependency>
      <groupId>org.junit.jupiter</groupId>
      <artifactId>junit-jupiter</artifactId>
      <version>${junit.version}</version>
      <scope>test</scope>
    </dependency>
  </dependencies>

  <build>
    <plugins>
      <plugin>
        <groupId>org.apache.maven.plugins</groupId>
        <artifactId>maven-surefire-plugin</artifactId>
        <version>3.2.5</version>
      </plugin>
      <plugin>
        <groupId>org.apache.maven.plugins</groupId>
        <artifactId>maven-jar-plugin</artifactId>
        <version>3.4.1</version>
        <configuration>
          <archive>
            <manifest>
              <mainClass>com.example.katas.KataCli</mainClass>
            </manifest>
          </archive>
        </configuration>
      </plugin>
    </plugins>
  </build>
</project>
```

(Version numbers shown are real releases at time of writing; check Maven Central for current ones.
Pin plugin versions — otherwise Maven's defaults change underneath you.)

| Element | Purpose |
|---|---|
| `groupId` | Organization / reverse domain (`org.springframework.boot`) |
| `artifactId` | Project name (`java-katas`, `flowgrid`) |
| `version` | `-SNAPSHOT` = in development, mutable; release versions are immutable |
| `packaging` | `jar` (default), `war`, `pom` (parent/aggregator) |
| `properties` | Variables (`${junit.version}`) and plugin settings (`maven.compiler.release`) |
| `dependencies` | What your code needs |
| `dependencyManagement` | Version *declarations* only — pins versions for children/transitives without adding deps |
| `build/plugins` | Configure plugins bound to lifecycle phases |
| `parent` | Inherit configuration (Spring Boot's `spring-boot-starter-parent`) |

`maven.compiler.release=21` compiles for Java 21 **and** checks you don't use APIs newer than 21 (better than `source`/`target`).

---

## 3. Dependency scopes

| Scope | Compile classpath | Test classpath | Runtime / packaged | Transitive? | Example |
|---|:-:|:-:|:-:|:-:|---|
| `compile` (default) | ✔ | ✔ | ✔ | ✔ | `spring-boot-starter-web` |
| `provided` | ✔ | ✔ | ✘ (container provides) | ✘ | `jakarta.servlet-api` for a WAR, Lombok |
| `runtime` | ✘ | ✔ | ✔ | ✔ | JDBC driver (`postgresql`) |
| `test` | ✘ | ✔ | ✘ | ✘ | JUnit, Mockito, Testcontainers |
| `import` | only in `<dependencyManagement>` with `<type>pom</type>` — imports a BOM | | | | `spring-boot-dependencies`, `testcontainers-bom` |

Using `runtime` for the JDBC driver is a design statement: your code compiles against `java.sql`
interfaces only and can't accidentally depend on `org.postgresql.*` classes.

---

## 4. The build lifecycle

Maven has three lifecycles: `clean`, `default`, `site`. The **default** lifecycle's key phases, in order:

```
validate → compile → test-compile → test → package → integration-test → verify → install → deploy
            (+ process-resources, process-test-resources, pre-/post-integration-test between them)
```

**Running a phase runs every earlier phase.** `mvn package` = validate + compile + test + package.

| Command | What happens | When you use it |
|---|---|---|
| `mvn clean` | Deletes `target/` | Stale build weirdness |
| `mvn compile` | Compiles `src/main/java` | Quick syntax check |
| `mvn test` | + compiles and runs unit tests (surefire) | Constantly |
| `mvn package` | + builds `target/java-katas-1.0.0-SNAPSHOT.jar` | Produce the artifact |
| `mvn verify` | + integration tests (failsafe) + checks | **CI** (every project's GitHub Actions from FlowGrid W4: `./mvnw -B verify`) |
| `mvn install` | + copies the jar into `~/.m2/repository` | Another local project depends on it |
| `mvn deploy` | + uploads to a remote repository | Releasing libraries |

**Phases vs goals:** a *plugin goal* (e.g. `compiler:compile`, `surefire:test`) does the work; phases
are ordered hooks that goals bind to. You can run a goal directly: `mvn dependency:tree`,
`mvn spring-boot:run`.

Useful flags: `-B` (batch, no colors — CI), `-q` (quiet), `-DskipTests`, `-Dtest=CsvImporterTest#rejectsBadDate`,
`-pl module -am` (build one module + what it needs), `-U` (force update snapshots), `-o` (offline), `-X` (debug output).

---

## 5. Plugins you must know

| Plugin | Bound to | Does | Notes |
|---|---|---|---|
| `maven-compiler-plugin` | `compile`, `test-compile` | Runs `javac` | Configure via `maven.compiler.release` |
| `maven-surefire-plugin` | `test` | Runs **unit** tests: `**/*Test.java`, `**/Test*.java`, `**/*Tests.java`, `**/*TestCase.java` | A failing test fails the build immediately |
| `maven-failsafe-plugin` | `integration-test`, `verify` | Runs **integration** tests: `**/*IT.java`, `**/IT*.java`, `**/*ITCase.java` | Runs in `integration-test`, but only fails the build in `verify` → `post-integration-test` cleanup (stop DB) still runs |
| `maven-jar-plugin` | `package` | Builds the jar; can set `Main-Class` | Plain jar does **not** include dependencies |
| `spring-boot-maven-plugin` | `package` (`repackage` goal) | Builds an executable **fat jar** with all deps + a launcher; `spring-boot:run`; `build-image` | `java -jar app.jar` works |
| `maven-shade-plugin` / `maven-assembly-plugin` | `package` | Fat jar for non-Spring apps | Kata CLI alternative |
| `maven-dependency-plugin` | — | `tree`, `analyze`, `copy-dependencies` | Debugging deps |
| `jacoco-maven-plugin` | `test`/`verify` | Coverage reports | Optional (FlowGrid onward) |

Failsafe needs explicit goals:

```xml
<plugin>
  <groupId>org.apache.maven.plugins</groupId>
  <artifactId>maven-failsafe-plugin</artifactId>
  <version>3.2.5</version>
  <executions>
    <execution>
      <goals>
        <goal>integration-test</goal>
        <goal>verify</goal>
      </goals>
    </execution>
  </executions>
</plugin>
```

FlowGrid M2: `ReservationConcurrencyIT` (Testcontainers Postgres) runs under failsafe with
`mvn verify`, while fast unit tests run under surefire with `mvn test`.

### Spring Boot projects (Week 3 intro API, then every project)

```xml
<parent>
  <groupId>org.springframework.boot</groupId>
  <artifactId>spring-boot-starter-parent</artifactId>
  <version>3.3.0</version>
  <relativePath/>
</parent>
...
<dependencies>
  <dependency>
    <groupId>org.springframework.boot</groupId>
    <artifactId>spring-boot-starter-web</artifactId>   <!-- no version: managed by the parent's BOM -->
  </dependency>
</dependencies>
<build>
  <plugins>
    <plugin>
      <groupId>org.springframework.boot</groupId>
      <artifactId>spring-boot-maven-plugin</artifactId>
    </plugin>
  </plugins>
</build>
```
The parent provides `dependencyManagement` for hundreds of libraries (tested-together versions),
plugin configuration and sensible defaults. Starters are curated dependency bundles.

---

## 6. Transitive dependencies and conflicts

You declare A; A depends on B; B depends on C → you get all three. When two paths bring
**different versions** of the same artifact, Maven picks one by **nearest wins** (shortest path
in the tree); on a tie, **first declared wins**. It does *not* pick the highest version.

```bash
mvn dependency:tree
mvn dependency:tree -Dincludes=com.fasterxml.jackson.core   # filter
mvn dependency:tree -Dverbose                                # show omitted/conflicting versions
mvn dependency:analyze                                       # used-undeclared / declared-unused
```

```
[INFO] com.example:java-katas:jar:1.0.0-SNAPSHOT
[INFO] +- com.example:csv-lib:jar:2.1:compile
[INFO] |  \- com.google.guava:guava:jar:20.0:compile         ← nearest (depth 2) → wins
[INFO] \- com.example:report-lib:jar:3.0:compile
[INFO]    \- com.example:charts:jar:1.4:compile
[INFO]       \- (com.google.guava:guava:jar:33.0-jre:compile - omitted for conflict with 20.0)
```
Symptom at runtime: `NoSuchMethodError` / `NoClassDefFoundError` / `ClassNotFoundException` because `charts` was compiled against Guava 33.

Fixes, in order of preference:

```xml
<!-- 1. Pin the version centrally -->
<dependencyManagement>
  <dependencies>
    <dependency>
      <groupId>com.google.guava</groupId>
      <artifactId>guava</artifactId>
      <version>33.0-jre</version>
    </dependency>
  </dependencies>
</dependencyManagement>

<!-- 2. Exclude a transitive dependency from one path -->
<dependency>
  <groupId>com.example</groupId>
  <artifactId>csv-lib</artifactId>
  <version>2.1</version>
  <exclusions>
    <exclusion>
      <groupId>com.google.guava</groupId>
      <artifactId>guava</artifactId>
    </exclusion>
  </exclusions>
</dependency>
```
3. Declare it directly (depth 1 = nearest). 4. Import a BOM that aligns versions.

---

## 7. Maven Wrapper (`mvnw`)

```bash
mvn -N wrapper:wrapper -Dmaven=3.9.6   # generate mvnw, mvnw.cmd, .mvn/wrapper/ (-N: don't recurse into modules)
./mvnw -v                               # downloads that exact Maven on first run
./mvnw verify
```
Commit `mvnw`, `mvnw.cmd` and `.mvn/`. Everyone (and CI, and Docker builds) uses the same Maven
version with no global install. On Linux: `chmod +x mvnw` (and `git update-index --chmod=+x mvnw`
if it was committed from Windows).

---

## 8. Multi-module basics (ForgeCI, Week 14; FlagForge SDK, Week 22)

ForgeCI is one repository with an **API app** and a **worker app** that share domain code, so it's
your first multi-module build. Shape (illustrative — design your own module boundaries):

```
forgeci/
├── pom.xml                <packaging>pom</packaging>  <modules>common, api, worker</modules>
├── common/pom.xml         <parent>forgeci-parent</parent>  plain jar: job model, queue contracts
├── api/pom.xml            <parent>forgeci-parent</parent>  Spring Boot app, depends on common
└── worker/pom.xml         <parent>forgeci-parent</parent>  Spring Boot app, depends on common
```

```xml
<!-- root pom.xml -->
<groupId>com.example</groupId>
<artifactId>forgeci-parent</artifactId>
<version>1.0.0-SNAPSHOT</version>
<packaging>pom</packaging>
<modules>
  <module>common</module>
  <module>api</module>
  <module>worker</module>
</modules>

<!-- api/pom.xml -->
<parent>
  <groupId>com.example</groupId>
  <artifactId>forgeci-parent</artifactId>
  <version>1.0.0-SNAPSHOT</version>
</parent>
<artifactId>forgeci-api</artifactId>
<dependencies>
  <dependency>
    <groupId>com.example</groupId>
    <artifactId>forgeci-common</artifactId>
    <version>${project.version}</version>
  </dependency>
</dependencies>
```
- **Aggregation** (`<modules>`): build all modules together; the **reactor** orders them by dependencies.
- **Inheritance** (`<parent>`): share properties, `dependencyManagement`, plugin config.
- `./mvnw -pl worker -am verify` builds `worker` and what it needs (`common`).
- Each Spring Boot module gets its own `spring-boot-maven-plugin` → two executable jars → two Docker images.
- FlowGrid and LedgerX stay **single-module**. FlagForge (Week 22) adds an SDK module that is a plain library jar (no Spring Boot plugin, minimal dependencies) and is *published* as an artifact: `./mvnw deploy` to GitHub Packages (a `<distributionManagement>` section + credentials in `~/.m2/settings.xml`, never in the POM), versioned with semantic versioning.

---

## 9. Maven vs Gradle

| | Maven | Gradle |
|---|---|---|
| Build file | `pom.xml` (declarative XML) | `build.gradle(.kts)` (Groovy/Kotlin DSL, programmable) |
| Model | Fixed lifecycle of phases | Task graph (DAG) |
| Performance | Good; limited incremental builds | Incremental builds, build cache, daemon → faster for large builds |
| Flexibility | Low — by design; custom logic needs plugins | High — also easy to create unreadable builds |
| Dependency conflicts | Nearest wins | **Highest version wins** by default |
| Scopes | `compile`/`runtime`/`test`/`provided` | `implementation`/`api`/`runtimeOnly`/`testImplementation`/`compileOnly` |
| Wrapper | `mvnw` | `gradlew` |
| Where | Most enterprise Java / Spring backends | Android (standard), many newer JVM projects |

Interview answer: "I use Maven because its conventions make every project look the same and it's
what most Spring backends use; I can read a Gradle build and I know the key differences — task
graph, incremental builds, and highest-version conflict resolution."

```kotlin
// build.gradle.kts equivalent of the kata POM
plugins { java; application }
java { toolchain { languageVersion = JavaLanguageVersion.of(21) } }
repositories { mavenCentral() }
dependencies {
    runtimeOnly("org.postgresql:postgresql:42.7.3")
    testImplementation("org.junit.jupiter:junit-jupiter:5.10.2")
    testRuntimeOnly("org.junit.platform:junit-platform-launcher")
}
application { mainClass = "com.example.katas.KataCli" }
tasks.test { useJUnitPlatform() }
```

---

## 🔨 Break it

1. Delete `maven.compiler.release` → compile a record → see which Java level is assumed / error message.
2. Name a test class `CsvImporterSpec` → `mvn test` runs 0 tests. Why? (Surefire's include patterns.)
3. Give the JDBC driver `test` scope, run the CLI jar → `No suitable driver found`.
4. Build with `maven-jar-plugin` only, `java -jar target/java-katas.jar` with a dependency used at runtime → `NoClassDefFoundError`. Fix with shade or `spring-boot-maven-plugin`.
5. Put an `*IT.java` test under surefire only → it never runs. Add failsafe → runs in `verify`.
6. Add two libraries that transitively bring different Jackson versions; read `dependency:tree -Dverbose`; fix with `dependencyManagement`.
7. Make a test fail, run `mvn package` → no jar. Run `mvn package -DskipTests` → jar (and why that's dangerous in CI).

## ⚠️ Common mistakes

- Committing `target/`.
- Not pinning plugin versions.
- Duplicating versions everywhere instead of properties/`dependencyManagement`.
- `compile` scope for test libraries (ships JUnit to production).
- `-DskipTests` as a habit.
- `mvn install` when `mvn verify` is what you meant (install pollutes `~/.m2`).
- Global `mvn` in CI instead of `./mvnw`.

## 🎤 Interview questions

<details><summary>1. What are Maven coordinates?</summary>

`groupId:artifactId:version` (plus optional packaging/classifier). They uniquely identify an artifact in a repository, e.g. `org.postgresql:postgresql:42.7.3`.
</details>

<details><summary>2. Walk me through the Maven default lifecycle.</summary>

validate → compile → test-compile → test → package → integration-test → verify → install → deploy
(with process-resources etc. in between). Invoking a phase runs all previous phases. Plugin goals bind to phases.
</details>

<details><summary>3. Explain dependency scopes.</summary>

compile (everywhere, transitive), provided (compile + test, supplied by the runtime), runtime
(not needed to compile, needed to run — JDBC drivers), test (test only), import (BOM in dependencyManagement).
</details>

<details><summary>4. How do you resolve a dependency conflict?</summary>

Inspect with `mvn dependency:tree -Dverbose`. Maven uses nearest-wins then first-declared. Fix
by pinning in `dependencyManagement`, excluding the transitive dependency, declaring it directly,
or importing a BOM. Confirm the runtime error (`NoSuchMethodError`) disappears and tests pass.
</details>

<details><summary>5. Surefire vs failsafe?</summary>

Surefire runs unit tests in the `test` phase and fails fast. Failsafe runs integration tests
(`*IT`) in `integration-test` and fails the build in `verify`, so post-integration-test cleanup
still runs. Run `mvn verify` to get both.
</details>

<details><summary>6. What is the Maven wrapper and why commit it?</summary>

Scripts (`mvnw`) plus properties that download and run a pinned Maven version. Reproducible builds
across machines, CI and Docker without installing Maven.
</details>

<details><summary>7. dependencies vs dependencyManagement?</summary>

`dependencies` adds libraries to the project. `dependencyManagement` only declares versions/scopes
to use *if* the dependency appears (directly or transitively) — used by parents and BOMs to centralize versions.
</details>

<details><summary>8. What does spring-boot-maven-plugin do?</summary>

Its `repackage` goal turns the plain jar into an executable fat jar (app classes + all dependency
jars under `BOOT-INF/lib` + a launcher) runnable with `java -jar`. Also provides `spring-boot:run`
and `build-image`.
</details>

<details><summary>9. Maven vs Gradle?</summary>

Maven: declarative XML, fixed lifecycle, strong conventions, nearest-wins resolution. Gradle:
Kotlin/Groovy DSL, task DAG, incremental builds and caching (faster), highest-version-wins, more
flexible. Both have wrappers and use Maven Central.
</details>

## ✅ Mastery checklist

- [ ] Write the kata `pom.xml` from memory (coordinates, release 21, JUnit test scope, driver runtime scope)
- [ ] Explain every lifecycle phase `mvn verify` runs
- [ ] Explain all five scopes with an example each
- [ ] Configure surefire + failsafe and run an `*IT` test
- [ ] Build an executable jar (jar plugin + shade, or Spring Boot plugin)
- [ ] Read `dependency:tree`, explain nearest-wins, fix a conflict with `dependencyManagement`
- [ ] Add and commit the Maven Wrapper; CI uses `./mvnw -B verify`
- [ ] Explain multi-module aggregation vs inheritance
- [ ] Give a 60-second Maven vs Gradle answer
