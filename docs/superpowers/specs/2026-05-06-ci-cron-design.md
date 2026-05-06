# Design: CI Optimization + Cron System

**Date:** 2026-05-06  
**Status:** Approved

---

## 1. CI Optimization

### Problem

The current pipeline runs 6 jobs sequentially. `unit-test` and `integration-test` are fully independent but chained, adding ~3-4 min of unnecessary wall-clock time. The `changelog` job is redundant with manual `CHANGELOG.md` maintenance.

### New Pipeline Shape

```
build ──┬── unit-test ──────┬── e2e → docker
        └── integration-test ┘
```

### Changes to `.github/workflows/ci.yml`

1. **Remove `changelog` job entirely.** Changelog is maintained manually per project conventions.

2. **Parallelize `unit-test` and `integration-test`:**
   - Both keep `needs: build`
   - Remove `needs: unit-test` from `integration-test`

3. **`e2e` waits for both:**
   - Change `needs: integration-test` → `needs: [unit-test, integration-test]`

4. **`docker` becomes the terminal job:**
   - Change `needs: e2e` (unchanged)
   - Remove `if: github.ref == 'refs/heads/main'` gate from needs chain (it stays on the push step)

### No other changes

Maven cache, Java setup, Infisical secrets, and test commands are unchanged.

---

## 2. Cron System

### Problem

- Redis cache is cold after the nightly sync completes; first real user requests pay the full MongoDB aggregation cost.
- OSM enrichment runs only on new restaurants; stale data (phone, website, hours) never gets refreshed.
- ES reindex only triggers after a data sync; a failed sync leaves ES stale indefinitely.
- No visibility into when scheduled jobs ran or whether they succeeded.

### Architecture

One new class `CronScheduler` in `com.st4r4x.sync`. It owns all `@Scheduled` methods and maintains an in-memory job registry. `SyncService` keeps its existing `scheduledSync()` — `CronScheduler` does not replace it.

### Job Registry

```java
ConcurrentHashMap<String, JobStatus> registry
```

`JobStatus` fields:
- `lastRunAt` — `Instant`
- `durationMs` — `long`
- `success` — `boolean`
- `errorMessage` — `String` (null on success)

### Scheduled Jobs

| Job key | Cron expression | Action |
|---|---|---|
| `cache-warmup` | `0 30 2 * * *` | Pre-populate Redis with common aggregations (30 min after nightly sync at 02:00) |
| `osm-reenrichment` | `0 0 3 * * 0` | Call `OsmEnrichmentService.enrichAll()` — Sundays 03:00 |
| `es-reindex` | `0 0 4 * * *` | Call `ElasticsearchSyncService.reindex()` — daily 04:00 |

### Cache Warm-up Queries

Called in `cache-warmup` via `RestaurantCacheService.getOrLoad*`:

1. `getOrLoadByBorough(loader)` — `restaurants:by_borough`
2. `getOrLoadWorstCuisines(borough, 10, loader)` for each of the 5 boroughs
3. `getTopRestaurants(10)` — already in Redis sorted set, no warm-up needed (rebuilt during sync)

Requires injecting `RestaurantCacheService` + `RestaurantDAO` into `CronScheduler`.

### Admin Endpoint

`GET /api/admin/cron/status`

- Returns the full `registry` map as JSON
- Secured with `@PreAuthorize("hasRole('ADMIN')")` — same guard as `/heatmap`
- Added to `AdminController` (`AdminController` is 86 lines — well within single-responsibility scope)

### Scheduling thread pool

`application.properties` already has `spring.task.scheduling.pool.size=2`. Bump to `4` to accommodate the new jobs running without starving each other (nightly sync + cache warmup + osm + es can theoretically overlap on startup/retry).

---

## 3. Files to Create / Modify

### New files
- `src/main/java/com/st4r4x/sync/CronScheduler.java` — all scheduled jobs + registry
- `src/main/java/com/st4r4x/sync/JobStatus.java` — simple POJO (lastRunAt, durationMs, success, errorMessage)

### Modified files
- `.github/workflows/ci.yml` — parallelize jobs, remove changelog
- `src/main/resources/application.properties` — bump `spring.task.scheduling.pool.size` to `4`
- `src/main/java/com/st4r4x/controller/AdminController.java` (or new `CronController`) — add `/api/admin/cron/status`
