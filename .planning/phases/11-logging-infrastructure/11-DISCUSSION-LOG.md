# Phase 11: Logging Infrastructure - Discussion Log

> **Audit trail only.** Do not use as input to planning, research, or execution agents.
> Decisions are captured in CONTEXT.md — this log preserves the alternatives considered.

**Date:** 2026-04-11
**Phase:** 11-logging-infrastructure
**Areas discussed:** Client X-Request-ID

---

## Client X-Request-ID

| Option | Description | Selected |
|--------|-------------|----------|
| Always generate fresh UUID | Ignore any client-provided header, always create a server-side UUID. Simplest, prevents log injection, guarantees uniqueness format. | ✓ |
| Accept from client if present | Reuse the client-provided value if the header exists, generate if absent. Useful when a reverse proxy already stamps its own correlation ID. | |
| Accept only from trusted upstream | Only accept X-Request-ID if it comes with a specific trusted-proxy header, otherwise generate. More correct but adds complexity. | |

**User's choice:** Always generate fresh UUID
**Notes:** None provided.

---

## Request ID Filter placement

| Option | Description | Selected |
|--------|-------------|----------|
| Dedicated RequestIdFilter | Clean separation of concerns — unauthenticated requests also get a request ID. Runs before JWT and RateLimit filters. | ✓ |
| Inline in JwtAuthenticationFilter | One fewer class, but anonymous requests would have no MDC context. | |

**User's choice:** Dedicated RequestIdFilter
**Notes:** None provided.

---

## Claude's Discretion

- Dev log pattern (exact format string beyond `[requestId]`) — left to Claude
- Third-party log verbosity (MongoDB, Spring, Hibernate log levels) — left to Claude
- Filter registration order (`@Order` or SecurityConfig placement) — left to Claude
- Logger field naming standardization (`log` vs `logger`) — left to Claude

## Deferred Ideas

None.
