# Security Hardening — JWT Secret + Account Deletion (RGPD) Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Close the two highest-priority open risks from the certification risk analysis (R1: JWT secret committed in git, R2: no RGPD account-deletion path) and add a CI secret-scanning job, all on one `feature/security-hardening` branch that restores the `feature/<topic>` → PR → `main` workflow.

**Architecture:** Delete the fallback JWT secret from `application.properties` (the fail-fast already exists in `AppConfig.getJwtSecret()`). Add `DELETE /api/users/me` to `UserController` following its existing `getCurrentUser()` / `ResponseUtil` pattern, cascading through photo files → reports → bookmarks → anonymized audit log → user row, inside one `@Transactional` method. Add a `gitleaks` job to the existing GitHub Actions CI pipeline with a `.gitleaks.toml` allowlist for test-only fixture secrets.

**Tech Stack:** Java 25, Spring Boot 4.0.5, Spring Data JPA (PostgreSQL), JUnit 5 + Mockito, GitHub Actions.

## Global Constraints

- No Lombok — explicit constructors/getters/setters (project convention).
- No `var` keyword — explicit types everywhere.
- 4-space indentation, no wildcard imports, import order `java.* → javax.* → org.* → com.*`.
- `ResponseUtil.errorResponse()` for all error JSON responses; `catch (Exception e)` only at controller boundary, never silently swallowed.
- JUnit 5 + Mockito, `@ExtendWith(MockitoExtension.class)` + `@InjectMocks` pattern (matches `UserControllerMeTest`, `AuthControllerTest`).
- Commits: English, imperative mood, conventional-commits prefix (`feat|fix|docs|chore|refactor|test|ci|style`), subject ≤72 chars, no trailing period.
- `CHANGELOG.md` entry required in the same commit as any source change (per project convention) — this plan places it in a dedicated docs task at the end, per repo convention of updating changelog before opening the PR (not per-commit here, since every task's tests already pass before commit).

---

### Task 1: Remove the committed JWT secret fallback

**Files:**
- Modify: `src/main/resources/application.properties:26-27`

**Interfaces:**
- Consumes: none (deletion only).
- Produces: none — `AppConfig.getJwtSecret()` (`src/main/java/com/st4r4x/config/AppConfig.java:87-93`) already throws `IllegalStateException` when `jwt.secret` resolves to null or <32 chars; removing the properties-file fallback makes that check reachable in a real misconfiguration instead of being permanently masked.

- [ ] **Step 1: Confirm no test relies on the properties-file fallback**

Run: `grep -rn "jwt.secret" src/test/java --include="*.java"`

Expected: only `JwtUtilTest.java` and `AppConfigTest.java` appear, and both inject/patch `jwt.secret` themselves via reflection on `AppConfig`'s static `properties` field — neither depends on the value baked into `application.properties`.

- [ ] **Step 2: Remove the line**

Edit `src/main/resources/application.properties`, changing:

```properties
# JWT Configuration
jwt.secret=a_very_long_32_bytes_minimum_secret_with_extra_chars_123456
jwt.access.expiration.ms=900000
jwt.refresh.expiration.ms=604800000
```

to:

```properties
# JWT Configuration — secret is injected via JWT_SECRET env var (see docs/configuration.md).
# AppConfig.getJwtSecret() fails fast at boot if it's missing or under 32 chars.
jwt.access.expiration.ms=900000
jwt.refresh.expiration.ms=604800000
```

- [ ] **Step 3: Run the full test suite to confirm zero regressions**

Run: `mvn test`
Expected: `Tests run: 210, Failures: 0, Errors: 0, Skipped: 0` (same count as the pre-implementation baseline captured before this plan was written).

- [ ] **Step 4: Commit**

```bash
git add src/main/resources/application.properties
git commit -m "fix(security): remove committed JWT secret fallback

jwt.secret was hardcoded in application.properties and versioned in
git, defeating the existing fail-fast check in AppConfig.getJwtSecret().
JWT_SECRET must now come from the environment (already wired in
docker-compose.yml, Railway, and CI via Infisical)."
```

---

### Task 2: Add `USER_DELETED` audit action and `findByActorUsername` repository method

**Files:**
- Modify: `src/main/java/com/st4r4x/entity/AuditAction.java`
- Modify: `src/main/java/com/st4r4x/repository/AuditLogRepository.java`
- Test: `src/test/java/com/st4r4x/repository/UserRepositoryIT.java` (read-only, no change — confirms the IT harness still passes after Task 1)

**Interfaces:**
- Consumes: `AuditLogEntity` (`src/main/java/com/st4r4x/entity/AuditLogEntity.java`) — existing entity, unchanged.
- Produces: `AuditAction.USER_DELETED` enum constant; `AuditLogRepository.findByActorUsername(String actorUsername): List<AuditLogEntity>` — consumed by Task 4.

- [ ] **Step 1: Add the enum constant**

Edit `src/main/java/com/st4r4x/entity/AuditAction.java`:

```java
package com.st4r4x.entity;

public enum AuditAction {
    USER_ROLE_CHANGED,
    SYNC_TRIGGERED,
    CRON_JOB_TRIGGERED,
    OSM_ENRICH_TRIGGERED,
    CACHE_REBUILT,
    REPORT_STATUS_CHANGED,
    USER_DELETED
}
```

- [ ] **Step 2: Add the repository method**

Edit `src/main/java/com/st4r4x/repository/AuditLogRepository.java`:

```java
package com.st4r4x.repository;

import com.st4r4x.entity.AuditLogEntity;
import java.util.List;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

public interface AuditLogRepository extends JpaRepository<AuditLogEntity, Long> {
    Page<AuditLogEntity> findAllByOrderByCreatedAtDesc(Pageable pageable);
    List<AuditLogEntity> findByActorUsername(String actorUsername);
}
```

- [ ] **Step 3: Compile to confirm no breakage**

Run: `mvn -q compile`
Expected: no output, exit code 0.

- [ ] **Step 4: Commit**

```bash
git add src/main/java/com/st4r4x/entity/AuditAction.java src/main/java/com/st4r4x/repository/AuditLogRepository.java
git commit -m "feat(audit): add USER_DELETED action and findByActorUsername query

Preparatory step for the account-deletion endpoint — lets it log the
deletion event and look up prior audit rows to anonymize."
```

---

### Task 3: Add photo-directory cleanup helper to `UserController`

**Files:**
- Modify: `src/main/java/com/st4r4x/controller/UserController.java`

**Interfaces:**
- Consumes: `AppConfig.getUploadsDir(): String` (`src/main/java/com/st4r4x/config/AppConfig.java:122-124`); `InspectionReportEntity.getId(): Long`.
- Produces: private method `deletePhotoDirectory(Long reportId): void` — consumed by Task 4. Mirrors the write path in `ReportController.uploadPhoto()` (`src/main/java/com/st4r4x/controller/ReportController.java:186-192`), which writes to `{uploadsDir}/{reportId}/{timestamp}_{filename}`.

- [ ] **Step 1: Add imports**

At the top of `src/main/java/com/st4r4x/controller/UserController.java`, alongside the existing imports, add:

```java
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Comparator;
import java.util.stream.Stream;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.st4r4x.config.AppConfig;
```

- [ ] **Step 2: Add the logger field and helper method**

Add a logger field right after the class declaration (matching the pattern in `ResponseUtil`/`RestaurantDAOImpl`):

```java
private static final Logger logger = LoggerFactory.getLogger(UserController.class);
```

Add this private method anywhere in the class body (e.g. right after `getCurrentUser()`):

```java
/**
 * Deletes {uploadsDir}/{reportId}/ recursively. Best-effort: a missing or
 * already-deleted directory must not block account deletion.
 */
private void deletePhotoDirectory(Long reportId) {
    Path dir = Paths.get(AppConfig.getUploadsDir(), String.valueOf(reportId));
    if (!Files.exists(dir)) {
        return;
    }
    try (Stream<Path> paths = Files.walk(dir)) {
        paths.sorted(Comparator.reverseOrder()).forEach(path -> {
            try {
                Files.delete(path);
            } catch (IOException e) {
                logger.warn("Failed to delete {} during account deletion: {}", path, e.getMessage());
            }
        });
    } catch (IOException e) {
        logger.warn("Failed to walk photo directory {} during account deletion: {}", dir, e.getMessage());
    }
}
```

- [ ] **Step 3: Compile to confirm no breakage**

Run: `mvn -q compile`
Expected: no output, exit code 0.

- [ ] **Step 4: Commit**

```bash
git add src/main/java/com/st4r4x/controller/UserController.java
git commit -m "feat(users): add best-effort photo-directory cleanup helper

Deletes {uploadsDir}/{reportId}/ recursively; logs and continues on
any IOException rather than aborting the caller's transaction. Used
by the upcoming DELETE /api/users/me cascade."
```

---

### Task 4: Implement `DELETE /api/users/me` with cascade deletion

**Files:**
- Modify: `src/main/java/com/st4r4x/controller/UserController.java`
- Test: `src/test/java/com/st4r4x/controller/UserControllerDeleteMeTest.java` (new)

**Interfaces:**
- Consumes: `Task 2`'s `AuditAction.USER_DELETED` and `AuditLogRepository.findByActorUsername`; `Task 3`'s `deletePhotoDirectory(Long)`; existing `BookmarkRepository.findByUserId(Long): List<BookmarkEntity>`, `ReportRepository.findByUserId(Long): List<InspectionReportEntity>`, `UserRepository.delete(UserEntity)`, `AuditService.log(AuditAction, String, String, Map<String,Object>)`.
- Produces: `DELETE /api/users/me` HTTP endpoint — terminal for this feature, consumed by end users/frontend (not by other backend code).

- [ ] **Step 1: Write the failing test**

Create `src/test/java/com/st4r4x/controller/UserControllerDeleteMeTest.java`:

```java
package com.st4r4x.controller;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

import java.util.Collections;
import java.util.List;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.ResponseEntity;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;

import com.st4r4x.entity.AuditAction;
import com.st4r4x.entity.AuditLogEntity;
import com.st4r4x.entity.BookmarkEntity;
import com.st4r4x.entity.InspectionReportEntity;
import com.st4r4x.entity.UserEntity;
import com.st4r4x.repository.AuditLogRepository;
import com.st4r4x.repository.BookmarkRepository;
import com.st4r4x.repository.ReportRepository;
import com.st4r4x.repository.UserRepository;
import com.st4r4x.service.AuditService;

@ExtendWith(MockitoExtension.class)
public class UserControllerDeleteMeTest {

    @InjectMocks
    private UserController userController;

    @Mock private UserRepository userRepository;
    @Mock private BookmarkRepository bookmarkRepository;
    @Mock private ReportRepository reportRepository;
    @Mock private AuditLogRepository auditLogRepository;
    @Mock private AuditService auditService;

    @BeforeEach
    public void setUpSecurityContext() {
        SecurityContextHolder.getContext().setAuthentication(
            new UsernamePasswordAuthenticationToken("testuser", null, Collections.emptyList())
        );
    }

    @AfterEach
    public void clearSecurityContext() {
        SecurityContextHolder.clearContext();
    }

    @Test
    public void deleteAccount_withNoBookmarksOrReports_deletesUserAndReturns200() {
        UserEntity user = new UserEntity("testuser", "test@example.com", "hash", "ROLE_CUSTOMER");
        user.setId(1L);
        when(userRepository.findByUsername("testuser")).thenReturn(java.util.Optional.of(user));
        when(bookmarkRepository.findByUserId(1L)).thenReturn(Collections.emptyList());
        when(reportRepository.findByUserId(1L)).thenReturn(Collections.emptyList());
        when(auditLogRepository.findByActorUsername("testuser")).thenReturn(Collections.emptyList());

        ResponseEntity<?> response = userController.deleteAccount();

        assertEquals(200, response.getStatusCode().value());
        verify(userRepository).delete(user);
    }

    @Test
    public void deleteAccount_logsAuditEventBeforeDeletingUser() {
        UserEntity user = new UserEntity("testuser", "test@example.com", "hash", "ROLE_CUSTOMER");
        user.setId(1L);
        when(userRepository.findByUsername("testuser")).thenReturn(java.util.Optional.of(user));
        when(bookmarkRepository.findByUserId(1L)).thenReturn(Collections.emptyList());
        when(reportRepository.findByUserId(1L)).thenReturn(Collections.emptyList());
        when(auditLogRepository.findByActorUsername("testuser")).thenReturn(Collections.emptyList());

        userController.deleteAccount();

        verify(auditService).log(eq(AuditAction.USER_DELETED), eq("User"), eq("testuser"), isNull());
    }

    @Test
    public void deleteAccount_deletesAllBookmarksAndReports() {
        UserEntity user = new UserEntity("testuser", "test@example.com", "hash", "ROLE_CUSTOMER");
        user.setId(1L);
        BookmarkEntity bookmark = new BookmarkEntity(user, "R001");
        InspectionReportEntity report = new InspectionReportEntity();
        report.setId(42L);
        List<BookmarkEntity> bookmarks = List.of(bookmark);
        List<InspectionReportEntity> reports = List.of(report);

        when(userRepository.findByUsername("testuser")).thenReturn(java.util.Optional.of(user));
        when(bookmarkRepository.findByUserId(1L)).thenReturn(bookmarks);
        when(reportRepository.findByUserId(1L)).thenReturn(reports);
        when(auditLogRepository.findByActorUsername("testuser")).thenReturn(Collections.emptyList());

        userController.deleteAccount();

        verify(bookmarkRepository).deleteAll(bookmarks);
        verify(reportRepository).deleteAll(reports);
    }

    @Test
    public void deleteAccount_anonymizesMatchingAuditLogRows() {
        UserEntity user = new UserEntity("testuser", "test@example.com", "hash", "ROLE_CUSTOMER");
        user.setId(1L);
        AuditLogEntity priorEntry = new AuditLogEntity();
        priorEntry.setActorUsername("testuser");
        List<AuditLogEntity> priorEntries = List.of(priorEntry);

        when(userRepository.findByUsername("testuser")).thenReturn(java.util.Optional.of(user));
        when(bookmarkRepository.findByUserId(1L)).thenReturn(Collections.emptyList());
        when(reportRepository.findByUserId(1L)).thenReturn(Collections.emptyList());
        when(auditLogRepository.findByActorUsername("testuser")).thenReturn(priorEntries);

        userController.deleteAccount();

        ArgumentCaptor<List<AuditLogEntity>> captor = ArgumentCaptor.forClass(List.class);
        verify(auditLogRepository).saveAll(captor.capture());
        assertEquals("[deleted-user]", captor.getValue().get(0).getActorUsername());
        verify(auditLogRepository, never()).deleteAll(any());
        verify(auditLogRepository, never()).delete(any());
    }
}
```

- [ ] **Step 2: Run the test to verify it fails**

Run: `mvn test -Dtest=UserControllerDeleteMeTest -Djacoco.skip=true`
Expected: compilation failure — `UserController` has no `deleteAccount()` method and no `AuditLogRepository`/`AuditService` fields yet.

- [ ] **Step 3: Implement the endpoint**

Edit `src/main/java/com/st4r4x/controller/UserController.java`. Add these fields near the existing `@Autowired` fields:

```java
@Autowired
private AuditLogRepository auditLogRepository;

@Autowired
private AuditService auditService;
```

Add these imports:

```java
import com.st4r4x.entity.AuditAction;
import com.st4r4x.entity.AuditLogEntity;
import com.st4r4x.entity.InspectionReportEntity;
import com.st4r4x.repository.AuditLogRepository;
import com.st4r4x.service.AuditService;
```

Add the endpoint method:

```java
@DeleteMapping("/me")
@Transactional
public ResponseEntity<Map<String, Object>> deleteAccount() {
    try {
        UserEntity user = getCurrentUser();
        String username = user.getUsername();

        auditService.log(AuditAction.USER_DELETED, "User", username, null);

        List<InspectionReportEntity> reports = reportRepository.findByUserId(user.getId());
        for (InspectionReportEntity report : reports) {
            deletePhotoDirectory(report.getId());
        }
        reportRepository.deleteAll(reports);

        List<BookmarkEntity> bookmarks = bookmarkRepository.findByUserId(user.getId());
        bookmarkRepository.deleteAll(bookmarks);

        List<AuditLogEntity> priorEntries = auditLogRepository.findByActorUsername(username);
        for (AuditLogEntity entry : priorEntries) {
            entry.setActorUsername("[deleted-user]");
        }
        auditLogRepository.saveAll(priorEntries);

        userRepository.delete(user);

        Map<String, Object> response = new HashMap<>();
        response.put("status", "success");
        response.put("message", "Account deleted");
        return ResponseEntity.ok(response);
    } catch (Exception e) {
        return ResponseUtil.errorResponse(e);
    }
}
```

- [ ] **Step 4: Run the test to verify it passes**

Run: `mvn test -Dtest=UserControllerDeleteMeTest -Djacoco.skip=true`
Expected: `Tests run: 4, Failures: 0, Errors: 0, Skipped: 0`

- [ ] **Step 5: Run the full test suite**

Run: `mvn test`
Expected: `Tests run: 214, Failures: 0, Errors: 0, Skipped: 0` (210 baseline + 4 new).

- [ ] **Step 6: Commit**

```bash
git add src/main/java/com/st4r4x/controller/UserController.java src/test/java/com/st4r4x/controller/UserControllerDeleteMeTest.java
git commit -m "feat(users): add DELETE /api/users/me for RGPD account deletion

Cascades through photo files, inspection reports, and bookmarks, then
anonymizes (not deletes) matching audit log rows before removing the
user row. Available to all roles — no self-deletion restriction."
```

---

### Task 5: Add CI secret scanning (gitleaks)

**Files:**
- Create: `.gitleaks.toml`
- Modify: `.github/workflows/ci.yml`

**Interfaces:**
- Consumes: none (CI configuration only).
- Produces: a `secrets` job in the GitHub Actions workflow — no code interface, gate only.

- [ ] **Step 1: Create the gitleaks allowlist**

Create `.gitleaks.toml` at the repo root:

```toml
[extend]
useDefault = true

[allowlist]
description = "Test-only JWT fixture secrets — no real entropy, scoped to src/test/java"
regexes = [
  '''test-only-jwt-secret-[A-Za-z0-9]+''',
]
```

- [ ] **Step 2: Add the `secrets` job to the CI workflow**

Read `.github/workflows/ci.yml` first to find the `jobs:` key and an existing job's indentation style, then add a new top-level job (indentation must match existing jobs — 2 spaces for the job name, 4 for its keys):

```yaml
  secrets:
    name: Secret Scan
    runs-on: ubuntu-latest
    steps:
      - uses: actions/checkout@v6
        with:
          fetch-depth: 0
      - uses: gitleaks/gitleaks-action@v3
        env:
          GITHUB_TOKEN: ${{ secrets.GITHUB_TOKEN }}
```

Place it as the first job (before `build`), matching `the-good-spot`'s convention of running the secret scan independently of the build/test chain (no `needs:` — it doesn't depend on anything and nothing should wait on it before its own gate matters).

