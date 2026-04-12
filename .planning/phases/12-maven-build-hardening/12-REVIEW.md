---
phase: 12-maven-build-hardening
reviewed: 2026-04-12T00:00:00Z
depth: standard
files_reviewed: 2
files_reviewed_list:
  - pom.xml
  - src/test/java/com/st4r4x/dao/RestaurantDAOIntegrationTest.java
findings:
  critical: 0
  warning: 2
  info: 3
  total: 5
status: issues_found
---

# Phase 12: Code Review Report

**Reviewed:** 2026-04-12
**Depth:** standard
**Files Reviewed:** 2
**Status:** issues_found

## Summary

Reviewed the Maven build hardening changes in `pom.xml` and the sole integration test file. The core hardening work — `@{argLine}` late-binding for Surefire and Failsafe, JaCoCo `initialize`-phase `prepare-agent`, JaCoCo check/report exclusions, and Byte Buddy version override — is correctly implemented and the comments explaining the rationale are accurate. No critical issues were found.

Two warnings are flagged: a compiler configuration that silently weakens cross-compilation safety (using `<source>`/`<target>` instead of `<release>`), and a misleading command in the integration test's Javadoc that would route the test to the wrong runner. Three info items cover a redundant logging dependency, a production-scoped dev utility, and an outdated JWT library.

## Warnings

### WR-01: Compiler plugin uses `<source>`/`<target>` instead of `<release>`

**File:** `pom.xml:176-181`
**Issue:** `maven-compiler-plugin` is configured with `<source>25</source>` and `<target>25</target>`. This pair does **not** enforce the bootstrap classpath — the compiler can silently reference APIs that do not exist in the declared target JDK. The `<release>` flag (added in Java 9) was introduced specifically to prevent this class of bug; it locks source level, target level, and bootstrap classpath to the same JDK version simultaneously. Using `<source>`/`<target>` alone on a preview JDK like Java 25 means the compiler will accept calls to APIs unavailable on earlier JDKs without a warning.

**Fix:**
```xml
<plugin>
  <groupId>org.apache.maven.plugins</groupId>
  <artifactId>maven-compiler-plugin</artifactId>
  <version>3.14.0</version>
  <configuration>
    <release>25</release>
    <encoding>UTF-8</encoding>
  </configuration>
</plugin>
```
Remove the top-level `<maven.compiler.source>` and `<maven.compiler.target>` properties or replace them with `<maven.compiler.release>25</maven.compiler.release>` — the compiler plugin respects both.

---

### WR-02: Misleading Javadoc command routes integration test to wrong runner

**File:** `src/test/java/com/st4r4x/dao/RestaurantDAOIntegrationTest.java:23`
**Issue:** The class comment instructs: `mvn verify -Dtest=RestaurantDAOIntegrationTest`. The `-Dtest` system property is processed by **Surefire**, not Failsafe. Because the file is named `RestaurantDAOIntegrationTest` (not `*IT.java`), Surefire would normally run it — but the class-level `@Ignore` means it gets silently skipped regardless. When the class-level `@Ignore` is eventually removed (Phase 14 Testcontainers migration), a developer following this comment verbatim would pass the `-Dtest` filter to Surefire, which would try to run it without a live MongoDB and fail in an unexpected way. If the intent is to run it via Failsafe, the file should be renamed to `RestaurantDAOIntegrationIT.java`; if it stays under Surefire, the comment should use `-Dgroups` or similar and omit the Failsafe assumption.

**Fix:** Update the comment to reflect the actual runner path:
```java
/**
 * To run via Surefire with a live MongoDB:
 *   mvn test -Dtest=RestaurantDAOIntegrationTest -DskipITs
 *
 * To migrate to Failsafe (Phase 14): rename to RestaurantDAOIntegrationIT.java
 * and run with: mvn verify -Dit.test=RestaurantDAOIntegrationIT
 */
```

---

## Info

### IN-01: Redundant `spring-boot-starter-logging` dependency

**File:** `pom.xml:101-104`
**Issue:** `spring-boot-starter-logging` is declared as an explicit compile-scope dependency. It is already included transitively by `spring-boot-starter-web` (via `spring-boot-starter` → `spring-boot-starter-logging`). The explicit declaration adds noise and can cause confusion about whether the project has a custom logging override.

**Fix:** Remove the explicit declaration:
```xml
<!-- DELETE these lines — already provided transitively by spring-boot-starter-web -->
<dependency>
    <groupId>org.springframework.boot</groupId>
    <artifactId>spring-boot-starter-logging</artifactId>
</dependency>
```

---

### IN-02: `dotenv-java` ships in the production artifact

**File:** `pom.xml:57-60`
**Issue:** `dotenv-java` has no `<scope>` element, so it defaults to `compile` and is bundled into the production jar. The project's own `CLAUDE.md` states "no dotenv in production" and Docker Compose injects environment variables directly. Keeping this dependency as `compile` scope contradicts the stated architecture and adds an unnecessary 40 KB jar to the deployable artifact.

**Fix:** Either add `<scope>provided</scope>` (so it compiles but is not bundled), or — if it is truly unused in the production code path — remove it entirely. If it is only referenced in dev/local startup code, move that logic behind a profile or remove the dependency.

---

### IN-03: JJWT version is outdated (0.11.5 vs. current 0.12.x)

**File:** `pom.xml:85-98`
**Issue:** All three `jjwt-*` artifacts are pinned to `0.11.5`. The `0.12.x` line (current stable) moved to a cleaner API (`Jwts.parser().verifyWith(...)`), deprecated the `signWith(Key, SignatureAlgorithm)` overload used in `0.11.x`, and includes several dependency updates. There are no known critical CVEs in `0.11.5`, but staying on an outdated version increases future migration cost.

**Fix:** When time allows, bump all three to the same `0.12.x` version and update `JwtUtil` call sites to the new API. This is not urgent but worth scheduling before the v3.0 release.

---

_Reviewed: 2026-04-12_
_Reviewer: Claude (gsd-code-reviewer)_
_Depth: standard_
