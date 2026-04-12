# Phase 12: Maven Build Hardening - Research

**Researched:** 2026-04-12
**Domain:** Maven build tooling — JaCoCo coverage, Surefire argLine late-binding, Failsafe plugin setup
**Confidence:** HIGH

---

<user_constraints>
## User Constraints (from CONTEXT.md)

### Locked Decisions

**D-01:** Measure the actual baseline first by running `mvn test jacoco:report` with no check goal, read `target/site/jacoco/index.html` instruction coverage %, then set threshold at `max(baseline − 5%, baseline)` if baseline ≥ 60%, or `baseline − 5%` if below 60%.

**D-02:** Threshold must be documented in `pom.xml` with a comment: "Measured baseline: X% — threshold set at Y% (baseline minus 5%)". Never set an aspirational target not yet met.

**D-03:** Threshold enforced on `INSTRUCTION` counter (not line or branch) — most stable metric across refactors.

**D-04:** Exclude the following packages from measurement (pure data carriers, no meaningful logic):
- `com/st4r4x/dto/**`
- `com/st4r4x/entity/**`
- `com/st4r4x/aggregation/**`
- `com/st4r4x/domain/**` (MongoDB POJOs — getters/setters only)

**D-05:** Everything else is measured: `service/**`, `dao/**`, `controller/**`, `security/**`, `cache/**`, `sync/**`, `util/**`, `config/**`.

**D-06:** Add `maven-failsafe-plugin` with `@{argLine} -XX:+EnableDynamicAgentLoading` as argLine — same late-binding pattern as Surefire.

**D-07:** Do NOT rename or move any tests in this phase. `RestaurantDAOIntegrationTest` stays under Surefire until Phase 14 migrates it to Testcontainers.

**D-08:** Failsafe bound to `integration-test` and `verify` lifecycle phases — no tests will run under it yet since there are no `*IT.java` files.

**D-09:** Surefire argLine MUST use `@{argLine}` (with `@`, not `$`) to pick up the JaCoCo agent string set by `prepare-agent` goal. Current pom.xml has literal `-XX:+EnableDynamicAgentLoading` — this MUST become `@{argLine} -XX:+EnableDynamicAgentLoading`.

**D-10:** JaCoCo `prepare-agent` goal must run in `initialize` phase (before `test-compile`) so `${argLine}` property is available when Surefire reads it.

### Claude's Discretion

- JaCoCo plugin version: use latest stable compatible with Spring Boot 3.4.4 BOM (or explicit recent version if not managed).
- Whether to add a separate `jacoco` Maven profile or wire goals directly into the default lifecycle — Claude decides based on simplicity.
- HTML report destination: default `target/site/jacoco/index.html` as specified in roadmap success criteria.

### Deferred Ideas (OUT OF SCOPE)

- Moving RestaurantDAOIntegrationTest to Failsafe → Phase 14 (Testcontainers)
- Publishing JaCoCo report as PR comment → Phase 15 (CI-08)
- Branch coverage threshold → deferred until coverage improves in Phase 19
</user_constraints>

---

<phase_requirements>
## Phase Requirements

| ID | Description | Research Support |
|----|-------------|------------------|
| TEST-07 | User can see JaCoCo code coverage report generated after `mvn test` | JaCoCo 0.8.13 `prepare-agent` + `report` goals wired to default lifecycle; HTML output at `target/site/jacoco/index.html` |
| TEST-08 | User can see the build fail when instruction coverage drops below a defined threshold (baseline measured first) | JaCoCo `check` goal with `INSTRUCTION/COVEREDRATIO` rule; `haltOnFailure=true` (default); baseline-first workflow documented in D-01 |
</phase_requirements>

---

## Summary

This phase wires JaCoCo coverage instrumentation, HTML report generation, and a threshold-enforcement gate into the project's Maven build by modifying only `pom.xml`. The critical correctness concern is the `@{argLine}` late-binding pattern for `maven-surefire-plugin`: the current pom uses a literal string (`-XX:+EnableDynamicAgentLoading`), which silently overwrites the JaCoCo Java agent string that `prepare-agent` deposits into the `argLine` property — causing Mockito's ByteBuddy instrumentation to crash with `StackOverflowError` on all controller tests. Changing to `@{argLine} -XX:+EnableDynamicAgentLoading` is the single most important correctness fix.

Spring Boot 3.4.4's BOM does NOT manage `jacoco-maven-plugin` — an explicit version must be declared. JaCoCo 0.8.13 (released 2025-04-02) provides official support for Java 24 and experimental support for Java 25. JaCoCo 0.8.12 (released 2024-03-31) has no Java 25 support. Given the project runs on Java 25.0.2, **0.8.13 is the minimum required version**. The Spring Boot 3.4.4 BOM manages `maven-failsafe-plugin` at 3.5.2 and `maven-surefire-plugin` at 3.5.2 — these versions do not need to be declared explicitly; only the Failsafe execution block needs to be added.

