# Roadmap — Restaurant Analytics

## Current state (v1.0)

Spring Boot REST API + Thymeleaf dashboard analysing New York restaurant inspection data stored in MongoDB.
Raw dataset loaded statically from `restaurants.json`.

---

## Phase 1 — External API integration

**Goal:** Replace the static JSON dataset with live data pulled from a public API (NYC Open Data / Yelp Fusion / Google Places).

### Tasks

- [ ] Add an `ExternalApiClient` service (RestTemplate or WebClient) to fetch restaurant data from NYC Open Data Socrata API (`data.cityofnewyork.us/resource/43nn-pn8j.json`)
- [ ] Map API response fields to the existing `Restaurant` / `Address` domain model
- [ ] Add a scheduled job (`@Scheduled`) to refresh the MongoDB collection periodically (e.g. nightly)
- [ ] Expose a `POST /api/restaurants/refresh` endpoint to trigger a manual sync
- [ ] Store the last sync timestamp and expose it via `GET /api/restaurants/sync-status`
- [ ] Add retry logic and error handling for API failures (exponential back-off)
- [ ] Write unit tests for the API client and scheduler

### Notes

- API key management via `.env` / Docker env vars (`NYC_API_APP_TOKEN`)
- Keep the static `restaurants.json` as a fallback seed for offline / test environments

---

## Phase 2 — Second NoSQL database (Redis or Elasticsearch)

**Goal:** Introduce a second NoSQL store to complement MongoDB — either a cache layer (Redis) or a full-text search engine (Elasticsearch).

### Option A — Redis (cache + leaderboard)

- [ ] Add `lettuce-core` / Spring Data Redis dependency
- [ ] Cache expensive aggregation results (borough counts, cuisine scores) with a configurable TTL
- [ ] Implement a real-time "top restaurants" sorted set updated on each data refresh
- [ ] Expose `GET /api/restaurants/top` backed by the Redis sorted set
- [ ] Add cache invalidation on manual sync

### Option B — Elasticsearch (full-text search)

- [ ] Add `elasticsearch-java` client dependency
- [ ] Index restaurant documents into Elasticsearch on each MongoDB sync
- [ ] Expose `GET /api/restaurants/search?q=<term>` for full-text search across name, cuisine, address
- [ ] Add faceted filtering (borough, cuisine type, score range)
- [ ] Build a search UI component in the dashboard

### Decision criteria

| Criterion       | Redis            | Elasticsearch     |
| --------------- | ---------------- | ----------------- |
| Primary benefit | Performance      | Discoverability   |
| Complexity      | Low              | Medium            |
| Academic value  | Caching patterns | Search & indexing |

> Recommended for academic scope: **Redis** (Phase 2a) then **Elasticsearch** (Phase 2b optional).

---

## Phase 3 — User management system

**Goal:** Add authentication, authorisation, and per-user preferences/bookmarks.

### Tasks

#### 3.1 Authentication

- [x] Add Spring Security + JWT (`jjwt` library)
- [x] `POST /api/auth/register` — create account (username, email, hashed password via BCrypt)
- [x] `POST /api/auth/login` — return signed JWT
- [x] `POST /api/auth/refresh` — refresh token endpoint
- [x] Store users in a dedicated MongoDB collection `users`

#### 3.2 Authorisation

- [ ] Define roles: `ROLE_USER`, `ROLE_ADMIN`
- [ ] Protect write/admin endpoints (`/api/restaurants/refresh`, etc.) behind `ROLE_ADMIN`
- [ ] Public read endpoints remain unauthenticated

#### 3.3 User features

- [ ] `POST /api/users/me/bookmarks/{restaurantId}` — bookmark a restaurant
- [ ] `GET /api/users/me/bookmarks` — list bookmarked restaurants
- [ ] `POST /api/users/me/reviews` — submit a score/comment for a restaurant
- [ ] Aggregate user reviews alongside inspection scores in the dashboard

#### 3.4 UI

- [ ] Login / register form (Thymeleaf or React SPA depending on evolution)
- [ ] User profile page showing bookmarks and reviews
- [ ] Admin panel for triggering data refresh and viewing sync logs

---

## Phase 4 — Stretch goals (post-academic)

| Feature            | Description                                              |
| ------------------ | -------------------------------------------------------- |
| GraphQL API        | Replace / complement REST with a GraphQL schema          |
| Geospatial queries | Add `$geoNear` queries + Leaflet.js map in the dashboard |
| Data pipeline      | Kafka ingestion topic fed by the external API client     |
| Observability      | Micrometer + Prometheus + Grafana dashboards             |
| CI/CD              | GitHub Actions: build, test, Docker push on every PR     |
