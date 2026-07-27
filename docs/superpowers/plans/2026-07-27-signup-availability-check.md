# Signup Availability Check + Password Confirmation Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Add live username/email availability checking (on blur) and a password confirmation field (submit-gating) to the registration form.

**Architecture:** Two new public `GET` endpoints on `AuthController` (`check-username`, `check-email`) backed by two new pure-lookup methods on `AuthService` that reuse the existing `userRepository.findByUsername`/`findByEmail` calls. `login.html`'s register form gets: (1) blur listeners on username/email that call the new endpoints and show cosmetic ✓/✗ feedback, and (2) a new password-confirm field whose mismatch blocks the "Create Account" click handler before any fetch call is made — the only submit-gating check in this form, since no server-side equivalent is possible (the server never receives a confirmation value).

**Tech Stack:** Java 25, Spring Boot 4 (`@RestController`, `@GetMapping`), JUnit 5 + Mockito (existing test stack), vanilla JS (existing `login.html` pattern, no build step).

## Global Constraints

- New endpoints: `GET /api/auth/check-username?username=X` → `{ "available": true|false }`; `GET /api/auth/check-email?email=X` → `{ "available": true|false }`. Both public, no auth.
- Both endpoints fall under the existing `RateLimitFilter`'s `/api/auth/**` strict bucket automatically — no filter code changes needed.
- Availability check triggers on `blur`, not on every keystroke. Only fires if the field is non-empty.
- Availability feedback is cosmetic only — never disables or blocks the "Create Account" button. A failed/errored availability probe shows no message (fail silent); the server's existing uniqueness check at `POST /api/auth/register` remains the real gate.
- Password confirmation is the one gating check in this form: if `#regPassword` and `#regPasswordConfirm` don't match, the button's click handler must show the mismatch error and `return` before making any fetch call.
- No changes to `RegisterRequest`, `POST /api/auth/register`, or any existing `AuthService.register()` logic beyond adding two new independent methods.
- Commits: English, imperative mood, conventional-commits prefix (`feat|fix|docs|chore|refactor|test|ci|style`), subject ≤72 chars, no trailing period.
- Work happens on branch `feature/signup-availability-check` (already created, rebased onto `feature/password-policy`, and pushed with the design spec) — never commit this work directly to `main`.

---

### Task 1: Add `isUsernameAvailable`/`isEmailAvailable` to `AuthService` with unit tests

**Files:**
- Modify: `src/main/java/com/st4r4x/service/AuthService.java`
- Modify: `src/test/java/com/st4r4x/service/AuthServiceTest.java`

**Interfaces:**
- Consumes: nothing new — reuses `userRepository.findByUsername(String)`/`findByEmail(String)` already injected into `AuthService`.
- Produces: `public boolean AuthService.isUsernameAvailable(String username)` and `public boolean AuthService.isEmailAvailable(String email)` — both throw `IllegalArgumentException` (via `ValidationUtil.requireNonEmpty`) if the argument is null/blank, otherwise return `true` if no existing user has that username/email, `false` otherwise. Task 2 (`AuthController`) calls both methods.

- [ ] **Step 1: Write the failing tests**

Add these test methods to `src/test/java/com/st4r4x/service/AuthServiceTest.java`, in a new section right after the `// ── role-assignment ──` section's last test (`register_adminCodeTakesPriorityOverControllerCode`, currently ending the file) — add before the final closing `}` of the class:

