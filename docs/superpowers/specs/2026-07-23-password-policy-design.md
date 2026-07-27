# Design: Password Complexity Policy

**Date:** 2026-07-23
**Status:** Approved

---

## Context

First of four planned sub-projects reworking the signup/login module (security + UX pass on `AuthController`/`AuthService`/`login.html`). The other three — JWT storage migration (localStorage → httpOnly cookie), forgot-password flow (Resend), and login form UX polish — are separate specs, sequenced in that order, since JWT storage is the most cross-cutting and the other two build on top of it.

**Current state**: `AuthService.register()` validates the password only via `ValidationUtil.requireNonEmpty(request.getPassword(), "password")` — any non-blank string is accepted, including single-character passwords. No client-side feedback exists either; `login.html`'s register form only checks that the three required fields are non-empty before submitting.

**Goal**: enforce a minimum password strength at registration, following NIST-leaning guidance (favor length over arbitrary special-character requirements) rather than a maximalist complexity rule.

## Rule

A password is valid if it has:
- At least 10 characters
- At least 1 uppercase letter
- At least 1 digit

No special-character requirement — arbitrary composition rules push users toward predictable substitutions (`P@ssw0rd`) without a real security gain, and length is the stronger signal.

**Applies to registration only, never to login.** Existing accounts created before this rule ships keep working — there is no password migration, and login must never reject a correct password because it's "too weak" by the new rule.

## Server-side validation

New method in `ValidationUtil` (`src/main/java/com/st4r4x/util/ValidationUtil.java`), next to the existing `requireNonEmpty`/`requirePositive`/`validateFieldName`:

```java
private static final Pattern PASSWORD_UPPERCASE = Pattern.compile(".*[A-Z].*");
private static final Pattern PASSWORD_DIGIT = Pattern.compile(".*[0-9].*");

public static void requireValidPassword(String password) {
    requireNonEmpty(password, "password");
    if (password.length() < 10) {
        throw new IllegalArgumentException("password doit contenir au moins 10 caractères");
    }
    if (!PASSWORD_UPPERCASE.matcher(password).matches()) {
        throw new IllegalArgumentException("password doit contenir au moins une majuscule");
    }
    if (!PASSWORD_DIGIT.matcher(password).matches()) {
        throw new IllegalArgumentException("password doit contenir au moins un chiffre");
    }
}
```

Three distinct exception messages (length / uppercase / digit) rather than one generic message — `AuthController.register()` already surfaces `e.getMessage()` verbatim to the client via the existing error envelope, so specific messages reach the user for free without any controller change.

`AuthService.register()` calls `ValidationUtil.requireValidPassword(request.getPassword())` immediately after the existing `requireNonEmpty` call on username/email, before the username/email uniqueness checks — fail fast on the cheapest check first, consistent with the existing ordering in that method.

`AuthService.login()` and `AuthService.refresh()` are untouched — no call to `requireValidPassword` there.

## Client-side feedback

In `login.html`, register section only (`#sectionRegister`):

- Below the password field, three small indicator lines (length ≥10 / uppercase / digit), each toggling a checkmark/cross class on every `input` event on `#regPassword`. Pure visual feedback — reuses the existing `.hint`-style text sizing, colored red/green per criterion (consistent with the existing `.error-msg`/`.success-msg` red/green palette already used on this page).
- The `Create Account` button is **not** disabled based on these indicators — clicking it always submits, and the existing error-display path (`registerError.textContent = result.body.message`) shows the server's rejection message if the password fails server-side validation. This keeps the server the single source of truth and avoids duplicating the rule in JS in a way that could drift from `ValidationUtil`.

## Testing

- `ValidationUtilTest` (new or extended, check if one already exists): cases for `requireValidPassword` — valid password passes; too-short rejected; no-uppercase rejected; no-digit rejected; each assertion checks both the exception type (`IllegalArgumentException`) and that the message names the specific failed criterion.
- `AuthServiceTest`: one new case — `register()` with a weak password throws `IllegalArgumentException` and never reaches `userRepository.save()` (verify `save` is never called, consistent with how the existing duplicate-username/email tests in that class are structured).

## Out of scope

- No password strength meter (zxcvbn-style entropy scoring) — the three-criteria checklist is sufficient for this project's threat model.
- No change to login, refresh, or any endpoint other than `POST /api/auth/register`.
- No migration or forced reset for existing weak passwords.
- No rate-limit change (existing `/api/auth/**` Bucket4j limiter is untouched).
