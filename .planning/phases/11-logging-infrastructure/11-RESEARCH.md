# Phase 11: Logging Infrastructure - Research

**Researched:** 2026-04-11
**Domain:** Spring Boot Logback configuration, SLF4J MDC, OncePerRequestFilter
**Confidence:** HIGH

<user_constraints>
## User Constraints (from CONTEXT.md)

### Locked Decisions

- **D-01:** Always generate a fresh server-side UUID — never accept or reuse a client-provided `X-Request-ID` header. Prevents log injection; guarantees UUID format regardless of caller.
- **D-02:** Implement a dedicated `RequestIdFilter extends OncePerRequestFilter` class. Do NOT inline into `JwtAuthenticationFilter`. Unauthenticated requests (Swagger, NYC data sync endpoints, public analytics) must also get a request ID. The filter runs before JWT and RateLimit filters in the chain.
- **D-03:** The filter sets `MDC.put("requestId", uuid)` at entry and calls `MDC.remove("requestId")` in a `finally` block at exit. It also writes the UUID to the `X-Request-ID` response header before the chain continues.
- **D-04:** `logback-spring.xml` (Spring Boot convention — enables Spring profile-switching in XML). Two appender configurations:
  - Profile `prod`: JSON encoder via `logstash-logback-encoder 7.3` (already pinned in STATE.md for Logback 1.2.x compatibility)
  - All other profiles (including no profile): pattern-based plaintext with `[%X{requestId}]` in the pattern
- **D-05:** `simplelogger.properties` is deleted. No fallback.

### Claude's Discretion

- **Dev log pattern**: Exact pattern string for plaintext appender. Suggested baseline: `%d{HH:mm:ss.SSS} %-5level %logger{36} [%X{requestId}] - %msg%n`
- **Third-party log verbosity**: Package-level overrides in `logback-spring.xml`. Suggested: MongoDB driver and Bson at `WARN`, Spring Web/Security at `INFO`, `com.aflokkat` at `DEBUG` in dev / `INFO` in prod. Mirrors what simplelogger.properties was trying to do.
- **Filter registration order**: `@Order` annotation so `RequestIdFilter` runs first (before `RateLimitFilter` and `JwtAuthenticationFilter`).
- **Logger field naming**: Existing classes use both `log` and `logger` — Claude may standardize to `log` if touching those files, but not required.

### Deferred Ideas (OUT OF SCOPE)

None — discussion stayed within phase scope.
</user_constraints>

<phase_requirements>
## Phase Requirements

| ID | Description | Research Support |
|----|-------------|------------------|
| QA-01 | User can see `logback-spring.xml` replacing the non-functional `simplelogger.properties` | `logback-spring.xml` is the Spring Boot convention file; `simplelogger.properties` has zero effect with Logback on classpath. Delete the dead file, create the Logback config. |
| QA-02 | User can see structured JSON log output in production profile (via logstash-logback-encoder 7.3) | logstash-logback-encoder 7.3 is the last version compatible with Logback 1.2.x (Spring Boot 2.6.15 ships 1.2.12). Add Maven dependency, configure `<springProfile name="prod">` appender with `LogstashEncoder`. |
| QA-03 | User can see a request ID (UUID) propagated via MDC and returned as `X-Request-ID` response header | `RequestIdFilter extends OncePerRequestFilter` with `@Component @Order(0)` — servlet-level registration ensures all requests get an ID before Spring Security runs. MDC key `requestId` then renders via `%X{requestId}` in pattern and as a field in JSON encoder. |
</phase_requirements>

---

## Summary

Phase 11 replaces a dead `simplelogger.properties` file (has no effect when Logback is the SLF4J binding, which it is via `spring-boot-starter-logging`) with a proper `logback-spring.xml` that provides two profiles: structured JSON for `prod` and human-readable plaintext for all other environments. A new `RequestIdFilter` generates a UUID per request, stores it in MDC, and returns it as an `X-Request-ID` response header — making logs traceable end-to-end.

The implementation scope is narrow: three new/modified artifacts (one XML config, one Java filter class, one pom.xml dependency addition) plus deletion of one dead properties file. No existing service classes need changes — SLF4J loggers already present in six classes pick up the new configuration automatically. The only SecurityConfig change is ensuring `RequestIdFilter` runs first.

