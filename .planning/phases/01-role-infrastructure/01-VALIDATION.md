---
phase: 1
slug: role-infrastructure
status: draft
nyquist_compliant: false
wave_0_complete: false
created: 2026-03-27
---

# Phase 1 — Validation Strategy

> Per-phase validation contract for feedback sampling during execution.

---

## Test Infrastructure

| Property | Value |
|----------|-------|
| **Framework** | JUnit 4 + Mockito (existing) |
| **Config file** | `pom.xml` (existing) |
| **Quick run command** | `mvn test -pl . -Dtest=AuthServiceTest,JwtUtilTest -q` |
| **Full suite command** | `mvn test -q` |
| **Estimated runtime** | ~30 seconds |

---

## Sampling Rate

- **After every task commit:** Run `mvn test -pl . -Dtest=AuthServiceTest,JwtUtilTest -q`
- **After every plan wave:** Run `mvn test -q`
- **Before `/gsd:verify-work`:** Full suite must be green
- **Max feedback latency:** 30 seconds

---

## Per-Task Verification Map

| Task ID | Plan | Wave | Requirement | Test Type | Automated Command | File Exists | Status |
|---------|------|------|-------------|-----------|-------------------|-------------|--------|
| 1-01-01 | 01 | 1 | AUTH-01 | unit | `mvn test -Dtest=AuthServiceTest -q` | ✅ | ⬜ pending |
| 1-01-02 | 01 | 1 | AUTH-02 | unit | `mvn test -Dtest=AuthServiceTest -q` | ✅ | ⬜ pending |
| 1-01-03 | 01 | 1 | AUTH-03 | unit | `mvn test -Dtest=SecurityConfigTest -q` | ❌ W0 | ⬜ pending |
| 1-01-04 | 01 | 1 | AUTH-04 | unit | `mvn test -Dtest=RateLimitFilterTest -q` | ❌ W0 | ⬜ pending |
| 1-01-05 | 01 | 1 | AUTH-05 | unit | `mvn test -Dtest=DataSeederTest -q` | ❌ W0 | ⬜ pending |

*Status: ⬜ pending · ✅ green · ❌ red · ⚠️ flaky*

---

## Wave 0 Requirements

- [ ] `src/test/java/com/aflokkat/config/SecurityConfigTest.java` — stubs for AUTH-03 (antMatcher rules)
- [ ] `src/test/java/com/aflokkat/security/RateLimitFilterTest.java` — stubs for AUTH-04 (rate limiting)
- [ ] `src/test/java/com/aflokkat/DataSeederTest.java` — stubs for AUTH-05 (seeded accounts)

*Existing infrastructure covers AUTH-01 and AUTH-02 via `AuthServiceTest`.*

---

## Manual-Only Verifications

| Behavior | Requirement | Why Manual | Test Instructions |
|----------|-------------|------------|-------------------|
| HTTP 429 returned on 11th request to /api/auth/login | AUTH-04 | Requires running app + HTTP client to send burst requests | `for i in $(seq 1 11); do curl -s -o /dev/null -w "%{http_code}\n" -X POST http://localhost:8080/api/auth/login -H 'Content-Type: application/json' -d '{"username":"x","password":"x"}'; done` |
| Seeded accounts can log in immediately after startup | AUTH-05 | Requires running Docker Compose stack | `curl -s -X POST http://localhost:8080/api/auth/login -d '{"username":"customer_test","password":"Test1234!"}' \| grep accessToken` |

---

## Validation Sign-Off

- [ ] All tasks have `<automated>` verify or Wave 0 dependencies
- [ ] Sampling continuity: no 3 consecutive tasks without automated verify
- [ ] Wave 0 covers all MISSING references
- [ ] No watch-mode flags
- [ ] Feedback latency < 30s
- [ ] `nyquist_compliant: true` set in frontmatter

**Approval:** pending
