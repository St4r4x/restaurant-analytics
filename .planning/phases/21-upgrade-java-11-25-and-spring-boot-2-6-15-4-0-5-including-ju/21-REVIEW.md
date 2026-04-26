---
phase: 21-upgrade-java-11-25-and-spring-boot-2-6-15-4-0-5-including-ju
reviewed: 2026-04-13T00:00:00Z
depth: standard
files_reviewed: 24
files_reviewed_list:
  - src/main/java/com/st4r4x/config/RedisConfig.java
  - src/main/java/com/st4r4x/config/SecurityConfig.java
  - src/main/java/com/st4r4x/entity/BookmarkEntity.java
  - src/main/java/com/st4r4x/entity/InspectionReportEntity.java
  - src/main/java/com/st4r4x/entity/UserEntity.java
  - src/main/java/com/st4r4x/security/JwtAuthenticationFilter.java
  - src/main/java/com/st4r4x/security/RateLimitFilter.java
  - src/main/java/com/st4r4x/sync/NycOpenDataClient.java
  - src/main/resources/application.properties
  - src/test/java/com/st4r4x/aggregation/AggregationPojoTest.java
  - src/test/java/com/st4r4x/config/AppConfigTest.java
  - src/test/java/com/st4r4x/config/MongoClientFactoryTest.java
  - src/test/java/com/st4r4x/config/SecurityConfigTest.java
  - src/test/java/com/st4r4x/controller/RestaurantControllerSampleTest.java
  - src/test/java/com/st4r4x/controller/UserControllerMeTest.java
  - src/test/java/com/st4r4x/dao/RestaurantDAOImplTest.java
  - src/test/java/com/st4r4x/dao/RestaurantDAOIT.java
  - src/test/java/com/st4r4x/domain/RestaurantTest.java
  - src/test/java/com/st4r4x/repository/UserRepositoryIT.java
  - src/test/java/com/st4r4x/util/ValidationUtilTest.java
  - src/test/resources/application-test.properties
  - pom.xml
  - CHANGELOG.md
  - CLAUDE.md
  - README.md
findings:
  critical: 0
  warning: 3
  info: 4
  total: 7
status: issues_found
---

# Phase 21: Code Review Report

**Reviewed:** 2026-04-13
**Depth:** standard
**Files Reviewed:** 24
**Status:** issues_found

## Summary

This phase successfully completes the Java 11 → 25 and Spring Boot 2.6.15 → 4.0.5 upgrade,
including the JUnit 4 → 5 migration, the javax → jakarta namespace migration, and the Spring
Security 6 lambda DSL migration.

The javax scan confirms zero remaining `javax.servlet.*` or `javax.persistence.*` imports
across all reviewed files. All test files use JUnit 5 annotations and assertions exclusively;
no `@RunWith`, `@ClassRule`, or `junit.framework` imports remain.

Spring Security 6 lambda DSL is correct in `SecurityConfig` and the filter chain structure
is well-formed. The Jackson 2 `ObjectMapper` bean in `RedisConfig` is intentionally declared
to coexist with the Spring Boot 4 Jackson 3 default.

Three issues require attention before this phase can be closed: one stale `spring.autoconfigure.exclude`
property referencing a moved class name, one type inconsistency between the `SecurityConfig` bean
declaration and the `JwtAuthenticationFilter` constructor, and one stale comment in `pom.xml`
that will mislead future maintainers. Four informational items are also noted.

## Warnings

### WR-01: `spring.autoconfigure.exclude` references moved class names

**File:** `src/main/resources/application.properties:13`

**Issue:** The exclude list names three classes under `org.springframework.boot.autoconfigure.mongo.*`.
Inspection of the deployed Spring Boot 4.0.5 autoconfigure jar confirms that this package
no longer exists — MongoDB auto-configuration has been relocated to dedicated modules
(`spring-boot-data-mongodb`). If Spring Boot 4 still attempts to resolve these class names
during startup, it will log a warning or throw an `IllegalStateException` depending on
`spring.autoconfigure.fail-on-error`. More practically, the intended effect (preventing
Spring Data MongoDB from auto-wiring) may silently fail if the classes are not found,
leaving the application relying on the fact that no MongoDB Spring Data starter is on the
classpath rather than on the explicit exclusion.

