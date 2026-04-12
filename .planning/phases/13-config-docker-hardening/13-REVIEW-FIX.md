---
phase: 13-config-docker-hardening
fixed_at: 2026-04-12T00:00:00Z
review_path: .planning/phases/13-config-docker-hardening/13-REVIEW.md
iteration: 1
findings_in_scope: 4
fixed: 4
skipped: 0
status: all_fixed
---

# Phase 13: Code Review Fix Report

**Fixed at:** 2026-04-12
**Source review:** .planning/phases/13-config-docker-hardening/13-REVIEW.md
**Iteration:** 1

**Summary:**
- Findings in scope: 4
- Fixed: 4
- Skipped: 0

## Fixed Issues

### WR-01: `mongo:latest` image tag is unpinned

**Files modified:** `docker-compose.yml`
**Commit:** b4537a3
**Applied fix:** Changed `image: mongo:latest` to `image: mongo:7.0` to pin the MongoDB image to a specific major version, matching the pinning discipline already applied to `redis:7-alpine` and `postgres:15`.

---

### WR-02: `SPRING_DATASOURCE_USERNAME` not injected via environment in `docker-compose.yml`

**Files modified:** `docker-compose.yml`, `.env.example`
**Commit:** c562b82
**Applied fix:** Added `SPRING_DATASOURCE_USERNAME: ${POSTGRES_USER:-restaurant}` to the app container `environment` block in `docker-compose.yml`, so the datasource username tracks `POSTGRES_USER` rather than relying on the `application.properties` hardcoded fallback. Also added a corresponding `SPRING_DATASOURCE_USERNAME=restaurant` entry (with explanatory comment) to `.env.example` so operators know to keep it in sync with `POSTGRES_USER`.

---

### WR-03: MongoDB port 27017 exposed to host with no authentication

**Files modified:** `docker-compose.yml`
**Commit:** dc57849
**Applied fix:** Removed the `ports: - "27017:27017"` entry from the `mongodb` service. The app container reaches MongoDB via the internal `restaurant-network` Docker bridge and requires no host port. This eliminates the unauthenticated exposure to the host network without requiring credential management changes for an academic project.

---

### WR-04: `restoreJwtSecret` in `AppConfigTest` skips dotenv restore when `savedDotenv` was already null

**Files modified:** `src/test/java/com/aflokkat/config/AppConfigTest.java`
**Commit:** 92aee0e
**Applied fix:** Added a `private boolean dotenvCleared = false` field. `clearDotenv()` now sets `dotenvCleared = true` after nulling the field. `restoreJwtSecret()` now checks `if (dotenvCleared)` (not `if (savedDotenv != null)`) before restoring, and resets `dotenvCleared = false` after the restore. This guarantees the field is always restored when `clearDotenv()` was called, even if `AppConfig.dotenv` was already `null` before the test ran.

---

_Fixed: 2026-04-12_
_Fixer: Claude (gsd-code-fixer)_
_Iteration: 1_
