# JWT httpOnly Cookie Migration Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Move both JWT access and refresh tokens from `localStorage` to httpOnly cookies, eliminating client-JS access to raw tokens while preserving all current auth behavior (login, register, refresh, role-based UI, logout).

**Architecture:** `AuthController` sets two cookies (`access_token`, `refresh_token`) via `HttpServletResponse` on `register`/`login`/`refresh`, and clears them on a new `logout` endpoint. `JwtAuthenticationFilter` reads `access_token` from `request.getCookies()` instead of the `Authorization` header. A new `GET /api/auth/me` endpoint replaces client-side JWT decoding. A single global `window.fetchWithAuth` in `fragments/ux-utils.html` replaces the 5 duplicated per-template implementations, adding reactive refresh-on-401. `app.cookie.secure` (new config key, default `true`, `false` in `docker-compose.yml`) keeps local HTTP dev-container testing working.

**Tech Stack:** Java 25, Spring Boot 4 (`HttpServletResponse`/`Cookie`, `@RestController`), JUnit 5 + Mockito (existing test stack), vanilla JS (existing template pattern, no build step).

## Global Constraints

- `access_token` cookie: httpOnly, secure (conditional — see `app.cookie.secure` below), SameSite=Strict, path `/`, `Max-Age` = `AppConfig.getJwtAccessTokenExpirationMs() / 1000` seconds (900s = 15 min today).
- `refresh_token` cookie: httpOnly, secure (conditional), SameSite=Strict, path `/api/auth/`, `Max-Age` = `AppConfig.getJwtRefreshTokenExpirationMs() / 1000` seconds (604800s = 7 days today).
- New config key `app.cookie.secure`, default `true` in `application.properties`, following the exact boolean-property pattern already used by `AppConfig.isRedisSsl()` (`"true".equalsIgnoreCase(getProperty(...))`). `docker-compose.yml`'s `app` service sets `APP_COOKIE_SECURE: "false"` for local dev (mirrors existing `APP_UPLOADS_DIR` entry in that same environment block).
- `register`/`login`/`refresh` response bodies no longer contain `accessToken`/`refreshToken` — body becomes `{"status": "success"}`. Tokens exist only as cookies (`Set-Cookie` response header).
- No CSRF token added — SameSite=Strict is the sole CSRF defense, per the design spec's decision.
- `AuthService.register()`/`login()`/`refresh()` method signatures are UNCHANGED — they still return `JwtResponse{accessToken, refreshToken}` internally; only `AuthController` changes how it turns that into an HTTP response.
- `SecurityConfig`'s `SessionCreationPolicy.STATELESS` is unchanged — cookies carry the JWT, they do not create a server session.
- Commits: English, imperative mood, conventional-commits prefix (`feat|fix|docs|chore|refactor|test|ci|style`), subject ≤72 chars, no trailing period.
- Work happens on branch `feature/jwt-httponly-cookies` (already created and pushed with the design spec) — never commit this work directly to `main`.

---

### Task 1: Add `app.cookie.secure` config and cookie-writing/-clearing helpers to `AuthController`

**Files:**
- Modify: `src/main/java/com/st4r4x/config/AppConfig.java`
- Modify: `src/main/resources/application.properties`
- Modify: `docker-compose.yml`
- Modify: `src/main/java/com/st4r4x/controller/AuthController.java`
- Modify: `src/test/java/com/st4r4x/controller/AuthControllerTest.java`

**Interfaces:**
- Consumes: nothing new.
- Produces: `AppConfig.isCookieSecure()` (`boolean`) — read by this task's own controller code. `AuthController`'s private `setAuthCookies(HttpServletResponse response, String accessToken, String refreshToken)` and `clearAuthCookies(HttpServletResponse response)` helpers — Tasks 2 and 4 call these when wiring `register`/`login`/`refresh`/`logout`.

- [ ] **Step 1: Add `isCookieSecure()` to `AppConfig`**

Add to `src/main/java/com/st4r4x/config/AppConfig.java`, right after the existing `isRedisSsl()` method:

```java
    public static boolean isCookieSecure() {
        return "true".equalsIgnoreCase(getProperty("app.cookie.secure", "true"));
    }
```

- [ ] **Step 2: Add the config key to `application.properties`**

In `src/main/resources/application.properties`, add this line right after `app.uploads.dir=/app/uploads` (currently line 71):

```properties
app.cookie.secure=true
```

- [ ] **Step 3: Set `APP_COOKIE_SECURE=false` in `docker-compose.yml` for local dev**

In `docker-compose.yml`, in the `app` service's `environment:` block, add this line right after `APP_UPLOADS_DIR: /app/uploads` (currently line 16):

```yaml
      APP_COOKIE_SECURE: "false"
```

- [ ] **Step 4: Write the failing tests for the cookie helpers**

Add these test methods to `src/test/java/com/st4r4x/controller/AuthControllerTest.java`, in a new section right before the `// ── check-username ──` section (i.e. after the `refresh` tests, before `checkUsername` tests):

```java
    // ── cookie helpers ────────────────────────────────────────────────────────

    @Test
    void login_setsAccessAndRefreshCookies_onSuccess() {
        AuthRequest req = new AuthRequest();
        req.setUsername("bob");
        req.setPassword("password");
        when(authService.login(req)).thenReturn(new JwtResponse("access-tok", "refresh-tok"));

        MockHttpServletResponse response = new MockHttpServletResponse();
        authController.login(req, response);

        Cookie accessCookie = response.getCookie("access_token");
        Cookie refreshCookie = response.getCookie("refresh_token");
        assertNotNull(accessCookie, "access_token cookie must be set");
        assertNotNull(refreshCookie, "refresh_token cookie must be set");
        assertEquals("access-tok", accessCookie.getValue());
        assertEquals("refresh-tok", refreshCookie.getValue());
        assertTrue(accessCookie.isHttpOnly());
        assertTrue(refreshCookie.isHttpOnly());
        assertEquals("/", accessCookie.getPath());
        assertEquals("/api/auth/", refreshCookie.getPath());
    }

    @Test
    void login_responseBody_doesNotContainTokens() {
        AuthRequest req = new AuthRequest();
        req.setUsername("bob");
        req.setPassword("password");
        when(authService.login(req)).thenReturn(new JwtResponse("access-tok", "refresh-tok"));

        MockHttpServletResponse response = new MockHttpServletResponse();
        ResponseEntity<?> result = authController.login(req, response);

        assertEquals(200, result.getStatusCode().value());
        assertFalse(result.getBody().toString().contains("access-tok"));
        assertFalse(result.getBody().toString().contains("refresh-tok"));
    }
```

Add the missing imports at the top of `AuthControllerTest.java`:

```java
import jakarta.servlet.http.Cookie;
import org.springframework.mock.web.MockHttpServletResponse;
```

- [ ] **Step 5: Run tests to verify they fail**

```bash
mvn test -Dtest=AuthControllerTest -q
```

Expected: compile error — `authController.login(req, response)` doesn't match the current single-argument `login(AuthRequest request)` signature.

- [ ] **Step 6: Add the cookie helper methods to `AuthController`**

