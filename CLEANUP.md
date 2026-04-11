# Cleanup & Product Improvements

Technical debt, dead code, and product-level improvements to address before a sale/demo.

---

## 1. Dead Code (Java)

### RestaurantService.java
- [x] Remove `getAverageScoreByCuisineAndBorough()` (lines 53–56) — no controller calls it
- [x] Remove `getCuisinesWithMinimumCount()` (lines 74–77) — no controller calls it

### RestaurantDAO.java + RestaurantDAOImpl.java
- [x] Remove `findAverageScoreByCuisineAndBorough()` — interface + impl (only called by dead service method above)
- [x] Remove `findCuisinesWithMinimumCount()` — interface + impl (only called by dead service method above)
- [x] Remove `findByCuisine()` — interface + impl (only referenced in integration test, not production)
- [x] Remove `countByCuisine()` — interface + impl (same)
- [x] Update `RestaurantDAOIntegrationTest` to remove the 2 tests that call `findByCuisine` / `countByCuisine`

### AppConfig.java
- [x] Remove `getRedisTopLimit()` (line 75–77) — never called anywhere

---

## 2. Dead Configuration

### application.properties
- [x] Remove `app.name=Restaurant Analytics` (line 32) — never read
- [x] Remove `app.version=1.0-SNAPSHOT` (line 33) — never read
- [x] Remove `spring.application.name=restaurant-analyzer` (line 2) — never read by application code
- [x] Remove `redis.top.limit=10` (line 49) — only read by `getRedisTopLimit()` which is dead

---

## 3. Naming / Branding (cosmetic but visible to buyers)

### pom.xml
- [x] `artifactId`: `quickstart-app` → `restaurant-hygiene-app`
- [x] `name`: `quickstart-app` → `Restaurant Hygiene Control App`
- [ ] `groupId`: `com.aflokkat` → `com.restauranthygiene` (big refactor — rename all packages)

### OpenApiConfig.java
- [x] Swagger contact name: `"Aflokkat — Big Data Module"` → `"Restaurant Hygiene Control"` (or buyer name)
- [x] Swagger contact URL: update to real repo/product URL
- [x] Swagger `version`: `"1.0"` → `"2.0"`

### application.properties
- [ ] `logging.level.com.aflokkat=DEBUG` → update package prefix once groupId renamed

### Templates HTML
- [x] `inspection-map.html` line 5: `<title>Carte des Restaurants</title>` → `<title>Inspection Map — NYC Restaurant Inspector</title>`
- [ ] `restaurant.html` line 266: `Note ${latestGrade}` → `Grade ${latestGrade}` (French label)

---

## 4. Package rename (com.aflokkat → com.restauranthygiene)

Big refactor — touches every Java file. Do in one atomic commit with IDE refactor tool.

- [ ] Rename package `com.aflokkat` → `com.restauranthygiene` across all Java sources and tests
- [ ] Update `logging.level.com.aflokkat` in `application.properties`
- [ ] Verify `mvn test` passes after rename

---

## 5. Files to delete

- [x] `restaurants.json` (11 MB at project root) — leftover from initial version, not used at runtime
- [ ] Any empty directories left after cleanup

---

## 6. Security hardening (critical before sale)

- [x] `jwt.secret` in `application.properties` is a hardcoded default — must be injected via env var only, remove the fallback value
- [x] `spring.datasource.password=restaurant` in `application.properties` — weak default, must be env var
- [x] `DataSeeder.SEED_PASSWORD = "Test1234!"` — seed accounts should be disabled or use env var in production
- [x] Add `spring.jpa.show-sql=false` comment explaining it must stay false in production
- [x] Consider adding `server.error.whitelabel.enabled=false` and a custom error page (currently shows Spring whitelabel on 404/500)
- [x] Review `anyRequest().permitAll()` in `SecurityConfig` — document explicitly why it's intentional (client-side IIFE guards)

---

## 7. Product-level improvements (for demo/sale)

### UX — Customer authenticated home
- [x] Fix: after CUSTOMER login, `window.location.href = "/"` lands on `landing.html` (server always returns landing for browser nav because no JWT header). Add JS redirect on `landing.html`: if `accessToken` in localStorage with `ROLE_CUSTOMER`, redirect to `/home` (new route) or send JWT via cookie/session
- [x] Alternative simpler fix: add `document.addEventListener('DOMContentLoaded', ...)` at top of `landing.html` that reads localStorage JWT and redirects CUSTOMER to `index.html` route directly

### Admin redirect after login
- [x] After ADMIN login, user lands on `landing.html` instead of `/admin` — add `ROLE_ADMIN → /admin` branch to `login.html` redirect logic (line 208)

### Error pages
- [x] Add `src/main/resources/templates/error/404.html` — custom branded 404 page
- [x] Add `src/main/resources/templates/error/500.html` — custom branded 500 page
- [x] Remove Spring whitelabel error page (`server.error.whitelabel.enabled=false` in properties)

### Favicon
- [ ] Add `src/main/resources/static/favicon.ico` — browser tab shows default icon currently

### README
- [ ] Update README to remove any academic references
- [ ] Add "Getting Started" section with Docker one-liner: `docker compose up -d`
- [ ] Add screenshots/GIF of the app in action
- [ ] Add architecture diagram link

### API versioning
- [ ] Consider prefixing all API routes with `/api/v1/` for professionalism (breaking change — coordinate with frontend)

### CI/CD
- [ ] Add `.github/workflows/ci.yml` — runs `mvn test` on push to `develop` and `main`

### Logging
- [ ] Change `logging.level.com.aflokkat=DEBUG` to `INFO` for production builds — DEBUG is very noisy
- [ ] Add log rotation config (`logging.file.name`, `logging.logback.rollingpolicy.*`)

### NYC data sync scheduling
- [ ] `SyncService` likely has a manual trigger only — add a `@Scheduled` cron job for automatic nightly sync (e.g. `0 0 2 * * *` = 2am daily)
- [ ] Expose sync schedule as a configurable property

### Performance
- [ ] MongoDB: verify 2dsphere index exists on `address.coord` (required for `findNearby`)
- [ ] MongoDB: verify text index on `name` and `address.street` for `searchByNameOrAddress` regex performance
- [ ] Consider pagination on `GET /api/restaurants/map-points` — currently loads all ~27K restaurants at once

---

## 8. Grade enum naming conflict

- [ ] `com.aflokkat.entity.Grade` (JPA enum) vs `com.aflokkat.domain.Grade` (MongoDB POJO) — same class name in different packages
- [ ] Rename JPA enum: `com.aflokkat.entity.Grade` → `com.aflokkat.entity.InspectionGrade`
- [x] Update all references in `InspectionReportEntity`, `ReportController`, `AdminController`, tests

---

## Priority order

| Priority | Item |
|---|---|
| 🔴 Must-do before sale | Security hardening (§6) |
| 🔴 Must-do before sale | Fix CUSTOMER home redirect (§7 UX) |
| 🔴 Must-do before sale | Remove `restaurants.json` 11MB (§5) |
| 🟡 Do soon | Dead code removal (§1–2) |
| 🟡 Do soon | `Grade` rename (§8) |
| 🟡 Do soon | Branding / naming (§3) |
| 🟡 Do soon | Custom error pages + favicon (§7) |
| 🟢 Nice to have | Package rename com.aflokkat (§4) |
| 🟢 Nice to have | CI/CD, API versioning, logging (§7) |
| 🟢 Nice to have | Nightly sync scheduling (§7) |

---

*Created: 2026-04-11*
