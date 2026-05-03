# Commercialisation Guide

## What This App Is

A public web dashboard and REST API for NYC restaurant hygiene data, sourced live from the NYC Open Data API. It serves three audiences:

- **General public / food consumers** — search restaurants, browse grades, check the map
- **Health inspectors / field controllers** — file and track internal inspection reports
- **City analytics users** — borough trends, cuisine rankings, at-risk restaurant lists

The data is free and open (NYC Open Data, CC0 license). The product is the interface, the analysis layer, and the role-gated workflow on top of it.

---

## Technical Hardening Required Before Launch

These are the gaps between the current academic state and a shippable product.

### Must-fix (blockers)

| Item | Current state | What to do |
|------|--------------|------------|
| `DataSeeder` | Seeds known credentials unconditionally on every startup | Gate behind `@Profile("dev")` — remove entirely from production containers |
| `CONTROLLER_SIGNUP_CODE` / `ADMIN_SIGNUP_CODE` | Env vars, no rotation mechanism | Move to a secrets manager (Railway secrets, HashiCorp Vault, AWS Secrets Manager) |
| `jwt.secret` | Set manually per deployment | Generate per environment, minimum 64 chars, rotate on breach |
| HTTPS | Handled by Railway proxy | Enforce `X-Forwarded-Proto` redirect in `SecurityConfig` for any non-Railway deploy |
| `nyc.api.max_records=0` | Unlimited — first sync can take 10+ minutes | Set a documented cap; add a progress/status endpoint for the admin panel |
| Error messages | Some stack traces may leak in 500 responses | Ensure `ResponseUtil` wraps all unhandled exceptions; never expose stack traces |

### Should-fix (before paying customers)

| Item | What to do |
|------|------------|
| Rate limiting | Currently only on `/api/auth/**`; add limits on search and map-points endpoints to prevent scraping |
| Photo storage | `app.uploads.dir` writes to local disk — not portable across containers; migrate to S3/R2/GCS |
| Pagination | Server-side pagination exists on most lists; verify at-risk and uncontrolled lists use DB-level `$limit` not in-memory slicing |
| Observability | Add structured logging (logstash-logback-encoder is already in pom.xml); wire to a log aggregator (Datadog, Grafana Cloud) |
| Health endpoint | `/api/restaurants/health` is public and unauthenticated — good; extend it with DB/Redis connectivity status |
| GDPR / data retention | `inspection_reports` and `users` tables contain PII (username, email); document retention policy and add a `/api/users/me` DELETE endpoint |

---

## Positioning Options

### Option A — Free public tool (ad-supported or grant-funded)

Keep the dashboard fully public. Monetise via:
- Display ads (Google AdSense) — low friction, low revenue
- NYC / public-health grants for civic tech projects
- Open-source credibility → consulting revenue

**Best for:** portfolio, civic tech, academic continuation.

### Option B — Freemium SaaS

| Tier | Features | Price |
|------|----------|-------|
| Free | Search, map, analytics dashboard, 5 bookmarks | $0 |
| Pro | Unlimited bookmarks, CSV exports, email alerts on grade changes | $9/month |
| Inspector | All Pro + report filing, photo uploads, team reports | $29/seat/month |

The `ROLE_CONTROLLER` workflow already maps cleanly to the Inspector tier.

**Best for:** restaurant owners monitoring competitors, food journalists, small inspection agencies.

### Option C — White-label B2B

License the platform to:
- Municipal health departments (other US cities with open inspection data)
- Food safety consultancies
- Restaurant chains for internal compliance tracking

Requires multi-tenancy (per-organisation data isolation), custom branding, and an SLA.

**Best for:** higher contract value, longer sales cycle.

---

## Data & Legal

- **NYC Open Data license**: CC0 (public domain). No attribution required, commercial use permitted.
- **User data**: email and username are stored in PostgreSQL. A privacy policy and cookie notice are required before collecting user registrations in the EU (GDPR) or California (CCPA).
- **Photo uploads**: inspection report photos may contain PII (faces, handwriting). Store with access control; do not expose publicly.
- **NYC Open Data reliability**: the API is maintained by NYC DOHMH. SLA is informal. Build a fallback: if the nightly sync fails, serve stale data with a visible "last updated" timestamp rather than an error page.

---

## Infrastructure for Scale

The current Railway deployment is sufficient for hundreds of daily users. For higher load:

| Bottleneck | Current | Upgrade path |
|-----------|---------|-------------|
| API | Single Spring Boot instance | Horizontal scale behind a load balancer; stateless (JWT, no session) so trivial |
| MongoDB | Single node | MongoDB Atlas M10+ with replica set for read scaling |
| Redis | Single node | Redis Cluster or Upstash (serverless, Railway-native) |
| Photo storage | Local disk | Cloudflare R2 (S3-compatible, no egress fees) |
| Search | MongoDB `$text` index | Elastic/OpenSearch for full-text relevance ranking if query volume grows |

---

## Suggested Launch Sequence

1. **Gate `DataSeeder`** behind `@Profile("dev")` and remove known credentials from production
2. **Add a privacy policy page** and cookie banner (required before user registration goes live publicly)
3. **Implement user deletion** (`DELETE /api/users/me`) for GDPR compliance
4. **Migrate photo storage** to object storage (R2 or S3)
5. **Set up monitoring** — at minimum: uptime check + alert on sync failure
6. **Soft launch** with free tier only — gather feedback, measure real usage patterns
7. **Add Stripe** (or Lemon Squeezy for simpler setup) for Pro/Inspector billing once there is demand signal
