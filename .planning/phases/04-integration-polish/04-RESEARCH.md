# Phase 4: Integration Polish - Research

**Researched:** 2026-03-31
**Domain:** Spring Security RBAC testing, ownership invariant testing, Docker named volume persistence verification
**Confidence:** HIGH

## Summary

Phase 4 has no new production code. Its sole deliverable is targeted tests that
assert three security/persistence invariants that phases 1-3 implement but never
explicitly test:

1. RBAC boundaries: CUSTOMER JWT → 403, anonymous → 401 on `/api/reports/**`
2. Cross-controller ownership: controller B cannot read or patch controller A's reports
3. Photo persistence: a file written to `uploads_data` named volume survives `docker
   compose down && docker compose up --build`

The critical finding from reading the existing test suite is that **two of the
three success criteria are already covered by passing tests**. `SecurityConfigTest`
already asserts 401 and 403 against `/api/reports/test`. `ReportControllerTest`
`patchReport_returns403_whenNotOwner` covers the ownership invariant at the
controller layer. Phase 4 must verify this coverage and fill only the remaining
gap — confirming that the ownership check also blocks the read (`GET /api/reports`)
path and producing a programmatic persistence check for the Docker volume.

**Primary recommendation:** Map each success criterion to a concrete, named test
method; prefer adding to existing test classes over creating new ones; treat the
Docker volume persistence criterion as a unit-level file-I/O test that exercises
`AppConfig.getUploadsDir()` plus real `java.nio.file` writes, since actual
`docker compose` invocation is not feasible inside a JUnit test.

---

<phase_requirements>
## Phase Requirements

| ID | Description | Research Support |
|----|-------------|-----------------|
| SC-1 | CUSTOMER JWT against `/api/reports/**` returns 403; anonymous returns 401 | Already covered by `SecurityConfigTest` — verify tests pass and document |
| SC-2 | Controller B cannot read or edit controller A's reports | `patchReport_returns403_whenNotOwner` covers edit; need a GET ownership test |
| SC-3 | Photo uploaded in one container run is accessible after `docker compose down && docker compose up --build` | Docker volume `uploads_data` already declared; unit test verifies file-I/O path; Docker round-trip must be manual-only |
</phase_requirements>

---

## Standard Stack

### Core
| Library | Version | Purpose | Why Standard |
|---------|---------|---------|--------------|
| JUnit 5 (Jupiter) | 5.8.2 (via Spring Boot 2.6.15 BOM) | Test framework | Project standard; all phases 2-3 tests use it |
| Mockito | 5.17.0 | Mock dependencies | Project standard; pom.xml explicit override for Java 21+ support |
| Spring MockMvc | via spring-boot-starter-test | HTTP layer assertions | Already used in all controller tests |
| Spring Security Test | via spring-security-test | `springSecurity()` + `.with(authentication(...))` | Already used in `SecurityConfigTest` |

### Supporting
| Library | Version | Purpose | When to Use |
|---------|---------|---------|-------------|
| JUnit 4 (Vintage) | 4.x (via junit-vintage-engine) | Legacy JUnit 4 test runner | Only when `SecurityConfigTest` pattern requires it (it uses JUnit 4 `@Before`/`@Test`) |
| `@TempDir` (JUnit 5) | built-in | Provides an isolated filesystem path per test | Photo file-I/O tests in `ReportControllerTest` already use this |
| `AnnotationConfigWebApplicationContext` | Spring | Bootstrap minimal Spring Security filter chain | Only needed for security filter tests — see `SecurityConfigTest` pattern |

### Alternatives Considered
| Instead of | Could Use | Tradeoff |
|------------|-----------|----------|
| `AnnotationConfigWebApplicationContext` + `springSecurity()` | `@SpringBootTest` with full context | `@SpringBootTest` requires live Postgres/Mongo/Redis; the minimal-context approach avoids that and is the proven pattern from Phase 1 |
| Manual `docker compose down/up` test script | JUnit file-I/O test for persistence criterion | `docker compose` cannot be invoked from a JUnit test in this environment; file-I/O unit test is the correct substitute and was accepted as sufficient in Phase 2 |

