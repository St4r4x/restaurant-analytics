# API Reference

Base URL: `http://localhost:8080`  
Interactive docs: `http://localhost:8080/swagger-ui.html`

All endpoints that return lists accept an optional `limit` query parameter (default varies per endpoint).  
Authenticated endpoints require `Authorization: Bearer <accessToken>`.

---

## Authentication

| Method | Path | Auth | Description |
|--------|------|------|-------------|
| POST | `/api/auth/register` | None | Register — body: `RegisterRequest { username, email, password, signupCode? }` |
| POST | `/api/auth/login` | None | Login — body: `AuthRequest { username, password }` → `JwtResponse { accessToken, refreshToken }` |
| POST | `/api/auth/refresh` | None | Refresh — body: `RefreshRequest { refreshToken }` → new `JwtResponse` |

`signupCode` is required for `ROLE_CONTROLLER` (env `CONTROLLER_SIGNUP_CODE`) and `ROLE_ADMIN` (env `ADMIN_SIGNUP_CODE`).

---

## Restaurants

All read endpoints are **public** (no auth required).

| Method | Path | Query params | Description |
|--------|------|-------------|-------------|
| GET | `/api/restaurants/by-borough` | — | Count per borough (sorted desc) |
| GET | `/api/restaurants/stats` | — | Global stats object |
| GET | `/api/restaurants/health` | — | Health check — version from `application.properties` |
| GET | `/api/restaurants/search` | `q`, `limit` | Search by name or address |
| GET | `/api/restaurants/map-points` | `borough?` | Lightweight `{ lat, lng, grade }` array for the map |
| GET | `/api/restaurants/{restaurantId}` | — | Full restaurant document (grades array included) |
| GET | `/api/restaurants/top` | `limit` | Top healthiest restaurants from Redis sorted set |
| GET | `/api/restaurants/cuisines` | — | Distinct cuisine types, alphabetical |
| GET | `/api/restaurants/by-cuisine` | `limit` | Top cuisines by restaurant count |
| GET | `/api/restaurants/cuisine-scores` | `cuisine`, `borough?` | Average inspection score per borough for a cuisine |
| GET | `/api/restaurants/worst-cuisines` | `borough`, `limit` | Cuisines with highest average score in a borough |
| GET | `/api/restaurants/popular-cuisines` | `minCount` | Cuisines with ≥ minCount restaurants |
| GET | `/api/restaurants/hygiene-radar` | `borough?`, `cuisine?`, `limit` | Best restaurants filtered by borough/cuisine |
| GET | `/api/restaurants/recent-inspections` | `days`, `limit` | Restaurants inspected in last N days |
| GET | `/api/restaurants/nearby` | `lat`, `lng`, `radius`, `limit` | Geospatial search (2dsphere, radius in metres) |
| POST | `/api/restaurants/refresh` | — | **ADMIN** — trigger NYC Open Data sync |
| POST | `/api/restaurants/rebuild-cache` | — | **ADMIN** — rebuild Redis cache |

---

## Inspections

| Method | Path | Auth | Description |
|--------|------|------|-------------|
| GET | `/api/inspection/uncontrolled` | None | Uncontrolled restaurants (grade C/Z or no inspection in 12 months) |
| GET | `/api/inspection/uncontrolled/export.csv` | None | Same list as CSV download |
| GET | `/api/inspection/at-risk/export.csv` | ADMIN | At-risk restaurants as CSV |

---

## Reports (CONTROLLER only)

| Method | Path | Description |
|--------|------|-------------|
| POST | `/api/reports` | Create report — body: `ReportRequest { restaurantId, grade, status, violationCodes?, notes? }` |
| GET | `/api/reports` | List own reports — optional `?status=OPEN\|IN_PROGRESS\|RESOLVED` |
| PATCH | `/api/reports/{id}` | Edit own report (partial update) |
| POST | `/api/reports/{id}/photo` | Upload photo (multipart/form-data) |
| GET | `/api/reports/{id}/photo` | Stream stored photo |
| GET | `/api/reports/stats` | **ADMIN** — counts by status and grade |

---

## Analytics

All analytics endpoints are **public**.

| Method | Path | Description |
|--------|------|-------------|
| GET | `/api/analytics/kpis` | KPI tiles: total count, % grade A, average score, at-risk count |
| GET | `/api/analytics/borough-grades` | Grade distribution by borough |
| GET | `/api/analytics/cuisine-rankings` | Top 10 cleanest and worst cuisines |
| GET | `/api/analytics/at-risk` | Top 50 restaurants whose last grade is C or Z |

---

## Users

| Method | Path | Auth | Description |
|--------|------|------|-------------|
| GET | `/api/users/me` | Required | Current user profile (`bookmarkCount`, `reportCount`) |
| GET | `/api/users/me/bookmarks` | Required | List bookmarked restaurant IDs |
| POST | `/api/users/me/bookmarks/{restaurantId}` | Required | Add bookmark |
| DELETE | `/api/users/me/bookmarks/{restaurantId}` | Required | Remove bookmark |

---

## Error Responses

All errors follow a consistent envelope:

```json
{ "error": "human-readable message" }
```

| Status | When |
|--------|------|
| 400 | Invalid parameters (`IllegalArgumentException`) |
| 401 | Missing or expired JWT |
| 403 | Insufficient role |
| 404 | Resource not found |
| 500 | Unhandled server error |