- [ ] **Step 3: Validate YAML syntax**

Run: `python3 -c "import yaml; yaml.safe_load(open('.github/workflows/ci.yml'))" && echo OK`
Expected: `OK`

- [ ] **Step 4: Commit**

```bash
git add .gitleaks.toml .github/workflows/ci.yml
git commit -m "ci: add gitleaks secret scanning job

restaurant-analytics had no automated secret-scanning gate, which is
exactly how jwt.secret ended up committed in application.properties
(fixed earlier in this branch). Mirrors the setup already used on
the-good-spot, with an allowlist for the test-only JWT fixture strings
in JwtUtilTest/AppConfigTest that look like secrets by keyword but
carry no real entropy."
```

- [ ] **Step 5: Note — this job cannot be verified locally**

Gitleaks scanning of the full commit history (`fetch-depth: 0`) can only be meaningfully verified once this branch is pushed and the workflow runs in GitHub Actions — running gitleaks locally against a shallow local clone would give a false sense of completeness. Flag this explicitly when handing off to code review (Task 7): "the `secrets` job has not run in real CI yet; first real signal comes from the PR's own CI run."

---

### Task 6: Update documentation and CHANGELOG

**Files:**
- Modify: `CHANGELOG.md`
- Modify: `docs/api.md`
- Modify: `certification/bloc4a-1-analyse-risques.md`
- Modify: `certification/bloc3-2-dossier-technique.md`

