# Design: Forgot Password (Resend)

**Date:** 2026-07-27
**Status:** Approved

---

## Context

Fourth and final sub-project in the signup/login rework series (after password complexity — PR #14, merged; signup availability check + password confirmation — PR #16, merged; JWT httpOnly cookie migration — PR #17, open). This one adds a password recovery flow, which is entirely absent from the app today: a user who forgets their password has no way back in short of asking an admin to manually intervene (no such admin tooling exists either).

This is a genuinely new feature (a new table, a new external dependency, two new endpoints, a new page) rather than a rework of existing auth code, unlike the first three sub-projects.

## Decisions

### Email provider: Resend, sandbox domain

No email-sending capability exists anywhere in this codebase today (confirmed: no `spring-boot-starter-mail`, no SMTP config, no email SDK in `pom.xml`). Resend is added as a new Maven dependency (official Java SDK). The sandbox sender (`onboarding@resend.dev`) is used rather than a verified custom domain — no DNS/SPF/DKIM setup required, works immediately. Known limitation, explicitly accepted: Resend's sandbox mode only delivers to the email address of the Resend account owner, not to arbitrary end-user addresses. This is acceptable for this project's academic/demo scope; upgrading to a verified domain later is a config-only change (a domain string + DNS records), not a code change — the app code sends to whatever email address the API call specifies regardless of sandbox/production mode.

### Reset token: dedicated PostgreSQL table, 1-hour expiry

A new `password_reset_tokens` table (JPA-managed, `spring.jpa.hibernate.ddl-auto=update` per existing project convention — confirmed in `application.properties`) rather than reusing the existing JWT infrastructure. Rationale: a reset token needs to be individually invalidatable (single-use, revocable if the user requests a second link before using the first) — a stateless JWT can't be revoked before its own expiry without a separate revocation-list mechanism this project doesn't have. A dedicated table is simpler than retrofitting revocation onto the JWT path for one use case.

**Schema** (`PasswordResetTokenEntity`, mirroring the style of existing entities like `BookmarkEntity`/`InspectionReportEntity` — `@ManyToOne` to `UserEntity`, not a raw `Long userId`, consistent with how those two entities already reference `UserEntity`):

| Column | Type | Notes |
|---|---|---|
| `id` | `BIGSERIAL` | PK |
| `user_id` | `BIGINT` | FK → `users.id`, `@ManyToOne` |
| `token_hash` | `VARCHAR` | SHA-256 hex digest of the raw token — the raw token is never persisted, same principle as a password hash |
| `expires_at` | `TIMESTAMP` | created + 1 hour |
| `used_at` | `TIMESTAMP`, nullable | null until consumed; a non-null value makes the token permanently unusable even if not yet expired |
| `created_at` | `TIMESTAMP` | for audit/debugging |

The raw token itself: 32 random bytes (`SecureRandom`), base64url-encoded (URL-safe, no padding) for safe inclusion in a link query parameter. Hashed with SHA-256 (`MessageDigest`, no external dependency needed) before storage; compared by hashing the incoming token and doing an equality lookup against `token_hash`.

### Endpoints

**`POST /api/auth/forgot-password`** — body `{ "email": "..." }`.
- Always returns `200 { "status": "success" }` — identical response whether or not the email exists in `users`. This is the anti-enumeration decision: an attacker probing arbitrary emails learns nothing about which ones are registered. No difference in response *timing* is engineered either — the email-exists path does strictly more work (generate token, write a row, call Resend) than the email-doesn't-exist path (nothing), so a sufficiently precise timing side-channel isn't fully eliminated by this design; closing that residual gap (e.g. constant-time padding) is out of scope — not a realistic risk for this project's threat model.
- If the email exists: generate a token, hash it, insert a row, send the reset email via `EmailService` with a link `https://<app-host>/reset-password?token=<raw-token>`. `<app-host>` is read from request context (`HttpServletRequest.getScheme()`/`getServerName()`/`getServerPort()`) rather than hardcoded, so it works correctly in both the local dev container and Railway production without a new config key.
- If the email doesn't exist: do nothing further, still return the same 200.
- Rate limiting: falls under the existing `RateLimitFilter`'s `/api/auth/**` strict bucket automatically (same `uri.startsWith("/api/auth/")` match already covering every other endpoint in this series) — no filter change needed, confirmed by reading `RateLimitFilter.java`.

**`POST /api/auth/reset-password`** — body `{ "token": "...", "newPassword": "..." }`.
- Look up `password_reset_tokens` by the SHA-256 hash of the incoming token.
- Validation order (fail fast, cheapest check first, consistent with `AuthService.register()`'s existing ordering pattern): token not found → 400 `"Invalid or expired reset link"`; `used_at` is not null → 400 `"This reset link has already been used"`; `expires_at` is in the past → 400 `"Invalid or expired reset link"` (same message as not-found — no need to distinguish "expired" from "never existed" to a caller who already possesses a token string, but "already used" gets a distinct message since it's operationally different information: the link worked once, try requesting a new one instead of assuming it never worked).
- Password complexity: `ValidationUtil.requireValidPassword(newPassword)` — same rule as registration (10+ chars, 1 uppercase, 1 digit), for the same reason (this is a new-password-entry point, same policy as signup).
- On success: update `UserEntity.passwordHash`, set `used_at = now()` on the token row, return `200 { "status": "success" }`.
- No session revocation: existing access tokens (15 min) simply expire on their own short timer; no refresh-token revocation mechanism exists anywhere else in this stateless-JWT app, so none is introduced here either — consistent with the app's existing security model, not a gap specific to this feature.
- Same rate-limit coverage as `forgot-password` (same `/api/auth/**` bucket).

### `EmailService` abstraction

New interface + Resend-backed implementation, following the existing `JwtService`/`JwtUtil` pattern in this codebase (`src/main/java/com/st4r4x/security/JwtService.java` + `JwtUtil.java`) — an interface exists specifically so tests can inject a mock without a real network call to Resend's API.

```java
public interface EmailService {
    void sendPasswordResetEmail(String toEmail, String resetLink);
}
```

Implementation (`ResendEmailService`) wraps the Resend Java SDK's client, constructed with the API key from `AppConfig` (new `getResendApiKey()` method, following the exact existing pattern of `getJwtSecret()` — fails fast at startup if missing, since a null/blank key would only surface as a runtime failure on the first password-reset attempt otherwise, which is worse than an immediate boot-time error). The API key is stored in Infisical (`RESEND_API_KEY`), following this project's established secrets pattern for every other credential (JWT secret, DB passwords, etc. — all flow through `AppConfig`'s env-var-first resolution).

### Frontend: `login.html` addition + new `reset-password.html` page

- `login.html`: a "Forgot password?" link under the Sign In tab's password field, opening a small inline section (not a separate page — consistent with this file's existing tab-based single-page pattern for Sign In / Create Account) with an email field and a "Send Reset Link" button. On submit: `POST /api/auth/forgot-password`, then show the identical generic success message regardless of the (invisible-to-the-client) email-exists outcome — this reinforces the anti-enumeration decision at the UI layer too, not just the API layer.
- New page `/reset-password?token=X`: new `ViewController.resetPassword()` route + new `reset-password.html` template. Reuses the password-complexity-checklist and password-confirmation UI patterns already built in `login.html` (from the first two sub-projects in this series) rather than reinventing them — same visual style (`.auth-card`, `.field`, checklist colors). On submit: `POST /api/auth/reset-password` with the token read from the URL's query string (`URLSearchParams`) and the new password; on success, redirect to `/login` with a success indicator (e.g. a query param the login page reads to show a one-time "Password reset — please sign in" banner); on failure, show the server's error message inline (same pattern as the existing register/login error handling).

## Out of scope

- No "your password was changed" notification email (a genuinely separate feature, not requested).
- No session/refresh-token revocation on successful reset.
- No rate limit beyond the existing generic `/api/auth/**` bucket (no per-email cooldown, e.g. "wait 60s before requesting another reset link for the same address" — the shared IP-based bucket already bounds abuse volume adequately for this project's scale).
- No verified custom sending domain — sandbox `onboarding@resend.dev`, with the known real-recipient limitation explicitly accepted above.
- No cleanup/expiry job for old rows in `password_reset_tokens` (expired/used rows accumulate but are cheap and harmless at this project's scale — a periodic delete job would be premature optimization here).