The critical version constraint is `logstash-logback-encoder 7.3`. The library's official README explicitly states: "Support for logback versions prior to 1.3.0 was removed in logstash-logback-encoder 7.4." Spring Boot 2.6.15 ships Logback 1.2.12 (verified from `mvn dependency:tree`). Using 7.4+ would cause a runtime class incompatibility. Version 7.3 is available in Maven Central and is the correct choice.

**Primary recommendation:** Add `logstash-logback-encoder 7.3` to pom.xml, create `src/main/resources/logback-spring.xml` with `<springProfile>` blocks, create `RequestIdFilter` as `@Component @Order(0)`, and delete `simplelogger.properties`.

## Standard Stack

### Core

| Library | Version | Purpose | Why Standard |
|---------|---------|---------|--------------|
| Logback | 1.2.12 | SLF4J binding, actual log output | Already present via `spring-boot-starter-logging`; Spring Boot 2.6.15 manages this version [VERIFIED: mvn dependency:tree] |
| logstash-logback-encoder | 7.3 | JSON encoding of log events (Logstash-compatible) | Last version supporting Logback 1.2.x; 7.4+ drops 1.2.x [VERIFIED: official README github.com/logfellow/logstash-logback-encoder] |
| SLF4J API | 1.7.x | Logger interface used by all existing classes | Transitively included; no separate declaration needed |

### Supporting

| Library | Version | Purpose | When to Use |
|---------|---------|---------|-------------|
| `org.slf4j.MDC` | (SLF4J API) | Thread-local key-value store for request context | Always — standard mechanism for propagating requestId through log output |
| `java.util.UUID` | JDK | Generate per-request unique identifier | `UUID.randomUUID().toString()` — no library needed |

### Alternatives Considered

| Instead of | Could Use | Tradeoff |
|------------|-----------|----------|
| logstash-logback-encoder 7.3 | jackson-logback-encoder or manual JSON layout | No benefit; logstash-logback-encoder is the industry standard for this exact purpose |
| `@Component @Order(0)` servlet filter | Spring Security `addFilterBefore()` | Servlet filter registration reaches ALL requests before Security chain; Security-only registration misses unauthenticated pre-Security traffic |

**Installation:**

```xml
<!-- Add to pom.xml <dependencies> -->
<dependency>
  <groupId>net.logstash.logback</groupId>
  <artifactId>logstash-logback-encoder</artifactId>
  <version>7.3</version>
</dependency>
```

**Version verification:** [VERIFIED: Maven Central] logstash-logback-encoder 7.3 is available. Official README confirms: "Support for logback versions prior to 1.3.0 was removed in logstash-logback-encoder 7.4."

## Architecture Patterns

### Recommended Project Structure

```
src/main/resources/
├── application.properties       # unchanged
├── logback-spring.xml           # NEW — replaces simplelogger.properties
└── simplelogger.properties      # DELETE

src/main/java/com/aflokkat/
├── security/
│   ├── JwtAuthenticationFilter.java   # unchanged
│   ├── RateLimitFilter.java           # unchanged
│   └── RequestIdFilter.java           # NEW @Component @Order(0)
└── config/
    └── SecurityConfig.java            # unchanged (no Security chain changes needed)
```

### Pattern 1: logback-spring.xml Profile-Switched Configuration

**What:** Single Logback XML file using Spring Boot's `<springProfile>` extension to switch between JSON (prod) and plaintext (dev/default) appenders. This is only possible with the filename `logback-spring.xml` — using `logback.xml` bypasses Spring Boot's context and disables `<springProfile>` tags.

**When to use:** Always — this is the Spring Boot standard for profile-aware Logback configuration.

**Example:**

