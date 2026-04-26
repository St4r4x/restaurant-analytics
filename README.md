# Restaurant Analytics — NYC Inspection Data

![CI](https://github.com/St4r4x/restaurant-analytics/actions/workflows/ci.yml/badge.svg)

Spring Boot REST API and web dashboard for exploring NYC restaurant hygiene data.
Data is synced live from the NYC Open Data API into MongoDB.

**Live:** https://restaurant-app-production-3b11.up.railway.app

Academic project — big data module.

## Stack

| Layer | Technology |
|-------|-----------|
| Language | Java 25 |
| Framework | Spring Boot 4.0.5 |
| Primary DB | MongoDB Atlas M0 (raw driver, no Spring Data) |
| RDBMS | Supabase PostgreSQL (users, bookmarks, reports) |
| Cache | Upstash Redis 7 (TLS, TTL 3600 s) |
| Security | JWT (access 15 min / refresh 7 days) |
| Build | Maven |
| CI/CD | GitHub Actions → GHCR → Railway |

## Local Development

```bash
# Copy and fill in secrets
cp .env.example .env

# Start all services (MongoDB, Redis, PostgreSQL, app)
docker compose up -d

# Follow logs
docker compose logs -f app
```

App runs on http://localhost:8080. Swagger UI: http://localhost:8080/swagger-ui.html

## User Roles

**ROLE_CUSTOMER**
- Search restaurants by name or address
- View restaurant detail page (grade badge, inspection history)
- Browse restaurants on the interactive map
- Bookmark restaurants

**ROLE_CONTROLLER**
- All customer capabilities
- File internal inspection reports (`POST /api/reports`)
- List and filter own reports (`GET /api/reports`)
- Edit own reports (`PATCH /api/reports/{id}`)
- Attach photos to reports (`POST /api/reports/{id}/photo`)

Controller registration requires the `CONTROLLER_SIGNUP_CODE` environment variable.

**ROLE_ADMIN**
- Access admin panel at `/admin`
- Trigger NYC Open Data sync (`POST /api/restaurants/refresh`)
- Rebuild Redis cache (`POST /api/restaurants/rebuild-cache`)
- Download at-risk restaurant CSV (`GET /api/inspection/at-risk/export.csv`)
- View inspection report statistics (`GET /api/reports/stats`)

Admin registration requires the `ADMIN_SIGNUP_CODE` environment variable.

## API Endpoints

### Authentication

| Method | Path | Description |
|--------|------|-------------|
| POST | /api/auth/register | Register (CUSTOMER, CONTROLLER, or ADMIN with signup code) |
| POST | /api/auth/login | Login → access + refresh JWT |
| POST | /api/auth/refresh | Refresh access token |

### Restaurants (public)

| Method | Path | Description |
|--------|------|-------------|
| GET | /api/restaurants/by-borough | Count per borough |
| GET | /api/restaurants/stats | Global stats |
| GET | /api/restaurants/health | Health check |
| GET | /api/restaurants/search?q=&limit= | Search by name or address |
| GET | /api/restaurants/map-points | Lightweight points for map |
| GET | /api/restaurants/{restaurantId} | Restaurant detail |
| GET | /api/restaurants/top | Top healthiest restaurants |
| GET | /api/restaurants/cuisines | All distinct cuisine types |
| GET | /api/restaurants/by-cuisine | Top cuisines by count |
| GET | /api/restaurants/hygiene-radar | Healthiest restaurants by borough/cuisine |
| GET | /api/restaurants/recent-inspections | Restaurants inspected in last N days |
| GET | /api/restaurants/nearby | Geospatial search (2dsphere) |

### Inspections (public)

| Method | Path | Description |
|--------|------|-------------|
| GET | /api/inspection/uncontrolled | Uncontrolled restaurants (grade C/Z or no inspection in 12 months) |
| GET | /api/inspection/uncontrolled/export.csv | Download as CSV |
| GET | /api/inspection/at-risk/export.csv | At-risk restaurants CSV (ADMIN only) |

### Reports (CONTROLLER only)

| Method | Path | Description |
|--------|------|-------------|
| POST | /api/reports | Create inspection report |
| GET | /api/reports | List own reports (optional ?status=) |
| PATCH | /api/reports/{id} | Edit own report |
| POST | /api/reports/{id}/photo | Upload photo |
| GET | /api/reports/{id}/photo | Stream photo |

### Analytics

| Method | Path | Description |
|--------|------|-------------|
| GET | /api/analytics/kpis | KPI tiles (total, % grade A, avg score, at-risk count) |
| GET | /api/analytics/borough-grades | Grade distribution by borough |
| GET | /api/analytics/cuisine-rankings | Top 10 cleanest and worst cuisines |
| GET | /api/analytics/at-risk | Top 50 restaurants with last grade C or Z |

### Admin (ADMIN only)

| Method | Path | Description |
|--------|------|-------------|
| POST | /api/restaurants/refresh | Trigger NYC Open Data sync |
| POST | /api/restaurants/rebuild-cache | Rebuild Redis cache |
| GET | /api/reports/stats | Report counts by status and grade |

### Users (authenticated)

| Method | Path | Description |
|--------|------|-------------|
| GET | /api/users/me | Current user profile |
| GET | /api/users/me/bookmarks | List bookmarks |
| POST | /api/users/me/bookmarks/{restaurantId} | Add bookmark |
| DELETE | /api/users/me/bookmarks/{restaurantId} | Remove bookmark |

## Pages

| URL | Auth | Description |
|-----|------|-------------|
| / | None | Landing page / customer dashboard |
| /login | None | Login / Register |
| /profile | Required | User profile |
| /analytics | None | City-wide analytics |
| /dashboard | CONTROLLER | Inspector dashboard |
| /restaurant/{camis} | None | Restaurant detail |
| /inspection-map | None | Interactive Leaflet map |
| /my-bookmarks | Required | Saved restaurants |
| /admin | ADMIN | Admin panel |

## Configuration

Key environment variables (see `.env.example`):

```bash
# MongoDB
MONGODB_URI=mongodb+srv://...
MONGODB_DATABASE=newyork
MONGODB_COLLECTION=restaurants

# PostgreSQL
SPRING_DATASOURCE_URL=jdbc:postgresql://...
SPRING_DATASOURCE_USERNAME=...
SPRING_DATASOURCE_PASSWORD=...

# Redis
REDIS_HOST=...
REDIS_PORT=6379
REDIS_PASSWORD=...
REDIS_SSL=true

# Auth
JWT_SECRET=<min 32 chars>
CONTROLLER_SIGNUP_CODE=<optional>
ADMIN_SIGNUP_CODE=<optional>
```

## Tests

```bash
mvn test                    # unit tests
mvn failsafe:integration-test failsafe:verify   # integration tests (requires MongoDB)
```

## CI/CD

```
push to main
  └── build → unit-test → integration-test → e2e → docker (push to GHCR)
                                                          ↓
                                              Railway detects CI passed → deploys
```

Secrets managed via [Infisical](https://infisical.com) (project: `NYC_Restaurant_Hygiene`).
