# Remaining Phases — v3.0 Production Readiness

> Handoff document for milestone completion without GSD tooling.
> Generated: 2026-04-15 | Branch: gsd/phase-16-security-hardening

---

## Status Summary

| Phase | Description | Status | Plans left |
|-------|-------------|--------|------------|
| 16 | Security Hardening | **In Progress** | 1 plan (16-03) |
| 17 | Code Quality & MongoDB Indexing | Not started | TBD |
| 18 | E2E Tests (Playwright) | Not started | TBD |
| 19 | Unit & Controller Tests | Not started | TBD |
| 20 | UI Visual Redesign | Not started | TBD |
| 21 | Java 25 / Spring Boot 4.0.5 upgrade | **Complete** | — |

After Phase 20, the milestone v3.0 is complete and the project is ready for deployment.

---

## Phase 16 — Security Hardening (In Progress)

**Branch:** `gsd/phase-16-security-hardening`
**Plans done:** 16-01 ✅ 16-02 ✅ 16-04 ✅ — **16-03 remaining**

### Plan 16-03 — Bean Validation on Auth DTOs

**Goal:** Reject empty credentials at the controller layer before they reach `AuthService`.

#### Files to modify

| File | Change |
|------|--------|
| `pom.xml` | Add `spring-boot-starter-validation` dependency |
| `src/main/java/com/aflokkat/dto/AuthRequest.java` | `@NotBlank` on `username`, `password` |
| `src/main/java/com/aflokkat/dto/RegisterRequest.java` | `@NotBlank` on `username`, `password`; `@NotBlank @Email` on `email` |
| `src/main/java/com/aflokkat/dto/RefreshRequest.java` | `@NotBlank` on `refreshToken` |
| `src/main/java/com/aflokkat/controller/AuthController.java` | Add `@Valid` before each `@RequestBody` param (3 methods) |
| `src/main/java/com/aflokkat/controller/GlobalExceptionHandler.java` | **Create** — `@RestControllerAdvice` returning `{status, message, timestamp}` on HTTP 400 |

#### pom.xml addition (inside `<dependencies>`, after security block)

```xml
<dependency>
  <groupId>org.springframework.boot</groupId>
  <artifactId>spring-boot-starter-validation</artifactId>
</dependency>
```

#### GlobalExceptionHandler.java (create)

```java
package com.aflokkat.controller;

import org.springframework.http.HttpStatus;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import java.time.Instant;
import java.util.Map;
import java.util.stream.Collectors;

@RestControllerAdvice
public class GlobalExceptionHandler {

    @ExceptionHandler(MethodArgumentNotValidException.class)
    @ResponseStatus(HttpStatus.BAD_REQUEST)
    public Map<String, Object> handleValidation(MethodArgumentNotValidException ex) {
        String message = ex.getBindingResult().getFieldErrors().stream()
                .map(fe -> fe.getField() + " " + fe.getDefaultMessage())
                .collect(Collectors.joining(", "));
        return Map.of(
                "status", "error",
                "message", message,
                "timestamp", Instant.now().toString()
        );
    }
}
```

#### Verification

```bash
mvn test -Dtest=AuthControllerValidationTest,GlobalExceptionHandlerTest -q
# Expected: exit 0 (5 + 3 = 8 tests GREEN)

mvn test -q
# Expected: exit 0 (full suite, no regression)
```

#### Success criteria

- `grep -c "spring-boot-starter-validation" pom.xml` → 1
- `grep -c "@NotBlank" src/main/java/com/aflokkat/dto/AuthRequest.java` → 2
- `grep -c "@NotBlank" src/main/java/com/aflokkat/dto/RegisterRequest.java` → 3
- `grep -c "@Email" src/main/java/com/aflokkat/dto/RegisterRequest.java` → 1
- `grep -c "@Valid" src/main/java/com/aflokkat/controller/AuthController.java` → 3
- `test -f src/main/java/com/aflokkat/controller/GlobalExceptionHandler.java`

#### After completing Phase 16

1. Run `mvn test -q` — full suite must be GREEN
2. Commit: `feat(16): input validation — Bean Validation on auth DTOs, GlobalExceptionHandler`
3. Merge `gsd/phase-16-security-hardening` → `develop` → `main`
4. Update `CHANGELOG.md`:
   ```
   ### Phase 16: Security Hardening (2026-04-15)
   - Explicit CORS policy (CorsConfigurationSource bean + http.cors)
   - Security headers (X-Content-Type-Options, X-Frame-Options, etc.)
   - Bean Validation on auth DTOs (@NotBlank, @Email) + GlobalExceptionHandler
   - Rate limiting extended to restaurant endpoints (separate bucket from auth)
   ```

---

## Phase 17 — Code Quality & MongoDB Indexing

**Depends on:** Phase 16 complete

### Goal

