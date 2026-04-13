---
phase: 16
slug: security-hardening
status: draft
nyquist_compliant: false
wave_0_complete: false
created: 2026-04-13
---

# Phase 16 — Validation Strategy

> Per-phase validation contract for feedback sampling during execution.

---

## Test Infrastructure

| Property | Value |
|----------|-------|
| **Framework** | JUnit 5 + Mockito 5.17.0 |
| **Config file** | pom.xml (maven-surefire-plugin) |
| **Quick run command** | `mvn test -Dtest=SecurityConfigTest,RateLimitFilterTest,GlobalExceptionHandlerTest -q` |
| **Full suite command** | `mvn test -q` |
| **Estimated runtime** | ~30 seconds |

---

## Sampling Rate

- **After every task commit:** Run `mvn test -Dtest=SecurityConfigTest,RateLimitFilterTest -q`
- **After every plan wave:** Run `mvn test -q`
- **Before `/gsd-verify-work`:** Full suite must be green
- **Max feedback latency:** 30 seconds

---

## Per-Task Verification Map

| Task ID | Plan | Wave | Requirement | Threat Ref | Secure Behavior | Test Type | Automated Command | File Exists | Status |
|---------|------|------|-------------|------------|-----------------|-----------|-------------------|-------------|--------|
| 16-01-01 | 01 | 0 | SEC-03 | T-16-03 / ASVS V5 | Empty password field → HTTP 400, not 200 | unit (MockMvc) | `mvn test -Dtest=AuthControllerValidationTest -q` | ❌ W0 | ⬜ pending |
| 16-01-02 | 01 | 0 | SEC-04 | T-16-04 / ASVS V5 | 400 body has status, message, timestamp keys | unit | `mvn test -Dtest=GlobalExceptionHandlerTest -q` | ❌ W0 | ⬜ pending |
| 16-01-03 | 01 | 0 | SEC-01 | T-16-01 / ASVS V14 | Unlisted origin OPTIONS → 403, not 200 | unit (MockMvc) | `mvn test -Dtest=SecurityConfigTest -q` | Partial W0 | ⬜ pending |
| 16-01-04 | 01 | 0 | SEC-02 | T-16-02 / ASVS V14 | Response includes X-Content-Type-Options + X-Frame-Options | unit (MockMvc) | `mvn test -Dtest=SecurityConfigTest -q` | Partial W0 | ⬜ pending |
| 16-01-05 | 01 | 0 | SEC-06 | T-16-06 / — | Restaurant path returns 429 after 100 req/min | unit | `mvn test -Dtest=RateLimitFilterTest -q` | Partial W0 | ⬜ pending |
| 16-02-01 | 02 | 1 | SEC-01 | T-16-01 / ASVS V14 | CorsConfigurationSource bean + http.cors(withDefaults()) wired | unit (MockMvc) | `mvn test -Dtest=SecurityConfigTest -q` | ✅ exists | ⬜ pending |
| 16-02-02 | 02 | 1 | SEC-02 | T-16-02 / ASVS V14 | X-Content-Type-Options: nosniff + X-Frame-Options: DENY in every response | unit (MockMvc) | `mvn test -Dtest=SecurityConfigTest -q` | ✅ exists | ⬜ pending |
| 16-03-01 | 03 | 1 | SEC-03 | T-16-03 / ASVS V5 | @Valid on all 3 auth controller params; @NotBlank on DTO fields | unit | `mvn test -Dtest=AuthControllerValidationTest -q` | ❌ W0 | ⬜ pending |
| 16-03-02 | 03 | 1 | SEC-04 | T-16-04 / ASVS V5 | GlobalExceptionHandler returns {status, message, timestamp} | unit | `mvn test -Dtest=GlobalExceptionHandlerTest -q` | ❌ W0 | ⬜ pending |
| 16-04-01 | 04 | 1 | SEC-06 | T-16-06 / — | Restaurant bucket independent from auth bucket | unit | `mvn test -Dtest=RateLimitFilterTest -q` | Partial W0 | ⬜ pending |
| 16-05-01 | 05 | 1 | SEC-07 | T-16-07 / ASVS V14 | server.forward-headers-strategy=native in application.properties | manual | `grep -c "server.forward-headers-strategy=native" src/main/resources/application.properties` | — | ⬜ pending |
| 16-05-02 | 05 | 1 | SEC-08 | T-16-08 / ASVS V6 | JWT startup assertion already tested | unit | `mvn test -Dtest=AppConfigTest -q` | ✅ exists | ⬜ pending |

*Status: ⬜ pending · ✅ green · ❌ red · ⚠️ flaky*

---

## Wave 0 Requirements

- [ ] `src/test/java/com/aflokkat/controller/AuthControllerValidationTest.java` — stubs for SEC-03 (empty field → 400)
- [ ] `src/test/java/com/aflokkat/controller/GlobalExceptionHandlerTest.java` — stubs for SEC-04 (JSON shape: status, message, timestamp)
- [ ] Extend `src/test/java/com/aflokkat/config/SecurityConfigTest.java` — add CORS assertions (SEC-01: unlisted origin → 403; SEC-02: header presence)
- [ ] Extend `src/test/java/com/aflokkat/security/RateLimitFilterTest.java` — add restaurant-path bucket test (SEC-06)

*Note: Package paths use `com.aflokkat` per Phase 21 rename (already on develop branch per CLAUDE.md)*

---

## Manual-Only Verifications

| Behavior | Requirement | Why Manual | Test Instructions |
|----------|-------------|------------|-------------------|
| `server.forward-headers-strategy=native` present | SEC-07 | One-line config addition, no runtime observable without reverse proxy | `grep -c "server.forward-headers-strategy=native" src/main/resources/application.properties` must return 1 |

---

## Validation Sign-Off

- [ ] All tasks have `<automated>` verify or Wave 0 dependencies
- [ ] Sampling continuity: no 3 consecutive tasks without automated verify
- [ ] Wave 0 covers all MISSING references
- [ ] No watch-mode flags
- [ ] Feedback latency < 30s
- [ ] `nyquist_compliant: true` set in frontmatter

**Approval:** pending
