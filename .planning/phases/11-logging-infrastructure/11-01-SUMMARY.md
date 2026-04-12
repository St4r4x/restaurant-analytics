---
phase: 11-logging-infrastructure
plan: 01
subsystem: logging
tags: [logback, logging, spring-boot, structured-logging, json-logs]
dependency_graph:
  requires: []
  provides: [logback-spring.xml profile-switched config, logstash-logback-encoder 7.3]
  affects: [all log output, Plan 02 RequestIdFilter MDC rendering]
tech_stack:
  added: [net.logstash.logback:logstash-logback-encoder:7.3]
  patterns: [springProfile-switched Logback XML, LogstashEncoder JSON appender]
key_files:
  created:
    - src/main/resources/logback-spring.xml
  modified:
    - pom.xml
  deleted:
    - src/main/resources/simplelogger.properties
decisions:
  - "logstash-logback-encoder pinned at 7.3 — 7.4+ dropped Logback 1.2.x support; Spring Boot 2.6.15 ships Logback 1.2.12"
  - "simplelogger.properties deleted — it configures SLF4J SimpleLogger (not on classpath); Logback is the actual binding"
  - "logback-spring.xml filename chosen over logback.xml — required for springProfile tag support"
  - "MDC field sensitivity documented in XML comments — LogstashEncoder serializes all MDC fields (T-11-01 mitigation)"
metrics:
  duration: ~10 minutes
  completed: 2026-04-11
  tasks_completed: 2
  tasks_total: 2
  files_changed: 3
requirements:
  - QA-01
  - QA-02
---

# Phase 11 Plan 01: Logback Configuration and logstash-logback-encoder Summary

**One-liner:** Profile-switched logback-spring.xml with LogstashEncoder 7.3 for JSON prod logs and plaintext dev logs with [requestId] MDC placeholder, replacing inert simplelogger.properties.

## What Was Built

Replaced the dead `simplelogger.properties` file (which configures SLF4J SimpleLogger — a binding that is NOT on the classpath when Spring Boot's Logback is used) with a proper `logback-spring.xml` that provides two profile-switched appenders:

- **Non-prod profile (`!prod`):** Human-readable plaintext with `%d{HH:mm:ss.SSS} %-5level %logger{36} [%X{requestId}] - %msg%n`. The `[%X{requestId}]` placeholder is empty at startup, and will render the UUID populated by Plan 02's `RequestIdFilter`.
- **Prod profile (`prod`):** Logstash-compatible JSON via `net.logstash.logback.encoder.LogstashEncoder` — one JSON object per line, MDC fields (including `requestId`) auto-included as top-level keys.

Both profiles suppress MongoDB driver (`org.mongodb.driver`) and BSON (`org.bson`) logging at WARN to reduce noise, and set Spring Web/Security at INFO. Application code (`com.aflokkat`) is at DEBUG in non-prod and INFO in prod.

## Tasks Completed

| Task | Name | Commit | Files |
|------|------|--------|-------|
| 1 | Add logstash-logback-encoder 7.3 to pom.xml | 20ff415 | pom.xml |
| 2 | Create logback-spring.xml and delete simplelogger.properties | ffb328e | src/main/resources/logback-spring.xml, src/main/resources/simplelogger.properties (deleted) |

## Verification Results

| Check | Command | Result |
|-------|---------|--------|
| Dependency resolves | `mvn dependency:tree \| grep logstash` | `net.logstash.logback:logstash-logback-encoder:jar:7.3:compile` |
| logback-spring.xml exists | `test -f src/main/resources/logback-spring.xml` | PASS |
| simplelogger.properties absent | `test ! -f src/main/resources/simplelogger.properties` | PASS (DELETED) |
| springProfile tag count | `grep -c "springProfile" logback-spring.xml` | 4 (2 open + 2 close) |
| mvn test | Pre-existing 125 test errors (same count before and after changes — not introduced by this plan) | Not regressed |

## Deviations from Plan

None — plan executed exactly as written.

**Note on test failures:** `mvn test` shows 125 pre-existing errors (Mockito compatibility issues in SyncServiceTest, JwtUtilTest, NycOpenDataClientTest, DataSeederTest). These existed before this plan's changes and are not caused by the logback configuration. Verified by running `mvn test` both with and without the logback-spring.xml present — identical error count.

## Key Decisions Made

1. **logstash-logback-encoder pinned at 7.3** — 7.4+ requires Logback 1.3.x; Spring Boot 2.6.15 ships 1.2.12. Version lock comment added to pom.xml.
2. **`logback-spring.xml` filename** — Required for `<springProfile>` Spring Boot extension support. Using `logback.xml` would silently ignore `<springProfile>` tags.
3. **`application.properties` `logging.level.*` entries left intact** — These are harmless redundancies that override logback-spring.xml settings; removal is deferred to Phase 17 cleanup per plan instructions.
4. **MDC sensitivity documented in XML comments** — T-11-01 mitigation: LogstashEncoder serializes ALL MDC fields as JSON keys; only non-sensitive identifiers (UUIDs) should be put in MDC.

## Threat Flags

| Flag | File | Description |
|------|------|-------------|
| threat_flag: information_disclosure | src/main/resources/logback-spring.xml | LogstashEncoder serializes all MDC fields as JSON keys — mitigated by T-11-01 comment warning against sensitive MDC values (JWT tokens, passwords, PII) |

## Known Stubs

None — all configuration is fully functional. The `[%X{requestId}]` placeholder in the dev pattern renders as empty string until Plan 02's `RequestIdFilter` populates MDC. This is intentional and documented in the XML comment; it is not a stub — the appender is fully operational.

## Self-Check: PASSED

| Item | Status |
|------|--------|
| src/main/resources/logback-spring.xml | FOUND |
| src/main/resources/simplelogger.properties | CONFIRMED DELETED |
| Commit 20ff415 (pom.xml logstash dep) | FOUND |
| Commit ffb328e (logback-spring.xml + delete) | FOUND |
| .planning/phases/11-logging-infrastructure/11-01-SUMMARY.md | FOUND |