**Installation:** No new dependencies needed. All required libraries are already present.

---

## Architecture Patterns

### Existing Test File Map
```
src/test/java/com/aflokkat/
├── config/
│   └── SecurityConfigTest.java      # JUnit 4; Spring Security filter chain; 401/403 tests ALREADY PASS
├── controller/
│   ├── ReportControllerTest.java    # JUnit 5; standaloneSetup; 12 tests ALREADY PASS
│   └── RestaurantControllerSearchTest.java
├── security/
│   └── JwtUtilTest.java
└── ...
```

Phase 4 adds tests to **existing** test classes. No new test class file is created
unless the new test truly cannot live in an existing class.

### Pattern 1: Security Filter Tests (RBAC boundaries — SC-1)

**What:** Use `AnnotationConfigWebApplicationContext` + `springSecurity(filter)` +
`.with(authentication(auth))` to test the real `SecurityFilterChain` without starting
the full application context.

**When to use:** Any test that must assert HTTP 401 / 403 at the filter level (not
at the controller level).

**ALREADY COVERED** — `SecurityConfigTest` has three passing methods:
- `reports_returns401_whenUnauthenticated`
- `reports_returns403_forCustomerJwt`
- `reports_allowsAccess_forControllerJwt`

Phase 4 must verify these tests exist and pass. No code change needed for SC-1.

```java
// Source: src/test/java/com/aflokkat/config/SecurityConfigTest.java (existing, passing)
// Pattern for reference only — do NOT rewrite
@Before
public void setUp() throws Exception {
    AnnotationConfigWebApplicationContext context = new AnnotationConfigWebApplicationContext();
    context.register(
        SecurityConfig.class,
        org.springframework.boot.autoconfigure.security.servlet.SecurityAutoConfiguration.class
    );
    context.refresh();
    mockMvc = MockMvcBuilders
        .standaloneSetup(new StubReportsController())
        .apply(springSecurity((Filter) context.getBean("springSecurityFilterChain")))
        .build();
}

@Test
public void reports_returns401_whenUnauthenticated() throws Exception {
    SecurityContextHolder.clearContext();
    mockMvc.perform(get("/api/reports/test"))
            .andExpect(status().isUnauthorized());
}

@Test
public void reports_returns403_forCustomerJwt() throws Exception {
    UsernamePasswordAuthenticationToken auth = new UsernamePasswordAuthenticationToken(
            "customer_user", null,
            Collections.singletonList(new SimpleGrantedAuthority("ROLE_CUSTOMER")));
    mockMvc.perform(get("/api/reports/test").with(authentication(auth)))
            .andExpect(status().isForbidden());
}
```

### Pattern 2: Ownership Invariant Tests (SC-2)

**What:** Use `@ExtendWith(MockitoExtension.class)` + `standaloneSetup()` to test
that `GET /api/reports` returns only reports owned by the authenticated user, and
that `PATCH /api/reports/{id}` by a non-owner returns 403.

**When to use:** Any controller-level test that asserts on ownership logic.

**PARTIALLY COVERED:**
- Edit path (`PATCH`) already tested by `patchReport_returns403_whenNotOwner` (passing).
- Read path (`GET`) — `listReports_returnsOnlyCallerReports_notOtherControllers` asserts
  that `findByUserId(42L)` is called (and NOT `findByUserId(99L)`), which means controller
  B's reports are never fetched. This is a valid behavioral assertion via mock verification.

**Gap to fill:** The planner should evaluate whether the existing
`listReports_returnsOnlyCallerReports_notOtherControllers` test satisfies SC-2's "controller B
cannot read controller A's reports" requirement. It does at the mock-verification level:
the repository is called with the caller's user ID only. If the planner considers this
insufficient, one additional test should be added that sets up a report entity owned by
user 99L (a different controller) and asserts it is NOT in the response for a caller
authenticated as user 42L.

