---
phase: 01-role-infrastructure
plan: "04"
subsystem: auth
tags: [spring-boot, postgresql, jpa, applicationrunner, seeding, test-data]

# Dependency graph
requires:
  - phase: 01-role-infrastructure
    provides: UserEntity constructor with role field, UserRepository with findByUsername, PasswordEncoder bean in SecurityConfig
provides:
  - DataSeeder @Component ApplicationRunner that idempotently seeds customer_test (ROLE_CUSTOMER) and controller_test (ROLE_CONTROLLER) on startup
  - Unit tests verifying creation-when-absent and skip-when-present behaviour
affects:
  - All phases requiring a ready-to-use test account without manual registration
  - Integration tests that depend on known seed credentials

# Tech tracking
tech-stack:
  added: []
  patterns:
    - ApplicationRunner for startup side-effects (seed/migration)
    - Idempotent seeding via findByUsername guard before save

key-files:
  created:
    - src/main/java/com/aflokkat/startup/DataSeeder.java
    - src/test/java/com/aflokkat/startup/DataSeederTest.java
  modified: []

key-decisions:
  - "Constructor injection chosen over @Autowired field injection for DataSeeder (testability, immutability)"
  - "Hardcoded seed password accepted for academic scope; comment documents the production alternative (env var)"
  - "DataSeeder logs at INFO on creation, DEBUG on skip to avoid noise on restarts"

patterns-established:
  - "Idempotent seed: always check repository.findByUsername().isPresent() before save()"
  - "Startup components go in com.aflokkat.startup package, implement ApplicationRunner"

requirements-completed: [AUTH-05]

# Metrics
duration: 2min
completed: 2026-03-29
---

# Phase 1 Plan 4: DataSeeder Summary

**Idempotent ApplicationRunner that seeds customer_test (ROLE_CUSTOMER) and controller_test (ROLE_CONTROLLER) into PostgreSQL on every startup, backed by 2 Mockito unit tests**

## Performance

- **Duration:** ~2 min
- **Started:** 2026-03-29T16:39:28Z
- **Completed:** 2026-03-29T16:40:51Z
- **Tasks:** 1 (TDD: RED already present, GREEN verified)
- **Files modified:** 2

## Accomplishments

- DataSeeder @Component implements ApplicationRunner, seeding two fixed test accounts on startup
- Idempotency guard: findByUsername().isPresent() check prevents duplicate rows on restarts
- Two unit tests pass: run_createsCustomerAndController_whenAbsent and run_skipsExisting_whenAlreadySeeded

## Task Commits

Each task was committed atomically:

1. **Task 1: DataSeeder + DataSeederTest (TDD GREEN)** - `ff32e22` (feat)

**Plan metadata:** (docs commit — see below)

## Files Created/Modified

- `src/main/java/com/aflokkat/startup/DataSeeder.java` - ApplicationRunner that seeds customer_test and controller_test; idempotent
- `src/test/java/com/aflokkat/startup/DataSeederTest.java` - 2 Mockito unit tests for creation and idempotency

## Decisions Made

- Constructor injection chosen over @Autowired field injection — DataSeeder already existed with constructor injection, which is better practice and works seamlessly with @InjectMocks in tests
- Seed password hardcoded as `SEED_PASSWORD` constant with a comment documenting the production alternative
- Logging strategy: INFO on first seed, DEBUG on skip (avoids noise on subsequent restarts)

## Deviations from Plan

None - plan executed exactly as written. Both files were already present as untracked work matching the plan spec exactly. Tests verified passing before commit.

## Issues Encountered

Pre-existing failures in AuthServiceTest (5 tests requiring mockito-inline for MockedStatic) and SecurityConfigTest (3 StackOverflow errors) are out-of-scope and pre-date this plan. They are documented in deferred-items.md if not already tracked.

## User Setup Required

None - no external service configuration required.

## Next Phase Readiness

- Seed accounts are available immediately after `docker compose up -d` — no manual registration needed
- Both accounts use password `Test1234!` and can log in via `POST /api/auth/login`
- Ready for Phase 2 feature work requiring role-specific endpoints

---
*Phase: 01-role-infrastructure*
*Completed: 2026-03-29*
