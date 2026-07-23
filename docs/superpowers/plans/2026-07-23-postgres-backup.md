# PostgreSQL Backup + MongoDB Restore Documentation Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Close risk R8 (no documented backup strategy) by adding a daily encrypted PostgreSQL backup workflow and documenting the MongoDB restore path (full resync from NYC Open Data, no dump needed).

**Architecture:** A standalone GitHub Actions workflow (`backup-postgres.yml`, separate from `ci.yml`) runs `pg_dump` against Supabase on a daily cron, encrypts the dump with GPG symmetric encryption, and uploads it as a 30-day-retention artifact. A new `docs/backup-restore.md` documents both the Postgres restore procedure and the MongoDB resync procedure (no dump exists for Mongo — it's fully re-derivable from the NYC Open Data API).

**Tech Stack:** GitHub Actions, PostgreSQL client tools (`pg_dump`, `psql`), GnuPG, Infisical (existing secrets provider) with a plain GitHub Actions secret as documented fallback.

## Global Constraints

- MongoDB gets **no backup infrastructure** — it is fully reconstructible from NYC Open Data via `POST /api/restaurants/refresh` (ADMIN role). Only documentation is needed for Mongo.
- The unencrypted `pg_dump` output must never persist past the encryption step in the same job — delete it immediately after encrypting.
- Cron schedule: `17 3 * * *` (03:17 UTC daily — off-the-hour per general cron hygiene).
- Artifact retention: 30 days.
- Encryption: GPG symmetric (`--symmetric --cipher-algo AES256`), passphrase from a GitHub Actions secret named `BACKUP_GPG_PASSPHRASE`.
- Commits: English, imperative mood, conventional-commits prefix (`feat|fix|docs|chore|refactor|test|ci|style`), subject ≤72 chars, no trailing period.
- This is a new standalone workflow file — do not add these steps to the existing `.github/workflows/ci.yml`.

---

### Task 1: Verify secret source and create required GitHub secrets

**Files:** none (this task is verification + secret provisioning via `gh` CLI, no repo file changes).

**Interfaces:**
- Consumes: nothing.
- Produces: confirms which secret name Task 2's workflow should reference for the Supabase connection string (`SUPABASE_DATABASE_URL` as a plain GitHub secret, OR the existing Infisical `prod` environment if it turns out to exist) — Task 2 needs this decision made before it can write the workflow.

This task exists because the design spec flagged genuine uncertainty: the existing CI (`.github/workflows/ci.yml`) authenticates to Infisical with `env-slug: dev` for test-time secrets, but nobody has confirmed whether an Infisical `prod` environment exists with the real Supabase URL, or whether that URL only lives in Railway's environment variables today.

- [x] **Step 1: Check whether an Infisical `prod` environment exists for this project**

You (the human operator) have access to the Infisical dashboard/CLI login that this session does not. Run:

```bash
infisical login
infisical secrets --projectId <project-id-for-nyc-restaurant-hygiene-z-qd-p> --env prod
```

(Find the project ID by running `infisical secrets --help` guidance, or by opening the Infisical web dashboard for the project slug `nyc-restaurant-hygiene-z-qd-p` and checking the URL/project settings page for its ID.)

**If a `prod` environment exists and contains a Postgres connection string** (commonly named `SPRING_DATASOURCE_URL`, `DATABASE_URL`, or similar): note the exact secret key name. Task 2 will use `Infisical/secrets-action@v1.0.16` with `env-slug: prod` (matching the existing pattern in `.github/workflows/ci.yml:95-101`) to fetch it, and no new GitHub secret is needed for the connection string.

**If no `prod` environment exists, or it doesn't contain the Postgres URL:** proceed to Step 2 to create a plain GitHub secret instead.

- [x] ~~Step 2 (fallback only — skip if Step 1 found the URL in Infisical): create `SUPABASE_DATABASE_URL` as a GitHub Actions secret~~ — **skipped**: Step 1 found the URL in Infisical `prod` (Path A), so this fallback didn't apply.

Get the real Supabase connection string from the Supabase dashboard (Project Settings → Database → Connection string → URI format, using the **connection pooler** URI if available since `pg_dump` is a short-lived connection). Then:

```bash
gh secret set SUPABASE_DATABASE_URL --repo St4r4x/restaurant-analytics
# Paste the connection string when prompted, then press Ctrl+D
```

Verify it was created:

```bash
gh secret list --repo St4r4x/restaurant-analytics
```

Expected: `SUPABASE_DATABASE_URL` appears in the list.

- [x] **Step 3: Create the GPG passphrase secret (required regardless of Step 1's outcome)**

```bash
openssl rand -base64 32 | gh secret set BACKUP_GPG_PASSPHRASE --repo St4r4x/restaurant-analytics
```

Verify:

```bash
gh secret list --repo St4r4x/restaurant-analytics
```

Expected: `BACKUP_GPG_PASSPHRASE` appears in the list.

**Save the passphrase value somewhere durable outside GitHub** (a password manager) before closing this step — GitHub secrets are write-only; there is no way to retrieve the value later, and losing it means every future backup becomes undecryptable.

- [x] **Step 4: Record the decision for Task 2**

Write down (in your own notes, not a repo file) which path Step 1 resolved to:
- Path A: Infisical `prod` environment has the URL under key `<KEY_NAME>`.
- Path B: GitHub secret `SUPABASE_DATABASE_URL` was created in Step 2.

Task 2's workflow file differs slightly depending on which path applies — the task below shows both variants.

---

### Task 2: Create the `backup-postgres.yml` workflow

**Files:**
- Create: `.github/workflows/backup-postgres.yml`

**Interfaces:**
- Consumes: the secret(s) provisioned in Task 1 (`BACKUP_GPG_PASSPHRASE` always; `SUPABASE_DATABASE_URL` env var, sourced either from the GitHub secret directly or from Infisical's export, depending on Task 1's Path A/B outcome).
- Produces: a GitHub Actions artifact named `postgres-backup-${{ github.run_id }}`, containing one file `backup.sql.gpg` — consumed manually during a restore (Task 4 documents how), not by any other workflow or task.

- [x] **Step 1: Write the workflow file**

If Task 1 resolved to **Path B** (plain GitHub secret `SUPABASE_DATABASE_URL`), create `.github/workflows/backup-postgres.yml` with this content:

```yaml
name: Backup PostgreSQL

on:
  schedule:
    - cron: '17 3 * * *'
  workflow_dispatch: {}

jobs:
  backup:
    name: Dump and Encrypt
    runs-on: ubuntu-latest
    timeout-minutes: 10
    steps:
      - name: Install PostgreSQL client
        run: sudo apt-get update && sudo apt-get install -y postgresql-client gnupg

      - name: Dump database
        env:
          SUPABASE_DATABASE_URL: ${{ secrets.SUPABASE_DATABASE_URL }}
        run: pg_dump --format=plain "$SUPABASE_DATABASE_URL" > backup.sql

      - name: Encrypt dump
        env:
          BACKUP_GPG_PASSPHRASE: ${{ secrets.BACKUP_GPG_PASSPHRASE }}
        run: |
          gpg --batch --yes --passphrase "$BACKUP_GPG_PASSPHRASE" \
            --symmetric --cipher-algo AES256 -o backup.sql.gpg backup.sql
          rm backup.sql

      - name: Upload encrypted backup
        uses: actions/upload-artifact@v7
        with:
          name: postgres-backup-${{ github.run_id }}
          path: backup.sql.gpg
          retention-days: 30
```

If Task 1 resolved to **Path A** (Infisical `prod` environment holds the URL under key `<KEY_NAME>` — substitute the real key name discovered in Task 1), create the file with this content instead (the only differences are the added Infisical step and referencing `env.<KEY_NAME>` instead of `secrets.SUPABASE_DATABASE_URL` in the dump step):

```yaml
name: Backup PostgreSQL

on:
  schedule:
    - cron: '17 3 * * *'
  workflow_dispatch: {}

jobs:
  backup:
    name: Dump and Encrypt
    runs-on: ubuntu-latest
    timeout-minutes: 10
    steps:
      - name: Fetch secrets from Infisical
        uses: Infisical/secrets-action@v1.0.16
        with:
          method: universal
          client-id: ${{ secrets.INFISICAL_CLIENT_ID }}
          client-secret: ${{ secrets.INFISICAL_CLIENT_SECRET }}
          project-slug: nyc-restaurant-hygiene-z-qd-p
          env-slug: prod
          export-type: env

      - name: Install PostgreSQL client
        run: sudo apt-get update && sudo apt-get install -y postgresql-client gnupg

      - name: Dump database
        run: pg_dump --format=plain "$<KEY_NAME>" > backup.sql

      - name: Encrypt dump
        env:
          BACKUP_GPG_PASSPHRASE: ${{ secrets.BACKUP_GPG_PASSPHRASE }}
        run: |
          gpg --batch --yes --passphrase "$BACKUP_GPG_PASSPHRASE" \
            --symmetric --cipher-algo AES256 -o backup.sql.gpg backup.sql
          rm backup.sql

      - name: Upload encrypted backup
        uses: actions/upload-artifact@v7
        with:
          name: postgres-backup-${{ github.run_id }}
          path: backup.sql.gpg
          retention-days: 30
```

(Replace `$<KEY_NAME>` with the actual shell variable reference to the Infisical-exported secret — Infisical's `export-type: env` step makes the secret available as a real environment variable with that exact name, so if the key in Infisical is literally `SUPABASE_DATABASE_URL`, the line reads `pg_dump --format=plain "$SUPABASE_DATABASE_URL" > backup.sql`, identical to Path B's dump step.)

- [x] **Step 2: Validate YAML syntax**

```bash
python3 -c "import yaml; yaml.safe_load(open('.github/workflows/backup-postgres.yml'))" && echo OK
```

Expected: `OK`

- [x] **Step 3: Trigger a manual run to verify it works end-to-end**

```bash
git add .github/workflows/backup-postgres.yml
git commit -m "ci: add daily encrypted PostgreSQL backup workflow

Closes R8 (no documented backup strategy) for the non-reconstructible
half of the data — Supabase Postgres holds user accounts, bookmarks,
and inspection reports with no free-tier automatic backup. Daily
pg_dump, GPG-encrypted (AES256), uploaded as a 30-day-retention
artifact. MongoDB is intentionally excluded — it's fully re-derivable
from NYC Open Data via POST /api/restaurants/refresh, so a backup job
for it would be complexity with no matching risk reduction."
git push
gh workflow run backup-postgres.yml --repo St4r4x/restaurant-analytics
```

Wait ~30 seconds, then check the run:

```bash
gh run list --repo St4r4x/restaurant-analytics --workflow=backup-postgres.yml --limit 1
```

Expected: a run with status `completed` and conclusion `success`. If it fails, run `gh run view --repo St4r4x/restaurant-analytics --log-failed` (using the run ID from the list) to see the exact error — most likely causes are a wrong connection string format (Supabase requires the pooler port for short-lived connections in many configurations) or a missing secret name typo.

- [x] **Step 4: Download and verify the artifact decrypts and restores cleanly**

```bash
gh run download --repo St4r4x/restaurant-analytics -D /tmp/backup-verify $(gh run list --repo St4r4x/restaurant-analytics --workflow=backup-postgres.yml --limit 1 --json databaseId --jq '.[0].databaseId')
ls /tmp/backup-verify/
```

Expected: a directory containing `backup.sql.gpg`.

```bash
gpg --batch --yes --passphrase "$(gh secret list --repo St4r4x/restaurant-analytics >/dev/null; echo 'PASTE_THE_SAVED_PASSPHRASE_HERE')" \
  --decrypt /tmp/backup-verify/postgres-backup-*/backup.sql.gpg > /tmp/backup-verify/backup.sql
head -20 /tmp/backup-verify/backup.sql
```

(You cannot programmatically retrieve the passphrase from GitHub — paste the value you saved in Task 1 Step 3 directly into this command, or export it as a local shell variable first: `export BACKUP_GPG_PASSPHRASE='...'` then use `--passphrase "$BACKUP_GPG_PASSPHRASE"`.)

Expected: `head` shows valid `pg_dump` SQL output (starts with `--` comment lines like `-- PostgreSQL database dump`, followed by `SET` statements and `CREATE TABLE` statements matching `UserEntity`/`BookmarkEntity`/`InspectionReportEntity`/`AuditLogEntity`'s underlying table names: `users`, `bookmarks`, `inspection_reports`, `audit_log`).

Clean up the local scratch files (they contain unencrypted PII — do not leave them on disk):

```bash
rm -rf /tmp/backup-verify
```

---

### Task 3: Write the backup/restore documentation

**Files:**
- Create: `docs/backup-restore.md`
- Modify: `docs/deployment.md` (add a "Disaster Recovery" section linking to the new doc)

**Interfaces:**
- Consumes: Task 2's workflow name (`backup-postgres.yml`) and artifact naming pattern (`postgres-backup-${{ github.run_id }}`) — referenced by name in the restore instructions.
- Produces: nothing consumed by later tasks — this is documentation.

- [x] **Step 1: Write `docs/backup-restore.md`**

```markdown
# Backup and Disaster Recovery

## PostgreSQL (Supabase) — daily encrypted backups

`.github/workflows/backup-postgres.yml` runs daily at 03:17 UTC (and on manual dispatch) and uploads a GPG-encrypted `pg_dump` as a GitHub Actions artifact, retained for 30 days.

**Why PostgreSQL and not MongoDB:** PostgreSQL holds user accounts, bookmarks, and inspection reports (with photos) — none of this is reconstructible if lost. MongoDB holds only the NYC restaurant dataset, which is fully re-derivable from the NYC Open Data API (see the MongoDB section below).

### Restoring from a backup

1. Go to the [Actions tab](https://github.com/St4r4x/restaurant-analytics/actions/workflows/backup-postgres.yml), find the run you want to restore from, and download its `postgres-backup-<run-id>` artifact (or via CLI: `gh run download --repo St4r4x/restaurant-analytics -D ./restore <run-id>`).
2. Decrypt it with the passphrase stored in your password manager (originally set as the `BACKUP_GPG_PASSPHRASE` GitHub Actions secret — GitHub secrets are write-only, so this passphrase only exists in your own records):

   ```bash
   gpg --batch --yes --passphrase "$BACKUP_GPG_PASSPHRASE" --decrypt backup.sql.gpg > backup.sql
   ```

3. Restore into the target database (a fresh Supabase project, or the existing one after clearing affected tables):

   ```bash
   psql "$SUPABASE_DATABASE_URL" < backup.sql
   ```

4. Delete the decrypted `backup.sql` file immediately after — it contains user emails, password hashes, and inspection report content in plaintext.

### Triggering an on-demand backup

Before a risky operation (schema migration, bulk data edit), trigger a backup manually instead of waiting for the next scheduled run:

```bash
gh workflow run backup-postgres.yml --repo St4r4x/restaurant-analytics
```

## MongoDB (Atlas) — no backup needed, full resync instead

The `newyork.restaurants` collection contains only data mirrored from the NYC Open Data API — there is nothing in it that isn't already available from the source. Instead of backing it up, restore it by resyncing:

1. Confirm the app is running and you have an ADMIN JWT (see `docs/development.md` for how to obtain one).
2. Trigger a full resync:

   ```bash
   curl -X POST https://<your-deployment-url>/api/restaurants/refresh \
     -H "Authorization: Bearer <ADMIN_JWT>"
   ```

3. Monitor progress:

   ```bash
   curl https://<your-deployment-url>/api/restaurants/sync-status
   ```

   Expect this to take several minutes depending on `nyc.api.max_records` (0 = unlimited, syncs the entire NYC Open Data dataset).

If the Redis cache or Elasticsearch index also need rebuilding after a Mongo resync, use the existing admin endpoints: `POST /api/admin/rebuild-cache` and the nightly Elasticsearch reindex (or trigger it manually via `POST /api/admin/cron/run/es-reindex`, per `docs/api.md`).
```

- [x] **Step 2: Add a "Disaster Recovery" section to `docs/deployment.md`**

Read the current end of `docs/deployment.md` first (it ends with a "Production Notes" section), then append this new section after it:

```markdown

---

## Disaster Recovery

See [backup-restore.md](backup-restore.md) for the full backup and restore procedure. Summary: PostgreSQL (Supabase) is backed up daily via `.github/workflows/backup-postgres.yml` (GPG-encrypted, 30-day retention); MongoDB (Atlas) has no backup because it's fully re-derivable from NYC Open Data.
```

- [x] **Step 3: Commit**

```bash
git add docs/backup-restore.md docs/deployment.md
git commit -m "docs: add backup/restore procedure for Postgres and MongoDB

PostgreSQL restore steps (decrypt + psql) and MongoDB resync steps
(POST /api/restaurants/refresh, no dump needed since the collection
is fully re-derivable from NYC Open Data). Links from deployment.md's
new Disaster Recovery section."
```

---

### Task 4: Update the certification risk analysis

**Files:**
- Modify: `certification/bloc4a-1-analyse-risques.md`

**Interfaces:**
- Consumes: Task 2's workflow existing and verified working, Task 3's documentation existing — this task only updates a status cell, it doesn't add new capability.
- Produces: nothing consumed by later tasks.

- [x] **Step 1: Update R8's status**

Read the current file first to find R8's exact row in the risk table (section "2. Cartographie des risques"), then change its "Statut" column from "Non traité" to:

```
**Traité** — PR <link to be filled with the actual PR number once opened> (2026-07-23)
```

(Use today's actual merge date if this is being executed after the PR merges, matching the pattern already used for R1/R2 in this same file.)

- [x] **Step 2: Add an indicator for backup job health**

In the same file's section 6 ("Indicateurs de suivi"), add a row:

```
| Job CI `backup-postgres` en échec | GitHub Actions | Quotidien (03:17 UTC) | Tout échec = vérifier la connectivité Supabase et relancer manuellement (`gh workflow run backup-postgres.yml`) |
```

- [x] **Step 3: Commit**

```bash
git add certification/bloc4a-1-analyse-risques.md
git commit -m "docs: flip R8 to Traité in certification risk analysis

PostgreSQL backup workflow and MongoDB restore documentation close
the remaining gap — R8 was the last unaddressed risk above score 4
in the analysis (R1/R2 closed in the earlier security-hardening PR)."
```

---

## Self-Review Notes

- **Spec coverage**: PostgreSQL backup (Task 2), MongoDB documentation-only decision (Task 3), restore documentation (Task 3), certification dossier update (Task 4) — all spec sections have a task. Secret provisioning (Task 1) was added because the spec explicitly flagged the Infisical-vs-GitHub-secret question as unresolved and requiring human action outside this session's access.
- **No placeholders left unresolved**: Task 2's two YAML variants (Path A/B) are both fully written out, not described abstractly — the implementer picks the one matching Task 1's actual finding rather than filling in a TBD.
- **Type/naming consistency verified**: the artifact name `postgres-backup-${{ github.run_id }}` in Task 2 is the exact string referenced in Task 3's restore documentation; the workflow filename `backup-postgres.yml` is used consistently across Tasks 2, 3, and 4's commit messages.
- **Out of scope reminder** (from spec): no automated restore testing, no alerting beyond GitHub's default failed-workflow email, no MongoDB backup job, no point-in-time recovery.