```xml
<!-- src/main/resources/logback-spring.xml -->
<?xml version="1.0" encoding="UTF-8"?>
<configuration>

  <!-- ============================================================ -->
  <!-- Non-prod profile: human-readable plaintext with [requestId] -->
  <!-- ============================================================ -->
  <springProfile name="!prod">
    <appender name="CONSOLE" class="ch.qos.logback.core.ConsoleAppender">
      <encoder>
        <pattern>%d{HH:mm:ss.SSS} %-5level %logger{36} [%X{requestId}] - %msg%n</pattern>
      </encoder>
    </appender>

    <!-- Third-party noise reduction -->
    <logger name="org.mongodb.driver" level="WARN"/>
    <logger name="org.bson" level="WARN"/>
    <logger name="org.springframework.web" level="INFO"/>
    <logger name="org.springframework.security" level="INFO"/>

    <!-- Application code: DEBUG in dev for full visibility -->
    <logger name="com.aflokkat" level="DEBUG"/>

    <root level="INFO">
      <appender-ref ref="CONSOLE"/>
    </root>
  </springProfile>

  <!-- ============================================================ -->
  <!-- prod profile: Logstash-compatible JSON one-line-per-event   -->
  <!-- ============================================================ -->
  <springProfile name="prod">
    <appender name="JSON_CONSOLE" class="ch.qos.logback.core.ConsoleAppender">
      <encoder class="net.logstash.logback.encoder.LogstashEncoder"/>
    </appender>

    <!-- Third-party noise: WARN in prod too (same intent as dev) -->
    <logger name="org.mongodb.driver" level="WARN"/>
    <logger name="org.bson" level="WARN"/>
    <logger name="org.springframework.web" level="INFO"/>
    <logger name="org.springframework.security" level="INFO"/>

    <!-- Application code: INFO in prod to reduce volume -->
    <logger name="com.aflokkat" level="INFO"/>

    <root level="INFO">
      <appender-ref ref="JSON_CONSOLE"/>
    </root>
  </springProfile>

</configuration>
```

[CITED: Spring Boot 2.6 reference docs — Logback configuration / Profile-specific configuration]

### Pattern 2: RequestIdFilter as Servlet-Level Component

**What:** A `OncePerRequestFilter` registered as `@Component @Order(0)` in the Spring servlet container. `Order(0)` means it runs before `RateLimitFilter` (which uses `@Order(1)`) and before the Spring Security `FilterChainProxy`. This is exactly how `RateLimitFilter` is registered — the same pattern applies.

**When to use:** When a cross-cutting concern (like request ID assignment) must apply to every HTTP request, including those that never reach the Spring Security chain.

**Critical detail — double-registration risk:** `JwtAuthenticationFilter` is NOT a `@Component` — it is manually constructed in `SecurityConfig` as a `@Bean` and disabled from servlet registration via a `FilterRegistrationBean` with `setEnabled(false)`. This avoids double-application. For `RequestIdFilter`, since it should run once at the servlet level (not also inside the Security chain), it should be `@Component @Order(0)` ONLY — do NOT also add it to the Security chain via `addFilterBefore()`.

**Example:**

```java
// src/main/java/com/aflokkat/security/RequestIdFilter.java
package com.aflokkat.security;

import org.slf4j.MDC;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import javax.servlet.FilterChain;
import javax.servlet.ServletException;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.util.UUID;

@Component
@Order(0)
public class RequestIdFilter extends OncePerRequestFilter {

    private static final String REQUEST_ID_HEADER = "X-Request-ID";
    private static final String MDC_KEY = "requestId";

    @Override
    protected void doFilterInternal(HttpServletRequest request,
                                    HttpServletResponse response,
                                    FilterChain chain)
            throws ServletException, IOException {
        String requestId = UUID.randomUUID().toString();
        MDC.put(MDC_KEY, requestId);
        response.setHeader(REQUEST_ID_HEADER, requestId);
        try {
            chain.doFilter(request, response);
        } finally {
            MDC.remove(MDC_KEY);
        }
    }
}
```

[ASSUMED] — Pattern derived from training knowledge; the OncePerRequestFilter + MDC pattern is standard SLF4J/Logback practice. The `@Component @Order(0)` registration matches the observed `@Order(1)` on `RateLimitFilter`.

### Pattern 3: MDC Key Rendering in Log Patterns

**What:** `%X{requestId}` in a Logback pattern renders the MDC value keyed `"requestId"`. When the MDC has no value for that key (e.g., application startup logs before any request), the expression renders as an empty string, not "null" or an error.

**When to use:** Always include in the plaintext pattern. The JSON encoder (`LogstashEncoder`) automatically includes all MDC fields as top-level JSON keys — no special pattern syntax needed.

