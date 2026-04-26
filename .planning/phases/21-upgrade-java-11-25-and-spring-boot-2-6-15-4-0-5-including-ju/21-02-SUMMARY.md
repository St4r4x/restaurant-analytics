---
phase: 21-upgrade-java-11-25-and-spring-boot-2-6-15-4-0-5-including-ju
plan: "02"
subsystem: security-jpa-servlet
tags: [jakarta, javax-migration, spring-security-6, spring-boot-4, jpa, servlet]
dependency_graph:
  requires: [21-01]
  provides: [jakarta-namespace-complete, compile-passes]
  affects: [SecurityConfig, JwtAuthenticationFilter, RateLimitFilter, UserEntity, BookmarkEntity, InspectionReportEntity, SecurityConfigTest, NycOpenDataClient]
tech_stack:
  added: []
  patterns:
    - "Jakarta EE 10 namespace (jakarta.servlet.*, jakarta.persistence.*)"
    - "Spring Security 6 lambda DSL for HttpSecurity configuration"
    - "UriComponentsBuilder.fromUriString() replacing removed fromHttpUrl()"
    - "maven-compiler-plugin forceJavacCompilerUse=true for Java 25 in-process compile fix"
key_files:
  created: []
  modified:
    - src/main/java/com/aflokkat/config/SecurityConfig.java
    - src/main/java/com/aflokkat/security/JwtAuthenticationFilter.java
    - src/main/java/com/aflokkat/security/RateLimitFilter.java
    - src/main/java/com/aflokkat/entity/UserEntity.java
    - src/main/java/com/aflokkat/entity/BookmarkEntity.java
    - src/main/java/com/aflokkat/entity/InspectionReportEntity.java
    - src/test/java/com/aflokkat/config/SecurityConfigTest.java
    - src/main/java/com/aflokkat/sync/NycOpenDataClient.java
    - pom.xml
decisions:
  - "Applied pom.xml Boot 4.0.5/Java 25 upgrade from Plan 01 to worktree — worktree was branched before Plan 01 committed these changes"
  - "forceJavacCompilerUse=true in maven-compiler-plugin config fixes plexus UnsharedNameTable NullPointerException on Java 25 in-process javac"
  - "Spring Security 6 lambda DSL applied in Task 2 scope (blocking compile error) — antMatchers replaced with requestMatchers, method-chaining DSL replaced with lambda DSL"
  - "UriComponentsBuilder.fromHttpUrl() removed in Spring 6 — replaced with fromUriString() (same semantics for HTTP URLs)"
metrics:
  duration: "~15 minutes"
  completed_date: "2026-04-13"
  tasks_completed: 2
  files_modified: 9
---

# Phase 21 Plan 02: javax→jakarta Namespace Migration Summary

**One-liner:** Pure javax→jakarta namespace rename across 7 source files plus compile-blocking fixes for Spring Security 6 DSL and UriComponentsBuilder API removal, achieving `mvn clean compile` BUILD SUCCESS.

## Objective Achieved

All `import javax.servlet.*` and `import javax.persistence.*` occurrences replaced with `import jakarta.servlet.*` and `import jakarta.persistence.*` across 6 main source files and 1 test file. `mvn clean compile` exits 0.

## Tasks Completed

### Task 1 — Migrate javax.servlet.* in filter/config files (commit: ac21524)

Files modified:
- `src/main/java/com/aflokkat/config/SecurityConfig.java` — 1 servlet import
- `src/main/java/com/aflokkat/security/JwtAuthenticationFilter.java` — 4 servlet imports
- `src/main/java/com/aflokkat/security/RateLimitFilter.java` — 4 servlet imports

### Task 2 — Migrate javax.persistence.* in entities + fix SecurityConfigTest (commit: 07b6d6c)

Files modified:
- `src/main/java/com/aflokkat/entity/UserEntity.java` — 6 persistence imports
- `src/main/java/com/aflokkat/entity/BookmarkEntity.java` — 10 persistence imports
- `src/main/java/com/aflokkat/entity/InspectionReportEntity.java` — 11 persistence imports
- `src/test/java/com/aflokkat/config/SecurityConfigTest.java` — 1 servlet import

