# Design: Migrate JWT Storage from localStorage to httpOnly Cookies

**Date:** 2026-07-27
**Status:** Approved

---

## Context

Third sub-project in the signup/login rework series (after password complexity, PR #14 merged, and signup availability check + password confirmation, PR #16 merged). Both access and refresh JWTs currently live in `localStorage`, readable by any JS running on the page — a successful XSS anywhere in the app (10 templates read/write `localStorage.getItem('accessToken')`/`'refreshToken'`) hands the attacker both tokens outright, including the 7-day refresh token.

This sub-project moves both tokens into httpOnly cookies, set by the server, invisible to JS entirely. It is the most cross-cutting of the four sub-projects (touches `AuthController`, `AuthService` call sites, `JwtAuthenticationFilter`, `SecurityConfig`, and all 10 templates that currently read the JWT), which is why it's sequenced before the remaining forgot-password sub-project — that one will build its "already logged in" checks on top of the new cookie-based model rather than the old one.

**Current state** (verified by reading the code):
- `AuthController.register()`/`login()`/`refresh()` return `JwtResponse { accessToken, refreshToken }` in the JSON body.
- `JwtAuthenticationFilter.doFilterInternal()` reads `Authorization: Bearer <token>` header.
- `SecurityConfig` disables CSRF (`csrf(csrf -> csrf.disable())`) — was safe because auth was header-based, not cookie-based (a forged cross-site request has no way to set an `Authorization` header).
- 10 templates read/write `localStorage.getItem/setItem/removeItem('accessToken'/'refreshToken')`: `index.html`, `dashboard.html`, `restaurant.html`, `admin.html`, `profile.html`, `my-bookmarks.html`, `login.html`, `inspection-map.html`, and fragments `navbar.html`, `ux-utils.html`.
- 4 templates decode the JWT client-side to read the `role` claim for UI purposes: `login.html`, `fragments/navbar.html`, `dashboard.html`, `admin.html` (`JSON.parse(atob(token.split('.')[1]))`).
- `fragments/ux-utils.html` runs a proactive refresh scheduler on every page load: reads the access token's `exp` claim, schedules a `setTimeout` to call `/api/auth/refresh` shortly before expiry.
- 5 templates each define their own local `fetchWithAuth` helper that adds the `Authorization` header: `index.html`, `dashboard.html`, `restaurant.html`, `admin.html`, `my-bookmarks.html` — duplicated, slightly different in each.
- `AppConfig.getJwtAccessTokenExpirationMs()` = 900000 (15 min), `getJwtRefreshTokenExpirationMs()` = 604800000 (7 days) — these existing config values become the cookies' `Max-Age`.

## Decisions

### CSRF: SameSite=Strict, no separate CSRF token

The app is single-origin (no mobile app, no third-party cross-origin API consumer). `SameSite=Strict` already prevents the cookie from being attached to any request originating from another site, which closes the CSRF gap that a plain cookie would otherwise reopen. No double-submit cookie or Spring Security synchronizer token is added — revisit only if the app later needs cross-origin API consumers, which would need SameSite=Lax or None anyway and a real CSRF token regardless.

### Both access and refresh tokens move to cookies

Moving only the access token and leaving the refresh token in `localStorage` would defeat much of the point — the refresh token is the longer-lived, more dangerous one to leak (7 days vs. 15 minutes). Both become httpOnly.

- `access_token` cookie: httpOnly, secure, SameSite=Strict, path `/`, `Max-Age` = `AppConfig.getJwtAccessTokenExpirationMs() / 1000` seconds.
- `refresh_token` cookie: httpOnly, secure, SameSite=Strict, path `/api/auth/` (scopes it to only the auth endpoints that need it — `refresh` and `logout` — rather than being sent on every single request across the whole app), `Max-Age` = `AppConfig.getJwtRefreshTokenExpirationMs() / 1000` seconds.

`secure` is always set to `true` — this app is only ever served over HTTPS in any environment that matters (Railway terminates TLS; local dev over plain `http://localhost` will not receive a `secure` cookie from the browser, which is an accepted limitation covered in the Testing section below, not a design flaw to work around).

### New endpoint: `GET /api/auth/me`

Client-side code can no longer decode the JWT to learn `username`/`role` — the token is invisible to JS now. Replaces that with a server round-trip: reads the already-populated `SecurityContextHolder` (same mechanism `UserController.getCurrentUser()` already uses) and returns `{ "username": "...", "role": "..." }`. Called once per page load by any template that currently decodes the JWT (`navbar.html`, `admin.html`, `dashboard.html`, and `login.html`'s "redirect if already logged in" check).

### New endpoint: `POST /api/auth/logout`

JS can no longer clear cookies the way it cleared `localStorage`. This endpoint sets both cookies with `Max-Age=0`, clearing them. `navbar.html`'s "Sign Out" button calls this instead of `localStorage.removeItem(...)`.

### Reactive refresh-on-401, replacing proactive scheduled refresh

`fragments/ux-utils.html`'s current scheduler reads the access token's `exp` claim client-side to schedule a refresh moments before expiry — impossible once the token is httpOnly. Replaced with a standard reactive pattern: a single global `window.fetchWithAuth(url, options)` (see below) that, on receiving a `401`, calls `POST /api/auth/refresh` once, and if that succeeds, retries the original request once; if the refresh itself fails, redirects to `/login`. This is simpler and more robust than clock-based scheduling (immune to client clock drift, tab-sleep, etc.) at the cost of one extra round-trip on the rare request that lands exactly as the token expires.

`POST /api/auth/refresh` no longer needs a `refreshToken` field in its request body — it reads the `refresh_token` cookie directly. The endpoint still exists at the same path; only its input source changes (cookie instead of JSON body) and its output (cookies instead of JSON body, per the next decision).

### Response bodies no longer carry tokens

`register`/`login`/`refresh` currently return `{ accessToken, refreshToken }` in the JSON body — with cookies as the transport, leaving the tokens in the body too would still expose them to any script that can read the fetch response (which is exactly the XSS scenario this migration is meant to close). The response body for these three endpoints becomes `{ "status": "success" }` (matching the existing envelope style used elsewhere in this codebase, e.g. `UserController`'s responses) — no token material in JSON, ever.

Internally, `AuthService.register()`/`login()`/`refresh()` keep returning `JwtResponse { accessToken, refreshToken }` — that return type doesn't change, since `AuthController` still needs both token strings to set the two cookies. Only the controller's HTTP-level output changes; the service layer's method signatures are untouched.

### Centralize `fetchWithAuth` in `fragments/ux-utils.html`

The 5 duplicated local implementations are removed from their respective templates. A single `window.fetchWithAuth(url, options)` is defined once in `fragments/ux-utils.html` (already loaded on every page via `th:replace="fragments/ux-utils :: ux-utils"`), doing:
1. `fetch(url, { ...options, credentials: 'same-origin' })` — no `Authorization` header added; the cookie rides along automatically. `credentials: 'same-origin'` is the browser default for same-origin requests already, set explicitly for clarity/future-proofing rather than because it changes current behavior.
2. If the response status is `401`: call `POST /api/auth/refresh` (also with `credentials: 'same-origin'`). If that succeeds (200), retry the original request once via the same `fetch` call. If the refresh itself returns non-200, redirect to `/login`.
3. Otherwise return the original response as-is.

Every template that previously defined its own `fetchWithAuth` (`index.html`, `dashboard.html`, `restaurant.html`, `admin.html`, `my-bookmarks.html`) has that local definition removed and its call sites now use the global one from `ux-utils.html`.

## Out of scope

- No change to `JwtUtil`/`JwtService` (token generation/validation logic) — only the transport mechanism changes.
- No change to `SecurityConfig`'s `SessionCreationPolicy.STATELESS` — cookies don't create a server-side session; the app remains stateless.
- No change to role/permission logic anywhere.
- No change to the multipart photo-upload endpoint's auth handling beyond it also now relying on the cookie instead of a manually-attached header (already noted in `dashboard.html`'s existing comment about not using `fetchWithAuth` there due to `Content-Type` conflicts — that constraint is unrelated to this migration and remains true, but that code path still benefits from the cookie riding along automatically without any header logic needed for auth specifically).
- No new "remember me" / session-length options — cookie `Max-Age` values mirror the existing token expiration config unchanged.
- No test-suite migration for how integration tests currently authenticate (if any inject a Bearer header directly) — flagged for the implementation plan to check and adjust, not decided here since it depends on what's actually in the test files.

## Decision: `secure` cookie flag is conditional on environment

Local development over plain `http://localhost:8080` (as used by `docker compose up` and this project's dev-container testing) will not receive `secure` cookies from the browser, since `secure` requires HTTPS — this would silently break login in every local/dev-container test session, which is how this project is actually tested day to day.

Resolution: the `secure` flag is driven by a new config property `app.cookie.secure`, read via `AppConfig` the same way other config flows through that class (JVM property → env var → `.env` → `application.properties`, per `AppConfig.getProperty`'s existing precedence). Default value in `application.properties` is `true` (safe default for anything that isn't explicitly overridden, including Railway production). `docker-compose.yml`'s `app` service environment block sets `APP_COOKIE_SECURE=false` for local development, mirroring how `SPRING_PROFILES_ACTIVE`/`JWT_SECRET` etc. are already injected there as environment variables. This keeps production safe by default while making local HTTP testing (which is how this project has been verified throughout this whole sub-project series) actually work, without a Spring profile switch or a second code path.