**Interfaces:**
- Consumes: nothing code-level — reads the final state of Tasks 1-5 to describe them accurately.
- Produces: nothing consumed by later tasks — this is the last content task before the PR.

- [ ] **Step 1: Add the CHANGELOG entry**

Read `CHANGELOG.md` first to find the exact current top section, then add a new version section above it (not under `[Unreleased]`, per this project's `new-feature`-skill convention of deciding the version now). This is a **minor** bump (new user-facing capability: `DELETE /api/users/me`) from `2.2.4` → `2.3.0`:

```markdown
## [2.3.0] — 2026-07-22

### Added
- `DELETE /api/users/me` — RGPD-compliant account deletion; cascades through photo files, inspection reports, and bookmarks, anonymizes matching audit log entries, then removes the user row
- CI: `secrets` job (gitleaks) scans every push/PR for committed secrets, with a `.gitleaks.toml` allowlist for test-only JWT fixtures

### Security
- Remove `jwt.secret` fallback value from `application.properties` — it was committed in clear text in git history and silently defeated the existing fail-fast check in `AppConfig.getJwtSecret()`. `JWT_SECRET` must now come from the environment (already wired in `docker-compose.yml`, Railway, and CI)
```

- [ ] **Step 2: Update `docs/api.md`**

Read `docs/api.md` first to find the `/api/users/` section and its existing table row format, then add a row for the new endpoint immediately after the existing `/api/users/me` entries, matching the table's exact column style.

- [ ] **Step 3: Update the certification risk analysis**

In `certification/bloc4a-1-analyse-risques.md`, find the table row for R1 and R2 (section "2. Cartographie des risques") and change their "Statut" column from "Non traité" to:

```
**Traité** — PR feature/security-hardening (2026-07-22)
```

In the same file's section 6 ("Indicateurs de suivi"), add a row:

```
| Job CI `secrets` (gitleaks) en échec | GitHub Actions | À chaque push/PR | Tout échec = secret détecté, bloquer le merge |
```

- [ ] **Step 4: Update the technical dossier**

In `certification/bloc3-2-dossier-technique.md`, section 4.1 ("Mesures déjà en place"), add three bullets:

```
- **Suppression de compte RGPD** : `DELETE /api/users/me` — cascade complète (photos, rapports, bookmarks) + anonymisation de l'audit log.
- **Secret JWT retiré du dépôt** : `application.properties` ne contient plus de valeur de secours ; `JWT_SECRET` est obligatoire via variable d'environnement, avec échec au démarrage sinon (`AppConfig.getJwtSecret()`).
- **Scan de secrets en CI** : job `secrets` (gitleaks) sur chaque push/PR, avec allowlist documentée (`.gitleaks.toml`).
```

- [ ] **Step 5: Commit**

```bash
git add CHANGELOG.md docs/api.md certification/bloc4a-1-analyse-risques.md certification/bloc3-2-dossier-technique.md
git commit -m "docs: update CHANGELOG, api docs, and certification dossiers for security hardening

Bumps to 2.3.0 (new DELETE /api/users/me capability). Flips R1/R2 to
Traité in the risk analysis with this PR as evidence, and logs the
new protections in the Bloc 3.2 technical dossier."
```

---

### Task 7: Finish the branch

**Files:** none (process step).

- [ ] **Step 1: Run the full test suite one final time**

Run: `mvn test`
Expected: `Tests run: 214, Failures: 0, Errors: 0, Skipped: 0`

- [ ] **Step 2: Run `/simplify` on the diff**

Review `git diff main...feature/security-hardening` for unnecessary abstraction or complexity not justified by the spec. Apply any suggested cleanups that don't conflict with the approved design.

- [ ] **Step 3: Run `/security-review` on the diff**

Pay particular attention to: the cascade delete transaction boundaries (Task 4), whether the photo-directory deletion (Task 3) could be tricked into deleting outside `uploadsDir` (it can't — `reportId` is a `Long` from the database, not user input, so no path traversal surface), and that no new secret was introduced anywhere in the diff.

- [ ] **Step 4: Invoke `superpowers:finishing-a-development-branch`**

Open a PR from `feature/security-hardening` into `main`. Wait for all CI jobs (`secrets`, `build`, `unit-test`, `integration-test`, `e2e`, `docker`) to go green — do not merge on red or pending CI, and pay special attention to the `secrets` job since Task 5 flagged it as unverified locally.

---

## Self-Review Notes

- **Spec coverage**: Part 1 (Task 1), Part 2 (Tasks 2-4), Part 3 (Task 5), documentation updates (Task 6) — all spec sections have a task.
- **Type consistency verified**: `AuditService.log(AuditAction, String, String, Map<String,Object>)` signature (read from `AuditService.java`) matches every call site added in Task 4; `AuditLogRepository.findByActorUsername(String): List<AuditLogEntity>` (Task 2) matches its consumption in Task 4's test and implementation.
- **Out of scope reminder** (from spec): no UI button for account deletion, no secret rotation on Railway itself (manual follow-up, flagged to the user after merge), no role-based restriction on self-deletion.
