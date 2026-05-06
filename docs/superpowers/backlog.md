# Feature Backlog

Ideas deferred from the 2026-05-06 brainstorming session. Pick any of these for the next cycle.

## Data & Analytics

- **Violation heatmap by category** — which violation types cluster in which boroughs (purely from existing MongoDB data, no new data needed)
- **Predictive at-risk scoring** — score each restaurant based on days since last inspection + grade trend + violation count; surface "about to fail" restaurants separately from the existing at-risk list

## User-facing

- **Email alerts** (Spring Mail + cron) — notify users when a bookmarked restaurant gets a new inspection or grade change
- **Public report feed** — anonymized, paginated timeline of recent controller reports visible to all authenticated users (currently reports are write-only for CONTROLLER, no public read)
- **Restaurant comparison** — side-by-side grade/score/violation history for 2-3 restaurants; useful for chains

## Ops & Reliability

- **Rate limiting on all API routes** — Bucket4j is already in the project but only covers `/api/auth/**`; extend to search/autocomplete to prevent scraping
- **ES index health card on admin page** — doc count, last reindex time, index size via `GET /api/admin/es/status` hitting the ES stats API
- **Structured logging** (Logback JSON appender) — `logstash-logback-encoder` is already in pom.xml; just needs `logback-spring.xml` wired up with the JSON encoder for Railway log search