[CITED: Logback documentation — MDC and patterns: logback.qos.ch/manual/mdc.html]

### Anti-Patterns to Avoid

- **Using `logback.xml` instead of `logback-spring.xml`:** The `<springProfile>` tag is a Spring Boot extension that only works when Logback is initialized via Spring Boot's `LoggingSystem`. With `logback.xml`, the `<springProfile>` elements are silently ignored and both appenders activate.
- **Calling `MDC.clear()` instead of `MDC.remove("requestId")`:** `MDC.clear()` wipes all MDC state, including any keys set by downstream components. Use targeted `MDC.remove("requestId")` in the `finally` block.
- **Accepting client `X-Request-ID` header:** Allows log injection if caller supplies a value containing log separators or spoofed UUIDs. Always generate server-side (D-01 locked).
- **Adding `RequestIdFilter` to both the servlet container AND the Spring Security chain:** Causes double MDC put/remove per request. `RateLimitFilter` pattern shows the correct approach: `@Component` only, no Security chain registration.
- **Leaving `simplelogger.properties` on the classpath:** The file has zero effect when Logback is the SLF4J binding (SimpleLogger is a different, standalone SLF4J implementation). The file is not harmful, but leaving it is confusing. D-05 mandates deletion.

## Don't Hand-Roll

| Problem | Don't Build | Use Instead | Why |
|---------|-------------|-------------|-----|
| JSON log formatting | Custom Layout or manual `ObjectMapper` in appender | `LogstashEncoder` from logstash-logback-encoder | Handles nested objects, timestamps, log level, MDC fields, stack traces, message escaping — all correct for Logstash/ELK ingestion |
| Request correlation | Custom ThreadLocal or `HttpSession` attribute | `org.slf4j.MDC` | Standard SLF4J mechanism; works automatically with all SLF4J-compatible loggers; Logback renders it in both pattern and JSON appenders |
| Unique request ID | Custom counter or timestamp-based ID | `UUID.randomUUID().toString()` | Guaranteed globally unique, no clock drift, 128 bits of entropy, standard format |

**Key insight:** The JSON encoder, MDC integration, and UUID generation are solved problems with minimal custom code required. The entire implementation is ~60 lines of Java plus ~50 lines of XML.

## Common Pitfalls

### Pitfall 1: logstash-logback-encoder version drift

**What goes wrong:** If a future developer upgrades to 7.4+, the application fails at startup with `ClassNotFoundException` or `NoSuchMethodError` because Logback 1.2.12 (managed by Spring Boot 2.6.15 BOM) lacks classes required by the encoder.

**Why it happens:** logstash-logback-encoder 7.4 dropped support for Logback 1.2.x. Spring Boot 2.6.15 ships Logback 1.2.12. The BOM overrides the encoder's declared transitive dependency on Logback 1.3.x, causing a runtime version mismatch.

**How to avoid:** Pin `<version>7.3</version>` explicitly in pom.xml. Add a comment: `<!-- 7.4+ requires Logback 1.3.x; Spring Boot 2.6.15 ships 1.2.12 — do not upgrade -->`.

**Warning signs:** Any `ClassNotFoundException` referencing Logback or logstash-logback-encoder classes at startup.

### Pitfall 2: springProfile tags silently ignored

**What goes wrong:** Both appenders (JSON and plaintext) are active simultaneously — or neither is — depending on the Logback version behavior when the file is named `logback.xml`.

**Why it happens:** `<springProfile>` is injected by Spring Boot's `LoggingApplicationListener` only when it processes `logback-spring.xml`. The standard Logback JoranConfigurator does not understand this tag.

**How to avoid:** File MUST be named `logback-spring.xml` (not `logback.xml`). Verify by starting with `--spring.profiles.active=prod` and checking that output is JSON.

**Warning signs:** Log output is plaintext even when prod profile is active, or JSON appender writes plaintext fallback.

### Pitfall 3: MDC not cleared on async or error dispatch

**What goes wrong:** A subsequent request on the same thread sees a stale `requestId` from the previous request if the `finally` block was skipped due to an `AsyncContext` dispatch or error forward.

**Why it happens:** `OncePerRequestFilter` calls `doFilterInternal` once per dispatch type by default. On async dispatches, the filter may not re-enter if `shouldNotFilterAsyncDispatch()` is not overridden.

