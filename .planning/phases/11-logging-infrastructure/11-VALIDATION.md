---
phase: 11
slug: logging-infrastructure
status: draft
nyquist_compliant: false
wave_0_complete: false
created: 2026-04-11
---

# Phase 11 — Validation Strategy

> Per-phase validation contract for feedback sampling during execution.

---

## Test Infrastructure

| Property | Value |
|----------|-------|
| **Framework** | JUnit 4 + Mockito (Spring Boot Test) |
| **Config file** | `pom.xml` (spring-boot-starter-test already present) |
| **Quick run command** | `mvn test -Dtest=RequestIdFilterTest -DfailIfNoTests=false` |
| **Full suite command** | `mvn test` |
| **Estimated runtime** | ~30 seconds |

---

## Sampling Rate

- **After every task commit:** Run `mvn test -Dtest=RequestIdFilterTest -DfailIfNoTests=false`
- **After every plan wave:** Run `mvn test`
- **Before `/gsd-verify-work`:** Full suite must be green
- **Max feedback latency:** 30 seconds

---

## Per-Task Verification Map

| Task ID | Plan | Wave | Requirement | Threat Ref | Secure Behavior | Test Type | Automated Command | File Exists | Status |
|---------|------|------|-------------|------------|-----------------|-----------|-------------------|-------------|--------|
| 11-01-01 | 01 | 1 | QA-01 | — | N/A (dependency addition) | build | `mvn dependency:tree \| grep logstash` | ✅ pom.xml | ⬜ pending |
| 11-01-02 | 01 | 1 | QA-01, QA-02 | — | No secret leakage in logs | integration | `mvn test -Dtest=LogbackConfigTest -DfailIfNoTests=false` | ❌ W0 | ⬜ pending |
| 11-01-03 | 01 | 1 | QA-01 | — | simplelogger.properties absent | build | `test ! -f src/main/resources/simplelogger.properties && echo OK` | ✅ exists now | ⬜ pending |
| 11-02-01 | 02 | 2 | QA-02, QA-03 | — | Server-generated UUID only (no client injection) | unit | `mvn test -Dtest=RequestIdFilterTest -DfailIfNoTests=false` | ❌ W0 | ⬜ pending |
| 11-02-02 | 02 | 2 | QA-03 | — | X-Request-ID header present on every response | unit | `mvn test -Dtest=RequestIdFilterTest -DfailIfNoTests=false` | ❌ W0 | ⬜ pending |

*Status: ⬜ pending · ✅ green · ❌ red · ⚠️ flaky*

---

## Wave 0 Requirements

- [ ] `src/test/java/com/aflokkat/RequestIdFilterTest.java` — stubs for QA-02, QA-03 (UUID generated, MDC set, X-Request-ID header present, client header ignored)
- [ ] `src/test/java/com/aflokkat/LogbackConfigTest.java` — optional: verify logback-spring.xml can be parsed (compilation check)

*Existing `src/test/` infrastructure covers JUnit 4 setup — only new test files needed.*

---

## Manual-Only Verifications

| Behavior | Requirement | Why Manual | Test Instructions |
|----------|-------------|------------|-------------------|
| JSON log lines emitted in prod profile | QA-01 | Requires running app with `--spring.profiles.active=prod` and inspecting stdout | Start app with prod profile; curl any endpoint; verify log line is valid JSON with `requestId` field |
| Plaintext log lines in dev profile | QA-01 | Requires running app without prod profile | Start app normally; curl any endpoint; verify log line contains `[requestId]` pattern |
| X-Request-ID header in HTTP response | QA-03 | Requires live HTTP client | `curl -i http://localhost:8080/api/restaurants/health` and check for `X-Request-ID` header |

---

## Validation Sign-Off

- [ ] All tasks have `<automated>` verify or Wave 0 dependencies
- [ ] Sampling continuity: no 3 consecutive tasks without automated verify
- [ ] Wave 0 covers all MISSING references
- [ ] No watch-mode flags
- [ ] Feedback latency < 30s
- [ ] `nyquist_compliant: true` set in frontmatter

**Approval:** pending