The correct Boot 4 approach is to remove the `spring.autoconfigure.exclude` property entirely
if no Spring Data MongoDB starter is present on the classpath — the exclusion is already
redundant. If a Boot 4 MongoDB auto-configuration class name is required, confirm the correct
FQCN against the `spring-boot-data-mongodb-4.0.5.jar` manifest.

**Fix:**
```properties
# Remove or update — these class names do not exist in Spring Boot 4.0.5 autoconfigure jar.
# If no spring-boot-starter-data-mongodb is on the classpath, this property is not needed.
# spring.autoconfigure.exclude=...   <-- remove this line
```

---

### WR-02: `SecurityConfig` declares `JwtUtil` bean but `JwtAuthenticationFilter` accepts `JwtService`

**File:** `src/main/java/com/st4r4x/config/SecurityConfig.java:24-31`

**Issue:** `SecurityConfig.jwtUtil()` returns a `JwtUtil` instance (concrete class), and
`SecurityConfig.jwtAuthenticationFilter(JwtUtil jwtUtil)` accepts `JwtUtil` by concrete type.
However, `JwtAuthenticationFilter` declares its field as `JwtService` (the interface):

```java
// JwtAuthenticationFilter.java:21
private final JwtService jwtUtil;
public JwtAuthenticationFilter(JwtService jwtUtil) { ... }
```

Spring will wire these correctly because `JwtUtil implements JwtService`, but the `SecurityConfig`
bean factory method parameter type `JwtUtil` will force Spring to inject the concrete bean
by class type rather than by the `JwtService` interface contract. This defeats the purpose of
introducing the `JwtService` interface (which was added specifically to allow test mocking
without Byte Buddy instrumentation). If a test later tries to replace `JwtUtil` with a
stub `JwtService` bean, Spring will still prefer the `JwtUtil`-typed factory parameter,
potentially causing a `NoUniqueBeanDefinitionException` or injecting the wrong bean.

**Fix:**
```java
// SecurityConfig.java
@Bean
public JwtService jwtUtil() {          // return type: interface, not concrete class
    return new JwtUtil();
}

@Bean
public JwtAuthenticationFilter jwtAuthenticationFilter(JwtService jwtUtil) {  // param: interface
    return new JwtAuthenticationFilter(jwtUtil);
}
```

---

### WR-03: `RestaurantDAOIT` does not use `@AfterAll`-managed Testcontainers lifecycle annotation

**File:** `src/test/java/com/st4r4x/dao/RestaurantDAOIT.java:29`

**Issue:** The `MongoDBContainer` field is declared `public static` without `@Container`
or any automatic lifecycle management. The container is started manually in `@BeforeAll` via
`mongoContainer.start()`. This pattern is intentional and works, but `mongoContainer` is
not declared `private` — leaving it `public static` exposes the live container reference
to other test classes in the same classloader, which could cause interference if the
Failsafe runner loads multiple IT test classes in the same JVM fork. The `UserRepositoryIT`
has the same pattern for a different container.

More critically: if `@BeforeAll` throws an exception before `mongoContainer.start()` is
reached, the container will never be started, but no `@AfterAll` cleanup will run for it
either (since `@AfterAll` only fires when the class was initialized). With `mongoContainer`
left in a non-started state, the JVM may leak the Docker container process handle. Wrapping
the start in a try-finally or using the `@Container` annotation with Testcontainers JUnit 5
extension (which manages the lifecycle automatically) would be safer.

**Fix:**
```java
// Add @Testcontainers at class level and @Container on the field:
@Testcontainers
public class RestaurantDAOIT {

    @Container
    public static MongoDBContainer mongoContainer =
        new MongoDBContainer("mongo:7.0");

    // Remove mongoContainer.start() from @BeforeAll — TC extension manages it.
    // The MongoClientFactory reset and System.setProperty injection still belong in @BeforeAll,
    // but @BeforeAll fires after @Container startup so the URI is already available.
}
```

