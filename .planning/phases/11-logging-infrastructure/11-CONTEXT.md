# Phase 11: Logging Infrastructure - Context

**Gathered:** 2026-04-11
**Status:** Ready for planning

<domain>
## Phase Boundary

Replace the non-functional `simplelogger.properties` with a Logback configuration that:
- Produces Logstash-compatible JSON log lines when `--spring.profiles.active=prod`
- Produces human-readable plaintext log lines (with `[requestId]`) in all other profiles
- Propagates a server-generated UUID request ID via MDC through all service layers
- Returns that request ID as an `X-Request-ID` response header on every HTTP response
- Deletes `simplelogger.properties` entirely

New capabilities (rate limiting extensions, security headers, etc.) belong in later phases.

</domain>

<decisions>
## Implementation Decisions

### Request ID Generation
- **D-01:** Always generate a fresh server-side UUID — never accept or reuse a client-provided `X-Request-ID` header. Prevents log injection; guarantees UUID format regardless of caller.

### Request ID Filter
- **D-02:** Implement a dedicated `RequestIdFilter extends OncePerRequestFilter` class. Do NOT inline into `JwtAuthenticationFilter`. Unauthenticated requests (Swagger, NYC data sync endpoints, public analytics) must also get a request ID. The filter runs before JWT and RateLimit filters in the chain.
- **D-03:** The filter sets `MDC.put("requestId", uuid)` at entry and calls `MDC.remove("requestId")` in a `finally` block at exit. It also writes the UUID to the `X-Request-ID` response header before the chain continues.

### Logback Profiles
- **D-04:** `logback-spring.xml` (Spring Boot convention — enables Spring profile-switching in XML). Two appender configurations:
  - Profile `prod`: JSON encoder via `logstash-logback-encoder 7.3` (already pinned in STATE.md for Logback 1.2.x compatibility)
  - All other profiles (including no profile): pattern-based plaintext with `[%X{requestId}]` in the pattern
- **D-05:** `simplelogger.properties` is deleted. No fallback.

### Claude's Discretion
- **Dev log pattern**: Claude decides the exact pattern string for plaintext appender. Suggested baseline (mirrors simplelogger.properties preferences — no thread name, short logger name): `%d{HH:mm:ss.SSS} %-5level %logger{36} [%X{requestId}] - %msg%n`
- **Third-party log verbosity**: Claude decides package-level overrides in `logback-spring.xml`. Suggested: MongoDB driver and Bson at `WARN`, Spring Web/Security at `INFO`, `com.aflokkat` at `DEBUG` in dev / `INFO` in prod. Mirrors what simplelogger.properties was trying to do.
- **Filter registration order**: Claude decides the `@Order` annotation or registration order so `RequestIdFilter` runs first (before `RateLimitFilter` and `JwtAuthenticationFilter`).
- **Logger field naming**: Existing classes use both `log` and `logger` field names — Claude may standardize to `log` (Lombok `@Slf4j` convention) if touching those files, but not required.

</decisions>

<canonical_refs>
## Canonical References

**Downstream agents MUST read these before planning or implementing.**

### Requirements
- `.planning/REQUIREMENTS.md` §Code Quality — QA-01, QA-02, QA-03 (the three requirements this phase satisfies)

### Phase success criteria
- `.planning/ROADMAP.md` §Phase 11 — Four success criteria define the exact acceptance bar (JSON prod, plaintext dev with requestId, X-Request-ID header, simplelogger.properties deleted)

### Dependency pinning
- `.planning/STATE.md` §Accumulated Context / Decisions — `logstash-logback-encoder 7.3` pin (7.4+ drops Logback 1.2.x support), other v3.0 dependency decisions

### Existing codebase integration points
- `src/main/java/com/aflokkat/security/JwtAuthenticationFilter.java` — existing `OncePerRequestFilter`; new `RequestIdFilter` must run before it
- `src/main/java/com/aflokkat/security/RateLimitFilter.java` — second filter in chain; `RequestIdFilter` must precede it
- `src/main/resources/simplelogger.properties` — the file to delete
- `pom.xml` — `spring-boot-starter-logging` already present; `logstash-logback-encoder 7.3` dependency to add

No external ADRs — all decisions captured above.

</canonical_refs>

<code_context>
## Existing Code Insights

### Reusable Assets
- `JwtAuthenticationFilter` (`src/main/java/com/aflokkat/security/JwtAuthenticationFilter.java`): Pattern for `OncePerRequestFilter` implementation in this project — new `RequestIdFilter` follows the same structure
- `RateLimitFilter` (`src/main/java/com/aflokkat/security/RateLimitFilter.java`): Second filter in chain; shows how filters are ordered/registered in `SecurityConfig`
- SLF4J loggers already wired in 6 classes (`RestaurantService`, `RestaurantCacheService`, `DataSeeder`, `NycOpenDataClient`, `SyncService`, `RestaurantDAOImpl`) — they will automatically pick up the new Logback config with no code changes

### Established Patterns
- `spring-boot-starter-logging` in pom.xml — Logback is already the SLF4J binding; no binding conflict to resolve
- Spring profile switching convention: `logback-spring.xml` (not `logback.xml`) is the correct filename for Spring's profile-aware processing via `<springProfile>` tags
- Filter registration: filters are registered in `SecurityConfig` as Spring beans; new `RequestIdFilter` follows same pattern

### Integration Points
- `SecurityConfig`: filter chain configuration — add `RequestIdFilter` before `RateLimitFilter` and `JwtAuthenticationFilter`
- `src/main/resources/`: add `logback-spring.xml` here alongside (then delete) `simplelogger.properties`

</code_context>

<specifics>
## Specific Ideas

No specific visual or behavioral references beyond the requirements. The implementation is primarily infrastructure.

</specifics>

<deferred>
## Deferred Ideas

None — discussion stayed within phase scope.

</deferred>

---

*Phase: 11-logging-infrastructure*
*Context gathered: 2026-04-11*