The coverage baseline is unknown at research time — it must be measured as the first implementation action. There are 28 test files (not 27 as stated in the original roadmap; the count increased during the Phase 11 work). `RestaurantDAOIntegrationTest` requires a live MongoDB on `localhost:27017` and will fail in a cold build; this is an existing known issue, deferred to Phase 14. For threshold measurement purposes, either a live MongoDB must be available, or `RestaurantDAOIntegrationTest` must be annotated `@Ignore` temporarily.

**Primary recommendation:** Wire JaCoCo 0.8.13 goals directly into the default lifecycle (no profile needed). Fix the Surefire argLine first, then add `prepare-agent` → `report` → `check` in a single plugin block. Add Failsafe with the same argLine pattern but no executions. Measure baseline before writing the `<minimum>` value.

---

## Standard Stack

### Core

| Library | Version | Purpose | Why Standard |
|---------|---------|---------|--------------|
| jacoco-maven-plugin | 0.8.13 | Bytecode instrumentation for coverage + HTML/XML report + threshold check | Only JaCoCo version with official Java 24 support and experimental Java 25; Spring Boot BOM does not manage this plugin |
| maven-surefire-plugin | 3.5.2 (BOM) | Runs unit tests; carries the `@{argLine}` flag | Already in BOM via Spring Boot 3.4.4 parent; no explicit version needed |
| maven-failsafe-plugin | 3.5.2 (BOM) | Runs integration tests (`*IT.java`); same argLine pattern | Managed by Spring Boot 3.4.4 BOM; only lifecycle binding needs to be added |

[VERIFIED: Maven Central — latest jacoco-maven-plugin is 0.8.13 (2025-04-02), retrieved 2026-04-12]
[VERIFIED: Spring Boot 3.4.4 BOM at `~/.m2/repository/org/springframework/boot/spring-boot-dependencies/3.4.4/spring-boot-dependencies-3.4.4.pom` — confirms `maven-surefire-plugin.version=3.5.2` and `maven-failsafe-plugin.version=3.5.2`]
[VERIFIED: JaCoCo changelog — 0.8.13 "officially supports Java 23 and Java 24" with "experimental Java 25 class file support"; 0.8.14 "officially supports Java 25"]

> **Note on JaCoCo 0.8.14:** 0.8.14 was released 2025-10-11 and offers full official Java 25 support. Given the project runs Java 25.0.2, 0.8.14 is preferable if available on Maven Central. Maven Central confirmed both 0.8.12 and 0.8.13 exist. Check `https://repo1.maven.org/maven2/org/jacoco/jacoco-maven-plugin/` for 0.8.14 availability at implementation time — prefer it over 0.8.13 if present.

### Supporting

| Library | Version | Purpose | When to Use |
|---------|---------|---------|-------------|
| junit-vintage-engine | (BOM) | Allows JUnit 4 tests to run on JUnit 5 platform | Already in pom.xml — no change needed; ensures Surefire 3.5.2 picks up JUnit 4 tests via the vintage provider |

### Alternatives Considered

| Instead of | Could Use | Tradeoff |
|------------|-----------|----------|
| Default lifecycle wiring | Separate `jacoco` Maven profile | Profile approach requires `-Pjacoco` on every `mvn test` invocation; D-10 requires report and check on plain `mvn test` — profile approach violates the success criteria |
| INSTRUCTION counter (D-03) | LINE or BRANCH counter | LINE fluctuates with blank lines/formatting; BRANCH requires both branches covered; INSTRUCTION is the most stable counter for threshold enforcement across minor refactors |

**Installation:**

No new `<dependencies>` needed. JaCoCo is a build-time plugin, not a runtime dependency. Only `<build><plugins>` additions are required.

---

## Architecture Patterns

### Recommended pom.xml Plugin Structure

```
<build>
  <plugins>
    <!-- (existing) spring-boot-maven-plugin -->
    <!-- (existing) maven-compiler-plugin -->
    <!-- (modified)  maven-surefire-plugin  — @{argLine} fix -->
    <!-- (new)       jacoco-maven-plugin    — 3 goals -->
    <!-- (new)       maven-failsafe-plugin  — lifecycle binding only -->
  </plugins>
</build>
```

No profiles, no new modules, no Maven wrapper changes.

### Pattern 1: JaCoCo Three-Goal Wiring (prepare-agent → report → check)

**What:** Three JaCoCo plugin goals chained in a single `<plugin>` block, each bound to a specific lifecycle phase.

**When to use:** Whenever coverage instrumentation, reporting, and threshold enforcement must all fire on plain `mvn test`.