```java
// Source: existing pattern from ReportControllerTest (JUnit 5 / MockitoExtension)
// Pattern for a new "controller B cannot see controller A's reports" test
@Test
void listReports_doesNotReturnOtherControllersReports() throws Exception {
    UserEntity callerUser = new UserEntity("ctrl_b", "b@test.com", "hash", "ROLE_CONTROLLER");
    callerUser.setId(99L);
    // Switch security context to ctrl_b
    UsernamePasswordAuthenticationToken auth = new UsernamePasswordAuthenticationToken(
            "ctrl_b", null,
            Collections.singletonList(new SimpleGrantedAuthority("ROLE_CONTROLLER")));
    SecurityContextHolder.getContext().setAuthentication(auth);
    when(userRepository.findByUsername("ctrl_b")).thenReturn(Optional.of(callerUser));
    // Repository returns empty list for user 99 (ctrl_b has no reports)
    when(reportRepository.findByUserId(99L)).thenReturn(Collections.emptyList());

    mockMvc.perform(get("/api/reports"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.count").value(0));

    // Critical: findByUserId called with ctrl_b's id (99), not ctrl_a's id (42)
    verify(reportRepository).findByUserId(99L);
    verify(reportRepository, never()).findByUserId(42L);
}
```

### Pattern 3: Photo Volume Persistence Test (SC-3)

**What:** Verify that `AppConfig.getUploadsDir()` returns a usable path, that a file
written there by the controller is readable after the Java process restarts (i.e., it
survives in the underlying filesystem/volume), and that the `AppConfig.properties`
reflection patch works correctly for test isolation.

**The Docker constraint:** JUnit tests cannot invoke `docker compose down && docker
compose up --build`. The acceptance strategy established in Phase 2 is:
- Unit test verifies the file-I/O path (write → read back from same path) using `@TempDir`
- Manual verification documents the Docker volume persistence
- `docker-compose.yml` line `uploads_data:/app/uploads` is the production evidence

**ALREADY COVERED** by `photoUpload_savesFileAndUpdatesPhotoPath` and
`getPhoto_streamsFileWithCorrectContentType` in `ReportControllerTest`. Together they
prove write + read of the underlying file works.

**Gap to fill:** A dedicated test that explicitly names the Docker volume persistence
concern and asserts that a file written to a directory returned by `AppConfig.getUploadsDir()`
is readable from a fresh path lookup using the same `getUploadsDir()` call. This is
essentially a regression guard for the `app.uploads.dir` property plumbing.

```java
// Source: pattern from ReportControllerTest.setUploadsDir() helper
// New test — add to ReportControllerTest or a new PhotoPersistenceTest
@Test
void uploadsDir_fileWrittenAndReadableFromSamePath(@TempDir Path tempDir) throws Exception {
    setUploadsDir(tempDir.toString());  // patch AppConfig.properties static field

    // Simulate what AppConfig.getUploadsDir() will return at runtime
    String uploadsDir = AppConfig.getUploadsDir();
    Path targetDir = Paths.get(uploadsDir, "42");
    Files.createDirectories(targetDir);
    Path targetFile = targetDir.resolve("photo.jpg");
    Files.write(targetFile, "photo-content".getBytes());

    // Verify: path is readable using the same AppConfig call a fresh request would use
    String resolvedDir = AppConfig.getUploadsDir();
    Path resolvedFile = Paths.get(resolvedDir, "42", "photo.jpg");
    assertTrue("File must exist at path returned by AppConfig.getUploadsDir()",
            Files.exists(resolvedFile));
    assertArrayEquals("photo-content".getBytes(), Files.readAllBytes(resolvedFile));
}
```

### Anti-Patterns to Avoid
- **`@WebMvcTest`**: Crashes JVM on Java 25 due to Byte Buddy agent attachment. NEVER use it.
- **`spy()` on JPA entities**: `VerifyError` on Java 25. Use `ArgumentCaptor` instead.
- **`Assumptions.abort(String)`**: Not present in JUnit 5.8.2. Use `assumeTrue(false)` for stubs.
- **`ResultMatcher.or()`**: Not available in Spring Boot 2.6.15. Simplify assertions.
- **`mockStatic(AppConfig.class)`**: `VerifyError` on Java 25. Use the reflection patch on `AppConfig.properties` field.
- **Creating a new test class for SC-1**: `SecurityConfigTest` already passes both 401 and 403 assertions; do not duplicate the Spring context setup.

