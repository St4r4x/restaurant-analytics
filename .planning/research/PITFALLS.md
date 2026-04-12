# Domain Pitfalls

**Domain:** Spring Boot RBAC / file upload / map UI extension on existing monolith
**Researched:** 2026-03-27
**Confidence:** HIGH (codebase read directly; Spring Security and Spring Boot 2.6 patterns are well-known)

---

## Critical Pitfalls

These mistakes cause rewrites or security holes.

---

### Pitfall 1: `.anyRequest().permitAll()` stays in place after adding role guards

**What goes wrong:** `SecurityConfig` currently has `.anyRequest().permitAll()`. Adding `@PreAuthorize("hasRole('CONTROLLER')")` on individual methods appears to work in happy-path tests, but the HTTP-level rule still allows unauthenticated requests to reach the dispatcher — only the method-level annotation stops them. If `@EnableMethodSecurity` ever loses `proxyTargetClass=true` or a method is called internally (self-invocation), the guard is bypassed entirely.

**Why it happens:** `@EnableMethodSecurity` is already on `SecurityConfig`, which is good. But method-security works via Spring AOP proxies. A call from one `@Service` method to another in the same bean skips the proxy — the `@PreAuthorize` on the second method never fires.

**Consequences:**
- Controller-only report endpoints become accessible to any authenticated or unauthenticated user.
- Cross-role data leaks (a CUSTOMER reading another controller's draft report).
- In Spring Boot 2.6 with `@EnableMethodSecurity`, the default is `prePostEnabled=true`; if someone switches to the legacy `@EnableGlobalMethodSecurity`, behaviour silently changes.

**Prevention:**
- Lock down URL patterns at the `HttpSecurity` level *in addition* to method annotations. Route `/api/reports/**` to `hasRole('CONTROLLER')` directly in `filterChain`. Method annotations are a second defensive layer, not the first.
- Never rely on `@PreAuthorize` alone for HTTP endpoints.
- Avoid self-invocation of guarded methods; extract into separate Spring beans if needed.

**Detection:** Write a test that calls a CONTROLLER-only endpoint with a CUSTOMER JWT and assert HTTP 403. If you get 200 or 401, the HTTP-level guard is missing.

**Phase:** Role-based access control setup (Phase 1 / auth extension).

---

### Pitfall 2: Registering users as ROLE_USER then promoting them is a race condition

**What goes wrong:** `AuthService.register()` hardcodes `"ROLE_USER"`. The plan is to extend registration with a signup code to grant `ROLE_CONTROLLER`. A common mistake is to register first (saves with `ROLE_USER`) then check the code and call a separate `setRole()` call — two database writes without a transaction around them.

**Why it happens:** JPA `save()` flushes on commit. If the code check is not inside `@Transactional` with the user creation, a failure between the two operations leaves a persisted `ROLE_USER` row with no role promotion, and there is no compensating rollback.

**Consequences:** Orphaned CONTROLLER accounts silently degraded to ROLE_USER. Hard to detect; no exception is thrown.

**Prevention:**
- Perform the signup-code check and user creation atomically inside a single `@Transactional` service method.
- Validate the code *before* calling `userRepository.save()`, not after.
- The signup code itself should be constant-time compared (use `MessageDigest.isEqual` or similar) to prevent timing attacks.

**Detection:** Warning sign is any code path where `userRepository.save()` is called before the role is finalized.

**Phase:** Controller registration / signup code (Phase 1).

---

### Pitfall 3: File upload stored on the container filesystem

**What goes wrong:** Storing uploaded inspection photos at a local path inside the Docker container (`/uploads/...`) means files disappear on every `docker compose up --build` or container restart because the container layer is ephemeral.

**Why it happens:** `MultipartFile.transferTo(new File("/uploads/..."))` is the most natural Spring implementation; it works perfectly in development and silently fails in production.

**Consequences:** All uploaded inspection photos are lost on redeploy. No error thrown — files simply cease to exist.

**Prevention:**
- Mount a named Docker volume for file storage and write to that path: `volumes: - report_photos:/app/uploads`.
- OR store files in a dedicated object store (MinIO, S3) accessible outside the container.
- Configure `spring.servlet.multipart.max-file-size` and `spring.servlet.multipart.max-request-size` explicitly (default is 1MB per part, 10MB total — too small for photos).
- Store only the file *reference* (relative path or object key) in the JPA entity, never the binary in the database.

**Detection:** Restart the app container after uploading a photo and verify the file is still accessible. If it is gone, storage is not persisted.

**Phase:** Inspection report / file upload (Phase 2).

---

### Pitfall 4: Refresh token promotes stale role

**What goes wrong:** `AuthService.refresh()` re-reads the user from PostgreSQL and generates a new access token with the current role. This is correct. However, `generateRefreshToken()` does not embed a role claim — it is a plain subject-only token. The problem is the opposite: if a CONTROLLER has their role revoked (or demoted to CUSTOMER), their existing 7-day refresh token still allows them to keep getting new access tokens with the promoted role — until the refresh token itself expires.

Reading the code: `refresh()` does look up `user.getRole()` from the DB, so role revocation is actually handled correctly here. **However**, the refresh token has no jti (JWT ID) and there is no revocation list. A stolen refresh token is valid for 7 days with no way to invalidate it short of changing the JWT secret (which invalidates every user's session).

**Why it happens:** Stateless JWT design by default has no invalidation mechanism.

**Consequences:** A compromised CONTROLLER refresh token gives 7-day window for report filing or data exfiltration.

**Prevention (pragmatic for academic scope):**
- Store refresh token hash in PostgreSQL alongside the user; on refresh, validate the stored hash matches.
- On logout or role change, delete the stored hash — token is instantly invalidated.
- This is a one-column addition to `users` table (or a separate `refresh_tokens` table for multi-device support).

**Detection:** Issue a refresh token, change the user's role in the DB directly, call `/api/auth/refresh`, and verify the new access token reflects the updated role AND that the old refresh token is rejected after logout.

**Phase:** Role-based access control setup (Phase 1); store token hash when implementing controller registration.

---

### Pitfall 5: `@PreAuthorize` role name mismatch — `ROLE_` prefix double-counting

**What goes wrong:** Spring Security's `hasRole('CONTROLLER')` internally prepends `ROLE_` and checks for `ROLE_CONTROLLER`. `hasAuthority('ROLE_CONTROLLER')` does not prepend. The existing filter puts the raw role string from the JWT into `SimpleGrantedAuthority(role)` — meaning whatever string is stored in the DB is used verbatim.

If `UserEntity.role` is stored as `"ROLE_CONTROLLER"` and `@PreAuthorize("hasRole('CONTROLLER')")` is used, Spring looks for `ROLE_CONTROLLER` — which matches. But if `@PreAuthorize("hasAuthority('CONTROLLER')")` is used somewhere, it looks for the literal string `"CONTROLLER"`, which does not match `"ROLE_CONTROLLER"`. Mixing the two annotations causes silent authorization failures that are very hard to debug.

**Why it happens:** The two annotation forms look equivalent to developers who don't know the prefix convention.

**Consequences:** `403 Forbidden` on endpoints that should work; or `200 OK` on endpoints that should be blocked — depending on which form was accidentally used.

**Prevention:**
- Pick one convention for the entire codebase and document it at the top of `SecurityConfig`.
- Recommendation: store `"ROLE_CONTROLLER"` and `"ROLE_CUSTOMER"` in the DB (consistent with Spring defaults), and always use `hasRole('CONTROLLER')` in annotations.
- Write a test that asserts role string format at registration time.

**Detection:** Grep for `hasAuthority` vs `hasRole` mixed in the same codebase, or a CONTROLLER user getting 403 on a `hasRole('CONTROLLER')`-annotated endpoint.

**Phase:** Role-based access control setup (Phase 1).

---

## Moderate Pitfalls

---

### Pitfall 6: Leaflet map loads over HTTP on an HTTPS page (mixed content block)

**What goes wrong:** The existing `inspection-map.html` likely loads Leaflet tiles from `http://` tile URLs. Browsers block mixed content — HTTPS page cannot load HTTP resources. Even in local development this is invisible, but as soon as the app is served over HTTPS (or the browser flags it), the map tiles stop rendering with no obvious error in the UI.

**Prevention:**
- Always use `https://` tile URLs: `https://{s}.tile.openstreetmap.org/{z}/{x}/{y}.png`.
- Add `crossOrigin: ''` attribute on Leaflet tile layer for CORS compliance.
- Test in a browser with mixed-content warnings enabled.

**Phase:** Customer map UI (Phase 3).

---

### Pitfall 7: Rendering 10,000+ restaurant markers crashes the browser

**What goes wrong:** NYC restaurant dataset contains ~27,000 active restaurants. Loading all of them as individual Leaflet markers on page load will cause the browser to hang for several seconds and then become unresponsive — especially on lower-end machines.

**Prevention:**
- Use marker clustering (`Leaflet.markercluster` plugin) — it groups nearby markers at low zoom levels and expands them as the user zooms in.
- Add a viewport-based filter on the API: only return restaurants within the current map bounding box (`$geoWithin`).
- Implement MongoDB 2dsphere index if not already present (the DAO code calls `ensureIndexes()` — verify geospatial index exists for `address.coordinates`).

**Detection:** Open the map, open Chrome DevTools Performance tab, record while the map loads. If render time exceeds 1s with full dataset, clustering is needed.

**Phase:** Customer map UI (Phase 3).

---

### Pitfall 8: File upload bypass of MIME type check via Content-Type spoofing

**What goes wrong:** Checking only `multipart.getContentType()` returns `"image/jpeg"` is not safe — the browser sends whatever Content-Type the client claims. A malicious user can upload a PHP script with `Content-Type: image/jpeg` and the server accepts it as a photo.

**Prevention:**
- Read the first bytes of the uploaded file and check the magic bytes (JPEG starts with `FF D8 FF`; PNG starts with `89 50 4E 47`).
- Use Apache Tika (`tika-core`) for reliable MIME detection from bytes, not headers.
- Reject files whose detected MIME type is not in an explicit allowlist.
- Store files with a generated UUID filename, not the original filename — prevents path traversal.
- Never execute uploaded files; serve them from a separate path that does not map to servlet scope.

**Phase:** Inspection report / file upload (Phase 2).

---

### Pitfall 9: `InspectionReport` JPA entity cross-ownership — controller reads another controller's drafts

**What goes wrong:** A report list endpoint `GET /api/reports` that returns all reports without scoping by the authenticated user exposes every controller's draft inspection reports to every other controller.

**Why it happens:** The natural first implementation is `reportRepository.findAll()`. Adding a `WHERE user_id = ?` filter is an afterthought.

**Prevention:**
- From day one, all report queries must be scoped to `authenticatedUserId`: `reportRepository.findByControllerUserId(currentUserId)`.
- Extract the current user ID from `SecurityContextHolder.getContext().getAuthentication().getName()` (which is the username), then look up the user ID from `UserRepository`.
- Never pass `userId` as a request parameter — always derive it from the token.

**Detection:** Log in as controller A, create a report. Log in as controller B, call `GET /api/reports`. If B sees A's report, ownership scoping is missing.

**Phase:** Inspection report CRUD (Phase 2).

---

### Pitfall 10: Thymeleaf templates that serve role-gated pages without server-side auth check

**What goes wrong:** `ViewController` currently returns Thymeleaf templates for all routes without any authentication check. Adding a `/controller-dashboard` route that returns the template without guarding it at the HTTP layer means any user (including unauthenticated) can see the HTML shell even if the API calls inside it require auth.

**Why it happens:** Frontend JavaScript performs the auth check; backend route just serves the HTML. This is a common SPA antipattern applied to server-rendered pages.

**Consequences:** CONTROLLER-only UI pages rendered (with empty data) for any visitor. Information disclosure in page structure/source.

**Prevention:**
- Add `@PreAuthorize` on `ViewController` methods for protected pages, OR handle unauthenticated view requests in the `authenticationEntryPoint` (already in `SecurityConfig`) to redirect to `/login`.
- The existing `authenticationEntryPoint` already redirects non-`/api/` paths to `/login` on 401 — this only fires if the route is protected. Add the route to `authorizeRequests()`.

**Phase:** Role-based UI pages (Phase 1 or Phase 2).

---

### Pitfall 11: MongoDB codec registry misses `InspectionReportEntity` fields

**What goes wrong:** `MongoClientFactory` registers POJOs via `PojoCodecProvider`. Adding a new domain POJO (e.g. if any report data is stored in MongoDB) without registering it with the codec causes silent `null` deserialization — no exception, just missing data.

Note: The plan is to store reports in PostgreSQL (JPA), which avoids this entirely. However, if any future phase stores report metadata or violation codes in MongoDB (to co-locate with restaurant data), this pitfall applies.

**Prevention:**
- Keep all new structured entities in PostgreSQL via JPA, as planned.
- If MongoDB is used for any report data, register the POJO class with `PojoCodecProvider` and write a round-trip deserialization test.

**Phase:** Inspection report entity design (Phase 2).

---

## Minor Pitfalls

---

### Pitfall 12: `spring.servlet.multipart.enabled` defaults to true but file upload size limits cause silent 413

**What goes wrong:** Spring Boot's default `max-file-size` is 1MB per part and `max-request-size` is 10MB. An uploaded inspection photo (especially from a mobile device) easily exceeds this. The server returns `HTTP 413 Payload Too Large` but the default Spring error response body is not JSON — it is a Whitelabel Error Page — which breaks any JavaScript fetch handler expecting JSON.

**Prevention:**
- Set explicit limits in `application.properties`: `spring.servlet.multipart.max-file-size=10MB` and `spring.servlet.multipart.max-request-size=25MB`.
- Add a `@ControllerAdvice` that catches `MaxUploadSizeExceededException` and returns a structured JSON error.

**Phase:** File upload (Phase 2).

---

### Pitfall 13: Controller signup code stored in `application.properties` as plaintext

**What goes wrong:** Hardcoding a registration code in properties is convenient but the code ends up in version control, visible to anyone with repo access. Rotating it requires a redeployment.

**Prevention:**
- Store the code as an environment variable (`CONTROLLER_SIGNUP_CODE`) injected via Docker Compose, not in `application.properties` (or at minimum in `.gitignore`-d local config).
- Hash the stored reference code so even if the config leaks, the raw code is not revealed.

**Phase:** Controller registration (Phase 1).

---

### Pitfall 14: `@CrossOrigin(origins = "*")` on `AuthController` remains after RBAC addition

**What goes wrong:** `AuthController` has `@CrossOrigin(origins = "*", allowedHeaders = "*")`. `SecurityConfig` also has `CORS wildcard` flagged as High severity in CONCERNS.md. After adding RBAC, this means any origin can attempt login/registration. This is not a new pitfall introduced by the milestone, but RBAC makes it more impactful because CONTROLLER accounts are now higher-privilege targets.

**Prevention:**
- Replace wildcard CORS with an explicit origin allowlist (`http://localhost:8080` for local dev, deployed domain for production) in `SecurityConfig.cors()` configuration.
- Remove the `@CrossOrigin` annotation from individual controllers — manage CORS centrally.

**Phase:** Security hardening (Phase 1 alongside auth extension).

---

## Phase-Specific Warnings

| Phase Topic | Likely Pitfall | Mitigation |
|---|---|---|
| Role-based auth extension | `ROLE_` prefix mismatch between `hasRole` and `hasAuthority` (Pitfall 5) | Pick one convention; document it; test it |
| Controller registration with signup code | Race condition in register + promote (Pitfall 2) | Atomic `@Transactional` registration |
| Controller registration with signup code | Signup code in plaintext properties (Pitfall 13) | Use env var injected via Docker Compose |
| URL-level access control | `anyRequest().permitAll()` still active (Pitfall 1) | Add explicit `.antMatchers("/api/reports/**").hasRole("CONTROLLER")` |
| Thymeleaf pages for controller dashboard | Pages served without server-side auth (Pitfall 10) | Guard view routes in `SecurityConfig` |
| Inspection report CRUD | Cross-owner data leak (Pitfall 9) | Always scope repository queries to authenticated user |
| File upload for inspection photos | Files lost on container restart (Pitfall 3) | Docker named volume or external object store |
| File upload for inspection photos | MIME type spoofing / path traversal (Pitfall 8) | Magic byte check; UUID filenames |
| File upload for inspection photos | 413 not returning JSON (Pitfall 12) | `MaxUploadSizeExceededException` handler in `@ControllerAdvice` |
| Customer map UI | Browser crash from 27K markers (Pitfall 7) | Leaflet.markercluster + viewport-bounded API |
| Customer map UI | Mixed content HTTP tiles over HTTPS (Pitfall 6) | Always use `https://` tile URLs |
| JWT token revocation | Stolen refresh token valid 7 days (Pitfall 4) | Store refresh token hash in DB; delete on logout |

---

## Existing Codebase Issues That Become Critical Under This Milestone

The following items from `CONCERNS.md` are currently low/moderate risk but become directly exploitable when RBAC is added:

| Concern | Current Risk | Risk After RBAC | Action Required |
|---|---|---|---|
| CORS wildcard `*` | Medium | High — any origin attacks CONTROLLER login | Fix in Phase 1 alongside auth |
| No rate limiting on auth endpoints | Medium | High — CONTROLLER accounts become brute-force targets | Add Spring Security `RateLimitFilter` or Spring Boot actuator throttle |
| JWT validation swallows exceptions | Medium | High — invalid tokens silently treated as unauthenticated, not rejected | Log and return 401 explicitly |
| NoSQL injection via unvalidated sort field | Medium | Medium — unchanged, but controller data in system raises stakes | Validate field name against allowlist |
| `anyRequest().permitAll()` | Low (no protected data) | Critical (protected CONTROLLER endpoints) | Replace with explicit rule matrix |

---

## Sources

- Direct codebase analysis: `SecurityConfig.java`, `JwtAuthenticationFilter.java`, `JwtUtil.java`, `AuthService.java`, `UserEntity.java`, `ViewController.java` (read 2026-03-27)
- Spring Security method security AOP proxy limitations: HIGH confidence (well-documented Spring behavior)
- Leaflet marker performance limits: MEDIUM confidence (community consensus ~1000 markers without clustering)
- Docker volume ephemerality: HIGH confidence
- Spring multipart defaults (1MB/10MB): HIGH confidence (Spring Boot 2.6 documentation)
- JWT stateless revocation patterns: HIGH confidence (OWASP JWT cheat sheet)