**How to avoid:** The `finally { MDC.remove("requestId"); }` pattern ensures cleanup even on exception paths. For this project (no async endpoints), the default `shouldNotFilterAsyncDispatch()` returning `true` (filter skips async re-dispatch) is correct behavior. Document this assumption.

**Warning signs:** Logs for one request showing another request's UUID.

### Pitfall 4: Double-registration of RequestIdFilter

**What goes wrong:** Every request gets the MDC set and cleared twice (or the header set twice), causing confusing log output.

**Why it happens:** `@Component` registers the filter in the servlet container. If the same bean is also added via `SecurityConfig.addFilterBefore()`, Spring Security's chain calls it a second time inside the container's chain.

**How to avoid:** Use `@Component @Order(0)` only. Do NOT add to the Security chain. Follow the exact same pattern as `RateLimitFilter`.

**Warning signs:** Each request produces two `X-Request-ID` headers (browsers see first value only, but it indicates double execution).

### Pitfall 5: simplelogger.properties removal timing

**What goes wrong:** Deleted too early (before `logback-spring.xml` is functional), leaving no logging configuration — Spring Boot falls back to a hardcoded default which suppresses debug logs.

**How to avoid:** The plan should: (1) add `logstash-logback-encoder` dependency, (2) create `logback-spring.xml`, (3) verify application starts and logs correctly, (4) then delete `simplelogger.properties` as the final step.

**Warning signs:** After deletion, application produces no log output or only root-level WARN.

## Code Examples

### RequestIdFilter complete implementation

```java
// Source: derived from RateLimitFilter pattern in this codebase + SLF4J MDC standard
package com.aflokkat.security;

import org.slf4j.MDC;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import javax.servlet.FilterChain;
import javax.servlet.ServletException;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.util.UUID;

/**
 * Generates a server-side UUID per request, stores it in MDC as "requestId",
 * and returns it as the X-Request-ID response header.
 *
 * Registered as a servlet filter at Order(0) — runs before RateLimitFilter (Order 1)
 * and before the Spring Security FilterChainProxy. Do NOT also register via SecurityConfig.
 *
 * Client-supplied X-Request-ID headers are intentionally ignored (D-01).
 */
@Component
@Order(0)
public class RequestIdFilter extends OncePerRequestFilter {

    private static final String REQUEST_ID_HEADER = "X-Request-ID";
    private static final String MDC_KEY = "requestId";

    @Override
    protected void doFilterInternal(HttpServletRequest request,
                                    HttpServletResponse response,
                                    FilterChain chain)
            throws ServletException, IOException {
        String requestId = UUID.randomUUID().toString();
        MDC.put(MDC_KEY, requestId);
        response.setHeader(REQUEST_ID_HEADER, requestId);
        try {
            chain.doFilter(request, response);
        } finally {
            MDC.remove(MDC_KEY);
        }
    }
}
```

### logback-spring.xml complete configuration

```xml
<!-- Source: Spring Boot 2.6 Logback profile-switching convention -->
<?xml version="1.0" encoding="UTF-8"?>
<configuration>

  <!-- Non-prod: human-readable plaintext with [requestId] -->
  <springProfile name="!prod">
    <appender name="CONSOLE" class="ch.qos.logback.core.ConsoleAppender">
      <encoder>
        <pattern>%d{HH:mm:ss.SSS} %-5level %logger{36} [%X{requestId}] - %msg%n</pattern>
      </encoder>
    </appender>

    <logger name="org.mongodb.driver" level="WARN"/>
    <logger name="org.bson" level="WARN"/>
    <logger name="org.springframework.web" level="INFO"/>
    <logger name="org.springframework.security" level="INFO"/>
    <logger name="com.aflokkat" level="DEBUG"/>

    <root level="INFO">
      <appender-ref ref="CONSOLE"/>
    </root>
  </springProfile>

  <!-- prod: Logstash-compatible JSON (one JSON object per line) -->
  <!-- logstash-logback-encoder 7.3 — do NOT upgrade; 7.4+ requires Logback 1.3.x -->
  <!-- Spring Boot 2.6.15 ships Logback 1.2.12 -->
  <springProfile name="prod">
    <appender name="JSON_CONSOLE" class="ch.qos.logback.core.ConsoleAppender">
      <encoder class="net.logstash.logback.encoder.LogstashEncoder"/>
    </appender>

    <logger name="org.mongodb.driver" level="WARN"/>
    <logger name="org.bson" level="WARN"/>
    <logger name="org.springframework.web" level="INFO"/>
    <logger name="org.springframework.security" level="INFO"/>
    <logger name="com.aflokkat" level="INFO"/>

    <root level="INFO">
      <appender-ref ref="JSON_CONSOLE"/>
    </root>
  </springProfile>

</configuration>
```

