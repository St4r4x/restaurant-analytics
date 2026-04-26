---
phase: 13-config-docker-hardening
reviewed: 2026-04-12T00:00:00Z
depth: standard
files_reviewed: 11
files_reviewed_list:
  - docker-compose.yml
  - Dockerfile
  - .dockerignore
  - .env.example
  - .gitignore
  - src/main/java/com/aflokkat/config/AppConfig.java
  - src/main/resources/application.properties
  - src/test/java/com/aflokkat/config/AppConfigTest.java
  - src/test/java/com/aflokkat/config/SecurityConfigTest.java
  - src/test/java/com/aflokkat/security/JwtUtilTest.java
  - src/test/resources/application-test.properties
findings:
  critical: 0
  warning: 4
  info: 3
  total: 7
status: issues_found
---

# Phase 13: Code Review Report

**Reviewed:** 2026-04-12
**Depth:** standard
**Files Reviewed:** 11
**Status:** issues_found

## Summary

Phase 13 introduced Docker hardening, environment-based secret injection, and test infrastructure for `AppConfig`, `SecurityConfig`, and `JwtUtil`. The overall design is sound: secrets are never hardcoded in tracked files, the non-root Docker user pattern is correctly applied, and `AppConfig.getJwtSecret()` enforces a minimum-length guard at startup. No critical security issues were found.

Four warnings stand out: the `mongo:latest` image tag breaks reproducibility and can silently pull a breaking change; `SPRING_DATASOURCE_USERNAME` is absent from `docker-compose.yml` (Spring falls back to `application.properties`, creating a hidden coupling); MongoDB has no authentication configured in the Compose stack (unauthenticated port 27017 is exposed to the host); and the `@After` restore logic in `AppConfigTest` has a logic gap that can leave `dotenv` nulled out across tests if the field is already null before `clearDotenv()` is called.

Three informational items are noted: the builder stage uses `eclipse-temurin:25` (Java 25, a non-LTS EA release) while the project targets Java 11 per CLAUDE.md; the `application.properties` default for `spring.datasource.password` is the literal string `restaurant`, which will silently work in environments where the variable is unset; and the `SPRING_DATASOURCE_PASSWORD` duplication between `POSTGRES_PASSWORD` and `SPRING_DATASOURCE_PASSWORD` in `.env.example` is a maintainability risk.

---

## Warnings

### WR-01: `mongo:latest` image tag is unpinned

**File:** `docker-compose.yml:47`
**Issue:** `image: mongo:latest` will silently pull the newest MongoDB major version on the next `docker compose pull`, potentially introducing breaking API or behavior changes (e.g., Mongo 5 → 6 → 7 all have aggregation pipeline changes). `redis:7-alpine` and `postgres:15` are pinned — `mongo` is the exception.
**Fix:**
```yaml
mongodb:
  image: mongo:7.0
```
Pin to the specific major (or major.minor) version you have tested against.

---

### WR-02: `SPRING_DATASOURCE_USERNAME` not injected via environment in `docker-compose.yml`

**File:** `docker-compose.yml:9-21`
**Issue:** The app container receives `SPRING_DATASOURCE_PASSWORD` via the `environment` block but not `SPRING_DATASOURCE_USERNAME`. Spring resolves `spring.datasource.username` from `application.properties` (hardcoded `restaurant`). This creates a hidden coupling: if the Postgres user is ever changed via `POSTGRES_USER` in `.env`, the app will still try to connect as `restaurant` and authentication will fail silently during startup.
**Fix:**
```yaml
environment:
  # existing vars ...
  SPRING_DATASOURCE_PASSWORD: ${SPRING_DATASOURCE_PASSWORD}
  SPRING_DATASOURCE_USERNAME: ${POSTGRES_USER:-restaurant}
```
Then add `SPRING_DATASOURCE_USERNAME` to `.env.example` with a note that it must match `POSTGRES_USER`.

---

### WR-03: MongoDB port 27017 exposed to host with no authentication

**File:** `docker-compose.yml:52-53`
**Issue:** `ports: - "27017:27017"` publishes the MongoDB port to `0.0.0.0` on the host. The MongoDB service has no `MONGO_INITDB_ROOT_USERNAME` / `MONGO_INITDB_ROOT_PASSWORD` credentials configured (`MONGO_INITDB_DATABASE` only creates the DB, it does not enable auth). Any process on the host — or on a LAN network if the host firewall is open — can read and write to the database without credentials.
**Fix (minimal for local dev):** Either remove the host-side port binding (keep it internal to the Docker network) or add MongoDB authentication:
```yaml
mongodb:
  image: mongo:7.0
  environment:
    MONGO_INITDB_ROOT_USERNAME: ${MONGO_INITDB_ROOT_USERNAME}
    MONGO_INITDB_ROOT_PASSWORD: ${MONGO_INITDB_ROOT_PASSWORD}
    MONGO_INITDB_DATABASE: newyork
```
For an academic/local project, removing the `ports` entry is the simpler mitigation. The app container accesses MongoDB via the internal `restaurant-network` and does not need the host port.

