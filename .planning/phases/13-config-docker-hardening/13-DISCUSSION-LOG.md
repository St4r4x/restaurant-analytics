# Phase 13: Config & Docker Hardening - Discussion Log

> **Audit trail only.** Do not use as input to planning, research, or execution agents.
> Decisions are captured in CONTEXT.md — this log preserves the alternatives considered.

**Date:** 2026-04-12
**Phase:** 13-config-docker-hardening
**Areas discussed:** Local dev secret injection, Dockerfile Java version fix, docker-compose secrets scope, Startup assertion scope

---

## Local Dev Secret Injection

| Option | Description | Selected |
|--------|-------------|----------|
| .env file | Developer copies .env.example → .env, fills in values. AppConfig already loads it via dotenv. | ✓ |
| Shell environment only | Developer sets export JWT_SECRET=... in ~/.bashrc or similar. No .env file. | |
| Keep dev fallback in application.properties | Keep a non-secret placeholder value that passes the assertion. | |

**User's choice:** `.env` file
**Notes:** AppConfig already has dotenv support wired in — this approach requires zero code changes to the lookup chain.

---

## Dockerfile Java Version Fix

| Option | Description | Selected |
|--------|-------------|----------|
| Upgrade to Java 25 Alpine | FROM maven:3.9-eclipse-temurin-25 + FROM eclipse-temurin:25-jre-alpine. Fixes mismatch + satisfies DOCKER-04. | ✓ |
| Downgrade pom.xml to Java 21 | Keep Dockerfile on Java 21, change pom.xml back to java.version=21. | |
| Keep Java 25 but stay on Jammy | Upgrade to Java 25 but keep jre-jammy instead of alpine. | |

**User's choice:** Upgrade to Java 25 Alpine
**Notes:** Critical mismatch — pom.xml compiles with Java 25 but Dockerfile runs Java 21 JRE. Would throw UnsupportedClassVersionError at container startup.

---

## docker-compose Secrets Scope

| Option | Description | Selected |
|--------|-------------|----------|
| Move everything to .env | All credentials (JWT, signup codes, DB password/user) go in .env. docker-compose uses ${VAR} references. | ✓ |
| Only app-level secrets | Keep POSTGRES_PASSWORD: restaurant hardcoded in docker-compose.yml. Only move JWT and signup codes. | |

**User's choice:** Move everything to .env
**Notes:** Single source of truth for all secrets. A new developer clones, copies .env.example, and the full stack runs.

---

## Startup Assertion Scope

| Option | Description | Selected |
|--------|-------------|----------|
| JWT only | Assert JWT_SECRET only (required for all auth). Signup codes null = disabled is intentional. | ✓ |
| JWT + signup codes | Fail-fast if JWT_SECRET, CONTROLLER_SIGNUP_CODE, or ADMIN_SIGNUP_CODE is missing. | |

**User's choice:** JWT only

| Option | Description | Selected |
|--------|-------------|----------|
| AppConfig.getJwtSecret() | Throw IllegalStateException immediately when value missing or <32 chars. Consistent with existing pattern. | ✓ |
| ApplicationRunner bean | Dedicated @Component validates all required env vars at startup. More Spring-idiomatic. | |

**User's choice:** AppConfig.getJwtSecret()
**Notes:** Fails before Spring context fully loads. Consistent with the existing AppConfig static method pattern.

---

## Claude's Discretion

- Memory limit values (no user preference expressed)
- `.dockerignore` content
- `application-test.properties` content
- Exact error message text for JWT_SECRET assertion

## Deferred Ideas

- Rotating JWT secrets without downtime
- Docker Secrets (Swarm/K8s)
- `.env` validation pre-commit hook