**Example:**
```xml
<!-- Source: https://www.jacoco.org/jacoco/trunk/doc/prepare-agent-mojo.html
              https://www.jacoco.org/jacoco/trunk/doc/report-mojo.html
              https://www.jacoco.org/jacoco/trunk/doc/check-mojo.html -->
<plugin>
  <groupId>org.jacoco</groupId>
  <artifactId>jacoco-maven-plugin</artifactId>
  <version>0.8.13</version>
  <executions>

    <!-- Goal 1: deposit -javaagent:... into the 'argLine' property -->
    <execution>
      <id>jacoco-prepare-agent</id>
      <phase>initialize</phase>
      <goals><goal>prepare-agent</goal></goals>
    </execution>

    <!-- Goal 2: generate HTML/XML report after tests complete -->
    <execution>
      <id>jacoco-report</id>
      <phase>test</phase>
      <goals><goal>report</goal></goals>
      <configuration>
        <excludes>
          <exclude>com/st4r4x/dto/**</exclude>
          <exclude>com/st4r4x/entity/**</exclude>
          <exclude>com/st4r4x/aggregation/**</exclude>
          <exclude>com/st4r4x/domain/**</exclude>
        </excludes>
      </configuration>
    </execution>

    <!-- Goal 3: enforce minimum coverage threshold (baseline-driven) -->
    <execution>
      <id>jacoco-check</id>
      <phase>test</phase>
      <goals><goal>check</goal></goals>
      <configuration>
        <excludes>
          <exclude>com/st4r4x/dto/**</exclude>
          <exclude>com/st4r4x/entity/**</exclude>
          <exclude>com/st4r4x/aggregation/**</exclude>
          <exclude>com/st4r4x/domain/**</exclude>
        </excludes>
        <rules>
          <rule>
            <element>BUNDLE</element>
            <limits>
              <limit>
                <!-- Measured baseline: X% — threshold set at Y% (baseline minus 5%) -->
                <counter>INSTRUCTION</counter>
                <value>COVEREDRATIO</value>
                <minimum>0.YY</minimum>  <!-- fill in after measuring baseline -->
              </limit>
            </limits>
          </rule>
        </rules>
      </configuration>
    </execution>

  </executions>
</plugin>
```

