# Phase 16: Security Hardening - Discussion Log

> **Audit trail only.** Do not use as input to planning, research, or execution agents.
> Decisions are captured in CONTEXT.md — this log preserves the alternatives considered.

**Date:** 2026-04-13
**Phase:** 16-security-hardening
**Areas discussed:** CORS allowed origins, Security headers depth, Input validation constraints, Restaurant rate limit threshold

---

## CORS Allowed Origins

| Option | Description | Selected |
|--------|-------------|----------|
| localhost:8080 only | Only the app's own origin. Tight policy — demonstrates CORS understanding. | ✓ |
| localhost:* (any port) | Allow any localhost port — handy if dev frontend runs on :3000 or :5173. | |
| Env-var configurable | ALLOWED_ORIGINS env var, default localhost:8080. Clean for portfolio but adds config entry. | |

**User's choice:** localhost:8080 only

---

## CORS Allowed Methods

| Option | Description | Selected |
|--------|-------------|----------|
| GET, POST, PUT, DELETE, OPTIONS | Covers all API methods + OPTIONS for preflight. | ✓ |
| GET, POST, OPTIONS only | Minimal — but PUT is used by controller report edit, so this would break CORS for that. | |

**User's choice:** GET, POST, PUT, DELETE, OPTIONS

---

## Security Headers Depth

| Option | Description | Selected |
|--------|-------------|----------|
| Just the 2 required | X-Content-Type-Options + X-Frame-Options only. Exactly what requirements spec. | ✓ |
| Add Referrer-Policy too | Also add Referrer-Policy: strict-origin-when-cross-origin. Low risk, no HTTPS required. | |
| Full suite | X-Content-Type-Options, X-Frame-Options, Referrer-Policy, X-XSS-Protection, CSP. CSP requires tuning for inline JS. | |

**User's choice:** Just the 2 required

---

## Input Validation — Password Strictness

| Option | Description | Selected |
|--------|-------------|----------|
| @NotBlank only | Matches success criterion exactly. Non-empty = valid. | ✓ |
| @NotBlank + @Size(min=8) | Reject passwords shorter than 8 chars. Not in requirements. | |
| @NotBlank + @Size(min=8, max=255) | Floor and ceiling. Most thorough. | |

**User's choice:** @NotBlank only

---

## Input Validation — Email

| Option | Description | Selected |
|--------|-------------|----------|
| @Email + @NotBlank | Rejects clearly malformed emails. SEC-03 mentions @Email. | ✓ |
| @NotBlank only | Just check it's non-empty. Looser but simpler. | |

**User's choice:** @Email + @NotBlank

---

## Input Validation — Scope

| Option | Description | Selected |
|--------|-------------|----------|
| Auth DTOs only | AuthRequest, RegisterRequest, RefreshRequest. Attack surface scope. | ✓ |
| All RequestBody DTOs | Include ReportRequest too. More complete but slightly out of scope. | |

**User's choice:** Auth DTOs only (no ReportRequest)

---

## Restaurant Rate Limit Threshold

| Option | Description | Selected |
|--------|-------------|----------|
| 100 req/min | 10× the auth limit. Prevents scraping without blocking normal browsing. | ✓ |
| 60 req/min | 1 request/second ceiling. Still generous for normal use. | |
| Configurable via property | Add restaurant.rate-limit.* properties. Consistent with existing auth rate-limit pattern. | |

**User's choice:** 100 req/min (also adopted the configurable-via-property approach for implementation consistency)

---

## Restaurant Rate Limit Implementation

| Option | Description | Selected |
|--------|-------------|----------|
| Same filter, second bucket map | One RateLimitFilter handles both paths. Keeps logic co-located. | ✓ |
| Separate RestaurantRateLimitFilter | New filter class. Easier independent testing but duplicates bucket logic. | |

**User's choice:** Same filter, second bucket map

---

## Claude's Discretion

- Security headers implementation: Spring Security `headers()` DSL in `SecurityConfig`
- `@RestControllerAdvice` class location: `com.aflokkat.controller` package
- Validation error message format: concatenated field errors from `getBindingResult()`
- CORS allowedHeaders: `Authorization`, `Content-Type`

## Deferred Ideas

None — discussion stayed within phase scope.