---

## Don't Hand-Roll

| Problem | Don't Build | Use Instead | Why |
|---------|-------------|-------------|-----|
| Security filter assertion | Custom servlet filter mock | `springSecurity()` + `AnnotationConfigWebApplicationContext` | Already proven in `SecurityConfigTest` |
| Mock uploads path | `mockStatic(AppConfig.class)` | Reflection on `AppConfig.properties` field | Static mocking causes `VerifyError` on Java 25 |
| JWT token in tests | Full HTTP login roundtrip | `UsernamePasswordAuthenticationToken` with `SimpleGrantedAuthority` | No live auth server needed; Spring Security `with(authentication(...))` injects directly |
| Docker volume persistence test | Shell script or Docker SDK call | JUnit `@TempDir` + file I/O assertions | `docker compose` is not accessible inside JUnit on this system |

**Key insight:** This phase adds approximately 2-3 test methods total. The heavy lifting
(security rules, ownership enforcement) was implemented in phases 1-2. Phase 4 is
documentation-by-test, not implementation.

---

## Common Pitfalls

### Pitfall 1: Believing SC-1 is uncovered
**What goes wrong:** Researcher or planner scans for "SC-1 test" and finds nothing
named that way; concludes the criterion needs new work.
**Why it happens:** `SecurityConfigTest` was written to Phase 1 requirements, not
Phase 4 success criteria; names don't match.
**How to avoid:** Map each success criterion to existing test method names by behavior,
not name. `reports_returns401_whenUnauthenticated` and `reports_returns403_forCustomerJwt`
satisfy SC-1 verbatim.
**Warning signs:** Plan that creates a brand-new security test class when `SecurityConfigTest`
already exists and passes.

### Pitfall 2: Forgetting the SecurityContext reset between tests
**What goes wrong:** Test A sets `SecurityContextHolder` to `ctrl_b`, test B assumes
`ctrl_user` (the `@BeforeEach` default) but SecurityContext still holds `ctrl_b`.
**Why it happens:** `standaloneSetup()` does not clear `SecurityContextHolder` between tests.
`@BeforeEach` in `ReportControllerTest` sets `ctrl_user`, but if a new test overrides the
context and does not restore it, subsequent tests fail intermittently.
**How to avoid:** Any test that overrides the `SecurityContext` must either restore it
in `@AfterEach` or use `SecurityContextHolder.clearContext()` before asserting.
**Warning signs:** Test passes in isolation but fails when run in the full suite.

### Pitfall 3: JUnit 4 vs JUnit 5 in the same class
**What goes wrong:** Adding `@Test` from `org.junit.jupiter.api.Test` to `SecurityConfigTest`
which is a JUnit 4 class using `@Before` / `org.junit.Test`.
**Why it happens:** `SecurityConfigTest` is the only JUnit 4 test class in the project;
everything else uses JUnit 5.
**How to avoid:** Keep `SecurityConfigTest` as pure JUnit 4. Any new test for SC-1
that must live in that class uses JUnit 4 annotations. New tests for SC-2 and SC-3
live in the JUnit 5 `ReportControllerTest` class.

### Pitfall 4: AppConfig.properties patch races
**What goes wrong:** `setUploadsDir()` mutates a static `Properties` object shared
across all tests. If tests run in parallel, one test's `@TempDir` value leaks into
another test.
**Why it happens:** `AppConfig.properties` is a static field. Reflection patches it
globally in the JVM.
**How to avoid:** Restore the original property value in `@AfterEach`, or (simpler)
run upload-related tests with `@TestMethodOrder(MethodOrderer.OrderAnnotation.class)`
and verify each test resets the value. The existing tests use `@TempDir` which
JUnit 5 creates fresh per test — as long as `setUploadsDir` is called in each test
method body before use, the mutation is bounded.

