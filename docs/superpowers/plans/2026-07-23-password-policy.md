# Password Complexity Policy Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Enforce a minimum password strength (10+ chars, 1 uppercase, 1 digit) at registration only, validated server-side in `ValidationUtil` with a live client-side checklist in `login.html`.

**Architecture:** A new `ValidationUtil.requireValidPassword(String)` static method throws `IllegalArgumentException` with a specific message per failed criterion. `AuthService.register()` calls it right after the existing `requireNonEmpty` checks, before the username/email uniqueness lookups. `login.html`'s register form gets a live 3-line checklist under the password field, purely cosmetic — the button always submits, and the server's rejection message (already surfaced verbatim by `AuthController`) is the actual enforcement.

**Tech Stack:** Java 25, JUnit 5 + Mockito (existing test stack), vanilla JS (existing `login.html` pattern, no build step).

## Global Constraints

- Password rule: minimum 10 characters, at least 1 uppercase letter (`A-Z`), at least 1 digit (`0-9`). No special-character requirement.
- Applies to `POST /api/auth/register` only. Never to `login()` or `refresh()`.
- No password migration for existing accounts — the rule is enforced only at the moment of registration.
- Server-side validation is the source of truth. Client-side feedback is cosmetic and must never block submission.
- Commits: English, imperative mood, conventional-commits prefix (`feat|fix|docs|chore|refactor|test|ci|style`), subject ≤72 chars, no trailing period.
- Work happens on branch `feature/password-policy` (already created and pushed with the design spec) — never commit this work directly to `main`.

---

### Task 1: Add `requireValidPassword` to `ValidationUtil` with unit tests

**Files:**
- Modify: `src/main/java/com/st4r4x/util/ValidationUtil.java`
- Modify: `src/test/java/com/st4r4x/util/ValidationUtilTest.java`

**Interfaces:**
- Consumes: nothing new.
- Produces: `public static void ValidationUtil.requireValidPassword(String password)` — throws `IllegalArgumentException` with message `"password ne peut pas être null ou vide"` (via existing `requireNonEmpty`) if null/blank, `"password doit contenir au moins 10 caractères"` if length < 10, `"password doit contenir au moins une majuscule"` if no uppercase letter, `"password doit contenir au moins un chiffre"` if no digit. Task 2 (`AuthService`) calls this method.

- [ ] **Step 1: Write the failing tests**

Add these test methods to `src/test/java/com/st4r4x/util/ValidationUtilTest.java`, right after the existing `testValidateFieldName_EmptyFieldName` method (before the closing `}` of the class):

```java
    @Test
    void testRequireValidPassword_ValidPassword() {
        // Should not throw: 10+ chars, has uppercase, has digit
        ValidationUtil.requireValidPassword("Password12");
    }

    @Test
    void testRequireValidPassword_NullPassword() {
        assertThrows(IllegalArgumentException.class, () ->
            ValidationUtil.requireValidPassword(null));
    }

    @Test
    void testRequireValidPassword_EmptyPassword() {
        assertThrows(IllegalArgumentException.class, () ->
            ValidationUtil.requireValidPassword(""));
    }

    @Test
    void testRequireValidPassword_TooShort() {
        IllegalArgumentException ex = assertThrows(IllegalArgumentException.class, () ->
            ValidationUtil.requireValidPassword("Short1"));
        assertTrue(ex.getMessage().contains("10 caractères"));
    }

    @Test
    void testRequireValidPassword_NoUppercase() {
        IllegalArgumentException ex = assertThrows(IllegalArgumentException.class, () ->
            ValidationUtil.requireValidPassword("lowercase123"));
        assertTrue(ex.getMessage().contains("majuscule"));
    }

    @Test
    void testRequireValidPassword_NoDigit() {
        IllegalArgumentException ex = assertThrows(IllegalArgumentException.class, () ->
            ValidationUtil.requireValidPassword("NoDigitsHere"));
        assertTrue(ex.getMessage().contains("chiffre"));
    }

    @Test
    void testRequireValidPassword_ExactlyTenChars() {
        // Boundary: exactly 10 chars, has uppercase and digit — should pass
        ValidationUtil.requireValidPassword("Abcdefghi1");
    }

    @Test
    void testRequireValidPassword_NineChars() {
        // Boundary: 9 chars — should fail on length
        assertThrows(IllegalArgumentException.class, () ->
            ValidationUtil.requireValidPassword("Abcdefgh1"));
    }
```