Dead code removed, all endpoints documented in Swagger, error responses uniform, MongoDB queries use indexes.

### Key tasks

#### 1. Dead code audit

Search for and remove unused classes/endpoints. Candidate areas:
- `ResponseUtil` — verify all callers use consistent response shape
- Any controller/service method not reachable from any frontend or API

#### 2. OpenAPI / Swagger completion

Every controller endpoint must have:
- A `@Tag` annotation on the controller class (groups in Swagger UI)
- `@Operation(summary = "...")` on each method
- `@ApiResponse` for at least 200 and error codes
- `@SecurityRequirement(name = "Bearer")` on authenticated endpoints

Check: `http://localhost:8080/swagger-ui.html` — every endpoint visible, grouped, with lock icons on secured ones.

#### 3. ResponseUtil consistency

Ensure all error responses (400, 401, 403, 404, 429, 500) return JSON `{status, message, timestamp}`.
- `GlobalExceptionHandler` (created in Phase 16) handles 400 validation errors
- Add `@ExceptionHandler` entries for 404 (`NoHandlerFoundException`) and 500 (`Exception`) if missing

#### 4. MongoDB indexes at startup

In `MongoClientFactory` or a `@PostConstruct` method in a config bean, create indexes on the `restaurants` collection:

```java
// Index for search by borough and cuisine
collection.createIndex(Indexes.ascending("boro", "cuisine_description"));
// Index for score queries
collection.createIndex(Indexes.ascending("camis"));
// Index for grade filter
collection.createIndex(Indexes.ascending("grades.grade"));
```

#### Verification

```bash
# Swagger: all endpoints documented
curl http://localhost:8080/v3/api-docs | jq '.paths | keys | length'

# Error response shape: 404 returns structured JSON
curl http://localhost:8080/api/restaurants/nonexistent | jq '.status, .message, .timestamp'

# Index verification (against running MongoDB)
mvn test -Dtest=RestaurantDAOIT -q
# explain() output shows IXSCAN not COLLSCAN
```

#### Success criteria

1. Swagger UI shows every endpoint with tag, description, response codes, and lock on secured endpoints
2. Every error response (400–500) returns `{status, message, timestamp}` — no Spring whitepage
3. MongoDB `explain()` on restaurant search shows `IXSCAN`
4. No dead code from `CLEANUP.md` remains (grep returns 0 matches)
5. Restaurant list DAO returns projected fields only (not full documents)

---

## Phase 18 — E2E Tests (Playwright)

**Depends on:** Phase 17 complete, Phase 13 Docker health checks

### Goal

Automated browser tests covering 4 critical flows, running in CI against a live `docker compose` stack.

### Setup

Add Playwright dependency to `pom.xml` (pinned at `1.49.0` per project constraints):

```xml
<dependency>
  <groupId>com.microsoft.playwright</groupId>
  <artifactId>playwright</artifactId>
  <version>1.49.0</version>
  <scope>test</scope>
</dependency>
```

### Test flows to implement

| Test class | Flow | Assertions |
|------------|------|------------|
| `LoginE2ETest` | Valid login → redirect to home | URL contains `/home` or `/` |
| `LoginE2ETest` | Invalid login → error message | Error element visible |
| `SearchE2ETest` | Search restaurant by name | Results list non-empty |
| `MapE2ETest` | Open map page | Map container renders (no JS error) |
| `DashboardAccessE2ETest` | CUSTOMER JWT → `/dashboard` blocked | Redirect or error visible |

### Auth pattern for Playwright

```java
// Do NOT use storageState() — app uses localStorage JWT
APIRequestContext api = playwright.request().newContext(...);
APIResponse loginResp = api.post("/api/auth/login", RequestOptions.create()
    .setData(Map.of("username", "controller1", "password", "password")));
String token = new JSONObject(loginResp.text()).getString("accessToken");

page.addInitScript("window.localStorage.setItem('accessToken', '" + token + "')");
page.navigate(baseUrl + "/dashboard");
```

### CI integration (`.github/workflows/ci.yml`)

Update the `e2e` job (currently placeholder) to:
1. Run `docker compose up -d`
2. Wait for health: `curl --retry 10 --retry-delay 3 http://localhost:8080/api/restaurants/health`
3. Run `mvn failsafe:integration-test -Pe2e`
4. Run `docker compose down`

Set `NYC_API_MAX_RECORDS=200` in the E2E compose env to cap sync to ~10s.

#### Success criteria

1. `mvn failsafe:integration-test -Pe2e` runs all 5 tests and reports pass/fail per test
2. CI `e2e` job boots full stack, runs tests, tears down without orphaned containers
3. Login test covers both valid and invalid credential scenarios
4. Dashboard test confirms CUSTOMER role cannot access `/dashboard`

---

## Phase 19 — Unit & Controller Tests

