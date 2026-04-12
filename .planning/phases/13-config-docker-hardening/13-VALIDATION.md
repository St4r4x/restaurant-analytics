---
phase: 13
slug: config-docker-hardening
status: draft
nyquist_compliant: false
wave_0_complete: false
created: 2026-04-12
---

# Phase 13 — Validation Strategy

> Per-phase validation contract for feedback sampling during execution.

---

## Test Infrastructure

| Property | Value |
|----------|-------|
| **Framework** | JUnit 4 (vintage) + JUnit 5 (Jupiter) via Maven Surefire |
| **Config file** | `pom.xml` — surefire plugin with `@{argLine} -XX:+EnableDynamicAgentLoading` |
| **Quick run command** | `mvn test -Dtest=AppConfigTest,JwtUtilTest -q` |
| **Full suite command** | `mvn test` |
| **Estimated runtime** | ~30 seconds |

---

## Sampling Rate

- **After every task commit:** Run `mvn test -q` — full suite must stay at 174+ passing, 0 failures
- **After every plan wave:** Run `mvn test` (full output with counts)
- **Before `/gsd-verify-work`:** Full suite green + grep-zero check + `docker compose config` validates

---

## Per-Task Verification Map

| Task ID | Plan | Wave | Requirement | Threat Ref | Secure Behavior | Test Type | Automated Command | File Exists | Status |
|---------|------|------|-------------|------------|-----------------|-----------|-------------------|-------------|--------|
| 13-01-01 | 01 | 1 | CFG-02 | T-13-01 | IllegalStateException on missing JWT_SECRET | unit | `mvn test -Dtest=AppConfigTest` | Existing — new test case needed | ⬜ pending |
| 13-01-02 | 01 | 1 | CFG-02 | T-13-01 | IllegalStateException on JWT_SECRET < 32 chars | unit | `mvn test -Dtest=AppConfigTest` | Existing — new test case needed | ⬜ pending |
| 13-01-03 | 01 | 1 | CFG-01 | T-13-02 | No hardcoded secrets remain in source | grep-zero | `grep -r "changeme\|dev-only\|changeit-please" src/ src/main/resources/application.properties` | N/A (shell check) | ⬜ pending |
| 13-01-04 | 01 | 1 | CFG-04 | — | N/A | file-exists | `test -f src/test/resources/application-test.properties` | No — Wave 0 gap | ⬜ pending |
| 13-01-05 | 01 | 1 | CFG-05 | — | N/A | file-exists | `test -f .env.example` | No — Wave 0 gap | ⬜ pending |
| 13-02-01 | 02 | 1 | DOCKER-04 | T-13-03 | Two-stage build with Java 25 Alpine | build | `docker compose build app 2>&1 \| grep -i "successfully built"` | N/A (build check) | ⬜ pending |
| 13-02-02 | 02 | 1 | DOCKER-05 | T-13-04 | Non-root user in runtime stage | file-check | `grep -c "USER appuser" Dockerfile` | N/A (source check) | ⬜ pending |
| 13-02-03 | 02 | 1 | DOCKER-06 | — | N/A | file-exists | `test -f .dockerignore` | No — Wave 0 gap | ⬜ pending |
| 13-03-01 | 03 | 2 | DOCKER-03 | — | Memory limits on all 4 containers | config | `docker compose config \| grep -c "memory: "` | N/A (compose check) | ⬜ pending |
| 13-03-02 | 03 | 2 | DOCKER-01 | — | Health checks present and correct | config | `docker compose config \| grep -c "healthcheck"` | N/A (compose check) | ⬜ pending |
| 13-03-03 | 03 | 2 | CFG-03 | T-13-02 | Signup codes from env (hardcoded removed) | grep-zero | `grep "changeme" docker-compose.yml && echo FOUND || echo CLEAN` | N/A (source check) | ⬜ pending |

*Status: ⬜ pending · ✅ green · ❌ red · ⚠️ flaky*

---

## Wave 0 Requirements

- [ ] `src/test/resources/application-test.properties` — safe test values for CFG-04 (JWT 64-char dummy, localhost DB/Redis, empty signup codes)
- [ ] New test cases in `AppConfigTest` — two cases for CFG-02: (a) JWT_SECRET absent → IllegalStateException, (b) JWT_SECRET < 32 chars → IllegalStateException
- [ ] `JwtUtilTest.setUp()` — patch `AppConfig.properties` via reflection before `new JwtUtil()` to prevent assertion firing in unit tests (project's established pattern per STATE.md)

*Existing infrastructure (JUnit 4 + Mockito, Maven Surefire) covers all other phase requirements.*

---

## Manual-Only Verifications

| Behavior | Requirement | Why Manual | Test Instructions |
|----------|-------------|------------|-------------------|
| App starts without JWT_SECRET → descriptive error | CFG-02 | Runtime startup test requires actual app launch | Run `unset JWT_SECRET && mvn spring-boot:run 2>&1 \| grep "JWT_SECRET"` — should show IllegalStateException message |
| `docker compose up` starts all 4 services in order | DOCKER-01/02 | Requires live Docker daemon | `docker compose up -d && docker compose ps` — all services should show healthy |
| Non-root user in running container | DOCKER-05 | Requires running container | `docker compose exec app whoami` — should return `appuser` |
| Image smaller than single-stage build | DOCKER-04 success criteria | Requires docker build comparison | `docker images restaurant-analytics_app` — compare size to baseline |

---

## Validation Sign-Off

- [ ] All tasks have `<automated>` verify or Wave 0 dependencies
- [ ] Sampling continuity: no 3 consecutive tasks without automated verify
- [ ] Wave 0 covers all MISSING references
- [ ] No watch-mode flags
- [ ] Feedback latency < 60s
- [ ] `nyquist_compliant: true` set in frontmatter

**Approval:** pending
