# Design: Security Hardening — JWT Secret + Account Deletion (RGPD)

**Date:** 2026-07-22
**Status:** Approved

---

## Context

Two open risks were identified during the RNCP Level 7 certification review
(`certification/bloc4a-1-analyse-risques.md`, risks R1 and R2, both scored 9-12
on a P×I scale — the two highest-priority open items):

- **R1**: `jwt.secret` is committed in clear text in `application.properties`.
- **R2**: no way for a user to delete their own account (RGPD compliance gap).

This work also serves a second purpose: it is the first `feature/<topic>` →
PR → `main` cycle since mid-May (all commits since then landed directly on
`main`), restoring the branching workflow described in the Bloc 2.3
certification deliverable (`docs/superpowers/plans/`). The PR itself is the
evidence for that deliverable.

Both fixes ship in **one branch, one PR** — `feature/security-hardening` —
since a split into two PRs would add process overhead without a matching
gain (the git-workflow fix has no code of its own; it's demonstrated by how
this PR is shipped).

A third, smaller item is folded into the same PR: a CI secret-scanning job.
Reviewing two sibling personal projects (`diggo`, `the-good-spot`) for
git-workflow and CI conventions surfaced one concrete gap — `the-good-spot`
runs a `gitleaks` job on every PR/push (`.github/workflows/ci.yml`, job
`secrets`), with a `.gitleaks.toml` allowlist for legitimate
client-exposable keys. `restaurant-analytics` has no equivalent job, and
this PR is literally about a secret that leaked into git history — adding
the scan here directly prevents recurrence rather than deferring it to an
unscheduled follow-up.

## Part 1 — JWT secret

### Current state (verified by reading the code)

The fail-fast mechanism **already exists**: `AppConfig.getJwtSecret()`
(`src/main/java/com/st4r4x/config/AppConfig.java:87-93`) throws
`IllegalStateException` if the secret is missing or under 32 characters.
`docker-compose.yml` and CI (Infisical secrets) already inject `JWT_SECRET`
as an environment variable. `docs/configuration.md` already documents it
correctly.

The actual defect is narrower than it first appeared: `application.properties:27`
still holds `jwt.secret=a_very_long_32_bytes_minimum_secret_with_extra_chars_123456`
as tier-3 fallback in `AppConfig.getProperty()` — this silently defeats the
fail-fast if `JWT_SECRET` is ever missing from a real deployment, and it's a
cryptographic secret sitting in git history regardless.

### Change

- Delete line 27 (`jwt.secret=...`) from `application.properties`.
- No other code change — the fail-fast, the env var wiring, and the docs are
  already correct.
- Verified: no test uses `@SpringBootTest` (grep confirms only `UserRepositoryIT`
  does, and it doesn't touch `JwtUtil`); every other test mocks `JwtService`/
  `JwtUtil` directly or patches `AppConfig`'s static `properties` field via
  reflection (`JwtUtilTest` pattern). Removing the fallback cannot break
  `mvn test`.
- **Manual follow-up after merge** (not part of this PR): rotate the actual
  secret on Railway's `JWT_SECRET` env var, since the old value is now known
  to have been exposed in git history. This is an infra action, tracked as a
  reminder, not a code change.

## Part 2 — `DELETE /api/users/me`

### Endpoint

New method on `UserController`, following the existing pattern in that class
(`getCurrentUser()`, `ResponseUtil.errorResponse()`, `@Transactional` as used
by `removeBookmark()`):

```java
@DeleteMapping("/me")
@Transactional
public ResponseEntity<Map<String, Object>> deleteAccount() {
    try {
        UserEntity user = getCurrentUser();
        auditService.log(AuditAction.USER_DELETED, user.getUsername(), null);
        deletePhotosForUser(user.getId());
        reportRepository.deleteAll(reportRepository.findByUserId(user.getId()));
        bookmarkRepository.deleteAll(bookmarkRepository.findByUserId(user.getId()));
        anonymizeAuditLog(user.getUsername());
        userRepository.delete(user);
        return ResponseUtil.success(Map.of("message", "Account deleted"));
    } catch (Exception e) {
        return ResponseUtil.errorResponse(e);
    }
}
```

No role restriction — available to `CUSTOMER`, `CONTROLLER`, and `ADMIN`
alike (confirmed: no business rule blocking self-deletion for accounts with
existing inspection reports).

### Cascade order (full deletion, confirmed choice)

1. **Log the deletion event first** — `AuditService.log(USER_DELETED, ...)`
   before any data is removed. Because `AuditLogEntity` uses
   `GenerationType.IDENTITY` (a synchronous insert), this row already exists
   by the time step 5 runs its `findByActorUsername` lookup, so the
   USER_DELETED entry itself gets swept into the later anonymization pass
   along with every prior entry for this user — the end state is that no
   audit row anywhere retains the real username after account deletion,
   which satisfies GDPR erasure more completely than a scheme that preserved
   one identifying row.
2. **Delete photo files on disk** — for every `InspectionReportEntity` owned
   by the user, delete `{uploadsDir}/{reportId}/` recursively (mirrors the
   write path in `ReportController.uploadPhoto()`,
   `src/main/java/com/st4r4x/controller/ReportController.java:186-192`).
3. **Delete `InspectionReportEntity` rows** owned by the user
   (`reportRepository.deleteAll(reportRepository.findByUserId(...))`).
4. **Delete `BookmarkEntity` rows** owned by the user (same pattern, reusing
   `findByUserId` already present on `BookmarkRepository`).
5. **Anonymize `AuditLogEntity` rows** where `actorUsername` matches — set to
   `"[deleted-user]"` rather than deleting, to preserve audit trail integrity
   without retaining PII. Requires adding
   `List<AuditLogEntity> findByActorUsername(String actorUsername)` to
   `AuditLogRepository` (currently only has `findAllByOrderByCreatedAtDesc`).
6. **Delete the `UserEntity`** itself.

### New repository method

```java
// AuditLogRepository
List<AuditLogEntity> findByActorUsername(String actorUsername);
```

### New audit action

`AuditAction.USER_DELETED` added to the existing enum
(`src/main/java/com/st4r4x/entity/AuditAction.java`), following the same
pattern as `SYNC_TRIGGERED`, `ROLE_CHANGED`, etc.

### Error handling

- Photo file deletion failures are logged (`logger.warn`) but do not abort
  the transaction — a missing/already-deleted file on disk must not prevent
  account deletion. Matches the project's existing convention (`@Scheduled`/
  `@Async` methods swallow-and-log; this is the equivalent case for a
  best-effort cleanup step inside a larger transaction).