### Unit test pattern for RequestIdFilter

```java
// Source: follows RateLimitFilterTest pattern already in this codebase
package com.aflokkat.security;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.slf4j.MDC;
import org.springframework.mock.web.MockFilterChain;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

import static org.junit.jupiter.api.Assertions.*;

class RequestIdFilterTest {

    private RequestIdFilter filter;

    @BeforeEach
    void setUp() {
        filter = new RequestIdFilter();
    }

    @Test
    void filter_setsXRequestIdHeader() throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/api/restaurants/stats");
        MockHttpServletResponse response = new MockHttpServletResponse();
        MockFilterChain chain = new MockFilterChain();

        filter.doFilterInternal(request, response, chain);

        assertNotNull(response.getHeader("X-Request-ID"), "X-Request-ID header must be set");
    }

    @Test
    void filter_headerIsValidUuid() throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/api/restaurants/stats");
        MockHttpServletResponse response = new MockHttpServletResponse();
        MockFilterChain chain = new MockFilterChain();

        filter.doFilterInternal(request, response, chain);

        String header = response.getHeader("X-Request-ID");
        // UUID format: 8-4-4-4-12 hex digits
        assertTrue(header.matches("[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}"),
                "X-Request-ID must be a valid UUID");
    }

    @Test
    void filter_ignoresClientSuppliedRequestId() throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/api/restaurants/stats");
        request.addHeader("X-Request-ID", "attacker-controlled-value");
        MockHttpServletResponse response = new MockHttpServletResponse();
        MockFilterChain chain = new MockFilterChain();

        filter.doFilterInternal(request, response, chain);

        assertNotEquals("attacker-controlled-value", response.getHeader("X-Request-ID"),
                "Server must generate a new UUID, not reuse client value");
    }

    @Test
    void filter_clearsMdcAfterRequest() throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/api/restaurants/stats");
        MockHttpServletResponse response = new MockHttpServletResponse();
        MockFilterChain chain = new MockFilterChain();

        filter.doFilterInternal(request, response, chain);

        assertNull(MDC.get("requestId"), "MDC requestId must be cleared after filter");
    }

    @Test
    void filter_generatesUniqueIdsPerRequest() throws Exception {
        MockHttpServletRequest req1 = new MockHttpServletRequest("GET", "/api/restaurants/stats");
        MockHttpServletResponse resp1 = new MockHttpServletResponse();
        filter.doFilterInternal(req1, resp1, new MockFilterChain());

        MockHttpServletRequest req2 = new MockHttpServletRequest("GET", "/api/restaurants/stats");
        MockHttpServletResponse resp2 = new MockHttpServletResponse();
        filter.doFilterInternal(req2, resp2, new MockFilterChain());

        assertNotEquals(resp1.getHeader("X-Request-ID"), resp2.getHeader("X-Request-ID"),
                "Each request must get a distinct UUID");
    }
}
```

## State of the Art

| Old Approach | Current Approach | When Changed | Impact |
|--------------|------------------|--------------|--------|
| `simplelogger.properties` | `logback-spring.xml` | Present — simplelogger.properties was never valid here | simplelogger.properties configures the SLF4J SimpleLogger binding, which is NOT on the classpath — Spring Boot uses Logback. The file does nothing. |
| Plaintext-only logging | Profile-switched JSON/plaintext | Spring Boot 2.x introduced `<springProfile>` | Log aggregators (ELK, Datadog, Loki) require structured JSON; dev humans require readable plaintext |
| No request correlation | MDC + X-Request-ID | Standard for years; adoption in this project now | Enables "find all logs for request ABC-123" in both dev and prod |

