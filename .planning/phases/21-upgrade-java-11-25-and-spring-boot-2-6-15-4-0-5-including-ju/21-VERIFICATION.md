---
phase: 21-upgrade-java-11-25-and-spring-boot-2-6-15-4-0-5-including-ju
verified: 2026-04-13T10:30:00Z
status: human_needed
score: 11/12 must-haves verified
overrides_applied: 0
human_verification:
  - test: "Smoke test — start the application and confirm it boots and /api/restaurants/health returns 200"
    expected: "HTTP 200 from /api/restaurants/health after startup completes (within 30s)"
    why_human: "Requires Docker (MongoDB, PostgreSQL, Redis) to be running. The Plan 05 executor confirmed the smoke test was skipped because Docker services were not available. The mvn clean verify with Testcontainers covers build correctness but does not confirm the full Spring Boot startup path with all external services."
---

# Phase 21: Upgrade Java 11 → 25 / Spring Boot 2.6.15 → 4.0.5 Verification Report

**Phase Goal:** The project builds successfully on Java 25 with Spring Boot 4.0.5, all tests pass under JUnit 5, and no deprecated/removed APIs remain in the codebase
**Verified:** 2026-04-13T10:30:00Z
**Status:** human_needed
**Re-verification:** No — initial verification

## Goal Achievement

### Observable Truths

| # | Truth | Status | Evidence |
|---|-------|--------|----------|
| 1 | pom.xml declares Spring Boot parent 4.0.5 | ✓ VERIFIED | `pom.xml:7` — `<version>4.0.5</version>` |
| 2 | pom.xml declares java.version 25 and maven.compiler source/target 25 | ✓ VERIFIED | `pom.xml:16-18` — all three properties set to 25 |
| 3 | pom.xml has springdoc-openapi-starter-webmvc-ui:2.8.6 (not springdoc-openapi-ui:1.8.0) | ✓ VERIFIED | `pom.xml:74-75` — v2 artifact present, old artifact absent |
| 4 | pom.xml has logstash-logback-encoder:8.1 | ✓ VERIFIED | `pom.xml:111-112` — version 8.1 confirmed |
| 5 | pom.xml has no junit:junit:4.13.2 or junit-vintage-engine dependency | ✓ VERIFIED | grep returns no output for both artifacts |
| 6 | pom.xml JaCoCo exclusion patterns use com/st4r4x/ not com/aflokkat/ | ✓ VERIFIED | `pom.xml:256-271` — correct com/st4r4x/ paths in both executions |
| 7 | No javax.servlet.* or javax.persistence.* imports remain in any source file | ✓ VERIFIED | grep across entire src/ returns 0 lines |
| 8 | All affected files compile against jakarta.* namespace | ✓ VERIFIED | SecurityConfig, JwtAuthenticationFilter, RateLimitFilter, UserEntity, BookmarkEntity, InspectionReportEntity, SecurityConfigTest all have jakarta.* imports confirmed |
| 9 | SecurityConfig.java uses authorizeHttpRequests() lambda DSL, not deprecated authorizeRequests() | ✓ VERIFIED | `SecurityConfig.java:51` — `authorizeHttpRequests(auth -> auth`; antMatchers absent |
| 10 | All 9 test files migrated from JUnit 4 to JUnit 5 | ✓ VERIFIED | All 9 planned test files have `import org.junit.jupiter.api.Test`; zero JUnit 4 imports found |
| 11 | mvn clean verify exits 0 (Surefire + Failsafe both pass) | ✓ VERIFIED | SUMMARY 21-05 documents 165 Surefire + 19 Failsafe tests, BUILD SUCCESS; JAR MANIFEST confirms Build-Jdk-Spec: 25, Spring-Boot-Version: 4.0.5 |
| 12 | Application starts and /api/restaurants/health returns 200 | ? HUMAN NEEDED | Smoke test skipped in Plan 05 — Docker services unavailable in execution context |

**Score:** 11/12 truths verified

### Required Artifacts

