---
phase: 01-role-infrastructure
plan: 01
subsystem: auth
tags: [jwt, spring-security, postgresql, mockito, role-assignment]

# Dependency graph
requires: []
provides:
  - RegisterRequest DTO with optional signupCode field
  - AuthService.register() assigns ROLE_CUSTOMER (no code) or ROLE_CONTROLLER (correct code) or throws 400 (wrong/disabled code)
  - AppConfig.getControllerSignupCode() reads CONTROLLER_SIGNUP_CODE env var
  - 4 unit tests covering all role-assignment scenarios
affects:
  - 01-02-security-config
  - 01-03-rate-limiting
  - Phase 2 (controller report submission requires ROLE_CONTROLLER JWT claim)

# Tech tracking
tech-stack:
  added:
    - mockito-core 5.17.0 (upgraded from 4.0.0 — required for Java 21+ static mocking support)
    - mockito-junit-jupiter 5.17.0
    - byte-buddy 1.14.18 (property override — BOM 1.11.x does not support Java 21+)
  patterns:
    - Role strings stored as ROLE_CUSTOMER / ROLE_CONTROLLER (Spring hasRole() prefix convention)
    - AppConfig.getProperty() pattern for env-var -> .env -> application.properties resolution
    - mockStatic(AppConfig.class) try-with-resources pattern for static method mocking in JUnit 5

key-files:
  created: []
  modified:
    - src/main/java/com/aflokkat/dto/RegisterRequest.java
    - src/main/java/com/aflokkat/service/AuthService.java
    - src/main/java/com/aflokkat/config/AppConfig.java
    - src/test/java/com/aflokkat/service/AuthServiceTest.java
    - pom.xml

key-decisions:
  - "Use ROLE_CUSTOMER / ROLE_CONTROLLER strings (never ROLE_USER) — locked in for Phase 2 onwards"
  - "Controller signup is fail-safe: if CONTROLLER_SIGNUP_CODE env var absent, any signupCode value returns 400"
  - "Upgrade Mockito to 5.x and Byte Buddy to 1.14.x to support static mocking on Java 21+ runtime"

patterns-established:
  - "Role assignment: no signupCode -> ROLE_CUSTOMER; correct signupCode -> ROLE_CONTROLLER; wrong/absent -> IllegalArgumentException"
  - "mockStatic pattern: try (MockedStatic<AppConfig> cfg = mockStatic(AppConfig.class)) { cfg.when(...).thenReturn(...); }"

requirements-completed: [AUTH-01, AUTH-02]

# Metrics
duration: 15min
completed: 2026-03-29
---

# Phase 1 Plan 1: Role-Assignment Registration Summary

**Registration now assigns ROLE_CUSTOMER by default or ROLE_CONTROLLER when the correct env-var signup code is provided, with fail-safe rejection when the code is wrong or disabled**

## Performance

- **Duration:** ~15 min
- **Started:** 2026-03-29T16:28:00Z
- **Completed:** 2026-03-29T16:43:34Z
- **Tasks:** 1
- **Files modified:** 5

## Accomplishments
- Replaced hardcoded `ROLE_USER` in `AuthService.register()` with role-assignment logic based on signupCode
- Added `signupCode` field (optional) to `RegisterRequest` DTO with getter/setter
- Added `AppConfig.getControllerSignupCode()` reading `CONTROLLER_SIGNUP_CODE` env var
- All 4 role-assignment unit tests pass alongside 10 pre-existing `AuthServiceTest` cases (14 total)
- Fixed Mockito/Byte Buddy incompatibility with Java 21+ runtime (upgraded to 5.x / 1.14.x)

## Task Commits

Each task was committed atomically:

1. **Task 1: Add signupCode role-assignment to registration** - `66275a4` (feat)

**Plan metadata:** TBD (docs: complete plan)

## Files Created/Modified
- `src/main/java/com/aflokkat/dto/RegisterRequest.java` - Added signupCode field + getter/setter
- `src/main/java/com/aflokkat/service/AuthService.java` - Role-assignment logic replacing ROLE_USER
- `src/main/java/com/aflokkat/config/AppConfig.java` - Added getControllerSignupCode() static method
- `src/test/java/com/aflokkat/service/AuthServiceTest.java` - 4 new tests + updated existing stubs
- `pom.xml` - Mockito 5.17.0, mockito-junit-jupiter 5.17.0, byte-buddy 1.14.18

## Decisions Made
- ROLE_CUSTOMER and ROLE_CONTROLLER locked in as the only two roles assigned at registration. ROLE_USER is removed — no new user will ever receive it.
- Controller signup is fail-safe: if `CONTROLLER_SIGNUP_CODE` is not set in the environment, any request with a signupCode receives HTTP 400, preventing accidental privilege escalation.
- Mockito upgraded to 5.x (from BOM-managed 4.0.0) because `mockStatic` relies on Byte Buddy instrumentation which requires 1.14.x+ on Java 21/25 runtimes. This is a test-only dependency change.

## Deviations from Plan

### Auto-fixed Issues

**1. [Rule 3 - Blocking] Added mockito-inline / upgraded Mockito + Byte Buddy for Java 21+ static mocking**
- **Found during:** Task 1 verification (mvn test -Dtest=AuthServiceTest)
- **Issue:** Tests using `mockStatic(AppConfig.class)` failed with "SubclassByteBuddyMockMaker does not support static mocks" on Mockito 4.0.0. Adding `mockito-inline` 4.0.0 caused a secondary error "Unknown Java version: 0" because Byte Buddy 1.11.22 (Spring Boot 2.6 BOM) doesn't support Java 21+.
- **Fix:** Upgraded `mockito-core` and `mockito-junit-jupiter` to 5.17.0 (static mocking built-in), removed redundant `mockito-inline` artifact (Mockito 5 no longer needs it separately), added `<byte-buddy.version>1.14.18</byte-buddy.version>` property to override the BOM version.
- **Files modified:** `pom.xml`
- **Verification:** `mvn test -Dtest=AuthServiceTest` exits 0, 14/14 tests pass
- **Committed in:** `66275a4` (Task 1 commit)

---

**Total deviations:** 1 auto-fixed (Rule 3 — blocking dependency issue)
**Impact on plan:** Required to unblock test execution. Test-only dependency change; no production code affected.

## Issues Encountered
- Maven runs on Java 25 by default on this machine (`/usr/lib/jvm/java-25-openjdk-amd64`). Tests must be run with `JAVA_HOME=/usr/lib/jvm/java-21-openjdk-amd64` or the system default Java updated. The pom.xml fix (byte-buddy 1.14.18) makes the tests runnable on Java 21+; Java 25 support requires Byte Buddy 1.15.x (not yet tested).

## Next Phase Readiness
- Role assignment foundation complete — ROLE_CUSTOMER and ROLE_CONTROLLER are now correctly persisted in PostgreSQL
- Ready for plan 01-02 (SecurityConfig URL guards using hasRole())
- Blocker from STATE.md remains: `anyRequest().permitAll()` in SecurityConfig must be replaced before Phase 2 feature code

## Self-Check: PASSED

- FOUND: .planning/phases/01-role-infrastructure/01-01-SUMMARY.md
- FOUND: src/main/java/com/aflokkat/dto/RegisterRequest.java
- FOUND: src/main/java/com/aflokkat/service/AuthService.java
- FOUND: src/main/java/com/aflokkat/config/AppConfig.java
- FOUND: commit 66275a4

---
*Phase: 01-role-infrastructure*
*Completed: 2026-03-29*