---

### WR-04: `restoreJwtSecret` in `AppConfigTest` skips dotenv restore when `savedDotenv` was already null

**File:** `src/test/java/com/aflokkat/config/AppConfigTest.java:37-52`
**Issue:** `clearDotenv()` sets `savedDotenv = f.get(null)` before nulling the field. If `AppConfig.dotenv` is already `null` at that point (e.g., the `.env` file did not load), `savedDotenv` stays `null`. In `restoreJwtSecret()`, the condition `if (savedDotenv != null)` skips the restore — the field remains `null` for every subsequent test. This is benign when all JWT tests call `clearDotenv()` and the field was always null, but it is a latent bug: if the field starts non-null and a test crashes before `@After` runs, or test ordering changes, the dotenv state is permanently lost across the suite. The existing comment says "Restore dotenv if it was cleared" — but it only restores when the *saved value* was non-null, not when the *action* of clearing was taken.
**Fix:** Track whether `clearDotenv()` was called with a boolean flag, independent of the saved value:
```java
private boolean dotenvCleared = false;

private void clearDotenv() throws Exception {
    Field f = AppConfig.class.getDeclaredField("dotenv");
    f.setAccessible(true);
    savedDotenv = f.get(null);
    f.set(null, null);
    dotenvCleared = true;
}

@After
public void restoreJwtSecret() throws Exception {
    // ... restore properties ...
    if (dotenvCleared) {
        Field f = AppConfig.class.getDeclaredField("dotenv");
        f.setAccessible(true);
        f.set(null, savedDotenv);   // restores null-to-null correctly
        dotenvCleared = false;
    }
}
```

---

## Info

### IN-01: Builder and runtime images use Java 25 (non-LTS, EA-track) — project targets Java 11

**File:** `Dockerfile:1,16`
**Issue:** `FROM maven:3.9-eclipse-temurin-25` and `FROM eclipse-temurin:25-jre-alpine` pull Java 25, which is an Early Access / development release (non-LTS). CLAUDE.md states `Language: Java 11`. If `pom.xml` declares `<java.version>11</java.version>` the code compiles and runs, but CI and colleagues may get different behavior on LTS-only toolchains, and EA images receive no long-term security patches.
**Fix:** Align both stages with the project's stated Java version:
```dockerfile
FROM maven:3.9-eclipse-temurin-21 as builder
# ...
FROM eclipse-temurin:21-jre-alpine
```
Java 21 is the current LTS and is broadly available. If Java 11 is a hard requirement, use `eclipse-temurin:11-jre-alpine`.

---

### IN-02: `spring.datasource.password` default value in `application.properties` is a real password

**File:** `src/main/resources/application.properties:20`
**Issue:** `spring.datasource.password=${SPRING_DATASOURCE_PASSWORD:restaurant}` uses `restaurant` as the fallback. If `SPRING_DATASOURCE_PASSWORD` is not set, Spring will try to connect with the password `restaurant`. This silently succeeds in local dev (where the Postgres container is also created with `restaurant`) and can mask a missing secret instead of failing fast.
**Fix:** Remove the default so a missing variable causes an explicit startup failure:
```properties
spring.datasource.password=${SPRING_DATASOURCE_PASSWORD}
```

---

### IN-03: `POSTGRES_PASSWORD` and `SPRING_DATASOURCE_PASSWORD` are separate values in `.env.example`

**File:** `.env.example:21-23`
**Issue:** The example requires operators to set two different values that must be identical (`POSTGRES_PASSWORD` for the Postgres container, `SPRING_DATASOURCE_PASSWORD` for Spring Boot). They can silently diverge. A comment is present but the duplication is a maintenance trap.
**Fix:** Either collapse to one variable in `docker-compose.yml`:
```yaml
SPRING_DATASOURCE_PASSWORD: ${POSTGRES_PASSWORD}
```
and remove `SPRING_DATASOURCE_PASSWORD` from `.env.example`, or add a stronger warning comment that both values must be identical and why two exist.

---

_Reviewed: 2026-04-12_
_Reviewer: Claude (gsd-code-reviewer)_
_Depth: standard_