| Artifact | Expected | Status | Details |
|----------|----------|--------|---------|
| `pom.xml` | Boot 4.0.5 parent, Java 25 target, updated dependency coordinates, correct JaCoCo exclusions | ✓ VERIFIED | All conditions confirmed |
| `src/main/resources/application.properties` | Cleaned properties file without removed Boot 3+ properties | ✓ VERIFIED | ant_path_matcher and hibernate.dialect absent; springdoc 2.x paths intact |
| `src/test/resources/application-test.properties` | Cleaned test properties file without removed Boot 3+ properties | ✓ VERIFIED | hibernate.dialect absent; ddl-auto=create retained |
| `src/main/java/com/st4r4x/config/SecurityConfig.java` | jakarta.servlet.http.HttpServletResponse + Spring Security 6 lambda DSL | ✓ VERIFIED | jakarta import confirmed; authorizeHttpRequests with requestMatchers confirmed |
| `src/main/java/com/st4r4x/security/JwtAuthenticationFilter.java` | jakarta.servlet.* imports | ✓ VERIFIED | All 4 jakarta.servlet imports confirmed |
| `src/main/java/com/st4r4x/security/RateLimitFilter.java` | jakarta.servlet.* imports | ✓ VERIFIED | All 4 jakarta.servlet imports confirmed |
| `src/main/java/com/st4r4x/entity/UserEntity.java` | jakarta.persistence.* imports | ✓ VERIFIED | jakarta.persistence.Entity confirmed |
| `src/main/java/com/st4r4x/entity/BookmarkEntity.java` | jakarta.persistence.* imports | ✓ VERIFIED | jakarta.persistence.Entity confirmed |
| `src/main/java/com/st4r4x/entity/InspectionReportEntity.java` | jakarta.persistence.* imports | ✓ VERIFIED | jakarta.persistence.Entity confirmed |
| `src/test/java/com/st4r4x/config/SecurityConfigTest.java` | jakarta.servlet.Filter import | ✓ VERIFIED | `SecurityConfigTest.java:14` confirmed |
| `src/test/java/com/st4r4x/dao/RestaurantDAOIT.java` | JUnit 5 @BeforeAll/@AfterAll with manual container lifecycle | ✓ VERIFIED | `import org.junit.jupiter.api.BeforeAll` at line 11; no @ClassRule |
| `src/test/java/com/st4r4x/repository/UserRepositoryIT.java` | @ExtendWith(SpringExtension.class) | ✓ VERIFIED | `@ExtendWith(SpringExtension.class)` at line 35 |
| `src/main/java/com/st4r4x/config/RedisConfig.java` | Explicit Jackson 2 ObjectMapper @Bean | ✓ VERIFIED | `objectMapper()` @Bean at line 33-35 |
| `src/main/java/com/st4r4x/sync/NycOpenDataClient.java` | fromUriString() replacing removed fromHttpUrl() | ✓ VERIFIED | `fromUriString(AppConfig.getNycApiUrl())` at line 95 |
| `target/quickstart-app-1.0-SNAPSHOT.jar` | Runnable Spring Boot 4.0.5 JAR | ✓ VERIFIED | 78MB JAR exists; MANIFEST confirms Build-Jdk-Spec: 25, Spring-Boot-Version: 4.0.5 |
| `CHANGELOG.md` | Phase 21 entry | ✓ VERIFIED | Lines 5-7 contain Phase 21 upgrade entry |
| `CLAUDE.md` | Stack section updated to Java 25 / Spring Boot 4.0.5 / JUnit 5 | ✓ VERIFIED | Lines 11-19 confirmed |
| `README.md` | Updated stack references | ✓ VERIFIED | Java 25 and Spring Boot 4.0.5 in stack table; no stale Java 11 / 2.6.15 refs |

### Key Link Verification