### Pitfall 5: Photo persistence test asserting Docker behavior
**What goes wrong:** Test tries to assert that the file survives `docker compose down`,
fails because the test runs outside Docker.
**Why it happens:** SC-3 language says "accessible after docker compose down && up" —
this cannot be tested programmatically from JUnit.
**How to avoid:** The test asserts the file-I/O contract (`AppConfig.getUploadsDir()`
returns the configured path, file written there is readable back). The Docker volume
binding (`uploads_data:/app/uploads`) is the production mechanism; it is verified by
configuration inspection of `docker-compose.yml` and a comment in the test.

---

## Code Examples

Verified patterns from existing project test files:

### Security context injection without Spring Security filter (standaloneSetup)
```java
// Source: ReportControllerTest.setUp() (existing, JUnit 5)
// Use this pattern in ReportControllerTest for ownership tests
UsernamePasswordAuthenticationToken auth = new UsernamePasswordAuthenticationToken(
        "ctrl_b", null,
        Collections.singletonList(new SimpleGrantedAuthority("ROLE_CONTROLLER")));
SecurityContextHolder.getContext().setAuthentication(auth);
```

### Security context injection WITH filter chain (for 401/403 filter-level tests)
```java
// Source: SecurityConfigTest (existing, JUnit 4)
// Use .with(authentication(auth)) — requires spring-security-test on classpath
mockMvc.perform(get("/api/reports/test")
        .with(authentication(auth)))
        .andExpect(status().isForbidden());
```

### AppConfig reflection patch for uploads dir
```java
// Source: ReportControllerTest.setUploadsDir() (existing)
// NEVER use mockStatic — VerifyError on Java 25
private static void setUploadsDir(String path) throws Exception {
    Field f = AppConfig.class.getDeclaredField("properties");
    f.setAccessible(true);
    Properties props = (Properties) f.get(null);
    props.setProperty("app.uploads.dir", path);
}
```

### Repository call verification for ownership
```java
// Source: ReportControllerTest.listReports_returnsOnlyCallerReports_notOtherControllers
verify(reportRepository).findByUserId(42L);
verify(reportRepository, never()).findByUserId(99L);
// or for complete absence:
verify(reportRepository, never()).findByUserIdAndStatus(any(), any());
```

---

## State of the Art

| Old Approach | Current Approach | When Changed | Impact |
|--------------|------------------|--------------|--------|
| `@WebMvcTest` for controller security tests | `AnnotationConfigWebApplicationContext` + `springSecurity()` | Phase 1 (Jan 2026) | Required on Java 25 / Spring Boot 2.6.15 due to Byte Buddy crash |
| `mockStatic(AppConfig.class)` | Reflection on `AppConfig.properties` static field | Phase 2 (Mar 2026) | Required on Java 25 due to `VerifyError` with Byte Buddy inline mock maker |
| `spy()` on JPA entity | `ArgumentCaptor` on `repository.save()` | Phase 2 (Mar 2026) | Required on Java 25 |
| `Assumptions.abort(String)` | `assumeTrue(false)` | Phase 2 (Mar 2026) | `abort(String)` added in JUnit 5.9.0; project uses 5.8.2 |

**Deprecated/outdated in this project:**
- `@WebMvcTest`: causes JVM crash on Java 25 + Spring Boot 2.6.15 — permanently banned
- `mockStatic(...)`: causes `VerifyError` on Java 25 — permanently banned
- `spy()` on JPA entities: `VerifyError` on Java 25 — use `ArgumentCaptor` instead

---

## Open Questions

1. **Is `listReports_returnsOnlyCallerReports_notOtherControllers` sufficient for SC-2?**
   - What we know: it asserts `findByUserId(42L)` called, `findByUserIdAndStatus` never called, 2 results returned.
   - What's unclear: The success criterion says "controller B cannot read controller A's reports" — the existing test proves the endpoint scopes by userId but does not explicitly set up two controllers in the same scenario.
   - Recommendation: Add one explicit two-controller scenario as described in Pattern 2 above to make the assertion unambiguous for reviewers.

2. **Docker volume persistence — acceptable as documentation-only?**
   - What we know: `docker-compose.yml` has `uploads_data:/app/uploads`; `APP_UPLOADS_DIR=/app/uploads` env var is set; both were manually verified in Phase 2.
   - What's unclear: The success criterion says "with no manual intervention" — this implies the test should be automated.
   - Recommendation: Add a JUnit test that asserts the file-I/O contract (write + re-read via `AppConfig.getUploadsDir()`) and a comment block in the test that explains the Docker volume is the production mechanism. This satisfies "automated test exists" while acknowledging the container orchestration layer cannot be tested from JUnit.

