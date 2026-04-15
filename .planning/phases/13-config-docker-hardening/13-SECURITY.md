---
phase: 13
slug: config-docker-hardening
status: verified
threats_open: 0
asvs_level: 2
created: 2026-04-12
---

# Phase 13 — Security

> Per-phase security contract: threat register, accepted risks, and audit trail.

---

## Trust Boundaries

| Boundary | Description | Data Crossing |
|----------|-------------|---------------|
| env → AppConfig | JWT_SECRET and signup codes cross the OS/container boundary into the JVM via System.getenv() and dotenv | Sensitive credentials (JWT signing key, DB password) |
| .env file → developer machine | Secrets in .env are visible to any process with filesystem read access | Plaintext secrets at rest |
| .env file → docker compose → container env | JWT_SECRET, POSTGRES_PASSWORD travel from .env into container environment at `docker compose up` | Sensitive credentials injected into container |
| docker-compose.yml → git repository | This file is tracked; any hardcoded value becomes permanent git history | Public-facing secret exposure risk |
| container user → host | App container running as root can escape to host if container escape is exploited | OS-level privilege |
| build context → Docker daemon | Files sent to Docker daemon during build; sensitive files in context baked into image layers | Secrets, git history, internal planning |

---

## Threat Register

| Threat ID | Category | Component | Disposition | Mitigation | Status |
|-----------|----------|-----------|-------------|------------|--------|
| T-13-01 | Information Disclosure | `application.properties` — jwt.secret hardcoded | mitigate | Line removed; comment points to JWT_SECRET env var | closed |
| T-13-02 | Elevation of Privilege | `AppConfig.getJwtSecret()` — default fallback allowed startup with known weak key | mitigate | `IllegalStateException` thrown when JWT_SECRET absent or < 32 chars (`AppConfig.java:82`) | closed |
| T-13-03 | Information Disclosure | `.env.example` committed to repository | accept | See Accepted Risks Log | closed |
| T-13-04 | Information Disclosure | `.env` file with real secrets on developer filesystem | mitigate | `.gitignore` lines 16-17: `.env` and `.env.*`; `!.env.example` negation | closed |
| T-13-05 | Elevation of Privilege | JWT signing key length < 32 chars accepted silently | mitigate | `AppConfig.java:81` — `secret.length() < 32` guard enforces 256-bit minimum | closed |
| T-13-06 | Elevation of Privilege | App container running as root (no USER directive) | mitigate | `Dockerfile:21` — `addgroup/adduser appuser`; `Dockerfile:35` — `USER appuser` before ENTRYPOINT | closed |
| T-13-07 | Information Disclosure | Docker build context includes `.env`, `.git/`, `.planning/` | mitigate | `.dockerignore` excludes `.env`, `.env.*`, `.git/`, `.planning/` | closed |
| T-13-08 | Information Disclosure | Java 21 runtime — UnsupportedClassVersionError exposes stack trace with version info | mitigate | `Dockerfile:1` — `maven:3.9-eclipse-temurin-25`; `Dockerfile:16` — `eclipse-temurin:25-jre-alpine` | closed |
| T-13-09 | Information Disclosure | `docker-compose.yml` — `CONTROLLER_SIGNUP_CODE: changeme` hardcoded | mitigate | `docker-compose.yml:18` — `${CONTROLLER_SIGNUP_CODE:-}`; zero `changeme` literals | closed |
| T-13-10 | Information Disclosure | `docker-compose.yml` — `POSTGRES_PASSWORD: restaurant` hardcoded | mitigate | `docker-compose.yml:97` — `${POSTGRES_PASSWORD}` (required, no default) | closed |
| T-13-11 | Denial of Service | Containers with no memory limit can OOM all sibling containers | mitigate | `docker-compose.yml` — `memory: 512m/512m/128m/256m` on all 4 services | closed |
| T-13-12 | Elevation of Privilege | JWT_SECRET empty in compose allows auth bypass if assertion skipped | mitigate | `docker-compose.yml:17` — `JWT_SECRET: ${JWT_SECRET}` (required); AppConfig assertion fires at startup | closed |

---

## Accepted Risks Log

| Risk ID | Threat Ref | Rationale | Accepted By | Date |
|---------|------------|-----------|-------------|------|
| AR-13-01 | T-13-03 | `.env.example` contains only `<FILL_IN>` placeholders — no real secrets. It is the documentation artifact and must be committed for developer onboarding. | St4r4x | 2026-04-12 |

---

## Security Audit Trail

| Audit Date | Threats Total | Closed | Open | Run By |
|------------|---------------|--------|------|--------|
| 2026-04-12 | 12 | 12 | 0 | gsd-security-auditor (sonnet) |

---

## Sign-Off

- [x] All threats have a disposition (mitigate / accept / transfer)
- [x] Accepted risks documented in Accepted Risks Log
- [x] `threats_open: 0` confirmed
- [x] `status: verified` set in frontmatter

**Approval:** verified 2026-04-12