This step only ADDS the two private helpers — it does not yet wire them into `login`/`register`/`refresh` (that's Tasks 2-4, one at a time, each independently testable). Add to `src/main/java/com/st4r4x/controller/AuthController.java`, replacing the existing private `errorResponse` method block with itself plus these two new methods:

```java
    private void setAuthCookies(HttpServletResponse response, String accessToken, String refreshToken) {
        Cookie accessCookie = new Cookie("access_token", accessToken);
        accessCookie.setHttpOnly(true);
        accessCookie.setSecure(AppConfig.isCookieSecure());
        accessCookie.setPath("/");
        accessCookie.setMaxAge((int) (AppConfig.getJwtAccessTokenExpirationMs() / 1000));
        accessCookie.setAttribute("SameSite", "Strict");
        response.addCookie(accessCookie);

        Cookie refreshCookie = new Cookie("refresh_token", refreshToken);
        refreshCookie.setHttpOnly(true);
        refreshCookie.setSecure(AppConfig.isCookieSecure());
        refreshCookie.setPath("/api/auth/");
        refreshCookie.setMaxAge((int) (AppConfig.getJwtRefreshTokenExpirationMs() / 1000));
        refreshCookie.setAttribute("SameSite", "Strict");
        response.addCookie(refreshCookie);
    }

    private void clearAuthCookies(HttpServletResponse response) {
        Cookie accessCookie = new Cookie("access_token", "");
        accessCookie.setHttpOnly(true);
        accessCookie.setSecure(AppConfig.isCookieSecure());
        accessCookie.setPath("/");
        accessCookie.setMaxAge(0);
        response.addCookie(accessCookie);

        Cookie refreshCookie = new Cookie("refresh_token", "");
        refreshCookie.setHttpOnly(true);
        refreshCookie.setSecure(AppConfig.isCookieSecure());
        refreshCookie.setPath("/api/auth/");
        refreshCookie.setMaxAge(0);
        response.addCookie(refreshCookie);
    }
```

Add the required imports at the top of `AuthController.java`:

```java
import jakarta.servlet.http.Cookie;
import jakarta.servlet.http.HttpServletResponse;

import com.st4r4x.config.AppConfig;
```

Now update `login` ONLY (the method the Step 4 tests target) to accept the injected response and call the new helper, replacing:

```java
    @PostMapping("/login")
    public ResponseEntity<?> login(@RequestBody AuthRequest request) {
        try {
            JwtResponse response = authService.login(request);
            return ResponseEntity.ok(response);
        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest().body(errorResponse(e));
        } catch (Exception e) {
            return ResponseEntity.internalServerError().body(errorResponse(e));
        }
    }
```

with:

```java
    @PostMapping("/login")
    public ResponseEntity<?> login(@RequestBody AuthRequest request, HttpServletResponse response) {
        try {
            JwtResponse tokens = authService.login(request);
            setAuthCookies(response, tokens.getAccessToken(), tokens.getRefreshToken());
            return ResponseEntity.ok(Map.of("status", "success"));
        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest().body(errorResponse(e));
        } catch (Exception e) {
            return ResponseEntity.internalServerError().body(errorResponse(e));
        }
    }
```

(`register` and `refresh` are updated in Tasks 2 and 3 respectively — this task only touches `login`, keeping this task's diff small and independently testable. Verify `JwtResponse` has `getAccessToken()`/`getRefreshToken()` getters — check `src/main/java/com/st4r4x/dto/JwtResponse.java` before writing this; if the getter names differ, use the actual names.)

- [ ] **Step 7: Run tests to verify they pass**

```bash
mvn test -Dtest=AuthControllerTest -q
```

Expected: `BUILD SUCCESS`. The two new tests pass. The pre-existing `login_returns200_onValidCredentials` test will now FAIL to compile (it calls `authController.login(req)` with one argument) — fix it in this same step by updating that one test to pass a `new MockHttpServletResponse()` as the second argument:

```java
    @Test
    void login_returns200_onValidCredentials() {
        AuthRequest req = new AuthRequest();
        req.setUsername("bob");
        req.setPassword("password");
        when(authService.login(req)).thenReturn(new JwtResponse("access", "refresh"));

        ResponseEntity<?> response = authController.login(req, new MockHttpServletResponse());

        assertEquals(200, response.getStatusCode().value());
    }
```

And `login_returns400_onInvalidCredentials`:

```java
    @Test
    void login_returns400_onInvalidCredentials() {
        AuthRequest req = new AuthRequest();
        req.setUsername("bob");
        req.setPassword("wrong");
        when(authService.login(req)).thenThrow(new IllegalArgumentException("Invalid credentials"));

        ResponseEntity<?> response = authController.login(req, new MockHttpServletResponse());

        assertEquals(400, response.getStatusCode().value());
    }
```

Re-run `mvn test -Dtest=AuthControllerTest -q` — expect `BUILD SUCCESS`, all tests pass.

- [ ] **Step 8: Run the full suite**

```bash
mvn test -q
```

Expected: `BUILD SUCCESS`. (`register` and `refresh` controller methods are untouched in this task, so their existing tests still pass unchanged.)

- [ ] **Step 9: Commit**

```bash
git add src/main/java/com/st4r4x/config/AppConfig.java src/main/resources/application.properties docker-compose.yml src/main/java/com/st4r4x/controller/AuthController.java src/test/java/com/st4r4x/controller/AuthControllerTest.java
git commit -m "feat(auth): add cookie helpers and migrate login to httpOnly cookies

setAuthCookies/clearAuthCookies on AuthController set access_token
(path /, 15 min) and refresh_token (path /api/auth/, 7 days) as
httpOnly+SameSite=Strict cookies. app.cookie.secure (default true,
false in docker-compose.yml for local HTTP dev) controls the Secure
flag. Only login() is wired so far — register()/refresh()/logout()
follow in later tasks. Response body no longer contains raw tokens."
```

---

### Task 2: Migrate `register` to cookies

**Files:**
- Modify: `src/main/java/com/st4r4x/controller/AuthController.java`
- Modify: `src/test/java/com/st4r4x/controller/AuthControllerTest.java`

**Interfaces:**
- Consumes: `setAuthCookies`/`clearAuthCookies` from Task 1.
- Produces: `POST /api/auth/register` now sets cookies instead of returning tokens in the body — same contract as `login` now has.

- [ ] **Step 1: Update the tests**

In `src/test/java/com/st4r4x/controller/AuthControllerTest.java`, update `register_returns200_onSuccess`:

```java
    @Test
    void register_returns200_onSuccess() {
        RegisterRequest req = new RegisterRequest();
        req.setUsername("alice");
        req.setEmail("alice@example.com");
        req.setPassword("Password123");
        when(authService.register(req)).thenReturn(new JwtResponse("access", "refresh"));

        MockHttpServletResponse response = new MockHttpServletResponse();
        ResponseEntity<?> result = authController.register(req, response);

        assertEquals(200, result.getStatusCode().value());
        assertNotNull(response.getCookie("access_token"));
        assertNotNull(response.getCookie("refresh_token"));
    }
```

Update `register_returns400_onIllegalArgument`:

```java
    @Test
    void register_returns400_onIllegalArgument() {
        RegisterRequest req = new RegisterRequest();
        req.setUsername("alice");
        when(authService.register(req)).thenThrow(new IllegalArgumentException("Username already exists"));

        ResponseEntity<?> response = authController.register(req, new MockHttpServletResponse());

        assertEquals(400, response.getStatusCode().value());
    }
```

Update `register_returns500_onUnexpectedError`:

```java
    @Test
    void register_returns500_onUnexpectedError() {
        RegisterRequest req = new RegisterRequest();
        when(authService.register(req)).thenThrow(new RuntimeException("DB unavailable"));

        ResponseEntity<?> response = authController.register(req, new MockHttpServletResponse());

        assertEquals(500, response.getStatusCode().value());
    }
```

- [ ] **Step 2: Run tests to verify they fail**

```bash
mvn test -Dtest=AuthControllerTest -q
```

Expected: compile error — `register(req, response)` doesn't match the current single-argument signature.

- [ ] **Step 3: Update the `register` method**

In `src/main/java/com/st4r4x/controller/AuthController.java`, replace:

```java
    @PostMapping("/register")
    public ResponseEntity<?> register(@RequestBody RegisterRequest request) {
        try {
            JwtResponse response = authService.register(request);
            return ResponseEntity.ok(response);
        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest().body(errorResponse(e));
        } catch (Exception e) {
            return ResponseEntity.internalServerError().body(errorResponse(e));
        }
    }
```

with:

```java
    @PostMapping("/register")
    public ResponseEntity<?> register(@RequestBody RegisterRequest request, HttpServletResponse response) {
        try {
            JwtResponse tokens = authService.register(request);
            setAuthCookies(response, tokens.getAccessToken(), tokens.getRefreshToken());
            return ResponseEntity.ok(Map.of("status", "success"));
        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest().body(errorResponse(e));
        } catch (Exception e) {
            return ResponseEntity.internalServerError().body(errorResponse(e));
        }
    }
```

- [ ] **Step 4: Run tests, then the full suite**

```bash
mvn test -Dtest=AuthControllerTest -q
mvn test -q
```

Expected: `BUILD SUCCESS` both times.

- [ ] **Step 5: Commit**

```bash
git add src/main/java/com/st4r4x/controller/AuthController.java src/test/java/com/st4r4x/controller/AuthControllerTest.java
git commit -m "feat(auth): migrate register to httpOnly cookies

Same pattern as login() from the previous commit — cookies instead
of tokens in the response body."
```

---

### Task 3: Migrate `refresh` to cookies (reads AND writes cookies)

**Files:**
- Modify: `src/main/java/com/st4r4x/controller/AuthController.java`
- Modify: `src/test/java/com/st4r4x/controller/AuthControllerTest.java`

**Interfaces:**
- Consumes: `setAuthCookies`/`clearAuthCookies` from Task 1.
- Produces: `POST /api/auth/refresh` now reads `refresh_token` from the request's cookies instead of a JSON body field, and sets new cookies on success instead of returning tokens in the body. `RefreshRequest` DTO becomes unused by this endpoint (still exists as a class — check for other usages before deciding whether to delete it; do not delete it as part of this task, that's a separate cleanup decision outside this plan's scope).

- [ ] **Step 1: Update the tests**

Replace `refresh_returns200_onValidToken`:

```java
    @Test
    void refresh_returns200_onValidToken() {
        when(authService.refresh("valid-refresh")).thenReturn(new JwtResponse("new-access", "new-refresh"));

        MockHttpServletRequest request = new MockHttpServletRequest();
        request.setCookies(new Cookie("refresh_token", "valid-refresh"));
        MockHttpServletResponse response = new MockHttpServletResponse();

        ResponseEntity<?> result = authController.refresh(request, response);

        assertEquals(200, result.getStatusCode().value());
        assertNotNull(response.getCookie("access_token"));
        assertEquals("new-access", response.getCookie("access_token").getValue());
    }
```

Replace `refresh_returns400_onExpiredOrInvalidToken`:

```java
    @Test
    void refresh_returns400_onExpiredOrInvalidToken() {
        when(authService.refresh("expired-token")).thenThrow(new IllegalArgumentException("Invalid refresh token"));

        MockHttpServletRequest request = new MockHttpServletRequest();
        request.setCookies(new Cookie("refresh_token", "expired-token"));

        ResponseEntity<?> response = authController.refresh(request, new MockHttpServletResponse());

        assertEquals(400, response.getStatusCode().value());
    }
```

Add one new test for the missing-cookie case (there was no equivalent before, since the old code took the token from the request body which was always structurally present even if empty):

```java
    @Test
    void refresh_returns400_whenNoRefreshTokenCookie() {
        MockHttpServletRequest request = new MockHttpServletRequest(); // no cookies set
        when(authService.refresh(null)).thenThrow(new IllegalArgumentException("refreshToken ne peut pas être null ou vide"));

        ResponseEntity<?> response = authController.refresh(request, new MockHttpServletResponse());

        assertEquals(400, response.getStatusCode().value());
    }
```

Add the missing import at the top of the file:

```java
import org.springframework.mock.web.MockHttpServletRequest;
```

- [ ] **Step 2: Run tests to verify they fail**

```bash
mvn test -Dtest=AuthControllerTest -q
```

Expected: compile error — `refresh(request, response)` doesn't match the current `refresh(RefreshRequest request)` signature.

- [ ] **Step 3: Update the `refresh` method**

Replace:

```java
    @PostMapping("/refresh")
    public ResponseEntity<?> refresh(@RequestBody RefreshRequest request) {
        try {
            JwtResponse response = authService.refresh(request.getRefreshToken());
            return ResponseEntity.ok(response);
        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest().body(errorResponse(e));
        } catch (Exception e) {
            return ResponseEntity.internalServerError().body(errorResponse(e));
        }
    }
```

with:

```java
    @PostMapping("/refresh")
    public ResponseEntity<?> refresh(HttpServletRequest request, HttpServletResponse response) {
        try {
            String refreshToken = extractCookie(request, "refresh_token");
            JwtResponse tokens = authService.refresh(refreshToken);
            setAuthCookies(response, tokens.getAccessToken(), tokens.getRefreshToken());
            return ResponseEntity.ok(Map.of("status", "success"));
        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest().body(errorResponse(e));
        } catch (Exception e) {
            return ResponseEntity.internalServerError().body(errorResponse(e));
        }
    }

    private String extractCookie(HttpServletRequest request, String name) {
        if (request.getCookies() == null) return null;
        for (Cookie cookie : request.getCookies()) {
            if (name.equals(cookie.getName())) return cookie.getValue();
        }
        return null;
    }
```

Add the missing import:

```java
import jakarta.servlet.http.HttpServletRequest;
```

`AuthService.refresh(String refreshToken)` already handles a `null` argument via its existing `ValidationUtil.requireNonEmpty(refreshToken, "refreshToken")` call (throws `IllegalArgumentException`) — no change needed there, confirmed by reading `src/main/java/com/st4r4x/service/AuthService.java` before writing this task.

- [ ] **Step 4: Run tests, then the full suite**

```bash
mvn test -Dtest=AuthControllerTest -q
mvn test -q
```

Expected: `BUILD SUCCESS` both times.

- [ ] **Step 5: Commit**

```bash
git add src/main/java/com/st4r4x/controller/AuthController.java src/test/java/com/st4r4x/controller/AuthControllerTest.java
git commit -m "feat(auth): migrate refresh to read/write httpOnly cookies

refresh() now reads refresh_token from the request cookie (path
/api/auth/) instead of a JSON body field, and sets fresh cookies on
success. RefreshRequest DTO is no longer used by this endpoint (left
in place — unrelated cleanup, out of scope for this task)."
```

---

### Task 4: Add `GET /api/auth/me` and `POST /api/auth/logout`

**Files:**
- Modify: `src/main/java/com/st4r4x/controller/AuthController.java`
- Modify: `src/test/java/com/st4r4x/controller/AuthControllerTest.java`

**Interfaces:**
- Consumes: `clearAuthCookies` from Task 1. Reads `SecurityContextHolder` the same way `UserController.getCurrentUser()` already does (confirmed pattern by reading that class before writing this task).
- Produces: `GET /api/auth/me` → `{"username": "...", "role": "..."}` for an authenticated caller, `401` for an unauthenticated one (handled by `SecurityConfig`'s existing `authenticationEntryPoint`, since this endpoint requires authentication — see Task 5 for the security-config change that makes this true). `POST /api/auth/logout` → clears both cookies, `{"status": "success"}`. Task 6 (`login.html`, `navbar.html`) calls both.

- [ ] **Step 1: Write the failing tests**

Add to `src/test/java/com/st4r4x/controller/AuthControllerTest.java`, in a new section after the cookie-helper tests added in Task 1 (before `checkUsername`):

```java
    // ── me / logout ──────────────────────────────────────────────────────────

    @Test
    void me_returnsUsernameAndRole_whenAuthenticated() {
        var auth = new org.springframework.security.authentication.UsernamePasswordAuthenticationToken(
            "alice", null,
            java.util.List.of(new org.springframework.security.core.authority.SimpleGrantedAuthority("ROLE_CUSTOMER")));
        org.springframework.security.core.context.SecurityContextHolder.getContext().setAuthentication(auth);

        try {
            ResponseEntity<?> response = authController.me();

            assertEquals(200, response.getStatusCode().value());
            assertEquals(Map.of("username", "alice", "role", "ROLE_CUSTOMER"), response.getBody());
        } finally {
            org.springframework.security.core.context.SecurityContextHolder.clearContext();
        }
    }

    @Test
    void logout_clearsCookies() {
        MockHttpServletResponse response = new MockHttpServletResponse();

        ResponseEntity<?> result = authController.logout(response);

        assertEquals(200, result.getStatusCode().value());
        assertEquals(0, response.getCookie("access_token").getMaxAge());
        assertEquals(0, response.getCookie("refresh_token").getMaxAge());
    }
```

(Fully-qualified class names are used inline above to avoid adding four more import lines for a two-test section — consistent with how `AuthController.java`'s own `errorResponse` helper already inlines an anonymous `Object` rather than adding a DTO; if this makes the test section hard to read, adding the four imports at the top is also acceptable, use your judgment on which is cleaner in the actual file.)

- [ ] **Step 2: Run tests to verify they fail**

```bash
mvn test -Dtest=AuthControllerTest -q
```

Expected: compile error — `me()` and `logout(response)` don't exist on `AuthController`.

- [ ] **Step 3: Implement both endpoints**

Add to `src/main/java/com/st4r4x/controller/AuthController.java`, after the `refresh` method and before `check-username`:

```java
    @GetMapping("/me")
    public ResponseEntity<?> me() {
        String username = org.springframework.security.core.context.SecurityContextHolder
            .getContext().getAuthentication().getName();
        String role = org.springframework.security.core.context.SecurityContextHolder
            .getContext().getAuthentication().getAuthorities().stream()
            .findFirst()
            .map(a -> a.getAuthority())
            .orElse(null);
        return ResponseEntity.ok(Map.of("username", username, "role", role));
    }

    @PostMapping("/logout")
    public ResponseEntity<?> logout(HttpServletResponse response) {
        clearAuthCookies(response);
        return ResponseEntity.ok(Map.of("status", "success"));
    }
```

(Use plain method calls, not the fully-qualified inline form shown in the test — add `import org.springframework.security.core.context.SecurityContextHolder;` at the top of `AuthController.java` for the production code, since this is permanent code, not a one-off test.)

- [ ] **Step 4: Run tests, then the full suite**

```bash
mvn test -Dtest=AuthControllerTest -q
mvn test -q
```

Expected: `BUILD SUCCESS` both times.

- [ ] **Step 5: Commit**

```bash
git add src/main/java/com/st4r4x/controller/AuthController.java src/test/java/com/st4r4x/controller/AuthControllerTest.java
git commit -m "feat(auth): add GET /api/auth/me and POST /api/auth/logout

/me replaces client-side JWT decoding (no longer possible once the
token is httpOnly) — returns {username, role} read from the security
context already populated by JwtAuthenticationFilter. /logout clears
both cookies, replacing the old localStorage.removeItem calls."
```

---

### Task 5: Migrate `JwtAuthenticationFilter` to read the cookie, update `SecurityConfig`

**Files:**
- Modify: `src/main/java/com/st4r4x/security/JwtAuthenticationFilter.java`
- Modify: `src/test/java/com/st4r4x/security/JwtAuthenticationFilterTest.java`
- Modify: `src/main/java/com/st4r4x/config/SecurityConfig.java`

**Interfaces:**
- Consumes: nothing from earlier tasks directly — this is the read-side counterpart to Tasks 1-4's write-side cookie work. Cookie name `access_token` must match exactly what Task 1's `setAuthCookies` writes.
- Produces: requests carrying a valid `access_token` cookie get authenticated the same way requests with a valid `Authorization: Bearer` header used to. `/api/users/**` (already `authenticated()` in `SecurityConfig`) now also protects the new `/api/auth/me` endpoint from Task 4 by adding a matcher for it.

- [ ] **Step 1: Update the filter test to use cookies instead of headers**

Rewrite `src/test/java/com/st4r4x/security/JwtAuthenticationFilterTest.java` in full — replace every test that used `request.addHeader("Authorization", "Bearer ...")` with `request.setCookies(new Cookie("access_token", ...))`:

```java
package com.st4r4x.security;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.mock.web.MockFilterChain;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;

import jakarta.servlet.http.Cookie;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.impl.DefaultClaims;

import java.util.HashMap;
import java.util.Map;

@ExtendWith(MockitoExtension.class)
class JwtAuthenticationFilterTest {

    @Mock
    private JwtService jwtService;

    private JwtAuthenticationFilter filter;

    @BeforeEach
    void setUp() {
        filter = new JwtAuthenticationFilter(jwtService);
    }

    @AfterEach
    void tearDown() {
        SecurityContextHolder.clearContext();
    }

    private static Claims claimsFor(String username, String role) {
        Map<String, Object> data = new HashMap<>();
        data.put("sub", username);
        if (role != null) {
            data.put("role", role);
        }
        return new DefaultClaims(data);
    }

    @Test
    void noAccessTokenCookie_doesNotAuthenticate() throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest();
        MockHttpServletResponse response = new MockHttpServletResponse();

        filter.doFilterInternal(request, response, new MockFilterChain());

        assertNull(SecurityContextHolder.getContext().getAuthentication());
        verifyNoInteractions(jwtService);
    }

    @Test
    void cookiesPresentButNoAccessTokenCookie_doesNotAuthenticate() throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.setCookies(new Cookie("some_other_cookie", "value"));
        MockHttpServletResponse response = new MockHttpServletResponse();

        filter.doFilterInternal(request, response, new MockFilterChain());

        assertNull(SecurityContextHolder.getContext().getAuthentication());
        verifyNoInteractions(jwtService);
    }

    @Test
    void validAccessTokenCookie_setsAuthenticationWithUsernameAndRole() throws Exception {
        when(jwtService.getClaimsIfValid("valid-token")).thenReturn(claimsFor("alice", "ROLE_ADMIN"));

        MockHttpServletRequest request = new MockHttpServletRequest();
        request.setCookies(new Cookie("access_token", "valid-token"));
        MockHttpServletResponse response = new MockHttpServletResponse();

        filter.doFilterInternal(request, response, new MockFilterChain());

        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        assertNotNull(auth);
        assertEquals("alice", auth.getPrincipal());
        assertTrue(auth.getAuthorities().stream()
            .anyMatch(a -> a.getAuthority().equals("ROLE_ADMIN")));
    }

    @Test
    void validTokenWithoutRoleClaim_setsAuthenticationWithNoAuthorities() throws Exception {
        when(jwtService.getClaimsIfValid("valid-token")).thenReturn(claimsFor("bob", null));

        MockHttpServletRequest request = new MockHttpServletRequest();
        request.setCookies(new Cookie("access_token", "valid-token"));
        MockHttpServletResponse response = new MockHttpServletResponse();

        filter.doFilterInternal(request, response, new MockFilterChain());

        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        assertNotNull(auth);
        assertEquals("bob", auth.getPrincipal());
        assertTrue(auth.getAuthorities().isEmpty());
    }

    @Test
    void invalidOrExpiredTokenCookie_doesNotAuthenticate() throws Exception {
        when(jwtService.getClaimsIfValid("bad-token")).thenReturn(null);

        MockHttpServletRequest request = new MockHttpServletRequest();
        request.setCookies(new Cookie("access_token", "bad-token"));
        MockHttpServletResponse response = new MockHttpServletResponse();

        filter.doFilterInternal(request, response, new MockFilterChain());

        assertNull(SecurityContextHolder.getContext().getAuthentication());
    }

    @Test
    void filterChainAlwaysContinues_regardlessOfTokenValidity() throws Exception {
        when(jwtService.getClaimsIfValid("bad-token")).thenReturn(null);

        MockHttpServletRequest request = new MockHttpServletRequest();
        request.setCookies(new Cookie("access_token", "bad-token"));
        MockHttpServletResponse response = new MockHttpServletResponse();
        MockFilterChain chain = new MockFilterChain();

        filter.doFilterInternal(request, response, chain);

        assertNotNull(chain.getRequest(), "Filter must always delegate to the chain, even without authentication");
    }
}
```

- [ ] **Step 2: Run tests to verify they fail**

```bash
mvn test -Dtest=JwtAuthenticationFilterTest -q
```

Expected: test failures — the filter still reads the `Authorization` header, so a cookie-only request produces no authentication where the test expects one (and vice versa for the "no cookie" tests, which may accidentally pass since both old and new code produce no authentication with no input — the important RED signal is on the "valid token" tests).

- [ ] **Step 3: Update `JwtAuthenticationFilter`**

Replace the full contents of `src/main/java/com/st4r4x/security/JwtAuthenticationFilter.java`:

```java
package com.st4r4x.security;

import java.io.IOException;
import java.util.Collections;
import java.util.List;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.Cookie;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.web.authentication.WebAuthenticationDetailsSource;
import org.springframework.web.filter.OncePerRequestFilter;

public class JwtAuthenticationFilter extends OncePerRequestFilter {

    private final JwtService jwtUtil;

    public JwtAuthenticationFilter(JwtService jwtUtil) {
        this.jwtUtil = jwtUtil;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain filterChain)
            throws ServletException, IOException {

        String token = extractAccessTokenCookie(request);
        if (token != null) {
            io.jsonwebtoken.Claims claims = jwtUtil.getClaimsIfValid(token);
            if (claims != null) {
                String username = claims.getSubject();
                String role = claims.get("role", String.class);
                List<GrantedAuthority> authorities = role != null
                        ? Collections.singletonList(new SimpleGrantedAuthority(role))
                        : Collections.emptyList();
                UsernamePasswordAuthenticationToken authentication =
                        new UsernamePasswordAuthenticationToken(username, null, authorities);
                authentication.setDetails(new WebAuthenticationDetailsSource().buildDetails(request));
                SecurityContextHolder.getContext().setAuthentication(authentication);
            }
        }

        filterChain.doFilter(request, response);
    }

    private String extractAccessTokenCookie(HttpServletRequest request) {
        if (request.getCookies() == null) return null;
        for (Cookie cookie : request.getCookies()) {
            if ("access_token".equals(cookie.getName())) return cookie.getValue();
        }
        return null;
    }
}
```

- [ ] **Step 4: Run tests to verify they pass**

```bash
mvn test -Dtest=JwtAuthenticationFilterTest -q
```

Expected: `BUILD SUCCESS`, all 6 tests pass.

- [ ] **Step 5: Update `SecurityConfig` to protect the new `/api/auth/me` endpoint**

In `src/main/java/com/st4r4x/config/SecurityConfig.java`, the current matcher `.requestMatchers("/api/auth/**").permitAll()` would make `/api/auth/me` public, which is wrong — `me()` reads `SecurityContextHolder.getContext().getAuthentication()` and calls `.getName()` on it, which throws `NullPointerException` if there's no authentication. Add a specific matcher for `/api/auth/me` BEFORE the general `/api/auth/**` matcher (matcher order matters — first match wins, same reasoning already documented in this file for `/api/reports/stats` vs `/api/reports/**`):

Replace:

```java
                // Public: auth endpoints, read-only NYC data, Swagger
                .requestMatchers("/api/auth/**").permitAll()
```

with:

```java
                // Public: auth endpoints, read-only NYC data, Swagger
                // MUST precede the /api/auth/** wildcard below (first match wins)
                .requestMatchers("/api/auth/me").authenticated()
                .requestMatchers("/api/auth/**").permitAll()
```

- [ ] **Step 6: Run the full suite**

```bash
mvn test -q
```

Expected: `BUILD SUCCESS`. Check specifically for any `SecurityConfigTest` that asserts on the exact matcher list/order — if one exists, read it and update it to reflect the new `/api/auth/me` matcher before proceeding; do not skip this check.

- [ ] **Step 7: Commit**

```bash
git add src/main/java/com/st4r4x/security/JwtAuthenticationFilter.java src/test/java/com/st4r4x/security/JwtAuthenticationFilterTest.java src/main/java/com/st4r4x/config/SecurityConfig.java
git commit -m "feat(auth): read access_token from cookie in JwtAuthenticationFilter

Replaces the Authorization: Bearer header read with a cookie read.
/api/auth/me is now explicitly authenticated() (must precede the
general /api/auth/** permitAll matcher, same ordering pattern already
used for /api/reports/stats vs /api/reports/**)."
```

---

### Task 6: Centralize `fetchWithAuth` in `fragments/ux-utils.html`, remove the 5 duplicates

**Files:**
- Modify: `src/main/resources/templates/fragments/ux-utils.html`
- Modify: `src/main/resources/templates/index.html`
- Modify: `src/main/resources/templates/dashboard.html`
- Modify: `src/main/resources/templates/restaurant.html`
- Modify: `src/main/resources/templates/admin.html`
- Modify: `src/main/resources/templates/my-bookmarks.html`
- Modify: `src/main/resources/templates/profile.html`

**Interfaces:**
- Consumes: `POST /api/auth/refresh` (Task 3) for the refresh-on-401 retry.
- Produces: `window.fetchWithAuth(url, options)` — global function available on every page (since `fragments/ux-utils.html` is loaded everywhere via `th:replace`). Task 7 templates that still reference `fetchWithAuth` after this task's removals continue to work because the global one is a drop-in replacement with the same call signature.

- [ ] **Step 1: Add `window.fetchWithAuth` to `fragments/ux-utils.html`**

In `src/main/resources/templates/fragments/ux-utils.html`, DELETE the entire "Proactive JWT refresh" IIFE block (the `(function scheduleTokenRefresh() { ... })();` block, currently lines 61-105 — from the comment `// Proactive JWT refresh...` through the closing `})();` right before `window.showToast = ...`). This is impossible to keep: it reads the access token's `exp` claim from `localStorage`, which no longer holds any token.

Replace that deleted block with:

```javascript
  // Reactive refresh-on-401 — the cookie-based replacement for the old
  // proactive exp-based scheduler (which required reading the JWT client-side,
  // impossible now that both tokens are httpOnly).
  window.fetchWithAuth = function(url, options) {
    options = options || {};
    options.credentials = 'same-origin';
    return fetch(url, options).then(function(response) {
      if (response.status !== 401) return response;
      return fetch('/api/auth/refresh', { method: 'POST', credentials: 'same-origin' })
        .then(function(refreshResponse) {
          if (!refreshResponse.ok) {
            if (window.location.pathname !== '/login') window.location.href = '/login';
            return response;
          }
          return fetch(url, options);
        });
    });
  };
```

(Verify the exact line range to delete by re-reading the file at edit time — the block starts at the comment `// Proactive JWT refresh — runs on every page load and schedules ahead-of-time renewal` and ends at the `})();` immediately before the blank line that precedes `window.showToast = function(...)`.)

- [ ] **Step 2: Remove the local `fetchWithAuth` from `index.html`**

In `src/main/resources/templates/index.html`, the script block currently starts with:

```javascript
  <script>
    var token = localStorage.getItem('accessToken');
    if (!token) { window.location.href = '/login'; }

    function fetchWithAuth(url, options) {
      options = options || {};
      return fetch(url, Object.assign({}, options, { headers: Object.assign({}, options.headers || {}, { 'Authorization': 'Bearer ' + token }) }));
    }

    function gradeBadgeHtml(grade) {
```

Replace those first lines (removing the `token` variable, the `if (!token)` guard, and the local `fetchWithAuth` definition — the global one from `ux-utils.html` takes over) with just:

```javascript
  <script>
    function gradeBadgeHtml(grade) {
```

(This page is reachable by both authenticated and anonymous users per `ViewController.index()` — it returns `"landing"` for anonymous, `"index"` only when `auth != null`, so the removed `if (!token) { window.location.href = '/login'; }` guard was likely already dead weight for this specific template even before this migration; removing it here is in scope since it directly depended on the `token` variable being deleted. If unsure whether this changes behavior, note it in the task report as DONE_WITH_CONCERNS rather than guessing.)

- [ ] **Step 3: Remove the local `fetchWithAuth`/auth-guard from `dashboard.html`**

In `src/main/resources/templates/dashboard.html`, replace:

```javascript
<script>
// ── Auth guard (client-side — JWT is in localStorage, not HTTP headers) ────
(function() {
    const token = localStorage.getItem('accessToken');
    if (!token) { window.location.href = '/login'; return; }
    try {
        const payload = JSON.parse(atob(token.split('.')[1]));
        if (payload.role !== 'ROLE_CONTROLLER') {
            window.location.href = '/';
        }
    } catch { window.location.href = '/login'; }
})();

// ── Auth utilities ─────────────────────────────────────────────────────────
function getAuthHeaders() {
    const token = localStorage.getItem("accessToken");
    if (!token) return { "Content-Type": "application/json" };
    return { "Content-Type": "application/json", Authorization: "Bearer " + token };
}
function handleFetchErrorResponse(response) {
    if (response.status === 401 || response.status === 403) {
        localStorage.removeItem("accessToken");
        localStorage.removeItem("refreshToken");
        window.location.href = "/login";
        throw new Error("Unauthorized");
    }
    return response;
}
function fetchWithAuth(url, options = {}) {
    options.headers = { ...getAuthHeaders(), ...(options.headers || {}) };
    return fetch(url, options).then(handleFetchErrorResponse);
}
```

with:

```javascript
<script>
// ── Auth guard — server-side role check via GET /api/auth/me ──────────────
(function() {
    fetch('/api/auth/me', { credentials: 'same-origin' })
        .then(function(response) {
            if (!response.ok) { window.location.href = '/login'; return null; }
            return response.json();
        })
        .then(function(data) {
            if (data && data.role !== 'ROLE_CONTROLLER') {
                window.location.href = '/';
            }
        });
})();
```

(The multipart photo-upload code further down this file, around the comment `// CRITICAL: Do NOT use fetchWithAuth() here.`, currently reads `localStorage.getItem('accessToken')` to build a manual `Authorization` header for a `fetch` call that can't use `fetchWithAuth` due to a `Content-Type` conflict — this is addressed separately in Task 7, not here, since it's a different code path than the `fetchWithAuth` removal this task focuses on.)

- [ ] **Step 4: Remove the local `getAuthHeaders`/`handleFetchErrorResponse`/`fetchWithAuth` from `restaurant.html`**

In `src/main/resources/templates/restaurant.html`, find and remove the `getAuthHeaders()`, `handleFetchErrorResponse()`, and `fetchWithAuth()` function definitions (currently around lines 136-158). Also find the `handleBookmarkClick()` function's guard (around line 177) that reads `if (!localStorage.getItem('accessToken')) { window.location.href = '/login'; return; }` and the bookmark-loading guard around line 492 (`if (localStorage.getItem('accessToken')) { ... }`) — both need a cookie-compatible replacement. Since there's no synchronous way to check "is the user logged in" anymore (the cookie is invisible to JS), replace both guards with an async check via `GET /api/auth/me`:

For `handleBookmarkClick()`, replace:

```javascript
      function handleBookmarkClick() {
        if (!localStorage.getItem('accessToken')) {
          window.location.href = '/login';
          return;
        }
```

with:

```javascript
      function handleBookmarkClick() {
        fetchWithAuth('/api/auth/me').then(function(response) {
          if (!response.ok) { window.location.href = '/login'; return; }
```

(this changes the function's control flow to be async — read the rest of `handleBookmarkClick()`'s body in the actual file before editing, and nest the remaining logic inside this `.then()` callback, closing the added callback and the function's original closing brace appropriately; the exact remaining body depends on what's currently there, which must be read at edit time rather than assumed).

For the bookmark-loading guard around line 492, replace:

```javascript
          // Bookmark toggle state (only if logged in)
          if (localStorage.getItem('accessToken')) {
            fetchWithAuth('/api/users/me/bookmarks')
```

with:

```javascript
          // Bookmark toggle state (only if logged in) — fetchWithAuth's 401 handling
          // covers the anonymous case: /api/users/me/bookmarks returns 401 if unauthenticated,
          // fetchWithAuth's refresh-then-redirect logic handles it, no client-side pre-check needed
          {
            fetchWithAuth('/api/users/me/bookmarks')
```

(The bare `{ }` block preserves the existing indentation/closing-brace structure without needing to hunt down and remove a matching `}` elsewhere in a large file — confirm this is syntactically equivalent by reading the surrounding code before committing; if the enclosing structure makes this awkward, removing the guard entirely and its matching closing brace is equally correct, use judgment based on what's actually there.)

- [ ] **Step 5: Remove the local auth guard/`fetchWithAuth` from `admin.html`**

In `src/main/resources/templates/admin.html`, replace:

```javascript
<script>
// Admin auth guard
(function() {
    var token = localStorage.getItem('accessToken');
    if (!token) { window.location.href = '/login'; return; }
    try {
        var payload = JSON.parse(atob(token.split('.')[1]));
        if (payload.role !== 'ROLE_ADMIN') { window.location.href = '/'; }
    } catch (e) { window.location.href = '/login'; }
})();

function getAuthHeaders() {
    var token = localStorage.getItem('accessToken');
    return { 'Content-Type': 'application/json', 'Authorization': token ? 'Bearer ' + token : '' };
}
function handleFetchErrorResponse(response) {
    if (response.status === 401 || response.status === 403) {
        localStorage.removeItem('accessToken');
        localStorage.removeItem('refreshToken');
        window.location.href = '/login';
        throw new Error('Unauthorized');
    }
    return response;
}
function fetchWithAuth(url, options) {
    options = options || {};
    options.headers = Object.assign({}, getAuthHeaders(), options.headers || {});
    return fetch(url, options).then(handleFetchErrorResponse);
}
```

with:

```javascript
<script>
// Admin auth guard — server-side role check via GET /api/auth/me
(function() {
    fetch('/api/auth/me', { credentials: 'same-origin' })
        .then(function(response) {
            if (!response.ok) { window.location.href = '/login'; return null; }
            return response.json();
        })
        .then(function(data) {
            if (data && data.role !== 'ROLE_ADMIN') { window.location.href = '/'; }
        });
})();
```

- [ ] **Step 6: Remove the local `getAuthHeaders`/`handleFetchErrorResponse`/`fetchWithAuth` from `my-bookmarks.html`**

In `src/main/resources/templates/my-bookmarks.html`, remove the `getAuthHeaders()`, `handleFetchErrorResponse()`, and `fetchWithAuth()` definitions (currently around lines 176-199). Find the `loadBookmarks()` guard around line 286 (`if (!localStorage.getItem('accessToken')) { window.location.href = '/login'; return; }`) and replace it the same way as Task 6 Step 4's `handleBookmarkClick()` pattern — read the actual current body of `loadBookmarks()` in the file before editing, and restructure its guard to check via `fetchWithAuth('/api/auth/me')` asynchronously rather than a synchronous `localStorage` read.

- [ ] **Step 7: Remove the local `fetchWithAuth` from `profile.html`**

In `src/main/resources/templates/profile.html`, replace:

```javascript
    var token = localStorage.getItem('accessToken');
    if (!token) { window.location.href = '/login'; }

    function fetchWithAuth(url, options) {
      options = options || {};
      return fetch(url, Object.assign({}, options, { headers: Object.assign({}, options.headers || {}, { 'Authorization': 'Bearer ' + token }) }));
    }
```

with:

```javascript
    fetch('/api/auth/me', { credentials: 'same-origin' })
      .then(function(response) { if (!response.ok) window.location.href = '/login'; });
```

- [ ] **Step 8: Manual verification (no JS test suite for this project's frontend)**

This task touches enough templates that a full `mvn test` pass does not prove the frontend still works — none of these changes are covered by JUnit. After this task's edits, start the app (`docker compose up -d --build`) and manually verify, for EACH of the 6 modified templates:

1. `index.html` (`/` while logged in): loads without a console error, bookmarks strip renders.
2. `dashboard.html` (`/dashboard` as a CONTROLLER): loads, redirects non-controllers to `/`, redirects anonymous users to `/login`.
3. `restaurant.html` (`/restaurant/{any-valid-camis}`): loads for anonymous AND logged-in users, bookmark button works when logged in.
4. `admin.html` (`/admin` as ADMIN): loads, redirects non-admins to `/`, redirects anonymous users to `/login`.
5. `my-bookmarks.html` (`/my-bookmarks`): loads for a logged-in user, redirects anonymous users to `/login`.
6. `profile.html` (`/profile`): loads for a logged-in user.

Do this AFTER Task 8 (login/navbar) is also done, since none of these pages can be reached in a logged-in state until login itself sets the cookie correctly — note in this task's report that full verification is deferred to Task 8's manual check, and this task's own check is limited to confirming no JS syntax errors via browser devtools console on each page (reachable in an anonymous/logged-out state at minimum).

- [ ] **Step 9: Commit**

```bash
git add src/main/resources/templates/fragments/ux-utils.html src/main/resources/templates/index.html src/main/resources/templates/dashboard.html src/main/resources/templates/restaurant.html src/main/resources/templates/admin.html src/main/resources/templates/my-bookmarks.html src/main/resources/templates/profile.html
git commit -m "feat(auth): centralize fetchWithAuth in ux-utils.html

Removes 5 duplicated per-template fetchWithAuth implementations (all
read the Authorization header from localStorage, none of which exists
anymore). The global window.fetchWithAuth relies on the cookie riding
along automatically and adds reactive refresh-on-401. Auth guards that
decoded the JWT client-side now call GET /api/auth/me instead."
```

---

### Task 7: Update `dashboard.html`'s multipart photo upload (separate code path)

**Files:**
- Modify: `src/main/resources/templates/dashboard.html`

**Interfaces:**
- Consumes: nothing new — the cookie rides along on this `fetch` call automatically, same as any other same-origin request.
- Produces: nothing consumed by other tasks.

- [ ] **Step 1: Remove the manual Authorization header from the photo upload**

In `src/main/resources/templates/dashboard.html`, find the block starting with the comment `// CRITICAL: Do NOT use fetchWithAuth() here.` (currently around line 462). Read the current exact code around it before editing — it should look like:

```javascript
// CRITICAL: Do NOT use fetchWithAuth() here.
// fetchWithAuth injects Content-Type: application/json which breaks multipart boundary.
    const formData = new FormData();
    formData.append('file', file);  // param name must match @RequestParam("file")
    const token = localStorage.getItem('accessToken');
    fetch('/api/reports/' + reportId + '/photo', {
        method: 'POST',
        headers: { Authorization: 'Bearer ' + token },
```

Replace the `const token = ...` line and the `headers: { Authorization: ... }` with a plain `fetch` call that relies on the cookie:

```javascript
// CRITICAL: Do NOT use fetchWithAuth() here.
// fetchWithAuth injects Content-Type: application/json which breaks multipart boundary.
// The auth cookie rides along automatically on this same-origin request — no manual header needed.
    const formData = new FormData();
    formData.append('file', file);  // param name must match @RequestParam("file")
    fetch('/api/reports/' + reportId + '/photo', {
        method: 'POST',
        credentials: 'same-origin',
```

(Read the rest of that `fetch` call's options object in the actual file before editing — there may be a `body: formData` line and `.then(...)` chain immediately after the shown snippet that must be preserved unchanged; this step only removes the `Authorization` header and the now-dead `token` variable.)

- [ ] **Step 2: Manual verification**

With the app running, log in as a CONTROLLER, open a report in the dashboard, upload a photo. Expected: upload succeeds (same as before this migration) with no `Authorization` header sent — check the browser's Network tab to confirm the request still succeeds and no `Authorization` header is present, only the `Cookie` header.

- [ ] **Step 3: Commit**

```bash
git add src/main/resources/templates/dashboard.html
git commit -m "fix(auth): rely on cookie for photo upload auth, drop manual header

The multipart upload couldn't use the new global fetchWithAuth
(Content-Type conflict, unchanged from before), but no longer needs
a manually-attached Authorization header either — the httpOnly cookie
rides along automatically on this same-origin request."
```

---

### Task 8: Migrate `login.html` and `fragments/navbar.html`

**Files:**
- Modify: `src/main/resources/templates/login.html`
- Modify: `src/main/resources/templates/fragments/navbar.html`

**Interfaces:**
- Consumes: `POST /api/auth/login`, `POST /api/auth/register` (Tasks 1-2, no longer return tokens in body), `GET /api/auth/me` (Task 4), `POST /api/auth/logout` (Task 4).
- Produces: nothing consumed by other tasks — final piece of the login/logout UX.

- [ ] **Step 1: Update `login.html`'s "redirect if already logged in" check**

Replace:

```javascript
      // Redirect if already logged in
      (function() {
        const t = localStorage.getItem("accessToken");
        if (!t) return;
        try {
          const role = JSON.parse(atob(t.split(".")[1])).role;
          window.location.href = role === "ROLE_CONTROLLER" ? "/dashboard" : role === "ROLE_ADMIN" ? "/admin" : "/";
        } catch { window.location.href = "/"; }
      })();
```

with:

```javascript
      // Redirect if already logged in
      (function() {
        fetch("/api/auth/me", { credentials: "same-origin" })
          .then((res) => (res.ok ? res.json() : null))
          .then((data) => {
            if (!data) return;
            window.location.href = data.role === "ROLE_CONTROLLER" ? "/dashboard" : data.role === "ROLE_ADMIN" ? "/admin" : "/";
          })
          .catch(() => {});
      })();
```

- [ ] **Step 2: Update the login handler's success path**

Replace:

```javascript
          .then((result) => {
            if (result.status === 200 && result.body.accessToken) {
              localStorage.setItem("accessToken", result.body.accessToken);
              localStorage.setItem("refreshToken", result.body.refreshToken);
              try {
                const role = JSON.parse(atob(result.body.accessToken.split(".")[1])).role;
                window.location.href = role === "ROLE_CONTROLLER" ? "/dashboard" : role === "ROLE_ADMIN" ? "/admin" : "/";
              } catch { window.location.href = "/"; }
            } else {
              errorEl.style.display = "block";
              errorEl.textContent = result.body.message || "Invalid username or password.";
              btn.disabled = false;
              btn.textContent = "Sign In";
            }
          })
```

with:

```javascript
          .then((result) => {
            if (result.status === 200) {
              fetch("/api/auth/me", { credentials: "same-origin" })
                .then((res) => res.json())
                .then((data) => {
                  window.location.href = data.role === "ROLE_CONTROLLER" ? "/dashboard" : data.role === "ROLE_ADMIN" ? "/admin" : "/";
                })
                .catch(() => { window.location.href = "/"; });
            } else {
              errorEl.style.display = "block";
              errorEl.textContent = result.body.message || "Invalid username or password.";
              btn.disabled = false;
              btn.textContent = "Sign In";
            }
          })
```

Also update the `fetch("/api/auth/login", ...)` call itself to add `credentials: "same-origin"` (needed so the browser accepts the `Set-Cookie` response and includes cookies on the immediately-following `/api/auth/me` call):

```javascript
        fetch("/api/auth/login", {
          method: "POST",
          credentials: "same-origin",
          headers: { "Content-Type": "application/json" },
          body: JSON.stringify({ username, password }),
        })
```

- [ ] **Step 3: Update the register handler's success path**

Replace:

```javascript
          .then((result) => {
            if (result.status === 200 && result.body.accessToken) {
              localStorage.setItem("accessToken", result.body.accessToken);
              localStorage.setItem("refreshToken", result.body.refreshToken);
              window.location.href = "/";
            } else {
              errorEl.style.display = "block";
              errorEl.textContent = result.body.message || "Could not create account.";
              btn.disabled = false;
              btn.textContent = "Create Account";
            }
          })
```

with:

```javascript
          .then((result) => {
            if (result.status === 200) {
              window.location.href = "/";
            } else {
              errorEl.style.display = "block";
              errorEl.textContent = result.body.message || "Could not create account.";
              btn.disabled = false;
              btn.textContent = "Create Account";
            }
          })
```

Also add `credentials: "same-origin"` to the `fetch("/api/auth/register", ...)` call, same reasoning as Step 2:

```javascript
        fetch("/api/auth/register", {
          method: "POST",
          credentials: "same-origin",
          headers: { "Content-Type": "application/json" },
          body: JSON.stringify(body),
        })
```

- [ ] **Step 4: Update `fragments/navbar.html`**

Replace the entire IIFE in `src/main/resources/templates/fragments/navbar.html`:

```javascript
<script>
  (function() {
    var t = localStorage.getItem('accessToken');
    var el = document.getElementById('nav-auth');
    if (!t) {
      el.innerHTML = '<a href="/login" class="btn-signin">Sign In</a>';
    } else {
      try {
        var payload = JSON.parse(atob(t.split('.')[1]));
        var sub = payload.sub;
        // sub is trusted (from our own JWT) — escaping for safety
        var safeUser = sub.replace(/</g,'&lt;').replace(/>/g,'&gt;');
        el.innerHTML = '<a href="/profile" style="font-size:0.85em;color:#444;text-decoration:none;font-weight:600">' + safeUser + '</a>'
          + ' <button onclick="(function(){localStorage.removeItem(\'accessToken\');localStorage.removeItem(\'refreshToken\');window.location.href=\'/login\';})()" '
          + 'style="font-size:0.8em;font-weight:600;letter-spacing:0.04em;background:none;border:1px solid #ddd;color:#666;padding:5px 12px;border-radius:2px;cursor:pointer;text-transform:uppercase">Sign Out</button>';
        document.getElementById('nav-bookmarks').style.display = 'block';
        if (payload.role === 'ROLE_CONTROLLER') {
          document.getElementById('nav-dashboard').style.display = 'block';
          document.getElementById('nav-uncontrolled').style.display = 'block';
        }
        if (payload.role === 'ROLE_ADMIN') {
          document.getElementById('nav-admin').style.display = 'block';
        }
      } catch(e) {
        el.innerHTML = '<a href="/login" class="btn-signin">Sign In</a>';
      }
    }
    document.querySelectorAll('nav a[data-nav]').forEach(function(link) {
      if (window.location.pathname === link.getAttribute('data-nav')) {
        link.classList.add('active');
      }
    });
  })();
</script>
```

with:

```javascript
<script>
  function doSignOut() {
    fetch('/api/auth/logout', { method: 'POST', credentials: 'same-origin' })
      .then(function() { window.location.href = '/login'; })
      .catch(function() { window.location.href = '/login'; });
  }
  (function() {
    var el = document.getElementById('nav-auth');
    fetch('/api/auth/me', { credentials: 'same-origin' })
      .then(function(response) { return response.ok ? response.json() : null; })
      .then(function(data) {
        if (!data) {
          el.innerHTML = '<a href="/login" class="btn-signin">Sign In</a>';
          return;
        }
        var sub = data.username;
        // sub is trusted (server-controlled response) — escaping for safety
        var safeUser = sub.replace(/</g,'&lt;').replace(/>/g,'&gt;');
        el.innerHTML = '<a href="/profile" style="font-size:0.85em;color:#444;text-decoration:none;font-weight:600">' + safeUser + '</a>'
          + ' <button onclick="doSignOut()" '
          + 'style="font-size:0.8em;font-weight:600;letter-spacing:0.04em;background:none;border:1px solid #ddd;color:#666;padding:5px 12px;border-radius:2px;cursor:pointer;text-transform:uppercase">Sign Out</button>';
        document.getElementById('nav-bookmarks').style.display = 'block';
        if (data.role === 'ROLE_CONTROLLER') {
          document.getElementById('nav-dashboard').style.display = 'block';
          document.getElementById('nav-uncontrolled').style.display = 'block';
        }
        if (data.role === 'ROLE_ADMIN') {
          document.getElementById('nav-admin').style.display = 'block';
        }
      })
      .catch(function() {
        el.innerHTML = '<a href="/login" class="btn-signin">Sign In</a>';
      });
    document.querySelectorAll('nav a[data-nav]').forEach(function(link) {
      if (window.location.pathname === link.getAttribute('data-nav')) {
        link.classList.add('active');
      }
    });
  })();
</script>
```

- [ ] **Step 5: Manual verification — full end-to-end flow**

With the app running (`docker compose up -d --build`, ensure `APP_COOKIE_SECURE=false` is picked up per Task 1):

1. Go to `/login`, register a new account. Expected: cookies set (check browser devtools → Application → Cookies → both `access_token` and `refresh_token` present, `HttpOnly` column checked), redirected to `/`.
2. Confirm navbar shows the username and a working "Sign Out" button, not "Sign In".
3. Click "Sign Out". Expected: cookies cleared (check devtools again), redirected to `/login`.
4. Log back in with the same account. Expected: same cookie behavior, correct redirect based on role.
5. Navigate to `/profile`, `/my-bookmarks` (as CUSTOMER), `/dashboard` (should redirect away — CUSTOMER isn't CONTROLLER), `/admin` (should redirect away).
6. If a CONTROLLER or ADMIN test account exists, repeat step 5's navigation logged in as each role and confirm the correct pages are reachable and the wrong ones redirect.
7. Open browser devtools → Application → Local Storage → confirm `accessToken`/`refreshToken` keys are ABSENT (not just empty) after a fresh login — proves nothing fell back to localStorage anywhere.
8. To verify the refresh-on-401 path specifically: this requires the access token to actually expire (15 min) or manually deleting the `access_token` cookie via devtools while keeping `refresh_token` — delete just `access_token`, then trigger any authenticated fetch (e.g. reload `/profile`); expected: the request transparently succeeds after an automatic `/api/auth/refresh` call (visible in the Network tab as a `POST /api/auth/refresh` immediately followed by a retry of the original request), no visible error to the user, no redirect to `/login`.

- [ ] **Step 6: Commit**

```bash
git add src/main/resources/templates/login.html src/main/resources/templates/fragments/navbar.html
git commit -m "feat(auth): migrate login.html and navbar.html to cookie-based auth

login.html no longer reads/writes localStorage or decodes the JWT —
role-based redirect comes from GET /api/auth/me. navbar.html's Sign
Out button calls POST /api/auth/logout instead of clearing
localStorage. Full end-to-end flow (register/login/navbar/logout)
manually verified in the dev container."
```

---

### Task 9: Update `inspection-map.html`'s role check, sweep for any remaining `localStorage` auth references

**Files:**
- Modify: `src/main/resources/templates/inspection-map.html`

**Interfaces:**
- Consumes: `GET /api/auth/me` (Task 4).
- Produces: nothing consumed by other tasks — final cleanup task.

- [ ] **Step 1: Update `inspection-map.html`'s dashboard-link visibility check**

Read the current code around the comment `// Show dashboard link for controllers` (currently around line 201) in `src/main/resources/templates/inspection-map.html` in full before editing — it currently looks like:

```javascript
        // Show dashboard link for controllers
        try {
          const t = localStorage.getItem('accessToken');
          if (t) {
            const payload = JSON.parse(atob(t.split('.')[1]));
            if (payload.role === 'ROLE_CONTROLLER') {
```

Replace with an async check via `/api/auth/me`, restructuring the surrounding code to fit inside the new `.then()` callback (read the rest of the current `if (payload.role === 'ROLE_CONTROLLER') { ... }` block's body in the actual file before editing, since its exact contents determine how to nest it):

```javascript
        // Show dashboard link for controllers
        fetch('/api/auth/me', { credentials: 'same-origin' })
          .then((response) => (response.ok ? response.json() : null))
          .then((data) => {
            if (data && data.role === 'ROLE_CONTROLLER') {
```

(Close the added `.then()` callback and its arrow function appropriately after the existing conditional body, replacing the old `try`/`catch` wrapper — the exact closing braces depend on what the rest of that block currently contains, which must be read at edit time.)

- [ ] **Step 2: Sweep the entire templates directory for any remaining auth-related `localStorage` references**

```bash
grep -rn "localStorage.*[Tt]oken\|accessToken\|refreshToken" src/main/resources/templates/
```

Expected: no results (or only unrelated matches, e.g. a code comment mentioning the old behavior for context — read each hit and judge). If any genuine remaining reference to reading/writing the JWT via `localStorage` turns up that Tasks 6-9 missed, fix it following the same `GET /api/auth/me` / `fetchWithAuth` pattern established in this plan, and note it in this task's report as an addition beyond what was originally scoped (DONE_WITH_CONCERNS, not silently expanding scope without flagging it).

- [ ] **Step 3: Manual verification**

With the app running, visit `/inspection-map` both as an anonymous user and as a CONTROLLER. Expected: dashboard link appears only for the CONTROLLER.

- [ ] **Step 4: Commit**

```bash
git add src/main/resources/templates/inspection-map.html
git commit -m "feat(auth): migrate inspection-map.html's role check to GET /api/auth/me

Completes the localStorage-to-cookie migration across all templates —
verified via a full grep sweep for any remaining accessToken/
refreshToken references in src/main/resources/templates/."
```

---

### Task 10: Push branch and open PR

**Files:** none (git/GitHub operations only).

**Interfaces:**
- Consumes: all commits from Tasks 1-9 on `feature/jwt-httponly-cookies`.
- Produces: an open PR against `main`, ready for CI.

- [ ] **Step 1: Run the full test suite one final time**

```bash
mvn test -q
```

Expected: `BUILD SUCCESS`.

- [ ] **Step 2: Push the branch**

```bash
git push origin feature/jwt-httponly-cookies
```

- [ ] **Step 3: Open the PR**

```bash
gh pr create --repo St4r4x/restaurant-analytics --base main --title "feat(auth): migrate JWT storage from localStorage to httpOnly cookies" --body "$(cat <<'EOF'
## Summary
- Both access_token (15 min, path /) and refresh_token (7 days, path /api/auth/) move from localStorage to httpOnly+Secure+SameSite=Strict cookies — closes the XSS-can-steal-both-tokens gap.
- New GET /api/auth/me replaces client-side JWT decoding for role-based UI (navbar, admin/dashboard guards, login redirect).
- New POST /api/auth/logout clears both cookies (JS can no longer clear httpOnly cookies directly).
- Reactive refresh-on-401 (via a single global window.fetchWithAuth in ux-utils.html) replaces the old proactive exp-based scheduler and the 5 duplicated per-template fetchWithAuth implementations.
- New app.cookie.secure config (default true, false in docker-compose.yml) keeps local HTTP dev-container testing working without weakening the production default.
- SameSite=Strict is the sole CSRF defense (single-origin app, no separate CSRF token) — design spec has the full reasoning.
- Design spec: docs/superpowers/specs/2026-07-27-jwt-httponly-cookies-design.md
- Implementation plan: docs/superpowers/plans/2026-07-27-jwt-httponly-cookies.md

## Test plan
- [x] AuthControllerTest covers cookie-setting on login/register/refresh, cookie-clearing on logout, /me's authenticated response
- [x] JwtAuthenticationFilterTest rewritten for cookie-based extraction (6 tests)
- [x] Manual end-to-end verification in the dev container: register → cookies set → navbar shows user → sign out → cookies cleared → log back in → role-based page access (profile/bookmarks/dashboard/admin) → refresh-on-401 (verified by deleting the access_token cookie mid-session and confirming a transparent retry)
- [x] Full grep sweep of src/main/resources/templates/ confirms no remaining localStorage-based token references
EOF
)"
```

- [ ] **Step 4: Wait for CI, verify green**

```bash
gh pr checks --repo St4r4x/restaurant-analytics --watch --interval 20
```

Expected: all checks (Build, Unit Tests, Integration Tests, Secret Scan, E2E Smoke Test, Docker Build and Push) pass. Pay particular attention to the E2E Smoke Test — it hits `GET /api/restaurants/health`, which is unauthenticated and unaffected by this migration, so it should pass unchanged; if it fails, that's a signal something in this migration broke request handling more broadly than intended (e.g. a `SecurityConfig` matcher ordering mistake), not a false alarm to dismiss.

Do not merge — leave the PR open for the user to review and merge, consistent with this repo's established workflow for this feature series.

---

## Self-Review Notes

- **Spec coverage**: cookie emission (Tasks 1-3), cookie clearing/logout (Task 4), cookie reading in the filter + SecurityConfig matcher (Task 5), centralized fetchWithAuth + reactive refresh (Task 6), the one non-fetchWithAuth code path — multipart upload (Task 7), login/navbar (Task 8), remaining template sweep (Task 9). All design-spec decisions (SameSite=Strict only, both tokens migrated, GET /me, reactive refresh, no tokens in response bodies, app.cookie.secure) have a corresponding task.
- **No placeholders**: every step shows exact code or, where the plan cannot know a file's exact current byte-for-byte content at execution time (large files like `restaurant.html`/`dashboard.html`'s bookmark-guard sections, `inspection-map.html`'s nested conditional), explicitly instructs the implementer to read the actual current content before editing rather than guessing — this is a deliberate accommodation for sections verified to exist and be structured roughly as described, but not fully quoted in this plan to avoid quoting hundreds of lines of surrounding, unrelated code. This is different from a placeholder ("add validation here") — it names the exact old and new snippets at the edit boundary and defers only the surrounding untouched context.
- **Type/name consistency**: cookie names (`access_token`, `refresh_token`) are identical across Task 1 (write), Task 3 (read for refresh), Task 5 (read in filter), and all of Tasks 6-9's client-side checks (`/api/auth/me`, `/api/auth/logout` paths match between Task 4's definition and every later task's call sites). `AppConfig.isCookieSecure()` name matches between Task 1's definition and its two call sites within that same task.
- **Sequencing dependency respected**: Task 6 (global fetchWithAuth) must land before Tasks 7-9 (which call it or its refresh-on-401 behavior implicitly), and Task 4 (`/me`, `/logout`) must land before Task 8 (login/navbar, which calls both) — task order in this plan reflects that.