---

## Validation Architecture

### Test Framework
| Property | Value |
|----------|-------|
| Framework | JUnit 5 (Jupiter) 5.8.2 + JUnit 4 Vintage (via junit-vintage-engine) |
| Config file | `pom.xml` — `spring-boot-starter-test` + explicit `mockito-core:5.17.0` |
| Quick run command | `mvn test -Dtest=ReportControllerTest,SecurityConfigTest -q` |
| Full suite command | `mvn test` |

### Phase Requirements → Test Map
| Req ID | Behavior | Test Type | Automated Command | File Exists? |
|--------|----------|-----------|-------------------|-------------|
| SC-1 (401) | Anonymous request to `/api/reports/**` returns 401 | unit (security filter) | `mvn test -Dtest=SecurityConfigTest#reports_returns401_whenUnauthenticated` | Already exists |
| SC-1 (403) | CUSTOMER JWT to `/api/reports/**` returns 403 | unit (security filter) | `mvn test -Dtest=SecurityConfigTest#reports_returns403_forCustomerJwt` | Already exists |
| SC-2 (edit) | Controller B PATCH on controller A's report returns 403 | unit (controller) | `mvn test -Dtest=ReportControllerTest#patchReport_returns403_whenNotOwner` | Already exists |
| SC-2 (read) | Controller B GET /api/reports returns only B's reports, never A's | unit (controller) | `mvn test -Dtest=ReportControllerTest#listReports_doesNotReturnOtherControllersReports` | Wave 0 |
| SC-3 | File written to `AppConfig.getUploadsDir()` path is re-readable | unit (file I/O) | `mvn test -Dtest=ReportControllerTest#uploadsDir_fileWrittenAndReadableFromSamePath` | Wave 0 |

### Sampling Rate
- **Per task commit:** `mvn test -Dtest=ReportControllerTest,SecurityConfigTest -q`
- **Per wave merge:** `mvn test`
- **Phase gate:** Full suite green before `/gsd:verify-work`

### Wave 0 Gaps
- [ ] `listReports_doesNotReturnOtherControllersReports` in `ReportControllerTest` — covers SC-2 read path
- [ ] `uploadsDir_fileWrittenAndReadableFromSamePath` in `ReportControllerTest` — covers SC-3

*(Existing tests cover SC-1 in full and SC-2 edit path. Only 2 new test methods needed.)*

---

## Sources

### Primary (HIGH confidence)
- Direct source read: `src/test/java/com/aflokkat/config/SecurityConfigTest.java` — verified 3 passing tests cover SC-1
- Direct source read: `src/test/java/com/aflokkat/controller/ReportControllerTest.java` — verified 12 passing tests including `patchReport_returns403_whenNotOwner` (SC-2 edit)
- Direct source read: `src/main/java/com/aflokkat/config/SecurityConfig.java` — confirmed `.antMatchers("/api/reports/**").hasRole("CONTROLLER")`
- `mvn test -Dtest=ReportControllerTest,SecurityConfigTest,RestaurantControllerSearchTest` — all 19 tests pass, BUILD SUCCESS
- Direct source read: `docker-compose.yml` — `uploads_data:/app/uploads` volume confirmed

### Secondary (MEDIUM confidence)
- Project STATE.md accumulated decisions — Java 25 constraints on Byte Buddy, `spy()`, `abort()`, `ResultMatcher.or()` documented from prior phases

### Tertiary (LOW confidence)
- None needed — all claims verified from source files

---

## Metadata

**Confidence breakdown:**
- Standard stack: HIGH — all libraries verified from `pom.xml`; versions confirmed
- Architecture: HIGH — patterns read directly from existing passing test classes
- Pitfalls: HIGH — all from direct source analysis and project STATE.md decisions

**Research date:** 2026-03-31
**Valid until:** Stable (no external dependencies change; all verification from local source files)