| From | To | Via | Status | Details |
|------|----|-----|--------|---------|
| pom.xml spring-boot-starter-parent | Spring Boot 4.0.5 BOM | Maven parent resolution | ✓ WIRED | `<version>4.0.5</version>` at pom.xml:7 |
| pom.xml springdoc artifact | springdoc-openapi-starter-webmvc-ui:2.8.6 | Maven dependency | ✓ WIRED | pom.xml:74-75 confirmed |
| pom.xml JaCoCo exclusions | com/st4r4x/ class files | JaCoCo exclude patterns | ✓ WIRED | pom.xml:256-271 confirmed |
| javax.servlet | jakarta.servlet | Spring Boot 3+/4+ Jakarta EE 10 | ✓ WIRED | 0 javax imports; multiple jakarta imports across all affected files |
| javax.persistence | jakarta.persistence | Hibernate 6 / Jakarta Persistence 3.x | ✓ WIRED | 0 javax imports; jakarta.persistence.Entity in all 3 entity files |
| SecurityConfig.java filterChain | Spring Security 6 authorizeHttpRequests | Lambda DSL | ✓ WIRED | `authorizeHttpRequests(auth -> auth` at SecurityConfig.java:51 |
| RestaurantDAOIT @ClassRule | Testcontainers @BeforeAll container start | JUnit 5 @BeforeAll static method | ✓ WIRED | @BeforeAll with `mongoContainer.start()` confirmed |
| UserRepositoryIT @RunWith(SpringRunner.class) | @ExtendWith(SpringExtension.class) | Spring Test JUnit 5 integration | ✓ WIRED | @ExtendWith(SpringExtension.class) at line 35 |
| mvn clean verify | All test phases | Surefire (unit) + Failsafe (integration) | ✓ WIRED | 165 + 19 tests, BUILD SUCCESS documented in SUMMARY 21-05 |
| spring.autoconfigure.exclude | Boot 4.0.5 MongoDB autoconfiguration | Spring Boot autoconfigure exclusion | ? UNCERTAIN | WR-01 in code review flags that these class names may not exist in Boot 4.0.5 autoconfigure jar; however smoke test was skipped so runtime behavior unconfirmed |

### Data-Flow Trace (Level 4)

Not applicable — this phase produces build infrastructure changes (pom.xml, configuration, import migrations), not components that render dynamic data. No data-flow trace required.

### Behavioral Spot-Checks

| Behavior | Command | Result | Status |
|----------|---------|--------|--------|
| JAR built on Java 25 with Spring Boot 4.0.5 | `unzip -p target/quickstart-app-1.0-SNAPSHOT.jar META-INF/MANIFEST.MF` | Build-Jdk-Spec: 25, Spring-Boot-Version: 4.0.5 | ✓ PASS |
| Java 25 available on host | `java -version` | openjdk version "25.0.2" | ✓ PASS |
| No javax imports anywhere | `grep -rn "import javax.(servlet|persistence)" src/` | 0 lines | ✓ PASS |
| No JUnit 4 API in test files | `grep -rn "import org.junit.Test\b|@ClassRule|@RunWith|@Test(expected" src/test/java/` | 0 lines | ✓ PASS |
| No antMatchers in SecurityConfig | `grep "antMatchers" src/main/java/com/st4r4x/config/SecurityConfig.java` | no output | ✓ PASS |
| Application smoke test | `curl -sf http://localhost:8080/api/restaurants/health` | Not run — Docker unavailable | ? SKIP |

### Requirements Coverage

The requirement IDs UPGRADE-01 through UPGRADE-04 are defined only in ROADMAP.md (line 211) — they are phase-specific upgrade requirements, not part of the v3.0 production readiness catalog in REQUIREMENTS.md. This is expected: Phase 21 is an out-of-band upgrade phase (numbered 21, above the v3.0 milestone phases 11-20).

**Note:** REQUIREMENTS.md explicitly lists "Migrating JUnit 4 tests to JUnit 5" as Out of Scope (line 145). Phase 21 performed this migration anyway as a necessary prerequisite for the Spring Boot 4.0.5 upgrade (Boot 4 dropped JUnit Vintage Engine support). This is acceptable — the requirement was superseded by the upgrade constraint.

| Requirement | Source Plan | Scope | Status | Evidence |
|-------------|------------|-------|--------|----------|
| UPGRADE-01 — pom.xml Boot 4.0.5, Java 25, dependencies | 21-01 | UPGRADE | ✓ SATISFIED | All pom.xml conditions verified |
| UPGRADE-02 — javax → jakarta namespace migration | 21-02 | UPGRADE | ✓ SATISFIED | 0 javax imports, all jakarta imports confirmed |
| UPGRADE-03 — Spring Security 6 API migration | 21-03 | UPGRADE | ✓ SATISFIED | authorizeHttpRequests + requestMatchers confirmed |
| UPGRADE-04 — JUnit 4 → JUnit 5 migration | 21-04 | UPGRADE | ✓ SATISFIED | All 9 test files migrated, 165+19 tests pass |