**Depends on:** Phase 12 (JaCoCo argLine fix in place)

### Goal

Service-layer and controller-layer covered by fast (no Spring context) tests.

### Unit tests to add (Mockito, no Spring context)

#### `AuthServiceTest`

| Test | Description |
|------|-------------|
| `register_success` | Mock `UserRepository.existsByUsername` returns false → user saved, no exception |
| `register_duplicateUsername` | Mock returns true → throws `RuntimeException` |
| `login_validCredentials` | Mock `UserRepository.findByUsername` + `BCryptPasswordEncoder.matches` → returns `JwtResponse` |
| `login_invalidPassword` | `matches` returns false → throws exception |
| `refresh_validToken` | Mock `JwtUtil.validateRefreshToken` true → returns new `JwtResponse` |

#### `RestaurantServiceTest`

| Test | Description |
|------|-------------|
| `search_delegatesToDAO` | Verify `RestaurantDAO.search(...)` called with correct params |
| `stats_returnsCachedValue` | Mock cache hit → `RestaurantDAO.getStats()` NOT called |
| `byBorough_delegatesToDAO` | Verify `RestaurantDAO.countByBorough()` called |

### Controller slice tests (`@WebMvcTest`)

#### `AuthControllerTest`

```java
@WebMvcTest(AuthController.class)
// Mock: AuthService
```

| Test | Expected |
|------|----------|
| `POST /api/auth/login` valid body | HTTP 200, body has `accessToken` |
| `POST /api/auth/login` empty password | HTTP 400 (Bean Validation — from Phase 16) |
| `POST /api/auth/register` valid body | HTTP 200 |
| `POST /api/auth/refresh` valid body | HTTP 200 |

#### `RestaurantControllerTest`

```java
@WebMvcTest(RestaurantController.class)
// Mock: RestaurantService
```

| Test | Expected |
|------|----------|
| `GET /api/restaurants/by-borough` | HTTP 200, JSON array |
| `GET /api/restaurants/stats` | HTTP 200, JSON object |
| `GET /api/restaurants/health` | HTTP 200 |

#### `InspectionControllerTest` and `UserControllerTest`

Similar pattern — mock service, assert HTTP status and response JSON shape.

### Coverage target

After Phase 19, run `mvn jacoco:report` and verify line coverage >= current baseline (38% per Phase 12).
Goal: reach at least 50% with new tests.

#### Success criteria

1. `mvn test -q` passes all existing + new tests with zero failures
2. `AuthService` and `RestaurantService` have Mockito unit tests covering register/login/refresh and search/stats/byBorough
3. `@WebMvcTest` tests exist for `AuthController`, `RestaurantController`, `InspectionController`, `UserController`
4. JaCoCo line coverage >= 50%

---

## Phase 20 — UI Visual Redesign

**Depends on:** Phase 18 (E2E test class names must not silently break)

### Goal

All 13 application pages share a dark/neutral design system. CSS tokens, unified component classes, animated grade badges, Lucide SVG icons.

### Design tokens (create `src/main/resources/static/css/design-system.css`)

```css
:root {
  /* Colors */
  --bg-primary:    #0f0f0f;
  --bg-secondary:  #1a1a1a;
  --bg-card:       #242424;
  --text-primary:  #f5f5f5;
  --text-secondary:#a0a0a0;
  --accent:        #3b82f6;  /* blue-500 */
  --accent-hover:  #2563eb;  /* blue-600 */
  --error:         #ef4444;
  --success:       #22c55e;
  --warning:       #f59e0b;

  /* Grade badge colors */
  --grade-a: #22c55e;
  --grade-b: #f59e0b;
  --grade-c: #ef4444;
  --grade-z: #6b7280;

  /* Spacing */
  --space-1: 0.25rem;
  --space-2: 0.5rem;
  --space-4: 1rem;
  --space-6: 1.5rem;
  --space-8: 2rem;

  /* Radius */
  --radius-sm: 4px;
  --radius-md: 8px;
  --radius-lg: 12px;

  /* Typography */
  --font-sans: 'Inter', system-ui, -apple-system, sans-serif;
  --font-mono: 'JetBrains Mono', 'Fira Code', monospace;
}
```

### Shared component classes

```css
.card {
  background: var(--bg-card);
  border: 1px solid #2a2a2a;
  border-radius: var(--radius-md);
  padding: var(--space-4);
}

.btn-primary {
  background: var(--accent);
  color: var(--text-primary);
  border: none;
  border-radius: var(--radius-sm);
  padding: var(--space-2) var(--space-4);
  cursor: pointer;
  transition: background 0.2s;
}

.btn-primary:hover { background: var(--accent-hover); }

.grade-badge {
  display: inline-flex;
  align-items: center;
  justify-content: center;
  width: 2rem;
  height: 2rem;
  border-radius: 50%;
  font-weight: bold;
  animation: fadeSlideIn 0.3s ease-out;
}

.grade-a { background: var(--grade-a); color: #fff; }
.grade-b { background: var(--grade-b); color: #fff; }
.grade-c { background: var(--grade-c); color: #fff; }
.grade-z { background: var(--grade-z); color: #fff; }

@keyframes fadeSlideIn {
  from { opacity: 0; transform: translateY(-4px); }
  to   { opacity: 1; transform: translateY(0); }
}
```