- Everything else (repository calls) is transactional — if the `UserEntity`
  delete fails, the whole operation rolls back via `@Transactional`.

## Testing

New test class: `UserControllerDeleteMeTest`
(`src/test/java/com/st4r4x/controller/`), following the `@ExtendWith(MockitoExtension.class)`
+ `@InjectMocks` pattern already used by `UserControllerMeTest`.

Cases:
- Successful deletion: verifies `userRepository.delete()` called, bookmarks
  and reports fetched and deleted, audit log anonymized, audit event logged
  before deletion.
- Cascade with zero bookmarks/reports (new account) — must not throw.
- Photo directory deletion failure is logged but does not prevent the
  transaction from completing (mock `Files.deleteIfExists` throwing
  `IOException` via a thin wrapper, or verify the log call — final approach
  decided at implementation time based on how testable the static `Files`
  calls turn out to be).
- Audit log anonymization: verifies rows matching `actorUsername` get
  `actorUsername = "[deleted-user]"` and are saved, not deleted.

No new test needed for the `jwt.secret` removal (config deletion, not logic)
— covered by re-running the full `mvn test` suite to confirm zero regressions
(already verified during design: 210 unit tests + 14 integration tests
green before this change; same command re-run after implementation must show
identical results).

## Part 3 — CI secret scanning (gitleaks)

Add a `secrets` job to `.github/workflows/ci.yml`, mirroring `the-good-spot`'s
setup:

```yaml
secrets:
  runs-on: ubuntu-latest
  steps:
    - uses: actions/checkout@v6
      with:
        fetch-depth: 0
    - uses: gitleaks/gitleaks-action@v3
      env:
        GITHUB_TOKEN: ${{ secrets.GITHUB_TOKEN }}
```

`fetch-depth: 0` is required so gitleaks can scan full git history, not just
the PR diff — the leaked `jwt.secret` value is still present in prior commits
even after this PR removes the live line, and a shallow scan would miss it.

### Expected false positives

`JwtUtilTest.java` and `AppConfigTest.java` already inject fixture strings
like `"test-only-jwt-secret-64chars-xxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxx"`
into a reflection-patched `AppConfig` field — these look like secrets by
keyword (`jwt.secret`) even though they carry no real entropy and are
scoped to test-only code. Add a `.gitleaks.toml` at repo root, following
`the-good-spot`'s pattern, allowlisting the `test-only-jwt-secret-` prefix
specifically (not a broad `*secret*` exemption, to avoid masking a real
future leak):

```toml
[extend]
useDefault = true

[allowlist]
description = "Test-only JWT fixture secrets — no real entropy, scoped to src/test/java"
regexes = [
  '''test-only-jwt-secret-[A-Za-z0-9]+''',
]
```

If gitleaks flags anything else once the job first runs in CI, extend this
allowlist entry-by-entry rather than disabling the job — same posture as
`the-good-spot`'s existing allowlist for Supabase publishable keys.

## Documentation updates (same PR, per project convention)

- `CHANGELOG.md` — new entry under a fresh version section (not `[Unreleased]`,
  per `new-feature` skill convention — patch bump, since this fixes bugs/gaps
  without adding a new user-facing capability... actually adding
  `DELETE /api/users/me` **is** a new capability → **minor** bump).
- `docs/api.md` — add `DELETE /api/users/me` to the endpoint table.
- `certification/bloc4a-1-analyse-risques.md` — flip R1 and R2 from "Non
  traité" to "Traité", referencing this PR/commit as evidence; add the
  gitleaks CI job as a new indicator of continuous monitoring (section 6).
- `certification/bloc3-2-dossier-technique.md` — update section 4.1 (mesures
  déjà en place) to include the new endpoint, the secret removal, and the
  CI secret-scanning job.

## Out of scope

- Secret rotation on Railway itself (infra action, not code — flagged as a
  manual follow-up above).
- A user-facing "delete my account" UI button — this spec covers the API
  endpoint only; a follow-up feature can wire it into the profile page if
  wanted.
- Restricting self-deletion for `CONTROLLER` accounts with existing reports
  — explicitly ruled out during brainstorming (uniform cascade for all roles).