Add the missing static import at the top of the file (the class currently only imports `assertThrows`):

```java
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
```

- [ ] **Step 2: Run tests to verify they fail**

```bash
mvn test -Dtest=ValidationUtilTest -q
```

Expected: compile error — `requireValidPassword` does not exist on `ValidationUtil`.

- [ ] **Step 3: Implement `requireValidPassword`**

Edit `src/main/java/com/st4r4x/util/ValidationUtil.java` — add a `Pattern` import and the new method after `requireNonEmpty`:

```java
package com.st4r4x.util;

import java.util.regex.Pattern;

/**
 * Utilitaires de validation
 */
public class ValidationUtil {

    private static final Pattern PASSWORD_UPPERCASE = Pattern.compile(".*[A-Z].*");
    private static final Pattern PASSWORD_DIGIT = Pattern.compile(".*[0-9].*");

    private ValidationUtil() {
        // Classe utilitaire
    }

    /**
     * Valide qu'une string n'est pas null ou vide
     */
    public static void requireNonEmpty(String value, String fieldName) {
        if (value == null || value.trim().isEmpty()) {
            throw new IllegalArgumentException(fieldName + " ne peut pas être null ou vide");
        }
    }

    /**
     * Valide qu'un mot de passe respecte la politique de complexité :
     * au moins 10 caractères, une majuscule, un chiffre.
     * Ne s'applique qu'à l'inscription — jamais à la connexion.
     */
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

    /**
     * Valide qu'un nombre est positif
     */
    public static void requirePositive(int value, String fieldName) {
        if (value <= 0) {
            throw new IllegalArgumentException(fieldName + " doit être positif, reçu: " + value);
        }
    }

    /**
     * Validate qu'un fieldName ne contient pas de caractères dangereux
     */
    public static void validateFieldName(String fieldName) {
        requireNonEmpty(fieldName, "fieldName");
        if (!fieldName.matches("^[a-zA-Z0-9_]+$")) {
            throw new IllegalArgumentException("fieldName contient des caractères invalides: " + fieldName);
        }
    }
}
```

- [ ] **Step 4: Run tests to verify they pass**

```bash
mvn test -Dtest=ValidationUtilTest -q
```

Expected: `BUILD SUCCESS`, all tests pass (existing 12 + 8 new = 20 tests).

- [ ] **Step 5: Commit**

```bash
git add src/main/java/com/st4r4x/util/ValidationUtil.java src/test/java/com/st4r4x/util/ValidationUtilTest.java
git commit -m "feat(auth): add password complexity validation to ValidationUtil

requireValidPassword enforces 10+ chars, 1 uppercase, 1 digit — no
special-character requirement. Not yet wired into AuthService.register()."
```

---

### Task 2: Wire `requireValidPassword` into `AuthService.register()`

**Files:**
- Modify: `src/main/java/com/st4r4x/service/AuthService.java:52-56`
- Modify: `src/test/java/com/st4r4x/service/AuthServiceTest.java`

**Interfaces:**
- Consumes: `ValidationUtil.requireValidPassword(String)` from Task 1.
- Produces: `AuthService.register(RegisterRequest)` now rejects weak passwords with `IllegalArgumentException` before checking username/email uniqueness. No signature change — `AuthController.register()` (untouched) already catches `IllegalArgumentException` and returns 400 with `e.getMessage()`.

- [ ] **Step 1: Write the failing test**

Every existing test in `AuthServiceTest` that calls `register()` successfully uses a password like `"password123"` (12 chars, no uppercase) or `"pass"` (4 chars) — these will now fail the new rule. Add one new failing-path test, then fix the existing passing-path tests in Step 3.