### Thymeleaf templates to update

All 13 templates in `src/main/resources/templates/`:

1. Add `<link rel="stylesheet" th:href="@{/css/design-system.css}">` to base layout
2. Replace all `background: white` / light backgrounds with `var(--bg-primary)` or `var(--bg-secondary)`
3. Replace all inline hex colors with CSS variables
4. Replace emoji characters (🔍 🏙️ ⚠️ etc.) with Lucide SVG icons
5. Apply `.card` class to all restaurant cards
6. Apply `.grade-badge .grade-a/b/c/z` to all grade displays

### Lucide icons

Include via CDN or copy SVGs locally:
```html
<!-- In base layout <head> -->
<script src="https://unpkg.com/lucide@latest/dist/umd/lucide.min.js"></script>
<!-- Usage: <i data-lucide="search"></i> -->
<!-- After DOM ready: lucide.createIcons(); -->
```

#### Success criteria

1. Any page has dark background `#0f0f0f`–`#1a1a1a` with `#f5f5f5` text
2. All color/spacing/typography values come from CSS variables in `design-system.css` — no inline styles or hardcoded hex in templates
3. Restaurant cards use `.card` class consistently across search, analytics, and dashboard
4. Grade A badge shows green circle with CSS animation; B/C/Z have distinct colors
5. `grep -r "U+1F" src/main/resources/templates/` returns 0 (no emoji in templates)

---

## Deployment Checklist (Post Phase 20)

### Pre-deployment

```bash
# 1. Full test suite
mvn clean verify -q
# Expected: BUILD SUCCESS

# 2. Docker build
docker compose build
# Expected: no errors

# 3. Environment setup
cp .env.example .env
# Fill in: JWT_SECRET (min 32 chars), MONGODB_URI, POSTGRES_*, REDIS_*

# 4. Stack smoke test
docker compose up -d
curl http://localhost:8080/api/restaurants/health
# Expected: {"status":"UP"} or similar
```

### Environment variables required (`.env`)

```bash
JWT_SECRET=<min-32-char-random-string>
MONGODB_URI=mongodb://mongodb:27017
MONGODB_DATABASE=newyork
MONGODB_COLLECTION=restaurants
POSTGRES_DB=restaurantdb
POSTGRES_USER=restaurant
POSTGRES_PASSWORD=<strong-password>
REDIS_HOST=redis
REDIS_PORT=6379
NYC_API_APP_TOKEN=<optional-token>
NYC_API_MAX_RECORDS=50000
```

### Production startup

```bash
docker compose up -d
# Services start in order: mongodb → postgres → redis → app
# App waits for all three health checks before accepting traffic

# Tail logs
docker compose logs -f app

# Data sync (first run)
curl -X POST http://localhost:8080/api/sync/start \
  -H "Authorization: Bearer <admin-jwt>"

# Verify data loaded
curl http://localhost:8080/api/restaurants/stats
```

### GitHub release

After all phases complete and pushed to `main`:

```bash
git tag -a v3.0.0 -m "v3.0 Production Readiness"
git push github.com-personal St4r4x/restaurant-analytics v3.0.0
```

---

## Key Technical Constraints (from project history)

| Constraint | Value | Reason |
|------------|-------|--------|
| Testcontainers | 1.x (1.19.8) | 2.x dropped JUnit 4 support |
| Bucket4j | 7.6.1 | 8.x requires JDK 17 |
| Playwright | 1.49.0 | Upgrade only if CI browser install fails |
| JaCoCo argLine | `@{argLine}` (late-binding) | Literal string causes StackOverflowError with Mockito |
| CORS | Both `CorsConfigurationSource` bean AND `http.cors(withDefaults())` required | Either alone causes OPTIONS 403 |
| Playwright auth | `addInitScript()` to inject localStorage JWT | `storageState()` does not work for localStorage JWT |
| GHCR push | Requires OCI `LABEL org.opencontainers.image.source` in Dockerfile | Absent label → `permission_denied` |
| `AppConfig.getProperty()` | Must check `System.getProperty()` before env vars | Required for Testcontainers URI injection |
| Mockito on Java 25 | Use `UsernamePasswordAuthenticationToken` concrete class | `mock(Authentication.class)` fails |
| `AppConfig` static mock | Use reflection to patch `.properties` field | `mockStatic(AppConfig.class)` causes `VerifyError` |