```java
    // ── availability checks ──────────────────────────────────────────────────

    @Test
    void isUsernameAvailable_returnsTrue_whenNotTaken() {
        when(userRepository.findByUsername("newuser")).thenReturn(Optional.empty());

        assertTrue(authService.isUsernameAvailable("newuser"));
    }

    @Test
    void isUsernameAvailable_returnsFalse_whenTaken() {
        when(userRepository.findByUsername("alice")).thenReturn(Optional.of(new UserEntity()));

        assertFalse(authService.isUsernameAvailable("alice"));
    }

    @Test
    void isUsernameAvailable_throws_whenBlank() {
        assertThrows(IllegalArgumentException.class, () -> authService.isUsernameAvailable(""));
    }

    @Test
    void isEmailAvailable_returnsTrue_whenNotTaken() {
        when(userRepository.findByEmail("new@example.com")).thenReturn(Optional.empty());

        assertTrue(authService.isEmailAvailable("new@example.com"));
    }

    @Test
    void isEmailAvailable_returnsFalse_whenTaken() {
        when(userRepository.findByEmail("alice@example.com")).thenReturn(Optional.of(new UserEntity()));

        assertFalse(authService.isEmailAvailable("alice@example.com"));
    }

    @Test
    void isEmailAvailable_throws_whenBlank() {
        assertThrows(IllegalArgumentException.class, () -> authService.isEmailAvailable(null));
    }
```

- [ ] **Step 2: Run tests to verify they fail**

```bash
mvn test -Dtest=AuthServiceTest -q
```

Expected: compile error — `isUsernameAvailable`/`isEmailAvailable` do not exist on `AuthService`.

- [ ] **Step 3: Implement the two methods**

Edit `src/main/java/com/st4r4x/service/AuthService.java` — add both methods after `register(RegisterRequest request)` and before `login(AuthRequest request)`:

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

- [ ] **Step 4: Run tests to verify they pass**

```bash
mvn test -Dtest=AuthServiceTest -q
```

Expected: `BUILD SUCCESS`, all tests pass (existing 14 + 6 new = 20 tests).

- [ ] **Step 5: Commit**

```bash
git add src/main/java/com/st4r4x/service/AuthService.java src/test/java/com/st4r4x/service/AuthServiceTest.java
git commit -m "feat(auth): add username/email availability checks to AuthService

isUsernameAvailable/isEmailAvailable reuse the existing repository
lookups used by register()'s uniqueness check. Not yet exposed via
any endpoint."
```

---

### Task 2: Expose availability checks via `AuthController`

**Files:**
- Modify: `src/main/java/com/st4r4x/controller/AuthController.java`
- Modify: `src/test/java/com/st4r4x/controller/AuthControllerTest.java`

**Interfaces:**
- Consumes: `AuthService.isUsernameAvailable(String)`/`isEmailAvailable(String)` from Task 1.
- Produces: `GET /api/auth/check-username?username=X` and `GET /api/auth/check-email?email=X`, both returning `200 OK` with body `{"available": true|false}` on success, `400 Bad Request` with the existing `errorResponse(e)` envelope if the query param is missing/blank. Task 3 (client-side) calls both endpoints.

- [ ] **Step 1: Write the failing tests**

Add these test methods to `src/test/java/com/st4r4x/controller/AuthControllerTest.java`, in a new section right after the `// ── refresh ──` section's last test (`refresh_returns400_onExpiredOrInvalidToken`) — add before the final closing `}` of the class:

```java
    // ── check-username ───────────────────────────────────────────────────────

    @Test
    void checkUsername_returns200_withAvailableTrue() {
        when(authService.isUsernameAvailable("newuser")).thenReturn(true);

        ResponseEntity<?> response = authController.checkUsername("newuser");

        assertEquals(200, response.getStatusCode().value());
        assertEquals(Map.of("available", true), response.getBody());
    }

    @Test
    void checkUsername_returns200_withAvailableFalse() {
        when(authService.isUsernameAvailable("alice")).thenReturn(false);

        ResponseEntity<?> response = authController.checkUsername("alice");

        assertEquals(200, response.getStatusCode().value());
        assertEquals(Map.of("available", false), response.getBody());
    }

    @Test
    void checkUsername_returns400_onBlankUsername() {
        when(authService.isUsernameAvailable("")).thenThrow(new IllegalArgumentException("username ne peut pas être null ou vide"));

        ResponseEntity<?> response = authController.checkUsername("");

        assertEquals(400, response.getStatusCode().value());
    }

    // ── check-email ───────────────────────────────────────────────────────────

    @Test
    void checkEmail_returns200_withAvailableTrue() {
        when(authService.isEmailAvailable("new@example.com")).thenReturn(true);

        ResponseEntity<?> response = authController.checkEmail("new@example.com");

        assertEquals(200, response.getStatusCode().value());
        assertEquals(Map.of("available", true), response.getBody());
    }

    @Test
    void checkEmail_returns200_withAvailableFalse() {
        when(authService.isEmailAvailable("alice@example.com")).thenReturn(false);

        ResponseEntity<?> response = authController.checkEmail("alice@example.com");

        assertEquals(200, response.getStatusCode().value());
        assertEquals(Map.of("available", false), response.getBody());
    }

    @Test
    void checkEmail_returns400_onBlankEmail() {
        when(authService.isEmailAvailable("")).thenThrow(new IllegalArgumentException("email ne peut pas être null ou vide"));

        ResponseEntity<?> response = authController.checkEmail("");

        assertEquals(400, response.getStatusCode().value());
    }
```