Add this test to `src/test/java/com/st4r4x/service/AuthServiceTest.java`, in the `// ── register ──` section, right after `register_throws_whenUsernameIsBlank`:

```java
    @Test
    void register_throws_whenPasswordTooWeak() {
        RegisterRequest req = new RegisterRequest();
        req.setUsername("alice");
        req.setEmail("alice@example.com");
        req.setPassword("weak"); // 4 chars, no uppercase, no digit

        IllegalArgumentException ex = assertThrows(IllegalArgumentException.class,
                () -> authService.register(req));
        assertTrue(ex.getMessage().contains("10 caractères"));
        verify(userRepository, never()).save(any(UserEntity.class));
    }
```

Add the missing static import at the top of the file (check current imports first — `verify`, `never`, `any` are already statically imported via `import static org.mockito.Mockito.*;`, and `assertTrue`/`assertThrows` via `import static org.junit.jupiter.api.Assertions.*;` — both wildcard imports already exist in this file, so no new import line is needed).

- [ ] **Step 2: Run test to verify it fails**

```bash
mvn test -Dtest=AuthServiceTest#register_throws_whenPasswordTooWeak -q
```

Expected: FAIL — `register()` does not yet call `requireValidPassword`, so no exception is thrown and the test's `assertThrows` fails.

- [ ] **Step 3: Implement the wiring, and fix existing tests' passwords**

Edit `src/main/java/com/st4r4x/service/AuthService.java`, in `register()`:

```java
    public JwtResponse register(RegisterRequest request) {
        ValidationUtil.requireNonEmpty(request.getUsername(), "username");
        ValidationUtil.requireNonEmpty(request.getEmail(), "email");
        ValidationUtil.requireValidPassword(request.getPassword());

        if (userRepository.findByUsername(request.getUsername()).isPresent()) {
            throw new IllegalArgumentException("Username already exists");
        }
```

(This replaces the single line `ValidationUtil.requireNonEmpty(request.getPassword(), "password");` with `ValidationUtil.requireValidPassword(request.getPassword());` — `requireValidPassword` already calls `requireNonEmpty` internally per Task 1, so the null/blank check is preserved.)

Now fix every existing test in `AuthServiceTest` whose `req.setPassword(...)` value would fail the new rule, so they keep passing. Change these exact lines:

