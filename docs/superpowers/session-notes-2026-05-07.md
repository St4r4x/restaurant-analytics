# Session Notes — 2026-05-07

Observations from this session. Use as input for next brainstorming cycle.

## Graph insights (main branch, 1389 nodes / 82 communities)

**`ok()` is a 44-edge god node** — the `ResponseUtil.ok()` helper touches every controller and most tests. If you ever split the controller layer, this will be the first friction point. Worth watching but not refactoring now.

**Analytics API Layer has cohesion 0.04** — the lowest of any community. `AnalyticsController`, `AnalyticsDAO`, `AnalyticsDAOImpl` are technically one community but barely connected internally. Sign that the analytics surface is still growing without a clear internal contract.

**`InspectionReportEntity` cross-DB coupling** — the graph found an inferred edge from `InspectionReportEntity` (MongoDB) to PostgreSQL via the changelog. This is the entity that bridges inspection data (Mongo) with user reports (PostgreSQL JPA). Worth a design review before it grows further.

**Backlog items already have infrastructure:**
- ES Index Health Card → `ElasticsearchSyncService` already exposes index stats; just needs an admin endpoint + UI card
- Email Alerts → `RestaurantCacheService` already tracks grade changes via Redis; alerts can hook into the existing cache invalidation path

## Unimplemented plans in `docs/superpowers/plans/`

These plans were written but may not be fully shipped — worth checking CI status:
- `2026-05-05-osm-enrichment.md` — OSM contact enrichment on restaurant.html; known ~30-40% match rate
- `2026-05-06-grade-trend-audit-metrics.md` — Prometheus actuator + grade trend chart on analytics page
- `2026-05-06-ci-cron.md` — CI parallelization + cron job status endpoint

## Quick wins spotted

- **Bucket4j on search/autocomplete** — already in `pom.xml`, only covers `/api/auth/**`; one `@RateLimiter` annotation extends it to Elasticsearch autocomplete (prevents scraping)
- **Logback JSON** — `logstash-logback-encoder` is in `pom.xml`; just needs `logback-spring.xml` with JSON encoder for Railway structured logs
- **Cache warm-up cron** — `CronScheduler` already exists with a 02:30 daily slot; wire `RestaurantCacheService.warmUp()` into it

## Claude Code setup changes made this session

- Removed 24 GSD agents, replaced with superpowers workflow
- Added 4 hooks: HF skill suggester, graphify nudge, ruff autofix, graph context injector
- Added `hf-graph-bridge` skill
- Expanded allow-list for Python/ML/git tools
- Fixed TFE_TOKEN via `settings.json` env block
- Disabled `data-engineering` and `mongodb` plugins (not relevant to this stack)
- `skillListingBudgetFraction` set to 0.1
