# Design: PostgreSQL Backup + MongoDB Restore Documentation

**Date:** 2026-07-23
**Status:** Approved

---

## Context

Risk R8 in the certification risk analysis (`certification/bloc4a-1-analyse-risques.md`) flags the absence of a documented backup strategy for MongoDB and PostgreSQL as a risk to Integrity/Availability (score 6, "Non traité"). Both risks R1 and R2 (the two highest-scored, 9-12) were already closed in the `feature/security-hardening` PR; R8 is next in priority order.

**Actual infrastructure** (confirmed via Railway MCP tools — the Railway project only hosts `restaurant-app` and `elasticsearch`, no database service):

- **PostgreSQL** is hosted on **Supabase** (free tier — no automatic point-in-time recovery or backup on this tier).
- **MongoDB** is hosted on **MongoDB Atlas** (free tier, same limitation).
- CI already authenticates to secrets via **Infisical** (`Infisical/secrets-action@v1.0.16`, `env-slug: dev` for the existing `integration-test`/`e2e` jobs).

This materially changes the risk profile from what the original dossier assumed (self-hosted `docker-compose.yml` volumes) — the actual exposure is entirely on the two managed free-tier services, not on Railway-local volumes.

## Scope decision: PostgreSQL only, MongoDB is documentation-only

**PostgreSQL holds non-reconstructible data**: `UserEntity`, `BookmarkEntity`, `InspectionReportEntity` (with photos), `AuditLogEntity`. If lost, there is no way to recover a user's account, bookmarks, or inspection report history — this needs an actual backup.

**MongoDB holds only `newyork.restaurants`**, which is entirely re-derivable from the NYC Open Data API via the existing `SyncService` (triggered manually via `POST /api/restaurants/refresh`, ADMIN role). Adding a backup job for data that can be fully reconstructed on demand is complexity with no corresponding risk reduction — YAGNI. The correct fix for R8's MongoDB half is **documentation**: make the restore path (re-sync from source) explicit in `docs/deployment.md`, so "restore MongoDB" isn't a mystery during an actual incident.

## Approach

A new, standalone GitHub Actions workflow — `.github/workflows/backup-postgres.yml` — separate from `ci.yml` (no relationship to build/test, no reason to couple them). Triggered by:
- `schedule`: daily cron, `17 3 * * *` (03:17 UTC — off the top of the hour per general cron hygiene, low-traffic window)
- `workflow_dispatch`: manual trigger, for testing and on-demand backups before risky operations (e.g. before a schema migration)

### Flow

1. Fetch the Supabase connection string via Infisical (`env-slug: prod` — **to be confirmed against the actual Infisical project during implementation**; if that environment/secret doesn't exist, the implementer creates a plain GitHub Actions secret `SUPABASE_DATABASE_URL` instead and this doc's Infisical reference becomes informational only).
2. Run `pg_dump --format=plain "$SUPABASE_DATABASE_URL" > backup.sql`.
3. Encrypt: `gpg --batch --yes --passphrase "$BACKUP_GPG_PASSPHRASE" --symmetric --cipher-algo AES256 -o backup.sql.gpg backup.sql`.
4. Delete the unencrypted `backup.sql` immediately (`rm backup.sql`) — never let the plaintext dump persist past the encryption step, even within the same job's filesystem.
5. Upload `backup.sql.gpg` via `actions/upload-artifact@v7`, `retention-days: 30`, name pattern `postgres-backup-${{ github.run_id }}` (unique per run — GitHub artifact names must be unique within a run's retention window across the repo's history is not a real constraint, but per-run naming avoids any ambiguity when browsing the Actions UI).

### New secrets required

| Secret | Scope | Purpose |
|---|---|---|
| `BACKUP_GPG_PASSPHRASE` | GitHub Actions repo secret | Symmetric encryption key for the dump. Generate with `openssl rand -base64 32`, store nowhere else in plaintext. |
| `SUPABASE_DATABASE_URL` (or Infisical equivalent) | Infisical `prod` env, or GitHub secret as fallback | Postgres connection string with read access sufficient for `pg_dump` |

### Error handling

If `pg_dump` fails (network issue, expired credentials, Supabase downtime), the job fails and GitHub's default email notification fires — no custom alerting is being built for this; that's out of scope for a solo-maintained academic project at this stage (YAGNI, matches the project's existing posture of not building alerting for the nightly NYC Open Data sync failure either).

## Restore documentation

New file `docs/backup-restore.md`:

- **PostgreSQL restore**: download the artifact, `gpg --batch --yes --passphrase "$BACKUP_GPG_PASSPHRASE" --decrypt backup.sql.gpg > backup.sql`, then `psql "$SUPABASE_DATABASE_URL" < backup.sql` against a fresh/target database.
- **MongoDB restore**: no dump exists or is needed. Restore path is: ensure `newyork.restaurants` is empty or accept upsert semantics, then trigger `POST /api/restaurants/refresh` (ADMIN JWT required) to fully resync from NYC Open Data. Document expected duration (several minutes, per `nyc.api.max_records` setting) and how to check completion via `GET /api/restaurants/sync-status`.

`docs/deployment.md` gets a new "Disaster Recovery" section that links to `docs/backup-restore.md` rather than duplicating it.

## Testing

Not applicable in the unit/integration-test sense — this is a CI workflow, not application code. Verification is a manual `workflow_dispatch` run before merging, confirming:
1. The job completes successfully against the real Supabase instance.
2. The uploaded artifact can be downloaded, decrypted with the passphrase, and `psql`'d into a scratch database without error.
3. No plaintext `backup.sql` remains in any published artifact or log output (check the job's step summary for accidental `cat`/`echo` of the dump content).

## Out of scope

- MongoDB backup infrastructure (see Scope Decision above — documentation only).
- Point-in-time recovery / continuous backup — daily full dumps are sufficient for this project's traffic and risk profile; upgrading to a paid Supabase/Atlas tier with native PITR is a future option to revisit if the project's stakes change, not something to build around now.
- Automated restore testing (e.g. a second CI job that restores into a scratch Postgres container and runs a smoke query) — worth considering later, but the manual verification in the Testing section is proportionate to a solo academic project's current risk tolerance.
- Alerting beyond GitHub's default failed-workflow email.

## Certification dossier update (deferred to implementation)

Once implemented and verified, `certification/bloc4a-1-analyse-risques.md` R8's status flips from "Non traité" to "Traité", following the same pattern used for R1/R2 in the security-hardening PR (reference the PR/commit as evidence).