Note: if the `@Testcontainers` extension conflicts with the current `@BeforeAll`/`@AfterAll`
ordering semantics relied on for `MongoClientFactory.closeInstance()`, the existing manual
pattern is acceptable to keep, but the field should be made `private static` to prevent
cross-class interference.

---

## Info

### IN-01: Stale comments in `pom.xml` reference Spring Boot 2.6.15 and Java 11

**File:** `pom.xml:40` and `pom.xml:115` and `pom.xml:237`

**Issue:** Three comments still reference the pre-upgrade versions:
- Line 40: `<!-- MongoDB Driver (included by Spring Boot 2.6.x) -->`
- Line 115: `<!-- Rate limiting — Bucket4j 7.x required for Java 11; do NOT use 8.x (requires Java 17) -->`
- Line 237: `Version must be explicit — Spring Boot 2.6.15 BOM does not manage jacoco-maven-plugin`

The Bucket4j comment is particularly misleading now that the project runs Java 25. The
constraint is still valid (Bucket4j 7.x does run on Java 25, while 8.x has different
requirements), but stating "required for Java 11" is wrong — the reason to keep 7.x is
API stability, not the Java version floor.

**Fix:** Update the three comments to reflect the current versions.

---

### IN-02: `CLAUDE.md` key notes section contains stale information

**File:** `CLAUDE.md:135-136`

**Issue:** Two notes reference pre-upgrade state:
- "Integration tests require live MongoDB on `localhost:27017` with `newyork` DB populated" — this
  was true before Phase 14 added Testcontainers; the integration tests are now self-contained.
- The `mvn test -Dtest=RestaurantDAOIntegrationTest` example still references the old class name
  (`RestaurantDAOIntegrationTest` was renamed to `RestaurantDAOIT` in Phase 14).

**Fix:**
```markdown
# In CLAUDE.md Key Notes section, replace:
- Integration tests require live MongoDB on `localhost:27017` with `newyork` DB populated
# With:
- Integration tests use Testcontainers (mongo:7.0, postgres:15-alpine) — no live database required.
  Run with: mvn failsafe:integration-test

# Also update the mvn test example:
mvn failsafe:integration-test -Dit.test=RestaurantDAOIT
```

---

### IN-03: `RestaurantDAOImplTest` contains tests that only test themselves

**File:** `src/test/java/com/st4r4x/dao/RestaurantDAOImplTest.java:19-60`

**Issue:** All five tests in this file throw and catch an `IllegalArgumentException` that they
construct themselves — the DAO implementation code is never called. For example:

```java
try {
    throw new IllegalArgumentException("limit doit être positif, reçu: -1");
} catch (IllegalArgumentException e) {
    assertTrue(e.getMessage().contains("limit"), ...);
}
```

These tests are tautologies: they always pass regardless of what `RestaurantDAOImpl` does, and
the empty `testIndicesCreationDoesNotThrowWhenIndexExists` test has no assertions at all.
The tests provide zero coverage of the actual DAO code, but they do consume test execution
time and add noise to coverage reports.

**Fix:** Either delete these tests entirely (the real validation is covered by `RestaurantDAOIT`)
or replace them with genuine unit tests that call the DAO under test with a mock `MongoCollection`.

---

### IN-04: `SecurityConfigTest` comment references "Spring Boot 2.6 + Java 25 JVM crash"

**File:** `src/test/java/com/st4r4x/config/SecurityConfigTest.java:26`

**Issue:** The comment on line 26 reads: "Avoids @WebMvcTest to work around a Spring Boot 2.6
+ Java 25 JVM crash caused by Mockito's dynamic byte-buddy-agent attachment". The project is
now on Spring Boot 4.0.5, so this comment should reference the current version to avoid
confusion about why the standalone setup pattern was chosen.

**Fix:**
```java
// Avoids @WebMvcTest to work around a Java 25 + Mockito byte-buddy-agent attachment
// issue in the @WebMvcTest test execution lifecycle. The AnnotationConfigWebApplicationContext
// standalone approach is compatible with Spring Boot 4.0.5 and Java 25.
```

---

_Reviewed: 2026-04-13_
_Reviewer: Claude (gsd-code-reviewer)_
_Depth: standard_