[VERIFIED: https://www.jacoco.org/jacoco/trunk/doc/check-mojo.html — element types, counter names, value names, minimum as decimal ratio 0.0–1.0]
[VERIFIED: https://www.jacoco.org/jacoco/trunk/doc/prepare-agent-mojo.html — default propertyName is `argLine`]

### Pattern 2: Surefire argLine Late-Binding Fix

**What:** Replace literal `-XX:+EnableDynamicAgentLoading` with `@{argLine} -XX:+EnableDynamicAgentLoading` in the Surefire configuration.

**Why critical:** JaCoCo's `prepare-agent` goal sets the `argLine` Maven property to `-javaagent:/path/to/jacocoagent.jar=destfile=...` during the `initialize` phase. If Surefire's argLine is a literal string at POM model initialization time (using `${argLine}`), Maven substitutes it before `prepare-agent` runs, getting an empty or stale value. The `@{...}` syntax is a Surefire-specific late-binding mechanism that reads the property at plugin execution time — after `prepare-agent` has set it.

**Without the fix:** JaCoCo agent is never attached; no coverage data is written; the JVM warning `WARNING: Dynamic loading of agents will be disallowed` may appear or, worse, the JaCoCo agent overwrites the `argLine` property completely, dropping the `-XX:+EnableDynamicAgentLoading` flag, causing Mockito ByteBuddy instrumentation to fail with `StackOverflowError`.

**Example:**
```xml
<!-- Source: https://maven.apache.org/surefire/maven-surefire-plugin/test-mojo.html -->
<!-- "Using an alternate syntax @{...} allows late replacement of properties when
     the plugin is executed, so properties modified by other plugins are picked up correctly." -->
<plugin>
  <groupId>org.apache.maven.plugins</groupId>
  <artifactId>maven-surefire-plugin</artifactId>
  <!-- No version: Spring Boot 3.4.4 BOM manages at 3.5.2 -->
  <configuration>
    <argLine>@{argLine} -XX:+EnableDynamicAgentLoading</argLine>
  </configuration>
</plugin>
```

[VERIFIED: https://maven.apache.org/surefire/maven-surefire-plugin/test-mojo.html — `@{...}` late-binding explicitly documented]
[VERIFIED: https://www.jacoco.org/jacoco/trunk/doc/prepare-agent-mojo.html — default propertyName = `argLine`]

### Pattern 3: Failsafe Plugin Lifecycle Binding (No Active Tests)

**What:** Add Failsafe with the correct argLine pattern and lifecycle bindings but no `<includes>` patterns — zero tests will run today, infrastructure is ready for Phase 14.

**When to use:** Phase 14 will create `*IT.java` files; Failsafe must already be configured with the correct argLine to avoid the same JaCoCo-drops-agent bug when integration tests are added.

**Example:**
```xml
<!-- Source: https://maven.apache.org/surefire/maven-failsafe-plugin/ -->
<plugin>
  <groupId>org.apache.maven.plugins</groupId>
  <artifactId>maven-failsafe-plugin</artifactId>
  <!-- No version: Spring Boot 3.4.4 BOM manages at 3.5.2 -->
  <executions>
    <execution>
      <goals>
        <goal>integration-test</goal>
        <goal>verify</goal>
      </goals>
    </execution>
  </executions>
  <configuration>
    <argLine>@{argLine} -XX:+EnableDynamicAgentLoading</argLine>
  </configuration>
</plugin>
```

[ASSUMED — standard Failsafe configuration pattern, consistent with Surefire pattern documented above]

### Pattern 4: Baseline Measurement Workflow (MANDATORY first step)

**What:** Run `mvn test` with JaCoCo wired but WITHOUT the `check` goal to measure the actual instruction coverage % before setting a threshold. The `check` goal block must be commented out or the execution removed during this measurement step.

**Procedure:**
1. Add `prepare-agent` and `report` goals to pom.xml (no `check` goal yet)
2. Run `mvn test` — this both runs tests and generates `target/site/jacoco/index.html`
3. Open `target/site/jacoco/index.html`, read the "Instructions" covered % on the summary row
4. Compute threshold: `floor(baseline_pct - 5)`, expressed as a decimal ratio (e.g., 42% → 0.37)
5. Add the `check` execution with the computed `<minimum>`
6. Add required comment: `<!-- Measured baseline: X% — threshold set at Y% (baseline minus 5%) -->`

**Alternative:** Run `mvn test jacoco:report` if the `report` goal is not yet bound to the default lifecycle.

[VERIFIED: D-01, D-02 from CONTEXT.md — required workflow]

### Anti-Patterns to Avoid

- **`${argLine}` instead of `@{argLine}`:** Using dollar-sign syntax in Surefire's argLine causes early property evaluation before `prepare-agent` runs. JaCoCo agent is never attached. Coverage file is empty. Report shows 0% everywhere.
- **Setting aspirational thresholds:** Never set `<minimum>0.80</minimum>` if the baseline is 45%. The build will fail immediately and every commit will be red until coverage reaches 80%.
- **`prepare-agent` bound to `process-test-classes` or later:** The `initialize` phase must be used so the `argLine` property is set before the test compiler or Surefire reads it.
- **Duplicate argLine JVM flags:** `@{argLine} -XX:+EnableDynamicAgentLoading -XX:+EnableDynamicAgentLoading` — doubled flags are harmless but messy. Confirm the JaCoCo agent string itself does not include `-XX:+EnableDynamicAgentLoading` (it does not; it only adds `-javaagent:`).
- **Exclusion format with dots instead of slashes:** JaCoCo exclusion patterns use filesystem path separators (`com/st4r4x/dto/**`), not package notation (`com.st4r4x.dto.*`). Using dots will silently fail to exclude the classes.

---

## Don't Hand-Roll

| Problem | Don't Build | Use Instead | Why |
|---------|-------------|-------------|-----|
| Coverage instrumentation | Custom Java agent, ASM bytecode rewriter | `jacoco:prepare-agent` | JaCoCo handles class file versioning, exclusion filtering, forked JVM coordination, multi-threaded test runs |
| Coverage threshold gate | Shell script parsing `jacoco.xml` | `jacoco:check` goal | `check` integrates into the Maven lifecycle, produces structured failure messages pointing to the exact rule violated |
| Coverage report HTML | Template engine generating HTML from exec file | `jacoco:report` goal | JaCoCo report engine handles drill-down, source highlighting, branch coloring |

**Key insight:** JaCoCo's exec binary format is not a public API. Parsing it externally is fragile and unnecessary — all three operations (instrument, report, check) must go through the official plugin.

---

## Common Pitfalls

### Pitfall 1: The argLine Overwrite Trap

**What goes wrong:** Surefire receives `-javaagent:jacoco.jar=...` and ignores `-XX:+EnableDynamicAgentLoading` — OR Surefire receives only `-XX:+EnableDynamicAgentLoading` with no JaCoCo agent at all. Tests run but JaCoCo records no data, report shows 0%, check goal passes trivially at 0% if threshold is left at default, OR check fails with `Instructions covered ratio is 0.00, but expected minimum is 0.42`.

**Why it happens:** Maven's `${argLine}` is evaluated at POM model parse time. At that moment, `prepare-agent` has not yet run, so `${argLine}` evaluates to either empty string or whatever value was previously in the property. When `prepare-agent` later sets `argLine`, Surefire has already captured the old value and will use it.

**How to avoid:** Always use `@{argLine}` (with `@`, not `$`) in both Surefire and Failsafe argLine configurations.

**Warning signs:** JaCoCo generates `target/jacoco.exec` but it has size 0 bytes; or `target/site/jacoco/index.html` shows "Instructions Covered: n/a" or 0%; or tests throw `StackOverflowError` in Mockito-heavy test classes.

[VERIFIED: https://maven.apache.org/surefire/maven-surefire-plugin/test-mojo.html — late-binding `@{...}` explicitly documented and required when other plugins modify `argLine`]

### Pitfall 2: RestaurantDAOIntegrationTest Fails Without Live MongoDB

**What goes wrong:** `mvn test` exits non-zero because `RestaurantDAOIntegrationTest` cannot connect to `localhost:27017`. JaCoCo generates a partial exec file. Coverage report may undercount (some classes never executed). Check goal may pass or fail depending on threshold.

**Why it happens:** `RestaurantDAOIntegrationTest` uses `new RestaurantDAOImpl()` which reads from `application.properties` and connects to MongoDB. No `@Ignore` annotation is present on the class.

**How to avoid:** For baseline measurement, either: (a) have a live MongoDB running on localhost:27017 with the `newyork` database, or (b) add `@Ignore` to `RestaurantDAOIntegrationTest` temporarily and document that the baseline was measured without it. Option (b) is safer for CI. The `@Ignore` annotation should be preserved until Phase 14 Testcontainers migration.

**Warning signs:** `Connection refused: localhost/127.0.0.1:27017` in build output.

[VERIFIED: Codebase grep — RestaurantDAOIntegrationTest has no `@Ignore` annotation; comment in file says "add @Ignore to skip in normal build"]

### Pitfall 3: Wrong Exclusion Path Format

**What goes wrong:** Classes in `com.st4r4x.dto` continue to appear in the coverage report and drag down the metric, even though they are supposed to be excluded.

**Why it happens:** JaCoCo `<excludes>` patterns match against class file paths (with `/` separators), not Java package names (with `.` separators). Using `com.st4r4x.dto.*` will not match `com/st4r4x/dto/AuthRequest.class`.

**How to avoid:** Always use forward-slash path notation in JaCoCo excludes: `com/st4r4x/dto/**` (not `com.st4r4x.dto.*`). The `**` glob matches all classes in the package and its sub-packages.

**Warning signs:** The HTML report still shows `dto` package entries with coverage metrics.

[VERIFIED: https://www.jacoco.org/jacoco/trunk/doc/check-mojo.html and report-mojo.html — exclusion patterns use filesystem path format]

### Pitfall 4: Java 25 + JaCoCo Version Mismatch

**What goes wrong:** JaCoCo emits warnings about unsupported class file version, or silently fails to instrument certain classes, resulting in artificially low coverage numbers.

**Why it happens:** JaCoCo 0.8.12 and earlier do not know how to parse Java 25 class files. When the agent encounters a class it cannot instrument, it may skip it or throw an error depending on the `junkclasses` setting.

**How to avoid:** Use JaCoCo 0.8.13 (experimental Java 25 support) or 0.8.14 (official Java 25 support). The project uses Java 25.0.2, making 0.8.12 or earlier unacceptable.

**Warning signs:** Log lines containing `Can not instrument class ... due to java.lang.IllegalArgumentException: Unsupported class file major version`.

[VERIFIED: JaCoCo changelog — 0.8.13 adds experimental Java 25 support; 0.8.14 adds official Java 25 support; retrieved 2026-04-12]

### Pitfall 5: `check` Goal Bound to `verify` Phase (Not Triggered by `mvn test`)

**What goes wrong:** `mvn test` generates the report but never enforces the threshold. The build appears green even when coverage drops. Only `mvn verify` (or `mvn package`) would fail.

**Why it happens:** Some JaCoCo tutorials bind `check` to `verify`, which runs after the `test` phase. A plain `mvn test` stops before `verify`.

**How to avoid:** Bind the `check` goal to the `test` phase (same as `report`) so that both fire on plain `mvn test`. This satisfies the success criterion "Running `mvn test` with coverage below threshold exits with non-zero code."

[VERIFIED: Maven lifecycle — `verify` is after `test`; binding `check` to `test` ensures it runs on plain `mvn test`]

---

## Code Examples

### Complete jacoco-maven-plugin Block (Template)

```xml
<!-- Source: https://www.jacoco.org/jacoco/trunk/doc/maven.html -->
<plugin>
  <groupId>org.jacoco</groupId>
  <artifactId>jacoco-maven-plugin</artifactId>
  <version>0.8.13</version>  <!-- explicit: not managed by Spring Boot 3.4.4 BOM -->
  <executions>

    <!-- 1. Instrument: deposits -javaagent:... into ${argLine} at initialize phase -->
    <execution>
      <id>jacoco-prepare-agent</id>
      <phase>initialize</phase>
      <goals><goal>prepare-agent</goal></goals>
    </execution>

    <!-- 2. Report: generates target/site/jacoco/index.html after tests -->
    <execution>
      <id>jacoco-report</id>
      <phase>test</phase>
      <goals><goal>report</goal></goals>
      <configuration>
        <excludes>
          <exclude>com/st4r4x/dto/**</exclude>
          <exclude>com/st4r4x/entity/**</exclude>
          <exclude>com/st4r4x/aggregation/**</exclude>
          <exclude>com/st4r4x/domain/**</exclude>
        </excludes>
      </configuration>
    </execution>

    <!-- 3. Check: fail build if INSTRUCTION coverage falls below threshold -->
    <execution>
      <id>jacoco-check</id>
      <phase>test</phase>
      <goals><goal>check</goal></goals>
      <configuration>
        <excludes>
          <exclude>com/st4r4x/dto/**</exclude>
          <exclude>com/st4r4x/entity/**</exclude>
          <exclude>com/st4r4x/aggregation/**</exclude>
          <exclude>com/st4r4x/domain/**</exclude>
        </excludes>
        <rules>
          <rule>
            <element>BUNDLE</element>
            <limits>
              <limit>
                <!-- Measured baseline: X% — threshold set at Y% (baseline minus 5%) -->
                <counter>INSTRUCTION</counter>
                <value>COVEREDRATIO</value>
                <minimum>0.YY</minimum>
              </limit>
            </limits>
          </rule>
        </rules>
      </configuration>
    </execution>

  </executions>
</plugin>
```

### Surefire Plugin Fix (argLine Late-Binding)

```xml
<!-- Source: https://maven.apache.org/surefire/maven-surefire-plugin/test-mojo.html -->
<!-- @{argLine} — Surefire late-binding: reads argLine property at execution time,
     after jacoco:prepare-agent has set it in the initialize phase.
     ${argLine} would be evaluated at POM parse time (before prepare-agent runs). -->
<plugin>
  <groupId>org.apache.maven.plugins</groupId>
  <artifactId>maven-surefire-plugin</artifactId>
  <!-- No <version>: Spring Boot 3.4.4 BOM manages at 3.5.2 -->
  <configuration>
    <argLine>@{argLine} -XX:+EnableDynamicAgentLoading</argLine>
  </configuration>
</plugin>
```

### Failsafe Plugin (Lifecycle Binding Only)

```xml
<!-- Source: https://maven.apache.org/surefire/maven-failsafe-plugin/ -->
<!-- No *IT.java files exist yet — Failsafe will find zero tests to run.
     argLine uses same @{argLine} late-binding pattern for Phase 14 readiness.
     D-06: -XX:+EnableDynamicAgentLoading required for Mockito ByteBuddy on Java 25. -->
<plugin>
  <groupId>org.apache.maven.plugins</groupId>
  <artifactId>maven-failsafe-plugin</artifactId>
  <!-- No <version>: Spring Boot 3.4.4 BOM manages at 3.5.2 -->
  <executions>
    <execution>
      <goals>
        <goal>integration-test</goal>
        <goal>verify</goal>
      </goals>
    </execution>
  </executions>
  <configuration>
    <argLine>@{argLine} -XX:+EnableDynamicAgentLoading</argLine>
  </configuration>
</plugin>
```

### Threshold Calculation Logic

```
# After running mvn test (with prepare-agent + report, without check):
# Read from target/site/jacoco/index.html — "Total" row, "Instructions" column

baseline_pct = <number from report, e.g. 42>
threshold_pct = max(baseline_pct - 5, 0)
minimum_ratio = threshold_pct / 100.0   # e.g. 0.37

# In pom.xml:
# <!-- Measured baseline: 42% — threshold set at 37% (baseline minus 5%) -->
# <minimum>0.37</minimum>
```

---

## Runtime State Inventory

> This phase modifies only `pom.xml`. No rename, migration, or runtime state is involved.

| Category | Items Found | Action Required |
|----------|-------------|------------------|
| Stored data | None | None |
| Live service config | None | None |
| OS-registered state | None | None |
| Secrets/env vars | None | None |
| Build artifacts | `target/` — will gain `jacoco.exec` and `target/site/jacoco/` after first post-fix `mvn test` | No migration; build artifacts are ephemeral |

**Nothing found in category:** All categories are empty — verified by phase scope (pom.xml-only change).

---

## Environment Availability

| Dependency | Required By | Available | Version | Fallback |
|------------|------------|-----------|---------|----------|
| Java 25 | jacoco-maven-plugin 0.8.13 experimental support | ✓ | 25.0.2 | — |
| Maven 3.8.7 | All plugin goals | ✓ | 3.8.7 | — |
| MongoDB (localhost:27017) | RestaurantDAOIntegrationTest | ✗ (cold machine) | — | Add `@Ignore` to RestaurantDAOIntegrationTest before measuring baseline |
| Internet / Maven Central | Downloading jacoco-maven-plugin 0.8.13 | ✓ | — | Use offline repo if blocked |

**Missing dependencies with no fallback:**
- None blocking the pom.xml changes themselves.

**Missing dependencies with fallback:**
- MongoDB: required to get a complete baseline. Fallback: `@Ignore` RestaurantDAOIntegrationTest for baseline measurement run, document that baseline excludes integration test coverage. Restore or leave `@Ignore` based on Phase 14 decisions (D-07).

---

## Validation Architecture

Nyquist validation is enabled (`workflow.nyquist_validation: true`).

### Test Framework

| Property | Value |
|----------|-------|
| Framework | JUnit 4 (via junit-vintage-engine) + Mockito 5.17.0 |
| Config file | None — Surefire 3.5.2 auto-detects via JUnit 5 platform |
| Quick run command | `mvn test -Dsurefire.failIfNoSpecifiedTests=false` |
| Full suite command | `mvn test` |

### Phase Requirements → Test Map

| Req ID | Behavior | Test Type | Automated Command | File Exists? |
|--------|----------|-----------|-------------------|-------------|
| TEST-07 | JaCoCo HTML report generated at `target/site/jacoco/index.html` after `mvn test` | Smoke (file existence check) | `mvn test && test -f target/site/jacoco/index.html && echo PASS` | ❌ Wave 0 — verify step, not a Java test file |
| TEST-07 | Report contains line/branch metrics (not empty) | Smoke (file content check) | `grep -q "el class=\"ctr2\"" target/site/jacoco/index.html && echo REPORT_HAS_DATA` | ❌ Wave 0 |
| TEST-08 | Build fails when coverage below threshold | Functional (threshold enforcement) | `mvn test` must exit 0 at baseline; set threshold above baseline, `mvn test` must exit non-zero | Manual / shell script |
| TEST-08 | Failure message is JaCoCo threshold message, not StackOverflowError | Functional (error message check) | `mvn test 2>&1 \| grep -i "coverage check failed\|jacoco"` | Manual |
| (all) | All 28 existing test files pass — no regression | Regression | `mvn test` — examine Surefire report `target/surefire-reports/` | ✅ existing tests |

### Sampling Rate

- **Per task commit:** `mvn test -pl . -am` (standard build — confirm all tests still pass)
- **Per wave merge:** `mvn test` + verify `target/site/jacoco/index.html` exists
- **Phase gate:** All 28 tests green + JaCoCo report present + threshold check passes before `/gsd-verify-work`

### Wave 0 Gaps

- [ ] No new test Java files required — this phase is `pom.xml`-only
- [ ] Verification shell commands (TEST-07, TEST-08 smoke checks above) should be run manually by the implementor — they are not automatable as JUnit tests
- [ ] `target/site/jacoco/index.html` will only exist after `mvn test` completes with the new config — cannot be pre-verified

*(No framework install needed — existing JUnit 4 + vintage engine setup is sufficient)*

---

## State of the Art

| Old Approach | Current Approach | When Changed | Impact |
|--------------|------------------|--------------|--------|
| `${argLine}` in Surefire | `@{argLine}` late-binding | Surefire 2.19+ supported it; required since JaCoCo adopted it ~2016 | Without this fix, JaCoCo agent never attaches when Surefire argLine is configured |
| `jacoco-maven-plugin` bound to `verify` phase for check | Bound to `test` phase | JaCoCo best practice — depends on success criteria | Ensures `mvn test` (not just `mvn verify`) enforces the threshold |
| JaCoCo 0.8.11 (last Spring Boot 2.x era version) | 0.8.13 or 0.8.14 | 0.8.13: 2025-04-02, 0.8.14: 2025-10-11 | Minimum required for Java 25 class file support |

**Deprecated/outdated:**
- `${argLine}` in Surefire argLine with JaCoCo: still works if JaCoCo is the ONLY thing setting argLine, but the `@{argLine}` pattern is the documented approach and handles composition correctly.
- `maven-failsafe-plugin` version 2.x: Spring Boot BOM now pins 3.5.2; using 2.x would require explicit version override.

---

## Assumptions Log

| # | Claim | Section | Risk if Wrong |
|---|-------|---------|---------------|
| A1 | Failsafe plugin with no `*IT.java` files runs zero tests and exits 0 | Architecture Patterns (Failsafe block) | If Failsafe somehow counts existing `*Test.java` files as integration tests, those tests would run twice and coverage numbers would be affected. Mitigation: Failsafe's default `<includes>` pattern is `**/*IT.java, **/*ITCase.java, **/IT*.java` — no overlap with `*Test.java`. LOW risk. |
| A2 | Spring Boot 3.4.4 BOM does not manage `jacoco-maven-plugin` | Standard Stack | LOW risk — verified by direct inspection of BOM POM at `~/.m2/repository/.../3.4.4/spring-boot-dependencies-3.4.4.pom`; confirmed no JaCoCo entry. |
| A3 | JaCoCo 0.8.14 is available on Maven Central (released 2025-10-11) | Standard Stack note | MEDIUM risk — Maven Central confirmed 0.8.12 and 0.8.13 but 0.8.14 availability was not directly verified via `curl`. Implementor should check at implementation time. Fallback: 0.8.13 is sufficient (experimental Java 25 support). |

**All other claims in this document were verified against official sources or local repository inspection.**

---

## Open Questions

1. **Actual coverage baseline**
   - What we know: Codebase has 55 main Java files, 28 test files; exclusions remove `dto/`, `entity/`, `aggregation/`, `domain/`
   - What's unclear: The actual instruction coverage % for the non-excluded packages
   - Recommendation: Measure as the first implementation action (run `mvn test` after adding `prepare-agent` + `report` without `check`); do not estimate or guess

2. **RestaurantDAOIntegrationTest @Ignore status during baseline measurement**
   - What we know: The class currently has no `@Ignore`; it requires a live MongoDB; it will fail in a cold build
   - What's unclear: Whether to add `@Ignore` temporarily for baseline measurement, or to require a live MongoDB
   - Recommendation: Add `@Ignore` for the baseline measurement step, then re-evaluate in the Phase 14 planning. Document in the threshold comment that the baseline was measured with this test ignored.

3. **JaCoCo 0.8.13 vs 0.8.14 for Java 25**
   - What we know: 0.8.13 has experimental Java 25 support; 0.8.14 has official Java 25 support
   - What's unclear: Whether 0.8.14 is available on Maven Central as of implementation time (released 2025-10-11; implementation is 2026-04-12 — likely available)
   - Recommendation: Use 0.8.14 if available, 0.8.13 otherwise. Check with `curl -I https://repo1.maven.org/maven2/org/jacoco/jacoco-maven-plugin/0.8.14/` at implementation time.

---

## Security Domain

> This phase modifies only `pom.xml` build tooling. No application logic, endpoints, authentication, or data handling is changed. ASVS categories V2–V6 do not apply. Security domain is explicitly not applicable to this phase.

---

## Project Constraints (from CLAUDE.md)

Directives from `./CLAUDE.md` that affect this phase:

| Constraint | Impact on Phase 12 |
|------------|-------------------|
| Build tool: Maven (`mvn`) | All commands use `mvn`; no Gradle alternatives |
| Java 11 stated, but `pom.xml` shows Java 25 | Pom.xml is the authority; research confirmed Java 25.0.2 installed |
| Testing: JUnit 4 + Mockito | JaCoCo must not break vintage engine setup; argLine fix is critical for this |
| Spring Boot 2.6.15 stated, but `pom.xml` shows 3.4.4 | Pom.xml is the authority; BOM versions (Surefire 3.5.2, Failsafe 3.5.2) confirmed from the 3.4.4 BOM |
| `RestaurantDAOIntegrationTest` requires live MongoDB | Cold build will fail without @Ignore; must be addressed before baseline measurement |
| `anyRequest().permitAll()` pattern | No impact on coverage tooling |
| Mockito mock(Authentication.class) fails on Java 25 | Already resolved by using concrete `UsernamePasswordAuthenticationToken`; no new issue for this phase |

---

## Sources

### Primary (HIGH confidence)
- `~/.m2/repository/org/springframework/boot/spring-boot-dependencies/3.4.4/spring-boot-dependencies-3.4.4.pom` — confirmed Surefire 3.5.2, Failsafe 3.5.2; confirmed NO JaCoCo entry
- Maven Central API `https://search.maven.org/solrsearch/` — confirmed jacoco-maven-plugin latest is 0.8.13 (retrieved 2026-04-12); 0.8.12 and 0.8.13 exist on repo1.maven.org
- `https://www.jacoco.org/jacoco/trunk/doc/changes.html` — confirmed 0.8.13 official Java 24 + experimental Java 25; 0.8.14 official Java 25
- `https://www.jacoco.org/jacoco/trunk/doc/check-mojo.html` — confirmed rules/limits/counter/value/minimum structure
- `https://www.jacoco.org/jacoco/trunk/doc/prepare-agent-mojo.html` — confirmed default propertyName = `argLine`
- `https://maven.apache.org/surefire/maven-surefire-plugin/test-mojo.html` — confirmed `@{argLine}` late-binding explicitly documented
- Local codebase inspection — confirmed 28 test files; confirmed Surefire has no version tag (BOM-managed); confirmed RestaurantDAOIntegrationTest has no @Ignore

### Secondary (MEDIUM confidence)
- `https://www.jacoco.org/jacoco/trunk/doc/report-mojo.html` — exclusion pattern format (wildcard `*` and `?`)
- Java 25 + JaCoCo 0.8.13 compatibility — cross-referenced changelog + Maven Central date

### Tertiary (LOW confidence)
- Failsafe default `<includes>` pattern `**/*IT.java` not running `*Test.java` — [ASSUMED] standard Failsafe behavior, not directly verified in this session

---

## Metadata

**Confidence breakdown:**
- Standard stack: HIGH — JaCoCo version verified on Maven Central; BOM versions verified from local repo
- Architecture (argLine fix): HIGH — Surefire official docs explicitly document `@{argLine}` late-binding
- JaCoCo goal configuration: HIGH — official JaCoCo mojo docs verified
- Pitfalls: HIGH — each pitfall is grounded in verified mechanism (Maven lifecycle, property evaluation timing, path format)
- Coverage baseline: LOW — unknown until measurement; cannot be determined at research time

**Research date:** 2026-04-12
**Valid until:** 2026-07-12 (stable tooling; 90-day estimate — JaCoCo and Surefire versions move slowly)