**Deprecated/outdated:**

- `simplelogger.properties`: Only configures `org.slf4j.simple.SimpleLogger` — an alternative SLF4J binding not present in this project. The file is inert and must be deleted (D-05).
- `logging.level.*` in `application.properties`: These still work and override Logback XML settings when present. The existing `logging.level.root=INFO` and `logging.level.com.aflokkat=DEBUG` in `application.properties` may conflict with `logback-spring.xml` logger declarations. Research recommendation: remove the `logging.level.*` properties from `application.properties` once `logback-spring.xml` takes over — or leave them as compatible overrides (they produce the same effect).

## Runtime State Inventory

> This is a config/code-only phase with no renamed entities and no stored state to migrate.

| Category | Items Found | Action Required |
|----------|-------------|------------------|
| Stored data | None — no log data stored in DB | None |
| Live service config | None — logging config is file-based, not in UI/DB | None |
| OS-registered state | None | None |
| Secrets/env vars | None | None |
| Build artifacts | `simplelogger.properties` — inert dead file | Delete as part of phase |

## Assumptions Log

| # | Claim | Section | Risk if Wrong |
|---|-------|---------|---------------|
| A1 | `RequestIdFilter` as `@Component @Order(0)` servlet filter (not Security chain) is the correct registration pattern — follows `RateLimitFilter` at `@Order(1)` | Architecture Patterns — Pattern 2 | If wrong: double-execution or Security chain bypasses filter for some requests. Low risk — pattern is identical to existing `RateLimitFilter`. |
| A2 | `logging.level.*` entries in `application.properties` can coexist with `logback-spring.xml` logger declarations (Spring Boot merges them) | State of the Art | If wrong: conflicting logger levels cause unexpected verbosity. Low risk — Spring Boot behavior for this is well-documented and unchanged in 2.6.x. |
| A3 | `LogstashEncoder` (default configuration, no additional options) produces output compatible with Logstash/ELK ingestion without custom fields | Code Examples — logback-spring.xml | If wrong: log aggregator may require field name remapping. Low risk for portfolio scope. |

## Open Questions

1. **`logging.level.*` in `application.properties` vs `logback-spring.xml` logger declarations**
   - What we know: Both mechanisms work. Spring Boot applies `application.properties` logging overrides after processing `logback-spring.xml`.
   - What's unclear: Whether to remove `logging.level.root=INFO` and `logging.level.com.aflokkat=DEBUG` from `application.properties` once `logback-spring.xml` is in place.
   - Recommendation: Leave the `application.properties` entries in place for Phase 11 — they are redundant but harmless, and removing them is a QA-04 cleanup concern (Phase 17), not a Phase 11 concern.

2. **`X-Request-ID` header visibility when response is committed before filter returns**
   - What we know: `response.setHeader()` must be called before the response is committed (first byte written). The filter sets it before `chain.doFilter()` per D-03, which is correct.
   - What's unclear: Whether any streaming responses in the project could commit the response before the filter gets to set the header — unlikely since all controllers return `ResponseEntity` or JSON.
   - Recommendation: No special handling needed. Standard pattern.

## Environment Availability

| Dependency | Required By | Available | Version | Fallback |
|------------|------------|-----------|---------|---------|
| Maven | Build | ✓ | 3.8.7 [VERIFIED: mvn --version] | — |
| Java | Build/Run | ✓ | 25.0.2 [VERIFIED: java -version] | — |
| logstash-logback-encoder 7.3 | QA-02 | ✓ | 7.3 (Maven Central) [VERIFIED: maven central repo1] | — |

**Missing dependencies with no fallback:** None.

**Missing dependencies with fallback:** None.

## Validation Architecture

### Test Framework

| Property | Value |
|----------|-------|
| Framework | JUnit 4 (vintage) + JUnit 5 Jupiter — both active via junit-vintage-engine |
| Config file | `pom.xml` surefire plugin — `-XX:+EnableDynamicAgentLoading` argLine already configured |
| Quick run command | `mvn test -Dtest=RequestIdFilterTest -pl . -q` |
| Full suite command | `mvn test` |

### Phase Requirements → Test Map