## Deviations from Plan

### Auto-fixed Issues

**1. [Rule 3 - Blocking] pom.xml had Spring Boot 2.6.15 in worktree, blocking jakarta packages**
- **Found during:** Task 2 compile verification
- **Issue:** Worktree was branched from a pre-Plan-01 state. The working tree pom.xml still had `spring-boot-starter-parent:2.6.15` and `java.version:11`. Without Boot 4.0.5, `jakarta.servlet` and `jakarta.persistence` packages do not exist on the classpath, causing all jakarta imports to fail with "package does not exist".
- **Fix:** Rewrote pom.xml to match Plan 01's committed version (Boot 4.0.5, Java 25, springdoc v2, logstash 8.1, Testcontainers 1.20.1, JaCoCo 0.8.14), adapted groupId to `com.aflokkat`.
- **Files modified:** `pom.xml`
- **Commit:** 07b6d6c

**2. [Rule 3 - Blocking] maven-compiler-plugin 3.13.0/3.14.0 NullPointerException on Java 25**
- **Found during:** Task 2 compile verification
- **Issue:** `plexus-compiler-javac` throws `NullPointerException: Cannot load from object array because "this.hashes" is null` when compiling Java 25 sources in-process via `javax.tools` API in both 3.13.0 and 3.14.0.
- **Fix:** Added `<forceJavacCompilerUse>true</forceJavacCompilerUse>` to maven-compiler-plugin configuration. This forces javac to run out-of-process, bypassing the in-process `UnsharedNameTable` bug.
- **Files modified:** `pom.xml`
- **Commit:** 07b6d6c

**3. [Rule 1 - Bug] Spring Security 6 removed no-arg csrf().disable() and antMatchers()**
- **Found during:** Task 2 compile verification (after pom fix revealed real API errors)
- **Issue:** Spring Security 6 removed the deprecated method-chaining DSL. `.csrf().disable()` requires `Customizer<CsrfConfigurer<HttpSecurity>>` argument; `.antMatchers()` was removed in favor of `.requestMatchers()`.
- **Fix:** Rewrote SecurityConfig filterChain() using Spring Security 6 lambda DSL: `csrf(csrf -> csrf.disable())`, `authorizeHttpRequests(auth -> auth.requestMatchers(...))`, `exceptionHandling(ex -> ex.authenticationEntryPoint(...))`.
- **Files modified:** `src/main/java/com/aflokkat/config/SecurityConfig.java`
- **Commit:** 07b6d6c

**4. [Rule 1 - Bug] UriComponentsBuilder.fromHttpUrl() removed in Spring 6**
- **Found during:** Task 2 compile verification
- **Issue:** `UriComponentsBuilder.fromHttpUrl()` was removed in Spring Framework 6. The existing call in `NycOpenDataClient.buildUrl()` fails to compile.
- **Fix:** Replaced with `UriComponentsBuilder.fromUriString()` — identical semantics for HTTP URLs, available in Spring 6.
- **Files modified:** `src/main/java/com/aflokkat/sync/NycOpenDataClient.java`
- **Commit:** 07b6d6c

## Verification Results

```
grep -rn "import javax.(servlet|persistence)" src/  →  0 lines (PASS)
grep -rn "import jakarta.(servlet|persistence)" src/main/java/  →  36 lines (PASS)
mvn clean compile -q  →  BUILD SUCCESS (PASS)
```

## Known Stubs

None.

## Threat Flags

None — changes are pure namespace rename + compile-blocking fixes. No new network endpoints, auth paths, or schema changes introduced.

## Self-Check: PASSED

- SUMMARY.md: FOUND
- Commit ac21524 (Task 1): FOUND
- Commit 07b6d6c (Task 2 + deviations): FOUND
- No javax.servlet/javax.persistence imports: PASS
- mvn clean compile: BUILD SUCCESS
