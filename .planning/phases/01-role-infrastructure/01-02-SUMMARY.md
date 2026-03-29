---
phase: 01-role-infrastructure
plan: 02
subsystem: auth
tags: [spring-security, jwt, role-based-access, mockmvc, junit4]

# Dependency graph
requires:
  - phase: 01-role-infrastructure
    provides: "JwtAuthenticationFilter, JwtUtil, UserEntity with ROLE_ fields (01-01)"
provides:
  - "antMatchers URL-level access rules: /api/reports/** CONTROLLER-only, /api/users/** any-auth"
  - "403 accessDeniedHandler returning JSON for wrong-role access"
  - "SecurityConfigTest with 3 slice tests (401 unauthenticated, 403 CUSTOMER, 200 CONTROLLER)"
  - "FilterRegistrationBean preventing JwtAuthenticationFilter double-registration"
  - "MethodSecurityConfig isolating @EnableMethodSecurity from test contexts"
affects:
  - 02-controller-reports
  - 03-customer-ui

# Tech tracking
tech-stack:
  added:
    - "spring-security-test (SecurityMockMvcRequestPostProcessors.authentication)"
    - "junit-vintage-engine (JUnit 4 on JUnit 5 Surefire platform)"
    - "Byte Buddy 1.16.0 (Java 25 support, overriding Spring Boot BOM 1.14.x)"
    - "Mockito 5.17.0 (replaces mockito-inline; inline mocking built in)"
  patterns:
    - "Test slice using AnnotationConfigWebApplicationContext + standaloneSetup MockMvc (replaces @WebMvcTest due to Java 25 JVM crash)"
    - "SecurityConfig registered before SecurityAutoConfiguration to prevent duplicate FilterChain"
    - "authentication() post-processor survives SecurityContextPersistenceFilter stateless reset"
    - "FilterRegistrationBean.setEnabled(false) prevents filter double-registration"
    - "@EnableMethodSecurity in separate MethodSecurityConfig to isolate AOP from test context"

key-files:
  created:
    - src/test/java/com/aflokkat/config/SecurityConfigTest.java
    - src/main/java/com/aflokkat/config/MethodSecurityConfig.java
  modified:
    - src/main/java/com/aflokkat/config/SecurityConfig.java
    - pom.xml

key-decisions:
  - "Abandoned @WebMvcTest for SecurityConfigTest: Mockito's dynamic byte-buddy-agent attachment on Java 25 JVM causes JVM crash (not a Java StackOverflowError). Used JUnit 4 + AnnotationConfigWebApplicationContext instead."
  - "SecurityConfig must be registered BEFORE SecurityAutoConfiguration in AnnotationConfigWebApplicationContext: @ConditionalOnDefaultWebSecurity checks for existing SecurityFilterChain bean; wrong order produces two competing chains."
  - "authentication() post-processor (from spring-security-test) injected via .with() rather than SecurityContextHolder.setAuthentication(): the former survives the stateless SecurityContextPersistenceFilter reset; the latter does not."
  - "FilterRegistrationBean.setEnabled(false) added to SecurityConfig: JwtAuthenticationFilter declared as @Bean is auto-registered by Spring Boot as servlet filter AND added to security chain, causing double execution."
  - "Byte Buddy pinned to 1.16.0: Spring Boot 2.6.x BOM pulls 1.14.18 which cannot instrument Java 25 class files (major version 69)."

patterns-established:
  - "Spring Security test slice on Java 25: use AnnotationConfigWebApplicationContext + standaloneSetup + springSecurity(filterChain). Never @WebMvcTest."
  - "Filter chain ordering: register SecurityConfig before SecurityAutoConfiguration to avoid default chain duplication."
  - "Role check pattern: hasRole('CONTROLLER') → stored as ROLE_CONTROLLER in UserEntity, matched as SimpleGrantedAuthority('ROLE_CONTROLLER') in tests."

requirements-completed: [AUTH-03]

# Metrics
duration: ~90min
completed: 2026-03-29
---

# Phase 1 Plan 02: Security Antmatchers Summary