| Req ID | Behavior | Test Type | Automated Command | File Exists? |
|--------|----------|-----------|-------------------|-------------|
| QA-01 | `logback-spring.xml` present, `simplelogger.properties` absent | Integration smoke | `mvn spring-boot:run` starts without errors; check `src/main/resources/` | ❌ Wave 0 (manual verification during implementation) |
| QA-02 | Prod profile produces JSON output (LogstashEncoder active) | Manual smoke | Start with `--spring.profiles.active=prod`, verify first log line parses as JSON | ❌ Manual only — no automated JSON format assertion in unit tests |
| QA-03 | Every HTTP response has `X-Request-ID` UUID header matching MDC | Unit | `mvn test -Dtest=RequestIdFilterTest` | ❌ Wave 0 |

### Sampling Rate

- **Per task commit:** `mvn test -Dtest=RequestIdFilterTest -q`
- **Per wave merge:** `mvn test`
- **Phase gate:** Full suite green (`mvn test`) before `/gsd-verify-work`

### Wave 0 Gaps

- [ ] `src/test/java/com/aflokkat/security/RequestIdFilterTest.java` — covers QA-03 (UUID generation, header setting, MDC cleanup, client-value ignored, uniqueness per request)
- [ ] No conftest/fixture gaps — test uses `MockHttpServletRequest` / `MockHttpServletResponse` from `spring-test`, already in pom.xml

*(QA-01 and QA-02 are verified by manual inspection / startup smoke test — not automated unit tests)*

## Security Domain

### Applicable ASVS Categories

| ASVS Category | Applies | Standard Control |
|---------------|---------|-----------------|
| V2 Authentication | no | Not modified |
| V3 Session Management | no | Stateless JWT unchanged |
| V4 Access Control | no | Filter runs before Security; adds header only |
| V5 Input Validation | yes | Client `X-Request-ID` header explicitly ignored (D-01) — prevents log injection via header value |
| V6 Cryptography | no | UUID is not a secret; no crypto required |
| V7 Error Handling and Logging | yes | This entire phase; MDC provides non-repudiation of request traces |

### Known Threat Patterns for Logging Infrastructure

| Pattern | STRIDE | Standard Mitigation |
|---------|--------|---------------------|
| Log injection via client-supplied request ID | Tampering | D-01: always generate server-side UUID; never accept `X-Request-ID` from client |
| Sensitive data leakage in log fields | Information Disclosure | LogstashEncoder logs all MDC fields — do not put sensitive values in MDC (passwords, tokens) |
| MDC leak between requests (thread pool reuse) | Spoofing | `finally { MDC.remove("requestId"); }` — mandatory cleanup ensures no cross-request contamination |

## Sources

### Primary (HIGH confidence)

- [VERIFIED: Maven Central repo1.maven.org] — logstash-logback-encoder 7.3 POM retrieved; logback-classic dependency confirmed
- [VERIFIED: official README github.com/logfellow/logstash-logback-encoder] — "Support for logback versions prior to 1.3.0 was removed in logstash-logback-encoder 7.4"
- [VERIFIED: mvn dependency:tree] — Logback 1.2.12 confirmed in project
- [VERIFIED: codebase grep] — `RateLimitFilter.java` `@Component @Order(1)`, `SecurityConfig.java` `FilterRegistrationBean` pattern, `JwtAuthenticationFilter.java` structure
- [CITED: Spring Boot 2.6 reference] — `logback-spring.xml` naming requirement for `<springProfile>` support

### Secondary (MEDIUM confidence)

- [CITED: logback documentation logback.qos.ch] — MDC `%X{key}` pattern syntax, `MDC.put`/`MDC.remove` contract

### Tertiary (LOW confidence)

- None

## Metadata

**Confidence breakdown:**

- Standard stack: HIGH — Logback 1.2.12 verified from mvn; logstash 7.3 pin verified from official README
- Architecture: HIGH — filter registration pattern verified from existing codebase; logback-spring.xml profile switching is Spring Boot convention
- Pitfalls: HIGH — version compatibility pitfall verified by official README; profile-switching pitfall is documented Spring Boot behavior

**Research date:** 2026-04-11
**Valid until:** 2026-07-11 (stable — Spring Boot 2.6.15 is EOL/pinned; no churn expected)