- `register_returnsTokens_onSuccess`: line `req.setPassword("password123");` → `req.setPassword("Password123");` (add uppercase). Also update the two mock stubs referencing that literal: `when(passwordEncoder.encode("password123")).thenReturn("hashed");` → `when(passwordEncoder.encode("Password123")).thenReturn("hashed");`.
- `register_throws_whenUsernameAlreadyExists`: `req.setPassword("password123");` → `req.setPassword("Password123");` (cosmetic only — this test throws before reaching the password check anyway, since username-exists is checked after password validation in the new order; keep it valid for clarity and future-proofing).
- `register_throws_whenEmailAlreadyExists`: same change, `"password123"` → `"Password123"`.
- `register_throws_whenUsernameIsBlank`: `req.setPassword("password123");` → `req.setPassword("Password123");` (this test throws on username, before reaching password validation — but keep the value valid so the test still isolates the username failure specifically).
- `register_assignsCustomerRole_whenNoSignupCode`: `req.setPassword("pass");` → `req.setPassword("Password123");`.
- `register_assignsControllerRole_whenCorrectSignupCode`: `req.setPassword("pass");` → `req.setPassword("Password123");`.
- `register_throws_whenWrongSignupCode`: `req.setPassword("pass");` → `req.setPassword("Password123");` (this test's assertion is about the signup-code rejection message, not password — keep the password valid so the test isolates the code failure).
- `register_throws_whenSignupCodeEnvVarAbsent`: `req.setPassword("pass");` → `req.setPassword("Password123");` (same reasoning).
- `register_assignsAdminRole_whenCorrectAdminSignupCode`: `req.setPassword("pass");` → `req.setPassword("Password123");`.
- `register_doesNotAssignAdmin_whenAdminCodeDisabled`: `req.setPassword("pass");` → `req.setPassword("Password123");`.
- `register_adminCodeTakesPriorityOverControllerCode`: `req.setPassword("pass");` → `req.setPassword("Password123");`.

- [ ] **Step 4: Run all AuthServiceTest tests to verify they pass**

```bash
mvn test -Dtest=AuthServiceTest -q
```

Expected: `BUILD SUCCESS`, all tests pass (existing 13 + 1 new = 14 tests).

- [ ] **Step 5: Run the full test suite to check for other breakage**

```bash
mvn test -q
```

Expected: `BUILD SUCCESS`. `src/test/java/com/st4r4x/controller/AuthControllerTest.java` has password literals like `"password123"` and `"password"`, but that file mocks `AuthService` entirely (`@Mock private AuthService authService; @InjectMocks private AuthController authController;`) — `authService.register(req)` is stubbed directly, so the real `ValidationUtil.requireValidPassword` is never invoked. No changes needed there; this step is just confirming that expectation holds (full suite green).

- [ ] **Step 6: Commit**

```bash
git add src/main/java/com/st4r4x/service/AuthService.java src/test/java/com/st4r4x/service/AuthServiceTest.java
git commit -m "feat(auth): enforce password complexity on registration

AuthService.register() now rejects passwords under 10 chars or missing
an uppercase/digit before checking username/email uniqueness. login()
and refresh() are untouched — existing accounts keep working."
```

If Step 5 required fixing `AuthControllerTest.java`, include it in this commit's `git add` and mention it in the message.

---

### Task 3: Add live password-strength checklist to `login.html`

**Files:**
- Modify: `src/main/resources/templates/login.html`

**Interfaces:**
- Consumes: nothing from Task 1/2 directly — this is pure client-side UI. Relies on the server-side error message format from Task 1 (`"password doit contenir au moins ..."`) being surfaced by the existing `registerError.textContent = result.body.message` line, which needs no code change.
- Produces: nothing consumed by other tasks — this is the final UI layer.

- [ ] **Step 1: Add the checklist markup**

In `src/main/resources/templates/login.html`, find the password field block inside `#sectionRegister` (currently lines 65-68):

```html
          <div class="field">
            <label for="regPassword">Password</label>
            <input id="regPassword" type="password" placeholder="••••••••" autocomplete="new-password" />
          </div>
```

Replace it with:

```html
          <div class="field">
            <label for="regPassword">Password</label>
            <input id="regPassword" type="password" placeholder="••••••••" autocomplete="new-password" />
            <ul id="pwChecklist" style="list-style:none;padding:0;margin:6px 0 0;font-size:0.75rem;">
              <li id="pwCheckLength" style="color:#aaa;">— At least 10 characters</li>
              <li id="pwCheckUpper" style="color:#aaa;">— At least 1 uppercase letter</li>
              <li id="pwCheckDigit" style="color:#aaa;">— At least 1 digit</li>
            </ul>
          </div>
```

- [ ] **Step 2: Add the live-check JavaScript**

In the `<script>` block, right before the line `document.getElementById("registerBtn").addEventListener("click", () => {` (currently line 152), insert:

```javascript
      document.getElementById("regPassword").addEventListener("input", (e) => {
        const value = e.target.value;
        const checks = [
          { id: "pwCheckLength", pass: value.length >= 10 },
          { id: "pwCheckUpper", pass: /[A-Z]/.test(value) },
          { id: "pwCheckDigit", pass: /[0-9]/.test(value) },
        ];
        checks.forEach(({ id, pass }) => {
          const el = document.getElementById(id);
          el.style.color = pass ? "#2e7d32" : "#c62828";
          el.textContent = (pass ? "✓ " : "✗ ") + el.textContent.replace(/^[✓✗—] /, "");
        });
      });
```

- [ ] **Step 3: Verify manually (no JS test suite exists for this project's frontend)**

Start the app (see `docs/development.md` if unsure — typically `docker compose up -d` then `mvn spring-boot:run`), then:

1. Open `http://localhost:8080/login` in a browser.
2. Click the "Create Account" tab.
3. Type into the Password field one character at a time: `w`, `wea`, `weak`, `Weak1234`, `Weak12345`.
4. Expected: all 3 checklist lines start grey with `—`. Typing `w`/`wea`/`weak` keeps length red `✗`, adds green `✓` for uppercase once an uppercase letter is typed, adds green `✓` for digit once a digit is typed. At `Weak12345` (10 chars, has `W`, has digits) all 3 lines turn green `✓`.
5. Submit the form with a password that fails server-side validation (e.g. temporarily type `short1A` and click "Create Account" before finishing the full string, or use browser devtools to bypass the input — simplest: just submit `"weak"` by clicking the button right after typing it). Expected: the existing `#registerError` box shows the server's message (e.g. `"password doit contenir au moins 10 caractères"`), and the account is NOT created (submitting again with a valid password should still work).

- [ ] **Step 4: Commit**

```bash
git add src/main/resources/templates/login.html
git commit -m "feat(auth): add live password strength checklist to signup form

Cosmetic only — the Create Account button still always submits, and
the server's rejection message (already surfaced verbatim) is the
real enforcement. Guides the user before they hit submit."
```

---

### Task 4: Push branch and open PR

**Files:** none (git/GitHub operations only).

**Interfaces:**
- Consumes: all commits from Tasks 1-3 on `feature/password-policy`.
- Produces: an open PR against `main`, ready for CI.

- [ ] **Step 1: Push the branch**

```bash
git push origin feature/password-policy
```

- [ ] **Step 2: Open the PR**

```bash
gh pr create --repo St4r4x/restaurant-analytics --title "feat(auth): enforce password complexity policy on registration" --body "$(cat <<'EOF'
## Summary
- Adds ValidationUtil.requireValidPassword() — 10+ chars, 1 uppercase, 1 digit, no special-character requirement.
- Wired into AuthService.register() only — login()/refresh() and existing accounts are untouched.
- Live client-side checklist in login.html's signup form (cosmetic — server stays the source of truth).
- Design spec: docs/superpowers/specs/2026-07-23-password-policy-design.md

## Test plan
- [x] ValidationUtilTest covers valid/too-short/no-uppercase/no-digit/boundary cases
- [x] AuthServiceTest covers the new rejection path, all pre-existing tests updated to use a valid password literal
- [x] Manual browser check: live checklist updates correctly, server rejection message surfaces on submit with a weak password
EOF
)"
```

- [ ] **Step 3: Wait for CI, verify green**

```bash
gh pr checks --repo St4r4x/restaurant-analytics --watch --interval 20
```

Expected: all checks (Build, Unit Tests, Integration Tests, Secret Scan, E2E Smoke Test, Docker Build and Push) pass.

Do not merge — leave the PR open for the user to review and merge, per this repo's established workflow of user-reviewed merges on this feature series.

---

## Self-Review Notes

- **Spec coverage**: rule definition (Task 1), server enforcement scoped to registration only (Task 2), client-side cosmetic feedback (Task 3), all four spec sections covered. "Out of scope" items (strength meter, login/refresh changes, migration, rate-limit changes) are correctly absent from all tasks.
- **No placeholders**: every step shows exact code, exact file paths, exact test names and assertions.
- **Type/name consistency**: `requireValidPassword(String password)` signature is identical everywhere it's referenced (Task 1 definition, Task 2 call site, Task 4 PR description). Error message substrings (`"10 caractères"`, `"majuscule"`, `"chiffre"`) match between Task 1's implementation and Task 1/2's test assertions.
- **Existing-test breakage anticipated**: Task 2 explicitly identifies and fixes every pre-existing `AuthServiceTest` password literal that would break under the new rule, rather than leaving that discovery to a failing CI run. Task 2 Step 5 also proactively greps for an `AuthControllerTest` (mentioned in `CHANGELOG.md`'s Unreleased section) that might have the same issue.