**Spring Security antMatchers locking /api/reports/** to CONTROLLER role only, verified by 3 slice tests using a JUnit 4 + AnnotationConfigWebApplicationContext workaround for Java 25 JVM incompatibility**

## Performance

- **Duration:** ~90 min
- **Started:** 2026-03-29T18:00:00Z
- **Completed:** 2026-03-29T19:55:00Z
- **Tasks:** 2 (RED + GREEN)
- **Files modified:** 4 (SecurityConfig.java, SecurityConfigTest.java, pom.xml, MethodSecurityConfig.java created)

## Accomplishments
- Replaced blanket `anyRequest().permitAll()` with explicit ordered antMatchers — /api/reports/** requires CONTROLLER role, /api/users/** requires any auth, public paths explicitly whitelisted
- Added 403 `accessDeniedHandler` returning JSON for authenticated users with insufficient role
- Created SecurityConfigTest with 3 verified slice tests (401 unauthenticated, 403 CUSTOMER, 200 CONTROLLER), all green
- Resolved Java 25 JVM incompatibility blocking @WebMvcTest by switching to AnnotationConfigWebApplicationContext with Byte Buddy 1.16.0

## Task Commits

Each task was committed atomically:

1. **Task 1: SecurityConfigTest RED** - `b2eec53` (test)
2. **Task 2: antMatchers + accessDeniedHandler GREEN** - `ed67c50` (feat)

**Plan metadata:** (docs commit — see below)

## Files Created/Modified
- `src/main/java/com/aflokkat/config/SecurityConfig.java` — Added antMatchers rules, accessDeniedHandler, FilterRegistrationBean; removed @EnableMethodSecurity
- `src/main/java/com/aflokkat/config/MethodSecurityConfig.java` — New file isolating @EnableMethodSecurity from SecurityConfig
- `src/test/java/com/aflokkat/config/SecurityConfigTest.java` — JUnit 4 slice test using AnnotationConfigWebApplicationContext + standaloneSetup + springSecurity()
- `pom.xml` — Added spring-security-test, junit-vintage-engine; upgraded Byte Buddy to 1.16.0; removed mockito-inline; added -XX:+EnableDynamicAgentLoading to Surefire

## Decisions Made
- Abandoned @WebMvcTest entirely for this test class: Mockito's dynamic agent attachment kills the JVM on Java 25, not a code bug. No fix exists within @WebMvcTest.
- Used JUnit 4 (not JUnit 5) to match existing test suite and avoid @ExtendWith(MockitoExtension.class) which also triggers agent attachment.
- SecurityConfig registered before SecurityAutoConfiguration in test context: ensures @ConditionalOnDefaultWebSecurity skips the default chain.
- Kept `anyRequest().permitAll()` as catch-all at end of ordered rules (non-API view routes remain open for Phase 3).

## Deviations from Plan

### Auto-fixed Issues

**1. [Rule 3 - Blocking] @WebMvcTest causes JVM crash on Java 25 — replaced with AnnotationConfigWebApplicationContext**
- **Found during:** Task 1 (Create SecurityConfigTest RED)
- **Issue:** Plan specified @WebMvcTest + @Import(SecurityConfig.class) + @MockBean. On Java 25, Mockito's MockitoTestExecutionListener attempts to self-attach byte-buddy-agent dynamically. This kills the forked Surefire JVM before any test runs. No configuration option avoids it within @WebMvcTest.
- **Fix:** Replaced @WebMvcTest with JUnit 4 + AnnotationConfigWebApplicationContext + MockMvcBuilders.standaloneSetup(). Upgraded Byte Buddy to 1.16.0 and added -XX:+EnableDynamicAgentLoading to Surefire argLine.
- **Files modified:** SecurityConfigTest.java, pom.xml
- **Verification:** `mvn test -Dtest=SecurityConfigTest` passes 3/3 tests
- **Committed in:** b2eec53 (Task 1 commit), ed67c50 (Task 2 commit)

**2. [Rule 3 - Blocking] Removed mockito-inline (conflicts with Mockito 5.x)**
- **Found during:** Task 1 (pom.xml dependency resolution)
- **Issue:** mockito-inline 5.2.0 and mockito-core 5.17.0 both register a MockMaker, causing `Unknown Java version: 0` on Java 21+. Mockito 5.x merged inline mocking into core.
- **Fix:** Removed mockito-inline from pom.xml entirely.
- **Files modified:** pom.xml
- **Committed in:** b2eec53

**3. [Rule 2 - Missing Critical] Added FilterRegistrationBean to prevent JwtAuthenticationFilter double-registration**
- **Found during:** Task 2 (SecurityConfig implementation)
- **Issue:** JwtAuthenticationFilter declared as @Bean in SecurityConfig is both added to the security chain (via addFilterBefore) and auto-registered by Spring Boot as a standalone servlet filter — double-execution in production and StackOverflow in MockMvc.
- **Fix:** Added FilterRegistrationBean<JwtAuthenticationFilter> with setEnabled(false) to SecurityConfig.
- **Files modified:** SecurityConfig.java
- **Committed in:** ed67c50

**4. [Rule 2 - Missing Critical] Extracted @EnableMethodSecurity to MethodSecurityConfig**
- **Found during:** Task 2 (Test context debugging)
- **Issue:** @EnableMethodSecurity on SecurityConfig activates AOP proxying infrastructure which conflicts with the standalone AnnotationConfigWebApplicationContext test setup.
- **Fix:** Created MethodSecurityConfig.java with @EnableMethodSecurity; removed annotation from SecurityConfig.
- **Files modified:** SecurityConfig.java, MethodSecurityConfig.java (created)
- **Committed in:** ed67c50

---

**Total deviations:** 4 auto-fixed (2 blocking, 2 missing critical)
**Impact on plan:** All fixes essential — test approach was fundamentally incompatible with runtime JVM version; production correctness required FilterRegistrationBean fix.

## Issues Encountered
- `AuthServiceTest` has 14 pre-existing failures (`Mockito cannot mock JwtUtil`). This predates plan 01-02 — confirmed by stash test. Logged as deferred item; out of scope for this plan.
- `mvn test -q` therefore exits non-zero, but SecurityConfigTest (3 tests), AppConfigTest (7 tests), and all other non-AuthServiceTest suites pass.

## User Setup Required
None - no external service configuration required.

## Next Phase Readiness
- Security boundary is locked: /api/reports/** correctly rejects unauthenticated and wrong-role requests
- FilterChain order established; new endpoints can rely on this pattern
- AuthServiceTest pre-existing Mockito failures need resolution before full suite can be green (out of scope here)
- Phase 2 (controller reports) can proceed — security rules are in place

---
*Phase: 01-role-infrastructure*
*Completed: 2026-03-29*