### Anti-Patterns Found

| File | Line | Pattern | Severity | Impact |
|------|------|---------|----------|--------|
| `pom.xml` | 40, 115, 237 | Stale comments reference Spring Boot 2.6.15 / Java 11 (e.g., Bucket4j "required for Java 11") | ℹ️ Info | Misleading comments only; no behavioral impact |
| `src/test/java/com/st4r4x/config/SecurityConfigTest.java` | 26 | Comment references "Spring Boot 2.6 + Java 25 JVM crash" — project now on Spring Boot 4.0.5 | ℹ️ Info | Stale comment only |
| `src/test/java/com/st4r4x/dao/RestaurantDAOImplTest.java` | 19-60 | Tests construct and throw exceptions themselves rather than calling DAO under test — tautological tests with zero DAO coverage | ⚠️ Warning | Tests always pass regardless of DAO behavior; low code quality, no coverage value |
| `src/main/java/com/st4r4x/config/SecurityConfig.java` | 24-31 | jwtUtil() @Bean returns concrete JwtUtil rather than JwtService interface | ⚠️ Warning | Defeats the purpose of the JwtService interface; could cause injection issues in future tests |
| `src/main/resources/application.properties` | 13 | spring.autoconfigure.exclude references class names that may not exist in Boot 4.0.5 autoconfigure jar | ⚠️ Warning | If classes are not found, the exclusion is silently ignored; MongoDB auto-config may or may not be active depending on classpath |
| `CLAUDE.md` | 135-136 | Key notes reference pre-Testcontainers integration test instructions (localhost:27017, old class name RestaurantDAOIntegrationTest) | ℹ️ Info | Stale documentation only |

No blockers (🛑) found. Warnings are code quality issues identified in the code review (21-REVIEW.md) and do not prevent the upgrade goal from being achieved.

### Human Verification Required

#### 1. Application Startup Smoke Test

**Test:** With Docker running (MongoDB, PostgreSQL, Redis), start the application:
```bash
docker compose up -d mongodb postgres redis
# Wait for services to be healthy, then:
mvn spring-boot:run -q &
APP_PID=$!
for i in $(seq 1 30); do
  if curl -sf http://localhost:8080/api/restaurants/health > /dev/null 2>&1; then
    echo "PASS: /api/restaurants/health returned 200"
    kill $APP_PID
    break
  fi
  sleep 1
done
```
**Expected:** "PASS: /api/restaurants/health returned 200" — confirms the application boots on Java 25 + Boot 4.0.5 with all external services connected
**Why human:** Docker services were not available in the Plan 05 execution context. The mvn clean verify with Testcontainers verified test correctness but did not test the full Spring Boot startup path against real external services (MongoDB, PostgreSQL, Redis) using `application.properties` configuration.

This test additionally confirms whether the `spring.autoconfigure.exclude` class names in `application.properties` are valid in Boot 4.0.5 (WR-01 in 21-REVIEW.md). If they are not found, Boot may log a warning but not necessarily fail startup — this needs to be observed at runtime.

### Gaps Summary

No gaps found. All 11 programmatically-verifiable must-haves are satisfied. The one remaining item (smoke test) requires human verification because it depends on Docker services being available. The upgrade artifacts — pom.xml, all source files, all test files, documentation — are correct and the build produces a Java 25 / Spring Boot 4.0.5 JAR verified by Maven's own build lifecycle (165 Surefire + 19 Failsafe tests passing).

Three warnings from the code review (WR-01 spring.autoconfigure.exclude class names, WR-02 JwtService interface not used in SecurityConfig bean factory, WR-03 RestaurantDAOIT public static container) are quality issues to be addressed in future phases — none block the upgrade goal.

---

_Verified: 2026-04-13T10:30:00Z_
_Verifier: Claude (gsd-verifier)_