Add the missing import at the top of the file (the class currently has no `java.util.Map` import):

```java
import java.util.Map;
```

- [ ] **Step 2: Run tests to verify they fail**

```bash
mvn test -Dtest=AuthControllerTest -q
```

Expected: compile error — `checkUsername`/`checkEmail` do not exist on `AuthController`.

- [ ] **Step 3: Implement the two endpoints**

Edit `src/main/java/com/st4r4x/controller/AuthController.java`. Add the `GetMapping`/`RequestParam` imports and `java.util.Map`:

```java
import java.util.Map;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.CrossOrigin;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
```

Add both endpoints after `refresh(RefreshRequest request)` and before the private `errorResponse` helper:

```java
    @GetMapping("/check-username")
    public ResponseEntity<?> checkUsername(@RequestParam String username) {
        try {
            boolean available = authService.isUsernameAvailable(username);
            return ResponseEntity.ok(Map.of("available", available));
        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest().body(errorResponse(e));
        } catch (Exception e) {
            return ResponseEntity.internalServerError().body(errorResponse(e));
        }
    }

    @GetMapping("/check-email")
    public ResponseEntity<?> checkEmail(@RequestParam String email) {
        try {
            boolean available = authService.isEmailAvailable(email);
            return ResponseEntity.ok(Map.of("available", available));
        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest().body(errorResponse(e));
        } catch (Exception e) {
            return ResponseEntity.internalServerError().body(errorResponse(e));
        }
    }
```

- [ ] **Step 4: Run tests to verify they pass**

```bash
mvn test -Dtest=AuthControllerTest -q
```

Expected: `BUILD SUCCESS`, all tests pass (existing 7 + 6 new = 13 tests).

- [ ] **Step 5: Run the full suite**

```bash
mvn test -q
```

Expected: `BUILD SUCCESS`.

- [ ] **Step 6: Commit**

```bash
git add src/main/java/com/st4r4x/controller/AuthController.java src/test/java/com/st4r4x/controller/AuthControllerTest.java
git commit -m "feat(auth): expose GET /api/auth/check-username and check-email

Public endpoints, same error envelope as the existing three POST
endpoints. Both fall under the existing RateLimitFilter's /api/auth/**
bucket automatically — no filter change needed. Not yet called from
the frontend."
```

---

### Task 3: Add live availability feedback to `login.html`

**Files:**
- Modify: `src/main/resources/templates/login.html`

