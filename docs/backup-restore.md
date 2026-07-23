# Backup and Disaster Recovery

## PostgreSQL (Supabase) — daily encrypted backups

`.github/workflows/backup-postgres.yml` runs daily at 03:17 UTC (and on manual dispatch), dumps the Supabase Postgres database, encrypts it with GPG, and uploads it as a GitHub Actions artifact, retained for 30 days.

**Why PostgreSQL and not MongoDB:** PostgreSQL holds user accounts, bookmarks, and inspection reports (with photos) — none of this is reconstructible if lost. MongoDB holds only the NYC restaurant dataset, which is fully re-derivable from the NYC Open Data API (see the MongoDB section below).

### How the backup connects (for context when restoring)

- Credentials are pulled from **Infisical**, `prod` environment (project slug `nyc-restaurant-hygiene-z-qd-p`), not from a GitHub Actions secret — there is no single "database URL" secret to look up in `gh secret list`.
- The connection goes through the **Supavisor pooler** (`aws-0-eu-west-1.pooler.supabase.com:5432`), not the direct host (`db.<project-ref>.supabase.co`). The direct host is IPv6-only and unreachable from GitHub-hosted runners — the same applies if you're restoring from a network without IPv6 egress.
- The pooler username is project-scoped: `postgres.<project-ref>` (not the bare `postgres` role name).
- The workflow installs `postgresql-client-17` from the official PGDG apt repo, because Supabase runs Postgres 17 and `pg_dump`/`psql` refuse to talk to a newer major version than the client. Use a matching client version (17+) when restoring locally.

### Restoring from a backup

1. **Get the artifact.** Go to the [Actions tab](https://github.com/St4r4x/restaurant-analytics/actions/workflows/backup-postgres.yml), find the run you want to restore from, and download its `postgres-backup-<run-id>` artifact (or via CLI: `gh run download --repo St4r4x/restaurant-analytics -D ./restore <run-id>`).

2. **Get the GPG passphrase.** This is the `BACKUP_GPG_PASSPHRASE` GitHub Actions secret — GitHub secrets are write-only, so retrieve it from wherever it was saved when created (password manager), not from GitHub itself.

3. **Decrypt:**

   ```bash
   gpg --batch --yes --passphrase "$BACKUP_GPG_PASSPHRASE" --decrypt backup.sql.gpg > backup.sql
   ```

4. **Get the database credentials from Infisical**, `prod` environment (do not use `dev` — those are test-only credentials and won't match the target database):

   ```bash
   infisical login
   infisical secrets --projectId <id-for-nyc-restaurant-hygiene-z-qd-p> --env prod
   ```

   (Find the project ID from the Infisical dashboard for project slug `nyc-restaurant-hygiene-z-qd-p`, or via `infisical` CLI project listing.) This returns `SPRING_DATASOURCE_USERNAME` / `SPRING_DATASOURCE_PASSWORD` — the same variables the app itself uses in production.

5. **Restore into the target database** via the Supavisor pooler (mirroring the backup workflow's connection, since the direct host is unreachable from most networks/CI):

   ```bash
   PGHOST=aws-0-eu-west-1.pooler.supabase.com \
   PGPORT=5432 \
   PGDATABASE=postgres \
   PGUSER="$SPRING_DATASOURCE_USERNAME" \
   PGPASSWORD="$SPRING_DATASOURCE_PASSWORD" \
   psql < backup.sql
   ```

   Target a fresh Supabase project, or the existing one after clearing affected tables.

6. **Delete the decrypted `backup.sql` file immediately after** — it contains user emails, password hashes, and inspection report content in plaintext.

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
