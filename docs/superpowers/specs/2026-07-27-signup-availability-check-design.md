# Design: Live Username/Email Availability Check + Password Confirmation

**Date:** 2026-07-27
**Status:** Approved

---

## Context

Second sub-project in the signup/login rework series (after the password complexity policy, `docs/superpowers/specs/2026-07-23-password-policy-design.md`, currently PR #14 not yet merged). While testing that PR's dev container, the user surfaced two gaps in the registration form:

1. No live feedback on whether the chosen username/email is already taken — `AuthService.register()` already checks uniqueness server-side (`findByUsername`/`findByEmail`, HTTP 400 `"Username already exists"`/`"Email already exists"`), but the user only learns this after clicking "Create Account" and getting the error back.
2. No password confirmation field — a typo in the password field is only caught the next time the user tries to log in and fails, with no way to tell "I mistyped it at signup" apart from "I forgot it."

This spec covers both, since they land in the same form and were raised together — but they are functionally independent and implemented as separable tasks.

## Part 1 — Username/email availability check

### Trigger: blur, not live-as-you-type

Checked on `blur` of `#regUsername`/`#regEmail` (when the field loses focus), not on every keystroke — standard UX pattern (GitHub, Twitter signup forms), avoids a network call per keystroke. Only fires if the field is non-empty.

### New endpoints

Two new `GET` endpoints on `AuthController`, following the existing `@Operation`/`@GetMapping` Swagger pattern used elsewhere in the codebase (e.g. `RestaurantController`) — though note `AuthController` currently has no `@Tag` and no `@Operation` annotations at all; add them to the two new endpoints only, don't retrofit the three existing POST endpoints (out of scope, unrelated to this change):

- `GET /api/auth/check-username?username=X` → `{ "available": true }` or `{ "available": false }`
- `GET /api/auth/check-email?email=X` → `{ "available": true }` or `{ "available": false }`

Both are public (no auth), matching the existing `/api/auth/**` endpoints. Both fall under the existing `RateLimitFilter`'s strict `/api/auth/**` bucket automatically — `RateLimitFilter.shouldNotFilter()` matches on `uri.startsWith("/api/auth/")`, so no filter change is needed.

New `AuthService` methods reuse the existing repository calls instead of duplicating the lookup logic:

```java
public boolean isUsernameAvailable(String username) {
    ValidationUtil.requireNonEmpty(username, "username");
    return userRepository.findByUsername(username).isEmpty();
}

public boolean isEmailAvailable(String email) {
    ValidationUtil.requireNonEmpty(email, "email");
    return userRepository.findByEmail(email).isEmpty();
}
```

Controller methods return `ResponseEntity.ok(Map.of("available", authService.isUsernameAvailable(username)))` (and the email equivalent) — wrapped in the same try/catch IllegalArgumentException→400 pattern as the existing three endpoints, for a blank/missing query param.

### Client-side feedback

On blur, fetch the relevant endpoint, then show inline next to the field (reusing the existing checklist color palette from the password-policy work — `#2e7d32` green / `#c62828` red):
- While the request is in flight: small grey "Checking…" text.
- Available: green "✓ Username available" / "✓ Email available".
- Taken: red "✗ Username already taken" / "✗ Email already registered".
- Network/error case: no message shown (fail silent — the server-side check at submit is still the real gate, so a failed availability probe shouldn't block or scare the user with a spurious error).

**Cosmetic only, like the password checklist:** the "Create Account" button is never disabled based on this result. A race condition (someone else takes the username between the blur-check and the actual submit) is still caught by the server's existing uniqueness check at `POST /api/auth/register` — this endpoint is a UX convenience, not a new authority.

## Part 2 — Password confirmation field

### New field

`#regPasswordConfirm`, placed between the existing `#regPassword` field (with its complexity checklist from the prior sub-project) and the `Inspector Code` field.

### Live comparison

On `input` of `#regPasswordConfirm` (only once it's non-empty — don't show a "doesn't match" error against an empty confirm field the user hasn't started typing into yet): compare against `#regPassword`'s current value.
- Mismatch: red text "Les mots de passe ne correspondent pas" under the confirm field (same red as other error messaging on this page, `#c62828`).
- Match: no message (clear it).
- Also re-check on `input` of `#regPassword` itself, in case the user edits the first field after already filling the second — otherwise a stale "matches" state could linger incorrectly.

### Submit gating — the one place this differs from the password-complexity checklist

Unlike password complexity (server-side is the enforcement, client-side is pure decoration), password confirmation **never reaches the server** — `RegisterRequest` carries a single `password` field; the confirmation value is never sent. There is no server-side equivalent check possible, so the client-side check is the *only* protection against a typo shipping through.

Therefore: the "Create Account" button click handler validates client-side that `#regPassword.value === #regPasswordConfirm.value` *before* doing anything else (before even the existing required-fields check). If they don't match, show the mismatch error and return early — no fetch call is made. This is a genuine gate, not decoration, specifically because no other layer catches this particular mistake.

## Out of scope

- No password-strength meter changes (already covered by the prior sub-project).
- No change to `RegisterRequest`, `POST /api/auth/register`, or any part of `AuthService.register()` beyond adding the two new independent availability methods.
- No debounce/live-as-you-type checking for username/email — blur-triggered only.
- No CAPTCHA or bot-protection on the new endpoints — same trust model as the existing `/api/auth/**` surface, protected only by the existing rate limiter.
- No caching of availability results client-side (e.g. re-checking on every blur even if the value hasn't changed since the last check) — simplicity over a minor redundant-request optimization.