**Interfaces:**
- Consumes: `GET /api/auth/check-username?username=X` and `GET /api/auth/check-email?email=X` from Task 2, both returning `{"available": boolean}` on 200.
- Produces: nothing consumed by other tasks — this is a UI layer. Task 4 (password confirmation) touches the same file but a different part of it (the password field block and the register button's click handler) — no interface overlap.

- [ ] **Step 1: Add feedback markup below the username and email fields**

In `src/main/resources/templates/login.html`, find the username and email field blocks inside `#sectionRegister` (currently lines 57-64):

```html
          <div class="field">
            <label for="regUsername">Username</label>
            <input id="regUsername" type="text" placeholder="choose a username" autocomplete="username" />
          </div>
          <div class="field">
            <label for="regEmail">Email</label>
            <input id="regEmail" type="email" placeholder="you@example.com" autocomplete="email" />
          </div>
```

Replace it with:

```html
          <div class="field">
            <label for="regUsername">Username</label>
            <input id="regUsername" type="text" placeholder="choose a username" autocomplete="username" />
            <div id="usernameAvailability" class="hint"></div>
          </div>
          <div class="field">
            <label for="regEmail">Email</label>
            <input id="regEmail" type="email" placeholder="you@example.com" autocomplete="email" />
            <div id="emailAvailability" class="hint"></div>
          </div>
```

- [ ] **Step 2: Add the availability-check JavaScript**

In the `<script>` block, right before the line `document.getElementById("registerBtn").addEventListener("click", () => {`, insert:

```javascript
      function checkAvailability(inputId, resultId, endpoint, param) {
        const input = document.getElementById(inputId);
        const resultEl = document.getElementById(resultId);
        input.addEventListener("blur", () => {
          const value = input.value.trim();
          if (!value) {
            resultEl.textContent = "";
            return;
          }
          resultEl.style.color = "#aaa";
          resultEl.textContent = "Checking…";
          fetch(`${endpoint}?${param}=${encodeURIComponent(value)}`)
            .then((res) => res.json())
            .then((data) => {
              if (data.available === true) {
                resultEl.style.color = "#2e7d32";
                resultEl.textContent = "✓ " + (param === "username" ? "Username" : "Email") + " available";
              } else if (data.available === false) {
                resultEl.style.color = "#c62828";
                resultEl.textContent = "✗ " + (param === "username" ? "Username already taken" : "Email already registered");
              } else {
                resultEl.textContent = "";
              }
            })
            .catch(() => {
              resultEl.textContent = "";
            });
        });
      }

      checkAvailability("regUsername", "usernameAvailability", "/api/auth/check-username", "username");
      checkAvailability("regEmail", "emailAvailability", "/api/auth/check-email", "email");
```

- [ ] **Step 3: Verify manually (no JS test suite exists for this project's frontend)**

Start the app (`docker compose up -d --build` per this repo's dev-container workflow, or `mvn spring-boot:run` with `.env` sourced), then:

1. Open `http://localhost:8080/login`, click "Create Account".
2. Type a username, then click into the Email field (triggers blur on username). Expected: "Checking…" briefly appears, then either "✓ Username available" (green) or "✗ Username already taken" (red) if that exact username already exists in this environment's database.
3. Repeat for the email field by clicking elsewhere after typing an email.
4. Confirm the "Create Account" button is never disabled by this feedback alone — you can still click it regardless of what the availability check shows.
5. Clear the username field entirely and blur it — expected: no message shown (empty field skips the check).

- [ ] **Step 4: Commit**

```bash
git add src/main/resources/templates/login.html
git commit -m "feat(auth): show live username/email availability on blur

Cosmetic only — the Create Account button still always submits. The
server's uniqueness check at POST /api/auth/register remains the real
gate; this is a UX convenience that can race harmlessly (someone else
takes the name between the blur-check and the actual submit)."
```

---

### Task 4: Add password confirmation field with submit gating

**Files:**
- Modify: `src/main/resources/templates/login.html`

**Interfaces:**
- Consumes: nothing from Tasks 1-3.
- Produces: nothing consumed by other tasks — final UI layer for this plan.

- [ ] **Step 1: Add the confirm-password field**

In `src/main/resources/templates/login.html`, find the password field block (which already has the complexity checklist from the prior sub-project):

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

Add a new field immediately after it (still before the `Inspector Code` field):

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
          <div class="field">
            <label for="regPasswordConfirm">Confirm Password</label>
            <input id="regPasswordConfirm" type="password" placeholder="••••••••" autocomplete="new-password" />
            <div id="passwordMismatch" class="hint" style="color:#c62828;display:none;">Les mots de passe ne correspondent pas</div>
          </div>
```

- [ ] **Step 2: Add live comparison JavaScript**

In the `<script>` block, right after the existing `regPassword` `input` listener (the one that updates `pwChecklist`, currently the block starting `document.getElementById("regPassword").addEventListener("input", (e) => {`), insert a new function and two listeners:

```javascript
      function checkPasswordsMatch() {
        const password = document.getElementById("regPassword").value;
        const confirm = document.getElementById("regPasswordConfirm").value;
        const mismatchEl = document.getElementById("passwordMismatch");
        if (confirm.length === 0) {
          mismatchEl.style.display = "none";
          return;
        }
        mismatchEl.style.display = password !== confirm ? "block" : "none";
      }

      document.getElementById("regPassword").addEventListener("input", checkPasswordsMatch);
      document.getElementById("regPasswordConfirm").addEventListener("input", checkPasswordsMatch);
```

(This is a second, separate listener on `#regPassword` — added alongside the existing checklist-update listener, not replacing it. Both fire independently on the same `input` event.)

- [ ] **Step 3: Gate the register button's click handler on password match**

Find the `registerBtn` click handler (currently starting `document.getElementById("registerBtn").addEventListener("click", () => {`):

```javascript
      document.getElementById("registerBtn").addEventListener("click", () => {
        const username = document.getElementById("regUsername").value.trim();
        const email = document.getElementById("regEmail").value.trim();
        const password = document.getElementById("regPassword").value;
        const signupCode = document.getElementById("regSignupCode").value.trim();
        const errorEl = document.getElementById("registerError");
        const successEl = document.getElementById("registerSuccess");
        errorEl.style.display = "none";
        successEl.style.display = "none";

        if (!username || !email || !password) {
          errorEl.style.display = "block";
          errorEl.textContent = "Username, email and password are required.";
          return;
        }
```

Replace the opening of that handler (through the existing required-fields check) with this — inserting the password-match check FIRST, before the required-fields check:

```javascript
      document.getElementById("registerBtn").addEventListener("click", () => {
        const username = document.getElementById("regUsername").value.trim();
        const email = document.getElementById("regEmail").value.trim();
        const password = document.getElementById("regPassword").value;
        const passwordConfirm = document.getElementById("regPasswordConfirm").value;
        const signupCode = document.getElementById("regSignupCode").value.trim();
        const errorEl = document.getElementById("registerError");
        const successEl = document.getElementById("registerSuccess");
        errorEl.style.display = "none";
        successEl.style.display = "none";

        if (password !== passwordConfirm) {
          document.getElementById("passwordMismatch").style.display = "block";
          return;
        }

        if (!username || !email || !password) {
          errorEl.style.display = "block";
          errorEl.textContent = "Username, email and password are required.";
          return;
        }
```

The rest of the handler (the `fetch("/api/auth/register", ...)` call and everything after) is unchanged.

- [ ] **Step 4: Verify manually**

With the app running (same setup as Task 3):

1. Go to `http://localhost:8080/login`, "Create Account" tab.
2. Type a password in `#regPassword`, then type a DIFFERENT value in `#regPasswordConfirm`. Expected: red "Les mots de passe ne correspondent pas" appears live as you type in the confirm field.
3. Fill in username/email, leave the mismatch as-is, click "Create Account". Expected: the mismatch message is visible, no network request is made (check the browser's Network tab — no `POST /api/auth/register` call), no account is created.
4. Fix the confirm field to match exactly. Expected: the mismatch message disappears.
5. Click "Create Account" with matching passwords and valid username/email/password. Expected: normal registration flow proceeds (network request fires, account created or existing-account error shown per the server's actual response).
6. Edit `#regPassword` after already filling in a matching `#regPasswordConfirm` (e.g. add a character) so they no longer match. Expected: the mismatch message reappears immediately (proves the listener on `#regPassword` itself, not just on the confirm field, is working).

- [ ] **Step 5: Commit**

```bash
git add src/main/resources/templates/login.html
git commit -m "feat(auth): add password confirmation field with submit gating

Unlike the password-complexity checklist (server-side is the real
enforcement, client-side is decoration), password confirmation has no
server-side equivalent — RegisterRequest carries a single password
field, so the confirmation value never reaches the server. The click
handler blocks submission entirely on mismatch, before any fetch call."
```

---

### Task 5: Push branch and open PR

**Files:** none (git/GitHub operations only).

**Interfaces:**
- Consumes: all commits from Tasks 1-4 on `feature/signup-availability-check`.
- Produces: an open PR against `feature/password-policy` (NOT `main` — this branch was rebased onto `feature/password-policy`, which is itself an open, unmerged PR; this PR should target that branch so the diff shown is just this sub-project's changes, not password-policy's changes too).

- [ ] **Step 1: Push the branch**

```bash
git push origin feature/signup-availability-check
```

- [ ] **Step 2: Open the PR against feature/password-policy**

```bash
gh pr create --repo St4r4x/restaurant-analytics --base feature/password-policy --title "feat(auth): add signup availability check + password confirmation" --body "$(cat <<'EOF'
## Summary
- Adds GET /api/auth/check-username and GET /api/auth/check-email — public, rate-limited under the existing /api/auth/** bucket, reusing AuthService's existing uniqueness lookups.
- login.html's signup form checks both on blur and shows cosmetic ✓/✗ feedback — never blocks submission, the server's existing uniqueness check at POST /api/auth/register remains the real gate.
- Adds a password confirmation field. Unlike the complexity checklist, this DOES gate submission (no server-side equivalent is possible since RegisterRequest carries a single password field) — mismatch blocks the click handler before any fetch call.
- Base branch is feature/password-policy (PR #14, not yet merged) — this PR should be merged after that one, or rebased onto main once #14 lands.
- Design spec: docs/superpowers/specs/2026-07-27-signup-availability-check-design.md
- Implementation plan: docs/superpowers/plans/2026-07-27-signup-availability-check.md

## Test plan
- [x] AuthServiceTest covers isUsernameAvailable/isEmailAvailable (available/taken/blank cases)
- [x] AuthControllerTest covers both new endpoints (200 available=true/false, 400 on blank param)
- [x] Manual browser check: blur-triggered availability feedback, password-mismatch blocking submission with no network call, mismatch clearing correctly, editing the first password field after the second re-triggers the check
EOF
)"
```

- [ ] **Step 3: Wait for CI, verify green**

```bash
gh pr checks --repo St4r4x/restaurant-analytics --watch --interval 20
```

Expected: all checks (Build, Unit Tests, Integration Tests, Secret Scan, E2E Smoke Test, Docker Build and Push) pass.

Do not merge — leave the PR open for the user to review and merge, consistent with this repo's established workflow.

---

## Self-Review Notes

- **Spec coverage**: Part 1 (availability endpoints + blur-triggered client feedback, cosmetic) → Tasks 1-3. Part 2 (password confirmation, submit-gating) → Task 4. Both "Out of scope" items (no debounce/live-as-you-type, no CAPTCHA, no client-side result caching, no `RegisterRequest` changes) correctly have no corresponding task.
- **No placeholders**: every step shows exact code, exact file paths, exact test names/assertions.
- **Type/name consistency**: `isUsernameAvailable(String)`/`isEmailAvailable(String)` signatures match between Task 1's definition and Task 2's controller call sites. Endpoint paths (`/api/auth/check-username`, `/api/auth/check-email`) and query param names (`username`, `email`) match between Task 2's implementation and Task 3's fetch calls. Element IDs (`usernameAvailability`, `emailAvailability`, `regPasswordConfirm`, `passwordMismatch`) are referenced identically across Tasks 3-4's HTML and JS.
- **Base-branch correction applied**: the branch was rebased onto `feature/password-policy` (not `main`) before this plan was written, since the design assumes the password-complexity checklist markup already exists in `login.html`. Task 5's PR explicitly targets `feature/password-policy` via `--base`, not the default `main`, to keep the diff scoped to this sub-project.
